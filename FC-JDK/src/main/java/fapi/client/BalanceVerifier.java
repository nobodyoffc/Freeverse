package fapi.client;

import config.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight client-side balance drift verifier.
 * Uses server-reported authoritative balance and compares against a locally expected value.
 */
public class BalanceVerifier {
    private static final Logger log = LoggerFactory.getLogger(BalanceVerifier.class);

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

    public static BalanceVerifier fromSettings(Settings settings) {
        if (settings == null) {
            return null;
        }
        var map = settings.getSettingMap();
        double tolerancePct = getDouble(map, Settings.BALANCE_TOLERANCE_PCT, Settings.DEFAULT_BALANCE_TOLERANCE_PCT);
        long toleranceSatMin = getLong(map, Settings.BALANCE_TOLERANCE_SAT_MIN, Settings.DEFAULT_BALANCE_TOLERANCE_SAT_MIN);
        double accumWarnPct = getDouble(map, Settings.BALANCE_DRIFT_ACCUM_PCT, Settings.DEFAULT_BALANCE_DRIFT_ACCUM_PCT);
        long accumWarnSat = getLong(map, Settings.BALANCE_DRIFT_ACCUM_SAT, Settings.DEFAULT_BALANCE_DRIFT_ACCUM_SAT);
        double stopPct = getDouble(map, Settings.BALANCE_DRIFT_STOP_PCT, Settings.DEFAULT_BALANCE_DRIFT_STOP_PCT);
        long stopSat = getLong(map, Settings.BALANCE_DRIFT_STOP_SAT, Settings.DEFAULT_BALANCE_DRIFT_STOP_SAT);
        long maxConsecutive = getLong(map, Settings.BALANCE_MAX_CONSECUTIVE_DRIFT, Settings.DEFAULT_BALANCE_MAX_CONSECUTIVE_DRIFT);
        String actionStr = getString(map, Settings.BALANCE_DRIFT_ACTION, Settings.DEFAULT_BALANCE_DRIFT_ACTION);
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
        if (stopped || serverBalance == null) {
            return Result.noop();
        }
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
                return action == Action.stop ? Result.stop(absDiff, stopThreshold) : Result.warn(absDiff, stopThreshold);
            }

            if (accumulatedDrift >= accumThreshold || consecutiveDrift >= maxConsecutiveDrift) {
                log.warn("Balance drift WARN: drift={}, threshold={}, consecutive={}, accum={}", absDiff, accumThreshold, consecutiveDrift, accumulatedDrift);
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
}
