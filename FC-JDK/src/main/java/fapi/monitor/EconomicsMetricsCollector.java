package fapi.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 经济模型指标收集器
 * <p>
 * 收集 FAPI 经济模型相关的指标：
 * - 扣费失败率
 * - 信用拒绝次数
 * - 数据源高度差
 * - 快照滞后
 * - 充值/消费统计
 */
public class EconomicsMetricsCollector {
    private static final Logger log = LoggerFactory.getLogger(EconomicsMetricsCollector.class);
    
    // ==================== 扣费指标 ====================
    
    /** 总扣费请求数 */
    private final LongAdder totalChargeRequests = new LongAdder();
    
    /** 扣费成功数 */
    private final LongAdder chargeSuccess = new LongAdder();
    
    /** 扣费失败数（余额不足但在信用额度内） */
    private final LongAdder chargeInsufficientWithinCredit = new LongAdder();
    
    /** 信用额度超限拒绝数 */
    private final LongAdder creditExceeded = new LongAdder();
    
    /** 黑名单拒绝数 */
    private final LongAdder blacklistRejected = new LongAdder();
    
    /** 扣费错误数 */
    private final LongAdder chargeErrors = new LongAdder();
    
    // ==================== 充值指标 ====================
    
    /** 总充值次数 */
    private final LongAdder totalCredits = new LongAdder();
    
    /** 充值总额（聪） */
    private final LongAdder totalCreditAmount = new LongAdder();
    
    /** 充值冲突次数 */
    private final LongAdder creditConflicts = new LongAdder();
    
    // ==================== 消费指标 ====================
    
    /** 总消费金额（聪） */
    private final LongAdder totalSpendAmount = new LongAdder();
    
    /** 免费请求数 */
    private final LongAdder freeRequests = new LongAdder();
    
    /** 重传去重数 */
    private final LongAdder retransmitDeduplicated = new LongAdder();
    
    // ==================== 数据源指标 ====================
    
    /** 当前最佳区块高度 */
    private final AtomicLong currentBestHeight = new AtomicLong(0);
    
    /** 数据源高度（用于计算差距） */
    private final AtomicLong dataSourceHeight = new AtomicLong(0);
    
    // ==================== 快照指标 ====================
    
    /** 最后快照时间 */
    private final AtomicLong lastSnapshotTime = new AtomicLong(0);
    
    /** 快照次数 */
    private final LongAdder snapshotCount = new LongAdder();
    
    /** 快照失败次数 */
    private final LongAdder snapshotFailures = new LongAdder();
    
    // ==================== WAL 指标 ====================
    
    /** WAL 写入次数 */
    private final LongAdder walWrites = new LongAdder();
    
    /** WAL 写入字节数 */
    private final LongAdder walBytesWritten = new LongAdder();
    
    /** WAL fsync 次数 */
    private final LongAdder walFsyncs = new LongAdder();
    
    /** WAL fsync 总耗时（毫秒） */
    private final LongAdder walFsyncTimeMs = new LongAdder();
    
    // ==================== 结算指标 ====================
    
    /** 结算次数 */
    private final LongAdder settleCount = new LongAdder();
    
    /** 结算总分配金额 */
    private final LongAdder totalDistributed = new LongAdder();
    
    // ==================== 回滚指标 ====================
    
    /** 回滚检测次数 */
    private final LongAdder rollbackDetected = new LongAdder();
    
    /** 回滚处理次数 */
    private final LongAdder rollbackHandled = new LongAdder();
    
    // ==================== 告警阈值 ====================
    
    /** 扣费失败率告警阈值（百分比） */
    private double chargeFailureRateAlertThreshold = 10.0;
    
    /** 信用拒绝率告警阈值（百分比） */
    private double creditExceededRateAlertThreshold = 5.0;
    
    /** 数据源高度差告警阈值 */
    private long heightDiffAlertThreshold = 10;
    
    /** 快照滞后告警阈值（毫秒） */
    private long snapshotLagAlertThreshold = 30 * 60 * 1000; // 30分钟
    
    // ==================== 记录方法 ====================
    
    /**
     * 记录扣费成功
     */
    public void recordChargeSuccess(long amount) {
        totalChargeRequests.increment();
        chargeSuccess.increment();
        totalSpendAmount.add(amount);
    }
    
    /**
     * 记录余额不足但在信用额度内
     */
    public void recordChargeInsufficientWithinCredit(long amount) {
        totalChargeRequests.increment();
        chargeInsufficientWithinCredit.increment();
        totalSpendAmount.add(amount);
    }
    
