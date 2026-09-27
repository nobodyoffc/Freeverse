package fapi.components.call;

import core.crypto.Hash;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * A sender's signed list of the frames it just sent (VOICE_SPEC §5.1).
 * Encryption proves only that a frame came from someone holding the call
 * key, which every member does; this proves which participant sent it.
 *
 * <pre>
 * kind(1)=0x02 routeId(4) ssrc(4) firstSeq(8) count(1) digests(8 × count) sig(64)
 * digest = first 8 bytes of SHA-256(the complete MediaFrame bytes), or 8 zero
 *          bytes for a seq the sender did not send (DTX)
 * sig    = Schnorr(tPriv, "FreerCall-attest-v1" ‖ str(callOrMeetingId) ‖ every byte before sig)
 * </pre>
 *
 * Delivered reliably, as a FUDP NOTIFY, never as a datagram.
 */
public record Attestation(int routeId, int ssrc, long firstSeq, byte[][] digests, byte[] sig) {

    public static final int KIND = 0x02;
    public static final int DIGEST_LEN = 8;
    public static final int MAX_COUNT = 64;
    public static final String TAG = "FreerCall-attest-v1";

    /** First 8 bytes of SHA-256 of a complete, sealed media frame. */
    public static byte[] digest(byte[] mediaFrame) {
        return Arrays.copyOf(Hash.sha256(mediaFrame), DIGEST_LEN);
    }

    /**
     * @param frames the sealed frames, one per seq from {@code firstSeq}, 1 to
     *               {@value #MAX_COUNT} of them; null for a seq that was not sent
     *               (DTX), which gets an all-zero digest no frame can match
     */
    public static Attestation sign(byte[] tPriv, String callOrMeetingId, int routeId, int ssrc, long firstSeq,
                                   List<byte[]> frames) {
        if (frames.isEmpty() || frames.size() > MAX_COUNT) {
            throw new IllegalArgumentException("an attestation covers 1.." + MAX_COUNT + " frames");
        }
        byte[][] digests = new byte[frames.size()][];
        for (int i = 0; i < digests.length; i++) {
            digests[i] = frames.get(i) == null ? new byte[DIGEST_LEN] : digest(frames.get(i));
        }
        byte[] body = body(routeId, ssrc, firstSeq, digests);
        return new Attestation(routeId, ssrc, firstSeq, digests,
                CallSig.sign(tPriv, CallBytes.of(TAG).str(callOrMeetingId).bytes(body).toBytes()));
    }

    /** Signed by the holder of {@code tPub}, for this call? */
    public boolean verify(byte[] tPub, String callOrMeetingId) {
        byte[] body = body(routeId, ssrc, firstSeq, digests);
        return CallSig.verify(tPub, CallBytes.of(TAG).str(callOrMeetingId).bytes(body).toBytes(), sig);
    }

    /** Does it cover {@code seq}, and is {@code mediaFrame} what it says was sent there? */
    public boolean vouchesFor(long seq, byte[] mediaFrame) {
        long i = seq - firstSeq;
        return i >= 0 && i < digests.length && Arrays.equals(digests[(int) i], digest(mediaFrame));
    }

    public long lastSeq() {
        return firstSeq + digests.length - 1;
    }

    public byte[] toBytes() {
        byte[] body = body(routeId, ssrc, firstSeq, digests);
        return ByteBuffer.allocate(body.length + sig.length).put(body).put(sig).array();
    }

    /** @return the attestation, or null if malformed */
    public static Attestation parse(byte[] b) {
        if (b == null || b.length < 18 + DIGEST_LEN + 64 || (b[0] & 0xFF) != KIND) return null;
        ByteBuffer buf = ByteBuffer.wrap(b);
        buf.get();
        int routeId = buf.getInt();
        int ssrc = buf.getInt();
        long firstSeq = buf.getLong();
        int count = buf.get() & 0xFF;
        if (count < 1 || count > MAX_COUNT || b.length != 18 + count * DIGEST_LEN + 64) return null;
        byte[][] digests = new byte[count][DIGEST_LEN];
        for (byte[] d : digests) buf.get(d);
        byte[] sig = new byte[64];
        buf.get(sig);
        return new Attestation(routeId, ssrc, firstSeq, digests, sig);
    }

    private static byte[] body(int routeId, int ssrc, long firstSeq, byte[][] digests) {
        CallBytes b = new CallBytes().u8(KIND).u32(routeId & 0xFFFFFFFFL).u32(ssrc & 0xFFFFFFFFL).u64(firstSeq)
                .u8(digests.length);
        for (byte[] d : digests) b.bytes(d);
        return b.toBytes();
    }
}
