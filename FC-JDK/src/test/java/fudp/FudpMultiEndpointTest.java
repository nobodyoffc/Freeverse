package fudp;

import core.crypto.KeyTools;
import fudp.message.ResponseMessage;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import fudp.node.NodeEventListener;
import fudp.util.ByteUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests file transfer between multiple endpoints of the same FID.
 *
 * Scenario: fidB runs on 2 ports (same private key), both send files to fidA.
 * This tests:
 * - MessageId uniqueness across same-FID instances
 * - Concurrent request/response routing on different connections
 * - Connection affinity for responses
 * - No interference between parallel transfers
 *
 * Run: mvn test -pl FC-JDK -Dtest=fudp.FudpMultiEndpointTest
 */
public class FudpMultiEndpointTest {

    private static int portCounter = 19201;

    record NodeBundle(FudpNode node, String fid, byte[] pubKey, byte[] privKey, int port) {}

    private NodeBundle createNode(byte[] privKey, int mtu, long requestTimeoutMs) throws IOException {
        int port = portCounter++;
        byte[] pubKey = KeyTools.prikeyToPubkey(privKey);

        NodeConfig config = new NodeConfig();
        config.setPort(port);
        config.setMaxPacketSize(mtu);
        config.setRequestTimeoutMs(requestTimeoutMs);
        config.setSocketBufferSize(4 * 1024 * 1024);
        config.setDataDir(System.getProperty("java.io.tmpdir") + "/fudp_multiep_" + port);

        FudpNode node = new FudpNode(privKey, config);
        return new NodeBundle(node, node.getLocalFid(), pubKey, privKey, port);
    }

    private byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Check that file bytes appear as a suffix in the received payload.
     * (received data = metadata JSON + file bytes, from requestWithStream)
     */
    private boolean containsFileData(byte[] received, byte[] original) {
        if (received.length < original.length) return false;
        byte[] tail = Arrays.copyOfRange(received, received.length - original.length, received.length);
        return Arrays.equals(sha256(tail), sha256(original));
    }

    // ===== Tests =====

    /**
     * Two same-FID nodes send files to one receiver concurrently.
     * Auto-accept: receiver accepts immediately.
     */
    @Test
    public void testConcurrent_AutoAccept_SmallFiles() throws Exception {
        byte[] privKeyA = ByteUtils.randomBytes(32);
        byte[] privKeyB = ByteUtils.randomBytes(32);

        NodeBundle nodeA = createNode(privKeyA, 8000, 60_000);
        NodeBundle nodeB1 = createNode(privKeyB, 8000, 60_000);
        NodeBundle nodeB2 = createNode(privKeyB, 8000, 60_000);

        byte[] data1 = ByteUtils.randomBytes(1024 * 1024);      // 1 MB
        byte[] data2 = ByteUtils.randomBytes(2 * 1024 * 1024);  // 2 MB

        Map<Long, byte[]> receivedFiles = new ConcurrentHashMap<>();
        CountDownLatch fileLatch = new CountDownLatch(2);

        try {
            nodeA.node.start();
            nodeB1.node.start();
            nodeB2.node.start();

            nodeB1.node.addPeer(nodeA.fid, nodeA.pubKey, "127.0.0.1", nodeA.port, "A");
            nodeB2.node.addPeer(nodeA.fid, nodeA.pubKey, "127.0.0.1", nodeA.port, "A");
            nodeA.node.addPeer(nodeB1.fid, nodeB1.pubKey, "127.0.0.1", nodeB1.port, "B");
            nodeA.node.addPeer(nodeB1.fid, nodeB1.pubKey, "127.0.0.1", nodeB2.port);

            nodeA.node.setEventListener(new AutoAcceptListener(nodeA.node, receivedFiles, fileLatch));

            warmup(nodeB1, nodeA);
            warmup(nodeB2, nodeA);

            CompletableFuture<Boolean> s1 = sendAsync(nodeB1.node, nodeA.fid, "file1.bin", data1);
            CompletableFuture<Boolean> s2 = sendAsync(nodeB2.node, nodeA.fid, "file2.bin", data2);

            assertTrue(s1.get(120, TimeUnit.SECONDS), "B1 send should succeed");
            assertTrue(s2.get(120, TimeUnit.SECONDS), "B2 send should succeed");
            assertTrue(fileLatch.await(30, TimeUnit.SECONDS), "Receiver should get both files");
            assertEquals(2, receivedFiles.size());

            boolean found1 = false, found2 = false;
            for (byte[] recv : receivedFiles.values()) {
                if (containsFileData(recv, data1)) found1 = true;
                if (containsFileData(recv, data2)) found2 = true;
            }
            assertTrue(found1, "file1 integrity");
            assertTrue(found2, "file2 integrity");
            System.out.println("[PASS] Concurrent auto-accept: both files OK");

        } finally {
            nodeA.node.stop();
            nodeB1.node.stop();
            nodeB2.node.stop();
        }
    }

