package clients;

import core.crypto.*;
import data.fcData.*;
import data.fchData.Cid;
import data.apipData.Fcdsl;
import data.apipData.RequestBody;
import ui.Inputer;
import ui.Menu;
import config.Settings;
import ui.Shower;
import config.ApiAccount;
import config.ApiProvider;
import constants.FieldNames;
import constants.Strings;
import constants.Values;
import data.fcData.TalkUnit.DataType;
import data.fcData.TalkUnit.IdSignature;
import data.feipData.Group;
import data.feipData.Team;
import data.feipData.serviceParams.TalkParams;
import handlers.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.jetbrains.annotations.Nullable;
import utils.*;
import utils.http.AuthType;
import utils.http.RequestMethod;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import static ui.Inputer.chooseOne;
import static constants.FieldNames.*;
import static constants.Strings.NAME;
import static data.fcData.TalkUnit.DataType.*;
import static data.fcData.TalkUnit.makeTalkUnit;

public class TalkClient extends FcClient {

    private String myFid;
    private String sid;
    private byte[] myPrikey;
    private String dealer;
    private String dealerPubkey;
    private TalkIdManager talkIdHandler;
    private Displayer displayer;

    private final ConcurrentLinkedQueue<TalkUnit> receivedQueue = new ConcurrentLinkedQueue<>();

    private TalkUnitManager talkUnitHandler;
    private CidManager cidHandler;
    private CashManager cashHandler;
    private SessionManager sessionHandler;
    private MailManager mailHandler;
    private ContactManager contactHandler;
    private GroupManager groupHandler;
    private TeamManager teamHandler;
    private HatManager hatHandler;
    private DiskManager diskHandler;

    private volatile boolean running = false;
    private transient EventLoopGroup group;
    private transient Channel channel;

    private final BufferedReader br;
    private Settings settings;

    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final int RECONNECT_DELAY_MS = 2000;

    public TalkClient(String url, BufferedReader br, Map<Manager.ManagerType, Manager> handlers) {
        this.urlHead = url;
        this.br = br;
        this.displayer = new Displayer(this);
        if(handlers!=null && !handlers.isEmpty()) {
            // Cast handlers to specific types
            this.cidHandler = (CidManager) handlers.get(Manager.ManagerType.CID);
            this.cashHandler = (CashManager) handlers.get(Manager.ManagerType.CASH);
            this.sessionHandler = (SessionManager) handlers.get(Manager.ManagerType.SESSION);
            this.mailHandler = (MailManager) handlers.get(Manager.ManagerType.MAIL);
            this.contactHandler = (ContactManager) handlers.get(Manager.ManagerType.CONTACT);
            this.groupHandler = (GroupManager) handlers.get(Manager.ManagerType.GROUP);
            this.teamHandler = (TeamManager) handlers.get(Manager.ManagerType.TEAM);
            this.hatHandler = (HatManager) handlers.get(Manager.ManagerType.HAT);
            this.diskHandler = (DiskManager) handlers.get(Manager.ManagerType.DISK);
            this.talkIdHandler = (TalkIdManager) handlers.get(Manager.ManagerType.TALK_ID);
            this.talkUnitHandler = (TalkUnitManager) handlers.get(Manager.ManagerType.TALK_UNIT);
        }
    }

    public TalkClient(ApiProvider apiProvider, ApiAccount apiAccount, byte[] symkey, ApipClient apipClient, BufferedReader br) {
        super(apiProvider,apiAccount,symkey,apipClient);
        this.br = br;
        this.myFid = apiAccount.getUserId();
        this.myPrikey = Decryptor.decryptPrikey(apiAccount.getUserPrikeyCipher(), symkey);
        this.displayer = new Displayer(this);
        this.dealer = ((TalkParams)apiProvider.getService().getParams()).getDealer();
        this.dealerPubkey = apiProvider.getDealerPubkey();
        this.talkIdHandler = new TalkIdManager(apiAccount.getUserId(),null, null);
        this.diskHandler = new DiskManager(apiAccount.getUserId(), null);
        this.hatHandler = new HatManager(settings);
        this.talkUnitHandler = new TalkUnitManager(settings);
    }

    private void checkHandlers() {
        if(this.cidHandler == null) this.cidHandler = (CidManager) settings.getManager(Manager.ManagerType.CID);
        if(this.cashHandler == null) this.cashHandler = (CashManager) settings.getManager(Manager.ManagerType.CASH);
        if(this.sessionHandler == null) this.sessionHandler = (SessionManager) settings.getManager(Manager.ManagerType.SESSION);
        if(this.mailHandler == null) this.mailHandler = (MailManager) settings.getManager(Manager.ManagerType.MAIL);
        if(this.contactHandler == null) this.contactHandler = (ContactManager) settings.getManager(Manager.ManagerType.CONTACT);
        if(this.groupHandler == null) this.groupHandler = (GroupManager) settings.getManager(Manager.ManagerType.GROUP);
        if(this.teamHandler == null) this.teamHandler = (TeamManager) settings.getManager(Manager.ManagerType.TEAM);
        if(this.hatHandler == null) this.hatHandler = (HatManager) settings.getManager(Manager.ManagerType.HAT);
        if(this.diskHandler == null) this.diskHandler = (DiskManager) settings.getManager(Manager.ManagerType.DISK);
        if(this.talkIdHandler == null) this.talkIdHandler = (TalkIdManager) settings.getManager(Manager.ManagerType.TALK_ID);
        if(this.talkUnitHandler == null) this.talkUnitHandler = (TalkUnitManager) settings.getManager(Manager.ManagerType.TALK_UNIT);
    }

    public void start() throws Exception {
        checkHandlers();
        if (running) {
            throw new IllegalStateException("Client is already running");
        }

        group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new IdleStateHandler(0, 30, 0));
                            pipeline.addLast(new TalkClientHandler(TalkClient.this));
                        }
                    });
            URL url1 = new URL(apiProvider.getApiUrl());

            this.channel = bootstrap.connect(url1.getHost(), url1.getPort()).sync().channel();
            showInstruction();
            displayer.start();
            displayer.displayAppNotice("Talk client is working...");
//            sendBytes("Test sendBytes".getBytes());
//            sendWords("Test sendWords",getTalkIdInfoById(dealer));
            if(serverSession==null){
                serverSession = sessionHandler.getSessionByUserId(dealer);
                if(serverSession==null)
                    askKey(dealer, dealer, TalkUnit.IdType.FID, apipClient, br);
            }

