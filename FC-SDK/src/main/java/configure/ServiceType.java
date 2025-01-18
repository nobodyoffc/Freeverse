package configure;

public enum ServiceType {
    NASA_RPC,
    APIP,
    FEIP,
    ES,
    REDIS,
    DISK,
    OTHER,
    TALK,
    MAP,
    SWAP_HALL;

    @Override
    public String toString() {
        return this.name();
    }

    // New method to check if a string matches a ServiceType
    public static ServiceType fromString(String input) {
        if (input == null) {
            return null;
        }
        for (ServiceType type : ServiceType.values()) {
            if (type.name().equalsIgnoreCase(input)) {
                return type;
            }
        }
        return null;
    }

    // Updated method to check if a string contains the ServiceType name
    public static ServiceType typeInString(String input) {
        if (input == null) {
            return null;
        }
        String lowercaseInput = input.toLowerCase();
        for (ServiceType type : ServiceType.values()) {
            if (lowercaseInput.contains(type.name().toLowerCase())) {
                return type;
            }
        }
        return null;
    }
}
