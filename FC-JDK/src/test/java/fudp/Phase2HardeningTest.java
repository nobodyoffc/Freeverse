package fudp;

import core.crypto.CryptoDataByte;
import fudp.crypto.CryptoManager;
import fudp.security.DecryptRateLimiter;
import fudp.security.ReplayProtection;
import org.bitcoinj.core.ECKey;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests covering the Phase-2 hardening fixes:
 *  - F2: bounded LRU ECDH cache (with byte[]-keyed lookup, no per-packet hex)
 *  - F3: configurable replay-protection timestamp tolerance (default 60 s)
 *  - N1: per-source decrypt-failure rate limiter
 *  - N2: no PUBLIC_KEY reflection on decrypt failure (covered by absence; see
 *        Protocol.handleIncomingPacket — there is no test here because it is a
 *        deletion of a side-effect; the integration tests still pass).
 */
class Phase2HardeningTest {

    // ---------- F2: bounded LRU ECDH cache ----------

    @Test
    void ecdhCacheCapsAtConfiguredSizeAndEvictsLru() {
        // Cap = 4. Force 6 distinct peers → 2 evictions, size stays at cap.
        byte[] aliceKey = new ECKey().getPrivKeyBytes();
        CryptoManager alice = new CryptoManager(aliceKey, /*ecdhCacheCap=*/ 4);

        long evictionsBefore = alice.getEcdhEvictionCount();
        for (int i = 0; i < 6; i++) {
            byte[] peerPubkey = new ECKey().getPubKey();
            // Encrypt forces an ECDH lookup-or-compute for this peer.
            alice.encryptAsyTwoWay("hi".getBytes(), peerPubkey);
        }
        // Baseline + 2 evictions (6 inserts at cap 4).
        assertEquals(evictionsBefore + 2, alice.getEcdhEvictionCount(),
                "two LRU evictions expected when 6 peers exceed a cap of 4");
        assertEquals(4, alice.getEcdhCacheSize(), "cache size must equal cap after overflow");
    }

    @Test
    void ecdhCacheLookupReusesEntryNoFreshComputation() {
        byte[] aliceKey = new ECKey().getPrivKeyBytes();
        byte[] bobPubkey = new ECKey().getPubKey();
        CryptoManager alice = new CryptoManager(aliceKey, 16);

        // First call computes & caches, second is a hit.
        alice.encryptAsyTwoWay("hi".getBytes(), bobPubkey);
        long evictionsAfterFirst = alice.getEcdhEvictionCount();
        int sizeAfterFirst = alice.getEcdhCacheSize();

        for (int i = 0; i < 100; i++) {
            alice.encryptAsyTwoWay("hi".getBytes(), bobPubkey);
        }
        assertEquals(evictionsAfterFirst, alice.getEcdhEvictionCount(),
                "no evictions when same peer is hit repeatedly");
        assertEquals(sizeAfterFirst, alice.getEcdhCacheSize(),
                "cache size unchanged on hits");
    }

    @Test
    void ecdhCacheRejectsZeroOrNegativeCap() {
        byte[] aliceKey = new ECKey().getPrivKeyBytes();
        assertThrows(IllegalArgumentException.class, () -> new CryptoManager(aliceKey, 0));
        assertThrows(IllegalArgumentException.class, () -> new CryptoManager(aliceKey, -1));
    }

    @Test
    void ecdhRoundTripSurvivesOverflow() {
        // Sanity: the cache changes do not break correctness of encrypt/decrypt
        // even when LRU evictions repeatedly remove and recompute entries.
        byte[] aliceKey = new ECKey().getPrivKeyBytes();
        byte[] bobKey = new ECKey().getPrivKeyBytes();
        CryptoManager alice = new CryptoManager(aliceKey, /*cap=*/ 2);
        CryptoManager bob = new CryptoManager(bobKey, /*cap=*/ 2);

        // Force evictions on alice's side by encrypting to a churn of peers
        // between roundtrips with bob.
        for (int i = 0; i < 5; i++) {
            byte[] payload = ("msg-" + i).getBytes();
            CryptoDataByte enc = alice.encryptAsyTwoWay(payload, bob.getLocalPublicKey());
            byte[] dec = bob.decryptAsyTwoWay(enc);
            assertArrayEquals(payload, dec, "round-trip must survive cache eviction");
            // Churn: lookup three other peers to force eviction.
            for (int j = 0; j < 3; j++) {
                alice.encryptAsyTwoWay("noise".getBytes(), new ECKey().getPubKey());
            }
        }
    }

    // ---------- F3: configurable replay tolerance ----------

