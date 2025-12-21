package core.crypto;

import core.crypto.Algorithm.X25519;
import data.fcData.AlgorithmId;
import org.bitcoinj.core.ECKey;
import org.junit.Test;
import utils.BytesUtils;
import utils.Hex;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.*;

public class NewAlgorithmTest {

    private static final String TEST_MESSAGE = "Hello, this is a test message for new encryption algorithms!";
    private static final byte[] TEST_DATA = TEST_MESSAGE.getBytes(StandardCharsets.UTF_8);

    @Test
    public void testAesGcm256SymmetricEncryption() {
        System.out.println("\n=== Testing FC_AesGcm256_No1_NrC7 Symmetric Encryption ===");

        // Generate random symmetric key
        byte[] symkey = BytesUtils.getRandomBytes(32);
        System.out.println("Symmetric Key: " + Hex.toHex(symkey));

        // Encrypt
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesGcm256_No1_NrC7);
        CryptoDataByte encryptResult = encryptor.encryptBySymkey(TEST_DATA, symkey);

        assertNotNull("Encryption should succeed", encryptResult);
        assertEquals("Encryption should return success code", Integer.valueOf(0), encryptResult.getCode());
        assertNotNull("Cipher should not be null", encryptResult.getCipher());
        assertEquals("Algorithm should be FC_AesGcm256_No1_NrC7",
                    AlgorithmId.FC_AesGcm256_No1_NrC7, encryptResult.getAlg());

        System.out.println("Encryption successful");
        System.out.println("Cipher (base64): " + java.util.Base64.getEncoder().encodeToString(encryptResult.getCipher()));
        System.out.println("IV: " + Hex.toHex(encryptResult.getIv()));

        // Decrypt
        Decryptor decryptor = new Decryptor();
        CryptoDataByte decryptResult = decryptor.decrypt(encryptResult);

        assertNotNull("Decryption should succeed", decryptResult);
        assertEquals("Decryption should return success code", Integer.valueOf(0), decryptResult.getCode());
        assertNotNull("Decrypted data should not be null", decryptResult.getData());

        String decryptedMessage = new String(decryptResult.getData(), StandardCharsets.UTF_8);
        System.out.println("Decrypted message: " + decryptedMessage);

