package fcData;

import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import crypto.CryptoDataByte;
import crypto.Decryptor;
import crypto.Encryptor;
import crypto.KeyTools;
import javaTools.BytesTools;
import javaTools.Hex;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

public class TransferUnit {
    private boolean isEncrypted;
    private boolean isSymEncrypted;
    private byte flag = 0;//
    private byte[] keyName;
    private byte[] data;

//    public TransferUnit(byte[] data) {
//        this.isEncrypted = false;
//        this.isSymEncrypted = false;
//        this.flag = 0;
//        BytesTools.setBit(this.flag, 0, false);
//        BytesTools.setBit(this.flag, 1, false);
//        this.data = data;
//    }

//    public TransferUnit(byte[] keyName, byte[] data) {
//        this.isEncrypted = true;
//        this.isSymEncrypted = true;
//        this.flag = 0;
//        BytesTools.setBit(this.flag, 0, true);
//        BytesTools.setBit(this.flag, 1, true);
//        this.keyName = keyName;
//
//        this.data = data;
//    }
//
//    public TransferUnit(boolean isEncrypted,boolean isSymEncrypted, byte[] keyName, byte[] data) {
//        this.isEncrypted = isSymEncrypted;
//        this.isSymEncrypted = isSymEncrypted;
//        this.flag = 0;
//        if(isEncrypted) {
//            BytesTools.setBit(this.flag, 0, true);
//            if (isSymEncrypted) {
//                BytesTools.setBit(this.flag, 1, true);
//                this.keyName = keyName;
//            }
//        }
//        this.data = data;
//    }

    public TransferUnit() {}

    @Test
    public void test(){
        TalkUnit talkUnit = new TalkUnit();
        talkUnit.setFrom("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
        talkUnit.setIdType(TalkUnit.IdType.GROUP_LIST);
        talkUnit.setToList(new ArrayList<>());
        talkUnit.getToList().add("db91fc9c16fcc9ae9330ac51b6a30442ab348ce61a43394c34c2612f88fa6019");
        talkUnit.getToList().add("0be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a");
        talkUnit.setDataType(TalkUnit.DataType.SIGNED_TEXT);

        Signature signature = new Signature();
        signature.setFid("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
        String msg = "hello world!";
        signature.sign(msg, KeyTools.getPriKey32("L2bHRej6Fxxipvb4TiR5bu1rkT3tRp8yWEsUy4R1Zb8VMm2x7sd8"), AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7);

        talkUnit.setData(signature);

        talkUnit.toBytes();
        ///Test crypt
        String symKey = "db91fc9c16fcc9ae9330ac51b6a30442ab348ce61a43394c34c2612f88fa6019";
        byte[] bundle = encrypt(talkUnit, Hex.fromHex(symKey));
//        byte[] bundle = encryptor.encryptToBundleBySymKey(talkUnit.getBytes(),Hex.fromHex(symKey));

//        Decryptor decryptor = new Decryptor();
//        CryptoDataByte cryptoDataByte2 = decryptor.decryptBundleBySymKey(bundle,Hex.fromHex(symKey));
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

        decrypt(bundle, Hex.fromHex(symKey));
    }

    private static byte[] encrypt(TalkUnit talkUnit, byte[] symKey) {
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte = encryptor.encryptBySymKey(talkUnit.toBytes(),symKey);
        return cryptoDataByte.toBundle();
    }

    public static TalkUnit decrypt(byte[] bundle, byte[] symKey) {
        CryptoDataByte cryptoDataByte1 = CryptoDataByte.fromBundle(bundle);
        Decryptor decryptor = new Decryptor();
        cryptoDataByte1.setSymKey(symKey);
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
        transferUnit.setEncrypted(Boolean.TRUE.equals(BytesTools.getBit(transferUnit.flag, 0)));
        transferUnit.setSymEncrypted(Boolean.TRUE.equals(BytesTools.getBit(transferUnit.flag, 1)));
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
        flag = BytesTools.setBit(flag,0,isEncrypted);
    }

    public boolean isSymEncrypted() {
        return isSymEncrypted;
    }

    public void setSymEncrypted(boolean symEncrypted) {
        isSymEncrypted = symEncrypted;
        flag = BytesTools.setBit(flag,1,isSymEncrypted);
    }
}
