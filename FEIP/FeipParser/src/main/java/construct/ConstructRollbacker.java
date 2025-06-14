package construct;

import utils.EsUtils;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import constants.IndicesNames;
import data.feipData.AppHistory;
import data.feipData.CodeHistory;
import data.feipData.ProtocolHistory;
import data.feipData.ServiceHistory;
import utils.JsonUtils;

import java.io.IOException;
import java.util.*;

import static constants.OpNames.PUBLISH;

public class ConstructRollbacker {

	public boolean rollback(ElasticsearchClient esClient, long lastHeight) throws Exception {
		return rollbackProtocol(esClient,lastHeight) 
				|| rollbackService(esClient,lastHeight)
				|| rollbackApp(esClient,lastHeight)
				|| rollbackCode(esClient,lastHeight);
	}

	private boolean rollbackProtocol(ElasticsearchClient esClient, long lastHeight) throws Exception {
		boolean error = false;
		Map<String, ArrayList<String>> resultMap = getEffectedProtocols(esClient,lastHeight);
		ArrayList<String> itemIdList = resultMap.get("itemIdList");
		ArrayList<String> histIdList = resultMap.get("histIdList");
		
		if(itemIdList==null||itemIdList.isEmpty())return error;
		System.out.println("If rolling back is interrupted, reparse all effected ids of index 'protocol': ");
		JsonUtils.printJson(itemIdList);
		deleteEffectedItems(esClient, IndicesNames.PROTOCOL, itemIdList);
		if(histIdList==null||histIdList.isEmpty())return error;
		deleteRolledHists(esClient, IndicesNames.PROTOCOL_HISTORY,histIdList);
		
		List<ProtocolHistory>reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.PROTOCOL_HISTORY,"pid",itemIdList, ProtocolHistory.class);

		reparseProtocol(esClient,reparseHistList);
		
