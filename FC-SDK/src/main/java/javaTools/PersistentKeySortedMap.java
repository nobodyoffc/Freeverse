package javaTools;

import org.jetbrains.annotations.NotNull;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import fcData.MailDetail;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;
import java.util.Map.Entry;

public class PersistentKeySortedMap {
    private final String sid;
    private final String mapName;
    private volatile DB db;
    private volatile NavigableMap<byte[], byte[]> map;
    private final Object lock = new Object();

    public PersistentKeySortedMap(String sid, String mapName) {
        this.sid = sid;
        this.mapName = mapName;
    }

    private void initializeDb() {
        if (db == null || db.isClosed()) {
            synchronized (lock) {
                if (db == null || db.isClosed()) {
                    String dbName = FileTools.makeFileName(null, sid, mapName, constants.Strings.DOT_DB);
                    db = DBMaker.fileDB(dbName).transactionEnable().make();
                    map = db.treeMap(mapName)
                            .keySerializer(org.mapdb.Serializer.BYTE_ARRAY)
                            .valueSerializer(org.mapdb.Serializer.BYTE_ARRAY)
                            .createOrOpen();
                }
            }
        } else if (map == null) map = initializeMap();
    }

    @NotNull
    private static NavigableMap<byte[], byte[]> initializeMap() {
        return new ConcurrentSkipListMap<>(new Comparator<byte[]>() {
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

    public List<MailDetail> getList(byte[] start, int size) {
        initializeDb();
        List<MailDetail> result = new ArrayList<>(size);
        
        java.util.Iterator<java.util.Map.Entry<byte[], byte[]>> iterator;
        if (start == null) {
            iterator = map.entrySet().iterator();
        } else {
            iterator = map.tailMap(start, false).entrySet().iterator();
        }
        
        while (iterator.hasNext() && result.size() < size) {
            byte[] value = iterator.next().getValue();
            MailDetail mailDetail = MailDetail.fromBytes(value);
            result.add(mailDetail);
        }
        
        return result;
    }

    public <T> List<T> getListFromEnd(byte[] start, int size, Function<byte[], T> deserializer) {
        initializeDb();
        List<T> result = new ArrayList<>(size);
        
        java.util.NavigableMap<byte[], byte[]> descendingView;
        if (start == null) {
            descendingView = map.descendingMap();
        } else {
            descendingView = map.headMap(start, false).descendingMap();
        }
        
        Iterator<Entry<byte[], byte[]>> iterator = descendingView.entrySet().iterator();
        
        while (iterator.hasNext() && result.size() < size) {
            byte[] value = iterator.next().getValue();
            T data = deserializer.apply(value);
            result.add(data);
        }
        
        return result;
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
     
    public Set<Map.Entry<byte[], byte[]>> entrySet() {
        initializeDb();
        return new AbstractSet<Entry<byte[], byte[]>>() {
            @Override
            public Iterator<Map.Entry<byte[], byte[]>> iterator() {
                return new Iterator<Map.Entry<byte[], byte[]>>() {
                    private final Iterator<Map.Entry<byte[], byte[]>> mapIterator = map.entrySet().iterator();

                    @Override
                    public boolean hasNext() {
                        return mapIterator.hasNext();
                    }

                    @Override
                    public Map.Entry<byte[], byte[]> next() {
                        return mapIterator.next();
                    }
                };
            }

            @Override
            public int size() {
                return map.size();
            }
        };
    }

    public void remove(byte[] mailId) {
        initializeDb();
        map.remove(mailId);
        db.commit();
    }
}
