package fudp.message;

import fudp.util.Varint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * File complete message - sent when file transfer is complete.
 *
 * Payload format:
 * ┌─────────────────────────────────────┐
 * │ Transfer ID Length (varint)         │
 * ├─────────────────────────────────────┤
 * │ Transfer ID (UTF-8)                 │
 * ├─────────────────────────────────────┤
 * │ Total Chunks (4 bytes)              │
 * ├─────────────────────────────────────┤
 * │ Total Bytes (8 bytes)               │
 * └─────────────────────────────────────┘
 */
public class FileCompleteMessage extends AppMessage {

    private String transferId;
    private int totalChunks;
    private long totalBytes;

    public FileCompleteMessage() {
        super(MessageType.FILE_COMPLETE);
        this.transferId = "";
        this.totalChunks = 0;
        this.totalBytes = 0;
    }

    public FileCompleteMessage(String transferId, int totalChunks, long totalBytes) {
        super(MessageType.FILE_COMPLETE);
        this.transferId = transferId;
        this.totalChunks = totalChunks;
        this.totalBytes = totalBytes;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    @Override
    public byte[] encodePayload() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Transfer ID
            byte[] idBytes = transferId.getBytes(StandardCharsets.UTF_8);
            out.write(Varint.encode(idBytes.length));
            out.write(idBytes);

            // Total chunks (4 bytes)
            ByteBuffer chunksBuffer = ByteBuffer.allocate(4);
            chunksBuffer.putInt(totalChunks);
            out.write(chunksBuffer.array());

            // Total bytes (8 bytes)
            ByteBuffer bytesBuffer = ByteBuffer.allocate(8);
            bytesBuffer.putLong(totalBytes);
            out.write(bytesBuffer.array());

            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode file complete", e);
        }
    }

    @Override
    public void decodePayload(byte[] payload) {
        if (payload == null || payload.length < 1) {
            throw new IllegalArgumentException("Invalid file complete payload");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);

        // Transfer ID
        int idLen = (int) Varint.decode(buffer);
        byte[] idBytes = new byte[idLen];
        buffer.get(idBytes);
        transferId = new String(idBytes, StandardCharsets.UTF_8);

        // Total chunks
        totalChunks = buffer.getInt();

        // Total bytes
        totalBytes = buffer.getLong();
    }

    @Override
    public String toString() {
        return "FileCompleteMessage{" +
                "transferId='" + transferId + '\'' +
                ", totalChunks=" + totalChunks +
                ", totalBytes=" + totalBytes +
                '}';
    }
}


