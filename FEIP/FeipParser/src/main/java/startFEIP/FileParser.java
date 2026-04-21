package startFEIP;

import data.feipData.*;
import publish.PublishParser;
import publish.PublishRollbacker;
import utils.EsUtils;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import constants.Constants;
import constants.IndicesNames;
import construct.*;

import core.fch.OpReFileUtils;
import data.fchData.OpReturn;
import core.fch.opReReadResult;
import identity.IdentityParser;
import identity.IdentityRollbacker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import organize.OrganizationParser;
import organize.OrganizationRollbacker;

import personal.PersonalParser;
import personal.PersonalRollbacker;
import finance.FinanceParser;
import finance.FinanceRollbacker;
import utils.FchUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileParser {

	private static final int POLL_INTERVAL_SECONDS = 30;
	private static final long MAX_POLL_WAIT_SECONDS = 3600; // 1 hour max wait for new file

	private String path = null;
	private  String fileName = null;
	private long pointer =0;
	private int length =0;
	private long lastHeight = 0;
	private int lastIndex = 0;
	private String lastId = null;
	private final AtomicBoolean running = new AtomicBoolean(true);

	public static Feip parseFeip(OpReturn opre) {

		if(opre.getOpReturn()==null)return null;

		// Reject excessively large OpReturn payloads to prevent memory/storage abuse
		if(opre.getOpReturn().length() > FeipConstants.MAX_CONTENT_LENGTH) {
			log.warn("OpReturn payload exceeds max length ({}) on {}.", FeipConstants.MAX_CONTENT_LENGTH, opre.getId());
			return null;
		}

		Feip feip = null;
		try {
			feip = new Gson().fromJson(opre.getOpReturn(), Feip.class);
		}catch(JsonSyntaxException e) {
			log.debug("Failed parsing FEIP JSON on {}. ",opre.getId());
		}
		return  feip;
	}

	enum FEIP_NAME{
		CID,NOBODY,MASTER,HOME,NOTICE_FEE,REPUTATION,SERVICE,PROTOCOL,APP,CODE,NID, CONTACT,MAIL, SECRET,STATEMENT,GROUP,TEAM, BOX, TOKEN, PROOF,TEXT,REMARK,SOUND,IMAGE,VIDEO
	}

	private static final Logger log = LoggerFactory.getLogger(FileParser.class);

	private void showFound(String protocolName, OpReturn opre) {
		log.info("{} @{}.{}", protocolName, opre.getHeight(), opre.getId());
	}

	private static final int MAX_CONSECUTIVE_ERRORS = 50;

	public boolean parseFile(ElasticsearchClient esClient, boolean isRollback) throws Exception {

		IdentityRollbacker cidRollbacker = new IdentityRollbacker();
		IdentityParser identityParser = new IdentityParser();

		ConstructParser constructParser = new ConstructParser();
		ConstructRollbacker constructRollbacker = new ConstructRollbacker();

		PersonalParser personalParser = new PersonalParser();
		PersonalRollbacker personalRollbacker = new PersonalRollbacker();

		PublishParser publishParser = new PublishParser();
		PublishRollbacker publishRollbacker = new PublishRollbacker();

		FinanceParser financeParser = new FinanceParser();
		FinanceRollbacker financeRollbacker = new FinanceRollbacker();

		OrganizationParser organizationParser = new OrganizationParser();
		OrganizationRollbacker organizationRollbacker = new OrganizationRollbacker();


		if(isRollback) {
			cidRollbacker.rollback(esClient, lastHeight);
			constructRollbacker.rollback(esClient, lastHeight);
			personalRollbacker.rollback(esClient, lastHeight);
			publishRollbacker.rollback(esClient,lastHeight);
			organizationRollbacker.rollback(esClient, lastHeight);
			financeRollbacker.rollback(esClient, lastHeight);
		}

		pointer += length;

		log.info("Start parse {} from {}", fileName, pointer);

		TimeUnit.SECONDS.sleep(2);

		boolean error = false;
		int consecutiveErrors = 0;
		String currentFileName = fileName;
		RandomAccessFile raf = new RandomAccessFile(new File(path, currentFileName), "r");

		try {
		while(!error && running.get()) {
			// Reopen file if fileName changed (file switch)
			if (!currentFileName.equals(fileName)) {
				raf.close();
				currentFileName = fileName;
				raf = new RandomAccessFile(new File(path, currentFileName), "r");
			}
			raf.seek(pointer);
			FileInputStream fis = new FileInputStream(raf.getFD());
			opReReadResult readOpResult = OpReFileUtils.readOpReFromFile(fis);
			// Do NOT close fis here — it shares the file descriptor with raf
			length = readOpResult.getLength();
			pointer += length;

			boolean isValid= false;

			if(readOpResult.isFileEnd()) {
				if(pointer> Constants.MaxOpFileSize) {
					fileName = OpReFileUtils.getNextFile(fileName);
					long waitedSeconds = 0;
					while(!new File(path, fileName).exists() && running.get()) {
						log.info("{} Waiting {} seconds for new file ...", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis())), POLL_INTERVAL_SECONDS);
						TimeUnit.SECONDS.sleep(POLL_INTERVAL_SECONDS);
						waitedSeconds += POLL_INTERVAL_SECONDS;
						if (waitedSeconds >= MAX_POLL_WAIT_SECONDS) {
							log.warn("Max wait time ({} seconds) exceeded for new file {}.", MAX_POLL_WAIT_SECONDS, fileName);
							break;
						}
					}
					if (!running.get()) { error = true; continue; }
					pointer = 0;
					continue;
				}else {
					log.info("{} Waiting for new item ...", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis())));
					FchUtils.waitForChangeInDirectory(path, running);
					if (!running.get()) { error = true; continue; }
					continue;
				}
			}


			if(readOpResult.isRollback()) {
				cidRollbacker.rollback(esClient,readOpResult.getOpReturn().getHeight());
				constructRollbacker.rollback(esClient, readOpResult.getOpReturn().getHeight());
				personalRollbacker.rollback(esClient, readOpResult.getOpReturn().getHeight());
				publishRollbacker.rollback(esClient, readOpResult.getOpReturn().getHeight());
				organizationRollbacker.rollback(esClient, readOpResult.getOpReturn().getHeight());
				financeRollbacker.rollback(esClient, readOpResult.getOpReturn().getHeight());
				continue;
			}

			OpReturn opre = readOpResult.getOpReturn();

			lastHeight = opre.getHeight();
			lastIndex = opre.getTxIndex();
			lastId = opre.getId();

            if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired) continue;

			Feip feip = parseFeip(opre);
			if(feip==null)continue;
			if(feip.getType()==null)continue;
			if(!feip.getType().equals("FEIP"))continue;

			if(feip.getSn() == null)continue;

			Feip.FeipProtocol protocolName = Feip.FeipProtocol.fromSn(feip.getSn());

			if(protocolName==null)continue;

			log.info("");

			String historyIndex = null;
			Object historyDoc = null;
			String historyId = null;

			showFound(protocolName.name(), opre);
			try {
				switch (protocolName) {
					case CID -> {
						FreerHist identityHist = identityParser.makeCid(opre, feip);
						if (identityHist == null) break;
						isValid = identityParser.parseCidInfo(esClient, identityHist);
						if (isValid) { historyIndex = IndicesNames.FREER_HISTORY; historyId = identityHist.getId(); historyDoc = identityHist; }
					}
					case NOBODY -> {
						FreerHist identityHist4 = identityParser.makeNobody(opre, feip);
						if (identityHist4 == null) break;
						isValid = identityParser.parseCidInfo(esClient, identityHist4);
						if (isValid) { historyIndex = IndicesNames.FREER_HISTORY; historyId = identityHist4.getId(); historyDoc = identityHist4; }
					}
					case MASTER -> {
						FreerHist identityHist1 = identityParser.makeMaster(opre, feip);
						if (identityHist1 == null) break;
						isValid = identityParser.parseCidInfo(esClient, identityHist1);
						if (isValid) { historyIndex = IndicesNames.FREER_HISTORY; historyId = identityHist1.getId(); historyDoc = identityHist1; }
					}
					case HOME -> {
						FreerHist identityHist2 = identityParser.makeHome(opre, feip);
						if (identityHist2 == null) break;
						isValid = identityParser.parseCidInfo(esClient, identityHist2);
						if (isValid) { historyIndex = IndicesNames.FREER_HISTORY; historyId = identityHist2.getId(); historyDoc = identityHist2; }
					}
					case NOTICE_FEE -> {
						FreerHist identityHist3 = identityParser.makeNoticeFee(opre, feip);
						if (identityHist3 == null) break;
						isValid = identityParser.parseCidInfo(esClient, identityHist3);
						if (isValid) { historyIndex = IndicesNames.FREER_HISTORY; historyId = identityHist3.getId(); historyDoc = identityHist3; }
					}
					case REPUTATION -> {
						RepuHist repuHist = identityParser.makeReputation(opre, feip);
						if (repuHist == null) break;
						isValid = identityParser.parseReputation(esClient, repuHist);
						if (isValid) { historyIndex = IndicesNames.REPUTATION_HISTORY; historyId = repuHist.getId(); historyDoc = repuHist; }
					}
					case PROTOCOL -> {
						ProtocolHistory freeProtocolHist = constructParser.makeProtocol(opre, feip);
						if (freeProtocolHist == null) break;
						isValid = constructParser.parseProtocol(esClient, freeProtocolHist);
						if (isValid) { historyIndex = IndicesNames.PROTOCOL_HISTORY; historyId = freeProtocolHist.getId(); historyDoc = freeProtocolHist; }
					}
					case SERVICE -> {
						ServiceHistory serviceHist = constructParser.makeService(opre, feip);
						if (serviceHist == null) break;
						isValid = constructParser.parseService(esClient, serviceHist);
						if (isValid) { historyIndex = IndicesNames.SERVICE_HISTORY; historyId = serviceHist.getId(); historyDoc = serviceHist; }
					}
					case APP -> {
						AppHistory appHist = constructParser.makeApp(opre, feip);
						if (appHist == null) break;
						isValid = constructParser.parseApp(esClient, appHist);
						if (isValid) { historyIndex = IndicesNames.APP_HISTORY; historyId = appHist.getId(); historyDoc = appHist; }
					}
					case CODE -> {
						CodeHistory codeHist = constructParser.makeCode(opre, feip);
						if (codeHist == null) break;
						isValid = constructParser.parseCode(esClient, codeHist);
						if (isValid) { historyIndex = IndicesNames.CODE_HISTORY; historyId = codeHist.getId(); historyDoc = codeHist; }
					}
					case NID -> {
						isValid = identityParser.parseNid(esClient, opre, feip);
					}
					case CONTACT -> {
						isValid = personalParser.parseContact(esClient, opre, feip);
					}
					case MAIL -> {
						isValid = personalParser.parseMail(esClient, opre, feip);
					}
					case SECRET -> {
						isValid = personalParser.parseSecret(esClient, opre, feip);
					}
					case STATEMENT -> {
						isValid = publishParser.parseStatement(esClient, opre, feip);
					}
					case TEXT -> {
						TextHistory textHist = publishParser.makeText(opre, feip);
						if (textHist == null) break;
						isValid = publishParser.parseText(esClient, textHist);
						if (isValid) { historyIndex = IndicesNames.TEXT_HISTORY; historyId = textHist.getId(); historyDoc = textHist; }
					}
					case REMARK -> {
						RemarkHistory remarkHist = publishParser.makeRemark(opre, feip);
						if (remarkHist == null) break;
						isValid = publishParser.parseRemark(esClient, remarkHist);
						if (isValid) { historyIndex = IndicesNames.REMARK_HISTORY; historyId = remarkHist.getId(); historyDoc = remarkHist; }
					}
					case SQUARE -> {
						SquareHistory squareHist = organizationParser.makeSquare(opre, feip);
						if (squareHist == null) break;
						isValid = organizationParser.parseSquare(esClient, squareHist);
						if (isValid) { historyIndex = IndicesNames.SQUARE_HISTORY; historyId = squareHist.getId(); historyDoc = squareHist; }
					}
					case TEAM -> {
						TeamHistory teamHist = organizationParser.makeTeam(opre, feip);
						if (teamHist == null) break;
						isValid = organizationParser.parseTeam(esClient, teamHist);
						if (isValid) { historyIndex = IndicesNames.TEAM_HISTORY; historyId = teamHist.getId(); historyDoc = teamHist; }
					}
					case BOX -> {
						BoxHistory boxHist = personalParser.makeBox(opre, feip);
						if (boxHist == null) break;
						isValid = personalParser.parseBox(esClient, boxHist);
						if (isValid) { historyIndex = IndicesNames.BOX_HISTORY; historyId = boxHist.getId(); historyDoc = boxHist; }
					}
					case PROOF -> {
						ProofHistory proofHist = financeParser.makeProof(opre, feip);
						if (proofHist == null) break;
						isValid = financeParser.parseProof(esClient, proofHist);
						if (isValid) { historyIndex = IndicesNames.PROOF_HISTORY; historyId = proofHist.getId(); historyDoc = proofHist; }
					}
					case TOKEN -> {
						TokenHistory tokenHist = financeParser.makeToken(opre, feip);
						if (tokenHist == null) break;
						try {
							isValid = financeParser.parseToken(esClient, tokenHist);
						} catch (NumberFormatException e) {
							log.error("NumberFormatException parsing token at {}.", opre.getId(), e);
						}
						if (isValid) { historyIndex = IndicesNames.TOKEN_HISTORY; historyId = tokenHist.getId(); historyDoc = tokenHist; }
					}
					case SOUND -> {
						SoundHistory soundHist = publishParser.makeSound(opre, feip);
						if (soundHist == null) break;
						isValid = publishParser.parseSound(esClient, soundHist);
						if (isValid) { historyIndex = IndicesNames.SOUND_HISTORY; historyId = soundHist.getId(); historyDoc = soundHist; }
					}
					case IMAGE -> {
						ImageHistory imageHist = publishParser.makeImage(opre, feip);
						if (imageHist == null) break;
						isValid = publishParser.parseImage(esClient, imageHist);
						if (isValid) { historyIndex = IndicesNames.IMAGE_HISTORY; historyId = imageHist.getId(); historyDoc = imageHist; }
					}
					case VIDEO -> {
						VideoHistory videoHist = publishParser.makeVideo(opre, feip);
						if (videoHist == null) break;
						isValid = publishParser.parseVideo(esClient, videoHist);
						if (isValid) { historyIndex = IndicesNames.VIDEO_HISTORY; historyId = videoHist.getId(); historyDoc = videoHist; }
					}
					default -> {
					}
				}
				consecutiveErrors = 0;
			}catch (Exception e){
				log.error("Parsing failed for {} at height {}.", protocolName, lastHeight, e);
				consecutiveErrors++;
				if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
					log.error("Reached {} consecutive errors. Stopping parser.", MAX_CONSECUTIVE_ERRORS);
					error = true;
				}
			}
			if(isValid) writeHistoryAndMark(esClient, historyIndex, historyId, historyDoc, readOpResult.getLength());
		}
		} finally {
			raf.close();
		}
		return error;
	}

	/**
	 * Atomically writes the history document and ParseMark in a single bulk request.
	 * This ensures that either both are written or neither is, preventing checkpoint
	 * inconsistency on crash.
	 */
	@SuppressWarnings("unchecked")
	private void writeHistoryAndMark(ElasticsearchClient esClient, String historyIndex, String historyId, Object historyDoc, int length) throws IOException {
		ParseMark parseMark = new ParseMark();
		parseMark.setFileName(fileName);
		parseMark.setPointer(pointer - length);
		parseMark.setLength(length);
		parseMark.setLastHeight(lastHeight);
		parseMark.setLastIndex(lastIndex);
		parseMark.setLastId(lastId);

		BulkRequest.Builder br = new BulkRequest.Builder();

		// Add history document if present (some protocols like NID, CONTACT, MAIL, SECRET, STATEMENT index internally)
		if (historyIndex != null && historyId != null && historyDoc != null) {
			final String idx = historyIndex;
			final String id = historyId;
			final Object doc = historyDoc;
			br.operations(op -> op.index(i -> i.index(idx).id(id).document(doc)));
		}

		// Add ParseMark
		br.operations(op -> op.index(i -> i.index(IndicesNames.FEIP_MARK).id(parseMark.getLastId()).document(parseMark)));

		BulkRequest bulkRequest = br.build();
		co.elastic.clients.elasticsearch.core.BulkResponse bulkResponse = EsRetry.bulkWithRetry(esClient, bulkRequest);
		if (bulkResponse.errors()) {
			log.error("Bulk write failed for history+mark at height {}. Errors: {}", lastHeight,
					bulkResponse.items().stream()
							.filter(item -> item.error() != null)
							.map(item -> item.error().reason())
							.toList());
		}
	}

	/**
	 * Signals the parser to stop gracefully after the current record.
	 */
	public void requestStop() {
		running.set(false);
		log.info("Parser stop requested.");
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public long getPointer() {
		return pointer;
	}

	public void setPointer(long pointer) {
		this.pointer = pointer;
	}

	public long getLastHeight() {
		return lastHeight;
	}

	public void setLastHeight(long lastHeight) {
		this.lastHeight = lastHeight;
	}

	public long getLastIndex() {
		return lastIndex;
	}

	public void setLastIndex(int lastIndex) {
		this.lastIndex = lastIndex;
	}

	public String getLastId() {
		return lastId;
	}

	public void setLastId(String lastId) {
		this.lastId = lastId;
	}

	public int getLength() {
		return length;
	}

	public void setLength(int length) {
		this.length = length;
	}

	public void reparseIdList(ElasticsearchClient esClient, String index, List<String> idList) throws Exception {

		if(idList==null || idList.isEmpty())return;
		switch (index) {
			case IndicesNames.FREER:
				// Clear only FEIP-managed fields instead of deleting entire document,
				// to preserve blockchain fields (balance, cash, income, etc.) from BlockWriter
				clearFeipFieldsFromFreer(esClient, (ArrayList<String>) idList);
				TimeUnit.SECONDS.sleep(2);

				ArrayList<FreerHist> reparseCidList = getReparseHistList(esClient, IndicesNames.FREER_HISTORY,idList,"signer", FreerHist.class);

				for(FreerHist idHist: reparseCidList) {
					new IdentityParser().parseCidInfo(esClient,idHist);
				}
				new IdentityRollbacker().reviseCidRepuAndHot(esClient, (ArrayList<String>) idList);
				break;
			case IndicesNames.PROTOCOL:
				EsUtils.bulkDeleteList(esClient, IndicesNames.PROTOCOL, (ArrayList<String>) idList);
				TimeUnit.SECONDS.sleep(2);

				ArrayList<ProtocolHistory> reparseFreeProtocolList = getReparseHistList(esClient, IndicesNames.PROTOCOL_HISTORY,idList,"pid", ProtocolHistory.class);

				for(ProtocolHistory idHist: reparseFreeProtocolList) {
					new ConstructParser().parseProtocol(esClient, idHist);
				}
				break;
			case IndicesNames.CODE:
				EsUtils.bulkDeleteList(esClient, IndicesNames.CODE, (ArrayList<String>) idList);
				TimeUnit.SECONDS.sleep(2);

				ArrayList<CodeHistory> reparseCodeList = getReparseHistList(esClient, IndicesNames.CODE_HISTORY,idList,"coid",CodeHistory.class);

				for(CodeHistory idHist: reparseCodeList) {
					new ConstructParser().parseCode(esClient, idHist);
				}
				break;
			case IndicesNames.APP:
				EsUtils.bulkDeleteList(esClient, IndicesNames.APP, (ArrayList<String>) idList);
				TimeUnit.SECONDS.sleep(2);

				ArrayList<AppHistory> reparseAppList = getReparseHistList(esClient, IndicesNames.APP_HISTORY,idList,"aid",AppHistory.class);

				for(AppHistory idHist: reparseAppList) {
					new ConstructParser().parseApp(esClient, idHist);
				}
				break;
			case IndicesNames.SERVICE:
				EsUtils.bulkDeleteList(esClient, IndicesNames.SERVICE, (ArrayList<String>) idList);
				TimeUnit.SECONDS.sleep(2);
				ArrayList<ServiceHistory> reparseServiceList = getReparseHistList(esClient, IndicesNames.SERVICE_HISTORY,idList,"sid",ServiceHistory.class);

				for(ServiceHistory idHist: reparseServiceList) {
					new ConstructParser().parseService(esClient, idHist);
				}
				break;
			case IndicesNames.SQUARE:
				EsUtils.bulkDeleteList(esClient, IndicesNames.SQUARE, (ArrayList<String>) idList);
				TimeUnit.SECONDS.sleep(2);
				ArrayList<SquareHistory> reparseSquareList = getReparseHistList(esClient, IndicesNames.SQUARE_HISTORY,idList,"gid",SquareHistory.class);

				for(SquareHistory idHist: reparseSquareList) {
					new OrganizationParser().parseSquare(esClient, idHist);
				}
				break;
			case IndicesNames.TEAM:
				EsUtils.bulkDeleteList(esClient, IndicesNames.TEAM, (ArrayList<String>) idList);
				TimeUnit.SECONDS.sleep(2);
				ArrayList<TeamHistory> reparseTeamList = getReparseHistList(esClient, IndicesNames.TEAM_HISTORY,idList,"tid",TeamHistory.class);

				for(TeamHistory idHist: reparseTeamList) {
					new OrganizationParser().parseTeam(esClient, idHist);
				}
				break;
			default:
				break;
		}
	}

	private <T>ArrayList<T> getReparseHistList(ElasticsearchClient esClient, String histIndex,
											   List<String> idList, String idField, Class<T> clazz)
			throws ElasticsearchException, IOException {

		List<FieldValue> fieldValueList = new ArrayList<FieldValue>();
		for(String id:idList) {
			fieldValueList.add(FieldValue.of(id));
		}

		SearchResponse<T> result = esClient.search(s->s
						.index(histIndex)
						.query(q->q
								.terms(t->t
										.field(idField)
										.terms(t1->t1.value(fieldValueList))))
				, clazz);
		if(result.hits()==null||result.hits().total()==null){
			log.info("Result is null");
			return null;
		}
		if(result.hits().total().value()==0)return null;
		List<Hit<T>> hitList = result.hits().hits();
		ArrayList <T> reparseList = new ArrayList<T>();
		for(Hit<T> hit:hitList) {
			reparseList.add(hit.source());
		}
		return reparseList;
	}

	/**
	 * Clear only FEIP-managed fields from Freer documents instead of deleting them,
	 * so blockchain fields (balance, cash, income, cd, cdd, weight, etc.) are preserved.
	 */
	private void clearFeipFieldsFromFreer(ElasticsearchClient esClient, ArrayList<String> idList) throws Exception {
		if (idList == null || idList.isEmpty()) return;

		Map<String, Object> clearFields = new HashMap<>();
		for (String field : FeipConstants.FREER_FEIP_FIELDS) {
			clearFields.put(field, null);
		}

		BulkRequest.Builder br = new BulkRequest.Builder();
		for (String id : idList) {
			br.operations(op -> op.update(u -> u
					.index(IndicesNames.FREER)
					.id(id)
					.action(a -> a.doc(clearFields))));
		}
		esClient.bulk(br.build());
	}
}
