package handlers;

import crypto.CryptoDataByte;
import crypto.Encryptor;
import fcData.AlgorithmId;
import fcData.FcSession;
import feip.feipData.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import utils.FileUtils;
import utils.Hex;
import utils.JsonUtils;
import utils.MapQueue;

import java.util.Map;

import appTools.Settings;

import java.util.List;
import java.util.Collections;
import java.util.HashMap;
import java.util.ArrayList;

import utils.IdNameUtils;
/**
 * FcSessionClient is a client for managing sessions.
 * There are two ways to store sessions: 1) MapQueue in memory and MapDB for persistence; 2) JedisPool in Redis.
 */
public class SessionHandler extends Handler<FcSession> {
    private static final String ID_SESSION_NAME = "idSessionName";
    private static final String SESSIONS = "sessions";
    private final JedisPool jedisPool;
    // private final String dbName;
    private final String jedisNameSessionKey;
    private final String jedisIdNameKey;
    private final SessionDB sessionDB;
    private final MapQueue<String, FcSession> nameSessionMap;
    private final MapQueue<String, String> idNameMap;
    private final String mainFid;
    private final String sid;
    private final boolean useRedis;
    private final String jedisUsedSessionsKey;
    private final Map<String, List<String>> usedSessionsMap;
    
        
    public SessionHandler(String mainFid, String sid, JedisPool jedisPool, String dbPath) {
        this.mainFid = mainFid;
        this.sid = sid;
        this.jedisPool = jedisPool;
        this.useRedis = (jedisPool != null);
        
        
        this.jedisNameSessionKey = useRedis ? FileUtils.makeFileName(null, sid, SESSIONS, null) : null;
        this.jedisIdNameKey = useRedis ? FileUtils.makeFileName(null, sid, ID_SESSION_NAME, null) : null;
        this.sessionDB = useRedis ? null : new SessionDB(mainFid, sid, dbPath);
        this.nameSessionMap = useRedis ? null : new MapQueue<>(1000);
        this.idNameMap = useRedis ? null : new MapQueue<>(1000);
        this.jedisUsedSessionsKey = useRedis ? FileUtils.makeFileName(null, sid, "usedSessions", null) : null;
        this.usedSessionsMap = useRedis ? null : new HashMap<>();
    }

    public SessionHandler(Settings settings){
        this.mainFid = settings.getMainFid();
        this.sid = settings.getSid();
        this.jedisPool = (JedisPool)settings.getClient(Service.ServiceType.REDIS);
        this.useRedis = (jedisPool != null);
        // this.dbName = settings.getSid()==null? FileTools.makeFileName(mainFid, sid, SESSIONS, constants.Strings.DOT_DB) 
        //     : FileTools.makeFileName(null, sid, SESSIONS, constants.Strings.DOT_DB);
        this.jedisNameSessionKey = useRedis ? FileUtils.makeFileName(null, sid, SESSIONS, null) : null;
        this.jedisIdNameKey = useRedis ? FileUtils.makeFileName(null, sid, ID_SESSION_NAME, null) : null;
        this.sessionDB = useRedis ? null : new SessionDB(mainFid, sid, settings.getDbDir());
        this.nameSessionMap = useRedis ? null : new MapQueue<>(1000);
        this.idNameMap = useRedis ? null : new MapQueue<>(1000);
        this.jedisUsedSessionsKey = useRedis ? FileUtils.makeFileName(null, sid, "usedSessions", null) : null;
        this.usedSessionsMap = useRedis ? null : new HashMap<>();
    }     

    public FcSession getSessionByName(String sessionName) {
        if(sessionName==null)return null;
        FcSession session = null;
        if (!useRedis) {
            // Try cache first
            session = nameSessionMap.get(sessionName);
            if(session!=null) return session;
            session = sessionDB.getSessionByName(sessionName);
            if (session != null) {
                nameSessionMap.put(sessionName, session);
                idNameMap.put(session.getId(), sessionName);
                return session;
            }
            return null;
        }

        // Try Redis
        try (var jedis = jedisPool.getResource()) {
            return getSessionFromJedisWithKeyName(sessionName, jedis);
        }catch (Exception e){
            return null;
        }
    }

    public FcSession getSessionFromJedisWithId(String id, Jedis jedis) {
        String sessionName = jedis.hget(jedisIdNameKey, id);
        if(sessionName==null) return null;
        
        FcSession session = getSessionFromJedisWithKeyName(sessionName, jedis);
        if(session==null){
            jedis.hdel(jedisIdNameKey, id);
            return null;
        }
        return session;
    }

    public FcSession getSessionFromJedisWithKeyName(String sessionName, Jedis jedis) {
        if(sessionName==null) return null;

        String sessionJson = jedis.hget(jedisNameSessionKey, sessionName);
        if(sessionJson==null) return null;

        return FcSession.fromJson(sessionJson,FcSession.class);
    }

