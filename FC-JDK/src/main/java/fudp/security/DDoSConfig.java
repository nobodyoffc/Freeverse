package fudp.security;

/**
 * Configuration for FUDP DDoS defense.
 * 
 * This is a P2P design where every node can be both an initiator (connecting to others)
 * and a responder (receiving connections). Both roles have protection settings.
 */
public class DDoSConfig {
    
    // ========== Responder Settings (when receiving connections) ==========
    
    /**
     * Base PoW difficulty for new connections (leading zero bits).
     * ~5ms solve time at difficulty 12.
     */
    private int baseDifficulty = 12;
    
    /**
     * Maximum PoW difficulty under heavy load (leading zero bits).
     * ~500ms solve time at difficulty 20.
     */
    private int maxDifficulty = 20;
    
    /**
     * How long a verified IP stays in the whitelist (milliseconds).
     * Default: 1 hour.
     */
    private long verifiedTtlMs = 3600000;
    
    /**
     * How long to wait for a challenge response before expiring (milliseconds).
     * Default: 5 seconds.
     */
    private long challengeTtlMs = 5000;
    
    /**
     * Maximum number of pending challenges (to prevent memory exhaustion).
     * Default: 10000.
     */
    private int maxPendingChallenges = 10000;
    
    /**
     * Maximum packets per second from a single verified IP.
     * Default: 100 packets/second.
     */
    private int maxPacketsPerSecondPerIp = 100;
    
    /**
     * Burst capacity for rate limiting (allows short bursts above the rate limit).
     * Default: 200 packets.
     */
    private int rateLimitBurstCapacity = 200;
    
    // ========== Initiator Settings (when connecting to others) ==========
    
    /**
     * Maximum PoW difficulty we will accept from a remote peer.
     * Refuse to connect if difficulty exceeds this (protection against malicious peers).
     * Default: 16 (~50ms work).
     */
    private int maxAcceptableDifficulty = 16;
    
    /**
     * Maximum time to spend solving PoW (milliseconds).
     * Abort connection if PoW takes longer.
     * Default: 2 seconds.
     */
    private long maxPowTimeMs = 2000;
    
    /**
     * How many consecutive high-difficulty challenges before blacklisting a peer.
     * Default: 3.
     */
    private int maxConsecutiveHighDifficulty = 3;
    
    // ========== Adaptive Difficulty Settings ==========
    
    /**
     * Packet rate threshold to increase difficulty.
     * When incoming packet rate exceeds this, increase PoW difficulty.
     * Default: 5000 packets/second.
     */
    private int highLoadPacketsPerSecond = 5000;
    
    /**
     * Packet rate threshold to decrease difficulty.
     * When incoming packet rate drops below this, decrease PoW difficulty.
     * Default: 1000 packets/second.
     */
    private int lowLoadPacketsPerSecond = 1000;
    
    /**
     * How quickly to adjust difficulty (milliseconds between adjustments).
     * Default: 1 second.
     */
    private long difficultyAdjustIntervalMs = 1000;
    
    /**
     * How much to increase difficulty per adjustment step.
     * Default: 2 bits.
     */
    private int difficultyIncreaseStep = 2;
    
    /**
     * How much to decrease difficulty per adjustment step.
     * Default: 1 bit.
     */
    private int difficultyDecreaseStep = 1;
    
    // ========== Global Settings ==========
    
    /**
     * Whether DDoS defense is enabled.
     * Default: false (opt-in). Enable explicitly for internet-facing nodes.
     * When disabled, no challenge/rate-limiting is applied, allowing full-speed
     * peer-to-peer communication (file transfer, messaging, etc.).
     */
    private boolean enabled = false;
    
    /**
     * Cleanup interval for expired entries (milliseconds).
     * Default: 60 seconds.
     */
    private long cleanupIntervalMs = 60000;
    
    // ========== Getters and Setters ==========
    
    public int getBaseDifficulty() {
        return baseDifficulty;
    }
    
    public DDoSConfig setBaseDifficulty(int baseDifficulty) {
        this.baseDifficulty = Math.max(ProofOfWork.MIN_DIFFICULTY, 
                Math.min(baseDifficulty, ProofOfWork.MAX_DIFFICULTY));
        return this;
    }
    
    public int getMaxDifficulty() {
        return maxDifficulty;
    }
    
    public DDoSConfig setMaxDifficulty(int maxDifficulty) {
        this.maxDifficulty = Math.max(ProofOfWork.MIN_DIFFICULTY, 
                Math.min(maxDifficulty, ProofOfWork.MAX_DIFFICULTY));
        return this;
    }
    
    public long getVerifiedTtlMs() {
        return verifiedTtlMs;
    }
    
    public DDoSConfig setVerifiedTtlMs(long verifiedTtlMs) {
        this.verifiedTtlMs = Math.max(60000, verifiedTtlMs); // At least 1 minute
        return this;
    }
    
    public long getChallengeTtlMs() {
        return challengeTtlMs;
    }
    
    public DDoSConfig setChallengeTtlMs(long challengeTtlMs) {
        this.challengeTtlMs = Math.max(1000, Math.min(challengeTtlMs, 30000)); // 1-30 seconds
        return this;
    }
    
    public int getMaxPendingChallenges() {
        return maxPendingChallenges;
    }
    
