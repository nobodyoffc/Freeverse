package core.crypto.Algorithm;

import core.crypto.KeyTools;
import core.crypto.CryptoDataByte;
import core.crypto.EncryptType;
import data.fcData.AlgorithmId;

import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.*;
import org.bouncycastle.jcajce.provider.asymmetric.util.ECUtil;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.*;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.encoders.Hex;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;

public class Bitcore {
    private static final boolean SHORT_TAG = false;
    private static final boolean NO_KEY = false;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static byte[] encrypt(byte[] message, PublicKey recipientPublicKey) throws Exception {
        // Generate ephemeral key pair
        ECNamedCurveParameterSpec params = ECNamedCurveTable.getParameterSpec("secp256k1");
        ECDomainParameters domainParams = new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
        ECKeyPairGenerator keyPairGenerator = new ECKeyPairGenerator();
        keyPairGenerator.init(new ECKeyGenerationParameters(domainParams, new SecureRandom()));
        AsymmetricCipherKeyPair ephemeralKeyPair = keyPairGenerator.generateKeyPair();

        ECPrivateKeyParameters ephemeralPrivateKey = (ECPrivateKeyParameters) ephemeralKeyPair.getPrivate();
        ECPublicKeyParameters ephemeralPublicKey = (ECPublicKeyParameters) ephemeralKeyPair.getPublic();

        // Get recipient's public key
        ECPublicKeyParameters recipientPublicKeyParams = (ECPublicKeyParameters) ECUtil.generatePublicKeyParameter(recipientPublicKey);

        // Generate shared secret
        byte[] sharedSecret = generateSharedSecret(ephemeralPrivateKey, recipientPublicKeyParams);
        byte[] kEkM = sha512(sharedSecret);
        byte[] kE = Arrays.copyOfRange(kEkM, 0, 32);
        byte[] kM = Arrays.copyOfRange(kEkM, 32, 64);

        // Generate IV
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);

        // Encrypt message
        byte[] c = encryptAESCBC(message, kE, iv);

        // Calculate HMAC
        byte[] ciphertext = new byte[iv.length + c.length];
        System.arraycopy(iv, 0, ciphertext, 0, iv.length);
        System.arraycopy(c, 0, ciphertext, iv.length, c.length);
        byte[] d = hmacSha256(ciphertext, kM);
        if (SHORT_TAG) {
            d = Arrays.copyOfRange(d, 0, 4);
        }

        // Combine all parts
        byte[] encbuf;
        if (NO_KEY) {
            encbuf = new byte[ciphertext.length + d.length];
            System.arraycopy(ciphertext, 0, encbuf, 0, ciphertext.length);
            System.arraycopy(d, 0, encbuf, ciphertext.length, d.length);
        } else {
            byte[] ephemeralPublicKeyBytes = ephemeralPublicKey.getQ().getEncoded(true);
            encbuf = new byte[ephemeralPublicKeyBytes.length + ciphertext.length + d.length];
            System.arraycopy(ephemeralPublicKeyBytes, 0, encbuf, 0, ephemeralPublicKeyBytes.length);
            System.arraycopy(ciphertext, 0, encbuf, ephemeralPublicKeyBytes.length, ciphertext.length);
            System.arraycopy(d, 0, encbuf, ephemeralPublicKeyBytes.length + ciphertext.length, d.length);
        }

