package clients.talkClient;

import apip.apipData.CidInfo;
import apip.apipData.Fcdsl;
import apip.apipData.RequestBody;
import appTools.Inputer;
import appTools.Settings;
import clients.Client;
import clients.apipClient.ApipClient;
import clients.talkClient.UI.RoomInfo;
import configure.ApiAccount;
import configure.ApiProvider;
import configure.ServiceType;
import constants.FieldNames;
import crypto.*;
import fcData.*;
import fch.fchData.Address;
import feip.feipData.Group;
import feip.feipData.Team;
import javaTools.*;
import javaTools.http.AuthType;
import javaTools.http.RequestMethod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import static constants.FieldNames.LAST_TIME;
import static constants.Strings.*;



public class TalkTcpClient extends Client {
    public static PersistentHashMap sessionKeyFileMap = new PersistentHashMap(SID, SESSION_KEY);
    public static PersistentHashMap talkIdInfoFileMap = new PersistentHashMap(SID, TALK_ID_INFO);
    public static PersistentHashMap contactFileMap = new PersistentHashMap(SID, CONTACT);
    public static PersistentHashMap roomInfoFileMap  = new PersistentHashMap(SID, ROOM_INFO);
    public static PersistentHashMap fidCidFileMap  = new PersistentHashMap(SID, FieldNames.CID);
    public static PersistentHashMap fidAvatarFileMap  = new PersistentHashMap(SID, FieldNames.AVATAR);
    public static PersistentHashMap sessionNameFidFileMap  = new PersistentHashMap(SID, SESSION_NAME);

    public static final ConcurrentHashMap<String,byte[]> sessionKeyMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String,TalkIdInfo> talkIdInfoMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String,ContactDetail> contactMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String,UI.RoomInfo> roomInfoMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String,String> fidCidMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String,String> fidAvatarMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String,String> sessionNameFidMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String,String> shortcutIdMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String,String> idShortcutMap = new ConcurrentHashMap<>();


    public static final ConcurrentLinkedQueue<TalkUnit> sendingQueue = new ConcurrentLinkedQueue<>();
    public static final ConcurrentLinkedQueue<String> displayMessageQueue = new ConcurrentLinkedQueue<>();
    public static final ConcurrentLinkedQueue<TalkUnit> receivedQueue = new ConcurrentLinkedQueue<>();
    public static final ConcurrentHashMap<Integer, TalkUnit> pendingRequestMap = new ConcurrentHashMap<>();

    public static final ConcurrentHashMap<String,Long> lastTimeMap = new ConcurrentHashMap<>();

    public static String sid;
    public static String myFid;
    public static String myPriKeyCipher;
    public static String myPubKey;
    public static String dealerPubKey;
    public static String dealer;
    public static String lastTalkId;

    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    private Socket socket;
    private String ip;
    private Integer port;


    public TalkTcpClient(ApiProvider apiProvider, ApiAccount apiAccount, byte[] symKey, ApipClient apipClient) {
        super(apiProvider, apiAccount, symKey, apipClient);
        sid = apiProvider.getId();
        myFid = apiAccount.getUserId();
        myPriKeyCipher = apiAccount.getUserPriKeyCipher();
        myPubKey = apiAccount.getUserPubKey();
        dealerPubKey = apiProvider.getDealerPubKey();
    }
    public void start(Settings settings) {
        System.out.println("Starting client...");


        if (!connect()) return;

        lastTimeMap.putAll(loadLastTime(myFid,sid));

        try  {
            Send sendThread = new Send(this,settings);
            Get getThread = new Get(this, settings);
            UI uiThread = new UI(sendThread,getThread,this, settings, br);

            getThread.start();
            sendThread.start();
            uiThread.start();

            // Wait for threads to finish
            uiThread.join();
            getThread.join();
            sendThread.join();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            close(settings);
        }
    }

    private void close(Settings settings) {
        settings.saveServerSettings(settings.getApiAccountId(ServiceType.TALK));
        JsonTools.saveToJsonFile(lastTimeMap, null, sid,LAST_TIME,false);
        sessionKeyFileMap.close();
        talkIdInfoFileMap.close() ;
        contactFileMap.close() ;
        roomInfoFileMap.close();
        fidCidFileMap.close();
        fidAvatarFileMap.close();
        sessionNameFidFileMap.close();
        try {
            br.close();
            socket.close();
        } catch (IOException ignore) {
        }
    }

    private boolean connect() {
        try {
            URL url = new URL(apiProvider.getApiUrl());
            ip = url.getHost();
            port = url.getPort();
            socket = new Socket(ip, port);
        } catch (IOException e) {
            System.out.println("Failed to create Talk socket.");
            return false;
        }
        return true;
    }
    @NotNull
    public static TalkUnit makeSignInRequest(String mainFid,String sid,String dealerId) {

        TalkUnit talkUnitRequest = new TalkUnit(TalkUnit.IdType.FID, dealerId, null, TalkUnit.DataType.REQUEST);

        RequestBody requestBody = new RequestBody();

        requestBody.setSid(sid);
        requestBody.setOp(Op.SIGN_IN);

        talkUnitRequest.setData(requestBody);
        talkUnitRequest.setDataType(TalkUnit.DataType.REQUEST);
        return talkUnitRequest;
    }

