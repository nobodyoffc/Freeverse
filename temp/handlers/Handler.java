package handlers;

import appTools.Inputer;
import appTools.Menu;
import appTools.Settings;
import appTools.Shower;
import clients.ApipClient;
import db.EasyDB;
import db.LocalDB;
import fcData.FcEntity;
import feip.feipData.Service;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Hex;
import utils.JsonUtils;
import utils.ObjectUtils;
import utils.StringUtils;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static constants.FieldNames.*;

/**
 * This class is a generic handler for all types of FC handlers.
 * It has a handler type to identify the type of data it handles.
 * It has a sort type to identify the sort type of the main local database.
 * It has a main persistent local database with class type T.
 * It has a meta map to persist metadata of the handler.
 * Other persistent maps can be created and used by createMap(), putInMap(), getFromMap(), putAllInMap(), removeFromMap(), clearMap().
 */
public class Handler<T extends FcEntity>{
    protected static final Logger log = LoggerFactory.getLogger(Handler.class);
    protected final LocalDB<T> localDB;
    protected final FcEntity.SimpleFcSerializer<T> valueSerializer;
    protected final Class<T> itemClass;
    protected final ApipClient apipClient;
    protected final CashHandler cashHandler;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    protected final String mainFid;
    private final String sid;
    private final byte[] symKey;
    protected final byte[] priKey;
    protected final String itemName;
    private final HandlerType handlerType;
    protected final Settings settings;
    protected volatile AtomicBoolean isRunning = new AtomicBoolean(false);
    protected List<String> hideFieldsInListing;
    
        public Handler(){
            this.handlerType = null;
            this.mainFid = null;
            this.sid = null;
            this.symKey = null;
            this.priKey = null;
            this.valueSerializer = null;
            this.localDB = null;
            this.itemClass = null;
            this.apipClient = null;
            this.itemName = null;
            this.cashHandler = null;
            this.settings = null;
        }
        public Handler(HandlerType handlerType){
            this.handlerType = handlerType;
            this.mainFid = null;
            this.sid = null;
            this.symKey = null;
            this.priKey = null;
            this.valueSerializer = null;
            this.localDB = null;
            this.itemClass = null;
            this.apipClient = null;
            this.itemName = handlerType.name().toLowerCase();
            this.cashHandler = null;
            this.settings = null;
        }
    
