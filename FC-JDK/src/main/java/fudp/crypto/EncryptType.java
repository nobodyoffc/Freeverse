package fudp.crypto;

/**
 * Encryption type definitions for FUDP
 */
public enum EncryptType {
    AsyTwoWay(0x02),  // ECDH bidirectional encryption
    Symkey(0x03);     // Symmetric key encryption

    private final int number;

    EncryptType(int number) {
        this.number = number;
    }

    public int getNumber() {
        return number;
    }

    public static EncryptType fromNumber(int number) {
        for (EncryptType type : values()) {
            if (type.number == number) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown encrypt type: " + number);
    }
}
