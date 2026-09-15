package core.crypto;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import core.crypto.Algorithm.Bitcore;
import data.fcData.AlgorithmId;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Security;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks FC-JDK against the shared vectors in {@code Protocols/FTSP/vectors/}, which every
 * implementation must pass. Regenerate them with {@link CryptoVectorsGenerator}. A few values
 * are also pinned straight from the FTSP documents so the files cannot drift from the specs.
 */
public class CryptoVectorsTest {

    private static final HexFormat HF = HexFormat.of();
    private static Path dir;

    @BeforeAll
    static void locateVectors() {
        Security.addProvider(new BouncyCastleProvider());
        for (String candidate : new String[]{"../Protocols/FTSP/vectors", "Protocols/FTSP/vectors"}) {
            Path p = Paths.get(candidate);
            if (Files.isDirectory(p)) {
                dir = p;
                return;
            }
        }
        fail("Protocols/FTSP/vectors not found from " + Paths.get("").toAbsolutePath());
    }

    @Test
    void kdf() throws Exception {
        for (JsonElement e : vectors("kdf.json")) {
            JsonObject v = e.getAsJsonObject();
            String id = str(v, "id");
            Kdf kdf = Kdf.fromDisplayName(str(v, "kdf"));
            assertEquals(kdf, Kdf.fromId(hex(v, "kdfId")[0]), id);
            assertEquals(str(v, "symkey"), HF.formatHex(kdf.deriveSymkey(str(v, "password").toCharArray(), hex(v, "salt"))), id);
        }
        // FTSP25 TV-PW2SK-1 and TV-PW2SK-2.
        assertEquals("18f6a17f6fe849af1a43a7c006c2315006d5e644daf69f8b0555ad57defce54f",
                HF.formatHex(Kdf.Sha256Iv_No1_NrC7.deriveSymkey("MyPassword".toCharArray(), HF.parseHex("000102030405060708090a0b"))));
        assertEquals("442c5118b7f5ae123a6c07ff37e5793fb511496a0677a0a7ad6806e030906630",
                HF.formatHex(Kdf.Sha256Iv_No1_NrC7.deriveSymkey("MyPassword".toCharArray(), HF.parseHex("000102030405060708090a0b0c0d0e0f"))));
    }

    @Test
    void phrase() throws Exception {
        for (JsonElement e : vectors("phrase.json")) {
            JsonObject v = e.getAsJsonObject();
            String id = str(v, "id");
            byte[] utf8 = hex(v, "phraseUtf8Hex");
            assertArrayEquals(str(v, "phrase").getBytes(StandardCharsets.UTF_8), utf8, id);
            if ("sha256".equals(str(v, "scheme"))) {
                assertEquals(str(v, "priKey32"), HF.formatHex(Hash.sha256(utf8)), id);
                continue;
            }
            byte[] key = Kdf.Argon2id_No1_NrC7.deriveSymkey(str(v, "phrase").toCharArray(), hex(v, "salt"));
            assertEquals(str(v, "priKey32"), HF.formatHex(key), id);
            // FTSP28 step 1: the char[] path must hash exactly like the UTF-8 bytes, non-ASCII included.
            assertArrayEquals(key, argon2idFromBytes(utf8, hex(v, "salt")), id);
        }
        // FTSP28 TV1, TV2, TV3 and TV-LEGACY.
        assertEquals("3107f02758ff375bfed40885d7e7a24239e4a3bf55caa9cbea7ffeddfd7ddbf6",
                HF.formatHex(Kdf.Argon2id_No1_NrC7.deriveSymkey("Hello world!".toCharArray(), new byte[0])));
        assertEquals("338c985a49e05a31cd2eb80a149dcea46df6ae7a487fc86a53a65da1fa8ec201",
                HF.formatHex(Kdf.Argon2id_No1_NrC7.deriveSymkey("correct horse battery staple".toCharArray(), new byte[0])));
        assertEquals("70d94592a1242af620ec77f7fe9a7b2df5e39e28059d50d2e30f9f119e165cad",
                HF.formatHex(Kdf.Argon2id_No1_NrC7.deriveSymkey("你好，世界".toCharArray(), new byte[0])));
        assertEquals("afa4d6d948331f7152dbc602a3e5b1900126225a0189bde2cbf1f0c5a2fd49b9",
                HF.formatHex(Kdf.Argon2id_No1_NrC7.deriveSymkey("correct horse battery staple".toCharArray(),
                        HF.parseHex("c4bbcb1fbec99d65bf59d85c8cb62ee2"))));
    }

