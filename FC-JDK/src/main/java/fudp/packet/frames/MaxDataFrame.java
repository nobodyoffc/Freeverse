package fudp.packet.frames;

import fudp.packet.Frame;
import fudp.packet.FrameType;
import fudp.util.Varint;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * MAX_DATA frame for connection-level flow control
 */
public class MaxDataFrame extends Frame {

    private long maxData;

    public MaxDataFrame() {
        super(FrameType.MAX_DATA);
    }

    public MaxDataFrame(long maxData) {
        super(FrameType.MAX_DATA);
        this.maxData = maxData;
    }

    @Override
    public byte[] toBytes() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            out.write(Varint.encode(FrameType.MAX_DATA.getValue()));
            out.write(Varint.encode(maxData));

            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize MaxDataFrame", e);
        }
    }

    @Override
    public int getSize() {
        return Varint.encodedLength(FrameType.MAX_DATA.getValue()) +
               Varint.encodedLength(maxData);
    }

    public static MaxDataFrame parse(ByteBuffer buffer) {
        MaxDataFrame frame = new MaxDataFrame();
        frame.maxData = Varint.decode(buffer);
        return frame;
    }

    public long getMaxData() {
        return maxData;
    }

    public void setMaxData(long maxData) {
        this.maxData = maxData;
    }

    @Override
    public String toString() {
        return String.format("MaxDataFrame[maxData=%d]", maxData);
    }
}
