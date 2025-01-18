package fcData;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import tools.FileTools;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public class TalkIdDB {
    private static final String TALK_ID_DB = "talkId";
    private final DB db;
    private final HTreeMap<String, TalkIdInfo> talkIdInfoMap;
    private final HTreeMap<String, String> metaMap;

    public TalkIdDB(String myFid, String sid) {
        String dbPath = FileTools.makeFileName(myFid, sid, TALK_ID_DB, constants.Strings.DOT_DB);
        File file = new File(dbPath);
        this.db = DBMaker.fileDB(file)
                .fileMmapEnable()
                .checksumHeaderBypass()
                .transactionEnable()
                .make();

        this.talkIdInfoMap = db.hashMap("talkIdInfoMap")
                .keySerializer(Serializer.STRING)
                .valueSerializer((Serializer<TalkIdInfo>)TalkIdInfo.serializer(TalkIdInfo.class))
                .createOrOpen();

        this.metaMap = db.hashMap("metaMap")
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.STRING)
                .createOrOpen();
    }

    public synchronized void put(String id, TalkIdInfo info) {
        talkIdInfoMap.put(id, info);
        db.commit();
    }

    public TalkIdInfo get(String id) {
        return (TalkIdInfo) talkIdInfoMap.get(id);
    }

    public synchronized void delete(String id) {
        talkIdInfoMap.remove(id);
        db.commit();
    }

    public synchronized void setLastTalkId(String id) {
        metaMap.put("lastTalkId", id);
        db.commit();
    }

    public String getLastTalkId() {
        return metaMap.get("lastTalkId");
    }

    public void close() {
        db.close();
    }

    public List<TalkIdInfo> search(String searchTerm) {
        return talkIdInfoMap.values().stream()
                .filter(info -> TalkIdInfo.matchesTalkIdInfo(info, searchTerm))
                .collect(Collectors.toList());
    } 
} 
