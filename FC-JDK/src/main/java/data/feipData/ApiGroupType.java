package data.feipData;

public enum ApiGroupType {

    BASE,
    MAP,
    ROAD,
    DOCK;

    public static final String BASE_NO1_NRC7 = "BASE@No1_NrC7";
    public static final String DISK_NO1_NRC7 = "DISK@No1_NrC7";
    public static final String DOCK_NO1_NRC7 = "DOCK@No1_NrC7";
    public static final String MAP_NO1_NRC7 = "MAP@No1_NrC7";
    public static final String ROAD_NO1_NRC7 = "ROAD@No1_NrC7";

    @Override
    public String toString() {
        return this.name();
    }

    // New method to check if a string matches a ServiceType
    public static ApiGroupType fromString(String input) {
        if (input == null) {
            return null;
        }
        for (ApiGroupType type : ApiGroupType.values()) {
            if (type.name().equalsIgnoreCase(input)) {
                return type;
            }
        }
        return null;
    }
}
