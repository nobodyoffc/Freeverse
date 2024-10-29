package crypto.old.EccAesFromPeiTeam;

public class util {
    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String convertBytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars).toLowerCase();
    }

    public static byte[] hexStringToByteArray(String hexString){
        byte[] bytes = new byte[hexString.length() / 2];

        for(int i = 0; i < hexString.length(); i += 2){
            String sub = hexString.substring(i, i + 2);
            Integer intVal = Integer.parseInt(sub, 16);
            bytes[i / 2] = intVal.byteValue();
        }
        return bytes;
    }
}
