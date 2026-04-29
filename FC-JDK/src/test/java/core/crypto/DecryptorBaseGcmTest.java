package core.crypto;

import constants.CodeMessage;
import data.fcData.AlgorithmId;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DecryptorBaseGcmTest {

    @Test
    public void decryptBySymkeyBase_rejectsGcmTransformation() throws Exception {
        byte[] key = new byte[32];
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(key);
        new SecureRandom().nextBytes(iv);

        CryptoDataByte ctx = new CryptoDataByte();
        ctx.setSymkey(key);
        ctx.setIv(iv);
        ctx.setAlg(AlgorithmId.FC_AesGcm256_No1_NrC7);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Decryptor.decryptBySymkeyBase("AES", "AES/GCM/NoPadding", "BC",
                new ByteArrayInputStream(new byte[16]), out, ctx);

        assertEquals(CodeMessage.Code4002NoSuchAlgorithm, ctx.getCode(),
                "AEAD transformations must be refused on this path");
        assertEquals(0, out.size(), "nothing should be written on refusal");
    }

    @Test
    public void decryptBySymkeyBase_cbcRoundTrip() throws Exception {
        byte[] key = new byte[32];
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(key);
        new SecureRandom().nextBytes(iv);

        byte[] plaintext = "CBC branch regression check".getBytes();

        CryptoDataByte encCtx = new CryptoDataByte();
        encCtx.setSymkey(key);
        encCtx.setIv(iv);
        encCtx.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7);

        ByteArrayOutputStream cipherOut = new ByteArrayOutputStream();
        Encryptor.encryptBySymkeyBase("AES", "AES/CBC/PKCS7Padding", "BC",
                new ByteArrayInputStream(plaintext), cipherOut, encCtx);
        assertEquals(0, encCtx.getCode());

        CryptoDataByte decCtx = new CryptoDataByte();
        decCtx.setSymkey(key);
        decCtx.setIv(iv);
        decCtx.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7);

        ByteArrayOutputStream plainOut = new ByteArrayOutputStream();
        Decryptor.decryptBySymkeyBase("AES", "AES/CBC/PKCS7Padding", "BC",
                new ByteArrayInputStream(cipherOut.toByteArray()), plainOut, decCtx);

        assertEquals(0, decCtx.getCode());
        assertArrayEquals(plaintext, plainOut.toByteArray());
    }
}
