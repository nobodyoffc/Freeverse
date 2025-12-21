package fudp.util;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Utility class for byte array operations
 */
public class ByteUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Concatenate multiple byte arrays
     */
    public static byte[] concat(byte[]... arrays) {
        int totalLength = 0;
        for (byte[] array : arrays) {
            if (array != null) {
                totalLength += array.length;
            }
        }

        byte[] result = new byte[totalLength];
        int offset = 0;
        for (byte[] array : arrays) {
            if (array != null) {
                System.arraycopy(array, 0, result, offset, array.length);
                offset += array.length;
            }
        }
        return result;
    }

    /**
     * Read a long from a byte array (big-endian)
     */
    public static long readLong(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 8).getLong();
    }

    /**
     * Write a long to a byte array (big-endian)
     */
    public static void writeLong(byte[] data, int offset, long value) {
        ByteBuffer.wrap(data, offset, 8).putLong(value);
    }

    /**
     * Read an int from a byte array (big-endian)
     */
    public static int readInt(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4).getInt();
    }

    /**
     * Write an int to a byte array (big-endian)
     */
    public static void writeInt(byte[] data, int offset, int value) {
        ByteBuffer.wrap(data, offset, 4).putInt(value);
    }

    /**
     * Read a short from a byte array (big-endian)
     */
    public static short readShort(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 2).getShort();
    }

    /**
     * Write a short to a byte array (big-endian)
     */
    public static void writeShort(byte[] data, int offset, short value) {
        ByteBuffer.wrap(data, offset, 2).putShort(value);
    }

    /**
     * Convert a long to bytes (big-endian)
     */
    public static byte[] longToBytes(long value) {
        return ByteBuffer.allocate(8).putLong(value).array();
    }

    /**
     * Convert an int to bytes (big-endian)
     */
    public static byte[] intToBytes(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    /**
     * Generate random bytes
     */
    public static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    /**
     * Generate a random long
     */
    public static long randomLong() {
        return SECURE_RANDOM.nextLong();
    }

    /**
     * Copy a portion of a byte array
     */
    public static byte[] copy(byte[] data, int offset, int length) {
        byte[] result = new byte[length];
        System.arraycopy(data, offset, result, 0, length);
        return result;
    }

    /**
     * Check if two byte arrays are equal
     */
    public static boolean equals(byte[] a, byte[] b) {
        return Arrays.equals(a, b);
    }

    /**
     * Convert bytes to hex string
     */
    public static String toHex(byte[] bytes) {
        if (bytes == null) return null;
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Convert hex string to bytes
     */
    public static byte[] fromHex(String hex) {
        if (hex == null) return null;
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Clear sensitive data from a byte array
     */
    public static void clear(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }

    /**
     * XOR two byte arrays
     */
    public static byte[] xor(byte[] a, byte[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Arrays must have the same length");
        }
        byte[] result = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = (byte) (a[i] ^ b[i]);
        }
        return result;
    }
}
