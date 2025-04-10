package writeEs;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateByQueryResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import constants.FieldNames;
import constants.Strings;
import fch.WeightMethod;
import fch.fchData.Cid;
import fch.fchData.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.EsUtils;
import utils.FchUtils;
import utils.JsonUtils;

import java.io.IOException;
import java.util.*;

import static constants.IndicesNames.*;

public class CdMaker {
	private static final Logger log = LoggerFactory.getLogger(CdMaker.class);
//	static NewEsClient newEsClient = new NewEsClient();
//	public static void main(String[] args) throws Exception {
//		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
//		System.out.println("Is SSL EsClient? 'y' to confirm. Other to create http EsClient:");
//		String input = Inputer.inputString(br);
//		ElasticsearchClient esClient;
//		if("y".equals(input))esClient= newEsClient.getSimpleEsClientSSL(br);
//		else esClient = newEsClient.getSimpleEsClient();
//		System.out.println(esClient.info());
//
//		CdMaker cdMaker = new CdMaker();
//		Block bestBlock = new Block();
//		bestBlock.setTime((long)(System.currentTimeMillis()/1000));
//		cdMaker.makeUtxoCd(esClient,bestBlock);
//		cdMaker.makeAddrCd(esClient);
//		System.out.println("CDs of cashes and addresses were made.");
//		br.close();
//		newEsClient.shutdownClient();
//	}

	public void makeUtxoCd(ElasticsearchClient esClient, Block bestBlock)
			throws ElasticsearchException, IOException {
		long bestBlockTime = bestBlock.getTime();

		System.out.println("Make all cd of UTXOs...");
		log.debug("Make all cd of UTXOs...");

		UpdateByQueryResponse response = esClient.updateByQuery(u -> u
				.conflicts(Conflicts.Proceed)
				.timeout(Time.of(t->t.time("1800s")))
				.index(CASH)
				.query(q -> q.bool(b -> b
						.filter(f -> f.term(t -> t.field(FieldNames.VALID).value(true)))))
				.script(s -> s.inline(i1 -> i1.source(
								"ctx._source.cd = (long)(((long)((params.bestBlockTime - ctx._source.birthTime)/86400)*ctx._source.value)/100000000)")
						.params("bestBlockTime", JsonData.of(bestBlockTime)))));
		if(response!=null&& response.took()!=null)
			log.debug(
				response.updated()
						+" utxo updated within "
						+response.took()/1000
						+" seconds. Version conflicts: "
						+response.versionConflicts());
	}


	public void makeAddrCd(ElasticsearchClient esClient) throws Exception {

		System.out.println("Make all cd of Addresses...");
		long sum = 0;
		long count = 0;

		SearchResponse<Cid> response = esClient.search(
				s -> s.index(CID).size(EsUtils.READ_MAX).sort(sort -> sort.field(f -> f.field(Strings.FID))),
				Cid.class);

		ArrayList<Cid> addrOldList = getResultAddrList(response);
		Map<String, Cid> addrOldMap = new HashMap<>();
		for(Cid addr : addrOldList){
			addrOldMap.put(addr.getId(),addr);
			sum+=addr.getBalance();
		}

		Map<String,Long> addrNewCdMap = makeAddrCdMap(esClient, addrOldList);
		Map<String, Long> addrNewWeightMap = makeWeight(addrNewCdMap, addrOldMap);
		updateAddrMap(esClient, addrNewCdMap, addrNewWeightMap);
		count+=response.hits().hits().size();

		while (response.hits().hits().size() >= EsUtils.READ_MAX) {

			Hit<Cid> last = response.hits().hits().get(response.hits().hits().size() - 1);
			String lastId = last.id();
			response = esClient.search(s -> s.index(CID).size(EsUtils.READ_MAX)
					.sort(sort -> sort.field(f -> f.field(Strings.FID))).searchAfter(lastId), Cid.class);

			addrOldList = getResultAddrList(response);
			addrOldMap = new HashMap<>();
			for (Cid addr : addrOldList) {
				addrOldMap.put(addr.getId(), addr);
			}

			addrNewCdMap = makeAddrCdMap(esClient, addrOldList);
			addrNewWeightMap = makeWeight(addrNewCdMap, addrOldMap);
			updateAddrMap(esClient, addrNewCdMap, addrNewWeightMap);
			count+=response.hits().hits().size();
		}
		String time = FchUtils.convertTimestampToDate(System.currentTimeMillis());
		Map<String,Long> dataMap =new HashMap<>();
		dataMap.put("circulating",sum);
		JsonUtils.writeObjectToJsonFile(dataMap,"state.json",false);
		log.debug(time+": Made cd values of all "+count+" address.");
		System.out.println(time+": Made cd values of all "+count+" address.");
	}

