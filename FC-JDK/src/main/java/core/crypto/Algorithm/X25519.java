package core.crypto.Algorithm;

import core.crypto.Encryptor;
import org.bouncycastle.crypto.agreement.X25519Agreement;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class X25519 {

    public static final int PRIVATE_KEY_SIZE = 32;
    public static final int PUBLIC_KEY_SIZE = 32;
    public static final int SHARED_SECRET_SIZE = 32;
    public static final String INFO = "hkdf";

    /**
     * Generate a shared secret using X25519 key agreement
     * @param priKeyBytes 32-byte X25519 private key
     * @param pubKeyBytes 32-byte X25519 public key
     * @return 32-byte shared secret
     */
    public static byte[] getSharedSecret(byte[] priKeyBytes, byte[] pubKeyBytes) {
        if (priKeyBytes == null || priKeyBytes.length != PRIVATE_KEY_SIZE) {
            throw new IllegalArgumentException("Private key must be " + PRIVATE_KEY_SIZE + " bytes");
        }
        if (pubKeyBytes == null || pubKeyBytes.length != PUBLIC_KEY_SIZE) {
            throw new IllegalArgumentException("Public key must be " + PUBLIC_KEY_SIZE + " bytes");
        }

        X25519PrivateKeyParameters priKey = new X25519PrivateKeyParameters(priKeyBytes, 0);
        X25519PublicKeyParameters pubKey = new X25519PublicKeyParameters(pubKeyBytes, 0);

        X25519Agreement agreement = new X25519Agreement();
        agreement.init(priKey);

        byte[] sharedSecret = new byte[SHARED_SECRET_SIZE];
        agreement.calculateAgreement(pubKey, sharedSecret, 0);

        return sharedSecret;
    }

    /**
     * Convert shared secret to symmetric key using HKDF-SHA512
     * @param sharedSecret The X25519 shared secret
     * @param nonce The initialization vector/nonce (used as HKDF salt)
     * @return 32-byte symmetric key
     */
    @NotNull
    public static byte[] sharedSecretToSymkey(byte[] sharedSecret, byte[] nonce) throws Exception {
        return HKDF.hkdf(sharedSecret, nonce, INFO.getBytes(), 32);
    }

    /**
     * Generate symmetric key from asymmetric keys
     * @param priKey X25519 private key
     * @param pubKey X25519 public key
     * @param iv Initialization vector
     * @return 32-byte symmetric key
     */
    public static byte[] asyKeyToSymkey(byte[] priKey, byte[] pubKey, byte[] iv) throws Exception {
        byte[] sharedSecret = getSharedSecret(priKey, pubKey);

        byte[] symkey = sharedSecretToSymkey(sharedSecret, iv);

        Arrays.fill(sharedSecret, (byte) 0);
        return symkey;
    }

    /**
     * Generate X25519 public key from private key
     * @param priKeyBytes 32-byte private key
     * @return 32-byte public key
     */
    public static byte[] generatePublicKey(byte[] priKeyBytes) {
        if (priKeyBytes == null || priKeyBytes.length != PRIVATE_KEY_SIZE) {
            throw new IllegalArgumentException("Private key must be " + PRIVATE_KEY_SIZE + " bytes");
        }

        X25519PrivateKeyParameters priKey = new X25519PrivateKeyParameters(priKeyBytes, 0);
        byte[] pubKey = new byte[PUBLIC_KEY_SIZE];
        priKey.generatePublicKey().encode(pubKey, 0);

        return pubKey;
    }
}
