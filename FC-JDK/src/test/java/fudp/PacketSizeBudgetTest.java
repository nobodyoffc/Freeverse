package fudp;

import fudp.packet.frames.AckFrame;
import fudp.transport.AckManager;
import fudp.util.ByteUtils;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static fudp.DatagramTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * No packet exceeds maxPacketSize, and ACK ranges have holes only where
 * packets were lost.
 * <p>
 * Both failed with data flowing in both directions. The receiver left the
 * peer's ACK-only packets out of its ACK ranges, so every other packet number
 * was a hole: ACK frames hit their 128-range cap (264 bytes) while covering
 * only the last ~250 packets. Piggybacked unbudgeted on a full STREAM packet,
 * that made 1664-byte packets on a 1400-byte setting — over a 1500-byte path
 * MTU at the default 1350, so IP-fragmented on a real network. The crypto
 * overhead was also budgeted 16 bytes short.
 */
public class PacketSizeBudgetTest {

    private static final int MAX_PACKET = 1400; // what DatagramTestSupport configures

    @Test
    public void bidirectionalBulkStaysWithinMaxPacketSize() throws Exception {
        for (double loss : new double[]{0.0, 0.10}) {
            runBidirectional(loss);
        }
    }

    private void runBidirectional(double loss) throws Exception {
        NodeBundle server = createNode(19771);
        NodeBundle client = createNode(19772);
        LossyProxy proxy = new LossyProxy(19773, 19771);
        server.node().setEventListener(respondingListener(server.node(), null));
        client.node().setEventListener(respondingListener(client.node(), null));

        // Largest ACK frame each side receives, in ranges and bytes.
        AtomicInteger maxRanges = new AtomicInteger();
        AtomicInteger maxAckBytes = new AtomicInteger();
        Protocol.PacketListener ackProbe = (conn, packet) -> {
            for (var f : packet.getFrames()) {
                if (f instanceof AckFrame ack) {
                    maxRanges.accumulateAndGet(ack.getAckRanges().size(), Math::max);
                    maxAckBytes.accumulateAndGet(ack.getSize(), Math::max);
                }
            }
        };
        try {
            server.node().start();
            client.node().start();
            proxy.start();
            introduce(client, server, 19773);
            client.node().request(server.fid(), "hello", new byte[8]).get(15, TimeUnit.SECONDS);
            server.node().getProtocol().addPacketListener(ackProbe);
            client.node().getProtocol().addPacketListener(ackProbe);
            proxy.dropRate = loss;

            // 4 MB each way at once, so both sides send full STREAM packets
            // while owing ACKs.
            CompletableFuture<?> up = CompletableFuture.runAsync(() -> request(client, server, 4 << 20));
            CompletableFuture<?> down = CompletableFuture.runAsync(() -> request(server, client, 4 << 20));
            up.get(180, TimeUnit.SECONDS);
            down.get(180, TimeUnit.SECONDS);

            System.out.printf("[PacketSizeBudgetTest] loss=%.0f%% largest packet c->s=%d s->c=%d (limit %d); "
                            + "largest ACK %d ranges / %d bytes%n",
                    loss * 100, proxy.maxToServer.get(), proxy.maxToClient.get(), MAX_PACKET,
                    maxRanges.get(), maxAckBytes.get());

            assertTrue(proxy.maxToServer.get() <= MAX_PACKET, "client sent a " + proxy.maxToServer + "-byte packet");
            assertTrue(proxy.maxToClient.get() <= MAX_PACKET, "server sent a " + proxy.maxToClient + "-byte packet");
            assertEquals(0, client.node().getProtocol().getOversizePacketCount());
            assertEquals(0, server.node().getProtocol().getOversizePacketCount());
            if (loss == 0.0) {
                // Without loss, holes can only come from momentary reordering
                // between sender threads; nothing like the old 128.
                assertTrue(maxRanges.get() <= 8,
                        "loss-free ACKs must stay near one range, saw " + maxRanges.get());
            }
        } finally {
            proxy.stop();
            client.node().stop();
            server.node().stop();
        }
    }

    private static void request(NodeBundle from, NodeBundle to, int bytes) {
        try {
            var resp = from.node().request(to.fid(), "bulk", ByteUtils.randomBytes(bytes)).get(180, TimeUnit.SECONDS);
            assertTrue(resp.isSuccess());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** An ACK is trimmed to its byte budget oldest-first, and stays pending if nothing fits. */
    @Test
    public void ackFrameRespectsItsByteBudget() {
        AckManager acks = new AckManager(null);
        // Every other packet number: 50 ranges.
        for (long pn = 0; pn < 100; pn += 2) acks.onPacketReceived(pn);

        assertNull(acks.generateAckFrame(3), "a frame that cannot hold even one range is not generated");
        assertTrue(acks.hasPendingAcks(), "...and the ACK stays pending for a caller with more room");

        AckFrame small = acks.generateAckFrame(40);
        assertNotNull(small);
        assertTrue(small.getSize() <= 40, "frame is " + small.getSize() + " bytes");
        assertEquals(98, small.getLargestAcknowledged(), "the newest ranges are the ones kept");
        assertTrue(small.getAckRanges().size() < 50);
        assertFalse(acks.hasPendingAcks());

        // Non-eliciting packet numbers fill the holes without making an ACK due.
        for (long pn = 1; pn < 100; pn += 2) acks.onNonElicitingPacketReceived(pn);
        assertFalse(acks.hasPendingAcks(), "ACK-only and DATAGRAM-only packets elicit no ACK");
        acks.onPacketReceived(100);
        AckFrame full = acks.generateAckFrame(Integer.MAX_VALUE);
        assertEquals(1, full.getAckRanges().size(), "0..100 is one contiguous range");
        assertEquals(100, full.getAckRanges().get(0).length);
    }
}
