package handlers;

import apip.apipData.Fcdsl;
import appTools.Inputer;
import appTools.Menu;
import appTools.Settings;
import appTools.Shower;
import clients.ApipClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import constants.*;
import crypto.Decryptor;
import crypto.KeyTools;
import fcData.FcEntity;
import fcData.ReplyBody;
import fch.FchMainNetwork;
import utils.IdNameUtils;
import fch.BlockFileUtils;
import fch.FchUtils;
import fch.fchData.Block;
import fch.fchData.Cash;
import fch.fchData.OpReturn;
import fch.fchData.SendTo;
import feip.feipData.Service;
import feip.feipData.serviceParams.Params;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.TalkServer;
import server.rollback.Rollbacker;
import utils.BytesUtils;
import utils.Hex;
import utils.MapQueue;
import utils.NumberUtils;
import utils.ObjectUtils;
import utils.http.AuthType;
import utils.http.RequestMethod;
import utils.EsUtils;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static appTools.Settings.DEALER_MIN_BALANCE;
import static appTools.Settings.LISTEN_PATH;
import static constants.Constants.DEFAULT_DISPLAY_LIST_SIZE;
import static constants.FieldNames.*;
import static constants.Strings.TIME;
import static constants.Strings.VIA;

public class AccountHandler extends Handler<FcEntity> {
    final static Logger log = LoggerFactory.getLogger(AccountHandler.class);
    public static final Long DEFAULT_DEALER_MIN_BALANCE = 100000000L;
    public static final Long DEFAULT_DISTRIBUTE_BALANCE = 100 * 100000000L;
    public static final Long APIP_REQUEST_INTERVAL = 30000L;
    private static final int MAX_USER_BALANCE_CACHE_SIZE = 2;
    private static final int MAX_FID_VIA_CACHE_SIZE = 2;
    private static final int MAX_VIA_BALANCE_CACHE_SIZE = 2;
    // private static final String ORDER_VIA_SHARE = "orderViaShare";
    // private static final String CONSUME_VIA_SHARE = "consumeViaShare";
    public static final String USER_BALANCE = "balance";
    public static final String FID_CONSUME_VIA = "fid_consume_via";
    public static final String VIA_BALANCE = "via_balance";
    public static final String N_PRICE = "n_price";
    public String dbPath;
    private final BufferedReader br;
    private Long myBalance;
    private final MapQueue<String, Long> userBalance;
    private final MapQueue<String, String> fidViaMap;
    private final MapQueue<String, Long> viaBalance;
    private final Map<String, Long> payoffMap;

    // Constructor injected fields
    private final String mainFid;
    private final String sid;
    private final ApipClient apipClient;
    private ElasticsearchClient esClient;
    private final CashHandler cashHandler;
    private Long dealerMinBalance;
    private Long minDistributeBalance;
    private final AccountDB accountDB;
    private final byte[] priKey;
    private String startHeight;

    private final Long priceBase;
    private Map<String, Long> nPriceMap;
    private Double orderViaShare;
    private Double consumeViaShare;
    private Double minPay;
    
    // Add these constants at the class level
    private final String REDIS_KEY_USER_BALANCE;
    private final String REDIS_KEY_FID_CONSUME_VIA;
    private final String REDIS_KEY_VIA_BALANCE;
    // Add these fields
    private final JedisPool jedisPool;
    private final boolean useRedis;

    // Add these constants at the class level
    private static final long AUTO_UPDATE_INTERVAL = 30 * 1000;//3600000*24; // 1 hour in milliseconds
    private Thread distributionThread;
    private AutoScanStrategy autoScanStrategy;

    private String listenPath;

    // Add this enum definition
    public enum AutoScanStrategy {
        NO_AUTO_SCAN("No automatic scanning"),
        ES_CLIENT("Elasticsearch client scanning"),
        APIP_CLIENT("APIP client scanning"),
        WEBHOOK("Webhook notifications"),
        FILE_MONITOR("Monitor file changes");

        private final String description;

