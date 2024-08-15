package configure;

public enum ServiceType {
    NASA_RPC,
    APIP,
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
}
