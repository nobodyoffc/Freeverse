package construct;

import constants.OpNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.EsUtils;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkResponse;
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

import static constants.FieldNames.*;
import static constants.OpNames.*;

public class ConstructRollbacker {

	private static final Logger log = LoggerFactory.getLogger(ConstructRollbacker.class);

	/**
	 * Runs every sub-rollback and reports whether any of them failed. These must not
	 * short-circuit: skipping a category because an earlier one failed (or succeeded) leaves
	 * the index set in a mixture of pre- and post-reorg state.
	 */
	public boolean rollback(ElasticsearchClient esClient, long lastHeight) throws Exception {
		boolean error = false;
		error |= rollbackProtocol(esClient, lastHeight);
		error |= rollbackService(esClient, lastHeight);
		error |= rollbackApp(esClient, lastHeight);
		error |= rollbackCode(esClient, lastHeight);
		return error;
	}

	private boolean rollbackProtocol(ElasticsearchClient esClient, long lastHeight) throws Exception {
		boolean error = false;
		Map<String, ArrayList<String>> resultMap = getEffectedProtocols(esClient,lastHeight);
		ArrayList<String> itemPidList = resultMap.get("itemIdList");
		ArrayList<String> histIdList = resultMap.get("histIdList");

		if(itemPidList==null||itemPidList.isEmpty())return error;
		log.warn("If rolling back is interrupted, reparse all effected ids of index 'protocol': ");
		JsonUtils.printJson(itemPidList);

		// Query reparse data BEFORE deleting to prevent data loss on crash
		List<ProtocolHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.PROTOCOL_HISTORY, PID, PIDS, itemPidList, ProtocolHistory.class);

		error |= deleteEffectedItems(esClient, IndicesNames.PROTOCOL, itemPidList);
		if(histIdList!=null&&!histIdList.isEmpty())
			error |= deleteRolledHists(esClient, IndicesNames.PROTOCOL_HISTORY, histIdList);

		reparseProtocol(esClient, reparseHistList);