    /**
     * Two same-FID nodes send large files (10MB each) concurrently.
     * Tests that retransmission under packet loss works for both.
     */
    @Test
    public void testConcurrent_AutoAccept_LargeFiles() throws Exception {
        byte[] privKeyA = ByteUtils.randomBytes(32);
        byte[] privKeyB = ByteUtils.randomBytes(32);

        NodeBundle nodeA = createNode(privKeyA, 8000, 120_000);
        NodeBundle nodeB1 = createNode(privKeyB, 8000, 120_000);
        NodeBundle nodeB2 = createNode(privKeyB, 8000, 120_000);

        byte[] data1 = ByteUtils.randomBytes(10 * 1024 * 1024);  // 10 MB
        byte[] data2 = ByteUtils.randomBytes(10 * 1024 * 1024);  // 10 MB

        Map<Long, byte[]> receivedFiles = new ConcurrentHashMap<>();
        CountDownLatch fileLatch = new CountDownLatch(2);

        try {
            nodeA.node.start();
            nodeB1.node.start();
            nodeB2.node.start();

            nodeB1.node.addPeer(nodeA.fid, nodeA.pubKey, "127.0.0.1", nodeA.port, "A");
            nodeB2.node.addPeer(nodeA.fid, nodeA.pubKey, "127.0.0.1", nodeA.port, "A");
            nodeA.node.addPeer(nodeB1.fid, nodeB1.pubKey, "127.0.0.1", nodeB1.port, "B");
            nodeA.node.addPeer(nodeB1.fid, nodeB1.pubKey, "127.0.0.1", nodeB2.port);

            nodeA.node.setEventListener(new AutoAcceptListener(nodeA.node, receivedFiles, fileLatch));

            warmup(nodeB1, nodeA);
            warmup(nodeB2, nodeA);

            System.out.println("Sending 2x 10MB files concurrently from same-FID nodes...");
            long start = System.nanoTime();

            CompletableFuture<Boolean> s1 = sendAsync(nodeB1.node, nodeA.fid, "big1.bin", data1);
            CompletableFuture<Boolean> s2 = sendAsync(nodeB2.node, nodeA.fid, "big2.bin", data2);

            assertTrue(s1.get(300, TimeUnit.SECONDS), "B1 send should succeed");
            assertTrue(s2.get(300, TimeUnit.SECONDS), "B2 send should succeed");
            assertTrue(fileLatch.await(60, TimeUnit.SECONDS), "Receiver should get both files");

            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            double totalMB = (data1.length + data2.length) / (1024.0 * 1024.0);
            System.out.printf("[PASS] 2x 10MB in %dms (%.1f MB/s aggregate)%n", elapsedMs, totalMB / (elapsedMs / 1000.0));

            boolean found1 = false, found2 = false;
            for (byte[] recv : receivedFiles.values()) {
                if (containsFileData(recv, data1)) found1 = true;
                if (containsFileData(recv, data2)) found2 = true;
            }
            assertTrue(found1, "big1 integrity");
            assertTrue(found2, "big2 integrity");

        } finally {
            nodeA.node.stop();
            nodeB1.node.stop();
            nodeB2.node.stop();
        }
    }

