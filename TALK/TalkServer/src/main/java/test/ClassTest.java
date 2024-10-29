package test;

import crypto.CryptoDataByte;
import crypto.Encryptor;
import crypto.KeyTools;
import fcData.AlgorithmId;
import fcData.Signature;
import fcData.TalkUnit;
import javaTools.Hex;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static fcData.TalkUnit.decrypt;
import static fcData.TalkUnit.fromBytes;

public class ClassTest {

    public static void main(String[] args) {
        new ClassTest().test();
    }
    @Test
    public void test() {
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

        System.out.println(Hex.toHex(talkUnit.toBytes()));

        TalkUnit talkUnit1 = fromBytes(talkUnit.toBytes());

        assert talkUnit1 != null;
        System.out.println(talkUnit1.toNiceJson());

        // cipher
        talkUnit.setDataType(TalkUnit.DataType.ENCRYPTED_TEXT);
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);

        byte[] symKey = Hex.fromHex("db91fc9c16fcc9ae9330ac51b6a30442ab348ce61a43394c34c2612f88fa6019");
        CryptoDataByte cryptoDataByte = encryptor.encryptBySymKey(msg.getBytes(),symKey);
        talkUnit.setData(cryptoDataByte);

        talkUnit.toBytes();

        talkUnit1 = fromBytes(talkUnit.toBytes());
        assert talkUnit1 != null;
        System.out.println(talkUnit1.toNiceJson());

        byte[] cipher = talkUnit1.encrypt(symKey);

        System.out.println(CryptoDataByte.fromBundle(cipher).toNiceJson());

        TalkUnit talkUnit2 = decrypt(cipher,symKey);
        System.out.println(talkUnit2.toNiceJson());
    }
}
