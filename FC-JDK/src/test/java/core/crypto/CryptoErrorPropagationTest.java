package core.crypto;

import constants.CodeMessage;
import data.fcData.AlgorithmId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for three defects found in the 2026-09 crypto audit:
 *
 * 1. Encryptor reported Code0Success over a failure raised by the layer below,
 *    handing the caller a "successful" CryptoDataByte with an empty cipher.
 * 2. FC_EccK1ChaCha20Poly1305_No1_NrC7 had no case in Decryptor.decryptStreamByAsy,
 *    so it fell through to the AES-CBC default and could not decrypt its own output.
 * 3. checkKeysMakeType never set the type on the AsyTwoWay branch, so
 *    EccAes256K1P7 threw NPE while encrypting.
 *
 * Note: AES-CBC tamper detection is deliberately NOT asserted here. The CBC
 * sum is still unverified on the asymmetric path; that is tracked separately.
 */
public class CryptoErrorPropagationTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static byte[] prikey() {
        byte[] pri = new byte[32];
        RANDOM.nextBytes(pri);
        return pri;
    }

    private static CryptoDataByte received(CryptoDataByte sent, AlgorithmId alg,
                                           byte[] prikeyB, byte[] pubkeyA, byte[] cipher) {
        CryptoDataByte in = new CryptoDataByte();
        in.setAlg(alg);
        in.setType(EncryptType.AsyTwoWay);
        in.setCipher(cipher);
        in.setIv(sent.getIv());
        in.setSum(sent.getSum());
        in.setPrikeyB(prikeyB);
        in.setPubkeyA(pubkeyA);
        return in;
    }

    @Test
    @DisplayName("A cipher-layer failure is not overwritten with Code0Success")
    public void encryptPreservesFailureCode() {
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);

        // 7-byte key: AesCbc256 sets Code4008WrongKeyLength and writes nothing.
        CryptoDataByte result = encryptor.encryptBySymkey("payload".getBytes(), new byte[7]);

        assertNotNull(result.getCode(), "a failed encryption must carry a code");
        assertNotEquals(0, result.getCode(),
                "wrong key length must not be reported as success");
        assertEquals(CodeMessage.Code4008WrongKeyLength, result.getCode());
    }

    @Test
    @DisplayName("A successful encryption still reports Code0Success and a cipher")
    public void encryptStillReportsSuccess() {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);

        CryptoDataByte result = encryptor.encryptBySymkey("payload".getBytes(), key);

        assertEquals(0, result.getCode());
        assertNotNull(result.getCipher());
        assertTrue(result.getCipher().length > 0);
    }

    @Test
    @DisplayName("EccAes256K1P7 encrypts AsyTwoWay without NPE and round-trips")
    public void legacyEccAesAsyTwoWayRoundTrip() {
        byte[] priA = prikey(), pubA = KeyTools.prikeyToPubkey(priA);
        byte[] priB = prikey(), pubB = KeyTools.prikeyToPubkey(priB);
        byte[] data = "legacy algorithm payload".getBytes();

        byte[] priACopy = priA.clone();

        Encryptor encryptor = new Encryptor(AlgorithmId.EccAes256K1P7_No1_NrC7);
        CryptoDataByte sent = assertDoesNotThrow(
                () -> encryptor.encryptByAsyTwoWay(data, priA, pubB),
                "AsyTwoWay encryption must not throw");

        assertEquals(0, sent.getCode(), sent.getMessage());
        assertEquals(EncryptType.AsyTwoWay, sent.getType());
        assertNotNull(sent.getCipher());
        assertTrue(sent.getCipher().length > 0, "cipher must not be empty");
        assertNotNull(sent.getSum(), "EccAes256K1P7 carries its own cipher-derived sum");

        // The legacy implementation wipes private keys in place when it finishes;
        // it must wipe its own copy, not the array the caller still holds.
        assertArrayEquals(priACopy, priA, "caller's private key must survive encryption");

        // Full round trip through the public decrypt entry point.
        CryptoDataByte back = received(sent, AlgorithmId.EccAes256K1P7_No1_NrC7,
                priB, pubA, sent.getCipher().clone());
        new Decryptor().decrypt(back);
        assertEquals(0, back.getCode(), back.getMessage());
        assertArrayEquals(data, back.getData());
    }

    @Test
    @DisplayName("ChaCha20-Poly1305 asymmetric round-trips and rejects tampering")
    public void eccChaCha20Poly1305RoundTripAndTamper() {
        byte[] priA = prikey(), pubA = KeyTools.prikeyToPubkey(priA);
        byte[] priB = prikey(), pubB = KeyTools.prikeyToPubkey(priB);
        byte[] data = "ChaCha20-Poly1305 asymmetric payload".getBytes();
        AlgorithmId alg = AlgorithmId.FC_EccK1ChaCha20Poly1305_No1_NrC7;

        CryptoDataByte sent = new Encryptor(alg).encryptByAsyTwoWay(data, priA, pubB);
        assertEquals(0, sent.getCode(), sent.getMessage());
        assertNotNull(sent.getCipher());

        // Clean round trip
        CryptoDataByte ok = received(sent, alg, priB, pubA, sent.getCipher().clone());
        new Decryptor().decrypt(ok);
        assertEquals(0, ok.getCode(), ok.getMessage());
        assertArrayEquals(data, ok.getData());

        // Flipped ciphertext byte must fail the Poly1305 tag
        byte[] tampered = sent.getCipher().clone();
        tampered[tampered.length / 2] ^= 0x01;
        CryptoDataByte bad = received(sent, alg, priB, pubA, tampered);
        new Decryptor().decrypt(bad);
        assertNotEquals(0, bad.getCode(), "tampered AEAD ciphertext must not decrypt as success");
        assertNull(bad.getData());

        // A third party's key must not decrypt it
        CryptoDataByte wrongKey = received(sent, alg, prikey(), pubA, sent.getCipher().clone());
        new Decryptor().decrypt(wrongKey);
        assertNotEquals(0, wrongKey.getCode(), "wrong private key must not decrypt as success");
    }

    @Test
    @DisplayName("AEAD algorithms carry no sum; non-AEAD algorithms do")
    public void aeadClassification() {
        assertTrue(AlgorithmId.FC_AesGcm256_No1_NrC7.isAead());
        assertTrue(AlgorithmId.FC_EccK1AesGcm256_No1_NrC7.isAead());
        assertTrue(AlgorithmId.FC_X25519AesGcm256_No1_NrC7.isAead());
        assertTrue(AlgorithmId.FC_ChaCha20Poly1305_No1_NrC7.isAead());
        assertTrue(AlgorithmId.FC_EccK1ChaCha20Poly1305_No1_NrC7.isAead());

        assertFalse(AlgorithmId.FC_AesCbc256_No1_NrC7.isAead());
        assertFalse(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7.isAead());
        assertFalse(AlgorithmId.FC_ChaCha20_No1_NrC7.isAead());
        assertFalse(AlgorithmId.FC_EccK1ChaCha20_No1_NrC7.isAead());
        assertFalse(AlgorithmId.EccAes256K1P7_No1_NrC7.isAead());

        // An AEAD encryption must not emit a sum, a non-AEAD one must.
        byte[] priA = prikey(), priB = prikey();
        byte[] pubB = KeyTools.prikeyToPubkey(priB);
        byte[] data = "sum presence".getBytes();

        CryptoDataByte aead = new Encryptor(AlgorithmId.FC_EccK1ChaCha20Poly1305_No1_NrC7)
                .encryptByAsyTwoWay(data, priA, pubB);
        assertNull(aead.getSum(), "AEAD output must not carry a redundant sum");

        CryptoDataByte nonAead = new Encryptor(AlgorithmId.FC_EccK1ChaCha20_No1_NrC7)
                .encryptByAsyTwoWay(data, priA, pubB);
        assertNotNull(nonAead.getSum(), "non-AEAD output must carry a sum");
    }
}
