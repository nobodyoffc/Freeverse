package utils;

import apip.apipData.RequestBody;
import ch.qos.logback.classic.Logger;
import handlers.AccountHandler;
import clients.ApipClient;
import clients.DiskClient;
import clients.TalkClient;
import constants.CodeMessage;
import crypto.Hash;
import crypto.KeyTools;
import fcData.*;
import fcData.TalkUnit.DataType;
import fcData.TalkUnit.State;
import handlers.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import server.TalkServer;

import java.util.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import static fcData.TalkUnit.IdType.*;

public class TalkUnitExecutor {
    public final static Logger log = (Logger) LoggerFactory.getLogger(TalkUnitExecutor.class);
    private TalkServer talkServer;
    private TalkClient talkClient;


    private final DiskClient diskClient;
    private final DiskHandler diskHandler;
    private final HatHandler hatHandler;
    private final SessionHandler sessionHandler;
    private final TalkUnitHandler talkUnitHandler;
    private final TeamHandler teamHandler;
    private final GroupHandler groupHandler;
    private final ApipClient apipClient;
    private TalkIdHandler talkIdHandler;
    private AccountHandler accountHandler;



    private Integer code;
    private String message;
    private Object data;
    private TalkUnit executeTalkUnit;
    private String displayText;
    private Map<String,Integer> payToForBytes;
    private List<TalkUnit> sendTalkUnitList = new ArrayList<>();

    public TalkUnitExecutor(TalkServer talkServer){
        this.talkServer = talkServer;
        this.diskClient = talkServer.getDiskClient();
        this.teamHandler = talkServer.getTeamHandler();
        this.groupHandler = talkServer.getGroupHandler();
        this.apipClient = talkServer.getApipClient();
        this.sessionHandler = talkServer.getSessionHandler();
        this.talkUnitHandler = talkServer.getTalkUnitHandler();
        this.diskHandler = talkServer.getDiskHandler();
        this.hatHandler = talkServer.getHatHandler();
        this.accountHandler = talkServer.getAccountHandler();
    }

    public TalkUnitExecutor(TalkClient talkClient){
        this.talkClient = talkClient;
        this.diskClient = talkClient.getDiskClient();
        this.teamHandler = talkClient.getTeamHandler();
        this.groupHandler = talkClient.getGroupHandler();
        this.apipClient = talkClient.getApipClient();
        this.sessionHandler = talkClient.getSessionHandler();
        this.talkUnitHandler = talkClient.getTalkUnitHandler();
        this.diskHandler = talkClient.getDiskHandler();
        this.hatHandler = talkClient.getHatHandler();
        this.talkIdHandler = talkClient.getTalkIdHandler();
    }

//    public static void setGot(String id, TalkUnitHandler talkUnitHandler) {
//        TalkUnit talkUnit = talkUnitHandler.getPending(id);
//        talkUnit.setStata(State.GOT);
//        if(talkUnit.getDataType()!=DataType.REQUEST)
//            talkUnitHandler.movePendingToMain(id);
//    }

//    public static void setRelayed(String id, TalkUnitHandler talkUnitHandler) {
//        TalkUnit talkUnit = talkUnitHandler.getPending(id);
//        talkUnit.setStata(State.RELAYED);
//        talkUnitHandler.putPending(talkUnit);
//    }

    private static void closeRequestUnit(TalkUnitHandler talkUnitHandler, TalkUnit parsedTalkUnit, ReplyBody replyBody) {
        TalkUnit requestTalkUnit = talkUnitHandler.get(replyBody.getRequestId(),parsedTalkUnit);
        if(requestTalkUnit==null) return;
        requestTalkUnit.setStata(State.DONE);
        parsedTalkUnit.setStata(State.DONE);
        talkUnitHandler.put(requestTalkUnit);
        talkUnitHandler.put(parsedTalkUnit);
    }

    // public static byte[] reply(String requestTalkUnitId, String fromFid, String toFid,
    //                            Integer code, String message, Object data, Integer nonce, byte[] sessionKey,
    //                            byte[] myPriKey, String userPubKey, @Nullable Long price, Long nPrice,
    //                            AccountHandler accountHandler, EncryptType unitEncryptType, EncryptType dataEncryptType) {

