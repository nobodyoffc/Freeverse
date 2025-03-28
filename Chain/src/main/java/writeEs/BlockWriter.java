package writeEs;

import constants.IndicesNames;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest.Builder;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import fch.OpReFileUtils;
import fch.fchData.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import parser.Preparer;
import parser.ReadyBlock;
import utils.EsUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

public class BlockWriter {

	private static final Logger log = LoggerFactory.getLogger(BlockWriter.class);
	public void writeIntoEs(ElasticsearchClient esClient, ReadyBlock readyBlock, OpReFileUtils opReFile) throws Exception {

		Block block = readyBlock.getBlock();
		BlockHas blockHas = readyBlock.getBlockHas();
		ArrayList<Tx> txList = readyBlock.getTxList();
		ArrayList<TxHas> txHasList = readyBlock.getTxHasList();
		ArrayList<Cash> inList = readyBlock.getInList();
		ArrayList<Cash> outList = readyBlock.getOutWriteList();
		ArrayList<OpReturn> opReturnList = readyBlock.getOpReturnList();
		BlockMark blockMark = readyBlock.getBlockMark();
		ArrayList<Cid> addrList = readyBlock.getAddrList();
		
		opReFile.writeOpReturnListIntoFile(opReturnList);


		Builder br = new Builder();
		putBlock(block, br);
		putBlockHas(blockHas, br);
		putTx(esClient, txList, br);
		putTxHas(esClient, txHasList, br);
		putCash(esClient, outList, br);
		putCash(esClient, inList, br);
		putOpReturn(esClient, opReturnList, br);
		putAddress(esClient, addrList, br);
		putBlockMark(blockMark, br);
		BulkResponse response = EsUtils.bulkWithBuilder(esClient, br);

		System.out.println("Main chain linked. "
				+"Orphan: "+Preparer.orphanList.size()
				+" Fork: "+Preparer.forkList.size()
				+" id: "+blockMark.getId()
				+" file: "+Preparer.CurrentFile
				+" pointer: "+Preparer.Pointer
				+" Height:"+blockMark.getHeight());

		
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

		Preparer.mainList.add(blockMark);
		if (Preparer.mainList.size() > EsUtils.READ_MAX) {
			Preparer.mainList.remove(0);
		}
		Preparer.BestHash = blockMark.getId();
		Preparer.BestHeight = blockMark.getHeight();
		Preparer.BeforeBestBlockMark = Preparer.BestBlockMark;
		Preparer.BestBlockMark = blockMark;
	}

	private void putBlockMark(BlockMark blockMark, Builder br) {
		br.operations(op -> op.index(i -> i.index(IndicesNames.BLOCK_MARK).id(blockMark.getId()).document(blockMark)));
	}

	private void putAddress(ElasticsearchClient esClient, ArrayList<Cid> addrList, Builder br) throws Exception {

		if (addrList.size() > EsUtils.WRITE_MAX / 5) {
			Iterator<Cid> iter = addrList.iterator();
			ArrayList<String> idList = new ArrayList<>();
			while (iter.hasNext())
				idList.add(iter.next().getId());
			EsUtils.bulkWriteList(esClient, IndicesNames.CID, addrList, idList, Cid.class);
			TimeUnit.SECONDS.sleep(3);
		} else {
			for (Cid am : addrList) {
				br.operations(op -> op.index(i -> i.index(IndicesNames.CID).id(am.getId()).document(am)));
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
				EsUtils.bulkWriteList(esClient, IndicesNames.OPRETURN, opReturnList, idList, OpReturn.class);
				TimeUnit.SECONDS.sleep(3);
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
				EsUtils.bulkWriteList(esClient, IndicesNames.CASH, cashList, idList, Cash.class);
				TimeUnit.SECONDS.sleep(3);
			} else {
				for (Cash om : cashList) {
					br.operations(op -> op.index(i -> i.index(IndicesNames.CASH).id(om.getId()).document(om)));
				}
			}
		}
	}

	private void putTxHas(ElasticsearchClient esClient, ArrayList<TxHas> txHasList, Builder br) throws Exception {
		if (txHasList != null) {
			if (txHasList.size() > EsUtils.WRITE_MAX / 5) {
				Iterator<TxHas> iter = txHasList.iterator();
				ArrayList<String> idList = new ArrayList<>();
				while (iter.hasNext())
					idList.add(iter.next().getId());
				EsUtils.bulkWriteList(esClient, IndicesNames.TX_HAS, txHasList, idList, TxHas.class);
				TimeUnit.SECONDS.sleep(3);
			} else {
				for (TxHas ot : txHasList) {
					br.operations(op -> op.index(i -> i.index(IndicesNames.TX_HAS).id(ot.getId()).document(ot)));
				}
			}
		}
	}

	private void putTx(ElasticsearchClient esClient, ArrayList<Tx> txList, Builder br) throws Exception {
		if (txList.size() > EsUtils.WRITE_MAX / 5) {
			Iterator<Tx> iter = txList.iterator();
			ArrayList<String> idList = new ArrayList<>();
			while (iter.hasNext())
				idList.add(iter.next().getId());
			EsUtils.bulkWriteList(esClient, IndicesNames.TX, txList, idList, Tx.class);
			TimeUnit.SECONDS.sleep(3);
		} else {
			for (Tx tm : txList) {
				br.operations(op -> op.index(i -> i.index(IndicesNames.TX).id(tm.getId()).document(tm)));
			}
		}
	}

	private void putBlockHas(BlockHas blockHas, Builder br) {
		br.operations(op -> op.index(i -> i.index(IndicesNames.BLOCK_HAS).id(blockHas.getId()).document(blockHas)));
	}

	private void putBlock(Block block, Builder br) {
		br.operations(op -> op.index(i -> i.index(IndicesNames.BLOCK).id(block.getId()).document(block)));
	}
}
