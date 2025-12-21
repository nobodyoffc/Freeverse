package core.crypto;

import core.crypto.Algorithm.ChaCha20;
import core.crypto.Algorithm.Ecc256K1ChaCha20;
import data.fcData.AlgorithmId;
import org.bitcoinj.core.ECKey;
import org.junit.jupiter.api.Test;
import utils.BytesUtils;
import utils.Hex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for ChaCha20 and Ecc256K1ChaCha20 algorithms
 */
public class ChaCha20Test {

    @Test
    public void testChaCha20SymmetricEncryptionDecryption() {
        System.out.println("\n=== Testing ChaCha20 Symmetric Encryption/Decryption ===");

        // Test data
        String plaintext = "Hello, ChaCha20! This is a test message for symmetric encryption.";
        byte[] data = plaintext.getBytes();

        // Generate random 32-byte key and 12-byte nonce
        byte[] key = BytesUtils.getRandomBytes(32);
        byte[] nonce = BytesUtils.getRandomBytes(12);

        System.out.println("Original message: " + plaintext);
        System.out.println("Key (hex): " + Hex.toHex(key));
        System.out.println("Nonce (hex): " + Hex.toHex(nonce));

        // Encrypt
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_ChaCha20_No1_NrC7);
        CryptoDataByte encrypted = encryptor.encryptBySymkey(data, key, nonce);

        assertEquals(0, encrypted.getCode(), "Encryption should succeed");
        assertNotNull(encrypted.getCipher(), "Cipher should not be null");
        assertNotNull(encrypted.getSum(), "Sum should be generated for ChaCha20");

        System.out.println("Encryption successful");
        System.out.println("Cipher (hex): " + Hex.toHex(encrypted.getCipher()));
        System.out.println("Sum (hex): " + Hex.toHex(encrypted.getSum()));

        // Decrypt
        Decryptor decryptor = new Decryptor();
        encrypted.setSymkey(key);
        CryptoDataByte decrypted = decryptor.decrypt(encrypted);

        assertEquals(0, decrypted.getCode(), "Decryption should succeed");
        assertNotNull(decrypted.getData(), "Decrypted data should not be null");

        String decryptedText = new String(decrypted.getData());
        System.out.println("Decrypted message: " + decryptedText);

