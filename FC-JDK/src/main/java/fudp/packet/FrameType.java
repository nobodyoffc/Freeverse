package fudp.packet;

/**
 * Frame type definitions for FUDP protocol.
 * 
 * Simplified version without SYMKEY_PROPOSAL and SYMKEY_ACK.
 * All encryption uses AsyTwoWay (ECDH) mode.
 */
public enum FrameType {
    PADDING(0x00),
    ACK(0x01),
    CONNECTION_CLOSE(0x02),
    MAX_DATA(0x03),
    MAX_STREAM_DATA(0x04),
    MAX_STREAMS(0x05),
    // Note: 0x06 and 0x07 were previously SYMKEY_PROPOSAL and SYMKEY_ACK (now removed)
    // STREAM uses 0x08-0x0F (base 0x08 + flags in lower 3 bits)
    STREAM(0x08);

    private final int value;

    FrameType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static FrameType fromValue(int value) {
        // Handle STREAM frame with flags (0x08-0x0F)
        // STREAM base is 0x08, with flags in lower 3 bits: FIN=0x01, LEN=0x02, OFF=0x04
        if (value >= 0x08 && value <= 0x0F) {
            return STREAM;
        }

        for (FrameType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown frame type: " + value);
    }
}
