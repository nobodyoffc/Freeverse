package core.crypto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import data.fcData.AlgorithmId;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Security;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

import static data.fcData.AlgorithmId.*;

/**
 * Writes the cross-implementation vectors in {@code Protocols/FTSP/vectors/}. FC-JDK is the
 * reference implementation (FTSP0 §5), so the files are generated here and every other
 * implementation tests against them; {@link CryptoVectorsTest} checks FC-JDK itself.
 * Inputs are the FTSP0 §2.1 shared example keys, so the output is stable across runs
 * except for asymmetric ciphers whose ephemeral key the encryptor picks.
 *
 * Run: {@code mvn -q test-compile exec:java -pl FC-JDK -Dexec.classpathScope=test
 * -Dexec.mainClass=core.crypto.CryptoVectorsGenerator -Dexec.args=<vectors dir>}
 */
public final class CryptoVectorsGenerator {

    static final HexFormat HF = HexFormat.of();
    static final byte[] PLAINTEXT = "Hello world!".getBytes(StandardCharsets.UTF_8);
    static final byte[] IV12 = HF.parseHex("000102030405060708090a0b");
    static final byte[] IV16 = HF.parseHex("000102030405060708090a0b0c0d0e0f");
    static final byte[] SYMKEY = HF.parseHex("dc1e7c03e162397b355b6f1c895dfdf3790d98c10b920c55e91272b8eecada2a");
    static final String PASSWORD = "MyPassword";
    static final byte[] PUB_FID_A = HF.parseHex("030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a");
    static final byte[] PRI_FID_A = HF.parseHex("a048f6c843f92bfe036057f7fc2bf2c27353c624cf7ad97e98ed41432f700575");
    static final byte[] PUB_FID_B = HF.parseHex("02536e4f3a6871831fa91089a5d5a950b96a31c861956f01459c0cd4f4374b2f67");
    static final byte[] PRI_FID_B = HF.parseHex("ee72e6dd4047ef7f4c9886059cbab42eaab08afe7799cbc0539269ee7e2ec30c");

    /** The non-conformant salt FreerForMac used for phrase keys before FTSP28 pinned it empty. */
    static final String FREER_MAC_PHRASE_SALT = "fc.freer.phrase.v1";

    public static void main(String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        Path dir = Paths.get(args.length > 0 ? args[0] : "Protocols/FTSP/vectors");
        Files.createDirectories(dir);
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

        write(dir.resolve("kdf.json"), gson, kdfVectors());
        write(dir.resolve("phrase.json"), gson, phraseVectors());
        JsonArray json = new JsonArray();
        JsonArray bundles = new JsonArray();
        cipherVectors(json, bundles);
        write(dir.resolve("cipher-json.json"), gson, doc("CryptoDataStr JSON ciphers (FVEP8). Decrypt cipherJson with secret; plaintext must equal plaintextHex. derivedWith is the KDF that actually produced the key; kdfRecorded says whether the JSON names it.", json));
        write(dir.resolve("bundle.json"), gson, doc("Binary CryptoDataByte bundles (FTSP30). expect=decrypt: parse, check alg/type/kdfRecorded, decrypt with secret. expect=reject: a conforming parser must refuse the bytes. canonical=true: current writers produce exactly these bytes.", bundles));
        System.out.println("Wrote vectors to " + dir.toAbsolutePath().normalize());
    }

    static JsonObject kdfVectors() {
        JsonArray v = new JsonArray();
        for (Kdf kdf : Kdf.values()) {
            for (byte[] salt : new byte[][]{IV12, IV16}) {
                JsonObject o = new JsonObject();
                o.addProperty("id", "KDF-" + (kdf == Kdf.Argon2id_No1_NrC7 ? "ARGON2ID" : "SHA256IV") + "-SALT" + salt.length);
                o.addProperty("kdf", kdf.getDisplayName());
                o.addProperty("kdfId", HF.formatHex(new byte[]{kdf.getId()}));
                o.addProperty("password", PASSWORD);
                o.addProperty("passwordUtf8Hex", HF.formatHex(PASSWORD.getBytes(StandardCharsets.UTF_8)));
                o.addProperty("salt", HF.formatHex(salt));
                o.addProperty("symkey", HF.formatHex(kdf.deriveSymkey(PASSWORD.toCharArray(), salt)));
                v.add(o);
            }
        }
        return doc("Password KDFs. Sha256Iv (FTSP25): SHA256(SHA256(pwUTF8) || salt). Argon2id (FTSP29): Argon2id v0x13, t=3, m=65536 KiB, p=1, 32-byte output. In ciphers the salt is the IV.", v);
    }

