package tools;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import constants.FieldNames;
import fcData.MailDetail;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.io.File;

public class PersistentSequenceMap {
    private final String sid;
    private final String myFid;
    private final String mapName;
    private volatile DB db;
    private volatile HTreeMap<byte[], byte[]> map;
    private volatile HTreeMap<Long, byte[]> orderMap;
    private volatile HTreeMap<String, Long> metaMap;
    private volatile long currentIndex;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final String dbPath;
    public PersistentSequenceMap(String myFid,String sid, String mapName, String dbPath) {
        this.sid = sid;
        this.mapName = mapName;
        this.myFid = myFid;
        if(dbPath==null)dbPath = FileTools.getUserDir()+"/db/";
        if(!dbPath.endsWith("/"))dbPath += "/";
        this.dbPath = dbPath;
    }

    private void initializeDb() {
        if (!FileTools.checkDirOrMakeIt(dbPath)) return;
        rwLock.writeLock().lock();
        try {
            if (db == null || db.isClosed()) {
                String dbName = FileTools.makeFileName(myFid, sid, mapName, constants.Strings.DOT_DB);
                // Ensure proper path separator and file permissions
                db = DBMaker.fileDB(new File(dbPath, dbName))
                        .transactionEnable()
                        .make();
                map = db.hashMap(mapName)
                        .keySerializer(Serializer.BYTE_ARRAY)
                        .valueSerializer(Serializer.BYTE_ARRAY)
                        .createOrOpen();
                orderMap = db.hashMap(mapName + "_order")
                        .keySerializer(Serializer.LONG)
                        .valueSerializer(Serializer.BYTE_ARRAY)
                        .createOrOpen();
                metaMap = db.hashMap(mapName + "_meta")
                        .keySerializer(Serializer.STRING)
                        .valueSerializer(Serializer.LONG)
                        .createOrOpen();
                currentIndex = orderMap.size();
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public boolean isEmpty() {
        initializeDb();
        rwLock.readLock().lock();
        try {
            return map.isEmpty();
        } finally {
            rwLock.readLock().unlock();
        }
    }   

    public void clear() {
        initializeDb();
        rwLock.writeLock().lock();
        try {
            map.clear();
            orderMap.clear();
            metaMap.clear();
            currentIndex = 0;
            setLastHeight(0);
        } finally {
            rwLock.writeLock().unlock();
        }
    } 

    public int size() {
        initializeDb();
        rwLock.readLock().lock();
        try {
            return map.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }   

    public <T> List<T> values(Class<T> clazz, Function<byte[], T> mapper) {
        initializeDb();
        rwLock.readLock().lock();
        try {
            return new ArrayList<>(map.values()).stream()
                    .map(value -> mapper.apply(value))
                    .collect(Collectors.toList());
        } finally {
            rwLock.readLock().unlock();
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

    public <T> List<T> getListFromEnd(byte[] startKey, int size, Function<byte[], T> mapper) {
        initializeDb();
        rwLock.readLock().lock();
        boolean startFound = (startKey == null);
        try {
            List<T> result = new ArrayList<>(size);
            
            for (long i = currentIndex - 1; i >= 0; i--) {
                byte[] key = orderMap.get(i);
                if (!startFound) {
                    if (Arrays.equals(key, startKey)) {
                        startFound = true;
                    }
                    continue;
                }
                
                byte[] value = map.get(key);
                T data = mapper.apply(value);

                result.add(data);

                
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

    public List<byte[]> getValuesBatch(int offset, int limit) {
        initializeDb();
        rwLock.readLock().lock();
        try {
            List<byte[]> batch = new ArrayList<>();
            // Only iterate through the requested range
            for (long i = offset; i < Math.min(offset + limit, currentIndex); i++) {
                byte[] key = orderMap.get(i);
                if (key != null) {
                    byte[] value = map.get(key);
                    if (value != null) {
                        batch.add(value);
                    }
                }
            }
            return batch;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void setLastTime(long time) {
        initializeDb();
        rwLock.writeLock().lock();
        try {
            metaMap.put(FieldNames.LAST_TIME, time);
            db.commit();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public long getLastTime() {
        initializeDb();
        rwLock.readLock().lock();
        try {
            return metaMap.getOrDefault(FieldNames.LAST_TIME, 0L);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void setLastHeight(long height) {
        initializeDb();
        rwLock.writeLock().lock();
        try {
            metaMap.put(FieldNames.LAST_HEIGHT, height);
            db.commit();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public long getLastHeight() {
        initializeDb();
        rwLock.readLock().lock();
        try {
            return metaMap.getOrDefault(FieldNames.LAST_HEIGHT, 0L);
        } finally {
            rwLock.readLock().unlock();
        }
    }
}
