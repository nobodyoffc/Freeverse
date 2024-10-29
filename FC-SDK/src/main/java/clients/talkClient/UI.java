package clients.talkClient;

import appTools.Inputer;
import appTools.Menu;
import appTools.Settings;
import clients.apipClient.ApipClient;
import clients.mailClient.MailClient;
import clients.talkClient.TalkTcpClient.TalkIdInfo;
import constants.FieldNames;
import constants.Strings;
import fcData.*;
import fcData.TalkUnit.IdType;
import javaTools.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

import static clients.talkClient.TalkTcpClient.*;
import static constants.FieldNames.*;
import static constants.IndicesNames.*;

public class UI extends Thread {
    private final ApipClient apipClient;
    private final MailClient mailClient;
    private final TalkTcpClient talkTcpClient;
    private final BufferedReader br;
    
    private final byte[] symKey;

    private final Send sendThread;
    private final Get getThread;
    private final Displayer displayer;
    private final Settings settings;
    private byte[] dealerSessionKey;

    public static class RoomInfo{
        private String roomId;
        private String name;
        private String owner;
        private String[] members;

        public byte[] toBytes(){
            return javaTools.JsonTools.toJson(this).getBytes();
        }

        public static RoomInfo fromBytes(byte[] bytes){
            return javaTools.JsonTools.fromJson(new String(bytes), RoomInfo.class);
        }  

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }

        public String getRoomId() {
            return roomId;
        }

        public void setRoomId(String roomId) {
            this.roomId = roomId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String[] getMembers() {
            return members;
        }

        public void setMembers(String[] members) {
            this.members = members;
        }
    }


    public UI(Send sendThread, Get getThread, TalkTcpClient talkTcpClient, Settings settings, BufferedReader br) {
        this.sendThread = sendThread;
        this.getThread = getThread;
        this.talkTcpClient = talkTcpClient;
        this.apipClient = talkTcpClient.getApipClient();
        this.br = br;
        this.symKey = talkTcpClient.getSymKey();
        this.mailClient = new MailClient(myFid,apipClient, sid, symKey, myPriKeyCipher, lastTimeMap);
        this.displayer = new Displayer();
        this.settings =settings;
    }

