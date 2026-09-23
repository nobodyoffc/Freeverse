package fapi.components.call;

import core.crypto.Hash;
import core.fch.SchnorrSignature;

import java.math.BigInteger;

/**
 * {@code Schnorr(key, m)} in VOICE_SPEC §4: the BCH Schnorr signature that
 * FIMP0V3 uses, over SHA-256d of the preimage {@code m}. Every call preimage
 * starts with its own literal tag, so a signature made for one purpose never
 * verifies for another.
 */
public final class CallSig {

    private CallSig() {}

    public static byte[] sign(byte[] privKey32, byte[] preimage) {
        return SchnorrSignature.schnorr_sign(Hash.sha256x2(preimage), new BigInteger(1, privKey32));
    }

    public static boolean verify(byte[] pubKey33, byte[] preimage, byte[] sig) {
        if (pubKey33 == null || pubKey33.length != 33 || sig == null || sig.length != 64) return false;
        try {
            return SchnorrSignature.schnorr_verify(Hash.sha256x2(preimage), pubKey33, sig);
        } catch (RuntimeException e) {
            return false; // a point not on the curve, and so on
        }
    }
}