        AutoScanStrategy(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    public AccountHandler(TalkServer talkServer){
        super(HandlerType.ACCOUNT);
        this.br = talkServer.getBr();
        this.mainFid = talkServer.getDealer();
        this.priKey = talkServer.getDealerPriKey();
        this.sid = talkServer.getService().getId();
        this.apipClient = talkServer.getApipClient();
        this.cashHandler = talkServer.getCashHandler();
        this.jedisPool = talkServer.getJedisPool();
        this.priceBase = talkServer.getPrice();

        this.useRedis = (jedisPool != null);
        
        // Add dealer min balance input logic
        if(br!=null) {
            Long inputDealerMinBalance = Inputer.inputLong(br, "Input dealer minimum balance (press Enter for default " + DEFAULT_DEALER_MIN_BALANCE / 100000000.0 + " FCH):", null);
            this.dealerMinBalance = inputDealerMinBalance != null ? inputDealerMinBalance : DEFAULT_DEALER_MIN_BALANCE;

            // Add min distribute balance input logic
            Long inputMinDistributeBalance = Inputer.inputLong(br, "Input minimum distribute balance (press Enter for default " + DEFAULT_DISTRIBUTE_BALANCE / 100000000.0 + " FCH):", null);
            this.minDistributeBalance = inputMinDistributeBalance != null ? inputMinDistributeBalance : DEFAULT_DISTRIBUTE_BALANCE;
        }
        // Initialize collections
        if(useRedis){
            this.userBalance = null;
            this.fidViaMap = null;
            this.viaBalance = null;
        }else {
            this.userBalance = new MapQueue<>(MAX_USER_BALANCE_CACHE_SIZE);
            this.fidViaMap = new MapQueue<>(MAX_FID_VIA_CACHE_SIZE);
            this.viaBalance = new MapQueue<>(MAX_VIA_BALANCE_CACHE_SIZE);
        }
        this.payoffMap = new HashMap<>();

        // Initialize storage (Redis or AccountDB)
        this.accountDB = useRedis ? new AccountDB(null, sid, dbPath) : new AccountDB(mainFid, sid, dbPath);
        if(br!=null) {
            accountDB.setDealerMinBalance(this.dealerMinBalance);
            accountDB.setMinDistributeBalance(this.minDistributeBalance);
        }
        // Get start height
        if (useRedis) {
            REDIS_KEY_USER_BALANCE = IdNameUtils.makeKeyName(null,sid, USER_BALANCE,null);
            REDIS_KEY_FID_CONSUME_VIA = IdNameUtils.makeKeyName(null,sid, FID_CONSUME_VIA,null);
            REDIS_KEY_VIA_BALANCE = IdNameUtils.makeKeyName(null,sid, VIA_BALANCE,null);
        } else {
            REDIS_KEY_USER_BALANCE = null;
            REDIS_KEY_FID_CONSUME_VIA = null;
            REDIS_KEY_VIA_BALANCE = null;
        }

        if (br != null) setNPrices(Arrays.stream(talkServer.getChargeType()).toList(),br,false);

        if(accountDB.getLastIncome().isEmpty() && br!=null) {
            this.startHeight = Inputer.inputLongStr(br, "Input the start height by which the order scanning starts. Enter to start from 0:");
        }else this.startHeight=null;

        String orderViaShareStr = talkServer.getTalkParams().getOrderViaShare();
        orderViaShare = Double.parseDouble(orderViaShareStr);
        String consumeViaShareStr = talkServer.getTalkParams().getConsumeViaShare();
        consumeViaShare = Double.parseDouble( consumeViaShareStr);

        // Add auto update input logic after other initializations
        AutoScanStrategy storedStrategy = accountDB.getAutoScanStrategy();
        if (storedStrategy != null) {
            this.autoScanStrategy = storedStrategy;
        } else if (br != null) {
            this.autoScanStrategy = selectAutoScanStrategy(br);
            accountDB.setAutoScanStrategy(this.autoScanStrategy);
        } else {
            this.autoScanStrategy = AutoScanStrategy.NO_AUTO_SCAN;
            accountDB.setAutoScanStrategy(this.autoScanStrategy);
        }

        initShareAndCost(br, orderViaShare, consumeViaShare, false);

        // Load existing listenPath if available
        this.listenPath = accountDB.getListenPath();
        
    }
    public AccountHandler(Settings settings){
        super(HandlerType.ACCOUNT);

        this.br = settings.getBr();
        this.mainFid = settings.getMainFid();
        this.priKey = Decryptor.decryptPriKey(settings.getMyPriKeyCipher(),settings.getSymKey());
        this.sid = settings.getService().getId();
        this.dbPath = settings.getDbDir();
        this.apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);
        this.esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        this.cashHandler = settings.getHandler(Handler.HandlerType.CASH)!=null ?(CashHandler) settings.getHandler(Handler.HandlerType.CASH): new CashHandler(settings);
        this.jedisPool =(JedisPool) settings.getClient(Service.ServiceType.REDIS);
        Params params = ObjectUtils.objectToClass(settings.getService().getParams(), Params.class);
        String priceStr = params.getPricePerKBytes();
        if(priceStr!=null) {
            double price = Double.parseDouble(priceStr);
            this.priceBase = FchUtils.coinToSatoshi(price);
        }else this.priceBase = 0L;
        String orderViaShareStr = params.getOrderViaShare();
        orderViaShare = Double.parseDouble(orderViaShareStr);
        String consumeViaShareStr = params.getConsumeViaShare();
        consumeViaShare = Double.parseDouble( consumeViaShareStr);
        String minPayStr = params.getMinPayment();
        minPay = Double.parseDouble(minPayStr);

        this.useRedis = (jedisPool != null);
        
        // Initialize collections
        if(useRedis){
            this.userBalance = null;
            this.fidViaMap = null;
            this.viaBalance = null;
        }else {
            this.userBalance = new MapQueue<>(MAX_USER_BALANCE_CACHE_SIZE);
            this.fidViaMap = new MapQueue<>(MAX_FID_VIA_CACHE_SIZE);
            this.viaBalance = new MapQueue<>(MAX_VIA_BALANCE_CACHE_SIZE);
        }
        this.payoffMap = new HashMap<>();

        this.accountDB = useRedis ? new AccountDB(null, sid, dbPath) : new AccountDB(mainFid, sid, dbPath);

        // Add dealer min balance input logic
        if(this.accountDB.getDealerMinBalance()!=null) {
            this.dealerMinBalance = this.accountDB.getDealerMinBalance();
        }else if(settings.getSettingMap().get(DEALER_MIN_BALANCE)!=null){
            this.dealerMinBalance = (long)settings.getSettingMap().get(DEALER_MIN_BALANCE);
            accountDB.setDealerMinBalance(this.dealerMinBalance);
        }else if(br!=null){
            Double inputDealerMinBalance = Inputer.inputDouble(br, "dealer minimum balance(FCH)", FchUtils.satoshiToCoin(DEFAULT_DEALER_MIN_BALANCE));
            // Add default value if input is null
            this.dealerMinBalance = inputDealerMinBalance != null ? 
                FchUtils.coinToSatoshi(inputDealerMinBalance) :
                DEFAULT_DEALER_MIN_BALANCE;
            accountDB.setDealerMinBalance(this.dealerMinBalance);
        }else{
            this.dealerMinBalance = DEFAULT_DEALER_MIN_BALANCE;
            accountDB.setDealerMinBalance(this.dealerMinBalance);
        }

        // Add min distribute balance input logic
        if(this.accountDB.getMinDistributeBalance()!=null) {
            this.minDistributeBalance = this.accountDB.getMinDistributeBalance();
        }else if(settings.getSettingMap().get(Settings.MIN_DISTRIBUTE_BALANCE)!=null){
            this.minDistributeBalance = (long)settings.getSettingMap().get(Settings.MIN_DISTRIBUTE_BALANCE);
            accountDB.setMinDistributeBalance(this.minDistributeBalance);
        }else if(br!=null){
            Double inputMinDistributeBalance = Inputer.inputDouble(br, "minimum distribute balance(FCH)" , FchUtils.satoshiToCoin(DEFAULT_DISTRIBUTE_BALANCE));
            // Add default value if input is null
            this.minDistributeBalance = inputMinDistributeBalance != null ?
                FchUtils.coinToSatoshi(inputMinDistributeBalance) :
                DEFAULT_DISTRIBUTE_BALANCE;
            accountDB.setMinDistributeBalance(this.minDistributeBalance);
        }else{
            this.minDistributeBalance = DEFAULT_DISTRIBUTE_BALANCE;
            accountDB.setMinDistributeBalance(this.minDistributeBalance);
        }

        // Get start height
        if (useRedis) {
            REDIS_KEY_USER_BALANCE = IdNameUtils.makeKeyName(null,sid, USER_BALANCE,true);
            REDIS_KEY_FID_CONSUME_VIA = IdNameUtils.makeKeyName(null,sid, FID_CONSUME_VIA,true);
            REDIS_KEY_VIA_BALANCE = IdNameUtils.makeKeyName(null,sid, VIA_BALANCE,true);
        } else {
            REDIS_KEY_USER_BALANCE = null;
            REDIS_KEY_FID_CONSUME_VIA = null;
            REDIS_KEY_VIA_BALANCE = null;
        }

        if(br!=null)setNPrices(settings.getApiList(),br,false);
        else setAllNPricesWithValue(settings.getApiList(), 1L);
        if(accountDB.getLastIncome().isEmpty()) {
            if(br==null)this.startHeight = null;
            else this.startHeight = Inputer.inputLongStr(br, "Input the start height by which the order scanning starts. Enter to start from 0:");
        }else this.startHeight=null;
        initShareAndCost(br, orderViaShare, consumeViaShare, false);

        // Add auto update input logic after other initializations
        AutoScanStrategy storedStrategy = accountDB.getAutoScanStrategy();
        if (storedStrategy != null) {
            this.autoScanStrategy = storedStrategy;
        } else if (br != null) {
            this.autoScanStrategy = selectAutoScanStrategy(br);
            accountDB.setAutoScanStrategy(this.autoScanStrategy);
        } else {
            this.autoScanStrategy = AutoScanStrategy.NO_AUTO_SCAN;
            accountDB.setAutoScanStrategy(this.autoScanStrategy);
        }

        switch(this.autoScanStrategy){
            case ES_CLIENT:
            case APIP_CLIENT:
                break;
            case WEBHOOK:
            case FILE_MONITOR:{
                // Load existing listenPath if available
                this.listenPath = accountDB.getListenPath();
                if(listenPath==null|| listenPath.isEmpty()){
                    this.listenPath = (String) settings.getSettingMap().get(LISTEN_PATH);
                    accountDB.setListenPath(this.listenPath);
                }
                break;
            }
            default:
                this.listenPath = null;
                break;
        }
    }
    public AccountHandler(String mainFid, Service service, String mainFidPriKeyCipher, List<String> apiList,  byte[] symKey, ApipClient apipClient, CashHandler cashHandler,JedisPool jedisPool, BufferedReader br) {
        super(HandlerType.ACCOUNT);

        this.br = br;
        this.mainFid = mainFid;
        this.priKey = Decryptor.decryptPriKey(mainFidPriKeyCipher,symKey);
        this.sid = service.getId();
        this.apipClient = apipClient;
        this.cashHandler = cashHandler;
        this.jedisPool = jedisPool;

        this.useRedis = (jedisPool != null);
        // Initialize collections
        if(useRedis){
        this.userBalance = null;
        this.fidViaMap = null;
        this.viaBalance = null;
        }else {
        this.userBalance = new MapQueue<>(MAX_USER_BALANCE_CACHE_SIZE);
        this.fidViaMap = new MapQueue<>(MAX_FID_VIA_CACHE_SIZE);
        this.viaBalance = new MapQueue<>(MAX_VIA_BALANCE_CACHE_SIZE);
        }
        this.payoffMap = new HashMap<>();

        Params params = (Params) service.getParams();
        String priceStr = params.getPricePerKBytes();
        if(priceStr!=null) {
            double price = Double.parseDouble(priceStr);
            this.priceBase = FchUtils.coinToSatoshi(price);
        }else this.priceBase = 0L;
        String orderViaShareStr = params.getOrderViaShare();
        orderViaShare = Double.parseDouble(orderViaShareStr);
        String consumeViaShareStr = params.getConsumeViaShare();
        consumeViaShare = Double.parseDouble( consumeViaShareStr);

        // Initialize storage and save balance settings
        this.accountDB = useRedis ? new AccountDB(null, service.getId(), dbPath) : new AccountDB(mainFid, service.getId(), dbPath);
        // Add dealer min balance input logic
        if(this.accountDB.getDealerMinBalance()!=null) {
            this.dealerMinBalance = this.accountDB.getDealerMinBalance();
        }else{
            Double inputDealerMinBalance = Inputer.inputDouble(br, "dealer minimum balance(FCH)", FchUtils.satoshiToCoin(DEFAULT_DEALER_MIN_BALANCE));
            this.dealerMinBalance = FchUtils.coinToSatoshi(inputDealerMinBalance);
            accountDB.setDealerMinBalance(this.dealerMinBalance);
        }

        // Add min distribute balance input logic
        if(this.accountDB.getMinDistributeBalance()!=null) {
            this.minDistributeBalance = this.accountDB.getMinDistributeBalance();
        }else{
            Double inputMinDistributeBalance = Inputer.inputDouble(br, "minimum distribute balance(FCH)" , FchUtils.satoshiToCoin(DEFAULT_DISTRIBUTE_BALANCE));
            this.minDistributeBalance = FchUtils.coinToSatoshi(inputMinDistributeBalance);
            accountDB.setMinDistributeBalance(this.minDistributeBalance);
        }

        // Get start height
        if (useRedis) {
            REDIS_KEY_USER_BALANCE = IdNameUtils.makeKeyName(null,sid, USER_BALANCE,true);
            REDIS_KEY_FID_CONSUME_VIA = IdNameUtils.makeKeyName(null,sid, FID_CONSUME_VIA,true);
            REDIS_KEY_VIA_BALANCE = IdNameUtils.makeKeyName(null,sid, VIA_BALANCE,true);
        } else {
            REDIS_KEY_USER_BALANCE = null;
            REDIS_KEY_FID_CONSUME_VIA = null;
            REDIS_KEY_VIA_BALANCE = null;
        }

        setNPrices(apiList,br,false);

        if(accountDB.getLastIncome().isEmpty()) {
            this.startHeight = Inputer.inputLongStr(br, "Input the start height by which the order scanning starts:");
        }else this.startHeight=null;
        initShareAndCost(br, orderViaShare, consumeViaShare, false);

        // Add auto update input logic after other initializations
        AutoScanStrategy storedStrategy = accountDB.getAutoScanStrategy();
        if (storedStrategy != null) {
            this.autoScanStrategy = storedStrategy;
        } else if (br != null) {
            this.autoScanStrategy = selectAutoScanStrategy(br);
            accountDB.setAutoScanStrategy(this.autoScanStrategy);
        } else {
            this.autoScanStrategy = AutoScanStrategy.NO_AUTO_SCAN;
            accountDB.setAutoScanStrategy(this.autoScanStrategy);
        }
    }

    public void menu(BufferedReader br, boolean withSettings) {
        Menu menu = new Menu("Account Management Menu", this::close);
        
        // Replace toggle auto update option with strategy selection
        menu.add("Change Auto Scan Strategy", () -> {
            AutoScanStrategy newStrategy = selectAutoScanStrategy(br);
            autoScanStrategy = newStrategy;
            accountDB.setAutoScanStrategy(newStrategy);
            System.out.println("Auto scan strategy changed to: " + newStrategy.getDescription());
        });
        
        menu.add("Update Income", () -> {
            Map<String, Income> newIncomes = updateIncome();
            if (newIncomes == null) {
                System.out.println("No new incomes found.");
            } else {
                System.out.println("Processed " + newIncomes.size() + " new incomes.");
            }
        });
        
        menu.add("Update Expense", () -> {
            int newExpenses = updateExpense();
            if (newExpenses == -1) {
                System.out.println("Request too frequent. Please wait before trying again.");
            } else {
                System.out.println("Processed " + newExpenses + " new expenses.");
            }
        });
        
        menu.add("Show Income List", () -> {
            Integer incomeSize = Inputer.inputInteger(br, "Enter number of records to show: ", 1, DEFAULT_DISPLAY_LIST_SIZE);
            if(incomeSize==null)return;
            showIncomeList(incomeSize, br);
        });
        
        menu.add("Show Expense List", () -> {
            Integer expenseSize = Inputer.inputInteger(br, "Enter number of records to show: ", 1, DEFAULT_DISPLAY_LIST_SIZE);
            if(expenseSize==null)return;
            showExpenseList(expenseSize, br);
        });

        if(cashHandler!=null)menu.add("Manage My Cashes", cashHandler::menu);
        
        menu.add("Distribute Balance", () -> {
            boolean distributionResult = distribute();
            if (distributionResult) {
                System.out.println("Distribution completed successfully.");
            } else {
                System.out.println("Distribution failed. Insufficient funds or other error.");
            }
        });
        
        menu.add("Settle Payments", () -> {
            boolean settlementResult = settle();
            if (settlementResult) {
                System.out.println("Settlement completed successfully.");
            } else {
                System.out.println("Settlement failed.");
            }
        });
        
        menu.add("Update My Balance", () -> {
            boolean balanceUpdate = updateMyBalance();
            if (balanceUpdate) {
                System.out.println("Current balance: " + myBalance);
            } else {
                System.out.println("Failed to update balance.");
            }
        });
        
        menu.add("Modify Contributor Shares", () -> inputContributorShare(br));
        
        menu.add("Modify Fixed Costs", () -> inputFixedCost(br));
        
        menu.showAndSelect(br);
    }

    

    
    public void start() {
        if (!getIsRunning().get()) {
            getIsRunning().set(true);
            distributionThread = new Thread(() -> {
                while (getIsRunning().get()) {
                    if(autoScanStrategy != AutoScanStrategy.NO_AUTO_SCAN){
                        try {
                            switch (autoScanStrategy) {
                                case ES_CLIENT:
                                    if (esClient != null) {
                                        updateAll();
                                    }
                                    break;
                                case APIP_CLIENT:
                                    if (apipClient != null) {
                                        updateAll();
                                    }
                                    break;
                                case WEBHOOK:
                                    if (listenPath != null) {
                                        FchUtils.waitForNewItemInFile(listenPath);
                                        TimeUnit.SECONDS.sleep(2);
                                        updateAll();
                                    }
                                    break;
                                case FILE_MONITOR:
                                    if (listenPath != null) {
                                        FchUtils.waitForChangeInDirectory(listenPath, getIsRunning());
                                        TimeUnit.SECONDS.sleep(2);
                                        updateAll();
                                    }
                                    break;
                                default:
                                    break;
                            }
                            
                            if (myBalance > minDistributeBalance && distribute()) {
                                settle();
                            }

                            Thread.sleep(AUTO_UPDATE_INTERVAL);
                        } catch (InterruptedException e) {
                            log.error("Distribution thread interrupted", e);
                            Thread.currentThread().interrupt();
                            break;
                        } catch (Exception e) {
                            log.error("Error in distribution thread", e);
                        }
                    }
                }
            });
            distributionThread.setName("Auto-Scan-Thread");
            distributionThread.start();
        }
    }

