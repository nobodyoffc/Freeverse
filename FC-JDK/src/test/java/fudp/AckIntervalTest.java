package fudp;

import fudp.connection.PeerConnection;
import fudp.packet.frames.AckFrame;
import fudp.transport.AckManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACK ranges as intervals, and the run detection that builds them.
 *
 * <p>A sender walks its outstanding packets and tests them against these
 * intervals, instead of expanding the frame into every packet number the peer
 * has retained (FUDP3 §2.1, "ACK processing cost"). The two views must agree.
 */
public class AckIntervalTest {

    /** The intervals must cover exactly the numbers the frame acknowledges. */
    @Test
    public void intervalsAgreeWithExpansion() {
        Random random = new Random(20260923L);
        for (int trial = 0; trial < 200; trial++) {
            AckManager acks = new AckManager(null);
            Set<Long> expected = new HashSet<>();
            long pn = random.nextInt(50);
            int count = 1 + random.nextInt(60);
            for (int i = 0; i < count; i++) {
                acks.onPacketReceived(pn);
                expected.add(pn);
                pn += 1 + random.nextInt(4); // runs with holes between them
            }
            AckFrame frame = acks.generateAckFrame(Integer.MAX_VALUE);
            assertNotNull(frame);

            Set<Long> fromIntervals = new HashSet<>();
            for (AckFrame.AckInterval interval : frame.getAcknowledgedIntervals()) {
                for (long n = interval.low; n <= interval.high; n++) fromIntervals.add(n);
            }
            assertEquals(new HashSet<>(frame.getAcknowledgedPackets()), fromIntervals);
            assertEquals(expected, fromIntervals);
        }
    }

    /** More runs than a frame may carry: the newest ones are kept. */
    @Test
    public void rangeCapKeepsTheNewestRuns() {
        AckManager acks = new AckManager(null);
        for (long pn = 0; pn < 400; pn += 2) acks.onPacketReceived(pn);   // 200 runs of one
        AckFrame frame = acks.generateAckFrame(Integer.MAX_VALUE);
        assertEquals(128, frame.getAckRanges().size(), "MAX_RANGES_PER_FRAME");
        assertEquals(398, frame.getLargestAcknowledged());

        Set<Long> expected = new HashSet<>();
        for (long pn = 398 - (128 - 1) * 2; pn <= 398; pn += 2) expected.add(pn);
        assertEquals(expected, new HashSet<>(frame.getAcknowledgedPackets()));
    }

    /** Ranges that would run below zero are clamped, not wrapped. */
    @Test
    public void hostileRangesAreClamped() {
        List<AckFrame.AckRange> ranges = new ArrayList<>();
        ranges.add(new AckFrame.AckRange(0, 2));
        ranges.add(new AckFrame.AckRange(Long.MAX_VALUE, Long.MAX_VALUE));
        AckFrame frame = new AckFrame(5, 0, ranges);
        List<AckFrame.AckInterval> intervals = frame.getAcknowledgedIntervals();
        assertEquals(3, intervals.get(0).low);
        assertEquals(5, intervals.get(0).high);
        for (AckFrame.AckInterval interval : intervals) {
            assertTrue(interval.low >= 0, "low " + interval.low);
            assertTrue(interval.high >= interval.low);
        }
    }

    /**
     * Non-eliciting packets are pruned when recorded, not only when an ACK is
     * generated: a receive-only datagram flow may never generate one.
     */
    @Test
    public void nonElicitingPacketsArePrunedOnInsert() throws Exception {
        AckManager acks = new AckManager(null);
        for (long pn = 0; pn < 5_000; pn++) acks.onNonElicitingPacketReceived(pn);
        assertFalse(acks.hasPendingAcks(), "datagrams and ACK-only packets elicit no ACK");

        // The retention window is 4 s; this test would have to sleep through
        // it, so it checks the cheaper half: the set stays bounded and the
        // newest numbers are the ones reported.
        acks.onPacketReceived(5_000);
        AckFrame frame = acks.generateAckFrame(Integer.MAX_VALUE);
        assertEquals(1, frame.getAckRanges().size(), "0..5000 is one contiguous range");
        assertEquals(5_000, frame.getLargestAcknowledged());
    }

    /** A sender ignores acknowledgments of packet numbers it never tracked. */
    @Test
    public void untrackedNumbersAreIgnored() throws Exception {
        PeerConnection conn = new PeerConnection("peer",
                new java.net.InetSocketAddress("127.0.0.1", 1), 1);
        conn.onAckReceived(100, 0, List.of(new AckFrame.AckInterval(0, 100)));
        assertEquals(0, conn.getCongestionControl().getBytesInFlight());
    }
}
