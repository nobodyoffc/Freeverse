import com.google.common.hash.Hashing;
import crypto.Hash;
import javaTools.BytesTools;
import javaTools.Hex;

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
    public static byte[] sha256(byte[] b) {
        return Hashing.sha256().hashBytes(b).asBytes();
    }
}
