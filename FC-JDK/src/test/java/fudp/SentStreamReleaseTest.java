package fudp;

import core.crypto.KeyTools;
import fudp.message.ResponseMessage;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import fudp.node.NodeEventListener;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A node that has sent many messages must still accept new streams from the
 * peer. Each message has a stream of its own; a stream left in the manager
 * after its last frame counted against the peer's limit of concurrent streams
 * (100), and past it the node refused every new stream from the peer, requests
 * included. A CALL relay hit it a minute into a meeting, after sending that
 * many attestations and notices.
 */
public class SentStreamReleaseTest {

    private static FudpNode node(byte[] priv, int port) throws Exception {
        NodeConfig c = new NodeConfig();
        c.setPort(port);
        c.setDataDir(Files.createTempDirectory("stream-release-" + port).toString());
        return new FudpNode(priv, c);
    }

    @Test
    public void manySentNotifiesDoNotStopThePeersRequests() throws Exception {
        SecureRandom rng = new SecureRandom();
        byte[] aPriv = new byte[32], bPriv = new byte[32];
        rng.nextBytes(aPriv);
        rng.nextBytes(bPriv);
        String aFid = KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(aPriv));
        String bFid = KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(bPriv));
        FudpNode a = node(aPriv, 23601), b = node(bPriv, 23602);
        int notifies = 150; // well past the limit of 100
        CountDownLatch received = new CountDownLatch(notifies);
        a.setEventListener(new NodeEventListener() {
            @Override
            public void onRequestReceived(String peerId, long connectionId, long requestId, String serviceName,
                                          byte[] data) {
                try {
                    a.respond(peerId, connectionId, requestId, 0, "pong".getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        b.setEventListener(new NodeEventListener() {
            @Override
            public void onNotifyReceived(String peerId, long messageId, int dataType, byte[] data) {
                received.countDown();
            }
        });
        try {
            a.start();
            b.start();
            a.addPeer(bFid, KeyTools.prikeyToPubkey(bPriv), "127.0.0.1", 23602);
            b.addPeer(aFid, KeyTools.prikeyToPubkey(aPriv), "127.0.0.1", 23601);
            for (int i = 0; i < notifies; i++) {
                a.sendNotify(bFid, ("notice " + i).getBytes(StandardCharsets.UTF_8));
                if (i % 20 == 19) Thread.sleep(50);
            }
            assertTrue(received.await(10, TimeUnit.SECONDS), "every notify arrived");

            ResponseMessage r = b.request(aFid, "ping", "ping".getBytes(StandardCharsets.UTF_8))
                    .get(10, TimeUnit.SECONDS);
            assertEquals("pong", new String(r.getData(), StandardCharsets.UTF_8),
                    "after " + notifies + " notifies from a, a still takes b's request");
        } finally {
            a.stop();
            b.stop();
        }
    }
}
