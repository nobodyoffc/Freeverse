package fapi.components.call;

import core.crypto.KeyTools;
import fapi.client.FapiClient;
import fapi.message.FapiRequest;
import fapi.message.FapiResponse;
import fudp.connection.PeerConnection;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import fudp.node.NodeEventListener;
import org.junit.jupiter.api.Test;
import utils.Hex;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The Phase 4 load test (VOICE_SPEC §14): a meeting of {@code bench.participants}
 * (40) on a live CALL relay, {@code bench.speakers} (3) of them talking, over
 * real FUDP. Speakers send 50 sealed frames a second of Opus-sized payload and
 * an attestation every second; everyone listens. It reports what arrived; the
 * relay's CPU and bandwidth are read on the relay host over the same window.
 * <p>
 * Run it on a host other than the relay's: {@code mvn -f FC-JDK/pom.xml test
 * -Dtest=MeetingLoadBench -Dbench.relay=fudp://<ip>:19950 -Dsurefire.failIfNoSpecifiedTests=false}.
 * The relay is FC-JDK's CallRelayServer, whose key this test derives the same way.
 */
public class MeetingLoadBench {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int FRAME_MS = 20, OPUS_BYTES = 60; // 24 kbps

    static byte[] key() {
        byte[] k = new byte[32];
        RNG.nextBytes(k);
        return k;
    }

    /** One synthetic participant: a FID, a transport key, a node, a FAPI client to the relay. */
    static final class Participant {
        final byte[] fidPriv = key(), tPriv = key();
        final byte[] tPub = KeyTools.prikeyToPubkey(tPriv);
        final String fid = KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(fidPriv));
        final int ssrc = RNG.nextInt();
        final AtomicLong framesIn = new AtomicLong(), bytesIn = new AtomicLong(), attestationsIn = new AtomicLong();
        FudpNode node;
        FapiClient fapi;
        long connection, routeId, seq;
        byte[] senderKey;
        final List<byte[]> pending = new ArrayList<>();
        long pendingFirst = -1;

