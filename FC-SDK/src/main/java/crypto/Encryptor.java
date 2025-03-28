package crypto;
/*
 * There are 4 types of encrypting and decrypting:
 * 1. SymKey: Encrypt or decrypt by a 32 bytes symmetric key.
 * 2. Password: Encrypt or decrypt by a UTF-8 password which no longer than 64 bytes.
 * 3. AsyOneWay: Encrypt by the public key B. A random key pair B will be generate and the new public key will be given in the encrypting result. When decrypting, only the private key B is required.
 * 4. AsyTwoWay: Encrypt by the public key of B and the private of key A. You can decrypt it with priKeyB and pubKeyA, or, with priKeyA and pubKeyB.
 *  The type of SymKey is the base method. When encrypting or decrypting with the other 3 type method, a symKey will be calculated at first and then be used to encrypt or to decrypt. You can get the symKey if you need.
 */

import com.google.common.hash.Hashing;

import constants.CodeMessage;
import constants.Constants;
import crypto.Algorithm.AesCbc256;
import crypto.Algorithm.Ecc256K1;
import crypto.Algorithm.aesCbc256.CipherInputStreamWithHash;
import crypto.old.EccAes256K1P7;
import fcData.AlgorithmId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.BytesUtils;
import utils.FileUtils;
import utils.Hex;
import org.bitcoinj.core.ECKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

import static fcData.AlgorithmId.*;

public class Encryptor {
    AlgorithmId algorithmId;
    protected static final Logger log = LoggerFactory.getLogger(Encryptor.class);

    public Encryptor() {
        this.algorithmId = FC_AesCbc256_No1_NrC7;
    }

    public Encryptor(AlgorithmId algorithmId) {
        this.algorithmId = algorithmId;
    }

    public static String encryptFile(String fileName, String pubKeyHex) {

        byte[] pubKey = Hex.fromHex(pubKeyHex);
        Encryptor encryptor = new Encryptor(FC_EccK1AesCbc256_No1_NrC7);
        String tempFileName = FileUtils.getTempFileName();
        CryptoDataByte result1 = encryptor.encryptFileByAsyOneWay(fileName, tempFileName, pubKey);
        if(result1.getCode()!=0)return null;
        String cipherFileName;
        try {
            cipherFileName = Hash.sha256x2(new File(tempFileName));
            Files.move(Paths.get(tempFileName),Paths.get(cipherFileName));
        } catch (IOException e) {
            return null;
        }
        return cipherFileName;
    }

    public static String encryptBySymKeyToJson(byte[] data, byte[]symKey) {
        Encryptor encryptor = new Encryptor(FC_AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte = encryptor.encryptBySymKey(data,symKey);
        if(cryptoDataByte.getCode()!=0)return null;
        return cryptoDataByte.toJson();
    }

    public CryptoDataByte encryptByPassword(@NotNull byte[] msg, @NotNull char[] password){
        byte[] iv = BytesUtils.getRandomBytes(16);
        byte[] symKey = passwordToSymKey(password, iv);
        CryptoDataByte cryptoDataByte = encryptBySymKey(msg,symKey,iv);
        cryptoDataByte.setType(EncryptType.Password);
        return cryptoDataByte;
    }
    public CryptoDataByte encryptStrByPassword(@NotNull String msgStr, @NotNull char[] password){
        byte[] msg = msgStr.getBytes(StandardCharsets.UTF_8);
        return encryptByPassword(msg,password);
    }
    public String encryptStrToJsonBySymKey(@NotNull String msgStr, @NotNull String symKeyHex){
        byte[] msg = msgStr.getBytes(StandardCharsets.UTF_8);
        byte[] key;
        try {
            key = HexFormat.of().parseHex(symKeyHex);
        }catch (Exception ignore){
            CryptoDataStr cryptoDataStr = new CryptoDataStr();
            cryptoDataStr.setCodeMessage(CodeMessage.Code4007FailedToParseHex);
            return cryptoDataStr.toNiceJson();
        }

        return encryptToJsonBySymKey(msg,key);
    }
    public String encryptToJsonBySymKey(@NotNull byte[] msg, @NotNull byte[] key){
        byte[] iv = BytesUtils.getRandomBytes(16);
        CryptoDataByte cryptoDataByte = encryptBySymKey(msg,key, iv);
        return cryptoDataByte.toNiceJson();
    }
    public CryptoDataByte encryptFileByPassword(@NotNull String dataFileName, @NotNull String cipherFileName, @NotNull char[]password){
        FileUtils.createFileWithDirectories(cipherFileName);
        byte[] iv = BytesUtils.getRandomBytes(16);
        byte[] key = passwordToSymKey(password, iv);


        CryptoDataByte cryptoDataByte = encryptFileBySymKey(dataFileName,cipherFileName,key,iv);
//        cryptoDataByte.setSymKey(key);
        cryptoDataByte.setType(EncryptType.Password);

        return cryptoDataByte;
    }
    public CryptoDataByte encryptFileBySymKey(@NotNull String dataFileName, @NotNull String cipherFileName, @NotNull byte[]key){
        return encryptFileBySymKey(dataFileName,cipherFileName,key,null);
    }
    public CryptoDataByte encryptFileBySymKey(@NotNull String dataFileName, @NotNull String cipherFileName, @NotNull byte[]key, byte[] iv){
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        if(iv==null)iv = BytesUtils.getRandomBytes(16);
        cryptoDataByte.setType(EncryptType.SymKey);
        cryptoDataByte.setAlg(algorithmId);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.setSymKey(key);

        String tempFile = FileUtils.getTempFileName();
        try (FileInputStream fis = new FileInputStream(dataFileName);
             FileOutputStream fos = new FileOutputStream(tempFile)) {
            switch (cryptoDataByte.getAlg()) {
                default -> AesCbc256.encrypt(fis, fos, cryptoDataByte);
            }
        } catch (FileNotFoundException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code1011DataNotFound);
            return cryptoDataByte;
        } catch (IOException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4001FailedToEncrypt);
            return cryptoDataByte;
        }

