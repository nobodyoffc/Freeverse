package fudp.connection;

/**
 * Carries both peer identity and connection identity through the request processing pipeline.
 * <p>
 * Use {@code peerId} for identity-level operations (balance, access control).
 * Use {@code connectionId} for connection-affinity operations (respond, sendAck, routing).
 */
public record ConnectionContext(String peerId, long connectionId) {

    /**
     * Create a ConnectionContext from a PeerConnection.
     */
    public static ConnectionContext of(PeerConnection conn) {
        return new ConnectionContext(conn.getPeerId(), conn.getConnectionId());
    }
}
