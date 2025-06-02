package handlers;

import data.apipData.Fcdsl;
import ui.Inputer;
import ui.Menu;
import config.Settings;
import ui.Shower;
import clients.ApipClient;
import clients.FeipClient;
import constants.FieldNames;
import constants.Values;
import core.crypto.Decryptor;
import db.LocalDB;
import data.fchData.SendTo;
import data.feipData.Group;
import data.feipData.GroupOpData;
import data.feipData.Service;
import utils.Hex;
import utils.JsonUtils;
import utils.StringUtils;
import utils.http.AuthType;
import utils.http.RequestMethod;
import clients.NaSaClient.NaSaRpcClient;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupManager extends Manager<Group> {
    // 1. Constants
    public static final int DEFAULT_SIZE = 50;

    // 2. Instance Variables
    private final BufferedReader br;
    private final String myFid;
    private final ApipClient apipClient;
    // private final String sid;
    private final byte[] symKey;
    private final String myPrikeyCipher;
    private final Map<String, Long> lastTimeMap;

    // 3. Constructor
    public GroupManager(Settings settings) {
        super(settings, ManagerType.GROUP, LocalDB.SortType.UPDATE_ORDER, Group.class, true, true);
        this.apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);
        this.myFid = settings.getMainFid();
        this.symKey = settings.getSymkey();
        this.myPrikeyCipher = settings.getMyPrikeyCipher();
        this.br = settings.getBr(); 
        this.lastTimeMap = new HashMap<>();
    }   

    // 4. Public Methods - Main Interface
    public void menu(BufferedReader br, boolean isRootMenu) {
        byte[] priKey = Decryptor.decryptPrikey(myPrikeyCipher, symKey);
        Menu menu = newMenu("Group", isRootMenu);
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
            localDB.put(group.getId(), group);
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
        Group group = localDB.get(gid);
        if(group == null) return false;
        return Arrays.asList(group.getNamers()).contains(fid);
    }

    public boolean isLastNamer(String fid, String gid) {
        if(fid == null || gid == null) return false;
        Group group = localDB.get(gid);
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
        long offset = 0;

        while (true) {
            // Get groups in batches using localDB
            List<Group> batchGroups = localDB.getList(size, null, offset, false, null, null, true, true);
            if (batchGroups == null || batchGroups.isEmpty()) break;

            // Handle selection if required
            List<Group> chosenGroupList;
            if (choose) {
                chosenGroupList = Inputer.chooseMultiFromListShowingMultiField(
                        batchGroups,
                        Arrays.asList(FieldNames.GID, FieldNames.NAME),
                        Arrays.asList(11, 21),
                        "Choose groups:",
                        1,
                        br
                );
            } else {
                chosenGroupList = batchGroups;
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
        opItems(chosenGroupMaskList, true, priKey, myFid, apipClient, br);
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
        opItems(chosenGroupMaskList, false, priKey, myFid, apipClient, br);
    }
    private void handleLeaveGroups(byte[] priKey, BufferedReader br) {
        List<Group> chosenGroupList = pullLocalGroupList(true, br);
        if(chosenGroupList==null || chosenGroupList.isEmpty()) return;
        List<String> idList = new ArrayList<>();
        for(Group group : chosenGroupList) idList.add(group.getId());
        showItems(chosenGroupList,br);
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
    public String opItems(byte[] priKey, String offLineFid, List<SendTo> sendToList, GroupOpData data, ApipClient apipClient, NaSaRpcClient nasaClient, BufferedReader br){
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
        showItems(chosenGroupList, br);
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
    public void opItems(List<Group> chosenGroupList, boolean isMyGroupList,
            byte[] priKey, String offLineFid, ApipClient apipClient, BufferedReader br) {
        String[] options;
        if(isMyGroupList)
            options = new String[]{"view", "Leave", "Update", "Members"};
        else options = new String[]{"view", "join","Leave","Update","Members"};
        while(true) {
            String subOp = Inputer.chooseOne(options,null,"What to do?",br);
            if(subOp==null || "".equals(subOp))return;
            switch (subOp) {
                case "view" -> viewItems(chosenGroupList, br);
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
                 .addNewFields(FieldNames.GID, FieldNames.NAME, Values.DESC,FieldNames.MEMBERS)
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
    public void showItems(List<Group> groupList, BufferedReader br) {
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
        Shower.showOrChooseList(title, fields, widths, valueListList, null);
    }
    public void viewItems(List<Group> chosenGroupMaskList, BufferedReader br) {
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
