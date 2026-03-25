package fapi.client;

import config.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight client-side balance drift verifier.
 * Uses server-reported authoritative balance and compares against a locally expected value.
 * <p>
 * 增强功能：
 * - 基于本地 pricePerKB 和计量数据推导预期消费
 * - 跟踪最近 N 次漂移事件
 * - 支持累积偏差和连续超阈值检测
 */
public class BalanceVerifier {
    private static final Logger log = LoggerFactory.getLogger(BalanceVerifier.class);
    
    /** 最大漂移历史记录数 */
    private static final int MAX_DRIFT_HISTORY = 100;

    // ==================== 配置键常量 ====================
    public static final String KEY_TOLERANCE_PCT = "balanceTolerancePct";
    public static final String KEY_TOLERANCE_SAT_MIN = "balanceToleranceSatMin";
    public static final String KEY_DRIFT_ACCUM_PCT = "balanceDriftAccumPct";
    public static final String KEY_DRIFT_ACCUM_SAT = "balanceDriftAccumSat";
    public static final String KEY_DRIFT_STOP_PCT = "balanceDriftStopPct";
    public static final String KEY_DRIFT_STOP_SAT = "balanceDriftStopSat";
    public static final String KEY_MAX_CONSECUTIVE_DRIFT = "balanceMaxConsecutiveDrift";
    public static final String KEY_DRIFT_ACTION = "balanceDriftAction";
    public static final String KEY_DISPLAY_PRECISION = "balanceDisplayPrecision";
    public static final String KEY_LOG_BALANCE_READ_ERROR = "logBalanceReadError";
    
    // ==================== 默认值常量 ====================
    public static final double DEFAULT_TOLERANCE_PCT = 0.02d;
    public static final long DEFAULT_TOLERANCE_SAT_MIN = 10_000L;
    public static final double DEFAULT_DRIFT_ACCUM_PCT = 0.05d;
    public static final long DEFAULT_DRIFT_ACCUM_SAT = 50_000L;
    public static final double DEFAULT_DRIFT_STOP_PCT = 0.1d;
    public static final long DEFAULT_DRIFT_STOP_SAT = 100_000L;
    public static final long DEFAULT_MAX_CONSECUTIVE_DRIFT = 3L;
    public static final String DEFAULT_DRIFT_ACTION = "warn";
    public static final long DEFAULT_DISPLAY_PRECISION = 8L;
    public static final boolean DEFAULT_LOG_BALANCE_READ_ERROR = true;

    public enum Action {
        log, warn, stop
    }

    public static class Result {
        public enum Type { NOOP, WARN, STOP }

        private final Type type;
        private final long drift;
        private final long threshold;

        private Result(Type type, long drift, long threshold) {
            this.type = type;
            this.drift = drift;
            this.threshold = threshold;
        }

        public static Result noop() { return new Result(Type.NOOP, 0, 0); }
        public static Result warn(long drift, long threshold) { return new Result(Type.WARN, drift, threshold); }
        public static Result stop(long drift, long threshold) { return new Result(Type.STOP, drift, threshold); }

        public Type getType() { return type; }
        public long getDrift() { return drift; }
        public long getThreshold() { return threshold; }
    }

    private final double tolerancePct;
    private final long toleranceSatMin;
    private final double accumWarnPct;
    private final long accumWarnSat;
    private final double stopPct;
    private final long stopSat;
    private final long maxConsecutiveDrift;
    private final Action action;

    private long accumulatedDrift = 0;
    private long consecutiveDrift = 0;
    private Long expectedBalance = null;
    private boolean stopped = false;
    
    // 增强功能字段
    private long pricePerKb = 0;
    private final AtomicLong totalLocalSpend = new AtomicLong(0);
    private final AtomicLong totalServerSpend = new AtomicLong(0);
    private final List<DriftEvent> driftHistory = new ArrayList<>();
    private Long lastServerBalance = null;
    private long lastObserveTime = 0;

    public static BalanceVerifier fromSettings(Settings settings) {
        if (settings == null) {
            return null;
        }
        var map = settings.getSettingMap();
        double tolerancePct = getDouble(map, KEY_TOLERANCE_PCT, DEFAULT_TOLERANCE_PCT);
        long toleranceSatMin = getLong(map, KEY_TOLERANCE_SAT_MIN, DEFAULT_TOLERANCE_SAT_MIN);
        double accumWarnPct = getDouble(map, KEY_DRIFT_ACCUM_PCT, DEFAULT_DRIFT_ACCUM_PCT);
        long accumWarnSat = getLong(map, KEY_DRIFT_ACCUM_SAT, DEFAULT_DRIFT_ACCUM_SAT);
        double stopPct = getDouble(map, KEY_DRIFT_STOP_PCT, DEFAULT_DRIFT_STOP_PCT);
        long stopSat = getLong(map, KEY_DRIFT_STOP_SAT, DEFAULT_DRIFT_STOP_SAT);
        long maxConsecutive = getLong(map, KEY_MAX_CONSECUTIVE_DRIFT, DEFAULT_MAX_CONSECUTIVE_DRIFT);
        String actionStr = getString(map, KEY_DRIFT_ACTION, DEFAULT_DRIFT_ACTION);
        Action action = parseAction(actionStr);
        return new BalanceVerifier(tolerancePct, toleranceSatMin, accumWarnPct, accumWarnSat, stopPct, stopSat, maxConsecutive, action);
    }

