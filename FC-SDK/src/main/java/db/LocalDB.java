package db;

import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import java.util.*;

public interface LocalDB<T> {
    String DOT_DB = ".db";
    String MAP_NAMES_META_KEY = "map_names";
    String LOCAL_REMOVED_MAP = "local_removed";
    String ON_CHAIN_DELETED_MAP = "on_chain_deleted";

    enum SortType {
        NO_SORT,      // No sorting, use HashMap
        KEY_ORDER,    // Sorted by key, use TreeMap
        ACCESS_ORDER, // Most recently accessed at end, use LinkedHashMap(access-order=true)
        UPDATE_ORDER, // Most recently updated at end, use custom updating map
        BIRTH_ORDER   // Creation order, use LinkedHashMap(insertion-order)
    }

    void initialize(String dbPath, String dbName);

    SortType getSortType();

    void put(String key, T value);

    T get(String key);

    List<T> get(List<String> keys);

    void remove(String key);

    void commit();

    void close();

    boolean isClosed();

    long getTempIndex();

    String getTempId();

    // Map access methods
    HTreeMap<String, T> getItemMap();

    NavigableMap<Long, String> getIndexIdMap();

    NavigableMap<String, Long> getIdIndexMap();

    HTreeMap<String, Object> getMetaMap();

    // Add these new methods
    Long getIndexById(String id);

    String getIdByIndex(long index);

    int getSize();

    Object getMeta(String key);

    // Rename existing method to getItemMap
    LinkedHashMap<String, T> getMap(Integer size, String fromId, Long fromIndex,
                                    boolean isFromInclude, String toId, Long toIndex, boolean isToInclude, boolean isFromEnd);

    // Add new method that returns List
    List<T> getList(Integer size, String fromId, Long fromIndex,
                    boolean isFromInclude, String toId, Long toIndex, boolean isToInclude, boolean isFromEnd);

    // Add these new methods
    void putAll(Map<String, T> items);

    Map<String, T> getAll();

    List<T> searchString(String part);

    // Add these new methods
    void putMeta(String key, Object value);

    void removeMeta(String key);

    void clear();

    // Add new method to remove multiple items
    void removeList(List<String> ids);

    void removeFromMap(String mapName, String key);
    void removeFromMap(String mapName, List<String> keys);

    void clearDB();  // Add this new method to the interface

    void putAll(List<T> items, String idField);


    Set<String> getMapNames();
    /**
     * Puts a value into a named map with the specified serializer
     * @param mapName The name of the map to store the value in
     * @param key The key to store the value under
     * @param value The value to store
     * @param serializer The serializer to use for the value
     * @param <V> The type of the value
     */
    <V> void putInMap(String mapName, String key, V value, Serializer<V> serializer);

    /**
     * Gets a value from a named map using the specified serializer
     * @param mapName The name of the map to retrieve from
     * @param key The key to retrieve
     * @param serializer The serializer to use for the value
     * @param <V> The type of the value
     * @return The value associated with the key, or null if not found
     */
    <V> V getFromMap(String mapName, String key, Serializer<V> serializer);

    /**
     * Gets all values from a named map using the specified serializer
     * @param mapName The name of the map to retrieve from
     * @param serializer The serializer to use for the values
     * @param <V> The type of the values
     * @return A Map containing all key-value pairs in the named map
     */
    <V> Map<String, V> getAllFromMap(String mapName, Serializer<V> serializer);

    void clearMap(String mapName);
    <V> List<V> getFromMap(String mapName, List<String> keyList, Serializer<V> serializer);
    <V> void putAllInMap(String mapName, List<String> keyList, List<V> valueList, Serializer<V> serializer);
}
