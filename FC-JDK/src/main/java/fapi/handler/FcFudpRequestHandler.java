package fapi.handler;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TrackHits;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.json.JsonData;
import clients.NaSaClient.NaSaRpcClient;
import constants.CodeMessage;
import constants.Constants;
import config.Settings;
import data.apipData.Equals;
import data.apipData.Except;
import data.apipData.Fcdsl;
import data.apipData.FcQuery;
import data.apipData.Filter;
import data.apipData.Match;
import data.apipData.Part;
import data.apipData.Range;
import data.apipData.Sort;
import data.apipData.Terms;
import data.fcData.FcEntity;
import data.fchData.Block;
import data.fchData.Cash;
import data.fchData.FchChainInfo;
import data.feipData.Service;
import fapi.util.ResponseBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static constants.FieldNames.*;

/**
 * FUDP 请求处理器
 * 处理 FAPI 查询请求，复用 FcHttpRequestHandler 的查询逻辑
 */
public class FcFudpRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(FcFudpRequestHandler.class);
    
    private final ElasticsearchClient esClient;
    private final Settings settings;
    
    // Entity 类型到索引的映射
    private static final Map<String, IndexMapping> ENTITY_INDEX_MAP = createEntityIndexMap();
    
    // 索引名称到实体类的映射（支持自定义索引）
    private final Map<String, IndexMapping> indexNameMapping = new ConcurrentHashMap<>();
    
    private static Map<String, IndexMapping> createEntityIndexMap() {
        Map<String, IndexMapping> map = new HashMap<>();
        FcEntity.entityClassMap.forEach((indexName, clazz) -> {
            map.put(indexName, new IndexMapping(indexName, clazz, "id"));
        });
        return Collections.unmodifiableMap(map);
    }
    
    /**
     * 索引映射信息
     */
    static class IndexMapping {
        String indexName;
        Class<?> entityClass;
        String idFieldName;
        
        IndexMapping(String indexName, Class<?> entityClass, String idFieldName) {
            this.indexName = indexName;
            this.entityClass = entityClass;
            this.idFieldName = idFieldName;
        }
    }
    
    public FcFudpRequestHandler(Settings settings) {
        this.settings = settings;
        this.esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        if (esClient == null) {
            throw new IllegalStateException("ElasticsearchClient is required for FAPI");
        }
        initializeIndexMappings();
    }
    
    /**
     * 初始化索引映射
     */
    private void initializeIndexMappings() {
        // 从 ENTITY_INDEX_MAP 初始化索引名称映射
        for (IndexMapping mapping : ENTITY_INDEX_MAP.values()) {
            indexNameMapping.put(mapping.indexName, mapping);
        }
    }
    
    /**
     * 处理请求
     * @param service Service对象
     * @param peerId 请求来源的 Peer ID
     * @param data FCDSL JSON bytes
     * @return 响应数据（byte[]）
     */
    public byte[] handleRequest(Service service, String peerId, byte[] data) {
        try {
            // 1. 解析 FCDSL JSON
            Fcdsl fcdsl = parseFcdslFromBytes(data);
            if (fcdsl.isBadFcdsl()) {
                return ResponseBuilder.buildErrorResponse(
                    CodeMessage.Code1013BadRequest, 
                    "Invalid FCDSL", 
                    settings
                );
            }
            
            // 2. 检查是否有特殊 endpoint 请求（如 "totals"）
            if (fcdsl.getEndpoint() != null && !fcdsl.getEndpoint().isEmpty()) {
                return handleEndpointRequest(fcdsl, peerId);
            }
            
            // 3. 确定目标索引和实体类型
            IndexMapping mapping = determineIndexMapping(fcdsl);
            if (mapping == null) {
                return ResponseBuilder.buildErrorResponse(
                    CodeMessage.Code1012BadQuery,
                    "Cannot determine index: both index and entity are missing or invalid",
                    settings
                );
            }
            
            // 3. 执行查询
            if (fcdsl.getIds() != null) {
                // IDs 查询
                // Log blockByIds request
                boolean isBlockByIds = mapping.entityClass == Block.class;
                if (isBlockByIds) {
                    log.info("[LOG] blockByIds() Request - Index: {}, IDs: {}", mapping.indexName, fcdsl.getIds());
                }
                
                List<?> result = doIdsRequest(mapping.indexName, mapping.entityClass, fcdsl.getIds());
                if (result == null) {
                    if (isBlockByIds) {
                        log.info("[LOG] blockByIds() Response - No blocks found for the given IDs");
                    }
                    return ResponseBuilder.buildErrorResponse(
                        CodeMessage.Code1011DataNotFound,
                        "No data found for the given IDs",
                        settings
                    );
                }
                
                // Log blockByIds response
                if (isBlockByIds) {
                    log.info("[LOG] blockByIds() Response - Found {} blocks", result.size());
                }
                
                return ResponseBuilder.buildSuccessResponse(
                    result,
                    (long) result.size(),
                    (long) result.size(),
                    null,
                    settings,
                    peerId
                );
            } else {
                // 普通查询
                QueryResult queryResult = doRequest(mapping.indexName, mapping.entityClass, fcdsl, null);
                if (queryResult == null) {
                    return ResponseBuilder.buildErrorResponse(
                        CodeMessage.Code1011DataNotFound,
                        "No data found",
                        settings
                    );
                }
                return ResponseBuilder.buildSuccessResponse(
                    queryResult.data,
                    queryResult.got,
                    queryResult.total,
                    queryResult.last,
                    settings,
                    peerId
                );
            }
        } catch (IllegalArgumentException e) {
            log.error("Invalid request: {}", e.getMessage());
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1013BadRequest,
                e.getMessage(),
                settings,
                peerId
            );
        } catch (Exception e) {
            log.error("Failed to handle FAPI request", e);
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1020OtherError,
                "Internal server error: " + e.getMessage(),
                settings,
                peerId
            );
        }
    }
    
    /**
     * 解析 FCDSL JSON
     */
    private Fcdsl parseFcdslFromBytes(byte[] data) {
        try {
            String jsonStr = new String(data, StandardCharsets.UTF_8);
            Fcdsl fcdsl = JsonUtils.fromJson(jsonStr, Fcdsl.class);
            return fcdsl;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse FCDSL: " + e.getMessage(), e);
        }
    }
    
    /**
     * 确定索引映射
     */
    private IndexMapping determineIndexMapping(Fcdsl fcdsl) {
        // 优先使用 fcdsl.index
        if (fcdsl.getIndex() != null && !fcdsl.getIndex().isEmpty()) {
            String indexName = fcdsl.getIndex();
            IndexMapping mapping = indexNameMapping.get(indexName);
            if (mapping != null) {
                return mapping;
            }
            throw new IllegalArgumentException("Cannot find mapping for index: " + indexName);
        }
        
        // 如果 index 为空，使用 entity 映射
        if (fcdsl.getEntity() != null && !fcdsl.getEntity().isEmpty()) {
            IndexMapping mapping = ENTITY_INDEX_MAP.get(fcdsl.getEntity());
            if (mapping != null) {
                return mapping;
            }
            throw new IllegalArgumentException("Unknown entity type: " + fcdsl.getEntity());
        }
        
        return null;
    }
    
    /**
     * 处理特殊 endpoint 请求（如 "totals"、"chainInfo"）
     */
    private byte[] handleEndpointRequest(Fcdsl fcdsl, String peerId) {
        String endpoint = fcdsl.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1012BadQuery,
                "Endpoint is missing",
                settings,
                peerId
            );
        }
        switch (endpoint.toLowerCase()) {
            case "totals":
                return handleTotalsEndpoint(peerId);
            case "chaininfo":
                return handleChainInfoEndpoint(fcdsl, peerId);
            case "blocktimehistory":
                return handleBlockTimeHistoryEndpoint(fcdsl, peerId);
            case "difficultyhistory":
                return handleDifficultyHistoryEndpoint(fcdsl, peerId);
            case "hashratehistory":
                return handleHashRateHistoryEndpoint(fcdsl, peerId);
            case "balancebyids":
                return handleBalanceByIdsEndpoint(fcdsl, peerId);
            case "broadcasttx":
                return handleBroadcastTxEndpoint(fcdsl, peerId);
            case "decodetx":
                return handleDecodeTxEndpoint(fcdsl, peerId);
            case "estimatefee":
                return handleEstimateFeeEndpoint(fcdsl, peerId);
            case "cashvalid":
                return handleCashValidEndpoint(fcdsl, peerId);
            case "getutxo":
                return handleGetUtxoEndpoint(fcdsl, peerId);
            default:
                return ResponseBuilder.buildErrorResponse(
                    CodeMessage.Code1012BadQuery,
                    "Unknown endpoint: " + endpoint,
                    settings,
                    peerId
                );
        }
    }
    
    /**
     * 处理 totals endpoint - 返回所有索引及其文档数量
     * 类似于 APIP Totals 接口的功能
     * 只返回 entityClassMap 中存在的索引
     */
    private byte[] handleTotalsEndpoint(String peerId) {
        try {
            // 使用 Elasticsearch cat API 获取所有索引信息
            IndicesResponse result = esClient.cat().indices();
            List<IndicesRecord> indicesRecordList = result.valueBody();
            
            // 获取 entityClassMap 的 keyset
            Set<String> entityNames = FcEntity.getEntityNames();
            
            // 只保留在 entityClassMap 中的索引
            Map<String, String> allSumMap = new HashMap<>();
            for (IndicesRecord record : indicesRecordList) {
                String indexName = record.index();
                if (entityNames.contains(indexName)) {
                    allSumMap.put(indexName, record.docsCount());
                }
            }
            
            return ResponseBuilder.buildSuccessResponse(
                allSumMap,
                (long) allSumMap.size(),
                (long) allSumMap.size(),
                null,
                settings,
                peerId
            );
        } catch (Exception e) {
            log.error("Failed to handle totals endpoint", e);
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1020OtherError,
                "Failed to get totals: " + e.getMessage(),
                settings,
                peerId
            );
        }
    }
    
    /**
     * 处理 balanceByIds endpoint - 根据 FID 列表返回余额
     */
    private byte[] handleBalanceByIdsEndpoint(Fcdsl fcdsl, String peerId) {
        List<String> fids = fcdsl.getIds();
        if (fids == null || fids.isEmpty()) {
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1015FidMissed,
                "FID list is missing",
                settings,
                peerId
            );
        }
        
        try {
            Map<String, Long> balanceMap = sumCashValueByOwners(fids);
            return ResponseBuilder.buildSuccessResponse(
                balanceMap,
                (long) balanceMap.size(),
                (long) balanceMap.size(),
                null,
                settings,
                peerId
            );
        } catch (Exception e) {
            log.error("Failed to handle balanceByIds endpoint", e);
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1020OtherError,
                "Failed to get balances: " + e.getMessage(),
                settings,
                peerId
            );
        }
    }
    
    /**
     * 处理 broadcastTx endpoint - 广播交易
     */
    private byte[] handleBroadcastTxEndpoint(Fcdsl fcdsl, String peerId) {
        Map<String, String> other = fcdsl.getOther();
        if (other == null || !other.containsKey("rawTx")) {
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1013BadRequest,
                "rawTx parameter is missing",
                settings,
                peerId
            );
        }
        
        String rawTx = other.get("rawTx");
        try {
            NaSaRpcClient naSaRpcClient = (NaSaRpcClient) settings.getClient(Service.ServiceType.NASA_RPC);
            if (naSaRpcClient == null) {
                return ResponseBuilder.buildErrorResponse(
                    CodeMessage.Code1020OtherError,
                    "NaSa RPC client is not available",
                    settings,
                    peerId
                );
            }
            
            String result = naSaRpcClient.sendRawTransaction(rawTx);
            if (result == null || result.isEmpty()) {
                return ResponseBuilder.buildErrorResponse(
                    CodeMessage.Code1020OtherError,
                    "Failed to broadcast transaction",
                    settings,
                    peerId
                );
            }
            
            // Remove quotes if present
            if (result.startsWith("\"")) result = result.substring(1);
            if (result.endsWith("\"")) result = result.substring(0, result.length() - 1);
            
            if (!utils.Hex.isHexString(result)) {
                return ResponseBuilder.buildErrorResponse(
                    CodeMessage.Code1020OtherError,
                    result,
                    settings,
                    peerId
                );
            }
            
            return ResponseBuilder.buildSuccessResponse(
                result,
                1L,
                1L,
                null,
                settings,
                peerId
            );
        } catch (Exception e) {
            log.error("Failed to broadcast transaction", e);
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1020OtherError,
                "Failed to broadcast: " + e.getMessage(),
                settings,
                peerId
            );
        }
    }
    
    /**
     * 处理 decodeTx endpoint - 解码交易
     */
    private byte[] handleDecodeTxEndpoint(Fcdsl fcdsl, String peerId) {
        Map<String, String> other = fcdsl.getOther();
        if (other == null || !other.containsKey("rawTx")) {
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1013BadRequest,
                "rawTx parameter is missing",
                settings,
                peerId
            );
        }
        
        String rawTx = other.get("rawTx");
        try {
            NaSaRpcClient naSaRpcClient = (NaSaRpcClient) settings.getClient(Service.ServiceType.NASA_RPC);
            if (naSaRpcClient == null) {
                return ResponseBuilder.buildErrorResponse(
                    CodeMessage.Code1020OtherError,
                    "NaSa RPC client is not available",
                    settings,
                    peerId
                );
            }
            
            Object result = naSaRpcClient.decodeRawTransaction(rawTx);
            if (result == null) {
                return ResponseBuilder.buildErrorResponse(
                    CodeMessage.Code1020OtherError,
                    "Failed to decode transaction",
                    settings,
                    peerId
                );
            }
            
            return ResponseBuilder.buildSuccessResponse(
                result,
                1L,
                1L,
                null,
                settings,
                peerId
            );
        } catch (Exception e) {
            log.error("Failed to decode transaction", e);
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1020OtherError,
                "Failed to decode: " + e.getMessage(),
                settings,
                peerId
            );
        }
    }
    
    /**
     * 处理 estimateFee endpoint - 估算手续费
     * 新版本 Bitcoin RPC 的 estimatefee 不再支持参数，使用 estimatesmartfee 代替
     */
    private byte[] handleEstimateFeeEndpoint(Fcdsl fcdsl, String peerId) {
        Map<String, String> other = fcdsl.getOther();
        Integer nBlocks = null;
        
        if (other != null && other.containsKey("nBlocks")) {
            try {
                nBlocks = Integer.parseInt(other.get("nBlocks"));
            } catch (NumberFormatException e) {
                return ResponseBuilder.buildErrorResponse(
                    CodeMessage.Code1013BadRequest,
                    "Invalid nBlocks parameter",
                    settings,
                    peerId
                );
            }
        }
        
        try {
            NaSaRpcClient naSaRpcClient = (NaSaRpcClient) settings.getClient(Service.ServiceType.NASA_RPC);
            if (naSaRpcClient == null) {
                return ResponseBuilder.buildErrorResponse(
                    CodeMessage.Code1020OtherError,
                    "NaSa RPC client is not available",
                    settings,
                    peerId
                );
            }
            
            Double feeRate = null;
            
            // 如果指定了 nBlocks，使用 estimatesmartfee
            if (nBlocks != null && nBlocks > 0) {
                try {
                    NaSaRpcClient.ResultEstimateSmartFee smartFeeResult = naSaRpcClient.estimateSmartFee(nBlocks);
                    if (smartFeeResult != null) {
                        feeRate = smartFeeResult.getFeerate();
                    }
                } catch (Exception e) {
                    log.warn("estimatesmartfee failed, falling back to estimatefee: {}", e.getMessage());
                }
            }
            
            // 如果没有指定 nBlocks 或者 estimatesmartfee 失败，使用无参数的 estimatefee
            if (feeRate == null) {
                clients.NaSaClient.RpcRequest jsonRPC2Request = new clients.NaSaClient.RpcRequest(
                    clients.NaSaClient.NasaRpcNames.ESTIMATE_FEE, 
                    null  // 不传递参数
                );
                
                Object rawResult = clients.NaSaClient.RpcRequest.requestRpc(
                    naSaRpcClient.getUrl(), 
                    naSaRpcClient.getUsername(), 
                    naSaRpcClient.getPassword(), 
                    clients.NaSaClient.NasaRpcNames.ESTIMATE_FEE, 
                    jsonRPC2Request
                );
                
                if (rawResult == null) {
                    return ResponseBuilder.buildErrorResponse(
                        CodeMessage.Code1020OtherError,
                        "Failed to estimate fee",
                        settings,
                        peerId
                    );
                }
                
                // 安全地转换结果为 Double
                if (rawResult instanceof Number) {
                    feeRate = ((Number) rawResult).doubleValue();
                } else if (rawResult instanceof String) {
                    try {
                        feeRate = Double.parseDouble((String) rawResult);
                    } catch (NumberFormatException e) {
                        log.warn("Cannot parse fee rate string: {}", rawResult);
                    }
                }
            }
            
            if (feeRate == null || feeRate < 0) {
                return ResponseBuilder.buildErrorResponse(
                    CodeMessage.Code1020OtherError,
                    "Invalid fee rate returned (may be no data available)",
                    settings,
                    peerId
                );
            }
            
            return ResponseBuilder.buildSuccessResponse(
                feeRate,
                1L,
                1L,
                null,
                settings,
                peerId
            );
        } catch (Exception e) {
            log.error("Failed to estimate fee", e);
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1020OtherError,
                "Failed to estimate fee: " + e.getMessage(),
                settings,
                peerId
            );
        }
    }
    
    /**
     * 处理 cashValid endpoint - 获取有效的 UTXO
     */
    private byte[] handleCashValidEndpoint(Fcdsl fcdsl, String peerId) {
        Map<String, String> other = fcdsl.getOther();
        
        // 如果 other 为空，执行普通的 cashValid 查询
        if (other == null || other.isEmpty()) {
            // 确保 filter 中包含 valid=true 条件
            if (fcdsl.getFilter() == null) {
                fcdsl.addNewFilter().addNewTerms().addNewFields(VALID).addNewValues("true");
            }
            
            // 执行查询
            IndexMapping mapping = ENTITY_INDEX_MAP.get("cash");
            if (mapping == null) {
                return ResponseBuilder.buildErrorResponse(
                    CodeMessage.Code1012BadQuery,
                    "Cash index not found",
                    settings,
                    peerId
                );
            }
            
            QueryResult queryResult = doRequest(mapping.indexName, mapping.entityClass, fcdsl, null);
            if (queryResult == null) {
                return ResponseBuilder.buildErrorResponse(
                    CodeMessage.Code1011DataNotFound,
                    "No valid cashes found",
                    settings,
                    peerId
                );
            }
            
            return ResponseBuilder.buildSuccessResponse(
                queryResult.data,
                queryResult.got,
                queryResult.total,
                queryResult.last,
                settings,
                peerId
            );
        }
        
        // 解析 other 参数
        String fid = other.get("fid");
        String amountStr = other.get("amount");
        String cdStr = other.get("cd");
        String msgSizeStr = other.get("msgSize");
        String outputSizeStr = other.get("outputSize");
        String sinceHeightStr = other.get("sinceHeight");
        
        if (fid == null || fid.isEmpty()) {
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1015FidMissed,
                "fid parameter is required",
                settings,
                peerId
            );
        }
        
        try {
            Long amount = amountStr != null ? utils.FchUtils.coinToSatoshi(Double.parseDouble(amountStr)) : null;
            Long cd = cdStr != null ? Long.parseLong(cdStr) : null;
            int msgSize = msgSizeStr != null ? Integer.parseInt(msgSizeStr) : 0;
            int outputSize = outputSizeStr != null ? Integer.parseInt(outputSizeStr) : 0;
            Long sinceHeight = sinceHeightStr != null ? Long.parseLong(sinceHeightStr) : null;
            
            List<Cash> cashList = getValidCashes(fid, amount, cd, sinceHeight, outputSize, msgSize);
            if (cashList == null || cashList.isEmpty()) {
                return ResponseBuilder.buildErrorResponse(
                    CodeMessage.Code2007CashNoFound,
                    "No valid cashes found",
                    settings,
                    peerId
                );
            }
            
            return ResponseBuilder.buildSuccessResponse(
                cashList,
                (long) cashList.size(),
                (long) cashList.size(),
                null,
                settings,
                peerId
            );
        } catch (NumberFormatException e) {
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1013BadRequest,
                "Invalid parameter format: " + e.getMessage(),
                settings,
                peerId
            );
        } catch (Exception e) {
            log.error("Failed to get valid cashes", e);
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1020OtherError,
                "Failed to get valid cashes: " + e.getMessage(),
                settings,
                peerId
            );
        }
    }
    
    /**
     * 处理 getUtxo endpoint - 获取 UTXO（与 cashValid 类似但返回 Utxo 格式）
     */
    private byte[] handleGetUtxoEndpoint(Fcdsl fcdsl, String peerId) {
        Map<String, String> other = fcdsl.getOther();
        if (other == null || other.isEmpty()) {
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1013BadRequest,
                "Parameters are missing",
                settings,
                peerId
            );
        }
        
        String fid = other.get("address");
        if (fid == null || fid.isEmpty()) {
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1015FidMissed,
                "address parameter is required",
                settings,
                peerId
            );
        }
        
        if (!core.crypto.KeyTools.isGoodFid(fid)) {
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code2003IllegalFid,
                "Invalid FID format",
                settings,
                peerId
            );
        }
        
        try {
            String amountStr = other.get("amount");
            String cdStr = other.get("cd");
            
            Long amount = amountStr != null ? utils.FchUtils.coinStrToSatoshi(amountStr) : 0L;
            Long cd = cdStr != null ? Long.parseLong(cdStr) : 0L;
            
            List<Cash> cashList = getValidCashes(fid, amount, cd, null, 0, 0);
            if (cashList == null || cashList.isEmpty()) {
                return ResponseBuilder.buildErrorResponse(
                    CodeMessage.Code2007CashNoFound,
                    "No UTXOs found",
                    settings,
                    peerId
                );
            }
            
            // 转换为 Utxo 格式
            List<data.apipData.Utxo> utxoList = new ArrayList<>();
            for (Cash cash : cashList) {
                utxoList.add(data.apipData.Utxo.cashToUtxo(cash));
            }
            
            return ResponseBuilder.buildSuccessResponse(
                utxoList,
                (long) utxoList.size(),
                (long) utxoList.size(),
                null,
                settings,
                peerId
            );
        } catch (Exception e) {
            log.error("Failed to get UTXOs", e);
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1020OtherError,
                "Failed to get UTXOs: " + e.getMessage(),
                settings,
                peerId
            );
        }
    }

    /**
     * 处理 chainInfo endpoint，支持在 fcdsl.other 中携带高度参数
     */
    private byte[] handleChainInfoEndpoint(Fcdsl fcdsl, String peerId) {
        Map<String, String> other = fcdsl.getOther();
        String heightStr = other != null ? other.get(HEIGHT) : null;

        try {
            FchChainInfo chainInfo = new FchChainInfo();
            if (heightStr == null || heightStr.isBlank()) {
                NaSaRpcClient naSaRpcClient = (NaSaRpcClient) settings.getClient(Service.ServiceType.NASA_RPC);
                if (naSaRpcClient == null) {
                    return ResponseBuilder.buildErrorResponse(
                        CodeMessage.Code1020OtherError,
                        "NaSa RPC client is not available",
                        settings,
                        peerId
                    );
                }
                chainInfo.infoBest(naSaRpcClient);
            } else {
                long height;
                try {
                    height = Long.parseLong(heightStr);
                } catch (NumberFormatException e) {
                    return ResponseBuilder.buildErrorResponse(
                        CodeMessage.Code1013BadRequest,
                        "Invalid height parameter",
                        settings,
                        peerId
                    );
                }
                ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
                if (esClient == null) {
                    return ResponseBuilder.buildErrorResponse(
                        CodeMessage.Code1020OtherError,
                        "Elasticsearch client is not available",
                        settings,
                        peerId
                    );
                }
                chainInfo.infoByHeight(height, esClient);
            }

            return ResponseBuilder.buildSuccessResponse(
                chainInfo,
                1L,
                1L,
                null,
                settings,
                peerId
            );
        } catch (Exception e) {
            log.error("Failed to handle chainInfo endpoint", e);
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1020OtherError,
                "Failed to process chainInfo endpoint",
                settings,
                peerId
            );
        }
    }

    private byte[] handleBlockTimeHistoryEndpoint(Fcdsl fcdsl, String peerId) {
        HistoryParams params = parseHistoryParams(fcdsl, peerId);
        if (params.errorResponse != null) {
            return params.errorResponse;
        }

        if (params.count > FchChainInfo.MAX_REQUEST_COUNT) {
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1013BadRequest,
                "The count can not be bigger than " + FchChainInfo.MAX_REQUEST_COUNT,
                settings,
                peerId
            );
        }

        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        if (esClient == null) {
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1020OtherError,
                "Elasticsearch client is not available",
                settings,
                peerId
            );
        }

        Map<Long, Long> hist = FchChainInfo.blockTimeHistory(params.startTime, params.endTime, params.count, esClient);
        if (hist == null) {
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1020OtherError,
                "Failed to get the block time history",
                settings,
                peerId
            );
        }

        Long total = null;
        Long bestHeight = settings.getBestHeight();
        if (bestHeight != null && bestHeight > 0) {
            total = bestHeight - 1;
        }

        return ResponseBuilder.buildSuccessResponse(
            hist,
            (long) hist.size(),
            total,
            null,
            settings,
            peerId
        );
    }

    private byte[] handleDifficultyHistoryEndpoint(Fcdsl fcdsl, String peerId) {
        HistoryParams params = parseHistoryParams(fcdsl, peerId);
        if (params.errorResponse != null) {
            return params.errorResponse;
        }

        if (params.count > FchChainInfo.MAX_REQUEST_COUNT) {
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1013BadRequest,
                "The count can not be bigger than " + FchChainInfo.MAX_REQUEST_COUNT,
                settings,
                peerId
            );
        }

        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        if (esClient == null) {
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1020OtherError,
                "Elasticsearch client is not available",
                settings,
                peerId
            );
        }

        Map<Long, String> hist = FchChainInfo.difficultyHistory(params.startTime, params.endTime, params.count, esClient);
        if (hist == null) {
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1020OtherError,
                "Failed to get the difficulty history",
                settings,
                peerId
            );
        }

        Long total = null;
        Long bestHeight = settings.getBestHeight();
        if (bestHeight != null && bestHeight > 0) {
            total = bestHeight - 1;
        }

        return ResponseBuilder.buildSuccessResponse(
            hist,
            (long) hist.size(),
            total,
            null,
            settings,
            peerId
        );
    }

    private byte[] handleHashRateHistoryEndpoint(Fcdsl fcdsl, String peerId) {
        HistoryParams params = parseHistoryParams(fcdsl, peerId);
        if (params.errorResponse != null) {
            return params.errorResponse;
        }

        if (params.count > FchChainInfo.MAX_REQUEST_COUNT) {
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1013BadRequest,
                "The count can not be bigger than " + FchChainInfo.MAX_REQUEST_COUNT,
                settings,
                peerId
            );
        }

        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        if (esClient == null) {
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1020OtherError,
                "Elasticsearch client is not available",
                settings,
                peerId
            );
        }

        Map<Long, String> hist = FchChainInfo.hashRateHistory(params.startTime, params.endTime, params.count, esClient);
        if (hist == null) {
            return ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1020OtherError,
                "Failed to get the hash rate history",
                settings,
                peerId
            );
        }

        Long total = null;
        Long bestHeight = settings.getBestHeight();
        if (bestHeight != null && bestHeight > 0) {
            total = bestHeight - 1;
        }

        return ResponseBuilder.buildSuccessResponse(
            hist,
            (long) hist.size(),
            total,
            null,
            settings,
            peerId
        );
    }

    private HistoryParams parseHistoryParams(Fcdsl fcdsl, String peerId) {
        HistoryParams params = new HistoryParams();
        Map<String, String> other = fcdsl.getOther();
        if (other == null || other.isEmpty()) {
            return params;
        }

        String startTimeStr = other.get(START_TIME);
        String endTimeStr = other.get(END_TIME);
        String countStr = other.get(COUNT);

        try {
            if (startTimeStr != null && !startTimeStr.isBlank()) {
                params.startTime = Long.parseLong(startTimeStr);
            }
            if (endTimeStr != null && !endTimeStr.isBlank()) {
                params.endTime = Long.parseLong(endTimeStr);
            }
            if (countStr != null && !countStr.isBlank()) {
                params.count = Integer.parseInt(countStr);
            }
        } catch (NumberFormatException e) {
            params.errorResponse = ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1013BadRequest,
                "Invalid history parameters",
                settings,
                peerId
            );
        }

        return params;
    }

    private static class HistoryParams {
        long startTime;
        long endTime;
        int count;
        byte[] errorResponse;
    }
    
    /**
     * 执行 IDs 查询
     */
    private <T> List<T> doIdsRequest(String index, Class<T> clazz, List<String> ids) {
        if (ids.size() > Constants.MaxRequestSize) {
            throw new IllegalArgumentException(
                "Maximum IDs count exceeded: " + Constants.MaxRequestSize
            );
        }
        
        try {
            MgetResponse<T> result = esClient.mget(m -> m.index(index).ids(ids), clazz);
            List<MultiGetResponseItem<T>> items = result.docs();
            
            List<T> meetList = new ArrayList<>();
            for (MultiGetResponseItem<T> item : items) {
                if (item.result().found()) {
                    meetList.add(item.result().source());
                }
            }
            
            if (meetList.isEmpty()) {
                return null;
            }
            return meetList;
        } catch (Exception e) {
            log.error("Failed to execute IDs query", e);
            throw new RuntimeException("Query execution failed", e);
        }
    }
    
    /**
     * 查询结果
     */
    static class QueryResult {
        Object data;
        Long got;
        Long total;
        List<String> last;
    }
    
    /**
     * 执行普通查询
     * 复用 FcHttpRequestHandler 的查询逻辑
     */
    private <T> QueryResult doRequest(String index, Class<T> tClass, Fcdsl fcdsl, List<Sort> defaultSortList) {
        if (index == null || tClass == null) {
            return null;
        }
        
        SearchRequest.Builder searchBuilder = new SearchRequest.Builder();
        searchBuilder.index(index);
        
        // 如果没有 query、filter、except，使用 matchAll
        if (fcdsl.getQuery() == null && fcdsl.getExcept() == null && fcdsl.getFilter() == null) {
            MatchAllQuery matchAllQuery = getMatchAllQuery();
            searchBuilder.query(q -> q.matchAll(matchAllQuery));
        } else {
            // 构建查询
            List<Query> queryList = null;
            if (fcdsl.getQuery() != null) {
                FcQuery fcQuery = fcdsl.getQuery();
                queryList = getQueryList(fcQuery);
                if (queryList == null) {
                    return null;
                }
            }
            
            List<Query> filterList = null;
            if (fcdsl.getFilter() != null) {
                Filter fcFilter = fcdsl.getFilter();
                filterList = getQueryList(fcFilter);
                if (filterList == null) {
                    return null;
                }
            }
            
            List<Query> exceptList = null;
            if (fcdsl.getExcept() != null) {
                Except fcExcept = fcdsl.getExcept();
                exceptList = getQueryList(fcExcept);
                if (exceptList == null) {
                    return null;
                }
            }
            
            BoolQuery.Builder bBuilder = QueryBuilders.bool();
            if (queryList != null && !queryList.isEmpty()) {
                bBuilder.must(queryList);
            }
            if (filterList != null && !filterList.isEmpty()) {
                bBuilder.filter(filterList);
            }
            if (exceptList != null && !exceptList.isEmpty()) {
                bBuilder.mustNot(exceptList);
            }
            
            searchBuilder.query(q -> q.bool(bBuilder.build()));
        }
        
        // 处理 size
        int size = 0;
        try {
            if (fcdsl.getSize() != null) {
                size = Integer.parseInt(fcdsl.getSize());
            }
        } catch (Exception e) {
            log.error("Invalid size parameter: {}", fcdsl.getSize());
            return null;
        }
        if (size == 0 || size > Constants.MaxRequestSize) {
            size = Constants.DefaultSize;
        }
        searchBuilder.size(size);
        
        // 处理 sort
        if (fcdsl.getSort() != null) {
            defaultSortList = fcdsl.getSort();
        }
        if (defaultSortList != null && !defaultSortList.isEmpty()) {
            searchBuilder.sort(Sort.getSortList(defaultSortList));
        }
        
        // 处理 after（分页）
        if (fcdsl.getAfter() != null) {
            List<String> after = fcdsl.getAfter();
            searchBuilder.searchAfter(after);
        }
        
        // 处理字段过滤（fields 和 noFields）
        boolean hasFields = fcdsl.getFields() != null && !fcdsl.getFields().isEmpty();
        boolean hasNoFields = fcdsl.getNoFields() != null && !fcdsl.getNoFields().isEmpty();
        
        if (hasFields && hasNoFields) {
            // 同时包含 includes 和 excludes
            searchBuilder.source(s -> s.filter(f -> f
                .includes(fcdsl.getFields())
                .excludes(fcdsl.getNoFields())
            ));
        } else if (hasFields) {
            // 只有 includes
            searchBuilder.source(s -> s.filter(f -> f.includes(fcdsl.getFields())));
        } else if (hasNoFields) {
            // 只有 excludes
            searchBuilder.source(s -> s.filter(f -> f.excludes(fcdsl.getNoFields())));
        }
        
        // 启用 total hits 跟踪
        TrackHits.Builder tb = new TrackHits.Builder();
        tb.enabled(true);
        searchBuilder.trackTotalHits(tb.build());
        
        SearchRequest searchRequest = searchBuilder.build();
        
        // 执行查询
        SearchResponse<T> result;
        try {
            result = esClient.search(searchRequest, tClass);
        } catch (Exception e) {
            log.error("Failed to execute Elasticsearch query", e);
            return null;
        }
        
        if (result == null) {
            return null;
        }
        
        // 处理结果
        QueryResult queryResult = new QueryResult();
        
        // 设置 total
        var totalHits = result.hits().total();
        if (totalHits != null) {
            queryResult.total = totalHits.value();
        }
        
        List<Hit<T>> hitList = result.hits().hits();
        if (hitList.isEmpty()) {
            return null;
        }
        
        // 提取数据
        List<T> tList = new ArrayList<>();
        for (Hit<T> hit : hitList) {
            tList.add(hit.source());
        }
        queryResult.data = tList;
        queryResult.got = (long) tList.size();
        
        // 设置 last（分页游标）
        List<String> sortList = hitList.get(hitList.size() - 1).sort();
        if (sortList != null && !sortList.isEmpty()) {
            queryResult.last = sortList;
        }
        
        return queryResult;
    }
    
    /**
     * 将 FcQuery/Filter/Except 转换为 Query 列表
     */
    private List<Query> getQueryList(FcQuery query) {
        BoolQuery termsQuery;
        BoolQuery partQuery;
        BoolQuery matchQuery;
        BoolQuery rangeQuery;
        BoolQuery existsQuery;
        BoolQuery unexistsQuery;
        BoolQuery equalsQuery;
        BoolQuery unequalsQuery;
        
        List<Query> queryList = new ArrayList<>();
        
        if (query.getTerms() != null) {
            termsQuery = getTermsQuery(query.getTerms());
            Query q = new Query.Builder().bool(termsQuery).build();
            if (q != null) queryList.add(q);
        }
        
        if (query.getPart() != null) {
            partQuery = getPartQuery(query.getPart());
            if (partQuery == null) return null;
            Query q = new Query.Builder().bool(partQuery).build();
            if (q != null) queryList.add(q);
        }
        
        if (query.getMatch() != null) {
            matchQuery = getMatchQuery(query.getMatch());
            Query q = new Query.Builder().bool(matchQuery).build();
            if (q != null) queryList.add(q);
        }
        
        if (query.getExists() != null) {
            existsQuery = getExistsQuery(query.getExists());
            Query q = new Query.Builder().bool(existsQuery).build();
            if (q != null) queryList.add(q);
        }
        
        if (query.getUnexists() != null) {
            unexistsQuery = getUnexistQuery(query.getUnexists());
            Query q = new Query.Builder().bool(unexistsQuery).build();
            if (q != null) queryList.add(q);
        }
        
        if (query.getEquals() != null) {
            equalsQuery = getEqualQuery(query.getEquals());
            if (equalsQuery == null) return null;
            Query q = new Query.Builder().bool(equalsQuery).build();
            if (q != null) queryList.add(q);
        }
        
        if (query.getUnequals() != null) {
            unequalsQuery = getUnequalQuery(query.getUnequals());
            Query q = new Query.Builder().bool(unequalsQuery).build();
            if (q != null) queryList.add(q);
        }
        
        if (query.getRange() != null) {
            rangeQuery = getRangeQuery(query.getRange());
            Query q = new Query.Builder().bool(rangeQuery).build();
            if (q != null) queryList.add(q);
        }
        
        if (queryList.isEmpty()) {
            return null;
        }
        
        return queryList;
    }
    
    /**
     * 将 Filter 转换为 Query 列表（Filter 和 FcQuery 结构相同）
     */
    private List<Query> getQueryList(Filter filter) {
        // Filter 继承自 FcQuery，可以直接转换
        return getQueryList((FcQuery) filter);
    }
    
    /**
     * 将 Except 转换为 Query 列表（Except 和 FcQuery 结构相同）
     */
    private List<Query> getQueryList(Except except) {
        // Except 继承自 FcQuery，可以直接转换
        return getQueryList((FcQuery) except);
    }
    
    /**
     * 构建 Terms 查询
     */
    private BoolQuery getTermsQuery(Terms terms) {
        List<FieldValue> valueList = new ArrayList<>();
        for (String value : terms.getValues()) {
            if (value == null || value.isBlank()) continue;
            // Decode the search value
            String decodedValue = java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
            valueList.add(FieldValue.of(decodedValue));
        }
        
        BoolQuery.Builder termsBoolBuilder = makeBoolShouldTermsQuery(terms.getFields(), valueList);
        termsBoolBuilder.queryName("terms");
        return termsBoolBuilder.build();
    }
    
    /**
     * 构建 Part 查询（通配符查询）
     */
    private BoolQuery getPartQuery(Part part) {
        BoolQuery.Builder partBoolBuilder = new BoolQuery.Builder();
        boolean isCaseInsensitive;
        try {
            isCaseInsensitive = Boolean.parseBoolean(part.getIsCaseInsensitive());
        } catch (Exception e) {
            log.error("Invalid isCaseInsensitive parameter: {}", part.getIsCaseInsensitive());
            return null;
        }
        
        List<Query> queryList = new ArrayList<>();
        for (String field : part.getFields()) {
            if (field == null || field.isBlank()) continue;
            // Decode the search value
            String decodedValue = java.net.URLDecoder.decode(part.getValue(), StandardCharsets.UTF_8);
            WildcardQuery wQuery = WildcardQuery.of(w -> w
                .field(field)
                .caseInsensitive(isCaseInsensitive)
                .value("*" + decodedValue + "*"));
            queryList.add(new Query.Builder().wildcard(wQuery).build());
        }
        partBoolBuilder.should(queryList);
        partBoolBuilder.queryName("part");
        return partBoolBuilder.build();
    }
    
    /**
     * 构建 Match 查询
     */
    private BoolQuery getMatchQuery(Match match) {
        if (match.getValue() == null) return null;
        if (match.getFields() == null || match.getFields().length == 0) return null;
        
        List<Query> queryList = new ArrayList<>();
        for (String field : match.getFields()) {
            if (field == null || field.isBlank()) continue;
            MatchQuery.Builder mBuilder = new MatchQuery.Builder();
            mBuilder.field(field);
            // Decode the search value
            String decodedValue = java.net.URLDecoder.decode(match.getValue(), StandardCharsets.UTF_8);
            mBuilder.query(decodedValue);
            queryList.add(new Query.Builder().match(mBuilder.build()).build());
        }
        
        BoolQuery.Builder bBuilder = new BoolQuery.Builder();
        bBuilder.should(queryList);
        return bBuilder.build();
    }
    
    /**
     * 构建 Range 查询
     */
    private BoolQuery getRangeQuery(Range range) {
        if (range.getFields() == null) return null;
        
        String[] fields = range.getFields();
        if (fields.length == 0) return null;
        
        BoolQuery.Builder bBuilder = new BoolQuery.Builder();
        bBuilder.queryName("range");
        
        List<Query> queryList = new ArrayList<>();
        for (String field : fields) {
            if (field == null || field.isBlank()) continue;
            RangeQuery.Builder rangeBuilder = new RangeQuery.Builder();
            rangeBuilder.field(field);
            
            int count = 0;
            if (range.getGt() != null) {
                rangeBuilder.gt(JsonData.of(range.getGt()));
                count++;
            }
            if (range.getGte() != null) {
                rangeBuilder.gte(JsonData.of(range.getGte()));
                count++;
            }
            if (range.getLt() != null) {
                rangeBuilder.lt(JsonData.of(range.getLt()));
                count++;
            }
            if (range.getLte() != null) {
                rangeBuilder.lte(JsonData.of(range.getLte()));
                count++;
            }
            
            if (count > 0) {
                queryList.add(new Query.Builder().range(rangeBuilder.build()).build());
            }
        }
        
        if (queryList.isEmpty()) {
            return null;
        }
        
        bBuilder.should(queryList);
        return bBuilder.build();
    }
    
    /**
     * 构建 Exists 查询
     */
    private BoolQuery getExistsQuery(String[] exists) {
        BoolQuery.Builder ebBuilder = new BoolQuery.Builder();
        List<Query> eQueryList = new ArrayList<>();
        for (String e : exists) {
            if (e == null || e.isBlank()) continue;
            ExistsQuery.Builder eBuilder = new ExistsQuery.Builder();
            eBuilder.queryName("exists");
            eBuilder.field(e);
            eQueryList.add(new Query.Builder().exists(eBuilder.build()).build());
        }
        return ebBuilder.must(eQueryList).build();
    }
    
    /**
     * 构建 Unexists 查询
     */
    private BoolQuery getUnexistQuery(String[] unexist) {
        BoolQuery.Builder ueBuilder = new BoolQuery.Builder();
        List<Query> queryList = new ArrayList<>();
        for (String e : unexist) {
            if (e == null || e.isBlank()) continue;
            ExistsQuery.Builder eBuilder = new ExistsQuery.Builder();
            eBuilder.queryName("exist");
            eBuilder.field(e);
            queryList.add(new Query.Builder().exists(eBuilder.build()).build());
        }
        return ueBuilder.mustNot(queryList).build();
    }
    
    /**
     * 构建 Equals 查询
     */
    private BoolQuery getEqualQuery(Equals equals) {
        if (equals.getValues() == null || equals.getFields() == null) return null;
        
        List<FieldValue> valueList = new ArrayList<>();
        for (String str : equals.getValues()) {
            if (str == null || str.isBlank()) continue;
            // Decode the search value
            String decodedValue = java.net.URLDecoder.decode(str, StandardCharsets.UTF_8);
            valueList.add(FieldValue.of(decodedValue));
        }
        
        BoolQuery.Builder boolBuilder = makeBoolShouldTermsQuery(equals.getFields(), valueList);
        boolBuilder.queryName("equal");
        return boolBuilder.build();
    }
    
    /**
     * 构建 Unequals 查询
     */
    private BoolQuery getUnequalQuery(Equals unequals) {
        if (unequals.getValues() == null || unequals.getFields() == null) return null;
        
        List<FieldValue> valueList = new ArrayList<>();
        for (String str : unequals.getValues()) {
            if (str == null || str.isBlank()) continue;
            // Decode the search value
            String decodedValue = java.net.URLDecoder.decode(str, StandardCharsets.UTF_8);
            valueList.add(FieldValue.of(decodedValue));
        }
        
        BoolQuery.Builder boolBuilder = makeBoolMustNotTermsQuery(unequals.getFields(), valueList);
        boolBuilder.queryName("unequal");
        return boolBuilder.build();
    }
    
    /**
     * 构建 MatchAll 查询
     */
    private MatchAllQuery getMatchAllQuery() {
        MatchAllQuery.Builder queryBuilder = new MatchAllQuery.Builder();
        queryBuilder.queryName("all");
        return queryBuilder.build();
    }
    
    /**
     * 构建 should Terms 查询（辅助方法）
     */
    private BoolQuery.Builder makeBoolShouldTermsQuery(String[] fields, List<FieldValue> valueList) {
        BoolQuery.Builder termsBoolBuilder = new BoolQuery.Builder();
        List<Query> queryList = new ArrayList<>();
        for (String field : fields) {
            if (field == null || field.isBlank()) continue;
            TermsQuery tQuery = TermsQuery.of(t -> t
                .field(field)
                .terms(t1 -> t1.value(valueList))
            );
            queryList.add(new Query.Builder().terms(tQuery).build());
        }
        termsBoolBuilder.should(queryList);
        return termsBoolBuilder;
    }
    
    /**
     * 构建 mustNot Terms 查询（辅助方法）
     */
    private BoolQuery.Builder makeBoolMustNotTermsQuery(String[] fields, List<FieldValue> valueList) {
        BoolQuery.Builder termsBoolBuilder = new BoolQuery.Builder();
        List<Query> queryList = new ArrayList<>();
        for (String field : fields) {
            if (field == null || field.isBlank()) continue;
            TermsQuery tQuery = TermsQuery.of(t -> t
                .field(field)
                .terms(t1 -> t1.value(valueList))
            );
            queryList.add(new Query.Builder().terms(tQuery).build());
        }
        termsBoolBuilder.mustNot(queryList);
        return termsBoolBuilder;
    }
    
    /**
     * 根据 FID 列表计算余额
     */
    private Map<String, Long> sumCashValueByOwners(List<String> fids) {
        Map<String, Long> balanceMap = new HashMap<>();
        
        try {
            // 构建查询：获取所有 owner 在 fids 中且 valid=true 的 cash
            List<FieldValue> fidValues = new ArrayList<>();
            for (String fid : fids) {
                fidValues.add(FieldValue.of(fid));
                balanceMap.put(fid, 0L); // 初始化余额为 0
            }
            
            SearchRequest.Builder searchBuilder = new SearchRequest.Builder();
            searchBuilder.index("cash");
            searchBuilder.size(10000); // 设置较大的 size 以获取所有结果
            
            // 构建 bool 查询
            BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
            
            // owner must be in fids list
            TermsQuery ownerQuery = TermsQuery.of(t -> t
                .field(OWNER)
                .terms(t1 -> t1.value(fidValues))
            );
            boolBuilder.must(new Query.Builder().terms(ownerQuery).build());
            
            // valid must be true
            TermQuery validQuery = TermQuery.of(t -> t
                .field(VALID)
                .value(true)
            );
            boolBuilder.must(new Query.Builder().term(validQuery).build());
            
            searchBuilder.query(q -> q.bool(boolBuilder.build()));
            
            // 执行查询
            SearchResponse<Cash> result = esClient.search(searchBuilder.build(), Cash.class);
            
            if (result.hits() != null && result.hits().hits() != null) {
                for (Hit<Cash> hit : result.hits().hits()) {
                    Cash cash = hit.source();
                    if (cash != null && cash.getOwner() != null && cash.getValue() != null) {
                        balanceMap.merge(cash.getOwner(), cash.getValue(), Long::sum);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to sum cash values by owners", e);
            throw new RuntimeException("Failed to calculate balances", e);
        }
        
        return balanceMap;
    }
    
    /**
     * 获取有效的 UTXO（重建 CashManager.getValidCashes 的核心逻辑）
     */
    private List<Cash> getValidCashes(String fid, Long amount, Long cd, Long sinceHeight, 
                                     int outputSize, int msgSize) throws Exception {
        List<Cash> cashList = new ArrayList<>();
        
        // 构建查询
        SearchRequest.Builder searchBuilder = new SearchRequest.Builder();
        searchBuilder.index("cash");
        searchBuilder.size(200); // 最多返回 200 个
        searchBuilder.trackTotalHits(t -> t.enabled(true));
        
        // 添加排序：按 CD 升序、ID 升序
        searchBuilder.sort(s -> s.field(f -> f.field(CD).order(co.elastic.clients.elasticsearch._types.SortOrder.Asc)));
        searchBuilder.sort(s -> s.field(f -> f.field(ID).order(co.elastic.clients.elasticsearch._types.SortOrder.Asc)));
        
        // 构建 bool 查询
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
        
        // owner = fid
        TermQuery ownerQuery = TermQuery.of(t -> t.field(OWNER).value(fid));
        boolBuilder.must(new Query.Builder().term(ownerQuery).build());
        
        // valid = true
        TermQuery validQuery = TermQuery.of(t -> t.field(VALID).value(true));
        boolBuilder.must(new Query.Builder().term(validQuery).build());
        
        // sinceHeight 条件
        if (sinceHeight != null) {
            RangeQuery heightQuery = RangeQuery.of(r -> r
                .field(BIRTH_HEIGHT)
                .gt(JsonData.of(sinceHeight))
            );
            boolBuilder.must(new Query.Builder().range(heightQuery).build());
        }
        
        searchBuilder.query(q -> q.bool(boolBuilder.build()));
        
        // 执行查询
        SearchResponse<Cash> result = esClient.search(searchBuilder.build(), Cash.class);
        
        if (result.hits() == null || result.hits().hits() == null || result.hits().hits().isEmpty()) {
            return cashList;
        }
        
        // 提取所有 cash
        for (Hit<Cash> hit : result.hits().hits()) {
            if (hit.source() != null) {
                cashList.add(hit.source());
            }
        }
        
        // 如果 amount 和 cd 都为 null，直接返回所有结果
        if (amount == null && cd == null) {
            return cashList;
        }
        
        // 否则，筛选出满足 amount 和 cd 要求的最小集合
        Long bestHeight = settings.getBestHeight();
        amount = amount == null ? 0L : amount;
        cd = cd == null ? 0L : cd;
        
        long fchSum = 0;
        long cdSum = 0;
        long fee = 0;
        List<Cash> meetList = new ArrayList<>();
        
        for (Cash cash : cashList) {
            // 跳过未成熟的 coinbase
            if (isImmature(cash, bestHeight)) {
                continue;
            }
            
            // 计算 CD
            long cdd = 0;
            if (cash.getBirthTime() != null) {
                cdd = utils.FchUtils.cdd(cash.getValue(), cash.getBirthTime(), System.currentTimeMillis() / 1000);
            }
            
            fchSum += cash.getValue();
            cdSum += cdd;
            meetList.add(cash);
            
            // 计算手续费
            long txSize = core.fch.TxCreator.calcTxSize(meetList.size(), outputSize, msgSize);
            fee = core.fch.TxCreator.calcFee(txSize, core.fch.TxCreator.DEFAULT_FEE_RATE);
            
            // 检查是否满足条件
            if (fchSum >= (amount + fee) && cdSum >= cd) {
                break;
            }
        }
        
        // 检查是否满足条件
        if (fchSum < (amount + fee) || cdSum < cd) {
            throw new IllegalStateException("Can't get enough amount or cd within 200 cashes");
        }
        
        return meetList;
    }
    
    /**
     * 检查 cash 是否未成熟（coinbase 需要等待一定高度）
     */
    private boolean isImmature(Cash cash, Long bestHeight) {
        if (bestHeight == null) return false;
        if (!"coinbase".equals(cash.getIssuer())) return false;
        
        // Fund 地址需要等待 10000 个区块
        if (constants.Constants.FUND_FID.equals(cash.getOwner())) {
            return (bestHeight - cash.getBirthHeight()) < 10000;
        }
        
        // 普通 coinbase 需要等待 100 个区块
        return (bestHeight - cash.getBirthHeight()) < 100;
    }
}