        return encbuf;
    }


    private static byte[] generateSharedSecret(ECPrivateKeyParameters privateKey, ECPublicKeyParameters publicKey) {
        ECDHBasicAgreement agreement = new ECDHBasicAgreement();
        agreement.init(privateKey);
        BigInteger sharedSecret = agreement.calculateAgreement(publicKey);
        return bigIntegerToBytes(sharedSecret, 32);
    }

    private static byte[] bigIntegerToBytes(BigInteger b, int numBytes) {
        byte[] bytes = new byte[numBytes];
        byte[] biBytes = b.toByteArray();
        int start = (biBytes.length == numBytes + 1) ? 1 : 0;
        int length = Math.min(biBytes.length, numBytes);
        System.arraycopy(biBytes, start, bytes, numBytes - length, length);
        return bytes;
    }

    private static byte[] sha512(byte[] input) {
        SHA512Digest digest = new SHA512Digest();
        byte[] output = new byte[digest.getDigestSize()];
        digest.update(input, 0, input.length);
        digest.doFinal(output, 0);
        return output;
    }

    private static byte[] hmacSha256(byte[] data, byte[] key) {
        HMac hmac = new HMac(new SHA256Digest());
        hmac.init(new KeyParameter(key));
        byte[] output = new byte[hmac.getMacSize()];
        hmac.update(data, 0, data.length);
        hmac.doFinal(output, 0);
        return output;
    }

    public static byte[] decrypt(byte[] encbuf, byte[] priKeyBytes) throws Exception {
        PrivateKey privateKey = createPrivateKey(priKeyBytes);
        ECPrivateKeyParameters privKeyParams = (ECPrivateKeyParameters) ECUtil.generatePrivateKeyParameter(privateKey);

        int offset = 0;
        int tagLength = SHORT_TAG ? 4 : 32;

        ECPublicKeyParameters publicKey;
        if (!NO_KEY) {
            byte[] pubKeyBytes;
            switch (encbuf[0]) {
                case 4 -> {
                    pubKeyBytes = Arrays.copyOfRange(encbuf, 0, 65);
                    offset = 65;
                }
                case 3, 2 -> {
                    pubKeyBytes = Arrays.copyOfRange(encbuf, 0, 33);
                    offset = 33;
                }
                default -> throw new Error("Invalid public key type: " + encbuf[0]);
            }

            ECNamedCurveParameterSpec params = ECNamedCurveTable.getParameterSpec("secp256k1");
            ECDomainParameters domainParams = new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
            ECPoint point = params.getCurve().decodePoint(pubKeyBytes);
            publicKey = new ECPublicKeyParameters(point, domainParams);
        } else {
            throw new Error("NO_KEY option is not supported in this implementation");
        }

        byte[] ciphertext = Arrays.copyOfRange(encbuf, offset, encbuf.length - tagLength);
        byte[] d = Arrays.copyOfRange(encbuf, encbuf.length - tagLength, encbuf.length);

        byte[] sharedSecret = generateSharedSecret(privKeyParams, publicKey);
        byte[] kEkM = sha512(sharedSecret);
        byte[] kE = Arrays.copyOfRange(kEkM, 0, 32);
        byte[] kM = Arrays.copyOfRange(kEkM, 32, 64);

        byte[] d2 = hmacSha256(ciphertext, kM);
        if (SHORT_TAG) {
            d2 = Arrays.copyOfRange(d2, 0, 4);
        }

        if (!constantTimeEquals(d, d2)) {
            return null;
        }

        byte[] iv = Arrays.copyOfRange(ciphertext, 0, 16);
        byte[] c = Arrays.copyOfRange(ciphertext, 16, ciphertext.length);

        return decryptAESCBC(c, kE, iv);
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
    // ... (keep other methods like generateSharedSecret, sha512, hmacSha256, etc.)

    private static byte[] encryptAESCBC(byte[] plaintext, byte[] key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "BC");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec);
        return cipher.doFinal(plaintext);
    }

    private static byte[] decryptAESCBC(byte[] ciphertext, byte[] key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "BC");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec);
        return cipher.doFinal(ciphertext);
    }

    public static byte[] passwordToKey(String password) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(
                password.getBytes(StandardCharsets.UTF_8));
    }

    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC", "BC");
        ECNamedCurveGenParameterSpec ecSpec = new ECNamedCurveGenParameterSpec("secp256k1");
        keyGen.initialize(ecSpec, new SecureRandom());
        return keyGen.generateKeyPair();
    }


    public static PrivateKey createPrivateKey(byte[] privateKeyBytes) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException  {
        // Ensure the private key is 32 bytes
        if (privateKeyBytes.length != 32) {
            throw new IllegalArgumentException("Private key must be 32 bytes");
        }

        // Get curve parameters
        X9ECParameters params = SECNamedCurves.getByName("secp256k1");
        ECCurve curve = params.getCurve();
        ECDomainParameters domainParams = new ECDomainParameters(curve, params.getG(), params.getN(), params.getH());

        // Create EC Parameter Spec
        ECParameterSpec ecSpec = new ECParameterSpec(curve, params.getG(), params.getN(), params.getH());

        // Convert byte array to BigInteger
        BigInteger privateKeyBigInteger = new BigInteger(1, privateKeyBytes);

        // Create EC Private Key Spec
        ECPrivateKeySpec privateKeySpec = new ECPrivateKeySpec(privateKeyBigInteger, ecSpec);

        // Generate PrivateKey
        KeyFactory keyFactory = KeyFactory.getInstance("ECDSA", "BC");
        return keyFactory.generatePrivate(privateKeySpec);
    }

    public static KeyPair createKeyPair(byte[] privateKeyBytes) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException, InvalidKeySpecException {
        if (privateKeyBytes.length != 32) {
            throw new IllegalArgumentException("Private key must be 32 bytes");
        }

        // Get curve parameters
        X9ECParameters params = SECNamedCurves.getByName("secp256k1");
        ECDomainParameters domainParams = new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
        ECParameterSpec ecSpec = new ECParameterSpec(params.getCurve(), params.getG(), params.getN(), params.getH());

        // Create private key
        BigInteger privateKeyBigInteger = new BigInteger(1, privateKeyBytes);
        ECPrivateKeySpec privateKeySpec = new ECPrivateKeySpec(privateKeyBigInteger, ecSpec);

        // Derive public key
        ECPoint q = domainParams.getG().multiply(privateKeyBigInteger);
        ECPublicKeySpec publicKeySpec = new ECPublicKeySpec(q, ecSpec);

        // Create KeyFactory and generate KeyPair
        KeyFactory keyFactory = KeyFactory.getInstance("EC", "BC");
        PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
        PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

        return new KeyPair(publicKey, privateKey);
    }

    public static void main(String[] args) throws Exception {
        testEncryptAndDecrypt();

        testDecryhptCipherFromFreeSignV1();


    }

    public static byte[] encrypt(byte[] msg,byte[] pubKey){
        PublicKey publicKey;
        try {
            publicKey = createPublicKey(pubKey);
            return Bitcore.encrypt(msg, publicKey );

        } catch (NoSuchAlgorithmException e) {
            System.out.println(e.getLocalizedMessage());
            return null;
        } catch (NoSuchProviderException e) {
            System.out.println(e.getLocalizedMessage());
            return null;
        } catch (InvalidKeySpecException e) {
            System.out.println(e.getLocalizedMessage());
            return null;
        } catch (Exception e) {
            System.out.println(e.getLocalizedMessage());
            return null;
        }

    }

    

    private static void testEncryptAndDecrypt() throws Exception {
        byte[] privateKeyBytes = KeyTools.getPrikey32("L2bHRej6Fxxipvb4TiR5bu1rkT3tRp8yWEsUy4R1Zb8VMm2x7sd8");
        KeyPair keyPair = createKeyPair(privateKeyBytes);

        String message = "Hello, ECIES!";
        byte[] encrypted = encrypt(message.getBytes(), keyPair.getPublic());
        // byte[] encrypted = encrypt(message.getBytes(), keyPair.getPublic());

        System.out.println("Encrypted: " + Hex.toHexString(encrypted));
        System.out.println("Cipher: "+Base64.getEncoder().encodeToString(encrypted));

        byte[] decrypted = decrypt(encrypted, privateKeyBytes);//keyPair.getPrivate());
        System.out.println("Decrypted: " + new String(decrypted));
    }

    private static void testDecryhptCipherFromFreeSignV1() throws Exception {
        String cipher = "AjTU0rGQvDxhCs3F5x4Pcz3Bsiiry2LryPcKcPIZ2iDsD68U5he9FkM6AVUzEHTjmfBLkhfFu7rv4fveoyMi5YH+wQoiWDxgs/MYjGZBL/Fuq6XZ6IOCXfWyfwphE4uxhEg5TD9ZBRsrJbNxwbdfee5ev5Gvc8kwYROycs0sAG3rNdoJbEZZ7bs2DqvHbAWdG7w4gYLhP9o+C/xVTZHz7Ks9VHb6i04/1at40etlWXxPWSvkdDWxTtyWSSsY2jrbYjfe+ytXQRTRY4gYQdwg+9s=";
                //"A1f7bKbSMYNVvfgTGY8yf+bD4RQEzouTnkJB7bDNRc1zZCYWTy+duQECOa+CMhkB7PVua6YAFm1UQdTsHRIML/ehzdic3tn+Vm11IMsuE0j6dgoiZMcja0fcRJifSieKqA==";

        byte[] cipherBytes = Base64.getDecoder().decode(cipher);

        byte[] privateKeyBytes = KeyTools.getPrikey32("L2bHRej6Fxxipvb4TiR5bu1rkT3tRp8yWEsUy4R1Zb8VMm2x7sd8");
        System.out.println("PubKey B:"+Hex.toHexString(KeyTools.prikeyToPubkey(privateKeyBytes)));

        assert privateKeyBytes != null;

        // You need to provide the correct private key here
//        PrivateKey privateKey = createPrivateKey(privateKeyBytes);// ... load your private key here

        byte[] decrypted = decrypt(cipherBytes, privateKeyBytes);
        System.out.println("Decrypted: " + new String(decrypted));

        CryptoDataByte bitcoreCipher = parseBitcoreCipher(cipher);
        System.out.println(bitcoreCipher.toNiceJson());

        byte[] cipher2 = fromCryptoDataByte(bitcoreCipher);
        System.out.println("Cipher2:"+Base64.getEncoder().encodeToString(cipher2));
    }

    public static CryptoDataByte parseBitcoreCipher(String base64Cipher) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setAlg(AlgorithmId.BitCore_EccAes256);
        cryptoDataByte.setType(EncryptType.AsyOneWay);
        // Decode the Base64 string
        byte[] decodedCipher = Base64.getDecoder().decode(base64Cipher);

        // Extract the public key (first 33 bytes)
        byte[] pubKeyBytes = Arrays.copyOfRange(decodedCipher, 0, 33);
        cryptoDataByte.setPubkeyA(pubKeyBytes);



        // Extract the IV (next 16 bytes)
        byte[] ivBytes = Arrays.copyOfRange(decodedCipher, 33, 49);
        cryptoDataByte.setIv(ivBytes);

        // The rest is the ciphertext
        byte[] ciphertextBytes = Arrays.copyOfRange(decodedCipher, 49, decodedCipher.length);
        cryptoDataByte.setCipher(ciphertextBytes);

        return cryptoDataByte;
    }

    public static byte[] fromCryptoDataByte(CryptoDataByte cryptoDataByte) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            byteArrayOutputStream.write(cryptoDataByte.getPubkeyA());
            byteArrayOutputStream.write(cryptoDataByte.getIv());
            byteArrayOutputStream.write(cryptoDataByte.getCipher());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return byteArrayOutputStream.toByteArray();
    }

    public static PublicKey createPublicKey(byte[] publicKeyBytes) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException {
        // Get curve parameters
        X9ECParameters params = SECNamedCurves.getByName("secp256k1");
        ECParameterSpec ecSpec = new ECParameterSpec(params.getCurve(), params.getG(), params.getN(), params.getH());

        // Create point from public key bytes
        ECPoint point = params.getCurve().decodePoint(publicKeyBytes);
        
        // Create public key spec
        ECPublicKeySpec publicKeySpec = new ECPublicKeySpec(point, ecSpec);

        // Generate PublicKey
        KeyFactory keyFactory = KeyFactory.getInstance("EC", "BC");
        return keyFactory.generatePublic(publicKeySpec);
    }
}