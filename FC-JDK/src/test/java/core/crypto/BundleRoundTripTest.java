package core.crypto;

import data.fcData.AlgorithmId;
import org.bitcoinj.core.ECKey;
import org.junit.jupiter.api.Test;
import utils.Hex;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test encryption/decryption through bundle serialization
 */
public class BundleRoundTripTest {

    @Test
    public void testAsyGcmBundleRoundTrip() {
        // Generate valid secp256k1 keys
        ECKey keyA = new ECKey();
        ECKey keyB = new ECKey();

        byte[] prikeyA = keyA.getPrivKeyBytes();
        byte[] pubkeyA = keyA.getPubKey();
        byte[] prikeyB = keyB.getPrivKeyBytes();
        byte[] pubkeyB = keyB.getPubKey();

        byte[] plaintext = "Hello, Bundle World!".getBytes();

        System.out.println("=== Input ===");
        System.out.println("Plaintext: " + new String(plaintext));
        System.out.println("Plaintext length: " + plaintext.length);

        // Encrypt
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesGcm256_No1_NrC7);
        CryptoDataByte encrypted = encryptor.encryptByAsyTwoWay(plaintext, prikeyA, pubkeyB);

        assertEquals(Integer.valueOf(0), encrypted.getCode(), "Encryption should succeed: " + encrypted.getMessage());

        System.out.println("\n=== After Encryption ===");
        System.out.println("Algorithm: " + encrypted.getAlg());
        System.out.println("Type: " + encrypted.getType());
        System.out.println("IV length: " + (encrypted.getIv() != null ? encrypted.getIv().length : "null"));
        System.out.println("Cipher length: " + (encrypted.getCipher() != null ? encrypted.getCipher().length : "null"));
        System.out.println("PubkeyA length: " + (encrypted.getPubkeyA() != null ? encrypted.getPubkeyA().length : "null"));

        // Convert to bundle
        byte[] bundle = encrypted.toBundle();
        assertNotNull(bundle, "Bundle should not be null");
        System.out.println("Bundle length: " + bundle.length);

        // Parse bundle back
        CryptoDataByte fromBundle = CryptoDataByte.fromBundle(bundle);
        assertNotNull(fromBundle, "FromBundle should not be null");

        System.out.println("\n=== After FromBundle ===");
        System.out.println("Algorithm: " + fromBundle.getAlg());
        System.out.println("Type: " + fromBundle.getType());
        System.out.println("IV length: " + (fromBundle.getIv() != null ? fromBundle.getIv().length : "null"));
        System.out.println("Cipher length: " + (fromBundle.getCipher() != null ? fromBundle.getCipher().length : "null"));
        System.out.println("PubkeyA length: " + (fromBundle.getPubkeyA() != null ? fromBundle.getPubkeyA().length : "null"));

        // Set up for decryption - B decrypts with B's private key
        fromBundle.setPrikeyB(prikeyB);

        // Decrypt
        Decryptor decryptor = new Decryptor();
        decryptor.decrypt(fromBundle);

        System.out.println("\n=== After Decryption ===");
        System.out.println("Code: " + fromBundle.getCode());
        System.out.println("Message: " + fromBundle.getMessage());
        System.out.println("Data length: " + (fromBundle.getData() != null ? fromBundle.getData().length : "null"));

        assertEquals(Integer.valueOf(0), fromBundle.getCode(), "Decryption should succeed: " + fromBundle.getMessage());
        byte[] decrypted = fromBundle.getData();
        assertNotNull(decrypted, "Decrypted data should not be null");

        System.out.println("Decrypted: " + new String(decrypted));

        assertArrayEquals(plaintext, decrypted, "Decrypted data should match original");
    }

    @Test
    public void testSymGcmBundleRoundTrip() {
        byte[] plaintext = "Hello, Symmetric Bundle!".getBytes();
        byte[] symkey = new byte[32];
        new java.security.SecureRandom().nextBytes(symkey);

        System.out.println("\n=== Symmetric GCM Bundle Test ===");
        System.out.println("Plaintext length: " + plaintext.length);

        // Encrypt
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesGcm256_No1_NrC7);
        CryptoDataByte encrypted = encryptor.encryptBySymkey(plaintext, symkey);

        assertEquals(Integer.valueOf(0), encrypted.getCode(), "Encryption should succeed");

        System.out.println("After encryption - IV length: " + encrypted.getIv().length);
        System.out.println("Cipher length: " + encrypted.getCipher().length);

        // Create key name
        encrypted.makeKeyName(symkey);

        // Convert to bundle
        byte[] bundle = encrypted.toBundle();
        assertNotNull(bundle, "Bundle should not be null");
        System.out.println("Bundle length: " + bundle.length);

        // Parse bundle back
        CryptoDataByte fromBundle = CryptoDataByte.fromBundle(bundle);
        assertNotNull(fromBundle, "FromBundle should not be null");

        System.out.println("After fromBundle - IV length: " + fromBundle.getIv().length);
        System.out.println("Cipher length: " + fromBundle.getCipher().length);

        // Decrypt
        fromBundle.setSymkey(symkey);
        Decryptor decryptor = new Decryptor();
        decryptor.decrypt(fromBundle);

        System.out.println("After decryption - Code: " + fromBundle.getCode());
        System.out.println("Data length: " + (fromBundle.getData() != null ? fromBundle.getData().length : "null"));

        assertEquals(Integer.valueOf(0), fromBundle.getCode(), "Decryption should succeed: " + fromBundle.getMessage());
        assertArrayEquals(plaintext, fromBundle.getData(), "Data should match");
    }
}
