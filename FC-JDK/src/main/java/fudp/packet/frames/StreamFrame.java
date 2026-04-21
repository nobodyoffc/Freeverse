package fudp.packet.frames;

import fudp.packet.Frame;
import fudp.packet.FrameType;
import fudp.util.Varint;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * STREAM frame for carrying application data
 *
 * Flags (encoded in type byte):
 * - Bit 0: FIN - stream end marker
 * - Bit 1: LEN - has length field
 * - Bit 2: OFF - has offset field
 */
public class StreamFrame extends Frame {

    private static final int FLAG_FIN = 0x01;
    private static final int FLAG_LEN = 0x02;
    private static final int FLAG_OFF = 0x04;

    private long streamId;
    private long offset;
    private byte[] data;
    private boolean fin;
    private boolean implicitLength = false; // E3: when true, omit length varint (last frame in packet)

    public StreamFrame() {
        super(FrameType.STREAM);
    }

    public StreamFrame(long streamId, long offset, byte[] data, boolean fin) {
        super(FrameType.STREAM);
        this.streamId = streamId;
        this.offset = offset;
        this.data = data;
        this.fin = fin;
    }

    @Override
    public byte[] toBytes() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Build type byte with flags
            int typeByte = FrameType.STREAM.getValue();
            if (fin) typeByte |= FLAG_FIN;
            if (!implicitLength) typeByte |= FLAG_LEN; // Include length unless implicit (last frame)
            if (offset > 0) typeByte |= FLAG_OFF;

            out.write(Varint.encode(typeByte));
            out.write(Varint.encode(streamId));

            if (offset > 0) {
                out.write(Varint.encode(offset));
            }

            if (!implicitLength) {
                out.write(Varint.encode(data.length));
            }
            out.write(data);

            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize StreamFrame", e);
        }
    }

    @Override
    public int getSize() {
        int size = Varint.encodedLength(FrameType.STREAM.getValue());
        size += Varint.encodedLength(streamId);
        if (offset > 0) {
            size += Varint.encodedLength(offset);
        }
        size += Varint.encodedLength(data.length);
        size += data.length;
        return size;
    }

    /**
     * Parse a StreamFrame from a ByteBuffer
     */
    public static StreamFrame parse(ByteBuffer buffer, int typeByte) {
        StreamFrame frame = new StreamFrame();

        frame.fin = (typeByte & FLAG_FIN) != 0;
        boolean hasLength = (typeByte & FLAG_LEN) != 0;
        boolean hasOffset = (typeByte & FLAG_OFF) != 0;

        frame.streamId = Varint.decode(buffer);

        if (hasOffset) {
            frame.offset = Varint.decode(buffer);
        } else {
            frame.offset = 0;
        }

        int length;
        if (hasLength) {
            length = (int) Varint.decode(buffer);
        } else {
            length = buffer.remaining();
        }

        if (length < 0 || length > buffer.remaining()) {
            throw new IllegalArgumentException(
                    "Invalid StreamFrame data length: " + length + " (remaining=" + buffer.remaining() + ")");
        }

        frame.data = new byte[length];
        buffer.get(frame.data);

        return frame;
    }

    // Getters and setters
    public long getStreamId() {
        return streamId;
    }

    public void setStreamId(long streamId) {
        this.streamId = streamId;
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

    public boolean isFin() {
        return fin;
    }

    public void setFin(boolean fin) {
        this.fin = fin;
    }

    public boolean isImplicitLength() {
        return implicitLength;
    }

    public void setImplicitLength(boolean implicitLength) {
        this.implicitLength = implicitLength;
    }

    @Override
    public String toString() {
        return String.format("StreamFrame[streamId=%d, offset=%d, len=%d, fin=%b]",
                streamId, offset, data != null ? data.length : 0, fin);
    }
}
