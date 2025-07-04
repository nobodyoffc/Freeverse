package test;

import data.apipData.RequestBody;
import config.Settings;
import clients.ApipClient;
import constants.FieldNames;
import handlers.NonceManager;
import utils.*;
import constants.CodeMessage;
import core.crypto.CryptoDataByte;
import core.crypto.Decryptor;
import core.crypto.Encryptor;
import core.crypto.KeyTools;
import data.fcData.AlgorithmId;
import data.fcData.ReplyBody;
import data.fcData.Signature;
import data.fcData.TalkUnit;
import data.feipData.Service;
import data.feipData.serviceParams.TalkParams;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.TalkServer;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import static constants.FieldNames.BALANCE;
import static config.Settings.addSidBriefToName;

@SuppressWarnings("unused")
class ServerUdpThread extends Thread {
    private static final Logger log = LoggerFactory.getLogger(TalkServer.class);
    private final ApipClient apipClient;
    private final JedisPool jedisPool;
    private String userFid;
    private String userPubkey;
    private byte[] sessionKey;
    private final byte[] accountPrikey;
    private long startTime;
    private long balance;
    private String via;
    private long viaConsume;
    private  Map<String,Long> nPriceMap;
    private final Service service;
    private final TalkParams talkParams;
    private final Settings settings;



    public ServerUdpThread( byte[] accountPrikey, Map<String,Long> nPriceMap, Service service, Settings settings, ApipClient apipClient, JedisPool jedisPool) {
        this.apipClient = apipClient;
        this.jedisPool = jedisPool;
        this.accountPrikey = accountPrikey;
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
//                    done = handleSignIn(transferUnit, accountPrikey, outputStreamWriter, jedisPool);
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
                case SIGN_IN -> handleSignIn(talkUnit, accountPrikey, outputStreamWriter, jedisPool);
                case ASK_KEY -> {}
                case SHARE_KEY -> {}
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
        signature.sign(service.toJson(), accountPrikey,AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7);

        talkUnitTouch.setData(signature.toJson());

        outputStreamWriter.write(talkUnitTouch.toJson()+"\n");
        outputStreamWriter.flush();
    }

    /*
    signIn with twoWayAsy encrypted request and answer.
    others with symkey encrypted request and answer.

    encrypted signed text for special guarantee

        1. send talk item
            - send server-msg
                + send data
                + send error
            - send text
        2. all talkItem.data is Signature but Group text
        3. signature.msg


     */

    private void replyEncrypted(String from, TalkUnit.IdType toType, String to, OutputStreamWriter outputStreamWriter, int code, String data, String otherError, Integer nonce, boolean byAccountPrikey) {

//        TransferUnit transferUnit = new TransferUnit(from,toType,to, TransferUnit.DataType.ENCRYPTED_REPLY);
        TalkUnit talkUnit = new TalkUnit();
        ReplyBody replier = new ReplyBody();
        String replyJson = replier.reply(code,otherError,data);
        
        Encryptor encryptor;
        CryptoDataByte cryptoDataByte;
        byte[] dataBytes = replyJson.getBytes();

        if(!byAccountPrikey){
            if(sessionKey==null){
                System.out.println("Server error: no key to encrypt data.");
                sendToClient(outputStreamWriter,"Server error: no key to encrypt data.");
                return;
            }
            encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
            cryptoDataByte = encryptor.encryptBySymkey(dataBytes, this.sessionKey);
        }else {
            if(accountPrikey==null){
                System.out.println("Server error: no key to encrypt data.");
                sendToClient(outputStreamWriter,"Server error: no key to encrypt data.");
                return;
            }
            encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
            if( userPubkey!=null) cryptoDataByte = encryptor.encryptByAsyTwoWay(dataBytes, this.accountPrikey,userPubkey.getBytes());
            else cryptoDataByte = encryptor.encryptByAsyOneWay(dataBytes, this.accountPrikey);
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
            balance= utils.FchUtils.coinToSatoshi(Double.parseDouble(minPay));
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
            jedis.hdel(addSidBriefToName(service.getId(), FieldNames.BALANCE), userFid);
            newBalance = 0;
        } else
            jedis.hset(addSidBriefToName(service.getId(), FieldNames.BALANCE), userFid, String.valueOf(newBalance));

        viaConsume += charge;
        balance= newBalance;
        return newBalance;
    }

    private boolean handleSignIn(TalkUnit talkUnitRequest, byte[] waiterPrikey, OutputStreamWriter osw, JedisPool jedisPool) {

        try (Jedis jedis = jedisPool.getResource()) {
            Integer nonce = talkUnitRequest.getNonce();
            RequestBody requestBody = checkRequest(talkUnitRequest, osw, jedis);
            if (requestBody==null) return false;

            sessionKey = BytesUtils.getRandomBytes(32);
            Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
            CryptoDataByte result = encryptor.encryptByAsyTwoWay(sessionKey, waiterPrikey,Hex.fromHex(userPubkey));
            if(result==null || result.getCode()!=0)return false;
            replyEncrypted(talkParams.getDealer(), TalkUnit.IdType.FID, userFid, osw, CodeMessage.Code0Success, result.toJson(), null, nonce, false);

            return true;
        }
    }

