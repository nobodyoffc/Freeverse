package fudp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * IP verification with Proof-of-Work challenge for DDoS defense.
 * 
 * This is the core component that protects FUDP nodes from:
 * - IP spoofing attacks (challenge goes to real IP, only real owner can respond)
 * - CPU exhaustion attacks (no crypto until IP is verified)
 * - Connection flooding (rate limiting per verified IP)
 * 
 * All incoming packets must pass through this verifier BEFORE any expensive
 * cryptographic operations are performed.
 */
public class IpVerifier {
    private static final Logger log = LoggerFactory.getLogger(IpVerifier.class);
    
    /**
     * Decision returned by the verifier.
     */
    public enum Decision {
        /** Verified IP, within rate limit - process normally */
        VERIFIED_ALLOW,
        
        /** Verified IP, but rate limit exceeded - drop packet */
        VERIFIED_RATE_LIMITED,
        
        /** New IP, challenge was sent - drop packet, await response */
        CHALLENGE_SENT,
        
        /** Challenge already pending for this IP - drop packet */
        CHALLENGE_PENDING,
        
        /** Too many pending challenges (under attack) - drop packet */
        CHALLENGE_OVERLOAD,
        
        /** Challenge response verified, IP now whitelisted - process packet */
        CHALLENGE_VERIFIED,
        
        /** Challenge response failed (wrong solution) - drop packet */
        CHALLENGE_FAILED,
        
        /** DDoS defense is disabled - process normally */
        DISABLED
    }
    
    // Verified IPs with rate limiting
    private final ConcurrentHashMap<String, VerifiedEntry> verified = new ConcurrentHashMap<>();
    
    // Pending challenges waiting for response
    private final ConcurrentHashMap<String, ChallengeEntry> pending = new ConcurrentHashMap<>();
    
    // Configuration
    private final DDoSConfig config;
    
    // Adaptive difficulty
    private final AtomicInteger currentDifficulty;
    private final AtomicLong lastDifficultyAdjust = new AtomicLong(0);
    private final AtomicLong packetCount = new AtomicLong(0);
    private final AtomicLong lastPacketCountReset = new AtomicLong(System.currentTimeMillis());
    
    // Callback to send challenge packets
    private final BiConsumer<SocketAddress, byte[]> challengeSender;
    
    // Control packet type constants (must match Protocol.java)
    public static final byte CONTROL_CHALLENGE = 0x03;
    public static final byte CONTROL_CHALLENGE_RESPONSE = 0x04;
    
    /**
     * Create an IP verifier.
     * 
     * @param config the DDoS configuration
     * @param challengeSender callback to send challenge packets (address, payload)
     */
    public IpVerifier(DDoSConfig config, BiConsumer<SocketAddress, byte[]> challengeSender) {
        this.config = config;
        this.challengeSender = challengeSender;
        this.currentDifficulty = new AtomicInteger(config.getBaseDifficulty());
    }
    
    /**
     * Check an incoming packet and decide what to do.
     * 
     * This method MUST be called before any expensive operations (like decryption).
     * 
     * @param from the source address
     * @param packet the raw packet data
     * @return the decision on how to handle this packet
     */
    public Decision checkIncoming(SocketAddress from, byte[] packet) {
        if (!config.isEnabled()) {
            return Decision.DISABLED;
        }
        
        String ipKey = extractIpKey(from);
        if (ipKey == null) {
            return Decision.CHALLENGE_OVERLOAD; // Can't extract IP, drop
        }
        
        // Track packet rate for adaptive difficulty
        packetCount.incrementAndGet();
        
        // Step 0: Check if this is a CHALLENGE packet - must allow through for DDoS flow to work
        // CHALLENGE packets are part of the DDoS defense mechanism itself
        if (isChallenge(packet)) {
            return Decision.DISABLED; // Allow CHALLENGE packets through to be processed by ChallengeHandler
        }
        
        // Step 1: Check if this is a challenge response
        if (isChallengeResponse(packet)) {
            return handleChallengeResponse(from, ipKey, packet);
        }
        
        // Step 2: Check if IP is already verified
        VerifiedEntry verifiedEntry = verified.get(ipKey);
        if (verifiedEntry != null) {
            if (verifiedEntry.isExpired()) {
                verified.remove(ipKey);
            } else {
                // Verified IP - check rate limit
                if (verifiedEntry.tryConsume()) {
                    return Decision.VERIFIED_ALLOW;
                } else {
                    return Decision.VERIFIED_RATE_LIMITED;
                }
            }
        }
        
        // Step 3: Unverified IP - send challenge
        return sendChallenge(from, ipKey);
    }
    
