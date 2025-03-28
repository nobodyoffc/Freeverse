package handlers;

import appTools.Settings;
import fcData.TalkIdInfo;
import fch.fchData.Cid;
import feip.feipData.Group;
import feip.feipData.Team;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TalkIdHandler extends Handler {
    private final TalkIdDB talkIdDB;
    private final Map<String, TalkIdInfo> talkIdInfoCache;
    private final Map<String, String> tempNameTalkIdMap;
    private final Map<String, String> talkIdTempNameMap;
    private String lastTalkId;

    public TalkIdHandler(String myFid, String sid, String dbPath) {
        this.talkIdDB = new TalkIdDB(myFid, sid, dbPath);
        this.talkIdInfoCache = new ConcurrentHashMap<>();
        this.tempNameTalkIdMap = new ConcurrentHashMap<>();
        this.talkIdTempNameMap = new ConcurrentHashMap<>();
        this.lastTalkId = talkIdDB.getLastTalkId();
    }

    public TalkIdHandler(Settings settings){
        this.talkIdDB = new TalkIdDB(settings.getMainFid(), settings.getSid(), settings.getDbDir());
        this.talkIdInfoCache = new ConcurrentHashMap<>();
        this.tempNameTalkIdMap = new ConcurrentHashMap<>();
        this.talkIdTempNameMap = new ConcurrentHashMap<>();
        this.lastTalkId = talkIdDB.getLastTalkId();
    }   

    public String getLastTalkId() {
        if(lastTalkId == null) {
            lastTalkId = talkIdDB.getLastTalkId();
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
        talkIdDB.put(id, info);
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
        talkIdDB.put(id, info);
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
        talkIdDB.put(id, info);
        setLastTalkId(id);
        return info;
    }

    public TalkIdInfo get(String id) {
        TalkIdInfo cached = talkIdInfoCache.get(id);
        if (cached != null) {
            setLastTalkId(id);
            return cached;
        }

        TalkIdInfo info = talkIdDB.get(id);
        if (info != null) {
            talkIdInfoCache.put(id, info);
            setLastTalkId(id);
        }
        return info;
    }

    public void close() {
        talkIdDB.setLastTalkId(lastTalkId);
        talkIdDB.close();
    }

    public List<TalkIdInfo> search(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        String term = searchTerm.toLowerCase().trim();
        Set<TalkIdInfo> results = new HashSet<>();

        // Search in cache
        for (TalkIdInfo info : talkIdInfoCache.values()) {
            if (TalkIdInfo.matchesTalkIdInfo(info, term)) {
                results.add(info);
            }
        }

        // Search in DB
        List<TalkIdInfo> dbResults = talkIdDB.search(term);
        results.addAll(dbResults);

        return new ArrayList<>(results);
    }

    public void put(String id, TalkIdInfo info) {
        talkIdInfoCache.put(id, info);
        talkIdDB.put(id, info);
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