    @Test
    void cipherJson() throws Exception {
        Decryptor d = new Decryptor();
        for (JsonElement e : vectors("cipher-json.json")) {
            JsonObject v = e.getAsJsonObject();
            String json = str(v, "cipherJson");
            JsonObject secret = v.getAsJsonObject("secret");
            CryptoDataByte result = switch (EncryptType.valueOf(str(v, "type"))) {
                case Symkey -> d.decryptJsonBySymkey(json, hex(secret, "symkey"));
                case Password -> d.decryptJsonByPassword(json, str(secret, "password").toCharArray());
                case AsyOneWay -> d.decryptJsonByAsyOneWay(json, hex(secret, "prikey"));
                case AsyTwoWay -> d.decryptJsonByAsyTwoWay(json, hex(secret, "prikey"), hex(secret, "pubkey"));
            };
            assertDecrypted(v, result);
        }
    }

    @Test
    void bundle() throws Exception {
        Decryptor d = new Decryptor();
        for (JsonElement e : vectors("bundle.json")) {
            JsonObject v = e.getAsJsonObject();
            String id = str(v, "id");
            byte[] bundle = hex(v, "bundleHex");
            assertArrayEquals(bundle, Base64.getDecoder().decode(str(v, "bundleBase64")), id);
            CryptoDataByte parsed = CryptoDataByte.fromBundle(bundle);
            if ("reject".equals(str(v, "expect"))) {
                assertNull(parsed, id + " must be rejected");
                continue;
            }
            assertNotNull(parsed, id);
            assertEquals(AlgorithmId.fromDisplayName(str(v, "alg")), parsed.getAlg(), id);
            assertEquals(EncryptType.valueOf(str(v, "type")), parsed.getType(), id);
            Kdf recorded = str(v, "kdfRecorded") == null ? null : Kdf.fromDisplayName(str(v, "kdfRecorded"));
            assertEquals(recorded, parsed.getKdf(), id);
            if (v.get("typeByte").getAsInt() == CryptoConstants.BUNDLE_TYPE_PASSWORD_WITH_KDF) {
                // A reader that predates type 4 has no EncryptType for it, so it refuses the bundle.
                assertNull(EncryptType.fromNumber(CryptoConstants.BUNDLE_TYPE_PASSWORD_WITH_KDF), id);
            }
            if (v.get("canonical").getAsBoolean()) {
                assertArrayEquals(bundle, parsed.toBundle(), id + ": toBundle does not reproduce the bundle");
            }
            JsonObject secret = v.getAsJsonObject("secret");
            CryptoDataByte result = switch (parsed.getType()) {
                case Symkey -> d.decryptBundleBySymkey(bundle, hex(secret, "symkey"));
                case Password -> d.decryptBundleByPassword(bundle, str(secret, "password").toCharArray());
                case AsyOneWay -> d.decryptBundleByAsyOneWay(bundle, hex(secret, "prikey"));
                case AsyTwoWay -> d.decryptBundleByAsyTwoWay(bundle, hex(secret, "prikey"), hex(secret, "pubkey"));
            };
            assertDecrypted(v, result);
        }
    }

    @Test
    void wrongPasswordFails() throws Exception {
        for (JsonElement e : vectors("bundle.json")) {
            JsonObject v = e.getAsJsonObject();
            if (!"BUNDLE-T3-PASSWORD-GCM-ARGON2ID".equals(str(v, "id"))) continue;
            CryptoDataByte result = new Decryptor().decryptBundleByPassword(hex(v, "bundleHex"), "not the password".toCharArray());
            assertNotEquals(Integer.valueOf(0), result.getCode());
            assertNull(result.getData());
            assertNull(result.getKdf(), "a failed trial must not report a KDF");
            return;
        }
        fail("BUNDLE-T3-PASSWORD-GCM-ARGON2ID missing from bundle.json");
    }

    @Test
    void algorithms() throws Exception {
        for (JsonElement e : vectors("algorithms.json")) {
            JsonObject v = e.getAsJsonObject();
            String id = str(v, "id");
            boolean reject = "reject-decrypt".equals(str(v, "expect"));
            CryptoDataByte result;
            try {
                result = decryptAlgorithmVector(v);
            } catch (Exception ex) {
                if (reject) continue;
                throw new AssertionError(id + " threw", ex);
            }
            if (reject) {
                assertFalse(result != null && Integer.valueOf(0).equals(result.getCode()), id + ": tampered cipher reported success");
            } else {
                assertDecrypted(v, result);
            }
        }
    }

