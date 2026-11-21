package writeEs;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import constants.IndicesNames;
import data.fchData.Cash;
import data.fchData.Freer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.FchUtils;

import java.io.IOException;
import java.util.*;

import static constants.FieldNames.*;

/**
 * Repairs Freer documents written wrong by BlockMaker before the CTOR fix.
 *
 * Blocks are in txid order, so a tx can spend a cash created by a later tx in the same block.
 * When that happened to an address new in the block, the spend started its balance at
 * +value and its cash count at +1 instead of -value and -1, so the stored balance is
 * 2 x value too high and the cash count 2 too high for ever after. The guide could also be
 * set to the address itself, from its own spend, instead of the address that paid it.
 *
 * Income and expend were added correctly, so a hurt document is one whose balance is not
 * income - expend. Those are recomputed from the cash index; lastHeight is left alone.
 */
public class FreerRepairer {
    private static final Logger log = LoggerFactory.getLogger(FreerRepairer.class);
    private static final int PAGE = 1000;

    static final String MISMATCH_SCRIPT =
            "if (doc['balance'].size()==0 || doc['income'].size()==0 || doc['expend'].size()==0) return false;"
            + " return doc['balance'].value != doc['income'].value - doc['expend'].value;";

    /** Finds and repairs every hurt Freer. Returns how many were rewritten. */
    public long repair(ElasticsearchClient esClient) throws IOException {
        Set<String> done = new HashSet<>();
        long fixedGuides = 0;
        while (true) {
            SearchResponse<Freer> result = esClient.search(s -> s
                    .index(IndicesNames.FREER)
                    .size(PAGE)
                    .query(q -> q.script(sc -> sc.script(scr -> scr.inline(i -> i.source(MISMATCH_SCRIPT))))),
                    Freer.class);

            List<Freer> page = new ArrayList<>();
            for (Hit<Freer> hit : result.hits().hits()) {
                Freer freer = hit.source();
                if (freer == null || freer.getId() == null || done.contains(freer.getId())) continue;
                page.add(freer);
            }
            if (page.isEmpty()) break;

            fixedGuides += repairPage(esClient, page);
            for (Freer freer : page) done.add(freer.getId());
            System.out.println("Repaired " + done.size() + " Freer documents so far...");
            esClient.indices().refresh(r -> r.index(IndicesNames.FREER));
        }
        System.out.println("Done. " + done.size() + " Freer documents repaired, " + fixedGuides + " guides corrected.");
        log.info("Freer repair: {} documents, {} guides.", done.size(), fixedGuides);
        return done.size();
    }

    private long repairPage(ElasticsearchClient esClient, List<Freer> page) throws IOException {
        List<String> ids = new ArrayList<>();
        for (Freer freer : page) ids.add(freer.getId());
        Map<String, Map<String, Long>> aggs = FchUtils.aggsTxoByAddrs(esClient, ids);
        Map<String, Long> utxoSum = aggs.get(FchUtils.UTXO_SUM);
        Map<String, Long> utxoCount = aggs.get(FchUtils.UTXO_COUNT);
        Map<String, Long> stxoSum = aggs.get(FchUtils.STXO_SUM);
        Map<String, Long> txoSum = aggs.get(FchUtils.TXO_SUM);
        Map<String, Long> cdd = aggs.get(FchUtils.CDD);

        BulkRequest.Builder br = new BulkRequest.Builder();
        long guides = 0;
        for (Freer freer : page) {
            String id = freer.getId();
            Map<String, Object> update = new HashMap<>();
            update.put(BALANCE, utxoSum.getOrDefault(id, 0L));
            update.put(CASH, utxoCount.getOrDefault(id, 0L));
            update.put(INCOME, txoSum.getOrDefault(id, 0L));
            update.put(EXPEND, stxoSum.getOrDefault(id, 0L));
            update.put(CDD, cdd.getOrDefault(id, 0L));

            if (freer.getGuide() == null || id.equals(freer.getGuide())) {
                String guide = firstPayer(esClient, id);
                if (guide != null) {
                    update.put("guide", guide);
                    guides++;
                }
            }

            log.info("Freer {}: balance {} -> {}, cash {} -> {}, guide {} -> {}", id,
                    freer.getBalance(), update.get(BALANCE), freer.getCash(), update.get(CASH),
                    freer.getGuide(), update.getOrDefault("guide", freer.getGuide()));

            br.operations(o -> o.update(u -> u
                    .index(IndicesNames.FREER)
                    .id(id)
                    .retryOnConflict(5)
                    .action(a -> a.doc(update))));
        }

        BulkResponse response = esClient.bulk(br.build());
        if (response.errors()) {
            for (BulkResponseItem item : response.items()) {
                if (item.error() != null) log.error("Freer {} not repaired: {}", item.id(), item.error().reason());
            }
            throw new IOException("Some Freer documents were not repaired; see the log and run again.");
        }
        return guides;
    }

    /** The address that first paid `fid` other than itself: the issuer of its earliest such cash. */
    private String firstPayer(ElasticsearchClient esClient, String fid) throws IOException {
        SearchResponse<Cash> result = esClient.search(s -> s
                .index(IndicesNames.CASH)
                .size(1)
                .query(q -> q.bool(b -> b
                        .filter(f -> f.term(t -> t.field(OWNER).value(fid)))
                        .mustNot(m -> m.term(t -> t.field(ISSUER).value(fid)))))
                .sort(so -> so.field(f -> f.field(BIRTH_HEIGHT).order(SortOrder.Asc)))
                .sort(so -> so.field(f -> f.field(BIRTH_TX_INDEX).order(SortOrder.Asc))),
                Cash.class);
        if (result.hits().hits().isEmpty()) return null;
        Cash cash = result.hits().hits().get(0).source();
        return cash == null ? null : cash.getIssuer();
    }
}
