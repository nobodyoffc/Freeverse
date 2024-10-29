package organize;

import clients.esClient.EsTools;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.google.gson.Gson;
import constants.IndicesNames;
import fch.fchData.OpReturn;
import feip.feipData.*;
import startFEIP.StartFEIP;

import java.io.IOException;
import java.util.*;

public class OrganizationParser {

	public GroupHistory makeGroup(OpReturn opre, FcInfo feip) {

		Gson gson = new Gson();

		GroupData groupRaw = new GroupData();

		try {
			groupRaw = gson.fromJson(gson.toJson(feip.getData()), GroupData.class);
			if(groupRaw==null)return null;
		}catch(com.google.gson.JsonSyntaxException e) {
			return null;
		}

		GroupHistory groupHist = new GroupHistory();

		if(groupRaw.getOp()==null)return null;
		groupHist.setOp(groupRaw.getOp());

		switch(groupRaw.getOp()) {

			case "create":
				if(groupRaw.getName()==null)return null;
				if(groupRaw.getGid()!=null)return null;
            	if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired * 100) return null;
				groupHist.setTxId(opre.getTxId());
				groupHist.setGid(opre.getTxId());
				groupHist.setHeight(opre.getHeight());
				groupHist.setIndex(opre.getTxIndex());
				groupHist.setTime(opre.getTime());
				groupHist.setSigner(opre.getSigner());
				groupHist.setCdd(opre.getCdd());

				groupHist.setName(groupRaw.getName());
				if(groupRaw.getDesc()!=null)groupHist.setDesc(groupRaw.getDesc());

				break;

			case "update":
				if(groupRaw.getGid()==null) return null;
				if(groupRaw.getName()==null) return null;
				groupHist.setGid(groupRaw.getGid());
				groupHist.setTxId(opre.getTxId());
				groupHist.setHeight(opre.getHeight());
				groupHist.setIndex(opre.getTxIndex());
				groupHist.setTime(opre.getTime());
				groupHist.setSigner(opre.getSigner());
				groupHist.setCdd(opre.getCdd());

				groupHist.setName(groupRaw.getName());
				if(groupRaw.getDesc()!=null)groupHist.setDesc(groupRaw.getDesc());

				break;

			case "join":
				if(groupRaw.getGid()==null)return null;
            	if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired) return null;
				groupHist.setGid(groupRaw.getGid());

				groupHist.setTxId(opre.getTxId());
				groupHist.setHeight(opre.getHeight());
				groupHist.setIndex(opre.getTxIndex());
				groupHist.setTime(opre.getTime());
				groupHist.setSigner(opre.getSigner());
				groupHist.setCdd(opre.getCdd());
				break;
			case "leave":
				if(groupRaw.getGid()==null)return null;
				groupHist.setGid(groupRaw.getGid());

