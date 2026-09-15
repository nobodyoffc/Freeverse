package core.crypto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.crypto.Algorithm.Bitcore;
import core.crypto.Algorithm.X25519;
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
        write(dir.resolve("algorithms.json"), gson, algorithmVectors());
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

    /**
     * Every other FTSP cipher profile — the ChaCha20 variants, the legacy CBC and P7 ECC
     * profiles, X25519 and BitCore — plus tampered ciphers that readers must reject. A tamper
     * case is only emitted if FC-JDK itself rejects it; otherwise it is listed in knownGaps.
     */
    static JsonObject algorithmVectors() throws Exception {
        JsonArray v = new JsonArray();
        JsonArray gaps = new JsonArray();
        byte[] xPriA = new byte[32];
        byte[] xPriB = new byte[32];
        Arrays.fill(xPriA, (byte) 0x41);
        Arrays.fill(xPriB, (byte) 0x42);
        byte[] xPubA = X25519.generatePublicKey(xPriA);
        byte[] xPubB = X25519.generatePublicKey(xPriB);

        addForms(v, "SYMKEY-CHACHA20", symkeyCipher(FC_ChaCha20_No1_NrC7, IV12), symkeySecret(), true);
        addForms(v, "SYMKEY-CHACHA20POLY1305", symkeyCipher(FC_ChaCha20Poly1305_No1_NrC7, IV12), symkeySecret(), true);
        addForms(v, "ASYONEWAY-ECCK1AESCBC256", asyOneWay(FC_EccK1AesCbc256_No1_NrC7, PUB_FID_A), prikeySecret(PRI_FID_A, null), true);
        addForms(v, "ASYTWOWAY-ECCK1AESCBC256", asyTwoWay(FC_EccK1AesCbc256_No1_NrC7, PRI_FID_A, PUB_FID_B), prikeySecret(PRI_FID_B, PUB_FID_A), true);
        addForms(v, "ASYTWOWAY-ECCK1CHACHA20", asyTwoWay(FC_EccK1ChaCha20_No1_NrC7, PRI_FID_A, PUB_FID_B), prikeySecret(PRI_FID_B, PUB_FID_A), true);
        addForms(v, "ASYONEWAY-ECCK1CHACHA20POLY1305", asyOneWay(FC_EccK1ChaCha20Poly1305_No1_NrC7, PUB_FID_A), prikeySecret(PRI_FID_A, null), true);
        addForms(v, "ASYTWOWAY-ECCK1CHACHA20POLY1305", asyTwoWay(FC_EccK1ChaCha20Poly1305_No1_NrC7, PRI_FID_A, PUB_FID_B), prikeySecret(PRI_FID_B, PUB_FID_A), true);
        addForms(v, "ASYTWOWAY-X25519AESGCM256", asyTwoWay(FC_X25519AesGcm256_No1_NrC7, xPriA, xPubB), prikeySecret(xPriB, xPubA), true);
        // EccAes256K1P7 has no bundle prefix, so it only travels as JSON.
        addForms(v, "ASYTWOWAY-ECCAES256K1P7", asyTwoWay(EccAes256K1P7_No1_NrC7, PRI_FID_A, PUB_FID_B), prikeySecret(PRI_FID_B, PUB_FID_A), false);

        JsonObject bitcore = new JsonObject();
        bitcore.addProperty("id", "BITCORE-ENCBUF");
        bitcore.addProperty("expect", "decrypt");
        bitcore.addProperty("form", "bitcoreEncbuf");
        bitcore.addProperty("type", EncryptType.AsyOneWay.name());
        bitcore.addProperty("alg", BitCore_EccAes256.getDisplayName());
        bitcore.add("secret", prikeySecret(PRI_FID_A, null));
        bitcore.addProperty("plaintextHex", HF.formatHex(PLAINTEXT));
        bitcore.addProperty("encbufHex", HF.formatHex(Bitcore.encrypt(PLAINTEXT, Bitcore.createKeyPair(PRI_FID_A).getPublic())));
        v.add(bitcore);

        tamper(v, gaps, "TAMPER-SYMKEY-AESGCM256", symkeyCipher(FC_AesGcm256_No1_NrC7, IV12), symkeySecret());
        tamper(v, gaps, "TAMPER-SYMKEY-AESCBC256", symkeyCipher(FC_AesCbc256_No1_NrC7, IV16), symkeySecret());
        tamper(v, gaps, "TAMPER-SYMKEY-CHACHA20", symkeyCipher(FC_ChaCha20_No1_NrC7, IV12), symkeySecret());
        tamper(v, gaps, "TAMPER-SYMKEY-CHACHA20POLY1305", symkeyCipher(FC_ChaCha20Poly1305_No1_NrC7, IV12), symkeySecret());
        tamper(v, gaps, "TAMPER-ASYTWOWAY-ECCK1AESGCM256", asyTwoWay(FC_EccK1AesGcm256_No1_NrC7, PRI_FID_A, PUB_FID_B), prikeySecret(PRI_FID_B, PUB_FID_A));
        tamper(v, gaps, "TAMPER-ASYTWOWAY-ECCK1AESCBC256", asyTwoWay(FC_EccK1AesCbc256_No1_NrC7, PRI_FID_A, PUB_FID_B), prikeySecret(PRI_FID_B, PUB_FID_A));
        tamper(v, gaps, "TAMPER-ASYTWOWAY-ECCK1CHACHA20", asyTwoWay(FC_EccK1ChaCha20_No1_NrC7, PRI_FID_A, PUB_FID_B), prikeySecret(PRI_FID_B, PUB_FID_A));
        tamper(v, gaps, "TAMPER-ASYTWOWAY-ECCK1CHACHA20POLY1305", asyTwoWay(FC_EccK1ChaCha20Poly1305_No1_NrC7, PRI_FID_A, PUB_FID_B), prikeySecret(PRI_FID_B, PUB_FID_A));
        tamper(v, gaps, "TAMPER-ASYTWOWAY-X25519AESGCM256", asyTwoWay(FC_X25519AesGcm256_No1_NrC7, xPriA, xPubB), prikeySecret(xPriB, xPubA));

        JsonObject doc = doc("Other FTSP cipher profiles. form=json: decrypt cipherJson; form=bundle: parse bundleHex, check alg, decrypt; form=bitcoreEncbuf: Bitcore.decrypt(encbufHex, prikey). expect=decrypt: plaintext must equal plaintextHex. expect=reject-decrypt: a tampered cipher that must not decrypt successfully. knownGaps lists tamper cases FC-JDK itself does not yet detect.", v);
        doc.add("knownGaps", gaps);
        return doc;
    }

    static CryptoDataByte asyOneWay(AlgorithmId alg, byte[] pubB) {
        CryptoDataByte c = new Encryptor(alg).encryptByAsyOneWay(PLAINTEXT.clone(), pubB.clone());
        return requireOk(c);
    }

    static CryptoDataByte asyTwoWay(AlgorithmId alg, byte[] priA, byte[] pubB) {
        CryptoDataByte c = new Encryptor(alg).encryptByAsyTwoWay(PLAINTEXT.clone(), priA.clone(), pubB.clone());
        return requireOk(c);
    }

    static void addForms(JsonArray v, String id, CryptoDataByte c, JsonObject secret, boolean bundleable) {
        if (c.getAlg() == null || c.getType() == null) throw new IllegalStateException(id + ": encryptor left alg or type unset");
        byte[] bundle = bundleable ? c.toBundle() : null;
        if (bundleable && bundle == null) throw new IllegalStateException(id + ": toBundle returned null");
        JsonObject json = formEntry(id + "-JSON", c, "json", "decrypt", secret);
        json.addProperty("cipherJson", cipherJson(c, false));
        v.add(json);
        if (bundle != null) {
            JsonObject b = formEntry(id + "-BUNDLE", c, "bundle", "decrypt", secret);
            b.addProperty("bundleHex", HF.formatHex(bundle));
            v.add(b);
        }
    }

    /** Flips the last cipher byte — inside the tag for AEAD, the final block otherwise. */
    static void tamper(JsonArray v, JsonArray gaps, String id, CryptoDataByte c, JsonObject secret) {
        byte[] bundle = c.toBundle();
        if (bundle == null) throw new IllegalStateException(id + ": toBundle returned null");
        int sumLength = c.getAlg().isAead() ? 0 : CryptoConstants.SUM_LENGTH;
        bundle[bundle.length - 1 - sumLength] ^= 0x01;
        CryptoDataByte result;
        try {
            result = decryptBundle(bundle, c.getType(), secret);
        } catch (Exception e) {
            result = null;
        }
        boolean accepted = result != null && Integer.valueOf(0).equals(result.getCode());
        if (accepted) {
            if (c.getAlg().isAead()) throw new IllegalStateException(id + ": FC-JDK accepted a tampered AEAD cipher");
            gaps.add(id + ": FC-JDK does not detect this tampering, so it is not a vector yet");
            return;
        }
        JsonObject o = formEntry(id, c, "bundle", "reject-decrypt", secret);
        o.addProperty("bundleHex", HF.formatHex(bundle));
        v.add(o);
    }

    static CryptoDataByte decryptBundle(byte[] bundle, EncryptType type, JsonObject secret) {
        Decryptor d = new Decryptor();
        byte[] prikey = secret.has("prikey") ? HF.parseHex(secret.get("prikey").getAsString()) : null;
        return switch (type) {
            case Symkey -> d.decryptBundleBySymkey(bundle, HF.parseHex(secret.get("symkey").getAsString()));
            case AsyOneWay -> d.decryptBundleByAsyOneWay(bundle, prikey);
            case AsyTwoWay -> d.decryptBundleByAsyTwoWay(bundle, prikey, HF.parseHex(secret.get("pubkey").getAsString()));
            case Password -> d.decryptBundleByPassword(bundle, secret.get("password").getAsString().toCharArray());
        };
    }

    static JsonObject formEntry(String id, CryptoDataByte c, String form, String expect, JsonObject secret) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("expect", expect);
        o.addProperty("form", form);
        o.addProperty("type", c.getType().name());
        o.addProperty("alg", c.getAlg().getDisplayName());
        o.add("secret", secret);
        o.addProperty("plaintextHex", HF.formatHex(PLAINTEXT));
        return o;
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
