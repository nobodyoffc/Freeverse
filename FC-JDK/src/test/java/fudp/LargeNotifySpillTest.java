package fudp;

import core.crypto.KeyTools;
import fudp.node.FudpNode;
import fudp.node.MessageFrameAssembler;
import fudp.node.NodeConfig;
import fudp.message.NotifyMessage;
import fudp.node.NodeEventListener;
import fudp.node.NotifyPayload;
import fudp.util.ByteUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A NOTIFY larger than the reassembly spill threshold must still be delivered.
 *
 * Above {@link MessageFrameAssembler#DEFAULT_SPILL_THRESHOLD} a reassembled
 * message is file-backed, and the file-backed receive path used to understand
 * only RESPONSE and REQUEST. A NOTIFY that took that path was received in full
 * and then discarded with a warning, so the payload vanished and the sender sat
 * waiting for an ACK that never came. These tests pin the delivery, the
 * integrity of the bytes and the ACK, on both sides of the threshold.
 */
public class LargeNotifySpillTest {

    private static int portCounter = 19401;

    private final List<FudpNode> nodes = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (FudpNode node : nodes) {
            try {
                node.stop();
            } catch (Exception ignore) {
                // best effort
            }
        }
        nodes.clear();
    }

    private record NodeBundle(FudpNode node, String fid, byte[] pubKey, int port) {}

    private NodeBundle createNode() throws IOException {
        return createNode(-1);
    }

    /** @param materialCap bytes, or -1 to leave the default in place. */
    private NodeBundle createNode(long materialCap) throws IOException {
        int port = portCounter++;
        byte[] privKey = ByteUtils.randomBytes(32);
        byte[] pubKey = KeyTools.prikeyToPubkey(privKey);

        NodeConfig config = new NodeConfig();
        config.setPort(port);
        config.setMaxPacketSize(1400);
        config.setSocketBufferSize(2 * 1024 * 1024);
        config.setDataDir(System.getProperty("java.io.tmpdir") + "/fudp_notify_spill_" + port);
        if (materialCap >= 0) config.setMaxMaterializedMessageBytes(materialCap);

        FudpNode node = new FudpNode(privKey, config);
        nodes.add(node);
        node.start();
        return new NodeBundle(node, node.getLocalFid(), pubKey, port);
    }

    /**
     * Send one NOTIFY of {@code size} bytes and assert it arrives intact and acked.
     */
    private void assertNotifyRoundTrips(int size) throws Exception {
        NodeBundle sender = createNode();
        NodeBundle receiver = createNode();

        sender.node.addPeer(receiver.fid, receiver.pubKey, "127.0.0.1", receiver.port, "receiver");
        receiver.node.addPeer(sender.fid, sender.pubKey, "127.0.0.1", sender.port, "sender");

        // Bring the connection up first, exactly as FudpSpeedTest does: the first
        // notify pays for the handshake, and we do not want that on the clock.
        CountDownLatch warm = new CountDownLatch(1);
        receiver.node.setEventListener(new NodeEventListener() {
            @Override
            public void onNotifyReceived(String peerId, long messageId, int dataType, byte[] data) {
                warm.countDown();
            }
        });
        assertTrue(sender.node.sendNotifyWaitAck(receiver.fid, "warmup".getBytes(), 30_000),
                "Warmup notify should be acked");
        assertTrue(warm.await(30, TimeUnit.SECONDS), "Warmup notify should be delivered");
        Thread.sleep(300);

        byte[] payload = new byte[size];
        new SecureRandom().nextBytes(payload);

        AtomicReference<byte[]> received = new AtomicReference<>();
        AtomicInteger receivedDataType = new AtomicInteger(-1);
        CountDownLatch delivered = new CountDownLatch(1);

        receiver.node.setEventListener(new NodeEventListener() {
            @Override
            public void onNotifyReceived(String peerId, long messageId, int dataType, byte[] data) {
                receivedDataType.set(dataType);
                received.set(data);
                delivered.countDown();
            }
        });

        boolean acked = sender.node.sendNotifyWaitAck(receiver.fid, payload,
                NotifyMessage.DATA_TYPE_JSON, 120_000);

        assertTrue(acked, "Sender should receive a NOTIFY_ACK for a " + size + "-byte notify");
        assertTrue(delivered.await(30, TimeUnit.SECONDS),
                "Receiver should deliver a " + size + "-byte notify to the listener");

        byte[] got = received.get();
        assertNotNull(got, "Delivered notify payload should not be null");
        assertEquals(payload.length, got.length, "Delivered notify should be the full length");
        assertArrayEquals(payload, got, "Delivered notify bytes should match what was sent");
        assertEquals(NotifyMessage.DATA_TYPE_JSON, receivedDataType.get(),
                "dataType should survive the file-backed path");
    }

    @Test
    @Timeout(180)
    void notifyBelowSpillThresholdRoundTrips() throws Exception {
        // In-memory reassembly path — the control case.
        assertNotifyRoundTrips(2 * 1024 * 1024);
    }

    @Test
    @Timeout(300)
    void notifyAboveSpillThresholdRoundTrips() throws Exception {
        // Comfortably past DEFAULT_SPILL_THRESHOLD (16 MB), so reassembly spills to
        // disk and delivery goes through the file-backed receive path.
        int size = (int) (MessageFrameAssembler.DEFAULT_SPILL_THRESHOLD + (4L * 1024 * 1024));
        assertNotifyRoundTrips(size);
    }

    /**
     * A NOTIFY past the receiver's materialisation limit is refused rather than
     * turned into a byte[] that the heap may not have room for. The sender must
     * learn this from an ERROR and give up immediately -- waiting out the ACK timer
     * would be the same silent stall the drop used to cause -- and the spill file
     * must not be left behind.
     */
    @Test
    @Timeout(180)
    void notifyOverMaterialisationCapIsRefusedAndSenderFailsFast() throws Exception {
        long cap = 20L * 1024 * 1024;
        NodeBundle sender = createNode();
        NodeBundle receiver = createNode(cap);

        sender.node.addPeer(receiver.fid, receiver.pubKey, "127.0.0.1", receiver.port, "receiver");
        receiver.node.addPeer(sender.fid, sender.pubKey, "127.0.0.1", sender.port, "sender");

        CountDownLatch warm = new CountDownLatch(1);
        CountDownLatch delivered = new CountDownLatch(1);
        receiver.node.setEventListener(new NodeEventListener() {
            @Override
            public void onNotifyReceived(String peerId, long messageId, int dataType, byte[] data) {
                if (data.length == "warmup".length()) warm.countDown();
                else delivered.countDown();
            }
        });
        assertTrue(sender.node.sendNotifyWaitAck(receiver.fid, "warmup".getBytes(), 30_000),
                "Warmup notify should be acked");
        assertTrue(warm.await(30, TimeUnit.SECONDS), "Warmup notify should be delivered");
        Thread.sleep(300);

        // Comfortably over the cap, and over the spill threshold so it takes the
        // file-backed path where the limit is enforced.
        byte[] payload = new byte[(int) cap + (8 * 1024 * 1024)];
        new SecureRandom().nextBytes(payload);

        long ackTimeoutMs = 60_000;
        long startedAt = System.currentTimeMillis();
        boolean acked = sender.node.sendNotifyWaitAck(receiver.fid, payload,
                NotifyMessage.DATA_TYPE_RAW, ackTimeoutMs);
        long elapsed = System.currentTimeMillis() - startedAt;

        assertFalse(acked, "An over-cap notify must not be reported as acked");
        assertTrue(elapsed < ackTimeoutMs / 2,
                "Sender should fail fast on the ERROR, not wait out the ACK timer (took " + elapsed + "ms)");
        assertFalse(delivered.await(2, TimeUnit.SECONDS),
                "An over-cap notify must not be delivered to the listener");

        // The refused message's spill file must be reclaimed, not leaked.
        File spillDir = new File(System.getProperty("java.io.tmpdir")
                + "/fudp_notify_spill_" + receiver.port, "recv-spill");
        File[] leftover = spillDir.listFiles();
        assertTrue(leftover == null || leftover.length == 0,
                "Refused notify must not leave a spill file behind");
    }

    /**
     * A listener that takes the payload as a stream is not subject to the
     * materialisation limit: it never forces the notify into one array, so a notify
     * well past the limit is delivered whole instead of refused. The payload must
     * also be readable as a stream without ever calling getData().
     */
    @Test
    @Timeout(300)
    void streamingListenerReceivesNotifyOverTheMaterialisationCap() throws Exception {
        long cap = 8L * 1024 * 1024;
        NodeBundle sender = createNode();
        NodeBundle receiver = createNode(cap);

        sender.node.addPeer(receiver.fid, receiver.pubKey, "127.0.0.1", receiver.port, "receiver");
        receiver.node.addPeer(sender.fid, sender.pubKey, "127.0.0.1", sender.port, "sender");

        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<String> digest = new AtomicReference<>();
        AtomicLong seenLength = new AtomicLong(-1);
        AtomicBoolean wasFileBacked = new AtomicBoolean();
        AtomicBoolean byteArrayPathUsed = new AtomicBoolean();

        receiver.node.setEventListener(new NodeEventListener() {
            @Override
            public boolean handlesNotifyStream() {
                return true;
            }

            @Override
            public void onNotifyStream(String peerId, long messageId, int dataType, NotifyPayload payload) {
                seenLength.set(payload.length());
                wasFileBacked.set(payload.isFileBacked());
                try (InputStream in = payload.open()) {
                    digest.set(sha256Hex(in));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                delivered.countDown();
            }

            @Override
            public void onNotifyReceived(String peerId, long messageId, int dataType, byte[] data) {
                byteArrayPathUsed.set(true);
            }
        });

        // Warm the connection up through the streaming callback too.
        assertTrue(sender.node.sendNotifyWaitAck(receiver.fid, "warmup".getBytes(), 30_000),
                "Warmup notify should be acked");
        assertTrue(delivered.await(30, TimeUnit.SECONDS), "Warmup notify should reach the stream callback");

        CountDownLatch big = new CountDownLatch(1);
        AtomicReference<CountDownLatch> gate = new AtomicReference<>(big);

        // Four times the cap, and past the spill threshold, so it is on disk.
        byte[] payload = new byte[(int) (MessageFrameAssembler.DEFAULT_SPILL_THRESHOLD + (16L * 1024 * 1024))];
        new SecureRandom().nextBytes(payload);
        String expected = sha256Hex(new java.io.ByteArrayInputStream(payload));

        digest.set(null);
        receiver.node.setEventListener(new NodeEventListener() {
            @Override
            public boolean handlesNotifyStream() {
                return true;
            }

            @Override
            public void onNotifyStream(String peerId, long messageId, int dataType, NotifyPayload p) {
                seenLength.set(p.length());
                wasFileBacked.set(p.isFileBacked());
                try (InputStream in = p.open()) {
                    digest.set(sha256Hex(in));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                gate.get().countDown();
            }

            @Override
            public void onNotifyReceived(String peerId, long messageId, int dataType, byte[] data) {
                byteArrayPathUsed.set(true);
            }
        });

        assertTrue(sender.node.sendNotifyWaitAck(receiver.fid, payload,
                        NotifyMessage.DATA_TYPE_RAW, 180_000),
                "A streaming listener should accept and ack a notify past the materialisation cap");
        assertTrue(big.await(60, TimeUnit.SECONDS), "Streaming listener should be called");

        assertEquals(payload.length, seenLength.get(), "Stream callback should see the full length");
        assertTrue(wasFileBacked.get(), "A payload this size should be file-backed");
        assertEquals(expected, digest.get(), "Streamed bytes should match what was sent");
        assertFalse(byteArrayPathUsed.get(), "The byte[] callback must not be used by a streaming listener");
    }

    private static String sha256Hex(InputStream in) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
