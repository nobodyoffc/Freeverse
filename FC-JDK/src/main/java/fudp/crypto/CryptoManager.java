package fudp.crypto;

import core.crypto.Algorithm.Ecc256K1AesGcm256;
import core.crypto.CryptoDataByte;
import core.crypto.Decryptor;
import core.crypto.EncryptType;
import core.crypto.Encryptor;
import core.crypto.KeyTools;
import data.fcData.AlgorithmId;
import fudp.util.ByteUtils;
import utils.Hex;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Crypto manager for FUDP protocol.
 * 
 * Handles encryption/decryption using only AsyTwoWay (ECDH) mode.
 * This eliminates the complexity of symmetric key negotiation.
 */
public class CryptoManager {

    private final byte[] localPrivateKey;
    private final byte[] localPublicKey;
    private final SecureRandom secureRandom;

    // ECDH shared secret cache: peerPubKeyHex -> sharedSecret
    // This cache improves performance by avoiding repeated ECDH computation
    private final Map<String, byte[]> ecdhCache;

    // Default algorithm for AsyTwoWay encryption
    private static final AlgorithmId DEFAULT_ASY_ALGORITHM = AlgorithmId.FC_EccK1AesGcm256_No1_NrC7;

    /**
     * Session Epoch: A random 8-byte value generated at node startup.
     * This is included in every encrypted packet payload.
     * 
     * When a peer detects that our session epoch has changed,
     * they know we have restarted and should reset their
     * replay protection window for us.
     */
    private final long sessionEpoch;

    public CryptoManager(byte[] privateKey) {
        this.localPrivateKey = privateKey;
        this.localPublicKey = KeyTools.prikeyToPubkey(privateKey);
        this.secureRandom = new SecureRandom();
        this.ecdhCache = new ConcurrentHashMap<>();
        
        // Generate random session epoch at startup
        this.sessionEpoch = secureRandom.nextLong();
        
        // Warm up JVM crypto - first encryption is slow due to class loading and JIT
        warmUp();
    }
    
    /**
     * Warm up the JVM crypto subsystem to avoid slow first encryption.
     * This performs a dummy encryption/decryption to trigger class loading and JIT compilation.
     */
    private void warmUp() {
        try {
            long start = System.currentTimeMillis();
            byte[] dummyData = new byte[32];
            secureRandom.nextBytes(dummyData);
            
            // Generate a dummy key pair for warmup
            byte[] dummyPrivKey = new byte[32];
            secureRandom.nextBytes(dummyPrivKey);
            byte[] dummyPubKey = KeyTools.prikeyToPubkey(dummyPrivKey);
            
            // Warm up encryption
            Encryptor encryptor = new Encryptor(DEFAULT_ASY_ALGORITHM);
            CryptoDataByte encrypted = encryptor.encryptByAsyTwoWay(dummyData, localPrivateKey, dummyPubKey);
            
            // Warm up decryption
            encrypted.setPrikeyB(dummyPrivKey);
            Decryptor decryptor = new Decryptor();
            decryptor.decrypt(encrypted);
            
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("[CryptoManager] JVM crypto warmup completed in " + elapsed + "ms");
        } catch (Exception e) {
            System.err.println("[CryptoManager] Warmup failed (non-critical): " + e.getMessage());
        }
    }
    
    /**
     * Get the session epoch for this node instance.
     * This value is unique per process lifetime and is included in every packet
     * to allow peers to detect when we have restarted.
     */
    public long getSessionEpoch() {
        return sessionEpoch;
    }

