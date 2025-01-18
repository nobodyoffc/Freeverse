package handlers;

import fcData.Hat;
import tools.PersistentSequenceMap;
import constants.FieldNames;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;

public class HatHandler extends Handler {
    // Constants
    private static final int CACHE_SIZE = 1000;

    // Instance Variables
    private final String myFid;
    private final PersistentSequenceMap hatDB;
    private final ConcurrentHashMap<String, Hat> hatCache;  
    private final PersistentSequenceMap cipherRawDidMap;

    // Constructor
    public HatHandler(String myFid,String sid) {
        this.myFid = myFid;
        this.hatDB = new PersistentSequenceMap(myFid, sid, FieldNames.HAT);
        this.hatCache = new ConcurrentHashMap<>(CACHE_SIZE);
        this.cipherRawDidMap = new PersistentSequenceMap(myFid, sid, "cipher_raw_did_map");
    }

    // Public Methods
    public Hat getHatByDid(String did) {
        if (did == null) return null;

        // Try cache first
        Hat hat = getFromCache(did);
        if (hat != null) {
            return hat;
        }

        // If not in cache, get from persistent storage
        byte[] hatBytes = hatDB.get(did.getBytes());
        if (hatBytes == null) return null;

        hat = Hat.fromJson(new String(hatBytes), Hat.class);
        if (hat != null) {
            addToCache(hat);
        }
        return hat;
    }

    public void putHat(Hat hat) {
        if (hat == null || hat.getDid() == null) return;

        // Update both cache and persistent storage
        addToCache(hat);
        hatDB.put(hat.getDid().getBytes(), hat.toJson().getBytes());
        
        // Add cipher-raw DID mapping if rawDid exists
        if (hat.getRawDid() != null) {
            cipherRawDidMap.put(hat.getDid().getBytes(), hat.getRawDid().getBytes());
        }
    }

    public void removeHat(String did) {
        if (did == null) return;

        // Remove from both cache and persistent storage
        hatCache.remove(did);
        hatDB.remove(did.getBytes());
    }

    public List<Hat> getAllHats() {
        List<Hat> hats = new ArrayList<>();
        
        // Get all values from persistent storage
        for (Map.Entry<byte[], byte[]> entry : hatDB.entrySet()) {
            String did = new String(entry.getKey());
            // Check cache first
            Hat hat = hatCache.get(did);
            if (hat == null) {
                // If not in cache, deserialize from storage
                hat = Hat.fromJson(new String(entry.getValue()), Hat.class);
                if (hat != null) {
                    addToCache(hat);
                }
            }
            if (hat != null) {
                hats.add(hat);
            }
        }
        return hats;
    }

    // Private Cache Management Methods
    private void addToCache(Hat hat) {
        if (hat != null && hat.getDid() != null) {
            synchronized (hatCache) {
                if (hatCache.size() >= CACHE_SIZE) {
                    // Remove oldest entry when cache is full
                    Optional<Map.Entry<String, Hat>> oldest = hatCache.entrySet().stream()
                        .min(Comparator.comparingLong(e -> e.getValue().getLast() != null ? e.getValue().getLast() : Long.MAX_VALUE));
                    oldest.ifPresent(entry -> hatCache.remove(entry.getKey()));
                }
                // Update last access time
                hat.setLast(System.currentTimeMillis());
                hatCache.put(hat.getDid(), hat);
            }
        }
    }

    private Hat getFromCache(String did) {
        Hat hat = hatCache.get(did);
        if (hat != null) {
            // Update last access time
            hat.setLast(System.currentTimeMillis());
            synchronized (hatCache) {
                hatCache.put(did, hat);
            }
        }
        return hat;
    }

    // Cleanup Method
    public void close() {
        hatCache.clear();
        hatDB.close();
        cipherRawDidMap.close();
    }

    // Add new method to get rawDid for a cipher DID
    public String getRawDidForCipher(String cipherDid) {
        if (cipherDid == null) return null;
        
        byte[] rawDidBytes = cipherRawDidMap.get(cipherDid.getBytes());
        return rawDidBytes != null ? new String(rawDidBytes) : null;
    }
    
} 