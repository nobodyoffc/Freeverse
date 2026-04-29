package fudp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Replay attack protection using sliding window.
 * <p>
 * Windows are keyed by connectionId (not peerId) to support multiple
 * simultaneous connections from the same FID. Each connection has its own
 * independent packet number sequence starting from 0, so replay detection
 * must be scoped per connection.
 * <p>
 * This implementation includes peer restart detection via Session Epoch:
 * Each node generates a random Session Epoch at startup.
 * When we detect that a connection's Session Epoch has changed, we know
 * the peer has restarted and we reset that connection's sliding window.
 */
public class ReplayProtection {
    private static final Logger log = LoggerFactory.getLogger(ReplayProtection.class);

    // Window must be large enough to handle out-of-order packet processing
    // in the multi-threaded executor.  With 14000+ packets for a 17MB file
    // and 4 executor threads, a window of 1024 caused 85%+ of packets to be
    // falsely marked as "too old" and dropped.
    // 65536 covers even very large transfers with ample margin (~85MB at 1350-byte MTU).
    private static final long WINDOW_SIZE = 65536;

    /** Default timestamp tolerance: ±60 seconds. Generous for NTP-synced
     *  hosts and mobile devices, tight enough that an attacker cannot replay
     *  a captured packet 8+ minutes later (the previous 500 s window). */
    public static final long DEFAULT_TIMESTAMP_TOLERANCE_MS = 60_000L;

    /** Minimum allowed tolerance (1 second) — tighter than this is too tight
     *  to handle even brief clock jitter between peers. */
    public static final long MIN_TIMESTAMP_TOLERANCE_MS = 1_000L;

    /** Maximum allowed tolerance (1 hour) — beyond this, timestamp-based
     *  replay protection becomes meaningless. */
    public static final long MAX_TIMESTAMP_TOLERANCE_MS = 3_600_000L;

    /** Default global cap on the number of replay-protection windows.
     *  Each window allocates a 65 536-bit BitSet (~8 KB) so 4 096 windows ≈ 32 MB.
     *  Past this cap, the least recently touched window is evicted. */
    public static final int DEFAULT_MAX_WINDOWS = 4096;

    // Per-connection packet windows (keyed by connectionId), bounded LRU.
    // We use access-ordered LinkedHashMap under a lock rather than
    // ConcurrentHashMap so eviction order is well-defined. The receive path
    // is single-threaded (Protocol.receiveLoop), so contention is minimal.
    private final LinkedHashMap<Long, PacketWindow> windows;
    private final int maxWindows;
    private final long timestampToleranceMs;
    private final AtomicLong evictionCount = new AtomicLong();

    public ReplayProtection() {
        this(DEFAULT_MAX_WINDOWS, DEFAULT_TIMESTAMP_TOLERANCE_MS);
    }

    public ReplayProtection(int maxWindows) {
        this(maxWindows, DEFAULT_TIMESTAMP_TOLERANCE_MS);
    }

    public ReplayProtection(int maxWindows, long timestampToleranceMs) {
        if (maxWindows <= 0) {
            throw new IllegalArgumentException("maxWindows must be positive");
        }
        if (timestampToleranceMs < MIN_TIMESTAMP_TOLERANCE_MS
                || timestampToleranceMs > MAX_TIMESTAMP_TOLERANCE_MS) {
            throw new IllegalArgumentException(
                    "timestampToleranceMs must be between "
                    + MIN_TIMESTAMP_TOLERANCE_MS + " and " + MAX_TIMESTAMP_TOLERANCE_MS
                    + " (got " + timestampToleranceMs + ")");
        }
        this.maxWindows = maxWindows;
        this.timestampToleranceMs = timestampToleranceMs;
        // accessOrder=true → get() promotes entries; eldest is the LRU window.
        this.windows = new LinkedHashMap<>(16, 0.75f, true);
    }

    /** Active timestamp tolerance in ms (for monitoring/tests). */
    public long getTimestampToleranceMs() {
        return timestampToleranceMs;
    }

    /** Number of windows evicted under memory pressure (for monitoring). */
    public long getEvictionCount() {
        return evictionCount.get();
    }

    /** Current number of tracked windows (for monitoring). */
    public synchronized int getWindowCount() {
        return windows.size();
    }

    public enum CheckResult {
        OK,
        DUPLICATE,
        INVALID_TIMESTAMP,
        PEER_RESTART  // Indicates peer restart detected via session epoch change
    }

