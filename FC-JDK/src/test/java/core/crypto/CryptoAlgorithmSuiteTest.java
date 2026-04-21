package core.crypto;

import data.fcData.AlgorithmId;
import org.bitcoinj.core.ECKey;
import org.junit.jupiter.api.Test;
import utils.BytesUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for all crypto algorithm combinations.
 * Covers: roundtrip, wrong-key rejection, IV uniqueness, key material not in output.
 */
public class CryptoAlgorithmSuiteTest {

    private static final byte[] TEST_DATA = "Roundtrip test payload for all algorithms".getBytes(StandardCharsets.UTF_8);

    // --- Symmetric roundtrip tests ---

    @Test
    public void testAesCbc256Roundtrip() {
        assertSymmetricRoundtrip(AlgorithmId.FC_AesCbc256_No1_NrC7);
    }

    @Test
    public void testAesGcm256Roundtrip() {
        assertSymmetricRoundtrip(AlgorithmId.FC_AesGcm256_No1_NrC7);
    }

    @Test
    public void testChaCha20Roundtrip() {
        assertSymmetricRoundtrip(AlgorithmId.FC_ChaCha20_No1_NrC7);
    }

    @Test
    public void testChaCha20Poly1305Roundtrip() {
        assertSymmetricRoundtrip(AlgorithmId.FC_ChaCha20Poly1305_No1_NrC7);
    }

    // --- Asymmetric roundtrip tests ---

