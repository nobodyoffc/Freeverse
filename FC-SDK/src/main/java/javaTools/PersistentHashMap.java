package javaTools;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public class PersistentHashMap {
    private final String sid;
    private final String mapName;
    private volatile DB db;
    private volatile ConcurrentMap<byte[], byte[]> map;
    private final Object lock = new Object();

    public PersistentHashMap(String sid, String mapName) {
        this.sid = sid;
        this.mapName = mapName;
    }

    public void initializeDb() {
        if (db == null || db.isClosed()) {
            synchronized (lock) {
                if (db == null || db.isClosed()) {
                    String dbName = FileTools.makeFileName(null, sid, mapName, constants.Strings.DOT_DB);
                    db = DBMaker.fileDB(dbName).transactionEnable().make();
                    map = db.hashMap(mapName)
                            .keySerializer(Serializer.BYTE_ARRAY)
                            .valueSerializer(Serializer.BYTE_ARRAY)
                            .createOrOpen();
                }
            }
        }
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
}