package fudp.security;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Token bucket rate limiter for per-IP packet rate limiting.
 * 
 * Allows burst traffic up to bucket capacity, then limits to a steady rate.
 * Thread-safe implementation using atomic operations.
 */
public class TokenBucket {
    
    private final int capacity;           // Maximum tokens (burst capacity)
    private final double refillRate;      // Tokens per millisecond
    private final AtomicLong tokens;      // Current tokens * 1000 (for precision)
    private final AtomicLong lastRefillTime;
    
    private static final long PRECISION = 1000; // Fixed-point precision
    
    /**
     * Create a token bucket rate limiter.
     * 
     * @param tokensPerSecond the sustained rate limit
     * @param burstCapacity the maximum burst size (typically 1-2x tokensPerSecond)
     */
    public TokenBucket(int tokensPerSecond, int burstCapacity) {
        if (tokensPerSecond <= 0) {
            throw new IllegalArgumentException("Tokens per second must be positive");
        }
        if (burstCapacity <= 0) {
            throw new IllegalArgumentException("Burst capacity must be positive");
        }
        
        this.capacity = burstCapacity;
        this.refillRate = tokensPerSecond / 1000.0; // tokens per millisecond
        this.tokens = new AtomicLong(burstCapacity * PRECISION);
        this.lastRefillTime = new AtomicLong(System.currentTimeMillis());
    }
    
    /**
     * Create a token bucket with burst capacity equal to rate.
     * 
     * @param tokensPerSecond the rate limit (also used as burst capacity)
     */
    public TokenBucket(int tokensPerSecond) {
        this(tokensPerSecond, tokensPerSecond);
    }
    
    /**
     * Try to consume one token.
     * 
     * @return true if token was consumed, false if rate limited
     */
    public boolean tryConsume() {
        return tryConsume(1);
    }
    
    /**
     * Try to consume multiple tokens.
     * 
     * @param count number of tokens to consume
     * @return true if tokens were consumed, false if rate limited
     */
    public boolean tryConsume(int count) {
        if (count <= 0) {
            return true;
        }
        
        long requiredTokens = count * PRECISION;
        
        // Refill tokens based on elapsed time
        refill();
        
        // Try to consume tokens atomically
        while (true) {
            long currentTokens = tokens.get();
            if (currentTokens < requiredTokens) {
                return false; // Not enough tokens
            }
            
            if (tokens.compareAndSet(currentTokens, currentTokens - requiredTokens)) {
                return true; // Successfully consumed
            }
            // CAS failed, retry
        }
    }
    
    /**
     * Refill tokens based on elapsed time.
     */
    private void refill() {
        long now = System.currentTimeMillis();
        long last = lastRefillTime.get();
        long elapsed = now - last;
        
        if (elapsed <= 0) {
            return;
        }
        
        // Calculate tokens to add
        long tokensToAdd = (long) (elapsed * refillRate * PRECISION);
        if (tokensToAdd <= 0) {
            return;
        }
        
        // Update last refill time
        if (!lastRefillTime.compareAndSet(last, now)) {
            // Another thread updated, skip this refill
            return;
        }
        
        // Add tokens up to capacity
        long maxTokens = capacity * PRECISION;
        while (true) {
            long currentTokens = tokens.get();
            long newTokens = Math.min(maxTokens, currentTokens + tokensToAdd);
            if (tokens.compareAndSet(currentTokens, newTokens)) {
                break;
            }
        }
    }
    
    /**
     * Get current token count (approximate, for monitoring).
     * 
     * @return approximate number of available tokens
     */
    public int getAvailableTokens() {
        refill();
        return (int) (tokens.get() / PRECISION);
    }
    
    /**
     * Get the rate limit in tokens per second.
     * 
     * @return tokens per second
     */
    public int getRate() {
        return (int) (refillRate * 1000);
    }
    
    /**
     * Get the burst capacity.
     * 
     * @return maximum burst size
     */
    public int getCapacity() {
        return capacity;
    }
    
    /**
     * Reset the bucket to full capacity.
     */
    public void reset() {
        tokens.set(capacity * PRECISION);
        lastRefillTime.set(System.currentTimeMillis());
    }
}
