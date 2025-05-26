package data.fcData;


import config.Settings;
import constants.FieldNames;
import constants.Strings;
import core.crypto.Encryptor;
import core.crypto.Hash;
import core.crypto.old.EccAes256K1P7;
import org.jetbrains.annotations.Nullable;

import redis.clients.jedis.Jedis;
import utils.BytesUtils;
import utils.Hex;
import utils.IdNameUtils;
import utils.JsonUtils;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static constants.FieldNames.PUB_KEY;
import static constants.FieldNames.SESSION_KEY;
import static constants.Values.TRUE;

public class FcSession extends FcObject {
    private String key;
    private String pubkey;
    private String keyCipher;
    private String userId;
    private transient byte[] keyBytes;


    // public static void deleteSession(String sessionName, String sid, Jedis jedis) {
    //     jedis.select(1);
    //     String key = Settings.addSidBriefToName(sid, sessionName);
    //     jedis.del(key);
    // }
    public byte[] makeKeyBytes(){
        if(this.key!=null)
            this.keyBytes = Hex.fromHex(this.key);
        return this.keyBytes;
    }
    private static FcSession fromMap(Map<String, String> sessionMap) {
        FcSession session = new FcSession();
        session.setUserId(sessionMap.get(FieldNames.ID));
        String sessionKey = sessionMap.get(SESSION_KEY);
        if(sessionKey!=null) {
            byte[] keyBytes = Hex.fromHex(sessionKey);
            session.setKeyBytes(keyBytes);
            session.setKey(sessionKey);
            session.setId(IdNameUtils.makeKeyName(keyBytes));
        }
        session.setPubkey(sessionMap.get(PUB_KEY));
        session.makeKeyBytes();
        return session;
    }



    public String sign(byte[] dataBytes) {
        return sign(keyBytes,dataBytes);
    }
    public static String sign(byte[] sessionKeyBytes, byte[] dataBytes) {
        byte[] signBytes = Hash.sha256x2(BytesUtils.bytesMerger(dataBytes, sessionKeyBytes));
        return Hex.toHex(signBytes);
    }

    public String verifySign(String sign, byte[] requestBodyBytes) {
        if(sign==null)return "The sign is null.";
        if(requestBodyBytes==null)return "The byte array is null.";
        byte[] signBytes = BytesUtils.bytesMerger(requestBodyBytes, keyBytes);
        String doubleSha256Hash = HexFormat.of().formatHex(Hash.sha256x2(signBytes));

        if(!sign.equals(doubleSha256Hash)){
            return "The sign of the request body should be: "+doubleSha256Hash;
        }
        return TRUE;
    }


    public static FcSession getSessionFromJedis(String sid, String pubKey, Jedis jedis, String id) {
        jedis.select(0);
        String sessionName = jedis.hget(Settings.addSidBriefToName(sid, Strings.ID_SESSION_NAME), id);
        FcSession fcSession =new FcSession();
        fcSession.setUserId(id);
        fcSession.setId(sessionName);
        jedis.select(1);
        String sessionKey = jedis.hget(sessionName, SESSION_KEY);
        if (sessionKey != null) {
            String sessionKeyCipher = EccAes256K1P7.encryptWithPubKey(sessionKey.getBytes(), Hex.fromHex(pubKey));
            fcSession.setKeyCipher(sessionKeyCipher);
        } else {
            try {
                fcSession = FcSession.makeSession(sid, jedis, id);
            } catch (Exception e) {
                System.out.println(e.getMessage());
                return null;
            }
        }
        jedis.select(0);
        fcSession.makeKeyBytes();
        return fcSession;
    }

    @Nullable
    public static FcSession makeNewSession(String sid, String pubKey, Jedis jedis, String id) {
        FcSession fcSession;
        try {
            fcSession = FcSession.makeSession(sid, jedis, id);
            Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
            String sessionKeyCipher = encryptor.encryptByAsyOneWay(fcSession.getKey().getBytes(), Hex.fromHex(pubKey)).toJson();//EccAes256K1P7.encryptWithPubKey(session.getSessionKey().getBytes(), Hex.fromHex(pubKey));
            fcSession.setKeyCipher(sessionKeyCipher);
            fcSession.makeKeyBytes();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }
        return fcSession;
    }

    public static FcSession makeSession(String sid, Jedis jedis, String id) {
        String sessionKey;
        String sessionName;
        FcSession fcSession;

        jedis.select(1);
        do {
            sessionKey = genKey(32);
            sessionName = makeSessionName(Hex.fromHex(sessionKey));
        } while (!jedis.exists(sessionName));
        fcSession = new FcSession();
        Map<String,String> sessionMap = new HashMap<>();
        sessionMap.put("sessionKey",sessionKey);
        sessionMap.put("id", id);

        //Delete the old session of the requester.
        jedis.select(0);
        String oldSessionName = jedis.hget(Settings.addSidBriefToName(sid,Strings.ID_SESSION_NAME), id);

        jedis.select(1);
        if (oldSessionName != null) jedis.del(oldSessionName);

        //Set the new session
        jedis.hmset(sessionName, sessionMap);

        fcSession.setKey(sessionKey);

        jedis.select(0);
        jedis.hset(Settings.addSidBriefToName(sid,Strings.ID_SESSION_NAME), id, sessionName);
        fcSession.makeKeyBytes();
        return fcSession;
    }

    public static String genKey(Integer length) {
        if(length==null)return null;
        SecureRandom random = new SecureRandom();
        byte[] keyBytes = new byte[length];
        random.nextBytes(keyBytes);
        return BytesUtils.bytesToHexStringBE(keyBytes);
    }

    public static String makeSessionName(byte[] sessionKey) {
        return IdNameUtils.makeKeyName(sessionKey);
    }
    public String makeId() {
        if(keyBytes==null & key!=null)keyBytes=Hex.fromHex(key);
        return IdNameUtils.makeKeyName(keyBytes);
    }

    public byte[] getKeyBytes() {
        if(keyBytes == null) {
            keyBytes = Hex.fromHex(key);
        }
        return keyBytes;
    }

    public void setKeyBytes(byte[] keyBytes) {
        this.keyBytes = keyBytes;
    }

//    public String getId() {
//        return id;
//    }
//
//    public void setId(String id) {
//        this.id = id;
//    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getPubkey() {
        return pubkey;
    }

    public void setPubkey(String pubkey) {
        this.pubkey = pubkey;
    }

    public String getKeyCipher() {
        return keyCipher;
    }

    public void setKeyCipher(String keyCipher) {
        this.keyCipher = keyCipher;
    }
    public static String toJsonList(List<FcSession> value) {
        return JsonUtils.toJson(value);
    }

    public static List<FcSession> fromJsonList(String json) {
        return JsonUtils.listFromJson(json, FcSession.class);
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
