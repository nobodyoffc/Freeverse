package fudp;

import core.crypto.KeyTools;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import fudp.node.NodeEventListener;
import org.junit.jupiter.api.Test;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FUDP over TCP (FUDP8): a client whose UDP to the server goes nowhere — the
 * server's UDP port it knows has nobody listening — reaches it over TCP
 * instead, discovery, handshake, notifies and datagrams alike, with nothing
 * above the socket knowing the difference.
 */
public class TcpBridgeTest {

    private static final SecureRandom RNG = new SecureRandom();

    private static byte[] key() {
        byte[] k = new byte[32];
        RNG.nextBytes(k);
        return k;
    }

    private static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static int freeUdpPort() throws Exception {
        try (DatagramSocket s = new DatagramSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static FudpNode node(byte[] priv, int port, LinkedBlockingQueue<String> heard) throws Exception {
        NodeConfig c = new NodeConfig();
        c.setPort(port);
        c.setDataDir(System.getProperty("java.io.tmpdir") + "/fudp_tcp_" + port + "_" + System.nanoTime());
        FudpNode n = new FudpNode(priv, c);
        n.setEventListener(new NodeEventListener() {
            @Override
            public void onNotifyReceived(String peerId, long messageId, int dataType, byte[] data) {
                heard.add("notify:" + new String(data, StandardCharsets.UTF_8));
            }

            @Override
            public void onDatagram(String peerId, long connectionId, byte[] data) {
                heard.add("datagram:" + new String(data, StandardCharsets.UTF_8));
            }
        });
        n.start();
        return n;
    }

    @Test
    public void aClientReachesAServerOverTcpAlone() throws Exception {
        byte[] serverPriv = key(), clientPriv = key();
        byte[] serverPub = KeyTools.prikeyToPubkey(serverPriv);
        String serverFid = KeyTools.pubkeyToFchAddr(serverPub);
        String clientFid = KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(clientPriv));
        LinkedBlockingQueue<String> atServer = new LinkedBlockingQueue<>(), atClient = new LinkedBlockingQueue<>();
        FudpNode server = node(serverPriv, freeUdpPort(), atServer);
        FudpNode client = node(clientPriv, 0, atClient);
        try {
            int tcpPort = freePort();
            server.getProtocol().tcp().listen(tcpPort);

            // The client knows the server at a UDP port where nobody listens: only TCP can answer.
            int nowhere = freeUdpPort();
            InetSocketAddress as = new InetSocketAddress("127.0.0.1", nowhere);
            client.getProtocol().tcp().connect("127.0.0.1", tcpPort, as);

            byte[] discovered = client.discoverPublicKey("127.0.0.1", nowhere, 3_000).get(5, TimeUnit.SECONDS);
            assertArrayEquals(serverPub, discovered, "HELLO and PUBLIC_KEY cross the TCP bridge");

            client.addPeer(serverFid, serverPub, "127.0.0.1", nowhere);
            assertNotNull(client.pingAwaitPong(serverFid, false, 5_000).get(6, TimeUnit.SECONDS),
                    "the encrypted handshake completes over TCP");

            client.sendNotify(serverFid, "hello server".getBytes(StandardCharsets.UTF_8), 1);
            assertEquals("notify:hello server", atServer.poll(5, TimeUnit.SECONDS));
            server.sendNotify(clientFid, "hello client".getBytes(StandardCharsets.UTF_8), 1);
            assertEquals("notify:hello client", atClient.poll(5, TimeUnit.SECONDS));

            long toServer = client.getProtocol().getConnectionManager().getAnyConnection(serverFid).getConnectionId();
            long toClient = server.getProtocol().getConnectionManager().getAnyConnection(clientFid).getConnectionId();
            client.enableDatagrams(toServer);
            server.enableDatagrams(toClient);
            client.sendDatagram(toServer, "frame up".getBytes(StandardCharsets.UTF_8));
            assertEquals("datagram:frame up", atServer.poll(5, TimeUnit.SECONDS));
            server.sendDatagram(toClient, "frame down".getBytes(StandardCharsets.UTF_8));
            assertEquals("datagram:frame down", atClient.poll(5, TimeUnit.SECONDS));
        } finally {
            client.stop();
            server.stop();
        }
    }
}