		return error;
	}

	private Map<String, ArrayList<String>> getEffectedProtocols(ElasticsearchClient esClient, long height) throws ElasticsearchException, IOException {
		List<Hit<ProtocolHistory>> effectedHits = EsUtils.scanHitsAboveHeight(esClient, IndicesNames.PROTOCOL_HISTORY, "height", height, ProtocolHistory.class);
		
		Set<String> itemSet = new HashSet<>();
		ArrayList<String> histList = new ArrayList<>();

		for(Hit<ProtocolHistory> hit: effectedHits) {
			
			ProtocolHistory item = hit.source();
			if(item==null || item.getOp()==null){
				continue;
			}
			String op = item.getOp();
			switch (op) {
				case PUBLISH -> {
					if(item.getId()==null){
						continue;
					}
					itemSet.add(item.getId());
				}
				case OpNames.STOP, RECOVER, CLOSE -> {
					if (item.getPids() == null || item.getPids().size() == 0) {
						continue;
					}
					for(String pid: item.getPids()){
						if(pid==null){
							continue;
						}
						itemSet.add(pid);
					}
				}
				default -> {
					if(item.getPid()!=null){
						itemSet.add(item.getPid());
					}
				}
			}
			
			histList.add(hit.id());
		}
		

		ArrayList<String> itemList = new ArrayList<>(itemSet);
		
		Map<String,ArrayList<String>> resultMap = new HashMap<>();
		resultMap.put("itemIdList", itemList);
		resultMap.put("histIdList", histList);
		
		return resultMap;
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
	
	private void reparseProtocol(ElasticsearchClient esClient, List<ProtocolHistory> reparseHistList) throws Exception {

		if(reparseHistList==null)return;
		ConstructParser parser = new ConstructParser();
		for(ProtocolHistory freeProtocolHist: reparseHistList) {
			parser.parseProtocol(esClient, freeProtocolHist);
		}
	}

	private boolean rollbackService(ElasticsearchClient esClient, long lastHeight) throws Exception {

		boolean error = false;
		Map<String, ArrayList<String>> resultMap = getEffectedServices(esClient,lastHeight);
		ArrayList<String> itemIdList = resultMap.get("itemIdList");
		ArrayList<String> histIdList = resultMap.get("histIdList");

		if(itemIdList==null||itemIdList.isEmpty())return error;
		log.warn("If rolling back is interrupted, reparse all effected ids of index 'service': ");
		JsonUtils.printJson(itemIdList);

		List<ServiceHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.SERVICE_HISTORY, SID, SIDS, itemIdList, ServiceHistory.class);

		error |= deleteEffectedItems(esClient, IndicesNames.SERVICE, itemIdList);
		if(histIdList!=null&&!histIdList.isEmpty())
			error |= deleteRolledHists(esClient, IndicesNames.SERVICE_HISTORY, histIdList);

		reparseService(esClient, reparseHistList);

		return error;
	}

	private Map<String, ArrayList<String>> getEffectedServices(ElasticsearchClient esClient, long lastHeight) throws ElasticsearchException, IOException {
		
		List<Hit<ServiceHistory>> effectedHits = EsUtils.scanHitsAboveHeight(esClient, IndicesNames.SERVICE_HISTORY, "height", lastHeight, ServiceHistory.class);
		
		Set<String> itemSet = new HashSet<String>();
		ArrayList<String> histList = new ArrayList<String>();

		for(Hit<ServiceHistory> hit: effectedHits) {
			
			ServiceHistory item = hit.source();
			if(item==null || item.getOp()==null){
				continue;
			}
			String op = item.getOp();
			switch (op) {
				case PUBLISH -> {
					if(item.getId()==null){
						continue;
					}
					itemSet.add(item.getId());
				}
				case OpNames.STOP, RECOVER, CLOSE -> {
					if (item.getSids() == null || item.getSids().isEmpty()) {
						continue;
					}
					for(String sid: item.getSids()){
						if(sid==null){
							continue;
						}
						itemSet.add(sid);
					}
				}
				default -> {
					if(item.getSid()!=null){
						itemSet.add(item.getSid());
					}
				}
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
		ConstructParser parser = new ConstructParser();
		for(ServiceHistory serviceHist: reparseHistList) {
			parser.parseService(esClient, serviceHist);
		}
	}

	private boolean rollbackApp(ElasticsearchClient esClient, long lastHeight) throws Exception {

		boolean error = false;
		Map<String, ArrayList<String>> resultMap = getEffectedApps(esClient,lastHeight);
		ArrayList<String> itemIdList = resultMap.get("itemIdList");
		ArrayList<String> histIdList = resultMap.get("histIdList");

		if(itemIdList==null||itemIdList.isEmpty())return error;
		log.warn("If rolling back is interrupted, reparse all effected ids of index 'app': ");
		JsonUtils.printJson(itemIdList);

		List<AppHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.APP_HISTORY, AID, AIDS, itemIdList, AppHistory.class);

		error |= deleteEffectedItems(esClient, IndicesNames.APP, itemIdList);
		if(histIdList!=null&&!histIdList.isEmpty())
			error |= deleteRolledHists(esClient, IndicesNames.APP_HISTORY, histIdList);

		reparseApp(esClient, reparseHistList);

		return error;
	}

	private Map<String, ArrayList<String>> getEffectedApps(ElasticsearchClient esClient, long lastHeight) throws Exception {
		List<Hit<AppHistory>> effectedHits = EsUtils.scanHitsAboveHeight(esClient, IndicesNames.APP_HISTORY, "height", lastHeight, AppHistory.class);
		
		Set<String> itemSet = new HashSet<String>();
		ArrayList<String> histList = new ArrayList<String>();

		for(Hit<AppHistory> hit: effectedHits) {
			
			AppHistory item = hit.source();
			if(item==null || item.getOp()==null){
				continue;
			}
			String op = item.getOp();
			switch (op) {
				case PUBLISH -> {
					if(item.getId()==null){
						continue;
					}
					itemSet.add(item.getId());
				}
				case OpNames.STOP, RECOVER, CLOSE -> {
					if (item.getAids() == null || item.getAids().size() == 0) {
						continue;
					}
					for(String aid: item.getAids()){
						if(aid==null){
							continue;
						}
						itemSet.add(aid);
					}
				}
				default -> {
					if(item.getAid()!=null){
						itemSet.add(item.getAid());
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

	private void reparseApp(ElasticsearchClient esClient, List<AppHistory> reparseHistList) throws Exception {
		if(reparseHistList==null)return;
		ConstructParser parser = new ConstructParser();
		for(AppHistory appHist: reparseHistList) {
			parser.parseApp(esClient, appHist);
		}
	}

	private boolean rollbackCode(ElasticsearchClient esClient, long lastHeight) throws Exception {

		boolean error = false;
		Map<String, ArrayList<String>> resultMap = getEffectedCodes(esClient,lastHeight);
		ArrayList<String> itemIdList = resultMap.get("itemIdList");
		ArrayList<String> histIdList = resultMap.get("histIdList");

		if(itemIdList==null||itemIdList.isEmpty())return error;
		log.warn("If rolling back is interrupted, reparse all effected ids of index 'code': ");
		JsonUtils.printJson((itemIdList));

		List<CodeHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.CODE_HISTORY, CODE_ID, CODE_IDS, itemIdList, CodeHistory.class);

		error |= deleteEffectedItems(esClient, IndicesNames.CODE, itemIdList);
		if(histIdList!=null&&!histIdList.isEmpty())
			error |= deleteRolledHists(esClient, IndicesNames.CODE_HISTORY, histIdList);

		reparseCode(esClient, reparseHistList);

		return error;
	}

	private Map<String, ArrayList<String>> getEffectedCodes(ElasticsearchClient esClient, long lastHeight) throws Exception {
		List<Hit<CodeHistory>> effectedHits = EsUtils.scanHitsAboveHeight(esClient, IndicesNames.CODE_HISTORY, HEIGHT, lastHeight, CodeHistory.class);
		
		Set<String> itemSet = new HashSet<String>();
		ArrayList<String> histList = new ArrayList<String>();

		for(Hit<CodeHistory> hit: effectedHits) {
			
			CodeHistory item = hit.source();
			if(item==null || item.getOp()==null){
				continue;
			}
			String op = item.getOp();
			switch (op) {
				case PUBLISH -> {
					if(item.getId()==null){
						continue;
					}
					itemSet.add(item.getId());
				}
				case OpNames.STOP, RECOVER, CLOSE -> {
					if (item.getCodeIds() == null || item.getCodeIds().size() == 0) {
						continue;
					}
					for(String codeId: item.getCodeIds()){
						if(codeId==null){
							continue;
						}
						itemSet.add(codeId);
					}
				}
				default -> {
					if(item.getCodeId()!=null){
						itemSet.add(item.getCodeId());
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

	private void reparseCode(ElasticsearchClient esClient, List<CodeHistory> reparseHistList) throws Exception {
		if(reparseHistList==null)return;
		ConstructParser parser = new ConstructParser();
		for(CodeHistory codeHist: reparseHistList) {
			parser.parseCode(esClient, codeHist);
		}
	}

}
