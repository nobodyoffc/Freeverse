package server.reward;

import fcData.FcReplier;
import fch.TxCreator;
import fch.Wallet;
import feip.feipData.serviceParams.Params;
import clients.apipClient.ApipClient;
import apip.apipData.Sort;
import fch.ParseTools;
import fch.fchData.*;
import feip.feipData.DataOnChain;
import fch.Inputer;
import appTools.Menu;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import configure.ApiAccount;
import javaTools.JsonTools;
import javaTools.NumberTools;
import nasa.NaSaRpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.Settings;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static constants.FieldNames.*;
import static constants.Constants.*;
import static constants.Strings.*;
import static server.Settings.addSidBriefToName;

public class Rewarder {
    private static final Logger log = LoggerFactory.getLogger(Rewarder.class);
    private final ElasticsearchClient esClient;
    private ApipClient apipClient;
    private NaSaRpcClient naSaRpcClient;
    private final JedisPool jedisPool;
    private String account;
    private String lastOrderId;
    private long paidSum;
    private long bestHeight;
    private Map<String, Long> pendingMap;
    private final String sid;
    private Map<String, String> unpaidCostMap;

    private final int Cover4Decimal = 10000;

    public Rewarder(String sid,String account,ApipClient apipClient,ElasticsearchClient esClient,NaSaRpcClient naSaRpcClient,JedisPool jedisPool) {
        this.esClient = esClient;
        if(apipClient!=null)this.apipClient=apipClient;
        this.jedisPool = jedisPool;
        this.sid =sid;
        this.account = account;
        this.naSaRpcClient = naSaRpcClient;
        pendingMap = new HashMap<>();
    }

