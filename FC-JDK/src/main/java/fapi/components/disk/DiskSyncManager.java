package fapi.components.disk;

import data.apipData.Fcdsl;
import data.fcData.DiskItem;
import fapi.client.FapiClient;
import fapi.message.FapiResponse;
import fapi.service.FapiServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages automatic DISK data synchronization from remote FAPI servers.
 * <p>
 * Runs on a daily schedule via {@link ScheduledExecutorService}, iterating
 * through configured remote sources sequentially, fetching new DiskItems
 * via paginated {@code disk.list} calls, and downloading files that are
 * not yet stored locally.
 */
public class DiskSyncManager {
    private static final Logger log = LoggerFactory.getLogger(DiskSyncManager.class);

    private static final int PAGE_SIZE = 100;
    public static final long DEFAULT_MAX_DATA_SIZE = 100L * 1024 * 1024;          // 100 MB
    public static final long DEFAULT_MAX_TOTAL_DISK_USAGE = 10L * 1024 * 1024 * 1024; // 10 GB
    public static final long DEFAULT_MIN_DEALER_BALANCE = 1_000_000L;              // 0.01 FCH
    public static final long DEFAULT_SYNC_INTERVAL_HOURS = 24;

    public static final String KEY_DISK_SYNC_SOURCES = "diskSyncSources";
    public static final String KEY_MAX_DATA_SIZE = "maxDataSize";
    public static final String KEY_MAX_TOTAL_DISK_USAGE = "maxTotalDiskUsage";
    public static final String KEY_MIN_DEALER_BALANCE = "minDealerBalance";
    public static final String KEY_DISK_SYNC_INTERVAL_HOURS = "diskSyncIntervalHours";

    private final FapiServer fapiServer;
    private final FapiDiskHandler diskHandler;
    private final List<DiskSyncSource> sources;
    private final long maxDataSize;
    private final long maxTotalDiskUsage;
    private final long minDealerBalance;
    private final long defaultDataLifeDays;
    private final String dbDir;
    private final long syncIntervalHours;

    private ScheduledExecutorService scheduler;
    private Map<String, DiskSyncState> syncStates;
    private volatile boolean running;

    public DiskSyncManager(FapiServer fapiServer,
                           FapiDiskHandler diskHandler,
                           List<DiskSyncSource> sources,
                           long maxDataSize,
                           long maxTotalDiskUsage,
                           long minDealerBalance,
                           long defaultDataLifeDays,
                           String dbDir,
                           long syncIntervalHours) {
        this.fapiServer = fapiServer;
        this.diskHandler = diskHandler;
        this.sources = sources != null ? sources : Collections.emptyList();
        this.maxDataSize = maxDataSize;
        this.maxTotalDiskUsage = maxTotalDiskUsage;
        this.minDealerBalance = minDealerBalance;
        this.defaultDataLifeDays = defaultDataLifeDays;
        this.dbDir = dbDir;
        this.syncIntervalHours = syncIntervalHours > 0 ? syncIntervalHours : DEFAULT_SYNC_INTERVAL_HOURS;
    }

    /**
     * Start the daily sync scheduler.
     */
    public void start() {
        if (sources.isEmpty()) {
            log.info("DiskSyncManager: no sync sources configured, not starting");
            return;
        }
        this.syncStates = DiskSyncState.loadAll(dbDir);
        this.running = true;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                r -> { Thread t = new Thread(r, "disk-sync"); t.setDaemon(true); return t; });

        scheduler.scheduleAtFixedRate(this::syncAllSafe, 1, syncIntervalHours * 60, TimeUnit.MINUTES);

