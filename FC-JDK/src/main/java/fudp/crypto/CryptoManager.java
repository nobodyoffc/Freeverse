package fudp.crypto;

import core.crypto.Algorithm.Ecc256K1AesGcm256;
import core.crypto.CryptoDataByte;
import core.crypto.Decryptor;
import core.crypto.EncryptType;
import core.crypto.Encryptor;
import core.crypto.KeyTools;
import data.fcData.AlgorithmId;
import fudp.util.ByteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Hex;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Crypto manager for FUDP protocol.
 * 
 * Handles encryption/decryption using only AsyTwoWay (ECDH) mode.
 * This eliminates the complexity of symmetric key negotiation.
 *
 * Performance optimization: Uses ThreadLocal pre-allocated JCA instances
 * (Mac for HKDF, Cipher for AES-GCM) to avoid expensive provider lookups
 * and object creation on every packet. This is critical for high-throughput
 * file transfers where 10,000+ packets per transfer are common.
 */
public class CryptoManager {
    private static final Logger log = LoggerFactory.getLogger(CryptoManager.class);

    private final byte[] localPrivateKey;
    private final byte[] localPublicKey;
    private final SecureRandom secureRandom;

    // ECDH shared secret cache: peerPubKeyHex -> sharedSecret
    // This cache improves performance by avoiding repeated ECDH computation
    private final Map<String, byte[]> ecdhCache;

    // Default algorithm for AsyTwoWay encryption
    private static final AlgorithmId DEFAULT_ASY_ALGORITHM = AlgorithmId.FC_EccK1AesGcm256_No1_NrC7;

    // HKDF constants (must match HKDF.java and Ecc256K1AesGcm256.INFO)
    private static final String HMAC_ALGO = "HmacSHA512";
    private static final String HKDF_INFO = "hkdf"; // Must match Ecc256K1AesGcm256.INFO

    // ThreadLocal pre-allocated JCA instances to avoid costly getInstance() per packet.
    // Mac.getInstance and Cipher.getInstance involve synchronized provider lookups,
    // Security.addProvider() calls, and object allocation that dominate per-packet cost.
    private static final ThreadLocal<Mac> TL_MAC_EXTRACT = ThreadLocal.withInitial(() -> {
        try { return Mac.getInstance(HMAC_ALGO); } catch (Exception e) { throw new RuntimeException(e); }
    });
    private static final ThreadLocal<Mac> TL_MAC_EXPAND = ThreadLocal.withInitial(() -> {
        try { return Mac.getInstance(HMAC_ALGO); } catch (Exception e) { throw new RuntimeException(e); }
    });
    private static final ThreadLocal<Cipher> TL_CIPHER = ThreadLocal.withInitial(() -> {
        try { return Cipher.getInstance("AES/GCM/NoPadding"); } catch (Exception e) { throw new RuntimeException(e); }
    });

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
            byte[] dummyData = new byte[32];
            secureRandom.nextBytes(dummyData);
            
            // Warm up by encrypting to ourselves (self-ECDH).
            // This ensures decrypt uses the exact same shared secret as encrypt,
            // because both sides resolve to ECDH(localPrivateKey, localPublicKey).
            CryptoDataByte encrypted = encryptAsyTwoWay(dummyData, localPublicKey);
            
            // Warm up decryption with the fast path
            try {
                decryptAsyTwoWay(encrypted);
            } catch (Exception ignore) {
                // Non-critical warmup failure
            }
            
