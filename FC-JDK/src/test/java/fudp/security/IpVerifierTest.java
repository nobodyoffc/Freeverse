package fudp.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IpVerifier.
 */
class IpVerifierTest {

    private DDoSConfig config;
    private List<ChallengeRecord> sentChallenges;
    private IpVerifier verifier;

    @BeforeEach
    void setUp() {
        config = new DDoSConfig()
                .setBaseDifficulty(8)  // Low difficulty for fast tests
                .setMaxDifficulty(12)
                .setChallengeTtlMs(5000)
                .setVerifiedTtlMs(3600000)
                .setMaxPendingChallenges(100)
                .setMaxPacketsPerSecondPerIp(10)
                .setRateLimitBurstCapacity(10);  // Match rate for predictable testing

        sentChallenges = new ArrayList<>();

        verifier = new IpVerifier(config, (addr, payload) -> {
            sentChallenges.add(new ChallengeRecord(addr, payload));
        });
    }

    @Test
    void testUnverifiedIpGetsChallenged() {
        SocketAddress addr = new InetSocketAddress("192.168.1.1", 12345);
        byte[] packet = createEncryptedPacket();

        IpVerifier.Decision decision = verifier.checkIncoming(addr, packet);

        assertEquals(IpVerifier.Decision.CHALLENGE_SENT, decision);
        assertEquals(1, sentChallenges.size());

        // Verify challenge was sent to correct address
        assertEquals(addr, sentChallenges.get(0).address);
    }

    @Test
    void testSecondPacketFromSameIpReturnsPending() {
        SocketAddress addr = new InetSocketAddress("192.168.1.1", 12345);
        byte[] packet = createEncryptedPacket();

        // First packet triggers challenge
        verifier.checkIncoming(addr, packet);

        // Second packet should return CHALLENGE_PENDING
        IpVerifier.Decision decision = verifier.checkIncoming(addr, packet);

        assertEquals(IpVerifier.Decision.CHALLENGE_PENDING, decision);
        assertEquals(1, sentChallenges.size()); // Only one challenge sent
    }

    @Test
    @Timeout(10)
    void testValidChallengeResponseVerifiesIp() throws TimeoutException {
        SocketAddress addr = new InetSocketAddress("192.168.1.1", 12345);
        byte[] packet = createEncryptedPacket();

        // Trigger challenge
        verifier.checkIncoming(addr, packet);
        ChallengeRecord challenge = sentChallenges.get(0);

        // Parse the challenge to get nonce and difficulty
        IpVerifier.ParsedChallenge parsed = IpVerifier.parseChallenge(challenge.payload);
        assertNotNull(parsed);

        // Solve the PoW
        byte[] solution = ProofOfWork.solve(parsed.nonce, parsed.difficulty, 5000);

        // Create and verify response
        byte[] responsePayload = IpVerifier.buildChallengeResponsePayload(parsed.nonce, solution);
        byte[] responsePacket = createChallengeResponsePacket(responsePayload);

        IpVerifier.Decision decision = verifier.checkIncoming(addr, responsePacket);

        assertEquals(IpVerifier.Decision.CHALLENGE_VERIFIED, decision);

        // Now the IP should be verified
        assertTrue(verifier.isVerified(extractIp(addr)));
    }

    @Test
    void testVerifiedIpAllowed() throws TimeoutException {
        SocketAddress addr = new InetSocketAddress("192.168.1.1", 12345);
        
        // Manually add to verified list
        verifier.addVerified("192.168.1.1");
        
        byte[] packet = createEncryptedPacket();
        IpVerifier.Decision decision = verifier.checkIncoming(addr, packet);

        assertEquals(IpVerifier.Decision.VERIFIED_ALLOW, decision);
        assertEquals(0, sentChallenges.size()); // No challenge sent
    }

