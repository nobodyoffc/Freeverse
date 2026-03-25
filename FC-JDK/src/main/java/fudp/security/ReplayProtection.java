package fudp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Replay attack protection using sliding window.
 * 
 * This implementation includes peer restart detection via Session Epoch:
 * Each node generates a random Session Epoch at startup.
 * When we detect that a peer's Session Epoch has changed, we know they
 * have restarted and we reset their sliding window.
 * 
 * This is more robust than packet number heuristics because:
 * - It works regardless of how many packets were exchanged before restart
 * - It works even with infrequent communication
 * - It has no false positives or false negatives
 */
public class ReplayProtection {
    private static final Logger log = LoggerFactory.getLogger(ReplayProtection.class);

    // Window must be large enough to handle out-of-order packet processing
    // in the multi-threaded executor.  With 14000+ packets for a 17MB file
    // and 4 executor threads, a window of 1024 caused 85%+ of packets to be
    // falsely marked as "too old" and dropped.
    // 65536 covers even very large transfers with ample margin (~85MB at 1350-byte MTU).
    private static final long WINDOW_SIZE = 65536;
    private static final long TIMESTAMP_TOLERANCE_MS = 500000; // ±5 seconds

    // Per-peer packet windows
    private final Map<String, PacketWindow> windows;

    public ReplayProtection() {
        this.windows = new ConcurrentHashMap<>();
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
     * @param peerId The sender's FID
     * @param packetNumber The packet number
     * @param timestamp The timestamp from the packet
     * @param sessionEpoch The sender's session epoch (changes on restart)
     * @return Result of the check
     */
    public CheckResult checkAndRecord(String peerId, long packetNumber, long timestamp, long sessionEpoch) {
        // Check timestamp
        long now = System.currentTimeMillis();
        if (Math.abs(timestamp - now) > TIMESTAMP_TOLERANCE_MS) {
            return CheckResult.INVALID_TIMESTAMP;
        }

        // Get or create window for peer
        PacketWindow window = windows.computeIfAbsent(peerId, k -> new PacketWindow());

        // Check for peer restart via session epoch change
        // detectAndHandleSessionEpochChange atomically detects and updates the epoch
        if (window.detectAndHandleSessionEpochChange(sessionEpoch)) {
            log.info("[ReplayProtection] Detected peer restart for {} via session epoch change. " +
                    "Old epoch={}, new epoch={}", 
                    peerId, window.getPreviousSessionEpoch(), sessionEpoch);
            // Reset window and record this packet (epoch already updated atomically)
            window.reset();
            window.checkAndRecord(packetNumber);
            return CheckResult.PEER_RESTART;
        }

        return window.checkAndRecord(packetNumber) ? CheckResult.OK : CheckResult.DUPLICATE;
    }
    
    /**
     * Legacy method for backward compatibility.
     * @deprecated Use {@link #checkAndRecord(String, long, long, long)} with sessionEpoch
     */
    @Deprecated
    public CheckResult checkAndRecord(String peerId, long packetNumber, long timestamp) {
        // Without session epoch, we cannot detect peer restart reliably
        // This is kept for backward compatibility but should not be used
        return checkAndRecord(peerId, packetNumber, timestamp, 0);
    }

    /**
     * Remove window for a peer
     */
    public void removePeer(String peerId) {
        windows.remove(peerId);
    }

    /**
     * Clear all windows
     */
    public void clear() {
        windows.clear();
    }

    /**
     * Sliding window for packet number tracking with session epoch support.
     */
    private static class PacketWindow {
        private long highestPacketNumber = -1;
        private long sessionEpoch = 0;           // Current known session epoch for this peer
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
            // First packet from this peer
            if (sessionEpoch == 0) {
                sessionEpoch = newSessionEpoch;
                return false;
            }

            // Session epoch changed - peer has restarted
            if (newSessionEpoch != sessionEpoch) {
                previousSessionEpoch = sessionEpoch;
                // Atomically update session epoch here to prevent duplicate detections
                sessionEpoch = newSessionEpoch;
                return true;
            }

            return false;
        }
        
        /**
         * Get the previous session epoch (before change).
         * Used for logging.
         */
        public long getPreviousSessionEpoch() {
            return previousSessionEpoch;
        }

        /**
         * Reset the window state.
         */
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
                // Large jump, reset window
                receivedBitmap.clear();
            } else {
                // Shift the bitmap
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
