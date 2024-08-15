package javaTools;

public class Hex {
    private static final String HEX_PATTERN = "^[0-9a-fA-F]+$";

    public static String toHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static byte[] fromHex(String hexString) {
        hexString = hexString.toLowerCase();
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
        }
        return data;
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

    public static char[] byteArrayToHexCharArray(byte[] byteArray) {
        char[] hexChars = new char[byteArray.length * 2];
        for (int i = 0; i < byteArray.length; i++) {
            int v = byteArray[i] & 0xFF;
            hexChars[i * 2] = Character.forDigit((v >>> 4) & 0x0F, 16);
            hexChars[i * 2 + 1] = Character.forDigit(v & 0x0F, 16);
        }
        return hexChars;
    }

    public static boolean isHexCharArray(char[] charArray) {
        for (char c : charArray) {
            if (Character.digit(c, 16) == -1) {
                return false;
            }
        }
        return true;
    }

    public static boolean isHexString(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        if(str.length()%2!=0)return false;
        return str.matches(HEX_PATTERN);
    }
}
