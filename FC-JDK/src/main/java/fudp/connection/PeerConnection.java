package fudp.connection;

import fudp.packet.Frame;
import fudp.packet.frames.AckFrame;
import fudp.packet.frames.StreamFrame;
import fudp.stream.Stream;
import fudp.stream.StreamManager;
import fudp.transport.*;
import fudp.congestion.CongestionControl;
import fudp.congestion.RttEstimator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a connection to a peer.
 *
 * Simplified version without symmetric key session management.
 * All encryption uses AsyTwoWay (ECDH) mode.
 */
public class PeerConnection {
    private static final Logger log = LoggerFactory.getLogger(PeerConnection.class);

    private final String peerId;           // Peer FID
    private byte[] peerPublicKey;          // Peer public key
    private SocketAddress peerAddress;
    private ConnectionState state;
    private final long connectionId;

    // Packet number management
    private long nextPacketNumber = 0;

    // Gap-based loss detection counts TRACKED packets only. Packet numbers are
    // also spent on packets that are never tracked (ACK-only, DATAGRAM-only),
    // and the peer may list those in its ACKs; measuring the gap in packet
    // numbers let a burst of untracked ones make an in-flight packet look
    // lost, and an ACK for an untracked number count as evidence against it.
    private final java.util.concurrent.atomic.AtomicLong nextTrackedSeq =
            new java.util.concurrent.atomic.AtomicLong();
    private volatile long largestAckedTrackedSeq = -1;

    // Sent packets tracking
    private final Map<Long, SentPacket> sentPackets;

    // Stream management
    private final StreamManager streamManager;

    // DATAGRAM frames (FUDP7): off until the application learns the peer
    // supports them, since an older peer loses every packet carrying one.
    private volatile boolean datagramsEnabled = false;
    private final DatagramBudget datagramBudget = new DatagramBudget(DatagramBudget.DEFAULT_RATE_BPS);

    // Transport layer
    private final AckManager ackManager;
    private final RttEstimator rttEstimator;
    private final CongestionControl congestionControl;

    // Timestamps
    private final Instant createdAt;
    private volatile Instant lastActivity;

    // Statistics
    private long packetsSent = 0;
    private long packetsReceived = 0;
    private long bytesOut = 0;
    private long bytesIn = 0;
    private long retransmitCount = 0;           // 重传次数
    private long suspectedLostCount = 0;        // 疑似丢失（超时检测到的）
    private long confirmedLostCount = 0;        // 确认丢失（重传后仍未收到ACK）
    private long ackedAfterSuspectedLost = 0;   // 疑似丢失后收到ACK的（误判）
    
    // Peer restart handling flag (to avoid duplicate processing in multi-threaded scenarios)
    private volatile boolean peerRestartHandled = false;

    // Session epoch: identifies the remote node's session (changes on restart).
    // Used to detect stale connections when the same FID reconnects with a new epoch.
    private volatile long sessionEpoch = 0;

    // E2: Once the peer has acknowledged our epoch, no need to keep sending it
    private volatile boolean epochConfirmed = false;

    // Per-connection loss signal throttle (moved from Protocol to avoid global throttle)
    private volatile long lastLossSignalTime = 0;

    // The peer's OWN connectionId for this connection, learned from the header
    // of its packets (each side stamps its local id). 0 = not yet learned.
    // Used to recognise the same connection when the peer's source address
    // changes (NAT rebind → path migration) and to detect the peer rebuilding
    // its connection state (fresh packet-number space) without restarting.
    private volatile long remoteConnectionId = 0;

    public PeerConnection(String peerId, SocketAddress address, long connectionId) {
        this(peerId, address, connectionId, 2000);
    }

    public PeerConnection(String peerId, SocketAddress address, long connectionId, long lossDetectionMinThresholdMs) {
        this.peerId = peerId;
        this.peerAddress = address;
        this.connectionId = connectionId;
        this.state = ConnectionState.IDLE;
        this.minTimeThresholdMs = lossDetectionMinThresholdMs;

        this.sentPackets = new ConcurrentHashMap<>();
        this.streamManager = new StreamManager(this);
        this.ackManager = new AckManager(this);
        this.rttEstimator = new RttEstimator();
        this.congestionControl = new CongestionControl();

        this.createdAt = Instant.now();
        this.lastActivity = this.createdAt;
    }

