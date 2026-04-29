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

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicLong;

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

    /**
     * Default cap on the ECDH shared-secret cache. With LRU eviction this
     * gives a bounded memory footprint (~16 KB at 512 entries × 32 byte
     * secrets + map overhead) and reasonable hit rates for real workloads.
     * The previous "soft cap at 1000 with random eviction" turned a flood
     * of attacker-chosen pubkeys into eviction of legitimate peers.
     */
    public static final int DEFAULT_ECDH_CACHE_SIZE = 512;

    private final byte[] localPrivateKey;
    private final byte[] localPublicKey;
    private final SecureRandom secureRandom;

    // ECDH shared secret cache, bounded LRU. Key is the peer's compressed
    // public-key bytes wrapped in BytesKey so we don't allocate a hex string
    // per packet (was a real GC pressure source at 10k+ pkt/s).
    // The receive path is single-threaded (Protocol.receiveLoop) and
    // file-transfer sends are also serialised, so a synchronised
    // LinkedHashMap is sufficient and gives well-defined LRU order.
    private final LinkedHashMap<BytesKey, byte[]> ecdhCache;
    private final int ecdhCacheCap;
    private final AtomicLong ecdhEvictionCount = new AtomicLong();

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

    // AEAD failure counters. Tag failures are attack signal (tampered packets,
    // replayed-with-rewritten-header, or wrong-key bundles) — distinct from
    // generic crypto exceptions. We rate-limit logging via these counters so a
    // flood of bad packets cannot itself become a logging DoS.
    private final AtomicLong aeadTagFailCount = new AtomicLong();
    private final AtomicLong aeadOtherFailCount = new AtomicLong();

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
        this(privateKey, DEFAULT_ECDH_CACHE_SIZE);
    }

    public CryptoManager(byte[] privateKey, int ecdhCacheCap) {
        if (ecdhCacheCap <= 0) {
            throw new IllegalArgumentException("ecdhCacheCap must be positive");
        }
        this.localPrivateKey = privateKey;
        this.localPublicKey = KeyTools.prikeyToPubkey(privateKey);
        this.secureRandom = new SecureRandom();
        this.ecdhCacheCap = ecdhCacheCap;
        // accessOrder=true → get() promotes; eldest is true LRU.
        this.ecdhCache = new LinkedHashMap<>(16, 0.75f, true);

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
            removeSharedSecret(localPublicKey);
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
     *
     * @param aad optional Associated Authenticated Data (covered by the AEAD
     *            tag but not encrypted). Pass null or empty when no AAD is
     *            needed. For FUDP this is the 21-byte packet header (F1).
     */
    private static byte[] aesGcmEncrypt(byte[] plaintext, byte[] key, byte[] iv, byte[] aad) throws Exception {
        Cipher cipher = TL_CIPHER.get();
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
        if (aad != null && aad.length > 0) {
            cipher.updateAAD(aad);
        }
        return cipher.doFinal(plaintext);
    }

    /**
     * Fast AES-GCM-256 decryption using pre-allocated Cipher instance.
     * Bypasses AesGcm256.decrypt() which calls Security.addProvider(),
     * Cipher.getInstance(), and Hash.sha256x2() per call.
     *
     * @param aad optional AAD; must match the value used at encryption time
     *            byte-for-byte or the AEAD tag check will fail.
     */
    private static byte[] aesGcmDecrypt(byte[] ciphertext, byte[] key, byte[] iv, byte[] aad) throws Exception {
        Cipher cipher = TL_CIPHER.get();
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
        if (aad != null && aad.length > 0) {
            cipher.updateAAD(aad);
        }
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
        return encryptAsyTwoWay(plaintext, peerPublicKey, null);
    }

    /**
     * Encrypt with optional Associated Authenticated Data.
     *
     * <p>When {@code aad} is non-null/non-empty, it is bound to the AEAD tag
     * but not transmitted; callers and the receiver must agree on it
     * out-of-band. For FUDP, the packet header is the AAD (F1).
     *
     * <p>The fallback path through {@link Encryptor} does not support AAD.
     * If the fast path raises an unexpected exception while AAD is in use,
     * we surface it instead of silently falling back, since the fallback
     * would erase the integrity guarantee the caller is relying on.
     */
    public CryptoDataByte encryptAsyTwoWay(byte[] plaintext, byte[] peerPublicKey, byte[] aad) {
        byte[] sharedSecret = getOrComputeSharedSecret(peerPublicKey);

        // Fast path: reuse ThreadLocal instances
        try {
            // Generate random IV (12 bytes for AES-GCM per NIST)
            byte[] iv = generateIv(12);

            // Fast HKDF key derivation (reuses ThreadLocal Mac)
            byte[] symKey = deriveSymKey(sharedSecret, iv);

            // Fast AES-GCM encryption (reuses ThreadLocal Cipher)
            byte[] ciphertext = aesGcmEncrypt(plaintext, symKey, iv, aad);
            
            CryptoDataByte result = new CryptoDataByte();
            result.setCipher(ciphertext);
            result.setIv(iv);
            result.setType(EncryptType.AsyTwoWay);
            result.setPubkeyA(localPublicKey);
            result.setPubkeyB(peerPublicKey);
            result.setAlg(DEFAULT_ASY_ALGORITHM);
            
            return result;
        } catch (Exception e) {
            // Encryption rarely fails on the fast path (no tag verification on
            // encrypt). Surface as a real warning the first few times so we
            // notice misconfigurations / bugs.
            long n = aeadOtherFailCount.incrementAndGet();
            if (n <= 5 || n % 1000 == 0) {
                log.warn("[CryptoManager] Fast encrypt failed (n={}), falling back to standard path", n, e);
            }
            removeSharedSecret(peerPublicKey);
            // The standard-path Encryptor cannot bind AAD. If the caller
            // requested AAD, refuse to silently drop the integrity binding.
            if (aad != null && aad.length > 0) {
                throw new RuntimeException(
                        "Fast encrypt failed and fallback cannot bind AAD; refusing to weaken integrity", e);
            }
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
        return decryptAsyTwoWay(cryptoData, null);
    }

    /**
     * Decrypt with optional AAD. {@code aad} must match the value passed to
     * {@link #encryptAsyTwoWay(byte[], byte[], byte[])} byte-for-byte; any
     * mismatch fails the AEAD tag check and raises a tag-failure
     * {@link RuntimeException}. The fallback Decryptor cannot bind AAD, so
     * a tag failure is final — we do not retry through the slow path.
     */
    public byte[] decryptAsyTwoWay(CryptoDataByte cryptoData, byte[] aad) {
        byte[] peerPubKey = cryptoData.getPubkeyA();
        if (peerPubKey == null) {
            throw new RuntimeException("Decryption failed: missing sender public key");
        }

        byte[] sharedSecret = getOrComputeSharedSecret(peerPubKey);

        // Fast path: reuse ThreadLocal instances
        try {
            byte[] iv = cryptoData.getIv();
            byte[] ciphertext = cryptoData.getCipher();

            // Fast HKDF key derivation (reuses ThreadLocal Mac)
            byte[] symKey = deriveSymKey(sharedSecret, iv);

            // Fast AES-GCM decryption (reuses ThreadLocal Cipher)
            return aesGcmDecrypt(ciphertext, symKey, iv, aad);
        } catch (Exception e) {
            // Distinguish AEAD tag failure (attack signal) from other faults.
            // AEADBadTagException can be wrapped, so unwrap one level.
            Throwable root = (e.getCause() != null) ? e.getCause() : e;
            boolean tagFail = (e instanceof AEADBadTagException) || (root instanceof AEADBadTagException);

            if (tagFail) {
                long n = aeadTagFailCount.incrementAndGet();
                // Rate-limited warn: first 5, then every 1000th.
                if (n <= 5 || n % 1000 == 0) {
                    log.warn("[CryptoManager] AEAD tag verification failed (count={}). " +
                            "Possible tampering or wrong-key packet.", n);
                }
                // Tag failure cannot be salvaged by the slow path — same key+iv+ct.
                // Surface as a regular RuntimeException so the upper layer drops the packet.
                throw new RuntimeException("Decryption failed: AEAD tag mismatch", e);
            }

            long n = aeadOtherFailCount.incrementAndGet();
            if (n <= 5 || n % 1000 == 0) {
                log.warn("[CryptoManager] Fast decrypt failed (count={}), falling back to standard path", n, e);
            }
            removeSharedSecret(peerPubKey);
            // Standard-path Decryptor cannot verify AAD. If the caller bound
            // AAD, do not silently fall back to a path that ignores it.
            if (aad != null && aad.length > 0) {
                throw new RuntimeException(
                        "Fast decrypt failed and fallback cannot verify AAD", e);
            }
            cryptoData.setPrikeyB(localPrivateKey);
            Decryptor decryptor = new Decryptor();
            decryptor.decrypt(cryptoData);

            if (cryptoData.getCode() != null && cryptoData.getCode() != 0) {
                throw new RuntimeException("Decryption failed: " + cryptoData.getMessage());
            }
            return cryptoData.getData();
        }
    }

    /** AEAD tag-failure counter (attack signal). Exposed for monitoring/tests. */
    public long getAeadTagFailCount() {
        return aeadTagFailCount.get();
    }

    /** Other AEAD failure counter. Exposed for monitoring/tests. */
    public long getAeadOtherFailCount() {
        return aeadOtherFailCount.get();
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
        synchronized (ecdhCache) {
            ecdhCache.values().forEach(ByteUtils::clear);
            ecdhCache.clear();
        }
    }

    // ---- Bounded ECDH cache (private internals) ----

    /** Number of cached shared secrets (for monitoring/tests). */
    public int getEcdhCacheSize() {
        synchronized (ecdhCache) {
            return ecdhCache.size();
        }
    }

    /** Number of LRU evictions performed (for monitoring/tests). */
    public long getEcdhEvictionCount() {
        return ecdhEvictionCount.get();
    }

    /**
     * Look up the shared secret for a peer pubkey, computing and caching it
     * (with LRU eviction at the configured cap) on miss. The cache key is the
     * raw pubkey bytes wrapped in BytesKey — no per-packet hex allocation.
     */
    private byte[] getOrComputeSharedSecret(byte[] peerPublicKey) {
        BytesKey key = new BytesKey(peerPublicKey);
        synchronized (ecdhCache) {
            byte[] cached = ecdhCache.get(key); // promotes in access order
            if (cached != null) {
                return cached;
            }
        }
        // Compute outside the lock — ECDH is ~ms of CPU and we don't want
        // to block other lookups while it runs.
        byte[] secret = Ecc256K1AesGcm256.getInstance()
                .getSharedSecret(localPrivateKey, peerPublicKey);
        synchronized (ecdhCache) {
            byte[] existing = ecdhCache.get(key);
            if (existing != null) {
                // Another path computed it concurrently; keep the existing entry.
                return existing;
            }
            // Evict eldest until we are strictly under the cap, then insert.
            while (ecdhCache.size() >= ecdhCacheCap) {
                var it = ecdhCache.entrySet().iterator();
                if (!it.hasNext()) break;
                it.next();
                it.remove();
                ecdhEvictionCount.incrementAndGet();
            }
            ecdhCache.put(key, secret);
            return secret;
        }
    }

    /** Drop the cached shared secret for a pubkey (used on encrypt/decrypt fault). */
    private void removeSharedSecret(byte[] peerPublicKey) {
        BytesKey key = new BytesKey(peerPublicKey);
        synchronized (ecdhCache) {
            ecdhCache.remove(key);
        }
    }

    /**
     * byte[]-keyed wrapper. Required because LinkedHashMap uses Object.equals/hashCode,
     * and byte[] has identity equality. We avoid Hex.toHex(...) per packet (was a real
     * GC hot spot at high throughput). hashCode is computed once at construction.
     */
    private static final class BytesKey {
        private final byte[] data;
        private final int hash;

        BytesKey(byte[] data) {
            this.data = data;
            this.hash = Arrays.hashCode(data);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BytesKey other)) return false;
            return Arrays.equals(data, other.data);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
