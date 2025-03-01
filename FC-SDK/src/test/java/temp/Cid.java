//package temp;
//
//import co.elastic.clients.elasticsearch.ElasticsearchClient;
//import co.elastic.clients.elasticsearch._types.FieldValue;
//import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
//import co.elastic.clients.elasticsearch.core.SearchResponse;
//import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
//import constants.FieldNames;
//import constants.IndicesNames;
//import fcData.FcObject;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//@JsonIgnoreProperties(ignoreUnknown = true)
//public class Cid extends FcObject {
//	private String pubKey;		//public key
//	private Long balance;		//value of fch in satoshi
//	private Long income;		//total amount of fch received in satoshi
//	private Long expend;		//total amount of fch paid in satoshi
//	private String guide;	//the address of the address which sent the first fch to this address
//	private Long birthHeight;	//the height where this address got its first fch
//	private Long lastHeight; 	//the height where this address info changed latest. If roll back happened, lastHei point to the lastHeight before fork.
//	private Long cdd;		//the total amount of coindays destroyed
//	private Long cd;		//CoinDays
//	private Long weight;  // Calculated from cd, cdd and reputation
//	private Long cash;		//Count of UTXO
//	private String btcAddr;	//the btc address
//	private String ethAddr;	//the eth address
//	private String ltcAddr;	//the ltc address
//	private String dogeAddr;	//the doge address
//	private String trxAddr;	//the doge address
//	private String bchAddr;
//
//	public static void makeAddress (List<Cid> cidList, ElasticsearchClient esClient) throws Exception {
//
//		List<String> fidList = cidList.stream().map(Cid::getId).toList();
//
//		List<FieldValue> fieldValueList = new ArrayList<FieldValue>();
//		for (String value : fidList) fieldValueList.add(FieldValue.of(value));
//
//		SearchResponse<Void> response = esClient.search(s->s
//						.index(IndicesNames.CASH)
//						.size(0)
//						.aggregations("addrFilterAggs",a->a
//								.filter(f->f.terms(t->t
//										.field(FieldNames.OWNER)
//										.terms(t1->t1
//												.value(fieldValueList))))
//								.aggregations("utxoFilterAggs",a0->a0
//										.filter(f1->f1.match(m->m.field("valid").query(true)))
//										.aggregations("utxoAggs",a3->a3
//												.terms(t2->t2
//														.field(FieldNames.OWNER)
//														.size(200000))
//												.aggregations("utxoSum",t5->t5
//														.sum(s1->s1
//																.field("value"))))
//								)
//								.aggregations("stxoFilterAggs",a0->a0
//										.filter(f1->f1.match(m->m.field("valid").query(false)))
//										.aggregations("stxoAggs",a1->a1
//												.terms(t2->t2
//														.field(FieldNames.OWNER)
//														.size(200000))
//												.aggregations("stxoSum",t3->t3
//														.sum(s1->s1
//																.field("value")))
//												.aggregations("cddSum",t4->t4
//														.sum(s1->s1
//																.field("cdd")))
//										)
//								)
//								.aggregations("txoAggs",a1->a1
//										.terms(t2->t2
//												.field(FieldNames.OWNER)
//												.size(200000))
//										.aggregations("txoSum",t3->t3
//												.sum(s1->s1
//														.field("value")))
//								)
//
//						)
//				, void.class);
//
//		Map<String, Long> utxoSumMap = new HashMap<>();
//		Map<String, Long> stxoSumMap = new HashMap<>();
//		Map<String, Long> txoSumMap = new HashMap<>();
//		Map<String, Long> cddMap = new HashMap<>();
//		Map<String, Long> utxoCountMap = new HashMap<>();
//
//		List<StringTermsBucket> utxoBuckets = response.aggregations()
//				.get("addrFilterAggs")
//				.filter()
//				.aggregations()
//				.get("utxoFilterAggs")
//				.filter()
//				.aggregations()
//				.get("utxoAggs")
//				.sterms()
//				.buckets().array();
//
//		for (StringTermsBucket bucket: utxoBuckets) {
//			String addr = bucket.key();
//			long value1 = (long)bucket.aggregations().get("utxoSum").sum().value();
//			utxoCountMap.put(addr, bucket.docCount());
//			utxoSumMap.put(addr, value1);
//		}
//
//		List<StringTermsBucket> stxoBuckets = response.aggregations()
//				.get("addrFilterAggs")
//				.filter()
//				.aggregations()
//				.get("stxoFilterAggs")
//				.filter()
//				.aggregations()
//				.get("stxoAggs")
//				.sterms()
//				.buckets().array();
//
//		for (StringTermsBucket bucket: stxoBuckets) {
//			String addr = bucket.key();
//			long value1 = (long)bucket.aggregations().get("stxoSum").sum().value();
//			stxoSumMap.put(addr, value1);
//			long cddSum = (long)bucket.aggregations().get("cddSum").sum().value();
//			cddMap.put(addr, cddSum);
//		}
//
//		List<StringTermsBucket> txoBuckets = response.aggregations()
//				.get("addrFilterAggs")
//				.filter()
//				.aggregations()
//				.get("txoAggs")
//				.sterms()
//				.buckets().array();
//
//		for (StringTermsBucket bucket: txoBuckets) {
//			String addr = bucket.key();
//			long value1 = (long)bucket.aggregations().get("txoSum").sum().value();
//			txoSumMap.put(addr, value1);
//		}
//		for(Cid cid : cidList){
//			String addr = cid.getId();
//			Long cashValue = utxoSumMap.get(addr);
//			if(cashValue!=null) cid.setBalance(cashValue);
//			Long spentValue = stxoSumMap.get(addr);
//			if(spentValue!=null) cid.setExpend(spentValue);
//			Long totalValue = txoSumMap.get(addr);
//			if(totalValue!=null) cid.setIncome(totalValue);
//			Long cdd = cddMap.get(addr);
//			if(cdd!=null) cid.setCdd(cdd);
//			Long count = utxoCountMap.get(addr);
//			if(count!=null) cid.setCash(count);
//		}
//	}
//
//
//	public String getTrxAddr() {
//		return trxAddr;
//	}
//
//	public void setTrxAddr(String trxAddr) {
//		this.trxAddr = trxAddr;
//	}
//
//	public String getPubKey() {
//		return pubKey;
//	}
//
//	public void setPubKey(String pubKey) {
//		this.pubKey = pubKey;
//	}
//
//	public Long getBalance() {
//		return balance;
//	}
//
//	public void setBalance(Long balance) {
//		this.balance = balance;
//	}
//
//	public Long getIncome() {
//		return income;
//	}
//
//	public void setIncome(Long income) {
//		this.income = income;
//	}
//
//	public Long getExpend() {
//		return expend;
//	}
//
//	public void setExpend(Long expend) {
//		this.expend = expend;
//	}
//
//	public String getGuide() {
//		return guide;
//	}
//
//	public void setGuide(String guide) {
//		this.guide = guide;
//	}
//
//	public Long getBirthHeight() {
//		return birthHeight;
//	}
//
//	public void setBirthHeight(Long birthHeight) {
//		this.birthHeight = birthHeight;
//	}
//
//	public Long getLastHeight() {
//		return lastHeight;
//	}
//
//	public void setLastHeight(Long lastHeight) {
//		this.lastHeight = lastHeight;
//	}
//
//	public Long getCdd() {
//		return cdd;
//	}
//
//	public void setCdd(Long cdd) {
//		this.cdd = cdd;
//	}
//
//	public String getBtcAddr() {
//		return btcAddr;
//	}
//
//	public void setBtcAddr(String btcAddr) {
//		this.btcAddr = btcAddr;
//	}
//
//	public String getEthAddr() {
//		return ethAddr;
//	}
//
//	public void setEthAddr(String ethAddr) {
//		this.ethAddr = ethAddr;
//	}
//
//	public String getLtcAddr() {
//		return ltcAddr;
//	}
//
//	public void setLtcAddr(String ltcAddr) {
//		this.ltcAddr = ltcAddr;
//	}
//
//	public String getDogeAddr() {
//		return dogeAddr;
//	}
//
//	public void setDogeAddr(String dogeAddr) {
//		this.dogeAddr = dogeAddr;
//	}
//
//	public Long getCd() {
//		return cd;
//	}
//
//	public void setCd(Long cd) {
//		this.cd = cd;
//	}
//
//	public Long getCash() {
//		return cash;
//	}
//
//	public void setCash(Long cash) {
//		this.cash = cash;
//	}
//
//	public Long getWeight() {
//		return weight;
//	}
//
//	public void setWeight(Long weight) {
//		this.weight = weight;
//	}
//
//	public String getBchAddr() {
//		return bchAddr;
//	}
//
//	public void setBchAddr(String bchAddr) {
//		this.bchAddr = bchAddr;
//	}
//}