    public void putSession(FcSession session) {
        String sessionName = session.getName();
        String fid = session.getId();
        
        if (!useRedis) {
            // Update cache
            nameSessionMap.put(sessionName, session);
            idNameMap.put(fid, sessionName);
            sessionDB.putSession(sessionName, session);
            sessionDB.putIdName(fid,sessionName);
            
            // Add to usedSessions
            List<String> sessionNames = usedSessionsMap.computeIfAbsent(fid, k -> new ArrayList<>());
            if (!sessionNames.contains(sessionName)) {
                sessionNames.add(sessionName);
                sessionDB.putUsedSessions(fid, sessionNames);
            }
            return;
        }

        // Update Redis
        try (var jedis = jedisPool.getResource()) {
            jedis.hset(jedisNameSessionKey, Map.of(sessionName, session.toJson()));
            jedis.hset(jedisIdNameKey, fid, sessionName);
            
            // Add to usedSessions in Redis
            String sessionsJson = jedis.hget(jedisUsedSessionsKey, fid);
            List<String> sessionNames = sessionsJson != null ? 
                JsonUtils.listFromJson(sessionsJson, String.class) : new ArrayList<>();
            if (!sessionNames.contains(sessionName)) {
                sessionNames.add(sessionName);
                jedis.hset(jedisUsedSessionsKey, fid, JsonUtils.toJson(sessionNames));
            }
        }
    }

    
    public FcSession getSessionById(String id) {
        // Try cache first
        String sessionName = getNameById(id);
        return getSessionByName(sessionName);
    }
    public String getNameById(String id) {
        if (!useRedis) {
            String sessionName = idNameMap.get(id);
            if (sessionName != null) 
                return sessionName;
            sessionName = sessionDB.getNameById(id);
            if (sessionName != null) {
                idNameMap.put(id, sessionName);
                return sessionName;
            }
            return null;
        }

        // Try Redis
        try (var jedis = jedisPool.getResource()) {
            return jedis.hget(jedisIdNameKey, id);
        }
    }

    public void putFidName(String id, String sessionName) {
        if (!useRedis) {
            idNameMap.put(id, sessionName);
            sessionDB.putIdName(id, sessionName);
            return;
        }

        // Update Redis
        try (var jedis = jedisPool.getResource()) {
            jedis.hset(jedisIdNameKey, id, sessionName);
        } 
    }

    public void removeSessionByName(String sessionName) {
        if (!useRedis) {
            FcSession session = nameSessionMap.remove(sessionName);
            if (session != null) {
                String fid = session.getId();
                idNameMap.remove(fid);
                sessionDB.removeSessionByName(sessionName);
                
                // Remove from usedSessions
                List<String> sessionNames = usedSessionsMap.get(fid);
                if (sessionNames != null) {
                    sessionNames.remove(sessionName);
                    if (sessionNames.isEmpty()) {
                        usedSessionsMap.remove(fid);
                        sessionDB.removeUsedSessions(fid);
                    } else {
                        sessionDB.putUsedSessions(fid, sessionNames);
                    }
                }
            }
            return;
        }

        // Remove from Redis
        try (var jedis = jedisPool.getResource()) {
            String fid = null;
            // Find the fid for this session
            Map<String, String> idNameEntries = jedis.hgetAll(jedisIdNameKey);
            for (Map.Entry<String, String> entry : idNameEntries.entrySet()) {
                if (sessionName.equals(entry.getValue())) {
                    fid = entry.getKey();
                    break;
                }
            }
            
            if (fid != null) {
                jedis.hdel(jedisNameSessionKey, sessionName);
                jedis.hdel(jedisIdNameKey, fid);
                
                // Update usedSessions
                String sessionsJson = jedis.hget(jedisUsedSessionsKey, fid);
                if (sessionsJson != null) {
                    List<String> sessionNames = JsonUtils.listFromJson(sessionsJson, String.class);
                    sessionNames.remove(sessionName);
                    if (sessionNames.isEmpty()) {
                        jedis.hdel(jedisUsedSessionsKey, fid);
                    } else {
                        jedis.hset(jedisUsedSessionsKey, fid, JsonUtils.toJson(sessionNames));
                    }
                }
            }
        }
    }

    public void removeSessionById(String id) {
        String sessionName = getNameById(id);
        if(sessionName==null)return;
        
        if (!useRedis) {
            nameSessionMap.remove(sessionName);
            idNameMap.remove(id);
            sessionDB.removeSessionById(id);
            sessionDB.removeIdName(id);
            return;
        }

        // Remove from Redis
        try (var jedis = jedisPool.getResource()) {
            jedis.hdel(jedisNameSessionKey, sessionName);
            jedis.hdel(jedisIdNameKey, id);
        } 
    }

    public void removeFidName(String id) {
        if (!useRedis) {
            idNameMap.remove(id);
            sessionDB.removeIdName(id);
            return;
        }

        // Remove from Redis
        try (var jedis = jedisPool.getResource()) {
            jedis.hdel(jedisIdNameKey, id);
        } 
    }

