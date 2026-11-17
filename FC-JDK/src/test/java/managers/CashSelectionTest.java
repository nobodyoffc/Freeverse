package managers;

import core.fch.TxCreator;
import data.fchData.Cash;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The pure cash-picking step behind getValidCashes, without Elasticsearch. */
public class CashSelectionTest {

    private static final long COIN = 100_000_000L;

    private static Cash cash(String id, long value) {
        Cash c = new Cash();
        c.setId(id);
        c.setValue(value);
        c.setValid(true);
        return c;
    }

    private static long sum(List<Cash> list) {
        return list.stream().mapToLong(Cash::getValue).sum();
    }

    @Test
    void picksInListOrderUntilAmountPlusFeeIsCovered() {
        List<Cash> list = List.of(cash("a", COIN), cash("b", COIN), cash("c", COIN));
        List<Cash> picked = CashManager.pickCashes(list, COIN + 1, 0, 2, 0, null, Integer.MAX_VALUE);
        assertNotNull(picked);
        assertEquals(List.of("a", "b"), picked.stream().map(Cash::getId).toList());
    }

    @Test
    void returnsNullWhenTheListIsNotEnough() {
        List<Cash> list = List.of(cash("a", COIN), cash("b", COIN));
        assertNull(CashManager.pickCashes(list, 2 * COIN, 0, 2, 0, null, Integer.MAX_VALUE),
                "2 F cannot also pay the fee");
    }

    @Test
    void stopsAtMaxInputs() {
        List<Cash> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) list.add(cash("c" + i, COIN));
        assertNull(CashManager.pickCashes(list, 5 * COIN, 0, 2, 0, null, 5));
        assertNotNull(CashManager.pickCashes(list, 5 * COIN, 0, 2, 0, null, 6));
    }

    @Test
    void feeGrowsWithInputsAndOutputs() {
        List<Cash> list = List.of(cash("a", COIN));
        long fee1 = TxCreator.calcFee(TxCreator.calcTxSize(1, 2, 0), TxCreator.DEFAULT_FEE_RATE);
        assertNotNull(CashManager.pickCashes(list, COIN - fee1, 0, 2, 0, null, 200));
        assertNull(CashManager.pickCashes(list, COIN - fee1 + 1, 0, 2, 0, null, 200));
    }

    @Test
    void largestValueFirstCoversABigAmountWithFewInputs() {
        List<Cash> list = new ArrayList<>();
        for (int i = 0; i < 300; i++) list.add(cash("s" + i, COIN));   // 300 x 1 F
        list.add(cash("big1", 40_000 * COIN));
        list.add(cash("big2", 30_000 * COIN));
        // In list order the 300 small ones come first and 200 inputs are not enough.
        assertNull(CashManager.pickCashes(list, 50_000 * COIN, 0, 2, 0, null, 200));

        list.sort(CashManager.largestValueFirst());
        List<Cash> picked = CashManager.pickCashes(list, 50_000 * COIN, 0, 2, 0, null, 200);
        assertNotNull(picked);
        assertEquals(List.of("big1", "big2"), picked.stream().map(Cash::getId).toList());
        assertTrue(sum(picked) >= 50_000 * COIN);
    }
}
