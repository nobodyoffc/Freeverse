package startFEIP;

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

import fch.OpReFileUtils;
import fch.FchUtils;
import fch.fchData.OpReturn;
import fch.opReReadResult;
import feip.feipData.*;
import identity.IdentityParser;
import identity.IdentityRollbacker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import organize.OrganizationParser;
import organize.OrganizationRollbacker;

import personal.PersonalParser;
import personal.PersonalRollbacker;
import publish.PublishParser;
import publish.PublishRollbacker;

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
		CID,NOBODY,MASTER,HOMEPAGE,NOTICE_FEE,REPUTATION,SERVICE,PROTOCOL,APP,CODE,NID, CONTACT,MAIL, SECRET,STATEMENT,GROUP,TEAM, BOX, TOKEN, PROOF
	}

	private static final Logger log = LoggerFactory.getLogger(FileParser.class);

	public boolean parseFile(ElasticsearchClient esClient, boolean isRollback) throws Exception {

		IdentityRollbacker cidRollbacker = new IdentityRollbacker();
		IdentityParser cidParser = new IdentityParser();

		ConstructParser constructParser = new ConstructParser();
		ConstructRollbacker constructRollbacker = new ConstructRollbacker();

		PersonalParser personalParser = new PersonalParser();
		PersonalRollbacker personalRollbacker = new PersonalRollbacker();

		PublishParser publishParser = new PublishParser();
		PublishRollbacker publishRollbacker = new PublishRollbacker();

		OrganizationParser organizationParser = new OrganizationParser();
		OrganizationRollbacker organizationRollbacker = new OrganizationRollbacker();

		if(isRollback) {
			cidRollbacker.rollback(esClient, lastHeight);
			constructRollbacker.rollback(esClient, lastHeight);
			personalRollbacker.rollback(esClient, lastHeight);
			organizationRollbacker.rollback(esClient, lastHeight);
			publishRollbacker.rollback(esClient, lastHeight);
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
					long result = fis.skip(pointer);
					continue;
				}
			}


			if(readOpResult.isRollback()) {
				cidRollbacker.rollback(esClient,readOpResult.getOpReturn().getHeight());
				constructRollbacker.rollback(esClient, readOpResult.getOpReturn().getHeight());
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

			FEIP_NAME feipName = checkFeipSn(feip);
			if(feipName == null)continue;
			System.out.println();
			switch (feipName) {
				case CID -> {
					System.out.println("Cid @"+opre.getHeight()+"."+opre.getId());
					CidHist identityHist = cidParser.makeCid(opre, feip);
					if (identityHist == null) break;
					isValid = cidParser.parseCidInfo(esClient, identityHist);
					if (isValid)
						esClient.index(i -> i.index(IndicesNames.CID_HISTORY).id(identityHist.getId()).document(identityHist));
					System.out.println(isValid);
				}
				case NOBODY -> {
					System.out.println("Nobody @"+opre.getHeight()+"."+opre.getId());
					CidHist identityHist4 = cidParser.makeNobody(opre, feip);
					if (identityHist4 == null) break;
					isValid = cidParser.parseCidInfo(esClient, identityHist4);
					if (isValid)
						esClient.index(i -> i.index(IndicesNames.CID_HISTORY).id(identityHist4.getId()).document(identityHist4));
					System.out.println(isValid);
				}
				case MASTER -> {
					System.out.println("Master @"+opre.getHeight()+"."+opre.getId());
					CidHist identityHist1 = cidParser.makeMaster(opre, feip);
					if (identityHist1 == null) break;
					isValid = cidParser.parseCidInfo(esClient, identityHist1);
					if (isValid)
						esClient.index(i -> i.index(IndicesNames.CID_HISTORY).id(identityHist1.getId()).document(identityHist1));
					System.out.println(isValid);
				}
				case HOMEPAGE -> {
					System.out.println("Homepage @"+opre.getHeight()+"."+opre.getId());
					CidHist identityHist2 = cidParser.makeHomepage(opre, feip);
					if (identityHist2 == null) break;
					isValid = cidParser.parseCidInfo(esClient, identityHist2);
					if (isValid)
						esClient.index(i -> i.index(IndicesNames.CID_HISTORY).id(identityHist2.getId()).document(identityHist2));
					System.out.println(isValid);
				}
				case NOTICE_FEE -> {
					System.out.println("Notice fee @"+opre.getHeight()+"."+opre.getId());
					CidHist identityHist3 = cidParser.makeNoticeFee(opre, feip);
					if (identityHist3 == null) break;
					isValid = cidParser.parseCidInfo(esClient, identityHist3);
					if (isValid)
						esClient.index(i -> i.index(IndicesNames.CID_HISTORY).id(identityHist3.getId()).document(identityHist3));
					System.out.println(isValid);
				}
				case REPUTATION -> {
					System.out.println("Reputation @"+opre.getHeight()+"."+opre.getId());
					RepuHist repuHist = cidParser.makeReputation(opre, feip);
					if (repuHist == null) break;
					isValid = cidParser.parseReputation(esClient, repuHist);
					if (isValid)
						esClient.index(i -> i.index(IndicesNames.REPUTATION_HISTORY).id(repuHist.getId()).document(repuHist));
					System.out.println(isValid);
				}
				case PROTOCOL -> {
					System.out.println("Protocol @"+opre.getHeight()+"."+opre.getId());
					ProtocolHistory freeProtocolHist = constructParser.makeProtocol(opre, feip);
					if (freeProtocolHist == null) break;
					isValid = constructParser.parseProtocol(esClient, freeProtocolHist);
					if (isValid)
						esClient.index(i -> i.index(IndicesNames.PROTOCOL_HISTORY).id(freeProtocolHist.getId()).document(freeProtocolHist));
					System.out.println(isValid);
				}
				case SERVICE -> {
					System.out.println("Service @"+opre.getHeight()+"."+opre.getId());
					ServiceHistory serviceHist = constructParser.makeService(opre, feip);
					if (serviceHist == null) break;
					isValid = constructParser.parseService(esClient, serviceHist);
					if (isValid)
						esClient.index(i -> i.index(IndicesNames.SERVICE_HISTORY).id(serviceHist.getId()).document(serviceHist));
					System.out.println(isValid);
				}
				case APP -> {
					System.out.println("APP @"+opre.getHeight()+"."+opre.getId());
					AppHistory appHist = constructParser.makeApp(opre, feip);
					if (appHist == null) break;
					isValid = constructParser.parseApp(esClient, appHist);
					if (isValid)
						esClient.index(i -> i.index(IndicesNames.APP_HISTORY).id(appHist.getId()).document(appHist));
					System.out.println(isValid);
				}
				case CODE -> {
					System.out.println("Code @"+opre.getHeight()+"."+opre.getId());
					CodeHistory codeHist = constructParser.makeCode(opre, feip);
					if (codeHist == null) break;
					isValid = constructParser.parseCode(esClient, codeHist);
					if (isValid)
						esClient.index(i -> i.index(IndicesNames.CODE_HISTORY).id(codeHist.getId()).document(codeHist));
					System.out.println(isValid);
				}
				case NID -> {
					System.out.println("Nid @"+opre.getHeight()+"."+opre.getId());
					isValid = publishParser.parseNid(esClient, opre, feip);
					System.out.println(isValid);
				}
				case CONTACT -> {
					System.out.println("Contact @"+opre.getHeight()+"."+opre.getId());
					isValid = personalParser.parseContact(esClient, opre, feip);
					System.out.println(isValid);
				}
				case MAIL -> {
					System.out.println("Mail @"+opre.getHeight()+"."+opre.getId());
					isValid = personalParser.parseMail(esClient, opre, feip);
					System.out.println(isValid);
				}
				case SECRET -> {
					System.out.println("Secret @"+opre.getHeight()+"."+opre.getId());
					isValid = personalParser.parseSecret(esClient, opre, feip);
					System.out.println(isValid);
				}
				case STATEMENT -> {
					System.out.println("Statement @"+opre.getHeight()+"."+opre.getId());
					isValid = publishParser.parseStatement(esClient, opre, feip);
					System.out.println(isValid);
				}
				case GROUP -> {
					System.out.println("Group @"+opre.getHeight()+"."+opre.getId());
					GroupHistory groupHist = organizationParser.makeGroup(opre, feip);
					if (groupHist == null) break;
					isValid = organizationParser.parseGroup(esClient, groupHist);
					if (isValid)
						esClient.index(i -> i.index(IndicesNames.GROUP_HISTORY).id(groupHist.getId()).document(groupHist));
					System.out.println(isValid);
				}
				case TEAM -> {
					System.out.println("Team @"+opre.getHeight()+"."+opre.getId());
					TeamHistory teamHist = organizationParser.makeTeam(opre, feip);
					if (teamHist == null) break;
					isValid = organizationParser.parseTeam(esClient, teamHist);
					if (isValid)
						esClient.index(i -> i.index(IndicesNames.TEAM_HISTORY).id(teamHist.getId()).document(teamHist));
					System.out.println(isValid);
				}
				case BOX -> {
					System.out.println("Box @"+opre.getHeight()+"."+opre.getId());
					BoxHistory boxHist = personalParser.makeBox(opre, feip);
					if (boxHist == null) break;
					isValid = personalParser.parseBox(esClient, boxHist);
					if (isValid)
						esClient.index(i -> i.index(IndicesNames.BOX_HISTORY).id(boxHist.getId()).document(boxHist));
					System.out.println(isValid);
				}
				case PROOF -> {
					System.out.println("Proof @"+opre.getHeight()+"."+opre.getId());
					ProofHistory proofHist = publishParser.makeProof(opre, feip);
					if (proofHist == null) break;
					isValid = publishParser.parseProof(esClient, proofHist);
					if (isValid)
						esClient.index(i -> i.index(IndicesNames.PROOF_HISTORY).id(proofHist.getId()).document(proofHist));
					System.out.println(isValid);
				}
				case TOKEN -> {
					System.out.println("Token @"+opre.getHeight()+"."+opre.getId());
					TokenHistory tokenHist = publishParser.makeToken(opre, feip);
					if (tokenHist == null) break;
					try {
						isValid = publishParser.parseToken(esClient, tokenHist);
					}catch (NumberFormatException ignore) {}
					if (isValid)
						esClient.index(i -> i.index(IndicesNames.TOKEN_HISTORY).id(tokenHist.getId()).document(tokenHist));
					System.out.println(isValid);
				}
				default -> {
				}
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

	private FEIP_NAME checkFeipSn(Feip feip) {

		String sn = feip.getSn();
		if(sn.equals("1"))return FEIP_NAME.PROTOCOL;
		if(sn.equals("2"))return FEIP_NAME.CODE;
		if(sn.equals("3"))return FEIP_NAME.CID;
		if(sn.equals("4"))return FEIP_NAME.NOBODY;
		if(sn.equals("5"))return FEIP_NAME.SERVICE;
		if(sn.equals("6"))return FEIP_NAME.MASTER;
		if(sn.equals("7"))return FEIP_NAME.MAIL;
		if(sn.equals("8"))return FEIP_NAME.STATEMENT;
		if(sn.equals("9"))return FEIP_NAME.HOMEPAGE;
		if(sn.equals("10"))return FEIP_NAME.NOTICE_FEE;
		if(sn.equals("11"))return FEIP_NAME.NID;
		if(sn.equals("12"))return FEIP_NAME.CONTACT;

		if(sn.equals("13"))return FEIP_NAME.BOX;
		if(sn.equals("14"))return FEIP_NAME.PROOF;
		if(sn.equals("15"))return FEIP_NAME.APP;
		if(sn.equals("16"))return FEIP_NAME.REPUTATION;
		if(sn.equals("17"))return FEIP_NAME.SECRET;
		if(sn.equals("18"))return FEIP_NAME.TEAM;
		if(sn.equals("19"))return FEIP_NAME.GROUP;
		if(sn.equals("20"))return FEIP_NAME.TOKEN;


		return null;
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
			case IndicesNames.CID:
				EsUtils.bulkDeleteList(esClient, IndicesNames.CID, (ArrayList<String>) idList);
				TimeUnit.SECONDS.sleep(2);

				ArrayList<CidHist> reparseCidList = getReparseHistList(esClient, IndicesNames.CID_HISTORY,idList,"signer", CidHist.class);

				for(CidHist idHist: reparseCidList) {
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
			case IndicesNames.GROUP:
				EsUtils.bulkDeleteList(esClient, IndicesNames.GROUP, (ArrayList<String>) idList);
				TimeUnit.SECONDS.sleep(2);
				ArrayList<GroupHistory> reparseGroupList = getReparseHistList(esClient, IndicesNames.GROUP_HISTORY,idList,"gid",GroupHistory.class);

				for(GroupHistory idHist: reparseGroupList) {
					new OrganizationParser().parseGroup(esClient, idHist);
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
		if(result.hits().total().value()==0)return null;
		List<Hit<T>> hitList = result.hits().hits();
		ArrayList <T> reparseList = new ArrayList<T>();
		for(Hit<T> hit:hitList) {
			reparseList.add(hit.source());
		}
		return reparseList;
	}
}
