package fapi.components.call;

import config.Settings;
import core.crypto.KeyTools;
import fapi.client.FapiClient;
import fapi.components.CallComponent;
import fapi.message.FapiRequest;
import fapi.message.FapiResponse;
import fapi.service.FapiServer;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import fudp.node.NodeEventListener;
import fudp.transport.DatagramResult;
import org.junit.jupiter.api.Test;
import utils.Hex;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A 1:1 call through the whole server path (VOICE_SPEC §6.2, §7): a real
 * FapiServer with CallComponent registered, its node's events passed on
 * through FudpEventAware, and FAPI clients on nodes under throwaway keys.
 * CallRelayTest covers the rules; this covers the wiring.
 */
public class CallComponentFapiTest {

    private static final SecureRandom RNG = new SecureRandom();

    private static byte[] key() {
        byte[] k = new byte[32];
        RNG.nextBytes(k);
        return k;
    }

    private static FudpNode node(byte[] priv, int port) throws Exception {
        NodeConfig c = new NodeConfig();
        c.setPort(port);
        c.setDataDir(System.getProperty("java.io.tmpdir") + "/call_fapi_" + port + "_" + System.nanoTime());
        return new FudpNode(priv, c);
    }

    static final class Client {
        final byte[] fidPriv = key(), tPriv = key();
        final byte[] tPub = KeyTools.prikeyToPubkey(tPriv);
        final String fid = KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(fidPriv));
        final int ssrc = RNG.nextInt();
        final LinkedBlockingQueue<byte[]> datagrams = new LinkedBlockingQueue<>();
        final LinkedBlockingQueue<String> notices = new LinkedBlockingQueue<>();
        FudpNode node;
        FapiClient fapi;
        long routeId;

        void start(int port, String relayFid, byte[] relayPub) throws Exception {
            node = node(tPriv, port);
            node.setEventListener(new NodeEventListener() {
                @Override
                public void onDatagram(String peerId, long connectionId, byte[] data) {
                    datagrams.add(data);
                }

                @Override
                public void onNotifyReceived(String peerId, long messageId, int dataType, byte[] data) {
                    if (dataType == 1) notices.add(new String(data, StandardCharsets.UTF_8));
                }
            });
            node.start();
            node.addPeer(relayFid, relayPub, "127.0.0.1", 19940);
            fapi = new FapiClient(node, relayFid, "call-test");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> call(String api, Map<String, Object> params) {
            FapiResponse r = fapi.request(FapiRequest.operation(api, params));
            assertNotNull(r, api + ": no response");
            assertTrue(r.isSuccess(), api + " refused: " + r.getCode() + " " + r.getMessage());
            return (Map<String, Object>) r.getData();
        }

        Map<String, Object> delegation(String callId) {
            Delegation d = Delegation.sign(fidPriv, callId, tPub, System.currentTimeMillis() / 1000 + 3600);
            Map<String, Object> m = new HashMap<>();
            m.put("fid", d.fid);
            m.put("fidPub", d.fidPub);
            m.put("tPub", d.tPub);
            m.put("expiresSec", d.expiresSec);
            m.put("sig", d.sig);
            return m; // as the phone sends it: the §4.1 object, not a string
        }
    }

    @Test
    public void aCallThroughFapiServerAndCallComponent() throws Exception {
        byte[] relayPriv = key();
        byte[] relayPub = KeyTools.prikeyToPubkey(relayPriv);
        String relayFid = KeyTools.pubkeyToFchAddr(relayPub);
        FudpNode relayNode = node(relayPriv, 19940);

        Settings settings = mock(Settings.class);
        when(settings.getMainFid()).thenReturn(relayFid);
        FapiServer server = new FapiServer(settings); // no on-chain Service: no billing, a free relay
        server.setFudpNode(relayNode);
        server.initialize();
        server.registerComponent(new CallComponent());
        relayNode.setEventListener(server); // what the server's starter does

        Client caller = new Client(), callee = new Client();
        try {
            relayNode.start();
            caller.start(19941, relayFid, relayPub);
            callee.start(19942, relayFid, relayPub);
            String callId = Hex.toHex(key()).substring(0, 32);

            caller.call("call.create", new HashMap<>(Map.of("meetingId", callId, "kind", "p2p",
                    "delegation", caller.delegation(callId))));
            Map<String, Object> cj = caller.call("call.join", new HashMap<>(Map.of("meetingId", callId,
                    "ssrc", Integer.toUnsignedLong(caller.ssrc), "ts", System.currentTimeMillis(),
                    "delegation", caller.delegation(callId))));
            assertEquals(Boolean.TRUE, cj.get("datagram"));
            caller.routeId = ((Number) cj.get("routeId")).longValue();

            byte[] secret = CallKeys.p2pSecret(caller.tPriv, callee.tPub, callId, caller.fid, callee.fid);
            byte[] authPriv = CallKeys.authPriv(secret);
            caller.call("call.register", new HashMap<>(Map.of("meetingId", callId,
                    "authPub", Hex.toHex(CallKeys.authPub(authPriv)), "delegation", caller.delegation(callId))));

            long ts = System.currentTimeMillis();
            Map<String, Object> ej = callee.call("call.join", new HashMap<>(Map.of("meetingId", callId,
                    "ssrc", Integer.toUnsignedLong(callee.ssrc), "ts", ts, "delegation", callee.delegation(callId),
                    "admitSig", Hex.toHex(CallKeys.admitSig(authPriv, callId, callee.tPub, callee.ssrc, ts)))));
            callee.routeId = ((Number) ej.get("routeId")).longValue();
            assertNotNull(caller.notices.poll(5, TimeUnit.SECONDS), "roster notice reached the caller");

            for (Client c : new Client[]{caller, callee}) {
                c.node.enableDatagrams(c.node.getProtocol().getConnectionManager().getAnyConnection(relayFid)
                        .getConnectionId());
            }
            byte[] key = CallKeys.senderKey(secret, caller.fid, caller.ssrc, 0);
            long conn = caller.node.getProtocol().getConnectionManager().getAnyConnection(relayFid).getConnectionId();
            for (long seq = 0; seq < 10; seq++) {
                assertEquals(DatagramResult.SENT, caller.node.sendDatagram(conn, MediaFrame.seal(key,
                        new MediaFrame.Header(MediaFrame.FLAG_VAD, (int) caller.routeId, caller.ssrc, seq, seq * 960,
                                30, 0), new byte[]{(byte) seq})));
            }
            for (long seq = 0; seq < 10; seq++) {
                byte[] got = callee.datagrams.poll(5, TimeUnit.SECONDS);
                assertNotNull(got, "frame " + seq + " came through FapiServer's datagram hook");
                assertArrayEquals(new byte[]{(byte) seq}, MediaFrame.open(key, got));
            }

            Map<String, Object> info = caller.call("call.info", new HashMap<>(Map.of("meetingId", callId)));
            assertEquals(2, ((Number) info.get("participants")).intValue());
            callee.call("call.leave", new HashMap<>(Map.of("meetingId", callId)));
            info = caller.call("call.info", new HashMap<>(Map.of("meetingId", callId)));
            assertEquals(1, ((Number) info.get("participants")).intValue());
        } finally {
            for (FudpNode n : new FudpNode[]{caller.node, callee.node, relayNode}) if (n != null) n.stop();
        }
    }
}
