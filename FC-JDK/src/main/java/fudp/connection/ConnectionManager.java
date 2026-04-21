package fudp.connection;

import fudp.util.ByteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all peer connections.
 * <p>
 * Supports multiple simultaneous connections per peer (FID).
 * The same user can connect from multiple endpoints (e.g., phone + desktop)
 * and each connection is tracked independently.
 * <p>
 * Identity-level operations use peerId (FID).
 * Connection-level operations use connectionId.
 */
public class ConnectionManager {
    private static final Logger log = LoggerFactory.getLogger(ConnectionManager.class);

    /** Default maximum connections per FID */
    public static final int DEFAULT_MAX_CONNECTIONS_PER_FID = 5;

    // PeerId (FID) -> (ConnectionId -> PeerConnection)
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, PeerConnection>> connectionsByPeerId;

    // ConnectionId -> PeerConnection (flat index for O(1) lookup)
    private final ConcurrentHashMap<Long, PeerConnection> connectionsByConnId;

    // Address -> ConnectionId (for identifying which connection a packet belongs to)
    private final ConcurrentHashMap<SocketAddress, Long> addressToConnId;

    // Configurable max connections per FID
    private int maxConnectionsPerFid = DEFAULT_MAX_CONNECTIONS_PER_FID;

    // Loss detection minimum time threshold (passed to new PeerConnections)
    private long lossDetectionMinThresholdMs = 2000;

    public ConnectionManager() {
        this.connectionsByPeerId = new ConcurrentHashMap<>();
        this.connectionsByConnId = new ConcurrentHashMap<>();
        this.addressToConnId = new ConcurrentHashMap<>();
    }

    /**
     * Set the maximum number of connections allowed per FID.
     */
    public void setMaxConnectionsPerFid(int max) {
        this.maxConnectionsPerFid = max;
    }

    public int getMaxConnectionsPerFid() {
        return maxConnectionsPerFid;
    }

    public void setLossDetectionMinThresholdMs(long ms) {
        this.lossDetectionMinThresholdMs = ms;
    }

    /**
     * Get or create a connection for a peer at a given address.
     * <p>
     * Supports multiple connections per FID:
     * <ul>
     *   <li>If this peerId already has a connection at this exact address, return it.</li>
     *   <li>If this peerId connects from a NEW address, create a new connection (multi-endpoint).</li>
     *   <li>If a DIFFERENT peerId was at this address, clean up the old one first.</li>
     *   <li>If the per-FID connection limit is reached, evict the oldest idle connection.</li>
     * </ul>
     *
     * @param peerId  the peer's FID (cryptographically verified identity)
     * @param address the source address of the packet
     * @return the PeerConnection for this peer at this address
     */
    public PeerConnection getOrCreate(String peerId, SocketAddress address) {
        // 1. Check if this address already maps to a known connection
        Long existingConnId = addressToConnId.get(address);
        if (existingConnId != null) {
            PeerConnection existingConn = connectionsByConnId.get(existingConnId);
            if (existingConn != null) {
                if (existingConn.getPeerId().equals(peerId)) {
                    // Same peer, same address — reuse existing connection
                    return existingConn;
                } else {
                    // Different peer at the same address (e.g., node restarted with new key)
                    // Clean up the old connection
                    log.info("Address {} changed owner from {} to {}, removing old connection",
                            address, existingConn.getPeerId(), peerId);
                    removeConnection(existingConnId);
                }
            } else {
                // Stale entry in addressToConnId — clean up
                addressToConnId.remove(address);
            }
        }

        // 2. Get or create the peer's connection map
        ConcurrentHashMap<Long, PeerConnection> peerConns =
                connectionsByPeerId.computeIfAbsent(peerId, k -> new ConcurrentHashMap<>());

        // 3. Enforce per-FID connection limit
        if (peerConns.size() >= maxConnectionsPerFid) {
            // Evict the oldest idle connection for this peer
            PeerConnection evicted = evictIdlestConnection(peerId, peerConns);
            if (evicted != null) {
                log.info("Evicted idle connection {} for peer {} (limit={})",
                        evicted.getConnectionId(), peerId, maxConnectionsPerFid);
            } else if (peerConns.size() >= maxConnectionsPerFid) {
                // All connections are active — evict the least recently used
                evicted = evictLruConnection(peerId, peerConns);
                if (evicted != null) {
                    log.warn("Evicted LRU connection {} for peer {} (limit={}, all active)",
                            evicted.getConnectionId(), peerId, maxConnectionsPerFid);
                }
            }
        }

        // 4. Create new connection
        long connectionId = generateConnectionId();
        PeerConnection conn = new PeerConnection(peerId, address, connectionId, lossDetectionMinThresholdMs);

        peerConns.put(connectionId, conn);
        connectionsByConnId.put(connectionId, conn);
        addressToConnId.put(address, connectionId);

        log.debug("Created new connection {} for peer {} at {} (total connections for peer: {})",
                connectionId, peerId, address, peerConns.size());

        return conn;
    }

