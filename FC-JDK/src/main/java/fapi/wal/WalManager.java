package fapi.wal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * WAL (Write-Ahead Log) 管理器
 * <p>
 * 实现写前日志机制，确保数据持久化和恢复能力。
 * <p>
 * 特性：
 * - 顺序追加写入
 * - 哈希链验证
 * - 可配置的 fsync 策略
 * - 自动轮转
 * - 启动恢复
 */
public class WalManager implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(WalManager.class);
    
    /** 初始哈希（创世常量） */
    public static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    
    /** 默认最大 WAL 文件大小：256MB */
    private static final long DEFAULT_MAX_WAL_SIZE = 256 * 1024 * 1024;
    
    /** 默认 fsync 间隔：50ms */
    private static final long DEFAULT_FSYNC_INTERVAL_MS = 50;
    
    /** WAL 文件扩展名 */
    private static final String WAL_EXTENSION = ".wal";
    
    /** WAL 文件前缀 */
    private static final String WAL_PREFIX = "wal_";
    
    private final Path walDir;
    private final long maxWalSize;
    private final long fsyncIntervalMs;
    private final ReentrantLock writeLock = new ReentrantLock();
    
    private volatile Path currentWalFile;
    private volatile BufferedWriter currentWriter;
    private volatile String lastHash;
    private final AtomicLong currentSeq = new AtomicLong(0);
    private final AtomicLong currentWalSize = new AtomicLong(0);
    private volatile long lastFsyncTime = 0;
    private volatile boolean closed = false;
    
    /** 待 fsync 的条目计数 */
    private final AtomicLong pendingFsyncCount = new AtomicLong(0);
    
    /**
     * 构造函数
     */
    public WalManager(Path walDir) throws IOException {
        this(walDir, DEFAULT_MAX_WAL_SIZE, DEFAULT_FSYNC_INTERVAL_MS);
    }
    
    /**
     * 完整构造函数
     */
    public WalManager(Path walDir, long maxWalSize, long fsyncIntervalMs) throws IOException {
        this.walDir = walDir;
        this.maxWalSize = maxWalSize;
        this.fsyncIntervalMs = fsyncIntervalMs;
        this.lastHash = GENESIS_HASH;
        
        // 创建目录
        Files.createDirectories(walDir);
        
        // 初始化或恢复
        initialize();
        
        log.info("WalManager initialized at {}, maxSize={}, fsyncInterval={}ms", 
                walDir, maxWalSize, fsyncIntervalMs);
    }
    
    /**
     * 初始化 WAL 管理器
     */
    private void initialize() throws IOException {
        // 查找最新的 WAL 文件
        Path latestWal = findLatestWalFile();
        
        if (latestWal != null) {
            // 恢复状态
            RecoveryResult recovery = recoverFromWal(latestWal);
            this.lastHash = recovery.lastHash;
            this.currentSeq.set(recovery.lastSeq);
            this.currentWalSize.set(Files.size(latestWal));
            
            // 检查是否需要轮转
            if (currentWalSize.get() >= maxWalSize) {
                rotateWal();
            } else {
                this.currentWalFile = latestWal;
                this.currentWriter = openWriter(latestWal, true);
            }
            
            log.info("Recovered from WAL: lastSeq={}, lastHash={}...", 
                    currentSeq.get(), lastHash.substring(0, 8));
        } else {
            // 创建新的 WAL 文件
            createNewWalFile();
        }
    }
    
    /**
     * 追加 WAL 条目
     */
    public WalEntry append(WalEntryType type, String key, Object data) throws IOException {
        if (closed) {
            throw new IOException("WalManager is closed");
        }
        
        writeLock.lock();
        try {
            // 创建条目
            long seq = currentSeq.incrementAndGet();
            WalEntry entry = new WalEntry(type, key, data, lastHash, seq);
            
            // 写入
            String line = entry.toJsonLine() + "\n";
            byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
            
            currentWriter.write(line);
            currentWalSize.addAndGet(bytes.length);
            lastHash = entry.getHash();
            pendingFsyncCount.incrementAndGet();
            
            // 检查是否需要 fsync
            checkFsync();
            
            // 检查是否需要轮转
            if (currentWalSize.get() >= maxWalSize) {
                rotateWal();
            }
            
            return entry;
            
        } finally {
            writeLock.unlock();
        }
    }
    
    /**
     * 追加批量 WAL 条目（原子操作）
     */
    public List<WalEntry> appendBatch(List<WalEntryData> entries) throws IOException {
        if (closed) {
            throw new IOException("WalManager is closed");
        }
        
        if (entries == null || entries.isEmpty()) {
            return new ArrayList<>();
        }
        
        writeLock.lock();
        try {
            List<WalEntry> result = new ArrayList<>(entries.size());
            StringBuilder batch = new StringBuilder();
            
            for (WalEntryData entryData : entries) {
                long seq = currentSeq.incrementAndGet();
                WalEntry entry = new WalEntry(entryData.type, entryData.key, entryData.data, lastHash, seq);
                
                String line = entry.toJsonLine() + "\n";
                batch.append(line);
                lastHash = entry.getHash();
                result.add(entry);
            }
            
            // 批量写入
            String batchStr = batch.toString();
            byte[] bytes = batchStr.getBytes(StandardCharsets.UTF_8);
            currentWriter.write(batchStr);
            currentWalSize.addAndGet(bytes.length);
            pendingFsyncCount.addAndGet(entries.size());
            
            // 强制 fsync 批量写入
            forceFsync();
            
            // 检查是否需要轮转
            if (currentWalSize.get() >= maxWalSize) {
                rotateWal();
            }
            
            return result;
            
        } finally {
            writeLock.unlock();
        }
    }
    
    /**
     * 强制 fsync
     */
    public void forceFsync() throws IOException {
        writeLock.lock();
        try {
            if (currentWriter != null && pendingFsyncCount.get() > 0) {
                currentWriter.flush();
                pendingFsyncCount.set(0);
                lastFsyncTime = System.currentTimeMillis();
            }
        } finally {
            writeLock.unlock();
        }
    }
    
    /**
     * 检查并执行 fsync
     */
    private void checkFsync() throws IOException {
        long now = System.currentTimeMillis();
        if (now - lastFsyncTime >= fsyncIntervalMs && pendingFsyncCount.get() > 0) {
            currentWriter.flush();
            pendingFsyncCount.set(0);
            lastFsyncTime = now;
        }
    }
    
    /**
     * 轮转 WAL 文件
     */
    private void rotateWal() throws IOException {
        // 先 fsync 并关闭当前文件
        if (currentWriter != null) {
            currentWriter.flush();
            currentWriter.close();
        }
        
        // 创建新文件
        createNewWalFile();
        
        log.info("WAL rotated, new file: {}", currentWalFile.getFileName());
    }
    
    /**
     * 创建新的 WAL 文件
     */
    private void createNewWalFile() throws IOException {
        String filename = WAL_PREFIX + System.currentTimeMillis() + WAL_EXTENSION;
        currentWalFile = walDir.resolve(filename);
        currentWriter = openWriter(currentWalFile, false);
        currentWalSize.set(0);
        lastFsyncTime = System.currentTimeMillis();
    }
    
    /**
     * 打开写入器
     */
    private BufferedWriter openWriter(Path file, boolean append) throws IOException {
        return Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                append ? new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND}
                       : new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING});
    }
    
    /**
     * 查找最新的 WAL 文件
     */
    private Path findLatestWalFile() throws IOException {
        if (!Files.exists(walDir)) {
            return null;
        }
        
        try (var stream = Files.list(walDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(WAL_EXTENSION))
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
     * 从 WAL 文件恢复
     */
    public RecoveryResult recoverFromWal(Path walFile) throws IOException {
        RecoveryResult result = new RecoveryResult();
        result.lastHash = GENESIS_HASH;
        result.lastSeq = 0;
        result.entries = new ArrayList<>();
        result.validEntryCount = 0;
        result.invalidEntryCount = 0;
        
        if (!Files.exists(walFile)) {
            return result;
        }
        
        try (BufferedReader reader = Files.newBufferedReader(walFile, StandardCharsets.UTF_8)) {
            String line;
            int lineNum = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.trim().isEmpty()) continue;
                
                try {
                    WalEntry entry = WalEntry.fromJsonLine(line);
                    
                    // 验证哈希
                    if (!entry.verifyHash()) {
                        log.warn("WAL entry hash mismatch at line {}", lineNum);
                        result.invalidEntryCount++;
                        // 截断到最后一条有效记录
                        break;
                    }
                    
                    // 验证链
                    if (!entry.verifyChain(result.lastHash)) {
                        log.warn("WAL chain broken at line {}", lineNum);
                        result.invalidEntryCount++;
                        break;
                    }
                    
                    result.entries.add(entry);
                    result.lastHash = entry.getHash();
                    result.lastSeq = entry.getSeq();
                    result.validEntryCount++;
                    
                } catch (Exception e) {
                    log.warn("Failed to parse WAL entry at line {}: {}", lineNum, e.getMessage());
                    result.invalidEntryCount++;
                    break;
                }
            }
        }
        
        if (result.invalidEntryCount > 0) {
            log.warn("WAL recovery found {} invalid entries, truncating", result.invalidEntryCount);
        }
        
        return result;
    }
    
    /**
     * 读取所有 WAL 文件的条目
     */
    public List<WalEntry> readAllEntries() throws IOException {
        List<WalEntry> allEntries = new ArrayList<>();
        
        try (var stream = Files.list(walDir)) {
            List<Path> walFiles = stream
                    .filter(p -> p.getFileName().toString().endsWith(WAL_EXTENSION))
                    .sorted((a, b) -> {
                        try {
                            return Long.compare(
                                    Files.getLastModifiedTime(a).toMillis(),
                                    Files.getLastModifiedTime(b).toMillis()
                            );
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .toList();
            
            for (Path walFile : walFiles) {
                RecoveryResult result = recoverFromWal(walFile);
                allEntries.addAll(result.entries);
            }
        }
        
        return allEntries;
    }
    
    /**
     * 清理已快照的 WAL 文件
     */
    public void cleanupBeforeSnapshot(long snapshotSeq) throws IOException {
        writeLock.lock();
        try {
            try (var stream = Files.list(walDir)) {
                List<Path> walFiles = stream
                        .filter(p -> p.getFileName().toString().endsWith(WAL_EXTENSION))
                        .filter(p -> !p.equals(currentWalFile))
                        .toList();
                
                for (Path walFile : walFiles) {
                    RecoveryResult result = recoverFromWal(walFile);
                    // 如果文件中所有条目都在快照之前，可以删除
                    if (!result.entries.isEmpty() && 
                        result.entries.get(result.entries.size() - 1).getSeq() <= snapshotSeq) {
                        Files.delete(walFile);
                        log.info("Deleted old WAL file: {}", walFile.getFileName());
                    }
                }
            }
        } finally {
            writeLock.unlock();
        }
    }
    
    /**
     * 写入检查点标记
     */
    public WalEntry writeCheckpoint(long snapshotSeq, String snapshotHash) throws IOException {
        CheckpointData data = new CheckpointData(snapshotSeq, snapshotHash, System.currentTimeMillis());
        return append(WalEntryType.CHECKPOINT, "checkpoint", data);
    }
    
    /**
     * 获取最新的哈希
     */
    public String getLastHash() {
        return lastHash;
    }
    
    /**
     * 获取当前序列号
     */
    public long getCurrentSeq() {
        return currentSeq.get();
    }
    
    /**
     * 获取 WAL 目录
     */
    public Path getWalDir() {
        return walDir;
    }
    
    /**
     * 获取当前 WAL 文件大小
     */
    public long getCurrentWalSize() {
        return currentWalSize.get();
    }
    
    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        
        writeLock.lock();
        try {
            if (currentWriter != null) {
                currentWriter.flush();
                currentWriter.close();
            }
        } finally {
            writeLock.unlock();
        }
        
        log.info("WalManager closed");
    }
    
    // ==================== 内部数据类 ====================
    
    /**
     * WAL 条目数据（用于批量追加）
     */
    public static class WalEntryData {
        public final WalEntryType type;
        public final String key;
        public final Object data;
        
        public WalEntryData(WalEntryType type, String key, Object data) {
            this.type = type;
            this.key = key;
            this.data = data;
        }
    }
    
    /**
     * 恢复结果
     */
    public static class RecoveryResult {
        public List<WalEntry> entries;
        public String lastHash;
        public long lastSeq;
        public int validEntryCount;
        public int invalidEntryCount;
    }
    
    /**
     * 检查点数据
     */
    public static class CheckpointData {
        public final long snapshotSeq;
        public final String snapshotHash;
        public final long ts;
        
        public CheckpointData(long snapshotSeq, String snapshotHash, long ts) {
            this.snapshotSeq = snapshotSeq;
            this.snapshotHash = snapshotHash;
            this.ts = ts;
        }
    }
}

