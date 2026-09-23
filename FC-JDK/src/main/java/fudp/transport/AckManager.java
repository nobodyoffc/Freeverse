package fudp.transport;

import fudp.connection.PeerConnection;
import fudp.packet.frames.AckFrame;
import fudp.util.Varint;

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
    /**
     * Received packet numbers, ascending, with their receive times in
     * lockstep; live entries are {@code [head, tail)}.
     * <p>
     * Plain arrays rather than a TreeMap because an ACK frame is generated
     * for every packet received, and each one used to copy the whole
     * retained set ({@code new ArrayList<>(descendingKeySet())}, boxed) and
     * then walk it. Random access makes both the retention prune and the run
     * detection in {@link #generateAckFrame(int)} cheap.
     */
    private long[] packetNumbers = new long[1024];
    private long[] receiveTimes = new long[1024];
    /** Index of the oldest retained entry; everything before it is pruned. */
    private int head = 0;
    /** One past the newest retained entry. */
    private int tail = 0;
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
        if (record(packetNumber, System.currentTimeMillis())) {
            newSinceLastAck++;
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
        long now = System.currentTimeMillis();
        record(packetNumber, now);
        // No ACK frame may follow for a long time (a receive-only datagram
        // flow), and pruning otherwise happens only when one is generated.
        pruneRetained(now);
    }

    /**
     * Insert a packet number in ascending order.
     * <p>
     * A number already retained keeps its ORIGINAL receive time. The
     * retention prune below drops from the front and stops at the first
     * entry newer than the cutoff, which is correct only while receive times
     * ascend with packet numbers. Refreshing an existing entry parked a
     * recent time at a low index and stopped the prune there — permanently,
     * and taking the MAX_RETAINED check with it. The retention window asks
     * how long ago we first saw a number, which a second copy does not change.
     *
     * @return true if it was not already retained
     */
    private boolean record(long packetNumber, long now) {
        if (packetNumber > largestReceived) {
            largestReceived = packetNumber;
        }
        if (tail == head || packetNumber > packetNumbers[tail - 1]) {
            ensureRoom();
            packetNumbers[tail] = packetNumber;
            receiveTimes[tail] = now;
            tail++;
            return true;
        }
        // Out of order or duplicate: binary search for its place.
        int lo = head, hi = tail;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (packetNumbers[mid] < packetNumber) lo = mid + 1; else hi = mid;
        }
        if (lo < tail && packetNumbers[lo] == packetNumber) {
            return false;
        }
        ensureRoom();
        System.arraycopy(packetNumbers, lo, packetNumbers, lo + 1, tail - lo);
        System.arraycopy(receiveTimes, lo, receiveTimes, lo + 1, tail - lo);
        packetNumbers[lo] = packetNumber;
        receiveTimes[lo] = now;
        tail++;
        return true;
    }

    private void ensureRoom() {
        if (tail < packetNumbers.length) return;
        if (head > 0) {
            compact();
            if (tail < packetNumbers.length) return;
        }
        packetNumbers = Arrays.copyOf(packetNumbers, packetNumbers.length * 2);
        receiveTimes = Arrays.copyOf(receiveTimes, receiveTimes.length * 2);
    }

    /** Move the live entries to the front, dropping the pruned prefix. */
    private void compact() {
        int live = tail - head;
        System.arraycopy(packetNumbers, head, packetNumbers, 0, live);
        System.arraycopy(receiveTimes, head, receiveTimes, 0, live);
        head = 0;
        tail = live;
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
        if (newSinceLastAck == 0 || tail == head) return null;

        long now = System.currentTimeMillis();
        pruneRetained(now);

        // Compute actual ACK delay in microseconds
        long ackDelayUs = (firstPendingAckTime > 0)
            ? (now - firstPendingAckTime) * 1000
            : 0;

        // Fold the (ascending) numbers into descending (gap, length) ranges.
        // Each run of consecutive numbers is found by binary search rather
        // than walked: within a run {@code packetNumbers[j] - j} is constant,
        // and it never decreases across the ascending array, so a run's first
        // index is the first one with that difference. Walking cost
        // O(retained) per ACK — and an ACK is generated for every packet.
        List<AckFrame.AckRange> ranges = new ArrayList<>();
        int i = tail - 1;
        long prevLow = -1;
        while (i >= head && ranges.size() < MAX_RANGES_PER_FRAME) {
            long high = packetNumbers[i];
            long key = high - i;
            int lo = head, hi = i;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (packetNumbers[mid] - mid < key) lo = mid + 1; else hi = mid;
            }
            long low = packetNumbers[lo];
            long gap = ranges.isEmpty() ? 0 : prevLow - high - 2;
            ranges.add(new AckFrame.AckRange(gap, high - low));
            prevLow = low;
            i = lo - 1;
        }

        long largest = packetNumbers[tail - 1];
        // Trim the oldest ranges against a running total: this runs for every
        // ACK, so the frame is measured once, not rebuilt per range dropped.
        int size = new AckFrame(largest, ackDelayUs, ranges).getSize();
        while (size > maxBytes && ranges.size() > 1) {
            AckFrame.AckRange dropped = ranges.remove(ranges.size() - 1);
            size -= Varint.encodedLength(dropped.gap) + Varint.encodedLength(dropped.length);
            size += Varint.encodedLength(ranges.size()) - Varint.encodedLength(ranges.size() + 1);
        }
        if (size > maxBytes) return null;

        newSinceLastAck = 0;
        firstPendingAckTime = 0;
        return new AckFrame(largest, ackDelayUs, ranges);
    }

    /** Prune entries past the retention window / memory cap (oldest first). */
    private void pruneRetained(long now) {
        long cutoff = now - ACK_RETAIN_MS;
        while (tail - head > 1
                && (receiveTimes[head] < cutoff || tail - head > MAX_RETAINED)) {
            head++;
        }
        // Reclaim once the pruned prefix outgrows the live part. Dropping
        // entry by entry (the old TreeMap iterator) moved every retained
        // number on each ACK-only packet received.
        if (head > 1024 && head * 2 > tail) {
            compact();
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
        head = 0;
        tail = 0;
        newSinceLastAck = 0;
        largestReceived = -1;
        firstPendingAckTime = 0;
    }
}
