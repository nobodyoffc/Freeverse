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
}
