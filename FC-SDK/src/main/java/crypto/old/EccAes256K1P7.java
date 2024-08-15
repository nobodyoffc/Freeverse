package crypto.old;

import crypto.CryptoDataStr;
import crypto.CryptoDataByte;
import crypto.EncryptType;
import fcData.Affair;
import fcData.AlgorithmId;
import fcData.Op;
import constants.Constants;
import crypto.Hash;
import crypto.KeyTools;
import javaTools.BytesTools;
import javaTools.JsonTools;

import com.google.gson.Gson;
import javaTools.FileTools;

import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

import static fcData.AlgorithmId.EccAes256K1P7_No1_NrC7;
import static fcData.AlgorithmId.FC_EccK1AesCbc256_No1_NrC7;

/**
 * * ECDH<p>
 * * secp256k1<p>
 * * AES-256-CBC-PKCS7Padding<p>
 * * By No1_NrC7 with the help of chatGPT
 */

public class EccAes256K1P7 {
    private static final Logger log = LoggerFactory.getLogger(EccAes256K1P7.class);
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @org.junit.jupiter.api.Test
    public void test() throws Exception {

        Gson gson = new Gson();

        System.out.println("----------------------");
        System.out.println("Encode: ");
        System.out.println("    message: UTF-8");
        System.out.println("    key: Hex char[]");
        System.out.println("    ciphertext: Base64");
        System.out.println("----------------------");

        String msg = "hello world!";
        System.out.println("msg: " + msg);

        // ECC Test
        System.out.println("----------------------");
        System.out.println("Basic Test");
        System.out.println("----------------------");
        System.out.println("AsyOneWay:");
        System.out.println("----------");

        EccAes256K1P7 ecc = new EccAes256K1P7();

        CryptoDataByte cryptoDataByte;
        CryptoDataStr cryptoDataStr = new CryptoDataStr();

        String pubKeyB = "02536e4f3a6871831fa91089a5d5a950b96a31c861956f01459c0cd4f4374b2f67";
        cryptoDataStr.setData(msg);
        cryptoDataStr.setPubKeyB(pubKeyB);
        cryptoDataStr.setType(EncryptType.AsyOneWay);
        cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);

        // Encrypt with new keys
        ecc.encrypt(cryptoDataByte);

        char[] symKey;
        System.out.println("SymKey: " + HexFormat.of().formatHex(cryptoDataByte.getSymKey()));
        cryptoDataByte.clearSymKey();
        cryptoDataStr = CryptoDataStr.fromCryptoDataByte(cryptoDataByte);
        System.out.println("Encrypted with a new key pair:" + gson.toJson(cryptoDataStr));

        //Decrypt with new key
        String priKeyB = "ee72e6dd4047ef7f4c9886059cbab42eaab08afe7799cbc0539269ee7e2ec30c";
        cryptoDataByte.setData(null);
        cryptoDataByte.setPriKeyB(HexFormat.of().parseHex(priKeyB));

        ecc.decrypt(cryptoDataByte);
        cryptoDataByte.clearSymKey();
        System.out.println("Decrypted from bytes:" + gson.toJson(CryptoDataStr.fromCryptoDataByte(cryptoDataByte)));

        cryptoDataStr = CryptoDataStr.fromCryptoDataByte(cryptoDataByte);
        cryptoDataStr.setPriKeyB(priKeyB.toCharArray());
        ecc.decrypt(cryptoDataStr);
        System.out.println("Decrypted from String and char array:" + gson.toJson(cryptoDataStr));

        System.out.println("EccAes JSON without symKey:");
        cryptoDataStr.clearSymKey();
        System.out.println(gson.toJson(cryptoDataStr));


        System.out.println("----------------------");
        System.out.println("AsyTwoWay:");
        System.out.println("----------");

        String pubKeyA = "030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a";
        String priKeyA = "a048f6c843f92bfe036057f7fc2bf2c27353c624cf7ad97e98ed41432f700575";

        cryptoDataStr = new CryptoDataStr();
        cryptoDataStr.setType(EncryptType.AsyTwoWay);
        cryptoDataStr.setData(msg);
        cryptoDataStr.setPubKeyB(pubKeyB);
        ecc.encrypt(cryptoDataStr);
        System.out.println("Lack priKeyA: " + gson.toJson(cryptoDataStr));

        cryptoDataStr.setPriKeyA(priKeyA.toCharArray());

        ecc.encrypt(cryptoDataStr);
        System.out.println("Encrypt: " + gson.toJson(cryptoDataStr));

        cryptoDataStr.setPriKeyB(priKeyB.toCharArray());
        cryptoDataStr.setData(null);
        ecc.decrypt(cryptoDataStr);
        System.out.println("Decrypt by private Key B: " + gson.toJson(cryptoDataStr));
        cryptoDataStr.setPriKeyA(priKeyA.toCharArray());
        cryptoDataStr.setData(null);
        ecc.decrypt(cryptoDataStr);
        System.out.println("Decrypt by private Key A: " + gson.toJson(cryptoDataStr));

        System.out.println("----------------------");
        System.out.println("SymKey:");
        System.out.println("----------");
        cryptoDataStr = new CryptoDataStr();
        cryptoDataStr.setType(EncryptType.SymKey);

        String symKeyStr = "3b7ca1c4925c597083bb94c8e1582a621e4e72510780aa31ef0a769a406c2870";
        symKey = symKeyStr.toCharArray();
        cryptoDataStr.setSymKey(symKey);
        ecc.encrypt(cryptoDataStr);
        System.out.println("Lack msg: " + gson.toJson(cryptoDataStr));

        cryptoDataStr.setData(msg);
        cryptoDataStr.setSymKey(symKey);
        ecc.encrypt(cryptoDataStr);
        System.out.println("SymKey encrypt: " + gson.toJson(cryptoDataStr));

        cryptoDataStr.setData(null);
        cryptoDataStr.setSymKey(symKey);
        ecc.decrypt(cryptoDataStr);
        System.out.println("SymKey decrypt: " + gson.toJson(cryptoDataStr));

        System.out.println("----------------------");
        System.out.println("Password:");
        System.out.println("----------");

        cryptoDataStr = new CryptoDataStr();
        cryptoDataStr.setType(EncryptType.Password);
        cryptoDataStr.setData(msg);
        cryptoDataStr.setSymKey(symKey);
        String passwordStr = "password马云！";
        char[] password = passwordStr.toCharArray();
        cryptoDataStr.setPassword(password);

        System.out.println("password:" + String.valueOf(password));
        ecc.encrypt(cryptoDataStr);
        System.out.println("Password encrypt: \n" + gson.toJson(cryptoDataStr));

        cryptoDataStr.setData(null);
        cryptoDataStr.setSymKey(null);
        password = "password马云！".toCharArray();
        cryptoDataStr.setPassword(password);

        ecc.decrypt(cryptoDataStr);
        System.out.println("Password decrypt: \n" + gson.toJson(cryptoDataStr));
        System.out.println("----------------------");
        System.out.println("----------------------");
        System.out.println("Test Json");
        System.out.println("----------------------");
        System.out.println("AsyOneWay json:");
        System.out.println("----------");

        String encOneWayJson0 = ecc.encrypt(msg, pubKeyB);
        checkResult(cryptoDataStr, "Encrypted: \n" + encOneWayJson0);

        String eccAesData1 = ecc.decrypt(encOneWayJson0, priKeyB.toCharArray());
        checkResult(cryptoDataStr, "Decrypted:\n" + eccAesData1);

        System.out.println("----------");

        System.out.println("AsyTwoWay json:");
        System.out.println("----------");

        cryptoDataStr = new CryptoDataStr();
        cryptoDataStr.setType(EncryptType.AsyTwoWay);
        cryptoDataStr.setData(msg);
        cryptoDataStr.setPubKeyB(pubKeyB);
        String twoWayJson1 = gson.toJson(cryptoDataStr);

        System.out.println("TwoWayJson1:" + twoWayJson1);


        String encTwoWayJson1 = ecc.encrypt(msg, pubKeyB, priKeyA.toCharArray());
        checkResult(cryptoDataStr, "Encrypted: \n" + encTwoWayJson1);
        eccAesData1 = ecc.decrypt(encTwoWayJson1, priKeyB.toCharArray());
        checkResult(cryptoDataStr, "Decrypted:\n" + eccAesData1);

        System.out.println("----------");

        System.out.println("SymKey json:");
        System.out.println("----------");
        cryptoDataStr = new CryptoDataStr();
        cryptoDataStr.setType(EncryptType.SymKey);
        cryptoDataStr.setData(msg);
        symKey = symKeyStr.toCharArray();
        cryptoDataStr.setSymKey(symKey);

        String symKeyJson1 = gson.toJson(cryptoDataStr);
        System.out.println("SymKeyJson1:" + symKeyJson1);
        System.out.println("SymKey: " + Arrays.toString(symKey));
        String encSymKeyJson1 = ecc.encrypt(msg, symKey);
        checkResult(cryptoDataStr, "Encrypted: \n" + encSymKeyJson1);

        String decSymKeyJson = ecc.decrypt(encSymKeyJson1, symKey);
        checkResult(cryptoDataStr, "Decrypted:\n" + decSymKeyJson);
        System.out.println("----------");

        System.out.println("Password json:");
        System.out.println("----------");
        cryptoDataStr = new CryptoDataStr();
        cryptoDataStr.setType(EncryptType.Password);
        cryptoDataStr.setData(msg);
        cryptoDataStr.setPassword(passwordStr.toCharArray());

        String passwordDataJson1 = gson.toJson(cryptoDataStr);
        System.out.println("PasswordJson1:" + passwordDataJson1);

        String encPasswordJson1 = ecc.encrypt(msg, passwordStr.toCharArray());
        checkResult(cryptoDataStr, "Encrypted: \n" + encPasswordJson1);

        String decPasswordJson = ecc.decrypt(encPasswordJson1, passwordStr.toCharArray());
        checkResult(cryptoDataStr, "Decrypted:\n" + decPasswordJson);
        System.out.println("----------------------");

        System.out.println("----------------------");
        System.out.println("Test Constructor");
        System.out.println("----------------------");

        System.out.println("AsyOneWay encrypt Constructor:");
        System.out.println("----------");
        cryptoDataStr = new CryptoDataStr(EncryptType.AsyOneWay, msg, pubKeyB);
        ecc.encrypt(cryptoDataStr);
        System.out.println(gson.toJson(cryptoDataStr));

        System.out.println("----------");
        System.out.println("AsyTwoWay encrypt Constructor:");
        System.out.println("----------");

