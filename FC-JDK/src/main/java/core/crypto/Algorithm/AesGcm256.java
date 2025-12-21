package core.crypto.Algorithm;

import constants.CodeMessage;
import core.crypto.*;
import data.fcData.AlgorithmId;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.Security;

public class AesGcm256 {
    public static CryptoDataByte encrypt(InputStream inputStream, OutputStream outputStream, byte[] key, byte[] iv, CryptoDataByte cryptoDataByte) {
        String algorithm = "AES";
        String transformation = "AES/GCM/NoPadding";
        String provider = "BC";
        if(cryptoDataByte==null) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setSymkey(key);
            cryptoDataByte.setIv(iv);
            if(cryptoDataByte.getAlg()==null) cryptoDataByte.setAlg(AlgorithmId.FC_AesGcm256_No1_NrC7);
        }else if(cryptoDataByte.getSymkey()==null)cryptoDataByte.setSymkey(key);
        else if(cryptoDataByte.getIv()==null)cryptoDataByte.setIv(iv);
        if(cryptoDataByte.getAlg()==null) cryptoDataByte.setAlg(AlgorithmId.FC_AesGcm256_No1_NrC7);
        return Encryptor.encryptBySymkeyBase(algorithm,transformation,provider,inputStream,outputStream,cryptoDataByte);
    }

    public static CryptoDataByte encrypt(InputStream inputStream, OutputStream outputStream,CryptoDataByte cryptoDataByte) {
        return encrypt(inputStream,outputStream,null,null,cryptoDataByte);
    }

    public static CryptoDataByte encryptStream(InputStream inputStream, OutputStream outputStream, byte[] key, byte[] iv) {
        return encrypt(inputStream,outputStream,key,iv,null);
    }


    public static void decryptStream(InputStream inputStream, OutputStream outputStream, CryptoDataByte cryptoDataByte) {
        Security.addProvider(new BouncyCastleProvider());

        if (cryptoDataByte == null) return;

        byte[] key = cryptoDataByte.getSymkey();
        byte[] iv = cryptoDataByte.getIv();

        if (key == null || key.length != 32) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4006InvalidKey);
            return;
        }

        if (iv == null) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4009MissingIv);
            return;
        }

        try {
            // Read all ciphertext from input stream
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            byte[] ciphertext = baos.toByteArray();

            // Initialize cipher with GCMParameterSpec for proper tag handling
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv); // 128-bit auth tag

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            // Decrypt all at once - GCM needs full ciphertext for tag verification
            byte[] plaintext = cipher.doFinal(ciphertext);

            // Write decrypted data to output stream
            outputStream.write(plaintext);

            if (cryptoDataByte.getCode() == null) {
                cryptoDataByte.set0CodeMessage();
            }

        } catch (Exception e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code1029FailedToDecrypt, e.getMessage());
        }
    }

    public static CryptoDataByte decryptStream(InputStream inputStream, OutputStream outputStream, byte[] key, byte[] iv ){
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setSymkey(key);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.setType(EncryptType.Symkey);
        cryptoDataByte.setAlg(AlgorithmId.FC_AesGcm256_No1_NrC7);
        decryptStream(inputStream,outputStream,cryptoDataByte);
        return cryptoDataByte;
    }

    public static CryptoDataByte decrypt(CryptoDataByte cryptoDataByte) {
        try (ByteArrayInputStream bisCipher = new ByteArrayInputStream(cryptoDataByte.getCipher());
             ByteArrayOutputStream bosData = new ByteArrayOutputStream()) {
            decryptStream(bisCipher, bosData, cryptoDataByte);

            // Only set success if no error occurred during decryption
            if (cryptoDataByte.getCode() == null || cryptoDataByte.getCode() == 0) {
                byte[] data = bosData.toByteArray();
                byte[] did = Hash.sha256x2(data);

                cryptoDataByte.setDid(did);
                // AES-GCM has built-in authentication, no need for additional sum check
                // If decryption succeeded without exception, authentication is valid
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
