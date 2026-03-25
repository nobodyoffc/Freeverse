package managers;

import data.apipData.Fcdsl;
import data.fchData.Cash;
import data.feipData.ServiceType;
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
import data.feipData.Square;
import data.feipData.SquareOpData;
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

public class SquareManager extends Manager<Square> {
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
    public SquareManager(Settings settings) {
        super(settings, ManagerType.SQUARE, LocalDB.SortType.UPDATE_ORDER, Square.class, true, true);
        this.apipClient = (ApipClient) settings.getClient(ServiceType.APIP);
        this.myFid = settings.getMainFid();
        this.symKey = settings.getSymkey();
        this.myPrikeyCipher = settings.getMyPrikeyCipher();
        this.br = settings.getBr(); 
        this.lastTimeMap = new HashMap<>();
    }   

    // 4. Public Methods - Main Interface
    public void menu(BufferedReader br, boolean isRootMenu) {
        byte[] priKey = Decryptor.decryptPrikey(myPrikeyCipher, symKey);
        Menu menu = newMenu("Square", isRootMenu);
        menu.add("List", () -> handleListSquares(priKey, br));
        menu.add("Check", () -> checkSquare(br));
        menu.add("Create", () -> handleCreateSquare(priKey, br));
        menu.add("Find", () -> handleFindSquares(priKey, br));
        menu.add("Leave", () -> handleLeaveSquares(priKey, br));
        menu.add("Join", () -> handleJoinSquare(priKey, br));
        menu.showAndSelect(br);
    }
    public void checkSquare(BufferedReader br) {
        List<Square> squareList;
        Long lastTime = lastTimeMap.get(FieldNames.SQUARE);
        if (lastTime == null) lastTime = 0L;
        squareList = pullSquareList(myFid, lastTime, apipClient, br);
        if(squareList==null){
            System.out.println("No updated square found.");
            return;
        }
        for(Square square : squareList){
            localDB.put(square.getId(), square);
        }

        if (!squareList.isEmpty()) {
            lastTimeMap.put(FieldNames.SQUARE, squareList.get(0).getLastHeight());
            JsonUtils.saveToJsonFile(lastTimeMap, myFid, null, FieldNames.LAST_TIME, false);
        }

        System.out.println("Found " + squareList.size() + " updated squares.");
    }
    public Square getSquareInfo(String gid, ApipClient apipClient){
        Map<String, Square> result = apipClient.squareByIds(RequestMethod.POST,AuthType.SYMKEY_ENCRYPT,gid);
        if(result==null || result.isEmpty())return null;
        return result.get(gid);
    }
    public List<String> getSquareMembers(String gid,ApipClient apipClient){
        Map<String, String[]> result = apipClient.squareMembers(RequestMethod.POST,AuthType.SYMKEY_ENCRYPT,gid);
        if(result==null || result.isEmpty())return null;
        return Arrays.asList(result.get(gid));
    }

    public boolean isMemberOf(String fid,String gid,ApipClient apipClient){
        List<String> result = getSquareMembers(gid,apipClient);
        return result.contains(fid);
    }

    public boolean isNamer(String fid, String gid) {
        if(fid == null || gid == null) return false;
        Square square = localDB.get(gid);
        if(square == null) return false;
        return Arrays.asList(square.getNamers()).contains(fid);
    }

    public boolean isLastNamer(String fid, String gid) {
        if(fid == null || gid == null) return false;
        Square square = localDB.get(gid);
        if(square == null) return false;
        List<String> namers = Arrays.asList(square.getNamers());
        return namers.get(namers.size()-1).equals(fid);
    }

    public List<Square> pullSquareList(String myFid, Long sinceHeight, ApipClient apipClient, @Nullable BufferedReader br) {
        int size = DEFAULT_SIZE;
        List<Square> resultList;
        List<String> last = new ArrayList<>();
        while(true){
            resultList = apipClient.mySquares(myFid,sinceHeight,size,last,RequestMethod.POST,AuthType.SYMKEY_ENCRYPT);
            if(resultList==null)return null;
            if(resultList.size()<size)break;
            if(br!=null && !Inputer.askIfYes(br,"Get more squares?"))break;
        }
        return resultList;
    }

