package fapi.components.call;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import core.crypto.KeyTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import utils.Hex;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CallRelay (VOICE_SPEC §7) for 1:1 calls, driven directly: real delegations,
 * keys and sealed frames, a fake transport and billing, and a clock the test
 * controls.
 */
public class CallRelayTest {

    private static final SecureRandom RNG = new SecureRandom();
    private static final long T0 = 1_790_000_000_000L;

    // ===== Fakes =====

    record Sent(long connectionId, byte[] data) {}

    record Notice(String peerId, int dataType, byte[] data) {
        Map<String, Object> json() {
            return new Gson().fromJson(new String(data, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, Object>>() {}.getType());
        }
    }

    final List<Sent> datagrams = new ArrayList<>();
    final List<Long> enabled = new ArrayList<>();
    final List<Notice> notices = new ArrayList<>();
    final Map<String, Long> charges = new LinkedHashMap<>();
    final Map<String, String> payers = new LinkedHashMap<>();
    boolean affordable = true;
    final java.util.Set<String> unableToPay = new java.util.HashSet<>();

    CallRelay relay;

    @BeforeEach
    void setUp() {
        relay = new CallRelay(new CallRelay.Transport() {
            @Override
            public boolean sendDatagram(long connectionId, byte[] data) {
                datagrams.add(new Sent(connectionId, data));
                return true;
            }

            @Override
            public void enableDatagrams(long connectionId) {
                enabled.add(connectionId);
            }

            @Override
            public void notify(String peerId, int dataType, byte[] data) {
                notices.add(new Notice(peerId, dataType, data));
            }

            @Override
            public String peerAddress(long connectionId) {
                return "203.0.113.7:" + (40000 + (connectionId & 0xfff));
            }
        }, new CallRelay.Billing() {
            @Override
            public boolean canAfford(String fid, long amount) {
                return affordable;
            }

            @Override
            public boolean charge(String key, String fid, long amount, String meta) {
                if (unableToPay.contains(fid)) return false;
                charges.putIfAbsent(key, amount);
                payers.putIfAbsent(key, fid);
                return true;
            }
        }, new CallRelay.Pricing(10, 20));
    }

    /** One side of a call: a FID, its throwaway transport key, its FUDP connection. */
    static final class Side {
        final byte[] fidPriv = key(), tPriv = key();
        final byte[] tPub = KeyTools.prikeyToPubkey(tPriv);
        final String fid = KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(fidPriv));
        final String peerId = KeyTools.pubkeyToFchAddr(tPub); // what FUDP authenticates
        final long connectionId = RNG.nextLong();
        final int ssrc = RNG.nextInt();
        long routeId;

        String delegation(String callId, long nowMs) {
            return Delegation.sign(fidPriv, callId, tPub, nowMs / 1000 + 3600).toJson();
        }
    }

    static byte[] key() {
        byte[] k = new byte[32];
        RNG.nextBytes(k);
        return k;
    }

    static String callId() {
        byte[] b = new byte[16];
        RNG.nextBytes(b);
        return Hex.toHex(b);
    }

