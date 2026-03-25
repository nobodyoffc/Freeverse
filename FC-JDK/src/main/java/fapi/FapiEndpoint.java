package fapi;

import java.util.HashMap;
import java.util.Map;

/**
 * Enum defining all FAPI endpoint names.
 * Each endpoint maps to a specific handler for processing requests.
 */
public enum FapiEndpoint {
    // General endpoints
    TOTALS("totals", EndpointGroup.GENERAL),
    
    // Chain info endpoints
    CHAIN_INFO("chainInfo", EndpointGroup.CHAIN_INFO),
    BLOCK_TIME_HISTORY("blockTimeHistory", EndpointGroup.CHAIN_INFO),
    DIFFICULTY_HISTORY("difficultyHistory", EndpointGroup.CHAIN_INFO),
    HASH_RATE_HISTORY("hashRateHistory", EndpointGroup.CHAIN_INFO),
    
    // Cash/Balance endpoints
    BALANCE_BY_IDS("balanceByIds", EndpointGroup.CASH),
    CASH_VALID("cashValid", EndpointGroup.CASH),
    GET_UTXO("getUtxo", EndpointGroup.CASH),
    
    // Transaction endpoints
    BROADCAST_TX("broadcastTx", EndpointGroup.TRANSACTION),
    DECODE_TX("decodeTx", EndpointGroup.TRANSACTION),
    ESTIMATE_FEE("estimateFee", EndpointGroup.TRANSACTION),
    
    // Mempool endpoints
    UNCONFIRMED("unconfirmed", EndpointGroup.MEMPOOL),
    UNCONFIRMED_CASHES("unconfirmedCashes", EndpointGroup.MEMPOOL);
    
    private final String name;
    private final EndpointGroup group;
    
    // Static lookup map for fast name-to-enum conversion
    private static final Map<String, FapiEndpoint> NAME_MAP = new HashMap<>();
    
    static {
        for (FapiEndpoint endpoint : values()) {
            NAME_MAP.put(endpoint.name.toLowerCase(), endpoint);
        }
    }
    
    FapiEndpoint(String name, EndpointGroup group) {
        this.name = name;
        this.group = group;
    }
    
    /**
     * Get the endpoint name (used in FCDSL requests)
     */
    public String getName() {
        return name;
    }
    
    /**
     * Get the endpoint group
     */
    public EndpointGroup getGroup() {
        return group;
    }
    
    /**
     * Find endpoint by name (case-insensitive)
     * @param name The endpoint name from FCDSL
     * @return The matching FapiEndpoint, or null if not found
     */
    public static FapiEndpoint fromName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return NAME_MAP.get(name.toLowerCase());
    }
    
    /**
     * Check if a name is a valid endpoint
     */
    public static boolean isValidEndpoint(String name) {
        return fromName(name) != null;
    }
    
    /**
     * Endpoint group classification
     */
    public enum EndpointGroup {
        GENERAL,
        CHAIN_INFO,
        CASH,
        TRANSACTION,
        MEMPOOL
    }
}

