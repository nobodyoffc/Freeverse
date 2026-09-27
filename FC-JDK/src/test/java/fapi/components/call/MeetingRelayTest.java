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
    /** Frames per packed send to one receiver (§7.5). */
    final List<Integer> batches = new ArrayList<>();
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
            public int sendDatagrams(long connectionId, List<byte[]> data) {
                batches.add(data.size());
                for (byte[] d : data) sent.add(new Sent(connectionId, d));
                return data.size();
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
        return frame(m, k, seq, 40);
    }

    /** A voice frame at {@code level} -dBov (smaller is louder; 127 is silence). */
    byte[] frame(Member m, Keys k, long seq, int level) {
        byte[] senderKey = CallKeys.senderKey(k.secret, m.fid, m.ssrc, 0);
        return MediaFrame.seal(senderKey, new MediaFrame.Header(MediaFrame.FLAG_VAD, (int) m.routeId, m.ssrc, seq,
                seq * 960, level, 0), new byte[]{1, 2, 3, 4});
    }

    /**
     * {@code speakers} each send a 20 ms frame, at their level, from {@code from}
     * for {@code ms}, with selection every 100 ms. @return the time after
     */
    long talk(Keys k, Map<Member, Integer> speakers, long from, long ms) {
        for (long t = from; t < from + ms; t += 20) {
            for (Map.Entry<Member, Integer> e : speakers.entrySet()) {
                Member m = e.getKey();
                relay.onDatagram(m.connectionId, frame(m, k, seqOf.merge(m, 1L, Long::sum), e.getValue()), t);
            }
            relay.flushPacked(t + CallRelay.PACK_WAIT_MS);
            if ((t - from) % CallRelay.SELECT_EVERY_MS == 0) relay.selectSpeakers(t);
        }
        return from + ms;
    }

    final Map<Member, Long> seqOf = new HashMap<>();

    /** Who the relay forwarded {@code speaker}'s frames to, in what was sent since the last clear. */
    java.util.Set<Long> receiversOf(Member speaker) {
        java.util.Set<Long> to = new java.util.HashSet<>();
        for (Sent x : sent) {
            MediaFrame.Header h = MediaFrame.Header.parse(x.data());
            if (h != null && h.ssrc() == speaker.ssrc) to.add(x.connectionId());
        }
        return to;
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
        long t = talk(k, Map.of(b, 40), T0, 100); // b is heard, so selected
        sent.clear();
        talk(k, Map.of(b, 40), t, 20);
        assertEquals(java.util.Set.of(host.connectionId, c.connectionId), receiversOf(b));
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

    // ===== Speaker selection (§7.4) =====

    /** A meeting with {@code n} members, all joined; the first is the host. */
    List<Member> meetingOf(Keys k, int n) throws Exception {
        List<Member> ms = new ArrayList<>();
        for (int i = 0; i < n; i++) ms.add(new Member());
        create(ms.get(0), k);
        for (Member m : ms) join(m, k, k.authPriv, T0);
        return ms;
    }

    @Test
    public void onlyTheLoudestThreeAreForwarded() throws Exception {
        Keys k = keys();
        List<Member> ms = meetingOf(k, 6);
        Map<Member, Integer> voices = new LinkedHashMap<>();
        voices.put(ms.get(1), 30);
        voices.put(ms.get(2), 34);
        voices.put(ms.get(3), 38);
        voices.put(ms.get(4), 42); // the quietest of four speakers
        long t = talk(k, voices, T0, 400);
        sent.clear();
        talk(k, voices, t, 200);
        for (int i = 1; i <= 3; i++) assertFalse(receiversOf(ms.get(i)).isEmpty(), "speaker " + i + " is heard");
        assertTrue(receiversOf(ms.get(4)).isEmpty(), "a fourth, quieter voice is not forwarded");
        assertTrue(receiversOf(ms.get(5)).isEmpty(), "nor is silence");
        assertEquals(java.util.Set.of(ms.get(0).connectionId, ms.get(2).connectionId, ms.get(3).connectionId,
                ms.get(4).connectionId, ms.get(5).connectionId), receiversOf(ms.get(1)), "everyone but the speaker");
        assertTrue(((Number) relay.stats().get("framesNotSelected")).longValue() > 0);
    }

    @Test
    public void aNewVoiceTakesASlotOnlyWhenClearlyLouderForLongEnough() throws Exception {
        Keys k = keys();
        List<Member> ms = meetingOf(k, 5);
        Member a = ms.get(1), b = ms.get(2), c = ms.get(3), d = ms.get(4);
        Map<Member, Integer> voices = new LinkedHashMap<>(Map.of(a, 40, b, 40, c, 40));
        long t = talk(k, voices, T0, 400);

        voices.put(d, 36); // 4 dB louder than the weakest: not enough
        t = talk(k, voices, t, 1_000);
        sent.clear();
        t = talk(k, voices, t, 100);
        assertTrue(receiversOf(d).isEmpty(), "under 6 dB louder: no switch, however long");

        voices.put(d, 30); // 10 dB louder: after 300 ms, and not before
        t = talk(k, voices, t, 200);
        sent.clear();
        t = talk(k, voices, t, 40);
        assertTrue(receiversOf(d).isEmpty(), "not yet 300 ms louder");
        t = talk(k, voices, t, 300);
        sent.clear();
        talk(k, voices, t, 100);
        assertFalse(receiversOf(d).isEmpty(), "now it has a slot");
        long heard = List.of(a, b, c).stream().filter(x -> !receiversOf(x).isEmpty()).count();
        assertEquals(2, heard, "and one of the three it outshouted lost its slot");
    }

    @Test
    public void aSilentSpeakerYieldsItsSlot() throws Exception {
        Keys k = keys();
        List<Member> ms = meetingOf(k, 5);
        Member a = ms.get(1), b = ms.get(2), c = ms.get(3), d = ms.get(4);
        long t = talk(k, new LinkedHashMap<>(Map.of(a, 40, b, 40, c, 40)), T0, 400);
        // a stops; a quiet d starts, louder than silence by any margin.
        t = talk(k, new LinkedHashMap<>(Map.of(b, 40, c, 40, d, 60)), t, 800);
        sent.clear();
        talk(k, new LinkedHashMap<>(Map.of(b, 40, c, 40, d, 60)), t, 100);
        assertFalse(receiversOf(d).isEmpty(), "the silent speaker's slot went to one who talks");
    }

    @Test
    public void anAttestationGoesOnlyToThoseWhoGotItsFrames() throws Exception {
        Keys k = keys();
        List<Member> ms = meetingOf(k, 6);
        Member loud = ms.get(1), unheard = ms.get(5);
        Map<Member, Integer> voices = new LinkedHashMap<>();
        voices.put(loud, 30);
        voices.put(ms.get(2), 32);
        voices.put(ms.get(3), 34);
        voices.put(unheard, 50);
        talk(k, voices, T0, 600);
        notices.clear();
        for (Member m : List.of(loud, unheard)) {
            long last = seqOf.get(m);
            byte[] att = Attestation.sign(m.tPriv, k.meetingId, (int) m.routeId, m.ssrc, last - 9,
                    java.util.Collections.nCopies(10, new byte[8])).toBytes();
            relay.onNotify(m.peerId, 0, att);
        }
        long toLoud = notices.stream().filter(n -> n.dataType() == 0 && !n.peerId().equals(loud.peerId)).count();
        assertEquals(5, toLoud, "the loud speaker's attestation reaches everyone who heard it");
        assertTrue(notices.stream().noneMatch(n -> n.dataType() == 0 && java.util.Arrays.equals(n.data(),
                        Attestation.sign(unheard.tPriv, k.meetingId, (int) unheard.routeId, unheard.ssrc,
                                seqOf.get(unheard) - 9, java.util.Collections.nCopies(10, new byte[8])).toBytes())),
                "nobody got the unheard speaker's frames, so nobody needs its attestation");
        assertEquals(5, notices.stream().filter(n -> n.dataType() == 0).count(), "and no more went out");
    }

    // ===== Host controls (§7.2) =====

    Map<String, Object> control(Member by, Keys k, String action, Object target) throws CallRelay.Refused {
        Map<String, Object> p = params("meetingId", k.meetingId, "delegation", by.delegation(k.meetingId, T0),
                "action", action);
        if (target != null) p.put("target", target);
        return relay.control(by.peerId, p, T0);
    }

    @Test
    public void onlyTheHostModeratesButAnyoneMayLiftTheirOwnSoftMute() throws Exception {
        Keys k = keys();
        List<Member> ms = meetingOf(k, 3);
        Member host = ms.get(0), b = ms.get(1), c = ms.get(2);
        CallRelay.Refused notHost = assertThrows(CallRelay.Refused.class, () -> control(b, k, "mute", c.fid));
        assertEquals(403, notHost.code);

        control(host, k, "mute", b.fid);
        long t = talk(k, Map.of(b, 30), T0, 400);
        sent.clear();
        talk(k, Map.of(b, 30), t, 100);
        assertTrue(receiversOf(b).isEmpty(), "the relay drops a muted speaker's frames");
        assertTrue(notices.stream().anyMatch(n -> n.peerId().equals(b.peerId) && "muted".equals(n.json().get("type"))));

        control(b, k, "unmute", b.fid); // a soft mute: b may lift it
        t = talk(k, Map.of(b, 30), t + 100, 200);
        sent.clear();
        talk(k, Map.of(b, 30), t, 100);
        assertFalse(receiversOf(b).isEmpty());

        control(host, k, "lockMute", Integer.toUnsignedLong(b.ssrc)); // by ssrc this time
        CallRelay.Refused locked = assertThrows(CallRelay.Refused.class, () -> control(b, k, "unmute", b.fid));
        assertEquals(403, locked.code, "only the host lifts a locked mute");
        CallRelay.Refused others = assertThrows(CallRelay.Refused.class, () -> control(b, k, "unmute", c.fid));
        assertEquals(403, others.code, "nor may anyone unmute someone else");
        control(host, k, "unmute", b.fid);
    }

    @Test
    public void aKickedMemberIsOutAndMayNotComeBack() throws Exception {
        Keys k = keys();
        List<Member> ms = meetingOf(k, 3);
        Member host = ms.get(0), b = ms.get(1);
        control(host, k, "kick", b.fid);
        assertEquals(2, relay.info(k.meetingId).get("participants"));
        assertTrue(notices.stream().anyMatch(n -> n.peerId().equals(b.peerId) && "kicked".equals(n.json().get("type"))));
        // Same FID from a new transport key: still barred (the owner must rotate the symkey to stop decryption).
        Member sameFid = new Member();
        byte[] bPriv = b.fidPriv;
        CallRelay.Refused barred = assertThrows(CallRelay.Refused.class, () -> {
            Map<String, Object> p = params("meetingId", k.meetingId, "ssrc", Integer.toUnsignedLong(sameFid.ssrc),
                    "ts", T0 + 5, "delegation", Delegation.sign(bPriv, k.meetingId, sameFid.tPub, T0 / 1000 + 3600).toJson(),
                    "admitSig", Hex.toHex(CallKeys.admitSig(k.authPriv, k.meetingId, sameFid.tPub, sameFid.ssrc, T0 + 5)));
            relay.join(sameFid.peerId, sameFid.connectionId, p, T0 + 5);
        });
        assertEquals(403, barred.code);
    }

    @Test
    public void aPinIsHeardHoweverQuietAndPinsAreFew() throws Exception {
        Keys k = keys();
        List<Member> ms = meetingOf(k, 7);
        Member host = ms.get(0), quiet = ms.get(6);
        Map<Member, Integer> voices = new LinkedHashMap<>();
        voices.put(ms.get(1), 30);
        voices.put(ms.get(2), 32);
        voices.put(ms.get(3), 34);
        voices.put(quiet, 60);
        control(host, k, "pin", quiet.fid);
        long t = talk(k, voices, T0, 400);
        sent.clear();
        talk(k, voices, t, 100);
        assertFalse(receiversOf(quiet).isEmpty(), "pinned: forwarded though far from the loudest three");
        control(host, k, "pin", ms.get(4).fid);
        CallRelay.Refused third = assertThrows(CallRelay.Refused.class, () -> control(host, k, "pin", ms.get(5).fid));
        assertEquals(429, third.code);
        control(host, k, "unpin", quiet.fid);
        control(host, k, "pin", ms.get(5).fid);
    }

    @Test
    public void theHostHandsOverAndEnds() throws Exception {
        Keys k = keys();
        List<Member> ms = meetingOf(k, 3);
        Member host = ms.get(0), b = ms.get(1);
        control(host, k, "handoverHost", b.fid);
        CallRelay.Refused former = assertThrows(CallRelay.Refused.class, () -> control(host, k, "mute", b.fid));
        assertEquals(403, former.code, "the former host is a member now");
        notices.clear();
        control(b, k, "end", null);
        assertEquals(false, relay.info(k.meetingId).get("open"));
        assertEquals(3, notices.stream().filter(n -> n.dataType() == 1 && "ended".equals(n.json().get("type"))).count(),
                "everyone is told");
    }

    @Test
    public void aRaisedHandShowsInTheRoster() throws Exception {
        Keys k = keys();
        List<Member> ms = meetingOf(k, 3);
        Member b = ms.get(1);
        notices.clear();
        relay.hand(b.peerId, params("meetingId", k.meetingId, "delegation", b.delegation(k.meetingId, T0),
                "raised", true), T0);
        Notice roster = notices.stream().filter(n -> n.dataType() == 1 && "roster".equals(n.json().get("type")))
                .reduce((x, y) -> y).orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) roster.json().get("roster");
        assertTrue(entries.stream().anyMatch(e -> b.fid.equals(e.get("fid")) && Boolean.TRUE.equals(e.get("hand"))));
    }

    // ===== Rekeying (§4.5) =====

    Map<String, Object> prove(Member m, Keys k, byte[] authPriv, int epoch, long now) throws CallRelay.Refused {
        return relay.prove(m.peerId, params("meetingId", k.meetingId, "delegation", m.delegation(k.meetingId, now),
                "keyEpoch", epoch, "ts", now,
                "admitSig", Hex.toHex(CallKeys.admitSig(authPriv, k.meetingId, m.tPub, m.ssrc, now))), now);
    }

    @Test
    public void aRekeyDropsOnlyThoseWhoCannotProveTheNewKey() throws Exception {
        Keys k = keys();
        List<Member> ms = meetingOf(k, 4);
        Member host = ms.get(0), keeps = ms.get(1), removed = ms.get(2), slow = ms.get(3);
        // The owner rotated the symkey (removed is out); the host follows with the new admission key.
        byte[] newSecret = CallKeys.meetingSecret(key(), key(), "FTeamEntity", 4, k.meetingId);
        byte[] newAuthPriv = CallKeys.authPriv(newSecret);
        notices.clear();
        Map<String, Object> r = relay.rekey(host.peerId, params("meetingId", k.meetingId,
                "delegation", host.delegation(k.meetingId, T0), "symkeyVersion", 4, "nonce", Hex.toHex(key()),
                "authPub", Hex.toHex(CallKeys.authPub(newAuthPriv))), T0);
        int epoch = ((Number) r.get("keyEpoch")).intValue();
        assertEquals(1, epoch);
        assertEquals(4, notices.stream().filter(n -> n.dataType() == 1 && "rekey".equals(n.json().get("type"))).count(),
                "everyone is told");

        prove(host, k, newAuthPriv, epoch, T0 + 1_000);
        prove(keeps, k, newAuthPriv, epoch, T0 + 2_000);
        CallRelay.Refused old = assertThrows(CallRelay.Refused.class,
                () -> prove(removed, k, k.authPriv, epoch, T0 + 3_000));
        assertEquals(401, old.code, "the old key proves nothing");
        prove(slow, k, newAuthPriv, epoch, T0 + 29_000); // it had to ask for the new symkey first

        // A newcomer is admitted under the new key only.
        Member late = new Member();
        CallRelay.Refused oldJoin = assertThrows(CallRelay.Refused.class, () -> join(late, k, k.authPriv, T0 + 5_000));
        assertEquals(401, oldJoin.code);
        join(late, k, newAuthPriv, T0 + 5_000);

        notices.clear();
        relay.tick(T0 + CallRelay.PROVE_WITHIN_MS - 1);
        assertEquals(5, relay.info(k.meetingId).get("participants"), "30 s to prove");
        relay.tick(T0 + CallRelay.PROVE_WITHIN_MS);
        assertEquals(4, relay.info(k.meetingId).get("participants"), "only the one without the new key is gone");
        assertTrue(notices.stream().anyMatch(n -> n.peerId().equals(removed.peerId)
                && "kicked".equals(n.json().get("type")) && "rekey".equals(n.json().get("reason"))));
    }

    @Test
    public void onlyTheHostRekeys() throws Exception {
        Keys k = keys();
        List<Member> ms = meetingOf(k, 2);
        CallRelay.Refused notHost = assertThrows(CallRelay.Refused.class, () -> relay.rekey(ms.get(1).peerId,
                params("meetingId", k.meetingId, "delegation", ms.get(1).delegation(k.meetingId, T0),
                        "symkeyVersion", 4, "nonce", Hex.toHex(key()), "authPub", Hex.toHex(CallKeys.authPub(key()))), T0));
        assertEquals(403, notHost.code);
    }

    // ===== Adapting to each receiver, and uplink reports (§7.2, §7.4) =====

    Map<String, Object> report(Member m, Keys k, double loss, long now) throws CallRelay.Refused {
        return relay.report(m.peerId, params("meetingId", k.meetingId, "delegation", m.delegation(k.meetingId, now),
                "streams", Map.of("1", Map.of("loss", loss, "lateLoss", 0.0, "jitterMs", 30))), now);
    }

    @Test
    public void aLossyReceiverGetsFewerSpeakersThenRecovers() throws Exception {
        Keys k = keys();
        List<Member> ms = meetingOf(k, 5);
        Member listener = ms.get(4);
        Member loudest = ms.get(1), second = ms.get(2), third = ms.get(3);
        Map<Member, Integer> voices = new LinkedHashMap<>(Map.of(loudest, 30, second, 36, third, 42));
        long t = talk(k, voices, T0, 400);

        assertEquals(2, report(listener, k, 0.2, t).get("speakers"));
        sent.clear();
        t = talk(k, voices, t, 100);
        assertTrue(receiversOf(third).stream().noneMatch(c -> c == listener.connectionId),
                "the quietest of the three no longer reaches the lossy receiver");
        assertTrue(receiversOf(loudest).contains(listener.connectionId));
        assertTrue(receiversOf(third).contains(ms.get(0).connectionId), "others still get all three");

        assertEquals(1, report(listener, k, 0.2, t).get("speakers"));
        assertEquals(1, report(listener, k, 0.5, t).get("speakers"), "never below one");
        assertEquals(1, report(listener, k, 0.01, t + 1).get("speakers"), "a clean report alone restores nothing");
        relay.tick(t + CallRelay.RECOVER_AFTER_MS);
        relay.tick(t + 2 * CallRelay.RECOVER_AFTER_MS);
        sent.clear();
        talk(k, voices, t + 2 * CallRelay.RECOVER_AFTER_MS, 100);
        assertTrue(receiversOf(third).contains(listener.connectionId), "10 s at a time, back to three");
    }

    @Test
    public void eachSenderHearsItsUplinkLossButDtxPausesAreNotLoss() throws Exception {
        Keys k = keys();
        List<Member> ms = meetingOf(k, 2);
        Member speaker = ms.get(1);
        byte[] senderKey = CallKeys.senderKey(k.secret, speaker.fid, speaker.ssrc, 0);
        long t = T0;
        for (long seq = 1; seq <= 40; seq++, t += 20) {
            if (seq == 11 || seq == 12) continue;             // lost on the way: 2 of 40
            if (seq >= 21 && seq <= 29) continue;             // not sent: DTX
            int flags = MediaFrame.FLAG_VAD | (seq == 30 ? MediaFrame.FLAG_DTX : 0); // the first after the pause
            relay.onDatagram(speaker.connectionId, MediaFrame.seal(senderKey, new MediaFrame.Header(flags,
                    (int) speaker.routeId, speaker.ssrc, seq, seq * 960, 40, 0), new byte[]{1}), t);
        }
        notices.clear();
        relay.tick(t + CallRelay.UPLINK_EVERY_MS);
        Map<String, Object> up = notices.stream().filter(n -> n.peerId().equals(speaker.peerId)
                && "uplink".equals(n.json().get("type"))).findFirst().orElseThrow().json();
        double loss = ((Number) up.get("loss")).doubleValue();
        assertEquals(2.0 / 31, loss, 0.002, "2 lost of the 31 sent; the DTX pause is no loss");
    }

    // ===== Packing (§7.5) =====

    @Test
    public void aReceiverGetsItsSpeakersOfOneMomentInOnePacket() throws Exception {
        Keys k = keys();
        List<Member> ms = meetingOf(k, 6);
        Map<Member, Integer> voices = new LinkedHashMap<>(Map.of(ms.get(1), 30, ms.get(2), 34, ms.get(3), 38));
        long t = talk(k, voices, T0, 400);
        batches.clear();
        talk(k, voices, t, 200);
        // Three listeners who get all three speakers, 10 moments: one send each, three frames in it.
        long threes = batches.stream().filter(b -> b == 3).count();
        assertTrue(threes >= 25, "the silent listeners get one packed send per moment: " + batches);
        // A speaker gets the other two speakers, packed together too.
        assertTrue(batches.stream().allMatch(b -> b >= 2), "nothing goes out alone when its moment is complete");
    }
}
