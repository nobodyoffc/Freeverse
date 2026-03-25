package fudp.message;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Failure notification for a relayed message that could not be delivered.
 *
 * Payload format:
 * ┌─────────────────────────────────────┐
 * │ Original Message ID (8 bytes)       │
 * ├─────────────────────────────────────┤
 * │ Error Code (1 byte)                 │
 * ├─────────────────────────────────────┤
 * │ Error Message Length (2 bytes)      │
 * ├─────────────────────────────────────┤
 * │ Error Message (UTF-8 string)        │
 * └─────────────────────────────────────┘
 */
public class RelayFailMessage extends AppMessage {

    private long originalMessageId;
    private int errorCode;
    private String errorMessage;

    public RelayFailMessage() {
        super(MessageType.RELAY_FAIL);
        this.errorMessage = "";
    }

    public RelayFailMessage(long originalMessageId, int errorCode, String errorMessage) {
        super(MessageType.RELAY_FAIL);
        this.originalMessageId = originalMessageId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage != null ? errorMessage : "";
    }

    public long getOriginalMessageId() {
        return originalMessageId;
    }

    public void setOriginalMessageId(long originalMessageId) {
        this.originalMessageId = originalMessageId;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage != null ? errorMessage : "";
    }

    @Override
    public byte[] encodePayload() {
        byte[] msgBytes = errorMessage.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(8 + 1 + 2 + msgBytes.length);
        buffer.putLong(originalMessageId);
        buffer.put((byte) errorCode);
        buffer.putShort((short) msgBytes.length);
        buffer.put(msgBytes);
        return buffer.array();
    }

    @Override
    public void decodePayload(byte[] payload) {
        if (payload == null || payload.length < 11) {
            throw new IllegalArgumentException("Invalid relay fail payload");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        originalMessageId = buffer.getLong();
        errorCode = buffer.get() & 0xFF;
        int msgLength = buffer.getShort() & 0xFFFF;
        if (buffer.remaining() < msgLength) {
            throw new IllegalArgumentException("Invalid relay fail payload: message truncated");
        }
        byte[] msgBytes = new byte[msgLength];
        buffer.get(msgBytes);
        errorMessage = new String(msgBytes, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "RelayFailMessage{" +
                "messageId=" + messageId +
                ", originalMessageId=" + originalMessageId +
                ", errorCode=" + errorCode +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}