    @Test
    public void testEccK1AesCbc256AsyRoundtrip() {
        assertAsymmetricRoundtrip(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
    }

    @Test
    public void testEccK1AesGcm256AsyRoundtrip() {
        assertAsymmetricRoundtrip(AlgorithmId.FC_EccK1AesGcm256_No1_NrC7);
    }

    @Test
    public void testEccK1ChaCha20AsyRoundtrip() {
        assertAsymmetricRoundtrip(AlgorithmId.FC_EccK1ChaCha20_No1_NrC7);
    }

    // FC_EccK1ChaCha20Poly1305 asymmetric encryption requires wiring Ecc256K1ChaCha20Poly1305 — tested separately when implemented.
    // FC_X25519AesGcm256 uses X25519 keys (not secp256k1) — tested in NewAlgorithmTest.

    // --- Password roundtrip tests ---

    @Test
    public void testAesCbc256PasswordRoundtrip() {
        assertPasswordRoundtrip(AlgorithmId.FC_AesCbc256_No1_NrC7);
    }

    @Test
    public void testAesGcm256PasswordRoundtrip() {
        assertPasswordRoundtrip(AlgorithmId.FC_AesGcm256_No1_NrC7);
    }

    @Test
    public void testChaCha20Poly1305PasswordRoundtrip() {
        assertPasswordRoundtrip(AlgorithmId.FC_ChaCha20Poly1305_No1_NrC7);
    }

    // --- Wrong key tests ---

    @Test
    public void testAesGcm256WrongKeyFails() {
        assertWrongKeyFails(AlgorithmId.FC_AesGcm256_No1_NrC7);
    }

    @Test
    public void testChaCha20Poly1305WrongKeyFails() {
        assertWrongKeyFails(AlgorithmId.FC_ChaCha20Poly1305_No1_NrC7);
    }

    @Test
    public void testAesCbc256WrongKeyFails() {
        assertWrongKeyFails(AlgorithmId.FC_AesCbc256_No1_NrC7);
    }

    // --- IV uniqueness test ---

    @Test
    public void testIvUniquenessAcrossEncryptions() {
        byte[] symkey = BytesUtils.getRandomBytes(CryptoConstants.KEY_LENGTH_256);
        Set<String> ivSet = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesGcm256_No1_NrC7);
            CryptoDataByte result = encryptor.encryptBySymkey(TEST_DATA, symkey);
            assertEquals(Integer.valueOf(0), result.getCode());

            String ivHex = java.util.HexFormat.of().formatHex(result.getIv());
            assertTrue(ivSet.add(ivHex),
                    "IV must be unique across encryptions (collision at iteration " + i + ")");
        }
    }

    // --- Key material not in output test ---

    @Test
    public void testKeyMaterialNotInCipherOutput() {
        byte[] symkey = BytesUtils.getRandomBytes(CryptoConstants.KEY_LENGTH_256);

        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesGcm256_No1_NrC7);
        CryptoDataByte result = encryptor.encryptBySymkey(TEST_DATA, symkey);
        assertEquals(Integer.valueOf(0), result.getCode());

        byte[] cipher = result.getCipher();
        assertNotNull(cipher);

        // Check that the key bytes don't appear as a substring in the ciphertext
        assertFalse(containsSubarray(cipher, symkey),
                "Symmetric key must not appear in cipher output");
    }

    // --- Bundle roundtrip test ---

    @Test
    public void testBundleRoundtripAllAlgorithms() {
        AlgorithmId[] symAlgs = {
            AlgorithmId.FC_AesCbc256_No1_NrC7,
            AlgorithmId.FC_AesGcm256_No1_NrC7,
            AlgorithmId.FC_ChaCha20_No1_NrC7,
            AlgorithmId.FC_ChaCha20Poly1305_No1_NrC7,
        };

        for (AlgorithmId alg : symAlgs) {
            byte[] symkey = BytesUtils.getRandomBytes(CryptoConstants.KEY_LENGTH_256);
            Encryptor enc = new Encryptor(alg);
            CryptoDataByte encrypted = enc.encryptBySymkey(TEST_DATA, symkey);
            assertEquals(Integer.valueOf(0), encrypted.getCode(), "Encrypt should succeed for " + alg);

            byte[] bundle = encrypted.toBundle();
            assertNotNull(bundle, "Bundle should not be null for " + alg);

            CryptoDataByte parsed = CryptoDataByte.fromBundle(bundle);
            assertNotNull(parsed, "FromBundle should succeed for " + alg);
            assertEquals(alg, parsed.getAlg(), "Algorithm should match for " + alg);

            parsed.setSymkey(symkey);
            Decryptor dec = new Decryptor();
            dec.decrypt(parsed);
            assertEquals(Integer.valueOf(0), parsed.getCode(), "Decrypt bundle should succeed for " + alg);
            assertArrayEquals(TEST_DATA, parsed.getData(), "Decrypted data should match for " + alg);
        }
    }

    // --- Helper methods ---

    private void assertSymmetricRoundtrip(AlgorithmId alg) {
        byte[] symkey = BytesUtils.getRandomBytes(CryptoConstants.KEY_LENGTH_256);
        Encryptor encryptor = new Encryptor(alg);
        CryptoDataByte encrypted = encryptor.encryptBySymkey(TEST_DATA, symkey);

        assertNotNull(encrypted);
        assertEquals(Integer.valueOf(0), encrypted.getCode(), "Encrypt failed for " + alg);
        assertNotNull(encrypted.getCipher());

        Decryptor decryptor = new Decryptor();
        CryptoDataByte decrypted = decryptor.decrypt(encrypted);

        assertEquals(Integer.valueOf(0), decrypted.getCode(), "Decrypt failed for " + alg);
        assertArrayEquals(TEST_DATA, decrypted.getData(), "Roundtrip failed for " + alg);
    }

    private void assertAsymmetricRoundtrip(AlgorithmId alg) {
        ECKey keyB = new ECKey();
        byte[] pubkeyB = keyB.getPubKey();
        byte[] prikeyB = keyB.getPrivKeyBytes();

        Encryptor encryptor = new Encryptor(alg);
        CryptoDataByte encrypted = encryptor.encryptByAsyOneWay(TEST_DATA, pubkeyB);

        assertNotNull(encrypted);
        assertEquals(Integer.valueOf(0), encrypted.getCode(), "Encrypt failed for " + alg);

        encrypted.setPrikeyB(prikeyB);
        Decryptor decryptor = new Decryptor();
        decryptor.decrypt(encrypted);

        assertEquals(Integer.valueOf(0), encrypted.getCode(), "Decrypt failed for " + alg);
        assertArrayEquals(TEST_DATA, encrypted.getData(), "Roundtrip failed for " + alg);
    }

    private void assertPasswordRoundtrip(AlgorithmId alg) {
        char[] password = "TestPassword!@#$%^&*()".toCharArray();

        Encryptor encryptor = new Encryptor(alg);
        CryptoDataByte encrypted = encryptor.encryptByPassword(TEST_DATA, password);

        assertNotNull(encrypted);
        assertEquals(Integer.valueOf(0), encrypted.getCode(), "Encrypt failed for " + alg);

        String json = encrypted.toJson();
        assertNotNull(json);

        CryptoDataByte decrypted = new Decryptor().decryptJsonByPassword(json, password);

        assertEquals(Integer.valueOf(0), decrypted.getCode(), "Decrypt failed for " + alg);
        assertArrayEquals(TEST_DATA, decrypted.getData(), "Password roundtrip failed for " + alg);
    }

    private void assertWrongKeyFails(AlgorithmId alg) {
        byte[] correctKey = BytesUtils.getRandomBytes(CryptoConstants.KEY_LENGTH_256);
        byte[] wrongKey = BytesUtils.getRandomBytes(CryptoConstants.KEY_LENGTH_256);

        Encryptor encryptor = new Encryptor(alg);
        CryptoDataByte encrypted = encryptor.encryptBySymkey(TEST_DATA, correctKey);
        assertEquals(Integer.valueOf(0), encrypted.getCode());

        // Try decrypting with wrong key
        encrypted.setSymkey(wrongKey);
        Decryptor decryptor = new Decryptor();
        CryptoDataByte result = decryptor.decrypt(encrypted);

        // Should either return error code or produce wrong data (not silently succeed with correct plaintext)
        boolean failed = (result.getCode() != null && result.getCode() != 0) ||
                         result.getData() == null ||
                         !java.util.Arrays.equals(TEST_DATA, result.getData());
        assertTrue(failed, "Decryption with wrong key should fail or produce wrong data for " + alg);
    }

    private static boolean containsSubarray(byte[] haystack, byte[] needle) {
        if (needle.length > haystack.length) return false;
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
