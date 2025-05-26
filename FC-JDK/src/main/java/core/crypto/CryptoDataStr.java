package core.crypto;

import com.google.gson.GsonBuilder;

import constants.CodeMessage;

import com.google.gson.Gson;
import core.crypto.old.EccAes256K1P7;
import data.fcData.AlgorithmId;
import utils.BytesUtils;
import utils.Hex;


import javax.annotation.Nullable;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

import static utils.BytesUtils.byteArrayToUtf8CharArray;
import static utils.Hex.byteArrayToHexCharArray;
import static utils.JsonUtils.readOneJsonFromFile;

public class CryptoDataStr {
    private EncryptType type;
    private AlgorithmId alg;
    private String data;
    private transient String did;
    private String cipher;
    private transient String cipherId;
    private transient char[] symkey;
    private String keyName;
    private transient char[] password;
    private String pubkeyA;
    private transient String pubkeyB;
    private transient char[] prikeyA;
    private transient char[] prikeyB;
    private String iv;
    private String sum;
    private transient String message;
    private transient Integer code;

    public CryptoDataStr() {
    }

    /**
     * For all types and operations. From Json string without sensitive data.
     */
    public CryptoDataStr(String eccAesDataJson) {
        fromJson(eccAesDataJson);
    }

    /**
     * For all type. Encrypt or Decrypt.
     */
    public CryptoDataStr(String eccAesDataJson, char[] key) {
        fromJson(eccAesDataJson);
        switch (this.type) {
            case AsyOneWay -> prikeyB = key;
            case AsyTwoWay -> checkKeyPairAndSetPriKey(this, key);
            case Symkey -> symkey = key;
            case Password -> password = key;
        }
    }

    /**
     * For AsyOneWay encrypt. The classic encrypting mode.
     */
    public CryptoDataStr(EncryptType asyOneWay, String data, String pubkeyB) {
        if (asyOneWay == EncryptType.AsyOneWay) {
            this.type = asyOneWay;
            this.alg = AlgorithmId.EccAes256K1P7_No1_NrC7;
            this.data = data;
            this.pubkeyB = pubkeyB;
        } else {
            this.message = "Constructing wrong. " + EncryptType.AsyOneWay + " is required for this constructor. ";
        }
    }
    public CryptoDataStr(AlgorithmId alg, EncryptType asyOneWay, String data, String pubkeyB) {
        if (asyOneWay == EncryptType.AsyOneWay) {
            this.type = asyOneWay;
            if(alg!=null)this.alg = alg;
            else this.alg = AlgorithmId.EccAes256K1P7_No1_NrC7;
            this.data = data;
            this.pubkeyB = pubkeyB;
        } else {
            this.message = "Constructing wrong. " + EncryptType.AsyOneWay + " is required for this constructor. ";
        }
    }

    /**
     * For AsyTwoWay encrypt
     */
    public CryptoDataStr(EncryptType asyTwoWay, String data, String pubkeyB, char[] prikeyA) {
        if (asyTwoWay == EncryptType.AsyTwoWay) {
            this.type = asyTwoWay;
            this.alg = AlgorithmId.EccAes256K1P7_No1_NrC7;
            this.data = data;
            this.pubkeyB = pubkeyB;
            this.prikeyA = prikeyA;
        } else {
            this.message = "Constructing wrong. " + EncryptType.AsyTwoWay + " is needed for this constructor. ";
        }
    }
    public CryptoDataStr(AlgorithmId alg, EncryptType asyTwoWay, String data, String pubkeyB, char[] prikeyA) {
        if (asyTwoWay == EncryptType.AsyTwoWay) {
            this.type = asyTwoWay;
            if(alg!=null)this.alg = alg;
            else this.alg = AlgorithmId.EccAes256K1P7_No1_NrC7;
            this.data = data;
            this.pubkeyB = pubkeyB;
            this.prikeyA = prikeyA;
        } else {
            this.message = "Constructing wrong. " + EncryptType.AsyTwoWay + " is needed for this constructor. ";
        }
    }

    /**
     * For Symkey or Password encrypt
     */
    public CryptoDataStr(EncryptType symkeyOrPasswordType, String data, char[] symkeyOrPassword) {
        this.type = symkeyOrPasswordType;
        switch (symkeyOrPasswordType) {
            case Symkey -> symkey = symkeyOrPassword;
            case Password -> password = symkeyOrPassword;
            default ->
                    this.message = "Constructing wrong. " + EncryptType.Symkey + " or " + EncryptType.Password + " is required for this constructor. ";
        }
        this.alg = AlgorithmId.EccAes256K1P7_No1_NrC7;
        this.data = data;
    }
    public CryptoDataStr(AlgorithmId alg, EncryptType symkeyOrPasswordType, String data, char[] symkeyOrPassword) {
        this.type = symkeyOrPasswordType;
        switch (symkeyOrPasswordType) {
            case Symkey -> symkey = symkeyOrPassword;
            case Password -> password = symkeyOrPassword;
            default ->
                    this.message = "Constructing wrong. " + EncryptType.Symkey + " or " + EncryptType.Password + " is required for this constructor. ";
        }
        if(alg!=null)this.alg=alg;
        else this.alg = AlgorithmId.EccAes256K1P7_No1_NrC7;
        this.data = data;
    }

