package fudp.message;

import fudp.util.Varint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Request message for application-level request/response pattern.
 *
 * Payload format:
 * ┌─────────────────────────────────────┐
 * │ Service Name Length (varint)        │
 * ├─────────────────────────────────────┤
 * │ Service Name (UTF-8)                │  e.g., "user.profile"
 * ├─────────────────────────────────────┤
 * │ Request Data                        │
 * └─────────────────────────────────────┘
 */
public class RequestMessage extends AppMessage {

    private String sid;
    private byte[] data;

    public RequestMessage() {
        super(MessageType.REQUEST);
        this.sid = "";
        this.data = new byte[0];
    }

    public RequestMessage(long messageId, String sid, byte[] data) {
        super(MessageType.REQUEST, messageId);
        this.sid = sid;
        this.data = data;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    @Override
    public byte[] encodePayload() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] nameBytes = sid.getBytes(StandardCharsets.UTF_8);
            out.write(Varint.encode(nameBytes.length));
            out.write(nameBytes);
            out.write(data);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode request", e);
        }
    }

    @Override
    public void decodePayload(byte[] payload) {
        if (payload == null || payload.length < 1) {
            throw new IllegalArgumentException("Invalid request payload");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        int nameLength = (int) Varint.decode(buffer);
        byte[] nameBytes = new byte[nameLength];
        buffer.get(nameBytes);
        sid = new String(nameBytes, StandardCharsets.UTF_8);
        data = new byte[buffer.remaining()];
        buffer.get(data);
    }

    @Override
    public String toString() {
        return "RequestMessage{" +
                "messageId=" + messageId +
                ", serviceName='" + sid + '\'' +
                ", dataLength=" + data.length +
                '}';
    }
}
