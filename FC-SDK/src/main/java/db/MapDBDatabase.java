package db;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import fcData.FcEntity;
import handlers.Handler;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import tools.FileTools;
import tools.ObjectTools;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.stream.Collectors;

public class MapDBDatabase <T> implements LocalDB<T> {
    private static final String SORT_TYPE_META_KEY = "sort_type";
    private final Handler<T> handler;
    private volatile DB db;
    private volatile HTreeMap<String, T> itemMap;
    private volatile ConcurrentNavigableMap<Long, String> indexIdMap;
    private volatile ConcurrentNavigableMap<String, Long> idIndexMap;
    private volatile HTreeMap<String, Object> metaMap;
    private final Serializer<T> valueSerializer;
    private SortType sortType;

    // Add a map to store references to named maps
    private final ConcurrentHashMap<String, HTreeMap<String, ?>> namedMaps = new ConcurrentHashMap<>();

    public MapDBDatabase(Handler<T> handler, Serializer<T> valueSerializer, SortType sortType) {
        this.handler = handler;
        this.valueSerializer = valueSerializer;
        this.sortType = sortType;
    }

    @Override
    public SortType getSortType() {
        return sortType;
    }

    @Override
    public void initialize(String dbPath, String dbName) {
        if (db == null || db.isClosed()) {
            String fileName = FileTools.makeFileName(handler.getMainFid(), handler.getSid(), dbName.toLowerCase(), LocalDB.DOT_DB);
            String path = Path.of(dbPath, fileName).toString();
            System.out.println("Initializing MapDBDatabase with path: " + path);
            db = DBMaker.fileDB(path)
                    .fileMmapEnable()
                    .checksumHeaderBypass()
                    .transactionEnable()
                    .make();

            itemMap = db.hashMap("items_" + dbName)
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(valueSerializer)
                    .createOrOpen();

            metaMap = db.hashMap("meta_" + dbName)
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(new FcEntity.GsonSerializer())
                    .createOrOpen();

            // Read sort type from meta map or use the provided one
            String storedSortType = (String) metaMap.get(SORT_TYPE_META_KEY);
            if (storedSortType != null) {
                this.sortType = SortType.valueOf(storedSortType);
            } else {
                metaMap.put(SORT_TYPE_META_KEY, this.sortType.name());
            }

            // Only initialize index maps if sorting is enabled
            if (this.sortType != SortType.NO_SORT) {
                indexIdMap = db.treeMap("indexId_" + dbName)
                        .keySerializer(Serializer.LONG)
                        .valueSerializer(Serializer.STRING)
                        .createOrOpen();

                idIndexMap = db.treeMap("idIndex_" + dbName)
                        .keySerializer(Serializer.STRING)
                        .valueSerializer(Serializer.LONG)
                        .createOrOpen();

                if(sortType != SortType.NO_SORT){
                    if(itemMap.size() != indexIdMap.size() || itemMap.size() != idIndexMap.size()){
                        reIndex(null, null);
                    }
                }
            }

            // Initialize all existing named maps
            @SuppressWarnings("unchecked")
            Set<String> mapNames = (Set<String>) metaMap.getOrDefault(LocalDB.MAP_NAMES_META_KEY, new HashSet<String>());
            for (String mapName : mapNames) {
                // Note: We'll need to store serializer information in meta to properly recreate maps
                Object serializerInfo = metaMap.get(mapName + "_serializer");
                if (serializerInfo != null) {
                    Serializer<?> serializer = getSerializerFromInfo(serializerInfo);
                    HTreeMap<String, ?> map = db.hashMap(mapName)
                            .keySerializer(Serializer.STRING)
                            .valueSerializer(serializer)
                            .createOrOpen();
                    namedMaps.put(mapName, map);
                }
            }
        }
    }

    @Override
    public void put(String key, T value) {
        itemMap.put(key, value);
        updateIndex(key);
        commit();
    }

    private void updateAccessOrder(String id) {
        if (sortType == SortType.ACCESS_ORDER) {
            // Move the accessed item to the end by updating its index
            Long existingIndex = idIndexMap.get(id);
            if (existingIndex != null) {
                // Remove from current position
                indexIdMap.remove(existingIndex);
                idIndexMap.remove(id);

                // Add at the end with next available index
                long newIndex = indexIdMap.isEmpty() ? 1 : indexIdMap.lastKey() + 1;
                indexIdMap.put(newIndex, id);
                idIndexMap.put(id, newIndex);
            }
        }
    }