    public static void checkRewarderParams(String sid, Params params, JedisPool jedisPool, BufferedReader br) {
        RewardParams rewardParams = Rewarder.getRewardParams(sid, jedisPool);

        if(rewardParams==null) {
            rewardParams = Rewarder.setRewardParameters(sid, params.getConsumeViaShare(), params.getOrderViaShare(),jedisPool,br);
            writeRewardParamsToRedis(sid,rewardParams,jedisPool);
        }

        if(rewardParams.getOrderViaShare()==null){
            rewardParams.setOrderViaShare(params.getOrderViaShare());
            writeRewardParamsToRedis(sid,rewardParams,jedisPool);
        }

        if(rewardParams.getConsumeViaShare()==null){
            rewardParams.setConsumeViaShare(params.getConsumeViaShare());
            writeRewardParamsToRedis(sid,rewardParams,jedisPool);
        }

        System.out.println("Check the reward parameters:");
        JsonTools.printJson(rewardParams);
        Menu.anyKeyToContinue(br);
    }
    public RewardInfo doReward(List<ApiAccount> chargedAccountList,byte[] priKey) {

        Wallet wallet = new Wallet(apipClient,esClient,naSaRpcClient);
        bestHeight = wallet.getBestHeight();

        ArrayList<Sort> sortList = new ArrayList<>();
        Sort sort1 = new Sort(VALUE, ASC);
        Sort sort2 = new Sort(CASH_ID, ASC);
        sortList.add(sort1);
        sortList.add(sort2);
        List<Cash> cashList;

        cashList = wallet.getAllCashList(account, true, 0, sortList, null);
        if (cashList == null || cashList.isEmpty()) {
            log.debug("No cash.");
            return null;
        }

        Cash.checkImmatureCoinbase(cashList, bestHeight);

        if (cashList.size() > 200) {
            wallet.mergeCashList(cashList, priKey);
            try {
                TimeUnit.SECONDS.sleep(600);
            } catch (InterruptedException ignore) {
            }
            cashList = wallet.getAllCashList(account, true, 0, sortList, null);
        }

        long total = Cash.sumCashValue(cashList);
        log.debug("Ready to reward "+ParseTools.satoshiToCoin(total)+" F from "+cashList.get(0).getOwner()+"...");
        double sumApiMinPaymentDouble = sumApiMinPayment(chargedAccountList);
        long sumApiMinPayment = ParseTools.coinToSatoshi(sumApiMinPaymentDouble);
        total -= sumApiMinPayment;

        if (total < 0) {
            log.debug("The balance is insufficient to send rewards.");
            return null;
        }

        Map<String, String> orderViaMap;
        Map<String, String> consumeViaMap;
        Map<String, String> builderShareMap;
        Map<String, String> costMap;
        try (Jedis jedis = jedisPool.getResource()) {
            orderViaMap = jedis.hgetAll(Settings.addSidBriefToName(sid, ORDER_VIA));
            consumeViaMap = jedis.hgetAll(Settings.addSidBriefToName(sid, CONSUME_VIA));
            builderShareMap = jedis.hgetAll(Settings.addSidBriefToName(sid, BUILDER_SHARE_MAP));
            costMap = jedis.hgetAll(Settings.addSidBriefToName(sid, COST_MAP));
        } catch (Exception e) {
            log.error("Getting {},{},{} or {} from redis wrong.", Settings.addSidBriefToName(sid, ORDER_VIA), Settings.addSidBriefToName(sid, CONSUME_VIA), Settings.addSidBriefToName(sid, BUILDER_SHARE_MAP), Settings.addSidBriefToName(sid, COST_MAP), e);
            return null;
        }

        String opReturn = makeRewardOpReturn();

        int sendToCount = 0;
        if (orderViaMap != null) sendToCount += orderViaMap.size();
        if (consumeViaMap != null) sendToCount += consumeViaMap.size();
        if (builderShareMap != null) sendToCount += builderShareMap.size();
        if (costMap != null) sendToCount += costMap.size();

        long fee = TxCreator.calcTxSize(cashList.size(), sendToCount, opReturn.length());
        total -= fee;

        RewardParams rewardParams = getRewardParams(sid, jedisPool);

        pendingMap = getPendingMapFromRedis(jedisPool);

        RewardInfo rewardInfo = makeRewardInfo(total, rewardParams);

        Map<String, SendTo> sendToMap = makeSendToMap(rewardInfo);

        pendingDust(sendToMap, jedisPool);
        addQualifiedPendingToPay(sendToMap);
        backUpPending();

        double feeRate = wallet.getFeeRate();
        String txSigned = TxCreator.createTransactionSignFch(cashList, priKey, sendToMap.values().stream().toList(), opReturn, feeRate);

        FcReplier fcReplier = Wallet.sendTx(txSigned, apipClient, naSaRpcClient);
        if (fcReplier.getCode() != 0) {
            log.debug("The balance is insufficient to send rewards.");
            if (fcReplier.getData() != null) log.debug((String) fcReplier.getData());
            return null;
        }
        rewardInfo.setRewardId((String) fcReplier.getData());
        rewardInfo.setState(RewardState.paid);

        long apiCost = sumApiCost(chargedAccountList);
        rewardInfo.setApiCost(apiCost);
        resetApiCost(chargedAccountList);

        if (esClient != null)
            backUpRewardInfo(rewardInfo, esClient);

        return rewardInfo;
    }

    public Map<String, Long> getPendingMapFromRedis(JedisPool jedisPool) {
        try(Jedis jedis = jedisPool.getResource()) {
            Map<String, String> pendingStrMap = jedis.hgetAll(Settings.addSidBriefToName(sid,REWARD_PENDING_MAP));
            for (String key : pendingStrMap.keySet()) {
                Long amount = Long.parseLong(pendingStrMap.get(key));
                pendingMap.put(key, amount);
            }
        }
        return pendingMap;
    }
    public void pendingDust(Map<String, SendTo> sendToMap,JedisPool jedisPool) {
        Iterator<Map.Entry<String, SendTo>> iterator = sendToMap.entrySet().iterator();
        try(Jedis jedis = jedisPool.getResource()) {
            while (iterator.hasNext()) {
                Map.Entry<String, SendTo> entry = iterator.next();
                SendTo sendTo = entry.getValue();
                String fid = sendTo.getFid();
                double amount = sendTo.getAmount();
                if (amount < MinPayValue) {
                    addToPending(fid, ParseTools.coinToSatoshi(amount), jedis);
                    iterator.remove();
                }
            }
        }
    }
    private void addQualifiedPendingToPay(Map<String, SendTo> sendToMap) {
        if(pendingMap==null || pendingMap.isEmpty())return;
        for(String key: pendingMap.keySet()){
            double amount = ParseTools.satoshiToCoin(pendingMap.get(key));
            SendTo sendTo = new SendTo();
            if (amount >= MinPayValue){
                if(sendToMap.get(key)!=null){
                    sendTo = sendToMap.get(key);
                    sendTo.setFid(key);
                    sendTo.setAmount(sendTo.getAmount()+amount);
                }else {
                    sendTo.setFid(key);
                    sendTo.setAmount(amount);
                }
                sendToMap.put(key,sendTo);
                pendingMap.remove(key);
            }
        }
    }
    public void addToPending(String fid, Long amount,Jedis jedis) {
        Long pendingValue = 0L;

        pendingValue = Long.parseLong(jedis.hget(Settings.addSidBriefToName(sid,REWARD_PENDING_MAP), fid));

        if(pendingMap.get(fid)!=null) pendingValue += pendingMap.get(fid);
        pendingMap.put(fid,pendingValue+ amount);
    }


