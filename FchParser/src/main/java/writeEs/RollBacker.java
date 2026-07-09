package writeEs;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import constants.Constants;
import constants.FieldNames;
import constants.IndicesNames;
import core.fch.OpReFileUtils;
import data.fchData.BlockMask;
import data.fchData.Cash;
import data.fchData.OpReturn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.BytesUtils;
import utils.EsUtils;
import utils.FchUtils;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static constants.FieldNames.*;

public class RollBacker {
	private static final Logger log = LoggerFactory.getLogger(RollBacker.class);

	public void rollback(ElasticsearchClient esClient, long lastHeight) throws Exception {

		long bestHeight = EsUtils.getBestBlock(esClient).getHeight();
		if(bestHeight==lastHeight) {
			System.out.println("The height you rollback to is the best height:" +bestHeight );
			return;
		}

		System.out.println("Rollback to : "+ lastHeight  + " ...");
		log.info("Rollback to {} from best height {} ...", lastHeight, bestHeight);
		System.out.println("Recover spent cashes.Wait for 2 seconds...");
		TimeUnit.SECONDS.sleep(2);

		ArrayList<String> addrList = readEffectedAddresses(esClient, lastHeight);

		recoverStxoToUtxo(esClient, lastHeight);
		System.out.println("Cash recovered. Wait for 2 seconds...");
		TimeUnit.SECONDS.sleep(2);

		try {
			System.out.println("Delete blocks...");
			deleteBlocks(esClient, lastHeight);
		} catch (Exception e) {
			log.error("Error when deleting in rollback",e);
			e.printStackTrace();
		}

		try {
			System.out.println("Delete TX...");
			deleteTxs(esClient, lastHeight);
		} catch (Exception e) {
			log.error("Error when deleting in rollback",e);
			e.printStackTrace();
		}

		try {
			System.out.println("Delete OpReturn...");
			deleteOpReturns(esClient, lastHeight);
		} catch (Exception e) {
			log.error("Error when deleting in rollback",e);
			e.printStackTrace();
		}
		try {
			System.out.println("Delete cash...");
			deleteUtxos(esClient, lastHeight);
		} catch (Exception e) {
			log.error("Error when deleting in rollback",e);
			e.printStackTrace();
		}
		try {
			System.out.println("Delete address...");
			deleteNewAddresses(esClient, lastHeight);
		} catch (Exception e) {
			log.error("Error when deleting in rollback",e);
			e.printStackTrace();
		}
		try {
			System.out.println("Delete p2sh...");
			deleteNewP2sh(esClient, lastHeight);
		} catch (Exception e) {
			log.error("Error when deleting in rollback",e);
			e.printStackTrace();
		}
		try {
			System.out.println("Delete block mark...");
			deleteBlockMarks(esClient, lastHeight);
		} catch (Exception e) {
			log.error("Error when deleting in rollback",e);
			e.printStackTrace();
		}
		System.out.println("Data deleted. Wait for 2 seconds...");
		TimeUnit.SECONDS.sleep(2);
		System.out.println("Recover address...");


		Map<String, Map<String, Long>> aggsMaps = FchUtils.aggsTxoByAddrs(esClient, addrList);
		bulkUpdateAddr(esClient, aggsMaps, lastHeight);

		recordInOpReturnFile(lastHeight);

		System.out.println("Prepare parsing again. Wait for 2 seconds...");
		TimeUnit.SECONDS.sleep(2);
	}

	private ArrayList<String> readEffectedAddresses(ElasticsearchClient esClient, long lastHeight) throws IOException {
		Set<String> addrSet = new HashSet<>();
		int size = EsUtils.READ_MAX;
		SearchResponse<Cash> response = esClient.search(s -> s.index(IndicesNames.CASH)
						.size(size)
						.sort(s1 -> s1.field(f -> f.field(FieldNames.OWNER).order(SortOrder.Asc)))
						.trackTotalHits(t->t.enabled(true))
						.query(q -> q.bool(b -> b
								.should(m -> m.range(r -> r.field("spendHeight").gt(JsonData.of(lastHeight))))
								.should(m1 -> m1.range(r1 -> r1.field("birthHeight").gt(JsonData.of(lastHeight))))))
				, Cash.class);
		for(Hit<Cash>item: response.hits().hits()){
			if (item.source() != null) {
				addrSet.add(item.source().getOwner());
			}
		}
		int hitSize = response.hits().hits().size();
		List<FieldValue> last;

		last= response.hits().hits().get(hitSize - 1).sort();

		while(hitSize>=size){
			List<FieldValue> finalLast = last;
			response = esClient.search(s -> s.index(IndicesNames.CASH)
							.size(size)
							.sort(s1 -> s1.field(f -> f.field(ID).order(SortOrder.Asc)))
							.searchAfter(finalLast)
							.trackTotalHits(t->t.enabled(true))
							.query(q -> q.bool(b -> b
									.should(m -> m.range(r -> r.field(SPEND_HEIGHT).gt(JsonData.of(lastHeight))))
									.should(m1 -> m1.range(r1 -> r1.field(BIRTH_HEIGHT).gt(JsonData.of(lastHeight))))))
					, Cash.class);
			for(Hit<Cash>item: response.hits().hits()){
				if (item.source() != null) {
					addrSet.add(item.source().getOwner());
				}
			}
			hitSize = response.hits().hits().size();
			last = response.hits().hits().get(hitSize - 1).sort();
		}
		return new ArrayList<>(addrSet);
	}


