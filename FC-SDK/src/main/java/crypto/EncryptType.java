package crypto;

public enum EncryptType {
    AsyOneWay((byte)1), AsyTwoWay((byte)2), SymKey((byte)0), Password((byte)3);

    private final byte number;
    EncryptType(byte i) {
        this.number = i;
    }

    public byte getNumber() {
        return number;
    }
}
