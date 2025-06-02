package handlers;

import config.Settings;
import core.crypto.CryptoDataByte;
import core.crypto.Encryptor;
import db.LocalDB;
import data.fcData.AlgorithmId;
import data.fcData.FcSession;
import data.feipData.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import utils.Hex;
import utils.JsonUtils;

import java.util.*;

import utils.IdNameUtils;

/**
 * FcSessionClient is a client for managing sessions.
 * There are two ways to store sessions: 1) LevelDB for persistence; 2) JedisPool in Redis.
 */
public class SessionManager extends Manager<FcSession> {
    private static final String ID_SESSION_NAME = "idSessionName";
    private static final String SESSIONS = "sessions";
    private static final String USED_SESSIONS = "usedSessions";
    private static final String USER_ID_SESSION_NAME_MAP = "userIdSessionName";
    
    private final JedisPool jedisPool;
    private final String jedisNameSessionKey;
    private final String jedisUserIdNameKey;
    private final String jedisUsedSessionsKey;
    private final boolean useRedis;

    public SessionManager(Settings settings) {
        super(settings, ManagerType.SESSION, LocalDB.SortType.UPDATE_ORDER, FcSession.class, true, false);
        this.jedisPool = (JedisPool)settings.getClient(Service.ServiceType.REDIS);
        this.useRedis = (jedisPool != null);
        
        if (useRedis) {
            this.jedisNameSessionKey = IdNameUtils.makeKeyName(null,settings.getSid(),SESSIONS,null);
            this.jedisUserIdNameKey = IdNameUtils.makeKeyName(null, settings.getSid(), ID_SESSION_NAME, null);
            this.jedisUsedSessionsKey = IdNameUtils.makeKeyName(null, settings.getSid(), USED_SESSIONS, null);
        } else {
            this.jedisNameSessionKey = null;
            this.jedisUserIdNameKey = null;
            this.jedisUsedSessionsKey = null;
            
            // Create maps for LevelDB
            createMap(USER_ID_SESSION_NAME_MAP);
            createMap(USED_SESSIONS);
        }
    }

    public FcSession getSessionByName(String sessionName) {
        if (sessionName == null) return null;
        
        if (!useRedis) {
            return localDB.get(sessionName);
        }

        // Try Redis
        try (var jedis = jedisPool.getResource()) {
            return getSessionFromJedisWithKeyName(sessionName, jedis);
        } catch (Exception e) {
            return null;
        }
    }

    public FcSession getSessionFromJedisWithKeyName(String sessionName, Jedis jedis) {
        if (sessionName == null) return null;

        String sessionJson = jedis.hget(jedisNameSessionKey, sessionName);
        if (sessionJson == null) return null;

        return FcSession.fromJson(sessionJson, FcSession.class);
    }

