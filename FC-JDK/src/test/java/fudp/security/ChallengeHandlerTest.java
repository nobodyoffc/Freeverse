package fudp.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ChallengeHandler (initiator-side protection).
 */
class ChallengeHandlerTest {

    private DDoSConfig config;
    private List<ResponseRecord> sentResponses;
    private ChallengeHandler handler;

    @BeforeEach
    void setUp() {
        config = new DDoSConfig()
                .setMaxAcceptableDifficulty(12)  // Accept up to difficulty 12
                .setMaxPowTimeMs(5000)           // 5 second timeout
                .setMaxConsecutiveHighDifficulty(3);

        sentResponses = new ArrayList<>();

        handler = new ChallengeHandler(config, (addr, payload) -> {
            synchronized (sentResponses) {
                sentResponses.add(new ResponseRecord(addr, payload));
            }
        });
    }

    @AfterEach
    void tearDown() {
        handler.shutdown();
    }

    @Test
    @Timeout(10)
    void testHandleAcceptableChallenge() throws InterruptedException {
        SocketAddress addr = new InetSocketAddress("192.168.1.1", 9000);
        
        // Create challenge with acceptable difficulty
        byte[] challengePayload = createChallengePayload(8); // Low difficulty
        
        // Handle challenge
        boolean accepted = handler.handleChallenge(addr, challengePayload);
        assertTrue(accepted);
        
        // Wait for async solving
        Thread.sleep(2000);
        
        // Should have sent a response
        synchronized (sentResponses) {
            assertEquals(1, sentResponses.size());
            assertEquals(addr, sentResponses.get(0).address);
        }
    }

    @Test
    void testRejectExcessiveDifficulty() {
        SocketAddress addr = new InetSocketAddress("192.168.1.1", 9000);
        
        // Create challenge with excessive difficulty
        byte[] challengePayload = createChallengePayload(20); // Too high (max is 12)
        
        // Handle challenge - should reject
        boolean accepted = handler.handleChallenge(addr, challengePayload);
        assertFalse(accepted);
        
        // No response sent
        synchronized (sentResponses) {
            assertEquals(0, sentResponses.size());
        }
        
        // Should record high difficulty
        assertEquals(1, handler.getHighDifficultyCount(addr));
    }

    @Test
    void testSuspiciousPeerAfterMultipleHighDifficulty() {
        SocketAddress addr = new InetSocketAddress("192.168.1.1", 9000);
        
        // Send multiple high-difficulty challenges
        for (int i = 0; i < 3; i++) {
            byte[] challengePayload = createChallengePayload(20);
            handler.handleChallenge(addr, challengePayload);
        }
        
        // Peer should be marked suspicious
        assertTrue(handler.isPeerSuspicious(addr));
        assertEquals(3, handler.getHighDifficultyCount(addr));
    }

    @Test
    void testResetPeerReputation() {
        SocketAddress addr = new InetSocketAddress("192.168.1.1", 9000);
        
        // Make peer suspicious
        for (int i = 0; i < 3; i++) {
            handler.handleChallenge(addr, createChallengePayload(20));
        }
        assertTrue(handler.isPeerSuspicious(addr));
        
        // Reset reputation
        handler.resetPeerReputation(addr);
        
        assertFalse(handler.isPeerSuspicious(addr));
        assertEquals(0, handler.getHighDifficultyCount(addr));
    }

    @Test
    void testInvalidChallengePayload() {
        SocketAddress addr = new InetSocketAddress("192.168.1.1", 9000);
        
        // Too short payload
        boolean accepted = handler.handleChallenge(addr, new byte[10]);
        assertFalse(accepted);
        
        // Wrong type
        byte[] wrongType = new byte[26];
        wrongType[0] = 0x00; // Wrong type
        accepted = handler.handleChallenge(addr, wrongType);
        assertFalse(accepted);
    }

