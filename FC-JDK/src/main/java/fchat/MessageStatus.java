package fchat;

/**
 * Status of a message in its lifecycle.
 */
public enum MessageStatus {
    PENDING, SENT, DELIVERED, READ, FAILED;

    public static MessageStatus fromString(String value) {
        if (value == null) return null;
        try {
            return MessageStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isSent() {
        return this == SENT || this == DELIVERED || this == READ;
    }

    public boolean isDelivered() {
        return this == DELIVERED || this == READ;
    }
}
