package fudp.packet;

import java.nio.ByteBuffer;

/**
 * Base class for all FUDP frames
 */
public abstract class Frame {

    protected FrameType type;

    public Frame(FrameType type) {
        this.type = type;
    }

    public FrameType getType() {
        return type;
    }

    /**
     * Serialize the frame to bytes
     */
    public abstract byte[] toBytes();

    /**
     * Get the size of the serialized frame
     */
    public abstract int getSize();

    /**
     * Whether this frame should be retransmitted on loss
     */
    public boolean shouldRetransmit() {
        return type != FrameType.ACK && type != FrameType.PADDING;
    }

    /**
     * Whether this frame elicits an ACK
     */
    public boolean isAckEliciting() {
        return type != FrameType.ACK && type != FrameType.PADDING;
    }
}
