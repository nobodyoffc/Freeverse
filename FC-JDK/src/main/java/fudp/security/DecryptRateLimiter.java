package fudp.security;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-source rate limiter for the decrypt path.
 *
 * <p>The decrypt path runs ECDH on cache miss (~1 ms of CPU per call). A
 * peer that floods packets bearing fresh per-packet pubkeys can force
 * ECDH-per-packet and exhaust server CPU. {@link IpVerifier} mitigates
 * this when DDoS defense is enabled, but defense is opt-in (off by
 * default) and once an IP is verified there is no second-line defense.
 *
 * <p>This class tracks recent decrypt failures per source IP and tells
 * the receive loop to drop further packets from that IP for a cooldown
 * period after a configurable threshold of consecutive failures. The
 * cooldown skips the ECDH attempt entirely.
 *
 * <p>The cooldown is short (default 1 second) so legitimate peers that
 * recover from a transient issue resume promptly, but long enough to
 * make a sustained flood self-throttling.
 *
 * <p>Thread-safety: callers must serialise calls to {@link #shouldDrop},
 * {@link #recordFailure}, and {@link #recordSuccess}, OR use the same
 * instance from a single thread. Protocol.receiveLoop is single-threaded,
 * so we use simple synchronization here without finer-grained locks.
 */
public class DecryptRateLimiter {

    public static final int DEFAULT_FAILURE_THRESHOLD = 5;
    public static final long DEFAULT_COOLDOWN_MS = 1_000L;
    public static final int DEFAULT_MAX_TRACKED = 4096;

    private static final long ENTRY_TTL_MS = 60_000L; // drop unused entries after 1 minute

    private final int failureThreshold;
    private final long cooldownMs;
    private final int maxTracked;

    /** Per-source state, bounded LRU. Key is the source IP string (port-less). */
    private final LinkedHashMap<String, Entry> entries =
            new LinkedHashMap<>(16, 0.75f, /*accessOrder=*/ true);

    public DecryptRateLimiter() {
        this(DEFAULT_FAILURE_THRESHOLD, DEFAULT_COOLDOWN_MS, DEFAULT_MAX_TRACKED);
    }

    public DecryptRateLimiter(int failureThreshold, long cooldownMs, int maxTracked) {
        if (failureThreshold <= 0) throw new IllegalArgumentException("failureThreshold must be positive");
        if (cooldownMs <= 0) throw new IllegalArgumentException("cooldownMs must be positive");
        if (maxTracked <= 0) throw new IllegalArgumentException("maxTracked must be positive");
        this.failureThreshold = failureThreshold;
        this.cooldownMs = cooldownMs;
        this.maxTracked = maxTracked;
    }

    /**
     * Decide whether to drop a packet from {@code from} BEFORE running the
     * expensive decrypt path. Returns true iff the source is currently in
     * a cooldown window.
     */
    public synchronized boolean shouldDrop(SocketAddress from) {
        String key = ipKey(from);
        if (key == null) return false;
        Entry e = entries.get(key);
        if (e == null) return false;
        // No cooldown active → allow through; do NOT touch the failure count
        // (otherwise a shouldDrop() call between recordFailure() calls would
        // reset and the threshold could never be reached).
        if (e.cooldownUntil == 0) return false;
        long now = System.currentTimeMillis();
        if (now < e.cooldownUntil) {
            return true;
        }
        // Cooldown expired — clear failure count so the peer gets a fresh
        // chance, then return false (allow the packet through).
        e.failures = 0;
        e.cooldownUntil = 0;
        return false;
    }

    /**
     * Record a decrypt failure for {@code from}. After the configured
     * threshold of consecutive failures, future calls to {@link #shouldDrop}
     * will return true until {@code cooldownMs} has elapsed.
     */
    public synchronized void recordFailure(SocketAddress from) {
        String key = ipKey(from);
        if (key == null) return;
        Entry e = entries.get(key);
        long now = System.currentTimeMillis();
        if (e == null) {
            evictIfNeeded();
            e = new Entry();
            entries.put(key, e);
        }
        e.failures++;
        e.lastTouchedMs = now;
        if (e.failures >= failureThreshold) {
            e.cooldownUntil = now + cooldownMs;
        }
    }

    /**
     * Record a decrypt success for {@code from}: clears the failure count
     * so a legitimate peer that recovers does not stay penalised.
     */
    public synchronized void recordSuccess(SocketAddress from) {
        String key = ipKey(from);
        if (key == null) return;
        Entry e = entries.get(key);
        if (e == null) return;
        e.failures = 0;
        e.cooldownUntil = 0;
        e.lastTouchedMs = System.currentTimeMillis();
    }

    /** Number of currently tracked sources (for monitoring). */
    public synchronized int getTrackedCount() {
        return entries.size();
    }

    /** Active cooldown window in ms (for monitoring/tests). */
    public long getCooldownMs() {
        return cooldownMs;
    }

    /** Failure threshold (for monitoring/tests). */
    public int getFailureThreshold() {
        return failureThreshold;
    }

    private void evictIfNeeded() {
        long now = System.currentTimeMillis();
        // Drop stale (TTL-expired) entries first.
        Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Entry> me = it.next();
            if (now - me.getValue().lastTouchedMs > ENTRY_TTL_MS) {
                it.remove();
            }
        }
        // Hard cap: evict eldest until under the cap.
        while (entries.size() >= maxTracked) {
            Iterator<Map.Entry<String, Entry>> evictor = entries.entrySet().iterator();
            if (!evictor.hasNext()) break;
            evictor.next();
            evictor.remove();
        }
    }

    private static String ipKey(SocketAddress addr) {
        if (addr instanceof InetSocketAddress inet && inet.getAddress() != null) {
            return inet.getAddress().getHostAddress();
        }
        return null;
    }

    private static final class Entry {
        int failures;
        long cooldownUntil;
        long lastTouchedMs;
    }
}
