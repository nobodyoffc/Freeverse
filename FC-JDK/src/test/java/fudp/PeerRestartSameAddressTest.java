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

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A client that restarts and comes back from the same address must keep
 * getting replies. The server reuses its connection object for that address
 * and resets it for the new remote connId, but it kept the old session epoch.
 * Once the client stopped stamping its epoch on packets, each packet was checked
 * against that old epoch, read as another restart, and the reset sent the
 * server's stream allocator back to 0. The next reply reused a stream ID the
 * client had already retired, so the client dropped it. Field failure
 * 2026-10-16: after an app restart the phone's first FAPI request after the
 * handshake timed out.
 */
public class PeerRestartSameAddressTest {

    private static FudpNode node(byte[] priv, int port) throws Exception {
        NodeConfig c = new NodeConfig();
        c.setPort(port);
        c.setDataDir(Files.createTempDirectory("peer-restart-" + port).toString());
        return new FudpNode(priv, c);
    }

    private static void ask(FudpNode client, String serverFid, int n, String phase) throws Exception {
        for (int i = 0; i < n; i++) {
            ResponseMessage r = client.request(serverFid, "echo", ("q" + i).getBytes(StandardCharsets.UTF_8))
                    .get(5, TimeUnit.SECONDS);
            assertEquals("a" + i, new String(r.getData(), StandardCharsets.UTF_8), phase + " request " + i);
            Thread.sleep(100); // let ACKs confirm the epoch so later packets omit it
        }
    }

    @Test
    public void clientRestartedOnSameAddressKeepsGettingReplies() throws Exception {
        SecureRandom rng = new SecureRandom();
        byte[] sPriv = new byte[32], cPriv = new byte[32];
        rng.nextBytes(sPriv);
        rng.nextBytes(cPriv);
        String sFid = KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(sPriv));
        String cFid = KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(cPriv));
        int sPort = 23611, cPort = 23612;

        FudpNode server = node(sPriv, sPort);
        server.setEventListener(new NodeEventListener() {
            @Override
            public void onRequestReceived(String peerId, long connectionId, long requestId, String serviceName,
                                          byte[] data) {
                try {
                    String q = new String(data, StandardCharsets.UTF_8);
                    server.respond(peerId, connectionId, requestId, 0,
                            ("a" + q.substring(1)).getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        FudpNode first = node(cPriv, cPort);
        FudpNode second = null;
        try {
            server.start();
            first.start();
            first.addPeer(sFid, KeyTools.prikeyToPubkey(sPriv), "127.0.0.1", sPort);
            server.addPeer(cFid, KeyTools.prikeyToPubkey(cPriv), "127.0.0.1", cPort);
            ask(first, sFid, 3, "before restart");

            // Restart the client: same key, same port, new process state.
            first.stop();
            second = node(cPriv, cPort);
            second.start();
            second.addPeer(sFid, KeyTools.prikeyToPubkey(sPriv), "127.0.0.1", sPort);
            ask(second, sFid, 6, "after restart");
        } finally {
            if (second != null) second.stop();
            first.stop();
            server.stop();
        }
    }
}
