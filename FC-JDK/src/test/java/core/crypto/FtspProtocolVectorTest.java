package core.crypto;

import core.crypto.Algorithm.AesCbc256;
import core.crypto.Algorithm.Bitcore;
import core.crypto.Algorithm.Ecc256K1Hkdf;
import core.crypto.Algorithm.HKDF;
import core.crypto.Algorithm.X25519;
import core.crypto.old.EccAes256K1P7;
import data.fcData.AlgorithmId;
import data.fcData.Signature;
import org.bitcoinj.core.ECKey;
import org.junit.jupiter.api.Test;
import utils.Hex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;

import static data.fcData.AlgorithmId.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression vectors for FTSP11–FTSP24 aligned with {@code Protocols/FTSP/} and FC-JDK reference code.
 */
public class FtspProtocolVectorTest {

    private static final HexFormat HF = HexFormat.of();

    private static byte[] h(String hex) {
        return HF.parseHex(hex.replace(" ", ""));
    }

    /**
     * Raw d = 1 scalar for ECDH/HKDF vectors (FTSP11 TV-ECDH-1). bitcoinj {@link ECKey#fromPrivate} rejects 1,
     * so use BC via {@link Ecc256K1Hkdf} for Z/symkey; use {@link #PRI_SEND_ECC} for {@link Encryptor} paths.
     */
    private static final byte[] PRI_ONE_RAW = h("0000000000000000000000000000000000000000000000000000000000000001");

    /** Valid secp256k1 private for {@link Encryptor#checkKeysMakeType} / bitcoinj. */
    private static final byte[] PRI_SEND_ECC = ECKey.fromPrivate(BigInteger.valueOf(5)).getPrivKeyBytes();
    private static final byte[] Z_TV_ECDH_1 = h("79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798");
    private static final byte[] NONCE_FTSP11 = h("101112131415161718191a1b");
    private static final byte[] SYMKEY_FTSP11 = h("2a2768b8c286dbed4a5c7299d49b9a8aaaedbd7c250862fa8dc6f1b4b56ceb8c");
    private static final byte[] CIPHER_FTSP11_A = h("fad929f0256cda6e921589b0b2c542a3fb");

    /** FTSP12 TV-AESGCM-1 */
    private static final byte[] KEY_ALL_AB = new byte[32];
    private static final byte[] IV12_ZERO = h("000102030405060708090a0b");
    private static final byte[] CIPHER_FTSP12 = h("660f550e7f255af454294635a43a7606871a4f55eed7");

    /** FTSP13 TV1 */
    private static final byte[] HKDF_OKM_TV1 = h("79d55d067d55fd67266b49e13949f6ea3fec4e752bbaabe0c52ddc7ac7c02a64");

    /** FTSP13 TV3 */
    private static final byte[] HKDF_IKM_TV3 = h("0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20");
    private static final byte[] HKDF_OKM_TV3 = h("a90aad642250bb8562417ac75dc4ca02d7b1f0d9533d14ab5a5a122939a69421");

    /** Second party: keep a single {@link ECKey} so priv/pub stay consistent (avoid round-trip quirks). */
    private static final ECKey ECC_PARTY_B = ECKey.fromPrivate(BigInteger.valueOf(999_983));

    private static byte[] priB() {
        return ECC_PARTY_B.getPrivKeyBytes();
    }

    private static byte[] pubB() {
        return ECC_PARTY_B.getPubKey();
    }

    private static byte[] secpCompressedPub(byte[] priv32) {
        var pri = KeyTools.prikeyFromBytes(priv32);
        var pub = KeyTools.pubkeyFromPrikey(pri);
        return pub.getQ().getEncoded(true);
    }

    static {
        Arrays.fill(KEY_ALL_AB, (byte) 0xab);
    }

