package fudp.packet.frames;

import fudp.packet.Frame;
import fudp.packet.FrameType;
import fudp.util.Varint;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * MAX_STREAMS frame for limiting number of streams
 */
public class MaxStreamsFrame extends Frame {

    private long maxStreams;

    public MaxStreamsFrame() {
        super(FrameType.MAX_STREAMS);
    }

    public MaxStreamsFrame(long maxStreams) {
        super(FrameType.MAX_STREAMS);
        this.maxStreams = maxStreams;
    }

    @Override
    public byte[] toBytes() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            out.write(Varint.encode(FrameType.MAX_STREAMS.getValue()));
            out.write(Varint.encode(maxStreams));

            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize MaxStreamsFrame", e);
        }
    }

    @Override
    public int getSize() {
        return Varint.encodedLength(FrameType.MAX_STREAMS.getValue()) +
               Varint.encodedLength(maxStreams);
    }

    public static MaxStreamsFrame parse(ByteBuffer buffer) {
        MaxStreamsFrame frame = new MaxStreamsFrame();
        frame.maxStreams = Varint.decode(buffer);
        return frame;
    }

    public long getMaxStreams() {
        return maxStreams;
    }

    public void setMaxStreams(long maxStreams) {
        this.maxStreams = maxStreams;
    }

    @Override
    public String toString() {
        return String.format("MaxStreamsFrame[maxStreams=%d]", maxStreams);
    }
}
