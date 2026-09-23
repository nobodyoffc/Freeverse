package fapi.components.call;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import core.crypto.KeyTools;
import fudp.message.ResponseMessage;
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

/**
 * A 1:1 call through {@link CallRelay} over real FUDP (VOICE_SPEC §6.2, §7):
 * two clients whose nodes run under throwaway transport keys, a relay node
 * wired the way CallComponent wires it, sealed frames both ways.
 */
public class CallRelayFudpTest {

    private static final SecureRandom RNG = new SecureRandom();
    private static final Gson GSON = new Gson();

    private static byte[] key() {
        byte[] k = new byte[32];
        RNG.nextBytes(k);
        return k;
    }

    private static FudpNode node(byte[] priv, int port) throws Exception {
        NodeConfig c = new NodeConfig();
        c.setPort(port);
        c.setDataDir(System.getProperty("java.io.tmpdir") + "/call_fudp_" + port + "_" + System.nanoTime());
        return new FudpNode(priv, c);
    }

    /** A client: FID key for delegations, transport key for its node. */
    static final class Client {
        final byte[] fidPriv = key(), tPriv = key();
        final byte[] tPub = KeyTools.prikeyToPubkey(tPriv);
        final String fid = KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(fidPriv));
        final int ssrc = RNG.nextInt();
        final LinkedBlockingQueue<byte[]> datagrams = new LinkedBlockingQueue<>();
        final LinkedBlockingQueue<String> notices = new LinkedBlockingQueue<>();
        FudpNode node;
        long relayConn;
        long routeId;

        void start(int port) throws Exception {
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
        }

        Map<String, Object> call(String relayFid, String method, Map<String, Object> params) throws Exception {
            ResponseMessage r = node.request(relayFid, method, GSON.toJson(params).getBytes(StandardCharsets.UTF_8))
                    .get(10, TimeUnit.SECONDS);
            Map<String, Object> body = GSON.fromJson(new String(r.getData(), StandardCharsets.UTF_8),
                    new TypeToken<Map<String, Object>>() {}.getType());
            assertTrue(r.isSuccess(), method + " refused: " + body);
            return body;
        }