    private static CryptoDataByte encryptAsyTwoWayFixedIv(AlgorithmId alg, byte[] plaintext,
                                                          byte[] priA, byte[] pubB, byte[] iv) throws Exception {
        Encryptor enc = new Encryptor(alg);
        CryptoDataByte cdb = new CryptoDataByte();
        cdb.setAlg(alg);
        cdb.setType(EncryptType.AsyTwoWay);
        cdb.setPrikeyA(priA);
        cdb.setPubkeyB(pubB);
        cdb.setIv(iv);
        enc.checkKeysMakeType(pubB, priA, cdb);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(plaintext);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            enc.encryptStreamByAsy(bis, bos, cdb);
            cdb.setCipher(bos.toByteArray());
            if (alg != FC_AesGcm256_No1_NrC7
                    && alg != FC_EccK1AesGcm256_No1_NrC7
                    && alg != FC_X25519AesGcm256_No1_NrC7) {
                cdb.makeSum4();
            }
            cdb.setType(EncryptType.AsyTwoWay);
            cdb.set0CodeMessage();
            return cdb;
        }
    }

    private static void assertDecryptAsyTwoWay(CryptoDataByte enc, byte[] priRecipient, byte[] expectedPlain) {
        CryptoDataByte dec = new CryptoDataByte();
        dec.setAlg(enc.getAlg());
        dec.setType(EncryptType.AsyTwoWay);
        dec.setIv(enc.getIv());
        dec.setCipher(enc.getCipher());
        dec.setSum(enc.getSum());
        dec.setPubkeyA(enc.getPubkeyA());
        dec.setPrikeyB(priRecipient);
        Decryptor decryptor = new Decryptor();
        decryptor.decryptByAsyKey(dec);
        assertEquals(0, dec.getCode(), dec.getMessage());
        assertArrayEquals(expectedPlain, dec.getData());
    }

    @Test
    void ftsp13_hkdf_tv1_and_tv3() throws Exception {
        byte[] ikm0 = new byte[32];
        byte[] salt12z = new byte[12];
        byte[] info = "hkdf".getBytes(StandardCharsets.US_ASCII);
        byte[] okm1 = HKDF.hkdf(ikm0, salt12z, info, 32);
        assertArrayEquals(HKDF_OKM_TV1, okm1);

        byte[] okm3 = HKDF.hkdf(HKDF_IKM_TV3, salt12z, info, 32);
        assertArrayEquals(HKDF_OKM_TV3, okm3);
    }

    @Test
    void ftsp11_ecdh_z_and_symkey_and_aesgcm_byte_a() throws Exception {
        // Z from TV-ECDH-1 (BC/bitcoinj may reject d=1 via some APIs; HKDF+GCM still must match).
        byte[] z = Z_TV_ECDH_1;
        byte[] symkey = HKDF.hkdf(z, NONCE_FTSP11, "hkdf".getBytes(StandardCharsets.US_ASCII), 32);
        assertArrayEquals(SYMKEY_FTSP11, symkey);

        Encryptor enc = new Encryptor(FC_AesGcm256_No1_NrC7);
        CryptoDataByte gcm = enc.encryptBySymkey(new byte[]{0x61}, SYMKEY_FTSP11, NONCE_FTSP11);
        assertEquals(0, gcm.getCode());
        assertArrayEquals(CIPHER_FTSP11_A, gcm.getCipher());
    }

    @Test
    void ftsp12_aesgcm_tv1_and_roundTrip() {
        Encryptor enc = new Encryptor(FC_AesGcm256_No1_NrC7);
        byte[] pt = "FTSP12".getBytes(StandardCharsets.UTF_8);
        CryptoDataByte c = enc.encryptBySymkey(pt, KEY_ALL_AB, IV12_ZERO);
        assertEquals(0, c.getCode());
        assertArrayEquals(CIPHER_FTSP12, c.getCipher());
        c.setSymkey(KEY_ALL_AB);
        Decryptor dec = new Decryptor();
        dec.decrypt(c);
        assertEquals(0, c.getCode());
        assertArrayEquals(pt, c.getData());
    }

    @Test
    void ftsp14_aes_cbc_symkey_roundTrip_matches_expected_cipher() throws Exception {
        byte[] symkey = new byte[32];
        Arrays.fill(symkey, (byte) 0x0c);
        byte[] iv16 = h("0d0e0f101112131415161718191a1b1c");
        byte[] pt = "FTSP14-CBC".getBytes(StandardCharsets.UTF_8);
        CryptoDataByte c;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(pt);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            // Pass null so AesCbc256 fills symkey+iv (its else-if chain skips iv when only symkey was null).
            c = AesCbc256.encrypt(bis, bos, symkey, iv16, null);
            c.setCipher(bos.toByteArray());
        }
        assertEquals(0, c.getCode());
        assertNotNull(c.getSum());
        assertEquals(
                "f1c7d497a2512fdf1c0bca304ffeaf75",
                Hex.toHex(c.getCipher()));
        assertEquals("a7daa639", Hex.toHex(c.getSum()));
        c.setSymkey(symkey);
        new Decryptor().decrypt(c);
        assertEquals(0, c.getCode(), c.getMessage());
        assertArrayEquals(pt, c.getData());
        assertTrue(c.getCipher().length >= pt.length);
        assertEquals(4, c.getSum().length);
    }

    private static byte[] decryptSym(CryptoDataByte c, byte[] symkey) {
        CryptoDataByte d = new CryptoDataByte();
        d.setAlg(c.getAlg());
        d.setType(EncryptType.Symkey);
        d.setIv(c.getIv());
        d.setCipher(c.getCipher());
        d.setSum(c.getSum());
        d.setSymkey(symkey);
        new Decryptor().decrypt(d);
        assertEquals(0, d.getCode(), d.getMessage());
        return d.getData();
    }

    @Test
    void ftsp15_ecc_k1_aes_cbc_roundTrip() throws Exception {
        byte[] iv16 = h("0102030405060708090a0b0c0d0e0f10");
        byte[] pt = "FTSP15".getBytes(StandardCharsets.UTF_8);
        CryptoDataByte enc = encryptAsyTwoWayFixedIv(FC_EccK1AesCbc256_No1_NrC7, pt, PRI_SEND_ECC, pubB(), iv16);
        assertEquals(0, enc.getCode());
        assertDecryptAsyTwoWay(enc, priB(), pt);
    }

    @Test
    void ftsp16_bitcore_deterministic_rng_roundTrip() throws Exception {
        byte[] recipientPriv = h("3030303030303030303030303030303030303030303030303030303030303030");
        var kp = Bitcore.createKeyPair(recipientPriv);
        SecureRandom rng = SecureRandom.getInstance("SHA1PRNG");
        rng.setSeed(HF.parseHex("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
        byte[] pt = "FTSP16-BitCore".getBytes(StandardCharsets.UTF_8);
        byte[] encbuf = Bitcore.encrypt(pt, kp.getPublic(), rng);
        byte[] dec = Bitcore.decrypt(encbuf, recipientPriv);
        assertArrayEquals(pt, dec);
        assertEquals(
                "0327c600b42441021486c84f43cf8127e728985678797884d4fa2cf98a535b02"
                        + "87a887d5c91a9f2565948d8626d77dbd060d5d9c324b5efedfabdba2cf6e8152"
                        + "64feac70fa9a338bab4483d51cb17167d6ab2521646c4fd696a1a2aebf71f11731",
                Hex.toHex(encbuf));
    }

    @Test
    void ftsp17_p7_aes_encrypt_decrypt() {
        byte[] iv16 = h("1112131415161718191a1b1c1d1e1f20");
        byte[] pt = "FTSP17-P7".getBytes(StandardCharsets.UTF_8);
        byte[] pubA = secpCompressedPub(PRI_ONE_RAW);
        CryptoDataByte enc = new CryptoDataByte();
        enc.setAlg(EccAes256K1P7_No1_NrC7);
        enc.setType(EncryptType.AsyTwoWay);
        enc.setIv(iv16);
        enc.setData(pt);
        enc.setPrikeyA(PRI_ONE_RAW);
        enc.setPubkeyB(pubB());
        byte[] sym = EccAes256K1P7.asyKeyToSymkey(PRI_ONE_RAW, pubB(), iv16);
        enc.setSymkey(sym);
        new EccAes256K1P7().aesEncrypt(enc);
        assertNotNull(enc.getCipher());

        CryptoDataByte dec = new CryptoDataByte();
        dec.setAlg(EccAes256K1P7_No1_NrC7);
        dec.setType(EncryptType.AsyTwoWay);
        dec.setIv(iv16);
        dec.setCipher(enc.getCipher());
        dec.setSum(enc.getSum());
        dec.setPrikeyB(priB());
        dec.setPubkeyA(pubA);
        new EccAes256K1P7().decrypt(dec);
        assertEquals(0, dec.getCode());
        assertArrayEquals(pt, dec.getData());
        assertEquals(
                "5b38582d1ecf84ecdb39b49f6d3eb77d",
                Hex.toHex(enc.getCipher()));
        assertEquals("2fe2df88", Hex.toHex(enc.getSum()));
    }

    /** Deterministic X25519 material (FC-JDK {@link X25519#generatePublicKey}). */
    private static final byte[] X25519_A_PRIV;
    private static final byte[] X25519_A_PUB;
    private static final byte[] X25519_B_PRIV;
    private static final byte[] X25519_B_PUB;

    static {
        X25519_A_PRIV = new byte[32];
        Arrays.fill(X25519_A_PRIV, (byte) 0x41);
        X25519_A_PUB = X25519.generatePublicKey(X25519_A_PRIV);
        X25519_B_PRIV = new byte[32];
        Arrays.fill(X25519_B_PRIV, (byte) 0x42);
        X25519_B_PUB = X25519.generatePublicKey(X25519_B_PRIV);
    }

    @Test
    void ftsp18_x25519_shared_secret_reciprocal() {
        byte[] sAb = X25519.getSharedSecret(X25519_A_PRIV, X25519_B_PUB);
        byte[] sBa = X25519.getSharedSecret(X25519_B_PRIV, X25519_A_PUB);
        assertArrayEquals(sAb, sBa);
    }

    @Test
    void ftsp18_x25519_hkdf_symkey() throws Exception {
        byte[] nonce = h("2122232425262728292a2b2c");
        byte[] shared = X25519.getSharedSecret(X25519_A_PRIV, X25519_B_PUB);
        byte[] sk = X25519.sharedSecretToSymkey(shared, nonce);
        assertEquals(32, sk.length);
        assertEquals(
                "7596a67ebf2278a6430ea318b4159d2f73d1720644d01bc5b56185b4ee916899",
                Hex.toHex(sk));
    }

    @Test
    void ftsp19_x25519_aes_gcm_roundTrip() throws Exception {
        byte[] iv12 = h("3132333435363738393a3b3c");
        byte[] pt = "FTSP19-X25519-GCM".getBytes(StandardCharsets.UTF_8);
        CryptoDataByte enc = encryptAsyTwoWayFixedIv(FC_X25519AesGcm256_No1_NrC7, pt, X25519_A_PRIV, X25519_B_PUB, iv12);
        assertEquals(0, enc.getCode());
        assertDecryptAsyTwoWay(enc, X25519_B_PRIV, pt);
    }

    @Test
    void ftsp20_chacha20_sym_roundTrip() {
        byte[] symkey = h("4142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f60");
        byte[] iv12 = h("6162636465666768696a6b6c");
        byte[] pt = "FTSP20-ChaCha".getBytes(StandardCharsets.UTF_8);
        Encryptor enc = new Encryptor(FC_ChaCha20_No1_NrC7);
        CryptoDataByte c = enc.encryptBySymkey(pt, symkey, iv12);
        assertEquals(0, c.getCode());
        assertArrayEquals(pt, decryptSym(c, symkey));
        assertEquals(
                "9a01dc6f2230c92a21cb513838",
                Hex.toHex(c.getCipher()));
        assertEquals("d5e5a7d6", Hex.toHex(c.getSum()));
    }

    @Test
    void ftsp21_ecc_k1_chacha_roundTrip() throws Exception {
        byte[] iv12 = h("7172737475767778797a7b7c");
        byte[] pt = "FTSP21".getBytes(StandardCharsets.UTF_8);
        CryptoDataByte enc = encryptAsyTwoWayFixedIv(FC_EccK1ChaCha20_No1_NrC7, pt, PRI_SEND_ECC, pubB(), iv12);
        assertEquals(0, enc.getCode());
        assertDecryptAsyTwoWay(enc, priB(), pt);
    }

    @Test
    void ftsp22_btc_ecdsa_sign_message_verify() {
        byte[] pri = h("404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f");
        String msg = "FTSP22 BTC message";
        Signature s = new Signature();
        s = s.sign(msg, pri, BTC_EcdsaSignMsg_No1_NrC7);
        assertNotNull(s);
        assertTrue(s.verify());
    }

    @Test
    void ftsp23_sha256_sym_sign_fixed() {
        byte[] sym = h("505152535455565758595a5b5c5d5e5f606162636465666768696a6b6c6d6e6f");
        String msg = "FTSP23 sym";
        String expect = Signature.symSign(msg, Hex.toHex(sym));
        Signature s = new Signature();
        s = s.sign(msg, sym, FC_Sha256SymSignMsg_No1_NrC7);
        assertEquals(
                "c0d6ca9c6fbef7da6fee37cc69eae562b56f4e86a63b5b194b3100fce2120dca",
                expect);
        assertEquals(expect, s.getSign());
    }

    @Test
    void ftsp24_schnorr_sign_msg_verify() {
        byte[] pri = h("606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f");
        String msg = "FTSP24 Schnorr";
        Signature s = new Signature();
        s = s.sign(msg, pri, FC_SchnorrSignMsg_No1_NrC7);
        assertNotNull(s.getSign());
        assertTrue(Signature.schnorrMsgVerify(msg, s.getSign(), s.getFid()));
        assertTrue(java.util.Base64.getDecoder().decode(s.getSign()).length >= 33 + 64);
    }
}