    /**
     * Get connection by connection ID (primary lookup for connection-affine operations).
     */
    public PeerConnection getByConnectionId(long connectionId) {
        return connectionsByConnId.get(connectionId);
    }

    /**
     * Get all connections for a peer.
     *
     * @return unmodifiable collection of connections, or empty collection if none
     */
    public Collection<PeerConnection> getConnectionsByPeerId(String peerId) {
        ConcurrentHashMap<Long, PeerConnection> peerConns = connectionsByPeerId.get(peerId);
        if (peerConns == null || peerConns.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableCollection(peerConns.values());
    }

    /**
     * Get the "best" connection for a peer (most recently active).
     * Used for outbound-initiated messages where any connection will do.
     *
     * @return the most recently active connection, or null if no connections exist
     */
    public PeerConnection getAnyConnection(String peerId) {
        ConcurrentHashMap<Long, PeerConnection> peerConns = connectionsByPeerId.get(peerId);
        if (peerConns == null || peerConns.isEmpty()) {
            return null;
        }

        // Pick the most recently active connection that is not CLOSED/CLOSING
        PeerConnection best = null;
        Instant bestActivity = Instant.MIN;
        for (PeerConnection conn : peerConns.values()) {
            ConnectionState state = conn.getState();
            if (state == ConnectionState.CLOSED || state == ConnectionState.CLOSING) {
                continue;
            }
            if (conn.getLastActivity().isAfter(bestActivity)) {
                bestActivity = conn.getLastActivity();
                best = conn;
            }
        }

        // Fallback: return any connection if all are closing/closed
        if (best == null && !peerConns.isEmpty()) {
            best = peerConns.values().iterator().next();
        }

        return best;
    }

    /**
     * Get connection by address.
     */
    public PeerConnection getByAddress(SocketAddress address) {
        Long connId = addressToConnId.get(address);
        return connId != null ? connectionsByConnId.get(connId) : null;
    }

    /**
     * Get the peer ID associated with an address.
     */
    public String getPeerIdByAddress(SocketAddress address) {
        Long connId = addressToConnId.get(address);
        if (connId != null) {
            PeerConnection conn = connectionsByConnId.get(connId);
            return conn != null ? conn.getPeerId() : null;
        }
        return null;
    }

    /**
     * Remove a single connection by connection ID.
     */
    public void removeConnection(long connectionId) {
        PeerConnection conn = connectionsByConnId.remove(connectionId);
        if (conn != null) {
            addressToConnId.remove(conn.getPeerAddress());

            // Remove from the per-peer map
            ConcurrentHashMap<Long, PeerConnection> peerConns =
                    connectionsByPeerId.get(conn.getPeerId());
            if (peerConns != null) {
                peerConns.remove(connectionId);
                // Clean up the peer entry if no connections remain
                if (peerConns.isEmpty()) {
                    connectionsByPeerId.remove(conn.getPeerId(), peerConns);
                }
            }

            log.debug("Removed connection {} for peer {}", connectionId, conn.getPeerId());
        }
    }

    /**
     * Remove all connections for a peer.
     *
     * @return the list of removed connections
     */
    public List<PeerConnection> removeAllConnections(String peerId) {
        ConcurrentHashMap<Long, PeerConnection> peerConns = connectionsByPeerId.remove(peerId);
        if (peerConns == null || peerConns.isEmpty()) {
            return Collections.emptyList();
        }

        List<PeerConnection> removed = new ArrayList<>(peerConns.values());
        for (PeerConnection conn : removed) {
            connectionsByConnId.remove(conn.getConnectionId());
            addressToConnId.remove(conn.getPeerAddress());
        }

        log.debug("Removed all {} connections for peer {}", removed.size(), peerId);
        return removed;
    }

    /**
     * Get all connections across all peers.
     */
    public Collection<PeerConnection> getAllConnections() {
        return Collections.unmodifiableCollection(connectionsByConnId.values());
    }

    /**
     * Get total connection count.
     */
    public int getConnectionCount() {
        return connectionsByConnId.size();
    }

    /**
     * Get connection count for a specific peer.
     */
    public int getConnectionCount(String peerId) {
        ConcurrentHashMap<Long, PeerConnection> peerConns = connectionsByPeerId.get(peerId);
        return peerConns != null ? peerConns.size() : 0;
    }

    /**
     * Check if any connection exists for a peer.
     */
    public boolean hasConnection(String peerId) {
        ConcurrentHashMap<Long, PeerConnection> peerConns = connectionsByPeerId.get(peerId);
        return peerConns != null && !peerConns.isEmpty();
    }

    /**
     * Get the set of all connected peer IDs.
     */
    public Set<String> getConnectedPeerIds() {
        return Collections.unmodifiableSet(connectionsByPeerId.keySet());
    }

    /**
     * Close all connections.
     */
    public void closeAll() {
        for (PeerConnection conn : connectionsByConnId.values()) {
            conn.setState(ConnectionState.CLOSED);
        }
        connectionsByPeerId.clear();
        connectionsByConnId.clear();
        addressToConnId.clear();
    }

    /**
     * Get statistics.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalConnections", connectionsByConnId.size());
        stats.put("totalPeers", connectionsByPeerId.size());

        int idle = 0, establishing = 0, established = 0, closing = 0;
        for (PeerConnection conn : connectionsByConnId.values()) {
            switch (conn.getState()) {
                case IDLE -> idle++;
                case ESTABLISHING -> establishing++;
                case ESTABLISHED -> established++;
                case CLOSING -> closing++;
                case CLOSED -> { /* should not occur in active map */ }
            }
        }

        stats.put("idle", idle);
        stats.put("establishing", establishing);
        stats.put("established", established);
        stats.put("closing", closing);

        return stats;
    }

    // ==================== Internal Helpers ====================

    /**
     * Generate a unique connection ID.
     */
    private long generateConnectionId() {
        return ByteUtils.randomLong();
    }

    /**
     * Evict the most idle (longest since last activity) connection for a peer.
     *
     * @return the evicted connection, or null if none could be evicted
     */
    private PeerConnection evictIdlestConnection(String peerId,
                                                  ConcurrentHashMap<Long, PeerConnection> peerConns) {
        PeerConnection idlest = null;
        Instant oldestActivity = Instant.MAX;

        for (PeerConnection conn : peerConns.values()) {
            ConnectionState state = conn.getState();
            // Prefer evicting IDLE or CLOSING connections
            if (state == ConnectionState.IDLE || state == ConnectionState.CLOSING
                    || state == ConnectionState.CLOSED) {
                if (conn.getLastActivity().isBefore(oldestActivity)) {
                    oldestActivity = conn.getLastActivity();
                    idlest = conn;
                }
            }
        }

        if (idlest != null) {
            removeConnection(idlest.getConnectionId());
        }
        return idlest;
    }

    /**
     * Evict the least recently used connection for a peer (regardless of state).
     */
    private PeerConnection evictLruConnection(String peerId,
                                               ConcurrentHashMap<Long, PeerConnection> peerConns) {
        PeerConnection lru = null;
        Instant oldestActivity = Instant.MAX;

        for (PeerConnection conn : peerConns.values()) {
            if (conn.getLastActivity().isBefore(oldestActivity)) {
                oldestActivity = conn.getLastActivity();
                lru = conn;
            }
        }

        if (lru != null) {
            removeConnection(lru.getConnectionId());
        }
        return lru;
    }
}
