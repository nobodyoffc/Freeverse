package writeEs;

import constants.IndicesNames;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest.Builder;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import data.fchData.Cash;
import data.fchData.*;
import core.fch.OpReFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import parser.Preparer;
import parser.ReadyBlock;
import utils.EsUtils;

import java.util.*;

public class BlockWriter {

	private static final Logger log = LoggerFactory.getLogger(BlockWriter.class);
	public void writeIntoEs(ElasticsearchClient esClient, ReadyBlock readyBlock, OpReFileUtils opReFile) throws Exception {

		Block block = readyBlock.getBlock();
		LinkedHashMap<String, Tx> txMap = readyBlock.getTxLinkedMap();
		LinkedHashMap<String, Cash> inMap = readyBlock.getInMap();
		LinkedHashMap<String, Cash> outWriteMap = readyBlock.getOutWriteMap();
		LinkedHashMap<String, OpReturn> opReturnMap = readyBlock.getOpReturnMap();
		BlockMask blockMask = readyBlock.getBlockMark();
		LinkedHashMap<String, Freer> addrMap = readyBlock.getAddrMap();
		Map<String, P2SH> p2SHMap = readyBlock.getP2SHMap();
		Map<String, Multisig> multisigMap = readyBlock.getMultisigMap();

		opReFile.writeOpReturnListIntoFile(new ArrayList<>(opReturnMap.values()));

		Builder br = new Builder();
		putBlock(block, br);
		putTx(esClient, txMap, br);
		putCash(esClient, new ArrayList<>(outWriteMap.values()), br);
		putCash(esClient, new ArrayList<>(inMap.values()), br);
		putOpReturn(esClient, new ArrayList<>(opReturnMap.values()), br);
		putAddress(esClient, new ArrayList<>(addrMap.values()), br);
		putP2SH(esClient, new ArrayList<>(p2SHMap.values()), br);
		putMultisig(esClient, new ArrayList<>(multisigMap.values()), br);
		putBlockMark(blockMask, br);
		BulkResponse response = EsUtils.bulkWithBuilder(esClient, br);

		System.out.println("Main chain linked. "
				+"Orphan: "+Preparer.orphanList.size()
				+" Fork: "+Preparer.forkList.size()
				+" id: "+ blockMask.getId()
				+" file: "+Preparer.CurrentFile
				+" pointer: "+Preparer.Pointer
				+" Height:"+ blockMask.getHeight());

		
		response.items().iterator();

		if (response.errors()) {
			log.error("bulkWriteToEs error");
			for(BulkResponseItem item:response.items()) {
				if(item.error()!=null) {
					System.out.println("index: "+item.index()+", Type: "+item.error().type()+"\nReason: "+item.error().reason());
				}
			}	
			throw new Exception("bulkWriteToEs error");
		}

		Preparer.mainList.add(blockMask);
		if (Preparer.mainList.size() > EsUtils.READ_MAX) {
			Preparer.mainList.remove(0);
		}
		Preparer.BestHash = blockMask.getId();
		Preparer.BestHeight = blockMask.getHeight();
		Preparer.beforeBestBlockMask = Preparer.bestBlockMask;
		Preparer.bestBlockMask = blockMask;
	}

	private void putBlockMark(BlockMask blockMask, Builder br) {
		br.operations(op -> op.index(i -> i.index(IndicesNames.BLOCK_MARK).id(blockMask.getId()).document(blockMask)));
	}

	private void putAddress(ElasticsearchClient esClient, ArrayList<Freer> addrList, Builder br) throws Exception {

		if (addrList.size() > EsUtils.WRITE_MAX / 5) {
			Iterator<Freer> iter = addrList.iterator();
			ArrayList<String> idList = new ArrayList<>();
			while (iter.hasNext())
				idList.add(iter.next().getId());
			BulkResponse response = EsUtils.bulkWriteList(esClient, IndicesNames.FREER, addrList, idList, Freer.class);
			checkBulkWriteErrors(response, "CID", addrList.size());
			// TimeUnit.SECONDS.sleep(3); // Testing if this sleep is necessary - commented out to observe ES behavior
		} else {
			for (Freer am : addrList) {
				br.operations(op -> op.index(i -> i.index(IndicesNames.FREER).id(am.getId()).document(am)));
			}
		}
	}

	private void putOpReturn(ElasticsearchClient esClient, ArrayList<OpReturn> opReturnList, Builder br)
			throws Exception {

		if (opReturnList != null) {
			if (opReturnList.size() > 100) {
				Iterator<OpReturn> iter = opReturnList.iterator();
				ArrayList<String> idList = new ArrayList<>();
				while (iter.hasNext())
					idList.add(iter.next().getId());
				BulkResponse response = EsUtils.bulkWriteList(esClient, IndicesNames.OPRETURN, opReturnList, idList, OpReturn.class);
				checkBulkWriteErrors(response, "OPRETURN", opReturnList.size());
				// TimeUnit.SECONDS.sleep(3); // Testing if this sleep is necessary - commented out to observe ES behavior
			} else {
				for (OpReturn or : opReturnList) {
					br.operations(op -> op.index(i -> i.index(IndicesNames.OPRETURN).id(or.getId()).document(or)));
				}
			}
		}
	}

