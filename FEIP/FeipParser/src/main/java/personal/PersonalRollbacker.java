package personal;

import startFEIP.Reparser;
import constants.OpNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.EsUtils;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import constants.IndicesNames;
import data.fcData.FcEntity;
import data.feipData.BoxHistory;
import data.feipData.ContactHistory;
import data.feipData.MailHistory;
import data.feipData.SecretHistory;
import utils.JsonUtils;

import java.util.*;
import java.util.function.Function;

import static constants.FieldNames.*;

public class PersonalRollbacker {

	private static final Logger log = LoggerFactory.getLogger(PersonalRollbacker.class);

	public boolean rollback(ElasticsearchClient esClient, long lastHeight) throws Exception {
		boolean error = rollbackBox(esClient, lastHeight);
		PersonalParser parser = new PersonalParser();
		error |= rollbackByHistory(esClient, lastHeight, "contact", IndicesNames.CONTACT, IndicesNames.CONTACT_HISTORY,
				CONTACT_ID, CONTACT_IDS, ContactHistory.class, h -> touched(h.getContactId(), h.getContactIds()),
				h -> parser.parseContact(esClient, h));
		error |= rollbackByHistory(esClient, lastHeight, "mail", IndicesNames.MAIL, IndicesNames.MAIL_HISTORY,
				MAIL_ID, MAIL_IDS, MailHistory.class, h -> touched(h.getMailId(), h.getMailIds()),
				h -> parser.parseMail(esClient, h));
		error |= rollbackByHistory(esClient, lastHeight, "secret", IndicesNames.SECRET, IndicesNames.SECRET_HISTORY,
				SECRET_ID, SECRET_IDS, SecretHistory.class, h -> touched(h.getSecretId(), h.getSecretIds()),
				h -> parser.parseSecret(esClient, h));
		return error;
	}

	/**
	 * Undo every op above lastHeight on one of the history-backed personal protocols: delete each
	 * item those ops touched, delete their histories, then rebuild the items from the histories at
	 * or below lastHeight. An item born above lastHeight has no surviving history and stays deleted.
	 *
	 * @param idField  the history field naming the item an add/send/update touched
	 * @param idsField the history field listing the items a delete/recover touched
	 */
	<H extends FcEntity> boolean rollbackByHistory(ElasticsearchClient esClient, long lastHeight, String what,
			String itemIndex, String histIndex, String idField, String idsField, Class<H> histClass,
			Function<H, List<String>> touchedIds, Reparser.Step<H> replay) throws Exception {
		List<Hit<H>> rolledHits = EsUtils.scanHitsAboveHeight(esClient, histIndex, "height", lastHeight, histClass);
		if (rolledHits.isEmpty()) return false;

		Set<String> itemSet = new LinkedHashSet<>();
		ArrayList<String> histIdList = new ArrayList<>();
		for (Hit<H> hit : rolledHits) {
			histIdList.add(hit.id());
			if (hit.source() != null) itemSet.addAll(touchedIds.apply(hit.source()));
		}
		ArrayList<String> itemIdList = new ArrayList<>(itemSet);
		log.warn("If rolling back is interrupted, reparse all effected ids of index '{}': ", itemIndex);
		JsonUtils.printJson(itemIdList);

		// Read the surviving histories before deleting anything: the deletes destroy the input.
		List<H> reparseHistList = EsUtils.getHistsForReparse(esClient, histIndex, idField, idsField, itemIdList, lastHeight, histClass);

		boolean error = false;
		if (!itemIdList.isEmpty()) error |= deleteEffectedItems(esClient, itemIndex, itemIdList);
		error |= deleteRolledHists(esClient, histIndex, histIdList);
		error |= Reparser.replay(what, reparseHistList, replay);
		return error;
	}

	static List<String> touched(String id, List<String> ids) {
		List<String> list = new ArrayList<>();
		if (id != null) list.add(id);
		if (ids != null) {
			for (String one : ids) if (one != null) list.add(one);
		}
		return list;
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