//            sessionKey = checkSession(getTalkIdInfoById(dealer));

            while(true){
                TalkIdInfo talkIdInfo;
                String words = null;
                try{
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

                        String input;
                        input = Inputer.inputString(br);

                        if(input.trim().equalsIgnoreCase(Strings.EXIT)){
                            exit();
                            return;
                        }

                        if(input.isBlank()){ //Send to the last talkId.
                            if(talkIdHandler.getLastTalkId()==null)continue;
                            talkIdInfo = talkIdHandler.get(talkIdHandler.getLastTalkId());
                            break;
                        }else if(input.charAt(0)==ASCII.ESCAPE){
                            System.out.println("Waiting for messages...");
                        } else{
                            String[] inputs = input.split(" ");

                            if (inputs.length == 1) {
                                if(inputs[0].equals("?")){
                                    showInstruction();
                                    continue;
                                }else if(inputs[0].equalsIgnoreCase("$")) {
                                    //Take the second input as command.
                                    executeCommand(inputs);
                                    continue;
                                }
                                talkIdInfo = processSingleInput(inputs[0]);//Take it as talkId.
                                if (talkIdInfo != null) break;
                                continue;
                            }

                            List<TalkIdInfo> talkIdInfoList = null;
                            switch (inputs[0]) { //Take the first input as talkId type.
                                case FieldNames.CID -> {
                                    talkIdInfoList = findCid(inputs[1],false);
                                    if(talkIdInfoList==null || talkIdInfoList.isEmpty())continue;
                                    talkIdInfo = chooseOneTalkIdInfo(talkIdInfoList);
                                }
                                case FieldNames.FID ->{
                                    talkIdInfoList = findFid(inputs[1],false);
                                    if(talkIdInfoList==null || talkIdInfoList.isEmpty())continue;
                                    talkIdInfo = chooseOneTalkIdInfo(talkIdInfoList);
                                }
                                case FieldNames.GROUP -> {
                                    talkIdInfoList = findGroup(inputs[1],false);
                                    if(talkIdInfoList==null || talkIdInfoList.isEmpty())continue;
                                    talkIdInfo = chooseOneTalkIdInfo(talkIdInfoList);
                                }
                                case FieldNames.TEAM -> {
                                    talkIdInfoList = findTeam(inputs[1],false);
                                    if(talkIdInfoList==null || talkIdInfoList.isEmpty())continue;
                                    talkIdInfo = chooseOneTalkIdInfo(talkIdInfoList);
                                }
                                default -> {
                                    //More than one input. Search the first one as talkId. The rest is words.
                                    talkIdInfoList = findTalkIdInfos(inputs[0],true);
                                    talkIdInfo = chooseOneTalkIdInfo(talkIdInfoList);
                                    if (talkIdInfo == null) continue;
                                    words = input.substring(inputs[0].length());
                                }
                            }
                            break;
                        }
                    }

                    if(talkIdInfo==null)continue;
                    System.out.println("To "+talkIdInfo.getShowName()+":\n");

                    if (words == null) words = br.readLine();
//                    System.out.println(words);
                    if("".equals(words))continue;
                    sendWords(words, talkIdInfo);

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (Exception e) {
            stop();
            throw e;
        }
    }


    private void executeCommand(String[] inputs) {
        //
        System.out.println("Command not implemented.");
        /*
        System command:
        * update: mail, cid, fid, group, team
        * request: askKey, askRoomInfo, askData, askHat
        */

        Menu menu = new Menu();
        menu.setTitle("Commands");
        menu.add("My ID",
                "My Groups",
                "My Teams",
                "My mails",
                "My Contacts",
                "My Cashes",
                "Find talkId",
                "Ask Key",
                "Ask Data",
                "Ask HAT",
                "Share Key",
                "Share Data",
                "Share HAT",
                "Exit");
        menu.show();
        int choice = menu.choose(br);

        switch (choice){
            case 1 -> settings.checkFidInfo(apipClient, br);
            case 2 -> groupHandler.menu(br,false);
            case 3 -> teamHandler.menu(br,false);
            case 4 -> mailHandler.menu(br,false);
            case 5 -> contactHandler.menu(br, false);
            case 6 -> cashHandler.menu(br, false);
            case 7 -> findTalkId(br);
            case 8 -> askKey(inputs[1],null,null,apipClient,br);
            // case 9 -> requestData(inputs[1],null,null);
            // case 10 -> requestHat(inputs[1],null,null);

        }

    }
    private TalkIdInfo findTalkId (BufferedReader br) {
        List<TalkIdInfo> talkIdInfoList;
        try {
            talkIdInfoList = findTalkIdList(br);
            if(talkIdInfoList==null || talkIdInfoList.isEmpty())return null;
            if(talkIdInfoList.size()==1)return talkIdInfoList.get(0);
            return chooseOneTalkIdInfo(talkIdInfoList);
        } catch (IOException e) {
            System.out.println("! Failed to find talkId:"+e.getMessage());
            return null;
        }
    }

    private List<TalkIdInfo> findTalkIdList(BufferedReader br) throws IOException {
        List<TalkIdInfo> finalTalkIdInfoList = new ArrayList<>();
        while(true){
            String idType = chooseOne(new String[]{FieldNames.CID,FieldNames.FID,FieldNames.GROUP,FieldNames.TEAM},null,"Search as:",br);
            String searchString = Inputer.inputString(br,"Search for:");

            List<TalkIdInfo> talkIdInfoList = null;

            switch (idType) { //Take the first input as talkId type.
                case FieldNames.CID -> {
                    talkIdInfoList = findCid(searchString,false);
                    if(talkIdInfoList==null || talkIdInfoList.isEmpty()){
                        System.out.println("No such CID.");
                        continue;
                    }
                }
                case FieldNames.FID ->{
                    talkIdInfoList = findFid(searchString,false);
                    if(talkIdInfoList==null || talkIdInfoList.isEmpty()){
                        System.out.println("No such FID.");
                        continue;
                    }
                }
                case FieldNames.GROUP -> {
                    talkIdInfoList = findGroup(searchString,false);
                    if(talkIdInfoList==null || talkIdInfoList.isEmpty()){
                        System.out.println("No such group.");
                        continue;
                    }
                }
                case FieldNames.TEAM -> {
                    talkIdInfoList = findTeam(searchString,false);
                    if(talkIdInfoList==null || talkIdInfoList.isEmpty()){
                        System.out.println("No such team.");
                        continue;
                    }
                }
                default -> {
                    continue;
                }
            }
            List<String> showFieldList = new ArrayList<>() {
                {add(FieldNames.TYPE); add(FieldNames.SHOW_NAME);add(FieldNames.ID);}
            };
            List<Integer> widthList = new ArrayList<>() {{
                add(10); add(21);add(13);
            }};
            String ask = "Choose some talkId:";
            List<TalkIdInfo> chosenTalkIdInfoList = Inputer.chooseMultiFromListShowingMultiField(talkIdInfoList,showFieldList,widthList,ask,1,br);
            finalTalkIdInfoList.addAll(chosenTalkIdInfoList);
            if(!Inputer.askIfYes(br,"Find more?"))return finalTalkIdInfoList;
        }
    }

    private TalkUnit sendWords(String words, TalkIdInfo talkIdInfo) {
        TalkUnit talkUnit;
        DataType dataType;
        if(talkIdInfo.getIdType().equals(TalkUnit.IdType.FID))dataType= ENCRYPTED_TEXT;
        else dataType= ENCRYPTED_ID_SIGNED_TEXT;

        byte[] sessionKey = prepareSession(talkIdInfo);
        String pubkeyB=null;
        if(!talkIdInfo.getIdType().equals(TalkUnit.IdType.FID))return null;
        if(sessionKey==null){
            pubkeyB = talkIdInfo.getPubkey();
        }
        TalkUnit rawTalkUnit = new TalkUnit(myFid, words, dataType, talkIdInfo.getId(),talkIdInfo.getIdType());
        talkUnit = makeTalkUnit(rawTalkUnit, sessionKey, myPrikey, pubkeyB);
        if(talkUnit==null)return null;
        boolean done = sendTalkUnit(talkUnit,this.sessionKey, myPrikey, dealerPubkey);
        talkUnitHandler.saveTalkUnit(rawTalkUnit, done);

        return talkUnit;
    }


    private byte[] prepareSession(TalkIdInfo talkIdInfo) {
        FcSession fcSession = sessionHandler.getSessionByUserId(talkIdInfo.getId());
        if(fcSession!=null) return fcSession.getKeyBytes();
        boolean isOwner = myFid.equals(talkIdInfo.getOwner());
        byte[] newSessionKey = null;
        displayer.pause();
        cleanBr(br);
        System.out.println("The key of "+talkIdInfo.getShowName()+ " is missed in current client.");
        System.out.println("'a' to ask key from it.");
        System.out.println("'g' to generate a new one and share it to others.");
        System.out.println("'ao' to ask key from others.");
        System.out.println("'am' to ask key from the other clients of yourself.");
        System.out.println("other to cancel current operation.");
        String input = Inputer.inputString(br);
        switch (input){
            case "a" -> {
                askKey(talkIdInfo.getId(), talkIdInfo.getId(),talkIdInfo.getIdType(), apipClient, br);
                displayNotice("Sent askKey request to "+talkIdInfo.getId());
                displayer.resumeDisplay();
                return null;
            }
            case "g" -> {
                if(!isOwner && !talkIdInfo.getIdType().equals(TalkUnit.IdType.FID)){
                    System.out.println("You are not the owner. You can't generate a new key.");
                    displayer.resumeDisplay();
                    break;
                }
                String pubkey=null;
                if(talkIdInfo.getIdType().equals(TalkUnit.IdType.FID)){
                    pubkey = KeyTools.getPubkey(talkIdInfo.getId(),sessionHandler,talkIdHandler, contactHandler,apipClient);
                }
                FcSession newFcSession = sessionHandler.addNewSession(talkIdInfo.getId(), pubkey);

                if(talkIdInfo.getIdType().equals(TalkUnit.IdType.FID)){
                    shareSessionKey(talkIdInfo.getId(), newFcSession.getKeyBytes());
                    shareSessionKey(myFid, newFcSession.getKeyBytes());
                }else{
                    List<String> memberList = getMembers(talkIdInfo);
                    for(String fid:memberList) {
                        shareSessionKey(fid,newFcSession.getKeyBytes());
                    }
                }
                displayNotice("New session is generated and shared.");
                displayer.resumeDisplay();
                return newFcSession.getKeyBytes();
            }

            case "ao" -> {
                if(!talkIdInfo.getIdType().equals(TalkUnit.IdType.FID)){
                    talkIdInfo = searchCidOrFid(apipClient, br);
                }
                if(talkIdInfo==null)return null;
                askKey(talkIdInfo.getId(), null,talkIdInfo.getIdType(), apipClient, br);
                displayNotice("Sent askKey request to others.");
                displayer.resumeDisplay();
                return null;
            }
            case "am" -> {
                askKey(talkIdInfo.getId(), myFid, TalkUnit.IdType.FID, apipClient, br);
                displayNotice("Sent askKey request to your clients.");
                displayer.resumeDisplay();
                return null;
            }
            default -> {
                displayer.resumeDisplay();
                return null;
            }
        }
        displayer.resumeDisplay();
        return newSessionKey;
    }

    private void cleanBr(BufferedReader br) {
        try {
            while (br.ready()) {
                br.read();
            }
        } catch (IOException e) {
            System.err.println("Error clearing input buffer: " + e.getMessage());
        }
    }

    private void displayNotice(String message) {
        displayer.displayAppNotice(message);
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
                # search CID, FID, GID, or TID as talkId:
                    - input cid, fid, group, or team, and add the search string after a space.
                    such as: 'cid nasa', 'team 2ab4'
                # $ command without recipient:
                    - After the the recipient and begin with '$', such as '$fresh mail'
                    - Requests for server: addMember,removeMember,updateItems
                    - Requests for users: askKey, askData, askHat
                # Show the instruction:
                    ?
                # Exit:
                    exit
                ---------------------
                """);
    }


    private void exit() {
        displayer.stopDisplay();
        // Add this line to exit the entire program
        System.exit(0);
    }

    private TalkIdInfo processSingleInput(String input) throws IOException {
        String talkId = talkIdHandler.getTalkIdFromTempName(input);
        TalkIdInfo talkIdInfo = talkIdHandler.get(talkId);

        if (talkIdInfo != null) return talkIdInfo;

        List<TalkIdInfo> talkIdInfos = searchTalkIdInfos(input);
        if (talkIdInfos.isEmpty()) {
            System.out.println("It's unknown talk ID. Try add the type(cid,fid,group,or team) before it.");
            return null;
        }
        if (talkIdInfos.size() == 1) {
            return talkIdInfos.get(0);
        } else {
            return (TalkIdInfo) Inputer.chooseOneFromList(talkIdInfos, FieldNames.SHOW_NAME, "Choose who or where you are talking to:", br);
        }
    }
    public void stop() {
        running = false;
        if (channel != null) {
            channel.close();
            channel = null;
        }
        if (group != null) {
            group.shutdownGracefully();
            group = null;
        }
    }

    public boolean sendTalkUnit(TalkUnit talkUnit, byte[] sessionKey, byte[] myPrikey, String pubkey) {
        TalkUnit.checkSelfUnitEncrypt(talkUnit);
        CryptoDataByte cryptoDataByte = talkUnit.encryptUnit(sessionKey, myPrikey, pubkey, talkUnit.getUnitEncryptType());
        log.debug("Encrypted talkUnit for send:"+cryptoDataByte.toNiceJson());
        if(cryptoDataByte.getCode()!=0){
            log.debug("Failed to encrypt talk unit:"+cryptoDataByte.getMessage());
            return false;
        }
        return sendBytes(cryptoDataByte.toBundle());
    }

    public boolean sendMessage(String message) {
        return TalkUnitSender.sendBytesByNettyChannel(message.getBytes(),channel);
    }

    public boolean sendBytes(byte[] bytes){
        return TalkUnitSender.sendBytesByNettyChannel(bytes,channel);
    }

    public void askKey() {
        displayer.pause();
        Menu menu = new Menu();
        menu.setTitle("Choose type of ID to ask key for:");
        menu.add("FID");
        menu.add("Group");
        menu.add("Team");
        menu.add("Cancel");

        int choice = menu.choose(br);
        String id = null;
        TalkUnit.IdType type = null;

        switch (choice) {
            case 1 -> {
                System.out.println("Search for FID:");
                TalkIdInfo fidInfo = searchCidOrFid(apipClient, br);
                if (fidInfo != null) {
                    id = fidInfo.getId();
                    type = TalkUnit.IdType.FID;
                }
            }
            case 2 -> {
                System.out.println("Search for Group:");
                TalkIdInfo groupInfo = searchGroup(Inputer.inputString(br, "Enter group search term:"));
                if (groupInfo != null) {
                    id = groupInfo.getId();
                    type = TalkUnit.IdType.GROUP;
                }
            }
            case 3 -> {
                System.out.println("Search for Team:");
                TalkIdInfo teamInfo = searchTeam(Inputer.inputString(br, "Enter team search term:"));
                if (teamInfo != null) {
                    id = teamInfo.getId();
                    type = TalkUnit.IdType.TEAM;
                }
            }
            default -> {
                displayer.resumeDisplay();
                return;
            }
        }

        if (id != null && type != null) {
            askKey(id, null,type, apipClient, br);
        }
        
        displayer.resumeDisplay();
    }

    public static TalkUnit request(String fromFid, TalkUnit.IdType toType, String to, Op op, Object data, byte[] sessionKey, byte[] prikey, String pubkey) {
        RequestBody requestBody = new RequestBody();
        requestBody.setOp(op);
        requestBody.setData(data);

        if(sessionKey==null && pubkey==null){
            return null;
        } 

        CryptoDataByte cryptoDataByte;
        if (sessionKey != null) {
            Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
            cryptoDataByte = encryptor.encryptBySymkey(requestBody.toBytes(), sessionKey);
        } else {
            Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
            cryptoDataByte = encryptor.encryptByAsyTwoWay(requestBody.toBytes(), prikey, Hex.fromHex(pubkey));
        }

        if (cryptoDataByte == null || cryptoDataByte.getCode() != 0) {
            System.out.println("Failed to encrypt data.");
            return null;
        }

        return new TalkUnit(fromFid, cryptoDataByte.toBundle(), DataType.ENCRYPTED_REQUEST, to, toType);
    }

    public List<TalkIdInfo> searchTalkIdInfos(String searchString) {
        return talkIdHandler.search(searchString);
    }

    public TalkUnit readTalkUnit(DataInputStream dis){
        byte[] receivedBytes;

        TalkUnit talkUnit;
        try {
            receivedBytes = TcpUtils.readBytes(dis);
            if(receivedBytes==null)return null;

            CryptoDataByte cryptoDataByte = CryptoDataByte.fromBundle(receivedBytes);
            if(cryptoDataByte!=null) {
                if (cryptoDataByte.getType().equals(EncryptType.Symkey))
                    cryptoDataByte.setSymkey(sessionKey);
                else if (cryptoDataByte.getType().equals(EncryptType.AsyTwoWay))
                    cryptoDataByte.setPrikeyB(Decryptor.decryptPrikey(apiAccount.getUserPrikeyCipher(), symkey));

                new Decryptor().decrypt(cryptoDataByte);
                if (cryptoDataByte.getCode() != 0) return null;
                talkUnit = TalkUnit.fromBytes(cryptoDataByte.getData());
            }else {
                talkUnit = TalkUnit.fromBytes(receivedBytes);
            }
            return talkUnit;

        }catch (Exception e){
            System.out.println("Failed to read talkUnit:"+e.getMessage());
            return null;
        }
    }


    public static byte[] decryptSessionKey(byte[] cipherBytes, byte[] symkey) {
        Decryptor decryptor = new Decryptor();
        CryptoDataByte cryptoDataByte = decryptor.decrypt(cipherBytes,symkey);
        if(cryptoDataByte.getCode()!=0)return null;
        return cryptoDataByte.getData();
    }

    public TalkIdInfo searchCidOrFid(ApipClient apipClient, BufferedReader br) {
        Cid cid = apipClient.searchCidOrFid(br);
        if(cid ==null)return null;
        return TalkIdInfo.fromCidInfo(cid);
    }


    public static void showTalkIdInfos(List<TalkIdInfo> talkIdInfoList){
        if(talkIdInfoList==null || talkIdInfoList.isEmpty())return;
        String title = "Talk Id List";
        String[] fields = {TYPE,SHOW_NAME};
        int[] widths = {10,20};
        List<List<Object>> valueListList = new ArrayList<>();
        for(TalkIdInfo talkIdInfo:talkIdInfoList)valueListList.add(Arrays.asList(talkIdInfo.getIdType(),talkIdInfo.getShowName()));
        Shower.showOrChooseList(title, fields, widths, valueListList, null);
    }


    public void shareSessionKey(String fid, byte[] sessionKey) {
        TalkUnit talkUnit;
        ReplyBody replier = new ReplyBody();
        replier.setOp(Op.SHARE_KEY);
        replier.setData(Hex.toHex(sessionKey));

        TalkIdInfo talkIdInfo = TalkIdInfo.fidTalkIdInfo(fid);
        TalkUnit rawTalkUnit = new TalkUnit(myFid, replier, ENCRYPTED_REPLY, talkIdInfo.getId(),talkIdInfo.getIdType());
        talkUnit = makeTalkUnit(rawTalkUnit,sessionKey, myPrikey, talkIdInfo.getPubkey());
        if(talkUnit == null)return;
        boolean done = sendTalkUnit(talkUnit, sessionKey, myPrikey, dealerPubkey);
        talkUnitHandler.saveTalkUnit(rawTalkUnit, done);
    }

    public List<String> getMembers(TalkIdInfo talkIdInfo){
        return switch (talkIdInfo.getIdType()){
            case GROUP -> getGroupMembers(talkIdInfo.getId(),apipClient);
            case TEAM -> getTeamMembers(talkIdInfo.getId(),apipClient);
            default -> null;
        };
    }

    public List<String> getGroupMembers(String id, ApipClient apipClient) {
        Map<String, Group> groupMap = apipClient.groupByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, id);
        if(groupMap==null || groupMap.isEmpty())return null;
        Group group = groupMap.get(id);
        return Arrays.asList(group.getMembers());
    }

    public List<String> getTeamMembers(String id, ApipClient apipClient) {
        Map<String, Team> teamMap = apipClient.teamByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, id);
        if(teamMap==null || teamMap.isEmpty())return null;
        Team team = teamMap.get(id);
        return Arrays.asList(team.getMembers());
    }

    ////////////////////////////////////////////////////////////////

    public TalkIdInfo searchInfo(String input) throws IOException {
        TalkIdInfo talkIdInfo = new TalkIdInfo();
        if ("".equals(input)) {
            showInstruction();
            return null;
        }
        String idType = StringUtils.getWordAtPosition(input, 1);
        String part = StringUtils.getWordAtPosition(input, 2);
        if(idType==null || part==null){
            System.out.println("""
                    To search, the type and the content are required.
                    The type could be 'cid', 'fid', 'group', 'team', 'hat' or 'did'.
                    The content could be a part of the name or ID.""");
        }
        if (idType == null) return null;
        idType = idType.toLowerCase();

        switch (idType) {
            case FieldNames.CID -> {
                talkIdInfo = searchCid(part, br);
                if (talkIdInfo == null) {
                    System.out.println("CID not found. Try again.");
                    return null;
                }
            }
            case FID -> {
                talkIdInfo = searchFid(part, br);
                if (talkIdInfo == null) {
                    System.out.println("FID not found. Try again.");
                    return null;
                }
            }
            case FieldNames.GROUP -> {
                talkIdInfo = searchGroup(part);
                if (talkIdInfo == null) {
                    System.out.println("Group not found. Try again.");
                    return null;
                }
            }
            case FieldNames.TEAM -> {
                talkIdInfo = searchTeam(part);
                if (talkIdInfo == null) {
                    System.out.println("Team not found. Try again.");
                    return null;
                }
            }
            case FieldNames.HAT ,FieldNames.DID -> {
                //String fromFid, TalkUnit.IdType toType, String to, Op op, Object data, byte[] sessionKey, byte[] prikey, byte[] pubkey
                RequestBody requestBody = new RequestBody();
                requestBody.setOp(Op.ASK_HAT);
                requestBody.setData(part);
                TalkUnit rawTalkUnit = new TalkUnit(myFid, requestBody, ENCRYPTED_REQUEST, talkIdInfo.getId(),talkIdInfo.getIdType());
                TalkUnit talkUnit = TalkUnit.makeTalkUnit(rawTalkUnit,sessionKey,myPrikey,dealerPubkey);//request(myFid, TalkUnit.IdType.FID,dealer,Op.ASK_HAT,part, sessionKey,myPrikey,dealerPubkey);
                if(talkUnit!=null){
                    boolean done = sendTalkUnit(talkUnit, sessionKey, myPrikey, dealerPubkey);
                    talkUnitHandler.saveTalkUnit(rawTalkUnit, done);
                }
            }

            default -> throw new IllegalStateException("Unexpected value: " + idType);
        }

        return talkIdInfo;
    }

    public TalkIdInfo searchFid(String part, BufferedReader br) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(FID).addNewValue(part);
        List<Cid> result = apipClient.fidSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        Cid fid = Inputer.chooseOneFromList(result, FID, "Choose the FID:", br);
        if (fid == null) return null;

        TalkIdInfo talkIdInfo = new TalkIdInfo();
        talkIdInfo.setId(fid.getId());
        talkIdInfo.setIdType(TalkUnit.IdType.FID);

        Cid cid = apipClient.cidInfoById(fid.getId());
        if (cid != null) {
            talkIdInfo.setShowName(cid.getCid());
        }

        return talkIdInfo;
    }

    public TalkIdInfo searchCid(String part, BufferedReader br) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(FieldNames.USED_CIDS).addNewValue(part);
        List<Cid> result = apipClient.cidSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        Cid cid = Inputer.chooseOneFromList(result, FieldNames.CID, "Choose the CID:", br);
        if (cid == null) return null;

        TalkIdInfo talkIdInfo = new TalkIdInfo();
        talkIdInfo.setId(cid.getId());
        talkIdInfo.setShowName(cid.getCid());
        talkIdInfo.setIdType(TalkUnit.IdType.FID);

        return talkIdInfo;
    }

    public  TalkIdInfo searchGroup(String part) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(FieldNames.GID, FieldNames.NAME, Values.DESC).addNewValue(part);
        List<Group> result = apipClient.groupSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        Group group = Inputer.chooseOneFromList(result, NAME, "Choose the group:", br);
        if (group == null) return null;
        return TalkIdInfo.fromGroup(group);
    }

    public TalkIdInfo searchTeam(String part) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(FieldNames.TID, FieldNames.STD_NAME, FieldNames.LOCAL_NAMES, Values.DESC).addNewValue(part);
        List<Team> result = apipClient.teamSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        Team team = Inputer.chooseOneFromList(result, NAME, "Choose the team:", br);
        if (team == null) return null;
        return TalkIdInfo.fromTeam(team);
    }

    public TalkIdInfo searchTalkId(String part) {
        TalkIdInfo result = null;

        result = searchGroup(part, result);

        if(result==null)
            result = searchTeam(part, result);

        return result;
    }

    public TalkIdInfo searchGroup(String part, TalkIdInfo result) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(FieldNames.GID, NAME, Values.DESC).addNewValue(part);

        List<Group> groupList = apipClient.groupSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if (!groupList.isEmpty()) {
            Group group;
            if (groupList.size() == 1) {
                group = groupList.get(0);
                result = TalkIdInfo.fromGroup(group);
            } else {
                group = Inputer.chooseOneFromList(groupList, NAME, "Choose the group.", br);
                if (group != null) {
                    result = TalkIdInfo.fromGroup(group);
                }
            }
        }
        return result;
    }

    public TalkIdInfo searchTeam(String part, TalkIdInfo result) {
        Fcdsl fcdsl;
        fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(FieldNames.TID, FieldNames.STD_NAME, FieldNames.LOCAL_NAMES, Values.DESC).addNewValue(part);
        Team team;
        List<Team> teamList = apipClient.teamSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if (!teamList.isEmpty()) {
            if (teamList.size() == 1) {
                team = teamList.get(0);
                result = TalkIdInfo.fromTeam(team);
            } else {
                team = Inputer.chooseOneFromList(teamList, NAME, "Choose the team.", br);
                if (team != null) {
                    result = TalkIdInfo.fromTeam(team);
                }
            }
        }
        return result;
    }

    public TalkIdInfo getFidTalkInfo(String fid, BufferedReader br, ApipClient apipClient) {
        TalkIdInfo talkIdInfo = talkIdHandler.get(fid);
        if (talkIdInfo != null)
            return talkIdInfo;

        ContactDetail contactDetail = contactHandler.getContact(fid);
        if (contactDetail != null) {
            talkIdInfo = TalkIdInfo.fromContact(contactDetail);
            talkIdHandler.put(fid, talkIdInfo);
            return talkIdInfo;
        }

        Cid cid = apipClient.searchCidOrFid(br);
        if (cid != null) {
            talkIdInfo = TalkIdInfo.fromCidInfo(cid);
            talkIdHandler.put(fid, talkIdInfo);
            return talkIdInfo;
        }
        return null;
    }

    public synchronized TalkIdInfo chooseOneTalkIdInfo(List<TalkIdInfo> talkIdInfoList) throws IOException {

        if (talkIdInfoList.isEmpty()) {
            System.out.println("No matching CIDs found.");
            return null;
        }

        // Let user choose one from the list
        return Inputer.chooseOneFromList(talkIdInfoList, "showName", "Choose one:", br);
    }

    public synchronized List<TalkIdInfo> findTalkIdInfos(String searchString,boolean allResources   ) throws IOException {
        List<TalkIdInfo> results = new ArrayList<>();
        results.addAll(findCid(searchString,allResources));
        results.addAll(findFid(searchString,allResources));
        results.addAll(findGroup(searchString,allResources));
        results.addAll(findTeam(searchString,allResources));
        return results;
    }

    public synchronized List<TalkIdInfo> findCid(String searchString,boolean allResources) throws IOException {
        List<TalkIdInfo> results = new ArrayList<>();
        String lowerSearchString = searchString.toLowerCase();

        // 1. Search using TalkIdHandler
        results.addAll(talkIdHandler.search(searchString));

        // 2. Search in contacts using ContactHandler
        if(results.isEmpty() || allResources) {
            List<ContactDetail> contacts = contactHandler.searchContacts(lowerSearchString);
            for (ContactDetail contact : contacts) {
                if (contact.getCid() != null && contact.getCid().toLowerCase().contains(lowerSearchString)) {
                    results.add(TalkIdInfo.fromContact(contact));
                }
            }
        }

        // 3. Search with apipClient.cidInfoSearch
        if(results.isEmpty() || allResources) {
            Fcdsl fcdsl = new Fcdsl();
            fcdsl.addNewQuery().addNewPart().addNewFields(FieldNames.USED_CIDS).addNewValue(searchString);
            List<Cid> apiResults = apipClient.cidSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);

            for (Cid cid : apiResults) {
                results.add(TalkIdInfo.fromCidInfo(cid));
                // Add to TalkIdHandler cache
                talkIdHandler.put(cid.getId(), TalkIdInfo.fromCidInfo(cid));
            }
        }

        // Remove duplicates based on the ID
        return results.stream()
                .distinct()
                .collect(Collectors.toList());
    }

    public synchronized List<TalkIdInfo> findFid(String searchString,boolean allResources) throws IOException {
        List<TalkIdInfo> results = new ArrayList<>();
        String lowerSearchString = searchString.toLowerCase();

        // 1. Search using TalkIdHandler
        results.addAll(talkIdHandler.search(searchString));

        // 2. Search in contacts using ContactHandler
        if(results.isEmpty() || allResources) {
            List<ContactDetail> contacts = contactHandler.searchContacts(searchString);
            for (ContactDetail contact : contacts) {
                if (contact.getFid() != null && contact.getFid().toLowerCase().contains(lowerSearchString)) {
                    results.add(TalkIdInfo.fromContact(contact));
                }
            }
        }

        // 3. Search with apipClient.cidInfoSearch
        if(results.isEmpty() || allResources) {
            Fcdsl fcdsl = new Fcdsl();
            fcdsl.addNewQuery().addNewPart().addNewFields(FieldNames.FID).addNewValue(searchString);
            List<Cid> apiResults = apipClient.cidSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);

            for (Cid cid : apiResults) {
                results.add(TalkIdInfo.fromCidInfo(cid));
                // Add to TalkIdHandler cache
                talkIdHandler.put(cid.getId(), TalkIdInfo.fromCidInfo(cid));
            }
        }

        // Remove duplicates and return
        return results.stream()
                .distinct()
                .collect(Collectors.toList());
    }

    public synchronized List<TalkIdInfo> findGroup(String searchString,boolean allResources) throws IOException {
        List<TalkIdInfo> results = new ArrayList<>();

        // 1. Search using TalkIdHandler
        results.addAll(talkIdHandler.search(searchString).stream()
                .filter(info -> info.getIdType() == TalkUnit.IdType.GROUP)
                .collect(Collectors.toList()));

        // 2. Search with apipClient.groupSearch
        if(results.isEmpty() || allResources) {
            Fcdsl fcdsl = new Fcdsl();
            fcdsl.addNewQuery().addNewPart().addNewFields(FieldNames.GID, FieldNames.NAME, Values.DESC).addNewValue(searchString);
            List<Group> apiResults = apipClient.groupSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);

            for (Group group : apiResults) {
                TalkIdInfo info = TalkIdInfo.fromGroup(group);
                results.add(info);
                // Add to TalkIdHandler cache
                talkIdHandler.put(group.getId(), info);
            }
        }

        // Remove duplicates and return
        return results.stream()
                .distinct()
                .collect(Collectors.toList());
    }

    public synchronized List<TalkIdInfo> findTeam(String searchString,boolean allResources) throws IOException {
        List<TalkIdInfo> results = new ArrayList<>();

        // 1. Search using TalkIdHandler
        results.addAll(talkIdHandler.search(searchString).stream()
                .filter(info -> info.getIdType() == TalkUnit.IdType.TEAM)
                .collect(Collectors.toList()));

        // 2. Search with apipClient.groupSearch
        if(results.isEmpty() || allResources) {
            Fcdsl fcdsl = new Fcdsl();
            fcdsl.addNewQuery().addNewPart().addNewFields(FieldNames.TID, FieldNames.STD_NAME, FieldNames.LOCAL_NAMES, Values.DESC).addNewValue(searchString);
            List<Team> apiResults = apipClient.teamSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);

            for (Team team : apiResults) {
                TalkIdInfo info = TalkIdInfo.fromTeam(team);
                results.add(info);
                // Add to TalkIdHandler cache
                talkIdHandler.put(team.getId(), info);
            }
        }

        // Remove duplicates and return
        return results.stream()
                .distinct()
                .collect(Collectors.toList());
    }

    public TalkIdInfo getTalkIdInfoById(String talkId) {
        if(talkId==null) return null;
        TalkIdInfo talkIdInfo = null;

        // 1. Check talkIdHandler
        talkIdInfo = talkIdHandler.get(talkId);
        if (talkIdInfo != null) return talkIdInfo;

        // 2. Check contactHandler
        ContactDetail contactDetail = contactHandler.getContact(talkId);
        if (contactDetail != null) {
            talkIdInfo = TalkIdInfo.fromContact(contactDetail);
            talkIdHandler.put(talkId, talkIdInfo);
            return talkIdInfo;
        }

        // 4. Check groupHandler
        Group group = groupHandler.getGroupInfo(talkId, apipClient);
        if (group != null) {
            talkIdInfo = TalkIdInfo.fromGroup(group);
            talkIdHandler.put(talkId, talkIdInfo);
            return talkIdInfo;
        }

        // 5. Check teamHandler
        Team team = teamHandler.getTeamInfo(talkId, apipClient);
        if (team != null) {
            talkIdInfo = TalkIdInfo.fromTeam(team);
            talkIdHandler.put(talkId, talkIdInfo);
            return talkIdInfo;
        }

        // 6. Check apipClient.cidInfoByIds as last resort
        Map<String, Cid> cidInfoMap = apipClient.cidInfoByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, talkId);
        if (cidInfoMap != null && !cidInfoMap.isEmpty()) {
            Cid cid = cidInfoMap.get(talkId);
            if (cid != null) {
                talkIdInfo = TalkIdInfo.fromCidInfo(cid);
                talkIdHandler.put(talkId, talkIdInfo);
                return talkIdInfo;
            }
        }

        return null;
    }
    public boolean isRunning() {
        return running;
    }
    public void setRunning(boolean running) {
        this.running = running;
    }
    public EventLoopGroup getGroup() {
        return group;
    }
    public void setGroup(EventLoopGroup group) {
        this.group = group;
    }
    public Channel getChannel() {
        return channel;
    }
    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public ConcurrentLinkedQueue<TalkUnit> getReceivedQueue() {
        return receivedQueue;
    }


    public CidManager getCidHandler() {
        return cidHandler;
    }

    public void setCidHandler(CidManager cidHandler) {
        this.cidHandler = cidHandler;
    }

    public CashManager getCashHandler() {
        return cashHandler;
    }

    public void setCashHandler(CashManager cashHandler) {
        this.cashHandler = cashHandler;
    }

    public SessionManager getSessionHandler() {
        return sessionHandler;
    }

    public void setSessionHandler(SessionManager sessionHandler) {
        this.sessionHandler = sessionHandler;
    }

    public MailManager getMailHandler() {
        return mailHandler;
    }

    public void setMailHandler(MailManager mailHandler) {
        this.mailHandler = mailHandler;
    }

    public ContactManager getContactHandler() {
        return contactHandler;
    }

    public void setContactHandler(ContactManager contactHandler) {
        this.contactHandler = contactHandler;
    }

    public GroupManager getGroupHandler() {
        return groupHandler;
    }

    public void setGroupHandler(GroupManager groupHandler) {
        this.groupHandler = groupHandler;
    }

    public TeamManager getTeamHandler() {
        return teamHandler;
    }

    public void setTeamHandler(TeamManager teamHandler) {
        this.teamHandler = teamHandler;
    }

    public BufferedReader getBr() {
        return br;
    }

    public Displayer getDisplayer() {
        return displayer;
    }

    public void setDisplayer(Displayer displayer) {
        this.displayer = displayer;
    }

    public String getMyFid() {
        return myFid;
    }

    public void setMyFid(String myFid) {
        this.myFid = myFid;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public byte[] getMyPrikey() {
        return myPrikey;
    }

    public void setMyPrikey(byte[] myPrikey) {
        this.myPrikey = myPrikey;
    }

    public String getDealer() {
        return dealer;
    }

    public void setDealer(String dealer) {
        this.dealer = dealer;
    }

    public TalkIdManager getTalkIdHandler() {
        return talkIdHandler;
    }

    public TalkUnitManager getTalkUnitHandler() {
        return talkUnitHandler;
    }

    public Settings getSettings() {
        return settings;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    @Nullable
    public TalkUnit parseTalkUnitData(final TalkUnit talkUnit, final byte[] prikey) {
        if (talkUnit == null || talkUnit.getData() == null) return null;

        switch (talkUnit.getDataType()) {
            case TEXT, BYTES, REQUEST, REPLY,
                    SIGNED_TEXT, SIGNED_BYTES, SIGNED_REQUEST, SIGNED_HAT, SIGNED_REPLY -> {
                return  talkUnit;
            }
            case ENCRYPTED_REQUEST, ENCRYPTED_BYTES, ENCRYPTED_TEXT, ENCRYPTED_HAT,
                    ENCRYPTED_SIGNED_BYTES, ENCRYPTED_REPLY, ENCRYPTED_SIGNED_HAT,
                    ENCRYPTED_SIGNED_REPLY, ENCRYPTED_SIGNED_REQUEST, ENCRYPTED_SIGNED_TEXT -> {
                CryptoDataByte cryptoDataByte = decryptTalkUnitData(talkUnit, prikey);
                if (cryptoDataByte == null) return null;

                switch (talkUnit.getDataType()) {
                    case ENCRYPTED_BYTES -> setDataAsBytes(talkUnit, cryptoDataByte.getData());
                    case ENCRYPTED_TEXT -> setDataAsText(talkUnit, cryptoDataByte.getData());
                    case ENCRYPTED_HAT -> setDataAsHat(talkUnit, cryptoDataByte.getData());
                    case ENCRYPTED_REQUEST -> setDataAsRequest(talkUnit, cryptoDataByte.getData());
                    case ENCRYPTED_REPLY -> setDataAsReply(talkUnit, cryptoDataByte.getData());
                    case ENCRYPTED_SIGNED_BYTES, 
                        ENCRYPTED_SIGNED_TEXT, 
                        ENCRYPTED_SIGNED_HAT, 
                        ENCRYPTED_SIGNED_REPLY, 
                        ENCRYPTED_SIGNED_REQUEST -> {
                        Signature signature = Signature.fromBundle(cryptoDataByte.getData());
                        if(signature.getMsg()==null)signature.setMsg(Hex.toHex(talkUnit.getIdBytes().getBytes()));
                        if(!signature.verify()) return null;
                        switch (talkUnit.getDataType()) {
                            case ENCRYPTED_SIGNED_BYTES -> setDataAsBytes(talkUnit, cryptoDataByte.getData());
                            case ENCRYPTED_SIGNED_TEXT -> setDataAsText(talkUnit, cryptoDataByte.getData());
                            case ENCRYPTED_SIGNED_HAT -> setDataAsHat(talkUnit, cryptoDataByte.getData());
                            case ENCRYPTED_SIGNED_REQUEST -> setDataAsRequest(talkUnit, cryptoDataByte.getData());
                            case ENCRYPTED_SIGNED_REPLY -> setDataAsReply(talkUnit, cryptoDataByte.getData());
                            default -> {
                                return null;
                            }
                        }
                    }
                    case ENCRYPTED_ID_SIGNED_BYTES, ENCRYPTED_ID_SIGNED_TEXT, ENCRYPTED_ID_SIGNED_HAT,
                        ENCRYPTED_ID_SIGNED_REPLY, ENCRYPTED_ID_SIGNED_REQUEST -> {
                        IdSignature idSignature = IdSignature.fromBytes(cryptoDataByte.getData(), IdSignature.class);
                        if(!idSignature.verify(talkUnit.getId())) return null;
                        talkUnit.setData(idSignature.getData());
                        switch (talkUnit.getDataType()) {
                            case ENCRYPTED_ID_SIGNED_BYTES -> setDataAsBytes(talkUnit, cryptoDataByte.getData() );
                            case ENCRYPTED_ID_SIGNED_TEXT -> setDataAsText(talkUnit, cryptoDataByte.getData());
                            case ENCRYPTED_ID_SIGNED_HAT -> setDataAsHat(talkUnit, cryptoDataByte.getData());
                            case ENCRYPTED_ID_SIGNED_REQUEST -> setDataAsRequest(talkUnit, cryptoDataByte.getData());
                            case ENCRYPTED_ID_SIGNED_REPLY -> setDataAsReply(talkUnit, cryptoDataByte.getData());
                            default -> {
                                return null;
                            }
                        }
                    }
                    default -> {
                        return null;
                    }
                }
            }
            default -> {
                return null;
            }
        }
        return talkUnit;
    }

    private static void setDataAsBytes(TalkUnit talkUnit, byte[] data) {
        talkUnit.setData(data);
        talkUnit.setDataType(DataType.BYTES);
    }

    private static void setDataAsText(TalkUnit talkUnit, byte[] data) {
        talkUnit.setData(new String(data));
        talkUnit.setDataType(DataType.TEXT);
    }

    private static void setDataAsHat(TalkUnit talkUnit, byte[] data) {
        Hat hat = Hat.fromBytes(data, Hat.class);
        if(hat == null) return;
        talkUnit.setData(hat);
        talkUnit.setDataType(DataType.HAT);
    }

    private static void setDataAsRequest(TalkUnit talkUnit, byte[] data) {
        RequestBody requestBody = RequestBody.fromBytes(data, RequestBody.class);
        if(requestBody == null) return;
        talkUnit.setData(requestBody);
        talkUnit.setDataType(DataType.REQUEST);
    } 

    private static void setDataAsReply(TalkUnit talkUnit, byte[] data) {
        ReplyBody replyBody = ReplyBody.fromBytes(data, ReplyBody.class);
        if(replyBody == null) return;
        talkUnit.setData(replyBody);
        talkUnit.setDataType(DataType.REPLY);
    }   


    @Nullable
    private CryptoDataByte decryptTalkUnitData(final TalkUnit talkUnit, byte[] prikey) {
        byte[] bytes = (byte[])talkUnit.getData();
        Decryptor decryptor;
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromBundle(bytes);
        if (cryptoDataByte == null) return null;
        EncryptType encryptType = cryptoDataByte.getType();

        if(encryptType.equals(EncryptType.Symkey)) {
            if(cryptoDataByte.getKeyName() == null) return null;
            String keyName = Hex.toHex(cryptoDataByte.getKeyName());
            FcSession session  = sessionHandler.getSessionByName(keyName);
            if(session == null) return null;
            byte[] senderSessionKey = session.getKeyBytes();
            if(senderSessionKey == null) return null;
            decryptor = new Decryptor();
            cryptoDataByte = decryptor.decrypt(bytes, senderSessionKey);
            talkUnit.setFrom(session.getUserId());
        }else if (encryptType.equals(EncryptType.AsyTwoWay) && prikey != null) {
            decryptor = new Decryptor();
            cryptoDataByte.setPrikeyB(prikey);
            cryptoDataByte = decryptor.decrypt(cryptoDataByte);
            byte[] senderPubkey = cryptoDataByte.getPubkeyA();
            if(senderPubkey == null) return null;
            talkUnit.setFrom(KeyTools.pubkeyToFchAddr(senderPubkey));
        } else return null;

        if (cryptoDataByte.getCode() != 0) {
            System.out.println("Failed to decrypt data from " + talkUnit.getTo());
            return null;
        }
        return cryptoDataByte;
    }

    // public TalkUnit makeReplyTalkUnit(TalkUnit requestTalkUnit, Op op, int code, String message, Object data) {
    //     TalkUnit replyTalkUnit = new TalkUnit();
    //     replyTalkUnit.setTo(requestTalkUnit.getFrom());

    //     FcReplier replyBody = new FcReplier();
    //     replyBody.setOp(op);
    //     replyBody.setCode(code);
    //     replyBody.setMessage(message);
    //     replyBody.setNonce(requestTalkUnit.getNonce());
    //     replyBody.setTime(requestTalkUnit.getTime());
    //     replyBody.setData(data);
    //     byte[] replyBytes = replyBody.toBytes();

    //     FcSession session = sessionHandler.getSessionByName(requestTalkUnit.getFrom());
    //     if(session!=null){
    //         String cipher = encryptBySymkey(replyBytes,session.getKeyBytes());
    //         replyTalkUnit.setData(cipher);
    //         replyTalkUnit.setDataType(DataType.ENCRYPTED_REPLY);
    //     }else{
    //         String pubkey = getPubkey(requestTalkUnit.getFrom(), apipClient);
    //         if(pubkey==null)return null;
    //         String cipher = new Encryptor().encryptByAsyTwoWay(replyBytes,myPrikey,Hex.fromHex(pubkey)).toJson();
    //         replyTalkUnit.setData(cipher);
    //         replyTalkUnit.setDataType(DataType.ENCRYPTED_REPLY);
    //     }
    //     return replyTalkUnit;
    // }

    private void askKey(String whoSKey, String askWhom, TalkUnit.IdType type, ApipClient apipClient, BufferedReader br) {
        if (askWhom != null) {
            if (!KeyTools.isGoodFid(askWhom)) {
                System.out.println("Invalid FID: " + askWhom);
                return;
            }
            type = TalkUnit.IdType.FID;
        }

        if (type == null) {
            while (true) {
                String choice = Inputer.chooseOne(new String[]{TalkUnit.IdType.FID.name(), TalkUnit.IdType.TEAM.name(), TalkUnit.IdType.GROUP.name()}, null, "Which type ID you are asking session key from? ", br);
                if (choice == null) {
                    System.out.println("Canceled");
                    return;
                }
                type = TalkUnit.IdType.valueOf(choice);
                switch (type) {
                    case FID -> {
                        if (KeyTools.isGoodFid(whoSKey)) break;
                        System.out.println("It is not a FID. Try again.");
                    }
                    case TEAM, GROUP -> {
                        if (Hex.isHex32(whoSKey)) break;
                        System.out.println("It is not a team Id or group Id. Try again.");
                    }
                }
            }
        }

        System.out.println("[APP] Asking the session key of " + whoSKey);

        if (type.equals(TalkUnit.IdType.FID)) {
            // For FID type, directly ask key from the FID itself
            TalkIdInfo talkIdInfo;
            if (askWhom == null) {
                talkIdInfo = talkIdHandler.get(whoSKey);
            } else {
                talkIdInfo = talkIdHandler.get(askWhom);
            }

            if (talkIdInfo == null) {
                System.out.println("No such talk ID: " + whoSKey);
                return;
            }

            TalkUnit rawTalkUnit = new TalkUnit(myFid, new RequestBody(Op.ASK_KEY,whoSKey), DataType.ENCRYPTED_REQUEST, talkIdInfo.getId(),talkIdInfo.getIdType());
            rawTalkUnit.setDataEncryptType(EncryptType.AsyTwoWay);
            TalkUnit talkUnit = makeTalkUnit(rawTalkUnit,null, myPrikey, talkIdInfo.getPubkey());
            if(talkUnit!=null){
                talkUnit.setUnitEncryptType(EncryptType.AsyTwoWay);
                boolean done = sendTalkUnit(talkUnit, sessionKey, myPrikey, dealerPubkey);
                talkUnitHandler.saveTalkUnit(rawTalkUnit, done);
                System.out.println("[APP] Asked the key of "+whoSKey+" from "+talkUnit.getTo());
            }
            return;
        }
        // Rest of the code for TEAM and GROUP types
        String targetFid = askWhom;
        TalkIdInfo talkIdInfo = null;
        while (true) {
            if (targetFid != null) {
                talkIdInfo = talkIdHandler.get(targetFid);
                if (talkIdInfo == null) {
                    System.out.println("You have not connected with " + targetFid + ". Try other.");
                    targetFid = null;
                    continue;
                }else break;
            }
            String ownerFid = null;
            if (type.equals(TalkUnit.IdType.TEAM)) {
                Team team = teamHandler.getTeamInfo(whoSKey, apipClient);
                if (team != null) {
                    ownerFid = team.getOwner();
                }
            } else if (type.equals(TalkUnit.IdType.GROUP)) {
                Group group = groupHandler.getGroupInfo(whoSKey, apipClient);
                if (group != null) {
                    ownerFid = group.getNamers()[group.getNamers().length - 1];
                }
            }

            // Show options to user
            Menu menu = new Menu();
            menu.setTitle("Ask session key from:");
            if (ownerFid != null) {
                menu.add("Owner/Namer (" + ownerFid + ")");
            }
            menu.add("Search another FID");
            menu.add("Cancel");

            int choice = menu.choose(br);

            if (choice == 1 && ownerFid != null) {
                targetFid = ownerFid;
            } else if (choice == 1 || choice == 2) {
                // Search for FID
                System.out.println("Search for FID to ask key from:");
                talkIdInfo = searchCidOrFid(apipClient, br);
                if (talkIdInfo != null) break;
            }
        }
     // If we have a target FID, create and send the ask key request
        Map<String, String> askMap = new HashMap<>();
        if (type.equals(TalkUnit.IdType.TEAM)) {
            askMap.put(FieldNames.TID, whoSKey);
        } else if (type.equals(TalkUnit.IdType.GROUP)) {
            askMap.put(FieldNames.GID, whoSKey);
        }

        RequestBody requestBody = new RequestBody();
        requestBody.setOp(Op.ASK_KEY);
        requestBody.setData(askMap);
        TalkUnit rawTalkUnit = new TalkUnit(myFid, requestBody, DataType.ENCRYPTED_REQUEST, talkIdInfo.getId(),talkIdInfo.getIdType());
        TalkUnit talkUnit = makeTalkUnit(rawTalkUnit, null, myPrikey, talkIdInfo.getPubkey());
        if (talkUnit == null) return;
        boolean done = sendTalkUnit(talkUnit, sessionKey, myPrikey, dealerPubkey);
        talkUnitHandler.saveTalkUnit(rawTalkUnit, done);
    }

    public boolean tryReconnect() {
        for (int attempt = 1; attempt <= MAX_RECONNECT_ATTEMPTS; attempt++) {
            try {
                System.out.println("Attempting to reconnect... (Attempt " + attempt + "/" + MAX_RECONNECT_ATTEMPTS + ")");
                
                // Create new connection
                Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new IdleStateHandler(0, 30, 0));
                            pipeline.addLast(new TalkClientHandler(TalkClient.this));
                        }
                    });
                
                URL url1 = new URL(apiProvider.getApiUrl());
                this.channel = bootstrap.connect(url1.getHost(), url1.getPort()).sync().channel();
                
                if (channel.isActive()) {
                    System.out.println("Successfully reconnected to server");
                    // Reset running state and resend initial message if needed
                    setRunning(false);  // This will trigger the initial handshake again
                    return true;
                }
                
                Thread.sleep(RECONNECT_DELAY_MS);
            } catch (Exception e) {
                System.out.println("Reconnection attempt " + attempt + " failed: " + e.getMessage());
                if (attempt == MAX_RECONNECT_ATTEMPTS) {
                    System.err.println("Failed to reconnect after " + MAX_RECONNECT_ATTEMPTS + " attempts");
                    stop();
                    return false;
                }
            }
        }
        return false;
    }

    public void disply(String string) {
        displayer.displayMessage(string);
    }

    public String getDealerPubkey() {
        return dealerPubkey;
    }

    public void setDealerPubkey(String dealerPubkey) {
        this.dealerPubkey = dealerPubkey;
    }

    public HatManager getHatHandler() {
        return hatHandler;
    }

    public void setHatHandler(HatManager hatHandler) {
        this.hatHandler = hatHandler;
    }

    public DiskManager getDiskHandler() {
        return diskHandler;
    }

    public void setDiskHandler(DiskManager diskHandler) {
        this.diskHandler = diskHandler;
    }
}
