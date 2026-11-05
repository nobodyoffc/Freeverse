package identity;

import com.google.gson.Gson;
import core.crypto.KeyTools;
import data.fchData.OpReturn;
import data.feipData.Feip;
import data.feipData.FreerHist;
import data.feipData.NobodyOpData;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import startFEIP.FileParser;

/**
 * FEIP4: the FID a Nobody op changes is the one its key controls, whoever signs it.
 */
public class NobodyAnnounceTest {

    private static final String LEAKED_HEX = "a048f6c843f92bfe036057f7fc2bf2c27353c624cf7ad97e98ed41432f700575";

    private static OpReturn opFrom(String signer, String prikey) {
        Feip feip = new Feip();
        feip.setType("FEIP");
        feip.setSn("4");
        feip.setVer("1");
        feip.setName("Nobody");
        feip.setData(NobodyOpData.makeNobody(prikey));
        OpReturn opre = new OpReturn();
        opre.setOpReturn(new Gson().toJson(feip));
        opre.setSigner(signer);
        opre.setId("00".repeat(32));
        opre.setHeight(4_100_000L);
        opre.setTime(1_780_000_000L);
        opre.setTxIndex(1);
        return opre;
    }

    private static FreerHist make(OpReturn opre) {
        return new IdentityParser().makeNobody(opre, FileParser.parseFeip(opre));
    }

    private static String fidOf(String prikey) {
        return KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(prikey));
    }

    @Test
    public void ownKeyTargetsSigner() {
        String leaked = fidOf(LEAKED_HEX);
        FreerHist hist = make(opFrom(leaked, LEAKED_HEX));
        assertNotNull(hist);
        assertEquals(leaked, hist.getSigner());
        assertEquals(leaked, hist.targetFid());
    }

    @Test
    public void anyoneMayAnnounceALeakedKey() {
        String leaked = fidOf(LEAKED_HEX);
        String announcer = fidOf("1".repeat(64));
        assertNotEquals(leaked, announcer);

        FreerHist hist = make(opFrom(announcer, LEAKED_HEX));
        assertNotNull(hist, "a third party's announcement is valid");
        assertEquals(announcer, hist.getSigner());
        assertEquals(leaked, hist.getFid());
        assertEquals(leaked, hist.targetFid());
        assertEquals(LEAKED_HEX, hist.getPrikey());
    }

    @Test
    public void wifKeyTargetsItsFid() {
        String wif = KeyTools.prikey32To38WifCompressed(LEAKED_HEX);
        FreerHist hist = make(opFrom(fidOf("1".repeat(64)), wif));
        assertNotNull(hist);
        assertEquals(fidOf(LEAKED_HEX), hist.targetFid());
    }

    @Test
    public void invalidKeyIsIgnoredWithoutThrowing() {
        String signer = fidOf("1".repeat(64));
        assertNull(make(opFrom(signer, "not a key")));
        assertNull(make(opFrom(signer, "zz".repeat(32))));
        assertNull(make(opFrom(signer, null)));
    }

    @Test
    public void oldHistoryWithoutFidTargetsSigner() {
        FreerHist hist = new FreerHist();
        hist.setSigner("FSigner");
        assertEquals("FSigner", hist.targetFid());
    }
}