    /**
     * Check if a packet is a CHALLENGE control packet.
     * CHALLENGE packets must be allowed through so the ChallengeHandler can process them.
     */
    private boolean isChallenge(byte[] packet) {
        // Minimum packet size: header (21) + control type (1) + nonce (16) + difficulty (1) + timestamp (8) = 47
        if (packet == null || packet.length < 47) {
            return false;
        }
        
        // Check packet type is CONTROL (bits 0-1 of flags = 0x02)
        byte flags = packet[0];
        int packetType = flags & 0x03;
        if (packetType != 0x02) { // PACKET_TYPE_CONTROL
            return false;
        }
        
        // Check control type is CHALLENGE
        // Control payload starts after header (21 bytes)
        boolean isChallenge = packet.length > 21 && packet[21] == CONTROL_CHALLENGE;
        if (isChallenge) {
            log.debug("[IpVerifier] Received CHALLENGE packet, allowing through for processing");
        }
        return isChallenge;
    }
    
    /**
     * Check if a packet is a CHALLENGE_RESPONSE control packet.
     */
    private boolean isChallengeResponse(byte[] packet) {
        // Minimum packet size: header (21) + control type (1) + nonce (16) + solution (8) = 46
        if (packet == null || packet.length < 46) {
            return false;
        }
        
        // Check packet type is CONTROL (bits 0-1 of flags = 0x02)
        byte flags = packet[0];
        int packetType = flags & 0x03;
        if (packetType != 0x02) { // PACKET_TYPE_CONTROL
            return false;
        }
        
        // Check control type is CHALLENGE_RESPONSE
        // Control payload starts after header (21 bytes)
        return packet.length > 21 && packet[21] == CONTROL_CHALLENGE_RESPONSE;
    }
    
    /**
     * Handle a challenge response packet.
     */
    private Decision handleChallengeResponse(SocketAddress from, String ipKey, byte[] packet) {
        // Extract nonce and solution from packet
        // Packet structure: header (21) + type (1) + nonce (16) + solution (8)
        if (packet.length < 46) {
            return Decision.CHALLENGE_FAILED;
        }
        
        byte[] nonce = Arrays.copyOfRange(packet, 22, 38);
        byte[] solution = Arrays.copyOfRange(packet, 38, 46);
        
        // Find pending challenge
        ChallengeEntry challenge = pending.get(ipKey);
        if (challenge == null) {
            log.debug("[IpVerifier] No pending challenge for {}", ipKey);
            return Decision.CHALLENGE_FAILED;
        }
        
        if (challenge.isExpired()) {
            pending.remove(ipKey);
            log.debug("[IpVerifier] Challenge expired for {}", ipKey);
            return Decision.CHALLENGE_FAILED;
        }
        
        // Verify nonce matches
        if (!Arrays.equals(nonce, challenge.nonce)) {
            log.debug("[IpVerifier] Nonce mismatch for {}", ipKey);
            return Decision.CHALLENGE_FAILED;
        }
        
        // Verify PoW solution
        if (!ProofOfWork.verify(nonce, solution, challenge.difficulty)) {
            log.debug("[IpVerifier] Invalid PoW solution for {}", ipKey);
            return Decision.CHALLENGE_FAILED;
        }
        
        // Success! Add to verified whitelist
        pending.remove(ipKey);
        verified.put(ipKey, new VerifiedEntry(
                System.currentTimeMillis() + config.getVerifiedTtlMs(),
                new TokenBucket(config.getMaxPacketsPerSecondPerIp(), config.getRateLimitBurstCapacity())
        ));
        
        log.debug("[IpVerifier] IP verified: {}", ipKey);
        return Decision.CHALLENGE_VERIFIED;
    }
    
