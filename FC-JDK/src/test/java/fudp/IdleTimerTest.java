package fudp;

import fudp.connection.PeerConnection;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The idle timer follows QUIC (RFC 9000 §10.1): a packet from the peer
 * restarts it, and so does the first ack-eliciting packet sent after one,
 * but no later send. A node that keeps sending to a peer that is gone (a
 * relay's notices, their retransmissions) must still time it out.
 */
public class IdleTimerTest {

    private static Instant later(PeerConnection c) throws InterruptedException {
        Instant before = c.getLastActivity();
        while (!Instant.now().isAfter(before)) Thread.sleep(2);
        Thread.sleep(2);
        return before;
    }

    @Test
    public void sendsAloneDoNotKeepAConnectionAlive() throws Exception {
        PeerConnection c = new PeerConnection("peer", new InetSocketAddress("127.0.0.1", 1), 1);
        c.onPacketReceived(100);

        Instant heard = later(c);
        c.recordSentPacket(1, List.of(), 100, true);
        Instant firstSend = c.getLastActivity();
        assertTrue(firstSend.isAfter(heard), "the first ack-eliciting send after hearing from the peer counts");

        later(c);
        c.recordSentPacket(2, List.of(), 100, true);
        c.recordSentPacket(3, List.of(), 100, true, 1); // a retransmission
        assertEquals(firstSend, c.getLastActivity(), "sending again to a silent peer does not");

        later(c);
        c.onPacketReceived(40);
        Instant heardAgain = c.getLastActivity();
        assertTrue(heardAgain.isAfter(firstSend), "hearing from the peer does");

        later(c);
        c.recordSentPacket(4, List.of(), 60, false);
        assertEquals(heardAgain, c.getLastActivity(), "a packet that elicits no ACK never does");
        c.recordSentPacket(5, List.of(), 100, true);
        assertTrue(c.getLastActivity().isAfter(heardAgain), "the next ack-eliciting one does");
    }
}
