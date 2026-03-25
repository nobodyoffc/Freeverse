package fchat;

/**
 * Types of message content.
 * Ordinal values are used in the binary wire format — do NOT reorder existing entries.
 */
public enum ContentType {
    TEXT, HAT, STREAM,
    SYMKEY, MEMBERS, HISTORY,
    REQUEST, RESPONSE,
    TYPING, RECEIPT, PRESENCE,
    REACTION, EDIT, DELETE, FORWARD;

    public static ContentType fromString(String value) {
        if (value == null) return null;
        try {
            return ContentType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String toLowerCase() {
        return name().toLowerCase();
    }
}