    /**
     * 记录信用额度超限
     */
    public void recordCreditExceeded() {
        totalChargeRequests.increment();
        creditExceeded.increment();
    }
    
    /**
     * 记录黑名单拒绝
     */
    public void recordBlacklistRejected() {
        totalChargeRequests.increment();
        blacklistRejected.increment();
    }
    
    /**
     * 记录扣费错误
     */
    public void recordChargeError() {
        totalChargeRequests.increment();
        chargeErrors.increment();
    }
    
    /**
     * 记录免费请求
     */
    public void recordFreeRequest() {
        totalChargeRequests.increment();
        freeRequests.increment();
    }
    
    /**
     * 记录重传去重
     */
    public void recordRetransmitDeduplicated() {
        retransmitDeduplicated.increment();
    }
    
    /**
     * 记录充值
     */
    public void recordCredit(long amount) {
        totalCredits.increment();
        totalCreditAmount.add(amount);
    }
    
    /**
     * 记录充值冲突
     */
    public void recordCreditConflict() {
        creditConflicts.increment();
    }
    
    /**
     * 更新最佳区块高度
     */
    public void updateBestHeight(long height) {
        currentBestHeight.set(height);
    }
    
    /**
     * 更新数据源高度
     */
    public void updateDataSourceHeight(long height) {
        dataSourceHeight.set(height);
    }
    
    /**
     * 记录快照
     */
    public void recordSnapshot() {
        snapshotCount.increment();
        lastSnapshotTime.set(System.currentTimeMillis());
    }
    
    /**
     * 记录快照失败
     */
    public void recordSnapshotFailure() {
        snapshotFailures.increment();
    }
    
    /**
     * 记录 WAL 写入
     */
    public void recordWalWrite(long bytes) {
        walWrites.increment();
        walBytesWritten.add(bytes);
    }
    
    /**
     * 记录 WAL fsync
     */
    public void recordWalFsync(long durationMs) {
        walFsyncs.increment();
        walFsyncTimeMs.add(durationMs);
    }
    
    /**
     * 记录结算
     */
    public void recordSettle(long distributed) {
        settleCount.increment();
        totalDistributed.add(distributed);
    }
    
    /**
     * 记录回滚检测
     */
    public void recordRollbackDetected() {
        rollbackDetected.increment();
    }
    
    /**
     * 记录回滚处理
     */
    public void recordRollbackHandled() {
        rollbackHandled.increment();
    }
    
    // ==================== 获取指标 ====================
    
    /**
     * 获取扣费失败率（百分比）
     */
    public double getChargeFailureRate() {
        long total = totalChargeRequests.sum();
        if (total == 0) return 0.0;
        long success = chargeSuccess.sum() + chargeInsufficientWithinCredit.sum();
        return (1.0 - (double) success / total) * 100;
    }
    
    /**
     * 获取信用拒绝率（百分比）
     */
    public double getCreditExceededRate() {
        long total = totalChargeRequests.sum();
        if (total == 0) return 0.0;
        return (double) creditExceeded.sum() / total * 100;
    }
    
    /**
     * 获取数据源高度差
     */
    public long getHeightDiff() {
        return Math.abs(dataSourceHeight.get() - currentBestHeight.get());
    }
    
    /**
     * 获取快照滞后（毫秒）
     */
    public long getSnapshotLag() {
        long lastSnapshot = lastSnapshotTime.get();
        if (lastSnapshot == 0) return 0;
        return System.currentTimeMillis() - lastSnapshot;
    }
    
