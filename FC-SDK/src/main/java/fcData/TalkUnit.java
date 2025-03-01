package fcData;

import apip.apipData.RequestBody;
import ch.qos.logback.classic.Logger;
import clients.ApipClient;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.google.gson.Gson;
import constants.CodeMessage;
import crypto.*;
import handlers.ContactHandler;
import handlers.SessionHandler;
import handlers.TalkIdHandler;
import org.slf4j.LoggerFactory;
import tools.*;
import org.bitcoinj.core.ECKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static fcData.TalkUnit.DataType.*;
import static fcData.TalkUnit.IdType.FID;

public class TalkUnit extends FcObject implements Comparable<TalkUnit> {

    public final static Logger log = (Logger) LoggerFactory.getLogger(TalkUnit.class);
    private transient Integer code;
    private transient String message;
    private transient String id; //for database, time formatted + nonce
    private transient BytesTools.ByteArrayAsKey idBytes;
    private transient EncryptType unitEncryptType;
    private transient EncryptType dataEncryptType;
    private transient FcSession bySession;
    private transient FcSession fromSession;

    //fields in json
    private String from; //Massage creator
    private String by; //Massage deliver

    // fields in bytes
    private IdType toType;
    private String to;
    private Long time;
    private Integer nonce;
    private DataType dataType;
    private State state;
    //Body
    private Object data;


    public TalkUnit() {
        this.nonce = Math.abs(BytesTools.bytesToIntBE(BytesTools.getRandomBytes(4)));
        this.time = System.currentTimeMillis();
        makeId();
        this.state = State.NEW;
    }

    public TalkUnit(String fromFid, Object data, @NotNull DataType dataType, @Nullable String to, @NotNull IdType toType) {
        this.nonce = Math.abs(BytesTools.bytesToIntBE(BytesTools.getRandomBytes(4)));
        this.time = System.currentTimeMillis();
        makeId();
        this.from = fromFid;
        this.toType = toType;
        this.to = to;
        this.dataType = dataType;
        this.data = data;
        this.state = State.NEW;
    }

    public static TalkUnit makeTalkUnit( TalkUnit rawTalkUnit, byte[] sessionKey, byte[] priKey, String pubKey) {
        return makeTalkUnit(rawTalkUnit.getFrom(), rawTalkUnit.getData(), rawTalkUnit.getDataType(), rawTalkUnit.getTo(), rawTalkUnit.getToType(),rawTalkUnit.getTime(),rawTalkUnit.getNonce(), sessionKey, priKey, pubKey, rawTalkUnit.getDataEncryptType());
    }

    public static boolean isRequest(DataType dataType){
        return switch( dataType) {
            case REQUEST, ENCRYPTED_REQUEST, ENCRYPTED_SIGNED_REQUEST, ENCRYPTED_ID_SIGNED_REQUEST, ID_SIGNED_REQUEST ->
                    true;
            default -> false;
        };
    }

    public static TalkUnit createReplyUnit(TalkUnit requestTalkUnit, Integer code, String message, Object data, Op op) {
        if(requestTalkUnit==null) return null;

        ReplyBody replyBody = new ReplyBody();

        replyBody.setRequestId(requestTalkUnit.getId());
        if (requestTalkUnit.getDataType() == REQUEST && requestTalkUnit.getData() instanceof RequestBody requestBody) {
            replyBody.setNonce(requestBody.getNonce());
        }

        replyBody.setOp(op);
        replyBody.setCode(code!=null?code:CodeMessage.Code0Success);
        replyBody.setMessage(message != null ? message : code!=null?CodeMessage.getMsg(code):CodeMessage.Msg0Success);
        if (data != null) replyBody.setData(data);

        TalkUnit replyTalkUnit = new TalkUnit(requestTalkUnit.getTo(), replyBody, ENCRYPTED_REPLY, requestTalkUnit.getFrom(), FID);
        replyTalkUnit.setUnitEncryptType(requestTalkUnit.getUnitEncryptType());
        replyTalkUnit.setDataEncryptType(requestTalkUnit.getDataEncryptType());

        return replyTalkUnit;
    }

    @NotNull
    public static TalkUnit createNotifyUnit(String senderFid, String recipientFid, String msg) {
        Affair affair = Affair.makeNotifyAffair(senderFid, recipientFid, msg);
        return new TalkUnit(senderFid,affair, ENCRYPTED_AFFAIR, recipientFid, FID);
    }

    public boolean makeTalkUnit( byte[] sessionKey, byte[] priKey, String pubKey) {
        TalkUnit talkUnit = makeTalkUnit(from, data, dataType, to, toType,this.time,this.nonce, sessionKey, priKey, pubKey, dataEncryptType);
        if(talkUnit==null)return false;
        this.data = talkUnit.getData();
        return true;
    }

    private static TalkUnit makeTalkUnit(String myFid, Object rawData, DataType dataType,
                                        String toId, IdType idType,Long time,Integer nonce, byte[] sessionKey, byte[] priKey, String pubKey, @Nullable EncryptType dataEncryptType) {
        TalkUnit talkUnit = new TalkUnit();
        talkUnit.setDataEncryptType(dataEncryptType);
        talkUnit.setTo(toId);
        talkUnit.setIdType(idType);
        talkUnit.setData(rawData);
        talkUnit.setDataType(dataType);
        if(time!=null)talkUnit.setTime(time);
        if(nonce!=null){
            talkUnit.setNonce(nonce);
            talkUnit.makeId();
        }
        talkUnit.setNonce(nonce);

        checkSelfDataEncrypt(talkUnit);

        switch (dataType) {
            case TEXT, GOT, RELAYED, BYTES, REQUEST, REPLY, HAT, AFFAIR ->
                    talkUnit.setData(rawData);
            case SIGNED_TEXT, SIGNED_BYTES, SIGNED_REQUEST, SIGNED_HAT, SIGNED_REPLY, SIGNED_AFFAIR -> {
                if(priKey==null && sessionKey==null){
                    log.debug("Miss key.");
                    return null;
                }
                talkUnit.signData(dataType, priKey, sessionKey, talkUnit);
            }
            case ENCRYPTED_TEXT,ENCRYPTED_GOT, ENCRYPTED_RELAYED, ENCRYPTED_BYTES, ENCRYPTED_REQUEST, ENCRYPTED_HAT, ENCRYPTED_REPLY, ENCRYPTED_AFFAIR -> {
                if((priKey==null ||pubKey==null)  && sessionKey==null){
                    log.debug("Miss key.");
                    return null;
                }
                CryptoDataByte cryptoDataByte = talkUnit.encryptData(sessionKey, pubKey, priKey);
                talkUnit.setData(cryptoDataByte);

            }

            case ENCRYPTED_SIGNED_TEXT,ENCRYPTED_SIGNED_GOT,ENCRYPTED_SIGNED_RELAYED, ENCRYPTED_SIGNED_BYTES, ENCRYPTED_SIGNED_HAT,
                 ENCRYPTED_SIGNED_REPLY, ENCRYPTED_SIGNED_REQUEST, ENCRYPTED_SIGNED_AFFAIR -> {
                if((priKey==null ||pubKey==null)  && sessionKey==null){
                    log.debug("Miss key.");
                    return null;
                }
                DataType newDataType;
                switch (dataType){
                    case ENCRYPTED_SIGNED_TEXT -> newDataType = SIGNED_TEXT;
                    case ENCRYPTED_SIGNED_GOT -> newDataType = SIGNED_GOT;
                    case ENCRYPTED_SIGNED_RELAYED -> newDataType = SIGNED_RELAYED;
                    case ENCRYPTED_SIGNED_BYTES -> newDataType = SIGNED_BYTES;
                    case ENCRYPTED_SIGNED_REQUEST -> newDataType = SIGNED_REQUEST;
                    case ENCRYPTED_SIGNED_REPLY -> newDataType = SIGNED_REPLY;
                    case ENCRYPTED_SIGNED_HAT -> newDataType = SIGNED_HAT;
                    case ENCRYPTED_SIGNED_AFFAIR -> newDataType = SIGNED_AFFAIR;
                    default -> {
                        return null;
                    }
                }
                talkUnit.signData(newDataType, priKey, sessionKey, talkUnit);
                talkUnit.setDataEncryptType(dataEncryptType);
                talkUnit.encryptData(sessionKey, pubKey, priKey);
                if(talkUnit.getData()==null)return null;
            }
            case ID_SIGNED_TEXT, ID_SIGNED_BYTES, ID_SIGNED_HAT,
                    ID_SIGNED_REPLY, ID_SIGNED_REQUEST, ID_SIGNED_AFFAIR -> {
                if(priKey==null && sessionKey==null){
                    log.debug("Miss key.");
                    return null;
                }
                talkUnit.signTalkUnitId(myFid, dataType, priKey);
                if (talkUnit.getData() == null) return null;
            }
            case ENCRYPTED_ID_SIGNED_TEXT, ENCRYPTED_ID_SIGNED_BYTES, ENCRYPTED_ID_SIGNED_HAT,
                 ENCRYPTED_ID_SIGNED_REPLY, ENCRYPTED_ID_SIGNED_REQUEST, ENCRYPTED_ID_SIGNED_AFFAIR -> {
                if((priKey==null ||pubKey==null)  && sessionKey==null){
                    log.debug("Miss key.");
                    return null;
                }
                DataType newDataType;
                switch (dataType){
                    case ENCRYPTED_ID_SIGNED_TEXT -> newDataType = ID_SIGNED_TEXT;
                    case ENCRYPTED_ID_SIGNED_BYTES -> newDataType = ID_SIGNED_BYTES;
                    case ENCRYPTED_ID_SIGNED_REQUEST -> newDataType = ID_SIGNED_REQUEST;
                    case ENCRYPTED_ID_SIGNED_REPLY -> newDataType = ID_SIGNED_REPLY;
                    case ENCRYPTED_ID_SIGNED_HAT -> newDataType = ID_SIGNED_HAT;
                    case ENCRYPTED_ID_SIGNED_AFFAIR -> newDataType = ID_SIGNED_AFFAIR;
                    default -> {
                        return null;
                    }
                }
                talkUnit.signTalkUnitId(myFid,newDataType, priKey);
                if(talkUnit.getData()==null)return null;
                talkUnit.setDataEncryptType(dataEncryptType);
                talkUnit.encryptData(sessionKey, pubKey, priKey);
                if (talkUnit.getData()==null)return null;
            }
            default -> {
                log.debug("No such dataType:{}",dataType);
                return null;
            }
        }
        talkUnit.setStata(State.ENCRYPTED);
        return talkUnit;
    }

