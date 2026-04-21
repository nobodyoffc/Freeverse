package startFEIP;

import utils.EsUtils;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import constants.IndicesNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class IndicesFEIP {

	static final Logger log = LoggerFactory.getLogger(IndicesFEIP.class);

	/**
	 * Loads an ES mapping JSON string from a resource file under mappings/.
	 */
	private static String loadMapping(String fileName) {
		try (InputStream is = IndicesFEIP.class.getClassLoader().getResourceAsStream("mappings/" + fileName)) {
			if (is == null) {
				log.error("Mapping resource not found: mappings/{}", fileName);
				return null;
			}
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			log.error("Failed to load mapping: mappings/{}", fileName, e);
			return null;
		}
	}

	private static void createIndex(ElasticsearchClient esClient, String indexName, String mappingFile) {
		String mapping = loadMapping(mappingFile);
		if (mapping != null) {
			EsUtils.createIndex(esClient, indexName, mapping);
		}
	}

	public static void createAllIndices(ElasticsearchClient esClient) throws ElasticsearchException {

		if (esClient == null) {
			log.info("Create a Java client for ES first.");
			return;
		}

		// Identity
		createIndex(esClient, IndicesNames.NID, "nid.json");
		createIndex(esClient, IndicesNames.FREER_HISTORY, "freer_history.json");
		createIndex(esClient, IndicesNames.REPUTATION_HISTORY, "reputation_history.json");
		createIndex(esClient, IndicesNames.FEIP_MARK, "feip_mark.json");

		// Construct
		createIndex(esClient, IndicesNames.PROTOCOL, "protocol.json");
		createIndex(esClient, IndicesNames.PROTOCOL_HISTORY, "protocol_history.json");
		createIndex(esClient, IndicesNames.CODE, "code.json");
		createIndex(esClient, IndicesNames.CODE_HISTORY, "code_history.json");
		createIndex(esClient, IndicesNames.SERVICE, "service.json");
		createIndex(esClient, IndicesNames.SERVICE_HISTORY, "service_history.json");
		createIndex(esClient, IndicesNames.APP, "app.json");
		createIndex(esClient, IndicesNames.APP_HISTORY, "app_history.json");

		// Organize
		createIndex(esClient, IndicesNames.SQUARE, "square.json");
		createIndex(esClient, IndicesNames.SQUARE_HISTORY, "square_history.json");
		createIndex(esClient, IndicesNames.TEAM, "team.json");
		createIndex(esClient, IndicesNames.TEAM_HISTORY, "team_history.json");

		// Personal
		createIndex(esClient, IndicesNames.CONTACT, "contact.json");
		createIndex(esClient, IndicesNames.MAIL, "mail.json");
		createIndex(esClient, IndicesNames.SECRET, "secret.json");
		createIndex(esClient, IndicesNames.BOX, "box.json");
		createIndex(esClient, IndicesNames.BOX_HISTORY, "box_history.json");

		// Publish
		createIndex(esClient, IndicesNames.STATEMENT, "statement.json");
		createIndex(esClient, IndicesNames.TEXT, "text.json");
		createIndex(esClient, IndicesNames.TEXT_HISTORY, "text_history.json");
		createIndex(esClient, IndicesNames.REMARK, "remark.json");
		createIndex(esClient, IndicesNames.REMARK_HISTORY, "remark_history.json");
		createIndex(esClient, IndicesNames.SOUND, "sound.json");
		createIndex(esClient, IndicesNames.SOUND_HISTORY, "sound_history.json");
		createIndex(esClient, IndicesNames.IMAGE, "image.json");
		createIndex(esClient, IndicesNames.IMAGE_HISTORY, "image_history.json");
		createIndex(esClient, IndicesNames.VIDEO, "video.json");
		createIndex(esClient, IndicesNames.VIDEO_HISTORY, "video_history.json");

		// Finance
		createIndex(esClient, IndicesNames.PROOF, "proof.json");
		createIndex(esClient, IndicesNames.PROOF_HISTORY, "proof_history.json");
		createIndex(esClient, IndicesNames.TOKEN, "token.json");
		createIndex(esClient, IndicesNames.TOKEN_HOLDER, "token_holder.json");
		createIndex(esClient, IndicesNames.TOKEN_HISTORY, "token_history.json");

		// Other
		createIndex(esClient, IndicesNames.NOBODY, "nobody.json");
		createIndex(esClient, IndicesNames.NEWS, "news.json");
	}

	public static void deleteAllIndices(ElasticsearchClient esClient) throws IOException {

		if (esClient == null) {
			log.info("Create a Java client for ES first.");
			return;
		}

		EsUtils.deleteIndex(esClient, IndicesNames.FREER_HISTORY);
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

		EsUtils.deleteIndex(esClient, IndicesNames.SQUARE);
		EsUtils.deleteIndex(esClient, IndicesNames.SQUARE_HISTORY);
		EsUtils.deleteIndex(esClient, IndicesNames.TEAM);
		EsUtils.deleteIndex(esClient, IndicesNames.TEAM_HISTORY);

		EsUtils.deleteIndex(esClient, IndicesNames.CONTACT);
		EsUtils.deleteIndex(esClient, IndicesNames.MAIL);
		EsUtils.deleteIndex(esClient, IndicesNames.SECRET);
		EsUtils.deleteIndex(esClient, IndicesNames.BOX);
		EsUtils.deleteIndex(esClient, IndicesNames.BOX_HISTORY);

		EsUtils.deleteIndex(esClient, IndicesNames.STATEMENT);
		EsUtils.deleteIndex(esClient, IndicesNames.TEXT);
		EsUtils.deleteIndex(esClient, IndicesNames.REMARK);
		EsUtils.deleteIndex(esClient, IndicesNames.TEXT_HISTORY);
		EsUtils.deleteIndex(esClient, IndicesNames.REMARK_HISTORY);
		EsUtils.deleteIndex(esClient, IndicesNames.SOUND);
		EsUtils.deleteIndex(esClient, IndicesNames.SOUND_HISTORY);
		EsUtils.deleteIndex(esClient, IndicesNames.IMAGE);
		EsUtils.deleteIndex(esClient, IndicesNames.IMAGE_HISTORY);
		EsUtils.deleteIndex(esClient, IndicesNames.VIDEO);
		EsUtils.deleteIndex(esClient, IndicesNames.VIDEO_HISTORY);
		EsUtils.deleteIndex(esClient, IndicesNames.PROOF);
		EsUtils.deleteIndex(esClient, IndicesNames.PROOF_HISTORY);
		EsUtils.deleteIndex(esClient, IndicesNames.NID);
		EsUtils.deleteIndex(esClient, IndicesNames.TOKEN);
		EsUtils.deleteIndex(esClient, IndicesNames.TOKEN_HOLDER);
		EsUtils.deleteIndex(esClient, IndicesNames.TOKEN_HISTORY);
		EsUtils.deleteIndex(esClient, IndicesNames.NOBODY);
		EsUtils.deleteIndex(esClient, IndicesNames.NEWS);
	}
}
