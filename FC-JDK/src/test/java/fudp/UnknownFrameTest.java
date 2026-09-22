package fudp;

import fudp.connection.PeerConnection;
import fudp.packet.Frame;
import fudp.packet.FrameType;
import fudp.packet.frames.DatagramFrame;
import fudp.transport.DatagramResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static fudp.DatagramTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A peer that does not know a frame type loses only the packet carrying it,
 * and the connection survives.
 * <p>
 * This is what happens when a DATAGRAM (0x10) reaches a peer from before
 * FUDP7: its parser throws on the unknown type. To exercise that path on the
 * current code, the sender here puts a type no version knows (0x1f) on the
 * wire. The receiver must drop that packet and nothing else. In particular it
 * must not treat the parse failure as a decrypt failure: those feed a
 * per-address limiter that, after five in a row, drops every packet from the
 * address for a second — a 25 pps datagram flow would black-hole the
 * connection.
 */
public class UnknownFrameTest {

    /** A frame with a type byte no FUDP version defines. */
    static final class UnknownFrame extends Frame {
        UnknownFrame() {
            super(FrameType.PADDING); // local label only; toBytes writes the real type
        }

        @Override
        public byte[] toBytes() {
            return new byte[]{0x1f, 0x02, 0x55, 0x55};
        }

        @Override
        public int getSize() {
            return 4;
        }

        @Override
        public boolean shouldRetransmit() {
            return false;
        }

        @Override
        public boolean isAckEliciting() {
            return false;
        }
    }

    @Test
    public void unknownFrameLosesOnlyItsPacket() throws Exception {
        NodeBundle client = createNode(19711);
        NodeBundle server = createNode(19712);
        LinkedBlockingQueue<byte[]> received = new LinkedBlockingQueue<>();
        server.node().setEventListener(respondingListener(server.node(), (p, c, data) -> received.add(data)));
        client.node().setEventListener(respondingListener(client.node(), null));
        try {
            server.node().start();
            client.node().start();
            introduce(client, server, server.port());
            long[] ids = connectWithDatagrams(client, server);
            Protocol clientProto = client.node().getProtocol();
            Protocol serverProto = server.node().getProtocol();
            PeerConnection conn = clientProto.getConnectionManager().getByConnectionId(ids[0]);

            // 1. A packet mixing a datagram with an unknown frame is lost whole.
            clientProto.sendFramesForTest(conn, List.of(
                    new DatagramFrame("in-bad-packet".getBytes(StandardCharsets.UTF_8)),
                    new UnknownFrame()));
            assertEquals(DatagramResult.SENT, client.node().sendDatagram(ids[0],
                    "after".getBytes(StandardCharsets.UTF_8)));
            byte[] first = received.poll(3, TimeUnit.SECONDS);
            assertNotNull(first, "the next good packet must be delivered");
            assertEquals("after", new String(first, StandardCharsets.UTF_8),
                    "the datagram in the unparseable packet must not be delivered");

            // 2. A sustained run of them (an old peer receiving 25 pps of
            //    datagrams) must not trip the decrypt-failure limiter.
            int burst = 50;
            for (int i = 0; i < burst; i++) {
                clientProto.sendFramesForTest(conn, List.of(new UnknownFrame()));
            }
            // Good traffic straight after the burst must get through at once,
            // not after a cooldown.
            long t0 = System.nanoTime();
            assertEquals(DatagramResult.SENT, client.node().sendDatagram(ids[0],
                    "still-here".getBytes(StandardCharsets.UTF_8)));
            byte[] next = received.poll(3, TimeUnit.SECONDS);
            long waitedMs = (System.nanoTime() - t0) / 1_000_000;
            assertNotNull(next, "a datagram after the burst must arrive");
            assertEquals("still-here", new String(next, StandardCharsets.UTF_8));
            assertTrue(waitedMs < 500, "delivery after the burst took " + waitedMs + "ms (limiter cooldown?)");

            assertEquals(burst + 1, serverProto.getFrameParseFailCount(), "every bad packet counted as a parse failure");
            assertEquals(0, serverProto.getDecryptFailCount(), "authentic packets are not decrypt failures");
            assertEquals(0, serverProto.getDecryptDropCount(), "the source must never be rate-limited");

            // 3. The connection itself is untouched: same connection, requests work.
            var resp = client.node().request(server.fid(), "after-burst", new byte[100]).get(5, TimeUnit.SECONDS);
            assertTrue(resp.isSuccess());
            assertEquals(1, serverProto.getConnectionManager().getConnectionsByPeerId(client.fid()).size());
            assertNotNull(serverProto.getConnectionManager().getByConnectionId(ids[1]),
                    "the server's connection must survive");
        } finally {
            client.node().stop();
            server.node().stop();
        }
    }
}