    public static boolean checkSelfDataEncrypt(TalkUnit talkUnit){
        boolean isSelf = talkUnit.getTo().equals(talkUnit.getFrom());
        if(isSelf && talkUnit.getDataEncryptType().equals(EncryptType.AsyTwoWay)){
            talkUnit.setDataEncryptType(EncryptType.AsyOneWay);
            convertToSignedDataType(talkUnit);
        }
        return isSelf;
    }

    private static void convertToSignedDataType(TalkUnit talkUnit) {
        switch (talkUnit.getDataType()){
            case ENCRYPTED_GOT -> talkUnit.setDataType(ENCRYPTED_SIGNED_GOT);
            case ENCRYPTED_RELAYED -> talkUnit.setDataType(ENCRYPTED_SIGNED_RELAYED);
            case ENCRYPTED_BYTES -> talkUnit.setDataType(ENCRYPTED_SIGNED_BYTES);
            case ENCRYPTED_REQUEST -> talkUnit.setDataType(ENCRYPTED_SIGNED_REQUEST);
            case ENCRYPTED_REPLY -> talkUnit.setDataType(ENCRYPTED_SIGNED_REPLY);
            case ENCRYPTED_HAT -> talkUnit.setDataType(ENCRYPTED_SIGNED_HAT);
            case ENCRYPTED_AFFAIR -> talkUnit.setDataType(ENCRYPTED_SIGNED_AFFAIR);
            default -> {}
        }
    }

    public static boolean checkSelfUnitEncrypt(TalkUnit talkUnit){
        boolean isSelf = talkUnit.getTo().equals(talkUnit.getBy());
        if(isSelf && talkUnit.getUnitEncryptType().equals(EncryptType.AsyTwoWay))
            talkUnit.setUnitEncryptType(EncryptType.AsyOneWay);
        return isSelf;
    }
//
//    public static TalkUnit makeNotifyAffairUnit(String fromFid, String recipientFid, String message,
//                                                FcSession session, byte[] myPriKey) {
//        // Create payment notice affair
//        Affair affair = Affair.makeNotifyAffair(fromFid, recipientFid, message);
//
//        TalkUnit talkUnit = new TalkUnit(
//                fromFid,
//                affair,
//                ENCRYPTED_AFFAIR,
//                recipientFid,
//                IdType.FID
//        );
//        byte[] sessionKey = session.getKeyBytes();
//        String pubKey = session.getPubKey();
//        talkUnit.makeTalkUnit(sessionKey, myPriKey, pubKey);
//        // Create and return affair unit for recipient notification
//        return talkUnit;
//    }

