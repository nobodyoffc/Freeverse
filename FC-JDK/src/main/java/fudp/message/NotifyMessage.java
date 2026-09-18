package fudp.message;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

/**
 * One-way data message for arbitrary data transfer.
 * Use FLAG_NEED_ACK to request delivery acknowledgment (NOTIFY_ACK).
 *
 * Payload format:
 * ┌─────────────────────────────────────┐
 * │ Data Type (1 byte)                  │  0=raw, 1=json, 2=protobuf, etc.
 * ├─────────────────────────────────────┤
 * │ Data Length (4 bytes)               │
 * ├─────────────────────────────────────┤
 * │ Data (variable)                     │
 * └─────────────────────────────────────┘
 */
public class NotifyMessage extends AppMessage {

    public static final int DATA_TYPE_RAW = 0;
    public static final int DATA_TYPE_JSON = 1;
    public static final int DATA_TYPE_PROTOBUF = 2;
    public static final int DATA_TYPE_MSGPACK = 3;

    private int dataType;
    private byte[] data;

    // File-backed notify data: for a large notify spilled to disk during
    // reassembly, `data` lives at [dataFileOffset, +dataFileLength) inside
    // dataFile (dataType stays in memory — it is one byte). `data` is null
    // until first materialised via getData(); adopters should stream via
    // openData().
    private File dataFile;
    private long dataFileOffset;
    private long dataFileLength;

    public NotifyMessage() {
        super(MessageType.NOTIFY);
        this.dataType = DATA_TYPE_RAW;
        this.data = new byte[0];
    }

    public NotifyMessage(byte[] data) {
        super(MessageType.NOTIFY);
        this.dataType = DATA_TYPE_RAW;
        this.data = data != null ? data : new byte[0];
    }

    public NotifyMessage(byte[] data, int dataType) {
        super(MessageType.NOTIFY);
        this.dataType = dataType;
        this.data = data != null ? data : new byte[0];
    }

    public int getDataType() {
        return dataType;
    }

    public void setDataType(int dataType) {
        this.dataType = dataType;
    }

    /**
     * The notify data. For a file-backed notify this lazily reads the whole
     * region into memory on first call (backward compatibility with byte[]-based
     * consumers, including {@code NodeEventListener.onNotifyReceived}); prefer
     * {@link #openData()} for large payloads.
     */
    public byte[] getData() {
        if (data == null && dataFile != null) {
            try (InputStream in = openData()) {
                data = in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read file-backed notify data", e);
            }
        }
        return data;
    }

    public void setData(byte[] data) {
        this.data = data != null ? data : new byte[0];
        this.dataFile = null;
    }

    public boolean isFileBacked() {
        return dataFile != null;
    }

    public File getDataFile() {
        return dataFile;
    }

    /** Point this notify's data at [offset, offset+length) inside {@code file}. */
    public void setFileBackedData(File file, long offset, long length) {
        this.dataFile = file;
        this.dataFileOffset = offset;
        this.dataFileLength = length;
        this.data = null;
    }

    /** Length of the notify data, whether held in RAM or file-backed. */
    public long dataLength() {
        if (dataFile != null) return dataFileLength;
        return data != null ? data.length : 0;
    }

    /** Open a stream over the notify data (works for both in-RAM and file-backed). */
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
        byte[] body = getData();
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + body.length);
        buffer.put((byte) dataType);
        buffer.putInt(body.length);
        buffer.put(body);
        return buffer.array();
    }

    @Override
    public void decodePayload(byte[] buf, int offset, int length) {
        if (buf == null || length < 5) {
            throw new IllegalArgumentException("Invalid notify message payload");
        }
        ByteBuffer buffer = ByteBuffer.wrap(buf, offset, length);
        dataType = buffer.get() & 0xFF;
        int dataLen = buffer.getInt();
        if (dataLen < 0 || buffer.remaining() < dataLen) {
            throw new IllegalArgumentException("Invalid notify message payload: data truncated");
        }
        data = new byte[dataLen];
        buffer.get(data);
    }

    @Override
    public String toString() {
        return "NotifyMessage{" +
                "messageId=" + messageId +
                ", dataType=" + dataType +
                ", dataLength=" + dataLength() +
                (dataFile != null ? ", fileBacked=true" : "") +
                '}';
    }
}