    Map<String, Object> params(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    void create(Side host, String callId, long now) throws CallRelay.Refused {
        relay.create(host.peerId, params("meetingId", callId, "kind", "p2p",
                "delegation", host.delegation(callId, now)), now);
    }

    Map<String, Object> join(Side s, String callId, byte[] authPriv, long now) throws CallRelay.Refused {
        return join(s, callId, authPriv, now, Map.of());
    }

    Map<String, Object> join(Side s, String callId, byte[] authPriv, long now, Map<String, Object> extra)
            throws CallRelay.Refused {
        Map<String, Object> p = params("meetingId", callId, "ssrc", Integer.toUnsignedLong(s.ssrc), "ts", now,
                "delegation", s.delegation(callId, now));
        p.putAll(extra);
        if (authPriv != null) p.put("admitSig", Hex.toHex(CallKeys.admitSig(authPriv, callId, s.tPub, s.ssrc, now)));
        Map<String, Object> r = relay.join(s.peerId, s.connectionId, p, now);
        s.routeId = ((Number) r.get("routeId")).longValue();
        return r;
    }

    byte[] frame(Side s, byte[] senderKey, long seq) {
        return MediaFrame.seal(senderKey, new MediaFrame.Header(MediaFrame.FLAG_VAD, (int) s.routeId, s.ssrc, seq,
                seq * 960, 40, 0), new byte[]{1, 2, 3, 4});
    }

    /** Caller creates and joins, callee accepts, caller registers, callee joins (§6.2). */
    record Call(Side caller, Side callee, String callId, byte[] secret, byte[] authPriv) {}

    Call establish(long now) throws CallRelay.Refused {
        Side caller = new Side(), callee = new Side();
        String callId = callId();
        create(caller, callId, now);
        join(caller, callId, null, now);
        byte[] secret = CallKeys.p2pSecret(caller.tPriv, callee.tPub, callId, caller.fid, callee.fid);
        byte[] authPriv = CallKeys.authPriv(secret);
        relay.register(caller.peerId, params("meetingId", callId, "authPub", Hex.toHex(CallKeys.authPub(authPriv)),
                "delegation", caller.delegation(callId, now)), now);
        join(callee, callId, authPriv, now);
        return new Call(caller, callee, callId, secret, authPriv);
    }

    // ===== A whole call =====

    @Test
    public void aRelayedCallForwardsEachSideToTheOther() throws Exception {
        Call c = establish(T0);
        assertEquals(List.of(c.caller.connectionId, c.callee.connectionId), enabled, "datagrams enabled at join");
        assertEquals(2, relay.info(c.callId).get("participants"));

        // The caller heard about the callee joining.
        Notice roster = notices.stream().filter(n -> n.peerId.equals(c.caller.peerId)).reduce((a, b) -> b).orElseThrow();
        assertEquals(1, roster.dataType);
        assertEquals("roster", roster.json().get("type"));
        assertEquals(2, ((List<?>) roster.json().get("roster")).size());

        byte[] callerKey = CallKeys.senderKey(c.secret, c.caller.fid, c.caller.ssrc, 0);
        byte[] f = frame(c.caller, callerKey, 1);
        relay.onDatagram(c.caller.connectionId, f);
        assertEquals(1, datagrams.size());
        assertEquals(c.callee.connectionId, datagrams.get(0).connectionId(), "to the other side only");
        assertArrayEquals(f, datagrams.get(0).data(), "unchanged");
        assertArrayEquals(new byte[]{1, 2, 3, 4}, MediaFrame.open(callerKey, datagrams.get(0).data()),
                "the callee can open it; the relay could not");

        byte[] calleeKey = CallKeys.senderKey(c.secret, c.callee.fid, c.callee.ssrc, 0);
        relay.onDatagram(c.callee.connectionId, frame(c.callee, calleeKey, 1));
        assertEquals(c.caller.connectionId, datagrams.get(1).connectionId());
    }

    @Test
    public void nobodyCanSendAsSomeoneElse() throws Exception {
        Call c = establish(T0);
        byte[] key = CallKeys.senderKey(c.secret, c.callee.fid, c.callee.ssrc, 0);
        // The caller's connection sends frames labelled with the callee's route and ssrc.
        relay.onDatagram(c.caller.connectionId, frame(c.callee, key, 1));
        relay.onDatagram(c.caller.connectionId, new byte[]{0x7E, 0, 0, 0}); // not a media frame
        relay.onDatagram(RNG.nextLong(), frame(c.caller, key, 1)); // not in any call
        assertTrue(datagrams.isEmpty());
        assertEquals(3L, relay.stats().get("framesDropped"));
    }

    @Test
    public void attestationsGoToTheOtherSideIfTheyAreTheSendersOwn() throws Exception {
        Call c = establish(T0);
        notices.clear();
        byte[] key = CallKeys.senderKey(c.secret, c.caller.fid, c.caller.ssrc, 0);
        List<byte[]> frames = List.of(frame(c.caller, key, 1), frame(c.caller, key, 2));
        byte[] a = Attestation.sign(c.caller.tPriv, c.callId, (int) c.caller.routeId, c.caller.ssrc, 1, frames).toBytes();
        relay.onNotify(c.caller.peerId, 0, a);
        assertEquals(1, notices.size());
        assertEquals(c.callee.peerId, notices.get(0).peerId);
        assertArrayEquals(a, notices.get(0).data);

        // An attestation for the callee's ssrc, sent from the caller's connection, goes nowhere.
        byte[] forged = Attestation.sign(c.caller.tPriv, c.callId, (int) c.callee.routeId, c.callee.ssrc, 1, frames).toBytes();
        relay.onNotify(c.caller.peerId, 0, forged);
        assertEquals(1, notices.size());
    }

    // ===== Admission (§4.4, §7.2) =====

    @Test
    public void theCalleeWaitsUntilTheCallerRegisters() throws Exception {
        Side caller = new Side(), callee = new Side();
        String callId = callId();
        create(caller, callId, T0);
        join(caller, callId, null, T0);
        CallRelay.Refused r = assertThrows(CallRelay.Refused.class, () -> join(callee, callId, null, T0));
        assertEquals(409, r.code, "409 until registered: the callee retries (§6.2)");
    }

    // ===== Direct-path candidates (§6.1) =====

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> candidatesOf(Map<String, Object> joinResult, String fid) {
        for (Map<String, Object> e : (List<Map<String, Object>>) joinResult.get("roster")) {
            if (fid.equals(e.get("fid"))) return (List<Map<String, Object>>) e.get("candidates");
        }
        throw new AssertionError("not in the roster: " + fid);
    }

    @Test
    public void candidatesAreSharedOnlyWhenTheJoinerAsks() throws Exception {
        Side caller = new Side(), callee = new Side();
        String callId = callId();
        create(caller, callId, T0);
        Map<String, Object> r = join(caller, callId, null, T0, Map.of("reflexive", true,
                "candidates", List.of(Map.of("t", "lan", "a", "192.168.1.20:50000"))));
        List<Map<String, Object>> c = candidatesOf(r, caller.fid);
        assertEquals(2, c.size());
        assertEquals(Map.of("t", "lan", "a", "192.168.1.20:50000"), c.get(0));
        assertEquals("map", c.get(1).get("t"), "the address the relay sees, as a MAP server would");
        assertTrue(((String) c.get(1).get("a")).startsWith("203.0.113.7:"));

        byte[] secret = key();
        relay.register(caller.peerId, params("meetingId", callId, "delegation", caller.delegation(callId, T0),
                "authPub", Hex.toHex(CallKeys.authPub(CallKeys.authPriv(secret)))), T0);
        Map<String, Object> r2 = join(callee, callId, CallKeys.authPriv(secret), T0);
        assertNull(candidatesOf(r2, callee.fid), "shared nothing: shows nothing");
        assertEquals(2, candidatesOf(r2, caller.fid).size(), "the callee learns the caller's");
    }

    @Test
    public void aCandidateMustBeAnAddress() throws Exception {
        Side caller = new Side();
        String callId = callId();
        create(caller, callId, T0);
        for (Object bad : List.of(Map.of("t", "lan", "a", "evil.example:80"), Map.of("t", "map", "a", "1.2.3.4:5"),
                Map.of("t", "lan", "a", "1.2.3.4:0"), "1.2.3.4:5")) {
            CallRelay.Refused e = assertThrows(CallRelay.Refused.class,
                    () -> join(caller, callId, null, T0, Map.of("candidates", List.of(bad))));
            assertEquals(400, e.code, String.valueOf(bad));
        }
    }

    @Test
    public void aJoinRetriedAfterALostReplyGetsTheSameAnswer() throws Exception {
        Call c = establish(T0);
        long routeId = c.callee.routeId;
        notices.clear();
        Map<String, Object> again = join(c.callee, c.callId, c.authPriv, T0 + 5_000);
        assertEquals(routeId, ((Number) again.get("routeId")).longValue(), "the same routeId as the join that took");
        assertEquals(2, relay.info(c.callId).get("participants"), "not joined twice");
        assertTrue(notices.isEmpty(), "nobody is told of a join that changed nothing");
    }

    @Test
    public void anEarlyJoinKnocksOnTheHost() throws Exception {
        Side caller = new Side(), callee = new Side();
        String callId = callId();
        create(caller, callId, T0);
        join(caller, callId, null, T0);
        notices.clear();
        assertThrows(CallRelay.Refused.class, () -> join(callee, callId, null, T0));
        assertEquals(1, notices.size(), "the host hears who is waiting (§6.2 step 3)");
        Notice knock = notices.get(0);
        assertEquals(caller.peerId, knock.peerId);
        assertEquals(1, knock.dataType);
        Map<String, Object> body = knock.json();
        assertEquals("knock", body.get("type"));
        assertEquals(callId, body.get("meetingId"));
        assertEquals(callee.fid, body.get("fid"));
        Delegation d = Delegation.fromJson((String) body.get("delegation"));
        assertEquals(Delegation.Check.OK, d.verify(callId, T0 / 1000), "the callee's own signed delegation");
    }

    @Test
    public void afterRegistrationEveryJoinNeedsTheAdmissionKey() throws Exception {
        Call c = establish(T0);
        Side intruder = new Side();
        CallRelay.Refused noSig = assertThrows(CallRelay.Refused.class, () -> join(intruder, c.callId, null, T0));
        assertEquals(400, noSig.code);
        byte[] wrongAuth = CallKeys.authPriv(key());
        CallRelay.Refused badSig = assertThrows(CallRelay.Refused.class, () -> join(intruder, c.callId, wrongAuth, T0));
        assertEquals(401, badSig.code);
    }

    @Test
    public void aDelegationMustBeForThisConnectionAndThisCall() throws Exception {
        Side caller = new Side(), other = new Side();
        String callId = callId();
        // Signed for another transport key than the connection's.
        String foreign = Delegation.sign(caller.fidPriv, callId, other.tPub, T0 / 1000 + 3600).toJson();
        CallRelay.Refused r1 = assertThrows(CallRelay.Refused.class, () -> relay.create(caller.peerId,
                params("meetingId", callId, "kind", "p2p", "delegation", foreign), T0));
        assertEquals(401, r1.code);
        // For another call.
        CallRelay.Refused r2 = assertThrows(CallRelay.Refused.class, () -> relay.create(caller.peerId,
                params("meetingId", callId, "kind", "p2p", "delegation", caller.delegation(callId(), T0)), T0));
        assertEquals(401, r2.code);
        // Expired.
        String expired = Delegation.sign(caller.fidPriv, callId, caller.tPub, T0 / 1000 - 1).toJson();
        CallRelay.Refused r3 = assertThrows(CallRelay.Refused.class, () -> relay.create(caller.peerId,
                params("meetingId", callId, "kind", "p2p", "delegation", expired), T0));
        assertEquals(401, r3.code);
    }

    @Test
    public void onlyTheHostRegisters() throws Exception {
        Side caller = new Side(), callee = new Side();
        String callId = callId();
        create(caller, callId, T0);
        CallRelay.Refused r = assertThrows(CallRelay.Refused.class, () -> relay.register(callee.peerId,
                params("meetingId", callId, "authPub", Hex.toHex(KeyTools.prikeyToPubkey(key())),
                        "delegation", callee.delegation(callId, T0)), T0));
        assertEquals(403, r.code);
    }

    @Test
    public void joinsAreFreshAndSsrcsUnique() throws Exception {
        Call c = establish(T0);
        relay.leave(c.callee.connectionId, T0 + 5); // make room, so only the clock can refuse
        Side late = new Side();
        long skewed = T0 - CallRelay.CLOCK_SKEW_MS - 1;
        Map<String, Object> old = params("meetingId", c.callId, "ssrc", Integer.toUnsignedLong(late.ssrc),
                "ts", skewed, "delegation", late.delegation(c.callId, T0), "admitSig",
                Hex.toHex(CallKeys.admitSig(c.authPriv, c.callId, late.tPub, late.ssrc, skewed)));
        CallRelay.Refused skew = assertThrows(CallRelay.Refused.class,
                () -> relay.join(late.peerId, late.connectionId, old, T0 + 5));
        assertEquals(400, skew.code);

        // The callee replays its exact join.
        CallRelay.Refused replay = assertThrows(CallRelay.Refused.class, () -> join(c.callee, c.callId, c.authPriv, T0));
        assertEquals(409, replay.code);

        Side sameSsrc = new Side();
        Map<String, Object> p = params("meetingId", c.callId, "ssrc", Integer.toUnsignedLong(c.caller.ssrc),
                "ts", T0 + 20, "delegation", sameSsrc.delegation(c.callId, T0 + 20), "admitSig",
                Hex.toHex(CallKeys.admitSig(c.authPriv, c.callId, sameSsrc.tPub, c.caller.ssrc, T0 + 20)));
        CallRelay.Refused dup = assertThrows(CallRelay.Refused.class,
                () -> relay.join(sameSsrc.peerId, sameSsrc.connectionId, p, T0 + 20));
        assertEquals(409, dup.code);
    }

    @Test
    public void aFullCallAndAnEmptyWalletAreRefused() throws Exception {
        Call c = establish(T0);
        CallRelay.Refused full = assertThrows(CallRelay.Refused.class, () -> join(new Side(), c.callId, c.authPriv, T0 + 1));
        assertEquals(403, full.code, "p2p holds two");

        Side caller = new Side();
        String callId = callId();
        create(caller, callId, T0);
        affordable = false;
        CallRelay.Refused broke = assertThrows(CallRelay.Refused.class, () -> join(caller, callId, null, T0));
        assertEquals(402, broke.code);
        CallRelay.Refused cannotCreate = assertThrows(CallRelay.Refused.class, () -> create(new Side(), callId(), T0));
        assertEquals(402, cannotCreate.code, "the caller must afford a minute of both sides before it rings");
    }

    // ===== Billing (§7.5) =====

    @Test
    public void eachMinuteIsChargedOnceForItsTraffic() throws Exception {
        Call c = establish(T0);
        byte[] key = CallKeys.senderKey(c.secret, c.caller.fid, c.caller.ssrc, 0);
        long bytes = 0;
        for (int i = 1; i <= 20; i++) {
            byte[] f = frame(c.caller, key, i);
            bytes += f.length;
            relay.onDatagram(c.caller.connectionId, f);
        }
        relay.tick(T0 + CallRelay.MINUTE_MS);
        relay.tick(T0 + CallRelay.MINUTE_MS + 500); // a second tick in the same minute charges nothing more

        long kb = (bytes + 1023) / 1024;
        String callerKey = "call:" + c.callId + ":" + c.caller.fid + ":" + Integer.toUnsignedString(c.caller.ssrc) + ":0";
        String calleeKey = "call:" + c.callId + ":" + c.callee.fid + ":" + Integer.toUnsignedString(c.callee.ssrc) + ":0";
        assertEquals(kb * 10, charges.get(callerKey), "the caller's traffic in");
        assertEquals(kb * 20, charges.get(calleeKey), "the callee's traffic out");
        assertEquals(2, charges.size());
        assertEquals(c.caller.fid, payers.get(callerKey));
        assertEquals(c.caller.fid, payers.get(calleeKey), "the caller pays for the callee too (§7.5)");
    }

    @Test
    public void aCallerWhoCannotPayIsWarnedThenTheCallEnds() throws Exception {
        Call c = establish(T0);
        byte[] key = CallKeys.senderKey(c.secret, c.caller.fid, c.caller.ssrc, 0);
        relay.onDatagram(c.caller.connectionId, frame(c.caller, key, 1));
        unableToPay.add(c.caller.fid);
        notices.clear();
        relay.tick(T0 + CallRelay.MINUTE_MS);
        assertTrue(notices.stream().anyMatch(n -> n.peerId.equals(c.caller.peerId)
                && "balance".equals(n.json().get("type"))));
        assertEquals(2, relay.info(c.callId).get("participants"), "a grace period first");

        relay.tick(T0 + CallRelay.MINUTE_MS + CallRelay.UNPAID_GRACE_MS);
        assertTrue(notices.stream().anyMatch(n -> n.peerId.equals(c.caller.peerId)
                && "kicked".equals(n.json().get("type"))));
        assertTrue(notices.stream().anyMatch(n -> n.peerId.equals(c.callee.peerId)
                && "kicked".equals(n.json().get("type"))));
        assertEquals(0, relay.info(c.callId).get("participants"), "the caller pays for both: the call ends");
    }

    // ===== Lifecycle and limits (§7.2, §7.6) =====

    @Test
    public void theHostRolePassesAndAnEmptyCallCloses() throws Exception {
        Call c = establish(T0);
        notices.clear();
        relay.leave(c.caller.connectionId, T0 + 1000);
        Notice roster = notices.stream().filter(n -> n.peerId.equals(c.callee.peerId)).findFirst().orElseThrow();
        assertEquals(c.callee.fid, roster.json().get("host"), "the one present longest becomes host");

        relay.leave(c.callee.connectionId, T0 + 2000);
        relay.tick(T0 + 2000 + CallRelay.EMPTY_CLOSE_MS - 1);
        assertEquals(true, relay.info(c.callId).get("open"));
        relay.tick(T0 + 2000 + CallRelay.EMPTY_CLOSE_MS);
        assertEquals(false, relay.info(c.callId).get("open"));
    }

    @Test
    public void aHostHasAtMostFourCallsOpen() throws Exception {
        Side host = new Side();
        for (int i = 0; i < CallRelay.MEETINGS_PER_HOST; i++) create(host, callId(), T0);
        CallRelay.Refused r = assertThrows(CallRelay.Refused.class, () -> create(host, callId(), T0));
        assertEquals(429, r.code);
    }

    @Test
    public void joinAttemptsAreRateLimitedPerKey() throws Exception {
        Side caller = new Side();
        String callId = callId();
        create(caller, callId, T0);
        for (int i = 0; i < CallRelay.JOINS_PER_KEY_PER_MINUTE; i++) {
            long t = T0 + i;
            join(caller, callId, null, t);
            relay.leave(caller.connectionId, t);
        }
        CallRelay.Refused r = assertThrows(CallRelay.Refused.class, () -> join(caller, callId, null, T0 + 100));
        assertEquals(429, r.code);
    }

    @Test
    public void onlyP2pIsServedForNow() {
        Side host = new Side();
        String id = callId();
        CallRelay.Refused r = assertThrows(CallRelay.Refused.class, () -> relay.create(host.peerId,
                params("meetingId", id, "kind", "meeting", "delegation", host.delegation(id, T0)), T0));
        assertEquals(400, r.code);
    }
}
