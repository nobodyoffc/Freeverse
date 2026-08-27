package fudp.transport;

import fudp.connection.PeerConnection;
import fudp.packet.frames.AckFrame;

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages ACK generation and tracking.
 * <p>
 * ACK frames are carried in non-ack-eliciting packets that are never
 * retransmitted, so an acknowledgment must not depend on any single ACK frame
 * arriving. Every generated ACK frame therefore re-advertises ALL recently
 * received packet numbers (QUIC-style ranges), retained for {@link #ACK_RETAIN_MS}:
 * if one ACK packet is lost, the next one still covers the same packet numbers.
 * Without this redundancy, a lost ACK permanently orphans the packets it
 * covered — the sender falsely declares them lost after its loss-detection
 * threshold, retransmits them and collapses its congestion window, which in the
 * field pinned uploads at ~35KB/s on a 1MB/s WAN path.
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

    /**
     * How long a received packet number keeps being re-advertised in ACK frames.
     * Must comfortably exceed the sender's loss-detection threshold (default
     * max(500ms, 2*RTT)) plus one RTT, so that even if several consecutive ACK
     * packets are lost, a later ACK still reaches the sender before it falsely
     * declares the covered packets lost.
     */
    private static final long ACK_RETAIN_MS = 4000;

    /** Bound on retained packet numbers (memory cap; ~4000 pkt/s * 4s). */
    private static final int MAX_RETAINED = 16384;

    /** Bound on ranges encoded per frame (a healthy link produces 1-2 ranges). */
    private static final int MAX_RANGES_PER_FRAME = 128;

    private final PeerConnection connection;
    /** Received packet number -> receive timestamp (ms), for retention pruning. */
    private final TreeMap<Long, Long> receivedPackets = new TreeMap<>();
    /** Count of packets received since the last generated ACK frame. */
    private int newSinceLastAck = 0;
    private long largestReceived = -1;
    private long firstPendingAckTime = 0;

    public AckManager(PeerConnection connection) {
        this.connection = connection;
    }

    /**
     * Record a received packet number
     */
    public synchronized void onPacketReceived(long packetNumber) {
        if (newSinceLastAck == 0) {
            firstPendingAckTime = System.currentTimeMillis();
        }
        if (receivedPackets.put(packetNumber, System.currentTimeMillis()) == null) {
            newSinceLastAck++;
        }

        if (packetNumber > largestReceived) {
            largestReceived = packetNumber;
        }
    }

    /**
     * Check if ACK should be sent immediately
     */
    public synchronized boolean shouldSendAckImmediately() {
        return newSinceLastAck >= ACK_THRESHOLD;
    }

    /**
     * Generate an ACK frame covering all retained packet numbers.
     * Returns null when nothing new arrived since the last generated frame
     * (retained-but-already-acked numbers alone don't warrant a new frame).
     */
    public synchronized AckFrame generateAckFrame() {
        if (newSinceLastAck == 0 || receivedPackets.isEmpty()) return null;

        long now = System.currentTimeMillis();

        // Prune entries past the retention window / memory cap (oldest first).
        long cutoff = now - ACK_RETAIN_MS;
        Iterator<Map.Entry<Long, Long>> it = receivedPackets.entrySet().iterator();
        while (it.hasNext() && receivedPackets.size() > 1) {
            Map.Entry<Long, Long> e = it.next();
            if (e.getValue() < cutoff || receivedPackets.size() > MAX_RETAINED) {
                it.remove();
            } else {
                break; // TreeMap keys ascend with time in practice; stop at first keeper
            }
        }

        // Compute actual ACK delay in microseconds
        long ackDelayUs = (firstPendingAckTime > 0)
            ? (now - firstPendingAckTime) * 1000
            : 0;

        List<Long> sorted = new ArrayList<>(receivedPackets.descendingKeySet());

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
                if (ranges.size() >= MAX_RANGES_PER_FRAME) break;
                long length = currentHigh - currentLow;
                long gap = ranges.isEmpty() ? 0 : prevLow - currentHigh - 2;
                ranges.add(new AckFrame.AckRange(gap, length));
                prevLow = currentLow;
                currentHigh = pn;
                currentLow = pn;
            }
        }
        // Add final range
        if (ranges.size() < MAX_RANGES_PER_FRAME) {
            long length = currentHigh - currentLow;
            long gap = ranges.isEmpty() ? 0 : prevLow - currentHigh - 2;
            ranges.add(new AckFrame.AckRange(gap, length));
        }

        newSinceLastAck = 0;
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
    public synchronized boolean hasPendingAcks() {
        return newSinceLastAck > 0;
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
        receivedPackets.clear();
        newSinceLastAck = 0;
        largestReceived = -1;
        firstPendingAckTime = 0;
    }
}
