package fudp.message;

/**
 * Application-level message types for FUDP Node.
 * These are transmitted as STREAM frame data using the underlying FUDP protocol.
 */
public enum MessageType {
    // Chat/Messaging
    CHAT(0x01),                    // Simple text message
    CHAT_ACK(0x02),                // Message delivered acknowledgment

    // Request/Response
    REQUEST(0x10),                 // Application request
    RESPONSE(0x11),                // Application response
    ERROR(0x12),                   // Error response

    // File Transfer
    FILE_OFFER(0x20),              // Offer to send a file
    FILE_ACCEPT(0x21),             // Accept file offer
    FILE_REJECT(0x22),             // Reject file offer
    FILE_CHUNK(0x23),              // File data chunk
    FILE_COMPLETE(0x24),           // File transfer complete
    FILE_CANCEL(0x25),             // Cancel transfer

    // Control
    PING(0x30),                    // Keep-alive ping
    PONG(0x31),                    // Ping response
    PEER_INFO(0x32),               // Exchange peer information

    // Relay
    RELAY(0x40),                   // Relay message to target FID
    RELAY_ACK(0x41),               // Relay delivery confirmed
    RELAY_FAIL(0x42),              // Relay delivery failed
    RELAY_QUERY(0x43),             // Query relay path/cost
    RELAY_QUOTE(0x44),             // Relay cost quote response

    // NAT Traversal
    NAT_REGISTER(0x50),            // Register with relay for NAT traversal
    NAT_KEEPALIVE(0x51),           // Keep NAT mapping alive
    NAT_PROBE(0x52),               // Probe for direct connectivity
    NAT_PROBE_RESPONSE(0x53);      // Response to connectivity probe

    private final int code;

    MessageType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static MessageType fromCode(int code) {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type code: " + code);
    }
}
