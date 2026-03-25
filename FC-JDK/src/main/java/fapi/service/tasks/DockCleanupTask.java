package fapi.service.tasks;

import fapi.components.DockComponent;
import fapi.service.BlockEventDispatcher.BlockTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Periodic cleanup task for expired DOCK items.
 * <p>
 * Registered as a BlockTask so it runs based on blockchain height changes.
 * Skips execution until at least {@code cleanupInterval} blocks have passed
 * since the last cleanup, then delegates to {@link DockComponent#cleanupExpiredItems(long)}.
 */
public class DockCleanupTask implements BlockTask {
    private static final Logger log = LoggerFactory.getLogger(DockCleanupTask.class);

    public static final String TASK_NAME = "DockCleanupTask";
    public static final int PRIORITY = 200;
    public static final long DEFAULT_CLEANUP_INTERVAL = 1440; // ~1 day in blocks

    private final DockComponent dockComponent;
    private final long cleanupInterval;
    private long lastCleanupHeight = 0;

    public DockCleanupTask(DockComponent dockComponent, long cleanupInterval) {
        this.dockComponent = dockComponent;
        this.cleanupInterval = cleanupInterval > 0 ? cleanupInterval : DEFAULT_CLEANUP_INTERVAL;
    }

    @Override
    public String getName() {
        return TASK_NAME;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public void execute(long currentHeight, long previousHeight) {
        try {
            if (lastCleanupHeight == 0) {
                lastCleanupHeight = currentHeight;
                log.info("DockCleanupTask: initialized lastCleanupHeight to {}", currentHeight);
                return;
            }

            if (currentHeight - lastCleanupHeight < cleanupInterval) {
                return;
            }

            long deleted = dockComponent.cleanupExpiredItems(currentHeight);
            lastCleanupHeight = currentHeight;

            if (deleted > 0) {
                log.info("DockCleanupTask: deleted {} expired items at height {}", deleted, currentHeight);
            }
        } catch (Exception e) {
            log.error("DockCleanupTask error", e);
        }
    }

    public long getCleanupInterval() {
        return cleanupInterval;
    }
}
