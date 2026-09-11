package writeEs;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import constants.Constants;
import constants.FieldNames;
import constants.IndicesNames;
import core.fch.OpReFileUtils;
import data.fchData.Block;
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

		Block bestBlock = EsUtils.getBestBlock(esClient);
		if(bestBlock == null) {
			log.warn("No best block in ES; nothing to roll back to {}.", lastHeight);
			return;
		}
		long bestHeight = bestBlock.getHeight();
		if(bestHeight==lastHeight) {
			System.out.println("The height you rollback to is the best height:" +bestHeight );
			// Nothing to delete, but an earlier rollback may have deleted without recomputing.
			List<String> pending = loadPendingAddresses();
			if (!pending.isEmpty()) {
				esClient.indices().refresh(r -> r.index(IndicesNames.CASH));
				bulkUpdateAddr(esClient, FchUtils.aggsTxoByAddrs(esClient, pending), lastHeight);
				clearPendingAddresses();
			}
			return;
		}

		System.out.println("Rollback to : "+ lastHeight  + " ...");
		log.info("Rollback to {} from best height {} ...", lastHeight, bestHeight);
		System.out.println("Recover spent cashes.Wait for 2 seconds...");
		TimeUnit.SECONDS.sleep(2);

		// The addresses to recompute are found from the cash above lastHeight, which the steps
		// below delete or reset. A rollback that fails after those steps could not find them
		// again, so they are saved first and carried into the next rollback until one recomputes
		// them.
		Set<String> addrSet = new HashSet<>(readEffectedAddresses(esClient, lastHeight));
		addrSet.addAll(loadPendingAddresses());
		ArrayList<String> addrList = new ArrayList<>(addrSet);
		savePendingAddresses(addrList);

		// Announce the rollback to the FEIP parser before anything is deleted. Every OpReturn above
		// lastHeight will be appended to the file again by the re-parse, so FEIP must roll back
		// whether or not this rollback completes; announcing only a completed one left the records
		// of a failed rollback followed by their re-parsed duplicates, with no marker between.
		recordInOpReturnFile(lastHeight);

		// Each step can be run again -- a range delete, or a reset of whatever still matches -- so
		// a failed rollback is repaired by the next one. Every step still runs, so one failure does
		// not strand the others, and then the rollback throws.
		List<String> failed = new ArrayList<>();
		runStep(failed, "recover spent cash", () -> recoverStxoToUtxo(esClient, lastHeight));
		System.out.println("Cash recovered. Wait for 2 seconds...");
		TimeUnit.SECONDS.sleep(2);

		runStep(failed, "delete blocks", () -> deleteBlocks(esClient, lastHeight));
		runStep(failed, "delete txs", () -> deleteTxs(esClient, lastHeight));
		runStep(failed, "delete opreturns", () -> deleteOpReturns(esClient, lastHeight));
		runStep(failed, "delete cash", () -> deleteUtxos(esClient, lastHeight));
		runStep(failed, "delete addresses", () -> deleteNewAddresses(esClient, lastHeight));
		runStep(failed, "delete multisig", () -> deleteNewMultisig(esClient, lastHeight));
		runStep(failed, "delete p2sh", () -> deleteNewP2sh(esClient, lastHeight));
		runStep(failed, "delete block marks", () -> deleteBlockMarks(esClient, lastHeight));
		System.out.println("Data deleted. Wait for 2 seconds...");
		TimeUnit.SECONDS.sleep(2);
		System.out.println("Recover address...");

		runStep(failed, "recompute addresses", () -> {
			esClient.indices().refresh(r -> r.index(IndicesNames.CASH));
			Map<String, Map<String, Long>> aggsMaps = FchUtils.aggsTxoByAddrs(esClient, addrList);
			bulkUpdateAddr(esClient, aggsMaps, lastHeight);
		});

		if (!failed.isEmpty()) {
			log.error("Rollback to {} failed at: {}. The indices are between heights; run the rollback again.", lastHeight, failed);
			throw new IOException("Rollback to " + lastHeight + " failed at: " + failed);
		}

		clearPendingAddresses();

		System.out.println("Prepare parsing again. Wait for 2 seconds...");
		TimeUnit.SECONDS.sleep(2);
	}

	@FunctionalInterface
	private interface Step {
		void run() throws Exception;
	}

	private static void runStep(List<String> failed, String name, Step step) {
		try {
			step.run();
		} catch (Exception e) {
			log.error("Rollback step '{}' failed", name, e);
			failed.add(name);
		}
	}

	/** Delete-by-query reports per-document failures in its response rather than by throwing. */
	private static void requireNoFailures(String what, long deleted, List<?> failures) throws IOException {
		if (failures != null && !failures.isEmpty()) {
			throw new IOException(what + ": " + failures.size() + " failures (" + deleted + " done)");
		}
	}

	/**
	 * Cash owners that have a freer document. OP_RETURN and unparseable outputs carry the
	 * placeholder owners "OpReturn" and "Unknown", which BlockMaker never indexes as addresses;
	 * updating them failed with document_missing on every rollback that touched such an output.
	 */
	static boolean isAddressOwner(String owner) {
		return owner != null && !owner.isEmpty()
				&& !"Unknown".equalsIgnoreCase(owner) && !"OpReturn".equalsIgnoreCase(owner);
	}

	/** Relative to the working directory, like CdMaker's state.json. */
	static final String PENDING_ADDRESSES_FILE = "fch_rollback_pending_addresses.json";

	private static List<String> loadPendingAddresses() throws IOException {
		java.nio.file.Path path = java.nio.file.Paths.get(PENDING_ADDRESSES_FILE);
		if (!java.nio.file.Files.exists(path)) return new ArrayList<>();
		List<String> pending = utils.JsonUtils.listFromJson(java.nio.file.Files.readString(path), String.class);
		if (pending == null) return new ArrayList<>();
		log.warn("Carrying {} addresses from an earlier rollback that did not recompute them.", pending.size());
		return pending;
	}

	private static void savePendingAddresses(List<String> addrList) throws IOException {
		java.nio.file.Path path = java.nio.file.Paths.get(PENDING_ADDRESSES_FILE);
		java.nio.file.Path temp = java.nio.file.Paths.get(PENDING_ADDRESSES_FILE + ".tmp");
		java.nio.file.Files.writeString(temp, utils.JsonUtils.toJson(addrList));
		java.nio.file.Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
				java.nio.file.StandardCopyOption.ATOMIC_MOVE);
	}

	private static void clearPendingAddresses() throws IOException {
		java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(PENDING_ADDRESSES_FILE));
	}

	private ArrayList<String> readEffectedAddresses(ElasticsearchClient esClient, long lastHeight) throws IOException {
		Set<String> addrSet = new HashSet<>();
		int size = EsUtils.READ_MAX;
		// Every page must use the SAME sort, and it must be unique: an earlier version sorted page
		// one by OWNER and every later page by ID while still passing the OWNER search_after token,
		// so Elasticsearch compared an address against the id field and silently skipped or
		// repeated documents -- leaving addresses out of the balance recomputation below.
		Query effectedCashQuery = Query.of(q -> q.bool(b -> b
				.should(m -> m.range(r -> r.field(SPEND_HEIGHT).gt(JsonData.of(lastHeight))))
				.should(m1 -> m1.range(r1 -> r1.field(BIRTH_HEIGHT).gt(JsonData.of(lastHeight))))
				.minimumShouldMatch("1")));

		List<FieldValue> searchAfter = null;
		while (true) {
			List<FieldValue> lastSort = searchAfter;
			SearchResponse<Cash> response = esClient.search(s -> {
				s.index(IndicesNames.CASH)
						.size(size)
						.sort(s1 -> s1.field(f -> f.field(ID).order(SortOrder.Asc)))
						.trackTotalHits(t -> t.enabled(false))
						.query(effectedCashQuery);
				if (lastSort != null) s.searchAfter(lastSort);
				return s;
			}, Cash.class);

			List<Hit<Cash>> hits = response.hits().hits();
			if (hits.isEmpty()) break;

			for (Hit<Cash> item : hits) {
				if (item.source() != null && isAddressOwner(item.source().getOwner())) {
					addrSet.add(item.source().getOwner());
				}
			}

			if (hits.size() < size) break;
			searchAfter = hits.get(hits.size() - 1).sort();
			if (searchAfter == null || searchAfter.isEmpty()) break;
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
		int ops = 0;

		for(String addr : addrSet) {
			if (!isAddressOwner(addr)) continue;

			Map<String,Object> updateMap = new HashMap<>();

			// INCOME is the total received (TXO_SUM). It must not be read from STXO_SUM, which is
			// the total *spent* -- that made INCOME equal EXPEND for every rolled-back address, and
			// wrote a null INCOME for any address with received-but-unspent outputs.
			Long income = txoSumMap.get(addr);
			updateMap.put(INCOME, income != null ? income : 0L);

			Long utxoSum = utxoSumMap.get(addr);
			if(utxoSum!=null) {
				Long utxoCount = utxoCountMap.get(addr);
				updateMap.put(BALANCE, utxoSum);
				updateMap.put(CASH, utxoCount != null ? utxoCount : 0L);
			}else {
				updateMap.put(BALANCE, 0L);
				updateMap.put(CASH, 0L);
			}

			Long stxoSum = stxoSumMap.get(addr);
			updateMap.put(EXPEND, stxoSum != null ? stxoSum : 0L);

			Long stxoCdd = stxoCddMap.get(addr);
			updateMap.put(CDD, stxoCdd != null ? stxoCdd : 0L);

			updateMap.put(LAST_HEIGHT,lastHeight);

			br.operations(o1->o1.update(u->u
					.index(IndicesNames.FREER)
					.id(addr)
					// The FEIP parser writes the same documents; a version conflict is not a failure.
					.retryOnConflict(5)
					.action(a->a
							.doc(updateMap)))
			);
			ops++;
		}
		if (ops == 0) return;

		br.timeout(t->t.time("600s"));
		BulkResponse response = esClient.bulk(br.build());
		if (response == null || !response.errors()) return;

		// An address with no freer document has no balance to restore. Anything else is a real
		// failure, and the reasons are logged: "reported errors" alone could not be diagnosed.
		List<String> failures = new ArrayList<>();
		int missing = 0;
		for (BulkResponseItem item : response.items()) {
			if (item.error() == null) continue;
			if ("document_missing_exception".equals(item.error().type())) {
				missing++;
				continue;
			}
			if (failures.size() < 10) failures.add(item.id() + ": " + item.error().type() + " " + item.error().reason());
			else if (failures.size() == 10) failures.add("...");
		}
		if (missing > 0) log.warn("Rollback: {} addresses have no freer document; skipped.", missing);
		if (!failures.isEmpty()) {
			log.error("Rollback: bulk address update failed: {}", failures);
			throw new IOException("Rollback bulk address update failed: " + failures);
		}
	}

	private void recoverStxoToUtxo(ElasticsearchClient esClient, long lastHeight) throws Exception {
		var response = esClient.updateByQuery(u->u
				.index(IndicesNames.CASH)
				.conflicts(co.elastic.clients.elasticsearch._types.Conflicts.Proceed)
				.refresh(true)
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
								// These were left holding the undone spend.
								+ "ctx._source.spendBlockId=null;"
								+ "ctx._source.spendTxIndex=null;"
								+ "ctx._source.lastTime=ctx._source.birthTime;"
								+ "ctx._source.lastHeight=ctx._source.birthHeight;"
				)))
		);
		requireNoFailures("recover spent cash", response.updated() == null ? 0 : response.updated(), response.failures());
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

	private void deleteNewMultisig(ElasticsearchClient esClient, long lastHeight) throws Exception {
		deleteHigherThan(esClient, IndicesNames.MULTISIG,"birthHeight",lastHeight);
	}

	/** BlockWriter indexes P2SH separately from multisig; this index was never rolled back. */
	private void deleteNewP2sh(ElasticsearchClient esClient, long lastHeight) throws Exception {
		deleteHigherThan(esClient, IndicesNames.P2SH,"birthHeight",lastHeight);
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
		requireNoFailures("delete block marks", response.deleted() == null ? 0 : response.deleted(), response.failures());
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

		var response = esClient.deleteByQuery(d->d
				.index(index)
				.conflicts(co.elastic.clients.elasticsearch._types.Conflicts.Proceed)
				.refresh(true)
				.query(q->q
						.range(r->r
								.field(rangeField)
								.gt(JsonData.of(lastHeight))))
		);
		requireNoFailures("delete from " + index, response.deleted() == null ? 0 : response.deleted(), response.failures());

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
