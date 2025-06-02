package handlers;

import clients.ApipClient;
import data.feipData.Service;
import feature.avatar.AvatarMaker;
import utils.MapQueue;
import ui.Inputer;
import ui.Menu;
import config.Settings;
import data.fchData.Cid;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CidManager extends Manager<Cid> {
    private static final int MAX_FID_CID_CACHE_SIZE = 100;
    private static final int MAX_CID_FID_CACHE_SIZE = 100;
    private static final int MAX_FID_AVATAR_CACHE_SIZE = 50;
    
    private static final String MAP_FID_CID = "fidCid";
    private static final String MAP_CID_FID = "cidFid";
    private static final String MAP_FID_AVATAR = "fidAvatar";

    private final String mainFid;
    private final String sid;
    private final ApipClient apipClient;
    private final String avatarBasePath;
    private final String avatarFilePath;

    private final MapQueue<String, String> fidCidCache;
    private final MapQueue<String, String> cidFidCache;
    private final MapQueue<String, String> fidAvatarCache;
    public CidManager(Settings settings){
        super(settings, ManagerType.CID);
        this.apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);
        this.mainFid = settings.getMainFid();
        this.sid = settings.getSid();
        this.avatarBasePath = Settings.DEFAULT_AVATAR_BASE_PATH;
        this.avatarFilePath = Settings.DEFAULT_AVATAR_FILE_PATH;
        
        this.fidCidCache = new MapQueue<>(MAX_FID_CID_CACHE_SIZE);
        this.cidFidCache = new MapQueue<>(MAX_CID_FID_CACHE_SIZE);
        this.fidAvatarCache = new MapQueue<>(MAX_FID_AVATAR_CACHE_SIZE);
        
        // Initialize maps in LevelDB
        createMap(MAP_FID_CID);
        createMap(MAP_CID_FID);
        createMap(MAP_FID_AVATAR);
    }

    public String getCid(String fid) {
        if (fid == null) return null;

        // Check cache first
        String cid = fidCidCache.get(fid);
        if (cid != null) return cid;

        // Check DB
        cid = localDB.getFromMap(MAP_FID_CID, fid);
        if (cid != null) {
            fidCidCache.put(fid, cid);
            return cid;
        }

        // Query API
        Map<String, Cid> cidInfoMap = apipClient.cidByIds(utils.http.RequestMethod.POST,
                                                                 utils.http.AuthType.FC_SIGN_BODY, fid);
        if (cidInfoMap != null && !cidInfoMap.isEmpty()) {
            Cid cidInfo = cidInfoMap.get(fid);
            if (cidInfo != null) {
                cid = cidInfo.getCid();
                if (cid != null) {
                    updateCidPair(fid, cid);
                    return cid;
                }
            }
        }
        return null;
    }

    public String getFid(String cid) {
        if (cid == null) return null;

        // Check cache first
        String fid = cidFidCache.get(cid);
        if (fid != null) return fid;

        // Check DB
        fid = localDB.getFromMap(MAP_CID_FID, cid);
        if (fid != null) {
            cidFidCache.put(cid, fid);
            return fid;
        }

        return null;
    }

    private void updateCidPair(String fid, String cid) {
        fidCidCache.put(fid, cid);
        cidFidCache.put(cid, fid);
        localDB.putInMap(MAP_FID_CID, fid, cid);
        localDB.putInMap(MAP_CID_FID, cid, fid);
    }

    public String getAvatar(String fid) {
        if (fid == null) return null;

        // Check cache first
        String avatarPath = fidAvatarCache.get(fid);
        if (avatarPath != null) return avatarPath;

        // Check DB
        avatarPath = localDB.getFromMap(MAP_FID_AVATAR, fid);
        if (avatarPath != null) {
            fidAvatarCache.put(fid, avatarPath);
            return avatarPath;
        }

        // Generate new avatar
        try {
            String[] paths = AvatarMaker.getAvatars(new String[]{fid}, avatarBasePath, avatarFilePath);
            if (paths != null && paths.length > 0) {
                avatarPath = paths[0];
                fidAvatarCache.put(fid, avatarPath);
                localDB.putInMap(MAP_FID_AVATAR, fid, avatarPath);
                return avatarPath;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Map<String, String> getAvatars(String[] fids) {
        if (fids == null || fids.length == 0) return null;

        Map<String, String> result = new HashMap<>();
        for (String fid : fids) {
            String avatarPath = getAvatar(fid);
            if (avatarPath != null) {
                result.put(fid, avatarPath);
            }
        }
        return result;
    }

    @Override
    public void close() {
        super.close();
    }

    public void menu(BufferedReader br, boolean withSettings) {
        Menu menu = new Menu("CID Management Menu", this::close);
        
        menu.add("Get CID by FID", () -> {
            String fid = Inputer.inputString(br, "Input FID:");
            if (fid == null) return;
            
            String cid = getCid(fid);
            if (cid != null) {
                System.out.println("CID: " + cid);
            } else {
                System.out.println("No CID found for FID: " + fid);
            }
        });
        
        menu.add("Get FID by CID", () -> {
            String cid = Inputer.inputString(br, "Input CID:");
            if (cid == null) return;
            
            String fid = getFid(cid);
            if (fid != null) {
                System.out.println("FID: " + fid);
            } else {
                System.out.println("No FID found for CID: " + cid);
            }
        });
        
        menu.add("Generate Avatar", () -> {
            String fid = Inputer.inputString(br, "Input FID for avatar generation:");
            if (fid == null) return;
            
            String avatarPath = getAvatar(fid);
            if (avatarPath != null) {
                System.out.println("Avatar generated at: " + avatarPath);
            } else {
                System.out.println("Failed to generate avatar for FID: " + fid);
            }
        });
        
        menu.add("Generate Multiple Avatars", () -> {
            System.out.println("Input FIDs (one per line, empty line to finish):");
            List<String> fidList = new ArrayList<>();
            while (true) {
                String fid = Inputer.inputString(br, "");
                if (fid == null || fid.trim().isEmpty()) break;
                fidList.add(fid);
            }
            
            if (fidList.isEmpty()) return;
            
            Map<String, String> avatarPaths = getAvatars(fidList.toArray(new String[0]));
            if (avatarPaths != null && !avatarPaths.isEmpty()) {
                System.out.println("\nGenerated Avatars:");
                avatarPaths.forEach((fid, path) -> 
                    System.out.println(fid + " -> " + path));
            } else {
                System.out.println("Failed to generate any avatars");
            }
        });
        
        menu.add("Search CID/FID", () -> {
            String searchTerm = Inputer.inputString(br, "Input CID, FID or part of them:");
            if (searchTerm == null) return;
            
            Map<String, String[]> results = apipClient.fidCidSeek(searchTerm, 
                utils.http.RequestMethod.POST,
                utils.http.AuthType.FC_SIGN_BODY);
                
            if (results != null && !results.isEmpty()) {
                System.out.println("\nSearch Results:");
                results.forEach((key, values) -> {
                    System.out.println("Key: " + key);
                    System.out.println("Values: " + String.join(", ", values));
                    System.out.println();
                });
            } else {
                System.out.println("No results found");
            }
        });
        
        menu.add("Show Cached Entries", () -> {
            System.out.println("\nFID->CID Cache:");
            fidCidCache.getMap().forEach((fid, cid) -> 
                System.out.println(fid + " -> " + cid));
                
            System.out.println("\nCID->FID Cache:");
            cidFidCache.getMap().forEach((cid, fid) -> 
                System.out.println(cid + " -> " + fid));
                
            System.out.println("\nAvatar Cache:");
            fidAvatarCache.getMap().forEach((fid, path) -> 
                System.out.println(fid + " -> " + path));
        });

        menu.showAndSelect(br);
    }

    public String getMainFid() {
        return mainFid;
    }

    public String getSid() {
        return sid;
    }

    public String getAvatarBasePath() {
        return avatarBasePath;
    }

    public String getAvatarFilePath() {
        return avatarFilePath;
    }
    
} 