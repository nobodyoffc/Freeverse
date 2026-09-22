package fudp.transport;

import fudp.packet.Frame;

import java.util.List;

/**
 * Represents a sent packet for ACK tracking
 */
public class SentPacket {
    public final long packetNumber;
    public final List<Frame> frames;
    public final int size;
    public final boolean ackEliciting;
    public final long sentTime;
    private int retransmitCount;
    // Position among TRACKED packets on this connection (see
    // PeerConnection.recordSentPacket). Gap-based loss detection counts in
    // this space, not in packet numbers, which untracked packets also use.
    private long trackedSeq;

    public SentPacket(long packetNumber, List<Frame> frames, int size, boolean ackEliciting) {
        this.packetNumber = packetNumber;
        this.frames = frames;
        this.size = size;
        this.ackEliciting = ackEliciting;
        this.sentTime = System.currentTimeMillis();
        this.retransmitCount = 0;
    }

    public int getRetransmitCount() { return retransmitCount; }
    public void setRetransmitCount(int retransmitCount) { this.retransmitCount = retransmitCount; }
    public void incrementRetransmitCount() { retransmitCount++; }
    public long getTrackedSeq() { return trackedSeq; }
    public void setTrackedSeq(long trackedSeq) { this.trackedSeq = trackedSeq; }
}
