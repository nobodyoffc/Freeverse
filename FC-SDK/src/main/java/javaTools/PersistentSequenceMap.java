package javaTools;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import fcData.MailDetail;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

public class PersistentSequenceMap {
    private final String sid;
    private final String mapName;
    private volatile DB db;
    private volatile HTreeMap<byte[], byte[]> map;
    private volatile HTreeMap<Long, byte[]> orderMap;
    private volatile long currentIndex;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    public PersistentSequenceMap(String sid, String mapName) {
        this.sid = sid;
        this.mapName = mapName;
    }

    private void initializeDb() {
        rwLock.writeLock().lock();
        try {
            if (db == null || db.isClosed()) {
                String dbName = FileTools.makeFileName(null, sid, mapName, constants.Strings.DOT_DB);
                db = DBMaker.fileDB(dbName).transactionEnable().make();
                map = db.hashMap(mapName)
                        .keySerializer(Serializer.BYTE_ARRAY)
                        .valueSerializer(Serializer.BYTE_ARRAY)
                        .createOrOpen();
                orderMap = db.hashMap(mapName + "_order")
                        .keySerializer(Serializer.LONG)
                        .valueSerializer(Serializer.BYTE_ARRAY)
                        .createOrOpen();
                currentIndex = orderMap.size();
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public List<MailDetail> getList(byte[] start, int size) {
        initializeDb();
        rwLock.readLock().lock();
        try {
            List<MailDetail> result = new ArrayList<>(size);
            boolean startFound = (start == null);
            
            for (long i = 0; i < currentIndex && result.size() < size; i++) {
                byte[] key = orderMap.get(i);
                if (!startFound) {
                    if (Arrays.equals(key, start)) {
                        startFound = true;
                    }
                    continue;
                }
                
                byte[] value = map.get(key);
                MailDetail mailDetail = MailDetail.fromBytes(value);
                result.add(mailDetail);
            }
            
            return result;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<MailDetail> getListFromEnd(byte[] start, int size) {
        initializeDb();
        rwLock.readLock().lock();
        boolean startFound = (start == null);
        try {
            List<MailDetail> result = new ArrayList<>(size);
            
            for (long i = currentIndex - 1; i >= 0; i--) {
                byte[] key = orderMap.get(i);
                if (!startFound) {
                    if (Arrays.equals(key, start)) {
                        startFound = true;
                    }
                    continue;
                }
                
                byte[] value = map.get(key);
                MailDetail mailDetail = MailDetail.fromBytes(value);

                result.add(mailDetail);

                
                if (result.size() >= size) {
                    break;
                }
            }
            
            return result;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public byte[] get(byte[] key) {
        initializeDb();
        rwLock.readLock().lock();
        try {
            return map.get(key);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void put(byte[] key, byte[] value) {
        initializeDb();
        rwLock.writeLock().lock();
        try {
            if (!map.containsKey(key)) {
                orderMap.put(currentIndex++, key);
            }
            map.put(key, value);
            db.commit();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public byte[] computeIfAbsent(byte[] key, Function<? super byte[], ? extends byte[]> mappingFunction) {
        initializeDb();
        rwLock.writeLock().lock();
        try {
            byte[] result = map.computeIfAbsent(key, mappingFunction);
            db.commit();
            return result;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void close() {
        rwLock.writeLock().lock();
        try {
            if (db != null && !db.isClosed()) {
                db.close();
                db = null;
                map = null;
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public Set<Map.Entry<byte[], byte[]>> entrySet() {
        initializeDb();
        rwLock.readLock().lock();
        try {
            return new AbstractSet<Map.Entry<byte[], byte[]>>() {
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
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void remove(byte[] mailId) {
        initializeDb();
        rwLock.writeLock().lock();
        try {
            map.remove(mailId);
            for (long i = 0; i < currentIndex; i++) {
                if (Arrays.equals(orderMap.get(i), mailId)) {
                    orderMap.remove(i);
                    break;
                }
            }
            db.commit();
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
