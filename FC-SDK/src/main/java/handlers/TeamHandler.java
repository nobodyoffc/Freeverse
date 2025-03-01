package handlers;

import apip.apipData.Fcdsl;
import appTools.Inputer;
import appTools.Menu;
import appTools.Settings;
import appTools.Shower;
import clients.ApipClient;
import clients.Client;
import clients.FeipClient;
import constants.FieldNames;
import fch.fchData.SendTo;
import feip.feipData.Service;
import feip.feipData.Team;
import feip.feipData.TeamOpData;
import tools.Hex;
import tools.JsonTools;
import tools.PersistentSequenceMap;
import tools.StringTools;
import tools.http.AuthType;
import tools.http.RequestMethod;
import nasa.NaSaRpcClient;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TeamHandler extends Handler {
    // 1. Constants
    public static final int DEFAULT_SIZE = 50;

    // 2. Instance Variables
    private final BufferedReader br;
    private final String myFid;
    private final ApipClient apipClient;
    private final byte[] symKey;
    private final String myPriKeyCipher;
    private final PersistentSequenceMap teamDB;
    private final Map<String, Long> lastTimeMap;

    // 3. Constructor
    public TeamHandler(String myFid, ApipClient apipClient, byte[] symKey,
                       String myPriKeyCipher, Map<String, Long> lastTimeMap, String dbPath, BufferedReader br) {
        this.myFid = myFid;
        this.apipClient = apipClient;
        this.symKey = symKey;
        this.myPriKeyCipher = myPriKeyCipher;
        this.lastTimeMap = lastTimeMap;
        this.teamDB = new PersistentSequenceMap(myFid, null, FieldNames.TEAM, dbPath);
        this.br = br;
    }

    public TeamHandler(Settings settings){
        this.apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);
        this.myFid = settings.getMainFid();
        this.symKey = settings.getSymKey();
        this.myPriKeyCipher = settings.getMyPriKeyCipher();
        this.br = settings.getBr(); 
        this.lastTimeMap = new HashMap<>();
        this.teamDB = new PersistentSequenceMap(myFid, (String)null, FieldNames.TEAM, settings.getDbDir());
    }   

    // 4. Public Methods - Main Interface
    public void menu() {
        byte[] priKey = Client.decryptPriKey(myPriKeyCipher, symKey);
        Menu menu = new Menu("Team", this::close);
        menu.add("List", () -> handleListTeams(priKey, br));
        menu.add("Check", () -> checkTeam(br));
        menu.add("Create", () -> handleCreateTeam(priKey, br));
        menu.add("Find", () -> handleFindTeams(priKey, br));
        menu.add("Leave", () -> handleLeaveTeams(priKey, br));
        menu.add("Join", () -> handleJoinTeam(priKey, br));
        menu.add("Transfer", () -> handleTransferTeam(priKey, br));
        menu.add("Take Over", () -> handleTakeOverTeam(priKey, br));
        menu.add("Manage Members", () -> handleManageMembers(priKey, br));
        menu.showAndSelect(br);
    }

    private void handleManageMembers(byte[] priKey, BufferedReader br) {
        Menu menu = new Menu("Manage Members");
        menu.add("Invite", () -> handleInviteMembers(priKey, br));
        menu.add("Withdraw Invitation", () -> handleWithdrawInvitation(priKey, br));
        menu.add("Dismiss", () -> handleDismissMembers(priKey, br));
        menu.add("Appoint", () -> handleAppointMembers(priKey, br));
        menu.add("Cancel Appointment", () -> handleCancelAppointment(priKey, br));
        menu.showAndSelect(br);
    }

    public void checkTeam(BufferedReader br) {
        List<Team> teamList;
        Long lastTime = lastTimeMap.get(FieldNames.TEAM);
        if (lastTime == null) lastTime = 0L;
        teamList = pullTeamList(myFid, lastTime, apipClient, br);
        if (teamList == null) {
            System.out.println("No updated team found.");
            return;
        }
        for (Team team : teamList) {
            teamDB.put(Hex.fromHex(team.getId()), team.toJson().getBytes());
        }

        if (!teamList.isEmpty()) {
            lastTimeMap.put(FieldNames.TEAM, teamList.get(0).getLastHeight());
            JsonTools.saveToJsonFile(lastTimeMap, myFid, null, FieldNames.LAST_TIME, false);
        }

        System.out.println("Found " + teamList.size() + " updated teams.");
    }

    // 5. Team Operation Methods
    public String createTeam(byte[] priKey, String offLineFid, List<SendTo> sendToList,
            String stdName, String consensusId, String[] localNames, String[] waiters, 
            String[] accounts, String desc, ApipClient apipClient, NaSaRpcClient nasaClient) {
        TeamOpData data = TeamOpData.makeCreate(stdName, consensusId, localNames, waiters, accounts, desc);
        return FeipClient.team(priKey, offLineFid, sendToList,  data, apipClient, nasaClient, br);
    }

    public String joinTeam(byte[] priKey, String offLineFid, List<SendTo> sendToList,
            String tid, String consensusId, ApipClient apipClient, NaSaRpcClient nasaClient) {
        TeamOpData data = TeamOpData.makeJoin(tid, consensusId);
        return FeipClient.team(priKey, offLineFid, sendToList,  data, apipClient, nasaClient, br);
    }

    public String leaveTeams(byte[] priKey, String offLineFid, List<SendTo> sendToList,
            List<String> tids, ApipClient apipClient, NaSaRpcClient nasaClient, BufferedReader br) {
        TeamOpData data = TeamOpData.makeLeave(tids);
        return FeipClient.team(priKey, offLineFid, sendToList,  data, apipClient, nasaClient, br);
    }

    // Add more team operation methods following TeamData.Op...

    // 6. Team List Operation Methods
    public void joinTeams(List<Team> chosenTeamList, boolean isMyTeamList,
            byte[] priKey, String offLineFid, ApipClient apipClient, BufferedReader br) {
        for(Team team : chosenTeamList) {
            if(isMyTeamList) {
                System.out.println("You have already joined this team: ["+team.getId()+"]");
                continue;
            } else {
                System.out.println("Team: "+ StringTools.omitMiddle(team.getId(), 15)+" - "+team.getStdName());
                if(!Inputer.askIfYes(br,"Join this team?")) continue;
                List<String> members = getTeamMembers(team.getId(), apipClient);
                if(members!=null && members.contains(myFid)) {
                    System.out.println("You are already a member of ["+team.getId()+"]. ");
                    continue;
                }
            }
            String consensusId = Inputer.inputString(br, "Input consensus ID:");
            String joinResult = joinTeam(priKey, offLineFid, null, team.getId(), consensusId, apipClient, null);
            if(!Hex.isHexString(joinResult)) System.out.println(joinResult);
            else System.out.println("Joined ["+team.getStdName()+"].");
            if(!Inputer.askIfYes(br,"Join next team?")) break;
        }
        System.out.println("Work done. Check teams a few minutes later.");
    }

    public void leaveTeams(List<Team> chosenTeamList, boolean isMyTeamList,
            byte[] priKey, String offLineFid, ApipClient apipClient, BufferedReader br) {
        showTeamList(chosenTeamList, br);
        if (!Inputer.askIfYes(br, "Leave all these teams?")) return;

        List<String> leaveIdList = new ArrayList<>();
        if (!isMyTeamList) {
            for (Team team : chosenTeamList) {
                List<String> members = getTeamMembers(team.getId(), apipClient);
                if (members != null && !members.contains(myFid)) {
                    System.out.println("You are not a member of [" + team.getId() + "].");
                    continue;
                }
                leaveIdList.add(team.getId());
            }
        }

        String leaveResult = leaveTeams(priKey, offLineFid, null, leaveIdList, apipClient, null, br);
        if(!Hex.isHexString(leaveResult)) System.out.println(leaveResult);
    }

    // 7. Member Management Methods
    private void handleInviteMembers(byte[] priKey, BufferedReader br) {
        Team team = searchAndChooseTeam(apipClient, br);
        if(team == null) return;
        
        String[] memberList = Inputer.inputStringArray(br, "Input FIDs to invite (separated by comma):", 0);
        if(memberList.length == 0) return;

        List<String> inviteeList = new ArrayList<>();
        for(String member : memberList) {
            if(team.getMembers() != null && Arrays.asList(team.getMembers()).contains(member)) {
                System.out.println("["+member+"] is already a member of ["+team.getId()+"].");
                continue;
            }
            inviteeList.add(member);
        }
        
        TeamOpData data = TeamOpData.makeInvite(team.getId(), inviteeList.toArray(new String[0]));
        String result = FeipClient.team(priKey, myFid, null, data, apipClient, null, br);
        if(!Hex.isHexString(result)) System.out.println(result);
        else System.out.println("Invitation sent successfully.");
    }

    private void handleWithdrawInvitation(byte[] priKey, BufferedReader br) {
        Team team = searchAndChooseTeam(apipClient, br);
        if(team == null) return;
        
        String[] memberList = Inputer.inputStringArray(br, "Input FIDs to withdraw invitation (separated by comma):", 0);
        if(memberList == null || memberList.length == 0) return;

        List<String> withdrawList = new ArrayList<>();
        for(String member : memberList) {
            if(team.getInvitees() == null || !Arrays.asList(team.getInvitees()).contains(member)) {
                System.out.println("["+member+"] is not in the invitation list of ["+team.getId()+"].");
                continue;
            }
            withdrawList.add(member);
        }
        
        TeamOpData data = TeamOpData.makeWithdrawInvitation(team.getId(), withdrawList.toArray(new String[0]));
        String result = FeipClient.team(priKey, myFid, null, data, apipClient, null, br);
        if(!Hex.isHexString(result)) System.out.println(result);
        else System.out.println("Invitation withdrawn successfully.");
    }

    private void handleDismissMembers(byte[] priKey, BufferedReader br) {
        Team team = searchAndChooseTeam(apipClient, br);
        if(team == null) return;
        
        String[] memberList = Inputer.inputStringArray(br, "Input FIDs to dismiss (separated by comma):", 0);
        if(memberList == null || memberList.length == 0) return;

        List<String> dismissList = new ArrayList<>();
        for(String member : memberList) {
            if(team.getMembers() == null || !Arrays.asList(team.getMembers()).contains(member)) {
                System.out.println("["+member+"] is not a member of ["+team.getId()+"].");
                continue;
            }
            dismissList.add(member);
        }
        
        TeamOpData data = TeamOpData.makeDismiss(team.getId(), dismissList.toArray(new String[0]));
        String result = FeipClient.team(priKey, myFid, null, data, apipClient, null, br);
        if(!Hex.isHexString(result)) System.out.println(result);
        else System.out.println("Members dismissed successfully.");
    }

    private void handleAppointMembers(byte[] priKey, BufferedReader br) {
        Team team = searchAndChooseTeam(apipClient, br);
        if(team == null) return;
        
        String[] memberList = Inputer.inputStringArray(br, "Input FIDs to appoint (separated by comma):",0);
        if(memberList == null || memberList.length == 0) return;

        List<String> appointList = new ArrayList<>();
        for(String member : memberList) {
            if(team.getMembers() == null || !Arrays.asList(team.getMembers()).contains(member)) {
                System.out.println("["+member+"] is not a member of ["+team.getId()+"].");
                continue;
            }
            appointList.add(member);
        }

        TeamOpData data = TeamOpData.makeAppoint(team.getId(), appointList.toArray(new String[0]));
        String result = FeipClient.team(priKey, myFid, null, data, apipClient, null, br);
        if(!Hex.isHexString(result)) System.out.println(result);
        else System.out.println("Members appointed successfully.");
    }

    private void handleCancelAppointment(byte[] priKey, BufferedReader br) {
        Team team = searchAndChooseTeam(apipClient, br);
        if(team == null) return;
        
        String[] memberList = Inputer.inputStringArray(br, "Input FIDs to cancel appointment (separated by comma):", 0);
        if(memberList == null || memberList.length == 0) return;
        
        List<String> cancelList = new ArrayList<>();
        for(String member : memberList) {
            if(team.getMembers() == null || !Arrays.asList(team.getMembers()).contains(member)) {
                System.out.println("["+member+"] is not a manager of ["+team.getId()+"].");
                continue;
            }
            cancelList.add(member);
        }
        
        TeamOpData data = TeamOpData.makeCancelAppointment(team.getId(), cancelList.toArray(new String[0]));
        String result = FeipClient.team(priKey, myFid, null, data, apipClient, null, br);
        if(!Hex.isHexString(result)) System.out.println(result);
        else System.out.println("Appointments cancelled successfully.");
    }

    private void handleRateMember(byte[] priKey, BufferedReader br) {
        Team team = searchAndChooseTeam(apipClient, br);
        if(team == null) return;
        
        String tid = team.getId();
        Integer rate = Inputer.inputInteger(br, "Input rating (0-5):", 0, 5);
        if(rate == null) return;
        
        TeamOpData data = TeamOpData.makeRate(tid, rate);
        String result = FeipClient.team(priKey, myFid, null, data, apipClient, null, br);
        if(!Hex.isHexString(result)) System.out.println(result);
        else System.out.println("Rating submitted successfully.");
    }

    // 8. Team Transfer and Take-over Methods
    private void handleTransferTeam(byte[] priKey, BufferedReader br) {
        Team team = searchAndChooseTeam(apipClient, br);
        if(team == null) {
            System.out.println("No such team.");
            return;
        }
        if(!team.getOwner().equals(myFid)) {
            System.out.println("You are not the owner of this team.");
            return;
        }

        String transferee = Inputer.inputString(br, "Input the FID you want to transfer the team to:");
        if(transferee == null || transferee.isEmpty()) return;
        
        TeamOpData data = TeamOpData.makeTransfer(team.getId(), transferee);
        String result = FeipClient.team(priKey, myFid, null, data, apipClient, null, br);
        if(!Hex.isHexString(result)) System.out.println(result);
        else System.out.println("Team transferred successfully.");
    }

    private void handleTakeOverTeam(byte[] priKey, BufferedReader br) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.TRANSFEREE).addNewValues(myFid);
        List<Team> teamList = apipClient.teamSearch(fcdsl, null, null);
        if(teamList == null || teamList.isEmpty()) {
            System.out.println("No any team you are transferee of.");
            return;
        }

        List<String> tidList = new ArrayList<>();   
        for(Team team : teamList) {
            tidList.add(team.getId());
        }

        Map<String, Team> teamMap = apipClient.teamOtherPersons(null, null, tidList.toArray(new String[0]));
        if(teamMap == null || teamMap.isEmpty()) {
            System.out.println("No such team.");
            return;
        }
        
        for(Team team : teamMap.values()) {
            if(!Inputer.askIfYes(br, "Take over team: " + team.getId() + " - " + team.getStdName() + "?")) continue;
            String consensusId = team.getConsensusId();
            if(consensusId == null || consensusId.isEmpty()) {
                System.out.println("No consensus ID for this team: " + team.getId() + " - " + team.getStdName());
                continue;
            }

            TeamOpData data = TeamOpData.makeTakeOver(team.getId(), consensusId);
            String result = FeipClient.team(priKey, myFid, null, data, apipClient, null, br);
            if(!Hex.isHexString(result)) System.out.println(result);
            else System.out.println("Team taken over successfully.");
            if(!Inputer.askIfYes(br, "Take over another team?")) break;
        }
    }

    // 9. Utility Methods
    public List<Team> searchTeams(String searchTerm, ApipClient apipClient) {
        Fcdsl fcdsl = new Fcdsl();
        if(searchTerm != null && !"".equals(searchTerm))
            fcdsl.addNewQuery()
                 .addNewPart()
                 .addNewFields(FieldNames.TID, FieldNames.STD_NAME, FieldNames.LOCAL_NAMES, FieldNames.ACCOUNTS, FieldNames.DESC, FieldNames.MEMBERS)
                 .addNewValue(searchTerm);
        return apipClient.teamSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
    }

    public List<Team> chooseTeamList(List<Team> teamList, BufferedReader br) {
        if(teamList == null || teamList.isEmpty()) return null;
        return Inputer.chooseMultiFromListShowingMultiField(
            teamList, 
            Arrays.asList(FieldNames.TID, FieldNames.STD_NAME), 
            Arrays.asList(15, 15), 
            "Choose teams:", 
            1, 
            br
        );
    }

    public Team searchAndChooseTeam(ApipClient apipClient, BufferedReader br) {
        while(true) {
            String input = Inputer.inputString(br, "Input the hint of the team you want to find:");
            if(input == null || "".equals(input)) return null;
            
            List<Team> result = searchTeams(input, apipClient);
            
            if(result == null || result.isEmpty()) {
                System.out.println("No such team. Try again.");
                continue;
            }
            
            Team chosenTeam = Inputer.chooseOneFromList(result, FieldNames.STD_NAME, "Choose the team:", br);
            if(chosenTeam == null) {
                System.out.println("Try again.");
                continue;
            }
            return chosenTeam;
        }
    }

    public void showTeamList(List<Team> teamList, BufferedReader br) {
        if(teamList == null || teamList.isEmpty()) return;
        String title = "Teams";
        String[] fields = { FieldNames.TID, FieldNames.MEMBER_NUM, FieldNames.STD_NAME, FieldNames.DESC };
        int[] widths = { 15, 10, 20, 25 };
        List<List<Object>> valueListList = new ArrayList<>();
        for(Team team : teamList) {
            List<Object> valueList = new ArrayList<>();
            valueList.add(StringTools.omitMiddle(team.getId(), 15));
            valueList.add(team.getMemberNum());
            valueList.add(StringTools.omitMiddle(team.getStdName(), 20));
            valueList.add(StringTools.omitMiddle(team.getDesc(), 25));
            valueListList.add(valueList);
        }
        Shower.showDataTable(title, fields, widths, valueListList, 0, true);
    }

    public List<String> getTeamMembers(String tid, ApipClient apipClient) {
        Map<String, String[]> result = apipClient.teamMembers(RequestMethod.POST, AuthType.FC_SIGN_BODY, tid);
        if(result == null || result.isEmpty()) return null;
        return Arrays.asList(result.get(tid));
    }

    public Boolean isOwner(String fid, String tid) {
        if(fid == null || tid == null) return false;
        Team team = Team.fromJson(new String(teamDB.get(Hex.fromHex(tid))));
        if(team == null) return false;
        return fid.equals(team.getOwner());
    }

    public Boolean isMemberOf(String fid,String gid,ApipClient apipClient){
        List<String> result = getTeamMembers(gid,apipClient);
        return result.contains(fid);
    }
    public List<Team> pullTeamList(String myFid, Long sinceHeight, ApipClient apipClient, @Nullable BufferedReader br) {
        int size = DEFAULT_SIZE;
        List<Team> resultList;
        List<String> last = new ArrayList<>();
        while(true) {
            resultList = apipClient.myTeams(myFid, sinceHeight, size, last, RequestMethod.POST, AuthType.FC_SIGN_BODY);
            if(resultList == null) return null;
            if(resultList.size() < size) break;
            if(br != null && !Inputer.askIfYes(br, "Get more teams?")) break;
        }
        return resultList;
    }

    public List<Team> pullLocalTeamList(boolean choose, BufferedReader br) {
        List<Team> resultTeamList = new ArrayList<>();
        int size = DEFAULT_SIZE;
        int offset = 0;

        while (true) {
            List<byte[]> batchTeams = teamDB.getValuesBatch(offset, size);
            if (batchTeams == null || batchTeams.isEmpty()) break;

            List<Team> teamBatch = new ArrayList<>();
            for (byte[] teamBytes : batchTeams) {
                Team team = Team.fromJson(new String(teamBytes));
                if (team != null) {
                    teamBatch.add(team);
                }
            }

            if (teamBatch.isEmpty()) break;

            List<Team> chosenTeamList;
            if (choose) {
                chosenTeamList = Inputer.chooseMultiFromListShowingMultiField(
                    teamBatch,
                    Arrays.asList(FieldNames.TID, FieldNames.STD_NAME),
                    Arrays.asList(11, 21),
                    "Choose teams:",
                    1,
                    br
                );
            } else {
                chosenTeamList = teamBatch;
            }

            resultTeamList.addAll(chosenTeamList);

            if (batchTeams.size() < size) break;
            if (br != null && !Inputer.askIfYes(br, "Get more teams?")) break;

            offset += size;
        }

        if (resultTeamList.isEmpty()) return null;
        return resultTeamList;
    }

    private void handleListTeams(byte[] priKey, BufferedReader br) {
        List<Team> chosenTeamList = pullLocalTeamList(true, br);
        if(chosenTeamList == null || chosenTeamList.isEmpty()) return;
        opTeamList(chosenTeamList, true, priKey, myFid, apipClient, br);
    }

    private void handleCreateTeam(byte[] priKey, BufferedReader br) {
        String stdName = Inputer.inputString(br, "Input the standard name:");
        String consensusId = Inputer.inputString(br, "Input the consensus ID:");
        String desc = Inputer.inputString(br, "Input the description:");
        String[] localNames = Inputer.inputStringArray(br, "Input the local names (separated by comma):", 0);
        String[] waiters = Inputer.inputStringArray(br, "Input the waiters (separated by comma):", 0);
        String[] accounts = Inputer.inputStringArray(br, "Input the accounts (separated by comma):", 0);
        if(!Inputer.askIfYes(br, "Name:" + stdName + "\nConsensus ID:" + consensusId + 
                "\nDescription:" + desc + "\nCreate it?")) return;
        String createResult = createTeam(priKey, myFid, null, stdName, consensusId, localNames, waiters, accounts, desc, apipClient, null);
        if(!Hex.isHexString(createResult)) System.out.println(createResult);
        else System.out.println("Work done. Check teams a few minutes later.");
    }

    private void handleFindTeams(byte[] priKey, BufferedReader br) {
        String searchString = Inputer.inputString(br, "Input the search string. Enter to do default searching:");
        List<Team> searchResult = searchTeams(searchString, apipClient);
        if(searchResult == null || searchResult.isEmpty()) return;
        List<Team> chosenTeamList = chooseTeamList(searchResult, br);
        opTeamList(chosenTeamList, false, priKey, myFid, apipClient, br);
    }

    private void handleLeaveTeams(byte[] priKey, BufferedReader br) {
        List<Team> chosenTeamList = pullLocalTeamList(true, br);
        if(chosenTeamList == null || chosenTeamList.isEmpty()) return;
        List<String> idList = new ArrayList<>();
        for(Team team : chosenTeamList) idList.add(team.getId());
        showTeamList(chosenTeamList, br);
        if(Inputer.askIfYes(br, "Leave all teams?")) {
            String leaveResult = leaveTeams(priKey, myFid, null, idList, apipClient, null, br);
            if(!Hex.isHexString(leaveResult)) System.out.println(leaveResult);
            System.out.println("Work done. Check teams a few minutes later.");
        }
    }

    private void handleJoinTeam(byte[] priKey, BufferedReader br) {
        Team chosenTeam = searchAndChooseTeam(apipClient, br);
        if(chosenTeam == null) return;
        String tid = chosenTeam.getId();
        String consensusId = Inputer.inputString(br, "Input consensus ID:");
        String joinResult = joinTeam(priKey, myFid, null, tid, consensusId, apipClient, null);
        if(!Hex.isHexString(joinResult)) System.out.println(joinResult);
        else System.out.println("Work done. Check teams a few minutes later.");
    }

    private void opTeamList(List<Team> chosenTeamList, boolean isMyTeamList,
            byte[] priKey, String offLineFid, ApipClient apipClient, BufferedReader br) {
        String[] options;
        if(isMyTeamList)
            options = new String[]{"view", "Leave", "Members", "Manage Members"};
        else 
            options = new String[]{"view", "join", "Leave", "Members","Manage Members","rate"};
        
        while(true) {
            String subOp = Inputer.chooseOne(options, null, "What to do?", br);
            if(subOp == null || "".equals(subOp)) return;
            switch (subOp) {
                case "view" -> viewTeams(chosenTeamList, br);
                case "join" -> joinTeams(chosenTeamList, isMyTeamList, priKey, offLineFid, apipClient, br);
                case "Leave" -> leaveTeams(chosenTeamList, isMyTeamList, priKey, offLineFid, apipClient, br);
                case "Members" -> viewTeamMembers(chosenTeamList, apipClient, br);
                case "Manage Members" -> handleManageMembers(priKey, br);
                case "rate" -> handleRateMember(priKey, br);

                default -> {return;}
            }
        }
    }

    private void viewTeams(List<Team> chosenTeamList, BufferedReader br) {
        while (true) {
            List<Team> viewTeamList = chooseTeamList(chosenTeamList, br);
            System.out.println(JsonTools.toNiceJson(viewTeamList));
            if(Inputer.askIfYes(br, "Continue?")) continue;
            else break;
        }
    }

    private void viewTeamMembers(List<Team> chosenTeamList, ApipClient apipClient, BufferedReader br) {
        for(Team team : chosenTeamList) {
            List<String> members = getTeamMembers(team.getId(), apipClient);
            System.out.println("Members of [" + team.getStdName() + "]:");
            System.out.println(members);
            System.out.println();
            if(br != null) Menu.anyKeyToContinue(br);
        }
    }

    public Team getTeamInfo(String tid, ApipClient apipClient) {
        // First check local database
        byte[] teamBytes = teamDB.get(Hex.fromHex(tid));
        if (teamBytes != null) {
            Team team = Team.fromJson(new String(teamBytes));
            if (team != null) {
                return team;
            }
        }

        // If not found locally, fetch from API
        Map<String, Team> result = apipClient.teamByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, tid);
        if (result == null || result.isEmpty()) return null;
        
        // Cache the result before returning
        Team team = result.get(tid);
        if (team != null) {
            teamDB.put(Hex.fromHex(tid), team.toJson().getBytes());
        }
        
        return team;
    }
}
