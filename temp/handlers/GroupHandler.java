package handlers;

import apip.apipData.Fcdsl;
import appTools.Inputer;
import appTools.Menu;
import appTools.Settings;
import appTools.Shower;
import clients.ApipClient;
import clients.FeipClient;
import constants.FieldNames;
import crypto.Decryptor;
import fcData.PersistentSequenceMap;
import fch.fchData.SendTo;
import feip.feipData.Group;
import feip.feipData.GroupOpData;
import feip.feipData.Service;
import nasa.NaSaRpcClient;
import utils.Hex;
import utils.JsonUtils;
import utils.StringUtils;
import utils.http.AuthType;
import utils.http.RequestMethod;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.util.*;

public class GroupHandler extends Handler {
    // 1. Constants
    public static final int DEFAULT_SIZE = 50;

    // 2. Instance Variables
    private final BufferedReader br;
    private final String myFid;
    private final ApipClient apipClient;
    // private final String sid;
    private final byte[] symKey;
    private final String myPriKeyCipher;
    private final PersistentSequenceMap groupDB;
    private final Map<String, Long> lastTimeMap;

    // 3. Constructor
    public GroupHandler(String myFid, ApipClient apipClient, byte[] symKey,
                        String myPriKeyCipher, Map<String, Long> lastTimeMap, String dbPath, BufferedReader br) {
        this.myFid = myFid;
        this.apipClient = apipClient;
        // this.sid = sid;
        this.symKey = symKey;
        this.myPriKeyCipher = myPriKeyCipher;
        this.lastTimeMap = lastTimeMap;
        this.groupDB = new PersistentSequenceMap(myFid, null, FieldNames.GROUP, dbPath);
        this.br = br;
    }

