package fudp.crypto;

import core.crypto.CryptoDataByte;
import org.bitcoinj.core.ECKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CryptoManager - testing encryption/decryption.
 */
public class CryptoManagerTest {

    private CryptoManager managerA;
    private CryptoManager managerB;
    private byte[] privateKeyA;
    private byte[] privateKeyB;

    @BeforeEach
    public void setUp() {
        // Generate valid secp256k1 keys using ECKey
        ECKey keyA = new ECKey();
        ECKey keyB = new ECKey();

        privateKeyA = keyA.getPrivKeyBytes();
        privateKeyB = keyB.getPrivKeyBytes();

        managerA = new CryptoManager(privateKeyA);
        managerB = new CryptoManager(privateKeyB);
    }

    @Test
    public void testAsyTwoWayEncryptDecrypt() {
        // Test data
        byte[] plaintext = "Hello, World!".getBytes();

        // A encrypts for B
        CryptoDataByte encrypted = managerA.encryptAsyTwoWay(plaintext, managerB.getLocalPublicKey());
        assertNotNull(encrypted, "Encryption should return non-null");
        assertEquals(Integer.valueOf(0), encrypted.getCode(), "Encryption should succeed: " + encrypted.getMessage());

        // B decrypts - need both prikeyB and pubkeyA for ECDH
        encrypted.setPrikeyB(privateKeyB);
        encrypted.setPubkeyA(managerA.getLocalPublicKey()); // Sender's public key for ECDH
        core.crypto.Decryptor decryptor = new core.crypto.Decryptor();
        decryptor.decryptByAsyKey(encrypted);

        assertEquals(Integer.valueOf(0), encrypted.getCode(), "Decryption should succeed: " + encrypted.getMessage());
        byte[] decrypted = encrypted.getData();
        assertNotNull(decrypted, "Decrypted data should not be null");
        assertArrayEquals(plaintext, decrypted, "Decrypted data should match original");
    }


    @Test
    public void testLargePayload() {
        // Test larger payload
        byte[] plaintext = new byte[1000];
        new SecureRandom().nextBytes(plaintext);

        // A encrypts for B
        CryptoDataByte encrypted = managerA.encryptAsyTwoWay(plaintext, managerB.getLocalPublicKey());
        assertEquals(Integer.valueOf(0), encrypted.getCode(), "Encryption should succeed: " + encrypted.getMessage());

        // B decrypts - need both prikeyB and pubkeyA for ECDH
        encrypted.setPrikeyB(privateKeyB);
        encrypted.setPubkeyA(managerA.getLocalPublicKey()); // Sender's public key for ECDH
        core.crypto.Decryptor decryptor = new core.crypto.Decryptor();
        decryptor.decryptByAsyKey(encrypted);

        assertEquals(Integer.valueOf(0), encrypted.getCode(), "Decryption should succeed: " + encrypted.getMessage());
        byte[] decrypted = encrypted.getData();
        assertArrayEquals(plaintext, decrypted, "Large payload should match");
    }

    @Test
    public void testFidGeneration() {
        String fidA = managerA.getLocalFid();
        String fidB = managerB.getLocalFid();

        assertNotNull(fidA, "FID A should not be null");
        assertNotNull(fidB, "FID B should not be null");
        assertNotEquals(fidA, fidB, "FIDs should be different");
        assertTrue(fidA.length() > 20, "FID should be reasonable length");
    }

}
