package fudp.connection;

import fudp.packet.Frame;
import fudp.stream.Stream;
import fudp.stream.StreamManager;
import fudp.transport.*;
import fudp.congestion.CongestionControl;
import fudp.congestion.RttEstimator;

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

    private final String peerId;           // Peer FID
    private byte[] peerPublicKey;          // Peer public key
    private SocketAddress peerAddress;
    private ConnectionState state;
    private final long connectionId;

    // Packet number management
    private long nextPacketNumber = 0;
    private long largestAckedPacketNumber = -1;

    // Sent packets tracking
    private final Map<Long, SentPacket> sentPackets;

    // Stream management
    private final StreamManager streamManager;

    // Transport layer
    private final AckManager ackManager;
    private final RttEstimator rttEstimator;
    private final CongestionControl congestionControl;

    // Timestamps
    private final Instant createdAt;
    private Instant lastActivity;

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

    public PeerConnection(String peerId, SocketAddress address, long connectionId) {
        this.peerId = peerId;
        this.peerAddress = address;
        this.connectionId = connectionId;
        this.state = ConnectionState.IDLE;

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
        SentPacket sent = new SentPacket(packetNumber, frames, size, ackEliciting);
        // Only track ACK-eliciting packets for loss detection
        // ACK-only packets don't need acknowledgment and shouldn't be counted as lost
        if (ackEliciting) {
            sentPackets.put(packetNumber, sent);
        }
        packetsSent++;
        bytesOut += size;
    }

    /**
     * Process received ACK.
     */
    public void onAckReceived(long largestAcked, long ackDelay, List<Long> ackedPackets) {
        for (long pn : ackedPackets) {
            SentPacket sent = sentPackets.remove(pn);
            if (sent != null) {
                // Update RTT estimation
                if (pn == largestAcked) {
                    long rttSample = System.currentTimeMillis() - sent.sentTime;
                    rttEstimator.updateRtt(rttSample - ackDelay / 1000);
                }

                // Update congestion control
                congestionControl.onAck(sent.size);
            }
            
            // Check if this packet was previously marked as suspected lost
            // If so, it was a false positive (late ACK, not real loss)
            if (suspectedLostPacketNumbers.remove(pn)) {
                ackedAfterSuspectedLost++;
            }
        }

        if (largestAcked > largestAckedPacketNumber) {
            largestAckedPacketNumber = largestAcked;
        }
    }

    // Track packets that were marked as suspected lost (for accurate loss tracking)
    private final Set<Long> suspectedLostPacketNumbers = ConcurrentHashMap.newKeySet();

    // Loss detection configuration
    // Time-based threshold multiplier (RFC 9002 recommends 9/8 = 1.125, but we use more conservative value)
    private static final double TIME_THRESHOLD_MULTIPLIER = 1.5;  // More conservative than QUIC's 1.125
    // Minimum time threshold in milliseconds (prevents false positives on fast networks)
    private static final long MIN_TIME_THRESHOLD_MS = 50;
    // Packet reordering threshold (number of packets that can arrive out of order)
    private static final long PACKET_THRESHOLD = 3;

    /**
     * Get packets that need retransmission.
     */
    public List<SentPacket> detectLostPackets() {
        List<SentPacket> lost = new ArrayList<>();
        
        // Calculate time threshold: max(MIN_TIME_THRESHOLD, smoothedRtt * multiplier + rttVariance)
        // Adding RTT variance makes it more adaptive to network jitter
        long smoothedRtt = rttEstimator.getSmoothedRtt();
        long rttVar = rttEstimator.getRttVariance();
        long timeThreshold = Math.max(MIN_TIME_THRESHOLD_MS, 
                (long) (smoothedRtt * TIME_THRESHOLD_MULTIPLIER) + rttVar);

        for (Map.Entry<Long, SentPacket> entry : sentPackets.entrySet()) {
            long pn = entry.getKey();
            SentPacket packet = entry.getValue();

            // Skip non-ACK-eliciting packets (they don't need to be acknowledged)
            if (!packet.ackEliciting) {
                continue;
            }

            // Lost if:
            // 1. More than PACKET_THRESHOLD packets have been acknowledged after it (packet reordering)
            // 2. More than timeThreshold has passed (time-based loss detection)
            boolean lostByPacketNumber = largestAckedPacketNumber - pn >= PACKET_THRESHOLD;
            boolean lostByTime = System.currentTimeMillis() - packet.sentTime > timeThreshold;
            
            if (lostByPacketNumber || lostByTime) {
                lost.add(packet);
            }
        }

        // Remove lost packets from tracking and update stats
        for (SentPacket packet : lost) {
            sentPackets.remove(packet.packetNumber);
            suspectedLostCount++;
            suspectedLostPacketNumbers.add(packet.packetNumber);
            
            // If this packet has been retransmitted too many times, consider it confirmed lost
            if (packet.retransmitCount >= 3) {
                confirmedLostCount++;
            }
        }

        return lost;
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
        largestAckedPacketNumber = -1;
        peerRestartHandled = false; // Reset flag for next restart detection
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
