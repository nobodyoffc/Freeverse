package fudp.message;

import java.nio.ByteBuffer;

/**
 * Ping message for keep-alive and latency measurement.
 *
 * Payload format:
 * ┌─────────────────────────────────────┐
 * │ Timestamp (8 bytes)                 │
 * └─────────────────────────────────────┘
 */
public class PingMessage extends AppMessage {

    private long timestamp;

    public PingMessage() {
        super(MessageType.PING);
        this.timestamp = System.currentTimeMillis();
    }

    public PingMessage(long timestamp) {
        super(MessageType.PING);
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isWantInfo() {
        return hasFlag(AppMessage.FLAG_WANT_PONG_INFO);
    }

    public void setWantInfo(boolean wantInfo) {
        if (wantInfo) {
            setFlag(AppMessage.FLAG_WANT_PONG_INFO);
        } else {
            clearFlag(AppMessage.FLAG_WANT_PONG_INFO);
        }
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public byte[] encodePayload() {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(timestamp);
        return buffer.array();
    }

    @Override
    public void decodePayload(byte[] payload) {
        if (payload == null || payload.length < 8) {
            throw new IllegalArgumentException("Invalid ping payload");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        timestamp = buffer.getLong();
    }

    @Override
    public String toString() {
        return "PingMessage{" +
                "messageId=" + messageId +
                ", timestamp=" + timestamp +
                '}';
    }
}
