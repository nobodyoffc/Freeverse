package fudp.packet.frames;

import fudp.packet.Frame;
import fudp.packet.FrameType;
import fudp.util.Varint;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * ACK frame for acknowledging received packets
 */
public class AckFrame extends Frame {

    private long largestAcknowledged;
    private long ackDelay; // microseconds
    private List<AckRange> ackRanges;

    public AckFrame() {
        super(FrameType.ACK);
        this.ackRanges = new ArrayList<>();
    }

    public AckFrame(long largestAcknowledged, long ackDelay, List<AckRange> ranges) {
        super(FrameType.ACK);
        this.largestAcknowledged = Math.max(0, largestAcknowledged);
        this.ackDelay = Math.max(0, ackDelay);
        this.ackRanges = ranges != null ? ranges : new ArrayList<>();
    }

    @Override
    public byte[] toBytes() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            out.write(Varint.encode(FrameType.ACK.getValue()));
            out.write(Varint.encode(largestAcknowledged));
            out.write(Varint.encode(ackDelay));
            out.write(Varint.encode(ackRanges.size()));

            if (!ackRanges.isEmpty()) {
                // First ACK range
                out.write(Varint.encode(ackRanges.get(0).length));

                // Additional ranges
                for (int i = 1; i < ackRanges.size(); i++) {
                    AckRange range = ackRanges.get(i);
                    out.write(Varint.encode(range.gap));
                    out.write(Varint.encode(range.length));
                }
            }

            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize AckFrame", e);
        }
    }

    @Override
    public int getSize() {
        int size = Varint.encodedLength(FrameType.ACK.getValue());
        size += Varint.encodedLength(largestAcknowledged);
        size += Varint.encodedLength(ackDelay);
        size += Varint.encodedLength(ackRanges.size());

        if (!ackRanges.isEmpty()) {
            size += Varint.encodedLength(ackRanges.get(0).length);
            for (int i = 1; i < ackRanges.size(); i++) {
                AckRange range = ackRanges.get(i);
                size += Varint.encodedLength(range.gap);
                size += Varint.encodedLength(range.length);
            }
        }
        return size;
    }

    @Override
    public boolean shouldRetransmit() {
        return false;
    }

    @Override
    public boolean isAckEliciting() {
        return false;
    }

    /**
     * Parse an AckFrame from a ByteBuffer
     */
    public static AckFrame parse(ByteBuffer buffer) {
        AckFrame frame = new AckFrame();

        frame.largestAcknowledged = Varint.decode(buffer);
        frame.ackDelay = Varint.decode(buffer);
        int rangeCount = (int) Varint.decode(buffer);

        if (rangeCount > 0) {
            // First ACK range
            long firstRange = Varint.decode(buffer);
            frame.ackRanges.add(new AckRange(0, firstRange));

            // Additional ranges
            for (int i = 1; i < rangeCount; i++) {
                long gap = Varint.decode(buffer);
                long length = Varint.decode(buffer);
                frame.ackRanges.add(new AckRange(gap, length));
            }
        }

        return frame;
    }

    /**
     * Get the set of acknowledged packet numbers
     */
    public List<Long> getAcknowledgedPackets() {
        List<Long> packets = new ArrayList<>();
        if (ackRanges.isEmpty()) return packets;

        long pn = largestAcknowledged;

        // First range
        AckRange firstRange = ackRanges.get(0);
        for (long i = 0; i <= firstRange.length; i++) {
            packets.add(pn - i);
        }
        pn = pn - firstRange.length - 1;

        // Additional ranges
        for (int i = 1; i < ackRanges.size(); i++) {
            AckRange range = ackRanges.get(i);
            pn = pn - range.gap - 1;
            for (long j = 0; j <= range.length; j++) {
                packets.add(pn - j);
            }
            pn = pn - range.length - 1;
        }

        return packets;
    }

    // Getters and setters
    public long getLargestAcknowledged() {
        return largestAcknowledged;
    }

    public void setLargestAcknowledged(long largestAcknowledged) {
        this.largestAcknowledged = largestAcknowledged;
    }

    public long getAckDelay() {
        return ackDelay;
    }

    public void setAckDelay(long ackDelay) {
        this.ackDelay = ackDelay;
    }

    public List<AckRange> getAckRanges() {
        return ackRanges;
    }

    public void setAckRanges(List<AckRange> ackRanges) {
        this.ackRanges = ackRanges;
    }

    /**
     * ACK range representation
     */
    public static class AckRange {
        public final long gap;    // Gap from previous range
        public final long length; // Number of consecutive packets - 1

        public AckRange(long gap, long length) {
            this.gap = gap;
            this.length = length;
        }
    }

    @Override
    public String toString() {
        return String.format("AckFrame[largest=%d, delay=%d, ranges=%d]",
                largestAcknowledged, ackDelay, ackRanges.size());
    }
}