        public Handler(HandlerType handlerType, String dbDir, LocalDB.SortType sortType, FcEntity.SimpleFcSerializer<T> valueSerializer, Class<T> itemClass, ApipClient apipClient, CashHandler cashHandler) {
            this.handlerType = handlerType;
            this.valueSerializer = valueSerializer;
            this.localDB = new EasyDB<>(new FcEntity.SimpleFcSerializer<T>(itemClass), sortType, itemClass);
            this.localDB.initialize(getMainFid(), getSid(), dbDir, handlerType.toString());
            createMap(LocalDB.LOCAL_REMOVED_MAP, FcEntity.SimpleSerializer.getSerializer(Long.class));
            createMap(LocalDB.ON_CHAIN_DELETED_MAP, FcEntity.SimpleSerializer.getSerializer(Long.class));
            this.mainFid = null;
            this.sid = null;
            this.symKey = null;
            this.priKey = null;
            this.itemClass = itemClass;
            this.apipClient = apipClient;
            this.itemName = handlerType.name().toLowerCase();
            this.cashHandler = cashHandler;
            this.settings = null;
        }
        public Handler(Settings settings, HandlerType handlerType){
            this(settings,handlerType,null,null,null, false);
        }
        public Handler(Settings settings, HandlerType handlerType, LocalDB.SortType sortType, FcEntity.SimpleFcSerializer<T> valueSerializer, Class<T> itemClass, boolean withLocalDB) {
            this.handlerType = handlerType;
            this.settings = settings;
            this.mainFid = settings.getMainFid();
            this.sid = settings.getSid();
            this.symKey = settings.getSymKey();
            this.priKey = Settings.getMainFidPriKey(symKey,settings);
    
            if(withLocalDB){
                String dbDir = settings.getDbDir();
                this.valueSerializer = valueSerializer;
                this.localDB = new EasyDB<>(valueSerializer, sortType, itemClass);
                this.localDB.initialize(getMainFid(), getSid(), dbDir, handlerType.name());
            }else{
                this.valueSerializer = null;
                this.localDB = null;
            }
    
            this.itemClass = itemClass;
            this.apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);
            this.itemName = handlerType.name().toLowerCase();
            this.cashHandler = (CashHandler) settings.getHandler(HandlerType.CASH);
        }
    
        public void menu(BufferedReader br, boolean withSettings) {
            Menu menu = new Menu(handlerType.toString(), this::close);
    
            addBasicMenuItems(br, menu);
            if(withSettings)
                menu.add("Settings", () -> settings.setting(br, null));
    
            menu.showAndSelect(br);
        }
    
        protected void put(String id, T item) {
            if (localDB == null) return;
            lock.writeLock().lock();
            try {
                localDB.put(id, item);
            } finally {
                lock.writeLock().unlock();
            }
        }
    
        protected void putAll(Map<String, T> items) {
            if (localDB == null) return;
            lock.writeLock().lock();
            try {
                localDB.putAll(items);
            } finally {
                lock.writeLock().unlock();
            }
        }
    
        protected void putAll(List<T> items, String idField) {
            if (localDB == null) return;
            lock.writeLock().lock();
            try {
                localDB.putAll(items, idField);
            } finally {
                lock.writeLock().unlock();
            }
        }
    
    
        protected T get(String id) {
            if (localDB == null) return null;
            lock.readLock().lock();
            try {
                return localDB.get(id);
            } finally {
                lock.readLock().unlock();
            }
        }
    
        protected List<T> get(List<String> ids) {
            if (localDB == null) return null;
            lock.readLock().lock();
            try {
                return localDB.get(ids);
            } finally {
                lock.readLock().unlock();
            }
        }
    
        protected Map<String, T> getAll() {
            if (localDB == null) return null;
            lock.readLock().lock();
            try {
                return localDB.getAll();
            } finally {
                lock.readLock().unlock();
            }
        }
    
        protected NavigableMap<Long, String> getIndexIdMap() {
            if (localDB == null) return null;
            return localDB.getIndexIdMap();
        }
    
        protected NavigableMap<String, Long> getIdIndexMap() {
            if (localDB == null) return null;
            return localDB.getIdIndexMap();
        }
    
        protected T getItemById(String id) {
            if (localDB == null) return null;
            return localDB.get(id);
        }
    
        protected T getItemByIndex(long index) {
            if (localDB == null) return null;
            String id = localDB.getIndexIdMap().get(index);
            return id != null ? localDB.get(id) : null;
        }
    
        protected Long getIndexById(String id) {
            if (localDB == null) return null;
            return localDB.getIdIndexMap().get(id);
        }
    
        protected String getIdByIndex(long index) {
            if (localDB == null) return null;
            return localDB.getIndexIdMap().get(index);
        }
    
        protected List<T> getItemList(Integer size, Long fromIndex, String fromId,
                boolean isFromInclude, Long toIndex, String toId, boolean isToInclude, boolean isFromEnd) {
            if (localDB == null) return null;
            return localDB.getList(size, fromId, fromIndex, isFromInclude, toId, toIndex, isToInclude, isFromEnd);
        }
    
        protected LinkedHashMap<String, T> getItemMap(int size, Long fromIndex, String fromId,
                boolean isFromInclude, Long toIndex, String toId, boolean isToInclude, boolean isFromEnd) {
            if (localDB == null) return null;
            lock.readLock().lock();
            try {
                return localDB.getMap(size, fromId, fromIndex, isFromInclude, toId, toIndex, isToInclude, isFromEnd);
            } finally {
                lock.readLock().unlock();
            }
        }
        public LocalDB.SortType getDatabaseSortType() {
            if (localDB == null) return null;
            return localDB.getSortType();
        }
    
        public AtomicBoolean getIsRunning() {
            return isRunning;
        }
    
        public void setIsRunning(AtomicBoolean isRunning) {
            this.isRunning = isRunning;
        }
    
        protected <T2> List<T2> loadAllOnChainItems(String index, String sortField, String termField, Long lastHeight, Boolean active, ApipClient apipClient, Class<T2> tClass, BufferedReader br, boolean freshLast) {
            if(apipClient==null){
                System.out.println("No ApipClient is available.");
                return null;
            }
            List<T2> itemList = new ArrayList<>();

            List<String> last = null;
            if(freshLast) {
                Object obj = getMeta(FieldNames.LAST);
                last = ObjectUtils.objectToList(obj, String.class);
            }

            while (true) {
                List<T2> subSecretList = apipClient.loadSinceHeight(index, FieldNames.ID,sortField,termField,mainFid, lastHeight, ApipClient.DEFAULT_SIZE, last, active,tClass);
                if (subSecretList == null || subSecretList.isEmpty()) break;
                List<T2> batchChosenList;
                if(br!=null) {
                    batchChosenList = Inputer.chooseMultiFromListGeneric(subSecretList, 0, subSecretList.size(), "Choose the items you want:", br);
                    itemList.addAll(batchChosenList);
                }else itemList.addAll(subSecretList);
                last = apipClient.getFcClientEvent().getResponseBody().getLast();
    
                if (subSecretList.size() < ApipClient.DEFAULT_SIZE) break;
            }
            Long bestHeight = apipClient.getFcClientEvent().getResponseBody().getBestHeight();
            if(bestHeight==null)bestHeight = apipClient.bestHeight();
            if(freshLast) {
                if (last != null && !last.isEmpty()) putMeta(FieldNames.LAST, last);
                putMeta(FieldNames.LAST_HEIGHT, bestHeight);
            }
            return itemList;
        }
    
        @org.jetbrains.annotations.Nullable
        protected String carve(String opReturnStr, long cd, BufferedReader br) {
            if (br != null && !Inputer.askIfYes(br, "Are you sure to do below operation on chain?\n" + JsonUtils.jsonToNiceJson(opReturnStr)+ "\n")) {
                return null;
            }
    
            String result;
            if(cashHandler!=null) result = cashHandler.carve(opReturnStr, cd);
            else if(apipClient!=null) result = CashHandler.carve(opReturnStr, cd, priKey, apipClient);
            else return null;
    
            if (Hex.isHex32(result)) {
                System.out.println("Carved: " + result + ".\nWait a few minutes for confirmations before updating secrets...");
                log.info("Carved: "+result);
                return result;
            } else if (StringUtils.isBase64(result)) {
                System.out.println("Sign the TX and broadcast it:\n" + result);
            } else {
                System.out.println("Failed to carve:" + result);
                log.debug("Carve failed:" + result);
            }
            return null;
        }
    
        protected void showItemDetails(List<T> items, BufferedReader br) {
            JsonUtils.showListInNiceJson(items, br);
        }
    
        protected void removeItems(List<String> itemIds, BufferedReader br) {
            if(itemIds==null || itemIds.isEmpty()) return;
            if(Inputer.askIfYes(br, "Remove " + itemIds.size() + " items from local?")){
                remove(itemIds);
            }
            System.out.println("Removed " + itemIds.size() + " items from local.");
        }
    
        public enum HandlerType {
            TEST,
            ACCOUNT,
            CASH,
            CONTACT,
            CID,
            DISK,
            GROUP,
            HAT,
            MAIL,
            SECRET,
            ROOM,
            SESSION,
            NONCE,
            MEMPOOL,
            WEBHOOK,
            TALK_ID,
            TALK_UNIT,
            TEAM;
    
            @Override
            public String toString() {
                return this.name();
            }
    
            public static HandlerType fromString(String input) {
                if (input == null) {
                    return null;
                }
                for (HandlerType type : HandlerType.values()) {
                    if (type.name().equalsIgnoreCase(input)) {
                        return type;
                    }
                }
                return null;
            }
        }
    
        // Method to create a new map by name
        @SuppressWarnings("unchecked")
        public void createMap(String mapName, FcEntity.SimpleSerializer<?> serializer) {
            lock.writeLock().lock();
            try {
                // Get existing map names from meta
                Set<String> mapNames = (Set<String>) localDB.getMetaMap().getOrDefault(LocalDB.MAP_NAMES_META_KEY, new HashSet<String>());
                
                // Create the new map if it doesn't exist
                if (!mapNames.contains(mapName)) {
                    // Add new map name to the set and update meta
                    mapNames.add(mapName);
                    localDB.putMeta(LocalDB.MAP_NAMES_META_KEY, mapNames);
                    localDB.putMeta(mapName + "_serializer", FcEntity.SimpleSerializer.getSerializerInfo(serializer));
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
    
        // Method to put an item into a named map
        public <V> void putInMap(String mapName, String key, V value, FcEntity.SimpleSerializer<V> serializer) {
            if (localDB == null) return;
            lock.writeLock().lock();
            try {
                localDB.putInMap(mapName, key, value, serializer);
            } finally {
                lock.writeLock().unlock();
            }
        }
    
        // Method to get an item from a named map
        public <V> V getFromMap(String mapName, String key, FcEntity.SimpleSerializer<V> serializer) {
            if (localDB == null) return null;
            lock.readLock().lock();
            try {
                return localDB.getFromMap(mapName, key, serializer);
            } finally {
                lock.readLock().unlock();
            }
        }
    
        public List<T> searchInValue(String part) {
            return localDB.searchString(part);
        }
    
        // Method to get multiple items from a named map
        public <V> List<V> getFromMap(String mapName, List<String> keyList, FcEntity.SimpleSerializer<V> serializer) {
            lock.readLock().lock();
            try {
                return localDB.getFromMap(mapName, keyList,serializer);
            } finally {
                lock.readLock().unlock();
            }
        }
    
        public <V> Map<String, V> getAllFromMap(String mapName, FcEntity.SimpleSerializer<V> serializer) {
            if (localDB == null) return null;
            lock.readLock().lock();
            try {
                return localDB.getAllFromMap(mapName, serializer);
            } finally {
                lock.readLock().unlock();
            }
        }
    
    
        // Method to put multiple items into a named map
        public <V> void putAllInMap(String mapName, List<String> keyList, List<V> valueList, FcEntity.SimpleSerializer<V> serializer) {
            if (localDB == null) return;
            lock.writeLock().lock();
            try {
                localDB.putAllInMap(mapName, keyList, valueList, serializer);
            } finally {
                lock.writeLock().unlock();
            }
        }
    
        // Method to remove an item from a named map
        public void removeFromMap(String mapName, String key) {
            if (localDB == null) return;
            lock.writeLock().lock();
            try {
                localDB.removeFromMap(mapName, key);
            } finally {
                lock.writeLock().unlock();
            }
        }
    
        public void removeFromMap(String mapName, List<String> keys) {
            if (localDB == null) return;
            lock.writeLock().lock();
            try {
                localDB.removeFromMap(mapName, keys);
            } finally {
                lock.writeLock().unlock();
            }
        }
    
        // Method to clear all items from a named map
        public void clearMap(String mapName) {
            if (localDB == null) return;
            lock.writeLock().lock();
            try {
                localDB.clearMap(mapName);
            } finally {
                lock.writeLock().unlock();
            }
        }
    
        public Object getMeta(String key) {
            if (localDB == null) return null;
            lock.readLock().lock();
            try {
                return localDB.getMeta(key);
            } finally {
                lock.readLock().unlock();
            }
        }
    
        public void putMeta(String key, Object value) {
            if (localDB == null) return;
            lock.writeLock().lock();
            try {
                localDB.putMeta(key, value);
            } finally {
                lock.writeLock().unlock();
            }
        }
    
        public void removeMeta(String key) {
            if (localDB == null) return;
            lock.writeLock().lock();
            try {
                localDB.removeMeta(key);
            } finally {
                lock.writeLock().unlock();
            }
        }
    
        public void chooseToShow(List<T> itemList, BufferedReader br){
            if(itemList.isEmpty()){
                return;
            }
            List<T> chosenItems = showAndChooseItems("Choose to show items...", itemList, 20, br, true, true);
            if(chosenItems==null || chosenItems.isEmpty()){
                return;
            }
            JsonUtils.showListInNiceJson(chosenItems, br);
        }
    
    
        public List<T> showAndChooseItems(String promote, @Nullable List<T> itemList, Integer sizeInPage, @Nullable BufferedReader br, boolean isFromEnd, boolean withChoose) {
            // Implement the logic to display webhook requests
            System.out.println(promote);
            int count = 0;
    
             if(sizeInPage==null){
                sizeInPage = Shower.DEFAULT_SIZE;
            }
    
            int totalItems = localDB.getSize();
            int totalPages = (totalItems + sizeInPage - 1) / sizeInPage; // Ceiling division to handle non-even division
            int currentPage;
    
            List<T> chosenItems = new ArrayList<>();
            if(itemList!=null){
                showItemList(promote, itemList, count);
                if(br!=null && withChoose){
                    List<Integer> choices = Inputer.chooseMulti(br, count, count+itemList.size());
                    if(choices.size()==1 && choices.get(0)==(-1))
                        return itemList;
                    if(choices.isEmpty()){
                        return null;
                    }
                    for(Integer choice : choices){
                        chosenItems.add(itemList.get(choice-1));
                    }
                }
                if(withChoose)System.out.println(chosenItems.size() +" items are chosen.");
                return chosenItems;
            }
    
            Long fromIndex = null;
            while(true){
                List<T> batchItemList = getItemList(sizeInPage,fromIndex , null, false, null, null, true, isFromEnd);
    
                if(batchItemList.isEmpty())break;
    
                try {
                    fromIndex = localDB.getTempIndex();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
    
                showItemList(promote, batchItemList, 0);//count);
                if(br!=null && withChoose){
                    List<Integer> choices = Inputer.chooseMulti(br, 0, batchItemList.size());
                    if(choices.isEmpty()){
                        continue;
                    }
                    if(choices.size()==1 && choices.get(0)==(-1)){
                        chosenItems.addAll(batchItemList);
                    }else {
                        List<T> items = new ArrayList<>();
                        for (Integer choice : choices) {
                            items.add(batchItemList.get(choice - 1));
                        }
                        chosenItems.addAll(items);
                        System.out.println(items.size() + " added.");
                    }
                }
                count += sizeInPage;
                currentPage = count / sizeInPage;
                if(currentPage >= totalPages){
                    break;
                }
                if(br!=null && Inputer.askIfYes(br, "Do you want to stop?")){
                    break;
                }else{
                    fromIndex = localDB.getTempIndex();
                }
            }
            System.out.println(chosenItems.size() +" items are chosen.");
            return chosenItems;
        }
    
        protected void showItemList(String title, List<T> itemList, int beginFrom) {
            Shower.showDataTable(title, itemList,beginFrom,hideFieldsInListing, true);
    }
    protected void opItems(List<T> items, String ask, BufferedReader br){
        System.out.println("To override this method, implement it in the subclass.");
    }

    protected List<T> searchItems(BufferedReader br, boolean withChoose,boolean withOperation){
        String searchStr = Inputer.inputString(br, "Input the search string:");
        List<T> foundItems = searchInValue(searchStr);
        System.out.println();
        if(foundItems.size()>0 && Inputer.askIfYes(br, "Found "+foundItems.size()+" items. List and choose?")){
            showItemList(handlerType.toString(), foundItems, 0);
            if(withChoose){
                List<Integer> choices = Inputer.chooseMulti(br, 0, foundItems.size());
                if(choices.isEmpty()){
                    return null;
                }
                List<T> items = new ArrayList<>();

                if(choices.size()==1 && choices.get(0)==(-1)){
                    items.addAll(foundItems);
                }else {
                    for (Integer choice : choices) {
                        items.add(foundItems.get(choice - 1));
                    }
                }
                if(withOperation){
                    opItems(items, "Operate on the selected items", br);
                }
                return items;
            }else{
                return foundItems;
            }
        }
        return null;
    }

    public void addBasicMenuItems(BufferedReader br, Menu menu) {
        menu.add("List Local "+ StringUtils.capitalize(itemName), () -> listItems(br));
        menu.add("Search Local "+ StringUtils.capitalize(itemName), () -> searchItems(br, true,true));
        menu.add("Add Local "+ StringUtils.capitalize(itemName), addItemsToLocalDB(br, itemName));
        menu.add("Clear Local Database", () -> clearTheDatabase(br));
    }

    private void clearTheDatabase(BufferedReader br) {
        if(Inputer.askIfYes(br, "Are you sure you want to clear the entire database? This will remove ALL data including metadata.")){
            clearDB();
            System.out.println("Database cleared completely.");
        }
    }

    @NotNull
    private Runnable addItemsToLocalDB(BufferedReader br, String itemName) {
        return () -> {
            try {
                Map<String, T> items = new HashMap<>();
                System.out.print("Enter number of "+itemName+": ");
                int numItems = Integer.parseInt(br.readLine().trim());
                for (int i = 0; i < numItems; i++) {
                    System.out.print("Enter ID: ");
                    String id = br.readLine().trim();
                    System.out.print("Enter "+itemName+": ");
                    T item = Inputer.createFromUserInput(br, itemClass, null, null);
                    if(item==null)return;
                    items.put(id, item);
                }
                putAll(items);
                System.out.println("\n"+items.size() +" "+ itemName+ "s added.");
            } catch (IOException | ReflectiveOperationException e) {
                System.out.println("Error: " + e.getMessage());
            }
        };
    }


    protected void listItems(BufferedReader br){
        List<T> chosenItems = showAndChooseItems("Chose to show details...",null,Shower.DEFAULT_SIZE,br,true,true);

        if(chosenItems==null || chosenItems.isEmpty()){
            return;
        }
        opItems(chosenItems,"What you want to do with them?",br);
    }

    public void remove(String id) {
        if (localDB == null) return;
        lock.writeLock().lock();
        try {
            localDB.remove(id);
            markAsLocallyRemoved(Arrays.asList(id));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clear() {
        if (localDB == null) return;
        lock.writeLock().lock();
        try {
            localDB.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void remove(List<String> ids) {
        if (localDB == null) return;
        lock.writeLock().lock();
        try {
            localDB.removeList(ids);
        } finally {
            lock.writeLock().unlock();
        }
    }


    public List<T> chooseItems(BufferedReader br) {
        List<T> chosenItems = new ArrayList<>();
        String lastKey = null;
        int totalDisplayed = 0;
        Integer size = Shower.DEFAULT_SIZE; //Inputer.inputInteger(br, "Enter the size of a page: ", 1, 0);

        while (true) {
            List<T> currentList = getItemList(size, null, lastKey, false, null, null, true, true);
            if (currentList.isEmpty()) {
                break;
            }

            lastKey = localDB.getTempId();//currentList.get(currentList.size()-1);

            List<T> result = chooseItemList(currentList,  totalDisplayed, br);

            totalDisplayed += currentList.size();

            if (result == null)
                continue;
            result.remove(null);  // Remove the break signal
            chosenItems.addAll(result);
        }

        return chosenItems;
    }

    private List<T> chooseItemList(List<T> currentList, int totalDisplayed, BufferedReader br) {
        String title = "Choose Items";
        List<T> chosenItems = new ArrayList<>();
        Shower.showDataTable(title, currentList, totalDisplayed, true);

        System.out.println("Enter item numbers to select (comma-separated), 'a' for all. 'q' to quit, or press Enter for more:");
        String input;
        while(true) {
            input = Inputer.inputString(br);

            if ("".equals(input)) {
                return null;  // Signal to continue to next page
            }

            if (input.equals("q")) {
                return chosenItems;
            }

            if (input.equals("a")) {
                chosenItems.addAll(currentList);
                chosenItems.add(null);  // Signal to break the loop
                return chosenItems;
            }

            String[] selections = input.split(",");
            try {
                boolean hasInvalidInput = false;
                for (String selection : selections) {
                    try {
                        int index = Integer.parseInt(selection.trim()) - 1;
                        if (index >= 0 && index < totalDisplayed + currentList.size()) {
                            int listIndex = index - totalDisplayed;
                            chosenItems.add(currentList.get(listIndex));
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid input: " + selection+". Try again.");
                        hasInvalidInput = true;
                        break;
                    }
                }
                if (hasInvalidInput) {
                    continue;  
                }
            } catch (Exception e) {
                System.out.println("Error processing input. Try again.");
                continue;
            }
            return chosenItems;
        }
    }

    public HandlerType getHandlerType() {
        return handlerType;
    }


    // Method to get all map names
    public Set<String> getMapNames() {
        if (localDB == null) return new HashSet<>();
        lock.readLock().lock();
        try {
            return localDB.getMapNames();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clearDB() {
        if (localDB == null) return;
        lock.writeLock().lock();
        try {
            localDB.clearDB();
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Add new methods for managing removed and deleted items
    public void markAsLocallyRemoved(List<String> ids) {
        if (localDB == null) return;
        lock.writeLock().lock();
        try {
            List<Long> times = new ArrayList<>();
            long currentTime = System.currentTimeMillis();
            for(String ignored : ids){
                times.add(currentTime);
            }
            putAllInMap(LocalDB.LOCAL_REMOVED_MAP, ids, times, FcEntity.SimpleSerializer.getSerializer(Long.class));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void markAsOnChainDeleted(List<String> ids) {
        if (localDB == null) return;
        lock.writeLock().lock();
        try {
            List<Long> times = new ArrayList<>();
            long currentTime = System.currentTimeMillis();
            for(String ignored : ids){
                times.add(currentTime);
            }
            putAllInMap(LocalDB.ON_CHAIN_DELETED_MAP, ids, times, FcEntity.SimpleSerializer.getSerializer(Long.class));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Long getLocalRemovalTime(String id) {
        if (localDB == null) return null;
        lock.readLock().lock();
        try {
            return getFromMap(LocalDB.LOCAL_REMOVED_MAP, id, FcEntity.SimpleSerializer.getSerializer(Long.class));
        } finally {
            lock.readLock().unlock();
        }
    }

    public Long getOnChainDeletionTime(String id) {
        if (localDB == null) return null;
        lock.readLock().lock();
        try {
            return getFromMap(LocalDB.ON_CHAIN_DELETED_MAP, id, FcEntity.SimpleSerializer.getSerializer(Long.class));
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<String, Long> getAllLocallyRemoved() {
        if (localDB == null) return new HashMap<>();
        lock.readLock().lock();
        try {
            return getAllFromMap(LocalDB.LOCAL_REMOVED_MAP, FcEntity.SimpleSerializer.getSerializer(Long.class));
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<String, Long> getAllOnChainDeletedRecords() {
        if (localDB == null) return new HashMap<>();
        lock.readLock().lock();
        try {
            return getAllFromMap(LocalDB.ON_CHAIN_DELETED_MAP, FcEntity.SimpleSerializer.getSerializer(Long.class));
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clearLocallyRemoved(String id) {
        if (localDB == null) return;
        lock.writeLock().lock();
        try {
            removeFromMap(LocalDB.LOCAL_REMOVED_MAP, id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clearOnChainDeleted(String id) {
        if (localDB == null) return;
        lock.writeLock().lock();
        try {
            removeFromMap(LocalDB.ON_CHAIN_DELETED_MAP, id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clearAllLocallyRemoved() {
        if (localDB == null) return;
        lock.writeLock().lock();
        try {
            clearMap(LocalDB.LOCAL_REMOVED_MAP);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clearAllOnChainDeleted() {
        if (localDB == null) return;
        lock.writeLock().lock();
        try {
            clearMap(LocalDB.ON_CHAIN_DELETED_MAP);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public FcEntity.SimpleFcSerializer<T> getValueSerializer() {
        return valueSerializer;
    }

    public String getMainFid() {
        return mainFid;
    }

    public String getSid() {
        return sid;
    }
    public void close() {
        if (localDB != null)
            localDB.close();
        isRunning.set(false);
    }

}