    static JsonObject phraseVectors() {
        JsonArray v = new JsonArray();
        // FTSP28 TV1-TV3, then a phrase with a 4-byte UTF-8 character (a surrogate pair in Java).
        String[] phrases = {"Hello world!", "correct horse battery staple", "你好，世界", "🔑 Ünïcødé"};
        for (int i = 0; i < phrases.length; i++) {
            v.add(phrase("PHRASE-ARGON2ID-" + (i + 1), "argon2id-empty-salt", true, phrases[i], new byte[0]));
        }
        String legacy = "correct horse battery staple";
        byte[] legacyUtf8 = legacy.getBytes(StandardCharsets.UTF_8);
        v.add(phrase("PHRASE-LEGACY-FREER-ANDROID-SALT", "argon2id-sha256prefix16-salt", false, legacy,
                Arrays.copyOf(Hash.sha256(legacyUtf8), 16)));
        v.add(phrase("PHRASE-LEGACY-FREERFORMAC-SALT", "argon2id-freerformac-salt", false, legacy,
                FREER_MAC_PHRASE_SALT.getBytes(StandardCharsets.UTF_8)));
        JsonObject sha = new JsonObject();
        sha.addProperty("id", "PHRASE-SHA256");
        sha.addProperty("scheme", "sha256");
        sha.addProperty("conformant", false);
        sha.addProperty("phrase", legacy);
        sha.addProperty("phraseUtf8Hex", HF.formatHex(legacyUtf8));
        sha.addProperty("priKey32", HF.formatHex(Hash.sha256(legacyUtf8)));
        v.add(sha);
        return doc("Phrase to secp256k1 private key (FTSP28). Conformant: Argon2id with the FTSP29 parameters and an empty salt. conformant=false entries exist only to recover keys made by older builds.", v);
    }

