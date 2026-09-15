package core.crypto;

/**
 * Cryptographic constants used across the crypto package.
 */
public final class CryptoConstants {

    private CryptoConstants() {}

    // Key sizes (bytes)
    public static final int KEY_LENGTH_256 = 32;
    public static final int EXTENDED_KEY_LENGTH = 64;

    // IV/nonce sizes (bytes)
    public static final int IV_LENGTH_CBC = 16;
    public static final int IV_LENGTH_GCM = 12;
    public static final int IV_LENGTH_CHACHA20 = 12;

    // HMAC / authentication tag sizes (bytes)
    public static final int SUM_LENGTH = 4;
    public static final int HMAC_SHA256_LENGTH = 32;

    // Bundle format sizes (bytes)
    public static final int ALG_BYTES_LENGTH = 6;
    public static final int KEY_NAME_LENGTH = 6;
    public static final int PUBKEY_COMPRESSED_LENGTH = 33;
    public static final int PUBKEY_X25519_LENGTH = 32;
    public static final int KDF_ID_LENGTH = 1;

    // Bundle type byte for EncryptType.Password with a recorded KDF id (FTSP30).
    // Type byte 3 remains the legacy Password layout, which records no KDF.
    public static final byte BUNDLE_TYPE_PASSWORD_WITH_KDF = 4;

    // GCM tag size (bits, as required by GCMParameterSpec)
    public static final int GCM_TAG_LENGTH_BITS = 128;
}