    /**
     * Performs all periodic update operations in sequence
     */
    public void updateAll() {
        checkAndHandleRollback();
        updateIncome();
        updateExpense();
        updateMyBalance();
        getIsRunning().set(true);
    }

    public Long getPriceByDataTypeName(String dataType) {
        return nPriceMap.get(dataType);
    }

    // Inner classes for Income and Expense
    public static class Income implements Serializable {
        private String cashId;
        private String from;
        private Long value;
        private Long time;
        private Long birthHeight;



        // Constructor and getters/setters
        public Income(String cashId, String from, Long value, Long time, Long birthHeight) {
            this.cashId = cashId;
            this.from = from;
            this.value = value;
            this.time = time;
            this.birthHeight = birthHeight;

        }
        public static LinkedHashMap<String,Integer>getFieldWidthMap(){
            LinkedHashMap<String,Integer> map = new LinkedHashMap<>();
            map.put(CASH_ID,FcEntity.ID_DEFAULT_SHOW_SIZE);
            map.put(FROM,FcEntity.ID_DEFAULT_SHOW_SIZE);
            map.put(VALUE,FcEntity.AMOUNT_DEFAULT_SHOW_SIZE);
            map.put(TIME,FcEntity.TIME_DEFAULT_SHOW_SIZE);
            map.put(BIRTH_HEIGHT,FcEntity.AMOUNT_DEFAULT_SHOW_SIZE);
            return map;
        }
        public String getCashId() {
            return cashId;
        }

        public void setCashId(String cashId) {
            this.cashId = cashId;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public Long getValue() {
            return value;
        }

        public void setValue(Long value) {
            this.value = value;
        }

        public Long getTime() {
            return time;
        }

        public void setTime(Long time) {
            this.time = time;
        }

        public Long getBirthHeight() {
            return birthHeight;
        }

        public void setBirthHeight(Long birthHeight) {
            this.birthHeight = birthHeight;
        }   
    }

    public static class Expense implements Serializable {
        private String cashId;
        private String to;
        private Long value;
        private Long time;
        private Long birthHeight;
        // Constructor and getters/setters
        public Expense(String cashId, String to, Long value, Long time, Long birthHeight) {
            this.cashId = cashId;
            this.to = to;
            this.value = value;
            this.time = time;
            this.birthHeight = birthHeight;
        }
        public static LinkedHashMap<String,Integer>getFieldWidthMap(){
            LinkedHashMap<String,Integer> map = new LinkedHashMap<>();
            map.put(CASH_ID,FcEntity.ID_DEFAULT_SHOW_SIZE);
            map.put(TO,FcEntity.ID_DEFAULT_SHOW_SIZE);
            map.put(VALUE,FcEntity.AMOUNT_DEFAULT_SHOW_SIZE);
            map.put(TIME,FcEntity.TIME_DEFAULT_SHOW_SIZE);
            map.put(BIRTH_HEIGHT,FcEntity.AMOUNT_DEFAULT_SHOW_SIZE);
            return map;
        }
        // Getters and setters

        public String getCashId() {
            return cashId;
        }

        public void setCashId(String cashId) {
            this.cashId = cashId;
        }

        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
        }

        public Long getValue() {
            return value;
        }

        public void setValue(Long value) {
            this.value = value;
        }

        public Long getTime() {
            return time;
        }

        public void setTime(Long time) {
            this.time = time;
        }

        public Long getBirthHeight() {
            return birthHeight;
        }

        public void setBirthHeight(Long birthHeight) {
            this.birthHeight = birthHeight;
        }
    }

    /**
     * Updates myBalance using APIP client
     * @return true if update successful, false otherwise
     */
    public boolean updateMyBalance() {
        // Try APIP client first
        if (apipClient != null) {
            Map<String, Long> balanceMap = apipClient.balanceByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, mainFid);
            if (balanceMap != null && !balanceMap.isEmpty()) {
                myBalance = balanceMap.get(mainFid);
                return true;
            }
        }
        
        // Fallback to ES client if APIP client is not available
        if (esClient != null) {
            try {
                // Create bool query to find valid cashes owned by mainFid
                BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder()
                    .must(m -> m
                        .term(t -> t
                            .field(FieldNames.OWNER)
                            .value(mainFid)
                        )
                    );

                // Build search request
                SearchRequest searchRequest = new SearchRequest.Builder()
                    .index(constants.IndicesNames.CASH)
                    .query(q -> q.bool(boolQueryBuilder.build()))
                    .size(10000) // Set a reasonable limit
                    .build();

                // Execute search
                SearchResponse<Cash> response = esClient.search(searchRequest, Cash.class);
                
                // Sum up all cash values
                long totalBalance = response.hits().hits().stream()
                    .map(Hit::source)
                    .mapToLong(Cash::getValue)
                    .sum();

                myBalance = totalBalance;
                return true;
            } catch (IOException e) {
                log.error("Error searching ES for balance", e);
                return false;
            }
        }
        
        return false;
    }

    private boolean checkAndHandleRollback() {
        try {
            Long lastHeight = accountDB.getLastHeight();
            String lastBlockId = accountDB.getLastBlockId();
            boolean isRolledBack = false;
            
            if (lastHeight == null || lastBlockId == null) {
                return false;
            }
            
            if (apipClient != null) {
                isRolledBack = Rollbacker.isRolledBack(lastHeight, lastBlockId, apipClient);
            } else if (esClient != null) {
                isRolledBack = Rollbacker.isRolledBack(esClient, lastHeight, lastBlockId);
            }
            
            if (isRolledBack) {
                // Rollback detected, adjust height by 30 blocks
                long newHeight = Math.max(0, lastHeight - 30);
                
                // Remove transactions after newHeight
                accountDB.removeIncomeSinceHeight(newHeight);
                accountDB.removeExpenseSinceHeight(newHeight);
                
                // Update height and block ID
                accountDB.setLastHeight(newHeight);
                Block block = BlockFileUtils.getBlockByHeight(esClient, newHeight);
                if (block != null) {
                    accountDB.setLastBlockId(block.getId());
                }
                
                // Clear last income and expense to force rescan from new height
                accountDB.setLastIncome(new ArrayList<>());
                accountDB.setLastExpense(new ArrayList<>());
                this.startHeight=String.valueOf(newHeight);
                return true;
            }
        } catch (IOException e) {
            log.error("Error checking for rollback", e);
        }
        return false;
    }

    public boolean isBadBalance(String senderFid) {
        Long balance = checkUserBalance(senderFid);//getBalanceByFid(Settings.addSidBriefToName(talkServer.getService().getSid(),BALANCE), jedis, senderFid);
        if (balance==null || balance < 0) {
            updateIncome();
            balance = checkUserBalance(senderFid);
            return balance == null || balance < 0;
        }
        return false;
    }
    
        public void persistBalance(){
            accountDB.updateUserBalance(userBalance.getMap());
        }
        /**
         * Adds a value to the user's balance
         * @param userFid The user's FID
         * @param value The value to add
         * @return The new balance
         */
        public Long addUserBalance(String userFid, Long value) {
            if(!KeyTools.isGoodFid(userFid) || value==null)return null;
            if (useRedis) {
                try (var jedis = jedisPool.getResource()) {
                    String currentBalanceStr = jedis.hget(REDIS_KEY_USER_BALANCE, userFid);
                    long currentBalance = currentBalanceStr != null ? Long.parseLong(currentBalanceStr) : 0L;
                    long newBalance = currentBalance + value;
                    
                    if (newBalance <= 0) {
                        jedis.hdel(REDIS_KEY_USER_BALANCE, userFid);
                    } else {
                        jedis.hset(REDIS_KEY_USER_BALANCE, userFid, String.valueOf(newBalance));
                    }
                    return newBalance;
                }
            } else {
                // Existing MapQueue and AccountDB logic
                Long currentBalance = userBalance.get(userFid);
                if (currentBalance == null) {
                    currentBalance = accountDB.getUserBalance(userFid);
                    if(currentBalance == null)
                        currentBalance = 0L;
                    if(userBalance.size() >= MAX_USER_BALANCE_CACHE_SIZE)
                        accountDB.updateUserBalance(userBalance.peek());
                }
                long newBalance = currentBalance + value;
                if (newBalance <= 0) {
                    userBalance.remove(userFid);
                    accountDB.removeUserBalance(userFid);
                } else {
                    userBalance.put(userFid, newBalance);
                }
                return newBalance;
            }
        }

        public Long userSpend(String userFid, String api,Long length) {
            long amount = length / 1000;
            // Get nPrice multiplier
            long nPrice;
            if(api!=null)
                nPrice = getNPrice(api);
            else nPrice=1;
            
            long cost = amount * priceBase * nPrice;
            return addUserBalance(userFid, -cost);
        }
    
        public void addAllBalance(int length, String from, List<String> toFidList, long kbPrice) {
            int kb = (int) Math.ceil(length / 1024.0); // Round up to ensure partial KB counts as 1
            addUserBalance(from,(-1)*(kbPrice*toFidList.size()+1)*kb);
            for(String fid:toFidList){
                addUserBalance(fid,kbPrice*kb);
            }
        }

        public void addAllBalance(Map<String, Long> balanceMap) {
            if(useRedis){
                try (var jedis = jedisPool.getResource()) {
                    // Process each balance individually
                    for (Map.Entry<String, Long> entry : balanceMap.entrySet()) {
                        String fid = entry.getKey();
                        Long addValue = entry.getValue();
                        
                        // Get current balance for this specific FID
                        String currentBalanceStr = jedis.hget(REDIS_KEY_USER_BALANCE, fid);
                        Long currentBalance = currentBalanceStr != null ? 
                            Long.parseLong(currentBalanceStr) : 0L;
                        
                        // Add new value
                        Long newBalance = currentBalance + addValue;
                        
                        // Update Redis
                        if (newBalance <= 0) {
                            jedis.hdel(REDIS_KEY_USER_BALANCE, fid);
                        } else {
                            jedis.hset(REDIS_KEY_USER_BALANCE, fid, String.valueOf(newBalance));
                        }
                    }
                }
            } else {
                // Create a map to collect entries that need to be saved to DB
                Map<String, Long> toSaveDB = new HashMap<>();
                
                // For each balance to add
                for (Map.Entry<String, Long> entry : balanceMap.entrySet()) {
                    String fid = entry.getKey();
                    Long addValue = entry.getValue();
                    
                    // Get current balance from cache or DB
                    Long currentBalance = userBalance.get(fid);
                    if (currentBalance == null) {
                        currentBalance = accountDB.getUserBalance(fid);
                        if (currentBalance == null) {
                            currentBalance = 0L;
                        }
                    }
                    
                    // Add new value
                    Long newBalance = currentBalance + addValue;
                    
                    // If cache is full, collect the oldest entries
                    if (userBalance.size() >= MAX_USER_BALANCE_CACHE_SIZE) {
                        Map.Entry<String, Long> oldest = userBalance.peek();
                        if (oldest != null) {
                            toSaveDB.put(oldest.getKey(), oldest.getValue());
                        }
                    }
                    
                    // Update cache
                    if (newBalance <= 0) {
                        userBalance.remove(fid);
                        accountDB.removeUserBalance(fid);
                    } else {
                        userBalance.put(fid, newBalance);
                    }
                }
                
                // Batch save all collected entries to DB
                if (!toSaveDB.isEmpty()) {
                    accountDB.updateUserBalance(toSaveDB);
                }
            }
        }
    
