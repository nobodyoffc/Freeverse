package fapi.components.call;

import core.crypto.KeyTools;
import utils.Hex;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The CALL relay's state and rules (VOICE_SPEC §7), with no FAPI or FUDP in
 * it: {@link CallComponent} maps requests and node events onto it, and tests
 * drive it directly. It serves 1:1 calls ({@code kind = p2p}) and meetings
 * ({@code kind = meeting}, VOICE_SPEC §8), whose creator pays for everyone
 * (Decision 14). Speaker selection, host controls and rekeying are Phase 4
 * milestones still to come.
 * <p>
 * Identity: every connection authenticates as a throwaway transport key
 * ({@code tPub}), and every request carries a {@link Delegation} from a FID
 * to that key. The relay admits, bills and names participants by the
 * delegated FID, and only after checking the delegation is for this very
 * connection.
 * <p>
 * Thread-safety: requests and {@link #tick} synchronize on the relay;
 * {@link #onDatagram} runs on the node's receive thread and reads only
 * concurrent maps and per-participant atomics, so it never waits for them.
 */
public final class CallRelay {

    public static final int MAX_PARTICIPANTS = 64;
    public static final int P2P_DEFAULT_PARTICIPANTS = 2;
    public static final int MEETINGS_PER_HOST = 4;
    public static final int JOINS_PER_KEY_PER_MINUTE = 10;
    public static final int MAX_CANDIDATES = 8;
    public static final long CLOCK_SKEW_MS = 60_000;
    public static final long EMPTY_CLOSE_MS = 60_000;
    public static final long MINUTE_MS = 60_000;
    public static final long UNPAID_GRACE_MS = 60_000;
    /** Inbound datagram budget per participant (§7.6): 64 kbps, 250 ms of burst. */
    public static final long INBOUND_BPS = 64_000;
    /** A 1:1 call forwards one speaker to each side. */
    public static final int P2P_SPEAKERS = 1;
    /** A meeting forwards the top N speakers to each participant (§7.4). */
    public static final int DEFAULT_SPEAKERS = 3, MAX_SPEAKERS = 5;
    /** Speaker selection (§7.4): ranked every 100 ms on the level of the last 300 ms of voice. */
    public static final long SELECT_EVERY_MS = 100, SCORE_WINDOW_MS = 300;
    /** A newcomer displaces a selected speaker once it has been this much louder for {@link #SCORE_WINDOW_MS}. */
    public static final double DISPLACE_DB = 6;
    /** Forwarded seqs remembered per receiver and speaker, for passing on attestations (§7.3 step 6). */
    static final int FORWARD_LOG = 512;
    /** Bytes per participant per minute at 24 kbps with FUDP overhead (§7.5), for the join balance check. */
    static final long EST_BYTES_IN_PER_MINUTE = 400 * 1024;

    // ===== Collaborators =====

    /** What the relay needs from FUDP. */
    public interface Transport {
        /** @return true if handed to the socket */
        boolean sendDatagram(long connectionId, byte[] data);

        void enableDatagrams(long connectionId);

        /** A reliable push: attestations use dataType 0, relay notices dataType 1 (JSON). */
        void notify(String peerId, int dataType, byte[] data);

        /** The ip:port this connection's packets come from, as the relay sees it; null if unknown. */
        default String peerAddress(long connectionId) {
            return null;
        }
    }

    /** What the relay needs from FAPI4 billing. */
    public interface Billing {
        boolean canAfford(String fid, long amount);

        /** Idempotent on {@code key}. @return false if the charge was refused (credit exhausted) */
        boolean charge(String key, String fid, long amount, String meta);
    }

    public record Pricing(long perKBIn, long perKBOut) {
        long cost(long bytesIn, long bytesOut) {
            return ceilKB(bytesIn) * perKBIn + ceilKB(bytesOut) * perKBOut;
        }

        private static long ceilKB(long bytes) {
            return (bytes + 1023) / 1024;
        }
    }

    /** A refused request: an FAPI code and a reason. */
    public static final class Refused extends Exception {
        public final int code;

        Refused(int code, String reason) {
            super(reason);
            this.code = code;
        }
    }

    // FapiCode values, repeated so this class stays free of FAPI.
    static final int BAD_REQUEST = 400, UNAUTHORIZED = 401, PAYMENT_REQUIRED = 402, FORBIDDEN = 403,
            NOT_FOUND = 404, CONFLICT = 409, TOO_MANY_REQUESTS = 429;

    // ===== State =====

    final class Participant {
        final String fid;
        final String peerId;
        final long connectionId;
        final int ssrc;
        final int routeId;
        final long joinedAtMs;
        final String delegationJson;
        final long maxCostPerMinute;
        final AtomicLong bytesIn = new AtomicLong(), bytesOut = new AtomicLong();
        final AtomicLong framesDropped = new AtomicLong();
        long minuteIndex;
        long unpaidSinceMs = -1;
        /** Shared only if the participant asked (§6.1). */
        List<Map<String, String>> candidates;
        // inbound token bucket, touched only on the receive thread
        double tokens = INBOUND_BPS / 8.0 / 4;
        long lastRefillMs = -1;
        // loudness of recent voice frames, for speaker selection (§7.4)
        private final long[] voiceAtMs = new long[32];
        private final int[] voiceDb = new int[32];
        private int voicePos;
        /** Selection thread only: since when this unselected speaker has been louder enough to displace. */
        long louderSinceMs = -1;
        /** What was forwarded to this receiver, per speaker ssrc: which attestations it needs (§5.1). */
        final Map<Integer, ForwardLog> forwarded = new ConcurrentHashMap<>();

        /** A voice frame's level, -dBov as the header carries it (127 is silence). */
        synchronized void noteVoice(long nowMs, int level) {
            voiceAtMs[voicePos] = nowMs;
            voiceDb[voicePos] = -level;
            voicePos = (voicePos + 1) % voiceAtMs.length;
        }

        /** Mean level in dB of the voice frames of the last 300 ms; NEGATIVE_INFINITY if there were none. */
        synchronized double score(long nowMs) {
            long sum = 0;
            int n = 0;
            for (int i = 0; i < voiceAtMs.length; i++) {
                if (voiceAtMs[i] > 0 && nowMs - voiceAtMs[i] <= SCORE_WINDOW_MS) {
                    sum += voiceDb[i];
                    n++;
                }
            }
            return n == 0 ? Double.NEGATIVE_INFINITY : (double) sum / n;
        }

        Participant(String fid, String peerId, long connectionId, int ssrc, int routeId, long now,
                    String delegationJson, long maxCostPerMinute) {
            this.fid = fid;
            this.peerId = peerId;
            this.connectionId = connectionId;
            this.ssrc = ssrc;
            this.routeId = routeId;
            this.joinedAtMs = now;
            this.delegationJson = delegationJson;
            this.maxCostPerMinute = maxCostPerMinute;
        }

        boolean withinInboundBudget(int bytes, long nowMs) {
            double capacity = INBOUND_BPS / 8.0 / 4;
            if (lastRefillMs >= 0) {
                tokens = Math.min(capacity, tokens + Math.max(0, nowMs - lastRefillMs) / 1e3 * INBOUND_BPS / 8.0);
            }
            lastRefillMs = nowMs;
            if (tokens < bytes) return false;
            tokens -= bytes;
            return true;
        }

        Map<String, Object> rosterEntry() {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("fid", fid);
            e.put("ssrc", Integer.toUnsignedLong(ssrc));
            e.put("routeId", Integer.toUnsignedLong(routeId));
            e.put("delegation", delegationJson);
            if (candidates != null) e.put("candidates", candidates);
            return e;
        }
    }

    /** The seqs of one speaker's frames forwarded to one receiver, most recent {@link #FORWARD_LOG}. */
    static final class ForwardLog {
        private final long[] seqs = new long[FORWARD_LOG];
        private int count, pos;

        synchronized void add(long seq) {
            seqs[pos] = seq;
            pos = (pos + 1) % seqs.length;
            if (count < seqs.length) count++;
        }

        synchronized boolean anyIn(long first, long last) {
            for (int i = 0; i < count; i++) {
                if (seqs[i] >= first && seqs[i] <= last) return true;
            }
            return false;
        }
    }

    final class Meeting {
        final String id;
        final String kind;
        /** Fixed at create, unlike the host role, which can pass on. */
        final String creatorFid;
        /** Speakers forwarded to each participant (§7.4). */
        final int speakers;
        /** A meeting's cap on its whole charge per minute (§7.5); 0 for none. */
        long maxCostPerMinute;
        /** The speakers forwarded now (§7.4): replaced whole, read on the receive thread. */
        volatile java.util.Set<Integer> selected = java.util.Set.of();
        /** Forwarded whatever their level: the host's pins (§7.2). */
        final java.util.Set<Integer> pinned = ConcurrentHashMap.newKeySet();

        /** Whether a speaker's frames go out now. A 1:1 call forwards everything. */
        boolean forwards(int ssrc) {
            return !isMeeting() || selected.contains(ssrc) || pinned.contains(ssrc);
        }
        long capWindow = -1, capSpent;
        String hostFid;
        byte[] authPub;
        final int keyEpoch = 0;
        final int maxParticipants;
        final long createdMs;
        long emptySinceMs;
        /** In join order: the first is present longest. */
        final Map<Long, Participant> byConnection = new ConcurrentHashMap<>();
        final List<Participant> order = new ArrayList<>();

        /**
         * Who pays for a participant's traffic (§7.5): whoever created the call,
         * for everyone. In a 1:1 call that is the caller (Decision 12), in a
         * meeting its first host (Decision 14), even after the role passes on.
         * Nobody needs an account at the relay just to take part.
         */
        String payerFor(String participantFid) {
            return creatorFid;
        }

        boolean isMeeting() {
            return "meeting".equals(kind);
        }

        Meeting(String id, String kind, String hostFid, int maxParticipants, long now) {
            this.id = id;
            this.creatorFid = hostFid;
            this.kind = kind;
            this.speakers = "meeting".equals(kind) ? DEFAULT_SPEAKERS : P2P_SPEAKERS;
            this.hostFid = hostFid;
            this.maxParticipants = maxParticipants;
            this.createdMs = now;
            this.emptySinceMs = now;
        }
    }

    private final Transport transport;
    private final Billing billing;
    private final Pricing pricing;
    private final SecureRandom random = new SecureRandom();

    private final Map<String, Meeting> meetings = new HashMap<>();
    /** Every participant by connection, for the receive thread. */
    private final Map<Long, Participant> participants = new ConcurrentHashMap<>();
    private final Map<Long, Meeting> meetingOf = new ConcurrentHashMap<>();
    private final Map<String, Long> seenJoins = new HashMap<>();
    private final Map<String, ArrayDeque<Long>> joinAttempts = new HashMap<>();

    final AtomicLong framesIn = new AtomicLong(), framesOut = new AtomicLong(), framesDropped = new AtomicLong();
    final AtomicLong attestationsForwarded = new AtomicLong(), minutesCharged = new AtomicLong();
    /** Frames received from a speaker not among the selected (§7.4): billed, not forwarded. */
    final AtomicLong framesNotSelected = new AtomicLong();

    public CallRelay(Transport transport, Billing billing, Pricing pricing) {
        this.transport = transport;
        this.billing = billing;
        this.pricing = pricing;
    }

    // ===== Requests (§7.2) =====

    /**
     * {@code call.create}. The caller's delegation makes it the host.
     *
     * @return {@code price}
     */
    public synchronized Map<String, Object> create(String peerId, Map<String, Object> p, long now) throws Refused {
        String meetingId = str(p, "meetingId");
        String kind = str(p, "kind");
        boolean isMeeting = "meeting".equals(kind);
        if (!isMeeting && !"p2p".equals(kind)) throw new Refused(BAD_REQUEST, "kind is p2p or meeting");
        if (isMeeting) checkMeetingId(meetingId);
        else checkCallId(meetingId);
        // A meeting's joiners prove the key from the start (§4.4); only a 1:1 call registers it later.
        if (isMeeting && p.get("authPub") == null) {
            throw new Refused(BAD_REQUEST, "a meeting gives its authPub at create");
        }
        Delegation d = delegation(p, peerId, meetingId, now);
        if (meetings.containsKey(meetingId)) throw new Refused(CONFLICT, "meeting exists");
        long hosted = meetings.values().stream().filter(m -> m.hostFid.equals(d.fid)).count();
        if (hosted >= MEETINGS_PER_HOST) throw new Refused(TOO_MANY_REQUESTS, "too many meetings for this host");
        int max = (int) Math.min(MAX_PARTICIPANTS, Math.max(2, num(p, "maxParticipants",
                isMeeting ? MAX_PARTICIPANTS : P2P_DEFAULT_PARTICIPANTS)));
        // The creator pays for everyone (§7.5): it must afford a minute of at least two.
        long oneMinute = oneMinuteCost(null) * P2P_DEFAULT_PARTICIPANTS;
        if (oneMinute > 0 && !billing.canAfford(d.fid, oneMinute)) {
            throw new Refused(PAYMENT_REQUIRED, "balance below one minute of this call");
        }
        Meeting m = new Meeting(meetingId, kind, d.fid, max, now);
        if (p.get("authPub") != null) m.authPub = pubKey(str(p, "authPub"));
        if (isMeeting) m.maxCostPerMinute = Math.max(0, num(p, "maxCostPerMinute", 0));
        meetings.put(meetingId, m);
        return Map.of("price", Map.of("perKBIn", pricing.perKBIn(), "perKBOut", pricing.perKBOut()),
                "maxParticipants", max, "speakers", m.speakers);
    }

    /** {@code call.register}: the host sets {@code authPub} once the callee has accepted (§4.4). */
    public synchronized Map<String, Object> register(String peerId, Map<String, Object> p, long now) throws Refused {
        Meeting m = meeting(str(p, "meetingId"));
        Delegation d = delegation(p, peerId, m.id, now);
        if (!d.fid.equals(m.hostFid)) throw new Refused(FORBIDDEN, "only the host registers");
        byte[] authPub = pubKey(str(p, "authPub"));
        if (m.authPub != null && !java.util.Arrays.equals(m.authPub, authPub)) {
            throw new Refused(CONFLICT, "authPub already registered");
        }
        m.authPub = authPub;
        return Map.of();
    }

    /**
     * {@code call.join} (§7.2). Before the host registers {@code authPub}, only
     * the host may join, and anyone else gets 409 and retries (§6.2 step 4).
     */
    public synchronized Map<String, Object> join(String peerId, long connectionId, Map<String, Object> p, long now)
            throws Refused {
        Meeting m = meeting(str(p, "meetingId"));
        Delegation d = delegation(p, peerId, m.id, now);
        rateLimitJoins(d.tPub, now);
        int ssrc = (int) num(p, "ssrc", -1L);
        if (p.get("ssrc") == null) throw new Refused(BAD_REQUEST, "ssrc missing");
        long ts = num(p, "ts", 0);
        if (Math.abs(now - ts) > CLOCK_SKEW_MS) throw new Refused(BAD_REQUEST, "ts outside ±60 s of the relay's clock");

        if (m.authPub != null) {
            byte[] sig = hex(str(p, "admitSig"), 64);
            if (!CallKeys.verifyAdmit(m.authPub, m.id, d.tPubBytes(), ssrc, ts, sig)) {
                throw new Refused(UNAUTHORIZED, "admitSig does not verify");
            }
        } else if (!d.fid.equals(m.hostFid)) {
            knock(m, d);
            throw new Refused(CONFLICT, "not open yet: the host has not registered authPub");
        }
        String replayKey = m.id + "|" + d.tPub + "|" + ts;
        if (seenJoins.containsKey(replayKey)) throw new Refused(CONFLICT, "join replayed");
        Participant already = participants.get(connectionId);
        if (already != null && meetingOf.get(connectionId) == m && already.ssrc == ssrc) {
            // A retry whose first reply was lost on the way back (VOICE_SPEC §6.2
            // step 10): it took, so answer it the same way again.
            seenJoins.put(replayKey, now);
            return joinResult(m, already);
        }
        if (already != null) throw new Refused(CONFLICT, "this connection is already in a call");
        for (Participant o : m.order) {
            if (o.ssrc == ssrc) throw new Refused(CONFLICT, "ssrc in use: pick a new one");
        }
        if (m.order.size() >= m.maxParticipants) throw new Refused(FORBIDDEN, "meeting full");
        long maxCost = num(p, "maxCostPerMinute", 0);
        long oneMinute = oneMinuteCost(m);
        if (maxCost > 0) oneMinute = Math.min(oneMinute, maxCost);
        if (oneMinute > 0 && !billing.canAfford(m.payerFor(d.fid), oneMinute)) {
            throw new Refused(PAYMENT_REQUIRED, "balance below one minute of this call");
        }

        List<Map<String, String>> candidates = candidates(p, connectionId);

        seenJoins.put(replayKey, now);
        Participant joined = new Participant(d.fid, peerId, connectionId, ssrc, newRouteId(m), now, d.toJson(),
                maxCost);
        joined.candidates = candidates;
        m.order.add(joined);
        m.byConnection.put(connectionId, joined);
        participants.put(connectionId, joined);
        meetingOf.put(connectionId, m);
        transport.enableDatagrams(connectionId);
        pushRoster(m, joined);
        return joinResult(m, joined);
    }

    private Map<String, Object> joinResult(Meeting m, Participant joined) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("routeId", Integer.toUnsignedLong(joined.routeId));
        r.put("datagram", true); // the §2.3 capability signal
        r.put("roster", roster(m));
        r.put("keyEpoch", m.keyEpoch);
        r.put("speakers", m.speakers);
        return r;
    }

    /** {@code call.leave}, or the connection went away. Bills the part-minute. */
    public synchronized void leave(long connectionId, long now) {
        Participant p = participants.remove(connectionId);
        Meeting m = meetingOf.remove(connectionId);
        if (p == null || m == null) return;
        chargeMinute(m, p, now, true);
        m.byConnection.remove(connectionId);
        m.order.remove(p);
        if (m.order.isEmpty()) {
            m.emptySinceMs = now;
        } else if (p.fid.equals(m.hostFid) && m.order.stream().noneMatch(o -> o.fid.equals(m.hostFid))) {
            m.hostFid = m.order.get(0).fid; // the host role passes to whoever has been present longest
        }
        pushRoster(m, null);
    }

    /** {@code call.info}: public, for the meeting card and to confirm an end. */
    public synchronized Map<String, Object> info(String meetingId) {
        Meeting m = meetings.get(meetingId);
        if (m == null) return Map.of("open", false, "participants", 0);
        return Map.of("open", true, "kind", m.kind, "participants", m.order.size(), "started", m.createdMs);
    }

    public synchronized Map<String, Object> stats() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("meetings", meetings.size());
        s.put("participants", participants.size());
        s.put("framesIn", framesIn.get());
        s.put("framesOut", framesOut.get());
        s.put("framesDropped", framesDropped.get());
        s.put("framesNotSelected", framesNotSelected.get());
        s.put("attestationsForwarded", attestationsForwarded.get());
        s.put("minutesCharged", minutesCharged.get());
        return s;
    }

    // ===== Node events =====

    /**
     * A DATAGRAM (§7.3), on the node's receive thread. Forward it unchanged to
     * everyone else in the call, if it really is this connection's stream.
     */
    public void onDatagram(long connectionId, byte[] data) {
        onDatagram(connectionId, data, System.currentTimeMillis());
    }

    /** As {@link #onDatagram(long, byte[])}, at a given time: for tests, which run their own clock. */
    public void onDatagram(long connectionId, byte[] data, long nowMs) {
        Participant from = participants.get(connectionId);
        Meeting m = meetingOf.get(connectionId);
        if (from == null || m == null) {
            framesDropped.incrementAndGet();
            return;
        }
        MediaFrame.Header h = MediaFrame.Header.parse(data);
        // The key alone cannot stop a member sending as another: every member holds
        // it. Binding routeId and ssrc to the authenticated connection does.
        if (h == null || h.routeId() != from.routeId || h.ssrc() != from.ssrc
                || !from.withinInboundBudget(data.length, nowMs)) {
            from.framesDropped.incrementAndGet();
            framesDropped.incrementAndGet();
            return;
        }
        framesIn.incrementAndGet();
        from.bytesIn.addAndGet(data.length);
        if ((h.flags() & MediaFrame.FLAG_VAD) != 0) from.noteVoice(nowMs, h.level());
        if (!m.forwards(from.ssrc)) {
            framesNotSelected.incrementAndGet(); // received and billed, not one of the loudest
            return;
        }
        for (Participant to : m.byConnection.values()) {
            if (to == from) continue; // nobody hears themselves
            if (transport.sendDatagram(to.connectionId, data)) {
                framesOut.incrementAndGet();
                to.bytesOut.addAndGet(data.length);
                if (m.isMeeting()) to.forwarded.computeIfAbsent(from.ssrc, k -> new ForwardLog()).add(h.seq());
            }
        }
    }

    /**
     * A NOTIFY from a participant. An attestation (§5.1) naming the sender's
     * own route and ssrc goes, unchanged, to everyone else in the call.
     */
    public void onNotify(String peerId, int dataType, byte[] data) {
        if (dataType != 0) return;
        Participant from = null;
        for (Participant p : participants.values()) {
            if (p.peerId.equals(peerId)) {
                from = p;
                break;
            }
        }
        Attestation a = Attestation.parse(data);
        if (from == null || a == null || a.routeId() != from.routeId || a.ssrc() != from.ssrc) return;
        Meeting m = meetingOf.get(from.connectionId);
        if (m == null) return;
        for (Participant to : m.byConnection.values()) {
            if (to == from) continue;
            if (m.isMeeting()) {
                // Only to those who got frames in its range (§5.1): the others played none of them.
                ForwardLog log = to.forwarded.get(from.ssrc);
                if (log == null || !log.anyIn(a.firstSeq(), a.lastSeq())) continue;
            }
            transport.notify(to.peerId, 0, data);
            attestationsForwarded.incrementAndGet();
        }
    }

    // ===== Speaker selection (§7.4) =====

    /**
     * Call every {@link #SELECT_EVERY_MS}: rank each meeting's speakers on the
     * level of their last 300 ms of voice and keep the loudest N. A free slot
     * goes to the loudest active speaker at once; a full set changes only when
     * a newcomer has been {@link #DISPLACE_DB} louder than the weakest for
     * 300 ms, so the set does not flap between two similar voices. A selected
     * speaker who falls silent is displaced by anyone active in the same way.
     */
    public synchronized void selectSpeakers(long now) {
        for (Meeting m : meetings.values()) {
            if (!m.isMeeting()) continue;
            Map<Integer, Participant> bySsrc = new HashMap<>();
            Map<Integer, Double> score = new HashMap<>();
            for (Participant p : m.order) {
                bySsrc.put(p.ssrc, p);
                score.put(p.ssrc, p.score(now));
            }
            java.util.LinkedHashSet<Integer> next = new java.util.LinkedHashSet<>();
            for (Integer s : m.selected) if (bySsrc.containsKey(s)) next.add(s);
            List<Participant> ranked = new ArrayList<>(m.order);
            ranked.removeIf(p -> score.get(p.ssrc) == Double.NEGATIVE_INFINITY);
            ranked.sort((x, y) -> Double.compare(score.get(y.ssrc), score.get(x.ssrc)));
            for (Participant p : ranked) {
                if (next.size() >= m.speakers || next.contains(p.ssrc)) continue;
                next.add(p.ssrc);
                p.louderSinceMs = -1;
            }
            for (Participant p : ranked) {
                if (next.contains(p.ssrc)) continue;
                Integer weakest = null;
                for (Integer s : next) {
                    if (weakest == null || score.get(s) < score.get(weakest)) weakest = s;
                }
                if (weakest == null || score.get(p.ssrc) < score.get(weakest) + DISPLACE_DB) {
                    p.louderSinceMs = -1;
                } else if (p.louderSinceMs < 0) {
                    p.louderSinceMs = now;
                } else if (now - p.louderSinceMs >= SCORE_WINDOW_MS) {
                    next.remove(weakest);
                    next.add(p.ssrc);
                    p.louderSinceMs = -1;
                }
            }
            m.selected = java.util.Set.copyOf(next);
        }
    }

    // ===== Time =====

    /**
     * Call about once a second: bills every full minute (§7.5), removes those
     * unpaid past the grace period, closes meetings empty for a minute, and
     * forgets old join records.
     */
    public synchronized void tick(long now) {
        for (Meeting m : new ArrayList<>(meetings.values())) {
            for (Participant p : new ArrayList<>(m.order)) {
                while (now - p.joinedAtMs >= (p.minuteIndex + 1) * MINUTE_MS) chargeMinute(m, p, now, false);
                if (p.unpaidSinceMs >= 0 && now - p.unpaidSinceMs >= UNPAID_GRACE_MS) {
                    transport.notify(p.peerId, 1, json(Map.of("type", "kicked", "meetingId", m.id,
                            "reason", "balance")));
                    leave(p.connectionId, now);
                }
            }
            if (m.order.isEmpty() && now - m.emptySinceMs >= EMPTY_CLOSE_MS) meetings.remove(m.id);
        }
        seenJoins.values().removeIf(t -> now - t > 2 * CLOCK_SKEW_MS);
        for (Iterator<ArrayDeque<Long>> it = joinAttempts.values().iterator(); it.hasNext(); ) {
            ArrayDeque<Long> q = it.next();
            while (!q.isEmpty() && now - q.peekFirst() > MINUTE_MS) q.pollFirst();
            if (q.isEmpty()) it.remove();
        }
    }

    /**
     * One minute's charge, keyed so a retry never charges twice. Two devices of
     * one FID are separate participants, so the key carries the ssrc too.
     */
    private void chargeMinute(Meeting m, Participant p, long now, boolean partial) {
        long in = p.bytesIn.getAndSet(0), out = p.bytesOut.getAndSet(0);
        long minute = p.minuteIndex++;
        long cost = pricing.cost(in, out);
        if (p.maxCostPerMinute > 0) cost = Math.min(cost, p.maxCostPerMinute);
        if (m.maxCostPerMinute > 0) {
            // The host's cap on the whole meeting, per wall-clock minute (§7.5).
            long window = now / MINUTE_MS;
            if (window != m.capWindow) {
                m.capWindow = window;
                m.capSpent = 0;
            }
            cost = Math.min(cost, m.maxCostPerMinute - m.capSpent);
            if (cost > 0) m.capSpent += cost;
        }
        if (cost <= 0) return;
        String key = "call:" + m.id + ":" + p.fid + ":" + Integer.toUnsignedString(p.ssrc) + ":" + minute;
        String payer = m.payerFor(p.fid);
        minutesCharged.incrementAndGet();
        if (billing.charge(key, payer, cost, partial ? "part-minute" : null)) {
            p.unpaidSinceMs = -1;
        } else if (!partial && p.unpaidSinceMs < 0) {
            p.unpaidSinceMs = now;
            // Told to whoever pays: in a 1:1 call, the caller.
            for (Participant o : m.order) {
                if (o.fid.equals(payer)) {
                    transport.notify(o.peerId, 1, json(Map.of("type", "balance", "meetingId", m.id,
                            "graceSeconds", UNPAID_GRACE_MS / 1000)));
                }
            }
        }
    }

    private long oneMinuteCost(Meeting m) {
        return pricing.cost(EST_BYTES_IN_PER_MINUTE, EST_BYTES_IN_PER_MINUTE * P2P_SPEAKERS);
    }

    // ===== Helpers =====

    private Meeting meeting(String id) throws Refused {
        Meeting m = meetings.get(id);
        if (m == null) throw new Refused(NOT_FOUND, "no such call");
        return m;
    }

    /**
     * The request's delegation, checked (§4.1) and bound to this connection:
     * its {@code tPub} must be the key the connection authenticated with.
     */
    private Delegation delegation(Map<String, Object> p, String peerId, String meetingId, long now) throws Refused {
        Object raw = p.get("delegation");
        if (raw == null) throw new Refused(BAD_REQUEST, "delegation missing");
        Delegation d;
        try {
            d = raw instanceof String s ? Delegation.fromJson(s)
                    : Delegation.fromJson(new com.google.gson.Gson().toJson(raw));
        } catch (RuntimeException e) {
            throw new Refused(BAD_REQUEST, "delegation unreadable");
        }
        if (d == null) throw new Refused(BAD_REQUEST, "delegation unreadable");
        Delegation.Check c = d.verify(meetingId, now / 1000);
        if (c != Delegation.Check.OK) throw new Refused(UNAUTHORIZED, "delegation " + c);
        byte[] tPub = d.tPubBytes();
        if (!peerId.equals(KeyTools.pubkeyToFchAddr(tPub))) {
            throw new Refused(UNAUTHORIZED, "delegation is for another transport key");
        }
        return d;
    }

    private void rateLimitJoins(String tPub, long now) throws Refused {
        ArrayDeque<Long> q = joinAttempts.computeIfAbsent(tPub, k -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() > MINUTE_MS) q.pollFirst();
        if (q.size() >= JOINS_PER_KEY_PER_MINUTE) throw new Refused(TOO_MANY_REQUESTS, "too many joins");
        q.addLast(now);
    }

    private int newRouteId(Meeting m) {
        while (true) {
            int id = random.nextInt();
            if (id != 0 && m.order.stream().noneMatch(p -> p.routeId == id)) return id;
        }
    }

    private List<Map<String, Object>> roster(Meeting m) {
        List<Map<String, Object>> r = new ArrayList<>();
        for (Participant p : m.order) r.add(p.rosterEntry());
        return r;
    }

    /** §7.2: a roster notice to everyone in the call but {@code except}. */
    private void pushRoster(Meeting m, Participant except) {
        byte[] body = json(Map.of("type", "roster", "meetingId", m.id, "host", m.hostFid, "roster", roster(m)));
        for (Participant p : m.order) {
            if (p != except) transport.notify(p.peerId, 1, body);
        }
    }

    /**
     * Someone with a delegation for this call is waiting to join: pass it to
     * the host, which may take it as the callee's answer (§6.2 step 3). The
     * delegation is the joiner's own signed statement, so the relay can
     * forward it but not make one up; the join rate limit bounds how often.
     */
    private void knock(Meeting m, Delegation d) {
        byte[] body = json(Map.of("type", "knock", "meetingId", m.id, "fid", d.fid, "delegation", d.toJson()));
        for (Participant p : m.order) {
            if (p.fid.equals(m.hostFid)) transport.notify(p.peerId, 1, body);
        }
    }

    /**
     * Direct-path candidates a joiner shares with the others (VOICE_SPEC §6.1):
     * its own {@code lan} addresses, and with {@code reflexive = true} the
     * address the relay sees it at, as {@code map}. Sharing is the joiner's
     * choice, since it shows its IP to the others; a joiner that sends
     * neither shares nothing.
     */
    private List<Map<String, String>> candidates(Map<String, Object> p, long connectionId) throws Refused {
        List<Map<String, String>> out = new ArrayList<>();
        if (p.get("candidates") instanceof List<?> list) {
            if (list.size() > MAX_CANDIDATES) throw new Refused(BAD_REQUEST, "too many candidates");
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> c) || !"lan".equals(c.get("t")) || !(c.get("a") instanceof String a)
                        || !isEndpoint(a)) {
                    throw new Refused(BAD_REQUEST, "a candidate is {t: lan, a: ip:port}");
                }
                out.add(Map.of("t", "lan", "a", a));
            }
        }
        if (Boolean.TRUE.equals(p.get("reflexive"))) {
            String seen = transport.peerAddress(connectionId);
            if (seen != null && isEndpoint(seen)) out.add(Map.of("t", "map", "a", seen));
        }
        return out.isEmpty() ? null : out;
    }

    /** ip:port, or [ipv6]:port, with a port in range; no host names. */
    static boolean isEndpoint(String a) {
        if (a.length() > 64) return false;
        int colon = a.lastIndexOf(':');
        if (colon <= 0) return false;
        String host = a.substring(0, colon);
        try {
            int port = Integer.parseInt(a.substring(colon + 1));
            if (port < 1 || port > 65535) return false;
        } catch (NumberFormatException e) {
            return false;
        }
        if (host.startsWith("[") && host.endsWith("]")) return host.length() > 2 && host.matches("\\[[0-9a-fA-F:.]+]");
        return host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    private static byte[] json(Object o) {
        return new com.google.gson.Gson().toJson(o).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** A meeting's id: {@code "mtg_" + 24 hex} (§8). */
    private static void checkMeetingId(String id) throws Refused {
        if (id == null || id.length() != 28 || !id.startsWith("mtg_") || !id.substring(4).matches("[0-9a-f]{24}")) {
            throw new Refused(BAD_REQUEST, "a meeting's id is mtg_ and 24 lowercase hex");
        }
    }

    private static void checkCallId(String id) throws Refused {
        byte[] b;
        try {
            b = Hex.fromHex(id);
        } catch (RuntimeException e) {
            b = null;
        }
        if (b == null || b.length != 16) throw new Refused(BAD_REQUEST, "meetingId of a p2p call is its 16-byte callId, hex");
    }

    private static byte[] pubKey(String hex) throws Refused {
        return hex(hex, 33);
    }

    private static byte[] hex(String hex, int len) throws Refused {
        byte[] b;
        try {
            b = hex == null ? null : Hex.fromHex(hex);
        } catch (RuntimeException e) {
            b = null;
        }
        if (b == null || b.length != len) throw new Refused(BAD_REQUEST, "expected " + len + " bytes of hex");
        return b;
    }

    private static String str(Map<String, Object> p, String k) throws Refused {
        Object v = p.get(k);
        if (!(v instanceof String s) || s.isEmpty()) throw new Refused(BAD_REQUEST, k + " missing");
        return s;
    }

    private static long num(Map<String, Object> p, String k, long dflt) {
        Object v = p.get(k);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return dflt;
            }
        }
        return dflt;
    }
}
