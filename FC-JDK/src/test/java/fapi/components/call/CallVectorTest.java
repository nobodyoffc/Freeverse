package fapi.components.call;

import core.crypto.KeyTools;
import utils.Hex;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The call wire formats against {@code callVectors.json}, which FreerForMac's
 * vector-gen writes from these same classes (CallRef). A failure here means
 * the format changed: regenerate there, and copy the file to FCDomain's test
 * resources and here, so the Mac and FC-JDK copies are checked against it.
 */
public class CallVectorTest {

    private static JsonObject v() throws Exception {
        try (InputStream in = CallVectorTest.class.getClassLoader().getResourceAsStream("callVectors.json")) {
            assertNotNull(in, "callVectors.json missing from test resources");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static byte[] h(JsonObject o, String k) {
        return Hex.fromHex(o.get(k).getAsString());
    }

    private static String s(JsonObject o, String k) {
        return o.get(k).getAsString();
    }

    @Test
    public void everyVectorReproduces() throws Exception {
        JsonObject v = v(), keys = v.getAsJsonObject("keys");
        byte[] fidPrivA = h(keys, "fid_priv_a"), tPrivA = h(keys, "t_priv_a"), tPrivB = h(keys, "t_priv_b");
        byte[] tPubA = h(keys, "t_pub_a"), tPubB = h(keys, "t_pub_b");
        String fidA = s(keys, "fid_a"), fidB = s(keys, "fid_b"), callId = s(keys, "call_id");
        assertEquals(fidA, KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(fidPrivA)));
        assertArrayEquals(tPubA, KeyTools.prikeyToPubkey(tPrivA));

        JsonObject del = v.getAsJsonObject("delegation");
        long expires = del.get("expires_sec").getAsLong();
        Delegation d = Delegation.sign(fidPrivA, callId, tPubA, expires);
        assertEquals(s(del, "json"), d.toJson(), "delegation JSON");
        assertEquals(Delegation.Check.OK, Delegation.fromJson(s(del, "json")).verify(callId, expires - 60));

        byte[] p2p = CallKeys.p2pSecret(tPrivA, tPubB, callId, fidA, fidB);
        assertEquals(s(v, "p2p_secret"), Hex.toHex(p2p));
        assertEquals(s(v, "p2p_secret"), Hex.toHex(CallKeys.p2pSecret(tPrivB, tPubA, callId, fidB, fidA)));

        JsonObject m = v.getAsJsonObject("meeting");
        assertEquals(s(m, "secret"), Hex.toHex(CallKeys.meetingSecret(h(m, "symkey"), h(m, "nonce"),
                s(m, "entity_id"), m.get("symkey_version").getAsLong(), s(m, "meeting_id"))));

        JsonObject sk = v.getAsJsonObject("sender_key");
        int ssrc = Integer.parseUnsignedInt(s(sk, "ssrc"));
        assertEquals(s(sk, "epoch0"), Hex.toHex(CallKeys.senderKey(p2p, fidA, ssrc, 0)));
        assertEquals(s(sk, "epoch1"), Hex.toHex(CallKeys.senderKey(p2p, fidA, ssrc, 1)));
        assertEquals(s(sk, "nonce_seq_258"), Hex.toHex(CallKeys.frameNonce(ssrc, 258)));

        JsonObject ad = v.getAsJsonObject("admission");
        byte[] authPriv = CallKeys.authPriv(p2p);
        assertEquals(s(ad, "auth_priv"), Hex.toHex(authPriv));
        assertEquals(s(ad, "auth_pub"), Hex.toHex(CallKeys.authPub(authPriv)));
        long ts = ad.get("ts_ms").getAsLong();
        assertEquals(s(ad, "admit_sig"), Hex.toHex(CallKeys.admitSig(authPriv, callId, tPubB, ssrc, ts)));
        assertTrue(CallKeys.verifyAdmit(h(ad, "auth_pub"), callId, tPubB, ssrc, ts, h(ad, "admit_sig")));

        byte[] senderKey = CallKeys.senderKey(p2p, fidA, ssrc, 0);
        JsonObject mf = v.getAsJsonObject("media_frame");
        MediaFrame.Header header = MediaFrame.Header.parse(h(mf, "frame"));
        assertNotNull(header);
        assertEquals(s(mf, "header"), Hex.toHex(header.toBytes()));
        assertEquals(s(mf, "frame"), Hex.toHex(MediaFrame.seal(senderKey, header, h(mf, "payload"))));
        assertArrayEquals(h(mf, "payload"), MediaFrame.open(senderKey, h(mf, "frame")));

        JsonObject at = v.getAsJsonObject("attestation");
        List<byte[]> frames = new ArrayList<>();
        for (var e : at.getAsJsonArray("frames")) frames.add(Hex.fromHex(e.getAsString()));
        Attestation a = Attestation.sign(tPrivA, callId, 0x01020304, ssrc, 258, frames);
        assertEquals(s(at, "attestation"), Hex.toHex(a.toBytes()));
        Attestation parsed = Attestation.parse(h(at, "attestation"));
        assertTrue(parsed.verify(tPubA, callId));
        for (int i = 0; i < frames.size(); i++) assertTrue(parsed.vouchesFor(258 + i, frames.get(i)));
    }
}
