package finance;

import data.fchData.OpReturn;
import data.feipData.Feip;
import data.feipData.TokenHistory;
import data.feipData.TokenOpData;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A FEIP20 deploy carries an optional {@code cover}: the record id of the FEIP24 Image shown as
 * the token's logo. It must reach the history record, which is all a rollback rebuilds from, and
 * anything that is not a record id is dropped rather than indexed.
 */
public class TokenCoverTest {

    private static final String IMAGE_ID = "ab".repeat(32);
    private final FinanceParser parser = new FinanceParser();

    private static OpReturn opre(String txid) {
        OpReturn opre = new OpReturn();
        opre.setId(txid);
        opre.setHeight(3_500_000L);
        opre.setTxIndex(1);
        opre.setTime(1_700_000_000L);
        opre.setSigner("FQ8KKsMFYXkmNPDVauEMWFBnd7yfhiqqN8");
        opre.setCdd(1L);
        return opre;
    }

    private static Feip deploy(String cover) {
        Map<String, Object> data = new HashMap<>();
        data.put("op", "deploy");
        data.put("name", "Example Coin");
        if (cover != null) data.put("cover", cover);
        Feip feip = new Feip();
        feip.setType("FEIP");
        feip.setData(data);
        return feip;
    }

    @Test
    void aCoverReachesTheHistory() {
        assertEquals(IMAGE_ID, parser.makeToken(opre("01".repeat(32)), deploy(IMAGE_ID)).getCover());
        assertEquals(IMAGE_ID, parser.makeToken(opre("02".repeat(32)), deploy(IMAGE_ID.toUpperCase())).getCover(),
                "stored lowercase");
        assertNull(parser.makeToken(opre("03".repeat(32)), deploy(null)).getCover());
    }

    @Test
    void aCoverThatIsNotARecordIdIsDropped() {
        TokenHistory hist = parser.makeToken(opre("04".repeat(32)), deploy("https://example.org/logo.png"));
        assertNotNull(hist, "the deploy itself stands");
        assertNull(hist.getCover());
    }

    @Test
    void theOpFieldsAndMappingsNameIt() throws Exception {
        assertTrue(List.of(TokenOpData.OP_FIELDS.get("deploy")).contains("cover"));
        assertEquals(IMAGE_ID, TokenOpData.makeDeploy("n", null, null, null, null, null, null, null,
                null, null, null, IMAGE_ID).getCover());
        for (String index : List.of("token", "token_history")) {
            try (InputStream in = getClass().getResourceAsStream("/mappings/" + index + ".json")) {
                assertNotNull(in, index);
                assertTrue(new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("\"cover\":{\"type\":\"keyword\"}"), index);
            }
        }
    }
}
