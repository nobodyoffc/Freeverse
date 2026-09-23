package fapi.components.call;

import core.crypto.KeyTools;
import utils.Hex;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** VOICE_SPEC §4–§5: delegations, call keys, sealed media frames, attestations, replay. */
public class CallCryptoTest {

    private static final SecureRandom RNG = new SecureRandom();
    private static final String CALL_ID = "00112233445566778899aabbccddeeff";
    private static final long NOW = 1_790_000_000L;

    private static byte[] key() {
        byte[] k = new byte[32];
        RNG.nextBytes(k);
        return k;
    }

    // ===== Delegation (§4.1) =====

    @Test
    public void delegationVerifiesForItsCallOnly() {
        byte[] fidPriv = key(), tPub = KeyTools.prikeyToPubkey(key());
        Delegation d = Delegation.sign(fidPriv, CALL_ID, tPub, NOW + 3600);
        assertEquals(KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(fidPriv)), d.fid);
        assertEquals(Delegation.Check.OK, d.verify(CALL_ID, NOW));
        assertEquals(Delegation.Check.BAD_SIGNATURE, d.verify("ffeeddccbbaa99887766554433221100", NOW));
        assertEquals(Delegation.Check.EXPIRED, d.verify(CALL_ID, NOW + 3600));
        assertEquals(Delegation.Check.OK, Delegation.fromJson(d.toJson()).verify(CALL_ID, NOW));
    }

    @Test
    public void delegationRejectsTampering() {
        byte[] fidPriv = key();
        Delegation good = Delegation.sign(fidPriv, CALL_ID, KeyTools.prikeyToPubkey(key()), NOW + 60);

        Delegation otherKey = Delegation.fromJson(good.toJson());
        otherKey.tPub = Hex.toHex(KeyTools.prikeyToPubkey(key())); // swap in an attacker's transport key
        assertEquals(Delegation.Check.BAD_SIGNATURE, otherKey.verify(CALL_ID, NOW));

        Delegation otherFid = Delegation.fromJson(good.toJson());
        otherFid.fid = KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(key())); // claim to be someone else
        assertEquals(Delegation.Check.FID_MISMATCH, otherFid.verify(CALL_ID, NOW));

        Delegation extended = Delegation.fromJson(good.toJson());
        extended.expiresSec += 1;
        assertEquals(Delegation.Check.BAD_SIGNATURE, extended.verify(CALL_ID, NOW));

        Delegation garbage = Delegation.fromJson(good.toJson());
        garbage.sig = "zz";
        assertEquals(Delegation.Check.MALFORMED, garbage.verify(CALL_ID, NOW));
    }

    @Test
    public void delegationMayNotClaimMoreThanADay() {
        byte[] fidPriv = key(), tPub = KeyTools.prikeyToPubkey(key());
        assertEquals(Delegation.Check.OK,
                Delegation.sign(fidPriv, CALL_ID, tPub, NOW + Delegation.MAX_LIFETIME_SEC).verify(CALL_ID, NOW));
        assertEquals(Delegation.Check.TOO_LONG,
                Delegation.sign(fidPriv, CALL_ID, tPub, NOW + Delegation.MAX_LIFETIME_SEC + 1).verify(CALL_ID, NOW));
    }

    // ===== Keys (§4.2–§4.4) =====

    @Test
    public void bothEndsOfACallDeriveTheSameSecret() {
        byte[] tPrivA = key(), tPrivB = key();
        byte[] tPubA = KeyTools.prikeyToPubkey(tPrivA), tPubB = KeyTools.prikeyToPubkey(tPrivB);
        String fidA = "FAxce6vmEQwx3n36PLjFCMuAdvxTYw3J4b", fidB = "FDKCfjKCgFDgpVBfRNYMasaXmhURP23dHh";

        byte[] atA = CallKeys.p2pSecret(tPrivA, tPubB, CALL_ID, fidA, fidB);
        byte[] atB = CallKeys.p2pSecret(tPrivB, tPubA, CALL_ID, fidB, fidA); // B lists itself first
        assertArrayEquals(atA, atB);
        assertEquals(32, atA.length);
        assertFalse(java.util.Arrays.equals(atA,
                CallKeys.p2pSecret(tPrivA, tPubB, "ffeeddccbbaa99887766554433221100", fidA, fidB)));
        assertThrows(IllegalArgumentException.class, () -> CallKeys.p2pSecret(tPrivA, tPubB, "0011", fidA, fidB));
    }

    @Test
    public void meetingSecretDependsOnEveryInput() {
        byte[] symkey = key(), nonce = key();
        byte[] s = CallKeys.meetingSecret(symkey, nonce, "room1", 3, "mtg_1");
        assertArrayEquals(s, CallKeys.meetingSecret(symkey, nonce, "room1", 3, "mtg_1"));
        assertFalse(java.util.Arrays.equals(s, CallKeys.meetingSecret(symkey, nonce, "room1", 4, "mtg_1")));
        assertFalse(java.util.Arrays.equals(s, CallKeys.meetingSecret(symkey, nonce, "room1", 3, "mtg_2")));
        assertFalse(java.util.Arrays.equals(s, CallKeys.meetingSecret(symkey, key(), "room1", 3, "mtg_1")));
    }

    @Test
    public void senderKeysDifferBySsrcFidAndEpoch() {
        byte[] secret = key();
        byte[] k = CallKeys.senderKey(secret, "FA", 7, 0);
        assertFalse(java.util.Arrays.equals(k, CallKeys.senderKey(secret, "FA", 8, 0)), "two devices, one FID");
        assertFalse(java.util.Arrays.equals(k, CallKeys.senderKey(secret, "FB", 7, 0)));
        assertFalse(java.util.Arrays.equals(k, CallKeys.senderKey(secret, "FA", 7, 1)));
        assertArrayEquals(k, CallKeys.senderKey(secret, "FA", 7, 0));
        // ssrc is unsigned on the wire
        assertFalse(java.util.Arrays.equals(CallKeys.senderKey(secret, "FA", -1, 0),
                CallKeys.senderKey(secret, "FA", 1, 0)));
    }

    @Test
    public void admissionProvesTheCallKeyWithoutRevealingIt() {
        byte[] secret = key();
        byte[] authPriv = CallKeys.authPriv(secret), authPub = CallKeys.authPub(authPriv);
        byte[] tPub = KeyTools.prikeyToPubkey(key());
        byte[] sig = CallKeys.admitSig(authPriv, "mtg_1", tPub, 42, 1_790_000_000_000L);
        assertTrue(CallKeys.verifyAdmit(authPub, "mtg_1", tPub, 42, 1_790_000_000_000L, sig));
        assertFalse(CallKeys.verifyAdmit(authPub, "mtg_1", tPub, 42, 1_790_000_000_001L, sig), "replayed with a new ts");
        assertFalse(CallKeys.verifyAdmit(authPub, "mtg_1", tPub, 43, 1_790_000_000_000L, sig), "another ssrc");
        assertFalse(CallKeys.verifyAdmit(authPub, "mtg_1", KeyTools.prikeyToPubkey(key()), 42,
                1_790_000_000_000L, sig), "another transport key");
        byte[] otherAuthPub = CallKeys.authPub(CallKeys.authPriv(key()));
        assertFalse(CallKeys.verifyAdmit(otherAuthPub, "mtg_1", tPub, 42, 1_790_000_000_000L, sig), "another call");
    }

    // ===== Media frame (§5) =====

    private static MediaFrame.Header header(long seq) {
        return new MediaFrame.Header(MediaFrame.FLAG_VAD, 0x01020304, 0xCAFEBABE, seq, 123456, 40, 0);
    }

    @Test
    public void mediaFrameRoundTripsAndKeepsItsHeaderReadable() {
        byte[] k = key(), opus = new byte[120];
        RNG.nextBytes(opus);
        byte[] frame = MediaFrame.seal(k, header(9), opus);
        assertEquals(MediaFrame.HEADER + opus.length + MediaFrame.TAG_LEN, frame.length);
        assertEquals(header(9), MediaFrame.Header.parse(frame), "the relay reads the header without the key");
        assertArrayEquals(opus, MediaFrame.open(k, frame));
    }

    @Test
    public void anyChangeToAFrameFailsAuthentication() {
        byte[] k = key();
        byte[] frame = MediaFrame.seal(k, header(9), new byte[]{1, 2, 3, 4, 5});
        assertNull(MediaFrame.open(key(), frame), "wrong key");
        for (int i = 1; i < frame.length; i++) {
            byte[] bad = frame.clone();
            bad[i] ^= 0x01;
            if (i == 1) continue; // flags: a reserved bit makes it unparseable, checked below
            assertNull(MediaFrame.open(k, bad), "a bit flipped at byte " + i);
        }
        byte[] reserved = frame.clone();
        reserved[1] |= 0x04;
        assertNull(MediaFrame.Header.parse(reserved));
        byte[] kind = frame.clone();
        kind[0] = 0x7E; // the spike's clear frames are another kind
        assertNull(MediaFrame.Header.parse(kind));
    }

    // ===== Attestation (§5.1) =====

    @Test
    public void attestationProvesWhoSentTheFrames() {
        byte[] tPriv = key(), tPub = KeyTools.prikeyToPubkey(tPriv), k = key();
        List<byte[]> frames = new ArrayList<>();
        for (long seq = 100; seq < 150; seq++) frames.add(MediaFrame.seal(k, header(seq), new byte[]{(byte) seq}));
        Attestation a = Attestation.sign(tPriv, "mtg_1", 0x01020304, 0xCAFEBABE, 100, frames);

        Attestation back = Attestation.parse(a.toBytes());
        assertNotNull(back);
        assertTrue(back.verify(tPub, "mtg_1"));
        assertFalse(back.verify(KeyTools.prikeyToPubkey(key()), "mtg_1"), "signed by someone else");
        assertFalse(back.verify(tPub, "mtg_2"), "another meeting");
        assertEquals(149, back.lastSeq());
        assertTrue(back.vouchesFor(120, frames.get(20)));
        assertFalse(back.vouchesFor(120, frames.get(21)), "a frame substituted at seq 120");
        assertFalse(back.vouchesFor(150, frames.get(0)), "outside its range");

        byte[] relabelled = a.toBytes();
        relabelled[5] ^= 0x01; // the ssrc: a relay mapping the frames to another sender
        assertFalse(Attestation.parse(relabelled).verify(tPub, "mtg_1"));
    }

    @Test
    public void attestationCoversOneTo64Frames() {
        byte[] tPriv = key();
        assertThrows(IllegalArgumentException.class,
                () -> Attestation.sign(tPriv, "c", 0, 1, 0, new ArrayList<>()));
        List<byte[]> many = new ArrayList<>();
        for (int i = 0; i < 65; i++) many.add(new byte[]{(byte) i});
        assertThrows(IllegalArgumentException.class, () -> Attestation.sign(tPriv, "c", 0, 1, 0, many));
        assertNull(Attestation.parse(new byte[10]));
    }

    // ===== Replay window (§5) =====

    @Test
    public void replayWindowDropsRepeatsAndTheTooOld() {
        ReplayWindow w = new ReplayWindow();
        assertTrue(w.accept(10));
        assertFalse(w.accept(10));
        assertTrue(w.accept(12));
        assertTrue(w.accept(11), "reordered within the window");
        assertFalse(w.accept(11));
        assertTrue(w.accept(5000));
        assertFalse(w.accept(5000 - ReplayWindow.SIZE), "older than the window");
        assertTrue(w.accept(5000 - ReplayWindow.SIZE + 1));
        assertFalse(w.accept(12), "an old seq after a jump");
        assertFalse(w.accept(-1));
    }
}
