package test;

import apip.apipData.RequestBody;
import appTools.Settings;
import clients.apipClient.ApipClient;
import clients.redisClient.RedisTools;
import constants.ReplyCodeMessage;
import constants.Strings;
import crypto.CryptoDataByte;
import crypto.Decryptor;
import crypto.Encryptor;
import crypto.KeyTools;
import fcData.AlgorithmId;
import fcData.FcReplier;
import fcData.Signature;
import fcData.TalkUnit;
import fch.ParseTools;
import feip.feipData.Service;
import feip.feipData.serviceParams.TalkParams;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import startTalkServer.TalkTcpServer;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import static constants.Strings.BALANCE;
import static server.RequestChecker.isBadNonce;
import static server.RequestChecker.isBadTime;
import static appTools.Settings.addSidBriefToName;
import static startTalkServer.TalkTcpServer.sid;
import static startTalkServer.TalkTcpServer.socketPendingContentsMap;

@SuppressWarnings("unused")
class ServerUdpThread extends Thread {
    private static final Logger log = LoggerFactory.getLogger(TalkTcpServer.class);
    private final ApipClient apipClient;
    private final JedisPool jedisPool;
    private String userFid;
    private String userPubKey;
    private byte[] sessionKey;
    private final byte[] accountPriKey;
    private long startTime;
    private long balance;
    private String via;
    private long viaConsume;
    private  Map<String,Long> nPriceMap;
    private final Service service;
    private final TalkParams talkParams;
    private final Settings settings;



    public ServerUdpThread( byte[] accountPriKey, Map<String,Long> nPriceMap, Service service, Settings settings, ApipClient apipClient, JedisPool jedisPool) {
        this.apipClient = apipClient;
        this.jedisPool = jedisPool;
        this.accountPriKey = accountPriKey;
        this.nPriceMap = nPriceMap;
        this.service = service;
        this.talkParams = (TalkParams)service.getParams();
        this.settings = settings;
    }

    public void run() {
        startTime = System.currentTimeMillis();
        String talkItemStr;
        TalkUnit talkUnit;
//
//        sendServiceInfo();
//
//        while (true) {
//            talkItemStr = brFromClient.readLine();
//            if(talkItemStr==null)continue;
//
//            //System.out.println(talkItemStr);
//            try {
//                transferUnit = TransferUnit.fromJson(talkItemStr);
//                if(transferUnit ==null)continue;
//            } catch (JsonSyntaxException e) {
//                replyEncrypted(talkParams.getAccount(), TransferUnit.ToType.FID, userFid, outputStreamWriter, ReplyCodeMessage.Code1020OtherError, null,"No TalkItem object.", null,false);
//                outputStreamWriter.write("Illegal message:" + talkItemStr + ". Send TalkItem please.");
//                outputStreamWriter.flush();
//                continue;
//            }
//
//            JsonTools.printJson(transferUnit);
//
//            //Check sessionKey and signIn
//            if (sessionKey==null){
//                Boolean done;
//                if(TransferUnit.DataType.ENCRYPTED_REQUEST.equals(transferUnit.getDataType())){
//                //Check sign in
//                    done = handleSignIn(transferUnit, accountPriKey, outputStreamWriter, jedisPool);
//                    if (!done){
//                        outputStreamWriter.write("Failed to sign in. Bye.");
//                        outputStreamWriter.flush();
//                        if (timeOut()) return;
//                    }
//
//                    synchronized (TalkTcpServer.sockets) {
//                        TalkTcpServer.sockets.add(socket);
//                    }
//                } else {
//                    //Return no user info
//                    outputStreamWriter.write("Sign in please.");
//                    outputStreamWriter.flush();
//                    continue;
//                }
//            }
//
//            //Check server request
//            RequestBody requestBody = null;
//
//            if(transferUnit.getToType().equals(TransferUnit.ToType.SERVER))
//                HandleServerRequest(transferUnit, outputStreamWriter);
//
//            switch (transferUnit.getToType()) {
//                case SELF -> {}
//                case FID -> {}
//                case GROUP -> {}
//                case TEAM -> {}
//                case FID20_LIST -> {}
//                case ROOM -> {}
//                case ANYONE -> {}
//                case EVERYONE -> {}
//                default -> {
//                    continue;
//                }
//            }
//        }

    }

