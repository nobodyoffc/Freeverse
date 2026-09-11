package personal;

import startFEIP.Reparser;
import constants.OpNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.EsUtils;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import constants.IndicesNames;
import data.feipData.BoxHistory;
import utils.JsonUtils;

import java.util.*;

import static constants.FieldNames.BID;
import static constants.FieldNames.BIDS;

public class PersonalRollbacker {

	private static final Logger log = LoggerFactory.getLogger(PersonalRollbacker.class);

	public boolean rollback(ElasticsearchClient esClient, long lastHeight) throws Exception {
		boolean error = rollbackBox(esClient, lastHeight);

		// NOTE: contact, mail and secret have no *_HISTORY index, so the only thing that can be
		// undone here is the creation of documents born after lastHeight. Post-creation state
		// changes (delete / recover) made at a rolled-back height cannot be reverted, because the
		// information needed to replay them was never persisted. See the audit triage report.
		List<String> indexList = new ArrayList<String>();
		indexList.add(IndicesNames.CONTACT);
		indexList.add(IndicesNames.MAIL);
		indexList.add(IndicesNames.SECRET);
		DeleteByQueryResponse response = esClient.deleteByQuery(d->d.index(indexList)
				.conflicts(co.elastic.clients.elasticsearch._types.Conflicts.Proceed)
				.query(q->q.range(r->r.field("birthHeight").gt(JsonData.of(lastHeight)))));
		if (response != null && response.failures() != null && !response.failures().isEmpty()) {
			log.error("Rollback: deleteByQuery reported {} failures on contact/mail/secret", response.failures().size());
			error = true;
		}
		return error;
	}


	private boolean rollbackBox(ElasticsearchClient esClient, long lastHeight) throws Exception {
		boolean error = false;
		Map<String, ArrayList<String>> resultMap = getEffectedBoxes(esClient,lastHeight);
		ArrayList<String> itemIdList = resultMap.get("itemIdList");
		ArrayList<String> histIdList = resultMap.get("histIdList");

		if(itemIdList==null||itemIdList.isEmpty())return false;
		log.warn("If rolling back is interrupted, reparse all effected ids of index 'box': ");
		JsonUtils.printJson(itemIdList);

		List<BoxHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.BOX_HISTORY, BID, BIDS, itemIdList, lastHeight, BoxHistory.class);

		error |= deleteEffectedItems(esClient, IndicesNames.BOX, itemIdList);
		if(histIdList!=null&&!histIdList.isEmpty())
			error |= deleteRolledHists(esClient, IndicesNames.BOX_HISTORY, histIdList);

		error |= reparseBox(esClient, reparseHistList);

		return error;
	}

	private Map<String, ArrayList<String>> getEffectedBoxes(ElasticsearchClient esClient, long lastHeight) throws Exception {
		List<Hit<BoxHistory>> effectedHits = EsUtils.scanHitsAboveHeight(esClient, IndicesNames.BOX_HISTORY, "height", lastHeight, BoxHistory.class);

		Set<String> itemSet = new HashSet<String>();
		ArrayList<String> histList = new ArrayList<String>();

		for(Hit<BoxHistory> hit: effectedHits) {

			BoxHistory item = hit.source();
			if(item==null){
				log.info("Box hist is null");
				continue;
			}
			String op = item.getOp();
			switch (op) {
				case OpNames.CREATE -> {
					if(item.getId()==null){
						continue;
					}
					itemSet.add(item.getId());
				}
				case OpNames.RECOVER -> {
					if(item.getBids()==null || item.getBids().isEmpty()){
						continue;
					}
					for(String bid: item.getBids()){
						if(bid==null){
							continue;
						}
						itemSet.add(bid);
					}
				}
				default -> {
					if(item.getBid()!=null){
						itemSet.add(item.getBid());
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

	private boolean reparseBox(ElasticsearchClient esClient, List<BoxHistory> reparseHistList) {
		PersonalParser parser = new PersonalParser();
		return Reparser.replay("box", reparseHistList, h -> parser.parseBox(esClient, h));
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