        Map<String, Object> call(String api, Map<String, Object> p) {
            FapiResponse r = fapi.request(FapiRequest.operation(api, p));
            assertNotNull(r, api + ": no answer");
            assertTrue(r.isSuccess(), api + ": " + r.getCode() + " " + r.getMessage());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) r.getData();
            return data;
        }

        String delegation(String meetingId) {
            return Delegation.sign(fidPriv, meetingId, tPub, System.currentTimeMillis() / 1000 + 3600).toJson();
        }
    }

    @Test
    public void aFortyPersonMeeting() throws Exception {
        String relay = System.getProperty("bench.relay");
        assumeTrue(relay != null && relay.startsWith("fudp://"), "set -Dbench.relay=fudp://host:port");
        int n = Integer.getInteger("bench.participants", 40);
        int speakers = Integer.getInteger("bench.speakers", 3);
        int seconds = Integer.getInteger("bench.seconds", 60);
        int warmup = Integer.getInteger("bench.warmup", 10);
        int basePort = Integer.getInteger("bench.basePort", 21000);
        String host = relay.substring(7, relay.lastIndexOf(':'));
        int port = Integer.parseInt(relay.substring(relay.lastIndexOf(':') + 1));

        // CallRelayServer's key and service id.
        byte[] relayPriv = MessageDigest.getInstance("SHA-256")
                .digest("FreerCall test relay".getBytes(StandardCharsets.UTF_8));
        byte[] relayPub = KeyTools.prikeyToPubkey(relayPriv);
        String relayFid = KeyTools.pubkeyToFchAddr(relayPub);
        String sid = "call-relay-test-" + relayFid;

        byte[] id = new byte[12];
        RNG.nextBytes(id);
        String meetingId = "mtg_" + Hex.toHex(id);
        byte[] secret = CallKeys.meetingSecret(key(), key(), "FLoadTestTeam", 1, meetingId);
        byte[] authPriv = CallKeys.authPriv(secret);

        List<Participant> all = new ArrayList<>();
        ScheduledExecutorService clock = Executors.newScheduledThreadPool(2);
        try {
            for (int i = 0; i < n; i++) {
                Participant p = new Participant();
                NodeConfig c = new NodeConfig();
                c.setPort(basePort + i);
                c.setDataDir(Files.createTempDirectory("meeting-bench-" + i).toString());
                p.node = new FudpNode(p.tPriv, c);
                p.node.setEventListener(new NodeEventListener() {
                    @Override
                    public void onDatagram(String peerId, long connectionId, byte[] data) {
                        p.framesIn.incrementAndGet();
                        p.bytesIn.addAndGet(data.length);
                    }

                    @Override
                    public void onNotifyReceived(String peerId, long messageId, int dataType, byte[] data) {
                        if (dataType == 0) p.attestationsIn.incrementAndGet();
                    }
                });
                p.node.start();
                p.node.addPeer(relayFid, relayPub, host, port);
                p.fapi = new FapiClient(p.node, relayFid, sid);
                all.add(p);
            }
            Participant chair = all.get(0);
            chair.call("call.create", new HashMap<>(Map.of("meetingId", meetingId, "kind", "meeting",
                    "authPub", Hex.toHex(CallKeys.authPub(authPriv)), "delegation", chair.delegation(meetingId))));
            for (Participant p : all) {
                long ts = System.currentTimeMillis();
                Map<String, Object> r = p.call("call.join", new HashMap<>(Map.of("meetingId", meetingId,
                        "ssrc", Integer.toUnsignedLong(p.ssrc), "ts", ts, "delegation", p.delegation(meetingId),
                        "admitSig", Hex.toHex(CallKeys.admitSig(authPriv, meetingId, p.tPub, p.ssrc, ts)))));
                p.routeId = ((Number) r.get("routeId")).longValue();
                PeerConnection conn = p.node.getProtocol().getConnectionManager().getAnyConnection(relayFid);
                p.connection = conn.getConnectionId();
                p.node.enableDatagrams(p.connection);
                p.senderKey = CallKeys.senderKey(secret, p.fid, p.ssrc, 0);
            }
            System.out.printf("[bench] %d joined %s on %s; %d speaking%n", n, meetingId, relay, speakers);

            // Speakers: a frame every 20 ms, an attestation every second.
            for (int s = 0; s < speakers; s++) {
                Participant p = all.get(1 + s);
                int level = 30 + 4 * s;
                clock.scheduleAtFixedRate(() -> {
                    byte[] opus = new byte[OPUS_BYTES];
                    RNG.nextBytes(opus);
                    long seq = p.seq++;
                    byte[] frame = MediaFrame.seal(p.senderKey, new MediaFrame.Header(MediaFrame.FLAG_VAD,
                            (int) p.routeId, p.ssrc, seq, seq * 960, level, 0), opus);
                    p.node.sendDatagram(p.connection, frame);
                    synchronized (p.pending) {
                        if (p.pendingFirst < 0) p.pendingFirst = seq;
                        p.pending.add(frame);
                        if (p.pending.size() == 1000 / FRAME_MS) {
                            byte[] att = Attestation.sign(p.tPriv, meetingId, (int) p.routeId, p.ssrc, p.pendingFirst,
                                    new ArrayList<>(p.pending)).toBytes();
                            p.pending.clear();
                            p.pendingFirst = -1;
                            try {
                                p.node.sendNotify(relayFid, att, 0);
                            } catch (Exception ignored) {
                                // counted as missing on the far side
                            }
                        }
                    }
                }, s * 3L, FRAME_MS, TimeUnit.MILLISECONDS);
            }

            Thread.sleep(warmup * 1000L);
            long[] before = new long[n], bytesBefore = new long[n], attBefore = new long[n];
            for (int i = 0; i < n; i++) {
                before[i] = all.get(i).framesIn.get();
                bytesBefore[i] = all.get(i).bytesIn.get();
                attBefore[i] = all.get(i).attestationsIn.get();
            }
            long windowStart = System.currentTimeMillis();
            System.out.printf("[bench] window starts %d (epoch ms), %d s%n", windowStart, seconds);
            Thread.sleep(seconds * 1000L);
            long windowMs = System.currentTimeMillis() - windowStart;

            double expectedPerListener = speakers * windowMs / (double) FRAME_MS;
            double worst = 1, sum = 0;
            long bytes = 0, atts = 0;
            for (int i = 0; i < n; i++) {
                boolean speaks = i >= 1 && i <= speakers;
                double expected = speaks ? expectedPerListener * (speakers - 1) / speakers : expectedPerListener;
                double got = (all.get(i).framesIn.get() - before[i]) / expected;
                worst = Math.min(worst, got);
                sum += got;
                bytes += all.get(i).bytesIn.get() - bytesBefore[i];
                atts += all.get(i).attestationsIn.get() - attBefore[i];
            }
            System.out.printf("[bench] frames delivered: mean %.4f, worst %.4f of expected%n", sum / n, worst);
            System.out.printf("[bench] payload out of the relay: %.1f kbps in total, %.1f kbps per listener%n",
                    bytes * 8.0 / windowMs, bytes * 8.0 / windowMs / n);
            System.out.printf("[bench] attestations received: %.1f per s in total%n", atts * 1000.0 / windowMs);
            assertTrue(sum / n > 0.95, "most frames arrive");
        } finally {
            clock.shutdownNow();
            for (Participant p : all) if (p.node != null) p.node.stop();
        }
    }
}
