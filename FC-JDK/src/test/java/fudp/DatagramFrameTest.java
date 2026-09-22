package fudp;

import core.crypto.KeyTools;
import fudp.connection.PeerConnection;
import fudp.crypto.CryptoManager;
import fudp.crypto.PacketCrypto;
import fudp.packet.Frame;
import fudp.packet.FrameType;
import fudp.packet.Packet;
import fudp.packet.frames.AckFrame;
import fudp.packet.frames.DatagramFrame;
import fudp.packet.frames.StreamFrame;
import fudp.transport.DatagramResult;
import fudp.util.ByteUtils;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static fudp.DatagramTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DATAGRAM frame (FUDP7): encoding, packets carrying several frames, and the
 * size limit that keeps every datagram inside one packet.
 */
public class DatagramFrameTest {

    @Test
    public void encodingRoundTrip() {
        for (int len : new int[]{0, 1, 63, 64, 200, 1200, 16383, 16384}) {
            byte[] data = ByteUtils.randomBytes(len);
            DatagramFrame frame = new DatagramFrame(data);
            byte[] wire = frame.toBytes();

            assertEquals(frame.getSize(), wire.length, "getSize must match the encoding, len=" + len);
            assertEquals(DatagramFrame.encodedSize(len), wire.length);
            assertEquals(0x10, wire[0], "type byte");

            ByteBuffer buf = ByteBuffer.wrap(wire);
            buf.get(); // type, consumed by Packet.parseFrames in real use
            DatagramFrame parsed = DatagramFrame.parse(buf);
            assertArrayEquals(data, parsed.getData(), "len=" + len);
            assertFalse(buf.hasRemaining(), "parse must consume exactly the frame");
        }
    }

    @Test
    public void knownEncodingVector() {
        // Type 0x10, length 3 (1-byte varint), data. Pinned for cross-client vectors.
        assertArrayEquals(new byte[]{0x10, 0x03, 0x0a, 0x0b, 0x0c},
                new DatagramFrame(new byte[]{0x0a, 0x0b, 0x0c}).toBytes());
        // Length 64 needs the 2-byte varint form (0x40 0x40).
        byte[] wire = new DatagramFrame(new byte[64]).toBytes();
        assertEquals(0x10, wire[0]);
        assertEquals(0x40, wire[1] & 0xff);
        assertEquals(0x40, wire[2] & 0xff);
        assertEquals(67, wire.length);
    }

    @Test
    public void lengthPastEndOfPacketIsRejected() {
        byte[] wire = new DatagramFrame(new byte[10]).toBytes();
        ByteBuffer truncated = ByteBuffer.wrap(wire, 0, wire.length - 1).slice();
        truncated.get();
        assertThrows(IllegalArgumentException.class, () -> DatagramFrame.parse(truncated));
    }

    @Test
    public void notRetransmittedAndNotAckEliciting() {
        DatagramFrame frame = new DatagramFrame(new byte[20]);
        assertEquals(FrameType.DATAGRAM, frame.getType());
        assertFalse(frame.shouldRetransmit());
        assertFalse(frame.isAckEliciting());
        assertEquals(FrameType.DATAGRAM, FrameType.fromValue(0x10));

        Packet onlyDatagrams = new Packet(1, 1);
        onlyDatagrams.addFrame(new DatagramFrame(new byte[5]));
        onlyDatagrams.addFrame(new DatagramFrame(new byte[5]));
        assertFalse(onlyDatagrams.isAckEliciting(), "DATAGRAM-only packet must not elicit an ACK");
        assertTrue(onlyDatagrams.hasDatagram());

        Packet withAck = new Packet(1, 2);
        withAck.addFrame(new AckFrame(3, 0, List.of(new AckFrame.AckRange(0, 0))));
        withAck.addFrame(new DatagramFrame(new byte[5]));
        assertFalse(withAck.isAckEliciting(), "DATAGRAM + ACK packet must not elicit an ACK");

        Packet withStream = new Packet(1, 3);
        withStream.addFrame(new DatagramFrame(new byte[5]));
        withStream.addFrame(new StreamFrame(4, 0, new byte[5], false));
        assertTrue(withStream.isAckEliciting(), "a STREAM frame still elicits an ACK");
    }