    /**
     * Allocate a new packet number.
     */
    public synchronized long allocatePacketNumber() {
        return nextPacketNumber++;
    }

    /**
     * Record a sent packet for ACK tracking.
     * Note: Only ACK-eliciting packets should be tracked for loss detection.
     * ACK-only packets don't need acknowledgment and should not be counted as lost.
     */
    public void recordSentPacket(long packetNumber, List<Frame> frames, int size, boolean ackEliciting) {
        recordSentPacket(packetNumber, frames, size, ackEliciting, 0);
    }

    /**
     * Record a sent packet, optionally inheriting a retransmit count from a previous attempt.
     */
    public void recordSentPacket(long packetNumber, List<Frame> frames, int size, boolean ackEliciting, int retransmitCount) {
        SentPacket sent = new SentPacket(packetNumber, frames, size, ackEliciting);
        sent.setRetransmitCount(retransmitCount);
        // Only track ACK-eliciting packets for loss detection
        // ACK-only packets don't need acknowledgment and shouldn't be counted as lost
        if (ackEliciting) {
            sent.setTrackedSeq(nextTrackedSeq.getAndIncrement());
            sentPackets.put(packetNumber, sent);
        }
        packetsSent++;
        bytesOut += size;
        lastActivity = Instant.now();
    }

    /**
     * Process received ACK.
     */
    /**
     * Process one ACK frame, given its acknowledged ranges as intervals.
     * <p>
     * <b>It walks the outstanding packets, not the acknowledged numbers.</b>
     * An ACK frame re-advertises every packet number the peer has retained —
     * about 4 seconds of them (FUDP3 §2.1) — while what is outstanding here is
     * bounded by the congestion window. Expanding the frame into a list and
     * looking up each number cost O(retained) per ACK, on every one of the
     * thousands of ACKs a transfer receives each second: the sender fell
     * behind the ACK stream, its RTT estimate climbed past the loss timeout,
     * and it retransmitted packets that had already arrived.
     */
    public void onAckReceived(long largestAcked, long ackDelay, List<AckFrame.AckInterval> intervals) {
        // E2: Peer has responded, so it has seen our epoch — no need to keep sending it
        epochConfirmed = true;
        if (intervals == null || intervals.isEmpty()) return;

        // RTT is sampled when this ACK newly covers a tracked packet sent after
        // every tracked packet acked so far (QUIC's "largest acknowledged is
        // newly acked", restated over tracked packets). largestAcked itself
        // may be a packet we never tracked (DATAGRAM-only), and requiring
        // pn == largestAcked starved the estimator whenever datagrams flowed.
        long previousLargestSeq = largestAckedTrackedSeq;
        SentPacket rttPacket = null;

        List<SentPacket> acked = new ArrayList<>();
        for (Map.Entry<Long, SentPacket> entry : sentPackets.entrySet()) {
            if (covers(intervals, entry.getKey())) {
                acked.add(entry.getValue());
            }
        }
        for (SentPacket sent : acked) {
            sentPackets.remove(sent.packetNumber);
            if (rttPacket == null || sent.getTrackedSeq() > rttPacket.getTrackedSeq()) {
                rttPacket = sent;
            }
            if (sent.getTrackedSeq() > largestAckedTrackedSeq) {
                largestAckedTrackedSeq = sent.getTrackedSeq();
            }

            // Update congestion control
            congestionControl.onAck(sent.size);
        }

        // Packets previously marked as suspected lost but now ACKed were false
        // positives (late ACK, not real loss).
        for (Iterator<Map.Entry<Long, Long>> it = suspectedLostPacketNumbers.entrySet().iterator();
             it.hasNext(); ) {
            Map.Entry<Long, Long> entry = it.next();
            if (!covers(intervals, entry.getKey())) continue;
            it.remove();
            ackedAfterSuspectedLost++;
            // Spurious loss = the path reorders deeper than our current
            // gap threshold assumed. Widen it to the OBSERVED reordering
            // extent (RACK-style adaptation) so it converges in one or two
            // events — heavily load-balanced routes can reorder by dozens
            // of packets, and every misfire needlessly multiplies the
            // congestion window down (~50KB window on a 900KB/s path in
            // the field). The extent includes some retransmit delay, so
            // it over-estimates slightly; the cap bounds the damage and
            // the capped 4s timeout remains the real-loss backstop.
            long extent = largestAckedTrackedSeq - entry.getValue() + 2;
            long widened = Math.min(MAX_PACKET_THRESHOLD,
                    Math.max(packetReorderThreshold + 4, extent));
            if (widened > packetReorderThreshold) {
                packetReorderThreshold = widened;
            }
        }

        if (rttPacket != null && rttPacket.getTrackedSeq() > previousLargestSeq) {
            long rttSample = System.currentTimeMillis() - rttPacket.sentTime;
            rttEstimator.updateRtt(Math.max(1, rttSample - ackDelay / 1000));
        }
    }

