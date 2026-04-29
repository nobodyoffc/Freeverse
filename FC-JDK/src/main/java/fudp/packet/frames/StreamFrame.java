package fudp.packet.frames;

import fudp.packet.Frame;
import fudp.packet.FrameType;
import fudp.util.Varint;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * STREAM frame for carrying application data.
 *
 * <p>Flags (encoded in type byte):
 * <ul>
 *   <li>Bit 0: FIN - stream end marker</li>
 *   <li>Bit 1: LEN - reserved; always set on the wire (length varint is
 *       always emitted). Older drafts allowed omitting the length on the
 *       last frame in a packet for a 1-2 byte saving, but in combination
 *       with an unauthenticated header that turned packet truncation into
 *       a parser oracle. Header AAD (F1) already closes the truncation
 *       hole, but mandating an explicit length keeps the parser simple
 *       and removes the special-case branch.</li>
 *   <li>Bit 2: OFF - has offset field</li>
 * </ul>
 */
public class StreamFrame extends Frame {

    private static final int FLAG_FIN = 0x01;
    private static final int FLAG_LEN = 0x02;
    private static final int FLAG_OFF = 0x04;

    private long streamId;
    private long offset;
    private byte[] data;
    private boolean fin;

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

            // Build type byte with flags. LEN is always set — see class javadoc.
            int typeByte = FrameType.STREAM.getValue() | FLAG_LEN;
            if (fin) typeByte |= FLAG_FIN;
            if (offset > 0) typeByte |= FLAG_OFF;

            out.write(Varint.encode(typeByte));
            out.write(Varint.encode(streamId));

            if (offset > 0) {
                out.write(Varint.encode(offset));
            }
            out.write(Varint.encode(data.length));
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
     * Parse a StreamFrame from a ByteBuffer. The length varint is mandatory.
     */
    public static StreamFrame parse(ByteBuffer buffer, int typeByte) {
        StreamFrame frame = new StreamFrame();

        frame.fin = (typeByte & FLAG_FIN) != 0;
        boolean hasLength = (typeByte & FLAG_LEN) != 0;
        boolean hasOffset = (typeByte & FLAG_OFF) != 0;

        // LEN is always set in the released wire format. A frame missing
        // it is a protocol violation and we refuse to fall back to
        // "remaining bytes" — that branch was the truncation oracle.
        if (!hasLength) {
            throw new IllegalArgumentException(
                    "StreamFrame missing mandatory LEN flag (typeByte=0x"
                    + Integer.toHexString(typeByte) + ")");
        }

        frame.streamId = Varint.decode(buffer);

        if (hasOffset) {
            frame.offset = Varint.decode(buffer);
        } else {
            frame.offset = 0;
        }

        int length = (int) Varint.decode(buffer);
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

    @Override
    public String toString() {
        return String.format("StreamFrame[streamId=%d, offset=%d, len=%d, fin=%b]",
                streamId, offset, data != null ? data.length : 0, fin);
    }
}