    @Override
    public T get(String key) {
        T value = itemMap.get(key);
        if (value != null) {
            updateAccessOrder(key);
            commit();
        }
        return value;
    }

    @Override
    public List<T> get(List<String> keys) {
        List<T> values = new ArrayList<>();
        for (String key : keys) {
            T value = itemMap.get(key);
            if (value != null) {
                updateAccessOrder(key);
                values.add(value);
            }
        }
        commit();

        return values;
    }

    @Override
    public void remove(String key) {
        if (sortType != SortType.NO_SORT) {
            Long index = idIndexMap.get(key);
            if (index != null) {
                // Remove from index maps
                indexIdMap.remove(index);
                idIndexMap.remove(key);

                shiftHigherIndicesDown1(index);
            }
        }
        itemMap.remove(key);
        // Add removal tracking
        putInMap(LocalDB.LOCAL_REMOVED_MAP, key, System.currentTimeMillis(), Serializer.LONG);
        commit();
    }

    private void shiftHigherIndicesDown1(Long index) {
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
    }

    @Override
    public void commit() {
        // Remove currentIndex persistence since we'll initialize it from existing indices
        db.commit();
    }

    @Override
    public void close() {
        if (db != null && !db.isClosed()) {
            // Add commit here before closing
            db.commit();

            namedMaps.clear();
            db.close();
            db = null;
            itemMap = null;
            indexIdMap = null;
            idIndexMap = null;
            metaMap = null;
        }
    }

    @Override
    public boolean isClosed() {
        return db == null || db.isClosed();
    }

    @Override
    public HTreeMap<String, T> getItemMap() {
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
        return metaMap.get(key);
    }

    @Override
    public LinkedHashMap<String, T> getMap(Integer size, String fromId, Long fromIndex,
                                           boolean isFromInclude, String toId, Long toIndex, boolean isToInclude, boolean isFromEnd) {
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
            
            T item = itemMap.get(lastId);
            if (item != null) {
                result.put(lastId, item);
                updateAccessOrder(lastId);
            }
        }

        // Store the appropriate index for next pagination
        if (lastIndex != null) tempIndex = lastIndex;

        
        // Store the last processed ID
        tempId = lastId;
        
        // Commit changes if we updated any indices
        if (sortType == SortType.ACCESS_ORDER && !result.isEmpty()) {
            commit();
        }
        
