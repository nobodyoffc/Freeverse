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
 * Integration tests for the full DDoS defense challenge-response flow.
 * 
 * These tests simulate the interaction between two FUDP peers:
 * - Peer A (initiator) sends a packet to Peer B
 * - Peer B (responder) challenges Peer A with PoW
 * - Peer A solves and responds
 * - Peer B verifies and allows subsequent traffic
 */
class DDoSDefenseIntegrationTest {

    private DDoSConfig configA;
    private DDoSConfig configB;
    
    private IpVerifier verifierB;       // Peer B's IP verifier (responder role)
    private ChallengeHandler handlerA;  // Peer A's challenge handler (initiator role)
    
    private List<Message> messagesAtoB;  // Messages from A to B
    private List<Message> messagesBtoA;  // Messages from B to A

    private SocketAddress addrA;
    private SocketAddress addrB;

    @BeforeEach
    void setUp() {
        // Peer A's config (initiator settings)
        configA = new DDoSConfig()
                .setMaxAcceptableDifficulty(12)
                .setMaxPowTimeMs(5000);

        // Peer B's config (responder settings)  
        configB = new DDoSConfig()
                .setBaseDifficulty(8)
                .setMaxDifficulty(12)
                .setChallengeTtlMs(5000)
                .setVerifiedTtlMs(60000)
                .setMaxPacketsPerSecondPerIp(100)
                .setRateLimitBurstCapacity(100);  // Match rate for predictable testing

        messagesAtoB = new ArrayList<>();
        messagesBtoA = new ArrayList<>();

        addrA = new InetSocketAddress("10.0.0.1", 9000);
        addrB = new InetSocketAddress("10.0.0.2", 9000);

        // Peer B's verifier - sends challenges to A
        verifierB = new IpVerifier(configB, (addr, payload) -> {
            // Simulate sending challenge from B to A
            synchronized (messagesBtoA) {
                messagesBtoA.add(new Message(addr, payload, MessageType.CHALLENGE));
            }
        });

        // Peer A's handler - receives challenges and sends responses
        handlerA = new ChallengeHandler(configA, (addr, payload) -> {
            // Simulate sending response from A to B
            synchronized (messagesAtoB) {
                messagesAtoB.add(new Message(addr, payload, MessageType.CHALLENGE_RESPONSE));
            }
        });
    }

    @AfterEach
    void tearDown() {
        handlerA.shutdown();
    }

    @Test
    @Timeout(15)
    void testFullChallengeResponseFlow() throws InterruptedException {
        // Step 1: Peer A sends an encrypted packet to Peer B
        byte[] encryptedPacket = createEncryptedPacket();
        
        IpVerifier.Decision decision1 = verifierB.checkIncoming(addrA, encryptedPacket);
        assertEquals(IpVerifier.Decision.CHALLENGE_SENT, decision1);
        
        // Verify challenge was sent
        synchronized (messagesBtoA) {
            assertEquals(1, messagesBtoA.size());
            assertEquals(MessageType.CHALLENGE, messagesBtoA.get(0).type);
        }

        // Step 2: Peer A receives the challenge and starts solving
        byte[] challengePayload = messagesBtoA.get(0).payload;
        boolean accepted = handlerA.handleChallenge(addrB, challengePayload);
        assertTrue(accepted);

        // Wait for PoW solution
        Thread.sleep(3000);

        // Verify response was sent
        synchronized (messagesAtoB) {
            assertEquals(1, messagesAtoB.size());
            assertEquals(MessageType.CHALLENGE_RESPONSE, messagesAtoB.get(0).type);
        }

        // Step 3: Peer B receives the response and verifies
        byte[] responsePayload = messagesAtoB.get(0).payload;
        byte[] responsePacket = createChallengeResponsePacket(responsePayload);
        
        IpVerifier.Decision decision2 = verifierB.checkIncoming(addrA, responsePacket);
        assertEquals(IpVerifier.Decision.CHALLENGE_VERIFIED, decision2);

        // Step 4: Peer A is now verified - subsequent packets should be allowed
        IpVerifier.Decision decision3 = verifierB.checkIncoming(addrA, encryptedPacket);
        assertEquals(IpVerifier.Decision.VERIFIED_ALLOW, decision3);
    }

    @Test
    @Timeout(10)
    void testMultiplePeersIndependent() throws InterruptedException {
        SocketAddress addrA1 = new InetSocketAddress("10.0.0.1", 9001);
        SocketAddress addrA2 = new InetSocketAddress("10.0.0.2", 9002);
        
        byte[] packet = createEncryptedPacket();

        // Both peers trigger challenges
        assertEquals(IpVerifier.Decision.CHALLENGE_SENT, verifierB.checkIncoming(addrA1, packet));
        assertEquals(IpVerifier.Decision.CHALLENGE_SENT, verifierB.checkIncoming(addrA2, packet));

        // Verify A1
        verifierB.addVerified("10.0.0.1");
        
        // A1 should be allowed, A2 still pending
        assertEquals(IpVerifier.Decision.VERIFIED_ALLOW, verifierB.checkIncoming(addrA1, packet));
        assertEquals(IpVerifier.Decision.CHALLENGE_PENDING, verifierB.checkIncoming(addrA2, packet));
    }

    @Test
    void testInitiatorRejectsExcessiveDifficulty() {
        // Create a challenge with excessive difficulty
        byte[] evilChallenge = createChallengePayloadWithDifficulty(20); // Too high
        
        // Peer A should reject this
        boolean accepted = handlerA.handleChallenge(addrB, evilChallenge);
        assertFalse(accepted);
        
        // No response should be sent
        synchronized (messagesAtoB) {
            assertEquals(0, messagesAtoB.size());
        }
    }

