package handlers;

import fcData.Room;
import appTools.Inputer;
import appTools.Menu;
import appTools.Settings;
import constants.FieldNames;
import tools.JsonTools;
import tools.PersistentSequenceMap;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;
import java.util.Collections;

public class RoomHandler extends Handler {
    // 1. Constants
    public static final int DEFAULT_SIZE = 50;
    private static final int CACHE_SIZE = 1000; // Configurable cache size

    // 2. Instance Variables
    private final BufferedReader br;
    private final String myFid;
    private final PersistentSequenceMap roomDB;
    private final ConcurrentHashMap<String, Room> roomCache;

    // 3. Constructor
    public RoomHandler(String myFid, BufferedReader br,String dbPath) {
        this.myFid = myFid;
        this.br = br;
        this.roomDB = new PersistentSequenceMap(myFid, null, FieldNames.ROOM,dbPath);
        this.roomCache = new ConcurrentHashMap<>(CACHE_SIZE);
    }

    public RoomHandler(Settings settings){
        this.myFid = settings.getMainFid();
        this.br = settings.getBr(); 
        this.roomDB = new PersistentSequenceMap(myFid, null, FieldNames.ROOM,settings.getDbDir());
        this.roomCache = new ConcurrentHashMap<>(CACHE_SIZE);
    }   

    // 4. Public Methods - Main Interface
    public void menu() {
        Menu menu = new Menu("Room", this::close);
        menu.add("List", () -> handleListRooms(br));
        menu.add("Create", () -> handleCreateRoom(br));
        menu.add("Join", () -> handleJoinRoom(br));
        menu.add("Leave", () -> handleLeaveRooms(br));
        menu.showAndSelect(br);
    }

    // 5. Private Methods - Menu Handlers
    private void handleListRooms(BufferedReader br) {
        List<Room> rooms = pullLocalRoomList(true, br);
        if (rooms == null || rooms.isEmpty()) return;
        opRoomList(rooms, br);
    }

    private void handleCreateRoom(BufferedReader br) {
        String name = Inputer.inputString(br, "Input the room name:");
        if (!Inputer.askIfYes(br, "Create room with name: " + name + "?")) return;
        
        Room room = new Room();
        room.setName(name);
        room.setOwner(myFid);
        room.setRoomId(generateRoomId());
        room.setBirthTime(System.currentTimeMillis());
        room.setMembers(new ArrayList<>(List.of(myFid)));
        room.setMemberNum(1L);

        roomDB.put(room.getRoomId().getBytes(), room.toJson().getBytes());
        addToCache(room); // Add new room to cache
        System.out.println("Room created successfully: " + room.getRoomId());
    }

    private void handleJoinRoom(BufferedReader br) {
        String roomId = Inputer.inputString(br, "Input the room ID to join:");
        Room room = getRoomById(roomId);
        if (room == null) {
            System.out.println("Room not found.");
            return;
        }
        
        if (room.getMembers().contains(myFid)) {
            System.out.println("You are already a member of this room.");
            return;
        }

        List<String> updatedMembers = new ArrayList<>(room.getMembers());
        updatedMembers.add(myFid);
        room.setMembers(updatedMembers);
        room.setMemberNum(room.getMemberNum() + 1);
        
        roomDB.put(room.getRoomId().getBytes(), room.toJson().getBytes());
        addToCache(room); // Update cache with modified room
        System.out.println("Successfully joined room: " + room.getName());
    }

    private void handleLeaveRooms(BufferedReader br) {
        List<Room> rooms = pullLocalRoomList(true, br);
        if (rooms == null || rooms.isEmpty()) return;
        
        for (Room room : rooms) {
            if (room.getOwner().equals(myFid)) {
                System.out.println("Cannot leave room '" + room.getName() + "' - you are the owner");
                continue;
            }
            
            List<String> updatedMembers = new ArrayList<>(room.getMembers());
            updatedMembers.remove(myFid);
            room.setMembers(updatedMembers);
            room.setMemberNum(room.getMemberNum() - 1);
            
            roomDB.put(room.getRoomId().getBytes(), room.toJson().getBytes());
            addToCache(room); // Update cache with modified room
            System.out.println("Left room: " + room.getName());
        }
    }

    // 6. Utility Methods
    private String generateRoomId() {
        return myFid + "_" + System.currentTimeMillis();
    }

    public Room getRoomById(String roomId) {
        // First try to get from cache
        Room room = getFromCache(roomId);
        if (room != null) {
            return room;
        }
        
        // If not in cache, get from persistent storage
        byte[] roomBytes = roomDB.get(roomId.getBytes());
        if (roomBytes == null) return null;
        
        room = Room.fromJson(new String(roomBytes));
        if (room != null) {
            addToCache(room); // Add to cache for future use
        }
        return room;
    }

