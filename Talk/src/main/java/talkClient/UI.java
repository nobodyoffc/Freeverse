//package talkClient;
//
//import appTools.Inputer;
//import appTools.Menu;
//import config.Settings;
//import clients.ApipClient;
//import clients.Displayer;
//import handlers.CashHandler;
//import handlers.GroupHandler;
//import handlers.MailHandler;
//import handlers.TeamHandler;
//import handlers.ContactHandler;
//import fcData.TalkIdInfo;
//import constants.FieldNames;
//import constants.Strings;
//import fcData.*;
//import fcData.TalkUnit.IdType;
//import feip.feipData.Group;
//import tools.*;
//
//import java.io.BufferedReader;
//import java.io.IOException;
//import java.util.*;
//
//import static appTools.Inputer.chooseOne;
//import static constants.FieldNames.*;
//
//public class UI extends Thread {
//    private final ApipClient apipClient;
//    private final MailHandler mailHandler;
//    private final ContactHandler contactHandler;
//    private final CashHandler cashHandler;
//    private final GroupHandler groupHandler;
//    private final TeamHandler teamHandler;
//    private final ClientTalk clientTalk;
//    private final BufferedReader br;
//
//    private final byte[] symKey;
//
//    private final Send sendThread;
//    private final Get getThread;
//    private final Displayer displayer;
//    private final Settings settings;
//    private byte[] dealerSessionKey;
//
//
//    public UI(Send sendThread, Get getThread, ClientTalk clientTalk, CashHandler cashHandler, Settings settings, BufferedReader br) {
//        this.sendThread = sendThread;
//        this.getThread = getThread;
//        this.clientTalk = clientTalk;
//        this.apipClient = clientTalk.getApipClient();
//        this.br = br;
//        this.symKey = clientTalk.getSymKey();
//        this.cashHandler = cashHandler;
//        this.mailHandler = new MailHandler(clientTalk.getMyFid(),apipClient, cashHandler, symKey, clientTalk.getMyPriKeyCipher(), br);
//        this.contactHandler = new ContactHandler(clientTalk.getMyFid(),apipClient, cashHandler, symKey, clientTalk.getMyPriKeyCipher(), br);
//        this.groupHandler = new GroupHandler(clientTalk.getMyFid(),apipClient, clientTalk.getSid(), symKey, clientTalk.getMyPriKeyCipher(), clientTalk.getLastTimeMap(), br);
//        this.teamHandler = new TeamHandler(clientTalk.getMyFid(),apipClient, clientTalk.getSid(), symKey, clientTalk.getMyPriKeyCipher(), clientTalk.getLastTimeMap(), br);
//        this.displayer = new Displayer(clientTalk);
//        this.settings =settings;
//    }
//
//    public void run() {
//
//        displayer.start();
//
//        mailHandler.checkMail(br);
//        contactHandler.checkContacts(br);
//        groupHandler.checkGroup(br);
//        teamHandler.checkTeam(br);
//
//
//        dealerSessionKey = waitForServerSessionKey(clientTalk.getDealer());
//        if(dealerSessionKey==null)return;
//
//        showInstruction();
//
//        while(true){
//            TalkIdInfo talkIdInfo = null;
//            byte[] sessionKey = null;
//            String words = null;
//            try{
//                idLoop:
//                while(true) {
//                    displayer.resumeDisplay();
//                    try {
//                        Thread.sleep(1000); // Wait for 1 second
//                    } catch (InterruptedException e) {
//                        Thread.currentThread().interrupt();
//                        System.out.println("Waiting interrupted: " + e.getMessage());
//                    }
//                    System.out.println("Waiting for messages. Enter to talk with ...");
//                    br.readLine(); //Pause displaying to prepare for reading the talkId.
//                    displayer.pause();
//                    System.out.print("> ");
//
//                    String input = br.readLine();
//
//                    if(input.trim().equalsIgnoreCase(Strings.EXIT)){
//                        exit();
//                        return;
//                    }
//
//                    if(input.isBlank()){ //Send to the last talkId.
//                        if(clientTalk.getLastTalkId()==null)continue idLoop;
//                        talkIdInfo = clientTalk.getTalkIdInfoMap().get(clientTalk.getLastTalkId());
//                        break idLoop;
//                    }else if(input.charAt(0)==ASCII.ESCAPE){
//                        System.out.println("Waiting for messages...");
//                    } else{
//                        String[] inputs = input.split(" ");
//
//                        if (inputs.length == 1) {
//                            if(inputs[0].equals("?")){
//                                showInstruction();
//                                continue idLoop;
//                            }else if(inputs[0].equalsIgnoreCase("$")) {
//                                 //Take the second input as command.
//                                executeCommand(inputs);
//                                continue  idLoop;
//                            }
//                            talkIdInfo = processSingleInput(inputs[0]);//Take it as talkId.
//                            if (talkIdInfo != null) break;
//                            continue idLoop;
//                        }
//
//                        List<TalkIdInfo> talkIdInfoList = null;
//                        switch (inputs[0]) { //Take the first input as talkId type.
//                            case FieldNames.CID -> {
//                                talkIdInfoList = clientTalk.findCid(inputs[1],false);
//                                if(talkIdInfoList==null || talkIdInfoList.isEmpty())continue idLoop;
//                                talkIdInfo = clientTalk.chooseOneTalkIdInfo(talkIdInfoList);
//                                break idLoop;
//                            }
//                            case FieldNames.FID ->{
//                                talkIdInfoList = clientTalk.findFid(inputs[1],false);
//                                if(talkIdInfoList==null || talkIdInfoList.isEmpty())continue idLoop;
//                                talkIdInfo = clientTalk.chooseOneTalkIdInfo(talkIdInfoList);
//                                break idLoop;
//                            }
//                            case FieldNames.GROUP -> {
//                                talkIdInfoList = clientTalk.findGroup(inputs[1],false);
//                                if(talkIdInfoList==null || talkIdInfoList.isEmpty())continue idLoop;
//                                talkIdInfo = clientTalk.chooseOneTalkIdInfo(talkIdInfoList);
//                                break idLoop;
//                            }
//                            case FieldNames.TEAM -> {
//                                talkIdInfoList = clientTalk.findTeam(inputs[1],false);
//                                if(talkIdInfoList==null || talkIdInfoList.isEmpty())continue idLoop;
//                                talkIdInfo = clientTalk.chooseOneTalkIdInfo(talkIdInfoList);
//                                break idLoop;
//                            }
//                            case FieldNames.ROOM -> {
//                                talkIdInfoList = clientTalk.findRoom(inputs[1],false);
//                                if(talkIdInfoList==null || talkIdInfoList.isEmpty()){
//                                    clientTalk.requestRoomInfo(input);
//                                    continue idLoop;
//                                }
//                                talkIdInfo = clientTalk.chooseOneTalkIdInfo(talkIdInfoList);
//                                break idLoop;
//                            }
//
//                            default -> {
//                                //More than one input. Search the first one as talkId. The rest is words.
//                                talkIdInfoList = clientTalk.findTalkIdInfos(inputs[0],true);
//                                talkIdInfo = clientTalk.chooseOneTalkIdInfo(talkIdInfoList);
//                                if (talkIdInfo == null) continue idLoop;
//                                words = input.substring(inputs[0].length());
//                                break  idLoop;
//                            }
//                        }
//                    }
//                }
//
//                if(talkIdInfo==null)continue;
//                System.out.println("To "+talkIdInfo.getShowName()+":");
//
//                sessionKey = clientTalk.getSessionKey(talkIdInfo);
//
//                if(sessionKey==null && !talkIdInfo.getIdType().equals(TalkUnit.IdType.GROUP)){
//                    sessionKey = prepareSessionKey(talkIdInfo);
//                    if(sessionKey==null)continue;
//                }
//
//                if (words == null) words = br.readLine();
//                System.out.println(words);
//
//                sayWords(words, talkIdInfo, sessionKey);
//
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        }
//    }
//
//    private void executeCommand(String[] inputs) {
//        //
//        System.out.println("Command not implemented.");
//        /*
//        System command:
//        * update: mail, cid, fid, group, team
//        * request: askKey, askRoomInfo, askData, askHat,createRoom,closeRoom,addRoomMember,removeRoomMember
//        * setup: room
//        */
//
//        Menu menu = new Menu();
//        menu.setTitle("Commands");
//        menu.add("My ID",
//                "My Groups",
//                "My Teams",
//                "My Rooms",
//                "My mails",
//                "My Contacts",
//                "My Cashes",
//                "Find talkId",
//                "Ask Key",
//                "Ask Room Info",
//                "Ask Data",
//                "Ask HAT",
//                "Create Room",
//                "Add Room Members",
//                "Remove Room Members",
//                "Close Room");
//        menu.show();
//        int choice = menu.choose(br);
//
//        switch (choice){
//            case 1 -> settings.checkFidInfo(apipClient, br);
//            case 2 -> groupHandler.menu();
//            case 3 -> teamHandler.menu();
//            // case 4 -> roomsClient.menu();
//            case 5 -> mailHandler.menu();
//            case 6 -> contactHandler.menu();
//            case 7 -> cashHandler.menu();
//            case 8 -> findTalkId(br);
//
////            case 13 -> talkTcpClient.askKey(inputs[1],null,null);
////            case 14 -> talkTcpClient.requestRoomInfo(inputs[1]);
////            case 15 -> talkTcpClient.requestData(inputs[1],null,null);
////            case 16 -> talkTcpClient.requestHat(inputs[1],null,null);
////            case 17 -> talkTcpClient.createRoom(inputs[1],null,null);
////            case 18 -> talkTcpClient.addRoomMember(inputs[1],null,null);
////            case 19 -> talkTcpClient.removeRoomMember(inputs[1],null,null);
////            case 20 -> talkTcpClient.closeRoom(inputs[1],null,null);
//        }
//
//    }
//    private TalkIdInfo findTalkId (BufferedReader br) {
//        List<TalkIdInfo> talkIdInfoList;
//        try {
//            talkIdInfoList = findTalkIdList(br);
//            if(talkIdInfoList==null || talkIdInfoList.isEmpty())return null;
//            if(talkIdInfoList.size()==1)return talkIdInfoList.get(0);
//            return clientTalk.chooseOneTalkIdInfo(talkIdInfoList);
//        } catch (IOException e) {
//            System.out.println("! Failed to find talkId:"+e.getMessage());
//            return null;
//        }
//    }
//
//    private List<TalkIdInfo> findTalkIdList(BufferedReader br) throws IOException {
//        List<TalkIdInfo> finalTalkIdInfoList = new ArrayList<>();
//        while(true){
//            String idType = chooseOne(new String[]{FieldNames.CID,FieldNames.FID,FieldNames.GROUP,FieldNames.TEAM,FieldNames.ROOM},null,"Search as:",br);
//            String searchString = Inputer.inputString(br,"Search for:");
//
//            List<TalkIdInfo> talkIdInfoList = null;
//
//            switch (idType) { //Take the first input as talkId type.
//                case FieldNames.CID -> {
//                    talkIdInfoList = clientTalk.findCid(searchString,false);
//                    if(talkIdInfoList==null || talkIdInfoList.isEmpty()){
//                        System.out.println("No such CID.");
//                        continue;
//                    }
//                }
//                case FieldNames.FID ->{
//                    talkIdInfoList = clientTalk.findFid(searchString,false);
//                    if(talkIdInfoList==null || talkIdInfoList.isEmpty()){
//                        System.out.println("No such FID.");
//                        continue;
//                    }
//                }
//                case FieldNames.GROUP -> {
//                    talkIdInfoList = clientTalk.findGroup(searchString,false);
//                    if(talkIdInfoList==null || talkIdInfoList.isEmpty()){
//                        System.out.println("No such group.");
//                        continue;
//                    }
//                }
//                case FieldNames.TEAM -> {
//                    talkIdInfoList = clientTalk.findTeam(searchString,false);
//                    if(talkIdInfoList==null || talkIdInfoList.isEmpty()){
//                        System.out.println("No such team.");
//                        continue;
//                    }
//                }
//                case FieldNames.ROOM -> {
//                    talkIdInfoList = clientTalk.findRoom(searchString,false);
//                    if(talkIdInfoList==null || talkIdInfoList.isEmpty()){
//                        System.out.println("No such room in local. You may request it from its members.");
//                        continue;
//                    }
//                }
//                default -> {
//                    continue;
//                }
//            }
//            List<String> showFieldList = new ArrayList<>() {
//                {add(FieldNames.TYPE); add(FieldNames.SHOW_NAME);add(FieldNames.ID);}
//            };
//            List<Integer> widthList = new ArrayList<>() {{
//                add(10); add(21);add(13);
//            }};
//            String ask = "Choose some talkId:";
//            List<TalkIdInfo> chosenTalkIdInfoList = Inputer.chooseMultiFromListShowingMultiField(talkIdInfoList,showFieldList,widthList,ask,1,br);
//            finalTalkIdInfoList.addAll(chosenTalkIdInfoList);
//            if(!Inputer.askIfYes(br,"Find more?"))return finalTalkIdInfoList;
//        }
//    }
//
//    private TalkUnit sayWords(String words, TalkIdInfo talkIdInfo, byte[] sessionKey) {
//        TalkUnit talkUnit;
//        if(talkIdInfo.getIdType().equals(TalkUnit.IdType.GROUP))
//            talkUnit = clientTalk.makeBytesTalkUnit(words.getBytes(), TalkUnit.DataType.TEXT, clientTalk.getMyFid(), talkIdInfo, null, null, null, null);
//        else{
//            talkUnit = clientTalk.makeBytesTalkUnit(words.getBytes(), TalkUnit.DataType.ENCRYPTED_TEXT, clientTalk.getMyFid(), talkIdInfo, sessionKey, null, null, null);
//        }
//        clientTalk.addToSendingQueue(talkUnit);
//        return talkUnit;
//    }
//
//    private void shareGroupList(List<Group> groupMaskList, List<String> idList, BufferedReader br) {
//        String words = JsonTools.toJson(groupMaskList);
//        String searchString = Inputer.inputString(br,"Search for those you want to share with:");
//        List<TalkIdInfo> talkIdInfoList = null;
//        try {
//            talkIdInfoList = clientTalk.findTalkIdInfos(searchString,true);
//        } catch (IOException e) {
//            System.out.println("! Failed to find talkInfoId:"+e.getMessage());
//            return;
//        }
//        TalkIdInfo talkIdInfo = Inputer.chooseOneFromList(talkIdInfoList, FieldNames.SHOW_NAME, "Choose who or where you are talking to:", br);
//        TalkUnit talkUnit = clientTalk.makeBytesTalkUnit(words.getBytes(), TalkUnit.DataType.TEXT, clientTalk.getMyFid(), talkIdInfo, null, null, null, idList);
//        clientTalk.addToSendingQueue(talkUnit);
//    }
//
//    private byte[] prepareSessionKey(TalkIdInfo talkIdInfo) {
//        boolean isOwner = talkIdInfo.getOwner().equals(clientTalk.getMyFid());
//        byte[] sessionKey=null;
//        System.out.println("The key of "+talkIdInfo.getShowName()+ " is missed in current client.");
//        System.out.println("'g' to generate a new one and share it to others.");
//        System.out.println("'ao' to ask key from others.");
//        System.out.println("'am' to ask key from the other clients of yourself.");
//        System.out.println("other to cancel current operation.");
//        String input = Inputer.inputString(br);
//        switch (input){
//            case "g" -> {
//                if(!isOwner && !talkIdInfo.getIdType().equals(IdType.FID)){
//                    System.out.println("You are not the owner. You can't generate a new key.");
//                    break;
//                }
//                sessionKey = clientTalk.newKey(talkIdInfo.getId());
//                clientTalk.updateSessionKeyMaps(talkIdInfo.getId(), sessionKey);
//
//                if(talkIdInfo.getIdType().equals(IdType.FID)){
//                    clientTalk.shareSessionKey(talkIdInfo.getId(), sessionKey, apipClient, symKey);
//                    clientTalk.shareSessionKey(clientTalk.getMyFid(), sessionKey, apipClient, symKey);
//                }else{
//                    List<String> memberList = clientTalk.getMembers(talkIdInfo);
//                    for(String fid:memberList) {
//                        clientTalk.shareSessionKey(fid,sessionKey,apipClient,symKey);
//                    }
//                }
//                return sessionKey;
//            }
//
//            case "ao" -> {
//                if(!talkIdInfo.getIdType().equals(TalkUnit.IdType.FID)){
//                    talkIdInfo = clientTalk.searchCidOrFid(apipClient, br);
//                }
//                if(talkIdInfo==null)return null;
//                Map<String,String> askMap = new HashMap<>();
//                askMap.put(ID,talkIdInfo.getId());
//                clientTalk.askKey(talkIdInfo.getId(),askMap,talkIdInfo.getPubKey());
//                return null;
//            }
//            case "am" -> {
//                clientTalk.askKey(clientTalk.getMyFid(),null,talkIdInfo.getPubKey());
//                return null;
//            }
//            default -> {
//                return null;
//            }
//        }
//        return  sessionKey;
//    }
//
//
//    private byte[] waitForServerSessionKey(String fid) {
//        synchronized (clientTalk.getSessionKeyMap()){
//            byte[] dealerSessionKey = clientTalk.getSessionKeyMap().get(fid);
//            if(dealerSessionKey!=null)return dealerSessionKey;
//            while (true) {
//                try {
//                    clientTalk.getSessionKeyMap().wait();
//                    dealerSessionKey = clientTalk.getSessionKeyMap().get(fid);
//                    if(dealerSessionKey==null)continue;
//                    return dealerSessionKey;
//                } catch (InterruptedException e) {
//                    System.out.println("Failed to wait for the server session key.");
//                    return null;
//                }
//            }
//        }
//    }
//
//
//    public static void showInstruction() {
//        System.out.println("""
//                ---------------------
//                INSTRUCTION
//                ---------------------
//                # Send to the last talkId:
//                    - Enter to lock the last one to send to in case the new message changing it.
//                # search talkId:
//                    - search the input as talkId.
//                # search CID, FID, GID, TID or RoomId as talkId:
//                    - input cid, fid, group, team or room, and add the search string after a space.
//                    such as: 'cid nasa', 'room 2ab4'
//                # $ command without recipient:
//                    - After the the recipient and begin with '$', such as '$fresh mail'
//                    - Requests for server: createRoom,askRoomInfo, shareRoomInfo, addMember,removeMember,closeRoom,updateItems
//                    - Requests for users: askKey, askRoomInfo, askData, askHat
//                # Show the instruction:
//                    ?
//                # Exit:
//                    exit
//                ---------------------
//                """);
//    }
//
//    private void exit() {
//        displayer.stopDisplay();
//        getThread.stopThread();
//        sendThread.stopThread();
//        synchronized (clientTalk.getSendingQueue()) {
//            clientTalk.getSendingQueue().notifyAll();
//        }
//        synchronized (clientTalk.getDisplayMessageQueue()) {
//            clientTalk.getDisplayMessageQueue().notifyAll();
//        }
//        synchronized (clientTalk.getReceivedQueue()) {
//            clientTalk.getReceivedQueue().notifyAll();
//        }
//        clientTalk.close(settings);
//        // Add this line to exit the entire program
//        System.exit(0);
//    }
//
//    private TalkIdInfo processSingleInput(String input) throws IOException {
//        String talkId = clientTalk.getShortcutIdMap().get(input);
//        TalkIdInfo talkIdInfo = clientTalk.getTalkIdInfoById(talkId);
//
//        if (talkIdInfo != null) return talkIdInfo;
//
//        List<TalkIdInfo> talkIdInfos = clientTalk.searchTalkIdInfos(input);
//        if (talkIdInfos.isEmpty()) {
//            System.out.println("It's unknown talk ID. Try add the type(cid,fid,group,team,or room) before it.");
//            return null;
//        }
//        if (talkIdInfos.size() == 1) {
//            return talkIdInfos.get(0);
//        } else {
//            return (TalkIdInfo) Inputer.chooseOneFromList(talkIdInfos, FieldNames.SHOW_NAME, "Choose who or where you are talking to:", br);
//        }
//    }
//}
