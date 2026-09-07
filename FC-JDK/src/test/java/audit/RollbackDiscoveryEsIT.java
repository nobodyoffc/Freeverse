package audit;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import clients.EsClientMaker;
import data.feipData.ProtocolHistory;
import org.junit.jupiter.api.*;
import utils.EsUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration coverage for the two rollback-discovery fixes, against a local Elasticsearch.
 *
 * These are the fixes that could not be unit-tested and were shipped verified-by-reading only:
 *
 *  1. The getEffected* queries specified no `size`, so Elasticsearch returned its default of
 *     10 hits and a reorg touching more than 10 histories reverted only 10.
 *  2. getHistsForReparse ran a two-field OR on page one and dropped the second clause on every
 *     later page, losing histories matched only by the multi-id field past READ_MAX.
 *
 * Uses its own audittest_* indices and deletes them afterwards; it never touches real data.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RollbackDiscoveryEsIT {

    private static final String IDX = "audittest_protocol_history";
    private static final long ROLLBACK_HEIGHT = 1000L;

    private ElasticsearchClient esClient;

    @BeforeAll
    void connect() {
        esClient = new EsClientMaker().getClientHttp("127.0.0.1", 9200);
    }

    /** Each test starts from an empty index so results cannot depend on execution order. */
    @BeforeEach
    void freshIndex() throws Exception {
        assumeConnected();
        deleteIndexQuietly();
        createIndex();
    }

    @AfterAll
    void cleanup() {
        if (esClient != null) deleteIndexQuietly();
    }

    private void assumeConnected() {
        Assumptions.assumeTrue(esClient != null, "No Elasticsearch on 127.0.0.1:9200");
    }

    private void deleteIndexQuietly() {
        try {
            esClient.indices().delete(d -> d.index(IDX));
        } catch (Exception ignored) {
        }
    }

    private void createIndex() throws Exception {
        esClient.indices().create(c -> c.index(IDX).mappings(m -> m
                .properties("id", p -> p.keyword(k -> k))
                .properties("height", p -> p.long_(l -> l))
                .properties("index", p -> p.short_(sh -> sh))
                .properties("pid", p -> p.keyword(k -> k))
                .properties("pids", p -> p.keyword(k -> k))
                .properties("op", p -> p.keyword(k -> k))));
    }

    private void indexHistories(int count, String pidValue, boolean useMultiIdField) throws Exception {
        indexHistories(count, pidValue, useMultiIdField, 1);
    }

    /**
     * Index `count` histories above the rollback height, matched either by `pid` or by `pids`.
     * `heightOffset` controls where they land in the height sort, which decides which
     * search_after page they fall on.
     */
    private void indexHistories(int count, String pidValue, boolean useMultiIdField, long heightOffset) throws Exception {
        List<ProtocolHistory> batch = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ProtocolHistory h = new ProtocolHistory();
            String id = (useMultiIdField ? "multi-" : "single-") + pidValue + "-" + i;
            h.setId(id);
            h.setHeight(ROLLBACK_HEIGHT + heightOffset + i);
            h.setIndex(i % 100);
            h.setOp("update");
            if (useMultiIdField) h.setPids(new ArrayList<>(List.of(pidValue)));
            else h.setPid(pidValue);
            batch.add(h);
            ids.add(id);
        }
        for (int start = 0; start < batch.size(); start += 500) {
            int end = Math.min(start + 500, batch.size());
            BulkRequest.Builder br = new BulkRequest.Builder();
            for (int i = start; i < end; i++) {
                ProtocolHistory doc = batch.get(i);
                String id = ids.get(i);
                br.operations(op -> op.index(idx -> idx.index(IDX).id(id).document(doc)));
            }
            BulkResponse resp = esClient.bulk(br.build());
            assertFalse(resp.errors(), "bulk indexing failed");
        }
        esClient.indices().refresh(r -> r.index(IDX));
    }

    @Test
    @DisplayName("Discovery returns every affected history, not Elasticsearch's default 10")
    void scanReturnsMoreThanTenHits() throws Exception {
        indexHistories(250, "pidA", false);

        List<Hit<ProtocolHistory>> hits = EsUtils.scanHitsAboveHeight(
                esClient, IDX, "height", ROLLBACK_HEIGHT, ProtocolHistory.class);

        // Pre-fix this returned exactly 10 and the other 240 were never rolled back.
        assertEquals(250, hits.size(), "must see every history above the rollback height");
    }

    @Test
    @DisplayName("Discovery pages past READ_MAX without skipping or repeating")
    void scanPagesBeyondReadMax() throws Exception {
        int total = EsUtils.READ_MAX + 337;   // forces a second page
        indexHistories(total, "pidB", false);

        List<Hit<ProtocolHistory>> hits = EsUtils.scanHitsAboveHeight(
                esClient, IDX, "height", ROLLBACK_HEIGHT, ProtocolHistory.class);

        assertEquals(total, hits.size(), "paging must not lose documents");
        long distinct = hits.stream().map(Hit::id).distinct().count();
        assertEquals(total, distinct, "paging must not repeat documents");
    }

    @Test
    @DisplayName("getHistsForReparse keeps the second terms clause past page one")
    void reparseKeepsSecondClauseAcrossPages() throws Exception {
        // Enough single-field matches to fill page one on their own, plus multi-field matches
        // that can only be found via the second clause.
        int singles = EsUtils.READ_MAX;
        int multis = 150;
        // The multi-id histories must sort AFTER the first page, otherwise they ride along on
        // page one and the dropped clause on later pages costs nothing.
        indexHistories(singles, "pidC", false, 1);
        indexHistories(multis, "pidC", true, singles + 10);

        ArrayList<String> ids = new ArrayList<>(List.of("pidC"));
        List<ProtocolHistory> found = EsUtils.getHistsForReparse(
                esClient, IDX, "pid", "pids", ids, ProtocolHistory.class);

        assertNotNull(found, "must return a list, never null");
        // Pre-fix: page one returned 1000 (both clauses), then the continuation dropped the
        // `pids` clause, so the 150 multi-id histories past the page boundary were lost.
        assertEquals(singles + multis, found.size(),
                "histories matched only by the second field must survive pagination");
    }

    @Test
    @DisplayName("getHistsForReparse returns an empty list, not null, when nothing matches")
    void reparseReturnsEmptyListNotNull() throws Exception {
        List<ProtocolHistory> found = EsUtils.getHistsForReparse(
                esClient, IDX, "pid", "pids", new ArrayList<>(List.of("no-such-pid")),
                ProtocolHistory.class);
        assertNotNull(found, "pre-fix this returned null and every caller had to null-check");
        assertTrue(found.isEmpty());
    }

}
