package fchat;

/**
 * Types of data requests in messaging.
 */
public enum RequestType {
    HAT, MEMBERS, SYMKEY, MESSAGE_SYNC, HISTORY, PUBLIC_KEY, SYMKEY_HISTORY;

    public static RequestType fromString(String value) {
        if (value == null) return null;
        try {
            return RequestType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
