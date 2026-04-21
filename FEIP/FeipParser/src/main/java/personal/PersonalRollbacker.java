package personal;

import constants.OpNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.EsUtils;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
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

	public void rollback(ElasticsearchClient esClient, long lastHeight) throws Exception {
		rollbackBox(esClient,lastHeight);

		List<String> indexList = new ArrayList<String>();
		indexList.add(IndicesNames.CONTACT);
		indexList.add(IndicesNames.MAIL);
		indexList.add(IndicesNames.SECRET);
		esClient.deleteByQuery(d->d.index(indexList)
				.conflicts(co.elastic.clients.elasticsearch._types.Conflicts.Proceed)
				.query(q->q.range(r->r.field("birthHeight").gt(JsonData.of(lastHeight)))));
	}


	private boolean rollbackBox(ElasticsearchClient esClient, long lastHeight) throws Exception {
		Map<String, ArrayList<String>> resultMap = getEffectedBoxes(esClient,lastHeight);
		ArrayList<String> itemIdList = resultMap.get("itemIdList");
		ArrayList<String> histIdList = resultMap.get("histIdList");

		if(itemIdList==null||itemIdList.isEmpty())return false;
		log.warn("If rolling back is interrupted, reparse all effected ids of index 'box': ");
		JsonUtils.printJson(itemIdList);

		List<BoxHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.BOX_HISTORY, BID, BIDS, itemIdList, BoxHistory.class);

		deleteEffectedItems(esClient, IndicesNames.BOX, itemIdList);
		if(histIdList!=null&&!histIdList.isEmpty())
			deleteRolledHists(esClient, IndicesNames.BOX_HISTORY, histIdList);

		reparseBox(esClient, reparseHistList);

		return false;
	}

	private Map<String, ArrayList<String>> getEffectedBoxes(ElasticsearchClient esClient, long lastHeight) throws Exception {
		SearchResponse<BoxHistory> resultSearch = esClient.search(s->s
				.index(IndicesNames.BOX_HISTORY)
				.query(q->q
						.range(r->r
								.field("height")
								.gt(JsonData.of(lastHeight)))),BoxHistory.class);

		Set<String> itemSet = new HashSet<String>();
		ArrayList<String> histList = new ArrayList<String>();

		for(Hit<BoxHistory> hit: resultSearch.hits().hits()) {

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

	private void reparseBox(ElasticsearchClient esClient, List<BoxHistory> reparseHistList) throws Exception {
		if(reparseHistList==null)return;
		PersonalParser parser = new PersonalParser();
		for(BoxHistory boxHist: reparseHistList) {
			parser.parseBox(esClient, boxHist);
		}
	}

	private void deleteEffectedItems(ElasticsearchClient esClient,String index, ArrayList<String> itemIdList) throws Exception {
		EsUtils.bulkDeleteList(esClient, index, itemIdList);
	}

	private void deleteRolledHists(ElasticsearchClient esClient, String index, ArrayList<String> histIdList) throws Exception {
		EsUtils.bulkDeleteList(esClient, index, histIdList);
	}
}
