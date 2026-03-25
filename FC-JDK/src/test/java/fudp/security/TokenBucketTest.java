package fudp.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TokenBucket rate limiter.
 */
class TokenBucketTest {

    @Test
    void testConstructorValidation() {
        // Valid construction
        TokenBucket bucket = new TokenBucket(100, 200);
        assertEquals(100, bucket.getRate());
        assertEquals(200, bucket.getCapacity());
        
        // Single-argument constructor
        TokenBucket bucket2 = new TokenBucket(50);
        assertEquals(50, bucket2.getRate());
        assertEquals(50, bucket2.getCapacity());
        
        // Invalid arguments
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(0));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(-1));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(100, 0));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(100, -1));
    }

    @Test
    void testBurstCapacity() {
        // Bucket with 10 tokens/sec and burst capacity of 20
        TokenBucket bucket = new TokenBucket(10, 20);
        
        // Should be able to consume burst capacity immediately
        int consumed = 0;
        while (bucket.tryConsume()) {
            consumed++;
            if (consumed > 25) break; // Safety limit
        }
        
        // Should have consumed exactly the burst capacity
        assertEquals(20, consumed);
    }

    @Test
    void testRateLimiting() throws InterruptedException {
        // Bucket with 100 tokens/sec = 0.1 tokens/ms
        TokenBucket bucket = new TokenBucket(100, 10);
        
        // Exhaust initial tokens
        while (bucket.tryConsume()) {
            // Drain the bucket
        }
        
        // Should be rate limited now
        assertFalse(bucket.tryConsume());
        
        // Wait for refill (100ms should give us ~10 tokens)
        Thread.sleep(100);
        
        // Should have tokens again
        assertTrue(bucket.tryConsume());
    }

    @Test
    void testConsumeMultiple() {
        TokenBucket bucket = new TokenBucket(100, 100);
        
        // Consume 50 tokens at once
        assertTrue(bucket.tryConsume(50));
        assertEquals(50, bucket.getAvailableTokens());
        
        // Consume remaining 50
        assertTrue(bucket.tryConsume(50));
        assertEquals(0, bucket.getAvailableTokens());
        
        // Should be empty now
        assertFalse(bucket.tryConsume());
    }

    @Test
    void testConsumeZeroOrNegative() {
        TokenBucket bucket = new TokenBucket(100, 100);
        
        // Consuming 0 or negative should always succeed
        assertTrue(bucket.tryConsume(0));
        assertTrue(bucket.tryConsume(-1));
        
        // Available tokens shouldn't change
        assertEquals(100, bucket.getAvailableTokens());
    }

    @Test
    void testConsumeMoreThanAvailable() {
        TokenBucket bucket = new TokenBucket(100, 50);
        
        // Try to consume more than capacity
        assertFalse(bucket.tryConsume(100));
        
        // Bucket should still be full
        assertEquals(50, bucket.getAvailableTokens());
    }

    @Test
    void testReset() {
        TokenBucket bucket = new TokenBucket(100, 100);
        
        // Drain the bucket
        while (bucket.tryConsume()) {
            // Drain
        }
        assertEquals(0, bucket.getAvailableTokens());
        
        // Reset
        bucket.reset();
        assertEquals(100, bucket.getAvailableTokens());
    }

    @Test
    void testRefillDoesNotExceedCapacity() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(1000, 50);
        
        // Drain half the bucket
        bucket.tryConsume(25);
        assertEquals(25, bucket.getAvailableTokens());
        
        // Wait for refill (longer than needed to fully refill)
        Thread.sleep(200);
        
        // Should be at capacity, not over
        assertEquals(50, bucket.getAvailableTokens());
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        // Use a bucket with high capacity but NO refill during the test
        // by setting a very low rate
        TokenBucket bucket = new TokenBucket(1, 1000);  // 1 token/sec, 1000 burst
        
        // Multiple threads trying to consume
        int numThreads = 10;
        int consumePerThread = 200;
        Thread[] threads = new Thread[numThreads];
        int[] successCounts = new int[numThreads];
        
        for (int i = 0; i < numThreads; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < consumePerThread; j++) {
                    if (bucket.tryConsume()) {
                        successCounts[threadIndex]++;
                    }
                }
            });
        }
        
        // Start all threads simultaneously
        for (Thread t : threads) {
            t.start();
        }
        
        // Wait for completion
        for (Thread t : threads) {
            t.join();
        }
        
        // Total successful consumes
        int totalSuccess = 0;
        for (int count : successCounts) {
            totalSuccess += count;
        }
        
        // Should have consumed approximately the burst capacity (1000)
        // With 10 threads x 200 attempts = 2000 attempts, but only 1000 should succeed
        // Allow some tolerance for refill during execution
        assertTrue(totalSuccess >= 1000, "Should consume at least burst capacity");
        assertTrue(totalSuccess <= 1100, "Should not consume much more than burst capacity");
    }

    @Test
    void testGettersAfterOperations() {
        TokenBucket bucket = new TokenBucket(100, 150);
        
        // Initial state
        assertEquals(100, bucket.getRate());
        assertEquals(150, bucket.getCapacity());
        assertEquals(150, bucket.getAvailableTokens());
        
        // After consuming
        bucket.tryConsume(50);
        assertEquals(100, bucket.getAvailableTokens());
        
        // Getters should remain constant
        assertEquals(100, bucket.getRate());
        assertEquals(150, bucket.getCapacity());
    }
}
