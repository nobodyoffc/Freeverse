package javaTools;

import constants.Strings;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.security.SecureRandom;

public class StringTools {

    public static char getRandomLowerCaseChar() {
        SecureRandom secureRandom = new SecureRandom();
        return (char) (secureRandom.nextInt(26) + 'a');
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
        return Strings.TEMP + Hex.toHex(BytesTools.getRandomBytes(3));
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
}