				groupHist.setTxId(opre.getTxId());
				groupHist.setHeight(opre.getHeight());
				groupHist.setIndex(opre.getTxIndex());
				groupHist.setTime(opre.getTime());
				groupHist.setSigner(opre.getSigner());
				break;
			default:
				return null;
		}
		return groupHist;
	}

	public boolean parseGroup(ElasticsearchClient esClient, GroupHistory groupHist) throws ElasticsearchException, IOException {

		if(groupHist==null || groupHist.getOp()==null)return false;
		boolean isValid = false;
		Group group;

		switch(groupHist.getOp()) {
			case "create":
				group = EsTools.getById(esClient, IndicesNames.GROUP, groupHist.getGid(), Group.class);
				if(group==null) {
					group = new Group();
					group.setGid(groupHist.getTxId());
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

					group.setLastTxId(groupHist.getTxId());
					group.setLastTime(groupHist.getTime());
					group.setLastHeight(groupHist.getHeight());

					if(groupHist.getCdd()==null){
						group.setCddToUpdate(1L);
					}
					else {
						group.setCddToUpdate(groupHist.getCdd()+1);
						if(group.gettCdd()==null)group.settCdd(groupHist.getCdd());
						else group.settCdd(group.gettCdd()+groupHist.getCdd());
					}

					Group group1=group;
					esClient.index(i->i.index(IndicesNames.GROUP).id(groupHist.getGid()).document(group1));
					isValid = true;
				}else {
					isValid = false;
				}

				break;

			case "join":

				group = EsTools.getById(esClient, IndicesNames.GROUP, groupHist.getGid(), Group.class);

				if(group==null) {
					isValid = false;
					break;
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

				group.setLastTxId(groupHist.getTxId());
				group.setLastTime(groupHist.getTime());
				group.setLastHeight(groupHist.getHeight());

				if(groupHist.getCdd()!=null) {
					Long tCdd = 0L;
					if(group.gettCdd()!=null)tCdd = group.gettCdd();
					group.settCdd(tCdd + groupHist.getCdd());
				}
				Group group2 = group;

				esClient.index(i->i.index(IndicesNames.GROUP).id(groupHist.getGid()).document(group2));

				isValid = true;
				break;

			case "update":

				group = EsTools.getById(esClient, IndicesNames.GROUP, groupHist.getGid(), Group.class);

				if(group==null) {
					isValid = false;
					break;
				}

				if(groupHist.getCdd()==null || groupHist.getCdd() < group.getCddToUpdate())return isValid;
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
				if(!found)return isValid;

				group.setName(groupHist.getName());
				group.setDesc(groupHist.getDesc());

				Set<String> namerSet = new HashSet<String>();
				for(String namer: group.getNamers()) {
					namerSet.add(namer);
				}
				namerSet.add(groupHist.getSigner());
				String[] namers = namerSet.toArray(new String[namerSet.size()]);

				group.setNamers(namers);

				group.setLastTxId(groupHist.getTxId());
				group.setLastTime(groupHist.getTime());
				group.setLastHeight(groupHist.getHeight());

				Group group3 = group;

				esClient.index(i->i.index(IndicesNames.GROUP).id(groupHist.getGid()).document(group3));
				isValid = true;
				break;

			case "leave":

				group = EsTools.getById(esClient, IndicesNames.GROUP, groupHist.getGid(), Group.class);

				if(group==null) {
					isValid = false;
					break;
				}
				String [] activeMembers1 = new String[group.getMembers().length+1];

				Set<String>memberSet1 = new HashSet<String>();

				boolean found1 =false;
				for(String member:group.getMembers()) {
					if(!member.equals(groupHist.getSigner())) {
						memberSet1.add(member);
					}else found1=true;
				}

				if(!found1)return isValid;

				activeMembers1 = memberSet1.toArray(new String[0]);
				group.setMembers(activeMembers1);
				group.setMemberNum((long) activeMembers1.length);

				//TODO Important: If no one is in this group, delete the group and its history.
				if(activeMembers1.length==0){
					Group finalGroup = group;
					esClient.delete(d->d.index(IndicesNames.GROUP).id(finalGroup.getGid()));
					Group finalGroup1 = group;
					esClient.deleteByQuery(d->d.index(IndicesNames.GROUP_HISTORY).query(q->q.term(t->t.field("gid").value(finalGroup1.getGid()))));
					isValid = false;
					return isValid;
				}

				group.setLastTxId(groupHist.getTxId());
				group.setLastTime(groupHist.getTime());
				group.setLastHeight(groupHist.getHeight());

				if(groupHist.getCdd()!=null) {
					if(group.gettCdd()==null)group.settCdd(groupHist.getCdd());
					else group.settCdd(group.gettCdd() + groupHist.getCdd());
				}

				Group group4 = group;

				esClient.index(i->i.index(IndicesNames.GROUP).id(groupHist.getGid()).document(group4));

				isValid = true;
				break;

		}
		return isValid;
	}

	public TeamHistory makeTeam(OpReturn opre, FcInfo feip)  {

        if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired) return null;

		Gson gson = new Gson();

		TeamData teamRaw = new TeamData();

		try {
			teamRaw = gson.fromJson(gson.toJson(feip.getData()), TeamData.class);
			if(teamRaw==null)return null;
		}catch(com.google.gson.JsonSyntaxException e) {
			return null;
		}

		TeamHistory teamHist = new TeamHistory();

		if(teamRaw.getOp()==null)return null;
		teamHist.setOp(teamRaw.getOp());

		switch(teamRaw.getOp()) {

			case "create":
				if(teamRaw.getStdName()==null) return null;
				if(teamRaw.getTid()!=null) return null;
				if(teamRaw.getConsensusId()==null) return null;
            	if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired * 100) return null;
				teamHist.setTxId(opre.getTxId());
				teamHist.setTid(opre.getTxId());
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
				if(teamRaw.getTid()==null)return null;
				teamHist.setTid(teamRaw.getTid());

				teamHist.setTxId(opre.getTxId());
				teamHist.setHeight(opre.getHeight());
				teamHist.setIndex(opre.getTxIndex());
				teamHist.setTime(opre.getTime());
				teamHist.setSigner(opre.getSigner());
				break;
			case "transfer":
				if(teamRaw.getTid()==null)return null;
				if(teamRaw.getTransferee() ==null)return null;
				if(!teamRaw.getConfirm().equals("I transfer the team to the transferee."))return null;
				teamHist.setTid(teamRaw.getTid());
				teamHist.setTransferee(teamRaw.getTransferee());

				teamHist.setTxId(opre.getTxId());
				teamHist.setHeight(opre.getHeight());
				teamHist.setIndex(opre.getTxIndex());
				teamHist.setTime(opre.getTime());
				teamHist.setSigner(opre.getSigner());
				break;
			case "take over":
				if(teamRaw.getTid()==null)return null;
				if(!teamRaw.getConfirm().equals("I take over the team and agree with the team consensus."))return null;
				teamHist.setTid(teamRaw.getTid());
				if(teamRaw.getConsensusId()!=null)teamHist.setConsensusId(teamRaw.getConsensusId());

				teamHist.setTxId(opre.getTxId());
				teamHist.setHeight(opre.getHeight());
				teamHist.setIndex(opre.getTxIndex());
				teamHist.setTime(opre.getTime());
				teamHist.setSigner(opre.getSigner());
				break;

			case "update":
				if(teamRaw.getTid()==null) return null;
				if(teamRaw.getStdName()==null) return null;
				if(teamRaw.getConsensusId()==null) return null;

				teamHist.setTid(teamRaw.getTid());
				teamHist.setTxId(opre.getTxId());
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
				if(teamRaw.getTid()==null)return null;
				if(!teamRaw.getConfirm().equals("I agree with the new consensus."))return null;
				teamHist.setTid(teamRaw.getTid());
				if(teamRaw.getConsensusId()!=null)
					teamHist.setConsensusId(teamRaw.getConsensusId());

				teamHist.setTxId(opre.getTxId());
				teamHist.setHeight(opre.getHeight());
				teamHist.setIndex(opre.getTxIndex());
				teamHist.setTime(opre.getTime());
				teamHist.setSigner(opre.getSigner());
				break;
			case "invite", "withdraw invitation", "dismiss", "appoint", "cancel appointment":
				if(teamRaw.getTid()==null)return null;
				if(teamRaw.getList()==null)return null;
				if(teamRaw.getList().length==0)return null;
				teamHist.setTid(teamRaw.getTid());
				teamHist.setList(teamRaw.getList());

				teamHist.setTxId(opre.getTxId());
				teamHist.setHeight(opre.getHeight());
				teamHist.setIndex(opre.getTxIndex());
				teamHist.setTime(opre.getTime());
				teamHist.setSigner(opre.getSigner());
				break;

			case "join":
				if(teamRaw.getTid()==null)return null;
				if(!teamRaw.getConfirm().equals("I join the team and agree with the team consensus."))return null;
				teamHist.setTid(teamRaw.getTid());
				if(teamRaw.getConsensusId()!=null)teamHist.setConsensusId(teamRaw.getConsensusId());

				teamHist.setTxId(opre.getTxId());
				teamHist.setHeight(opre.getHeight());
				teamHist.setIndex(opre.getTxIndex());
				teamHist.setTime(opre.getTime());
				teamHist.setSigner(opre.getSigner());
				break;

			case "rate":
				if(teamRaw.getTid()==null)return null;
				if(teamRaw.getRate()<0 ||teamRaw.getRate()>5)return null;
            	if (opre.getCdd() < StartFEIP.CddRequired) return null;
				teamHist.setTid(teamRaw.getTid());
				teamHist.setRate(teamRaw.getRate());
				teamHist.setCdd(opre.getCdd());

				teamHist.setTxId(opre.getTxId());
				teamHist.setHeight(opre.getHeight());
				teamHist.setIndex(opre.getTxIndex());
				teamHist.setTime(opre.getTime());
				teamHist.setSigner(opre.getSigner());
				break;
			default:
				return null;
		}
		return teamHist;
	}

	public boolean parseTeam(ElasticsearchClient esClient, TeamHistory teamHist) throws ElasticsearchException, IOException, InterruptedException {
		if(teamHist==null || teamHist.getOp()==null)return false;
		boolean isValid = false;
		Team team;
		boolean found = false;
		switch(teamHist.getOp()) {
			case "create":
				team = EsTools.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);
				if(team==null) {
					team = new Team();
					team.setTid(teamHist.getTxId());
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

					team.setLastTxId(teamHist.getTxId());
					team.setLastTime(teamHist.getTime());
					team.setLastHeight(teamHist.getHeight());

					team.setActive(true);

					Team team1=team;
					esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team1));
					isValid = true;
				}else {
					isValid = false;
				}
				break;

			case "disband":

				team = EsTools.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					isValid = false;
					break;
				}

				if(! team.getOwner().equals(teamHist.getSigner())) {
					isValid = false;
					break;
				}
				if(Boolean.FALSE.equals(team.isActive())) {
					isValid = false;
					break;
				}
				team.setLastTxId(teamHist.getTxId());
				team.setLastTime(teamHist.getTime());
				team.setLastHeight(teamHist.getHeight());
				team.setActive(false);

				Team team2 = team;
				esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team2));
				isValid = true;

				break;

			case "transfer":

				team = EsTools.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					isValid = false;
					break;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					isValid = false;
					break;
				}

				if(! team.getOwner().equals(teamHist.getSigner())) {
					Cid resultCid = EsTools.getById(esClient, IndicesNames.CID, teamHist.getSigner(), Cid.class);
					if(resultCid.getMaster()!=null) {
						if(! resultCid.getMaster().equals(teamHist.getSigner())) {
							isValid = false;
							break;
						}
					}else {
						isValid = false;
						break;
					}
				}

				if(teamHist.getTransferee().equals(team.getOwner())) {
					team.setTransferee(null);
				}else team.setTransferee(teamHist.getTransferee());

				team.setLastTxId(teamHist.getTxId());
				team.setLastTime(teamHist.getTime());
				team.setLastHeight(teamHist.getHeight());

				Team team3 = team;

				esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team3));
				isValid = true;

				break;

			case "take over":

				team = EsTools.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					isValid = false;
					break;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					isValid = false;
					break;
				}

				if(team.getTransferee()==null) {
					isValid = false;
					break;
				}

				if(teamHist.getConsensusId()!=null) {
					if(!teamHist.getConsensusId().equals(team.getConsensusId())){
						isValid = false;
						break;
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

					esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team4));
					isValid = true;
					break;
				}
				break;

			case "update":
				team = EsTools.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					isValid = false;
					break;
				}

				if(! team.getOwner().equals(teamHist.getSigner())) {
					isValid = false;
					break;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					isValid = false;
					break;
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

				team.setLastTxId(teamHist.getTxId());
				team.setLastTime(teamHist.getTime());
				team.setLastHeight(teamHist.getHeight());

				Team team5 = team;

				esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team5));
				isValid = true;
				break;

			case "agree consensus":

				team = EsTools.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					isValid = false;
					break;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					isValid = false;
					break;
				}

				if(!teamHist.getConsensusId().equals(team.getConsensusId())){
					isValid = false;
					break;
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

					team.setLastTxId(teamHist.getTxId());
					team.setLastTime(teamHist.getTime());
					team.setLastHeight(teamHist.getHeight());

					Team team6 = team;

					esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team6));
					isValid = true;
					break;
				}else {
					isValid = false;
					break;
				}

			case "invite":

				team = EsTools.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					isValid = false;
					break;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					isValid = false;
					break;
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
							team.setLastTxId(teamHist.getTxId());
							team.setLastTime(teamHist.getTime());
							team.setLastHeight(teamHist.getHeight());

							Team team7 = team;

							esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team7));
							isValid = true;
							break;
						}
					}
				}
				break;

			case "withdraw invitation":

				team = EsTools.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					isValid = false;
					break;
				}

				if(Boolean.FALSE.equals(team.isActive()) ){
					isValid = false;
					break;
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
							team.setLastTxId(teamHist.getTxId());
							team.setLastTime(teamHist.getTime());
							team.setLastHeight(teamHist.getHeight());

							Team team7 = team;

							esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team7));
							isValid = true;
							break;
						}
					}
				}
				break;

			case "join":

				team = EsTools.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					isValid = false;
					break;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					isValid = false;
					break;
				}

				if(team.getConsensusId()==null) {
					isValid = false;
					break;
				}

				if(!teamHist.getConsensusId().equals(team.getConsensusId())){
					isValid = false;
					break;
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
							team.setLastTxId(teamHist.getTxId());
							team.setLastTime(teamHist.getTime());
							team.setLastHeight(teamHist.getHeight());

							Team team7 = team;

							esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team7));
							isValid = true;
							break;
						}
					}
				}
				break;

			case "leave":

				team = EsTools.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					isValid = false;
					break;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					isValid = false;
					break;
				}

				if(team.getOwner().equals(teamHist.getSigner())) {
					isValid = false;
					break;
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
					team.setLastTxId(teamHist.getTxId());
					team.setLastTime(teamHist.getTime());
					team.setLastHeight(teamHist.getHeight());

					Team team7 = team;

					esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team7));
					isValid = true;
					break;
				}
				break;

			case "dismiss":

				team = EsTools.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					isValid = false;
					break;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					isValid = false;
					break;
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

						team.setLastTxId(teamHist.getTxId());
						team.setLastTime(teamHist.getTime());
						team.setLastHeight(teamHist.getHeight());

						Team team7 = team;

						esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team7));
						isValid = true;
						break;
					}
				}
				break;

			case "appoint":

				team = EsTools.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					isValid = false;
					break;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					isValid = false;
					break;
				}

				if(! team.getOwner().equals(teamHist.getSigner())) {
					isValid = false;
					break;
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
				team.setLastTxId(teamHist.getTxId());
				team.setLastTime(teamHist.getTime());
				team.setLastHeight(teamHist.getHeight());

				Team team7 = team;

				esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team7));
				isValid = true;
				break;

			case "cancel appointment":
				team = EsTools.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					isValid = false;
					break;
				}

				if(Boolean.FALSE.equals(team.isActive())) {
					isValid = false;
					break;
				}

				if(! team.getOwner().equals(teamHist.getSigner())) {
					isValid = false;
					break;
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
				team.setLastTxId(teamHist.getTxId());
				team.setLastTime(teamHist.getTime());
				team.setLastHeight(teamHist.getHeight());

				Team team8 = team;

				esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team8));
				isValid = true;
				break;

			case "rate":
				team = EsTools.getById(esClient, IndicesNames.TEAM, teamHist.getTid(), Team.class);

				if(team==null) {
					isValid = false;
					break;
				}

				if(team.getOwner().equals(teamHist.getSigner())) {
					isValid = false;
					break;
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

				team.setLastTxId(teamHist.getTxId());
				team.setLastTime(teamHist.getTime());
				team.setLastHeight(teamHist.getHeight());

				Team team9 = team;

				esClient.index(i->i.index(IndicesNames.TEAM).id(teamHist.getTid()).document(team9));
				isValid = true;
				break;
			default:
				throw new IllegalStateException("Unexpected value: " + teamHist.getOp());
		}
		return isValid;
	}

}
