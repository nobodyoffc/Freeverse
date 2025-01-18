package fcData;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import appTools.Inputer;

import org.mapdb.HTreeMap;

import handlers.AccountHandler.Expense;
import handlers.AccountHandler.Income;
import fch.ParseTools;
import tools.FileTools;
import tools.NumberTools;

import java.util.concurrent.ConcurrentMap;
import java.util.Map;
import java.util.HashMap;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AccountDB {
    private static final String ACCOUNT_DB = "account";
    private final String myFid;
    private final String sid;
    
    private volatile DB db;
    private volatile ConcurrentMap<String, Long> userBalanceMap;
    private volatile ConcurrentMap<String, String> fidConsumeViaMap; 
    private volatile ConcurrentMap<String, Long> viaBalanceMap;
    private volatile ConcurrentMap<String, Long> unpaidViaBalanceMap;
    private volatile ConcurrentMap<String, Long> unpaidFixedCostMap;
    private volatile ConcurrentMap<String, Long> fixedCostMap;
    private volatile ConcurrentMap<String, Double> contributorShareMap; 
    private volatile ConcurrentMap<String, String> metaMap;
    private volatile HTreeMap<String, Income> incomeMap;
    private volatile HTreeMap<Long, String> incomeOrderMap;
    private volatile long currentIncomeIndex;
    private volatile HTreeMap<String, Expense> expenseMap;
    private volatile HTreeMap<Long, String> expenseOrderMap;
    private volatile long currentExpenseIndex;
    private final Object lock = new Object();
    private volatile ConcurrentMap<String, Long> payoffMap;  

    public AccountDB(String myFid, String sid) {
        this.myFid = myFid;
        this.sid = sid;
        initializeDb();
    }

    private void initializeDb() {
        if (db == null || db.isClosed()) {
            synchronized (lock) {
                if (db == null || db.isClosed()) {
                    String dbName = FileTools.makeFileName(myFid, sid, ACCOUNT_DB, constants.Strings.DOT_DB);
                    db = DBMaker.fileDB(dbName)
                            .fileMmapEnable()
                            .checksumHeaderBypass()
                            .transactionEnable()
                            .make();
                    
                    userBalanceMap = db.hashMap("userBalance")
                            .keySerializer(Serializer.STRING)
                            .valueSerializer(Serializer.LONG)
                            .createOrOpen();
                            
                    fidConsumeViaMap = db.hashMap("fidVia")
                            .keySerializer(Serializer.STRING)
                            .valueSerializer(Serializer.STRING)
                            .createOrOpen();
                            
                    viaBalanceMap = db.hashMap("viaBalance")
                            .keySerializer(Serializer.STRING)
                            .valueSerializer(Serializer.LONG)
                            .createOrOpen();
                            
                    unpaidViaBalanceMap = db.hashMap("unpaidViaBalance")
                            .keySerializer(Serializer.STRING)
                            .valueSerializer(Serializer.LONG)
                            .createOrOpen();
                            
                    fixedCostMap = db.hashMap("fixedCost")
                            .keySerializer(Serializer.STRING)
                            .valueSerializer(Serializer.LONG)
                            .createOrOpen();
                            
                    unpaidFixedCostMap = db.hashMap("unpaidFixedCost")
                            .keySerializer(Serializer.STRING)
                            .valueSerializer(Serializer.LONG)
                            .createOrOpen();
                            
                    contributorShareMap = db.hashMap("contributorShare")
                            .keySerializer(Serializer.STRING)
                            .valueSerializer(Serializer.DOUBLE)
                            .createOrOpen();
                            
                    metaMap = db.hashMap("meta")
                            .keySerializer(Serializer.STRING)
                            .valueSerializer(Serializer.STRING)
                            .createOrOpen();
                            
                    incomeMap = db.hashMap("income")
                            .keySerializer(Serializer.STRING)
                            .valueSerializer(Serializer.JAVA)
                            .createOrOpen();
                            
                    incomeOrderMap = db.hashMap("income_order")
                            .keySerializer(Serializer.LONG)
                            .valueSerializer(Serializer.STRING)
                            .createOrOpen();
                    currentIncomeIndex = incomeOrderMap.size();
                            
                    expenseMap = db.hashMap("expense")
                            .keySerializer(Serializer.STRING)
                            .valueSerializer(Serializer.JAVA)
                            .createOrOpen();
                            
                    expenseOrderMap = db.hashMap("expense_order")
                            .keySerializer(Serializer.LONG)
                            .valueSerializer(Serializer.STRING)
                            .createOrOpen();
                    currentExpenseIndex = expenseOrderMap.size();
                            
                    payoffMap = db.hashMap("payoff")
                            .keySerializer(Serializer.STRING)
                            .valueSerializer(Serializer.LONG)
                            .createOrOpen();
                }
            }
        }
    }

    public void inputContributorShare(BufferedReader br) {
        try {
            // Clear existing contributor shares
            if(contributorShareMap.size()>0){
                for(String fid:contributorShareMap.keySet()){
                    System.out.println(fid+" -> "+contributorShareMap.get(fid));
                }
                if(Inputer.askIfYes(br,"There are already "+contributorShareMap.size()+" contributors. Reset all contributors? ")){
                    contributorShareMap.clear();
                    db.commit();
                }else return;
            }
            
            double totalShare = 0.0;
            while (true) {
                String fid = Inputer.inputFid(br, "Input the contributor FID:");
                if (fid == null || fid.isEmpty()) continue;
                
                Double share;
                if (Inputer.askIfYes(br, "Set the rest of the share percentage ("+(100-totalShare)+"%) for " + fid + "? \n")) {
                    share = NumberTools.roundDouble4((100 - totalShare)/100);
                    contributorShareMap.put(fid, share);
                    db.commit();
                    break;
                } else {
                    share = Inputer.inputDouble(br, "Input the share percentage for " + fid + ":");
                    if (share == null) continue;
                    totalShare += share;
                }
                
                if (totalShare > 100) {
                    System.out.println("Total share exceeds 100%. Please try again.");
                    if(Inputer.askIfYes(br,"Reset the share for "+fid+"?")){
                        totalShare =0;
                        contributorShareMap.clear();
                        continue;
                    }
                    totalShare -= share;
                    continue;
                }
                share = NumberTools.roundDouble4(share/100);
                contributorShareMap.put(fid, share);
                db.commit();
                
                if (totalShare == 100) break;
                System.out.println("There is still " + (100 - totalShare) + " left. Please add more contributors.");
            }
        } catch (Exception e) {
            System.out.println("Error adding contributor share: " + e.getMessage());
        }
    }

    public void inputFixedCost(BufferedReader br) {
        if(fixedCostMap.size()>0){
            for(String fid:fixedCostMap.keySet()){
                System.out.println(fid+" -> "+ParseTools.satoshiToCoin(fixedCostMap.get(fid)));
            }
            if(Inputer.askIfYes(br,"There are already "+fixedCostMap.size()+" fixed costs. Reset all fixed costs? ")){
                fixedCostMap.clear();
                db.commit();
            }else return;
        }
        try {
            while (true) {
                System.out.println("\nInput fid and fixed cost(split by space). Empty line to finish:");
                String line = br.readLine();
                if (line == null || line.trim().isEmpty()) {
                    break;
                }

                String[] parts = line.trim().split("\\s+");
                if (parts.length != 2) {
                    System.out.println("Invalid input format. Please enter fid and cost separated by space.");
                    continue;
                }

                String fid = parts[0];
                Double cost;
                try {
                    cost = Double.parseDouble(parts[1]);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid cost value. Please enter a valid number.");
                    continue;
                }

                if (cost <= 0) {
                    System.out.println("Cost must be greater than 0.");
                    continue;
                }
                long costLong = ParseTools.coinToSatoshi(cost);
                fixedCostMap.put(fid, costLong);
                db.commit();
                System.out.println("Added fixed cost: " + fid + " -> " + cost);
            }
        } catch (Exception e) {
            System.out.println("Error adding fixed cost: " + e.getMessage());
        }
    }


    public void removeUserBalance(String fid) {
        userBalanceMap.remove(fid);
        db.commit();
    }

    // User Balance methods
    public Long getUserBalance(String fid) {
        return userBalanceMap.getOrDefault(fid, 0L);
    }

    public void setUserBalance(String fid, Long balance) {
        userBalanceMap.put(fid, balance);
        db.commit();
    }

    // FID Via methods
    public String getFidVia(String fid) {
        return fidConsumeViaMap.get(fid);
    }

    public void setFidVia(String fid, String viaFid) {
        fidConsumeViaMap.put(fid, viaFid);
        db.commit();
    }

    public ConcurrentMap<String, Long> getViaBalanceMap() {
        return viaBalanceMap;
    }

    public void clearViaBalanceMap() {
        viaBalanceMap.clear();
        db.commit();
    }   

    // Via Balance methods
    public Long getViaBalance(String viaFid) {
        return viaBalanceMap.getOrDefault(viaFid, 0L);
    }

    public void setViaBalance(String viaFid, Long balance) {
        viaBalanceMap.put(viaFid, balance);
        db.commit();
    }

    // Unpaid Via Balance methods
    public Long getUnpaidViaBalance(String viaFid) {
        return unpaidViaBalanceMap.getOrDefault(viaFid, 0L);
    }

    public void setUnpaidViaBalance(String viaFid, Long balance) {
        unpaidViaBalanceMap.put(viaFid, balance);
        db.commit();
    }

    // Fixed Cost methods
    public Long getFixedCost(String fid) {
        return fixedCostMap.getOrDefault(fid, 0L);
    }

    public void setFixedCost(String fid, Long cost) {
        fixedCostMap.put(fid, cost);
        db.commit();
    }

    // Unpaid Fixed Cost methods
    public Long getUnpaidFixedCost(String fid) {
        return unpaidFixedCostMap.getOrDefault(fid, 0L);
    }

    public void setUnpaidFixedCost(String fid, Long cost) {
        unpaidFixedCostMap.put(fid, cost);
        db.commit();
    }

    // Contributor Share methods
    public Double getContributorShare(String fid) {
        return contributorShareMap.getOrDefault(fid, 0.0);
    }

    public void setContributorShare(String fid, Double share) {
        contributorShareMap.put(fid, share);
        db.commit();
    }

    // Meta data methods remain the same
    // ... rest of the meta methods ...

    public void close() {
        synchronized (lock) {
            if (db != null && !db.isClosed()) {
                db.close();
                db = null;
            }
        }
    }
    // Bulk update methods for maps
    public void updateUserBalance(Map<String, Long> balances) {
        userBalanceMap.putAll(balances);
        db.commit();
    }   

    // Bulk add methods for maps
    public Map<String, Long>  addUserBalance(Map<String, Long> balances) {
        Map<String, Long> outCacheMap = new HashMap<>();
        balances.forEach((fid, balance) -> {
            Long existingBalance = userBalanceMap.getOrDefault(fid, 0L);
            userBalanceMap.put(fid, existingBalance + balance);
            outCacheMap.put(fid, existingBalance + balance);
        });
        db.commit();
        return outCacheMap;
    }

    public void updateUserBalance(Map.Entry<String, Long> balance) {
        if(balance==null)return;
        userBalanceMap.put(balance.getKey(), balance.getValue());
        db.commit();
    }
    
    public void addUserBalance(Map.Entry<String, Long> balance) {
        if(balance==null)return;
        Long existingBalance = userBalanceMap.getOrDefault(balance.getKey(), 0L);
        userBalanceMap.put(balance.getKey(), existingBalance + balance.getValue());
        db.commit();
    }

    public void updateViaBalance(Map<String, Long> balances) {
        viaBalanceMap.putAll(balances);
        db.commit();
    }

    public void addViaBalance(Map<String, Long> balances) {
        balances.forEach((viaFid, balance) -> {
            Long existingBalance = viaBalanceMap.getOrDefault(viaFid, 0L);
            viaBalanceMap.put(viaFid, existingBalance + balance);
        });
        db.commit();
    }

    public void updateViaBalance(Map.Entry<String, Long> balance) {
        if(balance==null)return;
        viaBalanceMap.put(balance.getKey(), balance.getValue());
        db.commit();
    }

    public void addViaBalance(Map.Entry<String, Long> balance) {
        if(balance==null)return;
        Long existingBalance = viaBalanceMap.getOrDefault(balance.getKey(), 0L);
        viaBalanceMap.put(balance.getKey(), existingBalance + balance.getValue());
        db.commit();
    }

    public void updateUnpaidViaBalance(Map<String, Long> balances) {
        unpaidViaBalanceMap.putAll(balances);
        db.commit();
    }

    public void addUnpaidViaBalance(Map<String, Long> balances) {
        balances.forEach((viaFid, balance) -> {
            Long existingBalance = unpaidViaBalanceMap.getOrDefault(viaFid, 0L);
            unpaidViaBalanceMap.put(viaFid, existingBalance + balance);
        });
        db.commit();
    }

    public void updateUnpaidViaBalance(Map.Entry<String, Long> balance) {
        if(balance==null)return;
        unpaidViaBalanceMap.put(balance.getKey(), balance.getValue());
        db.commit();
    }

    public void clearUnpaidViaBalanceMap() {
        unpaidViaBalanceMap.clear();
        db.commit();
    } 

    public void addUnpaidViaBalance(Map.Entry<String, Long> balance) {
        if(balance==null)return;
        Long existingBalance = unpaidViaBalanceMap.getOrDefault(balance.getKey(), 0L);
        unpaidViaBalanceMap.put(balance.getKey(), existingBalance + balance.getValue());
        db.commit();
    }

    public void clearUnpaidFixedCostMap() {
        unpaidFixedCostMap.clear();
        db.commit();
    }


    public void updateUnpaidFixedCostMap(Map<String, Long> costs) {
        unpaidFixedCostMap.putAll(costs);
        db.commit();
    }

    public void updateFidVia(Map<String, String> fidViaMap) {
        this.fidConsumeViaMap.putAll(fidViaMap);
        db.commit();
    }   

    public void updateFidConsumeVia(Map.Entry<String, String> fidVia) {
        if(fidVia==null)return;
        fidConsumeViaMap.put(fidVia.getKey(), fidVia.getValue());
        db.commit();
    }   

    // Getter methods for entire maps
    public Map<String, Long> getUnpaidViaBalance() {
        return new HashMap<>(unpaidViaBalanceMap);
    }

    public Map<String, Long> getFixedCostMap() {
        return new HashMap<>(fixedCostMap);
    }

    public Map<String, Long> getUnpaidFixedCostMap() {
        return new HashMap<>(unpaidFixedCostMap);
    }

    public Map<String, Double> getContributorShareMap() {
        return new HashMap<>(contributorShareMap);
    }

    // Via share getters
    public Double getOrderViaShare() {
        String share = metaMap.getOrDefault("orderViaShare", "0.0");
        if(share.isEmpty())
            return null;
        return Double.parseDouble(share);
    }

    public Double getConsumeViaShare() {
        String share = metaMap.getOrDefault("consumeViaShare", "0.0");
        if(share.isEmpty())
            return null;
        return Double.parseDouble(share);
    }

    // Income/Expense tracking methods
    public List<String> getLastIncome() {
        String lastIncome = metaMap.getOrDefault("lastIncome", "");
        return lastIncome.isEmpty() ? new ArrayList<>() : Arrays.asList(lastIncome.split(","));
    }

    public void setLastIncome(List<String> lastIncome) {
        if(lastIncome==null || lastIncome.isEmpty())return;
        metaMap.put("lastIncome", String.join(",", lastIncome));
        db.commit();
    }

    public List<String> getLastExpense() {
        String lastExpense = metaMap.getOrDefault("lastExpense", "");
        return lastExpense.isEmpty() ? new ArrayList<>() : Arrays.asList(lastExpense.split(","));
    }

    public void setLastExpense(List<String> lastExpense) {
        metaMap.put("lastExpense", String.join(",", lastExpense));
        db.commit();
    }

    public void setLastSettleHeight(Long height) {
        metaMap.put("lastSettleHeight", height.toString());
        db.commit();
    }

    // Add new methods for income/expense management
    public void addIncome(String cashId, Income income) {
        incomeMap.put(cashId, income);
        incomeOrderMap.put(currentIncomeIndex++, cashId);
        db.commit();
    }

    public void addExpense(String cashId, Expense expense) {
        expenseMap.put(cashId, expense);
        expenseOrderMap.put(currentExpenseIndex++, cashId);
        db.commit();
    }

    public List<Income> getIncomeFromBeginning(int size) {
        List<Income> result = new ArrayList<>(size);
        for (long i = 0; i < Math.min(currentIncomeIndex, size); i++) {
            String cashId = incomeOrderMap.get(i);
            if (cashId != null) {
                Income income = incomeMap.get(cashId);
                if (income != null) {
                    result.add(income);
                }
            }
        }
        return result;
    }

    public List<Income> getIncomeFromEnd(int size) {
        List<Income> result = new ArrayList<>(size);
        for (long i = currentIncomeIndex - 1; i >= Math.max(0, currentIncomeIndex - size); i--) {
            String cashId = incomeOrderMap.get(i);
            if (cashId != null) {
                Income income = incomeMap.get(cashId);
                if (income != null) {
                    result.add(income);
                }
            }
        }
        return result;
    }

    public Map<String, Income> getIncomeMap() {
        return new HashMap<>(incomeMap);
    }

    public Map<String, Expense> getExpenseMap() {
        return new HashMap<>(expenseMap);
    }

    // Add new method for batch income updates
    public void updateIncomes(Map<String, Income> incomes) {
        incomeMap.putAll(incomes);
        db.commit();
    }

    public String getLastExpenseHeight() {
        return metaMap.getOrDefault("lastExpenseHeight", "");
    }

    public void setLastExpenseHeight(String height) {
        metaMap.put("lastExpenseHeight", height);
        db.commit();
    }

    // Add this method for batch expense updates
    public void updateExpenses(Map<String, Expense> expenses) {
        expenseMap.putAll(expenses);
        db.commit();
    }

    public List<Expense> getExpenseFromBeginning(int size) {
        List<Expense> result = new ArrayList<>(size);
        for (long i = 0; i < Math.min(currentExpenseIndex, size); i++) {
            String cashId = expenseOrderMap.get(i);
            if (cashId != null) {
                Expense expense = expenseMap.get(cashId);
                if (expense != null) {
                    result.add(expense);
                }
            }
        }
        return result;
    }

    public List<Expense> getExpenseFromEnd(int size) {
        List<Expense> result = new ArrayList<>(size);
        for (long i = currentExpenseIndex - 1; i >= Math.max(0, currentExpenseIndex - size); i--) {
            String cashId = expenseOrderMap.get(i);
            if (cashId != null) {
                Expense expense = expenseMap.get(cashId);
                if (expense != null) {
                    result.add(expense);
                }
            }
        }
        return result;
    }

    // Add these methods for payoff management
    public void updatePayoffMap(Map<String, Long> payoffs) {
        payoffMap.putAll(payoffs);
        db.commit();
    }

    public Map<String, Long> getPayoffMap() {
        return new HashMap<>(payoffMap);
    }

    public void clearPayoffMap() {
        payoffMap.clear();
        db.commit();
    }

    public void setOrderViaShare(double orderViaShare) {
        metaMap.put("orderViaShare", String.valueOf(orderViaShare));
        db.commit();
    }

    public void setConsumeViaShare(double consumeViaShare) {
        metaMap.put("consumeViaShare", String.valueOf(consumeViaShare));
        db.commit();
    }

    public boolean hasNPrices() {
        // Check if any keys in metaMap start with "nPrice_"
        return metaMap.keySet().stream()
            .anyMatch(key -> key.startsWith("nPrice_"));
    }

    public Map<String, Long> getNPriceMap() {
        Map<String, Long> nPriceMap = new HashMap<>();
        
        // Iterate through metaMap and collect all nPrice entries
        metaMap.forEach((key, value) -> {
            if (key.startsWith("nPrice_")) {
                String apiName = key.substring("nPrice_".length());
                try {
                    Long price = Long.parseLong(value);
                    nPriceMap.put(apiName, price);
                } catch (NumberFormatException e) {
                    // Skip invalid values
                }
            }
        });
        
        return nPriceMap;
    }

    public void setNPrice(String apiName, Long value) {
        if (apiName == null || value == null) return;
        
        metaMap.put("nPrice_" + apiName, value.toString());
        db.commit();
    }
} 