    public void askKey(String fid,Map<String,String> askMap,String pubKey) {
        RequestBody requestBody = new RequestBody();
        requestBody.setOp(Op.ASK_KEY);
        if(askMap!=null){
            requestBody.setData(askMap);
        }
        TalkUnit talkUnit = null;
        if(pubKey!=null){
            byte[] priKey = decryptPriKey(myPriKeyCipher,symKey);
            CryptoDataByte cryptoDataByte = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7).encryptByAsyTwoWay(requestBody.toJson().getBytes(), priKey, Hex.fromHex(pubKey));
            if(cryptoDataByte==null || cryptoDataByte.getCode()!=0){
                System.out.println("Failed to encrypt data.");
                return;
            }
            talkUnit = TalkTcpClient.makeBytesTalkUnit(cryptoDataByte.toBundle(), TalkUnit.DataType.ENCRYPTED_REQUEST, myFid, TalkIdInfo.fidTalkIdInfo(fid), null, null,null, null);
        }else{
            talkUnit = TalkTcpClient.request(fid, sid, TalkUnit.IdType.FID, fid, null, Op.ASK_KEY, requestBody, null, null, null);
        }
        TalkTcpClient.addToSendingQueue(talkUnit);
        System.out.println("Asked key from "+fid);
    }

    public static TalkUnit requestServer(String fid, String sid, Op op, Object data, String dealer){
        return request(fid,sid, TalkUnit.IdType.FID,dealer,null,op,data,null,null,null);
    }

    public static TalkUnit requestWithPriKey(String fid, String sid, TalkUnit.IdType toType, String to, Op op, Object data, byte[] priKey, byte[] pubKey){
        return request(fid, sid, toType, to, null,op, data, null, priKey, pubKey);
    }

    public static TalkUnit requestWithSymKey(String fid, String sid, String to, Op op,Object data, byte[] sessionKey, TalkUnit.IdType toType){
        return request(fid, sid, toType, to, null,op, data, sessionKey, null, null);
    }

    public static TalkUnit request(String fid, String sid, TalkUnit.IdType toType, String to, List<String> toList, Op op, Object data, byte[] sessionKey, byte[] priKey, byte[] pubKey) {
        TalkUnit talkUnitRequest = new TalkUnit(toType, to, toList, TalkUnit.DataType.REQUEST);
        RequestBody requestBody = new RequestBody();

        requestBody.setSid(sid);
        requestBody.setOp(op);

        if(sessionKey==null && pubKey==null){
            requestBody.setData(data);
            talkUnitRequest.setData(requestBody);
            talkUnitRequest.setDataType(TalkUnit.DataType.REQUEST);
        } else {
            CryptoDataByte cryptoDataByte;
            if (sessionKey != null) {
                Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
                cryptoDataByte = encryptor.encryptBySymKey(requestBody.toJson().getBytes(), sessionKey);
            } else {
                Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
                cryptoDataByte = encryptor.encryptByAsyTwoWay(requestBody.toJson().getBytes(), priKey, pubKey);
            }

            if (cryptoDataByte == null || cryptoDataByte.getCode() != 0) {
                System.out.println("Failed to encrypt data.");
                return null;
            }
            talkUnitRequest.setData(cryptoDataByte.toBundle());
            talkUnitRequest.setDataType(TalkUnit.DataType.ENCRYPTED_REQUEST);
        }

        return talkUnitRequest;
    }

    public List<TalkIdInfo> searchTalkIdInfos(String searchString) {
        List<TalkIdInfo> results = new ArrayList<>();
        byte[] searchBytes = searchString.getBytes();
    
        for (byte[] value : talkIdInfoFileMap.values()) {
            if (BytesTools.contains(value, searchBytes)) {
                TalkIdInfo talkIdInfo = TalkIdInfo.fromBytes(value);
                if (talkIdInfo != null) {
                    results.add(talkIdInfo);
                }
            }
        }
        return results;
    }

    public TalkUnit readTalkUnit(DataInputStream dis){
        byte[] receivedBytes;

        TalkUnit talkUnit;
        try {
            receivedBytes = TcpTools.readBytes(dis);
            if(receivedBytes==null)return null;

            CryptoDataByte cryptoDataByte = CryptoDataByte.fromBundle(receivedBytes);
            if(cryptoDataByte!=null) {
                if (cryptoDataByte.getType().equals(EncryptType.SymKey))
                    cryptoDataByte.setSymKey(sessionKey);
                else if (cryptoDataByte.getType().equals(EncryptType.AsyTwoWay))
                    cryptoDataByte.setPriKeyB(decryptPriKey(apiAccount.getUserPriKeyCipher(), symKey));

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
    public static void requestRoomInfo(String[] roomIds, String fid,byte[] sessionKey) {
        byte[] bytes;
        RequestBody requestBody = new RequestBody();
        requestBody.setOp(Op.ASK_ROOM_INFO);
        requestBody.setData(roomIds);
        bytes = requestBody.toJson().getBytes();
        TalkTcpClient.makeBytesTalkUnit(bytes, TalkUnit.DataType.REQUEST,myFid,TalkIdInfo.fidTalkIdInfo(fid), sessionKey,null,null, null);
    }

    @Nullable
    public static TalkUnit makeBytesTalkUnit(byte[] bytes, TalkUnit.DataType dataType, String myFid, TalkIdInfo talkIdInfo, byte[] sessionKey, byte[]priKey, byte[] pubKey, List<String> toList) {
        Encryptor encryptor;
        CryptoDataByte cryptoDataByte;
        String talkId = talkIdInfo.getId();
        byte[] finalBytes;
        switch (dataType) {
            case TEXT, BYTES, REQUEST, REPLY,
                    SIGNED_TEXT, SIGNED_BYTES, SIGNED_REQUEST, SIGNED_HAT, SIGNED_REPLY -> {
                finalBytes = bytes;
            }
            case ENCRYPTED_REQUEST, ENCRYPTED_BYTES, ENCRYPTED_TEXT, ENCRYPTED_HAT,
                    ENCRYPTED_SIGNED_BYTES, ENCRYPTED_REPLY, ENCRYPTED_SIGNED_HAT,
                    ENCRYPTED_SIGNED_REPLY, ENCRYPTED_SIGNED_REQUEST, ENCRYPTED_SIGNED_TEXT ->
            {
                if (sessionKey != null) {
                    encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
                    cryptoDataByte = encryptor.encryptBySymKey(bytes, sessionKey);
                }else if (priKey != null && pubKey != null) {
                    encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
                    cryptoDataByte = encryptor.encryptByAsyTwoWay(bytes, priKey, pubKey);
                } else return null;
                if(cryptoDataByte.getCode()!=0){
                    System.out.println("Failed to encrypt text for "+ talkId);
                    return null;
                }
                finalBytes = cryptoDataByte.toBundle();
            }
            default -> {
                return null;
            }
        }
    
        TalkUnit talkUnit = new TalkUnit(talkIdInfo.getIdType(), talkId,toList, dataType);
        talkUnit.setData(finalBytes);
        return talkUnit;
    }
    public static byte[] decryptSessionKey(byte[] cipherBytes, byte[] symKey) {
        Decryptor decryptor = new Decryptor();
        CryptoDataByte cryptoDataByte = decryptor.decrypt(cipherBytes,symKey);
        if(cryptoDataByte.getCode()!=0)return null;
        return cryptoDataByte.getData();
    }

    public static TalkIdInfo searchCidOrFid(ApipClient apipClient, BufferedReader br) {
        CidInfo cidInfo = apipClient.searchCidOrFid(br);
        if(cidInfo==null)return null;
        return TalkIdInfo.fromCidInfo(cidInfo);
    }
    @Nullable
    public static String getPubKey(String fid, ApipClient apipClient) {
        String pubKey;
        pubKey = contactMap.get(fid).getPubKey();
        if (pubKey == null) {
            byte[] contactBytes = contactFileMap.get(KeyTools.addrToHash160(pubKey));
            ContactDetail contactDetail = ContactDetail.fromBytes(contactBytes);
            pubKey = contactDetail.getPubKey();
        }
        if (pubKey == null) pubKey = apipClient.getPubKey(fid, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        return pubKey;
    }

    public static void addToSendingQueue(TalkUnit talkUnit) {
        synchronized (sendingQueue) {
            sendingQueue.add(talkUnit);
            sendingQueue.notify();
        }
    }

    
    // Helper method to update both maps
    private synchronized void updateTalkIdInfo(TalkIdInfo talkIdInfo) {
        synchronized (talkIdInfoMap) {
            talkIdInfoMap.put(talkIdInfo.getId(), talkIdInfo);
        }
        synchronized (talkIdInfoFileMap) {
            talkIdInfoFileMap.put(KeyTools.addrToHash160(talkIdInfo.getId()), talkIdInfo.toBytes());
        }
    }

    public static void updateSessionKeyMaps(String id, byte[] sessionKey) {
        byte[] idBytes = null;
        if(KeyTools.isValidFchAddr(id))idBytes=KeyTools.addrToHash160(id);
        else if(Hex.isHex32(id))idBytes=Hex.fromHex(id);
        else return;
        
        sessionKeyFileMap.put(idBytes, sessionKey);

        synchronized (sessionKeyMap) {
            sessionKeyMap.put(id, sessionKey);
        }

        String sessionName = IdNameTools.makeKeyName(sessionKey);
        synchronized (sessionNameFidMap) {
            sessionNameFidMap.put(sessionName, id);
        }
        sessionNameFidFileMap.put(Hex.fromHex(sessionName), idBytes);
    }

    public static class TalkIdInfo{
        private String id;
        private TalkUnit.IdType type;
        private String stdName;
        private String showName;
        private String owner;
        private String pubKey;

        @NotNull
        public static TalkIdInfo fidTalkIdInfo(String fid) {
            TalkIdInfo talkIdInfo = new TalkIdInfo();
            talkIdInfo.setId(fid);
            talkIdInfo.setIdType(TalkUnit.IdType.FID);
            return talkIdInfo;
        }

        public byte[] toBytes(){
            return JsonTools.toJson(this).getBytes();
        }

        public static TalkIdInfo fromBytes(byte[] bytes){
            return JsonTools.fromJson(new String(bytes), TalkIdInfo.class);
        }

        public static TalkIdInfo fromContact(ContactDetail contactDetail){
            TalkIdInfo talkIdInfo = new TalkIdInfo();
            talkIdInfo.setId(contactDetail.getFid());
            talkIdInfo.setShowName(contactDetail.getCid());
            talkIdInfo.setIdType(TalkUnit.IdType.FID);
            talkIdInfo.setPubKey(contactDetail.getPubKey());
            return talkIdInfo;
        }


        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
            if(this.stdName!=null && this.showName == null)makeName();
        }

        public void setShowName(String showName) {
            this.showName = showName;
        }


        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }

        public TalkUnit.IdType getIdType() {
            return type;
        }

        public void setIdType(TalkUnit.IdType toType) {
            this.type = toType;
        }

        public String getStdName() {
            return stdName;
        }

        public void setStdName(String stdName) {
            this.stdName = stdName;
            if(this.id !=null && this.showName == null)makeName();
        }

        public String getShowName() {
            return showName;
        }

        public void makeName() {
            this.showName = this.stdName+"("+ StringTools.omitMiddle(this.id,11)+")";
        }

        public TalkUnit.IdType getType() {
            return type;
        }

        public void setType(TalkUnit.IdType type) {
            this.type = type;
        }

        public String getPubKey() {
            return pubKey;
        }

        public void setPubKey(String pubKey) {
            this.pubKey = pubKey;
        }

        public static TalkTcpClient.TalkIdInfo roomToTalkIdInfo(RoomInfo roomInfo) {
            TalkTcpClient.TalkIdInfo talkIdInfo = new TalkTcpClient.TalkIdInfo();
            talkIdInfo.setId(roomInfo.getRoomId());
            talkIdInfo.setIdType(TalkUnit.IdType.FID_LIST);
            talkIdInfo.setStdName(roomInfo.getName());
        
            talkIdInfo.setOwner(roomInfo.getOwner());
            return talkIdInfo;
        }

        @NotNull
        public static TalkTcpClient.TalkIdInfo teamToTalkIdInfo(Team team) {
            TalkTcpClient.TalkIdInfo talkIdInfo = new TalkTcpClient.TalkIdInfo();
            talkIdInfo.setId(team.getTid());
            talkIdInfo.setIdType(TalkUnit.IdType.GROUP);
            talkIdInfo.setStdName(team.getStdName());
            talkIdInfo.setOwner(team.getOwner());
            return talkIdInfo;
        }

        @NotNull
        public static TalkTcpClient.TalkIdInfo groupToTalkIdInfo(Group group) {
            TalkTcpClient.TalkIdInfo talkIdInfo = new TalkTcpClient.TalkIdInfo();
            talkIdInfo.setId(group.getGid());
            talkIdInfo.setIdType(TalkUnit.IdType.GROUP);
            talkIdInfo.setStdName(group.getName());
            return talkIdInfo;
        }

        public static TalkIdInfo fromCidInfo(CidInfo cidInfo) {
            TalkIdInfo talkIdInfo = new TalkIdInfo();
            talkIdInfo.setId(cidInfo.getFid());
            talkIdInfo.setIdType(TalkUnit.IdType.FID);
            talkIdInfo.setShowName(cidInfo.getCid());
            talkIdInfo.setPubKey(cidInfo.getPubKey());
            return talkIdInfo;
        }
        
    }


    public static void shareSessionKey(String fid, byte[] sessionKey, ApipClient apipClient,byte[] symKey) {
        TalkUnit talkUnit;
        RequestBody requestBody = new RequestBody();
        requestBody.setOp(Op.SHARE_KEY);
        requestBody.setData(Hex.toHex(sessionKey));
        byte[] priKey = decryptPriKey(myPriKeyCipher,symKey);
        
        String pubKey;

        pubKey = TalkTcpClient.getPubKey(fid,apipClient);
        if(pubKey==null)return;
        byte[] toPubKey = Hex.fromHex(pubKey);
        TalkIdInfo talkIdInfo = TalkIdInfo.fidTalkIdInfo(fid);
        talkUnit = makeBytesTalkUnit(requestBody.toJson().getBytes(), TalkUnit.DataType.ENCRYPTED_REQUEST, myFid, talkIdInfo, null, priKey, toPubKey, null);
        TalkTcpClient.addToSendingQueue(talkUnit);
    }

    public List<String> getMembers(TalkIdInfo talkIdInfo){
        return switch (talkIdInfo.getIdType()){
            case GROUP -> getGroupMembers(talkIdInfo.getId(),apipClient);
            case TEAM -> getTeamMembers(talkIdInfo.getId(),apipClient);
            case FID_LIST -> getRoomMembers(talkIdInfo.getId());
            default -> null;
        };
    }

    public List<String> getRoomMembers(String id) {
        return Arrays.asList(roomInfoMap.get(id).getMembers());
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



    public byte[] newKey(String id) {
        byte[] sessionKey;
        sessionKey = BytesTools.getRandomBytes(32);
        TalkTcpClient.updateSessionKeyMaps(id, sessionKey);
        return sessionKey;
    }
    public  List<RoomInfo> searchRoomInfoList(String part) {
        List<RoomInfo> roomInfoList = new ArrayList<>();
        
        // Search in roomInfoMap
        for (String id : roomInfoMap.keySet()) {
            RoomInfo roomInfo = roomInfoMap.get(id);
            if (roomInfo.getRoomId().contains(part) || roomInfo.getName().contains(part)) {
                roomInfoList.add(roomInfo);
            }
        }
        
        // If not found, search in roomInfoFileMap
        if (roomInfoList.isEmpty()) {
            for (byte[] id : roomInfoFileMap.keySet()) {
                RoomInfo roomInfo = RoomInfo.fromBytes(roomInfoFileMap.get(id));
                if (roomInfo.getRoomId().contains(part)
                    || roomInfo.getName().contains(part)
                    || Arrays.asList(roomInfo.getMembers()).contains(part)
                    || roomInfo.getOwner().contains(part)) {
                    roomInfoList.add(roomInfo);
                }
            }
        }
        
        return roomInfoList;
    }

    public byte[] getSessionKey(TalkIdInfo talkIdInfo) {
        byte[] sessionKey = sessionKeyMap.get(talkIdInfo.getId());
        if (sessionKey != null) return sessionKey;

        byte[] cipherBytes = sessionKeyFileMap.get(KeyTools.addrToHash160(talkIdInfo.getId()));
        if (cipherBytes != null) {
                sessionKey = TalkTcpClient.decryptSessionKey(cipherBytes, symKey);
                if (sessionKey != null) {
                    sessionKeyMap.put(talkIdInfo.getId(), sessionKey);
                    return sessionKey;
                }
            }
        return null;
    }

    public void askSessionKey(String fid,String id,String pubKey,String myPriKeyCipher, byte[] symKey) {
        byte[] priKey = decryptPriKey(myPriKeyCipher, symKey);
        TalkUnit talkUnit = TalkTcpClient.requestWithPriKey(myFid,sid,TalkUnit.IdType.FID,fid,Op.ASK_KEY,id,priKey,Hex.fromHex(pubKey));
        synchronized (sendingQueue) {
            sendingQueue.add(talkUnit);
        }
    }
    public  void requestRoomInfo(String searchString) throws IOException {
        while (true) {
            CidInfo cidInfo = apipClient.searchCidOrFid(br);
            if(cidInfo==null)return;
            
            String fid = cidInfo.getFid();
            byte[] priKey = decryptPriKey(myPriKeyCipher, symKey);
            TalkUnit talkUnit = TalkTcpClient.requestWithPriKey(myFid, sid, TalkUnit.IdType.FID, fid, Op.ASK_ROOM_INFO, searchString, priKey, Hex.fromHex(cidInfo.getPubKey()));
            sendingQueue.add(talkUnit);
            System.out.println("Sent a request for the room info to " + fid);
            return;
        }
    }

    ////////////////////////////////////////////////////////////////

    public TalkIdInfo searchInfo(String input) throws IOException {
        TalkIdInfo talkIdInfo = new TalkIdInfo();
        if ("".equals(input)) {
            UI.showInstruction();
            return null;
        }
        String idType = StringTools.getWordAtPosition(input, 1);
        String part = StringTools.getWordAtPosition(input, 2);
        if(idType==null || part==null){
            System.out.println("""
                    To search, the type and the content are required.
                    The type could be 'cid', 'fid', 'group', 'team', 'room', 'hat' or 'did'.
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
            case FieldNames.ROOM -> {
                talkIdInfo = searchRoom(part);
                if (talkIdInfo == null) {
                    System.out.println("Room not found. Try again.");
                    return null;
                }
            }
            case FieldNames.HAT ,FieldNames.DID -> {
                TalkUnit talkUnit = requestServer(myFid, sid,Op.ASK_HAT,part, dealer);
                if(talkUnit!=null)sendingQueue.add(talkUnit);
            }

            default -> throw new IllegalStateException("Unexpected value: " + idType);
        }

        return talkIdInfo;
    }

    public TalkIdInfo searchFid(String part, BufferedReader br) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(FID).addNewValue(part);
        List<Address> result = apipClient.fidSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        Address fid = Inputer.chooseOneFromList(result, FID, "Choose the FID:", br);
        if (fid == null) return null;

        TalkIdInfo talkIdInfo = new TalkIdInfo();
        talkIdInfo.setId(fid.getFid());
        talkIdInfo.setIdType(TalkUnit.IdType.FID);

        CidInfo cidInfo = apipClient.cidInfoById(fid.getFid());
        if (cidInfo != null) {
            talkIdInfo.setShowName(cidInfo.getCid());
        }

        return talkIdInfo;
    }

    public TalkIdInfo searchCid(String part, BufferedReader br) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(FieldNames.USED_CIDS).addNewValue(part);
        List<CidInfo> result = apipClient.cidInfoSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        CidInfo cidInfo = Inputer.chooseOneFromList(result, FieldNames.CID, "Choose the CID:", br);
        if (cidInfo == null) return null;

        TalkIdInfo talkIdInfo = new TalkIdInfo();
        talkIdInfo.setId(cidInfo.getFid());
        talkIdInfo.setShowName(cidInfo.getCid());
        talkIdInfo.setIdType(TalkUnit.IdType.FID);

        return talkIdInfo;
    }

    public  TalkIdInfo searchGroup(String part) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(FieldNames.GID, FieldNames.NAME, FieldNames.DESC).addNewValue(part);
        List<Group> result = apipClient.groupSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        Group group = Inputer.chooseOneFromList(result, NAME, "Choose the group:", br);
        if (group == null) return null;
        return TalkIdInfo.groupToTalkIdInfo(group);
    }

    public TalkIdInfo searchTeam(String part) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(FieldNames.TID, FieldNames.STD_NAME, FieldNames.LOCAL_NAMES, DESC).addNewValue(part);
        List<Team> result = apipClient.teamSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        Team team = Inputer.chooseOneFromList(result, NAME, "Choose the team:", br);
        if (team == null) return null;
        return TalkIdInfo.teamToTalkIdInfo(team);
    }

    public TalkIdInfo searchRoom(String part) {
        List<RoomInfo> roomInfoList = searchRoomInfoList(part);
        
        if (roomInfoList.isEmpty()) {
            if (Inputer.askIfYes(br, "Room not found. Ask someone to share the room info?")) {
                try {
                    requestRoomInfo(part);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        RoomInfo roomInfo = Inputer.chooseOneFromList(roomInfoList, NAME, "Choose the room:", br);
        TalkIdInfo talkIdInfo = TalkIdInfo.roomToTalkIdInfo(roomInfo);
        
        if (Inputer.askIfYes(br, "Show the detail of " + talkIdInfo.getShowName() + "?")) {
            System.out.println(JsonTools.toNiceJson(roomInfo));
        }
        
        return talkIdInfo;
    }

    public TalkIdInfo searchTalkId(String part) {
        TalkIdInfo result = null;

        result = searchGroup(part, result);

        if(result==null)
            result = searchTeam(part, result);

        if(result==null)
            result = searchRoom(part);

        return result;
    }

    public TalkIdInfo searchGroup(String part, TalkIdInfo result) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(FieldNames.GID, NAME, DESC).addNewValue(part);

        List<Group> groupList = apipClient.groupSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if (!groupList.isEmpty()) {
            Group group;
            if (groupList.size() == 1) {
                group = groupList.get(0);
                result = TalkIdInfo.groupToTalkIdInfo(group);
            } else {
                group = Inputer.chooseOneFromList(groupList, NAME, "Choose the group.", br);
                if (group != null) {
                    result = TalkIdInfo.groupToTalkIdInfo(group);
                }
            }
        }
        return result;
    }

    public TalkIdInfo searchTeam(String part, TalkIdInfo result) {
        Fcdsl fcdsl;
        fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(FieldNames.TID, FieldNames.STD_NAME, FieldNames.LOCAL_NAMES, DESC).addNewValue(part);
        Team team;
        List<Team> teamList = apipClient.teamSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if (!teamList.isEmpty()) {
            if (teamList.size() == 1) {
                team = teamList.get(0);
                result = TalkIdInfo.teamToTalkIdInfo(team);
            } else {
                team = Inputer.chooseOneFromList(teamList, NAME, "Choose the team.", br);
                if (team != null) {
                    result = TalkIdInfo.teamToTalkIdInfo(team);
                }
            }
        }
        return result;
    }

    public TalkIdInfo getFidTalkInfo(String fid, BufferedReader br, ApipClient apipClient) {
        TalkIdInfo talkIdInfo = talkIdInfoMap.get(fid);
        if (talkIdInfo != null) 
        return talkIdInfo;

        ContactDetail contactDetail = contactMap.get(fid);
        if (contactDetail == null) {
            contactDetail = ContactDetail.fromBytes(contactFileMap.get(KeyTools.addrToHash160(fid)));
        }
        if (contactDetail != null) {
            talkIdInfo = TalkIdInfo.fromContact(contactDetail);
            updateTalkIdInfo(talkIdInfo);
            return talkIdInfo;
        }
        
        CidInfo cidInfo = apipClient.searchCidOrFid(br);
        if (cidInfo != null) {
            talkIdInfo = TalkIdInfo.fromCidInfo(cidInfo);
            updateTalkIdInfo(talkIdInfo);
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
        return Inputer.chooseOneFromList(talkIdInfoList, "showName", "Choose on:", br);
    }
    
    public synchronized List<TalkIdInfo> findCid(String searchString,boolean allResources) throws IOException {
        List<TalkIdInfo> results = new ArrayList<>();
        String lowerSearchString = searchString.toLowerCase();
    
        // 1. Find in talkIdInfoMap
        synchronized (talkIdInfoMap) {
            for (TalkIdInfo info : talkIdInfoMap.values()) {
                if (info.getShowName() != null && info.getShowName().toLowerCase().contains(lowerSearchString)) {
                    results.add(info);
                }
            }
        }
    
        // 2. Find in talkIdInfoFileMap
        if(results.isEmpty()|| allResources){
            synchronized (talkIdInfoFileMap) {
                List<TalkIdInfo> fileMapResults = searchTalkIdInfos(searchString);
                results.addAll(fileMapResults);
            }
        }
    
        // 3. Find in contactMap
        if(results.isEmpty()|| allResources){
            synchronized (contactMap) {
                for (ContactDetail contact : contactMap.values()) {
                    if (contact.getCid() != null && contact.getCid().toLowerCase().contains(lowerSearchString)) {
                        results.add(TalkIdInfo.fromContact(contact));
                    }
                }
            }
        }
    
        // 4. Find in contactFileMap
        if(results.isEmpty()|| allResources){
            synchronized (contactFileMap) {
                for (byte[] value : contactFileMap.values()) {
                    ContactDetail contact = ContactDetail.fromBytes(value);
                    if (contact != null && contact.getCid() != null && contact.getCid().toLowerCase().contains(lowerSearchString)) {
                        results.add(TalkIdInfo.fromContact(contact));
                    }
                }
            }
        }
    
        // 5. Find with apipClient.cidInfoSearch
        if(results.isEmpty()|| allResources){
            Fcdsl fcdsl = new Fcdsl();
            fcdsl.addNewQuery().addNewPart().addNewFields(FieldNames.USED_CIDS).addNewValue(searchString);
            List<CidInfo> apiResults = apipClient.cidInfoSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        
            for (CidInfo cidInfo : apiResults) {
                results.add(TalkIdInfo.fromCidInfo(cidInfo));
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

        // 1. Find in talkIdInfoMap
        synchronized (talkIdInfoMap) {
            for (TalkIdInfo info : talkIdInfoMap.values()) {
                if (info.getId() != null && info.getId().toLowerCase().contains(lowerSearchString)) {
                    results.add(info);
                }
            }
        }

        // 2. Find in talkIdInfoFileMap
        if(results.isEmpty()|| allResources){
            synchronized (talkIdInfoFileMap) {
                results.addAll(searchTalkIdInfos(searchString));
            }
        }

        // 3. Find in contactMap
        if(results.isEmpty()|| allResources){
            synchronized (contactMap) {
                for (Map.Entry<String, ContactDetail> entry : contactMap.entrySet()) {
                    if (entry.getKey().toLowerCase().contains(lowerSearchString)) {
                    results.add(TalkIdInfo.fromContact(entry.getValue()));
                }
                }
            }
        }

        // 4. Find in contactFileMap
        if(results.isEmpty()|| allResources){
            synchronized (contactFileMap) {
                for (Map.Entry<byte[], byte[]> entry : contactFileMap.entrySet()) {
                    String fid = KeyTools.hash160ToFchAddr(entry.getKey());
                if (fid.toLowerCase().contains(lowerSearchString)) {
                    ContactDetail contact = ContactDetail.fromBytes(entry.getValue());
                    results.add(TalkIdInfo.fromContact(contact));
                }
                }
            }
        }

        // 5. Find with apipClient.cidInfoSearch
        Fcdsl fcdsl = new Fcdsl();
        if(results.isEmpty()|| allResources){
            fcdsl.addNewQuery().addNewPart().addNewFields(FieldNames.FID).addNewValue(searchString);
            List<CidInfo> apiResults = apipClient.cidInfoSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);

            for (CidInfo cidInfo : apiResults) {
                results.add(TalkIdInfo.fromCidInfo(cidInfo));
            }
        }

        // Remove duplicates and return
        return results.stream().distinct().collect(Collectors.toList());
    }

    public synchronized List<TalkIdInfo> findGroup(String searchString,boolean allResources) throws IOException {
        List<TalkIdInfo> results = new ArrayList<>();
        String lowerSearchString = searchString.toLowerCase();

        // 1. Find in talkIdInfoMap
        
        synchronized (talkIdInfoMap) {
            for (TalkIdInfo info : talkIdInfoMap.values()) {
                if (info.getIdType() == TalkUnit.IdType.GROUP &&
                    (info.getId().toLowerCase().contains(lowerSearchString) ||
                     (info.getShowName() != null && info.getShowName().toLowerCase().contains(lowerSearchString)))) {
                    results.add(info);
            }
            }
        }

        // 2. Find in talkIdInfoFileMap
        if(results.isEmpty()|| allResources){
                synchronized (talkIdInfoFileMap) {
                results.addAll(searchTalkIdInfos(searchString).stream()
                    .filter(info -> info.getIdType() == TalkUnit.IdType.GROUP)
                    .collect(Collectors.toList()));
            }
        }

        // 3. Find with apipClient.groupSearch
        if(results.isEmpty()|| allResources){
            Fcdsl fcdsl = new Fcdsl();
                fcdsl.addNewQuery().addNewPart().addNewFields(FieldNames.GID, FieldNames.NAME, FieldNames.DESC).addNewValue(searchString);
            List<Group> apiResults = apipClient.groupSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);

        for (Group group : apiResults) {
                results.add(TalkIdInfo.groupToTalkIdInfo(group));
            }
        }

        // Remove duplicates and return
        return results.stream().distinct().collect(Collectors.toList());
    }

    public synchronized List<TalkIdInfo> findTeam(String searchString,boolean allResources) throws IOException {
        List<TalkIdInfo> results = new ArrayList<>();
        String lowerSearchString = searchString.toLowerCase();

        // 1. Find in talkIdInfoMap
        synchronized (talkIdInfoMap) {
            for (TalkIdInfo info : talkIdInfoMap.values()) {
                if (info.getIdType() == TalkUnit.IdType.TEAM &&
                    (info.getId().toLowerCase().contains(lowerSearchString) ||
                     (info.getShowName() != null && info.getShowName().toLowerCase().contains(lowerSearchString)))) {
                    results.add(info);
                }
            }
        }

        // 2. Find in talkIdInfoFileMap
        if(results.isEmpty()|| allResources){
            synchronized (talkIdInfoFileMap) {
                results.addAll(searchTalkIdInfos(searchString).stream()
                    .filter(info -> info.getIdType() == TalkUnit.IdType.TEAM)
                    .collect(Collectors.toList()));
            }
        }

        // 3. Find with apipClient.teamSearch
        if(results.isEmpty()|| allResources){
            Fcdsl fcdsl = new Fcdsl();
            fcdsl.addNewQuery().addNewPart().addNewFields(FieldNames.TID, FieldNames.STD_NAME, FieldNames.LOCAL_NAMES, FieldNames.DESC).addNewValue(searchString);
            List<Team> apiResults = apipClient.teamSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);

        for (Team team : apiResults) {
                results.add(TalkIdInfo.teamToTalkIdInfo(team));
            }
            }

        // Remove duplicates and return
        return results.stream().distinct().collect(Collectors.toList());
    }

    public synchronized List<TalkIdInfo> findRoom(String searchString,boolean allResources) throws IOException {
        List<TalkIdInfo> results = new ArrayList<>();
        String lowerSearchString = searchString.toLowerCase();

        // 1. Find in talkIdInfoMap
        synchronized (talkIdInfoMap) {
            for (TalkIdInfo info : talkIdInfoMap.values()) {
                if (info.getIdType() == TalkUnit.IdType.FID_LIST &&
                    (info.getId().toLowerCase().contains(lowerSearchString) ||
                     (info.getShowName() != null && info.getShowName().toLowerCase().contains(lowerSearchString)))) {
                    results.add(info);
                }
            }
        }

        // 2. Find in talkIdInfoFileMap
        if(results.isEmpty()|| allResources){
            synchronized (talkIdInfoFileMap) {
                results.addAll(searchTalkIdInfos(searchString).stream()
                    .filter(info -> info.getIdType() == TalkUnit.IdType.FID_LIST)
                    .collect(Collectors.toList()));
            }
        }

        // 3. Find in roomInfoMap
        if(results.isEmpty()|| allResources){
            synchronized (roomInfoMap) {
                for (RoomInfo roomInfo : roomInfoMap.values()) {
                    if (roomInfo.getRoomId().toLowerCase().contains(lowerSearchString) ||
                        roomInfo.getName().toLowerCase().contains(lowerSearchString)) {
                        results.add(TalkIdInfo.roomToTalkIdInfo(roomInfo));
                    }
                }
            }
        }

        // 4. Find in roomInfoFileMap
        if(results.isEmpty()|| allResources){
            synchronized (roomInfoFileMap) {
                for (byte[] value : roomInfoFileMap.values()) {
                    RoomInfo roomInfo = RoomInfo.fromBytes(value);
                if (roomInfo.getRoomId().toLowerCase().contains(lowerSearchString) ||
                    roomInfo.getName().toLowerCase().contains(lowerSearchString)) {
                        results.add(TalkIdInfo.roomToTalkIdInfo(roomInfo));
                    }
                }
            }
        }

        // Remove duplicates and return
        return results.stream().distinct().collect(Collectors.toList());
    }

    public TalkIdInfo getTalkIdInfoById(String talkId) {
        if(talkId==null)return null;
        TalkIdInfo talkIdInfo = null;

        // 1. Check talkIdInfoMap
        talkIdInfo = talkIdInfoMap.get(talkId);
        if (talkIdInfo != null) return talkIdInfo;

        // 2. Check talkIdInfoFileMap
        byte[] talkIdInfoBytes = talkIdInfoFileMap.get(KeyTools.addrToHash160(talkId));
        if (talkIdInfoBytes != null) {
            talkIdInfo = TalkIdInfo.fromBytes(talkIdInfoBytes);
            if (talkIdInfo != null) {
                updateTalkIdInfo(talkIdInfo);
                return talkIdInfo;
            }
        }

        // 3. Check contactMap
        ContactDetail contactDetail = contactMap.get(talkId);
        if (contactDetail != null) {
            talkIdInfo = TalkIdInfo.fromContact(contactDetail);
            updateTalkIdInfo(talkIdInfo);
            return talkIdInfo;
        }

        // 4. Check contactFileMap
        byte[] contactBytes = contactFileMap.get(KeyTools.addrToHash160(talkId));
        if (contactBytes != null) {
            contactDetail = ContactDetail.fromBytes(contactBytes);
            if (contactDetail != null) {
                talkIdInfo = TalkIdInfo.fromContact(contactDetail);
                updateTalkIdInfo(talkIdInfo);
                return talkIdInfo;
            }
        }

        // 5. Check roomInfoMap
        RoomInfo roomInfo = roomInfoMap.get(talkId);
        if (roomInfo != null) {
            talkIdInfo = TalkIdInfo.roomToTalkIdInfo(roomInfo);
            updateTalkIdInfo(talkIdInfo);
            return talkIdInfo;
        }

        // 6. Check roomInfoFileMap
        byte[] roomInfoBytes = roomInfoFileMap.get(KeyTools.addrToHash160(talkId));
        if (roomInfoBytes != null) {
            roomInfo = RoomInfo.fromBytes(roomInfoBytes);
            if (roomInfo != null) {
                talkIdInfo = TalkIdInfo.roomToTalkIdInfo(roomInfo);
                updateTalkIdInfo(talkIdInfo);
                return talkIdInfo;
            }
        }

        // 7. Check apipClient.cidInfoByIds
        Map<String, CidInfo> cidInfoMap = apipClient.cidInfoByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, talkId);
        if (cidInfoMap != null && !cidInfoMap.isEmpty()) {
            CidInfo cidInfo = cidInfoMap.get(talkId);
            if (cidInfo != null) {
                talkIdInfo = TalkIdInfo.fromCidInfo(cidInfo);
                updateTalkIdInfo(talkIdInfo);
                return talkIdInfo;
            }
        }

        // 8. Check apipClient.groupByIds
        Map<String, Group> groupMap = apipClient.groupByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, talkId);
        if (groupMap != null && !groupMap.isEmpty()) {
            Group group = groupMap.get(talkId);
            if (group != null) {
                talkIdInfo = TalkIdInfo.groupToTalkIdInfo(group);
                updateTalkIdInfo(talkIdInfo);
                return talkIdInfo;
            }
        }

        // 9. Check apipClient.teamByIds
        Map<String, Team> teamMap = apipClient.teamByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, talkId);
        if (teamMap != null && !teamMap.isEmpty()) {
            Team team = teamMap.get(talkId);
            if (team != null) {
                talkIdInfo = TalkIdInfo.teamToTalkIdInfo(team);
                updateTalkIdInfo(talkIdInfo);
                return talkIdInfo;
            }
        }

        // If not found in any of the sources
        return null;
    }


    ////////////////////////////////////////////////////////////////
    public Socket getSocket() {
        return socket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public Map<Integer, TalkUnit> getPendingRequestMap() {
        return pendingRequestMap;
    }

    public Queue<TalkUnit> getSendingQueue() {
        return sendingQueue;
    }

    public ConcurrentHashMap<String, byte[]> getSessionKeyMap() {
        return sessionKeyMap;
    }
}