            // Clean up the self-ECDH cache entry (not useful for real operation)
            ecdhCache.remove(Hex.toHex(localPublicKey));
        } catch (Exception e) {
            log.debug("[CryptoManager] Warmup failed (non-critical)", e);
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

    // ---- Fast HKDF (reuses ThreadLocal Mac instances) ----

    /**
     * Fast HKDF-Extract using pre-allocated Mac instance.
     * Equivalent to HKDF.extract(salt, ikm) but avoids Mac.getInstance().
     */
    private static byte[] hkdfExtract(byte[] salt, byte[] ikm) throws Exception {
        byte[] effectiveSalt = (salt != null && salt.length > 0) ? salt : new byte[64]; // SHA-512 output = 64 bytes
        Mac mac = TL_MAC_EXTRACT.get();
        mac.init(new SecretKeySpec(effectiveSalt, HMAC_ALGO));
        return mac.doFinal(ikm);
    }

    /**
     * Fast HKDF-Expand using pre-allocated Mac instance.
     * Equivalent to HKDF.expand(prk, info, 32) but avoids Mac.getInstance().
     */
    private static byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws Exception {
        Mac mac = TL_MAC_EXPAND.get();
        mac.init(new SecretKeySpec(prk, HMAC_ALGO));
        // For 32-byte output with SHA-512 (64 byte hash), only one iteration needed
        mac.update(new byte[0]); // previousT = empty for first iteration
        if (info != null) mac.update(info);
        mac.update((byte) 1);
        byte[] t = mac.doFinal();
        byte[] result = new byte[length];
        System.arraycopy(t, 0, result, 0, length);
        return result;
    }

    /**
     * Fast HKDF key derivation: sharedSecret + iv → 32-byte symmetric key.
     * Replaces Ecc256K1AesGcm256.sharedSecretToSymkey() with zero allocation overhead.
     */
    private static byte[] deriveSymKey(byte[] sharedSecret, byte[] iv) throws Exception {
        byte[] prk = hkdfExtract(iv, sharedSecret);
        return hkdfExpand(prk, HKDF_INFO.getBytes(), 32);
    }

    // ---- Fast AES-GCM (reuses ThreadLocal Cipher instance) ----

    /**
     * Fast AES-GCM-256 encryption using pre-allocated Cipher instance.
     * Bypasses the expensive Encryptor.encryptBySymkeyBase() which calls
     * Security.addProvider(), Cipher.getInstance(), and hash computation per call.
     */
    private static byte[] aesGcmEncrypt(byte[] plaintext, byte[] key, byte[] iv) throws Exception {
        Cipher cipher = TL_CIPHER.get();
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
        return cipher.doFinal(plaintext);
    }

    /**
     * Fast AES-GCM-256 decryption using pre-allocated Cipher instance.
     * Bypasses AesGcm256.decrypt() which calls Security.addProvider(),
     * Cipher.getInstance(), and Hash.sha256x2() per call.
     */
    private static byte[] aesGcmDecrypt(byte[] ciphertext, byte[] key, byte[] iv) throws Exception {
        Cipher cipher = TL_CIPHER.get();
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
        return cipher.doFinal(ciphertext);
    }

    // ---- Public encrypt/decrypt API ----

    /**
     * Encrypt payload using AsyTwoWay (ECDH) mode with shared secret caching.
     * 
     * Uses fast path: cached ECDH shared secret → fast HKDF → fast AES-GCM.
     * Avoids per-packet: Security.addProvider(), Mac.getInstance(), Cipher.getInstance(),
     * SHA-256 hashing, and ByteArrayInputStream/OutputStream allocation.
     * 
     * @param plaintext The data to encrypt
     * @param peerPublicKey The peer's public key
     * @return CryptoDataByte bundle
     */
    public CryptoDataByte encryptAsyTwoWay(byte[] plaintext, byte[] peerPublicKey) {
        // Get or compute shared secret (ECDH is the expensive part)
        String peerPubKeyHex = Hex.toHex(peerPublicKey);
        byte[] sharedSecret = ecdhCache.computeIfAbsent(peerPubKeyHex, k -> {
            byte[] secret = Ecc256K1AesGcm256.getInstance().getSharedSecret(localPrivateKey, peerPublicKey);
            return secret;
        });
        
        // Fast path: reuse ThreadLocal instances
        try {
            // Generate random IV (12 bytes for AES-GCM per NIST)
            byte[] iv = generateIv(12);
            
            // Fast HKDF key derivation (reuses ThreadLocal Mac)
            byte[] symKey = deriveSymKey(sharedSecret, iv);
            
            // Fast AES-GCM encryption (reuses ThreadLocal Cipher)
            byte[] ciphertext = aesGcmEncrypt(plaintext, symKey, iv);
            
            CryptoDataByte result = new CryptoDataByte();
            result.setCipher(ciphertext);
            result.setIv(iv);
            result.setType(EncryptType.AsyTwoWay);
            result.setPubkeyA(localPublicKey);
            result.setPubkeyB(peerPublicKey);
            result.setAlg(DEFAULT_ASY_ALGORITHM);
            
            return result;
        } catch (Exception e) {
            // Fallback to standard encryption if fast path fails
            log.debug("[CryptoManager] Fast encrypt failed, falling back to standard path", e);
            ecdhCache.remove(peerPubKeyHex);
            Encryptor encryptor = new Encryptor(DEFAULT_ASY_ALGORITHM);
            return encryptor.encryptByAsyTwoWay(plaintext, localPrivateKey, peerPublicKey);
        }
    }

    /**
     * Decrypt an AsyTwoWay encrypted bundle with shared secret caching.
     * 
     * Uses fast path: cached ECDH shared secret → fast HKDF → fast AES-GCM.
     * 
     * @param cryptoData The encrypted bundle
     * @return Decrypted plaintext
     * @throws RuntimeException if decryption fails
     */
    public byte[] decryptAsyTwoWay(CryptoDataByte cryptoData) {
        byte[] peerPubKey = cryptoData.getPubkeyA();
        if (peerPubKey == null) {
            throw new RuntimeException("Decryption failed: missing sender public key");
        }
        
        // Get or compute shared secret (ECDH is the expensive part)
        String peerPubKeyHex = Hex.toHex(peerPubKey);
        byte[] sharedSecret = ecdhCache.computeIfAbsent(peerPubKeyHex, k -> {
            byte[] secret = Ecc256K1AesGcm256.getInstance().getSharedSecret(localPrivateKey, peerPubKey);
            return secret;
        });
        
        // Fast path: reuse ThreadLocal instances
        try {
            byte[] iv = cryptoData.getIv();
            byte[] ciphertext = cryptoData.getCipher();
            
            // Fast HKDF key derivation (reuses ThreadLocal Mac)
            byte[] symKey = deriveSymKey(sharedSecret, iv);
            
            // Fast AES-GCM decryption (reuses ThreadLocal Cipher)
            return aesGcmDecrypt(ciphertext, symKey, iv);
        } catch (Exception e) {
            // Fallback to standard decryption if fast path fails
            log.debug("[CryptoManager] Fast decrypt failed, falling back to standard path", e);
            ecdhCache.remove(peerPubKeyHex);
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