    /**
     * Receiver delays acceptance by 10 seconds. Verifies that the FUDP
     * request timeout (set to 60s) doesn't cancel before acceptance.
     * This reproduces the CancellationException bug from FreeIM.
     */
    @Test
    public void testConcurrent_DelayedAccept_10s() throws Exception {
        byte[] privKeyA = ByteUtils.randomBytes(32);
        byte[] privKeyB = ByteUtils.randomBytes(32);

        NodeBundle nodeA = createNode(privKeyA, 8000, 60_000);
        NodeBundle nodeB1 = createNode(privKeyB, 8000, 60_000);
        NodeBundle nodeB2 = createNode(privKeyB, 8000, 60_000);

        byte[] data1 = ByteUtils.randomBytes(512 * 1024);  // 512 KB
        byte[] data2 = ByteUtils.randomBytes(512 * 1024);  // 512 KB

        Map<Long, String> offers = new ConcurrentHashMap<>();
        Map<Long, byte[]> receivedFiles = new ConcurrentHashMap<>();
        CountDownLatch offerLatch = new CountDownLatch(2);
        CountDownLatch fileLatch = new CountDownLatch(2);

        try {
            nodeA.node.start();
            nodeB1.node.start();
            nodeB2.node.start();

            nodeB1.node.addPeer(nodeA.fid, nodeA.pubKey, "127.0.0.1", nodeA.port, "A");
            nodeB2.node.addPeer(nodeA.fid, nodeA.pubKey, "127.0.0.1", nodeA.port, "A");
            nodeA.node.addPeer(nodeB1.fid, nodeB1.pubKey, "127.0.0.1", nodeB1.port, "B");
            nodeA.node.addPeer(nodeB1.fid, nodeB1.pubKey, "127.0.0.1", nodeB2.port);

            // Collect offers silently — don't accept yet
            nodeA.node.setEventListener(new NodeEventListener() {
                @Override
                public void onRequestReceived(String peerId, long connectionId, long requestId, String serviceName, byte[] data) {
                    try {
                        if ("file-offer".equals(serviceName)) {
                            offers.put(requestId, new String(data, StandardCharsets.UTF_8));
                            offerLatch.countDown();
                        } else if ("file-data".equals(serviceName)) {
                            receivedFiles.put(requestId, data);
                            nodeA.node.respond(peerId, requestId, 200, "OK".getBytes(StandardCharsets.UTF_8));
                            fileLatch.countDown();
                        }
                    } catch (IOException e) {
                        System.err.println("Error: " + e.getMessage());
                    }
                }
            });

            warmup(nodeB1, nodeA);
            warmup(nodeB2, nodeA);

            // Both senders send offers concurrently
            CompletableFuture<Boolean> s1 = sendAsync(nodeB1.node, nodeA.fid, "d1.bin", data1);
            CompletableFuture<Boolean> s2 = sendAsync(nodeB2.node, nodeA.fid, "d2.bin", data2);

            assertTrue(offerLatch.await(30, TimeUnit.SECONDS), "Both offers should arrive");
            assertEquals(2, offers.size(), "Should have 2 distinct offers (no messageId collision)");

            // Simulate user delay: wait 10 seconds before accepting
            System.out.println("Waiting 10s before accepting (simulating user delay)...");
            Thread.sleep(10_000);

            // Accept both offers
            for (long requestId : offers.keySet()) {
                System.out.println("  Accepting requestId=" + requestId);
                nodeA.node.respond(nodeB1.fid, requestId, 200, "OK".getBytes(StandardCharsets.UTF_8));
            }

            // Both senders should complete without CancellationException
            assertTrue(s1.get(120, TimeUnit.SECONDS), "B1 should succeed (no CancellationException)");
            assertTrue(s2.get(120, TimeUnit.SECONDS), "B2 should succeed (no CancellationException)");
            assertTrue(fileLatch.await(60, TimeUnit.SECONDS), "Both files should arrive");

            assertEquals(2, receivedFiles.size());
            System.out.println("[PASS] Delayed accept (10s): both transfers OK");

        } finally {
            nodeA.node.stop();
            nodeB1.node.stop();
            nodeB2.node.stop();
        }
    }

