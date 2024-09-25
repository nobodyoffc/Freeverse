package startTalkServer;

import apip.apipData.RequestBody;
import clients.apipClient.ApipClient;
import fcData.TalkUnit;
import clients.redisClient.RedisTools;
import com.google.gson.JsonSyntaxException;
import constants.ReplyCodeMessage;
import constants.Strings;
import crypto.CryptoDataByte;
import crypto.Decryptor;
import crypto.Encryptor;
import crypto.KeyTools;
import fcData.AlgorithmId;
import fcData.Signature;
import fcData.FcReplier;
import fch.ParseTools;
import feip.feipData.Service;
import feip.feipData.serviceParams.TalkParams;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.http.AuthType;
import javaTools.http.RequestMethod;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import settings.Settings;
import settings.TalkServerSettings;

import java.io.*;
import java.net.Socket;
import java.util.*;

import static constants.Strings.*;
import static server.RequestChecker.*;
import static settings.Settings.addSidBriefToName;
import static startTalkServer.TalkTcpServer.*;

class ServerTcpThread extends Thread {
    private static final Logger log = LoggerFactory.getLogger(TalkTcpServer.class);
    private final Socket socket;
    private final ApipClient apipClient;
    private final JedisPool jedisPool;
    private String userFid;
    private byte[] userPubKey;
    private byte[] sessionKey;
    private String sessionName;
    private final byte[] dealerPriKey;
    private long startTime;
    private long balance;
    private String via;
    private long viaConsume;
    private  Map<String,Long> nPriceMap;
    private final Service service;
    private final TalkParams talkParams;
    private final TalkServerSettings settings;
    private long price;
    private final Map<String, Set<Socket>> fidSocketsMap;



    public ServerTcpThread(Socket socket, Map<String, Set<Socket>> fidSocketsMap, byte[] dealerPriKey, long price, Map<String,Long> nPriceMap, Service service, TalkServerSettings settings, ApipClient apipClient, JedisPool jedisPool) {
        this.socket = socket;
        this.apipClient = apipClient;
        this.jedisPool = jedisPool;
        this.dealerPriKey = dealerPriKey;
        this.service = service;
        this.talkParams = (TalkParams)service.getParams();
        this.price = price;
        this.settings = settings;
        this.nPriceMap = nPriceMap;
        this.fidSocketsMap = fidSocketsMap;
    }

