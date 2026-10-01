package fapi.chain;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import constants.FieldNames;
import constants.IndicesNames;
import data.fchData.Block;
import data.fchData.Cash;
import data.fchData.OpReturn;
import data.feipData.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Reads the chain from the local Elasticsearch that the parsers fill. */
public class EsChainSource implements ChainSource {

    static final int CASH_PAGE = 1000;
    static final int IDS_BATCH = 100;

    private final ElasticsearchClient esClient;

    public EsChainSource(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    @Override
    public Block bestBlock() {
        try {
            SearchResponse<Block> r = esClient.search(s -> s
                    .index(IndicesNames.BLOCK)
                    .size(1)
                    .sort(o -> o.field(f -> f.field(FieldNames.HEIGHT).order(SortOrder.Desc))), Block.class);
            List<Hit<Block>> hits = r.hits().hits();
            if (hits.isEmpty() || hits.get(0).source() == null) {
                throw new ChainUnavailableException("No block in Elasticsearch");
            }
            return hits.get(0).source();
        } catch (ChainUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new ChainUnavailableException("Best block query failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Cash> cashesOwnedSince(String owner, long fromHeight) {
        List<Cash> out = new ArrayList<>();
        List<FieldValue> after = null;
        try {
            while (true) {
                SearchRequest.Builder b = new SearchRequest.Builder()
                        .index(IndicesNames.CASH)
                        .size(CASH_PAGE)
                        .sort(o -> o.field(f -> f.field(FieldNames.BIRTH_HEIGHT).order(SortOrder.Asc)))
                        .sort(o -> o.field(f -> f.field(FieldNames.ID).order(SortOrder.Asc)));
                BoolQuery.Builder q = new BoolQuery.Builder()
                        .must(m -> m.term(t -> t.field(FieldNames.OWNER).value(owner)))
                        .must(m -> m.term(t -> t.field(FieldNames.VALID).value(true)))
                        .must(m -> m.range(r -> r.field(FieldNames.BIRTH_HEIGHT).gt(JsonData.of(fromHeight))));
                b.query(Query.of(x -> x.bool(q.build())));
                if (after != null) b.searchAfter(after);

                SearchResponse<Cash> r = esClient.search(b.build(), Cash.class);
                List<Hit<Cash>> hits = r.hits().hits();
                for (Hit<Cash> h : hits) if (h.source() != null) out.add(h.source());
                if (hits.size() < CASH_PAGE) return out;
                after = hits.get(hits.size() - 1).sort();
            }
        } catch (Exception e) {
            throw new ChainUnavailableException("Cash query for " + owner + " failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, String> opReturns(Collection<String> txIds) {
        Map<String, String> out = new HashMap<>();
        List<String> ids = new ArrayList<>(new LinkedHashSet<>(txIds));
        try {
            for (int i = 0; i < ids.size(); i += IDS_BATCH) {
                List<String> batch = ids.subList(i, Math.min(i + IDS_BATCH, ids.size()));
                SearchResponse<OpReturn> r = esClient.search(s -> s
                        .index(IndicesNames.OPRETURN)
                        .size(batch.size())
                        .query(q -> q.ids(x -> x.values(batch))), OpReturn.class);
                for (Hit<OpReturn> h : r.hits().hits()) {
                    if (h.id() != null && h.source() != null && h.source().getOpReturn() != null) {
                        out.put(h.id(), h.source().getOpReturn());
                    }
                }
            }
            return out;
        } catch (Exception e) {
            throw new ChainUnavailableException("OpReturn query failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Cash> validCashes(String fid, int limit) {
        try {
            SearchResponse<Cash> r = esClient.search(s -> s
                    .index(IndicesNames.CASH)
                    .size(limit)
                    .sort(o -> o.field(f -> f.field(FieldNames.CD).order(SortOrder.Asc)))
                    .sort(o -> o.field(f -> f.field(FieldNames.ID).order(SortOrder.Asc)))
                    .query(q -> q.bool(b -> b
                            .must(m -> m.term(t -> t.field(FieldNames.OWNER).value(fid)))
                            .must(m -> m.term(t -> t.field(FieldNames.VALID).value(true))))), Cash.class);
            List<Cash> out = new ArrayList<>();
            for (Hit<Cash> h : r.hits().hits()) if (h.source() != null) out.add(h.source());
            return out;
        } catch (Exception e) {
            throw new ChainUnavailableException("Valid cash query for " + fid + " failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Service> services(Collection<String> sids) {
        Map<String, Service> out = new HashMap<>();
        List<String> ids = new ArrayList<>(new LinkedHashSet<>(sids));
        if (ids.isEmpty()) return out;
        try {
            var r = esClient.mget(m -> m.index(IndicesNames.SERVICE).ids(ids), Service.class);
            for (var item : r.docs()) {
                if (item.isResult() && item.result().found() && item.result().source() != null) {
                    out.put(item.result().id(), item.result().source());
                }
            }
            return out;
        } catch (Exception e) {
            throw new ChainUnavailableException("Service query failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String describe() {
        return "local Elasticsearch";
    }
}
