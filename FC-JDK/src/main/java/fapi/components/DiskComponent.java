package fapi.components;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import data.apipData.Fcdsl;
import data.apipData.Sort;
import data.fcData.DiskItem;
import data.feipData.ServiceType;
import data.feipData.serviceParams.DiskParams;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fapi.AbstractFapiComponent;
import fapi.FapiCode;
import fapi.components.disk.DiskProtocol;
import fapi.components.disk.DiskSyncManager;
import fapi.components.disk.DiskSyncSource;
import fapi.components.disk.FapiDiskHandler;
import fapi.message.FapiRequest;
import fapi.message.FapiResponse;
import fapi.message.UnifiedCodec.UnifiedResponse;
import fapi.query.FcdslQueryExecutor;
import fapi.query.QueryResult;
import config.Settings;
import utils.JsonUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static constants.FieldNames.DISK_DIR;
import static constants.FieldNames.SINCE;
import static constants.FieldNames.ID;
import static constants.Strings.DATA;
import static constants.Values.DISK;

/**
 * DISK Component - Provides decentralized file storage via FAPI.
 * 
 * Supports two protocol modes:
 * 1. Binary protocol for PUT/CARVE/GET (efficient, no Base64 overhead)
 * 2. JSON protocol for CHECK/LIST (standard FapiRequest/FapiResponse)
 * 
 * API Methods:
 * - disk.put    - Store file with expiration (binary)
 * - disk.carve  - Store file permanently (binary)
 * - disk.get    - Retrieve file by ID (binary)
 * - disk.check  - Check file existence and metadata for one or more IDs (JSON)
 * - disk.list   - Query stored files with FCDSL (JSON)
 */
public class DiskComponent extends AbstractFapiComponent {
    
    /** Maximum number of IDs allowed in a single check request */
    public static final int MAX_CHECK_IDS = 200;
    
    private FapiDiskHandler diskHandler;
    private FcdslQueryExecutor queryExecutor;
    private ElasticsearchClient esClient;
    private String indexName;
    private long defaultDataLifeDays = 30;
    private long maxDataSize = DiskSyncManager.DEFAULT_MAX_DATA_SIZE;
    private long maxTotalDiskUsage = DiskSyncManager.DEFAULT_MAX_TOTAL_DISK_USAGE;
    private DiskSyncManager diskSyncManager;
    
    @Override
    public String getName() {
        return "DISK";
    }
    
    @Override
    public List<String> getApiList() {
        return List.of(
            "disk.put",     // Store file with expiration (binary protocol)
            "disk.carve",   // Store file permanently (binary protocol)
            "disk.get",     // Retrieve file by ID (binary protocol)
            "disk.check",   // Check file existence and metadata (accepts ID list, max 200)
            "disk.list"     // Query stored files with FCDSL
        );
    }
    
    @Override
    protected void doInitialize() {
        // Get Elasticsearch client
        this.esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);
        if (esClient == null) {
            throw new IllegalStateException("ElasticsearchClient is required for DISK component");
        }
        
        // Build index name: {sid}_data
        String sid = settings.getSid();
        this.indexName = Settings.addSidBriefToName(sid, DISK);
        
        // Initialize query executor for list operations
        this.queryExecutor = new FcdslQueryExecutor(esClient);
        
        // Get storage directory from settings
        Path storageRoot = getStorageRoot();
        
        // Create disk handler
        this.diskHandler = new FapiDiskHandler(storageRoot, esClient, indexName);
        
        // Read default data life days from service params
        if (settings.getService() != null && settings.getService().getParams() != null) {
            DiskParams params = DiskParams.fromObject(settings.getService().getParams());
            if (params != null && params.getDataLifeDays() != null) {
                try {
                    defaultDataLifeDays = Long.parseLong(params.getDataLifeDays());
                } catch (NumberFormatException e) {
                    log.warn("Invalid dataLifeDays in service params, using default: {}", defaultDataLifeDays);
                }
            }
        }
        
