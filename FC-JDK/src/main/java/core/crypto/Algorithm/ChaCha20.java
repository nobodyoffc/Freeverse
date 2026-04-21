package core.crypto.Algorithm;

import com.google.common.hash.Hashing;
import constants.CodeMessage;
import core.crypto.CryptoConstants;
import core.crypto.CryptoDataByte;
import core.crypto.Decryptor;
import core.crypto.EncryptType;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Security;

/**
 * ChaCha20 stream cipher implementation using BouncyCastle provider.
 * ChaCha20 is a stream cipher designed by Daniel J. Bernstein.
 *
 * Key features:
 * - 256-bit key size
 * - 96-bit (12-byte) nonce/IV
 * - High performance on both software and hardware
 * - Constant-time execution resistant to timing attacks
 */
public class ChaCha20 {

    private static final String ALGORITHM = "ChaCha20";
    private static final String TRANSFORMATION = "ChaCha20";
    private static final String PROVIDER = "BC";

    static {
        // Ensure BouncyCastle provider is registered
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Encrypt data using ChaCha20 with provided key and IV.
     *
     * @param inputStream Input stream containing plaintext data
     * @param outputStream Output stream for ciphertext
     * @param key 32-byte symmetric key
     * @param iv 12-byte nonce/IV
     * @param cryptoDataByte CryptoDataByte object to store metadata
     * @return Updated CryptoDataByte with encryption results
     */
    public static CryptoDataByte encrypt(InputStream inputStream, OutputStream outputStream,
                                         byte[] key, byte[] iv, CryptoDataByte cryptoDataByte) {
        if (cryptoDataByte == null) {
            cryptoDataByte = new CryptoDataByte();
        }

        // Validate key length (must be 32 bytes for ChaCha20-256)
        if (key == null || key.length != CryptoConstants.KEY_LENGTH_256) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4008WrongKeyLength,
                "ChaCha20 requires a 32-byte key");
            return cryptoDataByte;
        }

        // Validate IV length (must be 12 bytes for ChaCha20)
        if (iv == null || iv.length != CryptoConstants.IV_LENGTH_CHACHA20) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4009MissingIv,
                "ChaCha20 requires a 12-byte nonce");
            return cryptoDataByte;
        }

        cryptoDataByte.setSymkey(key);
        cryptoDataByte.setIv(iv);

        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION, PROVIDER);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

            var hashFunction = Hashing.sha256();
            var hasherIn = hashFunction.newHasher();

            byte[] buffer = new byte[4096];
            int bytesRead;

            try (CipherOutputStream cos = new CipherOutputStream(outputStream, cipher)) {
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    hasherIn.putBytes(buffer, 0, bytesRead);
                    cos.write(buffer, 0, bytesRead);
                }
            }

            // Calculate data ID from input (plaintext) hash
            byte[] did = Decryptor.sha256(hasherIn.hash().asBytes());

            cryptoDataByte.setDid(did);

            // Set algorithm if not already set
            if(cryptoDataByte.getAlg() == null) {
                cryptoDataByte.setAlg(data.fcData.AlgorithmId.FC_ChaCha20_No1_NrC7);
            }

            // Set type if not already set
            if(cryptoDataByte.getType() == null) {
                cryptoDataByte.setType(EncryptType.Symkey);
            }

            // ChaCha20 is a stream cipher without built-in authentication,
            // so we need to generate a sum for integrity verification
            cryptoDataByte.makeSum4();

            cryptoDataByte.set0CodeMessage();
            return cryptoDataByte;

        } catch (Exception e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4001FailedToEncrypt,
                "ChaCha20 encryption failed: " + e.getMessage());
            return cryptoDataByte;
        }
    }

    /**
     * Encrypt byte array using ChaCha20.
     */
    public static CryptoDataByte encrypt(ByteArrayInputStream bisMsg, ByteArrayOutputStream bosCipher,
                                         byte[] key, byte[] iv, CryptoDataByte cryptoDataByte) {
        return encrypt((InputStream) bisMsg, (OutputStream) bosCipher, key, iv, cryptoDataByte);
    }

    /**
     * Decrypt data using ChaCha20 with provided key and IV.
     *
     * @param cryptoDataByte CryptoDataByte containing cipher and decryption parameters
     * @return Updated CryptoDataByte with decryption results
     */
    public static CryptoDataByte decrypt(CryptoDataByte cryptoDataByte) {
        byte[] cipher = cryptoDataByte.getCipher();
        if (cipher == null) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4013BadCipher, "Cipher is null");
            return cryptoDataByte;
        }

        try (ByteArrayInputStream bis = new ByteArrayInputStream(cipher);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            decryptStream(bis, bos, cryptoDataByte);

            if (cryptoDataByte.getCode() == 0) {
                cryptoDataByte.setData(bos.toByteArray());
                cryptoDataByte.makeDid();

                // Verify integrity using sum
                if (!cryptoDataByte.checkSum()) {
                    cryptoDataByte.setCodeMessage(CodeMessage.Code4011BadSum);
                }
            }

            return cryptoDataByte;
        } catch (Exception e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code1029FailedToDecrypt,
                "ChaCha20 decryption failed: " + e.getMessage());
            return cryptoDataByte;
        }
    }

    /**
     * Decrypt stream using ChaCha20.
     *
     * @param inputStream Input stream containing ciphertext
     * @param outputStream Output stream for plaintext
     * @param cryptoDataByte CryptoDataByte containing decryption parameters
     */
    public static void decryptStream(InputStream inputStream, OutputStream outputStream,
                                     CryptoDataByte cryptoDataByte) {
        byte[] key = cryptoDataByte.getSymkey();
        byte[] iv = cryptoDataByte.getIv();

        // Validate key
        if (key == null || key.length != CryptoConstants.KEY_LENGTH_256) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4008WrongKeyLength,
                "ChaCha20 requires a 32-byte key");
            return;
        }

        // Validate IV
        if (iv == null || iv.length != CryptoConstants.IV_LENGTH_CHACHA20) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4009MissingIv,
                "ChaCha20 requires a 12-byte nonce");
            return;
        }

        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION, PROVIDER);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            var hashFunction = Hashing.sha256();
            var hasherIn = hashFunction.newHasher();

            try (CipherOutputStream cos = new CipherOutputStream(outputStream, cipher)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    hasherIn.putBytes(buffer, 0, bytesRead);
                    cos.write(buffer, 0, bytesRead);
                }
            }

            byte[] cipherId = Decryptor.sha256(hasherIn.hash().asBytes());
            cryptoDataByte.setCipherId(cipherId);
            cryptoDataByte.set0CodeMessage();

        } catch (Exception e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code1029FailedToDecrypt,
                "ChaCha20 decryption failed: " + e.getMessage());
        }
    }
}