        assertEquals("Decrypted message should match original", TEST_MESSAGE, decryptedMessage);
        System.out.println("✓ AES-GCM-256 symmetric encryption test PASSED\n");
    }

    @Test
    public void testEccK1AesGcm256AsymmetricEncryption() {
        System.out.println("\n=== Testing FC_EccK1AesGcm256_No1_NrC7 Asymmetric Encryption ===");

        // Generate key pairs
        ECKey keyA = new ECKey();
        ECKey keyB = new ECKey();

        byte[] prikeyA = keyA.getPrivKeyBytes();
        byte[] pubkeyA = keyA.getPubKey();
        byte[] prikeyB = keyB.getPrivKeyBytes();
        byte[] pubkeyB = keyB.getPubKey();

        System.out.println("Key A Public: " + Hex.toHex(pubkeyA));
        System.out.println("Key B Public: " + Hex.toHex(pubkeyB));

        // Test AsyTwoWay encryption (A encrypts for B)
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesGcm256_No1_NrC7);
        CryptoDataByte encryptResult = encryptor.encryptByAsyTwoWay(TEST_DATA, prikeyA, pubkeyB);

        assertNotNull("Encryption should succeed", encryptResult);
        assertEquals("Encryption should return success code", Integer.valueOf(0), encryptResult.getCode());
        assertNotNull("Cipher should not be null", encryptResult.getCipher());
        assertEquals("Algorithm should be FC_EccK1AesGcm256_No1_NrC7",
                    AlgorithmId.FC_EccK1AesGcm256_No1_NrC7, encryptResult.getAlg());

        System.out.println("Encryption successful");
        System.out.println("Public Key A: " + Hex.toHex(encryptResult.getPubkeyA()));

        // Decrypt with B's private key and A's public key
        encryptResult.setPrikeyB(prikeyB);
        encryptResult.setPubkeyA(pubkeyA);

        Decryptor decryptor = new Decryptor();
        decryptor.decryptByAsyKey(encryptResult);

        assertEquals("Decryption should return success code", Integer.valueOf(0), encryptResult.getCode());
        assertNotNull("Decrypted data should not be null", encryptResult.getData());

        String decryptedMessage = new String(encryptResult.getData(), StandardCharsets.UTF_8);
        System.out.println("Decrypted message: " + decryptedMessage);

        assertEquals("Decrypted message should match original", TEST_MESSAGE, decryptedMessage);
        System.out.println("✓ ECC K1 + AES-GCM-256 asymmetric encryption test PASSED\n");
    }

    @Test
    public void testX25519AesGcm256AsymmetricEncryption() {
        System.out.println("\n=== Testing FC_X25519AesGcm256_No1_NrC7 with X25519 Key Exchange ===");

        // Generate X25519 key pairs
        byte[] prikeyA = BytesUtils.getRandomBytes(32);
        byte[] pubkeyA = X25519.generatePublicKey(prikeyA);

        byte[] prikeyB = BytesUtils.getRandomBytes(32);
        byte[] pubkeyB = X25519.generatePublicKey(prikeyB);

        System.out.println("X25519 Key A Private: " + Hex.toHex(prikeyA));
        System.out.println("X25519 Key A Public: " + Hex.toHex(pubkeyA));
        System.out.println("X25519 Key B Private: " + Hex.toHex(prikeyB));
        System.out.println("X25519 Key B Public: " + Hex.toHex(pubkeyB));

        // Verify shared secret generation
        byte[] sharedSecretAB = X25519.getSharedSecret(prikeyA, pubkeyB);
        byte[] sharedSecretBA = X25519.getSharedSecret(prikeyB, pubkeyA);
        System.out.println("Shared Secret A->B: " + Hex.toHex(sharedSecretAB));
        System.out.println("Shared Secret B->A: " + Hex.toHex(sharedSecretBA));

        assertTrue("Shared secrets should match", Arrays.equals(sharedSecretAB, sharedSecretBA));
        System.out.println("✓ X25519 key exchange verified");

        // Test AsyTwoWay encryption with X25519
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_X25519AesGcm256_No1_NrC7);
        CryptoDataByte encryptResult = encryptor.encryptByAsyTwoWay(TEST_DATA, prikeyA, pubkeyB);

        assertNotNull("Encryption should succeed", encryptResult);
        assertEquals("Encryption should return success code", Integer.valueOf(0), encryptResult.getCode());
        assertNotNull("Cipher should not be null", encryptResult.getCipher());
        assertEquals("Algorithm should be FC_X25519AesGcm256_No1_NrC7",
                    AlgorithmId.FC_X25519AesGcm256_No1_NrC7, encryptResult.getAlg());

        System.out.println("Encryption successful with X25519");
        System.out.println("Cipher length: " + encryptResult.getCipher().length);

        // Decrypt with B's private key and A's public key
        encryptResult.setPrikeyB(prikeyB);
        encryptResult.setPubkeyA(pubkeyA);

        Decryptor decryptor = new Decryptor();
        decryptor.decryptByAsyKey(encryptResult);

        assertEquals("Decryption should return success code", Integer.valueOf(0), encryptResult.getCode());
        assertNotNull("Decrypted data should not be null", encryptResult.getData());

        String decryptedMessage = new String(encryptResult.getData(), StandardCharsets.UTF_8);
        System.out.println("Decrypted message: " + decryptedMessage);

        assertEquals("Decrypted message should match original", TEST_MESSAGE, decryptedMessage);
        System.out.println("✓ X25519 + AES-GCM-256 asymmetric encryption test PASSED\n");
    }

    @Test
    public void testBundleSerializationAesGcm() {
        System.out.println("\n=== Testing Bundle Serialization for AES-GCM ===");

        byte[] symkey = BytesUtils.getRandomBytes(32);

        // Encrypt
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesGcm256_No1_NrC7);
        CryptoDataByte encryptResult = encryptor.encryptBySymkey(TEST_DATA, symkey);

        assertEquals("Encryption should succeed", Integer.valueOf(0), encryptResult.getCode());

        // Convert to bundle
        byte[] bundle = encryptResult.toBundle();
        assertNotNull("Bundle should not be null", bundle);
        System.out.println("Bundle created, length: " + bundle.length + " bytes");
        System.out.println("Bundle (hex): " + Hex.toHex(bundle));

        // Parse bundle back
        CryptoDataByte parsedData = CryptoDataByte.fromBundle(bundle);
        assertNotNull("Parsed data should not be null", parsedData);
        assertEquals("Algorithm should match", AlgorithmId.FC_AesGcm256_No1_NrC7, parsedData.getAlg());
        assertEquals("Encrypt type should match", EncryptType.Symkey, parsedData.getType());
        assertArrayEquals("IV should match", encryptResult.getIv(), parsedData.getIv());
        assertArrayEquals("Cipher should match", encryptResult.getCipher(), parsedData.getCipher());
        assertArrayEquals("Sum should match", encryptResult.getSum(), parsedData.getSum());

        System.out.println("Bundle parsed successfully");

        // Decrypt from bundle
        parsedData.setSymkey(symkey);
        Decryptor decryptor = new Decryptor();
        CryptoDataByte decryptResult = decryptor.decrypt(parsedData);

        assertEquals("Decryption should succeed", Integer.valueOf(0), decryptResult.getCode());
        String decryptedMessage = new String(decryptResult.getData(), StandardCharsets.UTF_8);
        assertEquals("Decrypted message should match original", TEST_MESSAGE, decryptedMessage);

        System.out.println("✓ Bundle serialization test PASSED\n");
    }

    @Test
    public void testBundleSerializationX25519() {
        System.out.println("\n=== Testing Bundle Serialization for X25519 + AES-GCM ===");

        byte[] prikeyA = BytesUtils.getRandomBytes(32);
        byte[] pubkeyA = X25519.generatePublicKey(prikeyA);
        byte[] prikeyB = BytesUtils.getRandomBytes(32);
        byte[] pubkeyB = X25519.generatePublicKey(prikeyB);

        // Encrypt
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_X25519AesGcm256_No1_NrC7);
        CryptoDataByte encryptResult = encryptor.encryptByAsyTwoWay(TEST_DATA, prikeyA, pubkeyB);

        assertEquals("Encryption should succeed", Integer.valueOf(0), encryptResult.getCode());

        // Convert to bundle
        byte[] bundle = encryptResult.toBundle();
        assertNotNull("Bundle should not be null", bundle);
        System.out.println("Bundle created, length: " + bundle.length + " bytes");

        // Parse bundle back
        CryptoDataByte parsedData = CryptoDataByte.fromBundle(bundle);
        assertNotNull("Parsed data should not be null", parsedData);
        assertEquals("Algorithm should match", AlgorithmId.FC_X25519AesGcm256_No1_NrC7, parsedData.getAlg());
        assertEquals("Encrypt type should match", EncryptType.AsyTwoWay, parsedData.getType());
        assertArrayEquals("Public key A should match", encryptResult.getPubkeyA(), parsedData.getPubkeyA());

        System.out.println("Bundle parsed successfully");

        // Decrypt from bundle
        parsedData.setPrikeyB(prikeyB);
        parsedData.setPubkeyA(pubkeyA);

        Decryptor decryptor = new Decryptor();
        decryptor.decryptByAsyKey(parsedData);

        assertEquals("Decryption should succeed", Integer.valueOf(0), parsedData.getCode());
        String decryptedMessage = new String(parsedData.getData(), StandardCharsets.UTF_8);
        assertEquals("Decrypted message should match original", TEST_MESSAGE, decryptedMessage);

        System.out.println("✓ X25519 bundle serialization test PASSED\n");
    }

    @Test
    public void testJsonSerializationAesGcm() {
        System.out.println("\n=== Testing JSON Serialization for AES-GCM ===");

        byte[] symkey = BytesUtils.getRandomBytes(32);

        // Encrypt
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesGcm256_No1_NrC7);
        CryptoDataByte encryptResult = encryptor.encryptBySymkey(TEST_DATA, symkey);

        assertEquals("Encryption should succeed", Integer.valueOf(0), encryptResult.getCode());

        // Convert to JSON
        String json = encryptResult.toJson();
        assertNotNull("JSON should not be null", json);
        System.out.println("JSON created:");
        System.out.println(encryptResult.toNiceJson());

        // Parse JSON back
        CryptoDataByte parsedData = CryptoDataByte.fromJson(json);
        assertNotNull("Parsed data should not be null", parsedData);
        assertEquals("Algorithm should match", AlgorithmId.FC_AesGcm256_No1_NrC7, parsedData.getAlg());
        assertEquals("Encrypt type should match", EncryptType.Symkey, parsedData.getType());

        System.out.println("JSON parsed successfully");

        // Decrypt from JSON
        parsedData.setSymkey(symkey);
        Decryptor decryptor = new Decryptor();
        CryptoDataByte decryptResult = decryptor.decrypt(parsedData);

        assertEquals("Decryption should succeed", Integer.valueOf(0), decryptResult.getCode());
        String decryptedMessage = new String(decryptResult.getData(), StandardCharsets.UTF_8);
        assertEquals("Decrypted message should match original", TEST_MESSAGE, decryptedMessage);

        System.out.println("✓ JSON serialization test PASSED\n");
    }

    @Test
    public void testPasswordBasedEncryptionWithAesGcm() {
        System.out.println("\n=== Testing Password-Based Encryption with AES-GCM ===");

        char[] password = "MySecretPassword123!".toCharArray();

        // Encrypt with password
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesGcm256_No1_NrC7);
        CryptoDataByte encryptResult = encryptor.encryptByPassword(TEST_DATA, password);

        assertEquals("Encryption should succeed", Integer.valueOf(0), encryptResult.getCode());
        assertEquals("Type should be Password", EncryptType.Password, encryptResult.getType());
        System.out.println("Password-based encryption successful");

        // Decrypt with password
        Decryptor decryptor = new Decryptor();
        CryptoDataByte decryptResult = decryptor.decryptJsonByPassword(encryptResult.toJson(), password);

        assertEquals("Decryption should succeed", Integer.valueOf(0), decryptResult.getCode());
        String decryptedMessage = new String(decryptResult.getData(), StandardCharsets.UTF_8);
        assertEquals("Decrypted message should match original", TEST_MESSAGE, decryptedMessage);

        System.out.println("✓ Password-based encryption with AES-GCM test PASSED\n");
    }

    @Test
    public void testAsyOneWayEncryptionWithX25519() {
        System.out.println("\n=== Testing AsyOneWay Encryption with X25519 ===");

        // Generate recipient key pair
        byte[] prikeyB = BytesUtils.getRandomBytes(32);
        byte[] pubkeyB = X25519.generatePublicKey(prikeyB);

        System.out.println("Recipient Public Key: " + Hex.toHex(pubkeyB));

        // Encrypt (sender doesn't need a key pair, one will be generated)
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_X25519AesGcm256_No1_NrC7);
        CryptoDataByte encryptResult = encryptor.encryptByAsyOneWay(TEST_DATA, pubkeyB);

        assertEquals("Encryption should succeed", Integer.valueOf(0), encryptResult.getCode());
        assertEquals("Type should be AsyOneWay", EncryptType.AsyOneWay, encryptResult.getType());
        assertNotNull("Ephemeral public key should be generated", encryptResult.getPubkeyA());

        System.out.println("AsyOneWay encryption successful");
        System.out.println("Ephemeral Public Key: " + Hex.toHex(encryptResult.getPubkeyA()));

        // Decrypt with recipient's private key only
        encryptResult.setPrikeyB(prikeyB);

        Decryptor decryptor = new Decryptor();
        decryptor.decryptByAsyKey(encryptResult);

        assertEquals("Decryption should succeed", Integer.valueOf(0), encryptResult.getCode());
        String decryptedMessage = new String(encryptResult.getData(), StandardCharsets.UTF_8);
        assertEquals("Decrypted message should match original", TEST_MESSAGE, decryptedMessage);

        System.out.println("✓ AsyOneWay encryption with X25519 test PASSED\n");
    }
}
