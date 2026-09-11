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
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.json.JsonData;
import constants.FieldNames;
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

	/** First OpReturn file; replay starts here when no parse mark precedes an interrupted op. */
	static final String FIRST_OP_FILE = "opreturn0.byte";

	/**
	 * FEIP_MARK document naming the op currently being applied; see {@link #beginOp}. It carries
	 * no lastHeight, so the parse-mark searches (which require one) never mistake it for a mark.
	 */
	static final String INFLIGHT_ID = "inflight";
	private static final String INFLIGHT_FILE = "inflightFile";
	private static final String INFLIGHT_POINTER = "inflightPointer";
	private static final String INFLIGHT_HEIGHT = "inflightHeight";
	private static final String INFLIGHT_OP_ID = "inflightOpId";

	/** A history document produced by a protocol's make step, and the index it belongs to. */
	record HistRef(String index, String id, Object doc) {
		static HistRef of(String index, data.fcData.FcEntity hist) {
			return hist == null ? null : new HistRef(index, hist.getId(), hist);
		}
	}

	private final IdentityParser identityParser = new IdentityParser();
	private final ConstructParser constructParser = new ConstructParser();
	private final PersonalParser personalParser = new PersonalParser();
	private final PublishParser publishParser = new PublishParser();
	private final FinanceParser financeParser = new FinanceParser();
	private final OrganizationParser organizationParser = new OrganizationParser();

	/** Ops that threw part-way through in this run and were rolled back; they are not re-applied. */
	private final Set<String> poisonedOps = new HashSet<>();

	public boolean parseFile(ElasticsearchClient esClient, boolean isRollback) throws Exception {

		if(isRollback) {
			rollbackAll(esClient, lastHeight);
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
			// Reopen file if fileName changed (file switch, or a rewind by recoverPartialOp)
			if (!currentFileName.equals(fileName)) {
				raf.close();
				currentFileName = fileName;
				raf = new RandomAccessFile(new File(path, currentFileName), "r");
			}
			raf.seek(pointer);
			opReReadResult readOpResult = OpReFileUtils.readOpReFromFile(raf);
			length = readOpResult.getLength();
			pointer += length;

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
					// Reopen after a long wait: the JIT may drop raf from its live-variable
					// set while blocked here, allowing the GC to run its FileCleanable and
					// close the fd before we return. Reopening guarantees a valid handle.
					try { raf.close(); } catch (IOException ignored) {}
					raf = new RandomAccessFile(new File(path, currentFileName), "r");
					continue;
				}
			}


			if(readOpResult.isRollback()) {
				long rollbackHeight = readOpResult.getOpReturn().getHeight();
				rollbackAll(esClient, rollbackHeight);
				// Mark the marker itself, or a restart before the next valid op resumes from a mark
				// the rollback just deleted -- or from one written before this marker.
				lastHeight = rollbackHeight;
				lastIndex = Integer.MAX_VALUE;
				lastId = "rollback@" + fileName + ":" + (pointer - length);
				writeHistoryAndMark(esClient, null, length);
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

			showFound(protocolName.name(), opre);

			HistRef hist = null;
			boolean isValid = false;
			boolean begun = false;
			try {
				if (poisonedOps.contains(opre.getId())) {
					log.warn("Not applying {} {}: it threw part-way through earlier in this run and was rolled back.",
							protocolName, opre.getId());
				} else if (writesOwnDocuments(protocolName)) {
					beginOp(esClient, opre);
					begun = true;
					isValid = applyInline(esClient, protocolName, opre, feip);
				} else {
					hist = makeHistory(protocolName, opre, feip);
					if (hist != null) {
						beginOp(esClient, opre);
						begun = true;
						isValid = applyHistory(esClient, protocolName, hist.doc());
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
				if (begun && hist != null) {
					// It threw after it may already have written. Undo it by rebuilding what it
					// touched, then replay from before it without it.
					poisonedOps.add(opre.getId());
					recoverPartialOp(esClient, opre, hist);
					continue;
				}
			}
			writeHistoryAndMark(esClient, isValid ? hist : null, readOpResult.getLength());
		}
		} finally {
			raf.close();
		}
		return error;
	}

	/** Protocols whose parse step indexes its own documents and keeps no *_HISTORY record. */
	static boolean writesOwnDocuments(Feip.FeipProtocol protocol) {
		return switch (protocol) {
			case NID, CONTACT, MAIL, SECRET, STATEMENT -> true;
			default -> false;
		};
	}

	/** Pure: builds the history document for an op, or null if the op is malformed. */
	HistRef makeHistory(Feip.FeipProtocol protocol, OpReturn opre, Feip feip) {
		return switch (protocol) {
			case CID -> HistRef.of(IndicesNames.FREER_HISTORY, identityParser.makeCid(opre, feip));
			case NOBODY -> HistRef.of(IndicesNames.FREER_HISTORY, identityParser.makeNobody(opre, feip));
			case MASTER -> HistRef.of(IndicesNames.FREER_HISTORY, identityParser.makeMaster(opre, feip));
			case HOME -> HistRef.of(IndicesNames.FREER_HISTORY, identityParser.makeHome(opre, feip));
			case NOTICE_FEE -> HistRef.of(IndicesNames.FREER_HISTORY, identityParser.makeNoticeFee(opre, feip));
			case REPUTATION -> HistRef.of(IndicesNames.REPUTATION_HISTORY, identityParser.makeReputation(opre, feip));
			case PROTOCOL -> HistRef.of(IndicesNames.PROTOCOL_HISTORY, constructParser.makeProtocol(opre, feip));
			case SERVICE -> HistRef.of(IndicesNames.SERVICE_HISTORY, constructParser.makeService(opre, feip));
			case APP -> HistRef.of(IndicesNames.APP_HISTORY, constructParser.makeApp(opre, feip));
			case CODE -> HistRef.of(IndicesNames.CODE_HISTORY, constructParser.makeCode(opre, feip));
			case TEXT -> HistRef.of(IndicesNames.TEXT_HISTORY, publishParser.makeText(opre, feip));
			case REMARK -> HistRef.of(IndicesNames.REMARK_HISTORY, publishParser.makeRemark(opre, feip));
			case SQUARE -> HistRef.of(IndicesNames.SQUARE_HISTORY, organizationParser.makeSquare(opre, feip));
			case TEAM -> HistRef.of(IndicesNames.TEAM_HISTORY, organizationParser.makeTeam(opre, feip));
			case BOX -> HistRef.of(IndicesNames.BOX_HISTORY, personalParser.makeBox(opre, feip));
			case PROOF -> HistRef.of(IndicesNames.PROOF_HISTORY, financeParser.makeProof(opre, feip));
			case TOKEN -> HistRef.of(IndicesNames.TOKEN_HISTORY, financeParser.makeToken(opre, feip));
			case SOUND -> HistRef.of(IndicesNames.SOUND_HISTORY, publishParser.makeSound(opre, feip));
			case IMAGE -> HistRef.of(IndicesNames.IMAGE_HISTORY, publishParser.makeImage(opre, feip));
			case VIDEO -> HistRef.of(IndicesNames.VIDEO_HISTORY, publishParser.makeVideo(opre, feip));
			default -> null;
		};
	}

	private boolean applyHistory(ElasticsearchClient esClient, Feip.FeipProtocol protocol, Object hist) throws Exception {
		return switch (protocol) {
			case CID, NOBODY, MASTER, HOME, NOTICE_FEE -> identityParser.parseCidInfo(esClient, (FreerHist) hist);
			case REPUTATION -> identityParser.parseReputation(esClient, (RepuHist) hist);
			case PROTOCOL -> constructParser.parseProtocol(esClient, (ProtocolHistory) hist);
			case SERVICE -> constructParser.parseService(esClient, (ServiceHistory) hist);
			case APP -> constructParser.parseApp(esClient, (AppHistory) hist);
			case CODE -> constructParser.parseCode(esClient, (CodeHistory) hist);
			case TEXT -> publishParser.parseText(esClient, (TextHistory) hist);
			case REMARK -> publishParser.parseRemark(esClient, (RemarkHistory) hist);
			case SQUARE -> organizationParser.parseSquare(esClient, (SquareHistory) hist);
			case TEAM -> organizationParser.parseTeam(esClient, (TeamHistory) hist);
			case BOX -> personalParser.parseBox(esClient, (BoxHistory) hist);
			case PROOF -> financeParser.parseProof(esClient, (ProofHistory) hist);
			case TOKEN -> {
				try {
					yield financeParser.parseToken(esClient, (TokenHistory) hist);
				} catch (NumberFormatException e) {
					log.error("NumberFormatException parsing token {}.", ((TokenHistory) hist).getId(), e);
					yield false;
				}
			}
			case SOUND -> publishParser.parseSound(esClient, (SoundHistory) hist);
			case IMAGE -> publishParser.parseImage(esClient, (ImageHistory) hist);
			case VIDEO -> publishParser.parseVideo(esClient, (VideoHistory) hist);
			default -> false;
		};
	}

	private boolean applyInline(ElasticsearchClient esClient, Feip.FeipProtocol protocol, OpReturn opre, Feip feip) throws Exception {
		return switch (protocol) {
			case NID -> identityParser.parseNid(esClient, opre, feip);
			case CONTACT -> personalParser.parseContact(esClient, opre, feip);
			case MAIL -> personalParser.parseMail(esClient, opre, feip);
			case SECRET -> personalParser.parseSecret(esClient, opre, feip);
			case STATEMENT -> publishParser.parseStatement(esClient, opre, feip);
			default -> false;
		};
	}

	/**
	 * Record the op about to be applied, before any of its writes.
	 *
	 * An op's state writes, its history and its ParseMark are separate Elasticsearch requests, so
	 * a crash between them used to leave the op applied but unmarked, and the restart applied it
	 * again: a token issue or transfer counted twice. With this record present at startup,
	 * {@link #recoverInterruptedOp} rolls back to before the op and replays it once. The record is
	 * deleted in the same bulk that writes the op's ParseMark.
	 */
	private void beginOp(ElasticsearchClient esClient, OpReturn opre) throws IOException {
		Map<String, Object> doc = new HashMap<>();
		doc.put(INFLIGHT_FILE, fileName);
		doc.put(INFLIGHT_POINTER, pointer - length);
		doc.put(INFLIGHT_HEIGHT, opre.getHeight());
		doc.put(INFLIGHT_OP_ID, opre.getId());
		EsRetry.executeWithRetry(() -> esClient.index(i -> i.index(IndicesNames.FEIP_MARK).id(INFLIGHT_ID).document(doc)));
	}

	/**
	 * Write the history document (if any) and the ParseMark, and clear the in-flight record, in
	 * one bulk. Bulk items are not atomic with each other, so a failure here stops the parser
	 * rather than carrying on without a mark; the in-flight record, if it survived, makes the
	 * next start roll back and replay the op.
	 */
	private void writeHistoryAndMark(ElasticsearchClient esClient, HistRef hist, int length) throws IOException {
		ParseMark parseMark = new ParseMark();
		parseMark.setFileName(fileName);
		parseMark.setPointer(pointer - length);
		parseMark.setLength(length);
		parseMark.setLastHeight(lastHeight);
		parseMark.setLastIndex(lastIndex);
		parseMark.setLastId(lastId);

		BulkRequest.Builder br = new BulkRequest.Builder();

		if (hist != null) {
			br.operations(op -> op.index(i -> i.index(hist.index()).id(hist.id()).document(hist.doc())));
		}

		br.operations(op -> op.index(i -> i.index(IndicesNames.FEIP_MARK).id(parseMark.getLastId()).document(parseMark)));
		br.operations(op -> op.delete(d -> d.index(IndicesNames.FEIP_MARK).id(INFLIGHT_ID)));

		BulkRequest bulkRequest = br.build();
		co.elastic.clients.elasticsearch.core.BulkResponse bulkResponse = EsRetry.bulkWithRetry(esClient, bulkRequest);
		if (bulkResponse.errors()) {
			List<String> reasons = bulkResponse.items().stream()
					.filter(item -> item.error() != null)
					.map(item -> item.error().reason())
					.toList();
			log.error("Bulk write failed for history+mark at height {}. Errors: {}", lastHeight, reasons);
			throw new IOException("Failed to write history and parse mark at height " + lastHeight + ": " + reasons);
		}
	}

	/**
	 * An op threw after it may have written. Roll back to before it and move the read position
	 * back to the last mark below its height, so everything after that mark is replayed. The op
	 * itself is in {@link #poisonedOps} and is marked but not re-applied when the replay reaches it.
	 */
	private void recoverPartialOp(ElasticsearchClient esClient, OpReturn opre, HistRef hist) throws Exception {
		log.warn("Rolling back to before {} at height {} after it failed part-way.", opre.getId(), opre.getHeight());
		ParseMark resume = rollBackPartialOp(esClient, opre.getHeight(), hist);
		if (resume == null) {
			fileName = FIRST_OP_FILE;
			pointer = 0;
		} else {
			fileName = resume.getFileName();
			pointer = resume.getPointer() + resume.getLength();
		}
		length = 0;
	}

	/**
	 * Index the op's history so the rollbackers can see what it touched, then roll back to the
	 * last parse mark below its height.
	 *
	 * @return that mark, from which parsing must resume; null if there is none, in which case
	 * parsing must restart from the first file
	 */
	private static ParseMark rollBackPartialOp(ElasticsearchClient esClient, long opHeight, HistRef hist) throws Exception {
		if (hist != null) {
			esClient.index(i -> i.index(hist.index()).id(hist.id()).document(hist.doc())
					.refresh(co.elastic.clients.elasticsearch._types.Refresh.True));
		}
		ParseMark resume = findLatestMark(esClient, opHeight - 1);
		long rollbackHeight = resume != null ? resume.getLastHeight() : opHeight - 1;
		rollbackAll(esClient, rollbackHeight);
		return resume;
	}

	/**
	 * If the previous run stopped while an op was being applied, roll back to before it so the
	 * resumed parse applies it exactly once. Must run before the resume mark is chosen: the
	 * rollback deletes the marks above the height it rolls back to.
	 */
	public static void recoverInterruptedOp(ElasticsearchClient esClient, String path) throws Exception {
		co.elastic.clients.elasticsearch.core.GetResponse<Map> got =
				esClient.get(g -> g.index(IndicesNames.FEIP_MARK).id(INFLIGHT_ID), Map.class);
		if (!got.found() || got.source() == null) return;

		Map<?, ?> inflight = got.source();
		String file = (String) inflight.get(INFLIGHT_FILE);
		long opPointer = ((Number) inflight.get(INFLIGHT_POINTER)).longValue();
		long opHeight = ((Number) inflight.get(INFLIGHT_HEIGHT)).longValue();
		log.warn("The last run stopped while applying op {} at height {}. Rolling back to before it.",
				inflight.get(INFLIGHT_OP_ID), opHeight);

		HistRef hist = null;
		try (RandomAccessFile raf = new RandomAccessFile(new File(path, file), "r")) {
			raf.seek(opPointer);
			opReReadResult read = OpReFileUtils.readOpReFromFile(raf);
			if (!read.isFileEnd() && !read.isRollback() && read.getOpReturn() != null) {
				OpReturn opre = read.getOpReturn();
				Feip feip = parseFeip(opre);
				Feip.FeipProtocol protocol = (feip == null || feip.getSn() == null) ? null : Feip.FeipProtocol.fromSn(feip.getSn());
				if (protocol != null && !writesOwnDocuments(protocol)) {
					hist = new FileParser().makeHistory(protocol, opre, feip);
				}
			}
		}

		rollBackPartialOp(esClient, opHeight, hist);
		esClient.delete(d -> d.index(IndicesNames.FEIP_MARK).id(INFLIGHT_ID)
				.refresh(co.elastic.clients.elasticsearch._types.Refresh.True));
	}

	/**
	 * The mark of the last op parsed at or below `maxHeight` (null for no bound).
	 *
	 * Marks are ordered by (lastHeight, lastIndex). This used to be written as two `.field()` calls
	 * on one FieldSort builder, which keeps only the second, so the sort was by height alone and
	 * several ops in one block resumed from an arbitrary one of them -- replaying the ops after it.
	 */
	public static ParseMark findLatestMark(ElasticsearchClient esClient, Long maxHeight) throws IOException {
		esClient.indices().refresh(r -> r.index(IndicesNames.FEIP_MARK));
		SearchResponse<ParseMark> result = esClient.search(s -> s
						.index(IndicesNames.FEIP_MARK)
						.query(q -> q.bool(b -> {
							b.filter(f -> f.exists(e -> e.field(FieldNames.LAST_HEIGHT)));
							if (maxHeight != null)
								b.filter(f -> f.range(r -> r.field(FieldNames.LAST_HEIGHT).lte(JsonData.of(maxHeight))));
							return b;
						}))
						.size(1)
						.sort(s1 -> s1.field(f -> f.field(FieldNames.LAST_HEIGHT).order(SortOrder.Desc)))
						.sort(s1 -> s1.field(f -> f.field(FieldNames.LAST_INDEX).order(SortOrder.Desc)))
				, ParseMark.class);
		if (result.hits() == null || result.hits().hits().isEmpty()) return null;
		return result.hits().hits().get(0).source();
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

				ArrayList<FreerHist> reparseCidList = getReparseHistList(esClient, IndicesNames.FREER_HISTORY,idList,FieldNames.SIGNER, FreerHist.class);

				for(FreerHist idHist: reparseCidList) {
					new IdentityParser().parseCidInfo(esClient,idHist);
				}
				new IdentityRollbacker().reviseCidRepuAndHot(esClient, (ArrayList<String>) idList);
				break;
			case IndicesNames.PROTOCOL:
				EsUtils.bulkDeleteList(esClient, IndicesNames.PROTOCOL, (ArrayList<String>) idList);
				TimeUnit.SECONDS.sleep(2);

				ArrayList<ProtocolHistory> reparseFreeProtocolList = getReparseHistList(esClient, IndicesNames.PROTOCOL_HISTORY,idList,FieldNames.PID, ProtocolHistory.class);

				for(ProtocolHistory idHist: reparseFreeProtocolList) {
					new ConstructParser().parseProtocol(esClient, idHist);
				}
				break;
			case IndicesNames.CODE:
				EsUtils.bulkDeleteList(esClient, IndicesNames.CODE, (ArrayList<String>) idList);
				TimeUnit.SECONDS.sleep(2);

				ArrayList<CodeHistory> reparseCodeList = getReparseHistList(esClient, IndicesNames.CODE_HISTORY,idList,FieldNames.CODE_ID,CodeHistory.class);

				for(CodeHistory idHist: reparseCodeList) {
					new ConstructParser().parseCode(esClient, idHist);
				}
				break;
			case IndicesNames.APP:
				EsUtils.bulkDeleteList(esClient, IndicesNames.APP, (ArrayList<String>) idList);
				TimeUnit.SECONDS.sleep(2);

				ArrayList<AppHistory> reparseAppList = getReparseHistList(esClient, IndicesNames.APP_HISTORY,idList,FieldNames.AID,AppHistory.class);

				for(AppHistory idHist: reparseAppList) {
					new ConstructParser().parseApp(esClient, idHist);
				}
				break;
			case IndicesNames.SERVICE:
				EsUtils.bulkDeleteList(esClient, IndicesNames.SERVICE, (ArrayList<String>) idList);
				TimeUnit.SECONDS.sleep(2);
				ArrayList<ServiceHistory> reparseServiceList = getReparseHistList(esClient, IndicesNames.SERVICE_HISTORY,idList,FieldNames.SID,ServiceHistory.class);

				for(ServiceHistory idHist: reparseServiceList) {
					new ConstructParser().parseService(esClient, idHist);
				}
				break;
			case IndicesNames.SQUARE:
				EsUtils.bulkDeleteList(esClient, IndicesNames.SQUARE, (ArrayList<String>) idList);
				TimeUnit.SECONDS.sleep(2);
				ArrayList<SquareHistory> reparseSquareList = getReparseHistList(esClient, IndicesNames.SQUARE_HISTORY,idList,FieldNames.SQUARE_ID,SquareHistory.class);

				for(SquareHistory idHist: reparseSquareList) {
					new OrganizationParser().parseSquare(esClient, idHist);
				}
				break;
			case IndicesNames.TEAM:
				EsUtils.bulkDeleteList(esClient, IndicesNames.TEAM, (ArrayList<String>) idList);
				TimeUnit.SECONDS.sleep(2);
				ArrayList<TeamHistory> reparseTeamList = getReparseHistList(esClient, IndicesNames.TEAM_HISTORY,idList,FieldNames.TID,TeamHistory.class);

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
		// Paginated and in (height, index) order: a replay out of order, or of only the first ten
		// histories, rebuilds a different entity.
		return new ArrayList<>(EsUtils.getHistsForReparse(esClient, histIndex, idField, null,
				new ArrayList<>(idList), clazz));
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

	/**
	 * Run every category rollbacker for `height` and fail loudly if any of them reported an error.
	 *
	 * All six must run even when an earlier one fails, or throws: skipping the rest would leave
	 * the indices holding a mixture of pre- and post-reorg state. But once a failure has happened
	 * the database IS in that mixed state, so parsing must not continue on top of it --
	 * BlockWriter takes the same line and throws rather than advancing the chain after a failed
	 * write.
	 */
	static void rollbackAll(ElasticsearchClient esClient, long height) throws Exception {
		// The rollbackers find their work by searching, and search only sees what has been
		// refreshed. Histories and marks written in the last second would otherwise be missed.
		esClient.indices().refresh();

		boolean error = false;
		error |= runRollback("identity", height, () -> new IdentityRollbacker().rollback(esClient, height));
		error |= runRollback("construct", height, () -> new ConstructRollbacker().rollback(esClient, height));
		error |= runRollback("personal", height, () -> new PersonalRollbacker().rollback(esClient, height));
		error |= runRollback("publish", height, () -> new PublishRollbacker().rollback(esClient, height));
		error |= runRollback("organization", height, () -> new OrganizationRollbacker().rollback(esClient, height));
		error |= runRollback("finance", height, () -> new FinanceRollbacker().rollback(esClient, height));
		// Marks above the height describe ops that no longer exist. Left behind, a restart would
		// resume from one of them and skip the replay.
		error |= runRollback("parse marks", height, () -> deleteMarksAbove(esClient, height));

		if (error) {
			log.error("Rollback to height {} did not complete cleanly. FEIP indices may hold a "
					+ "mixture of pre- and post-reorg state; refusing to parse further.", height);
			throw new Exception("Incomplete FEIP rollback at height " + height);
		}
	}

	@FunctionalInterface
	private interface RollbackStep {
		boolean run() throws Exception;
	}

	private static boolean runRollback(String name, long height, RollbackStep step) {
		try {
			return step.run();
		} catch (Exception e) {
			log.error("The {} rollback to height {} threw.", name, height, e);
			return true;
		}
	}

	private static boolean deleteMarksAbove(ElasticsearchClient esClient, long height) throws IOException {
		var response = esClient.deleteByQuery(d -> d
				.index(IndicesNames.FEIP_MARK)
				.conflicts(co.elastic.clients.elasticsearch._types.Conflicts.Proceed)
				.refresh(true)
				.query(q -> q.range(r -> r.field(FieldNames.LAST_HEIGHT).gt(JsonData.of(height)))));
		if (response.failures() != null && !response.failures().isEmpty()) {
			log.error("Rollback: deleting parse marks above {} reported {} failures", height, response.failures().size());
			return true;
		}
		return false;
	}

}
