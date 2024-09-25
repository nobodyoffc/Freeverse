package startTalkServer;


import com.google.gson.Gson;
import constants.Strings;
import crypto.Hash;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;
import redis.clients.jedis.Jedis;
import settings.Settings;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import static constants.Values.TRUE;

public class Session {
    private String name;
    private transient byte[] key;
    private String fid;
    private Long expire;

    public static Session getSessionFromJedis(String fid, String sid, Jedis jedis) {
        String sessionName = jedis.hget(Settings.addSidBriefToName(sid,Strings.FID_SESSION_NAME), fid);
        if(sessionName==null)return null;
        String sessionStr = jedis.hget(Settings.addSidBriefToName(sid,Strings.NAME_SESSION),sessionName);
        if(sessionStr==null){
            jedis.hdel(Settings.addSidBriefToName(sid,Strings.FID_SESSION_NAME), fid);
            return null;
        }

        return new Gson().fromJson(sessionStr, Session.class);
    }

    public String toJson(){
        return JsonTools.toJson(this);
    }

    public String toNiceJson(){
        return JsonTools.toNiceJson(this);
    }

    public byte[] sign(byte[] dataBytes) {
        return sign(key,dataBytes);
    }
    public static byte[] sign(byte[] sessionKeyBytes, byte[] dataBytes) {
        return Hash.sha256x2(BytesTools.bytesMerger(dataBytes, sessionKeyBytes));
    }

    public String verifySign(String sign, byte[] requestBodyBytes) {
        if(sign==null)return "The sign is null.";
        if(requestBodyBytes==null)return "The byte array is null.";
        byte[] signBytes = BytesTools.bytesMerger(requestBodyBytes, key);
        String doubleSha256Hash = HexFormat.of().formatHex(Hash.sha256x2(signBytes));

        if(!sign.equals(doubleSha256Hash)){
            return "The sign of the request body should be: "+doubleSha256Hash;
        }
        return TRUE;
    }


    public static Session makeNewSession(String sid, Jedis jedis, String fid, Long sessionDays) {
        byte[] sessionKey;
        String sessionName;
        Session session;

        jedis.select(1);
        do {
            sessionKey = genNewKey();
            sessionName = makeKeyName(sessionKey);
        } while (!jedis.exists(Settings.addSidBriefToName(sid,sessionName)));

        Map<String,String> sessionMap = new HashMap<>();
        sessionMap.put("sessionKey",Hex.toHex(sessionKey));
        sessionMap.put("fid", fid);

        //Delete the old session of the requester.
        jedis.select(0);
        String oldSessionName = jedis.hget(Settings.addSidBriefToName(sid,Strings.FID_SESSION_NAME), fid);

        jedis.select(1);
        if (oldSessionName != null) jedis.del(Settings.addSidBriefToName(sid,sessionName));

        //Set the new session
        session = new Session();
        session.setKey(sessionKey);

        jedis.hmset(sessionName, sessionMap);
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

    private static byte[] genNewKey() {
        SecureRandom random = new SecureRandom();
        byte[] keyBytes = new byte[32];
        random.nextBytes(keyBytes);
        return keyBytes;
    }

    public static String makeKeyName(byte[] sessionKey) {
        return Hex.toHex(Hash.sha256(sessionKey)).substring(0,12);
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public byte[] getKey() {
        return key;
    }

    public void setKey(byte[] key) {
        this.key = key;
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

}