    public FcSession addNewSession(String fid, String userPubKey) {
        byte[] sessionKey;
        String sessionName;
        FcSession session = null;

        sessionKey = IdNameUtils.genNew32BytesKey();
        sessionName = IdNameUtils.makeKeyName(sessionKey);

        //Set the new session
        session = new FcSession();
        session.setKeyBytes(sessionKey);
        session.setKey(Hex.toHex(sessionKey));
        session.setId(fid);
        session.setName(sessionName);
        session.setPubKey(userPubKey);
        session.setKeyCipher(makeKeyCipher(sessionKey,userPubKey));

        if (!useRedis) {
            String oldSessionName = idNameMap.get(fid);
            if(oldSessionName != null) {
                nameSessionMap.remove(oldSessionName);
                idNameMap.remove(fid);
                sessionDB.removeSessionByName(oldSessionName);
                sessionDB.removeIdName(fid);
            }
            idNameMap.put(fid, sessionName);
            nameSessionMap.put(sessionName, session);
            sessionDB.putIdName(fid, sessionName);
            sessionDB.putSession(sessionName, session);
            return session;
        }

        try (var jedis = jedisPool.getResource()) {
            String oldSessionName = jedis.hget(jedisIdNameKey, fid);
            if(oldSessionName != null) {
                jedis.hdel(jedisNameSessionKey, oldSessionName);
                jedis.hdel(jedisIdNameKey, fid);
            }
            jedis.hset(jedisIdNameKey, fid, sessionName);
            jedis.hset(jedisNameSessionKey, sessionName, session.toJson());
        }
        return session;
    }

    private String makeKeyCipher(byte[] sessionKey, String userPubKey) {
        if(sessionKey==null || userPubKey==null)return null;
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte = encryptor.encryptByAsyOneWay(sessionKey,Hex.fromHex(userPubKey));
        if(cryptoDataByte==null)return null;
        return cryptoDataByte.toJson();
    }


    public void close() {
        if (!useRedis) {
            sessionDB.close();
        }
    }

    // public String getDbName() {
    //     return dbName;
    // }

    public String getJedisNameSessionKey() {
        return jedisNameSessionKey;
    }

    public String getJedisIdNameKey() {
        return jedisIdNameKey;
    }

    public String getMainFid() {
        return mainFid;
    }

    public String getSid() {
        return sid;
    }

    public synchronized void putUsedSessions(String key, List<String> sessionNames) {
        if (key == null || sessionNames == null) return;
        
        // Create an unmodifiable copy of the sessions list for thread safety
        List<String> sessionNamesCopy = Collections.unmodifiableList(new ArrayList<>(sessionNames));
        
        if (!useRedis) {
            usedSessionsMap.put(key, sessionNamesCopy);
            synchronized (sessionDB) {
                sessionDB.putUsedSessions(key, sessionNamesCopy);
            }
            return;
        }

        // Update Redis
        try (var jedis = jedisPool.getResource()) {
            jedis.hset(jedisUsedSessionsKey, key, JsonUtils.toJson(sessionNamesCopy));
        }
    }

    public List<FcSession> getUsedSessions(String key) {
        if (key == null) return null;
        
        if (!useRedis) {
            List<String> sessionNames = usedSessionsMap.get(key);
            if (sessionNames == null) {
                synchronized (sessionDB) {
                    sessionNames = sessionDB.getUsedSessions(key);
                }
                if (sessionNames != null) {
                    usedSessionsMap.put(key, sessionNames);
                }
            }
            
            if (sessionNames == null) return null;
            
            // Convert session names to FcSession objects
            List<FcSession> sessions = new ArrayList<>();
            for (String name : sessionNames) {
                FcSession session = getSessionByName(name);
                if (session != null) {
                    sessions.add(session);
                }
            }
            return Collections.unmodifiableList(sessions);
        }

        // Try Redis
        try (var jedis = jedisPool.getResource()) {
            String sessionsJson = jedis.hget(jedisUsedSessionsKey, key);
            if (sessionsJson == null) return null;
            
            List<String> sessionNames = JsonUtils.listFromJson(sessionsJson, String.class);
            List<FcSession> sessions = new ArrayList<>();
            for (String name : sessionNames) {
                FcSession session = getSessionByName(name);
                if (session != null) {
                    sessions.add(session);
                }
            }
            return Collections.unmodifiableList(sessions);
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized void removeUsedSessions(String key) {
        if (key == null) return;
        
        if (!useRedis) {
            usedSessionsMap.remove(key);
            synchronized (sessionDB) {
                sessionDB.removeUsedSessions(key);
            }
            return;
        }

        // Remove from Redis (Redis operations are already thread-safe)
        try (var jedis = jedisPool.getResource()) {
            jedis.hdel(jedisUsedSessionsKey, key);
        }
    }

}
