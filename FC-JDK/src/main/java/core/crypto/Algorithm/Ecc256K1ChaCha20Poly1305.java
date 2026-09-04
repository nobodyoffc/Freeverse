package core.crypto.Algorithm;

import core.crypto.CryptoDataByte;
import data.fcData.AlgorithmId;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Ecc256K1ChaCha20Poly1305 combines secp256k1 ECDH with HKDF key derivation and
 * ChaCha20-Poly1305 AEAD encryption.
 *
 * This is the authenticated counterpart of {@link Ecc256K1ChaCha20}: the Poly1305
 * tag is produced and verified by the cipher itself, so no separate sum is carried.
 *
 * The HKDF info string differs from the raw ChaCha20 variant so that the two
 * algorithms derive distinct keys from the same ECDH shared secret (domain
 * separation).
 *
 * Usage:
 * - AsyOneWay: Sender generates ephemeral key pair, encrypts with recipient's public key
 * - AsyTwoWay: Both parties use their respective key pairs for bidirectional encryption
 */
public class Ecc256K1ChaCha20Poly1305 implements AsymmetricCipher {

    private static final Ecc256K1ChaCha20Poly1305 INSTANCE = new Ecc256K1ChaCha20Poly1305();

    public static Ecc256K1ChaCha20Poly1305 getInstance() {
        return INSTANCE;
    }

    public static final String INFO = "hkdf-chacha20poly1305";

    @Override
    public byte[] getSharedSecret(byte[] priKeyBytes, byte[] pubKeyBytes) {
        return Ecc256K1Hkdf.getSharedSecret(priKeyBytes, pubKeyBytes);
    }

    @Override
    @NotNull
    public byte[] sharedSecretToSymkey(byte[] sharedSecret, byte[] nonce) throws Exception {
        return HKDF.hkdf(sharedSecret, nonce, INFO.getBytes(), 32);
    }

    @Override
    public byte[] asyKeyToSymkey(byte[] priKey, byte[] pubKey, byte[] nonce) throws Exception {
        byte[] sharedSecret = getSharedSecret(priKey, pubKey);
        byte[] symkey = sharedSecretToSymkey(sharedSecret, nonce);
        Arrays.fill(sharedSecret, (byte) 0);
        return symkey;
    }

    @Override
    public CryptoDataByte encrypt(byte[] plaintext, byte[] priKey, byte[] pubKey, byte[] nonce) throws Exception {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();

        byte[] symkey = asyKeyToSymkey(priKey, pubKey, nonce);
        cryptoDataByte.setSymkey(symkey);
        cryptoDataByte.setIv(nonce);
        cryptoDataByte.setAlg(AlgorithmId.FC_ChaCha20Poly1305_No1_NrC7);

        ByteArrayInputStream bis = new ByteArrayInputStream(plaintext);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        ChaCha20Poly1305.encrypt(bis, bos, symkey, nonce, cryptoDataByte);

        if (cryptoDataByte.getCode() == null || cryptoDataByte.getCode() == 0) {
            cryptoDataByte.setCipher(bos.toByteArray());
        }
        cryptoDataByte.setAlg(AlgorithmId.FC_EccK1ChaCha20Poly1305_No1_NrC7);

        return cryptoDataByte;
    }

    @Override
    public CryptoDataByte decrypt(byte[] ciphertext, byte[] priKey, byte[] pubKey, byte[] nonce) throws Exception {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();

        byte[] symkey = asyKeyToSymkey(priKey, pubKey, nonce);
        cryptoDataByte.setSymkey(symkey);
        cryptoDataByte.setIv(nonce);
        cryptoDataByte.setCipher(ciphertext);
        cryptoDataByte.setAlg(AlgorithmId.FC_ChaCha20Poly1305_No1_NrC7);

        ChaCha20Poly1305.decrypt(cryptoDataByte);

        // Restore the asymmetric algorithm ID
        cryptoDataByte.setAlg(AlgorithmId.FC_EccK1ChaCha20Poly1305_No1_NrC7);

        return cryptoDataByte;
    }
}
