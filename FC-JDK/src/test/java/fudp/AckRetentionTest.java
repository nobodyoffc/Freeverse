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

    @Test
    void aReplayedPacketNumberDoesNotPinTheRetentionPruneOpen() throws Exception {
        AckManager acks = freshManager();

        for (long pn = 0; pn < 50; pn++) {
            acks.onPacketReceived(pn);
        }
        assertNotNull(acks.generateAckFrame(), "a first frame covers the batch");

        Thread.sleep(PAST_RETENTION_MS);

        // An attacker replays the oldest number while ordinary traffic
        // continues. Packet 0 is long past the retention window.
        for (long pn = 1_000; pn < 1_050; pn++) {
            acks.onPacketReceived(0);
            acks.onPacketReceived(pn);
        }

        AckFrame frame = acks.generateAckFrame();
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
        assertNotNull(acks.generateAckFrame());
        acks.onPacketReceived(7);
        assertNull(acks.generateAckFrame(),
                "nothing new arrived, so there is nothing to say");
    }
}
