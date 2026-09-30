package fapi.components;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import core.crypto.Hash;
import data.apipData.Fcdsl;
import data.fcData.DockItem;
import db.fcdsl.FcdslException;
import db.fcdsl.FcdslResult;
import fapi.components.dock.DockStore;
import fapi.migrate.EsIndexPages;
import data.feipData.Service;
import data.feipData.ServiceType;
import fapi.AbstractFapiComponent;
import fapi.FapiBalanceManager;
import fapi.FapiCode;
import fapi.client.FapiClient;
import fapi.message.FapiRequest;
import fapi.message.FapiResponse;
import fapi.message.UnifiedCodec.UnifiedResponse;
import config.Settings;
import constants.FieldNames;
import utils.FchUtils;
import utils.Hex;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Base64;

/**
 * DOCK Component - Store-and-forward messaging service via FAPI.
 * <p>
 * DOCK temporarily stores short data for recipients who may not be online.
 * Data is stored in a LevelDB {@link DockStore} (metadata + Base64 payload), migrated once
 * from the Elasticsearch index that held it before.
 * Large data should use the DISK service instead.
 * <p>
 * Features:
 * - Multi-recipient support (FID, team ID, group ID, room ID)
 * - TTL-based expiration
 * - Charging model:
 *   - Sender pays: ingress fee + storage fee (size * days)
 *   - Recipient pays: egress fee when retrieving
 * <p>
 * API Methods:
 * - dock.put    - Store data for recipients (sender pays storage + ingress)
 * - dock.get    - Retrieve data as binary (recipient pays egress)
 * - dock.fetch  - List items with inline Base64 data for multiple recipient IDs (recipient pays egress)
 * - dock.list   - List items metadata for a given recipient ID
 * - dock.check  - Check item status without downloading
 * - dock.delete - Sender removes item (partial refund)
 * - dock.extend - Extend TTL (sender pays additional storage)
 */
public class DockComponent extends AbstractFapiComponent {
    
    private static final String COMPONENT_NAME = "DOCK";
    private static final int DEFAULT_MAX_DAYS = 7;
    private static final int MAX_ALLOWED_DAYS = 365;
    private static final int MAX_RECIPIENTS = 100;
    private static final long DEFAULT_MAX_DATA_SIZE = 64 * 1024; // 64 KB default; large data should use DISK
    private static final int BLOCKS_PER_DAY = 1440; // ~1 block per minute for FCH

    public static final String KEY_DOCK_FORWARD_ENABLED = "dockForwardEnabled";
    /** Setting: stored items one list/fetch may read before it fails as too broad. */
    public static final String KEY_DOCK_MAX_EXAMINED = "dockMaxExamined";
    private static final long DEFAULT_MAX_EXAMINED = 100_000;

    private static final java.security.SecureRandom NONCE_RANDOM = new java.security.SecureRandom();

    private DockStore store;
    private FapiBalanceManager balanceManager;
    private long maxDataSize = DEFAULT_MAX_DATA_SIZE;
    private boolean forwardEnabled = true; // allow forwarding by default
    
    // Pricing (from Service)
    private long pricePerKBIn;      // Ingress fee (satoshi per KB)
    private long pricePerKBOut;     // Egress fee (satoshi per KB)
    private long pricePerKBDay;     // Storage fee (satoshi per KB per day)
    private int defaultMaxDays = DEFAULT_MAX_DAYS;
    
    @Override
    public String getName() {
        return COMPONENT_NAME;
    }
    
    @Override
    public List<String> getApiList() {
        return List.of(
            "dock.put",     // Store data for recipients
            "dock.get",     // Retrieve data as binary
            "dock.fetch",   // List items with inline Base64 data
            "dock.list",    // List items metadata
            "dock.check",   // Check item status
            "dock.delete",  // Sender removes item
            "dock.extend"   // Extend TTL
        );
    }
    
    @Override
    protected void doInitialize() {
        this.balanceManager = server.getBalanceManager();
        
        loadServiceConfig();
        openStore();
        
        log.info("DOCK component initialized: items={}, maxDataSize={}, pricePerKBIn={}, pricePerKBOut={}, pricePerKBDay={}, defaultMaxDays={}, forwardEnabled={}",
                store.count(), maxDataSize, pricePerKBIn, pricePerKBOut, pricePerKBDay, defaultMaxDays, forwardEnabled);
    }
    