        log.info("DiskSyncManager started: {} sources, interval={}h, maxDataSize={}, maxTotal={}, minBalance={}",
                sources.size(), syncIntervalHours, maxDataSize, maxTotalDiskUsage, minDealerBalance);
    }

    /**
     * Stop the scheduler gracefully.
     */
    public void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (syncStates != null) {
            DiskSyncState.saveAll(dbDir, syncStates);
        }
        log.info("DiskSyncManager stopped");
    }

    private void syncAllSafe() {
        try {
            syncAll();
        } catch (Exception e) {
            log.error("DiskSyncManager: unexpected error during sync cycle", e);
        }
    }

    /**
     * Run one full sync cycle: iterate sources sequentially.
     */
    public void syncAll() {
        log.info("DiskSyncManager: starting sync cycle for {} sources", sources.size());
        long cycleStart = System.currentTimeMillis();
        int totalSynced = 0;
        long totalBytes = 0;

        for (DiskSyncSource source : sources) {
            if (!running) break;
            if (!source.isEnabled()) {
                log.debug("DiskSyncManager: source {} is disabled, skipping", source.getSid());
                continue;
            }
            try {
                SyncResult result = syncFromSource(source);
                totalSynced += result.itemsSynced;
                totalBytes += result.bytesSynced;
            } catch (Exception e) {
                log.error("DiskSyncManager: error syncing from {}: {}", source.getSid(), e.getMessage(), e);
            }
        }

        DiskSyncState.saveAll(dbDir, syncStates);
        long elapsed = System.currentTimeMillis() - cycleStart;
        log.info("DiskSyncManager: sync cycle completed in {}ms, items={}, bytes={}", elapsed, totalSynced, totalBytes);
    }

    /**
     * Sync data from a single remote source.
     */
    private SyncResult syncFromSource(DiskSyncSource source) {
        String sid = source.getSid();
        log.info("DiskSyncManager: syncing from source sid={}, url={}", sid, source.getUrl());

        FapiClient client = fapiServer.getOrCreateClient(source.getUrl());
        if (client == null) {
            log.warn("DiskSyncManager: failed to connect to {}", source.getUrl());
            return SyncResult.EMPTY;
        }

        // Check balance on remote service
        if (!checkRemoteBalance(client, sid)) {
            return SyncResult.EMPTY;
        }

        DiskSyncState state = syncStates.computeIfAbsent(sid, DiskSyncState::new);
        long currentUsage = diskHandler.getTotalStorageSize();
        int itemsSynced = 0;
        long bytesSynced = 0;

        List<String> cursor = buildCursor(state);

        while (running) {
            // Check disk usage before each page
            if (currentUsage >= maxTotalDiskUsage) {
                log.warn("DiskSyncManager: maxTotalDiskUsage reached ({}/{}), stopping sync for {}",
                        currentUsage, maxTotalDiskUsage, sid);
                break;
            }

            Fcdsl fcdsl = new Fcdsl();
            fcdsl.addSort("since", "asc");
            fcdsl.addSort("id", "asc");
            fcdsl.addSize(PAGE_SIZE);
            if (cursor != null) {
                fcdsl.setAfter(cursor);
            }

            List<DiskItem> items = client.diskList(fcdsl);
            FapiResponse lastResponse = client.getLastResponse();

            if (items == null || items.isEmpty()) {
                log.debug("DiskSyncManager: no more items from {}", sid);
                break;
            }

            for (DiskItem item : items) {
                if (!running) break;
                String id = item.getId();
                if (id == null || id.isEmpty()) continue;

                // Skip items we already have
                if (diskHandler.exists(id)) continue;

                // Skip oversized items
                if (item.getSize() != null && item.getSize() > maxDataSize) {
                    log.debug("DiskSyncManager: skipping {}, size {} > maxDataSize {}", id, item.getSize(), maxDataSize);
                    continue;
                }

                // Skip if adding would exceed total usage
                long itemSize = item.getSize() != null ? item.getSize() : 0;
                if (currentUsage + itemSize > maxTotalDiskUsage) {
                    log.warn("DiskSyncManager: would exceed maxTotalDiskUsage, stopping sync for {}", sid);
                    updateState(state, items, lastResponse);
                    return new SyncResult(itemsSynced, bytesSynced);
                }

                // Download the file
                if (downloadAndStore(client, id)) {
                    itemsSynced++;
                    bytesSynced += itemSize;
                    currentUsage += itemSize;
                }
            }

            // Update cursor from response
            cursor = updateState(state, items, lastResponse);
            if (cursor == null) break;

            // If we got fewer items than page size, we've reached the end
            if (items.size() < PAGE_SIZE) break;
        }

        state.setLastSyncTime(System.currentTimeMillis());
        state.setItemsSynced(state.getItemsSynced() + itemsSynced);
        state.setBytesSynced(state.getBytesSynced() + bytesSynced);
        log.info("DiskSyncManager: finished source sid={}, synced {} items, {} bytes", sid, itemsSynced, bytesSynced);
        return new SyncResult(itemsSynced, bytesSynced);
    }

    private boolean checkRemoteBalance(FapiClient client, String sid) {
        // Make a small request to get balance info
        Fcdsl probe = new Fcdsl();
        probe.addSize(0);
        client.diskList(probe);

        Long balance = client.getLastBalance();
        if (balance == null) {
            log.warn("DiskSyncManager: unable to get balance for source {}", sid);
            return true; // proceed anyway — server may not charge
        }
        if (balance < minDealerBalance) {
            log.warn("DiskSyncManager: balance {} < minDealerBalance {} for source {}, skipping",
                    balance, minDealerBalance, sid);
            return false;
        }
        log.debug("DiskSyncManager: balance={} for source {}", balance, sid);
        return true;
    }

    private List<String> buildCursor(DiskSyncState state) {
        if (state.getLastSyncSince() != null && state.getLastSyncId() != null) {
            return List.of(state.getLastSyncSince(), state.getLastSyncId());
        }
        return null;
    }

    private List<String> updateState(DiskSyncState state, List<DiskItem> items, FapiResponse response) {
        // Prefer response.last (the ES search_after values)
        if (response != null && response.getLast() != null && response.getLast().size() >= 2) {
            List<String> last = response.getLast();
            state.setLastSyncSince(last.get(0));
            state.setLastSyncId(last.get(1));
            return last;
        }

        // Fallback: use the last item's fields
        if (items != null && !items.isEmpty()) {
            DiskItem lastItem = items.get(items.size() - 1);
            if (lastItem.getSince() != null && lastItem.getId() != null) {
                String since = String.valueOf(lastItem.getSince());
                state.setLastSyncSince(since);
                state.setLastSyncId(lastItem.getId());
                return List.of(since, lastItem.getId());
            }
        }
        return null;
    }

    private boolean downloadAndStore(FapiClient client, String id) {
        try {
            File tempFile = File.createTempFile("disk-sync-", ".tmp");
            try {
                DiskItem result = client.diskGet(id, tempFile);
                if (result == null) {
                    log.warn("DiskSyncManager: failed to download {}: {}", id,
                            client.getLastError() != null ? client.getLastError().getMessage() : "unknown");
                    return false;
                }

                byte[] content = java.nio.file.Files.readAllBytes(tempFile.toPath());
                if (content.length == 0) {
                    log.warn("DiskSyncManager: downloaded empty content for {}", id);
                    return false;
                }

                // storeFromBytes computes hash and verifies it becomes the id (content-addressable)
                DiskItem stored = diskHandler.storeFromBytes(content, false, defaultDataLifeDays);
                if (stored == null) {
                    log.warn("DiskSyncManager: failed to store {}", id);
                    return false;
                }

                // Verify the stored id matches what we requested
                if (!id.equalsIgnoreCase(stored.getId())) {
                    log.error("DiskSyncManager: hash mismatch for {}, stored as {}", id, stored.getId());
                    return false;
                }

                log.debug("DiskSyncManager: synced {} ({} bytes)", id, content.length);
                return true;
            } finally {
                tempFile.delete();
            }
        } catch (Exception e) {
            log.error("DiskSyncManager: error downloading {}: {}", id, e.getMessage());
            return false;
        }
    }

    public boolean isRunning() { return running; }
    public List<DiskSyncSource> getSources() { return Collections.unmodifiableList(sources); }
    public Map<String, DiskSyncState> getSyncStates() { return syncStates; }

    private record SyncResult(int itemsSynced, long bytesSynced) {
        static final SyncResult EMPTY = new SyncResult(0, 0);
    }
}
