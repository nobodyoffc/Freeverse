package fapi.components.call;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * One end-to-end sealed audio frame, the {@code Data} of a DATAGRAM frame
 * (VOICE_SPEC §5). The same bytes travel direct and through the relay, which
 * reads the header and forwards the frame unchanged.
 *
 * <pre>
 * kind(1)=0x01 flags(1) routeId(4) ssrc(4) seq(8) timestamp(4) level(1) keyEpoch(1)
 * ciphertext = AES-256-GCM(senderKey, nonce = u32(ssrc) ‖ u64(seq), opus, aad = the 24 header bytes)
 * </pre>
 *
 * The header is the AAD, so changing any of it, the level included, breaks
 * the tag.
 */
public final class MediaFrame {

    public static final int KIND = 0x01;
    public static final int HEADER = 24;
    public static final int TAG_LEN = 16;

    public static final int FLAG_VAD = 0x01;
    public static final int FLAG_DTX = 0x02;
    /** The payload is a probe or report (§6.2, §9.4), not Opus. */
    public static final int FLAG_CONTROL = 0x80;

    /** The clear header: what the relay sees. */
    public record Header(int flags, int routeId, int ssrc, long seq, long timestamp, int level, int keyEpoch) {

        public byte[] toBytes() {
            return ByteBuffer.allocate(HEADER).put((byte) KIND).put((byte) flags).putInt(routeId).putInt(ssrc)
                    .putLong(seq).putInt((int) timestamp).put((byte) level).put((byte) keyEpoch).array();
        }

        /** @return the header, or null if {@code frame} is not a v1 media frame */
        public static Header parse(byte[] frame) {
            if (frame == null || frame.length < HEADER + TAG_LEN || (frame[0] & 0xFF) != KIND) return null;
            ByteBuffer b = ByteBuffer.wrap(frame, 1, HEADER - 1);
            int flags = b.get() & 0xFF;
            if ((flags & 0x7C) != 0) return null; // bits 2-6 are reserved and must be zero
            return new Header(flags, b.getInt(), b.getInt(), b.getLong(), b.getInt() & 0xFFFFFFFFL,
                    b.get() & 0xFF, b.get() & 0xFF);
        }
    }

    private MediaFrame() {}

    public static byte[] seal(byte[] senderKey, Header h, byte[] payload) {
        byte[] header = h.toBytes();
        byte[] ct = gcm(Cipher.ENCRYPT_MODE, senderKey, CallKeys.frameNonce(h.ssrc(), h.seq()), header, payload);
        byte[] out = Arrays.copyOf(header, HEADER + ct.length);
        System.arraycopy(ct, 0, out, HEADER, ct.length);
        return out;
    }

    /**
     * @return the payload, or null if the frame fails authentication under
     *         {@code senderKey} — a wrong key, a changed header, a changed body
     */
    public static byte[] open(byte[] senderKey, byte[] frame) {
        Header h = Header.parse(frame);
        if (h == null) return null;
        try {
            return gcm(Cipher.DECRYPT_MODE, senderKey, CallKeys.frameNonce(h.ssrc(), h.seq()),
                    Arrays.copyOf(frame, HEADER), Arrays.copyOfRange(frame, HEADER, frame.length));
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private static byte[] gcm(int mode, byte[] key, byte[] nonce, byte[] aad, byte[] data) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LEN * 8, nonce));
            c.updateAAD(aad);
            return c.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
