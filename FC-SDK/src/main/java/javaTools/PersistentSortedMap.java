package javaTools;

import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.Comparator;
import java.util.function.Function;

public class PersistentSortedMap {
    private final String sid;
    private final String mapName;
    private volatile DB db;
    private volatile ConcurrentSkipListMap<byte[], byte[]> map;
    private final Object lock = new Object();

    public PersistentSortedMap(String sid, String mapName) {
        this.sid = sid;
        this.mapName = mapName;
    }

    private void initializeDb() {
        if (db == null || db.isClosed()) {
            synchronized (lock) {
                if (db == null || db.isClosed()) {
                    String dbName = FileTools.makeFileName(null, sid, mapName, constants.Strings.DOT_DB);
                    db = DBMaker.fileDB(dbName).transactionEnable().make();
                    map = new ConcurrentSkipListMap<>(new Comparator<byte[]>() {
                        @Override
                        public int compare(byte[] a, byte[] b) {
                            // Lexicographical comparison
                            for (int i = 0; i < Math.min(a.length, b.length); i++) {
                                int diff = Byte.compare(a[i], b[i]);
                                if (diff != 0) return diff;
                            }
                            return Integer.compare(a.length, b.length);
                        }
                    });
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
}