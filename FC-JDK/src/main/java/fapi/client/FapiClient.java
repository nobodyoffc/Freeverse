package fapi.client;

import data.apipData.Fcdsl;
import data.fchData.*;
import data.feipData.*;
import fapi.FapiDefaults;
import fapi.message.FapiRequest;
import fapi.message.FapiResponse;
import fapi.message.UnifiedCodec;
import fapi.message.UnifiedCodec.UnifiedResponse;
import data.fcData.DockItem;
import fudp.message.ResponseMessage;
import fudp.message.PongMessage;
import fudp.node.FudpNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import config.Settings;
import constants.FieldNames;
import constants.IndicesNames;
import constants.Values;
import core.crypto.KeyTools;
import utils.JsonUtils;
import utils.ObjectUtils;

import ui.ProgressInputStream;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongConsumer;

/**
 * FAPI 客户端，封装 FUDP 请求，提供便捷的查询方法
 * 参考 ApipClient 的设计，但使用 FUDP 协议而非 HTTP
 */
public class FapiClient {
    private static final Logger log = LoggerFactory.getLogger(FapiClient.class);
    
    public static final long DEFAULT_HELLO_TIMEOUT_MS = 5000;
    public static final long DEFAULT_PING_TIMEOUT_MS = 5000;

    private final FudpNode fudpNode;
    private final String servicePeerId;  // FAPI 服务提供者的 Peer ID (FID)
    private final String serviceSid;      // Service ID (链上注册的交易ID)
    private final long requestTimeoutSeconds;
    private final BalanceVerifier balanceVerifier;
    private final Settings settings;
    private final AutoRechargeManager autoRechargeManager;
    private final String via;  // 消费渠道标识
    private String serverUrl;  // URL of the FAPI server this client is connected to
    
    private FapiResponse lastResponse;
    private Exception lastError;
    private Long lastBalance;
    private Long lastBalanceSeq;
    private Long lastBalanceTimestampMillis;
    private Long lastCharged;
    private final ThreadLocal<Boolean> insideBalanceUpdate = ThreadLocal.withInitial(() -> Boolean.FALSE);
    
    public FapiClient(FudpNode fudpNode, String servicePeerId, String serviceSid) {
        this(fudpNode, servicePeerId, serviceSid, 30, null);
    }
    
    public FapiClient(FudpNode fudpNode, String servicePeerId, String serviceSid, long requestTimeoutSeconds) {
        this(fudpNode, servicePeerId, serviceSid, requestTimeoutSeconds, null);
    }

    public FapiClient(FudpNode fudpNode, String servicePeerId, String serviceSid, long requestTimeoutSeconds, Settings settings) {
        this.fudpNode = fudpNode;
        this.servicePeerId = servicePeerId;
        this.serviceSid = serviceSid;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.settings = settings;
        this.balanceVerifier = BalanceVerifier.fromSettings(settings);
        this.autoRechargeManager = (settings != null) ? new AutoRechargeManager(this, settings) : null;
        
        // 从 settingMap 读取 via 配置
        if (settings != null && settings.getSettingMap() != null) {
            Object viaObj = settings.getSettingMap().get(fapi.FapiBalanceManager.KEY_DEFAULT_VIA);
            this.via = viaObj != null ? viaObj.toString() : fapi.FapiBalanceManager.DEFAULT_VIA;
        } else {
            this.via = fapi.FapiBalanceManager.DEFAULT_VIA;
        }
    }
    
    /**
     * 发送 FapiRequest 请求（新架构核心方法）
     */
    public FapiResponse request(FapiRequest fapiRequest) {
        try {
            if (balanceVerifier != null && balanceVerifier.isStopped()) {
                lastError = new IllegalStateException("Balance verification stopped due to drift");
                FapiResponse errorResp = buildErrorResponse(403, "Balance verification stopped");
                this.lastResponse = errorResp;
                return errorResp;
            }
            
            // 自动填充 via（消费渠道）
            if (fapiRequest.getVia() == null && via != null) {
                fapiRequest.setVia(via);
            }
            
            // 使用统一编码格式编码请求
            byte[] requestData = UnifiedCodec.encodeRequest(fapiRequest);
            log.debug("[FapiClient] Sending request: api={}, dataLen={}, timeout={}s",
                    fapiRequest.getApi(), requestData.length, requestTimeoutSeconds);
            CompletableFuture<ResponseMessage> future = fudpNode.request(servicePeerId, serviceSid, requestData);
            
            log.debug("[FapiClient] Waiting for response: api={}", fapiRequest.getApi());
            ResponseMessage response = future.get(requestTimeoutSeconds, TimeUnit.SECONDS);
            log.debug("[FapiClient] Response received: api={}, status={}, dataLen={}",
                    fapiRequest.getApi(), response.getStatusCode(),
                    response.getData() != null ? response.getData().length : 0);
            
            // FUDP uses STATUS_SUCCESS = 0 for success, not HTTP 200
            if (response.getStatusCode() != ResponseMessage.STATUS_SUCCESS) {
                this.lastError = new IOException("Request failed with status: " + response.getStatusCode());
                this.lastResponse = buildErrorResponse(response.getStatusCode(),
                    response.getData() != null ? new String(response.getData()) : "Unknown error");
                return lastResponse;
            }
            
            // 使用统一编码格式解析响应
            FapiResponse fapiResponse = parseUnifiedResponse(response.getData());
            updateBalanceFromResponse(fapiResponse);
            this.lastResponse = fapiResponse;
            this.lastError = null;
            
            return fapiResponse;
            
        } catch (TimeoutException e) {
            this.lastError = e;
            FapiResponse errorResp = buildErrorResponse(408, "Request timeout after " + requestTimeoutSeconds + "s");
            this.lastResponse = errorResp;
            log.warn("FAPI request timeout ({}s): api={}", requestTimeoutSeconds, 
                    fapiRequest != null ? fapiRequest.getApi() : "null");
            return errorResp;
        } catch (Exception e) {
            this.lastError = e;
            FapiResponse errorResp = buildErrorResponse(500, e.getMessage());
            this.lastResponse = errorResp;
            log.error("Error sending FAPI request: api={}", 
                    fapiRequest != null ? fapiRequest.getApi() : "null", e);
            return errorResp;
        }
    }
    
    /**
     * 发送查询类请求
     * @param api API名称，如 "base.search"
     * @param fcdsl 查询参数
     */
    public FapiResponse query(String api, Fcdsl fcdsl) {
        return request(FapiRequest.query(api, fcdsl));
    }
    
    /**
     * 发送查询类请求（带费用限制）
     * @param api API名称，如 "base.search"
     * @param fcdsl 查询参数
     * @param maxCost 最大费用（聪），超过此限制服务端将拒绝请求
     */
    public FapiResponse query(String api, Fcdsl fcdsl, long maxCost) {
        return request(FapiRequest.query(api, fcdsl).withMaxCost(maxCost));
    }
    
    /**
     * 发送操作类请求
     * @param api API名称，如 "base.broadcastTx"
     * @param params 操作参数
     */
    public FapiResponse operation(String api, Object params) {
        return request(FapiRequest.operation(api, params));
    }
    
    /**
     * 发送操作类请求（带费用限制）
     * @param api API名称，如 "base.broadcastTx"
     * @param params 操作参数
     * @param maxCost 最大费用（聪），超过此限制服务端将拒绝请求
     */
    public FapiResponse operation(String api, Object params, long maxCost) {
        return request(FapiRequest.operation(api, params).withMaxCost(maxCost));
    }
    
    /**
     * 发送简单请求（无参数）
     * @param api API名称，如 "base.health"
     */
    public FapiResponse simple(String api) {
        return request(FapiRequest.simple(api));
    }
    
    /**
     * 发送简单请求（带费用限制）
     * @param api API名称，如 "base.health"
     * @param maxCost 最大费用（聪），超过此限制服务端将拒绝请求
     */
    public FapiResponse simple(String api, long maxCost) {
        return request(FapiRequest.simple(api).withMaxCost(maxCost));
    }
    
    /**
     * 发送带二进制数据的请求（使用统一协议）
     * @param fapiRequest FAPI请求
     * @param binaryData 二进制数据
     * @return UnifiedResponse，包含响应和可选的二进制数据
     */
    public UnifiedResponse requestWithBinaryData(FapiRequest fapiRequest, byte[] binaryData) {
        try {
            if (balanceVerifier != null && balanceVerifier.isStopped()) {
                lastError = new IllegalStateException("Balance verification stopped due to drift");
                FapiResponse errorResp = buildErrorResponse(403, "Balance verification stopped");
                this.lastResponse = errorResp;
                return new UnifiedResponse(errorResp, null);
            }
            
            // 自动填充 via（消费渠道）
            if (fapiRequest.getVia() == null && via != null) {
                fapiRequest.setVia(via);
            }
            
            // 使用统一编码格式编码请求（包含二进制数据）
            byte[] requestData = UnifiedCodec.encodeRequest(fapiRequest, binaryData);
            CompletableFuture<ResponseMessage> future = fudpNode.request(servicePeerId, serviceSid, requestData);
            
            ResponseMessage response = future.get(requestTimeoutSeconds, TimeUnit.SECONDS);
            
            if (response.getStatusCode() != ResponseMessage.STATUS_SUCCESS) {
                this.lastError = new IOException("Request failed with status: " + response.getStatusCode());
                FapiResponse errorResp = buildErrorResponse(response.getStatusCode(),
                    response.getData() != null ? new String(response.getData()) : "Unknown error");
                this.lastResponse = errorResp;
                return new UnifiedResponse(errorResp, null);
            }
            
            // 使用统一编码格式解析响应（包含可能的二进制数据）
            UnifiedResponse unifiedResponse = UnifiedCodec.decodeResponse(response.getData());
            updateBalanceFromResponse(unifiedResponse.response());
            this.lastResponse = unifiedResponse.response();
            this.lastError = null;
            
            return unifiedResponse;
            
        } catch (TimeoutException e) {
            this.lastError = e;
            FapiResponse errorResp = buildErrorResponse(408, "Request timeout after " + requestTimeoutSeconds + "s");
            this.lastResponse = errorResp;
            log.warn("FAPI binary request timeout ({}s): api={}", requestTimeoutSeconds, 
                    fapiRequest != null ? fapiRequest.getApi() : "null");
            return new UnifiedResponse(errorResp, null);
        } catch (Exception e) {
            this.lastError = e;
            FapiResponse errorResp = buildErrorResponse(500, e.getMessage());
            this.lastResponse = errorResp;
            log.error("Error sending FAPI request with binary data: api={}", 
                    fapiRequest != null ? fapiRequest.getApi() : "null", e);
            return new UnifiedResponse(errorResp, null);
        }
    }
    