    /**
     * For AsyOneWay or AsyTwoWay decrypt
     */
    public CryptoDataStr(EncryptType asyOneWayOrAsyTwoWayType, String pubkeyA, String pubkeyB, String iv, String cipher, @Nullable String sum, char[] priKey) {
        if (asyOneWayOrAsyTwoWayType == EncryptType.AsyOneWay || asyOneWayOrAsyTwoWayType == EncryptType.AsyTwoWay) {
            byte[] pubKeyBytesA = HexFormat.of().parseHex(pubkeyA);
            byte[] pubKeyBytesB = HexFormat.of().parseHex(pubkeyB);
            byte[] priKeyBytes = BytesUtils.hexCharArrayToByteArray(priKey);
            this.alg = AlgorithmId.EccAes256K1P7_No1_NrC7;
            this.type = asyOneWayOrAsyTwoWayType;
            this.iv = iv;
            this.cipher = cipher;
            this.sum = sum;
            this.pubkeyA = pubkeyA;
            this.pubkeyB = pubkeyB;
            if (EccAes256K1P7.isTheKeyPair(pubKeyBytesA, priKeyBytes)) {
                this.prikeyA = priKey;
            } else if (EccAes256K1P7.isTheKeyPair(pubKeyBytesB, priKeyBytes)) {
                this.prikeyB = priKey;
            } else this.message = "The priKey doesn't match pubKeyA or pubKeyB.";
        } else
            this.message = "Constructing wrong. " + EncryptType.AsyOneWay + " or" + EncryptType.AsyTwoWay + " is required for this constructor. ";

    }
    public CryptoDataStr(AlgorithmId alg, EncryptType asyOneWayOrAsyTwoWayType, String pubkeyA, String pubkeyB, String iv, String cipher, @Nullable String sum, char[] priKey) {
        if (asyOneWayOrAsyTwoWayType == EncryptType.AsyOneWay || asyOneWayOrAsyTwoWayType == EncryptType.AsyTwoWay) {
            byte[] pubKeyBytesA = HexFormat.of().parseHex(pubkeyA);
            byte[] pubKeyBytesB = HexFormat.of().parseHex(pubkeyB);
            byte[] priKeyBytes = BytesUtils.hexCharArrayToByteArray(priKey);
            if(alg!=null)this.alg=alg;
            else this.alg = AlgorithmId.EccAes256K1P7_No1_NrC7;
            this.type = asyOneWayOrAsyTwoWayType;
            this.iv = iv;
            this.cipher = cipher;
            this.sum = sum;
            this.pubkeyA = pubkeyA;
            this.pubkeyB = pubkeyB;
            if (EccAes256K1P7.isTheKeyPair(pubKeyBytesA, priKeyBytes)) {
                this.prikeyA = priKey;
            } else if (EccAes256K1P7.isTheKeyPair(pubKeyBytesB, priKeyBytes)) {
                this.prikeyB = priKey;
            } else this.message = "The priKey doesn't match pubKeyA or pubKeyB.";
        } else
            this.message = "Constructing wrong. " + EncryptType.AsyOneWay + " or" + EncryptType.AsyTwoWay + " is required for this constructor. ";

    }

    public CryptoDataStr(EncryptType symkeyOrPasswordType, String iv, String cipher, @Nullable String sum, char[] symkeyOrPassword) {
        this.alg = AlgorithmId.EccAes256K1P7_No1_NrC7;
        this.type = symkeyOrPasswordType;
        this.iv = iv;
        this.cipher = cipher;
        this.sum = sum;
        if (symkeyOrPasswordType == EncryptType.Symkey) {
            this.symkey = symkeyOrPassword;
        } else if (symkeyOrPasswordType == EncryptType.Password) {
            this.password = symkeyOrPassword;
        } else {
            this.message = "Constructing wrong. " + EncryptType.Symkey + " or" + EncryptType.Password + " is required for this constructor. ";
        }
    }
    public CryptoDataStr(AlgorithmId alg, EncryptType symkeyOrPasswordType, String iv, String cipher, @Nullable String sum, char[] symkeyOrPassword) {
        if(alg!=null)this.alg =alg;
        else this.alg = AlgorithmId.EccAes256K1P7_No1_NrC7;
        this.type = symkeyOrPasswordType;
        this.iv = iv;
        this.cipher = cipher;
        this.sum = sum;
        if (symkeyOrPasswordType == EncryptType.Symkey) {
            this.symkey = symkeyOrPassword;
        } else if (symkeyOrPasswordType == EncryptType.Password) {
            this.password = symkeyOrPassword;
        } else {
            this.message = "Constructing wrong. " + EncryptType.Symkey + " or" + EncryptType.Password + " is required for this constructor. ";
        }
    }