    private void bulkUpdateAddr(ElasticsearchClient esClient, Map<String, Map<String, Long>> aggsMaps,long lastHeight) throws ElasticsearchException, IOException {
		Map<String, Long> utxoSumMap = aggsMaps.get(utils.FchUtils.UTXO_SUM);
		Map<String, Long> stxoSumMap = aggsMaps.get(utils.FchUtils.STXO_SUM);
		Map<String, Long> stxoCddMap = aggsMaps.get(utils.FchUtils.CDD);
		Map<String, Long> utxoCountMap = aggsMaps.get(utils.FchUtils.UTXO_COUNT);
		Map<String, Long> txoSumMap = aggsMaps.get(utils.FchUtils.TXO_SUM);
		Set<String> addrSet = txoSumMap.keySet();

		if(addrSet.isEmpty())return;

		BulkRequest.Builder br = new BulkRequest.Builder();

		for(String addr : addrSet) {

			Map<String,Object> updateMap = new HashMap<>();

			if(txoSumMap.get(addr)!=null) {
				updateMap.put(INCOME, stxoSumMap.get(addr));
			}else {
				updateMap.put(INCOME, 0);
				updateMap.put(BALANCE, 0);
				updateMap.put(EXPEND, 0);
				updateMap.put(CASH, 0);
				updateMap.put(CD, 0);
				updateMap.put(CDD, 0);
				continue;
			}

			if(utxoSumMap.get(addr)!=null) {
				updateMap.put(BALANCE, utxoSumMap.get(addr));
				updateMap.put(CASH, utxoCountMap.get(addr));
			}else {
				updateMap.put(BALANCE, 0);
				updateMap.put(CASH, 0);
			}

			if(stxoSumMap.get(addr)!=null) {
				updateMap.put(EXPEND, stxoSumMap.get(addr));
			}else {
				updateMap.put(EXPEND, 0);
			}

			if(stxoCddMap.get(addr)!=null) {
				updateMap.put(CDD, stxoCddMap.get(addr));
			}else {
				updateMap.put(CDD, 0);
			}

			updateMap.put(LAST_HEIGHT,lastHeight);

			br.operations(o1->o1.update(u->u
					.index(IndicesNames.FREER)
					.id(addr)
					.action(a->a
							.doc(updateMap)))
			);
		}

		br.timeout(t->t.time("600s"));
		esClient.bulk(br.build());
	}

	private void recoverStxoToUtxo(ElasticsearchClient esClient, long lastHeight) throws Exception {
		esClient.updateByQuery(u->u
				.index(IndicesNames.CASH)
				.query(q->q.bool(b->b
						.must(m->m.range(r->r.field(SPEND_HEIGHT).gt(JsonData.of(lastHeight))))
						.must(m1->m1.range(r1->r1.field(BIRTH_HEIGHT).lte(JsonData.of(lastHeight))))))
				.script(s->s.inline(i->i.source(
						"ctx._source.spendTime=0;"
								+ "ctx._source.spendTxId=null;"
								+ "ctx._source.spendHeight=0;"
								+ "ctx._source.spendIndex=0;"
								+ "ctx._source.unlockScript=null;"
								+ "ctx._source.sigHash=null;"
								+ "ctx._source.sequence=null;"
								+ "ctx._source.cdd=0;"
								+ "ctx._source.valid=true;"
				)))
		);
	}

	private void deleteOpReturns(ElasticsearchClient esClient, long lastHeight) throws Exception {
		deleteHigherThan(esClient, IndicesNames.OPRETURN,"height",lastHeight);
	}

	private void deleteBlocks(ElasticsearchClient esClient, long lastHeight) throws Exception {
		deleteHigherThan(esClient, IndicesNames.BLOCK,"height",lastHeight);
	}