		return error;
	}

	private Map<String, ArrayList<String>> getEffectedProtocols(ElasticsearchClient esClient, long height) throws ElasticsearchException, IOException {
		SearchResponse<ProtocolHistory> resultSearch = esClient.search(s->s
				.index(IndicesNames.PROTOCOL_HISTORY)
				.query(q->q
						.range(r->r
								.field("height")
								.gt(JsonData.of(height)))), ProtocolHistory.class);
		
		Set<String> itemSet = new HashSet<>();
		ArrayList<String> histList = new ArrayList<>();

		for(Hit<ProtocolHistory> hit: resultSearch.hits().hits()) {
			
			ProtocolHistory item = hit.source();
			if(item.getOp().equals(PUBLISH)) {
				itemSet.add(item.getId());
			}else {
				itemSet.add(item.getPid());
			}
			histList.add(hit.id());
		}
		

		ArrayList<String> itemList = new ArrayList<>(itemSet);
		
		Map<String,ArrayList<String>> resultMap = new HashMap<>();
		resultMap.put("itemIdList", itemList);
		resultMap.put("histIdList", histList);
		
		return resultMap;
	}

	private void deleteEffectedItems(ElasticsearchClient esClient,String index, ArrayList<String> itemIdList) throws Exception {
		
		EsUtils.bulkDeleteList(esClient, index, itemIdList);
	}

	private void deleteRolledHists(ElasticsearchClient esClient, String index, ArrayList<String> histIdList) throws Exception {
		
		EsUtils.bulkDeleteList(esClient, index, histIdList);
	}
	
	private void reparseProtocol(ElasticsearchClient esClient, List<ProtocolHistory> reparseHistList) throws Exception {
		
		if(reparseHistList==null)return;
		for(ProtocolHistory freeProtocolHist: reparseHistList) {
			new ConstructParser().parseProtocol(esClient, freeProtocolHist);
		}
	}

	private boolean rollbackService(ElasticsearchClient esClient, long lastHeight) throws Exception {
		
		boolean error = false;
		Map<String, ArrayList<String>> resultMap = getEffectedServices(esClient,lastHeight);
		ArrayList<String> itemIdList = resultMap.get("itemIdList");
		ArrayList<String> histIdList = resultMap.get("histIdList");
		
		if(itemIdList==null||itemIdList.isEmpty())return error;
		System.out.println("If rolling back is interrupted, reparse all effected ids of index 'service': ");
		JsonUtils.printJson(itemIdList);
		deleteEffectedItems(esClient, IndicesNames.SERVICE,itemIdList);
		if(histIdList==null||histIdList.isEmpty())return error;
		deleteRolledHists(esClient, IndicesNames.SERVICE_HISTORY,histIdList);
		
		List<ServiceHistory>reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.SERVICE_HISTORY,"sid",itemIdList,ServiceHistory.class);

		reparseService(esClient,reparseHistList);
		
		return error;
	}

	private Map<String, ArrayList<String>> getEffectedServices(ElasticsearchClient esClient, long lastHeight) throws ElasticsearchException, IOException {
		
		SearchResponse<ServiceHistory> resultSearch = esClient.search(s->s
				.index(IndicesNames.SERVICE_HISTORY)
				.query(q->q
						.range(r->r
								.field("height")
								.gt(JsonData.of(lastHeight)))),ServiceHistory.class);
		
		Set<String> itemSet = new HashSet<String>();
		ArrayList<String> histList = new ArrayList<String>();

		for(Hit<ServiceHistory> hit: resultSearch.hits().hits()) {
			
			ServiceHistory item = hit.source();
			if(item.getOp().equals(PUBLISH)) {
				itemSet.add(item.getId());
			}else {
				itemSet.add(item.getSid());
			}
			histList.add(hit.id());
		}
		

		ArrayList<String> itemList = new ArrayList<>(itemSet);
		
		Map<String,ArrayList<String>> resultMap = new HashMap<>();
		resultMap.put("itemIdList", itemList);
		resultMap.put("histIdList", histList);
		
		return resultMap;
	}

	private void reparseService(ElasticsearchClient esClient, List<ServiceHistory> reparseHistList) throws Exception {
		if(reparseHistList==null)return;
		for(ServiceHistory serviceHist: reparseHistList) {
			new ConstructParser().parseService(esClient, serviceHist);
		}
	}

	private boolean rollbackApp(ElasticsearchClient esClient, long lastHeight) throws Exception {
		
		boolean error = false;
		Map<String, ArrayList<String>> resultMap = getEffectedApps(esClient,lastHeight);
		ArrayList<String> itemIdList = resultMap.get("itemIdList");
		ArrayList<String> histIdList = resultMap.get("histIdList");
		
		if(itemIdList==null||itemIdList.isEmpty())return error;
		System.out.println("If rolling back is interrupted, reparse all effected ids of index 'app': ");
		JsonUtils.printJson(itemIdList);
		deleteEffectedItems(esClient, IndicesNames.APP,itemIdList);
		if(histIdList==null||histIdList.isEmpty())return error;
		deleteRolledHists(esClient, IndicesNames.APP_HISTORY,histIdList);
		
		List<AppHistory>reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.APP_HISTORY,"aid",itemIdList,AppHistory.class);

		reparseApp(esClient,reparseHistList);
		
		return error;
	}

	private Map<String, ArrayList<String>> getEffectedApps(ElasticsearchClient esClient, long lastHeight) throws Exception {
		SearchResponse<AppHistory> resultSearch = esClient.search(s->s
				.index(IndicesNames.APP_HISTORY)
				.query(q->q
						.range(r->r
								.field("height")
								.gt(JsonData.of(lastHeight)))),AppHistory.class);
		
		Set<String> itemSet = new HashSet<String>();
		ArrayList<String> histList = new ArrayList<String>();

		for(Hit<AppHistory> hit: resultSearch.hits().hits()) {
			
			AppHistory item = hit.source();
			if(item.getOp().equals(PUBLISH)) {
				itemSet.add(item.getId());
			}else {
				itemSet.add(item.getAid());
			}
			histList.add(hit.id());
		}
		

		ArrayList<String> itemList = new ArrayList<String>(itemSet);
		
		Map<String,ArrayList<String>> resultMap = new HashMap<String,ArrayList<String>>();
		resultMap.put("itemIdList", itemList);
		resultMap.put("histIdList", histList);
		
		return resultMap;
	}

	private void reparseApp(ElasticsearchClient esClient, List<AppHistory> reparseHistList) throws Exception {
		if(reparseHistList==null)return;
		for(AppHistory appHist: reparseHistList) {
			new ConstructParser().parseApp(esClient, appHist);
		}
	}

	private boolean rollbackCode(ElasticsearchClient esClient, long lastHeight) throws Exception {
		
		boolean error = false;
		Map<String, ArrayList<String>> resultMap = getEffectedCodes(esClient,lastHeight);
		ArrayList<String> itemIdList = resultMap.get("itemIdList");
		ArrayList<String> histIdList = resultMap.get("histIdList");
		
		if(itemIdList==null||itemIdList.isEmpty())return error;
		System.out.println("If rolling back is interrupted, reparse all effected ids of index 'code': ");
		JsonUtils.printJson((itemIdList));
		deleteEffectedItems(esClient, IndicesNames.CODE,itemIdList);
		if(histIdList==null||histIdList.isEmpty())return error;
		deleteRolledHists(esClient, IndicesNames.CODE_HISTORY,histIdList);
		
		List<CodeHistory>reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.CODE_HISTORY,"codeId",itemIdList,CodeHistory.class);

		reparseCode(esClient,reparseHistList);
		
		return error;
	}

	private Map<String, ArrayList<String>> getEffectedCodes(ElasticsearchClient esClient, long lastHeight) throws Exception {
		SearchResponse<CodeHistory> resultSearch = esClient.search(s->s
				.index(IndicesNames.CODE_HISTORY)
				.query(q->q
						.range(r->r
								.field("height")
								.gt(JsonData.of(lastHeight)))),CodeHistory.class);
		
		Set<String> itemSet = new HashSet<String>();
		ArrayList<String> histList = new ArrayList<String>();

		for(Hit<CodeHistory> hit: resultSearch.hits().hits()) {
			
			CodeHistory item = hit.source();
			if(item.getOp().equals(PUBLISH)) {
				itemSet.add(item.getId());
			}else {
				itemSet.add(item.getCodeId());
			}
			histList.add(hit.id());
		}
		

		ArrayList<String> itemList = new ArrayList<String>(itemSet);
		
		Map<String,ArrayList<String>> resultMap = new HashMap<String,ArrayList<String>>();
		resultMap.put("itemIdList", itemList);
		resultMap.put("histIdList", histList);
		
		return resultMap;
	}

	private void reparseCode(ElasticsearchClient esClient, List<CodeHistory> reparseHistList) throws Exception {
		if(reparseHistList==null)return;
		for(CodeHistory codeHist: reparseHistList) {
			new ConstructParser().parseCode(esClient, codeHist);
		}
	}

}
