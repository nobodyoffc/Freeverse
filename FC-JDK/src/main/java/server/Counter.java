package server;

import data.apipData.WebhookPushBody;
import ui.Inputer;
import data.fchData.Block;
import data.feipData.Service;
import data.feipData.serviceParams.Params;
import clients.ApipClient;
import data.apipData.Fcdsl;
import co.elastic.clients.elasticsearch._types.SortOrder;
import com.google.gson.reflect.TypeToken;
import utils.*;
import config.ApiAccount;
import constants.*;
import utils.http.AuthType;
import utils.http.RequestMethod;
import clients.NaSaClient.NaSaRpcClient;
import redis.clients.jedis.JedisPool;
import server.balance.BalanceInfo;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.google.gson.Gson;

import data.fchData.Cash;
import data.fchData.OpReturn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import server.order.Order;
import server.order.OrderOpReturn;
import server.reward.RewardInfo;
import server.reward.Rewarder;
import server.rollback.Rollbacker;
import config.Settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static constants.Constants.*;
import static constants.FieldNames.NEW_CASHES;
import static constants.FieldNames.OWNER;
import static constants.IndicesNames.BLOCK;
import static constants.IndicesNames.ORDER;
import static constants.Strings.*;
import static utils.RedisUtils.readHashLong;
import static constants.Values.FALSE;
import static config.Settings.FROM_WEBHOOK;
import static config.Settings.addSidBriefToName;

public class Counter implements Runnable {

    /*
    1. automatic distribution
    2. the threshold of the new order sum actives the distribution
     */
    protected volatile AtomicBoolean running = new AtomicBoolean(true);
    protected static final Logger log = LoggerFactory.getLogger(Counter.class);
    protected ElasticsearchClient esClient; //for the data of this service
    protected ApipClient apipClient; //for the data of the FCH and FEIP
    protected NaSaRpcClient naSaRpcClient;//for the data of the FCH
    protected final JedisPool jedisPool; //for the running data of this service
    protected final Gson gson = new Gson();
    protected final String account;
    protected final String minPayment;
    protected final String listenPath;
    protected boolean fromWebhook;
    protected final String sid;
    protected final List<ApiAccount> paidApiAccountList;
    protected byte[] counterPriKey;


    public Counter(Settings settings, byte[] counterPriKey, Params params) {
        this.sid = settings.getSid();
        this.listenPath = (String) settings.getSettingMap().get(LISTEN_PATH);
        if(settings.getSettingMap().get(FROM_WEBHOOK)!=null)
            this.fromWebhook = (Boolean) settings.getSettingMap().get(FROM_WEBHOOK);
        this.account= params.getDealer();
        this.minPayment  = params.getMinPayment();

        this.esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        this.naSaRpcClient = (NaSaRpcClient) settings.getClient(Service.ServiceType.NASA_RPC);
        this.apipClient =(ApipClient)settings.getClient(Service.ServiceType.APIP);
        this.jedisPool = (JedisPool) settings.getClient(Service.ServiceType.REDIS);
        this.paidApiAccountList = settings.getPaidAccountList();
        this.counterPriKey=counterPriKey;
    }


    public AtomicBoolean isRunning(){
        return running;
    }

