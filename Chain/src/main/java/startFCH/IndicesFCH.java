package startFCH;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;

import constants.IndicesNames;
import utils.EsUtils;

import java.io.IOException;


public class IndicesFCH {
	public static void createAllIndices(ElasticsearchClient esClient) throws ElasticsearchException {

		if (esClient == null) {
			System.out.println("Create a Java client for ES first.");
			return;
		}

		String blockMarkJsonStr = "{\"mappings\":{\"properties\":{\"_fileOrder\":{\"type\":\"short\"},\"_pointer\":{\"type\":\"long\"},\"height\":{\"type\":\"long\"},\"id\":{\"type\":\"keyword\"},\"preId\":{\"type\":\"keyword\"},\"time\":{\"type\":\"long\"},\"size\":{\"type\":\"long\"},\"status\":{\"type\":\"keyword\"}}}}";
		String blockJsonStr = "{\"mappings\":{\"properties\":{\"cdd\":{\"type\":\"long\"},\"bits\":{\"type\":\"long\"},\"fee\":{\"type\":\"long\"},\"height\":{\"type\":\"long\"},\"id\":{\"type\":\"keyword\"},\"inValueT\":{\"type\":\"long\"},\"merkleRoot\":{\"type\":\"keyword\"},\"nonce\":{\"type\":\"long\"},\"outValueT\":{\"type\":\"long\"},\"preId\":{\"type\":\"keyword\"},\"size\":{\"type\":\"long\"},\"time\":{\"type\":\"long\"},\"txCount\":{\"type\":\"long\"},\"version\":{\"type\":\"keyword\"}}}}";
		String blockHasJsonStr = "{\"mappings\":{\"properties\":{\"height\":{\"type\":\"long\"},\"id\":{\"type\":\"keyword\"},\"txMarks\":{\"properties\":{\"cdd\":{\"type\":\"long\"},\"fee\":{\"type\":\"long\"},\"txId\":{\"type\":\"keyword\"},\"outValue\":{\"type\":\"long\"}}}}}}";
		String txJsonStr = "{\"mappings\":{\"properties\":{\"blockId\":{\"type\":\"keyword\"},\"blockTime\":{\"type\":\"long\"},\"cdd\":{\"type\":\"long\"},\"coinbase\":{\"type\":\"text\"},\"fee\":{\"type\":\"long\"},\"height\":{\"type\":\"long\"},\"id\":{\"type\":\"keyword\"},\"inCount\":{\"type\":\"long\"},\"inValueT\":{\"type\":\"long\"},\"lockTime\":{\"type\":\"long\"},\"opReBrief\":{\"type\":\"text\"},\"outCount\":{\"type\":\"long\"},\"outValueT\":{\"type\":\"long\"},\"txIndex\":{\"type\":\"long\"},\"version\":{\"type\":\"long\"}}}}";
		String txHasJsonStr = "{\"mappings\":{\"properties\":{\"height\":{\"type\":\"long\"},\"id\":{\"type\":\"keyword\"},\"rawTx\":{\"type\":\"text\"},\"inMarks\":{\"properties\":{\"owner\":{\"type\":\"wildcard\"},\"cdd\":{\"type\":\"long\"},\"cashId\":{\"type\":\"keyword\"},\"value\":{\"type\":\"long\"}}},\"outMarks\":{\"properties\":{\"owner\":{\"type\":\"wildcard\"},\"cdd\":{\"type\":\"long\"},\"cashId\":{\"type\":\"keyword\"},\"value\":{\"type\":\"long\"}}}}}}";
		String cashJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"issuer\":{\"type\":\"wildcard\"},\"owner\":{\"type\":\"wildcard\"},\"value\":{\"type\":\"long\"},\"birthHeight\":{\"type\":\"long\"},\"birthTime\":{\"type\":\"long\"},\"birthBlockId\":{\"type\":\"keyword\"},\"birthTxIndex\":{\"type\":\"long\"},\"birthTxId\":{\"type\":\"keyword\"},\"birthIndex\":{\"type\":\"long\"},\"lockScript\":{\"type\":\"text\"},\"sequence\":{\"type\":\"keyword\"},\"sigHash\":{\"type\":\"keyword\"},\"spendBlockId\":{\"type\":\"keyword\"},\"spendHeight\":{\"type\":\"long\"},\"spendTxIndex\":{\"type\":\"long\"},\"spendTxId\":{\"type\":\"keyword\"},\"spendIndex\":{\"type\":\"long\"},\"spendTime\":{\"type\":\"long\"},\"type\":{\"type\":\"keyword\"},\"unlockScript\":{\"type\":\"text\"},\"lastTime\":{\"type\":\"long\"},\"valid\":{\"type\":\"boolean\"},\"cdd\":{\"type\":\"long\"}}}}";
		String cidJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"wildcard\"},\"cid\":{\"type\":\"wildcard\"},\"usedCids\":{\"type\":\"wildcard\"},\"pubkey\":{\"type\":\"keyword\"},\"prikey\":{\"type\":\"keyword\"},\"balance\":{\"type\":\"long\"},\"cash\":{\"type\":\"long\"},\"income\":{\"type\":\"long\"},\"expend\":{\"type\":\"long\"},\"cd\":{\"type\":\"long\"},\"cdd\":{\"type\":\"long\"},\"reputation\":{\"type\":\"long\"},\"hot\":{\"type\":\"long\"},\"weight\":{\"type\":\"long\"},\"master\":{\"type\":\"wildcard\"},\"guide\":{\"type\":\"wildcard\"},\"noticeFee\":{\"type\":\"keyword\"},\"homepages\":{\"type\":\"text\"},\"btcAddr\":{\"type\":\"wildcard\"},\"ethAddr\":{\"type\":\"wildcard\"},\"ltcAddr\":{\"type\":\"wildcard\"},\"dogeAddr\":{\"type\":\"wildcard\"},\"trxAddr\":{\"type\":\"wildcard\"},\"bchAddr\":{\"type\":\"wildcard\"},\"birthHeight\":{\"type\":\"long\"},\"nameTime\":{\"type\":\"long\"},\"lastHeight\":{\"type\":\"long\"}}}}";
		String opreturnJsonStr = "{\"mappings\":{\"properties\":{\"cdd\":{\"type\":\"long\"},\"height\":{\"type\":\"long\"},\"id\":{\"type\":\"keyword\"},\"opReturn\":{\"type\":\"text\"},\"recipient\":{\"type\":\"wildcard\"},\"signer\":{\"type\":\"wildcard\"},\"time\":{\"type\":\"long\"},\"txIndex\":{\"type\":\"long\"}}}}";

		EsUtils.createIndex(esClient, IndicesNames.BLOCK_MARK, blockMarkJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.BLOCK, blockJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.BLOCK_HAS, blockHasJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.TX, txJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.TX_HAS, txHasJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.CASH, cashJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.CID, cidJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.OPRETURN, opreturnJsonStr);

		String p2shJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"wildcard\"},\"redeemScript\":{\"type\":\"keyword\"},\"m\":{\"type\":\"short\"},\"n\":{\"type\":\"short\"},\"pubkeys\":{\"type\":\"keyword\"},\"fids\":{\"type\":\"wildcard\"},\"birthHeight\":{\"type\":\"long\"},\"birthTime\":{\"type\":\"long\"},\"birthTxId\":{\"type\":\"keyword\"}}}}";
		EsUtils.createIndex(esClient, IndicesNames.P2SH, p2shJsonStr);
	}


	public static void deleteAllIndices(ElasticsearchClient esClient) throws ElasticsearchException, IOException {

		if(esClient==null) {
			System.out.println("Create a Java client for ES first.");
			return;
		}

		EsUtils.deleteIndex(esClient, IndicesNames.BLOCK_MARK);
		EsUtils.deleteIndex(esClient, IndicesNames.BLOCK);
		EsUtils.deleteIndex(esClient, IndicesNames.BLOCK_HAS);
		EsUtils.deleteIndex(esClient, IndicesNames.TX);
		EsUtils.deleteIndex(esClient, IndicesNames.TX_HAS);
		EsUtils.deleteIndex(esClient, IndicesNames.CASH);
		EsUtils.deleteIndex(esClient, IndicesNames.CID);
		EsUtils.deleteIndex(esClient, IndicesNames.OPRETURN);
		EsUtils.deleteIndex(esClient, IndicesNames.P2SH);
	}	
}
