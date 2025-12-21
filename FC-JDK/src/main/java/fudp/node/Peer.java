package fudp.node;

import fudp.connection.ConnectionState;

/**
 * Information about a known peer.
 */
public class Peer {

    private String id;           // FID
    private byte[] publicKey;
    private String host;
    private int port;
    private String alias;            // User-friendly name
    private long lastSeen;
    private ConnectionState state;
    private long pricePerKb;         // Price per KB in satoshi (for balance calculation)

    public Peer() {
        this.state = ConnectionState.IDLE;
        this.lastSeen = 0;
    }

    public Peer(String id, byte[] publicKey, String host, int port) {
        this.id = id;
        this.publicKey = publicKey;
        this.host = host;
        this.port = port;
        this.state = ConnectionState.IDLE;
        this.lastSeen = System.currentTimeMillis();
        this.pricePerKb = 0; // Default: 0 (will be set from blockchain or config)
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(byte[] publicKey) {
        this.publicKey = publicKey;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public ConnectionState getState() {
        return state;
    }

    public void setState(ConnectionState state) {
        this.state = state;
    }

    public long getPricePerKb() {
        return pricePerKb;
    }

    public void setPricePerKb(long pricePerKb) {
        this.pricePerKb = pricePerKb;
    }

    /**
     * Get display name (alias or truncated FID).
     */
    public String getDisplayName() {
        if (alias != null && !alias.isEmpty()) {
            return alias;
        }
        if (id != null && id.length() > 8) {
            return id.substring(0, 8) + "...";
        }
        return id;
    }

    /**
     * Check if address information is available.
     */
    public boolean hasAddress() {
        return host != null && !host.isEmpty() && port > 0;
    }

    @Override
    public String toString() {
        return "PeerInfo{" +
                "peerId='" + id + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", alias='" + alias + '\'' +
                ", state=" + state +
                ", pricePerKb=" + pricePerKb +
                '}';
    }
}