    public void run() {
        System.out.println("The counter is running...");
        int countBackUpBalance = 0;
        int countReward = 0;
        Rewarder rewarder = new Rewarder(sid,account,this.apipClient,this.esClient,this.naSaRpcClient,this.jedisPool);
        checkIfNewStart();

        while (running.get()) {
            checkRollback();
            getNewOrders();
            countBackUpBalance++;
            countReward++;
            if (countBackUpBalance == BalanceBackupInterval) {
                countBackUpBalance = backupBalance();

//                localTask();
            }

            if (countReward == RewardInterval) {
                RewardInfo rewardInfo= rewarder.doReward(paidApiAccountList,counterPriKey);
                if(rewardInfo!=null) log.info("Reward is done: " +
                        "\n\tTotal amount: " + utils.FchUtils.satoshiToCoin(rewardInfo.getRewardT()) +
                        "\n\tBestHeight:" + rewardInfo.getBestHeight());
            }

            if(listenPath!=null) waitNewOrder();
            else {
                try {
                TimeUnit.SECONDS.sleep(60);
                } catch (Exception ignore) {}
            }
        }
    }
    public static boolean isDirectoryEmpty(File directory) {
        if(!directory.exists()){
            boolean done = directory.mkdir();
            if(!done) System.out.println("Failed to make directory:"+directory);
            return true;
        }
        if (directory.isDirectory()) {
            String[] files = directory.list();
            return files == null || files.length == 0;
        }
        return true;
    }
    protected void localTask() {}
    public static boolean checkUserBalance(String sid, JedisPool jedisPool, ElasticsearchClient esClient, BufferedReader br) {
        try(Jedis jedis = jedisPool.getResource()) {
            Map<String, String> balanceMap = jedis.hgetAll(addSidBriefToName(sid, FieldNames.BALANCE));
            if(balanceMap==null||balanceMap.isEmpty()){
                String fileName = Settings.getLocalDataDir(sid)+ FieldNames.BALANCE;
                File file = new File(fileName + 0 + DOT_JSON);
                if(!file.exists()||file.length()==0){
                    while(true) {
                        if (Inputer.askIfYes(br, "No balance in redis and files. Import from file?")) {
                            String importFileName = Inputer.inputString(br, "Input the path and file name:");
                            File file1 = new File(importFileName);
                            if(!file1.exists()){
                                System.out.println("File does not exist. Try again.");
                                continue;
                            }
                            BalanceInfo.recoverUserBalanceFromFile(sid, file1.getPath(), jedisPool);
                            return true;
                        }else if(Inputer.askIfYes(br, "Import from ES?")) {
                            BalanceInfo.recoverUserBalanceFromEs(sid,esClient, jedisPool);
                            return true;
                        }else return false;
                    }
                }
            }else{
                System.out.println("There are "+balanceMap.size()+" users.");
                jedis.select(0);
                String lastHeightStr = jedis.get(addSidBriefToName(sid,ORDER_LAST_HEIGHT));
                if(lastHeightStr==null) {
                    jedis.set(addSidBriefToName(sid,ORDER_LAST_HEIGHT),"0");
                    jedis.set(addSidBriefToName(sid,ORDER_LAST_BLOCK_ID), zeroBlockId);
                    System.out.println("No balance yet. New start.");
                    return true;
                }
                long lastHeight = Long.parseLong(lastHeightStr);
                String lastOrderDate = FcUtils.heightToNiceDate(lastHeight);
                System.out.println("The last order was created at "+lastOrderDate);
            }
        }
        return true;
    }

    protected int backupBalance() {
        int countBackUpBalance;
        try {
            BalanceInfo.backupBalance(sid,this.esClient,jedisPool);
            BalanceInfo.deleteOldBalance(sid,esClient);
        } catch (Exception e) {
            log.error("Failed to backup user balance, consumeVia, orderVia, or pending reward to ES.", e);
        }
        countBackUpBalance = 0;
        return countBackUpBalance;
    }

    protected void checkIfNewStart() {
        try(Jedis jedis = jedisPool.getResource()) {
            String orderLastHeightKey = addSidBriefToName(sid, ORDER_LAST_HEIGHT);
            String lastHeightStr = jedis.get(orderLastHeightKey);
            if (lastHeightStr == null) {
                jedis.set(orderLastHeightKey, "0");
                String orderLastBlockIdKey = addSidBriefToName(sid, ORDER_LAST_BLOCK_ID);
                jedis.set(orderLastBlockIdKey, Constants.zeroBlockId);
                return;
            }
            if("0".equals(lastHeightStr)){
                String orderLastBlockIdKey = addSidBriefToName(sid, ORDER_LAST_BLOCK_ID);
                jedis.set(orderLastBlockIdKey, Constants.zeroBlockId);
            }
        }
    }

protected void waitNewOrder() {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    log.debug(LocalDateTime.now().format(formatter) + "  Wait for new order...");
    utils.FchUtils.waitForChangeInDirectory(listenPath,running);
}

    protected void checkRollback() {
        try(Jedis jedis = jedisPool.getResource()) {
            long lastHeight = RedisUtils.readLong(Settings.addSidBriefToName(sid,ORDER_LAST_HEIGHT));
            String lastBlockId = jedis.get(Settings.addSidBriefToName(sid,Strings.ORDER_LAST_BLOCK_ID));
            try {
                boolean rolledBack;
                if(apipClient!=null)rolledBack= Rollbacker.isRolledBack(lastHeight, lastBlockId, apipClient);
                else rolledBack = Rollbacker.isRolledBack( esClient,lastHeight, lastBlockId);

                if (rolledBack)
                    Rollbacker.rollback(sid,lastHeight - 30, esClient, jedisPool);
            } catch (IOException e) {
                log.debug("Order rollback wrong.");
                e.printStackTrace();
            } catch (Exception e) {
                log.debug("Order rollback wrong.");
                throw new RuntimeException(e);
            }
        }
    }