    private RequestBody checkRequest(TalkUnit talkUnitRequest, OutputStreamWriter osw, Jedis jedis) {
        RequestBody requestBody;
        Integer nonce = talkUnitRequest.getNonce();
        CryptoDataByte cryptoDataByte = decryptWithPrikey(talkUnitRequest, accountPrikey, sessionKey,osw, nonce);
        if (cryptoDataByte != null) {
            String requestJson = new String(cryptoDataByte.getData());

            requestBody = RequestBody.fromJson(requestJson,RequestBody.class);
            long windowTime = (Long)settings.getSettingMap().get(Settings.WINDOW_TIME);//RedisTools.readHashLong(jedis, Settings.addSidBriefToName(TalkServer.sid, SETTINGS), WINDOW_TIME);
            this.userFid = KeyTools.pubkeyToFchAddr(cryptoDataByte.getPubkeyA());
            if (!userFid.equals(talkUnitRequest.getFrom())) {
                Map<String, String> dataMap = new HashMap<>();
                dataMap.put("userFid", userFid);
                dataMap.put("pubkey", Hex.toHex(cryptoDataByte.getPubkeyA()));
                replyEncrypted(talkParams.getDealer(), TalkUnit.IdType.FID, userFid, osw, CodeMessage.Code1020OtherError, JsonUtils.toJson(dataMap), "The pubkey is not of the user FID.", nonce, false);
            } else {
                if (requestBody.getVia() != null) via = requestBody.getVia();
                if (isBadBalanceTcp(jedis, service.getId(), userFid)) {
                    String otherError = "Send at lest " + talkParams.getMinPayment() + " F to " + talkParams.getMinPayment() + " to buy the service #" + service.getId() + ".";
                    String data = null;
                    replyEncrypted(talkParams.getDealer(), TalkUnit.IdType.FID, userFid, osw, CodeMessage.Code1020OtherError, data, otherError, nonce, false);
                } else if (!service.getId().equals(requestBody.getSid())) {
                    Map<String, String> dataMap = new HashMap<>();
                    dataMap.put("Signed SID:", requestBody.getSid());
                    dataMap.put("Requested SID:", service.getId());
                    replyEncrypted(talkParams.getDealer(), TalkUnit.IdType.FID, userFid, osw, CodeMessage.Code1020OtherError, JsonUtils.toJson(dataMap), "The signed SID is not the requested SID.", nonce, false);
                } else if (isBadNonce(requestBody.getNonce(), windowTime, jedis)) {
                    replyEncrypted(talkParams.getDealer(), TalkUnit.IdType.FID, userFid, osw, CodeMessage.Code1007UsedNonce, null, null, nonce, false);
                } else if (NonceManager.isBadTime(requestBody.getTime(), windowTime)) {
                    Map<String, String> dataMap = new HashMap<>();
                    dataMap.put("windowTime", String.valueOf(windowTime));
                    replyEncrypted(talkParams.getDealer(), TalkUnit.IdType.FID, userFid, osw, CodeMessage.Code1006RequestTimeExpired, JsonUtils.toJson(dataMap), null, nonce, false);
                } else {
                    return requestBody;
                }
            }
        }
        return null;
    }

    public boolean isBadBalanceTcp(Jedis jedis, String sid, String fid) {
        long balance = RedisUtils.readHashLong(jedis, addSidBriefToName(sid, BALANCE), fid);
        String priceStr = talkParams.getPricePerKBytes();
        long price = FchUtils.coinStrToSatoshi(priceStr);
        return balance<price;
    }
    @Nullable
    public CryptoDataByte decryptWithPrikey(TalkUnit talkUnitRequest, byte[] waiterPrikey, byte[] sessionKey, OutputStreamWriter osw, Integer nonce) {
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromJson((String) talkUnitRequest.getData());
        Decryptor decryptor = new Decryptor();
        switch (cryptoDataByte.getType()){
            case AsyTwoWay -> 
                cryptoDataByte.setPrikeyB(waiterPrikey);
            case Symkey -> cryptoDataByte.setSymkey(sessionKey);
            default -> {
                replyEncrypted(talkParams.getDealer(), TalkUnit.IdType.FID, userFid, osw, CodeMessage.Code1020OtherError,null,"Failed to decrypt type:"+cryptoDataByte.getType(), nonce, false);
                return null;
            }
        }
        
        decryptor.decryptByAsyKey(cryptoDataByte);

        if(cryptoDataByte.getCode()!=0){
            replyEncrypted(talkParams.getDealer(), TalkUnit.IdType.FID, userFid, osw, CodeMessage.Code1020OtherError,null,"Failed to decrypt request.", nonce, false);
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
        toTalkUnit.setData(JsonUtils.toJson(sign));
        OutputStreamWriter osw = new OutputStreamWriter(socket.getOutputStream());
        osw.write(toTalkUnit.getData()+ "\n");
        osw.flush();
    }
    public boolean isBadNonce(long nonce, long windowTime, Jedis jedis){
        if(windowTime==0)return false;
        jedis.select(2);
        if (nonce == 0) return true;
        String nonceStr = String.valueOf(nonce);
        if (jedis.get(nonceStr) != null)
            return true;
        jedis.set(nonceStr, "");
        jedis.expire(nonceStr, windowTime);
        jedis.select(0);
        return false;
    }
}
