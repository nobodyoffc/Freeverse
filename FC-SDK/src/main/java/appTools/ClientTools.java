package appTools;

import crypto.Hash;
import javaTools.BytesTools;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

import static constants.ApiNames.apiList;
import static constants.ApiNames.freeApiList;

public class ClientTools {
    public static String getApiNameFromUrl(String url) {
        int lastSlashIndex = url.lastIndexOf('/');
        if (lastSlashIndex != -1 && lastSlashIndex != url.length() - 1) {
            String name = url.substring(lastSlashIndex + 1);
            if (apiList.contains(name) || freeApiList.contains(name)) {
                return name;
            }
            return "";
        } else {
            return "";  // Return empty string if '/' is the last character or not found
        }
    }

    public static String getSessionKeySign(byte[] sessionKeyBytes, byte[] dataBytes) {
        return HexFormat.of().formatHex(Hash.sha256x2(BytesTools.bytesMerger(dataBytes, sessionKeyBytes)));
    }

    public static boolean isGoodSign(String msg, String sign, String symKey) {
        byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
        return isGoodSign(msgBytes, sign, HexFormat.of().parseHex(symKey));
    }

    public static boolean isGoodSign(byte[] msgBytes, String sign, byte[] symKey) {
        if (sign == null || msgBytes == null) return false;
        byte[] signBytes = BytesTools.bytesMerger(msgBytes, symKey);
        String doubleSha256Hash = HexFormat.of().formatHex(Hash.sha256x2(signBytes));
        return (sign.equals(doubleSha256Hash));
    }

    public static String getSessionName(byte[] sessionKey) {
        if (sessionKey == null) return null;
        return HexFormat.of().formatHex(Arrays.copyOf(sessionKey, 6));
    }
}