        cryptoDataStr = new CryptoDataStr(EncryptType.AsyTwoWay, msg, pubKeyB, priKeyA.toCharArray());
        ecc.encrypt(cryptoDataStr);
        System.out.println(gson.toJson(cryptoDataStr));
        System.out.println("----------");
        System.out.println("SymKey encrypt Constructor:");
        System.out.println("----------");
        symKey = symKeyStr.toCharArray();
        cryptoDataStr = new CryptoDataStr(EncryptType.SymKey, msg, symKey);
        ecc.encrypt(cryptoDataStr);
        System.out.println(gson.toJson(cryptoDataStr));
        System.out.println("----------");
        System.out.println("Password encrypt Constructor:");
        System.out.println("----------");
        cryptoDataStr = new CryptoDataStr(EncryptType.Password, msg, password);
        ecc.encrypt(cryptoDataStr);
        System.out.println(gson.toJson(cryptoDataStr));
        System.out.println("----------");
        System.out.println("Asy Decrypt Constructor:");
        System.out.println("----------");
        String cipher = "yu7qzwXoEeKwRsCT/fLxaA==";
        String iv = "988a330ab28e61fa01471bf13ce6cc7d";
        String sum = "346a8033";
        cryptoDataStr = new CryptoDataStr(EncryptType.AsyOneWay, pubKeyA, pubKeyB, iv, cipher, sum, priKeyB.toCharArray());
        ecc.decrypt(cryptoDataStr);
        System.out.println(gson.toJson(cryptoDataStr));
        System.out.println("----------");
        System.out.println("Sym Decrypt Constructor:");
        System.out.println("----------");
        cipher = "6f20f3ukM3ol0KRJHACb0w==";
        iv = "862dc48880b515d589851df25827fbcf";
        sum = "befc5792";
        cryptoDataStr = new CryptoDataStr(EncryptType.SymKey, iv, cipher, sum, symKey);
        ecc.decrypt(cryptoDataStr);
        System.out.println(gson.toJson(cryptoDataStr));
        System.out.println("----------------------");
        System.out.println("Bundle test");
        System.out.println("----------------------");
        System.out.println("String");
        System.out.println("----------");
        System.out.println("AsyOneWay bundle test");
        System.out.println("----------");

        System.out.println("msg:" + msg + ",pubKeyB:" + pubKeyB);
        String bundle = ecc.encryptAsyOneWayBundle(msg, pubKeyB);
        System.out.println("Cipher bundle: " + bundle);
        String msgBundle = ecc.decryptAsyOneWayBundle(bundle, priKeyB.toCharArray());
        System.out.println("Msg from bundle:" + msgBundle);

        System.out.println("----------------------");
        System.out.println("AsyTwoWay bundle test");
        System.out.println("----------");

        bundle = ecc.encryptAsyTwoWayBundle(msg, pubKeyB, priKeyA.toCharArray());
        System.out.println("Cipher bundle: " + bundle);
        msgBundle = ecc.decryptAsyTwoWayBundle(bundle, pubKeyA, priKeyB.toCharArray());
        System.out.println("Msg from PriKeyB:" + msgBundle);
        msgBundle = ecc.decryptAsyTwoWayBundle(bundle, pubKeyB, priKeyA.toCharArray());
        System.out.println("Msg from PriKeyA:" + msgBundle);

        System.out.println("----------------------");
        System.out.println("SymKey bundle test");
        System.out.println("----------");

        bundle = ecc.encryptSymKeyBundle(msg, symKey);
        System.out.println("Cipher bundle: " + bundle);
        msgBundle = ecc.decryptSymKeyBundle(bundle, symKey);
        System.out.println("Msg from bundle:" + msgBundle);
        System.out.println("----------------------");

        System.out.println("byte[]");
        System.out.println("----------");
        byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
        byte[] pubKeyBBytes = HexFormat.of().parseHex(pubKeyB);
        byte[] priKeyBBytes = HexFormat.of().parseHex(priKeyB);
        byte[] pubKeyABytes = HexFormat.of().parseHex(pubKeyA);
        byte[] priKeyABytes = HexFormat.of().parseHex(priKeyA);


        System.out.println("AsyOneWay bundle test");
        System.out.println("----------");

        byte[] bundleBytes = ecc.encryptAsyOneWayBundle(msgBytes, pubKeyBBytes);
        System.out.println("Cipher bundle: " + Base64.getEncoder().encodeToString(bundleBytes));
        byte[] msgBundleBytes = ecc.decryptAsyOneWayBundle(bundleBytes, priKeyBBytes);
        System.out.println("Msg from bundle:" + new String(msgBundleBytes));

        System.out.println("----------------------");
        System.out.println("AsyTwoWay bundle test");
        System.out.println("----------");

        //Reload sensitive parameters
        priKeyBBytes = HexFormat.of().parseHex(priKeyB);
        priKeyABytes = HexFormat.of().parseHex(priKeyA);

        bundleBytes = ecc.encryptAsyTwoWayBundle(msgBytes, pubKeyBBytes, priKeyABytes);
        System.out.println("Cipher bundle: " + Base64.getEncoder().encodeToString(bundleBytes));
        msgBundleBytes = ecc.decryptAsyTwoWayBundle(bundleBytes, pubKeyABytes, priKeyBBytes);
        System.out.println("Msg from PriKeyB:" + new String(msgBundleBytes));

        //Reload sensitive parameters
        priKeyBBytes = HexFormat.of().parseHex(priKeyB);
        priKeyABytes = HexFormat.of().parseHex(priKeyA);
        msgBundleBytes = ecc.decryptAsyTwoWayBundle(bundleBytes, pubKeyBBytes, priKeyABytes);
        System.out.println("Msg from PriKeyA:" + new String(msgBundleBytes));

        System.out.println("----------------------");
        System.out.println("SymKey bundle test");
        System.out.println("----------");

        byte[] symKeyBytes = HexFormat.of().parseHex(symKeyStr);
        bundleBytes = ecc.encryptSymKeyBundle(msgBytes, symKeyBytes);
        System.out.println("Cipher bundle: " + Base64.getEncoder().encodeToString(bundleBytes));
        //Reload sensitive parameters
        symKeyBytes = HexFormat.of().parseHex(symKeyStr);
        msgBundleBytes = ecc.decryptSymKeyBundle(bundleBytes, symKeyBytes);
        System.out.println("Msg from bundle:" + new String(msgBundleBytes));

        System.out.println("----------------------");
        System.out.println("Char Array as msg:");
        System.out.println("----------------------");

        System.out.println("msg: " + symKeyStr);
        String cipherAsyOne = ecc.encrypt(symKeyStr.toCharArray(), pubKeyB);
        String cipherAsyTwo = ecc.encrypt(symKeyStr.toCharArray(), pubKeyB, priKeyA.toCharArray());
        String cipherSymKey = ecc.encrypt(symKeyStr.toCharArray(), symKeyStr.toCharArray());
        String cipherPassword = ecc.encrypt(symKeyStr.toCharArray(), passwordStr.toCharArray());

        System.out.println("cipherAsyOne:" + cipherAsyOne);
        System.out.println("cipherAsyTwo:" + cipherAsyTwo);
        System.out.println("cipherSymKey:" + cipherSymKey);
        System.out.println("cipherPassword:" + cipherPassword);

