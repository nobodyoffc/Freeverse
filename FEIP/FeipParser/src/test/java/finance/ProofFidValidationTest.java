package finance;

import data.fchData.OpReturn;
import data.feipData.Feip;
import data.feipData.ProofHistory;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** A proof op naming a malformed FID -- issueTo, transferTo or any cosigner -- is invalid as a whole. */
public class ProofFidValidationTest {

    private static final String SIGNER = "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK";
    private static final String GOOD = "FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7";
    private static final String BAD = "FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC8";
    private final FinanceParser parser = new FinanceParser();

    private static OpReturn opre() {
        OpReturn opre = new OpReturn();
        opre.setId("tx1");
        opre.setHeight(100L);
        opre.setTxIndex(1);
        opre.setTime(1_700_000_000L);
        opre.setSigner(SIGNER);
        opre.setCdd(0L);
        return opre;
    }

    private static Feip feip(Object... kv) {
        Map<String, Object> data = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) data.put((String) kv[i], kv[i + 1]);
        Feip feip = new Feip();
        feip.setType("FEIP");
        feip.setData(data);
        return feip;
    }

    @Test
    void issueWithAMalformedCosignerIsRejected() {
        assertNull(parser.makeProof(opre(), feip("op", "issue", "title", "T", "content", "C",
                "cosigners", List.of(GOOD, BAD))));

        ProofHistory h = parser.makeProof(opre(), feip("op", "issue", "title", "T", "content", "C",
                "cosigners", List.of(GOOD)));
        assertNotNull(h);
        assertEquals(List.of(GOOD), h.getCosigners());
    }

    @Test
    void issueToAndTransferToMustBeGoodFids() {
        assertNull(parser.makeProof(opre(), feip("op", "issue", "title", "T", "content", "C", "issueTo", BAD)));
        assertNotNull(parser.makeProof(opre(), feip("op", "issue", "title", "T", "content", "C", "issueTo", GOOD)));

        assertNull(parser.makeProof(opre(), feip("op", "transfer", "proofId", "p1", "transferTo", BAD)));
        assertNotNull(parser.makeProof(opre(), feip("op", "transfer", "proofId", "p1", "transferTo", GOOD)));
    }
}
