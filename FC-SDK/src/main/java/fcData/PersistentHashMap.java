package fcData;
import constants.FieldNames;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import utils.FileUtils;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public class PersistentHashMap {
    private final String myFid;
    private final String sid;
    private final String mapName;
    private volatile DB db;
    private volatile ConcurrentMap<byte[], byte[]> map;
    private volatile ConcurrentMap<String, Long> metaMap;
    private final Object lock = new Object();
    
    public PersistentHashMap(String myFid, String sid, String mapName) {
        this.myFid = myFid;
        this.sid = sid;
        this.mapName = mapName;
    }

    public void initializeDb() {
        if (db == null || db.isClosed()) {
            synchronized (lock) {
                if (db == null || db.isClosed()) {
                    String dbName = FileUtils.makeFileName(myFid, sid, mapName, constants.Strings.DOT_DB);
                    db = DBMaker.fileDB(dbName).transactionEnable().make();
                    map = db.hashMap(mapName)
                            .keySerializer(Serializer.BYTE_ARRAY)
                            .valueSerializer(Serializer.BYTE_ARRAY)
                            .createOrOpen();
                    metaMap = db.hashMap(mapName + "_meta")
                            .keySerializer(Serializer.STRING)
                            .valueSerializer(Serializer.LONG)
                            .createOrOpen();
                }
            }
        }
    }

    public ConcurrentMap<byte[], byte[]> getMap() {
        initializeDb();
        return map;
    }   

    public byte[] get(byte[] key) {
        initializeDb();
        return map.get(key);
    }

    public void put(byte[] key, byte[] value) {
        initializeDb();
        map.put(key, value);
        db.commit();
    }

    public byte[] computeIfAbsent(byte[] key, Function<? super byte[], ? extends byte[]> mappingFunction) {
        initializeDb();
        byte[] result = map.computeIfAbsent(key, mappingFunction);
        db.commit();
        return result;
    }

    public void close() {
        synchronized (lock) {
            if (db != null && !db.isClosed()) {
                db.close();
                db = null;
                map = null;
            }
        }
    }

    public byte[][] keySet() {
        initializeDb();
        return map.keySet().toArray(new byte[0][]);
    }

    public byte[][] values() {
        initializeDb();
        return map.values().toArray(new byte[0][]);
    }

    @SuppressWarnings("unchecked")
    public Entry<byte[], byte[]>[] entrySet() {
        initializeDb();
        return map.entrySet().toArray(new Map.Entry[0]);
    }

    public void setLastTime(long time) {
        initializeDb();
        synchronized (lock) {
            metaMap.put(FieldNames.LAST_TIME, time);
            db.commit();
        }
    }

    public long getLastTime() {
        initializeDb();
        return metaMap.getOrDefault(FieldNames.LAST_TIME, 0L);
    }

    public void setLastHeight(long height) {
        initializeDb();
        synchronized (lock) {
            metaMap.put(FieldNames.LAST_HEIGHT, height);
            db.commit();
        }
    }

    public long getLastHeight() {
        initializeDb();
        return metaMap.getOrDefault(FieldNames.LAST_HEIGHT, 0L);
    }
}