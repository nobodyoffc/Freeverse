package fapi.components.call;

import com.google.gson.Gson;
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
 * Meetings on the CALL relay (VOICE_SPEC §7, §8): {@code kind = meeting},
 * admission by {@code admitSig} for everyone, the creator paying for all
 * (Decision 14) within its cap, the host role, and the limits.
 */
public class MeetingRelayTest {

    private static final SecureRandom RNG = new SecureRandom();
    private static final long T0 = 1_790_000_000_000L;

    record Sent(long connectionId, byte[] data) {}

    record Notice(String peerId, int dataType, byte[] data) {
        @SuppressWarnings("unchecked")
        Map<String, Object> json() {
            return new Gson().fromJson(new String(data, StandardCharsets.UTF_8), Map.class);
        }
    }

    final List<Sent> sent = new ArrayList<>();
    final List<Notice> notices = new ArrayList<>();
    final Map<String, Long> charges = new LinkedHashMap<>();
    final Map<String, String> payers = new LinkedHashMap<>();
    CallRelay relay;

    /** One member: a FID, its throwaway transport key, its connection. */
    static final class Member {
        final byte[] fidPriv = key(), tPriv = key();
        final byte[] tPub = KeyTools.prikeyToPubkey(tPriv);
        final String fid = KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(fidPriv));
        final String peerId = KeyTools.pubkeyToFchAddr(tPub);
        final long connectionId = RNG.nextLong();
        final int ssrc = RNG.nextInt();
        long routeId;