        System.out.println("decrypted AsyOne:" + ecc.decrypt(cipherAsyOne, priKeyB.toCharArray()));
        System.out.println("decrypted AsyTwo:" + ecc.decrypt(cipherAsyTwo, priKeyB.toCharArray()));
        System.out.println("decrypted SymKey:" + ecc.decrypt(cipherSymKey, symKeyStr.toCharArray()));
        System.out.println("decrypted Password:" + ecc.decrypt(cipherPassword, passwordStr.toCharArray()));
    }

    private void checkResult(CryptoDataStr cryptoDataStr, String s) {
        if (cryptoDataStr.getMessage() != null) {
            System.out.println(cryptoDataStr.getMessage());
        } else {
            System.out.println(s);
        }
    }
    public void encrypt(CryptoDataByte cryptoDataByte) {
        if (isErrorAlgAndType(cryptoDataByte)) return;
        switch (cryptoDataByte.getType()) {
            case AsyOneWay, AsyTwoWay -> encryptAsy(cryptoDataByte);
            case SymKey -> encryptWithSymKey(cryptoDataByte);
            case Password -> encryptWithPassword(cryptoDataByte);
            default -> cryptoDataByte.setMessage("Wrong type: " + cryptoDataByte.getType());
        }
    }

    public void decrypt(CryptoDataByte cryptoDataByte) {
        if (isErrorAlgAndType(cryptoDataByte)) return;

        switch (cryptoDataByte.getType()) {
            case AsyOneWay, AsyTwoWay -> decryptAsy(cryptoDataByte);
            case SymKey -> decryptWithSymKey(cryptoDataByte);
            case Password -> decryptWithPassword(cryptoDataByte);
        }
    }

    public static String encryptKeyWithPassword(byte[] keyBytes, char[] password) {
        EccAes256K1P7 ecc = new EccAes256K1P7();
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.Password);
        cryptoDataByte.setData(keyBytes);
        cryptoDataByte.setPassword(BytesTools.utf8CharArrayToByteArray(password));
        ecc.encrypt(cryptoDataByte);
        if (cryptoDataByte.getMessage() != null) {
            return "Error:" + cryptoDataByte.getMessage();
        }
        return CryptoDataStr.fromCryptoDataByte(cryptoDataByte).toJson();
    }
    public static String encryptWithSymKey(byte[] msgBytes, byte[] symKey) {
        EccAes256K1P7 ecc = new EccAes256K1P7();

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.SymKey);
        cryptoDataByte.setData(msgBytes);
        cryptoDataByte.setSymKey(symKey);
        ecc.encrypt(cryptoDataByte);
        if (cryptoDataByte.getMessage() == null)
            return CryptoDataStr.fromCryptoDataByte(cryptoDataByte).toJson();
        return "Error:" + cryptoDataByte.getMessage();
    }

    public static String encryptWithPubKey(byte[] msgBytes, byte[] pubKey) {
        EccAes256K1P7 ecc = new EccAes256K1P7();

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.AsyOneWay);
        cryptoDataByte.setData(msgBytes);
        cryptoDataByte.setPubKeyB(pubKey);
        ecc.encrypt(cryptoDataByte);
        if (cryptoDataByte.getMessage() == null)
            return CryptoDataStr.fromCryptoDataByte(cryptoDataByte).toJson();
        return "Error:" + cryptoDataByte.getMessage();
    }

    public static String encryptWithPubKeyPriKey(byte[] msgBytes, byte[] pubKeyB,byte[]priKeyA) {
        EccAes256K1P7 ecc = new EccAes256K1P7();

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.AsyTwoWay);
        cryptoDataByte.setData(msgBytes);
        cryptoDataByte.setPubKeyB(pubKeyB);
        cryptoDataByte.setPriKeyA(priKeyA);
        ecc.encrypt(cryptoDataByte);
        if (cryptoDataByte.getMessage() == null)
            return CryptoDataStr.fromCryptoDataByte(cryptoDataByte).toJson();
        return "Error:" + cryptoDataByte.getMessage();
    }

    public void encrypt(CryptoDataStr cryptoDataStr) {
        if (cryptoDataStr == null) return;
        cryptoDataStr.setMessage(null);

        if (cryptoDataStr.getType() == null) {
            cryptoDataStr.setMessage("EccAesType is required.");
            //eccAesData.clearAllSensitiveData();
            return;
        }
        if (!isGoodEncryptParams(cryptoDataStr)) {
            //eccAesData.clearAllSensitiveData();
            return;
        }
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);
        encrypt(cryptoDataByte);
        CryptoDataStr cryptoDataStr1 = CryptoDataStr.fromCryptoDataByte(cryptoDataByte);
        copyEccAesData(cryptoDataStr1, cryptoDataStr);
    }

    public String encrypt(String msg, String pubKeyB) {
        CryptoDataStr cryptoDataStr = new CryptoDataStr(EncryptType.AsyOneWay, msg, pubKeyB);
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);
        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return CryptoDataStr.fromCryptoDataByte(cryptoDataByte).toJson();
    }

    public String encrypt(String msg, String pubKeyB, char[] priKeyA) {
        CryptoDataStr cryptoDataStr = new CryptoDataStr(EncryptType.AsyTwoWay, msg, pubKeyB, priKeyA);

        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);
        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return CryptoDataStr.fromCryptoDataByte(cryptoDataByte).toJson();
    }

    public String encrypt(String msg, char[] symKey32OrPassword) {
        CryptoDataStr cryptoDataStr;
        if (symKey32OrPassword.length == 64) {
            boolean isHex = isCharArrayHex(symKey32OrPassword);
            if (isHex) cryptoDataStr = new CryptoDataStr(EncryptType.SymKey, msg, symKey32OrPassword);
            else cryptoDataStr = new CryptoDataStr(EncryptType.Password, msg, symKey32OrPassword);
        } else cryptoDataStr = new CryptoDataStr(EncryptType.Password, msg, symKey32OrPassword);

        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);
        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return CryptoDataStr.fromCryptoDataByte(cryptoDataByte).toJson();
    }

    public String encrypt(char[] msg, String pubKeyB) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.AsyOneWay);
        cryptoDataByte.setData(BytesTools.charArrayToByteArray(msg, StandardCharsets.UTF_8));
        cryptoDataByte.setPubKeyB(BytesTools.hexToByteArray(pubKeyB));
        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return CryptoDataStr.fromCryptoDataByte(cryptoDataByte).toJson();
    }

    public String encrypt(char[] msg, String pubKeyB, char[] priKeyA) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.AsyTwoWay);
        cryptoDataByte.setData(BytesTools.charArrayToByteArray(msg, StandardCharsets.UTF_8));
        cryptoDataByte.setPubKeyB(BytesTools.hexToByteArray(pubKeyB));
        cryptoDataByte.setPriKeyA(BytesTools.hexCharArrayToByteArray(priKeyA));
        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return CryptoDataStr.fromCryptoDataByte(cryptoDataByte).toJson();
    }

    public String encrypt(char[] msg, char[] symKey32OrPassword) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        if (symKey32OrPassword.length == 64) {
            boolean isHex = isCharArrayHex(symKey32OrPassword);
            if (isHex) {
                cryptoDataByte.setType(EncryptType.SymKey);
                cryptoDataByte.setSymKey(BytesTools.hexCharArrayToByteArray(symKey32OrPassword));
            } else {
                cryptoDataByte.setType(EncryptType.Password);
                cryptoDataByte.setPassword(BytesTools.charArrayToByteArray(symKey32OrPassword, StandardCharsets.UTF_8));
            }
        } else {
            cryptoDataByte.setType(EncryptType.Password);
            cryptoDataByte.setPassword(BytesTools.charArrayToByteArray(symKey32OrPassword, StandardCharsets.UTF_8));
        }

        cryptoDataByte.setData(BytesTools.charArrayToByteArray(msg, StandardCharsets.UTF_8));
        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return CryptoDataStr.fromCryptoDataByte(cryptoDataByte).toJson();
    }


    public static byte[] decryptJsonBytes(String keyCipherJson, byte[] keyOrPassword) {
        EccAes256K1P7 ecc = new EccAes256K1P7();
//        System.out.println("Decrypt key...");
        CryptoDataByte result = ecc.decrypt(keyCipherJson, keyOrPassword);
        if (result.getMessage() != null) {
            log.debug("Decrypting wrong: " + result.getMessage());
            return null;
        }
        return result.getData();
    }

    public static CryptoDataByte encryptWithPassword(byte[] initSymKey, byte[] passwordBytes) {
        EccAes256K1P7 ecc = new EccAes256K1P7();
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.Password);
        cryptoDataByte.setData(initSymKey);
        cryptoDataByte.setPassword(passwordBytes);
        ecc.encrypt(cryptoDataByte);
        if (cryptoDataByte.getMessage() != null) {
            System.out.println("Failed to encrypt key: " + cryptoDataByte.getMessage());
            return null;
        }
        return cryptoDataByte;
    }

    public static CryptoDataByte makeIvCipherToCryptoDataByte(byte[] ivCipherBytes) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setAlg(AlgorithmId.FC_Aes256Cbc_No1_NrC7);
        cryptoDataByte.setType(EncryptType.SymKey);
        byte[] iv = Arrays.copyOfRange(ivCipherBytes, 0, 16);
        byte[] cipher = Arrays.copyOfRange(ivCipherBytes, 16, ivCipherBytes.length);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.setCipher(cipher);
        return cryptoDataByte;
    }

    public static ECPrivateKeyParameters priKeyFromHex(String privateKeyHex) {
        BigInteger privateKeyValue = new BigInteger(privateKeyHex, 16); // Convert hex to BigInteger
        X9ECParameters ecParameters = org.bouncycastle.asn1.sec.SECNamedCurves.getByName("secp256k1"); // Use the same curve name as in key pair generation
        ECDomainParameters domainParameters = new ECDomainParameters(ecParameters.getCurve(), ecParameters.getG(), ecParameters.getN(), ecParameters.getH());
        return new ECPrivateKeyParameters(privateKeyValue, domainParameters);
    }

    public static ECPrivateKeyParameters priKeyFromBytes(byte[] privateKey) {
        return priKeyFromHex(HexFormat.of().formatHex(privateKey));
    }

    public static ECPublicKeyParameters pubKeyFromPriKey(ECPrivateKeyParameters privateKey) {
        X9ECParameters ecParameters = org.bouncycastle.asn1.sec.SECNamedCurves.getByName("secp256k1");
        ECDomainParameters domainParameters = new ECDomainParameters(ecParameters.getCurve(), ecParameters.getG(), ecParameters.getN(), ecParameters.getH());

        ECPoint Q = domainParameters.getG().multiply(privateKey.getD()); // Scalar multiplication of base point (G) and private key

        return new ECPublicKeyParameters(Q, domainParameters);
    }

    public static byte[] pubKeyToBytes(ECPublicKeyParameters publicKey) {
        return publicKey.getQ().getEncoded(true);
    }

    public static byte[] getRandomIv() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] iv = new byte[16];
        secureRandom.nextBytes(iv);
        return iv;
    }

    public static boolean isTheKeyPair(byte[] pubKeyByte, byte[] priKeyByte) {
        ECPrivateKeyParameters priKey = priKeyFromBytes(priKeyByte);
        byte[] pubKeyFromPriKey = pubKeyToBytes(pubKeyFromPriKey(priKey));
        return Arrays.equals(pubKeyByte, pubKeyFromPriKey);
    }

    public static byte[] decryptWithPriKey(String cipher, byte[] priKey) {
        EccAes256K1P7 ecc = new EccAes256K1P7();
        CryptoDataByte cryptoDataBytes = ecc.decrypt(cipher, priKey);
        if (cryptoDataBytes.getMessage() != null) {
            System.out.println("Decrypt sessionKey wrong: " + cryptoDataBytes.getMessage());
            BytesTools.clearByteArray(priKey);
            return null;
        }
        return cryptoDataBytes.getData();
    }

    private boolean isCharArrayHex(char[] symKey32OrPassword) {
        boolean isHex = false;
        for (char c : symKey32OrPassword) {
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
                isHex = true;
            } else {
                return false;
            }
        }
        return isHex;
    }

    public String encrypt(File originalFile, char[] symKey) {
        CryptoDataStr cryptoDataStr = new CryptoDataStr();
        cryptoDataStr.setType(EncryptType.SymKey);
        cryptoDataStr.setAlg(FC_EccK1AesCbc256_No1_NrC7);
        cryptoDataStr.setSymKey(symKey);

        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);
        byte[] symKeyBytes = BytesTools.hexCharArrayToByteArray(symKey);
        return encrypt(originalFile, cryptoDataByte);
    }

    public String encrypt(File originalFile, String pubKeyB) {
        CryptoDataStr cryptoDataStr = new CryptoDataStr();
        cryptoDataStr.setType(EncryptType.AsyOneWay);
        cryptoDataStr.setAlg(FC_EccK1AesCbc256_No1_NrC7);
        cryptoDataStr.setPubKeyB(pubKeyB);

        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);
        return encrypt(originalFile, cryptoDataByte);
    }

    public String encrypt(File originalFile, String pubKeyB, char[] priKeyA) {
        CryptoDataStr cryptoDataStr = new CryptoDataStr();
        cryptoDataStr.setType(EncryptType.AsyTwoWay);
        cryptoDataStr.setAlg(FC_EccK1AesCbc256_No1_NrC7);
        cryptoDataStr.setPubKeyB(pubKeyB);
        cryptoDataStr.setPriKeyA(priKeyA);

        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);
        return encrypt(originalFile, cryptoDataByte);
    }

    private String encrypt(File originalFile, CryptoDataByte cryptoDataByte) {
        byte[] msgBytes;
        try (FileInputStream fis = new FileInputStream(originalFile)) {
            msgBytes = fis.readAllBytes();
        } catch (IOException e) {
            return "FileInputStream wrong.";
        }

        if (msgBytes != null && msgBytes.length != 0)
            cryptoDataByte.setData(msgBytes);

        EccAes256K1P7 ecc = new EccAes256K1P7();
        ecc.encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();

        if (cryptoDataByte.getMessage() != null) {
            return cryptoDataByte.getMessage();
        } else {
            String parentPath = originalFile.getParent();
            String originalFileName = originalFile.getName();
            int endIndex = originalFileName.lastIndexOf('.');
            String suffix = "_" + originalFileName.substring(endIndex + 1);
            String encryptedFileName = originalFileName.substring(0, endIndex) + suffix + Constants.DOT_FV;
            File encryptedFile = FileTools.getNewFile(parentPath, encryptedFileName, FileTools.CreateNewFileMode.REWRITE);
            if (encryptedFile == null) return "Error:Creating encrypted file wrong.";

            try (FileOutputStream fos = new FileOutputStream(encryptedFile)) {
                byte[] cipherBytes;
                cipherBytes = cryptoDataByte.getCipher();
                cryptoDataByte.setCipher(null);

                Affair affair = new Affair();
                affair.setOp(Op.encrypt);

                if (cryptoDataByte.getType() == EncryptType.AsyOneWay || cryptoDataByte.getType() == EncryptType.AsyTwoWay)
                    affair.setFid(KeyTools.pubKeyToFchAddr(cryptoDataByte.getPubKeyB()));
                affair.setOid(Hash.sha256x2(originalFile));
                CryptoDataStr cryptoDataStr = CryptoDataStr.fromCryptoDataByte(cryptoDataByte);
                affair.setData(cryptoDataStr);
                fos.write(new Gson().toJson(affair).getBytes());
                fos.write(cipherBytes);
            } catch (IOException e) {
                return "Error: Writing encrypted file wrong.";
            }
        }
        return "Done.";
    }

    public void decrypt(CryptoDataStr cryptoDataStr) {
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);
        decrypt(cryptoDataByte);
        CryptoDataStr cryptoDataStr1 = CryptoDataStr.fromCryptoDataByte(cryptoDataByte);
        copyEccAesData(cryptoDataStr1, cryptoDataStr);
    }
    public String decrypt(String eccAesDataJson, char[] key){
        return decrypt(eccAesDataJson,key,null);
    }
    public String decrypt(String eccAesDataJson, char[] key, @Nullable String pubKeyA) {
        Gson gson = new Gson();
        CryptoDataStr cryptoDataStr = CryptoDataStr.fromJson(eccAesDataJson);
        switch (cryptoDataStr.getType()) {
            case AsyOneWay -> cryptoDataStr.setPriKeyB(key);
            case AsyTwoWay -> {
                cryptoDataStr.setPriKeyB(key);
                cryptoDataStr.setPubKeyA(pubKeyA);
            }
            case SymKey -> cryptoDataStr.setSymKey(key);
            case Password -> cryptoDataStr.setPassword(key);
            default -> cryptoDataStr.setMessage("Wrong EccAesType type" + cryptoDataStr.getType());
        }
        if (cryptoDataStr.getMessage() != null) {
            return "Error:" + cryptoDataStr.getMessage();
        }
        decrypt(cryptoDataStr);
        return cryptoDataStr.getData();
    }

    public byte[] decryptForBytes(String eccAesDataJson, char[] key) {
        Gson gson = new Gson();
        CryptoDataStr cryptoDataStr = gson.fromJson(eccAesDataJson, CryptoDataStr.class);

        switch (cryptoDataStr.getType()) {
            case AsyOneWay -> cryptoDataStr.setPriKeyB(key);
            case AsyTwoWay -> cryptoDataStr.setPriKeyB(key);
            case SymKey -> cryptoDataStr.setSymKey(key);
            case Password -> cryptoDataStr.setPassword(key);
            default -> cryptoDataStr.setMessage("Wrong EccAesType type" + cryptoDataStr.getType());
        }
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);
        if (cryptoDataByte.getMessage() != null) {
            return null;
        }
        decrypt(cryptoDataByte);
        return cryptoDataByte.getData();
    }

    public CryptoDataByte decrypt(String eccAesDataJson, byte[] key) {
        Gson gson = new Gson();
        CryptoDataStr cryptoDataStr = gson.fromJson(eccAesDataJson, CryptoDataStr.class);
        CryptoDataByte cryptoDataBytes = CryptoDataByte.fromCryptoData(cryptoDataStr);
        switch (cryptoDataBytes.getType()) {
            case AsyOneWay, AsyTwoWay -> cryptoDataBytes.setPriKeyB(key);
            case SymKey -> cryptoDataBytes.setSymKey(key);
            case Password -> cryptoDataBytes.setPassword(key);
            default -> cryptoDataBytes.setMessage("Wrong EccAesType type" + cryptoDataBytes.getType());
        }
        if (cryptoDataBytes.getMessage() != null) {
            return cryptoDataBytes;
        }
        decrypt(cryptoDataBytes);
        return cryptoDataBytes;
    }

    private String decrypt(File encryptedFile, CryptoDataByte cryptoDataByte) {

        EccAes256K1P7 ecc = new EccAes256K1P7();
        ecc.decrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();

        if (cryptoDataByte.getMessage() != null) {
            return cryptoDataByte.getMessage();
        } else {
            String parentPath = encryptedFile.getParent();
            String encryptedFileName = encryptedFile.getName();
            int endIndex1 = encryptedFileName.lastIndexOf('_');
            int endIndex2 = encryptedFileName.lastIndexOf('.');
            String oldSuffix;
            String originalFileName;
            if(endIndex1!=-1) {
                oldSuffix = encryptedFileName.substring(endIndex1 + 1, endIndex2);
                originalFileName = encryptedFileName.substring(0, endIndex1) + "." + oldSuffix;
            }else originalFileName = encryptedFileName+ Constants.DOT_DECRYPTED;

            File originalFile = FileTools.getNewFile(parentPath, originalFileName, FileTools.CreateNewFileMode.REWRITE);
            if (originalFile == null) return "Create recovered file failed.";
            try (FileOutputStream fos = new FileOutputStream(originalFile)) {
                fos.write(cryptoDataByte.getData());
                return "Done";
            } catch (IOException e) {
                return "Write file wrong";
            }
        }
    }
    @Test
    public void test1(){
        String oFileName1 = "a.png";
        String oFileName2 = "/user/t/a.png";

        String eFileName1 = getEncryptedFileName(oFileName1);
        String eFileName2 = getEncryptedFileName(oFileName2);
        System.out.println(eFileName1);
        System.out.println(eFileName2);
        System.out.println(getDecryptedFileName(eFileName1));
        System.out.println(getDecryptedFileName(eFileName2));

        String eFileName3 = "uwhkfdksau4232l3jl";
        String originalFileName;
        int endIndex1 = eFileName3.lastIndexOf('_');
        int endIndex2 = eFileName3.lastIndexOf('.');
        if(endIndex1!=-1) {
            String oldSuffix = eFileName3.substring(endIndex1 + 1, endIndex2);
            originalFileName = eFileName3.substring(0, endIndex1) + "." + oldSuffix;
        }else originalFileName = eFileName3+ Constants.DOT_DECRYPTED;
        System.out.println(originalFileName);

    }
    public static String getEncryptedFileName(String originalFileFullName){
        File file = new File(originalFileFullName);
        String parentPath = file.getParent();
        String originalFileName = file.getName();
        int endIndex = originalFileName.lastIndexOf('.');
        String suffix = "_" + originalFileName.substring(endIndex + 1);
        String encryptedFileName = originalFileName.substring(0, endIndex) + suffix + Constants.DOT_FV;
        if(parentPath==null)return encryptedFileName;
        else return parentPath+"/"+encryptedFileName;
    }
    public static String getDecryptedFileName(String encryptedFileFullName){
        File encryptedFile=new File(encryptedFileFullName);
        String parentPath = encryptedFile.getParent();
        String encryptedFileName = encryptedFile.getName();
        int endIndex1 = encryptedFileName.lastIndexOf('_');
        int endIndex2 = encryptedFileName.lastIndexOf('.');
        String oldSuffix;
        String originalFileName;
        if(endIndex1!=-1) {
            oldSuffix = encryptedFileName.substring(endIndex1 + 1, endIndex2);
            originalFileName = encryptedFileName.substring(0, endIndex1) + "." + oldSuffix;
        }else originalFileName = encryptedFileName + Constants.DOT_DECRYPTED;
        if(parentPath==null)return originalFileName;
        else return parentPath+"/"+originalFileName;
    }

    public String decrypt(File encryptedFile, byte[] priKeyBBytes) {
        byte[] cipherBytes;
        Affair affair;
        Gson gson = new Gson();
        CryptoDataStr cryptoDataStr;
        CryptoDataByte cryptoDataByte;
        try (FileInputStream fis = new FileInputStream(encryptedFile)) {
            affair = JsonTools.readObjectFromJsonFile(fis, Affair.class);
            cipherBytes = fis.readAllBytes();
            if (affair == null) return "Affair is null.";
            if (affair.getData() == null) return "Affair.data is null.";
            cryptoDataStr = gson.fromJson(gson.toJson(affair.getData()), CryptoDataStr.class);
            if (cryptoDataStr == null) return "Got eccAesData null.";
        } catch (IOException e) {
            return "Reading file wrong.";
        }
        cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);
        cryptoDataByte.setPriKeyB(priKeyBBytes);
        cryptoDataByte.setCipher(cipherBytes);
        return decrypt(encryptedFile, cryptoDataByte);
    }

    public String decrypt(File encryptedFile, char[] symKey) {
        byte[] cipherBytes;
        Affair affair;
        Gson gson = new Gson();
        CryptoDataStr cryptoDataStr;
        CryptoDataByte cryptoDataByte;
        try (FileInputStream fis = new FileInputStream(encryptedFile)) {
            affair = JsonTools.readObjectFromJsonFile(fis, Affair.class);
            cipherBytes = fis.readAllBytes();
            if (affair == null) return "Error:affair is null.";
            if (affair.getData() == null) return "Error:affair.data is null.";
            cryptoDataStr = gson.fromJson(gson.toJson(affair.getData()), CryptoDataStr.class);
            if (cryptoDataStr == null) return "Got eccAesData null.";
        } catch (IOException e) {
            return "Read file wrong.";
        }
        cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);
        cryptoDataByte.setSymKey(BytesTools.hexCharArrayToByteArray(symKey));
        if (cryptoDataByte.getMessage() != null) return "Error:" + cryptoDataByte.getMessage();
        cryptoDataByte.setCipher(cipherBytes);
        return decrypt(encryptedFile, cryptoDataByte);
    }

    private void decryptAsy(CryptoDataByte cryptoDataByte) {
        if (!isGoodDecryptParams(cryptoDataByte)) {
            return;
        }

        if (cryptoDataByte.getPubKeyB() == null && cryptoDataByte.getPubKeyA() == null) {
            cryptoDataByte.setMessage("No any public key found.");
            return;
        }

        byte[] priKeyBytes = new byte[0];
        byte[] pubKeyBytes = new byte[0];

        Result result = checkPriKeyAndPubKey(cryptoDataByte, priKeyBytes, pubKeyBytes);
        if (result == null) return;

        byte[] symKey =
                asyKeyToSymKey(result.priKeyBytes(), result.pubKeyBytes(),cryptoDataByte.getIv());
        if(symKey==null){
            cryptoDataByte.setMessage("Failed to make symKey from the priKey and the pubKey of another party.");
            return;
        }
        cryptoDataByte.setSymKey(symKey);

        if (cryptoDataByte.getSum() != null) {
            if (!isGoodAesSum(cryptoDataByte)) {
                cryptoDataByte.setCodeMessage(20);
                return;
            }
        }

        byte[] msgBytes = new byte[0];
        try {
            msgBytes = Aes256CbcP7.decrypt(cryptoDataByte.getCipher(), symKey, cryptoDataByte.getIv());
        } catch (NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException |
                 NoSuchAlgorithmException | NoSuchProviderException | IllegalBlockSizeException |
                 BadPaddingException e) {
            cryptoDataByte.setMessage("Decrypt message wrong: " + e.getMessage());
        }
        cryptoDataByte.setData(msgBytes);
        cryptoDataByte.setSymKey(symKey);

        cryptoDataByte.clearAllSensitiveDataButSymKey();
    }

    @org.jetbrains.annotations.Nullable
    private static Result checkPriKeyAndPubKey(CryptoDataByte cryptoDataByte, byte[] priKeyBytes, byte[] pubKeyBytes) {
        if (cryptoDataByte.getType() == EncryptType.AsyOneWay) {
            priKeyBytes = cryptoDataByte.getPriKeyB();
            if (priKeyBytes == null || BytesTools.isFilledKey(priKeyBytes)) {
                cryptoDataByte.setMessage("The private key is null or filled with 0.");
                return null;
            }
            pubKeyBytes = cryptoDataByte.getPubKeyA();
        } else if (cryptoDataByte.getType() == EncryptType.AsyTwoWay) {
            boolean found = false;
            if (cryptoDataByte.getPriKeyB() != null && !BytesTools.isFilledKey(cryptoDataByte.getPriKeyB())) {
                if (cryptoDataByte.getPubKeyA() != null) {
                    if (isTheKeyPair(cryptoDataByte.getPubKeyA(), cryptoDataByte.getPriKeyB())) {
                        if (isTheKeyPair(cryptoDataByte.getPubKeyB(), cryptoDataByte.getPriKeyB())) {
                            found = false;
                        } else {
                            found = true;
                            priKeyBytes = cryptoDataByte.getPriKeyB();
                            pubKeyBytes = cryptoDataByte.getPubKeyB();
                        }
                    } else {
                        found = true;
                        priKeyBytes = cryptoDataByte.getPriKeyB();
                        pubKeyBytes = cryptoDataByte.getPubKeyA();
                    }
                }
            } else if (cryptoDataByte.getPubKeyA() != null && !BytesTools.isFilledKey(cryptoDataByte.getPriKeyA())) {
                if (isTheKeyPair(cryptoDataByte.getPubKeyA(), cryptoDataByte.getPriKeyA())) {
                    if (isTheKeyPair(cryptoDataByte.getPubKeyB(), cryptoDataByte.getPriKeyA())) {
                        found = false;
                    } else {
                        found = true;
                        priKeyBytes = cryptoDataByte.getPriKeyA();
                        pubKeyBytes = cryptoDataByte.getPubKeyB();
                    }
                } else {
                    found = true;
                    priKeyBytes = cryptoDataByte.getPriKeyA();
                    pubKeyBytes = cryptoDataByte.getPubKeyA();
                }
            }

            if (!found) {
                cryptoDataByte.setMessage("Private key or public key absent, or the private key and the public key is a pair.");
                return null;
            }
        } else {
            cryptoDataByte.setMessage("Wrong type:" + cryptoDataByte.getType());
            return null;
        }
        return new Result(priKeyBytes, pubKeyBytes);
    }

    private record Result(byte[] priKeyBytes, byte[] pubKeyBytes) {
    }

    public static byte[] asyKeyToSymKey(byte[] priKeyBytes, byte[] pubKeyBytes, byte[] iv) {
        byte[] symKey;
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }

        byte[] sharedSecret = getSharedSecret(priKeyBytes, pubKeyBytes);

        byte[] sharedSecretHash;

        sharedSecretHash = sha256.digest(sharedSecret);
        byte[] secretHashWithIv = BytesTools.addByteArray(sharedSecretHash, iv);
        symKey = sha256.digest(sha256.digest(secretHashWithIv));

        clearByteArray(sharedSecret);
        return symKey;
    }

    private void decryptWithPassword(CryptoDataByte cryptoDataByte) {
        if (!isGoodPasswordDecryptParams(cryptoDataByte)) {
            //eccAesDataByte.clearAllSensitiveData();
            return;
        }
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] symKeyBytes = makeSymKeyFromPassword(cryptoDataByte, sha256, cryptoDataByte.getIv());
            cryptoDataByte.setSymKey(symKeyBytes);
            if (!isGoodAesSum(cryptoDataByte)) {
                cryptoDataByte.setCodeMessage(20);
                return;
            }
            byte[] msg = Aes256CbcP7.decrypt(cryptoDataByte.getCipher(), cryptoDataByte.getSymKey(), cryptoDataByte.getIv());
            cryptoDataByte.setData(msg);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | NoSuchProviderException |
                 InvalidAlgorithmParameterException | InvalidKeyException | IllegalBlockSizeException |
                 BadPaddingException e) {
            cryptoDataByte.setMessage("Decrypt with password wrong: " + e.getMessage());
        }
    }

    public String encryptAsyOneWayBundle(String msg, String pubKeyB) {
        CryptoDataStr cryptoDataStr = new CryptoDataStr(EncryptType.AsyOneWay, msg, pubKeyB);
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);

        encrypt(cryptoDataByte);
        cryptoDataByte.clearSymKey();

        byte[] bundleBytes = BytesTools.addByteArray(BytesTools.addByteArray(cryptoDataByte.getPubKeyA(), cryptoDataByte.getIv()), cryptoDataByte.getCipher());
        if (cryptoDataByte.getMessage() != null) return "Error:" + cryptoDataByte.getMessage();
        return Base64.getEncoder().encodeToString(bundleBytes);
    }

    public String decryptAsyOneWayBundle(String bundle, char[] priKeyB) {
        byte[] bundleBytes = Base64.getDecoder().decode(bundle);
        byte[] pubKeyABytes = new byte[33];
        byte[] ivBytes = new byte[16];
        byte[] cipherBytes = new byte[bundleBytes.length - pubKeyABytes.length - ivBytes.length];
        byte[] priKeyBBytes = BytesTools.hexCharArrayToByteArray(priKeyB);

        pubKeyABytes = Arrays.copyOfRange(bundleBytes, 0, 33);
        ivBytes = Arrays.copyOfRange(bundleBytes, 33, 49);
        cipherBytes = Arrays.copyOfRange(bundleBytes, 33 + 16, bundleBytes.length);

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.AsyOneWay);
        cryptoDataByte.setIv(ivBytes);
        cryptoDataByte.setPubKeyA(pubKeyABytes);
        cryptoDataByte.setPriKeyB(priKeyBBytes);
        cryptoDataByte.setCipher(cipherBytes);

        decrypt(cryptoDataByte);
        cryptoDataByte.clearSymKey();

        if (cryptoDataByte.getMessage() != null) {
            return "Error:" + cryptoDataByte.getMessage();
        }
        return new String(cryptoDataByte.getData(), StandardCharsets.UTF_8);
    }

    public String encryptAsyTwoWayBundle(String msg, String pubKeyB, char[] priKeyA) {
        CryptoDataStr cryptoDataStr = new CryptoDataStr(EncryptType.AsyTwoWay, msg, pubKeyB, priKeyA);
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);

        encrypt(cryptoDataByte);
        cryptoDataByte.clearSymKey();

        byte[] bundleBytes = BytesTools.addByteArray(cryptoDataByte.getIv(), cryptoDataByte.getCipher());
        if (cryptoDataByte.getMessage() != null) return "Error:" + cryptoDataByte.getMessage();
        return Base64.getEncoder().encodeToString(bundleBytes);
    }

    public String decryptAsyTwoWayBundle(String bundle, String pubKeyA, char[] priKeyB) {
        byte[] priKeyBBytes = BytesTools.hexCharArrayToByteArray(priKeyB);
        byte[] pubKeyABytes = HexFormat.of().parseHex(pubKeyA);

        byte[] bundleBytes = Base64.getDecoder().decode(bundle);
        byte[] ivBytes = Arrays.copyOfRange(bundleBytes, 0, 16);
        byte[] cipherBytes = Arrays.copyOfRange(bundleBytes, 16, bundleBytes.length);

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.AsyTwoWay);
        cryptoDataByte.setIv(ivBytes);
        cryptoDataByte.setPubKeyA(pubKeyABytes);
        cryptoDataByte.setPriKeyB(priKeyBBytes);
        cryptoDataByte.setCipher(cipherBytes);

        decrypt(cryptoDataByte);
        cryptoDataByte.clearSymKey();

        if (cryptoDataByte.getMessage() != null) {
            return "Error:" + cryptoDataByte.getMessage();
        }
        return new String(cryptoDataByte.getData(), StandardCharsets.UTF_8);
    }

    public String encryptSymKeyBundle(String msg, char[] symKey) {
        CryptoDataStr cryptoDataStr = new CryptoDataStr(EncryptType.SymKey, msg, symKey);

        encrypt(cryptoDataStr);
        String iv = cryptoDataStr.getIv();
        String cipher = cryptoDataStr.getCipher();
        byte[] ivBytes = HexFormat.of().parseHex(iv);
        byte[] cipherBytes = Base64.getDecoder().decode(cipher);

        byte[] ivCipherBytes = BytesTools.addByteArray(ivBytes, cipherBytes);
        String bundle = Base64.getEncoder().encodeToString(ivCipherBytes);
        //eccAesData.clearAllSensitiveData();
        if (cryptoDataStr.getMessage() != null) return "Error:" + cryptoDataStr.getMessage();
        return bundle;
    }

    public String encryptPasswordBundle(String msg, char[] password) {
        CryptoDataStr cryptoDataStr = new CryptoDataStr(EncryptType.Password, msg, password);

        encrypt(cryptoDataStr);
        String iv = cryptoDataStr.getIv();
        String cipher = cryptoDataStr.getCipher();
        byte[] ivBytes = HexFormat.of().parseHex(iv);
        byte[] cipherBytes = Base64.getDecoder().decode(cipher);

        byte[] ivCipherBytes = BytesTools.addByteArray(ivBytes, cipherBytes);
        String bundle = Base64.getEncoder().encodeToString(ivCipherBytes);
        //eccAesData.clearAllSensitiveData();
        if (cryptoDataStr.getMessage() != null) return "Error:" + cryptoDataStr.getMessage();
        return bundle;
    }

    public String decryptSymKeyBundle(String bundle, char[] symKey) {
        CryptoDataByte cryptoDataByte = makeIvCipherToCryptoDataByte(Base64.getDecoder().decode(bundle));
        cryptoDataByte.setSymKey(BytesTools.hexCharArrayToByteArray(symKey));
        decrypt(cryptoDataByte);

        if (cryptoDataByte.getMessage() != null) return "Error:" + cryptoDataByte.getMessage();

        return new String(cryptoDataByte.getData(), StandardCharsets.UTF_8);
    }

    public byte[] encryptAsyOneWayBundle(byte[] msg, byte[] pubKeyB) {

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.AsyOneWay);
        cryptoDataByte.setData(msg);
        cryptoDataByte.setPubKeyB(pubKeyB);

        encrypt(cryptoDataByte);
        cryptoDataByte.clearSymKey();

        if (cryptoDataByte.getMessage() != null) return null;
        return BytesTools.addByteArray(BytesTools.addByteArray(cryptoDataByte.getPubKeyA(), cryptoDataByte.getIv()), cryptoDataByte.getCipher());
    }

    public byte[] decryptAsyOneWayBundle(byte[] bundle, byte[] priKeyB) {

        byte[] pubKeyA = Arrays.copyOfRange(bundle, 0, 33);
        byte[] ivBytes = Arrays.copyOfRange(bundle, 33, 49);
        byte[] cipherBytes = Arrays.copyOfRange(bundle, 33 + 16, bundle.length);

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.AsyOneWay);
        cryptoDataByte.setIv(ivBytes);
        cryptoDataByte.setPubKeyA(pubKeyA);
        cryptoDataByte.setPriKeyB(priKeyB);
        cryptoDataByte.setCipher(cipherBytes);

        decrypt(cryptoDataByte);
        cryptoDataByte.clearSymKey();

        if (cryptoDataByte.getMessage() != null) return ("Error:" + cryptoDataByte.getMessage()).getBytes();
        return cryptoDataByte.getData();
    }

    public byte[] encryptAsyTwoWayBundle(byte[] msg, byte[] pubKeyB, byte[] priKeyA) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.AsyTwoWay);
        cryptoDataByte.setData(msg);
        cryptoDataByte.setPubKeyB(pubKeyB);
        cryptoDataByte.setPriKeyA(priKeyA);

        encrypt(cryptoDataByte);
        cryptoDataByte.clearSymKey();

        if (cryptoDataByte.getMessage() != null) return null;
        return BytesTools.addByteArray(cryptoDataByte.getIv(), cryptoDataByte.getCipher());
    }

    public byte[] decryptAsyTwoWayBundle(byte[] bundle, byte[] pubKeyA, byte[] priKeyB) {

        byte[] iv = Arrays.copyOfRange(bundle, 0, 16);
        byte[] cipher = Arrays.copyOfRange(bundle, 16, bundle.length);

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.AsyTwoWay);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.setCipher(cipher);
        cryptoDataByte.setPubKeyA(pubKeyA);
        cryptoDataByte.setPriKeyB(priKeyB);

        decrypt(cryptoDataByte);
        cryptoDataByte.clearSymKey();

        if (cryptoDataByte.getMessage() != null) return ("Error:" + cryptoDataByte.getMessage()).getBytes();
        return cryptoDataByte.getData();
    }

    public byte[] encryptSymKeyBundle(byte[] msg, byte[] symKey) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();

        cryptoDataByte.setType(EncryptType.SymKey);
        cryptoDataByte.setSymKey(symKey);
        cryptoDataByte.setData(msg);

        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return BytesTools.addByteArray(cryptoDataByte.getIv(), cryptoDataByte.getCipher());
    }

    public byte[] decryptSymKeyBundle(byte[] bundle, byte[] symKey) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        byte[] iv = Arrays.copyOfRange(bundle, 0, 16);
        byte[] cipher = Arrays.copyOfRange(bundle, 16, bundle.length);
        cryptoDataByte.setType(EncryptType.SymKey);
        cryptoDataByte.setSymKey(symKey);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.setCipher(cipher);

        decrypt(cryptoDataByte);
        if (cryptoDataByte.getMessage() != null) return ("Error:" + cryptoDataByte.getMessage()).getBytes();
        return cryptoDataByte.getData();
    }

    public byte[] encryptPasswordBundle(byte[] msg, byte[] password) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();

        cryptoDataByte.setType(EncryptType.Password);
        cryptoDataByte.setPassword(password);
        cryptoDataByte.setData(msg);

        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return BytesTools.addByteArray(cryptoDataByte.getIv(), cryptoDataByte.getCipher());
    }

    public byte[] decryptPasswordBundle(byte[] bundle, byte[] password) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        byte[] iv = Arrays.copyOfRange(bundle, 0, 16);
        byte[] cipher = Arrays.copyOfRange(bundle, 16, bundle.length);
        cryptoDataByte.setType(EncryptType.Password);
        cryptoDataByte.setPassword(password);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.setCipher(cipher);

        decrypt(cryptoDataByte);
        if (cryptoDataByte.getMessage() != null) return ("Error:" + cryptoDataByte.getMessage()).getBytes();
        return cryptoDataByte.getData();
    }

    private void encryptAsy(CryptoDataByte cryptoDataByte) {
        if (!isGoodEncryptParams(cryptoDataByte)) {
            return;
        }

        // Generate IV
        byte[] iv = getRandomIv();
        cryptoDataByte.setIv(iv);

        //Make sharedSecret
        byte[] priKey;
        byte[] pubKey = cryptoDataByte.getPubKeyB();

        byte[] priKeyABytes = cryptoDataByte.getPriKeyA();
        if (priKeyABytes != null) {
            priKey=priKeyABytes;
        } else {
            priKey = makeRandomKeyPair(cryptoDataByte);
        }

        byte[] symKey =
                asyKeyToSymKey(priKey, pubKey, cryptoDataByte.getIv());
        if(symKey==null){
            cryptoDataByte.setMessage("Failed to make symKey from the priKey and the pubKey of another party.");
            return;
        }

        cryptoDataByte.setSymKey(symKey);

        // Encrypt the original AES key with the shared secret key
        aesEncrypt(cryptoDataByte);
    }

    private byte[] makeRandomKeyPair(CryptoDataByte cryptoDataByte) {
        byte[] priKey;
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256k1");
        ECDomainParameters domainParameters = new ECDomainParameters(spec.getCurve(), spec.getG(), spec.getN(), spec.getH(), spec.getSeed());

        // Generate EC key pair for sender
        ECKeyPairGenerator generator = new ECKeyPairGenerator();
        generator.init(new ECKeyGenerationParameters(domainParameters, new SecureRandom()));

        AsymmetricCipherKeyPair keyPair = generator.generateKeyPair();

        ECPrivateKeyParameters newPriKey = (ECPrivateKeyParameters) keyPair.getPrivate();
        byte[] newPubKey = pubKeyToBytes(pubKeyFromPriKey(newPriKey));


        priKey = priKeyToBytes(newPriKey);
        cryptoDataByte.setPubKeyA(newPubKey);
        return priKey;
    }

    private void encryptWithPassword(CryptoDataByte cryptoDataByte) {

        cryptoDataByte.setType(EncryptType.Password);
        if (!isGoodEncryptParams(cryptoDataByte)) {
            //eccAesDataByte.clearAllSensitiveData();
            return;
        }

        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            cryptoDataByte.setMessage("Create sha256 digester wrong:" + e.getMessage());
            return;
        }
        byte[] iv = getRandomIv();
        cryptoDataByte.setIv(iv);
        byte[] symKeyBytes = makeSymKeyFromPassword(cryptoDataByte, sha256, iv);
        cryptoDataByte.setSymKey(symKeyBytes);
        aesEncrypt(cryptoDataByte);
    }

    private void encryptWithSymKey(CryptoDataByte cryptoDataByte) {

        if (!isGoodEncryptParams(cryptoDataByte)) {
            //eccAesDataByte.clearAllSensitiveData();
            return;
        }
        cryptoDataByte.setType(EncryptType.SymKey);
        isGoodEncryptParams(cryptoDataByte);
        cryptoDataByte.setIv(getRandomIv());
        aesEncrypt(cryptoDataByte);
        cryptoDataByte.clearAllSensitiveDataButSymKey();
    }

    private void decryptWithSymKey(CryptoDataByte cryptoDataByte) {
        if (!isGoodSymKeyDecryptParams(cryptoDataByte)) {
            //eccAesDataByte.clearAllSensitiveData();
            return;
        }
        try {
            if (!isGoodAesSum(cryptoDataByte)) {
                cryptoDataByte.setCodeMessage(20);
                return;
            }
            byte[] msg = Aes256CbcP7.decrypt(cryptoDataByte.getCipher(), cryptoDataByte.getSymKey(), cryptoDataByte.getIv());
            cryptoDataByte.setData(msg);
            cryptoDataByte.clearAllSensitiveDataButSymKey();
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | NoSuchProviderException |
                 InvalidAlgorithmParameterException | InvalidKeyException | IllegalBlockSizeException |
                 BadPaddingException e) {
            cryptoDataByte.setMessage("Decrypt with symKey wrong: " + e.getMessage());
        }
    }

    public void aesEncrypt(CryptoDataByte cryptoDataByte) {

        if (!isGoodEncryptParams(cryptoDataByte)) {
            //eccAesDataByte.clearAllSensitiveData();
            return;
        }

        byte[] iv = cryptoDataByte.getIv();
        byte[] msgBytes = cryptoDataByte.getData();
        byte[] symKeyBytes = cryptoDataByte.getSymKey();
        MessageDigest sha256;
        byte[] cipher;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
            cipher = Aes256CbcP7.encrypt(msgBytes, symKeyBytes, iv);
        } catch (NoSuchAlgorithmException | NoSuchProviderException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            cryptoDataByte.setMessage("Aes encrypting wrong: " + e.getMessage());
            return;
        }
        byte[] sum4 = getSum4(sha256, symKeyBytes, iv, cipher);
        cryptoDataByte.setCipher(cipher);
        cryptoDataByte.setSum(sum4);
        cryptoDataByte.setData(null);
        cryptoDataByte.clearAllSensitiveDataButSymKey();
    }
