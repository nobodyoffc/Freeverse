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
     * Decode the message-specific payload.
     * Subclasses must implement this to deserialize their specific data.
     */
    public abstract void decodePayload(byte[] payload);
}
