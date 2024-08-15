package crypto.old.EccAesFromPeiTeam;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
public class AES256 {

    private static String iv = "0123456789ABCDEF";
    private static String Algorithm = "AES";
    private static String AlgorithmProvider = "AES/CBC/PKCS7Padding";//"AES/CBC/PKCS5Padding"; //算法/模式/补码方式

    public static byte[] generatorKey() {
//        byte[] key = new byte[16];
        byte[] key = new byte[32];
        SecureRandom random = new SecureRandom();
        random.nextBytes(key);
        return key;
    }

    public static IvParameterSpec getIv() throws UnsupportedEncodingException {
        IvParameterSpec ivParameterSpec = new IvParameterSpec(iv.getBytes("utf-8"));
        return ivParameterSpec;
    }

    public static byte[] encrypt(String src, String key) throws NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException, InvalidAlgorithmParameterException, NoSuchProviderException {
//        byte[] kee = key.getBytes("utf-8");
//        System.out.println("# symKey");
//        System.out.println(key);
        Security.addProvider(new BouncyCastleProvider());
        byte[] kee = hexStringToBytes(key);
        SecretKey secretKey = new SecretKeySpec(kee, Algorithm);
        IvParameterSpec ivParameterSpec = getIv();
        Cipher cipher = Cipher.getInstance(AlgorithmProvider,"BC");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);
        byte[] cipherBytes = cipher.doFinal(src.getBytes(Charset.forName("utf-8")));
        return cipherBytes;
    }

    public static byte[] decrypt(String src, String key) throws Exception {
//        byte[] kee = key.getBytes("utf-8");
        Security.addProvider(new BouncyCastleProvider());
        byte[] kee = hexStringToBytes(key);
        SecretKey secretKey = new SecretKeySpec(kee, Algorithm);

        IvParameterSpec ivParameterSpec = getIv();
        Cipher cipher = Cipher.getInstance(AlgorithmProvider,"BC");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);
        byte[] hexBytes = hexStringToBytes(src);
        byte[] plainBytes = cipher.doFinal(hexBytes);
        return plainBytes;
    }

    public static String byteToHexString(byte[] src) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xff;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                sb.append("0");
            }
            sb.append(hv);
        }
        return sb.toString();
    }

    public static byte[] hexStringToBytes(String hexString) {
        hexString = hexString.toUpperCase();
        int mix = hexString.length() % 2;
        int length = hexString.length() / 2 + mix;
        char[] hexChars = hexString.toCharArray();
        byte[] b = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2 - mix;
            if ( pos < 0 ) {
                b[i] = charToByte(hexChars[pos + 1]);
            } else {
                b[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
            }
        }
        return b;
    }

    private static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }
}

