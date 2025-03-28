// ... existing code ...
package handlers;

import constants.FieldNames;
import db.LocalDB;
import db.LevelDB;
import fcData.FcEntity;

import appTools.Inputer;

import handlers.AccountHandler.Expense;
import handlers.AccountHandler.Income;
import fch.FchUtils;
import org.jetbrains.annotations.NotNull;
import utils.NumberUtils;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static constants.FieldNames.LAST_HEIGHT;

public class AccountDB {
    private final String myFid;
    private final String sid;
    private final String dbPath;
    
    private volatile LocalDB<FcEntity> db;
    private final Object lock = new Object();

    private volatile long currentIncomeIndex;
    private volatile long currentExpenseIndex;
// ... existing code ...

    public AccountDB(String myFid, String sid, String dbPath) {
        this.myFid = myFid;
        this.sid = sid;
        this.dbPath = dbPath;
        initializeDb();
    }

    private void initializeDb() {
        if (db == null || db.isClosed()) {
            synchronized (lock) {
                if (db == null || db.isClosed()) {

                    db = new LevelDB<>(LocalDB.SortType.NO_SORT, FcEntity.class);
                    db.initialize(myFid, sid, dbPath, FieldNames.ACCOUNT_DB);
                    
                    // Initialize maps with proper type registration
                    initializeMap(FieldNames.USER_BALANCE_MAP,Long.class);
                    initializeMap(FieldNames.FID_VIA_MAP,String.class);
                    initializeMap(FieldNames.VIA_BALANCE_MAP,Long.class);
                    initializeMap(FieldNames.UNPAID_VIA_BALANCE_MAP,Long.class);
                    initializeMap(FieldNames.FIXED_COST_MAP,Long.class);
                    initializeMap(FieldNames.UNPAID_FIXED_COST_MAP,Long.class);
                    initializeMap(FieldNames.CONTRIBUTOR_SHARE_MAP,Double.class);
                    initializeMap(FieldNames.META_MAP,String.class);
                    initializeMap(FieldNames.INCOME_MAP,Income.class);
                    initializeMap(FieldNames.INCOME_ORDER_MAP,String.class);
                    initializeMap(FieldNames.EXPENSE_MAP,Expense.class);
                    initializeMap(FieldNames.EXPENSE_ORDER_MAP,String.class);
                    initializeMap(FieldNames.PAYOFF_MAP,Long.class);

                    // Initialize indices
                    Map<String, String> incomeOrderMap = db.getAllFromMap(FieldNames.INCOME_ORDER_MAP);
                    currentIncomeIndex = incomeOrderMap.size();
                    
                    Map<String, String> expenseOrderMap = db.getAllFromMap(FieldNames.EXPENSE_ORDER_MAP);
                    currentExpenseIndex = expenseOrderMap.size();
                }
            }
        }
    }
    
