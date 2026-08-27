package fapi.components.disk;

import core.crypto.Hash;
import data.apipData.Fcdsl;
import data.fcData.DiskItem;
import fapi.client.FapiClient;
import fapi.components.DiskComponent;
import fapi.message.FapiResponse;
import fapi.service.FapiServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.DateUtils;
import utils.ObjectUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages automatic DISK data synchronization from remote FAPI servers.
 * <p>
 * Runs on a schedule via {@link ScheduledExecutorService}, iterating through
 * configured remote sources sequentially, fetching new DiskItems via paginated
 * {@code disk.list} calls, and downloading files that are not yet stored locally.
 * <p>
 * Robustness guarantees:
 * <ul>
 *   <li>The pagination cursor only advances past items that were stored, already
 *       present, or deliberately skipped (oversized). Items that fail to download
 *       are kept in a bounded per-source retry list and retried on later cycles.</li>
 *   <li>When the disk-usage limit is hit, the cursor stays before the unsynced
 *       item so sync resumes once space is freed.</li>
 *   <li>Downloaded content is hash-verified against the requested ID before it is
 *       stored, so a bad source cannot inject junk data that would propagate to
 *       peers syncing from this server (mutual-sync safety).</li>
 *   <li>Size limits are enforced on the actual downloaded size, not the size the
 *       remote claims in its listing.</li>
 *   <li>Only one sync cycle runs at a time; manual triggers share the scheduler
 *       thread with the periodic task.</li>
 * </ul>
 */
public class DiskSyncManager {
    private static final Logger log = LoggerFactory.getLogger(DiskSyncManager.class);

    private static final int PAGE_SIZE = 100;
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final int MAX_FAILED_ITEMS = 1000;
    public static final long DEFAULT_MAX_DATA_SIZE = 500L * 1024 * 1024;          // 500 MB
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
    private final AtomicBoolean syncing = new AtomicBoolean(false);

    /** Resolves a source SID to its current on-chain fudp URL; null result keeps the cached URL. */
    private java.util.function.Function<String, String> urlResolver;
    /** Persists a refreshed URL back to settings so the cache survives restarts. */
    private java.util.function.BiConsumer<String, String> urlPersister;

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
     * Start the periodic sync scheduler.
     */
    public void start() {
        if (sources.isEmpty()) {
            log.info("DiskSyncManager: no sync sources configured, not starting");
            return;
        }
        this.syncStates = new ConcurrentHashMap<>(DiskSyncState.loadAll(dbDir));
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

    /**
     * Trigger a sync cycle on the scheduler thread (shared with the periodic
     * task, so a manual trigger can never run concurrently with it).
     *
     * @return true if the sync was queued, false if the manager is not running
     */
    public boolean triggerSync() {
        if (scheduler == null || scheduler.isShutdown()) return false;
        scheduler.execute(this::syncAllSafe);
        return true;
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
     * Only one cycle runs at a time; overlapping calls return immediately.
     */
    public void syncAll() {
        if (!syncing.compareAndSet(false, true)) {
            log.info("DiskSyncManager: a sync cycle is already in progress, skipping");
            return;
        }
        try {
            if (syncStates == null) syncStates = new ConcurrentHashMap<>();
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
                    DiskSyncState state = syncStates.get(source.getSid());
                    if (state != null) recordError(state, "Unexpected error: " + e.getMessage());
                }
                // Persist after each source so a crash loses at most one source's progress
                DiskSyncState.saveAll(dbDir, syncStates);
            }

            long elapsed = System.currentTimeMillis() - cycleStart;
            log.info("DiskSyncManager: sync cycle completed in {}ms, items={}, bytes={}", elapsed, totalSynced, totalBytes);
        } finally {
            syncing.set(false);
        }
    }

