package fudp.node;

import fudp.connection.ConnectionState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Information about a known peer.
 * Supports multiple addresses (endpoints) for the same FID.
 */
public class Peer {

    /**
     * A single network endpoint (host:port).
     */
    public static class Endpoint {
        public String host;
        public int port;

        public Endpoint() {}

        public Endpoint(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Endpoint e)) return false;
            return port == e.port && Objects.equals(host, e.host);
        }

        @Override
        public int hashCode() {
            return Objects.hash(host, port);
        }

        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    private String id;           // FID
    private byte[] publicKey;
    private String host;         // primary address (kept for backward compatibility with JSON)
    private int port;            // primary port
    private List<Endpoint> endpoints;  // all known endpoints (includes primary)
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
        this.pricePerKb = 0;
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
     * Get all known endpoints for this peer.
     * Includes the primary (host, port) plus any additional endpoints.
     */
    public List<Endpoint> getEndpoints() {
        List<Endpoint> result = new ArrayList<>();
        // Always include primary address first
        if (host != null && !host.isEmpty() && port > 0) {
            result.add(new Endpoint(host, port));
        }
        // Add additional endpoints (deduplicated)
        if (endpoints != null) {
            for (Endpoint ep : endpoints) {
                if (!result.contains(ep)) {
                    result.add(ep);
                }
            }
        }
        return result;
    }

    /**
     * Add an additional endpoint for this peer.
     * If the peer has no primary address yet, sets it as primary.
     * Returns true if this is a new endpoint.
     */
    public boolean addEndpoint(String epHost, int epPort) {
        Endpoint ep = new Endpoint(epHost, epPort);

        // Set as primary if no address yet
        if (host == null || host.isEmpty() || port <= 0) {
            this.host = epHost;
            this.port = epPort;
            return true;
        }

        // Already the primary?
        if (epHost.equals(host) && epPort == port) {
            return false;
        }

        // Check existing endpoints
        if (endpoints == null) {
            endpoints = new ArrayList<>();
        }
        if (endpoints.contains(ep)) {
            return false;
        }
        endpoints.add(ep);
        return true;
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
