package fudp.message;

import fudp.util.Varint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * File chunk message - carries a chunk of file data.
 *
 * Payload format:
 * ┌─────────────────────────────────────┐
 * │ Transfer ID Length (varint)         │
 * ├─────────────────────────────────────┤
 * │ Transfer ID (UTF-8)                 │
 * ├─────────────────────────────────────┤
 * │ Chunk Index (4 bytes)               │
 * ├─────────────────────────────────────┤
 * │ Offset (8 bytes)                    │
 * ├─────────────────────────────────────┤
 * │ Chunk Data                          │
 * └─────────────────────────────────────┘
 */
public class FileChunkMessage extends AppMessage {

    private String transferId;
    private int chunkIndex;
    private long offset;
    private byte[] data;

    public FileChunkMessage() {
        super(MessageType.FILE_CHUNK);
        this.transferId = "";
        this.chunkIndex = 0;
        this.offset = 0;
        this.data = new byte[0];
    }

    public FileChunkMessage(String transferId, int chunkIndex, long offset, byte[] data) {
        super(MessageType.FILE_CHUNK);
        this.transferId = transferId;
        this.chunkIndex = chunkIndex;
        this.offset = offset;
        this.data = data != null ? data : new byte[0];
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
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

            // Transfer ID
            byte[] idBytes = transferId.getBytes(StandardCharsets.UTF_8);
            out.write(Varint.encode(idBytes.length));
            out.write(idBytes);

            // Chunk index (4 bytes)
            ByteBuffer indexBuffer = ByteBuffer.allocate(4);
            indexBuffer.putInt(chunkIndex);
            out.write(indexBuffer.array());

            // Offset (8 bytes)
            ByteBuffer offsetBuffer = ByteBuffer.allocate(8);
            offsetBuffer.putLong(offset);
            out.write(offsetBuffer.array());

            // Data
            out.write(data);

            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode file chunk", e);
        }
    }

    @Override
    public void decodePayload(byte[] payload) {
        if (payload == null || payload.length < 1) {
            throw new IllegalArgumentException("Invalid file chunk payload");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);

        // Transfer ID
        int idLen = (int) Varint.decode(buffer);
        byte[] idBytes = new byte[idLen];
        buffer.get(idBytes);
        transferId = new String(idBytes, StandardCharsets.UTF_8);

        // Chunk index
        chunkIndex = buffer.getInt();

        // Offset
        offset = buffer.getLong();

        // Data
        data = new byte[buffer.remaining()];
        buffer.get(data);
    }

    @Override
    public String toString() {
        return "FileChunkMessage{" +
                "transferId='" + transferId + '\'' +
                ", chunkIndex=" + chunkIndex +
                ", offset=" + offset +
                ", dataLength=" + data.length +
                '}';
    }
}