    protected void getNewOrders() {
        System.out.println("Scan new orders...");
        long lastHeight = RedisUtils.readLong(addSidBriefToName(sid,ORDER_LAST_HEIGHT));
        List<Cash> cashList;
        if(fromWebhook){
            if(!isDirectoryEmpty(new File(this.listenPath)))
                cashList=getNewCashListFromFile(lastHeight,this.listenPath);
            else return;
        } else{
            if(apipClient!=null)
                cashList = getNewCashListFromApip(lastHeight, account, apipClient);
            else cashList = getNewCashListFromEs(lastHeight, account, esClient);
        }
        if (cashList != null && cashList.size() > 0) {
            setLastOrderInfoToRedis(cashList);
            getValidOrderList(cashList);
        }
    }

    protected List<Cash> getNewCashListFromFile(long lastHeight,String listenPath) {
        long initLastHeight = lastHeight;
        String method = ApipApiNames.NEW_CASH_BY_FIDS;
        long bestHeight = 0;
        List<Cash> allCashList = new ArrayList<>();

        int i=0;
        File file;
        while (true) {
            file = new File(listenPath, method+i+DOT_JSON);
            if(!file.exists()){
                i++;
                if(new File(listenPath,method+i+DOT_JSON).exists())continue;
                if(isDirectoryEmpty(new File(listenPath)))break;
                else {
                    i=0;
                    continue;
                }
            }
            if(file.length()==0){
                System.out.println("File "+file.getName()+" is empty.");
                return null;
            }
            System.out.println("Got new order file:"+file.getName());
            try(FileInputStream fis = new FileInputStream(file)){
                String webhookPushBodyStr = new String(fis.readAllBytes());
                WebhookPushBody webhookPushBody = gson.fromJson(webhookPushBodyStr,WebhookPushBody.class);
                if(webhookPushBody==null){
                    System.out.println("Failed to parse webhookPushBody.");
                    return null;
                }
                if(webhookPushBody.getBestHeight()==null){
                    fis.close();
                    deleteFile(file);
                    continue;
                }
                if(webhookPushBody.getBestHeight() > lastHeight) {

                        if(apipClient!=null) {
                            allCashList = getNewCashListFromApip(lastHeight, account, apipClient);
                            if (apipClient.getFcClientEvent() != null && apipClient.getFcClientEvent().getCode() == 0) {
                                bestHeight = apipClient.getFcClientEvent().getResponseBody().getBestHeight();
                                lastHeight = bestHeight;
                            }
                        }else if(esClient!=null){
                            allCashList = getNewCashListFromEs(lastHeight,account,esClient);
                            Block block = EsUtils.getBestOne(esClient, BLOCK, HEIGHT, SortOrder.Desc, Block.class);
                            if(block!=null){
                                bestHeight = block.getHeight();
                                lastHeight = bestHeight;
                            }
                        }

                        if(allCashList==null || allCashList.isEmpty()) {
                            List<Cash> cashList = ObjectUtils.objectToList(webhookPushBodyStr, Cash.class);//DataGetter.getCashList(webhookPushBodyStr);
                            if(cashList==null)return null;
                            if(allCashList==null)allCashList = new ArrayList<>();
                            allCashList.addAll(cashList);
                            bestHeight = webhookPushBody.getBestHeight();
                        }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            deleteFile(file);
            i++;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            if(bestHeight!=0)jedis.set(addSidBriefToName(sid, ORDER_LAST_HEIGHT), String.valueOf(bestHeight));
        }
        allCashList.removeIf(cash -> cash.getBirthHeight() < initLastHeight);

        return allCashList;
    }

    private static void deleteFile(File file) {
        if(!file.delete()){
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException ignore) {
            }
            if(!file.delete()){
                throw new RuntimeException("Failed to delete: "+ file.getName());
            }
            System.out.println("File deleted.");
        }
    }

    protected List<Cash> getNewCashListFromJedis(long lastHeight, JedisPool jedisPool) {

        try(Jedis jedis = jedisPool.getResource()){
            String newCashesKey = addSidBriefToName(sid,NEW_CASHES);
            String cashListStr = jedis.get(newCashesKey);
            Type t = new TypeToken<ArrayList<Cash>>() {}.getType();
            List<Cash> cashList= new Gson().fromJson(cashListStr, t);
            jedis.del(newCashesKey);
            cashList.removeIf(cash -> cash.getBirthHeight() > lastHeight || !cash.isValid());
            return cashList;
        }
    }

    protected void getValidOrderList(List<Cash> cashList) {
        System.out.println("Got new cashes. Check valid order...");
        try(Jedis jedis0Common = jedisPool.getResource()) {
            ArrayList<Order> orderList = getNewOrderList(cashList);
            if (orderList.size() == 0) return;

            Map<String, OrderInfo> opReturnOrderInfoMap;

            ArrayList<String> txidList = getTxIdList(orderList);
            opReturnOrderInfoMap = getOpReturnOrderInfoMap(txidList);

            Iterator<Order> iter = orderList.iterator();
            while (iter.hasNext()){
                Order order = iter.next();
                OrderInfo orderInfo = opReturnOrderInfoMap.get(order.getTxId());
                if(orderInfo==null)continue;
                if(orderInfo.isIgnored()){
                    iter.remove();
                    continue;
                }
                String via = orderInfo.getVia();
                if (via != null) order.setVia(via);
            }

            ArrayList<String> orderIdList = new ArrayList<>();
            for (Order order : orderList) {
                String payer = order.getFromFid();
                if (payer != null) {
                    long balance = readHashLong(jedis0Common, Settings.addSidBriefToName(sid, FieldNames.BALANCE), payer);
                    jedis0Common.hset(Settings.addSidBriefToName(sid, FieldNames.BALANCE), payer, String.valueOf(balance + order.getAmount()));
                } else continue;

                String via = order.getVia();
                if (via != null) {
                    order.setVia(via);
                    long viaT = readHashLong(jedis0Common, Settings.addSidBriefToName(sid, FieldNames.ORDER_VIA), via);
                    jedis0Common.hset(Settings.addSidBriefToName(sid, FieldNames.CONSUME_VIA), via, String.valueOf(viaT + order.getAmount()));
                }

                log.debug("New order from [" + order.getFromFid() + "]: " + utils.FchUtils.satoshiToCoin(order.getAmount()) + "f.");

                orderIdList.add(order.getOrderId());
            }
            try {
                String index = addSidBriefToName(sid,ORDER).toLowerCase();
                EsUtils.bulkWriteList(esClient, index, orderList, orderIdList, Order.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    protected ArrayList<String> getTxIdList(ArrayList<Order> orderList) {
        ArrayList<String> txIdList = new ArrayList<>();
        for(Order order :orderList){
            txIdList.add(order.getTxId());
        }
        return txIdList;
    }

    protected ArrayList<Order> getNewOrderList(List<Cash> cashList) {
        double minPayment = Double.parseDouble(this.minPayment);
        long minPaymentLong = (long) minPayment * 100000000;

        ArrayList<Order> orderList = new ArrayList<>();

        Iterator<Cash> iterator = cashList.iterator();
        while (iterator.hasNext()) {
            Cash cash = iterator.next();
            if (cash.getValue() < minPaymentLong) {
                iterator.remove();
                continue;
            }

            String issuer = cash.getIssuer();
            if(issuer.equals(account) ||"999".equals(FchUtils.getLast3(cash.getValue()))){
                iterator.remove();
                continue;
            }

            Order order = new Order();
            order.setOrderId(cash.getId());
            order.setFromFid(cash.getIssuer());
            order.setAmount(cash.getValue());
            order.setHeight(cash.getBirthHeight());
            order.setTime(cash.getBirthTime());
            order.setToFid(cash.getOwner());
            order.setTxId(cash.getBirthTxId());
            order.setTxIndex(cash.getBirthTxIndex());

            orderList.add(order);
        }
        return orderList;
    }

    protected Map<String, OrderInfo> getOpReturnOrderInfoMap(ArrayList<String> txidList) {

        Map<String, OrderInfo> validOrderInfoMap = new HashMap<>();
        EsUtils.MgetResult<OpReturn> result1 = new EsUtils.MgetResult<>();

        try {
            result1 = EsUtils.getMultiByIdList(esClient, IndicesNames.OPRETURN, txidList, OpReturn.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (result1.getResultList() == null || result1.getResultList().size() == 0) return validOrderInfoMap;

        List<OpReturn> opReturnList = result1.getResultList();

        for (OpReturn opReturn : opReturnList) {
            try {
                String goodOp = JsonUtils.strToJson(opReturn.getOpReturn());
                OrderOpReturn orderOpreturn = gson.fromJson(goodOp, OrderOpReturn.class);
                if(orderOpreturn==null)continue;
                OrderInfo orderInfo = new OrderInfo();
                orderInfo.setId(opReturn.getId());
                if(orderOpreturn.getType().equals("apip")
                        && orderOpreturn.getSn().equals("0")
                        && orderOpreturn.getData().getOp().equals(Strings.IGNORE)){
                    orderInfo.setIgnored(true);
                }else if (orderOpreturn.getType().equals("apip")
                        && orderOpreturn.getSn().equals("0")
                        && orderOpreturn.getData().getOp().equals(Values.BUY)
                        && orderOpreturn.getData().getSid().equals(sid)
                ) {
                    orderInfo.setVia(orderOpreturn.getData().getVia());
                }
                validOrderInfoMap.put(opReturn.getId(), orderInfo);
            } catch (Exception ignored) {
//                e.printStackTrace();
//                throw new RuntimeException(e);
            }
        }
        return validOrderInfoMap;
    }

    protected void setLastOrderInfoToRedis(List<Cash> cashList) {
        long lastHeight = 0;
        String lastBlockId = null;
        for (Cash cash : cashList) {
            if (cash.getBirthHeight() > lastHeight) {
                lastHeight = cash.getBirthHeight();
                lastBlockId = cash.getBirthBlockId();
            }
        }
        try(Jedis jedis0Common = jedisPool.getResource()) {
            jedis0Common.set(Settings.addSidBriefToName(sid,ORDER_LAST_HEIGHT), String.valueOf(lastHeight));
            jedis0Common.set(Settings.addSidBriefToName(sid,Strings.ORDER_LAST_BLOCK_ID), lastBlockId);
        }
    }

    protected List<Cash> getNewCashListFromApip(long lastHeight, String account, ApipClient apipClient) {

        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewRange().addNewFields(BIRTH_HEIGHT).addGt(String.valueOf(lastHeight));
        fcdsl.addSort(BIRTH_HEIGHT, Values.DESC).addSort(FieldNames.ID, Values.ASC);
        fcdsl.addNewFilter().addNewTerms().addNewFields(OWNER).addNewValues(account);
        fcdsl.addNewExcept().addNewTerms().addNewFields(ACTIVE).addNewValues(FALSE);
        fcdsl.setSize(String.valueOf(3000));
        return apipClient.cashSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
    }

    protected List<Cash> getNewCashListFromEs(long lastHeight, String account, ElasticsearchClient esClient) {
        List<Cash> cashList = null;
        try {
            cashList = EsUtils.rangeGt(
                    esClient,
                    IndicesNames.CASH,
                    BIRTH_HEIGHT,
                    lastHeight,
                    FieldNames.ID,
                    SortOrder.Asc,
                    OWNER,
                    account,
                    Cash.class);
            if (cashList==null || cashList.size() == 0) return null;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cashList;
    }

    static class OrderInfo {
        protected String id;
        protected String via;
        protected boolean ignored;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getVia() {
            return via;
        }

        public void setVia(String via) {
            this.via = via;
        }

        public boolean isIgnored() {
            return ignored;
        }

        public void setIgnored(boolean ignored) {
            this.ignored = ignored;
        }
    }

    public void close() {
        running.set(false);
    }
    public void restart(){
        running.set(true);
    }

    public AtomicBoolean getRunning() {
        return running;
    }

    public void setRunning(AtomicBoolean running) {
        this.running = running;
    }

    public ElasticsearchClient getEsClient() {
        return esClient;
    }

    public ApipClient getApipClient() {
        return apipClient;
    }

    public JedisPool getJedisPool() {
        return jedisPool;
    }

    public Gson getGson() {
        return gson;
    }

    public String getAccount() {
        return account;
    }

    public String getMinPayment() {
        return minPayment;
    }

    public String getListenPath() {
        return listenPath;
    }

    public boolean isFromWebhook() {
        return fromWebhook;
    }

    public List<ApiAccount> getPaidApiAccountList() {
        return paidApiAccountList;
    }
}