    /**
     * Sync data from a single remote source.
     */
    private SyncResult syncFromSource(DiskSyncSource source) {
        String sid = source.getSid();
        refreshSourceUrl(source);
        log.info("DiskSyncManager: syncing from source sid={}, url={}", sid, source.getUrl());

        DiskSyncState state = syncStates.computeIfAbsent(sid, DiskSyncState::new);
        state.setLastError(null);

        FapiClient client = fapiServer.getOrCreateClient(source.getUrl());
        if (client == null) {
            recordError(state, "Failed to connect to " + source.getUrl());
            return SyncResult.EMPTY;
        }

        long currentUsage = diskHandler.getTotalStorageSize();
        int itemsSynced = 0;
        long bytesSynced = 0;

        try {
            // Retry previously failed downloads before paginating further
            SyncResult retried = retryFailedItems(client, state, currentUsage);
            itemsSynced += retried.itemsSynced;
            bytesSynced += retried.bytesSynced;
            currentUsage += retried.bytesSynced;

            List<String> cursor = buildCursor(state);

            pageLoop:
            while (running) {
                if (currentUsage >= maxTotalDiskUsage) {
                    recordError(state, "maxTotalDiskUsage reached (" + currentUsage + "/" + maxTotalDiskUsage + "), sync paused");
                    break;
                }

                Fcdsl fcdsl = new Fcdsl();
                fcdsl.addSort("since", "asc");
                fcdsl.addSort("id", "asc");
                fcdsl.addSize(PAGE_SIZE);
                if (cursor != null) {
                    fcdsl.setAfter(cursor);
                }

                // Use the returned response directly: the client is shared with other
                // components, so client.getLastResponse() could belong to another call.
                FapiResponse response = client.query("disk.list", fcdsl);
                if (response == null) {
                    recordError(state, "disk.list: no response from " + source.getUrl());
                    break;
                }
                if (response.getCode() != 0) {
                    recordError(state, "disk.list failed: code=" + response.getCode() + " " + response.getMessage());
                    break;
                }

                // The list response already carries our balance — no separate probe needed
                Long balance = response.getBalance();
                if (balance != null && balance < minDealerBalance) {
                    recordError(state, "balance " + balance + " < minDealerBalance " + minDealerBalance + ", sync paused");
                    requestRechargeToMinBalance(client, balance, source);
                    break;
                }

                List<DiskItem> items = ObjectUtils.objectToList(response.getData(), DiskItem.class);
                if (items == null || items.isEmpty()) {
                    log.debug("DiskSyncManager: no more items from {}", sid);
                    break;
                }

                for (DiskItem item : items) {
                    if (!running) break pageLoop;
                    String id = item.getId();
                    if (id == null || id.isEmpty()) continue;

                    // Skip items we already have (also breaks mutual-sync echo loops)
                    if (diskHandler.exists(id)) {
                        advanceCursor(state, item);
                        continue;
                    }

                    long claimedSize = item.getSize() != null ? item.getSize() : -1;
                    if (claimedSize > maxDataSize) {
                        log.debug("DiskSyncManager: skipping {}, size {} > maxDataSize {}", id, claimedSize, maxDataSize);
                        advanceCursor(state, item);
                        continue;
                    }
                    if (claimedSize > 0 && currentUsage + claimedSize > maxTotalDiskUsage) {
                        // Leave the cursor before this item so it is synced once space frees up
                        recordError(state, "maxTotalDiskUsage would be exceeded, sync paused");
                        break pageLoop;
                    }

                    FetchResult result = fetchAndStore(client, item, currentUsage);
                    switch (result.status) {
                        case STORED -> {
                            itemsSynced++;
                            bytesSynced += result.bytes;
                            currentUsage += result.bytes;
                            advanceCursor(state, item);
                        }
                        case TOO_LARGE -> advanceCursor(state, item);
                        case FAILED -> {
                            // The retry list covers this item, so the cursor may move on
                            recordFailure(state, id);
                            advanceCursor(state, item);
                        }
                        case DISK_FULL -> {
                            recordError(state, "maxTotalDiskUsage would be exceeded, sync paused");
                            break pageLoop;
                        }
                    }
                }

                // Prefer the server-provided search_after values for the next page
                List<String> nextCursor;
                if (response.getLast() != null && response.getLast().size() >= 2) {
                    nextCursor = response.getLast();
                    state.setLastSyncSince(nextCursor.get(0));
                    state.setLastSyncId(nextCursor.get(1));
                } else {
                    nextCursor = buildCursor(state);
                }

                if (items.size() < PAGE_SIZE) break;
                if (nextCursor == null || nextCursor.equals(cursor)) {
                    recordError(state, "pagination cursor did not advance, aborting to avoid a loop");
                    break;
                }
                cursor = nextCursor;
            }
        } finally {
            state.setLastSyncTime(System.currentTimeMillis());
            state.setItemsSynced(state.getItemsSynced() + itemsSynced);
            state.setBytesSynced(state.getBytesSynced() + bytesSynced);
        }

        log.info("DiskSyncManager: finished source sid={}, synced {} items, {} bytes, pendingRetry={}",
                sid, itemsSynced, bytesSynced, state.getFailedItems().size());
        return new SyncResult(itemsSynced, bytesSynced);
    }