    public List<Square> pullLocalSquareList(boolean choose, BufferedReader br) {
        List<Square> resultSquareList = new ArrayList<>();
        int size = DEFAULT_SIZE;
        long offset = 0;

        while (true) {
            // Get squares in batches using localDB
            List<Square> batchSquares = localDB.getList(size, null, offset, false, null, null, true, true);
            if (batchSquares == null || batchSquares.isEmpty()) break;

            // Handle selection if required
            List<Square> chosenSquareList;
            if (choose) {
                chosenSquareList = Inputer.chooseMultiFromListShowingMultiField(
                        batchSquares,
                        Arrays.asList(FieldNames.SQUARE_ID, FieldNames.NAME),
                        Arrays.asList(11, 21),
                        "Choose squares:",
                        1,
                        br
                );
            } else {
                chosenSquareList = batchSquares;
            }

            resultSquareList.addAll(chosenSquareList);

            if (batchSquares.size() < size) break;
            if (br != null && !Inputer.askIfYes(br, "Get more squares?")) break;

            offset += size;
        }

        if (resultSquareList.isEmpty()) return null;
        return resultSquareList;
    }

    // 5. Private Methods - Menu Handlers
    private void handleListSquares(byte[] priKey, BufferedReader br) {
        List<Square> chosenSquareMaskList = pullLocalSquareList(true, br);
        if(chosenSquareMaskList==null || chosenSquareMaskList.isEmpty()) return;
        opItems(chosenSquareMaskList, true, priKey, myFid, apipClient, br);
    }
    private void handleCreateSquare(byte[] priKey, BufferedReader br) {
        String name = Inputer.inputString(br,"Input the name:");
        String description = Inputer.inputString(br,"Input the description:");
        if(!Inputer.askIfYes(br,"Name:"+name+"\nDescription:"+description+"\nCreate it?")) return;
        String createResult = createSquare(priKey, myFid, null, name, description, null, apipClient, null);
        if(!Hex.isHexString(createResult))System.out.println(createResult);
        else System.out.println("Work done. Check squares a few minutes later.");
    }
    private void handleFindSquares(byte[] priKey, BufferedReader br) {
        String searchString = Inputer.inputString(br,"Input the search string. Enter to do default searching:");
        List<Square> searchResult = searchSquares(searchString, apipClient);
        if(searchResult==null || searchResult.isEmpty()) return;
        List<Square> chosenSquareMaskList = chooseSquareList(searchResult, br);
        opItems(chosenSquareMaskList, false, priKey, myFid, apipClient, br);
    }
    private void handleLeaveSquares(byte[] priKey, BufferedReader br) {
        List<Square> chosenSquareList = pullLocalSquareList(true, br);
        if(chosenSquareList==null || chosenSquareList.isEmpty()) return;
        List<String> idList = new ArrayList<>();
        for(Square square : chosenSquareList) idList.add(square.getId());
        showItems(chosenSquareList,br);
        if(Inputer.askIfYes(br,"Leave all squares?")) {
            String leaveResult = leaveSquares(priKey, myFid, null, idList, apipClient, null);
            if(!Hex.isHexString(leaveResult))System.out.println(leaveResult);
            System.out.println("Work done. Check squares a few minutes later.");
        }
    }
    private void handleJoinSquare(byte[] priKey, BufferedReader br) {
        Square chosenSquare = searchAndChooseSquare(apipClient, br);
        if(chosenSquare==null) return;
        String gid = chosenSquare.getId();
        String joinResult = joinSquare(priKey, myFid, null, gid, apipClient, null);
        if(!Hex.isHexString(joinResult))System.out.println(joinResult);
        else System.out.println("Work done. Check squares a few minutes later.");
    }

    // 6. Square Operation Methods
    public String createSquare(byte[] priKey, String offLineFid, List<Cash> sendToList,
            String name, String desc, Map<String, String> home, ApipClient apipClient, NaSaRpcClient nasaClient) {
        SquareOpData data = SquareOpData.makeCreate(name, desc, home);
        return FeipClient.square(priKey, offLineFid, sendToList, null, data, apipClient, nasaClient, null);
    }
    public String updateSquare(byte[] priKey, String offLineFid, List<Cash> sendToList,
                                     Long cd, NaSaRpcClient nasaClient, String gid, String name, String desc, Map<String, String> home, ApipClient apipClient) {
        SquareOpData data = SquareOpData.makeUpdate(gid, name, desc, home);
        return FeipClient.square(priKey, offLineFid, sendToList, cd, data, apipClient, nasaClient, null);
    }
    public String joinSquare(byte[] priKey, String offLineFid, List<Cash> sendToList,
            String gid, ApipClient apipClient, NaSaRpcClient nasaClient) {
        SquareOpData data = SquareOpData.makeJoin(gid);
        return FeipClient.square(priKey, offLineFid, sendToList, null, data, apipClient, nasaClient, null);
    }
    public String leaveSquares(byte[] priKey, String offLineFid, List<Cash> sendToList,
            List<String> gids, ApipClient apipClient, NaSaRpcClient nasaClient) {
        SquareOpData data = SquareOpData.makeLeave(gids);
        return FeipClient.square(priKey, offLineFid, sendToList, null, data, apipClient, nasaClient, null);
    }
    public String opItems(byte[] priKey, String offLineFid, List<Cash> sendToList, SquareOpData data, ApipClient apipClient, NaSaRpcClient nasaClient, BufferedReader br){
        return FeipClient.square(priKey, offLineFid, sendToList, null, data, apipClient, nasaClient, br);
    }

