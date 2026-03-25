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
     * 
     * When a different peer connects from the same address (e.g., node stops
     * and a new node starts on the same IP:port), the old connection is cleaned up.
     */
    public PeerConnection getOrCreate(String peerId, SocketAddress address) {
        PeerConnection conn = connectionsByPeerId.get(peerId);

        if (conn == null) {
            // Check if this address is already mapped to a different peer ID
            // This can happen when a node stops and a new node (different peer ID)
            // starts on the same address (e.g., same IP:port but different key)
            String existingPeerId = addressToPeerId.get(address);
            if (existingPeerId != null && !existingPeerId.equals(peerId)) {
                // Clean up the old connection for the previous peer at this address
                removeConnection(existingPeerId);
            }
            
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
                
                // Check if new address is already mapped to a different peer ID
                String existingPeerId = addressToPeerId.get(address);
                if (existingPeerId != null && !existingPeerId.equals(peerId)) {
                    // Clean up the old connection for the previous peer at this address
                    removeConnection(existingPeerId);
                }
                
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

        int idle = 0, establishing = 0, established = 0, closing = 0;
        for (PeerConnection conn : connectionsByPeerId.values()) {
            switch (conn.getState()) {
                case IDLE -> idle++;
                case ESTABLISHING -> establishing++;
                case ESTABLISHED -> established++;
                case CLOSING -> closing++;
                case CLOSED -> {
                    // Closed connections are not tracked in the map (they are removed)
                    // So this case should not occur, but we handle it for completeness
                }
            }
        }

        stats.put("idle", idle);
        stats.put("establishing", establishing);
        stats.put("established", established);
        stats.put("closing", closing);

        return stats;
    }
}