    static JsonObject phrase(String id, String scheme, boolean conformant, String phrase, byte[] salt) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("scheme", scheme);
        o.addProperty("conformant", conformant);
        o.addProperty("phrase", phrase);
        o.addProperty("phraseUtf8Hex", HF.formatHex(phrase.getBytes(StandardCharsets.UTF_8)));
        o.addProperty("salt", HF.formatHex(salt));
        o.addProperty("priKey32", HF.formatHex(Kdf.Argon2id_No1_NrC7.deriveSymkey(phrase.toCharArray(), salt)));
        return o;
    }

    static void cipherVectors(JsonArray json, JsonArray bundles) throws Exception {
        CryptoDataByte gcmSym = symkeyCipher(FC_AesGcm256_No1_NrC7, IV12);
        CryptoDataByte cbcSym = symkeyCipher(FC_AesCbc256_No1_NrC7, IV16);
        CryptoDataByte pwGcmArgon = passwordCipher(FC_AesGcm256_No1_NrC7, IV12, Kdf.Argon2id_No1_NrC7);
        CryptoDataByte pwGcmSha = passwordCipher(FC_AesGcm256_No1_NrC7, IV12, Kdf.Sha256Iv_No1_NrC7);
        CryptoDataByte pwCbcSha = passwordCipher(FC_AesCbc256_No1_NrC7, IV16, Kdf.Sha256Iv_No1_NrC7);
        CryptoDataByte asy1 = asyCipher(FC_EccK1AesGcm256_No1_NrC7, EncryptType.AsyOneWay, PRI_FID_B, PUB_FID_A, IV12);
        CryptoDataByte asy2 = asyCipher(FC_EccK1AesGcm256_No1_NrC7, EncryptType.AsyTwoWay, PRI_FID_A, PUB_FID_B, IV12);

        json.add(jsonEntry("JSON-SYMKEY-GCM", gcmSym, symkeySecret(), null, false));
        json.add(jsonEntry("JSON-SYMKEY-CBC", cbcSym, symkeySecret(), null, false));
        json.add(jsonEntry("JSON-PASSWORD-GCM-ARGON2ID", pwGcmArgon, passwordSecret(), Kdf.Argon2id_No1_NrC7, true));
        json.add(jsonEntry("JSON-PASSWORD-GCM-SHA256IV", pwGcmSha, passwordSecret(), Kdf.Sha256Iv_No1_NrC7, true));
        json.add(jsonEntry("JSON-PASSWORD-GCM-NOKDF-ARGON2ID", pwGcmArgon, passwordSecret(), Kdf.Argon2id_No1_NrC7, false));
        json.add(jsonEntry("JSON-PASSWORD-GCM-NOKDF-SHA256IV", pwGcmSha, passwordSecret(), Kdf.Sha256Iv_No1_NrC7, false));
        json.add(jsonEntry("JSON-PASSWORD-CBC-NOKDF-SHA256IV", pwCbcSha, passwordSecret(), Kdf.Sha256Iv_No1_NrC7, false));
        json.add(jsonEntry("JSON-ASYONEWAY-ECCK1GCM", asy1, prikeySecret(PRI_FID_A, null), null, false));
        json.add(jsonEntry("JSON-ASYTWOWAY-ECCK1GCM", asy2, prikeySecret(PRI_FID_B, PUB_FID_A), null, false));

        boolean t4Canonical = CryptoDataByte.WRITE_PASSWORD_BUNDLE_WITH_KDF;
        bundles.add(bundleEntry("BUNDLE-T0-SYMKEY-GCM", gcmSym.toBundle(), gcmSym, null, null, symkeySecret(), true));
        bundles.add(bundleEntry("BUNDLE-T0-SYMKEY-CBC", cbcSym.toBundle(), cbcSym, null, null, symkeySecret(), true));
        bundles.add(bundleEntry("BUNDLE-T1-ASYONEWAY-ECCK1GCM", asy1.toBundle(), asy1, null, null, prikeySecret(PRI_FID_A, null), true));
        bundles.add(bundleEntry("BUNDLE-T2-ASYTWOWAY-ECCK1GCM", asy2.toBundle(), asy2, null, null, prikeySecret(PRI_FID_B, PUB_FID_A), true));

        byte[] t3GcmArgon = withoutKdf(pwGcmArgon).toBundle();
        byte[] t3GcmSha = withoutKdf(pwGcmSha).toBundle();
        byte[] t3CbcSha = withoutKdf(pwCbcSha).toBundle();
        bundles.add(bundleEntry("BUNDLE-T3-PASSWORD-GCM-ARGON2ID", t3GcmArgon, pwGcmArgon, null, Kdf.Argon2id_No1_NrC7, passwordSecret(), !t4Canonical));
        bundles.add(bundleEntry("BUNDLE-T3-PASSWORD-GCM-SHA256IV", t3GcmSha, pwGcmSha, null, Kdf.Sha256Iv_No1_NrC7, passwordSecret(), !t4Canonical));
        bundles.add(bundleEntry("BUNDLE-T3-PASSWORD-CBC-SHA256IV", t3CbcSha, pwCbcSha, null, Kdf.Sha256Iv_No1_NrC7, passwordSecret(), !t4Canonical));

        byte[] t4GcmArgon = toType4(t3GcmArgon, Kdf.Argon2id_No1_NrC7);
        bundles.add(bundleEntry("BUNDLE-T4-PASSWORD-GCM-ARGON2ID", t4GcmArgon, pwGcmArgon, Kdf.Argon2id_No1_NrC7, Kdf.Argon2id_No1_NrC7, passwordSecret(), t4Canonical));
        bundles.add(bundleEntry("BUNDLE-T4-PASSWORD-GCM-SHA256IV", toType4(t3GcmSha, Kdf.Sha256Iv_No1_NrC7), pwGcmSha, Kdf.Sha256Iv_No1_NrC7, Kdf.Sha256Iv_No1_NrC7, passwordSecret(), t4Canonical));
        bundles.add(bundleEntry("BUNDLE-T4-PASSWORD-CBC-SHA256IV", toType4(t3CbcSha, Kdf.Sha256Iv_No1_NrC7), pwCbcSha, Kdf.Sha256Iv_No1_NrC7, Kdf.Sha256Iv_No1_NrC7, passwordSecret(), t4Canonical));

        byte[] t0 = gcmSym.toBundle();
        byte[] legacyPrefix = t0.clone();
        System.arraycopy(HF.parseHex("000000000003"), 0, legacyPrefix, 0, 6);
        bundles.add(bundleEntry("BUNDLE-T0-SYMKEY-GCM-LEGACY-PREFIX", legacyPrefix, gcmSym, null, null, symkeySecret(), false));

        byte[] unknownPrefix = t0.clone();
        Arrays.fill(unknownPrefix, 0, 6, (byte) 0xff);
        byte[] unknownType = t0.clone();
        unknownType[6] = 9;
        byte[] unknownKdf = t4GcmArgon.clone();
        unknownKdf[7] = 0x7f;
        byte[] zeroKdf = t4GcmArgon.clone();
        zeroKdf[7] = 0x00;
        bundles.add(rejectEntry("REJECT-TRUNCATED", "ends inside keyName", Arrays.copyOf(t0, 10)));
        bundles.add(rejectEntry("REJECT-UNKNOWN-ALG-PREFIX", "ffffffffffff is not a registered algorithm prefix", unknownPrefix));
        bundles.add(rejectEntry("REJECT-UNKNOWN-TYPE", "type byte 9 is not registered", unknownType));
        bundles.add(rejectEntry("REJECT-UNKNOWN-KDF", "KDF id 0x7f is not registered", unknownKdf));
        bundles.add(rejectEntry("REJECT-KDF-ZERO", "KDF id 0x00 is invalid", zeroKdf));
        bundles.add(rejectEntry("REJECT-TYPE4-NO-KDF-BYTE", "type 4 with nothing after the type byte", Arrays.copyOf(t4GcmArgon, 7)));
    }

    static CryptoDataByte symkeyCipher(AlgorithmId alg, byte[] iv) {
        CryptoDataByte c = new Encryptor(alg).encryptBySymkey(PLAINTEXT, SYMKEY, iv);
        c.setAlg(alg);
        c.setType(EncryptType.Symkey);
        return requireOk(c);
    }

    static CryptoDataByte passwordCipher(AlgorithmId alg, byte[] iv, Kdf kdf) {
        byte[] key = kdf.deriveSymkey(PASSWORD.toCharArray(), iv);
        CryptoDataByte c = new Encryptor(alg).encryptBySymkey(PLAINTEXT, key, iv);
        c.setAlg(alg);
        c.setType(EncryptType.Password);
        c.setKdf(kdf);
        return requireOk(c);
    }

    static CryptoDataByte asyCipher(AlgorithmId alg, EncryptType type, byte[] priA, byte[] pubB, byte[] iv) throws Exception {
        Encryptor enc = new Encryptor(alg);
        CryptoDataByte c = new CryptoDataByte();
        c.setType(type);
        c.setPrikeyA(priA);
        c.setPubkeyB(pubB);
        c.setIv(iv);
        enc.checkKeysMakeType(pubB, priA, c);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(PLAINTEXT);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            enc.encryptStreamByAsy(bis, bos, c);
            c.setCipher(bos.toByteArray());
        }
        c.setAlg(alg);
        c.setType(type);
        c.set0CodeMessage();
        if (type == EncryptType.AsyOneWay) c.setPubkeyB(null);
        return requireOk(c);
    }

    static CryptoDataByte requireOk(CryptoDataByte c) {
        if (c.getCipher() == null || (c.getCode() != null && c.getCode() != 0))
            throw new IllegalStateException("encryption failed: " + c.getMessage());
        return c;
    }

    /** Password bundles written today (type 3) carry no KDF; drop it so toBundle takes that path. */
    static CryptoDataByte withoutKdf(CryptoDataByte c) {
        CryptoDataByte copy = new CryptoDataByte();
        copy.setAlg(c.getAlg());
        copy.setType(c.getType());
        copy.setIv(c.getIv());
        copy.setCipher(c.getCipher());
        copy.setSum(c.getSum());
        return copy;
    }

    /** Type 3 has nothing between the type byte and the IV, so type 4 is type 3 with the KDF id inserted there. */
    static byte[] toType4(byte[] type3, Kdf kdf) {
        if (type3[6] != EncryptType.Password.getNumber()) throw new IllegalStateException("not a type-3 bundle");
        byte[] out = new byte[type3.length + 1];
        System.arraycopy(type3, 0, out, 0, 7);
        out[6] = CryptoConstants.BUNDLE_TYPE_PASSWORD_WITH_KDF;
        out[7] = kdf.getId();
        System.arraycopy(type3, 7, out, 8, type3.length - 7);
        return out;
    }

    static String cipherJson(CryptoDataByte c, boolean withKdf) {
        Kdf kdf = c.getKdf();
        c.setData(null);
        c.setSymkey(null);
        c.setPassword(null);
        c.setPrikeyA(null);
        c.setPrikeyB(null);
        if (!withKdf) c.setKdf(null);
        String json = c.toJson();
        c.setKdf(kdf);
        return json;
    }

    static JsonObject jsonEntry(String id, CryptoDataByte c, JsonObject secret, Kdf derivedWith, boolean withKdf) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("type", c.getType().name());
        o.addProperty("alg", c.getAlg().getDisplayName());
        if (derivedWith != null) {
            o.addProperty("derivedWith", derivedWith.getDisplayName());
            o.addProperty("kdfRecorded", withKdf);
        }
        o.add("secret", secret);
        o.addProperty("plaintextHex", HF.formatHex(PLAINTEXT));
        o.addProperty("cipherJson", cipherJson(c, withKdf));
        return o;
    }

    static JsonObject bundleEntry(String id, byte[] bundle, CryptoDataByte source, Kdf recorded, Kdf derivedWith,
                                  JsonObject secret, boolean canonical) {
        if (bundle == null) throw new IllegalStateException(id + ": toBundle returned null");
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("expect", "decrypt");
        o.addProperty("canonical", canonical);
        o.addProperty("typeByte", bundle[6] & 0xff);
        o.addProperty("type", source.getType().name());
        o.addProperty("alg", source.getAlg().getDisplayName());
        if (recorded != null) o.addProperty("kdfRecorded", recorded.getDisplayName());
        if (derivedWith != null) o.addProperty("derivedWith", derivedWith.getDisplayName());
        o.add("secret", secret);
        o.addProperty("plaintextHex", HF.formatHex(PLAINTEXT));
        o.addProperty("bundleHex", HF.formatHex(bundle));
        o.addProperty("bundleBase64", Base64.getEncoder().encodeToString(bundle));
        return o;
    }

    static JsonObject rejectEntry(String id, String reason, byte[] bundle) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("expect", "reject");
        o.addProperty("reason", reason);
        o.addProperty("bundleHex", HF.formatHex(bundle));
        o.addProperty("bundleBase64", Base64.getEncoder().encodeToString(bundle));
        return o;
    }

    static JsonObject symkeySecret() {
        JsonObject o = new JsonObject();
        o.addProperty("symkey", HF.formatHex(SYMKEY));
        return o;
    }

    static JsonObject passwordSecret() {
        JsonObject o = new JsonObject();
        o.addProperty("password", PASSWORD);
        return o;
    }

    static JsonObject prikeySecret(byte[] prikey, byte[] peerPubkey) {
        JsonObject o = new JsonObject();
        o.addProperty("prikey", HF.formatHex(prikey));
        if (peerPubkey != null) o.addProperty("pubkey", HF.formatHex(peerPubkey));
        return o;
    }

    static JsonObject doc(String description, JsonArray vectors) {
        JsonObject o = new JsonObject();
        o.addProperty("description", description);
        o.addProperty("generatedBy", "FC-JDK core.crypto.CryptoVectorsGenerator");
        o.add("vectors", vectors);
        return o;
    }

    static void write(Path path, Gson gson, JsonObject content) throws Exception {
        Files.writeString(path, gson.toJson(content) + "\n");
    }
}