    //     TalkUnit talkUnitReply = createReplyUnit(fromFid,requestTalkUnitId, toFid, code, message, data, nonce);
    //     talkUnitReply.setUnitEncryptType(unitEncryptType);
    //     talkUnitReply.setDataEncryptType(dataEncryptType);

    //     talkUnitReply.makeTalkUnit(sessionKey, myPriKey, userPubKey);

    //     CryptoDataByte cryptoDataByte1 = talkUnitReply.encryptUnit(sessionKey, myPriKey, userPubKey, unitEncryptType);
    //     if (cryptoDataByte1 == null) return null;
    //     byte[] bytes = cryptoDataByte1.toBundle();

    //     if (price != null && code!=CodeMessage.Code1004InsufficientBalance) {
    //         long newBalance = updateSenderBalance(toFid, accountHandler, price, nPrice, bytes.length);
    //         if (newBalance < 0) {
    //             return reply(requestTalkUnitId, fromFid, toFid, CodeMessage.Code1004InsufficientBalance, "No balance or balance was run out. Top up please.", null, nonce, sessionKey, myPriKey, userPubKey, null, nPrice,accountHandler,unitEncryptType,dataEncryptType);
    //         }
    //     }
    //     return bytes;
    // }

    public TalkUnit reply(Integer code,String message,Object data,Op op){
        String msg = message;
        if(code!=null && msg==null)msg = CodeMessage.getMsg(code);
        return TalkUnit.createReplyUnit(this.executeTalkUnit,code,message,data,op);
    }
    public TalkUnit replySuccess(Object data,Op op){
        return  reply(CodeMessage.Code0Success,CodeMessage.Msg0Success,data,op);
    }

    public TalkUnit replyOtherError(String message,Object data,Op op){
        return  reply(CodeMessage.Code1020OtherError,message,data,op);
    }

    public static Boolean checkGotOrRelay(TalkUnit decryptedTalkUnit, TalkUnitHandler talkUnitHandler) {
        if(decryptedTalkUnit.getDataType()!= DataType.ENCRYPTED_GOT && decryptedTalkUnit.getDataType()!= DataType.ENCRYPTED_RELAYED && decryptedTalkUnit.getDataType()!= DataType.ENCRYPTED_SIGNED_GOT && decryptedTalkUnit.getDataType()!= DataType.ENCRYPTED_SIGNED_RELAYED)return false;
        if(!(decryptedTalkUnit.getData() instanceof String id))return false;

        TalkUnit sourceTalkUnit = talkUnitHandler.get(id,decryptedTalkUnit);
        if(sourceTalkUnit==null){
            log.debug("Failed to got sent talkUnit {}",decryptedTalkUnit.getData());
            return null;
        }
        if(decryptedTalkUnit.getDataType()== DataType.ENCRYPTED_GOT){
            if(TalkUnit.isRequest(sourceTalkUnit.getDataType())){
                sourceTalkUnit.setStata(State.GOT);
                talkUnitHandler.put(sourceTalkUnit);
            }else
                sourceTalkUnit.setStata(State.DONE);
        }else if(decryptedTalkUnit.getDataType()== DataType.ENCRYPTED_RELAYED){
            sourceTalkUnit.setStata(State.RELAYED);
        }
        talkUnitHandler.put(sourceTalkUnit);
        return true;
    }

    @Nullable
    public static byte[] readAndCheck(ChannelHandlerContext ctx, ByteBuf msg, Logger log) {
        ctx.channel().read();

        // Add null check for msg
        if (msg == null) return null;

        // Check if there are enough bytes to read the length (4 bytes)
        if (msg.readableBytes() < 4) {
            log.debug("DEBUG: readableBytes is less than 4: "+ msg.readableBytes());
            return null;
        }
        // Read the length prefix
        int length = msg.readInt();
        // Check if we have enough bytes for the complete message
        if (msg.readableBytes() < length){
            log.debug("DEBUG: readableBytes is {} less than length: {}", msg.readableBytes(),length);
            return null;
        }
        // Create a byte array of the exact size we need
        byte[] bytes = new byte[length];
        // Read the message bytes
        msg.readBytes(bytes);
        log.debug("Got new msg: {}", new String(bytes));
        return bytes;
    }

