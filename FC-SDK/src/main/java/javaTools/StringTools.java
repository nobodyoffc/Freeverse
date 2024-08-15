package javaTools;

import constants.Strings;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class StringTools {
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
}
