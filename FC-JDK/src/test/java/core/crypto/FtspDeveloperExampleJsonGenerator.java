package core.crypto;

import core.crypto.Algorithm.Bitcore;
import core.crypto.Algorithm.HKDF;
import core.crypto.Algorithm.X25519;
import core.crypto.old.EccAes256K1P7;
import data.fcData.AlgorithmId;
import data.fcData.Signature;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import utils.Hex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.security.SecureRandom;
import java.util.HexFormat;

import static data.fcData.AlgorithmId.*;

/**
 * One-shot helper to print developer-facing JSON examples (stdout). Not a unit test.
 * Run: {@code mvn -q test-compile exec:java -pl FC-JDK -Dexec.classpathScope=test -Dexec.mainClass=core.crypto.FtspDeveloperExampleJsonGenerator}
 */
public final class FtspDeveloperExampleJsonGenerator {

    private static final HexFormat HF = HexFormat.of();

    private static byte[] h(String hex) {
        return HF.parseHex(hex.replace(" ", ""));
    }

    private static String stripSensitive(CryptoDataByte cdb) {
        cdb.setPrikeyA(null);
        cdb.setPrikeyB(null);
        cdb.setSymkey(null);
        cdb.setPassword(null);
        return cdb.toNiceJson();
    }

    private static CryptoDataByte asyEncrypt(
            AlgorithmId alg,
            EncryptType type,
            byte[] priEphemeral,
            byte[] pubRecipient,
            byte[] iv,
            byte[] plaintext) throws Exception {
        Encryptor enc = new Encryptor(alg);
        CryptoDataByte cdb = new CryptoDataByte();
        cdb.setType(type);
        cdb.setPrikeyA(priEphemeral);
        cdb.setPubkeyB(pubRecipient);
        cdb.setIv(iv);
        enc.checkKeysMakeType(pubRecipient, priEphemeral, cdb);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(plaintext);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            enc.encryptStreamByAsy(bis, bos, cdb);
            cdb.setCipher(bos.toByteArray());
        }
        if (alg != FC_AesGcm256_No1_NrC7
                && alg != FC_EccK1AesGcm256_No1_NrC7
                && alg != FC_X25519AesGcm256_No1_NrC7) {
            cdb.makeSum4();
        }
        cdb.setAlg(alg);
        cdb.setType(type);
        cdb.set0CodeMessage();
        if (type == EncryptType.AsyOneWay) {
            cdb.setPubkeyB(null);
        }
        return cdb;
    }

    public static void main(String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        byte[] msg = "Hello world!".getBytes(StandardCharsets.UTF_8);
        byte[] iv16 = h("000102030405060708090a0b0c0d0e0f");
        byte[] iv12 = h("000102030405060708090a0b");

        byte[] pubFidA = h("030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a");
        byte[] priFidA = h("a048f6c843f92bfe036057f7fc2bf2c27353c624cf7ad97e98ed41432f700575");
        byte[] pubFidB = h("02536e4f3a6871831fa91089a5d5a950b96a31c861956f01459c0cd4f4374b2f67");
        byte[] priFidB = h("ee72e6dd4047ef7f4c9886059cbab42eaab08afe7799cbc0539269ee7e2ec30c");
        byte[] symkey = h("dc1e7c03e162397b355b6f1c895dfdf3790d98c10b920c55e91272b8eecada2a");
        char[] password = "MyPassword".toCharArray();

        // FTSP11 — EccK1 AES-GCM, AsyOneWay (ephemeral B → static A)
        System.out.println("\n### FTSP11");
        System.out.println(stripSensitive(asyEncrypt(FC_EccK1AesGcm256_No1_NrC7, EncryptType.AsyOneWay, priFidB, pubFidA, iv12, msg)));

        // FTSP12 — sym AES-GCM
        System.out.println("\n### FTSP12");
        Encryptor gcmSym = new Encryptor(FC_AesGcm256_No1_NrC7);
        CryptoDataByte c12 = gcmSym.encryptBySymkey(msg, symkey, iv12);
        c12.setAlg(FC_AesGcm256_No1_NrC7);
        c12.setType(EncryptType.Symkey);
        System.out.println(stripSensitive(c12));

        // FTSP13 — HKDF (not a cipher JSON)
        byte[] ikm13 = symkey;
        byte[] salt13 = iv16;
        byte[] info13 = "FTSP13-dev-example".getBytes(StandardCharsets.UTF_8);
        byte[] okm13 = HKDF.hkdf(ikm13, salt13, info13, 32);
        System.out.println("\n### FTSP13");
        System.out.println("{");
        System.out.println("  \"ikm\": \"" + Hex.toHex(ikm13) + "\",");
        System.out.println("  \"salt\": \"" + Hex.toHex(salt13) + "\",");
        System.out.println("  \"info\": \"FTSP13-dev-example\",");
        System.out.println("  \"L\": 32,");
        System.out.println("  \"okm\": \"" + Hex.toHex(okm13) + "\"");
        System.out.println("}");

        // FTSP14 — symkey CBC
        System.out.println("\n### FTSP14 symkey");
        Encryptor cbc = new Encryptor(FC_AesCbc256_No1_NrC7);
        CryptoDataByte c14s = cbc.encryptBySymkey(msg, symkey, iv16);
        c14s.setAlg(FC_AesCbc256_No1_NrC7);
        c14s.setType(EncryptType.Symkey);
        System.out.println(stripSensitive(c14s));

        System.out.println("\n### FTSP14 password");
        byte[] pwSk = Encryptor.passwordToSymkey(password, iv16);
        CryptoDataByte c14p = cbc.encryptBySymkey(msg, pwSk, iv16);
        c14p.setAlg(FC_AesCbc256_No1_NrC7);
        c14p.setType(EncryptType.Password);
        System.out.println(stripSensitive(c14p));

        // FTSP15 — AsyTwoWay (A → B) and AsyOneWay (ephemeral B → A)
        System.out.println("\n### FTSP15 AsyTwoWay (sender fidA → recipient fidB)");
        System.out.println(stripSensitive(asyEncrypt(FC_EccK1AesCbc256_No1_NrC7, EncryptType.AsyTwoWay, priFidA, pubFidB, iv16, msg)));
        System.out.println("\n### FTSP15 AsyOneWay (ephemeral fidB → fidA)");
        System.out.println(stripSensitive(asyEncrypt(FC_EccK1AesCbc256_No1_NrC7, EncryptType.AsyOneWay, priFidB, pubFidA, iv16, msg)));

        // FTSP16 — BitCore encbuf (SHA1PRNG — OpenJDK typical)
        System.out.println("\n### FTSP16");
        var kpA = Bitcore.createKeyPair(priFidA);
        SecureRandom rng = SecureRandom.getInstance("SHA1PRNG");
        rng.setSeed(h("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
        byte[] encbuf = Bitcore.encrypt(msg, kpA.getPublic(), rng);
        System.out.println("{");
        System.out.println("  \"note\": \"encbuf = ephemeralPub ‖ IV ‖ ciphertext ‖ HMAC-SHA256 (see FTSP16); SHA1PRNG seed fixed for reproducibility on typical OpenJDK.\",");
        System.out.println("  \"recipientFid\": \"FEk41Kqjar45fLDriztUDTUkdki7mmcjWK\",");
        System.out.println("  \"encbuf\": \"" + Hex.toHex(encbuf) + "\"");
        System.out.println("}");

        // FTSP17 — P7 AsyTwoWay B → A
        System.out.println("\n### FTSP17");
        CryptoDataByte p7 = new CryptoDataByte();
        p7.setAlg(EccAes256K1P7_No1_NrC7);
        p7.setType(EncryptType.AsyTwoWay);
        p7.setIv(iv16);
        p7.setData(msg);
        p7.setPrikeyA(priFidB);
        p7.setPubkeyB(pubFidA);
        byte[] symP7 = EccAes256K1P7.asyKeyToSymkey(priFidB, pubFidA, iv16);
        p7.setSymkey(symP7);
        new EccAes256K1P7().aesEncrypt(p7);
        p7.setPubkeyA(pubFidB);
        p7.clearSymkey();
        p7.setAlg(EccAes256K1P7_No1_NrC7);
        p7.setType(EncryptType.AsyTwoWay);
        p7.set0CodeMessage();
        System.out.println(stripSensitive(p7));

        // FTSP18 — X25519 (raw 32-byte keys; not the secp FIDs)
        byte[] xPriA = new byte[32];
        byte[] xPriB = new byte[32];
        java.util.Arrays.fill(xPriA, (byte) 0x41);
        java.util.Arrays.fill(xPriB, (byte) 0x42);
        byte[] xPubA = X25519.generatePublicKey(xPriA);
        byte[] xPubB = X25519.generatePublicKey(xPriB);
        byte[] xShared = X25519.getSharedSecret(xPriA, xPubB);
        byte[] xOkm = X25519.sharedSecretToSymkey(xShared, iv12);
        System.out.println("\n### FTSP18");
        System.out.println("{");
        System.out.println("  \"note\": \"X25519 uses raw 32-byte scalars (different from secp256k1 FID keys above).\",");
        System.out.println("  \"priA\": \"" + Hex.toHex(xPriA) + "\",");
        System.out.println("  \"pubA\": \"" + Hex.toHex(xPubA) + "\",");
        System.out.println("  \"priB\": \"" + Hex.toHex(xPriB) + "\",");
        System.out.println("  \"pubB\": \"" + Hex.toHex(xPubB) + "\",");
        System.out.println("  \"sharedSecret\": \"" + Hex.toHex(xShared) + "\",");
        System.out.println("  \"saltIv12\": \"" + Hex.toHex(iv12) + "\",");
        System.out.println("  \"hkdfInfo\": \"hkdf\",");
        System.out.println("  \"symkey32\": \"" + Hex.toHex(xOkm) + "\"");
        System.out.println("}");

        // FTSP19
        System.out.println("\n### FTSP19");
        System.out.println(stripSensitive(asyEncrypt(FC_X25519AesGcm256_No1_NrC7, EncryptType.AsyTwoWay, xPriA, xPubB, iv12, msg)));

        // FTSP20
        System.out.println("\n### FTSP20");
        Encryptor ch = new Encryptor(FC_ChaCha20_No1_NrC7);
        CryptoDataByte c20 = ch.encryptBySymkey(msg, symkey, iv12);
        c20.setAlg(FC_ChaCha20_No1_NrC7);
        c20.setType(EncryptType.Symkey);
        System.out.println(stripSensitive(c20));

        // FTSP21
        System.out.println("\n### FTSP21");
        System.out.println(stripSensitive(asyEncrypt(FC_EccK1ChaCha20_No1_NrC7, EncryptType.AsyTwoWay, priFidA, pubFidB, iv12, msg)));

        // FTSP22–24 signatures
        String hello = "Hello world!";
        System.out.println("\n### FTSP22");
        Signature s22 = new Signature().sign(hello, priFidA, BTC_EcdsaSignMsg_No1_NrC7);
        System.out.println(s22.toNiceJson());

        System.out.println("\n### FTSP23");
        Signature s23 = new Signature().sign(hello, symkey, FC_Sha256SymSignMsg_No1_NrC7);
        System.out.println(s23.toNiceJson());

        System.out.println("\n### FTSP24");
        Signature s24 = new Signature().sign(hello, priFidA, FC_SchnorrSignMsg_No1_NrC7);
        System.out.println(s24.toNiceJson());
    }

    private FtspDeveloperExampleJsonGenerator() {}
}
