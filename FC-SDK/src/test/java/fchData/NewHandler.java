package fchData;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.mapdb.HTreeMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.JsonSyntaxException;

import appTools.Inputer;
import appTools.Menu;
import appTools.Settings;
import appTools.Shower;
import tools.FileTools;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.io.IOException;

public class NewHandler<T>{
    protected final LocalDB<T> localDB;
    protected final Serializer<T> valueSerializer;
    protected final Class<T> itemClass;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Gson gson = new Gson();
    private final Settings settings;
    private final String mainFid;
    private final String sid;
    private final byte[] symKey;
    private final byte[] priKey;
    private final HandlerType handlerType;
    

    public NewHandler(HandlerType handlerType){
        this.handlerType = handlerType;
        this.settings = null;
        this.mainFid = null;
        this.sid = null;
        this.symKey = null;
        this.priKey = null;
        this.valueSerializer = null;
        this.localDB = null;
        this.itemClass = null;
    }

    public NewHandler(HandlerType handlerType, String dbDir,LocalDB.SortType sortType, Serializer<T> valueSerializer, Class<T> itemClass) {
        this.handlerType = handlerType;
        this.valueSerializer = valueSerializer;
        this.localDB = new MapDBDatabase<>(valueSerializer, sortType);
        this.localDB.initialize(dbDir, handlerType.toString());
        this.settings = null;
        this.mainFid = null;
        this.sid = null;
        this.symKey = null;
        this.priKey = null;
        this.itemClass = itemClass;
    }
    public NewHandler(Settings settings, HandlerType handlerType){
        this(settings,handlerType,null,null,null);
    }
    public NewHandler(Settings settings, HandlerType handlerType, LocalDB.SortType sortType, Serializer<T> valueSerializer, Class<T> itemClass) {
        this.settings = settings;
        this.handlerType = handlerType;
        this.mainFid = settings.getMainFid();
        this.sid = settings.getSid();
        this.symKey = settings.getSymKey();
        this.priKey = Settings.getMainFidPriKey(symKey,settings);
        String dbName = FileTools.makeFileName(mainFid, sid, handlerType.toString(), constants.Strings.DOT_DB);
        String dbDir = settings.getDbDir();
        this.valueSerializer = valueSerializer;
        this.localDB = new MapDBDatabase<>(valueSerializer, sortType);
        this.localDB.initialize(dbDir, dbName);
        this.itemClass = itemClass;
    }

    public NewHandler(Settings settings,HandlerType handlerType, Class<T> itemClass){
        this.handlerType = handlerType;
        this.settings = settings;
        this.mainFid = settings.getMainFid();
        this.sid = settings.getSid();
        this.symKey = settings.getSymKey();
        this.priKey = Settings.getMainFidPriKey(symKey,settings);
        this.valueSerializer = null;
        this.localDB = null;
        this.itemClass = itemClass;
    }

