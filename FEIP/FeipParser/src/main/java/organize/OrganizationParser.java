package organize;

import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import constants.OpNames;
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

	public GroupHistory makeGroup(OpReturn opre, Feip feip) {

		Gson gson = new Gson();

		GroupOpData groupRaw = new GroupOpData();

		try {
			groupRaw = gson.fromJson(gson.toJson(feip.getData()), GroupOpData.class);
			if(groupRaw==null){
				System.out.println("Bad group data");
				return null;
			}
		}catch(com.google.gson.JsonSyntaxException e) {
			System.out.println("Bad group data");
			return null;
		}

		GroupHistory groupHist = new GroupHistory();

		if(groupRaw.getOp()==null){
			System.out.println("OP is null");
			return null;
		}
		groupHist.setOp(groupRaw.getOp());

		switch(groupRaw.getOp()) {

			case "create":
				if(groupRaw.getName()==null){
					System.out.println("Name is null");
					return null;
				}
				if(groupRaw.getGid()!=null){
					System.out.println("GID is not null");
					return null;
				}
				if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired){
					System.out.println("CDD is less than required");
					return null;
				}
				groupHist.setId(opre.getId());
				groupHist.setGid(opre.getId());
				groupHist.setHeight(opre.getHeight());
				groupHist.setIndex(opre.getTxIndex());
				groupHist.setTime(opre.getTime());
				groupHist.setSigner(opre.getSigner());
				groupHist.setCdd(opre.getCdd());

				groupHist.setName(groupRaw.getName());
				if(groupRaw.getDesc()!=null)groupHist.setDesc(groupRaw.getDesc());

				break;

			case "update":
				if(groupRaw.getGid()==null){
					System.out.println("GID is null");
					return null;
				}
				if(groupRaw.getName()==null){
					System.out.println("Name is null");
					return null;
				}
				groupHist.setGid(groupRaw.getGid());
				groupHist.setId(opre.getId());
				groupHist.setHeight(opre.getHeight());
				groupHist.setIndex(opre.getTxIndex());
				groupHist.setTime(opre.getTime());
				groupHist.setSigner(opre.getSigner());
				groupHist.setCdd(opre.getCdd());

				groupHist.setName(groupRaw.getName());
				if(groupRaw.getDesc()!=null)groupHist.setDesc(groupRaw.getDesc());

				break;

			case "join":
				if(groupRaw.getGid()==null){
					System.out.println("GID is null");
					return null;
				}
				if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired){
					System.out.println("CDD is less than required");
					return null;
				}
				groupHist.setGid(groupRaw.getGid());

				groupHist.setId(opre.getId());
				groupHist.setHeight(opre.getHeight());
				groupHist.setIndex(opre.getTxIndex());
				groupHist.setTime(opre.getTime());
				groupHist.setSigner(opre.getSigner());
				groupHist.setCdd(opre.getCdd());
				break;
			case "leave":
				if(groupRaw.getGids()==null || groupRaw.getGids().isEmpty()){
					System.out.println("GIDs are null or empty");
					return null;
				}
				groupHist.setGids(groupRaw.getGids());

				groupHist.setId(opre.getId());
				groupHist.setHeight(opre.getHeight());
				groupHist.setIndex(opre.getTxIndex());
				groupHist.setTime(opre.getTime());
				groupHist.setSigner(opre.getSigner());
				break;
			default:
				System.out.println("Invalid operation");
				return null;
		}
		return groupHist;
	}

	public boolean parseGroup(ElasticsearchClient esClient, GroupHistory groupHist) throws Exception {

		if(groupHist==null || groupHist.getOp()==null){
			System.out.println("Group history is null or OP is null");
			return false;
		}
		Group group;

		switch(groupHist.getOp()) {
			case CREATE:
				group = EsUtils.getById(esClient, IndicesNames.GROUP, groupHist.getGid(), Group.class);
				if(group==null) {
					group = new Group();
					group.setId(groupHist.getId());
					group.setName(groupHist.getName());
					group.setDesc(groupHist.getDesc());

					String[] namers = new String[1];
					String[] activeMembers = new String[1];

					namers[0]=groupHist.getSigner();
					activeMembers[0]=groupHist.getSigner();

					group.setNamers(namers);
					group.setMembers(activeMembers);
					group.setMemberNum((long) activeMembers.length);

					group.setBirthTime(groupHist.getTime());
					group.setBirthHeight(groupHist.getHeight());

					group.setLastTxId(groupHist.getId());
					group.setLastTime(groupHist.getTime());
					group.setLastHeight(groupHist.getHeight());

					if(groupHist.getCdd()==null){
						group.setCddToUpdate(1L);
					} else {
						group.setCddToUpdate(groupHist.getCdd()+1);
						if(group.gettCdd()==null)group.settCdd(groupHist.getCdd());
						else group.settCdd(group.gettCdd()+groupHist.getCdd());
					}

					Group group1=group;
					IndexResponse result = esClient.index(i->i.index(IndicesNames.GROUP).id(groupHist.getGid()).document(group1));
					System.out.println(result.result());
					if(!CREATED.equals(result.result().jsonValue()) && !UPDATED.equals(result.result().jsonValue())){
						System.out.println("Failed to create group");
						return false;
					}

					// Create News
					News.createNews(esClient, groupHist.getId(), groupHist.getSigner(), CREATE, Feip.FeipProtocol.GROUP.getName(),
							groupHist.getId(), groupHist.getName(), groupHist.getDesc(), groupHist.getHeight(), groupHist.getTime());
					return true;
				}else {
					System.out.println("Group is not found");
					return false;
				}


			case "join":

				group = EsUtils.getById(esClient, IndicesNames.GROUP, groupHist.getGid(), Group.class);

				if(group==null) {
					System.out.println("Group is not found");
					return false;
				}
				String [] activeMembers = new String[group.getMembers().length+1];

				Set<String>memberSet = new HashSet<String>();

				for(String member:group.getMembers()) {
					memberSet.add(member);
				}
				memberSet.add(groupHist.getSigner());
				activeMembers = memberSet.toArray(new String[memberSet.size()]);

				group.setMembers(activeMembers);
				group.setMemberNum((long) activeMembers.length);

				group.setLastTxId(groupHist.getId());
				group.setLastTime(groupHist.getTime());
				group.setLastHeight(groupHist.getHeight());

				if(groupHist.getCdd()!=null) {
					Long tCdd = 0L;
					if(group.gettCdd()!=null)tCdd = group.gettCdd();
					group.settCdd(tCdd + groupHist.getCdd());
				}
				Group group2 = group;

				IndexResponse result2 = esClient.index(i->i.index(IndicesNames.GROUP).id(groupHist.getGid()).document(group2));
				System.out.println(result2.result());

				return CREATED.equals(result2.result().jsonValue()) || UPDATED.equals(result2.result().jsonValue());

			case "update":

				group = EsUtils.getById(esClient, IndicesNames.GROUP, groupHist.getGid(), Group.class);

				if(group==null) {
					System.out.println("Group is not found");
					return false;
				}

				if(groupHist.getCdd()==null || groupHist.getCdd() < group.getCddToUpdate()){
					System.out.println("CDD is less than required");
					return false;
				}
				group.setCddToUpdate(groupHist.getCdd()+1);

				Long tCdd = 0L;
				if(group.gettCdd()!=null) tCdd = group.gettCdd();
				group.settCdd(tCdd + groupHist.getCdd());

				boolean found =false;
				for(String member:group.getMembers()) {
					if(member.equals(groupHist.getSigner())) {
						found=true;
						break;
					}
				}
				if(!found){
					System.out.println("Signer is not found in group");
					return false;
				}

				group.setName(groupHist.getName());
				group.setDesc(groupHist.getDesc());

				Set<String> namerSet = new HashSet<String>();
				for(String namer: group.getNamers()) {
					namerSet.add(namer);
				}
				namerSet.add(groupHist.getSigner());
				String[] namers = namerSet.toArray(new String[namerSet.size()]);

				group.setNamers(namers);

				group.setLastTxId(groupHist.getId());
				group.setLastTime(groupHist.getTime());
				group.setLastHeight(groupHist.getHeight());

				Group group3 = group;

				IndexResponse result1 = esClient.index(i->i.index(IndicesNames.GROUP).id(groupHist.getGid()).document(group3));
				System.out.println(result1.result());
				return CREATED.equals(result1.result().jsonValue()) || UPDATED.equals(result1.result().jsonValue());

			case "leave":

				if(groupHist.getGids()==null || groupHist.getGids().isEmpty()){
					System.out.println("GIDs are null or empty");
					return false;
				}

				EsUtils.MgetResult<Group> result = EsUtils.getMultiByIdList(esClient, IndicesNames.GROUP, groupHist.getGids(), Group.class);
				if(result==null||result.getResultList()==null||result.getResultList().isEmpty()){
					System.out.println("Group list is empty");
					return false;
				}

				BulkRequest.Builder br = new BulkRequest.Builder();
				for(Group group1:result.getResultList()){

					String [] activeMembers1 = new String[group1.getMembers().length+1];

					Set<String>memberSet1 = new HashSet<String>();

					boolean found1 =false;
					for(String member:group1.getMembers()) {
						if(!member.equals(groupHist.getSigner())) {
							memberSet1.add(member);
						}else found1=true;
					}

					if(!found1){
						System.out.println("Signer is not found in group");
						return false;
					}

					activeMembers1 = memberSet1.toArray(new String[0]);
					group1.setMembers(activeMembers1);
					group1.setMemberNum((long) activeMembers1.length);

					//TODO Important: If no one is in this group, delete the group and its history.
					if(activeMembers1.length==0){
						Group finalGroup = group1;
						esClient.delete(d->d.index(IndicesNames.GROUP).id(finalGroup.getId()));
						Group finalGroup1 = group1;
						esClient.deleteByQuery(d->d.index(IndicesNames.GROUP_HISTORY).query(q->q.term(t->t.field("gid").value(finalGroup1.getId()))));
						System.out.println("Group and group history are deleted");
						return false;
					}

					group1.setLastTxId(groupHist.getId());
					group1.setLastTime(groupHist.getTime());
					group1.setLastHeight(groupHist.getHeight());

					if(groupHist.getCdd()!=null) {
						if(group1.gettCdd()==null)group1.settCdd(groupHist.getCdd());
						else group1.settCdd(group1.gettCdd() + groupHist.getCdd());
					}

					Group group4 = group1;
					br.operations(op -> op
							.index(idx -> idx
									.index(IndicesNames.GROUP)
									.id(group4.getId())
									.document(group4)
							)
					);
				}

				BulkResponse result3 = esClient.bulk(br.build());
				if(result3.errors()){
					System.out.println("Failed");
					return false;
				}
				else {
					System.out.println("Done");
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
			teamRaw = gson.fromJson(gson.toJson(feip.getData()), TeamOpData.class);
			if(teamRaw==null){
				System.out.println("Team data is null");
				return null;
			}
		}catch(com.google.gson.JsonSyntaxException e) {
			System.out.println("Bad team data");
			return null;
		}

		TeamHistory teamHist = new TeamHistory();

		if(teamRaw.getOp()==null){
			System.out.println("OP is null");
			return null;
		}
		teamHist.setOp(teamRaw.getOp());

		switch(teamRaw.getOp()) {

			case CREATE:
				if(teamRaw.getStdName()==null){
					System.out.println("StdName is null");
					return null;
				}
				if(teamRaw.getTid()!=null){
					System.out.println("TID is not null");
					return null;
				}
				if(teamRaw.getConsensusId()==null){
					System.out.println("ConsensusId is null");
					return null;
				}
				if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired){
					System.out.println("CDD is less than required");
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

				break;

			case "disband", "leave":
				if(teamRaw.getTids()==null){
					System.out.println("TIDs are null");
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
					System.out.println("TID is null");
					return null;
				}
				if(teamRaw.getTransferee() ==null){
					System.out.println("Transferee is null");
					return null;
				}
				if(!teamRaw.getConfirm().equals("I transfer the team to the transferee.")){
					System.out.println("Confirm is not 'I transfer the team to the transferee.'");
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
					System.out.println("TID is null");
					return null;
				}
				if(!teamRaw.getConfirm().equals("I take over the team and agree with the team consensus.")){
					System.out.println("Confirm is not 'I take over the team and agree with the team consensus.'");
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
					System.out.println("TID is null");
					return null;
				}
				if(teamRaw.getStdName()==null){
					System.out.println("StdName is null");
					return null;
				}
				if(teamRaw.getConsensusId()==null){
					System.out.println("ConsensusId is null");
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

				break;
			case "agree consensus":
				if(teamRaw.getTid()==null){
					System.out.println("TID is null");
					return null;
				}
				if(!teamRaw.getConfirm().equals("I agree with the new consensus.")){
					System.out.println("Confirm is not 'I agree with the new consensus.'");
					return null;
				}
				teamHist.setTid(teamRaw.getTid());
				if(teamRaw.getConsensusId()!=null)
					teamHist.setConsensusId(teamRaw.getConsensusId());

				teamHist.setId(opre.getId());
				teamHist.setHeight(opre.getHeight());
				teamHist.setIndex(opre.getTxIndex());
				teamHist.setTime(opre.getTime());
				teamHist.setSigner(opre.getSigner());
				break;
			case "invite", "withdraw invitation", "dismiss", "appoint", "cancel appointment":
				if(teamRaw.getTid()==null){
					System.out.println("TID is null");
					return null;
				}
				if(teamRaw.getList()==null){
					System.out.println("List is null");
					return null;
				}
				if(teamRaw.getList().length==0){
					System.out.println("List is empty");
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
					System.out.println("TID is null");
					return null;
				}
				if(!teamRaw.getConfirm().equals("I join the team and agree with the team consensus.")){
					System.out.println("Confirm is not 'I join the team and agree with the team consensus.'");
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

			case "rate":
				if(teamRaw.getTid()==null){
					System.out.println("TID is null");
					return null;
				}
				if(teamRaw.getRate()<0 ||teamRaw.getRate()>5){
					System.out.println("Rate is less than 0 or greater than 5");
					return null;
				}
				if (opre.getCdd() < StartFEIP.CddRequired){
					System.out.println("CDD is less than required");
					return null;
				}
				teamHist.setTid(teamRaw.getTid());
				teamHist.setRate(teamRaw.getRate());
				teamHist.setCdd(opre.getCdd());

				teamHist.setId(opre.getId());
				teamHist.setHeight(opre.getHeight());
				teamHist.setIndex(opre.getTxIndex());
				teamHist.setTime(opre.getTime());
				teamHist.setSigner(opre.getSigner());
				break;
			default:
				System.out.println("Invalid operation");
				return null;
		}
		return teamHist;
	}

	public boolean parseTeam(ElasticsearchClient esClient, TeamHistory teamHist) throws Exception {
		if(teamHist==null || teamHist.getOp()==null){
			System.out.println("Team history is null or OP is null");
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
					System.out.println(result.result());
					if(!CREATED.equals(result.result().jsonValue()) && !UPDATED.equals(result.result().jsonValue())){
						System.out.println("Failed to create team");
						return false;
					}

					// Create News
					News.createNews(esClient, teamHist.getId(), teamHist.getSigner(), CREATE, Feip.FeipProtocol.TEAM.getName(),
							teamHist.getId(), teamHist.getStdName(), teamHist.getDesc(), teamHist.getHeight(), teamHist.getTime());
					return true;
				}else {
					System.out.println("Team is not found");
					return false;
				}

			case DISBAND:
				if(teamHist.getTids()==null||teamHist.getTids().isEmpty()) {
					System.out.println("TIDs are null or empty");
					return false;
				}

				EsUtils.MgetResult<Team> result = EsUtils.getMultiByIdList(esClient, IndicesNames.TEAM, teamHist.getTids(), Team.class);
				if(result==null||result.getResultList()==null||result.getResultList().isEmpty()) {
					System.out.println("Team list is empty");
					return false;
				}

				if(result.getMissList()!=null && !result.getMissList().isEmpty()) {
					System.out.println("Teams not found: "+result.getMissList());
				}

				BulkRequest.Builder br = new BulkRequest.Builder();
				for(Team team1:result.getResultList()) {
					if(! team1.getOwner().equals(teamHist.getSigner())) {
						continue;
					}
					if(Boolean.FALSE.equals(team1.isActive())) {
						continue;
					}
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
				BulkResponse result4 = esClient.bulk(br.build());
				if(result4.errors())System.out.println("Failed");
				else System.out.println("Done");

				// Create News
				News.createNews(esClient, teamHist.getId(), teamHist.getSigner(), OpNames.DISBAND, Feip.FeipProtocol.TEAM.getName(),
						null, null, StringUtils.listToString(teamHist.getTids()), teamHist.getHeight(), teamHist.getTime());

				return true;

			case TRANSFER:

				team = EsUtils.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					System.out.println("Team is not found");
					return false;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					System.out.println("Team is not active");
					return false;
				}

				if(! team.getOwner().equals(teamHist.getSigner())) {
					Freer resultCid = EsUtils.getById(esClient, IndicesNames.FREER, teamHist.getSigner(), Freer.class);
					if(resultCid.getMaster()!=null) {
						if(! resultCid.getMaster().equals(teamHist.getSigner())) {
							System.out.println("Signer is not the master");
							return false;
						}
					}else {
						System.out.println("Signer is not the master");
						return false;
					}
				}

				if(teamHist.getTransferee().equals(team.getOwner())) {
					team.setTransferee(null);
				}else team.setTransferee(teamHist.getTransferee());

				team.setLastTxId(teamHist.getId());
				team.setLastTime(teamHist.getTime());
				team.setLastHeight(teamHist.getHeight());

				Team team3 = team;

				IndexResponse result3 = esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team3));
				System.out.println(result3.result());
				if(!CREATED.equals(result3.result().jsonValue()) && !UPDATED.equals(result3.result().jsonValue())){
					System.out.println("Failed to transfer team");
					return false;
				}
				// Create News
				News.createNews(esClient, teamHist.getId(), teamHist.getSigner(), OpNames.TRANSFER, Feip.FeipProtocol.TEAM.getName(),
						teamHist.getTid(), teamHist.getTransferee(), null, teamHist.getHeight(), teamHist.getTime());

				return true;

			case TAKE_OVER:

				team = EsUtils.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					System.out.println("Team is not found");
					return false;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					System.out.println("Team is not active");
					return false;
				}

				if(team.getTransferee()==null) {
					System.out.println("Transferee is null");
					return false;
				}

				if(teamHist.getConsensusId()!=null) {
					if(!teamHist.getConsensusId().equals(team.getConsensusId())){
						System.out.println("ConsensusId is not the same");
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
					System.out.println(result5.result());
					if(!CREATED.equals(result5.result().jsonValue()) && !UPDATED.equals(result5.result().jsonValue())){
						System.out.println("Failed to take over team");
						return false;
					}
					// Create News
					News.createNews(esClient, teamHist.getId(), teamHist.getSigner(), OpNames.TAKE_OVER, Feip.FeipProtocol.TEAM.getName(),
							teamHist.getTid(), null, null, teamHist.getHeight(), teamHist.getTime());

					return true;
				}

			case UPDATE:
				team = EsUtils.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					System.out.println("Team is not found");
					return false;
				}

				if(! team.getOwner().equals(teamHist.getSigner())) {
					System.out.println("Signer is not the owner");
					return false;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					System.out.println("Team is not active");
					return false;
				}

				team.setStdName(teamHist.getStdName());
				if(teamHist.getLocalNames() !=null) team.setLocalNames(teamHist.getLocalNames());
				if(teamHist.getWaiters()!=null)team.setWaiters(teamHist.getWaiters());
				if(teamHist.getAccounts()!=null)team.setAccounts(teamHist.getAccounts());
				if(teamHist.getDesc() !=null) team.setDesc(teamHist.getDesc());

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


				if(team.getConsensusId() !=null) {
					if(! team.getConsensusId().equals(teamHist.getConsensusId())) {
						team.setNotAgreeMembers(team.getMembers());
					}
				}

				team.setLastTxId(teamHist.getId());
				team.setLastTime(teamHist.getTime());
				team.setLastHeight(teamHist.getHeight());

				Team team5 = team;

				IndexResponse result5 = esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team5));
				System.out.println(result5.result());
				return CREATED.equals(result5.result().jsonValue()) || UPDATED.equals(result5.result().jsonValue());

			case "agree consensus":

				team = EsUtils.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					System.out.println("Team is not found");
					return false;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					System.out.println("Team is not active");
					return false;
				}

				if(!teamHist.getConsensusId().equals(team.getConsensusId())){
					System.out.println("ConsensusId is not the same");
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
					System.out.println(result6.result());
					return CREATED.equals(result6.result().jsonValue()) || UPDATED.equals(result6.result().jsonValue());
				}else {
					System.out.println("Signer is not in the not agree members");
					return false;
				}

			case "invite":

				team = EsUtils.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					System.out.println("Team is not found");
					return false;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					System.out.println("Team is not active");
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
							System.out.println(result7.result());
							return CREATED.equals(result7.result().jsonValue()) || UPDATED.equals(result7.result().jsonValue());
						}
					}
				}

			case "withdraw invitation":

				team = EsUtils.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					System.out.println("Team is not found");
					return false;
				}

				if(Boolean.FALSE.equals(team.isActive()) ){
					System.out.println("Team is not active");
					return false;
				}

				if(team.getManagers()!=null) {
					for(String admin:team.getManagers()) {
						if(admin.equals(teamHist.getSigner())) {

							Set<String> inviteeSet = new HashSet<String>();
							for(String invitee:team.getInvitees()) {
								inviteeSet.add(invitee);
							}
							for(String invitee:teamHist.getList()) {
								inviteeSet.remove(invitee);
							}
							String[] invitees = inviteeSet.toArray(new String[inviteeSet.size()]);
							team.setInvitees(invitees);
							team.setLastTxId(teamHist.getId());
							team.setLastTime(teamHist.getTime());
							team.setLastHeight(teamHist.getHeight());

							Team team7 = team;

							IndexResponse result8 = esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team7));
							System.out.println(result8.result());
							return CREATED.equals(result8.result().jsonValue()) || UPDATED.equals(result8.result().jsonValue());
						}
					}
				}

			case "join":

				team = EsUtils.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					System.out.println("Team is not found");
					return false;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					System.out.println("Team is not active");
					return false;
				}

				if(team.getConsensusId()==null) {
					System.out.println("ConsensusId is null");
					return false;
				}

				if(!teamHist.getConsensusId().equals(team.getConsensusId())){
					System.out.println("ConsensusId is not the same");
					return false;
				}
				if(team.getInvitees()!=null) {
					for(String invitee:team.getInvitees()) {
						if(invitee.equals(teamHist.getSigner())) {

							Set<String> activeMemberSet = new HashSet<String>();
							for(String activeMember:team.getMembers()) {
								activeMemberSet.add(activeMember);
							}
							activeMemberSet.add(teamHist.getSigner());
							String[] activeMembers = activeMemberSet.toArray(new String[activeMemberSet.size()]);

							Set<String>leftMemberSet = new HashSet<String>();

							if(team.getExMembers()!=null) {
								for(String leftMember:team.getExMembers()) {
									if(!leftMember.equals(teamHist.getSigner())) {
										leftMemberSet.add(leftMember);
									}
								}
								String[] leftMembers = leftMemberSet.toArray(new String[leftMemberSet.size()]);
								team.setExMembers(leftMembers);
							}

							Set<String> inviteeSet = new HashSet<String>();
							for(String invite: team.getInvitees()) {
								if(!invite.equals(teamHist.getSigner())) inviteeSet.add(invite);
							}

							if(inviteeSet.size()==0) {
								team.setInvitees(null);
							}else {
								String[] invitees = inviteeSet.toArray(new String[inviteeSet.size()]);
								team.setInvitees(invitees);
							}
							team.setMembers(activeMembers);
							team.setMemberNum((long) activeMembers.length);
							team.setLastTxId(teamHist.getId());
							team.setLastTime(teamHist.getTime());
							team.setLastHeight(teamHist.getHeight());

							Team team7 = team;

							IndexResponse result9 = esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team7));
							System.out.println(result9.result());
							return CREATED.equals(result9.result().jsonValue()) || UPDATED.equals(result9.result().jsonValue());
						}
					}
				}

			case "leave":
				if(teamHist.getTids()==null||teamHist.getTids().isEmpty()) {
					System.out.println("TIDs are null or empty");
					return false;
				}
				MgetResult<Team> result1 = EsUtils.getMultiByIdList(esClient,IndicesNames.TEAM, teamHist.getTids(), Team.class);
				if(result1==null||result1.getResultList()==null||result1.getResultList().isEmpty()) {
					System.out.println("Team list is empty");
					return false;
				}

				if(result1.getMissList()!=null&&!result1.getMissList().isEmpty()) {
					System.out.println("Teams not found: "+result1.getMissList());
				}

				BulkRequest.Builder br1 = new BulkRequest.Builder();

				Iterator<Team> iterator = result1.getResultList().iterator();
				while(iterator.hasNext()) {
					team = iterator.next();

					if(Boolean.FALSE.equals(team.isActive())) {
						continue;
					}

					if(team.getOwner().equals(teamHist.getSigner())) {
						continue;
					}

					found = false;
					Set<String> activeMemberSet = new HashSet<String>();
					for(String activeMember:team.getMembers()) {
						if(activeMember.equals(teamHist.getSigner())) {
							found = true;
						}else {
							activeMemberSet.add(activeMember);
						}
					}
					if(found) {
						String[] activeMembers = activeMemberSet.toArray(new String[0]);
						team.setMembers(activeMembers);
						team.setMemberNum((long) activeMembers.length);

						Set<String> exMemberSet = new HashSet<String>();
						if(team.getExMembers()!=null) {
							exMemberSet.addAll(Arrays.asList(team.getExMembers()));
							exMemberSet.add(teamHist.getSigner());
							String[] exMembers = exMemberSet.toArray(new String[0]);
							team.setExMembers(exMembers);
						}else{
							team.setExMembers(new String[]{teamHist.getSigner()});
						}

						if(team.getManagers()!=null) {
							Set<String> magagerSet = new HashSet<String>();
							for(String magager:team.getManagers()) {
								if(!magager.equals(teamHist.getSigner())) {
									magagerSet.add(magager);
								}
							}
							String[] managers = magagerSet.toArray(new String[0]);
							team.setManagers(managers);
						}
						team.setLastTxId(teamHist.getId());
						team.setLastTime(teamHist.getTime());
						team.setLastHeight(teamHist.getHeight());

						Team team7 = team;

						br1.operations(op -> op
								.index(idx -> idx
										.index(IndicesNames.TEAM)
										.id(team7.getId())
										.document(team7)
								)
						);
					}
					BulkResponse result6 = esClient.bulk(br1.build());
					if(result6.errors()){
						System.out.println("Failed");
						return false;
					}
					else {
						System.out.println("Done");
						return true;
					}
				}

			case "dismiss":
				if(teamHist.getTid()==null) {
					System.out.println("TID is null");
					return false;
				}

				team = EsUtils.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					System.out.println("Team is not found");
					return false;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					System.out.println("Team is not active");
					return false;
				}

				for(String manager:team.getManagers()) {
					if(manager.equals(teamHist.getSigner())) {

						Set<String> activeMemberSet1 = new HashSet<String>();
						if(team.getMembers()!=null) {
							for(String activeMember:team.getMembers()) {
								activeMemberSet1.add(activeMember);
							}
						}

						Set<String>exMemberSet = new HashSet<String>();
						if(team.getExMembers()!=null) {
							for(String leftMember:team.getExMembers()) {
								exMemberSet.add(leftMember);
							}
						}

						Set<String>magagerSet = new HashSet<String>();
						if(team.getManagers()!=null) {
							for(String magager:team.getManagers()) {
								magagerSet.add(magager);
							}
						}

						for(String dismissedPerson:teamHist.getList()) {
							if(dismissedPerson.equals(team.getOwner()))continue;
							if(!activeMemberSet1.contains(dismissedPerson))continue;
							exMemberSet.add(dismissedPerson);
							magagerSet.remove(dismissedPerson);
							activeMemberSet1.remove(dismissedPerson);
						}

						String[] activeMembers = activeMemberSet1.toArray(new String[activeMemberSet1.size()]);
						team.setMembers(activeMembers);
						team.setMemberNum((long) activeMembers.length);

						if(exMemberSet.size()==0) {
							team.setExMembers(null);
						}else {
							String[] leftMembers = exMemberSet.toArray(new String[exMemberSet.size()]);
							team.setExMembers(leftMembers);
						}

						if(magagerSet.size()==0) {
							team.setManagers(null);
						}else {
							String[] magagers = magagerSet.toArray(new String[magagerSet.size()]);
							team.setManagers(magagers);
						}

						team.setLastTxId(teamHist.getId());
						team.setLastTime(teamHist.getTime());
						team.setLastHeight(teamHist.getHeight());

						Team team7 = team;

						IndexResponse result9 = esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team7));
						System.out.println(result9.result());
						return CREATED.equals(result9.result().jsonValue()) || UPDATED.equals(result9.result().jsonValue());
					}
				}

			case "appoint":

				team = EsUtils.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					System.out.println("Team is not found");
					return false;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					System.out.println("Team is not active");
					return false;
				}

				if(! team.getOwner().equals(teamHist.getSigner())) {
					System.out.println("Signer is not the owner");
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
				System.out.println(result10.result());
				return CREATED.equals(result10.result().jsonValue()) || UPDATED.equals(result10.result().jsonValue());

			case "cancel appointment":
				team = EsUtils.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					System.out.println("Team is not found");
					return false;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					System.out.println("Team is not active");
					return false;
				}

				if(! team.getOwner().equals(teamHist.getSigner())) {
					System.out.println("Signer is not the owner");
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
				System.out.println(result11.result());
				return CREATED.equals(result11.result().jsonValue()) || UPDATED.equals(result11.result().jsonValue());

			case "rate":
				team = EsUtils.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					System.out.println("Team is not found");
					return false;
				}

				if(team.getOwner().equals(teamHist.getSigner())) {
					System.out.println("Signer is the owner");
					return false;
				}

				if(teamHist.getCdd()!=null)
					if(team.gettCdd()!=null) {
						if (team.gettCdd() + teamHist.getCdd() == 0) {
							team.settRate(0f);
						} else {
							team.settRate(
									(team.gettRate() * team.gettCdd() + teamHist.getRate() * teamHist.getCdd())
											/ (team.gettCdd() + teamHist.getCdd())
							);
						}
						team.settCdd(team.gettCdd() + teamHist.getCdd());
					}

				team.setLastTxId(teamHist.getId());
				team.setLastTime(teamHist.getTime());
				team.setLastHeight(teamHist.getHeight());

				Team team9 = team;

				IndexResponse result12 = esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team9));
				System.out.println(result12.result());
				return CREATED.equals(result12.result().jsonValue()) || UPDATED.equals(result12.result().jsonValue());
			default:
				System.out.println("Invalid operation");
				return false;
		}
	}

}
