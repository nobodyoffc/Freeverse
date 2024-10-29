package personal;

import clients.esClient.EsTools;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import constants.IndicesNames;
import feip.feipData.BoxHistory;
import javaTools.JsonTools;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class PersonalRollbacker {

	public void rollback(ElasticsearchClient esClient, long lastHeight) throws Exception {
		// TODO Auto-generated method stub
		rollbackBox(esClient,lastHeight);

		List<String> indexList = new ArrayList<String>();
		indexList.add(IndicesNames.CONTACT);
		indexList.add(IndicesNames.MAIL);
		indexList.add(IndicesNames.SECRET);
		esClient.deleteByQuery(d->d.index(indexList).query(q->q.range(r->r.field("birthHeight").gt(JsonData.of(lastHeight)))));
		
		TimeUnit.SECONDS.sleep(3);

	}


	private boolean rollbackBox(ElasticsearchClient esClient, long lastHeight) throws Exception {
		// TODO Auto-generated method stub

		Map<String, ArrayList<String>> resultMap = getEffectedBoxes(esClient,lastHeight);
		ArrayList<String> itemIdList = resultMap.get("itemIdList");
		ArrayList<String> histIdList = resultMap.get("histIdList");

		if(itemIdList==null||itemIdList.isEmpty())return false;
		System.out.println("If rolling back is interrupted, reparse all effected ids of index 'box': ");
		JsonTools.printJson(itemIdList);
		deleteEffectedItems(esClient, IndicesNames.BOX,itemIdList);
		if(histIdList==null||histIdList.isEmpty())return false;
		deleteRolledHists(esClient, IndicesNames.BOX_HISTORY,histIdList);
		TimeUnit.SECONDS.sleep(2);

		List<BoxHistory>reparseHistList = EsTools.getHistsForReparse(esClient, IndicesNames.BOX_HISTORY,"bid",itemIdList,BoxHistory.class);

		reparseBox(esClient,reparseHistList);

		return false;
	}

	private Map<String, ArrayList<String>> getEffectedBoxes(ElasticsearchClient esClient, long lastHeight) throws ElasticsearchException, IOException {
		// TODO Auto-generated method stub
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
			if(item.getOp().equals("create")) {
				itemSet.add(item.getTxId());
			}else {
				itemSet.add(item.getBid());
			}
			histList.add(hit.id());
		}

		ArrayList<String> itemList = new ArrayList<String>(itemSet);

		Map<String,ArrayList<String>> resultMap = new HashMap<String,ArrayList<String>>();
		resultMap.put("itemIdList", itemList);
		resultMap.put("histIdList", histList);

		return resultMap;
	}

	private void reparseBox(ElasticsearchClient esClient, List<BoxHistory> reparseHistList) throws ElasticsearchException, IOException {
		// TODO Auto-generated method stub
		if(reparseHistList==null)return;
		for(BoxHistory boxHist: reparseHistList) {
			new PersonalParser().parseBox(esClient, boxHist);
		}
	}

	private void deleteEffectedItems(ElasticsearchClient esClient,String index, ArrayList<String> itemIdList) throws Exception {
		// TODO Auto-generated method stub
		EsTools.bulkDeleteList(esClient, index, itemIdList);
	}

	private void deleteRolledHists(ElasticsearchClient esClient, String index, ArrayList<String> histIdList) throws Exception {
		// TODO Auto-generated method stub
		EsTools.bulkDeleteList(esClient, index, histIdList);
	}
}
