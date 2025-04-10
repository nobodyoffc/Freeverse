package handlers;

import appTools.Inputer;
import appTools.Menu;
import appTools.Settings;
import appTools.Shower;
import clients.ApipClient;
import crypto.Hash;
import db.EasyDB;
import db.LevelDB;
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

import static appTools.Shower.DEFAULT_PAGE_SIZE;
import static constants.FieldNames.*;
import static db.LocalDB.LOCAL_REMOVED_MAP;
import static db.LocalDB.ON_CHAIN_DELETED_MAP;

/**
 * This class is a generic handler for all types of FC handlers.
 * It has a handler type to identify the type of data it handles.
 * It has a sort type to identify the sort type of the main local database.
 * It has a main persistent local database with class type T.
 * It has a meta map to persist metadata of the handler.
 * Other persistent maps can be created and used by createMap(), putInMap(), getFromMap(), putAllInMap(), removeFromMap(), clearMap().
 */
public abstract class Handler<T extends FcEntity>{
    protected static final Logger log = LoggerFactory.getLogger(Handler.class);
    protected db.LocalDB<T> localDB;
    protected final Class<T> itemClass;
    protected final ApipClient apipClient;
    protected CashHandler cashHandler;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    protected String name;
    protected String mainFid;
    private final String sid;
    private final byte[] symKey;
    protected final byte[] priKey;
    protected final String itemName;
    private final HandlerType handlerType;
    protected final Settings settings;
//    protected volatile AtomicBoolean isRunning = new AtomicBoolean(false);
    protected List<String> hideFieldsInListing;
    private final LocalDB.DbType dbType;
    private final Boolean opOnChain;
    
        public Handler(){
            this.handlerType = null;
            this.mainFid = null;
            this.sid = null;
            this.symKey = null;
            this.priKey = null;
            this.localDB = null;
            this.itemClass = null;
            this.apipClient = null;
            this.itemName = null;
            this.cashHandler = null;
            this.settings = null;
            this.dbType = null;
            this.opOnChain=false;
        }
        public Handler(HandlerType handlerType){
            this.handlerType = handlerType;
            this.dbType = null;
            this.sid = null;
            this.symKey = null;
            this.priKey = null;
            this.mainFid = null;
            this.localDB = null;
            this.itemClass = null;
            this.apipClient = null;
            this.itemName = handlerType.name().toLowerCase();
            this.cashHandler = null;
            this.settings = null;
            this.opOnChain=false;
        }
    public Handler(Settings settings, HandlerType handlerType) {
        this(settings, handlerType,null, null, false, false);
    }
    public Handler(Settings settings, HandlerType handlerType, LocalDB.SortType sortType, Class<T> itemClass, boolean withLocalDB, Boolean opOnChain) {
        this.settings = settings;
        this.mainFid = settings.getMainFid();
        this.sid = settings.getSid();
        this.handlerType = handlerType;
        this.itemClass = itemClass;
        this.symKey = settings.getSymKey();
        this.priKey = Settings.getMainFidPriKey(symKey,settings);
        this.dbType = settings.getLocalDBType()==null? LocalDB.DbType.LEVEL_DB:settings.getLocalDBType();
        this.opOnChain = opOnChain;

        this.itemName = handlerType.toString();

        log.info("Creating {} Handler: {}", itemName, this.mainFid);

        if(withLocalDB){
            initializeDB(this.mainFid, sid, settings.getDbDir(), itemName, sortType, itemClass);
            if(opOnChain){
                localDB.createMap(LOCAL_REMOVED_MAP, Long.class);
                localDB.createMap(ON_CHAIN_DELETED_MAP, Long.class);
            }
        }else{
            this.localDB = null;
        }

        this.apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);
        this.cashHandler = (CashHandler) settings.getHandler(HandlerType.CASH);
    }

    public void menu(BufferedReader br, boolean withSettings) {
        Menu menu = new Menu(handlerType.toString(), this::close);
        addBasicMenuItems(br, menu);
        if(withSettings)
            menu.add("Settings", () -> settings.setting(br, null));

        menu.showAndSelect(br);
    }

    protected T getByIndex(long index) {
        if (localDB == null) return null;
        String id = localDB.getIndexIdMap().get(index);
        return id != null ? localDB.get(id) : null;
    }

    public db.LocalDB.SortType getDatabaseSortType() {
        if (localDB == null) return null;
        return localDB.getSortType();
    }

