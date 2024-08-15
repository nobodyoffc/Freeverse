package crypto.Algorithm;

import crypto.Encryptor;
import crypto.KeyTools;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class Ecc256K1 {
    public static byte[] getSharedSecret(byte[] priKeyBytes, byte[] pubKeyBytes) {
        ECPrivateKeyParameters priKey = KeyTools.priKeyFromBytes(priKeyBytes);
        ECPublicKeyParameters pubKey = KeyTools.pubKeyFromBytes(pubKeyBytes);
        ECDHBasicAgreement agreement = new ECDHBasicAgreement();
        agreement.init(priKey);
        return agreement.calculateAgreement(pubKey).toByteArray();
    }
    @NotNull
    public static byte[] sharedSecretToSymKey(byte[] sharedSecret, byte[] nonce) {
        byte[] symKey;
        byte[] secretHashWithNonce = new byte[sharedSecret.length+ nonce.length];
        System.arraycopy(nonce, 0, secretHashWithNonce, 0, nonce.length);
        System.arraycopy(sharedSecret, 0, secretHashWithNonce, nonce.length, sharedSecret.length);
        byte[] hash = Encryptor.sha512(secretHashWithNonce);

        symKey = new byte[32];
        System.arraycopy(hash,0,symKey,0,32);
        return symKey;
    }

    public static byte[] asyKeyToSymKey(byte[] priKey, byte[] pubKey,byte[]iv) {
        byte[] symKey;

        byte[] sharedSecret = getSharedSecret(priKey, pubKey);

        symKey = sharedSecretToSymKey(sharedSecret, iv);

        Arrays.fill(sharedSecret, (byte) 0);
        return symKey;
    }


}