    /**
     * Send a challenge to an unverified IP.
     */
    private Decision sendChallenge(SocketAddress from, String ipKey) {
        // Check if already pending
        ChallengeEntry existing = pending.get(ipKey);
        if (existing != null && !existing.isExpired()) {
            return Decision.CHALLENGE_PENDING;
        }
        
        // Check pending limit
        if (pending.size() >= config.getMaxPendingChallenges()) {
            log.warn("[IpVerifier] Challenge overload, pending={}", pending.size());
            return Decision.CHALLENGE_OVERLOAD;
        }
        
        // Create challenge
        byte[] nonce = ProofOfWork.generateNonce();
        int difficulty = getCurrentDifficulty();
        long expiresAt = System.currentTimeMillis() + config.getChallengeTtlMs();
        
        pending.put(ipKey, new ChallengeEntry(nonce, difficulty, expiresAt));
        
        // Build and send challenge packet
        byte[] challengePayload = buildChallengePayload(nonce, difficulty);
        if (challengeSender != null) {
            challengeSender.accept(from, challengePayload);
        }
        
        log.debug("[IpVerifier] Sent challenge to {} (difficulty={})", ipKey, difficulty);
        return Decision.CHALLENGE_SENT;
    }
    
    /**
     * Build the payload for a CHALLENGE control packet.
     * 
     * Structure: type (1) + nonce (16) + difficulty (1) + timestamp (8) = 26 bytes
     */
    private byte[] buildChallengePayload(byte[] nonce, int difficulty) {
        ByteBuffer buffer = ByteBuffer.allocate(26);
        buffer.put(CONTROL_CHALLENGE);
        buffer.put(nonce);
        buffer.put((byte) difficulty);
        buffer.putLong(System.currentTimeMillis());
        return buffer.array();
    }
    
    /**
     * Build a CHALLENGE_RESPONSE payload.
     * 
     * Structure: type (1) + nonce (16) + solution (8) = 25 bytes
     * 
     * @param nonce the challenge nonce
     * @param solution the PoW solution
     * @return the response payload
     */
    public static byte[] buildChallengeResponsePayload(byte[] nonce, byte[] solution) {
        ByteBuffer buffer = ByteBuffer.allocate(25);
        buffer.put(CONTROL_CHALLENGE_RESPONSE);
        buffer.put(nonce);
        buffer.put(solution);
        return buffer.array();
    }
    
    /**
     * Parse a CHALLENGE packet payload.
     * 
     * @param payload the payload (26 bytes: type + nonce + difficulty + timestamp)
     * @return parsed challenge or null if invalid
     */
    public static ParsedChallenge parseChallenge(byte[] payload) {
        if (payload == null || payload.length < 26) {
            return null;
        }
        if (payload[0] != CONTROL_CHALLENGE) {
            return null;
        }
        
        byte[] nonce = Arrays.copyOfRange(payload, 1, 17);
        int difficulty = payload[17] & 0xFF;
        long timestamp = ByteBuffer.wrap(payload, 18, 8).getLong();
        
        return new ParsedChallenge(nonce, difficulty, timestamp);
    }
    
    /**
     * Parsed challenge data.
     */
    public static class ParsedChallenge {
        public final byte[] nonce;
        public final int difficulty;
        public final long timestamp;
        
