package core.crypto;

import com.google.common.hash.Hashing;

import constants.CodeMessage;
import constants.Constants;
import core.crypto.Algorithm.*;
import core.crypto.old.EccAes256K1P7;
import data.fcData.AlgorithmId;
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
import java.util.Base64;
import java.util.HexFormat;

public class Decryptor {

    protected static final Logger log = LoggerFactory.getLogger(Decryptor.class);

    public Decryptor() {}


    public static CryptoDataByte decryptTry(String cipher, byte[] priKey){
        // Try to decrypt to get other fields like title, type, memo
        Decryptor decryptor = new Decryptor();
        CryptoDataByte cryptoDataByte = null;

        if (cipher.startsWith("A")) {
            cryptoDataByte = decryptBundleTry(cipher,priKey);
        }

        // FC algorithm
        if (cryptoDataByte == null || cryptoDataByte.getCode()!=0 ) {
            try {
                cryptoDataByte = CryptoDataByte.fromJson(cipher);
            } catch (Exception e) {
                cryptoDataByte = new CryptoDataByte();
                cryptoDataByte.setCodeMessage(CodeMessage.Code1020OtherError,e.getMessage());
                return cryptoDataByte;
            }
        }

        cryptoDataByte.setPrikeyB(priKey);
        cryptoDataByte = decryptor.decrypt(cryptoDataByte);

        return cryptoDataByte;
    }