    public void set0CodeMessage() {
        this.code = CodeMessage.Code0Success;
        this.message = CodeMessage.getMsg(CodeMessage.Code0Success);
    }

    public void setCodeMessage(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
    public void setCodeMessage(Integer code) {
        this.code = code;
        this.message = CodeMessage.getMsg(code);
    }
    public void setOtherCodeMessage(String message) {
        code = CodeMessage.Code1020OtherError;
        this.message = message;
    }

    public static CryptoDataStr fromCryptoDataByte(CryptoDataByte cryptoDataByte) {
        CryptoDataStr cryptoDataStr = new CryptoDataStr();

        if (cryptoDataByte.getType() != null)
            cryptoDataStr.setType(cryptoDataByte.getType());
        if (cryptoDataByte.getAlg() != null)
            cryptoDataStr.setAlg(cryptoDataByte.getAlg());
        if (cryptoDataByte.getCipher() != null)
            cryptoDataStr.setCipher(Base64.getEncoder().encodeToString(cryptoDataByte.getCipher()));
        if (cryptoDataByte.getIv() != null)
            cryptoDataStr.setIv(HexFormat.of().formatHex(cryptoDataByte.getIv()));
        if (cryptoDataByte.getData() != null)
            cryptoDataStr.setData(new String(cryptoDataByte.getData(), StandardCharsets.UTF_8));
        if (cryptoDataByte.getPassword() != null)
            cryptoDataStr.setPassword(byteArrayToUtf8CharArray(cryptoDataByte.getPassword()));
        if (cryptoDataByte.getPubkeyA() != null)
            cryptoDataStr.setPubkeyA(HexFormat.of().formatHex(cryptoDataByte.getPubkeyA()));
        if (cryptoDataByte.getPubkeyB() != null)
            cryptoDataStr.setPubkeyB(HexFormat.of().formatHex(cryptoDataByte.getPubkeyB()));
        if (cryptoDataByte.getPrikeyA() != null)
            cryptoDataStr.setPrikeyA(byteArrayToHexCharArray(cryptoDataByte.getPrikeyA()));
        if (cryptoDataByte.getPrikeyB() != null)
            cryptoDataStr.setPrikeyB(byteArrayToHexCharArray(cryptoDataByte.getPrikeyB()));
        if (cryptoDataByte.getSymkey() != null)
            cryptoDataStr.setSymkey(byteArrayToHexCharArray(cryptoDataByte.getSymkey()));
        if (cryptoDataByte.getSum() != null)
            cryptoDataStr.setSum(HexFormat.of().formatHex(cryptoDataByte.getSum()));
        if (cryptoDataByte.getMessage() != null)
            cryptoDataStr.setMessage(cryptoDataByte.getMessage());
        if(cryptoDataByte.getDid()!=null)
            cryptoDataStr.setDid(Hex.toHex(cryptoDataByte.getDid()));
        if(cryptoDataByte.getCipherId()!=null)
            cryptoDataStr.setCipherId(Hex.toHex(cryptoDataByte.getCipherId()));
        if(cryptoDataByte.getCode()!=null)
            cryptoDataStr.setCode(cryptoDataByte.getCode());
        if(cryptoDataByte.getKeyName()!=null)
            cryptoDataStr.setKeyName(Hex.toHex(cryptoDataByte.getKeyName()));

        return cryptoDataStr;
    }

    private void checkKeyPairAndSetPriKey(CryptoDataStr cryptoDataStr, char[] key) {
        byte[] keyBytes = BytesUtils.hexCharArrayToByteArray(key);
        if (cryptoDataStr.getPubkeyA() != null) {
            byte[] pubKey = HexFormat.of().parseHex(cryptoDataStr.getPubkeyA());
            if (EccAes256K1P7.isTheKeyPair(pubKey, keyBytes)) {
                cryptoDataStr.setPrikeyA(key);
                return;
            } else cryptoDataStr.setPrikeyB(key);
        }
        if (cryptoDataStr.getPubkeyB() != null) {
            byte[] pubKey = HexFormat.of().parseHex(cryptoDataStr.getPubkeyB());
            if (EccAes256K1P7.isTheKeyPair(pubKey, keyBytes)) {
                cryptoDataStr.setPrikeyB(key);
                return;
            } else cryptoDataStr.setPrikeyA(key);
        }
        cryptoDataStr.setMessage("No pubKeyA or pubKeyB.");
    }

    public void fromJson1(String json) {
        CryptoDataStr cryptoDataStr = fromJson(json);
        this.type = cryptoDataStr.getType();
        this.alg = cryptoDataStr.getAlg();
        this.data = cryptoDataStr.getData();
        this.cipher = cryptoDataStr.getCipher();
        this.pubkeyA = cryptoDataStr.getPubkeyA();
//        this.pubKeyB = cryptoData.getPubKeyB();
        this.sum = cryptoDataStr.getSum();
//        this.badSum = cryptoData.isBadSum();
        this.iv = cryptoDataStr.getIv();
//        this.message = cryptoData.getMessage();
    }

    public String toJson() {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(AlgorithmId.class, new AlgorithmId.AlgorithmTypeSerializer())
                .create();
        return gson.toJson(this);
    }

    public static CryptoDataStr writeToFileStream(FileInputStream fis) throws IOException {
        byte[] jsonBytes = readOneJsonFromFile(fis);
        if (jsonBytes == null) return null;
        return fromJson(new String(jsonBytes));
    }

    public String toNiceJson() {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(AlgorithmId.class, new AlgorithmId.AlgorithmTypeSerializer())
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
        return gson.toJson(this);
    }

    public static CryptoDataStr fromJson(String json) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(AlgorithmId.class, new AlgorithmId.AlgorithmTypeDeserializer())
                .create();
        return gson.fromJson(json, CryptoDataStr.class);
    }

