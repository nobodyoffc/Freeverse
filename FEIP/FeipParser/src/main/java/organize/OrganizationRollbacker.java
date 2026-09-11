package organize;

import startFEIP.Reparser;
import constants.FieldNames;
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
import data.feipData.SquareHistory;
import data.feipData.TeamHistory;
import utils.JsonUtils;

import java.io.IOException;
import java.util.*;

import static constants.FieldNames.*;

public class OrganizationRollbacker {

	private static final Logger log = LoggerFactory.getLogger(OrganizationRollbacker.class);

	public boolean rollback(ElasticsearchClient esClient, long lastHeight) throws Exception {
		boolean error = false;
		error |= rollbackSquare(esClient, lastHeight);
		error |= rollbackTeam(esClient, lastHeight);
		return error;
	}
	
	private boolean rollbackSquare(ElasticsearchClient esClient, long lastHeight) throws Exception {
		boolean error = false;
		Map<String, ArrayList<String>> resultMap = getEffectedSquares(esClient,lastHeight);
		ArrayList<String> itemIdList = resultMap.get("itemIdList");
		ArrayList<String> histIdList = resultMap.get("histIdList");

		if(itemIdList==null||itemIdList.isEmpty())return error;
		log.warn("If Rollbacking is interrupted, reparse all effected ids of index 'square': ");
		JsonUtils.printJson(itemIdList);

		List<SquareHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.SQUARE_HISTORY, FieldNames.SQUARE_ID, SQUARE_IDS, itemIdList, lastHeight, SquareHistory.class);

		error |= deleteEffectedItems(esClient, IndicesNames.SQUARE, itemIdList);
		if(histIdList!=null&&!histIdList.isEmpty())
			error |= deleteRolledHists(esClient, IndicesNames.SQUARE_HISTORY, histIdList);

		error |= reparseSquare(esClient, reparseHistList);

		return error;
	}

	private Map<String, ArrayList<String>> getEffectedSquares(ElasticsearchClient esClient,long height) throws Exception {
		List<Hit<SquareHistory>> effectedHits = EsUtils.scanHitsAboveHeight(esClient, IndicesNames.SQUARE_HISTORY, "height", height, SquareHistory.class);
		
		Set<String> itemSet = new HashSet<String>();
		ArrayList<String> histList = new ArrayList<String>();

		for(Hit<SquareHistory> hit: effectedHits) {
			
			SquareHistory item = hit.source();
			if(item==null){
				log.info("Square hist is null");
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

				case OpNames.LEAVE -> {
					if(item.getSquareIds()==null || item.getSquareIds().isEmpty()){
						continue;
					}
					for(String gid: item.getSquareIds()){
						if(gid==null){
							continue;
						}
						itemSet.add(gid);
					}
				}

				default -> {
					if(item.getSquareId()!=null){
						itemSet.add(item.getSquareId());
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
	
	private boolean reparseSquare(ElasticsearchClient esClient, List<SquareHistory> reparseHistList) {
		OrganizationParser parser = new OrganizationParser();
		return Reparser.replay("square", reparseHistList, h -> parser.parseSquare(esClient, h));
	}
	
	private boolean rollbackTeam(ElasticsearchClient esClient, long lastHeight) throws Exception {
		boolean error = false;
		Map<String, ArrayList<String>> resultMap = getEffectedTeams(esClient,lastHeight);
		ArrayList<String> itemIdList = resultMap.get("itemIdList");
		ArrayList<String> histIdList = resultMap.get("histIdList");

		if(itemIdList==null||itemIdList.isEmpty())return error;
		log.warn("If Rollbacking is interrupted, reparse all effected ids of index 'team': ");
		JsonUtils.printJson(itemIdList);

		List<TeamHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.TEAM_HISTORY, TID, TIDS, itemIdList, lastHeight, TeamHistory.class);

		error |= deleteEffectedItems(esClient, IndicesNames.TEAM, itemIdList);
		if(histIdList!=null&&!histIdList.isEmpty())
			error |= deleteRolledHists(esClient, IndicesNames.TEAM_HISTORY, histIdList);

		error |= reparseTeam(esClient, reparseHistList);

		return error;
	}

	private Map<String, ArrayList<String>> getEffectedTeams(ElasticsearchClient esClient,long height) throws ElasticsearchException, IOException {
		List<Hit<TeamHistory>> effectedHits = EsUtils.scanHitsAboveHeight(esClient, IndicesNames.TEAM_HISTORY, "height", height, TeamHistory.class);
		
		Set<String> itemSet = new HashSet<String>();
		ArrayList<String> histList = new ArrayList<String>();

		for(Hit<TeamHistory> hit: effectedHits) {
			
			TeamHistory item = hit.source();
			if(item==null || item.getOp()==null){
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
				default -> {
					if(item.getTid()!=null){
						itemSet.add(item.getTid());
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
	
	private boolean reparseTeam(ElasticsearchClient esClient, List<TeamHistory> reparseHistList) {
		OrganizationParser parser = new OrganizationParser();
		return Reparser.replay("team", reparseHistList, h -> parser.parseTeam(esClient, h));
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