    public static double sumApiMinPayment(List<ApiAccount> chargedAccountList) {
        if(chargedAccountList==null||chargedAccountList.isEmpty())return 0;
        long sum = 0;
        for(ApiAccount apiAccount :chargedAccountList){
            if(apiAccount.getMinPayment()!=null)
                sum+=apiAccount.getMinPayment();
        }
        return sum;
    }

    private String makeRewardOpReturn() {
        String opReturnJson;
        DataOnChain dataOnChain = new DataOnChain();
        dataOnChain.setType(FBBP);
        dataOnChain.setSn("1");
        dataOnChain.setVer("1");

        RewardData rewardData = new RewardData();

        rewardData.setOp(REWARD);
        rewardData.setSid(sid);
        dataOnChain.setData(rewardData);

        opReturnJson = JsonTools.toJson(dataOnChain);
        return opReturnJson;
    }


    private static long sumApiCost(List<ApiAccount> chargedAccountList) {
        if(chargedAccountList==null || chargedAccountList.size()==0)return 0;
        long apiCost = 0;
        for(ApiAccount apiAccount: chargedAccountList){
            if(apiAccount.getPayments()==null)continue;
            for(String key:apiAccount.getPayments().keySet()){
                double paid = apiAccount.getPayments().get(key);
                apiCost += ParseTools.coinToSatoshi(paid);
            }
        }
        return apiCost;
    }
    private static void resetApiCost(List<ApiAccount> chargedAccountList) {
        if(chargedAccountList==null || chargedAccountList.size()==0)return;
        for(ApiAccount apiAccount: chargedAccountList){
            apiAccount.setPayments(new HashMap<>());
        }
    }

    private void backUpPending() {
        Map<String ,String > pendingStrMap = new HashMap<>();
        for(String key: pendingMap.keySet()){
            String amountStr = String.valueOf(pendingMap.get(key));
            pendingStrMap.put(key,amountStr);
        }
        try(Jedis jedis = jedisPool.getResource()) {
            jedis.hmset(addSidBriefToName(sid,REWARD_PENDING_MAP),pendingStrMap);
        }catch (Exception e){
            log.error("Write pending map into redis wrong.");
        }
    }

    private Map<String, SendTo> makeNoDustSendToMap(RewardInfo rewardInfo) {
        Map<String, SendTo> sendToMap = makeSendToMap(rewardInfo);
        sendToMap.entrySet().removeIf(entry -> entry.getValue().getAmount() < MinPayValue);
        return sendToMap;
    }
    public static HashMap<String,SendTo> makeSendToMap(RewardInfo rewardInfo) {
        HashMap<String,SendTo> sendToMap= new HashMap<>();

        ArrayList<Payment> buildList = rewardInfo.getBuilderList();
        ArrayList<Payment> costList = rewardInfo.getCostList();
        ArrayList<Payment> consumeViaList = rewardInfo.getConsumeViaList();
        ArrayList<Payment> orderViaList = rewardInfo.getOrderViaList();

        makePayDetailListIntoSendToMap(sendToMap,orderViaList);
        makePayDetailListIntoSendToMap(sendToMap,consumeViaList);
        makePayDetailListIntoSendToMap(sendToMap,costList);
        makePayDetailListIntoSendToMap(sendToMap,buildList);

        return sendToMap;
    }

    public static void makePayDetailListIntoSendToMap(HashMap<String,SendTo> sendToMap,ArrayList<Payment> payDetailList) {

        for(Payment payDetail:payDetailList){
            SendTo sendTo = new SendTo();
            String fid = payDetail.getFid();
            sendTo.setFid(fid);
            double amount = (double) payDetail.getAmount() /FchToSatoshi;
            if(sendToMap.get(fid)!=null){
                amount = amount+ sendToMap.get(fid).getAmount();
                sendTo.setAmount(NumberTools.roundDouble8(amount));
            }else{
                sendTo.setAmount(NumberTools.roundDouble8(amount));
            }
            sendToMap.put(sendTo.getFid(),sendTo);
        }
    }

