package fudp;

import fudp.connection.PeerConnection;
import fudp.message.ResponseMessage;
import fudp.packet.frames.DatagramFrame;
import fudp.packet.frames.StreamFrame;
import fudp.transport.DatagramResult;
import fudp.util.ByteUtils;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static fudp.DatagramTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 10% loss in each direction on loopback. Datagrams are never retransmitted,
 * so about 10% of them are lost and none arrives twice; meanwhile a stream
 * transfer on the same connection completes exactly as it would without them.
 */
public class DatagramLossTest {

    @Test
    public void datagramsAreNotRetransmittedAndStreamsAreUnaffected() throws Exception {
        int serverPort = 19721, clientPort = 19722, proxyPort = 19723;
        NodeBundle server = createNode(serverPort);
        NodeBundle client = createNode(clientPort);
        LossyProxy proxy = new LossyProxy(proxyPort, serverPort);

        ConcurrentHashMap<Long, AtomicInteger> seen = new ConcurrentHashMap<>();
        server.node().setEventListener(respondingListener(server.node(),
                (peer, conn, data) -> seen.computeIfAbsent(seqOf(data), k -> new AtomicInteger()).incrementAndGet()));
        client.node().setEventListener(respondingListener(client.node(), null));
        try {
            server.node().start();
            client.node().start();
            proxy.start();
            introduce(client, server, proxyPort);
            long[] ids = connectWithDatagrams(client, server);
            PeerConnection conn = client.node().getProtocol().getConnectionManager().getByConnectionId(ids[0]);
            long retransmitsBefore = conn.getRetransmitCount();

            proxy.dropRate = 0.10;

            // A 1 MB request on the same connection, running while datagrams flow.
            byte[] upload = ByteUtils.randomBytes(1024 * 1024);
            CompletableFuture<ResponseMessage> transfer =
                    client.node().request(server.fid(), "upload", upload);

            // 1000 datagrams at 200/s (5 s), 160 bytes each: 256 kbps, the default budget.
            int total = 1000;
            int sent = 0;
            long next = System.nanoTime();
            for (int i = 0; i < total; i++) {
                if (client.node().sendDatagram(ids[0], stamped(i, 160)) == DatagramResult.SENT) sent++;
                next += 5_000_000L;
                long wait = next - System.nanoTime();
                if (wait > 0) java.util.concurrent.locks.LockSupport.parkNanos(wait);
            }

            ResponseMessage resp = transfer.get(60, TimeUnit.SECONDS);
            assertTrue(resp.isSuccess(), "the stream transfer must complete under loss");
            assertEquals(upload.length, ByteBuffer.wrap(resp.getData()).getInt(),
                    "the server must have received the whole upload");
            assertTrue(conn.getRetransmitCount() > retransmitsBefore,
                    "sanity: the stream did need retransmissions at 10% loss");

            Thread.sleep(500); // let the last datagrams land
            int delivered = seen.size();
            long duplicates = seen.values().stream().filter(c -> c.get() > 1).count();
            double lossPct = 100.0 * (sent - delivered) / sent;
            System.out.printf("[DatagramLossTest] sent=%d delivered=%d loss=%.1f%% duplicates=%d "
                            + "streamRetransmits=%d proxyDropped=%d%n",
                    sent, delivered, lossPct, duplicates,
                    conn.getRetransmitCount() - retransmitsBefore, proxy.dropped.get());

            assertTrue(sent >= total * 0.98, "the budget should pass ~all of them, sent=" + sent);
            assertEquals(0, duplicates, "a datagram must never arrive twice");
            // 1000 trials at p=0.1: 5.0..15.0% is beyond +/-5 sigma. A retransmitting
            // implementation would show ~0%.
            assertTrue(lossPct >= 5.0 && lossPct <= 15.0,
                    "datagram loss should track the 10% link loss (no retransmission), was " + lossPct + "%");

            // Datagrams never entered bytes in flight: once the stream is fully
            // ACKed, nothing is left outstanding.
            long deadline = System.currentTimeMillis() + 10_000;
            while (conn.getCongestionControl().getBytesInFlight() != 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertEquals(0, conn.getCongestionControl().getBytesInFlight(),
                    "bytes in flight must drain to zero; datagrams are not counted");
            assertEquals(1, client.node().getProtocol().getConnectionManager()
                    .getConnectionsByPeerId(server.fid()).size(), "everything ran on one connection");
        } finally {
            proxy.stop();
            client.node().stop();
            server.node().stop();
        }
    }

    /**
     * FUDP7 code check: a receiver may list DATAGRAM-only packet numbers in its
     * ACKs, and those must not count as evidence that a tracked packet was lost.
     * Before the fix, gap-based detection measured the gap in packet numbers,
     * so ten datagrams sent after a stream packet made that packet "lost" as
     * soon as anything after them was acknowledged.
     */
    @Test
    public void untrackedPacketNumbersAreNotLossEvidence() throws Exception {
        PeerConnection conn = new PeerConnection("peer", new InetSocketAddress("127.0.0.1", 1), 1);

        // pn 0: a stream packet, still in flight. pn 1..10: datagram-only
        // packets (untracked). pn 11: a stream packet that gets ACKed.
        conn.recordSentPacket(conn.allocatePacketNumber(), List.of(new StreamFrame(0, 0, new byte[100], false)), 150, true);
        for (int i = 0; i < 10; i++) {
            conn.recordSentPacket(conn.allocatePacketNumber(), List.of(new DatagramFrame(new byte[80])), 150, false);
        }
        long pn11 = conn.allocatePacketNumber();
        conn.recordSentPacket(pn11, List.of(new StreamFrame(0, 100, new byte[100], false)), 150, true);

        Thread.sleep(100); // RTT sample ~100 ms, clearly distinct from the 50 ms initial estimate
        List<Long> acked = new ArrayList<>();
        for (long pn = 1; pn <= 11; pn++) acked.add(pn); // datagram numbers listed too
        conn.onAckReceived(11, 0, acked);
        long minRtt = conn.getRttEstimator().getMinRtt();
        assertTrue(minRtt >= 90 && minRtt < 1000,
                "an ACK whose largest number is untracked must still yield an RTT sample (minRtt=" + minRtt + ")");

        Thread.sleep(150); // older than the gap-detection age guard (~sRTT), younger than the 2 s timeout
        assertTrue(conn.detectLostPackets().packets().isEmpty(),
                "only one TRACKED packet followed pn 0; ten untracked numbers are no loss evidence");

        // Real loss is still caught: seven tracked packets ACKed past a missing one.
        long lostPn = conn.allocatePacketNumber();
        conn.recordSentPacket(lostPn, List.of(new StreamFrame(0, 200, new byte[100], false)), 150, true);
        List<Long> later = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            long pn = conn.allocatePacketNumber();
            conn.recordSentPacket(pn, List.of(new StreamFrame(0, 300 + i * 100, new byte[100], false)), 150, true);
            later.add(pn);
        }
        Thread.sleep(300);
        conn.onAckReceived(later.get(later.size() - 1), 0, later);
        var detection = conn.detectLostPackets();
        assertTrue(detection.packets().stream().anyMatch(p -> p.packetNumber == lostPn),
                "a tracked packet with 7 tracked successors ACKed must be detected as lost");
        assertTrue(detection.gapLoss());
    }
}
