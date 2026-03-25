package fapi.rpc;

import clients.NaSaClient.NaSaRpcClient;
import clients.NaSaClient.NasaRpcNames;
import clients.NaSaClient.RpcRequest;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import data.fchData.FchChainInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Service for NASA RPC operations.
 * Wraps NaSaRpcClient with proper error handling and response formatting.
 */
public class RpcOperationService {
    private static final Logger log = LoggerFactory.getLogger(RpcOperationService.class);
    
    private final NaSaRpcClient rpcClient;
    private final ElasticsearchClient esClient;
    
    public RpcOperationService(NaSaRpcClient rpcClient, ElasticsearchClient esClient) {
        this.rpcClient = rpcClient;
        this.esClient = esClient;
    }
    
    /**
     * Check if the RPC client is available
     */
    public boolean isRpcAvailable() {
        return rpcClient != null;
    }
    
    /**
     * Check if the ES client is available
     */
    public boolean isEsAvailable() {
        return esClient != null;
    }
    
    // ==================== Transaction Operations ====================
    
    /**
     * Broadcast a raw transaction
     * @param rawTx The raw transaction hex
     * @return The transaction ID if successful, or error message
     */
    public RpcResult<String> broadcastTransaction(String rawTx) {
        if (rpcClient == null) {
            return RpcResult.error("NaSa RPC client is not available");
        }
        
        try {
            String result = rpcClient.sendRawTransaction(rawTx);
            if (result == null || result.isEmpty()) {
                return RpcResult.error("Failed to broadcast transaction");
            }
            
            // Remove quotes if present
            if (result.startsWith("\"")) result = result.substring(1);
            if (result.endsWith("\"")) result = result.substring(0, result.length() - 1);
            
            // Check if result is a valid txid (hex string)
            if (!utils.Hex.isHexString(result)) {
                return RpcResult.error(result);
            }
            
            return RpcResult.success(result);
        } catch (Exception e) {
            log.error("Failed to broadcast transaction", e);
            return RpcResult.error("Failed to broadcast: " + e.getMessage());
        }
    }
    
    /**
     * Decode a raw transaction
     * @param rawTx The raw transaction hex
     * @return The decoded transaction object
     */
    public RpcResult<Object> decodeTransaction(String rawTx) {
        if (rpcClient == null) {
            return RpcResult.error("NaSa RPC client is not available");
        }
        
        try {
            Object result = rpcClient.decodeRawTransaction(rawTx);
            if (result == null) {
                return RpcResult.error("Failed to decode transaction");
            }
            return RpcResult.success(result);
        } catch (Exception e) {
            log.error("Failed to decode transaction", e);
            return RpcResult.error("Failed to decode: " + e.getMessage());
        }
    }
    
    /**
     * Estimate transaction fee
     * @param nBlocks Number of blocks for confirmation target (optional)
     * @return The estimated fee rate
     */
    public RpcResult<Double> estimateFee(Integer nBlocks) {
        if (rpcClient == null) {
            return RpcResult.error("NaSa RPC client is not available");
        }
        
        try {
            Double feeRate = null;
            
            // If nBlocks specified, try estimatesmartfee first
            if (nBlocks != null && nBlocks > 0) {
                try {
                    NaSaRpcClient.ResultEstimateSmartFee smartFeeResult = rpcClient.estimateSmartFee(nBlocks);
                    if (smartFeeResult != null) {
                        feeRate = smartFeeResult.getFeerate();
                    }
                } catch (Exception e) {
                    log.warn("estimatesmartfee failed, falling back to estimatefee: {}", e.getMessage());
                }
            }
            
            // Fallback to estimatefee without parameters
            if (feeRate == null) {
                RpcRequest jsonRPC2Request = new RpcRequest(NasaRpcNames.ESTIMATE_FEE, null);
                Object rawResult = RpcRequest.requestRpc(
                    rpcClient.getUrl(),
                    rpcClient.getUsername(),
                    rpcClient.getPassword(),
                    NasaRpcNames.ESTIMATE_FEE,
                    jsonRPC2Request
                );
                
                if (rawResult == null) {
                    return RpcResult.error("Failed to estimate fee");
                }
                
                // Safely convert result to Double
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
                return RpcResult.error("Invalid fee rate returned (may be no data available)");
            }
            
            return RpcResult.success(feeRate);
        } catch (Exception e) {
            log.error("Failed to estimate fee", e);
            return RpcResult.error("Failed to estimate fee: " + e.getMessage());
        }
    }
    