    public boolean executeTalkUnit( @NotNull TalkUnit parsedTalkUnit) {
        this.executeTalkUnit = parsedTalkUnit;
        Class<?> classType = parsedTalkUnit.getData().getClass();
        switch(parsedTalkUnit.getDataType()){
            case REQUEST -> {
                if(!classType.equals(RequestBody.class))return false;
                return executeRequestBody(parsedTalkUnit, hatHandler,talkIdHandler);
            }
            case REPLY -> {
                if(!classType.equals(ReplyBody.class))return false;
                return executeReplyBody(parsedTalkUnit, hatHandler,talkIdHandler);
            }
            case AFFAIR -> {
                if(!classType.equals(Affair.class))return false;
                return executeAffair(parsedTalkUnit,hatHandler,talkIdHandler);
            }
            case TEXT -> {
                if(!classType.equals(String.class))return false;
                return executeText(parsedTalkUnit,talkIdHandler);
            }
            case BYTES -> {
                if(!classType.equals(byte[].class))return false;
                return executeBytes(parsedTalkUnit, diskHandler,talkIdHandler);
            }
            case HAT -> {
                if(!classType.equals(Hat.class))return false;
                return executeHat(parsedTalkUnit, hatHandler,talkIdHandler);
            }
            case ENCRYPTED_GOT, ENCRYPTED_RELAYED -> {
                return true;
            }
            default -> {
                return false;
            }
        }
        
        // Class<?> classType = this.executeTalkUnit.getData().getClass();
        // if (classType.equals(RequestBody.class))
        //     return executeRequestBody(this.executeTalkUnit,myPriKey, session, hatHandler,talkIdHandler, accountHandler);
        // else if (classType.equals(ReplyBody.class))
        //     return executeReplyBody(this.executeTalkUnit, session, hatHandler,talkIdHandler);
        // else if(classType.equals(Hat.class))
        //     return executeHat(this.executeTalkUnit, hatHandler,talkIdHandler);
        // else if(classType.equals(String.class))
        //     return executeText(this.executeTalkUnit,talkIdHandler);
        // else if(classType.equals(byte[].class))
        //     return executeBytes(this.executeTalkUnit, diskHandler,talkIdHandler);
        // else if(classType.equals(Affair.class))
        //     return executeAffair(this.executeTalkUnit, session, hatHandler,talkIdHandler);
        // return false;
    }

    public void clear(){
        code = null;
        message = null;
        data = null;
        executeTalkUnit = null;
        displayText = null;
        sendTalkUnitList.clear();
    }

    private boolean executeAffair(TalkUnit parsedTalkUnit,HatHandler hatHandler, TalkIdHandler talkIdHandler) {
        Affair affair = (Affair) parsedTalkUnit.getData();
        String title = makeTitle(parsedTalkUnit,talkIdHandler);
        boolean done = false;
        switch(affair.getOp()){
            case SHARE_KEY -> done = saveKey(parsedTalkUnit);
            case SHARE_DATA -> done = saveData(parsedTalkUnit,diskHandler, diskClient, hatHandler);
            case SHARE_HAT ->  done = saveHat(parsedTalkUnit,hatHandler);
            case NOTIFY -> showNotify(parsedTalkUnit, talkIdHandler);
            default -> {
                setCodeMsg(CodeMessage.Code1032NoSuchOperation);
                this.displayText = title+"[AFFAIR-"+affair.getOp()+"] Failed!";
            }
        }
        if(done)this.displayText = title+"[AFFAIR-"+affair.getOp()+"] Done.";
        else this.displayText = title+"[AFFAIR-"+affair.getOp()+"] Failed.";
        return done;
    }

    private boolean executeRequestBody(TalkUnit parsedTalkUnit, HatHandler hatHandler, TalkIdHandler talkIdHandler) {
        RequestBody requestBody = (RequestBody) parsedTalkUnit.getData();
        String title = makeTitle(parsedTalkUnit,talkIdHandler);
        boolean done;
        switch (requestBody.getOp()) {
            case ASK_KEY -> done = shareKey(parsedTalkUnit);
            case ASK_DATA -> done = shareData(parsedTalkUnit);
            case ASK_HAT -> done = shareHat(parsedTalkUnit,hatHandler);
            case PAY -> done = pay(parsedTalkUnit);
            default -> {
                setCodeMsg(CodeMessage.Code1032NoSuchOperation);
                this.displayText = title+"[REQUEST-"+requestBody.getOp()+"] Failed!";
                return false;
            }
        }
        if(done)this.displayText = title+"[REQUEST-"+requestBody.getOp()+"] Done.";
        else this.displayText = title+"[REQUEST-"+requestBody.getOp()+"] Failed.";
        return done;
    }

