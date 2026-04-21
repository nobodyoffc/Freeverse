package fapi.components;

import clients.NaSaClient.NaSaRpcClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.json.JsonData;
import core.crypto.KeyTools;
import core.fch.RawTxParser;
import core.fch.TxCreator;
import data.apipData.Fcdsl;
import data.apipData.Sort;
import data.apipData.UnconfirmedInfo;
import data.apipData.Utxo;
import data.fcData.EntityProperty;
import data.fchData.Cash;
import data.fchData.FchChainInfo;
import data.fchData.Freer;
import data.fchData.TxHasInfo;
import data.feipData.ServiceType;
import fapi.AbstractFapiComponent;
import fapi.FapiCode;
import fapi.message.FapiRequest;
import fapi.message.FapiResponse;
import fapi.query.FcdslQueryExecutor;
import fapi.query.QueryResult;
import fapi.rpc.RpcOperationService;
import fapi.rpc.RpcOperationService.RpcResult;
import utils.EsUtils;
import utils.FchUtils;

import java.util.*;

import static constants.Constants.FUND_FID;
import static constants.FieldNames.ID;

/**
 * BASE组件 - 提供链上基础数据
 * 
 * 整合了原有的EndpointHandler功能：
 * - GeneralEndpointHandler: totals, health
 * - CashEndpointHandler: balanceByIds, cashValid, getUtxo
 * - ChainInfoEndpointHandler: chainInfo, blockTimeHistory, difficultyHistory, hashRateHistory
 * - MempoolEndpointHandler: unconfirmed, unconfirmedCashes
 * - TransactionEndpointHandler: broadcastTx, decodeTx, estimateFee
 */
@SuppressWarnings("unchecked")
public class BaseComponent extends AbstractFapiComponent {
    
    private ElasticsearchClient esClient;
    private RpcOperationService rpcService;
    private FcdslQueryExecutor queryExecutor;
    
    @Override
    public String getName() {
        return "BASE";
    }
    
    @Override
    public List<String> getApiList() {
        return List.of(
            // 通用查询
            "base.getByIds",           // 根据ID获取实体
            "base.search",             // 搜索实体
            "base.freerByIds",         // 根据FID获取Freer（实时计算余额/CD）
            "base.totals",             // 获取所有索引文档数量
            "base.health",             // 健康检查
            // 余额查询
            "base.balanceByIds",       // 根据FID列表获取余额
            "base.cashValid",          // 获取有效UTXO
            "base.getUtxo",            // 获取UTXO（Utxo格式）
            // 链信息
            "base.chainInfo",          // 获取链信息
            "base.blockTimeHistory",   // 获取出块时间历史
            "base.difficultyHistory",  // 获取难度历史
            "base.hashRateHistory",    // 获取算力历史
            // 内存池
            "base.unconfirmed",        // 获取未确认交易信息
            "base.unconfirmedCashes",  // 获取未确认的Cash
            // 交易操作
            "base.broadcastTx",        // 广播交易
            "base.decodeTx",           // 解码交易
            "base.estimateFee"         // 估算交易费用
        );
    }
    
    @Override
    protected void doInitialize() {
        this.esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);
        if (esClient == null) {
            throw new IllegalStateException("ElasticsearchClient is required for BASE component");
        }
        this.queryExecutor = new FcdslQueryExecutor(esClient);
        
