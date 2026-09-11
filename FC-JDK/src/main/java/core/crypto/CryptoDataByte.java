package core.crypto;

import core.crypto.old.EccAes256K1P7;
import data.fcData.AlgorithmId;
import utils.BytesUtils;
import utils.Hex;
import org.jetbrains.annotations.NotNull;

import constants.CodeMessage;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

import static utils.JsonUtils.readOneJsonFromFile;

public class CryptoDataByte {
    // Bundle algorithm prefixes: the first 12 hex chars (6 bytes) of each algorithm's
    // on-chain protocol PID. Used as the bundle prefix written by toBundle().
    private static final String ALG_PID_PREFIX_AesCbc256 = "51515d32878c";               //PID:51515d32878c8eabdab8b768386b2affedc50b3d00d1f6f697396266b34c235e
    private static final String ALG_PID_PREFIX_EccK1AesCbc256 = "3ea47cd61381";           //PID:3ea47cd61381bdd97f3e36d4c71c8075a684a860db46791cc505abefbb8e923e
    private static final String ALG_PID_PREFIX_AesGcm256 = "76f7b226a8b3";                //PID:76f7b226a8b3eed8296b73f4c9317d4c01c02c57eac021bc04beb61ff8ad0efd
    private static final String ALG_PID_PREFIX_EccK1AesGcm256 = "a5acd7077805";           //PID:a5acd7077805d3e8ae6ddf7fb9d9ebd52c665942e5096e3d308f66d4cf5e844a
    private static final String ALG_PID_PREFIX_X25519AesGcm256 = "b4a25b3c3043";          //PID:b4a25b3c3043105fd3a568a628bf2edfb2ae543c229f491276c0d166a4de46ee
    private static final String ALG_PID_PREFIX_ChaCha20 = "bcc39a9628e2";                 //PID:bcc39a9628e265320ea5dfcdf35a50b1ac190cd62b9692a64de87fad925c4ade
    private static final String ALG_PID_PREFIX_EccK1ChaCha20 = "355319f84bd5";            //PID:355319f84bd534be45548621edf56ebd467e8905d78cfe8c8741bb8555f76d4a
    private static final String ALG_PID_PREFIX_ChaCha20Poly1305 = "b1788c3b7320";         //PID:b1788c3b73208c85f0afdec4bc5c366755c2f9de07dc01973d877fc1b31010a3
    private static final String ALG_PID_PREFIX_EccK1ChaCha20Poly1305 = "d1691132aee1";    //PID:d1691132aee137b59002552b2909f8a33b9cbfcbbf3ca12bad20965e2f968a59
    private static final String ALG_PID_PREFIX_BitCore = "e308bc027946";                  //PID:e308bc02794604f6819dd86ae89d56a70f48c5d17263287d90c6ae2b5320651d

    private EncryptType type;
    private AlgorithmId alg;
    private Kdf kdf;

    private transient byte[] data;
    private transient byte[] did;
    private transient byte[] symkey;
    private transient byte[] keyName;
    private transient byte[] password;
    private transient byte[] pubkeyA;
    private transient byte[] pubkeyB;
    private transient byte[] prikeyA;
    private transient byte[] prikeyB;
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
        if (cryptoDataStr.getKdf() != null)
            cryptoDataByte.setKdf(cryptoDataStr.getKdf());
        if (cryptoDataStr.getCipher() != null)
            cryptoDataByte.setCipher(Base64.getDecoder().decode(cryptoDataStr.getCipher()));
        if (cryptoDataStr.getIv() != null)
            cryptoDataByte.setIv(HexFormat.of().parseHex(cryptoDataStr.getIv()));
        if (cryptoDataStr.getData() != null)
            cryptoDataByte.setData(cryptoDataStr.getData().getBytes(StandardCharsets.UTF_8));
        if (cryptoDataStr.getPassword() != null)
            cryptoDataByte.setPassword(BytesUtils.utf8CharArrayToByteArray(cryptoDataStr.getPassword()));
        if (cryptoDataStr.getPubkeyA() != null)
            cryptoDataByte.setPubkeyA(HexFormat.of().parseHex(cryptoDataStr.getPubkeyA()));
        if (cryptoDataStr.getPubkeyB() != null)
            cryptoDataByte.setPubkeyB(HexFormat.of().parseHex(cryptoDataStr.getPubkeyB()));
        if (cryptoDataStr.getPrikeyA() != null)
            cryptoDataByte.setPrikeyA(BytesUtils.hexCharArrayToByteArray(cryptoDataStr.getPrikeyA()));
        if (cryptoDataStr.getPrikeyB() != null)
            cryptoDataByte.setPrikeyB(BytesUtils.hexCharArrayToByteArray(cryptoDataStr.getPrikeyB()));
        if (cryptoDataStr.getSymkey() != null)
            cryptoDataByte.setSymkey(BytesUtils.hexCharArrayToByteArray(cryptoDataStr.getSymkey()));
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