    private List<Room> pullLocalRoomList(boolean choose, BufferedReader br) {
        List<Room> resultRoomList = new ArrayList<>();
        int size = DEFAULT_SIZE;
        int offset = 0;

        while (true) {
            List<byte[]> batchRooms = roomDB.getValuesBatch(offset, size);
            if (batchRooms == null || batchRooms.isEmpty()) break;

            List<Room> roomBatch = new ArrayList<>();
            for (byte[] roomBytes : batchRooms) {
                Room room = Room.fromJson(new String(roomBytes));
                if (room != null) {
                    roomBatch.add(room);
                }
            }

            if (roomBatch.isEmpty()) break;

            if (choose) {
                List<Room> chosenRooms = Inputer.chooseMultiFromListShowingMultiField(
                    roomBatch,
                    Arrays.asList(FieldNames.ROOM_ID, FieldNames.NAME),
                    Arrays.asList(11, 21),
                    "Choose rooms:",
                    1,
                    br
                );
                resultRoomList.addAll(chosenRooms);
            } else {
                resultRoomList.addAll(roomBatch);
            }

            if (batchRooms.size() < size) break;
            offset += size;
        }

        return resultRoomList.isEmpty() ? null : resultRoomList;
    }

    private void opRoomList(List<Room> rooms, BufferedReader br) {
        while (true) {
            String[] options = {"view", "Leave", "Members"};
            String subOp = Inputer.chooseOne(options, null, "What to do?", br);
            if (subOp == null || subOp.isEmpty()) return;
            
            switch (subOp) {
                case "view" -> viewRooms(rooms, br);
                case "Leave" -> handleLeaveRooms(br);
                case "Members" -> viewRoomMembers(rooms, br);
                default -> {return;}
            }
        }
    }

    private void viewRooms(List<Room> rooms, BufferedReader br) {
        System.out.println(JsonTools.toNiceJson(rooms));
        Menu.anyKeyToContinue(br);
    }

    private void viewRoomMembers(List<Room> rooms, BufferedReader br) {
        for (Room room : rooms) {
            System.out.println("Members of room [" + room.getName() + "]:");
            System.out.println("Owner: " + room.getOwner());
            System.out.println("Members: " + room.getMembers());
            System.out.println();
            Menu.anyKeyToContinue(br);
        }
    }

    // Add these new private helper methods for cache management
    private void addToCache(Room room) {
        if (room != null && room.getRoomId() != null) {
            synchronized (roomCache) {
                if (roomCache.size() >= CACHE_SIZE) {
                    // Remove oldest entry when cache is full
                    Optional<Map.Entry<String, Room>> oldest = roomCache.entrySet().stream()
                        .min(Comparator.comparingLong(e -> e.getValue().getBirthTime()));
                    oldest.ifPresent(entry -> roomCache.remove(entry.getKey()));
                }
                roomCache.put(room.getRoomId(), room);
            }
        }
    }

    private Room getFromCache(String roomId) {
        Room room = roomCache.get(roomId);
        if (room != null) {
            // Update access time by re-putting
            synchronized (roomCache) {
                roomCache.put(roomId, room);
            }
        }
        return room;
    }

    public List<Room> findRooms(String searchStr) {
        List<Room> foundRooms = Collections.synchronizedList(new ArrayList<>());
        
        // First search in cache
        roomCache.values().forEach(room -> {
            if (matchesSearchCriteria(room, searchStr)) {
                foundRooms.add(room);
            }
        });
        
        // Then search in persistent storage
        for (Map.Entry<byte[], byte[]> entry : roomDB.entrySet()) {
            String roomId = new String(entry.getKey());
            // Skip if already found in cache
            if (roomCache.containsKey(roomId)) continue;
            
            Room room = Room.fromJson(new String(entry.getValue()));
            if (room != null && matchesSearchCriteria(room, searchStr)) {
                foundRooms.add(room);
                addToCache(room); // Add to cache for future use
            }
        }
        return foundRooms;
    }

    private boolean matchesSearchCriteria(Room room, String searchStr) {
        return (room.getRoomId() != null && room.getRoomId().contains(searchStr)) ||
               (room.getName() != null && room.getName().contains(searchStr));
    }

    // Add this helper method to choose one room from a list
    public Room chooseOneRoomFromList(List<Room> rooms, BufferedReader br) {
        if (rooms == null || rooms.isEmpty()) {
            System.out.println("No rooms found.");
            return null;
        }

        System.out.println("\nFound Rooms:");
        for (int i = 0; i < rooms.size(); i++) {
            Room room = rooms.get(i);
            System.out.printf("%d. [%s] %s (Members: %d)%n", 
                i + 1, 
                room.getRoomId(), 
                room.getName(), 
                room.getMemberNum());
        }

        int choice = Inputer.inputInt(br, "\nSelect a room number (or press Enter to cancel):", rooms.size());
        if (choice <= 0) return null;
        
        return rooms.get(choice - 1);
    }
    
}