        public ParsedChallenge(byte[] nonce, int difficulty, long timestamp) {
            this.nonce = nonce;
            this.difficulty = difficulty;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Get current adaptive difficulty.
     */
    public int getCurrentDifficulty() {
        adjustDifficultyIfNeeded();
        return currentDifficulty.get();
    }
    
    /**
     * Adjust difficulty based on packet rate.
     */
    private void adjustDifficultyIfNeeded() {
        long now = System.currentTimeMillis();
        long lastAdjust = lastDifficultyAdjust.get();
        
        if (now - lastAdjust < config.getDifficultyAdjustIntervalMs()) {
            return;
        }
        
        if (!lastDifficultyAdjust.compareAndSet(lastAdjust, now)) {
            return; // Another thread is adjusting
        }
        
        // Calculate packet rate
        long lastReset = lastPacketCountReset.get();
        long elapsed = now - lastReset;
        if (elapsed <= 0) return;
        
        long count = packetCount.getAndSet(0);
        lastPacketCountReset.set(now);
        
        double packetsPerSecond = (count * 1000.0) / elapsed;
        
        int current = currentDifficulty.get();
        int newDifficulty = current;
        
        if (packetsPerSecond > config.getHighLoadPacketsPerSecond()) {
            // High load - increase difficulty
            newDifficulty = Math.min(config.getMaxDifficulty(), 
                    current + config.getDifficultyIncreaseStep());
        } else if (packetsPerSecond < config.getLowLoadPacketsPerSecond()) {
            // Low load - decrease difficulty
            newDifficulty = Math.max(config.getBaseDifficulty(), 
                    current - config.getDifficultyDecreaseStep());
        }
        
        if (newDifficulty != current) {
            currentDifficulty.set(newDifficulty);
            log.info("[IpVerifier] Adjusted difficulty {} -> {} (rate={}/s)", 
                    current, newDifficulty, (int) packetsPerSecond);
        }
    }
    
    /**
     * Extract IP key from socket address.
     */
    private String extractIpKey(SocketAddress address) {
        if (address instanceof InetSocketAddress inet) {
            return inet.getAddress().getHostAddress();
        }
        return null;
    }
    
    /**
     * Manually add an IP to the verified whitelist.
     * Useful for trusted peers or local connections.
     */
    public void addVerified(String ip) {
        verified.put(ip, new VerifiedEntry(
                System.currentTimeMillis() + config.getVerifiedTtlMs(),
                new TokenBucket(config.getMaxPacketsPerSecondPerIp(), config.getRateLimitBurstCapacity())
        ));
    }
    
    /**
     * Check if an IP is verified.
     */
    public boolean isVerified(String ip) {
        VerifiedEntry entry = verified.get(ip);
        return entry != null && !entry.isExpired();
    }
    
    /**
     * Remove an IP from the verified whitelist.
     */
    public void removeVerified(String ip) {
        verified.remove(ip);
    }
    
    /**
     * Cleanup expired entries.
     * Should be called periodically.
     */
    public void cleanup() {
        // Cleanup expired verified entries
        Iterator<Map.Entry<String, VerifiedEntry>> verifiedIt = verified.entrySet().iterator();
        while (verifiedIt.hasNext()) {
            if (verifiedIt.next().getValue().isExpired()) {
                verifiedIt.remove();
            }
        }
        
        // Cleanup expired pending challenges
        Iterator<Map.Entry<String, ChallengeEntry>> pendingIt = pending.entrySet().iterator();
        while (pendingIt.hasNext()) {
            if (pendingIt.next().getValue().isExpired()) {
                pendingIt.remove();
            }
        }
    }
    
    /**
     * Get statistics for monitoring.
     */
    public Stats getStats() {
        return new Stats(
                verified.size(),
                pending.size(),
                currentDifficulty.get()
        );
    }
    
    /**
     * Statistics for monitoring.
     */
    public static class Stats {
        public final int verifiedCount;
        public final int pendingCount;
        public final int currentDifficulty;
        
        public Stats(int verifiedCount, int pendingCount, int currentDifficulty) {
            this.verifiedCount = verifiedCount;
            this.pendingCount = pendingCount;
            this.currentDifficulty = currentDifficulty;
        }
    }
    
    /**
     * Verified IP entry with rate limiter.
     */
    private static class VerifiedEntry {
        final long expiresAt;
        final TokenBucket rateLimiter;
        
        VerifiedEntry(long expiresAt, TokenBucket rateLimiter) {
            this.expiresAt = expiresAt;
            this.rateLimiter = rateLimiter;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
        
        boolean tryConsume() {
            return rateLimiter.tryConsume();
        }
    }
    
    /**
     * Pending challenge entry.
     */
    private static class ChallengeEntry {
        final byte[] nonce;
        final int difficulty;
        final long expiresAt;
        
        ChallengeEntry(byte[] nonce, int difficulty, long expiresAt) {
            this.nonce = nonce;
            this.difficulty = difficulty;
            this.expiresAt = expiresAt;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
