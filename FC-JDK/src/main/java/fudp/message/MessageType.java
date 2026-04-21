package fudp.message;

/**
 * Application-level message types for FUDP Node.
 * These are transmitted as STREAM frame data using the underlying FUDP protocol.
 *
 * FUDP is a pure transport protocol. Only 7 message types are needed:
 * - REQUEST/RESPONSE for bidirectional exchanges
 * - NOTIFY/NOTIFY_ACK for one-way data with optional delivery confirmation
 * - PING/PONG for keepalive and latency measurement
 * - ERROR for error reporting
 *
 * Application-level features (chat, file transfer, relay) are built on top
 * of these primitives, not embedded in the transport.
 */
public enum MessageType {
    // Request/Response
    REQUEST(0x10),                 // Application request (expects RESPONSE)
    RESPONSE(0x11),                // Application response (matches REQUEST by messageId)
    ERROR(0x12),                   // Error response

    // One-way data
    NOTIFY(0x20),                  // One-way data with optional ACK
    NOTIFY_ACK(0x21),              // Delivery acknowledgment for NOTIFY

    // Keepalive
    PING(0x30),                    // Keep-alive ping / latency measurement
    PONG(0x31);                    // Ping response

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
        throw new IllegalArgumentException("Unknown message type code: 0x" + Integer.toHexString(code));
    }
}
