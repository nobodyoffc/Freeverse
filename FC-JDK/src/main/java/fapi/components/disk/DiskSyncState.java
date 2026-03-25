package fapi.components.disk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks incremental sync progress for a single remote DISK source.
 * <p>
 * The cursor is stored as two strings (lastSyncSince, lastSyncId) matching
 * the FCDSL {@code after} format used by disk.list pagination.
 */
public class DiskSyncState {
    private static final Logger log = LoggerFactory.getLogger(DiskSyncState.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STATE_FILE = "diskSyncState.json";

    private String sid;
    private String lastSyncSince;
    private String lastSyncId;
    private long lastSyncTime;
    private long itemsSynced;
    private long bytesSynced;

    public DiskSyncState() {}

    public DiskSyncState(String sid) {
        this.sid = sid;
    }

    public String getSid() { return sid; }
    public void setSid(String sid) { this.sid = sid; }

    public String getLastSyncSince() { return lastSyncSince; }
    public void setLastSyncSince(String lastSyncSince) { this.lastSyncSince = lastSyncSince; }

    public String getLastSyncId() { return lastSyncId; }
    public void setLastSyncId(String lastSyncId) { this.lastSyncId = lastSyncId; }

    public long getLastSyncTime() { return lastSyncTime; }
    public void setLastSyncTime(long lastSyncTime) { this.lastSyncTime = lastSyncTime; }

    public long getItemsSynced() { return itemsSynced; }
    public void setItemsSynced(long itemsSynced) { this.itemsSynced = itemsSynced; }

    public long getBytesSynced() { return bytesSynced; }
    public void setBytesSynced(long bytesSynced) { this.bytesSynced = bytesSynced; }

    /**
     * Load all sync states from the JSON file in the given directory.
     *
     * @param dbDir directory containing the state file
     * @return mutable map of SID -> DiskSyncState
     */
    public static Map<String, DiskSyncState> loadAll(String dbDir) {
        if (dbDir == null) return new HashMap<>();
        Path path = Path.of(dbDir, STATE_FILE);
        if (!Files.exists(path)) return new HashMap<>();
        try {
            String json = Files.readString(path);
            Type type = new TypeToken<Map<String, DiskSyncState>>() {}.getType();
            Map<String, DiskSyncState> map = GSON.fromJson(json, type);
            return map != null ? map : new HashMap<>();
        } catch (Exception e) {
            log.error("Failed to load sync states from {}: {}", path, e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Persist all sync states to the JSON file in the given directory.
     */
    public static void saveAll(String dbDir, Map<String, DiskSyncState> states) {
        if (dbDir == null || states == null) return;
        Path dir = Path.of(dbDir);
        try {
            Files.createDirectories(dir);
            Path path = dir.resolve(STATE_FILE);
            Files.writeString(path, GSON.toJson(states));
        } catch (IOException e) {
            log.error("Failed to save sync states to {}: {}", dbDir, e.getMessage());
        }
    }
}
