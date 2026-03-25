package fudp.message;

import fudp.util.Varint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * File offer message - sent to initiate a file transfer.
 *
 * Payload format:
 * ┌─────────────────────────────────────┐
 * │ Transfer ID Length (varint)         │
 * ├─────────────────────────────────────┤
 * │ Transfer ID (UTF-8)                 │  Unique identifier for this transfer
 * ├─────────────────────────────────────┤
 * │ File Name Length (varint)           │
 * ├─────────────────────────────────────┤
 * │ File Name (UTF-8)                   │
 * ├─────────────────────────────────────┤
 * │ File Size (8 bytes)                 │
 * ├─────────────────────────────────────┤
 * │ File Hash Length (varint)           │
 * ├─────────────────────────────────────┤
 * │ File Hash (UTF-8, SHA-256 hex)      │
 * ├─────────────────────────────────────┤
 * │ Chunk Size (4 bytes)                │  Size of each chunk in bytes
 * └─────────────────────────────────────┘
 */
public class FileOfferMessage extends AppMessage {

    public static final int DEFAULT_CHUNK_SIZE = 32 * 1024; // 32KB chunks

    private String transferId;
    private String fileName;
    private long fileSize;
    private String fileHash;
    private int chunkSize;

    public FileOfferMessage() {
        super(MessageType.FILE_OFFER);
        this.transferId = "";
        this.fileName = "";
        this.fileSize = 0;
        this.fileHash = "";
        this.chunkSize = DEFAULT_CHUNK_SIZE;
    }

    public FileOfferMessage(String transferId, String fileName, long fileSize, String fileHash) {
        super(MessageType.FILE_OFFER);
        this.transferId = transferId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileHash = fileHash;
        this.chunkSize = DEFAULT_CHUNK_SIZE;
    }

    public FileOfferMessage(String transferId, String fileName, long fileSize, String fileHash, int chunkSize) {
        super(MessageType.FILE_OFFER);
        this.transferId = transferId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileHash = fileHash;
        this.chunkSize = chunkSize;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    @Override
    public byte[] encodePayload() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Transfer ID
            byte[] transferIdBytes = transferId.getBytes(StandardCharsets.UTF_8);
            out.write(Varint.encode(transferIdBytes.length));
            out.write(transferIdBytes);

            // File name
            byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
            out.write(Varint.encode(fileNameBytes.length));
            out.write(fileNameBytes);

            // File size (8 bytes)
            ByteBuffer sizeBuffer = ByteBuffer.allocate(8);
            sizeBuffer.putLong(fileSize);
            out.write(sizeBuffer.array());

            // File hash
            byte[] hashBytes = fileHash.getBytes(StandardCharsets.UTF_8);
            out.write(Varint.encode(hashBytes.length));
            out.write(hashBytes);

            // Chunk size (4 bytes)
            ByteBuffer chunkBuffer = ByteBuffer.allocate(4);
            chunkBuffer.putInt(chunkSize);
            out.write(chunkBuffer.array());

            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode file offer", e);
        }
    }

    @Override
    public void decodePayload(byte[] payload) {
        if (payload == null || payload.length < 1) {
            throw new IllegalArgumentException("Invalid file offer payload");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);

        // Transfer ID
        int transferIdLen = (int) Varint.decode(buffer);
        byte[] transferIdBytes = new byte[transferIdLen];
        buffer.get(transferIdBytes);
        transferId = new String(transferIdBytes, StandardCharsets.UTF_8);

        // File name
        int fileNameLen = (int) Varint.decode(buffer);
        byte[] fileNameBytes = new byte[fileNameLen];
        buffer.get(fileNameBytes);
        fileName = new String(fileNameBytes, StandardCharsets.UTF_8);

        // File size
        fileSize = buffer.getLong();

        // File hash
        int hashLen = (int) Varint.decode(buffer);
        byte[] hashBytes = new byte[hashLen];
        buffer.get(hashBytes);
        fileHash = new String(hashBytes, StandardCharsets.UTF_8);

        // Chunk size
        chunkSize = buffer.getInt();
    }

    @Override
    public String toString() {
        return "FileOfferMessage{" +
                "transferId='" + transferId + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", fileHash='" + fileHash + '\'' +
                ", chunkSize=" + chunkSize +
                '}';
    }
}


