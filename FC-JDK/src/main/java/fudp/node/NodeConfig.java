package fudp.node;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for FudpNode.
 */
public class NodeConfig {

    public static final String FUDP_DATA = "fudp_data";
    public static final String FUDP_PORT = "fudpPort";
    public static final String FUDP_DATA_DIR = "fudpDataDir";
    public static final String BALANCE_VERIFICATION = "balanceVerification";
    // Network
    private int port = 9000;
    private String bindAddress = "0.0.0.0";

    // File Transfer
    private int chunkSize = 32768;           // 32KB
    private String downloadDir = "~/Downloads/fudp";
    private boolean autoAcceptFiles = false;
    private long maxFileSize = 1073741824;   // 1GB

    // Timeouts
    private long requestTimeoutMs = 3000000;//30000;   // 30 seconds
    private long transferTimeoutMs = 300000; // 5 minutes
    private long connectionTimeoutMs = 1000000;//10000; // 10 seconds

    // Storage
    private String dataDir = "~/.fudp";

    // Logging
    private String logLevel = "INFO";
    private String logDir = "~/.fudp/logs";
    private int maxLogFiles = 7;
    private long maxLogSizeBytes = 10485760;  // 10MB per file

    // NAT Traversal
    private boolean behindNat = false;
    private List<String> relayIds = new ArrayList<>();
    private long natKeepaliveMs = 25000;
    private boolean attemptDirectConnect = true;

    // Address cache
    private long addressCacheTtlMs = 300000;  // 5 minutes

    // Pong info advertisement
    private PongDataProvider pongDataProvider;
    private int maxPongDataBytes = 1024;           // Max bytes to include in pong data
    private long pongInfoMinIntervalMs = 2000;     // Per-peer min interval for info pong

    // Balance Management
    private String balanceDbName = "{fid}_balances_leveldb";
    private int balanceBatchSize = 100;
    private long balanceBatchTimeoutMs = 50;
    private long defaultCreditLimit = 0;      // Default credit limit (satoshi); 0 means "compute from pricePerKb"
    private long creditLimitMinSats = 10000;          // Minimum credit limit floor
    private long creditLimitPriceMultiplier = 1000;   // Credit limit = pricePerKb * multiplier (e.g., ~1MB)

    // Balance Sync
    private long balanceSyncIntervalSeconds = 60;  // Balance query interval (seconds)
    private long balanceQueryTimeoutSeconds = 5;   // Balance query timeout (seconds)
    private long balanceQueryMinIntervalMs = 5000;  // Minimum balance query interval (ms, prevent query storm)
    private long requestIdCleanupIntervalMs = 3600000; // RequestId cleanup interval (ms)
    private int maxRequestIdsPerPeer = 10000;      // Maximum requestId count per peer

    // Balance Verification
    private boolean enableBalanceVerification = false;  // Enable balance verification (disabled by default; economics handled at upper layers)
    private int maxAnomalyCount = 10;                  // Maximum anomaly count threshold
    private long maxAnomalyAmount = 10000;             // Maximum anomaly amount threshold (satoshi)
    private long anomalyTimeWindowMs = 3600000;         // Anomaly time window (1 hour)
    private long priceCacheTtlMs = 3600000;            // pricePerKb cache time (1 hour)
    private boolean rejectOnAnomaly = false;           // Reject response on anomaly (default: warning only)

    // Credit Limit Violation and Blacklist
    private int maxViolationsPerTimeWindow = 10;       // Maximum violations per time window
    private long violationTimeWindowMs = 3600000;      // Violation time window (1 hour)
    private long blacklistDurationMs = 86400000;       // Blacklist duration (24 hours)

    // Balance Audit Log
    private int auditLogMaxEntries = 10000;            // Maximum log entries (memory)
    private boolean auditLogEnablePersistence = false; // Enable persistence (default: disabled)
    private long auditLogRetentionDays = 30;           // Persisted log retention days (if enabled)

    // Getters and Setters

    public int getPort() {
        return port;
    }