    @Test
    void testVerifiedIpRateLimited() {
        SocketAddress addr = new InetSocketAddress("192.168.1.1", 12345);
        
        // Manually add to verified list
        verifier.addVerified("192.168.1.1");
        
        byte[] packet = createEncryptedPacket();

        // Exhaust rate limit (config has 10 packets/sec with burst of 10)
        for (int i = 0; i < 10; i++) {
            IpVerifier.Decision decision = verifier.checkIncoming(addr, packet);
            assertEquals(IpVerifier.Decision.VERIFIED_ALLOW, decision);
        }

        // Next packet should be rate limited
        IpVerifier.Decision decision = verifier.checkIncoming(addr, packet);
        assertEquals(IpVerifier.Decision.VERIFIED_RATE_LIMITED, decision);
    }

    @Test
    void testInvalidChallengeResponseFails() {
        SocketAddress addr = new InetSocketAddress("192.168.1.1", 12345);
        byte[] packet = createEncryptedPacket();

        // Trigger challenge
        verifier.checkIncoming(addr, packet);

        // Create invalid response (wrong nonce)
        byte[] wrongNonce = ProofOfWork.generateNonce();
        byte[] fakeSolution = new byte[8];
        byte[] responsePayload = IpVerifier.buildChallengeResponsePayload(wrongNonce, fakeSolution);
        byte[] responsePacket = createChallengeResponsePacket(responsePayload);

        IpVerifier.Decision decision = verifier.checkIncoming(addr, responsePacket);

        assertEquals(IpVerifier.Decision.CHALLENGE_FAILED, decision);
        assertFalse(verifier.isVerified("192.168.1.1"));
    }

    @Test
    void testChallengeOverload() {
        // Create a fresh config with minimum max pending (100 is the minimum per DDoSConfig constraint)
        DDoSConfig overloadConfig = new DDoSConfig()
                .setBaseDifficulty(8)
                .setMaxPendingChallenges(100);  // This is the minimum allowed
        
        List<ChallengeRecord> overloadChallenges = new ArrayList<>();
        IpVerifier overloadVerifier = new IpVerifier(overloadConfig, (addr, payload) -> {
            overloadChallenges.add(new ChallengeRecord(addr, payload));
        });

        byte[] packet = createEncryptedPacket();

        // Fill up pending challenges to the limit
        for (int i = 0; i < 100; i++) {
            SocketAddress addr = new InetSocketAddress("10.0." + (i / 256) + "." + (i % 256), 12345);
            IpVerifier.Decision decision = overloadVerifier.checkIncoming(addr, packet);
            assertEquals(IpVerifier.Decision.CHALLENGE_SENT, decision, "Challenge " + i + " should be sent");
        }

        // Next should be overloaded
        SocketAddress overflowAddr = new InetSocketAddress("192.168.1.100", 12345);
        IpVerifier.Decision decision = overloadVerifier.checkIncoming(overflowAddr, packet);

        assertEquals(IpVerifier.Decision.CHALLENGE_OVERLOAD, decision);
    }

    @Test
    void testDisabledDDoSDefense() {
        config.setEnabled(false);
        verifier = new IpVerifier(config, (addr, payload) -> {
            sentChallenges.add(new ChallengeRecord(addr, payload));
        });

        SocketAddress addr = new InetSocketAddress("192.168.1.1", 12345);
        byte[] packet = createEncryptedPacket();

        IpVerifier.Decision decision = verifier.checkIncoming(addr, packet);

        assertEquals(IpVerifier.Decision.DISABLED, decision);
        assertEquals(0, sentChallenges.size());
    }

    @Test
    void testRemoveVerified() {
        verifier.addVerified("192.168.1.1");
        assertTrue(verifier.isVerified("192.168.1.1"));

        verifier.removeVerified("192.168.1.1");
        assertFalse(verifier.isVerified("192.168.1.1"));
    }

