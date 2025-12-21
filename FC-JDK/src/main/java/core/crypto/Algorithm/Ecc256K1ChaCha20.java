package core.crypto.Algorithm;

import core.crypto.CryptoDataByte;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Ecc256K1ChaCha20 combines secp256k1 ECDH with HKDF key derivation and ChaCha20 encryption.
 *
 * This provides:
 * - Elliptic curve Diffie-Hellman (ECDH) for shared secret generation
 * - HKDF (HMAC-based Key Derivation Function) for deriving encryption keys
 * - ChaCha20 stream cipher for encryption/decryption
 *
 * Key features:
 * - Post-quantum ready architecture (can replace ECDH with quantum-resistant KEM)
 * - Constant-time operations resistant to timing attacks
 * - High performance stream cipher
 * - Proper key derivation with domain separation
 *
 * Usage:
 * - AsyOneWay: Sender generates ephemeral key pair, encrypts with recipient's public key
 * - AsyTwoWay: Both parties use their respective key pairs for bidirectional encryption
 */
public class Ecc256K1ChaCha20 implements AsymmetricCipher {

    private static final Ecc256K1ChaCha20 INSTANCE = new Ecc256K1ChaCha20();

    public static Ecc256K1ChaCha20 getInstance() {
        return INSTANCE;
    }

    public static final String INFO = "hkdf-chacha20";

    /**
     * Generate shared secret using ECDH between private and public keys.
     *
     * @param priKeyBytes 32-byte private key
     * @param pubKeyBytes 33-byte compressed public key
     * @return 32-byte shared secret
     */
    @Override
    public byte[] getSharedSecret(byte[] priKeyBytes, byte[] pubKeyBytes) {
        return Ecc256K1Hkdf.getSharedSecret(priKeyBytes, pubKeyBytes);
    }

    /**
     * Static version for backward compatibility.
     */
    public static byte[] getSharedSecretStatic(byte[] priKeyBytes, byte[] pubKeyBytes) {
        return Ecc256K1Hkdf.getSharedSecret(priKeyBytes, pubKeyBytes);
    }

    /**
     * Derive symmetric key from shared secret using HKDF.
     *
     * @param sharedSecret 32-byte shared secret from ECDH
     * @param nonce 12-byte nonce (used as salt in HKDF)
     * @return 32-byte symmetric key for ChaCha20
     * @throws Exception if HKDF derivation fails
     */
    @Override
    @NotNull
    public byte[] sharedSecretToSymkey(byte[] sharedSecret, byte[] nonce) throws Exception {
        return HKDF.hkdf(sharedSecret, nonce, INFO.getBytes(), 32);
    }

    /**
     * Static version for backward compatibility.
     */
    @NotNull
    public static byte[] sharedSecretToSymkeyStatic(byte[] sharedSecret, byte[] nonce) throws Exception {
        return HKDF.hkdf(sharedSecret, nonce, INFO.getBytes(), 32);
    }

    /**
     * Convert asymmetric key pair to symmetric key using ECDH+HKDF.
     *
     * @param priKey 32-byte private key (own)
     * @param pubKey 33-byte public key (other party)
     * @param nonce 12-byte nonce for key derivation
     * @return 32-byte symmetric key for ChaCha20 encryption
     * @throws Exception if key derivation fails
     */
    @Override
    public byte[] asyKeyToSymkey(byte[] priKey, byte[] pubKey, byte[] nonce) throws Exception {
        byte[] symkey;

        // Perform ECDH to get shared secret
        byte[] sharedSecret = getSharedSecret(priKey, pubKey);

        // Derive symmetric key using HKDF
        symkey = sharedSecretToSymkey(sharedSecret, nonce);

        // Clear shared secret from memory
        Arrays.fill(sharedSecret, (byte) 0);

        return symkey;
    }

    /**
     * Static version for backward compatibility.
     */
    public static byte[] asyKeyToSymkeyStatic(byte[] priKey, byte[] pubKey, byte[] nonce) throws Exception {
        return INSTANCE.asyKeyToSymkey(priKey, pubKey, nonce);
    }

    /**
     * Encrypt data using ECDH+HKDF+ChaCha20.
     *
     * @param plaintext Input plaintext data
     * @param priKey 32-byte private key (sender's ephemeral or own key)
     * @param pubKey 33-byte public key (recipient's public key)
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
        cryptoDataByte.setAlg(data.fcData.AlgorithmId.FC_EccK1ChaCha20_No1_NrC7);

        // Encrypt using ChaCha20
        java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(plaintext);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();

        ChaCha20.encrypt(bis, bos, symkey, nonce, cryptoDataByte);

        if (cryptoDataByte.getCode() == 0 || cryptoDataByte.getCode() == null) {
            cryptoDataByte.setCipher(bos.toByteArray());
            cryptoDataByte.setAlg(data.fcData.AlgorithmId.FC_EccK1ChaCha20_No1_NrC7);
        }

        return cryptoDataByte;
    }

    /**
     * Static version for backward compatibility.
     */
    public static CryptoDataByte encryptStatic(byte[] plaintext, byte[] priKey, byte[] pubKey, byte[] nonce) throws Exception {
        return INSTANCE.encrypt(plaintext, priKey, pubKey, nonce);
    }

    /**
     * Decrypt data using ECDH+HKDF+ChaCha20.
     *
     * @param ciphertext Input ciphertext data
     * @param priKey 32-byte private key (recipient's own key)
     * @param pubKey 33-byte public key (sender's ephemeral or own key)
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
        cryptoDataByte.setAlg(data.fcData.AlgorithmId.FC_ChaCha20_No1_NrC7);

        // Decrypt using ChaCha20
        ChaCha20.decrypt(cryptoDataByte);

        // Restore the asymmetric algorithm ID
        cryptoDataByte.setAlg(data.fcData.AlgorithmId.FC_EccK1ChaCha20_No1_NrC7);

        return cryptoDataByte;
    }

    /**
     * Static version for backward compatibility.
     */
    public static CryptoDataByte decryptStatic(byte[] ciphertext, byte[] priKey, byte[] pubKey, byte[] nonce) throws Exception {
        return INSTANCE.decrypt(ciphertext, priKey, pubKey, nonce);
    }

    /**
     * Encrypt stream using ECDH+HKDF+ChaCha20.
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
        byte[] symkey = INSTANCE.asyKeyToSymkey(priKey, pubKey, nonce);
        cryptoDataByte.setSymkey(symkey);
        cryptoDataByte.setIv(nonce);
        cryptoDataByte.setAlg(data.fcData.AlgorithmId.FC_EccK1ChaCha20_No1_NrC7);

        // Encrypt using ChaCha20
        ChaCha20.encrypt(inputStream, outputStream, symkey, nonce, cryptoDataByte);

        cryptoDataByte.setAlg(data.fcData.AlgorithmId.FC_EccK1ChaCha20_No1_NrC7);

        return cryptoDataByte;
    }

    /**
     * Decrypt stream using ECDH+HKDF+ChaCha20.
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
        byte[] symkey = INSTANCE.asyKeyToSymkey(priKey, pubKey, nonce);
        cryptoDataByte.setSymkey(symkey);
        cryptoDataByte.setIv(nonce);

        // Decrypt using ChaCha20
        ChaCha20.decryptStream(inputStream, outputStream, cryptoDataByte);
    }
}
