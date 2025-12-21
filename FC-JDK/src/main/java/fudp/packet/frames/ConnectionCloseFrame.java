package fudp.packet.frames;

import fudp.packet.Frame;
import fudp.packet.FrameType;
import fudp.util.Varint;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * CONNECTION_CLOSE frame for closing a connection
 */
public class ConnectionCloseFrame extends Frame {

    // Error codes
    public static final int NO_ERROR = 0x00;
    public static final int INTERNAL_ERROR = 0x01;
    public static final int CRYPTO_ERROR = 0x02;
    public static final int FLOW_CONTROL_ERROR = 0x03;
    public static final int STREAM_LIMIT_ERROR = 0x04;
    public static final int PROTOCOL_VIOLATION = 0x05;

    private long errorCode;
    private String reasonPhrase;

    public ConnectionCloseFrame() {
        super(FrameType.CONNECTION_CLOSE);
        this.errorCode = NO_ERROR;
        this.reasonPhrase = "";
    }

    public ConnectionCloseFrame(long errorCode, String reasonPhrase) {
        super(FrameType.CONNECTION_CLOSE);
        this.errorCode = errorCode;
        this.reasonPhrase = reasonPhrase != null ? reasonPhrase : "";
    }

    @Override
    public byte[] toBytes() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            out.write(Varint.encode(FrameType.CONNECTION_CLOSE.getValue()));
            out.write(Varint.encode(errorCode));

            byte[] reasonBytes = reasonPhrase.getBytes(StandardCharsets.UTF_8);
            out.write(Varint.encode(reasonBytes.length));
            out.write(reasonBytes);

            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize ConnectionCloseFrame", e);
        }
    }

    @Override
    public int getSize() {
        byte[] reasonBytes = reasonPhrase.getBytes(StandardCharsets.UTF_8);
        int size = Varint.encodedLength(FrameType.CONNECTION_CLOSE.getValue());
        size += Varint.encodedLength(errorCode);
        size += Varint.encodedLength(reasonBytes.length);
        size += reasonBytes.length;
        return size;
    }

    /**
     * Parse a ConnectionCloseFrame from a ByteBuffer
     */
    public static ConnectionCloseFrame parse(ByteBuffer buffer) {
        ConnectionCloseFrame frame = new ConnectionCloseFrame();

        frame.errorCode = Varint.decode(buffer);
        int reasonLength = (int) Varint.decode(buffer);
        byte[] reasonBytes = new byte[reasonLength];
        buffer.get(reasonBytes);
        frame.reasonPhrase = new String(reasonBytes, StandardCharsets.UTF_8);

        return frame;
    }

    // Getters and setters
    public long getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(long errorCode) {
        this.errorCode = errorCode;
    }

    public String getReasonPhrase() {
        return reasonPhrase;
    }

    public void setReasonPhrase(String reasonPhrase) {
        this.reasonPhrase = reasonPhrase;
    }

    @Override
    public String toString() {
        return String.format("ConnectionCloseFrame[error=%d, reason=%s]", errorCode, reasonPhrase);
    }
}