    public void run() {
        startTime = System.currentTimeMillis();
        TalkUnit talkUnit;
        byte[] cipherBytes;
        try(DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            sendServiceInfo(dos);

            while (true) {
                if(balance<0)return;

                int length = dis.readInt();
                if(length==-1)continue;
                cipherBytes = new byte[length];
                dis.read(cipherBytes);

                try {
                    CryptoDataByte cryptoDataByte = CryptoDataByte.fromBundle(cipherBytes);
                    if(cryptoDataByte==null)return;
                    cryptoDataByte.setPriKeyB(dealerPriKey);
                    new Decryptor().decrypt(cryptoDataByte);
                    if(cryptoDataByte.getCode()!=0)return;
                    talkUnit = TalkUnit.fromBundle(cryptoDataByte.getData());

                } catch (JsonSyntaxException e) {
                    String msg = "Illegal message. Send TalkItem please.";
                    Integer code = ReplyCodeMessage.Code1020OtherError;
                    reply(code, msg,null, null, dos, dealerPriKey, sessionKey);
                    continue;
                }

                JsonTools.printJson(talkUnit);

                //Check sessionKey and signIn
                if (sessionKey==null){
                    Boolean done;
                    if(TalkUnit.DataType.REQUEST.equals(talkUnit.getDataType())){
                    //Check sign in
                        done = handleSignIn(talkUnit, dealerPriKey, dos, jedisPool);
                        if (!done){
                            if (timeOut()) return;
                            String str = "Failed to sign in. Try to sign in again.";
                            dos.writeInt(str.length());
                            dos.write(str.getBytes());
                            dos.flush();
                            continue;
                        }

                        synchronized (fidSocketsMap) {
                            Set<Socket> socketSet = fidSocketsMap.get(userFid);
                            if(socketSet==null)socketSet = new HashSet<>();
                            socketSet.add(socket);
                            fidSocketsMap.put(userFid,socketSet);
                        }
                    } else {
                        //Return no user info
                        String str = "Sign in please.";
                        dos.writeInt(str.length());
                        dos.write(str.getBytes());
                        dos.flush();
                        continue;
                    }
                }

                //Check server request
                RequestBody requestBody = null;

                if(talkUnit.getToType().equals(TalkUnit.ToType.SERVER))
                    HandleServerRequest(talkUnit, dos);

                switch (talkUnit.getToType()) {
                    case SELF -> {}
                    case FID -> {}
                    case GROUP -> {}
                    case TEAM -> {}
                    case FID_LIST -> {}
                    case ROOM -> {}
                    case ANYONE -> {}
                    case EVERYONE -> {}
                    default -> {
                        continue;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                updateUserBalance();
                if(via!=null)updateViaConsume();
                socket.close();
                synchronized (TalkTcpServer.sockets) {
                    TalkTcpServer.sockets.remove(socket);
                }
                System.out.println(userFid + " disconnected. Clients number: " + TalkTcpServer.sockets.size());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateUserBalance() {
        try(Jedis jedis = jedisPool.getResource()){
            if(balance>0)
                jedis.hset(Settings.addSidBriefToName(sid, FID_BALANCE),userFid,String.valueOf(balance));
            else jedis.hdel(Settings.addSidBriefToName(sid, FID_BALANCE),userFid);
        }
    }

    private void updateViaConsume() {
        try(Jedis jedis = jedisPool.getResource()){
            jedis.hset(Settings.addSidBriefToName(sid, CONSUME_VIA),via,String.valueOf(viaConsume));
        }
    }

    private void reply0Success(Object data, DataOutputStream dos, byte[] dealerPriKey, byte[] sessionKey, Integer nonce) {
        reply(ReplyCodeMessage.Code0Success,null,data, nonce, dos,dealerPriKey,sessionKey);
    }

    private void replyOtherError(String message, Object data, Integer nonce, DataOutputStream dos) {
        reply(ReplyCodeMessage.Code1020OtherError,message,data, nonce, dos,null,null);
    }
    private void replyOtherError(DataOutputStream dos, String message, Object data, Integer nonce, byte[] dealerPriKey, byte[] sessionKey) throws IOException {
        reply(ReplyCodeMessage.Code1020OtherError,message,data, nonce, dos,dealerPriKey,sessionKey);
    }
    private void replyError(Integer code, Object data, Integer nonce, DataOutputStream dos){
        reply(code,null,data, nonce, dos,null,null);
    }

    private void reply(Integer code, String message, Object data, Integer nonce, DataOutputStream dos, byte[] dealerPriKey, byte[] sessionKey) {
        TalkUnit talkUnitReply;
        FcReplier replier = new FcReplier();
        replier.setCode(code);
        replier.setMessage(ReplyCodeMessage.getMsg(code));
        replier.setNonce(nonce);

        if(message!=null)replier.setMessage(message);
        if(data!=null)replier.setData(data);

        talkUnitReply = new TalkUnit(this.talkParams.getDealer(), TalkUnit.ToType.YOU,null,null, TalkUnit.DataType.REPLY);
        talkUnitReply.setData(replier);
        byte[] bundle = talkUnitReply.toBundle();

        if(dealerPriKey==null && sessionKey==null){
            sendToClient(dos, bundle);
            return;
        }

        Encryptor encryptor;
        CryptoDataByte cryptoDataByte = null;

        if(sessionKey!=null){
            encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
            cryptoDataByte = encryptor.encryptBySymKey(bundle,sessionKey);
        }
        if(dealerPriKey!=null){
            encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
            cryptoDataByte = encryptor.encryptByAsyTwoWay(bundle,dealerPriKey,userPubKey);
        }

        if(cryptoDataByte==null || cryptoDataByte.getCode()!=0){
            replyOtherError("Failed to encrypt.",null, nonce, dos);
            return;
        }
        byte[] bytes = cryptoDataByte.toBundle();
        updateBalance(dos, REPLY, bytes.length);
        if(balance<0)return;
        sendToClient(dos, bytes);
    }

    private long updateBalance(DataOutputStream dos, String apiName, int length)  {
        long cost = length * nPriceMap.get(apiName) * price;
        balance -= cost;
        if(balance<0){
            if(via!=null)viaConsume+=(cost+balance);
            replyOtherError("No balance or balance was run out. Top up please.",null, null, dos);
        }
        if(via!=null)viaConsume+=cost;
        return balance;
    }

    @Nullable
    private void HandleServerRequest(TalkUnit talkUnit, DataOutputStream dos) {
        RequestBody requestBody;
        try (Jedis jedis = jedisPool.getResource()) {
            requestBody = checkRequest(talkUnit, dos, jedis);

            switch (requestBody.getOp()){
                case PING -> {}
                case SIGN_IN -> handleSignIn(talkUnit, dealerPriKey, dos, jedisPool);

                case CREAT_ROOM -> {}
                case ASK_ROOM_INFO -> {}
                case SHARE_ROOM_INFO -> {}
                case CLOSE_ROOM -> {}

                case ASK_KEY -> {}
                case SHARE_KEY -> {}

                case ADD_MEMBER -> {}
                case REMOVE_MEMBER -> {}

                case UPDATE_ITEMS -> {}

                case EXIT -> {}
            }
        }
    }

    private void sendServiceInfo(DataOutputStream dataOutputStream) throws IOException {
        TalkUnit talkUnitSendService = new TalkUnit();

        talkUnitSendService.setFrom(talkParams.getDealer());
        talkUnitSendService.setToType(TalkUnit.ToType.YOU);
        talkUnitSendService.setDataType(TalkUnit.DataType.SIGNED_TEXT);

        Signature signature = new Signature();
        signature.sign(service.toJson(), dealerPriKey,AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7);

        talkUnitSendService.setData(signature);

        byte[] bytes = talkUnitSendService.toBundle();
        dataOutputStream.writeInt(bytes.length);
        dataOutputStream.write(bytes);
        dataOutputStream.flush();
    }

    /*
    signIn with twoWayAsy encrypted request and answer.
    others with symKey encrypted request and answer.

    encrypted signed text for special guarantee

        1. send talk item
            - send server-msg
                + send data
                + send error
            - send text
        2. all talkItem.data is Signature but Group text
        3. signature.msg


     */
//
//    private void replyError(String from, TalkUnit.ToType toType, String to, DataOutputStream dos, int code, String data, String otherError, Integer nonce, boolean byDealerPriKey) {
//
////        TransferUnit transferUnit = new TransferUnit(from,toType,to, TransferUnit.DataType.ENCRYPTED_REPLY);
//        TalkUnit talkUnit;
//        replyOtherError();
//        FcReplier replier = new FcReplier();
//        String replyJson = replier.reply(code,otherError,data,nonce);
//
//        Encryptor encryptor;
//        CryptoDataByte cryptoDataByte;
//        byte[] dataBytes = replyJson.getBytes();
//
//        if(!byDealerPriKey){
//            if(sessionKey==null){
//                String str = "Server error: no key to encrypt data.";
//                sendToClient(dos,"Server error: no key to encrypt data.");
//                return;
//            }
//            encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
//            cryptoDataByte = encryptor.encryptBySymKey(dataBytes, this.sessionKey);
//        }else {
//            if(dealerPriKey ==null){
//                System.out.println("Server error: no key to encrypt data.");
//                sendToClient(dos,"Server error: no key to encrypt data.");
//                return;
//            }
//            encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
//            if( userPubKey!=null) cryptoDataByte = encryptor.encryptByAsyTwoWay(dataBytes, this.dealerPriKey,userPubKey);
//            else cryptoDataByte = encryptor.encryptByAsyOneWay(dataBytes, this.dealerPriKey);
//        }
//
//        talkUnit.setData(cryptoDataByte.toJson());
//        sendToClient(dos, talkUnit.toJson());
//    }

    private static void sendToClient(DataOutputStream dos, byte[]bytes) {
        try {
            dos.writeInt(bytes.length);
            dos.write(bytes);
            dos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private boolean timeOut() throws IOException {
        if (System.currentTimeMillis() - startTime > 5 * 60 * 1000) {
            socket.close();
            return true;
        }
        return false;
    }
    public Long updateBalance(long oldBalance, String api,long length, Jedis jedis, Long price) {
        if(userFid ==null)return null;

        if(userFid.equals(talkParams.getDealer())){
            String minPay = talkParams.getMinPayment();
            balance= ParseTools.coinToSatoshi(Double.parseDouble(minPay));
            return balance;
        }

        long newBalance;

        long amount = length / 1000;
        long nPrice;
        if(api!=null)
            nPrice = nPriceMap.get(api);
        else nPrice=1;

        long charge = amount * price * nPrice;

        //update user balance

        newBalance = oldBalance - charge;
        if (newBalance < 0) {
            charge = oldBalance;
            jedis.hdel(addSidBriefToName(sid, Strings.BALANCE), userFid);
            newBalance = 0;
        } else
            jedis.hset(addSidBriefToName(sid, Strings.BALANCE), userFid, String.valueOf(newBalance));

        viaConsume += charge;
        balance= newBalance;
        return newBalance;
    }

    private boolean handleSignIn(TalkUnit talkUnitRequest, byte[] dealerPriKey, DataOutputStream dos, JedisPool jedisPool) {

        try (Jedis jedis = jedisPool.getResource()) {
            Integer nonce = talkUnitRequest.getNonce();
            RequestBody requestBody = checkRequest(talkUnitRequest, dos, jedis);
            if (requestBody==null) return false;
            Long sessionDays=null;
            try {
                sessionDays = Long.parseLong(talkParams.getSessionDays());
            }catch (Exception ignore){}
            Session session = Session.makeNewSession(sid,jedis,userFid,sessionDays);
            sessionName = session.getName();
            sessionKey = session.getKey();

            reply0Success( sessionKey,dos, dealerPriKey,null, nonce);
            return true;
        }
    }

    private RequestBody checkRequest(TalkUnit talkUnitRequest, DataOutputStream dos, Jedis jedis) {
        RequestBody requestBody;
        Integer nonce = talkUnitRequest.getNonce();
        CryptoDataByte cryptoDataByte = decryptWithPriKey(talkUnitRequest, dealerPriKey, sessionKey,dos, nonce);
        if (cryptoDataByte != null) {
            String requestJson = new String(cryptoDataByte.getData());

            requestBody = RequestBody.fromJson(requestJson);
            long windowTime = settings.getWindowTime();//RedisTools.readHashLong(jedis, Settings.addSidBriefToName(TalkServer.sid, SETTINGS), WINDOW_TIME);
            this.userFid = KeyTools.pubKeyToFchAddr(cryptoDataByte.getPubKeyA());
            if (!userFid.equals(talkUnitRequest.getFrom())) {
                Map<String, String> dataMap = new HashMap<>();
                dataMap.put("userFid", userFid);
                dataMap.put("pubKey", Hex.toHex(cryptoDataByte.getPubKeyA()));
                replyOtherError("The pubKey is not of the user FID.", JsonTools.toJson(dataMap),nonce,dos);
            } else {
                if (requestBody.getVia() != null) via = requestBody.getVia();
                if (isBadBalanceTcp(jedis, TalkTcpServer.sid, userFid)) {
                    String otherError = "Send at lest " + talkParams.getMinPayment() + " F to " + talkParams.getMinPayment() + " to buy the service #" + TalkTcpServer.sid + ".";
                    String data = null;
                    replyOtherError(otherError, data,nonce,dos);
                } else if (!TalkTcpServer.sid.equals(requestBody.getSid())) {
                    Map<String, String> dataMap = new HashMap<>();
                    dataMap.put("Signed SID:", requestBody.getSid());
                    dataMap.put("Requested SID:", TalkTcpServer.sid);
                    replyOtherError("The signed SID is not the requested SID.", dataMap,nonce,dos);
                } else if (isBadNonce(requestBody.getNonce(), windowTime, jedis)) {
                    replyError( ReplyCodeMessage.Code1007UsedNonce,null,nonce, dos);
                } else if (isBadTime(requestBody.getTime(), windowTime)) {
                    Map<String, String> dataMap = new HashMap<>();
                    dataMap.put("windowTime", String.valueOf(windowTime));
                    replyError( ReplyCodeMessage.Code1006RequestTimeExpired,dataMap,nonce, dos);
                } else {
                    return requestBody;
                }
            }
        }
        return null;
    }

    public boolean isBadBalanceTcp(Jedis jedis, String sid, String fid) {
        long balance = RedisTools.readHashLong(jedis, addSidBriefToName(sid, BALANCE), fid);
        String priceStr = talkParams.getPricePerKBytes();
        long price = ParseTools.coinStrToSatoshi(priceStr);
        return balance<price;
    }

    @Nullable
    public CryptoDataByte decryptWithPriKey(TalkUnit talkUnitRequest, byte[] dealerPriKey, byte[] sessionKey, DataOutputStream dos, Integer nonce) {
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromJson((String) talkUnitRequest.getData());
        Decryptor decryptor = new Decryptor();
        decryptor.decrypt(cryptoDataByte);

        if(cryptoDataByte.getCode()!=0){
            replyOtherError("Failed to decrypt request.",null, nonce, dos);
            return null;
        }
        return cryptoDataByte;
    }

    private void handleText(String content, TalkUnit talkUnit, Socket socket, ApipClient apipClient, byte[] key) {
        String fid = TalkTcpServer.socketFidMap.get(socket);
        String roomId = talkUnit.getTo();
        if (TalkTcpServer.roomIdFidsMap.get(roomId) == null) {
            if (roomId.startsWith("F")) {
                ArrayList<String> fidList = new ArrayList<>();
                fidList.add(roomId.substring(0, roomId.indexOf("-")));
                fidList.add(roomId.substring(roomId.indexOf("-") + 1));
                TalkTcpServer.roomIdFidsMap.put(roomId, fidList);
            } else if (roomId.startsWith("G")) {
                String gid = roomId.substring(2);
                Map<String, String[]> gidMembersMap = apipClient.groupMembers(RequestMethod.POST, AuthType.FC_SIGN_BODY, gid);
                TalkTcpServer.roomIdFidsMap.put(gid, Arrays.stream(gidMembersMap.get(gid)).toList());
            } else if (roomId.startsWith("T")) {
                String tid = roomId.substring(2);
                Map<String, String[]> tidMembersMap = apipClient.teamMembers(RequestMethod.POST, AuthType.FC_SIGN_BODY, tid);
                TalkTcpServer.roomIdFidsMap.put(tid, Arrays.stream(tidMembersMap.get(tid)).toList());
            } else if (Hex.isHexString(roomId)) {
                try {
                    pendingContent(content, socket);
                    askRoomInfo(talkUnit, socket, key);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (!TalkTcpServer.roomIdFidsMap.get(roomId).contains(fid)) return;
            broadcastMessage(content, talkUnit.getTo());
        }
    }


    private static void askRoomInfo(TalkUnit fromTalkUnit, Socket socket, byte[] key) throws IOException {
        TalkUnit toTalkUnit = new TalkUnit();
        toTalkUnit.setDataType(TalkUnit.DataType.ENCRYPTED_REQUEST);
        toTalkUnit.setTo(fromTalkUnit.getTo());
        toTalkUnit.setNonce(fromTalkUnit.getNonce());
        String json = toTalkUnit.toJson();
        Signature sign = new Signature().sign(Hex.toHex(key), json.getBytes(),AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7);
        toTalkUnit.setData(JsonTools.toJson(sign));
        OutputStreamWriter osw = new OutputStreamWriter(socket.getOutputStream());

        osw.write(toTalkUnit.getData()+ "\n");
        osw.flush();
    }

    private static void pendingContent(String content, Socket socket) {
        if (socketPendingContentsMap == null) socketPendingContentsMap = new HashMap<>();
        Map<String, Integer> contentTimesMap;
        if (socketPendingContentsMap.get(socket) == null) {
            contentTimesMap = new HashMap<>();
            contentTimesMap.put(content, 1);
            socketPendingContentsMap.put(socket, contentTimesMap);
        } else {
            contentTimesMap = socketPendingContentsMap.get(socket);
            contentTimesMap.put(content, contentTimesMap.get(content)+1);
            if(contentTimesMap.get(content)>3) {
                contentTimesMap.remove(content);
                return;
            }
            socketPendingContentsMap.put(socket, contentTimesMap);
        }
    }
    private void broadcastMessage(String message, String roomId) {
        for (String fid : TalkTcpServer.roomIdFidsMap.get(roomId)) {
            for (Socket socket : TalkTcpServer.fidSocketsMap.get(fid)) {
                try {
                    OutputStreamWriter osw = new OutputStreamWriter(socket.getOutputStream());
                    osw.write(message + "\n");
                    osw.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