    @Nullable
    private void HandleServerRequest(TalkUnit talkUnit, OutputStreamWriter outputStreamWriter) {
        RequestBody requestBody;
        try (Jedis jedis = jedisPool.getResource()) {
            requestBody = checkRequest(talkUnit, outputStreamWriter, jedis);

            switch (requestBody.getOp()){
                case PING -> {}
                case SIGN_IN -> handleSignIn(talkUnit, accountPriKey, outputStreamWriter, jedisPool);

                case CREAT_ROOM -> {}
                case ASK_ROOM_INFO -> {}
                case SHARE_ROOM_INFO -> {}
                case CLOSE_ROOM -> {}

                case ASK_KEY -> {}
                case SHARE_KEY -> {}

                case ADD_MEMBER -> {}
                case REMOVE_MEMBER -> {}

                case UPDATE_DATA -> {}

                case EXIT -> {}
                default -> throw new IllegalArgumentException("Unexpected value: " + requestBody.getOp());
            }
        }
    }

    private void sendServiceInfo(OutputStreamWriter outputStreamWriter) throws IOException {
        TalkUnit talkUnitTouch = new TalkUnit();
        talkUnitTouch.setDataType(TalkUnit.DataType.SIGNED_REQUEST);

        Signature signature = new Signature();
        signature.sign(service.toJson(), accountPriKey,AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7);

        talkUnitTouch.setData(signature.toJson());

        outputStreamWriter.write(talkUnitTouch.toJson()+"\n");
        outputStreamWriter.flush();
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

    private void replyEncrypted(String from, TalkUnit.IdType toType, String to, OutputStreamWriter outputStreamWriter, int code, String data, String otherError, Integer nonce, boolean byAccountPriKey) {

//        TransferUnit transferUnit = new TransferUnit(from,toType,to, TransferUnit.DataType.ENCRYPTED_REPLY);
        TalkUnit talkUnit = new TalkUnit();
        FcReplier replier = new FcReplier();
        String replyJson = replier.reply(code,otherError,data,nonce);
        
        Encryptor encryptor;
        CryptoDataByte cryptoDataByte;
        byte[] dataBytes = replyJson.getBytes();

        if(!byAccountPriKey){
            if(sessionKey==null){
                System.out.println("Server error: no key to encrypt data.");
                sendToClient(outputStreamWriter,"Server error: no key to encrypt data.");
                return;
            }
            encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
            cryptoDataByte = encryptor.encryptBySymKey(dataBytes, this.sessionKey);
        }else {
            if(accountPriKey==null){
                System.out.println("Server error: no key to encrypt data.");
                sendToClient(outputStreamWriter,"Server error: no key to encrypt data.");
                return;
            }
            encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
            if( userPubKey!=null) cryptoDataByte = encryptor.encryptByAsyTwoWay(dataBytes, this.accountPriKey,userPubKey.getBytes());
            else cryptoDataByte = encryptor.encryptByAsyOneWay(dataBytes, this.accountPriKey);
        }
        
        talkUnit.setData(cryptoDataByte.toJson());
        sendToClient(outputStreamWriter, talkUnit.toJson());
    }

    private static void sendToClient(OutputStreamWriter outputStreamWriter, String msg) {
        try {
            outputStreamWriter.write(msg + "\n");
            outputStreamWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private boolean timeOut() throws IOException {
        if (System.currentTimeMillis() - startTime > 5 * 60 * 1000) {
//            socket.close();
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

    private boolean handleSignIn(TalkUnit talkUnitRequest, byte[] waiterPriKey, OutputStreamWriter osw, JedisPool jedisPool) {

        try (Jedis jedis = jedisPool.getResource()) {
            Integer nonce = talkUnitRequest.getNonce();
            RequestBody requestBody = checkRequest(talkUnitRequest, osw, jedis);
            if (requestBody==null) return false;

            sessionKey = BytesTools.getRandomBytes(32);
            Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
            CryptoDataByte result = encryptor.encryptByAsyTwoWay(sessionKey, waiterPriKey,Hex.fromHex(userPubKey));
            if(result==null || result.getCode()!=0)return false;
            replyEncrypted(talkParams.getDealer(), TalkUnit.IdType.FID, userFid, osw,ReplyCodeMessage.Code0Success, result.toJson(), null, nonce, false);

            return true;
        }
    }

    private RequestBody checkRequest(TalkUnit talkUnitRequest, OutputStreamWriter osw, Jedis jedis) {
        RequestBody requestBody;
        Integer nonce = talkUnitRequest.getNonce();
        CryptoDataByte cryptoDataByte = decryptWithPriKey(talkUnitRequest, accountPriKey, sessionKey,osw, nonce);
        if (cryptoDataByte != null) {
            String requestJson = new String(cryptoDataByte.getData());

            requestBody = RequestBody.fromJson(requestJson);
            long windowTime = (Long)settings.getSettingMap().get(Settings.WINDOW_TIME);//RedisTools.readHashLong(jedis, Settings.addSidBriefToName(TalkServer.sid, SETTINGS), WINDOW_TIME);
            this.userFid = KeyTools.pubKeyToFchAddr(cryptoDataByte.getPubKeyA());
            if (!userFid.equals(talkUnitRequest.getFrom())) {
                Map<String, String> dataMap = new HashMap<>();
                dataMap.put("userFid", userFid);
                dataMap.put("pubKey", Hex.toHex(cryptoDataByte.getPubKeyA()));
                replyEncrypted(talkParams.getDealer(), TalkUnit.IdType.FID, userFid, osw, ReplyCodeMessage.Code1020OtherError, JsonTools.toJson(dataMap), "The pubKey is not of the user FID.", nonce, false);
            } else {
                if (requestBody.getVia() != null) via = requestBody.getVia();
                if (isBadBalanceTcp(jedis, TalkTcpServer.sid, userFid)) {
                    String otherError = "Send at lest " + talkParams.getMinPayment() + " F to " + talkParams.getMinPayment() + " to buy the service #" + TalkTcpServer.sid + ".";
                    String data = null;
                    replyEncrypted(talkParams.getDealer(), TalkUnit.IdType.FID, userFid, osw, ReplyCodeMessage.Code1020OtherError, data, otherError, nonce, false);
                } else if (!TalkTcpServer.sid.equals(requestBody.getSid())) {
                    Map<String, String> dataMap = new HashMap<>();
                    dataMap.put("Signed SID:", requestBody.getSid());
                    dataMap.put("Requested SID:", TalkTcpServer.sid);
                    replyEncrypted(talkParams.getDealer(), TalkUnit.IdType.FID, userFid, osw, ReplyCodeMessage.Code1020OtherError, JsonTools.toJson(dataMap), "The signed SID is not the requested SID.", nonce, false);
                } else if (isBadNonce(requestBody.getNonce(), windowTime, jedis)) {
                    replyEncrypted(talkParams.getDealer(), TalkUnit.IdType.FID, userFid, osw, ReplyCodeMessage.Code1007UsedNonce, null, null, nonce, false);
                } else if (isBadTime(requestBody.getTime(), windowTime)) {
                    Map<String, String> dataMap = new HashMap<>();
                    dataMap.put("windowTime", String.valueOf(windowTime));
                    replyEncrypted(talkParams.getDealer(), TalkUnit.IdType.FID, userFid, osw, ReplyCodeMessage.Code1006RequestTimeExpired, JsonTools.toJson(dataMap), null, nonce, false);
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
    public CryptoDataByte decryptWithPriKey(TalkUnit talkUnitRequest, byte[] waiterPriKey, byte[] sessionKey, OutputStreamWriter osw, Integer nonce) {
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromJson((String) talkUnitRequest.getData());
        Decryptor decryptor = new Decryptor();
        switch (cryptoDataByte.getType()){
            case AsyTwoWay -> 
                cryptoDataByte.setPriKeyB(waiterPriKey);
            case SymKey -> cryptoDataByte.setSymKey(sessionKey);
            default -> {
                replyEncrypted(talkParams.getDealer(), TalkUnit.IdType.FID, userFid, osw,ReplyCodeMessage.Code1020OtherError,null,"Failed to decrypt type:"+cryptoDataByte.getType(), nonce, false);
                return null;
            }
        }
        
        decryptor.decryptByAsyKey(cryptoDataByte);

        if(cryptoDataByte.getCode()!=0){
            replyEncrypted(talkParams.getDealer(), TalkUnit.IdType.FID, userFid, osw,ReplyCodeMessage.Code1020OtherError,null,"Failed to decrypt request.", nonce, false);
            return null;
        }
        return cryptoDataByte;
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
}
