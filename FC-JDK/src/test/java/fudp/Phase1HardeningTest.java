package fudp;

import core.crypto.CryptoDataByte;
import fudp.crypto.CryptoManager;
import fudp.security.ReplayProtection;
import fudp.stream.FlowControlViolationException;
import fudp.stream.Stream;
import org.bitcoinj.core.ECKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests covering the Phase-1 hardening fixes:
 *  - N3: per-stream maxRecvData enforcement
 *  - N4: ReplayProtection.windows global LRU cap
 *  - N7: AEAD tag-failure counters
 *
 * N5 (bounded ChallengeHandler pool) and N13 (atomic putIfAbsent in
 * handleChallenge) are exercised by the existing
 * {@code ChallengeHandlerTest.testDuplicateChallengeIgnored}.
 */
class Phase1HardeningTest {

    // ---------- N3: per-stream maxRecvData enforcement ----------

    @Test
    void streamThrowsWhenBufferedDataExceedsMaxRecvData() {
        Stream stream = new Stream(0L);
        // Default maxRecvData = 100_000_000 (~95.4 MiB). Force many
        // out-of-order chunks that never close the gap (offset 0 is missing)
        // so they accumulate in recvBuffer.
        byte[] chunk = new byte[1024 * 1024]; // 1 MiB
        // 90 × 1 MiB = ~94.4 MiB, comfortably under the 100 MB cap.
        for (int i = 0; i < 90; i++) {
            long off = (long) (i + 1) * chunk.length; // skip offset 0
            stream.onDataReceived(off, chunk, false);
        }

        // An 11 MiB chunk would push us over (~94.4 + 11 > 100 MB).
        byte[] big = new byte[11 * 1024 * 1024];
        long bigOffset = 91L * chunk.length;
        FlowControlViolationException ex = assertThrows(
                FlowControlViolationException.class,
                () -> stream.onDataReceived(bigOffset, big, false));
        assertTrue(ex.getMessage().contains("maxRecvData"),
                "exception should reference maxRecvData: " + ex.getMessage());
    }

    @Test
    void streamAcceptsLargeInOrderTransferWithinLimits() {
        // In-order data drains immediately into receivedData and does NOT
        // count against the buffered cap. A 200 MB in-order transfer must
        // succeed with the default 100 MB maxRecvData.
        Stream stream = new Stream(0L);
        byte[] chunk = new byte[1024 * 1024];
        long offset = 0;
        for (int i = 0; i < 200; i++) {
            stream.onDataReceived(offset, chunk, false);
            offset += chunk.length;
        }
        assertEquals(200L * 1024 * 1024, stream.getRecvOffset());
    }

    // ---------- N4: ReplayProtection LRU cap ----------

    @Test
    void replayProtectionEvictsLruWindowsAtCap() {
        int cap = 8;
        ReplayProtection rp = new ReplayProtection(cap);
        long now = System.currentTimeMillis();

        // Fill the cap exactly. Each connection uses an arbitrary epoch and pn=1.
        for (long connId = 1; connId <= cap; connId++) {
            assertEquals(ReplayProtection.CheckResult.OK,
                    rp.checkAndRecord(connId, 1L, now, /*epoch*/ connId));
        }
        assertEquals(cap, rp.getWindowCount());
        assertEquals(0L, rp.getEvictionCount());

        // One more connection -> must evict the eldest (connId=1).
        assertEquals(ReplayProtection.CheckResult.OK,
                rp.checkAndRecord(cap + 1L, 1L, now, /*epoch*/ cap + 1L));
        assertEquals(cap, rp.getWindowCount(), "size stays at cap after eviction");
        assertEquals(1L, rp.getEvictionCount());

        // The evicted connection's window is gone — pn=1 is treated as a
        // fresh first packet, NOT a duplicate. This is the expected (and
        // documented) behaviour: an LRU-evicted peer effectively gets a
        // fresh replay window. The hard cap is the safety net, not a
        // correctness guarantee for evicted peers.
        assertEquals(ReplayProtection.CheckResult.OK,
                rp.checkAndRecord(1L, 1L, now, /*epoch*/ 1L));
    }

    @Test
    void replayProtectionRejectsZeroOrNegativeCap() {
        assertThrows(IllegalArgumentException.class, () -> new ReplayProtection(0));
        assertThrows(IllegalArgumentException.class, () -> new ReplayProtection(-1));
    }

    @Test
    void replayProtectionPromotesOnAccess() {
        // Verify access-order LRU: a "touched" window is not evicted first.
        int cap = 4;
        ReplayProtection rp = new ReplayProtection(cap);
        long now = System.currentTimeMillis();

        for (long connId = 1; connId <= cap; connId++) {
            rp.checkAndRecord(connId, 1L, now, /*epoch*/ connId);
        }
        // Touch connId=1 so it becomes most-recently-used.
        rp.checkAndRecord(1L, 2L, now, /*epoch*/ 1L);

        // Insert a new connection; it should evict connId=2 (the new LRU),
        // not connId=1.
        rp.checkAndRecord(99L, 1L, now, /*epoch*/ 99L);
        assertEquals(cap, rp.getWindowCount());

        // connId=1 still active -> recording its pn=3 should NOT be a fresh first packet
        // (pn=2 is already recorded). pn=3 is a new packet -> OK.
        assertEquals(ReplayProtection.CheckResult.OK,
                rp.checkAndRecord(1L, 3L, now, /*epoch*/ 1L));
        // pn=2 again is a duplicate.
        assertEquals(ReplayProtection.CheckResult.DUPLICATE,
                rp.checkAndRecord(1L, 2L, now, /*epoch*/ 1L));
    }

    // ---------- N7: AEAD tag-failure counter ----------

    @Test
    void cryptoManagerCountsAeadTagFailures() {
        byte[] aliceKey = new ECKey().getPrivKeyBytes();
        byte[] bobKey = new ECKey().getPrivKeyBytes();
        CryptoManager alice = new CryptoManager(aliceKey);
        CryptoManager bob = new CryptoManager(bobKey);

        long before = bob.getAeadTagFailCount();

        // Alice encrypts to Bob. Bob can decrypt fine.
        byte[] plaintext = "hello".getBytes();
        CryptoDataByte good = alice.encryptAsyTwoWay(plaintext, bob.getLocalPublicKey());
        byte[] decrypted = bob.decryptAsyTwoWay(good);
        assertArrayEquals(plaintext, decrypted);
        assertEquals(before, bob.getAeadTagFailCount(), "no tag failure on a good packet");

        // Now tamper with the ciphertext — flip a bit. Decrypt must fail with
        // a tag-mismatch RuntimeException AND the counter must increment.
        CryptoDataByte tampered = alice.encryptAsyTwoWay(plaintext, bob.getLocalPublicKey());
        byte[] ct = tampered.getCipher();
        ct[0] ^= 0x01;
        tampered.setCipher(ct);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> bob.decryptAsyTwoWay(tampered));
        assertTrue(ex.getMessage().toLowerCase().contains("tag")
                        || ex.getMessage().toLowerCase().contains("decryption failed"),
                "exception message should mention tag/decryption failure: " + ex.getMessage());
        assertEquals(before + 1, bob.getAeadTagFailCount(),
                "tag-fail counter must increment on tampered packet");
    }
}
