package audit;

import config.Settings;
import constants.Strings;
import core.crypto.CryptoDataByte;
import core.fch.RawTxParser;
import data.fcData.Signature;
import data.fchData.Cash;
import managers.NonceManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import utils.FchUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the FC-JDK fixes from the 2026-09-11 audit triage that can run without
 * Elasticsearch or a node.
 */
public class AuditFixes20260911Test {

    // ---- H-15: varint decoding -------------------------------------------------------------

    @Test
    @DisplayName("A 0xFE varint decodes all four bytes, not the low two")
    public void varintFourBytes() throws IOException {
        // 70000 = 0x00011170. Pre-fix the 2-byte conversion kept 0x1170 = 4464.
        byte[] bytes = {(byte) 0xFE, 0x70, 0x11, 0x01, 0x00};
        FchUtils.VariantResult v = FchUtils.parseVarint(new ByteArrayInputStream(bytes));
        assertEquals(70000L, v.number);
        assertArrayEquals(bytes, v.rawBytes);
    }

    @Test
    @DisplayName("A 0xFE varint above 2^31 is read unsigned")
    public void varintFourBytesUnsigned() throws IOException {
        byte[] bytes = {(byte) 0xFE, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        assertEquals(0xFFFFFFFFL, FchUtils.parseVarint(new ByteArrayInputStream(bytes)).number);
    }

    @Test
    @DisplayName("A 0xFF varint decodes eight bytes and returns instead of exiting the JVM")
    public void varintEightBytes() throws IOException {
        // Pre-fix this called System.exit(0), which would also have ended this test run.
        byte[] bytes = {(byte) 0xFF, 0x01, 0, 0, 0, 1, 0, 0, 0};
        assertEquals(0x0000000100000001L, FchUtils.parseVarint(new ByteArrayInputStream(bytes)).number);
    }

    @Test
    @DisplayName("A truncated varint throws instead of decoding zero padding")
    public void varintTruncated() {
        byte[] bytes = {(byte) 0xFD, 0x01};
        assertThrows(IOException.class, () -> FchUtils.parseVarint(new ByteArrayInputStream(bytes)));
    }

    @Test
    @DisplayName("A length larger than the bytes that follow is rejected")
    public void lengthBeyondBuffer() {
        byte[] bytes = {0x05, 0x01, 0x02};
        assertThrows(IOException.class, () -> FchUtils.parseLength(new ByteArrayInputStream(bytes)));
    }

    // ---- M-05 / M-06 / M-09: raw transaction parsing -----------------------------------------

    @Test
    @DisplayName("An empty output script parses as Unknown, and an OP_RETURN output stays invalid")
    public void rawTxEmptyScriptAndOpReturn() throws Exception {
        ByteArrayOutputStream tx = new ByteArrayOutputStream();
        tx.write(new byte[]{1, 0, 0, 0});                 // version
        tx.write(1);                                      // one input
        tx.write(new byte[36]);                           // previous outpoint
        tx.write(0);                                      // empty unlock script
        tx.write(new byte[]{-1, -1, -1, -1});             // sequence
        tx.write(2);                                      // two outputs
        tx.write(new byte[8]);                            // value
        tx.write(new byte[]{2, 0x6a, 0x00});              // OP_RETURN, empty push
        tx.write(new byte[8]);                            // value
        tx.write(0);                                      // empty output script
        tx.write(new byte[4]);                            // lock time

        // Pre-fix: the empty scripts threw ArrayIndexOutOfBounds on [0].
        Map<String, Object> parsed = RawTxParser.parseRawTxBytes(tx.toByteArray());

        @SuppressWarnings("unchecked")
        List<Cash> outs = (List<Cash>) parsed.get(Strings.newCashMapKey);
        assertEquals(2, outs.size());
        assertEquals("OP_RETURN", outs.get(0).getType());
        // Pre-fix: set false in the OP_RETURN branch, then overwritten with true.
        assertEquals(Boolean.FALSE, outs.get(0).isValid());
        assertEquals("Unknown", outs.get(1).getType());
        assertEquals(Boolean.TRUE, outs.get(1).isValid());
    }

    @Test
    @DisplayName("A raw transaction claiming more outputs than it has bytes is rejected")
    public void rawTxOutputCountBeyondBuffer() {
        byte[] tx = {1, 0, 0, 0, 0, (byte) 0xFD, (byte) 0xFF, (byte) 0xFF};
        assertThrows(IOException.class, () -> RawTxParser.parseRawTxBytes(tx));
    }

    // ---- H-34 / M-04: bundle parsing ---------------------------------------------------------

    @Test
    @DisplayName("A short asymmetric cipher bundle is rejected, not read past its end")
    public void shortCryptoBundle() {
        // Legacy prefix 000000000002 = EccK1AesCbc256, type 2 = AsyTwoWay, then 3 of 33 key bytes.
        byte[] bundle = {0, 0, 0, 0, 0, 2, 2, 1, 2, 3};
        // Pre-fix: ArrayIndexOutOfBoundsException copying the 33-byte public key.
        assertNull(CryptoDataByte.fromBundle(bundle));
    }

    @Test
    @DisplayName("A short signature bundle is rejected, not read past its end")
    public void shortSignatureBundle() {
        // Schnorr algorithm id, then 6 of the 20 hash160 bytes: passes the old 12-byte minimum.
        byte[] bundle = {0, 0, 0, 0, 0, 5, 1, 2, 3, 4, 5, 6};
        assertNull(Signature.fromBundle(bundle));
    }

    @Test
    @DisplayName("A signature length that runs past the bundle is rejected")
    public void signatureLengthBeyondBundle() {
        byte[] bundle = new byte[6 + 20 + 2 + 4];
        bundle[5] = 5;
        bundle[26] = (byte) 0xFF;   // claims 65280 signature bytes
        assertNull(Signature.fromBundle(bundle));
    }

    // ---- H-48: nonce replay ------------------------------------------------------------------

    private static NonceManager nonceManager(long windowMillis) {
        Settings settings = mock(Settings.class);
        Map<String, Object> map = new HashMap<>();
        map.put(Settings.WINDOW_TIME, windowMillis);
        when(settings.getSettingMap()).thenReturn(map);
        return new NonceManager(settings);
    }

    @Test
    @DisplayName("A nonce is accepted once, and its replay is refused")
    public void nonceReplayRefused() {
        NonceManager nonces = nonceManager(300_000L);
        assertTrue(nonces.useNonce(12345), "first use");
        // Pre-fix nothing recorded nonces, so a replay was indistinguishable from a first use.
        assertFalse(nonces.useNonce(12345), "replay");
        assertTrue(nonces.isBadNonce(12345));
        assertTrue(nonces.useNonce(54321), "a different nonce is unaffected");
    }

    @Test
    @DisplayName("A nonce can be used again once its window has passed")
    public void nonceReusableAfterWindow() throws InterruptedException {
        NonceManager nonces = nonceManager(20L);
        assertTrue(nonces.useNonce(7));
        Thread.sleep(40);
        assertTrue(nonces.useNonce(7));
    }

    @Test
    @DisplayName("A missing nonce is never accepted")
    public void nullNonceRefused() {
        assertFalse(nonceManager(300_000L).useNonce(null));
    }
}
