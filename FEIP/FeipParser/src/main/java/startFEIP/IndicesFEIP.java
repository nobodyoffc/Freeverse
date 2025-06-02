package startFEIP;

import utils.EsUtils;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import constants.IndicesNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class IndicesFEIP {

	static final Logger log = LoggerFactory.getLogger(IndicesFEIP.class);


	public static void createAllIndices(ElasticsearchClient esClient) throws ElasticsearchException {

		if (esClient == null) {
			System.out.println("Create a Java client for ES first.");
			return;
		}

//		String cidJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"balance\":{\"type\":\"long\"},\"birthHeight\":{\"type\":\"long\"},\"btcAddr\":{\"type\":\"wildcard\"},\"cd\":{\"type\":\"long\"},\"cdd\":{\"type\":\"long\"},\"weight\":{\"type\":\"long\"},\"dogeAddr\":{\"type\":\"wildcard\"},\"ethAddr\":{\"type\":\"wildcard\"},\"expend\":{\"type\":\"long\"},\"guide\":{\"type\":\"wildcard\"},\"income\":{\"type\":\"long\"},\"lastHeight\":{\"type\":\"long\"},\"ltcAddr\":{\"type\":\"wildcard\"},\"pubkey\":{\"type\":\"wildcard\"},\"trxAddr\":{\"type\":\"wildcard\"},\"cash\":{\"type\":\"long\"}\"cid\":{\"type\":\"wildcard\"},\"height\":{\"type\":\"long\"},\"homepages\":{\"type\":\"text\"},\"hot\":{\"type\":\"long\"},\"prikey\":{\"type\":\"keyword\"},\"lastHeight\":{\"type\":\"long\"},\"master\":{\"type\":\"wildcard\"},\"nameTime\":{\"type\":\"long\"},\"noticeFee\":{\"type\":\"float\"},\"reputation\":{\"type\":\"long\"},\"usedCids\":{\"type\":\"wildcard\"}}}}";
		String cidHistJsonStr = "{\"mappings\":{\"properties\":{\"homepages\":{\"type\":\"text\"},\"master\":{\"type\":\"wildcard\"},\"cipherPrikey\":{\"type\":\"keyword\"},\"alg\":{\"type\":\"wildcard\"},\"height\":{\"type\":\"long\"},\"id\":{\"type\":\"keyword\"},\"index\":{\"type\":\"short\"},\"name\":{\"type\":\"wildcard\"},\"noticeFee\":{\"type\":\"text\"},\"op\":{\"type\":\"wildcard\"},\"prikey\":{\"type\":\"keyword\"},\"signer\":{\"type\":\"wildcard\"},\"sn\":{\"type\":\"short\"},\"time\":{\"type\":\"long\"},\"ver\":{\"type\":\"short\"}}}}";
		String repuHistJsonStr = "{\"mappings\":{\"properties\":{\"cause\":{\"type\":\"text\"},\"height\":{\"type\":\"long\"},\"hot\":{\"type\":\"long\"},\"id\":{\"type\":\"keyword\"},\"index\":{\"type\":\"short\"},\"ratee\":{\"type\":\"wildcard\"},\"rater\":{\"type\":\"wildcard\"},\"reputation\":{\"type\":\"long\"},\"time\":{\"type\":\"long\"}}}}";
		String parseMarkJsonStr = "{\"mappings\":{\"properties\":{\"fileName\":{\"type\":\"wildcard\"},\"lastHeight\":{\"type\":\"long\"},\"lastId\":{\"type\":\"keyword\"},\"lastIndex\":{\"type\":\"long\"},\"length\":{\"type\":\"short\"},\"pointer\":{\"type\":\"long\"}}}}";

		// EsTools.createIndex(esClient, IndicesNames.CID, cidJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.CID_HISTORY, cidHistJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.REPUTATION_HISTORY, repuHistJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.FEIP_MARK, parseMarkJsonStr);

		String protocolJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"type\":{\"type\":\"wildcard\"},\"sn\":{\"type\":\"wildcard\"},\"ver\":{\"type\":\"wildcard\"},\"name\":{\"type\":\"wildcard\"},\"did\":{\"type\":\"keyword\"},\"lang\":{\"type\":\"wildcard\"},\"desc\":{\"type\":\"text\"},\"waiters\":{\"type\":\"keyword\"},\"preDid\":{\"type\":\"keyword\"},\"fileUrls\":{\"type\":\"text\"},\"title\":{\"type\":\"wildcard\"},\"owner\":{\"type\":\"wildcard\"},\"birthTxId\":{\"type\":\"keyword\"},\"birthTime\":{\"type\":\"long\"},\"birthHeight\":{\"type\":\"long\"},\"lastTxId\":{\"type\":\"keyword\"},\"lastTime\":{\"type\":\"long\"},\"lastHeight\":{\"type\":\"long\"},\"tCdd\":{\"type\":\"long\"},\"tRate\":{\"type\":\"float\"},\"active\":{\"type\":\"boolean\"},\"closed\":{\"type\":\"boolean\"},\"closeStatement\":{\"type\":\"text\"}}}}";
		String protocolHistJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"height\":{\"type\":\"long\"},\"index\":{\"type\":\"short\"},\"time\":{\"type\":\"long\"},\"type\":{\"type\":\"wildcard\"},\"sn\":{\"type\":\"wildcard\"},\"ver\":{\"type\":\"wildcard\"},\"name\":{\"type\":\"wildcard\"},\"did\":{\"type\":\"keyword\"},\"desc\":{\"type\":\"text\"},\"lang\":{\"type\":\"wildcard\"},\"preDid\":{\"type\":\"keyword\"},\"fileUrls\":{\"type\":\"text\"},\"signer\":{\"type\":\"wildcard\"},\"pid\":{\"type\":\"keyword\"},\"op\":{\"type\":\"keyword\"},\"rate\":{\"type\":\"short\"},\"waiters\":{\"type\":\"keyword\"},\"cdd\":{\"type\":\"long\"},\"closeStatement\":{\"type\":\"text\"}}}}";
		String codeJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"name\":{\"type\":\"wildcard\"},\"ver\":{\"type\":\"wildcard\"},\"did\":{\"type\":\"keyword\"},\"desc\":{\"type\":\"text\"},\"langs\":{\"type\":\"wildcard\"},\"urls\":{\"type\":\"text\"},\"protocols\":{\"type\":\"keyword\"},\"codes\":{\"type\":\"keyword\"},\"waiters\":{\"type\":\"keyword\"},\"owner\":{\"type\":\"wildcard\"},\"birthTime\":{\"type\":\"long\"},\"birthHeight\":{\"type\":\"long\"},\"lastTxId\":{\"type\":\"keyword\"},\"lastTime\":{\"type\":\"long\"},\"lastHeight\":{\"type\":\"long\"},\"tCdd\":{\"type\":\"long\"},\"tRate\":{\"type\":\"float\"},\"active\":{\"type\":\"boolean\"},\"closed\":{\"type\":\"boolean\"},\"closeStatement\":{\"type\":\"text\"}}}}";
		String codeHistJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"height\":{\"type\":\"long\"},\"index\":{\"type\":\"short\"},\"time\":{\"type\":\"long\"},\"signer\":{\"type\":\"wildcard\"},\"codeId\":{\"type\":\"keyword\"},\"op\":{\"type\":\"keyword\"},\"name\":{\"type\":\"wildcard\"},\"ver\":{\"type\":\"wildcard\"},\"did\":{\"type\":\"keyword\"},\"desc\":{\"type\":\"text\"},\"langs\":{\"type\":\"wildcard\"},\"urls\":{\"type\":\"text\"},\"protocols\":{\"type\":\"keyword\"},\"codes\":{\"type\":\"keyword\"},\"waiters\":{\"type\":\"keyword\"},\"rate\":{\"type\":\"short\"},\"cdd\":{\"type\":\"long\"},\"closeStatement\":{\"type\":\"text\"}}}}";
		String serviceJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"stdName\":{\"type\":\"text\"},\"localNames\":{\"type\":\"text\"},\"desc\":{\"type\":\"text\"},\"ver\":{\"type\":\"keyword\"},\"types\":{\"type\":\"text\"},\"urls\":{\"type\":\"text\"},\"waiters\":{\"type\":\"keyword\"},\"protocols\":{\"type\":\"keyword\"},\"codes\":{\"type\":\"keyword\"},\"services\":{\"type\":\"keyword\"},\"params\":{\"type\":\"object\"},\"owner\":{\"type\":\"wildcard\"},\"birthTime\":{\"type\":\"long\"},\"birthHeight\":{\"type\":\"long\"},\"lastTxId\":{\"type\":\"keyword\"},\"lastTime\":{\"type\":\"long\"},\"lastHeight\":{\"type\":\"long\"},\"tCdd\":{\"type\":\"long\"},\"tRate\":{\"type\":\"float\"},\"active\":{\"type\":\"boolean\"},\"closed\":{\"type\":\"boolean\"},\"closeStatement\":{\"type\":\"text\"}}}}";
		String serviceHistJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"height\":{\"type\":\"long\"},\"index\":{\"type\":\"short\"},\"time\":{\"type\":\"long\"},\"signer\":{\"type\":\"wildcard\"},\"stdName\":{\"type\":\"wildcard\"},\"localNames\":{\"type\":\"wildcard\"},\"desc\":{\"type\":\"text\"},\"types\":{\"type\":\"wildcard\"},\"ver\":{\"type\":\"keyword\"},\"urls\":{\"type\":\"text\"},\"waiters\":{\"type\":\"keyword\"},\"protocols\":{\"type\":\"keyword\"},\"codes\":{\"type\":\"keyword\"},\"services\":{\"type\":\"keyword\"},\"params\":{\"type\":\"object\"},\"sid\":{\"type\":\"keyword\"},\"op\":{\"type\":\"keyword\"},\"rate\":{\"type\":\"short\"},\"cdd\":{\"type\":\"long\"},\"closeStatement\":{\"type\":\"text\"}}}}";
		String appJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"stdName\":{\"type\":\"wildcard\"},\"localNames\":{\"type\":\"wildcard\"},\"types\":{\"type\":\"wildcard\"},\"desc\":{\"type\":\"text\"},\"ver\":{\"type\":\"keyword\"},\"urls\":{\"type\":\"text\"},\"downloads\":{\"properties\":{\"os\":{\"type\":\"text\"},\"link\":{\"type\":\"text\"},\"did\":{\"type\":\"keyword\"}}},\"waiters\":{\"type\":\"keyword\"},\"protocols\":{\"type\":\"keyword\"},\"services\":{\"type\":\"keyword\"},\"owner\":{\"type\":\"wildcard\"},\"birthTime\":{\"type\":\"long\"},\"birthHeight\":{\"type\":\"long\"},\"lastTxId\":{\"type\":\"keyword\"},\"lastTime\":{\"type\":\"long\"},\"lastHeight\":{\"type\":\"long\"},\"tCdd\":{\"type\":\"long\"},\"tRate\":{\"type\":\"float\"},\"active\":{\"type\":\"boolean\"},\"closed\":{\"type\":\"boolean\"},\"closeStatement\":{\"type\":\"text\"}}}}";
		String appHistJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"height\":{\"type\":\"long\"},\"index\":{\"type\":\"short\"},\"time\":{\"type\":\"long\"},\"signer\":{\"type\":\"wildcard\"},\"stdName\":{\"type\":\"wildcard\"},\"localNames\":{\"type\":\"wildcard\"},\"desc\":{\"type\":\"text\"},\"ver\":{\"type\":\"keyword\"},\"types\":{\"type\":\"wildcard\"},\"urls\":{\"type\":\"text\"},\"downloads\":{\"properties\":{\"os\":{\"type\":\"text\"},\"link\":{\"type\":\"text\"},\"did\":{\"type\":\"keyword\"}}},\"waiters\":{\"type\":\"keyword\"},\"protocols\":{\"type\":\"keyword\"},\"services\":{\"type\":\"keyword\"},\"aid\":{\"type\":\"keyword\"},\"op\":{\"type\":\"keyword\"},\"rate\":{\"type\":\"short\"},\"cdd\":{\"type\":\"long\"},\"closeStatement\":{\"type\":\"text\"}}}}";
		EsUtils.createIndex(esClient, IndicesNames.PROTOCOL, protocolJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.PROTOCOL_HISTORY, protocolHistJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.CODE, codeJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.CODE_HISTORY, codeHistJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.SERVICE, serviceJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.SERVICE_HISTORY, serviceHistJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.APP, appJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.APP_HISTORY, appHistJsonStr);

		String groupJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"name\":{\"type\":\"wildcard\"},\"desc\":{\"type\":\"text\"},\"namers\":{\"type\":\"wildcard\"},\"members\":{\"type\":\"wildcard\"},\"memberNum\":{\"type\":\"long\"},\"birthTime\":{\"type\":\"long\"},\"birthHeight\":{\"type\":\"long\"},\"lastTxId\":{\"type\":\"keyword\"},\"lastTime\":{\"type\":\"long\"},\"lastHeight\":{\"type\":\"long\"},\"cddToUpdate\":{\"type\":\"long\"},\"tCdd\":{\"type\":\"long\"}}}}";
		String groupHistJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"height\":{\"type\":\"long\"},\"index\":{\"type\":\"short\"},\"time\":{\"type\":\"long\"},\"signer\":{\"type\":\"wildcard\"},\"gid\":{\"type\":\"keyword\"},\"op\":{\"type\":\"keyword\"},\"name\":{\"type\":\"wildcard\"},\"desc\":{\"type\":\"text\"},\"cdd\":{\"type\":\"long\"}}}}";
		String teamJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"owner\":{\"type\":\"wildcard\"},\"stdName\":{\"type\":\"text\"},\"localNames\":{\"type\":\"text\"},\"waiters\":{\"type\":\"text\"},\"accounts\":{\"type\":\"text\"},\"consensusId\":{\"type\":\"keyword\"},\"desc\":{\"type\":\"text\"},\"members\":{\"type\":\"wildcard\"},\"exMembers\":{\"type\":\"wildcard\"},\"managers\":{\"type\":\"wildcard\"},\"transferee\":{\"type\":\"wildcard\"},\"invitees\":{\"type\":\"wildcard\"},\"leavers\":{\"type\":\"wildcard\"},\"notAgreeMembers\":{\"type\":\"wildcard\"},\"birthTime\":{\"type\":\"long\"},\"birthHeight\":{\"type\":\"long\"},\"lastTxId\":{\"type\":\"keyword\"},\"lastTime\":{\"type\":\"long\"},\"lastHeight\":{\"type\":\"long\"},\"tCdd\":{\"type\":\"long\"},\"tRate\":{\"type\":\"float\"},\"active\":{\"type\":\"boolean\"}}}}";
		String teamHistJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"height\":{\"type\":\"long\"},\"index\":{\"type\":\"short\"},\"time\":{\"type\":\"long\"},\"signer\":{\"type\":\"wildcard\"},\"cdd\":{\"type\":\"long\"},\"tid\":{\"type\":\"keyword\"},\"op\":{\"type\":\"keyword\"},\"list\":{\"type\":\"wildcard\"},\"stdName\":{\"type\":\"text\"},\"localNames\":{\"type\":\"text\"},\"waiters\":{\"type\":\"text\"},\"accounts\":{\"type\":\"text\"},\"consensusId\":{\"type\":\"keyword\"},\"desc\":{\"type\":\"text\"},\"rate\":{\"type\":\"short\"},\"transferee\":{\"type\":\"wildcard\"}}}}";

		EsUtils.createIndex(esClient, IndicesNames.GROUP, groupJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.GROUP_HISTORY, groupHistJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.TEAM, teamJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.TEAM_HISTORY, teamHistJsonStr);

		String contactJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"alg\":{\"type\":\"wildcard\"},\"cipher\":{\"type\":\"keyword\"},\"owner\":{\"type\":\"wildcard\"},\"birthTime\":{\"type\":\"long\"},\"birthHeight\":{\"type\":\"long\"},\"lastHeight\":{\"type\":\"long\"},\"active\":{\"type\":\"boolean\"}}}}";
		String mailJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"sender\":{\"type\":\"wildcard\"},\"recipient\":{\"type\":\"wildcard\"},\"alg\":{\"type\":\"wildcard\"},\"cipher\":{\"type\":\"keyword\"},\"cipherSend\":{\"type\":\"keyword\"},\"cipherReci\":{\"type\":\"keyword\"},\"textId\":{\"type\":\"keyword\"},\"owner\":{\"type\":\"wildcard\"},\"birthTime\":{\"type\":\"long\"},\"birthHeight\":{\"type\":\"long\"},\"lastHeight\":{\"type\":\"long\"},\"active\":{\"type\":\"boolean\"}}}}";
		String secretJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"alg\":{\"type\":\"wildcard\"},\"cipher\":{\"type\":\"keyword\"},\"owner\":{\"type\":\"wildcard\"},\"birthTime\":{\"type\":\"long\"},\"birthHeight\":{\"type\":\"long\"},\"lastHeight\":{\"type\":\"long\"},\"active\":{\"type\":\"boolean\"}}}}";
		String boxJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"name\":{\"type\":\"wildcard\"},\"desc\":{\"type\":\"text\"},\"contain\":{\"type\":\"text\"},\"active\":{\"type\":\"boolean\"},\"owner\":{\"type\":\"wildcard\"},\"birthTime\":{\"type\":\"long\"},\"birthHeight\":{\"type\":\"long\"},\"lastTxId\":{\"type\":\"keyword\"},\"lastTime\":{\"type\":\"long\"},\"lastHeight\":{\"type\":\"long\"}}}}";
		String boxHistJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"height\":{\"type\":\"long\"},\"index\":{\"type\":\"short\"},\"time\":{\"type\":\"long\"},\"signer\":{\"type\":\"wildcard\"},\"bid\":{\"type\":\"keyword\"},\"op\":{\"type\":\"keyword\"},\"name\":{\"type\":\"wildcard\"},\"desc\":{\"type\":\"text\"},\"contain\":{\"type\":\"text\"}}}}";

		EsUtils.createIndex(esClient, IndicesNames.CONTACT, contactJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.MAIL, mailJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.SECRET, secretJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.BOX, boxJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.BOX_HISTORY, boxHistJsonStr);

		String statementJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"title\":{\"type\":\"text\"},\"content\":{\"type\":\"text\"},\"owner\":{\"type\":\"wildcard\"},\"birthTime\":{\"type\":\"long\"},\"birthHeight\":{\"type\":\"long\"},\"lastHeight\":{\"type\":\"long\"},\"active\":{\"type\":\"boolean\"}}}}";
		String proofJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"title\":{\"type\":\"text\"},\"content\":{\"type\":\"text\"},\"cosignersInvited\":{\"type\":\"wildcard\"},\"cosignersSigned\":{\"type\":\"wildcard\"},\"isTransferable\":{\"type\":\"boolean\"},\"active\":{\"type\":\"boolean\"},\"issuer\":{\"type\":\"wildcard\"},\"owner\":{\"type\":\"wildcard\"},\"birthTime\":{\"type\":\"long\"},\"birthHeight\":{\"type\":\"long\"},\"lastTxId\":{\"type\":\"keyword\"},\"lastTime\":{\"type\":\"long\"},\"lastHeight\":{\"type\":\"long\"}}}}";
		String proofHistJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"height\":{\"type\":\"long\"},\"index\":{\"type\":\"short\"},\"time\":{\"type\":\"long\"},\"signer\":{\"type\":\"wildcard\"},\"cdd\":{\"type\":\"long\"},\"proofId\":{\"type\":\"keyword\"},\"op\":{\"type\":\"keyword\"},\"title\":{\"type\":\"text\"},\"content\":{\"type\":\"text\"},\"cosignersInvited\":{\"type\":\"wildcard\"},\"isTransferable\":{\"type\":\"boolean\"},\"allSignsRequired\":{\"type\":\"boolean\"}}}}";
		String tokenJsonStr ="{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"name\":{\"type\":\"text\"},\"desc\":{\"type\":\"text\"},\"consensusId\":{\"type\":\"keyword\"},\"capacity\":{\"type\":\"keyword\"},\"decimal\":{\"type\":\"keyword\"},\"transferable\":{\"type\":\"keyword\"},\"closable\":{\"type\":\"keyword\"},\"openIssue\":{\"type\":\"keyword\"},\"maxAmtPerIssue\":{\"type\":\"keyword\"},\"minCddPerIssue\":{\"type\":\"keyword\"},\"maxIssuesPerAddr\":{\"type\":\"keyword\"},\"closed\":{\"type\":\"keyword\"},\"deployer\":{\"type\":\"keyword\"},\"circulating\":{\"type\":\"double\"},\"birthTime\":{\"type\":\"long\"},\"birthHeight\":{\"type\":\"long\"},\"lastTxId\":{\"type\":\"keyword\"},\"lastTime\":{\"type\":\"long\"},\"lastHeight\":{\"type\":\"long\"}}}}";
		String tokenHolderJsonStr ="{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"fid\":{\"type\":\"keyword\"},\"tokenId\":{\"type\":\"keyword\"},\"balance\":{\"type\":\"double\"},\"firstHeight\":{\"type\":\"long\"},\"lastHeight\":{\"type\":\"long\"}}}}";
		String tokenHistJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"height\":{\"type\":\"long\"},\"index\":{\"type\":\"integer\"},\"time\":{\"type\":\"long\"},\"signer\":{\"type\":\"keyword\"},\"recipient\":{\"type\":\"keyword\"},\"cdd\":{\"type\":\"long\"},\"tokenId\":{\"type\":\"keyword\"},\"op\":{\"type\":\"keyword\"},\"name\":{\"type\":\"text\"},\"desc\":{\"type\":\"text\"},\"consensusId\":{\"type\":\"keyword\"},\"capacity\":{\"type\":\"keyword\"},\"decimal\":{\"type\":\"keyword\"},\"transferable\":{\"type\":\"keyword\"},\"closable\":{\"type\":\"keyword\"},\"openIssue\":{\"type\":\"keyword\"},\"maxAmtPerIssue\":{\"type\":\"keyword\"},\"minCddPerIssue\":{\"type\":\"keyword\"},\"maxIssuesPerAddr\":{\"type\":\"keyword\"},\"issueTo\":{\"type\":\"nested\",\"properties\":{\"fid\":{\"type\":\"keyword\"},\"amount\":{\"type\":\"double\"}}},\"transferTo\":{\"type\":\"nested\",\"properties\":{\"fid\":{\"type\":\"keyword\"},\"amount\":{\"type\":\"double\"}}}}}}";
		EsUtils.createIndex(esClient, IndicesNames.STATEMENT, statementJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.PROOF, proofJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.PROOF_HISTORY, proofHistJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.TOKEN, tokenJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.TOKEN_HOLDER, tokenHolderJsonStr);
		EsUtils.createIndex(esClient, IndicesNames.TOKEN_HISTORY, tokenHistJsonStr);

		String nidJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"name\":{\"type\":\"wildcard\"},\"desc\":{\"type\":\"text\"},\"oid\":{\"type\":\"wildcard\"},\"active\":{\"type\":\"boolean\"},\"namer\":{\"type\":\"wildcard\"},\"birthTime\":{\"type\":\"long\"},\"birthHeight\":{\"type\":\"long\"},\"lastTxId\":{\"type\":\"keyword\"},\"lastTime\":{\"type\":\"long\"},\"lastHeight\":{\"type\":\"long\"}}}}";
		EsUtils.createIndex(esClient, IndicesNames.NID, nidJsonStr);

		String nobodyJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"prikey\":{\"type\":\"keyword\"},\"deathTime\":{\"type\":\"long\"},\"deathTxIndex\":{\"type\":\"integer\"},\"deathTxId\":{\"type\":\"keyword\"}}}}";
		EsUtils.createIndex(esClient, IndicesNames.NOBODY, nobodyJsonStr);
	}

	public static void deleteAllIndices(ElasticsearchClient esClient) throws IOException {

		if (esClient == null) {
			System.out.println("Create a Java client for ES first.");
			return;
		}

		// EsTools.deleteIndex(esClient, IndicesNames.CID);
		EsUtils.deleteIndex(esClient, IndicesNames.CID_HISTORY);
		EsUtils.deleteIndex(esClient, IndicesNames.REPUTATION_HISTORY);
		EsUtils.deleteIndex(esClient, IndicesNames.FEIP_MARK);

		EsUtils.deleteIndex(esClient, IndicesNames.PROTOCOL);
		EsUtils.deleteIndex(esClient, IndicesNames.PROTOCOL_HISTORY);
		EsUtils.deleteIndex(esClient, IndicesNames.CODE);
		EsUtils.deleteIndex(esClient, IndicesNames.CODE_HISTORY);
		EsUtils.deleteIndex(esClient, IndicesNames.SERVICE);
		EsUtils.deleteIndex(esClient, IndicesNames.SERVICE_HISTORY);
		EsUtils.deleteIndex(esClient, IndicesNames.APP);
		EsUtils.deleteIndex(esClient, IndicesNames.APP_HISTORY);

		EsUtils.deleteIndex(esClient, IndicesNames.GROUP);
		EsUtils.deleteIndex(esClient, IndicesNames.GROUP_HISTORY);
		EsUtils.deleteIndex(esClient, IndicesNames.TEAM);
		EsUtils.deleteIndex(esClient, IndicesNames.TEAM_HISTORY);

		EsUtils.deleteIndex(esClient, IndicesNames.CONTACT);
		EsUtils.deleteIndex(esClient, IndicesNames.MAIL);
		EsUtils.deleteIndex(esClient, IndicesNames.SECRET);
		EsUtils.deleteIndex(esClient, IndicesNames.BOX);
		EsUtils.deleteIndex(esClient, IndicesNames.BOX_HISTORY);

		EsUtils.deleteIndex(esClient, IndicesNames.STATEMENT);
		EsUtils.deleteIndex(esClient, IndicesNames.PROOF);
		EsUtils.deleteIndex(esClient, IndicesNames.PROOF_HISTORY);
		EsUtils.deleteIndex(esClient, IndicesNames.NID);
		EsUtils.deleteIndex(esClient, IndicesNames.TOKEN);
		EsUtils.deleteIndex(esClient, IndicesNames.TOKEN_HOLDER);
		EsUtils.deleteIndex(esClient, IndicesNames.TOKEN_HISTORY);
		EsUtils.deleteIndex(esClient, IndicesNames.NOBODY);
	}
}
