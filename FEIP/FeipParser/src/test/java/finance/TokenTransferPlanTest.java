package finance;

import data.feipData.Token;
import data.feipData.TokenHistory;
import data.feipData.TokenHolder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for FinanceParser.planTransfer: the self-transfer that zeroed a balance (C-09)
 * and the holder documents written under each other's ids when new and existing recipients
 * interleaved. Pure, like TokenRecipientAggregationTest, because parseToken writes to the live
 * token indices.
 */
public class TokenTransferPlanTest {

    private static final String SENDER = "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK";
    private static final String FID_A = "FTqiqAyXHnK7uDTXzMap3acvqADK4ZGzts";
    private static final String FID_B = "F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW";
    private static final String TOKEN_ID = "test-token-id";
    private static final long HEIGHT = 100L;

    private static Token token() {
        Token t = new Token();
        t.setDecimal("2");
        return t;
    }

    private static TokenHistory.FidAmount to(String fid, double amount) {
        TokenHistory.FidAmount fa = new TokenHistory.FidAmount();
        fa.setFid(fid);
        fa.setAmount(amount);
        return fa;
    }

    private static TokenHolder holder(String fid, double balance) {
        TokenHolder h = new TokenHolder();
        h.setId(TokenHolder.getTokenHolderId(fid, TOKEN_ID));
        h.setFid(fid);
        h.setTokenId(TOKEN_ID);
        h.setBalance(balance);
        return h;
    }

    private static String id(String fid) {
        return TokenHolder.getTokenHolderId(fid, TOKEN_ID);
    }

    @Test
    @DisplayName("Sending your whole balance to yourself leaves it unchanged")
    public void selfTransferKeepsBalance() {
        FinanceParser.RecipientTotals totals = FinanceParser.aggregateRecipients(
                new ArrayList<>(List.of(to(SENDER, 100d))), token(), TOKEN_ID);

        Map<String, TokenHolder> plan = FinanceParser.planTransfer(holder(SENDER, 100d), SENDER, totals,
                List.of(), List.of(), TOKEN_ID, HEIGHT, 2);

        assertNotNull(plan);
        assertEquals(1, plan.size(), "one document, written once");
        // Pre-fix: credited to 200 through the recipient path, then overwritten by the debit to 0.
        assertEquals(100d, plan.get(id(SENDER)).getBalance(), 1e-9);
    }

    @Test
    @DisplayName("A transfer to yourself and another debits only what leaves")
    public void selfAndOtherRecipient() {
        FinanceParser.RecipientTotals totals = FinanceParser.aggregateRecipients(
                new ArrayList<>(List.of(to(SENDER, 30d), to(FID_A, 20d))), token(), TOKEN_ID);

        Map<String, TokenHolder> plan = FinanceParser.planTransfer(holder(SENDER, 100d), SENDER, totals,
                List.of(), List.of(id(FID_A)), TOKEN_ID, HEIGHT, 2);

        assertNotNull(plan);
        assertEquals(80d, plan.get(id(SENDER)).getBalance(), 1e-9);
        assertEquals(20d, plan.get(id(FID_A)).getBalance(), 1e-9);
    }

    @Test
    @DisplayName("Every holder document is written under its own id when new and existing recipients interleave")
    public void holdersAreKeyedByTheirOwnId() {
        // Recipient order: A (already a holder), then B (new). Pre-fix the id list was [A, B, sender]
        // but the documents were [B, A, sender], so B's balance landed in A's document and vice versa.
        FinanceParser.RecipientTotals totals = FinanceParser.aggregateRecipients(
                new ArrayList<>(List.of(to(FID_A, 10d), to(FID_B, 5d))), token(), TOKEN_ID);

        Map<String, TokenHolder> plan = FinanceParser.planTransfer(holder(SENDER, 50d), SENDER, totals,
                List.of(holder(FID_A, 1d)), List.of(id(FID_B)), TOKEN_ID, HEIGHT, 2);

        assertNotNull(plan);
        assertEquals(3, plan.size());
        for (Map.Entry<String, TokenHolder> e : plan.entrySet()) {
            assertEquals(id(e.getValue().getFid()), e.getKey(),
                    "document for " + e.getValue().getFid() + " must be written under its own id");
        }
        assertEquals(11d, plan.get(id(FID_A)).getBalance(), 1e-9);
        assertEquals(5d, plan.get(id(FID_B)).getBalance(), 1e-9);
        assertEquals(35d, plan.get(id(SENDER)).getBalance(), 1e-9);

        double before = 50d + 1d;
        double after = plan.values().stream().mapToDouble(TokenHolder::getBalance).sum();
        assertEquals(before, after, 1e-9, "a transfer moves tokens; it neither creates nor destroys them");
    }

    @Test
    @DisplayName("A stored holder that is not a recipient rejects the transfer instead of crediting it")
    public void mismatchedStoredHolderIsRejected() {
        FinanceParser.RecipientTotals totals = FinanceParser.aggregateRecipients(
                new ArrayList<>(List.of(to(FID_A, 10d))), token(), TOKEN_ID);

        Map<String, TokenHolder> plan = FinanceParser.planTransfer(holder(SENDER, 50d), SENDER, totals,
                List.of(holder(FID_B, 1d)), List.of(), TOKEN_ID, HEIGHT, 2);

        assertNull(plan);
    }
}