    /**
     * Retry items that failed to download on earlier cycles. Metadata is
     * re-fetched in batches via disk.check; items the remote no longer has
     * are dropped from the retry list.
     */
    private SyncResult retryFailedItems(FapiClient client, DiskSyncState state, long currentUsage) {
        Map<String, Integer> failed = state.getFailedItems();
        if (failed.isEmpty()) return SyncResult.EMPTY;

        // Drop ids obtained elsewhere in the meantime (e.g. a user put, or another source)
        failed.keySet().removeIf(diskHandler::exists);
        if (failed.isEmpty()) return SyncResult.EMPTY;

        log.info("DiskSyncManager: retrying {} previously failed items for {}", failed.size(), state.getSid());
        int itemsSynced = 0;
        long bytesSynced = 0;

        List<String> ids = new ArrayList<>(failed.keySet());
        for (int from = 0; from < ids.size() && running; from += DiskComponent.MAX_CHECK_IDS) {
            List<String> batch = ids.subList(from, Math.min(from + DiskComponent.MAX_CHECK_IDS, ids.size()));
            Map<String, DiskItem> metaMap = client.diskCheck(batch);
            if (metaMap == null) {
                log.warn("DiskSyncManager: disk.check failed while retrying items for {}, keeping them for next cycle",
                        state.getSid());
                break;
            }
            for (String id : batch) {
                if (!running) break;
                DiskItem meta = metaMap.get(id);
                if (meta == null) {
                    // The remote no longer has it — nothing left to sync
                    failed.remove(id);
                    continue;
                }
                if (meta.getId() == null) meta.setId(id);

                FetchResult result = fetchAndStore(client, meta, currentUsage);
                switch (result.status) {
                    case STORED -> {
                        failed.remove(id);
                        itemsSynced++;
                        bytesSynced += result.bytes;
                        currentUsage += result.bytes;
                    }
                    case TOO_LARGE -> failed.remove(id);
                    case FAILED -> recordFailure(state, id);
                    case DISK_FULL -> {
                        recordError(state, "maxTotalDiskUsage would be exceeded, retry paused");
                        return new SyncResult(itemsSynced, bytesSynced);
                    }
                }
            }
        }
        return new SyncResult(itemsSynced, bytesSynced);
    }

    /**
     * Re-resolve the source URL from its SID so sync follows on-chain service
     * migrations; the stored URL is only a cache used when the lookup fails.
     */
    private void refreshSourceUrl(DiskSyncSource source) {
        if (urlResolver == null) return;
        try {
            String freshUrl = urlResolver.apply(source.getSid());
            if (freshUrl != null && !freshUrl.isEmpty() && !freshUrl.equals(source.getUrl())) {
                log.info("DiskSyncManager: url of {} changed on chain: {} -> {}",
                        source.getSid(), source.getUrl(), freshUrl);
                source.setUrl(freshUrl);
                if (urlPersister != null) urlPersister.accept(source.getSid(), freshUrl);
            }
        } catch (Exception e) {
            log.warn("DiskSyncManager: failed to refresh url for {}: {}", source.getSid(), e.getMessage());
        }
    }

    private void recordFailure(DiskSyncState state, String id) {
        Map<String, Integer> failed = state.getFailedItems();
        Integer prev = failed.get(id);
        int attempts = (prev == null ? 0 : prev) + 1;
        if (attempts >= MAX_RETRY_ATTEMPTS) {
            failed.remove(id);
            log.error("DiskSyncManager: giving up on {} after {} failed attempts", id, attempts);
            return;
        }
        if (prev == null && failed.size() >= MAX_FAILED_ITEMS) {
            log.error("DiskSyncManager: retry list full ({} items), dropping {} — it will not be retried",
                    MAX_FAILED_ITEMS, id);
            return;
        }
        failed.put(id, attempts);
    }

    private void recordError(DiskSyncState state, String message) {
        state.setLastError(message);
        state.setLastErrorTime(System.currentTimeMillis());
        log.warn("DiskSyncManager: [{}] {}", state.getSid(), message);
    }

    /**
     * Ask the client's recharge manager to top the balance up to minDealerBalance.
     * The regular auto-recharge threshold is 0 or the credit-based negative value,
     * which can never reach minDealerBalance — without this explicit request a
     * low-balance pause would last forever.
     */
    private void requestRechargeToMinBalance(FapiClient client, long balance, DiskSyncSource source) {
        fapi.client.AutoRechargeManager recharger = client.getAutoRechargeManager();
        if (recharger == null || !recharger.isEnabled()) {
            log.warn("DiskSyncManager: auto-recharge not available; fund the dealer FID or lower minDealerBalance to resume sync");
            return;
        }
        Long preferredSat = null;
        if (source.getRechargeFch() != null && source.getRechargeFch() > 0) {
            preferredSat = utils.FchUtils.coinToSatoshi(source.getRechargeFch());
        }
        recharger.rechargeToFloor(balance, minDealerBalance, preferredSat)
                .thenAccept(result -> {
                    if (result == null) return;
                    if (result.isSuccess()) {
                        log.info("DiskSyncManager: recharge tx {} broadcast; sync resumes after the payment is confirmed",
                                result.getTxId());
                    } else {
                        log.warn("DiskSyncManager: recharge failed: {}", result.getMessage());
                    }
                })
                .exceptionally(e -> {
                    log.warn("DiskSyncManager: recharge error: {}", e.getMessage());
                    return null;
                });
    }

