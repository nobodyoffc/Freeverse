package handlers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import config.Settings;
import core.crypto.Hash;
import data.fcData.Income;
import data.feipData.Service;
import data.feipData.serviceParams.Params;
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
import utils.ObjectUtils;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import static config.Settings.CREDIT_LIMIT;

/**
 * BalanceManager implements the FAPI economics model described in
 * Docs/FAPI_ECONOMICS_DESIGN.md. It maintains a single authoritative ledger
 * using a LevelDB KV store with write-batch atomicity and append-only audit
 * records. Only transport metering events and recharge operations call into
 * this component; all economic state lives here.
 */
public class BalanceManager extends Manager<Income> implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(BalanceManager.class);

    private static final long MAX_AMOUNT = 10_000_000_000_000L; // 1e13 satoshi upper bound
    private static final int MAX_KEY_LENGTH = 128;
    private static final int MAX_META_BYTES = 2 * 1024;
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9._:-]+$");
    private static final String BALANCE_PREFIX = "B/";        // peer balance
    private static final String REQUEST_PREFIX = "R/req/";    // requestKey idempotency
    private static final String CASH_PREFIX = "R/cash/";      // cashId idempotency
    private static final String META_KEY = "M/meta";          // meta entry
    private static final String AUDIT_PREFIX = "A/";          // append-only audit rows
    private static final String ZERO_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    private final ReentrantLock lock = new ReentrantLock();
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final Path dbDir;
    private final DB db;
    private volatile Pricing pricing;
    private volatile MetaState metaState;
    private final long defaultCreditLimit;

    public BalanceManager(Settings settings) {
        super(settings, ManagerType.BALANCE);
        Objects.requireNonNull(settings, "settings cannot be null");

        Params params = ObjectUtils.objectToClass(settings.getService().getParams(), Params.class);
        this.pricing = parsePricing(params);

        Long creditLimit = ObjectUtils.objectToClass(settings.getSettingMap().get(CREDIT_LIMIT), Long.class);
        this.defaultCreditLimit = Math.max(0, creditLimit == null ? 0 : creditLimit);

        this.dbDir = settings.getDbDir() == null
            ? Path.of("fapi_balance")
            : Path.of(settings.getDbDir(), settings.getMainFid() + "_" + settings.getSid() + "_fapi_balance");

        try {
            Files.createDirectories(dbDir);
            Options options = new Options();
            options.createIfMissing(true);
            this.db = Iq80DBFactory.factory.open(dbDir.toFile(), options);
            this.metaState = loadMetaState();
            log.info("BalanceManager initialized. pricePerKB={}, orderViaShareBps={}, consumeViaShareBps={}, dbPath={}",
                pricing.getPricePerKb(), pricing.getOrderViaShareBps(), pricing.getConsumeViaShareBps(), dbDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize BalanceManager store", e);
        }
    }

    // -------- Public API --------

    /**
     * Charge by a metering record. A deterministic requestKey is derived from
     * the record to keep idempotency.
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
        if (pricing.getPricePerKb() == 0) {
            return ChargeResult.ok(0, getBalance(peerId));
        }

        long kb = (payloadBytes + 1023) / 1024;
        long amount = safeMultiply(kb, pricing.getPricePerKb());
        if (amount < 0 || amount > MAX_AMOUNT) {
            return ChargeResult.error(ResultCode.INVALID_AMOUNT, "Calculated charge is invalid");
        }

        String rawKey = peerId + ":" + meterRecord.getDirection() + ":" + meterRecord.getMessageType() + ":" +
            meterRecord.getSendTimestampMillis() + ":" + payloadBytes;
        String requestKey = rawKey.length() > MAX_KEY_LENGTH ? Hex.toHex(Hash.sha256(rawKey.getBytes(StandardCharsets.UTF_8))) : rawKey;

        ChargeMeta meta = new ChargeMeta(meterRecord.getDirection() == null ? null : meterRecord.getDirection().name(),
            meterRecord.getMessageType() == null ? null : meterRecord.getMessageType().name(),
            payloadBytes,
            meterRecord.getSendTimestampMillis(),
            meterRecord.getReceiveTimestampMillis(),
            meterRecord.getRetransmitCount(),
            meterRecord.getRttMicros(),
            meterRecord.getLossRateHint());

        String metaJson = gson.toJson(meta);
        if (metaJson.getBytes(StandardCharsets.UTF_8).length > MAX_META_BYTES) {
            return ChargeResult.error(ResultCode.INVALID_META, "Meta too large");
        }

        return charge(requestKey, peerId, amount, metaJson);
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
     * Credit (recharge) a user with cashId idempotency.
     */
    public CreditResult credit(String userId, String cashId, long amount, String src, CreditStatus status,
                               Long height, String blockId) {
        if (!isValidKey(userId) || !isValidKey(cashId)) {
            return CreditResult.error(ResultCode.INVALID_KEY, "Invalid userId or cashId");
        }
        if (!isValidAmount(amount)) {
            return CreditResult.error(ResultCode.INVALID_AMOUNT, "Invalid amount");
        }

        lock.lock();
        try {
            CashRecord existing = getCashRecord(cashId);
            if (existing != null) {
                boolean conflict = !userId.equals(existing.getUserId()) || existing.getAmount() != amount;
                if (conflict) {
                    return CreditResult.error(ResultCode.ALREADY_EXISTS_WITH_CONFLICT, "cashId conflict");
                }
                if (existing.getStatus() == status) {
                    return CreditResult.fromExisting(ResultCode.ALREADY_EXISTS, existing, getBalance(userId));
                }
            }

            long delta = creditDelta(existing == null ? null : existing.getStatus(), status, amount);
            PeerBalance balance = getPeerBalance(userId);
            long newBalance = balance.getBalance() + delta;
            balance.bumpSeq();
            balance.setBalance(newBalance);

            CashRecord record = new CashRecord(userId, amount, src, status, height, blockId, System.currentTimeMillis());
            persistCredit(cashId, record, balance);

            return new CreditResult(ResultCode.OK, amount, newBalance, balance.getCreditLimit(), status);
        } catch (Exception e) {
            log.error("Credit failed", e);
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
            return new BalanceView(peerId, 0, defaultCreditLimit, 0);
        }
        PeerBalance balance = getPeerBalance(peerId);
        return new BalanceView(peerId, balance.getBalance(), balance.getCreditLimit(),
            balance.getBalance() + balance.getCreditLimit());
    }

    /**
     * Reload pricing parameters from the Service params (manual refresh).
     */
    public synchronized boolean refreshPricingFromService() {
        Service service = settings.getService();
        Params params = ObjectUtils.objectToClass(service.getParams(), Params.class);
        Pricing newPricing = parsePricing(params);
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

    @Override
    public void close() {
        super.close();
        try {
            db.close();
        } catch (IOException e) {
            log.warn("Failed to close BalanceManager DB", e);
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
        AuditRecord audit = AuditRecord.income(cashId, record.getUserId(), record.getAmount(), metaState, record.getStatus(), record.getSrc());
        try (WriteBatch batch = db.createWriteBatch()) {
            putPeerBalance(batch, record.getUserId(), balance);
            putCashRecord(batch, cashId, record);
            appendAudit(batch, audit);
            writeMeta(batch);
            db.write(batch);
        } catch (IOException e) {
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

    private Pricing parsePricing(Params params) {
        if (params == null || params.getPricePerKB() == null) {
            throw new IllegalStateException("pricePerKB is required before starting BalanceManager");
        }
        long pricePerKb = FchUtils.coinToSatoshi(Double.parseDouble(params.getPricePerKB()));
        if (pricePerKb < 0) {
            throw new IllegalStateException("pricePerKB must be >= 0");
        }
        long orderShare = 0;
        if(params.getOrderViaShare()!=null)
            orderShare = parseShare(params.getOrderViaShare(), "orderViaShare");

        long consumeShare = 0;
        if(params.getConsumeViaShare()!=null)
            consumeShare = parseShare(params.getConsumeViaShare(), "consumeViaShare");

        return new Pricing(pricePerKb, orderShare, consumeShare);
    }

    private long parseShare(String shareStr, String name) {
        if (shareStr == null) {
            throw new IllegalStateException(name + " is required before starting BalanceManager");
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

        public BalanceView(String peerId, long balance, long creditLimit, long available) {
            this.peerId = peerId;
            this.balance = balance;
            this.creditLimit = creditLimit;
            this.available = available;
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
    }

    public static class Pricing {
        private final long pricePerKb;
        private final long orderViaShareBps;
        private final long consumeViaShareBps;

        public Pricing(long pricePerKb, long orderViaShareBps, long consumeViaShareBps) {
            this.pricePerKb = pricePerKb;
            this.orderViaShareBps = orderViaShareBps;
            this.consumeViaShareBps = consumeViaShareBps;
        }

        public long getPricePerKb() {
            return pricePerKb;
        }

        public long getOrderViaShareBps() {
            return orderViaShareBps;
        }

        public long getConsumeViaShareBps() {
            return consumeViaShareBps;
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
}
