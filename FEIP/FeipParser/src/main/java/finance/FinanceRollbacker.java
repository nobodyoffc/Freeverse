package finance;

import startFEIP.Reparser;
import constants.OpNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.EsUtils;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import constants.IndicesNames;
import data.feipData.ProofHistory;
import data.feipData.TokenHistory;
import utils.JsonUtils;

import java.util.*;

import static constants.FieldNames.*;
import static constants.OpNames.DESTROY;

public class FinanceRollbacker {

	private static final Logger log = LoggerFactory.getLogger(FinanceRollbacker.class);

	public boolean rollback(ElasticsearchClient esClient, long lastHeight) throws Exception {
		boolean error = false;
		error |= rollbackProof(esClient, lastHeight);
		error |= rollbackToken(esClient, lastHeight);
		return error;
	}

	private boolean rollbackProof(ElasticsearchClient esClient, long lastHeight) throws Exception {
		boolean error = false;
		Map<String, ArrayList<String>> resultMap = getEffectedProofs(esClient,lastHeight);
		ArrayList<String> itemIdList = resultMap.get("itemIdList");
		ArrayList<String> histIdList = resultMap.get("histIdList");

		if(itemIdList==null||itemIdList.isEmpty())return error;
		log.warn("If Rollbacking is interrupted, reparse all effected ids of index 'proof': ");
		JsonUtils.printJson(itemIdList);

		List<ProofHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.PROOF_HISTORY, PROOF_ID, PROOF_IDS, itemIdList, lastHeight, ProofHistory.class);

		error |= deleteEffectedItems(esClient, IndicesNames.PROOF, itemIdList);
		if(histIdList!=null&&!histIdList.isEmpty())
			error |= deleteRolledHists(esClient, IndicesNames.PROOF_HISTORY, histIdList);

		error |= reparseProof(esClient, reparseHistList);

