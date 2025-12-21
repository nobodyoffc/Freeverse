package fudp.connection;

import fudp.util.ByteUtils;
import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all peer connections
 */
public class ConnectionManager {

    // PeerId (FID) -> PeerConnection
    private final Map<String, PeerConnection> connectionsByPeerId;

    // ConnectionId -> PeerConnection
    private final Map<Long, PeerConnection> connectionsByConnId;

    // Address -> PeerId (for quick lookup)
    private final Map<SocketAddress, String> addressToPeerId;

    public ConnectionManager() {
        this.connectionsByPeerId = new ConcurrentHashMap<>();
        this.connectionsByConnId = new ConcurrentHashMap<>();
        this.addressToPeerId = new ConcurrentHashMap<>();
    }

    /**
     * Get or create a connection for a peer.
     * 
     * If the peer's address changed, update it automatically.
     * With AsyTwoWay encryption, address changes are handled seamlessly
     * since identity is verified by cryptographic signature, not IP address.
     */
    public PeerConnection getOrCreate(String peerId, SocketAddress address) {
        PeerConnection conn = connectionsByPeerId.get(peerId);

        if (conn == null) {
            long connectionId = generateConnectionId();
            conn = new PeerConnection(peerId, address, connectionId);

            connectionsByPeerId.put(peerId, conn);
            connectionsByConnId.put(connectionId, conn);
            addressToPeerId.put(address, peerId);
        } else {
            // Update address if changed (transparent address migration)
            SocketAddress oldAddress = conn.getPeerAddress();
            if (!address.equals(oldAddress)) {
                addressToPeerId.remove(oldAddress);
                conn.updateAddress(address);
                addressToPeerId.put(address, peerId);
            }
        }

        return conn;
    }

    /**
     * Get connection by peer ID
     */
    public PeerConnection getByPeerId(String peerId) {
        return connectionsByPeerId.get(peerId);
    }

    /**
     * Get connection by connection ID
     */
    public PeerConnection getByConnectionId(long connectionId) {
        return connectionsByConnId.get(connectionId);
    }

    /**
     * Get peer ID by address
     */
    public String getPeerIdByAddress(SocketAddress address) {
        return addressToPeerId.get(address);
    }

    /**
     * Get connection by address
     */
    public PeerConnection getByAddress(SocketAddress address) {
        String peerId = addressToPeerId.get(address);
        return peerId != null ? connectionsByPeerId.get(peerId) : null;
    }

    /**
     * Remove a connection
     */
    public void removeConnection(String peerId) {
        PeerConnection conn = connectionsByPeerId.remove(peerId);
        if (conn != null) {
            connectionsByConnId.remove(conn.getConnectionId());
            addressToPeerId.remove(conn.getPeerAddress());
        }
    }

    /**
     * Get all connections
     */
    public Collection<PeerConnection> getAllConnections() {
        return connectionsByPeerId.values();
    }

    /**
     * Get active connections count
     */
    public int getConnectionCount() {
        return connectionsByPeerId.size();
    }

    /**
     * Check if a connection exists
     */
    public boolean hasConnection(String peerId) {
        return connectionsByPeerId.containsKey(peerId);
    }

    /**
     * Generate a unique connection ID
     */
    private long generateConnectionId() {
        return ByteUtils.randomLong();
    }

    /**
     * Close all connections
     */
    public void closeAll() {
        for (PeerConnection conn : connectionsByPeerId.values()) {
            conn.setState(ConnectionState.CLOSED);
        }
        connectionsByPeerId.clear();
        connectionsByConnId.clear();
        addressToPeerId.clear();
    }

    /**
     * Get statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", connectionsByPeerId.size());

        int idle = 0, establishing = 0, established = 0, closing = 0, closed = 0;
        for (PeerConnection conn : connectionsByPeerId.values()) {
            switch (conn.getState()) {
                case IDLE -> idle++;
                case ESTABLISHING -> establishing++;
                case ESTABLISHED -> established++;
                case CLOSING -> closing++;
                case CLOSED -> closed++;
            }
        }

        stats.put("idle", idle);
        stats.put("establishing", establishing);
        stats.put("established", established);
        stats.put("closing", closing);

        return stats;
    }
}