    public static RewardParams getRewardParams(String sid, JedisPool jedisPool) {
        RewardParams rewardParams = new RewardParams();
        try(Jedis jedis = jedisPool.getResource()) {
            try {
                Map<String, String> shareMap = jedis.hgetAll(Settings.addSidBriefToName(sid,BUILDER_SHARE_MAP));
                rewardParams.setBuilderShareMap(shareMap);
                if (shareMap.isEmpty()) return null;
            } catch (Exception e) {
                System.out.println("Get builder's shares from redis failed. It's required for rewarding.");
                return null;
            }

            try {
                Map<String, String> costMap = jedis.hgetAll(Settings.addSidBriefToName(sid,COST_MAP));
                rewardParams.setCostMap(costMap);

                rewardParams.setOrderViaShare(jedis.hget(Settings.addSidBriefToName(sid,PARAMS), ORDER_VIA_SHARE));

                rewardParams.setConsumeViaShare(jedis.hget(Settings.addSidBriefToName(sid,PARAMS), CONSUME_VIA_SHARE));

            } catch (Exception ignore) {
            }
        }
        return rewardParams;
    }

    public RewardInfo makeRewardInfo(long incomeT, RewardParams rewardParams) {
        RewardInfo rewardInfo = new RewardInfo();

        try(Jedis jedis = jedisPool.getResource()) {
            Map<String, String> orderViaMap;
            Map<String, String> consumeViaMap;
            Map<String, String> builderShareMap;
            Map<String, String> costMap;

            try {
                orderViaMap = jedis.hgetAll(Settings.addSidBriefToName(sid,ORDER_VIA));
                consumeViaMap = jedis.hgetAll(Settings.addSidBriefToName(sid,CONSUME_VIA));
                unpaidCostMap = jedis.hgetAll(Settings.addSidBriefToName(sid,UNPAID_COST));
                builderShareMap = rewardParams.getBuilderShareMap();//jedis.hgetAll(Settings.addSidBriefToName(sid,BUILDER_SHARE_MAP));
                costMap = rewardParams.getCostMap();//jedis.hgetAll(Settings.addSidBriefToName(sid,COST_MAP));
            } catch (Exception e) {
                log.error("Getting {},{},{} or {} from redis wrong.", Settings.addSidBriefToName(sid,ORDER_VIA), Settings.addSidBriefToName(sid,CONSUME_VIA), Settings.addSidBriefToName(sid,BUILDER_SHARE_MAP), Settings.addSidBriefToName(sid,COST_MAP), e);
                return null;
            }

            addUnpaidToCostMap(costMap, unpaidCostMap);

            Integer orderViaShare = parseViaShare(rewardParams.getOrderViaShare(), Settings.addSidBriefToName(sid,ORDER_VIA));
            Integer consumeViaShare = parseViaShare(rewardParams.getConsumeViaShare(), Settings.addSidBriefToName(sid,CONSUME_VIA));
            if (orderViaShare < 0) return null;
            if (consumeViaShare < 0) return null;

            ArrayList<Payment> orderViaRewardList = makeViaPayList(orderViaMap, orderViaShare, Settings.addSidBriefToName(sid,ORDER_VIA));
            ArrayList<Payment> consumeViaRewardList = makeViaPayList(consumeViaMap, consumeViaShare, Settings.addSidBriefToName(sid, CONSUME_VIA));

            ArrayList<Payment> costList = makeCostPayList(costMap, incomeT,unpaidCostMap);
            if(unpaidCostMap!= null && !unpaidCostMap.isEmpty())jedis.hmset(Settings.addSidBriefToName(sid,UNPAID_COST),unpaidCostMap);

            ArrayList<Payment> builderRewardList = makeBuilderPayList(builderShareMap, incomeT);

            rewardInfo.setOrderViaList(orderViaRewardList);
            rewardInfo.setConsumeViaList(consumeViaRewardList);
            rewardInfo.setBuilderList(builderRewardList);
            rewardInfo.setCostList(costList);

            rewardInfo.setRewardT(paidSum);
            rewardInfo.setState(RewardState.unpaid);
            rewardInfo.setRewardId(String.valueOf(bestHeight));

            rewardInfo.setTime(System.currentTimeMillis());
            rewardInfo.setBestHeight(jedis.get(BEST_HEIGHT));
        }
        return rewardInfo;
    }

