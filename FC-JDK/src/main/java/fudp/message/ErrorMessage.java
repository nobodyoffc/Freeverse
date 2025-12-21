package fudp.message;

import fudp.util.Varint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Error message for reporting failures.
 *
 * Payload format:
 * ┌─────────────────────────────────────┐
 * │ Error Code (4 bytes)                │
 * ├─────────────────────────────────────┤
 * │ Error Message Length (varint)       │
 * ├─────────────────────────────────────┤
 * │ Error Message (UTF-8)               │
 * └─────────────────────────────────────┘
 */
public class ErrorMessage extends AppMessage {

    private int errorCode;
    private String errorMessage;

    public ErrorMessage() {
        super(MessageType.ERROR);
        this.errorCode = 0;
        this.errorMessage = "";
    }

    public ErrorMessage(int errorCode, String errorMessage) {
        super(MessageType.ERROR);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
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
        this.errorMessage = errorMessage;
    }

    @Override
    public byte[] encodePayload() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteBuffer codeBuffer = ByteBuffer.allocate(4);
            codeBuffer.putInt(errorCode);
            out.write(codeBuffer.array());

            byte[] msgBytes = errorMessage.getBytes(StandardCharsets.UTF_8);
            out.write(Varint.encode(msgBytes.length));
            out.write(msgBytes);

            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode error message", e);
        }
    }

    @Override
    public void decodePayload(byte[] payload) {
        if (payload == null || payload.length < 5) {
            throw new IllegalArgumentException("Invalid error payload");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        errorCode = buffer.getInt();
        int msgLength = (int) Varint.decode(buffer);
        byte[] msgBytes = new byte[msgLength];
        buffer.get(msgBytes);
        errorMessage = new String(msgBytes, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "ErrorMessage{" +
                "messageId=" + messageId +
                ", errorCode=" + errorCode +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
