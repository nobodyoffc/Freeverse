package handlers;

import fcData.FcEntity;
import db.LocalDB;
import db.LevelDB;
import fcData.TalkIdInfo;
import utils.FileUtils;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TalkIdDB {
    private static final String TALK_ID_DB = "talkId";
    private final LocalDB<FcEntity> db;

    public TalkIdDB(String myFid, String sid, String dbPath) {
        String fileName = FileUtils.makeFileName(myFid, sid, TALK_ID_DB, constants.Strings.DOT_DB);
        FileUtils.createFileDirectories(dbPath);
        
        this.db = new LevelDB<>(LocalDB.SortType.NO_SORT, FcEntity.class);
        db.initialize(myFid, sid, dbPath, fileName);
        
        // Register TalkIdInfo type for the map
        db.registerMapType("talkIdInfoMap", TalkIdInfo.class);
        
        // Create metadata map
        db.registerMapType("metaMap", String.class);
    }

    public synchronized void put(String id, TalkIdInfo info) {
        db.putInMap("talkIdInfoMap", id, info);
        db.commit();
    }

    public TalkIdInfo get(String id) {
        return db.getFromMap("talkIdInfoMap", id);
    }

    public synchronized void delete(String id) {
        db.removeFromMap("talkIdInfoMap", id);
        db.commit();
    }

    public synchronized void setLastTalkId(String id) {
        db.putInMap("metaMap", "lastTalkId", id);
        db.commit();
    }

    public String getLastTalkId() {
        return db.getFromMap("metaMap", "lastTalkId");
    }

    public void close() {
        db.close();
    }

    @SuppressWarnings("unchecked")
    public List<TalkIdInfo> search(String searchTerm) {
        Map<String, Object> allObjects = db.getAllFromMap("talkIdInfoMap");
        return allObjects.values().stream()
                .map(obj -> (TalkIdInfo) obj)
                .filter(info -> TalkIdInfo.matchesTalkIdInfo(info, searchTerm))
                .collect(Collectors.toList());
    } 
} 
