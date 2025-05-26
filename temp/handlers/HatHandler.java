package handlers;

import fcData.Hat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;

import appTools.Settings;

public class HatHandler extends Handler {
    // Constants
    private static final int CACHE_SIZE = 1000;

    // Instance Variables
    private final String myFid;
    private final String sid;
    private final HatDB hatDB;
    private final ConcurrentHashMap<String, Hat> hatCache;

    // Constructor
    public HatHandler(String myFid, String sid, String dbPath) {
        this.myFid = myFid;
        this.sid = sid;
        this.hatDB = new HatDB(myFid, sid, dbPath);
        this.hatCache = new ConcurrentHashMap<>(CACHE_SIZE);
    }

    public HatHandler(Settings settings) {
        this(settings.getMainFid(), settings.getSid(), settings.getDbDir());
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
        Hat hatFromDB = hatDB.getHat(did);
        if (hatFromDB != null) {
            addToCache(hatFromDB);
        }
        return hatFromDB;
    }

    public void putHat(Hat hat) {
        if (hat == null || hat.getDid() == null) return;

        // Update both cache and persistent storage
        addToCache(hat);
        hatDB.putHat(hat);
    }

    public void removeHat(String did) {
        if (did == null) return;

        // Remove from both cache and persistent storage
        hatCache.remove(did);
        hatDB.removeHat(did);
    }

    public List<Hat> getAllHats() {
        List<Hat> hats = hatDB.getAllHats();
        for (Hat hat : hats) {
            addToCache(hat);
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
    }

    // Add new method to get rawDid for a cipher DID
    public String getRawDidForCipher(String cipherDid) {
        if (cipherDid == null) return null;

        return hatDB.getRawDidForCipher(cipherDid);
    }
}
