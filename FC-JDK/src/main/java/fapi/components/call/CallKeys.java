package fapi.components.call;

import core.crypto.Algorithm.Ecc256K1Hkdf;
import core.crypto.Algorithm.HKDF;
import core.crypto.KeyTools;
import core.fch.SchnorrSignature;
import utils.Hex;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Key material for calls and meetings (VOICE_SPEC §4.2–§4.4). All HKDF is
 * HKDF-SHA256 (FTSP13); an empty salt means RFC 5869's all-zero salt.
 */
public final class CallKeys {

    public static final int KEY_LEN = 32;

    private CallKeys() {}

    /**
     * 1:1, forward secret (§4.2): once both sides delete their throwaway keys,
     * nothing recovers it.
     *
     * <pre>
     * HKDF(ikm = ECDH(tPriv_self, tPub_peer), salt = callId (16 bytes),
     *      info = "FreerCall v1 p2p" ‖ str(min(fidA,fidB)) ‖ str(max(fidA,fidB)))
     * </pre>
     *
     * The ECDH output is the shared point's x-coordinate, 32 bytes.
     *
     * @param callIdHex the 16-byte call id, hex, as it travels in signalling
     */
    public static byte[] p2pSecret(byte[] tPrivSelf, byte[] tPubPeer, String callIdHex, String fidA, String fidB) {
        byte[] callId = Hex.fromHex(callIdHex);
        if (callId == null || callId.length != 16) throw new IllegalArgumentException("callId must be 16 bytes");
        byte[] shared = Ecc256K1Hkdf.getSharedSecret(tPrivSelf, tPubPeer);
        String lo = fidA.compareTo(fidB) <= 0 ? fidA : fidB;
        String hi = lo.equals(fidA) ? fidB : fidA;
        byte[] info = CallBytes.of("FreerCall v1 p2p").str(lo).str(hi).toBytes();
        try {
            return hkdf(shared, callId, info);
        } finally {
            Arrays.fill(shared, (byte) 0);
        }
    }

    /**
     * Meeting, no stronger than the entity's symkey (§4.2).
     *
     * <pre>
     * HKDF(ikm = symkey, salt = nonce (32 bytes),
     *      info = "FreerCall v1 meeting" ‖ str(entityId) ‖ u64(symkeyVersion) ‖ str(meetingId))
     * </pre>
     */
    public static byte[] meetingSecret(byte[] symkey, byte[] nonce32, String entityId, long symkeyVersion,
                                       String meetingId) {
        if (nonce32.length != 32) throw new IllegalArgumentException("meeting nonce must be 32 bytes");
        byte[] info = CallBytes.of("FreerCall v1 meeting").str(entityId).u64(symkeyVersion).str(meetingId).toBytes();
        return hkdf(symkey, nonce32, info);
    }

    /**
     * One sender's frame key (§4.3). The {@code ssrc} is fresh on every join,
     * so two devices of one FID never share a key and counter.
     *
     * <pre>
     * HKDF(ikm = callSecret, salt = ∅, info = "FreerCall v1 sender" ‖ str(fid) ‖ u32(ssrc) ‖ u8(keyEpoch))
     * </pre>
     */
    public static byte[] senderKey(byte[] callSecret, String fid, int ssrc, int keyEpoch) {
        byte[] info = CallBytes.of("FreerCall v1 sender").str(fid).u32(ssrc & 0xFFFFFFFFL).u8(keyEpoch).toBytes();
        return hkdf(callSecret, null, info);
    }

    /**
     * The admission key (§4.4): the relay holds only {@code authPub}, and a
     * joiner proves it holds the call key by signing with {@code authPriv}.
     *
     * <pre>
     * authSeed = HKDF(ikm = callSecret, salt = ∅, info = "FreerCall v1 admit", L = 32)
     * authPriv = authSeed mod n   (retry with info ‖ 0x01 if zero)
     * </pre>
     *
     * @return the 32-byte private key; {@link #authPub} gives its public key
     */
    public static byte[] authPriv(byte[] callSecret) {
        byte[] info = CallBytes.of("FreerCall v1 admit").toBytes();
        for (int attempt = 0; ; attempt++) {
            byte[] seed = hkdf(callSecret, null, info);
            BigInteger k = new BigInteger(1, seed).mod(SchnorrSignature.n);
            if (k.signum() != 0) return to32(k);
            info = CallBytes.of("FreerCall v1 admit").bytes(repeat01(attempt + 1)).toBytes();
        }
    }

    public static byte[] authPub(byte[] authPriv) {
        return KeyTools.prikeyToPubkey(authPriv);
    }

    /**
     * {@code admitSig = Schnorr(authPriv, "FreerCall-admit-v1" ‖ str(meetingId) ‖ tPub ‖ u32(ssrc) ‖ u64(ts))},
     * sent with {@code call.join} (§7.2). {@code ts} is milliseconds.
     */
    public static byte[] admitSig(byte[] authPriv, String meetingId, byte[] tPub, int ssrc, long tsMs) {
        return CallSig.sign(authPriv, admitPreimage(meetingId, tPub, ssrc, tsMs));
    }

    public static boolean verifyAdmit(byte[] authPub, String meetingId, byte[] tPub, int ssrc, long tsMs, byte[] sig) {
        return CallSig.verify(authPub, admitPreimage(meetingId, tPub, ssrc, tsMs), sig);
    }

    static byte[] admitPreimage(String meetingId, byte[] tPub, int ssrc, long tsMs) {
        return CallBytes.of("FreerCall-admit-v1").str(meetingId).bytes(tPub).u32(ssrc & 0xFFFFFFFFL).u64(tsMs)
                .toBytes();
    }

    /** The 12-byte AES-GCM nonce of a media frame: {@code u32(ssrc) ‖ u64(seq)} (§4.3). */
    public static byte[] frameNonce(int ssrc, long seq) {
        return CallBytes.of("").u32(ssrc & 0xFFFFFFFFL).u64(seq).toBytes();
    }

    private static byte[] hkdf(byte[] ikm, byte[] salt, byte[] info) {
        try {
            return HKDF.hkdf(ikm, salt, info, KEY_LEN);
        } catch (Exception e) {
            throw new IllegalStateException("HKDF failed", e);
        }
    }

    /** The retry suffix: one 0x01 byte per retry ({@code info ‖ 0x01}, then {@code info ‖ 0x01 ‖ 0x01}, ...). */
    private static byte[] repeat01(int n) {
        byte[] b = new byte[n];
        Arrays.fill(b, (byte) 1);
        return b;
    }

    private static byte[] to32(BigInteger k) {
        byte[] raw = k.toByteArray();
        byte[] out = new byte[32];
        int src = Math.max(0, raw.length - 32);
        System.arraycopy(raw, src, out, 32 - (raw.length - src), raw.length - src);
        return out;
    }
}
