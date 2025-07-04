package data.fcData;

import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import core.crypto.CryptoDataByte;
import core.crypto.Decryptor;
import core.crypto.Encryptor;
import core.crypto.KeyTools;
import utils.BytesUtils;
import utils.Hex;
import org.junit.Test;

public class TransferUnit extends FcObject{
    private boolean isEncrypted;
    private boolean isSymEncrypted;
    private byte flag = 0;//
    private byte[] keyName;
    private byte[] data;

    public TransferUnit() {}

    @Test
    public void test(){
        TalkUnit talkUnit = new TalkUnit();
        talkUnit.setFrom("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
        talkUnit.setIdType(TalkUnit.IdType.GROUP);
        talkUnit.setDataType(TalkUnit.DataType.SIGNED_TEXT);

        Signature signature = new Signature();
        signature.setFid("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
        String msg = "hello world!";
        signature.sign(msg, KeyTools.getPrikey32("L2bHRej6Fxxipvb4TiR5bu1rkT3tRp8yWEsUy4R1Zb8VMm2x7sd8"), AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7);

        talkUnit.setData(signature);

        talkUnit.toBytes();
        ///Test crypt
        String symkey = "db91fc9c16fcc9ae9330ac51b6a30442ab348ce61a43394c34c2612f88fa6019";
        byte[] bundle = encrypt(talkUnit, Hex.fromHex(symkey));
//        byte[] bundle = encryptor.encryptToBundleBySymkey(talkUnit.getBytes(),Hex.fromHex(symkey));

//        Decryptor decryptor = new Decryptor();
//        CryptoDataByte cryptoDataByte2 = decryptor.decryptBundleBySymkey(bundle,Hex.fromHex(symkey));
//        System.out.println(cryptoDataByte2.toJson());


//        TransferUnit transferUnit = new TransferUnit();
//        transferUnit.setEncrypted(true);
//        transferUnit.setSymEncrypted(true);
//        transferUnit.setData(bundle);

        //receive
//        TransferUnit transferUnit1 = TransferUnit.fromBytes(transferUnit.toBytes());
//
//        System.out.println(transferUnit1.getFlag());
//        System.out.println(Hex.toHex(transferUnit1.getKeyName()));
//        System.out.println(Hex.toHex(transferUnit1.getData()));

        //

//        byte[] bundle1 = transferUnit1.getData();

        decrypt(bundle, Hex.fromHex(symkey));
    }

    private static byte[] encrypt(TalkUnit talkUnit, byte[] symkey) {
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte = encryptor.encryptBySymkey(talkUnit.toBytes(),symkey);
        return cryptoDataByte.toBundle();
    }

    public static TalkUnit decrypt(byte[] bundle, byte[] symkey) {
        CryptoDataByte cryptoDataByte1 = CryptoDataByte.fromBundle(bundle);
        Decryptor decryptor = new Decryptor();
        cryptoDataByte1.setSymkey(symkey);
        decryptor.decrypt(cryptoDataByte1);
        return TalkUnit.fromBytes(cryptoDataByte1.getData());
    }


    public byte[] toBytes(){
        if(isSymEncrypted && keyName==null){
            System.out.println("The keyName is required.");
            return null;
        }
        try(ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder()) {
            byteArrayBuilder.write(flag);
            if (isEncrypted && isSymEncrypted)
                byteArrayBuilder.write(keyName);
            byteArrayBuilder.write(data);
            return byteArrayBuilder.toByteArray();
        }
    }

    public static TransferUnit fromBytes(byte[] bytes) {
        TransferUnit transferUnit = new TransferUnit();
        int offset = 0;

        // Extract the flag (1 byte)
        transferUnit.flag = bytes[offset];
        offset += 1;

        // Check if the first bit of the flag is 0 (meaning keyName is included)
        transferUnit.setEncrypted(Boolean.TRUE.equals(BytesUtils.getBit(transferUnit.flag, 0)));
        transferUnit.setSymEncrypted(Boolean.TRUE.equals(BytesUtils.getBit(transferUnit.flag, 1)));
        if (transferUnit.isEncrypted && transferUnit.isSymEncrypted) {
            int keyNameLength = 6;
            transferUnit.keyName = new byte[keyNameLength];
            System.arraycopy(bytes, offset, transferUnit.keyName, 0, keyNameLength);
            offset += keyNameLength;
        }

        // Remaining bytes are the cipherBundle
        int dataLength = bytes.length - offset;
        transferUnit.data = new byte[dataLength];
        System.arraycopy(bytes, offset, transferUnit.data, 0, dataLength);

        return transferUnit;
    }



    public byte getFlag() {
        return flag;
    }

    public void setFlag(byte flag) {
        this.flag = flag;
    }

    public byte[] getKeyName() {
        return keyName;
    }

    public void setKeyName(byte[] keyName) {
        this.keyName = keyName;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public boolean isEncrypted() {
        return isEncrypted;
    }

    public void setEncrypted(boolean encrypted) {
        isEncrypted = encrypted;
        flag = BytesUtils.setBit(flag,0,isEncrypted);
    }

    public boolean isSymEncrypted() {
        return isSymEncrypted;
    }

    public void setSymEncrypted(boolean symEncrypted) {
        isSymEncrypted = symEncrypted;
        flag = BytesUtils.setBit(flag,1,isSymEncrypted);
    }
}
