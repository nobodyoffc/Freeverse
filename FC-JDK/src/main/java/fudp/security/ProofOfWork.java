package fudp.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.TimeoutException;

/**
 * Proof-of-Work utilities for FUDP DDoS defense.
 * 
 * Uses SHA-256 hash-based proof of work where the solution must produce
 * a hash with a specified number of leading zero bits.
 * 
 * This is used to verify IP addresses are real (not spoofed) by requiring
 * computational work that only the actual IP owner can complete.
 */
public class ProofOfWork {
    
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    // Difficulty levels and approximate solve times on modern CPU
    // 8 bits  = ~256 attempts = <1ms
    // 12 bits = ~4096 attempts = ~5ms
    // 16 bits = ~65536 attempts = ~50ms
    // 20 bits = ~1M attempts = ~500ms
    // 24 bits = ~16M attempts = ~8 seconds
    
    public static final int MIN_DIFFICULTY = 4;
    public static final int MAX_DIFFICULTY = 24;
    public static final int DEFAULT_DIFFICULTY = 12;
    
    /**
     * Generate a random nonce for a challenge.
     * 
     * @return 16-byte random nonce
     */
    public static byte[] generateNonce() {
        byte[] nonce = new byte[16];
        SECURE_RANDOM.nextBytes(nonce);
        return nonce;
    }
    
    /**
     * Verify a proof-of-work solution.
     * 
     * Checks that SHA256(nonce || solution) has at least 'difficulty' leading zero bits.
     * 
     * @param nonce the challenge nonce (16 bytes)
     * @param solution the proposed solution (8 bytes)
     * @param difficulty the required number of leading zero bits
     * @return true if the solution is valid
     */
    public static boolean verify(byte[] nonce, byte[] solution, int difficulty) {
        if (nonce == null || nonce.length != 16) {
            return false;
        }
        if (solution == null || solution.length != 8) {
            return false;
        }
        if (difficulty < MIN_DIFFICULTY || difficulty > MAX_DIFFICULTY) {
            return false;
        }
        
        byte[] hash = computeHash(nonce, solution);
        return countLeadingZeroBits(hash) >= difficulty;
    }
    
    /**
     * Solve a proof-of-work challenge.
     * 
     * Finds a solution where SHA256(nonce || solution) has at least 'difficulty' leading zero bits.
     * 
     * @param nonce the challenge nonce (16 bytes)
     * @param difficulty the required number of leading zero bits
     * @param timeoutMs maximum time to spend solving (in milliseconds)
     * @return the solution (8 bytes)
     * @throws TimeoutException if solution not found within timeout
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static byte[] solve(byte[] nonce, int difficulty, long timeoutMs) throws TimeoutException {
        if (nonce == null || nonce.length != 16) {
            throw new IllegalArgumentException("Nonce must be 16 bytes");
        }
        if (difficulty < MIN_DIFFICULTY || difficulty > MAX_DIFFICULTY) {
            throw new IllegalArgumentException("Difficulty must be between " + MIN_DIFFICULTY + " and " + MAX_DIFFICULTY);
        }
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        
        long startTime = System.currentTimeMillis();
        long solution = 0;
        byte[] solutionBytes = new byte[8];
        
        while (true) {
            // Check timeout
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                throw new TimeoutException("PoW solve timeout after " + timeoutMs + "ms");
            }
            
            // Convert solution to bytes
            longToBytes(solution, solutionBytes);
            
            // Compute hash and check
            byte[] hash = computeHash(nonce, solutionBytes);
            if (countLeadingZeroBits(hash) >= difficulty) {
                return solutionBytes.clone();
            }
            
            solution++;
            
            // Prevent infinite loop on overflow (extremely unlikely with reasonable difficulty)
            if (solution == Long.MIN_VALUE) {
                throw new TimeoutException("PoW solution space exhausted");
            }
        }
    }
    
    /**
     * Solve a proof-of-work challenge with periodic check callback.
     * 
     * @param nonce the challenge nonce (16 bytes)
     * @param difficulty the required number of leading zero bits
     * @param timeoutMs maximum time to spend solving
     * @param checkIntervalAttempts how often to check for interruption
     * @return the solution (8 bytes)
     * @throws TimeoutException if solution not found within timeout
     * @throws InterruptedException if thread is interrupted
     */
    public static byte[] solveInterruptible(byte[] nonce, int difficulty, long timeoutMs, int checkIntervalAttempts) 
            throws TimeoutException, InterruptedException {
        if (nonce == null || nonce.length != 16) {
            throw new IllegalArgumentException("Nonce must be 16 bytes");
        }
        if (difficulty < MIN_DIFFICULTY || difficulty > MAX_DIFFICULTY) {
            throw new IllegalArgumentException("Difficulty must be between " + MIN_DIFFICULTY + " and " + MAX_DIFFICULTY);
        }
        
        long startTime = System.currentTimeMillis();
        long solution = 0;
        byte[] solutionBytes = new byte[8];
        int attemptsSinceCheck = 0;
        
        while (true) {
            // Periodic checks
            if (++attemptsSinceCheck >= checkIntervalAttempts) {
                attemptsSinceCheck = 0;
                
                // Check timeout
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    throw new TimeoutException("PoW solve timeout after " + timeoutMs + "ms");
                }
                
                // Check interruption
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("PoW solve interrupted");
                }
            }
            
