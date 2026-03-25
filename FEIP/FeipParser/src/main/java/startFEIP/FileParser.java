package startFEIP;

import data.feipData.*;
import publish.PublishParser;
import publish.PublishRollbacker;
import utils.EsUtils;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileParser {

	private String path = null;
	private  String fileName = null;
	private long pointer =0;
	private int length =0;
	private long lastHeight = 0;
	private int lastIndex = 0;
	private String lastId = null;

	public static Feip parseFeip(OpReturn opre) {

		if(opre.getOpReturn()==null)return null;

		Feip feip = null;
		try {
//			String json = JsonTools.strToJson(opre.getOpReturn());
			feip = new Gson().fromJson(opre.getOpReturn(), Feip.class);
		}catch(JsonSyntaxException e) {
			log.debug("Bad json on {}. ",opre.getId());
		}
		return  feip;
	}

	enum FEIP_NAME{
		CID,NOBODY,MASTER,HOME,NOTICE_FEE,REPUTATION,SERVICE,PROTOCOL,APP,CODE,NID, CONTACT,MAIL, SECRET,STATEMENT,GROUP,TEAM, BOX, TOKEN, PROOF,TEXT,REMARK,SOUND,IMAGE,VIDEO
	}

	private static final Logger log = LoggerFactory.getLogger(FileParser.class);

	private void showFound(String protocolName, OpReturn opre) {
		System.out.println(protocolName + " @" + opre.getHeight() + "." + opre.getId());
	}

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

		FileInputStream fis;

		pointer += length;

		System.out.println("Start parse "+fileName+ " form "+pointer);
		log.info("Start parse {} from {}",fileName,pointer);

		TimeUnit.SECONDS.sleep(2);

		boolean error = false;

		while(!error) {
			fis = openFile();
			fis.skip(pointer);
			opReReadResult readOpResult = OpReFileUtils.readOpReFromFile(fis);
			fis.close();
			length = readOpResult.getLength();
			pointer += length;

			boolean isValid= false;

			if(readOpResult.isFileEnd()) {
				if(pointer> Constants.MaxOpFileSize) {
					fileName = OpReFileUtils.getNextFile(fileName);
					while(!new File(fileName).exists()) {
						System.out.print(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis())));
						System.out.println(" Waiting 30 seconds for new file ...");
						TimeUnit.SECONDS.sleep(30);
					}
					pointer = 0;
					fis = openFile();
					continue;
				}else {
					System.out.print(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis())));
					System.out.println(" Waiting for new item ...");
					fis.close();
					AtomicBoolean running = new AtomicBoolean();
					running.set(true);
					FchUtils.waitForChangeInDirectory(path,running);
					fis = openFile();
					fis.skip(pointer);
					continue;
				}
			}


			if(readOpResult.isRollback()) {
//				newsRollbacker.rollback(esClient, readOpResult.getOpReturn().getHeight());
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

			System.out.println();

			showFound(protocolName.name(), opre);
			try {
				switch (protocolName) {
					case CID -> {
						FreerHist identityHist = identityParser.makeCid(opre, feip);
						if (identityHist == null) break;
						isValid = identityParser.parseCidInfo(esClient, identityHist);
						if (isValid)
							esClient.index(i -> i.index(IndicesNames.FREER_HISTORY).id(identityHist.getId()).document(identityHist));
					}
					case NOBODY -> {

						FreerHist identityHist4 = identityParser.makeNobody(opre, feip);
						if (identityHist4 == null) break;
						isValid = identityParser.parseCidInfo(esClient, identityHist4);
						if (isValid)
							esClient.index(i -> i.index(IndicesNames.FREER_HISTORY).id(identityHist4.getId()).document(identityHist4));
					}
					case MASTER -> {

						FreerHist identityHist1 = identityParser.makeMaster(opre, feip);
						if (identityHist1 == null) break;
						isValid = identityParser.parseCidInfo(esClient, identityHist1);
						if (isValid)
							esClient.index(i -> i.index(IndicesNames.FREER_HISTORY).id(identityHist1.getId()).document(identityHist1));
					}
					case HOME -> {

						FreerHist identityHist2 = identityParser.makeHome(opre, feip);
						if (identityHist2 == null) break;
						isValid = identityParser.parseCidInfo(esClient, identityHist2);
						if (isValid)
							esClient.index(i -> i.index(IndicesNames.FREER_HISTORY).id(identityHist2.getId()).document(identityHist2));
					}
					case NOTICE_FEE -> {

						FreerHist identityHist3 = identityParser.makeNoticeFee(opre, feip);
						if (identityHist3 == null) break;
						isValid = identityParser.parseCidInfo(esClient, identityHist3);
						if (isValid)
							esClient.index(i -> i.index(IndicesNames.FREER_HISTORY).id(identityHist3.getId()).document(identityHist3));
					}
					case REPUTATION -> {

						RepuHist repuHist = identityParser.makeReputation(opre, feip);
						if (repuHist == null) break;
						isValid = identityParser.parseReputation(esClient, repuHist);
						if (isValid)
							esClient.index(i -> i.index(IndicesNames.REPUTATION_HISTORY).id(repuHist.getId()).document(repuHist));
					}
					case PROTOCOL -> {

						ProtocolHistory freeProtocolHist = constructParser.makeProtocol(opre, feip);
						if (freeProtocolHist == null) break;
						isValid = constructParser.parseProtocol(esClient, freeProtocolHist);
						if (isValid)
							esClient.index(i -> i.index(IndicesNames.PROTOCOL_HISTORY).id(freeProtocolHist.getId()).document(freeProtocolHist));
					}
					case SERVICE -> {

						ServiceHistory serviceHist = constructParser.makeService(opre, feip);
						if (serviceHist == null) break;
						isValid = constructParser.parseService(esClient, serviceHist);
						if (isValid)
							esClient.index(i -> i.index(IndicesNames.SERVICE_HISTORY).id(serviceHist.getId()).document(serviceHist));
					}
					case APP -> {

						AppHistory appHist = constructParser.makeApp(opre, feip);
						if (appHist == null) break;
						isValid = constructParser.parseApp(esClient, appHist);
						if (isValid)
							esClient.index(i -> i.index(IndicesNames.APP_HISTORY).id(appHist.getId()).document(appHist));
					}
					case CODE -> {

						CodeHistory codeHist = constructParser.makeCode(opre, feip);
						if (codeHist == null) break;
						isValid = constructParser.parseCode(esClient, codeHist);
						if (isValid)
							esClient.index(i -> i.index(IndicesNames.CODE_HISTORY).id(codeHist.getId()).document(codeHist));
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
						if (isValid)
							esClient.index(i -> i.index(IndicesNames.TEXT_HISTORY).id(textHist.getId()).document(textHist));
					}
					case REMARK -> {

						RemarkHistory remarkHist = publishParser.makeRemark(opre, feip);
						if (remarkHist == null) break;
						isValid = publishParser.parseRemark(esClient, remarkHist);
						if (isValid)
							esClient.index(i -> i.index(IndicesNames.REMARK_HISTORY).id(remarkHist.getId()).document(remarkHist));
					}
					case SQUARE -> {

						SquareHistory squareHist = organizationParser.makeSquare(opre, feip);
						if (squareHist == null) break;
						isValid = organizationParser.parseSquare(esClient, squareHist);
						if (isValid)
							esClient.index(i -> i.index(IndicesNames.SQUARE_HISTORY).id(squareHist.getId()).document(squareHist));
					}
					case TEAM -> {

						TeamHistory teamHist = organizationParser.makeTeam(opre, feip);
						if (teamHist == null) break;
						isValid = organizationParser.parseTeam(esClient, teamHist);
						if (isValid)
							esClient.index(i -> i.index(IndicesNames.TEAM_HISTORY).id(teamHist.getId()).document(teamHist));
					}
					case BOX -> {

						BoxHistory boxHist = personalParser.makeBox(opre, feip);
						if (boxHist == null) break;
						isValid = personalParser.parseBox(esClient, boxHist);
						if (isValid)
							esClient.index(i -> i.index(IndicesNames.BOX_HISTORY).id(boxHist.getId()).document(boxHist));
					}
					case PROOF -> {

						ProofHistory proofHist = financeParser.makeProof(opre, feip);
						if (proofHist == null) break;
						isValid = financeParser.parseProof(esClient, proofHist);
						if (isValid)
							esClient.index(i -> i.index(IndicesNames.PROOF_HISTORY).id(proofHist.getId()).document(proofHist));
					}
					case TOKEN -> {

						TokenHistory tokenHist = financeParser.makeToken(opre, feip);
						if (tokenHist == null) break;
						try {
							isValid = financeParser.parseToken(esClient, tokenHist);
						} catch (NumberFormatException ignore) {
						}
						if (isValid)
							esClient.index(i -> i.index(IndicesNames.TOKEN_HISTORY).id(tokenHist.getId()).document(tokenHist));
					}
					case SOUND -> {

						SoundHistory soundHist = publishParser.makeSound(opre, feip);
						if (soundHist == null) break;
						isValid = publishParser.parseSound(esClient, soundHist);
						if (isValid)
							esClient.index(i -> i.index(IndicesNames.SOUND_HISTORY).id(soundHist.getId()).document(soundHist));
					}
					case IMAGE -> {

						ImageHistory imageHist = publishParser.makeImage(opre, feip);
						if (imageHist == null) break;
						isValid = publishParser.parseImage(esClient, imageHist);
						if (isValid)
							esClient.index(i -> i.index(IndicesNames.IMAGE_HISTORY).id(imageHist.getId()).document(imageHist));
					}
					case VIDEO -> {

						VideoHistory videoHist = publishParser.makeVideo(opre, feip);
						if (videoHist == null) break;
						isValid = publishParser.parseVideo(esClient, videoHist);
						if (isValid)
							esClient.index(i -> i.index(IndicesNames.VIDEO_HISTORY).id(videoHist.getId()).document(videoHist));
					}
					default -> {
					}
				}
			}catch (Exception e){
				log.debug("Parsing failed.",e);
			}
			if(isValid)writeParseMark(esClient,readOpResult.getLength());
		}
		return error;
	}

	private void writeParseMark(ElasticsearchClient esClient, int length) throws IOException {

		ParseMark parseMark= new ParseMark();

		parseMark.setFileName(fileName);
		parseMark.setPointer(pointer-length);
		parseMark.setLength(length);
		parseMark.setLastHeight(lastHeight);
		parseMark.setLastIndex(lastIndex);
		parseMark.setLastId(lastId);

		esClient.index(i->i.index(IndicesNames.FEIP_MARK).id(parseMark.getLastId()).document(parseMark));
	}

	private FileInputStream openFile() throws FileNotFoundException {

		File file = new File(path,fileName);
		return new FileInputStream(file);
	}