    private boolean executeReplyBody(TalkUnit parsedTalkUnit, HatHandler hatHandler, TalkIdHandler talkIdHandler) {
        if(talkUnitHandler==null)return false;
        ReplyBody replyBody = (ReplyBody) parsedTalkUnit.getData();
        String requestId = replyBody.getRequestId();
        String roomId = talkUnitHandler.makeRoomId(parsedTalkUnit);
        TalkUnit requestTalkUnit = talkUnitHandler.get(roomId, requestId);
        if(requestTalkUnit==null)return false;

        String title = makeTitle(parsedTalkUnit,talkIdHandler);
        boolean done = false;
        switch (replyBody.getOp()) {
            case SHARE_KEY -> done = saveKey(parsedTalkUnit);
            case SHARE_DATA -> done = saveData(parsedTalkUnit,diskHandler,diskClient,hatHandler);
            case SHARE_HAT ->  done = saveHat(parsedTalkUnit,hatHandler);
            default -> {
                setCodeMsg(CodeMessage.Code1032NoSuchOperation);
                this.displayText = title+"[REPLY-"+ replyBody.getOp()+"] Failed!";
            }
        }
        if(done){
            this.displayText = title+"[REPLY-"+ replyBody.getOp()+"] Done.";
            closeRequestUnit(talkUnitHandler, parsedTalkUnit, replyBody);
        }
        else this.displayText = title+"[REPLY-"+ replyBody.getOp()+"] Failed.";
        return done;
    }

    public boolean pay(TalkUnit executeTalkUnit) {
        if(accountHandler==null)return false;

        if (!(executeTalkUnit.getData() instanceof RequestBody requestBody)) {
            return false;
        }
        sendTalkUnitList = new ArrayList<>();
        String payerFid = executeTalkUnit.getFrom();

        @SuppressWarnings("unchecked")
        Map<String, Long> paymentMap = (Map<String, Long>) requestBody.getData();
        if (paymentMap == null || paymentMap.isEmpty()) {
            return false;
        }

        long sum = 0L;
        for (Map.Entry<String, Long> entry : paymentMap.entrySet()) {
            String recipientFid = entry.getKey();
            Long amount = entry.getValue();

            if (!KeyTools.isGoodFid(recipientFid) || amount <= 0) {
                continue;
            }

            Long newBalance = accountHandler.updateUserBalance(payerFid, -amount);
            if (newBalance == null || newBalance < 0) {
                TalkUnit replyTalkUnit = reply(CodeMessage.Code1004InsufficientBalance,null,null,Op.NOTIFY);
                sendTalkUnitList.add(replyTalkUnit);
                return false;
            }
            sum += amount;

            accountHandler.updateUserBalance(recipientFid, amount);

            String msg = payerFid+" paid "+amount+" to "+recipientFid+".";
            Affair notifyAffair = Affair.makeNotifyAffair(payerFid,recipientFid,msg);
            TalkUnit notifyAffairUnit = new TalkUnit(payerFid,notifyAffair,DataType.ENCRYPTED_AFFAIR,recipientFid,FID);
            sendTalkUnitList.add(notifyAffairUnit);
        }

        String msgToPayer = "You paid "+sum+" to "+paymentMap.keySet()+" according to your request "+ executeTalkUnit.getId()+".";
        
        TalkUnit replyTalkUnit = replySuccess(msgToPayer, Op.NOTIFY);//createReplyUnit(executeTalkUnit.getFrom(), executeTalkUnit.getId(), executeTalkUnit.getTo(), CodeMessage.Code0Success, null, msgToPayer, ((RequestBody)executeTalkUnit.getData()).getNonce());
        sendTalkUnitList.add(replyTalkUnit);
        return true;
    }

    private void setCodeMsg(int code){
        this.code = code;
        this.message = CodeMessage.getMsg(code);
    }

