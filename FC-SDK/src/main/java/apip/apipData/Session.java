package apip.apipData;


import constants.Strings;
import crypto.Hash;
import crypto.old.EccAes256K1P7;
import crypto.CryptoDataStr;
import crypto.EncryptType;
import fcData.FcReplier;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import server.Settings;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import static constants.Strings.SESSION_KEY;
import static constants.Values.TRUE;

public class Session {
    private String sessionName;
    private String sessionKey;
    private String fid;
    private Long expireTime;
    private String sessionKeyCipher;

    public static Session getSessionFromJedis(String sid, FcReplier replier, String pubKey, Jedis jedis, String fid, long sessionDays) {
        jedis.select(0);
        String sessionName = jedis.hget(Settings.addSidBriefToName(sid,Strings.FID_SESSION_NAME), fid);
        Session session =new Session();
        session.setFid(fid);
        session.setSessionName(sessionName);
        jedis.select(1);
        String sessionKey = jedis.hget(sessionName, SESSION_KEY);
        if (sessionKey != null) {
            long expireMillis = jedis.ttl(sessionName);
            if (expireMillis > 0) {
                long expireTime = System.currentTimeMillis() + expireMillis * 1000;
                session.setExpireTime(expireTime);
            } else {
                session.setExpireTime(expireMillis);
            }
            String sessionKeyCipher = EccAes256K1P7.encryptWithPubKey(sessionKey.getBytes(), Hex.fromHex(pubKey));
            session.setSessionKeyCipher(sessionKeyCipher);
        } else {
            try {
                session = new Session().makeSession(sid, jedis, fid, sessionDays);
            } catch (Exception e) {
                replier.replyOtherError( "Some thing wrong when making sessionKey.\n" + e.getMessage(),null , jedis);
                return null;
            }
        }
        jedis.select(0);
        return session;
    }

    @Nullable
    public static Session makeNewSession(String sid, FcReplier replier, String pubKey, Jedis jedis, String fid, long sessionDays) {
        Session session;
        try {
            session = new Session().makeSession(sid, jedis, fid, sessionDays);
            String sessionKeyCipher = EccAes256K1P7.encryptWithPubKey(session.getSessionKey().getBytes(), Hex.fromHex(pubKey));
            session.setSessionKeyCipher(sessionKeyCipher);
//            session.setSessionKey(null);
        } catch (Exception e) {
            replier.replyOtherError("Some thing wrong when making sessionKey.\n" + e.getMessage(),null, jedis);
            return null;
        }
        return session;
    }

    public String toJson(){
        return JsonTools.toJson(this);
    }

    public String toNiceJson(){
        return JsonTools.toNiceJson(this);
    }

    public static String getSessionKeySign(byte[] sessionKeyBytes, byte[] dataBytes) {
        return HexFormat.of().formatHex(Hash.sha256x2(BytesTools.bytesMerger(dataBytes, sessionKeyBytes)));
    }

    public String checkSign(String sign, byte[] requestBodyBytes) {
        if(sign==null)return "The sign is null.";
        if(requestBodyBytes==null)return "The byte array is null.";
        byte[] signBytes = BytesTools.bytesMerger(requestBodyBytes, BytesTools.hexToByteArray(sessionKey));
        String doubleSha256Hash = HexFormat.of().formatHex(Hash.sha256x2(signBytes));

        if(!sign.equals(doubleSha256Hash)){
            return "The sign of the request body should be: "+doubleSha256Hash;
        }
        return TRUE;
    }
    public Session makeSession(String sid,Jedis jedis, String fid, long sessionDays) {
        String sessionKey;
        String sessionName;
        Session session;

        jedis.select(1);
        do {
            sessionKey = genSessionKey();
            sessionName = makeSessionName(sessionKey);
        } while (jedis.exists(sessionName));
        session = new Session();
        Map<String,String> sessionMap = new HashMap<>();
        sessionMap.put("sessionKey",sessionKey);
        sessionMap.put("fid", fid);

    //Delete the old session of the requester.
        jedis.select(0);
        String oldSessionName = jedis.hget(Settings.addSidBriefToName(sid,Strings.FID_SESSION_NAME), fid);

        jedis.select(1);
        if (oldSessionName != null) jedis.del(oldSessionName);

        //Set the new session
        jedis.hmset(sessionName, sessionMap);

        long lifeSeconds = sessionDays * 86400;

        jedis.expire(sessionName, lifeSeconds);

        session.setSessionKey(sessionKey);
        long expireTime = System.currentTimeMillis() + (lifeSeconds * 1000);

        session.setExpireTime(expireTime);

        jedis.select(0);
        jedis.hset(Settings.addSidBriefToName(sid,Strings.FID_SESSION_NAME), fid, sessionName);
        return session;
    }

    private String genSessionKey() {
        SecureRandom random = new SecureRandom();
        byte[] keyBytes = new byte[32];
        random.nextBytes(keyBytes);
        return BytesTools.bytesToHexStringBE(keyBytes);
    }

    public static String makeSessionName(String sessionKey) {
        return sessionKey.substring(0,12);
    }

    public static String encryptSessionKey(String sessionKey, String pubKey, String sign) throws Exception {
        EccAes256K1P7 ecc = new EccAes256K1P7();
        CryptoDataStr cryptoDataStr = new CryptoDataStr(EncryptType.AsyOneWay, sessionKey,pubKey);
        ecc.encrypt(cryptoDataStr);
        if(cryptoDataStr.getMessage()!=null){
            return "Error:"+ cryptoDataStr.getMessage();
        }
        return cryptoDataStr.toJson();
    }

    public String getSessionName() {
        return sessionName;
    }

    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    public Long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(Long expireTime) {
        this.expireTime = expireTime;
    }

    public String getSessionKeyCipher() {
        return sessionKeyCipher;
    }

    public void setSessionKeyCipher(String sessionKeyCipher) {
        this.sessionKeyCipher = sessionKeyCipher;
    }

}
