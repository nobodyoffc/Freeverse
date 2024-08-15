package javaTools;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Pattern;


public class BytesTools {

    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    private static final Pattern BASE64_PATTERN = Pattern.compile("^[A-Za-z0-9+/]+={0,2}$");

    /**
     * Creates a copy of bytes and appends b to the end of it
     */
    public static byte[] appendByte(byte[] bytes, byte b) {
        byte[] result = Arrays.copyOf(bytes, bytes.length + 1);
        result[result.length - 1] = b;
        return result;
    }

    public static char[] byteArrayToCharArray(byte[] bytes, Charset charset) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        CharBuffer charBuffer = charset.decode(byteBuffer);
        char[] chars = Arrays.copyOfRange(charBuffer.array(),
                charBuffer.position(), charBuffer.limit());
        Arrays.fill(byteBuffer.array(), (byte) 0); // Clear sensitive data
        return chars;
    }

    public static byte[] charArrayToByteArray(char[] chars, Charset charset) {
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = charset.encode(charBuffer);
        return Arrays.copyOfRange(byteBuffer.array(),
                byteBuffer.position(), byteBuffer.limit());
    }

    public static char[] byteArrayToUtf8CharArray(byte[] bytes) {
        Charset charset = StandardCharsets.UTF_8;
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        CharBuffer charBuffer = charset.decode(byteBuffer);
        return Arrays.copyOfRange(charBuffer.array(),
                charBuffer.position(), charBuffer.limit());
    }

    public static byte[] utf8CharArrayToByteArray(char[] chars) {
        Charset charset = StandardCharsets.UTF_8;
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = charset.encode(charBuffer);
        return Arrays.copyOfRange(byteBuffer.array(),
                byteBuffer.position(), byteBuffer.limit());
    }

    public static byte[] hexCharArrayToByteArray(char[] hex) {
        int length = hex.length;
        byte[] byteArray = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            int high = Character.digit(hex[i], 16) << 4;
            int low = Character.digit(hex[i + 1], 16);
            byteArray[i / 2] = (byte) (high | low);
        }
        return byteArray;
    }

    public static boolean isBase64Encoded(String s) {
        if (s == null) return false;
        // Check length
        if (s.length() % 4 != 0) return false;
        // Check valid Base64 characters
        return BASE64_PATTERN.matcher(s).matches();
    }

    public static char[] byteArrayToHexCharArray(byte[] byteArray) {
        char[] hexChars = new char[byteArray.length * 2];
        for (int i = 0; i < byteArray.length; i++) {
            int v = byteArray[i] & 0xFF;
            hexChars[i * 2] = Character.forDigit((v >>> 4) & 0x0F, 16);
            hexChars[i * 2 + 1] = Character.forDigit(v & 0x0F, 16);
        }
        return hexChars;
    }

    public static char[] byteArrayToBase64CharArray(byte[] byteArray) {
        byte[] base64Bytes = Base64.getEncoder().encode(byteArray);
        char[] base64Chars = new char[base64Bytes.length];
        for (int i = 0; i < base64Bytes.length; i++) {
            base64Chars[i] = (char) (base64Bytes[i] & 0xFF);
        }
        return base64Chars;
    }

    public static byte[] base64CharArrayToByteArray(char[] base64Chars) {
        byte[] base64Bytes = new byte[base64Chars.length];
        for (int i = 0; i < base64Chars.length; i++) {
            base64Bytes[i] = (byte) base64Chars[i];
        }
        return Base64.getDecoder().decode(base64Bytes);
    }

    /**
     * The regular {@link BigInteger#toByteArray()} method isn't quite what we often need:
     * it appends a leading zero to indicate that the number is positive and may need padding.
     *
     * @param b        the integer to format into a byte array
     * @param numBytes the desired size of the resulting byte array
     * @return numBytes byte long array.
     */
    public static byte[] bigIntegerToBytes(BigInteger b, int numBytes) {
        if (b == null)
            return null;
        byte[] bytes = new byte[numBytes];
        byte[] biBytes = b.toByteArray();
        int start = (biBytes.length == numBytes + 1) ? 1 : 0;
        int length = Math.min(biBytes.length, numBytes);
        System.arraycopy(biBytes, start, bytes, numBytes - length, length);
        return bytes;
    }

    /**
     * "a2acdb" --> "dbaca2"
     *
     * @param rawStr
     * @return
     */
    public static String revertHexBy2(String rawStr) {
        rawStr = rawStr.replaceAll(" ", "");
        String newStr = "";
        for (int i = 0; i < (int) rawStr.length() / 2; i = i + 1) {
            int lenth = rawStr.length();
            newStr = newStr + rawStr.substring((lenth - i * 2 - 2), (lenth - i * 2));
        }
        return newStr;
    }

    public static byte[] bigIntegerToBytes(BigInteger value) {
        if (value == null)
            return null;

        byte[] data = value.toByteArray();

        if (data.length != 1 && data[0] == 0) {
            byte[] tmp = new byte[data.length - 1];
            System.arraycopy(data, 1, tmp, 0, tmp.length);
            data = tmp;
        }
        return data;
    }

    //byte数组转char数组
    public static char[] bytesToChars(byte[] b) {
        char[] c = new char[b.length];
        for (byte i : b) {
            c[i] = (char) b[i];
        }
        return c;
    }

    //byte 与 int 的相互转换
    public static byte intToByte(int x) {
        return (byte) x;
    }

    public static int byteToUnsignedInt(byte b) {
        return b & 0xFF;
    }

    //byte 数组与 int 的相互转换
    public static int bytesToIntLE(byte[] a) {
        byte[] b = BytesTools.invertArray(a);
        return b[3] & 0xFF |
                (b[2] & 0xFF) << 8 |
                (b[1] & 0xFF) << 16 |
                (b[0] & 0xFF) << 24;
    }

    /**
     * Cast hex encoded value from byte[] to int
     * <p>
     * Limited to Integer.MAX_VALUE: 2^32-1 (4 bytes)
     *
     * @param b array contains the values
     * @return unsigned positive int value.
     */
    public static int bytesToIntBE(byte[] b) {
        if (b == null || b.length == 0)
            return 0;
        return new BigInteger(1, b).intValue();
    }

    public static byte[] intToByteArray(int a) {
        return new byte[]{
                (byte) ((a >> 24) & 0xFF),
                (byte) ((a >> 16) & 0xFF),
                (byte) ((a >> 8) & 0xFF),
                (byte) (a & 0xFF)
        };
    }

    //byte 数组与 long 的相互转换

    public static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(0, x);
        return buffer.array();
    }

    public static long bytes8ToLong(byte[] input, boolean littleEndian) {
        long value = 0;
        for (int count = 0; count < 8; ++count) {
            int shift = (littleEndian ? count : (7 - count)) << 3;
            value |= ((long) 0xff << shift) & ((long) input[count] << shift);
        }
        return value;
    }

    public static long bytes4ToLongLE(byte[] bytes) {

        return ByteBuffer.wrap(bytes)
                .order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
    }

    public static long bytes4ToLongBE(byte[] bytes) {
        return ByteBuffer.wrap(invertArray(bytes))
                .order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
    }

    public static String bytesToHexStringLE(byte[] data) {
        byte[] bytes = BytesTools.invertArray(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }


    public static String bytesToHexStringBE(byte[] b) {
        return HexFormat.of().formatHex(b);
    }


    /**
     * hex字符串转byte数组
     *
     * @param inHex 待转换的Hex字符串
     * @return 转换后的byte数组结果
     */
    public static byte[] hexToByteArray(String inHex) {
        int hexlen = inHex.length();
        boolean isOdd = hexlen % 2 == 1;

        if (isOdd) {
            hexlen++;
            inHex = "0" + inHex;
        }

        byte[] result = new byte[hexlen / 2];

        for (int i = 0, j = 0; i < hexlen; i += 2, j++) {
            result[j] = (byte) Integer.parseInt(inHex.substring(i, i + 2), 16);
        }

        return result;
    }


    public static Date bytesToDate(byte[] b) {
        int i = b[3] & 0xFF |
                (b[2] & 0xFF) << 8 |
                (b[1] & 0xFF) << 16 |
                (b[0] & 0xFF) << 24;
        long l = i * 1000;
        Date t = new Date(l);
        return t;
    }

    public static byte[] invertArray(byte[] a) {
        byte[] b = new byte[a.length];
        for (int i = 0; i < b.length; i++) {
            b[i] = a[b.length - i - 1];
        }
        return b;
    }

    public static String bytesToBinaryString(byte[] b) {
        String s = "";
        for (int i = 0; i < b.length; i++) {
            s = s + Integer.toBinaryString(b[i]);
        }
        return s;
    }

    public static byte[] bytesMerger(byte[] bt1, byte[] bt2) {
        byte[] bt3 = new byte[bt1.length + bt2.length];
        System.arraycopy(bt1, 0, bt3, 0, bt1.length);
        System.arraycopy(bt2, 0, bt3, bt1.length, bt2.length);
        return bt3;
    }

    public static byte[] bytesMerger(ArrayList<byte[]> bytesList) {
        Iterator<byte[]> iter = bytesList.iterator();
        int len = 0;
        while (iter.hasNext())
            len += iter.next().length;

        byte[] all = new byte[len];
        int decPos = 0;
        Iterator<byte[]> iter1 = bytesList.iterator();
        while (iter1.hasNext()) {
            byte[] src = iter1.next();
            System.arraycopy(src, 0, all, decPos, src.length);
            decPos += src.length;
        }
        return all;
    }

    public static byte[] getRandomBytes(int len) {
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[len];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    public static void clearByteArray(byte[] array) {
        if (array != null) {
            Arrays.fill(array, (byte) 0);
            array = null;
        }
    }

    public static void clearCharArray(char[] array) {
        if (array != null) {
            Arrays.fill(array, (char) 0);
        }
    }

    public static boolean isBase64Encoded(char[] chars) {
        int length = chars.length;

        // Check if length is a multiple of 4
        if (length % 4 != 0) {
            return false;
        }

        int countPadding = 0;

        for (int i = 0; i < length; i++) {
            char c = chars[i];

            boolean isBase64Char = (c >= 'A' && c <= 'Z') ||
                    (c >= 'a' && c <= 'z') ||
                    (c >= '0' && c <= '9') ||
                    (c == '+') ||
                    (c == '/');

            // Handle padding characters
            if (c == '=') {
                countPadding++;
                // Padding characters should only be at the end
                if (i < length - 2) {
                    return false;
                }
            } else if (countPadding > 0) {
                // If we have seen a padding character, no other character is allowed after it
                return false;
            }

            if (!isBase64Char && c != '=') {
                return false;
            }
        }

        // Check if there are no more than 2 padding characters
        if (countPadding > 2) {
            return false;
        }

        return true;
    }

    public static boolean isFilledKey(byte[] key) {
        for (byte b : key) {
            if (b != (byte) 0) return false;
        }
        return true;
    }

    public static boolean isFilledKey(char[] key) {
        for (char c : key) {
            if (c != (byte) 0) return false;
        }
        return true;
    }

    public static int bytes2ToIntBE(byte[] byteArray) {
        return ((byteArray[0] & 0xFF) << 8) | (byteArray[1] & 0xFF);
    }

    public static int bytes2ToIntLE(byte[] byteArray) {
        return ((byteArray[1] & 0xFF) << 8) | (byteArray[0] & 0xFF);
    }

    public static byte[] addByteArray(byte[] original, byte[] add) {
        byte[] total = new byte[original.length + add.length];  // For AES-256
        System.arraycopy(original, 0, total, 0, original.length);
        System.arraycopy(add, 0, total, original.length, add.length);
        return total;
    }

    public static long byte4ArrayToUnsignedInt(byte[] byteArray) {
        if (byteArray.length != 4) {
            throw new IllegalArgumentException("Input byte array must have exactly 4 bytes.");
        }

        long result = 0;

        for (int i = 0; i < 4; i++) {
            // Convert each byte to a long (treat it as unsigned)
            long unsignedByte = byteArray[i] & 0xFFL;

            // Shift and combine the bytes into the result
            result |= unsignedByte << (8 * i);
        }

        return result;
    }

    public static byte[] getPartOfBytes(byte[] original, int offset, int length) {
        byte[] part = new byte[length];
        System.arraycopy(original, offset, part, 0, part.length);
        return part;
    }
}


