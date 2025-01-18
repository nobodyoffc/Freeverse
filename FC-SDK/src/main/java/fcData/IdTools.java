package fcData;

import tools.Hex;

public class IdTools {
    public static String makeShortName(String id){
        return id.substring(0,6);
    }

    public static String makeShortName(byte[] id){
        String hex = Hex.toHex(id);
        return makeShortName(hex);
    }
}
