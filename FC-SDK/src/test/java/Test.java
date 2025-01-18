import com.google.common.hash.Hashing;
import crypto.Hash;
import fcData.AlgorithmId;
import fcData.Signature;
import tools.BytesTools;
import tools.Hex;

public class Test {
    public static void main(String[] args) {
//        byte[] symKey = BytesTools.bytesMerger(passwordBytes, randomBytes);
        byte[] passwordBytes = BytesTools.getRandomBytes(16);
        byte[] randomBytes = BytesTools.getRandomBytes(8);
        long start;
        byte[] hash;

        start= System.currentTimeMillis();
        hash =Hash.sha256(BytesTools.addByteArray(Hash.sha256(passwordBytes), randomBytes));
        System.out.println(Hex.toHex(hash));
        System.out.println("256:"+(System.currentTimeMillis()-start));

        start = System.currentTimeMillis();
        hash= sha256(BytesTools.addByteArray(sha256(passwordBytes), randomBytes));
        System.out.println(Hex.toHex(hash));
        System.out.println("local 256:"+(System.currentTimeMillis()-start));

        start = System.currentTimeMillis();
        hash = Hash.sha512(BytesTools.addByteArray(Hash.sha512(passwordBytes), randomBytes));
        System.out.println(Hex.toHex(hash));
        System.out.println("512:"+(System.currentTimeMillis()-start));



    }

    @org.junit.jupiter.api.Test
    public void test(){
        String keyStr = "db91fc9c16fcc9ae9330ac51b6a30442ab348ce61a43394c34c2612f88fa6019";
        byte[] key = Hex.fromHex(keyStr);

        Signature signature1 = new Signature();
        signature1.sign("hello",key, AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7);

        System.out.println(signature1.toNiceJson());

        signature1.strToBytes();

        byte[] bytes = signature1.toBundle();

        Signature signature2 = Signature.fromBundle(bytes);

        signature2.bytesToStr();
        System.out.println(signature2.toNiceJson());

        signature2.setKey(key);
        System.out.println(signature2.verify());
    }
    public static byte[] sha256(byte[] b) {
        return Hashing.sha256().hashBytes(b).asBytes();
    }
}
