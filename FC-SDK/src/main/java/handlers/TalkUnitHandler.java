package handlers;

import fcData.TalkUnit;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import appTools.Settings;
import tools.FileTools;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;

public class TalkUnitHandler extends Handler {
    private final String dbName;
    private final DB db;
    private final Map<String, ConcurrentHashMap<String, TalkUnit>> mapMap;
    private final ConcurrentHashMap<String, String> recentTalkUnits; // id -> roomId
    private final Queue<String> recentIdQueue; // to maintain order and size
    private final int RECENT_QUEUE_SIZE = 2000;
    private final ScheduledExecutorService commitScheduler;
    private volatile boolean hasUncommittedChanges = false;

    public TalkUnitHandler(String myFid, String sid) {
        String userHome = System.getProperty("user.home");
        this.dbName = FileTools.makeName(myFid, sid, "talkUnit", true);
        String dbPath = Paths.get(userHome, dbName + ".db").toString();
        
        // Initialize DB with a single file
        this.db = DBMaker.fileDB(new File(dbPath))
                .fileMmapEnable()
                .closeOnJvmShutdown()
                .make();
        
        this.mapMap = new ConcurrentHashMap<>();
        this.recentTalkUnits = new ConcurrentHashMap<>();
        this.recentIdQueue = new ConcurrentLinkedQueue<>();
        
        this.commitScheduler = Executors.newSingleThreadScheduledExecutor();
        commitScheduler.scheduleWithFixedDelay(this::commitIfNeeded, 5, 5, TimeUnit.SECONDS);
    }

    public TalkUnitHandler(Settings settings){
        String userHome = System.getProperty("user.home");
        this.dbName = FileTools.makeName(settings.getMainFid(), settings.getSid(), "talkUnit", true);
        String dbPath = Paths.get(userHome, dbName + ".db").toString();

        this.db = DBMaker.fileDB(new File(dbPath))
                .fileMmapEnable()
                .closeOnJvmShutdown()
                .make();
        this.mapMap = new ConcurrentHashMap<>();
        this.recentTalkUnits = new ConcurrentHashMap<>();
        this.recentIdQueue = new ConcurrentLinkedQueue<>();
        this.commitScheduler = Executors.newSingleThreadScheduledExecutor();
        commitScheduler.scheduleWithFixedDelay(this::commitIfNeeded, 5, 5, TimeUnit.SECONDS);
    }

    public void saveTalkUnit(TalkUnit rawTalkUnit, boolean done) {
        if(done){
            rawTalkUnit.setStata(TalkUnit.State.SENT);
        }
        else {
            rawTalkUnit.setStata(TalkUnit.State.FAILED_TO_SEND);
        }
        put(rawTalkUnit);
    }

    private void commitIfNeeded() {
        if (hasUncommittedChanges) {
            db.commit();
            hasUncommittedChanges = false;
        }
    }

    public void put(TalkUnit talkUnit) {
        if (talkUnit == null) return;
        
        String roomId = makeRoomId(talkUnit);
        String id = talkUnit.getId();
        
        // Add to main storage
        mapMap.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
              .put(id, talkUnit);
        
        // Add to recent map
        addToRecentMap(id, roomId);
        
        hasUncommittedChanges = true;
    }

    private void addToRecentMap(String id, String roomId) {
        recentTalkUnits.put(id, roomId);
        recentIdQueue.offer(id);
        
        // Maintain size limit
        while (recentIdQueue.size() > RECENT_QUEUE_SIZE) {
            String oldestId = recentIdQueue.poll();
            recentTalkUnits.remove(oldestId);
        }
    }

    public List<TalkUnit> getRecentTalkUnits() {
        List<TalkUnit> result = new ArrayList<>();
        
        for (String id : recentIdQueue) {
            String roomId = recentTalkUnits.get(id);
            if (roomId != null) {
                TalkUnit unit = get(roomId, id);
                if (unit != null) {
                    result.add(unit);
                }
            }
        }
        
        return result;
    }

    public TalkUnit getRecentTalkUnit(String id) {
        String roomId = recentTalkUnits.get(id);
        if (roomId != null) {
            return get(roomId, id);
        }
        return null;
    }

    public List<TalkUnit> getRecentTalkUnits(int limit) {
        List<TalkUnit> result = new ArrayList<>();
        int count = 0;
        
        for (String id : recentIdQueue) {
            if (count >= limit) break;
            
            String roomId = recentTalkUnits.get(id);
            if (roomId != null) {
                TalkUnit unit = get(roomId, id);
                if (unit != null) {
                    result.add(unit);
                    count++;
                }
            }
        }
        
        return result;
    }

    public TalkUnit get(String id,TalkUnit talkUnitForRoomId) {
        if(talkUnitForRoomId!=null){
            String roomId = makeRoomId(talkUnitForRoomId);
            if(roomId!=null)return get(roomId,id);
        }
        return get(id);
    }
    public TalkUnit get(String id){
        // Search through all rooms in mapMap
        for (Map<String, TalkUnit> room : mapMap.values()) {
            TalkUnit unit = room.get(id);
            if (unit != null) {
                return unit;
            }
        }
        return null;
    }
    public TalkUnit get(String roomId, String id) {
        if(roomId==null)return get(id);
        Map<String, TalkUnit> room = mapMap.get(roomId);
        return room != null ? room.get(id) : null;
    }

    public String makeRoomId(TalkUnit talkUnit) {
        if (talkUnit == null) return null;
        return makeRoomId(talkUnit.getFrom(), talkUnit.getTo());
    }

    public String makeRoomId(String from, String to) {
        if (from == null || to == null) return null;
        // Ensure consistent room ID regardless of message direction
        return from.compareTo(to) < 0 ? from + "_" + to : to + "_" + from;
    }

    public String getDbName() {
        return dbName;
    }

    public void close() {
        try {
            commitScheduler.shutdown();
            if (!commitScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                commitScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            commitScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        if (hasUncommittedChanges) {
            db.commit();
        }
        db.close();
        mapMap.clear();
        recentTalkUnits.clear();
        recentIdQueue.clear();
    }
} 