    public CryptoDataByte encryptData(byte[] sessionKey, String pubKey, byte[] priKey) {
        byte[] bytes;
        if (data instanceof byte[]) {
            bytes = (byte[]) data;
        } else if (data instanceof String) {
            bytes = ((String) data).getBytes();
        } else if (data instanceof RequestBody) {
            bytes = ((RequestBody) data).toBytes();
        } else if (data instanceof ReplyBody) {
            bytes = ((ReplyBody) data).toBytes();
            log.debug("Made replyBody:{}",new String(bytes));
        } else if (data instanceof Hat) {
            bytes = ((Hat) data).toBytes();
        } else if(data instanceof Signature){
            bytes = ((Signature) data).toBundle();
        } else if(data instanceof IdSignature){
            bytes = ((IdSignature) data).toBytes();
        } else {
            return null;
        }
        CryptoDataByte cryptoDataByte = encryptData(bytes, this.dataEncryptType, sessionKey,priKey,pubKey);
        if(cryptoDataByte==null){
            setCodeMessage(CodeMessage.Code4001FailedToEncrypt);
            return null;
        }
        if(cryptoDataByte.getCode()!=0){
            code = cryptoDataByte.getCode();
            message = cryptoDataByte.getMessage();
            return null;
        }
        data = cryptoDataByte;
        return cryptoDataByte;
    }
    public void setCodeMessage(int code){
        this.code = code;
        this.message = CodeMessage.getMsg(code);
    }
    public static TalkUnit parseTalkUnitData(final TalkUnit talkUnit, byte[] priKey, SessionHandler sessionHandler) {
        if (talkUnit.getData()== null) {
            log.debug("Data is null in parseTalkUnitData.");
            return null;
        }
        TalkUnit parsedUnit = clone(talkUnit);
        switch (parsedUnit.getDataType()) {
            case TEXT, BYTES, REQUEST, REPLY, HAT, AFFAIR,GOT,RELAYED -> {
            }
            case SIGNED_TEXT, SIGNED_BYTES, SIGNED_REQUEST, SIGNED_HAT, SIGNED_REPLY, SIGNED_AFFAIR,SIGNED_GOT,SIGNED_RELAYED -> {
                if(!(parsedUnit.getData() instanceof Signature signature)){
                    return null;
                }
                if (!signature.verify()) return null;
                parsedUnit.setFrom(signature.getFid());
                switch (parsedUnit.getDataType()) {
                    case SIGNED_TEXT -> {
                        parsedUnit.setData(signature.getMsg());
                        parsedUnit.setDataType(DataType.TEXT);
                    }
                    case SIGNED_BYTES -> {
                        parsedUnit.setData(signature.getMsg().getBytes());
                        parsedUnit.setDataType(DataType.BYTES);
                    }
                    case SIGNED_REQUEST -> {
                        parsedUnit.setData(RequestBody.fromJson(signature.getMsg(), RequestBody.class));
                        parsedUnit.setDataType(DataType.REQUEST);
                    }
                    case SIGNED_REPLY -> {
                        parsedUnit.setData(ReplyBody.fromJson(signature.getMsg(), ReplyBody.class));
                        parsedUnit.setDataType(DataType.REPLY);
                    }
                    case SIGNED_HAT -> {
                        parsedUnit.setData(Hat.fromJson(signature.getMsg(), Hat.class));
                        parsedUnit.setDataType(DataType.HAT);
                    }
                    case SIGNED_AFFAIR -> {
                        parsedUnit.setData(Affair.fromJson(signature.getMsg(), Affair.class));
                        parsedUnit.setDataType(DataType.AFFAIR);
                    }
                    case SIGNED_GOT -> {
                        parsedUnit.setData(signature.getMsg());
                        parsedUnit.setDataType(DataType.GOT);
                    }
                    case SIGNED_RELAYED -> {
                        parsedUnit.setData(signature.getMsg());
                        parsedUnit.setDataType(DataType.RELAYED);
                    }
                    default -> {
                        return null;
                    }
                }
            }

            case ENCRYPTED_TEXT,ENCRYPTED_GOT,ENCRYPTED_RELAYED, ENCRYPTED_BYTES, ENCRYPTED_REQUEST, ENCRYPTED_HAT, ENCRYPTED_REPLY, ENCRYPTED_AFFAIR -> {
                if(!(parsedUnit.getData() instanceof CryptoDataByte cryptoDataByte)) {
                    log.debug("The data is not cryptoDataByte:");
                    return null;
                }
                byte[] decryptedData = decryptData(parsedUnit, sessionHandler, priKey);
                if (decryptedData == null) {
                    log.debug("Failed to decrypt data:{}",cryptoDataByte.toNiceJson());
                    return null;
                }
                log.debug("Decrypted data:{}",new String(decryptedData));
                switch (parsedUnit.getDataType()) {
                    case ENCRYPTED_TEXT -> {
                        parsedUnit.setData(new String(decryptedData));
                        parsedUnit.setDataType(DataType.TEXT);
                    }
                    case ENCRYPTED_GOT -> {
                        parsedUnit.setData(decryptedData);
                        parsedUnit.setDataType(DataType.GOT);
                    }
                    case ENCRYPTED_RELAYED -> {
                        parsedUnit.setData(decryptedData);
                        parsedUnit.setDataType(DataType.RELAYED);
                    }
                    case ENCRYPTED_BYTES -> {
                        parsedUnit.setData(decryptedData);
                        parsedUnit.setDataType(DataType.BYTES);
                    }
                    case ENCRYPTED_REQUEST -> {
                        parsedUnit.setData(RequestBody.fromBytes(decryptedData, RequestBody.class));
                        parsedUnit.setDataType(DataType.REQUEST);
                    }
                    case ENCRYPTED_REPLY -> {
                        log.debug("Check Encrypted Reply...");
                        System.out.println("Decrypted data hex:"+Hex.toHex(decryptedData));
                        System.out.println("Decrypted data String:"+new String(decryptedData));
                        ReplyBody replyBody = ReplyBody.fromBytes(decryptedData, ReplyBody.class);
                        parsedUnit.setData(replyBody);
                        parsedUnit.setDataType(DataType.REPLY);
                    }
                    case ENCRYPTED_HAT -> {
                        parsedUnit.setData(Hat.fromBytes(decryptedData, Hat.class));
                        parsedUnit.setDataType(DataType.HAT);
                    }
                    case ENCRYPTED_AFFAIR -> {
                        parsedUnit.setData(Affair.fromBytes(decryptedData, Affair.class));
                        parsedUnit.setDataType(DataType.AFFAIR);
                    }
                    default -> {
                        return null;
                    }
                }
            }

            case ID_SIGNED_TEXT, ID_SIGNED_BYTES, ID_SIGNED_HAT, ID_SIGNED_REPLY, ID_SIGNED_REQUEST, ID_SIGNED_AFFAIR -> {
                if(!(parsedUnit.getData() instanceof IdSignature idSignature)){
                    return null;
                }
                if (!idSignature.verify(parsedUnit.getTo())) return null;
                parsedUnit.setFrom(idSignature.getFid());
                switch (parsedUnit.getDataType()) {
                    case ID_SIGNED_TEXT -> {
                        parsedUnit.setData(idSignature.getData());
                        parsedUnit.setDataType(DataType.TEXT);
                    }
                    case ID_SIGNED_BYTES -> {
                        parsedUnit.setData(idSignature.getData().getBytes());
                        parsedUnit.setDataType(DataType.BYTES);
                    }
                    case ID_SIGNED_REQUEST -> {
                        parsedUnit.setData(RequestBody.fromJson(idSignature.getData(), RequestBody.class));
                        parsedUnit.setDataType(DataType.REQUEST);
                    }
                    case ID_SIGNED_REPLY -> {
                        parsedUnit.setData(ReplyBody.fromJson(idSignature.getData(), ReplyBody.class));
                        parsedUnit.setDataType(DataType.REPLY);
                    }
                    case ID_SIGNED_HAT -> {
                        parsedUnit.setData(Hat.fromJson(idSignature.getData(), Hat.class));
                        parsedUnit.setDataType(DataType.HAT);
                    }
                    case ID_SIGNED_AFFAIR -> {
                        parsedUnit.setData(Affair.fromJson(idSignature.getData(), Affair.class));
                        parsedUnit.setDataType(DataType.AFFAIR);
                    }
                    default -> {
                        return null;
                    }
                }
            }

            case ENCRYPTED_SIGNED_TEXT,ENCRYPTED_SIGNED_GOT,ENCRYPTED_SIGNED_RELAYED, ENCRYPTED_SIGNED_BYTES, ENCRYPTED_SIGNED_HAT,
                 ENCRYPTED_SIGNED_REPLY, ENCRYPTED_SIGNED_REQUEST, ENCRYPTED_SIGNED_AFFAIR -> {
                if(!(parsedUnit.getData() instanceof CryptoDataByte cryptoDataByte)) {
                    return null;
                }
                byte[] decryptedData = decryptData( parsedUnit, sessionHandler, priKey);
                if (decryptedData == null) return null;

                Signature signature = Signature.fromBundle(decryptedData);
                if (!signature.verify()) return null;
                parsedUnit.setFrom(signature.getFid());
                switch (parsedUnit.getDataType()) {
                    case ENCRYPTED_SIGNED_TEXT -> {
                        parsedUnit.setData(signature.getMsg());
                        parsedUnit.setDataType(DataType.TEXT);
                    }
                    case ENCRYPTED_SIGNED_GOT -> {
                        parsedUnit.setData(signature.getMsg());
                        parsedUnit.setDataType(DataType.GOT);
                    }
                    case ENCRYPTED_SIGNED_RELAYED -> {
                        parsedUnit.setData(signature.getMsg());
                        parsedUnit.setDataType(DataType.RELAYED);
                    }
                    case ENCRYPTED_SIGNED_BYTES -> {
                        parsedUnit.setData(signature.getMsg().getBytes());
                        parsedUnit.setDataType(DataType.BYTES);
                    }
                    case ENCRYPTED_SIGNED_REQUEST -> {
                        parsedUnit.setData(RequestBody.fromJson(signature.getMsg(), RequestBody.class));
                        parsedUnit.setDataType(DataType.REQUEST);
                    }
                    case ENCRYPTED_SIGNED_REPLY -> {
                        parsedUnit.setData(ReplyBody.fromJson(signature.getMsg(), ReplyBody.class));
                        parsedUnit.setDataType(DataType.REPLY);
                    }
                    case ENCRYPTED_SIGNED_HAT -> {
                        parsedUnit.setData(Hat.fromJson(signature.getMsg(), Hat.class));
                        parsedUnit.setDataType(DataType.HAT);
                    }
                    case ENCRYPTED_SIGNED_AFFAIR -> {
                        parsedUnit.setData(Affair.fromJson(signature.getMsg(), Affair.class));
                        parsedUnit.setDataType(DataType.AFFAIR);
                    }
                    default -> {
                        return null;
                    }
                }
            }

            case ENCRYPTED_ID_SIGNED_TEXT, ENCRYPTED_ID_SIGNED_BYTES, ENCRYPTED_ID_SIGNED_HAT,
                 ENCRYPTED_ID_SIGNED_REPLY, ENCRYPTED_ID_SIGNED_REQUEST, ENCRYPTED_ID_SIGNED_AFFAIR -> {
                if(!(parsedUnit.getData() instanceof CryptoDataByte cryptoDataByte)) {
                    return null;
                }
                byte[] decryptedData = decryptData(parsedUnit, sessionHandler, priKey);
                if (decryptedData == null) return null;
                IdSignature idSignature = IdSignature.fromBytes(decryptedData, IdSignature.class);
                if (idSignature==null || !idSignature.verify(parsedUnit.getTo())) return null;
                parsedUnit.setFrom(idSignature.getFid());
                switch (parsedUnit.getDataType()) {
                    case ENCRYPTED_ID_SIGNED_TEXT -> {
                        parsedUnit.setData(idSignature.getData());
                        parsedUnit.setDataType(DataType.TEXT);
                    }
                    case ENCRYPTED_ID_SIGNED_BYTES -> {
                        parsedUnit.setData(idSignature.getData().getBytes());
                        parsedUnit.setDataType(DataType.BYTES);
                    }
                    case ENCRYPTED_ID_SIGNED_REQUEST -> {
                        parsedUnit.setData(RequestBody.fromJson(idSignature.getData(), RequestBody.class));
                        parsedUnit.setDataType(DataType.REQUEST);
                    }
                    case ENCRYPTED_ID_SIGNED_REPLY -> {
                        parsedUnit.setData(ReplyBody.fromJson(idSignature.getData(), ReplyBody.class));
                        parsedUnit.setDataType(DataType.REPLY);
                    }
                    case ENCRYPTED_ID_SIGNED_HAT -> {
                        parsedUnit.setData(Hat.fromJson(idSignature.getData(), Hat.class));
                        parsedUnit.setDataType(DataType.HAT);
                    }
                    case ENCRYPTED_ID_SIGNED_AFFAIR -> {
                        parsedUnit.setData(Affair.fromJson(idSignature.getData(), Affair.class));
                        parsedUnit.setDataType(DataType.AFFAIR);
                    }
                    default -> {
                        return null;
                    }
                }
            }
        }
        log.debug("Parsed talkUnit:{}",parsedUnit.toNiceJson());
        parsedUnit.setState(State.READY);
        return parsedUnit;
    }

