package handlers;

import apip.apipData.Fcdsl;
import appTools.Inputer;
import appTools.Menu;
import appTools.Settings;
import appTools.Shower;
import clients.ApipClient;
import clients.Client;
import constants.FieldNames;
import constants.Strings;
import constants.Values;
import crypto.KeyTools;
import fcData.AccountDB;
import fcData.IdNameTools;
import fch.ParseTools;
import fch.fchData.Cash;
import fch.fchData.OpReturn;
import fch.fchData.SendTo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import tools.BytesTools;
import tools.Hex;
import tools.MapQueue;
import tools.NumberTools;
import tools.ObjectTools;
import tools.http.AuthType;
import tools.http.RequestMethod;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static constants.Constants.DEFAULT_CASH_LIST_SIZE;
import static constants.FieldNames.BIRTH_HEIGHT;
import static constants.Strings.VIA;

public class AccountHandler {
    final static Logger log = LoggerFactory.getLogger(AccountHandler.class);
    private static final int MAX_USER_BALANCE_CACHE_SIZE = 2;
    private static final int MAX_FID_VIA_CACHE_SIZE = 2;
    private static final int MAX_VIA_BALANCE_CACHE_SIZE = 2;
    private static final String ORDER_VIA_SHARE = "orderViaShare";
    private static final String CONSUME_VIA_SHARE = "consumeViaShare";
    public static final String USER_BALANCE = "user_balance";
    public static final String FID_CONSUME_VIA = "fid_consume_via";
    public static final String VIA_BALANCE = "via_balance";
    public static final String N_PRICE = "n_price";
    private Long myBalance;
    private final MapQueue<String, Long> userBalance;
    private final MapQueue<String, String> fidViaMap;
    private final MapQueue<String, Long> viaBalance;
    private final Map<String, Long> payoffMap;

    // Constructor injected fields
    private final String mainFid;
    private final String sid;
    private final ApipClient apipClient;
    private final CashHandler cashHandler;
    private final Long minBalance;
    private final AccountDB accountDB;
    private final byte[] priKey;
    private final String startHeight;

    private final Long priceBase;
    private Map<String, Long> nPriceMap;
    private Double orderViaShare;
    private Double consumeViaShare;
    
    // Add these constants at the class level
    private final String REDIS_KEY_USER_BALANCE;  // format: user_balance:mainFid:sid
    private final String REDIS_KEY_FID_CONSUME_VIA;
    private final String REDIS_KEY_VIA_BALANCE;
    // Add these fields
    private final JedisPool jedisPool;
    private final boolean useRedis;


        // Constructor
    public AccountHandler(String mainFid, Long minBalance, Long priceBase, List<String> apiList, Double orderViaShare, Double consumeViaShare, String sid, String mainFidPriKeyCipher, byte[] symKey,
                            ApipClient apipClient, CashHandler cashHandler,
                            JedisPool jedisPool, BufferedReader br) {
        this.mainFid = mainFid;
        this.priKey = Client.decryptPriKey(mainFidPriKeyCipher,symKey);
        this.sid = sid;
        this.apipClient = apipClient;
        this.cashHandler = cashHandler;
        this.jedisPool = jedisPool;
        this.priceBase = priceBase;

        this.useRedis = (jedisPool != null);
        if(minBalance==null)this.minBalance=0L;
        else this.minBalance = minBalance;

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
        this.accountDB = useRedis ? new AccountDB(null, sid) : new AccountDB(mainFid, sid);

        // Get start height
        if (useRedis) {
            REDIS_KEY_USER_BALANCE = IdNameTools.makeKeyName(null,sid, USER_BALANCE,true);
            REDIS_KEY_FID_CONSUME_VIA = IdNameTools.makeKeyName(null,sid, FID_CONSUME_VIA,true);
            REDIS_KEY_VIA_BALANCE = IdNameTools.makeKeyName(null,sid, VIA_BALANCE,true);
        } else {
            REDIS_KEY_USER_BALANCE = null;
            REDIS_KEY_FID_CONSUME_VIA = null;
            REDIS_KEY_VIA_BALANCE = null;
        }

        setNPrices(apiList,br,false);

        if(accountDB.getLastIncome().isEmpty()) {
            this.startHeight = Inputer.inputLongStr(br, "Input the start height by which the order scanning starts:");
        } else {
            this.startHeight = null;
        } 
        initShareAndCost(br, orderViaShare, consumeViaShare, false);
    }