    public void put(String id, T item) {
        if (localDB == null) return;
        lock.writeLock().lock();
        try {
            localDB.put(id, item);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void putAll(Map<String, T> items) {
        if (localDB == null) return;
        lock.writeLock().lock();
        try {
            localDB.putAll(items);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public T get(String id) {
        if (localDB == null) return null;
        lock.readLock().lock();
        try {
            return (T) localDB.get(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<String, T> getAll() {
        if (localDB == null) return null;
        lock.readLock().lock();
        try {
            return localDB.getAll();
        } finally {
            lock.readLock().unlock();
        }
    }

    public NavigableMap<Long, String> getIndexIdMap() {
        if (localDB == null) return null;
        return localDB.getIndexIdMap();
    }

    public NavigableMap<String, Long> getIdIndexMap() {
        if (localDB == null) return null;
        return localDB.getIdIndexMap();
    }

    public T getItemById(String id) {
        if (localDB == null) return null;
        return (T) localDB.get(id);
    }

    public T getItemByIndex(long index) {
        if (localDB == null) return null;
        String id = localDB.getIndexIdMap().get(index);
        return id != null ? (T) localDB.get(id) : null;
    }

    public Long getIndexById(String id) {
        if (localDB == null) return null;
        return localDB.getIdIndexMap().get(id);
    }

    public String getIdByIndex(long index) {
        if (localDB == null) return null;
        return localDB.getIndexIdMap().get(index);
    }

    public List<T> getItemList(int size, boolean isFromEnd) {
        if (localDB == null) return null;
        return localDB.getItemList(size, null, null, false, null, null, false, isFromEnd);
    } 

    public List<T> getItemList(int size, Long fromIndex, String fromId, 
            boolean isFromInclude, Long toIndex, String toId, boolean isToInclude, boolean isFromEnd) {
        if (localDB == null) return null;
        return localDB.getItemList(size, fromId, fromIndex, isFromInclude, toId, toIndex, isToInclude, isFromEnd);
    } 
    
    public NavigableMap<String, T> getItemMap(int size, Long fromIndex, String fromId, 
            boolean isFromInclude, Long toIndex, String toId, boolean isToInclude, boolean isFromEnd) {
        if (localDB == null) return null;
        lock.readLock().lock();
        try {
            return localDB.getItemMap(size, fromId, fromIndex, isFromInclude, toId, toIndex, isToInclude, isFromEnd);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void close() {
        if (localDB == null) return;
        localDB.close();
    }

    public LocalDB.SortType getDatabaseSortType() {
        if (localDB == null) return null;
        return localDB.getSortType();
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
        SECRETE,
        ROOM,
        SESSION,
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
        
    public interface LocalDB<T> {
        static enum SortType {
            KEY_ORDER,      // Sorted by key
            UPDATE_ORDER,   // Most recent updated at end
            BIRTH_ORDER     // Most recent created at end
        }

        void initialize(String dbPath, String dbName);
        SortType getSortType();
        void put(String key, T value);
        T get(String key);
        void remove(String key);
        void commit();
        void close();
        boolean isClosed();
        
        // Map access methods
        @SuppressWarnings("rawtypes")
        HTreeMap getItemMap();
        NavigableMap<Long, String> getIndexIdMap();
        NavigableMap<String, Long> getIdIndexMap();
        HTreeMap<String, Object> getMetaMap();
        
        // Add these new methods
        Long getIndexById(String id);
        String getIdByIndex(long index);
        int getSize();
        Object getMeta(String key);
        
        // Rename existing method to getItemMap
        NavigableMap<String, T> getItemMap(Integer size, String fromId, Long fromIndex, 
                boolean isFromInclude, String toId, Long toIndex, boolean isToInclude, boolean isFromEnd);
        
        // Add new method that returns List
        List<T> getItemList(Integer size, String fromId, Long fromIndex, 
                boolean isFromInclude, String toId, Long toIndex, boolean isToInclude, boolean isFromEnd);
        
        // Add these new methods
        void putAll(Map<String, T> items);
        Map<String, T> getAll();

        List<String> searchString(String part);

        // Add these new methods
        void putMeta(String key, Object value);
        void removeMeta(String key);
        void clear();
    }

    @SuppressWarnings("hiding")
    public class MapDBDatabase<T> implements LocalDB<T> {
        private volatile DB db;
        @SuppressWarnings("rawtypes")
        private volatile HTreeMap itemMap;
        private volatile ConcurrentNavigableMap<Long, String> indexIdMap;
        private volatile ConcurrentNavigableMap<String, Long> idIndexMap;
        private volatile HTreeMap<String, Object> metaMap;
        private final ReadWriteLock dbLock = new ReentrantReadWriteLock();
        private final AtomicLong currentIndex = new AtomicLong(0);
        private final Serializer<T> valueSerializer;
        private final SortType sortType;
        
        public MapDBDatabase(Serializer<T> valueSerializer, SortType sortType) {
            this.valueSerializer = valueSerializer;
            this.sortType = sortType;
        }

        @Override
        public SortType getSortType() {
            return sortType;
        }
        
        @SuppressWarnings("unchecked")
        @Override
        public void initialize(String dbPath, String dbName) {
            dbLock.writeLock().lock();
            try {
                if (db == null || db.isClosed()) {
                    db = DBMaker.fileDB(Path.of(dbPath, dbName).toString())
                            .fileMmapEnable()
                            .checksumHeaderBypass()
                            .transactionEnable()
                            .make();
                                
                    itemMap = db.hashMap("items_" + dbName)
                            .keySerializer(Serializer.STRING)
                            .valueSerializer(valueSerializer)
                            .createOrOpen();
                                
                    indexIdMap = new ConcurrentSkipListMap<>(
                        db.treeMap("indexId_" + dbName)
                            .keySerializer(Serializer.LONG)
                            .valueSerializer(Serializer.STRING)
                            .createOrOpen()
                    );
                                
                    idIndexMap = new ConcurrentSkipListMap<>(
                        db.treeMap("idIndex_" + dbName)
                            .keySerializer(Serializer.STRING)
                            .valueSerializer(Serializer.LONG)
                            .createOrOpen()
                    );

                    metaMap = db.hashMap("meta_" + dbName)
                            .keySerializer(Serializer.STRING)
                            .valueSerializer(Serializer.JAVA)
                            .createOrOpen();
                }
            } finally {
                dbLock.writeLock().unlock();
            }
        }
        
        @SuppressWarnings("unchecked")
        @Override
        public void put(String key, T value) {
            dbLock.writeLock().lock();
            try {
                itemMap.put(key, value);
                updateIndex(key);
                commit();
            } finally {
                dbLock.writeLock().unlock();
            }
        }
        
        @SuppressWarnings("unchecked")
        @Override
        public T get(String key) {
            dbLock.readLock().lock();
            try {
                return (T) itemMap.get(key);
            } finally {
                dbLock.readLock().unlock();
            }
        }
        
        @Override
        public void remove(String key) {
            dbLock.writeLock().lock();
            try {
                Long index = idIndexMap.get(key);
                if (index != null) {
                    indexIdMap.remove(index);
                    idIndexMap.remove(key);
                }
                itemMap.remove(key);
                commit();
            } finally {
                dbLock.writeLock().unlock();
            }
        }
        
        @Override
        public void commit() {
            db.commit();
        }
        
        @Override
        public void close() {
            dbLock.writeLock().lock();
            try {
                if (db != null && !db.isClosed()) {
                    db.close();
                    db = null;
                    itemMap = null;
                    indexIdMap = null;
                    idIndexMap = null;
                    metaMap = null;
                }
            } finally {
                dbLock.writeLock().unlock();
            }
        }
        
        @Override
        public boolean isClosed() {
            return db == null || db.isClosed();
        }

        @SuppressWarnings("rawtypes")
        @Override
        public HTreeMap getItemMap() {
            return itemMap;
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
        public HTreeMap<String, Object> getMetaMap() {
            return metaMap;
        }

        @Override
        public Long getIndexById(String id) {
            dbLock.readLock().lock();
            try {
                return idIndexMap.get(id);
            } finally {
                dbLock.readLock().unlock();
            }
        }

        @Override
        public String getIdByIndex(long index) {
            dbLock.readLock().lock();
            try {
                return indexIdMap.get(index);
            } finally {
                dbLock.readLock().unlock();
            }
        }

        @Override
        public int getSize() {
            dbLock.readLock().lock();
            try {
                return itemMap.size();
            } finally {
                dbLock.readLock().unlock();
            }
        }

        @Override
        public Object getMeta(String key) {
            dbLock.readLock().lock();
            try {
                return metaMap.get(key);
            } finally {
                dbLock.readLock().unlock();
            }
        }

        @Override
        public NavigableMap<String, T> getItemMap(Integer size, String fromId, Long fromIndex,
                boolean isFromInclude, String toId, Long toIndex, boolean isToInclude, boolean isFromEnd) {
            dbLock.readLock().lock();
            try {
                NavigableMap<Long, String> subMap = indexIdMap;
                
                // Handle start boundary
                if (fromIndex != null || fromId != null) {
                    long startIndex = fromIndex != null ? fromIndex : idIndexMap.get(fromId);
                    subMap = subMap.tailMap(startIndex, isFromInclude);
                }
                
                // Handle end boundary
                if (toIndex != null || toId != null) {
                    long endIndex = toIndex != null ? toIndex : idIndexMap.get(toId);
                    subMap = subMap.headMap(endIndex, isToInclude);
                }

                // Reverse if needed
                if (isFromEnd) {
                    subMap = subMap.descendingMap();
                }

                // Build result map
                NavigableMap<String, T> result = new TreeMap<>();
                for (Map.Entry<Long, String> entry : subMap.entrySet()) {
                    // Break if we've reached the size limit (only if size is not null)
                    if (size != null && result.size() >= size) break;
                    
                    String id = entry.getValue();
                    @SuppressWarnings("unchecked")
                    T item = (T) itemMap.get(id);
                    if (item != null) {
                        result.put(id, item);
                    }
                }
                
                return result;
            } finally {
                dbLock.readLock().unlock();
            }
        }

        @Override
        public List<T> getItemList(Integer size, String fromId, Long fromIndex,
                boolean isFromInclude, String toId, Long toIndex, boolean isToInclude, boolean isFromEnd) {
            return getItemMap(size, fromId, fromIndex, isFromInclude, toId, toIndex, isToInclude, isFromEnd).values().stream().collect(Collectors.toList());
        }

        @SuppressWarnings("unchecked")
        @Override
        public void putAll(Map<String, T> items) {
            dbLock.writeLock().lock();
            try {
                for (Map.Entry<String, T> entry : items.entrySet()) {
                    itemMap.put(entry.getKey(), entry.getValue());
                    updateIndex(entry.getKey());
                }
                commit();
            } finally {
                dbLock.writeLock().unlock();
            }
        }

        @Override
        public Map<String, T> getAll() {
            dbLock.readLock().lock();
            try {
                Map<String, T> result = new HashMap<>();
                for (Object entry : itemMap.entrySet()) {
                    @SuppressWarnings("rawtypes")
                    Map.Entry mapEntry = (Map.Entry) entry;
                    @SuppressWarnings("unchecked")
                    T value = (T) mapEntry.getValue();
                    result.put((String) mapEntry.getKey(), value);
                }
                return result;
            } finally {
                dbLock.readLock().unlock();
            }
        }

        private void updateIndex(String id) {
            Long existingIndex = idIndexMap.get(id);
            
            switch (sortType) {
                case KEY_ORDER:
                    if (existingIndex == null) {
                        long insertIndex = 1;
                        for (Map.Entry<Long, String> entry : indexIdMap.entrySet()) {
                            if (id.compareTo(entry.getValue()) < 0) {
                                break;
                            }
                            insertIndex = entry.getKey() + 1;
                        }
                        
                        // Shift entries atomically
                        for (long i = currentIndex.get() + 1; i >= insertIndex; i--) {
                            String existingId = indexIdMap.get(i);
                            if (existingId != null) {
                                indexIdMap.put(i + 1, existingId);
                                idIndexMap.put(existingId, i + 1);
                            }
                        }
                        
                        indexIdMap.put(insertIndex, id);
                        idIndexMap.put(id, insertIndex);
                        currentIndex.set(Math.max(currentIndex.get() + 1, insertIndex));
                    }
                    break;
                    
                case UPDATE_ORDER:
                    // Always update index for UPDATE_ORDER
                    long newIndex = currentIndex.incrementAndGet();
                    if (existingIndex != null) {
                        indexIdMap.remove(existingIndex);
                    }
                    indexIdMap.put(newIndex, id);
                    idIndexMap.put(id, newIndex);
                    break;

                case BIRTH_ORDER:
                    // Only set index if it doesn't exist (preserve birth order)
                    if (existingIndex == null) {
                        long birthIndex = currentIndex.incrementAndGet();
                        indexIdMap.put(birthIndex, id);
                        idIndexMap.put(id, birthIndex);
                    }
                    break;
            }
        }

        // Method to create a new map by name
        public void createMap(String mapName, Serializer<?> serializer) {
            dbLock.writeLock().lock();
            try {
                db.hashMap(mapName)
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(serializer)
                    .createOrOpen();
            } finally {
                dbLock.writeLock().unlock();
            }
        }

        // Method to put an item into a named map
        public <V> void putInMap(String mapName, String key, V value, Serializer<V> serializer) {
            dbLock.writeLock().lock();
            try {
                HTreeMap<String, V> map = db.hashMap(mapName)
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(serializer)
                    .createOrOpen();
                map.put(key, value);
                commit();
            } finally {
                dbLock.writeLock().unlock();
            }
        }

        // Method to get an item from a named map
        public <V> V getFromMap(String mapName, String key, Serializer<V> serializer) {
            dbLock.readLock().lock();
            try {
                HTreeMap<String, V> map = db.hashMap(mapName)
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(serializer)
                    .createOrOpen();
                return map.get(key);
            } finally {
                dbLock.readLock().unlock();
            }
        }

        public <V> Map<String, V> getAllFromMap(String mapName, Serializer<V> serializer) {
            dbLock.readLock().lock();
            try {
                HTreeMap<String, V> map = db.hashMap(mapName)
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(serializer)
                    .createOrOpen();
                return map.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            } finally {
                dbLock.readLock().unlock();
            }
        }

        @Override
        public List<String> searchString(String part) {
            dbLock.readLock().lock();
            try {
                List<String> matchingKeys = new ArrayList<>();
                for (Object entry : itemMap.entrySet()) {
                    @SuppressWarnings("rawtypes")
                    Map.Entry mapEntry = (Map.Entry) entry;
                    String key = (String) mapEntry.getKey();
                    @SuppressWarnings("unchecked")
                    T value = (T) mapEntry.getValue();

                    if (value instanceof String) {
                        if (((String) value).contains(part)) {
                            matchingKeys.add(key);
                        }
                    } else {
                        try {
                            String json = gson.toJson(value);
                            Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
                            Map<String, Object> valueMap = gson.fromJson(json, mapType);

                            boolean matchFound = valueMap.values().stream().anyMatch(val -> {
                                if (val instanceof String) {
                                    return ((String) val).contains(part);
                                } else if (val instanceof Number) {
                                    return val.toString().contains(part);
                                } else if (val instanceof byte[]) {
                                    String utf8String = new String((byte[]) val, StandardCharsets.UTF_8);
                                    return utf8String.contains(part);
                                }
                                return false;
                            });

                            if (matchFound) {
                                matchingKeys.add(key);
                            }
                        } catch (JsonSyntaxException e) {
                            // Handle the case where the value is not a JSON object
                            System.err.println("Error parsing JSON for key: " + key + " - " + e.getMessage());
                        }
                    }
                }
                return matchingKeys;
            } finally {
                dbLock.readLock().unlock();
            }
        }

        // Method to get multiple items from a named map
        public List<T> getFromMap(String mapName, List<String> keyList) {
            dbLock.readLock().lock();
            try {
                HTreeMap<String, T> map = db.hashMap(mapName)
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(valueSerializer)
                    .createOrOpen();
                List<T> result = new ArrayList<>();
                for (String key : keyList) {
                    result.add(map.get(key));
                }
                return result;
            } finally {
                dbLock.readLock().unlock();
            }
        }

        // Method to put multiple items into a named map
        public <V> void putAllInMap(String mapName, List<String> keyList, List<V> valueList, Serializer<V> serializer) {
            dbLock.writeLock().lock();
            try {
                HTreeMap<String, V> map = db.hashMap(mapName)
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(serializer)
                    .createOrOpen();
                for (int i = 0; i < keyList.size(); i++) {
                    map.put(keyList.get(i), valueList.get(i));
                }
                commit();
            } finally {
                dbLock.writeLock().unlock();
            }
        }

        // Method to remove an item from a named map
        public void removeFromMap(String mapName, String key) {
            dbLock.writeLock().lock();
            try {
                HTreeMap<String, ?> map = db.hashMap(mapName)
                    .keySerializer(Serializer.STRING)
                    .createOrOpen();
                map.remove(key);
                commit();
            } finally {
                dbLock.writeLock().unlock();
            }
        }

        // Method to clear all items from a named map
        public void clearMap(String mapName) {
            dbLock.writeLock().lock();
            try {
                HTreeMap<String, ?> map = db.hashMap(mapName)
                    .keySerializer(Serializer.STRING)
                    .createOrOpen();
                map.clear();
                commit();
            } finally {
                dbLock.writeLock().unlock();
            }
        }

        @Override
        public void clear() {
            dbLock.writeLock().lock();
            try {
                itemMap.clear();
                commit();
            } finally {
                dbLock.writeLock().unlock();
            }
        }


        @Override
        public void putMeta(String key, Object value) {
            dbLock.writeLock().lock();
            try {
                metaMap.put(key, value);
                commit();
            } finally {
                dbLock.writeLock().unlock();
            }
        }

        @Override
        public void removeMeta(String key) {
            dbLock.writeLock().lock();
            try {
                metaMap.remove(key);
                commit();
            } finally {
                dbLock.writeLock().unlock();
            }
        }
    }

    // Method to create a new map by name
    public void createMap(String mapName, Serializer<?> serializer) {
        lock.writeLock().lock();
        try {
            ((MapDBDatabase<T>) localDB).createMap(mapName, serializer);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Method to put an item into a named map
    public <V> void putInMap(String mapName, String key, V value, Serializer<V> serializer) {
        lock.writeLock().lock();
        try {
            ((MapDBDatabase<T>) localDB).putInMap(mapName, key, value, serializer);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Method to get an item from a named map
    public <V> V getFromMap(String mapName, String key, Serializer<V> serializer) {
        lock.readLock().lock();
        try {
            return ((MapDBDatabase<T>) localDB).getFromMap(mapName, key, serializer);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<String> searchString(String part) {
        return localDB.searchString(part);
    }

    // Method to get multiple items from a named map
    public List<T> getFromMap(String mapName, List<String> keyList) {
        lock.readLock().lock();
        try {
            return ((MapDBDatabase<T>) localDB).getFromMap(mapName, keyList);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<String, T> getAllFromMap(String mapName, Serializer<T> serializer) {
        lock.readLock().lock();
        try {
            return ((MapDBDatabase<T>) localDB).getAllFromMap(mapName, serializer);
        } finally {
            lock.readLock().unlock();
        }
    }


    // Method to put multiple items into a named map
    public <V> void putAllInMap(String mapName, List<String> keyList, List<V> valueList, Serializer<V> serializer) {
        lock.writeLock().lock();
        try {
            ((MapDBDatabase<T>) localDB).putAllInMap(mapName, keyList, valueList, serializer);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Method to remove an item from a named map
    public void removeFromMap(String mapName, String key) {
        lock.writeLock().lock();
        try {
            ((MapDBDatabase<T>) localDB).removeFromMap(mapName, key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Method to clear all items from a named map
    public void clearMap(String mapName) {
        lock.writeLock().lock();
        try {
            ((MapDBDatabase<T>) localDB).clearMap(mapName);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Object getMeta(String key) {
        if (localDB == null) return null;
        lock.readLock().lock();
        try {
            return ((MapDBDatabase<T>) localDB).getMeta(key);
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

    public List<T> showItems(String title, @Nullable List<T> itemList, BufferedReader br,boolean isFromEnd,boolean withChoose) {
        // Implement the logic to display webhook requests
        System.out.println("Displaying "+title+"...");
        int count = 0;
        int size;
        if(br!=null){
            size = Inputer.inputInteger(br, "Enter the size of a page: ", 1, 100);
        }else{
            size = Shower.DEFAULT_SIZE;
        }
        int totalPage = localDB.getSize() / size;
        int currentPage = 0;
        String fromId = null;
        List<T> chosenItems = new ArrayList<>();

        if(itemList!=null){
            showItems(title, itemList, count);
            if(withChoose){
                List<Integer> choices = Shower.chooseMulti(br, count, count+itemList.size());
                if(choices.isEmpty()){
                    return new ArrayList<>();
                }
                for(Integer choice : choices){
                    chosenItems.add(itemList.get(choice-1));
                }
            }
            return chosenItems;
        }
        do{
            NavigableMap<String, T> itemMap =  (NavigableMap<String, T>) getItemMap(size, null, fromId, false, null, null, true, isFromEnd);
            Shower.showDataTable(title, List.of(itemMap.values()), count);
            if(withChoose){
                List<Integer> choices = Shower.chooseMulti(br, count, count+size);
                if(choices.isEmpty()){
                    break;
                }
                List<T> items = new ArrayList<>();
                for(Integer choice : choices){
                    items.add(itemMap.get(itemMap.keySet().toArray()[choice-1]));
                }
                chosenItems.addAll(items);
            }
            count += size;
            currentPage = count / size;
            if(currentPage == 0){
                currentPage = totalPage;
            }
            if(Inputer.askIfYes(br, "Do you want to continue?")){
                fromId = itemMap.lastKey();
            }else{
                break;
            }
        }while(currentPage < totalPage);  
        return chosenItems;
    }

    public void showItems(String title, List<T> itemList,int beginFrom){
            Shower.showDataTable(title, itemList,beginFrom);
    }

    // private List<T> chooseItems(List<Collection<T>> of, BufferedReader br) {
    //     List<T> chosenItems = new ArrayList<>();
    //     for (Collection<T> collection : of) {
    //         chosenItems.addAll(collection);
    //     }
    //     return chosenItems;
    // }

    public void menu(BufferedReader br) {
        Menu menu = new Menu(handlerType.toString());

        addBasicMenuItems(br, menu);

        menu.showAndSelect(br);
    }

    protected void addBasicMenuItems(BufferedReader br, Menu menu) {
        menu.add("Show Item List", () -> showItems(handlerType.toString(),null, br, true, false));
        menu.add("Put Item", () -> {
            try {
                System.out.print("Enter ID: ");
                String id = br.readLine().trim();
                System.out.print("Enter Item: ");
                T item = Inputer.createFromUserInput(br, itemClass, null, null);
                put(id, item);
            } catch (IOException | ReflectiveOperationException e) {
                System.out.println("Error: " + e.getMessage());
            }
        });
        menu.add("Get Item", () -> {
            try {
                System.out.print("Enter ID: ");
                String id = br.readLine().trim();
                System.out.println("Item: " + get(id));
            } catch (IOException e) {
                System.out.println("Error: " + e.getMessage());
            }
        });
        menu.add("Put All Items", () -> {
            try {
                Map<String, T> items = new HashMap<>();
                System.out.print("Enter number of items: ");
                int numItems = Integer.parseInt(br.readLine().trim());
                for (int i = 0; i < numItems; i++) {
                    System.out.print("Enter ID: ");
                    String id = br.readLine().trim();
                    System.out.print("Enter Item: ");
                    T item = Inputer.createFromUserInput(br, itemClass, null, null);
                    items.put(id, item);
                }
                putAll(items);
            } catch (IOException e) {
                System.out.println("Error: " + e.getMessage());
            } catch (ReflectiveOperationException e) {
                System.out.println("Error: " + e.getMessage());
            }
        });
        menu.add("Get All Items", () -> System.out.println("All Items: " + getAll()));
        menu.add("Remove Item", () -> {
            try {
                System.out.print("Enter ID to remove: ");
                String id = br.readLine().trim();
                remove(id);
                System.out.println("Item removed.");
            } catch (IOException e) {
                System.out.println("Error: " + e.getMessage());
            }
        });
        menu.add("Clear All Items", () -> {
            if(Inputer.askIfYes(br, "Are you sure you want to clear all items?")){
                clear();
                System.out.println("All items cleared.");
            }
        });
    }

    public void remove(String id) {
        if (localDB == null) return;
        lock.writeLock().lock();
        try {
            localDB.remove(id);
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

    public static void main(String[] args) {
        // Create directory first
        File directory = new File("test_db");
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                System.err.println("Failed to create directory: test_db");
                return;
            }
        }

        // Initialize handler with String serializer and UPDATE_ORDER sort type
        NewHandler<String> handler = new NewHandler<>(
            HandlerType.TEST,
            "test_db",
            LocalDB.SortType.UPDATE_ORDER,
            Serializer.STRING,
            String.class
        );

        try {
            // Test basic put and get
            System.out.println("Testing basic put and get...");
            handler.put("key1", "value1");
            handler.put("key2", "value2");
            System.out.println("Get key1: " + handler.get("key1"));
            System.out.println("Get key2: " + handler.get("key2"));

            // Test putAll and getAll
            System.out.println("\nTesting putAll and getAll...");
            Map<String, String> items = new HashMap<>();
            items.put("key3", "value3");
            items.put("key4", "value4");
            handler.putAll(items);
            System.out.println("All items: " + handler.getAll());

            // Test index-based operations
            System.out.println("\nTesting index operations...");
            NavigableMap<Long, String> indexIdMap = handler.getIndexIdMap();
            NavigableMap<String, Long> idIndexMap = handler.getIdIndexMap();
            System.out.println("Index-ID map: " + indexIdMap);
            System.out.println("ID-Index map: " + idIndexMap);

            // Test getting items by index
            System.out.println("\nTesting getItemByIndex...");
            for (Long index : indexIdMap.keySet()) {
                System.out.println("Item at index " + index + ": " + handler.getItemByIndex(index));
            }

            // Test ordered list retrieval
            System.out.println("\nTesting getItemOrderedList...");
            NavigableMap<String, String> orderedList = handler.getItemMap(
                2,      // size
                null,   // fromIndex
                null,   // fromId
                true,   // isFromInclude
                null,   // toIndex
                null,   // toId
                false   // isFromEnd
                , false
            );
            System.out.println("Ordered list (first 2 items): " + orderedList);

            // Test reverse ordered list
            System.out.println("\nTesting reverse ordered list...");
            NavigableMap<String, String> reverseList = handler.getItemMap(
                2,      // size
                null,   // fromIndex
                null,   // fromId
                true,   // isFromInclude
                null,   // toIndex
                null,   // toId
                true    // isFromEnd
                , false
            );
            System.out.println("Reverse ordered list (last 2 items): " + reverseList);

            // Test searchString
            System.out.println("\nTesting searchString...");
            List<String> searchResults = handler.searchString("value");
            System.out.println("Search results for 'value': " + searchResults);

            searchResults = handler.searchString("1");
            System.out.println("Search results for '1': " + searchResults);

            // Test createMap, putInMap, getFromMap, getAllFromMap, putAllInMap, removeFromMap, clearMap
            System.out.println("\nTesting createMap, putInMap, getFromMap, getAllFromMap, putAllInMap, removeFromMap, clearMap...");
            String mapName = "newMap";
            handler.createMap(mapName, Serializer.STRING);
            handler.putInMap(mapName, "mapKey1", "value1", Serializer.STRING);
            handler.putInMap(mapName, "mapKey2", "value2", Serializer.STRING); 
            System.out.println("Get mapKey1 from newMap: " + handler.getFromMap(mapName, "mapKey1", Serializer.STRING));
            System.out.println("Get mapKey2 from newMap: " + handler.getFromMap(mapName, "mapKey2", Serializer.STRING));
            System.out.println("All items from newMap: " + handler.getAllFromMap(mapName, Serializer.STRING));

            List<String> keyList = new ArrayList<>();
            keyList.add("mapKey1");
            keyList.add("mapKey2");
            List<String> valueList = new ArrayList<>();
            valueList.add("value1");
            valueList.add("value2");
            handler.putAllInMap(mapName, keyList, valueList, Serializer.STRING);
            System.out.println("All items from newMap after putAllInMap: " + handler.getAllFromMap(mapName, Serializer.STRING));

            // Test removeFromMap
            System.out.println("\nTesting removeFromMap...");
            handler.removeFromMap(mapName, "mapKey1");
            System.out.println("Get mapKey1 from newMap after removal: " + handler.getFromMap(mapName, "mapKey1", Serializer.STRING));

            // Test clearMap
            System.out.println("\nTesting clearMap...");
            handler.clearMap(mapName);
            System.out.println("Get mapKey2 from newMap after clearing: " + handler.getFromMap(mapName, "mapKey2", Serializer.STRING));

            // Additional tests for different value types
            System.out.println("\nTesting maps with different value types...");
            String intMapName = "intMap";
            handler.createMap(intMapName, Serializer.INTEGER);
            handler.putInMap(intMapName, "intKey1", 100, Serializer.INTEGER);
            System.out.println("Get intKey1 from intMap: " + handler.getFromMap(intMapName, "intKey1", Serializer.INTEGER));

            String doubleMapName = "doubleMap";
            handler.createMap(doubleMapName, Serializer.DOUBLE);
            handler.putInMap(doubleMapName, "doubleKey1", 99.99, Serializer.DOUBLE);
            System.out.println("Get doubleKey1 from doubleMap: " + handler.getFromMap(doubleMapName, "doubleKey1", Serializer.DOUBLE));

        } catch (Exception e) {
            System.err.println("Error during testing: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up
            handler.close();
            System.out.println("\nHandler closed.");
        }
    }

    public List<T> chooseItems(BufferedReader br) {
        List<T> chosenItems = new ArrayList<>();
        String lastKey = null;
        int totalDisplayed = 0;
        int size = Inputer.inputInteger(br, "Enter the size of a page: ", 1, 0);

        while (true) {
            List<T> currentList = getItemList(size, null, lastKey, false, null, null, true, true);
            if (currentList.isEmpty()) {
                break;
            }

            List<T> result = chooseItemList(currentList, chosenItems, totalDisplayed, br);
            if (result == null) {
                totalDisplayed += currentList.size();
                lastKey = getIdByIndex(totalDisplayed - 1);
                continue;
            } else if (result.contains(null)) {
                result.remove(null);  // Remove the break signal
                break;
            }

            totalDisplayed += currentList.size();
            lastKey = getIdByIndex(totalDisplayed - 1);
        }

        return chosenItems;
    }

    private List<T> chooseItemList(List<T> currentList, List<T> chosenItems, int totalDisplayed, BufferedReader br) {
        String title = "Choose Items";
        Shower.showDataTable(title, currentList, totalDisplayed);

        System.out.println("Enter item numbers to select (comma-separated), 'a' for all. 'q' to quit, or press Enter for more:");
        String input = Inputer.inputString(br);

        if ("".equals(input)) {
            return null;  // Signal to continue to next page
        }

        if (input.equals("q")) {
            chosenItems.add(null);  // Signal to break the loop
            return chosenItems;
        }

        if (input.equals("a")) {
            chosenItems.addAll(currentList);
            chosenItems.add(null);  // Signal to break the loop
            return chosenItems;
        }

        String[] selections = input.split(",");
        for (String selection : selections) {
            try {
                int index = Integer.parseInt(selection.trim()) - 1;
                if (index >= 0 && index < totalDisplayed + currentList.size()) {
                    int listIndex = index - totalDisplayed;
                    chosenItems.add(currentList.get(listIndex));
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input: " + selection);
            }
        }

        return chosenItems;
    }
}