//
//    private boolean aesEncryptFile(String sourceFilePathName,String destFilePathName,byte[] key) {
//
//        EccAesDataByte eccAesDataByte = new EccAesDataByte();
//        eccAesDataByte.setType(EccAesType.SymKey);
//        eccAesDataByte.setAlg(Algorithm.EccAes256K1P7_No1_NrC7.name());
//        byte[] iv = BytesTools.getRandomBytes(16);
//        eccAesDataByte.setIv(iv);
//
//        String tempFilePath = FileTools.getTempFileName();
//        try {
//            Aes256CbcP7.encryptFile(sourceFilePathName,tempFilePath,key);
//        } catch (Exception e) {
//            log.debug("Failed to encrypt file:"+e.getMessage());
//            return false;
//        }
//        byte[] cipherDid = Hash.Sha256x2Bytes(new File(destFilePathName));
//        byte[] sum = getSum4(key, iv, cipherDid);
//        eccAesDataByte.setSum(sum);
//
//        FileTools.createFileWithDirectories(destFilePathName);
//        FileOutputStream fos = new FileOutputStream(destFilePathName);
//        FileInputStream fis = new FileInputStream(tempFilePath);
//        EccAesData eccAesData = EccAesData.fromEccAesDataByte(eccAesDataByte);
//        byte[] headBytes = JsonTools.getString(eccAesData).getBytes();
//        fos.write(headBytes);
//
//        byte[] buffer = new byte[8192];
//        int bytesRead;
//        long bytesLength = 0;
//        while ((bytesRead = fis.read(buffer)) != -1) {
//            // Write the bytes read from the request input stream to the output stream
//            fos.write(buffer, 0, bytesRead);
//            hasher.putBytes(buffer, 0, bytesRead);
//            bytesLength +=bytesRead;
//        }
//
//
//        byte[] msgBytes = eccAesDataByte.getMsg();
//        byte[] symKeyBytes = eccAesDataByte.getSymKey();
//        MessageDigest sha256;
//        byte[] cipher;
//        try {
//            sha256 = MessageDigest.getInstance("SHA-256");
//            cipher = Aes256CbcP7.encrypt(msgBytes, symKeyBytes, iv);
//        } catch (NoSuchAlgorithmException | NoSuchProviderException | NoSuchPaddingException | InvalidKeyException |
//                 InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
//            eccAesDataByte.setError("Aes encrypting wrong: " + e.getMessage());
//            return;
//        }
//        byte[] sum4 = getSum4(sha256, symKeyBytes, iv, cipher);
//        eccAesDataByte.setCipher(cipher);
//        eccAesDataByte.setSum(sum4);
//        eccAesDataByte.setMsg(null);
//        eccAesDataByte.clearAllSensitiveDataButSymKey();
//    }

    private byte[] makeSymKeyFromPassword(CryptoDataByte cryptoDataByte, MessageDigest sha256, byte[] iv) {
        byte[] symKeyBytes = sha256.digest(BytesTools.addByteArray(sha256.digest(cryptoDataByte.getPassword()), iv));
        return symKeyBytes;
    }

    private static byte[] getSharedSecret(byte[] priKeyBytes, byte[] pubKeyBytes) {

        ECPrivateKeyParameters priKey = priKeyFromBytes(priKeyBytes);
        ECPublicKeyParameters pubKey = pubKeyFromBytes(pubKeyBytes);
        ECDHBasicAgreement agreement = new ECDHBasicAgreement();
        agreement.init(priKey);
        return agreement.calculateAgreement(pubKey).toByteArray();
    }

    public static ECPublicKeyParameters pubKeyFromBytes(byte[] publicKeyBytes) {

        X9ECParameters ecParameters = org.bouncycastle.asn1.sec.SECNamedCurves.getByName("secp256k1");
        ECDomainParameters domainParameters = new ECDomainParameters(ecParameters.getCurve(), ecParameters.getG(), ecParameters.getN(), ecParameters.getH());

        ECCurve curve = domainParameters.getCurve();

        ECPoint point = curve.decodePoint(publicKeyBytes);

        return new ECPublicKeyParameters(point, domainParameters);
    }

    public ECPublicKeyParameters pubKeyFromHex(String publicKeyHex) {
        return pubKeyFromBytes(HexFormat.of().parseHex(publicKeyHex));
    }

    public String pubKeyToHex(ECPublicKeyParameters publicKey) {
        return Hex.toHexString(pubKeyToBytes(publicKey));
    }

    public String priKeyToHex(ECPrivateKeyParameters privateKey) {
        BigInteger privateKeyValue = privateKey.getD();
        String hex = privateKeyValue.toString(16);
        while (hex.length() < 64) {  // 64 is for 256-bit key
            hex = "0" + hex;
        }
        return hex;
    }

    public byte[] priKeyToBytes(ECPrivateKeyParameters privateKey) {
        return HexFormat.of().parseHex(priKeyToHex(privateKey));//Hex.decode(priKeyToHex(privateKey));
    }

    public static byte[] getPartOfBytes(byte[] original, int offset, int length) {
        byte[] part = new byte[length];
        System.arraycopy(original, offset, part, 0, part.length);
        return part;
    }

    private boolean isErrorAlgAndType(CryptoDataByte cryptoDataByte) {
        if (cryptoDataByte.getMessage() != null) {
            cryptoDataByte.setMessage("There was an error. Check it at first:" + cryptoDataByte.getMessage() + " .");
            //eccAesDataByte.clearAllSensitiveData();
            return true;
        }

        if (cryptoDataByte.getAlg() == null) {
            cryptoDataByte.setAlg(EccAes256K1P7_No1_NrC7);
        } else if (!cryptoDataByte.getAlg().equals(EccAes256K1P7_No1_NrC7)) {
            cryptoDataByte.setMessage("This method only used by the algorithm " + EccAes256K1P7_No1_NrC7+ " .");
            //eccAesDataByte.clearAllSensitiveData();
            return true;
        }

        if (cryptoDataByte.getType() == null) {
            cryptoDataByte.setMessage("EccAesType is required.");
            //eccAesDataByte.clearAllSensitiveData();
            return true;
        }
        return false;
    }

    private boolean isGoodEncryptParams(CryptoDataByte cryptoDataByte) {
        EncryptType type = cryptoDataByte.getType();
        switch (type) {
            case AsyOneWay -> {
                return isGoodAsyOneWayEncryptParams(cryptoDataByte);
            }
            case AsyTwoWay -> {
                return isGoodAsyTwoWayEncryptParams(cryptoDataByte);
            }
            case SymKey -> {
                return isGoodSymKeyEncryptParams(cryptoDataByte);
            }
            case Password -> {
                return isGoodPasswordEncryptParams(cryptoDataByte);
            }
            default -> cryptoDataByte.setMessage("Wrong type: " + cryptoDataByte.getType());
        }
        return true;
    }

    private boolean isGoodEncryptParams(CryptoDataStr cryptoDataStr) {
        EncryptType type = cryptoDataStr.getType();
        switch (type) {
            case AsyOneWay -> {
                return isGoodAsyOneWayEncryptParams(cryptoDataStr);
            }
            case AsyTwoWay -> {
                return isGoodAsyTwoWayEncryptParams(cryptoDataStr);
            }
            case SymKey -> {
                return isGoodSymKeyEncryptParams(cryptoDataStr);
            }
            case Password -> {
                return isGoodPasswordEncryptParams(cryptoDataStr);
            }
            default -> cryptoDataStr.setMessage("Wrong type: " + cryptoDataStr.getType());
        }
        return true;
    }

    private boolean isGoodPasswordEncryptParams(CryptoDataByte cryptoDataByte) {

        if (cryptoDataByte.getData() == null) {
            cryptoDataByte.setMessage(EncryptType.Password.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoDataByte.getPassword() == null) {
            cryptoDataByte.setMessage(EncryptType.Password.name() + " parameters lack password.");
            return false;
        }

        return true;
    }

    private boolean isGoodSymKeyEncryptParams(CryptoDataByte cryptoDataByte) {

        if (cryptoDataByte.getData() == null) {
            cryptoDataByte.setMessage(EncryptType.SymKey.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoDataByte.getSymKey() == null) {
            cryptoDataByte.setMessage(EncryptType.SymKey.name() + " parameters lack symKey.");
            return false;
        }

        if (cryptoDataByte.getSymKey().length != Constants.SYM_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.SymKey.name() + " parameter symKey should be " + Constants.SYM_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getSymKey().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isGoodAsyTwoWayEncryptParams(CryptoDataByte cryptoDataByte) {

        if (cryptoDataByte.getData() == null) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoDataByte.getPubKeyB() == null) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameters lack pubKeyB.");
            return false;
        }

        if (cryptoDataByte.getPriKeyA() == null) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameters lack priKeyA.");
            return false;
        }

        if (cryptoDataByte.getPubKeyB().length != Constants.PUBLIC_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameter pubKeyB should be " + Constants.PUBLIC_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPubKeyB().length + " now.");
            return false;
        }

        if (cryptoDataByte.getPriKeyA().length != Constants.PRIVATE_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameter priKeyA should be " + Constants.PRIVATE_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPriKeyA().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isGoodAsyOneWayEncryptParams(CryptoDataByte cryptoDataByte) {
        if (cryptoDataByte.getData() == null) {
            cryptoDataByte.setMessage(EncryptType.AsyOneWay.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoDataByte.getPubKeyB() == null) {
            cryptoDataByte.setMessage(EncryptType.AsyOneWay.name() + " parameters lack pubKeyB.");
            return false;
        }

        if (cryptoDataByte.getPubKeyB().length != Constants.PUBLIC_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.AsyOneWay.name() + " parameter symKey should be " + Constants.PUBLIC_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getSymKey().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isGoodPasswordEncryptParams(CryptoDataStr cryptoDataStr) {

        if (cryptoDataStr.getData() == null) {
            cryptoDataStr.setMessage(EncryptType.Password.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoDataStr.getPassword() == null) {
            cryptoDataStr.setMessage(EncryptType.Password.name() + " parameters lack password.");
            return false;
        }

        return true;
    }

    private boolean isGoodSymKeyEncryptParams(CryptoDataStr cryptoDataStr) {

        if (cryptoDataStr.getData() == null) {
            cryptoDataStr.setMessage(EncryptType.SymKey.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoDataStr.getSymKey() == null) {
            cryptoDataStr.setMessage(EncryptType.SymKey.name() + " parameters lack symKey.");
            return false;
        }

        if (cryptoDataStr.getSymKey().length != Constants.SYM_KEY_BYTES_LENGTH * 2) {
            cryptoDataStr.setMessage(EncryptType.SymKey.name() + " parameter symKey should be " + Constants.SYM_KEY_BYTES_LENGTH * 2 + " characters. It is " + cryptoDataStr.getSymKey().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isGoodAsyTwoWayEncryptParams(CryptoDataStr cryptoDataStr) {

        if (cryptoDataStr.getData() == null) {
            cryptoDataStr.setMessage(EncryptType.AsyTwoWay.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoDataStr.getPubKeyB() == null) {
            cryptoDataStr.setMessage(EncryptType.AsyTwoWay.name() + " parameters lack pubKeyB.");
            return false;
        }

        if (cryptoDataStr.getPriKeyA() == null) {
            cryptoDataStr.setMessage(EncryptType.AsyTwoWay.name() + " parameters lack priKeyA.");
            return false;
        }

        if (cryptoDataStr.getPubKeyB().length() != Constants.PUBLIC_KEY_BYTES_LENGTH * 2) {
            cryptoDataStr.setMessage(EncryptType.AsyTwoWay.name() + " parameter pubKeyB should be " + Constants.PUBLIC_KEY_BYTES_LENGTH * 2 + " characters. It is " + cryptoDataStr.getPubKeyB().length() + " now.");
            return false;
        }

        if (cryptoDataStr.getPriKeyA().length != Constants.PRIVATE_KEY_BYTES_LENGTH * 2) {
            cryptoDataStr.setMessage(EncryptType.AsyTwoWay.name() + " parameter priKeyA should be " + Constants.PRIVATE_KEY_BYTES_LENGTH + " characters. It is " + cryptoDataStr.getPriKeyA().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isGoodAsyOneWayEncryptParams(CryptoDataStr cryptoDataStr) {
        if (cryptoDataStr.getData() == null) {
            cryptoDataStr.setMessage(EncryptType.AsyOneWay.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoDataStr.getPubKeyB() == null) {
            cryptoDataStr.setMessage(EncryptType.AsyOneWay.name() + " parameters lack pubKeyB.");
            return false;
        }

        if (cryptoDataStr.getPubKeyB().length() != Constants.PUBLIC_KEY_BYTES_LENGTH * 2) {
            cryptoDataStr.setMessage(EncryptType.AsyOneWay.name() + " parameter symKey should be " + Constants.PUBLIC_KEY_BYTES_LENGTH + " characters. It is " + cryptoDataStr.getSymKey().length + " now.");
            return false;
        }

        return true;
    }

    public boolean isGoodAesSum(CryptoDataByte cryptoDataByte) {

        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        byte[] sum4 = getSum4(sha256, cryptoDataByte.getSymKey(), cryptoDataByte.getIv(), cryptoDataByte.getCipher());
        if (!Arrays.equals(sum4, cryptoDataByte.getSum())) {
            cryptoDataByte.setMessage("The sum  is not equal to the value of sha256(symKey+iv+cipher).");
            return false;
        }
        return true;
    }

    private boolean isGoodDecryptParams(CryptoDataByte cryptoDataByte) {
        EncryptType type = cryptoDataByte.getType();
        switch (type) {
            case AsyOneWay -> {
                return isGoodAsyOneWayDecryptParams(cryptoDataByte);
            }
            case AsyTwoWay -> {
                return isGoodAsyTwoWayDecryptParams(cryptoDataByte);
            }
            case SymKey -> {
                return isGoodSymKeyDecryptParams(cryptoDataByte);
            }
            case Password -> {
                return isGoodPasswordDecryptParams(cryptoDataByte);
            }
            default -> cryptoDataByte.setMessage("Wrong type: " + cryptoDataByte.getType());
        }
        return true;
    }

    private boolean isGoodPasswordDecryptParams(CryptoDataByte cryptoDataByte) {

        if (cryptoDataByte.getCipher() == null) {
            cryptoDataByte.setMessage(EncryptType.SymKey.name() + " parameters lack cipher.");
            return false;
        }

        if (cryptoDataByte.getIv() == null) {
            cryptoDataByte.setMessage(EncryptType.SymKey.name() + " parameters lack iv.");
            return false;
        }

        if (cryptoDataByte.getPassword() == null || isZero(cryptoDataByte.getPassword())) {
            cryptoDataByte.setMessage(EncryptType.Password.name() + " parameters lack password.");
            return false;
        }

        if (cryptoDataByte.getIv().length != Constants.IV_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.SymKey.name() + " parameter iv should be " + Constants.IV_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getIv().length + " now.");
            return false;
        }
        return true;
    }

    private boolean isGoodSymKeyDecryptParams(CryptoDataByte cryptoDataByte) {
        if (cryptoDataByte.getCipher() == null) {
            cryptoDataByte.setMessage(EncryptType.SymKey.name() + " parameters lack cipher.");
            return false;
        }

        if (cryptoDataByte.getIv() == null) {
            cryptoDataByte.setMessage(EncryptType.SymKey.name() + " parameters lack iv.");
            return false;
        }

        if (cryptoDataByte.getSymKey() == null || isZero(cryptoDataByte.getSymKey())) {
            cryptoDataByte.setMessage(EncryptType.SymKey.name() + " parameters lack symKey.");
            return false;
        }

        if (cryptoDataByte.getIv().length != Constants.IV_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.SymKey.name() + " parameter iv should be " + Constants.IV_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getIv().length + " now.");
            return false;
        }

        if (cryptoDataByte.getSymKey() != null && cryptoDataByte.getSymKey().length != Constants.SYM_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.SymKey.name() + " parameter symKey should be " + Constants.SYM_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getSymKey().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isGoodAsyTwoWayDecryptParams(CryptoDataByte cryptoDataByte) {

        if (cryptoDataByte.getCipher() == null) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameters lack cipher.");
            return false;
        }

        if (cryptoDataByte.getIv() == null) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameters lack iv.");
            return false;
        }

        if (cryptoDataByte.getPubKeyA() == null || cryptoDataByte.getPubKeyA() == null) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameters lack pubKeyA and pubKeyB.");
            return false;
        }

        if ((cryptoDataByte.getPriKeyB() == null || isZero(cryptoDataByte.getPriKeyB()))
                && (cryptoDataByte.getPriKeyA() == null || isZero(cryptoDataByte.getPriKeyA()))) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameters lack both priKeyA and priKeyB.");
            return false;
        }

        if (cryptoDataByte.getPubKeyA() != null && cryptoDataByte.getPubKeyA().length != Constants.PUBLIC_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameter pubKeyA should be " + Constants.PUBLIC_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPubKeyA().length + " now.");
            return false;
        }

        if (cryptoDataByte.getPubKeyB() != null && cryptoDataByte.getPubKeyB().length != Constants.PUBLIC_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameter pubKeyB should be " + Constants.PUBLIC_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPubKeyB().length + " now.");
            return false;
        }

        if (cryptoDataByte.getPriKeyA() != null && cryptoDataByte.getPriKeyA().length != Constants.PRIVATE_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameter priKeyA should be " + Constants.PRIVATE_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPriKeyA().length + " now.");
            return false;
        }

        if (cryptoDataByte.getPriKeyB() != null && cryptoDataByte.getPriKeyB().length != Constants.PRIVATE_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameter priKeyB should be " + Constants.PRIVATE_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPriKeyB().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isGoodAsyOneWayDecryptParams(CryptoDataByte cryptoDataByte) {
        if (cryptoDataByte.getCipher() == null) {
            cryptoDataByte.setMessage(EncryptType.AsyOneWay.name() + " parameters lack cipher.");
            return false;
        }

        if (cryptoDataByte.getIv() == null) {
            cryptoDataByte.setMessage(EncryptType.AsyOneWay.name() + " parameters lack iv.");
            return false;
        }

        if (cryptoDataByte.getPubKeyA() == null) {
            cryptoDataByte.setMessage(EncryptType.AsyOneWay.name() + " parameters lack pubKeyA.");
            return false;
        }

        if (cryptoDataByte.getPriKeyB() == null || isZero(cryptoDataByte.getPriKeyB())) {
            cryptoDataByte.setMessage(EncryptType.AsyOneWay.name() + " parameters lack priKeyB.");
            return false;
        }

        if (cryptoDataByte.getPubKeyA().length != Constants.PUBLIC_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.AsyOneWay.name() + " parameter pubKeyA should be " + Constants.PUBLIC_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPubKeyA().length + " now.");
            return false;
        }

        if (cryptoDataByte.getPriKeyB().length != Constants.PRIVATE_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.AsyOneWay.name() + " parameter priKeyB should be " + Constants.PRIVATE_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPriKeyB().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isZero(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) return false;
        }
        return true;
    }

    private byte[] getSum4(MessageDigest sha256, byte[] symKey, byte[] iv, byte[] cipher) {
        byte[] sum32 = sha256.digest(BytesTools.addByteArray(symKey, BytesTools.addByteArray(iv, cipher)));
        return getPartOfBytes(sum32, 0, 4);
    }

    public static byte[] getSum4(byte[] symKey, byte[] iv, byte[] cipher) {
        byte[] sum32 = Hash.sha256(BytesTools.addByteArray(symKey, BytesTools.addByteArray(iv, cipher)));
        return getPartOfBytes(sum32, 0, 4);
    }

    public void copyEccAesData(CryptoDataStr fromCryptoDataStr, CryptoDataStr toCryptoDataStr) {
        toCryptoDataStr.setType(fromCryptoDataStr.getType());
        toCryptoDataStr.setAlg(fromCryptoDataStr.getAlg());
        toCryptoDataStr.setData(fromCryptoDataStr.getData());
        toCryptoDataStr.setCipher(fromCryptoDataStr.getCipher());
        toCryptoDataStr.setSymKey(fromCryptoDataStr.getSymKey());
        toCryptoDataStr.setPassword(fromCryptoDataStr.getPassword());
        toCryptoDataStr.setPubKeyA(fromCryptoDataStr.getPubKeyA());
        toCryptoDataStr.setPubKeyB(fromCryptoDataStr.getPubKeyB());
        toCryptoDataStr.setPriKeyA(fromCryptoDataStr.getPriKeyA());
        toCryptoDataStr.setPriKeyB(fromCryptoDataStr.getPriKeyB());
        toCryptoDataStr.setIv(fromCryptoDataStr.getIv());
        toCryptoDataStr.setSum(fromCryptoDataStr.getSum());
        toCryptoDataStr.setMessage(fromCryptoDataStr.getMessage());
    }

    public static void clearByteArray(byte[] array) {
        Arrays.fill(array, (byte) 0);
    }

}