    private boolean executeBytes(TalkUnit parsedTalkUnit, DiskHandler diskHandler, TalkIdHandler talkIdHandler) {
        String title = makeTitle(parsedTalkUnit,talkIdHandler);
        boolean done;
        try {
            byte[] bytes = (byte[]) parsedTalkUnit.getData();
            String did = Hex.toHex(Hash.sha256x2(bytes));
            log.info("Received byte array unit from {}, DID: {}", parsedTalkUnit.getFrom(), did);
            diskHandler.put(bytes);
            code = CodeMessage.Code0Success;
            message = CodeMessage.Msg0Success;
            this.displayText = title+"DID#"+did;
            executeTalkUnit.setStata(State.DONE);
            done = true;
        } catch (Exception e) {
            log.error("Error handling byte array unit: {}", e.getMessage());
            code = CodeMessage.Code3010FailedToHandleData;
            message = e.getMessage();
            this.displayText = title+"DID. Failed! "+e.getMessage();
            done = false;
        }
        return done;
    }

    private boolean executeText(TalkUnit parsedTalkUnit, TalkIdHandler talkIdHandler) {
        String title = makeTitle(parsedTalkUnit,talkIdHandler);
        boolean done;
        try {
            String text = (String) parsedTalkUnit.getData();
            log.info("Received text message from {}: {}", parsedTalkUnit.getFrom(), text);
            code = CodeMessage.Code0Success;
            message = CodeMessage.Msg0Success;
            this.displayText = title+text;
            executeTalkUnit.setStata(State.DONE);
            done = true;
        } catch (Exception e) {
            log.error("Error handling text message: {}", e.getMessage());
            code = CodeMessage.Code3010FailedToHandleData;
            message = e.getMessage();
            this.displayText = title+"TEXT. Failed! "+e.getMessage();
            done = false;
        }
        return done;
    }

    @Nullable
    private String makeTitle(TalkUnit talkUnit, TalkIdHandler talkIdHandler) {
        StringBuilder sb = new StringBuilder();
        sb.append("[")
                .append(talkUnit.getFrom());

        switch (talkUnit.getIdType()) {
            case FID -> {}
            case TEAM -> {
                sb.append("@")
                        .append("Team#");
                if(talkIdHandler!=null)
                    addShowName(talkUnit, talkIdHandler, sb);
                else sb.append(talkUnit.getTo());
            }
            case GROUP -> {
                sb.append("@")
                        .append("Group#");
                addShowName(talkUnit, talkIdHandler, sb);
            }
            default -> {
                return null;
            }
        }
        sb.append("]: ");
        return sb.toString();
    }
    private void addShowName(TalkUnit talkUnit, TalkIdHandler talkIdHandler, StringBuilder sb) {
        if(talkIdHandler!=null){
            TalkIdInfo talkIdInfo = talkIdHandler.get(talkUnit.getTo());
            String showName = talkIdInfo.getShowName();
            if(showName!=null) {
                sb.append(showName);
                return;
            }
        }
        sb.append(talkUnit.getTo());
    }
    private boolean executeHat(TalkUnit parsedTalkUnit, HatHandler hatHandler, TalkIdHandler talkIdHandler) {
        String title = makeTitle(parsedTalkUnit,talkIdHandler);
        boolean done;
        try {
            Hat hat = (Hat) parsedTalkUnit.getData();
            log.info("Received hat from {}, hat ID: {}", parsedTalkUnit.getFrom(), hat.getId());
            // Save hat using hatHandler
            hatHandler.putHat(hat);
            code = CodeMessage.Code0Success;
            message = CodeMessage.Msg0Success;
            this.displayText = title+"HAT-"+hat.getId();
            executeTalkUnit.setStata(State.DONE);
            done = true;
        } catch (Exception e) {
            log.error("Error handling hat: {}", e.getMessage());
            code = CodeMessage.Code3010FailedToHandleData;
            message = e.getMessage();
            this.displayText = title+"HAT. Failed!";
            done = false;
        }
        return done;
    }