    public void menu(BufferedReader br) {
        Menu menu = new Menu("Account Management Menu");
        
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
                System.out.println("Rate limited. Please wait before trying again.");
            } else {
                System.out.println("Processed " + newExpenses + " new expenses.");
            }
        });
        
        menu.add("Show Income List", () -> {
            Integer incomeSize = Inputer.inputInteger(br, "Enter number of records to show: ", 1, DEFAULT_CASH_LIST_SIZE);
            if(incomeSize==null)return;
            showIncomeList(incomeSize, br);
        });
        
        menu.add("Show Expense List", () -> {
            Integer expenseSize = Inputer.inputInteger(br, "Enter number of records to show: ", 1, DEFAULT_CASH_LIST_SIZE);
            if(expenseSize==null)return;
            showExpenseList(expenseSize, br);
        });
        
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

    public Long getPriceByDataTypeName(String dataType) {
        return nPriceMap.get(dataType);
    }

    // Inner classes for Income and Expense
    public static class Income implements Serializable {
        private String cashId;
        private String userFid; 
        private Long value;
        private Long time;

        // Constructor and getters/setters
        public Income(String cashId, String userFid, Long value, Long time) {
            this.cashId = cashId;
            this.userFid = userFid;
            this.value = value;
            this.time = time;
        }

        public String getCashId() {
            return cashId;
        }

        public void setCashId(String cashId) {
            this.cashId = cashId;
        }

        public String getUserFid() {
            return userFid;
        }

        public void setUserFid(String userFid) {
            this.userFid = userFid;
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

        // Getters and setters
    }

    public static class Expense implements Serializable {
        private String cashId;
        private String toFid;
        private Long value;
        private Long time;

        // Constructor and getters/setters
        public Expense(String cashId, String toFid, Long value, Long time) {
            this.cashId = cashId;
            this.toFid = toFid;
            this.value = value;
            this.time = time;
        }

        // Getters and setters

        public String getCashId() {
            return cashId;
        }

        public void setCashId(String cashId) {
            this.cashId = cashId;
        }

        public String getToFid() {
            return toFid;
        }

        public void setToFid(String toFid) {
            this.toFid = toFid;
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
    }

    /**
     * Updates myBalance using APIP client
     * @return true if update successful, false otherwise
     */
    public boolean updateMyBalance() {
        Map<String, Long> balanceMap = apipClient.balanceByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, mainFid);
        if (balanceMap != null && !balanceMap.isEmpty()) {
            myBalance = balanceMap.get(mainFid);
            return true;
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
            if(!KeyTools.isValidFchAddr(userFid) || value==null)return null;
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
         * @param userFid The user's FID to check
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
            if (userFid == null || viaFid == null || !KeyTools.isValidFchAddr(userFid) || !KeyTools.isValidFchAddr(viaFid)) {
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
    
        /**
         * Updates via balances based on user transactions
         * @param userFid The user's FID
         * @param value The transaction value
         * @param orderVia the via fid if this is an order, null if consumption
         */
        public void addViaBalance(String userFid, Long value, @Nullable String orderVia) {
            if(!KeyTools.isValidFchAddr(userFid))return;
            if(orderVia!=null && !KeyTools.isValidFchAddr(orderVia))return;
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

        // Add this field at the class level
        private long lastUpdateIncomeTime = 0;
    
        /**
         * Updates income records and related balances
         */
        public Map<String, Income> updateIncome() {
            // Rate limiting check (1 minute = 60000 milliseconds)
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUpdateIncomeTime < 60000) {
                return null; 
            }
            
            // Update the last execution time
            lastUpdateIncomeTime = currentTime;
            
            // Get last income based on storage type
            List<String> lastIncome;
  
            lastIncome = accountDB.getLastIncome();
            // Create Fcdsl query to get new incomes
            Fcdsl fcdsl = new Fcdsl();
            fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.OWNER).addNewValues(mainFid);
            if(startHeight!=null)
                fcdsl.getQuery().addNewRange().addNewFields(BIRTH_HEIGHT).addGt(startHeight);
            fcdsl.addSort(BIRTH_HEIGHT, Values.ASC).addSort(FieldNames.CASH_ID, Values.ASC);
            fcdsl.addSize(DEFAULT_CASH_LIST_SIZE);
            if (!lastIncome.isEmpty()) {
                fcdsl.addAfter(lastIncome);
            }
            
            // Get new incomes from APIP
            Map<String, Long> userFidValueMap = new HashMap<>();
            Map<String, Income> newIncomeMap = new HashMap<>();
            List<Cash> newCashes;
            List<Cash> newOrderCasheList = new ArrayList<>();
            while(true){
                if (!lastIncome.isEmpty()) {
                    fcdsl.addAfter(lastIncome);
                }
                newCashes = apipClient.cashSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
                if(apipClient.getFcClientEvent().getResponseBody().getLast()!=null)
                    lastIncome = apipClient.getFcClientEvent().getResponseBody().getLast();
                if (newCashes == null || newCashes.isEmpty()) {
                    break;
                }
                for(Cash cash:newCashes){
                    // Skip if issuer is the owner
                    if (cash.getIssuer().equals(cash.getOwner())) {
                        continue;
                    }
                    newOrderCasheList.add(cash);
                    // Create new income record
                    Income income = new Income(
                        cash.getCashId(),
                        cash.getIssuer(), 
                        cash.getValue(),
                        cash.getBirthTime()
                    );
                    
                    newIncomeMap.put(cash.getCashId(), income);
                    
                    // Update user balance
                    userFidValueMap.merge(cash.getIssuer(), cash.getValue(), Long::sum);
                }
                
                if(newCashes.size() < DEFAULT_CASH_LIST_SIZE){
                    break;
                }
            }

            // Store updates based on storage type
            if (!newIncomeMap.isEmpty()) {
                accountDB.updateIncomes(newIncomeMap);
            }

            if(!userFidValueMap.isEmpty()){
                addAllBalance(userFidValueMap);
            }

            // Display using map values instead of list
            Shower.showDataTable("New Incomes", new ArrayList<>(newIncomeMap.values()), 0);

            // Update lastIncome in persistent storage
            accountDB.setLastIncome(lastIncome);

            checkIncomeVia(newOrderCasheList);
            
            return newIncomeMap;
        }
    
    private void checkIncomeVia(List<Cash> newCashes) {
        if(newCashes==null || newCashes.isEmpty()) return;
        Map<String,String> txIdCashIdMap = new HashMap<>();
        for(Cash cash:newCashes){
            txIdCashIdMap.put(cash.getBirthTxId(), cash.getCashId());
        }
        Map<String, OpReturn> opReturnMap = apipClient.opReturnByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, txIdCashIdMap.keySet().toArray(new String[0]));
        if(opReturnMap==null || opReturnMap.isEmpty()) return;
        for(Map.Entry<String, OpReturn> entry:opReturnMap.entrySet()){
            OpReturn opReturn = entry.getValue();
            Map<String, String> opReturnData = ObjectTools.objectToMap(opReturn.getOpReturn(), String.class, String.class);
            if(opReturnData==null)continue;
            if(opReturnData.containsKey(VIA)){
                List<Cash> matchingCashes = newCashes.stream()
                    .filter(cash -> cash.getBirthTxId().equals(opReturn.getTxId()))
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
        // Rate limiting check (1 minute = 60000 milliseconds)
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateExpenseTime < 60000) {
            return -1;  // Rate limited
        }


        // Update the last execution time
        lastUpdateExpenseTime = currentTime;
        
        // Get last expense based on storage type
        List<String> lastExpense;

        lastExpense = accountDB.getLastExpense();

        // Create Fcdsl query to get new expenses
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.ISSUER).addNewValues(mainFid);
        if(startHeight!=null)
            fcdsl.getQuery().addNewRange().addNewFields(BIRTH_HEIGHT).addGt(startHeight);
        fcdsl.addSort(BIRTH_HEIGHT, Values.ASC).addSort(FieldNames.CASH_ID, Values.ASC);
        fcdsl.addSize(DEFAULT_CASH_LIST_SIZE);
        if (!lastExpense.isEmpty()) {
            fcdsl.addAfter(lastExpense);
        }
        
        // Get new expenses from APIP
        Map<String, Expense> newExpenseMap = new HashMap<>();
        while(true) {
            if (!lastExpense.isEmpty()) {
                fcdsl.addAfter(lastExpense);
            }
            List<Cash> newCashes = apipClient.cashSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
            if(apipClient.getFcClientEvent().getResponseBody().getLast()!=null)
                lastExpense = apipClient.getFcClientEvent().getResponseBody().getLast();
            if (newCashes == null || newCashes.isEmpty()) {
                break;
            }

            for (Cash cash : newCashes) {
                // Create new expense record
                Expense expense = new Expense(
                    cash.getCashId(),
                    cash.getOwner(),
                    cash.getValue(),
                    cash.getBirthTime()
                );
                
                newExpenseMap.put(cash.getCashId(), expense);
            }
            
            if(newCashes.size() < DEFAULT_CASH_LIST_SIZE){
                break;
            }
        }

        // Store updates based on storage type
        if (!newExpenseMap.isEmpty()) {
            accountDB.updateExpenses(newExpenseMap);
        }

        // Display using map values instead of list
        Shower.showDataTable("New Expenses", new ArrayList<>(newExpenseMap.values()), 0);

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
        long validSum = myBalance - minBalance;
        if (validSum <= 0) {
            return false;
        }

        // Get pending payoffMap from AccountDB
        payoffMap.putAll(accountDB.getPayoffMap());

        // Process unpaid via balances first
        if (!makePaymentToVia(validSum)) {
            return false;
        }
        validSum = myBalance - minBalance - calculateTotalPayoff();

        // Process fixed costs
        if (!makePaymentForFixedCost(validSum)) {
            return false;
        }
        validSum = myBalance - minBalance - calculateTotalPayoff();

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
                for (Map.Entry<Integer, String> entry : apiMap.entrySet()) {
                    accountDB.setNPrice(entry.getValue(), 1L);
                    if(nPriceMap == null) nPriceMap = new HashMap<>();
                    nPriceMap.put(entry.getValue(), 1L);
                }
                System.out.println("Done.");
                return;
            }
            if (str.equals("zero")) {
                for (Map.Entry<Integer, String> entry : apiMap.entrySet()) {
                    accountDB.setNPrice(entry.getValue(), 0L);
                    if(nPriceMap == null) nPriceMap = new HashMap<>();
                    nPriceMap.put(entry.getValue(), 0L);
                }
                System.out.println("Done.");
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
                accountDB.setNPrice(apiName, n);
                nPriceMap.put(apiName, n);
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

        // Convert payoffMap to SendTo list
        List<SendTo> sendToList = payoffMap.entrySet().stream()
            .map(entry -> new SendTo(entry.getKey(), ParseTools.satoshiToCoin(entry.getValue())))
            .collect(Collectors.toList());

        // Send payments using CashClient
        String txId = cashHandler.send(null, null, null, sendToList, null, null);
        // Update last settlement height
        Long bestHeight = cashHandler.getBestHeight();
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
            accountDB.setOrderViaShare(NumberTools.roundDouble8(inputShare/100.0));
        }

        if (consumeViaShare != null){
            accountDB.setConsumeViaShare(consumeViaShare);
        }else{
            Double inputShare = Inputer.inputDouble(br, "Input the consume via share percentage:");
            if(inputShare==null)return;
            accountDB.setConsumeViaShare(NumberTools.roundDouble8(inputShare/100));
        }
    }

    public void inputContributorShare(BufferedReader br) {
        accountDB.inputContributorShare(br);
    }

    public void inputFixedCost(BufferedReader br) {
        accountDB.inputFixedCost(br);
    }

    public void close() {
        if (useRedis) {
            BytesTools.clearByteArray(priKey);
        } else {

            saveMapsToAccountDB();
            BytesTools.clearByteArray(priKey);
            accountDB.close();
        }
    }

    /**
     * Shows income list with pagination from the end
     * @param size Number of records to show per page
     */
    public void showIncomeList(int size,BufferedReader br) {

        do {
            List<Income> incomes = accountDB.getIncomeFromEnd(size);
            if (incomes.isEmpty()) {
                System.out.println("No income records found.");
                return;
            }

            Shower.showDataTable("Income List", incomes, 0);
        } while (Inputer.askIfYes(br, "Show more? (y/n):"));
    }
    
    /**
     * Shows expense list with pagination from the end
     * @param size Number of records to show per page
     */
    public void showExpenseList(int size,BufferedReader br) {
        do {
            List<Expense> expenses = accountDB.getExpenseFromEnd(size);
            if (expenses.isEmpty()) {
                System.out.println("No expense records found.");
                return;
            }

            Shower.showDataTable("Expense List", expenses, 0);
        } while (Inputer.askIfYes(br, "Show more? (y/n):"));
    }

    public Map<String, Long> getPayoffMap() {
        return payoffMap;
    }

    /**
     * Checks if a via has accumulated balance
     * @param viaFid The via FID to check
     * @return The via's balance if greater than 0, null otherwise
     */
    public Long checkViaBalance(String viaFid) {
        if (viaFid == null) return null;
        
        Long balance = null;

        if (useRedis) {
            try (var jedis = jedisPool.getResource()) {
                String balanceStr = jedis.hget(REDIS_KEY_VIA_BALANCE, viaFid);
                if (balanceStr == null) return null;
                balance = Long.parseLong(balanceStr);
                return balance;
            }
        } else {
            // First check current balance
            balance = viaBalance.get(viaFid);
            if (balance != null && balance > 0) {
                return balance;
            }
            balance = accountDB.getViaBalance(viaFid);
            
            if (balance != null && balance > 0) {
                return balance;
            }
            
            return null;
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
}