    public static CryptoDataByte decryptBundleTry(String cipher, byte[] priKey) {
        CryptoDataByte cryptoDataByte;
        byte[] cipherBytes;
        try {
            cipherBytes = Base64.getDecoder().decode(cipher);
        } catch (IllegalArgumentException e) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code1029FailedToDecrypt);
            return cryptoDataByte;
        }

        try {
            cryptoDataByte = CryptoDataByte.fromBundle(cipherBytes);

            // Not FC algorithm
            if (cryptoDataByte == null || cryptoDataByte.getCode() != 0) {
                try {
                    cryptoDataByte = new CryptoDataByte();
                    byte[] dataBytes = Bitcore.decrypt(cipherBytes, priKey);
                    if(dataBytes==null){
                        cryptoDataByte.setCodeMessage(CodeMessage.Code1029FailedToDecrypt);
                        return cryptoDataByte;
                    }
                    cryptoDataByte.setCodeMessage(CodeMessage.Code0Success);
                    cryptoDataByte.setData(dataBytes);
                    return cryptoDataByte;
                } catch (Exception e) {
                    cryptoDataByte = new CryptoDataByte();
                    cryptoDataByte.setCodeMessage(CodeMessage.Code1020OtherError,e.getMessage());
                    return cryptoDataByte;
                }
            }
        } catch (Exception e) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code1020OtherError,e.getMessage());
            return cryptoDataByte;
        }
        cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setCodeMessage(CodeMessage.Code1020OtherError);
        return cryptoDataByte;
    }



    public static byte[] decryptPrikey(String userPrikeyCipher, byte[] symkey) {
        CryptoDataByte cryptoResult = new Decryptor().decryptJsonBySymkey(userPrikeyCipher, symkey);
        if (cryptoResult.getCode() != 0) {
            cryptoResult.printCodeMessage();
            return null;
        }
        return cryptoResult.getData();
    }

    @org.jetbrains.annotations.Nullable
    public static String decryptFile(String path, String gotFile,byte[]symkey,String prikeyCipher) {
        CryptoDataByte cryptoDataByte = new Decryptor().decryptJsonBySymkey(prikeyCipher,symkey);
        if(cryptoDataByte.getCode()!=0){
            log.debug("Failed to decrypt the user prikey.");
            log.debug(cryptoDataByte.getMessage());
            return null;
        }
        byte[] prikey = cryptoDataByte.getData();
        CryptoDataByte cryptoDataByte1 = new Decryptor().decryptFileToDidByAsyOneWay(path, gotFile, path, prikey);
        if(cryptoDataByte1.getCode()!=0){
            log.debug("Failed to decrypt file "+ Path.of(path, gotFile));
            return null;
        }
        BytesUtils.clearByteArray(prikey);
        return Hex.toHex(cryptoDataByte1.getDid());
    }

    public CryptoDataByte decrypt(byte[] bundle, byte[] key){
        return decryptBundleBySymkey(bundle,key);
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
            case Symkey -> cryptoDataByte.setSymkey(key);
            case Password -> cryptoDataByte.setPassword(key);
            case AsyOneWay, AsyTwoWay -> cryptoDataByte.setPrikeyB(key);
        }
        return decrypt(cryptoDataByte);
    }

    public CryptoDataByte decrypt(CryptoDataByte cryptoDataByte){
        switch (cryptoDataByte.getAlg()){
            case FC_AesCbc256_No1_NrC7, FC_AesGcm256_No1_NrC7, FC_ChaCha20_No1_NrC7 -> decryptBySymkey(cryptoDataByte);
            case FC_EccK1AesCbc256_No1_NrC7, FC_EccK1AesGcm256_No1_NrC7, FC_X25519AesGcm256_No1_NrC7, FC_EccK1ChaCha20_No1_NrC7 -> decryptByAsyKey(cryptoDataByte);
            case BitCore_EccAes256 -> decryptBitcore(cryptoDataByte);
            default -> cryptoDataByte.setCodeMessage(CodeMessage.Code4002NoSuchAlgorithm);
        }
        return cryptoDataByte;
    }

    private CryptoDataByte decryptBitcore(CryptoDataByte cryptoDataByte) {
        byte[] cipher = Bitcore.fromCryptoDataByte(cryptoDataByte);
        try {
            byte[] data = Bitcore.decrypt(cipher,cryptoDataByte.getPrikeyB());
            cryptoDataByte.setData(data);
            cryptoDataByte.setCodeMessage(CodeMessage.Code0Success);
            return cryptoDataByte;
        } catch (Exception e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code1020OtherError);
            cryptoDataByte.setMessage(e.getMessage());
            return cryptoDataByte;
        }
    }

    public String decryptJsonBySymkey(@NotNull String cryptoDataJson, @NotNull String symkeyHex) {
        byte[] key;
        try {
            key = HexFormat.of().parseHex(symkeyHex);
        }catch (Exception ignore){
            CryptoDataStr cryptoDataStr = new CryptoDataStr();
            cryptoDataStr.setCodeMessage(CodeMessage.Code4007FailedToParseHex);
            return "Error:" + CodeMessage.Code4007FailedToParseHex + "_" + CodeMessage.getMsg(CodeMessage.Code4007FailedToParseHex);
        }

        CryptoDataByte cryptoDataByte = decryptJsonBySymkey(cryptoDataJson,key);
        if(cryptoDataByte.getCode()!=0)
            return "Error:" + cryptoDataByte.getCode() + "_" + CodeMessage.getMsg(cryptoDataByte.getCode());
        return new String(cryptoDataByte.getData());
    }

    // private byte[] decryptBySymkey(@NotNull byte[]cipher, @NotNull byte[]iv, @NotNull byte[]key, @NotNull byte[] sum, byte[] alg) {
    //     try(ByteArrayInputStream bisCipher = new ByteArrayInputStream(cipher);
    //         ByteArrayOutputStream bosData = new ByteArrayOutputStream()) {
    //         CryptoDataByte cryptoDataByte = new CryptoDataByte();
    //         cryptoDataByte.setSymkey(key);
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
        byte[] symkey = Encryptor.passwordToSymkey(password, cryptoDataByte.getIv());
        cryptoDataByte.setType(EncryptType.Symkey);
        cryptoDataByte.setSymkey(symkey);
        decryptBySymkey(cryptoDataByte);
        cryptoDataByte.setType(EncryptType.Password);
        return cryptoDataByte;
    }

    public CryptoDataByte decryptJsonBySymkey(@NotNull String cryptoDataJson, @NotNull byte[]symkey) {
        CryptoDataByte cryptoDataByte;
        try {
            cryptoDataByte = CryptoDataByte.fromJson(cryptoDataJson);
        }catch (Exception e){
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code4013BadCipher);
            return cryptoDataByte;
        }
        cryptoDataByte.setSymkey(symkey);
        return decryptBySymkey(cryptoDataByte);
    }

    @NotNull
    private static CryptoDataByte decryptBySymkey(CryptoDataByte cryptoDataByte) {
        switch (cryptoDataByte.getAlg()) {
            case FC_AesCbc256_No1_NrC7 -> AesCbc256.decrypt(cryptoDataByte);
            case FC_AesGcm256_No1_NrC7 -> AesGcm256.decrypt(cryptoDataByte);
            case FC_ChaCha20_No1_NrC7 -> ChaCha20.decrypt(cryptoDataByte);
            default -> new EccAes256K1P7().decrypt(cryptoDataByte);
        }
        return cryptoDataByte;
    }

    public CryptoDataByte decryptFileByPassword(@NotNull String cipherFileName, @NotNull String dataFileName, @NotNull char[] password){
        FileUtils.createFileWithDirectories(dataFileName);


        CryptoDataByte cryptoDataByte = decryptFileBySymkey(new File(cipherFileName), new File(dataFileName), null, password);
        cryptoDataByte.setPassword(BytesUtils.charArrayToByteArray(password, StandardCharsets.UTF_8));
        cryptoDataByte.setType(EncryptType.Password);

        return cryptoDataByte;
    }

    public CryptoDataByte decryptFileBySymkey(@NotNull String cipherFileName, @NotNull String dataFileName, @NotNull byte[]key){
        FileUtils.createFileWithDirectories(dataFileName);
        return decryptFileBySymkey(new File(cipherFileName),new File(dataFileName),key, null);
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
    public CryptoDataByte decryptFileBySymkey(@NotNull File cipherFile, @NotNull File dataFile,  byte[]key, char[] password){

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
                    key = Encryptor.passwordToSymkey(password,cryptoDataByte.getIv());
                }else {
                    cryptoDataByte.setCodeMessage(CodeMessage.Code4006InvalidKey);
                    return cryptoDataByte;
                }
            }
            cryptoDataByte.setSymkey(key);

            switch (cryptoDataByte.getAlg()){
                case FC_AesCbc256_No1_NrC7 -> AesCbc256.decryptStream(fis,fos,cryptoDataByte);
                case FC_AesGcm256_No1_NrC7 -> AesGcm256.decryptStream(fis,fos,cryptoDataByte);
                case FC_ChaCha20_No1_NrC7 -> ChaCha20.decryptStream(fis,fos,cryptoDataByte);
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

            // Skip sum check for AES-GCM algorithms (they have built-in authentication)
            // ChaCha20 requires sum check as it doesn't have built-in authentication
            AlgorithmId alg = cryptoDataByte.getAlg();
            if(alg != AlgorithmId.FC_AesGcm256_No1_NrC7 &&
               alg != AlgorithmId.FC_EccK1AesGcm256_No1_NrC7 &&
               alg != AlgorithmId.FC_X25519AesGcm256_No1_NrC7) {
                cryptoDataByte.checkSum(cryptoDataByte.getAlg());
            }
            return cryptoDataByte;
        }
        return cryptoDataByte;
    }

    public CryptoDataByte decryptStreamBySymkey(@NotNull InputStream inputStream, @NotNull OutputStream outputStream, @NotNull byte[] key, @NotNull byte[] iv, @Nullable CryptoDataByte cryptoDataByte) {
        if(cryptoDataByte==null)cryptoDataByte = new CryptoDataByte();
        if(key!=null)cryptoDataByte.setSymkey(key);
        if(iv!=null)cryptoDataByte.setIv(iv);
        AlgorithmId algorithmId;
        if(cryptoDataByte.getAlg()!= null)algorithmId = cryptoDataByte.getAlg();
        else algorithmId = AlgorithmId.FC_AesCbc256_No1_NrC7;
        switch (algorithmId){
            case FC_AesCbc256_No1_NrC7 -> {
                AesCbc256.decryptStream(inputStream,outputStream,cryptoDataByte);
            }
            case FC_AesGcm256_No1_NrC7 -> {
                AesGcm256.decryptStream(inputStream,outputStream,cryptoDataByte);
            }
            case FC_ChaCha20_No1_NrC7 -> {
                ChaCha20.decryptStream(inputStream,outputStream,cryptoDataByte);
            }
            default -> AesCbc256.decryptStream(inputStream,outputStream,cryptoDataByte);
        }
        if(cryptoDataByte.getCode()==null)
            cryptoDataByte.set0CodeMessage();
        return cryptoDataByte;
    }

    public static void decryptBySymkeyBase(String algo, String transformation, String provider, InputStream inputStream, OutputStream outputStream, @Nullable CryptoDataByte cryptoDataByte) {
        Security.addProvider(new BouncyCastleProvider());
        if(cryptoDataByte==null)return ;
        if(cryptoDataByte.getSymkey()==null){
            cryptoDataByte.setCodeMessage(CodeMessage.Code4006InvalidKey);
            return;
        }
        byte[] key = cryptoDataByte.getSymkey();
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
            cryptoDataByte.setType(EncryptType.Symkey);
        if(cryptoDataByte.getAlg()==null)
            cryptoDataByte.setAlg(alg);
        if(cryptoDataByte.getCode()==null)
            cryptoDataByte.set0CodeMessage();
    }

    public CryptoDataByte decryptBundleBySymkey(@NotNull byte[]bundle, @NotNull byte[]key) {
        CryptoDataByte cryptoDataByte= CryptoDataByte.fromBundle(bundle);
        cryptoDataByte.setSymkey(key);
        decryptBySymkey(cryptoDataByte);
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
//        return decryptBySymkey(cipher,iv,key,sum,algBytes);
    }
    public CryptoDataByte decryptBundleByPassword(@NotNull byte[]bundle, @NotNull char[] password) {
        byte[] iv = new byte[16];
        System.arraycopy(bundle,6,iv,0,16);
        byte[]  symkey = Encryptor.passwordToSymkey(password,iv);
        return decryptBundleBySymkey(bundle,symkey);
    }

    public CryptoDataByte decryptBundleByAsyOneWay(@NotNull byte[] bundle, @NotNull byte[]prikey){
        return decryptBundleByAsy(bundle,prikey,null);
    }
    public CryptoDataByte decryptBundleByAsyTwoWay(@NotNull byte[] bundle, @NotNull byte[]prikeyX, @NotNull byte[]pubkeyY){
        return decryptBundleByAsy(bundle,prikeyX,pubkeyY);
    }
    private CryptoDataByte decryptBundleByAsy(@NotNull byte[] bundle, @NotNull byte[]prikeyX, byte[]pubkeyY){

        CryptoDataByte cryptoDataByte;

        boolean isTwoWay = pubkeyY != null;

        cryptoDataByte = CryptoDataByte.fromBundle(bundle);
        cryptoDataByte.setPrikeyB(prikeyX);
//        cryptoDataByte.setAlg(algorithm);
        if(isTwoWay)cryptoDataByte.setPubkeyA(pubkeyY);

        try(ByteArrayInputStream bis = new ByteArrayInputStream(cryptoDataByte.getCipher());
            ByteArrayOutputStream bos = new ByteArrayOutputStream()){
            decryptStreamByAsy(bis,bos,cryptoDataByte);

            if(cryptoDataByte.getCode()==0) {
                cryptoDataByte.setData(bos.toByteArray());
            }
            return cryptoDataByte;
        } catch (IOException e) {
            cryptoDataByte=new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code4001FailedToEncrypt);
            return cryptoDataByte;
        }
    }
    public CryptoDataByte decryptJsonByAsyOneWay(@NotNull String cryptoDataJson, @NotNull byte[]prikey){
        return decryptJsonByAsy(cryptoDataJson,prikey,null);
    }
    public CryptoDataByte decryptJsonByAsyTwoWay(@NotNull String cryptoDataJson, @NotNull byte[]prikey, @NotNull byte[] pubkey){
        return decryptJsonByAsy(cryptoDataJson,prikey,pubkey);
    }
    private CryptoDataByte decryptJsonByAsy(@NotNull String cryptoDataJson, @NotNull byte[]prikey, byte[] pubkey){
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromJson(cryptoDataJson);
        if(cryptoDataByte.getType().equals(EncryptType.AsyTwoWay)&& pubkey==null){
            cryptoDataByte.setCodeMessage(CodeMessage.Code4010KeyPairMismatch);
            return cryptoDataByte;
        }
        cryptoDataByte.setPrikeyA(prikey);
        cryptoDataByte.setPubkeyB(pubkey);
        decryptByAsyKey(cryptoDataByte);
        return cryptoDataByte;
    }

    public void decryptByAsyKey(CryptoDataByte cryptoDataByte){
        byte[] cipher = cryptoDataByte.getCipher();
        if(cipher==null){
            cryptoDataByte.setCodeMessage(CodeMessage.Code4013BadCipher);
            return;
        }
        try(ByteArrayInputStream bis = new ByteArrayInputStream(cipher);
            ByteArrayOutputStream bos = new ByteArrayOutputStream()){
            decryptStreamByAsy(bis,bos,cryptoDataByte);

            // Only set success if no error occurred during decryption
            if (cryptoDataByte.getCode() == null || cryptoDataByte.getCode() == 0) {
                cryptoDataByte.setData(bos.toByteArray());
                cryptoDataByte.makeDid();
                cryptoDataByte.setCodeMessage(CodeMessage.Code0Success);
            }
        } catch (IOException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4001FailedToEncrypt);
        }
    }
    public CryptoDataByte decryptFileByAsyOneWay(@NotNull String cipherFile, String dataFile, @NotNull byte[]prikeyX){
        CryptoDataByte cryptoDataByte = decryptFileByAsyTwoWay(null, cipherFile, null, dataFile, prikeyX, null);
        cryptoDataByte.setType(EncryptType.AsyOneWay);
        return cryptoDataByte;
    }
    public CryptoDataByte decryptFileByAsyTwoWay(@NotNull String cipherFile, String dataFile, @NotNull byte[]prikeyX, @NotNull byte[] pubkeyY){
        return decryptFileByAsyTwoWay(null, cipherFile, null, dataFile, prikeyX,pubkeyY);
    }

    public CryptoDataByte decryptFileToDidByAsyOneWay(String srcPath, String srcFileName, String destPath, @NotNull byte[]prikeyX){
        return decryptFileByAsyTwoWay(srcPath, srcFileName, destPath, null, prikeyX,null);
    }
    public CryptoDataByte decryptFileByAsyOneWay(String srcPath, String srcFileName, String destPath, @Nullable String destFileName, @NotNull byte[]prikeyX){
        return decryptFileByAsyTwoWay(srcPath, srcFileName, destPath, destFileName, prikeyX,null);
    }
    public CryptoDataByte decryptFileByAsyTwoWay(String srcPath, @NotNull String srcFileName, String destPath, @Nullable String destFileName, @NotNull byte[]prikeyX, byte[] pubkeyY){
        CryptoDataByte cryptoDataByte;
        String srcFullName = getFileFullName(srcPath, srcFileName);
        String destFullName = null;
        if(destFileName!=null) destFullName = getFileFullName(destPath, destFileName);

        if(srcFullName==null){
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code1011DataNotFound);
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
                cryptoDataByte.setCodeMessage(CodeMessage.Code4013BadCipher);
                return cryptoDataByte;
            }

            if(cryptoDataByte.getIv()==null){
                cryptoDataByte.setCodeMessage(CodeMessage.Code4009MissingIv);
                return cryptoDataByte;
            }
            if(prikeyX==null){
                cryptoDataByte.setCodeMessage(CodeMessage.Code1033MissPrikey);
                return cryptoDataByte;
            }
            if(cryptoDataByte.getPubkeyA()!=null)
                cryptoDataByte.setPrikeyB(prikeyX);
            else {
                cryptoDataByte.setPrikeyB(prikeyX);
                cryptoDataByte.setPubkeyA(pubkeyY);
            }

            decryptStreamByAsy(fis,fos,cryptoDataByte);

            byte[] did = Hash.sha256x2Bytes(new File(destFileForFos));
            cryptoDataByte.setDid(did);

            // Skip sum check for AES-GCM algorithms (they have built-in authentication)
            // ChaCha20 requires sum check as it doesn't have built-in authentication
            AlgorithmId alg = cryptoDataByte.getAlg();
            if(alg != AlgorithmId.FC_AesGcm256_No1_NrC7 &&
               alg != AlgorithmId.FC_EccK1AesGcm256_No1_NrC7 &&
               alg != AlgorithmId.FC_X25519AesGcm256_No1_NrC7) {
                if(!cryptoDataByte.checkSum())cryptoDataByte.setCodeMessage(CodeMessage.Code4011BadSum);
            }


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
            cryptoDataByte.setCodeMessage(CodeMessage.Code1011DataNotFound);
            return cryptoDataByte;
        } catch (IOException e) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code4001FailedToEncrypt);
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
        byte[] prikeyX;
        byte[] pubkeyY;
        byte[] iv = cryptoDataByte.getIv();

        // Adjust IV length based on algorithm type (for backward compatibility)
        if (iv != null && cryptoDataByte.getAlg() != null) {
            iv = Encryptor.adjustIvLength(iv, cryptoDataByte.getAlg());
            cryptoDataByte.setIv(iv);  // Update the IV in cryptoDataByte for decryption
        }

        byte[] prikeyA =cryptoDataByte.getPrikeyA();
        byte[] prikeyB = cryptoDataByte.getPrikeyB();
        byte[] pubkeyA = cryptoDataByte.getPubkeyA();
        byte[] pubkeyB = cryptoDataByte.getPubkeyB();

        if(prikeyA!=null && pubkeyB !=null){
            prikeyX = prikeyA;
            pubkeyY = pubkeyB;
        }else if(prikeyB!=null && pubkeyA!=null){
            prikeyX = prikeyB;
            pubkeyY = pubkeyA;
        }else if(prikeyA!=null && pubkeyA!=null){
            // Allow self-communication (same key pair)
            // Security is maintained because:
            // 1. Only the private key holder can compute ECDH
            // 2. Random IV ensures different symkey each time
            prikeyX = prikeyA;
            pubkeyY = pubkeyA;
        }
        else if(prikeyB!=null && pubkeyB!=null){
            // Allow self-communication (same key pair)
            prikeyX = prikeyB;
            pubkeyY = pubkeyB;
        }
        else {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4010KeyPairMismatch);
            return;
        }

        if(cryptoDataByte.getIv()==null) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4009MissingIv);
            return;
        }

        EncryptType type = cryptoDataByte.getType();
        AlgorithmId algo = cryptoDataByte.getAlg();

        // Default algorithm if not set
        if(algo==null){
            algo = AlgorithmId.EccAes256K1P7_No1_NrC7;
            cryptoDataByte.setAlg(algo);
        }

        // Only check sum for non-GCM algorithms (GCM has built-in authentication)
        boolean requiresSum = (algo != AlgorithmId.FC_AesGcm256_No1_NrC7 &&
                               algo != AlgorithmId.FC_EccK1AesGcm256_No1_NrC7 &&
                               algo != AlgorithmId.FC_X25519AesGcm256_No1_NrC7);

        if(requiresSum && cryptoDataByte.getSum()==null) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4011BadSum);
            return;
        }

        byte[] symkey;
        switch (algo){
            case FC_EccK1AesCbc256_No1_NrC7 -> {
                symkey = Ecc256K1AesCbc256.getInstance().asyKeyToSymkey(prikeyX,pubkeyY, iv);
                cryptoDataByte.setSymkey(symkey);
                cryptoDataByte.setType(EncryptType.Symkey);
                cryptoDataByte.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7);
                AesCbc256.decryptStream(is,os,cryptoDataByte);
            }
            case FC_EccK1AesGcm256_No1_NrC7 -> {
                try {
                    symkey = Ecc256K1AesGcm256.getInstance().asyKeyToSymkey(prikeyX,pubkeyY, iv);
                } catch (Exception e) {
                    cryptoDataByte.setCode(CodeMessage.Code1020OtherError);
                    cryptoDataByte.setMessage(e.getMessage());
                    return;
                }
                cryptoDataByte.setSymkey(symkey);
                cryptoDataByte.setType(EncryptType.Symkey);
                cryptoDataByte.setAlg(AlgorithmId.FC_AesGcm256_No1_NrC7);
                AesGcm256.decryptStream(is,os,cryptoDataByte);
            }
            case FC_X25519AesGcm256_No1_NrC7 -> {
                try {
                    symkey = X25519AesGcm256.getInstance().asyKeyToSymkey(prikeyX,pubkeyY, iv);
                } catch (Exception e) {
                    cryptoDataByte.setCode(CodeMessage.Code1020OtherError);
                    cryptoDataByte.setMessage(e.getMessage());
                    return;
                }
                cryptoDataByte.setSymkey(symkey);
                cryptoDataByte.setType(EncryptType.Symkey);
                cryptoDataByte.setAlg(AlgorithmId.FC_AesGcm256_No1_NrC7);
                AesGcm256.decryptStream(is,os,cryptoDataByte);
            }
            case FC_EccK1ChaCha20_No1_NrC7 -> {
                try {
                    symkey = Ecc256K1ChaCha20.getInstance().asyKeyToSymkey(prikeyX,pubkeyY, iv);
                } catch (Exception e) {
                    cryptoDataByte.setCode(CodeMessage.Code1020OtherError);
                    cryptoDataByte.setMessage(e.getMessage());
                    return;
                }
                cryptoDataByte.setSymkey(symkey);
                cryptoDataByte.setType(EncryptType.Symkey);
                cryptoDataByte.setAlg(AlgorithmId.FC_ChaCha20_No1_NrC7);
                ChaCha20.decryptStream(is,os,cryptoDataByte);
            }
            default -> {
                symkey = EccAes256K1P7.asyKeyToSymkey(prikeyX,pubkeyY,iv);
                cryptoDataByte.setSymkey(symkey);
                cryptoDataByte.setType(EncryptType.Symkey);
                cryptoDataByte.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7);
                AesCbc256.decryptStream(is,os,cryptoDataByte);
            }
        }

        cryptoDataByte.setAlg(algo);
        cryptoDataByte.setType(type);
    }
    public CryptoDataByte decryptStreamByAsy(InputStream is, OutputStream os, byte[]prikeyX, byte[]pubkeyY, byte[] iv, byte[] sum){
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setPrikeyA(prikeyX);
        cryptoDataByte.setPubkeyB(pubkeyY);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.setSum(sum);

        decryptStreamByAsy(is,os,cryptoDataByte);
        return cryptoDataByte;
    }
    public static void checkSum(CryptoDataByte cryptoDataByte) {
        byte[] newSum = CryptoDataByte.makeSum4(cryptoDataByte.getSymkey(), cryptoDataByte.getIv(), cryptoDataByte.getDid());
        if(cryptoDataByte.getSum()!=null && !Arrays.equals(newSum, cryptoDataByte.getSum())){
            cryptoDataByte.setCodeMessage(CodeMessage.Code4011BadSum);
        }else cryptoDataByte.set0CodeMessage();
    }

    public static byte[] sha256(byte[] b) {
        return Hashing.sha256().hashBytes(b).asBytes();
    }

}
