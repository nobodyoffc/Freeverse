package fapi.components;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fapi.AbstractFapiComponent;
import fapi.FapiCode;
import fapi.components.map.MapEntry;
import fapi.message.FapiRequest;
import fapi.message.FapiResponse;
import fudp.connection.PeerConnection;
import fudp.message.PongMessage;
import fudp.node.FudpNode;
import utils.Hex;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * MAP组件 - FID到网络地址的映射服务
 * 
 * 主要功能：
 * 1. 为NAT后的节点提供地址注册服务
 * 2. 为其他节点提供地址查询服务
 * 3. 通过FUDP ping验证节点可达性
 * 
 * 设计要点：
 * - 零参数注册：所有信息从FUDP连接中提取（peerId、pubkey、源IP:port）
 * - NAT友好：使用观察到的外部地址而非客户端声明的地址
 * - 心跳保活：客户端需定期重新注册以保持NAT映射
 * - 可达性验证：find时可选ping验证目标节点
 */
@SuppressWarnings("unchecked")
public class MapComponent extends AbstractFapiComponent {
    
    /** 过期阈值：30秒内视为新鲜 */
    private static final long FRESH_THRESHOLD_MS = 30_000;
    
    /** 清理阈值：24小时未活跃的条目将被清理 */
    private static final long CLEANUP_THRESHOLD_MS = 24 * 60 * 60 * 1000;
    
    /** Ping超时时间 */
    private static final long PING_TIMEOUT_MS = 5_000;
    
    /** 持久化文件名 */
    private static final String PERSISTENCE_FILE = "map_entries.json";
    
    /** 持久化间隔：60秒 */
    private static final long PERSISTENCE_INTERVAL_MS = 60_000;
    
    /** 清理间隔：10分钟 */
    private static final long CLEANUP_INTERVAL_MS = 10 * 60 * 1000;
    
    /** 内存存储 */
    private final ConcurrentHashMap<String, MapEntry> entries = new ConcurrentHashMap<>();
    
    /** JSON序列化 */
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    /** 调度器 */
    private ScheduledExecutorService scheduler;
    
    /** 持久化文件路径 */
    private Path persistencePath;
    
    /** 是否有未保存的更改 */
    private volatile boolean dirty = false;
    
    @Override
    public String getName() {
        return "MAP";
    }
    
    @Override
    public List<String> getApiList() {
        return List.of(
            "map.register",     // 注册自己（零参数）
            "map.find",         // 查找指定FID
            "map.unregister",   // 注销自己
            "map.list",         // 列出所有注册条目（管理用）
            "map.stats"         // 统计信息
        );
    }
    
    @Override
    protected void doInitialize() {
        // 初始化持久化路径
        String dataDir = settings.getDbDir();
        if (dataDir != null) {
            persistencePath = Paths.get(dataDir, PERSISTENCE_FILE);
        } else {
            persistencePath = Paths.get(System.getProperty("user.home"), ".fapi", PERSISTENCE_FILE);
        }
        
        // 加载持久化数据
        loadEntries();
        
        // 启动调度器
        scheduler = Executors.newScheduledThreadPool(2);
        
        // 定期持久化
        scheduler.scheduleWithFixedDelay(
            this::saveEntriesIfDirty,
            PERSISTENCE_INTERVAL_MS,
            PERSISTENCE_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        // 定期清理过期条目
        scheduler.scheduleWithFixedDelay(
            this::cleanupExpiredEntries,
            CLEANUP_INTERVAL_MS,
            CLEANUP_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        log.info("MapComponent initialized with {} entries from {}", entries.size(), persistencePath);
    }
    
    @Override
    protected void doClose(long timeoutMs) throws InterruptedException {
        if (scheduler != null) {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow();
            }
        }
        
        // 关闭前保存
        saveEntries();
        log.info("MapComponent closed, saved {} entries", entries.size());
    }
    
    @Override
    public FapiResponse handleRequest(FapiRequest request, String peerId) {
        String method = request.getMethodName();
        String requestId = request.getId();
        
        if (method == null) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "Method name is missing");
        }
        
