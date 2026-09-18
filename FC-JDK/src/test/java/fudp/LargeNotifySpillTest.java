package fudp;

import core.crypto.KeyTools;
import fudp.node.FudpNode;
import fudp.node.MessageFrameAssembler;
import fudp.node.NodeConfig;
import fudp.message.NotifyMessage;
import fudp.node.NodeEventListener;
import fudp.util.ByteUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
        int port = portCounter++;
        byte[] privKey = ByteUtils.randomBytes(32);
        byte[] pubKey = KeyTools.prikeyToPubkey(privKey);

        NodeConfig config = new NodeConfig();
        config.setPort(port);
        config.setMaxPacketSize(1400);
        config.setSocketBufferSize(2 * 1024 * 1024);
        config.setDataDir(System.getProperty("java.io.tmpdir") + "/fudp_notify_spill_" + port);

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
}