//    public AtomicBoolean getIsRunning() {
//        return isRunning;
//    }

//    public void setIsRunning(AtomicBoolean isRunning) {
//        this.isRunning = isRunning;
//    }

    protected <T2> List<T2> loadAllOnChainItems(String index, String sortField, String termField, Long lastHeight, Boolean active, ApipClient apipClient, Class<T2> tClass, BufferedReader br, boolean freshLast) {
        if(apipClient==null){
            System.out.println("No ApipClient is available.");
            return null;
        }
        List<T2> itemList = new ArrayList<>();

        List<String> last = null;
        if(freshLast) {
            Object obj = localDB.getState(LAST);
            last = ObjectUtils.objectToList(obj, String.class);
        }

        while (true) {
            List<T2> subSecretList = apipClient.loadSinceHeight(index,ID,sortField,termField,mainFid, lastHeight, ApipClient.DEFAULT_SIZE, last, active,tClass);
            if (subSecretList == null || subSecretList.isEmpty()) break;
            List<T2> batchChosenList;
            if(br!=null) {
                batchChosenList = Shower.showOrChooseListInPages(tClass.getSimpleName(),subSecretList,DEFAULT_PAGE_SIZE, null, true, tClass,br);
                if(batchChosenList!=null && !batchChosenList.isEmpty())
                    itemList.addAll(batchChosenList);
            }else itemList.addAll(subSecretList);
            last = apipClient.getFcClientEvent().getResponseBody().getLast();

            if (subSecretList.size() < ApipClient.DEFAULT_SIZE) break;
        }
        Long bestHeight = apipClient.getFcClientEvent().getResponseBody().getBestHeight();
        if(bestHeight==null)bestHeight = apipClient.bestHeight();
        if(freshLast) {
            if (last != null && !last.isEmpty()) localDB.putState(LAST, last);
            localDB.putState(LAST_HEIGHT, bestHeight);
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
        if(br!=null)Menu.anyKeyToContinue(br);
    }

    protected void removeItems(List<String> itemIds, BufferedReader br) {
        if(itemIds==null || itemIds.isEmpty()) return;
        if(Inputer.askIfYes(br, "Remove " + itemIds.size() + " items from local?")){
            remove(itemIds);
        }
        System.out.println("Removed " + itemIds.size() + " items from local.");
    }
    @NotNull
    protected Menu newMenu(String title, boolean isRootMenu) {
        Menu menu;
        if(isRootMenu)menu = new Menu(title, settings::close);
        else menu = new Menu(title,this::closeMenu);
        return menu;
    }
    /**
     * Gets a metadata value as Long
     * @param metaKey metadata key
     * @return Long value or 0L if not found or not a Long
     */
    public Long getLongState(String metaKey) {
        Object value = localDB.getState(metaKey);
        if (value instanceof Long) {
            return (Long) value;
        }
        return 0L;
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
    public void createMap(String mapName) {
        lock.writeLock().lock();
        try {
            // Get existing map names from meta
            Set<String> mapNames = (Set<String>) localDB.getMetaMap().getOrDefault(LocalDB.MAP_NAMES_META_KEY, new HashSet<String>());
            
            // Create the new map if it doesn't exist
            if (!mapNames.contains(mapName)) {
                // Add new map name to the set and update meta
                mapNames.add(mapName);
                localDB.putState(LocalDB.MAP_NAMES_META_KEY, mapNames);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<T> searchInValue(String part) {
        return localDB.searchString(part);
    }




    public void chooseToShowNiceJsonList(List<T> itemList, BufferedReader br){
        if(itemList.isEmpty()){
            return;
        }
        List<T> chosenItems = Shower.showOrChooseListInPages("Choose to show them",itemList, DEFAULT_PAGE_SIZE, null, true, itemClass, br);//FcEntity.chooseList(itemName,itemList,defaultFields,defaultWidths,null,br);//showAndChooseItems("Choose to show items...", itemList, 20, br, true, true);
        if(chosenItems==null || chosenItems.isEmpty()){
            return;
        }
        JsonUtils.showListInNiceJson(chosenItems, br);
    }


    public List<T> showOrChooseItemList(String promote, @Nullable List<T> itemList, Integer sizeInPage, @Nullable BufferedReader br, boolean isFromEnd, boolean choose) {
        if(itemList!=null)
            return Shower.showOrChooseListInPages(promote,itemList, DEFAULT_PAGE_SIZE, mainFid, choose, itemClass, br);

        int title = localDB.getSize();
        System.out.println("Total: "+ title + " items.");
        int totalPages = (int) Math.ceil((double) title / DEFAULT_PAGE_SIZE);
        int currentPage =1;
        if (dbEmpty()) {
            if(br!=null)Menu.anyKeyToContinue(br);
            return null;
        }
        Long fromIndex = null;
        List<T> finalChosenList = new ArrayList<>();
        while(true) {
            List<T> batchItemList = localDB.getList(sizeInPage, null, fromIndex, false, null, null, true, isFromEnd);
            if(batchItemList==null || batchItemList.isEmpty())break;
            String batchPromote = String.format("%s (Page %d/%d)", promote, currentPage, totalPages);
            List<T> chosenList = Shower.showOrChooseList(batchPromote,batchItemList , mainFid, choose, itemClass, br);
            if(chosenList!=null && !chosenList.isEmpty())
                finalChosenList.addAll(chosenList);
            if(batchItemList.size()<sizeInPage){
                System.out.println("No more items.");
                if(br!=null)Menu.anyKeyToContinue(br);
                break;
            }
            fromIndex = localDB.getIndexById(batchItemList.get(batchItemList.size()-1).getId());
            if(br!=null && Inputer.askIfYes(br,(totalPages-currentPage)+" pages left. Stop choosing?"))break;
            currentPage++;
        }
        return finalChosenList;
    }

    public void removeDeleted(Map<String, Long> deletedIds, Iterator<? extends FcEntity> iterator) {
        Set<String> checkedRemovedIds = null;
        if(deletedIds !=null && !deletedIds.isEmpty())
            checkedRemovedIds = deletedIds.keySet();

        while (iterator.hasNext()){
            if(checkedRemovedIds != null && checkedRemovedIds.contains(iterator.next().getId()))
                iterator.remove();
            else iterator.next();
        }
    }

    protected void opItems(List<T> items, String ask, BufferedReader br){
        System.out.println("To override this method, implement it in the subclass.");
    }

    protected List<T> searchItems(BufferedReader br, boolean withChoose,boolean withOperation){
        if (dbEmpty()) return null;
        String searchStr = Inputer.inputString(br, "Input the search string:");
        List<T> foundItems = searchInValue(searchStr);
        System.out.println();
        List<T> chosenItems;
        if(foundItems.size()>0){
            chosenItems = Shower.showOrChooseList(itemName,foundItems, null, true, itemClass, br);
            opItems(chosenItems,"What to do with them?",br);
        }
        return null;
    }

    protected boolean dbEmpty() {
        if(localDB.getSize()==0){
            System.out.println("No any "+itemName+" yet.");
            return true;
        }
        return false;
    }

    public void addBasicMenuItems(BufferedReader br, Menu menu) {
        menu.add("List Local "+ StringUtils.capitalize(itemName), () -> listItems(br));
        menu.add("Search Local "+ StringUtils.capitalize(itemName), () -> searchItems(br, true,true));
        menu.add("Add Local "+ StringUtils.capitalize(itemName), addItemsToLocalDB(br, itemName));
        menu.add("Clear Local Database", () -> clearTheDatabase(br));
    }

    protected void clearTheDatabase(BufferedReader br) {
        if(Inputer.askIfYes(br, "Are you sure you want to clear the entire database? This will remove ALL data including metadata.")){
            clearDB();
            System.out.println("Database cleared completely.");
        }
    }

    @NotNull
    protected Runnable addItemsToLocalDB(BufferedReader br, String itemName) {
        return () -> {
            try {
                Map<String, T> items = new HashMap<>();

                while(true) {
                    T item = Inputer.createFromInput(br,itemClass);
                    if(item==null)break;
                    String id = null;
                    try {
                        id = (String) item.getClass().getDeclaredField(ID).get(item);
                    }catch (Exception ignore){}
                    if(id==null || id.equals(""))id = Hash.sha256x2(item.toJson());
                    System.out.println(item.toNiceJson());
                    if(!Inputer.askIfYes(br, "Revise it?")){
                        items.put(id, item);
                    }
                    if(Inputer.askIfYes(br, "Finished?"))break;
                }
                localDB.putAll(items);
                System.out.println("\n"+items.size() +" "+ itemName+ "s added.");
            } catch (IOException | ReflectiveOperationException e) {
                System.out.println("Error: " + e.getMessage());
            }
        };
    }

    protected void listItems(BufferedReader br){
        List<T> chosenItems = chooseItemList(br);
        if(chosenItems!=null && chosenItems.size()>0)
            opItems(chosenItems,"What you want to do with them?",br);
    }


    protected List<T> chooseItemList(BufferedReader br) {
        return showOrChooseItemList(itemName,null, DEFAULT_PAGE_SIZE, br,true,true);
    }


    public void remove(String id) {
        if (localDB == null) return;
        lock.writeLock().lock();
        try {
            localDB.remove(id);
            markAsLocallyRemoved(Collections.singletonList(id));
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
        Integer size = DEFAULT_PAGE_SIZE; //Inputer.inputInteger(br, "Enter the size of a page: ", 1, 0);

        while (true) {
            List<T> currentList = localDB.getList(size, lastKey, null, false, null, null, true, true);


            if (currentList.isEmpty()) {
                break;
            }

            lastKey = localDB.getTempId();//currentList.get(currentList.size()-1);

            List<T> result = Shower.showOrChooseListInPages(null, currentList, DEFAULT_PAGE_SIZE, null, true, itemClass, br);

            if (result == null)
                continue;
            result.remove(null);  // Remove the break signal
            chosenItems.addAll(result);
        }

        return chosenItems;
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
        markIdListAs(ids, LOCAL_REMOVED_MAP);
    }

    public void markAsOnChainDeleted(List<String> ids) {
        markIdListAs(ids, ON_CHAIN_DELETED_MAP);
    }

    public void markIdListAs(List<String> ids, String mapName) {
        if (localDB == null) return;
        lock.writeLock().lock();
        try {
            List<Long> times = new ArrayList<>();
            long currentTime = System.currentTimeMillis();
            for(String ignored : ids){
                times.add(currentTime);
            }
            localDB.putAllInMap(mapName, ids, times);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Long getLocalRemovalTime(String id) {
        if (localDB == null) return null;
        lock.readLock().lock();
        try {
            return localDB.getFromMap(LOCAL_REMOVED_MAP, id);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Long getOnChainDeletionTime(String id) {
        if (localDB == null) return null;
        lock.readLock().lock();
        try {
            return localDB.getFromMap(ON_CHAIN_DELETED_MAP, id);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<String, Long> getAllLocallyRemoved() {
        if (localDB == null) return new HashMap<>();
        lock.readLock().lock();
        try {
            return localDB.getAllFromMap(LOCAL_REMOVED_MAP);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<String, Long> getAllOnChainDeletedRecords() {
        if (localDB == null) return new HashMap<>();
        lock.readLock().lock();
        try {
            return localDB.getAllFromMap(ON_CHAIN_DELETED_MAP);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clearLocallyRemoved(String id) {
        if (localDB == null) return;
        lock.writeLock().lock();
        try {
            localDB.removeFromMap(LOCAL_REMOVED_MAP, id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clearOnChainDeleted(String id) {
        if (localDB == null) return;
        lock.writeLock().lock();
        try {
            localDB.removeFromMap(ON_CHAIN_DELETED_MAP, id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clearAllLocallyRemoved() {
        if (localDB == null) return;
        lock.writeLock().lock();
        try {
            localDB.clearMap(LOCAL_REMOVED_MAP);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clearAllOnChainDeleted() {
        if (localDB == null) return;
        lock.writeLock().lock();
        try {
            localDB.clearMap(ON_CHAIN_DELETED_MAP);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getMainFid() {
        return mainFid;
    }

    public String getSid() {
        return sid;
    }
    public void close() {
        if (localDB != null) {
            try {
                log.debug("Closing LevelDB for handler: {}", handlerType);
                localDB.close();
                localDB = null;
            } catch (Exception e) {
                log.error("Error closing LevelDB for handler {}: {}", handlerType, e.getMessage(), e);
                throw new RuntimeException("Failed to close LevelDB for handler " + handlerType, e);
            }
        }
    }

    public void closeMenu() {
    }

    protected void initializeDB(String fid, String sid, String dbPath, String dbName, 
                               LocalDB.SortType sortType,
                                Class<T> entityClass) {
        // Replace direct instantiation with factory method call
        this.localDB = createLocalDB(sortType, entityClass, dbType);
        
        int maxRetries = 5;
        int retryCount = 0;
        long retryDelay = 1000; // Start with 1 second delay
        
        while(retryCount < maxRetries) {
            try {
                this.localDB.initialize(fid, sid, dbPath, dbName);
                break;
            } catch (Exception e) {
                retryCount++;
                log.warn("Failed to initialize database (attempt {}/{}): {}", retryCount, maxRetries, e.getMessage());
                
                if (retryCount == maxRetries) {
                    log.error("Failed to initialize database after {} attempts. Please ensure no other instance is running.", maxRetries);
                    System.exit(1);
                }
                
                try {
                    Thread.sleep(retryDelay);
                    retryDelay *= 2; // Exponential backoff
                } catch (InterruptedException e1) {
                    log.error("Failed to sleep during retry", e1);
                    System.exit(1);
                }
            }
        }
    }

    /**
     * Factory method to create the appropriate LocalDB implementation.
     * Override this method in subclasses to use different DB implementations.
     *
     * @param sortType        The sort type for the database
     * @param entityClass     The class of the entity
     * @param dbType
     * @return An instance of LocalDB
     */
    protected LocalDB<T> createLocalDB(LocalDB.SortType sortType,
                                       Class<T> entityClass, LocalDB.DbType dbType) {
        return switch (dbType) {
            case EASY_DB -> new EasyDB<>(sortType, entityClass);
            default -> new LevelDB<>(sortType, entityClass);
        };
    }

    public LocalDB.DbType getDbType() {
        return dbType;
    }

    public Boolean getOpOnChain() {
        return opOnChain;
    }

    /**
     * Get the field width map for displaying items
     * @return Map of field names to their display widths
     */
    protected LinkedHashMap<String, Integer> getFieldWidthMap(){
        return new LinkedHashMap<>();
    };

    /**
     * Get list of fields that contain timestamp values
     * @return List of timestamp field names
     */
    protected List<String> getTimestampFieldList() {
        return new ArrayList<>();
    }

    /**
     * Get list of fields that contain satoshi values
     * @return List of satoshi field names
     */
    protected List<String> getSatoshiFieldList() {
        return new ArrayList<>();
    }

    /**
     * Get map of height fields to their corresponding time fields
     * @return Map of height field names to time field names
     */
    protected Map<String, String> getHeightToTimeFieldMap() {
        return new HashMap<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Class<T> getItemClass() {
        return itemClass;
    }

    public String getItemName() {
        return itemName;
    }
}