        return switch (method) {
            case "register" -> handleRegister(request, peerId);
            case "find" -> handleFind(request, peerId);
            case "unregister" -> handleUnregister(request, peerId);
            case "list" -> handleList(request);
            case "stats" -> handleStats(request);
            default -> errorResponse(requestId, FapiCode.METHOD_NOT_ALLOWED, "Unknown method: " + method);
        };
    }
    
    // ==================== API Handlers ====================
    
    /**
     * 处理注册请求
     * 零参数：所有信息从FUDP连接中提取
     */
    private FapiResponse handleRegister(FapiRequest request, String peerId) {
        String requestId = request.getId();
        
        // 获取FUDP节点
        FudpNode node = server.getFudpNode();
        if (node == null) {
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, "FUDP node not available");
        }
        
        // 获取连接信息
        PeerConnection conn = node.getProtocol().getConnectionManager().getByPeerId(peerId);
        if (conn == null) {
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Connection not found for peer");
        }
        
        // 提取观察到的外部地址（从UDP包源地址）
        SocketAddress peerAddress = conn.getPeerAddress();
        if (!(peerAddress instanceof InetSocketAddress)) {
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Invalid peer address type");
        }
        
        InetSocketAddress inetAddr = (InetSocketAddress) peerAddress;
        String observedIp = inetAddr.getHostString();
        int observedPort = inetAddr.getPort();
        
        // 获取公钥
        byte[] pubkeyBytes = conn.getPeerPublicKey();
        if (pubkeyBytes == null) {
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Peer public key not available");
        }
        String pubkeyHex = Hex.toHex(pubkeyBytes);
        
        // 创建或更新条目
        long now = System.currentTimeMillis();
        MapEntry existingEntry = entries.get(peerId);
        
        MapEntry entry = new MapEntry();
        entry.setFid(peerId);
        entry.setPubkey(pubkeyHex);
        entry.setObservedIp(observedIp);
        entry.setObservedPort(observedPort);
        entry.setLastSeen(now);
        
        if (existingEntry != null) {
            // 保留首次注册时间
            entry.setRegisteredAt(existingEntry.getRegisteredAt());
        } else {
            entry.setRegisteredAt(now);
        }
        
        entries.put(peerId, entry);
        dirty = true;
        
        log.debug("Registered: {} -> {}:{}", peerId, observedIp, observedPort);
        
        return successResponse(requestId, entry);
    }
    
    /**
     * 处理查找请求
     * 参数：目标FID（通过fcdsl.ids或params.fid）
     */
    private FapiResponse handleFind(FapiRequest request, String peerId) {
        String requestId = request.getId();
        
        // 获取目标FID
        String targetFid = null;
        
        // 优先从fcdsl.ids获取
        if (request.getFcdsl() != null && request.getFcdsl().getIds() != null 
                && !request.getFcdsl().getIds().isEmpty()) {
            targetFid = request.getFcdsl().getIds().get(0);
        }
        
        // 回退到params.fid
        if (targetFid == null) {
            Map<String, Object> params = parseParams(request.getParams(), Map.class);
            if (params != null && params.get("fid") != null) {
                targetFid = params.get("fid").toString();
            }
        }
        
        if (targetFid == null || targetFid.isEmpty()) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "Target FID is required (fcdsl.ids or params.fid)");
        }
        
        // 查找条目
        MapEntry entry = entries.get(targetFid);
        if (entry == null) {
            return errorResponse(requestId, FapiCode.NOT_FOUND, "FID not registered: " + targetFid);
        }
        
        // 检查是否需要验证可达性
        boolean isFresh = !entry.isStale(FRESH_THRESHOLD_MS);
        
        if (!isFresh) {
            // 尝试ping验证
            boolean reachable = pingPeer(entry);
            if (reachable) {
                entry.setLastSeen(System.currentTimeMillis());
                entry.setStale(null);
                dirty = true;
            } else {
                // 标记为stale但不删除
                entry.setStale(true);
            }
        }
        
        return successResponse(requestId, entry);
    }
    
    /**
     * 处理注销请求
     * 零参数：使用peerId注销自己
     */
    private FapiResponse handleUnregister(FapiRequest request, String peerId) {
        String requestId = request.getId();
        
        MapEntry removed = entries.remove(peerId);
        if (removed == null) {
            return errorResponse(requestId, FapiCode.NOT_FOUND, "FID not registered");
        }
        
        dirty = true;
        log.debug("Unregistered: {}", peerId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("fid", peerId);
        
        return successResponse(requestId, result);
    }
    
    /**
     * 处理列表请求
     * 返回所有注册条目（管理用途）
     */
    private FapiResponse handleList(FapiRequest request) {
        String requestId = request.getId();
        
        List<MapEntry> entryList = new ArrayList<>(entries.values());
        
        FapiResponse response = successResponse(requestId, entryList);
        response.setGot((long) entryList.size());
        response.setTotal((long) entryList.size());
        
        return response;
    }
    
    /**
     * 处理统计请求
     */
    private FapiResponse handleStats(FapiRequest request) {
        String requestId = request.getId();
        
        long now = System.currentTimeMillis();
        int total = entries.size();
        int fresh = 0;
        int stale = 0;
        
        for (MapEntry entry : entries.values()) {
            if (entry.isStale(FRESH_THRESHOLD_MS)) {
                stale++;
            } else {
                fresh++;
            }
        }
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEntries", total);
        stats.put("freshEntries", fresh);
        stats.put("staleEntries", stale);
        stats.put("freshThresholdMs", FRESH_THRESHOLD_MS);
        stats.put("cleanupThresholdMs", CLEANUP_THRESHOLD_MS);
        stats.put("timestamp", now);
        
        return successResponse(requestId, stats);
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Ping目标节点验证可达性
     */
    private boolean pingPeer(MapEntry entry) {
        FudpNode node = server.getFudpNode();
        if (node == null) {
            return false;
        }
        
        try {
            // 确保节点在PeerBook中
            byte[] pubkey = Hex.fromHex(entry.getPubkey());
            node.addPeer(entry.getFid(), pubkey, entry.getObservedIp(), entry.getObservedPort());
            
            // 发送ping并等待pong
            CompletableFuture<PongMessage> pongFuture = node.pingAwaitPong(
                entry.getFid(), false, PING_TIMEOUT_MS);
            
            PongMessage pong = pongFuture.get(PING_TIMEOUT_MS + 1000, TimeUnit.MILLISECONDS);
            return pong != null;
            
        } catch (TimeoutException e) {
            log.debug("Ping timeout for {}", entry.getFid());
            return false;
        } catch (Exception e) {
            log.debug("Ping failed for {}: {}", entry.getFid(), e.getMessage());
            return false;
        }
    }
    
    /**
     * 清理过期条目
     */
    private void cleanupExpiredEntries() {
        int removed = 0;
        for (Map.Entry<String, MapEntry> e : entries.entrySet()) {
            if (e.getValue().shouldCleanup(CLEANUP_THRESHOLD_MS)) {
                entries.remove(e.getKey());
                removed++;
            }
        }
        
        if (removed > 0) {
            dirty = true;
            log.info("Cleaned up {} expired entries, {} remaining", removed, entries.size());
        }
    }
    
    /**
     * 加载持久化数据
     */
    private void loadEntries() {
        if (persistencePath == null || !Files.exists(persistencePath)) {
            return;
        }
        
        try {
            String json = Files.readString(persistencePath, StandardCharsets.UTF_8);
            Type type = new TypeToken<Map<String, MapEntry>>(){}.getType();
            Map<String, MapEntry> loaded = gson.fromJson(json, type);
            
            if (loaded != null) {
                entries.putAll(loaded);
                log.info("Loaded {} entries from {}", entries.size(), persistencePath);
            }
        } catch (Exception e) {
            log.warn("Failed to load entries from {}: {}", persistencePath, e.getMessage());
        }
    }
    
    /**
     * 保存条目（如果有更改）
     */
    private void saveEntriesIfDirty() {
        if (dirty) {
            saveEntries();
        }
    }
    
    /**
     * 保存条目到文件
     */
    private void saveEntries() {
        if (persistencePath == null) {
            return;
        }
        
        try {
            // 确保父目录存在
            Path parent = persistencePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            
            String json = gson.toJson(entries);
            Files.writeString(persistencePath, json, StandardCharsets.UTF_8);
            dirty = false;
            
            log.debug("Saved {} entries to {}", entries.size(), persistencePath);
        } catch (IOException e) {
            log.error("Failed to save entries to {}: {}", persistencePath, e.getMessage());
        }
    }
    
    // ==================== Public Access Methods ====================
    
    /**
     * 获取指定FID的条目
     */
    public MapEntry getEntry(String fid) {
        return entries.get(fid);
    }
    
    /**
     * 获取所有条目数量
     */
    public int getEntryCount() {
        return entries.size();
    }
}