    @Test
    void testStats() {
        SocketAddress addr1 = new InetSocketAddress("192.168.1.1", 12345);
        SocketAddress addr2 = new InetSocketAddress("192.168.1.2", 12345);
        byte[] packet = createEncryptedPacket();

        // Initial stats
        IpVerifier.Stats stats = verifier.getStats();
        assertEquals(0, stats.verifiedCount);
        assertEquals(0, stats.pendingCount);

        // Add pending
        verifier.checkIncoming(addr1, packet);
        verifier.checkIncoming(addr2, packet);
        stats = verifier.getStats();
        assertEquals(0, stats.verifiedCount);
        assertEquals(2, stats.pendingCount);

        // Add verified
        verifier.addVerified("192.168.1.100");
        stats = verifier.getStats();
        assertEquals(1, stats.verifiedCount);
        assertEquals(2, stats.pendingCount);
    }

    @Test
    void testParseChallenge() {
        byte[] nonce = ProofOfWork.generateNonce();
        int difficulty = 12;
        long timestamp = System.currentTimeMillis();

        // Build challenge payload
        ByteBuffer buffer = ByteBuffer.allocate(26);
        buffer.put(IpVerifier.CONTROL_CHALLENGE);
        buffer.put(nonce);
        buffer.put((byte) difficulty);
        buffer.putLong(timestamp);
        byte[] payload = buffer.array();

        // Parse
        IpVerifier.ParsedChallenge parsed = IpVerifier.parseChallenge(payload);

        assertNotNull(parsed);
        assertArrayEquals(nonce, parsed.nonce);
        assertEquals(difficulty, parsed.difficulty);
        assertEquals(timestamp, parsed.timestamp);
    }

    @Test
    void testParseChallengeInvalid() {
        // Too short
        assertNull(IpVerifier.parseChallenge(new byte[10]));

        // Wrong type
        byte[] wrongType = new byte[26];
        wrongType[0] = 0x00;
        assertNull(IpVerifier.parseChallenge(wrongType));

        // Null
        assertNull(IpVerifier.parseChallenge(null));
    }

    @Test
    void testCleanup() throws TimeoutException {
        // Add a verified IP
        verifier.addVerified("192.168.1.1");
        assertTrue(verifier.isVerified("192.168.1.1"));

        // Add a pending challenge
        SocketAddress addr = new InetSocketAddress("192.168.1.2", 12345);
        verifier.checkIncoming(addr, createEncryptedPacket());

        IpVerifier.Stats statsBefore = verifier.getStats();
        assertEquals(1, statsBefore.verifiedCount);
        assertEquals(1, statsBefore.pendingCount);

        // Cleanup shouldn't remove non-expired entries
        verifier.cleanup();

        IpVerifier.Stats statsAfter = verifier.getStats();
        assertEquals(1, statsAfter.verifiedCount);
        assertEquals(1, statsAfter.pendingCount);
    }

    // Helper methods

    private byte[] createEncryptedPacket() {
        // Create a minimal packet that looks like an encrypted data packet
        // Header (21 bytes): flags=0x00 (DATA type), version, connectionId, packetNumber
        // Plus some encrypted payload
        byte[] packet = new byte[100];
        packet[0] = 0x00; // DATA packet type
        return packet;
    }

    private byte[] createChallengeResponsePacket(byte[] payload) {
        // Create a control packet with CHALLENGE_RESPONSE payload
        // Header (21 bytes): flags=0x02 (CONTROL type)
        // Plus payload
        byte[] packet = new byte[21 + payload.length];
        packet[0] = 0x02; // CONTROL packet type
        System.arraycopy(payload, 0, packet, 21, payload.length);
        return packet;
    }

    private String extractIp(SocketAddress addr) {
        if (addr instanceof InetSocketAddress inet) {
            return inet.getAddress().getHostAddress();
        }
        return null;
    }

    private static class ChallengeRecord {
        final SocketAddress address;
        final byte[] payload;

        ChallengeRecord(SocketAddress address, byte[] payload) {
            this.address = address;
            this.payload = payload;
        }
    }
}