        try (FileInputStream fis = new FileInputStream(tempFile);
             FileOutputStream fos = new FileOutputStream(cipherFileName)) {
            fos.write(cryptoDataByte.toJson().getBytes());
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fis.close();
            Files.delete(Paths.get(tempFile));
        } catch (IOException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4001FailedToEncrypt);
        }

        return cryptoDataByte;
    }

    public static String makeEncryptedFileName(String originalFileName){
        int endIndex = originalFileName.lastIndexOf('.');
        String suffix = "_" + originalFileName.substring(endIndex + 1);
        return originalFileName.substring(0, endIndex) + suffix + Constants.DOT_FV;
    }

    public byte[] encryptStrToBundleBySymKey(@NotNull String msgStr, @NotNull String keyHex){
        byte[] msg = msgStr.getBytes(StandardCharsets.UTF_8);
        byte[] key;
        try {
            key = HexFormat.of().parseHex(keyHex);
        }catch (Exception ignore){
            CryptoDataStr cryptoDataStr = new CryptoDataStr();
            cryptoDataStr.setCodeMessage(CodeMessage.Code4007FailedToParseHex);
            return null;
        }
        return encryptToBundleBySymKey(msg,key);
    }
    public byte[] encryptToBundleBySymKey(@NotNull byte[] msg, @NotNull byte[] key){
        byte[] iv = BytesUtils.getRandomBytes(16);
        CryptoDataByte cryptoDataByte = encryptBySymKey(msg,key, iv);
        if(cryptoDataByte.getCode()!=0)return null;
        return cryptoDataByte.toBundle();
    }
    public byte[] encryptToBundleByPassword(@NotNull byte[] msg, @NotNull char[] password){
        byte[] iv = BytesUtils.getRandomBytes(16);
        byte[] symKey = Encryptor.passwordToSymKey(password,iv);
        CryptoDataByte cryptoDataByte = encryptBySymKey(msg,symKey, iv);
        if(cryptoDataByte.getCode()!=0)return null;
        return cryptoDataByte.toBundle();
    }

    public CryptoDataByte encryptBySymKey(@NotNull byte[] msg, @NotNull byte[] symKey){
        byte[] iv = BytesUtils.getRandomBytes(16);
        return encryptBySymKey(msg,symKey,iv,null);
    }
    public CryptoDataByte encryptBySymKey(@NotNull byte[] msg, @NotNull byte[] symKey, byte[] iv){
        return encryptBySymKey(msg,symKey,iv,null);
    }

    private CryptoDataByte encryptBySymKey(@NotNull byte[] msg, @Nullable  byte[] key, @Nullable  byte[] iv, @Nullable CryptoDataByte cryptoDataByte){
        try(ByteArrayInputStream bisMsg = new ByteArrayInputStream(msg);
            ByteArrayOutputStream bosCipher = new ByteArrayOutputStream()) {
            switch (algorithmId){
                case FC_AesCbc256_No1_NrC7 ->  cryptoDataByte = AesCbc256.encrypt(bisMsg, bosCipher, key,iv, cryptoDataByte);
                default -> {
                    System.out.println("The algorithm is not supported:"+algorithmId);
                    if(cryptoDataByte==null)cryptoDataByte = new CryptoDataByte();
                    cryptoDataByte.setCodeMessage(CodeMessage.Code4002NoSuchAlgorithm);
                    return cryptoDataByte;
                }
            }

            if(cryptoDataByte!=null && cryptoDataByte.getKeyName()==null)
                cryptoDataByte.makeKeyName(key);

            byte[] cipher = bosCipher.toByteArray();
            if(cryptoDataByte==null)cryptoDataByte = new CryptoDataByte();

            cryptoDataByte.setCipher(cipher);
            cryptoDataByte.set0CodeMessage();

            return cryptoDataByte;
        } catch (IOException e) {
            if(cryptoDataByte==null)cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code4001FailedToEncrypt);
            cryptoDataByte.setType(EncryptType.SymKey);
            return cryptoDataByte;
        }
    }



    public CryptoDataByte encryptStreamBySymKey(@NotNull InputStream inputStream, @NotNull OutputStream outputStream, byte[] key, byte[] iv, CryptoDataByte cryptoDataByte) {
        switch (algorithmId){
            case FC_AesCbc256_No1_NrC7,FC_EccK1AesCbc256_No1_NrC7-> {
                return AesCbc256.encrypt(inputStream,outputStream,key,iv,cryptoDataByte);
            }
            default -> {
                System.out.println("The algorithm is not supported:"+algorithmId);
                if(cryptoDataByte==null)cryptoDataByte = new CryptoDataByte();
                cryptoDataByte.setCodeMessage(CodeMessage.Code4002NoSuchAlgorithm);
                return cryptoDataByte;
            }
        }
    }

    public static CryptoDataByte encryptBySymKeyBase(String algo, String transformation, String provider, InputStream inputStream, OutputStream outputStream, CryptoDataByte cryptoDataByte) {
        AlgorithmId alg = null;
        if(cryptoDataByte.getAlg()!=null){
            alg = cryptoDataByte.getAlg();
        }

        byte[] key= cryptoDataByte.getSymKey();
        if(key.length!=32){
            cryptoDataByte.setCodeMessage(CodeMessage.Code4008WrongKeyLength);
            return cryptoDataByte;
        }

        Security.addProvider(new BouncyCastleProvider());

        SecretKeySpec keySpec = new SecretKeySpec(key, algo);
        byte[] iv=cryptoDataByte.getIv();
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        Cipher cipher;
        var hashFunction = Hashing.sha256();
        var hasherIn = hashFunction.newHasher();
        var hasherOut = hashFunction.newHasher();
        try {
            cipher = Cipher.getInstance(transformation, provider);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            try (CipherInputStreamWithHash cis = new CipherInputStreamWithHash(inputStream, cipher)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = cis.read(buffer, hasherIn, hasherOut)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        } catch (NoSuchAlgorithmException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4002NoSuchAlgorithm, e.getMessage());
            return cryptoDataByte;
        } catch (NoSuchProviderException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4003NoSuchProvider, e.getMessage());
            return cryptoDataByte;
        } catch (NoSuchPaddingException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4004NoSuchPadding, e.getMessage());
            return cryptoDataByte;
        } catch (InvalidAlgorithmParameterException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4005InvalidAlgorithmParameter, e.getMessage());
            return cryptoDataByte;
        } catch (InvalidKeyException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4006InvalidKey, e.getMessage());
            return cryptoDataByte;
        } catch (IOException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4001FailedToEncrypt, e.getMessage());
            return cryptoDataByte;
        }
        byte[] cipherId = Decryptor.sha256(hasherOut.hash().asBytes());
        byte[] did = Decryptor.sha256(hasherIn.hash().asBytes());
        cryptoDataByte.setCipherId(cipherId);
        cryptoDataByte.setDid(did);
        if(cryptoDataByte.getType()==null)
            cryptoDataByte.setType(EncryptType.SymKey);
        cryptoDataByte.setSymKey(key);
        cryptoDataByte.setAlg(alg);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.makeSum4();
        cryptoDataByte.set0CodeMessage();
        return cryptoDataByte;
    }

    public CryptoDataByte encryptStrByAsyOneWay(@NotNull String data,@NotNull  String pubKeyBHex){
        return encryptByAsyOneWay(data.getBytes(), Hex.fromHex(pubKeyBHex));
    }

    public CryptoDataByte encryptByAsyOneWay(@NotNull byte[] data, @NotNull byte[] pubKeyB){
        byte[] priKeyA;
        ECKey ecKey = new ECKey();
        priKeyA = ecKey.getPrivKeyBytes();
        return encryptByAsy(data, priKeyA, pubKeyB, EncryptType.AsyOneWay);
    }
    public byte[] encryptByAsyOneWayToBundle(@NotNull byte[] data, @NotNull byte[] pubKeyB){
        CryptoDataByte cryptoDataByte = encryptByAsyOneWay(data,pubKeyB);
        return cryptoDataByte.toBundle();
    }

    public CryptoDataByte encryptByAsyTwoWay(@NotNull byte[] data, @NotNull byte[]priKeyA, @NotNull byte[] pubKeyB){
        CryptoDataByte cryptoDataByte = encryptByAsy(data, priKeyA, pubKeyB, EncryptType.AsyTwoWay);
        cryptoDataByte.setPubKeyA(KeyTools.priKeyToPubKey(priKeyA));
        return cryptoDataByte;
    }
    public byte[] encryptByAsyTwoWayToBundle(@NotNull byte[] data,@NotNull byte[]priKeyA, @NotNull byte[] pubKeyB){
        CryptoDataByte cryptoDataByte = encryptByAsyTwoWay(data,priKeyA,pubKeyB);
        return cryptoDataByte.toBundle();
    }
    private CryptoDataByte encryptByAsy(@NotNull byte[] data, byte[]priKeyA, byte[] pubKeyB, EncryptType encryptType){
        CryptoDataByte cryptoDataByte;
        if(priKeyA==null){
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code1033MissPriKey);
            return cryptoDataByte;
        }
        if(pubKeyB==null){
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code1001PubKeyMissed);
            return cryptoDataByte;
        }

        try(ByteArrayInputStream bis = new ByteArrayInputStream(data);
            ByteArrayOutputStream bos = new ByteArrayOutputStream()){
            cryptoDataByte
                    = encryptStreamByAsyTwoWay(bis, bos, priKeyA, pubKeyB);
            cryptoDataByte.setCipher(bos.toByteArray());
            cryptoDataByte.makeSum4();
            cryptoDataByte.setType(encryptType);
            cryptoDataByte.setCodeMessage(CodeMessage.Code0Success);

            return cryptoDataByte;
        } catch (IOException e) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code4001FailedToEncrypt);
            return cryptoDataByte;
        }
    }

    public CryptoDataByte encryptFileByAsyOneWay(@NotNull String dataFileName, @NotNull String cipherFileName, @NotNull byte[] pubKeyB){
        return encryptFileByAsy(dataFileName, cipherFileName, pubKeyB, null);
    }
    public CryptoDataByte encryptFileByAsyTwoWay(@NotNull String dataFileName, @NotNull String cipherFileName, @NotNull byte[] pubKeyB,@NotNull byte[]priKeyA){
        return encryptFileByAsy(dataFileName, cipherFileName, pubKeyB, priKeyA);
    }
    private CryptoDataByte encryptFileByAsy(@NotNull String dataFileName, @NotNull String cipherFileName, @NotNull byte[] pubKeyB,byte[]priKeyA){
        FileUtils.createFileWithDirectories(cipherFileName);

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setAlg(algorithmId);

        checkKeysMakeType(pubKeyB, priKeyA, cryptoDataByte);

        byte[] iv = BytesUtils.getRandomBytes(16);
        cryptoDataByte.setIv(iv);

        String tempFile = FileUtils.getTempFileName();
        try(FileInputStream fis = new FileInputStream(dataFileName);
            FileOutputStream fos = new FileOutputStream(tempFile)){

            encryptStreamByAsy(fis, fos, cryptoDataByte);

        } catch (FileNotFoundException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code1011DataNotFound);
            return cryptoDataByte;
        } catch (IOException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4001FailedToEncrypt);
            return cryptoDataByte;
        }

        try (FileInputStream fis = new FileInputStream(tempFile);
             FileOutputStream fos = new FileOutputStream(cipherFileName)) {
            if(priKeyA==null)
                cryptoDataByte.setType(EncryptType.AsyOneWay);
            fos.write(cryptoDataByte.toJson().getBytes());
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fis.close();
            Files.delete(Paths.get(tempFile));
        } catch (IOException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4001FailedToEncrypt);
        }
        //            fos.write(cryptoDataStr.toJson().getBytes());


        return cryptoDataByte;
    }
    public CryptoDataByte encryptStreamByAsyTwoWay(@NotNull InputStream is, @NotNull OutputStream os, @NotNull byte[]priKeyX, @NotNull byte[]pubKeyY){
        return encryptStreamByAsy(is,os,priKeyX,pubKeyY,null);
    }
    public CryptoDataByte encryptStreamByAsyOneWay(@NotNull InputStream is, @NotNull OutputStream os, @NotNull byte[]pubKeyY){
        return encryptStreamByAsy(is,os,null,pubKeyY,null);
    }

    public CryptoDataByte encryptStreamByAsy(@NotNull InputStream is, @NotNull OutputStream os,@NotNull CryptoDataByte cryptoDataByte){
        return encryptStreamByAsy(is,os,null,null,cryptoDataByte);
    }
    private CryptoDataByte encryptStreamByAsy(@NotNull InputStream is, @NotNull OutputStream os, byte[]priKeyX, byte[]pubKeyY, CryptoDataByte cryptoDataByte){
        if(cryptoDataByte==null)cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setAlg(algorithmId);
        checkKeysMakeType(pubKeyY, priKeyX, cryptoDataByte);

        EncryptType type = cryptoDataByte.getType();

        priKeyX = cryptoDataByte.getPriKeyA();
        if(priKeyX==null){
            cryptoDataByte.setCodeMessage(CodeMessage.Code1033MissPriKey);
            return cryptoDataByte;
        }

        pubKeyY = cryptoDataByte.getPubKeyB();
        if(pubKeyY==null) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code1001PubKeyMissed);
            return cryptoDataByte;
        }

        byte[] iv;
        if(cryptoDataByte.getIv()!=null){
            iv = cryptoDataByte.getIv();
        }else {
            iv = BytesUtils.getRandomBytes(16);
            cryptoDataByte.setIv(iv);
        }

        byte[] symKey;
        switch (algorithmId) {
            case EccAes256K1P7_No1_NrC7 -> {
                symKey = EccAes256K1P7.asyKeyToSymKey(priKeyX, pubKeyY,cryptoDataByte.getIv());
                cryptoDataByte.setSymKey(symKey);
                EccAes256K1P7 ecc = new EccAes256K1P7();
                ecc.aesEncrypt(cryptoDataByte);
            }
            default -> {
                symKey = Ecc256K1.asyKeyToSymKey(priKeyX, pubKeyY, iv);
                cryptoDataByte.setSymKey(symKey);
                encryptStreamBySymKey(is,os,symKey,iv,cryptoDataByte);
            }
        }

        cryptoDataByte.setAlg(algorithmId);

        cryptoDataByte.setType(type);

        cryptoDataByte.set0CodeMessage();

        return cryptoDataByte;
    }

    public void checkKeysMakeType(byte[] pubKeyB, byte[] priKeyA, CryptoDataByte cryptoDataByte) {
        byte[] pubKeyA;

        if(priKeyA !=null || cryptoDataByte.getPriKeyA()!=null){
            if(cryptoDataByte.getPriKeyA()==null)
                cryptoDataByte.setPriKeyA(priKeyA);
            if(pubKeyB!=null)
                cryptoDataByte.setPubKeyB(pubKeyB);
        }else {
            cryptoDataByte.setType(EncryptType.AsyOneWay);
            ECKey ecKey = new ECKey();
            priKeyA = ecKey.getPrivKeyBytes();
            pubKeyA = ecKey.getPubKey();
            cryptoDataByte.setPubKeyA(pubKeyA);
            cryptoDataByte.setPriKeyA(priKeyA);
            if(pubKeyB!=null)
                cryptoDataByte.setPubKeyB(pubKeyB);
        }
        if(cryptoDataByte.getPubKeyA()==null){
            pubKeyA =KeyTools.priKeyToPubKey(priKeyA);
            cryptoDataByte.setPubKeyA(pubKeyA);
        }
    }


    public static byte[] passwordToSymKey(char[] password, byte[] iv) {
        byte[] passwordBytes = BytesUtils.charArrayToByteArray(password, StandardCharsets.UTF_8);
        return Decryptor.sha256(BytesUtils.addByteArray(Decryptor.sha256(passwordBytes), iv));
    }
    public static byte[] sha512(byte[] b) {
        return Hashing.sha512().hashBytes(b).asBytes();
    }
    public AlgorithmId getAlgorithmType() {
        return algorithmId;
    }

    public void setAlgorithmType(AlgorithmId algorithmId) {
        this.algorithmId = algorithmId;
    }
}
