package managers;

import clients.EsClientMaker;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import constants.CodeMessage;
import constants.FieldNames;
import constants.IndicesNames;
import core.fch.TxCreator;
import data.fchData.Block;
import data.fchData.Cash;
import data.fchData.Freer;
import managers.CashManager.SearchResult;
import org.junit.jupiter.api.*;
import utils.EsUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * getBalance / getUtxos / cashValid selection against a real Elasticsearch, for an address shaped
 * like FC5yQV66T6pjDAHVmBKGkjjnvkjtirgSmY on 2026-11-17: 229 valid cashes, the 200 lowest-cd ones
 * worth 201.99 F, the other 29 worth 104,532.19048313 F, balance 104,734.18048313 F.
 *
 * It recreates the cash, freer and block indices, so it only runs on a throwaway cluster named
 * {@value #SCRATCH_CLUSTER}. Host/port: -Dfeip.it.host / -Dfeip.it.port (default 127.0.0.1:9291).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ValidCashesEsIT {

    static final String SCRATCH_CLUSTER = "feip-it-scratch";
    private static final long COIN = 100_000_000L;

    private static final String BIG = "FC5yQV66T6pjDAHVmBKGkjjnvkjtirgSmY";
    private static final long BIG_BALANCE = 10_473_418_048_313L;     // 104734.18048313 F
    private static final String SMALL = "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK";   // 150 cashes
    private static final String DUST = "FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7";    // 300 x 1 F
    private static final long BEST_HEIGHT = 3_000_000L;

    private ElasticsearchClient es;

    @BeforeAll
    void setUp() throws Exception {
        String host = System.getProperty("feip.it.host", "127.0.0.1");
        int port = Integer.parseInt(System.getProperty("feip.it.port", "9291"));
        try {
            es = new EsClientMaker().getClientHttp(host, port);
            String cluster = es.info().clusterName();
            Assumptions.assumeTrue(SCRATCH_CLUSTER.equals(cluster),
                    "Refusing to run: cluster is '" + cluster + "', not the scratch cluster " + SCRATCH_CLUSTER);
        } catch (Exception e) {
            Assumptions.abort("No scratch Elasticsearch at " + host + ":" + port + ": " + e.getMessage());
        }
        for (String idx : List.of(IndicesNames.CASH, IndicesNames.FREER, IndicesNames.BLOCK)) {
            try { es.indices().delete(d -> d.index(idx)); } catch (Exception ignored) { }
        }
        // The production mappings (startFCH.IndicesFCH), plus cd, which the parser writes.
        es.indices().create(c -> c.index(IndicesNames.CASH).mappings(m -> m
                .properties("id", p -> p.keyword(k -> k))
                .properties("owner", p -> p.wildcard(w -> w))
                .properties("issuer", p -> p.wildcard(w -> w))
                .properties("value", p -> p.long_(l -> l))
                .properties("cd", p -> p.long_(l -> l))
                .properties("birthHeight", p -> p.long_(l -> l))
                .properties("birthTxId", p -> p.keyword(k -> k))
                .properties("birthIndex", p -> p.long_(l -> l))
                .properties("lockScript", p -> p.text(t -> t))
                .properties("valid", p -> p.boolean_(b -> b))));
        es.indices().create(c -> c.index(IndicesNames.FREER).mappings(m -> m
                .properties("id", p -> p.wildcard(w -> w))
                .properties("balance", p -> p.long_(l -> l))
                .properties("cash", p -> p.long_(l -> l))));
        es.indices().create(c -> c.index(IndicesNames.BLOCK).mappings(m -> m
                .properties("height", p -> p.long_(l -> l))));

        Block best = new Block();
        best.setId("bestblock");
        best.setHeight(BEST_HEIGHT);
        es.index(i -> i.index(IndicesNames.BLOCK).id("bestblock").document(best));

        List<Cash> all = new ArrayList<>();
        // BIG: 199 x 1.01 F + 1 x 1.00 F = 201.99 F with cd 1..200 (what the old 200-page saw) ...
        for (int i = 0; i < 200; i++) all.add(cash(BIG, "b" + i, i < 199 ? 101_000_000L : COIN, 1 + i, false));
        // ... and 28 x 3600 F + 1 x 3732.19048313 F = 104532.19048313 F, all with a higher cd.
        for (int i = 0; i < 29; i++) all.add(cash(BIG, "B" + i, i < 28 ? 3600 * COIN : 373_219_048_313L, 1_000_000 + i, false));
        // A spent cash must never count.
        all.add(cash(BIG, "spent", 999_999 * COIN, 5, true));
        // SMALL: 150 cashes of varied value, cd unrelated to value.
        Random rnd = new Random(42);
        for (int i = 0; i < 150; i++) all.add(cash(SMALL, "s" + i, (1 + rnd.nextInt(5000)) * 1_000_000L, rnd.nextInt(100_000), false));
        // DUST: 300 x 1 F.
        for (int i = 0; i < 300; i++) all.add(cash(DUST, "d" + i, COIN, i, false));

        BulkRequest.Builder br = new BulkRequest.Builder();
        for (Cash c : all) br.operations(op -> op.index(ix -> ix.index(IndicesNames.CASH).id(c.getId()).document(c)));
        Freer freer = new Freer();
        freer.setId(BIG);
        freer.setBalance(BIG_BALANCE);
        freer.setCash(229L);
        br.operations(op -> op.index(ix -> ix.index(IndicesNames.FREER).id(BIG).document(freer)));
        br.refresh(co.elastic.clients.elasticsearch._types.Refresh.True);
        assertFalse(es.bulk(br.build()).errors());
    }

    private static Cash cash(String owner, String id, long value, long cd, boolean spent) {
        Cash c = new Cash();
        c.setId(owner.substring(0, 6) + "_" + id);
        c.setOwner(owner);
        c.setIssuer("FIssuer");
        c.setValue(value);
        c.setCd(cd);
        c.setBirthHeight(1000L);
        c.setBirthTxId(c.getId() + "tx");
        c.setBirthIndex(0);
        c.setLockScript("76a914" + "00".repeat(20) + "88ac");
        c.setValid(!spent);
        return c;
    }

    private static long sum(List<Cash> list) {
        return list.stream().mapToLong(Cash::getValue).sum();
    }

    // ---- getBalance ----

    @Test
    void balanceIsTheFullFreerBalanceNotThe200CashPage() {
        assertEquals(BIG_BALANCE, CashManager.confirmedBalance(es, BIG));
        // The fallback sum over all valid cashes agrees (and excludes the spent one).
        assertEquals(BIG_BALANCE, CashManager.sumValidCashValue(es, BIG));
        // The old getBalance summed this page: 201.99 F.
        SearchResult<Cash> firstPage = CashManager.getValidCashes(BIG, null, null, 0L, 0, 0, null, es, null);
        assertEquals(20_199_000_000L, sum(firstPage.getData()));
    }

    @Test
    void balanceFallsBackToTheCashSumWithoutAFreer() {
        assertEquals(sum(CashManager.getAllValidCashes(SMALL, 10_000, es, null).getData()),
                CashManager.confirmedBalance(es, SMALL));
    }

    // ---- getUtxos / cashValid without amount ----

    @Test
    void listModeOfGetValidCashesIsUnchanged() {
        SearchResult<Cash> r = CashManager.getValidCashes(BIG, null, null, 0L, 0, 0, null, es, null);
        assertEquals(200, r.getGot());
        assertEquals(229, r.getTotal());
    }

    @Test
    void defaultPageReturnsEveryCash() {
        SearchResult<Cash> r = CashManager.getValidCashPage(BIG, null, 0, null, es, null);
        assertEquals(229, r.getGot());
        assertEquals(229, r.getTotal());
        assertNull(r.getLast(), "nothing follows");
        assertEquals(BIG_BALANCE, sum(r.getData()));
    }

    @Test
    void pagingWithAfterVisitsEveryCashOnce() {
        Set<String> seen = new HashSet<>();
        List<String> after = null;
        int pages = 0;
        do {
            SearchResult<Cash> r = CashManager.getValidCashPage(BIG, null, 100, after, es, null);
            assertEquals(229, r.getTotal());
            for (Cash c : r.getData()) assertTrue(seen.add(c.getId()), "duplicate " + c.getId());
            after = r.getLast();
            pages++;
        } while (after != null);
        assertEquals(3, pages);
        assertEquals(229, seen.size());
    }

    @Test
    void getAllValidCashesHonorsItsCap() {
        SearchResult<Cash> r = CashManager.getAllValidCashes(DUST, 250, es, null);
        assertEquals(250, r.getGot());
        assertEquals(300, r.getTotal());
        assertNotNull(r.getLast());
    }

    // ---- getUtxos / cashValid with amount ----

    @Test
    void amountAboveTheFirstPageIsCovered() {
        long amount = 50_000 * COIN;
        SearchResult<Cash> r = CashManager.getValidCashes(BIG, amount, null, null, 2, 0, null, es, null);
        assertFalse(r.hasError(), r.getMessage());
        long fee = TxCreator.calcFee(TxCreator.calcTxSize(r.getData().size(), 2, 0), TxCreator.DEFAULT_FEE_RATE);
        assertTrue(sum(r.getData()) >= amount + fee);
        assertTrue(r.getData().size() <= CashManager.MAX_INPUT_SIZE);
        assertEquals(14, r.getData().size(), "largest first: 1 x 3732 + 13 x 3600");
    }

    @Test
    void upToTheLargest200CashesIsSpendableInOneTx() {
        // The largest 200: 29 big ones + 171 x 1.01 F = 104704.90048313 F.
        long amount = 104_700 * COIN;
        SearchResult<Cash> r = CashManager.getValidCashes(BIG, amount, null, null, 2, 0, null, es, null);
        assertFalse(r.hasError(), r.getMessage());
        assertTrue(r.getData().size() <= 200 && r.getData().size() > 190, "size " + r.getData().size());
        assertTrue(sum(r.getData()) >= amount);
    }

    @Test
    void theWholeBalanceNeedsMoreThan200InputsSoItSaysMerge() {
        long amount = BIG_BALANCE - COIN;    // needs all 229 cashes
        SearchResult<Cash> r = CashManager.getValidCashes(BIG, amount, null, null, 2, 0, null, es, null);
        assertEquals(CodeMessage.Code2011TooManyCashesToSpend, r.getCode());
    }

    @Test
    void amountAboveTheBalanceIsInsufficientNotCashNoFound() {
        SearchResult<Cash> r = CashManager.getValidCashes(BIG, 110_000 * COIN, null, null, 0, 0, null, es, null);
        assertTrue(r.hasError());
        assertEquals(CodeMessage.Code1026InsufficientFchOnChain, r.getCode());
        assertTrue(r.getData().isEmpty());
    }

    @Test
    void enoughButTooFragmentedSaysMerge() {
        SearchResult<Cash> r = CashManager.getValidCashes(DUST, 250 * COIN, null, null, 2, 0, null, es, null);
        assertEquals(CodeMessage.Code2011TooManyCashesToSpend, r.getCode());
        SearchResult<Cash> beyond = CashManager.getValidCashes(DUST, 400 * COIN, null, null, 2, 0, null, es, null);
        assertEquals(CodeMessage.Code1026InsufficientFchOnChain, beyond.getCode());
    }

    @Test
    void noCashAtAllIsCashNoFound() {
        SearchResult<Cash> r = CashManager.getValidCashes("FNobodyHasThisAddressAtAll11111111", COIN, null, null, 2, 0, null, es, null);
        assertEquals(CodeMessage.Code2007CashNoFound, r.getCode());
    }

    // ---- addresses with fewer than 200 cashes: exactly as before ----

    @Test
    void smallAddressesGetExactlyTheOldSelection() throws Exception {
        long total = sum(CashManager.getAllValidCashes(SMALL, 10_000, es, null).getData());
        for (long amount : new long[]{0, 1, COIN, 10 * COIN, total / 3, total / 2, total - 10 * COIN, total, total + COIN}) {
            for (int outputs : new int[]{0, 2}) {
                List<String> old = oldSelection(SMALL, amount, outputs);
                SearchResult<Cash> now = CashManager.getValidCashes(SMALL, amount, null, null, outputs, 0, null, es, null);
                if (old == null) {
                    assertTrue(now.hasError(), "amount " + amount);
                    assertTrue(now.getData().isEmpty());
                } else {
                    assertFalse(now.hasError(), "amount " + amount + ": " + now.getMessage());
                    assertEquals(old, now.getData().stream().map(Cash::getId).toList(), "amount " + amount);
                }
            }
        }
        // And the first 200 of a big address are still tried first: a small amount gets the same cashes.
        assertEquals(oldSelection(BIG, 50 * COIN, 2),
                CashManager.getValidCashes(BIG, 50 * COIN, null, null, 2, 0, null, es, null).getData().stream().map(Cash::getId).toList());
    }

    /** The pre-fix ES selection, verbatim in effect: 200 cashes by cd asc, id asc; greedy; null if short. */
    private List<String> oldSelection(String fid, long amount, int outputSize) throws Exception {
        BoolQuery.Builder b = new BoolQuery.Builder()
                .must(new Query(new TermQuery.Builder().field(FieldNames.OWNER).value(fid).build()))
                .must(new Query(new TermQuery.Builder().field(FieldNames.VALID).value(true).build()))
                .must(new Query(new co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery.Builder()
                        .field(FieldNames.BIRTH_HEIGHT).gt(co.elastic.clients.json.JsonData.of(0L)).build()));
        SearchResponse<Cash> result = es.search(s -> s.index(IndicesNames.CASH)
                .sort(s1 -> s1.field(f -> f.field(FieldNames.CD).order(SortOrder.Asc)))
                .sort(s2 -> s2.field(f -> f.field(FieldNames.ID).order(SortOrder.Asc)))
                .size(200).query(q -> q.bool(b.build())), Cash.class);
        long fchSum = 0, fee = 0;
        List<String> meet = new ArrayList<>();
        for (Hit<Cash> hit : result.hits().hits()) {
            Cash cash = hit.source();
            fchSum += cash.getValue();
            meet.add(cash.getId());
            fee = TxCreator.calcFee(TxCreator.calcTxSize(meet.size(), outputSize, 0), TxCreator.DEFAULT_FEE_RATE);
            if (fchSum >= amount + fee) return meet;
        }
        return null;
    }

    @AfterAll
    void cleanUp() {
        if (es == null) return;
        for (String idx : List.of(IndicesNames.CASH, IndicesNames.FREER, IndicesNames.BLOCK)) {
            try { es.indices().delete(d -> d.index(idx)); } catch (Exception ignored) { }
        }
    }
}
