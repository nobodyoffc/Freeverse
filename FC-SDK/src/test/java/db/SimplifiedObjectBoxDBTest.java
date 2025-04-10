package db;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Simplified test for ObjectBoxDB functionality
 * This implementation doesn't rely on external dependencies
 */
public class SimplifiedObjectBoxDBTest {
    
    public static void main(String[] args) {
        System.out.println("ObjectBoxDB Simplified Test Starting...");
        String testDir = System.getProperty("java.io.tmpdir") + "/objectbox-test";
        
        // Create test directory if it doesn't exist
        File dir = new File(testDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        System.out.println("Using test directory: " + testDir);
        
        // Test a sample mock database
        testDatabase(testDir);
        
        System.out.println("\nAll tests completed!");
    }
    
    private static void testDatabase(String testDir) {
        System.out.println("\n========== Testing Simplified Mock Database ==========");
        
        // Create the mock database
        MockDatabase db = new MockDatabase();
        db.initialize(testDir, "test_db");
        
        try {
            // Test basic operations
            System.out.println("1. Testing basic operations (put/get)");
            TestEntity entity1 = new TestEntity("id1", "Alice", 30);
            TestEntity entity2 = new TestEntity("id2", "Bob", 25);
            TestEntity entity3 = new TestEntity("id3", "Charlie", 35);
            
            // Put and retrieve
            db.put("id1", entity1);
            db.put("id2", entity2);
            db.put("id3", entity3);
            
            System.out.println("   Size after adding 3 entities: " + db.getSize());
            TestEntity retrieved = db.get("id2");
            System.out.println("   Retrieved entity: " + retrieved);
            
            // Test getAll
            System.out.println("\n2. Testing getAll()");
            Map<String, TestEntity> allEntities = db.getAll();
            System.out.println("   All entities count: " + allEntities.size());
            for (Map.Entry<String, TestEntity> entry : allEntities.entrySet()) {
                System.out.println("   " + entry.getKey() + " -> " + entry.getValue());
            }
            
            // Test putAll
            System.out.println("\n3. Testing putAll(Map)");
            Map<String, TestEntity> newEntities = new HashMap<>();
            newEntities.put("id4", new TestEntity("id4", "Dave", 40));
            newEntities.put("id5", new TestEntity("id5", "Eve", 28));
            db.putAll(newEntities);
            
            System.out.println("   Size after putAll: " + db.getSize());
            
            // Test removal
            System.out.println("\n4. Testing remove()");
            db.remove("id3");
            System.out.println("   Size after removal: " + db.getSize());
            System.out.println("   Removed entity exists: " + (db.get("id3") != null));
            
            // Test search
            System.out.println("\n5. Testing searchString()");
            List<TestEntity> searchResults = db.searchString("bob");
            System.out.println("   Search results: " + searchResults.size());
            for (TestEntity entity : searchResults) {
                System.out.println("   " + entity);
            }
            
            // Test clear
            System.out.println("\n6. Testing clear()");
            db.clear();
            System.out.println("   Size after clear: " + db.getSize());
            
        } finally {
            db.close();
            System.out.println("Database closed");
        }
    }
    
    /**
     * A simple entity class for testing
     */
    public static class TestEntity {
        private String id;
        private String name;
        private int age;
        private long timestamp;
        
        public TestEntity() {
            // Default constructor
        }
        
        public TestEntity(String id, String name, int age) {
            this.id = id;
            this.name = name;
            this.age = age;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getId() {
            return id;
        }
        
        public void setId(String id) {
            this.id = id;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public int getAge() {
            return age;
        }
        
        public void setAge(int age) {
            this.age = age;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
        
        @Override
        public String toString() {
            return "TestEntity{" +
                    "id='" + id + '\'' +
                    ", name='" + name + '\'' +
                    ", age=" + age +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
    
    /**
     * A simplified mock database that demonstrates the key concepts
     */
    private static class MockDatabase {
        public enum SortType {
            NO_SORT,      // No sorting, use HashMap
            KEY_ORDER,    // Sorted by key, use TreeMap
            ACCESS_ORDER, // Most recently accessed at end, use LinkedHashMap(access-order=true)
            UPDATE_ORDER, // Most recently updated at end, use custom updating map
            BIRTH_ORDER   // Creation order, use LinkedHashMap(insertion-order)
        }
        
        private SortType sortType = SortType.UPDATE_ORDER;
        private String dbPath;
        private String dbName;
        private boolean isClosed = false;
        
        // Main storage maps similar to ObjectBoxDB
        private final Map<String, TestEntity> itemMap = new ConcurrentHashMap<>();
        private final NavigableMap<Long, String> indexIdMap = new ConcurrentSkipListMap<>();
        private final NavigableMap<String, Long> idIndexMap = new ConcurrentSkipListMap<>();
        private final Map<String, Object> metaMap = new ConcurrentHashMap<>();
        
        public void initialize(String dbPath, String dbName) {
            this.dbPath = dbPath;
            this.dbName = dbName;
            System.out.println("   Initializing database at " + dbPath + "/" + dbName);
        }
        
        public void put(String key, TestEntity value) {
            itemMap.put(key, value);
            
            // Update indices for sorting
            Long existingIndex = idIndexMap.get(key);
            
            if (existingIndex == null) {
                // New item, add at the end
                long newIndex = indexIdMap.isEmpty() ? 0 : indexIdMap.lastKey() + 1;
                indexIdMap.put(newIndex, key);
                idIndexMap.put(key, newIndex);
            } else if (sortType == SortType.UPDATE_ORDER) {
                // Move to the end for update order
                indexIdMap.remove(existingIndex);
                long newIndex = indexIdMap.isEmpty() ? 0 : indexIdMap.lastKey() + 1;
                indexIdMap.put(newIndex, key);
                idIndexMap.put(key, newIndex);
            }
        }
        
        public TestEntity get(String key) {
            TestEntity value = itemMap.get(key);
            
            // Update access order if necessary
            if (value != null && sortType == SortType.ACCESS_ORDER) {
                // Move to the end for access order
                Long existingIndex = idIndexMap.get(key);
                if (existingIndex != null) {
                    indexIdMap.remove(existingIndex);
                    long newIndex = indexIdMap.isEmpty() ? 0 : indexIdMap.lastKey() + 1;
                    indexIdMap.put(newIndex, key);
                    idIndexMap.put(key, newIndex);
                }
            }
            
            return value;
        }
        
        public void remove(String key) {
            TestEntity entity = itemMap.remove(key);
            
            if (entity != null) {
                // Update indices
                Long index = idIndexMap.remove(key);
                if (index != null) {
                    indexIdMap.remove(index);
                }
            }
        }
        
        public void putAll(Map<String, TestEntity> items) {
            for (Map.Entry<String, TestEntity> entry : items.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        }
        
        public Map<String, TestEntity> getAll() {
            return new HashMap<>(itemMap);
        }
        
        public int getSize() {
            return itemMap.size();
        }
        
        public void clear() {
            itemMap.clear();
            indexIdMap.clear();
            idIndexMap.clear();
        }
        
        public void close() {
            this.isClosed = true;
        }
        
        public List<TestEntity> searchString(String searchTerm) {
            if (searchTerm == null || searchTerm.isEmpty()) {
                return new ArrayList<>();
            }
            
            searchTerm = searchTerm.toLowerCase();
            List<TestEntity> result = new ArrayList<>();
            
            for (TestEntity entity : itemMap.values()) {
                if (entity.getName().toLowerCase().contains(searchTerm)) {
                    result.add(entity);
                }
            }
            
            return result;
        }
    }
} 