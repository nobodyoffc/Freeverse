package fudp.node;

/**
 * Event listener interface for FudpNode events.
 * Applications implement this to receive notifications about various events.
 */
public interface NodeEventListener {

    /**
     * Called when a peer connection is established.
     * With multi-connection support, this fires per connection, not per peer.
     *
     * @param peerId       the peer's FID
     * @param connectionId the unique connection ID
     */
    default void onPeerConnected(String peerId, long connectionId) {}

    /**
     * Called when a peer connection is closed.
     * With multi-connection support, this fires per connection, not per peer.
     *
     * @param peerId       the peer's FID
     * @param connectionId the unique connection ID
     */
    default void onPeerDisconnected(String peerId, long connectionId) {}

    /**
     * Called when a request is received.
     * Application should process the request and call node.respond(connectionId, requestId, ...) with the result.
     *
     * @param peerId       The sender's FID
     * @param connectionId The connection ID the request arrived on (use for response routing)
     * @param requestId    The request ID (use this when responding)
     * @param serviceName  The service being requested
     * @param data         The request data
     */
    default void onRequestReceived(String peerId, long connectionId, long requestId, String serviceName, byte[] data) {}

    /**
     * Called when a notify message is received.
     * @param peerId the sender peer ID
     * @param messageId the message ID
     * @param dataType the data type hint (0=raw, 1=json, 2=protobuf, etc.)
     * @param data the raw byte array
     */
    default void onNotifyReceived(String peerId, long messageId, int dataType, byte[] data) {}

    /**
     * Called when a notify acknowledgment is received.
     * @param peerId the peer who acknowledged
     * @param messageId the ID of the acknowledged message
     * @param rttMs round-trip time in milliseconds
     */
    default void onNotifyAck(String peerId, long messageId, long rttMs) {}

    /**
     * Called when a ping/pong completes (for latency measurement).
     */
    default void onPingComplete(String peerId, long rttMs) {}

    /**
     * Called when a pong carrying optional data is received.
     */
    default void onPongInfo(String peerId, byte[] data) {}

    /**
     * Called when stream data is being assembled (progress indication for large transfers).
     * Fired each time a chunk is added to the assembler.
     *
     * @param peerId         the sender's FID
     * @param streamId       the stream being assembled
     * @param bytesAssembled total bytes assembled so far on this stream
     */
    default void onStreamAssemblyProgress(String peerId, long streamId, long bytesAssembled) {}

    /**
     * Called when an error occurs.
     */
    default void onError(String peerId, int errorCode, String message) {}
}
