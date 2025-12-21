package core.crypto.Algorithm;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

public final class HKDF {

    private static final String HMAC_ALGO = "HmacSHA512";
    private static final int HASH_LEN = 64; // SHA-512 output bytes

    private HKDF() {}

    /**
     * HKDF-Extract(salt, IKM) -> PRK
     */
    public static byte[] extract(final byte[] salt, final byte[] ikm) throws Exception {
        byte[] effectiveSalt = salt;
        if (effectiveSalt == null || effectiveSalt.length == 0) {
            effectiveSalt = new byte[HASH_LEN]; // all zeros per RFC5869
        }
        Mac mac = Mac.getInstance(HMAC_ALGO);
        mac.init(new SecretKeySpec(effectiveSalt, HMAC_ALGO));
        return mac.doFinal(ikm);
    }

    /**
     * HKDF-Expand(PRK, info, L) -> OKM (length L bytes)
     */
    public static byte[] expand(final byte[] prk, final byte[] info, final int length) throws Exception {
        if (length <= 0 || length > 255 * HASH_LEN) {
            throw new IllegalArgumentException("length must be between 1 and " + (255 * HASH_LEN));
        }
        int n = (int) Math.ceil((double) length / HASH_LEN);
        byte[] okm = new byte[length];
        Mac mac = Mac.getInstance(HMAC_ALGO);
        mac.init(new SecretKeySpec(prk, HMAC_ALGO));

        byte[] previousT = new byte[0];
        int copied = 0;
        for (int i = 1; i <= n; i++) {
            mac.reset();
            mac.update(previousT);
            if (info != null) mac.update(info);
            mac.update((byte) i);
            byte[] t = mac.doFinal();
            int toCopy = Math.min(t.length, length - copied);
            System.arraycopy(t, 0, okm, copied, toCopy);
            copied += toCopy;
            previousT = t;
        }
        return okm;
    }

    /**
     * Convenience: full HKDF (Extract then Expand)
     * salt may be null/empty, info may be null
     */
    public static byte[] hkdf(final byte[] ikm, final byte[] salt, final byte[] info, final int length) throws Exception {
        byte[] prk = extract(salt, ikm);
        return expand(prk, info, length);
    }

    // ------------------------
    // 示例用法（main）
    // ------------------------
    public static void main(String[] args) throws Exception {
        SecureRandom rnd = new SecureRandom();

        // 假设这是通过 ECDH 得到的 shared secret（真实场景由 ECDH 生成）
        byte[] sharedSecret = new byte[32];
        rnd.nextBytes(sharedSecret);

        // 作为 salt 的随机 iv（例如 12 字节的 AES-GCM IV）
        byte[] iv = new byte[12];
        rnd.nextBytes(iv);

        // info 字段用于上下文区分
        byte[] info = "aes key".getBytes(StandardCharsets.UTF_8);

        // 派生 32 字节（256-bit）对称密钥
        byte[] derivedKey = HKDF.hkdf(sharedSecret, iv, info, 32);

        System.out.println("derivedKey (hex): " + bytesToHex(derivedKey));

        // 把派生的 key 封装为 SecretKey 用于 AES
        SecretKeySpec aesKey = new SecretKeySpec(derivedKey, "AES");

        // 简短示例：使用 AES/GCM/NoPadding 加密一个短消息
        byte[] plaintext = "hello hkdf".getBytes(StandardCharsets.UTF_8);
        byte[] gcmIv = new byte[12];
        rnd.nextBytes(gcmIv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, gcmIv);
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec);
        byte[] ciphertext = cipher.doFinal(plaintext);
        System.out.println("ciphertext length: " + ciphertext.length);
    }

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}