package crypto.Algorithm;

import crypto.*;
import fcData.AlgorithmId;

import java.io.*;

public class AesCbc256 {
    public static CryptoDataByte encrypt(InputStream inputStream, OutputStream outputStream, byte[] key, byte[] iv, CryptoDataByte cryptoDataByte) {
        String algorithm = "AES";
        String transformation = "AES/CBC/PKCS7Padding";
        String provider = "BC";
        if(cryptoDataByte==null) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setSymKey(key);
            cryptoDataByte.setIv(iv);
            if(cryptoDataByte.getAlg()==null) cryptoDataByte.setAlg(AlgorithmId.FC_Aes256Cbc_No1_NrC7);
        }else if(cryptoDataByte.getSymKey()==null)cryptoDataByte.setSymKey(key);
        else if(cryptoDataByte.getIv()==null)cryptoDataByte.setIv(iv);
        if(cryptoDataByte.getAlg()==null) cryptoDataByte.setAlg(AlgorithmId.FC_Aes256Cbc_No1_NrC7);
        return Encryptor.encryptBySymKeyBase(algorithm,transformation,provider,inputStream,outputStream,cryptoDataByte);
    }
    public static CryptoDataByte encrypt(InputStream inputStream, OutputStream outputStream,CryptoDataByte cryptoDataByte) {
        return encrypt(inputStream,outputStream,null,null,cryptoDataByte);
    }

    public static CryptoDataByte encryptStream(InputStream inputStream, OutputStream outputStream, byte[] key, byte[] iv) {
        return encrypt(inputStream,outputStream,key,iv,null);
    }

    /*
    Decrypt
     */

//    public static CryptoDataByte decrypt(InputStream inputStream, OutputStream outputStream, byte[] key, byte[] iv, @Nullable CryptoDataByte cryptoDataByte) {
//
//        String algorithm = "AES";
//        String transformation = "AES/CBC/PKCS7Padding";
//        String provider = "BC";
//        return DecryptorSym.decryptBase(algorithm,transformation,provider,inputStream,outputStream,key,iv,cryptoDataByte);
//    }

    public static void decryptStream(InputStream inputStream, OutputStream outputStream, CryptoDataByte cryptoDataByte) {

        String algorithm = "AES";
        String transformation = "AES/CBC/PKCS7Padding";
        String provider = "BC";
        Decryptor.decryptBySymKeyBase(algorithm,transformation,provider,inputStream,outputStream,cryptoDataByte);
    }
    public static CryptoDataByte decryptStream(InputStream inputStream, OutputStream outputStream, byte[] key, byte[] iv ){
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setSymKey(key);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.setType(EncryptType.SymKey);
        cryptoDataByte.setAlg(AlgorithmId.FC_Aes256Cbc_No1_NrC7);
        decryptStream(inputStream,outputStream,cryptoDataByte);
        return cryptoDataByte;
    }

    public static CryptoDataByte decrypt(CryptoDataByte cryptoDataByte) {
        try (ByteArrayInputStream bisCipher = new ByteArrayInputStream(cryptoDataByte.getCipher());
             ByteArrayOutputStream bosData = new ByteArrayOutputStream()) {
            decryptStream(bisCipher, bosData, cryptoDataByte);
            byte[] data = bosData.toByteArray();
            byte[] did = Hash.sha256x2(data);

            cryptoDataByte.setDid(did);
            if(cryptoDataByte.checkSum(AlgorithmId.FC_Aes256Cbc_No1_NrC7)) {
                cryptoDataByte.setData(data);
                cryptoDataByte.set0CodeMessage();
            }
        } catch (IOException e) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(10);
            return cryptoDataByte;
        }
        return cryptoDataByte;
    }
}
