package organize;

import startFEIP.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import constants.OpNames;
import startFEIP.FeipConstants;
import data.fcData.News;
import data.fchData.Freer;
import data.feipData.*;
import utils.EsUtils;
import utils.EsUtils.MgetResult;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;

import com.google.gson.Gson;
import constants.IndicesNames;
import data.fchData.OpReturn;
import startFEIP.StartFEIP;
import utils.StringUtils;

import java.util.*;

import static constants.OpNames.*;
import static constants.Values.CREATED;
import static constants.Values.UPDATED;

public class OrganizationParser {

	private static final Logger log = LoggerFactory.getLogger(OrganizationParser.class);

	public SquareHistory makeSquare(OpReturn opre, Feip feip) {

		Gson gson = new Gson();

		try {
			int ver = Integer.parseInt(feip.getVer());
			if(ver < 4){
				log.info("Ignored old version");
				return null;
			}
		}catch (Exception ignore){}

		SquareOpData squareRaw = new SquareOpData();

		try {
			squareRaw = gson.fromJson(gson.toJsonTree(feip.getData()), SquareOpData.class);
			if(squareRaw==null){
				log.info("Bad square data");
				return null;
			}
		}catch(com.google.gson.JsonSyntaxException e) {
			log.info("Bad square data");
			return null;
		}

		SquareHistory squareHist = new SquareHistory();

		if(squareRaw.getOp()==null){
			log.info("OP is null");
			return null;
		}
		squareHist.setOp(squareRaw.getOp());

		switch(squareRaw.getOp()) {

			case "create":
				if(squareRaw.getName()==null){
					log.info("Name is null");
					return null;
				}
				if(squareRaw.getSquareId()!=null){
					log.info("SquareId is not null");
					return null;
				}
				if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired){
					log.info("CDD is less than required");
					return null;
				}
				squareHist.setId(opre.getId());
				squareHist.setSquareId(opre.getId());
				squareHist.setHeight(opre.getHeight());
				squareHist.setIndex(opre.getTxIndex());
				squareHist.setTime(opre.getTime());
				squareHist.setSigner(opre.getSigner());
				squareHist.setCdd(opre.getCdd());

				squareHist.setName(squareRaw.getName());
				if(squareRaw.getDesc()!=null)squareHist.setDesc(squareRaw.getDesc());
				if(squareRaw.getHome()!=null)squareHist.setHome(squareRaw.getHome());

				break;

			case "update":
				if(squareRaw.getSquareId()==null){
					log.info("SquareId is null");
					return null;
				}
				if(squareRaw.getName()==null && squareRaw.getHome()==null){
					log.info("Name and home are both null");
					return null;
				}
				squareHist.setSquareId(squareRaw.getSquareId());
				squareHist.setId(opre.getId());
				squareHist.setHeight(opre.getHeight());
				squareHist.setIndex(opre.getTxIndex());
				squareHist.setTime(opre.getTime());
				squareHist.setSigner(opre.getSigner());
				squareHist.setCdd(opre.getCdd());

				if(squareRaw.getName()!=null)squareHist.setName(squareRaw.getName());
				if(squareRaw.getDesc()!=null)squareHist.setDesc(squareRaw.getDesc());
				if(squareRaw.getHome()!=null)squareHist.setHome(squareRaw.getHome());

				break;

			case "join":
				if(squareRaw.getSquareId()==null){
					log.info("SquareId is null");
					return null;
				}
				if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired){
					log.info("CDD is less than required");
					return null;
				}
				squareHist.setSquareId(squareRaw.getSquareId());

				squareHist.setId(opre.getId());
				squareHist.setHeight(opre.getHeight());
				squareHist.setIndex(opre.getTxIndex());
				squareHist.setTime(opre.getTime());
				squareHist.setSigner(opre.getSigner());
				squareHist.setCdd(opre.getCdd());
				break;
			case "leave":
				if(squareRaw.getSquareIds()==null || squareRaw.getSquareIds().isEmpty()){
					log.info("SquareIds are null or empty");
					return null;
				}
				squareHist.setSquareIds(squareRaw.getSquareIds());

