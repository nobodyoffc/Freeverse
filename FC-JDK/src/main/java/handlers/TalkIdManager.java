package handlers;

import config.Configure;
import data.fchData.Cid;
import config.Settings;
import data.fcData.TalkIdInfo;
import data.feipData.Group;
import data.feipData.Team;
import db.LocalDB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TalkIdManager extends Manager<TalkIdInfo> {
    private final Map<String, String> tempNameTalkIdMap;
    private final Map<String, String> talkIdTempNameMap;
    private final Map<String, TalkIdInfo> talkIdInfoCache;
    private String lastTalkId;

    public TalkIdManager(String myFid, String sid, String dbPath) {
        super(createSettings(myFid, sid, dbPath), ManagerType.TALK_ID, LocalDB.SortType.NO_SORT, TalkIdInfo.class, true, false);
        this.tempNameTalkIdMap = new ConcurrentHashMap<>();
        this.talkIdTempNameMap = new ConcurrentHashMap<>();
        this.talkIdInfoCache = new ConcurrentHashMap<>();
        this.lastTalkId = (String) localDB.getState("lastTalkId");
    }
//TODO
    private static Settings createSettings(String myFid, String sid, String dbPath) {
        Settings settings = new Settings((Configure) null, (String) null, null, null, null);
        settings.setMainFid(myFid);
        settings.setSid(sid);
        settings.setDbDir(dbPath);
        return settings;
    }

    public TalkIdManager(Settings settings) {
        super(settings, ManagerType.TALK_ID, LocalDB.SortType.NO_SORT, TalkIdInfo.class, true, false);
        this.tempNameTalkIdMap = new ConcurrentHashMap<>();
        this.talkIdTempNameMap = new ConcurrentHashMap<>();
        this.talkIdInfoCache = new ConcurrentHashMap<>();
        this.lastTalkId = (String) localDB.getState("lastTalkId");
    }   

    public String getLastTalkId() {
        if(lastTalkId == null) {
            lastTalkId = (String) localDB.getState("lastTalkId");
        }
        return lastTalkId;
    }

    public void setLastTalkId(String id) {
        this.lastTalkId = id;
    }

    public String setTempName(String talkId) {
        String existingTempName = talkIdTempNameMap.get(talkId);
        if (existingTempName != null) {
            return existingTempName;
        }

        String tempName;
        do {
            tempName = generateTempName();
        } while (tempNameTalkIdMap.containsKey(tempName));

        tempNameTalkIdMap.put(tempName, talkId);
        talkIdTempNameMap.put(talkId, tempName);
        return tempName;
    }

    private String generateTempName() {
        String chars = "abcdefghijklmnopqrstuvwxyz";
        StringBuilder tempName = new StringBuilder();
        Random random = new Random();
        
        for (int i = 0; i < 4; i++) {
            tempName.append(chars.charAt(random.nextInt(chars.length())));
        }
        return tempName.toString();
    }

    public TalkIdInfo fromCid(Cid cid) {
        String id = cid.getId();
        TalkIdInfo cached = talkIdInfoCache.get(id);
        if (cached != null) {
            setLastTalkId(id);
            return cached;
        }

        TalkIdInfo info = TalkIdInfo.fromCidInfo(cid);
        talkIdInfoCache.put(id, info);
        localDB.put(id, info);
        setLastTalkId(id);
        return info;
    }

    public TalkIdInfo fromGroup(Group group) {
        String id = group.getId();
        TalkIdInfo cached = talkIdInfoCache.get(id);
        if (cached != null) {
            setLastTalkId(id);
            return cached;
        }

        TalkIdInfo info = TalkIdInfo.fromGroup(group);
        talkIdInfoCache.put(id, info);
        localDB.put(id, info);
        setLastTalkId(id);
        return info;
    }

    public TalkIdInfo fromTeam(Team team) {
        String id = team.getId();
        TalkIdInfo cached = talkIdInfoCache.get(id);
        if (cached != null) {
            setLastTalkId(id);
            return cached;
        }

        TalkIdInfo info = TalkIdInfo.fromTeam(team);
        talkIdInfoCache.put(id, info);
        localDB.put(id, info);
        setLastTalkId(id);
        return info;
    }

    public TalkIdInfo get(String id) {
        // Check cache first
        TalkIdInfo cached = talkIdInfoCache.get(id);
        if (cached != null) {
            setLastTalkId(id);
            return cached;
        }

        // If not in cache, get from DB
        TalkIdInfo info = localDB.get(id);
        if (info != null) {
            talkIdInfoCache.put(id, info);
            setLastTalkId(id);
        }
        return info;
    }

    @Override
    public void close() {
        localDB.putState("lastTalkId", lastTalkId);
        talkIdInfoCache.clear();
        super.close();
    }

    public List<TalkIdInfo> search(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        String term = searchTerm.toLowerCase().trim();
        Set<TalkIdInfo> results = new HashSet<>();

        // Search in cache first
        for (TalkIdInfo info : talkIdInfoCache.values()) {
            if (TalkIdInfo.matchesTalkIdInfo(info, term)) {
                results.add(info);
            }
        }

        // Search in DB for any items not in cache
        Map<String, TalkIdInfo> allObjects = localDB.getAll();
        for (Map.Entry<String, TalkIdInfo> entry : allObjects.entrySet()) {
            if (!talkIdInfoCache.containsKey(entry.getKey())) {
                TalkIdInfo info = entry.getValue();
                if (TalkIdInfo.matchesTalkIdInfo(info, term)) {
                    results.add(info);
                    // Add to cache for future use
                    talkIdInfoCache.put(entry.getKey(), info);
                }
            }
        }

        return new ArrayList<>(results);
    }

    public void put(String id, TalkIdInfo info) {
        talkIdInfoCache.put(id, info);
        localDB.put(id, info);
        setLastTalkId(id);
    }

    public String getTalkIdFromTempName(String tempName) {
        return tempNameTalkIdMap.get(tempName);
    }

    public String getTempNameFromTalkId(String talkId) {
        return talkIdTempNameMap.get(talkId);
    }

    public boolean hasTempName(String tempName) {
        return tempNameTalkIdMap.containsKey(tempName);
    }

    public boolean hasTempNameForTalkId(String talkId) {
        return talkIdTempNameMap.containsKey(talkId);
    }
} 