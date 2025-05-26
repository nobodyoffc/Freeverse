package core.crypto.old.EccAesFromPeiTeam;

//import com.sun.scenario.effect.impl.sw.sse.SSEBlend_SRC_OUTPeer;
import org.junit.Test;

public class test {

    //测试数字信封的加密模块
    @Test
    public void testEncrypt() throws Exception {
        envelop env = new envelop();
        String msg = "This is a plaintext message from Java to JS";
        String encMsg = env.encryptMsg(msg);
        String symKey = env.getSymKey();
        String encKey = env.encryptKey(symKey);

        String decKey = env.decryptKey(encKey);
        env.setSymKey(decKey);
        String decMsg = env.decryptMsg(encMsg);

        System.out.println("##decKey");
        System.out.println(decKey);
        System.out.println("##symKey:");
        System.out.println(symKey);
        System.out.println("##encMsg:");
        System.out.println(encMsg);
        System.out.println("##encKey:");
        System.out.println(encKey);
        System.out.println("##secKey:");
        System.out.println(env.getEciesSecKey());
        System.out.println("##decMsg:");
        System.out.println(decMsg);
    }

    //测试数字信封的解密模块
    @Test
    public void testDecrypt() throws Exception {
        envelop env = new envelop();
        String sk = "73008242b797529eecf305cbe261ccdfabf37c78491517b2e499cefbf6e694f6";
        env.setEciesSecKey(sk);
        String encMsg = "4a6b84323936c76b09b902b606e1e4073dba66352778380df7c345be6ec65e231158648df835dca2c704a98991b6388a";
        String encKey = "c21e5fe8f816e2ef18ed3fa6be180a8f3a058d1668f23fd70e3c818410539fdd208294ca3f6ef54113e500df1acb4088abd242dbc5c0524dbe77444bd23cc1b85db9d295ae805fca938650f704fcac41a3903900b7f3d42c744e0ee52eddf9dbf3f7f908c22a06ef11bb4f161568e8afa507afc65656c94c94c759812f05817adf8bda5f75dc8d2a47091af8e51108587f33c3b9bfd3f160ab06ad7e9b678e70a6162e0beaeae576000da16bf4aae452";

        String decKey = env.decryptKey(encKey);
        System.out.println("##decKey:");
        System.out.println(decKey);
        env.setSymKey(decKey);

        String decMsg = env.decryptMsg(encMsg);
        System.out.println("##decMsg:");
        System.out.println(decMsg);
    }

    //测试AES加解密
    @Test
    public void testAES() throws Exception {
//        String plaintext = "hello world";
//        String kee = "623F81184B9C5EA643376F95B6C4A547AA771C3F64CF64AC6B477FE2D8C43E76";
        String kee = "762FF9F5F1E27C489E51C53FF6E73B1E4D98FEFEEC4303550748392F5DBB578D";
        byte[] ct = AES256.hexStringToBytes("f3f57997cc6d56d6097fd23ebf996610a8fed7a09137ffb4672e2dd09baa7c58cc5312403b3f74cdfe3366fcb68ed2ff");
//        byte[] ct = AES256.encrypt(plaintext, kee);
        byte[] pt = AES256.decrypt(AES256.byteToHexString(ct), kee);
        System.out.println(AES256.byteToHexString(ct));
        System.out.println(new String(pt));

    }

    //测试ECIES加解密
    @Test
    public void testECIES() throws Exception {
        EXPList.set_EXP_List();
        ECIES ecies = new ECIES();
        ecies.generateKeyPair();
        System.out.println("# Generate Key Pair:");

        String skStr = "447af2b3d4fc829a4616e5289addf29adef050c6dd0922e69409a90aea20669";
        ecies.generateKeyPair(skStr);
//        ecies.generateKeyPair();

        System.out.println("# Secret Key Hex Code:");
        byte[] sk = ecies.secretKey2Bytes();
        for (int i=0; i<sk.length;i++) {
            System.out.printf("%02X",sk[i]);
        }
        System.out.println();

        byte[] pk = ecies.publicKey2Bytes();
        System.out.println("# Public Key Hex Code:");
        for (int i = 0; i < pk.length; i++) {
            System.out.format("%02X", pk[i]);
        }
        System.out.println();

//        String str = "Hello world!";
//        System.out.println("# String to be Encrypted:");
//        System.out.println(str);
//        byte[] ct = ecies.encrypt(str);

        byte[] ct = AES256.hexStringToBytes("11ce651b6e779968a0f9d1f8e2e587463a9740881b10679a753764fc384cac47169153a3a73b8055b7e1976e1e9cc73f313dca44ef28f6514274272dbbf2e36a02eb6b8fdeaf0c3fafabfd9da166bac312e1f261aa92300e243b0c33de9b623f919009c1f6a879928a3d1011834a84fe581c1ac81572ace36169922d9577975380fcd0602a9d13b4e8fdd420b3e786f0ac283d0a923725353c2a3b8728ba3ed5bb262c54c3897c8bcdade3c3e53f2cad");

        System.out.println("# Cypher Hex Code:");
        for (int i = 0; i < ct.length; i++) {
            System.out.format("%02X", ct[i]);
        }
        System.out.println();
        byte[] pt = ecies.decrypt(ct);
        String string = new String(pt);
        System.out.println("# Decrypted String:");
        System.out.println(string);
    }

}
