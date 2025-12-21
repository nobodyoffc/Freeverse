package fudp.node;

import fudp.message.ResponseMessage;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for FudpNode.
 * Tests communication between two node instances.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FudpNodeIntegrationTest {

    private static FudpNode nodeA;
    private static FudpNode nodeB;
    private static byte[] privateKeyA;
    private static byte[] privateKeyB;
    private static Path tempDirA;
    private static Path tempDirB;

    private static final int PORT_A = 19001;
    private static final int PORT_B = 19002;

    @BeforeAll
    public static void setUp() throws Exception {
        // Generate test keys
        SecureRandom random = new SecureRandom();
        privateKeyA = new byte[32];
        privateKeyB = new byte[32];
        random.nextBytes(privateKeyA);
        random.nextBytes(privateKeyB);

        // Create temp directories for peer books
        tempDirA = Files.createTempDirectory("fudp_test_a");
        tempDirB = Files.createTempDirectory("fudp_test_b");

        // Create configs
        NodeConfig configA = new NodeConfig();
        configA.setPort(PORT_A);
        configA.setDataDir(tempDirA.toString());

        NodeConfig configB = new NodeConfig();
        configB.setPort(PORT_B);
        configB.setDataDir(tempDirB.toString());

        // Create nodes
        nodeA = new FudpNode(privateKeyA, configA);
        nodeB = new FudpNode(privateKeyB, configB);

        // Start nodes
        nodeA.start();
        nodeB.start();

        // Give time for UDP sockets to bind
        Thread.sleep(100);

        // Register each other as peers
        nodeA.addPeer(nodeB.getLocalFid(), nodeB.getLocalPublicKey(), "127.0.0.1", PORT_B);
        nodeB.addPeer(nodeA.getLocalFid(), nodeA.getLocalPublicKey(), "127.0.0.1", PORT_A);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        if (nodeA != null) {
            nodeA.stop();
        }
        if (nodeB != null) {
            nodeB.stop();
        }

        // Cleanup temp directories
        deleteDirectory(tempDirA);
        deleteDirectory(tempDirB);
    }

    private static void deleteDirectory(Path dir) {
        if (dir != null) {
            try {
                Files.walk(dir)
                        .sorted((a, b) -> -a.compareTo(b))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                // Ignore
                            }
                        });
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    @Test
    @Order(1)
    public void testNodesStarted() {
        assertTrue(nodeA.isRunning(), "Node A should be running");
        assertTrue(nodeB.isRunning(), "Node B should be running");
        assertNotNull(nodeA.getLocalFid(), "Node A should have FID");
        assertNotNull(nodeB.getLocalFid(), "Node B should have FID");
        assertNotEquals(nodeA.getLocalFid(), nodeB.getLocalFid(), "Nodes should have different FIDs");
    }

    @Test
    @Order(2)
    public void testPeerRegistration() {
        // Check Node A knows Node B
        Peer peerB = nodeA.getPeer(nodeB.getLocalFid());
        assertNotNull(peerB, "Node A should know Node B");
        assertEquals(PORT_B, peerB.getPort());
        assertEquals("127.0.0.1", peerB.getHost());

        // Check Node B knows Node A
        Peer peerA = nodeB.getPeer(nodeA.getLocalFid());
        assertNotNull(peerA, "Node B should know Node A");
        assertEquals(PORT_A, peerA.getPort());
    }

    @Test
    @Order(3)
    public void testChatMessage() throws Exception {
        // Set up listener on Node B to receive chat
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedMessage = new AtomicReference<>();
        AtomicReference<String> senderFid = new AtomicReference<>();

        nodeB.setEventListener(new NodeEventListener() {
            @Override
            public void onChatReceived(String peerId, long messageId, String message) {
                senderFid.set(peerId);
                receivedMessage.set(message);
                latch.countDown();
            }
        });

        // Send chat from Node A to Node B
        String testMessage = "Hello from Node A!";
        nodeA.sendChat(nodeB.getLocalFid(), testMessage);

        // Wait for message
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Should receive chat message within 5 seconds");
        assertEquals(testMessage, receivedMessage.get(), "Message content should match");
        assertEquals(nodeA.getLocalFid(), senderFid.get(), "Sender FID should match");
    }

    @Test
    @Order(4)
    public void testChatWithAck() throws Exception {
        // Set up listener on Node A
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedMessage = new AtomicReference<>();

        nodeA.setEventListener(new NodeEventListener() {
            @Override
            public void onChatReceived(String peerId, long messageId, String message) {
                receivedMessage.set(message);
                latch.countDown();
            }
        });

        // Send chat with ACK from Node B to Node A
        String testMessage = "Hello with ACK from Node B!";
        nodeB.sendChatWithAck(nodeA.getLocalFid(), testMessage);

        // Wait for message
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Should receive chat message with ACK");
        assertEquals(testMessage, receivedMessage.get());
    }

    @Test
    @Order(5)
    public void testRequestResponse() throws Exception {
        // Set up request handler on Node B
        nodeB.setEventListener(new NodeEventListener() {
            @Override
            public void onRequestReceived(String peerId, long requestId, String serviceName, byte[] data) {
                try {
                    // Echo the data back with service name prefix
                    String response = serviceName + ": " + new String(data);
                    nodeB.respond(peerId, requestId, ResponseMessage.STATUS_SUCCESS, response.getBytes());
                } catch (IOException e) {
                    fail("Failed to send response: " + e.getMessage());
                }
            }
        });

        // Send request from Node A to Node B
        byte[] requestData = "test data".getBytes();
        CompletableFuture<ResponseMessage> future = nodeA.request(nodeB.getLocalFid(), "echo.service", requestData);

        // Wait for response
        ResponseMessage response = future.get(5, TimeUnit.SECONDS);
        assertNotNull(response, "Should receive response");
        assertTrue(response.isSuccess(), "Response should be successful");

        String responseText = new String(response.getData());
        assertEquals("echo.service: test data", responseText);
    }

    @Test
    @Order(6)
    public void testPingPong() throws Exception {
        // Set up listener on Node A to receive pong
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Long> rttValue = new AtomicReference<>();

        nodeA.setEventListener(new NodeEventListener() {
            @Override
            public void onPingComplete(String peerId, long rttMs) {
                rttValue.set(rttMs);
                latch.countDown();
            }
        });

        // Send ping from Node A to Node B
        nodeA.ping(nodeB.getLocalFid());

        // Wait for pong
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Should receive pong within 5 seconds");
        assertNotNull(rttValue.get(), "RTT should be measured");
        assertTrue(rttValue.get() >= 0, "RTT should be non-negative");
        assertTrue(rttValue.get() < 1000, "RTT should be less than 1 second for localhost");
    }

    @Test
    @Order(7)
    public void testMultipleMessages() throws Exception {
        // Set up listener to count messages
        CountDownLatch latch = new CountDownLatch(5);
        ConcurrentLinkedQueue<String> messages = new ConcurrentLinkedQueue<>();

        nodeB.setEventListener(new NodeEventListener() {
            @Override
            public void onChatReceived(String peerId, long messageId, String message) {
                messages.add(message);
                latch.countDown();
            }
        });

        // Send 5 messages
        for (int i = 0; i < 5; i++) {
            nodeA.sendChat(nodeB.getLocalFid(), "Message " + i);
            Thread.sleep(50); // Small delay between messages
        }

        // Wait for all messages
        boolean allReceived = latch.await(10, TimeUnit.SECONDS);
        assertTrue(allReceived, "Should receive all 5 messages");
        assertEquals(5, messages.size(), "Should have received 5 messages");
    }

    @Test
    @Order(8)
    public void testPeerAlias() {
        // Set alias for Node B on Node A
        nodeA.setAlias(nodeB.getLocalFid(), "bob");

        // Should be able to find by alias
        Peer peer = nodeA.getPeer("bob");
        assertNotNull(peer, "Should find peer by alias");
        assertEquals(nodeB.getLocalFid(), peer.getId());
    }

    @Test
    @Order(9)
    public void testUnicodeMessage() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedMessage = new AtomicReference<>();

        nodeB.setEventListener(new NodeEventListener() {
            @Override
            public void onChatReceived(String peerId, long messageId, String message) {
                receivedMessage.set(message);
                latch.countDown();
            }
        });

        // Send unicode message
        String unicodeMessage = "Hello 世界! 🌍 مرحبا";
        nodeA.sendChat(nodeB.getLocalFid(), unicodeMessage);

        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Should receive unicode message");
        assertEquals(unicodeMessage, receivedMessage.get(), "Unicode content should match");
    }

    @Test
    @Order(10)
    public void testUnknownPeer() {
        // Try to send to unknown peer
        assertThrows(IOException.class, () -> {
            nodeA.sendChat("unknown_peer_id", "test");
        }, "Should throw exception for unknown peer");
    }

    @Test
    @Order(11)
    public void testListPeers() {
        // Node A should have at least Node B
        var peers = nodeA.listPeers();
        assertFalse(peers.isEmpty(), "Should have at least one peer");

        boolean foundB = peers.stream()
                .anyMatch(p -> p.getId().equals(nodeB.getLocalFid()));
        assertTrue(foundB, "Should find Node B in peer list");
    }

    @Test
    @Order(12)
    public void testBidirectionalCommunication() throws Exception {
        // Test that both nodes can send and receive
        CountDownLatch latchA = new CountDownLatch(1);
        CountDownLatch latchB = new CountDownLatch(1);
        AtomicReference<String> messageToA = new AtomicReference<>();
        AtomicReference<String> messageToB = new AtomicReference<>();

        nodeA.setEventListener(new NodeEventListener() {
            @Override
            public void onChatReceived(String peerId, long messageId, String message) {
                messageToA.set(message);
                latchA.countDown();
            }
        });

        nodeB.setEventListener(new NodeEventListener() {
            @Override
            public void onChatReceived(String peerId, long messageId, String message) {
                messageToB.set(message);
                latchB.countDown();
            }
        });

        // Send in both directions
        nodeA.sendChat(nodeB.getLocalFid(), "A to B");
        nodeB.sendChat(nodeA.getLocalFid(), "B to A");

        // Wait for both
        assertTrue(latchA.await(5, TimeUnit.SECONDS), "A should receive from B");
        assertTrue(latchB.await(5, TimeUnit.SECONDS), "B should receive from A");

        assertEquals("B to A", messageToA.get());
        assertEquals("A to B", messageToB.get());
    }
}
