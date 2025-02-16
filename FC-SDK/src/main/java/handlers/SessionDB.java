package handlers;

import fcData.FcEntity;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import fcData.FcSession;
import tools.FileTools;
import tools.JsonTools;

import org.mapdb.HTreeMap;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import java.io.IOException;
import java.util.concurrent.ConcurrentMap;
import java.util.List;

public class SessionDB {
    private static final String SESSION_DB = "session";
    private final String mainFid;
    private final String sid;
    private final String dbPath;
    
    private volatile DB db;
    private volatile HTreeMap<String, FcSession> nameSessionMap;
    private volatile ConcurrentMap<String, String> idNameMap;
    private volatile HTreeMap<String, List<String>> usedSessionsMap;
    private final Object lock = new Object();

    public SessionDB(String mainFid, String sid, String dbPath) {
        this.mainFid = mainFid;
        this.sid = sid;
        if(dbPath==null)dbPath = FileTools.getUserDir()+"/db/";
        if(!dbPath.endsWith("/"))dbPath += "/";
        this.dbPath = dbPath;
        initializeDb();
    }

    private void initializeDb() {
        if (!FileTools.checkDirOrMakeIt(dbPath)) return;
        if (db == null || db.isClosed()) {
            synchronized (lock) {
                if (db == null || db.isClosed()) {
                    String dbName = FileTools.makeFileName(mainFid, sid, SESSION_DB, constants.Strings.DOT_DB);
                    db = DBMaker.fileDB(dbPath+dbName)
                            .fileMmapEnable()
                            .checksumHeaderBypass()
                            .transactionEnable()
                            .make();
                    
                    Serializer<FcSession> sessionSerializer = new Serializer<FcSession>() {
                        @Override
                        public void serialize(DataOutput2 out, FcSession value) throws IOException {
                            byte[] bytes = value.toBytes();
                            out.packInt(bytes.length);
                            out.write(bytes);
                        }

                        @Override
                        public FcSession deserialize(DataInput2 input, int available) throws IOException {
                            int length = input.unpackInt();
                            byte[] bytes = new byte[length];
                            input.readFully(bytes);
                            return FcEntity.fromBytes(bytes, FcSession.class);
                        }
                    };
                    
                    nameSessionMap = db.hashMap("nameSession")
                            .keySerializer(Serializer.STRING)
                            .valueSerializer(sessionSerializer)
                            .createOrOpen();
                            
                    idNameMap = db.hashMap("idName")
                            .keySerializer(Serializer.STRING)
                            .valueSerializer(Serializer.STRING)
                            .createOrOpen();
                    
                    usedSessionsMap = db.hashMap("usedSessions")
                            .keySerializer(Serializer.STRING)
                            .valueSerializer(new Serializer<List<String>>() {
                                @Override
                                public void serialize(DataOutput2 out, List<String> value) throws IOException {
                                    String json = JsonTools.toJson(value);
                                    byte[] bytes = json.getBytes();
                                    out.packInt(bytes.length);
                                    out.write(bytes);
                                }

                                @Override
                                public List<String> deserialize(DataInput2 input, int available) throws IOException {
                                    int length = input.unpackInt();
                                    byte[] bytes = new byte[length];
                                    input.readFully(bytes);
                                    String json = new String(bytes);
                                    return JsonTools.listFromJson(json, String.class);
                                }
                            })
                            .createOrOpen();
                }
            }
        }
    }

    public void putSession(String sessionName, FcSession session) {
        nameSessionMap.put(sessionName, session);
        idNameMap.put(session.getId(), sessionName);
        db.commit();
    }

    public FcSession getSessionByName(String sessionName) {
        return nameSessionMap.get(sessionName);
    }

    public String getSessionNameById(String id) {
        return idNameMap.get(id);
    }

    public void removeSessionById(String id) {
        String sessionName = idNameMap.remove(id);
        if (sessionName != null) {
            nameSessionMap.remove(sessionName);
        }
        db.commit();
    }

    public void removeSessionByName(String sessionName) {
        FcSession session = nameSessionMap.remove(sessionName);
        if (session != null) {
            idNameMap.remove(session.getId());
        }
        db.commit();
    }

    public void close() {
        synchronized (lock) {
            if (db != null && !db.isClosed()) {
                db.close();
                db = null;
            }
        }
    }

    public FcSession getSessionById(String id) {
        String sessionName = idNameMap.get(id);
        if (sessionName == null) {
            return null;
        }
        return nameSessionMap.get(sessionName);
    }

    public String getNameById(String id) {
        return idNameMap.get(id);
    }

    public void putIdName(String id, String sessionName) {
        idNameMap.put(id, sessionName);
        db.commit();
    }

    public void removeIdName(String id) {
        idNameMap.remove(id);
        db.commit();
    }

    public void putUsedSessions(String key, List<String> sessionNames) {
        usedSessionsMap.put(key, sessionNames);
        db.commit();
    }

    public List<String> getUsedSessions(String key) {
        return usedSessionsMap.get(key);
    }

    public void removeUsedSessions(String key) {
        usedSessionsMap.remove(key);
        db.commit();
    }
} 