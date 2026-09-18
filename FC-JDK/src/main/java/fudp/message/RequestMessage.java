package fudp.message;

import fudp.util.Varint;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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

    // File-backed request data: for a large upload spilled to disk during
    // reassembly, the request `data` lives at [dataFileOffset, +dataFileLength)
    // inside dataFile (sid stays in memory — it is small). `data` is null until
    // first materialised via getData(); adopters should stream via openData().
    private File dataFile;
    private long dataFileOffset;
    private long dataFileLength;

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

    /**
     * The request data. For a file-backed request this lazily reads the whole
     * region into memory on first call (backward compatibility with byte[]-based
     * consumers); prefer {@link #openData()} for large payloads.
     */
    public byte[] getData() {
        if (data == null && dataFile != null) {
            // Read into one exactly-sized array: readAllBytes() grows a buffer and
            // copies at the end, so it peaks at roughly twice the payload size.
            if (dataFileLength > Integer.MAX_VALUE) {
                throw new IllegalStateException("File-backed request too large to materialise: " + dataFileLength);
            }
            byte[] buf = new byte[(int) dataFileLength];
            try (InputStream in = openData()) {
                int off = 0;
                while (off < buf.length) {
                    int n = in.read(buf, off, buf.length - off);
                    if (n < 0) throw new IOException("Truncated file-backed request data at " + off
                            + " of " + buf.length);
                    off += n;
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read file-backed request data", e);
            }
            data = buf;
        }
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
        this.dataFile = null;
    }

    public boolean isFileBacked() {
        return dataFile != null;
    }

    public File getDataFile() {
        return dataFile;
    }

    /** Point this request's data at [offset, offset+length) inside {@code file}. */
    public void setFileBackedData(File file, long offset, long length) {
        this.dataFile = file;
        this.dataFileOffset = offset;
        this.dataFileLength = length;
        this.data = null;
    }

    /** Length of the request data, whether held in RAM or file-backed. */
    public long dataLength() {
        if (dataFile != null) return dataFileLength;
        return data != null ? data.length : 0;
    }

    /** Open a stream over the request data (works for both in-RAM and file-backed). */
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
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] nameBytes = sid.getBytes(StandardCharsets.UTF_8);
            out.write(Varint.encode(nameBytes.length));
            out.write(nameBytes);
            out.write(getData());
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode request", e);
        }
    }

    @Override
    public void decodePayload(byte[] buf, int offset, int length) {
        if (buf == null || length < 1) {
            throw new IllegalArgumentException("Invalid request payload");
        }
        ByteBuffer buffer = ByteBuffer.wrap(buf, offset, length);
        int nameLength = (int) Varint.decode(buffer);
        if (nameLength < 0 || buffer.remaining() < nameLength) {
            throw new IllegalArgumentException("Invalid request payload: service name truncated");
        }
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
                ", dataLength=" + dataLength() +
                (dataFile != null ? ", fileBacked=true" : "") +
                '}';
    }
}