    /**
     * Load pricing and configuration from Service.
     */
    private void loadServiceConfig() {
        Service service = settings.getService();
        if (service == null) {
            log.warn("Service is null, using default pricing");
            this.pricePerKBIn = 10;
            this.pricePerKBOut = 10;
            this.pricePerKBDay = 1;
            return;
        }
        
        // Parse pricePerKBIn
        if (service.getPricePerKBIn() != null && !service.getPricePerKBIn().isEmpty()) {
            this.pricePerKBIn = FchUtils.coinToSatoshi(Double.parseDouble(service.getPricePerKBIn()));
        } else if (service.getPricePerKB() != null && !service.getPricePerKB().isEmpty()) {
            this.pricePerKBIn = FchUtils.coinToSatoshi(Double.parseDouble(service.getPricePerKB()));
        } else {
            this.pricePerKBIn = 10;
        }
        
        // Parse pricePerKBOut
        if (service.getPricePerKBOut() != null && !service.getPricePerKBOut().isEmpty()) {
            this.pricePerKBOut = FchUtils.coinToSatoshi(Double.parseDouble(service.getPricePerKBOut()));
        } else if (service.getPricePerKB() != null && !service.getPricePerKB().isEmpty()) {
            this.pricePerKBOut = FchUtils.coinToSatoshi(Double.parseDouble(service.getPricePerKB()));
        } else {
            this.pricePerKBOut = 10;
        }
        
        // Parse pricePerKBDay
        if (service.getPricePerKBDay() != null && !service.getPricePerKBDay().isEmpty()) {
            this.pricePerKBDay = FchUtils.coinToSatoshi(Double.parseDouble(service.getPricePerKBDay()));
        } else {
            this.pricePerKBDay = 1;
        }
        
        // Parse maxDataSize (bytes as string)
        if (service.getMaxDataSize() != null && !service.getMaxDataSize().isEmpty()) {
            try {
                this.maxDataSize = Long.parseLong(service.getMaxDataSize());
            } catch (NumberFormatException e) {
                log.warn("Invalid maxDataSize '{}', using default {}", service.getMaxDataSize(), DEFAULT_MAX_DATA_SIZE);
            }
        }
        
        // Parse defaultMaxDays with priority: on-chain -> settingMap -> hardcoded default
        boolean maxDaysResolved = false;
        
        // 1. On-chain: service.dataExpiresInDays
        if (service.getDataExpiresInDays() != null && !service.getDataExpiresInDays().isEmpty()) {
            try {
                this.defaultMaxDays = Integer.parseInt(service.getDataExpiresInDays());
                maxDaysResolved = true;
            } catch (NumberFormatException e) {
                log.warn("Invalid on-chain dataExpiresInDays '{}', trying settingMap", service.getDataExpiresInDays());
            }
        }
        
        // 2. User setting: settingMap.dataExpiresInDays
        if (!maxDaysResolved) {
            Map<String, Object> settingMap = settings.getSettingMap();
            if (settingMap != null && settingMap.containsKey(FieldNames.DATA_EXPIRES_IN_DAYS)) {
                Object val = settingMap.get(FieldNames.DATA_EXPIRES_IN_DAYS);
                if (val instanceof Number) {
                    this.defaultMaxDays = ((Number) val).intValue();
                    maxDaysResolved = true;
                }
            }
        }
        
        // 3. Hardcoded default (DEFAULT_MAX_DAYS = 7) is already the field initializer

        // Load forwardEnabled from settingMap (default: true)
        Map<String, Object> settingMap = settings.getSettingMap();
        if (settingMap != null && settingMap.containsKey(KEY_DOCK_FORWARD_ENABLED)) {
            Object val = settingMap.get(KEY_DOCK_FORWARD_ENABLED);
            if (val instanceof Boolean) {
                this.forwardEnabled = (Boolean) val;
            } else {
                this.forwardEnabled = !"false".equalsIgnoreCase(String.valueOf(val));
            }
        }
    }

    public boolean isForwardEnabled() {
        return forwardEnabled;
    }

    public void setForwardEnabled(boolean forwardEnabled) {
        this.forwardEnabled = forwardEnabled;
    }
    
    /**
     * Open the LevelDB store, and on first start copy in the items of the Elasticsearch index
     * that held DOCK before. A server without ES has nothing to copy.
     */
    private void openStore() {
        String sid = settings.getSid();
        long maxExamined = DEFAULT_MAX_EXAMINED;
        Map<String, Object> settingMap = settings.getSettingMap();
        if (settingMap != null && settingMap.get(KEY_DOCK_MAX_EXAMINED) instanceof Number n) maxExamined = n.longValue();
        Path dir = settings.getDbDir() == null
                ? Path.of("fapi_dock")
                : Path.of(settings.getDbDir(), settings.getMainFid() + "_" + sid + "_fapi_dock");
        try {
            this.store = DockStore.open(dir, maxExamined);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot open DOCK store at " + dir + ": " + e.getMessage(), e);
        }
        if (store.isMigrated()) return;
        if (!(settings.getClient(ServiceType.ES) instanceof ElasticsearchClient esClient)) {
            store.markMigrated();
            return;
        }
        String indexName = Settings.addSidBriefToName(sid, "dock");
        try {
            long n = store.migrateFrom(new EsIndexPages<>(esClient, indexName, DockItem.class));
            log.info("DOCK: migrated {} items from Elasticsearch index {} to {}", n, indexName, dir);
        } catch (Exception e) {
            // Not marked: the next start tries again. New items are stored meanwhile.
            log.error("DOCK: migration from Elasticsearch index {} failed; will retry at next start: {}",
                    indexName, e.getMessage(), e);
        }
    }
    
    @Override
    protected void doClose(long timeoutMs) throws InterruptedException {
        super.doClose(timeoutMs);
        if (store != null) {
            try {
                store.close();
            } catch (IOException e) {
                log.warn("DOCK: closing the store failed: {}", e.getMessage());
            }
        }
    }
    
