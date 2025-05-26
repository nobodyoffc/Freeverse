package core.crypto;
/*
 * There are 4 types of encrypting and decrypting:
 * 1. Symkey: Encrypt or decrypt by a 32 bytes symmetric key.
 * 2. Password: Encrypt or decrypt by a UTF-8 password which no longer than 64 bytes.
 * 3. AsyOneWay: Encrypt by the public key B. A random key pair B will be generate and the new public key will be given in the encrypting result. When decrypting, only the private key B is required.
 * 4. AsyTwoWay: Encrypt by the public key of B and the private of key A. You can decrypt it with prikeyB and pubkeyA, or, with prikeyA and pubkeyB.
 *  The type of Symkey is the base method. When encrypting or decrypting with the other 3 type method, a symkey will be calculated at first and then be used to encrypt or to decrypt. You can get the symkey if you need.
 */

import com.google.common.hash.Hashing;

import constants.CodeMessage;
import constants.Constants;
import core.crypto.old.EccAes256K1P7;
import core.crypto.Algorithm.AesCbc256;
import core.crypto.Algorithm.Ecc256K1;
import core.crypto.Algorithm.aesCbc256.CipherInputStreamWithHash;
import data.fcData.AlgorithmId;
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
import java.util.Objects;

import static data.fcData.AlgorithmId.*;

public class Encryptor {
    AlgorithmId algorithmId;
    protected static final Logger log = LoggerFactory.getLogger(Encryptor.class);

    public Encryptor() {
        this.algorithmId = FC_AesCbc256_No1_NrC7;
    }

    public Encryptor(AlgorithmId algorithmId) {
        this.algorithmId = algorithmId;
    }

