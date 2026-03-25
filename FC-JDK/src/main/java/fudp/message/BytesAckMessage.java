package fudp.message;

import java.nio.ByteBuffer;

/**
 * Acknowledgment for a received bytes message.
 *
 * Payload format:
 * ┌─────────────────────────────────────┐
 * │ Acked Message ID (8 bytes)          │
 * └─────────────────────────────────────┘
 */
public class BytesAckMessage extends AppMessage {

    private long ackedMessageId;

    public BytesAckMessage() {
        super(MessageType.BYTES_ACK);
    }

    public BytesAckMessage(long ackedMessageId) {
        super(MessageType.BYTES_ACK);
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
            throw new IllegalArgumentException("Invalid bytes ack payload");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        ackedMessageId = buffer.getLong();
    }

    @Override
    public String toString() {
        return "BytesAckMessage{" +
                "messageId=" + messageId +
                ", ackedMessageId=" + ackedMessageId +
                '}';
    }
}

