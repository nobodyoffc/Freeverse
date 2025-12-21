package core.crypto.Algorithm;

import core.crypto.Encryptor;
import core.crypto.KeyTools;
import data.fcData.AlgorithmId;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class Ecc256K1Hkdf {

    public static final String INFO = "hkdf";

    public static byte[] getSharedSecret(byte[] priKeyBytes, byte[] pubKeyBytes) {
        ECPrivateKeyParameters priKey = KeyTools.prikeyFromBytes(priKeyBytes);
        ECPublicKeyParameters pubKey = KeyTools.pubkeyFromBytes(pubKeyBytes);
        ECDHBasicAgreement agreement = new ECDHBasicAgreement();
        agreement.init(priKey);

        // Get the shared secret as BigInteger
        java.math.BigInteger z = agreement.calculateAgreement(pubKey);

        // Convert to fixed 32-byte array (ensures consistent length)
        byte[] secret = new byte[32];
        byte[] zBytes = z.toByteArray();

        // Handle sign byte: BigInteger.toByteArray() may add leading 0x00 for positive numbers
        int srcPos = (zBytes.length == 33 && zBytes[0] == 0) ? 1 : 0;
        int length = Math.min(zBytes.length - srcPos, 32);
        int destPos = 32 - length;  // Right-align (pad left with zeros)

        System.arraycopy(zBytes, srcPos, secret, destPos, length);

        return secret;
    }
    @NotNull
    public static byte[] sharedSecretToSymkey(byte[] sharedSecret, byte[] nonce) throws Exception {
        return HKDF.hkdf(sharedSecret, nonce, INFO.getBytes(), 32);
    }

    public static byte[] asyKeyToSymkey(byte[] priKey, byte[] pubKey,byte[]iv) throws Exception {
        byte[] symkey;

        byte[] sharedSecret = getSharedSecret(priKey, pubKey);

        symkey = sharedSecretToSymkey(sharedSecret, iv);

        Arrays.fill(sharedSecret, (byte) 0);
        return symkey;
    }


}
