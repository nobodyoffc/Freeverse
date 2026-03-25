package data.feipData;

public enum ServiceType {
    NASA_RPC,
    ES,
    REDIS,
    FCHP,
    FEIP,
    APIP,
    FAPI,
    FAPI_No1_NrC7,
    DISK,
    TALK,
    SWAP,
    SWAP_HALL,
    OTHER
    ;

    public static final String FAPI_NO1_NRC7 = "FAPI@No1_NrC7";
    public static final String FAPI_STR = "FAPI";

    public boolean isFapi() {
        return this == FAPI || this == FAPI_No1_NrC7;
    }

    public static boolean isFapi(ServiceType type) {
        return type != null && type.isFapi();
    }

    @Override
    public String toString() {
        String typeStr = this.name();
        int underscoreIndex = typeStr.indexOf('_');
        if (underscoreIndex >= 0) {
            typeStr = typeStr.substring(0, underscoreIndex) + "@" + typeStr.substring(underscoreIndex + 1);
        }
        return typeStr;
    }

    // New method to check if a string matches a ServiceType
    public static ServiceType fromString(String input) {
        if (input == null) {
            return null;
        }
        if (input.contains("@")) {
            input = input.replaceFirst("@", "_");
        }
        for (ServiceType type : ServiceType.values()) {
            if (type.name().equalsIgnoreCase(input)) {
                return type;
            }
        }
        return null;
    }
}
