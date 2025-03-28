package crypto;

import com.google.common.hash.Hashing;

import constants.CodeMessage;
import constants.Constants;
import crypto.Algorithm.AesCbc256;
import crypto.Algorithm.Bitcore;
import crypto.Algorithm.Ecc256K1;
import crypto.old.EccAes256K1P7;
import fcData.AlgorithmId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.BytesUtils;
import utils.FileUtils;
import utils.Hex;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.*;
import java.util.Arrays;
import java.util.HexFormat;

public class Decryptor {

    protected static final Logger log = LoggerFactory.getLogger(Decryptor.class);

    public Decryptor() {}

    public static byte[] decryptPriKey(String userPriKeyCipher, byte[] symKey) {
        CryptoDataByte cryptoResult = new Decryptor().decryptJsonBySymKey(userPriKeyCipher, symKey);
        if (cryptoResult.getCode() != 0) {
            cryptoResult.printCodeMessage();
            return null;
        }
        return cryptoResult.getData();
    }

    @org.jetbrains.annotations.Nullable
    public static String decryptFile(String path, String gotFile,byte[]symKey,String priKeyCipher) {
        CryptoDataByte cryptoDataByte = new Decryptor().decryptJsonBySymKey(priKeyCipher,symKey);
        if(cryptoDataByte.getCode()!=0){
            log.debug("Failed to decrypt the user priKey.");
            log.debug(cryptoDataByte.getMessage());
            return null;
        }
        byte[] priKey = cryptoDataByte.getData();
        CryptoDataByte cryptoDataByte1 = new Decryptor().decryptFileToDidByAsyOneWay(path, gotFile, path, priKey);
        if(cryptoDataByte1.getCode()!=0){
            log.debug("Failed to decrypt file "+ Path.of(path, gotFile));
            return null;
        }
        BytesUtils.clearByteArray(priKey);
        return Hex.toHex(cryptoDataByte1.getDid());
    }