    public void run() {

        displayer.start();

        mailClient.checkMail(br);

        dealerSessionKey = waitForServerSessionKey(dealer);
        if(dealerSessionKey==null)return;

        showInstruction();

        while(true){
            TalkIdInfo talkIdInfo = null;
            byte[] sessionKey = null;
            String words = null;
            try{
                idLoop:
                while(true) {
                    displayer.resumeDisplay();
                    try {
                        Thread.sleep(1000); // Wait for 1 second
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.out.println("Waiting interrupted: " + e.getMessage());
                    }
                    System.out.println("Waiting for messages. Enter to talk with ...");
                    br.readLine(); //Pause displaying to prepare for reading the talkId.
                    displayer.pause();
                    System.out.print("> ");

                    String input = br.readLine();

                    if(input.trim().equalsIgnoreCase(Strings.EXIT)){
                        exit();
                        return;
                    }

                    if(input.isBlank()){ //Send to the last talkId.
                        if(lastTalkId==null)continue idLoop;
                        talkIdInfo = talkIdInfoMap.get(lastTalkId);
                        break idLoop;
                    }else if(input.charAt(0)==ASCII.ESCAPE){
                        System.out.println("Waiting for messages...");
                    } else{
                        String[] inputs = input.split(" ");

                        if (inputs.length == 1) {
                            if(inputs[0].equals("?")){
                                showInstruction();
                                continue idLoop;
                            }else if(inputs[0].equalsIgnoreCase("$")) {
                                 //Take the second input as command.
                                executeCommand(inputs);
                                continue  idLoop;
                            }
                            talkIdInfo = processSingleInput(inputs[0]);//Take it as talkId.
                            if (talkIdInfo != null) break;
                            continue idLoop;
                        }

                        List<TalkIdInfo> talkIdInfoList = null;
                        switch (inputs[0]) { //Take the first input as talkId type.
                            case FieldNames.CID -> {
                                talkIdInfoList = talkTcpClient.findCid(inputs[1],false);
                                if(talkIdInfoList==null || talkIdInfoList.isEmpty())continue idLoop;
                                talkIdInfo = talkTcpClient.chooseOneTalkIdInfo(talkIdInfoList);
                                break idLoop;
                            }
                            case FieldNames.FID ->{
                                talkIdInfoList = talkTcpClient.findFid(inputs[1],false);
                                if(talkIdInfoList==null || talkIdInfoList.isEmpty())continue idLoop;
                                talkIdInfo = talkTcpClient.chooseOneTalkIdInfo(talkIdInfoList);
                                break idLoop;
                            }
                            case FieldNames.GROUP -> {
                                talkIdInfoList = talkTcpClient.findGroup(inputs[1],false);
                                if(talkIdInfoList==null || talkIdInfoList.isEmpty())continue idLoop;
                                talkIdInfo = talkTcpClient.chooseOneTalkIdInfo(talkIdInfoList);
                                break idLoop;
                            }
                            case FieldNames.TEAM -> {
                                talkIdInfoList = talkTcpClient.findTeam(inputs[1],false);
                                if(talkIdInfoList==null || talkIdInfoList.isEmpty())continue idLoop;
                                talkIdInfo = talkTcpClient.chooseOneTalkIdInfo(talkIdInfoList);
                                break idLoop;
                            }
                            case FieldNames.ROOM -> {
                                talkIdInfoList = talkTcpClient.findRoom(inputs[1],false);
                                if(talkIdInfoList==null || talkIdInfoList.isEmpty()){
                                    talkTcpClient.requestRoomInfo(input);
                                    continue idLoop;
                                }
                                talkIdInfo = talkTcpClient.chooseOneTalkIdInfo(talkIdInfoList);
                                break idLoop;
                            }
                        
                            default -> {
                                //More than one input. Search the first one as talkId. The rest is words.
                                talkIdInfo = processSingleInput(inputs[0]);
                                if (talkIdInfo == null) continue idLoop;
                                words = input.substring(inputs[0].length());
                                break  idLoop;
                            }
                        }
                    }
                }

                if(talkIdInfo==null)continue;
                System.out.println("To "+talkIdInfo.getShowName()+":");

                sessionKey = talkTcpClient.getSessionKey(talkIdInfo);

                if(sessionKey==null && !talkIdInfo.getIdType().equals(TalkUnit.IdType.GROUP)){
                    sessionKey = prepareSessionKey(talkIdInfo);
                    if(sessionKey==null)continue;
                }
     
                if (words == null) words = br.readLine();
                System.out.println(words);

                sayWords(words, talkIdInfo, sessionKey);

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void executeCommand(String[] inputs) {
        // 
        System.out.println("Command not implemented.");
        /*
        System command:
        * update: mail, cid, fid, group, team
        * request: askKey, askRoomInfo, askData, askHat,createRoom,closeRoom,addRoomMember,removeRoomMember
        * setup: room
        */

        Menu menu = new Menu();
        menu.setTitle("Commands");
        menu.add("My ID",
                "My Groups",
                "My Teams",
                "My Rooms",
                "Update mails",
                "List mails",
                "Update Contacts",
                "List Contacts",
                "Find FID",
                "Find CID",
                "Find Group",
                "Find Team",
                "Ask Key",
                "Ask Room Info",
                "Ask Data",
                "Ask HAT",
                "Create Room",
                "Add Room Members",
                "Remove Room Members",
                "Close Room");
        menu.show();
        int choice = menu.choose(br);

        switch (choice){
            case 1 -> settings.checkFidInfo(apipClient, br);
//            case 2 -> myGroups();
//            case 3 -> myTeams();
//            case 4 -> myRooms();
//            case 5 -> mailClient.checkMail(br);
            case 6 -> mailClient.menu(br);
//            case 7 -> settings.checkFidInfo(apipClient, br);
//            case 8 -> settings.checkFidInfo(apipClient, br);
//            case 9 -> talkTcpClient.findFid(inputs[1],false);
//            case 10 -> talkTcpClient.findCid(inputs[1],false);
//            case 11 -> talkTcpClient.findGroup(inputs[1],false);
//            case 12 -> talkTcpClient.findTeam(inputs[1],false);
//            case 13 -> talkTcpClient.askKey(inputs[1],null,null);
//            case 14 -> talkTcpClient.requestRoomInfo(inputs[1]);
//            case 15 -> talkTcpClient.requestData(inputs[1],null,null);
//            case 16 -> talkTcpClient.requestHat(inputs[1],null,null);
//            case 17 -> talkTcpClient.createRoom(inputs[1],null,null);
//            case 18 -> talkTcpClient.addRoomMember(inputs[1],null,null);
//            case 19 -> talkTcpClient.removeRoomMember(inputs[1],null,null);
//            case 20 -> talkTcpClient.closeRoom(inputs[1],null,null);
        }

    }




    private TalkUnit sayWords(String words, TalkIdInfo talkIdInfo, byte[] sessionKey) {
        TalkUnit talkUnit;
        if(talkIdInfo.getIdType().equals(TalkUnit.IdType.GROUP))
            talkUnit = TalkTcpClient.makeBytesTalkUnit(words.getBytes(), TalkUnit.DataType.TEXT, myFid, talkIdInfo, null, null, null, null);
        else if(talkIdInfo.getIdType().equals(TalkUnit.IdType.FID_LIST)){
            List<String> fidList =talkTcpClient. getRoomMembers(talkIdInfo.getId());
            talkUnit = TalkTcpClient.makeBytesTalkUnit(words.getBytes(), TalkUnit.DataType.ENCRYPTED_TEXT, myFid, talkIdInfo, sessionKey, null, null, fidList);
        }
        else{
            talkUnit = TalkTcpClient.makeBytesTalkUnit(words.getBytes(), TalkUnit.DataType.ENCRYPTED_TEXT, myFid, talkIdInfo, sessionKey, null, null, null);
        }
        TalkTcpClient.addToSendingQueue(talkUnit);
        return talkUnit;
    }

    private byte[] prepareSessionKey(TalkIdInfo talkIdInfo) {
        boolean isOwner = talkIdInfo.getOwner().equals(myFid);
        byte[] sessionKey=null;
        System.out.println("The key of "+talkIdInfo.getShowName()+ " is missed in current client.");
        System.out.println("'g' to generate a new one and share it to others.");
        System.out.println("'ao' to ask key from others.");
        System.out.println("'am' to ask key from the other clients of yourself.");
        System.out.println("other to cancel current operation.");
        String input = Inputer.inputString(br);
        switch (input){
            case "g" -> {
                if(!isOwner && !talkIdInfo.getIdType().equals(IdType.FID)){
                    System.out.println("You are not the owner. You can't generate a new key.");
                    break;
                }
                sessionKey = talkTcpClient.newKey(talkIdInfo.getId());
                TalkTcpClient.updateSessionKeyMaps(talkIdInfo.getId(), sessionKey);

                if(talkIdInfo.getIdType().equals(IdType.FID)){
                    TalkTcpClient.shareSessionKey(talkIdInfo.getId(), sessionKey, apipClient, symKey);
                    TalkTcpClient.shareSessionKey(myFid, sessionKey, apipClient, symKey);
                }else{
                    List<String> memberList = talkTcpClient.getMembers(talkIdInfo);
                    for(String fid:memberList) {
                        TalkTcpClient.shareSessionKey(fid,sessionKey,apipClient,symKey);
                    }
                }
                return sessionKey;
            }
                    
            case "ao" -> {
                if(!talkIdInfo.getIdType().equals(TalkUnit.IdType.FID)){
                    talkIdInfo = TalkTcpClient.searchCidOrFid(apipClient, br);
                }
                if(talkIdInfo==null)return null;
                Map<String,String> askMap = new HashMap<>();
                askMap.put(ID,talkIdInfo.getId());
                talkTcpClient.askKey(talkIdInfo.getId(),askMap,talkIdInfo.getPubKey());
                return null;
            }
            case "am" -> {
                talkTcpClient.askKey(myFid,null,talkIdInfo.getPubKey());
                return null;
            }
            default -> {
                return null;
            }
        }
        return  sessionKey;
    }


    private byte[] waitForServerSessionKey(String fid) {
        synchronized (sessionKeyMap){
            byte[] dealerSessionKey =sessionKeyMap.get(fid);
            if(dealerSessionKey!=null)return dealerSessionKey;
            while (true) {
                try {
                    sessionKeyMap.wait();
                    dealerSessionKey = sessionKeyMap.get(fid);
                    if(dealerSessionKey==null)continue;
                    return dealerSessionKey;
                } catch (InterruptedException e) {
                    System.out.println("Failed to wait for the server session key.");
                    return null;
                }
            }
        }
    }


    public static void showInstruction() {
        System.out.println("""
                ---------------------
                INSTRUCTION
                ---------------------
                # Send to the last talkId:
                    - Enter to lock the last one to send to in case the new message changing it.
                # search talkId:
                    - search the input as talkId.
                # search CID, FID, GID, TID or RoomId as talkId:
                    - input cid, fid, group, team or room, and add the search string after a space.
                    such as: 'cid nasa', 'room 2ab4'
                # $ command without recipient:
                    - After the the recipient and begin with '$', such as '$fresh mail'
                    - Requests for server: createRoom,askRoomInfo, shareRoomInfo, addMember,removeMember,closeRoom,updateItems
                    - Requests for users: askKey, askRoomInfo, askData, askHat
                # Show the instruction:
                    ?
                # Exit:
                    exit
                ---------------------
                """);
    }

    private void exit() {
        displayer.stopDisplay();
        getThread.stopThread();
        sendThread.stopThread();
        synchronized (sendingQueue) {
            sendingQueue.notifyAll();
        }
        synchronized (displayMessageQueue) {
            displayMessageQueue.notifyAll();
        }
        synchronized (receivedQueue) {
            receivedQueue.notifyAll();
        }
        try {
            talkTcpClient.getSocket().close();
        } catch (IOException e) {
            System.err.println("Error closing socket: " + e.getMessage());
        }
        // Add this line to exit the entire program
        System.exit(0);
    }

    private TalkIdInfo processSingleInput(String input) throws IOException {
        String talkId = shortcutIdMap.get(input);
        TalkIdInfo talkIdInfo = talkTcpClient.getTalkIdInfoById(talkId);

        if (talkIdInfo != null) return talkIdInfo;

        List<TalkIdInfo> talkIdInfos = talkTcpClient.searchTalkIdInfos(input);
        if (talkIdInfos.isEmpty()) {
            System.out.println("It's unknown talk ID. Try add the type(cid,fid,group,team,or room) before it.");
            return null;
        }
        if (talkIdInfos.size() == 1) {
            return talkIdInfos.get(0);
        } else {
            return (TalkIdInfo) Inputer.chooseOneFromList(talkIdInfos, FieldNames.SHOW_NAME, "Choose who or where you are talking to:", br);
        }
    }
}