    public BalanceVerifier(double tolerancePct,
                           long toleranceSatMin,
                           double accumWarnPct,
                           long accumWarnSat,
                           double stopPct,
                           long stopSat,
                           long maxConsecutiveDrift,
                           Action action) {
        this.tolerancePct = tolerancePct;
        this.toleranceSatMin = toleranceSatMin;
        this.accumWarnPct = accumWarnPct;
        this.accumWarnSat = accumWarnSat;
        this.stopPct = stopPct;
        this.stopSat = stopSat;
        this.maxConsecutiveDrift = Math.max(1, maxConsecutiveDrift);
        this.action = action == null ? Action.warn : action;
    }

    public Result observe(Long serverBalance) {
        lastObserveTime = System.currentTimeMillis();
        
        if (stopped || serverBalance == null) {
            return Result.noop();
        }
        
        lastServerBalance = serverBalance;
        
        if (expectedBalance == null) {
            expectedBalance = serverBalance;
            return Result.noop();
        }

        long diff = serverBalance - expectedBalance;
        long absDiff = Math.abs(diff);
        long warnThreshold = Math.max(toleranceSatMin, pctThreshold(expectedBalance, tolerancePct));
        long accumThreshold = Math.max(accumWarnSat, pctThreshold(expectedBalance, accumWarnPct));
        long stopThreshold = Math.max(stopSat, pctThreshold(expectedBalance, stopPct));

        if (absDiff > warnThreshold) {
            consecutiveDrift++;
            accumulatedDrift += absDiff;

            if (accumulatedDrift >= stopThreshold || consecutiveDrift >= maxConsecutiveDrift && absDiff >= stopThreshold) {
                stopped = action == Action.stop;
                log.warn("Balance drift STOP: drift={}, threshold={}, expected={}, server={}", absDiff, stopThreshold, expectedBalance, serverBalance);
                recordDriftEvent(absDiff, stopThreshold, "STOP");
                return action == Action.stop ? Result.stop(absDiff, stopThreshold) : Result.warn(absDiff, stopThreshold);
            }

            if (accumulatedDrift >= accumThreshold || consecutiveDrift >= maxConsecutiveDrift) {
                log.warn("Balance drift WARN: drift={}, threshold={}, consecutive={}, accum={}", absDiff, accumThreshold, consecutiveDrift, accumulatedDrift);
                recordDriftEvent(absDiff, accumThreshold, "WARN");
                expectedBalance = serverBalance;
                return Result.warn(absDiff, accumThreshold);
            }
        } else {
            consecutiveDrift = 0;
            accumulatedDrift = 0;
        }

        expectedBalance = serverBalance;
        return Result.noop();
    }

    public boolean isStopped() {
        return stopped && action == Action.stop;
    }

    public void resetExpected(Long expectedBalance) {
        this.expectedBalance = expectedBalance;
        this.accumulatedDrift = 0;
        this.consecutiveDrift = 0;
        this.stopped = false;
    }

    public void applyLocalDelta(long delta) {
        if (expectedBalance == null) {
            expectedBalance = delta;
        } else {
            expectedBalance += delta;
        }
    }

    private static long pctThreshold(long base, double pct) {
        if (pct <= 0) return 0;
        return (long) Math.abs(base * pct);
    }

