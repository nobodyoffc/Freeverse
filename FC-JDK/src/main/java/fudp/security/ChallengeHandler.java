package fudp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Handles incoming challenges when this node is initiating a connection.
 * 
 * In a P2P system, when we connect to another peer, they may send us a
 * CHALLENGE packet to verify our IP. This handler:
 * 
 * 1. Receives the challenge
 * 2. Checks if the difficulty is acceptable (protection against malicious peers)
 * 3. Solves the PoW within time limits
 * 4. Sends the response
 * 
 * This provides protection for the initiating peer against malicious peers
 * that might try to waste our CPU with excessive PoW difficulty.
 */
public class ChallengeHandler {
    private static final Logger log = LoggerFactory.getLogger(ChallengeHandler.class);
    
    private final DDoSConfig config;
    private final BiConsumer<SocketAddress, byte[]> responseSender;
    private final ExecutorService solverExecutor;
    
    // Track high-difficulty challenges per peer for reputation
    private final ConcurrentHashMap<String, AtomicInteger> highDifficultyCounts = new ConcurrentHashMap<>();
    
    // Pending solve operations
    private final ConcurrentHashMap<String, CompletableFuture<byte[]>> pendingSolves = new ConcurrentHashMap<>();
    
    /**
     * Create a challenge handler.
     * 
     * @param config the DDoS configuration
     * @param responseSender callback to send challenge response packets (address, payload)
     */
    public ChallengeHandler(DDoSConfig config, BiConsumer<SocketAddress, byte[]> responseSender) {
        this.config = config;
        this.responseSender = responseSender;
        this.solverExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "PoW-Solver");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Handle an incoming CHALLENGE packet.
     * 
     * @param from the source address (peer who sent the challenge)
     * @param payload the challenge payload (from control packet)
     * @return true if challenge was accepted and solving started, false if rejected
     */
    public boolean handleChallenge(SocketAddress from, byte[] payload) {
        log.debug("[ChallengeHandler] Received CHALLENGE from {} (payload {} bytes)", from, payload != null ? payload.length : 0);
        
        // Parse the challenge
        IpVerifier.ParsedChallenge challenge = IpVerifier.parseChallenge(payload);
        if (challenge == null) {
            log.warn("[ChallengeHandler] Invalid challenge from {}", from);
            return false;
        }
        
        String peerKey = extractPeerKey(from);
        int difficulty = challenge.difficulty;
        log.debug("[ChallengeHandler] Challenge from {} - difficulty={}", peerKey, difficulty);
        
        // Check if difficulty is acceptable
        if (difficulty > config.getMaxAcceptableDifficulty()) {
            log.warn("[ChallengeHandler] Refusing excessive difficulty {} from {} (max={})", 
                    difficulty, peerKey, config.getMaxAcceptableDifficulty());
            recordHighDifficulty(peerKey);
            return false;
        }
        
        // Check if we're already solving for this peer
        if (pendingSolves.containsKey(peerKey)) {
            log.debug("[ChallengeHandler] Already solving challenge for {}", peerKey);
            return true;
        }
        
        // Reset high difficulty count on normal challenge
        highDifficultyCounts.remove(peerKey);
        
        // Solve asynchronously
        CompletableFuture<byte[]> future = solveAsync(peerKey, challenge.nonce, difficulty);
        pendingSolves.put(peerKey, future);
        
        future.whenComplete((solution, error) -> {
            pendingSolves.remove(peerKey);
            
            if (error != null) {
                if (error instanceof TimeoutException) {
                    log.warn("[ChallengeHandler] PoW timeout for {} (difficulty={})", peerKey, difficulty);
                } else if (error instanceof ExcessiveDifficultyException) {
                    log.warn("[ChallengeHandler] Excessive difficulty from {}: {}", peerKey, error.getMessage());
                } else {
                    log.error("[ChallengeHandler] PoW solve failed for {}", peerKey, error);
                }
                return;
            }
            
            if (solution != null) {
                // Build and send response
                byte[] responsePayload = IpVerifier.buildChallengeResponsePayload(challenge.nonce, solution);
                if (responseSender != null) {
                    responseSender.accept(from, responsePayload);
                    log.debug("[ChallengeHandler] Sent challenge response to {} (difficulty={})", peerKey, difficulty);
                }
            }
        });
        
        return true;
    }
    