    // ==================== Chain Info Operations ====================
    
    /**
     * Get chain info (current or at specific height)
     * @param height The block height (null for current)
     * @return The chain info
     */
    public RpcResult<FchChainInfo> getChainInfo(Long height) {
        try {
            FchChainInfo chainInfo = new FchChainInfo();
            
            if (height == null) {
                if (rpcClient == null) {
                    return RpcResult.error("NaSa RPC client is not available");
                }
                chainInfo.infoBest(rpcClient);
            } else {
                if (esClient == null) {
                    return RpcResult.error("Elasticsearch client is not available");
                }
                chainInfo.infoByHeight(height, esClient);
            }
            
            return RpcResult.success(chainInfo);
        } catch (Exception e) {
            log.error("Failed to get chain info", e);
            return RpcResult.error("Failed to get chain info: " + e.getMessage());
        }
    }
    
    /**
     * Get block time history
     */
    public RpcResult<Map<Long, Long>> getBlockTimeHistory(long startTime, long endTime, int count) {
        if (esClient == null) {
            return RpcResult.error("Elasticsearch client is not available");
        }
        
        try {
            Map<Long, Long> hist = FchChainInfo.blockTimeHistory(startTime, endTime, count, esClient);
            if (hist == null) {
                return RpcResult.error("Failed to get the block time history");
            }
            return RpcResult.success(hist);
        } catch (Exception e) {
            log.error("Failed to get block time history", e);
            return RpcResult.error("Failed to get block time history: " + e.getMessage());
        }
    }
    
    /**
     * Get difficulty history
     */
    public RpcResult<Map<Long, String>> getDifficultyHistory(long startTime, long endTime, int count) {
        if (esClient == null) {
            return RpcResult.error("Elasticsearch client is not available");
        }
        
        try {
            Map<Long, String> hist = FchChainInfo.difficultyHistory(startTime, endTime, count, esClient);
            if (hist == null) {
                return RpcResult.error("Failed to get the difficulty history");
            }
            return RpcResult.success(hist);
        } catch (Exception e) {
            log.error("Failed to get difficulty history", e);
            return RpcResult.error("Failed to get difficulty history: " + e.getMessage());
        }
    }
    
    /**
     * Get hash rate history
     */
    public RpcResult<Map<Long, String>> getHashRateHistory(long startTime, long endTime, int count) {
        if (esClient == null) {
            return RpcResult.error("Elasticsearch client is not available");
        }
        
        try {
            Map<Long, String> hist = FchChainInfo.hashRateHistory(startTime, endTime, count, esClient);
            if (hist == null) {
                return RpcResult.error("Failed to get the hash rate history");
            }
            return RpcResult.success(hist);
        } catch (Exception e) {
            log.error("Failed to get hash rate history", e);
            return RpcResult.error("Failed to get hash rate history: " + e.getMessage());
        }
    }
    
    // ==================== Mempool Operations ====================
    
    /**
     * Get mempool transaction IDs
     */
    public String[] getMempoolTxIds() {
        if (rpcClient == null) {
            return null;
        }
        return rpcClient.getRawMempoolIds();
    }
    
    /**
     * Get raw transaction by ID
     */
    public String getRawTx(String txId) {
        if (rpcClient == null) {
            return null;
        }
        return rpcClient.getRawTx(txId);
    }
    
    // ==================== Getters ====================
    
    public NaSaRpcClient getRpcClient() {
        return rpcClient;
    }
    
    public ElasticsearchClient getEsClient() {
        return esClient;
    }
    
    /**
     * Generic result wrapper for RPC operations
     */
    public static class RpcResult<T> {
        private final boolean success;
        private final T data;
        private final String errorMessage;
        
        private RpcResult(boolean success, T data, String errorMessage) {
            this.success = success;
            this.data = data;
            this.errorMessage = errorMessage;
        }
        
        public static <T> RpcResult<T> success(T data) {
            return new RpcResult<>(true, data, null);
        }
        
        public static <T> RpcResult<T> error(String message) {
            return new RpcResult<>(false, null, message);
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public T getData() {
            return data;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
    }
}