    /**
     * Check if a packet is valid (not a replay) and record it.
     * Detects peer restart when session epoch changes.
     *
     * @param connectionId The connection ID (unique per connection, not per peer)
     * @param packetNumber The packet number
     * @param timestamp    The timestamp from the packet
     * @param sessionEpoch The sender's session epoch (changes on restart)
     * @return Result of the check
     */
    public CheckResult checkAndRecord(long connectionId, long packetNumber, long timestamp, long sessionEpoch) {
        // Check timestamp
        long now = System.currentTimeMillis();
        if (Math.abs(timestamp - now) > timestampToleranceMs) {
            return CheckResult.INVALID_TIMESTAMP;
        }

        // Get or create window for this connection (bounded LRU).
        PacketWindow window = getOrCreateWindow(connectionId);

        // Check for peer restart via session epoch change
        if (window.detectAndHandleSessionEpochChange(sessionEpoch)) {
            log.info("[ReplayProtection] Detected peer restart for connection {} via session epoch change. " +
                            "Old epoch={}, new epoch={}",
                    connectionId, window.getPreviousSessionEpoch(), sessionEpoch);
            // Window already reset atomically inside detectAndHandleSessionEpochChange()
            window.checkAndRecord(packetNumber);
            return CheckResult.PEER_RESTART;
        }

        return window.checkAndRecord(packetNumber) ? CheckResult.OK : CheckResult.DUPLICATE;
    }

    /**
     * Remove window for a single connection.
     */
    public synchronized void removeConnection(long connectionId) {
        windows.remove(connectionId);
    }

    /**
     * Remove windows for all connections of a peer.
     *
     * @param connectionIds the connection IDs belonging to the peer
     */
    public synchronized void removeAllForPeer(Collection<Long> connectionIds) {
        for (Long connId : connectionIds) {
            windows.remove(connId);
        }
    }

    /**
     * Clear all windows
     */
    public synchronized void clear() {
        windows.clear();
    }

    /**
     * Get an existing window (promoting it in LRU order) or create a new one,
     * evicting the eldest entry if the cap is exceeded.
     */
    private synchronized PacketWindow getOrCreateWindow(long connectionId) {
        PacketWindow w = windows.get(connectionId); // promotes in access order
        if (w != null) {
            return w;
        }
        if (windows.size() >= maxWindows) {
            // Evict eldest (LRU)
            Iterator<Map.Entry<Long, PacketWindow>> it = windows.entrySet().iterator();
            if (it.hasNext()) {
                Map.Entry<Long, PacketWindow> eldest = it.next();
                it.remove();
                long n = evictionCount.incrementAndGet();
                if (n <= 5 || n % 1000 == 0) {
                    log.warn("[ReplayProtection] Evicted LRU window for connectionId={} (count={}, cap={})",
                            eldest.getKey(), n, maxWindows);
                }
            }
        }
        w = new PacketWindow();
        windows.put(connectionId, w);
        return w;
    }

    /**
     * Sliding window for packet number tracking with session epoch support.
     */
    private static class PacketWindow {
        private long highestPacketNumber = -1;
        private long sessionEpoch = 0;           // Current known session epoch
        private long previousSessionEpoch = 0;   // For logging
        private final BitSet receivedBitmap;

        public PacketWindow() {
            this.receivedBitmap = new BitSet((int) WINDOW_SIZE);
        }

        /**
         * Detect if the peer has restarted based on session epoch change.
         * If restart is detected, atomically updates the session epoch to prevent
         * duplicate detections in multi-threaded scenarios.
         *
         * @param newSessionEpoch The session epoch from the received packet
         * @return true if peer restart was detected (session epoch changed)
         */
        public synchronized boolean detectAndHandleSessionEpochChange(long newSessionEpoch) {
            // First packet on this connection
            if (sessionEpoch == 0) {
                sessionEpoch = newSessionEpoch;
                return false;
            }

            // Session epoch changed - peer has restarted
            // Reset bitmap atomically with epoch update to prevent race
            if (newSessionEpoch != sessionEpoch) {
                previousSessionEpoch = sessionEpoch;
                sessionEpoch = newSessionEpoch;
                reset();
                return true;
            }

            return false;
        }

        public long getPreviousSessionEpoch() {
            return previousSessionEpoch;
        }

        public synchronized void reset() {
            highestPacketNumber = -1;
            receivedBitmap.clear();
        }

        public synchronized boolean checkAndRecord(long packetNumber) {
            if (packetNumber < 0) {
                return false;
            }

            // First packet
            if (highestPacketNumber < 0) {
                highestPacketNumber = packetNumber;
                receivedBitmap.set(0);
                return true;
            }

            // Packet too old (before window)
            if (packetNumber <= highestPacketNumber - WINDOW_SIZE) {
                return false;
            }

            // Packet within current window
            if (packetNumber <= highestPacketNumber) {
                int offset = (int) (highestPacketNumber - packetNumber);
                if (receivedBitmap.get(offset)) {
                    return false; // Already received (replay)
                }
                receivedBitmap.set(offset);
                return true;
            }

            // New highest packet - slide window
            long shift = packetNumber - highestPacketNumber;

            if (shift >= WINDOW_SIZE) {
                receivedBitmap.clear();
            } else {
                for (int i = (int) (WINDOW_SIZE - 1); i >= shift; i--) {
                    receivedBitmap.set(i, receivedBitmap.get((int) (i - shift)));
                }
                for (int i = 0; i < shift; i++) {
                    receivedBitmap.clear(i);
                }
            }

            highestPacketNumber = packetNumber;
            receivedBitmap.set(0);
            return true;
        }

        public long getHighestPacketNumber() {
            return highestPacketNumber;
        }
    }
}