    @NotNull
    public static TalkUnit clone(TalkUnit talkUnit) {
        TalkUnit parsedUnit = new TalkUnit();
        parsedUnit.setTo(talkUnit.getTo());
        parsedUnit.setTime(talkUnit.getTime());
        parsedUnit.setNonce(talkUnit.getNonce());
        parsedUnit.setIdType(talkUnit.getToType());
        parsedUnit.setBy(talkUnit.getBy());
        if(talkUnit.getId()==null) talkUnit.makeId();
        parsedUnit.setId(talkUnit.getId());
        if(talkUnit.getIdBytes()==null) talkUnit.makeIdBytes();
        parsedUnit.setIdBytes(talkUnit.getIdBytes());
        parsedUnit.setUnitEncryptType(talkUnit.getUnitEncryptType());
        parsedUnit.setDataEncryptType(talkUnit.getDataEncryptType());
        parsedUnit.setBySession(talkUnit.getBySession());
        parsedUnit.setData(talkUnit.getData());
        parsedUnit.setDataType(talkUnit.getDataType());
        parsedUnit.setFrom(talkUnit.getFrom());
        parsedUnit.setState(talkUnit.getState());
        return parsedUnit;
    }

    @Nullable
    public IdSignature signTalkUnitId(String myFid, DataType dataType, byte[] priKey) {
        String dataStr;
        switch (dataType){
            case ID_SIGNED_TEXT -> dataStr=(String) data;
            case ID_SIGNED_BYTES -> dataStr= new String((byte[]) data);
            case ID_SIGNED_REQUEST -> dataStr=((RequestBody) data).toJson();
            case ID_SIGNED_REPLY -> dataStr=((ReplyBody) data).toJson();
            case ID_SIGNED_HAT -> dataStr=((Hat) data).toJson();
            case ID_SIGNED_AFFAIR -> dataStr = ((Affair) data).toJson();
            default -> {
                return null;
            }
        }
        if(dataStr==null) return null;
        // Create ID signature
        if(id==null)makeId();
        IdSignature idSignature = new IdSignature(dataStr, myFid, id, priKey,AlgorithmId.FC_SchnorrSignMsg_No1_NrC7);
        idSignature.sign();
        data = idSignature;
        return idSignature;
    }