    private boolean shareKey(TalkUnit parsedTalkUnit) {
        if(parsedTalkUnit==null)return false;

        try {
            // Create reply body with session data
            if(!(parsedTalkUnit.getData() instanceof RequestBody requestBody))return false;
            if(!(requestBody.getOp().equals(Op.ASK_KEY)&& requestBody.getData() instanceof String id))return false;

            FcSession shareSession = null;

            if(id.length()==34){
                // Ask for the key of a FID
                if(!parsedTalkUnit.getTo().equals(parsedTalkUnit.getFrom()) && !id.equals(parsedTalkUnit.getTo()))return false;
                shareSession = sessionHandler.getSessionByUserId(parsedTalkUnit.getFrom());
                if(shareSession==null){
                    shareSession = sessionHandler.addNewSession(id,parsedTalkUnit.getFromSession().getPubKey());
                }
                shareSession.setUserId(id);
                TalkUnit replyTalkUnit = replySuccess(shareSession.toJson(), Op.SHARE_KEY);
                sendTalkUnitList.add(replyTalkUnit);
                return true; 
            }else if(id.length()==32) {
                // Ask for the key of a team or group
                if (!isMemberOfTeamOrGroup(parsedTalkUnit.getFrom(), id,groupHandler,teamHandler,apipClient)) return false;
                shareSession = sessionHandler.getSessionByUserId(id);
                if(shareSession==null){
                    if (!isMemberOfTeamOrGroup(parsedTalkUnit.getTo(), id, groupHandler, teamHandler, apipClient)) return false;
                    shareSession = sessionHandler.addNewSession(id,parsedTalkUnit.getFromSession().getPubKey());
                }
                TalkUnit replyTalkUnit = replySuccess(shareSession.toJson(), Op.SHARE_KEY);
                sendTalkUnitList.add(replyTalkUnit);
                return true;
            }else if(id.length()==12){
                // Ask self for a session
                if (!parsedTalkUnit.getFrom().equals(parsedTalkUnit.getTo())) return false;
                shareSession = sessionHandler.getSessionByName(id);
                if(shareSession==null){
                    TalkUnit replyTalkUnit = replyOtherError("Session not found",null,Op.NOTIFY);
                    sendTalkUnitList.add(replyTalkUnit);
                    return false;
                }
                TalkUnit replyTalkUnit = replySuccess(shareSession.toJson(), Op.SHARE_KEY);
                sendTalkUnitList.add(replyTalkUnit);
                return true;
            }else {
                TalkUnit replyTalkUnit = replyOtherError("Invalid ID",null,Op.NOTIFY);
                sendTalkUnitList.add(replyTalkUnit);
                return false;
            }
        } catch (Exception e) {
            log.error("Error sharing key: {}", e.getMessage());
            TalkUnit replyTalkUnit = replyOtherError(e.getMessage(),null,Op.NOTIFY);
            sendTalkUnitList.add(replyTalkUnit);
            return false;
        }
    }

    private static boolean isMemberOfTeamOrGroup(String fid, String id, GroupHandler groupHandler, TeamHandler teamHandler, ApipClient apipClient) {
        boolean isMember = groupHandler.isMemberOf(fid, id,apipClient);
        if(!isMember)
            isMember = teamHandler.isMemberOf(fid, id,apipClient);
        return isMember;
    }

    private boolean shareData(TalkUnit parsedTalkUnit) {
        if(diskHandler==null)return false;
        try {
            RequestBody requestBody = (RequestBody) parsedTalkUnit.getData();
            String did = (String) requestBody.getData();
            
            byte[] fileData = diskHandler.getBytes(did);
            if(fileData == null){
                TalkUnit replyTalkUnit = reply(CodeMessage.Code1011DataNotFound,CodeMessage.Msg1011DataNotFound,null,Op.NOTIFY);
                sendTalkUnitList.add(replyTalkUnit);
                return false;
            }else{
                TalkUnit replyTalkUnit = replySuccess(fileData, Op.SHARE_DATA);
                sendTalkUnitList.add(replyTalkUnit);
                return true;
            }
        } catch (Exception e) {
            log.error("Error sharing data: {}", e.getMessage());
            TalkUnit replyTalkUnit = replyOtherError(e.getMessage(),null,Op.NOTIFY);
            sendTalkUnitList.add(replyTalkUnit);
            return false;
        }
    }

