package handlers;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import fcData.Hat;
import tools.FileTools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class HatDB {
    private static final String HAT_DB = "hat";
    private final String myFid;
    private final String sid;
    private final String dbPath;
    
    private volatile DB db;
    private volatile HTreeMap<byte[], byte[]> hatMap;
    private volatile HTreeMap<byte[], byte[]> cipherRawDidMap;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    public HatDB(String myFid, String sid, String dbPath) {
        this.myFid = myFid;
        this.sid = sid;
        if(dbPath == null) dbPath = FileTools.getUserDir() + "/db/";
        if(!dbPath.endsWith("/")) dbPath += "/";
        this.dbPath = dbPath;
        initializeDb();
    }

    private void initializeDb() {
        if (!FileTools.checkDirOrMakeIt(dbPath)) return;
        if (db == null || db.isClosed()) {
            rwLock.writeLock().lock();
            try {
                if (db == null || db.isClosed()) {
                    String dbName = FileTools.makeFileName(myFid, sid, HAT_DB, constants.Strings.DOT_DB);
                    db = DBMaker.fileDB(dbPath + dbName)
                            .fileMmapEnable()
                            .transactionEnable()
                            .make();

                    hatMap = db.hashMap("hatMap")
                            .keySerializer(Serializer.BYTE_ARRAY)
                            .valueSerializer(Serializer.BYTE_ARRAY)
                            .createOrOpen();

                    cipherRawDidMap = db.hashMap("cipherRawDidMap")
                            .keySerializer(Serializer.BYTE_ARRAY)
                            .valueSerializer(Serializer.BYTE_ARRAY)
                            .createOrOpen();
                }
            } finally {
                rwLock.writeLock().unlock();
            }
        }
    }

    public Hat getHat(String did) {
        if (did == null) return null;
        
        initializeDb();
        rwLock.readLock().lock();
        try {
            byte[] hatBytes = hatMap.get(did.getBytes());
            if (hatBytes == null) return null;
            return Hat.fromJson(new String(hatBytes), Hat.class);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void putHat(Hat hat) {
        if (hat == null || hat.getDid() == null) return;

        initializeDb();
        rwLock.writeLock().lock();
        try {
            hatMap.put(hat.getDid().getBytes(), hat.toJson().getBytes());
            
            // Store cipher-raw DID mapping if rawDid exists
            if (hat.getRawDid() != null) {
                cipherRawDidMap.put(hat.getDid().getBytes(), hat.getRawDid().getBytes());
            }
            
            db.commit();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void removeHat(String did) {
        if (did == null) return;

        initializeDb();
        rwLock.writeLock().lock();
        try {
            hatMap.remove(did.getBytes());
            cipherRawDidMap.remove(did.getBytes());
            db.commit();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public List<Hat> getAllHats() {
        initializeDb();
        rwLock.readLock().lock();
        try {
            List<Hat> hats = new ArrayList<>();
            for (Map.Entry<byte[], byte[]> entry : hatMap.entrySet()) {
                Hat hat = Hat.fromJson(new String(entry.getValue()), Hat.class);
                if (hat != null) {
                    hats.add(hat);
                }
            }
            return hats;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public String getRawDidForCipher(String cipherDid) {
        if (cipherDid == null) return null;

        initializeDb();
        rwLock.readLock().lock();
        try {
            byte[] rawDidBytes = cipherRawDidMap.get(cipherDid.getBytes());
            return rawDidBytes != null ? new String(rawDidBytes) : null;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void close() {
        rwLock.writeLock().lock();
        try {
            if (db != null && !db.isClosed()) {
                db.close();
                db = null;
                hatMap = null;
                cipherRawDidMap = null;
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }
} 