    @Test
    void replayProtectionDefaultToleranceIs60Seconds() {
        ReplayProtection rp = new ReplayProtection();
        assertEquals(60_000L, rp.getTimestampToleranceMs());
    }

    @Test
    void replayProtectionRejectsTimestampOutsideTolerance() {
        ReplayProtection rp = new ReplayProtection(64, /*toleranceMs=*/ 30_000L);
        long now = System.currentTimeMillis();

        // Within tolerance: OK
        assertEquals(ReplayProtection.CheckResult.OK,
                rp.checkAndRecord(1L, 1L, now - 10_000L, /*epoch*/ 1L));
        // 60 s in the past: outside the 30 s tolerance
        assertEquals(ReplayProtection.CheckResult.INVALID_TIMESTAMP,
                rp.checkAndRecord(2L, 1L, now - 60_000L, /*epoch*/ 2L));
        // 60 s in the future: also outside
        assertEquals(ReplayProtection.CheckResult.INVALID_TIMESTAMP,
                rp.checkAndRecord(3L, 1L, now + 60_000L, /*epoch*/ 3L));
    }

    @Test
    void replayProtectionRejectsToleranceOutsideAllowedRange() {
        // Below MIN
        assertThrows(IllegalArgumentException.class,
                () -> new ReplayProtection(64, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new ReplayProtection(64, 500L));
        // Above MAX (1 hour)
        assertThrows(IllegalArgumentException.class,
                () -> new ReplayProtection(64, 7_200_000L));
    }

    // ---------- N1: per-source decrypt-failure rate limit ----------

    @Test
    void rateLimiterDoesNotDropUntilThresholdReached() {
        DecryptRateLimiter rl = new DecryptRateLimiter(/*threshold=*/ 3, /*cooldownMs=*/ 10_000L, 64);
        SocketAddress src = new InetSocketAddress("10.0.0.1", 9000);

        // First two failures don't trigger a drop.
        rl.recordFailure(src);
        assertFalse(rl.shouldDrop(src));
        rl.recordFailure(src);
        assertFalse(rl.shouldDrop(src));
        // Third failure crosses the threshold.
        rl.recordFailure(src);
        assertTrue(rl.shouldDrop(src), "third consecutive failure must trigger cooldown");
    }

    @Test
    void rateLimiterCooldownExpires() throws InterruptedException {
        DecryptRateLimiter rl = new DecryptRateLimiter(2, /*cooldownMs=*/ 100L, 64);
        SocketAddress src = new InetSocketAddress("10.0.0.2", 9000);

        rl.recordFailure(src);
        rl.recordFailure(src);
        assertTrue(rl.shouldDrop(src), "cooldown active immediately after threshold");

        Thread.sleep(150);
        assertFalse(rl.shouldDrop(src), "cooldown clears after window elapses");
    }

    @Test
    void rateLimiterSuccessClearsHistory() {
        DecryptRateLimiter rl = new DecryptRateLimiter(3, 10_000L, 64);
        SocketAddress src = new InetSocketAddress("10.0.0.3", 9000);

        rl.recordFailure(src);
        rl.recordFailure(src);
        rl.recordSuccess(src); // peer recovered
        rl.recordFailure(src);
        rl.recordFailure(src);
        // Only 2 failures since last success; should not drop.
        assertFalse(rl.shouldDrop(src), "success must reset failure count");
    }

    @Test
    void rateLimiterIsolatesSources() {
        DecryptRateLimiter rl = new DecryptRateLimiter(2, 10_000L, 64);
        SocketAddress a = new InetSocketAddress("10.0.0.4", 9000);
        SocketAddress b = new InetSocketAddress("10.0.0.5", 9000);

        rl.recordFailure(a);
        rl.recordFailure(a);
        assertTrue(rl.shouldDrop(a));
        // b is unaffected.
        assertFalse(rl.shouldDrop(b));
    }

    @Test
    void rateLimiterUnknownSourceTypeIsAllowed() {
        DecryptRateLimiter rl = new DecryptRateLimiter();
        // Non-Inet address — limiter cannot key it, should allow through.
        SocketAddress weird = new SocketAddress() {};
        assertFalse(rl.shouldDrop(weird));
        rl.recordFailure(weird); // must not throw
        assertFalse(rl.shouldDrop(weird));
    }

    @Test
    void rateLimiterRejectsInvalidConfig() {
        assertThrows(IllegalArgumentException.class,
                () -> new DecryptRateLimiter(0, 1_000L, 64));
        assertThrows(IllegalArgumentException.class,
                () -> new DecryptRateLimiter(3, 0L, 64));
        assertThrows(IllegalArgumentException.class,
                () -> new DecryptRateLimiter(3, 1_000L, 0));
    }
}