    public static CryptoDataStr readFromFileStream(FileInputStream fis) throws IOException {
        byte[] jsonBytes = readOneJsonFromFile(fis);
        if (jsonBytes == null) return null;
        return fromJson(new String(jsonBytes));
    }

    public void clearCharArray(char[] array) {
        if (array != null) {
            Arrays.fill(array, '\0');
            array = null;
        }
    }

    public void clearAllSensitiveData() {
        clearPassword();
        clearSymkey();
        clearPriKeyA();
        clearPriKeyB();
    }

    public void clearAllSensitiveDataButSymkey() {
        clearPassword();
        clearPriKeyA();
        clearPriKeyB();
    }

    public EncryptType getType() {
        return type;
    }

    public void setType(EncryptType type) {
        this.type = type;
    }

    public AlgorithmId getAlg() {
        return alg;
    }

    public void setAlg(AlgorithmId alg) {
        this.alg = alg;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getCipher() {
        return cipher;
    }

    public void setCipher(String cipher) {
        this.cipher = cipher;
    }

    public char[] getSymkey() {
        return symkey;
    }

    public void setSymkey(char[] symkey) {
        this.symkey = symkey;
    }

    public char[] getPassword() {
        return password;
    }

    public void setPassword(char[] password) {
        this.password = password;
    }

    public String getPubkeyA() {
        return pubkeyA;
    }

    public void setPubkeyA(String pubkeyA) {
        this.pubkeyA = pubkeyA;
    }

    public String getPubkeyB() {
        return pubkeyB;
    }

    public void setPubkeyB(String pubkeyB) {
        this.pubkeyB = pubkeyB;
    }

    public char[] getPrikeyA() {
        return prikeyA;
    }

    public void setPrikeyA(char[] prikeyA) {
        this.prikeyA = prikeyA;
    }

    public char[] getPrikeyB() {
        return prikeyB;
    }

    public void setPrikeyB(char[] prikeyB) {
        this.prikeyB = prikeyB;
    }

    public String getIv() {
        return iv;
    }

    public void setIv(String iv) {
        this.iv = iv;
    }

    public String getSum() {
        return sum;
    }

    public void setSum(String sum) {
        this.sum = sum;
    }

    public void clearPassword() {
        clearCharArray(password);
        this.password = null;
    }

    public void clearSymkey() {
        clearCharArray(symkey);
        this.symkey = null;
    }

    public void clearPriKeyA() {
        clearCharArray(prikeyA);
        this.prikeyA = null;
    }

    public void clearPriKeyB() {
        clearCharArray(prikeyB);
        this.prikeyB = null;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDid() {
        return did;
    }

    public void setDid(String did) {
        this.did = did;
    }

    public String getCipherId() {
        return cipherId;
    }

    public void setCipherId(String cipherId) {
        this.cipherId = cipherId;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }
}