    @NotNull
    public Signature signData(DataType dataType, byte[] priKey, byte[] sessionKey, TalkUnit talkUnit) {
        Signature signature = new Signature();
        switch(dataType) {
            case SIGNED_TEXT -> signature.setMsg((String) data);
            case SIGNED_BYTES -> signature.setMsg(new String((byte[]) data));
            case SIGNED_HAT -> signature.setMsg(((Hat) data).toJson());
            case SIGNED_REPLY -> signature.setMsg(((ReplyBody) data).toJson());
            case SIGNED_REQUEST -> signature.setMsg(((RequestBody) data).toJson());
            case SIGNED_AFFAIR -> signature.setMsg(((Affair) data).toJson());
            default -> {
                return null;
            }
        }
        // Then signed the data and encrypt it
        if (sessionKey != null) {

            signature.setAlg(AlgorithmId.FC_Sha256SymSignMsg_No1_NrC7);
            signature.setKey(sessionKey);
            signature.sign();
            CryptoDataByte cryptoDataByte = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7)
                    .encryptBySymKey(signature.toBundle(), sessionKey);
            if (cryptoDataByte.getCode() == 0) {
                talkUnit.setData(cryptoDataByte);
            }
        } else{
            signature.setAlg(AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7);
            signature.setKey(priKey);
            signature.sign();
        }
        data = signature;
        return signature;
    }

    /**
     * Sign the talk unit id instead of the whole talk unit.
     * Used for talk in a group or team to identify the sender and avoid to sign malicious data when talking.
     */
    public static class IdSignature extends FcEntity {
        private String data;

        private String fid;
        private String talkUnitId;
        private String idSign;


        private transient byte[] key;
        private transient AlgorithmId algorithmId;

        public IdSignature(String data,String fid, String talkUnitId, byte[] key, AlgorithmId algorithmId) {
            this.data = data;
            this.fid = fid;
            this.talkUnitId = talkUnitId;
            this.key = key;
            if(algorithmId==null)
                this.algorithmId = AlgorithmId.FC_SchnorrSignMsg_No1_NrC7;
            else
                this.algorithmId = algorithmId;

        }

        public void sign() {
            Signature signature = new Signature();
            signature.setMsg(talkUnitId);
            signature.setFid(KeyTools.priKeyToFid(key));
            signature.sign(talkUnitId, key, algorithmId);
            idSign = signature.getSign();
        }

        public boolean verify(String talkUnitId) {
            Signature signature = new Signature();
            if(talkUnitId!=null){
                this.talkUnitId = talkUnitId;
                signature.setMsg(talkUnitId);
            }else if(this.talkUnitId==null)
                return false;
            signature.setFid(fid);
            signature.setSign(idSign);
            return signature.verify();
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }

        public String getFid() {
            return fid;
        }

        public void setFid(String fid) {
            this.fid = fid;
        }

        public String getTalkUnitId() {
            return talkUnitId;
        }

        public void setTalkUnitId(String talkUnitId) {
            this.talkUnitId = talkUnitId;
        }

        public String getIdSign() {
            return idSign;
        }

        public void setIdSign(String idSign) {
            this.idSign = idSign;
        }

        public byte[] getKey() {
            return key;
        }

        public void setKey(byte[] key) {
            this.key = key;
        }

        public AlgorithmId getAlgorithmId() {
            return algorithmId;
        }

        public void setAlgorithmId(AlgorithmId algorithmId) {
            this.algorithmId = algorithmId;
        }
    }


    public static TalkUnit newSample(){
        TalkUnit talkUnit = new TalkUnit();
        talkUnit.setIdType(IdType.FID);
        talkUnit.setFrom(KeyTools.pubKeyToFchAddr(new ECKey().getPubKey()));
        talkUnit.setTo(KeyTools.pubKeyToFchAddr(new ECKey().getPubKey()));
        talkUnit.setDataType(DataType.TEXT);
        talkUnit.setData("The data at "+talkUnit.getTime());
        return talkUnit;
    }

    @Nullable
    public static TalkUnit readTalkUnitByTcp(DataInputStream dataInputStream) throws IOException {
        byte[] receivedBytes;
        receivedBytes = TcpTools.readBytes(dataInputStream);
        if(receivedBytes==null) return null;

        return fromBytes(receivedBytes);
    }

    // public static TalkUnit fromJson(String talkItemJson){
    //     return new Gson().fromJson(talkItemJson, TalkUnit.class);
    // }

    // public String toJson() {
    //     return JsonTools.toJson(this);
    // }
    // public String toNiceJson() {
    //     Gson gson = new GsonBuilder()
    //             .setPrettyPrinting()
    //             .create();
    //     return gson.toJson(this);
    // }
    // @Override
    // public String toString(){
    //     return toJson();
    // }

    public static final String MAPPINGS = "{\"mappings\":{\"properties\":{\"toType\":{\"type\":\"keyword\"},\"to\":{\"type\":\"wildcard\"},\"door\":{\"type\":\"wildcard\"},\"time\":{\"type\":\"long\"},\"nonce\":{\"type\":\"integer\"},\"from\":{\"type\":\"wildcard\"},\"size\":{\"type\":\"long\"},\"dataType\":{\"type\":\"keyword\"},\"data\":{\"type\":\"text\"}}}}";

    public void renew() {
        this.nonce = Math.abs(BytesTools.bytesToIntBE(BytesTools.getRandomBytes(4)));
        this.time = System.currentTimeMillis();
    }

    public enum State {
        NEW((byte) 0),
        READY((byte)1),
        ENCRYPTED((byte) 2),
        SENT((byte) 3),
        RELAYING((byte) 4),
        RELAYED((byte) 5),
        GOT((byte) 6),
        REJECTED((byte) 7),
        REPLIED((byte)8),
        PAID((byte)9),
        FAILED_TO_SEND((byte) 10),
        DONE((byte)99);

        //new, ready, sent, relaying，relayed, got,suspended
        public final byte number;
        State(byte number) {this.number=number;}
    }
    public enum IdType {

        FID((byte)0),
        GROUP((byte)1),
        TEAM((byte)2);

        public final byte number;
        IdType(byte number) {this.number=number;}
        public static IdType getIdType(byte number) {
            for (IdType toType : IdType.values()) {
                if (toType.number == number) {
                    return toType;
                }
            }
            return null;
        }
    }

    public enum DataType {
        BYTES((byte)0),
        TEXT((byte)1),
        HAT((byte)2),
        REQUEST((byte) 3),
        REPLY((byte)4),
        AFFAIR((byte)5),

        GOT((byte)8),
        RELAYED((byte)9),

        SIGNED_BYTES((byte)10),
        SIGNED_TEXT((byte)11),
        SIGNED_HAT((byte)12),
        SIGNED_REQUEST((byte)13),
        SIGNED_REPLY((byte)14),

        SIGNED_AFFAIR((byte)15),
        SIGNED_GOT((byte)18),
        SIGNED_RELAYED((byte)19),

        ENCRYPTED_BYTES((byte)20),
        ENCRYPTED_TEXT((byte)21),
        ENCRYPTED_HAT((byte)22),
        ENCRYPTED_REQUEST((byte)23),
        ENCRYPTED_REPLY((byte)24),

        ENCRYPTED_AFFAIR((byte)25),
        ENCRYPTED_GOT((byte)28),
        ENCRYPTED_RELAYED((byte)29),

        ENCRYPTED_SIGNED_BYTES((byte)30),
        ENCRYPTED_SIGNED_TEXT((byte)31),
        ENCRYPTED_SIGNED_HAT((byte)32),
        ENCRYPTED_SIGNED_REQUEST((byte)33),
        ENCRYPTED_SIGNED_REPLY((byte)34),

        ENCRYPTED_SIGNED_AFFAIR((byte)35),
        ENCRYPTED_SIGNED_GOT((byte)38),
        ENCRYPTED_SIGNED_RELAYED((byte)39),

        ID_SIGNED_BYTES((byte)40),
        ID_SIGNED_TEXT((byte)41),
        ID_SIGNED_HAT((byte)42),
        ID_SIGNED_REQUEST((byte)43),
        ID_SIGNED_REPLY((byte)44),
        ID_SIGNED_AFFAIR((byte)45),

        ENCRYPTED_ID_SIGNED_BYTES((byte)50),
        ENCRYPTED_ID_SIGNED_TEXT((byte)51),
        ENCRYPTED_ID_SIGNED_HAT((byte)52),
        ENCRYPTED_ID_SIGNED_REQUEST((byte)53),
        ENCRYPTED_ID_SIGNED_REPLY((byte)54),

        ENCRYPTED_ID_SIGNED_AFFAIR((byte)55);

        public final byte number;
        DataType(byte number) {this.number=number;}

        public static DataType getDataType(byte number) {
            for (DataType dataType : DataType.values()) {
                if (dataType.number == number) {
                    return dataType;
                }
            }
            return null;
        }
    }

    /**
     * Convert TalkUnit to bytes
     */
    @Override
    public byte[] toBytes(){

        try(ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder()){

            byteArrayBuilder.write(this.toType.number);
            switch (this.toType){
                case FID -> byteArrayBuilder.write(KeyTools.addrToHash160(to));
                case GROUP,TEAM -> Hex.fromHex(this.to);
            }

            byteArrayBuilder.write(BytesTools.longToBytes(this.time));
            byteArrayBuilder.write(BytesTools.intToByteArray(this.nonce));

            byteArrayBuilder.write(this.dataType.number);
            switch (this.dataType){
                case BYTES -> byteArrayBuilder.write((byte[]) data);
                case TEXT -> byteArrayBuilder.write(((String)data).getBytes());
                case REQUEST -> byteArrayBuilder.write(((RequestBody)data).toBytes());
                case REPLY ->  byteArrayBuilder.write(((ReplyBody)data).toBytes());
                case HAT ->  byteArrayBuilder.write(((Hat)data).toBytes());
                case AFFAIR -> byteArrayBuilder.write(((Affair)data).toBytes());
                case SIGNED_TEXT,SIGNED_BYTES,SIGNED_HAT,SIGNED_REPLY,SIGNED_REQUEST,SIGNED_AFFAIR
                        -> byteArrayBuilder.write(((Signature)data).toBundle());
                case ENCRYPTED_GOT, ENCRYPTED_RELAYED,ENCRYPTED_REQUEST,ENCRYPTED_BYTES,ENCRYPTED_HAT,ENCRYPTED_REPLY,
                        ENCRYPTED_SIGNED_BYTES,ENCRYPTED_SIGNED_TEXT,ENCRYPTED_SIGNED_HAT,
                        ENCRYPTED_SIGNED_REPLY,ENCRYPTED_SIGNED_REQUEST,ENCRYPTED_TEXT,
                        ENCRYPTED_ID_SIGNED_BYTES,ENCRYPTED_ID_SIGNED_TEXT,ENCRYPTED_ID_SIGNED_HAT,
                        ENCRYPTED_ID_SIGNED_REPLY,ENCRYPTED_ID_SIGNED_REQUEST,ENCRYPTED_AFFAIR,
                        ENCRYPTED_SIGNED_AFFAIR,ENCRYPTED_ID_SIGNED_AFFAIR -> {
                    byte[] cipher = ((CryptoDataByte) data).toBundle();
                    byteArrayBuilder.write(cipher);
                }
                default -> byteArrayBuilder.write(JsonTools.toJson(data).getBytes());
            }
            return byteArrayBuilder.toByteArray();
        }
    }

    public static TalkUnit fromBytes(byte[] bytes) {
        TalkUnit talkUnit = new TalkUnit();
        try {
            // Use setter methods
            talkUnit.setTime(null);
            talkUnit.setNonce(null);

            ByteBuffer buffer = ByteBuffer.wrap(bytes);

            // Extract ToType
            byte toTypeByte = buffer.get();
            talkUnit.setIdType(IdType.getIdType(toTypeByte));

            // Extract 'to' based on ToType
            int toLength = 0;
            byte[] toBytes;
            switch (talkUnit.getIdType()) {
                case FID -> {
                    toLength = 20;
                    toBytes = new byte[toLength];
                    buffer.get(toBytes);
                    talkUnit.setTo(KeyTools.hash160ToFchAddr(toBytes));
                }
                case GROUP, TEAM -> {
                    toLength = 32; // Hex
                    toBytes = new byte[toLength];
                    buffer.get(toBytes);
                    talkUnit.setTo(Hex.toHex(toBytes));
                }
            }

            talkUnit.setTime(buffer.getLong());

            talkUnit.setNonce(buffer.getInt());

            // Extract DataType
            byte dataTypeByte = buffer.get();
            talkUnit.setDataType(DataType.getDataType(dataTypeByte));

            // Extract data
            int remaining = buffer.remaining();
            byte[] dataBytes = new byte[remaining];
            buffer.get(dataBytes);
            Gson gson = new Gson();

            switch (talkUnit.getDataType()) {
                case BYTES -> talkUnit.setData(dataBytes);
                case TEXT -> talkUnit.setData(new String(dataBytes, StandardCharsets.UTF_8));
                case HAT -> talkUnit.setData(gson.fromJson(new String(dataBytes, StandardCharsets.UTF_8), Hat.class));
                case REPLY -> talkUnit.setData(gson.fromJson(new String(dataBytes, StandardCharsets.UTF_8), ReplyBody.class));
                case REQUEST -> talkUnit.setData(gson.fromJson(new String(dataBytes, StandardCharsets.UTF_8), RequestBody.class));
                case AFFAIR -> talkUnit.setData(Affair.fromBytes(dataBytes, Affair.class));
                case SIGNED_BYTES, SIGNED_TEXT, SIGNED_HAT, SIGNED_REPLY, SIGNED_REQUEST, SIGNED_AFFAIR -> {
                    Signature signature = Signature.fromBundle(dataBytes);
                    talkUnit.setData(signature);
                }
                case ENCRYPTED_GOT, ENCRYPTED_RELAYED,ENCRYPTED_BYTES, ENCRYPTED_TEXT, ENCRYPTED_HAT, ENCRYPTED_REPLY, ENCRYPTED_REQUEST,
                        ENCRYPTED_SIGNED_BYTES, ENCRYPTED_SIGNED_TEXT, ENCRYPTED_SIGNED_HAT, ENCRYPTED_SIGNED_REPLY,
                        ENCRYPTED_SIGNED_REQUEST, ENCRYPTED_ID_SIGNED_BYTES, ENCRYPTED_ID_SIGNED_TEXT, ENCRYPTED_ID_SIGNED_HAT,
                        ENCRYPTED_ID_SIGNED_REPLY, ENCRYPTED_ID_SIGNED_REQUEST, ENCRYPTED_AFFAIR,
                        ENCRYPTED_SIGNED_AFFAIR, ENCRYPTED_ID_SIGNED_AFFAIR
                        -> {
                    CryptoDataByte cryptoDataByte = CryptoDataByte.fromBundle(dataBytes);
                    talkUnit.setData(cryptoDataByte);
                }
                default -> {
                    return null;
                }
            }
        }catch (Exception e){
            System.out.println("Failed to parse. Error:"+e.getMessage());
            return null;
        }

        return talkUnit;
    }

    public String makeId() {
        return this.id = makeId(this.time, this.nonce);
    }

    public static String makeId(Long time, Integer nonce) {
        if(time==null)
            time = System.currentTimeMillis();
        if(nonce==null)
            nonce= Math.abs(BytesTools.bytesToIntBE(BytesTools.getRandomBytes(4)));

        String date = DateTools.longToTime(time,"yyyyMMdd_HHmmssSSS");
        return date + "_" + nonce;//Hex.toHex(BytesTools.intToByteArray(nonce));
    }

    public byte[] makeIdBytes() {
        this.idBytes = new BytesTools.ByteArrayAsKey(BytesTools.bytesMerger(BytesTools.longToBytes(this.time), BytesTools.intToByteArray(this.nonce)));

        return this.idBytes.getBytes();
    }
    @Override
    public int compareTo(@NotNull TalkUnit other) {
        return Arrays.compare(this.idBytes.getBytes(),other.getIdBytes().getBytes());
    }
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }
    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public Integer getNonce() {
        return nonce;
    }

    public void setNonce(Integer nonce) {
        this.nonce = nonce;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public IdType getIdType() {
        return toType;
    }

    public void setIdType(IdType toType) {
        this.toType = toType;
    }

    public DataType getDataType() {
        return dataType;
    }

    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }

    public State getStata() {
        return state;
    }

    public void setStata(State stata) {
        this.state = stata;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public BytesTools.ByteArrayAsKey getIdBytes() {
        return idBytes;
    }

    public void setIdBytes(BytesTools.ByteArrayAsKey idBytes) {
        this.idBytes = idBytes;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TalkUnit talkUnit = (TalkUnit) o;
        return talkUnit.makeId().equals(this.makeId());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(makeIdBytes());
    }

    public CryptoDataByte encryptUnit(byte[] sessionKey, byte[] priKey, String pubKey, @Nullable EncryptType unitEncryptType) {
        CryptoDataByte cryptoDataByte = null;
        if(unitEncryptType ==null) {
                if (sessionKey != null) {
                    Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
                    cryptoDataByte = encryptor.encryptBySymKey(toBytes(), sessionKey);
                return cryptoDataByte;
            } else if (priKey != null && pubKey != null) {
                Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
                cryptoDataByte = encryptor.encryptByAsyTwoWay(toBytes(), priKey, Hex.fromHex(pubKey));
                return cryptoDataByte;
            }
            return cryptoDataByte;
        }else{
            switch (unitEncryptType){
                case SymKey -> {
                    if(sessionKey==null){
                        cryptoDataByte = new CryptoDataByte();
                        cryptoDataByte.setCode(CodeMessage.Code1023MissSessionKey);
                        cryptoDataByte.setMessage(CodeMessage.Msg1023MissSessionKey);
                        return cryptoDataByte;
                    };
                    cryptoDataByte = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7)
                            .encryptBySymKey(toBytes(), sessionKey);
                }
                case AsyTwoWay -> {
                    if(pubKey==null){
                        cryptoDataByte = new CryptoDataByte();
                        cryptoDataByte.setCode(CodeMessage.Code1001PubKeyMissed);
                        cryptoDataByte.setMessage(CodeMessage.Msg1001PubKeyMissed);
                        return cryptoDataByte;
                    };
                    if(priKey==null){
                        cryptoDataByte = new CryptoDataByte();
                        cryptoDataByte.setCode(CodeMessage.Code1033MissPriKey);
                        cryptoDataByte.setMessage(CodeMessage.Msg1033MissPriKey);
                        return cryptoDataByte;
                    };
                    cryptoDataByte = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7)
                            .encryptByAsyTwoWay(toBytes(), priKey, Hex.fromHex(pubKey));
                }
                case AsyOneWay -> {
                    if(pubKey==null){
                        cryptoDataByte = new CryptoDataByte();
                        cryptoDataByte.setCode(CodeMessage.Code1001PubKeyMissed);
                        cryptoDataByte.setMessage(CodeMessage.Msg1001PubKeyMissed);
                        return cryptoDataByte;
                    };
                    Signature signature = new Signature();
                    signature.setMsg(Base64.getEncoder().encodeToString(toBytes()));
                    signature.setKey(priKey);
                    signature.setAlg(AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7);
                    signature.sign();
                    cryptoDataByte = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7)
                            .encryptByAsyOneWay(signature.toBundle(), Hex.fromHex(pubKey));
                }
                default -> {
                    cryptoDataByte = new CryptoDataByte();
                    cryptoDataByte.setCode(CodeMessage.Code4014NoSuchEncryptType);
                    cryptoDataByte.setMessage(CodeMessage.Msg4014NoSuchEncryptType);
                    return cryptoDataByte;
                }
            }
        }
        return cryptoDataByte;
    }

    public static FcSession prepareSession(String fid, SessionHandler sessionHandler, TalkIdHandler talkIdHandler, ContactHandler contactHandler, ApipClient apipClient) {
        FcSession fcSession;
        fcSession = sessionHandler.getSessionById(fid);
        if(fcSession==null){
            String pubKey = KeyTools.getPubKey(fid, sessionHandler, talkIdHandler, contactHandler, apipClient);
            fcSession = sessionHandler.addNewSession(fid, pubKey);
        }
        return fcSession;
    }

    public static FcSession prepareSession(CryptoDataByte cryptoDataByte,SessionHandler sessionHandler) {
        FcSession session;
        String pubKeyHex=null;
        if(cryptoDataByte.getKeyName()!=null)
            return sessionHandler.getSessionByName(Hex.toHex(cryptoDataByte.getKeyName()));

        byte[] pubKey;
        if(cryptoDataByte.getPubKeyA()!=null)pubKey = cryptoDataByte.getPubKeyA();
        else pubKey=cryptoDataByte.getPubKeyB();
        if(pubKey!=null)pubKeyHex=Hex.toHex(pubKey);
        String fid = KeyTools.pubKeyToFchAddr(pubKeyHex);
        session = sessionHandler.getSessionById(fid);
        if(session!=null){
            if(session.getPubKey()==null){
                session.setPubKey(pubKeyHex);
                sessionHandler.putSession(session);
            }
            return session;
        }
        session = sessionHandler.addNewSession(fid,pubKeyHex);
        return session;
    }
    public static TalkUnit decryptUnit(byte[] bundle, byte[] priKey, FcSession bySession, SessionHandler fcSessionHandler) {
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromBundle(bundle);
        if(cryptoDataByte==null){
            log.debug("Failed to get cryptoDataByte from bundle.");
            return null;
        }
        return decryptUnit(cryptoDataByte, priKey, bySession, fcSessionHandler);
    }

    @Nullable
    public static TalkUnit decryptUnit(CryptoDataByte cryptoDataByte, byte[] priKey, @Nullable FcSession bySession, @Nullable SessionHandler fcSessionHandler) {
        if (cryptoDataByte == null) return null;
        FcSession session = bySession;
        if(bySession==null && fcSessionHandler!=null)
            session = TalkUnit.prepareSession(cryptoDataByte,fcSessionHandler);
        if(session==null)return null;

        decrypt(priKey, cryptoDataByte, session);
        if (cryptoDataByte.getCode()!=0) {
            log.debug("Failed to decrypt talkUnit. Code:{}. Message:{}", cryptoDataByte.getCode(), cryptoDataByte.getMessage());
            return null;
        }

        TalkUnit talkUnit = null;
        byte[] data = null;
        if(cryptoDataByte.getType().equals(EncryptType.AsyOneWay)){
            byte[] bundle = cryptoDataByte.getData();
            if(bundle==null){
                log.debug("Failed to get bundle from cryptoDataByte.");
                return null;
            }
            Signature signature = Signature.fromBundle(bundle);
            if(!signature.verify()){
                log.debug("Failed to verify signature.");
                return null;
            }
            data = Base64.getDecoder().decode(signature.getMsg());
        }else{
            data = cryptoDataByte.getData();
        }
        talkUnit = fromBytes(data);
        if(talkUnit==null){
            log.debug("Failed to get talk unit from bundle.");
            return null;
        }
        if(talkUnit.getId()==null)talkUnit.makeId();
        if(talkUnit.getIdBytes()==null)talkUnit.makeIdBytes();
        log.debug("Got talkUnit from bytes:"+talkUnit.toNiceJson());

        if(cryptoDataByte.getType().equals(EncryptType.AsyTwoWay))
            talkUnit.setBy(session.getId());
        else {
            talkUnit.setBy(session.getId());
        }

        talkUnit.setStata(State.GOT);
        talkUnit.setBySession(session);
        talkUnit.setUnitEncryptType(cryptoDataByte.getType());
        return talkUnit;
    }

    @Nullable
    public static CryptoDataByte encryptData(byte[] bytes, EncryptType encryptType, byte[] sessionKey, byte[] priKey, String pubKey) {
        CryptoDataByte cryptoDataByte;
        if(encryptType==null) {
            if (sessionKey != null) {
                cryptoDataByte = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7)
                        .encryptBySymKey(bytes, sessionKey);
                } else {
                    if (pubKey == null){
                        cryptoDataByte = new CryptoDataByte();
                        cryptoDataByte.setCode(CodeMessage.Code1001PubKeyMissed);
                        cryptoDataByte.setMessage(CodeMessage.Msg1001PubKeyMissed);
                        return cryptoDataByte;
                    }
                cryptoDataByte = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7)
                        .encryptByAsyTwoWay(bytes, priKey, Hex.fromHex(pubKey));
            }
            return cryptoDataByte;
        }else{
            switch (encryptType){
                            case SymKey -> {
                                if(sessionKey==null) return CryptoDataByte.makeErrorCryptDataByte(CodeMessage.Code1023MissSessionKey);
                                cryptoDataByte = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7)
                                        .encryptBySymKey(bytes, sessionKey);
                            }
                            case AsyOneWay -> {
                                if(priKey==null)
                                    return CryptoDataByte.makeErrorCryptDataByte(CodeMessage.Code1033MissPriKey);
                                cryptoDataByte = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7)
                                        .encryptByAsyOneWay(bytes, Hex.fromHex(pubKey));
                            }
                            case AsyTwoWay -> {
                                if(pubKey==null)return CryptoDataByte.makeErrorCryptDataByte(CodeMessage.Code1001PubKeyMissed);
                                if(priKey==null)return CryptoDataByte.makeErrorCryptDataByte(CodeMessage.Code1033MissPriKey);
                                cryptoDataByte = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7)
                                        .encryptByAsyTwoWay(bytes, priKey, Hex.fromHex(pubKey));
                            }
                            default -> {
                                cryptoDataByte = new CryptoDataByte();
                                cryptoDataByte.setCode(CodeMessage.Code4002NoSuchAlgorithm);
                                cryptoDataByte.setMessage(CodeMessage.Msg1001PubKeyMissed);
                                return cryptoDataByte;
                            }
            }
        }
        return cryptoDataByte;
    }

    //    private static byte[] decryptData(CryptoDataByte cryptoDataByte, byte[] sessionKey, byte[] priKey) {