        String delegation(String meetingId, long nowMs) {
            return Delegation.sign(fidPriv, meetingId, tPub, nowMs / 1000 + 3600).toJson();
        }
    }

    /** A meeting's keys: the symkey-derived secret and the admission key pair (§4.2, §4.4). */
    record Keys(String meetingId, byte[] secret, byte[] authPriv, byte[] authPub) {}

    static byte[] key() {
        byte[] k = new byte[32];
        RNG.nextBytes(k);
        return k;
    }

    static Keys keys() {
        byte[] id = new byte[12];
        RNG.nextBytes(id);
        String meetingId = "mtg_" + Hex.toHex(id);
        byte[] secret = CallKeys.meetingSecret(key(), key(), "FTeamEntity", 3, meetingId);
        byte[] authPriv = CallKeys.authPriv(secret);
        return new Keys(meetingId, secret, authPriv, CallKeys.authPub(authPriv));
    }

    static Map<String, Object> params(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @BeforeEach
    void setUp() {
        relay = new CallRelay(new CallRelay.Transport() {
            @Override
            public boolean sendDatagram(long connectionId, byte[] data) {
                sent.add(new Sent(connectionId, data));
                return true;
            }

            @Override
            public void enableDatagrams(long connectionId) {}

            @Override
            public void notify(String peerId, int dataType, byte[] data) {
                notices.add(new Notice(peerId, dataType, data));
            }
        }, new CallRelay.Billing() {
            @Override
            public boolean canAfford(String fid, long amount) {
                return true;
            }

            @Override
            public boolean charge(String key, String fid, long amount, String meta) {
                charges.putIfAbsent(key, amount);
                payers.putIfAbsent(key, fid);
                return true;
            }
        }, new CallRelay.Pricing(10, 20));
    }

    Map<String, Object> create(Member host, Keys k, Object... extra) throws CallRelay.Refused {
        Map<String, Object> p = params("meetingId", k.meetingId, "kind", "meeting",
                "authPub", Hex.toHex(k.authPub), "delegation", host.delegation(k.meetingId, T0));
        for (int i = 0; i < extra.length; i += 2) p.put((String) extra[i], extra[i + 1]);
        return relay.create(host.peerId, p, T0);
    }

    Map<String, Object> join(Member m, Keys k, byte[] authPriv, long now) throws CallRelay.Refused {
        Map<String, Object> p = params("meetingId", k.meetingId, "ssrc", Integer.toUnsignedLong(m.ssrc), "ts", now,
                "delegation", m.delegation(k.meetingId, now));
        if (authPriv != null) p.put("admitSig", Hex.toHex(CallKeys.admitSig(authPriv, k.meetingId, m.tPub, m.ssrc, now)));
        Map<String, Object> r = relay.join(m.peerId, m.connectionId, p, now);
        m.routeId = ((Number) r.get("routeId")).longValue();
        return r;
    }

    byte[] frame(Member m, Keys k, long seq) {
        byte[] senderKey = CallKeys.senderKey(k.secret, m.fid, m.ssrc, 0);
        return MediaFrame.seal(senderKey, new MediaFrame.Header(MediaFrame.FLAG_VAD, (int) m.routeId, m.ssrc, seq,
                seq * 960, 40, 0), new byte[]{1, 2, 3, 4});
    }

    // ===== Creating (§7.2, §8) =====

    @Test
    public void aMeetingHasItsOwnIdFormatAndGivesAuthPubAtCreate() throws Exception {
        Member host = new Member();
        Keys k = keys();
        CallRelay.Refused hexId = assertThrows(CallRelay.Refused.class, () -> relay.create(host.peerId,
                params("meetingId", Hex.toHex(key()).substring(0, 32), "kind", "meeting",
                        "authPub", Hex.toHex(k.authPub), "delegation", host.delegation(k.meetingId, T0)), T0));
        assertEquals(400, hexId.code, "a meeting's id is mtg_ + 24 hex");
        CallRelay.Refused noAuth = assertThrows(CallRelay.Refused.class, () -> relay.create(host.peerId,
                params("meetingId", k.meetingId, "kind", "meeting", "delegation", host.delegation(k.meetingId, T0)),
                T0));
        assertEquals(400, noAuth.code, "joiners prove the key from the start (§4.4)");

        Map<String, Object> r = create(host, k);
        assertEquals(CallRelay.MAX_PARTICIPANTS, r.get("maxParticipants"));
        assertEquals(CallRelay.DEFAULT_SPEAKERS, r.get("speakers"));
        assertEquals("meeting", relay.info(k.meetingId).get("kind"));
    }

    // ===== Admission (§4.4) =====

    @Test
    public void everyoneProvesTheKeyTheHostToo() throws Exception {
        Member host = new Member(), member = new Member(), outsider = new Member();
        Keys k = keys();
        create(host, k);
        CallRelay.Refused hostNoSig = assertThrows(CallRelay.Refused.class, () -> join(host, k, null, T0));
        assertEquals(400, hostNoSig.code, "no admitSig: not even the host");
        join(host, k, k.authPriv, T0);
        join(member, k, k.authPriv, T0 + 1);
        CallRelay.Refused wrong = assertThrows(CallRelay.Refused.class,
                () -> join(outsider, k, CallKeys.authPriv(key()), T0 + 2));
        assertEquals(401, wrong.code, "without the symkey there is no admission");
        assertEquals(2, relay.info(k.meetingId).get("participants"));
    }

    @Test
    public void aFullMeetingAndABusyHostAreRefused() throws Exception {
        Member host = new Member();
        Keys k = keys();
        create(host, k, "maxParticipants", 3);
        join(host, k, k.authPriv, T0);
        join(new Member(), k, k.authPriv, T0 + 1);
        join(new Member(), k, k.authPriv, T0 + 2);
        CallRelay.Refused full = assertThrows(CallRelay.Refused.class, () -> join(new Member(), k, k.authPriv, T0 + 3));
        assertEquals(403, full.code);

        for (int i = 1; i < CallRelay.MEETINGS_PER_HOST; i++) create(host, keys());
        CallRelay.Refused busy = assertThrows(CallRelay.Refused.class, () -> create(host, keys()));
        assertEquals(429, busy.code, "at most " + CallRelay.MEETINGS_PER_HOST + " meetings per host at once");
    }

    // ===== Forwarding (§7.3) =====

    @Test
    public void aFrameGoesToEveryoneButItsSender() throws Exception {
        Member host = new Member(), b = new Member(), c = new Member();
        Keys k = keys();
        create(host, k);
        for (Member m : List.of(host, b, c)) join(m, k, k.authPriv, T0);
        sent.clear();
        relay.onDatagram(b.connectionId, frame(b, k, 1));
        List<Long> to = sent.stream().map(Sent::connectionId).sorted().toList();
        assertEquals(List.of(host.connectionId, c.connectionId).stream().sorted().toList(), to);
    }

    // ===== Charging (§7.5, Decision 14) =====

    @Test
    public void theCreatorPaysForEveryoneWithinItsCap() throws Exception {
        Member host = new Member(), b = new Member(), c = new Member();
        Keys k = keys();
        create(host, k);
        for (Member m : List.of(host, b, c)) join(m, k, k.authPriv, T0);
        for (int i = 1; i <= 50; i++) relay.onDatagram(b.connectionId, frame(b, k, i));
        relay.tick(T0 + CallRelay.MINUTE_MS);
        assertFalse(charges.isEmpty());
        assertTrue(payers.values().stream().allMatch(host.fid::equals), "every minute is the creator's: " + payers);

        // A cap on the whole meeting's minute.
        charges.clear();
        payers.clear();
        Keys capped = keys();
        Member host2 = new Member();
        create(host2, capped, "maxCostPerMinute", 25);
        List<Member> ms = List.of(host2, new Member(), new Member());
        long t = T0;
        for (Member m : ms) join(m, capped, capped.authPriv, t);
        for (int i = 1; i <= 50; i++) relay.onDatagram(ms.get(1).connectionId, frame(ms.get(1), capped, i));
        relay.tick(t + CallRelay.MINUTE_MS);
        long total = charges.values().stream().mapToLong(Long::longValue).sum();
        assertTrue(total <= 25, "the meeting never costs more than its cap per minute: " + total);
        assertTrue(total > 0);
    }

    // ===== The host role (§7.2) =====

    @Test
    public void theHostRolePassesButTheBillStaysWithTheCreator() throws Exception {
        Member host = new Member(), b = new Member(), c = new Member();
        Keys k = keys();
        create(host, k);
        for (Member m : List.of(host, b, c)) join(m, k, k.authPriv, T0);
        notices.clear();
        relay.leave(host.connectionId, T0 + 10);
        Notice roster = notices.stream().filter(n -> n.peerId().equals(b.peerId) && n.dataType() == 1)
                .reduce((x, y) -> y).orElseThrow();
        assertEquals(b.fid, roster.json().get("host"), "present longest");
        for (int i = 1; i <= 20; i++) relay.onDatagram(c.connectionId, frame(c, k, i));
        relay.tick(T0 + CallRelay.MINUTE_MS);
        assertTrue(payers.values().stream().allMatch(host.fid::equals), "the creator agreed to pay, not the new host");
    }

    @Test
    public void anEmptyMeetingClosesAfterAMinute() throws Exception {
        Member host = new Member();
        Keys k = keys();
        create(host, k);
        join(host, k, k.authPriv, T0);
        relay.leave(host.connectionId, T0 + 1_000);
        relay.tick(T0 + 1_000 + CallRelay.EMPTY_CLOSE_MS - 1);
        assertEquals(true, relay.info(k.meetingId).get("open"));
        relay.tick(T0 + 1_000 + CallRelay.EMPTY_CLOSE_MS);
        assertEquals(false, relay.info(k.meetingId).get("open"));
    }
}
