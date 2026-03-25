package fudp.message;

/**
 * Error codes for relay message failures.
 */
public class RelayErrorCode {
    
    /** Target FID not found in peer book */
    public static final int ERR_UNKNOWN_TARGET = 1;
    
    /** Maximum hop count exceeded */
    public static final int ERR_HOP_LIMIT = 2;
    
    /** Failed to deliver message to target */
    public static final int ERR_DELIVERY_FAILED = 3;
    
    /** Relay operation timed out */
    public static final int ERR_TIMEOUT = 4;
    
    /** Payload size exceeds maximum allowed */
    public static final int ERR_PAYLOAD_TOO_LARGE = 5;

    /**
     * Get human-readable description for error code.
     */
    public static String getDescription(int errorCode) {
        return switch (errorCode) {
            case ERR_UNKNOWN_TARGET -> "Unknown target FID";
            case ERR_HOP_LIMIT -> "Maximum hop count exceeded";
            case ERR_DELIVERY_FAILED -> "Failed to deliver message";
            case ERR_TIMEOUT -> "Relay operation timed out";
            case ERR_PAYLOAD_TOO_LARGE -> "Payload too large";
            default -> "Unknown error: " + errorCode;
        };
    }
}