    /**
     * 获取完整报告
     */
    public EconomicsMetricsReport getReport() {
        EconomicsMetricsReport report = new EconomicsMetricsReport();
        
        // 扣费指标
        report.totalChargeRequests = totalChargeRequests.sum();
        report.chargeSuccess = chargeSuccess.sum();
        report.chargeInsufficientWithinCredit = chargeInsufficientWithinCredit.sum();
        report.creditExceeded = creditExceeded.sum();
        report.blacklistRejected = blacklistRejected.sum();
        report.chargeErrors = chargeErrors.sum();
        report.chargeFailureRate = getChargeFailureRate();
        report.creditExceededRate = getCreditExceededRate();
        
        // 充值指标
        report.totalCredits = totalCredits.sum();
        report.totalCreditAmount = totalCreditAmount.sum();
        report.creditConflicts = creditConflicts.sum();
        
        // 消费指标
        report.totalSpendAmount = totalSpendAmount.sum();
        report.freeRequests = freeRequests.sum();
        report.retransmitDeduplicated = retransmitDeduplicated.sum();
        
        // 数据源指标
        report.currentBestHeight = currentBestHeight.get();
        report.dataSourceHeight = dataSourceHeight.get();
        report.heightDiff = getHeightDiff();
        
        // 快照指标
        report.lastSnapshotTime = lastSnapshotTime.get();
        report.snapshotCount = snapshotCount.sum();
        report.snapshotFailures = snapshotFailures.sum();
        report.snapshotLag = getSnapshotLag();
        
        // WAL 指标
        report.walWrites = walWrites.sum();
        report.walBytesWritten = walBytesWritten.sum();
        report.walFsyncs = walFsyncs.sum();
        report.avgWalFsyncTimeMs = walFsyncs.sum() > 0 
                ? (double) walFsyncTimeMs.sum() / walFsyncs.sum() : 0;
        
        // 结算指标
        report.settleCount = settleCount.sum();
        report.totalDistributed = totalDistributed.sum();
        
        // 回滚指标
        report.rollbackDetected = rollbackDetected.sum();
        report.rollbackHandled = rollbackHandled.sum();
        
        // 告警状态
        report.chargeFailureRateAlert = report.chargeFailureRate > chargeFailureRateAlertThreshold;
        report.creditExceededRateAlert = report.creditExceededRate > creditExceededRateAlertThreshold;
        report.heightDiffAlert = report.heightDiff > heightDiffAlertThreshold;
        report.snapshotLagAlert = report.snapshotLag > snapshotLagAlertThreshold;
        
        report.timestamp = System.currentTimeMillis();
        
        return report;
    }
    
    /**
     * 重置所有指标
     */
    public void reset() {
        totalChargeRequests.reset();
        chargeSuccess.reset();
        chargeInsufficientWithinCredit.reset();
        creditExceeded.reset();
        blacklistRejected.reset();
        chargeErrors.reset();
        totalCredits.reset();
        totalCreditAmount.reset();
        creditConflicts.reset();
        totalSpendAmount.reset();
        freeRequests.reset();
        retransmitDeduplicated.reset();
        snapshotCount.reset();
        snapshotFailures.reset();
        walWrites.reset();
        walBytesWritten.reset();
        walFsyncs.reset();
        walFsyncTimeMs.reset();
        settleCount.reset();
        totalDistributed.reset();
        rollbackDetected.reset();
        rollbackHandled.reset();
        
        log.info("Economics metrics reset");
    }
    
    // ==================== 阈值配置 ====================
    
    public void setChargeFailureRateAlertThreshold(double threshold) {
        this.chargeFailureRateAlertThreshold = threshold;
    }
    
    public void setCreditExceededRateAlertThreshold(double threshold) {
        this.creditExceededRateAlertThreshold = threshold;
    }
    
    public void setHeightDiffAlertThreshold(long threshold) {
        this.heightDiffAlertThreshold = threshold;
    }
    
    public void setSnapshotLagAlertThreshold(long threshold) {
        this.snapshotLagAlertThreshold = threshold;
    }
    
    /**
     * 经济模型指标报告
     */
    public static class EconomicsMetricsReport {
        // 扣费指标
        public long totalChargeRequests;
        public long chargeSuccess;
        public long chargeInsufficientWithinCredit;
        public long creditExceeded;
        public long blacklistRejected;
        public long chargeErrors;
        public double chargeFailureRate;
        public double creditExceededRate;
        
        // 充值指标
        public long totalCredits;
        public long totalCreditAmount;
        public long creditConflicts;
        
        // 消费指标
        public long totalSpendAmount;
        public long freeRequests;
        public long retransmitDeduplicated;
        
        // 数据源指标
        public long currentBestHeight;
        public long dataSourceHeight;
        public long heightDiff;
        
        // 快照指标
        public long lastSnapshotTime;
        public long snapshotCount;
        public long snapshotFailures;
        public long snapshotLag;
        
        // WAL 指标
        public long walWrites;
        public long walBytesWritten;
        public long walFsyncs;
        public double avgWalFsyncTimeMs;
        
        // 结算指标
        public long settleCount;
        public long totalDistributed;
        
        // 回滚指标
        public long rollbackDetected;
        public long rollbackHandled;
        
        // 告警状态
        public boolean chargeFailureRateAlert;
        public boolean creditExceededRateAlert;
        public boolean heightDiffAlert;
        public boolean snapshotLagAlert;
        
        public long timestamp;
        
        public boolean hasAnyAlert() {
            return chargeFailureRateAlert || creditExceededRateAlert 
                    || heightDiffAlert || snapshotLagAlert;
        }
    }
}

