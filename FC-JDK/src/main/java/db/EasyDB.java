package db;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import data.fcData.FcEntity;
import utils.FileUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A Realm-inspired implementation of LocalDB interface.
 * This implementation uses in-memory maps with JSON serialization to disk.
 * 
 * @param <T> The type of objects to store
 */
public class EasyDB<T extends FcEntity> implements LocalDB<T> {
    private static final String SETTINGS_MAP = "settingsMap";
//    private final FcEntity.SimpleFcSerializer<T> valueSerializer;
    private final SortType sortType;
    private final Class<T> entityClass;  // Add class type field
    private volatile String dbPath;
    private volatile String dbName;
    private volatile File dbFile;
    private final Gson gson;
    
    private volatile boolean isClosed = false;
    
    // Add read-write lock
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    
    // Change temporary variables to thread-local
    private final ThreadLocal<Long> tempIndex = new ThreadLocal<>();
    private final ThreadLocal<String> tempId = new ThreadLocal<>();
    
    // Main storage maps
    private final Map<String, T> itemMap = new ConcurrentHashMap<>();
    private final ConcurrentNavigableMap<Long, String> indexIdMap = new ConcurrentSkipListMap<>();
    private final ConcurrentNavigableMap<String, Long> idIndexMap = new ConcurrentSkipListMap<>();
    private final Map<String, Object> metaMap = new ConcurrentHashMap<>();
    private final Map<String, Object> settingsMap = new ConcurrentHashMap<>();
    private final Map<String, Object> stateMap = new ConcurrentHashMap<>();
    
    // Named maps for additional storage
    private final Map<String, Map<String, Object>> namedMaps = new ConcurrentHashMap<>();
    
    private final Map<String, Class<?>> mapTypes = new ConcurrentHashMap<>();
    private final Map<String, List<Object>> orderedLists = new ConcurrentHashMap<>();
    private final Map<String, Long> listCounts = new ConcurrentHashMap<>();
    private final Set<String> listNames = new HashSet<>();
    private final Set<String> mapNames = new HashSet<>();
    
    public EasyDB(SortType sortType, Class<T> entityClass) {
        this.sortType = sortType;
        this.entityClass = entityClass;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        
        // Initialize standard maps
        createMap(LOCAL_REMOVED_MAP);
        createMap(ON_CHAIN_DELETED_MAP);
        createMap(SETTINGS_MAP);
    }
    
    @Override
    public synchronized void initialize(String fid, String sid, String dbPath, String dbName) {
        this.dbPath = dbPath;
        this.dbName = dbName;
        
        if (isClosed) {
            this.isClosed = false;
        }
        
        String fileName = FileUtils.makeFileName(fid, sid, dbName.toLowerCase(), LocalDB.DOT_DB);
        dbFile = new File(Path.of(dbPath, fileName).toString());
        
        if (dbFile.exists()) {
            loadFromDisk(dbFile);
        }
        
        metaMap.put(SORT_TYPE_META_KEY, this.sortType.name());
        
        if (sortType != SortType.NO_SORT) {
            if (itemMap.size() != indexIdMap.size() || itemMap.size() != idIndexMap.size()) {
                reIndex(null, null);
            }
        }
    }
    
    @Override
    public SortType getSortType() {
        return sortType;
    }
    
    @Override
    public T get(String key) {
        T value;
        readLock.lock();
        try {
            value = itemMap.get(key);
        } finally {
            readLock.unlock();
        }
        
        // Update access order outside of the read lock if needed
        if (value != null && sortType == SortType.ACCESS_ORDER) {
            writeLock.lock();
            try {
                updateAccessOrderBatch(Collections.singletonList(key));
            } finally {
                writeLock.unlock();
            }
        }
        return value;
    }
    
