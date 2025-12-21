package fudp.packet.frames;

import fudp.packet.Frame;
import fudp.packet.FrameType;
import fudp.util.Varint;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * MAX_STREAM_DATA frame for stream-level flow control
 */
public class MaxStreamDataFrame extends Frame {

    private long streamId;
    private long maxStreamData;

    public MaxStreamDataFrame() {
        super(FrameType.MAX_STREAM_DATA);
    }

    public MaxStreamDataFrame(long streamId, long maxStreamData) {
        super(FrameType.MAX_STREAM_DATA);
        this.streamId = streamId;
        this.maxStreamData = maxStreamData;
    }

    @Override
    public byte[] toBytes() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            out.write(Varint.encode(FrameType.MAX_STREAM_DATA.getValue()));
            out.write(Varint.encode(streamId));
            out.write(Varint.encode(maxStreamData));

            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize MaxStreamDataFrame", e);
        }
    }

    @Override
    public int getSize() {
        return Varint.encodedLength(FrameType.MAX_STREAM_DATA.getValue()) +
               Varint.encodedLength(streamId) +
               Varint.encodedLength(maxStreamData);
    }

    public static MaxStreamDataFrame parse(ByteBuffer buffer) {
        MaxStreamDataFrame frame = new MaxStreamDataFrame();
        frame.streamId = Varint.decode(buffer);
        frame.maxStreamData = Varint.decode(buffer);
        return frame;
    }

    public long getStreamId() {
        return streamId;
    }

    public void setStreamId(long streamId) {
        this.streamId = streamId;
    }

    public long getMaxStreamData() {
        return maxStreamData;
    }

    public void setMaxStreamData(long maxStreamData) {
        this.maxStreamData = maxStreamData;
    }

    @Override
    public String toString() {
        return String.format("MaxStreamDataFrame[streamId=%d, maxData=%d]", streamId, maxStreamData);
    }
}
