package core.crypto.Algorithm;

import core.crypto.CryptoDataByte;
import core.crypto.EncryptType;
import data.fcData.AlgorithmId;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * X25519AesGcm256 combines X25519 key exchange with HKDF key derivation and AES-GCM-256 encryption.
 *
 * This provides:
 * - X25519 Diffie-Hellman for shared secret generation
 * - HKDF (HMAC-based Key Derivation Function) for deriving encryption keys
 * - AES-GCM-256 for authenticated encryption
 *
 * Key features:
 * - Modern elliptic curve (Curve25519) with better performance
 * - Constant-time operations resistant to timing attacks
 * - Authenticated encryption (no separate integrity check needed)
 * - Uses 12-byte IV for AES-GCM (per NIST recommendation)
 *
 * Note: X25519 uses 32-byte public keys (not compressed like secp256k1)
 *
 * Usage:
 * - AsyOneWay: Sender generates ephemeral key pair, encrypts with recipient's public key
 * - AsyTwoWay: Both parties use their respective key pairs for bidirectional encryption
 */
public class X25519AesGcm256 implements AsymmetricCipher {

    public static final String INFO = "hkdf";

    private static final X25519AesGcm256 INSTANCE = new X25519AesGcm256();

    public static X25519AesGcm256 getInstance() {
        return INSTANCE;
    }

    /**
     * Generate shared secret using X25519 key agreement.
     *
     * @param priKeyBytes 32-byte X25519 private key
     * @param pubKeyBytes 32-byte X25519 public key
     * @return 32-byte shared secret
     */
    @Override
    public byte[] getSharedSecret(byte[] priKeyBytes, byte[] pubKeyBytes) {
        return X25519.getSharedSecret(priKeyBytes, pubKeyBytes);
    }

    /**
     * Derive symmetric key from shared secret using HKDF.
     *
     * @param sharedSecret 32-byte shared secret from X25519
     * @param nonce 12-byte nonce (used as salt in HKDF)
     * @return 32-byte symmetric key for AES-GCM-256
     * @throws Exception if HKDF derivation fails
     */
    @Override
    @NotNull
    public byte[] sharedSecretToSymkey(byte[] sharedSecret, byte[] nonce) throws Exception {
        return HKDF.hkdf(sharedSecret, nonce, INFO.getBytes(), 32);
    }

    /**
     * Convert asymmetric key pair to symmetric key using X25519+HKDF.
     *
     * @param priKey 32-byte X25519 private key (own)
     * @param pubKey 32-byte X25519 public key (other party)
     * @param nonce 12-byte nonce for key derivation
     * @return 32-byte symmetric key for AES-GCM-256 encryption
     * @throws Exception if key derivation fails
     */
    @Override
    public byte[] asyKeyToSymkey(byte[] priKey, byte[] pubKey, byte[] nonce) throws Exception {
        byte[] symkey;

        // Perform X25519 to get shared secret
        byte[] sharedSecret = getSharedSecret(priKey, pubKey);

        // Derive symmetric key using HKDF
        symkey = sharedSecretToSymkey(sharedSecret, nonce);

        // Clear shared secret from memory
        Arrays.fill(sharedSecret, (byte) 0);

        return symkey;
    }

    /**
     * Encrypt data using X25519+HKDF+AES-GCM-256.
     *
     * @param plaintext Input plaintext data
     * @param priKey 32-byte X25519 private key (sender's ephemeral or own key)
     * @param pubKey 32-byte X25519 public key (recipient's public key)
     * @param nonce 12-byte nonce/IV
     * @return CryptoDataByte containing encrypted data and metadata
     * @throws Exception if encryption fails
     */
    @Override
    public CryptoDataByte encrypt(byte[] plaintext, byte[] priKey, byte[] pubKey, byte[] nonce) throws Exception {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();

        // Derive symmetric key
        byte[] symkey = asyKeyToSymkey(priKey, pubKey, nonce);
        cryptoDataByte.setSymkey(symkey);
        cryptoDataByte.setIv(nonce);
        cryptoDataByte.setAlg(AlgorithmId.FC_X25519AesGcm256_No1_NrC7);

        // Encrypt using AES-GCM-256
        ByteArrayInputStream bis = new ByteArrayInputStream(plaintext);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        AesGcm256.encrypt(bis, bos, symkey, nonce, cryptoDataByte);

        if (cryptoDataByte.getCode() == 0 || cryptoDataByte.getCode() == null) {
            cryptoDataByte.setCipher(bos.toByteArray());
            cryptoDataByte.setAlg(AlgorithmId.FC_X25519AesGcm256_No1_NrC7);
        }

        return cryptoDataByte;
    }

