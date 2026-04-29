package fudp;

import fudp.packet.Frame;
import fudp.packet.Packet;
import fudp.packet.frames.AckFrame;
import fudp.packet.frames.StreamFrame;
import fudp.util.Varint;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4 — F5: explicit length varint on every StreamFrame.
 *
 * <p>Locks in the wire-format invariant that a StreamFrame always carries
 * its length (LEN flag set, length varint emitted) regardless of position
 * in the packet. The previous "last-frame implicit length" optimisation
 * is removed, so the parser no longer has a "remaining bytes" branch.
 */
class Phase4StreamFrameLengthTest {

    /** STREAM frame type value, copied from FrameType.STREAM (0x08). */
    private static final int STREAM_TYPE = 0x08;
    private static final int FLAG_LEN = 0x02;

    @Test
    void singleStreamFrameAlwaysCarriesLengthVarint() {
        StreamFrame frame = new StreamFrame(/*streamId*/ 4L, /*offset*/ 0L,
                "hello".getBytes(), /*fin*/ false);
        byte[] wire = frame.toBytes();

        // First byte is the type/flags varint. With LEN set the low bits
        // of the type byte should include FLAG_LEN.
        ByteBuffer bb = ByteBuffer.wrap(wire);
        int typeByte = (int) Varint.decode(bb);
        assertEquals(STREAM_TYPE | FLAG_LEN, typeByte & ~0x05,
                "type byte must encode the STREAM type with LEN set");
        assertNotEquals(0, typeByte & FLAG_LEN, "LEN flag must be set");
    }

    @Test
    void lastStreamFrameInPacketStillCarriesLengthVarint() {
        // Build a packet with two StreamFrames, the last of which (under
        // the old behaviour) would have had its length omitted. Confirm
        // both serialise identically — i.e., both carry length.
        Packet packet = new Packet(1L, 1L);
        packet.addFrame(new StreamFrame(4L, 0L, "first".getBytes(), false));
        packet.addFrame(new StreamFrame(4L, 5L, "second".getBytes(), true));

        byte[] payload = packet.serializeFrames(/*epoch*/ 0L,
                /*includeTimestamp*/ false, /*includeEpoch*/ false);

        // Re-parse and verify both frames round-trip with full data
        // intact — there is no "remaining bytes" interpretation possible
        // because both frames carry an explicit length.
        Packet copy = new Packet(1L, 1L);
        copy.getHeader().setHasTimestamp(false);
        copy.getHeader().setHasEpoch(false);
        copy.parseFrames(payload);

        assertEquals(2, copy.getFrames().size());
        assertTrue(copy.getFrames().get(0) instanceof StreamFrame);
        assertTrue(copy.getFrames().get(1) instanceof StreamFrame);
        assertArrayEquals("first".getBytes(), ((StreamFrame) copy.getFrames().get(0)).getData());
        assertArrayEquals("second".getBytes(), ((StreamFrame) copy.getFrames().get(1)).getData());
        assertTrue(((StreamFrame) copy.getFrames().get(1)).isFin());
    }

    @Test
    void parserRejectsStreamFrameWithoutLenFlag() {
        // Build a wire-format StreamFrame manually with LEN cleared. The
        // parser must refuse — no "remaining bytes" fallback. This is the
        // assertion that nails the truncation oracle shut.
        ByteBuffer out = ByteBuffer.allocate(64);
        // Type byte: STREAM | FIN, LEN deliberately cleared.
        out.put(Varint.encode(STREAM_TYPE | 0x01)); // FIN set, LEN clear
        out.put(Varint.encode(7L)); // streamId
        // No length varint, just data.
        byte[] data = "tail".getBytes();
        out.put(data);
        out.flip();
        byte[] wire = new byte[out.remaining()];
        out.get(wire);

        // Parse path mirrors what Packet.parseFrames does: read typeByte,
        // dispatch by frame type, hand the buffer to StreamFrame.parse.
        ByteBuffer reader = ByteBuffer.wrap(wire);
        int typeByte = (int) Varint.decode(reader);
        assertEquals(STREAM_TYPE | 0x01, typeByte, "test fixture sanity");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> StreamFrame.parse(reader, typeByte));
        assertTrue(ex.getMessage().toLowerCase().contains("len"),
                "rejection message should mention LEN: " + ex.getMessage());
    }

    @Test
    void streamFrameRoundTripsViaPacketWithMixedFrameTypes() {
        // A mixed packet (ACK + StreamFrame) — the StreamFrame is still
        // not the only frame, and it parses correctly with explicit length.
        Packet packet = new Packet(2L, 5L);
        packet.addFrame(new AckFrame(4L, 0L, Collections.singletonList(new AckFrame.AckRange(0, 0))));
        packet.addFrame(new StreamFrame(4L, 0L, "payload-bytes".getBytes(), true));

        byte[] payload = packet.serializeFrames(0L, false, false);

        Packet copy = new Packet(2L, 5L);
        copy.getHeader().setHasTimestamp(false);
        copy.getHeader().setHasEpoch(false);
        copy.parseFrames(payload);

        List<Frame> got = copy.getFrames();
        assertEquals(2, got.size());
        assertTrue(got.get(0) instanceof AckFrame);
        assertTrue(got.get(1) instanceof StreamFrame);
        assertArrayEquals("payload-bytes".getBytes(), ((StreamFrame) got.get(1)).getData());
    }

    @Test
    void streamFrameSizeIncludesLengthVarintEvenForLastFrame() {
        // getSize() must agree with toBytes().length for any frame —
        // mismatched accounting was the kind of bug the implicit-length
        // toggle introduced.
        StreamFrame f1 = new StreamFrame(4L, 0L, new byte[100], true);
        assertEquals(f1.toBytes().length, f1.getSize());

        StreamFrame f2 = new StreamFrame(4L, 1024L, new byte[1], false);
        assertEquals(f2.toBytes().length, f2.getSize());
    }
}