        return cryptoDataByte;
    }
    public static CryptoDataByte fromJson(String json){
        CryptoDataStr cryptoDataStr = CryptoDataStr.fromJson(json);
        return CryptoDataByte.fromCryptoData(cryptoDataStr);
    }

    public byte[] toBundle() {
        if (iv == null || cipher == null || type == null || alg == null) {
            return null; // Handle basic null checks early
        }

        // Algorithms with built-in authentication (GCM, Poly1305) don't need a separate sum
        boolean requiresSum = !alg.isAead();

        if (requiresSum && sum == null) {
            return null; // sum is required but missing for non-GCM algorithms
        }

        if (type.equals(EncryptType.Symkey) || type.equals(EncryptType.Password)) {
            if (keyName == null) return null;
        }

        // Create algorithm byte array: the first 12 hex chars (6 bytes) of each
        // algorithm's on-chain protocol PID. See ALG_PID_PREFIX_* constants.
        byte[] algBytes = switch (alg) {
            case FC_AesCbc256_No1_NrC7 -> Hex.fromHex(ALG_PID_PREFIX_AesCbc256);
            case FC_EccK1AesCbc256_No1_NrC7 -> Hex.fromHex(ALG_PID_PREFIX_EccK1AesCbc256);
            case FC_AesGcm256_No1_NrC7 -> Hex.fromHex(ALG_PID_PREFIX_AesGcm256);
            case FC_EccK1AesGcm256_No1_NrC7 -> Hex.fromHex(ALG_PID_PREFIX_EccK1AesGcm256);
            case FC_X25519AesGcm256_No1_NrC7 -> Hex.fromHex(ALG_PID_PREFIX_X25519AesGcm256);
            case FC_ChaCha20_No1_NrC7 -> Hex.fromHex(ALG_PID_PREFIX_ChaCha20);
            case FC_EccK1ChaCha20_No1_NrC7 -> Hex.fromHex(ALG_PID_PREFIX_EccK1ChaCha20);
            case FC_ChaCha20Poly1305_No1_NrC7 -> Hex.fromHex(ALG_PID_PREFIX_ChaCha20Poly1305);
            case FC_EccK1ChaCha20Poly1305_No1_NrC7 -> Hex.fromHex(ALG_PID_PREFIX_EccK1ChaCha20Poly1305);
            case BitCore_EccAes256 -> Hex.fromHex(ALG_PID_PREFIX_BitCore);
            default -> null;
        };

        if (algBytes == null) return null;

        // BitCore carries the full 32-byte HMAC-SHA256 tag as its sum
        if (alg == AlgorithmId.BitCore_EccAes256 && sum.length != CryptoConstants.HMAC_SHA256_LENGTH)
            return null;

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            // Write algBytes (6 bytes)
            outputStream.write(algBytes);

            // Write EncryptType (1 byte)
            outputStream.write(type.getNumber());

            // Conditionally write pubKeyA based on EncryptType
            if (type == EncryptType.AsyOneWay || type == EncryptType.AsyTwoWay) {
                if (pubkeyA == null) return null; // Check if pubKeyA is needed but not provided
                outputStream.write(pubkeyA);
            }

            // Conditionally write keyName based on EncryptType
            if (type == EncryptType.Symkey) {
                outputStream.write(keyName);
            }

            // Write iv (12 or 16 bytes depending on algorithm)
            outputStream.write(iv);

            // Write cipher (variable length)
            outputStream.write(cipher);

            // Write sum (4 bytes) only for non-GCM algorithms
            if (requiresSum) {
                outputStream.write(sum);
            }

            // Convert the output stream to a byte array
            return outputStream.toByteArray();
        } catch (IOException e) {
            // Handle potential IO exceptions (shouldn't happen with ByteArrayOutputStream)
            return null;
        }
    }

    public void makeKeyName(byte[] key) {
        if(key==null)return;
        keyName = new byte[CryptoConstants.KEY_NAME_LENGTH];
        byte[] hash = Hash.sha256(key);
        System.arraycopy(hash,0,keyName,0,CryptoConstants.KEY_NAME_LENGTH);
    }

    public static CryptoDataByte fromBundle(byte[] bundle) {
        if (bundle == null || bundle.length < CryptoConstants.ALG_BYTES_LENGTH + 2) {
            return null;
        }
        int offset = 0;
        CryptoDataByte cryptoData = new CryptoDataByte();

        // Extract the algorithm bytes
        byte[] algBytes = new byte[CryptoConstants.ALG_BYTES_LENGTH];
        System.arraycopy(bundle, offset, algBytes, 0, CryptoConstants.ALG_BYTES_LENGTH);
        offset += CryptoConstants.ALG_BYTES_LENGTH;
        // Map algorithm bytes back to AlgorithmId. Both the new PID-based prefixes
        // and the legacy sequential prefixes are accepted so old ciphers still decrypt.
        AlgorithmId alg = switch (Hex.toHex(algBytes)) {
            // New PID-based prefixes (first 12 hex chars of the on-chain PID)
            case ALG_PID_PREFIX_AesCbc256 -> AlgorithmId.FC_AesCbc256_No1_NrC7;
            case ALG_PID_PREFIX_EccK1AesCbc256 -> AlgorithmId.FC_EccK1AesCbc256_No1_NrC7;
            case ALG_PID_PREFIX_AesGcm256 -> AlgorithmId.FC_AesGcm256_No1_NrC7;
            case ALG_PID_PREFIX_EccK1AesGcm256 -> AlgorithmId.FC_EccK1AesGcm256_No1_NrC7;
            case ALG_PID_PREFIX_X25519AesGcm256 -> AlgorithmId.FC_X25519AesGcm256_No1_NrC7;
            case ALG_PID_PREFIX_ChaCha20 -> AlgorithmId.FC_ChaCha20_No1_NrC7;
            case ALG_PID_PREFIX_EccK1ChaCha20 -> AlgorithmId.FC_EccK1ChaCha20_No1_NrC7;
            case ALG_PID_PREFIX_ChaCha20Poly1305 -> AlgorithmId.FC_ChaCha20Poly1305_No1_NrC7;
            case ALG_PID_PREFIX_EccK1ChaCha20Poly1305 -> AlgorithmId.FC_EccK1ChaCha20Poly1305_No1_NrC7;
            case ALG_PID_PREFIX_BitCore -> AlgorithmId.BitCore_EccAes256;
            // Legacy sequential prefixes (kept for backward-compatible decryption)
            case "000000000001" -> AlgorithmId.FC_AesCbc256_No1_NrC7;
            case "000000000002" -> AlgorithmId.FC_EccK1AesCbc256_No1_NrC7;
            case "000000000003" -> AlgorithmId.FC_AesGcm256_No1_NrC7;
            case "000000000004" -> AlgorithmId.FC_EccK1AesGcm256_No1_NrC7;
            case "000000000005" -> AlgorithmId.FC_X25519AesGcm256_No1_NrC7;
            case "000000000006" -> AlgorithmId.FC_ChaCha20_No1_NrC7;
            case "000000000007" -> AlgorithmId.FC_EccK1ChaCha20_No1_NrC7;
            case "000000000008" -> AlgorithmId.FC_ChaCha20Poly1305_No1_NrC7;
            case "000000000009" -> AlgorithmId.FC_EccK1ChaCha20Poly1305_No1_NrC7;
            default -> null;
        };

        if (alg == null) return null; // Return null if the algorithm ID isn't recognized

        cryptoData.setAlg(alg);

        // Extract the EncryptType byte
        // Each field below is copied only if the bundle still holds it. The single minimum-length
        // check above covers the algorithm and type, not the key, key name, IV or sum, so a short
        // bundle threw ArrayIndexOutOfBounds instead of being rejected.
        byte typeByte = bundle[6];
        offset++;
        EncryptType type = EncryptType.fromNumber(typeByte); // Assuming EncryptType has a method to get type from a number

        if (type == null) return null;

        cryptoData.setType(type);

        // Check if pubKeyA exists for Asy
        if (type == EncryptType.AsyOneWay || type == EncryptType.AsyTwoWay) {
            // Determine public key size based on algorithm
            int pubKeySize = (alg == AlgorithmId.FC_X25519AesGcm256_No1_NrC7) ? CryptoConstants.PUBKEY_X25519_LENGTH : CryptoConstants.PUBKEY_COMPRESSED_LENGTH;
            if (bundle.length < offset + pubKeySize) return null;
            byte[] pubKeyA = new byte[pubKeySize];
            System.arraycopy(bundle, offset, pubKeyA, 0, pubKeySize);
            cryptoData.setPubkeyA(pubKeyA);
            offset += pubKeySize;
        }

        // Check if keyName exists for Symkey or Password
        if (type == EncryptType.Symkey) {
            if (bundle.length < offset + CryptoConstants.KEY_NAME_LENGTH) return null;
            byte[] keyName = new byte[CryptoConstants.KEY_NAME_LENGTH];
            System.arraycopy(bundle, offset, keyName, 0, CryptoConstants.KEY_NAME_LENGTH);
            cryptoData.setKeyName(keyName);
            offset += CryptoConstants.KEY_NAME_LENGTH;
        }

        // Extract iv (length depends on algorithm: 12 bytes for GCM/ChaCha20, 16 bytes for CBC)
        boolean uses12ByteIv = (alg == AlgorithmId.FC_AesGcm256_No1_NrC7 ||
                                alg == AlgorithmId.FC_EccK1AesGcm256_No1_NrC7 ||
                                alg == AlgorithmId.FC_X25519AesGcm256_No1_NrC7 ||
                                alg == AlgorithmId.FC_ChaCha20_No1_NrC7 ||
                                alg == AlgorithmId.FC_EccK1ChaCha20_No1_NrC7 ||
                                alg == AlgorithmId.FC_ChaCha20Poly1305_No1_NrC7 ||
                                alg == AlgorithmId.FC_EccK1ChaCha20Poly1305_No1_NrC7);

        int ivLength = uses12ByteIv ? CryptoConstants.IV_LENGTH_GCM : CryptoConstants.IV_LENGTH_CBC;
        if (bundle.length < offset + ivLength) return null;
        byte[] iv = new byte[ivLength];
        System.arraycopy(bundle, offset, iv, 0, ivLength);
        cryptoData.setIv(iv);
        offset += ivLength;

        // Algorithms with built-in authentication (GCM, Poly1305) don't include a separate sum
        boolean hasSum = !alg.isAead();

        // Calculate cipher length dynamically. BitCore carries the full 32-byte
        // HMAC-SHA256 tag as its sum; other algorithms use a 4-byte sum.
        int sumLength;
        if (!hasSum) sumLength = 0;
        else if (alg == AlgorithmId.BitCore_EccAes256) sumLength = CryptoConstants.HMAC_SHA256_LENGTH;
        else sumLength = CryptoConstants.SUM_LENGTH;
        int cipherLength = bundle.length - offset - sumLength;

        if (cipherLength <= 0) return null; // Sanity check to ensure we have a valid cipher length

        // Extract cipher
        byte[] cipher = new byte[cipherLength];
        System.arraycopy(bundle, offset, cipher, 0, cipherLength);
        cryptoData.setCipher(cipher);
        offset += cipherLength;

        // Extract sum (trailing bytes) only for non-GCM algorithms
        if (hasSum) {
            byte[] sum = new byte[sumLength];
            System.arraycopy(bundle, offset, sum, 0, sumLength);
            cryptoData.setSum(sum);
        }
        cryptoData.setCode(0);
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
        clearSymkey();
        clearPriKeyA();
        clearPriKeyB();
    }

    public void clearAllSensitiveDataButSymkey() {
        clearPassword();
        clearPriKeyA();
        clearPriKeyB();
    }

    public void clearSymkey() {
        BytesUtils.clearByteArray(this.symkey);
        this.symkey = null;
    }

    public void clearPassword() {
        BytesUtils.clearByteArray(this.password);
        this.password = null;
    }

    public void clearPriKeyA() {
        BytesUtils.clearByteArray(this.prikeyA);
        this.prikeyA = null;
    }

    public void clearPriKeyB() {
        BytesUtils.clearByteArray(this.prikeyB);
        this.prikeyB = null;
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

    public Kdf getKdf() {
        return kdf;
    }

    public void setKdf(Kdf kdf) {
        this.kdf = kdf;
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

    public byte[] getSymkey() {
        return symkey;
    }

    public void setSymkey(byte[] symkey) {
        this.symkey = symkey;
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

    public byte[] getPubkeyA() {
        return pubkeyA;
    }

    public void setPubkeyA(byte[] pubkeyA) {
        this.pubkeyA = pubkeyA;
    }

    public byte[] getPubkeyB() {
        return pubkeyB;
    }

    public void setPubkeyB(byte[] pubkeyB) {
        this.pubkeyB = pubkeyB;
    }

    public byte[] getPrikeyA() {
        return prikeyA;
    }

    public void setPrikeyA(byte[] prikeyA) {
        this.prikeyA = prikeyA;
    }

    public byte[] getPrikeyB() {
        return prikeyB;
    }

    public void setPrikeyB(byte[] prikeyB) {
        this.prikeyB = prikeyB;
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

    public static byte[] makeSum4(byte[] symkey, byte[] iv, byte[] did) {
        if(symkey!=null && iv!=null && did!=null) {
            byte[] sum32 = Hash.sha256(BytesUtils.addByteArray(symkey, BytesUtils.addByteArray(iv, did)));
            return BytesUtils.getPartOfBytes(sum32, 0, 4);
        }
        return null;
    }

    public void makeSum4() {
        if(symkey==null){
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
        byte[] sum32 = Hash.sha256(BytesUtils.addByteArray(symkey, BytesUtils.addByteArray(iv, did)));
        sum = BytesUtils.getPartOfBytes(sum32, 0, 4);
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
                newSum = EccAes256K1P7.getSum4(symkey,iv,cipher);
            default -> newSum = makeSum4(symkey,iv,did);
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
        newSum = CryptoDataByte.makeSum4(symkey,iv,did);

        String sumHex = Hex.toHex(sum);
        String newSumHex = Hex.toHex(newSum);
        if(!newSumHex.equals(sumHex)){
            setCodeMessage(CodeMessage.Code4011BadSum);
            return false;
        }
        return true;
    }

    public static CryptoDataByte fromBase64(String base64) {
        byte[] bundle = Base64.getDecoder().decode(base64);
        return fromBundle(bundle);
    }

    public void printCodeMessage() {
        if (code != null || message != null)
            org.slf4j.LoggerFactory.getLogger(CryptoDataByte.class).debug("{} : {}", code, message);
    }

    public byte[] getKeyName() {
        return keyName;
    }

    public void setKeyName(byte[] keyName) {
        this.keyName = keyName;
    }

}
