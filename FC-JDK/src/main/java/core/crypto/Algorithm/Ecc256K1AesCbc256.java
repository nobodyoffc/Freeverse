package core.crypto.Algorithm;

import core.crypto.CryptoDataByte;
import core.crypto.EncryptType;
import data.fcData.AlgorithmId;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Ecc256K1AesCbc256 combines secp256k1 ECDH with SHA512 key derivation and AES-CBC-256 encryption.
 *
 * This provides:
 * - Elliptic curve Diffie-Hellman (ECDH) for shared secret generation
 * - SHA512 hash-based key derivation (legacy method)
 * - AES-CBC-256 with PKCS7 padding for encryption/decryption
 *
 * Key features:
 * - Compatible with legacy FC encryption format
 * - Uses 16-byte IV for AES-CBC
 * - Requires sum4 checksum for integrity verification
 *
 * Usage:
 * - AsyOneWay: Sender generates ephemeral key pair, encrypts with recipient's public key
 * - AsyTwoWay: Both parties use their respective key pairs for bidirectional encryption
 */
public class Ecc256K1AesCbc256 implements AsymmetricCipher {

    private static final Ecc256K1AesCbc256 INSTANCE = new Ecc256K1AesCbc256();

    public static Ecc256K1AesCbc256 getInstance() {
        return INSTANCE;
    }

    /**
     * Generate shared secret using ECDH between private and public keys.
     *
     * @param priKeyBytes 32-byte private key
     * @param pubKeyBytes 33-byte compressed public key
     * @return Shared secret bytes
     */
    @Override
    public byte[] getSharedSecret(byte[] priKeyBytes, byte[] pubKeyBytes) {
        return Ecc256K1.getSharedSecret(priKeyBytes, pubKeyBytes);
    }

    /**
     * Derive symmetric key from shared secret using SHA512.
     *
     * @param sharedSecret Shared secret from ECDH
     * @param nonce 16-byte nonce (IV) used in key derivation
     * @return 32-byte symmetric key for AES-CBC-256
     */
    @Override
    @NotNull
    public byte[] sharedSecretToSymkey(byte[] sharedSecret, byte[] nonce) {
        return Ecc256K1.sharedSecretToSymkey(sharedSecret, nonce);
    }

    /**
     * Convert asymmetric key pair to symmetric key using ECDH+SHA512.
     *
     * @param priKey 32-byte private key (own)
     * @param pubKey 33-byte public key (other party)
     * @param nonce 16-byte nonce for key derivation
     * @return 32-byte symmetric key for AES-CBC-256 encryption
     */
    @Override
    public byte[] asyKeyToSymkey(byte[] priKey, byte[] pubKey, byte[] nonce) {
        return Ecc256K1.asyKeyToSymkey(priKey, pubKey, nonce);
    }

    /**
     * Encrypt data using ECDH+SHA512+AES-CBC-256.
     *
     * @param plaintext Input plaintext data
     * @param priKey 32-byte private key (sender's ephemeral or own key)
     * @param pubKey 33-byte public key (recipient's public key)
     * @param nonce 16-byte nonce/IV
     * @return CryptoDataByte containing encrypted data and metadata
     */
    @Override
    public CryptoDataByte encrypt(byte[] plaintext, byte[] priKey, byte[] pubKey, byte[] nonce) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();

        // Derive symmetric key
        byte[] symkey = asyKeyToSymkey(priKey, pubKey, nonce);
        cryptoDataByte.setSymkey(symkey);
        cryptoDataByte.setIv(nonce);
        cryptoDataByte.setAlg(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);

        // Encrypt using AES-CBC-256
        ByteArrayInputStream bis = new ByteArrayInputStream(plaintext);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        AesCbc256.encrypt(bis, bos, symkey, nonce, cryptoDataByte);

        if (cryptoDataByte.getCode() == 0 || cryptoDataByte.getCode() == null) {
            cryptoDataByte.setCipher(bos.toByteArray());
            cryptoDataByte.setAlg(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
        }

        return cryptoDataByte;
    }

    /**
     * Decrypt data using ECDH+SHA512+AES-CBC-256.
     *
     * @param ciphertext Input ciphertext data
     * @param priKey 32-byte private key (recipient's own key)
     * @param pubKey 33-byte public key (sender's ephemeral or own key)
     * @param nonce 16-byte nonce/IV
     * @return CryptoDataByte containing decrypted data and metadata
     */
    @Override
    public CryptoDataByte decrypt(byte[] ciphertext, byte[] priKey, byte[] pubKey, byte[] nonce) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();

        // Derive symmetric key
        byte[] symkey = asyKeyToSymkey(priKey, pubKey, nonce);
        cryptoDataByte.setSymkey(symkey);
        cryptoDataByte.setIv(nonce);
        cryptoDataByte.setCipher(ciphertext);
        cryptoDataByte.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7);

        // Decrypt using AES-CBC-256
        AesCbc256.decrypt(cryptoDataByte);

        // Restore the asymmetric algorithm ID
        cryptoDataByte.setAlg(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);

        return cryptoDataByte;
    }

    /**
     * Encrypt stream using ECDH+SHA512+AES-CBC-256.
     *
     * @param inputStream Input stream containing plaintext
     * @param outputStream Output stream for ciphertext
     * @param priKey Private key
     * @param pubKey Public key
     * @param nonce Nonce/IV
     * @param cryptoDataByte CryptoDataByte to store metadata
     * @return Updated CryptoDataByte
     */
    public static CryptoDataByte encryptStream(java.io.InputStream inputStream, java.io.OutputStream outputStream,
                                               byte[] priKey, byte[] pubKey, byte[] nonce, CryptoDataByte cryptoDataByte) {
        if (cryptoDataByte == null) {
            cryptoDataByte = new CryptoDataByte();
        }

        // Derive symmetric key
        byte[] symkey = Ecc256K1.asyKeyToSymkey(priKey, pubKey, nonce);
        cryptoDataByte.setSymkey(symkey);
        cryptoDataByte.setIv(nonce);
        cryptoDataByte.setAlg(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);

        // Encrypt using AES-CBC-256
        AesCbc256.encrypt(inputStream, outputStream, symkey, nonce, cryptoDataByte);

        cryptoDataByte.setAlg(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);

        return cryptoDataByte;
    }

    /**
     * Decrypt stream using ECDH+SHA512+AES-CBC-256.
     *
     * @param inputStream Input stream containing ciphertext
     * @param outputStream Output stream for plaintext
     * @param priKey Private key
     * @param pubKey Public key
     * @param nonce Nonce/IV
     * @param cryptoDataByte CryptoDataByte to store metadata
     */
    public static void decryptStream(java.io.InputStream inputStream, java.io.OutputStream outputStream,
                                     byte[] priKey, byte[] pubKey, byte[] nonce, CryptoDataByte cryptoDataByte) {
        if (cryptoDataByte == null) {
            cryptoDataByte = new CryptoDataByte();
        }

        // Derive symmetric key
        byte[] symkey = Ecc256K1.asyKeyToSymkey(priKey, pubKey, nonce);
        cryptoDataByte.setSymkey(symkey);
        cryptoDataByte.setIv(nonce);

        // Decrypt using AES-CBC-256
        AesCbc256.decryptStream(inputStream, outputStream, cryptoDataByte);
    }
}
