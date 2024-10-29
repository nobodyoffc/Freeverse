package crypto.old.EccAesFromPeiTeam;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

public class envelop {
    String symKey;
    ECIES ecies;

    //构造函数初始化，会分配密钥
    public envelop() {
        symKey = AES256.byteToHexString(AES256.generatorKey()).toUpperCase();
//        symKey = AES256.generatorKey();
        EXPList.set_EXP_List();
        ecies = new ECIES();
        ecies.generateKeyPair();
    }

    //更换AES的密钥
    public void setSymKey(String key) {
        this.symKey = key;
    }

    //更换ECC的公钥
    public void setEciesPubKey(String pk) {
        byte[] pk_final = new byte[64];
        byte[] pk_buf = AES256.hexStringToBytes(pk.toUpperCase());
        System.arraycopy(pk_buf, 0, pk_final, 64-pk_buf.length, pk_buf.length);

        ecies.getPair(pk_final);
    }

    //更换ECC的私钥
    public void setEciesSecKey(String sk) {
        ecies.generateKeyPair(sk);
    }

    //获取AES的密钥
    public String getSymKey() {
        return symKey;
    }

    //获取ECC公钥
    public String getEciesPubKey() {
        byte[] pk = ecies.publicKey2Bytes();
        return AES256.byteToHexString(pk);
    }

    //获取ECC私钥
    public String getEciesSecKey() {
        byte[] sk = ecies.secretKey2Bytes();
        return AES256.byteToHexString(sk);
    }

    //对明文使用AES进行加密
    public String encryptMsg(String msg) throws NoSuchPaddingException, UnsupportedEncodingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException, NoSuchProviderException {
        byte[] ct = AES256.encrypt(msg, symKey);
        String encMsg = AES256.byteToHexString(ct);

        return encMsg;
    }

    //对AES的密钥进行非对称加密
    public String encryptKey(String key) throws BadPaddingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IllegalBlockSizeException, UnsupportedEncodingException, NoSuchPaddingException, InvalidKeyException, NoSuchProviderException {
        byte[] ct = ecies.encrypt(key);
        String encKey = AES256.byteToHexString(ct);

        return encKey;
    }

    //解密AES密钥
    public String decryptKey(String encKey) throws Exception {
        byte[] ct = AES256.hexStringToBytes(encKey);
        byte[] pt = ecies.decrypt(ct);
        String key = new String(pt);

        return key;
    }

    //解密得到明文
    public String decryptMsg(String encMsg) throws Exception {
        byte[] ct = AES256.hexStringToBytes(encMsg);
        byte[] pt = AES256.decrypt(AES256.byteToHexString(ct), symKey);
        String msg = new String(pt);

        return msg;
    }

}