    /** Is {@code packetNumber} in one of the (descending, disjoint) intervals? */
    private static boolean covers(List<AckFrame.AckInterval> intervals, long packetNumber) {
        int lo = 0, hi = intervals.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            AckFrame.AckInterval interval = intervals.get(mid);
            if (packetNumber > interval.high) {
                hi = mid - 1;          // intervals descend, so look newer
            } else if (packetNumber < interval.low) {
                lo = mid + 1;
            } else {
                return true;
            }
        }
        return false;
    }

    // Packets marked as suspected lost (for accurate loss tracking):
    // packet number -> its tracked seq, for measuring reordering extent.
    private final Map<Long, Long> suspectedLostPacketNumbers = new ConcurrentHashMap<>();

    // Loss detection configuration
    // Time-based threshold multiplier (RFC 9002 recommends 9/8 = 1.125, but we use more conservative value)
    private static final double TIME_THRESHOLD_MULTIPLIER = 2.0;  // Conservative multiplier
    // Minimum floor for the time-based (timeout) loss threshold; the effective
    // threshold adapts as max(floor, 2*sRTT + 4*RTTvar) so RTT jitter widens it.
    private final long minTimeThresholdMs;
    // Packet reordering threshold for gap-based loss detection: a packet is
    // lost when one sent >= this many packets AFTER it has been ACKed (QUIC
    // kPacketThreshold is 3; we start at 6 for reordering margin). Safe to
    // use now that ACK frames re-advertise all recently received packet
    // numbers: any ACK frame covers everything received so far, so a "late"
    // ACK can no longer make an already-delivered packet look lost.
    // ADAPTIVE: every spurious loss (packet ACKed after being declared lost)
    // widens the threshold, RACK-style, up to MAX_PACKET_THRESHOLD — paths
    // that reorder deeply stop triggering false congestion signals.
    private static final long INITIAL_PACKET_THRESHOLD = 6;
    /** Cap on remembered suspected-lost packet numbers. */
    private static final int MAX_SUSPECTED_LOST = 4096;

    private static final long MAX_PACKET_THRESHOLD = 64;
    private volatile long packetReorderThreshold = INITIAL_PACKET_THRESHOLD;
    // Ceiling for the timeout-based loss threshold (QUIC caps its PTO
    // similarly): tolerate multi-second RTT spikes, but never let loss
    // recovery stall beyond this.
    private static final long MAX_TIME_THRESHOLD_MS = 4000;

    /**
     * Loss detection result: the packets to retransmit, and whether any of
     * them were detected by an ACK GAP (real loss evidence) rather than by
     * timeout alone. Per QUIC RFC 9002 semantics, only gap-detected loss is a
     * congestion signal; timeout-detected "loss" is retransmitted but must NOT
     * shrink the congestion window — on jittery paths (e.g. cross-border
     * links with multi-second RTT spikes) timeouts are routinely spurious, and
     * treating them as congestion pinned the window at its floor.
     */
    public record LossDetection(List<SentPacket> packets, boolean gapLoss) {}

    /**
     * Get packets that need retransmission.
     */
    public LossDetection detectLostPackets() {
        List<SentPacket> lost = new ArrayList<>();
        boolean gapLoss = false;

        // Timeout threshold: clamp(2*sRTT + 4*RTTvar, floor, ceiling). The 4x
        // variance term adapts to jitter so an RTT spike inflates the
        // threshold instead of mass-expiring the whole flight; the ceiling
        // keeps chaotic RTT samples under heavy loss from ballooning the
        // threshold so far that lost packets sit unretransmitted for many
        // seconds — with the window full of them, the sender stalls silently
        // and the peer's idle timers fire.
        long smoothedRtt = rttEstimator.getSmoothedRtt();
        long rttVar = rttEstimator.getRttVariance();
        long timeThreshold = Math.min(MAX_TIME_THRESHOLD_MS, Math.max(minTimeThresholdMs,
                (long) (smoothedRtt * TIME_THRESHOLD_MULTIPLIER) + 4 * rttVar));
        // Gap-detected loss must also be at least ~1 RTT old: reordered
        // packets arrive within an RTT of their peers, while a truly lost
        // packet stays unACKed as later ones get ACKed past it (QUIC RACK
        // time window). Keeps sub-RTT reordering from misfiring as loss.
        long gapMinAge = Math.max(20, smoothedRtt);
        long now = System.currentTimeMillis();

        for (Map.Entry<Long, SentPacket> entry : sentPackets.entrySet()) {
            long pn = entry.getKey();
            SentPacket packet = entry.getValue();

            // Skip non-ACK-eliciting packets (they don't need to be acknowledged)
            if (!packet.ackEliciting) {
                continue;
            }

            long age = now - packet.sentTime;

            // Gap-based (SACK-style): a packet sent well after this one has
            // been ACKed — this one was really dropped. Detects loss within
            // ~1 RTT instead of waiting for the timeout.
            boolean lostByGap = largestAckedTrackedSeq - packet.getTrackedSeq() >= packetReorderThreshold
                    && age > gapMinAge;

            // Time-based (timeout): backstop for tail loss and dead links.
            // Exponential backoff per retransmission (QUIC PTO backoff): a
            // packet that keeps timing out waits 1x, 2x, then 4x the
            // threshold. Under sustained policing, constant-rate blind
            // retransmissions compete with fresh data for the trickle of
            // surviving packets and rack up failed attempts toward
            // abandonment (= permanent stream gap) within seconds. The cap
            // stays moderate: single-packet messages (small responses) have
            // no gap evidence and depend on this timer alone, so aggressive
            // backoff would directly inflate their tail latency.
            long effectiveTimeThreshold = timeThreshold << Math.min(packet.getRetransmitCount(), 2);
            boolean lostByTime = age > effectiveTimeThreshold;

            if (lostByGap || lostByTime) {
                lost.add(packet);
                if (lostByGap) {
                    gapLoss = true;
                }
            }
        }

        // NOTE: Do NOT remove lost packets here. They are removed by the caller
        // (retransmitTask) only after they are actually retransmitted or abandoned.
        // Previously, removing all detected lost packets here caused permanent data loss
        // when the retransmit task was rate-limited and couldn't retransmit all of them.

        return new LossDetection(lost, gapLoss);
    }

    /**
     * Remove a sent packet from tracking after it has been retransmitted or abandoned.
     * 
     * IMPORTANT: This also decrements bytesInFlight for the removed packet.
     * Without this, each retransmission leaks the old packet's size from bytesInFlight
     * (the retransmit sends a NEW packet which adds to bytesInFlight, but the OLD
     * entry's size was never subtracted), eventually causing bytesInFlight to exceed
     * congestionWindow permanently and deadlocking the sender.
     */
    /**
     * Remove a sent packet from tracking for retransmission.
     * @return the removed SentPacket, or null if the packet was already removed (e.g. by an ACK).
     */
    public SentPacket removeSentPacket(long packetNumber) {
        SentPacket removed = sentPackets.remove(packetNumber);
        if (removed != null) {
            // Decrement bytesInFlight for the old packet.
            // Uses onRetransmitRemove (NOT onAck) to avoid congestion window growth.
            // The retransmit will add its own bytes via onSend().
            congestionControl.onRetransmitRemove(removed.size);
            
            suspectedLostCount++;
            suspectedLostPacketNumbers.put(packetNumber, removed.getTrackedSeq());
            trimSuspectedLost();
            if (removed.getRetransmitCount() >= 3) {
                confirmedLostCount++;
            }
        }
        return removed;
    }


    /**
     * Keep the suspected-lost map bounded: an entry is only useful until its
     * ACK arrives, so a long-lived connection losing packets steadily would
     * otherwise remember every packet number it ever suspected. Trimming in
     * batches keeps the cost amortized; dropping the oldest costs at most one
     * threshold widening.
     */
    private void trimSuspectedLost() {
        if (suspectedLostPacketNumbers.size() <= MAX_SUSPECTED_LOST) return;
        List<Long> oldest = new ArrayList<>(suspectedLostPacketNumbers.keySet());
        Collections.sort(oldest);
        int drop = oldest.size() - MAX_SUSPECTED_LOST * 3 / 4;
        for (int i = 0; i < drop && i < oldest.size(); i++) {
            suspectedLostPacketNumbers.remove(oldest.get(i));
        }
    }

    /**
     * Abandon all sent packets that carry frames for the given stream.
     * Stops FUDP-level retransmissions for data that the application no longer
     * cares about (e.g. after an ACK timeout in sendBytesWaitAck).
     *
     * @return number of packets abandoned
     */
    public int abandonPacketsForStream(long streamId) {
        List<Long> toRemove = new ArrayList<>();
        for (Map.Entry<Long, SentPacket> entry : sentPackets.entrySet()) {
            for (Frame frame : entry.getValue().frames) {
                if (frame instanceof StreamFrame sf && sf.getStreamId() == streamId) {
                    toRemove.add(entry.getKey());
                    break;
                }
            }
        }
        for (long pn : toRemove) {
            SentPacket removed = sentPackets.remove(pn);
            if (removed != null) {
                congestionControl.onRetransmitRemove(removed.size);
            }
        }
        return toRemove.size();
    }

    /**
     * Record a retransmission.
     */
    public void recordRetransmit() {
        retransmitCount++;
    }

    /**
     * Get retransmit count.
     */
    public long getRetransmitCount() {
        return retransmitCount;
    }

    /**
     * Get suspected lost packet count (packets that triggered retransmission).
     * Note: This includes false positives (packets that were later ACKed).
     */
    public long getSuspectedLostCount() {
        return suspectedLostCount;
    }

    /**
     * Get confirmed lost packet count (packets that failed after multiple retries).
     */
    public long getConfirmedLostCount() {
        return confirmedLostCount;
    }

    /**
     * Get count of packets that were suspected lost but later ACKed (false positives).
     */
    public long getAckedAfterSuspectedLost() {
        return ackedAfterSuspectedLost;
    }

    /**
     * Get effective lost packet count.
     * This is the real loss: suspected lost minus those that were later ACKed.
     */
    public long getLostPacketCount() {
        return Math.max(0, suspectedLostCount - ackedAfterSuspectedLost);
    }

    /**
     * Calculate current loss rate based on effective lost packets.
     * @return loss rate as a value between 0.0 and 1.0
     */
    public double getLossRate() {
        if (packetsSent == 0) return 0.0;
        return (double) getLostPacketCount() / packetsSent;
    }

    /**
     * Get loss rate as percentage string.
     */
    public String getLossRatePercent() {
        return String.format("%.2f%%", getLossRate() * 100);
    }

    /**
     * Get retransmit rate (retransmits per sent packet).
     * This is a better indicator of network quality than raw loss rate.
     */
    public double getRetransmitRate() {
        if (packetsSent == 0) return 0.0;
        return (double) retransmitCount / packetsSent;
    }

    /**
     * Get retransmit rate as percentage string.
     */
    public String getRetransmitRatePercent() {
        return String.format("%.2f%%", getRetransmitRate() * 100);
    }

    /**
     * Open a new stream.
     */
    public Stream openStream() {
        return streamManager.openStream();
    }

    /**
     * Get a stream by ID.
     */
    public Stream getStream(long streamId) {
        return streamManager.getStream(streamId);
    }

    /**
     * Update peer address (for address migration).
     */
    public void updateAddress(SocketAddress newAddress) {
        this.peerAddress = newAddress;
        this.lastActivity = Instant.now();
    }

    /**
     * Record packet received.
     */
    public void onPacketReceived(int size) {
        packetsReceived++;
        bytesIn += size;
        lastActivity = Instant.now();

        if (state == ConnectionState.IDLE) {
            state = ConnectionState.ESTABLISHING;
        }
    }

    /**
     * Check if connection can send data.
     */
    public boolean canSend(int bytes) {
        return congestionControl.canSend(bytes);
    }

    /**
     * Get congestion window.
     */
    public long getCongestionWindow() {
        return congestionControl.getCongestionWindow();
    }

    /**
     * Reset connection state after peer restart.
     */
    public void resetForPeerRestart() {
        sentPackets.clear();
        streamManager.resetForRestart();
        ackManager.resetForRestart();
        largestAckedTrackedSeq = -1;
        peerRestartHandled = false; // Reset flag for next restart detection
        epochConfirmed = false;     // E2: Must re-send epoch after peer restart
    }

    public boolean isDatagramsEnabled() {
        return datagramsEnabled;
    }

    /**
     * Allow DATAGRAM frames on this connection. Call only once the peer has
     * shown it supports them (FUDP7 capability rules).
     */
    public void setDatagramsEnabled(boolean enabled) {
        this.datagramsEnabled = enabled;
    }

    public DatagramBudget getDatagramBudget() {
        return datagramBudget;
    }

    public long getPacketReorderThreshold() {
        return packetReorderThreshold;
    }

    public long getRemoteConnectionId() {
        return remoteConnectionId;
    }

    public void setRemoteConnectionId(long remoteConnectionId) {
        this.remoteConnectionId = remoteConnectionId;
    }
    
    /**
     * Atomically check and mark peer restart as handled.
     * Used to avoid duplicate processing when multiple threads detect peer restart concurrently.
     * 
     * @return true if this call successfully marked as handled (first caller wins), false if already handled
     */
    public synchronized boolean tryMarkPeerRestartHandled() {
        if (peerRestartHandled) {
            return false;
        }
        peerRestartHandled = true;
        return true;
    }
    
    /**
     * Check if peer restart has been handled.
     */
    public boolean isPeerRestartHandled() {
        return peerRestartHandled;
    }

    /**
     * Throttled loss signal. Returns true if loss was signaled (at most once per second).
     */
    public boolean trySignalLoss() {
        long now = System.currentTimeMillis();
        if (now - lastLossSignalTime > 1000) {
            lastLossSignalTime = now;
            congestionControl.onLoss();
            return true;
        }
        return false;
    }

    // === Rate-based send pacing (QUIC-style leaky bucket) ===
    //
    // Bulk senders must not emit line-rate bursts: shallow bottleneck buffers
    // and ingress policers (common on budget VPSes) clip bursts even when the
    // AVERAGE rate is far below the path capacity, producing a steady drip of
    // loss events that keeps multiplying the congestion window down. Packets
    // are instead spread at PACING_GAIN * cwnd / sRTT — slightly above the
    // ACK-clocked rate so the window can still grow, but never a burst.
    private static final double PACING_GAIN = 1.25;
    private static final double MIN_PACING_RATE_BPS = 10_000; // 10 KB/s floor
    private static final long PACER_BURST_ALLOWANCE_NANOS = 2_000_000; // 2ms

    private long pacerNextNanos = 0;

    // Optional ceiling on the pacing rate of stream data, in bits per second
    // (0 = none). A call layer sets it while a call is live: loss-based
    // congestion control fills whatever queue sits downstream (the receiver's
    // socket buffer, a bottleneck router), and DATAGRAM audio waits in that
    // queue behind the upload. Capping streams below the path rate keeps it
    // empty. Datagrams are not paced, so the cap never applies to them.
    private volatile long streamRateCapBps = 0;

    /** Pacing rate in bytes per second: PACING_GAIN * cwnd / sRTT, floored, then capped. */
    private double pacingRateBytesPerSec() {
        long srttMs = Math.max(1, rttEstimator.getSmoothedRtt());
        double rate = PACING_GAIN * congestionControl.getCongestionWindow() * 1000.0 / srttMs;
        if (rate < MIN_PACING_RATE_BPS) rate = MIN_PACING_RATE_BPS;
        long cap = streamRateCapBps;
        if (cap > 0) rate = Math.min(rate, cap / 8.0);
        return rate;
    }

    public long getStreamRateCapBps() {
        return streamRateCapBps;
    }

    /**
     * Cap the rate stream data is sent at on this connection, in bits per
     * second; 0 removes the cap. Retransmissions are budgeted to it too.
     */
    public void setStreamRateCapBps(long bitsPerSecond) {
        if (bitsPerSecond < 0) {
            throw new IllegalArgumentException("Stream rate cap must not be negative: " + bitsPerSecond);
        }
        this.streamRateCapBps = bitsPerSecond;
    }

    /**
     * Reserve a pacing slot for {@code bytes} about to be sent.
     *
     * @return nanoseconds the caller should sleep before sending (0 = send now)
     */
    public synchronized long reservePacingDelayNanos(int bytes) {
        double rateBps = pacingRateBytesPerSec();
        long nanosForBytes = (long) (bytes * 1_000_000_000.0 / rateBps);

        long now = System.nanoTime();
        if (pacerNextNanos < now - PACER_BURST_ALLOWANCE_NANOS) {
            pacerNextNanos = now; // idle: restart the bucket, allow a small burst
        }
        long delay = pacerNextNanos - now;
        pacerNextNanos += nanosForBytes;
        return Math.max(0, delay);
    }

    /**
     * Bytes the pacer allows within the given interval (for non-blocking
     * senders like the retransmit task that budget per cycle instead of
     * sleeping per packet).
     */
    public long pacingBudgetBytes(long intervalMs) {
        return (long) (pacingRateBytesPerSec() * intervalMs / 1000.0);
    }

    public long getSessionEpoch() {
        return sessionEpoch;
    }

    public void setSessionEpoch(long sessionEpoch) {
        this.sessionEpoch = sessionEpoch;
    }

    public boolean isEpochConfirmed() {
        return epochConfirmed;
    }

    public void setEpochConfirmed(boolean epochConfirmed) {
        this.epochConfirmed = epochConfirmed;
    }

    // Getters and setters
    public String getPeerId() {
        return peerId;
    }

    public byte[] getPeerPublicKey() {
        return peerPublicKey;
    }

    public void setPeerPublicKey(byte[] peerPublicKey) {
        this.peerPublicKey = peerPublicKey;
    }

    public SocketAddress getPeerAddress() {
        return peerAddress;
    }

    public ConnectionState getState() {
        return state;
    }

    public void setState(ConnectionState state) {
        this.state = state;
    }

    public long getConnectionId() {
        return connectionId;
    }

    public long getNextPacketNumber() {
        return nextPacketNumber;
    }

    public StreamManager getStreamManager() {
        return streamManager;
    }

    public AckManager getAckManager() {
        return ackManager;
    }

    public RttEstimator getRttEstimator() {
        return rttEstimator;
    }

    public CongestionControl getCongestionControl() {
        return congestionControl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastActivity() {
        return lastActivity;
    }

    public long getPacketsSent() {
        return packetsSent;
    }

    public long getPacketsReceived() {
        return packetsReceived;
    }

    public long getBytesOut() {
        return bytesOut;
    }

    public long getBytesIn() {
        return bytesIn;
    }

    @Override
    public String toString() {
        return String.format("PeerConnection[peer=%s, state=%s, sent=%d, recv=%d]",
                peerId, state, packetsSent, packetsReceived);
    }
}
