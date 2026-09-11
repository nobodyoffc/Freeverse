package identity;

import startFEIP.Reparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import startFEIP.FeipConstants;
import utils.EsUtils;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import constants.IndicesNames;
import data.fchData.Freer;
import data.feipData.FreerHist;
import data.feipData.RepuHist;
import utils.JsonUtils;

import java.util.*;

import static constants.FieldNames.SIGNER;

public class IdentityRollbacker {

	private static final Logger log = LoggerFactory.getLogger(IdentityRollbacker.class);

	public boolean rollback(ElasticsearchClient esClient, long height) throws Exception {
		boolean error = false;
		error |= rollbackCid(esClient, height);
		error |= rollbackRepu(esClient, height);
		error |= rollbackNid(esClient, height);
		return error;
	}

	public boolean rollbackNid(ElasticsearchClient esClient, long lastHeight) throws Exception {
		List<String> indexList = new ArrayList<String>();
		indexList.add(IndicesNames.NID);

		DeleteByQueryResponse response = esClient.deleteByQuery(d->d.index(indexList)
				.conflicts(co.elastic.clients.elasticsearch._types.Conflicts.Proceed)
				.query(q->q.range(r->r.field("birthHeight").gt(JsonData.of(lastHeight)))));
		if (response != null && response.failures() != null && !response.failures().isEmpty()) {
			log.error("Rollback: deleteByQuery reported {} failures on nid", response.failures().size());
			return true;
		}
		return false;
	}

	private boolean rollbackCid(ElasticsearchClient esClient, long height) throws Exception {
		boolean error = false;
		Map<String, ArrayList<String>> resultMap = getEffectedCidAndHistory(esClient,height);
		ArrayList<String> signerList = resultMap.get("signerList");
		ArrayList<String> histIdList = resultMap.get("histIdList");

		if(signerList==null || signerList.isEmpty())return error;

		log.warn("If Rollbacking is interrupted, reparse all effected ids of index 'cid': ");
		JsonUtils.printJson(signerList);

		// Query reparse data BEFORE deleting, so it's available even if crash occurs mid-rollback
		List<FreerHist> reparseList = EsUtils.getHistsForReparse(esClient, IndicesNames.FREER_HISTORY, SIGNER, null, signerList, height, FreerHist.class);

		error |= deleteEffectedCids(esClient, signerList);
		error |= deleteRolledHists(esClient, IndicesNames.FREER_HISTORY, histIdList);

		error |= reparse(esClient, reparseList);

		return error;
	}

	private Map<String, ArrayList<String>> getEffectedCidAndHistory(ElasticsearchClient esClient, long height) throws Exception {
	
		List<Hit<FreerHist>> effectedHits = EsUtils.scanHitsAboveHeight(esClient, IndicesNames.FREER_HISTORY, "height", height, FreerHist.class);
		
		Set<String> signerSet = new HashSet<String>();
		ArrayList<String> idList = new ArrayList<String>();

		for(Hit<FreerHist> hit: effectedHits) {
			if(hit.source()==null){
				log.info("Cid hist is null");
				continue;
			}
			signerSet.add(hit.source().getSigner());
			idList.add(hit.id());
		}
		

		ArrayList<String> signerList = new ArrayList<String>(signerSet);
		
		Map<String,ArrayList<String>> resultMap = new HashMap<String,ArrayList<String>>();
		resultMap.put("signerList", signerList);
		resultMap.put("histIdList", idList);
		
		return resultMap;
	}

	/**
	 * Clear FEIP-managed fields from affected Freer documents instead of deleting them,
	 * so that blockchain fields (balance, cash, income, cd, cdd, weight, etc.) written
	 * by BlockWriter are preserved.
	 */
	private boolean deleteEffectedCids(ElasticsearchClient esClient, ArrayList<String> signerList) throws Exception {
		if (signerList == null || signerList.isEmpty()) return false;

		Map<String, Object> clearFields = new HashMap<>();
		for (String field : FeipConstants.FREER_FEIP_FIELDS) {
			clearFields.put(field, null);
		}

		BulkRequest.Builder br = new BulkRequest.Builder();
		for (String signer : signerList) {
			br.operations(op -> op.update(u -> u
					.index(IndicesNames.FREER)
					.id(signer)
					.action(a -> a.doc(clearFields))));
		}
		BulkResponse response = esClient.bulk(br.build());
		if (response.errors()) {
			log.error("Rollback: clearing FEIP fields on freer reported errors");
			return true;
		}
		return false;
	}

