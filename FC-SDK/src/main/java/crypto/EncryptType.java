package crypto;

public enum EncryptType {
    SymKey((byte)0),AsyOneWay((byte)1), AsyTwoWay((byte)2),  Password((byte)3);

    private final byte number;
    EncryptType(byte i) {
        this.number = i;
    }

    public byte getNumber() {
        return number;
    }
    public static EncryptType fromNumber(byte typeByte) {
        for (EncryptType type : EncryptType.values()) {
            if (type.getNumber() == typeByte) {
                return type;
            }
        }
        // If no match is found, return null or throw an exception based on your requirements
        return null;
    }
}
