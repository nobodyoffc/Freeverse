package fapi.components.call;

import core.crypto.KeyTools;
import utils.Hex;
import com.google.gson.Gson;

import java.util.Arrays;

/**
 * A FID's statement that a throwaway transport key speaks for it in one call
 * or meeting (VOICE_SPEC §4.1, Decision 5). The call's FUDP node runs under
 * {@code tPub}, so the media code never needs the FID's own key.
 *
 * <pre>
 * sig = Schnorr(fidPriv, "FreerCall-delegate-v1" ‖ str(callOrMeetingId) ‖ tPub(33) ‖ u64(expiresSec))
 * </pre>
 *
 * Published as JSON {@code {fid, fidPub, tPub, expiresSec, sig}}, keys hex.
 */
public final class Delegation {

    public static final String TAG = "FreerCall-delegate-v1";
    /** A delegation may not claim to last longer than this. */
    public static final long MAX_LIFETIME_SEC = 24 * 3600;

    private static final Gson GSON = new Gson();

    public String fid;
    public String fidPub;
    public String tPub;
    public long expiresSec;
    public String sig;

    /**
     * Why a delegation was refused, or {@code OK}. {@code BAD_SIGNATURE} is
     * also what a delegation for a different call gives: the id is inside
     * the signed bytes, so the two cannot be told apart, and need not be.
     */
    public enum Check { OK, MALFORMED, FID_MISMATCH, BAD_SIGNATURE, EXPIRED, TOO_LONG }

    public static Delegation sign(byte[] fidPriv, String callOrMeetingId, byte[] tPub, long expiresSec) {
        byte[] fidPubBytes = KeyTools.prikeyToPubkey(fidPriv);
        Delegation d = new Delegation();
        d.fid = KeyTools.pubkeyToFchAddr(fidPubBytes);
        d.fidPub = Hex.toHex(fidPubBytes);
        d.tPub = Hex.toHex(tPub);
        d.expiresSec = expiresSec;
        d.sig = Hex.toHex(CallSig.sign(fidPriv, preimage(callOrMeetingId, tPub, expiresSec)));
        return d;
    }

    static byte[] preimage(String callOrMeetingId, byte[] tPub, long expiresSec) {
        return CallBytes.of(TAG).str(callOrMeetingId).bytes(tPub).u64(expiresSec).toBytes();
    }

    /**
     * Everything a verifier (peer or relay) must check: the key hashes to the
     * FID, the signature holds, it has not expired, it claims no more than
     * {@link #MAX_LIFETIME_SEC}, and it names this call.
     */
    public Check verify(String expectedCallOrMeetingId, long nowSec) {
        byte[] fidPubBytes, tPubBytes, sigBytes;
        try {
            fidPubBytes = Hex.fromHex(fidPub);
            tPubBytes = Hex.fromHex(tPub);
            sigBytes = Hex.fromHex(sig);
        } catch (RuntimeException e) {
            return Check.MALFORMED;
        }
        if (fid == null || fidPubBytes == null || fidPubBytes.length != 33 || tPubBytes == null
                || tPubBytes.length != 33 || sigBytes == null || sigBytes.length != 64) {
            return Check.MALFORMED;
        }
        if (!fid.equals(KeyTools.pubkeyToFchAddr(fidPubBytes))) return Check.FID_MISMATCH;
        if (!CallSig.verify(fidPubBytes, preimage(expectedCallOrMeetingId, tPubBytes, expiresSec), sigBytes)) {
            return Check.BAD_SIGNATURE;
        }
        if (expiresSec <= nowSec) return Check.EXPIRED;
        if (expiresSec - nowSec > MAX_LIFETIME_SEC) return Check.TOO_LONG;
        return Check.OK;
    }

    public byte[] tPubBytes() {
        return Hex.fromHex(tPub);
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static Delegation fromJson(String json) {
        return GSON.fromJson(json, Delegation.class);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Delegation d)) return false;
        return expiresSec == d.expiresSec && java.util.Objects.equals(fid, d.fid)
                && java.util.Objects.equals(fidPub, d.fidPub) && java.util.Objects.equals(tPub, d.tPub)
                && java.util.Objects.equals(sig, d.sig);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[]{fid, fidPub, tPub, expiresSec, sig});
    }
}