	private boolean deleteRolledHists(ElasticsearchClient esClient, String index, ArrayList<String> histIdList) throws Exception {
		BulkResponse response = EsUtils.bulkDeleteList(esClient, index, histIdList);
		if (response != null && response.errors()) {
			log.error("Rollback: bulk delete reported errors on index {}", index);
			return true;
		}
		return false;
	}

	private boolean reparse(ElasticsearchClient esClient, List<FreerHist> reparseList) {
		IdentityParser parser = new IdentityParser();
		return Reparser.replay("freer", reparseList, h -> parser.parseCidInfo(esClient, h));
	}

	private boolean rollbackRepu(ElasticsearchClient esClient, long height) throws Exception {
		boolean error = false;
		Map<String, ArrayList<String>> resultMap = getEffectedCidAndRepuHistory(esClient,height);
		ArrayList<String> rateeList = resultMap.get("rateeList");
		ArrayList<String> histIdList = resultMap.get("histIdList");
		
		if(rateeList==null || rateeList.isEmpty())return error;

		error |= deleteRolledHists(esClient, IndicesNames.REPUTATION_HISTORY, histIdList);
		// reviseCidRepuAndHot re-aggregates the rows that survive the
		// delete, and a bulk delete is not visible to search until the
		// index refreshes. Without this the aggregation can still count
		// the rows just removed and "restore" the pre-rollback totals.
		esClient.indices().refresh(r->r.index(IndicesNames.REPUTATION_HISTORY));

		error |= reviseCidRepuAndHot(esClient,rateeList);

		return error;

	}

