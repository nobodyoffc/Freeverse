package fudp.message;

import java.nio.ByteBuffer;

/**
 * Response message for application-level request/response pattern.
 *
 * Payload format:
 * ┌─────────────────────────────────────┐
 * │ Status Code (2 bytes)               │  0=success, others=error
 * ├─────────────────────────────────────┤
 * │ Response Data                       │
 * └─────────────────────────────────────┘
 */
public class ResponseMessage extends AppMessage {

    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_ERROR = 1;
    public static final int STATUS_NOT_FOUND = 404;
    public static final int STATUS_BAD_REQUEST = 400;
    public static final int STATUS_INTERNAL_ERROR = 500;
    public static final int STATUS_OVER_CREDIT_LIMIT = 403;  // Reserved for upper-layer economics
    public static final int STATUS_FORBIDDEN = 403;           // Peer is blacklisted (upper-layer)

    private int statusCode;
    private byte[] data;
    public ResponseMessage() {
        super(MessageType.RESPONSE);
        this.statusCode = STATUS_SUCCESS;
        this.data = new byte[0];
    }

    public ResponseMessage(long messageId, int statusCode, byte[] data) {
        super(MessageType.RESPONSE, messageId);
        this.statusCode = statusCode;
        this.data = data;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public boolean isSuccess() {
        return statusCode == STATUS_SUCCESS;
    }

    @Override
    public byte[] encodePayload() {
        int totalSize = 2 + data.length; // statusCode(2) + data
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.putShort((short) statusCode);
        buffer.put(data);
        return buffer.array();
    }

    @Override
    public void decodePayload(byte[] payload) {
        if (payload == null || payload.length < 2) {
            throw new IllegalArgumentException("Invalid response payload");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        statusCode = buffer.getShort() & 0xFFFF;
        
        int remaining = buffer.remaining();
        data = new byte[remaining];
        buffer.get(data);
    }

    @Override
    public String toString() {
        return "ResponseMessage{" +
                "messageId=" + messageId +
                ", statusCode=" + statusCode +
                ", dataLength=" + (data != null ? data.length : 0) +
                '}';
    }
}
