package utils;

import constants.Strings;
import crypto.Base58;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.security.SecureRandom;

public class StringUtils {

    public static String hexToBase64(String hex) {
        if(hex ==null || !Hex.isHexString(hex))return null;
        return Base64.getEncoder().encodeToString(Hex.fromHex(hex));
    }

    public enum EncodeType {
        HEX("hex"),
        BASE58("Base58"),
        BASE64("Base64"),
        UTF8("UTF-8");


        private final String displayName;

        EncodeType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @NotNull
        public static EncodeType fromText(String text) {
            if (Hex.isHexString(text)) {
                return HEX;
            } else if (Base58.isBase58Encoded(text)) {
                return BASE58;
            } else if (StringUtils.isBase64(text)) {
                return BASE64;
            } else {
                return UTF8;
            }
        }
    }

    public static String toHex(String str) {
        return Hex.toHex(str.getBytes());
    }

    public static String toBase58(String str) {
        return Base58.encode(str.getBytes());
    }

    public static String toBase64(String str) {
        return Base64.getEncoder().encodeToString(str.getBytes());
    }

    public static String toUTF8(String str) {
        return new String(str.getBytes());
    }

    public static byte[] fromHex(String str) {
        return Hex.fromHex(str);
    }

    public static byte[] fromBase58(String str) {
        return Base58.decode(str);
    }

    public static byte[] fromBase64(String str) {
        return Base64.getDecoder().decode(str);
    }

    public static byte[] fromUTF8(String str) {
        return str.getBytes();
    }

    public static char getRandomLowerCaseChar() {
        SecureRandom secureRandom = new SecureRandom();
        return (char) (secureRandom.nextInt(26) + 'a');
    }
    public static String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
    public static String getRandomLowerCaseString(int length) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            stringBuilder.append(getRandomLowerCaseChar());
        }
        return stringBuilder.toString();
    }

    public static String arrayToString(String[] array) {
        if (array == null || array.length == 0) {
            return "";
        }

        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            stringBuilder.append(array[i]);
            if (i < array.length - 1) {
                stringBuilder.append(",");
            }
        }

        return stringBuilder.toString();
    }

    public static String getWordAtPosition(String input, int position) {
        // Split the string into words using one or more spaces as the delimiter
        String[] words = input.trim().split("\\s+");

        // Check if the requested position is valid
        if (position < 1 || position > words.length) {
            return null; // or throw an exception, depending on your needs
        }

        // Return the word at the specified position (1-based index)
        return words[position - 1];
    }

    public static String listToString(List<String> list) {
        if (list == null || list.size() == 0) {
            return "";
        }

        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            stringBuilder.append(list.get(i));
            if (i < list.size() - 1) {
                stringBuilder.append(",");
            }
        }

        return stringBuilder.toString();
    }

    @NotNull
    public static String getTempName() {
        return Strings.TEMP + Hex.toHex(BytesUtils.getRandomBytes(3));
    }

    public static String[] splitString(String str) {
        return (str != null) ? str.split(",") : new String[0];
    }

    public static long parseLong(String str) {
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static float parseFloat(String str) {
        try {
            return Float.parseFloat(str);
        } catch (NumberFormatException e) {
            return 0.0f;
        }
    }

    public static boolean parseBoolean(String str) {
        return Boolean.parseBoolean(str);
    }

    public static boolean isContainCaseInsensitive(String[] array, String item) {
        return Arrays.stream(array)
                .anyMatch(s -> s.equalsIgnoreCase(item));
    }

    @NotNull
    public static String omitMiddle(String str, int width) {
        int halfWidth = (width -3) /2;
        String head = str.substring(0,halfWidth);
        String tail = str.substring(str.length()-halfWidth);
        str = head+"..."+tail;
        return str;
    }

    @Nullable
    public static String base64ToHex(String signedTx) {
        byte[] bytes = Base64.getDecoder().decode(signedTx);
        if(bytes==null)return null;
        return Hex.toHex(bytes);
    }

    public static boolean isBase64(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        // Must contain at least one character from the Base64 alphabet that's not in hex
        if (!str.matches(".*[G-Z+/].*")) {
            return false;
        }
        // Check if the string contains only valid Base64 characters
        if (!str.matches("^[A-Za-z0-9+/]*={0,2}$")) {
            return false;
        }
        // Check if padding is correct
        if (str.contains("=") && !str.matches(".*={1,2}$")) {
            return false;
        }
        try {
            Base64.getDecoder().decode(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean isBech32(String str) {
        if(str==null||str.isEmpty())return false;
        return str.matches("^[a-z0-9]+$");
    }

}