    /**
     * Encrypt payload using AsyTwoWay (ECDH) mode with shared secret caching.
     * 
     * ECDH computation is expensive (~30-50ms). By caching the shared secret,
     * subsequent encryptions to the same peer only need AES-GCM (~1ms).
     * 
     * @param plaintext The data to encrypt
     * @param peerPublicKey The peer's public key
     * @return CryptoDataByte bundle
     */
    public CryptoDataByte encryptAsyTwoWay(byte[] plaintext, byte[] peerPublicKey) {
        long start = System.currentTimeMillis();
        
        // Get or compute shared secret (ECDH is the expensive part)
        String peerPubKeyHex = Hex.toHex(peerPublicKey);
        byte[] sharedSecret = ecdhCache.computeIfAbsent(peerPubKeyHex, k -> {
            long ecdhStart = System.currentTimeMillis();
            byte[] secret = Ecc256K1AesGcm256.getInstance().getSharedSecret(localPrivateKey, peerPublicKey);
            System.err.println("[CRYPTO] ECDH computation took " + (System.currentTimeMillis() - ecdhStart) + "ms (cached for future use)");
            return secret;
        });
        
        // Use cached shared secret for fast AES-GCM encryption
        try {
            // Generate random IV (12 bytes for AES-GCM per NIST)
            byte[] iv = generateIv(12);
            
            // Derive symmetric key from cached shared secret (fast, just HKDF)
            byte[] symKey = Ecc256K1AesGcm256.getInstance().sharedSecretToSymkey(sharedSecret, iv);
            
            // Encrypt with AES-GCM directly (no ECDH needed)
            CryptoDataByte result = new CryptoDataByte();
            result.setSymkey(symKey);
            result.setIv(iv);
            result.setAlg(DEFAULT_ASY_ALGORITHM);
            
            java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(plaintext);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            core.crypto.Algorithm.AesGcm256.encrypt(bis, bos, symKey, iv, result);
            
            if (result.getCode() == null || result.getCode() == 0) {
                result.setCipher(bos.toByteArray());
                result.setType(EncryptType.AsyTwoWay);
                result.setPubkeyA(localPublicKey);
                result.setPubkeyB(peerPublicKey);
                result.setAlg(DEFAULT_ASY_ALGORITHM);
            }
            
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > 20) {
                System.err.println("[CRYPTO] encryptAsyTwoWay took " + elapsed + "ms (cached ECDH) - may indicate GC pause");
            }
            return result;
        } catch (Exception e) {
            // Fallback to standard encryption if caching fails
            System.err.println("[CRYPTO] Cached encryption failed, falling back to standard: " + e.getMessage());
            ecdhCache.remove(peerPubKeyHex); // Clear potentially corrupted cache
            Encryptor encryptor = new Encryptor(DEFAULT_ASY_ALGORITHM);
            return encryptor.encryptByAsyTwoWay(plaintext, localPrivateKey, peerPublicKey);
        }
    }

    /**
     * Decrypt an AsyTwoWay encrypted bundle with shared secret caching.
     * 
     * Uses cached ECDH shared secret for faster decryption on subsequent messages.
     * 
     * @param cryptoData The encrypted bundle
     * @return Decrypted plaintext
     * @throws RuntimeException if decryption fails
     */
    public byte[] decryptAsyTwoWay(CryptoDataByte cryptoData) {
        long start = System.currentTimeMillis();
        
        byte[] peerPubKey = cryptoData.getPubkeyA();
        if (peerPubKey == null) {
            throw new RuntimeException("Decryption failed: missing sender public key");
        }
        
        // Get or compute shared secret (ECDH is the expensive part)
        String peerPubKeyHex = Hex.toHex(peerPubKey);
        byte[] sharedSecret = ecdhCache.computeIfAbsent(peerPubKeyHex, k -> {
            long ecdhStart = System.currentTimeMillis();
            byte[] secret = Ecc256K1AesGcm256.getInstance().getSharedSecret(localPrivateKey, peerPubKey);
            System.err.println("[CRYPTO] ECDH computation (decrypt) took " + (System.currentTimeMillis() - ecdhStart) + "ms (cached)");
            return secret;
        });
        
        // Use cached shared secret for fast AES-GCM decryption
        try {
            byte[] iv = cryptoData.getIv();
            byte[] ciphertext = cryptoData.getCipher();
            
            // Derive symmetric key from cached shared secret (fast, just HKDF)
            byte[] symKey = Ecc256K1AesGcm256.getInstance().sharedSecretToSymkey(sharedSecret, iv);
            
            // Decrypt with AES-GCM directly (no ECDH needed)
            CryptoDataByte decryptData = new CryptoDataByte();
            decryptData.setSymkey(symKey);
            decryptData.setIv(iv);
            decryptData.setCipher(ciphertext);
            decryptData.setAlg(AlgorithmId.FC_AesGcm256_No1_NrC7);
            
            core.crypto.Algorithm.AesGcm256.decrypt(decryptData);
            
            if (decryptData.getCode() != null && decryptData.getCode() != 0) {
                throw new RuntimeException("AES-GCM decryption failed: " + decryptData.getMessage());
            }
            
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > 20) {
                System.err.println("[CRYPTO] decryptAsyTwoWay took " + elapsed + "ms (cached ECDH) - may indicate GC pause");
            }
            return decryptData.getData();
        } catch (Exception e) {
            // Fallback to standard decryption if caching fails
            System.err.println("[CRYPTO] Cached decryption failed, falling back to standard: " + e.getMessage());
            ecdhCache.remove(peerPubKeyHex); // Clear potentially corrupted cache
            cryptoData.setPrikeyB(localPrivateKey);
            Decryptor decryptor = new Decryptor();
            decryptor.decrypt(cryptoData);

            if (cryptoData.getCode() != null && cryptoData.getCode() != 0) {
                throw new RuntimeException("Decryption failed: " + cryptoData.getMessage());
            }
            return cryptoData.getData();
        }
    }

    /**
     * Generate a random IV.
     * 
     * @param length IV length in bytes
     * @return Random IV
     */
    public byte[] generateIv(int length) {
        byte[] iv = new byte[length];
        secureRandom.nextBytes(iv);
        return iv;
    }

    /**
     * Get the local public key.
     */
    public byte[] getLocalPublicKey() {
        return localPublicKey;
    }

    /**
     * Get the local FID (address from public key).
     */
    public String getLocalFid() {
        return KeyTools.pubkeyToFchAddr(localPublicKey);
    }

    /**
     * Convert public key to FID.
     */
    public static String pubkeyToFid(byte[] pubkey) {
        return KeyTools.pubkeyToFchAddr(pubkey);
    }

    /**
     * Clear sensitive data from memory.
     */
    public void clear() {
        ByteUtils.clear(localPrivateKey);
        ecdhCache.values().forEach(ByteUtils::clear);
        ecdhCache.clear();
    }
}