				squareHist.setId(opre.getId());
				squareHist.setHeight(opre.getHeight());
				squareHist.setIndex(opre.getTxIndex());
				squareHist.setTime(opre.getTime());
				squareHist.setSigner(opre.getSigner());
				break;
			default:
				log.info("Invalid operation");
				return null;
		}
		return squareHist;
	}

	public boolean parseSquare(ElasticsearchClient esClient, SquareHistory squareHist) throws Exception {

		if(squareHist==null || squareHist.getOp()==null){
			log.info("Square history is null or OP is null");
			return false;
		}
		Square square;

		switch(squareHist.getOp()) {
			case CREATE:
				square = EsUtils.getById(esClient, IndicesNames.SQUARE, squareHist.getSquareId(), Square.class);
				if(square==null) {
					square = new Square();
					square.setId(squareHist.getId());
					square.setName(squareHist.getName());
					square.setDesc(squareHist.getDesc());
					if(squareHist.getHome()!=null)square.setHome(squareHist.getHome());

					String[] namers = new String[1];
					String[] activeMembers = new String[1];

					namers[0]=squareHist.getSigner();
					activeMembers[0]=squareHist.getSigner();

					square.setNamers(namers);
					square.setMembers(activeMembers);
					square.setMemberNum((long) activeMembers.length);

					square.setBirthTime(squareHist.getTime());
					square.setBirthHeight(squareHist.getHeight());

					square.setLastTxId(squareHist.getId());
					square.setLastTime(squareHist.getTime());
					square.setLastHeight(squareHist.getHeight());

					if(squareHist.getCdd()==null){
						square.setCddToUpdate(1L);
					} else {
						square.setCddToUpdate(squareHist.getCdd()+1);
						if(square.gettCdd()==null)square.settCdd(squareHist.getCdd());
						else square.settCdd(square.gettCdd()+squareHist.getCdd());
					}

					Square square1=square;
					IndexResponse result = esClient.index(i->i.index(IndicesNames.SQUARE).id(squareHist.getSquareId()).document(square1));
					log.info("{}", result.result());
					if(!CREATED.equals(result.result().jsonValue()) && !UPDATED.equals(result.result().jsonValue())){
						log.info("Failed to create square");
						return false;
					}

					// Create News
					News.createNews(esClient, squareHist.getId(), squareHist.getSigner(), CREATE, Feip.FeipProtocol.SQUARE.getName(),
							squareHist.getId(), squareHist.getName(), squareHist.getDesc(), squareHist.getHeight(), squareHist.getTime());
					return true;
				}else {
					log.info("Square has existed.");
					return false;
				}


			case "join":

				square = EsUtils.getById(esClient, IndicesNames.SQUARE, squareHist.getSquareId(), Square.class);

				if(square==null) {
					log.info("Square is not found");
					return false;
				}
				Set<String>memberSet = new HashSet<String>();
				if(square.getMembers()!=null) Collections.addAll(memberSet, square.getMembers());

				if(memberSet.contains(squareHist.getSigner())) {
					// Joining again changed nothing but still added the op's CDD to the square's total.
					log.info("Signer is already a member of the square");
					return false;
				}
				String [] activeMembers;
				memberSet.add(squareHist.getSigner());
				activeMembers = memberSet.toArray(new String[memberSet.size()]);

				square.setMembers(activeMembers);
				square.setMemberNum((long) activeMembers.length);

				square.setLastTxId(squareHist.getId());
				square.setLastTime(squareHist.getTime());
				square.setLastHeight(squareHist.getHeight());

				if(squareHist.getCdd()!=null) {
					Long tCdd = 0L;
					if(square.gettCdd()!=null)tCdd = square.gettCdd();
					square.settCdd(tCdd + squareHist.getCdd());
				}
				Square square2 = square;

				IndexResponse result2 = esClient.index(i->i.index(IndicesNames.SQUARE).id(squareHist.getSquareId()).document(square2));
				log.info("{}", result2.result());

				return CREATED.equals(result2.result().jsonValue()) || UPDATED.equals(result2.result().jsonValue());

			case "update":

				square = EsUtils.getById(esClient, IndicesNames.SQUARE, squareHist.getSquareId(), Square.class);

				if(square==null) {
					log.info("Square is not found");
					return false;
				}

				if(squareHist.getCdd()==null || squareHist.getCdd() < square.getCddToUpdate()){
					log.info("CDD is less than required");
					return false;
				}
				square.setCddToUpdate(squareHist.getCdd()+1);

				Long tCdd = 0L;
				if(square.gettCdd()!=null) tCdd = square.gettCdd();
				square.settCdd(tCdd + squareHist.getCdd());

				boolean found =false;
				for(String member:square.getMembers()) {
					if(member.equals(squareHist.getSigner())) {
						found=true;
						break;
					}
				}
				if(!found){
					log.info("Signer is not found in square");
					return false;
				}

				if(squareHist.getName()!=null)square.setName(squareHist.getName());
				if(squareHist.getDesc()!=null)square.setDesc(squareHist.getDesc());
				if(squareHist.getHome()!=null)square.setHome(squareHist.getHome());

				Set<String> namerSet = new HashSet<String>();
				for(String namer: square.getNamers()) {
					namerSet.add(namer);
				}
				namerSet.add(squareHist.getSigner());
				String[] namers = namerSet.toArray(new String[namerSet.size()]);

				square.setNamers(namers);

				square.setLastTxId(squareHist.getId());
				square.setLastTime(squareHist.getTime());
				square.setLastHeight(squareHist.getHeight());

				Square square3 = square;

				IndexResponse result1 = esClient.index(i->i.index(IndicesNames.SQUARE).id(squareHist.getSquareId()).document(square3));
				log.info("{}", result1.result());
				return CREATED.equals(result1.result().jsonValue()) || UPDATED.equals(result1.result().jsonValue());

			case "leave":

				if(squareHist.getSquareIds()==null || squareHist.getSquareIds().isEmpty()){
					log.info("SquareIds are null or empty");
					return false;
				}

				EsUtils.MgetResult<Square> result = EsUtils.getMultiByIdList(esClient, IndicesNames.SQUARE, squareHist.getSquareIds(), Square.class);
				if(result.getResultList() == null || result.getResultList().isEmpty()){
					log.info("Square list is empty");
					return false;
				}

				BulkRequest.Builder br = new BulkRequest.Builder();
				for(Square square1:result.getResultList()){

					String [] activeMembers1;

					Set<String>memberSet1 = new HashSet<String>();

					boolean found1 =false;
					for(String member:square1.getMembers()) {
						if(!member.equals(squareHist.getSigner())) {
							memberSet1.add(member);
						}else found1=true;
					}

					if(!found1){
						log.info("Signer is not found in square");
						return false;
					}

					activeMembers1 = memberSet1.toArray(new String[0]);
					square1.setMembers(activeMembers1);
					square1.setMemberNum((long) activeMembers1.length);

					if(activeMembers1.length==0){
						// The last member left: the square goes. This used to return straight away,
						// so any further squares in the op were never left; and it deleted the
						// square's histories, which a rollback needs to rebuild the square.
						br.operations(op -> op.delete(d -> d.index(IndicesNames.SQUARE).id(square1.getId())));
						log.info("Square {} has no members left and is deleted", square1.getId());
						continue;
					}

					square1.setLastTxId(squareHist.getId());
					square1.setLastTime(squareHist.getTime());
					square1.setLastHeight(squareHist.getHeight());

					if(squareHist.getCdd()!=null) {
						if(square1.gettCdd()==null)square1.settCdd(squareHist.getCdd());
						else square1.settCdd(square1.gettCdd() + squareHist.getCdd());
					}

					br.operations(op -> op
							.index(idx -> idx
									.index(IndicesNames.SQUARE)
									.id(square1.getId())
									.document(square1)
							)
					);
				}

				BulkResponse result3 = esClient.bulk(br.build());
				if(result3.errors()){
					throw new java.io.IOException("Failed to bulk update squares on leave");
				} else {
					log.info("Done");
					return true;
				}

		}
		return false;
	}

	public TeamHistory makeTeam(OpReturn opre, Feip feip)  {

        if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired) return null;

		Gson gson = new Gson();

		TeamOpData teamRaw = new TeamOpData();

		try {
			teamRaw = gson.fromJson(gson.toJsonTree(feip.getData()), TeamOpData.class);
			if(teamRaw==null){
				log.info("Team data is null");
				return null;
			}
		}catch(com.google.gson.JsonSyntaxException e) {
			log.info("Bad team data");
			return null;
		}

		TeamHistory teamHist = new TeamHistory();

		if(teamRaw.getOp()==null){
			log.info("OP is null");
			return null;
		}
		teamHist.setOp(teamRaw.getOp());

		switch(teamRaw.getOp()) {

			case CREATE:
				if(teamRaw.getStdName()==null){
					log.info("StdName is null");
					return null;
				}
				if(teamRaw.getTid()!=null){
					log.info("TID is not null");
					return null;
				}
				if(teamRaw.getConsensusId()==null){
					log.info("ConsensusId is null");
					return null;
				}
				if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired){
					log.info("CDD is less than required");
					return null;
				}
				teamHist.setId(opre.getId());
				teamHist.setTid(opre.getId());
				teamHist.setHeight(opre.getHeight());
				teamHist.setIndex(opre.getTxIndex());
				teamHist.setTime(opre.getTime());
				teamHist.setSigner(opre.getSigner());

				teamHist.setStdName(teamRaw.getStdName());
				if(teamRaw.getLocalNames()!=null)teamHist.setLocalNames(teamRaw.getLocalNames());
				if(teamRaw.getWaiters()!=null)teamHist.setWaiters(teamRaw.getWaiters());
				if(teamRaw.getAccounts()!=null)teamHist.setAccounts(teamRaw.getAccounts());
				if(teamRaw.getDesc()!=null)teamHist.setDesc(teamRaw.getDesc());
				if(teamRaw.getConsensusId()!=null)teamHist.setConsensusId(teamRaw.getConsensusId());
				if(teamRaw.getHome()!=null)teamHist.setHome(teamRaw.getHome());

				break;

			case "disband", "leave":
				if(teamRaw.getTids()==null){
					log.info("TIDs are null");
					return null;
				}
				teamHist.setTids(teamRaw.getTids());

				teamHist.setId(opre.getId());
				teamHist.setHeight(opre.getHeight());
				teamHist.setIndex(opre.getTxIndex());
				teamHist.setTime(opre.getTime());
				teamHist.setSigner(opre.getSigner());
				break;
			case "transfer":
				if(teamRaw.getTid()==null){
					log.info("TID is null");
					return null;
				}
				if(teamRaw.getTransferee() ==null){
					log.info("Transferee is null");
					return null;
				}
				if(teamRaw.getConfirm()==null || !teamRaw.getConfirm().equals(FeipConstants.CONFIRM_TRANSFER_TEAM)){
					log.info("Confirm absents or is not '" + FeipConstants.CONFIRM_TRANSFER_TEAM + "'");
					return null;
				}
				teamHist.setTid(teamRaw.getTid());
				teamHist.setTransferee(teamRaw.getTransferee());

				teamHist.setId(opre.getId());
				teamHist.setHeight(opre.getHeight());
				teamHist.setIndex(opre.getTxIndex());
				teamHist.setTime(opre.getTime());
				teamHist.setSigner(opre.getSigner());
				break;
			case "take over":
				if(teamRaw.getTid()==null){
					log.info("TID is null");
					return null;
				}
				if(teamRaw.getConfirm()==null || !teamRaw.getConfirm().equals(FeipConstants.CONFIRM_TAKE_OVER_TEAM)){
					log.info("Confirm absents or is not '" + FeipConstants.CONFIRM_TAKE_OVER_TEAM + "'");
					return null;
				}
				teamHist.setTid(teamRaw.getTid());
				if(teamRaw.getConsensusId()!=null)teamHist.setConsensusId(teamRaw.getConsensusId());

				teamHist.setId(opre.getId());
				teamHist.setHeight(opre.getHeight());
				teamHist.setIndex(opre.getTxIndex());
				teamHist.setTime(opre.getTime());
				teamHist.setSigner(opre.getSigner());
				break;

			case "update":
				if(teamRaw.getTid()==null){
					log.info("TID is null");
					return null;
				}
				if(teamRaw.getStdName()==null){
					log.info("StdName is null");
					return null;
				}
				if(teamRaw.getConsensusId()==null){
					log.info("ConsensusId is null");
					return null;
				}

				teamHist.setTid(teamRaw.getTid());
				teamHist.setId(opre.getId());
				teamHist.setHeight(opre.getHeight());
				teamHist.setIndex(opre.getTxIndex());
				teamHist.setTime(opre.getTime());
				teamHist.setSigner(opre.getSigner());

				teamHist.setStdName(teamRaw.getStdName());
				if(teamRaw.getLocalNames()!=null)teamHist.setLocalNames(teamRaw.getLocalNames());
				if(teamRaw.getWaiters()!=null)teamHist.setWaiters(teamRaw.getWaiters());
				if(teamRaw.getAccounts()!=null)teamHist.setAccounts(teamRaw.getAccounts());
				if(teamRaw.getDesc()!=null)teamHist.setDesc(teamRaw.getDesc());
				if(teamRaw.getConsensusId()!=null)teamHist.setConsensusId(teamRaw.getConsensusId());
				if(teamRaw.getHome()!=null)teamHist.setHome(teamRaw.getHome());

				break;
			case "agree consensus":
				if(teamRaw.getTid()==null){
					log.info("TID is null");
					return null;
				}
				if(teamRaw.getConfirm()==null || !teamRaw.getConfirm().equals(FeipConstants.CONFIRM_AGREE_CONSENSUS)){
					log.info("Confirm absents or is not '" + FeipConstants.CONFIRM_AGREE_CONSENSUS + "'");
					return null;
				}
				// The consensus being agreed to must be named: parseTeam compares it for equality
				// with the team's current consensusId.
				if(teamRaw.getConsensusId()==null){
					log.info("ConsensusId is null");
					return null;
				}
				teamHist.setTid(teamRaw.getTid());
				teamHist.setConsensusId(teamRaw.getConsensusId());

				teamHist.setId(opre.getId());
				teamHist.setHeight(opre.getHeight());
				teamHist.setIndex(opre.getTxIndex());
				teamHist.setTime(opre.getTime());
				teamHist.setSigner(opre.getSigner());
				break;
			case "invite", "withdraw invitation", "dismiss", "appoint", "cancel appointment":
				if(teamRaw.getTid()==null){
					log.info("TID is null");
					return null;
				}
				if(teamRaw.getList()==null){
					log.info("List is null");
					return null;
				}
				if(teamRaw.getList().length==0){
					log.info("List is empty");
					return null;
				}
				teamHist.setTid(teamRaw.getTid());
				teamHist.setList(teamRaw.getList());

				teamHist.setId(opre.getId());
				teamHist.setHeight(opre.getHeight());
				teamHist.setIndex(opre.getTxIndex());
				teamHist.setTime(opre.getTime());
				teamHist.setSigner(opre.getSigner());
				break;

			case "join":
				if(teamRaw.getTid()==null){
					log.info("TID is null");
					return null;
				}
				if(teamRaw.getConfirm()==null || !teamRaw.getConfirm().equals(FeipConstants.CONFIRM_JOIN_TEAM)){
					log.info("Confirm absents or is not '" + FeipConstants.CONFIRM_JOIN_TEAM + "'");
					return null;
				}
				// The consensus being joined under must be named: parseTeam compares it for
				// equality with the team's current consensusId.
				if(teamRaw.getConsensusId()==null){
					log.info("ConsensusId is null");
					return null;
				}
				teamHist.setTid(teamRaw.getTid());
				teamHist.setConsensusId(teamRaw.getConsensusId());

				teamHist.setId(opre.getId());
				teamHist.setHeight(opre.getHeight());
				teamHist.setIndex(opre.getTxIndex());
				teamHist.setTime(opre.getTime());
				teamHist.setSigner(opre.getSigner());
				break;

			case "rate":
				if(teamRaw.getTid()==null){
					log.info("TID is null");
					return null;
				}
				if(teamRaw.getRate()==null || teamRaw.getRate()<0 ||teamRaw.getRate()>FeipConstants.MAX_RATE){
					log.info("Rate is less than 0 or greater than 5");
					return null;
				}
				if (opre.getCdd()==null || opre.getCdd() < StartFEIP.CddRequired){
					log.info("CDD is null or less than required");
					return null;
				}
				teamHist.setTid(teamRaw.getTid());
				teamHist.setRate(teamRaw.getRate());
				teamHist.setCause(teamRaw.getCause());
				teamHist.setCdd(opre.getCdd());

				teamHist.setId(opre.getId());
				teamHist.setHeight(opre.getHeight());
				teamHist.setIndex(opre.getTxIndex());
				teamHist.setTime(opre.getTime());
				teamHist.setSigner(opre.getSigner());
				break;
			default:
				log.info("Invalid operation");
				return null;
		}
		return teamHist;
	}

	public boolean parseTeam(ElasticsearchClient esClient, TeamHistory teamHist) throws Exception {
		if(teamHist==null || teamHist.getOp()==null){
			log.info("Team history is null or OP is null");
			return false;
		}
		Team team;
		boolean found = false;
		switch(teamHist.getOp()) {
			case CREATE:
				team = EsUtils.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);
				if(team==null) {
					team = new Team();
					team.setId(teamHist.getId());
					team.setOwner(teamHist.getSigner());
					team.setStdName(teamHist.getStdName());
					if(teamHist.getLocalNames()!=null)team.setLocalNames(teamHist.getLocalNames());
					if(teamHist.getWaiters()!=null)team.setWaiters(teamHist.getWaiters());
					if(teamHist.getAccounts()!=null)team.setAccounts(teamHist.getAccounts());
					if(teamHist.getConsensusId() !=null)team.setConsensusId(teamHist.getConsensusId());
					if(teamHist.getDesc() !=null)team.setDesc(teamHist.getDesc());
					if(teamHist.getHome() !=null)team.setHome(teamHist.getHome());

					String[] activeMembers = new String[1];
					activeMembers[0]=teamHist.getSigner();
					team.setMembers(activeMembers);
					team.setMemberNum((long) activeMembers.length);

					String[] magagers = new String[1];
					magagers[0]=teamHist.getSigner();
					team.setManagers(magagers);

					team.setBirthTime(teamHist.getTime());
					team.setBirthHeight(teamHist.getHeight());

					team.setLastTxId(teamHist.getId());
					team.setLastTime(teamHist.getTime());
					team.setLastHeight(teamHist.getHeight());

					team.setActive(true);

					Team team1=team;
					IndexResponse result = esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team1));
					log.info("{}", result.result());
					if(!CREATED.equals(result.result().jsonValue()) && !UPDATED.equals(result.result().jsonValue())){
						log.info("Failed to create team");
						return false;
					}

					// Create News
					News.createNews(esClient, teamHist.getId(), teamHist.getSigner(), CREATE, Feip.FeipProtocol.TEAM.getName(),
							teamHist.getId(), teamHist.getStdName(), teamHist.getDesc(), teamHist.getHeight(), teamHist.getTime());
					return true;
				}else {
					log.info("Team has existed");
					return false;
				}

			case DISBAND:
				if(teamHist.getTids()==null||teamHist.getTids().isEmpty()) {
					log.info("TIDs are null or empty");
					return false;
				}

				EsUtils.MgetResult<Team> result = EsUtils.getMultiByIdList(esClient, IndicesNames.TEAM, teamHist.getTids(), Team.class);
				if(result==null||result.getResultList()==null||result.getResultList().isEmpty()) {
					log.info("Team list is empty");
					return false;
				}

				if(result.getMissList()!=null && !result.getMissList().isEmpty()) {
					log.info("Teams not found: "+result.getMissList());
				}

				BulkRequest.Builder br = new BulkRequest.Builder();
				int disbanded = 0;
				for(Team team1:result.getResultList()) {
					if(! team1.getOwner().equals(teamHist.getSigner())) {
						// A team the signer does not own rejects the op before anything is written.
						log.info("Signer does not own team {}", team1.getId());
						return false;
					}
					if(Boolean.FALSE.equals(team1.isActive())) {
						continue;
					}
					disbanded++;
					team1.setLastTxId(teamHist.getId());
					team1.setLastTime(teamHist.getTime());
					team1.setLastHeight(teamHist.getHeight());
					team1.setActive(false);

					Team team2 = team1;
					br.operations(op -> op
							.index(idx -> idx
									.index(IndicesNames.TEAM)
									.id(team2.getId())
									.document(team2)
							)
					);
				}
				if(disbanded==0) {
					// An empty bulk request is rejected by Elasticsearch; and nothing was disbanded.
					log.info("No active team to disband");
					return false;
				}
				BulkResponse result4 = esClient.bulk(br.build());
				if(result4.errors()){
					throw new java.io.IOException("Failed to bulk disband team");
				}
				log.info("Done");

				// Create News
				News.createNews(esClient, teamHist.getId(), teamHist.getSigner(), OpNames.DISBAND, Feip.FeipProtocol.TEAM.getName(),
						null, null, StringUtils.listToString(teamHist.getTids()), teamHist.getHeight(), teamHist.getTime());

				return true;

			case TRANSFER:

				team = EsUtils.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					log.info("Team is not found");
					return false;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					log.info("Team is not active");
					return false;
				}

				if(!Permission.isOwnerOrMaster(esClient, team.getOwner(), teamHist.getSigner())) {
					log.info("Signer is not the owner or the owner's master");
					return false;
				}

				if(teamHist.getTransferee().equals(team.getOwner())) {
					team.setTransferee(null);
				}else team.setTransferee(teamHist.getTransferee());

				team.setLastTxId(teamHist.getId());
				team.setLastTime(teamHist.getTime());
				team.setLastHeight(teamHist.getHeight());

				Team team3 = team;

				IndexResponse result3 = esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team3));
				log.info("{}", result3.result());
				if(!CREATED.equals(result3.result().jsonValue()) && !UPDATED.equals(result3.result().jsonValue())){
					log.info("Failed to transfer team");
					return false;
				}
				// Create News
				News.createNews(esClient, teamHist.getId(), teamHist.getSigner(), OpNames.TRANSFER, Feip.FeipProtocol.TEAM.getName(),
						teamHist.getTid(), teamHist.getTransferee(), null, teamHist.getHeight(), teamHist.getTime());

				return true;

			case TAKE_OVER:

				team = EsUtils.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					log.info("Team is not found");
					return false;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					log.info("Team is not active");
					return false;
				}

				if(team.getTransferee()==null) {
					log.info("Transferee is null");
					return false;
				}

				if(teamHist.getConsensusId()!=null) {
					if(!teamHist.getConsensusId().equals(team.getConsensusId())){
						log.info("ConsensusId is not the same");
						return false;
					}
				}

				String taker = teamHist.getSigner();

				if(team.getTransferee().equals(taker)) {

					Set<String> activeMemberSet = new HashSet<String>();
					Collections.addAll(activeMemberSet, team.getMembers());
					activeMemberSet.add(taker);
					String[] activeMembers = activeMemberSet.toArray(new String[0]);
					team.setMembers(activeMembers);
					team.setMemberNum((long) activeMembers.length);

					team.setManagers(new String[]{taker});

					team.setTransferee(null);
					team.setOwner(taker);

					Team team4 = team;

					IndexResponse result5 = esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team4));
					log.info("{}", result5.result());
					if(!CREATED.equals(result5.result().jsonValue()) && !UPDATED.equals(result5.result().jsonValue())){
						log.info("Failed to take over team");
						return false;
					}
					// Create News
					News.createNews(esClient, teamHist.getId(), teamHist.getSigner(), OpNames.TAKE_OVER, Feip.FeipProtocol.TEAM.getName(),
							teamHist.getTid(), null, null, teamHist.getHeight(), teamHist.getTime());

					return true;
				}
				log.info("Taker is not the transferee.");
				return false;
			case UPDATE:
				team = EsUtils.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					log.info("Team is not found");
					return false;
				}

				if(! team.getOwner().equals(teamHist.getSigner())) {
					log.info("Signer is not the owner");
					return false;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					log.info("Team is not active");
					return false;
				}

				team.setStdName(teamHist.getStdName());
				if(teamHist.getLocalNames() !=null) team.setLocalNames(teamHist.getLocalNames());
				if(teamHist.getWaiters()!=null)team.setWaiters(teamHist.getWaiters());
				if(teamHist.getAccounts()!=null)team.setAccounts(teamHist.getAccounts());
				if(teamHist.getDesc() !=null) team.setDesc(teamHist.getDesc());
				if(teamHist.getHome() !=null) team.setHome(teamHist.getHome());

				if(teamHist.getConsensusId() !=null) {
					if(team.getConsensusId()!=null) {
						if(! team.getConsensusId().equals(teamHist.getConsensusId())) {
							team.setConsensusId(teamHist.getConsensusId());

							Set<String>memberSet = new HashSet<String>();
							for(String m:team.getMembers()) {
								if(m.equals(team.getOwner()))continue;
								memberSet.add(m);
							}
							team.setNotAgreeMembers(memberSet.toArray(new String[memberSet.size()]));
						}
					}else {
						team.setConsensusId(teamHist.getConsensusId());

						Set<String>memberSet = new HashSet<String>();
						for(String m:team.getMembers()) {
							if(m.equals(team.getOwner()))continue;
							memberSet.add(m);
						}
						team.setNotAgreeMembers(memberSet.toArray(new String[memberSet.size()]));
					}
				}

				team.setLastTxId(teamHist.getId());
				team.setLastTime(teamHist.getTime());
				team.setLastHeight(teamHist.getHeight());

				Team team5 = team;

				IndexResponse result5 = esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team5));
				log.info("{}", result5.result());
				return CREATED.equals(result5.result().jsonValue()) || UPDATED.equals(result5.result().jsonValue());

			case "agree consensus":

				team = EsUtils.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					log.info("Team is not found");
					return false;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					log.info("Team is not active");
					return false;
				}

				// makeTeam requires the id now, but a history stored before it did can still be
				// replayed by a rollback.
				if(teamHist.getConsensusId()==null || !teamHist.getConsensusId().equals(team.getConsensusId())){
					log.info("ConsensusId is not the same");
					return false;
				}

				found = false;
				String agreer = teamHist.getSigner();

				Set<String>notAgreeSet = new HashSet<String>();
				if(team.getNotAgreeMembers()!=null) {
					for(String member:team.getNotAgreeMembers()) {
						if(member.equals(agreer)) {
							found = true;
						}else{
							notAgreeSet.add(member);
						}
					}
				}
				if(found) {
					String[] notAgreeMembers = notAgreeSet.toArray(new String[notAgreeSet.size()]);

					if(notAgreeMembers.length==0) {
						team.setNotAgreeMembers(null);
					}else team.setNotAgreeMembers(notAgreeMembers);

					team.setLastTxId(teamHist.getId());
					team.setLastTime(teamHist.getTime());
					team.setLastHeight(teamHist.getHeight());

					Team team6 = team;

					IndexResponse result6 = esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team6));
					log.info("{}", result6.result());
					return CREATED.equals(result6.result().jsonValue()) || UPDATED.equals(result6.result().jsonValue());
				}else {
					log.info("Signer is not in the not agree members");
					return false;
				}

			case "invite":

				team = EsUtils.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					log.info("Team is not found");
					return false;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					log.info("Team is not active");
					return false;
				}

				if(team.getManagers()!=null) {
					for(String admin:team.getManagers()) {
						if(admin.equals(teamHist.getSigner())) {

							Set<String> inviteeSet = new HashSet<String>();
							if(team.getInvitees()!=null) {
								Collections.addAll(inviteeSet, team.getInvitees());
							}

							Set<String>  activeMemberSet = new HashSet<>(List.of(team.getMembers()));
							for(String invitee:teamHist.getList()) {
								if(invitee.equals(team.getOwner()))continue;
								if(activeMemberSet.contains(invitee))continue;
								inviteeSet.add(invitee);
							}

							String[] invitees = inviteeSet.toArray(new String[inviteeSet.size()]);
							team.setInvitees(invitees);
							team.setLastTxId(teamHist.getId());
							team.setLastTime(teamHist.getTime());
							team.setLastHeight(teamHist.getHeight());

							Team team7 = team;

							IndexResponse result7 = esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team7));
							log.info("{}", result7.result());
							return CREATED.equals(result7.result().jsonValue()) || UPDATED.equals(result7.result().jsonValue());
						}
					}
				}
				log.info("No manager.");
				return false;
			case "withdraw invitation":

				team = EsUtils.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					log.info("Team is not found");
					return false;
				}

				if(Boolean.FALSE.equals(team.isActive()) ){
					log.info("Team is not active");
					return false;
				}

				// This branch used to end without a return, so a signer who is not a manager fell
				// through into "join" and threw on the op's null consensusId.
				if(!applyWithdrawInvitation(team, teamHist.getSigner(), teamHist.getList())) {
					return false;
				}
				team.setLastTxId(teamHist.getId());
				team.setLastTime(teamHist.getTime());
				team.setLastHeight(teamHist.getHeight());

				Team withdrawn = team;

				IndexResponse result8 = esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(withdrawn));
				log.info("{}", result8.result());
				return CREATED.equals(result8.result().jsonValue()) || UPDATED.equals(result8.result().jsonValue());

			case "join":

				team = EsUtils.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					log.info("Team is not found");
					return false;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					log.info("Team is not active");
					return false;
				}

				if(team.getConsensusId()==null) {
					log.info("ConsensusId is null");
					return false;
				}

				if(teamHist.getConsensusId()==null || !teamHist.getConsensusId().equals(team.getConsensusId())){
					log.info("ConsensusId is not the same");
					return false;
				}
				if(team.getInvitees()!=null) {
					for(String invitee:team.getInvitees()) {
						if(invitee.equals(teamHist.getSigner())) {

							Set<String> activeMemberSet = new HashSet<String>();
							Collections.addAll(activeMemberSet, team.getMembers());
							activeMemberSet.add(teamHist.getSigner());
							String[] activeMembers = activeMemberSet.toArray(new String[0]);

							Set<String>leftMemberSet = new HashSet<String>();

							if(team.getExMembers()!=null) {
								for(String leftMember:team.getExMembers()) {
									if(!leftMember.equals(teamHist.getSigner())) {
										leftMemberSet.add(leftMember);
									}
								}
								String[] leftMembers = leftMemberSet.toArray(new String[0]);
								team.setExMembers(leftMembers);
							}

							Set<String> inviteeSet = new HashSet<String>();
							for(String invite: team.getInvitees()) {
								if(!invite.equals(teamHist.getSigner())) inviteeSet.add(invite);
							}

							if(inviteeSet.size()==0) {
								team.setInvitees(null);
							}else {
								String[] invitees = inviteeSet.toArray(new String[0]);
								team.setInvitees(invitees);
							}
							team.setMembers(activeMembers);
							team.setMemberNum((long) activeMembers.length);
							team.setLastTxId(teamHist.getId());
							team.setLastTime(teamHist.getTime());
							team.setLastHeight(teamHist.getHeight());

							Team team7 = team;

							IndexResponse result9 = esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team7));
							log.info("{}", result9.result());
							return CREATED.equals(result9.result().jsonValue()) || UPDATED.equals(result9.result().jsonValue());
						}
					}
				}
				log.info("Signer is not an invitee of the team");
				return false;

			case "leave":
				if(teamHist.getTids()==null||teamHist.getTids().isEmpty()) {
					log.info("TIDs are null or empty");
					return false;
				}
				MgetResult<Team> result1 = EsUtils.getMultiByIdList(esClient,IndicesNames.TEAM, teamHist.getTids(), Team.class);
				if(result1.getResultList() == null || result1.getResultList().isEmpty()) {
					log.info("Team list is empty");
					return false;
				}

				if(result1.getMissList()!=null&&!result1.getMissList().isEmpty()) {
					log.info("Teams not found: "+result1.getMissList());
				}

				// The bulk used to run inside the loop, after the first active team the signer did not
				// own: only that team was ever left, and when the signer was not a member of it the
				// bulk had no operations and threw. A leave naming no team it could apply to ran off
				// the end of this case into "dismiss".
				BulkRequest.Builder br1 = new BulkRequest.Builder();
				int left = 0;
				for(Team team1 : result1.getResultList()) {
					if(!applyLeave(team1, teamHist.getSigner())) continue;
					team1.setLastTxId(teamHist.getId());
					team1.setLastTime(teamHist.getTime());
					team1.setLastHeight(teamHist.getHeight());
					left++;
					br1.operations(op -> op
							.index(idx -> idx
									.index(IndicesNames.TEAM)
									.id(team1.getId())
									.document(team1)
							)
					);
				}
				if(left==0) {
					log.info("Signer is not a non-owner member of any active team listed");
					return false;
				}
				BulkResponse result6 = esClient.bulk(br1.build());
				if(result6.errors()){
					throw new java.io.IOException("Failed to bulk update teams on leave");
				}
				log.info("Done");
				return true;

			case "dismiss":
				if(teamHist.getTid()==null) {
					log.info("TID is null");
					return false;
				}

				team = EsUtils.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					log.info("Team is not found");
					return false;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					log.info("Team is not active");
					return false;
				}

				// This branch used to iterate a null managers list, and to end without a return, so a
				// signer who is not a manager fell through into "appoint".
				if(!applyDismiss(team, teamHist.getSigner(), teamHist.getList())) {
					return false;
				}
				team.setLastTxId(teamHist.getId());
				team.setLastTime(teamHist.getTime());
				team.setLastHeight(teamHist.getHeight());

				Team dismissed = team;

				IndexResponse result9 = esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(dismissed));
				log.info("{}", result9.result());
				return CREATED.equals(result9.result().jsonValue()) || UPDATED.equals(result9.result().jsonValue());

			case "appoint":

				team = EsUtils.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					log.info("Team is not found");
					return false;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					log.info("Team is not active");
					return false;
				}

				if(! team.getOwner().equals(teamHist.getSigner())) {
					log.info("Signer is not the owner");
					return false;
				}


				Set<String> activeMemberSet1 = new HashSet<String>();
				for(String member:team.getMembers()) {
					activeMemberSet1.add(member);
				}

				Set<String> magagerSet = new HashSet<String>();
				if(team.getManagers()!=null)
					for(String magager:team.getManagers()) {
						magagerSet.add(magager);
					}

				for(String member:teamHist.getList()) {
					if(member.equals(team.getOwner()))continue;
					if(activeMemberSet1.contains(member) ) {
						magagerSet.add(member);
					}
				}

				String[] managers = magagerSet.toArray(new String[0]);

				team.setManagers(managers);
				team.setLastTxId(teamHist.getId());
				team.setLastTime(teamHist.getTime());
				team.setLastHeight(teamHist.getHeight());

				Team team7 = team;

				IndexResponse result10 = esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team7));
				log.info("{}", result10.result());
				return CREATED.equals(result10.result().jsonValue()) || UPDATED.equals(result10.result().jsonValue());

			case "cancel appointment":
				team = EsUtils.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					log.info("Team is not found");
					return false;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					log.info("Team is not active");
					return false;
				}

				if(! team.getOwner().equals(teamHist.getSigner())) {
					log.info("Signer is not the owner");
					return false;
				}

				Set<String> activeMemberSet2 = new HashSet<String>();
				for(String member:team.getMembers()) {
					activeMemberSet2.add(member);
				}

				Set<String> magagerSet1 = new HashSet<String>();
				if(team.getManagers()!=null)
					for(String magager:team.getManagers()) {
						magagerSet1.add(magager);
					}

				for(String member:teamHist.getList()) {
					if(member.equals(team.getOwner()))continue;
					magagerSet1.remove(member);
				}

				String[] magagers1 = magagerSet1.toArray(new String[magagerSet1.size()]);

				team.setManagers(magagers1);
				team.setLastTxId(teamHist.getId());
				team.setLastTime(teamHist.getTime());
				team.setLastHeight(teamHist.getHeight());

				Team team8 = team;

				IndexResponse result11 = esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team8));
				log.info("{}", result11.result());
				return CREATED.equals(result11.result().jsonValue()) || UPDATED.equals(result11.result().jsonValue());

			case "rate":
				team = EsUtils.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					log.info("Team is not found");
					return false;
				}

				if(team.getOwner().equals(teamHist.getSigner())) {
					log.info("Signer is the owner");
					return false;
				}

				if(teamHist.getCdd()==null || teamHist.getRate()==null) {
					log.info("CDD or rate is null");
					return false;
				}
				// The else used to bind to the inner `if (tCdd != null)`, and nothing initialises
				// tCdd, so the first rating of every team was rejected as "CDD is null" and no team
				// could ever be rated.
				if(team.gettCdd()==null || team.gettRate()==null) {
					team.settRate(Float.valueOf(teamHist.getRate()));
					team.settCdd(teamHist.getCdd());
				} else if (team.gettCdd() + teamHist.getCdd() == 0) {
					team.settRate(0f);
					team.settCdd(0L);
				} else {
					team.settRate(
							(team.gettRate() * team.gettCdd() + teamHist.getRate() * teamHist.getCdd())
									/ (team.gettCdd() + teamHist.getCdd())
					);
					team.settCdd(team.gettCdd() + teamHist.getCdd());
				}
				team.setLastTxId(teamHist.getId());
				team.setLastTime(teamHist.getTime());
				team.setLastHeight(teamHist.getHeight());

				Team team9 = team;

				IndexResponse result12 = esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team9));
				log.info("{}", result12.result());
				return CREATED.equals(result12.result().jsonValue()) || UPDATED.equals(result12.result().jsonValue());
			default:
				log.info("Invalid operation");
				return false;
		}
	}

	// ---- Team membership rules, kept apart from Elasticsearch so they can be tested ----

	/**
	 * Remove {@code list} from the team's invitees. Only a manager may, and only while there are
	 * invitees; otherwise the team is left untouched and false is returned.
	 */
	static boolean applyWithdrawInvitation(Team team, String signer, String[] list) {
		if(team.getManagers()==null || !Arrays.asList(team.getManagers()).contains(signer)) {
			log.info("Signer is not a manager");
			return false;
		}
		if(team.getInvitees()==null || list==null) {
			log.info("No invitation to withdraw");
			return false;
		}
		Set<String> inviteeSet = new HashSet<String>(Arrays.asList(team.getInvitees()));
		for(String invitee : list) {
			inviteeSet.remove(invitee);
		}
		team.setInvitees(inviteeSet.toArray(new String[0]));
		return true;
	}

	/**
	 * Move {@code list} from members to exMembers, dropping any manager role. Only a manager may;
	 * the owner and non-members named are skipped. Returns false, with the team untouched, when
	 * the signer is not a manager.
	 */
	static boolean applyDismiss(Team team, String signer, String[] list) {
		if(team.getManagers()==null || !Arrays.asList(team.getManagers()).contains(signer)) {
			log.info("Signer is not a manager");
			return false;
		}
		Set<String> activeMemberSet = new HashSet<String>();
		if(team.getMembers()!=null) Collections.addAll(activeMemberSet, team.getMembers());

		Set<String> exMemberSet = new HashSet<String>();
		if(team.getExMembers()!=null) Collections.addAll(exMemberSet, team.getExMembers());

		Set<String> managerSet = new HashSet<String>();
		Collections.addAll(managerSet, team.getManagers());

		if(list!=null) {
			for(String dismissedPerson : list) {
				if(dismissedPerson.equals(team.getOwner())) continue;
				if(!activeMemberSet.contains(dismissedPerson)) continue;
				exMemberSet.add(dismissedPerson);
				managerSet.remove(dismissedPerson);
				activeMemberSet.remove(dismissedPerson);
			}
		}

		String[] activeMembers = activeMemberSet.toArray(new String[0]);
		team.setMembers(activeMembers);
		team.setMemberNum((long) activeMembers.length);
		team.setExMembers(exMemberSet.isEmpty() ? null : exMemberSet.toArray(new String[0]));
		team.setManagers(managerSet.isEmpty() ? null : managerSet.toArray(new String[0]));
		return true;
	}

	/**
	 * Take {@code signer} out of one team: out of members and managers, into exMembers. Returns
	 * false, with the team untouched, when the team is inactive, the signer owns it (an owner
	 * transfers or disbands instead), or the signer is not a member.
	 */
	static boolean applyLeave(Team team, String signer) {
		if(Boolean.FALSE.equals(team.isActive())) return false;
		if(signer==null || signer.equals(team.getOwner())) return false;
		if(team.getMembers()==null || !Arrays.asList(team.getMembers()).contains(signer)) return false;

		Set<String> activeMemberSet = new HashSet<String>(Arrays.asList(team.getMembers()));
		activeMemberSet.remove(signer);
		String[] activeMembers = activeMemberSet.toArray(new String[0]);
		team.setMembers(activeMembers);
		team.setMemberNum((long) activeMembers.length);

		Set<String> exMemberSet = new HashSet<String>();
		if(team.getExMembers()!=null) Collections.addAll(exMemberSet, team.getExMembers());
		exMemberSet.add(signer);
		team.setExMembers(exMemberSet.toArray(new String[0]));

		if(team.getManagers()!=null) {
			Set<String> managerSet = new HashSet<String>(Arrays.asList(team.getManagers()));
			managerSet.remove(signer);
			team.setManagers(managerSet.toArray(new String[0]));
		}
		return true;
	}

}
