package fapi.recharge;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import constants.IndicesNames;
import data.fchData.Block;
import fapi.FapiBalanceManager;
import fapi.FapiBalanceManager.CreditResult;
import fapi.FapiBalanceManager.CreditStatus;
import fapi.FapiBalanceManager.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ui.Inputer;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import data.fchData.OpReturn;
import utils.JsonUtils;

/**
 * 充值扫描器
 * <p>
 * 配合 BlockStorageWatcher 使用，当检测到新区块时，
 * 向 ES 查询新的 Cash 列表并处理充值入账。
 * <p>
 * 重构后的扫描逻辑：
 * 1. lastOrderScanHeight 永久化保存在 FapiBalanceManager 中
 * 2. 初始化时如果 lastOrderScanHeight 为空，由用户选择从 0 高度还是最新高度开始
 * 3. 扫描订单按 birthHeight 和 id 升序排序
 * 4. 保存的是最后一个订单的 birthHeight，不是区块链高度
 * 5. 监听到新区块时，读取 lastOrderScanHeight 查询 birthHeight 大于该值的新订单
 * 6. 直接从 ES 的 block 索引获取当前最佳区块高度
 * 7. 扫描时顺便更新 FapiBalanceManager 的 bestHeight 和 bestBlockId
 */
public class RechargeScanner implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(RechargeScanner.class);
    
    /** 默认确认区块数 */
    private static final int DEFAULT_CONFIRMATIONS = 1;
    
    /** 默认扫描间隔（毫秒） */
    private static final long DEFAULT_SCAN_INTERVAL_MS = 60_000;
    
    private final FapiBalanceManager balanceManager;
    private final Function<Long, List<CashInfo>> cashQueryFunction;
    private final String serviceAddress;
    private final int confirmations;
    private final ElasticsearchClient esClient;
    
    private BlockStorageWatcher watcher;
    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;
    
    // 缓存的最佳区块信息（避免频繁查询 ES）
    private volatile long cachedBestHeight = 0;
    private volatile String cachedBestBlockId = null;
    private volatile long lastBestBlockQueryTime = 0;
    private static final long BEST_BLOCK_CACHE_MS = 5000; // 5秒缓存
    
    /**
     * 构造函数
     * 
     * @param balanceManager 余额管理器
     * @param cashQueryFunction 查询新 Cash 的函数（输入：起始高度，输出：Cash 列表，需按 birthHeight 和 id 升序排序）
     * @param serviceAddress 服务地址（用于过滤收款地址）
     * @param esClient Elasticsearch 客户端（用于查询最佳区块高度）
     */
    public RechargeScanner(FapiBalanceManager balanceManager,
                           Function<Long, List<CashInfo>> cashQueryFunction,
                           String serviceAddress,
                           ElasticsearchClient esClient) {
        this(balanceManager, cashQueryFunction, serviceAddress, DEFAULT_CONFIRMATIONS, esClient);
    }
    
    /**
     * 完整构造函数
     * 
     * @param balanceManager 余额管理器
     * @param cashQueryFunction 查询新 Cash 的函数（输入：起始高度，输出：Cash 列表，需按 birthHeight 和 id 升序排序）
     * @param serviceAddress 服务地址
     * @param confirmations 确认区块数
     * @param esClient Elasticsearch 客户端（用于查询最佳区块高度）
     */
    public RechargeScanner(FapiBalanceManager balanceManager,
                           Function<Long, List<CashInfo>> cashQueryFunction,
                           String serviceAddress,
                           int confirmations,
                           ElasticsearchClient esClient) {
        this.balanceManager = balanceManager;
        this.cashQueryFunction = cashQueryFunction;
        this.serviceAddress = serviceAddress;
        this.confirmations = confirmations;
        this.esClient = esClient;
    }
    
    /**
     * 从 ES 的 block 索引获取最佳区块
     * <p>
     * 查询 height 最大的 Block
     * 
     * @return 最佳区块，如果查询失败返回 null
     */
    public Block queryBestBlockFromEs() {
        if (esClient == null) {
            log.warn("Cannot query best block: esClient is null");
            return null;
        }
        
        try {
            SearchResponse<Block> result = esClient.search(s -> s
                    .index(IndicesNames.BLOCK)
                    .size(1)
                    .sort(so -> so.field(f -> f.field("height").order(SortOrder.Desc))),
                    Block.class);
            
            if (result.hits().hits().isEmpty()) {
                log.warn("No blocks found in ES");
                return null;
            }
            
            return result.hits().hits().get(0).source();
        } catch (IOException e) {
            log.error("Failed to query best block from ES", e);
            return null;
        }
    }
    
    /**
     * 获取当前最佳区块高度（从 ES 查询，带缓存）
     * 
     * @return 最佳区块高度，如果查询失败返回 0
     */
    public long getBestHeight() {
        long now = System.currentTimeMillis();
        
        // 使用缓存（避免频繁查询 ES）
        if (cachedBestHeight > 0 && (now - lastBestBlockQueryTime) < BEST_BLOCK_CACHE_MS) {
            return cachedBestHeight;
        }
        
        Block bestBlock = queryBestBlockFromEs();
        if (bestBlock != null && bestBlock.getHeight() != null) {
            cachedBestHeight = bestBlock.getHeight();
            cachedBestBlockId = bestBlock.getId();
            lastBestBlockQueryTime = now;
            return cachedBestHeight;
        }
        
        return cachedBestHeight; // 返回旧缓存值（如果有）
    }
    
    /**
     * 获取当前最佳区块ID（从缓存）
     */
    public String getBestBlockId() {
        // 确保缓存是最新的
        getBestHeight();
        return cachedBestBlockId;
    }
    
    /**
     * 刷新最佳区块信息并更新 FapiBalanceManager
     * <p>
     * 同时检测回滚
     * 
     * @return 是否发生回滚
     */
    public boolean refreshAndUpdateBestBlock() {
        Block bestBlock = queryBestBlockFromEs();
        if (bestBlock == null || bestBlock.getHeight() == null) {
            return false;
        }
        
        long height = bestBlock.getHeight();
        String blockId = bestBlock.getId();
        
        // 更新缓存
        cachedBestHeight = height;
        cachedBestBlockId = blockId;
        lastBestBlockQueryTime = System.currentTimeMillis();
        
        // 更新 FapiBalanceManager 的 bestHeight 和 bestBlockId（同时检测回滚）
        boolean rollback = balanceManager.updateBestBlock(height, blockId);
        if (rollback) {
            log.warn("Rollback detected during best block update: height={}, blockId={}", height, blockId);
        }
        
        return rollback;
    }
    
    /**
     * 初始化扫描高度
     * <p>
     * 如果 lastOrderScanHeight 为空，由用户选择：
     * 1. 从 0 高度开始扫描订单
     * 2. 从最新高度开始扫描订单
     * 
     * @param br BufferedReader 用于用户输入
     * @return 初始化后的扫描高度
     */
    public long initializeScanHeight(BufferedReader br) {
        Long savedHeight = balanceManager.getLastOrderScanHeight();
        
        if (savedHeight != null) {
            log.info("RechargeScanner: loaded lastOrderScanHeight from persistence: {}", savedHeight);
            return savedHeight;
        }
        
        // 没有保存的高度，需要用户选择
        log.info("RechargeScanner: no saved lastOrderScanHeight found, prompting user for choice");
        
        long currentBestHeight = getBestHeight();
        
        System.out.println("\n=== 充值扫描器初始化 ===");
        System.out.println("没有找到保存的扫描高度，请选择扫描起始高度：");
        System.out.println("1. 从高度 0 开始扫描（将扫描所有历史订单）");
        System.out.println("2. 从当前最新高度 " + currentBestHeight + " 开始扫描（只扫描新订单）");
        
        boolean scanFromStart = Inputer.askIfYes(br, "选择从高度 0 开始扫描吗？");
        
        long initialHeight;
        if (scanFromStart) {
            initialHeight = 0;
            log.info("RechargeScanner: user chose to scan from height 0");
        } else {
            initialHeight = currentBestHeight;
            // 如果用户选择从最新高度开始，立即保存这个高度
            balanceManager.setLastOrderScanHeight(initialHeight);
            log.info("RechargeScanner: user chose to scan from current best height: {}, saved to persistence", initialHeight);
        }
        
        return initialHeight;
    }
    
    /**
     * 启动扫描器（使用 LISTEN_PATH 监控）
     * 
     * @param listenPath 监听的区块存储目录
     * @param br BufferedReader 用于初始化时的用户输入（可选）
     */
    public void startWithWatcher(Path listenPath, BufferedReader br) throws Exception {
        if (running) {
            log.warn("RechargeScanner already running");
            return;
        }
        
        // 刷新最佳区块信息
        refreshAndUpdateBestBlock();
        
        // 初始化扫描高度
        if (br != null && balanceManager.getLastOrderScanHeight() == null) {
            initializeScanHeight(br);
        }
        
        watcher = new BlockStorageWatcher(listenPath, this::onNewBlock);
        watcher.start();
        
        running = true;
        log.info("RechargeScanner started with watcher: {}", listenPath);
    }
    
    /**
     * 启动扫描器（使用 LISTEN_PATH 监控）- 无用户交互版本
     * <p>
     * 如果 lastOrderScanHeight 为空，将使用当前最佳高度作为起始高度
     */
    public void startWithWatcher(Path listenPath) throws Exception {
        if (running) {
            log.warn("RechargeScanner already running");
            return;
        }
        
        // 刷新最佳区块信息
        refreshAndUpdateBestBlock();
        
        // 如果没有保存的高度，使用当前最佳高度
        if (balanceManager.getLastOrderScanHeight() == null) {
            long currentBestHeight = getBestHeight();
            balanceManager.setLastOrderScanHeight(currentBestHeight);
            log.info("RechargeScanner: no saved height found, initialized to current best height: {}", currentBestHeight);
        }
        
        watcher = new BlockStorageWatcher(listenPath, this::onNewBlock);
        watcher.start();
        
        running = true;
        log.info("RechargeScanner started with watcher: {}", listenPath);
    }
    
    /**
     * 启动扫描器（定时轮询模式）
     * 
     * @param intervalMs 轮询间隔（毫秒）
     * @param br BufferedReader 用于初始化时的用户输入（可选）
     */
    public void startWithPolling(long intervalMs, BufferedReader br) {
        if (running) {
            log.warn("RechargeScanner already running");
            return;
        }
        
        // 刷新最佳区块信息
        refreshAndUpdateBestBlock();
        
        // 初始化扫描高度
        if (br != null && balanceManager.getLastOrderScanHeight() == null) {
            initializeScanHeight(br);
        }
        
        startPollingInternal(intervalMs);
    }
    
    /**
     * 启动扫描器（定时轮询模式）- 无用户交互版本
     * <p>
     * 如果 lastOrderScanHeight 为空，将使用当前最佳高度作为起始高度
     */
    public void startWithPolling(long intervalMs) {
        if (running) {
            log.warn("RechargeScanner already running");
            return;
        }
        
        // 刷新最佳区块信息
        refreshAndUpdateBestBlock();
        
        // 如果没有保存的高度，使用当前最佳高度
        if (balanceManager.getLastOrderScanHeight() == null) {
            long currentBestHeight = getBestHeight();
            balanceManager.setLastOrderScanHeight(currentBestHeight);
            log.info("RechargeScanner: no saved height found, initialized to current best height: {}", currentBestHeight);
        }
        
        startPollingInternal(intervalMs);
    }
    
    /**
     * 内部方法：启动轮询
     */
    private void startPollingInternal(long intervalMs) {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RechargeScanner-Poller");
            t.setDaemon(true);
            return t;
        });
        
        scheduler.scheduleAtFixedRate(this::pollForNewBlocks, 
                intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        
        running = true;
        log.info("RechargeScanner started with polling interval: {}ms", intervalMs);
    }
    
    /**
     * 新区块回调
     * <p>
     * 当监听到区块目录变化（有新区块产生）时触发
     */
    private void onNewBlock(Path blockFile) {
        log.debug("New block file detected: {}", blockFile);
        scanForNewCredits();
    }
    
    /**
     * 轮询新区块
     */
    private void pollForNewBlocks() {
        try {
            scanForNewCredits();
        } catch (Exception e) {
            log.error("Error polling for new blocks", e);
        }
    }
    
    /**
     * 批量查询 OpReturn 获取渠道信息
     * 
     * @param cashList 待处理的 Cash 列表
     * @return Map<cashId, viaFid> 渠道映射
     */
    private Map<String, String> queryViaFromOpReturns(List<CashInfo> cashList) {
        Map<String, String> viaMap = new HashMap<>();
        
        if (esClient == null) {
            log.debug("Cannot query OpReturns: esClient is null");
            return viaMap;
        }
        
        // 1. 收集所有 birthTxId 作为 OpReturn 的 id
        List<String> txIds = cashList.stream()
                .map(CashInfo::getBirthTxId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        
        if (txIds.isEmpty()) {
            log.debug("No transaction IDs to query for OpReturns");
            return viaMap;
        }
        
        // 2. 批量查询 ES opreturn 索引
        Map<String, OpReturn> opReturns = queryOpReturnsByIds(txIds);
        
        // 3. 解析每个 OpReturn 的 via 字段
        for (CashInfo cash : cashList) {
            String txId = cash.getBirthTxId();
            if (txId == null) continue;
            
            OpReturn opReturn = opReturns.get(txId);
            if (opReturn != null && opReturn.getOpReturn() != null) {
                String via = parseViaFromOpReturn(opReturn.getOpReturn());
                if (via != null) {
                    viaMap.put(cash.getCashId(), via);
                    log.debug("Found via for cashId={}: via={}", cash.getCashId(), via);
                }
            }
        }
        
        log.debug("queryViaFromOpReturns: found {} via mappings from {} cashes", 
                viaMap.size(), cashList.size());
        
        return viaMap;
    }
    
    /**
     * 批量查询 OpReturn 记录
     */
    private Map<String, OpReturn> queryOpReturnsByIds(List<String> txIds) {
        Map<String, OpReturn> result = new HashMap<>();
        
        if (txIds == null || txIds.isEmpty() || esClient == null) {
            return result;
        }
        
        try {
            // 分批查询，每批最多 100 个
            int batchSize = 100;
            for (int i = 0; i < txIds.size(); i += batchSize) {
                List<String> batch = txIds.subList(i, Math.min(i + batchSize, txIds.size()));
                
                co.elastic.clients.elasticsearch.core.SearchRequest.Builder searchBuilder = 
                        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder();
                searchBuilder.index(IndicesNames.OPRETURN);
                searchBuilder.size(batch.size());
                
                // 使用 ids 查询
                searchBuilder.query(q -> q
                        .ids(ids -> ids.values(batch))
                );
                
                co.elastic.clients.elasticsearch.core.SearchResponse<OpReturn> searchResponse = 
                        esClient.search(searchBuilder.build(), OpReturn.class);
                
                if (searchResponse.hits() != null && searchResponse.hits().hits() != null) {
                    for (co.elastic.clients.elasticsearch.core.search.Hit<OpReturn> hit : 
                            searchResponse.hits().hits()) {
                        OpReturn opReturn = hit.source();
                        if (opReturn != null && hit.id() != null) {
                            result.put(hit.id(), opReturn);
                        }
                    }
                }
            }
            
            log.debug("queryOpReturnsByIds: queried {} txIds, found {} OpReturns", 
                    txIds.size(), result.size());
            
        } catch (Exception e) {
            log.error("Error querying OpReturns: {}", e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * 从 OpReturn 内容解析 via 字段
     */
    private String parseViaFromOpReturn(String opReturnContent) {
        if (opReturnContent == null || opReturnContent.isEmpty()) {
            return null;
        }
        
        try {
            // 尝试解析为 JSON
            @SuppressWarnings("unchecked")
            Map<String, Object> json = JsonUtils.fromJson(opReturnContent, Map.class);
            if (json != null && json.containsKey("via")) {
                Object via = json.get("via");
                if (via instanceof String && isValidFid((String) via)) {
                    return (String) via;
                }
            }
        } catch (Exception e) {
            // 不是 JSON 格式，忽略
            log.trace("OpReturn is not JSON format or parse error: {}", opReturnContent);
        }
        
        return null;
    }
    
    /**
     * 简单验证 FID 格式
     */
    private boolean isValidFid(String fid) {
        // FID 通常是 34 个字符，以 F 开头
        return fid != null && fid.length() >= 26 && fid.length() <= 35 
                && (fid.startsWith("F") || fid.startsWith("1") || fid.startsWith("3"));
    }
    
    /**
     * 扫描新的充值
     * <p>
     * 重构后的逻辑：
     * 1. 从 FapiBalanceManager 读取 lastOrderScanHeight
     * 2. 从 ES 获取当前最佳区块高度（用于确认检查）
     * 3. 查询 birthHeight > lastOrderScanHeight 的订单
     * 4. 订单按 birthHeight 和 id 升序排序
     * 5. 查询 OpReturn 获取 via 渠道信息
     * 6. 如果没有新订单且 lastOrderScanHeight 为空，保存当前最佳高度
     * 7. 如果有新订单，保存最后一个订单的 birthHeight（不是区块链高度）
     * 8. 更新 FapiBalanceManager 的 bestHeight 和 bestBlockId
     */
    public void scanForNewCredits() {
        try {
            // 刷新最佳区块信息并更新 FapiBalanceManager
            refreshAndUpdateBestBlock();
            
            // 从持久化存储读取 lastOrderScanHeight
            Long lastOrderScanHeight = balanceManager.getLastOrderScanHeight();
            long currentBestHeight = cachedBestHeight;
            
            // 确定查询起始高度
            long fromHeight;
            if (lastOrderScanHeight != null) {
                fromHeight = lastOrderScanHeight;
            } else {
                // 如果没有保存的高度，从 0 开始（但这种情况应该在初始化时处理）
                fromHeight = 0;
            }
            
            log.debug("Scanning for new credits from height {}, currentBestHeight={}", fromHeight, currentBestHeight);
            
            // 查询新的 Cash（需要 birthHeight > fromHeight）
            List<CashInfo> newCashes = cashQueryFunction.apply(fromHeight);
            
            if (newCashes == null || newCashes.isEmpty()) {
//                log.debug("No new cashes found");
                
                // 如果没有新订单且 lastOrderScanHeight 为空，保存当前最佳高度
                if (lastOrderScanHeight == null && currentBestHeight > 0) {
                    balanceManager.setLastOrderScanHeight(currentBestHeight);
                    log.info("No new orders found and lastOrderScanHeight was null, saved current best height: {}", currentBestHeight);
                }
                return;
            }
            
            // 按 birthHeight 和 id 升序排序（确保顺序正确）
            newCashes.sort(Comparator
                    .comparingLong(CashInfo::getBirthHeight)
                    .thenComparing(CashInfo::getCashId));
            
            // 过滤出发送到服务地址的 Cash
            List<CashInfo> targetCashes = newCashes.stream()
                    .filter(cash -> serviceAddress.equals(cash.getOwner()))
                    .collect(Collectors.toList());
            
            // 批量查询 OpReturn 获取渠道信息
            Map<String, String> viaMap = queryViaFromOpReturns(targetCashes);
            
            int processed = 0;
            int skipped = newCashes.size() - targetCashes.size();
            int unconfirmed = 0;
            int withVia = 0;
            long lastProcessedHeight = fromHeight;
            
            for (CashInfo cash : targetCashes) {
                // 需要至少 N 个确认（使用从 ES 获取的真实区块高度）
                if (currentBestHeight > 0 && (currentBestHeight - cash.getBirthHeight() +1) < confirmations) {
                    log.debug("Cash {} not yet confirmed (birthHeight={}, currentBestHeight={})", 
                            cash.getCashId(), cash.getBirthHeight(), currentBestHeight);
                    unconfirmed++;
                    continue;
                }
                
                // 获取渠道信息
                String via = viaMap.get(cash.getCashId());
                
                // 处理充值（带渠道信息）
                CreditResult result = balanceManager.credit(
                        cash.getIssuer(),
                        cash.getCashId(),
                        cash.getValue(),
                        "block:" + cash.getBirthHeight(),
                        CreditStatus.CONFIRMED,
                        cash.getBirthHeight(),
                        cash.getBlockId(),
                        via  // 传递渠道信息
                );
                
                if (result.getCode() == ResultCode.OK) {
                    log.info("Credit processed: cashId={}, user={}, amount={}, via={}", 
                            cash.getCashId(), cash.getIssuer(), cash.getValue(), via);
                    processed++;
                    if (via != null) withVia++;
                    // 更新最后处理的订单高度
                    lastProcessedHeight = cash.getBirthHeight();
                } else if (result.getCode() == ResultCode.ALREADY_EXISTS) {
                    log.debug("Credit already exists: cashId={}", cash.getCashId());
                    // 即使已存在，也更新最后处理高度（表示我们已经看过这个高度的订单）
                    lastProcessedHeight = cash.getBirthHeight();
                } else {
                    log.warn("Credit failed: cashId={}, code={}", 
                            cash.getCashId(), result.getCode());
                }
            }
            
            // 保存最后处理的订单高度（重要：是订单的 birthHeight，不是区块链高度）
            if (lastProcessedHeight > fromHeight || lastOrderScanHeight == null) {
                balanceManager.setLastOrderScanHeight(lastProcessedHeight);
                log.debug("Saved lastOrderScanHeight: {}", lastProcessedHeight);
            }

            if (processed > 0 || skipped > 0 || unconfirmed > 0) {
                log.info("Scan completed: processed={}, withVia={}, skipped={}, unconfirmed={}, total={}, lastOrderScanHeight={}", 
                        processed, withVia, skipped, unconfirmed, newCashes.size(), lastProcessedHeight);
            }
            
        } catch (Exception e) {
            log.error("Error scanning for new credits", e);
        }
    }
    
    /**
     * 手动触发扫描
     */
    public void triggerScan() {
        scanForNewCredits();
    }
    
    /**
     * 重扫指定高度范围
     * 
     * @param fromHeight 起始高度
     * @param toHeight 结束高度
     */
    public void rescan(long fromHeight, long toHeight) {
        log.info("Rescanning from height {} to {}", fromHeight, toHeight);
        
        for (long height = fromHeight; height <= toHeight; height++) {
            List<CashInfo> cashes = cashQueryFunction.apply(height);
            if (cashes != null) {
                for (CashInfo cash : cashes) {
                    if (serviceAddress.equals(cash.getOwner())) {
                        balanceManager.credit(
                                cash.getIssuer(),
                                cash.getCashId(),
                                cash.getValue(),
                                "rescan:" + cash.getBirthHeight(),
                                CreditStatus.CONFIRMED,
                                cash.getBirthHeight(),
                                cash.getBlockId()
                        );
                    }
                }
            }
        }
        
        log.info("Rescan completed");
    }
    
    /**
     * 检查是否运行中
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * 获取最后扫描的订单高度
     * <p>
     * 注意：这是最后处理的订单的 birthHeight，不是区块链高度
     */
    public Long getLastOrderScanHeight() {
        return balanceManager.getLastOrderScanHeight();
    }
    
    /**
     * 获取缓存的最佳区块高度
     */
    public long getCachedBestHeight() {
        return cachedBestHeight;
    }
    
    /**
     * 获取缓存的最佳区块ID
     */
    public String getCachedBestBlockId() {
        return cachedBestBlockId;
    }
    
    /**
     * 获取监控器状态
     */
    public BlockStorageWatcher getWatcher() {
        return watcher;
    }
    
    @Override
    public void close() {
        running = false;
        
        if (watcher != null) {
            try {
                watcher.close();
            } catch (Exception e) {
                log.warn("Error closing watcher", e);
            }
        }
        
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
        
        log.info("RechargeScanner stopped");
    }
    
    /**
     * Cash 信息
     */
    public static class CashInfo {
        private final String cashId;
        private final String issuer;
        private final String owner;
        private final long value;
        private final long birthHeight;
        private final String blockId;
        private final String birthTxId;  // 交易ID，用于查询 OpReturn
        
        public CashInfo(String cashId, String issuer, String owner, 
                        long value, long birthHeight, String blockId) {
            this(cashId, issuer, owner, value, birthHeight, blockId, null);
        }
        
        public CashInfo(String cashId, String issuer, String owner, 
                        long value, long birthHeight, String blockId, String birthTxId) {
            this.cashId = cashId;
            this.issuer = issuer;
            this.owner = owner;
            this.value = value;
            this.birthHeight = birthHeight;
            this.blockId = blockId;
            this.birthTxId = birthTxId;
        }
        
        public String getCashId() { return cashId; }
        public String getIssuer() { return issuer; }
        public String getOwner() { return owner; }
        public long getValue() { return value; }
        public long getBirthHeight() { return birthHeight; }
        public String getBlockId() { return blockId; }
        public String getBirthTxId() { return birthTxId; }
    }
}