    private static long getLong(java.util.Map<String, Object> map, String key, long defVal) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) return defVal;
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(val.toString());
        } catch (Exception e) {
            return defVal;
        }
    }

    private static double getDouble(java.util.Map<String, Object> map, String key, double defVal) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) return defVal;
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(val.toString());
        } catch (Exception e) {
            return defVal;
        }
    }

    private static String getString(java.util.Map<String, Object> map, String key, String defVal) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) return defVal;
        Object val = map.get(key);
        return val.toString();
    }

    private static Action parseAction(String action) {
        if (action == null) return Action.warn;
        try {
            return Action.valueOf(action.toLowerCase());
        } catch (Exception e) {
            return Action.warn;
        }
    }
    
    // ==================== 增强功能方法 ====================
    
    /**
     * 设置本地价格（用于计算预期消费）
     */
    public void setPricePerKb(long pricePerKb) {
        this.pricePerKb = pricePerKb;
    }
    
    /**
     * 记录本地消费（基于计量数据）
     * 
     * @param payloadBytes 负载字节数
     * @return 计算的消费金额（聪）
     */
    public long recordLocalSpend(long payloadBytes) {
        if (pricePerKb <= 0 || payloadBytes <= 0) {
            return 0;
        }
        long kb = (payloadBytes + 1023) / 1024;
        long amount = kb * pricePerKb;
        totalLocalSpend.addAndGet(amount);
        
        // 同步更新预期余额
        if (expectedBalance != null) {
            expectedBalance -= amount;
        }
        
        return amount;
    }
    
    /**
     * 记录服务端报告的消费
     */
    public void recordServerSpend(long amount) {
        totalServerSpend.addAndGet(amount);
    }
    
    /**
     * 获取本地与服务端消费偏差
     */
    public long getSpendDrift() {
        return Math.abs(totalLocalSpend.get() - totalServerSpend.get());
    }
    
    /**
     * 获取本地计算的总消费
     */
    public long getTotalLocalSpend() {
        return totalLocalSpend.get();
    }
    
    /**
     * 获取服务端报告的总消费
     */
    public long getTotalServerSpend() {
        return totalServerSpend.get();
    }
    
    /**
     * 获取漂移历史
     */
    public List<DriftEvent> getDriftHistory() {
        synchronized (driftHistory) {
            return new ArrayList<>(driftHistory);
        }
    }
    
    /**
     * 记录漂移事件
     */
    private void recordDriftEvent(long drift, long threshold, String type) {
        DriftEvent event = new DriftEvent(
                System.currentTimeMillis(),
                drift,
                threshold,
                type,
                expectedBalance,
                lastServerBalance
        );
        
        synchronized (driftHistory) {
            driftHistory.add(event);
            // 保持历史记录数量在限制内
            while (driftHistory.size() > MAX_DRIFT_HISTORY) {
                driftHistory.remove(0);
            }
        }
    }
    
    /**
     * 获取最后服务端余额
     */
    public Long getLastServerBalance() {
        return lastServerBalance;
    }
    
    /**
     * 获取预期余额
     */
    public Long getExpectedBalance() {
        return expectedBalance;
    }
    
    /**
     * 获取累积漂移
     */
    public long getAccumulatedDrift() {
        return accumulatedDrift;
    }
    
    /**
     * 获取连续漂移次数
     */
    public long getConsecutiveDrift() {
        return consecutiveDrift;
    }
    
    /**
     * 获取验证器状态报告
     */
    public VerifierStatus getStatus() {
        return new VerifierStatus(
                expectedBalance,
                lastServerBalance,
                accumulatedDrift,
                consecutiveDrift,
                stopped,
                totalLocalSpend.get(),
                totalServerSpend.get(),
                pricePerKb,
                lastObserveTime
        );
    }
    
    /**
     * 漂移事件记录
     */
    public static class DriftEvent {
        public final long timestamp;
        public final long drift;
        public final long threshold;
        public final String type;
        public final Long expectedBalance;
        public final Long serverBalance;
        
        public DriftEvent(long timestamp, long drift, long threshold, String type,
                          Long expectedBalance, Long serverBalance) {
            this.timestamp = timestamp;
            this.drift = drift;
            this.threshold = threshold;
            this.type = type;
            this.expectedBalance = expectedBalance;
            this.serverBalance = serverBalance;
        }
    }
    
    /**
     * 验证器状态
     */
    public static class VerifierStatus {
        public final Long expectedBalance;
        public final Long lastServerBalance;
        public final long accumulatedDrift;
        public final long consecutiveDrift;
        public final boolean stopped;
        public final long totalLocalSpend;
        public final long totalServerSpend;
        public final long pricePerKb;
        public final long lastObserveTime;
        
        public VerifierStatus(Long expectedBalance, Long lastServerBalance, long accumulatedDrift,
                              long consecutiveDrift, boolean stopped, long totalLocalSpend,
                              long totalServerSpend, long pricePerKb, long lastObserveTime) {
            this.expectedBalance = expectedBalance;
            this.lastServerBalance = lastServerBalance;
            this.accumulatedDrift = accumulatedDrift;
            this.consecutiveDrift = consecutiveDrift;
            this.stopped = stopped;
            this.totalLocalSpend = totalLocalSpend;
            this.totalServerSpend = totalServerSpend;
            this.pricePerKb = pricePerKb;
            this.lastObserveTime = lastObserveTime;
        }
        
        public long getSpendDrift() {
            return Math.abs(totalLocalSpend - totalServerSpend);
        }
    }
}
