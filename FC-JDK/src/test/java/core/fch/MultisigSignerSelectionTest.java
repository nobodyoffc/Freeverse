package core.fch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** H-52: which signers are kept when more than m signed must not depend on HashMap order. */
public class MultisigSignerSelectionTest {

    @Test
    @DisplayName("Excess signers are dropped by member order, keeping the first m who signed")
    public void keepsFirstMInMemberOrder() {
        List<String> members = List.of("F1", "F2", "F3", "F4", "F5");
        Map<String, String> sigs = new HashMap<>();
        for (String fid : List.of("F5", "F2", "F4", "F1")) sigs.put(fid, "sig-" + fid);

        Map<String, String> kept = TxCreator.dropRedundantStringSigs(sigs, members, 3);

        assertEquals(List.of("F1", "F2", "F4"), List.copyOf(kept.keySet()));
    }

    @Test
    @DisplayName("Signatures from non-members are never selected")
    public void ignoresNonMembers() {
        Map<String, String> sigs = new HashMap<>();
        sigs.put("OUTSIDER", "x");
        sigs.put("F2", "y");

        Map<String, String> kept = TxCreator.dropRedundantStringSigs(sigs, List.of("F1", "F2"), 2);

        assertEquals(List.of("F2"), List.copyOf(kept.keySet()));
    }
}
