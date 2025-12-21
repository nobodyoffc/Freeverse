package core.crypto;

import data.fcData.AlgorithmId;
import org.bitcoinj.core.ECKey;
import org.junit.jupiter.api.Test;
import utils.Hex;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Diagnostic test for AES-GCM asymmetric encryption
 */
public class AsyGcmDebugTest {

    @Test
    public void testAsyGcmEncryptDecrypt() {
        // Generate valid secp256k1 keys
        ECKey keyA = new ECKey();
        ECKey keyB = new ECKey();

        byte[] prikeyA = keyA.getPrivKeyBytes();
        byte[] pubkeyA = keyA.getPubKey();
        byte[] prikeyB = keyB.getPrivKeyBytes();
        byte[] pubkeyB = keyB.getPubKey();

        byte[] plaintext = "Hello, World!".getBytes();

        System.out.println("=== Input ===");
        System.out.println("Plaintext length: " + plaintext.length);
        System.out.println("PrikeyA length: " + prikeyA.length);
        System.out.println("PubkeyA length: " + pubkeyA.length);
        System.out.println("PrikeyB length: " + prikeyB.length);
        System.out.println("PubkeyB length: " + pubkeyB.length);

        // Encrypt
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesGcm256_No1_NrC7);
        CryptoDataByte encrypted = encryptor.encryptByAsyTwoWay(plaintext, prikeyA, pubkeyB);

        System.out.println("\n=== After Encryption ===");
        System.out.println("Code: " + encrypted.getCode());
        System.out.println("Message: " + encrypted.getMessage());
        System.out.println("Algorithm: " + encrypted.getAlg());
        System.out.println("Type: " + encrypted.getType());
        System.out.println("Cipher length: " + (encrypted.getCipher() != null ? encrypted.getCipher().length : "null"));
        System.out.println("IV length: " + (encrypted.getIv() != null ? encrypted.getIv().length : "null"));
        System.out.println("IV: " + (encrypted.getIv() != null ? Hex.toHex(encrypted.getIv()) : "null"));
        System.out.println("PubkeyA length: " + (encrypted.getPubkeyA() != null ? encrypted.getPubkeyA().length : "null"));
        System.out.println("PubkeyB length: " + (encrypted.getPubkeyB() != null ? encrypted.getPubkeyB().length : "null"));
        System.out.println("Sum: " + (encrypted.getSum() != null ? Hex.toHex(encrypted.getSum()) : "null"));

        assertEquals(Integer.valueOf(0), encrypted.getCode(), "Encryption should succeed: " + encrypted.getMessage());
        assertNotNull(encrypted.getCipher(), "Cipher should not be null");
        assertTrue(encrypted.getCipher().length > 0, "Cipher should not be empty");

        // Set up for decryption - B decrypts with B's private key and A's public key
        encrypted.setPrikeyB(prikeyB);
        // pubkeyA should already be set during encryption

        System.out.println("\n=== Before Decryption ===");
        System.out.println("PrikeyA set: " + (encrypted.getPrikeyA() != null));
        System.out.println("PrikeyB set: " + (encrypted.getPrikeyB() != null));
        System.out.println("PubkeyA set: " + (encrypted.getPubkeyA() != null));
        System.out.println("PubkeyB set: " + (encrypted.getPubkeyB() != null));

        // Decrypt
        Decryptor decryptor = new Decryptor();
        decryptor.decryptByAsyKey(encrypted);

        System.out.println("\n=== After Decryption ===");
        System.out.println("Code: " + encrypted.getCode());
        System.out.println("Message: " + encrypted.getMessage());
        System.out.println("Data length: " + (encrypted.getData() != null ? encrypted.getData().length : "null"));

        assertEquals(Integer.valueOf(0), encrypted.getCode(), "Decryption should succeed: " + encrypted.getMessage());
        byte[] decrypted = encrypted.getData();
        assertNotNull(decrypted, "Decrypted data should not be null");

        System.out.println("Expected: " + new String(plaintext));
        System.out.println("Got: " + new String(decrypted));

        assertArrayEquals(plaintext, decrypted, "Decrypted data should match original");
    }

    @Test
    public void testSymGcmEncryptDecrypt() {
        // Test symmetric GCM encryption for comparison
        byte[] plaintext = "Hello, Symmetric!".getBytes();
        byte[] symkey = new byte[32];
        new java.security.SecureRandom().nextBytes(symkey);

        System.out.println("\n=== Symmetric GCM Test ===");
        System.out.println("Plaintext length: " + plaintext.length);

        // Encrypt
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesGcm256_No1_NrC7);
        CryptoDataByte encrypted = encryptor.encryptBySymkey(plaintext, symkey);

        System.out.println("After encryption - Code: " + encrypted.getCode());
        System.out.println("Cipher length: " + (encrypted.getCipher() != null ? encrypted.getCipher().length : "null"));
        System.out.println("IV length: " + (encrypted.getIv() != null ? encrypted.getIv().length : "null"));

        // Decrypt
        encrypted.setSymkey(symkey);
        Decryptor decryptor = new Decryptor();
        decryptor.decrypt(encrypted);

        System.out.println("After decryption - Code: " + encrypted.getCode());
        System.out.println("Data length: " + (encrypted.getData() != null ? encrypted.getData().length : "null"));

        assertEquals(Integer.valueOf(0), encrypted.getCode(), "Decryption should succeed: " + encrypted.getMessage());
        assertArrayEquals(plaintext, encrypted.getData(), "Data should match");
    }
}
