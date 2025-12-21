package core.crypto.Algorithm;

import core.crypto.CryptoDataByte;

/**
 * Interface for asymmetric cipher algorithms that combine:
 * - Key exchange (ECDH with secp256k1 or X25519)
 * - Key derivation (SHA512 or HKDF)
 * - Symmetric encryption (AES-CBC, AES-GCM, or ChaCha20)
 *
 * Implementations should support:
 * - AsyOneWay: Sender generates ephemeral key pair, encrypts with recipient's public key
 * - AsyTwoWay: Both parties use their respective key pairs for bidirectional encryption
 */
public interface AsymmetricCipher {

    /**
     * Generate shared secret using key exchange algorithm.
     *
     * @param priKeyBytes Private key bytes
     * @param pubKeyBytes Public key bytes
     * @return Shared secret bytes
     */
    byte[] getSharedSecret(byte[] priKeyBytes, byte[] pubKeyBytes);

    /**
     * Derive symmetric key from shared secret.
     *
     * @param sharedSecret Shared secret from key exchange
     * @param nonce Nonce/IV used in key derivation
     * @return 32-byte symmetric key
     * @throws Exception if key derivation fails
     */
    byte[] sharedSecretToSymkey(byte[] sharedSecret, byte[] nonce) throws Exception;

    /**
     * Convert asymmetric key pair to symmetric key.
     *
     * @param priKey Private key (own)
     * @param pubKey Public key (other party)
     * @param nonce Nonce for key derivation
     * @return 32-byte symmetric key
     * @throws Exception if key derivation fails
     */
    byte[] asyKeyToSymkey(byte[] priKey, byte[] pubKey, byte[] nonce) throws Exception;

    /**
     * Encrypt data using asymmetric encryption.
     *
     * @param plaintext Input plaintext data
     * @param priKey Private key (sender's ephemeral or own key)
     * @param pubKey Public key (recipient's public key)
     * @param nonce Nonce/IV
     * @return CryptoDataByte containing encrypted data and metadata
     * @throws Exception if encryption fails
     */
    CryptoDataByte encrypt(byte[] plaintext, byte[] priKey, byte[] pubKey, byte[] nonce) throws Exception;

    /**
     * Decrypt data using asymmetric decryption.
     *
     * @param ciphertext Input ciphertext data
     * @param priKey Private key (recipient's own key)
     * @param pubKey Public key (sender's ephemeral or own key)
     * @param nonce Nonce/IV
     * @return CryptoDataByte containing decrypted data and metadata
     * @throws Exception if decryption fails
     */
    CryptoDataByte decrypt(byte[] ciphertext, byte[] priKey, byte[] pubKey, byte[] nonce) throws Exception;
}
