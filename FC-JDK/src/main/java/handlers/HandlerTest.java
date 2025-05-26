package handlers;

import ui.Menu;
import config.Settings;
import config.Starter;
import db.LocalDB;
import db.LevelDB;  // Import the new LevelDB class
import data.fcData.FcEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

public class HandlerTest {
    private static final Logger log = LoggerFactory.getLogger(HandlerTest.class);
    
    public static void main(String[] args) {
        // Configure to use LevelDB for testing
        String name = "Handler LevelDB test";
        Menu.welcome(name);

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        Map<String,Object> settingMap = new HashMap<>();
        Settings settings = Starter.startTool(name, settingMap, br, null, null);
        if (settings == null) return;
        try {
            // Test with different sort types
            testHandler(settings, LocalDB.SortType.NO_SORT);
            testHandler(settings, LocalDB.SortType.KEY_ORDER);
            testHandler(settings, LocalDB.SortType.ACCESS_ORDER);
            testHandler(settings, LocalDB.SortType.UPDATE_ORDER);
            testHandler(settings, LocalDB.SortType.BIRTH_ORDER);

            System.out.println("All tests completed successfully!");

        } catch (Exception e) {
            System.err.println("Test failed with exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testHandler(Settings settings, LocalDB.SortType sortType) {
        System.out.println("\nTesting Handler with " + sortType + " sort type using LevelDB...");

        Handler<TestEntity> handler = createHandlerWithLevelDB(
            settings,
            Handler.HandlerType.TEST,
            sortType,
            TestEntity.class
        );

        try {
            // Test basic operations with timeout protection
            System.out.println("\n1. Testing basic put/get operations");
            TestEntity entity1 = new TestEntity("Test1", "Description1");
            
            // Add timeout protection for potentially problematic operations
            Thread operationThread = new Thread(() -> {
                try {
                    handler.localDB.put("1", entity1);
                    TestEntity retrieved = handler.localDB.get("1");
                    System.out.println("Retrieved entity: " + retrieved);
                } catch (Exception e) {
                    System.err.println("Error during put/get operations: " + e.getMessage());
                }
            });
            
            operationThread.start();
            try {
                operationThread.join(5000); // Wait up to 5 seconds
            } catch (InterruptedException e) {
                System.err.println("Operation was interrupted: " + e.getMessage());
                operationThread.interrupt();
                return;
            }
            
            if (operationThread.isAlive()) {
                System.err.println("Operation timed out - interrupting");
                operationThread.interrupt();
                throw new RuntimeException("Operation timed out");
            }

            // Test putAll and getAll with timeout protection
            System.out.println("\n2. Testing putAll/getAll operations");
            Map<String, TestEntity> testData = new HashMap<>();
            testData.put("2", new TestEntity("Test2", "Description2"));
            testData.put("3", new TestEntity("Test3", "Description3"));
            
            Thread putAllThread = new Thread(() -> {
                try {
                    handler.localDB.putAll(testData);
                    Map<String, TestEntity> allEntities = handler.localDB.getAll();
                    System.out.println("All entities: " + allEntities);
                } catch (Exception e) {
                    System.err.println("Error during putAll/getAll operations: " + e.getMessage());
                }
            });
            
            putAllThread.start();
            try {
                putAllThread.join(5000); // Wait up to 5 seconds
            } catch (InterruptedException e) {
                System.err.println("PutAll operation was interrupted: " + e.getMessage());
                putAllThread.interrupt();
                return;
            }
            
            if (putAllThread.isAlive()) {
                System.err.println("PutAll operation timed out - interrupting");
                putAllThread.interrupt();
                throw new RuntimeException("PutAll operation timed out");
            }

            // Test search functionality
            System.out.println("\n3. Testing search functionality");
            List<TestEntity> searchResults = handler.searchInValue("Test1");
            System.out.println("Search results for 'Test1': " + searchResults);

            // Test map operations
            System.out.println("\n4. Testing map operations");
            handler.createMap("test_map");
            handler.localDB.putInMap("test_map", "key1", "value1");
            String mapValue = handler.localDB.getFromMap("test_map", "key1");
            System.out.println("Retrieved map value: " + mapValue);

            // Test list operations
            System.out.println("\n5. Testing list operations");
            try {
                List<TestEntity> itemList = handler.localDB.getList(10, null, null, false, null, null, true, false);
                if (itemList != null && !itemList.isEmpty()) {
                    System.out.println("Retrieved items: " + itemList);
                } else {
                    System.out.println("No items retrieved from list operation");
                }
            } catch (Exception e) {
                System.err.println("Error during list operations: " + e.getMessage());
                e.printStackTrace();
            }

            // Test removal operations
            System.out.println("\n6. Testing removal operations");
            handler.remove("1");
            TestEntity removedEntity = handler.localDB.get("1");
            System.out.println("Entity after removal: " + removedEntity);

            // Test metadata operations
            System.out.println("\n7. Testing metadata operations");
            handler.localDB.putState("test_meta", "meta_value");
            Object metaValue = handler.localDB.getState("test_meta");
            System.out.println("Retrieved metadata: " + metaValue);

            // Test index operations (if applicable)
            if (sortType != LocalDB.SortType.NO_SORT) {
                System.out.println("\n8. Testing index operations");
                Long index = handler.localDB.getIndexById("2");
                String id = handler.localDB.getIdByIndex(index);
                System.out.println("Index for id '2': " + index);
                System.out.println("Id for index " + index + ": " + id);
            }

            // Test removal tracking
            System.out.println("\n9. Testing removal tracking");
            List<String> idsToRemove = Arrays.asList("2", "3");
            handler.markAsLocallyRemoved(idsToRemove);
            handler.markAsOnChainDeleted(idsToRemove);

            // Clean up
            System.out.println("\n10. Testing cleanup operations");
            handler.clearDB();
            Map<String, TestEntity> afterClear = handler.localDB.getAll();
            System.out.println("Entities after clear: " + afterClear);

        } finally {
            handler.close();
            System.out.println("\nHandler test with " + sortType + " completed.");
        }
    }
    
    /**
     * Create a Handler with LevelDB backing store
     */
    private static <T extends FcEntity> Handler<T> createHandlerWithLevelDB(
            Settings settings,
            Handler.HandlerType handlerType,
            LocalDB.SortType sortType,
            Class<T> itemClass) {
            
        // Create a custom Handler constructor that uses LevelDB
        return new Handler<T>(settings, handlerType, sortType, itemClass, true, true) {
            @Override
            protected void initializeDB(String fid, String sid, String dbPath, String dbName, 
                                        LocalDB.SortType sortType,
                                        Class<T> entityClass) {
                // Override to use LevelDB instead of EasyDB
                this.localDB = new LevelDB<>(sortType, entityClass);
                this.localDB.initialize(fid, sid, dbPath, dbName);
            }
        };
    }
} 