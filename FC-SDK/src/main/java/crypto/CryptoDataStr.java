package crypto;

import com.google.gson.GsonBuilder;
import com.google.gson.Gson;
import crypto.old.EccAes256K1P7;
import fcData.AlgorithmId;
import javaTools.BytesTools;
import javaTools.Hex;


import javax.annotation.Nullable;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

import static javaTools.BytesTools.byteArrayToUtf8CharArray;
import static javaTools.Hex.byteArrayToHexCharArray;
import static javaTools.JsonTools.readOneJsonFromFile;


public class CryptoDataStr {
    private EncryptType type;
    private AlgorithmId alg;
    private String data;
    private transient String did;
    private String cipher;
    private transient String cipherId;
    private transient char[] symKey;
    private transient char[] password;
    private String pubKeyA;
    private transient String pubKeyB;
    private transient char[] priKeyA;
    private transient char[] priKeyB;
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
            case AsyOneWay -> priKeyB = key;
            case AsyTwoWay -> checkKeyPairAndSetPriKey(this, key);
            case SymKey -> symKey = key;
            case Password -> password = key;
        }
    }

    /**
     * For AsyOneWay encrypt. The classic encrypting mode.
     */
    public CryptoDataStr(EncryptType asyOneWay, String data, String pubKeyB) {
        if (asyOneWay == EncryptType.AsyOneWay) {
            this.type = asyOneWay;
            this.alg = AlgorithmId.EccAes256K1P7_No1_NrC7;
            this.data = data;
            this.pubKeyB = pubKeyB;
        } else {
            this.message = "Constructing wrong. " + EncryptType.AsyOneWay + " is required for this constructor. ";
        }
    }
    public CryptoDataStr(AlgorithmId alg, EncryptType asyOneWay, String data, String pubKeyB) {
        if (asyOneWay == EncryptType.AsyOneWay) {
            this.type = asyOneWay;
            if(alg!=null)this.alg = alg;
            else this.alg = AlgorithmId.EccAes256K1P7_No1_NrC7;
            this.data = data;
            this.pubKeyB = pubKeyB;
        } else {
            this.message = "Constructing wrong. " + EncryptType.AsyOneWay + " is required for this constructor. ";
        }
    }

    /**
     * For AsyTwoWay encrypt
     */
    public CryptoDataStr(EncryptType asyTwoWay, String data, String pubKeyB, char[] priKeyA) {
        if (asyTwoWay == EncryptType.AsyTwoWay) {
            this.type = asyTwoWay;
            this.alg = AlgorithmId.EccAes256K1P7_No1_NrC7;
            this.data = data;
            this.pubKeyB = pubKeyB;
            this.priKeyA = priKeyA;
        } else {
            this.message = "Constructing wrong. " + EncryptType.AsyTwoWay + " is needed for this constructor. ";
        }
    }
    public CryptoDataStr(AlgorithmId alg, EncryptType asyTwoWay, String data, String pubKeyB, char[] priKeyA) {
        if (asyTwoWay == EncryptType.AsyTwoWay) {
            this.type = asyTwoWay;
            if(alg!=null)this.alg = alg;
            else this.alg = AlgorithmId.EccAes256K1P7_No1_NrC7;
            this.data = data;
            this.pubKeyB = pubKeyB;
            this.priKeyA = priKeyA;
        } else {
            this.message = "Constructing wrong. " + EncryptType.AsyTwoWay + " is needed for this constructor. ";
        }
    }

    /**
     * For SymKey or Password encrypt
     */
    public CryptoDataStr(EncryptType symKeyOrPasswordType, String data, char[] symKeyOrPassword) {
        this.type = symKeyOrPasswordType;
        switch (symKeyOrPasswordType) {
            case SymKey -> symKey = symKeyOrPassword;
            case Password -> password = symKeyOrPassword;
            default ->
                    this.message = "Constructing wrong. " + EncryptType.SymKey + " or " + EncryptType.Password + " is required for this constructor. ";
        }
        this.alg = AlgorithmId.EccAes256K1P7_No1_NrC7;
        this.data = data;
    }
    public CryptoDataStr(AlgorithmId alg, EncryptType symKeyOrPasswordType, String data, char[] symKeyOrPassword) {
        this.type = symKeyOrPasswordType;
        switch (symKeyOrPasswordType) {
            case SymKey -> symKey = symKeyOrPassword;
            case Password -> password = symKeyOrPassword;
            default ->
                    this.message = "Constructing wrong. " + EncryptType.SymKey + " or " + EncryptType.Password + " is required for this constructor. ";
        }
        if(alg!=null)this.alg=alg;
        else this.alg = AlgorithmId.EccAes256K1P7_No1_NrC7;
        this.data = data;
    }

    /**
     * For AsyOneWay or AsyTwoWay decrypt
     */
    public CryptoDataStr(EncryptType asyOneWayOrAsyTwoWayType, String pubKeyA, String pubKeyB, String iv, String cipher, @Nullable String sum, char[] priKey) {
        if (asyOneWayOrAsyTwoWayType == EncryptType.AsyOneWay || asyOneWayOrAsyTwoWayType == EncryptType.AsyTwoWay) {
            byte[] pubKeyBytesA = HexFormat.of().parseHex(pubKeyA);
            byte[] pubKeyBytesB = HexFormat.of().parseHex(pubKeyB);
            byte[] priKeyBytes = BytesTools.hexCharArrayToByteArray(priKey);
            this.alg = AlgorithmId.EccAes256K1P7_No1_NrC7;
            this.type = asyOneWayOrAsyTwoWayType;
            this.iv = iv;
            this.cipher = cipher;
            this.sum = sum;
            this.pubKeyA = pubKeyA;
            this.pubKeyB = pubKeyB;
            if (EccAes256K1P7.isTheKeyPair(pubKeyBytesA, priKeyBytes)) {
                this.priKeyA = priKey;
            } else if (EccAes256K1P7.isTheKeyPair(pubKeyBytesB, priKeyBytes)) {
                this.priKeyB = priKey;
            } else this.message = "The priKey doesn't match pubKeyA or pubKeyB.";
        } else
            this.message = "Constructing wrong. " + EncryptType.AsyOneWay + " or" + EncryptType.AsyTwoWay + " is required for this constructor. ";

    }
    public CryptoDataStr(AlgorithmId alg, EncryptType asyOneWayOrAsyTwoWayType, String pubKeyA, String pubKeyB, String iv, String cipher, @Nullable String sum, char[] priKey) {
        if (asyOneWayOrAsyTwoWayType == EncryptType.AsyOneWay || asyOneWayOrAsyTwoWayType == EncryptType.AsyTwoWay) {
            byte[] pubKeyBytesA = HexFormat.of().parseHex(pubKeyA);
            byte[] pubKeyBytesB = HexFormat.of().parseHex(pubKeyB);
            byte[] priKeyBytes = BytesTools.hexCharArrayToByteArray(priKey);
            if(alg!=null)this.alg=alg;
            else this.alg = AlgorithmId.EccAes256K1P7_No1_NrC7;
            this.type = asyOneWayOrAsyTwoWayType;
            this.iv = iv;
            this.cipher = cipher;
            this.sum = sum;
            this.pubKeyA = pubKeyA;
            this.pubKeyB = pubKeyB;
            if (EccAes256K1P7.isTheKeyPair(pubKeyBytesA, priKeyBytes)) {
                this.priKeyA = priKey;
            } else if (EccAes256K1P7.isTheKeyPair(pubKeyBytesB, priKeyBytes)) {
                this.priKeyB = priKey;
            } else this.message = "The priKey doesn't match pubKeyA or pubKeyB.";
        } else
            this.message = "Constructing wrong. " + EncryptType.AsyOneWay + " or" + EncryptType.AsyTwoWay + " is required for this constructor. ";

    }

    public CryptoDataStr(EncryptType symKeyOrPasswordType, String iv, String cipher, @Nullable String sum, char[] symKeyOrPassword) {
        this.alg = AlgorithmId.EccAes256K1P7_No1_NrC7;
        this.type = symKeyOrPasswordType;
        this.iv = iv;
        this.cipher = cipher;
        this.sum = sum;
        if (symKeyOrPasswordType == EncryptType.SymKey) {
            this.symKey = symKeyOrPassword;
        } else if (symKeyOrPasswordType == EncryptType.Password) {
            this.password = symKeyOrPassword;
        } else {
            this.message = "Constructing wrong. " + EncryptType.SymKey + " or" + EncryptType.Password + " is required for this constructor. ";
        }
    }
    public CryptoDataStr(AlgorithmId alg, EncryptType symKeyOrPasswordType, String iv, String cipher, @Nullable String sum, char[] symKeyOrPassword) {
        if(alg!=null)this.alg =alg;
        else this.alg = AlgorithmId.EccAes256K1P7_No1_NrC7;
        this.type = symKeyOrPasswordType;
        this.iv = iv;
        this.cipher = cipher;
        this.sum = sum;
        if (symKeyOrPasswordType == EncryptType.SymKey) {
            this.symKey = symKeyOrPassword;
        } else if (symKeyOrPasswordType == EncryptType.Password) {
            this.password = symKeyOrPassword;
        } else {
            this.message = "Constructing wrong. " + EncryptType.SymKey + " or" + EncryptType.Password + " is required for this constructor. ";
        }
    }

    public void set0CodeMessage() {
        this.code = 0;
        message = CryptoCodeMessage.getMessage(0);
    }

    public void setCodeMessage(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
    public void setCodeMessage(Integer code) {
        this.code = code;
        message = CryptoCodeMessage.getMessage(code);
    }
    public void setOtherCodeMessage(String message) {
        code = 9;
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
        if (cryptoDataByte.getPubKeyA() != null)
            cryptoDataStr.setPubKeyA(HexFormat.of().formatHex(cryptoDataByte.getPubKeyA()));
        if (cryptoDataByte.getPubKeyB() != null)
            cryptoDataStr.setPubKeyB(HexFormat.of().formatHex(cryptoDataByte.getPubKeyB()));
        if (cryptoDataByte.getPriKeyA() != null)
            cryptoDataStr.setPriKeyA(byteArrayToHexCharArray(cryptoDataByte.getPriKeyA()));
        if (cryptoDataByte.getPriKeyB() != null)
            cryptoDataStr.setPriKeyB(byteArrayToHexCharArray(cryptoDataByte.getPriKeyB()));
        if (cryptoDataByte.getSymKey() != null)
            cryptoDataStr.setSymKey(byteArrayToHexCharArray(cryptoDataByte.getSymKey()));
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

        return cryptoDataStr;
    }

    private void checkKeyPairAndSetPriKey(CryptoDataStr cryptoDataStr, char[] key) {
        byte[] keyBytes = BytesTools.hexCharArrayToByteArray(key);
        if (cryptoDataStr.getPubKeyA() != null) {
            byte[] pubKey = HexFormat.of().parseHex(cryptoDataStr.getPubKeyA());
            if (EccAes256K1P7.isTheKeyPair(pubKey, keyBytes)) {
                cryptoDataStr.setPriKeyA(key);
                return;
            } else cryptoDataStr.setPriKeyB(key);
        }
        if (cryptoDataStr.getPubKeyB() != null) {
            byte[] pubKey = HexFormat.of().parseHex(cryptoDataStr.getPubKeyB());
            if (EccAes256K1P7.isTheKeyPair(pubKey, keyBytes)) {
                cryptoDataStr.setPriKeyB(key);
                return;
            } else cryptoDataStr.setPriKeyA(key);
        }
        cryptoDataStr.setMessage("No pubKeyA or pubKeyB.");
    }

    public void fromJson1(String json) {
        CryptoDataStr cryptoDataStr = fromJson(json);
        this.type = cryptoDataStr.getType();
        this.alg = cryptoDataStr.getAlg();
        this.data = cryptoDataStr.getData();
        this.cipher = cryptoDataStr.getCipher();
        this.pubKeyA = cryptoDataStr.getPubKeyA();
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
        clearSymKey();
        clearPriKeyA();
        clearPriKeyB();
    }

    public void clearAllSensitiveDataButSymKey() {
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

    public char[] getSymKey() {
        return symKey;
    }

    public void setSymKey(char[] symKey) {
        this.symKey = symKey;
    }

    public char[] getPassword() {
        return password;
    }

    public void setPassword(char[] password) {
        this.password = password;
    }

    public String getPubKeyA() {
        return pubKeyA;
    }

    public void setPubKeyA(String pubKeyA) {
        this.pubKeyA = pubKeyA;
    }

    public String getPubKeyB() {
        return pubKeyB;
    }

    public void setPubKeyB(String pubKeyB) {
        this.pubKeyB = pubKeyB;
    }

    public char[] getPriKeyA() {
        return priKeyA;
    }

    public void setPriKeyA(char[] priKeyA) {
        this.priKeyA = priKeyA;
    }

    public char[] getPriKeyB() {
        return priKeyB;
    }

    public void setPriKeyB(char[] priKeyB) {
        this.priKeyB = priKeyB;
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

    public void clearSymKey() {
        clearCharArray(symKey);
        this.symKey = null;
    }

    public void clearPriKeyA() {
        clearCharArray(priKeyA);
        this.priKeyA = null;
    }

    public void clearPriKeyB() {
        clearCharArray(priKeyB);
        this.priKeyB = null;
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
}