    @Override
    public void put(String key, T value) {
        writeLock.lock();
        try {
            itemMap.put(key, value);
            updateIndex(key);
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public List<T> get(List<String> keys) {
        readLock.lock();
        try {
            List<T> values = new ArrayList<>();
            List<String> accessedKeys = new ArrayList<>();
            for (String key : keys) {
                T value = itemMap.get(key);
                if (value != null) {
                    values.add(value);
                    if (sortType == SortType.ACCESS_ORDER) {
                        accessedKeys.add(key);
                    }
                }
            }
            if (!accessedKeys.isEmpty()) {
                updateAccessOrderBatch(accessedKeys);
            }
            return values;
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public void remove(String key) {
        writeLock.lock();
        try {
            if (sortType != SortType.NO_SORT) {
                Long index = idIndexMap.get(key);
                if (index != null) {
                    indexIdMap.remove(index);
                    idIndexMap.remove(key);
                    
                    shiftHigherIndicesDown1(index);
                }
            }
            itemMap.remove(key);
            
            putInMap(LOCAL_REMOVED_MAP, key, System.currentTimeMillis());
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public void remove(List<T> list) {
        if (list == null || list.isEmpty()) return;
        
        writeLock.lock();
        try {
            // Collect indices to update
            List<Long> indicesToUpdate = new ArrayList<>();
            
            for (T item : list) {
                if (item == null) continue;
                
                String key = item.getId();
                if (key == null) continue;
                
                // Remove from item map
                itemMap.remove(key);
                
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
            writeLock.unlock();
        }
    }
    
    @Override
    public void commit() {
        saveToDisk();
    }
    
    @Override
    public void close() {
        if (!isClosed) {
            saveToDisk();
            isClosed = true;
        }
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
        return itemMap;
    }
    
    @Override
    public Map<String, Object> getMetaMap() {
        return metaMap;
    }
    
    @Override
    public Map<String, Object> getSettingsMap() {
        return settingsMap;
    }
    
    @Override
    public Map<String, Object> getStateMap() {
        return stateMap;
    }
    
    @Override
    public NavigableMap<Long, String> getIndexIdMap() {
        return indexIdMap;
    }
    
    @Override
    public NavigableMap<String, Long> getIdIndexMap() {
        return idIndexMap;
    }
    
    @Override
    public Long getIndexById(String id) {
        return idIndexMap.get(id);
    }
    
    @Override
    public String getIdByIndex(long index) {
        return indexIdMap.get(index);
    }
    
    @Override
    public int getSize() {
        return itemMap.size();
    }
    
    @Override
    public Object getMeta(String key) {
        readLock.lock();
        try {
            return metaMap.get(key);
        } finally {
            readLock.unlock();
        }
    }

    
    @Override
    public Object getState(String key) {
        readLock.lock();
        try {
            return stateMap.get(key);
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public LinkedHashMap<String, T> getMap(Integer size, String fromId, Long fromIndex,
                                          boolean isFromInclude, String toId, Long toIndex, 
                                          boolean isToInclude, boolean isFromEnd) {
        if (indexIdMap.isEmpty()) {
            return new LinkedHashMap<>();
        }

        try {
            NavigableMap<Long, String> subMap = indexIdMap;
            
            if (isFromEnd) {
                // Handle start boundary
                if (fromIndex != null) {
                    if (fromIndex == 1) return new LinkedHashMap<>();
                    subMap = subMap.headMap(fromIndex, false);
                } else if (fromId != null) {
                    Long endIndexFromId = idIndexMap.get(fromId);
                    if (endIndexFromId != null) {
                        subMap = subMap.headMap(endIndexFromId, isFromInclude);
                    }
                }
                
                // Handle end boundary
                if (toIndex != null) {
                    subMap = subMap.tailMap(toIndex, isToInclude);
                } else if (toId != null) {
                    Long startIndexFromId = idIndexMap.get(toId);
                    if (startIndexFromId != null) {
                        subMap = subMap.tailMap(startIndexFromId, isToInclude);
                    }
                }
            } else {
                if (fromIndex != null) {
                    subMap = subMap.tailMap(fromIndex, isFromInclude);
                } else if (fromId != null) {
                    Long startIndexFromId = idIndexMap.get(fromId);
                    if (startIndexFromId != null) {
                        subMap = subMap.tailMap(startIndexFromId, isFromInclude);
                    }
                }
                
                if (toIndex != null) {
                    subMap = subMap.headMap(toIndex, isToInclude);
                } else if (toId != null) {
                    Long endIndexFromId = idIndexMap.get(toId);
                    if (endIndexFromId != null) {
                        subMap = subMap.headMap(endIndexFromId, isToInclude);
                    }
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
            
            // Collect all entries first to avoid modifying the map while iterating
            List<Map.Entry<Long, String>> entries = new ArrayList<>(subMap.entrySet());
            
            for (Map.Entry<Long, String> entry : entries) {
                if (size != null && result.size() >= size) {
                    break;
                }
                
                lastIndex = entry.getKey();
                lastId = entry.getValue();
                
                T item = itemMap.get(lastId);
                if (item != null) {
                    result.put(lastId, item);
                }
            }
            
            // Update access order for all items after collecting them
            if (sortType == SortType.ACCESS_ORDER && !result.isEmpty()) {
                updateAccessOrderBatch(new ArrayList<>(result.keySet()));
            }
            
            // Store the appropriate index for next pagination
            if (lastIndex != null) {
                tempIndex.set(lastIndex);
            }
            
            // Store the last processed ID
            tempId.set(lastId);
            
            return result;
            
        } catch (Exception e) {
            System.err.println("ERROR in getMap: " + e.getMessage());
            e.printStackTrace();
            return new LinkedHashMap<>();
        }
    }
    
    private void updateAccessOrderBatch(List<String> ids) {
        if (sortType != SortType.ACCESS_ORDER || ids.isEmpty()) {
            return;
        }
        
        // Note: caller must hold writeLock
        for (String id : ids) {
            Long existingIndex = idIndexMap.get(id);
            if (existingIndex != null) {
                indexIdMap.remove(existingIndex);
                idIndexMap.remove(id);
            }
        }
        
        long newIndex = indexIdMap.isEmpty() ? 1 : indexIdMap.lastKey() + 1;
        for (String id : ids) {
            if (itemMap.containsKey(id)) {  // Only update if item still exists
                indexIdMap.put(newIndex, id);
                idIndexMap.put(id, newIndex);
                newIndex++;
            }
        }
    }
    
    @Override
    public List<T> getList(Integer size, String fromId, Long fromIndex,
                          boolean isFromInclude, String toId, Long toIndex, 
                          boolean isToInclude, boolean isFromEnd) {
        return new ArrayList<>(getMap(size, fromId, fromIndex, isFromInclude, toId, toIndex, isToInclude, isFromEnd).values());
    }
    
    @Override
    public synchronized void putAll(Map<String, T> items) {
        if (items == null || items.isEmpty()) return;
        
        writeLock.lock();
        try {
            if (sortType == SortType.KEY_ORDER) {
                TreeMap<String, T> sortedItems = new TreeMap<>(String::compareTo);
                sortedItems.putAll(items);
                
                itemMap.putAll(sortedItems);
                for (String key : sortedItems.keySet()) {
                    updateIndex(key);
                }
            } else {
                itemMap.putAll(items);
                for (String key : items.keySet()) {
                    updateIndex(key);
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
            return new HashMap<>(itemMap);
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public List<T> searchString(String part) {
        readLock.lock();
        try {
            List<T> matches = new ArrayList<>();
            for (Map.Entry<String, T> entry : itemMap.entrySet()) {
                T value = entry.getValue();
                String json = gson.toJson(value);
                if (json.contains(part)) {
                    matches.add(value);
                }
            }
            return matches;
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public void putMeta(String key, Object value) {
        writeLock.lock();
        try {
            metaMap.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public void putState(String key, Object value) {
        writeLock.lock();
        try {
            stateMap.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public void removeMeta(String key) {
        writeLock.lock();
        try {
            metaMap.remove(key);
        } finally {
            writeLock.unlock();
        }
    }
    

    @Override
    public void removeState(String key) {
        writeLock.lock();
        try {
            stateMap.remove(key);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void clearAllState() {
        writeLock.lock();
        try {
            stateMap.clear();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Map<String, String> getAllState() {
        readLock.lock();
        try {
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : stateMap.entrySet()) {
                result.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : null);
            }
            return result;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void clear() {
        itemMap.clear();
        if (sortType != SortType.NO_SORT) {
            indexIdMap.clear();
            idIndexMap.clear();
        }
    }
    
    @Override
    public void removeList(List<String> ids) {
        writeLock.lock();
        try {
            if (sortType == SortType.KEY_ORDER) {
                List<Long> indicesToRemove = new ArrayList<>();
                for (String key : ids) {
                    Long index = idIndexMap.get(key);
                    if (index != null) {
                        indicesToRemove.add(index);
                    }
                }
                
                Collections.sort(indicesToRemove);
                
                for (Long indexToRemove : indicesToRemove) {
                    String key = indexIdMap.get(indexToRemove);
                    if (key != null) {
                        itemMap.remove(key);
                        indexIdMap.remove(indexToRemove);
                        idIndexMap.remove(key);
                        putInMap(LOCAL_REMOVED_MAP, key, System.currentTimeMillis());
                        
                        shiftHigherIndicesDown1(indexToRemove);
                    }
                }
            } else {
                for (String key : ids) {
                    Long index = idIndexMap.get(key);
                    if (index != null) {
                        indexIdMap.remove(index);
                        idIndexMap.remove(key);
                        shiftHigherIndicesDown1(index);
                    }
                    itemMap.remove(key);
                    putInMap(LOCAL_REMOVED_MAP, key, System.currentTimeMillis());
                }
            }
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public synchronized void removeFromMap(String mapName, String key) {
        writeLock.lock();
        try {
            Map<String, Object> map = namedMaps.get(mapName);
            if (map != null) {
                map.remove(key);
            }
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public synchronized void removeFromMap(String mapName, List<String> keys) {
        writeLock.lock();
        try {
            Map<String, Object> map = namedMaps.get(mapName);
            if (map != null) {
                for (String key : keys) {
                    map.remove(key);
                }
            }
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public synchronized void clearDB() {
        writeLock.lock();
        try {
            itemMap.clear();
            if (sortType != SortType.NO_SORT) {
                indexIdMap.clear();
                idIndexMap.clear();
            }
            
            metaMap.clear();
            settingsMap.clear();
            stateMap.clear();
            namedMaps.clear();
            
            createMap(LOCAL_REMOVED_MAP);
            createMap(ON_CHAIN_DELETED_MAP);
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public Set<String> getMapNames() {
        return namedMaps.keySet();
    }
    
    @Override
    public void clearMap(String mapName) {
        Map<String, Object> map = namedMaps.get(mapName);
        if (map != null) {
            map.clear();
        }
    }
    
    @Override
    public int getMapSize(String mapName) {
        Map<String, Object> map = namedMaps.get(mapName);
        return map != null ? map.size() : 0;
    }
    
    @Override
    public void putAll(List<T> items, String idField) {
        if (items == null || items.isEmpty()) return;
        
        // Create a map of id -> item
        Map<String, T> itemMap = new HashMap<>();
        for (T item : items) {
            try {
                // Extract ID using reflection
                java.lang.reflect.Method method = item.getClass().getMethod("get" + capitalize(idField));
                String id = (String) method.invoke(item);
                itemMap.put(id, item);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to get ID from field: " + idField, e);
            }
        }
        
        putAll(itemMap);
    }
    
    // Helper methods
    private void createMap(String mapName) {
        if (!namedMaps.containsKey(mapName)) {
            namedMaps.put(mapName, new ConcurrentHashMap<>());
            
            // Store map name in meta
            Set<String> mapNames = new HashSet<>(getMapNames());
            mapNames.add(mapName);
            metaMap.put(MAP_NAMES_META_KEY, mapNames);
        }
    }
    
    private void updateIndex(String id) {
        // Skip index updates if sorting is disabled
        if (sortType == SortType.NO_SORT) {
            return;
        }
        
        Long existingIndex = idIndexMap.get(id);
        
        switch (sortType) {
            case NO_SORT -> {
                // Do nothing
            }
            case KEY_ORDER -> {
                if (existingIndex == null) {
                    // Find the correct position based on key order
                    long insertIndex = 1;
                    for (Map.Entry<Long, String> entry : indexIdMap.entrySet()) {
                        if (id.compareTo(entry.getValue()) < 0) {
                            break;
                        }
                        insertIndex = entry.getKey() + 1;
                    }
                    
                    // Shift all higher indices up by 1
                    NavigableMap<Long, String> entriesToShift = indexIdMap.tailMap(insertIndex, true);
                    List<Map.Entry<Long, String>> shiftList = new ArrayList<>(entriesToShift.entrySet());
                    
                    for (int i = shiftList.size() - 1; i >= 0; i--) {
                        Map.Entry<Long, String> entry = shiftList.get(i);
                        String existingId = entry.getValue();
                        long oldIndex = entry.getKey();
                        long newIndex = oldIndex + 1;
                        
                        indexIdMap.put(newIndex, existingId);
                        idIndexMap.put(existingId, newIndex);
                    }
                    
                    // Insert the new entry
                    indexIdMap.put(insertIndex, id);
                    idIndexMap.put(id, insertIndex);
                }
            }
            
            case UPDATE_ORDER, ACCESS_ORDER -> {
                // Remove old index if exists
                if (existingIndex != null) {
                    indexIdMap.remove(existingIndex);
                }
                
                // Add at the end with next available index
                long newIndex = indexIdMap.isEmpty() ? 1 : indexIdMap.lastKey() + 1;
                indexIdMap.put(newIndex, id);
                idIndexMap.put(id, newIndex);
            }
            case BIRTH_ORDER -> {
                // Only add if not already exists
                if (existingIndex == null) {
                    long newIndex = indexIdMap.isEmpty() ? 1 : indexIdMap.lastKey() + 1;
                    indexIdMap.put(newIndex, id);
                    idIndexMap.put(id, newIndex);
                }
            }
        }
    }
    
    private void shiftHigherIndicesDown1(Long index) {
        writeLock.lock();
        try {
            NavigableMap<Long, String> higherEntries = indexIdMap.tailMap(index, false);
            List<Map.Entry<Long, String>> entriesToShift = new ArrayList<>(higherEntries.entrySet());
            
            for (Map.Entry<Long, String> entry : entriesToShift) {
                String id = entry.getValue();
                long oldIndex = entry.getKey();
                long newIndex = oldIndex - 1;
                
                indexIdMap.remove(oldIndex);
                indexIdMap.put(newIndex, id);
                idIndexMap.put(id, newIndex);
            }
        } finally {
            writeLock.unlock();
        }
    }
    
    // Database file structure
    private static class DatabaseState<T> implements Serializable {
        private static final long serialVersionUID = 1L;
        
        Map<String, String> itemMapJson = new HashMap<>();
        Map<Long, String> indexIdMap = new HashMap<>();
        Map<String, Long> idIndexMap = new HashMap<>();
        Map<String, Object> metaMap = new HashMap<>();
        Map<String, Object> settingsMap = new HashMap<>();
        Map<String, Object> stateMap = new HashMap<>();
        Map<String, Map<String, Object>> namedMaps = new HashMap<>();
        Class<T> entityClass; // Add class information
        
        // Constructor to create a state from the database
        public DatabaseState(Map<String, T> itemMap, Map<Long, String> indexIdMap, 
                          Map<String, Long> idIndexMap, Map<String, Object> metaMap,
                          Map<String, Object> settingsMap, Map<String, Object> stateMap,
                          Map<String, Map<String, Object>> namedMaps, Gson gson,
                          Class<T> entityClass) {
            // Convert to serializable format
            for (Map.Entry<String, T> entry : itemMap.entrySet()) {
                itemMapJson.put(entry.getKey(), gson.toJson(entry.getValue()));
            }
            this.indexIdMap.putAll(indexIdMap);
            this.idIndexMap.putAll(idIndexMap);
            this.metaMap.putAll(metaMap);
            this.settingsMap.putAll(settingsMap);
            this.stateMap.putAll(stateMap);
            this.entityClass = entityClass;
            
            // Copy named maps
            for (Map.Entry<String, Map<String, Object>> entry : namedMaps.entrySet()) {
                Map<String, Object> mapCopy = new HashMap<>();
                mapCopy.putAll(entry.getValue());
                this.namedMaps.put(entry.getKey(), mapCopy);
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private void loadFromDisk(File file) {
        System.out.println("Loading database from: " + file.getAbsolutePath());
        
        try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(file.toPath()))) {
            // Read the database state
            DatabaseState<T> state = (DatabaseState<T>) ois.readObject();
            
            // Deserialize items using the stored class information
            for (Map.Entry<String, String> entry : state.itemMapJson.entrySet()) {
                T item = gson.fromJson(entry.getValue(), state.entityClass);
                itemMap.put(entry.getKey(), item);
            }
            
            // Copy indices
            indexIdMap.putAll(state.indexIdMap);
            idIndexMap.putAll(state.idIndexMap);
            
            // Copy metadata
            metaMap.putAll(state.metaMap);
            
            // Copy settings and state
            settingsMap.putAll(state.settingsMap);
            stateMap.putAll(state.stateMap);
            
            // Copy named maps
            for (Map.Entry<String, Map<String, Object>> entry : state.namedMaps.entrySet()) {
                Map<String, Object> map = namedMaps.computeIfAbsent(entry.getKey(), k -> new ConcurrentHashMap<>());
                map.putAll(entry.getValue());
            }
            
            // Load map types from meta
            Object typesObj = metaMap.get(MAP_TYPES_META_KEY);
            if (typesObj instanceof Map) {
                Map<String, String> typeNames = (Map<String, String>) typesObj;
                for (Map.Entry<String, String> entry : typeNames.entrySet()) {
                    try {
                        Class<?> typeClass = Class.forName(entry.getValue());
                        mapTypes.put(entry.getKey(), typeClass);
                    } catch (ClassNotFoundException e) {
                        System.err.println("Failed to load class for map type: " + entry.getValue());
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error loading database: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private synchronized void saveToDisk() {
        if (dbFile == null) {
            System.out.println("Cannot save database: dbFile is null");
            return;
        }
        
        System.out.println("Saving database to disk: " + dbFile.getAbsolutePath());
        
        writeLock.lock();
        try {
            if (!dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }
            
            DatabaseState<T> state = new DatabaseState<>(
                itemMap, new HashMap<>(indexIdMap), new HashMap<>(idIndexMap),
                new HashMap<>(metaMap), new HashMap<>(settingsMap), new HashMap<>(stateMap),
                new HashMap<>(namedMaps), gson,
                entityClass  // Use the stored class type
            );
            
            try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(dbFile.toPath()))) {
                oos.writeObject(state);
            }
            
        } catch (Exception e) {
            System.err.println("Error saving database: " + e.getMessage());
            e.printStackTrace();
        } finally {
            writeLock.unlock();
        }
        System.out.println("Done.");
    }
    
    public synchronized void reIndex(String updateOrderField, String birthOrderField) {
        writeLock.lock();
        try {
            if (sortType == SortType.NO_SORT) {
                return;
            }
            
            int itemSize = itemMap.size();
            int indexIdSize = indexIdMap.size();
            int idIndexSize = idIndexMap.size();
            
            if (itemSize == indexIdSize && itemSize == idIndexSize) {
                return;
            }
            
            indexIdMap.clear();
            idIndexMap.clear();
            
            List<Map.Entry<String, T>> entries = new ArrayList<>(itemMap.entrySet());
            
            switch (sortType) {
                case KEY_ORDER -> entries.sort(Map.Entry.comparingByKey());
                case UPDATE_ORDER, ACCESS_ORDER -> {
                    if (updateOrderField != null) {
                        entries.sort((e1, e2) -> {
                            Long h1 = getFieldValue(e1.getValue(), updateOrderField);
                            Long h2 = getFieldValue(e2.getValue(), updateOrderField);
                            return compareValues(h1, h2);
                        });
                    }
                }
                case BIRTH_ORDER -> {
                    if (birthOrderField != null) {
                        entries.sort((e1, e2) -> {
                            Long h1 = getFieldValue(e1.getValue(), birthOrderField);
                            Long h2 = getFieldValue(e2.getValue(), birthOrderField);
                            return compareValues(h1, h2);
                        });
                    }
                }
                default -> throw new IllegalArgumentException("Unexpected value: " + sortType);
            }
            
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
            return (Long) obj.getClass().getMethod(fieldName).invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }
    
    private int compareValues(Long h1, Long h2) {
        if (h1 == null && h2 == null) return 0;
        if (h1 == null) return -1;
        if (h2 == null) return 1;
        return h1.compareTo(h2);
    }
    
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    @Override
    public <V> void putInMap(String mapName, String key, V value) {
        Map<String, Object> map = namedMaps.computeIfAbsent(mapName, k -> new ConcurrentHashMap<>());
        map.put(key, value);
    }

    @Override
    public <V> V getFromMap(String mapName, String key) {
        Map<String, Object> map = namedMaps.get(mapName);
        if (map != null) {
            @SuppressWarnings("unchecked")
            V value = (V) map.get(key);
            return value;
        }
        return null;
    }

    @Override
    public <V> Map<String, V> getAllFromMap(String mapName) {
        Map<String, Object> map = namedMaps.get(mapName);
        if (map != null) {
            Map<String, V> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                @SuppressWarnings("unchecked")
                V value = (V) entry.getValue();
                result.put(entry.getKey(), value);
            }
            return result;
        }
        return null;
    }

    @Override
    public <V> List<V> getFromMap(String mapName, List<String> keyList) {
        List<V> result = new ArrayList<>();
        Map<String, Object> map = namedMaps.get(mapName);
        if (map != null) {
            for (String key : keyList) {
                @SuppressWarnings("unchecked")
                V value = (V) map.get(key);
                if (value != null) {
                    result.add(value);
                }
            }
        }
        return result;
    }

    @Override
    public <V> void putAllInMap(String mapName, List<String> keyList, List<V> valueList) {
        if (keyList.size() != valueList.size()) {
            throw new IllegalArgumentException("Key list and value list must be the same size");
        }
        
        Map<String, Object> map = namedMaps.computeIfAbsent(mapName, k -> new ConcurrentHashMap<>());
        for (int i = 0; i < keyList.size(); i++) {
            map.put(keyList.get(i), valueList.get(i));
        }
    }

    @Override
    public <V> void putAllInMap(String mapName, Map<String, V> map) {
        if (mapName == null || map == null || map.isEmpty()) {
            return;
        }

        Map<String, Object> targetMap = namedMaps.computeIfAbsent(mapName, k -> new ConcurrentHashMap<>());
        targetMap.putAll((Map<String, Object>) map);
    }

    public Class<T> getEntityClass() {
        return entityClass;
    }

    public String getDbPath() {
        return dbPath;
    }

    public void setDbPath(String dbPath) {
        this.dbPath = dbPath;
    }

    public File getDbFile() {
        return dbFile;
    }

    public void setDbFile(File dbFile) {
        this.dbFile = dbFile;
    }

    public void setClosed(boolean closed) {
        isClosed = closed;
    }

    @Override
    public void registerMapType(String mapName, Class<?> typeClass) {
        writeLock.lock();
        try {
            mapTypes.put(mapName, typeClass);
            // Store the map types in meta
            Map<String, String> typeNames = new HashMap<>();
            for (Map.Entry<String, Class<?>> entry : mapTypes.entrySet()) {
                typeNames.put(entry.getKey(), entry.getValue().getName());
            }
            metaMap.put(MAP_TYPES_META_KEY, typeNames);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Class<?> getMapType(String mapName) {
        readLock.lock();
        try {
            return mapTypes.get(mapName);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public <V> void createMap(String mapName, Class<V> vClass) {

    }

    // Settings Map methods
    @Override
    public void putSetting(String key, Object value) {
        if (key == null) {
            return;
        }
        
        writeLock.lock();
        try {
            String stringValue;
            
            if (value == null) {
                stringValue = null;
            } else if (value instanceof String) {
                stringValue = (String) value;
            } else if (value instanceof Number || value instanceof Boolean) {
                stringValue = value.toString();
            } else {
                // Use Gson for complex types
                stringValue = gson.toJson(value);
            }
            
            putInMap(SETTINGS_MAP, key, stringValue);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public String getSetting(String key) {
        if (key == null) {
            return null;
        }
        readLock.lock();
        try {
            return getFromMap(SETTINGS_MAP, key);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void removeSetting(String key) {
        if (key == null) {
            return;
        }

        writeLock.lock();
        try {
            removeFromMap(SETTINGS_MAP, key);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void removeAllSettings() {
        writeLock.lock();
        try {
            clearMap(SETTINGS_MAP);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Map<String, String> getAllSettings() {
        readLock.lock();
        try {
            return getAllFromMap(SETTINGS_MAP);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public T getByIndex(long index) {
        readLock.lock();
        try {
            return itemMap.get(indexIdMap.get(index));
        } finally {
            readLock.unlock();
        }
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
                mapTypes.put(listName, vClass);
                
                // Initialize the list
                orderedLists.put(listName, new ArrayList<>());
                listCounts.put(listName, 0L);
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
            List<Object> list = orderedLists.computeIfAbsent(listName, k -> new ArrayList<>());
            Long count = listCounts.computeIfAbsent(listName, k -> 0L);
            
            // Add the new element
            long index = count;
            list.add(value);
            
            // Increment the count
            listCounts.put(listName, count + 1);
            
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
            List<Object> list = orderedLists.computeIfAbsent(listName, k -> new ArrayList<>());
            Long count = listCounts.computeIfAbsent(listName, k -> 0L);
            
            // Add all elements
            long startIndex = count;
            list.addAll(values);
            
            // Update the count
            listCounts.put(listName, count + values.size());
            
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
            List<Object> list = orderedLists.get(listName);
            if (list == null || index >= list.size()) {
                return null;
            }
            
            @SuppressWarnings("unchecked")
            V value = (V) list.get((int) index);
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
            List<Object> list = orderedLists.get(listName);
            if (list == null) {
                return new ArrayList<>();
            }
            
            @SuppressWarnings("unchecked")
            List<V> result = new ArrayList<>((List<V>) list);
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
            List<Object> list = orderedLists.get(listName);
            if (list == null || startIndex >= list.size()) {
                return new ArrayList<>();
            }
            
            // Adjust endIndex if it's beyond the list size
            endIndex = Math.min(endIndex, list.size());
            
            // Create a new list with the elements in the range
            List<V> result = new ArrayList<>();
            for (long i = startIndex; i < endIndex; i++) {
                @SuppressWarnings("unchecked")
                V value = (V) list.get((int) (i & 0x7FFFFFFF));
                result.add(value);
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
            List<Object> list = orderedLists.get(listName);
            if (list == null || index >= list.size()) {
                return false;
            }
            
            // Remove the element
            list.remove((int) index);
            
            // Update the count
            listCounts.put(listName, (long) list.size());
            
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
            List<Object> list = orderedLists.get(listName);
            if (list == null) {
                return 0;
            }
            
            // Sort indices in descending order to avoid shifting issues
            List<Long> sortedIndices = new ArrayList<>(indices);
            sortedIndices.sort(Collections.reverseOrder());
            
            int removedCount = 0;
            for (Long index : sortedIndices) {
                if (index != null && index >= 0 && index < list.size()) {
                    list.remove(index);
                    removedCount++;
                }
            }
            
            // Update the count
            listCounts.put(listName, (long) list.size());
            
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
            List<Object> list = orderedLists.get(listName);
            return list != null ? list.size() : 0;
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
            List<Object> list = orderedLists.get(listName);
            if (list != null) {
                list.clear();
                listCounts.put(listName, 0L);
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public <V> List<V> getRangeFromListReverse(String listName, long startIndex, long endIndex) {
        if (listName == null || startIndex < 0 || endIndex <= startIndex) {
            return new ArrayList<>();
        }
        
        readLock.lock();
        try {
            List<Object> list = orderedLists.get(listName);
            if (list == null || list.isEmpty()) {
                return new ArrayList<>();
            }
            
            int count = list.size();
            
            // Convert from end-based indices to start-based indices
            // If startIndex is 0, we want the last element (count-1)
            // If endIndex is 1, we want up to the second-to-last element (count-2)
            int startFromBeginning = Math.max(0, count - (int)endIndex);
            int endFromBeginning = Math.min(count, count - (int)startIndex);
            
            // Adjust if the range is beyond the list size
            if (startFromBeginning >= count || endFromBeginning <= 0) {
                return new ArrayList<>();
            }
            
            List<V> result = new ArrayList<>();
            // Iterate in reverse order
            for (int i = endFromBeginning - 1; i >= startFromBeginning; i--) {
                @SuppressWarnings("unchecked")
                V value = (V) list.get(i);
                result.add(value);
            }
            
            return result;
        } finally {
            readLock.unlock();
        }
    }
}
