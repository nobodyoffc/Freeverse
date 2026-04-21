package organize;

import constants.FieldNames;
import constants.OpNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.EsUtils;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
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
		error = rollbackSquare(esClient,lastHeight);
		error = rollbackTeam(esClient,lastHeight);
		
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

		List<SquareHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.SQUARE_HISTORY, FieldNames.SQUARE_ID, SQUARE_IDS, itemIdList, SquareHistory.class);

		deleteEffectedItems(esClient, IndicesNames.SQUARE, itemIdList);
		if(histIdList!=null&&!histIdList.isEmpty())
			deleteRolledHists(esClient, IndicesNames.SQUARE_HISTORY, histIdList);

		reparseSquare(esClient, reparseHistList);

		return error;
	}

	private Map<String, ArrayList<String>> getEffectedSquares(ElasticsearchClient esClient,long height) throws Exception {
		SearchResponse<SquareHistory> resultSearch = esClient.search(s->s
				.index(IndicesNames.SQUARE_HISTORY)
				.query(q->q
						.range(r->r
								.field("height")
								.gt(JsonData.of(height)))),SquareHistory.class);
		
		Set<String> itemSet = new HashSet<String>();
		ArrayList<String> histList = new ArrayList<String>();

		for(Hit<SquareHistory> hit: resultSearch.hits().hits()) {
			
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
	
	private void reparseSquare(ElasticsearchClient esClient, List<SquareHistory> reparseHistList) throws Exception {
		if(reparseHistList==null)return;
		OrganizationParser parser = new OrganizationParser();
		for(SquareHistory squareHist: reparseHistList) {
			parser.parseSquare(esClient, squareHist);
		}
	}
	
	private boolean rollbackTeam(ElasticsearchClient esClient, long lastHeight) throws Exception {
		boolean error = false;
		Map<String, ArrayList<String>> resultMap = getEffectedTeams(esClient,lastHeight);
		ArrayList<String> itemIdList = resultMap.get("itemIdList");
		ArrayList<String> histIdList = resultMap.get("histIdList");

		if(itemIdList==null||itemIdList.isEmpty())return error;
		log.warn("If Rollbacking is interrupted, reparse all effected ids of index 'team': ");
		JsonUtils.printJson(itemIdList);

		List<TeamHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.TEAM_HISTORY, TID, TIDS, itemIdList, TeamHistory.class);

		deleteEffectedItems(esClient, IndicesNames.TEAM, itemIdList);
		if(histIdList!=null&&!histIdList.isEmpty())
			deleteRolledHists(esClient, IndicesNames.TEAM_HISTORY, histIdList);

		reparseTeam(esClient, reparseHistList);

		return error;
	}

	private Map<String, ArrayList<String>> getEffectedTeams(ElasticsearchClient esClient,long height) throws ElasticsearchException, IOException {
		SearchResponse<TeamHistory> resultSearch = esClient.search(s->s
				.index(IndicesNames.TEAM_HISTORY)
				.query(q->q
						.range(r->r
								.field("height")
								.gt(JsonData.of(height)))),TeamHistory.class);
		
		Set<String> itemSet = new HashSet<String>();
		ArrayList<String> histList = new ArrayList<String>();

		for(Hit<TeamHistory> hit: resultSearch.hits().hits()) {
			
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
	
	private void reparseTeam(ElasticsearchClient esClient, List<TeamHistory> reparseHistList) throws Exception {
		if(reparseHistList==null)return;
		OrganizationParser parser = new OrganizationParser();
		for(TeamHistory teamHist: reparseHistList) {
			parser.parseTeam(esClient, teamHist);
		}
	}

	private void deleteEffectedItems(ElasticsearchClient esClient,String index, ArrayList<String> itemIdList) throws Exception {
		EsUtils.bulkDeleteList(esClient, index, itemIdList);
	}

	private void deleteRolledHists(ElasticsearchClient esClient, String index, ArrayList<String> histIdList) throws Exception {
		EsUtils.bulkDeleteList(esClient, index, histIdList);
	}
}
