package fudp.message;

import java.nio.ByteBuffer;

/**
 * Acknowledgment for a relayed message that was successfully delivered.
 *
 * Payload format:
 * ┌─────────────────────────────────────┐
 * │ Original Message ID (8 bytes)       │
 * └─────────────────────────────────────┘
 */
public class RelayAckMessage extends AppMessage {

    private long originalMessageId;

    public RelayAckMessage() {
        super(MessageType.RELAY_ACK);
    }

    public RelayAckMessage(long originalMessageId) {
        super(MessageType.RELAY_ACK);
        this.originalMessageId = originalMessageId;
    }

    public long getOriginalMessageId() {
        return originalMessageId;
    }

    public void setOriginalMessageId(long originalMessageId) {
        this.originalMessageId = originalMessageId;
    }

    @Override
    public byte[] encodePayload() {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(originalMessageId);
        return buffer.array();
    }

    @Override
    public void decodePayload(byte[] payload) {
        if (payload == null || payload.length < 8) {
            throw new IllegalArgumentException("Invalid relay ack payload");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        originalMessageId = buffer.getLong();
    }

    @Override
    public String toString() {
        return "RelayAckMessage{" +
                "messageId=" + messageId +
                ", originalMessageId=" + originalMessageId +
                '}';
    }
}

