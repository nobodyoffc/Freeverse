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

    // GCM tag size (bits, as required by GCMParameterSpec)
    public static final int GCM_TAG_LENGTH_BITS = 128;
}
