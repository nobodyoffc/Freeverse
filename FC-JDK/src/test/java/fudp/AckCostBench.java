package fudp;

import fudp.connection.PeerConnection;
import fudp.packet.Frame;
import fudp.packet.frames.AckFrame;
import fudp.packet.frames.StreamFrame;
import fudp.transport.AckManager;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What one ACK costs to build and to process, against a large retained set.
 * <p>
 * Every ACK frame re-advertises all packet numbers retained over the last 4
 * seconds (FUDP3 §2.1), and one is generated for every packet received. Both
 * sides must therefore be independent of how many numbers that is. They were
 * not: building copied and walked the whole retained set, and processing
 * expanded the frame and looked up every number in it, which at 16 000
 * retained cost 159 us and 330 us per ACK. A sender receiving thousands of
 * ACKs a second fell behind, its RTT estimate climbed past the loss timeout,
 * and it retransmitted packets that had already arrived.
 * <p>
 * Not run by default (named {@code *Bench}): {@code mvn test -Dtest=AckCostBench}.
 */
public class AckCostBench {

    @Test
    public void ackCostDoesNotFollowTheRetainedSet() throws Exception {
        for (int retained : new int[]{1_000, 6_000, 16_000}) {
            AckManager acks = new AckManager(null);
            for (long pn = 0; pn < retained; pn++) acks.onPacketReceived(pn);

            AckFrame frame = null;
            long next = retained;
            for (int i = 0; i < 2_000; i++) {           // warm up
                acks.onPacketReceived(next++);
                frame = acks.generateAckFrame(1295);
            }
            long t = System.nanoTime();
            for (int i = 0; i < 2_000; i++) {
                acks.onPacketReceived(next++);
                frame = acks.generateAckFrame(1295);
            }
            long generateUs = (System.nanoTime() - t) / 2_000 / 1_000;

            PeerConnection conn = new PeerConnection("peer", new InetSocketAddress("127.0.0.1", 1), 1);
            List<Frame> frames = List.of(new StreamFrame(0, 0, new byte[1200], false));
            for (int i = 0; i < 80; i++) {
                conn.recordSentPacket(conn.allocatePacketNumber(), frames, 1300, true);
            }
            for (int i = 0; i < 2_000; i++) {           // warm up
                conn.onAckReceived(frame.getLargestAcknowledged(), 0, frame.getAcknowledgedIntervals());
            }
            t = System.nanoTime();
            for (int i = 0; i < 2_000; i++) {
                conn.onAckReceived(frame.getLargestAcknowledged(), 0, frame.getAcknowledgedIntervals());
            }
            long processUs = (System.nanoTime() - t) / 2_000 / 1_000;

            System.out.println("[AckCostBench] retained=" + retained
                    + " ranges=" + frame.getAckRanges().size()
                    + " generateAckFrame=" + generateUs + "us"
                    + " onAckReceived=" + processUs + "us");

            // Generous: measured at 1 us and 8 us, against 159 us and 330 us
            // before. Anything near the old numbers is the old algorithm back.
            assertTrue(generateUs < 50, "generateAckFrame took " + generateUs + "us at " + retained + " retained");
            assertTrue(processUs < 50, "onAckReceived took " + processUs + "us at " + retained + " retained");
        }
    }
}
