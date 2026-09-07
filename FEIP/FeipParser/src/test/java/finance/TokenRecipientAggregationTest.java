package finance;

import data.feipData.Token;
import data.feipData.TokenHistory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the token accounting defects confirmed in the 2026-09-07 audit triage.
 *
 * These exercise FinanceParser.aggregateRecipients directly rather than parseToken, because
 * parseToken writes to the live `token` / `token_holder` indices -- there is no index-name
 * indirection to point it at a scratch index.
 */
public class TokenRecipientAggregationTest {

    /** A real FCH address, so KeyTools.isGoodFid accepts it. */
    private static final String FID_A = "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK";
    private static final String FID_B = "FTqiqAyXHnK7uDTXzMap3acvqADK4ZGzts";
    private static final String TOKEN_ID = "test-token-id";

    private static Token token(int decimals) {
        Token t = new Token();
        t.setDecimal(String.valueOf(decimals));
        return t;
    }

    private static TokenHistory.FidAmount to(String fid, Double amount) {
        TokenHistory.FidAmount fa = new TokenHistory.FidAmount();
        fa.setFid(fid);
        fa.setAmount(amount);
        return fa;
    }

    private static List<TokenHistory.FidAmount> list(TokenHistory.FidAmount... items) {
        return new ArrayList<>(List.of(items));
    }

    @Test
    @DisplayName("Duplicate recipients are summed, so the total matches what is credited")
    public void duplicateRecipientsAreAggregated() {
        FinanceParser.RecipientTotals totals = FinanceParser.aggregateRecipients(
                list(to(FID_A, 10d), to(FID_B, 5d), to(FID_A, 7d)), token(8), TOKEN_ID);

        assertNotNull(totals);
        assertEquals(22d, totals.total, 1e-9, "total counts every entry");

        // Pre-fix the map used put(), so FID_A kept only the last value (7) while the sender was
        // debited the full 22 -- the missing 10 simply vanished.
        assertEquals(17d, totals.amountByFid.get(FID_A), 1e-9, "duplicate amounts must be summed");
        assertEquals(5d, totals.amountByFid.get(FID_B), 1e-9);

        double credited = totals.amountByFid.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(totals.total, credited, 1e-9,
                "what is debited must equal what is credited");
    }

    @Test
    @DisplayName("A duplicated recipient yields one holder id, not two")
    public void duplicateRecipientsProduceOneHolderId() {
        FinanceParser.RecipientTotals totals = FinanceParser.aggregateRecipients(
                list(to(FID_A, 1d), to(FID_A, 2d), to(FID_A, 3d)), token(8), TOKEN_ID);

        assertNotNull(totals);
        assertEquals(1, totals.holderIds.size(), "holder ids must be de-duplicated");
        assertEquals(1, totals.fidByHolderId.size());
        assertEquals(6d, totals.amountByFid.get(FID_A), 1e-9);
    }

    @Test
    @DisplayName("Negative amounts are rejected")
    public void negativeAmountRejected() {
        // Pre-fix a negative amount made `senderOldBalance - sum` INCREASE the sender's balance
        // while crediting the recipient a negative one.
        assertNull(FinanceParser.aggregateRecipients(
                list(to(FID_A, -5d)), token(8), TOKEN_ID));
        assertNull(FinanceParser.aggregateRecipients(
                list(to(FID_A, 10d), to(FID_B, -3d)), token(8), TOKEN_ID));
    }

    @Test
    @DisplayName("Zero, null, NaN and infinite amounts are rejected")
    public void nonPositiveAndNonFiniteAmountsRejected() {
        assertNull(FinanceParser.aggregateRecipients(list(to(FID_A, 0d)), token(8), TOKEN_ID));
        assertNull(FinanceParser.aggregateRecipients(list(to(FID_A, null)), token(8), TOKEN_ID));
        assertNull(FinanceParser.aggregateRecipients(list(to(FID_A, Double.NaN)), token(8), TOKEN_ID));
        assertNull(FinanceParser.aggregateRecipients(
                list(to(FID_A, Double.POSITIVE_INFINITY)), token(8), TOKEN_ID));
    }

    @Test
    @DisplayName("A bad fid and a null recipient list are rejected")
    public void badFidAndNullListRejected() {
        assertNull(FinanceParser.aggregateRecipients(list(to("not-an-address", 1d)), token(8), TOKEN_ID));
        assertNull(FinanceParser.aggregateRecipients(null, token(8), TOKEN_ID));
    }

    @Test
    @DisplayName("Amounts exceeding the token's decimal precision are rejected")
    public void tooManyDecimalPlacesRejected() {
        assertNull(FinanceParser.aggregateRecipients(list(to(FID_A, 1.234d)), token(2), TOKEN_ID));
        assertNotNull(FinanceParser.aggregateRecipients(list(to(FID_A, 1.23d)), token(2), TOKEN_ID));
    }
}
