package fudp.message;

import fudp.util.Varint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * File cancel message - sent to cancel an ongoing file transfer.
 *
 * Payload format:
 * ┌─────────────────────────────────────┐
 * │ Transfer ID Length (varint)         │
 * ├─────────────────────────────────────┤
 * │ Transfer ID (UTF-8)                 │
 * ├─────────────────────────────────────┤
 * │ Reason Length (varint)              │
 * ├─────────────────────────────────────┤
 * │ Reason (UTF-8, optional)            │
 * └─────────────────────────────────────┘
 */
public class FileCancelMessage extends AppMessage {

    private String transferId;
    private String reason;

    public FileCancelMessage() {
        super(MessageType.FILE_CANCEL);
        this.transferId = "";
        this.reason = "";
    }

    public FileCancelMessage(String transferId) {
        super(MessageType.FILE_CANCEL);
        this.transferId = transferId;
        this.reason = "";
    }

    public FileCancelMessage(String transferId, String reason) {
        super(MessageType.FILE_CANCEL);
        this.transferId = transferId;
        this.reason = reason != null ? reason : "";
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public byte[] encodePayload() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Transfer ID
            byte[] idBytes = transferId.getBytes(StandardCharsets.UTF_8);
            out.write(Varint.encode(idBytes.length));
            out.write(idBytes);

            // Reason
            byte[] reasonBytes = reason.getBytes(StandardCharsets.UTF_8);
            out.write(Varint.encode(reasonBytes.length));
            out.write(reasonBytes);

            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode file cancel", e);
        }
    }

    @Override
    public void decodePayload(byte[] payload) {
        if (payload == null || payload.length < 1) {
            throw new IllegalArgumentException("Invalid file cancel payload");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);

        // Transfer ID
        int idLen = (int) Varint.decode(buffer);
        byte[] idBytes = new byte[idLen];
        buffer.get(idBytes);
        transferId = new String(idBytes, StandardCharsets.UTF_8);

        // Reason
        if (buffer.hasRemaining()) {
            int reasonLen = (int) Varint.decode(buffer);
            byte[] reasonBytes = new byte[reasonLen];
            buffer.get(reasonBytes);
            reason = new String(reasonBytes, StandardCharsets.UTF_8);
        }
    }

    @Override
    public String toString() {
        return "FileCancelMessage{transferId='" + transferId + "', reason='" + reason + "'}";
    }
}


