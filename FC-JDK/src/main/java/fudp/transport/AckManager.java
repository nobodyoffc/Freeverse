package fudp.transport;

import fudp.connection.PeerConnection;
import fudp.packet.frames.AckFrame;

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages ACK generation and tracking
 */
public class AckManager {

    /** Maximum ACK delay in milliseconds before sending. Reduced for low latency. */
    private static final long MAX_ACK_DELAY_MS = 10;
    
    /** 
     * Number of packets to receive before sending immediate ACK.
     * Set to 1 for lowest latency (immediate ACK on every packet).
     * Set to 2 for QUIC-like behavior (better for bulk transfers).
     */
    private static final int ACK_THRESHOLD = 1;

    private final PeerConnection connection;
    private final TreeSet<Long> pendingAcks;
    private long largestReceived = -1;
    private long firstPendingAckTime = 0;
    private ScheduledFuture<?> ackTimer;

    public AckManager(PeerConnection connection) {
        this.connection = connection;
        this.pendingAcks = new TreeSet<>();
    }

    /**
     * Record a received packet number
     */
    public synchronized void onPacketReceived(long packetNumber) {
        if (pendingAcks.isEmpty()) {
            firstPendingAckTime = System.currentTimeMillis();
        }
        pendingAcks.add(packetNumber);

        if (packetNumber > largestReceived) {
            largestReceived = packetNumber;
        }
    }

    /**
     * Check if ACK should be sent immediately
     */
    public boolean shouldSendAckImmediately() {
        return pendingAcks.size() >= ACK_THRESHOLD;
    }

    /**
     * Generate an ACK frame
     */
    public synchronized AckFrame generateAckFrame() {
        if (pendingAcks.isEmpty() || largestReceived < 0) return null;

        // Compute actual ACK delay in microseconds
        long ackDelayUs = (firstPendingAckTime > 0)
            ? (System.currentTimeMillis() - firstPendingAckTime) * 1000
            : 0;

        List<Long> sorted = new ArrayList<>(pendingAcks);
        sorted.sort(Collections.reverseOrder());

        List<AckFrame.AckRange> ranges = new ArrayList<>();
        long currentHigh = sorted.get(0);
        long currentLow = currentHigh;
        long prevLow = -1;

        for (int i = 1; i < sorted.size(); i++) {
            long pn = sorted.get(i);
            if (currentLow - pn == 1) {
                // Consecutive packet, extend current range
                currentLow = pn;
            } else {
                // Gap found — finalize current range and start new one
                long length = currentHigh - currentLow;
                long gap = ranges.isEmpty() ? 0 : prevLow - currentHigh - 2;
                ranges.add(new AckFrame.AckRange(gap, length));
                prevLow = currentLow;
                currentHigh = pn;
                currentLow = pn;
            }
        }
        // Add final range
        long length = currentHigh - currentLow;
        long gap = ranges.isEmpty() ? 0 : prevLow - currentHigh - 2;
        ranges.add(new AckFrame.AckRange(gap, length));

        pendingAcks.clear();
        firstPendingAckTime = 0;
        return new AckFrame(sorted.get(0), ackDelayUs, ranges);
    }

    /**
     * Get maximum ACK delay
     */
    public long getMaxAckDelay() {
        return MAX_ACK_DELAY_MS;
    }

    /**
     * Check if there are pending ACKs
     */
    public boolean hasPendingAcks() {
        return !pendingAcks.isEmpty();
    }

    /**
     * Get largest received packet number
     */
    public long getLargestReceived() {
        return largestReceived;
    }

    /**
     * Reset ACK tracking after peer restart.
     */
    public synchronized void resetForRestart() {
        pendingAcks.clear();
        largestReceived = -1;
        firstPendingAckTime = 0;
    }
}
