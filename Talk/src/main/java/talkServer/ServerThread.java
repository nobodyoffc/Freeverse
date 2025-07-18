//package talkServer;
//
//import apip.apipData.RequestBody;
//import config.Settings;
//import clients.ApipClient;
//import co.elastic.clients.elasticsearch.ElasticsearchClient;
//import feip.feipData.Service.ServiceType;
//import constants.IndicesNames;
//import crypto.*;
//import fcData.TalkUnit;
//import tools.RedisTools;
//import com.google.gson.JsonSyntaxException;
//import constants.ReplyCodeMessage;
//import fcData.AlgorithmId;
//import fcData.Signature;
//import fcData.FcReplier;
//import fcData.FcSession;
//import fch.ParseTools;
//import feip.feipData.Service;
//import feip.feipData.serviceParams.TalkParams;
//import tools.Hex;
//import tools.JsonTools;
//import tools.TcpTools;
//import tools.http.AuthType;
//import tools.http.RequestMethod;
//import org.jetbrains.annotations.Nullable;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import redis.clients.jedis.Jedis;
//import redis.clients.jedis.JedisPool;
//
//import java.io.*;
//import java.net.Socket;
//import java.util.*;
//
//import static constants.ApiNames.SignInEcc;
//import static constants.Strings.*;
//import static server.RequestChecker.*;
//import static config.Settings.addSidBriefToName;
//
//class ServerThread extends Thread {
//    private static final Logger log = LoggerFactory.getLogger(TalkServer.class);
//    private final Socket socket;
//    private final ApipClient apipClient;
//    private final ElasticsearchClient esClient;
//    private final JedisPool jedisPool;
//    private String userFid;
//    private byte[] userPubKey;
//    private FcSession session;
//    private final byte[] dealerPriKey;
//    private long startTime;
//    private String via;
//    private long viaConsume;
//    private  Map<String,Long> nPriceMap;
//    private final Service service;
//    private final TalkParams talkParams;
//    private final String dealer;
//    private final Settings settings;
//    private long price;
//    private final Map<String, Set<Socket>> fidSocketsMap;
//    private final  Map<String, byte[]> fidSessionKeyMap;
//
//    public ServerThread(Socket socket, Map<String, Set<Socket>> fidSocketsMap, byte[] dealerPriKey, long price, Map<String,Long> nPriceMap, Service service, Settings settings, Map<String, byte[]> fidSessionKeyMap) {
//        this.socket = socket;
//        this.apipClient = (ApipClient) settings.getClient(ServiceType.APIP);
//        this.esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);
//        this.jedisPool = (JedisPool) settings.getClient(ServiceType.REDIS);
//        this.dealerPriKey = dealerPriKey;
//        this.service = service;
//        this.talkParams = (TalkParams)service.getParams();
//        this.price = price;
//        this.settings = settings;
//        this.nPriceMap = nPriceMap;
//        this.fidSocketsMap = fidSocketsMap;
//        this.fidSessionKeyMap = fidSessionKeyMap;
//        this.dealer = talkParams.getDealer();
//    }
//
//    public void run() {
//        startTime = System.currentTimeMillis();
//        TalkUnit talkUnit;
//        try(DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
//            DataInputStream dis = new DataInputStream(socket.getInputStream())) {
//
//            sendServiceInfo(dos);
//
//            while (true) {
//
//                talkUnit = readTalkUnit(dos, dis);
//                if (talkUnit == null) continue;
//
//                JsonTools.printJson(talkUnit);
//
//                //Check sessionKey and signIn
//                if (session==null) {
//                    handleSignIn(talkUnit, dealerPriKey, dos, jedisPool);
//                    continue;
//                }
//
//
//                if(talkUnit.getTo()!=null && talkUnit.getTo().equals(dealer) && talkUnit.getDataType().equals(TalkUnit.DataType.REQUEST)) {
//                    dealRequest(talkUnit, dos);
//                    continue;
//                }
//
//                List<String> fidList = makeTalkIdList(talkUnit);
//
//                if(fidList==null || fidList.isEmpty())continue;
//
//                byte[] bundle = talkUnit.toBytes();
//
//                if(!updateAllBalance(talkUnit.getFrom(),fidList,bundle.length,dos,jedisPool))continue;
//
//                sendToAll(bundle,fidList,fidSocketsMap);
//
//                TalkUnit finalTalkUnit = talkUnit;
//
//                esClient.index(i->i.index(Settings.addSidBriefToName(sid,IndicesNames.DATA)).id(finalTalkUnit.makeId()).document(finalTalkUnit));
//
//            }
//        } catch (IOException e) {
//            System.out.println("Lost user "+userFid+".");
//        } finally {
//            try {
////                updateUserBalance();
//                if(via!=null)updateViaConsume();
//                socket.close();
//                synchronized (TalkServer.fidSocketsMap) {
//                    if(userFid!=null && TalkServer.fidSocketsMap.get(userFid)!=null)
//                        TalkServer.fidSocketsMap.get(userFid).remove(socket);
//                }
//                System.out.println(userFid + " disconnected. Active user number: " + TalkServer.fidSocketsMap.size());
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//    }
//
//    private boolean updateAllBalance(String from, List<String> fidList, int length, DataOutputStream dos, JedisPool jedisPool) {
//        long cost = length/1000 * price;
//
//        String key = Settings.addSidBriefToName(sid,BALANCE);
//
//        try(Jedis jedis = jedisPool.getResource()){
//            if(!updateFidBalance(-(cost*fidList.size()+1),key,jedis,from))
//                return false;
//
//            for(String fid:fidList){
//                updateFidBalance(cost, key, jedis, fid);
//            }
//
//        }catch (Exception e){
//            log.error(key, e);
//        }
//        return true;
//    }
//
//    private static boolean updateFidBalance(long addValue, String key, Jedis jedis, String fid) {
//        String balanceStr = jedis.hget(key, fid);
//        long balance;
//        if(balanceStr!=null) {
//            balance = Long.parseLong(balanceStr);
//            balance += addValue;
//            if(balance<0){
//                return false;
//            }
//            jedis.hset(key, fid,String.valueOf(balance));
//        }else jedis.hset(key, fid,String.valueOf(addValue));
//        return true;
//    }
//
//    private void sendToAll(byte[] bytes, List<String> fidList, Map<String, Set<Socket>> fidSocketsMap) {
//        for (String fid : fidList) {
//            byte[] sessionKey = fidSessionKeyMap.get(fid);
//            if(sessionKey==null) {
//                sessionKey = readSessionKeyFromJedis(fid);
//                if (sessionKey == null) continue;
//
//                Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
//                CryptoDataByte cryptoDataByte = encryptor.encryptBySymKey(bytes,sessionKey);
//                if(cryptoDataByte.getCode()!=0)continue;
//                bytes = cryptoDataByte.toBundle();
//
//            }
//
//            for (Socket socket : fidSocketsMap.get(fid)) {
//                DataOutputStream dos;
//                try {
//                    dos = new DataOutputStream(socket.getOutputStream());
//                    TcpTools.writeBytes(dos, bytes);
//                } catch (IOException e) {
//                    throw new RuntimeException(e);
//                }
//            }
//        }
//    }
//
//    @Nullable
//    private byte[] readSessionKeyFromJedis(String fid) {
//        byte[] sessionKey;
//        try (Jedis jedis = jedisPool.getResource()) {
//            jedis.select(0);
//            String sessionName = jedis.hget(Settings.addSidBriefToName(sid, FID_SESSION_NAME), fid);
//            if(sessionName==null) return null;
//            jedis.select(1);
//            String sessionKeyHex = jedis.hget(Settings.addSidBriefToName(sid, sessionName),sessionName);
//            sessionKey = Hex.fromHex(sessionKeyHex);
//        }
//        return sessionKey;
//    }
//
//    @Nullable
//    private List<String> makeTalkIdList(TalkUnit talkUnit) {
//        List<String> fidList = new ArrayList<>();
//        switch (talkUnit.getIdType()) {
//            case FID -> fidList.add(talkUnit.getTo());
//            case GROUP -> {
//                List<String> groupMemberList = getGroupMemberList(talkUnit.getTo());
//                if(groupMemberList==null) return null;
//                fidList.addAll(groupMemberList);
//            }
//            case TEAM -> {
//                List<String> teamMemberList = getTeamMemberList(talkUnit.getTo());
//                if(teamMemberList==null) return null;
//                fidList.addAll(teamMemberList);
//            }
//            case FID_LIST -> {
//                fidList.addAll(talkUnit.getToList());
//            }
//            case GROUP_LIST -> {
//                List<String> groupListMemberList = getGroupListMemberList(talkUnit.getToList());
//                if(groupListMemberList==null) return null;
//                fidList.addAll(groupListMemberList);
//            }
//            case TEAM_LIST -> {
//                List<String> teamListMemberList = getTeamListMemberList(talkUnit.getToList());
//                if(teamListMemberList==null) return null;
//                fidList.addAll(teamListMemberList);
//            }
//            default -> fidList =null;
//        }
//        return fidList;
//    }
//
//
//    private List<String> getGroupMemberList(String gid) {
//        Map<String, String[]> result = apipClient.groupMembers(RequestMethod.POST, AuthType.FC_SIGN_BODY, gid);
//        if(result==null || result.isEmpty() || result.get(gid)==null)return null;
//        return Arrays.stream(result.get(gid)).toList();
//    }
//
//    private List<String> getGroupListMemberList(List<String> gids) {
//        Set<String> set = new LinkedHashSet<>();
//        Map<String, String[]> result = apipClient.groupMembers(RequestMethod.POST, AuthType.FC_SIGN_BODY, gids.toArray(new String[0]));
//        if(result==null || result.isEmpty() )return null;
//
//        for (Map.Entry<String, String[]> entry : result.entrySet()) {
//            set.addAll(Arrays.asList(entry.getValue()));
//        }
//
//        return set.stream().toList();
//    }
//
//    private List<String> getTeamMemberList(String tid) {
//        Map<String, String[]> result = apipClient.teamMembers(RequestMethod.POST, AuthType.FC_SIGN_BODY, tid);
//        if(result==null || result.isEmpty() || result.get(tid)==null)return null;
//        return Arrays.stream(result.get(tid)).toList();
//    }
//
//    private List<String> getTeamListMemberList(List<String> tids) {
//        Set<String> set = new LinkedHashSet<>();
//        Map<String, String[]> result = apipClient.teamMembers(RequestMethod.POST, AuthType.FC_SIGN_BODY, tids.toArray(new String[0]));
//        if(result==null || result.isEmpty() )return null;
//
//        for (Map.Entry<String, String[]> entry : result.entrySet()) {
//            set.addAll(Arrays.asList(entry.getValue()));
//        }
//
//        return set.stream().toList();
//    }
//
//    @Nullable
//    private TalkUnit readTalkUnit(DataOutputStream dos, DataInputStream dis) throws IOException {
//        CryptoDataByte cryptoDataByte;
//        TalkUnit talkUnit;
//        byte[] cipherBytes = TcpTools.readBytes(dis);
//
//        try {
//            cryptoDataByte = readCipher(cipherBytes, dos);
//            if(cryptoDataByte==null) return null;
//
//            talkUnit = decryptTalkUnit(cryptoDataByte, dos);
//            if(talkUnit==null) return null;
//
//            if(cryptoDataByte.getPubKeyA()!=null)userPubKey=cryptoDataByte.getPubKeyA();
//
//        } catch (JsonSyntaxException e) {
//            replyOtherError( "Illegal message. Send encrypted talkUnit please.",null, null, dos);
//            return null;
//        }
//        return talkUnit;
//    }
//
//    private void addSocket() {
//        try {
//            synchronized (fidSocketsMap) {
//                Set<Socket> socketSet = fidSocketsMap.get(userFid);
//                if (socketSet == null) socketSet = new HashSet<>();
//                socketSet.add(socket);
//                fidSocketsMap.put(userFid, socketSet);
//            }
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//    }
//
//    private TalkUnit decryptTalkUnit(CryptoDataByte cryptoDataByte, DataOutputStream dos) {
//        EncryptType type = cryptoDataByte.getType();
//        TalkUnit talkUnit;
//        FcSession session=null;
//        switch (type){
//            case AsyTwoWay -> {
//                cryptoDataByte.setPriKeyB(dealerPriKey);
//                byte[] pubKeyA = cryptoDataByte.getPubKeyA();
//                if(pubKeyA!=null) {
//                    userFid = KeyTools.pubKeyToFchAddr(pubKeyA);
//                    userPubKey = pubKeyA;
//                }
//            }
//            case SymKey -> {
//                byte[] keyName = cryptoDataByte.getKeyName();
//                if(keyName==null){
//                    replyError(ReplyCodeMessage.Code1002SessionNameMissed, null, null, dos);
//                    return null;
//                }
//                if(this.session==null) {
//                    session = getSession(keyName, dos, jedisPool);
//                }
//                else {
//                    if(!Hex.toHex(keyName).equals(this.session.getName())){
//                        replyOtherError("The requester is not the connector.",null,null,dos);
//                        return null;
//                    }
//                    session = this.session;
//                }
//                if (session == null) {
//                    replyError(ReplyCodeMessage.Code1023MissSessionKey,null,null,dos);
//                    return null;
//                }
//                userFid = session.getFid();
//                userPubKey = Hex.fromHex(session.getPubKey());
//                cryptoDataByte.setSymKey(session.getKeyBytes());
//            }
//            default -> {
//                replyOtherError("Only the ciphers encrypted in SymKey or AsyTwoWay are accepted. Your encryptedType:"+type+".", null, null, dos);
//                return null;
//            }
//        }
//
//        new Decryptor().decrypt(cryptoDataByte);
//        if(cryptoDataByte.getCode()!=0){
//            replyError(ReplyCodeMessage.Code1029FailedToDecrypt,cryptoDataByte.getMessage(),null,dos);
//            return null;
//        }
//        talkUnit = TalkUnit.fromBytes(cryptoDataByte.getData());
//        if(talkUnit==null){
//            replyError(ReplyCodeMessage.Code1030FailedToParseData,null,null,dos);
//            return null;
//        }
//        talkUnit.setFrom(userFid);
//        return talkUnit;
//    }
//
//    private CryptoDataByte readCipher(byte[] cipherBytes, DataOutputStream dos) {
//        CryptoDataByte cryptoDataByte = CryptoDataByte.fromBundle(cipherBytes);
//        if(cryptoDataByte==null){
//            replyError(ReplyCodeMessage.Code1030FailedToParseData, null, null, dos);
//            return null;
//        }
//        return cryptoDataByte;
//    }
//
//    @Nullable
//    private FcSession getSession(byte[] keyName, DataOutputStream dos, JedisPool jedisPool) {
//        FcSession session;
//        try(Jedis jedis = this.jedisPool.getResource()){
//            session = FcSession.getSessionFromJedisWithKeyName(Hex.toHex(keyName),sid,jedis);
//            if(session==null){
//                replyError(ReplyCodeMessage.Code1023MissSessionKey, null, null, dos);
//                return null;
//            }
//        }
//        return session;
//    }
//
////    private void updateUserBalance() {
////        try(Jedis jedis = jedisPool.getResource()){
////            if(balance>0)
////                jedis.hset(Settings.addSidBriefToName(sid, BALANCE),userFid,String.valueOf(balance));
////            else jedis.hdel(Settings.addSidBriefToName(sid, BALANCE),userFid);
////        }
////    }
//
//    private void updateViaConsume() {
//        try(Jedis jedis = jedisPool.getResource()){
//            jedis.hset(Settings.addSidBriefToName(sid, CONSUME_VIA),via,String.valueOf(viaConsume));
//        }
//    }
//
//    private boolean reply0Success(Object data, DataOutputStream dos, byte[] dealerPriKey, byte[] sessionKey, Integer nonce) {
//        return reply(ReplyCodeMessage.Code0Success,null,data, nonce, dos,dealerPriKey,sessionKey);
//    }
//
//    private boolean replyOtherError(String message, Object data, Integer nonce, DataOutputStream dos) {
//        return reply(ReplyCodeMessage.Code1020OtherError,message,data, nonce, dos,null,null);
//    }
//    @SuppressWarnings("unused")
//    private boolean  replyOtherError(DataOutputStream dos, String message, Object data, Integer nonce, byte[] dealerPriKey, byte[] sessionKey) throws IOException {
//        return reply(ReplyCodeMessage.Code1020OtherError,message,data, nonce, dos,dealerPriKey,sessionKey);
//    }
//    private boolean replyError(Integer code, Object data, Integer nonce, DataOutputStream dos){
//        return reply(code,null,data, nonce, dos,null,null);
//    }
//
//    //TODO reply balance
//    private boolean reply(Integer code, String message, Object data, Integer nonce, DataOutputStream dos, byte[] dealerPriKey, byte[] sessionKey) {
//        TalkUnit talkUnitReply;
//        byte[] bytes;
//        String balanceKey = Settings.addSidBriefToName(sid,BALANCE);
//        FcReplier replier = new FcReplier();
//        replier.setCode(code);
//        replier.setMessage(ReplyCodeMessage.getMsg(code));
//        replier.setNonce(nonce);
//        try(Jedis jedis = jedisPool.getResource()) {
//            replier.setBalance(RedisTools.readHashLong(jedis,balanceKey,userFid));
//
//            if (message != null) replier.setMessage(message);
//            if (data != null) replier.setData(data);
//
//            talkUnitReply = new TalkUnit(TalkUnit.IdType.FID, userFid, null, TalkUnit.DataType.REPLY);
//            talkUnitReply.setData(replier);
//            byte[] bundle = talkUnitReply.toBytes();
//
//            if (dealerPriKey == null && sessionKey == null) {
//                TcpTools.writeBytes(dos, bundle);
//                return true;
//            }
//
//
//            Encryptor encryptor;
//            CryptoDataByte cryptoDataByte;
//
//            if (sessionKey != null) {
//                encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
//                cryptoDataByte = encryptor.encryptBySymKey(bundle, sessionKey);
//            } else {
//                encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
//                cryptoDataByte = encryptor.encryptByAsyTwoWay(bundle, dealerPriKey, userPubKey);
//            }
//
//            if (cryptoDataByte == null || cryptoDataByte.getCode() != 0) {
//                replyOtherError("Failed to encrypt.", null, nonce, dos);
//                return false;
//            }
//            bytes = cryptoDataByte.toBundle();
//
//            if (!updateSenderBalance(dos, SignInEcc, bytes.length,jedis)) return false;
//        }
//        TcpTools.writeBytes(dos, bytes);
//        return true;
//    }
//
//    private boolean updateSenderBalance(DataOutputStream dos, String apiName, int length, Jedis jedis)  {
//
//        long cost;
//        if(apiName!=null)
//            cost = length * nPriceMap.get(apiName) * price;
//        else cost  = length *  price;
//
//        if(!updateFidBalance(-cost, Settings.addSidBriefToName(sid, BALANCE), jedis,userFid)) {
//            replyOtherError("No balance or balance was run out. Top up please.", null, null, dos);
//            return false;
//        }
//        else if(via!=null)viaConsume+=cost;
//        return true;
////
////        balance -= cost;
////        if(balance<0){
////            if(via!=null)viaConsume+=(cost+balance);
////            replyOtherError("No balance or balance was run out. Top up please.",null, null, dos);
////        }
////        if(via!=null)viaConsume+=cost;
////        return balance;
//    }
//
//    @Nullable
//    private void dealRequest(TalkUnit talkUnit, DataOutputStream dos) {
//        RequestBody requestBody;
//        try (Jedis jedis = jedisPool.getResource()) {
//            requestBody = (RequestBody)talkUnit.getData();
//            checkRequest(talkUnit,requestBody,dos, jedis);
//
//            switch (requestBody.getOp()){
//                case PING -> {}
//                case SIGN_IN -> handleSignIn(talkUnit, dealerPriKey, dos, jedisPool);
//
//                case CREAT_ROOM -> {}
//                case ASK_ROOM_INFO -> {}
//                case SHARE_ROOM_INFO -> {}
//                case CLOSE_ROOM -> {}
//
//                case ASK_KEY -> {}
//                case SHARE_KEY -> {}
//
//                case ADD_MEMBER -> {}
//                case REMOVE_MEMBER -> {}
//
//                case UPDATE_DATA -> {}
//
//                case EXIT -> {}
//                default -> System.out.println("Unexpected value: " + requestBody.getOp());
//            }
//        }
//    }
//
//    private void sendServiceInfo(DataOutputStream dataOutputStream) throws IOException {
//        TalkUnit talkUnitSendService = new TalkUnit();
//
////        talkUnitSendService.setFrom(talkParams.getDealer());
//        talkUnitSendService.setIdType(TalkUnit.IdType.YOU);
//        talkUnitSendService.setDataType(TalkUnit.DataType.SIGNED_TEXT);
//
//        Signature signature = new Signature();
//        signature.sign(service.toJson(), dealerPriKey,AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7);
//
//        talkUnitSendService.setData(signature);
//
//        byte[] bytes = talkUnitSendService.toBytes();
//        dataOutputStream.writeInt(bytes.length);
//        dataOutputStream.write(bytes);
//        dataOutputStream.flush();
//    }
//
//    /*
//    signIn with twoWayAsy encrypted request and answer.
//    others with symKey encrypted request and answer.
//
//    encrypted signed text for special guarantee
//
//        1. send talk item
//            - send server-msg
//                + send data
//                + send error
//            - send text
//        2. all talkItem.data is Signature but Group text
//        3. signature.msg
//
//
//     */
////
////    private void replyError(String from, TalkUnit.ToType toType, String to, DataOutputStream dos, int code, String data, String otherError, Integer nonce, boolean byDealerPriKey) {
////
//////        TransferUnit transferUnit = new TransferUnit(from,toType,to, TransferUnit.DataType.ENCRYPTED_REPLY);
////        TalkUnit talkUnit;
////        replyOtherError();
////        FcReplier replier = new FcReplier();
////        String replyJson = replier.reply(code,otherError,data,nonce);
////
////        Encryptor encryptor;
////        CryptoDataByte cryptoDataByte;
////        byte[] dataBytes = replyJson.getBytes();
////
////        if(!byDealerPriKey){
////            if(sessionKey==null){
////                String str = "Server error: no key to encrypt data.";
////                sendToClient(dos,"Server error: no key to encrypt data.");
////                return;
////            }
////            encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
////            cryptoDataByte = encryptor.encryptBySymKey(dataBytes, this.sessionKey);
////        }else {
////            if(dealerPriKey ==null){
////                System.out.println("Server error: no key to encrypt data.");
////                sendToClient(dos,"Server error: no key to encrypt data.");
////                return;
////            }
////            encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
////            if( userPubKey!=null) cryptoDataByte = encryptor.encryptByAsyTwoWay(dataBytes, this.dealerPriKey,userPubKey);
////            else cryptoDataByte = encryptor.encryptByAsyOneWay(dataBytes, this.dealerPriKey);
////        }
////
////        talkUnit.setData(cryptoDataByte.toJson());
////        sendToClient(dos, talkUnit.toJson());
////    }
//
//
//    @SuppressWarnings("unused")
//    private boolean timeOut() {
//        return System.currentTimeMillis() - startTime > 5 * 60 * 1000;
//    }
////    public Long updateBalance(long oldBalance, String api, long length, Jedis jedis, Long price) {
////        if(userFid ==null)return null;
////
////        if(userFid.equals(talkParams.getDealer())){
////            String minPay = talkParams.getMinPayment();
////            balance= ParseTools.coinToSatoshi(Double.parseDouble(minPay));
////            return balance;
////        }
////
////        long newBalance;
////
////        long amount = length / 1000;
////        long nPrice;
////        if(api!=null)
////            nPrice = nPriceMap.get(api);
////        else nPrice=1;
////
////        long charge = amount * price * nPrice;
////
////        //update user balance
////
////        newBalance = oldBalance - charge;
////        if (newBalance < 0) {
////            charge = oldBalance;
////            jedis.hdel(addSidBriefToName(sid, Strings.BALANCE), userFid);
////            newBalance = 0;
////        } else
////            jedis.hset(addSidBriefToName(sid, Strings.BALANCE), userFid, String.valueOf(newBalance));
////
////        viaConsume += charge;
////        balance= newBalance;
////        return newBalance;
////    }
//
//
//    private void handleSignIn(TalkUnit talkUnitRequest, byte[] dealerPriKey, DataOutputStream dos, JedisPool jedisPool) {
////        if (timeOut()) return;
//
//        Integer nonce = talkUnitRequest.getNonce();
//
//        if(!TalkUnit.DataType.REQUEST.equals(talkUnitRequest.getDataType())){
//            replyError(ReplyCodeMessage.Code1013BadRequest,null, nonce,dos);
//            return ;
//        }
//        RequestBody requestBody = (RequestBody)talkUnitRequest.getData();
//
//        if(requestBody==null){
//            replyError(ReplyCodeMessage.Code1013BadRequest,null, nonce,dos);
//            return ;
//        }
//
//        //Check sign in
//        try (Jedis jedis = jedisPool.getResource()) {
//
//            if(!checkRequest(talkUnitRequest,requestBody, dos, jedis)) return;
//
//            long sessionDays;
//            try {
//                sessionDays = Long.parseLong(talkParams.getSessionDays());
//            }catch (Exception ignore){
//                sessionDays = 100L;
//            }
//
//            if(requestBody.getMode()==null || requestBody.getMode().equals(RequestBody.SignInMode.NORMAL))
//                session = FcSession.getSessionFromJedisWithFid(talkUnitRequest.getFrom(),sid,jedis);
//
//            if(session==null){
//                session = FcSession.makeNewSession(sid,jedis,talkUnitRequest.getFrom(),Hex.toHex(userPubKey),sessionDays, null);
//            }
//
//            this.userFid = session.getFid();
//            this.userPubKey = Hex.fromHex(session.getPubKey());
//
//
//        }
//    }
//
////    private RequestBody parseRequest(TalkUnit talkUnitRequest, DataOutputStream dos) {
////        RequestBody requestBody = (RequestBody)talkUnitRequest.getData();
////        try {
////            requestBody = RequestBody.fromJson(requestJson);
////            if(requestBody==null){
////                replyError(ReplyCodeMessage.Code1013BadRequest,null,talkUnitRequest.getNonce(),dos);
////                return null;
////            }
////        }catch (Exception ignore){
////            replyError(ReplyCodeMessage.Code1013BadRequest,null,talkUnitRequest.getNonce(),dos);
////            return null;
////        }
////        return requestBody;
////    }
//
//    private boolean checkRequest(TalkUnit talkUnitRequest,RequestBody requestBody, DataOutputStream dos, Jedis jedis) {
//            Integer nonce = talkUnitRequest.getNonce();
//            double windowTimeD = (Double) settings.getSettingMap().get(WINDOW_TIME);//RedisTools.readHashLong(jedis, Settings.addSidBriefToName(TalkServer.sid, SETTINGS), WINDOW_TIME);
//            long windowTime= (long)windowTimeD;
//
//            if (requestBody.getVia() != null) via = requestBody.getVia();
//            if (isBadBalanceTcp(jedis, TalkServer.sid, talkUnitRequest.getFrom())) {
//                String data = "Send at lest " + talkParams.getMinPayment() + " F to " + talkParams.getDealer() + " to buy the service #" + TalkServer.sid + ".";
//                replyError(ReplyCodeMessage.Code1004InsufficientBalance, data,nonce,dos);
//                return false;
//            }
//            if (!TalkServer.sid.equals(requestBody.getSid())) {
//                Map<String, String> dataMap = new HashMap<>();
//                dataMap.put("Signed SID:", requestBody.getSid());
//                dataMap.put("Requested SID:", TalkServer.sid);
//                replyOtherError("The signed SID is not the requested SID.", dataMap,nonce,dos);
//                return false;
//            }
//            if (isBadNonce(requestBody.getNonce(), windowTime, jedis)) {
//                replyError( ReplyCodeMessage.Code1007UsedNonce,null,nonce, dos);
//                return false;
//            }
//            if (isBadTime(requestBody.getTime(), windowTime)) {
//                Map<String, String> dataMap = new HashMap<>();
//                dataMap.put("windowTime", String.valueOf(windowTime));
//                replyError( ReplyCodeMessage.Code1006RequestTimeExpired,dataMap,nonce, dos);
//                return false;
//            }
//            return true;
//    }
//
//    public boolean isBadBalanceTcp(Jedis jedis, String sid, String fid) {
//        long balance = RedisTools.readHashLong(jedis, addSidBriefToName(sid, BALANCE), fid);
////        if(this.balance==0)this.balance=balance;
//        if(balance<=0)return true;
//        String priceStr = talkParams.getPricePerKBytes();
//        long price = ParseTools.coinStrToSatoshi(priceStr);
//        return balance<price;
//    }
//
//    @Nullable
//    public CryptoDataByte decryptWithPriKey(TalkUnit talkUnitRequest, byte[] dealerPriKey, byte[] sessionKey, DataOutputStream dos, Integer nonce) {
//        CryptoDataByte cryptoDataByte = CryptoDataByte.fromJson((String) talkUnitRequest.getData());
//        Decryptor decryptor = new Decryptor();
//        decryptor.decrypt(cryptoDataByte);
//
//        if(cryptoDataByte.getCode()!=0){
//            replyOtherError("Failed to decrypt request.",null, nonce, dos);
//            return null;
//        }
//        return cryptoDataByte;
//    }
////
////    private void handleText(String content, TalkUnit talkUnit, Socket socket, ApipClient apipClient, byte[] key) {
////        String fid = TalkTcpServer.socketFidMap.get(socket);
////        String roomId = talkUnit.getTo();
////        if (TalkTcpServer.roomIdFidsMap.get(roomId) == null) {
////            if (roomId.startsWith("F")) {
////                ArrayList<String> fidList = new ArrayList<>();
////                fidList.add(roomId.substring(0, roomId.indexOf("-")));
////                fidList.add(roomId.substring(roomId.indexOf("-") + 1));
////                TalkTcpServer.roomIdFidsMap.put(roomId, fidList);
////            } else if (roomId.startsWith("G")) {
////                String gid = roomId.substring(2);
////                Map<String, String[]> gidMembersMap = apipClient.groupMembers(RequestMethod.POST, AuthType.FC_SIGN_BODY, gid);
////                TalkTcpServer.roomIdFidsMap.put(gid, Arrays.stream(gidMembersMap.get(gid)).toList());
////            } else if (roomId.startsWith("T")) {
////                String tid = roomId.substring(2);
////                Map<String, String[]> tidMembersMap = apipClient.teamMembers(RequestMethod.POST, AuthType.FC_SIGN_BODY, tid);
////                TalkTcpServer.roomIdFidsMap.put(tid, Arrays.stream(tidMembersMap.get(tid)).toList());
////            } else if (Hex.isHexString(roomId)) {
////                try {
////                    pendingContent(content, socket);
////                    askRoomInfo(talkUnit, socket, key);
////                } catch (IOException e) {
////                    e.printStackTrace();
////                }
////            }
////            if (!TalkTcpServer.roomIdFidsMap.get(roomId).contains(fid)) return;
////            broadcastMessage(content, talkUnit.getTo());
////        }
////    }
//
//
//    private static void askRoomInfo(TalkUnit fromTalkUnit, Socket socket, byte[] key) throws IOException {
//        TalkUnit toTalkUnit = new TalkUnit();
//        toTalkUnit.setDataType(TalkUnit.DataType.ENCRYPTED_REQUEST);
//        toTalkUnit.setTo(fromTalkUnit.getTo());
//        toTalkUnit.setNonce(fromTalkUnit.getNonce());
//        String json = toTalkUnit.toJson();
//        Signature sign = new Signature().sign(Hex.toHex(key), json.getBytes(),AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7);
//        toTalkUnit.setData(JsonTools.toJson(sign));
//        OutputStreamWriter osw = new OutputStreamWriter(socket.getOutputStream());
//
//        osw.write(toTalkUnit.getData()+ "\n");
//        osw.flush();
//    }
//
//    private static void pendingContent(String content, Socket socket) {
//        if (socketPendingContentsMap == null) socketPendingContentsMap = new HashMap<>();
//        Map<String, Integer> contentTimesMap;
//        if (socketPendingContentsMap.get(socket) == null) {
//            contentTimesMap = new HashMap<>();
//            contentTimesMap.put(content, 1);
//            socketPendingContentsMap.put(socket, contentTimesMap);
//        } else {
//            contentTimesMap = socketPendingContentsMap.get(socket);
//            contentTimesMap.put(content, contentTimesMap.get(content)+1);
//            if(contentTimesMap.get(content)>3) {
//                contentTimesMap.remove(content);
//                return;
//            }
//            socketPendingContentsMap.put(socket, contentTimesMap);
//        }
//    }
//    private void broadcastMessage(String message, List<String> fidList) {
//        for (String fid : fidList) {
//            for (Socket socket : TalkServer.fidSocketsMap.get(fid)) {
//                try {
//                    OutputStreamWriter osw = new OutputStreamWriter(socket.getOutputStream());
//                    osw.write(message + "\n");
//                    osw.flush();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//    }
//}