    @Test
    void vault() throws Exception {
        for (JsonElement e : vectors("vault.json")) {
            JsonObject v = e.getAsJsonObject();
            String id = str(v, "id");
            byte[] dek = VaultKey.unwrap(str(v, "dekCipher"), str(v, "password").toCharArray());
            if ("reject".equals(str(v, "expect"))) {
                assertNull(dek, id + " must be refused");
            } else {
                assertEquals(str(v, "dekHex"), dek == null ? null : HF.formatHex(dek), id);
                assertNull(VaultKey.unwrap(str(v, "dekCipher"), "not the password".toCharArray()), id + ": a wrong password must not unwrap");
            }
        }
    }

    private static CryptoDataByte decryptAlgorithmVector(JsonObject v) throws Exception {
        String id = str(v, "id");
        JsonObject secret = v.getAsJsonObject("secret");
        byte[] prikey = secret.has("prikey") ? hex(secret, "prikey") : null;
        Decryptor d = new Decryptor();
        switch (str(v, "form")) {
            case "json": {
                String json = str(v, "cipherJson");
                return switch (EncryptType.valueOf(str(v, "type"))) {
                    case Symkey -> d.decryptJsonBySymkey(json, hex(secret, "symkey"));
                    case Password -> d.decryptJsonByPassword(json, str(secret, "password").toCharArray());
                    case AsyOneWay -> d.decryptJsonByAsyOneWay(json, prikey);
                    case AsyTwoWay -> d.decryptJsonByAsyTwoWay(json, prikey, hex(secret, "pubkey"));
                };
            }
            case "bundle": {
                byte[] bundle = hex(v, "bundleHex");
                CryptoDataByte parsed = CryptoDataByte.fromBundle(bundle);
                assertNotNull(parsed, id);
                assertEquals(AlgorithmId.fromDisplayName(str(v, "alg")), parsed.getAlg(), id);
                return switch (parsed.getType()) {
                    case Symkey -> d.decryptBundleBySymkey(bundle, hex(secret, "symkey"));
                    case Password -> d.decryptBundleByPassword(bundle, str(secret, "password").toCharArray());
                    case AsyOneWay -> d.decryptBundleByAsyOneWay(bundle, prikey);
                    case AsyTwoWay -> d.decryptBundleByAsyTwoWay(bundle, prikey, hex(secret, "pubkey"));
                };
            }
            case "bitcoreEncbuf": {
                CryptoDataByte r = new CryptoDataByte();
                r.setData(Bitcore.decrypt(hex(v, "encbufHex"), prikey));
                r.set0CodeMessage();
                return r;
            }
            default:
                throw new AssertionError("unknown form in " + id);
        }
    }

    private static void assertDecrypted(JsonObject v, CryptoDataByte result) {
        String id = str(v, "id");
        assertNotNull(result, id);
        assertEquals(Integer.valueOf(0), result.getCode(), id + ": " + result.getMessage());
        assertEquals(str(v, "plaintextHex"), HF.formatHex(result.getData()), id);
        if (str(v, "derivedWith") != null) {
            assertEquals(Kdf.fromDisplayName(str(v, "derivedWith")), result.getKdf(), id + ": reported the wrong KDF");
        }
    }

    private static byte[] argon2idFromBytes(byte[] password, byte[] salt) {
        Argon2BytesGenerator gen = new Argon2BytesGenerator();
        gen.init(new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(Kdf.ARGON2ID_ITERATIONS)
                .withMemoryAsKB(Kdf.ARGON2ID_MEMORY_KIB)
                .withParallelism(Kdf.ARGON2ID_PARALLELISM)
                .withSalt(salt)
                .build());
        byte[] out = new byte[Kdf.DERIVED_KEY_LEN];
        gen.generateBytes(password, out);
        return out;
    }

    private static JsonArray vectors(String file) throws Exception {
        return JsonParser.parseString(Files.readString(dir.resolve(file))).getAsJsonObject().getAsJsonArray("vectors");
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static byte[] hex(JsonObject o, String key) {
        return HF.parseHex(str(o, key));
    }
}
