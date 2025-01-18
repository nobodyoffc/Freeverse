package crypto;

import crypto.old.EccAes256K1P7;
import fcData.AlgorithmId;
import tools.BytesTools;
import tools.Hex;
import org.jetbrains.annotations.NotNull;

import constants.CodeMessage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

import static tools.JsonTools.readOneJsonFromFile;

public class CryptoDataByte {

    public static final String ALG_0 = "000000000000";
    public static final String ALG_1 = "000000000001";
    private EncryptType type;
    private AlgorithmId alg;

    private transient byte[] data;
    private transient byte[] did;
    private transient byte[] symKey;
    private transient byte[] keyName;
    private transient byte[] password;
    private transient byte[] pubKeyA;
    private transient byte[] pubKeyB;
    private transient byte[] priKeyA;
    private transient byte[] priKeyB;
    private transient byte[] iv;
    private transient byte[] sum;
    private transient byte[] cipher;
    private transient byte[] cipherId;
    private transient InputStream msgInputStream;
    private transient InputStream cipherInputStream;
    private transient OutputStream msgOutputStream;
    private transient OutputStream cipherOutputStream;
    private transient String message;
    private transient Integer code;


    public CryptoDataByte() {
    }

    @NotNull
    public static CryptoDataByte makeErrorCryptDataByte(int code1033MissPriKey) {
        CryptoDataByte cryptoDataByte;
        cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setCodeMessage(code1033MissPriKey);
        return cryptoDataByte;
    }

    public String toNiceJson() {
        CryptoDataStr cryptoDataStr = CryptoDataStr.fromCryptoDataByte(this);
        return cryptoDataStr.toNiceJson();
    }

    public String toJson() {
        CryptoDataStr cryptoDataStr = CryptoDataStr.fromCryptoDataByte(this);
        return cryptoDataStr.toJson();
    }

    public static CryptoDataByte readFromFileStream(FileInputStream fis) throws IOException {
        byte[] jsonBytes = readOneJsonFromFile(fis);
        if (jsonBytes == null) return null;
        return fromJson(new String(jsonBytes));
    }
    public static CryptoDataByte readFromFile(String fileName,String path) throws IOException {
        byte[] jsonBytes;
        File file = new File(path,fileName);
        try(FileInputStream fis = new FileInputStream(file)) {
            jsonBytes = readOneJsonFromFile(fis);
        }
        if (jsonBytes == null) return null;
        return fromJson(new String(jsonBytes));
    }
    public static CryptoDataByte fromCryptoData(CryptoDataStr cryptoDataStr) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();

        if (cryptoDataStr.getType() != null)
            cryptoDataByte.setType(cryptoDataStr.getType());
        if (cryptoDataStr.getAlg() != null)
            cryptoDataByte.setAlg(cryptoDataStr.getAlg());
        if (cryptoDataStr.getCipher() != null)
            cryptoDataByte.setCipher(Base64.getDecoder().decode(cryptoDataStr.getCipher()));
        if (cryptoDataStr.getIv() != null)
            cryptoDataByte.setIv(HexFormat.of().parseHex(cryptoDataStr.getIv()));
        if (cryptoDataStr.getData() != null)
            cryptoDataByte.setData(cryptoDataStr.getData().getBytes(StandardCharsets.UTF_8));
        if (cryptoDataStr.getPassword() != null)
            cryptoDataByte.setPassword(BytesTools.utf8CharArrayToByteArray(cryptoDataStr.getPassword()));
        if (cryptoDataStr.getPubKeyA() != null)
            cryptoDataByte.setPubKeyA(HexFormat.of().parseHex(cryptoDataStr.getPubKeyA()));
        if (cryptoDataStr.getPubKeyB() != null)
            cryptoDataByte.setPubKeyB(HexFormat.of().parseHex(cryptoDataStr.getPubKeyB()));
        if (cryptoDataStr.getPriKeyA() != null)
            cryptoDataByte.setPriKeyA(BytesTools.hexCharArrayToByteArray(cryptoDataStr.getPriKeyA()));
        if (cryptoDataStr.getPriKeyB() != null)
            cryptoDataByte.setPriKeyB(BytesTools.hexCharArrayToByteArray(cryptoDataStr.getPriKeyB()));
        if (cryptoDataStr.getSymKey() != null)
            cryptoDataByte.setSymKey(BytesTools.hexCharArrayToByteArray(cryptoDataStr.getSymKey()));
        if (cryptoDataStr.getSum() != null)
            cryptoDataByte.setSum(HexFormat.of().parseHex(cryptoDataStr.getSum()));
        if (cryptoDataStr.getMessage() != null)
            cryptoDataByte.setMessage(cryptoDataStr.getMessage());
        if(cryptoDataStr.getDid()!=null)
            cryptoDataByte.setDid(Hex.fromHex(cryptoDataStr.getDid()));
        if(cryptoDataStr.getCipherId()!=null)
            cryptoDataByte.setCipherId(Hex.fromHex(cryptoDataStr.getCipherId()));
        if(cryptoDataStr.getKeyName()!=null)
            cryptoDataByte.setKeyName(Hex.fromHex(cryptoDataStr.getKeyName()));
//        cryptoDataByte.setBadSum(cryptoData.isBadSum());