	private Map<String, ArrayList<String>> getEffectedCidAndRepuHistory(ElasticsearchClient esClient, long height) throws Exception {
		List<Hit<RepuHist>> effectedHits = EsUtils.scanHitsAboveHeight(esClient, IndicesNames.REPUTATION_HISTORY, "height", height, RepuHist.class);
		
		Set<String> rateeSet = new HashSet<String>();
		ArrayList<String> idList = new ArrayList<String>();

		for(Hit<RepuHist> hit: effectedHits) {
			if(hit.source()==null){
				log.info("Repu hist is null");
				continue;
			}
			rateeSet.add(hit.source().getRatee());
			idList.add(hit.id());
		}
		

		ArrayList<String> rateeList = new ArrayList<String>(rateeSet);
		
		Map<String,ArrayList<String>> resultMap = new HashMap<String,ArrayList<String>>();
		resultMap.put("rateeList", rateeList);
		resultMap.put("histIdList", idList);
		
		return resultMap;
	}
	/**
	 * @return true if any of the bulk updates reported errors
	 */
	public boolean reviseCidRepuAndHot(ElasticsearchClient esClient, ArrayList<String> rateeList) throws Exception {
		boolean error = false;
		int i = 0;
		while(true) {
			ArrayList<String> rateeSubList = new ArrayList<String> ();
			for(int j = i; j<i+ EsUtils.WRITE_MAX; j++) {
				if(j>=rateeList.size())break;
				rateeSubList.add(rateeList.get(j));
			}
			Map<String,HashMap<String,Long>> reviseMapMap
				= aggsRepuAndHot(esClient,rateeSubList);

			// A ratee whose every rating fell inside the rolled-back
			// range has no surviving history row, so the terms
			// aggregation yields no bucket for it and it would be left
			// carrying the totals the rollback was meant to undo.
			// Absent history means zero, not "unchanged".
			for(String ratee:rateeSubList) {
				if(reviseMapMap.containsKey(ratee))continue;
				HashMap<String,Long> zeroed = new HashMap<String,Long>();
				zeroed.put("reputation", 0L);
				zeroed.put("hot", 0L);
				reviseMapMap.put(ratee, zeroed);
			}

			error |= updataRepuAndHot(esClient,reviseMapMap);

			i += rateeSubList.size();
			if(i>=rateeList.size())break;
		}
		return error;
	}
	private Map<String, HashMap<String, Long>> aggsRepuAndHot(ElasticsearchClient esClient,
			ArrayList<String> rateeSubList) throws Exception {

		List<FieldValue> fieldValueList = new ArrayList<FieldValue>();
		for(String ratee:rateeSubList) {
			fieldValueList.add(FieldValue.of(ratee));
		}
		
		SearchResponse<Void> response = esClient.search(s->s
				.index(IndicesNames.REPUTATION_HISTORY)
				.size(0)
				.aggregations("rateeFilter",a->a
						.filter(f->f
								.terms(t->t
										.field("ratee")
										.terms(t1->t1.value(fieldValueList))))
						.aggregations("rateeTerm",a1->a1
								// One bucket per ratee asked for. Without a size the terms
								// aggregation returns 10 buckets, and the caller zeroes every
								// ratee it did not get a bucket for.
								.terms(t2->t2
										.field("ratee")
										.size(rateeSubList.size()))
								.aggregations("repuSum",a2->a2.sum(s1->s1.field("reputation")))
								.aggregations("hotSum",a2->a2.sum(s1->s1.field("hot")))
								))
				, void.class);
		
		
		 List<StringTermsBucket> rateeBucketList = response.aggregations().get("rateeFilter").filter().aggregations().get("rateeTerm").sterms().buckets().array();

		 Map<String,HashMap<String,Long>> reviseMapMap = new  HashMap<String,HashMap<String,Long>>();
		 
		 for(StringTermsBucket bucket:rateeBucketList) {
			String ratee = bucket.key().stringValue();
			HashMap<String,Long> values = new HashMap<String,Long>();
			long repuSum = 0;
			long hotSum = 0;
			repuSum = (long) bucket.aggregations().get("repuSum").sum().value();
			hotSum = (long) bucket.aggregations().get("hotSum").sum().value();
			
			values.put("reputation", repuSum);
			values.put("hot", hotSum);
			
			reviseMapMap.put(ratee, values);
		 }
		return reviseMapMap;
	}
	private boolean updataRepuAndHot(ElasticsearchClient esClient, Map<String, HashMap<String, Long>> reviseMapMap) throws Exception {
		if(reviseMapMap.isEmpty())return false;

		// Weight is derived from reputation, so restoring reputation
		// without recomputing it leaves a number that was calculated
		// from a rating history that no longer exists.
		// IdentityParser.parseReputation calls reCalcWeight on every
		// write; the rollback has to do the same or it half-undoes the
		// parse. cd and cdd are read as they stand - this protocol never
		// touches them, and their own rollback owns them.
		List<String> rateeIdList = new ArrayList<String>(reviseMapMap.keySet());
		Map<String,Freer> freerMap = new HashMap<String,Freer>();
		EsUtils.MgetResult<Freer> mgetResult
			= EsUtils.getMultiByIdList(esClient, IndicesNames.FREER, rateeIdList, Freer.class);
		if(mgetResult!=null && mgetResult.getResultList()!=null) {
			for(Freer freer:mgetResult.getResultList()) {
				if(freer==null || freer.getId()==null)continue;
				freerMap.put(freer.getId(), freer);
			}
		}

		BulkRequest.Builder br = new BulkRequest.Builder();

		Set<String> rateeSet = reviseMapMap.keySet();
		for(String ratee:rateeSet) {
			HashMap<String,Long> doc = reviseMapMap.get(ratee);
			Freer freer = freerMap.get(ratee);
			if(freer==null) {
				// No Freer to read cd and cdd from. The update below
				// will fail for this id anyway; leaving weight out is
				// better than writing one computed from assumed zeros.
				log.info("Freer is not found, weight is not revised: " + ratee);
			}else {
				doc.put("weight", core.fch.Weight.calcWeight(
						freer.getCd() != null ? freer.getCd() : 0,
						freer.getCdd() != null ? freer.getCdd() : 0,
						doc.get("reputation")));
			}
			br.operations(o->o
					.update(u->u
							.index(IndicesNames.FREER)
							.id(ratee)
							.action(a->a
									.doc(doc))));
		}
		br.timeout(t->t.time("600s"));
		BulkResponse response = esClient.bulk(br.build());
		if (response.errors()) {
			log.error("Rollback: revising reputation and hot on freer reported errors");
			return true;
		}
		return false;
	}


}