    private boolean shareHat(TalkUnit parsedTalkUnit, HatHandler hatHandler) {
        try {
            RequestBody requestBody = (RequestBody) parsedTalkUnit.getData();
            String did = (String) requestBody.getData();
            
            Hat hat = hatHandler.getHatByDid(did);
            if (hat == null) {
                TalkUnit replyTalkUnit = reply(CodeMessage.Code1011DataNotFound,CodeMessage.Msg1011DataNotFound,null,Op.NOTIFY);
                sendTalkUnitList.add(replyTalkUnit);
                return false;
            }else{
                TalkUnit replyTalkUnit = replySuccess(hat, Op.SHARE_HAT);
                sendTalkUnitList.add(replyTalkUnit);
                return true;
            }
        } catch (Exception e) {
            log.error("Error sharing hat: {}", e.getMessage());
            TalkUnit replyTalkUnit = replyOtherError(e.getMessage(),null,Op.NOTIFY);
            sendTalkUnitList.add(replyTalkUnit);
            return false;
        }
    }

    private boolean saveKey(@Nullable TalkUnit replySessionTalkUnit) {
        if(replySessionTalkUnit==null)return false;
        try {
            FcSession newSession;
            try {
                ReplyBody replyBody = (ReplyBody) replySessionTalkUnit.getData();
                String sessionJson = (String) replyBody.getData();
                this.payToForBytes = new HashMap<>();
                this.payToForBytes.put(replySessionTalkUnit.getFrom(),sessionJson.getBytes().length);
                // Create new session from JSON
                newSession = FcSession.fromJson(sessionJson, FcSession.class);
                if (newSession == null){
                    return false;
                }
            }catch (Exception e) {
                return false;
            }
            // Save both the session name->session and id->name mappings
            sessionHandler.putSession(newSession);
            sessionHandler.putUserIdSessionId(newSession.getUserId(), newSession.getId());
            
            log.info("Saved session key for: {} with name: {}", replySessionTalkUnit.getFrom(), newSession.getId());
            code = CodeMessage.Code0Success;
            message = CodeMessage.Msg0Success;
            executeTalkUnit.setStata(State.DONE);
            return true;
        } catch (Exception e) {
            log.error("Error saving key: {}", e.getMessage());
            code = CodeMessage.Code3010FailedToHandleData;
            message = e.getMessage();
            return false;
        }
    }

    // private boolean saveData(TalkUnit parsedTalkUnit, DiskHandler diskHandler, DiskClient diskClient) {
    //     if(parsedTalkUnit==null)return false;
    //     if(diskHandler ==null)return false;
    //     try {
    //         ReplyBody replyBody = (ReplyBody) parsedTalkUnit.getData();
    //         byte[] dataBytes = (byte[]) replyBody.getData();
    //         String did = diskHandler.put(dataBytes);

            
    //         if (did != null) {
    //             code = CodeMessage.Code0Success;
    //             message = CodeMessage.Msg0Success;
    //             executeTalkUnit.setStata(State.DONE);
    //             this.payToForBytes = new HashMap<>();
    //             this.payToForBytes.put(parsedTalkUnit.getFrom(), dataBytes.length);
    //             log.info("Saved data from {}, DID: {}", parsedTalkUnit.getFrom(), did);
    //             return true;
    //         } else {
    //             throw new Exception("Failed to save data to disk");
    //         }
    //     } catch (Exception e) {
    //         log.error("Error saving data: {}", e.getMessage());
    //         code = CodeMessage.Code3010FailedToHandleData;
    //         message = e.getMessage();
    //         return false;
    //     }
    // }