    public NodeConfig setPort(int port) {
        this.port = port;
        return this;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public NodeConfig setBindAddress(String bindAddress) {
        this.bindAddress = bindAddress;
        return this;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public NodeConfig setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
        return this;
    }

    public String getDownloadDir() {
        return downloadDir;
    }

    public NodeConfig setDownloadDir(String downloadDir) {
        this.downloadDir = downloadDir;
        return this;
    }

    public boolean isAutoAcceptFiles() {
        return autoAcceptFiles;
    }

    public NodeConfig setAutoAcceptFiles(boolean autoAcceptFiles) {
        this.autoAcceptFiles = autoAcceptFiles;
        return this;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public NodeConfig setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
        return this;
    }

    public long getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public NodeConfig setRequestTimeoutMs(long requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
        return this;
    }

    public long getTransferTimeoutMs() {
        return transferTimeoutMs;
    }

    public NodeConfig setTransferTimeoutMs(long transferTimeoutMs) {
        this.transferTimeoutMs = transferTimeoutMs;
        return this;
    }

    public long getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public NodeConfig setConnectionTimeoutMs(long connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
        return this;
    }

    public String getDataDir() {
        return dataDir;
    }

    public NodeConfig setDataDir(String dataDir) {
        this.dataDir = dataDir;
        return this;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public NodeConfig setLogLevel(String logLevel) {
        this.logLevel = logLevel;
        return this;
    }

    public String getLogDir() {
        return logDir;
    }

    public NodeConfig setLogDir(String logDir) {
        this.logDir = logDir;
        return this;
    }

    public int getMaxLogFiles() {
        return maxLogFiles;
    }

    public NodeConfig setMaxLogFiles(int maxLogFiles) {
        this.maxLogFiles = maxLogFiles;
        return this;
    }

    public long getMaxLogSizeBytes() {
        return maxLogSizeBytes;
    }

    public NodeConfig setMaxLogSizeBytes(long maxLogSizeBytes) {
        this.maxLogSizeBytes = maxLogSizeBytes;
        return this;
    }

    public boolean isBehindNat() {
        return behindNat;
    }

    public NodeConfig setBehindNat(boolean behindNat) {
        this.behindNat = behindNat;
        return this;
    }

    public List<String> getRelayIds() {
        return relayIds;
    }

    public NodeConfig setRelayIds(List<String> relayIds) {
        this.relayIds = relayIds;
        return this;
    }

    public long getNatKeepaliveMs() {
        return natKeepaliveMs;
    }

    public NodeConfig setNatKeepaliveMs(long natKeepaliveMs) {
        this.natKeepaliveMs = natKeepaliveMs;
        return this;
    }

    public boolean isAttemptDirectConnect() {
        return attemptDirectConnect;
    }

    public NodeConfig setAttemptDirectConnect(boolean attemptDirectConnect) {
        this.attemptDirectConnect = attemptDirectConnect;
        return this;
    }

    public long getAddressCacheTtlMs() {
        return addressCacheTtlMs;
    }

    public NodeConfig setAddressCacheTtlMs(long addressCacheTtlMs) {
        this.addressCacheTtlMs = addressCacheTtlMs;
        return this;
    }

    public PongDataProvider getPongDataProvider() {
        return pongDataProvider;
    }

    public NodeConfig setPongDataProvider(PongDataProvider pongDataProvider) {
        this.pongDataProvider = pongDataProvider;
        return this;
    }

    public int getMaxPongDataBytes() {
        return maxPongDataBytes;
    }

    public NodeConfig setMaxPongDataBytes(int maxPongDataBytes) {
        this.maxPongDataBytes = maxPongDataBytes;
        return this;
    }

    public long getPongInfoMinIntervalMs() {
        return pongInfoMinIntervalMs;
    }

    public NodeConfig setPongInfoMinIntervalMs(long pongInfoMinIntervalMs) {
        this.pongInfoMinIntervalMs = pongInfoMinIntervalMs;
        return this;
    }

    /**
     * Resolve path with ~ expansion.
     */
    public String resolvePath(String path) {
        if (path.startsWith("~")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }

    /**
     * Get resolved data directory path.
     */
    public String getResolvedDataDir() {
        return resolvePath(dataDir);
    }

    /**
     * Get resolved download directory path.
     */
    public String getResolvedDownloadDir() {
        return resolvePath(downloadDir);
    }

    /**
     * Get resolved log directory path.
     */
    public String getResolvedLogDir() {
        return resolvePath(logDir);
    }

    // Balance Management Getters and Setters

    public String getBalanceDbName() {
        return balanceDbName;
    }

    public NodeConfig setBalanceDbName(String balanceDbName) {
        this.balanceDbName = balanceDbName;
        return this;
    }

    public int getBalanceBatchSize() {
        return balanceBatchSize;
    }

    public NodeConfig setBalanceBatchSize(int balanceBatchSize) {
        this.balanceBatchSize = balanceBatchSize;
        return this;
    }

    public long getBalanceBatchTimeoutMs() {
        return balanceBatchTimeoutMs;
    }

    public NodeConfig setBalanceBatchTimeoutMs(long balanceBatchTimeoutMs) {
        this.balanceBatchTimeoutMs = balanceBatchTimeoutMs;
        return this;
    }

    public long getDefaultCreditLimit() {
        return defaultCreditLimit;
    }

    public NodeConfig setDefaultCreditLimit(long defaultCreditLimit) {
        this.defaultCreditLimit = defaultCreditLimit;
        return this;
    }

    public long getCreditLimitMinSats() {
        return creditLimitMinSats;
    }

    public NodeConfig setCreditLimitMinSats(long creditLimitMinSats) {
        this.creditLimitMinSats = creditLimitMinSats;
        return this;
    }

    public long getCreditLimitPriceMultiplier() {
        return creditLimitPriceMultiplier;
    }

    public NodeConfig setCreditLimitPriceMultiplier(long creditLimitPriceMultiplier) {
        this.creditLimitPriceMultiplier = creditLimitPriceMultiplier;
        return this;
    }

    public long getBalanceSyncIntervalSeconds() {
        return balanceSyncIntervalSeconds;
    }

    public NodeConfig setBalanceSyncIntervalSeconds(long balanceSyncIntervalSeconds) {
        this.balanceSyncIntervalSeconds = balanceSyncIntervalSeconds;
        return this;
    }

    public long getBalanceQueryTimeoutSeconds() {
        return balanceQueryTimeoutSeconds;
    }

    public NodeConfig setBalanceQueryTimeoutSeconds(long balanceQueryTimeoutSeconds) {
        this.balanceQueryTimeoutSeconds = balanceQueryTimeoutSeconds;
        return this;
    }

    public long getBalanceQueryMinIntervalMs() {
        return balanceQueryMinIntervalMs;
    }

    public NodeConfig setBalanceQueryMinIntervalMs(long balanceQueryMinIntervalMs) {
        this.balanceQueryMinIntervalMs = balanceQueryMinIntervalMs;
        return this;
    }

    public long getRequestIdCleanupIntervalMs() {
        return requestIdCleanupIntervalMs;
    }

    public NodeConfig setRequestIdCleanupIntervalMs(long requestIdCleanupIntervalMs) {
        this.requestIdCleanupIntervalMs = requestIdCleanupIntervalMs;
        return this;
    }

    public int getMaxRequestIdsPerPeer() {
        return maxRequestIdsPerPeer;
    }

    public NodeConfig setMaxRequestIdsPerPeer(int maxRequestIdsPerPeer) {
        this.maxRequestIdsPerPeer = maxRequestIdsPerPeer;
        return this;
    }

    public boolean isEnableBalanceVerification() {
        return enableBalanceVerification;
    }

    public NodeConfig setEnableBalanceVerification(boolean enableBalanceVerification) {
        this.enableBalanceVerification = enableBalanceVerification;
        return this;
    }

    public int getMaxAnomalyCount() {
        return maxAnomalyCount;
    }

    public NodeConfig setMaxAnomalyCount(int maxAnomalyCount) {
        this.maxAnomalyCount = maxAnomalyCount;
        return this;
    }

    public long getMaxAnomalyAmount() {
        return maxAnomalyAmount;
    }

    public NodeConfig setMaxAnomalyAmount(long maxAnomalyAmount) {
        this.maxAnomalyAmount = maxAnomalyAmount;
        return this;
    }

    public long getAnomalyTimeWindowMs() {
        return anomalyTimeWindowMs;
    }

    public NodeConfig setAnomalyTimeWindowMs(long anomalyTimeWindowMs) {
        this.anomalyTimeWindowMs = anomalyTimeWindowMs;
        return this;
    }

    public long getPriceCacheTtlMs() {
        return priceCacheTtlMs;
    }

    public NodeConfig setPriceCacheTtlMs(long priceCacheTtlMs) {
        this.priceCacheTtlMs = priceCacheTtlMs;
        return this;
    }

    public boolean isRejectOnAnomaly() {
        return rejectOnAnomaly;
    }

    public NodeConfig setRejectOnAnomaly(boolean rejectOnAnomaly) {
        this.rejectOnAnomaly = rejectOnAnomaly;
        return this;
    }

    public int getMaxViolationsPerTimeWindow() {
        return maxViolationsPerTimeWindow;
    }

    public NodeConfig setMaxViolationsPerTimeWindow(int maxViolationsPerTimeWindow) {
        this.maxViolationsPerTimeWindow = maxViolationsPerTimeWindow;
        return this;
    }

    public long getViolationTimeWindowMs() {
        return violationTimeWindowMs;
    }

    public NodeConfig setViolationTimeWindowMs(long violationTimeWindowMs) {
        this.violationTimeWindowMs = violationTimeWindowMs;
        return this;
    }

    public long getBlacklistDurationMs() {
        return blacklistDurationMs;
    }

    public NodeConfig setBlacklistDurationMs(long blacklistDurationMs) {
        this.blacklistDurationMs = blacklistDurationMs;
        return this;
    }

    public int getAuditLogMaxEntries() {
        return auditLogMaxEntries;
    }

    public NodeConfig setAuditLogMaxEntries(int auditLogMaxEntries) {
        this.auditLogMaxEntries = auditLogMaxEntries;
        return this;
    }

    public boolean isAuditLogEnablePersistence() {
        return auditLogEnablePersistence;
    }

    public NodeConfig setAuditLogEnablePersistence(boolean auditLogEnablePersistence) {
        this.auditLogEnablePersistence = auditLogEnablePersistence;
        return this;
    }

    public long getAuditLogRetentionDays() {
        return auditLogRetentionDays;
    }

    public NodeConfig setAuditLogRetentionDays(long auditLogRetentionDays) {
        this.auditLogRetentionDays = auditLogRetentionDays;
        return this;
    }

    /**
     * Provider for optional pong data.
     */
    @FunctionalInterface
    public interface PongDataProvider {
        /**
         * Build data to include in pong when peer asks for info.
         * @param peerId peer requesting info
         * @return serialized bytes (will be truncated if exceeding maxPongDataBytes)
         */
        byte[] buildPongData(String peerId);
    }
}
