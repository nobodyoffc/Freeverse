package fudp;

import fudp.connection.PeerConnection;
import fudp.packet.frames.AckFrame;
import fudp.transport.AckManager;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The ACK retention prune, against a peer replaying an old packet number.
 *
 * <p>{@code generateAckFrame()} walks {@code receivedPackets} in key order and
 * breaks at the first entry newer than the cutoff. That is correct only while
 * receive times ascend with packet numbers. While {@code onPacketReceived}
 * used {@code put} rather than {@code putIfAbsent}, a duplicate refreshed its
 * entry's timestamp — so replaying the LOWEST retained number parked a recent
 * time at the lowest key and stopped the prune there every time, taking the
 * {@code MAX_RETAINED} check with it (that test lives inside the same loop).
 * The set then grew for as long as the replay continued.
 */
class AckRetentionTest {

    /** ACK_RETAIN_MS is 4 s and package-private; wait past it. */
    private static final long PAST_RETENTION_MS = 4_500;

    private AckManager freshManager() {
        PeerConnection conn = new PeerConnection(
                "FTestPeerIdentityForAckRetention", new InetSocketAddress("127.0.0.1", 9999), 1L);
        return conn.getAckManager();
    }

    /**
     * A late packet number arriving when the buffer is full and its front has
     * been pruned: making room compacts the entries, and the insert position,
     * once found before that, pointed past the end ("arraycopy: length -917 is
     * negative" on a CALL relay).
     */
    @Test
    void aLatePacketIntoAFullPrunedBufferIsRecorded() throws Exception {
        AckManager acks = freshManager();

        // Half the buffer's 1024 slots, then let them age out of the retention window.
        for (long pn = 0; pn < 1024; pn += 2) {
            acks.onPacketReceived(pn);
        }
        Thread.sleep(PAST_RETENTION_MS);
        // The other half fills the buffer, all but 1500; the prune then drops the aged half from the front.
        for (long pn = 1024; pn <= 1536; pn++) {
            if (pn != 1500) acks.onPacketReceived(pn);
        }
        assertNotNull(acks.generateAckFrame(Integer.MAX_VALUE));

        // A late number: it goes between retained ones while the buffer is full.
        assertDoesNotThrow(() -> acks.onNonElicitingPacketReceived(1500));
        acks.onPacketReceived(1537);

        AckFrame frame = acks.generateAckFrame(Integer.MAX_VALUE);
        assertNotNull(frame);
        boolean late = false;
        for (long pn : frame.getAcknowledgedPackets()) {
            if (pn == 1500) late = true;
        }
        assertTrue(late, "the late packet is acknowledged");
    }

    @Test
    void aReplayedPacketNumberDoesNotPinTheRetentionPruneOpen() throws Exception {
        AckManager acks = freshManager();

        for (long pn = 0; pn < 50; pn++) {
            acks.onPacketReceived(pn);
        }
        assertNotNull(acks.generateAckFrame(Integer.MAX_VALUE), "a first frame covers the batch");

        Thread.sleep(PAST_RETENTION_MS);

        // An attacker replays the oldest number while ordinary traffic
        // continues. Packet 0 is long past the retention window.
        for (long pn = 1_000; pn < 1_050; pn++) {
            acks.onPacketReceived(0);
            acks.onPacketReceived(pn);
        }

        AckFrame frame = acks.generateAckFrame(Integer.MAX_VALUE);
        assertNotNull(frame, "current traffic is still acknowledged");

        boolean coversAgedOut = false;
        for (long pn : frame.getAcknowledgedPackets()) {
            if (pn >= 1 && pn < 50) {
                coversAgedOut = true;
                break;
            }
        }
        assertFalse(coversAgedOut,
                "packets 1..49 aged out long ago; replaying packet 0 must not keep them alive");
    }

    /** A duplicate is not new, so it must not make an ACK frame due. */
    @Test
    void aDuplicateDoesNotCountAsNewlyArrived() {
        AckManager acks = freshManager();
        acks.onPacketReceived(7);
        assertNotNull(acks.generateAckFrame(Integer.MAX_VALUE));
        acks.onPacketReceived(7);
        assertNull(acks.generateAckFrame(Integer.MAX_VALUE),
                "nothing new arrived, so there is nothing to say");
    }
}
