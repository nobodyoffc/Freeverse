package test;

import core.crypto.CryptoDataByte;
import core.crypto.Encryptor;
import core.crypto.KeyTools;
import data.fcData.AlgorithmId;
import data.fcData.Signature;
import data.fcData.TalkUnit;
import utils.Hex;
import org.junit.Test;
import utils.JsonUtils;
import utils.ObjectUtils;

import java.util.Map;

import static data.fcData.TalkUnit.fromBytes;

public class ClassTest {

    public static void main(String[] args) {
        String text = "{\"via\":\"FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7\"}";
        Map<String, String> opReturnData = ObjectUtils.objectToMap(text, String.class, String.class);
        JsonUtils.printJson(opReturnData);
    }
    @Test
    public void test() {
        TalkUnit talkUnit = new TalkUnit();
        talkUnit.setFrom("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
        talkUnit.setDataType(TalkUnit.DataType.SIGNED_TEXT);

        Signature signature = new Signature();
        signature.setFid("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
        String msg = "hello world!";
        signature.sign(msg, KeyTools.getPrikey32("L2bHRej6Fxxipvb4TiR5bu1rkT3tRp8yWEsUy4R1Zb8VMm2x7sd8"), AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7);

        talkUnit.setData(signature);

        talkUnit.toBytes();

        System.out.println(Hex.toHex(talkUnit.toBytes()));

        TalkUnit talkUnit1 = fromBytes(talkUnit.toBytes());

        assert talkUnit1 != null;
        System.out.println(talkUnit1.toNiceJson());

        // cipher
        talkUnit.setDataType(TalkUnit.DataType.ENCRYPTED_TEXT);
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);

        String symKey = "db91fc9c16fcc9ae9330ac51b6a30442ab348ce61a43394c34c2612f88fa6019";
        byte[] symKeyBytes = Hex.fromHex(symKey);
        CryptoDataByte cryptoDataByte = encryptor.encryptBySymkey(msg.getBytes(),symKeyBytes);
        talkUnit.setData(cryptoDataByte);

        talkUnit.toBytes();

        talkUnit1 = fromBytes(talkUnit.toBytes());
        assert talkUnit1 != null;
        System.out.println(talkUnit1.toNiceJson());
    }
}