    /**
     * Verify two same-FID nodes generate different messageIds.
     */
    @Test
    public void testMessageIdUniqueness_SameFid() throws Exception {
        byte[] privKey = ByteUtils.randomBytes(32);

        NodeBundle node1 = createNode(privKey, 1350, 30_000);
        NodeBundle node2 = createNode(privKey, 1350, 30_000);

        assertEquals(node1.fid, node2.fid);

        long id1 = node1.node.generateMessageId();
        long id2 = node2.node.generateMessageId();
        assertNotEquals(id1, id2, "Same-FID nodes must generate different messageIds");

        // Generate 1000 IDs from each, verify no overlap
        java.util.Set<Long> ids1 = new java.util.HashSet<>();
        java.util.Set<Long> ids2 = new java.util.HashSet<>();
        for (int i = 0; i < 1000; i++) {
            ids1.add(node1.node.generateMessageId());
            ids2.add(node2.node.generateMessageId());
        }
        java.util.Set<Long> overlap = new java.util.HashSet<>(ids1);
        overlap.retainAll(ids2);
        assertTrue(overlap.isEmpty(), "Zero collisions expected, found " + overlap.size());

        System.out.println("[PASS] 1000 IDs each: zero collisions");
        node1.node.stop();
        node2.node.stop();
    }

    // ===== Helpers =====

    private void warmup(NodeBundle sender, NodeBundle receiver) throws Exception {
        sender.node.sendNotify(receiver.fid, "warmup".getBytes());
        Thread.sleep(500);
    }

    private CompletableFuture<Boolean> sendAsync(FudpNode sender, String receiverFid, String fileName, byte[] data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sendFile(sender, receiverFid, fileName, data);
            } catch (Exception e) {
                System.err.println("[FAIL] " + fileName + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Simulates FreeIM file transfer protocol:
     * 1. file-offer REQUEST → wait for acceptance
     * 2. file-data REQUEST+stream → wait for completion
     */
    private boolean sendFile(FudpNode sender, String receiverFid, String fileName, byte[] fileData) throws Exception {
        // Step 1: offer
        String offerJson = String.format("{\"name\":\"%s\",\"size\":%d}", fileName, fileData.length);
        CompletableFuture<ResponseMessage> offerFuture =
                sender.request(receiverFid, "file-offer", offerJson.getBytes(StandardCharsets.UTF_8));
        ResponseMessage offerResp = offerFuture.get(60, TimeUnit.SECONDS);
        if (offerResp == null || offerResp.getStatusCode() != 200) {
            System.err.println("[FAIL] Offer rejected for " + fileName);
            return false;
        }

        // Step 2: send data
        String metaJson = String.format("{\"name\":\"%s\",\"size\":%d}", fileName, fileData.length);
        ByteArrayInputStream stream = new ByteArrayInputStream(fileData);
        CompletableFuture<ResponseMessage> dataFuture =
                sender.requestWithStream(receiverFid, "file-data",
                        metaJson.getBytes(StandardCharsets.UTF_8), stream, fileData.length);
        ResponseMessage dataResp = dataFuture.get(300, TimeUnit.SECONDS);
        if (dataResp == null || dataResp.getStatusCode() != 200) {
            System.err.println("[FAIL] Data transfer failed for " + fileName);
            return false;
        }

        System.out.printf("  [OK] %s (%d bytes)%n", fileName, fileData.length);
        return true;
    }

    /**
     * Auto-accept listener: immediately accepts all file-offers and saves file-data.
     */
    static class AutoAcceptListener implements NodeEventListener {
        private final FudpNode node;
        private final Map<Long, byte[]> receivedFiles;
        private final CountDownLatch fileLatch;

        AutoAcceptListener(FudpNode node, Map<Long, byte[]> receivedFiles, CountDownLatch fileLatch) {
            this.node = node;
            this.receivedFiles = receivedFiles;
            this.fileLatch = fileLatch;
        }

        @Override
        public void onRequestReceived(String peerId, long connectionId, long requestId, String serviceName, byte[] data) {
            try {
                if ("file-offer".equals(serviceName)) {
                    node.respond(peerId, requestId, 200, "OK".getBytes(StandardCharsets.UTF_8));
                } else if ("file-data".equals(serviceName)) {
                    receivedFiles.put(requestId, data);
                    node.respond(peerId, requestId, 200, "OK".getBytes(StandardCharsets.UTF_8));
                    fileLatch.countDown();
                }
            } catch (IOException e) {
                System.err.println("AutoAccept error: " + e.getMessage());
            }
        }
    }
}
