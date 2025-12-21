package fapi.client;

import data.apipData.Fcdsl;
import data.fchData.*;
import data.feipData.*;
import fapi.message.FapiResponse;
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

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * FAPI 客户端，封装 FUDP 请求，提供便捷的查询方法
 * 参考 ApipClient 的设计，但使用 FUDP 协议而非 HTTP
 */
public class FapiClient {
    private static final Logger log = LoggerFactory.getLogger(FapiClient.class);
    
    public static final String[] DEFAULT_BOOTSTRAP_APIS = new String[]{
            "127.0.0.1:9000"
    };
    public static final long DEFAULT_HELLO_TIMEOUT_MS = 5000;
    public static final long DEFAULT_PING_TIMEOUT_MS = 5000;
    
    private final FudpNode fudpNode;
    private final String servicePeerId;  // FAPI 服务提供者的 Peer ID (FID)
    private final String serviceSid;      // Service ID (链上注册的交易ID)
    private final long requestTimeoutSeconds;
    private final BalanceVerifier balanceVerifier;
    
    private FapiResponse lastResponse;
    private Exception lastError;
    private Long lastBalance;
    private Long lastBalanceSeq;
    private Long lastBalanceTimestampMillis;
    
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
        this.balanceVerifier = BalanceVerifier.fromSettings(settings);
    }
    
    /**
     * 发送 FCDSL 查询请求
     */
    public FapiResponse general(Fcdsl fcdsl) {
        try {
            if (balanceVerifier != null && balanceVerifier.isStopped()) {
                lastError = new IllegalStateException("Balance verification stopped due to drift");
                return buildErrorResponse(403, "Balance verification stopped");
            }
            byte[] fcdslJson = JsonUtils.toJson(fcdsl).getBytes();
            CompletableFuture<ResponseMessage> future = fudpNode.request(servicePeerId, serviceSid, fcdslJson);
            
            ResponseMessage response = future.get(requestTimeoutSeconds, TimeUnit.SECONDS);
            
            // FUDP uses STATUS_SUCCESS = 0 for success, not HTTP 200
            if (response.getStatusCode() != ResponseMessage.STATUS_SUCCESS) {
                lastError = new IOException("Request failed with status: " + response.getStatusCode());
                return buildErrorResponse(response.getStatusCode(), 
                    response.getData() != null ? new String(response.getData()) : "Unknown error");
            }
            
            // 解析响应
            String responseJson = new String(response.getData());
            FapiResponse fapiResponse = JsonUtils.fromJson(responseJson, FapiResponse.class);
            updateBalanceFromResponse(fapiResponse);
            lastResponse = fapiResponse;
            lastError = null;
            
            return fapiResponse;
            
        } catch (TimeoutException e) {
            lastError = e;
            return buildErrorResponse(408, "Request timeout");
        } catch (Exception e) {
            lastError = e;
            log.error("Error sending FAPI request", e);
            return buildErrorResponse(500, e.getMessage());
        }
    }
    
    /**
     * 根据实体名和 ID 列表查询
     */
    public <T> Map<String, T> entityByIds(String entityName, Class<T> clazz, String... ids) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIndex(entityName);
        fcdsl.addIds(ids);
        
        FapiResponse response = general(fcdsl);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        return ObjectUtils.objectToMap(response.getData(), String.class, clazz);
    }

    /**
     * 根据实体名搜索
     */
    public <T> List<T> entitySearch(String entityName, Fcdsl fcdsl, Class<T> clazz) {
        fcdsl.setIndex(entityName.toLowerCase());
        FapiResponse response = general(fcdsl);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }

        return ObjectUtils.objectToList(response.getData(), clazz);
    }
    
    // ========== Block 相关方法 ==========
    
    public Block bestBlock() {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIndex(IndicesNames.BLOCK);
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
     * 获取链信息，可选指定高度（参数通过 fcdsl.other 携带）
     */
    public FchChainInfo chainInfo() {
        return chainInfo(null);
    }

    public FchChainInfo chainInfo(Long height) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEndpoint("chainInfo");
        if (height != null) {
            Map<String, String> other = new HashMap<>();
            other.put(FieldNames.HEIGHT, String.valueOf(height));
            fcdsl.setOther(other);
        }

        FapiResponse response = general(fcdsl);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }

        return ObjectUtils.objectToClass(response.getData(), FchChainInfo.class);
    }

    public Map<Long, Long> blockTimeHistory(Long startTime, Long endTime, Integer count) {
        Fcdsl fcdsl = buildHistoryEndpointFcdsl("blockTimeHistory", startTime, endTime, count);
        return sendHistoryRequest(fcdsl, Long.class, Long.class);
    }

    public Map<Long, String> difficultyHistory(Long startTime, Long endTime, Integer count) {
        Fcdsl fcdsl = buildHistoryEndpointFcdsl("difficultyHistory", startTime, endTime, count);
        return sendHistoryRequest(fcdsl, Long.class, String.class);
    }

    public Map<Long, String> hashRateHistory(Long startTime, Long endTime, Integer count) {
        Fcdsl fcdsl = buildHistoryEndpointFcdsl("hashRateHistory", startTime, endTime, count);
        return sendHistoryRequest(fcdsl, Long.class, String.class);
    }
    
    /**
     * 获取所有实体的列表及其文档数量
     * 类似于 APIP Totals 接口的功能
     * @return Map<String, String> 实体名 -> 文档数量
     */
    public Map<String, String> totals() {
        // 使用 endpoint 字段来请求特殊 API
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEndpoint("totals");
        
        FapiResponse response = general(fcdsl);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        
        // 解析响应数据为 Map<String, String>
        return ObjectUtils.objectToMap(response.getData(), String.class, String.class);
    }
    
    // ========== Wallet APIs ==========
    
    /**
     * 根据 FID 列表获取余额
     * @param fids FID 列表
     * @return Map<FID, Balance> FID -> 余额（单位：satoshi）
     */
    public Map<String, Long> balanceByIds(String... fids) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEndpoint("balanceByIds");
        fcdsl.addIds(fids);
        
        FapiResponse response = general(fcdsl);
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
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEndpoint("broadcastTx");
        
        Map<String, String> other = new HashMap<>();
        other.put("rawTx", rawTx);
        fcdsl.setOther(other);
        
        FapiResponse response = general(fcdsl);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        
        return response.getData().toString();
    }
    
    /**
     * 解码交易
     * @param rawTx 原始交易十六进制
     * @return 解码后的交易对象
     */
    public Object decodeTx(String rawTx) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEndpoint("decodeTx");
        
        Map<String, String> other = new HashMap<>();
        other.put("rawTx", rawTx);
        fcdsl.setOther(other);
        
        FapiResponse response = general(fcdsl);
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
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEndpoint("estimateFee");
        
        if (nBlocks != null && nBlocks > 0) {
            Map<String, String> other = new HashMap<>();
            other.put("nBlocks", String.valueOf(nBlocks));
            fcdsl.setOther(other);
        }
        
        FapiResponse response = general(fcdsl);
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
        fcdsl.setEndpoint("cashValid");
        
        FapiResponse response = general(fcdsl);
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
        fcdsl.setEndpoint("cashValid");
        
        Map<String, String> other = new HashMap<>();
        other.put("fid", fid);
        if (amount != null) other.put(FieldNames.AMOUNT, String.valueOf(amount));
        if (cd != null) other.put(FieldNames.CD, String.valueOf(cd));
        if (sinceHeight != null) other.put(FieldNames.SINCE_HEIGHT, String.valueOf(sinceHeight));
        if (outputSize != null) other.put("outputSize", String.valueOf(outputSize));
        if (msgSize != null) other.put("msgSize", String.valueOf(msgSize));
        fcdsl.setOther(other);
        
        FapiResponse response = general(fcdsl);
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
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEndpoint("getUtxo");
        
        Map<String, String> other = new HashMap<>();
        other.put(FieldNames.ADDRESS, address);
        if (amount != null) other.put(FieldNames.AMOUNT, String.valueOf(amount));
        if (cd != null) other.put(FieldNames.CD, String.valueOf(cd));
        fcdsl.setOther(other);
        
        FapiResponse response = general(fcdsl);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        
        return ObjectUtils.objectToList(response.getData(), data.apipData.Utxo.class);
    }
    
    
    // ========== 工具方法 ==========

    private Fcdsl buildHistoryEndpointFcdsl(String endpoint, Long startTime, Long endTime, Integer count) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEndpoint(endpoint);

        Map<String, String> other = new HashMap<>();
        if (startTime != null) {
            other.put(FieldNames.START_TIME, String.valueOf(startTime));
        }
        if (endTime != null) {
            other.put(FieldNames.END_TIME, String.valueOf(endTime));
        }
        int finalCount = (count == null || count <= 0) ? (int) FchChainInfo.DEFAULT_COUNT : count;
        other.put(FieldNames.COUNT, String.valueOf(finalCount));
        fcdsl.setOther(other);
        return fcdsl;
    }

    private <K, V> Map<K, V> sendHistoryRequest(Fcdsl fcdsl, Class<K> keyClass, Class<V> valueClass) {
        FapiResponse response = general(fcdsl);
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

    public BalanceVerifier getBalanceVerifier() {
        return balanceVerifier;
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
                    return new FapiClient(fudpNode, result.getPeerId(), service.getId(), 30, settings);
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
                if (!containsFapiType(svcMap.get(constants.FieldNames.TYPES))) continue;

                Service service = new Service();
                service.setId(stringVal(svcMap.get(constants.FieldNames.SID)));
                service.setStdName(stringVal(svcMap.get(constants.FieldNames.NAME)));
                service.setVer(stringVal(svcMap.get(constants.FieldNames.VER)));
                service.setDealerPubkey(stringVal(svcMap.get(constants.FieldNames.DEALER_PUBKEY)));
                Object types = svcMap.get(constants.FieldNames.TYPES);
                if (types instanceof List<?> list) {
                    String[] arr = list.stream().map(Object::toString).toArray(String[]::new);
                    service.setTypes(arr);
                } else if (types != null) {
                    service.setTypes(new String[]{types.toString()});
                }
                result.add(service);
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse pong info: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    private static boolean containsFapiType(Object typesObj) {
        if (typesObj instanceof List<?> list) {
            for (Object t : list) {
                if ("FAPI".equalsIgnoreCase(String.valueOf(t))) return true;
            }
        } else if (typesObj != null && "FAPI".equalsIgnoreCase(String.valueOf(typesObj))) {
            return true;
        }
        return false;
    }

    public static List<Endpoint> loadDefaultEndpoints() {
        List<Endpoint> list = new ArrayList<>();
        String env = System.getenv("FAPI_BOOTSTRAP");
        String[] seeds = env != null && !env.isBlank() ? env.split(",") : DEFAULT_BOOTSTRAP_APIS;
        for (String seed : seeds) {
            Endpoint ep = parseEndpoint(seed.trim());
            if (ep != null) list.add(ep);
        }
        return list;
    }

    public static Endpoint parseEndpoint(String seed) {
        if (seed == null || seed.isBlank()) return null;
        try {
            if (seed.startsWith("http")) {
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
