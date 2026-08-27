package fudp.message;

/**
 * Base class for all application-level messages.
 *
 * Message format:
 * ┌─────────────────────────────────────┐
 * │ Message Type (1 byte)               │
 * ├─────────────────────────────────────┤
 * │ Message ID (8 bytes)                │  ← For request/response correlation
 * ├─────────────────────────────────────┤
 * │ Flags (1 byte)                      │
 * ├─────────────────────────────────────┤
 * │ Payload Length (varint)             │
 * ├─────────────────────────────────────┤
 * │ Payload (variable)                  │
 * └─────────────────────────────────────┘
 */
public abstract class AppMessage {

    // Flags
    public static final int FLAG_NEED_ACK = 0x01;      // Require delivery confirmation
    public static final int FLAG_COMPRESSED = 0x02;    // Payload is compressed (gzip)
    public static final int FLAG_ENCRYPTED_APP = 0x04; // Additional app-level encryption
    public static final int FLAG_FRAGMENTED = 0x08;    // Message is fragmented
    public static final int FLAG_WANT_PONG_INFO = 0x10; // Ask responder to include optional info in pong

    protected final MessageType type;
    protected long messageId;
    protected int flags;

    protected AppMessage(MessageType type) {
        this.type = type;
        this.messageId = 0;
        this.flags = 0;
    }

    protected AppMessage(MessageType type, long messageId) {
        this.type = type;
        this.messageId = messageId;
        this.flags = 0;
    }

    public MessageType getType() {
        return type;
    }

    public long getMessageId() {
        return messageId;
    }

    public void setMessageId(long messageId) {
        this.messageId = messageId;
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public boolean hasFlag(int flag) {
        return (flags & flag) != 0;
    }

    public void setFlag(int flag) {
        this.flags |= flag;
    }

    public void clearFlag(int flag) {
        this.flags &= ~flag;
    }

    /**
     * Encode the message-specific payload.
     * Subclasses must implement this to serialize their specific data.
     */
    public abstract byte[] encodePayload();

    /**
     * Decode the message-specific payload from a slice of a larger buffer.
     * <p>
     * Implementations must read only bytes {@code [offset, offset+length)} and
     * must NOT retain a reference to {@code buf} (it may be reused by the caller).
     * Reading directly from the backing array — rather than a pre-sliced copy —
     * avoids an extra full-size payload copy on the receive path, which matters
     * for large messages (multi-MB uploads/downloads) under memory pressure.
     *
     * @param buf    backing array containing the payload slice
     * @param offset start of the payload within {@code buf}
     * @param length payload length in bytes
     */
    public abstract void decodePayload(byte[] buf, int offset, int length);

    /**
     * Convenience overload for a payload that occupies an entire array.
     */
    public void decodePayload(byte[] payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload is null");
        }
        decodePayload(payload, 0, payload.length);
    }
}
