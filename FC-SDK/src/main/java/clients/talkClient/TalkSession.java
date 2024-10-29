package clients.talkClient;


import com.google.gson.Gson;
import constants.FieldNames;
import constants.Strings;
import crypto.Hash;
import fcData.IdNameTools;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;
import redis.clients.jedis.Jedis;
import appTools.Settings;

import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import static constants.FieldNames.PUB_KEY;
import static constants.Strings.FID;
import static constants.Strings.SESSION_KEY;
import static constants.Values.TRUE;

public class TalkSession {
    private String name;
    private transient byte[] keyBytes;
    private String key;
    private String fid;
    private String pubKey;
    private Long expire;

    public static TalkSession getSessionFromJedisWithFid(String fid, String sid, Jedis jedis) {
        String sessionName = jedis.hget(Settings.addSidBriefToName(sid,Strings.FID_SESSION_NAME), fid);
        if(sessionName==null)return null;
        TalkSession session = getSessionFromJedisWithKeyName(sessionName,sid,jedis);
        if(session==null){
            jedis.select(0);
            jedis.hdel(Settings.addSidBriefToName(sid,Strings.FID_SESSION_NAME), fid);
            return null;
        }
        return session;
    }

    public static TalkSession getSessionFromJedisWithKeyName(String sessionName, String sid, Jedis jedis) {
        if(sessionName==null)return null;
        jedis.select(1);
        String key = Settings.addSidBriefToName(sid, sessionName);
        Map<String, String> sessionMap = jedis.hgetAll(key);
        if(sessionMap==null)return null;
        TalkSession session = TalkSession.fromMap(sessionMap);
        long ttl = jedis.ttl(key);
        if(ttl>0){
            session.setExpire(System.currentTimeMillis() + (ttl*1000));
        }
        return session;
    }

    private static TalkSession fromMap(Map<String, String> sessionMap) {
        TalkSession session = new TalkSession();
        session.setFid(sessionMap.get(FieldNames.FID));
        String sessionKey = sessionMap.get(SESSION_KEY);
        if(sessionKey!=null) {
            byte[] keyBytes = Hex.fromHex(sessionKey);
            session.setKeyBytes(keyBytes);
            session.setKey(sessionKey);
            session.setName(IdNameTools.makeKeyName(keyBytes));
        }
        session.setPubKey(sessionMap.get(PUB_KEY));
        return session;
    }

    public String toJson(){
        return JsonTools.toJson(this);
    }

    public String toNiceJson(){
        return JsonTools.toNiceJson(this);
    }
    public static TalkSession fromJson(String json){
        return new Gson().fromJson(json, TalkSession.class);
    }

    public byte[] sign(byte[] dataBytes) {
        return sign(keyBytes,dataBytes);
    }
    public static byte[] sign(byte[] sessionKeyBytes, byte[] dataBytes) {
        return Hash.sha256x2(BytesTools.bytesMerger(dataBytes, sessionKeyBytes));
    }

    public String verifySign(String sign, byte[] requestBodyBytes) {
        if(sign==null)return "The sign is null.";
        if(requestBodyBytes==null)return "The byte array is null.";
        byte[] signBytes = BytesTools.bytesMerger(requestBodyBytes, keyBytes);
        String doubleSha256Hash = HexFormat.of().formatHex(Hash.sha256x2(signBytes));

        if(!sign.equals(doubleSha256Hash)){
            return "The sign of the request body should be: "+doubleSha256Hash;
        }
        return TRUE;
    }


    public static TalkSession makeNewSession(String sid, Jedis jedis, String fid, String userPubKey, Long sessionDays) {
        byte[] sessionKey;
        String sessionName;
        TalkSession session;

        jedis.select(1);
        do {
            sessionKey = IdNameTools.genNew32BytesKey();
            sessionName = IdNameTools.makeKeyName(sessionKey);
        } while (jedis.exists(Settings.addSidBriefToName(sid,sessionName)));

        Map<String,String> sessionMap = new HashMap<>();
        sessionMap.put(SESSION_KEY,Hex.toHex(sessionKey));
        sessionMap.put(FID, fid);
        sessionMap.put(PUB_KEY, userPubKey);

        //Delete the old session of the requester.
        jedis.select(0);
        String oldSessionName = jedis.hget(Settings.addSidBriefToName(sid,Strings.FID_SESSION_NAME), fid);

        jedis.select(1);
        if (oldSessionName != null) jedis.del(Settings.addSidBriefToName(sid,sessionName));
        jedis.hmset(Settings.addSidBriefToName(sid,sessionName),sessionMap);

        //Set the new session
        session = new TalkSession();
        session.setKeyBytes(sessionKey);
        session.setKey(Hex.toHex(sessionKey));
        session.setFid(fid);
        session.setName(sessionName);
        session.setPubKey(userPubKey);

        if(sessionDays!=null) {
            long lifeSeconds = sessionDays * 86400;
            jedis.expire(sessionName, lifeSeconds);
            long expireTime = System.currentTimeMillis() + (lifeSeconds * 1000);
            session.setExpire(expireTime);
        }

        jedis.select(0);
        jedis.hset(Settings.addSidBriefToName(sid,Strings.FID_SESSION_NAME), fid, sessionName);
        return session;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public byte[] getKeyBytes() {
        return keyBytes;
    }

    public void setKeyBytes(byte[] keyBytes) {
        this.keyBytes = keyBytes;
    }

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    public Long getExpire() {
        return expire;
    }

    public void setExpire(Long expire) {
        this.expire = expire;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getPubKey() {
        return pubKey;
    }

    public void setPubKey(String pubKey) {
        this.pubKey = pubKey;
    }
}