    // 7. Square List Operation Methods
    public void joinSquares(List<Square> chosenSquareList, boolean isMySquareList,
            byte[] priKey, String offLineFid, ApipClient apipClient, BufferedReader br) {
        for(Square square : chosenSquareList) {
            if(isMySquareList) {
                System.out.println("You have already joined this square: ["+square.getId()+"]");
                continue;
            } else {
                System.out.println("Square: "+ StringUtils.omitMiddle(square.getId(), 15)+" - "+square.getName());
                if(!Inputer.askIfYes(br,"Join this square?")) continue;
                List<String> members = getSquareMembers(square.getId(),apipClient);
                if(members!=null && members.contains(myFid)) {
                    System.out.println("You are already a member of ["+square.getId()+"]. ");
                    continue;
                }
            }
            String joinResult = joinSquare(priKey,offLineFid,null,square.getId(),apipClient,null);
            if(!Hex.isHexString(joinResult))System.out.println(joinResult);
            else System.out.println("Joined ["+square.getName()+"].");
            if(!Inputer.askIfYes(br,"Join next square?")) break;
        }
        System.out.println("Work done. Check squares a few minutes later.");
    }
    public void leaveSquares(List<Square> chosenSquareList, boolean isMySquareList,
            byte[] priKey, String offLineFid, ApipClient apipClient, BufferedReader br) {
        showItems(chosenSquareList, br);
        if (!Inputer.askIfYes(br, "Leave all these squares?")) return;

        List<String> leaveIdList = new ArrayList<>();
        if (!isMySquareList) {
            for (Square square : chosenSquareList) {
                List<String> members = getSquareMembers(square.getId(), apipClient);
                if (members != null && !members.contains(myFid)) {
                    System.out.println("You are not a member of [" + square.getId() + "].");
                    continue;
                }
                leaveIdList.add(square.getId());
            }
        }

        String leaveResult = leaveSquares(priKey, offLineFid, null, leaveIdList, apipClient, null);
        if(!Hex.isHexString(leaveResult))System.out.println(leaveResult);
    }
    public void updateSquares(List<Square> chosenSquareList, boolean isMySquareList,
            byte[] priKey, String offLineFid, ApipClient apipClient, BufferedReader br) {
        for(Square square : chosenSquareList) {
            System.out.println("Square: "+ StringUtils.omitMiddle(square.getId(), 15)+" - "+square.getName());
            if(!Inputer.askIfYes(br,"Update this square?")) continue;
            if(!isMySquareList) {
                List<String> members = getSquareMembers(square.getId(), apipClient);
                if(members != null && !members.contains(myFid)) {
                    System.out.println("You are not a member of [" + square.getId() + "]. Join it first.");
                    continue;
                }
            }
            if(Inputer.askIfYes(br, "Update " + square.getId() + "?")) {
                long updateCd = square.getCddToUpdate() + 1;
                System.out.println("Updating [" + square.getId() + "], " + updateCd + " CD to required.");
                if(!Inputer.askIfYes(br, "Update it?")) continue;
                String updateResult = updateSquare(priKey, offLineFid, null, updateCd, null, square.getId(), null, null, null, apipClient);
                if(!Hex.isHexString(updateResult))System.out.println(updateResult);
                else System.out.println("Updated [" + square.getName() + "].");
            }
            if(!Inputer.askIfYes(br,"Update next square?")) break;
        }
        System.out.println("Work done. Check squares a few minutes later.");
    }
    public void opItems(List<Square> chosenSquareList, boolean isMySquareList,
            byte[] priKey, String offLineFid, ApipClient apipClient, BufferedReader br) {
        String[] options;
        if(isMySquareList)
            options = new String[]{"view", "Leave", "Update", "Members"};
        else options = new String[]{"view", "join","Leave","Update","Members"};
        while(true) {
            String subOp = Inputer.chooseOne(options,null,"What to do?",br);
            if(subOp==null || "".equals(subOp))return;
            switch (subOp) {
                case "view" -> viewItems(chosenSquareList, br);
                case "join" -> joinSquares(chosenSquareList, isMySquareList, priKey, offLineFid, apipClient, br);
                case "Leave" -> leaveSquares(chosenSquareList, isMySquareList, priKey, offLineFid, apipClient, br);
                case "Update" -> updateSquares(chosenSquareList, isMySquareList, priKey, offLineFid, apipClient, br);
                case "Members" -> viewSquareMembers(chosenSquareList, apipClient,br);
                default -> {return;}
            }
        }
    }

