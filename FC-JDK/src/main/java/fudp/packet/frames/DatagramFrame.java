package fudp.packet.frames;

import fudp.packet.Frame;
import fudp.packet.FrameType;
import fudp.util.Varint;

import java.nio.ByteBuffer;

/**
 * DATAGRAM frame: an unreliable application payload (FUDP7).
 *
 * <pre>
 * DATAGRAM Frame {
 *   Type (varint) = 0x10,
 *   Length (varint),
 *   Data (Length bytes)
 * }
 * </pre>
 *
 * A DATAGRAM frame is encrypted with its packet like any other frame, but it
 * is never retransmitted, does not elicit an ACK, and a packet carrying only
 * DATAGRAM frames is not counted in bytes in flight. It must fit in a single
 * packet: there is no fragmentation and no reassembly.
 */
public class DatagramFrame extends Frame {

    private final byte[] data;

    public DatagramFrame(byte[] data) {
        super(FrameType.DATAGRAM);
        if (data == null) {
            throw new IllegalArgumentException("DATAGRAM data must not be null");
        }
        this.data = data;
    }

    /** Encoded size of a DATAGRAM frame carrying {@code dataLength} bytes. */
    public static int encodedSize(int dataLength) {
        return Varint.encodedLength(FrameType.DATAGRAM.getValue())
                + Varint.encodedLength(dataLength)
                + dataLength;
    }

    @Override
    public byte[] toBytes() {
        byte[] type = Varint.encode(FrameType.DATAGRAM.getValue());
        byte[] length = Varint.encode(data.length);
        byte[] out = new byte[type.length + length.length + data.length];
        System.arraycopy(type, 0, out, 0, type.length);
        System.arraycopy(length, 0, out, type.length, length.length);
        System.arraycopy(data, 0, out, type.length + length.length, data.length);
        return out;
    }

    @Override
    public int getSize() {
        return encodedSize(data.length);
    }

    public static DatagramFrame parse(ByteBuffer buffer) {
        long length = Varint.decode(buffer);
        if (length < 0 || length > buffer.remaining()) {
            throw new IllegalArgumentException(
                    "DATAGRAM length " + length + " exceeds remaining " + buffer.remaining());
        }
        byte[] data = new byte[(int) length];
        buffer.get(data);
        return new DatagramFrame(data);
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public String toString() {
        return "DatagramFrame[len=" + data.length + "]";
    }
}
