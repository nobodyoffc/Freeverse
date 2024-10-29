package javaTools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

public class CharArrayTool {
    public static void main(String[] args) {

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        //用hex方式输入32字节私钥
        String ask = "Input a 32bytes key in hex: ";
        char[] keyCharArray = input32BytesKey(br, ask);
        byte[] keyBytes = hexCharArrayToByteArray(keyCharArray);
        System.out.println("The 32 bytes key in hex: " + HexFormat.of().formatHex(keyBytes));

        //输入utf8编码的普通密码
        ask = "Input a password in UTF8: ";
        char[] passwordCharArray = inputPassword(br, ask);
        byte[] passwordBytes = BytesTools.charArrayToByteArray(passwordCharArray, StandardCharsets.UTF_8);
        System.out.println("The password in hex: " + HexFormat.of().formatHex(passwordBytes));

        //用完后清除内存
        Arrays.fill(keyCharArray, (char) 0);
        Arrays.fill(keyBytes, (byte) 0);
        if (passwordCharArray != null) Arrays.fill(passwordCharArray, (char) 0);
        Arrays.fill(passwordBytes, (byte) 0);

        System.out.println("The filled char array: " + Arrays.toString(keyCharArray));
        System.out.println("The filled byte array: " + HexFormat.of().formatHex(keyBytes));
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

    public static char[] input32BytesKey(BufferedReader br, String ask) {
        System.out.println(ask);
        char[] symKey = new char[64];
        int num = 0;
        try {
            num = br.read(symKey);

            if (num != 64 || !isHexCharArray(symKey)) {
                System.out.println("The key should be 32 bytes in hex.");
                return null;
            }
            br.read();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return symKey;
    }

    public static boolean isHexCharArray(char[] charArray) {
        for (char c : charArray) {
            if (Character.digit(c, 16) == -1) {
                return false;
            }
        }
        return true;
    }

    public static char[] inputPassword(BufferedReader br, String ask) {
        System.out.println(ask);
        char[] input = new char[64];
        int num = 0;
        try {
            num = br.read(input);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (num == 0) return null;
        char[] password = new char[num];
        System.arraycopy(input, 0, password, 0, num);
        return password;
    }
}
