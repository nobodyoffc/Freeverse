package crypto;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import javaTools.Hex;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.junit.jupiter.api.Test;
import javaTools.BytesTools;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

import static java.lang.Integer.rotateLeft;

public class Hash {

    private Hash() {
    }

    public static String sha256(File file) throws IOException {
        return sha256HashCode(file).toString();
    }
    public static byte[] sha256Bytes(File file) throws IOException {
        return sha256HashCode(file).asBytes();
    }

    public static String sha256x2(File file) throws IOException {
        byte[] hashBytes = sha256x2Bytes(file);
        return Hex.toHex(hashBytes);
    }

    public static byte[] sha256x2Bytes(File file) throws IOException {
        return sha256(sha256HashCode(file).asBytes());
    }
    public static HashCode sha256HashCode(File file) throws IOException {
        HashFunction hashFunction = Hashing.sha256();
        try (FileInputStream fis = new FileInputStream(file)) {
            Hasher hasher = hashFunction.newHasher();
            byte[] buffer = new byte[8192]; // 8KB buffer size
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                hasher.putBytes(buffer, 0, bytesRead);
            }
            return hasher.hash();
        }
    }

    public static byte[] sha256x2(byte[] b) {
        return Hashing.sha256().hashBytes(Hashing.sha256().hashBytes(b).asBytes()).asBytes();
    }

    public static byte[] sha512x2(byte[] b) {
        return Hashing.sha512().hashBytes(Hashing.sha512().hashBytes(b).asBytes()).asBytes();
    }
    public static byte[] sha512(byte[] b) {
        return Hashing.sha512().hashBytes(b).asBytes();
    }

    public static String sha256(String s) {
        return Hashing.sha256().hashBytes(s.getBytes()).toString();
    }

    public static String sha256x2(String s) {
        return Hashing.sha256().hashBytes(Hashing.sha256().hashBytes(s.getBytes()).asBytes()).toString();
    }

    public static String sha512x2(String s) {
        return Hashing.sha512().hashBytes(Hashing.sha512().hashBytes(s.getBytes()).asBytes()).toString();
    }

    public static byte[] getSign(byte[] text, byte[] symKey) {
        byte[] bytes = BytesTools.bytesMerger(text, symKey);
        return Hash.sha256x2(bytes);
    }

    public static String getSign(String text, byte[] symKey) {
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        byte[] signBytes = getSign(textBytes, symKey);
        return BytesTools.bytesToHexStringBE(signBytes);
    }

    public static String getSign(String symKey, String text) {
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = BytesTools.hexToByteArray(symKey);
        byte[] bytes = BytesTools.bytesMerger(textBytes, keyBytes);
        System.out.println("----");
        System.out.println("Content in hex to be signed: ");
        System.out.println("----");
        System.out.println(HexFormat.of().formatHex(bytes));
//        System.out.println("------");
        byte[] signBytes = Hash.sha256x2(bytes);
        return BytesTools.bytesToHexStringBE(signBytes);
    }

    public static byte[] Ripemd160(byte[] b) {
        return Ripemd160.getHash(b);
    }

    /**
     * Keccak-256 hash function.
     *
     * @param hexInput hex encoded input data with optional 0x prefix
     * @return hash value as hex encoded string
     */
    public static String sha3(String hexInput) {
        byte[] bytes = Hex.fromHex(hexInput);
        byte[] result = sha3(bytes);
        return Hex.toHex(result);
    }

    /**
     * Keccak-256 hash function.
     *
     * @param input  binary encoded input data
     * @param offset of start of data
     * @param length of data
     * @return hash value
     */
    public static byte[] sha3(byte[] input, int offset, int length) {
        Keccak.DigestKeccak kecc = new Keccak.Digest256();
        kecc.update(input, offset, length);
        return kecc.digest();
    }

    /**
     * Keccak-256 hash function.
     *
     * @param input binary encoded input data
     * @return hash value
     */
    public static byte[] sha3(byte[] input) {
        return sha3(input, 0, input.length);
    }

    /**
     * Keccak-256 hash function that operates on a UTF-8 encoded String.
     *
     * @param utf8String UTF-8 encoded string
     * @return hash value as hex encoded string
     */
    public static String sha3String(String utf8String) {
        return Hex.toHex(sha3(utf8String.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Generates SHA-256 digest for the given {@code input}.
     *
     * @param input The input to digest
     * @return The hash value for the given input
     * @throws RuntimeException If we couldn't find any SHA-256 provider
     */
    public static byte[] sha256(byte[] input) {
        return Hashing.sha256().hashBytes(input).asBytes();
    }

    public static byte[] hmacSha512(byte[] key, byte[] input) {
        HMac hMac = new HMac(new SHA512Digest());
        hMac.init(new KeyParameter(key));
        hMac.update(input, 0, input.length);
        byte[] out = new byte[64];
        hMac.doFinal(out, 0);
        return out;
    }

    public static byte[] sha256hash160(byte[] input) {
        byte[] sha256 = sha256(input);
        RIPEMD160Digest digest = new RIPEMD160Digest();
        digest.update(sha256, 0, sha256.length);
        byte[] out = new byte[20];
        digest.doFinal(out, 0);
        return out;
    }

    @Test
    public void test() {
        String hash = sha256x2(" ");
        System.out.println(hash);
    }

    /**
     * Computes the RIPEMD-160 hash of an array of bytes. Not instantiable.
     */
    public static final class Ripemd160 {

        private static final int BLOCK_LEN = 64;  // In bytes

        private static final int[] KL = {0x00000000, 0x5A827999, 0x6ED9EBA1, 0x8F1BBCDC, 0xA953FD4E};  // Round constants for left line
        private static final int[] KR = {0x50A28BE6, 0x5C4DD124, 0x6D703EF3, 0x7A6D76E9, 0x00000000};  // Round constants for right line
        private static final int[] RL = {  // Message schedule for left line
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                7, 4, 13, 1, 10, 6, 15, 3, 12, 0, 9, 5, 2, 14, 11, 8,
                3, 10, 14, 4, 9, 15, 8, 1, 2, 7, 0, 6, 13, 11, 5, 12,
                1, 9, 11, 10, 0, 8, 12, 4, 13, 3, 7, 15, 14, 5, 6, 2,
                4, 0, 5, 9, 7, 12, 2, 10, 14, 1, 3, 8, 11, 6, 15, 13};
        private static final int[] RR = {  // Message schedule for right line
                5, 14, 7, 0, 9, 2, 11, 4, 13, 6, 15, 8, 1, 10, 3, 12,
                6, 11, 3, 7, 0, 13, 5, 10, 14, 15, 8, 12, 4, 9, 1, 2,
                15, 5, 1, 3, 7, 14, 6, 9, 11, 8, 12, 2, 10, 0, 4, 13,
                8, 6, 4, 1, 3, 11, 15, 0, 5, 12, 2, 13, 9, 7, 10, 14,
                12, 15, 10, 4, 1, 5, 8, 7, 6, 2, 13, 14, 0, 3, 9, 11};
        private static final int[] SL = {  // Left-rotation for left line
                11, 14, 15, 12, 5, 8, 7, 9, 11, 13, 14, 15, 6, 7, 9, 8,
                7, 6, 8, 13, 11, 9, 7, 15, 7, 12, 15, 9, 11, 7, 13, 12,
                11, 13, 6, 7, 14, 9, 13, 15, 14, 8, 13, 6, 5, 12, 7, 5,
                11, 12, 14, 15, 14, 15, 9, 8, 9, 14, 5, 6, 8, 6, 5, 12,
                9, 15, 5, 11, 6, 8, 13, 12, 5, 12, 13, 14, 11, 8, 5, 6};
        private static final int[] SR = {  // Left-rotation for right line
                8, 9, 9, 11, 13, 15, 15, 5, 7, 7, 8, 11, 14, 14, 12, 6,
                9, 13, 15, 7, 12, 8, 9, 11, 7, 7, 12, 7, 6, 15, 13, 11,
                9, 7, 15, 11, 8, 6, 6, 14, 12, 13, 5, 14, 13, 13, 7, 5,
                15, 5, 8, 11, 14, 14, 6, 14, 6, 9, 12, 9, 12, 5, 15, 8,
                8, 5, 12, 9, 12, 5, 14, 6, 8, 13, 6, 5, 15, 13, 11, 11};



        /*---- Static functions ----*/

        /**
         * Computes and returns a 20-byte (160-bit) hash of the specified binary message.
         * Each call will return a new byte array object instance.
         *
         * @param msg the message to compute the hash of
         * @return a 20-byte array representing the message's RIPEMD-160 hash
         * @throws NullPointerException if the message is {@code null}
         */
        public static byte[] getHash(byte[] msg) {
            // Compress whole message blocks
            Objects.requireNonNull(msg);
            int[] state = {0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476, 0xC3D2E1F0};
            int off = msg.length / BLOCK_LEN * BLOCK_LEN;
            compress(state, msg, off);

            // Final blocks, padding, and length
            byte[] block = new byte[BLOCK_LEN];
            System.arraycopy(msg, off, block, 0, msg.length - off);
            off = msg.length % block.length;
            block[off] = (byte) 0x80;
            off++;
            if (off + 8 > block.length) {
                compress(state, block, block.length);
                Arrays.fill(block, (byte) 0);
            }
            long len = (long) msg.length << 3;
            for (int i = 0; i < 8; i++)
                block[block.length - 8 + i] = (byte) (len >>> (i * 8));
            compress(state, block, block.length);

            // Int32 array to bytes in little endian
            byte[] result = new byte[state.length * 4];
            for (int i = 0; i < result.length; i++)
                result[i] = (byte) (state[i / 4] >>> (i % 4 * 8));
            return result;
        }



        /*---- Private functions ----*/

        private static void compress(int[] state, byte[] blocks, int len) {
            if (len % BLOCK_LEN != 0)
                throw new IllegalArgumentException();
            for (int i = 0; i < len; i += BLOCK_LEN) {

                // Message schedule
                int[] schedule = new int[16];
                for (int j = 0; j < BLOCK_LEN; j++)
                    schedule[j / 4] |= (blocks[i + j] & 0xFF) << (j % 4 * 8);

                // The 80 rounds
                int al = state[0], ar = state[0];
                int bl = state[1], br = state[1];
                int cl = state[2], cr = state[2];
                int dl = state[3], dr = state[3];
                int el = state[4], er = state[4];
                for (int j = 0; j < 80; j++) {
                    int temp;
                    temp = rotateLeft(al + f(j, bl, cl, dl) + schedule[RL[j]] + KL[j / 16], SL[j]) + el;
                    al = el;
                    el = dl;
                    dl = rotateLeft(cl, 10);
                    cl = bl;
                    bl = temp;
                    temp = rotateLeft(ar + f(79 - j, br, cr, dr) + schedule[RR[j]] + KR[j / 16], SR[j]) + er;
                    ar = er;
                    er = dr;
                    dr = rotateLeft(cr, 10);
                    cr = br;
                    br = temp;
                }
                int temp = state[1] + cl + dr;
                state[1] = state[2] + dl + er;
                state[2] = state[3] + el + ar;
                state[3] = state[4] + al + br;
                state[4] = state[0] + bl + cr;
                state[0] = temp;
            }
        }


        private static int f(int i, int x, int y, int z) {
            assert 0 <= i && i < 80;
            if (i < 16) return x ^ y ^ z;
            if (i < 32) return (x & y) | (~x & z);
            if (i < 48) return (x | ~y) ^ z;
            if (i < 64) return (x & z) | (y & ~z);
            return x ^ (y | ~z);
        }

    }
}





