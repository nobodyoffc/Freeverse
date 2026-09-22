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
        // A number already here keeps its ORIGINAL receive time.
        //
        // putIfAbsent, not put: the retention prune in
        // generateAckFrame() walks entries in key order and breaks at
        // the first one newer than the cutoff, which is correct only
        // while receive times ascend with packet numbers. Refreshing an
        // existing entry parked a recent time at a low key and stopped
        // the prune there — permanently, and taking the MAX_RETAINED
        // check with it, since that test is inside the same loop.
        //
        // The retention window asks how long ago we first saw a number,
        // which a second copy of it does not change.
        if (receivedPackets.putIfAbsent(packetNumber, System.currentTimeMillis()) == null) {
            newSinceLastAck++;
        }

        if (packetNumber > largestReceived) {
            largestReceived = packetNumber;
        }
    }

    /**
     * Record a received packet number that does not elicit an ACK (ACK-only,
     * DATAGRAM-only, or a mix of the two).
     * <p>
     * It is listed in the next ACK frame sent for other reasons, so the ranges
     * have holes only where packets were really lost, but it never triggers an
     * ACK on its own. The sender ignores the number (it does not track such
     * packets).
     * <p>
     * Leaving these out put a hole in the ranges for every packet the peer
     * sent without eliciting an ACK. With data flowing both ways that is
     * every other packet: ACK frames hit MAX_RANGES_PER_FRAME (264 bytes)
     * while covering only the last ~250 packet numbers, so the retention
     * window that protects against lost ACKs shrank to a fraction of a second.
     */
    public synchronized void onNonElicitingPacketReceived(long packetNumber) {
        receivedPackets.putIfAbsent(packetNumber, System.currentTimeMillis());
        if (packetNumber > largestReceived) {
            largestReceived = packetNumber;
        }
        // No ACK frame may follow for a long time (a receive-only datagram
        // flow), and pruning otherwise happens only when one is generated.
        pruneRetained(System.currentTimeMillis());
    }

    /**
     * Check if ACK should be sent immediately
     */
    public synchronized boolean shouldSendAckImmediately() {
        return newSinceLastAck >= ACK_THRESHOLD;
    }

    /**
     * Generate an ACK frame covering the retained packet numbers, newest
     * first, encoded in at most {@code maxBytes}. Ranges that do not fit are
     * left out, oldest first; they are still retained and re-advertised by
     * later frames.
     *
     * @return null when nothing new arrived since the last generated frame
     *         (retained-but-already-acked numbers alone don't warrant a new
     *         frame), or when not even the newest range fits in
     *         {@code maxBytes} — the ACK then stays pending for a caller with
     *         more room
     */
    public synchronized AckFrame generateAckFrame(int maxBytes) {
        if (newSinceLastAck == 0 || receivedPackets.isEmpty()) return null;

        long now = System.currentTimeMillis();
        pruneRetained(now);

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

        AckFrame frame = new AckFrame(sorted.get(0), ackDelayUs, ranges);
        while (frame.getSize() > maxBytes && ranges.size() > 1) {
            ranges.remove(ranges.size() - 1);
            frame = new AckFrame(sorted.get(0), ackDelayUs, ranges);
        }
        if (frame.getSize() > maxBytes) return null;

        newSinceLastAck = 0;
        firstPendingAckTime = 0;
        return frame;
    }

    /** Prune entries past the retention window / memory cap (oldest first). */
    private void pruneRetained(long now) {
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