    private static void addUnpaidToCostMap(Map<String, String> costMap, Map<String, String> unpaidCostMap) {
        for(String fid: unpaidCostMap.keySet()){
            String unpaid = unpaidCostMap.get(fid);
            String cost = costMap.get(fid);
            if(unpaid!=null){
                if(cost==null) costMap.put(fid,unpaid);
                else {
                    try{
                        double costAmt = Double.parseDouble(cost);
                        double unpaidAmt = Double.parseDouble(unpaid);
                        costMap.put(fid, String.valueOf(costAmt+unpaidAmt));
                        unpaidCostMap.remove(fid);
                    }catch (Exception ignore){
                        log.error("Failed to parse cost or unpaid cost from string to double.");
                    }
                }
            }
        }
    }


    private ArrayList<Payment> makeCostPayList(Map<String, String> costMap, long income, Map<String, String> unpaidCostMap) {
        long costTotal = 0;
        Map<String,Long> costAmountMap = new HashMap<>();
        for(String fid: costMap.keySet()) {
            long amount;
            try {
                amount = (long) (Float.parseFloat(costMap.get(fid))*FchToSatoshi);
            } catch (Exception e) {
                log.error("Get cost of {} from redis wrong.", fid, e);
                return null;
            }

            if(amount+costTotal+paidSum > income){
                if(costTotal+paidSum<income){
                    long payNow = income-costTotal-paidSum;
                    costAmountMap.put(fid, amount);
                    unpaidCostMap.put(fid,String.valueOf(ParseTools.satoshiToCoin(amount-payNow)));
                    amount = payNow;
                }else unpaidCostMap.put(fid,String.valueOf(ParseTools.satoshiToCoin(amount)));
            }else {
                costAmountMap.put(fid, amount);
            }
            costTotal += amount;
        }
        int payPercent = (int) (Cover4Decimal * (income-paidSum)/costTotal);
        if(payPercent > Cover4Decimal)payPercent= Cover4Decimal;
        return payCost(costAmountMap,payPercent);
    }
    private ArrayList<Payment> payCost(Map<String, Long> costAmountMap, int payPercent) {
        ArrayList<Payment> costList = new ArrayList<>();
        for (String fid : costAmountMap.keySet()) {
            Long amount = costAmountMap.get(fid);
            Payment payDetail = new Payment();
            payDetail.setFid(fid);
            payDetail.setFixed(amount);
            long finalPay = amount*payPercent/ Cover4Decimal;
            payDetail.setAmount(finalPay);
            paidSum+=finalPay;
            costList.add(payDetail);
        }
        return costList;
    }


    private ArrayList<Payment> makeBuilderPayList(Map<String, String> builderShareMap, long incomeT) {
        long builderSum = incomeT-paidSum;
        if(builderSum<=0)return null;
        ArrayList<Payment> builderList = new ArrayList<>();
        for (String builder : builderShareMap.keySet()) {
            int share;
            try {
                share = (int) (Float.parseFloat(builderShareMap.get(builder))* Cover4Decimal);
            }catch (Exception ignore){
                log.error("Get builder share of {} from redis wrong.",builder);
                return null;
            }
            long amount = builderSum*share/ Cover4Decimal;

            Payment payDetail = new Payment();
            payDetail.setFid(builder);
            payDetail.setShare(share);
            payDetail.setAmount(amount);
            builderList.add(payDetail);
        }
        paidSum += builderSum;
        return builderList;
    }

    private ArrayList<Payment> makeViaPayList(Map<String, String> viaMap, Integer viaShare, String orderVia) {
        ArrayList<Payment> viaPayDetailList = new ArrayList<>();
        for(String via: viaMap.keySet()){
            long amount;
            try {
                amount = viaShare *Long.parseLong(viaMap.get(via))/ Cover4Decimal;
                Payment payDetail = new Payment();
                payDetail.setFid(via);
                payDetail.setAmount(amount);
                payDetail.setShare(viaShare);
                viaPayDetailList.add(payDetail);

                paidSum += amount;

            }catch (Exception e){
                log.debug("Make {} of {} wrong.",via,orderVia,e);
            }
        }
        return viaPayDetailList;
    }