		return error;
	}

	private Map<String, ArrayList<String>> getEffectedProofs(ElasticsearchClient esClient,long height) throws Exception {
		List<Hit<ProofHistory>> effectedHits = EsUtils.scanHitsAboveHeight(esClient, IndicesNames.PROOF_HISTORY, HEIGHT, height, ProofHistory.class);

		Set<String> itemSet = new HashSet<String>();
		ArrayList<String> histList = new ArrayList<String>();

		for(Hit<ProofHistory> hit: effectedHits) {

			ProofHistory item = hit.source();
			if(item==null || item.getOp()==null){
				continue;
			}
			String op = item.getOp();
			switch (op) {
				case OpNames.ISSUE -> {
					if(item.getId()==null){
						continue;
					}
					itemSet.add(item.getId());
				}
				case DESTROY ->{
					if(item.getProofIds()==null || item.getProofIds().isEmpty()){
						continue;
					}
					for(String proofId: item.getProofIds()){
						if(proofId==null){
							continue;
						}
						itemSet.add(proofId);
					}
				}
				default -> {
					if(item.getProofId()!=null){
						itemSet.add(item.getProofId());
					}
				}
			}

			histList.add(hit.id());
		}


		ArrayList<String> itemList = new ArrayList<String>(itemSet);

		Map<String,ArrayList<String>> resultMap = new HashMap<String,ArrayList<String>>();
		resultMap.put("itemIdList", itemList);
		resultMap.put("histIdList", histList);

		return resultMap;
	}

	private boolean reparseProof(ElasticsearchClient esClient, List<ProofHistory> reparseHistList) {
		FinanceParser parser = new FinanceParser();
		return Reparser.replay("proof", reparseHistList, h -> parser.parseProof(esClient, h));
	}

	private boolean rollbackToken(ElasticsearchClient esClient, long lastHeight) throws Exception {
		boolean error = false;
		Map<String, ArrayList<String>> resultMap = getEffectedTokens(esClient,lastHeight);
		ArrayList<String> tokenIdList = resultMap.get("itemIdList");
		ArrayList<String> histIdList = resultMap.get("histIdList");

		if(tokenIdList==null||tokenIdList.isEmpty())return error;
		log.warn("If Rollback is interrupted, reparse all effected ids of index 'token': ");
		JsonUtils.printJson(tokenIdList);

		List<TokenHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.TOKEN_HISTORY, TOKEN_ID, TOKEN_IDS, tokenIdList, lastHeight, TokenHistory.class);

		error |= deleteEffectedItems(esClient, IndicesNames.TOKEN, tokenIdList);
		if(histIdList!=null&&!histIdList.isEmpty())
			error |= deleteRolledHists(esClient, IndicesNames.TOKEN_HISTORY, histIdList);
		error |= deleteEffectedTokenHolders(esClient, tokenIdList);

		error |= reparseToken(esClient, reparseHistList);

		return error;
	}

	private Map<String, ArrayList<String>> getEffectedTokens(ElasticsearchClient esClient,long height) throws Exception {
		List<Hit<TokenHistory>> effectedHits = EsUtils.scanHitsAboveHeight(esClient, IndicesNames.TOKEN_HISTORY, "height", height, TokenHistory.class);

		Set<String> tokenIdSet = new HashSet<String>();
		ArrayList<String> histList = new ArrayList<String>();

		for(Hit<TokenHistory> hit: effectedHits) {

			TokenHistory item = hit.source();
			if(item==null || item.getOp()==null){
				continue;
			}
			String op = item.getOp();
			switch (op) {
				case OpNames.DEPLOY -> {
					if(item.getId()==null){
						continue;
					}
					tokenIdSet.add(item.getId());
				}
				case OpNames.CLOSE -> {
					if(item.getTokenIds()==null || item.getTokenIds().isEmpty()){
						continue;
					}
					for(String tokenId: item.getTokenIds()){
						if(tokenId==null){
							continue;
						}
						tokenIdSet.add(tokenId);
					}
				}
				default -> {
					if(item.getTokenId()!=null){
						tokenIdSet.add(item.getTokenId());
					}
				}
			}

			histList.add(hit.id());
		}


		ArrayList<String> itemList = new ArrayList<String>(tokenIdSet);

		Map<String,ArrayList<String>> resultMap = new HashMap<String,ArrayList<String>>();
		resultMap.put("itemIdList", itemList);
		resultMap.put("histIdList", histList);

		return resultMap;
	}

	private boolean deleteEffectedTokenHolders(ElasticsearchClient esClient,List<String> tokenIdList) throws Exception {
		List<FieldValue> fieldValueList = new ArrayList<>();
		tokenIdList.forEach(tokenId->fieldValueList.add(FieldValue.of(tokenId)));

		DeleteByQueryResponse response = esClient.deleteByQuery(d->d.index(IndicesNames.TOKEN_HOLDER)
				.conflicts(co.elastic.clients.elasticsearch._types.Conflicts.Proceed)
				.query(q->q
						.terms(t->t
								.field("tokenId")
								.terms(ts->ts.value(fieldValueList))
						)));
		if (response != null && response.failures() != null && !response.failures().isEmpty()) {
			log.error("Rollback: deleteByQuery reported {} failures on token_holder", response.failures().size());
			return true;
		}
		return false;
	}

	private boolean reparseToken(ElasticsearchClient esClient, List<TokenHistory> reparseHistList) {
		FinanceParser parser = new FinanceParser();
		return Reparser.replay("token", reparseHistList, h -> parser.parseToken(esClient, h));
	}

	private boolean deleteEffectedItems(ElasticsearchClient esClient,String index, ArrayList<String> itemIdList) throws Exception {
		BulkResponse response = EsUtils.bulkDeleteList(esClient, index, itemIdList);
		if (response != null && response.errors()) {
			log.error("Rollback: bulk delete reported errors on index {}", index);
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
}