    private <T> void initializeMap(String mapName, Class<T> typeClass) {
        // Check if map exists first
        if (!db.getMapNames().contains(mapName)) {
            try {
                // Just register the type first
                db.registerMapType(mapName, typeClass);
                
                // Create a default value based on the class type directly
                T dummyValue = createDefaultValueFromClass(typeClass);
                
                if (dummyValue != null) {
                    // Create a unique dummy key
                    String dummyKey = "init_key_" + System.currentTimeMillis();
                    
                    // Add the entry
                    db.putInMap(mapName, dummyKey, dummyValue);
                    
                    // Remove the dummy entry
                    db.removeFromMap(mapName, dummyKey);
                } else {
                    // Use the direct method to create the map in LevelDB
                    if (db instanceof LevelDB) {
                        db.createMap(mapName, typeClass);
                    } else {
                        System.out.println("Warning: Could not create map: " + mapName + " - non-LevelDB implementation");
                    }
                }
            } catch (Exception e) {
                System.err.println("Error initializing map " + mapName + ": " + e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T createDefaultValueFromClass(Class<T> typeClass) {
        // Handle primitive wrapper classes directly
        if (typeClass == Long.class) {
            return (T) Long.valueOf(0L);
        } else if (typeClass == String.class) {
            return (T) "";
        } else if (typeClass == Double.class) {
            return (T) Double.valueOf(0.0);
        } else if (typeClass == Boolean.class) {
            return (T) Boolean.FALSE;
        } else if (typeClass == Integer.class) {
            return (T) Integer.valueOf(0);
        } else if (typeClass == Float.class) {
            return (T) Float.valueOf(0.0f);
        } else if (typeClass == Short.class) {
            return (T) Short.valueOf((short) 0);
        } else if (typeClass == Byte.class) {
            return (T) Byte.valueOf((byte) 0);
        } else if (typeClass == Character.class) {
            return (T) Character.valueOf('\0');
        }
        
        // Handle specific known classes
        if (typeClass.getName().endsWith("Income")) {
            try {
                // Assuming Income has a constructor that takes strings and longs
                return typeClass.getConstructor(String.class, String.class, Long.class, Long.class, Long.class)
                       .newInstance("", "", 0L, 0L, 0L);
            } catch (Exception e) {
                // Fall through to generic approach
            }
        } else if (typeClass.getName().endsWith("Expense")) {
            try {
                // Assuming Expense has a constructor that takes strings and longs
                return typeClass.getConstructor(String.class, String.class, Long.class, Long.class, Long.class)
                       .newInstance("", "", 0L, 0L, 0L);
            } catch (Exception e) {
                // Fall through to generic approach
            }
        }
        
        // Try to use a no-args constructor as a fallback
        try {
            return typeClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            // This will fail for classes without no-arg constructors and primitive wrappers
        }
        
        // If all else fails
        return null;
    }

    public void inputContributorShare(BufferedReader br) {
        try {
            // Clear existing contributor shares
            Map<String, Double> contributorShareMap = db.getAllFromMap(FieldNames.CONTRIBUTOR_SHARE_MAP);
            if(contributorShareMap.size() > 0){
                for(String fid : contributorShareMap.keySet()){
                    System.out.println(fid + " -> " + contributorShareMap.get(fid));
                }
                if(Inputer.askIfYes(br,"There are already "+contributorShareMap.size()+" contributors. Reset all contributors? ")){
                    db.clearMap(FieldNames.CONTRIBUTOR_SHARE_MAP);
                    db.commit();
                } else return;
            }
            
            double totalShare = 0.0;
            while (true) {
                String fid = Inputer.inputFid(br, "Input the contributor FID:");
                if (fid == null || fid.isEmpty()) continue;
                
                Double share;
                if (Inputer.askIfYes(br, "Set the rest of the share percentage ("+(100-totalShare)+"%) for " + fid + "? \n")) {
                    share = NumberUtils.roundDouble4((100 - totalShare)/100);
                    db.putInMap(FieldNames.CONTRIBUTOR_SHARE_MAP, fid, share);
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
                        totalShare = 0;
                        db.clearMap(FieldNames.CONTRIBUTOR_SHARE_MAP);
                        continue;
                    }
                    totalShare -= share;
                    continue;
                }
                share = NumberUtils.roundDouble4(share/100);
                db.putInMap(FieldNames.CONTRIBUTOR_SHARE_MAP, fid, share);
                db.commit();
                
                if (totalShare == 100) break;
                System.out.println("There is still " + (100 - totalShare) + " left. Please add more contributors.");
            }
        } catch (Exception e) {
            System.out.println("Error adding contributor share: " + e.getMessage());
        }
    }

    public void inputFixedCost(BufferedReader br) {
        Map<String, Long> fixedCostMap = db.getAllFromMap(FieldNames.FIXED_COST_MAP);
        if(fixedCostMap.size() > 0){
            for(String fid : fixedCostMap.keySet()){
                System.out.println(fid + " -> " + FchUtils.satoshiToCoin(fixedCostMap.get(fid)));
            }
            if(Inputer.askIfYes(br,"There are already "+fixedCostMap.size()+" fixed costs. Reset all fixed costs? ")){
                db.clearMap(FieldNames.FIXED_COST_MAP);
                db.commit();
            } else return;
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
                double cost;
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
                long costLong = FchUtils.coinToSatoshi(cost);
                db.putInMap(FieldNames.FIXED_COST_MAP, fid, costLong);
                db.commit();
                System.out.println("Added fixed cost: " + fid + " -> " + cost);
            }
        } catch (Exception e) {
            System.out.println("Error adding fixed cost: " + e.getMessage());
        }
    }

    public void removeUserBalance(String fid) {
        db.removeFromMap(FieldNames.USER_BALANCE_MAP, fid);
        db.commit();
    }

    // User Balance methods
    public Long getUserBalance(String fid) {
        return db.getFromMap(FieldNames.USER_BALANCE_MAP, fid);
    }

    public void setUserBalance(String fid, Long balance) {
        db.putInMap(FieldNames.USER_BALANCE_MAP, fid, balance);
        db.commit();
    }

    // FID Via methods
    public String getFidVia(String fid) {
        return db.getFromMap(FieldNames.FID_VIA_MAP, fid);
    }

    public void setFidVia(String fid, String viaFid) {
        db.putInMap(FieldNames.FID_VIA_MAP, fid, viaFid);
        db.commit();
    }

    public Map<String, Long> getViaBalanceMap() {
        return db.getAllFromMap(FieldNames.VIA_BALANCE_MAP);
    }

    public void clearViaBalanceMap() {
        db.clearMap(FieldNames.VIA_BALANCE_MAP);
        db.commit();
    }   

    // Via Balance methods
    public Long getViaBalance(String viaFid) {
        Long balance = db.getFromMap(FieldNames.VIA_BALANCE_MAP, viaFid);
        return balance != null ? balance : 0L;
    }

    public void setViaBalance(String viaFid, Long balance) {
        db.putInMap(FieldNames.VIA_BALANCE_MAP, viaFid, balance);
        db.commit();
    }

    // Unpaid Via Balance methods
    public Long getUnpaidViaBalance(String viaFid) {
        Long balance = db.getFromMap(FieldNames.UNPAID_VIA_BALANCE_MAP, viaFid);
        return balance != null ? balance : 0L;
    }

    public void setUnpaidViaBalance(String viaFid, Long balance) {
        db.putInMap(FieldNames.UNPAID_VIA_BALANCE_MAP, viaFid, balance);
        db.commit();
    }

    // Fixed Cost methods
    public Long getFixedCost(String fid) {
        Long cost = db.getFromMap(FieldNames.FIXED_COST_MAP, fid);
        return cost != null ? cost : 0L;
    }

    public void setFixedCost(String fid, Long cost) {
        db.putInMap(FieldNames.FIXED_COST_MAP, fid, cost);
        db.commit();
    }

    // Unpaid Fixed Cost methods
    public Long getUnpaidFixedCost(String fid) {
        Long cost = db.getFromMap(FieldNames.UNPAID_FIXED_COST_MAP, fid);
        return cost != null ? cost : 0L;
    }

    public void setUnpaidFixedCost(String fid, Long cost) {
        db.putInMap(FieldNames.UNPAID_FIXED_COST_MAP, fid, cost);
        db.commit();
    }

    // Contributor Share methods
    public Double getContributorShare(String fid) {
        Double share = db.getFromMap(FieldNames.CONTRIBUTOR_SHARE_MAP, fid);
        return share != null ? share : 0.0;
    }

    public void setContributorShare(String fid, Double share) {
        db.putInMap(FieldNames.CONTRIBUTOR_SHARE_MAP, fid, share);
        db.commit();
    }

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
        for (Map.Entry<String, Long> entry : balances.entrySet()) {
            db.putInMap(FieldNames.USER_BALANCE_MAP, entry.getKey(), entry.getValue());
        }
        db.commit();
    }   

    // Bulk add methods for maps
    public Map<String, Long> addUserBalance(Map<String, Long> balances) {
        Map<String, Long> outCacheMap = new HashMap<>();
        balances.forEach((fid, balance) -> {
            Long existingBalance = getUserBalance(fid);
            existingBalance = existingBalance != null ? existingBalance : 0L;
            Long newBalance = existingBalance + balance;
            db.putInMap(FieldNames.USER_BALANCE_MAP, fid, newBalance);
            outCacheMap.put(fid, newBalance);
        });
        db.commit();
        return outCacheMap;
    }

    public void updateUserBalance(Map.Entry<String, Long> balance) {
        if(balance == null) return;
        db.putInMap(FieldNames.USER_BALANCE_MAP, balance.getKey(), balance.getValue());
        db.commit();
    }
    
    public void addUserBalance(Map.Entry<String, Long> balance) {
        if(balance == null) return;
        Long existingBalance = getUserBalance(balance.getKey());
        existingBalance = existingBalance != null ? existingBalance : 0L;
        db.putInMap(FieldNames.USER_BALANCE_MAP, balance.getKey(), existingBalance + balance.getValue());
        db.commit();
    }

    public void updateViaBalance(Map<String, Long> balances) {
        for (Map.Entry<String, Long> entry : balances.entrySet()) {
            db.putInMap(FieldNames.VIA_BALANCE_MAP, entry.getKey(), entry.getValue());
        }
        db.commit();
    }

    public void addViaBalance(Map<String, Long> balances) {
        balances.forEach((viaFid, balance) -> {
            Long existingBalance = getViaBalance(viaFid);
            db.putInMap(FieldNames.VIA_BALANCE_MAP, viaFid, existingBalance + balance);
        });
        db.commit();
    }

    public void updateViaBalance(Map.Entry<String, Long> balance) {
        if(balance == null) return;
        db.putInMap(FieldNames.VIA_BALANCE_MAP, balance.getKey(), balance.getValue());
        db.commit();
    }

    public void addViaBalance(Map.Entry<String, Long> balance) {
        if(balance == null) return;
        Long existingBalance = getViaBalance(balance.getKey());
        db.putInMap(FieldNames.VIA_BALANCE_MAP, balance.getKey(), existingBalance + balance.getValue());
        db.commit();
    }

    public void updateUnpaidViaBalance(Map<String, Long> balances) {
        for (Map.Entry<String, Long> entry : balances.entrySet()) {
            db.putInMap(FieldNames.UNPAID_VIA_BALANCE_MAP, entry.getKey(), entry.getValue());
        }
        db.commit();
    }

    public void addUnpaidViaBalance(Map<String, Long> balances) {
        balances.forEach((viaFid, balance) -> {
            Long existingBalance = getUnpaidViaBalance(viaFid);
            db.putInMap(FieldNames.UNPAID_VIA_BALANCE_MAP, viaFid, existingBalance + balance);
        });
        db.commit();
    }

    public void updateUnpaidViaBalance(Map.Entry<String, Long> balance) {
        if(balance == null) return;
        db.putInMap(FieldNames.UNPAID_VIA_BALANCE_MAP, balance.getKey(), balance.getValue());
        db.commit();
    }

    public void clearUnpaidViaBalanceMap() {
        db.clearMap(FieldNames.UNPAID_VIA_BALANCE_MAP);
        db.commit();
    } 

    public void addUnpaidViaBalance(Map.Entry<String, Long> balance) {
        if(balance == null) return;
        Long existingBalance = getUnpaidViaBalance(balance.getKey());
        db.putInMap(FieldNames.UNPAID_VIA_BALANCE_MAP, balance.getKey(), existingBalance + balance.getValue());
        db.commit();
    }

    public void clearUnpaidFixedCostMap() {
        db.clearMap(FieldNames.UNPAID_FIXED_COST_MAP);
        db.commit();
    }

    public void updateUnpaidFixedCostMap(Map<String, Long> costs) {
        for (Map.Entry<String, Long> entry : costs.entrySet()) {
            db.putInMap(FieldNames.UNPAID_FIXED_COST_MAP, entry.getKey(), entry.getValue());
        }
        db.commit();
    }

    public void updateFidVia(Map<String, String> fidViaMap) {
        for (Map.Entry<String, String> entry : fidViaMap.entrySet()) {
            db.putInMap(FieldNames.FID_VIA_MAP, entry.getKey(), entry.getValue());
        }
        db.commit();
    }   

    public void updateFidConsumeVia(Map.Entry<String, String> fidVia) {
        if(fidVia == null) return;
        db.putInMap(FieldNames.FID_VIA_MAP, fidVia.getKey(), fidVia.getValue());
        db.commit();
    }   

    // Getter methods for entire maps
    public Map<String, Long> getUnpaidViaBalance() {
        return db.getAllFromMap(FieldNames.UNPAID_VIA_BALANCE_MAP);
    }

    public Map<String, Long> getFixedCostMap() {
        return db.getAllFromMap(FieldNames.FIXED_COST_MAP);
    }

    public Map<String, Long> getUnpaidFixedCostMap() {
        return db.getAllFromMap(FieldNames.UNPAID_FIXED_COST_MAP);
    }

    public Map<String, Double> getContributorShareMap() {
        return db.getAllFromMap(FieldNames.CONTRIBUTOR_SHARE_MAP);
    }

    // Via share getters
    public Double getOrderViaShare() {
        String share = (String) db.getSettings(FieldNames.ORDER_VIA_SHARE);
        if(share == null || share.isEmpty())
            return null;
        return Double.parseDouble(share);
    }

    public Double getConsumeViaShare() {
        String share = (String) db.getSettings(FieldNames.CONSUME_VIA_SHARE);
        if(share == null || share.isEmpty())
            return null;
        return Double.parseDouble(share);
    }

    // Income/Expense tracking methods
    public List<String> getLastIncome() {
        String lastIncome = (String) db.getSettings(FieldNames.LAST_INCOME);
        return lastIncome == null || lastIncome.isEmpty() ? new ArrayList<>() : Arrays.asList(lastIncome.split(","));
    }

    public void setLastIncome(List<String> lastIncome) {
        if(lastIncome == null || lastIncome.isEmpty()) return;
        db.putSettings(FieldNames.LAST_INCOME, String.join(",", lastIncome));
        db.commit();
    }

    public List<String> getLastExpense() {
        String lastExpense = (String) db.getSettings(FieldNames.LAST_EXPENSE);
        return lastExpense == null || lastExpense.isEmpty() ? new ArrayList<>() : Arrays.asList(lastExpense.split(","));
    }

    public void setLastExpense(List<String> lastExpense) {
        db.putSettings(FieldNames.LAST_EXPENSE, String.join(",", lastExpense));
        db.commit();
    }

    public void setLastSettleHeight(Long height) {
        db.putSettings(FieldNames.LAST_SETTLE_HEIGHT, height.toString());
        db.commit();
    }

    public List<Income> getIncome(int size, boolean fromEnd) {
        List<Income> result = new ArrayList<>(size);
        Map<String, String> map = db.getAllFromMap(FieldNames.INCOME_ORDER_MAP);
        
        // Sort by index in reverse order
        List<Map.Entry<String, String>> sortedMap = sort(map, fromEnd);

        int count = 0;
        for (Map.Entry<String, String> entry : sortedMap) {
            if (count >= size) break;
            
            String cashId = entry.getValue();
            Income income = db.getFromMap(FieldNames.INCOME_MAP, cashId);
            if (income != null) {
                result.add(income);
                count++;
            }
        }
        
        return result;
    }

    public Map<String, Income> getIncomeMap() {
        return db.getAllFromMap(FieldNames.INCOME_MAP);
    }

    public Map<String, Expense> getExpenseMap() {
        return db.getAllFromMap(FieldNames.EXPENSE_MAP);
    }

    // Add new method for batch income updates
    public void updateIncomes(Map<String, Income> incomes) {
        // Add all incomes to the incomeMap
        for (Map.Entry<String, Income> entry : incomes.entrySet()) {
            db.putInMap(FieldNames.INCOME_MAP, entry.getKey(), entry.getValue());
            db.putInMap(FieldNames.INCOME_ORDER_MAP, String.valueOf(currentIncomeIndex++), entry.getKey());
        }
        
        db.commit();
    }

    public String getLastExpenseHeight() {
        return (String) db.getSettings(FieldNames.LAST_EXPENSE_HEIGHT);
    }

    public void setLastExpenseHeight(String height) {
        db.putSettings(FieldNames.LAST_EXPENSE_HEIGHT, height);
        db.commit();
    }

    // Add this method for batch expense updates
    public void updateExpenses(Map<String, Expense> expenses) {
        // Add all expenses to the expenseMap
        for (Map.Entry<String, Expense> entry : expenses.entrySet()) {
            db.putInMap(FieldNames.EXPENSE_MAP, entry.getKey(), entry.getValue());
            db.putInMap(FieldNames.EXPENSE_ORDER_MAP, String.valueOf(currentExpenseIndex++), entry.getKey());
        }
        
        db.commit();
    }

    public List<Expense> getExpense(int size, boolean fromEnd) {
        List<Expense> result = new ArrayList<>(size);
        Map<String, String> map = db.getAllFromMap(FieldNames.EXPENSE_ORDER_MAP);
        
        // Sort by index in reverse order
        List<Map.Entry<String, String>> sortedMap = sort(map, fromEnd);

        int count = 0;
        for (Map.Entry<String, String> entry : sortedMap) {
            if (count >= size) break;
            
            String cashId = entry.getValue();
            Expense expense = db.getFromMap(FieldNames.EXPENSE_MAP, cashId);
            if (expense != null) {
                result.add(expense);
                count++;
            }
        }
        return result;
    }

    @NotNull
    private static List<Map.Entry<String, String>> sort(Map<String, String> orderMap, boolean fromEnd) {
        List<Map.Entry<String, String>> entries = new ArrayList<>(orderMap.entrySet());
        entries.sort((e1, e2) -> {
            long idx1 = Long.parseLong(e1.getKey());
            long idx2 = Long.parseLong(e2.getKey());
            if(fromEnd)return Long.compare(idx2, idx1); // Reverse order
            else return Long.compare(idx1, idx2);
        });
        return entries;
    }

    // Add these methods for payoff management
    public void updatePayoffMap(Map<String, Long> payoffs) {
        for (Map.Entry<String, Long> entry : payoffs.entrySet()) {
            db.putInMap(FieldNames.PAYOFF_MAP, entry.getKey(), entry.getValue());
        }
        db.commit();
    }

    public Map<String, Long> getPayoffMap() {
        return db.getAllFromMap(FieldNames.PAYOFF_MAP);
    }

    public void clearPayoffMap() {
        db.clearMap(FieldNames.PAYOFF_MAP);
        db.commit();
    }

    public void setOrderViaShare(double orderViaShare) {
        db.putSettings(FieldNames.ORDER_VIA_SHARE, String.valueOf(orderViaShare));
        db.commit();
    }

    public void setConsumeViaShare(double consumeViaShare) {
        db.putSettings(FieldNames.CONSUME_VIA_SHARE, String.valueOf(consumeViaShare));
        db.commit();
    }

    public boolean hasNPrices() {
        // Get all metadata entries and check if any keys start with "nPrice_"
        Map<String, String> metaMap = db.getAllFromMap(FieldNames.META_MAP);
        return metaMap.keySet().stream()
            .anyMatch(key -> key.startsWith("nPrice_"));
    }

    public Map<String, Long> getNPriceMap() {
        Map<String, Long> nPriceMap = new HashMap<>();
        Map<String, String> metaMap = db.getAllFromMap(FieldNames.META_MAP);
        
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
        
        db.putInMap(FieldNames.META_MAP, "nPrice_" + apiName, value.toString());
        db.commit();
    }

    public void setDealerMinBalance(Long balance) {
        if(balance == null) return;
        // Convert Long to String before storing
        db.putSettings(FieldNames.DEALER_MIN_BALANCE, balance.toString());
        db.commit();
    }

    public Long getDealerMinBalance() {
        String balanceStr = (String) db.getSettings(FieldNames.DEALER_MIN_BALANCE);
        if (balanceStr == null || balanceStr.isEmpty()) {
            return null;
        }
        
        try {
            return Long.parseLong(balanceStr);
        } catch (NumberFormatException e) {
            System.err.println("Invalid dealer min balance format: " + balanceStr);
            return null;
        }
    }

    public void setMinDistributeBalance(Long balance) {
        if(balance == null) return;
        // Convert Long to String before storing
        db.putSettings(FieldNames.MIN_DISTRIBUTE_BALANCE, balance.toString());
        db.commit();
    }

    public Long getMinDistributeBalance() {
        String balanceStr = (String) db.getSettings(FieldNames.MIN_DISTRIBUTE_BALANCE);
        if (balanceStr == null || balanceStr.isEmpty()) {
            return null;
        }
        
        try {
            return Long.parseLong(balanceStr);
        } catch (NumberFormatException e) {
            System.err.println("Invalid min distribute balance format: " + balanceStr);
            return null;
        }
    }

    public void setAutoScanStrategy(AccountHandler.AutoScanStrategy strategy) {
        if (strategy == null) return;
        db.putSettings(FieldNames.AUTO_SCAN_STRATEGY, strategy.name());
        db.commit();
    }

    public AccountHandler.AutoScanStrategy getAutoScanStrategy() {
        String strategyName = (String) db.getSettings(FieldNames.AUTO_SCAN_STRATEGY);
        if (strategyName == null || strategyName.isEmpty()) {
            return null;
        }
        try {
            return AccountHandler.AutoScanStrategy.valueOf(strategyName);
        } catch (IllegalArgumentException e) {
            // Handle case where stored value doesn't match any enum value
            return null;
        }
    }

    public void setAutoUpdate(boolean autoUpdate) {
        // Deprecated - kept for backward compatibility
        // Convert boolean to strategy
        AccountHandler.AutoScanStrategy strategy = autoUpdate ? 
            AccountHandler.AutoScanStrategy.APIP_CLIENT : 
            AccountHandler.AutoScanStrategy.NO_AUTO_SCAN;
        setAutoScanStrategy(strategy);
    }

    public Boolean getAutoUpdate() {
        // Deprecated - kept for backward compatibility
        AccountHandler.AutoScanStrategy strategy = getAutoScanStrategy();
        return strategy != null && strategy != AccountHandler.AutoScanStrategy.NO_AUTO_SCAN;
    }

    public void setLastHeight(Long height) {
        if(height == null) return;
        // Convert Long to String before storing
        db.putSettings(LAST_HEIGHT, height.toString());
        db.commit();
    }

    public Long getLastHeight() {
        String heightStr = (String) db.getSettings(LAST_HEIGHT);
        if (heightStr == null || heightStr.isEmpty()) {
            return null;
        }
        
        try {
            return Long.parseLong(heightStr);
        } catch (NumberFormatException e) {
            System.err.println("Invalid last height format: " + heightStr);
            return null;
        }
    }

    public void setLastBlockId(String blockId) {
        if(blockId == null) return;
        db.putSettings(FieldNames.LAST_BLOCK_ID, blockId);
        db.commit();
    }

    public String getLastBlockId() {
        return (String) db.getSettings(FieldNames.LAST_BLOCK_ID);
    }

    public void setListenPath(String path) {
        if (path == null) return;
        db.putSettings(FieldNames.LISTEN_PATH, path);
        db.commit();
    }

    public String getListenPath() {
        return (String) db.getSettings(FieldNames.LISTEN_PATH);
    }

    public void removeIncomeSinceHeight(long height) {
        // Get all income entries
        Map<String, Income> incomeMap = db.getAllFromMap(FieldNames.INCOME_MAP);
        Map<String, String> orderMap = db.getAllFromMap(FieldNames.INCOME_ORDER_MAP);
        
        // Create lists to store cashIds to remove
        List<String> cashIdsToRemove = new ArrayList<>();
        List<String> orderKeysToRemove = new ArrayList<>();
        
        // Find entries to remove
        for (Map.Entry<String, Income> entry : incomeMap.entrySet()) {
            if (entry.getValue().getBirthHeight() > height) {
                cashIdsToRemove.add(entry.getKey());
            }
        }
        
        // Find order entries to remove
        for (Map.Entry<String, String> entry : orderMap.entrySet()) {
            if (cashIdsToRemove.contains(entry.getValue())) {
                orderKeysToRemove.add(entry.getKey());
            }
        }
        
        // Remove entries
        db.removeFromMap(FieldNames.INCOME_MAP, cashIdsToRemove);
        db.removeFromMap(FieldNames.INCOME_ORDER_MAP, orderKeysToRemove);
        
        // Update currentIncomeIndex
        currentIncomeIndex = db.getAllFromMap(FieldNames.INCOME_ORDER_MAP).size();
        db.commit();
    }

    public void removeExpenseSinceHeight(long height) {
        // Get all expense entries
        Map<String, Expense> expenseMap = db.getAllFromMap(FieldNames.EXPENSE_MAP);
        Map<String, String> orderMap = db.getAllFromMap(FieldNames.EXPENSE_ORDER_MAP);
        
        // Create lists to store cashIds to remove
        List<String> cashIdsToRemove = new ArrayList<>();
        List<String> orderKeysToRemove = new ArrayList<>();
        
        // Find entries to remove
        for (Map.Entry<String, Expense> entry : expenseMap.entrySet()) {
            if (entry.getValue().getBirthHeight() > height) {
                cashIdsToRemove.add(entry.getKey());
            }
        }
        
        // Find order entries to remove
        for (Map.Entry<String, String> entry : orderMap.entrySet()) {
            if (cashIdsToRemove.contains(entry.getValue())) {
                orderKeysToRemove.add(entry.getKey());
            }
        }
        
        // Remove entries
        db.removeFromMap(FieldNames.EXPENSE_MAP, cashIdsToRemove);
        db.removeFromMap(FieldNames.EXPENSE_ORDER_MAP, orderKeysToRemove);
        
        // Update currentExpenseIndex
        currentExpenseIndex = db.getAllFromMap(FieldNames.EXPENSE_ORDER_MAP).size();
        db.commit();
    }

    public Income getIncomeById(String cashId) {
        return db.getFromMap(FieldNames.INCOME_MAP, cashId);
    }

    public Expense getExpenseById(String cashId) {
        return db.getFromMap(FieldNames.EXPENSE_MAP, cashId);
    }
} 