    public GroupHandler(Settings settings){
        this.apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);
        this.myFid = settings.getMainFid();
        this.symKey = settings.getSymKey();
        this.myPriKeyCipher = settings.getMyPriKeyCipher();
        this.br = settings.getBr(); 
        this.lastTimeMap = new HashMap<>();
        this.groupDB = new PersistentSequenceMap(myFid, null, FieldNames.GROUP, settings.getDbDir());
    }   

    // 4. Public Methods - Main Interface
    public void menu() {
        byte[] priKey = Decryptor.decryptPriKey(myPriKeyCipher, symKey);
        Menu menu = new Menu("Group", this::close);
        menu.add("List", () -> handleListGroups(priKey, br));
        menu.add("Check", () -> checkGroup(br));
        menu.add("Create", () -> handleCreateGroup(priKey, br));
        menu.add("Find", () -> handleFindGroups(priKey, br));
        menu.add("Leave", () -> handleLeaveGroups(priKey, br));
        menu.add("Join", () -> handleJoinGroup(priKey, br));
        menu.showAndSelect(br);
    }
    public void checkGroup(BufferedReader br) {
        List<Group> groupList;
        Long lastTime = lastTimeMap.get(FieldNames.GROUP);
        if (lastTime == null) lastTime = 0L;
        groupList = pullGroupList(myFid, lastTime, apipClient, br);
        if(groupList==null){
            System.out.println("No updated group found.");
            return;
        }
        for(Group group : groupList){
            groupDB.put(Hex.fromHex(group.getId()), group.toJson().getBytes());
        }

        if (!groupList.isEmpty()) {
            lastTimeMap.put(FieldNames.GROUP, groupList.get(0).getLastHeight());
            JsonUtils.saveToJsonFile(lastTimeMap, myFid, null, FieldNames.LAST_TIME, false);
        }

        System.out.println("Found " + groupList.size() + " updated groups.");
    }
    public Group getGroupInfo(String gid, ApipClient apipClient){
        Map<String, Group> result = apipClient.groupByIds(RequestMethod.POST,AuthType.FC_SIGN_BODY,gid);
        if(result==null || result.isEmpty())return null;
        return result.get(gid);
    }
    public List<String> getGroupMembers(String gid,ApipClient apipClient){
        Map<String, String[]> result = apipClient.groupMembers(RequestMethod.POST,AuthType.FC_SIGN_BODY,gid);
        if(result==null || result.isEmpty())return null;
        return Arrays.asList(result.get(gid));
    }

    public boolean isMemberOf(String fid,String gid,ApipClient apipClient){
        List<String> result = getGroupMembers(gid,apipClient);
        return result.contains(fid);
    }

    public boolean isNamer(String fid, String gid) {
        if(fid == null || gid == null) return false;
        Group group = Group.fromJson(new String(groupDB.get(Hex.fromHex(gid))));
        if(group == null) return false;
        return Arrays.asList(group.getNamers()).contains(fid);
    }

    public boolean isLastNamer(String fid, String gid) {
        if(fid == null || gid == null) return false;
        Group group = Group.fromJson(new String(groupDB.get(Hex.fromHex(gid))));
        if(group == null) return false;
        List<String> namers = Arrays.asList(group.getNamers());
        return namers.get(namers.size()-1).equals(fid);
    }

    public List<Group> pullGroupList(String myFid, Long sinceHeight, ApipClient apipClient, @Nullable BufferedReader br) {
        int size = DEFAULT_SIZE;
        List<Group> resultList;
        List<String> last = new ArrayList<>();
        while(true){
            resultList = apipClient.myGroups(myFid,sinceHeight,size,last,RequestMethod.POST,AuthType.FC_SIGN_BODY);
            if(resultList==null)return null;
            if(resultList.size()<size)break;
            if(br!=null && !Inputer.askIfYes(br,"Get more groups?"))break;
        }
        return resultList;
    }

    public List<Group> pullLocalGroupList(boolean choose, BufferedReader br) {
        List<Group> resultGroupList = new ArrayList<>();
        int size = DEFAULT_SIZE;
        int offset = 0;

        while (true) {
            // Get groups in batches
            List<byte[]> batchGroups = groupDB.getValuesBatch(offset, size);
            if (batchGroups == null || batchGroups.isEmpty()) break;

            // Convert byte arrays to Group objects
            List<Group> groupBatch = new ArrayList<>();
            for (byte[] groupBytes : batchGroups) {
                Group group = Group.fromJson(new String(groupBytes));
                if (group != null) {
                    groupBatch.add(group);
                }
            }

            if (groupBatch.isEmpty()) break;

            // Handle selection if required
            List<Group> chosenGroupList;
            if (choose) {
                chosenGroupList = Inputer.chooseMultiFromListShowingMultiField(
                        groupBatch,
                        Arrays.asList(FieldNames.GID, FieldNames.NAME),
                        Arrays.asList(11, 21),
                        "Choose groups:",
                        1,
                        br
                );
            } else {
                chosenGroupList = groupBatch;
            }

            resultGroupList.addAll(chosenGroupList);

            if (batchGroups.size() < size) break;
            if (br != null && !Inputer.askIfYes(br, "Get more groups?")) break;

            offset += size;
        }

        if (resultGroupList.isEmpty()) return null;
        return resultGroupList;
    }

    // 5. Private Methods - Menu Handlers
    private void handleListGroups(byte[] priKey, BufferedReader br) {
        List<Group> chosenGroupMaskList = pullLocalGroupList(true, br);
        if(chosenGroupMaskList==null || chosenGroupMaskList.isEmpty()) return;
        opGroupList(chosenGroupMaskList, true, priKey, myFid, apipClient, br);
    }
    private void handleCreateGroup(byte[] priKey, BufferedReader br) {
        String name = Inputer.inputString(br,"Input the name:");
        String description = Inputer.inputString(br,"Input the description:");
        if(!Inputer.askIfYes(br,"Name:"+name+"\nDescription:"+description+"\nCreate it?")) return;
        String createResult = createGroup(priKey, myFid, null, name, description, apipClient, null);
        if(!Hex.isHexString(createResult))System.out.println(createResult);
        else System.out.println("Work done. Check groups a few minutes later.");
    }
    private void handleFindGroups(byte[] priKey, BufferedReader br) {
        String searchString = Inputer.inputString(br,"Input the search string. Enter to do default searching:");
        List<Group> searchResult = searchGroups(searchString, apipClient);
        if(searchResult==null || searchResult.isEmpty()) return;
        List<Group> chosenGroupMaskList = chooseGroupList(searchResult, br);
        opGroupList(chosenGroupMaskList, false, priKey, myFid, apipClient, br);
    }
    private void handleLeaveGroups(byte[] priKey, BufferedReader br) {
        List<Group> chosenGroupList = pullLocalGroupList(true, br);
        if(chosenGroupList==null || chosenGroupList.isEmpty()) return;
        List<String> idList = new ArrayList<>();
        for(Group group : chosenGroupList) idList.add(group.getId());
        showGroupList(chosenGroupList,br);
        if(Inputer.askIfYes(br,"Leave all groups?")) {
            String leaveResult = leaveGroups(priKey, myFid, null, idList, apipClient, null);
            if(!Hex.isHexString(leaveResult))System.out.println(leaveResult);
            System.out.println("Work done. Check groups a few minutes later.");
        }
    }
    private void handleJoinGroup(byte[] priKey, BufferedReader br) {
        Group chosenGroup = searchAndChooseGroup(apipClient, br);
        if(chosenGroup==null) return;
        String gid = chosenGroup.getId();
        String joinResult = joinGroup(priKey, myFid, null, gid, apipClient, null);
        if(!Hex.isHexString(joinResult))System.out.println(joinResult);
        else System.out.println("Work done. Check groups a few minutes later.");
    }

    // 6. Group Operation Methods
    public String createGroup(byte[] priKey, String offLineFid, List<SendTo> sendToList,
            String name, String desc, ApipClient apipClient, NaSaRpcClient nasaClient) {
        GroupOpData data = GroupOpData.makeCreate(name, desc);
        return FeipClient.group(priKey, offLineFid, sendToList, null, data, apipClient, nasaClient, null);
    }
    public String updateGroup(byte[] priKey, String offLineFid, List<SendTo> sendToList,
                                     Long cd, NaSaRpcClient nasaClient, String gid, String name, String desc, ApipClient apipClient) {
        GroupOpData data = GroupOpData.makeUpdate(gid, name, desc);
        return FeipClient.group(priKey, offLineFid, sendToList, cd, data, apipClient, nasaClient, null);
    }
    public String joinGroup(byte[] priKey, String offLineFid, List<SendTo> sendToList,
            String gid, ApipClient apipClient, NaSaRpcClient nasaClient) {
        GroupOpData data = GroupOpData.makeJoin(gid);
        return FeipClient.group(priKey, offLineFid, sendToList, null, data, apipClient, nasaClient, null);
    }
    public String leaveGroups(byte[] priKey, String offLineFid, List<SendTo> sendToList,
            List<String> gids, ApipClient apipClient, NaSaRpcClient nasaClient) {
        GroupOpData data = GroupOpData.makeLeave(gids);
        return FeipClient.group(priKey, offLineFid, sendToList, null, data, apipClient, nasaClient, null);
    }
    public String opGroup(byte[] priKey, String offLineFid, List<SendTo> sendToList, GroupOpData data, ApipClient apipClient, NaSaRpcClient nasaClient, BufferedReader br){
        return FeipClient.group(priKey, offLineFid, sendToList, null, data, apipClient, nasaClient, br);
    }

    // 7. Group List Operation Methods
    public void joinGroups(List<Group> chosenGroupList, boolean isMyGroupList,
            byte[] priKey, String offLineFid, ApipClient apipClient, BufferedReader br) {
        for(Group group : chosenGroupList) {
            if(isMyGroupList) {
                System.out.println("You have already joined this group: ["+group.getId()+"]");
                continue;
            } else {
                System.out.println("Group: "+ StringUtils.omitMiddle(group.getId(), 15)+" - "+group.getName());
                if(!Inputer.askIfYes(br,"Join this group?")) continue;
                List<String> members = getGroupMembers(group.getId(),apipClient);
                if(members!=null && members.contains(myFid)) {
                    System.out.println("You are already a member of ["+group.getId()+"]. ");
                    continue;
                }
            }
            String joinResult = joinGroup(priKey,offLineFid,null,group.getId(),apipClient,null);
            if(!Hex.isHexString(joinResult))System.out.println(joinResult);
            else System.out.println("Joined ["+group.getName()+"].");
            if(!Inputer.askIfYes(br,"Join next group?")) break;
        }
        System.out.println("Work done. Check groups a few minutes later.");
    }
    public void leaveGroups(List<Group> chosenGroupList, boolean isMyGroupList,
            byte[] priKey, String offLineFid, ApipClient apipClient, BufferedReader br) {
        showGroupList(chosenGroupList, br);
        if (!Inputer.askIfYes(br, "Leave all these groups?")) return;

        List<String> leaveIdList = new ArrayList<>();
        if (!isMyGroupList) {
            for (Group group : chosenGroupList) {
                List<String> members = getGroupMembers(group.getId(), apipClient);
                if (members != null && !members.contains(myFid)) {
                    System.out.println("You are not a member of [" + group.getId() + "].");
                    continue;
                }
                leaveIdList.add(group.getId());
            }
        }

        String leaveResult = leaveGroups(priKey, offLineFid, null, leaveIdList, apipClient, null);
        if(!Hex.isHexString(leaveResult))System.out.println(leaveResult);
    }
    public void updateGroups(List<Group> chosenGroupList, boolean isMyGroupList,
            byte[] priKey, String offLineFid, ApipClient apipClient, BufferedReader br) {
        for(Group group : chosenGroupList) {
            System.out.println("Group: "+ StringUtils.omitMiddle(group.getId(), 15)+" - "+group.getName());
            if(!Inputer.askIfYes(br,"Update this group?")) continue;
            if(!isMyGroupList) {
                List<String> members = getGroupMembers(group.getId(), apipClient);
                if(members != null && !members.contains(myFid)) {
                    System.out.println("You are not a member of [" + group.getId() + "]. Join it first.");
                    continue;
                }
            }
            if(Inputer.askIfYes(br, "Update " + group.getId() + "?")) {
                long updateCd = group.getCddToUpdate() + 1;
                System.out.println("Updating [" + group.getId() + "], " + updateCd + " CD to required.");
                if(!Inputer.askIfYes(br, "Update it?")) continue;
                String updateResult = updateGroup(priKey, offLineFid, null, updateCd, null, group.getId(), null, null, apipClient);
                if(!Hex.isHexString(updateResult))System.out.println(updateResult);
                else System.out.println("Updated [" + group.getName() + "].");
            }
            if(!Inputer.askIfYes(br,"Update next group?")) break;
        }
        System.out.println("Work done. Check groups a few minutes later.");
    }
    public void opGroupList(List<Group> chosenGroupList, boolean isMyGroupList,
            byte[] priKey, String offLineFid, ApipClient apipClient, BufferedReader br) {
        String[] options;
        if(isMyGroupList)
            options = new String[]{"view", "Leave", "Update", "Members"};
        else options = new String[]{"view", "join","Leave","Update","Members"};
        while(true) {
            String subOp = Inputer.chooseOne(options,null,"What to do?",br);
            if(subOp==null || "".equals(subOp))return;
            switch (subOp) {
                case "view" -> viewGroups(chosenGroupList, br);
                case "join" -> joinGroups(chosenGroupList, isMyGroupList, priKey, offLineFid, apipClient, br);
                case "Leave" -> leaveGroups(chosenGroupList, isMyGroupList, priKey, offLineFid, apipClient, br);
                case "Update" -> updateGroups(chosenGroupList, isMyGroupList, priKey, offLineFid, apipClient, br);
                case "Members" -> viewGroupMembers(chosenGroupList, apipClient,br);
                default -> {return;}
            }
        }
    }

    // 8. Utility Methods
    public List<Group> searchGroups(String searchTerm, ApipClient apipClient) {
        Fcdsl fcdsl = new Fcdsl();
        if(searchTerm!=null && !"".equals(searchTerm))
            fcdsl.addNewQuery()
                 .addNewPart()
                 .addNewFields(FieldNames.GID, FieldNames.NAME, FieldNames.DESC,FieldNames.MEMBERS)
                 .addNewValue(searchTerm);
        return apipClient.groupSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
    }
    public List<Group> chooseGroupList(List<Group> groupList, BufferedReader br) {
        if(groupList==null || groupList.isEmpty())return null;
        return Inputer.chooseMultiFromListShowingMultiField(groupList, Arrays.asList(FieldNames.GID,FieldNames.NAME), Arrays.asList(15,15), "Choose groups:", 1, br);
    }
    public Group searchAndChooseGroup(ApipClient apipClient, BufferedReader br) {
        while(true) {
            String input = Inputer.inputString(br,"Input the hint of the group you want to join for searching:");
            if(input==null|| "".equals(input))return null;
            
            List<Group> result = searchGroups(input, apipClient);
            
            if(result==null || result.isEmpty()){
                System.out.println("No such group. Try again.");
                continue;
            }
            
            Group chosenGroup = Inputer.chooseOneFromList(result, FieldNames.NAME, "Choose the group to join:", br);
            if(chosenGroup==null){
                System.out.println("Try again.");
                continue;
            }
            return chosenGroup;
        }
    }
    public void showGroupList(List<Group> groupList, BufferedReader br) {
        if(groupList==null || groupList.isEmpty())return;
        String title = "Groups";
        String[] fields = { FieldNames.GID, FieldNames.MEMBER_NUM, FieldNames.TCDD,FieldNames.NAME};
        int[] widths = { 15, 10, 16,20};
        List<List<Object>> valueListList = new ArrayList<>();
        for(Group group : groupList){
            List<Object> valueList = new ArrayList<>();
            valueList.add(
                group.getId()!=null && group.getId().length()>15?
                StringUtils.omitMiddle(group.getId(), 15):group.getId()
            );
            valueList.add(group.getMemberNum());
            valueList.add(group.gettCdd());
            valueList.add(
                    group.getName()!=null && group.getName().length()>20?
                            StringUtils.omitMiddle(group.getName(), 20):group.getName()
            );
            valueListList.add(valueList);
        }
        Shower.showDataTable(title, fields, widths, valueListList, 0, true);
    }
    public void viewGroups(List<Group> chosenGroupMaskList, BufferedReader br) {
        while (true) {
            List<Group> viewGroupList = chooseGroupList(chosenGroupMaskList, br);
            System.out.println(JsonUtils.toNiceJson(viewGroupList));
            if(Inputer.askIfYes(br,"Continue?"))continue;
            else break;
        }
    }
    public void viewGroupMembers(List<Group> chosenGroupMaskList, ApipClient apipClient, @Nullable BufferedReader br) {
        for(Group group : chosenGroupMaskList){
            List<String> members = getGroupMembers(group.getId(),apipClient);
            System.out.println("Members of ["+group.getName()+"]:");
            System.out.println(members);
            System.out.println();
            if(br!=null)Menu.anyKeyToContinue(br);
        }
    }
}