        return result;
    }

    private long tempIndex;
    private String tempId;

    @Override
    public long getTempIndex() {
        return tempIndex;
    }

    @Override
    public String getTempId() {
        return tempId;
    }

    @Override
    public List<T> getList(Integer size, String fromId, Long fromIndex,
                           boolean isFromInclude, String toId, Long toIndex, boolean isToInclude, boolean isFromEnd) {
        return new ArrayList<>(getMap(size, fromId, fromIndex, isFromInclude, toId, toIndex, isToInclude, isFromEnd).values());
    }

    @Override
    public void putAll(Map<String, T> items) {
        if (items == null || items.isEmpty()) return;

        if (sortType == SortType.KEY_ORDER) {
            // Create a sorted map based on natural key ordering
            TreeMap<String, T> sortedItems = new TreeMap<>(String::compareTo);
            sortedItems.putAll(items);

            // First, add all items to the main map
            itemMap.putAll(sortedItems);

            // Then update indices for all items in order
            for (String key : sortedItems.keySet()) {
                updateIndex(key);
            }
        } else {
            // For UPDATE_ORDER and BIRTH_ORDER
            itemMap.putAll(items);
            for (String key : items.keySet()) {
                updateIndex(key);
            }
        }
        commit();
    }

    @Override
    public Map<String, T> getAll() {
        return new HashMap<>(itemMap);
    }

    private void updateIndex(String id) {
        // Skip index updates if sorting is disabled
        if (sortType == SortType.NO_SORT) {
            return;
        }

        Long existingIndex = idIndexMap.get(id);

        switch (sortType) {
            case NO_SORT -> {
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
                    NavigableMap<Long, String> entriesToShift = indexIdMap.tailMap(insertIndex, false);
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

            case UPDATE_ORDER,ACCESS_ORDER -> {
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

    // Method to create a new map by name
    public void createMap(String mapName, Serializer<?> serializer) {
        if (!namedMaps.containsKey(mapName)) {
            HTreeMap<String, ?> map = db.hashMap(mapName)
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(serializer)
                    .createOrOpen();
            namedMaps.put(mapName, map);

            // Store map name and serializer info in meta
            @SuppressWarnings("unchecked")
            Set<String> mapNames = (Set<String>) metaMap.getOrDefault(LocalDB.MAP_NAMES_META_KEY, new HashSet<String>());
            mapNames.add(mapName);
            metaMap.put(LocalDB.MAP_NAMES_META_KEY, mapNames);
            metaMap.put(mapName + "_serializer", getSerializerInfo(serializer));
            commit();
        }
    }

    // Helper method to get or create a map
    @SuppressWarnings("unchecked")
    private <V> HTreeMap<String, V> getOrCreateMap(String mapName, Serializer<V> serializer) {
        HTreeMap<String, V> map = (HTreeMap<String, V>) namedMaps.get(mapName);
        if (map == null) {
            createMap(mapName, serializer);
            map = (HTreeMap<String, V>) namedMaps.get(mapName);
        }
        return map;
    }

    // Method to put an item into a named map
    @Override
    public <V> void putInMap(String mapName, String key, V value, Serializer<V> serializer) {
        HTreeMap<String, V> map = getOrCreateMap(mapName, serializer);
        map.put(key, value);
        commit();
    }

    // Method to get an item from a named map
    @Override
    public <V> V getFromMap(String mapName, String key, Serializer<V> serializer) {
        HTreeMap<String, V> map = getOrCreateMap(mapName, serializer);
        return map.get(key);
    }

    // Method to get all items from a named map
    @Override
    public <V> Map<String, V> getAllFromMap(String mapName, Serializer<V> serializer) {
        HTreeMap<String, V> map = (HTreeMap<String, V>) namedMaps.get(mapName);
        if(map==null) return null;
        return map.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public List<T> searchString(String part) {
        List<T> matchings = new ArrayList<>();
        for (Map.Entry<String, T> entry : itemMap.entrySet()) {
            String key = entry.getKey();
            T value = entry.getValue();

            if (value instanceof String) {
                if (((String) value).contains(part)) {
                    matchings.add(value);
                }
            } else {
                try {
                    Gson gson = new Gson();
                    String json = gson.toJson(value);
                    Type mapType = new TypeToken<Map<String, Object>>() {
                    }.getType();
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
                        matchings.add(value);
                    }
                } catch (JsonSyntaxException e) {
                    // Handle the case where the value is not a JSON object
                    System.err.println("Error parsing JSON for key: " + key + " - " + e.getMessage());
                }
            }
        }
        return matchings;
    }

    // Method to get multiple items from a named map
    public <V> List<V> getFromMap(String mapName, List<String> keyList,Serializer<V> serializer) {
        HTreeMap<String, V> map = getOrCreateMap(mapName, serializer);
        List<V> result = new ArrayList<>();
        for (String key : keyList) {
            result.add(map.get(key));
        }
        return result;
    }

    // Method to put multiple items into a named map
    @Override
    public <V> void putAllInMap(String mapName, List<String> keyList, List<V> valueList, Serializer<V> serializer) {
        HTreeMap<String, V> map = getOrCreateMap(mapName, serializer);

        for (int i = 0; i < keyList.size(); i++) {
            if (valueList != null && i < valueList.size()) {
                map.put(keyList.get(i), valueList.get(i));
            } else {
                map.put(keyList.get(i), null);
            }
        }
        commit();
    }

    // Method to remove an item from a named map
    public void removeFromMap(String mapName, String key) {
        HTreeMap<String, ?> map = getOrCreateMap(mapName, null);
        map.remove(key);
        commit();
    }

    public void removeFromMap(String mapName, List<String> keys) {
        HTreeMap<String, ?> map = getOrCreateMap(mapName, null);
        for(String key:keys){
            map.remove(key);
        }
        commit();
    }

    // Method to clear all items from a named map
    @Override
    public void clearMap(String mapName) {
        HTreeMap<String, ?> map = getOrCreateMap(mapName, null);
        map.clear();
        commit();
    }

    @Override
    public void clear() {
        itemMap.clear();
        if (sortType != SortType.NO_SORT) {
            indexIdMap.clear();
            idIndexMap.clear();
        }
        commit();
    }

    @Override
    public void putMeta(String key, Object value) {
        metaMap.put(key, value);
        commit();
    }

    @Override
    public void removeMeta(String key) {
        metaMap.remove(key);
        commit();
    }

    public void removeList(List<String> ids) {
        if (sortType == SortType.KEY_ORDER) {
            // Sort ids to remove them in order
            List<Long> indicesToRemove = new ArrayList<>();
            for (String key : ids) {
                Long index = idIndexMap.get(key);
                if (index != null) {
                    indicesToRemove.add(index);
                }
            }

            Collections.sort(indicesToRemove);

            // Remove items and update indices
            for (Long indexToRemove : indicesToRemove) {
                String key = indexIdMap.get(indexToRemove);
                if (key != null) {
                    // Remove the item
                    itemMap.remove(key);
                    indexIdMap.remove(indexToRemove);
                    idIndexMap.remove(key);
                    // Add removal tracking
                    putInMap(LocalDB.LOCAL_REMOVED_MAP, key, System.currentTimeMillis(), Serializer.LONG);

                    // Shift all higher indices down by 1
                    shiftHigherIndicesDown1(indexToRemove);
                }
            }
        } else {
            // For UPDATE_ORDER and BIRTH_ORDER
            for (String key : ids) {
                Long index = idIndexMap.get(key);
                if (index != null) {
                    indexIdMap.remove(index);
                    idIndexMap.remove(key);
                    shiftHigherIndicesDown1(index);
                }
                itemMap.remove(key);
                // Add removal tracking
                putInMap(LocalDB.LOCAL_REMOVED_MAP, key, System.currentTimeMillis(), Serializer.LONG);
            }
        }
        commit();
    }

    @Override
    public Set<String> getMapNames() {
        @SuppressWarnings("unchecked")
        Set<String> mapNames = (Set<String>) metaMap.getOrDefault(LocalDB.MAP_NAMES_META_KEY, new HashSet<String>());
        return mapNames;
    }

    // Helper method to store serializer information
    public String getSerializerInfo(Serializer<?> serializer) {
        if (serializer == Serializer.STRING) {
            return "STRING";
        } else if (serializer == Serializer.INTEGER) {
            return "INTEGER";
        } else if (serializer == Serializer.LONG) {
            return "LONG";
        } else if (serializer == Serializer.DOUBLE) {
            return "DOUBLE";
        } else if (serializer == Serializer.BOOLEAN) {
            return "BOOLEAN";
        } else if (serializer == Serializer.BYTE_ARRAY) {
            return "BYTE_ARRAY";
        } else if (serializer == Serializer.JAVA) {
            return "JAVA";
        } else if (serializer == valueSerializer) {
            return "VALUE_SERIALIZER";
        } else if (serializer instanceof FcEntity.FcEntitySerializer) {
            return "FC_ENTITY:" + ((FcEntity.FcEntitySerializer<?>) serializer).getEntityClass().getName();
        }
        throw new IllegalArgumentException("Unsupported serializer type: " + serializer.getClass().getName());
    }

    // Helper method to recreate serializer from stored information
    private Serializer<?> getSerializerFromInfo(Object serializerInfo) {
        if (!(serializerInfo instanceof String info)) {
            throw new IllegalArgumentException("Serializer info must be a string");
        }

        switch (info) {
            case "STRING" -> {
                return Serializer.STRING;
            }
            case "INTEGER" -> {
                return Serializer.INTEGER;
            }
            case "LONG" -> {
                return Serializer.LONG;
            }
            case "DOUBLE" -> {
                return Serializer.DOUBLE;
            }
            case "BOOLEAN" -> {
                return Serializer.BOOLEAN;
            }
            case "BYTE_ARRAY" -> {
                return Serializer.BYTE_ARRAY;
            }
            case "VALUE_SERIALIZER" -> {
                return valueSerializer;
            }
            default -> {
                if (info.startsWith("FC_ENTITY:")) {
                    try {
                        String className = info.substring("FC_ENTITY:".length());
                        Class<?> loadedClass = Class.forName(className);

                        // Verify the loaded class is actually a subclass of FcEntity
                        if (!FcEntity.class.isAssignableFrom(loadedClass)) {
                            throw new IllegalArgumentException("Class " + className + " is not a subclass of FcEntity");
                        }

                        @SuppressWarnings("unchecked")
                        Class<? extends FcEntity> entityClass = (Class<? extends FcEntity>) loadedClass;
                        return FcEntity.getMapDBSerializer(entityClass);
                    } catch (ClassNotFoundException e) {
                        throw new IllegalArgumentException("Cannot find FC entity class: " + info, e);
                    }
                } else {
                    return new FcEntity.GsonSerializer();
                }
            }
        }
    }

    public void reIndex(String updateOrderField, String birthOrderField) {
        // Skip if sorting is disabled
        if (sortType == SortType.NO_SORT) {
            return;
        }

        // Check if sizes match
        int itemSize = itemMap.size();
        int indexIdSize = indexIdMap.size();
        int idIndexSize = idIndexMap.size();

        if (itemSize == indexIdSize && itemSize == idIndexSize) {
            return; // All sizes match, no need to reindex
        }

        // Clear existing indices
        indexIdMap.clear();
        idIndexMap.clear();

        // Create a list of entries to sort
        List<Map.Entry<String, T>> entries = new ArrayList<>(itemMap.entrySet());

        // Sort entries based on sort type
        switch (sortType) {
                        case KEY_ORDER -> entries.sort(Map.Entry.comparingByKey());
                        case UPDATE_ORDER,ACCESS_ORDER -> {
                            if (updateOrderField != null) {
                                entries.sort((e1, e2) -> {
                                    Long h1 = getFieldValue(e1.getValue(), updateOrderField);
                                    Long h2 = getFieldValue(e2.getValue(), updateOrderField);
                                    return compareHeights(h1, h2);
                                });
                            }
                        }
                        case BIRTH_ORDER -> {
                            if (birthOrderField != null) {
                                entries.sort((e1, e2) -> {
                                    Long h1 = getFieldValue(e1.getValue(), birthOrderField);
                                    Long h2 = getFieldValue(e2.getValue(), birthOrderField);
                                    return compareHeights(h1, h2);
                                });
                            }
                        }
                        default -> throw new IllegalArgumentException("Unexpected value: " + sortType);
        }

        // Rebuild indices
        long index = 1;
        for (Map.Entry<String, T> entry : entries) {
            String key = entry.getKey();
            indexIdMap.put(index, key);
            idIndexMap.put(key, index);
            index++;
        }

        commit();
    }

    private Long getFieldValue(T obj, String fieldName) {
        try {
            return (Long) obj.getClass().getMethod(fieldName).invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private int compareHeights(Long h1, Long h2) {
        if (h1 == null && h2 == null) return 0;
        if (h1 == null) return -1;
        if (h2 == null) return 1;
        return h1.compareTo(h2);
    }

    @Override
    public void clearDB() {
        // Clear all maps
        itemMap.clear();
        if (sortType != SortType.NO_SORT) {
            indexIdMap.clear();
            idIndexMap.clear();
        }

        // Clear all named maps
        for (HTreeMap<String, ?> map : namedMaps.values()) {
            map.clear();
        }
        namedMaps.clear();

        // Clear meta map
        metaMap.clear();

        // Commit changes
        commit();
    }

    @Override
    public void putAll(List<T> items, String idField) {
        if (items == null || items.isEmpty()) return;

        // Create a map of id -> item
        Map<String, T> itemMap = new HashMap<>();
        for (T item : items) {
            try {
                String id = ObjectTools.getValueByFieldName(item, idField);
                itemMap.put(id, item);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to get ID from field: " + idField 
                    + ". Make sure the field has a getter method or direct access method.", e);
            }
        }

        // Add all items to the main map
        this.itemMap.putAll(itemMap);

        if (sortType != SortType.NO_SORT) {
            // Clear existing indices for these items
            for (String id : itemMap.keySet()) {
                Long existingIndex = idIndexMap.get(id);
                if (existingIndex != null) {
                    indexIdMap.remove(existingIndex);
                    idIndexMap.remove(id);
                }
            }

            // Add new indices maintaining list order
            long startIndex = indexIdMap.isEmpty() ? 1 : indexIdMap.lastKey() + 1;
            for (int i = 0; i < items.size(); i++) {
                T item = items.get(i);
                try {
                    String id = ObjectTools.getValueByFieldName(item, idField);
                    long newIndex = startIndex + i;
                    indexIdMap.put(newIndex, id);
                    idIndexMap.put(id, newIndex);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to get ID from field: " + idField 
                        + ". Make sure the field has a getter method or direct access method.", e);
                }
            }
        }
        
        commit();
    }
}
