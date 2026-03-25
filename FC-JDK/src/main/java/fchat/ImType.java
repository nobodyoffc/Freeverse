package fchat;

/**
 * Types of instant messaging communication.
 */
public enum ImType {
    P2P,
    SQUARE,
    TEAM,
    ROOM;

    public static ImType fromString(String value) {
        if (value == null) return null;
        try {
            return ImType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String toLowerCase() {
        return name().toLowerCase();
    }
}
