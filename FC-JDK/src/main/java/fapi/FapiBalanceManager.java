package fapi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import core.crypto.Hash;
import data.feipData.Service;
import fapi.snapshot.SnapshotManager;
import fapi.wal.WalEntry;
import fapi.wal.WalEntryType;
import fapi.wal.WalManager;
import fudp.metrics.MeterRecord;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.WriteBatch;
import org.iq80.leveldb.impl.Iq80DBFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.FchUtils;
import utils.Hex;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * FAPI专用余额管理器
 * <p>
 * 实现 FAPI 经济模型，维护单一权威账本。
 * 使用 LevelDB KV 存储，支持写批量原子性和追加审计记录。
 * 仅传输计量事件和充值操作调用此组件，所有经济状态均在此处维护。
 * <p>
 * 这是一个独立的实现，专为 FAPI 服务设计，不继承任何基类。
 */
public class FapiBalanceManager implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(FapiBalanceManager.class);

    // ==================== 配置键常量 ====================
    public static final String CREDIT_LIMIT = "creditLimit";
    public static final String KEY_CREDIT_RETENTION_DAYS = "fapiCreditRetentionDays";
    public static final String KEY_SETTLE_CYCLE = "settleCycle";
    public static final String KEY_DEFAULT_VIA = "defaultVia";
    public static final String KEY_STAKEHOLDERS = "stakeholders";
    public static final String KEY_MIN_SETTLE_AMOUNT = "minSettleAmount";
    
    // ==================== 默认值常量 ====================
    public static final long DEFAULT_CREDIT_LIMIT = 10_000L; // 10000 聪
    public static final int DEFAULT_CREDIT_RETENTION_DAYS = 100;
    public static final long DEFAULT_SETTLE_CYCLE = 14400L; // 14400区块 ≈ 10天
    public static final String DEFAULT_VIA = "FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7";
    public static final long DEFAULT_MIN_SETTLE_AMOUNT = 100_000L; // 0.001 FCH = 100,000 聪
    
    private static final long MAX_AMOUNT = 10_000_000_000_000L; // 1e13 satoshi upper bound
    private static final int MAX_KEY_LENGTH = 128;
    private static final int MAX_META_BYTES = 2 * 1024;
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9._:-]+$");
    private static final String BALANCE_PREFIX = "B/";        // peer balance
    private static final String CHANNEL_BALANCE_PREFIX = "C/"; // channel share balance
    private static final String REQUEST_PREFIX = "R/req/";    // requestKey idempotency
    private static final String CASH_PREFIX = "R/cash/";      // cashId idempotency
    private static final String SETTLE_PREFIX = "S/";         // settle cycleId idempotency
    private static final String META_KEY = "M/meta";          // meta entry
    private static final String AUDIT_PREFIX = "A/";          // append-only audit rows
    private static final String LAST_ORDER_SCAN_HEIGHT_KEY = "M/lastOrderScanHeight";  // last scanned order height for recharge
    private static final String LAST_SETTLE_HEIGHT_KEY = "M/lastSettleHeight";  // last settle height
    private static final String ZERO_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    
    /** 回滚窗口：30个区块 */
    public static final int ROLLBACK_WINDOW = 30;
    
    /** 黑名单标志位：bit0 */
    public static final int FLAG_BLACKLISTED = 0x01;

    private final ReentrantLock lock = new ReentrantLock();
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final Path dbDir;
    private final DB db;
    private volatile Pricing pricing;
    private volatile MetaState metaState;
    private final long defaultCreditLimit;
    private final int creditRetentionDays; // 充值记录保留天数
    private final long minSettleAmount; // 最小结算金额（聪）
    private final Service service;
    private volatile boolean closed = false;
    
    // ==================== WAL 和快照支持 ====================
    private WalManager walManager;
    private SnapshotManager snapshotManager;
    private final AtomicLong txCount = new AtomicLong(0);
    private volatile long lastSnapshotTime = 0;
    private ScheduledExecutorService snapshotScheduler;
    
    // ==================== 内存缓存（用于快速查询和快照） ====================
    private final Map<String, PeerBalance> balanceCache = new ConcurrentHashMap<>();
    private final Map<String, CashRecord> recentCreditsCache = new ConcurrentHashMap<>();
    
    // ==================== 免费消息类型列表 ====================
    private volatile Set<String> freeMessageTypes = new HashSet<>();

    /**
     * 完整构造函数
     * 
     * @param service 服务对象（必需，用于获取定价信息）
     * @param dbDir 数据库目录
     * @param mainFid 主FID
     * @param sid 服务ID
     * @param creditLimit 信用额度（可为null，默认为0）
     * @param settingMap 配置映射（可为null，用于读取 creditRetentionDays 等配置）
     */
    public FapiBalanceManager(Service service, String dbDir, String mainFid, String sid, Long creditLimit, Map<String, Object> settingMap) {
        Objects.requireNonNull(service, "service cannot be null");

        this.service = service;
        this.pricing = parsePricing(service);

        // creditLimit priority: service.minCredit (on-chain, FCH) > settingMap.creditLimit (satoshi) > DEFAULT_CREDIT_LIMIT
        if (service.getMinCredit() != null && !service.getMinCredit().isEmpty()) {
            this.defaultCreditLimit = Math.max(0, FchUtils.coinToSatoshi(Double.parseDouble(service.getMinCredit())));
        } else {
            this.defaultCreditLimit = Math.max(0, creditLimit == null ? DEFAULT_CREDIT_LIMIT : creditLimit);
        }
        
        // 从 settingMap 读取 creditRetentionDays
        if (settingMap != null && settingMap.containsKey(KEY_CREDIT_RETENTION_DAYS)) {
            Object retentionDaysObj = settingMap.get(KEY_CREDIT_RETENTION_DAYS);
            if (retentionDaysObj instanceof Number) {
                this.creditRetentionDays = ((Number) retentionDaysObj).intValue();
            } else {
                this.creditRetentionDays = DEFAULT_CREDIT_RETENTION_DAYS;
            }
        } else {
            this.creditRetentionDays = DEFAULT_CREDIT_RETENTION_DAYS;
        }
        
        // 从 settingMap 读取 minSettleAmount
        if (settingMap != null && settingMap.containsKey(KEY_MIN_SETTLE_AMOUNT)) {
            Object minSettleAmountObj = settingMap.get(KEY_MIN_SETTLE_AMOUNT);
            if (minSettleAmountObj instanceof Number) {
                this.minSettleAmount = ((Number) minSettleAmountObj).longValue();
            } else {
                this.minSettleAmount = DEFAULT_MIN_SETTLE_AMOUNT;
            }
        } else {
            this.minSettleAmount = DEFAULT_MIN_SETTLE_AMOUNT;
        }

        this.dbDir = dbDir == null
            ? Path.of("fapi_balance")
            : Path.of(dbDir, mainFid + "_" + sid + "_fapi_balance");

        try {
            Files.createDirectories(this.dbDir);
            this.db = openLevelDb(this.dbDir);
            
            // 初始化 WAL 和快照管理器
            initializeWalAndSnapshot();
            
            // 执行启动恢复流程
            performStartupRecovery();
            
            // 启动定时快照任务
            startSnapshotScheduler();
            
            // Add PING/PONG as free message types by default (control messages should not be charged)
            Set<String> defaultFreeTypes = new HashSet<>();
            defaultFreeTypes.add("PING");
            defaultFreeTypes.add("PONG");
            defaultFreeTypes.add("PEER_INFO");
            defaultFreeTypes.add("CHAT_ACK");
            this.freeMessageTypes = defaultFreeTypes;
            
            log.info("FapiBalanceManager initialized. pricePerKB={} sat ({} FCH), pricePerKBIn={} sat, pricePerKBOut={} sat, " +
                    "creditLimit={} sat ({} FCH), orderViaShareBps={}, consumeViaShareBps={}, freeTypes={}, dbPath={}",
                pricing.getPricePerKb(), FchUtils.satoshiToCoin(pricing.getPricePerKb()),
                pricing.getPricePerKbIn(), pricing.getPricePerKbOut(),
                defaultCreditLimit, FchUtils.satoshiToCoin(defaultCreditLimit),
                pricing.getOrderViaShareBps(), pricing.getConsumeViaShareBps(),
                freeMessageTypes, this.dbDir);
            
            // Validate credit vs pricing configuration
            if (pricing.getPricePerKb() > 0 && defaultCreditLimit < pricing.getPricePerKb()) {
                log.warn("WARNING: creditLimit ({} sat = {} FCH) is less than pricePerKB ({} sat = {} FCH). " +
                        "New users cannot afford even 1 KB of data! " +
                        "Either increase settingMap.creditLimit (in satoshi) or service.minCredit (in FCH), " +
                        "or decrease pricePerKBIn/Out (in FCH). " +
                        "Example: for 1 satoshi/KB pricing, set pricePerKBIn/Out to \"0.00000001\".",
                    defaultCreditLimit, FchUtils.satoshiToCoin(defaultCreditLimit),
                    pricing.getPricePerKb(), FchUtils.satoshiToCoin(pricing.getPricePerKb()));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize FapiBalanceManager store", e);
        }
    }
    
    /**
     * Open LevelDB with stale lock handling and corrupted log recovery.
     */
    private DB openLevelDb(Path dbPath) throws IOException {
        File dbFolder = dbPath.toFile();
        handleStaleLock(dbFolder);

        Options options = new Options();
        options.createIfMissing(true);

        try {
            return Iq80DBFactory.factory.open(dbFolder, options);
        } catch (IOException e) {
            log.warn("First LevelDB open failed ({}). Attempting recovery by removing log files...", e.getMessage());
            // The iq80 LevelDB recovery reads *.log files; if they are corrupted/timed-out, remove them.
            File[] logFiles = dbFolder.listFiles((dir, name) -> name.endsWith(".log"));
            if (logFiles != null) {
                for (File lf : logFiles) {
                    log.warn("Removing potentially corrupted log file: {}", lf.getName());
                    if (!lf.delete()) {
                        log.error("Failed to delete log file: {}", lf.getAbsolutePath());
                    }
                }
            }
            handleStaleLock(dbFolder);
            return Iq80DBFactory.factory.open(dbFolder, options);
        }
    }

    private void handleStaleLock(File dbFolder) {
        File lockFile = new File(dbFolder, "LOCK");
        if (!lockFile.exists()) return;

        long lockAge = System.currentTimeMillis() - lockFile.lastModified();
        long staleThreshold = 5 * 60 * 1000; // 5 minutes

        if (lockAge > staleThreshold) {
            log.warn("Found stale LOCK file ({} ms old) in {}. Removing.", lockAge, dbFolder);
            if (!lockFile.delete()) {
                log.error("Failed to remove stale LOCK file: {}", lockFile.getAbsolutePath());
            }
        }
    }

    /**
     * 初始化 WAL 和快照管理器
     */
    private void initializeWalAndSnapshot() throws IOException {
        Path walDir = dbDir.resolve("wal");
        Path snapshotDir = dbDir.resolve("snapshots");
        
        this.walManager = new WalManager(walDir);
        this.snapshotManager = new SnapshotManager(snapshotDir);
        
        log.info("WAL and Snapshot managers initialized");
    }
    
    /**
     * 执行启动恢复流程
     * <p>
     * 恢复顺序：加载快照 → 重放 WAL → 重放审计（可选）
     */
    private void performStartupRecovery() throws IOException {
        log.info("Starting recovery process...");
        
        // 1. 尝试加载最新快照
        SnapshotManager.SnapshotData snapshot = snapshotManager.loadLatestSnapshot();
        
        if (snapshot != null) {
            // 恢复余额缓存
            if (snapshot.balances != null) {
                balanceCache.putAll(snapshot.balances);
            }
            // 恢复最近充值记录
            if (snapshot.recentCredits != null) {
                recentCreditsCache.putAll(snapshot.recentCredits);
            }
            // 恢复元数据
            if (snapshot.metaState != null) {
                this.metaState = snapshot.metaState;
            }
            
            log.info("Snapshot loaded: walSeq={}, balances={}", 
                    snapshot.walSeq, balanceCache.size());
            
            // 2. 重放快照之后的 WAL 条目
            replayWalFromSeq(snapshot.walSeq);
        } else {
            // 无快照，从 LevelDB 加载
            this.metaState = loadMetaState();
            loadBalanceCacheFromDb();
            loadRecentCreditsFromDb();
            
            log.info("No snapshot found, loaded from DB: balances={}", balanceCache.size());
        }
        
        lastSnapshotTime = System.currentTimeMillis();
    }
    
    /**
     * 从指定序列号开始重放 WAL
     */
    private void replayWalFromSeq(long fromSeq) throws IOException {
        List<WalEntry> entries = walManager.readAllEntries();
        int replayed = 0;
        
        for (WalEntry entry : entries) {
            if (entry.getSeq() <= fromSeq) {
                continue;
            }
            
            replayWalEntry(entry);
            replayed++;
        }
        
        log.info("Replayed {} WAL entries from seq {}", replayed, fromSeq);
    }
    
    /**
     * 重放单个 WAL 条目
     */
    private void replayWalEntry(WalEntry entry) {
        try {
            switch (entry.getType()) {
                case BALANCE_UPDATE:
                    PeerBalance balance = entry.parseData(PeerBalance.class);
                    if (balance != null) {
                        balanceCache.put(entry.getKey(), balance);
                    }
                    break;
                    
                case CREDIT:
                    CashRecord cashRecord = entry.parseData(CashRecord.class);
                    if (cashRecord != null) {
                        recentCreditsCache.put(entry.getKey(), cashRecord);
                    }
                    break;
                    
                case META_UPDATE:
                    MetaState meta = entry.parseData(MetaState.class);
                    if (meta != null) {
                        this.metaState = meta;
                    }
                    break;
                    
                case SETTLE:
                case CHARGE:
                case ADJUST:
                    // 这些类型的数据已经在其他条目中处理
                    break;
                    
                default:
                    log.debug("Skipping WAL entry type: {}", entry.getType());
            }
        } catch (Exception e) {
            log.warn("Failed to replay WAL entry: {}", entry, e);
        }
    }
    
    /**
     * 从 DB 加载余额缓存
     */
    private void loadBalanceCacheFromDb() {
        try (DBIterator it = db.iterator()) {
            byte[] prefix = key(BALANCE_PREFIX);
            for (it.seek(prefix); it.hasNext(); it.next()) {
                String k = asString(it.peekNext().getKey());
                if (!k.startsWith(BALANCE_PREFIX)) break;
                String peerId = k.substring(BALANCE_PREFIX.length());
                PeerBalance balance = gson.fromJson(asString(it.peekNext().getValue()), PeerBalance.class);
                balanceCache.put(peerId, balance);
            }
        } catch (IOException e) {
            log.warn("Failed to load balance cache from DB", e);
        }
    }
    
    /**
     * 从 DB 加载最近充值记录
     */
    private void loadRecentCreditsFromDb() {
        long cutoffTs = System.currentTimeMillis() - (long) creditRetentionDays * 24 * 60 * 60 * 1000;
        
        try (DBIterator it = db.iterator()) {
            byte[] prefix = key(CASH_PREFIX);
            for (it.seek(prefix); it.hasNext(); it.next()) {
                String k = asString(it.peekNext().getKey());
                if (!k.startsWith(CASH_PREFIX)) break;
                String cashId = k.substring(CASH_PREFIX.length());
                CashRecord record = gson.fromJson(asString(it.peekNext().getValue()), CashRecord.class);
                // 只保留 100 天内的记录
                if (record.getTs() >= cutoffTs) {
                    recentCreditsCache.put(cashId, record);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to load recent credits from DB", e);
        }
    }
    
    /**
     * 启动定时快照任务
     */
    private void startSnapshotScheduler() {
        snapshotScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FapiBalanceManager-Snapshot");
            t.setDaemon(true);
            return t;
        });
        
        // 每 10 分钟检查是否需要快照
        snapshotScheduler.scheduleAtFixedRate(() -> {
            try {
                checkAndCreateSnapshot();
            } catch (Exception e) {
                log.warn("Snapshot check failed", e);
            }
        }, SnapshotManager.DEFAULT_SNAPSHOT_INTERVAL_MS, 
           SnapshotManager.DEFAULT_SNAPSHOT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 检查并创建快照
     */
    private void checkAndCreateSnapshot() {
        long now = System.currentTimeMillis();
        long txSinceLastSnapshot = txCount.get();
        long timeSinceLastSnapshot = now - lastSnapshotTime;
        
        // 达到事务阈值或时间阈值时创建快照
        boolean shouldSnapshot = txSinceLastSnapshot >= SnapshotManager.DEFAULT_SNAPSHOT_TX_THRESHOLD
                || timeSinceLastSnapshot >= SnapshotManager.DEFAULT_SNAPSHOT_INTERVAL_MS;
        
        if (shouldSnapshot) {
            try {
                createSnapshot();
            } catch (IOException e) {
                log.error("Failed to create snapshot", e);
            }
        }
    }
    
    /**
     * 创建快照
     */
    public void createSnapshot() throws IOException {
        lock.lock();
        try {
            snapshotManager.createSnapshot(
                    new HashMap<>(balanceCache),
                    new HashMap<>(recentCreditsCache),
                    metaState,
                    walManager.getCurrentSeq(),
                    metaState.getAuditTipHash()
            );
            
            // 写入检查点到 WAL
            walManager.writeCheckpoint(walManager.getCurrentSeq(), 
                    SnapshotManager.computeBalancesHash(balanceCache));
            
            // 清理旧 WAL 文件
            walManager.cleanupBeforeSnapshot(walManager.getCurrentSeq() - 1000);
            
            txCount.set(0);
            lastSnapshotTime = System.currentTimeMillis();
            
            log.info("Snapshot created successfully");
        } finally {
            lock.unlock();
        }
    }

    // -------- Public API --------

    /**
     * Charge by a metering record. A deterministic requestKey is derived from
     * the record to keep idempotency.
     * <p>
     * 特性：
     * - 免费消息类型：如果消息类型在 freeMessageTypes 中，则免费
     * - 重传去重：重传消息（retransmitCount > 0）不重复计费，返回首次计费结果
     * - 黑名单检查：黑名单用户拒绝服务
     */
    public ChargeResult checkAndCharge(MeterRecord meterRecord) {
        if (meterRecord == null) {
            return ChargeResult.error(ResultCode.INVALID_AMOUNT, "meterRecord is null");
        }
        String peerId = meterRecord.getPeerId();
        long payloadBytes = meterRecord.getPayloadBytes();
        if (!isValidKey(peerId)) {
            return ChargeResult.error(ResultCode.INVALID_KEY, "Invalid peerId");
        }
        if (payloadBytes < 0) {
            return ChargeResult.error(ResultCode.INVALID_AMOUNT, "payloadBytes < 0");
        }
        
        // 黑名单检查
        if (isBlacklisted(peerId)) {
            return ChargeResult.error(ResultCode.BLACKLISTED, "User is blacklisted");
        }
        
        // 免费消息类型检查
        String messageType = meterRecord.getMessageType() != null 
                ? meterRecord.getMessageType().name() : null;
        if (isFreeMessageType(messageType)) {
            return ChargeResult.ok(0, getBalance(peerId));
        }
        
        // 价格为0时免费
        if (pricing.getPricePerKb() == 0) {
            return ChargeResult.ok(0, getBalance(peerId));
        }
        
        // 生成原始消息的 requestKey（不含重传次数，用于首次计费识别）
        String originalKey = peerId + ":" + meterRecord.getDirection() + ":" + messageType + ":" +
            meterRecord.getSendTimestampMillis() + ":" + payloadBytes;
        String originalRequestKey = originalKey.length() > MAX_KEY_LENGTH 
                ? Hex.toHex(Hash.sha256(originalKey.getBytes(StandardCharsets.UTF_8))) 
                : originalKey;
        
        // 重传去重：如果是重传消息，检查原始消息是否已计费
        int retransmitCount = meterRecord.getRetransmitCount();
        if (retransmitCount > 0) {
            RequestRecord existingOriginal = getRequestRecord(originalRequestKey);
            if (existingOriginal != null) {
                // 原始消息已计费，重传不再计费
                log.debug("Retransmit detected for {}, skipping charge", originalRequestKey);
                return ChargeResult.fromExisting(ResultCode.ALREADY_EXISTS, existingOriginal, getBalance(peerId));
            }
            // 原始消息未找到，按首次计费（可能是原始消息丢失）
            log.debug("Retransmit {} but original not found, charging as new", originalRequestKey);
        }

        long kb = (payloadBytes + 1023) / 1024;
        long amount = safeMultiply(kb, pricing.getPricePerKb());
        if (amount < 0 || amount > MAX_AMOUNT) {
            return ChargeResult.error(ResultCode.INVALID_AMOUNT, "Calculated charge is invalid");
        }

        ChargeMeta meta = new ChargeMeta(meterRecord.getDirection() == null ? null : meterRecord.getDirection().name(),
            messageType,
            payloadBytes,
            meterRecord.getSendTimestampMillis(),
            meterRecord.getReceiveTimestampMillis(),
            retransmitCount,
            meterRecord.getRttMicros(),
            meterRecord.getLossRateHint());

        String metaJson = gson.toJson(meta);
        if (metaJson.getBytes(StandardCharsets.UTF_8).length > MAX_META_BYTES) {
            return ChargeResult.error(ResultCode.INVALID_META, "Meta too large");
        }

        return charge(originalRequestKey, peerId, amount, metaJson);
    }

    /**
     * Charge a peer with a provided requestKey.
     */
    public ChargeResult charge(String requestKey, String peerId, long amount, String meta) {
        if (!isValidKey(requestKey) || !isValidKey(peerId)) {
            return ChargeResult.error(ResultCode.INVALID_KEY, "Invalid requestKey or peerId");
        }
        if (!isValidAmount(amount)) {
            return ChargeResult.error(ResultCode.INVALID_AMOUNT, "Invalid amount");
        }
        if (meta != null && meta.getBytes(StandardCharsets.UTF_8).length > MAX_META_BYTES) {
            return ChargeResult.error(ResultCode.INVALID_META, "Meta too large");
        }

        lock.lock();
        try {
            RequestRecord existing = getRequestRecord(requestKey);
            if (existing != null) {
                boolean conflict = !peerId.equals(existing.getPeerId()) || amount != existing.getAmount();
                ResultCode code = conflict ? ResultCode.ALREADY_EXISTS_WITH_CONFLICT : ResultCode.ALREADY_EXISTS;
                return ChargeResult.fromExisting(code, existing, getBalance(peerId));
            }

            PeerBalance balance = getPeerBalance(peerId);
            long projected = balance.getBalance() - amount;
            ResultCode code;
            RequestStatus status;
            long newBalance = balance.getBalance();

            if (projected >= -balance.getCreditLimit()) {
                newBalance = projected;
                balance.bumpSeq();
                balance.setBalance(newBalance);
                status = projected < 0 ? RequestStatus.INSUFFICIENT : RequestStatus.OK;
                code = projected < 0 ? ResultCode.INSUFFICIENT_BALANCE_BUT_WITHIN_CREDIT : ResultCode.OK;
                persistCharge(requestKey, peerId, amount, meta, status, balance);
            } else {
                status = RequestStatus.CREDIT_EXCEEDED;
                code = ResultCode.CREDIT_EXCEEDED;
                persistRequestOnly(requestKey, peerId, amount, meta, status);
            }

            return new ChargeResult(code, amount, newBalance, balance.getCreditLimit(), status);
        } catch (Exception e) {
            log.error("Charge failed", e);
            return ChargeResult.error(ResultCode.ERROR, e.getMessage());
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 基于双向流量计费
     * <p>
     * 根据请求和响应的字节数计算总费用并扣费。
     * 
     * @param requestKey 请求唯一标识（用于幂等）
     * @param peerId 用户ID
     * @param requestBytes 请求数据字节数
     * @param responseBytes 响应数据字节数
     * @param meta 元数据（可选）
     * @return 扣费结果
     */
    public ChargeResult chargeByTraffic(String requestKey, String peerId, 
                                         long requestBytes, long responseBytes, String meta) {
        long totalFee = pricing.calculateFee(requestBytes, responseBytes);
        return charge(requestKey, peerId, totalFee, meta);
    }
    
    /**
     * 检查用户是否有足够余额支付费用
     * 
     * @param peerId 用户ID
     * @param amount 所需费用（聪）
     * @return true 如果余额+信用额度 >= amount
     */
    public boolean canAfford(String peerId, long amount) {
        if (!isValidKey(peerId)) {
            return false;
        }
        BalanceView balance = getBalance(peerId);
        return balance.getAvailable() >= amount;
    }
    
    /**
     * 预估请求费用（用于预校验）
     * 
     * @param requestBytes 请求数据字节数
     * @param estimatedResponseBytes 预估响应数据字节数
     * @return 预估费用（聪）
     */
    public long estimateFee(long requestBytes, long estimatedResponseBytes) {
        return pricing.calculateFee(requestBytes, estimatedResponseBytes);
    }

    /**
     * Credit (recharge) a user with cashId idempotency.
     */
    public CreditResult credit(String userId, String cashId, long amount, String src, CreditStatus status,
                               Long height, String blockId) {
        return credit(userId, cashId, amount, src, status, height, blockId, null);
    }
    
    /**
     * Credit (recharge) a user with cashId idempotency and via channel support.
     * 
     * @param via 订单渠道FID（可为null，如果有则计算订单分成）
     */
    public CreditResult credit(String userId, String cashId, long amount, String src, CreditStatus status,
                               Long height, String blockId, String via) {
        log.info("Credit attempt: userId={}, cashId={}, amount={} satoshi ({} FCH), status={}, height={}, blockId={}, src={}, via={}",
                userId, cashId, amount, FchUtils.satoshiToCoin(amount), status, height, blockId, src, via);
        
        if (!isValidKey(userId) || !isValidKey(cashId)) {
            log.warn("Credit failed: Invalid key - userId={}, cashId={}", userId, cashId);
            return CreditResult.error(ResultCode.INVALID_KEY, "Invalid userId or cashId");
        }
        if (!isValidAmount(amount)) {
            log.warn("Credit failed: Invalid amount={}", amount);
            return CreditResult.error(ResultCode.INVALID_AMOUNT, "Invalid amount");
        }

        lock.lock();
        try {
            CashRecord existing = getCashRecord(cashId);
            if (existing != null) {
                boolean conflict = !userId.equals(existing.getUserId()) || existing.getAmount() != amount;
                if (conflict) {
                    log.warn("Credit failed: cashId conflict - existing userId={}, amount={}, new userId={}, amount={}",
                            existing.getUserId(), existing.getAmount(), userId, amount);
                    return CreditResult.error(ResultCode.ALREADY_EXISTS_WITH_CONFLICT, "cashId conflict");
                }
                if (existing.getStatus() == status) {
                    log.info("Credit skipped: Already exists with same status - cashId={}, userId={}, amount={}, status={}",
                            cashId, userId, amount, status);
                    return CreditResult.fromExisting(ResultCode.ALREADY_EXISTS, existing, getBalance(userId));
                }
            }

            long delta = creditDelta(existing == null ? null : existing.getStatus(), status, amount);
            PeerBalance balance = getPeerBalance(userId);
            long oldBalance = balance.getBalance();
            long newBalance = balance.getBalance() + delta;
            balance.bumpSeq();
            balance.setBalance(newBalance);

            log.info("Credit processing: userId={}, cashId={}, oldBalance={} sat, delta={} sat, newBalance={} sat ({} FCH)",
                    userId, cashId, oldBalance, delta, newBalance, FchUtils.satoshiToCoin(newBalance));

            CashRecord record = new CashRecord(userId, amount, src, status, height, blockId, System.currentTimeMillis());
            persistCredit(cashId, record, balance);
            
            // 计算并记录订单渠道分成
            if (via != null && isValidKey(via) && pricing.getOrderViaShareBps() > 0 && 
                status == CreditStatus.CONFIRMED && delta > 0) {
                long orderShare = (delta * pricing.getOrderViaShareBps()) / 10000;
                if (orderShare > 0) {
                    addChannelOrderShare(via, orderShare);
                    log.info("Order share added: via={}, share={} sat ({} FCH)", 
                            via, orderShare, FchUtils.satoshiToCoin(orderShare));
                }
            }

            log.info("Credit successful: cashId={}, userId={}, amount={} sat ({} FCH), newBalance={} sat ({} FCH), creditLimit={} sat",
                    cashId, userId, amount, FchUtils.satoshiToCoin(amount), newBalance, FchUtils.satoshiToCoin(newBalance), balance.getCreditLimit());
            
            return new CreditResult(ResultCode.OK, amount, newBalance, balance.getCreditLimit(), status);
        } catch (Exception e) {
            log.error("Credit failed with exception: userId={}, cashId={}, amount={}", userId, cashId, amount, e);
            return CreditResult.error(ResultCode.ERROR, e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get the latest balance snapshot for a peer.
     */
    public BalanceView getBalance(String peerId) {
        if (!isValidKey(peerId)) {
            log.debug("getBalance: Invalid peerId={}, returning zero balance", peerId);
            return new BalanceView(peerId, 0, defaultCreditLimit, 0, 0);
        }
        PeerBalance balance = getPeerBalance(peerId);
        BalanceView view = new BalanceView(peerId, balance.getBalance(), balance.getCreditLimit(),
            balance.getBalance() + balance.getCreditLimit(), balance.getSeq());
//        log.debug("getBalance: peerId={}, balance={} sat ({} FCH), creditLimit={} sat, available={} sat ({} FCH), seq={}",
//                peerId, balance.getBalance(), FchUtils.satoshiToCoin(balance.getBalance()),
//                balance.getCreditLimit(), view.getAvailable(), FchUtils.satoshiToCoin(view.getAvailable()), balance.getSeq());
        return view;
    }

    /**
     * Reload pricing parameters from the Service params (manual refresh).
     */
    public synchronized boolean refreshPricingFromService() {
        Pricing newPricing = parsePricing(service);
        this.pricing = newPricing;
        log.info("Pricing refreshed: pricePerKB={}, orderViaShareBps={}, consumeViaShareBps={}",
            newPricing.getPricePerKb(), newPricing.getOrderViaShareBps(), newPricing.getConsumeViaShareBps());
        return true;
    }

    public Pricing getPricing() {
        return pricing;
    }

    /**
     * Tail recent audit records (append-only).
     */
    public List<AuditRecord> getRecentAudit(int limit) {
        List<AuditRecord> all = new ArrayList<>();
        try (DBIterator it = db.iterator()) {
            byte[] prefix = key(AUDIT_PREFIX);
            for (it.seek(prefix); it.hasNext(); it.next()) {
                String k = asString(it.peekNext().getKey());
                if (!k.startsWith(AUDIT_PREFIX)) break;
                all.add(gson.fromJson(asString(it.peekNext().getValue()), AuditRecord.class));
            }
        } catch (IOException e) {
            log.warn("Failed to read audit log", e);
        }
        if (all.size() <= limit) return all;
        return all.subList(all.size() - limit, all.size());
    }
    
    // ==================== 渠道余额管理 ====================
    
    /**
     * 消费扣费（带渠道分成）
     * 
     * @param via 消费渠道FID（可为null，如果有则计算消费分成）
     */
    public ChargeResult chargeWithVia(String requestKey, String peerId, long amount, String meta, String via) {
        ChargeResult result = charge(requestKey, peerId, amount, meta);
        
        // 计算并记录消费渠道分成
        if (result.getCode() == ResultCode.OK && 
            via != null && isValidKey(via) && pricing.getConsumeViaShareBps() > 0) {
            
            long consumeShare = (amount * pricing.getConsumeViaShareBps()) / 10000;
            if (consumeShare > 0) {
                addChannelConsumeShare(via, consumeShare);
                log.debug("Consume share added: via={}, share={} sat", via, consumeShare);
            }
        }
        
        return result;
    }
    
    /**
     * 累加渠道订单分成
     */
    private void addChannelOrderShare(String channelId, long amount) {
        lock.lock();
        try {
            ChannelBalance balance = getChannelBalance(channelId);
            balance.setOrderShare(balance.getOrderShare() + amount);
            balance.bumpSeq();
            
            try (WriteBatch batch = db.createWriteBatch()) {
                putChannelBalance(batch, channelId, balance);
                db.write(batch);
                
                // 写入 WAL
                if (walManager != null) {
                    walManager.append(WalEntryType.CHANNEL_UPDATE, channelId, balance);
                }
            }
            
            log.debug("Channel order share updated: channelId={}, newOrderShare={} sat", 
                    channelId, balance.getOrderShare());
        } catch (IOException e) {
            log.error("Failed to add channel order share", e);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 累加渠道消费分成
     */
    private void addChannelConsumeShare(String channelId, long amount) {
        lock.lock();
        try {
            ChannelBalance balance = getChannelBalance(channelId);
            balance.setConsumeShare(balance.getConsumeShare() + amount);
            balance.bumpSeq();
            
            try (WriteBatch batch = db.createWriteBatch()) {
                putChannelBalance(batch, channelId, balance);
                db.write(batch);
                
                // 写入 WAL
                if (walManager != null) {
                    walManager.append(WalEntryType.CHANNEL_UPDATE, channelId, balance);
                }
            }
            
            log.debug("Channel consume share updated: channelId={}, newConsumeShare={} sat", 
                    channelId, balance.getConsumeShare());
        } catch (IOException e) {
            log.error("Failed to add channel consume share", e);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 获取渠道余额
     */
    private ChannelBalance getChannelBalance(String channelId) {
        byte[] value = db.get(key(CHANNEL_BALANCE_PREFIX + channelId));
        if (value == null) {
            return new ChannelBalance(0, 0, 0, 1);
        }
        return gson.fromJson(asString(value), ChannelBalance.class);
    }
    
    /**
     * 保存渠道余额
     */
    private void putChannelBalance(WriteBatch batch, String channelId, ChannelBalance balance) {
        batch.put(key(CHANNEL_BALANCE_PREFIX + channelId), toBytes(balance));
    }
    
    /**
     * 获取渠道余额视图（公开方法）
     */
    public ChannelBalanceView getChannelBalanceView(String channelId) {
        if (!isValidKey(channelId)) {
            return new ChannelBalanceView(channelId, 0, 0, 0, 0);
        }
        ChannelBalance balance = getChannelBalance(channelId);
        return new ChannelBalanceView(
            channelId,
            balance.getOrderShare(),
            balance.getConsumeShare(),
            balance.getTotalPending(),
            balance.getSettledAmount()
        );
    }
    
    /**
     * 获取所有渠道余额（用于结算）
     */
    public Map<String, ChannelBalance> getAllChannelBalances() {
        Map<String, ChannelBalance> result = new HashMap<>();
        
        try (DBIterator it = db.iterator()) {
            byte[] prefix = key(CHANNEL_BALANCE_PREFIX);
            for (it.seek(prefix); it.hasNext(); it.next()) {
                String k = asString(it.peekNext().getKey());
                if (!k.startsWith(CHANNEL_BALANCE_PREFIX)) break;
                String channelId = k.substring(CHANNEL_BALANCE_PREFIX.length());
                ChannelBalance balance = gson.fromJson(asString(it.peekNext().getValue()), ChannelBalance.class);
                if (balance.getTotalPending() > 0) {
                    result.put(channelId, balance);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read channel balances", e);
        }
        
        return result;
    }
    
    // ==================== 结算功能 ====================
    
    /**
     * 周期结算（推荐使用此方法）
     * <p>
     * 基于区块高度范围进行周期结算，分配渠道分成和股东利润。
     * 
     * @param fromHeight 起始区块高度（上次结算高度）
     * @param toHeight 结束区块高度（当前高度）
     * @param stakeholders 股份持有者及其分成比例（万分比，可为null）
     * @return 结算结果
     */
    public SettleResult periodicSettle(long fromHeight, long toHeight, Map<String, Long> stakeholders) {
        String cycleId = "settle:" + fromHeight + "-" + toHeight;
        
        if (fromHeight >= toHeight) {
            return SettleResult.error(ResultCode.INVALID_AMOUNT, "Invalid height range");
        }
        
        lock.lock();
        try {
            // 检查幂等
            SettleRecord existing = getSettleRecord(cycleId);
            if (existing != null) {
                return SettleResult.fromExisting(ResultCode.ALREADY_EXISTS, existing);
            }
            
            // 1. 获取所有渠道的待结算分成
            Map<String, ChannelBalance> channelBalances = getAllChannelBalances();
            
            // 2. 计算周期统计（从审计记录）
            CycleStats stats = calculateCycleStats(fromHeight, toHeight);
            
            // 3. 分配渠道分成（考虑最小结算金额）
            Map<String, Long> distributions = new HashMap<>();
            long totalChannelShare = 0;
            
            for (Map.Entry<String, ChannelBalance> entry : channelBalances.entrySet()) {
                String channelId = entry.getKey();
                ChannelBalance balance = entry.getValue();
                long pending = balance.getTotalPending();
                
                // 检查是否达到最小结算金额
                if (pending >= minSettleAmount) {
                    distributions.put(channelId, pending);
                    totalChannelShare += pending;
                } else {
                    log.debug("Channel {} pending {} sat below minSettleAmount {} sat, skipping", 
                            channelId, pending, minSettleAmount);
                }
            }
            
            // 4. 计算剩余利润分配给股东
            long netProfit = stats.netIncome - totalChannelShare;
            long reserveFund = 100_000_000L; // 1 FCH 备用金
            long availableForStakeholders = Math.max(0, netProfit - reserveFund);
            long totalStakeholderShare = 0;
            
            if (stakeholders != null && !stakeholders.isEmpty() && availableForStakeholders > 0) {
                long totalShares = stakeholders.values().stream().mapToLong(Long::longValue).sum();
                
                for (Map.Entry<String, Long> entry : stakeholders.entrySet()) {
                    String stakeholderId = entry.getKey();
                    long share = entry.getValue();
                    long profitShare = (availableForStakeholders * share) / totalShares;
                    
                    // 股东分成也要检查最小结算金额
                    if (profitShare >= minSettleAmount) {
                        distributions.merge(stakeholderId, profitShare, Long::sum);
                        totalStakeholderShare += profitShare;
                    }
                }
            }
            
            // 5. 执行结算
            long totalDistributed = distributions.values().stream().mapToLong(Long::longValue).sum();
            
            SettleRecord settleRecord = new SettleRecord(
                cycleId, stats.totalIncome, stats.totalSpend, stats.netIncome,
                totalDistributed, distributions, SettleStatus.CONFIRMED, System.currentTimeMillis()
            );
            
            persistPeriodicSettle(cycleId, settleRecord, channelBalances, distributions);
            
            log.info("Periodic settlement completed: cycleId={}, channels={}, channelShare={}, stakeholderShare={}, totalDistributed={}", 
                    cycleId, channelBalances.size(), totalChannelShare, totalStakeholderShare, totalDistributed);
            
            return new SettleResult(ResultCode.OK, settleRecord);
            
        } catch (Exception e) {
            log.error("Periodic settle failed", e);
            return SettleResult.error(ResultCode.ERROR, e.getMessage());
        } finally {
            lock.unlock();
        }
    }
//
//    /**
//     * 旧版结算方法（保留兼容性）
//     * @deprecated 请使用 {@link #periodicSettle(long, long, Map)}
//     */
//    @Deprecated
//    public SettleResult settle(String cycleId, Map<String, Long> stakeholders) {
//        // 使用当前高度范围调用新方法
//        Long lastHeight = getLastSettleHeight();
//        long fromHeight = lastHeight != null ? lastHeight : 0;
//        long toHeight = metaState.getBestHeight();
//
//        if (toHeight <= fromHeight) {
//            return SettleResult.error(ResultCode.INVALID_AMOUNT, "No new blocks to settle");
//        }
//
//        SettleResult result = periodicSettle(fromHeight, toHeight, stakeholders);
//        if (result.isSuccess()) {
//            setLastSettleHeight(toHeight);
//        }
//        return result;
//    }
//
    /**
     * 按区块高度范围计算周期统计
     */
    private CycleStats calculateCycleStats(long fromHeight, long toHeight) {
        long totalIncome = 0;
        long totalSpend = 0;
        
        for (AuditRecord record : getRecentAudit(Integer.MAX_VALUE)) {
            // 按区块高度过滤
            long recordHeight = record.getBestHeight();
            if (recordHeight <= fromHeight || recordHeight > toHeight) {
                continue;
            }
            
            if (record.getType() == AuditType.income && "confirmed".equals(record.getStatus())) {
                totalIncome += record.getAmount();
            } else if (record.getType() == AuditType.spend && "ok".equals(record.getStatus())) {
                totalSpend += record.getAmount();
            }
        }
        
        return new CycleStats(totalIncome, totalSpend, totalIncome - totalSpend);
    }
    
    /**
     * 持久化周期结算
     */
    private void persistPeriodicSettle(String cycleId, SettleRecord record, 
                                        Map<String, ChannelBalance> channelBalances,
                                        Map<String, Long> distributions) {
        try (WriteBatch batch = db.createWriteBatch()) {
            // 保存结算记录
            batch.put(key(SETTLE_PREFIX + cycleId), toBytes(record));
            
            // 处理渠道余额：将已结算的分成加入可用余额，清零待结算分成
            for (Map.Entry<String, ChannelBalance> entry : channelBalances.entrySet()) {
                String channelId = entry.getKey();
                ChannelBalance balance = entry.getValue();
                Long distributedAmount = distributions.get(channelId);
                
                if (distributedAmount != null && distributedAmount > 0) {
                    // 累加已结算金额
                    balance.setSettledAmount(balance.getSettledAmount() + distributedAmount);
                    // 清零已结算的部分
                    long orderSettled = Math.min(balance.getOrderShare(), distributedAmount);
                    long consumeSettled = distributedAmount - orderSettled;
                    balance.setOrderShare(balance.getOrderShare() - orderSettled);
                    balance.setConsumeShare(Math.max(0, balance.getConsumeShare() - consumeSettled));
                    balance.bumpSeq();
                    
                    putChannelBalance(batch, channelId, balance);
                    
                    // 将分成加入渠道方的可用余额
                    PeerBalance peerBalance = getPeerBalance(channelId);
                    peerBalance.setBalance(peerBalance.getBalance() + distributedAmount);
                    peerBalance.bumpSeq();
                    putPeerBalance(batch, channelId, peerBalance);
                    balanceCache.put(channelId, peerBalance);
                }
            }
            
            // 处理股东分成（非渠道）
            for (Map.Entry<String, Long> entry : distributions.entrySet()) {
                String peerId = entry.getKey();
                // 跳过已处理的渠道
                if (channelBalances.containsKey(peerId)) {
                    continue;
                }
                
                long amount = entry.getValue();
                PeerBalance peerBalance = getPeerBalance(peerId);
                peerBalance.setBalance(peerBalance.getBalance() + amount);
                peerBalance.bumpSeq();
                putPeerBalance(batch, peerId, peerBalance);
                balanceCache.put(peerId, peerBalance);
            }
            
            // 写入审计记录
            AuditRecord audit = AuditRecord.settle(cycleId, record.getTotalDistributed(), metaState);
            appendAudit(batch, audit);
            
            writeMeta(batch);
            db.write(batch);
            
            // 写入 WAL
            if (walManager != null) {
                walManager.append(WalEntryType.SETTLE, cycleId, record);
            }
            
            txCount.incrementAndGet();
            
        } catch (IOException e) {
            throw new IllegalStateException("Persist periodic settle failed", e);
        }
    }
    
    /**
     * 获取上次结算高度
     */
    public Long getLastSettleHeight() {
        byte[] value = db.get(key(LAST_SETTLE_HEIGHT_KEY));
        if (value == null) {
            return null;
        }
        try {
            String strValue = asString(value).trim();
            if (strValue.startsWith("\"") && strValue.endsWith("\"")) {
                strValue = strValue.substring(1, strValue.length() - 1);
            }
            return Long.parseLong(strValue);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse lastSettleHeight: {}", asString(value));
            return null;
        }
    }
    
    /**
     * 保存上次结算高度
     */
    public void setLastSettleHeight(long height) {
        lock.lock();
        try {
            try (WriteBatch batch = db.createWriteBatch()) {
                batch.put(key(LAST_SETTLE_HEIGHT_KEY), toBytes(String.valueOf(height)));
                db.write(batch);
                log.debug("Last settle height saved: {}", height);
            } catch (IOException e) {
                log.error("Failed to save last settle height", e);
            }
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 获取最小结算金额
     */
    public long getMinSettleAmount() {
        return minSettleAmount;
    }
    
    /**
     * 获取结算记录
     */
    private SettleRecord getSettleRecord(String cycleId) {
        byte[] value = db.get(key(SETTLE_PREFIX + cycleId));
        return value == null ? null : gson.fromJson(asString(value), SettleRecord.class);
    }
    
    // ==================== 回滚检测与处理 ====================
    
    /**
     * 更新最佳区块高度和ID
     * <p>
     * 检测回滚并处理。
     * 
     * @param height 新的区块高度
     * @param blockId 新的区块ID
     * @return 是否发生回滚
     */
    public boolean updateBestBlock(long height, String blockId) {
        lock.lock();
        try {
            long currentHeight = metaState.getBestHeight();
            String currentBlockId = metaState.getBestBlockId();
            
            // 检测回滚：同高度但 blockId 不同
            if (height <= currentHeight && !blockId.equals(currentBlockId)) {
                log.warn("Rollback detected: height={}, oldBlockId={}, newBlockId={}", 
                        height, currentBlockId, blockId);
                
                // 处理回滚
                handleRollback(height, blockId);
                return true;
            }
            
            // 正常更新
            if (height > currentHeight) {
                metaState.setBestHeight(height);
                metaState.setBestBlockId(blockId);
                
                // 持久化元数据
                try (WriteBatch batch = db.createWriteBatch()) {
                    writeMeta(batch);
                    db.write(batch);
                    
                    // 写入 WAL
                    if (walManager != null) {
                        walManager.append(WalEntryType.META_UPDATE, META_KEY, metaState);
                    }
                } catch (IOException e) {
                    log.error("Failed to persist meta update", e);
                }
            }
            
            return false;
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 处理回滚
     * <p>
     * 对回滚窗口内的充值追加冲减记录，然后重扫。
     */
    private void handleRollback(long rollbackHeight, String newBlockId) {
        log.info("Handling rollback from height {}", rollbackHeight);
        
        long startHeight = Math.max(0, rollbackHeight - ROLLBACK_WINDOW);
        List<String> affectedCashIds = new ArrayList<>();
        
        // 查找受影响的充值记录
        for (Map.Entry<String, CashRecord> entry : recentCreditsCache.entrySet()) {
            CashRecord record = entry.getValue();
            if (record.getHeight() != null && 
                record.getHeight() >= startHeight && 
                record.getStatus() == CreditStatus.CONFIRMED) {
                affectedCashIds.add(entry.getKey());
            }
        }
        
        // 冲减受影响的充值
        for (String cashId : affectedCashIds) {
            CashRecord record = recentCreditsCache.get(cashId);
            if (record != null) {
                // 追加调整记录（不直接修改原记录）
                adjustCredit(cashId, record.getUserId(), -record.getAmount(), 
                        "rollback:" + rollbackHeight, CreditStatus.REVERTED);
            }
        }
        
        // 更新元数据
        metaState.setBestHeight(rollbackHeight);
        metaState.setBestBlockId(newBlockId);
        
        log.info("Rollback handled: {} credits affected", affectedCashIds.size());
    }
    
    /**
     * 调整充值记录（用于回滚等场景）
     */
    public CreditResult adjustCredit(String cashId, String userId, long adjustAmount, 
                                      String note, CreditStatus newStatus) {
        lock.lock();
        try {
            PeerBalance balance = getPeerBalance(userId);
            long newBalance = balance.getBalance() + adjustAmount;
            balance.bumpSeq();
            balance.setBalance(newBalance);
            
            // 更新或创建调整记录
            CashRecord adjustRecord = new CashRecord(
                    userId, adjustAmount, note, newStatus, 
                    metaState.getBestHeight(), metaState.getBestBlockId(), 
                    System.currentTimeMillis()
            );
            
            String adjustCashId = cashId + ":adjust:" + System.currentTimeMillis();
            
            AuditRecord audit = AuditRecord.adjust(adjustCashId, userId, adjustAmount, metaState, note);
            
            try (WriteBatch batch = db.createWriteBatch()) {
                putPeerBalance(batch, userId, balance);
                putCashRecord(batch, adjustCashId, adjustRecord);
                appendAudit(batch, audit);
                writeMeta(batch);
                db.write(batch);
                
                // 更新缓存
                balanceCache.put(userId, balance);
                recentCreditsCache.put(adjustCashId, adjustRecord);
                
                // 写入 WAL
                if (walManager != null) {
                    walManager.append(WalEntryType.ADJUST, adjustCashId, adjustRecord);
                }
                
                txCount.incrementAndGet();
            } catch (IOException e) {
                throw new IllegalStateException("Persist adjust failed", e);
            }
            
            return new CreditResult(ResultCode.OK, adjustAmount, newBalance, 
                    balance.getCreditLimit(), newStatus);
            
        } finally {
            lock.unlock();
        }
    }
    
    // ==================== 黑名单功能 ====================
    
    /**
     * 检查用户是否在黑名单中
     */
    public boolean isBlacklisted(String peerId) {
        if (!isValidKey(peerId)) {
            return false;
        }
        PeerBalance balance = getPeerBalance(peerId);
        return (balance.getFlags() & FLAG_BLACKLISTED) != 0;
    }
    
    /**
     * 将用户加入黑名单
     */
    public boolean addToBlacklist(String peerId) {
        return setBlacklistFlag(peerId, true);
    }
    
    /**
     * 将用户从黑名单移除
     */
    public boolean removeFromBlacklist(String peerId) {
        return setBlacklistFlag(peerId, false);
    }
    
    /**
     * 设置黑名单标志
     */
    private boolean setBlacklistFlag(String peerId, boolean blacklist) {
        if (!isValidKey(peerId)) {
            return false;
        }
        
        lock.lock();
        try {
            PeerBalance balance = getPeerBalance(peerId);
            int oldFlags = balance.getFlags();
            int newFlags = blacklist 
                    ? (oldFlags | FLAG_BLACKLISTED) 
                    : (oldFlags & ~FLAG_BLACKLISTED);
            
            if (oldFlags == newFlags) {
                return true; // 已经是目标状态
            }
            
            balance.setFlags(newFlags);
            balance.bumpSeq();
            
            try (WriteBatch batch = db.createWriteBatch()) {
                putPeerBalance(batch, peerId, balance);
                writeMeta(batch);
                db.write(batch);
                
                // 更新缓存
                balanceCache.put(peerId, balance);
                
                // 写入 WAL
                if (walManager != null) {
                    walManager.append(WalEntryType.BALANCE_UPDATE, peerId, balance);
                }
            } catch (IOException e) {
                log.error("Failed to set blacklist flag", e);
                return false;
            }
            
            log.info("Blacklist {} for peer {}", blacklist ? "added" : "removed", peerId);
            return true;
            
        } finally {
            lock.unlock();
        }
    }
    
    // ==================== 免费消息类型管理 ====================
    
    /**
     * 设置免费消息类型列表
     */
    public void setFreeMessageTypes(Set<String> types) {
        this.freeMessageTypes = types != null ? new HashSet<>(types) : new HashSet<>();
    }
    
    /**
     * 检查消息类型是否免费
     */
    public boolean isFreeMessageType(String messageType) {
        return messageType != null && freeMessageTypes.contains(messageType);
    }
    
    /**
     * 获取免费消息类型列表
     */
    public Set<String> getFreeMessageTypes() {
        return new HashSet<>(freeMessageTypes);
    }
    
    // ==================== 元数据访问 ====================
    
    /**
     * 获取当前元数据状态
     */
    public MetaState getMetaState() {
        return metaState;
    }
    
    /**
     * 获取最佳区块高度
     */
    public long getBestHeight() {
        return metaState != null ? metaState.getBestHeight() : 0;
    }
    
    /**
     * 获取最佳区块ID
     */
    public String getBestBlockId() {
        return metaState != null ? metaState.getBestBlockId() : null;
    }
    
    /**
     * 获取最后订单扫描高度（用于充值扫描）
     * 这个高度是最后处理的订单的 birthHeight，不是区块链高度
     */
    public Long getLastOrderScanHeight() {
        byte[] value = db.get(key(LAST_ORDER_SCAN_HEIGHT_KEY));
        if (value == null) {
            return null;
        }
        try {
            String strValue = asString(value);
            // 处理可能存在的引号（兼容旧数据格式）
            if (strValue != null) {
                strValue = strValue.trim();
                if (strValue.startsWith("\"") && strValue.endsWith("\"")) {
                    strValue = strValue.substring(1, strValue.length() - 1);
                }
            }
            return Long.parseLong(strValue);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse lastOrderScanHeight: {}", asString(value));
            return null;
        }
    }
    
    /**
     * 保存最后订单扫描高度（用于充值扫描）
     * @param height 最后处理的订单的 birthHeight（不是区块链高度）
     */
    public void setLastOrderScanHeight(long height) {
        lock.lock();
        try {
            try (WriteBatch batch = db.createWriteBatch()) {
                batch.put(key(LAST_ORDER_SCAN_HEIGHT_KEY), toBytes(String.valueOf(height)));
                db.write(batch);
                log.debug("Last order scan height saved: {}", height);
            } catch (IOException e) {
                log.error("Failed to save last order scan height", e);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check if the manager is closed.
     */
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        
        // 停止快照调度器
        if (snapshotScheduler != null) {
            snapshotScheduler.shutdown();
            try {
                snapshotScheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // 创建最终快照
        try {
            if (snapshotManager != null && walManager != null) {
                createSnapshot();
            }
        } catch (IOException e) {
            log.warn("Failed to create final snapshot on close", e);
        }
        
        // 关闭 WAL 管理器
        if (walManager != null) {
            try {
                walManager.close();
            } catch (IOException e) {
                log.warn("Failed to close WAL manager", e);
            }
        }
        
        // 关闭快照管理器
        if (snapshotManager != null) {
            try {
                snapshotManager.close();
            } catch (IOException e) {
                log.warn("Failed to close Snapshot manager", e);
            }
        }
        
        // 关闭数据库
        try {
            db.close();
            log.info("FapiBalanceManager closed");
        } catch (IOException e) {
            log.warn("Failed to close FapiBalanceManager DB", e);
        }
    }

    // -------- Persistence helpers --------

    private void persistCharge(String requestKey, String peerId, long amount, String meta, RequestStatus status,
                               PeerBalance balance) {
        long now = System.currentTimeMillis();
        RequestRecord requestRecord = new RequestRecord(peerId, amount, metaHash(meta), status, now);
        AuditRecord audit = AuditRecord.spend(requestKey, peerId, amount, metaState, status, meta);

        try (WriteBatch batch = db.createWriteBatch()) {
            putPeerBalance(batch, peerId, balance);
            putRequestRecord(batch, requestKey, requestRecord);
            appendAudit(batch, audit);
            writeMeta(batch);
            db.write(batch);
        } catch (IOException e) {
            throw new IllegalStateException("Persist charge failed", e);
        }
    }

    private void persistRequestOnly(String requestKey, String peerId, long amount, String meta, RequestStatus status) {
        long now = System.currentTimeMillis();
        RequestRecord requestRecord = new RequestRecord(peerId, amount, metaHash(meta), status, now);
        AuditRecord audit = AuditRecord.spend(requestKey, peerId, amount, metaState, status, meta);

        try (WriteBatch batch = db.createWriteBatch()) {
            putRequestRecord(batch, requestKey, requestRecord);
            appendAudit(batch, audit);
            writeMeta(batch);
            db.write(batch);
        } catch (IOException e) {
            throw new IllegalStateException("Persist request failed", e);
        }
    }

    private void persistCredit(String cashId, CashRecord record, PeerBalance balance) {
        log.debug("Persisting credit: cashId={}, userId={}, amount={}, status={}", 
                cashId, record.getUserId(), record.getAmount(), record.getStatus());
        AuditRecord audit = AuditRecord.income(cashId, record.getUserId(), record.getAmount(), metaState, record.getStatus(), record.getSrc());
        try (WriteBatch batch = db.createWriteBatch()) {
            putPeerBalance(batch, record.getUserId(), balance);
            putCashRecord(batch, cashId, record);
            appendAudit(batch, audit);
            writeMeta(batch);
            db.write(batch);
            log.debug("Credit persisted successfully: cashId={}", cashId);
        } catch (IOException e) {
            log.error("Failed to persist credit: cashId={}", cashId, e);
            throw new IllegalStateException("Persist credit failed", e);
        }
    }

    private PeerBalance getPeerBalance(String peerId) {
        byte[] value = db.get(key(BALANCE_PREFIX + peerId));
        if (value == null) {
            return new PeerBalance(0, defaultCreditLimit, 0, 0, 1);
        }
        return gson.fromJson(asString(value), PeerBalance.class);
    }

    private void putPeerBalance(WriteBatch batch, String peerId, PeerBalance balance) {
        batch.put(key(BALANCE_PREFIX + peerId), toBytes(balance));
    }

    private RequestRecord getRequestRecord(String requestKey) {
        byte[] value = db.get(key(REQUEST_PREFIX + requestKey));
        return value == null ? null : gson.fromJson(asString(value), RequestRecord.class);
    }

    private void putRequestRecord(WriteBatch batch, String requestKey, RequestRecord record) {
        batch.put(key(REQUEST_PREFIX + requestKey), toBytes(record));
    }

    private CashRecord getCashRecord(String cashId) {
        byte[] value = db.get(key(CASH_PREFIX + cashId));
        return value == null ? null : gson.fromJson(asString(value), CashRecord.class);
    }

    private void putCashRecord(WriteBatch batch, String cashId, CashRecord record) {
        batch.put(key(CASH_PREFIX + cashId), toBytes(record));
    }

    private MetaState loadMetaState() {
        byte[] value = db.get(key(META_KEY));
        if (value == null) {
            return new MetaState(0L, "", ZERO_HASH, 1L, 0L);
        }
        return gson.fromJson(asString(value), MetaState.class);
    }

    private void writeMeta(WriteBatch batch) {
        batch.put(key(META_KEY), toBytes(metaState));
    }

    private void appendAudit(WriteBatch batch, AuditRecord record) {
        record.setPrevHash(metaState.getAuditTipHash() == null ? ZERO_HASH : metaState.getAuditTipHash());
        String canonical = record.canonicalWithoutHash();
        String hash = Hash.sha256(canonical);
        record.setHash(hash);
        metaState.setAuditTipHash(hash);
        String auditKey = AUDIT_PREFIX + record.getTs() + "/" + Optional.ofNullable(record.getRequestKey()).orElseGet(() -> Optional.ofNullable(record.getCashId()).orElse("n/a"));
        batch.put(key(auditKey), toBytes(record));
    }

    // -------- Validation helpers --------

    private boolean isValidKey(String key) {
        if (key == null || key.length() > MAX_KEY_LENGTH) return false;
        return KEY_PATTERN.matcher(key).matches();
    }

    private boolean isValidAmount(long amount) {
        return amount >= 0 && amount <= MAX_AMOUNT;
    }

    private long safeMultiply(long a, long b) {
        if (a == 0 || b == 0) return 0;
        long result = a * b;
        if (result / a != b) {
            return -1;
        }
        return result;
    }

    private String metaHash(String meta) {
        if (meta == null) return "";
        return Hash.sha256(meta);
    }

    private long creditDelta(CreditStatus from, CreditStatus to, long amount) {
        if (to == CreditStatus.CONFIRMED) {
            return (from == CreditStatus.CONFIRMED) ? 0 : amount;
        }
        if (to == CreditStatus.REVERTED) {
            if (from == CreditStatus.CONFIRMED) {
                return -amount;
            }
            return 0;
        }
        return 0;
    }

    private Pricing parsePricing(Service service) {
        if (service == null) {
            throw new IllegalStateException("service is required before starting FapiBalanceManager");
        }

        // Resolve pricePerKB: use pricePerKBOut as fallback if pricePerKB is absent
        long pricePerKb;
        if (service.getPricePerKB() != null && !service.getPricePerKB().isEmpty()) {
            pricePerKb = FchUtils.coinToSatoshi(Double.parseDouble(service.getPricePerKB()));
        } else if (service.getPricePerKBOut() != null && !service.getPricePerKBOut().isEmpty()) {
            pricePerKb = FchUtils.coinToSatoshi(Double.parseDouble(service.getPricePerKBOut()));
        } else {
            throw new IllegalStateException("Either pricePerKB or pricePerKBOut is required before starting FapiBalanceManager");
        }
        if (pricePerKb < 0) {
            throw new IllegalStateException("pricePerKB must be >= 0");
        }
        
        // Resolve directional prices: default to pricePerKB if absent
        long pricePerKbIn = pricePerKb;
        long pricePerKbOut = pricePerKb;
        
        if (service.getPricePerKBIn() != null && !service.getPricePerKBIn().isEmpty()) {
            pricePerKbIn = FchUtils.coinToSatoshi(Double.parseDouble(service.getPricePerKBIn()));
            if (pricePerKbIn < 0) {
                throw new IllegalStateException("pricePerKBIn must be >= 0");
            }
        }
        
        if (service.getPricePerKBOut() != null && !service.getPricePerKBOut().isEmpty()) {
            pricePerKbOut = FchUtils.coinToSatoshi(Double.parseDouble(service.getPricePerKBOut()));
            if (pricePerKbOut < 0) {
                throw new IllegalStateException("pricePerKBOut must be >= 0");
            }
        }
        
        long orderShare = 0;
        if(service.getOrderViaShare()!=null)
            orderShare = parseShare(service.getOrderViaShare(), "orderViaShare");

        long consumeShare = 0;
        if(service.getConsumeViaShare()!=null)
            consumeShare = parseShare(service.getConsumeViaShare(), "consumeViaShare");

        return new Pricing(pricePerKb, pricePerKbIn, pricePerKbOut, orderShare, consumeShare);
    }

    private long parseShare(String shareStr, String name) {
        if (shareStr == null) {
            throw new IllegalStateException(name + " is required before starting FapiBalanceManager");
        }
        double share = Double.parseDouble(shareStr);
        long bps = Math.round(share * 10_000);
        if (bps < 0 || bps > 10_000) {
            throw new IllegalStateException(name + " must be between 0 and 1");
        }
        return bps;
    }

    // -------- Serialization helpers --------

    private byte[] key(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] toBytes(Object obj) {
        return gson.toJson(obj).getBytes(StandardCharsets.UTF_8);
    }

    private String asString(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // -------- Data classes --------

    public enum ResultCode {
        OK,
        ALREADY_EXISTS,
        ALREADY_EXISTS_WITH_CONFLICT,
        INVALID_KEY,
        INVALID_AMOUNT,
        INVALID_META,
        CREDIT_EXCEEDED,
        INSUFFICIENT_BALANCE_BUT_WITHIN_CREDIT,
        BLACKLISTED,
        ERROR
    }

    public enum RequestStatus {
        OK,
        FAILED,
        INSUFFICIENT,
        CREDIT_EXCEEDED
    }

    public enum CreditStatus {
        PENDING,
        CONFIRMED,
        REVERTED
    }

    public enum AuditType {
        income,
        spend,
        settle,
        adjust
    }

    public static class BalanceView {
        private final String peerId;
        private final long balance;
        private final long creditLimit;
        private final long available;
        private final long seq;

        public BalanceView(String peerId, long balance, long creditLimit, long available, long seq) {
            this.peerId = peerId;
            this.balance = balance;
            this.creditLimit = creditLimit;
            this.available = available;
            this.seq = seq;
        }

        public String getPeerId() {
            return peerId;
        }

        public long getBalance() {
            return balance;
        }

        public long getCreditLimit() {
            return creditLimit;
        }

        public long getAvailable() {
            return available;
        }

        public long getSeq() {
            return seq;
        }
    }

    public static class Pricing {
        private final long pricePerKb;      // 向后兼容，双向统一价格
        private final long pricePerKbIn;    // 请求数据价格（上传）
        private final long pricePerKbOut;   // 响应数据价格（下载）
        private final long orderViaShareBps;
        private final long consumeViaShareBps;

        public Pricing(long pricePerKb, long orderViaShareBps, long consumeViaShareBps) {
            this(pricePerKb, pricePerKb, pricePerKb, orderViaShareBps, consumeViaShareBps);
        }
        
        public Pricing(long pricePerKb, long pricePerKbIn, long pricePerKbOut, 
                       long orderViaShareBps, long consumeViaShareBps) {
            this.pricePerKb = pricePerKb;
            this.pricePerKbIn = pricePerKbIn;
            this.pricePerKbOut = pricePerKbOut;
            this.orderViaShareBps = orderViaShareBps;
            this.consumeViaShareBps = consumeViaShareBps;
        }

        public long getPricePerKb() {
            return pricePerKb;
        }
        
        public long getPricePerKbIn() {
            return pricePerKbIn;
        }
        
        public long getPricePerKbOut() {
            return pricePerKbOut;
        }

        public long getOrderViaShareBps() {
            return orderViaShareBps;
        }

        public long getConsumeViaShareBps() {
            return consumeViaShareBps;
        }
        
        /**
         * 计算请求费用（双向计费）
         * @param requestBytes 请求数据字节数
         * @param responseBytes 响应数据字节数
         * @return 总费用（聪）
         */
        public long calculateFee(long requestBytes, long responseBytes) {
            long requestKb = (requestBytes + 1023) / 1024;
            long responseKb = (responseBytes + 1023) / 1024;
            return requestKb * pricePerKbIn + responseKb * pricePerKbOut;
        }
    }

    public static class ChargeResult {
        private final ResultCode code;
        private final long amount;
        private final long balance;
        private final long creditLimit;
        private final RequestStatus status;
        private final String message;

        public ChargeResult(ResultCode code, long amount, long balance, long creditLimit, RequestStatus status, String message) {
            this.code = code;
            this.amount = amount;
            this.balance = balance;
            this.creditLimit = creditLimit;
            this.status = status;
            this.message = message;
        }

        public ChargeResult(ResultCode code, long amount, long balance, long creditLimit, RequestStatus status) {
            this(code, amount, balance, creditLimit, status, null);
        }

        public static ChargeResult ok(long amount, BalanceView view) {
            return new ChargeResult(ResultCode.OK, amount, view.getBalance(), view.getCreditLimit(), RequestStatus.OK);
        }

        public static ChargeResult error(ResultCode code, String message) {
            return new ChargeResult(code, 0, 0, 0, RequestStatus.FAILED, message);
        }

        public static ChargeResult fromExisting(ResultCode code, RequestRecord existing, BalanceView view) {
            RequestStatus status = existing.getStatus();
            return new ChargeResult(code, existing.getAmount(), view.getBalance(), view.getCreditLimit(), status);
        }

        public ResultCode getCode() {
            return code;
        }

        public long getAmount() {
            return amount;
        }

        public long getBalance() {
            return balance;
        }

        public long getCreditLimit() {
            return creditLimit;
        }

        public RequestStatus getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class CreditResult {
        private final ResultCode code;
        private final long amount;
        private final long balance;
        private final long creditLimit;
        private final CreditStatus status;
        private final String message;

        public CreditResult(ResultCode code, long amount, long balance, long creditLimit, CreditStatus status, String message) {
            this.code = code;
            this.amount = amount;
            this.balance = balance;
            this.creditLimit = creditLimit;
            this.status = status;
            this.message = message;
        }

        public CreditResult(ResultCode code, long amount, long balance, long creditLimit, CreditStatus status) {
            this(code, amount, balance, creditLimit, status, null);
        }

        public static CreditResult error(ResultCode code, String message) {
            return new CreditResult(code, 0, 0, 0, CreditStatus.PENDING, message);
        }

        public static CreditResult fromExisting(ResultCode code, CashRecord existing, BalanceView view) {
            return new CreditResult(code, existing.getAmount(), view.getBalance(), view.getCreditLimit(), existing.getStatus());
        }

        public ResultCode getCode() {
            return code;
        }

        public long getAmount() {
            return amount;
        }

        public long getBalance() {
            return balance;
        }

        public long getCreditLimit() {
            return creditLimit;
        }

        public CreditStatus getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class PeerBalance {
        private long balance;
        private long creditLimit;
        private long seq;
        private int flags;
        private int version;

        public PeerBalance(long balance, long creditLimit, long seq, int flags, int version) {
            this.balance = balance;
            this.creditLimit = creditLimit;
            this.seq = seq;
            this.flags = flags;
            this.version = version;
        }

        public long getBalance() {
            return balance;
        }

        public void setBalance(long balance) {
            this.balance = balance;
        }

        public long getCreditLimit() {
            return creditLimit;
        }

        public void setCreditLimit(long creditLimit) {
            this.creditLimit = creditLimit;
        }

        public long getSeq() {
            return seq;
        }

        public int getFlags() {
            return flags;
        }
        
        public void setFlags(int flags) {
            this.flags = flags;
        }

        public int getVersion() {
            return version;
        }

        public void bumpSeq() {
            this.seq = this.seq + 1;
        }
    }

    public static class RequestRecord {
        private String peerId;
        private long amount;
        private String metaHash;
        private RequestStatus status;
        private long ts;

        public RequestRecord(String peerId, long amount, String metaHash, RequestStatus status, long ts) {
            this.peerId = peerId;
            this.amount = amount;
            this.metaHash = metaHash;
            this.status = status;
            this.ts = ts;
        }

        public String getPeerId() {
            return peerId;
        }

        public long getAmount() {
            return amount;
        }

        public String getMetaHash() {
            return metaHash;
        }

        public RequestStatus getStatus() {
            return status;
        }

        public long getTs() {
            return ts;
        }
    }

    public static class CashRecord {
        private String userId;
        private long amount;
        private String src;
        private CreditStatus status;
        private Long height;
        private String blockId;
        private long ts;

        public CashRecord(String userId, long amount, String src, CreditStatus status, Long height, String blockId, long ts) {
            this.userId = userId;
            this.amount = amount;
            this.src = src;
            this.status = status;
            this.height = height;
            this.blockId = blockId;
            this.ts = ts;
        }

        public String getUserId() {
            return userId;
        }

        public long getAmount() {
            return amount;
        }

        public String getSrc() {
            return src;
        }

        public CreditStatus getStatus() {
            return status;
        }

        public Long getHeight() {
            return height;
        }

        public String getBlockId() {
            return blockId;
        }

        public long getTs() {
            return ts;
        }
    }

    public static class MetaState {
        private long bestHeight;
        private String bestBlockId;
        private String auditTipHash;
        private long snapshotVersion;
        private long lastSnapshotHeight;

        public MetaState(long bestHeight, String bestBlockId, String auditTipHash, long snapshotVersion, long lastSnapshotHeight) {
            this.bestHeight = bestHeight;
            this.bestBlockId = bestBlockId;
            this.auditTipHash = auditTipHash;
            this.snapshotVersion = snapshotVersion;
            this.lastSnapshotHeight = lastSnapshotHeight;
        }

        public long getBestHeight() {
            return bestHeight;
        }

        public void setBestHeight(long bestHeight) {
            this.bestHeight = bestHeight;
        }

        public String getBestBlockId() {
            return bestBlockId;
        }

        public void setBestBlockId(String bestBlockId) {
            this.bestBlockId = bestBlockId;
        }

        public String getAuditTipHash() {
            return auditTipHash;
        }

        public void setAuditTipHash(String auditTipHash) {
            this.auditTipHash = auditTipHash;
        }

        public long getSnapshotVersion() {
            return snapshotVersion;
        }

        public long getLastSnapshotHeight() {
            return lastSnapshotHeight;
        }
    }

    public static class AuditRecord {
        private long ts;
        private AuditType type;
        private String cashId;
        private String requestKey;
        private String peerId;
        private long amount;
        private String currency = "FCH";
        private String status;
        private long bestHeight;
        private String bestBlockId;
        private String note;
        private String prevHash;
        private String hash;

        public static AuditRecord income(String cashId, String peerId, long amount, MetaState meta, CreditStatus status, String note) {
            AuditRecord record = base(amount, meta);
            record.type = AuditType.income;
            record.cashId = cashId;
            record.peerId = peerId;
            record.status = status.name().toLowerCase();
            record.note = Optional.ofNullable(note).orElse("");
            return record;
        }

        public static AuditRecord spend(String requestKey, String peerId, long amount, MetaState meta, RequestStatus status, String note) {
            AuditRecord record = base(amount, meta);
            record.type = AuditType.spend;
            record.requestKey = requestKey;
            record.peerId = peerId;
            record.status = status.name().toLowerCase();
            record.note = Optional.ofNullable(note).orElse("");
            return record;
        }
        
        public static AuditRecord settle(String cycleId, long amount, MetaState meta) {
            AuditRecord record = base(amount, meta);
            record.type = AuditType.settle;
            record.requestKey = cycleId;
            record.status = "confirmed";
            record.note = "cycle_settle";
            return record;
        }
        
        public static AuditRecord adjust(String cashId, String peerId, long amount, MetaState meta, String note) {
            AuditRecord record = base(amount, meta);
            record.type = AuditType.adjust;
            record.cashId = cashId;
            record.peerId = peerId;
            record.status = "adjusted";
            record.note = Optional.ofNullable(note).orElse("");
            return record;
        }

        private static AuditRecord base(long amount, MetaState meta) {
            AuditRecord record = new AuditRecord();
            record.ts = Instant.now().toEpochMilli();
            record.amount = amount;
            record.bestHeight = meta.getBestHeight();
            record.bestBlockId = meta.getBestBlockId() == null ? "" : meta.getBestBlockId();
            return record;
        }

        public String canonicalWithoutHash() {
            return ts + "," + type + "," + Optional.ofNullable(cashId).orElse("") + "," +
                Optional.ofNullable(requestKey).orElse("") + "," + Optional.ofNullable(peerId).orElse("") + "," +
                amount + "," + currency + "," + Optional.ofNullable(status).orElse("") + "," + bestHeight + "," +
                Optional.ofNullable(bestBlockId).orElse("") + "," + Optional.ofNullable(note).orElse("") + "," +
                Optional.ofNullable(prevHash).orElse("");
        }

        public long getTs() {
            return ts;
        }
        
        public long getBestHeight() {
            return bestHeight;
        }

        public void setHash(String hash) {
            this.hash = hash;
        }

        public void setPrevHash(String prevHash) {
            this.prevHash = prevHash;
        }

        public String getRequestKey() {
            return requestKey;
        }

        public String getCashId() {
            return cashId;
        }

        public AuditType getType() {
            return type;
        }

        public long getAmount() {
            return amount;
        }

        public String getStatus() {
            return status;
        }
    }

    public static class ChargeMeta {
        private final String direction;
        private final String type;
        private final long payloadBytes;
        private final long sendTs;
        private final long recvTs;
        private final int retransmitCount;
        private final Long rttMicros;
        private final Double lossRateHint;

        public ChargeMeta(String direction, String type, long payloadBytes, long sendTs, long recvTs,
                          int retransmitCount, Long rttMicros, Double lossRateHint) {
            this.direction = direction;
            this.type = type;
            this.payloadBytes = payloadBytes;
            this.sendTs = sendTs;
            this.recvTs = recvTs;
            this.retransmitCount = retransmitCount;
            this.rttMicros = rttMicros;
            this.lossRateHint = lossRateHint;
        }
    }
    
    // ==================== 结算相关数据类 ====================
    
    public enum SettleStatus {
        PENDING,
        CONFIRMED,
        FAILED
    }
    
    public static class SettleResult {
        private final ResultCode code;
        private final SettleRecord record;
        private final String message;
        
        public SettleResult(ResultCode code, SettleRecord record, String message) {
            this.code = code;
            this.record = record;
            this.message = message;
        }
        
        public SettleResult(ResultCode code, SettleRecord record) {
            this(code, record, null);
        }
        
        public static SettleResult error(ResultCode code, String message) {
            return new SettleResult(code, null, message);
        }
        
        public static SettleResult fromExisting(ResultCode code, SettleRecord existing) {
            return new SettleResult(code, existing, "Already exists");
        }
        
        public ResultCode getCode() {
            return code;
        }
        
        public SettleRecord getRecord() {
            return record;
        }
        
        public String getMessage() {
            return message;
        }
        
        public boolean isSuccess() {
            return code == ResultCode.OK;
        }
    }
    
    public static class SettleRecord {
        private final String cycleId;
        private final long totalIncome;
        private final long totalSpend;
        private final long netIncome;
        private final long totalDistributed;
        private final Map<String, Long> distributions;
        private final SettleStatus status;
        private final long ts;
        
        public SettleRecord(String cycleId, long totalIncome, long totalSpend, 
                           long netIncome, long totalDistributed, 
                           Map<String, Long> distributions, SettleStatus status, long ts) {
            this.cycleId = cycleId;
            this.totalIncome = totalIncome;
            this.totalSpend = totalSpend;
            this.netIncome = netIncome;
            this.totalDistributed = totalDistributed;
            this.distributions = distributions;
            this.status = status;
            this.ts = ts;
        }
        
        public String getCycleId() {
            return cycleId;
        }
        
        public long getTotalIncome() {
            return totalIncome;
        }
        
        public long getTotalSpend() {
            return totalSpend;
        }
        
        public long getNetIncome() {
            return netIncome;
        }
        
        public long getTotalDistributed() {
            return totalDistributed;
        }
        
        public Map<String, Long> getDistributions() {
            return distributions;
        }
        
        public SettleStatus getStatus() {
            return status;
        }
        
        public long getTs() {
            return ts;
        }
    }
    
    /**
     * 周期统计数据
     */
    public static class CycleStats {
        public final long totalIncome;
        public final long totalSpend;
        public final long netIncome;
        
        public CycleStats(long totalIncome, long totalSpend, long netIncome) {
            this.totalIncome = totalIncome;
            this.totalSpend = totalSpend;
            this.netIncome = netIncome;
        }
    }
    
    /**
     * 渠道余额（内部类）
     */
    public static class ChannelBalance {
        private long orderShare;      // 累积的订单分成（聪）
        private long consumeShare;    // 累积的消费分成（聪）
        private long settledAmount;   // 已结算金额（用于审计）
        private long seq;             // 版本号
        
        public ChannelBalance(long orderShare, long consumeShare, long settledAmount, long seq) {
            this.orderShare = orderShare;
            this.consumeShare = consumeShare;
            this.settledAmount = settledAmount;
            this.seq = seq;
        }
        
        public long getOrderShare() { return orderShare; }
        public void setOrderShare(long orderShare) { this.orderShare = orderShare; }
        
        public long getConsumeShare() { return consumeShare; }
        public void setConsumeShare(long consumeShare) { this.consumeShare = consumeShare; }
        
        public long getSettledAmount() { return settledAmount; }
        public void setSettledAmount(long settledAmount) { this.settledAmount = settledAmount; }
        
        public long getSeq() { return seq; }
        
        public void bumpSeq() { this.seq++; }
        
        public long getTotalPending() {
            return orderShare + consumeShare;
        }
    }
    
    /**
     * 渠道余额视图（公开类）
     */
    public static class ChannelBalanceView {
        private final String channelId;
        private final long orderShare;
        private final long consumeShare;
        private final long totalPending;
        private final long settledAmount;
        
        public ChannelBalanceView(String channelId, long orderShare, long consumeShare, 
                                   long totalPending, long settledAmount) {
            this.channelId = channelId;
            this.orderShare = orderShare;
            this.consumeShare = consumeShare;
            this.totalPending = totalPending;
            this.settledAmount = settledAmount;
        }
        
        public String getChannelId() { return channelId; }
        public long getOrderShare() { return orderShare; }
        public long getConsumeShare() { return consumeShare; }
        public long getTotalPending() { return totalPending; }
        public long getSettledAmount() { return settledAmount; }
    }
}

