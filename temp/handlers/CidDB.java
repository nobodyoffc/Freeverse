package handlers;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import utils.FileUtils;

import java.util.concurrent.ConcurrentMap;

public class CidDB {
    private static final String CID_DB = "cid";
    private final String myFid;
    private final String sid;
    
    private volatile DB db;
    private volatile ConcurrentMap<String, String> fidCidMap;
    private volatile ConcurrentMap<String, String> cidFidMap;
    private volatile ConcurrentMap<String, String> fidAvatarMap;
    private final Object lock = new Object();
    private final String dbPath;
    public CidDB(String myFid, String sid, String dbPath) {
        this.myFid = myFid;
        this.sid = sid;
        if(!dbPath.endsWith("/"))dbPath += "/";
        this.dbPath = dbPath;
        initializeDb();
    }

    private void initializeDb() {
        if (!FileUtils.checkDirOrMakeIt(dbPath)) return;
        if (db == null || db.isClosed()) {
            synchronized (lock) {
                if (db == null || db.isClosed()) {
                    String dbName = FileUtils.makeFileName(myFid, sid, CID_DB, constants.Strings.DOT_DB);
                    db = DBMaker.fileDB(dbPath+dbName)
                            .fileMmapEnable()
                            .checksumHeaderBypass()
                            .transactionEnable()
                            .make();
                    
                    fidCidMap = db.hashMap("fidCid")
                            .keySerializer(Serializer.STRING)
                            .valueSerializer(Serializer.STRING)
                            .createOrOpen();
                            
                    cidFidMap = db.hashMap("cidFid")
                            .keySerializer(Serializer.STRING)
                            .valueSerializer(Serializer.STRING)
                            .createOrOpen();
                            
                    fidAvatarMap = db.hashMap("fidAvatar")
                            .keySerializer(Serializer.STRING)
                            .valueSerializer(Serializer.STRING)
                            .createOrOpen();
                }
            }
        }
    }

    public String getCidByFid(String fid) {
        return fidCidMap.get(fid);
    }

    public void setFidCid(String fid, String cid) {
        fidCidMap.put(fid, cid);
        cidFidMap.put(cid, fid);
        db.commit();
    }

    public String getFidByCid(String cid) {
        return cidFidMap.get(cid);
    }

    public String getFidAvatar(String fid) {
        return fidAvatarMap.get(fid);
    }

    public void setFidAvatar(String fid, String avatarPath) {
        fidAvatarMap.put(fid, avatarPath);
        db.commit();
    }

    public void close() {
        synchronized (lock) {
            if (db != null && !db.isClosed()) {
                db.close();
                db = null;
            }
        }
    }
} 