        return cryptoDataByte;
    }
    public static CryptoDataByte fromJson(String json){
        CryptoDataStr cryptoDataStr = CryptoDataStr.fromJson(json);
        return CryptoDataByte.fromCryptoData(cryptoDataStr);
    }

    public byte[] toBundle() {
        if (iv == null || cipher == null || sum == null || type == null || alg == null) {
            return null; // Handle basic null checks early
        }

        if (type.equals(EncryptType.SymKey) || type.equals(EncryptType.Password)) {
            if (keyName == null) return null;
        }

        // Create algorithm byte array
        byte[] algBytes = switch (alg) {
            case FC_AesCbc256_No1_NrC7 -> new byte[]{0, 0, 0, 0, 0, 1};  // Defaults to all zeroes
            case FC_EccK1AesCbc256_No1_NrC7 -> new byte[]{0, 0, 0, 0, 0, 2};
            default -> null;
        };

        if (algBytes == null) return null;

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            // Write algBytes (6 bytes)
            outputStream.write(algBytes);

            // Write EncryptType (1 byte)
            outputStream.write(type.getNumber());

            // Conditionally write pubKeyA based on EncryptType
            if (type == EncryptType.AsyOneWay || type == EncryptType.AsyTwoWay) {
                if (pubKeyA == null) return null; // Check if pubKeyA is needed but not provided
                outputStream.write(pubKeyA);
            }

            // Conditionally write keyName based on EncryptType
            if (type == EncryptType.SymKey || type == EncryptType.Password) {
                outputStream.write(keyName);
            }

            // Write iv (16 bytes)
            outputStream.write(iv);

            // Write cipher (variable length)
            outputStream.write(cipher);

            // Write sum (4 bytes)
            outputStream.write(sum);

            // Convert the output stream to a byte array
            return outputStream.toByteArray();
        } catch (IOException e) {
            // Handle potential IO exceptions (shouldn't happen with ByteArrayOutputStream)
            e.printStackTrace();
            return null;
        }
    }

    public void makeKeyName(byte[] key) {
        if(key==null)return;
        keyName = new byte[6];
        byte[] hash = Hash.sha256(key);
        System.arraycopy(hash,0,keyName,0,6);
    }


    @NotNull
    private static byte[] makeBundleWithoutPubKey(byte[] iv, byte[] cipher, byte[] sum, byte[] algBytes) {
        byte[] bundle;
        bundle = new byte[6+ iv.length+ sum.length+ cipher.length];
        System.arraycopy(algBytes,0,bundle,0, 6);
        System.arraycopy(iv,0,bundle,6, iv.length);
        System.arraycopy(cipher,0,bundle, 6+ iv.length, cipher.length);
        System.arraycopy(sum,0,bundle, 6+ iv.length+ cipher.length, sum.length);
        return bundle;
    }

    public static CryptoDataByte fromBundle(byte[] bundle) {
        if (bundle == null || bundle.length < 8) { // Minimum 6 for algBytes and 1 for type
            return null;
        }
        int offset = 0;
        CryptoDataByte cryptoData = new CryptoDataByte();

        // Extract the algorithm bytes

        byte[] algBytes = new byte[6];
        System.arraycopy(bundle, offset, algBytes, 0, 6);
        offset += 6;
        // Map algorithm bytes back to AlgorithmId
        AlgorithmId alg = switch (Arrays.toString(algBytes)) {
            case "[0, 0, 0, 0, 0, 1]" -> AlgorithmId.FC_AesCbc256_No1_NrC7;
            case "[0, 0, 0, 0, 0, 2]" -> AlgorithmId.FC_EccK1AesCbc256_No1_NrC7;
            default -> null;
        };

        if (alg == null) return null; // Return null if the algorithm ID isn't recognized

        cryptoData.setAlg(alg);

        // Extract the EncryptType byte
        byte typeByte = bundle[6];
        offset++;
        EncryptType type = EncryptType.fromNumber(typeByte); // Assuming EncryptType has a method to get type from a number

        if (type == null) return null;

        cryptoData.setType(type);

        // Check if pubKeyA exists for Asy
        if (type == EncryptType.AsyOneWay || type == EncryptType.AsyTwoWay) {
            // Extract pubKeyA (33 bytes)
            byte[] pubKeyA = new byte[33];
            System.arraycopy(bundle, offset, pubKeyA, 0, 33);
            cryptoData.setPubKeyA(pubKeyA);
            offset += 33;
        }

        // Check if keyName exists for SymKey or Password
        if (type == EncryptType.SymKey || type == EncryptType.Password) {
            // Extract pubKeyA (33 bytes)
            byte[] keyName = new byte[6];
            System.arraycopy(bundle, offset, keyName, 0, 6);
            cryptoData.setKeyName(keyName);
            offset += 6;
        }

        // Extract iv (16 bytes)
        byte[] iv = new byte[16];
        System.arraycopy(bundle, offset, iv, 0, 16);
        cryptoData.setIv(iv);
        offset += 16;

        // Calculate cipher length dynamically
        int sumLength = 4;
        int cipherLength = bundle.length - offset - sumLength;

        if (cipherLength <= 0) return null; // Sanity check to ensure we have a valid cipher length

        // Extract cipher
        byte[] cipher = new byte[cipherLength];
        System.arraycopy(bundle, offset, cipher, 0, cipherLength);
        cryptoData.setCipher(cipher);
        offset += cipherLength;

        // Extract sum (last 4 bytes)
        byte[] sum = new byte[4];
        System.arraycopy(bundle, offset, sum, 0, 4);
        cryptoData.setSum(sum);

        return cryptoData;
    }

    public void set0CodeMessage() {
        this.code = CodeMessage.Code0Success;
        message = CodeMessage.getMsg(CodeMessage.Code0Success);
    }

    public void setCodeMessage(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public void setCodeMessage(Integer code) {
        this.code = code;
        message = CodeMessage.getMsg(code);
    }

    public void setOtherCodeMessage(String message) {
        code = CodeMessage.Code1020OtherError;
        this.message = message;
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

    public void clearSymKey() {
        BytesTools.clearByteArray(this.symKey);
        this.symKey = null;
    }

    public void clearPassword() {
        BytesTools.clearByteArray(this.password);
        this.password = null;
    }

    public void clearPriKeyA() {
        BytesTools.clearByteArray(this.priKeyA);
        this.priKeyA = null;
    }

    public void clearPriKeyB() {
        BytesTools.clearByteArray(this.priKeyB);
        this.priKeyB = null;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public AlgorithmId getAlg() {
        return alg;
    }

    public void setAlg(AlgorithmId alg) {
        this.alg = alg;
    }

    public EncryptType getType() {
        return type;
    }

    public void setType(EncryptType type) {
        this.type = type;
    }

    public byte[] getIv() {
        return iv;
    }

    public void setIv(byte[] iv) {
        this.iv = iv;
    }

    public byte[] getSymKey() {
        return symKey;
    }

    public void setSymKey(byte[] symKey) {
        this.symKey = symKey;
    }

    public byte[] getPassword() {
        return password;
    }

    public void setPassword(byte[] password) {
        this.password = password;
    }

    public byte[] getSum() {
        return sum;
    }

    public void setSum(byte[] sum) {
        this.sum = sum;
    }

    public byte[] getCipher() {
        return cipher;
    }

    public void setCipher(byte[] cipher) {
        this.cipher = cipher;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public byte[] getPubKeyA() {
        return pubKeyA;
    }

    public void setPubKeyA(byte[] pubKeyA) {
        this.pubKeyA = pubKeyA;
    }

    public byte[] getPubKeyB() {
        return pubKeyB;
    }

    public void setPubKeyB(byte[] pubKeyB) {
        this.pubKeyB = pubKeyB;
    }

    public byte[] getPriKeyA() {
        return priKeyA;
    }

    public void setPriKeyA(byte[] priKeyA) {
        this.priKeyA = priKeyA;
    }

    public byte[] getPriKeyB() {
        return priKeyB;
    }

    public void setPriKeyB(byte[] priKeyB) {
        this.priKeyB = priKeyB;
    }

    public byte[] getDid() {
        return did;
    }

    public void setDid(byte[] did) {
        this.did = did;
    }

    public byte[] getCipherId() {
        return cipherId;
    }

    public void setCipherId(byte[] cipherId) {
        this.cipherId = cipherId;
    }

    public InputStream getMsgInputStream() {
        return msgInputStream;
    }

    public void setMsgInputStream(InputStream msgInputStream) {
        this.msgInputStream = msgInputStream;
    }

    public InputStream getCipherInputStream() {
        return cipherInputStream;
    }

    public void setCipherInputStream(InputStream cipherInputStream) {
        this.cipherInputStream = cipherInputStream;
    }

    public OutputStream getMsgOutputStream() {
        return msgOutputStream;
    }

    public void setMsgOutputStream(OutputStream msgOutputStream) {
        this.msgOutputStream = msgOutputStream;
    }

    public OutputStream getCipherOutputStream() {
        return cipherOutputStream;
    }

    public void setCipherOutputStream(OutputStream cipherOutputStream) {
        this.cipherOutputStream = cipherOutputStream;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public static byte[] makeSum4(byte[] symKey, byte[] iv, byte[] did) {
        if(symKey!=null && iv!=null && did!=null) {
            byte[] sum32 = Hash.sha256(BytesTools.addByteArray(symKey, BytesTools.addByteArray(iv, did)));
            return BytesTools.getPartOfBytes(sum32, 0, 4);
        }
        return null;
    }

    public void makeSum4() {
        if(symKey==null){
            setCodeMessage(CodeMessage.Code4006InvalidKey);
            return;
        }
        if(iv==null){
            setCodeMessage(CodeMessage.Code4009MissingIv);
            return;
        }
        if(did==null){
            setCodeMessage(CodeMessage.Code3009DidMissed);
            return;
        }
        byte[] sum32 = Hash.sha256(BytesTools.addByteArray(symKey, BytesTools.addByteArray(iv, did)));
        sum = BytesTools.getPartOfBytes(sum32, 0, 4);
    }

    public void makeDid() {
        if(code==CodeMessage.Code0Success && this.data!=null){
            this.did = Hash.sha256x2(data);
        }
    }

    public boolean checkSum() {
        return checkSum(this.alg);
    }

    public boolean checkSum(AlgorithmId algorithmId) {
        byte[] newSum;
        switch (algorithmId){
            case EccAes256K1P7_No1_NrC7 ->
                newSum = EccAes256K1P7.getSum4(symKey,iv,cipher);
            default -> newSum = makeSum4(symKey,iv,did);
        }
        String sumHex = Hex.toHex(sum);
        String newSumHex = Hex.toHex(newSum);
        if(!newSumHex.equals(sumHex)){
            setCodeMessage(CodeMessage.Code4011BadSum);
            return false;
        }
        return true;
    }

    public boolean checkSum(byte[] did) {
        byte[] newSum;
        newSum = CryptoDataByte.makeSum4(symKey,iv,did);

        String sumHex = Hex.toHex(sum);
        String newSumHex = Hex.toHex(newSum);
        if(!newSumHex.equals(sumHex)){
            setCodeMessage(CodeMessage.Code4011BadSum);
            return false;
        }
        return true;
    }

    public void printCodeMessage() {
        System.out.println(code+" : "+ message);
    }

    public byte[] getKeyName() {
        return keyName;
    }

    public void setKeyName(byte[] keyName) {
        this.keyName = keyName;
    }

}