        public void updateAllBalance(Map<String, Long> balanceMap) {
            if (useRedis) {
                try (var jedis = jedisPool.getResource()) {
                    Map<String, String> balances = balanceMap.entrySet().stream()
                        .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> String.valueOf(e.getValue())
                        ));
                    jedis.hmset(REDIS_KEY_USER_BALANCE, balances);
                }
            } else {
                Map<String, Long> pulledMap = userBalance.putAll(balanceMap);
                Map<String, Long> all = new HashMap<>();
                all.putAll(pulledMap);
                all.putAll(balanceMap);
                accountDB.addUserBalance(all);
            }
        }
    
        /**
         * Checks if a user has sufficient balance
         * @param userFid The user's FID
         * @return The user's balance if greater than 0, null otherwise
         */
        public Long checkUserBalance(String userFid) {
            if (userFid == null) return null;
            
            Long balance = null;
            if (useRedis) {
                try (var jedis = jedisPool.getResource()) {
                    String balanceStr = jedis.hget(REDIS_KEY_USER_BALANCE, userFid);
                    if (balanceStr == null) return null;
                    balance = Long.parseLong(balanceStr);
                    return balance;
                }
            }else{
                // First check current balance
                balance = userBalance.get(userFid);
                if (balance != null && balance > 0) {
                    return balance;
                }
                balance = accountDB.getUserBalance(userFid);

                if(balance == null){
                    Map<String, Income> newIncomes = updateIncome();
                    if (newIncomes != null && !newIncomes.isEmpty()) {
                        balance = checkUserBalance(userFid);
                        if(balance == null)
                            return null;
                    }else{
                        return null;
                    }  
                }
                return balance;
            }
        }
    
        /**
         * Updates or creates an entry in the FID via map
         * @param userFid The user's FID
         * @param viaFid The via FID to map to
         */
        public void updateFidConsumeVia(String userFid, String viaFid) {
            if (userFid == null || viaFid == null || !KeyTools.isGoodFid(userFid) || !KeyTools.isGoodFid(viaFid)) {
                return;
            }
    
            if (useRedis) {
                try (var jedis = jedisPool.getResource()) {
                    jedis.hset(REDIS_KEY_FID_CONSUME_VIA, userFid, viaFid);
                }
            } else {
                if (fidViaMap.get(userFid) == null && fidViaMap.size()>=MAX_FID_VIA_CACHE_SIZE) {
                    accountDB.updateFidConsumeVia(fidViaMap.peek());
                }
                fidViaMap.put(userFid, viaFid);
            }
        }

        public String getFidConsumeVia(String userFid){
            if(useRedis){
                try (var jedis = jedisPool.getResource()) {
                    return jedis.hget(REDIS_KEY_FID_CONSUME_VIA, userFid);
                }
            }
            return fidViaMap.get(userFid);
        }
           /**
         * Updates via balances based on user transactions
         * @param userFid The user's FID
         * @param value The transaction value
         * @param orderVia the via fid if this is an order, null if consumption
         */
        public void addViaBalance(String userFid, Long value, @Nullable String orderVia) {
            if(!KeyTools.isGoodFid(userFid))return;
            if(orderVia!=null && !KeyTools.isGoodFid(orderVia))return;
            String viaFid;
            if(orderVia==null) {
                if (useRedis) {
                    try (var jedis = jedisPool.getResource()) {
                        viaFid = jedis.hget(REDIS_KEY_FID_CONSUME_VIA, userFid);
                    }
                } else {
                    viaFid = fidViaMap.get(userFid);
                    if (viaFid == null) {
                        viaFid = accountDB.getFidVia(userFid);
                        fidViaMap.put(userFid, viaFid);
                    }
                }
            }else{
                viaFid = orderVia;
            }
            if(viaFid==null)return;

            Double sharePercentage = orderVia==null ? accountDB.getConsumeViaShare():accountDB.getOrderViaShare() ;

            if (sharePercentage == null) return;
    
            Long viaShare = (long)(value * sharePercentage);
            
            if (useRedis) {
                try (var jedis = jedisPool.getResource()) {
                    String currentBalanceStr = jedis.hget(REDIS_KEY_VIA_BALANCE, viaFid);
                    long currentBalance = currentBalanceStr != null ? Long.parseLong(currentBalanceStr) : 0L;
                    jedis.hset(REDIS_KEY_VIA_BALANCE, viaFid, String.valueOf(currentBalance + viaShare));
                }
            } else {
                Long currentViaBalance = viaBalance.get(viaFid);
                if (currentViaBalance == null) {
                    currentViaBalance = 0L;
                    Long oldValue = accountDB.getViaBalance(viaFid);
                    if (oldValue != null) currentViaBalance = oldValue;
                    if (viaBalance.size() >= MAX_VIA_BALANCE_CACHE_SIZE)
                        accountDB.updateViaBalance(viaBalance.peek());  
                }
                viaBalance.put(viaFid, currentViaBalance + viaShare);
            }
        }

        /**
         * Adds elements from in-memory maps to the corresponding AccountDB maps
         */
        private void saveMapsToAccountDB() {
            // Add user balances
            accountDB.addUserBalance(userBalance.getMap());
    
            // Add fid via mappings
            accountDB.updateFidVia(fidViaMap.getMap());
    
            // Add via balances
            accountDB.updateViaBalance(viaBalance.getMap());
        }
    
 
        // Add this field at the class level
        private long lastUpdateIncomeTime = 0;
    
        /**
         * Updates income records and related balances
         */
        public Map<String, Income> updateIncome() {
            // Rate limiting check 
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUpdateIncomeTime < APIP_REQUEST_INTERVAL) {
                return null; 
            }
            
            // Update the last execution time
            lastUpdateIncomeTime = currentTime;
            
            // Get last income based on storage type
            List<String> lastIncome;
  
            lastIncome = accountDB.getLastIncome();
            
            // Get new incomes from APIP
            Map<String, Long> userFidValueMap = new HashMap<>();
            Map<String, Income> newIncomeMap = new HashMap<>();
            List<Cash> newCashes=null;
            List<Cash> newOrderCasheList = new ArrayList<>();
            Long lastHeight = null;
            String lastBlockId = null;
            
            while(true){
                if(apipClient!=null){
                    // Create Fcdsl query to get new incomes
                    Fcdsl fcdsl = new Fcdsl();
                    fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.OWNER).addNewValues(mainFid);
                    if((lastIncome==null|| lastIncome.isEmpty()) && startHeight!=null) {
                        fcdsl.getQuery().addNewRange().addNewFields(BIRTH_HEIGHT).addGt(startHeight);
                    }
                    fcdsl.addSort(BIRTH_HEIGHT, Values.ASC).addSort(ID, Values.ASC);
                    fcdsl.addSize(DEFAULT_DISPLAY_LIST_SIZE);
                    if (lastIncome!=null && !lastIncome.isEmpty()) {
                        fcdsl.addAfter(lastIncome);
                    }

                    if (lastIncome!=null && !lastIncome.isEmpty()) {
                        fcdsl.addAfter(lastIncome);
                    }
                    newCashes = apipClient.cashSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
                    ReplyBody responseBody = apipClient.getFcClientEvent().getResponseBody();
                    if(responseBody!=null){
                        lastIncome = responseBody.getLast();
                        lastHeight = responseBody.getBestHeight();
                        lastBlockId = responseBody.getBestBlockId();
                    }
                    if (newCashes == null || newCashes.isEmpty()) {
                        break;
                    }

                }else if (esClient!=null){
                    try {
                    // Create sort options list
                    List<SortOptions> soList = new ArrayList<>();
                    
                    FieldSort fs1 = FieldSort.of(f -> f
                        .field(BIRTH_HEIGHT)
                        .order(SortOrder.Asc));
                    SortOptions so1 = SortOptions.of(s -> s.field(fs1));
                    soList.add(so1);

                    FieldSort fs2 = FieldSort.of(f -> f
                        .field(ID)
                        .order(SortOrder.Asc));
                    SortOptions so2 = SortOptions.of(s -> s.field(fs2));
                    soList.add(so2);

                    // Build the bool query
                    BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder()
                        .must(m -> m
                            .term(t -> t
                                .field(FieldNames.OWNER)
                                .value(mainFid)
                            )
                        );
                    
                    // Add range condition only if lastIncome is empty
                    if ((lastIncome==null || lastIncome.isEmpty()) && startHeight!=null) {
                        boolQueryBuilder.must(m -> m
                            .range(r -> r
                                .field(BIRTH_HEIGHT)
                                .gt(JsonData.of(startHeight))
                            )
                        );
                    }

                    // Build search request
                    SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                        .index(IndicesNames.CASH)
                        .query(q -> q.bool(boolQueryBuilder.build()))
                        .sort(soList)
                        .size(DEFAULT_DISPLAY_LIST_SIZE);

                    if (lastIncome!=null && !lastIncome.isEmpty()) {
                        searchBuilder.searchAfter(lastIncome);
                    }

                    SearchResponse<Cash> response = esClient.search(searchBuilder.build(), Cash.class);

                    if (response.hits().hits().isEmpty()) {
                        break;
                    }

                    newCashes = response.hits().hits().stream()
                        .map(Hit::source)
                        .collect(Collectors.toList());

                    // Update lastIncome with sort values from last hit
                    if (!response.hits().hits().isEmpty()) {
                        lastIncome = response.hits().hits().get(response.hits().hits().size() - 1).sort();
                        
                        // Update last values
                        Cash lastCash = newCashes.get(newCashes.size() - 1);
                        lastHeight = lastCash.getBirthHeight();
                        lastBlockId = lastCash.getBirthBlockId();

                    }
                    } catch (IOException e) {
                        log.error("Error searching ES for cashes", e);
                        break;
                    }
                }else{
                    log.error("No client available to fetch cash data");
                    break;
                }
                if(newCashes.size() < DEFAULT_DISPLAY_LIST_SIZE){
                    break;
                }
            }

            addToNewIncomeAndUserBalance(userFidValueMap, newIncomeMap, newCashes, newOrderCasheList);

            // Store updates based on storage type
            if (!newIncomeMap.isEmpty()) {
                accountDB.updateIncomes(newIncomeMap);
                // Update lastHeight and lastBlockId
                if (lastHeight != null) {
                    accountDB.setLastHeight(lastHeight);
                    accountDB.setLastBlockId(lastBlockId);
                }
                // Display using map values instead of list
                Shower.showDataTable("New Incomes", Income.getFieldWidthMap(),new ArrayList<>(newIncomeMap.values()), Arrays.stream(new String[]{TIME}).toList(), Arrays.stream(new String[]{VALUE}).toList(),null, null, false, br);

            }

            if(!userFidValueMap.isEmpty()){
                addAllBalance(userFidValueMap);
            }

            // Update lastIncome in persistent storage
            accountDB.setLastIncome(lastIncome);

            checkIncomeVia(newOrderCasheList);
            
            return newIncomeMap;
        }

    private static void addToNewIncomeAndUserBalance(Map<String, Long> userFidValueMap, Map<String, Income> newIncomeMap, List<Cash> newCashes, List<Cash> newOrderCasheList) {
        if(newCashes==null||newCashes.isEmpty())return;
        for(Cash cash: newCashes){
            // Skip if issuer is the owner
            if (cash.getIssuer().equals(cash.getOwner())) {
                continue;
            }
            newOrderCasheList.add(cash);
            // Create new income record
            Income income = new Income(
                cash.getId(),
                cash.getIssuer(),
                cash.getValue(),
                cash.getBirthTime(),
                cash.getBirthHeight()
            );

            newIncomeMap.put(cash.getId(), income);

            // Update user balance
            userFidValueMap.merge(cash.getIssuer(), cash.getValue(), Long::sum);
        }
    }

    private void checkIncomeVia(List<Cash> newCashes) {
        if(newCashes==null || newCashes.isEmpty()) return;
        Map<String,String> txIdCashIdMap = new HashMap<>();
        for(Cash cash:newCashes){
            txIdCashIdMap.put(cash.getBirthTxId(), cash.getId());
        }
        Map<String, OpReturn> opReturnMap;
        if (apipClient != null) {
            opReturnMap = apipClient.opReturnByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, txIdCashIdMap.keySet().toArray(new String[0]));
        } else if (esClient != null) {
            try {
                opReturnMap = EsUtils.getOpReturnsByIds(esClient, txIdCashIdMap.keySet());
            } catch (Exception e) {
                log.error("Error getting op returns from ES", e);
                return;
            }
        } else{
            log.error("No client available to fetch OP_RETURN.");
            return;
        }
        if(opReturnMap==null || opReturnMap.isEmpty()) return;
        for(Map.Entry<String, OpReturn> entry:opReturnMap.entrySet()){
            OpReturn opReturn = entry.getValue();
            Map<String, String> opReturnData = ObjectUtils.objectToMap(opReturn.getOpReturn(), String.class, String.class);
            if(opReturnData==null)continue;
            if(opReturnData.containsKey(VIA)){
                List<Cash> matchingCashes = newCashes.stream()
                    .filter(cash -> cash.getBirthTxId().equals(opReturn.getId()))
                    .toList();
                if(matchingCashes.isEmpty())continue;
                for(Cash cash:matchingCashes){
                    addViaBalance(cash.getIssuer(),cash.getValue(),opReturnData.get(VIA));
                }
            }
        }
    }
            
        // Add this field at the class level
    private long lastUpdateExpenseTime = 0;
    
    /**
     * Updates expense records
     */
    public int updateExpense() {
        // Rate limiting check
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateExpenseTime < APIP_REQUEST_INTERVAL) {
            return -1;  // Rate limited
        }

        // Update the last execution time
        lastUpdateExpenseTime = currentTime;
        
        // Get last expense based on storage type
        List<String> lastExpense;
        lastExpense = accountDB.getLastExpense();

        // Get new expenses from APIP
        Map<String, Expense> newExpenseMap = new HashMap<>();
        Long lastHeight = null;
        String lastBlockId = null;

        while(true) {
            List<Cash> newCashes;
            if(apipClient!=null){
                // Create Fcdsl query to get new expenses
                Fcdsl fcdsl = new Fcdsl();
                fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.ISSUER).addNewValues(mainFid);
                if((lastExpense==null || lastExpense.isEmpty()) && startHeight!=null) {
                    fcdsl.getQuery().addNewRange().addNewFields(BIRTH_HEIGHT).addGt(startHeight);
                }
                fcdsl.addSort(BIRTH_HEIGHT, Values.ASC).addSort(FieldNames.ID, Values.ASC);
                fcdsl.addSize(DEFAULT_DISPLAY_LIST_SIZE);
                if (lastExpense!=null && !lastExpense.isEmpty()) {
                    fcdsl.addAfter(lastExpense);
                }
                newCashes = apipClient.cashSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
                if(apipClient.getFcClientEvent().getResponseBody().getLast()!=null)
                    lastExpense = apipClient.getFcClientEvent().getResponseBody().getLast();
                if (newCashes == null || newCashes.isEmpty()) {
                    break;
                }

                for (Cash cash : newCashes) {
                    // Create new expense record
                    Expense expense = new Expense(
                        cash.getId(),
                        cash.getOwner(),
                        cash.getValue(),
                        cash.getBirthTime(),
                        cash.getBirthHeight()
                    );
                    
                    newExpenseMap.put(cash.getId(), expense);
                }
            }else if (esClient!=null){
                try {
                    // Create sort options list
                    List<SortOptions> soList = new ArrayList<>();
                    
                    FieldSort fs1 = FieldSort.of(f -> f
                        .field(BIRTH_HEIGHT)
                        .order(SortOrder.Asc));
                    SortOptions so1 = SortOptions.of(s -> s.field(fs1));
                    soList.add(so1);

                    FieldSort fs2 = FieldSort.of(f -> f
                        .field(ID)
                        .order(SortOrder.Asc));
                    SortOptions so2 = SortOptions.of(s -> s.field(fs2));
                    soList.add(so2);

                    // Build the bool query
                    BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder()
                        .must(m -> m
                            .term(t -> t
                                .field("issuer")
                                .value(mainFid)
                            )
                        );
                    
                    // Add range condition only if lastExpense is empty
                    if (lastExpense==null || lastExpense.isEmpty() && startHeight!=null) {
                        boolQueryBuilder.must(m -> m
                            .range(r -> r
                                .field(BIRTH_HEIGHT)
                                .gt(JsonData.of(startHeight))
                            )
                        );
                    }

                    // Build search request
                    SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                        .index("cash")
                        .query(q -> q.bool(boolQueryBuilder.build()))
                        .sort(soList)
                        .size(DEFAULT_DISPLAY_LIST_SIZE);

                    if (lastExpense!=null && !lastExpense.isEmpty()) {
                        searchBuilder.searchAfter(lastExpense);
                    }

                    SearchResponse<Cash> response = esClient.search(searchBuilder.build(), Cash.class);

                    if (response.hits().hits().isEmpty()) {
                        break;
                    }

                    newCashes = response.hits().hits().stream()
                        .map(Hit::source)
                        .collect(Collectors.toList());

                    // Update lastExpense with sort values from last hit
                    if (!response.hits().hits().isEmpty()) {
                        lastExpense = response.hits().hits().get(response.hits().hits().size() - 1).sort();
                        
                        // Update last values
                        Cash lastCash = newCashes.get(newCashes.size() - 1);
                        lastHeight = lastCash.getBirthHeight();
                        lastBlockId = lastCash.getBirthBlockId();
                    }

                    for (Cash cash : newCashes) {
                        // Create new expense record
                        Expense expense = new Expense(
                            cash.getId(),
                            cash.getOwner(),
                            cash.getValue(),
                            cash.getBirthTime(),
                            cash.getBirthHeight()
                        );
                        
                        newExpenseMap.put(cash.getId(), expense);
                    }
                } catch (IOException e) {
                    log.error("Error searching ES for cashes", e);
                    break;
                }
            }else{
                log.error("No client available to fetch cash data");
                break;
            }
                
            if(newCashes.size() < DEFAULT_DISPLAY_LIST_SIZE){
                break;
            }
        }

        // Store updates based on storage type
        if (!newExpenseMap.isEmpty()) {
            accountDB.updateExpenses(newExpenseMap);
            // Update lastHeight and lastBlockId if available
            if (lastHeight != null) {
                accountDB.setLastHeight(lastHeight);
                accountDB.setLastBlockId(lastBlockId);
            }
            // Display using map values instead of list
            Shower.showDataTable("New Expenses", Income.getFieldWidthMap(),new ArrayList<>(newExpenseMap.values()), Arrays.stream(new String[]{TIME}).toList(), Arrays.stream(new String[]{VALUE}).toList(),null, null, false, br);
        }

        // Update lastExpense in persistent storage
        accountDB.setLastExpense(lastExpense);

        return newExpenseMap.size();  // Return number of new expenses processed
    }
    /**
     * Distributes available balance to various recipients
     * @return true if distribution was successful, false if insufficient funds
     */
    public boolean distribute() {
        // Update current balance
        if (!updateMyBalance()) {
            return false;
        }
        // Calculate available balance for distribution
        long validSum = myBalance - dealerMinBalance;
        if (validSum <= 0) {
            return false;
        }

        // Get pending payoffMap from AccountDB
        payoffMap.putAll(accountDB.getPayoffMap());

        // Process unpaid via balances first
        if (!makePaymentToVia(validSum)) {
            return false;
        }
        validSum = myBalance - dealerMinBalance - calculateTotalPayoff();

        // Process fixed costs
        if (!makePaymentForFixedCost(validSum)) {
            return false;
        }
        validSum = myBalance - dealerMinBalance - calculateTotalPayoff();

        // Distribute remaining balance as dividends
        return makeDividends(validSum);
    }

    private boolean makePaymentToVia(long availableBalance) {
        Map<String, Long> unpaidViaBalance = accountDB.getUnpaidViaBalance();
        
        // Handle unpaid balances first
        long unpaidTotal = unpaidViaBalance.values().stream().mapToLong(Long::longValue).sum();
        if (unpaidTotal > 0) {
            if (availableBalance >= unpaidTotal) {
                payoffMap.putAll(unpaidViaBalance);
                unpaidViaBalance.clear();
                accountDB.clearUnpaidViaBalanceMap();
            } else {
                if (useRedis) {
                    try (var jedis = jedisPool.getResource()) {
                        // Get all via balances from Redis
                        Map<String, String> redisViaBalances = jedis.hgetAll(REDIS_KEY_VIA_BALANCE);
                        // Convert and add to unpaid balances
                        for (Map.Entry<String, String> entry : redisViaBalances.entrySet()) {
                            unpaidViaBalance.put(entry.getKey(), Long.parseLong(entry.getValue()));
                        }
                        accountDB.addUnpaidViaBalance(unpaidViaBalance);
                        unpaidViaBalance.clear();
                        if (!redisViaBalances.isEmpty()) {
                            jedis.del(REDIS_KEY_VIA_BALANCE);
                        }
                        return true;
                    }
                } else {
                    // Get any additional balances from AccountDB
                    Map<String, Long> finalViaBalances = accountDB.getViaBalanceMap();
                    if (finalViaBalances != null && !finalViaBalances.isEmpty()) {
                        unpaidViaBalance.putAll(finalViaBalances);
                    }
                    // Get balances from memory cache
                    if(viaBalance.size()>0)
                        unpaidViaBalance.putAll(viaBalance.getMap());
                    
                    // Clear sources
                    viaBalance.clear();
                    accountDB.clearViaBalanceMap();
                    // Update unpaid balances in storage
                    accountDB.updateUnpaidViaBalance(unpaidViaBalance);
                    return false;
                } 
            }
        }

        // Handle current via balances
        long viaTotal = 0;

        if(useRedis){
            try (var jedis = jedisPool.getResource()) {
                Map<String, String> viaBalanceMap = jedis.hgetAll(REDIS_KEY_VIA_BALANCE);
                Map<String, Long> finalViaBalance = new HashMap<>();
                for(Map.Entry<String, String> entry:viaBalanceMap.entrySet()){
                    finalViaBalance.put(entry.getKey(), Long.parseLong(entry.getValue()));
                    viaTotal += finalViaBalance.get(entry.getKey());
                }
                
                if(availableBalance>=viaTotal){
                    payoffMap.putAll(finalViaBalance);
                    jedis.del(REDIS_KEY_VIA_BALANCE);
                    return true;
                }else{
                    accountDB.updateUnpaidViaBalance(finalViaBalance);
                    return false;
                } 
            }
        }else{
            Map<String, Long> finalViaBalance = accountDB.getViaBalanceMap();
            finalViaBalance.putAll(viaBalance.getMap());
            viaTotal = finalViaBalance.values().stream().mapToLong(Long::longValue).sum();
            if (availableBalance >= viaTotal) {
                payoffMap.putAll(finalViaBalance);
                accountDB.clearViaBalanceMap();
                viaBalance.clear();
                return true;
            } else {
                // Not enough funds, move all to unpaid
                unpaidViaBalance.putAll(finalViaBalance);
                accountDB.updateUnpaidViaBalance(unpaidViaBalance);
                accountDB.clearViaBalanceMap();
                viaBalance.clear();
                return false;
            }
        }
    }

    public void setNPrices(List<String> apiList, BufferedReader br, boolean reset) {
        if(accountDB.hasNPrices() && !reset) {
            if(this.nPriceMap == null) {
                nPriceMap = new HashMap<>();
                Map<String, Long> storedPrices = accountDB.getNPriceMap();
                if(storedPrices != null && !storedPrices.isEmpty()) {
                    nPriceMap.putAll(storedPrices);
                }
            }
            return;
        }
        Map<Integer, String> apiMap = apiListToMap(apiList);
        showAllAPIs(apiMap);
        
        while (true) {
            System.out.println("""
                    Set nPrices:
                    \t'a' to set all nPrices,
                    \t'one' to set all nPrices by 1,
                    \t'zero' to set all nPrices by 0,
                    \tan integer to set the corresponding API,
                    \tor 'q' to quit:\s""");
            String str = null;
            try {
                str = br.readLine();
                if ("".equals(str)) str = br.readLine();
                if (str.equals("q")) return;
                if (str.equals("a")) {
                    setAllNPrices(apiMap, br);
                    System.out.println("Done.");
                    return;
                }
            } catch (Exception e) {
                log.error("Set nPrice wrong. ", e);
            }
            if (str == null) {
                log.error("Set nPrice failed. ");
                continue;
            }

            if (str.equals("one")) {
                setAllNPricesWithValue(apiList, 1L);
                return;
            }
            if (str.equals("zero")) {
                setAllNPricesWithValue(apiList, 0L);
                return;
            }
            try {
                int i = Integer.parseInt(str);
                if (i > apiMap.size()) {
                    System.out.println("The integer should be no bigger than " + apiMap.size());
                } else {
                    setNPrice(i, apiMap, br);
                    System.out.println("Done.");
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Wrong input.");
            }
        }
    }

    private void setAllNPricesWithValue(List<String> apiList, long value) {
        Map<Integer, String> apiMap = apiListToMap(apiList);
        for (Map.Entry<Integer, String> entry : apiMap.entrySet()) {
            accountDB.setNPrice(entry.getValue(), value);
            if(nPriceMap == null) nPriceMap = new HashMap<>();
            nPriceMap.put(entry.getValue(), value);
        }
        System.out.println("All API prices are set to "+value+".");
    }

    private void setAllNPrices(Map<Integer, String> apiMap, BufferedReader br) throws IOException {
        for (int i : apiMap.keySet()) {
            setNPrice(i, apiMap, br);
        }
    }

    private void setNPrice(int i, Map<Integer, String> apiMap, BufferedReader br) throws IOException {
        if(this.nPriceMap == null) nPriceMap = new HashMap<>();
        String apiName = apiMap.get(i);
        while (true) {
            try {
                Long n = Inputer.inputLong(br, "Input the multiple number of API " + apiName + ":");
                // Store in local map
                accountDB.setNPrice(apiName, n);
                nPriceMap.put(apiName, n);
                
                // Store in Redis if available
                if (useRedis && jedisPool != null) {
                    try (Jedis jedis = jedisPool.getResource()) {
                        String redisKey = IdNameUtils.makeKeyName(null, sid, N_PRICE, null);
                        jedis.hset(redisKey, apiName, String.valueOf(n));
                    }
                }
                return;
            } catch (Exception e) {
                System.out.println("Wrong input.");
            }
        }
    }

    public static void showAllAPIs(Map<Integer, String> apiMap) {
        System.out.println("API list:");
        for (int i = 1; i <= apiMap.size(); i++) {
            System.out.println(i + ". " + apiMap.get(i));
        }
    }

    public Map<Integer, String> apiListToMap(List<String> apiList) {

        Map<Integer, String> apiMap = new HashMap<>();
        for (int i = 0; i < apiList.size(); i++) apiMap.put(i + 1, apiList.get(i));
        return apiMap;
    }

    public void resetNPrices(BufferedReader br, String sid, JedisPool jedisPool) {
        try(Jedis jedis = jedisPool.getResource()) {
            Map<String, String> nPriceMap = jedis.hgetAll(Settings.addSidBriefToName(sid,Strings.N_PRICE));
            for (String name : nPriceMap.keySet()) {
                String ask = "The price multiplier of " + name + " is " + nPriceMap.get(name) + ". Reset it? y/n";
                int input = Inputer.inputInt(br, ask, 0);
                if (input != 0)
                    jedis.hset(Settings.addSidBriefToName(sid,Strings.N_PRICE), name, String.valueOf(input));
            }
        }
    }

    private boolean makePaymentForFixedCost(long availableBalance) {
        Map<String, Long> fixedCostMap = accountDB.getFixedCostMap();
        Map<String, Long> unpaidFixedCostMap = accountDB.getUnpaidFixedCostMap();
        
        long totalCost = fixedCostMap.values().stream().mapToLong(Long::longValue).sum();
        long unpaidTotal = unpaidFixedCostMap.values().stream().mapToLong(Long::longValue).sum();
        totalCost += unpaidTotal;
        
        if (availableBalance >= totalCost) {
            payoffMap.putAll(fixedCostMap);
            payoffMap.putAll(unpaidFixedCostMap);
            accountDB.clearUnpaidFixedCostMap();
            return true;
        } else {
            // Not enough funds, move to unpaid
            unpaidFixedCostMap.putAll(fixedCostMap);
            accountDB.updateUnpaidFixedCostMap(unpaidFixedCostMap);
            return false;
        }
    }


    private boolean makeDividends(long availableBalance) {
        Map<String, Double> contributorShareMap = accountDB.getContributorShareMap();
        
        for (Map.Entry<String, Double> entry : contributorShareMap.entrySet()) {
            String contributorFid = entry.getKey();
            Double share = entry.getValue();
            long dividend = (long)(availableBalance * share);
            
            if (dividend > 0) {
                payoffMap.merge(contributorFid, dividend, Long::sum);
            }
        }
        
        return true;
    }

    private long calculateTotalPayoff() {
        return payoffMap.values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * Settles all pending payments
     * @return true if settlement was successful, false otherwise
     */
    public boolean settle() {
        if (payoffMap.isEmpty()) {
            return true;
        }
        System.out.println("Settle all payments...");
        // Convert payoffMap to SendTo list
        List<SendTo> sendToList = payoffMap.entrySet().stream()
            .map(entry -> new SendTo(entry.getKey(), FchUtils.satoshiToCoin(entry.getValue())))
            .collect(Collectors.toList());

        // Send payments using CashClient
        String txId;
        if(cashHandler!=null) txId = cashHandler.send(null, null, null, sendToList, null, null, null, null, FchMainNetwork.MAINNETWORK, null);
        else txId = CashHandler.send(priKey,sendToList,apipClient,esClient);
        // Update last settlement height
        Long bestHeight = Settings.getBestHeight(apipClient,null,esClient,jedisPool);
        accountDB.setLastSettleHeight(bestHeight);

        if (!Hex.isHex32(txId)) {
            // Save payoffMap to AccountDB if transaction was successful but couldn't get height
            System.out.println("Failed to get height after settlement, saved payoffMap to AccountDB.");
            accountDB.updatePayoffMap(payoffMap);
            payoffMap.clear();
            return false;
        }

        payoffMap.clear();

        accountDB.clearPayoffMap();
        return true;
    }

    /**
     * Initiates the account with contributor shares, fixed costs, and via share settings
     * @param br BufferedReader for user input
     * @param orderViaShare Share percentage for order via
     * @param consumeViaShare Share percentage for consume via
     */
    public void initShareAndCost(BufferedReader br, Double orderViaShare, Double consumeViaShare, boolean reset) {
        if (!reset) {
            if (!accountDB.getContributorShareMap().isEmpty()) {
                return;
            }
        }

        // Input contributor shares
        System.out.println("Set the share of contributors.");
        accountDB.inputContributorShare(br);

        // Input fixed costs
        System.out.println("Set the fixed costs.");
        accountDB.inputFixedCost(br);

        // Set via shares in Redis or AccountDB

        if (orderViaShare != null)
            accountDB.setOrderViaShare(orderViaShare);
        else{
            Double inputShare = Inputer.inputDouble(br, "Input the order via share percentage:");
            if(inputShare==null)return;
            accountDB.setOrderViaShare(NumberUtils.roundDouble8(inputShare/100.0));
        }

        if (consumeViaShare != null){
            accountDB.setConsumeViaShare(consumeViaShare);
        }else{
            Double inputShare = Inputer.inputDouble(br, "Input the consume via share percentage:");
            if(inputShare==null)return;
            accountDB.setConsumeViaShare(NumberUtils.roundDouble8(inputShare/100));
        }
    }

    public void inputContributorShare(BufferedReader br) {
        accountDB.inputContributorShare(br);
    }

    public void inputFixedCost(BufferedReader br) {
        accountDB.inputFixedCost(br);
    }

    public void close() {
        // Stop the distribution thread
        getIsRunning().set(false);
        if (distributionThread != null) {
            distributionThread.interrupt();
            try {
                distributionThread.join(5000); // Wait up to 5 seconds for thread to finish
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for distribution thread to stop", e);
                Thread.currentThread().interrupt();
            }
        }

        if (useRedis) {
            BytesUtils.clearByteArray(priKey);
        } else {
            saveMapsToAccountDB();
            BytesUtils.clearByteArray(priKey);
            accountDB.close();
        }
    }

    /**
     * Shows income list with pagination
     * @param size Number of records to show per page
     */
    public void showIncomeList(int size, BufferedReader br) {
        Map<String, Income> incomeMap = accountDB.getIncomeMap();
        if (incomeMap == null || incomeMap.isEmpty()) {
            System.out.println("No income records found.");
            return;
        }
        
        // Get income entries from the end (most recent first)
        List<Income> incomes = accountDB.getIncome(size, true);
        if (incomes.isEmpty()) {
            System.out.println("No income records found.");
            return;
        }
        
        int totalIncomes = incomes.size();
        int totalPages = (int) Math.ceil((double) totalIncomes / size);
        int currentPage = 0;
        
        while (true) {
            System.out.println("\n======= Income Records (Page " + (currentPage + 1) + "/" + totalPages + ") =======");
            System.out.printf("%-20s %-15s %-15s %-20s %-15s\n", "ID", "User FID", "Value (FCH)", "Time", "Block Height");
            
            int startIdx = currentPage * size;
            int endIdx = Math.min(startIdx + size, totalIncomes);
            
            for (int i = startIdx; i < endIdx; i++) {
                if (i >= incomes.size()) break;
                
                Income income = incomes.get(i);
                if (income != null) {
                    System.out.printf("%-20s %-15s %-15s %-20s %-15d\n",
                            income.getCashId(),
                            income.getFrom(),
                            FchUtils.satoshiToCoin(income.getValue()),
                            new Date(income.getTime()),
                            income.getBirthHeight());
                }
            }
            
            if (totalPages <= 1) break;
            
            System.out.println("\nN: Next page, P: Previous page, Q: Quit");
            try {
                String input = br.readLine();
                if (input == null || input.equalsIgnoreCase("Q")) {
                    break;
                } else if (input.equalsIgnoreCase("N")) {
                    currentPage = (currentPage + 1) % totalPages;
                } else if (input.equalsIgnoreCase("P")) {
                    currentPage = (currentPage - 1 + totalPages) % totalPages;
                }
            } catch (IOException e) {
                log.error("Error reading input: {}", e.getMessage());
                break;
            }
        }
    }

    /**
     * Shows expense list with pagination
     * @param size Number of records to show per page
     */
    public void showExpenseList(int size, BufferedReader br) {
        Map<String, Expense> expenseMap = accountDB.getExpenseMap();
        if (expenseMap == null || expenseMap.isEmpty()) {
            System.out.println("No expense records found.");
            return;
        }
        
        // Get expense entries from the end (most recent first)
        List<Expense> expenses = accountDB.getExpense(size, true);
        if (expenses.isEmpty()) {
            System.out.println("No expense records found.");
            return;
        }
        
        int totalExpenses = expenses.size();
        int totalPages = (int) Math.ceil((double) totalExpenses / size);
        int currentPage = 0;
        
        while (true) {
            System.out.println("\n======= Expense Records (Page " + (currentPage + 1) + "/" + totalPages + ") =======");
            System.out.printf("%-20s %-15s %-15s %-20s %-15s\n", "ID", "To FID", "Value (FCH)", "Time", "Block Height");
            
            int startIdx = currentPage * size;
            int endIdx = Math.min(startIdx + size, totalExpenses);
            
            for (int i = startIdx; i < endIdx; i++) {
                if (i >= expenses.size()) break;
                
                Expense expense = expenses.get(i);
                if (expense != null) {
                    System.out.printf("%-20s %-15s %-15s %-20s %-15d\n",
                            expense.getCashId(),
                            expense.getTo(),
                            FchUtils.satoshiToCoin(expense.getValue()),
                            new Date(expense.getTime()),
                            expense.getBirthHeight());
                }
            }
            
            if (totalPages <= 1) break;
            
            System.out.println("\nN: Next page, P: Previous page, Q: Quit");
            try {
                String input = br.readLine();
                if (input == null || input.equalsIgnoreCase("Q")) {
                    break;
                } else if (input.equalsIgnoreCase("N")) {
                    currentPage = (currentPage + 1) % totalPages;
                } else if (input.equalsIgnoreCase("P")) {
                    currentPage = (currentPage - 1 + totalPages) % totalPages;
                }
            } catch (IOException e) {
                log.error("Error reading input: {}", e.getMessage());
                break;
            }
        }
    }

    public Map<String, Long> getPayoffMap() {
        return accountDB.getPayoffMap();
    }

    /**
     * Checks if a via has accumulated balance in Redis
     * @param sid The service ID
     * @param viaFid The via FID to check
     * @param jedisPool The Redis connection pool
     * @return The via's balance if greater than 0, null otherwise
     */
    public static Long checkViaBalance(String sid, String viaFid, JedisPool jedisPool) {
        if (jedisPool == null || sid == null || viaFid == null) {
            return null;
        }
        
        try (var jedis = jedisPool.getResource()) {
            String key = IdNameUtils.makeKeyName(null, sid, VIA_BALANCE, null);
            String balanceStr = jedis.hget(key, viaFid);
            if (balanceStr == null) {
                return null;
            }
            return Long.parseLong(balanceStr);
        } catch (Exception e) {
            log.error("Error checking via balance in Redis", e);
            return null;
        }
    }

    /**
     * Gets multiple via balances from Redis
     * @param sid The service ID
     * @param viaFids List of via FIDs to check
     * @param jedisPool The Redis connection pool
     * @return Map of via FIDs to balances, empty map if error
     */
    public static Map<String, Long> checkViaBalances(String sid, List<String> viaFids, JedisPool jedisPool) {
        if (jedisPool == null || sid == null || viaFids == null || viaFids.isEmpty()) {
            return new HashMap<>();
        }

        try (var jedis = jedisPool.getResource()) {
            String key = IdNameUtils.makeKeyName(null, sid, VIA_BALANCE, null);
            List<String> balances = jedis.hmget(key, viaFids.toArray(new String[0]));
            
            Map<String, Long> result = new HashMap<>();
            for (int i = 0; i < viaFids.size(); i++) {
                String balanceStr = balances.get(i);
                if (balanceStr != null) {
                    result.put(viaFids.get(i), Long.parseLong(balanceStr));
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Error checking multiple via balances in Redis", e);
            return new HashMap<>();
        }
    }

    /**
     * Gets all via balances from Redis
     * @param sid The service ID
     * @param jedisPool The Redis connection pool
     * @return Map of all via FIDs to balances, empty map if error
     */
    public static Map<String, Long> checkAllViaBalances(String sid, JedisPool jedisPool) {
        if (jedisPool == null || sid == null) {
            return new HashMap<>();
        }

        try (var jedis = jedisPool.getResource()) {
            String key = IdNameUtils.makeKeyName(null, sid, VIA_BALANCE, null);
            Map<String, String> allBalances = jedis.hgetAll(key);
            
            Map<String, Long> result = new HashMap<>();
            for (Map.Entry<String, String> entry : allBalances.entrySet()) {
                try {
                    result.put(entry.getKey(), Long.parseLong(entry.getValue()));
                } catch (NumberFormatException e) {
                    log.error("Invalid via balance format for FID: " + entry.getKey(), e);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Error checking all via balances in Redis", e);
            return new HashMap<>();
        }
    }

    /**
     * Gets the total via balance
     * @param sid The service ID
     * @param jedisPool The Redis connection pool
     * @return The total via balance, 0 if error
     */
    public static long getTotalViaBalance(String sid, JedisPool jedisPool) {
        if (jedisPool == null || sid == null) {
            return 0L;
        }

        try (var jedis = jedisPool.getResource()) {
            String key = IdNameUtils.makeKeyName(null, sid, VIA_BALANCE, null);
            Map<String, String> allBalances = jedis.hgetAll(key);
            
            return allBalances.values().stream()
                .mapToLong(str -> {
                    try {
                        return Long.parseLong(str);
                    } catch (NumberFormatException e) {
                        log.error("Invalid via balance format", e);
                        return 0L;
                    }
                })
                .sum();
        } catch (Exception e) {
            log.error("Error getting total via balance from Redis", e);
            return 0L;
        }
    }

    public Long getPriceBase() {
        return priceBase;
    }

    public Map<String, Long> getnPriceMap() {
        return nPriceMap;
    }

    public void setnPriceMap(Map<String, Long> nPriceMap) {
        this.nPriceMap = nPriceMap;
    }

    public Long getNPrice(String apiName) {
        return nPriceMap.getOrDefault(apiName, 1L);
    }

    // Add static methods for reading nPrice
    public static Long getNPrice(String sid, String apiName, JedisPool jedisPool) {
        if (jedisPool == null || apiName == null || sid == null) {
            return null;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String redisKey = IdNameUtils.makeKeyName(null, sid, N_PRICE, null);
            String value = jedis.hget(redisKey, apiName);
            return value != null ? Long.parseLong(value) : null;
        } catch (Exception e) {
            log.error("Error reading nPrice from Redis", e);
            return null;
        }
    }

    public static Map<String, Long> getAllNPrices(String sid, JedisPool jedisPool) {
        if (jedisPool == null || sid == null) {
            return null;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String redisKey = IdNameUtils.makeKeyName(null, sid, N_PRICE, null);
            Map<String, String> redisMap = jedis.hgetAll(redisKey);
            
            if (redisMap.isEmpty()) {
                return null;
            }

            Map<String, Long> result = new HashMap<>();
            for (Map.Entry<String, String> entry : redisMap.entrySet()) {
                try {
                    result.put(entry.getKey(), Long.parseLong(entry.getValue()));
                } catch (NumberFormatException e) {
                    log.error("Error parsing nPrice value for key: " + entry.getKey(), e);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Error reading all nPrices from Redis", e);
            return null;
        }
    }

    // Add these static methods after the existing static methods
    
    /**
     * Updates a user's balance in Redis
     * @return The new balance if successful, null if failed
     */
    public static Long updateUserBalance(String sid, String uid, Long value, JedisPool jedisPool) {
        if (jedisPool == null || sid == null || uid == null || value == null) {
            return null;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String key = IdNameUtils.makeKeyName(null, sid, BALANCE, null);
            jedis.hset(key, uid, String.valueOf(value));
            return value;
        } catch (Exception e) {
            log.error("Error updating user balance in Redis", e);
            return null;
        }
    }

    /**
     * Adds to a user's balance in Redis
     * @return The new balance if successful, null if failed
     */
    public static Long addUserBalance(String sid, String uid, Long value, JedisPool jedisPool) {
        if (jedisPool == null || sid == null || uid == null || value == null) {
            return null;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String key = IdNameUtils.makeKeyName(null, sid, BALANCE, null);
            String currentBalanceStr = jedis.hget(key, uid);
            long currentBalance = currentBalanceStr != null ? Long.parseLong(currentBalanceStr) : 0L;
            long newBalance = currentBalance + value;
            
            if (newBalance <= 0) {
                jedis.hdel(key, uid);
                return 0L;
            } else {
                jedis.hset(key, uid, String.valueOf(newBalance));
                return newBalance;
            }
        } catch (Exception e) {
            log.error("Error adding to user balance in Redis", e);
            return null;
        }
    }

    /**
     * Updates multiple user balances in Redis
     * @return true if successful, false if failed
     */
    public static boolean updateUserBalances(String sid, Map<String, Long> balanceMap, JedisPool jedisPool) {
        if (jedisPool == null || sid == null || balanceMap == null || balanceMap.isEmpty()) {
            return false;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String key = IdNameUtils.makeKeyName(null, sid, BALANCE, null);
            Map<String, String> stringBalanceMap = balanceMap.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> String.valueOf(e.getValue())
                ));
            jedis.hmset(key, stringBalanceMap);
            return true;
        } catch (Exception e) {
            log.error("Error updating multiple user balances in Redis", e);
            return false;
        }
    }

    /**
     * Adds to multiple user balances in Redis
     * @return true if successful, false if failed
     */
    public static boolean addUserBalances(String sid, Map<String, Long> valueMap, JedisPool jedisPool) {
        if (jedisPool == null || sid == null || valueMap == null || valueMap.isEmpty()) {
            return false;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String key = IdNameUtils.makeKeyName(null, sid, BALANCE, null);
            
            // Get current balances for all users
            Set<String> userIds = valueMap.keySet();
            List<String> currentBalances = jedis.hmget(key, userIds.toArray(new String[0]));
            Map<String, String> updatedBalances = new HashMap<>();
            
            // Calculate new balances
            int i = 0;
            for (String uid : userIds) {
                String currentBalanceStr = currentBalances.get(i++);
                long currentBalance = currentBalanceStr != null ? Long.parseLong(currentBalanceStr) : 0L;
                long newBalance = currentBalance + valueMap.get(uid);
                
                if (newBalance > 0) {
                    updatedBalances.put(uid, String.valueOf(newBalance));
                }
            }
            
            // Update balances in Redis
            if (!updatedBalances.isEmpty()) {
                jedis.hmset(key, updatedBalances);
            }
            
            // Remove any zero or negative balances
            List<String> toRemove = valueMap.entrySet().stream()
                .filter(e -> !updatedBalances.containsKey(e.getKey()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
            
            if (!toRemove.isEmpty()) {
                jedis.hdel(key, toRemove.toArray(new String[0]));
            }
            
            return true;
        } catch (Exception e) {
            log.error("Error adding to multiple user balances in Redis", e);
            return false;
        }
    }

    /**
     * Merges balances with existing values in Redis
     * @return true if successful, false if failed
     */
    public static boolean mergeUserBalances(String sid, Map<String, Long> valueMap, JedisPool jedisPool) {
        if (jedisPool == null || sid == null || valueMap == null || valueMap.isEmpty()) {
            return false;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String key = IdNameUtils.makeKeyName(null, sid, BALANCE, null);
            Map<String, String> existingBalances = jedis.hgetAll(key);
            Map<String, String> updatedBalances = new HashMap<>();
            
            // Merge existing balances with new values
            for (Map.Entry<String, Long> entry : valueMap.entrySet()) {
                String uid = entry.getKey();
                Long newValue = entry.getValue();
                String existingBalanceStr = existingBalances.get(uid);
                long existingBalance = existingBalanceStr != null ? Long.parseLong(existingBalanceStr) : 0L;
                
                if (newValue > existingBalance) {
                    updatedBalances.put(uid, String.valueOf(newValue));
                }
            }
            
            // Update Redis with merged balances
            if (!updatedBalances.isEmpty()) {
                jedis.hmset(key, updatedBalances);
            }
            return true;
        } catch (Exception e) {
            log.error("Error merging user balances in Redis", e);
            return false;
        }
    }

    /**
     * Gets a user's balance from Redis
     * @return The user's balance if found, null if not found or error
     */
    public static Long getUserBalance(String sid, String uid, JedisPool jedisPool) {
        if (jedisPool == null || sid == null || uid == null) {
            return null;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String key = IdNameUtils.makeKeyName(null, sid, BALANCE, null);
            String balanceStr = jedis.hget(key, uid);
            return balanceStr != null ? Long.parseLong(balanceStr) : null;
        } catch (Exception e) {
            log.error("Error checking user balance in Redis", e);
            return null;
        }
    }

    /**
     * Gets multiple user balances from Redis
     * @return Map of user IDs to balances, empty map if error
     */
    public static Map<String, Long> getUserBalances(String sid, List<String> uids, JedisPool jedisPool) {
        if (jedisPool == null || sid == null || uids == null || uids.isEmpty()) {
            return new HashMap<>();
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String key = IdNameUtils.makeKeyName(null, sid, BALANCE, null);
            List<String> balances = jedis.hmget(key, uids.toArray(new String[0]));
            
            Map<String, Long> result = new HashMap<>();
            for (int i = 0; i < uids.size(); i++) {
                String balanceStr = balances.get(i);
                if (balanceStr != null) {
                    result.put(uids.get(i), Long.parseLong(balanceStr));
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Error checking multiple user balances in Redis", e);
            return new HashMap<>();
        }
    }

    /**
     * Gets all user balances from Redis
     * @return Map of all user IDs to balances, empty map if error
     */
    public static Map<String, Long> getAllUserBalances(String sid, JedisPool jedisPool) {
        if (jedisPool == null || sid == null) {
            return new HashMap<>();
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String key = IdNameUtils.makeKeyName(null, sid, BALANCE, null);
            Map<String, String> allBalances = jedis.hgetAll(key);
            
            Map<String, Long> result = new HashMap<>();
            for (Map.Entry<String, String> entry : allBalances.entrySet()) {
                try {
                    result.put(entry.getKey(), Long.parseLong(entry.getValue()));
                } catch (NumberFormatException e) {
                    log.error("Invalid balance format for user: " + entry.getKey(), e);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Error checking all user balances in Redis", e);
            return new HashMap<>();
        }
    }

    /**
     * Checks if a user has sufficient balance
     * @return true if balance is sufficient, false otherwise
     */
    public static boolean hasUserSufficientBalance(String sid, String uid, Long requiredAmount, JedisPool jedisPool) {
        if (jedisPool == null || sid == null || uid == null || requiredAmount == null) {
            return false;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String key = IdNameUtils.makeKeyName(null, sid, BALANCE, null);
            String balanceStr = jedis.hget(key, uid);
            if (balanceStr == null) {
                return false;
            }
            long balance = Long.parseLong(balanceStr);
            return balance >= requiredAmount;
        } catch (Exception e) {
            log.error("Error checking user sufficient balance in Redis", e);
            return false;
        }
    }

        /**
     * Checks if a user has sufficient balance
     * @param uid The user's FID
     * @param requiredAmount The amount required
     * @return true if balance is sufficient, false otherwise
     */
    public boolean hasSufficientBalance(String uid, Long requiredAmount) {
        if (uid == null || requiredAmount == null) {
            return false;
        }

        if (useRedis) {
            return hasUserSufficientBalance(sid, uid, requiredAmount, jedisPool);
        } else {
            Long balance = userBalance.get(uid);
            if (balance == null) {
                balance = accountDB.getUserBalance(uid);
                if (balance == null) {
                    return false;
                }
            }
            return balance >= requiredAmount;
        }
    }

    /**
     * Gets the total balance of all users
     * @return The total balance, 0 if error
     */
    public static long getTotalBalance(String sid, JedisPool jedisPool) {
        if (jedisPool == null || sid == null) {
            return 0L;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String key = IdNameUtils.makeKeyName(null, sid, BALANCE, null);
            Map<String, String> allBalances = jedis.hgetAll(key);
            
            return allBalances.values().stream()
                .mapToLong(str -> {
                    try {
                        return Long.parseLong(str);
                    } catch (NumberFormatException e) {
                        log.error("Invalid balance format", e);
                        return 0L;
                    }
                })
                .sum();
        } catch (Exception e) {
            log.error("Error getting total balance from Redis", e);
            return 0L;
        }
    }

    /**
     * Gets the balance for a specific via FID
     * @param viaFid The via FID to check
     * @return The via's balance if found, null otherwise
     */
    public Long getViaBalance(String viaFid) {
        if (!KeyTools.isGoodFid(viaFid)) {
            return null;
        }

        if (useRedis) {
            try (var jedis = jedisPool.getResource()) {
                String balanceStr = jedis.hget(REDIS_KEY_VIA_BALANCE, viaFid);
                return balanceStr != null ? Long.parseLong(balanceStr) : null;
            } catch (Exception e) {
                log.error("Error checking via balance", e);
                return null;
            }
        } else {
            // First check the in-memory cache
            Long balance = viaBalance.get(viaFid);
            if (balance != null) {
                return balance;
            }
            
            // If not in cache, check the database
            return accountDB.getViaBalance(viaFid);
        }
    }

    // Add method to select strategy
    private AutoScanStrategy selectAutoScanStrategy(BufferedReader br) {
        AutoScanStrategy[] strategies = AutoScanStrategy.values();
        AutoScanStrategy selectedStrategy= Inputer.chooseOne(strategies, null, "Select Auto Scan Strategy:", br);
        configureListenPath(br);
        return selectedStrategy;
    }

    // Add this method to handle listen path input
    private void configureListenPath(BufferedReader br) {
        if (autoScanStrategy == AutoScanStrategy.WEBHOOK || autoScanStrategy == AutoScanStrategy.FILE_MONITOR) {
            String path = Inputer.inputString(br, "Enter the path to monitor for updates:");
            if (path != null && !path.trim().isEmpty()) {
                this.listenPath = path;
                accountDB.setListenPath(path);
            }
        }
    }
    public Long getMyBalance() {
        return myBalance;
    }
    public void setMyBalance(Long myBalance) {
        this.myBalance = myBalance;
    }
    public String getMainFid() {
        return mainFid;
    }
    public String getSid() {
        return sid;
    }
    public Long getDealerMinBalance() {
        return dealerMinBalance;
    }
    public Double getOrderViaShare() {
        return orderViaShare;
    }
    public void setOrderViaShare(Double orderViaShare) {
        this.orderViaShare = orderViaShare;
    }
    public Double getConsumeViaShare() {
        return consumeViaShare;
    }
    public void setConsumeViaShare(Double consumeViaShare) {
        this.consumeViaShare = consumeViaShare;
    }
    public JedisPool getJedisPool() {
        return jedisPool;
    }
    public boolean isUseRedis() {
        return useRedis;
    }

    public AutoScanStrategy getAutoScanStrategy() {
        return autoScanStrategy;
    }
    public void setAutoScanStrategy(AutoScanStrategy autoScanStrategy) {
        this.autoScanStrategy = autoScanStrategy;
    }
    public String getListenPath() {
        return listenPath;
    }
    public void setListenPath(String listenPath) {
        this.listenPath = listenPath;
    }
    public long getLastUpdateIncomeTime() {
        return lastUpdateIncomeTime;
    }
    public void setLastUpdateIncomeTime(long lastUpdateIncomeTime) {
        this.lastUpdateIncomeTime = lastUpdateIncomeTime;
    }
    public long getLastUpdateExpenseTime() {
        return lastUpdateExpenseTime;
    }
    public void setLastUpdateExpenseTime(long lastUpdateExpenseTime) {
        this.lastUpdateExpenseTime = lastUpdateExpenseTime;
    }
    public Double getMinPay() {
        return minPay;
    }
    public void setMinPay(Double minPay) {
        this.minPay = minPay;
    }
}
