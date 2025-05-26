package core.crypto.Algorithm;

import core.crypto.Encryptor;
import core.crypto.KeyTools;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class Ecc256K1 {
    public static byte[] getSharedSecret(byte[] priKeyBytes, byte[] pubKeyBytes) {
        ECPrivateKeyParameters priKey = KeyTools.prikeyFromBytes(priKeyBytes);
        ECPublicKeyParameters pubKey = KeyTools.pubkeyFromBytes(pubKeyBytes);
        ECDHBasicAgreement agreement = new ECDHBasicAgreement();
        agreement.init(priKey);
        return agreement.calculateAgreement(pubKey).toByteArray();
    }
    @NotNull
    public static byte[] sharedSecretToSymkey(byte[] sharedSecret, byte[] nonce) {
        byte[] symkey;
        byte[] secretHashWithNonce = new byte[sharedSecret.length+ nonce.length];
        System.arraycopy(nonce, 0, secretHashWithNonce, 0, nonce.length);
        System.arraycopy(sharedSecret, 0, secretHashWithNonce, nonce.length, sharedSecret.length);
        byte[] hash = Encryptor.sha512(secretHashWithNonce);

        symkey = new byte[32];
        System.arraycopy(hash,0,symkey,0,32);
        return symkey;
    }

    public static byte[] asyKeyToSymkey(byte[] priKey, byte[] pubKey,byte[]iv) {
        byte[] symkey;

        byte[] sharedSecret = getSharedSecret(priKey, pubKey);

        symkey = sharedSecretToSymkey(sharedSecret, iv);

        Arrays.fill(sharedSecret, (byte) 0);
        return symkey;
    }


}