//
//	private FEIP_NAME checkFeipSn(Feip feip) {
//
//		String sn = feip.getSn();
//		if(sn.equals("1"))return FEIP_NAME.PROTOCOL;
//		if(sn.equals("2"))return FEIP_NAME.CODE;
//		if(sn.equals("3"))return FEIP_NAME.CID;
//		if(sn.equals("4"))return FEIP_NAME.NOBODY;
//		if(sn.equals("5"))return FEIP_NAME.SERVICE;
//		if(sn.equals("6"))return FEIP_NAME.MASTER;
//		if(sn.equals("7"))return FEIP_NAME.MAIL;
//		if(sn.equals("8"))return FEIP_NAME.STATEMENT;
//		if(sn.equals("9"))return FEIP_NAME.HOMEPAGE;
//		if(sn.equals("10"))return FEIP_NAME.NOTICE_FEE;
//		if(sn.equals("11"))return FEIP_NAME.NID;
//		if(sn.equals("12"))return FEIP_NAME.CONTACT;
//
//		if(sn.equals("13"))return FEIP_NAME.BOX;
//		if(sn.equals("14"))return FEIP_NAME.PROOF;
//		if(sn.equals("15"))return FEIP_NAME.APP;
//		if(sn.equals("16"))return FEIP_NAME.REPUTATION;
//		if(sn.equals("17"))return FEIP_NAME.SECRET;
//		if(sn.equals("18"))return FEIP_NAME.TEAM;
//		if(sn.equals("19"))return FEIP_NAME.GROUP;
//		if(sn.equals("20"))return FEIP_NAME.TOKEN;
//
//		if(sn.equals("21"))return FEIP_NAME.TEXT;
//		if(sn.equals("26"))return FEIP_NAME.REMARK;
//		if(sn.equals("27"))return FEIP_NAME.SOUND;
//		if(sn.equals("28"))return FEIP_NAME.IMAGE;
//		if(sn.equals("29"))return FEIP_NAME.VIDEO;
//
//		return null;
//	}


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
				EsUtils.bulkDeleteList(esClient, IndicesNames.FREER, (ArrayList<String>) idList);
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
			System.out.println("Result is null");
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
}