        assertEquals(plaintext, decryptedText, "Decrypted text should match original");
        System.out.println("✓ ChaCha20 symmetric encryption/decryption test passed");
    }

    @Test
    public void testChaCha20BundleFormat() {
        System.out.println("\n=== Testing ChaCha20 Bundle Format ===");

        String plaintext = "Testing ChaCha20 bundle serialization";
        byte[] data = plaintext.getBytes();
        byte[] key = BytesUtils.getRandomBytes(32);
        byte[] nonce = BytesUtils.getRandomBytes(12);

        // Encrypt and create bundle
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_ChaCha20_No1_NrC7);
        CryptoDataByte encrypted = encryptor.encryptBySymkey(data, key, nonce);

        byte[] bundle = encrypted.toBundle();
        assertNotNull(bundle, "Bundle should not be null");
        System.out.println("Bundle created, size: " + bundle.length + " bytes");

        // Parse bundle back
        CryptoDataByte parsed = CryptoDataByte.fromBundle(bundle);
        assertNotNull(parsed, "Parsed CryptoDataByte should not be null");
        assertEquals(AlgorithmId.FC_ChaCha20_No1_NrC7, parsed.getAlg(), "Algorithm should match");

        // Decrypt from bundle
        parsed.setSymkey(key);
        Decryptor decryptor = new Decryptor();
        CryptoDataByte decrypted = decryptor.decrypt(parsed);

        assertEquals(0, decrypted.getCode(), "Decryption should succeed");
        String decryptedText = new String(decrypted.getData());
        assertEquals(plaintext, decryptedText, "Decrypted text should match original");

        System.out.println("✓ ChaCha20 bundle format test passed");
    }

    @Test
    public void testEccK1ChaCha20AsyOneWay() throws Exception {
        System.out.println("\n=== Testing EccK1ChaCha20 AsyOneWay Encryption ===");

        String plaintext = "Testing EccK1ChaCha20 asymmetric encryption (one-way)";
        byte[] data = plaintext.getBytes();

        // Generate recipient key pair
        ECKey recipientKey = new ECKey();
        byte[] recipientPubKey = recipientKey.getPubKey();
        byte[] recipientPriKey = recipientKey.getPrivKeyBytes();

        System.out.println("Original message: " + plaintext);
        System.out.println("Recipient public key: " + Hex.toHex(recipientPubKey));

        // Encrypt using AsyOneWay (generates ephemeral key pair)
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1ChaCha20_No1_NrC7);
        CryptoDataByte encrypted = encryptor.encryptByAsyOneWay(data, recipientPubKey);

        assertEquals(0, encrypted.getCode(), "Encryption should succeed");
        assertNotNull(encrypted.getCipher(), "Cipher should not be null");
        assertNotNull(encrypted.getPubkeyA(), "Ephemeral public key should be generated");
        assertNotNull(encrypted.getSum(), "Sum should be generated for ChaCha20");

        System.out.println("Encryption successful");
        System.out.println("Ephemeral public key: " + Hex.toHex(encrypted.getPubkeyA()));

        // Decrypt using recipient's private key
        encrypted.setPrikeyB(recipientPriKey);
        Decryptor decryptor = new Decryptor();
        CryptoDataByte decrypted = decryptor.decrypt(encrypted);

        assertEquals(0, decrypted.getCode(), "Decryption should succeed");
        String decryptedText = new String(decrypted.getData());
        System.out.println("Decrypted message: " + decryptedText);

        assertEquals(plaintext, decryptedText, "Decrypted text should match original");
        System.out.println("✓ EccK1ChaCha20 AsyOneWay test passed");
    }

    @Test
    public void testEccK1ChaCha20AsyTwoWay() throws Exception {
        System.out.println("\n=== Testing EccK1ChaCha20 AsyTwoWay Encryption ===");

        String plaintext = "Testing EccK1ChaCha20 asymmetric encryption (two-way)";
        byte[] data = plaintext.getBytes();

        // Generate sender and recipient key pairs
        ECKey senderKey = new ECKey();
        byte[] senderPubKey = senderKey.getPubKey();
        byte[] senderPriKey = senderKey.getPrivKeyBytes();

        ECKey recipientKey = new ECKey();
        byte[] recipientPubKey = recipientKey.getPubKey();
        byte[] recipientPriKey = recipientKey.getPrivKeyBytes();

        System.out.println("Original message: " + plaintext);
        System.out.println("Sender public key: " + Hex.toHex(senderPubKey));
        System.out.println("Recipient public key: " + Hex.toHex(recipientPubKey));

        // Encrypt using AsyTwoWay
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1ChaCha20_No1_NrC7);
        CryptoDataByte encrypted = encryptor.encryptByAsyTwoWay(data, senderPriKey, recipientPubKey);

        assertEquals(0, encrypted.getCode(), "Encryption should succeed");
        assertNotNull(encrypted.getCipher(), "Cipher should not be null");

        System.out.println("Encryption successful");

        // Decrypt using recipient's private key and sender's public key
        encrypted.setPrikeyB(recipientPriKey);
        encrypted.setPubkeyA(senderPubKey);
        Decryptor decryptor = new Decryptor();
        CryptoDataByte decrypted = decryptor.decrypt(encrypted);

        assertEquals(0, decrypted.getCode(), "Decryption should succeed");
        String decryptedText = new String(decrypted.getData());
        System.out.println("Decrypted message: " + decryptedText);

        assertEquals(plaintext, decryptedText, "Decrypted text should match original");
        System.out.println("✓ EccK1ChaCha20 AsyTwoWay test passed");
    }

    @Test
    public void testChaCha20IvAdjustment() {
        System.out.println("\n=== Testing ChaCha20 IV Length Adjustment ===");

        // Test with 16-byte IV (should be truncated to 12 bytes)
        byte[] iv16 = BytesUtils.getRandomBytes(16);
        byte[] adjustedIv = Encryptor.adjustIvLength(iv16, AlgorithmId.FC_ChaCha20_No1_NrC7);

        assertEquals(12, adjustedIv.length, "IV should be truncated to 12 bytes for ChaCha20");
        System.out.println("16-byte IV truncated to 12 bytes successfully");

        // Test with 12-byte IV (should remain unchanged)
        byte[] iv12 = BytesUtils.getRandomBytes(12);
        byte[] adjustedIv12 = Encryptor.adjustIvLength(iv12, AlgorithmId.FC_ChaCha20_No1_NrC7);

        assertEquals(12, adjustedIv12.length, "12-byte IV should remain 12 bytes");
        assertArrayEquals(iv12, adjustedIv12, "12-byte IV should be unchanged");
        System.out.println("12-byte IV kept unchanged successfully");

        System.out.println("✓ ChaCha20 IV adjustment test passed");
    }

    @Test
    public void testChaCha20Performance() {
        System.out.println("\n=== Testing ChaCha20 Performance ===");

        // Test with larger data (1MB)
        int dataSize = 1024 * 1024; // 1MB
        byte[] largeData = BytesUtils.getRandomBytes(dataSize);
        byte[] key = BytesUtils.getRandomBytes(32);
        byte[] nonce = BytesUtils.getRandomBytes(12);

        System.out.println("Testing with " + dataSize + " bytes of data");

        // Measure encryption time
        long startTime = System.nanoTime();
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_ChaCha20_No1_NrC7);
        CryptoDataByte encrypted = encryptor.encryptBySymkey(largeData, key, nonce);
        long encryptTime = System.nanoTime() - startTime;

        assertEquals(0, encrypted.getCode(), "Encryption should succeed");
        System.out.println("Encryption time: " + (encryptTime / 1_000_000.0) + " ms");

        // Measure decryption time
        startTime = System.nanoTime();
        encrypted.setSymkey(key);
        Decryptor decryptor = new Decryptor();
        CryptoDataByte decrypted = decryptor.decrypt(encrypted);
        long decryptTime = System.nanoTime() - startTime;

        assertEquals(0, decrypted.getCode(), "Decryption should succeed");
        System.out.println("Decryption time: " + (decryptTime / 1_000_000.0) + " ms");

        // Verify data integrity
        assertArrayEquals(largeData, decrypted.getData(), "Decrypted data should match original");

        System.out.println("✓ ChaCha20 performance test passed");
    }
}
