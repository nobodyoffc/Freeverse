package fudp.message;

import java.nio.ByteBuffer;
import fudp.util.Varint;

/**
 * Pong message in response to Ping.
 *
 * Payload format:
 * ┌─────────────────────────────────────┐
 * │ Echo Timestamp (8 bytes)            │  Original ping timestamp
 * ├─────────────────────────────────────┤
 * │ Reply Timestamp (8 bytes)           │  Time when pong was sent
 * └─────────────────────────────────────┘
 */
public class PongMessage extends AppMessage {

    private long echoTimestamp;
    private long replyTimestamp;
    private byte[] data;

    public PongMessage() {
        super(MessageType.PONG);
        this.echoTimestamp = 0;
        this.replyTimestamp = System.currentTimeMillis();
        this.data = new byte[0];
    }

    public PongMessage(long echoTimestamp) {
        super(MessageType.PONG);
        this.echoTimestamp = echoTimestamp;
        this.replyTimestamp = System.currentTimeMillis();
        this.data = new byte[0];
    }

    public long getEchoTimestamp() {
        return echoTimestamp;
    }

    public void setEchoTimestamp(long echoTimestamp) {
        this.echoTimestamp = echoTimestamp;
    }

    public long getReplyTimestamp() {
        return replyTimestamp;
    }

    public void setReplyTimestamp(long replyTimestamp) {
        this.replyTimestamp = replyTimestamp;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data != null ? data : new byte[0];
    }

    /**
     * Calculate round-trip time from when ping was sent.
     */
    public long calculateRtt() {
        return System.currentTimeMillis() - echoTimestamp;
    }

    @Override
    public byte[] encodePayload() {
        byte[] info = data == null ? new byte[0] : data;
        byte[] dataLen = Varint.encode(info.length);

        ByteBuffer buffer = ByteBuffer.allocate(16 + dataLen.length + info.length);
        buffer.putLong(echoTimestamp);
        buffer.putLong(replyTimestamp);
        buffer.put(dataLen);
        buffer.put(info);
        return buffer.array();
    }

    @Override
    public void decodePayload(byte[] payload) {
        if (payload == null || payload.length < 16) {
            throw new IllegalArgumentException("Invalid pong payload");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        echoTimestamp = buffer.getLong();
        replyTimestamp = buffer.getLong();

        if (buffer.hasRemaining()) {
            int len = (int) Varint.decode(buffer);
            if (len < 0 || len > buffer.remaining()) {
                throw new IllegalArgumentException("Invalid pong data length");
            }
            byte[] info = new byte[len];
            buffer.get(info);
            data = info;
        } else {
            data = new byte[0];
        }
    }

    @Override
    public String toString() {
        return "PongMessage{" +
                "messageId=" + messageId +
                ", echoTimestamp=" + echoTimestamp +
                ", replyTimestamp=" + replyTimestamp +
                ", dataLen=" + (data == null ? 0 : data.length) +
                '}';
    }
}