	private void putCash(ElasticsearchClient esClient, ArrayList<Cash> cashList, Builder br) throws Exception {
		if (cashList != null) {
			if (cashList.size() > EsUtils.WRITE_MAX / 5) {
				Iterator<Cash> iter = cashList.iterator();
				ArrayList<String> idList = new ArrayList<>();
				while (iter.hasNext())
					idList.add(iter.next().getId());
				BulkResponse response = EsUtils.bulkWriteList(esClient, IndicesNames.CASH, cashList, idList, Cash.class);
				checkBulkWriteErrors(response, "CASH", cashList.size());
				// TimeUnit.SECONDS.sleep(3); // Testing if this sleep is necessary - commented out to observe ES behavior
			} else {
				for (Cash om : cashList) {
					br.operations(op -> op.index(i -> i.index(IndicesNames.CASH).id(om.getId()).document(om)));
				}
			}
		}
	}

	private void putTx(ElasticsearchClient esClient, LinkedHashMap<String, Tx> txLinkedMap, Builder br) throws Exception {
		if (txLinkedMap.size() > EsUtils.WRITE_MAX / 5) {
			ArrayList<String> idList = new ArrayList<>(txLinkedMap.keySet());

			BulkResponse response = EsUtils.bulkWriteList(esClient, IndicesNames.TX, new ArrayList<>(txLinkedMap.values()), idList, Tx.class);
			checkBulkWriteErrors(response, "TX", txLinkedMap.size());
			// TimeUnit.SECONDS.sleep(3); // Testing if this sleep is necessary - commented out to observe ES behavior
		} else {
			for (Tx tm : txLinkedMap.values()) {
				br.operations(op -> op.index(i -> i.index(IndicesNames.TX).id(tm.getId()).document(tm)));
			}
		}
	}

	private void putBlock(Block block, Builder br) {
		br.operations(op -> op.index(i -> i.index(IndicesNames.BLOCK).id(block.getId()).document(block)));
	}

	private void putP2SH(ElasticsearchClient esClient, ArrayList<P2SH> p2shList, Builder br) throws Exception {
		if (p2shList != null && !p2shList.isEmpty()) {
			if (p2shList.size() > 100) {
				Iterator<P2SH> iter = p2shList.iterator();
				ArrayList<String> idList = new ArrayList<>();
				while (iter.hasNext())
					idList.add(iter.next().getId());
				BulkResponse response = EsUtils.bulkWriteList(esClient, IndicesNames.P2SH, p2shList, idList, P2SH.class);
				checkBulkWriteErrors(response, "P2SH", p2shList.size());
				// TimeUnit.SECONDS.sleep(3); // Testing if this sleep is necessary - commented out to observe ES behavior
			} else {
				for (P2SH p2sh : p2shList) {
					br.operations(op -> op.index(i -> i.index(IndicesNames.P2SH).id(p2sh.getId()).document(p2sh)));
				}
			}
		}
	}

	private void putMultisig(ElasticsearchClient esClient, ArrayList<Multisig> multisigList, Builder br) throws Exception {
		if (multisigList != null && !multisigList.isEmpty()) {
			if (multisigList.size() > 100) {
				Iterator<Multisig> iter = multisigList.iterator();
				ArrayList<String> idList = new ArrayList<>();
				while (iter.hasNext())
					idList.add(iter.next().getId());
				BulkResponse response = EsUtils.bulkWriteList(esClient, IndicesNames.MULTISIG, multisigList, idList, Multisig.class);
				checkBulkWriteErrors(response, "MULTISIG", multisigList.size());
				// TimeUnit.SECONDS.sleep(3); // Testing if this sleep is necessary - commented out to observe ES behavior
			} else {
				for (Multisig multisig : multisigList) {
					br.operations(op -> op.index(i -> i.index(IndicesNames.MULTISIG).id(multisig.getId()).document(multisig)));
				}
			}
		}
	}

	/**
	 * Check for errors in bulk write response and log detailed information
	 * @param response BulkResponse from Elasticsearch
	 * @param indexType Type of index being written (for logging)
	 * @param itemCount Number of items attempted to write
	 * @throws Exception if there are errors in the bulk write
	 */
	private void checkBulkWriteErrors(BulkResponse response, String indexType, int itemCount) throws Exception {
		if (response!=null && response.errors()) {
			int errorCount = 0;
			log.error("Bulk write errors detected for index type: {}, attempted items: {}", indexType, itemCount);

			for (BulkResponseItem item : response.items()) {
				if (item.error() != null) {
					errorCount++;
					log.error("Index: {}, ID: {}, Error Type: {}, Reason: {}",
						item.index(), item.id(), item.error().type(), item.error().reason());

					// Log first 5 errors in detail, then summarize
					if (errorCount <= 5) {
						System.err.println(String.format("[ES Error] %s - Index: %s, Type: %s, Reason: %s",
							indexType, item.index(), item.error().type(), item.error().reason()));
					}
				}
			}

			log.error("Total bulk write errors for {}: {}/{}", indexType, errorCount, itemCount);
			throw new Exception(String.format("Bulk write failed for %s: %d/%d items failed", indexType, errorCount, itemCount));
		} else if(response==null){
			log.debug("Bulk write failed. The response of EsClient is null.");
		} else {
				log.debug("Bulk write successful for {}: {} items written", indexType, itemCount);
		}
	}
}