    public static String encryptFile(String fileName, String pubkeyHex) {

        byte[] pubkey = Hex.fromHex(pubkeyHex);
        Encryptor encryptor = new Encryptor(FC_EccK1AesCbc256_No1_NrC7);
        String tempFileName = FileUtils.getTempFileName();
        CryptoDataByte result1 = encryptor.encryptFileByAsyOneWay(fileName, tempFileName, pubkey);
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

    public static String encryptBySymkeyToJson(byte[] data, byte[]symkey) {
        Encryptor encryptor = new Encryptor(FC_AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte = encryptor.encryptBySymkey(data,symkey);
        if(cryptoDataByte.getCode()!=0)return null;
        return cryptoDataByte.toJson();
    }

    public CryptoDataByte encryptByPassword(@NotNull byte[] msg, @NotNull char[] password){
        byte[] iv = BytesUtils.getRandomBytes(16);
        byte[] symkey = passwordToSymkey(password, iv);
        CryptoDataByte cryptoDataByte = encryptBySymkey(msg,symkey,iv);
        cryptoDataByte.setType(EncryptType.Password);
        return cryptoDataByte;
    }
    public CryptoDataByte encryptStrByPassword(@NotNull String msgStr, @NotNull char[] password){
        byte[] msg = msgStr.getBytes(StandardCharsets.UTF_8);
        return encryptByPassword(msg,password);
    }
    public String encryptStrToJsonBySymkey(@NotNull String msgStr, @NotNull String symkeyHex){
        byte[] msg = msgStr.getBytes(StandardCharsets.UTF_8);
        byte[] key;
        try {
            key = HexFormat.of().parseHex(symkeyHex);
        }catch (Exception ignore){
            CryptoDataStr cryptoDataStr = new CryptoDataStr();
            cryptoDataStr.setCodeMessage(CodeMessage.Code4007FailedToParseHex);
            return cryptoDataStr.toNiceJson();
        }

        return encryptToJsonBySymkey(msg,key);
    }
    public String encryptToJsonBySymkey(@NotNull byte[] msg, @NotNull byte[] key){
        byte[] iv = BytesUtils.getRandomBytes(16);
        CryptoDataByte cryptoDataByte = encryptBySymkey(msg,key, iv);
        return cryptoDataByte.toNiceJson();
    }
    public CryptoDataByte encryptFileByPassword(@NotNull String dataFileName, @NotNull String cipherFileName, @NotNull char[]password){
        FileUtils.createFileWithDirectories(cipherFileName);
        byte[] iv = BytesUtils.getRandomBytes(16);
        byte[] key = passwordToSymkey(password, iv);


        CryptoDataByte cryptoDataByte = encryptFileBySymkey(dataFileName,cipherFileName,key,iv);
//        cryptoDataByte.setSymkey(key);
        cryptoDataByte.setType(EncryptType.Password);

        return cryptoDataByte;
    }
    public CryptoDataByte encryptFileBySymkey(@NotNull String dataFileName, @NotNull String cipherFileName, @NotNull byte[]key){
        return encryptFileBySymkey(dataFileName,cipherFileName,key,null);
    }
    public CryptoDataByte encryptFileBySymkey(@NotNull String dataFileName, @NotNull String cipherFileName, @NotNull byte[]key, byte[] iv){
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        if(iv==null)iv = BytesUtils.getRandomBytes(16);
        cryptoDataByte.setType(EncryptType.Symkey);
        cryptoDataByte.setAlg(algorithmId);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.setSymkey(key);

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

    public byte[] encryptStrToBundleBySymkey(@NotNull String msgStr, @NotNull String keyHex){
        byte[] msg = msgStr.getBytes(StandardCharsets.UTF_8);
        byte[] key;
        try {
            key = HexFormat.of().parseHex(keyHex);
        }catch (Exception ignore){
            CryptoDataStr cryptoDataStr = new CryptoDataStr();
            cryptoDataStr.setCodeMessage(CodeMessage.Code4007FailedToParseHex);
            return null;
        }
        return encryptToBundleBySymkey(msg,key);
    }
    public byte[] encryptToBundleBySymkey(@NotNull byte[] msg, @NotNull byte[] key){
        byte[] iv = BytesUtils.getRandomBytes(16);
        CryptoDataByte cryptoDataByte = encryptBySymkey(msg,key, iv);
        if(cryptoDataByte.getCode()!=0)return null;
        return cryptoDataByte.toBundle();
    }
    public byte[] encryptToBundleByPassword(@NotNull byte[] msg, @NotNull char[] password){
        byte[] iv = BytesUtils.getRandomBytes(16);
        byte[] symkey = Encryptor.passwordToSymkey(password,iv);
        CryptoDataByte cryptoDataByte = encryptBySymkey(msg,symkey, iv);
        if(cryptoDataByte.getCode()!=0)return null;
        return cryptoDataByte.toBundle();
    }

    public CryptoDataByte encryptBySymkey(@NotNull byte[] msg, @NotNull byte[] symkey){
        byte[] iv = BytesUtils.getRandomBytes(16);
        return encryptBySymkey(msg,symkey,iv,null);
    }
    public CryptoDataByte encryptBySymkey(@NotNull byte[] msg, @NotNull byte[] symkey, byte[] iv){
        return encryptBySymkey(msg,symkey,iv,null);
    }

    private CryptoDataByte encryptBySymkey(@NotNull byte[] msg, @Nullable  byte[] key, @Nullable  byte[] iv, @Nullable CryptoDataByte cryptoDataByte){
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

            if(cryptoDataByte.getKeyName() == null)
                cryptoDataByte.makeKeyName(key);

            byte[] cipher = bosCipher.toByteArray();

            cryptoDataByte.setCipher(cipher);
            cryptoDataByte.set0CodeMessage();

            return cryptoDataByte;
        } catch (IOException e) {
            if(cryptoDataByte==null)cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code4001FailedToEncrypt);
            cryptoDataByte.setType(EncryptType.Symkey);
            return cryptoDataByte;
        }
    }



    public CryptoDataByte encryptStreamBySymkey(@NotNull InputStream inputStream, @NotNull OutputStream outputStream, byte[] key, byte[] iv, CryptoDataByte cryptoDataByte) {
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

    public static CryptoDataByte encryptBySymkeyBase(String algo, String transformation, String provider, InputStream inputStream, OutputStream outputStream, CryptoDataByte cryptoDataByte) {
        AlgorithmId alg = null;
        if(cryptoDataByte.getAlg()!=null){
            alg = cryptoDataByte.getAlg();
        }

        byte[] key= cryptoDataByte.getSymkey();
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
            cryptoDataByte.setType(EncryptType.Symkey);
        cryptoDataByte.setSymkey(key);
        cryptoDataByte.setAlg(alg);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.makeSum4();
        cryptoDataByte.set0CodeMessage();
        return cryptoDataByte;
    }

    public CryptoDataByte encryptStrByAsyOneWay(@NotNull String data,@NotNull  String pubkeyBHex){
        return encryptByAsyOneWay(data.getBytes(), Hex.fromHex(pubkeyBHex));
    }

    public CryptoDataByte encryptByAsyOneWay(@NotNull byte[] data, @NotNull byte[] pubkeyB){
        byte[] prikeyA;
        ECKey ecKey = new ECKey();
        prikeyA = ecKey.getPrivKeyBytes();
        return encryptByAsy(data, prikeyA, pubkeyB, EncryptType.AsyOneWay);
    }
    public byte[] encryptByAsyOneWayToBundle(@NotNull byte[] data, @NotNull byte[] pubkeyB){
        CryptoDataByte cryptoDataByte = encryptByAsyOneWay(data,pubkeyB);
        return cryptoDataByte.toBundle();
    }

    public CryptoDataByte encryptByAsyTwoWay(@NotNull byte[] data, @NotNull byte[]prikeyA, @NotNull byte[] pubkeyB){
        CryptoDataByte cryptoDataByte = encryptByAsy(data, prikeyA, pubkeyB, EncryptType.AsyTwoWay);
        cryptoDataByte.setPubkeyA(KeyTools.prikeyToPubkey(prikeyA));
        return cryptoDataByte;
    }
    public byte[] encryptByAsyTwoWayToBundle(@NotNull byte[] data,@NotNull byte[]prikeyA, @NotNull byte[] pubkeyB){
        CryptoDataByte cryptoDataByte = encryptByAsyTwoWay(data,prikeyA,pubkeyB);
        return cryptoDataByte.toBundle();
    }
    private CryptoDataByte encryptByAsy(@NotNull byte[] data, byte[]prikeyA, byte[] pubkeyB, EncryptType encryptType){
        CryptoDataByte cryptoDataByte;
        if(prikeyA==null){
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code1033MissPrikey);
            return cryptoDataByte;
        }
        if(pubkeyB==null){
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code1001PubkeyMissed);
            return cryptoDataByte;
        }

        try(ByteArrayInputStream bis = new ByteArrayInputStream(data);
            ByteArrayOutputStream bos = new ByteArrayOutputStream()){
            cryptoDataByte
                    = encryptStreamByAsyTwoWay(bis, bos, prikeyA, pubkeyB);
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

    public CryptoDataByte encryptFileByAsyOneWay(@NotNull String dataFileName, @NotNull String cipherFileName, @NotNull byte[] pubkeyB){
        return encryptFileByAsy(dataFileName, cipherFileName, pubkeyB, null);
    }
    public CryptoDataByte encryptFileByAsyTwoWay(@NotNull String dataFileName, @NotNull String cipherFileName, @NotNull byte[] pubkeyB,@NotNull byte[]prikeyA){
        return encryptFileByAsy(dataFileName, cipherFileName, pubkeyB, prikeyA);
    }
    private CryptoDataByte encryptFileByAsy(@NotNull String dataFileName, @NotNull String cipherFileName, @NotNull byte[] pubkeyB,byte[]prikeyA){
        FileUtils.createFileWithDirectories(cipherFileName);

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setAlg(algorithmId);

        checkKeysMakeType(pubkeyB, prikeyA, cryptoDataByte);

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
            if(prikeyA==null)
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
    public CryptoDataByte encryptStreamByAsyTwoWay(@NotNull InputStream is, @NotNull OutputStream os, @NotNull byte[]prikeyX, @NotNull byte[]pubkeyY){
        return encryptStreamByAsy(is,os,prikeyX,pubkeyY,null);
    }
    public CryptoDataByte encryptStreamByAsyOneWay(@NotNull InputStream is, @NotNull OutputStream os, @NotNull byte[]pubkeyY){
        return encryptStreamByAsy(is,os,null,pubkeyY,null);
    }

    public CryptoDataByte encryptStreamByAsy(@NotNull InputStream is, @NotNull OutputStream os,@NotNull CryptoDataByte cryptoDataByte){
        return encryptStreamByAsy(is,os,null,null,cryptoDataByte);
    }
    private CryptoDataByte encryptStreamByAsy(@NotNull InputStream is, @NotNull OutputStream os, byte[]prikeyX, byte[]pubkeyY, CryptoDataByte cryptoDataByte){
        if(cryptoDataByte==null)cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setAlg(algorithmId);
        checkKeysMakeType(pubkeyY, prikeyX, cryptoDataByte);

        EncryptType type = cryptoDataByte.getType();

        prikeyX = cryptoDataByte.getPrikeyA();
        if(prikeyX==null){
            cryptoDataByte.setCodeMessage(CodeMessage.Code1033MissPrikey);
            return cryptoDataByte;
        }

        pubkeyY = cryptoDataByte.getPubkeyB();
        if(pubkeyY==null) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code1001PubkeyMissed);
            return cryptoDataByte;
        }

        byte[] iv;
        if(cryptoDataByte.getIv()!=null){
            iv = cryptoDataByte.getIv();
        }else {
            iv = BytesUtils.getRandomBytes(16);
            cryptoDataByte.setIv(iv);
        }

        byte[] symkey;
        if (Objects.requireNonNull(algorithmId) == EccAes256K1P7_No1_NrC7) {
            symkey = EccAes256K1P7.asyKeyToSymkey(prikeyX, pubkeyY, cryptoDataByte.getIv());
            cryptoDataByte.setSymkey(symkey);
            EccAes256K1P7 ecc = new EccAes256K1P7();
            ecc.aesEncrypt(cryptoDataByte);
        } else {
            symkey = Ecc256K1.asyKeyToSymkey(prikeyX, pubkeyY, iv);
            cryptoDataByte.setSymkey(symkey);
            encryptStreamBySymkey(is, os, symkey, iv, cryptoDataByte);
        }

        cryptoDataByte.setAlg(algorithmId);

        cryptoDataByte.setType(type);

        cryptoDataByte.set0CodeMessage();

        return cryptoDataByte;
    }

    public void checkKeysMakeType(byte[] pubkeyB, byte[] prikeyA, CryptoDataByte cryptoDataByte) {
        byte[] pubkeyA;

        if(prikeyA !=null || cryptoDataByte.getPrikeyA()!=null){
            if(cryptoDataByte.getPrikeyA()==null)
                cryptoDataByte.setPrikeyA(prikeyA);
            if(pubkeyB!=null)
                cryptoDataByte.setPubkeyB(pubkeyB);
        }else {
            cryptoDataByte.setType(EncryptType.AsyOneWay);
            ECKey ecKey = new ECKey();
            prikeyA = ecKey.getPrivKeyBytes();
            pubkeyA = ecKey.getPubKey();
            cryptoDataByte.setPubkeyA(pubkeyA);
            cryptoDataByte.setPrikeyA(prikeyA);
            if(pubkeyB!=null)
                cryptoDataByte.setPubkeyB(pubkeyB);
        }
        if(cryptoDataByte.getPubkeyA()==null){
            pubkeyA =KeyTools.prikeyToPubkey(prikeyA);
            cryptoDataByte.setPubkeyA(pubkeyA);
        }
    }


    public static byte[] passwordToSymkey(char[] password, byte[] iv) {
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