            // Convert solution to bytes
            longToBytes(solution, solutionBytes);
            
            // Compute hash and check
            byte[] hash = computeHash(nonce, solutionBytes);
            if (countLeadingZeroBits(hash) >= difficulty) {
                return solutionBytes.clone();
            }
            
            solution++;
        }
    }
    
    /**
     * Estimate the time to solve a given difficulty level.
     * 
     * @param difficulty the difficulty level
     * @return estimated milliseconds (very approximate)
     */
    public static long estimateSolveTimeMs(int difficulty) {
        // Rough estimates based on ~1M hashes/second on modern CPU
        long expectedAttempts = 1L << difficulty;
        return Math.max(1, expectedAttempts / 1_000_000);
    }
    
    /**
     * Compute SHA-256 hash of nonce || solution.
     */
    private static byte[] computeHash(byte[] nonce, byte[] solution) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(nonce);
            digest.update(solution);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in Java
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    
    /**
     * Count leading zero bits in a byte array.
     * 
     * @param bytes the byte array (typically a hash)
     * @return number of leading zero bits
     */
    static int countLeadingZeroBits(byte[] bytes) {
        int count = 0;
        for (byte b : bytes) {
            if (b == 0) {
                count += 8;
            } else {
                // Count leading zeros in this byte
                int unsigned = b & 0xFF;
                count += Integer.numberOfLeadingZeros(unsigned) - 24; // Adjust for int size
                break;
            }
        }
        return count;
    }
    
    /**
     * Convert a long to 8 bytes (big-endian).
     */
    private static void longToBytes(long value, byte[] dest) {
        dest[0] = (byte) (value >>> 56);
        dest[1] = (byte) (value >>> 48);
        dest[2] = (byte) (value >>> 40);
        dest[3] = (byte) (value >>> 32);
        dest[4] = (byte) (value >>> 24);
        dest[5] = (byte) (value >>> 16);
        dest[6] = (byte) (value >>> 8);
        dest[7] = (byte) value;
    }
    
    /**
     * Convert 8 bytes to a long (big-endian).
     */
    public static long bytesToLong(byte[] bytes) {
        if (bytes == null || bytes.length != 8) {
            throw new IllegalArgumentException("Bytes must be 8 bytes");
        }
        return ((long) (bytes[0] & 0xFF) << 56)
                | ((long) (bytes[1] & 0xFF) << 48)
                | ((long) (bytes[2] & 0xFF) << 40)
                | ((long) (bytes[3] & 0xFF) << 32)
                | ((long) (bytes[4] & 0xFF) << 24)
                | ((long) (bytes[5] & 0xFF) << 16)
                | ((long) (bytes[6] & 0xFF) << 8)
                | ((long) (bytes[7] & 0xFF));
    }
}