    public void putSession(FcSession session) {
        if(session==null)return;
        if(session.getId()==null)
            if(session.getKey()!=null||session.getKeyBytes()!=null)session.makeId();

        String sessionName = session.getId();
        String fid = session.getUserId();
        
        if (!useRedis) {
            localDB.put(sessionName, session);
            localDB.putInMap(USER_ID_SESSION_NAME_MAP, fid, sessionName);
            
            // Add to usedSessions
            List<String> sessionNames = localDB.getFromMap(USED_SESSIONS, fid);
            if (sessionNames == null) {
                sessionNames = new ArrayList<>();
            }
            if (!sessionNames.contains(sessionName)) {
                sessionNames.add(sessionName);
                localDB.putInMap(USED_SESSIONS, fid, sessionNames);
            }
            return;
        }

        // Update Redis
        try (var jedis = jedisPool.getResource()) {
            jedis.hset(jedisNameSessionKey, Map.of(sessionName, session.toJson()));
            jedis.hset(jedisUserIdNameKey, fid, sessionName);
            
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

    public FcSession getSessionByUserId(String userId) {
        String sessionName = getNameByUserId(userId);
        return getSessionByName(sessionName);
    }

    public String getNameByUserId(String userId) {
        if (!useRedis) {
            return localDB.getFromMap(USER_ID_SESSION_NAME_MAP, userId);
        }

        // Try Redis
        try (var jedis = jedisPool.getResource()) {
            return jedis.hget(jedisUserIdNameKey, userId);
        }
    }

    public void putUserIdSessionId(String userId, String sessionName) {
        if (!useRedis) {
            localDB.putInMap(USER_ID_SESSION_NAME_MAP, userId, sessionName);
            return;
        }

        // Update Redis
        try (var jedis = jedisPool.getResource()) {
            jedis.hset(jedisUserIdNameKey, userId, sessionName);
        }
    }

    public void removeSession(String sessionName) {
        if (!useRedis) {
            FcSession session = localDB.get(sessionName);
            if (session != null) {
                String fid = session.getUserId();
                remove(sessionName);
                localDB.removeFromMap(USER_ID_SESSION_NAME_MAP, fid);
                
                // Remove from usedSessions
                List<String> sessionNames = localDB.getFromMap(USED_SESSIONS, fid);
                if (sessionNames != null) {
                    sessionNames.remove(sessionName);
                    if (sessionNames.isEmpty()) {
                        localDB.removeFromMap(USED_SESSIONS, fid);
                    } else {
                        localDB.putInMap(USED_SESSIONS, fid, sessionNames);
                    }
                }
            }
            return;
        }

        // Remove from Redis
        try (var jedis = jedisPool.getResource()) {
            String fid = null;
            // Find the fid for this session
            Map<String, String> userIdNameEntries = jedis.hgetAll(jedisUserIdNameKey);
            for (Map.Entry<String, String> entry : userIdNameEntries.entrySet()) {
                if (sessionName.equals(entry.getValue())) {
                    fid = entry.getKey();
                    break;
                }
            }
            
            if (fid != null) {
                jedis.hdel(jedisNameSessionKey, sessionName);
                jedis.hdel(jedisUserIdNameKey, fid);
                
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

    public void removeSessionByUserId(String userId) {
        String sessionName = getNameByUserId(userId);
        if (sessionName == null) return;
        
        if (!useRedis) {
            remove(sessionName);
            localDB.removeFromMap(USER_ID_SESSION_NAME_MAP, userId);
            return;
        }

        // Remove from Redis
        try (var jedis = jedisPool.getResource()) {
            jedis.hdel(jedisNameSessionKey, sessionName);
            jedis.hdel(jedisUserIdNameKey, userId);
        }
    }

    public void removeFidName(String userId) {
        if (!useRedis) {
            localDB.removeFromMap(USER_ID_SESSION_NAME_MAP, userId);
            return;
        }

        // Remove from Redis
        try (var jedis = jedisPool.getResource()) {
            jedis.hdel(jedisUserIdNameKey, userId);
        }
    }

    public FcSession addNewSession(String fid, String userPubKey) {
        byte[] sessionKey = IdNameUtils.genNew32BytesKey();
        String sessionName = IdNameUtils.makeKeyName(sessionKey);

        // Set the new session
        FcSession session = new FcSession();
        session.setKeyBytes(sessionKey);
        session.setKey(Hex.toHex(sessionKey));
        session.setUserId(fid);
        session.setId(sessionName);
        session.setPubkey(userPubKey);
        session.setKeyCipher(makeKeyCipher(sessionKey, userPubKey));

        if (!useRedis) {
            String oldSessionName = localDB.getFromMap(USER_ID_SESSION_NAME_MAP, fid);
            if (oldSessionName != null) {
                remove(oldSessionName);
                localDB.removeFromMap(USER_ID_SESSION_NAME_MAP, fid);
            }
            localDB.putInMap(USER_ID_SESSION_NAME_MAP, fid, sessionName);
            localDB.put(sessionName, session);
            return session;
        }

        try (var jedis = jedisPool.getResource()) {
            String oldSessionName = jedis.hget(jedisUserIdNameKey, fid);
            if (oldSessionName != null) {
                jedis.hdel(jedisNameSessionKey, oldSessionName);
                jedis.hdel(jedisUserIdNameKey, fid);
            }
            jedis.hset(jedisUserIdNameKey, fid, sessionName);
            jedis.hset(jedisNameSessionKey, sessionName, session.toJson());
        }
        return session;
    }

    private String makeKeyCipher(byte[] sessionKey, String userPubKey) {
        if (sessionKey == null || userPubKey == null) return null;
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte = encryptor.encryptByAsyOneWay(sessionKey, Hex.fromHex(userPubKey));
        if (cryptoDataByte == null) return null;
        return cryptoDataByte.toJson();
    }

    public String getJedisNameSessionKey() {
        return jedisNameSessionKey;
    }

    public String getJedisUserIdNameKey() {
        return jedisUserIdNameKey;
    }

    public synchronized void putUsedSessions(String key, List<String> sessionNames) {
        if (key == null || sessionNames == null) return;
        
        // Create an unmodifiable copy of the sessions list for thread safety
        List<String> sessionNamesCopy = Collections.unmodifiableList(new ArrayList<>(sessionNames));
        
        if (!useRedis) {
            localDB.putInMap(USED_SESSIONS, key, sessionNamesCopy);
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
            List<String> sessionNames = localDB.getFromMap(USED_SESSIONS, key);
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
            localDB.removeFromMap(USED_SESSIONS, key);
            return;
        }

        // Remove from Redis
        try (var jedis = jedisPool.getResource()) {
            jedis.hdel(jedisUsedSessionsKey, key);
        }
    }
}
