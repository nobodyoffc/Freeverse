package fudp.message;

import java.nio.ByteBuffer;

/**
 * Delivery acknowledgment for a received NOTIFY message.
 *
 * Payload format:
 * ┌─────────────────────────────────────┐
 * │ Acked Message ID (8 bytes)          │
 * └─────────────────────────────────────┘
 */
public class NotifyAckMessage extends AppMessage {

    private long ackedMessageId;

    public NotifyAckMessage() {
        super(MessageType.NOTIFY_ACK);
    }

    public NotifyAckMessage(long ackedMessageId) {
        super(MessageType.NOTIFY_ACK);
        this.ackedMessageId = ackedMessageId;
    }

    public long getAckedMessageId() {
        return ackedMessageId;
    }

    public void setAckedMessageId(long ackedMessageId) {
        this.ackedMessageId = ackedMessageId;
    }

    @Override
    public byte[] encodePayload() {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(ackedMessageId);
        return buffer.array();
    }

    @Override
    public void decodePayload(byte[] payload) {
        if (payload == null || payload.length < 8) {
            throw new IllegalArgumentException("Invalid notify ack payload");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        ackedMessageId = buffer.getLong();
    }

    @Override
    public String toString() {
        return "NotifyAckMessage{" +
                "messageId=" + messageId +
                ", ackedMessageId=" + ackedMessageId +
                '}';
    }
}