    private boolean saveData(TalkUnit parsedTalkUnit, DiskHandler diskHandler, DiskClient diskClient, HatHandler hatHandler) {
        if (parsedTalkUnit == null || diskHandler == null) return false;
        String did = null;
        try {
            ReplyBody replyBody = (ReplyBody) parsedTalkUnit.getData();
            
            
            byte[] dataBytes = (byte[]) replyBody.getData();
            
            // 1. Save raw data to diskHandler
            String dataFilePath = diskHandler.put(dataBytes);
            if (dataFilePath == null) {
                code = CodeMessage.Code3010FailedToHandleData;
                message = "Failed to save data to disk handler";
                return false;
            }
            did = dataFilePath.substring(dataFilePath.lastIndexOf("/") + 1);
    
            // If diskClient exists, handle encryption and Hat creation
            if (diskClient != null) {
                DiskClient.encryptAndSaveData(dataFilePath, diskClient, dataBytes, hatHandler);
            }
    
            code = CodeMessage.Code0Success;
            message = CodeMessage.Msg0Success;
            executeTalkUnit.setStata(State.DONE);
            this.payToForBytes = new HashMap<>();
            this.payToForBytes.put(parsedTalkUnit.getFrom(), dataBytes.length);
            log.info("Saved data from {}, DID: {}", parsedTalkUnit.getFrom(), did);
            return true;
    
        } catch (Exception e) {
            log.error("Error saving data: {}", e.getMessage());
            code = CodeMessage.Code3010FailedToHandleData;
            message = e.getMessage();
            return false;
        }
    }

    private boolean saveHat(TalkUnit parsedTalkUnit, HatHandler hatHandler) {
        try {
            ReplyBody replyBody = (ReplyBody) parsedTalkUnit.getData();
            Hat hat = (Hat) replyBody.getData();
            // Save hat using hatHandler
            hatHandler.putHat(hat);
            
            code = CodeMessage.Code0Success;
            message = CodeMessage.Msg0Success;
            this.payToForBytes = new HashMap<>();
            this.payToForBytes.put(parsedTalkUnit.getFrom(), hat.toBytes().length);
            log.info("Saved hat ID from {}: {}",  parsedTalkUnit.getFrom(),hat.getId());

            return true;
        } catch (Exception e) {
            log.error("Error saving hat: {}", e.getMessage());
            code = CodeMessage.Code3010FailedToHandleData;
            message = e.getMessage();
            executeTalkUnit.setStata(State.DONE);
            return false;
        }
    }

    private void showNotify(TalkUnit parsedTalkUnit, TalkIdHandler talkIdHandler) {
        if (!(parsedTalkUnit.getData() instanceof Affair affair)) {
            setCodeMsg(CodeMessage.Code3010FailedToHandleData);
            return;
        }

        String title = makeTitle(parsedTalkUnit, talkIdHandler);
        
        try {
            // Simply display the notification message from the affair
            String notifyMessage = affair.getData() != null ? affair.getData().toString() : "No message";
            log.info("Received notification from {}, message: {}", parsedTalkUnit.getFrom(), notifyMessage);
            this.displayText = title + notifyMessage;
            setCodeMsg(CodeMessage.Code0Success);
            executeTalkUnit.setStata(State.DONE);
        } catch (Exception e) {
            log.error("Error processing notification: {}", e.getMessage());
            setCodeMsg(CodeMessage.Code3010FailedToHandleData);
            this.displayText = title + "NOTIFY. Failed! " + e.getMessage();
        }
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    // public byte[] getReplyBytes() {
    //     return replyBytes;
    // }

    // public void setReplyBytes(byte[] replyBytes) {
    //     this.replyBytes = replyBytes;
    // }

    public String getDisplayText() {
        return displayText;
    }

    public void setDisplayText(String displayText) {
        this.displayText = displayText;
    }

    // public TalkUnit getReplyTalkUnit() {
    //     return replyTalkUnit;
    // }

    // public void setReplyTalkUnit(TalkUnit replyTalkUnit) {
    //     this.replyTalkUnit = replyTalkUnit;
    // }

    public TalkUnit getExecuteTalkUnit() {
        return executeTalkUnit;
    }

    public void setExecuteTalkUnit(TalkUnit executeTalkUnit) {
        this.executeTalkUnit = executeTalkUnit;
    }

    public Map<String, Integer> getPayToForBytes() {
        return payToForBytes;
    }

    public void setPayToForBytes(Map<String, Integer> payToForBytes) {
        this.payToForBytes = payToForBytes;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public List<TalkUnit> getSendTalkUnitList() {
        return sendTalkUnitList;
    }

    public void setSendTalkUnitList(List<TalkUnit> sendTalkUnitList) {
        this.sendTalkUnitList = sendTalkUnitList;
    }

    public DiskClient getDiskClient() {
        return diskClient;
    }

    public TalkServer getTalkServer() {
        return talkServer;
    }


}