    private List<String> buildCursor(DiskSyncState state) {
        if (state.getLastSyncSince() != null && state.getLastSyncId() != null) {
            return List.of(state.getLastSyncSince(), state.getLastSyncId());
        }
        return null;
    }

    /**
     * Advance the persisted cursor past an item whose outcome is final
     * (stored, already present, deliberately skipped, or queued for retry).
     */
    private void advanceCursor(DiskSyncState state, DiskItem item) {
        if (item.getSince() != null && item.getId() != null) {
            state.setLastSyncSince(String.valueOf(item.getSince()));
            state.setLastSyncId(item.getId());
        }
    }

    /**
     * Download one item to a temp file, enforce size limits on the actual
     * downloaded size, verify its hash against the requested ID, and store it
     * preserving the remote's permanence/expire.
     */
    private FetchResult fetchAndStore(FapiClient client, DiskItem item, long currentUsage) {
        String id = item.getId();
        File tempFile = null;
        try {
            tempFile = File.createTempFile("disk-sync-", ".tmp");
            DiskItem meta = client.diskGet(id, tempFile);
            if (meta == null) {
                log.warn("DiskSyncManager: failed to download {}: {}", id,
                        client.getLastError() != null ? client.getLastError().getMessage() : "unknown");
                return FetchResult.FAILED;
            }

            long actualSize = tempFile.length();
            if (actualSize == 0) {
                log.warn("DiskSyncManager: downloaded empty content for {}", id);
                return FetchResult.FAILED;
            }
            // Enforce limits on the actual size — the listing only carries the remote's claim
            if (actualSize > maxDataSize) {
                log.warn("DiskSyncManager: skipping {}, actual size {} > maxDataSize {}", id, actualSize, maxDataSize);
                return FetchResult.TOO_LARGE;
            }
            if (currentUsage + actualSize > maxTotalDiskUsage) {
                return FetchResult.DISK_FULL;
            }

            // Verify content BEFORE storing so a bad source cannot inject junk data
            String actualDid = Hash.sha256x2(tempFile);
            if (!id.equalsIgnoreCase(actualDid)) {
                log.error("DiskSyncManager: content hash mismatch for {} (got {}), not stored", id, actualDid);
                return FetchResult.FAILED;
            }

            // Preserve permanence: a carved item stays permanent on the mirror; otherwise
            // keep at least the remote's remaining life and at least the local default
            boolean permanent = item.getExpire() == null;
            long dataLifeDays = defaultDataLifeDays;
            if (!permanent) {
                long dayMs = DateUtils.dayToLong(1);
                long remainingDays = (item.getExpire() - System.currentTimeMillis() + dayMs - 1) / dayMs;
                if (remainingDays > dataLifeDays) dataLifeDays = remainingDays;
            }

            DiskItem stored;
            try (InputStream in = new BufferedInputStream(new FileInputStream(tempFile))) {
                stored = diskHandler.storeFromStream(in, actualSize, permanent, dataLifeDays);
            }
            if (stored == null || !id.equalsIgnoreCase(stored.getId())) {
                log.error("DiskSyncManager: failed to store {}", id);
                return FetchResult.FAILED;
            }

            log.debug("DiskSyncManager: synced {} ({} bytes)", id, actualSize);
            return new FetchResult(FetchStatus.STORED, actualSize);
        } catch (Exception e) {
            log.error("DiskSyncManager: error downloading {}: {}", id, e.getMessage());
            return FetchResult.FAILED;
        } finally {
            if (tempFile != null) tempFile.delete();
        }
    }

    public void setUrlResolver(java.util.function.Function<String, String> urlResolver) {
        this.urlResolver = urlResolver;
    }

    public void setUrlPersister(java.util.function.BiConsumer<String, String> urlPersister) {
        this.urlPersister = urlPersister;
    }

    public boolean isRunning() { return running; }
    public boolean isSyncing() { return syncing.get(); }
    public List<DiskSyncSource> getSources() { return Collections.unmodifiableList(sources); }
    public Map<String, DiskSyncState> getSyncStates() { return syncStates; }

    private enum FetchStatus { STORED, TOO_LARGE, DISK_FULL, FAILED }

    private record FetchResult(FetchStatus status, long bytes) {
        static final FetchResult FAILED = new FetchResult(FetchStatus.FAILED, 0);
        static final FetchResult TOO_LARGE = new FetchResult(FetchStatus.TOO_LARGE, 0);
        static final FetchResult DISK_FULL = new FetchResult(FetchStatus.DISK_FULL, 0);
    }

    private record SyncResult(int itemsSynced, long bytesSynced) {
        static final SyncResult EMPTY = new SyncResult(0, 0);
    }
}
