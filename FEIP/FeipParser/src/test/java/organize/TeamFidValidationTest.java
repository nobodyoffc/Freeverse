package organize;

import data.fchData.OpReturn;
import data.feipData.Feip;
import data.feipData.TeamHistory;
import org.junit.jupiter.api.Test;
import startFEIP.FeipConstants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A team op naming a malformed FID -- as transferee or anywhere in its list -- is invalid as a
 * whole. Applying the good entries would leave the team in a state the signer never signed.
 */
public class TeamFidValidationTest {

    private static final String SIGNER = "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK";
    private static final String GOOD = "FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7";
    private static final String BAD = "FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC8";
    private final OrganizationParser parser = new OrganizationParser();

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
    void transferToAMalformedFidIsRejected() {
        assertNull(parser.makeTeam(opre(), feip("op", "transfer", "tid", "t1",
                "transferee", BAD, "confirm", FeipConstants.CONFIRM_TRANSFER_TEAM)));
        assertNull(parser.makeTeam(opre(), feip("op", "transfer", "tid", "t1",
                "transferee", "not a fid", "confirm", FeipConstants.CONFIRM_TRANSFER_TEAM)));

        TeamHistory h = parser.makeTeam(opre(), feip("op", "transfer", "tid", "t1",
                "transferee", GOOD, "confirm", FeipConstants.CONFIRM_TRANSFER_TEAM));
        assertNotNull(h);
        assertEquals(GOOD, h.getTransferee());
    }

    @Test
    void oneMalformedFidInTheListRejectsEveryListOp() {
        for (String op : List.of("invite", "withdraw invitation", "dismiss", "appoint", "cancel appointment")) {
            assertNull(parser.makeTeam(opre(), feip("op", op, "tid", "t1", "list", List.of(GOOD, BAD))), op);
            assertNull(parser.makeTeam(opre(), feip("op", op, "tid", "t1", "list", List.of(BAD))), op);

            TeamHistory h = parser.makeTeam(opre(), feip("op", op, "tid", "t1", "list", List.of(GOOD, SIGNER)));
            assertNotNull(h, op);
            assertArrayEquals(new String[]{GOOD, SIGNER}, h.getList(), op);
        }
    }
}