	private void deleteTxs(ElasticsearchClient esClient, long lastHeight) throws Exception {
		deleteHigherThan(esClient, IndicesNames.TX,"height",lastHeight);
	}

	private void deleteUtxos(ElasticsearchClient esClient, long lastHeight) throws Exception {
		deleteHigherThan(esClient, IndicesNames.CASH,"birthHeight",lastHeight);
	}

	private void deleteNewAddresses(ElasticsearchClient esClient, long lastHeight) throws Exception {
		deleteHigherThan(esClient, IndicesNames.FREER,"birthHeight",lastHeight);
	}

	private void deleteNewP2sh(ElasticsearchClient esClient, long lastHeight) throws Exception {
		deleteHigherThan(esClient, IndicesNames.MULTISIG,"birthHeight",lastHeight);
	}

	private void deleteBlockMarks(ElasticsearchClient esClient, long lastHeight) throws IOException {
		var response = esClient.deleteByQuery(d->d
				.index(IndicesNames.BLOCK_MARK)
				.conflicts(co.elastic.clients.elasticsearch._types.Conflicts.Proceed)
				.query(q->q
						.bool(b->b
								.should(s->s
										.range(r->r
												.field(HEIGHT)
												.gt(JsonData.of(lastHeight))))
								.should(s1->s1
										.range(r1->r1
												.field(ORPHAN_HEIGHT)
												.gt(JsonData.of(lastHeight))))))
		);
		log.info("Deleted {} block marks above height {} (linked or orphaned).", response.deleted(), lastHeight);
	}

	/**
	 * Finds the earliest file position among the block marks that a rollback to
	 * {@code lastHeight} will delete (height &gt; lastHeight OR orphanHeight &gt; lastHeight).
	 * <p>
	 * The fullnode stores blocks in receive order, not height order, so a block
	 * above the rollback height can sit EARLIER in the blk files than the resume
	 * point computed from the rollback-target block. After its mark is deleted the
	 * block exists neither in ES nor ahead of the parse pointer — the cause of the
	 * permanent "lost main chain" stalls. Callers must capture this position BEFORE
	 * calling {@link #rollback} and rewind the parse position to it.
	 *
	 * @return the mark with the minimal (fileOrder, pointer), or null if none match.
	 */
	public static BlockMask findMinMarkPositionAbove(ElasticsearchClient esClient, long lastHeight) throws IOException {
		SearchResponse<BlockMask> response = esClient.search(s->s.index(IndicesNames.BLOCK_MARK)
						.query(q->q
								.bool(b->b
										.should(s1->s1
												.range(r->r
														.field(HEIGHT)
														.gt(JsonData.of(lastHeight))))
										.should(s2->s2
												.range(r1->r1
														.field(ORPHAN_HEIGHT)
														.gt(JsonData.of(lastHeight))))))
						.size(1)
						.sort(so->so.field(f->f.field("_fileOrder").order(SortOrder.Asc)))
						.sort(so->so.field(f->f.field("_pointer").order(SortOrder.Asc)))
				, BlockMask.class);
		List<Hit<BlockMask>> hits = response.hits().hits();
		return hits.isEmpty() ? null : hits.get(0).source();
	}

	private void deleteHigherThan(ElasticsearchClient esClient, String index, String rangeField, long lastHeight) throws Exception {

		esClient.deleteByQuery(d->d
				.index(index)
				.conflicts(co.elastic.clients.elasticsearch._types.Conflicts.Proceed)
				.query(q->q
						.range(r->r
								.field(rangeField)
								.gt(JsonData.of(lastHeight))))
		);

	}

	private void recordInOpReturnFile(long lastHeight) throws IOException {

		String fileName = Constants.OPRETURN_FILE_NAME;
		File opFile;
		FileOutputStream opos;

		while(true) {
			opFile = new File(Constants.OPRETURN_FILE_DIR,fileName);
			if(opFile.length()>251658240) {
				fileName =  OpReFileUtils.getNextFile(fileName);
			}else break;
		}
		if(opFile.exists()) {
			opos = new FileOutputStream(opFile,true);
		}else {
			opos = new FileOutputStream(opFile);
		}

		OpReturn opRollBack = new OpReturn();//rollbackMarkInOpreturn
		opRollBack.setHeight(lastHeight);

		ArrayList<byte[]> opArrList = new ArrayList<>();
		opArrList.add(BytesUtils.intToByteArray(40));
		opArrList.add("Rollback........................".getBytes());
		opArrList.add(BytesUtils.longToBytes(opRollBack.getHeight()));

		opos.write(BytesUtils.bytesMerger(opArrList));
		opos.flush();
		opos.close();
	}
}