    public CryptoDataByte decrypt(byte[] bundle, byte[] key){
        return decryptBundleBySymKey(bundle,key);
    }
    public CryptoDataByte decrypt(String cryptoDataJson, byte[] key){
        CryptoDataByte cryptoDataByte;
        try {
            cryptoDataByte = CryptoDataByte.fromJson(cryptoDataJson);
        }catch (Exception e){
            cryptoDataByte= new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code4013BadCipher);
            return cryptoDataByte;
        }
        switch (cryptoDataByte.getType()) {
            case SymKey: cryptoDataByte.setSymKey(key);
            case Password: cryptoDataByte.setPassword(key);
            case AsyOneWay,AsyTwoWay: cryptoDataByte.setPriKeyB(key);
        }
        return decrypt(cryptoDataByte);
    }

    public CryptoDataByte decrypt(CryptoDataByte cryptoDataByte){
        switch (cryptoDataByte.getAlg()){
            case FC_AesCbc256_No1_NrC7 -> decryptBySymKey(cryptoDataByte);
            case FC_EccK1AesCbc256_No1_NrC7 -> decryptByAsyKey(cryptoDataByte);
            case BitCore_EccAes256 -> decryptBitcore(cryptoDataByte);
            default -> cryptoDataByte.setCodeMessage(CodeMessage.Code4002NoSuchAlgorithm);
        }
        return cryptoDataByte;
    }

    private CryptoDataByte decryptBitcore(CryptoDataByte cryptoDataByte) {
        byte[] cipher = Bitcore.fromCryptoDataByte(cryptoDataByte);
        try {
            byte[] data = Bitcore.decrypt(cipher,cryptoDataByte.getPriKeyB());
            cryptoDataByte.setData(data);
            cryptoDataByte.setCodeMessage(CodeMessage.Code0Success);
            return cryptoDataByte;
        } catch (Exception e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code1020OtherError);
            cryptoDataByte.setMessage(e.getMessage());
            return cryptoDataByte;
        }
    }

    public String decryptJsonBySymKey(@NotNull String cryptoDataJson, @NotNull String symKeyHex) {
        byte[] key;
        try {
            key = HexFormat.of().parseHex(symKeyHex);
        }catch (Exception ignore){
            CryptoDataStr cryptoDataStr = new CryptoDataStr();
            cryptoDataStr.setCodeMessage(CodeMessage.Code4007FailedToParseHex);
            return "Error:" + CodeMessage.Code4007FailedToParseHex + "_" + CodeMessage.getMsg(CodeMessage.Code4007FailedToParseHex);
        }

        CryptoDataByte cryptoDataByte = decryptJsonBySymKey(cryptoDataJson,key);
        if(cryptoDataByte.getCode()!=0)
            return "Error:" + cryptoDataByte.getCode() + "_" + CodeMessage.getMsg(cryptoDataByte.getCode());
        return new String(cryptoDataByte.getData());
    }

    // private byte[] decryptBySymKey(@NotNull byte[]cipher, @NotNull byte[]iv, @NotNull byte[]key, @NotNull byte[] sum, byte[] alg) {
    //     try(ByteArrayInputStream bisCipher = new ByteArrayInputStream(cipher);
    //         ByteArrayOutputStream bosData = new ByteArrayOutputStream()) {
    //         CryptoDataByte cryptoDataByte = new CryptoDataByte();
    //         cryptoDataByte.setSymKey(key);
    //         cryptoDataByte.setIv(iv);
    //         cryptoDataByte.setSum(sum);
    //         cryptoDataByte.setCipher(cipher);
    //         if(alg==null)alg = new byte[]{0,0,0,0,0,0};
    //         String algHex = Hex.toHex(alg);
    //         switch (algHex) {
    //             case "000000000000" -> AesCbc256.decryptStream(bisCipher,bosData,cryptoDataByte);
    //             default -> new EccAes256K1P7().decrypt(cryptoDataByte);
    //         }

    //         byte[] data = bosData.toByteArray();
    //         cryptoDataByte.setData(data);
    //         cryptoDataByte.makeDid();
    //         if(!cryptoDataByte.checkSum(alg)) {
    //             cryptoDataByte.setCodeMessage(20);
    //             return null;
    //         }
    //         return cryptoDataByte.getData();
    //     } catch (IOException e) {
    //         System.out.println(e.getMessage());
    //         return null;
    //     }
    // }

    public CryptoDataByte decryptJsonByPassword(@NotNull String cryptoDataJson, @NotNull char[]password) {
        CryptoDataByte cryptoDataByte;
        try {
            cryptoDataByte = CryptoDataByte.fromJson(cryptoDataJson);
        }catch (Exception e){
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code4013BadCipher);
            return cryptoDataByte;
        }
        return decryptByPassword(cryptoDataByte, password);
    }

    @NotNull
    private static CryptoDataByte decryptByPassword(@NotNull CryptoDataByte cryptoDataByte, @NotNull char[] password) {
        byte[] symKey = Encryptor.passwordToSymKey(password, cryptoDataByte.getIv());
        cryptoDataByte.setType(EncryptType.SymKey);
        cryptoDataByte.setSymKey(symKey);
        decryptBySymKey(cryptoDataByte);
        cryptoDataByte.setType(EncryptType.Password);
        return cryptoDataByte;
    }

    public CryptoDataByte decryptJsonBySymKey(@NotNull String cryptoDataJson, @NotNull byte[]symKey) {
        CryptoDataByte cryptoDataByte;
        try {
            cryptoDataByte = CryptoDataByte.fromJson(cryptoDataJson);
        }catch (Exception e){
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code4013BadCipher);
            return cryptoDataByte;
        }
        cryptoDataByte.setSymKey(symKey);
        return decryptBySymKey(cryptoDataByte);
    }

    @NotNull
    private static CryptoDataByte decryptBySymKey(CryptoDataByte cryptoDataByte) {
        switch (cryptoDataByte.getAlg()) {
            case FC_AesCbc256_No1_NrC7 -> AesCbc256.decrypt(cryptoDataByte);
            default -> new EccAes256K1P7().decrypt(cryptoDataByte);
        }
        return cryptoDataByte;
    }

    public CryptoDataByte decryptFileByPassword(@NotNull String cipherFileName, @NotNull String dataFileName, @NotNull char[] password){
        FileUtils.createFileWithDirectories(dataFileName);


        CryptoDataByte cryptoDataByte = decryptFileBySymKey(new File(cipherFileName), new File(dataFileName), null, password);
        cryptoDataByte.setPassword(BytesUtils.charArrayToByteArray(password, StandardCharsets.UTF_8));
        cryptoDataByte.setType(EncryptType.Password);

        return cryptoDataByte;
    }

    public CryptoDataByte decryptFileBySymKey(@NotNull String cipherFileName, @NotNull String dataFileName, @NotNull byte[]key){
        FileUtils.createFileWithDirectories(dataFileName);
        return decryptFileBySymKey(new File(cipherFileName),new File(dataFileName),key, null);
    }
    public static String recoverEncryptedFileName(String encryptedFileName){
        int endIndex1 = encryptedFileName.lastIndexOf('_');
        int endIndex2 = encryptedFileName.lastIndexOf('.');
        String oldSuffix;
        if(endIndex1!=-1) {
            oldSuffix = encryptedFileName.substring(endIndex1 + 1, endIndex2);
            return encryptedFileName.substring(0, endIndex1) + "." + oldSuffix;
        }else return encryptedFileName+ Constants.DOT_DECRYPTED;
    }
    public CryptoDataByte decryptFileBySymKey(@NotNull File cipherFile, @NotNull File dataFile,  byte[]key, char[] password){

        CryptoDataByte cryptoDataByte;
        try(FileOutputStream fos = new FileOutputStream(dataFile);
            FileInputStream fis = new FileInputStream(cipherFile)){

            cryptoDataByte = CryptoDataByte.readFromFileStream(fis);
            if(cryptoDataByte ==null){
                cryptoDataByte = new CryptoDataByte();
                cryptoDataByte.setCodeMessage(CodeMessage.Code4013BadCipher);
                return cryptoDataByte;
            }
            if(cryptoDataByte.getIv()==null){
                cryptoDataByte.setCodeMessage(CodeMessage.Code4009MissingIv);
                return cryptoDataByte;
            }
            if(key==null){
                if(password!=null){
                    key = Encryptor.passwordToSymKey(password,cryptoDataByte.getIv());
                }else {
                    cryptoDataByte.setCodeMessage(CodeMessage.Code4006InvalidKey);
                    return cryptoDataByte;
                }
            }
            cryptoDataByte.setSymKey(key);

            switch (cryptoDataByte.getAlg()){
                case FC_AesCbc256_No1_NrC7 -> AesCbc256.decryptStream(fis,fos,cryptoDataByte);
                default -> AesCbc256.decryptStream(fis,fos,cryptoDataByte);
            }
        } catch (FileNotFoundException e) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code1011DataNotFound);
            return cryptoDataByte;
        } catch (IOException e) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code4001FailedToEncrypt);
            return cryptoDataByte;
        }
        if(cryptoDataByte.getCode()==0) {
            byte[] did;
            try {
                did = Hash.sha256x2Bytes(dataFile);
            } catch (IOException e) {
                cryptoDataByte = new CryptoDataByte();
                cryptoDataByte.setCodeMessage(CodeMessage.Code4001FailedToEncrypt);
                return cryptoDataByte;
            }
            cryptoDataByte.setDid(did);

            cryptoDataByte.checkSum(cryptoDataByte.getAlg());
            return cryptoDataByte;
        }
        return cryptoDataByte;
    }

    public CryptoDataByte decryptStreamBySymKey(@NotNull InputStream inputStream, @NotNull OutputStream outputStream, @NotNull byte[] key, @NotNull byte[] iv, @Nullable CryptoDataByte cryptoDataByte) {
        if(cryptoDataByte==null)cryptoDataByte = new CryptoDataByte();
        if(key!=null)cryptoDataByte.setSymKey(key);
        if(iv!=null)cryptoDataByte.setIv(iv);
        AlgorithmId algorithmId;
        if(cryptoDataByte.getAlg()!= null)algorithmId = cryptoDataByte.getAlg();
        else algorithmId = AlgorithmId.FC_AesCbc256_No1_NrC7;
        switch (algorithmId){
            case FC_AesCbc256_No1_NrC7 -> {
                AesCbc256.decryptStream(inputStream,outputStream,cryptoDataByte);
            }
            default -> AesCbc256.decryptStream(inputStream,outputStream,cryptoDataByte);
        }
        cryptoDataByte.setCodeMessage(1);
        return cryptoDataByte;
    }

    public static void decryptBySymKeyBase(String algo, String transformation, String provider, InputStream inputStream, OutputStream outputStream, @Nullable CryptoDataByte cryptoDataByte) {
        Security.addProvider(new BouncyCastleProvider());
        if(cryptoDataByte==null)return ;
        if(cryptoDataByte.getSymKey()==null){
            cryptoDataByte.setCodeMessage(CodeMessage.Code4006InvalidKey);
            return;
        }
        byte[] key = cryptoDataByte.getSymKey();
        byte[] iv = cryptoDataByte.getIv();

        AlgorithmId alg = null;
        if(cryptoDataByte.getAlg()!=null)
            alg = cryptoDataByte.getAlg();

        if(key.length!=32){
            cryptoDataByte.setCodeMessage(CodeMessage.Code4008WrongKeyLength);
            return;
        }

        SecretKeySpec keySpec = new SecretKeySpec(key, algo);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        try {
            Cipher cipher = Cipher.getInstance(transformation, provider);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            var hashFunction = Hashing.sha256();
            var hasherIn = hashFunction.newHasher();

            try (CipherOutputStream cos = new CipherOutputStream(outputStream, cipher)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    hasherIn.putBytes(buffer, 0, bytesRead);
                    cos.write(buffer, 0, bytesRead);
                }
            }
            cryptoDataByte.setCipherId(sha256(hasherIn.hash().asBytes()));

        } catch (InvalidAlgorithmParameterException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4005InvalidAlgorithmParameter, e.getMessage());
            return;
        } catch (NoSuchPaddingException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4004NoSuchPadding, e.getMessage());
            return;
        } catch (NoSuchAlgorithmException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4002NoSuchAlgorithm, e.getMessage());
            return;
        } catch (IOException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4001FailedToEncrypt, e.getMessage());
            return;
        } catch (NoSuchProviderException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4003NoSuchProvider, e.getMessage());
            return;
        } catch (InvalidKeyException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4006InvalidKey, e.getMessage());
            return;
        }
        
        if(cryptoDataByte.getType()==null)
            cryptoDataByte.setType(EncryptType.SymKey);
        if(cryptoDataByte.getAlg()==null)
            cryptoDataByte.setAlg(alg);
        if(cryptoDataByte.getCode()==null)
            cryptoDataByte.set0CodeMessage();
    }

    public CryptoDataByte decryptBundleBySymKey(@NotNull byte[]bundle, @NotNull byte[]key) {
        CryptoDataByte cryptoDataByte= CryptoDataByte.fromBundle(bundle);
        cryptoDataByte.setSymKey(key);
        decryptBySymKey(cryptoDataByte);
        return cryptoDataByte;
//        byte[] algBytes = new byte[6];
//        byte[] iv = new byte[16];
//        byte[] sum = new byte[4];
//        System.arraycopy(bundle,0,algBytes,0,6);
//        System.arraycopy(bundle,6,iv,0,16);
//        int cipherLength = bundle.length - 6 - 16 - 4;
//        byte[] cipher = new byte[cipherLength];
//        System.arraycopy(bundle,16+6,cipher,0,cipherLength);
//        System.arraycopy(bundle,16+6+cipher.length,sum,0,4);
//        return decryptBySymKey(cipher,iv,key,sum,algBytes);
    }
    public CryptoDataByte decryptBundleByPassword(@NotNull byte[]bundle, @NotNull char[] password) {
        byte[] iv = new byte[16];
        System.arraycopy(bundle,6,iv,0,16);
        byte[]  symKey = Encryptor.passwordToSymKey(password,iv);
        return decryptBundleBySymKey(bundle,symKey);
    }

    public CryptoDataByte decryptBundleByAsyOneWay(@NotNull byte[] bundle, @NotNull byte[]priKey){
        return decryptBundleByAsy(bundle,priKey,null);
    }
    public CryptoDataByte decryptBundleByAsyTwoWay(@NotNull byte[] bundle, @NotNull byte[]priKeyX, @NotNull byte[]pubKeyY){
        return decryptBundleByAsy(bundle,priKeyX,pubKeyY);
    }
    private CryptoDataByte decryptBundleByAsy(@NotNull byte[] bundle, @NotNull byte[]priKeyX, byte[]pubKeyY){

        CryptoDataByte cryptoDataByte;

        boolean isTwoWay = pubKeyY != null;

        cryptoDataByte = CryptoDataByte.fromBundle(bundle);
        cryptoDataByte.setPriKeyB(priKeyX);
//        cryptoDataByte.setAlg(algorithm);
        if(isTwoWay)cryptoDataByte.setPubKeyA(pubKeyY);

        try(ByteArrayInputStream bis = new ByteArrayInputStream(cryptoDataByte.getCipher());
            ByteArrayOutputStream bos = new ByteArrayOutputStream()){
            decryptStreamByAsy(bis,bos,cryptoDataByte);

            if(cryptoDataByte.getCode()==0) {
                cryptoDataByte.setData(bos.toByteArray());
            }
            return cryptoDataByte;
        } catch (IOException e) {
            cryptoDataByte=new CryptoDataByte();
            cryptoDataByte.setCodeMessage(6);
            return cryptoDataByte;
        }
    }
    public CryptoDataByte decryptJsonByAsyOneWay(@NotNull String cryptoDataJson, @NotNull byte[]priKey){
        return decryptJsonByAsy(cryptoDataJson,priKey,null);
    }
    public CryptoDataByte decryptJsonByAsyTwoWay(@NotNull String cryptoDataJson, @NotNull byte[]priKey, @NotNull byte[] pubKey){
        return decryptJsonByAsy(cryptoDataJson,priKey,pubKey);
    }
    private CryptoDataByte decryptJsonByAsy(@NotNull String cryptoDataJson, @NotNull byte[]priKey, byte[] pubKey){
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromJson(cryptoDataJson);
        if(cryptoDataByte.getType().equals(EncryptType.AsyTwoWay)&& pubKey==null){
            cryptoDataByte.setCodeMessage(12);
            return cryptoDataByte;
        }
        cryptoDataByte.setPriKeyA(priKey);
        cryptoDataByte.setPubKeyB(pubKey);
        decryptByAsyKey(cryptoDataByte);
        return cryptoDataByte;
    }

    public void decryptByAsyKey(CryptoDataByte cryptoDataByte){
        byte[] cipher = cryptoDataByte.getCipher();
        if(cipher==null){
            cryptoDataByte.setCodeMessage(17);
            return;
        }
        try(ByteArrayInputStream bis = new ByteArrayInputStream(cipher);
            ByteArrayOutputStream bos = new ByteArrayOutputStream()){
            decryptStreamByAsy(bis,bos,cryptoDataByte);
            cryptoDataByte.setData(bos.toByteArray());
            cryptoDataByte.makeDid();
            cryptoDataByte.setCodeMessage(0);
        } catch (IOException e) {
            cryptoDataByte.setCodeMessage(6);
        }
    }
    public CryptoDataByte decryptFileByAsyOneWay(@NotNull String cipherFile, String dataFile, @NotNull byte[]priKeyX){
        CryptoDataByte cryptoDataByte = decryptFileByAsyTwoWay(null, cipherFile, null, dataFile, priKeyX, null);
        cryptoDataByte.setType(EncryptType.AsyOneWay);
        return cryptoDataByte;
    }
    public CryptoDataByte decryptFileByAsyTwoWay(@NotNull String cipherFile, String dataFile, @NotNull byte[]priKeyX, @NotNull byte[] pubKeyY){
        return decryptFileByAsyTwoWay(null, cipherFile, null, dataFile, priKeyX,pubKeyY);
    }

    public CryptoDataByte decryptFileToDidByAsyOneWay(String srcPath, String srcFileName, String destPath, @NotNull byte[]priKeyX){
        return decryptFileByAsyTwoWay(srcPath, srcFileName, destPath, null, priKeyX,null);
    }
    public CryptoDataByte decryptFileByAsyOneWay(String srcPath, String srcFileName, String destPath, @Nullable String destFileName, @NotNull byte[]priKeyX){
        return decryptFileByAsyTwoWay(srcPath, srcFileName, destPath, destFileName, priKeyX,null);
    }
    public CryptoDataByte decryptFileByAsyTwoWay(String srcPath, @NotNull String srcFileName, String destPath, @Nullable String destFileName, @NotNull byte[]priKeyX, byte[] pubKeyY){
        CryptoDataByte cryptoDataByte;
        String srcFullName = getFileFullName(srcPath, srcFileName);
        String destFullName = null;
        if(destFileName!=null) destFullName = getFileFullName(destPath, destFileName);

        if(srcFullName==null){
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(21);
            return cryptoDataByte;
        }

        String tempDestFileName;
        String destFileForFos;
        if(destFullName!=null)
            destFileForFos=destFullName;
        else {
            tempDestFileName = FileUtils.getTempFileName();
            destFileForFos = tempDestFileName;
        }
        FileUtils.createFileWithDirectories(destFileForFos);

        try(FileOutputStream fos = new FileOutputStream(destFileForFos);
            FileInputStream fis = new FileInputStream(srcFullName)){
            cryptoDataByte = CryptoDataByte.readFromFileStream(fis);
            if(cryptoDataByte ==null){
                cryptoDataByte = new CryptoDataByte();
                cryptoDataByte.setCodeMessage(8);
                return cryptoDataByte;
            }

            if(cryptoDataByte.getIv()==null){
                cryptoDataByte.setCodeMessage(13);
                return cryptoDataByte;
            }
            if(priKeyX==null){
                cryptoDataByte.setCodeMessage(15);
                return cryptoDataByte;
            }
            if(cryptoDataByte.getPubKeyA()!=null)
                cryptoDataByte.setPriKeyB(priKeyX);
            else {
                cryptoDataByte.setPriKeyB(priKeyX);
                cryptoDataByte.setPubKeyA(pubKeyY);
            }

            decryptStreamByAsy(fis,fos,cryptoDataByte);

            byte[] did = Hash.sha256x2Bytes(new File(destFileForFos));
            cryptoDataByte.setDid(did);
            if(!cryptoDataByte.checkSum())cryptoDataByte.setCodeMessage(20);


            String didHex = Hex.toHex(did);

//            Path destPathForFos;
            if(destFileName==null){
                if(destPath!=null) Files.move(Path.of(destFileForFos),Path.of(destPath,didHex), StandardCopyOption.REPLACE_EXISTING);
                else Files.move(Path.of(destFileForFos),Path.of(didHex), StandardCopyOption.REPLACE_EXISTING);
            } else {
                if (destPath != null)
                    Files.move(Path.of(destPath, destFileForFos), Path.of(destPath, destFileName), StandardCopyOption.REPLACE_EXISTING);
                else Files.move(Path.of(destFileForFos), Path.of(destFileName), StandardCopyOption.REPLACE_EXISTING);
            }

            return cryptoDataByte;

        } catch (FileNotFoundException e) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(11);
            return cryptoDataByte;
        } catch (IOException e) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(6);
            return cryptoDataByte;
        }
    }

    @org.jetbrains.annotations.Nullable
    private String getFileFullName(String path, String fileName) {
        String destFullName;
        if(fileName !=null){
            if(path ==null)destFullName= fileName;
            else destFullName= Paths.get(path, fileName).toString();
        }else destFullName=null;
        return destFullName;
    }
    public void decryptStreamByAsy(InputStream is, OutputStream os, CryptoDataByte cryptoDataByte){
        byte[] priKeyX;
        byte[] pubKeyY;
        byte[] iv = cryptoDataByte.getIv();

        byte[] priKeyA =cryptoDataByte.getPriKeyA();
        byte[] priKeyB = cryptoDataByte.getPriKeyB();
        byte[] pubKeyA = cryptoDataByte.getPubKeyA();
        byte[] pubKeyB = cryptoDataByte.getPubKeyB();

        if(priKeyA!=null && pubKeyB !=null){
            priKeyX = priKeyA;
            pubKeyY = pubKeyB;
        }else if(priKeyB!=null && pubKeyA!=null){
            priKeyX = priKeyB;
            pubKeyY = pubKeyA;
        }else if(priKeyA!=null && pubKeyA!=null){
            byte[] pubKey = KeyTools.priKeyToPubKey(priKeyA);
            if(!Arrays.equals(pubKey,pubKeyA)){
                priKeyX = priKeyA;
                pubKeyY = pubKeyA;
            }else {
                cryptoDataByte.setCodeMessage(19);
                return;
            }
        }
        else if(priKeyB!=null && pubKeyB!=null){
            byte[] pubKey = KeyTools.priKeyToPubKey(priKeyB);
            if(!Arrays.equals(pubKey,pubKeyA)){
                priKeyX = priKeyB;
                pubKeyY = pubKeyB;
            }else {
                cryptoDataByte.setCodeMessage(19);
                return;
            }
        }
        else {
            cryptoDataByte.setCodeMessage(19);
            return;
        }

        if(cryptoDataByte.getIv()==null) {
            cryptoDataByte.setCodeMessage(13);
            return;
        }

        if(cryptoDataByte.getSum()==null) {
            cryptoDataByte.setCodeMessage(14);
            return;
        }

        EncryptType type = cryptoDataByte.getType();
        AlgorithmId algo = cryptoDataByte.getAlg();
        if(algo==null){
            algo = AlgorithmId.EccAes256K1P7_No1_NrC7;
            cryptoDataByte.setAlg(algo);
        }

        byte[] symKey;
        switch (algo){
            case FC_EccK1AesCbc256_No1_NrC7 -> {
                symKey = Ecc256K1.asyKeyToSymKey(priKeyX,pubKeyY, iv);
                cryptoDataByte.setSymKey(symKey);
                cryptoDataByte.setType(EncryptType.SymKey);
                cryptoDataByte.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7);
                AesCbc256.decryptStream(is,os,cryptoDataByte);
            }
            default -> {
                symKey = EccAes256K1P7.asyKeyToSymKey(priKeyX,pubKeyY,iv);
                cryptoDataByte.setSymKey(symKey);
                cryptoDataByte.setType(EncryptType.SymKey);
                cryptoDataByte.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7);
                AesCbc256.decryptStream(is,os,cryptoDataByte);
            }
        }

        cryptoDataByte.setAlg(algo);
        cryptoDataByte.setType(type);
    }
    public CryptoDataByte decryptStreamByAsy(InputStream is, OutputStream os, byte[]priKeyX, byte[]pubKeyY, byte[] iv, byte[] sum){
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setPriKeyA(priKeyX);
        cryptoDataByte.setPubKeyB(pubKeyY);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.setSum(sum);

        decryptStreamByAsy(is,os,cryptoDataByte);
        return cryptoDataByte;
    }
    public static void checkSum(CryptoDataByte cryptoDataByte) {
        byte[] newSum = CryptoDataByte.makeSum4(cryptoDataByte.getSymKey(), cryptoDataByte.getIv(), cryptoDataByte.getDid());
        if(cryptoDataByte.getSum()!=null && !Arrays.equals(newSum, cryptoDataByte.getSum())){
            cryptoDataByte.setCodeMessage(20);
        }else cryptoDataByte.set0CodeMessage();
    }

    public static byte[] sha256(byte[] b) {
        return Hashing.sha256().hashBytes(b).asBytes();
    }

}