        NaSaRpcClient rpcClient = (NaSaRpcClient) settings.getClient(ServiceType.NASA_RPC);
        this.rpcService = new RpcOperationService(rpcClient, esClient);
    }
    
    @Override
    public FapiResponse handleRequest(FapiRequest request, String peerId) {
        String method = request.getMethodName();
        String requestId = request.getId();
        
        if (method == null) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "Method name is missing");
        }
        
        return switch (method) {
            // 通用查询
            case "getByIds" -> handleGetByIds(request);
            case "search" -> handleSearch(request);
            case "freerByIds" -> handleFreerByIds(request);
            case "totals" -> handleTotals(requestId);
            case "health" -> handleHealth(requestId);
            // 余额查询
            case "balanceByIds" -> handleBalanceByIds(request);
            case "cashValid" -> handleCashValid(request);
            case "getUtxo" -> handleGetUtxo(request);
            // 链信息
            case "chainInfo" -> handleChainInfo(request);
            case "blockTimeHistory" -> handleBlockTimeHistory(request);
            case "difficultyHistory" -> handleDifficultyHistory(request);
            case "hashRateHistory" -> handleHashRateHistory(request);
            // 内存池
            case "unconfirmed" -> handleUnconfirmed(request);
            case "unconfirmedCashes" -> handleUnconfirmedCashes(request);
            // 交易操作
            case "broadcastTx" -> handleBroadcastTx(request);
            case "decodeTx" -> handleDecodeTx(request);
            case "estimateFee" -> handleEstimateFee(request);
            default -> errorResponse(requestId, FapiCode.METHOD_NOT_ALLOWED, "Unknown method: " + method);
        };
    }
    
    // ==================== 通用查询 ====================
    
    /**
     * 处理getByIds请求
     */
    private FapiResponse handleGetByIds(FapiRequest request) {
        String requestId = request.getId();
        Fcdsl fcdsl = request.getFcdsl();
        
        if (fcdsl == null || fcdsl.getIds() == null || fcdsl.getIds().isEmpty()) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "fcdsl.ids is required");
        }
        
        String entityType = fcdsl.getEntity();
        if (entityType == null || entityType.isEmpty()) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "fcdsl.entity is required");
        }
        
        String index = getIndexName(entityType);
        EntityProperty prop = EntityProperty.getByName(entityType);
        if (prop == null) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "Unknown entity type: " + entityType);
        }
        
        try {
            Map<String, ?> result = queryExecutor.executeIdsQuery(index, prop.getEntityClass(), fcdsl.getIds());
            if (result == null || result.isEmpty()) {
                return errorResponse(requestId, FapiCode.NOT_FOUND, "No data found");
            }
            
            FapiResponse response = successResponse(requestId, result);
            response.setGot((long) result.size());
            response.setTotal((long) result.size());
            return response;
        } catch (Exception e) {
            log.error("Failed to handle getByIds", e);
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Failed to get data: " + e.getMessage());
        }
    }
    
    /**
     * 处理search请求
     */
    private FapiResponse handleSearch(FapiRequest request) {
        String requestId = request.getId();
        Fcdsl fcdsl = request.getFcdsl();
        
        if (fcdsl == null || fcdsl.getEntity() == null || fcdsl.getEntity().isEmpty()) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "fcdsl.entity is required");
        }
        
        String index = getIndexName(fcdsl.getEntity());
        EntityProperty prop = EntityProperty.getByName(fcdsl.getEntity());
        if (prop == null) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "Unknown entity type: " + fcdsl.getEntity());
        }
        
        try {
            ArrayList<Sort> defaultSortList = Sort.makeSortList(ID, true, null, null, null, null);
            QueryResult<?> queryResult = queryExecutor.executeQuery(index, prop.getEntityClass(), fcdsl, defaultSortList);
            if (queryResult == null || queryResult.isEmpty()) {
                return errorResponse(requestId, FapiCode.NOT_FOUND, "No data found");
            }
            
            FapiResponse response = successResponse(requestId, queryResult.getData());
            response.setGot(queryResult.getGot());
            response.setTotal(queryResult.getTotal());
            response.setLast(queryResult.getLast());
            return response;
        } catch (Exception e) {
            log.error("Failed to handle search", e);
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Search failed: " + e.getMessage());
        }
    }

    /**
     * 处理freerByIds请求 - 根据FID获取Freer并实时计算余额、UTXO数量和CD
     */
    private FapiResponse handleFreerByIds(FapiRequest request) {
        String requestId = request.getId();
        Fcdsl fcdsl = request.getFcdsl();

        List<String> fids = fcdsl != null ? fcdsl.getIds() : null;
        if (fids == null || fids.isEmpty()) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "fcdsl.ids (FID list) is required");
        }

        try {
            // 1. 从freer索引获取Freer记录
            Map<String, Freer> freerMap = queryExecutor.executeIdsQuery("freer", Freer.class, fids);
            if (freerMap == null || freerMap.isEmpty()) {
                return errorResponse(requestId, FapiCode.NOT_FOUND, "No freers found");
            }

            // 2. 从cash索引实时计算余额、数量和CD
            Map<String, long[]> liveStats = computeLiveCashStats(new ArrayList<>(freerMap.keySet()));

            // 3. 更新Freer对象并批量更新ES
            BulkRequest.Builder br = new BulkRequest.Builder();
            for (Map.Entry<String, Freer> entry : freerMap.entrySet()) {
                String fid = entry.getKey();
                Freer freer = entry.getValue();
                long[] stats = liveStats.getOrDefault(fid, new long[]{0, 0, 0});

                freer.setCash(stats[0]);
                freer.setBalance(stats[1]);
                freer.setCd(stats[2]);

                Map<String, Long> updateMap = new HashMap<>();
                updateMap.put("cash", stats[0]);
                updateMap.put("balance", stats[1]);
                updateMap.put("cd", stats[2]);

                br.operations(o -> o.update(u -> u
                    .index("freer").id(fid)
                    .action(a -> a.doc(updateMap))));
            }
            EsUtils.bulkWithBuilder(esClient, br);

            // 4. 返回更新后的Freer
            FapiResponse response = successResponse(requestId, freerMap);
            response.setGot((long) freerMap.size());
            response.setTotal((long) freerMap.size());
            return response;
        } catch (Exception e) {
            log.error("Failed to handle freerByIds", e);
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Failed to get freers: " + e.getMessage());
        }
    }

    /**
     * 从cash索引实时计算每个FID的UTXO数量、余额和CD
     * 使用ES聚合查询，一次请求完成所有计算
     *
     * @return Map<FID, long[]{count, balance, cd}>
     */
    private Map<String, long[]> computeLiveCashStats(List<String> fids) throws Exception {
        Map<String, long[]> statsMap = new HashMap<>();

        List<FieldValue> fidValues = new ArrayList<>();
        for (String fid : fids) {
            fidValues.add(FieldValue.of(fid));
        }

        long nowSeconds = System.currentTimeMillis() / 1000;

        // CD公式与Cash.makeCd()和CdMaker一致: ((now - birthTime) / 86400) * value / 100000000
        String cdScript = "long bt = doc['birthTime'].size() > 0 ? doc['birthTime'].value : 0L; " +
                          "long v = doc['value'].size() > 0 ? doc['value'].value : 0L; " +
                          "return ((long)((params.now - bt) / 86400)) * v / 100000000;";

        SearchResponse<Void> searchResponse = esClient.search(s -> s
            .index("cash")
            .size(0)
            .query(q -> q.bool(b -> b
                .must(m -> m.terms(t -> t.field("owner").terms(t1 -> t1.value(fidValues))))
                .must(m -> m.term(t -> t.field("valid").value(true)))))
            .aggregations("byOwner", a -> a
                .terms(t -> t.field("owner").size(fids.size()))
                .aggregations("balanceSum", a2 -> a2.sum(su -> su.field("value")))
                .aggregations("cdSum", a2 -> a2.sum(su -> su
                    .script(sc -> sc.inline(i -> i
                        .source(cdScript)
                        .params("now", JsonData.of(nowSeconds))))))),
            Void.class);

        List<StringTermsBucket> buckets = searchResponse.aggregations()
            .get("byOwner").sterms().buckets().array();

        for (StringTermsBucket bucket : buckets) {
            String owner = bucket.key().stringValue();
            long count = bucket.docCount();
            long balance = (long) bucket.aggregations().get("balanceSum").sum().value();
            long cd = (long) bucket.aggregations().get("cdSum").sum().value();
            statsMap.put(owner, new long[]{count, balance, cd});
        }

        return statsMap;
    }

    /**
     * 处理totals请求
     */
    private FapiResponse handleTotals(String requestId) {
        try {
            IndicesResponse result = esClient.cat().indices();
            List<IndicesRecord> indicesRecordList = result.valueBody();
            
            Set<String> entityNames = EntityProperty.getEntityNames();
            Map<String, String> allSumMap = new HashMap<>();
            for (IndicesRecord record : indicesRecordList) {
                String indexName = record.index();
                if (entityNames.contains(indexName)) {
                    allSumMap.put(indexName, record.docsCount());
                }
            }
            
            FapiResponse response = successResponse(requestId, allSumMap);
            response.setGot((long) allSumMap.size());
            response.setTotal((long) allSumMap.size());
            return response;
        } catch (Exception e) {
            log.error("Failed to handle totals", e);
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Failed to get totals: " + e.getMessage());
        }
    }
    
    /**
     * 健康检查
     */
    private FapiResponse handleHealth(String requestId) {
        // 使用 FapiServer 的 HealthChecker 获取完整健康状态
        if (server != null) {
            return successResponse(requestId, server.getHealthStatus());
        }
        
        // 回退：返回简单的组件健康信息
        Map<String, Object> health = Map.of(
            "status", "healthy",
            "component", getName(),
            "timestamp", System.currentTimeMillis()
        );
        return successResponse(requestId, health);
    }
    
    // ==================== 余额查询 ====================
    
    /**
     * 处理balanceByIds请求
     */
    private FapiResponse handleBalanceByIds(FapiRequest request) {
        String requestId = request.getId();
        Fcdsl fcdsl = request.getFcdsl();
        
        List<String> fids = fcdsl != null ? fcdsl.getIds() : null;
        if (fids == null || fids.isEmpty()) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "fcdsl.ids (FID list) is required");
        }
        
        try {
            Map<String, Long> balanceMap = sumCashValueByOwners(fids);
            FapiResponse response = successResponse(requestId, balanceMap);
            response.setGot((long) balanceMap.size());
            response.setTotal((long) balanceMap.size());
            return response;
        } catch (Exception e) {
            log.error("Failed to handle balanceByIds", e);
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Failed to get balances: " + e.getMessage());
        }
    }
    
    /**
     * 处理cashValid请求
     */
    private FapiResponse handleCashValid(FapiRequest request) {
        String requestId = request.getId();
        Fcdsl fcdsl = request.getFcdsl();
        Map<String, Object> params = parseParams(request.getParams(), Map.class);
        
        // 模式1：标准FCDSL查询（params为空）
        if (params == null || params.isEmpty()) {
            if (fcdsl == null) {
                fcdsl = new Fcdsl();
            }
            if (fcdsl.getFilter() == null) {
                fcdsl.addNewFilter().addNewTerms().addNewFields("valid").addNewValues("true");
            }
            
            try {
                QueryResult<Cash> queryResult = queryExecutor.executeQuery("cash", Cash.class, fcdsl, null);
                if (queryResult == null || queryResult.isEmpty()) {
                    return errorResponse(requestId, FapiCode.NOT_FOUND, "No valid cashes found");
                }
                
                FapiResponse response = successResponse(requestId, queryResult.getData());
                response.setGot(queryResult.getGot());
                response.setTotal(queryResult.getTotal());
                response.setLast(queryResult.getLast());
                return response;
            } catch (Exception e) {
                log.error("Failed to handle cashValid", e);
                return errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Query failed: " + e.getMessage());
            }
        }
        
        // 模式2：智能选币
        String fid = null;
        if (fcdsl != null && fcdsl.getIds() != null && !fcdsl.getIds().isEmpty()) {
            fid = fcdsl.getIds().get(0);
        }
        if (fid == null) {
            fid = (String) params.get("fid");
        }
        if (fid == null || fid.isEmpty()) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "fid parameter is required");
        }
        
        try {
            // Parse amount - handle both String and Number types, consistent with original CashValid implementation
            Long amount = null;
            Object amountObj = params.get("amount");
            if (amountObj != null) {
                if (amountObj instanceof Number) {
                    amount = FchUtils.coinToSatoshi(((Number) amountObj).doubleValue());
                } else {
                    String amountStr = amountObj.toString();
                    if (amountStr != null && !amountStr.isEmpty()) {
                        // Use Double.parseDouble to match original implementation, throws NumberFormatException on error
                        amount = FchUtils.coinToSatoshi(Double.parseDouble(amountStr));
                    }
                }
            }
            
            // Parse cd - handle both String and Number types
            Long cd = null;
            Object cdObj = params.get("cd");
            if (cdObj != null) {
                if (cdObj instanceof Number) {
                    cd = ((Number) cdObj).longValue();
                } else {
                    String cdStr = cdObj.toString();
                    if (!cdStr.isEmpty()) {
                        cd = Long.parseLong(cdStr);
                    }
                }
            }
            
            // Parse msgSize - handle both String and Number types
            int msgSize = 0;
            Object msgSizeObj = params.get("msgSize");
            if (msgSizeObj != null) {
                if (msgSizeObj instanceof Number) {
                    msgSize = ((Number) msgSizeObj).intValue();
                } else {
                    String msgSizeStr = msgSizeObj.toString();
                    if (!msgSizeStr.isEmpty()) {
                        msgSize = Integer.parseInt(msgSizeStr);
                    }
                }
            }
            
            // Parse outputSize - handle both String and Number types
            int outputSize = 0;
            Object outputSizeObj = params.get("outputSize");
            if (outputSizeObj != null) {
                if (outputSizeObj instanceof Number) {
                    outputSize = ((Number) outputSizeObj).intValue();
                } else {
                    String outputSizeStr = outputSizeObj.toString();
                    if (!outputSizeStr.isEmpty()) {
                        outputSize = Integer.parseInt(outputSizeStr);
                    }
                }
            }
            
            // Parse sinceHeight - handle both String and Number types
            Long sinceHeight = null;
            Object sinceHeightObj = params.get("sinceHeight");
            if (sinceHeightObj != null) {
                if (sinceHeightObj instanceof Number) {
                    sinceHeight = ((Number) sinceHeightObj).longValue();
                } else {
                    String sinceHeightStr = sinceHeightObj.toString();
                    if (!sinceHeightStr.isEmpty()) {
                        sinceHeight = Long.parseLong(sinceHeightStr);
                    }
                }
            }
            
            List<Cash> cashList = getValidCashes(fid, amount, cd, sinceHeight, outputSize, msgSize);
            if (cashList == null || cashList.isEmpty()) {
                return errorResponse(requestId, FapiCode.NOT_FOUND, "No valid cashes found");
            }
            
            FapiResponse response = successResponse(requestId, cashList);
            response.setGot((long) cashList.size());
            response.setTotal((long) cashList.size());
            return response;
        } catch (NumberFormatException e) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "Invalid parameter format: " + e.getMessage());
        } catch (IllegalStateException e) {
            return errorResponse(requestId, FapiCode.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to get valid cashes", e);
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Failed to get valid cashes: " + e.getMessage());
        }
    }
    
    /**
     * 处理getUtxo请求
     */
    private FapiResponse handleGetUtxo(FapiRequest request) {
        String requestId = request.getId();
        Map<String, Object> params = parseParams(request.getParams(), Map.class);
        
        if (params == null || params.isEmpty()) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "params is required");
        }
        
        String fid = params.get("address") != null ? params.get("address").toString() : null;
        if (fid == null || fid.isEmpty()) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "address parameter is required");
        }
        
        if (!KeyTools.isGoodFid(fid)) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "Invalid FID format");
        }
        
        try {
            String amountStr = params.get("amount") != null ? params.get("amount").toString() : null;
            String cdStr = params.get("cd") != null ? params.get("cd").toString() : null;
            
            Long amount = amountStr != null ? FchUtils.coinStrToSatoshi(amountStr) : 0L;
            Long cd = cdStr != null ? Long.parseLong(cdStr) : 0L;
            
            List<Cash> cashList = getValidCashes(fid, amount, cd, null, 0, 0);
            if (cashList == null || cashList.isEmpty()) {
                return errorResponse(requestId, FapiCode.NOT_FOUND, "No UTXOs found");
            }
            
            List<Utxo> utxoList = new ArrayList<>();
            for (Cash cash : cashList) {
                utxoList.add(Utxo.cashToUtxo(cash));
            }
            
            FapiResponse response = successResponse(requestId, utxoList);
            response.setGot((long) utxoList.size());
            response.setTotal((long) utxoList.size());
            return response;
        } catch (Exception e) {
            log.error("Failed to get UTXOs", e);
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Failed to get UTXOs: " + e.getMessage());
        }
    }
    
    // ==================== 链信息 ====================
    
    /**
     * 处理chainInfo请求
     */
    private FapiResponse handleChainInfo(FapiRequest request) {
        String requestId = request.getId();
        Map<String, Object> params = parseParams(request.getParams(), Map.class);
        
        Long height = null;
        if (params != null && params.get("height") != null) {
            try {
                height = Long.parseLong(params.get("height").toString());
            } catch (NumberFormatException e) {
                return errorResponse(requestId, FapiCode.BAD_REQUEST, "Invalid height parameter");
            }
        }
        
        RpcResult<FchChainInfo> result = rpcService.getChainInfo(height);
        if (!result.isSuccess()) {
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, result.getErrorMessage());
        }
        
        return successResponse(requestId, result.getData());
    }
    
    /**
     * 处理blockTimeHistory请求
     */
    private FapiResponse handleBlockTimeHistory(FapiRequest request) {
        String requestId = request.getId();
        HistoryParams params = parseHistoryParams(request);
        if (params.errorMessage != null) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, params.errorMessage);
        }
        
        if (params.count > FchChainInfo.MAX_REQUEST_COUNT) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, 
                "The count can not be bigger than " + FchChainInfo.MAX_REQUEST_COUNT);
        }
        
        RpcResult<Map<Long, Long>> result = rpcService.getBlockTimeHistory(params.startTime, params.endTime, params.count);
        if (!result.isSuccess()) {
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, result.getErrorMessage());
        }
        
        Map<Long, Long> hist = result.getData();
        FapiResponse response = successResponse(requestId, hist);
        response.setGot((long) hist.size());
        response.setTotal(getHistoryTotal());
        return response;
    }
    
    /**
     * 处理difficultyHistory请求
     */
    private FapiResponse handleDifficultyHistory(FapiRequest request) {
        String requestId = request.getId();
        HistoryParams params = parseHistoryParams(request);
        if (params.errorMessage != null) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, params.errorMessage);
        }
        
        if (params.count > FchChainInfo.MAX_REQUEST_COUNT) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, 
                "The count can not be bigger than " + FchChainInfo.MAX_REQUEST_COUNT);
        }
        
        RpcResult<Map<Long, String>> result = rpcService.getDifficultyHistory(params.startTime, params.endTime, params.count);
        if (!result.isSuccess()) {
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, result.getErrorMessage());
        }
        
        Map<Long, String> hist = result.getData();
        FapiResponse response = successResponse(requestId, hist);
        response.setGot((long) hist.size());
        response.setTotal(getHistoryTotal());
        return response;
    }
    
    /**
     * 处理hashRateHistory请求
     */
    private FapiResponse handleHashRateHistory(FapiRequest request) {
        String requestId = request.getId();
        HistoryParams params = parseHistoryParams(request);
        if (params.errorMessage != null) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, params.errorMessage);
        }
        
        if (params.count > FchChainInfo.MAX_REQUEST_COUNT) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, 
                "The count can not be bigger than " + FchChainInfo.MAX_REQUEST_COUNT);
        }
        
        RpcResult<Map<Long, String>> result = rpcService.getHashRateHistory(params.startTime, params.endTime, params.count);
        if (!result.isSuccess()) {
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, result.getErrorMessage());
        }
        
        Map<Long, String> hist = result.getData();
        FapiResponse response = successResponse(requestId, hist);
        response.setGot((long) hist.size());
        response.setTotal(getHistoryTotal());
        return response;
    }
    
    // ==================== 内存池 ====================
    
    /**
     * 处理unconfirmed请求
     */
    private FapiResponse handleUnconfirmed(FapiRequest request) {
        String requestId = request.getId();
        Fcdsl fcdsl = request.getFcdsl();
        
        List<String> idList = fcdsl != null ? fcdsl.getIds() : null;
        if (idList == null || idList.isEmpty()) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "fcdsl.ids (FID list) is required");
        }
        
        try {
            Map<String, UnconfirmedInfo> resultMap = getUnconfirmedInfo(idList);
            FapiResponse response = successResponse(requestId, resultMap);
            response.setGot((long) resultMap.size());
            response.setTotal((long) resultMap.size());
            return response;
        } catch (Exception e) {
            log.error("Failed to handle unconfirmed", e);
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Failed to get unconfirmed info: " + e.getMessage());
        }
    }
    
    /**
     * 处理unconfirmedCashes请求
     */
    private FapiResponse handleUnconfirmedCashes(FapiRequest request) {
        String requestId = request.getId();
        Fcdsl fcdsl = request.getFcdsl();
        
        List<String> fidList = fcdsl != null ? fcdsl.getIds() : null;
        
        try {
            Map<String, List<Cash>> resultMap = getUnconfirmedCashes(fidList);
            FapiResponse response = successResponse(requestId, resultMap);
            response.setGot((long) resultMap.size());
            response.setTotal((long) resultMap.size());
            return response;
        } catch (Exception e) {
            log.error("Failed to handle unconfirmedCashes", e);
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, "Failed to get unconfirmed cashes: " + e.getMessage());
        }
    }
    
    // ==================== 交易操作 ====================
    
    /**
     * 处理broadcastTx请求
     */
    private FapiResponse handleBroadcastTx(FapiRequest request) {
        String requestId = request.getId();
        Map<String, Object> params = parseParams(request.getParams(), Map.class);
        
        if (params == null || !params.containsKey("rawTx")) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "rawTx parameter is required");
        }
        
        String rawTx = params.get("rawTx").toString();
        RpcResult<String> result = rpcService.broadcastTransaction(rawTx);
        
        if (!result.isSuccess()) {
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, result.getErrorMessage());
        }
        
        return successResponse(requestId, result.getData());
    }
    
    /**
     * 处理decodeTx请求
     */
    private FapiResponse handleDecodeTx(FapiRequest request) {
        String requestId = request.getId();
        Map<String, Object> params = parseParams(request.getParams(), Map.class);
        
        if (params == null || !params.containsKey("rawTx")) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "rawTx parameter is required");
        }
        
        String rawTx = params.get("rawTx").toString();
        RpcResult<Object> result = rpcService.decodeTransaction(rawTx);
        
        if (!result.isSuccess()) {
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, result.getErrorMessage());
        }
        
        return successResponse(requestId, result.getData());
    }
    
    /**
     * 处理estimateFee请求
     */
    private FapiResponse handleEstimateFee(FapiRequest request) {
        String requestId = request.getId();
        Map<String, Object> params = parseParams(request.getParams(), Map.class);
        
        Integer nBlocks = null;
        if (params != null && params.containsKey("nBlocks")) {
            try {
                nBlocks = Integer.parseInt(params.get("nBlocks").toString());
            } catch (NumberFormatException e) {
                return errorResponse(requestId, FapiCode.BAD_REQUEST, "Invalid nBlocks parameter");
            }
        }
        
        RpcResult<Double> result = rpcService.estimateFee(nBlocks);
        
        if (!result.isSuccess()) {
            return errorResponse(requestId, FapiCode.INTERNAL_ERROR, result.getErrorMessage());
        }
        
        return successResponse(requestId, result.getData());
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 获取索引名称
     */
    private String getIndexName(String entityType) {
        EntityProperty prop = EntityProperty.getByName(entityType);
        if (prop != null) {
            return prop.getEntityName();
        }
        return switch (entityType.toLowerCase()) {
            case "address" -> "freer";
            case "transaction" -> "tx";
            case "utxo" -> "cash";
            default -> entityType;
        };
    }
    
    /**
     * 计算FID列表的余额
     */
    private Map<String, Long> sumCashValueByOwners(List<String> fids) throws Exception {
        Map<String, Long> balanceMap = new HashMap<>();
        
        List<FieldValue> fidValues = new ArrayList<>();
        for (String fid : fids) {
            fidValues.add(FieldValue.of(fid));
            balanceMap.put(fid, 0L);
        }
        
        SearchRequest.Builder searchBuilder = new SearchRequest.Builder();
        searchBuilder.index("cash");
        searchBuilder.size(10000);
        
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
        TermsQuery ownerQuery = TermsQuery.of(t -> t.field("owner").terms(t1 -> t1.value(fidValues)));
        boolBuilder.must(new Query.Builder().terms(ownerQuery).build());
        TermQuery validQuery = TermQuery.of(t -> t.field("valid").value(true));
        boolBuilder.must(new Query.Builder().term(validQuery).build());
        
        searchBuilder.query(q -> q.bool(boolBuilder.build()));
        
        SearchResponse<Cash> result = esClient.search(searchBuilder.build(), Cash.class);
        
        if (result.hits() != null && result.hits().hits() != null) {
            for (Hit<Cash> hit : result.hits().hits()) {
                Cash cash = hit.source();
                if (cash != null && cash.getOwner() != null && cash.getValue() != null) {
                    balanceMap.merge(cash.getOwner(), cash.getValue(), Long::sum);
                }
            }
        }
        
        return balanceMap;
    }
    
    /**
     * 获取有效UTXO（智能选币）
     */
    private List<Cash> getValidCashes(String fid, Long amount, Long cd, Long sinceHeight,
                                     int outputSize, int msgSize) throws Exception {
        List<Cash> cashList = new ArrayList<>();
        
        SearchRequest.Builder searchBuilder = new SearchRequest.Builder();
        searchBuilder.index("cash");
        searchBuilder.size(200);
        searchBuilder.trackTotalHits(t -> t.enabled(true));
        searchBuilder.sort(s -> s.field(f -> f.field("cd").order(SortOrder.Asc)));
        searchBuilder.sort(s -> s.field(f -> f.field("id").order(SortOrder.Asc)));
        
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
        boolBuilder.must(new Query.Builder().term(TermQuery.of(t -> t.field("owner").value(fid))).build());
        boolBuilder.must(new Query.Builder().term(TermQuery.of(t -> t.field("valid").value(true))).build());
        
        if (sinceHeight != null) {
            RangeQuery heightQuery = RangeQuery.of(r -> r.field("birthHeight").gt(JsonData.of(sinceHeight)));
            boolBuilder.must(new Query.Builder().range(heightQuery).build());
        }
        
        searchBuilder.query(q -> q.bool(boolBuilder.build()));
        
        SearchResponse<Cash> result = esClient.search(searchBuilder.build(), Cash.class);
        
        if (result.hits() == null || result.hits().hits() == null || result.hits().hits().isEmpty()) {
            return cashList;
        }
        
        for (Hit<Cash> hit : result.hits().hits()) {
            if (hit.source() != null) {
                cashList.add(hit.source());
            }
        }
        
        if (amount == null && cd == null) {
            return cashList;
        }
        
        Long bestHeight = settings.getBestHeight();
        amount = amount == null ? 0L : amount;
        cd = cd == null ? 0L : cd;
        
        long fchSum = 0;
        long cdSum = 0;
        long fee = 0;
        List<Cash> meetList = new ArrayList<>();
        
        for (Cash cash : cashList) {
            if (isImmature(cash, bestHeight)) continue;
            
            long cdd = 0;
            if (cash.getBirthTime() != null) {
                cdd = FchUtils.cdd(cash.getValue(), cash.getBirthTime(), System.currentTimeMillis() / 1000);
            }
            
            fchSum += cash.getValue();
            cdSum += cdd;
            meetList.add(cash);
            
            long txSize = TxCreator.calcTxSize(meetList.size(), outputSize, msgSize);
            fee = TxCreator.calcFee(txSize, TxCreator.DEFAULT_FEE_RATE);
            
            if (fchSum >= (amount + fee) && cdSum >= cd) {
                break;
            }
        }
        
        if (fchSum < (amount + fee) || cdSum < cd) {
            throw new IllegalStateException("Can't get enough amount or cd within 200 cashes");
        }
        
        return meetList;
    }
    
    /**
     * 检查Cash是否未成熟
     */
    private boolean isImmature(Cash cash, Long bestHeight) {
        if (bestHeight == null) return false;
        if (!"coinbase".equals(cash.getIssuer())) return false;
        
        if (FUND_FID.equals(cash.getOwner())) {
            return (bestHeight - cash.getBirthHeight()) < 10000;
        }
        return (bestHeight - cash.getBirthHeight()) < 100;
    }
    
    /**
     * 获取未确认交易信息
     */
    private Map<String, UnconfirmedInfo> getUnconfirmedInfo(List<String> idList) throws Exception {
        Map<String, UnconfirmedInfo> resultMap = new HashMap<>();
        
        for (String id : idList) {
            UnconfirmedInfo info = new UnconfirmedInfo();
            info.setFid(id);
            info.setIncomeCount(0);
            info.setIncomeValue(0);
            info.setSpendCount(0);
            info.setSpendValue(0);
            info.setNet(0);
            resultMap.put(id, info);
        }
        
        String[] mempoolTxIds = rpcService.getMempoolTxIds();
        if (mempoolTxIds == null || mempoolTxIds.length == 0) {
            return resultMap;
        }
        
        for (String txId : mempoolTxIds) {
            try {
                String rawTxHex = rpcService.getRawTx(txId);
                if (rawTxHex == null) continue;
                
                TxHasInfo txInfo = RawTxParser.parseMempoolTx(rawTxHex, txId, null, esClient);
                if (txInfo == null) continue;
                
                for (String fid : idList) {
                    UnconfirmedInfo info = resultMap.get(fid);
                    Map<String, Long> txValueMap = info.getTxValueMap();
                    if (txValueMap == null) {
                        txValueMap = new HashMap<>();
                        info.setTxValueMap(txValueMap);
                    }
                    
                    if (txInfo.getInCashList() != null) {
                        for (Cash cash : txInfo.getInCashList()) {
                            if (fid.equals(cash.getOwner()) && cash.getValue() != null) {
                                info.setSpendCount(info.getSpendCount() + 1);
                                info.setSpendValue(info.getSpendValue() + cash.getValue());
                                info.setNet(info.getNet() - cash.getValue());
                                txValueMap.merge(txId, -cash.getValue(), Long::sum);
                            }
                        }
                    }
                    
                    if (txInfo.getOutCashList() != null) {
                        for (Cash cash : txInfo.getOutCashList()) {
                            if (fid.equals(cash.getOwner()) && cash.isValid() != null 
                                && cash.isValid() && cash.getValue() != null) {
                                info.setIncomeCount(info.getIncomeCount() + 1);
                                info.setIncomeValue(info.getIncomeValue() + cash.getValue());
                                info.setNet(info.getNet() + cash.getValue());
                                txValueMap.merge(txId, cash.getValue(), Long::sum);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error processing mempool tx {}: {}", txId, e.getMessage());
            }
        }
        
        for (UnconfirmedInfo info : resultMap.values()) {
            if (info.getTxValueMap() != null && info.getTxValueMap().isEmpty()) {
                info.setTxValueMap(null);
            }
        }
        
        return resultMap;
    }
    
    /**
     * 获取未确认的Cash
     */
    private Map<String, List<Cash>> getUnconfirmedCashes(List<String> fidList) throws Exception {
        Map<String, List<Cash>> resultMap = new HashMap<>();
        
        if (fidList != null && !fidList.isEmpty()) {
            for (String fid : fidList) {
                resultMap.put(fid, new ArrayList<>());
            }
        }
        
        String[] mempoolTxIds = rpcService.getMempoolTxIds();
        if (mempoolTxIds == null || mempoolTxIds.length == 0) {
            return resultMap;
        }
        
        for (String txId : mempoolTxIds) {
            try {
                String rawTxHex = rpcService.getRawTx(txId);
                if (rawTxHex == null) continue;
                
                TxHasInfo txInfo = RawTxParser.parseMempoolTx(rawTxHex, txId, null, esClient);
                if (txInfo == null) continue;
                
                if (txInfo.getInCashList() != null) {
                    for (Cash cash : txInfo.getInCashList()) {
                        String owner = cash.getOwner();
                        if (owner != null && (fidList == null || fidList.isEmpty() || fidList.contains(owner))) {
                            resultMap.computeIfAbsent(owner, k -> new ArrayList<>()).add(cash);
                        }
                    }
                }
                
                if (txInfo.getOutCashList() != null) {
                    for (Cash cash : txInfo.getOutCashList()) {
                        String owner = cash.getOwner();
                        if (owner != null && (fidList == null || fidList.isEmpty() || fidList.contains(owner))) {
                            resultMap.computeIfAbsent(owner, k -> new ArrayList<>()).add(cash);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error processing mempool tx {}: {}", txId, e.getMessage());
            }
        }
        
        return resultMap;
    }
    
    /**
     * 解析历史查询参数
     */
    private HistoryParams parseHistoryParams(FapiRequest request) {
        HistoryParams params = new HistoryParams();
        Map<String, Object> paramsMap = parseParams(request.getParams(), Map.class);
        
        if (paramsMap == null || paramsMap.isEmpty()) {
            return params;
        }
        
        try {
            if (paramsMap.containsKey("startTime")) {
                params.startTime = Long.parseLong(paramsMap.get("startTime").toString());
            }
            if (paramsMap.containsKey("endTime")) {
                params.endTime = Long.parseLong(paramsMap.get("endTime").toString());
            }
            if (paramsMap.containsKey("count")) {
                params.count = Integer.parseInt(paramsMap.get("count").toString());
            }
        } catch (NumberFormatException e) {
            params.errorMessage = "Invalid history parameters";
        }
        
        return params;
    }
    
    private Long getHistoryTotal() {
        Long bestHeight = settings.getBestHeight();
        if (bestHeight != null && bestHeight > 0) {
            return bestHeight - 1;
        }
        return null;
    }
    
    /**
     * 历史参数容器
     */
    private static class HistoryParams {
        long startTime;
        long endTime;
        int count;
        String errorMessage;
    }
}