	private Map<String, Long> makeWeight(Map<String, Long> addrNewCdMap, Map<String, Cid> addrOldMap) {
		Map<String,Long> addrCdGrowMap = new HashMap<>();

		for(String id: addrNewCdMap.keySet()){
			long cdGrow = addrOldMap.get(id).getCd()==null?addrNewCdMap.get(id):addrNewCdMap.get(id)-addrOldMap.get(id).getCd();
			addrCdGrowMap.put(id,cdGrow);
		}

		Map<String,Long> addrWeightMap = new HashMap<>();
		for(String id: addrNewCdMap.keySet()){
			Long oldWeight = addrOldMap.get(id).getWeight();
			if(oldWeight==null) oldWeight=0L;
			long newWeight =oldWeight +(addrCdGrowMap.get(id)* WeightMethod.cdPercentInWeight)/100;
			addrWeightMap.put(id, newWeight);
		}
		return addrWeightMap;
	}

	private ArrayList<Cid> getResultAddrList(SearchResponse<Cid> response) {
		ArrayList<Cid> addrList = new ArrayList<>();
		for (Hit<Cid> hit : response.hits().hits()) {
			addrList.add(hit.source());
		}
		return addrList;
	}

	private Map<String, Long> makeAddrCdMap(ElasticsearchClient esClient, ArrayList<Cid> addrOldList)
			throws ElasticsearchException, IOException {

		List<FieldValue> fieldValueList = new ArrayList<>();
		for (Cid addr : addrOldList) {
			fieldValueList.add(FieldValue.of(addr.getId()));
		}

		SearchResponse<Cid> response = esClient.search(
				s -> s.index(CASH).size(0).query(q -> q.term(t -> t.field("valid").value(true)))
						.aggregations("filterByAddr",
								a -> a.filter(f -> f.terms(t -> t.field(FieldNames.OWNER).terms(t1 -> t1.value(fieldValueList))))
										.aggregations("termByAddr",
												a1 -> a1.terms(t3 -> t3.field(FieldNames.OWNER).size(addrOldList.size()))
														.aggregations("cdSum", a2 -> a2.sum(su -> su.field("cd"))))),
				Cid.class);

		Map<String, Long> addrCdMap = new HashMap<>();

		List<StringTermsBucket> utxoBuckets = response.aggregations().get("filterByAddr").filter().aggregations()
				.get("termByAddr").sterms().buckets().array();

		for (StringTermsBucket bucket : utxoBuckets) {
			String addr = bucket.key();
			long value1 = (long) bucket.aggregations().get("cdSum").sum().value();
			addrCdMap.put(addr, value1);
		}
		return addrCdMap;
	}

	private void updateAddrMap(ElasticsearchClient esClient, Map<String, Long> addrNewCdMap, Map<String, Long> addrNewWeightMap) throws Exception {
		// TODO Auto-generated method stub
		Set<String> addrSet = addrNewCdMap.keySet();

		BulkRequest.Builder br = new BulkRequest.Builder();

		for (String addr : addrSet) {
			Map<String, Long> updateMap = new HashMap<>();
			long cd = addrNewCdMap.get(addr);
			long weight = addrNewWeightMap.get(addr);
			updateMap.put("cd", cd);
			updateMap.put("weight",weight);
			br.operations(o -> o.update(u -> u.index(CID).id(addr).action(a -> a.doc(updateMap))));
		}

		if(addrNewCdMap.size()>0)
			EsUtils.bulkWithBuilder(esClient, br);
	}


}