    // 8. Utility Methods
    public List<Square> searchSquares(String searchTerm, ApipClient apipClient) {
        Fcdsl fcdsl = new Fcdsl();
        if(searchTerm!=null && !"".equals(searchTerm))
            fcdsl.addNewQuery()
                 .addNewPart()
                 .addNewFields(FieldNames.SQUARE_ID, FieldNames.NAME, Values.DESC,FieldNames.MEMBERS)
                 .addNewValue(searchTerm);
        return apipClient.squareSearch(fcdsl, RequestMethod.POST, AuthType.SYMKEY_ENCRYPT);
    }
    public List<Square> chooseSquareList(List<Square> squareList, BufferedReader br) {
        if(squareList==null || squareList.isEmpty())return null;
        return Inputer.chooseMultiFromListShowingMultiField(squareList, Arrays.asList(FieldNames.SQUARE_ID,FieldNames.NAME), Arrays.asList(15,15), "Choose squares:", 1, br);
    }
    public Square searchAndChooseSquare(ApipClient apipClient, BufferedReader br) {
        while(true) {
            String input = Inputer.inputString(br,"Input the hint of the square you want to join for searching:");
            if(input==null|| "".equals(input))return null;
            
            List<Square> result = searchSquares(input, apipClient);
            
            if(result==null || result.isEmpty()){
                System.out.println("No such square. Try again.");
                continue;
            }
            
            Square chosenSquare = Inputer.chooseOneFromList(result, FieldNames.NAME, "Choose the square to join:", br);
            if(chosenSquare==null){
                System.out.println("Try again.");
                continue;
            }
            return chosenSquare;
        }
    }
    public void showItems(List<Square> squareList, BufferedReader br) {
        if(squareList==null || squareList.isEmpty())return;
        String title = "Squares";
        String[] fields = { FieldNames.SQUARE_ID, FieldNames.MEMBER_NUM, FieldNames.TCDD,FieldNames.NAME};
        int[] widths = { 15, 10, 16,20};
        List<List<Object>> valueListList = new ArrayList<>();
        for(Square square : squareList){
            List<Object> valueList = new ArrayList<>();
            valueList.add(
                square.getId()!=null && square.getId().length()>15?
                StringUtils.omitMiddle(square.getId(), 15):square.getId()
            );
            valueList.add(square.getMemberNum());
            valueList.add(square.gettCdd());
            valueList.add(
                    square.getName()!=null && square.getName().length()>20?
                            StringUtils.omitMiddle(square.getName(), 20):square.getName()
            );
            valueListList.add(valueList);
        }
        Shower.showOrChooseList(title, fields, widths, valueListList, null);
    }
    public void viewItems(List<Square> chosenSquareMaskList, BufferedReader br) {
        while (true) {
            List<Square> viewSquareList = chooseSquareList(chosenSquareMaskList, br);
            System.out.println(JsonUtils.toNiceJson(viewSquareList));
            if(Inputer.askIfYes(br,"Continue?"))continue;
            else break;
        }
    }
    public void viewSquareMembers(List<Square> chosenSquareMaskList, ApipClient apipClient, @Nullable BufferedReader br) {
        for(Square square : chosenSquareMaskList){
            List<String> members = getSquareMembers(square.getId(),apipClient);
            System.out.println("Members of ["+square.getName()+"]:");
            System.out.println(members);
            System.out.println();
            if(br!=null)Menu.anyKeyToContinue(br);
        }
    }
}
