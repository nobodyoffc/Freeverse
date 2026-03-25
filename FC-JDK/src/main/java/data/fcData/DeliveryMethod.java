package data.fcData;

/**
 * Methods of message delivery.
 */
public enum DeliveryMethod {
    FUDP_DIRECT, ROAD_RELAY, DOCK_STORED;

    public static DeliveryMethod fromString(String value) {
        if (value == null) return null;
        try {
            return DeliveryMethod.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isOnline() {
        return this == FUDP_DIRECT || this == ROAD_RELAY;
    }
}