    @Test
    public void packetWithSeveralFramesRoundTrips() {
        Packet out = new Packet(7, 9);
        List<byte[]> payloads = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            byte[] p = ByteUtils.randomBytes(40 + i);
            payloads.add(p);
            out.addFrame(new DatagramFrame(p));
        }
        out.addFrame(new AckFrame(5, 0, List.of(new AckFrame.AckRange(0, 5))));
        byte[] streamData = ByteUtils.randomBytes(30);
        out.addFrame(new StreamFrame(2, 100, streamData, true));
        byte[] last = ByteUtils.randomBytes(1);
        payloads.add(last);
        out.addFrame(new DatagramFrame(last));

        byte[] plaintext = out.serializeFrames(12345L, true, true);

        Packet in = new Packet(7, 9);
        in.getHeader().setHasTimestamp(true);
        in.getHeader().setHasEpoch(true);
        in.parseFrames(plaintext);

        List<Frame> frames = in.getFrames();
        assertEquals(6, frames.size());
        assertEquals(12345L, in.getSessionEpoch());
        int d = 0;
        for (int i : new int[]{0, 1, 2, 5}) {
            assertInstanceOf(DatagramFrame.class, frames.get(i));
            assertArrayEquals(payloads.get(d++), ((DatagramFrame) frames.get(i)).getData());
        }
        assertInstanceOf(AckFrame.class, frames.get(3));
        StreamFrame sf = assertInstanceOf(StreamFrame.class, frames.get(4));
        assertArrayEquals(streamData, sf.getData());
        assertTrue(sf.isFin());
    }

    /**
     * The advertised maximum must fit in one packet with the worst-case prefix
     * (timestamp + session epoch), measured on the real encryption path.
     */
    @Test
    public void maxDatagramFitsInOnePacket() throws Exception {
        NodeBundle a = createNode(19701);
        try {
            int max = a.node().getMaxDatagramSize();
            int maxPacket = a.node().getConfig().getMaxPacketSize();
            assertTrue(max > 1000, "a 1400-byte packet should carry a >1000-byte datagram, got " + max);

            byte[] priv = ByteUtils.randomBytes(32);
            byte[] peerPub = KeyTools.prikeyToPubkey(ByteUtils.randomBytes(32));
            PacketCrypto crypto = new PacketCrypto(new CryptoManager(priv));

            Packet p = new Packet(1, Long.MAX_VALUE / 4);
            p.addFrame(new DatagramFrame(new byte[max]));
            crypto.encryptPacket(p, "peer", peerPub, true, true);
            int wire = p.toBytes().length;
            assertTrue(wire <= maxPacket, "max datagram produced a " + wire + "-byte packet, limit " + maxPacket);
            assertEquals(maxPacket, wire, "the maximum should fill the packet exactly, or it wastes room");
            System.out.println("[DatagramFrameTest] maxPacket=" + maxPacket + " maxDatagram=" + max
                    + " wire=" + wire);
        } finally {
            a.node().stop();
        }
    }

    @Test
    public void sendPathEnforcesSizeCapabilityAndPacking() throws Exception {
        NodeBundle client = createNode(19702);
        NodeBundle server = createNode(19703);
        LinkedBlockingQueue<byte[]> received = new LinkedBlockingQueue<>();
        server.node().setEventListener(respondingListener(server.node(), (peer, conn, data) -> received.add(data)));
        client.node().setEventListener(respondingListener(client.node(), null));
        try {
            server.node().start();
            client.node().start();
            introduce(client, server, server.port());

            client.node().request(server.fid(), "hello", new byte[8]).get(15, TimeUnit.SECONDS);
            PeerConnection conn = client.node().getProtocol().getConnectionManager().getAnyConnection(server.fid());
            long connId = conn.getConnectionId();

            // Capability gate: nothing goes on the wire until enabled.
            long sentBefore = conn.getPacketsSent();
            assertEquals(DatagramResult.NOT_ENABLED, client.node().sendDatagram(connId, new byte[10]));
            assertEquals(sentBefore, conn.getPacketsSent(), "a refused datagram must not produce a packet");

            client.node().enableDatagrams(connId);
            client.node().setDatagramRate(connId, 8_000_000); // budget is tested separately
            int max = client.node().getMaxDatagramSize();

            // Largest size goes through intact; one byte more is refused, not fragmented.
            byte[] big = ByteUtils.randomBytes(max);
            assertEquals(DatagramResult.SENT, client.node().sendDatagram(connId, big));
            assertArrayEquals(big, received.poll(5, TimeUnit.SECONDS));
            assertEquals(DatagramResult.TOO_LARGE, client.node().sendDatagram(connId, new byte[max + 1]));
            assertNull(received.poll(300, TimeUnit.MILLISECONDS), "an oversized datagram must never arrive");

            // Ten small datagrams sent together share one packet, in order.
            List<byte[]> batch = new ArrayList<>();
            for (int i = 0; i < 10; i++) batch.add(ByteUtils.randomBytes(50));
            sentBefore = conn.getPacketsSent();
            DatagramResult[] results = client.node().sendDatagrams(connId, batch);
            for (DatagramResult r : results) assertEquals(DatagramResult.SENT, r);
            assertEquals(sentBefore + 1, conn.getPacketsSent(), "10 x 50 bytes must be packed into one packet");
            for (byte[] expected : batch) {
                assertArrayEquals(expected, received.poll(5, TimeUnit.SECONDS));
            }

            // A batch too big for one packet splits across packets, still in order.
            batch.clear();
            // Two of these fill a packet exactly (each frame adds 3 bytes of framing).
            for (int i = 0; i < 5; i++) batch.add(ByteUtils.randomBytes(max / 2 - 3));
            sentBefore = conn.getPacketsSent();
            results = client.node().sendDatagrams(connId, batch);
            for (DatagramResult r : results) assertEquals(DatagramResult.SENT, r);
            assertEquals(sentBefore + 3, conn.getPacketsSent(), "5 datagrams, two per packet, need 3 packets");
            for (byte[] expected : batch) {
                assertArrayEquals(expected, received.poll(5, TimeUnit.SECONDS));
            }

            // Nothing sent as a datagram is in flight or awaiting retransmission.
            Thread.sleep(300);
            assertEquals(0, conn.getCongestionControl().getBytesInFlight(),
                    "datagram packets must not count toward bytes in flight");
        } finally {
            client.node().stop();
            server.node().stop();
        }
    }

    @Test
    public void rateBudgetDropsExcessAtSender() throws Exception {
        NodeBundle client = createNode(19704);
        NodeBundle server = createNode(19705);
        List<byte[]> received = Collections.synchronizedList(new ArrayList<>());
        server.node().setEventListener(respondingListener(server.node(), (peer, conn, data) -> received.add(data)));
        client.node().setEventListener(respondingListener(client.node(), null));
        try {
            server.node().start();
            client.node().start();
            introduce(client, server, server.port());
            long connId = connectWithDatagrams(client, server)[0];

            // Default 256 kbps holds 100 ms of budget = 3200 bytes. A burst of
            // 100 x 200 bytes gets ~16 through; the rest are dropped at once.
            int sent = 0, overBudget = 0;
            long start = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                DatagramResult r = client.node().sendDatagram(connId, new byte[200]);
                if (r == DatagramResult.SENT) sent++;
                else if (r == DatagramResult.OVER_BUDGET) overBudget++;
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            assertTrue(sent >= 15 && sent <= 20, "expected ~16 within budget, got " + sent + " in " + elapsedMs + "ms");
            assertEquals(100 - sent, overBudget);

            // A relay-style raised budget lets the same burst through.
            client.node().setDatagramRate(connId, 8_000_000);
            Thread.sleep(150);
            sent = 0;
            for (int i = 0; i < 50; i++) {
                if (client.node().sendDatagram(connId, new byte[200]) == DatagramResult.SENT) sent++;
            }
            assertEquals(50, sent, "8 Mbps holds 100 KB of budget; 10 KB must all pass");
        } finally {
            client.node().stop();
            server.node().stop();
        }
    }
}
