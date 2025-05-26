package db;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import data.fcData.FcEntity;
import data.feipData.Secret;
import org.iq80.leveldb.*;
import org.iq80.leveldb.impl.Iq80DBFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.BytesUtils;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Base64;

/**
 * A LevelDB implementation of the LocalDB interface.
 * This implementation uses LevelDB for persistent storage and provides:
 * 
 * 1. A main map with String keys and FcEntity values 
 * 2. Three system maps (meta, settings, state) for storing configuration and internal state
 * 3. User-defined maps with customizable value types
 * 4. Various sort order implementations (key, access, update, and birth order)
 * 5. Thread-safe operations via read-write locks
 * 6. In-memory caching for improved performance
 *
 * @param <T> The type of objects to store, must extend FcEntity
 */
public class LevelDB<T extends FcEntity> implements LocalDB<T> {
    public static final String ITEM_PREFIX = "item:";
    public static final String META_PREFIX = "meta:";
    public static final String SETTING_PREFIX = "setting:";
    public static final String STATE_PREFIX = "state:";
    public static final String MAP_PREFIX = "map:";
    public static final String LIST_COUNT_PREFIX = "list_count:";
    public static final String LIST_ITEM_PREFIX = "list_item:";
    private static final Logger log = LoggerFactory.getLogger(LevelDB.class);

    private final SortType sortType;
    private final Class<T> entityClass;
    private volatile String dbPath;
    private volatile String dbName;
    private DB db;
    private final Gson gson;
    private volatile boolean isClosed = false;

    // Add read-write lock
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    
    // Change temporary variables to thread-local
    private final ThreadLocal<Long> tempIndex = new ThreadLocal<>();
    private final ThreadLocal<String> tempId = new ThreadLocal<>();
    