        // Read maxDataSize from service params or settingMap
        if (settings.getService() != null && settings.getService().getParams() != null) {
            DiskParams params = DiskParams.fromObject(settings.getService().getParams());
            if (params != null && params.getMaxDataSize() != null) {
                try {
                    maxDataSize = Long.parseLong(params.getMaxDataSize());
                } catch (NumberFormatException e) {
                    log.warn("Invalid maxDataSize in service params, using default: {}", maxDataSize);
                }
            }
        }

        Map<String, Object> settingMap = settings.getSettingMap();
        if (settingMap != null) {
            Object v = settingMap.get(DiskSyncManager.KEY_MAX_DATA_SIZE);
            if (v instanceof Number) maxDataSize = ((Number) v).longValue();

            v = settingMap.get(DiskSyncManager.KEY_MAX_TOTAL_DISK_USAGE);
            if (v instanceof Number) maxTotalDiskUsage = ((Number) v).longValue();
        }

        // Check if index exists
        checkOrCreateIndex();

        // Initialize DISK sync manager if sources are configured
        initSyncManager(settingMap);
        
        log.info("DISK component initialized: storageRoot={}, index={}, dataLifeDays={}, maxDataSize={}", 
                storageRoot, indexName, defaultDataLifeDays, maxDataSize);
    }

    /**
     * Initialize the DiskSyncManager if sync sources are configured.
     */
    private void initSyncManager(Map<String, Object> settingMap) {
        if (settingMap == null || server == null) return;

        Object srcObj = settingMap.get(DiskSyncManager.KEY_DISK_SYNC_SOURCES);
        if (srcObj == null) return;

        List<DiskSyncSource> sources;
        try {
            Gson gson = new Gson();
            String json = gson.toJson(srcObj);
            sources = gson.fromJson(json, new TypeToken<List<DiskSyncSource>>() {}.getType());
        } catch (Exception e) {
            log.warn("Failed to parse diskSyncSources: {}", e.getMessage());
            return;
        }
        if (sources == null || sources.isEmpty()) return;

        long minDealerBalance = DiskSyncManager.DEFAULT_MIN_DEALER_BALANCE;
        long syncIntervalHours = DiskSyncManager.DEFAULT_SYNC_INTERVAL_HOURS;
        Object v = settingMap.get(DiskSyncManager.KEY_MIN_DEALER_BALANCE);
        if (v instanceof Number) minDealerBalance = ((Number) v).longValue();
        v = settingMap.get(DiskSyncManager.KEY_DISK_SYNC_INTERVAL_HOURS);
        if (v instanceof Number) syncIntervalHours = ((Number) v).longValue();

        String dbDir = settings.getDbDir();
        diskSyncManager = new DiskSyncManager(
                server, diskHandler, sources,
                maxDataSize, maxTotalDiskUsage, minDealerBalance,
                defaultDataLifeDays, dbDir, syncIntervalHours);
        diskSyncManager.start();
    }
    
    /**
     * Check if the Elasticsearch index exists, create if not.
     */
    private void checkOrCreateIndex() {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(indexName)).value();
            if (!exists) {
                esClient.indices().create(c -> c
                        .index(indexName)
                        .withJson(new java.io.StringReader(DiskItem.MAPPINGS)));
                log.info("Created DISK index: {}", indexName);
            }
        } catch (Exception e) {
            log.error("Failed to check/create index {}: {}", indexName, e.getMessage(), e);
        }
    }
    
    /**
     * Get storage root directory from settings.
     */
    private Path getStorageRoot() {
        Map<String, Object> settingMap = settings.getSettingMap();
        if (settingMap != null && settingMap.containsKey(DISK_DIR)) {
            return Paths.get(settingMap.get(DISK_DIR).toString());
        }
        
        // Default: use main data directory + DISK subdirectory
        String dbDir = settings.getDbDir();
        if (dbDir != null) {
            return Paths.get(dbDir, "disk");
        }
        
        // Fallback: user home + .fapi/disk
        return Paths.get(System.getProperty("user.home"), ".fapi", "disk");
    }
    
    @Override
    public FapiResponse handleRequest(FapiRequest request, String peerId) {
        String method = request.getMethodName();
        String requestId = request.getId();
        
        if (method == null) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "Method name is missing");
        }
        
        return switch (method) {
            case "check" -> handleCheck(request);
            case "list" -> handleList(request);
            // GET is handled via handleBinaryRequest(FapiRequest, peerId) for binary response
            case "get" -> errorResponse(requestId, FapiCode.BAD_REQUEST, 
                    "Use binary response handler for GET");
            // PUT and CARVE use binary protocol for both request and response
            case "put", "carve" -> errorResponse(requestId, FapiCode.BAD_REQUEST, 
                    "PUT/CARVE must use binary protocol");
            default -> errorResponse(requestId, FapiCode.METHOD_NOT_ALLOWED, "Unknown method: " + method);
        };
    }
    
    @Override
    public boolean returnsBinaryResponse(String method) {
        // GET returns binary response (file content)
        return "get".equals(method);
    }
    
    @Override
    public boolean returnsBinaryData(String method) {
        // GET returns binary data in unified response
        return "get".equals(method);
    }
    
    /**
     * 处理统一格式请求（支持二进制数据输入和输出）
     */
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
            case "put" -> handleUnifiedPut(request, binaryData, false);
            case "carve" -> handleUnifiedPut(request, binaryData, true);
            case "get" -> handleUnifiedGet(request);
            case "check" -> new UnifiedResponse(handleCheck(request), null);
            case "list" -> new UnifiedResponse(handleList(request), null);
            default -> new UnifiedResponse(
                errorResponse(requestId, FapiCode.METHOD_NOT_ALLOWED, "Unknown method: " + method), 
                null);
        };
    }
    
    /**
     * 处理统一格式的 PUT/CARVE 请求
     * Uses streaming store to avoid loading file content into additional buffers.
     */
    private UnifiedResponse handleUnifiedPut(FapiRequest request, byte[] binaryData, boolean permanent) {
        String requestId = request.getId();
        
        if (binaryData == null || binaryData.length == 0) {
            return new UnifiedResponse(
                errorResponse(requestId, FapiCode.BAD_REQUEST, "File content is required"), 
                null);
        }

        if (binaryData.length > maxDataSize) {
            return new UnifiedResponse(
                errorResponse(requestId, FapiCode.BAD_REQUEST,
                    "File size " + binaryData.length + " exceeds maxDataSize " + maxDataSize),
                null);
        }
        
        try {
            // Parse metadata from params
            long dataLifeDays = defaultDataLifeDays;
            @SuppressWarnings("unchecked")
            Map<String, Object> params = parseParams(request.getParams(), Map.class);
            if (params != null && params.containsKey("dataLifeDays")) {
                Object days = params.get("dataLifeDays");
                if (days instanceof Number) {
                    dataLifeDays = ((Number) days).longValue();
                }
            }
            
            // Store using streaming path (wraps byte[] in ByteArrayInputStream,
            // computes hash incrementally and writes to disk in single pass)
            DiskItem diskItem = diskHandler.storeFromBytes(binaryData, permanent, dataLifeDays);
            
            // Return success response with metadata
            return new UnifiedResponse(successResponse(requestId, diskItem), null);
            
        } catch (Exception e) {
            log.error("Failed to handle PUT request: {}", e.getMessage(), e);
            return new UnifiedResponse(
                errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Failed to store: " + e.getMessage()), 
                null);
        }
    }
    
    /**
     * 处理统一格式的 GET 请求
     * Uses streaming retrieve: sets streamSourcePath on the response instead of
     * loading the entire file into a byte[] array. FapiServer will stream the file
     * content directly through the FUDP transport.
     */
    private UnifiedResponse handleUnifiedGet(FapiRequest request) {
        String requestId = request.getId();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> params = parseParams(request.getParams(), Map.class);
        
        if (params == null || !params.containsKey(ID)) {
            return new UnifiedResponse(
                errorResponse(requestId, FapiCode.BAD_REQUEST, "id parameter is required"), 
                null);
        }
        
        String did = params.get(ID).toString();
        
        // Get file path without loading content into memory
        Path filePath = diskHandler.getFilePath(did);
        
        if (filePath == null) {
            return new UnifiedResponse(
                errorResponse(requestId, FapiCode.NOT_FOUND, "File not found: " + did), 
                null);
        }
        
        // Get file size without loading content
        long fileSize = diskHandler.getFileSize(did);
        if (fileSize <= 0) {
            return new UnifiedResponse(
                errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Failed to read file size: " + did), 
                null);
        }
        
        // Get metadata and extend expire (accessing data keeps it alive)
        DiskItem metadata = diskHandler.getMetadata(did);
        if (metadata != null) {
            metadata = diskHandler.extendExpire(metadata, false, defaultDataLifeDays);
        }
        
        // Build response with streaming source (file path) instead of byte[]
        FapiResponse response = successResponse(requestId, metadata);
        response.setDataSize(fileSize);
        response.setStreamSourcePath(filePath);
        response.setStreamSourceSize(fileSize);
        
        // Return with null binaryData — FapiServer will detect streamSourcePath and stream
        return new UnifiedResponse(response, null);
    }
    
    @Override
    @Deprecated
    public byte[] handleBinaryRequest(FapiRequest request, String peerId) {
        String method = request.getMethodName();
        
        if (!"get".equals(method)) {
            return createErrorResponse("Method " + method + " does not support binary response via JSON request");
        }
        
        // Handle GET with JSON request, binary response
        return handleJsonGet(request);
    }
    
    /**
     * Handle GET request from JSON FapiRequest, return binary response.
     * Request format: {"api": "disk.get", "params": {"did": "xxx"}}
     * Response format: Binary (status + metadata + content)
     */
    private byte[] handleJsonGet(FapiRequest request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = parseParams(request.getParams(), Map.class);
        
        if (params == null || !params.containsKey(ID)) {
            return DiskProtocol.encodeGetResponse(
                    DiskProtocol.STATUS_INVALID_REQUEST, 
                    "{\"error\":\"id parameter is required\"}", 
                    null);
        }
        
        String did = params.get(ID).toString();
        
        // Retrieve file content
        byte[] content = diskHandler.retrieve(did);
        
        if (content == null) {
            return DiskProtocol.encodeGetResponse(
                    DiskProtocol.STATUS_NOT_FOUND, 
                    "{\"error\":\"File not found\"}", 
                    null);
        }
        
        // Get metadata and extend expire (accessing data keeps it alive)
        DiskItem metadata = diskHandler.getMetadata(did);
        if (metadata != null) {
            metadata = diskHandler.extendExpire(metadata, false, defaultDataLifeDays);
        }
        String metadataJson = metadata != null ? JsonUtils.toJson(metadata) : "{}";
        
        // Return binary response with content
        return DiskProtocol.encodeGetResponse(
                DiskProtocol.STATUS_SUCCESS, 
                metadataJson, 
                content);
    }
    
    /**
     * Handle binary protocol requests (PUT, CARVE).
     * Called directly from FapiServer when service name is "DISK".
     * 
     * @param data Raw binary request data
     * @param peerId Requesting peer's FID
     * @return Binary response data
     * @deprecated 使用统一协议 handleUnifiedRequest 替代
     */
    @Deprecated
    public byte[] handleBinaryProtocolRequest(byte[] data, String peerId) {
        if (data == null || data.length < 1) {
            return createErrorResponse("Invalid request: empty data");
        }
        
        byte operation = DiskProtocol.getOperation(data);
        
        return switch (operation) {
            case DiskProtocol.OP_PUT -> handleBinaryPut(data, false);
            case DiskProtocol.OP_CARVE -> handleBinaryPut(data, true);
            case DiskProtocol.OP_GET -> handleBinaryGet(data);
            default -> createErrorResponse("Unknown operation: " + operation);
        };
    }
    
    /**
     * Handle binary PUT/CARVE request.
     * Uses streaming store to compute hash incrementally.
     */
    private byte[] handleBinaryPut(byte[] data, boolean permanent) {
        try {
            DiskProtocol.PutRequest request = DiskProtocol.decodePutRequest(data);
            
            if (request.content() == null || request.content().length == 0) {
                return createErrorResponse("Content is empty");
            }
            
            // Determine data life days
            long dataLifeDays = defaultDataLifeDays;
            if (request.metadata() != null && !request.metadata().isEmpty()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> metaMap = JsonUtils.fromJson(request.metadata(), Map.class);
                    if (metaMap != null && metaMap.containsKey("dataLifeDays")) {
                        Object days = metaMap.get("dataLifeDays");
                        if (days instanceof Number) {
                            dataLifeDays = ((Number) days).longValue();
                        }
                    }
                } catch (Exception e) {
                    // Ignore metadata parsing errors
                }
            }
            
            // Store using streaming path (computes hash incrementally)
            DiskItem diskItem = diskHandler.storeFromBytes(request.content(), permanent, dataLifeDays);
            
            // Return JSON response with metadata
            String responseJson = JsonUtils.toJson(diskItem);
            return responseJson.getBytes();
            
        } catch (Exception e) {
            log.error("Failed to handle PUT request: {}", e.getMessage(), e);
            return createErrorResponse("Failed to store: " + e.getMessage());
        }
    }
    
    /**
     * Handle binary GET request.
     * Note: Legacy binary GET still loads file into memory for backward compatibility.
     * New unified protocol uses streaming via handleUnifiedGet + FapiServer streaming.
     */
    private byte[] handleBinaryGet(byte[] data) {
        try {
            String did = DiskProtocol.decodeGetRequest(data);
            
            // Retrieve file content (legacy path — still uses byte[] for old binary protocol)
            byte[] content = diskHandler.retrieve(did);
            
            if (content == null) {
                // Not found
                return DiskProtocol.encodeGetResponse(
                        DiskProtocol.STATUS_NOT_FOUND, 
                        "{\"error\":\"File not found\"}", 
                        null);
            }
            
            // Get metadata and extend expire (accessing data keeps it alive)
            DiskItem metadata = diskHandler.getMetadata(did);
            if (metadata != null) {
                metadata = diskHandler.extendExpire(metadata, false, defaultDataLifeDays);
            }
            String metadataJson = metadata != null ? JsonUtils.toJson(metadata) : "{}";
            
            // Return binary response with content
            return DiskProtocol.encodeGetResponse(
                    DiskProtocol.STATUS_SUCCESS, 
                    metadataJson, 
                    content);
            
        } catch (Exception e) {
            log.error("Failed to handle GET request: {}", e.getMessage(), e);
            return DiskProtocol.encodeGetResponse(
                    DiskProtocol.STATUS_ERROR, 
                    "{\"error\":\"" + e.getMessage() + "\"}", 
                    null);
        }
    }
    
    /**
     * Create error response as JSON bytes.
     */
    private byte[] createErrorResponse(String message) {
        return ("{\"code\":400,\"message\":\"" + message + "\"}").getBytes();
    }
    
    /**
     * Handle CHECK request (JSON protocol).
     * <p>
     * Accepts a list of IDs (via fcdsl.ids or params.dids).
     * A single ID is just a list of size 1.
     * <p>
     * Response data is a {@code Map<did, DiskItem>} where:
     * - key = ID string
     * - value = DiskItem metadata if the file exists, or null if not found
     * <p>
     * If a file exists, the expire time is also extended based on request params:
     * - If params contain "permanent": true → upgrade to permanent
     * - If params contain "dataLifeDays" → extend expire by that many days
     * - Otherwise → extend expire by defaultDataLifeDays
     * If already permanent, expire stays unchanged.
     */
    private FapiResponse handleCheck(FapiRequest request) {
        String requestId = request.getId();
        
        // Collect IDs from fcdsl.ids (preferred) or params.dids or params.did (backward compatible)
        List<String> dids = null;
        
        // 1. Try fcdsl.ids
        if (request.getFcdsl() != null && request.getFcdsl().getIds() != null && !request.getFcdsl().getIds().isEmpty()) {
            dids = request.getFcdsl().getIds();
        }
        
        // 2. Try params.dids (list) or params.did (single, backward compatible)
        if (dids == null || dids.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = parseParams(request.getParams(), Map.class);
            if (params != null) {
                Object didsObj = params.get("dids");
                if (didsObj instanceof List<?> didList) {
                    dids = new ArrayList<>();
                    for (Object item : didList) {
                        if (item != null) dids.add(item.toString());
                    }
                }
                // Backward compatible: single "did" param
                if ((dids == null || dids.isEmpty()) && params.containsKey(ID)) {
                    dids = List.of(params.get(ID).toString());
                }
            }
        }
        
        if (dids == null || dids.isEmpty()) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, 
                "ID list is required (via fcdsl.ids, params.dids, or params.did)");
        }
        
        // Enforce upper limit
        if (dids.size() > MAX_CHECK_IDS) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, 
                "Too many IDs, max: " + MAX_CHECK_IDS + ", got: " + dids.size());
        }
        
        // Parse common params for expire extension
        @SuppressWarnings("unchecked")
        Map<String, Object> params = parseParams(request.getParams(), Map.class);
        boolean permanent = params != null && Boolean.TRUE.equals(params.get("permanent"));
        long dataLifeDays = defaultDataLifeDays;
        if (params != null) {
            Object daysObj = params.get("dataLifeDays");
            if (daysObj instanceof Number) {
                dataLifeDays = ((Number) daysObj).longValue();
            }
        }
        
        // Check each ID and build results: Map<did, DiskItem or null>
        Map<String, DiskItem> resultMap = new java.util.LinkedHashMap<>();
        for (String did : dids) {
            FapiDiskHandler.DiskCheckResult result = diskHandler.check(did);
            
            if (!result.exists() || result.metadata() == null) {
                // File not found — null value
                resultMap.put(did, null);
                continue;
            }
            
            // Extend expire for existing files
            DiskItem metadata = diskHandler.extendExpire(result.metadata(), permanent, dataLifeDays);
            resultMap.put(did, metadata);
        }
        
        return successResponse(requestId, resultMap);
    }
    
    /**
     * Handle LIST request (JSON protocol with FCDSL).
     */
    private FapiResponse handleList(FapiRequest request) {
        String requestId = request.getId();
        Fcdsl fcdsl = request.getFcdsl();
        
        if (fcdsl == null) {
            fcdsl = new Fcdsl();
        }
        
        // Set default sort if not specified. Sort only by "since" (numeric) to avoid
        // "all shards failed" when index was auto-created with dynamic mapping (did -> text).
        if (fcdsl.getSort() == null || fcdsl.getSort().isEmpty()) {
            ArrayList<Sort> defaultSort = Sort.makeSortList(SINCE, false, null, null, null, null);
            fcdsl.setSort(defaultSort);
        }
        
        try {
            if (!indexExists()) {
                checkOrCreateIndex();
                return successResponse(requestId, List.of(), 0L, 0L);
            }
            
            QueryResult<DiskItem> result = queryExecutor.executeQuery(indexName, DiskItem.class, fcdsl, null);
            
            if (result == null || result.isEmpty()) {
                return successResponse(requestId, List.of(), 0L, 0L);
            }
            
            FapiResponse response = successResponse(requestId, result.getData());
            response.setGot(result.getGot());
            response.setTotal(result.getTotal());
            response.setLast(result.getLast());
            return response;
            
        } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException e) {
            String cause = e.getMessage();
            if (e.response() != null && e.response().error() != null) {
                cause = e.response().error().reason();
            }
            log.error("disk.list failed (index={}): {}", indexName, cause, e);
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Query failed: " + cause);
        } catch (Exception e) {
            log.error("disk.list failed: {}", e.getMessage(), e);
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Query failed: " + e.getMessage());
        }
    }
    
    /**
     * Check if the DISK Elasticsearch index exists.
     */
    private boolean indexExists() {
        try {
            return esClient.indices().exists(e -> e.index(indexName)).value();
        } catch (Exception e) {
            log.warn("Failed to check index existence: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if given data is a binary disk protocol request.
     */
    public static boolean isBinaryDiskRequest(byte[] data) {
        return DiskProtocol.isDiskProtocol(data);
    }
    
    @Override
    protected void doClose(long timeoutMs) throws InterruptedException {
        if (diskSyncManager != null) {
            diskSyncManager.stop();
        }
    }

    // ==================== Getters ====================
    
    public FapiDiskHandler getDiskHandler() {
        return diskHandler;
    }
    
    public long getDefaultDataLifeDays() {
        return defaultDataLifeDays;
    }

    public long getMaxDataSize() {
        return maxDataSize;
    }

    public long getMaxTotalDiskUsage() {
        return maxTotalDiskUsage;
    }

    public DiskSyncManager getDiskSyncManager() {
        return diskSyncManager;
    }
}