    @Test
    @Timeout(10)
    void testDuplicateChallengeIgnored() throws InterruptedException {
        SocketAddress addr = new InetSocketAddress("192.168.1.1", 9000);
        // Use higher difficulty so solve takes longer to test concurrent rejection
        byte[] challengePayload = createChallengePayload(12);
        
        // Handle first challenge
        boolean accepted1 = handler.handleChallenge(addr, challengePayload);
        assertTrue(accepted1, "First challenge should be accepted");
        
        // Immediately send duplicates while first is still solving
        // Difficulty 12 should take several ms to solve
        for (int i = 0; i < 10; i++) {
            boolean accepted = handler.handleChallenge(addr, challengePayload);
            assertTrue(accepted, "Duplicate challenge should return true (already being handled)");
        }
        
        // Wait for solve to complete
        Thread.sleep(3000);
        
        // Only one response should be sent - duplicates while solving should be ignored
        synchronized (sentResponses) {
            // Due to timing, we might get 1 or 2 responses if the solve completes 
            // between some of the duplicate calls, but definitely not 11
            assertTrue(sentResponses.size() <= 2, 
                    "At most 2 responses should be sent, got " + sentResponses.size());
            assertTrue(sentResponses.size() >= 1, 
                    "At least 1 response should be sent");
        }
    }

    @Test
    void testCancelSolve() throws InterruptedException {
        SocketAddress addr = new InetSocketAddress("192.168.1.1", 9000);
        byte[] challengePayload = createChallengePayload(12); // Higher difficulty takes longer
        
        // Start solving
        handler.handleChallenge(addr, challengePayload);
        assertTrue(handler.isSolving(addr));
        
        // Cancel
        handler.cancelSolve(addr);
        
        // Should no longer be solving
        assertFalse(handler.isSolving(addr));
    }

    @Test
    void testStats() {
        SocketAddress addr1 = new InetSocketAddress("192.168.1.1", 9000);
        SocketAddress addr2 = new InetSocketAddress("192.168.1.2", 9000);
        
        // Initial stats
        ChallengeHandler.Stats stats = handler.getStats();
        assertEquals(0, stats.pendingSolves);
        assertEquals(0, stats.suspiciousPeers);
        
        // Add suspicious peers
        handler.handleChallenge(addr1, createChallengePayload(20));
        handler.handleChallenge(addr2, createChallengePayload(20));
        
        stats = handler.getStats();
        assertEquals(0, stats.pendingSolves);
        assertEquals(2, stats.suspiciousPeers); // Two peers with high difficulty
    }

    @Test
    @Timeout(10)
    void testValidResponseFormat() throws InterruptedException {
        SocketAddress addr = new InetSocketAddress("192.168.1.1", 9000);
        byte[] challengePayload = createChallengePayload(8);
        
        // Handle challenge
        handler.handleChallenge(addr, challengePayload);
        
        // Wait for response
        Thread.sleep(2000);
        
        synchronized (sentResponses) {
            assertEquals(1, sentResponses.size());
            
            byte[] response = sentResponses.get(0).payload;
            
            // Response should be: type (1) + nonce (16) + solution (8) = 25 bytes
            assertEquals(25, response.length);
            assertEquals(IpVerifier.CONTROL_CHALLENGE_RESPONSE, response[0]);
        }
    }

    @Test
    void testNormalDifficultyResetsHighCount() throws InterruptedException {
        SocketAddress addr = new InetSocketAddress("192.168.1.1", 9000);
        
        // Send one high-difficulty challenge
        handler.handleChallenge(addr, createChallengePayload(20));
        assertEquals(1, handler.getHighDifficultyCount(addr));
        
        // Send normal-difficulty challenge
        handler.handleChallenge(addr, createChallengePayload(8));
        
        // High difficulty count should be reset
        assertEquals(0, handler.getHighDifficultyCount(addr));
    }

    @Test
    void testExcessiveDifficultyExceptionDetails() {
        int requested = 20;
        int max = 12;
        
        ChallengeHandler.ExcessiveDifficultyException ex = 
                new ChallengeHandler.ExcessiveDifficultyException(requested, max);
        
        assertEquals(requested, ex.getRequestedDifficulty());
        assertEquals(max, ex.getMaxAcceptable());
        assertTrue(ex.getMessage().contains("20"));
        assertTrue(ex.getMessage().contains("12"));
    }

    // Helper methods

    private byte[] createChallengePayload(int difficulty) {
        byte[] nonce = ProofOfWork.generateNonce();
        long timestamp = System.currentTimeMillis();

        ByteBuffer buffer = ByteBuffer.allocate(26);
        buffer.put(IpVerifier.CONTROL_CHALLENGE);
        buffer.put(nonce);
        buffer.put((byte) difficulty);
        buffer.putLong(timestamp);
        return buffer.array();
    }

    private static class ResponseRecord {
        final SocketAddress address;
        final byte[] payload;

        ResponseRecord(SocketAddress address, byte[] payload) {
            this.address = address;
            this.payload = payload;
        }
    }
}
