package fapi.recharge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 区块存储目录监控器
 * <p>
 * 监听 LISTEN_PATH（本地区块存储目录）的文件变动，
 * 当检测到新区块落盘时，触发充值查询。
 * <p>
 * 基于 Java NIO WatchService 实现。
 */
public class BlockStorageWatcher implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(BlockStorageWatcher.class);
    
    /** 默认重试间隔（毫秒） */
    private static final long DEFAULT_RETRY_INTERVAL_MS = 5000;
    
    /** 默认最大落后高度（超过此值触发告警） */
    private static final int DEFAULT_MAX_LAG_HEIGHT = 10;
    
    private final Path listenPath;
    private final Consumer<Path> onNewBlockCallback;
    private final long retryIntervalMs;
    private final int maxLagHeight;
    
    private WatchService watchService;
    private ExecutorService watchExecutor;
    private volatile boolean running = false;
    private volatile boolean frozen = false;
    private volatile int consecutiveFailures = 0;
    private volatile long lastSuccessTime = 0;
    
    /**
     * 构造函数
     * 
     * @param listenPath 监听的区块存储目录
     * @param onNewBlockCallback 新区块回调
     */
    public BlockStorageWatcher(Path listenPath, Consumer<Path> onNewBlockCallback) {
        this(listenPath, onNewBlockCallback, DEFAULT_RETRY_INTERVAL_MS, DEFAULT_MAX_LAG_HEIGHT);
    }
    
    /**
     * 完整构造函数
     */
    public BlockStorageWatcher(Path listenPath, Consumer<Path> onNewBlockCallback,
                                long retryIntervalMs, int maxLagHeight) {
        this.listenPath = listenPath;
        this.onNewBlockCallback = onNewBlockCallback;
        this.retryIntervalMs = retryIntervalMs;
        this.maxLagHeight = maxLagHeight;
    }
    
    /**
     * 启动监控
     */
    public void start() throws IOException {
        if (running) {
            log.warn("BlockStorageWatcher already running");
            return;
        }
        
        if (!Files.exists(listenPath)) {
            log.warn("LISTEN_PATH does not exist: {}, creating...", listenPath);
            Files.createDirectories(listenPath);
        }
        
        if (!Files.isDirectory(listenPath)) {
            throw new IOException("LISTEN_PATH is not a directory: " + listenPath);
        }
        
        watchService = FileSystems.getDefault().newWatchService();
        listenPath.register(watchService, 
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY);
        
        running = true;
        lastSuccessTime = System.currentTimeMillis();
        
        watchExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "BlockStorageWatcher");
            t.setDaemon(true);
            return t;
        });
        
        watchExecutor.submit(this::watchLoop);
        
        log.info("BlockStorageWatcher started: {}", listenPath);
    }
    
    /**
     * 监控循环
     */
    private void watchLoop() {
        while (running) {
            try {
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                if (key == null) {
                    continue;
                }
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        log.warn("Watch event overflow");
                        continue;
                    }
                    
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path fileName = pathEvent.context();
                    Path fullPath = listenPath.resolve(fileName);
                    
                    // 检查是否为区块文件
                    if (isBlockFile(fileName)) {
                        handleNewBlock(fullPath);
                    }
                }
                
                if (!key.reset()) {
                    log.error("Watch key no longer valid");
                    break;
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in watch loop", e);
                handleWatchFailure();
            }
        }
    }
    
    /**
     * 检查是否为区块文件
     */
    private boolean isBlockFile(Path fileName) {
        String name = fileName.toString();
        // 区块文件通常以 .blk 或数字结尾
        return name.endsWith(".blk") || name.endsWith(".dat") || 
               name.matches(".*\\d+$") || name.matches("blk\\d+\\..*");
    }
    
    /**
     * 处理新区块
     */
    private void handleNewBlock(Path blockFile) {
        if (frozen) {
            log.warn("BlockStorageWatcher is frozen, ignoring new block: {}", blockFile);
            return;
        }
        
        try {
//            log.info("New block detected: {}", blockFile);
            consecutiveFailures = 0;
            lastSuccessTime = System.currentTimeMillis();
            
            if (onNewBlockCallback != null) {
                onNewBlockCallback.accept(blockFile);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle new block: {}", blockFile, e);
            handleCallbackFailure();
        }
    }
    
    /**
     * 处理监控失败
     */
    private void handleWatchFailure() {
        consecutiveFailures++;
        
        if (consecutiveFailures >= maxLagHeight) {
            log.error("BlockStorageWatcher consecutive failures: {}, freezing", consecutiveFailures);
            freeze();
        } else {
            try {
                Thread.sleep(retryIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 处理回调失败
     */
    private void handleCallbackFailure() {
        consecutiveFailures++;
        
        if (consecutiveFailures >= maxLagHeight) {
            log.error("BlockStorageWatcher callback failures: {}, freezing", consecutiveFailures);
            freeze();
        }
    }
    
    /**
     * 冻结监控（停止处理新区块，但继续监控）
     */
    public void freeze() {
        frozen = true;
        log.warn("BlockStorageWatcher frozen due to repeated failures");
    }
    
    /**
     * 解冻监控
     */
    public void unfreeze() {
        frozen = false;
        consecutiveFailures = 0;
        log.info("BlockStorageWatcher unfrozen");
    }
    
    /**
     * 检查是否冻结
     */
    public boolean isFrozen() {
        return frozen;
    }
    
    /**
     * 检查是否运行中
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * 获取连续失败次数
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }
    
    /**
     * 获取最后成功时间
     */
    public long getLastSuccessTime() {
        return lastSuccessTime;
    }
    
    /**
     * 获取监听路径
     */
    public Path getListenPath() {
        return listenPath;
    }
    
    @Override
    public void close() throws IOException {
        running = false;
        
        if (watchExecutor != null) {
            watchExecutor.shutdown();
            try {
                if (!watchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    watchExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                watchExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (watchService != null) {
            watchService.close();
        }
        
        log.info("BlockStorageWatcher stopped");
    }
}