    /**
     * 解析统一协议响应
     * 兼容统一协议和纯JSON格式
     */
    private FapiResponse parseUnifiedResponse(byte[] data) {
        if (data == null || data.length == 0) {
            return buildErrorResponse(500, "Empty response");
        }
        
        // 尝试使用统一协议解析
        if (UnifiedCodec.isUnifiedProtocol(data)) {
            try {
                UnifiedResponse unified = UnifiedCodec.decodeResponse(data);
                return unified.response();
            } catch (Exception e) {
                log.debug("Failed to parse as unified protocol, falling back to JSON: {}", e.getMessage());
            }
        }
        
        // 回退到纯JSON解析
        try {
            String responseJson = new String(data, StandardCharsets.UTF_8);
            return JsonUtils.fromJson(responseJson, FapiResponse.class);
        } catch (Exception e) {
            log.error("Failed to parse response: {}", e.getMessage());
            return buildErrorResponse(500, "Failed to parse response");
        }
    }
    
    /**
     * 根据实体名和 ID 列表查询
     */
    public <T> Map<String, T> entityByIds(String entityName, Class<T> clazz, String... ids) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEntity(entityName);
        fcdsl.addIds(ids);
        
        FapiResponse response = query("base.getByIds", fcdsl);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        return ObjectUtils.objectToMap(response.getData(), String.class, clazz);
    }

    /**
     * Look up Freer records by FIDs via BASE component.
     * Use this to discover a target's home.ROAD or home.MAP before calling roadRelay().
     *
     * @param fids One or more FIDs to look up
     * @return Map of FID -> Freer, or null on error
     */
    public Map<String, Freer> freerByIds(String... fids) {
        return entityByIds(constants.IndicesNames.FREER, Freer.class, fids);
    }

    /**
     * 根据实体名搜索
     */
    public <T> List<T> entitySearch(String entityName, Fcdsl fcdsl, Class<T> clazz) {
        fcdsl.setEntity(entityName.toLowerCase());
        FapiResponse response = query("base.search", fcdsl);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }

        return ObjectUtils.objectToList(response.getData(), clazz);
    }
    
    // ========== Block 相关方法 ==========
    
    public Block bestBlock() {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEntity(IndicesNames.BLOCK);
        fcdsl.addSort(FieldNames.HEIGHT, Values.DESC);
        fcdsl.addSize(1);
        
        List<Block> blocks = entitySearch(IndicesNames.BLOCK, fcdsl, Block.class);
        return blocks != null && !blocks.isEmpty() ? blocks.get(0) : null;
    }
    
    public Long bestHeight() {
        Block block = bestBlock();
        return block != null ? block.getHeight() : null;
    }

    /**
     * 获取链信息，可选指定高度
     */
    public FchChainInfo chainInfo() {
        return chainInfo(null);
    }

    public FchChainInfo chainInfo(Long height) {
        Map<String, Object> params = new HashMap<>();
        if (height != null) {
            params.put(FieldNames.HEIGHT, height);
        }

        FapiResponse response = operation("base.chainInfo", params);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }

        return ObjectUtils.objectToClass(response.getData(), FchChainInfo.class);
    }

    public Map<Long, Long> blockTimeHistory(Long startTime, Long endTime, Integer count) {
        Map<String, Object> params = buildHistoryParams(startTime, endTime, count);
        return sendHistoryRequest("base.blockTimeHistory", params, Long.class, Long.class);
    }

    public Map<Long, String> difficultyHistory(Long startTime, Long endTime, Integer count) {
        Map<String, Object> params = buildHistoryParams(startTime, endTime, count);
        return sendHistoryRequest("base.difficultyHistory", params, Long.class, String.class);
    }

    public Map<Long, String> hashRateHistory(Long startTime, Long endTime, Integer count) {
        Map<String, Object> params = buildHistoryParams(startTime, endTime, count);
        return sendHistoryRequest("base.hashRateHistory", params, Long.class, String.class);
    }
    
    /**
     * 获取所有实体的列表及其文档数量
     * @return Map<String, String> 实体名 -> 文档数量
     */
    public Map<String, String> totals() {
        FapiResponse response = simple("base.totals");
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        
        return ObjectUtils.objectToMap(response.getData(), String.class, String.class);
    }
    
    /**
     * 健康检查
     */
    public Map<String, Object> health() {
        FapiResponse response = simple("base.health");
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        
        return ObjectUtils.objectToMap(response.getData(), String.class, Object.class);
    }
    
    // ========== Wallet APIs ==========
    
    /**
     * 根据 FID 列表获取余额
     * @param fids FID 列表
     * @return Map<FID, Balance> FID -> 余额（单位：satoshi）
     */
    public Map<String, Long> balanceByIds(String... fids) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addIds(fids);
        
        FapiResponse response = query("base.balanceByIds", fcdsl);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        
        return ObjectUtils.objectToMap(response.getData(), String.class, Long.class);
    }
    
    /**
     * 广播交易
     * @param rawTx 原始交易十六进制
     * @return 交易ID
     */
    public String broadcastTx(String rawTx) {
        Map<String, Object> params = new HashMap<>();
        params.put("rawTx", rawTx);
        
        FapiResponse response = operation("base.broadcastTx", params);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            if(response!=null)
                return response.getMessage();
            else return null;
        }
        return response.getData().toString();
    }
    
    /**
     * 解码交易
     * @param rawTx 原始交易十六进制
     * @return 解码后的交易对象
     */
    public Object decodeTx(String rawTx) {
        Map<String, Object> params = new HashMap<>();
        params.put("rawTx", rawTx);
        
        FapiResponse response = operation("base.decodeTx", params);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        
        return response.getData();
    }
    
    /**
     * 估算手续费
     * @param nBlocks 区块数（可选，如果指定则使用 estimatesmartfee，否则使用 estimatefee）
     * @return 手续费率（单位：FCH/KB）
     */
    public Double estimateFee(Integer nBlocks) {
        Map<String, Object> params = new HashMap<>();
        if (nBlocks != null && nBlocks > 0) {
            params.put("nBlocks", nBlocks);
        }
        
        FapiResponse response = operation("base.estimateFee", params);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        
        Object data = response.getData();
        if (data instanceof Number) {
            return ((Number) data).doubleValue();
        }
        return null;
    }
    
    /**
     * 估算手续费（使用默认参数，不指定区块数）
     * @return 手续费率（单位：FCH/KB）
     */
    public Double estimateFee() {
        return estimateFee(null);
    }
    
    /**
     * 获取有效的 UTXO（简单查询）
     * @param fcdsl FCDSL 查询条件
     * @return Cash 列表
     */
    public List<Cash> cashValid(Fcdsl fcdsl) {
        FapiResponse response = query("base.cashValid", fcdsl);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        
        return ObjectUtils.objectToList(response.getData(), Cash.class);
    }
    
    /**
     * 获取有效的 UTXO（用于交易）
     * @param fid FID
     * @param amount 需要的金额（单位：FCH，可为null）
     * @param cd 需要的 CD（可为null）
     * @param sinceHeight 起始高度（可为null）
     * @param outputSize 输出数量
     * @param msgSize 消息大小
     * @return Cash 列表
     */
    public List<Cash> cashValid(String fid, Double amount, Long cd, Long sinceHeight, 
                                Integer outputSize, Integer msgSize) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addIds(fid);
        
        Map<String, Object> params = new HashMap<>();
        params.put("fid", fid);
        if (amount != null) params.put(FieldNames.AMOUNT, amount);
        if (cd != null) params.put(FieldNames.CD, cd);
        if (sinceHeight != null) params.put(FieldNames.SINCE_HEIGHT, sinceHeight);
        if (outputSize != null) params.put("outputSize", outputSize);
        if (msgSize != null) params.put("msgSize", msgSize);
        
        FapiRequest request = FapiRequest.query("base.cashValid", fcdsl);
        request.setParams(params);
        
        FapiResponse response = request(request);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        
        return ObjectUtils.objectToList(response.getData(), Cash.class);
    }
    
    /**
     * 获取 UTXO
     * @param address FID 地址
     * @param amount 需要的金额（单位：FCH，可为null）
     * @param cd 需要的 CD（可为null）
     * @return Utxo 列表
     */
    public List<data.apipData.Utxo> getUtxo(String address, Double amount, Long cd) {
        Map<String, Object> params = new HashMap<>();
        params.put(FieldNames.ADDRESS, address);
        if (amount != null) params.put(FieldNames.AMOUNT, amount);
        if (cd != null) params.put(FieldNames.CD, cd);
        
        FapiResponse response = operation("base.getUtxo", params);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        
        return ObjectUtils.objectToList(response.getData(), data.apipData.Utxo.class);
    }
    
    // ========== Mempool APIs ==========
    
    /**
     * 获取未确认交易信息
     * @param fids FID 列表
     * @return Map<FID, UnconfirmedInfo>
     */
    public Map<String, data.apipData.UnconfirmedInfo> unconfirmed(String... fids) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addIds(fids);
        
        FapiResponse response = query("base.unconfirmed", fcdsl);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        
        return ObjectUtils.objectToMap(response.getData(), String.class, data.apipData.UnconfirmedInfo.class);
    }
    
    /**
     * 获取未确认的 Cash
     * @param fids FID 列表（可选）
     * @return Map<FID, List<Cash>>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, List<Cash>> unconfirmedCashes(String... fids) {
        Fcdsl fcdsl = new Fcdsl();
        if (fids != null && fids.length > 0) {
            fcdsl.addIds(fids);
        }
        
        FapiResponse response = query("base.unconfirmedCashes", fcdsl);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        
        // 类型安全转换：实际返回的是 Map<String, List<Map>>，需要后续客户端自行转换
        Map rawMap = ObjectUtils.objectToMap(response.getData(), String.class, List.class);
        return (Map<String, List<Cash>>) rawMap;
    }
    
    // ========== MAP APIs (FID-to-Address Mapping) ==========
    
    /**
     * Register self on MAP service.
     * Zero-parameter: all info (FID, pubkey, IP:port) is extracted from FUDP connection.
     * Should be called every ~25 seconds to maintain NAT mappings.
     * 
     * @return MapEntry with registration info, or null on error
     */
    public fapi.components.map.MapEntry mapRegister() {
        FapiResponse response = simple("map.register");
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        return ObjectUtils.objectToClass(response.getData(), fapi.components.map.MapEntry.class);
    }
    
    /**
     * Find a registered FID on MAP service.
     * Returns the FID's pubkey, observed IP, and port.
     * 
     * @param fid The FID to look up
     * @return MapEntry with address info, or null if not found
     */
    public fapi.components.map.MapEntry mapFind(String fid) {
        if (fid == null || fid.isEmpty()) {
            lastError = new IllegalArgumentException("FID is required");
            return null;
        }
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addIds(fid);
        
        FapiResponse response = query("map.find", fcdsl);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        return ObjectUtils.objectToClass(response.getData(), fapi.components.map.MapEntry.class);
    }
    
    /**
     * Unregister self from MAP service.
     * 
     * @return true if successful, false otherwise
     */
    public boolean mapUnregister() {
        FapiResponse response = simple("map.unregister");
        return response != null && response.getCode() == 0;
    }
    
    /**
     * List all registered entries on MAP service.
     * 
     * @return List of MapEntry, or null on error
     */
    public List<fapi.components.map.MapEntry> mapList() {
        FapiResponse response = simple("map.list");
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        return ObjectUtils.objectToList(response.getData(), fapi.components.map.MapEntry.class);
    }
    
    /**
     * Get MAP service statistics.
     * 
     * @return Map with stats (totalEntries, freshEntries, staleEntries, etc.)
     */
    public Map<String, Object> mapStats() {
        FapiResponse response = simple("map.stats");
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        return ObjectUtils.objectToMap(response.getData(), String.class, Object.class);
    }
    
    /**
     * Start a background keepalive task for MAP registration.
     * Calls mapRegister() every intervalSeconds to maintain NAT mappings.
     * 
     * @param intervalSeconds Interval between registrations (recommended: 25)
     * @return ScheduledFuture that can be cancelled to stop the keepalive
     */
    public java.util.concurrent.ScheduledFuture<?> startMapKeepalive(int intervalSeconds) {
        java.util.concurrent.ScheduledExecutorService scheduler = 
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        
        return scheduler.scheduleAtFixedRate(() -> {
            try {
                fapi.components.map.MapEntry entry = mapRegister();
                if (entry != null) {
                    log.debug("MAP keepalive: registered {}:{}", entry.getObservedIp(), entry.getObservedPort());
                } else {
                    log.warn("MAP keepalive: registration failed");
                }
            } catch (Exception e) {
                log.error("MAP keepalive error: {}", e.getMessage());
            }
        }, 0, intervalSeconds, java.util.concurrent.TimeUnit.SECONDS);
    }
    
    // ========== ROAD APIs (Data Relay) ==========
    
    /**
     * Relay data to multiple target FIDs using ROAD service.
     *
     * @param targetFids List of destination FIDs
     * @param data The data to relay (can be encrypted or plain)
     * @return RoadRelayResult with success status and results for each target, or null on error
     */
    public RoadRelayResult roadRelay(List<String> targetFids, byte[] data) {
        return roadRelay(targetFids, data, 0, null);
    }
    
    /**
     * Relay data to a single target FID (convenience method).
     *
     * @param targetFid The destination FID
     * @param data The data to relay
     * @return RoadRelayResult with success status and fee info, or null on error
     */
    public RoadRelayResult roadRelay(String targetFid, byte[] data) {
        return roadRelay(List.of(targetFid), data, 0, null);
    }
    
    /**
     * Relay data to a single target FID with max cost protection.
     *
     * @param targetFid The destination FID
     * @param data The data to relay
     * @param maxCost Maximum satoshi willing to pay (0 = no limit)
     * @return RoadRelayResult with success status and fee info, or null on error
     */
    public RoadRelayResult roadRelay(String targetFid, byte[] data, long maxCost) {
        return roadRelay(List.of(targetFid), data, maxCost, null);
    }

    /**
     * Relay data to a single target FID, specifying the remote ROAD URL.
     * The sender discovers targetRoad from the target's freer.home.ROAD field.
     *
     * @param targetFid The destination FID
     * @param data The data to relay
     * @param targetRoad URL of the ROAD service where targetFid is registered (from freer.home.ROAD)
     * @return RoadRelayResult with success status and fee info, or null on error
     */
    public RoadRelayResult roadRelay(String targetFid, byte[] data, String targetRoad) {
        return roadRelay(List.of(targetFid), data, 0, targetRoad);
    }

    /**
     * Relay data to multiple target FIDs with max cost protection (no target ROAD).
     *
     * @param targetFids List of destination FIDs
     * @param data The data to relay
     * @param maxCost Maximum satoshi willing to pay for all relays (0 = no limit)
     * @return RoadRelayResult with success status and results for each target, or null on error
     */
    public RoadRelayResult roadRelay(List<String> targetFids, byte[] data, long maxCost) {
        return roadRelay(targetFids, data, maxCost, null);
    }

    /**
     * Relay data to multiple target FIDs with max cost protection and optional target ROAD URL.
     * <p>
     * If targetRoad is provided, the ROAD server will forward to that URL for targets
     * not found in the local MAP. The sender discovers targetRoad from the target's
     * freer.home.ROAD field (via freerByIds()).
     *
     * @param targetFids List of destination FIDs
     * @param data The data to relay
     * @param maxCost Maximum satoshi willing to pay for all relays (0 = no limit)
     * @param targetRoad URL of the remote ROAD for non-local targets (null = local only)
     * @return RoadRelayResult with success status and results for each target, or null on error
     */
    public RoadRelayResult roadRelay(List<String> targetFids, byte[] data, long maxCost, String targetRoad) {
        if (targetFids == null || targetFids.isEmpty()) {
            lastError = new IllegalArgumentException("targetFids is required");
            return null;
        }
        if (data == null || data.length == 0) {
            lastError = new IllegalArgumentException("data is required");
            return null;
        }
        
        try {
            Map<String, Object> params = new HashMap<>();
            params.put(FieldNames.TARGET_FIDS, targetFids);
            if (maxCost > 0) {
                params.put(FieldNames.MAX_COST, maxCost);
            }
            if (targetRoad != null && !targetRoad.isEmpty()
                    && !targetRoad.equals(serverUrl)) {
                params.put(FieldNames.TARGET_ROAD, targetRoad);
            }
            
            FapiRequest fapiRequest = FapiRequest.binaryOperation("road.relay", params, data.length, null);
            
            // Send request
            UnifiedResponse unified = requestWithBinaryData(fapiRequest, data);
            
            if (unified == null || unified.response() == null) {
                lastError = new RuntimeException("No response from server");
                return null;
            }
            
            FapiResponse resp = unified.response();
            
            // Parse response data
            @SuppressWarnings("unchecked")
            Map<String, Object> respData = resp.getData() != null 
                ? ObjectUtils.objectToMap(resp.getData(), String.class, Object.class)
                : new HashMap<>();
            
            long chargedIn = respData.containsKey(FieldNames.CHARGED_IN) 
                ? ((Number) respData.get(FieldNames.CHARGED_IN)).longValue() : 0;
            long chargedOut = respData.containsKey(FieldNames.CHARGED_OUT) 
                ? ((Number) respData.get(FieldNames.CHARGED_OUT)).longValue() : 0;
            int successCount = respData.containsKey("successCount") 
                ? ((Number) respData.get("successCount")).intValue() : 0;
            int failCount = respData.containsKey("failCount") 
                ? ((Number) respData.get("failCount")).intValue() : 0;
            int totalTargets = respData.containsKey("totalTargets") 
                ? ((Number) respData.get("totalTargets")).intValue() : targetFids.size();
            
            // Parse per-target results
            @SuppressWarnings("unchecked")
            Map<String, Object> relayResultsRaw = (Map<String, Object>) respData.get(FieldNames.RELAY_RESULTS);
            Map<String, TargetRelayResult> relayResults = new LinkedHashMap<>();
            
            if (relayResultsRaw != null) {
                for (Map.Entry<String, Object> entry : relayResultsRaw.entrySet()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> targetData = (Map<String, Object>) entry.getValue();
                    boolean success = Boolean.TRUE.equals(targetData.get("success"));
                    int code = targetData.containsKey("code") 
                        ? ((Number) targetData.get("code")).intValue() : 0;
                    String message = (String) targetData.get("message");
                    long targetChargedIn = targetData.containsKey(FieldNames.CHARGED_IN) 
                        ? ((Number) targetData.get(FieldNames.CHARGED_IN)).longValue() : 0;
                    long targetChargedOut = targetData.containsKey(FieldNames.CHARGED_OUT) 
                        ? ((Number) targetData.get(FieldNames.CHARGED_OUT)).longValue() : 0;
                    boolean chainRelayed = Boolean.TRUE.equals(targetData.get(FieldNames.CHAIN_RELAYED));
                    String relayedVia = (String) targetData.get(FieldNames.RELAYED_VIA);
                    
                    relayResults.put(entry.getKey(), new TargetRelayResult(
                        success, code, message, targetChargedIn, targetChargedOut, chainRelayed, relayedVia));
                }
            }
            
            return new RoadRelayResult(
                resp.isSuccess(),
                resp.getCode(),
                resp.getMessage(),
                chargedIn,
                chargedOut,
                successCount,
                failCount,
                totalTargets,
                relayResults
            );
            
        } catch (Exception e) {
            lastError = e;
            log.error("Failed to relay data: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Get ROAD service statistics.
     * 
     * @return Map with stats (totalRelays, successfulRelays, bytesIn, bytesOut, etc.)
     */
    public Map<String, Object> roadStats() {
        FapiResponse response = simple("road.stats");
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        return ObjectUtils.objectToMap(response.getData(), String.class, Object.class);
    }
    
    /**
     * Result of relay to a single target FID.
     */
    public record TargetRelayResult(
        boolean success,
        int code,
        String message,
        long chargedIn,
        long chargedOut,
        boolean chainRelayed,
        String relayedVia
    ) {
        public long totalCharged() {
            return chargedIn + chargedOut;
        }
    }
    
    /**
     * Result of roadRelay operation with multi-target support.
     */
    public record RoadRelayResult(
        boolean success,
        int code,
        String message,
        long chargedIn,
        long chargedOut,
        int successCount,
        int failCount,
        int totalTargets,
        Map<String, TargetRelayResult> relayResults
    ) {
        public long totalCharged() {
            return chargedIn + chargedOut;
        }
        
        /**
         * Check if all targets were successfully relayed.
         */
        public boolean allSucceeded() {
            return successCount == totalTargets && failCount == 0;
        }
        
        /**
         * Get result for a specific target FID.
         */
        public TargetRelayResult getResult(String targetFid) {
            return relayResults != null ? relayResults.get(targetFid) : null;
        }
    }
    
    // ========== Disk APIs (Unified Protocol) ==========
    
    /**
     * Store a file on DISK with expiration (temporary storage).
     * Uses unified protocol for efficient transfer.
     * 
     * @param file The file to upload
     * @return DiskItem with did and metadata, or null on error
     */
    public data.fcData.DiskItem diskPut(java.io.File file) {
        return diskPut(file, null, null);
    }
    
    /**
     * Store a file on DISK with expiration (temporary storage).
     * Uses unified protocol for efficient transfer.
     * 
     * @param file The file to upload
     * @param dataLifeDays Optional: number of days to store (null = use server default)
     * @return DiskItem with did and metadata, or null on error
     */
    public data.fcData.DiskItem diskPut(java.io.File file, Long dataLifeDays) {
        return diskPut(file, dataLifeDays, null);
    }
    
    /**
     * Store a file on DISK with expiration and progress tracking.
     * 
     * @param file The file to upload
     * @param dataLifeDays Optional: number of days to store (null = use server default)
     * @param progressCallback Optional: callback receiving cumulative bytes transferred
     * @return DiskItem with did and metadata, or null on error
     */
    public data.fcData.DiskItem diskPut(java.io.File file, Long dataLifeDays, LongConsumer progressCallback) {
        return storeFile("disk.put", file, dataLifeDays, progressCallback);
    }
    
    /**
     * Store a file on DISK permanently (no expiration).
     * Uses unified protocol for efficient transfer.
     * 
     * @param file The file to upload permanently
     * @return DiskItem with did and metadata, or null on error
     */
    public data.fcData.DiskItem diskCarve(java.io.File file) {
        return diskCarve(file, null);
    }
    
    /**
     * Store a file on DISK permanently with progress tracking.
     * 
     * @param file The file to upload permanently
     * @param progressCallback Optional: callback receiving cumulative bytes transferred
     * @return DiskItem with did and metadata, or null on error
     */
    public data.fcData.DiskItem diskCarve(java.io.File file, LongConsumer progressCallback) {
        return storeFile("disk.carve", file, null, progressCallback);
    }
    
    /**
     * Internal file storage method for PUT/CARVE.
     * Uses streaming upload to avoid loading entire file into memory:
     * 1. Computes SHA256x2 hash incrementally (first pass over file)
     * 2. Streams the file content directly through FUDP transport (second pass)
     *
     * For small files (< 1MB), falls back to in-memory upload for simplicity.
     *
     * @param api              API name ("disk.put" or "disk.carve")
     * @param file             The file to upload
     * @param dataLifeDays     Expiration in days (null for default / permanent)
     * @param progressCallback Optional callback receiving cumulative bytes sent
     */
    private data.fcData.DiskItem storeFile(String api, java.io.File file, Long dataLifeDays,
                                           LongConsumer progressCallback) {
        if (file == null || !file.exists() || !file.isFile()) {
            lastError = new IllegalArgumentException("File does not exist or is not a regular file");
            return null;
        }
        
        try {
            long fileSize = file.length();
            
            // Build request params
            Map<String, Object> params = new HashMap<>();
            if (dataLifeDays != null) {
                params.put("dataLifeDays", dataLifeDays);
            }
            
            // For small files (< 1MB), use the simple in-memory path
            if (fileSize < 1024 * 1024) {
                data.fcData.DiskItem result = storeFileInMemory(api, file, params);
                // Report completion for small files
                if (result != null && progressCallback != null) {
                    progressCallback.accept(fileSize);
                }
                return result;
            }
            
            // === Streaming upload path (avoids loading entire file into memory) ===
            
            // Pass 1: Compute file hash incrementally
            byte[] hashBytes;
            try (java.io.FileInputStream hashStream = new java.io.FileInputStream(file)) {
                hashBytes = core.crypto.Hash.sha256x2FromStream(hashStream);
            }
            if (hashBytes == null) {
                lastError = new RuntimeException("Failed to compute file hash");
                return null;
            }
            String dataHash = utils.Hex.toHex(hashBytes);
            
            // Create binary operation request (with dataSize and dataHash)
            FapiRequest fapiRequest = FapiRequest.binaryOperation(api, params, fileSize, dataHash);
            
            // Auto-fill via
            if (fapiRequest.getVia() == null && via != null) {
                fapiRequest.setVia(via);
            }
            
            // Encode the request header only (without binary data)
            byte[] headerData = UnifiedCodec.encodeRequestHeaderOnly(fapiRequest);
            
            // Pass 2: Stream file content through FUDP transport with progress tracking
            java.util.concurrent.CompletableFuture<fudp.message.ResponseMessage> future;
            try (java.io.FileInputStream rawStream = new java.io.FileInputStream(file)) {
                java.io.InputStream fileStream;
                if (progressCallback != null) {
                    fileStream = new ProgressInputStream(rawStream, progressCallback);
                } else {
                    fileStream = rawStream;
                }
                future = fudpNode.requestWithStream(
                    servicePeerId, serviceSid, headerData, fileStream, fileSize);
            }
            
            fudp.message.ResponseMessage response = future.get(requestTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
            
            if (response.getStatusCode() != fudp.message.ResponseMessage.STATUS_SUCCESS) {
                this.lastError = new IOException("Request failed with status: " + response.getStatusCode());
                FapiResponse errorResp = buildErrorResponse(response.getStatusCode(),
                    response.getData() != null ? new String(response.getData()) : "Unknown error");
                this.lastResponse = errorResp;
                return null;
            }
            
            // Decode response
            UnifiedResponse unified = UnifiedCodec.decodeResponse(response.getData());
            updateBalanceFromResponse(unified.response());
            this.lastResponse = unified.response();
            this.lastError = null;
            
            if (unified.response().getCode() != 0) {
                lastError = new RuntimeException("Server error: " + unified.response().getMessage());
                return null;
            }
            
            return utils.ObjectUtils.objectToClass(unified.response().getData(), data.fcData.DiskItem.class);
            
        } catch (java.util.concurrent.TimeoutException e) {
            lastError = e;
            log.warn("FAPI streaming upload timeout ({}s): api={}", requestTimeoutSeconds, api);
            return null;
        } catch (Exception e) {
            lastError = e;
            log.error("Failed to store file: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Simple in-memory file upload for small files (< 1MB).
     */
    private data.fcData.DiskItem storeFileInMemory(String api, java.io.File file, Map<String, Object> params) throws Exception {
        byte[] fileContent = java.nio.file.Files.readAllBytes(file.toPath());
        
        // Compute file hash
        byte[] hashBytes = core.crypto.Hash.sha256x2(fileContent);
        String dataHash = utils.BytesUtils.bytesToHexStringBE(hashBytes);
        
        // Create binary operation request
        FapiRequest fapiRequest = FapiRequest.binaryOperation(api, params, fileContent.length, dataHash);
        
        // Send request
        UnifiedResponse unified = requestWithBinaryData(fapiRequest, fileContent);
        
        if (unified == null || unified.response() == null) {
            lastError = new RuntimeException("No response from server");
            return null;
        }
        
        if (unified.response().getCode() != 0) {
            lastError = new RuntimeException("Server error: " + unified.response().getMessage());
            return null;
        }
        
        return utils.ObjectUtils.objectToClass(unified.response().getData(), data.fcData.DiskItem.class);
    }
    
    /**
     * Download a file from DISK by ID and write it to the output file.
     * <p>
     * Note: The server streams the file content via FudpNode.respondWithStream(),
     * so the server does NOT load the entire file into memory. On the client side,
     * the FUDP transport layer currently reassembles the data into byte[]; we then
     * write it to the output file immediately. Future improvement: add receive-side
     * streaming to avoid the client-side byte[] buffer as well.
     * 
     * @param did SHA256x2 hash of content (64 hex chars)
     * @param outputFile The file to write the downloaded content to
     * @return DiskItem metadata if successful, or null on failure
     */
    public data.fcData.DiskItem diskGet(String did, java.io.File outputFile) {
        return diskGet(did, outputFile, null);
    }
    
    /**
     * Download a file from DISK by ID with progress tracking.
     * <p>
     * The progress callback receives cumulative bytes written to the output file.
     * Note: Due to FUDP transport reassembling the entire response in memory first,
     * the progress reflects the file-write phase. For large transfers the network
     * receive phase dominates; a spinner is recommended while waiting for the response.
     * 
     * @param did              SHA256x2 hash of content (64 hex chars)
     * @param outputFile       The file to write the downloaded content to
     * @param progressCallback Optional: callback receiving cumulative bytes written
     * @return DiskItem metadata if successful, or null on failure
     */
    public data.fcData.DiskItem diskGet(String did, java.io.File outputFile,
                                        LongConsumer progressCallback) {
        if (did == null || did.length() != 64) {
            lastError = new IllegalArgumentException("Invalid ID: must be 64 hex characters");
            return null;
        }
        
        try {
            // Build request params
            Map<String, Object> params = new HashMap<>();
            params.put(FieldNames.ID, did);
            
            FapiRequest fapiRequest = FapiRequest.operation("disk.get", params);
            
            // Send request and get unified response
            UnifiedResponse unified = requestWithBinaryData(fapiRequest, null);
            
            if (unified == null || unified.response() == null) {
                lastError = new RuntimeException("No response from server");
                return null;
            }
            
            if (unified.response().getCode() != 0) {
                lastError = new RuntimeException("Server error: " + unified.response().getMessage());
                return null;
            }
            
            // Parse metadata
            data.fcData.DiskItem metadata = utils.ObjectUtils.objectToClass(
                unified.response().getData(), data.fcData.DiskItem.class);
            
            // Write binary data to output file with progress reporting
            byte[] content = unified.binaryData();
            if (content != null && content.length > 0) {
                if (outputFile.getParentFile() != null) {
                    outputFile.getParentFile().mkdirs();
                }
                
                if (progressCallback != null) {
                    // Write in chunks and report progress
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile)) {
                        int chunkSize = 64 * 1024; // 64KB chunks
                        long written = 0;
                        int offset = 0;
                        while (offset < content.length) {
                            int len = Math.min(chunkSize, content.length - offset);
                            fos.write(content, offset, len);
                            written += len;
                            offset += len;
                            progressCallback.accept(written);
                        }
                    }
                } else {
                    java.nio.file.Files.write(outputFile.toPath(), content);
                }
            }
            
            return metadata;
            
        } catch (Exception e) {
            lastError = e;
            log.error("Failed to get file: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if file exists and get metadata (single ID convenience method).
     * Uses standard JSON protocol.
     * 
     * @param did SHA256x2 hash of content (64 hex chars)
     * @return DiskItem metadata, or null if not found
     */
    public data.fcData.DiskItem diskCheck(String did) {
        Map<String, data.fcData.DiskItem> results = diskCheck(List.of(did));
        if (results == null) return null;
        return results.get(did);  // null if not found
    }
    
    /**
     * Check if files exist and get metadata for a list of IDs.
     * Uses standard JSON protocol. Max 200 IDs per request.
     * 
     * @param dids List of SHA256x2 hashes (64 hex chars each)
     * @return Map of ID to DiskItem. Value is null if the file does not exist. Returns null on error.
     */
    public Map<String, data.fcData.DiskItem> diskCheck(List<String> dids) {
        if (dids == null || dids.isEmpty()) {
            lastError = new IllegalArgumentException("ID list cannot be empty");
            return null;
        }
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addIds(dids);
        
        FapiResponse response = query("disk.check", fcdsl);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        return ObjectUtils.objectToMap(response.getData(), String.class, data.fcData.DiskItem.class);
    }
    
    /**
     * List stored files with FCDSL query.
     * Uses standard JSON protocol.
     * 
     * @param fcdsl Query parameters (filter, sort, size, etc.)
     * @return List of DiskItem, or null on error
     */
    public List<data.fcData.DiskItem> diskList(Fcdsl fcdsl) {
        if (fcdsl == null) {
            fcdsl = new Fcdsl();
        }
        
        FapiResponse response = query("disk.list", fcdsl);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            if (response != null && response.getCode() != 0) {
                log.warn("disk.list failed: code={}, message={}", response.getCode(), response.getMessage());
            }
            return null;
        }
        List<data.fcData.DiskItem> result = ObjectUtils.objectToList(response.getData(), data.fcData.DiskItem.class);
        if (result == null) {
            log.warn("disk.list: response data conversion failed. data type={}", 
                    response.getData().getClass().getSimpleName());
        }
        return result;
    }

    // ========== DOCK APIs (Store-and-Forward Messaging) ==========
    
    /**
     * Store data for recipients (store-and-forward messaging).
     * Sender pays: ingress fee + storage fee (size * days * pricePerKBDay).
     * Uses unified protocol for efficient transfer.
     * 
     * @param data The data to store
     * @param recipients List of recipient FIDs
     * @return DockItem with dockId and metadata, or null on error
     */
    public DockItem dockPut(byte[] data, List<String> recipients) {
        return dockPut(data, recipients, null);
    }
    
    /**
     * Store data for recipients with optional maxDays.
     * 
     * @param data The data to store
     * @param recipients List of recipient FIDs
     * @param maxDays Optional: number of days to store (null = use server default)
     * @return DockItem with dockId and metadata, or null on error
     */
    public DockItem dockPut(byte[] data, List<String> recipients, Integer maxDays) {
        return dockPut(data, recipients, maxDays, null);
    }
    
    /**
     * Store data for recipients with dataType and optional forwarding.
     * 
     * @param data The data to store
     * @param recipients List of recipient FIDs
     * @param maxDays Optional: number of days to store (null = use server default)
     * @param targetDockUrl Optional: URL of target DOCK server to forward to
     * @param dataType Optional: type of data (e.g., "IM", "HAT", "SYMKEY")
     * @return DockItem with dockId and metadata, or null on error
     */
    public DockItem dockPut(byte[] data, List<String> recipients, Integer maxDays, String targetDockUrl, String dataType) {
        if (data == null || data.length == 0) {
            lastError = new IllegalArgumentException("Data content cannot be empty");
            return null;
        }
        if (recipients == null || recipients.isEmpty()) {
            lastError = new IllegalArgumentException("Recipients list cannot be empty");
            return null;
        }
        
        try {
            Map<String, Object> params = new HashMap<>();
            params.put(FieldNames.RECIPIENTS, recipients);
            if (maxDays != null) {
                params.put(FieldNames.MAX_DAYS, maxDays);
            }
            if (targetDockUrl != null && !targetDockUrl.isEmpty()) {
                params.put(FieldNames.TARGET_DOCK_URL, targetDockUrl);
            }
            if (dataType != null && !dataType.isEmpty()) {
                params.put(FieldNames.DATA_TYPE, dataType);
            }
            
            byte[] hashBytes = core.crypto.Hash.sha256(data);
            String dataHash = utils.BytesUtils.bytesToHexStringBE(hashBytes);
            
            FapiRequest fapiRequest = FapiRequest.binaryOperation("dock.put", params, data.length, dataHash);
            
            UnifiedResponse unified = requestWithBinaryData(fapiRequest, data);
            
            if (unified == null || unified.response() == null) {
                lastError = new RuntimeException("No response from server");
                return null;
            }
            
            if (unified.response().getCode() != 0) {
                lastError = new RuntimeException("Server error: " + unified.response().getMessage());
                return null;
            }
            
            return utils.ObjectUtils.objectToClass(unified.response().getData(), DockItem.class);
            
        } catch (Exception e) {
            lastError = e;
            log.error("Failed to store data in DOCK: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Store data for recipients with optional forwarding to another DOCK.
     * Sender pays: ingress fee + storage fee (size * days * pricePerKBDay).
     * 
     * @param data The data to store
     * @param recipients List of recipient FIDs
     * @param maxDays Optional: number of days to store (null = use server default)
     * @param targetDockUrl Optional: URL of target DOCK server to forward to (null = store on connected DOCK)
     * @return DockItem with dockId and metadata (including fee breakdown if forwarded), or null on error
     */
    public DockItem dockPut(byte[] data, List<String> recipients, Integer maxDays, String targetDockUrl) {
        return dockPut(data, recipients, maxDays, targetDockUrl, null);
    }
    
    /**
     * Retrieve data by dockId.
     * Recipient pays egress fee.
     * Uses unified protocol for efficient download.
     * 
     * @param dockId The dock item ID
     * @return File content bytes, or null if not found
     */
    public byte[] dockGet(String dockId) {
        DockGetResult result = dockGetWithMetadata(dockId, null);
        return result != null ? result.content() : null;
    }
    
    /**
     * Retrieve data by dockId on behalf of a team/group/room.
     * Recipient pays egress fee.
     * 
     * @param dockId The dock item ID
     * @param recipientId Optional: team/group/room ID to retrieve on behalf of
     * @return File content bytes, or null if not found
     */
    public byte[] dockGet(String dockId, String recipientId) {
        DockGetResult result = dockGetWithMetadata(dockId, recipientId);
        return result != null ? result.content() : null;
    }
    
    /**
     * Retrieve data with metadata.
     * Uses unified protocol for efficient download.
     * 
     * @param dockId The dock item ID
     * @return DockGetResult with metadata and content, or null if not found
     */
    public DockGetResult dockGetWithMetadata(String dockId) {
        return dockGetWithMetadata(dockId, null);
    }
    
    /**
     * Retrieve data with metadata on behalf of a team/group/room.
     * Uses unified protocol for efficient download.
     * 
     * @param dockId The dock item ID
     * @param recipientId Optional: team/group/room ID to retrieve on behalf of
     * @return DockGetResult with metadata and content, or null if not found
     */
    public DockGetResult dockGetWithMetadata(String dockId, String recipientId) {
        if (dockId == null || dockId.isEmpty()) {
            lastError = new IllegalArgumentException("dockId is required");
            return null;
        }
        
        try {
            // Build request params
            Map<String, Object> params = new HashMap<>();
            params.put(FieldNames.ID, dockId);
            if (recipientId != null && !recipientId.isEmpty()) {
                params.put("recipientId", recipientId);
            }
            
            FapiRequest fapiRequest = FapiRequest.operation("dock.get", params);
            
            // Send request and get unified response
            UnifiedResponse unified = requestWithBinaryData(fapiRequest, null);
            
            if (unified == null || unified.response() == null) {
                lastError = new RuntimeException("No response from server");
                return null;
            }
            
            if (unified.response().getCode() != 0) {
                lastError = new RuntimeException("Server error: " + unified.response().getMessage());
                return null;
            }
            
            // Parse metadata from response
            @SuppressWarnings("unchecked")
            Map<String, Object> respData = unified.response().getData() != null 
                ? ObjectUtils.objectToMap(unified.response().getData(), String.class, Object.class)
                : new HashMap<>();
            
            // Create a DockItem-like object from response data
            DockItem metadata = new DockItem();
            if (respData.containsKey(FieldNames.ID)) {
                metadata.setId(respData.get(FieldNames.ID).toString());
            }
            if (respData.containsKey(FieldNames.SENDER)) {
                metadata.setSender(respData.get(FieldNames.SENDER).toString());
            }
            if (respData.containsKey(FieldNames.SIZE)) {
                Object sizeObj = respData.get(FieldNames.SIZE);
                if (sizeObj instanceof Number) {
                    metadata.setSize(((Number) sizeObj).longValue());
                }
            }
            if (respData.containsKey(FieldNames.CREATE_TIME)) {
                Object timeObj = respData.get(FieldNames.CREATE_TIME);
                if (timeObj instanceof Number) {
                    metadata.setCreateTime(((Number) timeObj).longValue());
                }
            }
            if (respData.containsKey(FieldNames.EXPIRE_HEIGHT)) {
                Object heightObj = respData.get(FieldNames.EXPIRE_HEIGHT);
                if (heightObj instanceof Number) {
                    metadata.setExpireHeight(((Number) heightObj).longValue());
                }
            }
            // Recipients list from response (if available)
            if (respData.containsKey(FieldNames.RECIPIENTS)) {
                @SuppressWarnings("unchecked")
                List<String> recipients = (List<String>) respData.get(FieldNames.RECIPIENTS);
                metadata.setRecipients(recipients != null ? recipients : new ArrayList<>());
            }
            
            // Binary data is in unified.binaryData()
            return new DockGetResult(metadata, unified.binaryData());
            
        } catch (Exception e) {
            lastError = e;
            log.error("Failed to get data from DOCK: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * List items for the caller's FID.
     * Uses standard JSON protocol with FCDSL query.
     * 
     * @param fcdsl Query parameters (filter, sort, size, etc.)
     * @return List of DockItem, or null on error
     */
    public List<DockItem> dockList(Fcdsl fcdsl) {
        return dockList(fcdsl, null);
    }
    
    /**
     * List items for a specific recipient ID (FID, team, group, or room).
     * Uses standard JSON protocol with FCDSL query.
     * 
     * @param fcdsl Query parameters (filter, sort, size, etc.)
     * @param recipientId Optional: team/group/room ID to list items for (null = caller's FID)
     * @return List of DockItem, or null on error
     */
    public List<DockItem> dockList(Fcdsl fcdsl, String recipientId) {
        if (fcdsl == null) {
            fcdsl = new Fcdsl();
        }
        
        // Build params with recipientId if provided
        Map<String, Object> params = new HashMap<>();
        if (recipientId != null && !recipientId.isEmpty()) {
            params.put("recipientId", recipientId);
        }
        
        // Use operation with params and fcdsl
        FapiRequest fapiRequest = new FapiRequest();
        fapiRequest.setApi("dock.list");
        fapiRequest.setFcdsl(fcdsl);
        if (!params.isEmpty()) {
            fapiRequest.setParams(params);
        }
        
        FapiResponse response = request(fapiRequest);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        return ObjectUtils.objectToList(response.getData(), DockItem.class);
    }
    
    /**
     * Fetch dock items with inline Base64 data for multiple recipient IDs.
     * Supports pagination via fcdsl.after / response.last.
     * 
     * @param recipientIds List of recipient IDs (FID, team, group, room)
     * @param fcdsl Optional: query parameters (size, sort, after for pagination)
     * @return FapiResponse containing list of dock items with dataBase64 field
     */
    public FapiResponse dockFetch(List<String> recipientIds, Fcdsl fcdsl) {
        if (fcdsl == null) {
            fcdsl = new Fcdsl();
        }
        
        Map<String, Object> params = new HashMap<>();
        if (recipientIds != null && !recipientIds.isEmpty()) {
            params.put(FieldNames.RECIPIENT_IDS, recipientIds);
        }
        
        FapiRequest fapiRequest = new FapiRequest();
        fapiRequest.setApi("dock.fetch");
        fapiRequest.setFcdsl(fcdsl);
        if (!params.isEmpty()) {
            fapiRequest.setParams(params);
        }
        
        return request(fapiRequest);
    }
    
    /**
     * Check item status without downloading.
     * Uses standard JSON protocol.
     * 
     * @param dockId The dock item ID
     * @return DockItem metadata, or null if not found
     */
    public DockItem dockCheck(String dockId) {
        if (dockId == null || dockId.isEmpty()) {
            lastError = new IllegalArgumentException("dockId is required");
            return null;
        }
        
        Map<String, Object> params = new HashMap<>();
        params.put(FieldNames.ID, dockId);
        
        FapiResponse response = operation("dock.check", params);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        return ObjectUtils.objectToClass(response.getData(), DockItem.class);
    }
    
    /**
     * Delete item (sender only, partial refund for unused storage).
     * Uses standard JSON protocol.
     * 
     * @param dockId The dock item ID
     * @return Map with deletion result and refund info, or null on error
     */
    public Map<String, Object> dockDelete(String dockId) {
        if (dockId == null || dockId.isEmpty()) {
            lastError = new IllegalArgumentException("dockId is required");
            return null;
        }
        
        Map<String, Object> params = new HashMap<>();
        params.put(FieldNames.ID, dockId);
        
        FapiResponse response = operation("dock.delete", params);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        return ObjectUtils.objectToMap(response.getData(), String.class, Object.class);
    }
    
    /**
     * Extend TTL for an item (sender only, pays additional storage fee).
     * Uses standard JSON protocol.
     * 
     * @param dockId The dock item ID
     * @param extraDays Number of additional days to extend
     * @return Map with extension result and fee info, or null on error
     */
    public Map<String, Object> dockExtend(String dockId, Integer extraDays) {
        if (dockId == null || dockId.isEmpty()) {
            lastError = new IllegalArgumentException("dockId is required");
            return null;
        }
        if (extraDays == null || extraDays <= 0) {
            lastError = new IllegalArgumentException("extraDays must be positive");
            return null;
        }
        
        Map<String, Object> params = new HashMap<>();
        params.put(FieldNames.ID, dockId);
        params.put(FieldNames.EXTRA_DAYS, extraDays);
        
        FapiResponse response = operation("dock.extend", params);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        return ObjectUtils.objectToMap(response.getData(), String.class, Object.class);
    }
    
    /**
     * Result of dockGetWithMetadata operation.
     */
    public record DockGetResult(DockItem metadata, byte[] content) {
    }
    
    // ========== 工具方法 ==========

    private Map<String, Object> buildHistoryParams(Long startTime, Long endTime, Integer count) {
        Map<String, Object> params = new HashMap<>();
        if (startTime != null) {
            params.put(FieldNames.START_TIME, startTime);
        }
        if (endTime != null) {
            params.put(FieldNames.END_TIME, endTime);
        }
        int finalCount = (count == null || count <= 0) ? (int) FchChainInfo.DEFAULT_COUNT : count;
        params.put(FieldNames.COUNT, finalCount);
        return params;
    }

    private <K, V> Map<K, V> sendHistoryRequest(String api, Map<String, Object> params, Class<K> keyClass, Class<V> valueClass) {
        FapiResponse response = operation(api, params);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        return ObjectUtils.objectToMap(response.getData(), keyClass, valueClass);
    }
    
    private FapiResponse buildErrorResponse(int code, String message) {
        FapiResponse response = new FapiResponse();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }

    private void updateBalanceFromResponse(FapiResponse response) {
        if (response == null) return;
        
        // Prevent re-entrant calls: checkAndRechargeIfNeeded → getServiceInfo → entityByIds → 
        // request → updateBalanceFromResponse would recurse infinitely
        if (insideBalanceUpdate.get()) return;
        insideBalanceUpdate.set(Boolean.TRUE);
        try {
            updateBalanceFromResponseInternal(response);
        } finally {
            insideBalanceUpdate.set(Boolean.FALSE);
        }
    }
    
    private void updateBalanceFromResponseInternal(FapiResponse response) {
        if (response.getCharged() != null) {
            lastCharged = response.getCharged();
        }
        
        if (response.getBalance() != null) {
            lastBalance = response.getBalance();
            lastBalanceSeq = response.getBalanceSeq();
            lastBalanceTimestampMillis = System.currentTimeMillis();
            if (balanceVerifier != null) {
                BalanceVerifier.Result result = balanceVerifier.observe(lastBalance);
                if (result.getType() == BalanceVerifier.Result.Type.WARN) {
                    log.warn("Balance drift warning: drift={}, threshold={}", result.getDrift(), result.getThreshold());
                } else if (result.getType() == BalanceVerifier.Result.Type.STOP) {
                    log.error("Balance drift stop: drift={}, threshold={}", result.getDrift(), result.getThreshold());
                }
            }
            
            if (autoRechargeManager != null && autoRechargeManager.isEnabled()) {
                autoRechargeManager.checkAndRechargeIfNeeded(lastBalance)
                    .thenAccept(result -> {
                        if (result != null && result.isSuccess()) {
                            log.info("Auto-recharge completed: txId={}, amount={} FCH", 
                                    result.getTxId(), result.getAmountFch());
                            System.out.println("Auto-recharge completed: txId="+result.getTxId() +"amount=" +result.getAmountFch()+" FCH");
                            System.out.println("Try it after the confirmation.");
                        }
                    })
                    .exceptionally(e -> {
                        log.error("Auto-recharge failed: {}", e.getMessage());
                        return null;
                    });
            }
        }
    }
    
    public FapiResponse getLastResponse() {
        return lastResponse;
    }

    public Long getLastBalance() {
        return lastBalance;
    }

    public Long getLastBalanceSeq() {
        return lastBalanceSeq;
    }

    public Long getLastBalanceTimestampMillis() {
        return lastBalanceTimestampMillis;
    }
    
    public Long getLastCharged() {
        return lastCharged;
    }

    public BalanceVerifier getBalanceVerifier() {
        return balanceVerifier;
    }
    
    public AutoRechargeManager getAutoRechargeManager() {
        return autoRechargeManager;
    }
    
    public Settings getSettings() {
        return settings;
    }
    
    public Exception getLastError() {
        return lastError;
    }
    
    public String getServicePeerId() {
        return servicePeerId;
    }
    
    public String getServiceSid() {
        return serviceSid;
    }
    
    public String getServerUrl() {
        return serverUrl;
    }
    
    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }
    
    public String getVia() {
        return via;
    }
    
    // ==================== 充值相关方法 ====================
    
    /**
     * 手动充值
     * 
     * @param amountFch 充值金额（FCH），如果为 null 则使用默认计算值
     * @return 充值结果
     */
    public AutoRechargeManager.RechargeResult manualRecharge(Double amountFch) {
        if (autoRechargeManager == null) {
            log.error("AutoRechargeManager not initialized. Settings required.");
            return AutoRechargeManager.RechargeResult.failure("AutoRechargeManager not initialized");
        }
        return autoRechargeManager.manualRecharge(amountFch);
    }
    
    /**
     * 手动充值（使用默认金额）
     * 
     * @return 充值结果
     */
    public AutoRechargeManager.RechargeResult manualRecharge() {
        return manualRecharge(null);
    }
    
    /**
     * 设置充值回调
     * 
     * @param callback 回调函数，接收 (RechargeResult, message)
     */
    public void setRechargeCallback(java.util.function.BiConsumer<AutoRechargeManager.RechargeResult, String> callback) {
        if (autoRechargeManager != null) {
            autoRechargeManager.setRechargeCallback(callback);
        }
    }
    
    /**
     * 检查是否正在充值
     */
    public boolean isRecharging() {
        return autoRechargeManager != null && autoRechargeManager.isRecharging();
    }
    
    /**
     * 获取上次成功充值的交易ID
     */
    public String getLastRechargeTxId() {
        return autoRechargeManager != null ? autoRechargeManager.getLastRechargeTxId() : null;
    }

    /**
     * 使用默认引导 API 发现并连接到 FAPI 服务。
     */
    public static FapiClient bootstrap(FudpNode fudpNode) {
        return bootstrap(fudpNode, null, DEFAULT_HELLO_TIMEOUT_MS, DEFAULT_PING_TIMEOUT_MS);
    }

    public static FapiClient bootstrap(FudpNode fudpNode, Settings settings) {
        return bootstrap(fudpNode, settings, DEFAULT_HELLO_TIMEOUT_MS, DEFAULT_PING_TIMEOUT_MS);
    }

    public static FapiClient bootstrap(FudpNode fudpNode, long helloTimeoutMs, long pingTimeoutMs) {
        return bootstrap(fudpNode, null, helloTimeoutMs, pingTimeoutMs);
    }

    public static FapiClient bootstrap(FudpNode fudpNode, Settings settings, long helloTimeoutMs, long pingTimeoutMs) {
        List<Endpoint> endpoints = loadDefaultEndpoints();
        for (Endpoint ep : endpoints) {
            try {
                DiscoveryResult result = discoverViaHelloAndPing(
                        fudpNode,
                        ep.host(),
                        ep.port(),
                        helloTimeoutMs,
                        pingTimeoutMs
                );
                if (result != null && !result.getServices().isEmpty()) {
                    Service service = result.getServices().get(0);
                    log.info("Bootstrap succeeded via {}: peerId={}, sid={}", ep.hostPort(), result.getPeerId(), service.getId());
                    FapiClient client = new FapiClient(fudpNode, result.getPeerId(), service.getId(), 30, settings);
                    client.setServerUrl("fudp://" + ep.host() + ":" + ep.port());
                    return client;
                }
                log.warn("Bootstrap endpoint {} returned no FAPI services", ep.hostPort());
            } catch (Exception e) {
                log.warn("Bootstrap via {} failed: {}", ep.hostPort(), describeException(e));
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
        log.error("No FAPI endpoint discovered via default bootstrap APIs");
        return null;
    }

    /**
     * Discover peer public key via HELLO and list FAPI services via PING.
     */
    public static DiscoveryResult discoverViaHelloAndPing(
            FudpNode fudpNode,
            String host,
            int port,
            long helloTimeoutMs,
            long pingTimeoutMs
    ) throws Throwable {
        byte[] pubkey;
        try {
            pubkey = fudpNode.discoverPublicKey(host, port, helloTimeoutMs)
                    .get(helloTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
        String peerId = KeyTools.pubkeyToFchAddr(pubkey);
        fudpNode.addPeer(peerId, pubkey, host, port);

        PongMessage pong;
        try {
            pong = fudpNode.pingAwaitPong(peerId, true, pingTimeoutMs)
                    .get(pingTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
        if (pong == null) {
            log.warn("discoverViaHelloAndPing: null pong from {}:{} for {}", host, port, peerId);
        } else {
            byte[] data = pong.getData();
            log.debug("discoverViaHelloAndPing: pong received from {}:{} len={} wantInfo={}", host, port, data == null ? 0 : data.length, true);
        }
        List<Service> services = parseFapiServicesFromPong(pong);

        return new DiscoveryResult(peerId, pubkey, services, pong);
    }

    public static List<Service> parseFapiServicesFromPong(PongMessage pong) {
        if (pong == null || pong.getData() == null || pong.getData().length == 0) {
            log.debug("parseFapiServicesFromPong: empty pong data");
            return java.util.Collections.emptyList();
        }
        try {
            String json = new String(pong.getData(), StandardCharsets.UTF_8);
            log.debug("parseFapiServicesFromPong: raw data {}", json);
            Map<?, ?> map = JsonUtils.fromJson(json, Map.class);
            Object svcObj = map.get(constants.FieldNames.SERVICES);
            if (!(svcObj instanceof List<?> services)) {
                log.debug("parseFapiServicesFromPong: no services field");
                return java.util.Collections.emptyList();
            }
            List<Service> result = new ArrayList<>();
            for (Object item : services) {
                if (!(item instanceof Map<?, ?> svcMap)) continue;
                
                // Check if type is FAPI
                if (!isFapiType(svcMap.get(constants.FieldNames.TYPE))) continue;

                Service service = new Service();
                service.setId(stringVal(svcMap.get(constants.FieldNames.SID)));
                service.setStdName(stringVal(svcMap.get(constants.FieldNames.NAME)));
                service.setVer(stringVal(svcMap.get(constants.FieldNames.VER)));
                service.setDealerPubkey(stringVal(svcMap.get(constants.FieldNames.DEALER_PUBKEY)));
                service.setPricePerKB(stringVal(svcMap.get(FieldNames.PRICE_PER_K_B)));
                service.setMinPayment(stringVal(svcMap.get(FieldNames.MIN_PAYMENT)));
                service.setMinCredit(stringVal(svcMap.get(FieldNames.MIN_CREDIT)));

                // Parse type field (single ServiceType)
                String typeStr = stringVal(svcMap.get(constants.FieldNames.TYPE));
                if (typeStr != null) {
                    service.makeServiceType(ServiceType.fromString(typeStr));
                }
                
                Object componentsObj = svcMap.get(constants.FieldNames.COMPONENTS);
                if (componentsObj instanceof List<?> list) {
                    List<String> components = list.stream()
                            .map(Object::toString)
                            .toList();
                    service.setComponents(components);
                }
                
                result.add(service);
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse pong info: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Check if the type value indicates a FAPI service
     */
    private static boolean isFapiType(Object typeObj) {
        if (typeObj == null) return false;
        String typeStr = String.valueOf(typeObj);
        return ServiceType.FAPI_NO1_NRC7.equalsIgnoreCase(typeStr) || ServiceType.FAPI_STR.equalsIgnoreCase(typeStr);
    }

    public static List<Endpoint> loadDefaultEndpoints() {
        List<Endpoint> list = new ArrayList<>();
        String env = System.getenv("FAPI_BOOTSTRAP");
        String[] seeds = env != null && !env.isBlank() ? env.split(",") : FapiDefaults.DEFAULT_ENDPOINTS;
        for (String seed : seeds) {
            Endpoint ep = parseEndpoint(seed.trim());
            if (ep != null) list.add(ep);
        }
        return list;
    }

    /**
     * Bootstrap FapiClient from a fudp:// URL.
     */
    public static FapiClient bootstrapFromUrl(FudpNode fudpNode, String url, Settings settings) {
        return bootstrapFromUrl(fudpNode, url, settings, DEFAULT_HELLO_TIMEOUT_MS, DEFAULT_PING_TIMEOUT_MS);
    }

    public static FapiClient bootstrapFromUrl(FudpNode fudpNode, String url, Settings settings,
                                              long helloTimeoutMs, long pingTimeoutMs) {
        if (fudpNode == null || url == null || url.isEmpty()) {
            log.error("bootstrapFromUrl: invalid parameters");
            return null;
        }
        Endpoint ep = parseFudpUrl(url);
        if (ep == null) {
            log.error("bootstrapFromUrl: failed to parse URL: {}", url);
            return null;
        }
        try {
            DiscoveryResult result = discoverViaHelloAndPing(fudpNode, ep.host(), ep.port(), helloTimeoutMs, pingTimeoutMs);
            if (result == null || result.getServices().isEmpty()) {
                log.warn("bootstrapFromUrl: no services from {}", url);
                return null;
            }
            Service service = result.getServices().get(0);
            log.info("bootstrapFromUrl: connected to {}, sid={}", url, service.getId());
            FapiClient client = new FapiClient(fudpNode, result.getPeerId(), service.getId(), 30, settings);
            client.setServerUrl(url);
            return client;
        } catch (Throwable e) {
            log.error("bootstrapFromUrl: failed for {}: {}", url, describeException(e));
            return null;
        }
    }

    /**
     * Parse fudp://host:port without opening a stream.
     */
    public static Endpoint parseFudpUrl(String fudpUrl) {
        if (fudpUrl == null || fudpUrl.isEmpty()) return null;
        if (!fudpUrl.toLowerCase().startsWith("fudp://")) return null;
        try {
            String hostPort = fudpUrl.substring(7);
            int pathIdx = hostPort.indexOf('/');
            if (pathIdx > 0) hostPort = hostPort.substring(0, pathIdx);
            int queryIdx = hostPort.indexOf('?');
            if (queryIdx > 0) hostPort = hostPort.substring(0, queryIdx);
            String host;
            int port = 8500;
            if (hostPort.contains(":")) {
                String[] parts = hostPort.split(":");
                host = parts[0];
                if (parts.length > 1 && !parts[1].isEmpty()) port = Integer.parseInt(parts[1]);
            } else {
                host = hostPort;
            }
            return host.isEmpty() ? null : new Endpoint(host, port, fudpUrl);
        } catch (Exception e) {
            log.warn("parseFudpUrl failed for {}: {}", fudpUrl, e.getMessage());
            return null;
        }
    }

    /**
     * Bootstrap FapiClient from SID using defaultClient to resolve Service.
     */
    public static FapiClient bootstrapFromSid(FudpNode fudpNode, String sid, FapiClient defaultClient, Settings settings) {
        if (fudpNode == null || sid == null || sid.isEmpty() || defaultClient == null) {
            log.error("bootstrapFromSid: invalid parameters");
            return null;
        }
        Map<String, Service> map = defaultClient.entityByIds(constants.IndicesNames.SERVICE, Service.class, sid);
        if (map == null || map.isEmpty()) {
            log.warn("bootstrapFromSid: Service not found for sid={}", sid);
            return null;
        }
        Service service = map.get(sid);
        if (service == null) return null;
        String apiUrl = service.getApiUrl();
        if (apiUrl == null || apiUrl.isEmpty()) {
            log.warn("bootstrapFromSid: no API URL for sid={}", sid);
            return null;
        }
        return bootstrapFromUrl(fudpNode, apiUrl, settings);
    }

    public static Endpoint parseEndpoint(String seed) {
        if (seed == null || seed.isBlank()) return null;
        try {
            if (seed.startsWith("fudp")) {
                Endpoint ep = parseFudpUrl(seed);
                if (ep != null) return ep;
                URL url = new URL(seed);
                try (var in = url.openStream()) {
                    String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, Object> map = JsonUtils.jsonToMap(json,String.class,Object.class);
                    if(map==null)return null;
                    String host = stringVal(map.getOrDefault("host", url.getHost()));
                    int port = parsePort(map.get("port"), url.getPort() > 0 ? url.getPort() : 8500);
                    return new Endpoint(host, port, seed);
                }
            }
            // host:port
            String host = seed;
            int port = 8500;
            if (seed.contains(":")) {
                String[] parts = seed.split(":");
                host = parts[0];
                if (parts.length > 1) {
                    port = Integer.parseInt(parts[1]);
                }
            }
            return new Endpoint(host, port, seed);
        } catch (Exception e) {
            log.warn("Ignore bad bootstrap seed {}: {}", seed, e.getMessage());
            return null;
        }
    }

    private static int parsePort(Object value, int defaultPort) {
        try {
            if (value == null) return defaultPort;
            if (value instanceof Number n) return n.intValue();
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return defaultPort;
        }
    }

    private static String stringVal(Object obj) {
        return obj == null ? null : obj.toString();
    }

    private static String describeException(Throwable t) {
        if (t == null) return "unknown error";
        String msg = t.getMessage();
        if (t.getCause() != null && (msg == null || msg.isBlank())) {
            return describeException(t.getCause());
        }
        return msg != null ? msg : t.getClass().getSimpleName();
    }

    public static class DiscoveryResult {
        private final String peerId;
        private final byte[] publicKey;
        private final List<Service> services;
        private final PongMessage pong;

        public DiscoveryResult(String peerId, byte[] publicKey, List<Service> services, PongMessage pong) {
            this.peerId = peerId;
            this.publicKey = publicKey;
            this.services = services != null ? services : java.util.Collections.emptyList();
            this.pong = pong;
        }

        public String getPeerId() {
            return peerId;
        }

        public byte[] getPublicKey() {
            return publicKey;
        }

        public List<Service> getServices() {
            return services;
        }

        public PongMessage getPong() {
            return pong;
        }
    }

    public static class Endpoint {
        private final String host;
        private final int port;
        private final String source;

        Endpoint(String host, int port, String source) {
            this.host = host;
            this.port = port;
            this.source = source;
        }

        public String host() { return host; }
        public int port() { return port; }
        String hostPort() { return host + ":" + port; }
        String source() { return source; }
    }
}