    @Override
    public FapiResponse handleRequest(FapiRequest request, String peerId) {
        String method = request.getMethodName();
        String requestId = request.getId();
        
        if (method == null) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "Method name is missing");
        }
        
        return switch (method) {
            case "list" -> handleList(request, peerId);
            case "fetch" -> handleFetch(request, peerId);
            case "check" -> handleCheck(request, peerId);
            case "delete" -> handleDelete(request, peerId);
            case "extend" -> handleExtend(request, peerId);
            case "put" -> errorResponse(requestId, FapiCode.BAD_REQUEST, "PUT must use unified binary protocol");
            case "get" -> errorResponse(requestId, FapiCode.BAD_REQUEST, "GET must use unified binary protocol");
            default -> errorResponse(requestId, FapiCode.METHOD_NOT_ALLOWED, "Unknown method: " + method);
        };
    }
    
    @Override
    public boolean returnsBinaryData(String method) {
        return "get".equals(method);
    }
    
    @Override
    public UnifiedResponse handleUnifiedRequest(FapiRequest request, byte[] binaryData, String peerId) {
        String method = request.getMethodName();
        String requestId = request.getId();
        
        if (method == null) {
            return new UnifiedResponse(
                errorResponse(requestId, FapiCode.BAD_REQUEST, "Method name is missing"),
                null);
        }
        
        return switch (method) {
            case "put" -> handlePut(request, binaryData, peerId);
            case "get" -> handleGet(request, peerId);
            case "fetch" -> new UnifiedResponse(handleFetch(request, peerId), null);
            case "list" -> new UnifiedResponse(handleList(request, peerId), null);
            case "check" -> new UnifiedResponse(handleCheck(request, peerId), null);
            case "delete" -> new UnifiedResponse(handleDelete(request, peerId), null);
            case "extend" -> new UnifiedResponse(handleExtend(request, peerId), null);
            default -> new UnifiedResponse(
                errorResponse(requestId, FapiCode.METHOD_NOT_ALLOWED, "Unknown method: " + method),
                null);
        };
    }
    
    // ==================== PUT Handler ====================
    
    /**
     * Handle PUT request - store data for recipients.
     * Sender pays: ingress fee + storage fee (size * days * pricePerKBDay).
     * Data is stored as Base64 directly in Elasticsearch.
     */
    private UnifiedResponse handlePut(FapiRequest request, byte[] binaryData, String peerId) {
        String requestId = request.getId();
        
        if (binaryData == null || binaryData.length == 0) {
            return new UnifiedResponse(
                errorResponse(requestId, FapiCode.BAD_REQUEST, "Data content is required"),
                null);
        }
        
        if (binaryData.length > maxDataSize) {
            return new UnifiedResponse(
                errorResponse(requestId, FapiCode.BAD_REQUEST, 
                    "Data size exceeds maximum allowed: " + maxDataSize + " bytes. Use DISK service for large data."),
                null);
        }
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = parseParams(request.getParams(), Map.class);
            
            if (params == null) {
                return new UnifiedResponse(
                    errorResponse(requestId, FapiCode.BAD_REQUEST, "params is required"),
                    null);
            }
            
            @SuppressWarnings("unchecked")
            List<String> recipients = (List<String>) params.get(FieldNames.RECIPIENTS);
            if (recipients == null || recipients.isEmpty()) {
                return new UnifiedResponse(
                    errorResponse(requestId, FapiCode.BAD_REQUEST, "recipients list is required"),
                    null);
            }
            
            if (recipients.size() > MAX_RECIPIENTS) {
                return new UnifiedResponse(
                    errorResponse(requestId, FapiCode.BAD_REQUEST, 
                        "Too many recipients, max: " + MAX_RECIPIENTS),
                    null);
            }
            
            int maxDays = defaultMaxDays;
            if (params.containsKey(FieldNames.MAX_DAYS)) {
                Object maxDaysObj = params.get(FieldNames.MAX_DAYS);
                if (maxDaysObj instanceof Number) {
                    maxDays = ((Number) maxDaysObj).intValue();
                }
            }
            if (maxDays <= 0 || maxDays > MAX_ALLOWED_DAYS) {
                return new UnifiedResponse(
                    errorResponse(requestId, FapiCode.BAD_REQUEST, 
                        "maxDays must be between 1 and " + MAX_ALLOWED_DAYS),
                    null);
            }
            
            // Check for forwarding to another DOCK
            String targetDockUrl = (String) params.get(FieldNames.TARGET_DOCK_URL);
            if (targetDockUrl != null && !targetDockUrl.isEmpty()
                    && !server.isSelfUrl(targetDockUrl)) {
                if (!forwardEnabled) {
                    return new UnifiedResponse(
                        errorResponse(requestId, FapiCode.METHOD_NOT_ALLOWED, "Forwarding is disabled on this server"),
                        null);
                }
                return handlePutForward(request, binaryData, peerId, recipients, maxDays, targetDockUrl);
            }
            
            // Calculate fees
            long dataSize = binaryData.length;
            long sizeKB = (dataSize + 1023) / 1024;
            long ingressFee = sizeKB * pricePerKBIn;
            long storageFee = sizeKB * maxDays * pricePerKBDay;
            long totalFee = ingressFee + storageFee;
            
            if (balanceManager != null && !balanceManager.canAfford(peerId, totalFee)) {
                return new UnifiedResponse(
                    errorResponse(requestId, FapiCode.PAYMENT_REQUIRED, 
                        "Insufficient balance. Required: " + totalFee + " satoshi"),
                    null);
            }
            
            // Generate dockId from sender + recipients + timestamp + random bytes: without the
            // random part, two puts to the same recipients in one millisecond got one id, and
            // the second overwrote the first
            byte[] nonce = new byte[8];
            NONCE_RANDOM.nextBytes(nonce);
            String dockIdInput = peerId + ":" + String.join(",", recipients) + ":" + System.currentTimeMillis()
                    + ":" + Hex.toHex(nonce);
            String dockId = Hex.toHex(Hash.sha256(dockIdInput.getBytes(StandardCharsets.UTF_8)));
            
            long currentHeight = balanceManager != null ? balanceManager.getBestHeight() : 0;
            long expireHeight = currentHeight + (long) maxDays * BLOCKS_PER_DAY;
            
            String dataType = params.containsKey(FieldNames.DATA_TYPE)
                    ? params.get(FieldNames.DATA_TYPE).toString() : null;
            
            // Create DockItem with data stored as Base64
            DockItem item = new DockItem(dockId, peerId, recipients, dataSize, currentHeight, expireHeight);
            item.setMaxDays(maxDays);
            item.setStorageFee(storageFee);
            item.setIngressFee(ingressFee);
            item.setCreateTime(System.currentTimeMillis());
            item.setDataBase64(Base64.getEncoder().encodeToString(binaryData));
            if (dataType != null) {
                item.setDataType(dataType);
            }
            
            // Store metadata + data as one item
            store.put(item);
            
            if (balanceManager != null && totalFee > 0) {
                String chargeKey = "dock.put:" + dockId;
                balanceManager.charge(chargeKey, peerId, totalFee, "dock.put:in+storage");
            }
            
            log.info("DOCK PUT: sender={}, id={}, recipients={}, size={}, maxDays={}, storageFee={}, ingressFee={}",
                    peerId, dockId, recipients.size(), dataSize, maxDays, storageFee, ingressFee);
            
            Map<String, Object> responseData = new LinkedHashMap<>();
            responseData.put(FieldNames.ID, dockId);
            responseData.put(FieldNames.SIZE, dataSize);
            responseData.put(FieldNames.MAX_DAYS, maxDays);
            responseData.put(FieldNames.CREATE_HEIGHT, currentHeight);
            responseData.put(FieldNames.EXPIRE_HEIGHT, expireHeight);
            responseData.put(FieldNames.STORAGE_FEE, storageFee);
            responseData.put(FieldNames.INGRESS_FEE, ingressFee);
            responseData.put("totalFee", totalFee);
            if (dataType != null) {
                responseData.put(FieldNames.DATA_TYPE, dataType);
            }
            
            return new UnifiedResponse(successResponse(requestId, responseData), null);
            
        } catch (Exception e) {
            log.error("Failed to handle PUT request: {}", e.getMessage(), e);
            return new UnifiedResponse(
                errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Failed to store: " + e.getMessage()),
                null);
        }
    }
    
    // ==================== PUT Forward Handler ====================
    
    /**
     * Handle PUT request with forwarding to another DOCK server.
     * This DOCK acts as a relay: connects to target DOCK, stores there,
     * and charges the sender for local forwarding fee + remote DOCK's fee.
     */
    private UnifiedResponse handlePutForward(FapiRequest request, byte[] binaryData, String peerId,
            List<String> recipients, int maxDays, String targetDockUrl) {
        String requestId = request.getId();
        
        try {
            FapiClient remoteClient = server.getOrCreateClient(targetDockUrl);
            if (remoteClient == null) {
                return new UnifiedResponse(
                    errorResponse(requestId, FapiCode.BAD_GATEWAY, 
                        "Cannot connect to target DOCK: " + targetDockUrl),
                    null);
            }
            
            long dataSize = binaryData.length;
            long sizeKB = (dataSize + 1023) / 1024;
            long localIngressFee = sizeKB * pricePerKBIn;
            long localEgressFee = sizeKB * pricePerKBOut;
            long localFee = localIngressFee + localEgressFee;
            
            long estimatedRemoteFee = sizeKB * (pricePerKBIn + maxDays * pricePerKBDay);
            long estimatedTotalFee = localFee + estimatedRemoteFee;
            
            if (balanceManager != null && !balanceManager.canAfford(peerId, estimatedTotalFee)) {
                return new UnifiedResponse(
                    errorResponse(requestId, FapiCode.PAYMENT_REQUIRED, 
                        "Insufficient balance. Estimated required: " + estimatedTotalFee + " satoshi"),
                    null);
            }
            
            DockItem remoteResult = remoteClient.dockPut(binaryData, recipients, maxDays);
            
            if (remoteResult == null) {
                server.removeClient(targetDockUrl);
                String errorMsg = remoteClient.getLastError() != null 
                        ? remoteClient.getLastError().getMessage() 
                        : "Unknown error";
                return new UnifiedResponse(
                    errorResponse(requestId, FapiCode.BAD_GATEWAY, 
                        "Remote DOCK rejected request: " + errorMsg),
                    null);
            }
            
            long remoteStorageFee = remoteResult.getStorageFee() != null ? remoteResult.getStorageFee() : 0;
            long remoteIngressFee = remoteResult.getIngressFee() != null ? remoteResult.getIngressFee() : 0;
            long remoteFee = remoteStorageFee + remoteIngressFee;
            long totalFee = localFee + remoteFee;
            
            if (balanceManager != null && totalFee > 0) {
                String chargeKey = "dock.put:forward:" + remoteResult.getId();
                balanceManager.charge(chargeKey, peerId, totalFee, "dock.put:forward");
            }
            
            log.info("DOCK PUT FORWARD: sender={}, target={}, dockId={}, size={}, localFee={} (in={}, out={}), remoteFee={}, totalFee={}",
                    peerId, targetDockUrl, remoteResult.getId(), dataSize, localFee, localIngressFee, localEgressFee, remoteFee, totalFee);
            
            Map<String, Object> responseData = new LinkedHashMap<>();
            responseData.put(FieldNames.ID, remoteResult.getId());
            responseData.put(FieldNames.SIZE, dataSize);
            responseData.put(FieldNames.MAX_DAYS, maxDays);
            responseData.put(FieldNames.TARGET_DOCK_URL, targetDockUrl);
            responseData.put(FieldNames.CREATE_HEIGHT, remoteResult.getCreateHeight());
            responseData.put(FieldNames.EXPIRE_HEIGHT, remoteResult.getExpireHeight());
            responseData.put(FieldNames.LOCAL_FEE, localFee);
            responseData.put(FieldNames.REMOTE_FEE, remoteFee);
            responseData.put("totalFee", totalFee);
            responseData.put(FieldNames.FORWARDED, true);
            
            return new UnifiedResponse(successResponse(requestId, responseData), null);
            
        } catch (Exception e) {
            log.error("Failed to forward PUT request to {}: {}", targetDockUrl, e.getMessage(), e);
            return new UnifiedResponse(
                errorResponse(requestId, FapiCode.INTERNAL_ERROR, 
                    "Failed to forward: " + e.getMessage()),
                null);
        }
    }
    
    // ==================== GET Handler ====================
    
    /**
     * Handle GET request - retrieve data as binary.
     * Recipient pays egress fee.
     */
    private UnifiedResponse handleGet(FapiRequest request, String peerId) {
        String requestId = request.getId();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> params = parseParams(request.getParams(), Map.class);
        
        if (params == null || !params.containsKey(FieldNames.ID)) {
            return new UnifiedResponse(
                errorResponse(requestId, FapiCode.BAD_REQUEST, "id parameter is required"),
                null);
        }
        
        String dockId = params.get(FieldNames.ID).toString();
        
        String recipientId = peerId;
        if (params.containsKey("recipientId")) {
            recipientId = params.get("recipientId").toString();
        }
        
        try {
            DockItem item = store.get(dockId);
            if (item == null) {
                return new UnifiedResponse(
                    errorResponse(requestId, FapiCode.NOT_FOUND, "Item not found: " + dockId),
                    null);
            }
            
            if (!item.isRecipient(recipientId) && !item.isRecipient(peerId)) {
                return new UnifiedResponse(
                    errorResponse(requestId, FapiCode.FORBIDDEN, "You are not a recipient of this item"),
                    null);
            }
            
            long currentHeight = balanceManager != null ? balanceManager.getBestHeight() : 0;
            if (item.isExpired(currentHeight)) {
                return new UnifiedResponse(
                    errorResponse(requestId, FapiCode.GONE, "Item has expired"),
                    null);
            }
            
            if (item.getDataBase64() == null) {
                return new UnifiedResponse(
                    errorResponse(requestId, FapiCode.NOT_FOUND, "Data not found for item: " + dockId),
                    null);
            }
            
            byte[] content = Base64.getDecoder().decode(item.getDataBase64());
            
            long sizeKB = (content.length + 1023) / 1024;
            long egressFee = sizeKB * pricePerKBOut;
            
            if (balanceManager != null && egressFee > 0 && !balanceManager.canAfford(peerId, egressFee)) {
                return new UnifiedResponse(
                    errorResponse(requestId, FapiCode.PAYMENT_REQUIRED, 
                        "Insufficient balance for egress fee: " + egressFee + " satoshi"),
                    null);
            }
            
            if (balanceManager != null && egressFee > 0) {
                String chargeKey = "dock.get:out:" + dockId + ":" + peerId + ":" + System.currentTimeMillis();
                balanceManager.charge(chargeKey, peerId, egressFee, "dock.get:out");
            }
            
            log.info("DOCK GET: recipient={}, dockId={}, size={}, egressFee={}",
                    peerId, dockId, content.length, egressFee);
            
            Map<String, Object> responseData = new LinkedHashMap<>();
            responseData.put(FieldNames.ID, dockId);
            responseData.put(FieldNames.SENDER, item.getSender());
            responseData.put(FieldNames.SIZE, content.length);
            responseData.put(FieldNames.CREATE_TIME, item.getCreateTime());
            responseData.put(FieldNames.EXPIRE_HEIGHT, item.getExpireHeight());
            responseData.put(FieldNames.EGRESS_FEE, egressFee);
            
            FapiResponse response = successResponse(requestId, responseData);
            response.setDataSize((long) content.length);
            
            return new UnifiedResponse(response, content);
            
        } catch (Exception e) {
            log.error("Failed to handle GET request: {}", e.getMessage(), e);
            return new UnifiedResponse(
                errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Failed to retrieve: " + e.getMessage()),
                null);
        }
    }
    
    // ==================== LIST Handler ====================
    
    /**
     * Handle LIST request - list items metadata for a given recipient ID.
     * Does not return data content. Use fetch or get to retrieve data.
     */
    private FapiResponse handleList(FapiRequest request, String peerId) {
        String requestId = request.getId();
        
        String recipientId = peerId;
        @SuppressWarnings("unchecked")
        Map<String, Object> params = parseParams(request.getParams(), Map.class);
        if (params != null && params.containsKey("recipientId")) {
            recipientId = params.get("recipientId").toString();
        }
        
        try {
            long currentHeight = balanceManager != null ? balanceManager.getBestHeight() : 0;
            FcdslResult<DockItem> page = store.find(request.getFcdsl(), List.of(recipientId), currentHeight, 20, 100);
            
            List<Map<String, Object>> results = new ArrayList<>();
            for (DockItem item : page.getItems()) {
                // Metadata only: no data content in a list
                Map<String, Object> itemInfo = new LinkedHashMap<>();
                itemInfo.put(FieldNames.ID, item.getId());
                itemInfo.put(FieldNames.SENDER, item.getSender());
                itemInfo.put(FieldNames.SIZE, item.getSize());
                itemInfo.put(FieldNames.CREATE_TIME, item.getCreateTime());
                itemInfo.put(FieldNames.EXPIRE_HEIGHT, item.getExpireHeight());
                itemInfo.put(FieldNames.REMAINING_DAYS, item.getRemainingDays(currentHeight, BLOCKS_PER_DAY));
                if (item.getDataType() != null) {
                    itemInfo.put(FieldNames.DATA_TYPE, item.getDataType());
                }
                long sizeKB = (item.getSize() + 1023) / 1024;
                itemInfo.put(FieldNames.EGRESS_FEE, sizeKB * pricePerKBOut);
                results.add(itemInfo);
            }
            
            FapiResponse resp = successResponse(requestId, results, (long) results.size(), page.getTotal());
            if (page.getLast() != null) {
                resp.setLast(page.getLast());
            }
            return resp;
            
        } catch (FcdslException e) {
            return queryRejected(requestId, e);
        } catch (Exception e) {
            log.error("Failed to handle LIST request: {}", e.getMessage(), e);
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Query failed: " + e.getMessage());
        }
    }
    
    /** A query the store won't run: 400 when it is wrong, 501 when it is unsupported or too broad. */
    private FapiResponse queryRejected(String requestId, FcdslException e) {
        int code = e.getReason() == FcdslException.Reason.BAD_QUERY ? FapiCode.BAD_REQUEST : FapiCode.NOT_IMPLEMENTED;
        return errorResponse(requestId, code, e.getMessage());
    }
    
    // ==================== FETCH Handler ====================
    
    /**
     * Handle FETCH request - list items with inline Base64 data.
     * Supports multiple recipientIds in one request.
     * Supports search_after pagination via fcdsl.after / response.last.
     * Recipient pays egress fee for all data returned.
     */
    private FapiResponse handleFetch(FapiRequest request, String peerId) {
        String requestId = request.getId();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> params = parseParams(request.getParams(), Map.class);
        
        List<String> recipientIds = new ArrayList<>();
        if (params != null && params.containsKey(FieldNames.RECIPIENT_IDS)) {
            @SuppressWarnings("unchecked")
            List<String> ids = (List<String>) params.get(FieldNames.RECIPIENT_IDS);
            if (ids != null) {
                recipientIds.addAll(ids);
            }
        } else if (params != null && params.containsKey("recipientId")) {
            recipientIds.add(params.get("recipientId").toString());
        }
        
        if (recipientIds.isEmpty()) {
            recipientIds.add(peerId);
        }
        
        try {
            long currentHeight = balanceManager != null ? balanceManager.getBestHeight() : 0;
            FcdslResult<DockItem> page = store.find(request.getFcdsl(), recipientIds, currentHeight, 20, 50);
            
            List<Map<String, Object>> results = new ArrayList<>();
            long totalEgressBytes = 0;
            
            for (DockItem item : page.getItems()) {
                Map<String, Object> itemInfo = new LinkedHashMap<>();
                itemInfo.put(FieldNames.ID, item.getId());
                itemInfo.put(FieldNames.SENDER, item.getSender());
                itemInfo.put(FieldNames.SIZE, item.getSize());
                itemInfo.put(FieldNames.CREATE_TIME, item.getCreateTime());
                itemInfo.put(FieldNames.EXPIRE_HEIGHT, item.getExpireHeight());
                itemInfo.put(FieldNames.RECIPIENTS, item.getRecipients());
                if (item.getDataType() != null) {
                    itemInfo.put(FieldNames.DATA_TYPE, item.getDataType());
                }
                
                // Data is stored as Base64, just pass it through
                if (item.getDataBase64() != null) {
                    itemInfo.put(FieldNames.DATA_BASE64, item.getDataBase64());
                    totalEgressBytes += item.getSize() != null ? item.getSize() : 0;
                }
                
                results.add(itemInfo);
            }
            
            // Charge egress fee for all data returned
            if (balanceManager != null && totalEgressBytes > 0) {
                long totalSizeKB = (totalEgressBytes + 1023) / 1024;
                long egressFee = totalSizeKB * pricePerKBOut;
                if (egressFee > 0) {
                    if (!balanceManager.canAfford(peerId, egressFee)) {
                        return errorResponse(requestId, FapiCode.PAYMENT_REQUIRED,
                                "Insufficient balance for egress fee: " + egressFee + " satoshi");
                    }
                    String chargeKey = "dock.fetch:out:" + peerId + ":" + System.currentTimeMillis();
                    balanceManager.charge(chargeKey, peerId, egressFee, "dock.fetch:out");
                }
            }
            
            log.debug("DOCK FETCH: peerId={}, recipientIds={}, got={}, total={}, egressBytes={}",
                    peerId, recipientIds, results.size(), page.getTotal(), totalEgressBytes);
            
            FapiResponse resp = successResponse(requestId, results, (long) results.size(), page.getTotal());
            if (page.getLast() != null) {
                resp.setLast(page.getLast());
            }
            return resp;
            
        } catch (FcdslException e) {
            return queryRejected(requestId, e);
        } catch (Exception e) {
            log.error("Failed to handle FETCH request: {}", e.getMessage(), e);
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Fetch failed: " + e.getMessage());
        }
    }
    
    // ==================== CHECK Handler ====================
    
    /**
     * Handle CHECK request - check item status without downloading.
     */
    private FapiResponse handleCheck(FapiRequest request, String peerId) {
        String requestId = request.getId();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> params = parseParams(request.getParams(), Map.class);
        
        if (params == null || !params.containsKey(FieldNames.ID)) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "id parameter is required");
        }
        
        String dockId = params.get(FieldNames.ID).toString();
        
        try {
            DockItem item = store.get(dockId);
            if (item == null) {
                return errorResponse(requestId, FapiCode.NOT_FOUND, "Item not found: " + dockId);
            }
            
            boolean isSender = peerId.equals(item.getSender());
            boolean isRecipient = item.isRecipient(peerId);
            
            if (!isSender && !isRecipient) {
                return errorResponse(requestId, FapiCode.FORBIDDEN, "Access denied");
            }
            
            long currentHeight = balanceManager != null ? balanceManager.getBestHeight() : 0;
            
            long sizeKB = (item.getSize() + 1023) / 1024;
            long egressFee = sizeKB * pricePerKBOut;
            
            Map<String, Object> responseData = new LinkedHashMap<>();
            responseData.put(FieldNames.ID, dockId);
            responseData.put(FieldNames.SENDER, item.getSender());
            responseData.put(FieldNames.SIZE, item.getSize());
            responseData.put(FieldNames.CREATE_TIME, item.getCreateTime());
            responseData.put(FieldNames.CREATE_HEIGHT, item.getCreateHeight());
            responseData.put(FieldNames.EXPIRE_HEIGHT, item.getExpireHeight());
            responseData.put(FieldNames.MAX_DAYS, item.getMaxDays());
            responseData.put(FieldNames.RECIPIENTS, item.getRecipients());
            if (item.getDataType() != null) {
                responseData.put(FieldNames.DATA_TYPE, item.getDataType());
            }
            responseData.put("expired", item.isExpired(currentHeight));
            responseData.put(FieldNames.REMAINING_DAYS, item.getRemainingDays(currentHeight, BLOCKS_PER_DAY));
            responseData.put(FieldNames.EGRESS_FEE, egressFee);
            
            if (isSender) {
                responseData.put(FieldNames.STORAGE_FEE, item.getStorageFee());
                responseData.put(FieldNames.INGRESS_FEE, item.getIngressFee());
            }
            
            return successResponse(requestId, responseData);
            
        } catch (Exception e) {
            log.error("Failed to handle CHECK request: {}", e.getMessage(), e);
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Check failed: " + e.getMessage());
        }
    }
    
    // ==================== DELETE Handler ====================
    
    /**
     * Handle DELETE request - sender removes item (partial refund for unused storage).
     */
    private FapiResponse handleDelete(FapiRequest request, String peerId) {
        String requestId = request.getId();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> params = parseParams(request.getParams(), Map.class);
        
        if (params == null || !params.containsKey(FieldNames.ID)) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "id parameter is required");
        }
        
        String dockId = params.get(FieldNames.ID).toString();
        
        try {
            DockItem item = store.get(dockId);
            if (item == null) {
                return errorResponse(requestId, FapiCode.NOT_FOUND, "Item not found: " + dockId);
            }
            
            if (!peerId.equals(item.getSender())) {
                return errorResponse(requestId, FapiCode.FORBIDDEN, "Only sender can delete");
            }
            
            // Calculate refund for unused storage
            long refund = 0;
            if (balanceManager != null && item.getStorageFee() != null && item.getStorageFee() > 0) {
                long currentHeight = balanceManager.getBestHeight();
                int remainingDays = item.getRemainingDays(currentHeight, BLOCKS_PER_DAY);
                if (remainingDays > 0 && item.getMaxDays() != null && item.getMaxDays() > 0) {
                    refund = (item.getStorageFee() * remainingDays) / item.getMaxDays();
                }
            }
            
            store.delete(dockId);
            
            if (refund > 0 && balanceManager != null) {
                String refundKey = "dock.delete:refund:" + dockId;
                balanceManager.charge(refundKey, peerId, -refund, "dock.delete:refund");
            }
            
            log.info("DOCK DELETE: sender={}, dockId={}, refund={}", peerId, dockId, refund);
            
            Map<String, Object> responseData = new LinkedHashMap<>();
            responseData.put(FieldNames.ID, dockId);
            responseData.put("deleted", true);
            responseData.put(FieldNames.REFUND, refund);
            
            return successResponse(requestId, responseData);
            
        } catch (Exception e) {
            log.error("Failed to handle DELETE request: {}", e.getMessage(), e);
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Delete failed: " + e.getMessage());
        }
    }
    
    // ==================== EXTEND Handler ====================
    
    /**
     * Handle EXTEND request - extend TTL (sender pays additional storage fee).
     */
    private FapiResponse handleExtend(FapiRequest request, String peerId) {
        String requestId = request.getId();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> params = parseParams(request.getParams(), Map.class);
        
        if (params == null || !params.containsKey(FieldNames.ID)) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "id parameter is required");
        }
        
        String dockId = params.get(FieldNames.ID).toString();
        
        int extraDays = DEFAULT_MAX_DAYS;
        if (params.containsKey(FieldNames.EXTRA_DAYS)) {
            Object extraDaysObj = params.get(FieldNames.EXTRA_DAYS);
            if (extraDaysObj instanceof Number) {
                extraDays = ((Number) extraDaysObj).intValue();
            }
        }
        
        if (extraDays <= 0 || extraDays > MAX_ALLOWED_DAYS) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, 
                "extraDays must be between 1 and " + MAX_ALLOWED_DAYS);
        }
        
        try {
            DockItem item = store.get(dockId);
            if (item == null) {
                return errorResponse(requestId, FapiCode.NOT_FOUND, "Item not found: " + dockId);
            }
            
            if (!peerId.equals(item.getSender())) {
                return errorResponse(requestId, FapiCode.FORBIDDEN, "Only sender can extend");
            }
            
            long sizeKB = (item.getSize() + 1023) / 1024;
            long additionalFee = sizeKB * extraDays * pricePerKBDay;
            
            if (balanceManager != null && !balanceManager.canAfford(peerId, additionalFee)) {
                return errorResponse(requestId, FapiCode.PAYMENT_REQUIRED, 
                    "Insufficient balance. Required: " + additionalFee + " satoshi");
            }
            
            long newExpireHeight = item.getExpireHeight() + (long) extraDays * BLOCKS_PER_DAY;
            item.setExpireHeight(newExpireHeight);
            item.setMaxDays((item.getMaxDays() != null ? item.getMaxDays() : 0) + extraDays);
            item.setStorageFee((item.getStorageFee() != null ? item.getStorageFee() : 0) + additionalFee);
            
            store.put(item);
            
            if (balanceManager != null && additionalFee > 0) {
                String chargeKey = "dock.extend:storage:" + dockId + ":" + System.currentTimeMillis();
                balanceManager.charge(chargeKey, peerId, additionalFee, "dock.extend:storage");
            }
            
            log.info("DOCK EXTEND: sender={}, dockId={}, extraDays={}, additionalFee={}, newExpireHeight={}",
                    peerId, dockId, extraDays, additionalFee, newExpireHeight);
            
            Map<String, Object> responseData = new LinkedHashMap<>();
            responseData.put(FieldNames.ID, dockId);
            responseData.put(FieldNames.EXTRA_DAYS, extraDays);
            responseData.put("newExpireHeight", newExpireHeight);
            responseData.put("additionalFee", additionalFee);
            responseData.put("totalStorageFee", item.getStorageFee());
            
            return successResponse(requestId, responseData);
            
        } catch (Exception e) {
            log.error("Failed to handle EXTEND request: {}", e.getMessage(), e);
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Extend failed: " + e.getMessage());
        }
    }
    
    // ==================== Cleanup ====================
    
    /**
     * Cleanup expired items (called periodically by DockCleanupTask).
     *
     * @param currentHeight current blockchain height
     * @return number of deleted items
     */
    public long cleanupExpiredItems(long currentHeight) {
        try {
            long deleted = store.deleteExpired(currentHeight);
            if (deleted > 0) {
                log.info("Cleaned up {} expired dock items at height {}", deleted, currentHeight);
            }
            return deleted;
        } catch (Exception e) {
            log.error("Failed to cleanup expired items: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    // ==================== Getters ====================
    
    public DockStore getStore() {
        return store;
    }
    
    public long getMaxDataSize() {
        return maxDataSize;
    }
    
    public long getPricePerKBIn() {
        return pricePerKBIn;
    }
    
    public long getPricePerKBOut() {
        return pricePerKBOut;
    }
    
    public long getPricePerKBDay() {
        return pricePerKBDay;
    }
    
    public int getDefaultMaxDays() {
        return defaultMaxDays;
    }
}