    private Integer parseViaShare(String viaShareStr, String orderVia) {
        int viaShare;
        try {
            viaShare = (int) (Float.parseFloat(viaShareStr)* Cover4Decimal);
        }catch (Exception e){
            e.printStackTrace();
            log.error("Parsing {} from redis wrong.",orderVia+"Share");
//            throw new RuntimeException();
            return -1;
        }
        return viaShare;
    }

    private boolean backUpRewardInfo(RewardInfo rewardInfo, ElasticsearchClient esClient) {

        try {
            esClient.index(i->i.index(addSidBriefToName(sid,REWARD).toLowerCase()).id(rewardInfo.getRewardId()).document(rewardInfo));
            log.debug("Backup rewardInfo into ES success. BestHeight.");
        } catch (IOException e) {
            log.error("Backup rewardInfo wrong. Check ES.",e);
            return false;
        }

        JsonTools.writeObjectToJsonFile(rewardInfo,REWARD_HISTORY_FILE,true);

        log.debug("Backup rewardInfo into "+REWARD_HISTORY_FILE+" success. BestHeight {}.",rewardInfo.getBestHeight());
        return true;
    }

    public static RewardParams setRewardParameters(String sid, String consumeViaShare, String orderViaShare,JedisPool jedisPool,BufferedReader br) {

        System.out.println("Set reward parameters. \n\tInput numbers like '1.23456789' for an amount of FCH or '0.1234' for a share which means '12.34%'.");
        System.out.println("\tThe reward will be executed once every 10 days. So, the cost is also for 10 days.");
        RewardParams rewardParams = getRewardParams(sid, jedisPool);

        if(rewardParams==null)rewardParams = new RewardParams();
//        Params params = Starter.myService.getParams();
        Double share;
        if(consumeViaShare==null){
            System.out.println("Set consumeViaShare(0~1)");
            share = Inputer.inputGoodShare(br);
            if (share != null) {
                consumeViaShare = String.valueOf(share);
                rewardParams.setConsumeViaShare(consumeViaShare);
            }
        }

        if(orderViaShare==null) {
            System.out.println("Set orderViaShare(0~1)");
            share = Inputer.inputGoodShare(br);
            if (share != null) {
                orderViaShare = String.valueOf(share);
                rewardParams.setOrderViaShare(orderViaShare);
            }
        }

        Map<String, String> costMap = Inputer.inputGoodFidValueStrMap(br, Settings.addSidBriefToName(sid, COST_MAP), false);
        if (costMap != null) {
            rewardParams.setCostMap(costMap);
        }

        Map<String, String> builderShareMap;
        while(true) {
            builderShareMap = Inputer.inputGoodFidValueStrMap(br, Settings.addSidBriefToName(sid, BUILDER_SHARE_MAP),true);

            if(builderShareMap==null ||builderShareMap.isEmpty()){
                System.out.println("BuilderShareMap can't be empty.");
                continue;
            }
            if(!Menu.isFullShareMap(builderShareMap)) continue;
            rewardParams.setBuilderShareMap(builderShareMap);
            break;
        }

        writeRewardParamsToRedis(sid,rewardParams,jedisPool);

        log.debug("Reward parameters were set.");
        return rewardParams;
    }

    private static void writeRewardParamsToRedis(String sid,RewardParams rewardParams,JedisPool jedisPool) {
        try(Jedis jedis = jedisPool.getResource()) {
            if(!rewardParams.getBuilderShareMap().isEmpty())
                jedis.hmset(Settings.addSidBriefToName(sid,BUILDER_SHARE_MAP),rewardParams.getBuilderShareMap());
            if(rewardParams.getCostMap()!=null&&!rewardParams.getCostMap().isEmpty())
                jedis.hmset(Settings.addSidBriefToName(sid,COST_MAP), rewardParams.getCostMap());
        }catch (Exception e){
            log.error("Write rewardParams into redis wrong.",e);
        }
    }

    public String getLastOrderIdFromEs() {
        return lastOrderId;
    }

    public void setLastOrderId(String lastOrderId) {
        this.lastOrderId = lastOrderId;
    }

    public long getBestHeight() {
        return bestHeight;
    }

    public void setBestHeight(long bestHeight) {
        this.bestHeight = bestHeight;
    }
}