    /**
     * Solve PoW asynchronously with timeout.
     */
    private CompletableFuture<byte[]> solveAsync(String peerKey, byte[] nonce, int difficulty) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        
        solverExecutor.submit(() -> {
            try {
                byte[] solution = ProofOfWork.solveInterruptible(
                        nonce, 
                        difficulty, 
                        config.getMaxPowTimeMs(),
                        10000 // Check for interruption every 10k attempts
                );
                future.complete(solution);
            } catch (TimeoutException e) {
                future.completeExceptionally(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        
        // Schedule timeout
        CompletableFuture.delayedExecutor(config.getMaxPowTimeMs() + 100, TimeUnit.MILLISECONDS)
                .execute(() -> {
                    if (!future.isDone()) {
                        future.completeExceptionally(new TimeoutException("PoW solve timeout"));
                    }
                });
        
        return future;
    }
    
    /**
     * Record a high-difficulty challenge from a peer.
     * Used for reputation tracking.
     */
    private void recordHighDifficulty(String peerKey) {
        AtomicInteger count = highDifficultyCounts.computeIfAbsent(peerKey, k -> new AtomicInteger(0));
        int newCount = count.incrementAndGet();
        
        if (newCount >= config.getMaxConsecutiveHighDifficulty()) {
            log.warn("[ChallengeHandler] Peer {} sent {} consecutive high-difficulty challenges, consider blacklisting", 
                    peerKey, newCount);
            // Upper layer can check isPeerSuspicious() and take action
        }
    }
    
    /**
     * Check if a peer has been sending suspicious (high difficulty) challenges.
     * 
     * @param address the peer's address
     * @return true if the peer should be considered suspicious
     */
    public boolean isPeerSuspicious(SocketAddress address) {
        String peerKey = extractPeerKey(address);
        AtomicInteger count = highDifficultyCounts.get(peerKey);
        return count != null && count.get() >= config.getMaxConsecutiveHighDifficulty();
    }
    
    /**
     * Get the high-difficulty count for a peer.
     */
    public int getHighDifficultyCount(SocketAddress address) {
        String peerKey = extractPeerKey(address);
        AtomicInteger count = highDifficultyCounts.get(peerKey);
        return count != null ? count.get() : 0;
    }
    
    /**
     * Reset the high-difficulty count for a peer (e.g., after successful connection).
     */
    public void resetPeerReputation(SocketAddress address) {
        String peerKey = extractPeerKey(address);
        highDifficultyCounts.remove(peerKey);
    }
    
    /**
     * Check if we're currently solving a challenge for a peer.
     */
    public boolean isSolving(SocketAddress address) {
        String peerKey = extractPeerKey(address);
        return pendingSolves.containsKey(peerKey);
    }
    
    /**
     * Cancel any pending solve for a peer.
     */
    public void cancelSolve(SocketAddress address) {
        String peerKey = extractPeerKey(address);
        CompletableFuture<byte[]> future = pendingSolves.remove(peerKey);
        if (future != null) {
            future.cancel(true);
        }
    }
    
    /**
     * Get statistics for monitoring.
     */
    public Stats getStats() {
        return new Stats(
                pendingSolves.size(),
                highDifficultyCounts.size()
        );
    }
    
    /**
     * Shutdown the handler.
     */
    public void shutdown() {
        solverExecutor.shutdown();
        try {
            if (!solverExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                solverExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            solverExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Cancel all pending solves
        for (CompletableFuture<byte[]> future : pendingSolves.values()) {
            future.cancel(true);
        }
        pendingSolves.clear();
    }
    
    /**
     * Cleanup old entries.
     */
    public void cleanup() {
        // High difficulty counts are automatically managed, but we can clear very old ones
        // For now, just clear peers with no recent activity (simple approach)
        if (highDifficultyCounts.size() > 10000) {
            // Too many entries, clear older half
            int toRemove = highDifficultyCounts.size() / 2;
            var iterator = highDifficultyCounts.entrySet().iterator();
            while (iterator.hasNext() && toRemove > 0) {
                iterator.next();
                iterator.remove();
                toRemove--;
            }
        }
    }
    
    private String extractPeerKey(SocketAddress address) {
        if (address instanceof InetSocketAddress inet) {
            return inet.getAddress().getHostAddress() + ":" + inet.getPort();
        }
        return address.toString();
    }
    
    /**
     * Statistics for monitoring.
     */
    public static class Stats {
        public final int pendingSolves;
        public final int suspiciousPeers;
        
        public Stats(int pendingSolves, int suspiciousPeers) {
            this.pendingSolves = pendingSolves;
            this.suspiciousPeers = suspiciousPeers;
        }
    }
    
    /**
     * Exception thrown when a peer requests excessive PoW difficulty.
     */
    public static class ExcessiveDifficultyException extends RuntimeException {
        private final int requestedDifficulty;
        private final int maxAcceptable;
        
        public ExcessiveDifficultyException(int requestedDifficulty, int maxAcceptable) {
            super("Requested difficulty " + requestedDifficulty + " exceeds maximum acceptable " + maxAcceptable);
            this.requestedDifficulty = requestedDifficulty;
            this.maxAcceptable = maxAcceptable;
        }
        
        public int getRequestedDifficulty() {
            return requestedDifficulty;
        }
        
        public int getMaxAcceptable() {
            return maxAcceptable;
        }
    }
}