    // Index maps (kept in memory for performance)
    private final ConcurrentNavigableMap<Long, String> indexIdMap = new ConcurrentSkipListMap<>();
    private final ConcurrentNavigableMap<String, Long> idIndexMap = new ConcurrentSkipListMap<>();
    private final Map<String, Object> metaCache = new ConcurrentHashMap<>();
    private final Map<String, Object> settingsCache = new ConcurrentHashMap<>();
    private final Map<String, Object> stateCache = new ConcurrentHashMap<>();
    private final Set<String> mapNames = new HashSet<>();
    private Map<String, String> mapTypes;
    private String folderName;
    private final Set<String> listNames = new HashSet<>();
    /**
     * Creates a new LevelDB instance with the specified serializer, sort type, and entity class.
     *
     * @param sortType    The sort type to use for the main map (determines order of iteration)
     * @param entityClass The class of entities being stored
     */
    public LevelDB(SortType sortType, Class<T> entityClass) {
        this.sortType = Objects.requireNonNullElse(sortType, SortType.NO_SORT);
        this.entityClass = entityClass;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Initializes the database with the given parameters.
     * Creates the database folder if it doesn't exist and opens the LevelDB instance.
     * Loads metadata and indices from the database.
     *
     * @param fid Prefix identifier for the database folder (optional)
     * @param sid Secondary prefix identifier for the database folder (optional)
     * @param dbPath The base path where the database folder will be created
     * @param dbName The name of the database
     * @throws RuntimeException if the database cannot be initialized
     */
    @Override
    public void initialize(String fid, String sid, String dbPath, String dbName) {
        this.dbPath = dbPath;
        this.dbName = dbName;
        
        if (isClosed) {
            this.isClosed = false;
        }
        
        folderName = Path.of(dbPath, makeDbFolderName(fid, sid, dbName)).toString();
        File dbFolder = new File(folderName);
        
        try {
            if (!dbFolder.exists()) {
                if(!dbFolder.mkdirs()){
                    log.error("Failed to create database directory: {}", folderName);
                    throw new RuntimeException("Failed to create database directory: " + folderName);
                }
            }
            
            // Handle stale lock file before opening the database
            handleStaleLock(dbFolder);
            
            Options options = new Options();
            options.createIfMissing(true);
            
            db = Iq80DBFactory.factory.open(dbFolder, options);
            log.info("LevelDB initialized: {}", dbFolder.getAbsolutePath());
            
            // Load metadata and indices
            loadMetaData();
            if (sortType != SortType.NO_SORT) {
                loadIndices();
            }
            
        } catch (IOException e) {
            log.error("Failed to initialize LevelDB: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize LevelDB", e);
        } catch (Exception e) {
            log.error("Unexpected error initializing LevelDB: {}", e.getMessage(), e);
            throw new RuntimeException("Unexpected error initializing LevelDB", e);
        }
    }
    
    private String makeDbFolderName(String fid, String sid, String dbName) {
        return (fid != null ? fid + "_" : "") + 
               (sid != null ? sid + "_" : "") + 
               dbName.toLowerCase() + "_leveldb";
    }
    
    private void loadMetaData() {
        try {
            // Clear existing cache
            metaCache.clear();
            settingsCache.clear();
            stateCache.clear();
            mapNames.clear();
            if (mapTypes != null) mapTypes.clear();
            else mapTypes = new HashMap<>();
            
            // Scan all meta entries
            try (DBIterator iterator = db.iterator()) {
                // Load META entries
                byte[] metaPrefix = META_PREFIX.getBytes(StandardCharsets.UTF_8);
                for (iterator.seek(metaPrefix);
                     iterator.hasNext();
                     iterator.next()) {

                    byte[] keyBytes = iterator.peekNext().getKey();
                    String keyStr = bytesToString(keyBytes);

                    if (!keyStr.startsWith(META_PREFIX)) {
                        break; // Exit when we're past META_PREFIX keys
                    }

                    String key = keyStr.substring(META_PREFIX.length());
                    byte[] valueBytes = iterator.peekNext().getValue();
                    Object value = fromBytes(valueBytes);

                    metaCache.put(key, value);
                    
                    // Check for map types
                    if (MAP_TYPES_META_KEY.equals(key) && value instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> types = (Map<String, String>) value;
                        mapTypes.putAll(types);
                    }
                    
                    // Check for map names
                    if (MAP_NAMES_META_KEY.equals(key) && value instanceof Set) {
                        @SuppressWarnings("unchecked")
                        Set<String> names = (Set<String>) value;
                        mapNames.addAll(names);
                    }
                }
                
                // Load SETTINGS entries
                byte[] settingsPrefix = SETTING_PREFIX.getBytes(StandardCharsets.UTF_8);
                for (iterator.seek(settingsPrefix);
                     iterator.hasNext();
                     iterator.next()) {
                    
                    byte[] keyBytes = iterator.peekNext().getKey();
                    String keyStr = bytesToString(keyBytes);
                    
                    if (!keyStr.startsWith(SETTING_PREFIX)) {
                        break; // Exit when we're past SETTINGS_PREFIX keys
                    }
                    
                    String key = keyStr.substring(SETTING_PREFIX.length());
                    byte[] valueBytes = iterator.peekNext().getValue();
                    Object value = fromBytes(valueBytes);
                    
                    settingsCache.put(key, value);
                }
                
                // Load STATE entries
                byte[] statePrefix = STATE_PREFIX.getBytes(StandardCharsets.UTF_8);
                for (iterator.seek(statePrefix);
                     iterator.hasNext();
                     iterator.next()) {
                    
                    byte[] keyBytes = iterator.peekNext().getKey();
                    String keyStr = bytesToString(keyBytes);
                    
                    if (!keyStr.startsWith(STATE_PREFIX)) {
                        break; // Exit when we're past STATE_PREFIX keys
                    }
                    
                    String key = keyStr.substring(STATE_PREFIX.length());
                    byte[] valueBytes = iterator.peekNext().getValue();
                    Object value = fromBytes(valueBytes);
                    
                    stateCache.put(key, value);
                }
            }
            
            // Check sort type
            Object sortTypeValue = metaCache.get(SORT_TYPE_META_KEY);

            if (sortTypeValue == null) {
                putMeta(SORT_TYPE_META_KEY, this.sortType.name());
            }
        } catch (Exception e) {
            log.error("Failed to load metadata: {}", e.getMessage(), e);
        }
    }

    private void loadIndices() {
        if (sortType == SortType.NO_SORT) {
            return;
        }
        
        // Clear existing indices
        indexIdMap.clear();
        idIndexMap.clear();
        
        try {
            // Need to reconstruct indices from items
            List<String> allKeys = new ArrayList<>();
            
            // Collect all item keys
            try (DBIterator iterator = db.iterator()) {
                byte[] itemPrefix = ITEM_PREFIX.getBytes(StandardCharsets.UTF_8);
                for (iterator.seek(itemPrefix);
                     iterator.hasNext();
                     iterator.next()) {
                    
                    byte[] keyBytes = iterator.peekNext().getKey();
                    String fullKey = bytesToString(keyBytes);
                    
                    if (!fullKey.startsWith(ITEM_PREFIX)) {
                        break; // Exit when we're past ITEM_PREFIX keys
                    }
                    
                    String key = fullKey.substring(ITEM_PREFIX.length());
                    allKeys.add(key);
                }
            }
            
            // Sort keys if needed
            if (sortType == SortType.KEY_ORDER) {
                Collections.sort(allKeys);
            }
            
            // Rebuild indices
            long index = 1;
            for (String key : allKeys) {
                indexIdMap.put(index, key);
                idIndexMap.put(key, index);
                index++;
            }
            
        } catch (Exception e) {
            log.error("Failed to load indices: {}", e.getMessage(), e);
        }
    }

    @Override
    public SortType getSortType() {
        return sortType;
    }

    /**
     * Stores an entity in the database with the specified key.
     * Updates indices if sorting is enabled.
     *
     * @param key The key to store the entity under
     * @param value The entity to store
     */
    @Override
    public void put(String key, T value) {
        if (key == null) {
            log.warn("Attempted to put null key in database");
            return;
        }
        if (value == null) {
            log.warn("Attempted to put null value for key: {}", key);
            return;
        }
        
        writeLock.lock();
        try {
            // Use the provided valueSerializer to convert entity to bytes
            byte[] valueBytes = value.toBytes();
            
            db.put(getItemKey(key), valueBytes);
            
            // Update indices if needed
            if (sortType != SortType.NO_SORT) {
                updateIndex(key);
            }
        } catch (Exception e) {
            log.error("Error putting item with key {}: {}", key, e.getMessage(), e);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Retrieves an entity from the database by key.
     * Updates access order if using ACCESS_ORDER sort type.
     *
     * @param key The key of the entity to retrieve
     * @return The entity, or null if not found
     */
    @Override
    public T get(String key) {
        if (key == null) return null;
        
        T item = null;
        
        // Read the item with read lock first
        readLock.lock();
        try {
            byte[] valueBytes = db.get(getItemKey(key));
            if (valueBytes == null) {
                return null;
            }
            
            item = deserializeItem(valueBytes);
        } finally {
            readLock.unlock();
        }
        
        // Then update access order with write lock if needed - separately
        if (sortType == SortType.ACCESS_ORDER && item != null) {
            updateAccessOrder(key);
        }
        
        return item;
    }

    @Override
    public List<T> get(List<String> keys) {
        if (keys == null || keys.isEmpty()) return new ArrayList<>();
        
        List<T> result = new ArrayList<>();
        List<String> accessedKeys = new ArrayList<>();
        
        // First get all items with read lock
        readLock.lock();
        try {
            for (String key : keys) {
                byte[] valueBytes = db.get(getItemKey(key));
                if (valueBytes != null) {
                    T item = deserializeItem(valueBytes);
                    if (item != null) {
                        result.add(item);
                        if (sortType == SortType.ACCESS_ORDER) {
                            accessedKeys.add(key);
                        }
                    }
                }
            }
        } finally {
            readLock.unlock();
        }
        
        // Then update access order with write lock if needed - separately
        if (sortType == SortType.ACCESS_ORDER && !accessedKeys.isEmpty()) {
            updateAccessOrderBatch(accessedKeys);
        }
        
        return result;
    }

    @Override
    public void remove(String key) {
        if (key == null) return;
        
        writeLock.lock();
        try {
            // Remove from LevelDB
            db.delete(getItemKey(key));
            
            // Update indices if needed
            if (sortType != SortType.NO_SORT) {
                Long index = idIndexMap.get(key);
                if (index != null) {
                    indexIdMap.remove(index);
                    idIndexMap.remove(key);
                    
                    // Use improved method to shift indices and maintain sequential order
                    shiftHigherIndicesDown1(index);
                }
            }
            
            // Mark as locally removed
            putInMap(LOCAL_REMOVED_MAP, key, System.currentTimeMillis()
            );
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Removes multiple items from the database in a batch operation.
     * This method is more efficient than calling remove() multiple times
     * as it uses a single write batch operation.
     *
     * @param list List of items to remove
     */
    public void remove(List<T> list) {
        if (list == null || list.isEmpty()) return;
        
        writeLock.lock();
        try {
            // Create a batch operation for better performance
            WriteBatch batch = db.createWriteBatch();
            try {
                // Collect indices to update
                List<Long> indicesToUpdate = new ArrayList<>();
                
                for (T item : list) {
                    if (item == null) continue;
                    
                    String key = item.getId();
                    if (key == null) continue;
                    
                    // Add to batch for deletion
                    batch.delete(getItemKey(key));
                    
                    // Collect index for later update
                    if (sortType != SortType.NO_SORT) {
                        Long index = idIndexMap.get(key);
                        if (index != null) {
                            indicesToUpdate.add(index);
                        }
                    }
                    
                    // Mark as locally removed
                    putInMap(LOCAL_REMOVED_MAP, key, System.currentTimeMillis());
                }
                
                // Commit the batch
                db.write(batch);
                
                // Update indices if needed
                if (sortType != SortType.NO_SORT && !indicesToUpdate.isEmpty()) {
                    // Sort indices in descending order to avoid shifting issues
                    Collections.sort(indicesToUpdate, Collections.reverseOrder());
                    
                    // Remove from maps and shift indices
                    for (Long index : indicesToUpdate) {
                        String key = indexIdMap.get(index);
                        if (key != null) {
                            indexIdMap.remove(index);
                            idIndexMap.remove(key);
                        }
                    }
                    
                    // Shift remaining indices
                    for (Long index : indicesToUpdate) {
                        shiftHigherIndicesDown1(index);
                    }
                }
            } finally {
                // Clean up the batch
                try {
                    batch.close();
                } catch (IOException e) {
                    log.error("Error closing batch", e);
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void commit() {
        // LevelDB writes are automatically committed
    }

    /**
     * Closes the database and releases resources.
     * Cleans up thread-local variables to prevent memory leaks.
     */
    @Override
    public void close() {
        if (!isClosed) {
            try {
                log.debug("Closing LevelDB instance: {}", dbName);
                
                // Close the database
                if (db != null) {
                    try {
                        db.close();
                        db = null;
                    } catch (IOException e) {
                        log.error("Error closing LevelDB database: {}", e.getMessage(), e);
                        throw e;
                    }
                }
                
                // Clean up thread-local variables
                cleanupThreadLocals();
                
                // Clear caches
                indexIdMap.clear();
                idIndexMap.clear();
                metaCache.clear();
                settingsCache.clear();
                stateCache.clear();
                mapNames.clear();
                if (mapTypes != null) {
                    mapTypes.clear();
                }
                
                log.debug("LevelDB instance closed successfully: {}", dbName);
            } catch (Exception e) {
                log.error("Error during LevelDB cleanup: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to close LevelDB", e);
            } finally {
                isClosed = true;
            }
        }
    }

    /**
     * Cleans up thread-local variables to prevent memory leaks
     */
    private void cleanupThreadLocals() {
        tempIndex.remove();
        tempId.remove();
    }

    @Override
    public boolean isClosed() {
        return isClosed;
    }

    @Override
    public long getTempIndex() {
        Long value = tempIndex.get();
        return value != null ? value : 0L;
    }

    @Override
    public String getTempId() {
        return tempId.get();
    }

    @Override
    public Map<String, T> getItemMap() {
        Map<String, T> result = new HashMap<>();
        try {
            try (DBIterator iterator = db.iterator()) {
                byte[] itemPrefix = ITEM_PREFIX.getBytes(StandardCharsets.UTF_8);
                for (iterator.seek(itemPrefix);
                     iterator.hasNext();
                     iterator.next()) {
                    
                    byte[] keyBytes = iterator.peekNext().getKey();
                    String fullKey = bytesToString(keyBytes);
                    
                    if (!fullKey.startsWith(ITEM_PREFIX)) {
                        break; // Exit when we're past ITEM_PREFIX keys
                    }
                    
                    String key = fullKey.substring(ITEM_PREFIX.length());
                    byte[] valueBytes = iterator.peekNext().getValue();
                    
                    T item = deserializeItem(valueBytes);
                    if (item != null) {
                        result.put(key, item);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error getting item map: {}", e.getMessage(), e);
        }
        return result;
    }

    @Override
    public Map<String, Object> getMetaMap() {
        return new HashMap<>(metaCache);
    }

    @Override
    public Map<String, Object> getSettingsMap() {
        return new HashMap<>(settingsCache);
    }

    @Override
    public Map<String, Object> getStateMap() {
        return new HashMap<>(stateCache);
    }

    @Override
    public NavigableMap<Long, String> getIndexIdMap() {
        return new TreeMap<>(indexIdMap);
    }

    @Override
    public NavigableMap<String, Long> getIdIndexMap() {
        return new TreeMap<>(idIndexMap);
    }

    @Override
    public Long getIndexById(String id) {
        return idIndexMap.get(id);
    }

    @Override
    public String getIdByIndex(long index) {
        return indexIdMap.get(index);
    }
    public T getByIndex(long index) {
        String id = getIndexIdMap().get(index);
        return id != null ? get(id) : null;
    }
    @Override
    public int getSize() {
        int count = 0;
        try {
            try (DBIterator iterator = db.iterator()) {
                byte[] itemPrefix = ITEM_PREFIX.getBytes(StandardCharsets.UTF_8);
                for (iterator.seek(itemPrefix);
                     iterator.hasNext();
                     iterator.next()) {
                    
                    byte[] keyBytes = iterator.peekNext().getKey();
                    String fullKey = bytesToString(keyBytes);
                    
                    if (!fullKey.startsWith(ITEM_PREFIX)) {
                        break; // Exit when we're past ITEM_PREFIX keys
                    }
                    
                    count++;
                }
            }
        } catch (Exception e) {
            log.error("Error getting size: {}", e.getMessage(), e);
        }
        return count;
    }

    @Override
    public Object getMeta(String key) {
        readLock.lock();
        try {
            // Check cache first
            if (metaCache.containsKey(key)) {
                return metaCache.get(key);
            }
            
            // Get from DB
            byte[] valueBytes = db.get(getMetaKey(key));
            if (valueBytes == null) {
                return null;
            }
            
            Object value = fromBytes(valueBytes);
            metaCache.put(key, value);
            
            return value;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Object getSetting(String key) {
        readLock.lock();
        try {
            // Check cache first
            if (settingsCache.containsKey(key)) {
                return settingsCache.get(key);
            }
            
            // Get from DB
            byte[] valueBytes = db.get(getSettingsKey(key));
            if (valueBytes == null) {
                return null;
            }
            
            Object value = fromBytes(valueBytes);
            settingsCache.put(key, value);
            
            return value;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Object getState(String key) {
        readLock.lock();
        try {
            // Check cache first
            if (stateCache.containsKey(key)) {
                return stateCache.get(key);
            }
            
            // Get from DB
            byte[] valueBytes = db.get(getStateKey(key));
            if (valueBytes == null) {
                return null;
            }
            
            Object value = fromBytes(valueBytes);
            stateCache.put(key, value);
            
            return value;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public LinkedHashMap<String, T> getMap(Integer size, String fromId, Long fromIndex,
                                          boolean isFromInclude, String toId, Long toIndex, 
                                          boolean isToInclude, boolean isFromEnd) {
//        if (indexIdMap.isEmpty()) {
//            return new LinkedHashMap<>();
//        }
//
//        // First determine the IDs we need to retrieve without locking
//        List<String> idsToRetrieve = new ArrayList<>();
//
//        try {
//            NavigableMap<Long, String> subMap = new TreeMap<>(indexIdMap);
//
//            // [Existing logic for calculating subMap...]
//            // The existing code to determine the subMap stays the same
//
//            // Collect IDs to retrieve
//            for (Map.Entry<Long, String> entry : subMap.entrySet()) {
//                if (size != null && idsToRetrieve.size() >= size) {
//                    break;
//                }
//
//                String id = entry.getValue();
//                Long index = entry.getKey();
//
//                idsToRetrieve.add(id);
//
//                // Store the appropriate index for next pagination
//                tempIndex.set(index);
//                tempId.set(id);
//            }
//        } catch (Exception e) {
//            log.error("Error determining IDs to retrieve: {}", e.getMessage(), e);
//            return new LinkedHashMap<>();
//        }
//
//        // Now retrieve the items
//        LinkedHashMap<String, T> result = new LinkedHashMap<>();
//        List<String> loadedIds = new ArrayList<>();
//
//        // Get all items with read lock
//        readLock.lock();
//        try {
//            for (String id : idsToRetrieve) {
//                byte[] valueBytes = db.get(getItemKey(id));
//                if (valueBytes != null) {
//                    T item = deserializeItem(valueBytes);
//                    if (item != null) {
//                        result.put(id, item);
//                        loadedIds.add(id);
//                    }
//                }
//            }
//        } finally {
//            readLock.unlock();
//        }
//
//        // Update access order if needed with write lock - separately
//        if (sortType == SortType.ACCESS_ORDER && !loadedIds.isEmpty()) {
//            updateAccessOrderBatch(loadedIds);
//        }
//
//        return result;
        NavigableMap<Long, String> subMap = indexIdMap;
        long startIndex;
        long endIndex;

        if(isFromEnd){
            // Handle start boundary
            if(fromIndex!=null){
                if(fromIndex ==1)return new LinkedHashMap<>();
                subMap = subMap.headMap(fromIndex, false);
            }else if(fromId!=null){
                endIndex = idIndexMap.get(fromId);
                subMap = subMap.headMap(endIndex,isFromInclude);
            }

            //Handle end boundary

            if (toIndex != null) {
                subMap = subMap.tailMap(toIndex, isToInclude);
            }else if(toId!=null){
                startIndex = idIndexMap.get(toId);
                subMap = subMap.tailMap(startIndex, isToInclude);
            }
        }else {
            if(fromIndex!=null){
                subMap = subMap.tailMap(fromIndex, isFromInclude);
            }else if(fromId!=null){
                startIndex = idIndexMap.get(fromId);
                subMap = subMap.tailMap(startIndex, isFromInclude);
            }

            if(toIndex!=null){
                subMap = subMap.headMap(toIndex,isToInclude);
            }else if(toId!=null){
                endIndex = idIndexMap.get(toId);
                subMap = subMap.headMap(endIndex, isToInclude);
            }
        }

        // Reverse if needed
        if (isFromEnd) {
            subMap = subMap.descendingMap();
        }

        // Build result map
        LinkedHashMap<String, T> result = new LinkedHashMap<>();
        String lastId = null;
        Long lastIndex = null;

        for (Map.Entry<Long, String> entry : subMap.entrySet()) {
            if (size != null && result.size() >= size) break;

            lastIndex = entry.getKey();
            lastId = entry.getValue();

            T item = get(lastId);//itemMap.get(lastId);
            if (item != null) {
                result.put(lastId, item);
                updateAccessOrder(lastId);
            }
        }

        // Store the appropriate index for next pagination
        if (lastIndex != null) tempIndex.set(lastIndex);


        // Store the last processed ID
        tempId.set(lastId);

        // Commit changes if we updated any indices
        if (sortType == SortType.ACCESS_ORDER && !result.isEmpty()) {
            commit();
        }

        return result;
    }

    @Override
    public List<T> getList(Integer size, String fromId, Long fromIndex,
                          boolean isFromInclude, String toId, Long toIndex, 
                          boolean isToInclude, boolean isFromEnd) {
        return new ArrayList<>(getMap(size, fromId, fromIndex, isFromInclude, toId, toIndex, isToInclude, isFromEnd).values());
    }

    @Override
    public void putAll(Map<String, T> items) {
        if (items == null || items.isEmpty()) return;
        
        writeLock.lock();
        try {
            // Use batch write for efficiency
            WriteBatch batch = db.createWriteBatch();
            try {
                for (Map.Entry<String, T> entry : items.entrySet()) {
                    String key = entry.getKey();
                    T value = entry.getValue();
                    
                    if (key != null && value != null) {
                        // Use toBytes like in the put method
                        byte[] valueBytes = value.toBytes();
                        batch.put(getItemKey(key), valueBytes);
                    }
                }
                
                // Commit the batch
                db.write(batch);
                
                // Update indices if needed
                if (sortType != SortType.NO_SORT) {
                    for (String key : items.keySet()) {
                        updateIndex(key);
                    }
                }
            } finally {
                try {
                    batch.close();
                } catch (IOException e) {
                    log.error("Error closing batch: {}", e.getMessage(), e);
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Map<String, T> getAll() {
        readLock.lock();
        try {
            return getItemMap();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public List<T> searchString(String part) {

        if (part == null || part.isEmpty()) {
            return new ArrayList<>();
        }
        
        // First collect keys that match the search criteria
        List<String> matchingKeys = new ArrayList<>();
        
        readLock.lock();
        try {
            try (DBIterator iterator = db.iterator()) {
                byte[] itemPrefix = ITEM_PREFIX.getBytes(StandardCharsets.UTF_8);
                for (iterator.seek(itemPrefix);
                     iterator.hasNext();
                     iterator.next()) {
                    
                    byte[] keyBytes = iterator.peekNext().getKey();
                    String fullKey = bytesToString(keyBytes);
                    
                    if (!fullKey.startsWith(ITEM_PREFIX)) {
                        break; // Exit when we're past ITEM_PREFIX keys
                    }
                    
                    byte[] valueBytes = iterator.peekNext().getValue();
                    
                    if (BytesUtils.contains(valueBytes, part.getBytes())) {
                        String key = fullKey.substring(ITEM_PREFIX.length());
                        matchingKeys.add(key);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error collecting keys in searchString: {}", e.getMessage(), e);
            return new ArrayList<>();
        } finally {
            readLock.unlock();
        }
        
        // Now load the actual items using the get method
        List<T> matches = new ArrayList<>();
        List<String> accessedKeys = new ArrayList<>();
        
        // We could call get() here, but let's avoid extra locking for efficiency
        readLock.lock();
        try {
            for (String key : matchingKeys) {
                byte[] valueBytes = db.get(getItemKey(key));
                if (valueBytes != null) {
                    T item = deserializeItem(valueBytes);
                    if (item != null) {
                        matches.add(item);
                        if (sortType == SortType.ACCESS_ORDER) {
                            accessedKeys.add(key);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error loading items in searchString: {}", e.getMessage(), e);
        } finally {
            readLock.unlock();
        }
        
        // Update access order if needed
        if (sortType == SortType.ACCESS_ORDER && !accessedKeys.isEmpty()) {
            updateAccessOrderBatch(accessedKeys);
        }
        
        return matches;
    }

    @Override
    public void putMeta(String key, Object value) {
        writeLock.lock();
        try {
            // Update cache
            metaCache.put(key, value);
            
            // Update DB
            byte[] valueBytes = toBytes(value);
            db.put(getMetaKey(key), valueBytes);
        } catch (Exception e) {
            log.error("Error putting metadata: {}", e.getMessage(), e);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void putSetting(String key, Object value) {
        writeLock.lock();
        try {
            // Update cache
            settingsCache.put(key, value);
            
            // Update DB
            byte[] valueBytes = toBytes(value);
            db.put(getSettingsKey(key), valueBytes);
        } catch (Exception e) {
            log.error("Error putting settings: {}", e.getMessage(), e);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void putState(String key, Object value) {
        if(value==null||key==null)return;
        writeLock.lock();
        try {
            // Update cache
            stateCache.put(key, value);
            
            // Update DB
            byte[] valueBytes = toBytes(value);
            db.put(getStateKey(key), valueBytes);
        } catch (Exception e) {
            log.error("Error putting state: {}", e.getMessage(), e);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void removeMeta(String key) {
        writeLock.lock();
        try {
            // Remove from cache
            metaCache.remove(key);
            
            // Remove from DB
            db.delete(getMetaKey(key));
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void removeSetting(String key) {
        writeLock.lock();
        try {
            // Remove from cache
            settingsCache.remove(key);
            
            // Remove from DB
            db.delete(getSettingsKey(key));
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void removeState(String key) {
        writeLock.lock();
        try {
            // Remove from cache
            stateCache.remove(key);
            
            // Remove from DB
            db.delete(getStateKey(key));
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void clear() {
        writeLock.lock();
        try {
            // Delete all items but keep metadata and maps
            WriteBatch batch = db.createWriteBatch();
            try {
                try (DBIterator iterator = db.iterator()) {
                    byte[] itemPrefix = ITEM_PREFIX.getBytes(StandardCharsets.UTF_8);
                    for (iterator.seek(itemPrefix);
                         iterator.hasNext();
                         iterator.next()) {
                        
                        byte[] keyBytes = iterator.peekNext().getKey();
                        String fullKey = bytesToString(keyBytes);
                        
                        if (!fullKey.startsWith(ITEM_PREFIX)) {
                            break; // Exit when we're past ITEM_PREFIX keys
                        }
                        
                        batch.delete(keyBytes);
                    }
                }
                
                // Commit the batch
                db.write(batch);
                
                // Clear indices
                if (sortType != SortType.NO_SORT) {
                    indexIdMap.clear();
                    idIndexMap.clear();
                }
            } finally {
                try {
                    batch.close();
                } catch (IOException e) {
                    log.error("Error closing batch: {}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error clearing database: {}", e.getMessage(), e);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void removeList(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        
        writeLock.lock();
        try {
            // Use batch delete for efficiency
            WriteBatch batch = db.createWriteBatch();
            try {
                for (String id : ids) {
                    batch.delete(getItemKey(id));
                    
                    // Update indices if needed
                    if (sortType != SortType.NO_SORT) {
                        Long index = idIndexMap.get(id);
                        if (index != null) {
                            indexIdMap.remove(index);
                            idIndexMap.remove(id);
                            
                            // We'll handle shifting indices after the batch commit
                        }
                    }
                    
                    // Mark as locally removed
                    putInMap(LOCAL_REMOVED_MAP, id, System.currentTimeMillis()
                    );
                }
                
                // Commit the batch
                db.write(batch);
                
                // Handle shifting indices if needed
                if (sortType != SortType.NO_SORT) {
                    reIndex(null, null);
                }
            } finally {
                try {
                    batch.close();
                } catch (IOException e) {
                    log.error("Error closing batch: {}", e.getMessage(), e);
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void clearDB() {
        writeLock.lock();
        try {
            // Close the existing DB
            if (db != null) {
                try {
                    db.close();
                } catch (IOException e) {
                    log.error("Error closing database: {}", e.getMessage(), e);
                }
            }
            
            //TODO Delete the database folder
            File dbFolder = new File(folderName);
            if (dbFolder.exists()) {
                deleteFolder(dbFolder);
            }
            
            // Recreate the database
            Options options = new Options();
            options.createIfMissing(true);
            
            try {
                db = Iq80DBFactory.factory.open(dbFolder, options);
            } catch (IOException e) {
                log.error("Error reopening database: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to reopen database", e);
            }
            
            // Clear in-memory data
            indexIdMap.clear();
            idIndexMap.clear();
            metaCache.clear();
            settingsCache.clear();
            stateCache.clear();
            mapNames.clear();
            
            // Reinitialize standard maps
            createMap(LOCAL_REMOVED_MAP, Long.class);
            createMap(ON_CHAIN_DELETED_MAP, Long.class);
        } finally {
            writeLock.unlock();
        }
    }

    private void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteFolder(file);
                } else {
                    file.delete();
                }
            }
        }
        folder.delete();
    }

    @Override
    public void putAll(List<T> items, String idField) {
        if (items == null || items.isEmpty() || idField == null || idField.isEmpty()) {
            return;
        }
        
        // Build a map from the list
        Map<String, T> itemMap = new HashMap<>();
        for (T item : items) {
            try {
                // Extract ID using reflection
                java.lang.reflect.Method method = item.getClass().getMethod("get" + capitalize(idField));
                String id = (String) method.invoke(item);
                if (id != null) {
                    itemMap.put(id, item);
                }
            } catch (Exception e) {
                log.error("Error extracting ID from field {}: {}", idField, e.getMessage(), e);
            }
        }
        
        // Put the map
        putAll(itemMap);
    }

    @Override
    public Set<String> getMapNames() {
        return new HashSet<>(mapNames);
    }

    @Override
    public void clearMap(String mapName) {
        if (mapName == null || !mapNames.contains(mapName)) {
            return;
        }
        
        writeLock.lock();
        try {
            WriteBatch batch = db.createWriteBatch();
            try {
                String mapKeyPrefix = getMapKeyPrefix(mapName);
                byte[] mapPrefixBytes = mapKeyPrefix.getBytes(StandardCharsets.UTF_8);
                
                try (DBIterator iterator = db.iterator()) {
                    for (iterator.seek(mapPrefixBytes);
                         iterator.hasNext();
                         iterator.next()) {
                        
                        byte[] keyBytes = iterator.peekNext().getKey();
                        String fullKey = bytesToString(keyBytes);
                        
                        if (!fullKey.startsWith(mapKeyPrefix)) {
                            break; // Exit when we're past map prefix keys
                        }
                        
                        batch.delete(keyBytes);
                    }
                }
                
                // Commit the batch
                db.write(batch);
            } finally {
                try {
                    batch.close();
                } catch (IOException e) {
                    log.error("Error closing batch: {}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error clearing map {}: {}", mapName, e.getMessage(), e);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public int getMapSize(String mapName) {
        if (mapName == null || !mapNames.contains(mapName)) {
            return 0;
        }
        
        readLock.lock();
        try {
            int count = 0;
            String mapKeyPrefix = getMapKeyPrefix(mapName);
            byte[] mapPrefixBytes = mapKeyPrefix.getBytes(StandardCharsets.UTF_8);
            
            try (DBIterator iterator = db.iterator()) {
                for (iterator.seek(mapPrefixBytes);
                     iterator.hasNext();
                     iterator.next()) {
                    
                    byte[] keyBytes = iterator.peekNext().getKey();
                    String fullKey = bytesToString(keyBytes);
                    
                    if (!fullKey.startsWith(mapKeyPrefix)) {
                        break; // Exit when we're past map prefix keys
                    }
                    
                    count++;
                }
            }
            
            return count;
        } catch (Exception e) {
            log.error("Error getting map size for {}: {}", mapName, e.getMessage(), e);
            return 0;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Creates a map with the specified name and value type.
     * Registers the map name and type in the metadata.
     *
     * @param mapName The name of the map to create
     * @param vClass The class of values to be stored in the map
     * @param <V> The type of values in the map
     */
    @Override
    public <V> void createMap(String mapName, Class<V> vClass) {
        if(mapTypes==null)mapTypes = new HashMap<>();
        if (!mapNames.contains(mapName)) {
            mapNames.add(mapName);
            putMeta(MAP_NAMES_META_KEY, mapNames);
            
            // Register the serializer's type for this map
            if (vClass != null) {
                String type = vClass.getName();
                mapTypes.put(mapName, type);
                putMeta(MAP_TYPES_META_KEY, mapTypes);
            }
        }
    }

    /**
     * Puts a value into a named map with the specified key.
     * Creates the map if it doesn't exist.
     *
     * @param <V>     The type of the value
     * @param mapName The name of the map
     * @param key     The key to store the value under
     * @param value   The value to store
     */
    @Override
    public <V> void putInMap(String mapName, String key, V value) {
        if (mapName == null || key == null) {
            return;
        }
        
        // Register the type if not already registered

        if (!mapTypes.containsKey(mapName)) {
            registerMapType(mapName, value.getClass());
        }
        
        // Ensure the map exists
        createMap(mapName, value.getClass());
        
        writeLock.lock();
        try {
            byte[] valueBytes = toBytes(value);
            db.put(getMapKey(mapName, key), valueBytes);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Retrieves a value from a named map by key.
     *
     * @param <V>     The type of the value
     * @param mapName The name of the map
     * @param key     The key to retrieve
     * @return The value, or null if not found
     */
    @Override
    public <V> V getFromMap(String mapName, String key) {
        if (mapName == null || key == null || !mapNames.contains(mapName)) {
            return null;
        }
        
        // Get the registered type
        Class<?> mapType = getMapType(mapName);
        
        readLock.lock();
        try {
            byte[] valueBytes = db.get(getMapKey(mapName, key));
            if (valueBytes == null) {
                return null;
            }
            
            @SuppressWarnings("unchecked")
            V value = (V) fromBytes(valueBytes);
            return value;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public <V> Map<String, V> getAllFromMap(String mapName) {
        if (mapName == null || !mapNames.contains(mapName)) {
            return new HashMap<>();
        }
        
        readLock.lock();
        try {
            Map<String, V> result = new HashMap<>();
            String mapKeyPrefix = getMapKeyPrefix(mapName);
            byte[] mapPrefixBytes = mapKeyPrefix.getBytes(StandardCharsets.UTF_8);
            
            try (DBIterator iterator = db.iterator()) {
                for (iterator.seek(mapPrefixBytes);
                     iterator.hasNext();
                     iterator.next()) {
                    
                    byte[] keyBytes = iterator.peekNext().getKey();
                    String fullKey = bytesToString(keyBytes);
                    
                    if (!fullKey.startsWith(mapKeyPrefix)) {
                        break; // Exit when we're past map prefix keys
                    }
                    
                    String key = fullKey.substring(mapKeyPrefix.length());
                    byte[] valueBytes = iterator.peekNext().getValue();
                    
                    @SuppressWarnings("unchecked")
                    V value = (V) fromBytes(valueBytes);
                    result.put(key, value);
                }
            }
            
            return result;
        } catch (Exception e) {
            log.error("Error getting all from map: {}", e.getMessage(), e);
            return new HashMap<>();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public <V> List<V> getFromMap(String mapName, List<String> keyList) {
        if (mapName == null || keyList == null || keyList.isEmpty() || !mapNames.contains(mapName)) {
            return new ArrayList<>();
        }
        
        readLock.lock();
        try {
            List<V> result = new ArrayList<>();
            
            for (String key : keyList) {
                V value = getFromMap(mapName, key);
                if (value != null) {
                    result.add(value);
                }
            }
            
            return result;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public <V> void putAllInMap(String mapName, List<String> keyList, List<V> valueList) {
        if (mapName == null || keyList == null || valueList == null || keyList.size() != valueList.size()) {
            return;
        }
        
        // Ensure the map exists
        createMap(mapName, valueList.get(0).getClass());
        
        writeLock.lock();
        try {
            // Use batch operation for better performance
            WriteBatch batch = db.createWriteBatch();
            try {
                for (int i = 0; i < keyList.size(); i++) {
                    String key = keyList.get(i);
                    V value = valueList.get(i);
                    
                    if (key != null && value != null) {
                        byte[] valueBytes = toBytes(value);
                        batch.put(getMapKey(mapName, key), valueBytes);
                    }
                }
                
                // Commit the batch
                db.write(batch);
            } finally {
                try {
                    batch.close();
                } catch (IOException e) {
                    log.error("Error closing batch: {}", e.getMessage(), e);
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public <V> void putAllInMap(String mapName, Map<String, V> map) {
        if (mapName == null || map == null || map.isEmpty()) {
            return;
        }

        // Get a sample value to determine the class type
        V sampleValue = map.values().iterator().next();
        if (sampleValue == null) {
            return;
        }

        // Convert map to lists and use existing implementation
        List<String> keyList = new ArrayList<>(map.keySet());
        List<V> valueList = new ArrayList<>(map.values());
        putAllInMap(mapName, keyList, valueList);
    }

    @Override
    public void removeFromMap(String mapName, String key) {
        if (mapName == null || key == null || !mapNames.contains(mapName)) {
            return;
        }
        
        writeLock.lock();
        try {
            db.delete(getMapKey(mapName, key));
        } finally {
            writeLock.unlock();
        }
    }

    

    @Override
    public void removeFromMap(String mapName, List<String> keys) {
        if (mapName == null || keys == null || keys.isEmpty() || !mapNames.contains(mapName)) {
            return;
        }
        
        writeLock.lock();
        try {
            // Use batch operation for better performance
            WriteBatch batch = db.createWriteBatch();
            try {
                for (String key : keys) {
                    if (key != null) {
                        batch.delete(getMapKey(mapName, key));
                    }
                }
                
                // Commit the batch
                db.write(batch);
            } finally {
                try {
                    batch.close();
                } catch (IOException e) {
                    log.error("Error closing batch: {}", e.getMessage(), e);
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void registerMapType(String mapName, Class<?> typeClass) {
        if (!mapNames.contains(mapName)) {
            mapNames.add(mapName);
            putMeta(MAP_NAMES_META_KEY, mapNames);
            
            // Register the serializer's type for this map
            if (mapTypes == null) {
                mapTypes = new HashMap<>();
            }
            
            mapTypes.put(mapName, typeClass.getName());
            putMeta(MAP_TYPES_META_KEY, mapTypes);
        }
    }

    @Override
    public Class<?> getMapType(String mapName) {
        String className = mapTypes.get(mapName);
        if (className == null) {
            return null;
        }
        
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            System.err.println("Failed to load class for map type: " + className);
            return null;
        }
    }

    /**
     * Serializes an object to bytes for storage in LevelDB.
     * Handles primitive types, strings, and complex objects via JSON.
     * For complex nested objects, type information is preserved.
     *
     * @param obj The object to serialize
     * @return The serialized bytes, or null if serialization fails
     */
    private byte[] toBytes(Object obj) {
        if (obj == null) {
            return null;
        }
        
        try {
            if (obj instanceof String) {
                return ("__STRING__:" + obj).getBytes(StandardCharsets.UTF_8);
            } else if (obj instanceof Long) {
                return ("__LONG__:" + obj).getBytes(StandardCharsets.UTF_8);
            } else if (obj instanceof Integer) {
                return ("__INT__:" + obj).getBytes(StandardCharsets.UTF_8);
            } else if (obj instanceof Boolean) {
                return ("__BOOLEAN__:" + obj).getBytes(StandardCharsets.UTF_8);
            } else if (obj instanceof Double) {
                return ("__DOUBLE__:" + obj).getBytes(StandardCharsets.UTF_8);
            } else if (obj instanceof Float) {
                return ("__FLOAT__:" + obj).getBytes(StandardCharsets.UTF_8);
            } else if (obj instanceof byte[]) {
                return ("__BYTES__:" + Base64.getEncoder().encodeToString((byte[]) obj)).getBytes(StandardCharsets.UTF_8);
            } else if (obj instanceof FcEntity) {
                // Specialized handling for FcEntity objects
                String typeName = obj.getClass().getName();
                String jsonData = ((FcEntity)obj).toJson();
                return ("__ENTITY__:" + typeName + ":" + jsonData).getBytes(StandardCharsets.UTF_8);
            } else if (obj instanceof Collection || obj instanceof Map) {
                // For collections and maps, preserve type information
                String typeName = obj.getClass().getName();
                // Use a specialized Gson that handles type adapters
                GsonBuilder builder = new GsonBuilder().setPrettyPrinting();
                // Register any needed type adapters here
                Gson specializedGson = builder.create();
                String jsonData = specializedGson.toJson(obj);
                return ("__CLASS__:" + typeName + ":" + jsonData).getBytes(StandardCharsets.UTF_8);
            } else {
                // For all other objects, use standard JSON serialization with type information
                String typeName = obj.getClass().getName();
                String jsonData = gson.toJson(obj);
                return ("__CLASS__:" + typeName + ":" + jsonData).getBytes(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("Error serializing object of type {}: {}", obj.getClass().getName(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Deserializes bytes to an object from LevelDB storage.
     * Handles primitive types, strings, and complex objects via JSON.
     * For complex nested objects, uses type information to restore the original type.
     *
     * @param bytes The bytes to deserialize
     * @return The deserialized object, or null if deserialization fails
     */
    @SuppressWarnings("unchecked")
    private Object fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        
        try {
            // Try to parse as a String
            String strValue = new String(bytes, StandardCharsets.UTF_8);
            
            // Check if it's a typed value
            if (strValue.startsWith("__STRING__:")) {
                return strValue.substring("__STRING__:".length());
            } else if (strValue.startsWith("__LONG__:")) {
                return Long.parseLong(strValue.substring("__LONG__:".length()));
            } else if (strValue.startsWith("__INT__:")) {
                return Integer.parseInt(strValue.substring("__INT__:".length()));
            } else if (strValue.startsWith("__BOOLEAN__:")) {
                return Boolean.parseBoolean(strValue.substring("__BOOLEAN__:".length()));
            } else if (strValue.startsWith("__DOUBLE__:")) {
                return Double.parseDouble(strValue.substring("__DOUBLE__:".length()));
            } else if (strValue.startsWith("__FLOAT__:")) {
                return Float.parseFloat(strValue.substring("__FLOAT__:".length()));
            } else if (strValue.startsWith("__BYTES__:")) {
                return Base64.getDecoder().decode(strValue.substring("__BYTES__:".length()));
            } else if (strValue.startsWith("__ENTITY__:")) {
                // Handle specialized FcEntity format
                int secondColonIndex = strValue.indexOf(':', "__ENTITY__:".length());
                if (secondColonIndex > 0) {
                    String className = strValue.substring("__ENTITY__:".length(), secondColonIndex);
                    String jsonData = strValue.substring(secondColonIndex + 1);
                    
                    try {
                        Class<?> clazz = Class.forName(className);
                        // Use the static fromJson method on FcEntity
                        if (FcEntity.class.isAssignableFrom(clazz)) {
                            return FcEntity.fromJson(jsonData, (Class<? extends FcEntity>) clazz);
                        }
                    } catch (ClassNotFoundException e) {
                        log.warn("Entity class not found: {}, falling back to raw JSON", className);
                        return jsonData;
                    }
                }
            } else if (strValue.startsWith("__CLASS__:")) {
                // Format is __CLASS__:className:jsonData
                int secondColonIndex = strValue.indexOf(':', "__CLASS__:".length());
                if (secondColonIndex > 0) {
                    String className = strValue.substring("__CLASS__:".length(), secondColonIndex);
                    String jsonData = strValue.substring(secondColonIndex + 1);
                    
                    try {
                        Class<?> clazz = Class.forName(className);
                        // Specialized handling for collections and maps
                        if (List.class.isAssignableFrom(clazz)) {
                            Type listType = new com.google.gson.reflect.TypeToken<List<Object>>(){}.getType();
                            return gson.fromJson(jsonData, listType);
                        } else if (Set.class.isAssignableFrom(clazz)) {
                            Type setType = new com.google.gson.reflect.TypeToken<Set<Object>>(){}.getType();
                            return gson.fromJson(jsonData, setType);
                        } else if (Map.class.isAssignableFrom(clazz)) {
                            Type mapType = new com.google.gson.reflect.TypeToken<Map<String, Object>>(){}.getType();
                            return gson.fromJson(jsonData, mapType);
                        } else {
                            // For custom classes, try to create a new instance with the correct type
                            try {
                                // See if there's a constructor available
                                Object instance = clazz.getDeclaredConstructor().newInstance();
                                // Use a reflection-based approach if needed
                                return gson.fromJson(jsonData, clazz);
                            } catch (Exception e) {
                                // Fall back to direct deserialization
                                return gson.fromJson(jsonData, clazz);
                            }
                        }
                    } catch (ClassNotFoundException e) {
                        log.warn("Class not found: {}. Falling back to string value.", className);
                        return jsonData;
                    }
                }
            }
            
            // If no prefix is found, return the string value
            return strValue;
        } catch (Exception e) {
            log.error("Error deserializing object: {}", e.getMessage(), e);
            return null;
        }
    }

    private T deserializeItem(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        
        try {
            return FcEntity.fromBytes(bytes, entityClass);
        } catch (Exception e) {
            log.error("Error deserializing item: {}", e.getMessage(), e);
            return null;
        }
    }

    // Helper methods for key generation
    private byte[] getItemKey(String key) {
        return (ITEM_PREFIX + key).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] getMetaKey(String key) {
        return (META_PREFIX + key).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] getSettingsKey(String key) {
        return (SETTING_PREFIX + key).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] getStateKey(String key) {
        return (STATE_PREFIX + key).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] getMapKey(String mapName, String key) {
        return (getMapKeyPrefix(mapName) + key).getBytes(StandardCharsets.UTF_8);
    }

    private String getMapKeyPrefix(String mapName) {
        return MAP_PREFIX + mapName + ":";
    }

    private String bytesToString(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // Helper methods for database operations
    private void updateIndex(String key) {
        if (sortType == SortType.NO_SORT) {
            return;
        }
        
        Long existingIndex = idIndexMap.get(key);
        
        switch (sortType) {
            case NO_SORT:
                // Do nothing
                break;
            case KEY_ORDER:
                if (existingIndex == null) {
                    // Find the correct position based on key order
                    long insertIndex = 1;
                    for (Map.Entry<Long, String> entry : indexIdMap.entrySet()) {
                        if (key.compareTo(entry.getValue()) < 0) {
                            break;
                        }
                        insertIndex = entry.getKey() + 1;
                    }
                    
                    // Shift higher indices up
                    shiftHigherIndicesUp1(insertIndex);
                    
                    // Insert at the correct position
                    indexIdMap.put(insertIndex, key);
                    idIndexMap.put(key, insertIndex);
                }
                break;
            case UPDATE_ORDER:
            case ACCESS_ORDER:
                // Remove old index if exists
                if (existingIndex != null) {
                    indexIdMap.remove(existingIndex);
                    idIndexMap.remove(key);
                    
                    // Shift higher indices down
                    shiftHigherIndicesDown1(existingIndex);
                }
                
                // Add at the end with next available index
                long lastIndex = indexIdMap.isEmpty() ? 0 : indexIdMap.lastKey();
                long newIndex = lastIndex + 1;
                
                indexIdMap.put(newIndex, key);
                idIndexMap.put(key, newIndex);
                break;
            case BIRTH_ORDER:
                // Only add if not already exists
                if (existingIndex == null) {
                    lastIndex = indexIdMap.isEmpty() ? 0 : indexIdMap.lastKey();
                    newIndex = lastIndex + 1;
                    
                    indexIdMap.put(newIndex, key);
                    idIndexMap.put(key, newIndex);
                }
                break;
        }
    }

    private void updateAccessOrder(String key) {
        if (sortType != SortType.ACCESS_ORDER) {
            return;
        }
        
        writeLock.lock();
        try {
            Long existingIndex = idIndexMap.get(key);
            if (existingIndex != null) {
                // Remove from current position
                indexIdMap.remove(existingIndex);
                idIndexMap.remove(key);
                
                // Shift higher indices down
                shiftHigherIndicesDown1(existingIndex);
                
                // Add at the end
                long lastIndex = indexIdMap.isEmpty() ? 0 : indexIdMap.lastKey();
                long newIndex = lastIndex + 1;
                
                indexIdMap.put(newIndex, key);
                idIndexMap.put(key, newIndex);
            }
        } finally {
            writeLock.unlock();
        }
    }

    private void updateAccessOrderBatch(List<String> keys) {
        if (sortType != SortType.ACCESS_ORDER || keys == null || keys.isEmpty()) {
            return;
        }
        
        writeLock.lock();
        try {
            // Sort keys by current index to minimize shifting
            List<String> sortedKeys = new ArrayList<>(keys);
            sortedKeys.sort((k1, k2) -> {
                Long i1 = idIndexMap.get(k1);
                Long i2 = idIndexMap.get(k2);
                if (i1 == null) return i2 == null ? 0 : -1;
                if (i2 == null) return 1;
                return i1.compareTo(i2);
            });
            
            // Process each key
            for (String key : sortedKeys) {
                Long existingIndex = idIndexMap.get(key);
                if (existingIndex != null) {
                    // Remove from current position
                    indexIdMap.remove(existingIndex);
                    idIndexMap.remove(key);
                    
                    // Shift higher indices down
                    shiftHigherIndicesDown1(existingIndex);
                }
            }
            
            // Add all keys at the end in order
            long lastIndex = indexIdMap.isEmpty() ? 0 : indexIdMap.lastKey();
            
            for (String key : sortedKeys) {
                lastIndex++;
                indexIdMap.put(lastIndex, key);
                idIndexMap.put(key, lastIndex);
            }
        } finally {
            writeLock.unlock();
        }
    }

    private void shiftHigherIndicesDown1(Long index) {
        List<Map.Entry<Long, String>> entriesToShift = new ArrayList<>();
        
        // Collect entries with higher indices
        for (Map.Entry<Long, String> entry : indexIdMap.entrySet()) {
            if (entry.getKey() > index) {
                entriesToShift.add(entry);
            }
        }
        
        // Sort by index in ascending order to rebuild indices sequentially
        entriesToShift.sort(Map.Entry.comparingByKey());
        
        // Rebuild indices sequentially starting from the removed index
        long newIndex = index;
        
        for (Map.Entry<Long, String> entry : entriesToShift) {
            String id = entry.getValue();
            long oldIndex = entry.getKey();
            
            // Remove from old index
            indexIdMap.remove(oldIndex);
            
            // Add with new sequential index
            indexIdMap.put(newIndex, id);
            idIndexMap.put(id, newIndex);
            
            newIndex++;
        }
    }

    private void shiftHigherIndicesUp1(Long index) {
        List<Map.Entry<Long, String>> entriesToShift = new ArrayList<>();
        
        // Collect entries with higher or equal indices
        for (Map.Entry<Long, String> entry : indexIdMap.entrySet()) {
            if (entry.getKey() >= index) {
                entriesToShift.add(entry);
            }
        }
        
        // Sort by index in descending order to avoid overwriting during shifting
        entriesToShift.sort((e1, e2) -> e2.getKey().compareTo(e1.getKey()));
        
        // Shift each entry up, starting from the highest index
        for (Map.Entry<Long, String> entry : entriesToShift) {
            String id = entry.getValue();
            long oldIndex = entry.getKey();
            long newIndex = oldIndex + 1;
            
            // Remove from old index
            indexIdMap.remove(oldIndex);
            
            // Add with new index
            indexIdMap.put(newIndex, id);
            idIndexMap.put(id, newIndex);
        }
    }

    public void reIndex(String updateOrderField, String birthOrderField) {
        if (sortType == SortType.NO_SORT) {
            return;
        }
        
        writeLock.lock();
        try {
            // Clear existing indices
            indexIdMap.clear();
            idIndexMap.clear();
            
            // Get all items
            Map<String, T> items = getItemMap();
            List<Map.Entry<String, T>> entries = new ArrayList<>(items.entrySet());
            
            // Sort based on sort type
            switch (sortType) {
                case KEY_ORDER:
                    // Sort by key
                    entries.sort(Map.Entry.comparingByKey());
                    break;
                case UPDATE_ORDER:
                    if (updateOrderField != null) {
                        // Sort by update field
                        entries.sort((e1, e2) -> {
                            Long h1 = getFieldValue(e1.getValue(), updateOrderField);
                            Long h2 = getFieldValue(e2.getValue(), updateOrderField);
                            return compareValues(h1, h2);
                        });
                    }
                    break;
                case BIRTH_ORDER:
                    if (birthOrderField != null) {
                        // Sort by birth field
                        entries.sort((e1, e2) -> {
                            Long h1 = getFieldValue(e1.getValue(), birthOrderField);
                            Long h2 = getFieldValue(e2.getValue(), birthOrderField);
                            return compareValues(h1, h2);
                        });
                    }
                    break;
                case ACCESS_ORDER:
                    // Nothing special for access order during reindex
                    break;
                default:
                    break;
            }
            
            // Rebuild indices
            long index = 1;
            for (Map.Entry<String, T> entry : entries) {
                String key = entry.getKey();
                indexIdMap.put(index, key);
                idIndexMap.put(key, index);
                index++;
            }
        } finally {
            writeLock.unlock();
        }
    }

    private Long getFieldValue(T obj, String fieldName) {
        try {
            java.lang.reflect.Method method = obj.getClass().getMethod("get" + capitalize(fieldName));
            return (Long) method.invoke(obj);
        } catch (Exception e) {
            log.error("Error getting field value: {}", e.getMessage(), e);
            return null;
        }
    }

    private int compareValues(Long h1, Long h2) {
        if (h1 == null && h2 == null) return 0;
        if (h1 == null) return -1;
        if (h2 == null) return 1;
        return h1.compareTo(h2);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    // Utility methods for LevelDB operations
    private byte[] bytes(long value) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (value & 0xffL);
            value >>= 8;
        }
        return result;
    }

    private byte[] bytes(int value) {
        byte[] result = new byte[4];
        for (int i = 3; i >= 0; i--) {
            result[i] = (byte) (value & 0xff);
            value >>= 8;
        }
        return result;
    }

    private long bytesToLong(byte[] bytes) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result <<= 8;
            result |= (bytes[i] & 0xff);
        }
        return result;
    }

    private int bytesToInt(byte[] bytes) {
        int result = 0;
        for (int i = 0; i < 4; i++) {
            result <<= 8;
            result |= (bytes[i] & 0xff);
        }
        return result;
    }

    public Class<T> getEntityClass() {
        return entityClass;
    }

    @Override
    public void removeAllSettings() {
        writeLock.lock();
        try {
            // Clear the settings cache
            settingsCache.clear();
            
            // Remove all settings entries from the database
            WriteBatch batch = db.createWriteBatch();
            try {
                try (DBIterator iterator = db.iterator()) {
                    byte[] settingsPrefix = SETTING_PREFIX.getBytes(StandardCharsets.UTF_8);
                    for (iterator.seek(settingsPrefix);
                         iterator.hasNext();
                         iterator.next()) {
                        
                        byte[] keyBytes = iterator.peekNext().getKey();
                        String fullKey = bytesToString(keyBytes);
                        
                        if (!fullKey.startsWith(SETTING_PREFIX)) {
                            break; // Exit when we're past SETTINGS_PREFIX keys
                        }
                        
                        batch.delete(keyBytes);
                    }
                }
                
                // Commit the batch
                db.write(batch);
            } finally {
                try {
                    batch.close();
                } catch (IOException e) {
                    log.error("Error closing batch when removing all settings: {}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error removing all settings: {}", e.getMessage(), e);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Map<String, String> getAllSettings() {
        readLock.lock();
        try {
            Map<String, String> result = new HashMap<>();
            
            // Convert all settings cache entries to strings
            for (Map.Entry<String, Object> entry : settingsCache.entrySet()) {
                Object value = entry.getValue();
                if (value != null) {
                    result.put(entry.getKey(), value.toString());
                }
            }
            
            // Make sure we haven't missed any settings directly from the database
            try (DBIterator iterator = db.iterator()) {
                byte[] settingsPrefix = SETTING_PREFIX.getBytes(StandardCharsets.UTF_8);
                for (iterator.seek(settingsPrefix);
                     iterator.hasNext();
                     iterator.next()) {
                    
                    byte[] keyBytes = iterator.peekNext().getKey();
                    String fullKey = bytesToString(keyBytes);
                    
                    if (!fullKey.startsWith(SETTING_PREFIX)) {
                        break; // Exit when we're past SETTINGS_PREFIX keys
                    }
                    
                    String key = fullKey.substring(SETTING_PREFIX.length());
                    if (!result.containsKey(key)) {
                        byte[] valueBytes = iterator.peekNext().getValue();
                        Object value = fromBytes(valueBytes);
                        if (value != null) {
                            result.put(key, value.toString());
                        }
                    }
                }
            }
            
            return result;
        } catch (Exception e) {
            log.error("Error getting all settings: {}", e.getMessage(), e);
            return new HashMap<>();
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public void clearAllState() {
        writeLock.lock();
        try {
            // Clear the state cache
            stateCache.clear();
            
            // Remove all state entries from the database
            WriteBatch batch = db.createWriteBatch();
            try {
                try (DBIterator iterator = db.iterator()) {
                    byte[] statePrefix = STATE_PREFIX.getBytes(StandardCharsets.UTF_8);
                    for (iterator.seek(statePrefix);
                         iterator.hasNext();
                         iterator.next()) {
                        
                        byte[] keyBytes = iterator.peekNext().getKey();
                        String fullKey = bytesToString(keyBytes);
                        
                        if (!fullKey.startsWith(STATE_PREFIX)) {
                            break; // Exit when we're past STATE_PREFIX keys
                        }
                        
                        batch.delete(keyBytes);
                    }
                }
                
                // Commit the batch
                db.write(batch);
            } finally {
                try {
                    batch.close();
                } catch (IOException e) {
                    log.error("Error closing batch when clearing all state: {}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error clearing all state: {}", e.getMessage(), e);
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public Map<String, String> getAllState() {
        readLock.lock();
        try {
            Map<String, String> result = new HashMap<>();
            
            // Convert all state cache entries to strings
            for (Map.Entry<String, Object> entry : stateCache.entrySet()) {
                Object value = entry.getValue();
                if (value != null) {
                    result.put(entry.getKey(), value.toString());
                }
            }
            
            // Make sure we haven't missed any state entries directly from the database
            try (DBIterator iterator = db.iterator()) {
                byte[] statePrefix = STATE_PREFIX.getBytes(StandardCharsets.UTF_8);
                for (iterator.seek(statePrefix);
                     iterator.hasNext();
                     iterator.next()) {
                    
                    byte[] keyBytes = iterator.peekNext().getKey();
                    String fullKey = bytesToString(keyBytes);
                    
                    if (!fullKey.startsWith(STATE_PREFIX)) {
                        break; // Exit when we're past STATE_PREFIX keys
                    }
                    
                    String key = fullKey.substring(STATE_PREFIX.length());
                    if (!result.containsKey(key)) {
                        byte[] valueBytes = iterator.peekNext().getValue();
                        Object value = fromBytes(valueBytes);
                        if (value != null) {
                            result.put(key, value.toString());
                        }
                    }
                }
            }
            
            return result;
        } catch (Exception e) {
            log.error("Error getting all state: {}", e.getMessage(), e);
            return new HashMap<>();
        } finally {
            readLock.unlock();
        }
    }

    
    
    /**
     * Creates a list of test Secret entities for testing purposes.
     *
     * @param count The number of Secret entities to create
     * @return A list of Secret entities
     */
    private static List<Secret> createTestSecrets(int count) {
        List<Secret> secrets = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            Secret secret = new Secret();
            secret.setId("secret_id_" + i);
            secret.setAlg("AES-256");
            secret.setCipher("encrypted_data_" + i);
            secret.setOwner("owner_" + i);
            secret.setBirthTime(System.currentTimeMillis());
            secret.setBirthHeight(100L + i);
            secret.setLastHeight(200L + i);
            secret.setActive(true);
            
            secrets.add(secret);
        }
        
        return secrets;
    }
    
    /**
     * Recursively deletes a directory and all its contents.
     *
     * @param directory The directory to delete
     * @return true if successful, false otherwise
     */
    private static boolean deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
        }
        return directory.delete();
    }

    @Override
    public <V> void createOrderedList(String listName, Class<V> vClass) {
        if (listName == null || vClass == null) {
            return;
        }
        
        writeLock.lock();
        try {
            if (!listNames.contains(listName)) {
                listNames.add(listName);
                putMeta(MAP_NAMES_META_KEY, mapNames);
                
                // Register the type for this list
                if (mapTypes == null) {
                    mapTypes = new HashMap<>();
                }
                
                String type = vClass.getName();
                mapTypes.put(listName, type);
                putMeta(MAP_TYPES_META_KEY, mapTypes);
                
                // Initialize the count to 0
                db.put(getListCountKey(listName), bytes(0L));
            }
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public <V> long addToList(String listName, V value) {
        if (listName == null || value == null) {
            return -1;
        }
        
        writeLock.lock();
        try {
            // Get the current count
            byte[] countBytes = db.get(getListCountKey(listName));
            long count = countBytes != null ? bytesToLong(countBytes) : 0;
            
            // Add the new element
            long index = count;
            byte[] valueBytes = toBytes(value);
            db.put(getListItemKey(listName, index), valueBytes);
            
            // Increment the count
            count++;
            db.put(getListCountKey(listName), bytes(count));
            
            return index;
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public <V> long addAllToList(String listName, List<V> values) {
        if (listName == null || values == null || values.isEmpty()) {
            return -1;
        }
        
        writeLock.lock();
        try {
            // Get the current count
            byte[] countBytes = db.get(getListCountKey(listName));
            long count = countBytes != null ? bytesToLong(countBytes) : 0;
            
            // Add all elements
            long startIndex = count;
            WriteBatch batch = db.createWriteBatch();
            try {
                for (V value : values) {
                    if (value != null) {
                        byte[] valueBytes = toBytes(value);
                        batch.put(getListItemKey(listName, count), valueBytes);
                        count++;
                    }
                }
                
                // Update the count
                batch.put(getListCountKey(listName), bytes(count));
                
                // Commit the batch
                db.write(batch);
            } finally {
                try {
                    batch.close();
                } catch (IOException e) {
                    log.error("Error closing batch: {}", e.getMessage(), e);
                }
            }
            
            return startIndex;
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public <V> V getFromList(String listName, long index) {
        if (listName == null || index < 0) {
            return null;
        }
        
        readLock.lock();
        try {
            byte[] valueBytes = db.get(getListItemKey(listName, index));
            if (valueBytes == null) {
                return null;
            }
            
            @SuppressWarnings("unchecked")
            V value = (V) fromBytes(valueBytes);
            return value;
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public <V> List<V> getAllFromList(String listName) {
        if (listName == null) {
            return new ArrayList<>();
        }
        
        readLock.lock();
        try {
            // Get the count
            byte[] countBytes = db.get(getListCountKey(listName));
            long count = countBytes != null ? bytesToLong(countBytes) : 0;
            
            List<V> result = new ArrayList<>();
            for (long i = 0; i < count; i++) {
                V value = getFromList(listName, i);
                if (value != null) {
                    result.add(value);
                }
            }
            
            return result;
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public <V> List<V> getRangeFromList(String listName, long startIndex, long endIndex) {
        if (listName == null || startIndex < 0 || endIndex <= startIndex) {
            return new ArrayList<>();
        }
        
        readLock.lock();
        try {
            // Get the count
            byte[] countBytes = db.get(getListCountKey(listName));
            long count = countBytes != null ? bytesToLong(countBytes) : 0;
            
            // Adjust endIndex if it's beyond the list size
            endIndex = Math.min(endIndex, count);
            
            List<V> result = new ArrayList<>();
            for (long i = startIndex; i < endIndex; i++) {
                V value = getFromList(listName, i);
                if (value != null) {
                    result.add(value);
                }
            }
            
            return result;
        } finally {
            readLock.unlock();
        }
    }
    
    /**
     * Gets a range of elements from an ordered list in reverse order (from end to beginning).
     * 
     * @param <V> The type of the values
     * @param listName The name of the list
     * @param startIndex The starting index (inclusive) from the end of the list
     * @param endIndex The ending index (exclusive) from the end of the list
     * @return A list of elements in the specified range in reverse order
     */
    public <V> List<V> getRangeFromListReverse(String listName, long startIndex, long endIndex) {
        if (listName == null || startIndex < 0 || endIndex <= startIndex) {
            return new ArrayList<>();
        }
        
        readLock.lock();
        try {
            // Get the count
            byte[] countBytes = db.get(getListCountKey(listName));
            long count = countBytes != null ? bytesToLong(countBytes) : 0;
            
            if (count == 0) {
                return new ArrayList<>();
            }
            
            // Convert indices to be from the end of the list
            // For the first page (startIndex=0, endIndex=40), we want items [count-1 to count-40]
            long startFromBeginning = Math.max(0, count - endIndex);
            long endFromBeginning = count - startIndex;
            
            // Adjust if the range is beyond the list size
            if (startFromBeginning >= count || endFromBeginning <= 0 || endFromBeginning <= startFromBeginning) {
                return new ArrayList<>();
            }
            
            List<V> result = new ArrayList<>();
            // Iterate in reverse order - from newest to oldest
            for (long i = endFromBeginning - 1; i >= startFromBeginning; i--) {
                V value = getFromList(listName, i);
                if (value != null) {
                    result.add(value);
                }
            }
            
            return result;
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public boolean removeFromList(String listName, long index) {
        if (listName == null || index < 0) {
            return false;
        }
        
        writeLock.lock();
        try {
            // Get the count
            byte[] countBytes = db.get(getListCountKey(listName));
            long count = countBytes != null ? bytesToLong(countBytes) : 0;
            
            if (index >= count) {
                return false;
            }
            
            // Remove the element
            db.delete(getListItemKey(listName, index));
            
            // Shift all elements after the removed one
            WriteBatch batch = db.createWriteBatch();
            try {
                for (long i = index + 1; i < count; i++) {
                    byte[] valueBytes = db.get(getListItemKey(listName, i));
                    if (valueBytes != null) {
                        batch.put(getListItemKey(listName, i - 1), valueBytes);
                        batch.delete(getListItemKey(listName, i));
                    }
                }
                
                // Update the count
                batch.put(getListCountKey(listName), bytes(count - 1));
                
                // Commit the batch
                db.write(batch);
            } finally {
                try {
                    batch.close();
                } catch (IOException e) {
                    log.error("Error closing batch: {}", e.getMessage(), e);
                }
            }
            
            return true;
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public int removeFromList(String listName, List<Long> indices) {
        if (listName == null || indices == null || indices.isEmpty()) {
            return 0;
        }
        
        writeLock.lock();
        try {
            // Get the count
            byte[] countBytes = db.get(getListCountKey(listName));
            long count = countBytes != null ? bytesToLong(countBytes) : 0;
            
            // Sort indices in descending order to avoid shifting issues
            List<Long> sortedIndices = new ArrayList<>(indices);
            Collections.sort(sortedIndices, Collections.reverseOrder());
            
            int removedCount = 0;
            WriteBatch batch = db.createWriteBatch();
            try {
                for (Long index : sortedIndices) {
                    if (index != null && index >= 0 && index < count) {
                        // Remove the element
                        batch.delete(getListItemKey(listName, index));
                        removedCount++;
                    }
                }
                
                // Shift all elements after the removed ones
                long shiftCount = 0;
                for (long i = 0; i < count; i++) {
                    if (sortedIndices.contains(i)) {
                        shiftCount++;
                    } else if (shiftCount > 0) {
                        byte[] valueBytes = db.get(getListItemKey(listName, i));
                        if (valueBytes != null) {
                            batch.put(getListItemKey(listName, i - shiftCount), valueBytes);
                            batch.delete(getListItemKey(listName, i));
                        }
                    }
                }
                
                // Update the count
                batch.put(getListCountKey(listName), bytes(count - removedCount));
                
                // Commit the batch
                db.write(batch);
            } finally {
                try {
                    batch.close();
                } catch (IOException e) {
                    log.error("Error closing batch: {}", e.getMessage(), e);
                }
            }
            
            return removedCount;
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public long getListSize(String listName) {
        if (listName == null) {
            return 0;
        }
        
        readLock.lock();
        try {
            byte[] countBytes = db.get(getListCountKey(listName));
            return countBytes != null ? bytesToLong(countBytes) : 0;
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public void clearList(String listName) {
        if (listName == null) {
            return;
        }
        
        writeLock.lock();
        try {
            // Get the count
            byte[] countBytes = db.get(getListCountKey(listName));
            long count = countBytes != null ? bytesToLong(countBytes) : 0;
            
            // Delete all elements
            WriteBatch batch = db.createWriteBatch();
            try {
                for (long i = 0; i < count; i++) {
                    batch.delete(getListItemKey(listName, i));
                }
                
                // Reset the count
                batch.put(getListCountKey(listName), bytes(0L));
                
                // Commit the batch
                db.write(batch);
            } finally {
                try {
                    batch.close();
                } catch (IOException e) {
                    log.error("Error closing batch: {}", e.getMessage(), e);
                }
            }
        } finally {
            writeLock.unlock();
        }
    }
    
    private byte[] getListCountKey(String listName) {
        return (LIST_COUNT_PREFIX + listName).getBytes(StandardCharsets.UTF_8);
    }
    
    private byte[] getListItemKey(String listName, long index) {
        return (LIST_ITEM_PREFIX + listName + ":" + index).getBytes(StandardCharsets.UTF_8);
    }

    public static void main(String[] args) {
        // Create a temporary test directory
        String testDbPath = "test_db_path";
        String testDbName = "test_db";
        File testDir = new File(testDbPath);
        if (!testDir.exists()) {
            testDir.mkdirs();
        }

        try {
            System.out.println("\n=== Testing Serialization and Deserialization ===");
            
            // Initialize the DB
            LevelDB<Secret> db = new LevelDB<>(SortType.NO_SORT, Secret.class);
            db.initialize("test", "test", testDbPath, testDbName);
            
            // Test primitive types
            System.out.println("\n1. Testing primitive types:");
            
            // String
            String stringValue = "Hello, World!";
            byte[] stringBytes = db.toBytes(stringValue);
            Object deserializedString = db.fromBytes(stringBytes);
            System.out.println("String: " + stringValue + " -> " + deserializedString + 
                              " (Equal: " + stringValue.equals(deserializedString) + ")");
            
            // Long
            Long longValue = 1234567890L;
            byte[] longBytes = db.toBytes(longValue);
            Object deserializedLong = db.fromBytes(longBytes);
            System.out.println("Long: " + longValue + " -> " + deserializedLong + 
                              " (Equal: " + longValue.equals(deserializedLong) + ")");
            
            // Integer
            Integer intValue = 42;
            byte[] intBytes = db.toBytes(intValue);
            Object deserializedInt = db.fromBytes(intBytes);
            System.out.println("Integer: " + intValue + " -> " + deserializedInt + 
                              " (Equal: " + intValue.equals(deserializedInt) + ")");
            
            // Boolean
            Boolean boolValue = true;
            byte[] boolBytes = db.toBytes(boolValue);
            Object deserializedBool = db.fromBytes(boolBytes);
            System.out.println("Boolean: " + boolValue + " -> " + deserializedBool + 
                              " (Equal: " + boolValue.equals(deserializedBool) + ")");
            
            // Double
            Double doubleValue = 3.14159265359;
            byte[] doubleBytes = db.toBytes(doubleValue);
            Object deserializedDouble = db.fromBytes(doubleBytes);
            System.out.println("Double: " + doubleValue + " -> " + deserializedDouble + 
                              " (Equal: " + doubleValue.equals(deserializedDouble) + ")");
            
            // Float
            Float floatValue = 2.71828f;
            byte[] floatBytes = db.toBytes(floatValue);
            Object deserializedFloat = db.fromBytes(floatBytes);
            System.out.println("Float: " + floatValue + " -> " + deserializedFloat + 
                              " (Equal: " + floatValue.equals(deserializedFloat) + ")");
            
            // byte[]
            byte[] originalBytes = "Binary data".getBytes(StandardCharsets.UTF_8);
            byte[] bytesBytes = db.toBytes(originalBytes);
            Object deserializedBytes = db.fromBytes(bytesBytes);
            System.out.println("byte[]: " + new String(originalBytes, StandardCharsets.UTF_8) + " -> " + 
                              new String((byte[])deserializedBytes, StandardCharsets.UTF_8) + 
                              " (Equal: " + Arrays.equals(originalBytes, (byte[])deserializedBytes) + ")");
            
            // Test collections
            System.out.println("\n2. Testing collections:");
            
            // List
            List<String> listValue = Arrays.asList("one", "two", "three");
            byte[] listBytes = db.toBytes(listValue);
            Object deserializedList = db.fromBytes(listBytes);
            System.out.println("List: " + listValue + " -> " + deserializedList + 
                              " (Equal: " + listValue.equals(deserializedList) + ")");
            
            // Map
            Map<String, Integer> mapValue = new HashMap<>();
            mapValue.put("one", 1);
            mapValue.put("two", 2);
            mapValue.put("three", 3);
            byte[] mapBytes = db.toBytes(mapValue);
            Object deserializedMap = db.fromBytes(mapBytes);
            System.out.println("Map: " + mapValue + " -> " + deserializedMap + 
                              " (Equal: " + mapValue.equals(deserializedMap) + ")");
            
            // Test complex objects
            System.out.println("\n3. Testing complex objects:");
            
            // Create a test Secret object
            Secret secret = new Secret();
            secret.setId("test_secret");
            secret.setAlg("AES256");
            secret.setCipher("encrypted_data");
            secret.setOwner("test_owner");
            secret.setBirthTime(System.currentTimeMillis());
            secret.setBirthHeight(100L);
            secret.setLastHeight(200L);
            secret.setActive(true);
            
            byte[] secretBytes = db.toBytes(secret);
            Object deserializedSecret = db.fromBytes(secretBytes);
            System.out.println("Secret: " + secret.getId() + " -> " + 
                              ((Secret)deserializedSecret).getId() +
                              " (Equal: " + secret.getId().equals(((Secret)deserializedSecret).getId()) + ")");
            
            // Close the database
            db.close();
            System.out.println("\nDatabase closed successfully");
            
        } catch (Exception e) {
            System.err.println("Error during test: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up test directory
            deleteDirectory(new File(testDbPath));
            System.out.println("\nTest directory cleaned up");
        }
    }

    private void handleStaleLock(File dbFolder) {
        File lockFile = new File(dbFolder, "LOCK");
        if (lockFile.exists()) {
            // Check if the lock file is stale (older than 30 seconds)
            long lockAge = System.currentTimeMillis() - lockFile.lastModified();
            long staleThreshold = 5 * 60 * 1000; // 5 minutes in milliseconds
            
            // Try to read and validate lock file content
            boolean isValidLock = false;
            String lockContent = "";
            try {
                lockContent = new String(java.nio.file.Files.readAllBytes(lockFile.toPath()), StandardCharsets.UTF_8);
                // Check if lock content indicates a valid Java/LevelDB process
                isValidLock = lockContent.contains("pid:") || 
                             lockContent.contains("java") || 
                             lockContent.contains("leveldb");
            } catch (IOException e) {
                log.warn("Could not read lock file content: {}", e.getMessage());
            }
            
            // If lock is old or content is invalid, try to remove it
            if (lockAge > staleThreshold || !isValidLock) {
                log.warn("Found potentially stale lock file ({} ms old). Attempting to remove it.", lockAge);
                if (lockFile.delete()) {
                    log.info("Successfully removed stale lock file");
                } else {
                    // If we can't delete it, try force unlock
                    try {
                        // Try to force release any file system locks
                        java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(
                            lockFile.toPath(), 
                            java.nio.file.StandardOpenOption.WRITE
                        );
                        java.nio.channels.FileLock fileLock = channel.tryLock();
                        if (fileLock != null) {
                            fileLock.release();
                            channel.close();
                            // Try delete again after releasing lock
                            if (lockFile.delete()) {
                                log.info("Successfully removed lock file after force unlock");
                            } else {
                                log.error("Failed to remove lock file even after force unlock");
                                throw new RuntimeException("Failed to remove lock file after force unlock");
                            }
                        } else {
                            channel.close();
                            log.error("Failed to acquire lock for removal");
                            throw new RuntimeException("Failed to acquire lock for removal");
                        }
                    } catch (IOException e) {
                        log.error("Failed to force unlock: {}", e.getMessage());
                        throw new RuntimeException("Failed to force unlock: " + e.getMessage());
                    }
                }
            } else {
                // Lock appears to be valid and recent
                log.warn("Found recent lock file ({} ms old) with content: {}. Database might be in use.", 
                         lockAge, lockContent.trim());
                throw new RuntimeException("Database appears to be in use by another process");
            }
        }
    }
}