    /**
     * Decrypt data using X25519+HKDF+AES-GCM-256.
     *
     * @param ciphertext Input ciphertext data
     * @param priKey 32-byte X25519 private key (recipient's own key)
     * @param pubKey 32-byte X25519 public key (sender's ephemeral or own key)
     * @param nonce 12-byte nonce/IV
     * @return CryptoDataByte containing decrypted data and metadata
     * @throws Exception if decryption fails
     */
    @Override
    public CryptoDataByte decrypt(byte[] ciphertext, byte[] priKey, byte[] pubKey, byte[] nonce) throws Exception {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();

        // Derive symmetric key
        byte[] symkey = asyKeyToSymkey(priKey, pubKey, nonce);
        cryptoDataByte.setSymkey(symkey);
        cryptoDataByte.setIv(nonce);
        cryptoDataByte.setCipher(ciphertext);
        cryptoDataByte.setAlg(AlgorithmId.FC_AesGcm256_No1_NrC7);

        // Decrypt using AES-GCM-256
        AesGcm256.decrypt(cryptoDataByte);

        // Restore the asymmetric algorithm ID
        cryptoDataByte.setAlg(AlgorithmId.FC_X25519AesGcm256_No1_NrC7);

        return cryptoDataByte;
    }

    /**
     * Encrypt stream using X25519+HKDF+AES-GCM-256.
     *
     * @param inputStream Input stream containing plaintext
     * @param outputStream Output stream for ciphertext
     * @param priKey Private key
     * @param pubKey Public key
     * @param nonce Nonce/IV
     * @param cryptoDataByte CryptoDataByte to store metadata
     * @return Updated CryptoDataByte
     * @throws Exception if encryption fails
     */
    public static CryptoDataByte encryptStream(java.io.InputStream inputStream, java.io.OutputStream outputStream,
                                               byte[] priKey, byte[] pubKey, byte[] nonce, CryptoDataByte cryptoDataByte) throws Exception {
        if (cryptoDataByte == null) {
            cryptoDataByte = new CryptoDataByte();
        }

        // Derive symmetric key
        byte[] symkey = X25519.asyKeyToSymkey(priKey, pubKey, nonce);
        try {
            cryptoDataByte.setSymkey(symkey);
            cryptoDataByte.setIv(nonce);
            cryptoDataByte.setAlg(AlgorithmId.FC_X25519AesGcm256_No1_NrC7);

            // Encrypt using AES-GCM-256
            AesGcm256.encrypt(inputStream, outputStream, symkey, nonce, cryptoDataByte);

            cryptoDataByte.setAlg(AlgorithmId.FC_X25519AesGcm256_No1_NrC7);

            return cryptoDataByte;
        } finally {
            java.util.Arrays.fill(symkey, (byte) 0);
        }
    }

    /**
     * Decrypt stream using X25519+HKDF+AES-GCM-256.
     *
     * @param inputStream Input stream containing ciphertext
     * @param outputStream Output stream for plaintext
     * @param priKey Private key
     * @param pubKey Public key
     * @param nonce Nonce/IV
     * @param cryptoDataByte CryptoDataByte to store metadata
     * @throws Exception if decryption fails
     */
    public static void decryptStream(java.io.InputStream inputStream, java.io.OutputStream outputStream,
                                     byte[] priKey, byte[] pubKey, byte[] nonce, CryptoDataByte cryptoDataByte) throws Exception {
        if (cryptoDataByte == null) {
            cryptoDataByte = new CryptoDataByte();
        }

        // Derive symmetric key
        byte[] symkey = X25519.asyKeyToSymkey(priKey, pubKey, nonce);
        try {
            cryptoDataByte.setSymkey(symkey);
            cryptoDataByte.setIv(nonce);

            // Decrypt using AES-GCM-256
            AesGcm256.decryptStream(inputStream, outputStream, cryptoDataByte);
        } finally {
            java.util.Arrays.fill(symkey, (byte) 0);
        }
    }

    /**
     * Generate X25519 public key from private key.
     *
     * @param priKeyBytes 32-byte private key
     * @return 32-byte public key
     */
    public static byte[] generatePublicKey(byte[] priKeyBytes) {
        return X25519.generatePublicKey(priKeyBytes);
    }
}
