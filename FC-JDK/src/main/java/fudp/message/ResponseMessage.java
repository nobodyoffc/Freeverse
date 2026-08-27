package fudp.message;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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

    // File-backed payload: when the response was too large to hold in RAM, the
    // assembler spilled the whole message to a temp file. The response data then
    // lives at [dataFileOffset, dataFileOffset + dataFileLength) inside dataFile
    // and `data` is null until first materialised. Adopters should stream via
    // openData() instead of getData() to avoid loading it all into memory.
    private File dataFile;
    private long dataFileOffset;
    private long dataFileLength;

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

    /**
     * The response data. For a file-backed response this lazily reads the whole
     * region into memory on first call (kept for backward compatibility with
     * byte[]-based consumers); prefer {@link #openData()} for large payloads.
     */
    public byte[] getData() {
        if (data == null && dataFile != null) {
            try (InputStream in = openData()) {
                data = in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read file-backed response data", e);
            }
        }
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
        this.dataFile = null;
    }

    public boolean isSuccess() {
        return statusCode == STATUS_SUCCESS;
    }

    /**
     * @return true if the response data was spilled to a temp file (large download);
     *         consume it via {@link #openData()} to avoid materialising it in RAM.
     */
    public boolean isFileBacked() {
        return dataFile != null;
    }

    public File getDataFile() {
        return dataFile;
    }

    /** Point this response at file-backed data at [offset, offset+length) inside {@code file}. */
    public void setFileBackedData(File file, long offset, long length) {
        this.dataFile = file;
        this.dataFileOffset = offset;
        this.dataFileLength = length;
        this.data = null;
    }

    /** Length of the response data, whether held in RAM or file-backed. */
    public long dataLength() {
        if (dataFile != null) return dataFileLength;
        return data != null ? data.length : 0;
    }

    /**
     * Open a stream over the response data (works for both in-RAM and file-backed).
     * The caller must close the stream.
     */
    public InputStream openData() throws IOException {
        if (dataFile != null) {
            return new FileRegionInputStream(dataFile, dataFileOffset, dataFileLength);
        }
        return new java.io.ByteArrayInputStream(data != null ? data : new byte[0]);
    }

    /** Delete the backing temp file, if file-backed. Call once done reading. */
    public void deleteBackingFile() {
        if (dataFile != null) {
            //noinspection ResultOfMethodCallIgnored
            dataFile.delete();
            dataFile = null;
        }
    }

    @Override
    public byte[] encodePayload() {
        byte[] d = getData();
        int totalSize = 2 + d.length; // statusCode(2) + data
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.putShort((short) statusCode);
        buffer.put(d);
        return buffer.array();
    }

    @Override
    public void decodePayload(byte[] buf, int offset, int length) {
        if (buf == null || length < 2) {
            throw new IllegalArgumentException("Invalid response payload");
        }
        ByteBuffer buffer = ByteBuffer.wrap(buf, offset, length);
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
                ", dataLength=" + dataLength() +
                (dataFile != null ? ", fileBacked=true" : "") +
                '}';
    }
}
