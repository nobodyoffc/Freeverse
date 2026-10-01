package fapi.chain;

import clients.EsClientMaker;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import data.apipData.Fcdsl;
import data.fcData.DiskItem;
import data.fcData.DockItem;
import data.fchData.Cash;
import data.fchData.OpReturn;
import data.feipData.Service;
import db.fcdsl.FcdslResult;
import fapi.components.disk.DiskMetaStore;
import fapi.components.dock.DockStore;
import fapi.migrate.EsIndexPages;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks against a live local Elasticsearch (http://127.0.0.1:9200), read-only. Skipped when
 * none answers. It runs the one-time migrations on the real DOCK and DISK indices, compares
 * the old ES queries with the same queries on the migrated stores, and checks EsChainSource
 * against direct ES counts.
 */
class LiveEsCheckTest {

    static ElasticsearchClient es;
    static EsClientMaker maker;

    @TempDir
    Path dir;

    @BeforeAll
    static void connect() throws Exception {
        boolean up;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:9200/").openConnection();
            c.setConnectTimeout(1000);
            c.setReadTimeout(2000);
            up = c.getResponseCode() == 200;
        } catch (Exception e) {
            up = false;
        }
        assumeTrue(up, "no Elasticsearch at 127.0.0.1:9200");
        maker = new EsClientMaker();
        es = maker.getClientHttp("127.0.0.1", 9200);
    }

    @AfterAll
    static void close() throws Exception {
        if (maker != null) maker.shutdownClient();
    }

    static List<String> indices(String suffix) throws Exception {
        List<String> out = new ArrayList<>();
        for (var r : es.cat().indices().valueBody()) {
            if (r.index() != null && r.index().endsWith(suffix) && !r.index().startsWith(".")) out.add(r.index());
        }
        return out;
    }

    static long count(String index) throws Exception {
        return es.count(c -> c.index(index)).count();
    }

    // ==================== DOCK ====================

    @Test
    void dockIndicesMigrateWhole() throws Exception {
        List<String> found = indices("_dock");
        assumeTrue(!found.isEmpty(), "no *_dock index");
        for (String index : found) {
            try (DockStore store = DockStore.open(dir.resolve(index), 100_000)) {
                long n = store.migrateFrom(new EsIndexPages<>(es, index, DockItem.class));
                assertEquals(count(index), n, index);
                assertEquals(n, store.count(), index);

                // every document came over with its data
                SearchResponse<DockItem> all = es.search(s -> s.index(index).size(10_000), DockItem.class);
                for (Hit<DockItem> h : all.hits().hits()) {
                    DockItem migrated = store.get(h.id());
                    assertNotNull(migrated, index + "/" + h.id());
                    assertEquals(h.source().getDataBase64(), migrated.getDataBase64());
                    assertEquals(h.source().getRecipients(), migrated.getRecipients());
                    assertEquals(h.source().getExpireHeight(), migrated.getExpireHeight());
                }
                compareDockQueries(index, store, all.hits().hits());
            }
        }
    }

    /**
     * The old dock.fetch query in ES (recipient, unexpired, sort createTime asc + id asc, then
     * the id DOCK appended) against the same request on the migrated store: same items, same
     * order, same cursor.
     */
    void compareDockQueries(String index, DockStore store, List<Hit<DockItem>> hits) throws Exception {
        Set<String> recipients = new TreeSet<>();
        TreeSet<Long> expiries = new TreeSet<>();
        for (Hit<DockItem> h : hits) {
            if (h.source().getRecipients() != null) recipients.addAll(h.source().getRecipients());
            if (h.source().getExpireHeight() != null) expiries.add(h.source().getExpireHeight());
        }
        List<Long> heights = new ArrayList<>(List.of(0L));
        if (!expiries.isEmpty()) heights.add(new ArrayList<>(expiries).get(expiries.size() / 2));
        for (String r : recipients) {
            for (long height : heights) {
                SearchResponse<DockItem> old = es.search(s -> s.index(index).size(50)
                        .query(q -> q.bool(b -> b
                                .must(m -> m.term(t -> t.field("recipients").value(r)))
                                .must(m -> m.range(g -> g.field("expireHeight").gt(JsonData.of(height))))))
                        .sort(o -> o.field(f -> f.field("createTime").order(SortOrder.Asc)))
                        .sort(o -> o.field(f -> f.field("id").order(SortOrder.Asc)))
                        .sort(o -> o.field(f -> f.field("id").order(SortOrder.Asc))), DockItem.class);
                List<String> esIds = new ArrayList<>();
                List<String> esLast = null;
                for (Hit<DockItem> h : old.hits().hits()) {
                    esIds.add(h.id());
                    esLast = h.sort().stream().map(v -> String.valueOf(v._get())).toList();
                }

                Fcdsl f = new Fcdsl();
                f.addSort("createTime", "asc");
                f.addSort("id", "asc");
                f.setSize("50");
                FcdslResult<DockItem> mine = store.find(f, List.of(r), height, 20, 50);
                List<String> myIds = mine.getItems().stream().map(DockItem::getId).toList();

                assertEquals(esIds, myIds, index + " recipient=" + r + " height=" + height);
                assertEquals(esLast, mine.getLast(), "cursor shape and values, " + index + " " + r);
            }
        }
    }

    // ==================== DISK ====================

    @Test
    void diskIndicesMigrateWholeWithTheSameTotalSize() throws Exception {
        List<String> found = indices("_disk");
        assumeTrue(!found.isEmpty(), "no *_disk index");
        for (String index : found) {
            try (DiskMetaStore store = DiskMetaStore.open(dir.resolve(index), 100_000)) {
                long n = store.migrateFrom(new EsIndexPages<>(es, index, DiskItem.class));
                assertEquals(count(index), n, index);
                var agg = es.search(s -> s.index(index).size(0)
                        .aggregations("total", a -> a.sum(x -> x.field("size"))), DiskItem.class);
                assertEquals((long) agg.aggregations().get("total").sum().value(), store.totalSize(), index);

                // DiskSyncManager's page against ES's
                SearchResponse<DiskItem> old = es.search(s -> s.index(index).size(100)
                        .sort(o -> o.field(f -> f.field("since").order(SortOrder.Asc)))
                        .sort(o -> o.field(f -> f.field("id").order(SortOrder.Asc))), DiskItem.class);
                Fcdsl f = new Fcdsl();
                f.addSort("since", "asc");
                f.addSort("id", "asc");
                f.addSize(100);
                FcdslResult<DiskItem> mine = store.query(store.compile(f));
                assertEquals(old.hits().hits().stream().map(Hit::id).toList(),
                        mine.getItems().stream().map(DiskItem::getId).toList(), index);
            }
        }
    }

    // ==================== EsChainSource ====================

    @Test
    void esChainSourceAgreesWithEs() throws Exception {
        assumeTrue(!indices("cash").isEmpty() && !indices("block").isEmpty(), "no chain indices");
        EsChainSource chain = new EsChainSource(es);

        SearchResponse<Void> top = es.search(s -> s.index("block").size(0)
                .aggregations("h", a -> a.max(m -> m.field("height"))), Void.class);
        assertEquals((long) top.aggregations().get("h").max().value(), chain.bestBlock().getHeight());

        // the owner with the most valid cashes, and a height leaving a few thousand of them
        SearchResponse<Void> owners = es.search(s -> s.index("cash").size(0)
                .query(q -> q.term(t -> t.field("valid").value(true)))
                .aggregations("o", a -> a.terms(t -> t.field("owner").size(1))), Void.class);
        String owner = owners.aggregations().get("o").sterms().buckets().array().get(0).key().stringValue();
        SearchResponse<Cash> nth = es.search(s -> s.index("cash").from(2499).size(1)
                .query(q -> q.bool(b -> b
                        .must(m -> m.term(t -> t.field("owner").value(owner)))
                        .must(m -> m.term(t -> t.field("valid").value(true)))))
                .sort(o -> o.field(f -> f.field("birthHeight").order(SortOrder.Desc))), Cash.class);
        long from = nth.hits().hits().isEmpty() ? 0 : nth.hits().hits().get(0).source().getBirthHeight() - 1;
        long expected = es.count(c -> c.index("cash").query(q -> q.bool(b -> b
                .must(m -> m.term(t -> t.field("owner").value(owner)))
                .must(m -> m.term(t -> t.field("valid").value(true)))
                .must(m -> m.range(r -> r.field("birthHeight").gt(JsonData.of(from))))))).count();

        List<Cash> got = chain.cashesOwnedSince(owner, from);
        assertEquals(expected, got.size(), "owner " + owner + " above " + from);
        assertEquals(expected, got.stream().map(Cash::getId).distinct().count(), "no cash twice across pages");
        for (int i = 1; i < got.size(); i++) {
            Cash a = got.get(i - 1), b = got.get(i);
            int c = Long.compare(a.getBirthHeight(), b.getBirthHeight());
            assertTrue(c < 0 || (c == 0 && a.getId().compareTo(b.getId()) < 0), "order at " + i);
        }
        assertTrue(expected > EsChainSource.CASH_PAGE, "paging exercised: " + expected);

        List<Cash> valid = chain.validCashes(owner, 50);
        assertEquals(50, valid.size());
        for (int i = 1; i < valid.size(); i++) assertTrue(valid.get(i - 1).getCd() <= valid.get(i).getCd());

        SearchResponse<OpReturn> ops = es.search(s -> s.index("opreturn").size(150), OpReturn.class);
        List<String> txIds = ops.hits().hits().stream().map(Hit::id).toList();
        Set<String> withText = new HashSet<>();
        for (Hit<OpReturn> h : ops.hits().hits()) if (h.source().getOpReturn() != null) withText.add(h.id());
        assertEquals(withText, chain.opReturns(txIds).keySet());

        SearchResponse<Service> svcs = es.search(s -> s.index("service").size(100), Service.class);
        List<String> sids = svcs.hits().hits().stream().map(Hit::id).toList();
        assertEquals(new HashSet<>(sids), chain.services(sids).keySet());
        assertTrue(chain.services(List.of("not-a-sid")).isEmpty());
    }
}
