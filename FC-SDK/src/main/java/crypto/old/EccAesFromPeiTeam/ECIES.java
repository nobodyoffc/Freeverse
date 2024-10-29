package crypto.old.EccAesFromPeiTeam;


import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;

public class ECIES {
    BigInteger secretKey;
    BigInteger[] publicKey;

    // 随机生成密钥对
    public void generateKeyPair() {
        secretKey = Math_Modulo.random();
        publicKey = Secp256k1.multiply_G(secretKey);
    }

    // 反序列化
    public void getPair (byte[] keys) {
        // 若 keys 为公钥，则创建仅有公钥，仅用于加密的 KeyPair 对象
        if (keys.length == 64) {
            publicKey = Secp256k1.setBytes(keys);
            secretKey = null;
            // 若 keys 为私钥，则创建完整的 KeyPair 对象
        } else if (keys.length == 32) {
            secretKey = Math_Modulo.setBytes(keys);
            publicKey = Secp256k1.multiply_G(secretKey);
        }
    }

    public void generateKeyPair (String sk) {
        byte[] sk_final = new byte[32];
        byte[] sk_buf = AES256.hexStringToBytes(sk.toUpperCase());
        System.arraycopy(sk_buf, 0, sk_final, 32-sk_buf.length, sk_buf.length);

        getPair(sk_final);
    }

    // 私钥序列化
    public byte[] secretKey2Bytes() {
        return Math_Modulo.toBytes(secretKey);
    }

    // 公钥序列化
    public byte[] publicKey2Bytes() {
        return Secp256k1.toBytes(publicKey);
    }

    // 加密
    public byte[] encrypt(String message) throws NoSuchAlgorithmException, IllegalBlockSizeException, InvalidKeyException, BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException, UnsupportedEncodingException, NoSuchProviderException {
        BigInteger r = Math_Modulo.random();
        // BigInteger r = new BigInteger("123456789", 16);
        BigInteger[] R = Secp256k1.multiply_G(r);
        BigInteger[] P = Secp256k1.multiply_Point(publicKey, r);
        BigInteger s = P[0];

        byte[] k = hash.SHA512(AES256.byteToHexString(Math_Modulo.toBytes(s)));
        byte[] ke = new byte[32];
        byte[] km = new byte[32];
        System.arraycopy(k, 0, ke, 0, 32);
        System.arraycopy(k, 32, km, 0, 32);
        String kee = AES256.byteToHexString(ke).toUpperCase();
        byte[] c = AES256.encrypt(message, kee);
        byte[] msgTotal = new byte[32 + c.length];

        System.arraycopy(km, 0, msgTotal, 0, 32);
        System.arraycopy(c, 0, msgTotal, 32, c.length);

        byte[] d = hash.SHA256(AES256.byteToHexString(msgTotal));
        byte[] cypherTotal = new byte[96 + c.length];

        System.arraycopy(Secp256k1.toBytes(R), 0, cypherTotal, 0, 64);
        System.arraycopy(d, 0, cypherTotal, 64, 32);
        System.arraycopy(c, 0, cypherTotal, 96, c.length);

        return cypherTotal;
    }


    // 解密
    public byte[] decrypt(byte[] cypher) throws Exception {
        // 首先分离 cypher 为 R, d, c
        byte[] Rbytes = new byte[64];
        byte[] d = new byte[32];
        byte[] c = new byte[cypher.length - 96];

        System.arraycopy(cypher, 0, Rbytes, 0, 64);
        System.arraycopy(cypher, 64, d, 0, 32);
        System.arraycopy(cypher, 96, c, 0, cypher.length - 96);

        BigInteger[] R = Secp256k1.setBytes(Rbytes);

        // 计算 S
        BigInteger[] P = Secp256k1.multiply_Point(R, secretKey);
        BigInteger s = P[0];


        // 计算 ke 和 km
        byte[] k = hash.SHA512(AES256.byteToHexString(Math_Modulo.toBytes(s)));

        byte[] ke = new byte[32];
        byte[] km = new byte[32];
        System.arraycopy(k, 0, ke, 0, 32);
        System.arraycopy(k, 32, km, 0, 32);
        String kee = AES256.byteToHexString(ke).toUpperCase();

        byte[] msgTotal = new byte[32 + c.length];

        System.arraycopy(km, 0, msgTotal, 0, 32);
        System.arraycopy(c, 0, msgTotal, 32, c.length);

        byte[] d0 = hash.SHA256(AES256.byteToHexString(msgTotal));

        if (Arrays.equals(d, d0)) {
//            String test = AES256.byteToHexString(c);
            byte[] message = AES256.decrypt(AES256.byteToHexString(c), kee);
            return message;
        } else {
            return null;
        }
    }


}