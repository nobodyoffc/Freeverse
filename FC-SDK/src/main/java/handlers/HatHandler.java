
package handlers;

import appTools.Settings;
import fcData.Hat;
import db.LocalDB;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class HatHandler extends Handler<Hat> {
    // Constants
    private static final int CACHE_SIZE = 1000;
    private static final String CIPHER_RAW_DID_MAP = "cipherRawDidMap";
    private final String sid;
    private final String mainFid;
    // Instance Variables
    private final ConcurrentHashMap<String, Hat> hatCache;

    // Constructor

    public HatHandler(Settings settings) {
        super(settings, HandlerType.HAT, LocalDB.SortType.UPDATE_ORDER, Hat.class, true, false);
        this.mainFid = settings.getMainFid();
        this.sid = settings.getSid();
        this.hatCache = new ConcurrentHashMap<>(CACHE_SIZE);
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
        Hat hatFromDB = localDB.get(did);
        if (hatFromDB != null) {
            addToCache(hatFromDB);
        }
        return hatFromDB;
    }

    public void putHat(Hat hat) {
        if (hat == null || hat.getId() == null) return;

        // Update both cache and persistent storage
        addToCache(hat);
        localDB.put(hat.getId(), hat);

        // Store cipher-raw DID mapping if rawDid exists
        if (hat.getRawDid() != null) {
            localDB.putInMap(CIPHER_RAW_DID_MAP, hat.getId(), hat.getRawDid());
        }
    }

    public void removeHat(String did) {
        if (did == null) return;

        // Remove from both cache and persistent storage
        hatCache.remove(did);
        remove(did);
        localDB.removeFromMap(CIPHER_RAW_DID_MAP, did);
    }

    public List<Hat> getAllHats() {
        Map<String, Hat> hats = localDB.getAll();
        for (Hat hat : hats.values()) {
            addToCache(hat);
        }
        return List.copyOf(hats.values());
    }

    // Private Cache Management Methods
    private void addToCache(Hat hat) {
        if (hat != null && hat.getId() != null) {
            synchronized (hatCache) {
                if (hatCache.size() >= CACHE_SIZE) {
                    // Remove oldest entry when cache is full
                    Optional<Map.Entry<String, Hat>> oldest = hatCache.entrySet().stream()
                            .min(Comparator.comparingLong(e -> e.getValue().getLast() != null ? e.getValue().getLast() : Long.MAX_VALUE));
                    oldest.ifPresent(entry -> hatCache.remove(entry.getKey()));
                }
                // Update last access time
                hat.setLast(System.currentTimeMillis());
                hatCache.put(hat.getId(), hat);
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

    public String getRawDidForCipher(String cipherDid) {
        if (cipherDid == null) return null;
        return localDB.getFromMap(CIPHER_RAW_DID_MAP, cipherDid);
    }

    @Override
    public void close() {
        hatCache.clear();
        super.close();
    }
} 