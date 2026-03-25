package fudp.message;

import fudp.util.Varint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * File accept message - sent to accept a file offer.
 *
 * Payload format:
 * ┌─────────────────────────────────────┐
 * │ Transfer ID Length (varint)         │
 * ├─────────────────────────────────────┤
 * │ Transfer ID (UTF-8)                 │
 * └─────────────────────────────────────┘
 */
public class FileAcceptMessage extends AppMessage {

    private String transferId;

    public FileAcceptMessage() {
        super(MessageType.FILE_ACCEPT);
        this.transferId = "";
    }

    public FileAcceptMessage(String transferId) {
        super(MessageType.FILE_ACCEPT);
        this.transferId = transferId;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    @Override
    public byte[] encodePayload() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] idBytes = transferId.getBytes(StandardCharsets.UTF_8);
            out.write(Varint.encode(idBytes.length));
            out.write(idBytes);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode file accept", e);
        }
    }

    @Override
    public void decodePayload(byte[] payload) {
        if (payload == null || payload.length < 1) {
            throw new IllegalArgumentException("Invalid file accept payload");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        int len = (int) Varint.decode(buffer);
        byte[] idBytes = new byte[len];
        buffer.get(idBytes);
        transferId = new String(idBytes, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "FileAcceptMessage{transferId='" + transferId + "'}";
    }
}


