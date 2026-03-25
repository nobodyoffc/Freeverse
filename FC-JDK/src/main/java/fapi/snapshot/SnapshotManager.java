package fapi.snapshot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import core.crypto.Hash;
import fapi.FapiBalanceManager.CashRecord;
import fapi.FapiBalanceManager.MetaState;
import fapi.FapiBalanceManager.PeerBalance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 快照管理器
 * <p>
 * 管理余额和状态的定期快照，用于快速恢复。
 * <p>
 * 快照格式（JSON）：
 * {
 *   "version": 1,
 *   "ts": 时间戳,
 *   "walSeq": WAL序列号,
 *   "auditTipHash": 审计哈希链顶端,
 *   "metaState": {...},
 *   "balances": { peerId: PeerBalance, ... },
 *   "recentCredits": [ CashRecord, ... ],
 *   "balancesHash": 余额摘要哈希
 * }
 */
public class SnapshotManager implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(SnapshotManager.class);
    private static final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    
    /** 当前快照版本 */
    public static final int SNAPSHOT_VERSION = 1;
    
    /** 快照文件扩展名 */
    private static final String SNAPSHOT_EXTENSION = ".snapshot";
    
    /** 快照文件前缀 */
    private static final String SNAPSHOT_PREFIX = "snapshot_";
    
    /** 默认保留的快照数量 */
    private static final int DEFAULT_MAX_SNAPSHOTS = 3;
    
    /** 默认快照间隔（毫秒）：10分钟 */
    public static final long DEFAULT_SNAPSHOT_INTERVAL_MS = 10 * 60 * 1000;
    
    /** 默认触发快照的事务数量 */
    public static final long DEFAULT_SNAPSHOT_TX_THRESHOLD = 100_000;
    
    private final Path snapshotDir;
    private final int maxSnapshots;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean closed = false;
    
    /**
     * 构造函数
     */
    public SnapshotManager(Path snapshotDir) throws IOException {
        this(snapshotDir, DEFAULT_MAX_SNAPSHOTS);
    }
    
    /**
     * 完整构造函数
     */
    public SnapshotManager(Path snapshotDir, int maxSnapshots) throws IOException {
        this.snapshotDir = snapshotDir;
        this.maxSnapshots = maxSnapshots;
        
        Files.createDirectories(snapshotDir);
        
        log.info("SnapshotManager initialized at {}, maxSnapshots={}", snapshotDir, maxSnapshots);
    }
    
    /**
     * 创建快照
     */
    public SnapshotInfo createSnapshot(
            Map<String, PeerBalance> balances,
            Map<String, CashRecord> recentCredits,
            MetaState metaState,
            long walSeq,
            String auditTipHash) throws IOException {
        
        if (closed) {
            throw new IOException("SnapshotManager is closed");
        }
        
        lock.lock();
        try {
            // 计算余额摘要哈希
            String balancesHash = computeBalancesHash(balances);
            
            // 创建快照对象
            SnapshotData snapshot = new SnapshotData();
            snapshot.version = SNAPSHOT_VERSION;
            snapshot.ts = System.currentTimeMillis();
            snapshot.walSeq = walSeq;
            snapshot.auditTipHash = auditTipHash;
            snapshot.metaState = metaState;
            snapshot.balances = balances;
            snapshot.recentCredits = recentCredits;
            snapshot.balancesHash = balancesHash;
            
            // 写入文件
            String filename = SNAPSHOT_PREFIX + snapshot.ts + SNAPSHOT_EXTENSION;
            Path snapshotFile = snapshotDir.resolve(filename);
            
            String json = gson.toJson(snapshot);
            Files.writeString(snapshotFile, json, StandardCharsets.UTF_8);
            
            log.info("Snapshot created: {}, balances={}, walSeq={}", 
                    filename, balances.size(), walSeq);
            
            // 清理旧快照
            cleanupOldSnapshots();
            
            return new SnapshotInfo(snapshotFile, snapshot.ts, walSeq, balancesHash);
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 加载最新快照
     */
    public SnapshotData loadLatestSnapshot() throws IOException {
        Path latestFile = findLatestSnapshotFile();
        if (latestFile == null) {
            log.info("No snapshot found");
            return null;
        }
        
        return loadSnapshot(latestFile);
    }
    
    /**
     * 加载指定快照
     */
    public SnapshotData loadSnapshot(Path snapshotFile) throws IOException {
        if (!Files.exists(snapshotFile)) {
            return null;
        }
        
        String json = Files.readString(snapshotFile, StandardCharsets.UTF_8);
        SnapshotData snapshot = gson.fromJson(json, SnapshotData.class);
        
        // 验证版本
        if (snapshot.version > SNAPSHOT_VERSION) {
            throw new IOException("Snapshot version " + snapshot.version + 
                    " is not supported (current: " + SNAPSHOT_VERSION + ")");
        }
        
        // 验证余额摘要
        String computedHash = computeBalancesHash(snapshot.balances);
        if (!computedHash.equals(snapshot.balancesHash)) {
            throw new IOException("Snapshot balances hash mismatch");
        }
        
        log.info("Snapshot loaded: {}, balances={}, walSeq={}", 
                snapshotFile.getFileName(), snapshot.balances.size(), snapshot.walSeq);
        
        return snapshot;
    }
    
    /**
     * 查找最新的快照文件
     */
    public Path findLatestSnapshotFile() throws IOException {
        if (!Files.exists(snapshotDir)) {
            return null;
        }
        
        try (var stream = Files.list(snapshotDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(SNAPSHOT_EXTENSION))
                    .max((a, b) -> {
                        try {
                            return Long.compare(
                                    Files.getLastModifiedTime(a).toMillis(),
                                    Files.getLastModifiedTime(b).toMillis()
                            );
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .orElse(null);
        }
    }
    
    /**
     * 清理旧快照
     */
    private void cleanupOldSnapshots() throws IOException {
        try (var stream = Files.list(snapshotDir)) {
            List<Path> snapshots = stream
                    .filter(p -> p.getFileName().toString().endsWith(SNAPSHOT_EXTENSION))
                    .sorted((a, b) -> {
                        try {
                            return Long.compare(
                                    Files.getLastModifiedTime(b).toMillis(),
                                    Files.getLastModifiedTime(a).toMillis()
                            );
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .toList();
            
            // 删除超出保留数量的旧快照
            for (int i = maxSnapshots; i < snapshots.size(); i++) {
                Files.delete(snapshots.get(i));
                log.info("Deleted old snapshot: {}", snapshots.get(i).getFileName());
            }
        }
    }
    
    /**
     * 计算余额摘要哈希
     * <p>
     * 按 peerId 排序后计算 Merkle 根哈希
     */
    public static String computeBalancesHash(Map<String, PeerBalance> balances) {
        if (balances == null || balances.isEmpty()) {
            return Hash.sha256("");
        }
        
        // 按 peerId 排序
        List<String> sortedKeys = new ArrayList<>(balances.keySet());
        Collections.sort(sortedKeys);
        
        // 构建规范字符串
        StringBuilder sb = new StringBuilder();
        for (String peerId : sortedKeys) {
            PeerBalance balance = balances.get(peerId);
            sb.append(peerId).append(":")
              .append(balance.getBalance()).append(":")
              .append(balance.getCreditLimit()).append(":")
              .append(balance.getSeq()).append(";");
        }
        
        return Hash.sha256(sb.toString());
    }
    
    /**
     * 列出所有快照
     */
    public List<SnapshotInfo> listSnapshots() throws IOException {
        List<SnapshotInfo> result = new ArrayList<>();
        
        if (!Files.exists(snapshotDir)) {
            return result;
        }
        
        try (var stream = Files.list(snapshotDir)) {
            List<Path> snapshots = stream
                    .filter(p -> p.getFileName().toString().endsWith(SNAPSHOT_EXTENSION))
                    .sorted((a, b) -> {
                        try {
                            return Long.compare(
                                    Files.getLastModifiedTime(b).toMillis(),
                                    Files.getLastModifiedTime(a).toMillis()
                            );
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .toList();
            
            for (Path snapshotFile : snapshots) {
                try {
                    SnapshotData data = loadSnapshot(snapshotFile);
                    result.add(new SnapshotInfo(snapshotFile, data.ts, data.walSeq, data.balancesHash));
                } catch (Exception e) {
                    log.warn("Failed to load snapshot info: {}", snapshotFile, e);
                }
            }
        }
        
        return result;
    }
    
    /**
     * 获取快照目录
     */
    public Path getSnapshotDir() {
        return snapshotDir;
    }
    
    @Override
    public void close() throws IOException {
        closed = true;
        log.info("SnapshotManager closed");
    }
    
    // ==================== 数据类 ====================
    
    /**
     * 快照数据
     */
    public static class SnapshotData {
        public int version;
        public long ts;
        public long walSeq;
        public String auditTipHash;
        public MetaState metaState;
        public Map<String, PeerBalance> balances;
        public Map<String, CashRecord> recentCredits;
        public String balancesHash;
    }
    
    /**
     * 快照信息（轻量级）
     */
    public static class SnapshotInfo {
        public final Path file;
        public final long ts;
        public final long walSeq;
        public final String balancesHash;
        
        public SnapshotInfo(Path file, long ts, long walSeq, String balancesHash) {
            this.file = file;
            this.ts = ts;
            this.walSeq = walSeq;
            this.balancesHash = balancesHash;
        }
    }
}