        String delegation(String callId) {
            return Delegation.sign(fidPriv, callId, tPub, System.currentTimeMillis() / 1000 + 3600).toJson();
        }
    }

    @Test
    public void aRelayedCallOverFudp() throws Exception {
        byte[] relayPriv = key();
        byte[] relayPub = KeyTools.prikeyToPubkey(relayPriv);
        String relayFid = KeyTools.pubkeyToFchAddr(relayPub);
        FudpNode relayNode = node(relayPriv, 19920);
        Client caller = new Client(), callee = new Client();

        CallRelay relay = new CallRelay(new CallRelay.Transport() {
            @Override
            public boolean sendDatagram(long connectionId, byte[] data) {
                return relayNode.sendDatagram(connectionId, data) == DatagramResult.SENT;
            }

            @Override
            public void enableDatagrams(long connectionId) {
                relayNode.enableDatagrams(connectionId);
            }

            @Override
            public void notify(String peerId, int dataType, byte[] data) {
                try {
                    relayNode.sendNotify(peerId, data, dataType);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, new CallRelay.Billing() {
            @Override
            public boolean canAfford(String fid, long amount) {
                return true;
            }

            @Override
            public boolean charge(String key, String fid, long amount, String meta) {
                return true;
            }
        }, new CallRelay.Pricing(0, 0));

        // Wired as CallComponent wires it: requests by method, datagrams and notifies inline.
        relayNode.setEventListener(new NodeEventListener() {
            @Override
            public void onRequestReceived(String peerId, long connectionId, long requestId, String service, byte[] data) {
                Map<String, Object> p = GSON.fromJson(new String(data, StandardCharsets.UTF_8),
                        new TypeToken<Map<String, Object>>() {}.getType());
                long now = System.currentTimeMillis();
                Object result;
                int status = ResponseMessage.STATUS_SUCCESS;
                try {
                    result = switch (service) {
                        case "call.create" -> relay.create(peerId, p, now);
                        case "call.register" -> relay.register(peerId, p, now);
                        case "call.join" -> relay.join(peerId, connectionId, p, now);
                        default -> Map.of();
                    };
                } catch (CallRelay.Refused r) {
                    status = r.code;
                    result = Map.of("code", r.code, "message", r.getMessage());
                }
                try {
                    relayNode.respond(peerId, connectionId, requestId, status,
                            GSON.toJson(result).getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onDatagram(String peerId, long connectionId, byte[] data) {
                relay.onDatagram(connectionId, data);
            }

            @Override
            public void onNotifyReceived(String peerId, long messageId, int dataType, byte[] data) {
                relay.onNotify(peerId, dataType, data);
            }
        });

        try {
            relayNode.start();
            caller.start(19921);
            callee.start(19922);
            for (Client c : new Client[]{caller, callee}) c.node.addPeer(relayFid, relayPub, "127.0.0.1", 19920);
            byte[] callIdBytes = new byte[16];
            RNG.nextBytes(callIdBytes);
            String callId = Hex.toHex(callIdBytes);

            // §6.2: the caller creates and joins.
            caller.call(relayFid, "call.create", new HashMap<>(Map.of("meetingId", callId, "kind", "p2p",
                    "delegation", caller.delegation(callId))));
            Map<String, Object> cj = caller.call(relayFid, "call.join", new HashMap<>(Map.of("meetingId", callId,
                    "ssrc", Integer.toUnsignedLong(caller.ssrc), "ts", System.currentTimeMillis(),
                    "delegation", caller.delegation(callId))));
            assertEquals(true, cj.get("datagram"), "the capability signal of FUDP7 §3");
            caller.routeId = ((Number) cj.get("routeId")).longValue();

            // INVITE/ACCEPT would carry the transport keys; both derive the call secret.
            byte[] secretAtCaller = CallKeys.p2pSecret(caller.tPriv, callee.tPub, callId, caller.fid, callee.fid);
            byte[] secretAtCallee = CallKeys.p2pSecret(callee.tPriv, caller.tPub, callId, callee.fid, caller.fid);
            assertArrayEquals(secretAtCaller, secretAtCallee);
            byte[] authPriv = CallKeys.authPriv(secretAtCaller);
            caller.call(relayFid, "call.register", new HashMap<>(Map.of("meetingId", callId,
                    "authPub", Hex.toHex(CallKeys.authPub(authPriv)), "delegation", caller.delegation(callId))));

            long ts = System.currentTimeMillis();
            Map<String, Object> ej = callee.call(relayFid, "call.join", new HashMap<>(Map.of("meetingId", callId,
                    "ssrc", Integer.toUnsignedLong(callee.ssrc), "ts", ts, "delegation", callee.delegation(callId),
                    "admitSig", Hex.toHex(CallKeys.admitSig(CallKeys.authPriv(secretAtCallee), callId, callee.tPub,
                            callee.ssrc, ts)))));
            callee.routeId = ((Number) ej.get("routeId")).longValue();

            String roster = caller.notices.poll(5, TimeUnit.SECONDS);
            assertNotNull(roster, "the caller hears the callee joined");
            assertTrue(roster.contains(callee.fid));

            for (Client c : new Client[]{caller, callee}) {
                c.relayConn = c.node.getProtocol().getConnectionManager().getAnyConnection(relayFid).getConnectionId();
                c.node.enableDatagrams(c.relayConn); // the relay said "datagram": true
            }

            // Audio both ways, sealed end to end: the relay forwards what it cannot read.
            byte[] callerKey = CallKeys.senderKey(secretAtCaller, caller.fid, caller.ssrc, 0);
            byte[] calleeKey = CallKeys.senderKey(secretAtCallee, callee.fid, callee.ssrc, 0);
            for (long seq = 0; seq < 20; seq++) {
                byte[] opus = ("caller " + seq).getBytes(StandardCharsets.UTF_8);
                assertEquals(DatagramResult.SENT, caller.node.sendDatagram(caller.relayConn, MediaFrame.seal(callerKey,
                        new MediaFrame.Header(MediaFrame.FLAG_VAD, (int) caller.routeId, caller.ssrc, seq, seq * 960, 30, 0),
                        opus)));
                callee.node.sendDatagram(callee.relayConn, MediaFrame.seal(calleeKey,
                        new MediaFrame.Header(MediaFrame.FLAG_VAD, (int) callee.routeId, callee.ssrc, seq, seq * 960, 30, 0),
                        ("callee " + seq).getBytes(StandardCharsets.UTF_8)));
                Thread.sleep(20);
            }
            for (long seq = 0; seq < 20; seq++) {
                byte[] got = callee.datagrams.poll(5, TimeUnit.SECONDS);
                assertNotNull(got, "callee got frame " + seq);
                assertEquals("caller " + seq, new String(MediaFrame.open(callerKey, got), StandardCharsets.UTF_8));
                byte[] back = caller.datagrams.poll(5, TimeUnit.SECONDS);
                assertNotNull(back, "caller got frame " + seq);
                assertEquals("callee " + seq, new String(MediaFrame.open(calleeKey, back), StandardCharsets.UTF_8));
            }
            assertEquals(40L, relay.stats().get("framesOut"));
        } finally {
            for (FudpNode n : new FudpNode[]{caller.node, callee.node, relayNode}) if (n != null) n.stop();
        }
    }
}