    public DDoSConfig setMaxPendingChallenges(int maxPendingChallenges) {
        this.maxPendingChallenges = Math.max(100, maxPendingChallenges);
        return this;
    }
    
    public int getMaxPacketsPerSecondPerIp() {
        return maxPacketsPerSecondPerIp;
    }
    
    public DDoSConfig setMaxPacketsPerSecondPerIp(int maxPacketsPerSecondPerIp) {
        this.maxPacketsPerSecondPerIp = Math.max(10, maxPacketsPerSecondPerIp);
        return this;
    }
    
    public int getRateLimitBurstCapacity() {
        return rateLimitBurstCapacity;
    }
    
    public DDoSConfig setRateLimitBurstCapacity(int rateLimitBurstCapacity) {
        this.rateLimitBurstCapacity = Math.max(maxPacketsPerSecondPerIp, rateLimitBurstCapacity);
        return this;
    }
    
    public int getMaxAcceptableDifficulty() {
        return maxAcceptableDifficulty;
    }
    
    public DDoSConfig setMaxAcceptableDifficulty(int maxAcceptableDifficulty) {
        this.maxAcceptableDifficulty = Math.max(ProofOfWork.MIN_DIFFICULTY, 
                Math.min(maxAcceptableDifficulty, ProofOfWork.MAX_DIFFICULTY));
        return this;
    }
    
    public long getMaxPowTimeMs() {
        return maxPowTimeMs;
    }
    
    public DDoSConfig setMaxPowTimeMs(long maxPowTimeMs) {
        this.maxPowTimeMs = Math.max(100, maxPowTimeMs);
        return this;
    }
    
    public int getMaxConsecutiveHighDifficulty() {
        return maxConsecutiveHighDifficulty;
    }
    
    public DDoSConfig setMaxConsecutiveHighDifficulty(int maxConsecutiveHighDifficulty) {
        this.maxConsecutiveHighDifficulty = Math.max(1, maxConsecutiveHighDifficulty);
        return this;
    }
    
    public int getHighLoadPacketsPerSecond() {
        return highLoadPacketsPerSecond;
    }
    
    public DDoSConfig setHighLoadPacketsPerSecond(int highLoadPacketsPerSecond) {
        this.highLoadPacketsPerSecond = Math.max(100, highLoadPacketsPerSecond);
        return this;
    }
    
    public int getLowLoadPacketsPerSecond() {
        return lowLoadPacketsPerSecond;
    }
    
    public DDoSConfig setLowLoadPacketsPerSecond(int lowLoadPacketsPerSecond) {
        this.lowLoadPacketsPerSecond = Math.max(10, lowLoadPacketsPerSecond);
        return this;
    }
    
    public long getDifficultyAdjustIntervalMs() {
        return difficultyAdjustIntervalMs;
    }
    
    public DDoSConfig setDifficultyAdjustIntervalMs(long difficultyAdjustIntervalMs) {
        this.difficultyAdjustIntervalMs = Math.max(100, difficultyAdjustIntervalMs);
        return this;
    }
    
    public int getDifficultyIncreaseStep() {
        return difficultyIncreaseStep;
    }
    
    public DDoSConfig setDifficultyIncreaseStep(int difficultyIncreaseStep) {
        this.difficultyIncreaseStep = Math.max(1, Math.min(difficultyIncreaseStep, 4));
        return this;
    }
    
    public int getDifficultyDecreaseStep() {
        return difficultyDecreaseStep;
    }
    
    public DDoSConfig setDifficultyDecreaseStep(int difficultyDecreaseStep) {
        this.difficultyDecreaseStep = Math.max(1, Math.min(difficultyDecreaseStep, 4));
        return this;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public DDoSConfig setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }
    
    public long getCleanupIntervalMs() {
        return cleanupIntervalMs;
    }
    
    public DDoSConfig setCleanupIntervalMs(long cleanupIntervalMs) {
        this.cleanupIntervalMs = Math.max(1000, cleanupIntervalMs);
        return this;
    }
    
    /**
     * Create a copy of this configuration.
     */
    public DDoSConfig copy() {
        DDoSConfig copy = new DDoSConfig();
        copy.baseDifficulty = this.baseDifficulty;
        copy.maxDifficulty = this.maxDifficulty;
        copy.verifiedTtlMs = this.verifiedTtlMs;
        copy.challengeTtlMs = this.challengeTtlMs;
        copy.maxPendingChallenges = this.maxPendingChallenges;
        copy.maxPacketsPerSecondPerIp = this.maxPacketsPerSecondPerIp;
        copy.rateLimitBurstCapacity = this.rateLimitBurstCapacity;
        copy.maxAcceptableDifficulty = this.maxAcceptableDifficulty;
        copy.maxPowTimeMs = this.maxPowTimeMs;
        copy.maxConsecutiveHighDifficulty = this.maxConsecutiveHighDifficulty;
        copy.highLoadPacketsPerSecond = this.highLoadPacketsPerSecond;
        copy.lowLoadPacketsPerSecond = this.lowLoadPacketsPerSecond;
        copy.difficultyAdjustIntervalMs = this.difficultyAdjustIntervalMs;
        copy.difficultyIncreaseStep = this.difficultyIncreaseStep;
        copy.difficultyDecreaseStep = this.difficultyDecreaseStep;
        copy.enabled = this.enabled;
        copy.cleanupIntervalMs = this.cleanupIntervalMs;
        return copy;
    }
}
