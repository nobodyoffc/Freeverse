package fapi.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 区块事件调度器
 * <p>
 * 统一管理基于区块高度变化的任务调度。
 * 支持两种模式：
 * 1. 文件系统监听模式 - 监听区块存储目录变化
 * 2. 轮询模式 - 定期检查区块高度
 * <p>
 * 当检测到新区块时，按顺序执行所有注册的任务。
 */
public class BlockEventDispatcher implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(BlockEventDispatcher.class);
    
    // ==================== 任务接口 ====================
    
    /**
     * 区块任务接口
     */
    public interface BlockTask {
        /**
         * 任务名称
         */
        String getName();
        
        /**
         * 执行任务
         * @param currentHeight 当前区块高度
         * @param previousHeight 上次处理的区块高度
         */
        void execute(long currentHeight, long previousHeight);
        
        /**
         * 任务优先级（数值越小优先级越高）
         * 默认为 100
         */
        default int getPriority() {
            return 100;
        }
        
        /**
         * 是否在每个区块都执行
         * false 表示只在高度变化时执行一次
         */
        default boolean executeOnEveryBlock() {
            return false;
        }
    }
    
    // ==================== 字段 ====================
    
    /** 已注册的任务列表 */
    private final CopyOnWriteArrayList<BlockTask> tasks = new CopyOnWriteArrayList<>();
    
    /** 区块高度获取函数 */
    private final Supplier<Long> heightSupplier;
    
    /** 调度线程池 */
    private ScheduledExecutorService scheduler;
    
    /** 文件系统监听器 */
    private WatchService watchService;
    
    /** 监听线程 */
    private Thread watcherThread;
    
    /** 运行状态 */
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    /** 上次处理的区块高度 */
    private final AtomicLong lastProcessedHeight = new AtomicLong(0);
    
    /** 缓存的当前区块高度 */
    private final AtomicLong cachedHeight = new AtomicLong(0);
    
    /** 最小处理间隔（毫秒） */
    private static final long MIN_PROCESS_INTERVAL_MS = 1000;
    
    /** 上次处理时间 */
    private volatile long lastProcessTime = 0;
    
    // ==================== 构造函数 ====================
    
    /**
     * 创建区块事件调度器
     * @param heightSupplier 区块高度获取函数
     */
    public BlockEventDispatcher(Supplier<Long> heightSupplier) {
        this.heightSupplier = heightSupplier;
    }
    
    // ==================== 任务注册 ====================
    
    /**
     * 注册任务
     */
    public void registerTask(BlockTask task) {
        tasks.add(task);
        // 按优先级排序
        tasks.sort((a, b) -> Integer.compare(a.getPriority(), b.getPriority()));
        log.info("BlockTask registered: {} (priority={})", task.getName(), task.getPriority());
    }
    
    /**
     * 注销任务
     */
    public void unregisterTask(BlockTask task) {
        tasks.remove(task);
        log.info("BlockTask unregistered: {}", task.getName());
    }
    
    /**
     * 注销指定名称的任务
     */
    public void unregisterTask(String taskName) {
        tasks.removeIf(t -> t.getName().equals(taskName));
        log.info("BlockTask unregistered by name: {}", taskName);
    }
    
    /**
     * 获取已注册的任务列表
     */
    public List<BlockTask> getTasks() {
        return List.copyOf(tasks);
    }
    
    // ==================== 启动模式 ====================
    
    /**
     * 使用文件系统监听模式启动
     * @param watchPath 监听的目录路径
     */
    public void startWithWatcher(Path watchPath) throws IOException {
        if (!running.compareAndSet(false, true)) {
            log.warn("BlockEventDispatcher already running");
            return;
        }
        
        // 初始化高度
        Long currentHeight = heightSupplier.get();
        if (currentHeight != null && currentHeight > 0) {
            cachedHeight.set(currentHeight);
            lastProcessedHeight.set(currentHeight);
        }
        
        // 创建文件系统监听器
        watchService = FileSystems.getDefault().newWatchService();
        watchPath.register(watchService, 
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY);
        
        // 启动监听线程
        watcherThread = new Thread(() -> {
            log.info("BlockEventDispatcher watcher thread started, watching: {}", watchPath);
            
            while (running.get()) {
                try {
                    WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                    if (key != null) {
                        boolean hasEvents = !key.pollEvents().isEmpty();
                        key.reset();
                        
                        if (hasEvents) {
                            // 有文件变化，检查区块高度
                            checkAndProcess();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error in watcher loop", e);
                }
            }
            
            log.info("BlockEventDispatcher watcher thread stopped");
        }, "BlockEventDispatcher-Watcher");
        
        watcherThread.setDaemon(true);
        watcherThread.start();
        
        log.info("BlockEventDispatcher started with watcher mode: {}", watchPath);
    }
    
    /**
     * 使用轮询模式启动
     * @param intervalMs 轮询间隔（毫秒）
     */
    public void startWithPolling(long intervalMs) {
        if (!running.compareAndSet(false, true)) {
            log.warn("BlockEventDispatcher already running");
            return;
        }
        
        // 初始化高度
        Long currentHeight = heightSupplier.get();
        if (currentHeight != null && currentHeight > 0) {
            cachedHeight.set(currentHeight);
            lastProcessedHeight.set(currentHeight);
        }
        
        // 创建调度线程池
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BlockEventDispatcher-Polling");
            t.setDaemon(true);
            return t;
        });
        
        // 延迟 5 秒后开始轮询
        scheduler.scheduleAtFixedRate(this::checkAndProcess, 
                5000, intervalMs, TimeUnit.MILLISECONDS);
        
        log.info("BlockEventDispatcher started with polling mode: interval={}ms", intervalMs);
    }
    
    // ==================== 核心处理 ====================
    
    /**
     * 检查区块高度并处理任务
     */
    public void checkAndProcess() {
        try {
            // 防止过于频繁的处理
            long now = System.currentTimeMillis();
            if (now - lastProcessTime < MIN_PROCESS_INTERVAL_MS) {
                return;
            }
            
            // 获取当前区块高度
            Long currentHeight = heightSupplier.get();
            if (currentHeight == null || currentHeight <= 0) {
                log.debug("Current height is null or 0, skipping");
                return;
            }
            
            long previousHeight = cachedHeight.getAndSet(currentHeight);
            
            // 检查高度是否变化
            if (currentHeight <= previousHeight) {
//                log.trace("No new blocks: current={}, previous={}", currentHeight, previousHeight);
                return;
            }
            
            lastProcessTime = now;
            
//            log.debug("New block detected: height {} -> {}", previousHeight, currentHeight);
            
            // 执行所有注册的任务
            for (BlockTask task : tasks) {
                try {
                    long taskStart = System.currentTimeMillis();
                    task.execute(currentHeight, previousHeight);
                    long taskDuration = System.currentTimeMillis() - taskStart;
                    
                    if (taskDuration > 1000) {
                        log.warn("BlockTask {} took {}ms", task.getName(), taskDuration);
                    }
//                    else {
//                        log.debug("BlockTask {} completed in {}ms", task.getName(), taskDuration);
//                    }
                } catch (Exception e) {
                    log.error("Error executing BlockTask {}", task.getName(), e);
                }
            }
            
            // 更新最后处理高度
            lastProcessedHeight.set(currentHeight);
            
        } catch (Exception e) {
            log.error("Error in checkAndProcess", e);
        }
    }
    
    /**
     * 手动触发一次检查
     */
    public void triggerCheck() {
        checkAndProcess();
    }
    
    // ==================== Getters ====================
    
    /**
     * 获取缓存的当前区块高度
     */
    public long getCachedHeight() {
        return cachedHeight.get();
    }
    
    /**
     * 获取上次处理的区块高度
     */
    public long getLastProcessedHeight() {
        return lastProcessedHeight.get();
    }
    
    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }
    
    // ==================== 关闭 ====================
    
    @Override
    public void close() throws IOException {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        
        log.info("Stopping BlockEventDispatcher...");
        
        // 关闭调度器
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // 关闭文件监听器
        if (watchService != null) {
            watchService.close();
        }
        
        // 等待监听线程结束
        if (watcherThread != null) {
            watcherThread.interrupt();
            try {
                watcherThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("BlockEventDispatcher stopped");
    }
    
    // ==================== 便捷任务创建方法 ====================
    
    /**
     * 创建简单任务
     */
    public static BlockTask simpleTask(String name, int priority, 
                                        java.util.function.BiConsumer<Long, Long> action) {
        return new BlockTask() {
            @Override
            public String getName() { return name; }
            
            @Override
            public int getPriority() { return priority; }
            
            @Override
            public void execute(long currentHeight, long previousHeight) {
                action.accept(currentHeight, previousHeight);
            }
        };
    }
}