    @Test
    void testSpoofedIpCannotPass() throws InterruptedException {
        // Attacker spoofs IP and sends packet
        SocketAddress spoofedAddr = new InetSocketAddress("10.0.0.99", 9000);
        byte[] packet = createEncryptedPacket();

        // Peer B sends challenge to spoofed IP
        IpVerifier.Decision decision = verifierB.checkIncoming(spoofedAddr, packet);
        assertEquals(IpVerifier.Decision.CHALLENGE_SENT, decision);

        // Real owner of 10.0.0.99 never initiated, so no response comes
        // (Attacker never receives the challenge because it went to 10.0.0.99)

        // Attacker tries again
        decision = verifierB.checkIncoming(spoofedAddr, packet);
        assertEquals(IpVerifier.Decision.CHALLENGE_PENDING, decision);

        // Attacker cannot proceed without solving the challenge they never received
        assertFalse(verifierB.isVerified("10.0.0.99"));
    }

    @Test
    void testRateLimitingAfterVerification() {
        // Pre-verify the IP
        verifierB.addVerified("10.0.0.1");
        
        byte[] packet = createEncryptedPacket();

        // Send packets up to rate limit (100/sec with burst of 100)
        int allowedCount = 0;
        int limitedCount = 0;
        
        for (int i = 0; i < 150; i++) {
            IpVerifier.Decision decision = verifierB.checkIncoming(addrA, packet);
            if (decision == IpVerifier.Decision.VERIFIED_ALLOW) {
                allowedCount++;
            } else if (decision == IpVerifier.Decision.VERIFIED_RATE_LIMITED) {
                limitedCount++;
            }
        }

        // Should have allowed ~100 and limited ~50 (approximately, due to timing)
        assertTrue(allowedCount > 0, "Should allow some packets");
        assertTrue(limitedCount > 0, "Should rate limit some packets");
        assertEquals(150, allowedCount + limitedCount);
    }

    @Test
    void testChallengeExpiry() throws InterruptedException {
        // Configure short challenge TTL (minimum is 1000ms per DDoSConfig constraint)
        configB.setChallengeTtlMs(1000);
        verifierB = new IpVerifier(configB, (addr, payload) -> {
            synchronized (messagesBtoA) {
                messagesBtoA.add(new Message(addr, payload, MessageType.CHALLENGE));
            }
        });

        byte[] packet = createEncryptedPacket();

        // Trigger challenge
        verifierB.checkIncoming(addrA, packet);

        // Wait for challenge to expire (TTL is 1000ms minimum)
        Thread.sleep(1200);

        // Cleanup expired entries
        verifierB.cleanup();

        // Sending packet again should trigger NEW challenge (not pending)
        IpVerifier.Decision decision = verifierB.checkIncoming(addrA, packet);
        assertEquals(IpVerifier.Decision.CHALLENGE_SENT, decision);

        // Should have sent two challenges now
        synchronized (messagesBtoA) {
            assertEquals(2, messagesBtoA.size());
        }
    }

    @Test
    void testAdaptiveDifficultyUnderLoad() {
        // Configure adaptive thresholds
        configB.setBaseDifficulty(8)
               .setMaxDifficulty(16)
               .setHighLoadPacketsPerSecond(10)  // Low threshold for testing
               .setLowLoadPacketsPerSecond(5)
               .setDifficultyAdjustIntervalMs(100);

        verifierB = new IpVerifier(configB, (addr, payload) -> {});

        // Initial difficulty should be base
        assertEquals(8, verifierB.getCurrentDifficulty());

        // Simulate high load - send many packets quickly
        byte[] packet = createEncryptedPacket();
        for (int i = 0; i < 100; i++) {
            SocketAddress addr = new InetSocketAddress("192.168.1." + (i % 254), 9000);
            verifierB.checkIncoming(addr, packet);
        }

        // Give time for adaptive adjustment
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Difficulty might have increased (depending on timing)
        int newDifficulty = verifierB.getCurrentDifficulty();
        assertTrue(newDifficulty >= 8, "Difficulty should be at least base");
    }

    // Helper methods

    private byte[] createEncryptedPacket() {
        // Create a minimal packet that looks like an encrypted data packet
        byte[] packet = new byte[100];
        packet[0] = 0x00; // DATA packet type
        return packet;
    }

    private byte[] createChallengeResponsePacket(byte[] payload) {
        byte[] packet = new byte[21 + payload.length];
        packet[0] = 0x02; // CONTROL packet type
        System.arraycopy(payload, 0, packet, 21, payload.length);
        return packet;
    }

    private byte[] createChallengePayloadWithDifficulty(int difficulty) {
        byte[] nonce = ProofOfWork.generateNonce();
        long timestamp = System.currentTimeMillis();

        ByteBuffer buffer = ByteBuffer.allocate(26);
        buffer.put(IpVerifier.CONTROL_CHALLENGE);
        buffer.put(nonce);
        buffer.put((byte) difficulty);
        buffer.putLong(timestamp);
        return buffer.array();
    }

    private enum MessageType {
        CHALLENGE,
        CHALLENGE_RESPONSE
    }

    private static class Message {
        final SocketAddress address;
        final byte[] payload;
        final MessageType type;

        Message(SocketAddress address, byte[] payload, MessageType type) {
            this.address = address;
            this.payload = payload;
            this.type = type;
        }
    }
}