//        Decryptor decryptor = new Decryptor();
//        if (sessionKey != null) {
//            cryptoDataByte = decryptor.decrypt(cryptoDataByte.toBundle(), sessionKey);
//        } else if (priKey != null) {
//            cryptoDataByte.setPriKeyB(priKey);
//            cryptoDataByte = decryptor.decrypt(cryptoDataByte);
//        } else {
//            return null;
//        }
//
//        return cryptoDataByte.getCode() == 0 ? cryptoDataByte.getData() : null;
//    }

//    public static RequestBody makeRequestBody(String from, String sid, IdType toType, String to,
//                                              Op op, Object data) {
//        RequestBody requestBody = new RequestBody();
//        requestBody.setSid(sid);
//        requestBody.setOp(op);
//        requestBody.setData(data);
//
//        return requestBody;
//    }

    public static ReplyBody makeReplyBody(String id, Op op, Integer code,
                                          String message, Object data, Integer nonce) {
        ReplyBody replyBody = new ReplyBody();
        replyBody.setRequestId(id);
        replyBody.setOp(op);
        replyBody.setCode(code);
        replyBody.setMessage(message);
        replyBody.setNonce(nonce);
        replyBody.setData(data);

        return replyBody;
    }

    public String getBy() {
        return by;
    }

    public void setBy(String by) {
        this.by = by;
    }

    public IdType getToType() {
        return toType;
    }

    public void setToType(IdType toType) {
        this.toType = toType;
    }

    public static byte[] notifyGot(TalkUnit sourseTalkUnit, byte[] myPriKey,FcSession fromSession, FcSession bySession) {
        if(sourseTalkUnit==null)return null;
        if(!sourseTalkUnit.getToType().equals(IdType.FID))return null;
        if(sourseTalkUnit.getFrom().equals(sourseTalkUnit.getTo()))return null;
        TalkUnit notifyUnit = new TalkUnit(sourseTalkUnit.getTo(), sourseTalkUnit.getId(), DataType.ENCRYPTED_GOT, sourseTalkUnit.getFrom(), IdType.FID);
        notifyUnit.setDataEncryptType(sourseTalkUnit.getDataEncryptType());
        notifyUnit.makeTalkUnit(fromSession.getKeyBytes(),myPriKey,fromSession.getPubKey());
        notifyUnit.setUnitEncryptType(sourseTalkUnit.getUnitEncryptType());
        CryptoDataByte cryptoDataByte = notifyUnit.encryptUnit(bySession.getKeyBytes(), myPriKey, bySession.getPubKey(), sourseTalkUnit.getUnitEncryptType());
        if(cryptoDataByte == null) return null;
        return cryptoDataByte.toBundle();
    }

    public static byte[] notifyRelayed(TalkUnit sourseTalkUnit, byte[] myPriKey, FcSession bySession) {
        if(sourseTalkUnit==null)return null;
        TalkUnit notifyUnit = new TalkUnit(sourseTalkUnit.getTo(), sourseTalkUnit.getId(), ENCRYPTED_RELAYED, sourseTalkUnit.getFrom(), IdType.FID);
        notifyUnit.setDataEncryptType(sourseTalkUnit.getDataEncryptType());
        notifyUnit.makeTalkUnit(bySession.getKeyBytes(),myPriKey,bySession.getPubKey());
        notifyUnit.setUnitEncryptType(sourseTalkUnit.getUnitEncryptType());
        CryptoDataByte cryptoDataByte = notifyUnit.encryptUnit(bySession.getKeyBytes(), myPriKey, bySession.getPubKey(), sourseTalkUnit.getUnitEncryptType());
        if(cryptoDataByte == null) return null;
        return cryptoDataByte.toBundle();
    }

    private static byte[] decryptData(TalkUnit decryptedUnit, SessionHandler sessionHandler, byte[] priKey) {
        if(!(decryptedUnit.getData() instanceof CryptoDataByte cryptoDataByte))return null;
        FcSession session = prepareSession(cryptoDataByte,sessionHandler);
        decrypt(priKey, cryptoDataByte, session);
        if (cryptoDataByte.getCode()!=0) return null;
        decryptedUnit.setDataEncryptType(cryptoDataByte.getType());
        decryptedUnit.setFromSession(session);
        decryptedUnit.setFrom(session.getId());
        return cryptoDataByte.getData();

//
//        if(cryptoDataByte.getType().equals(EncryptType.SymKey)) {
//            byte[] keyNameBytes = cryptoDataByte.getKeyName();
//            String keyName = null;
//            if(keyNameBytes != null) {
//                keyName = Hex.toHex(keyNameBytes);
//            }
//            if(keyName != null) {
//                session = sessionHandler.getSessionByName(keyName);
//            }
//            if(session == null || session.getKey() == null) {
//                decryptedUnit.setCodeMessage(CodeMessage.Code1023MissSessionKey);
//                return null;
//            }
//            decryptedUnit.setFrom(session.getId());
//            if(session.getKeyBytes() == null) {
//                session.makeKeyBytes();
//            }
//            decryptedData = decryptData(cryptoDataByte, session.getKeyBytes(), null);
//        } else if(cryptoDataByte.getType().equals(EncryptType.AsyTwoWay)) {
//            if(priKey == null) {
//                decryptedUnit.setCodeMessage(CodeMessage.Code1033MissPriKey);
//                return null;
//            }
//            decryptedUnit.setFrom(KeyTools.pubKeyToFchAddr(cryptoDataByte.getPubKeyA()));
//            decryptedData = decryptData(cryptoDataByte, null, priKey);
//        }
//
//        return decryptedData;
    }

    private static void decrypt(byte[] priKey, CryptoDataByte cryptoDataByte, FcSession session) {
        Decryptor decryptor = new Decryptor();
        switch (cryptoDataByte.getType()) {
            case SymKey -> {
                if (session != null) {
                    if(session.getKeyBytes()==null)session.makeKeyBytes();
                    cryptoDataByte.setSymKey(session.getKeyBytes());
                    cryptoDataByte = decryptor.decrypt(cryptoDataByte);
                }
            }
            case AsyTwoWay, AsyOneWay -> {
                if (priKey != null) {
                    cryptoDataByte.setPriKeyB(priKey);
                    cryptoDataByte = decryptor.decrypt(cryptoDataByte);
                }
            }
            default -> {
                log.debug("Failed to decrypt talkUnit. Type:{}. Message:{}", cryptoDataByte.getType(), cryptoDataByte.getMessage());
                return;
            }
        }
        log.debug("Decrypted:"+ cryptoDataByte.toNiceJson());
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public EncryptType getUnitEncryptType() {
        return unitEncryptType;
    }

    public void setUnitEncryptType(EncryptType unitEncryptType) {
        this.unitEncryptType = unitEncryptType;
    }

    public EncryptType getDataEncryptType() {
        return dataEncryptType;
    }

    public void setDataEncryptType(EncryptType dataEncryptType) {
        this.dataEncryptType = dataEncryptType;
    }

    public FcSession getBySession() {
        return bySession;
    }

    public void setBySession(FcSession bySession) {
        this.bySession = bySession;
    }

    public FcSession getFromSession() {
        return fromSession;
    }

    public void setFromSession(FcSession fromSession) {
        this.fromSession = fromSession;
    }
}
