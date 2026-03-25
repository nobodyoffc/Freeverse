package fudp.node;

/**
 * Event listener interface for FudpNode events.
 * Applications implement this to receive notifications about various events.
 */
public interface NodeEventListener {

    /**
     * Called when a peer connects.
     */
    default void onPeerConnected(String peerId) {}

    /**
     * Called when a peer disconnects.
     */
    default void onPeerDisconnected(String peerId) {}

    /**
     * Called when a chat message is received.
     */
    default void onChatReceived(String peerId, long messageId, String message) {}

    /**
     * Called when a chat acknowledgment is received.
     * @param peerId the peer who acknowledged the message
     * @param messageId the ID of the acknowledged message
     * @param rttMs round-trip time in milliseconds (from send to ACK received)
     */
    default void onChatAck(String peerId, long messageId, long rttMs) {
        // Default implementation calls the legacy method for backward compatibility
        onChatAck(peerId, messageId);
    }
    
    /**
     * Called when a chat acknowledgment is received (legacy, without RTT).
     * @deprecated Use {@link #onChatAck(String, long, long)} instead
     */
    @Deprecated
    default void onChatAck(String peerId, long messageId) {}

    /**
     * Called when a request is received.
     * Application should process the request and call node.respond() with the result.
     *
     * @param peerId The sender's FID
     * @param requestId The request ID (use this when responding)
     * @param serviceName The service being requested
     * @param data The request data
     */
    default void onRequestReceived(String peerId, long requestId, String serviceName, byte[] data) {}

    /**
     * Called when a file offer is received.
     * Application should call node.acceptFile() or node.rejectFile() to respond.
     */
    default void onFileOfferReceived(String peerId, FileOffer offer) {}

    /**
     * Called to report file transfer progress.
     */
    default void onFileProgress(String transferId, long transferred, long total) {}

    /**
     * Called when a file transfer completes successfully.
     */
    default void onFileComplete(String transferId, String filePath) {}

    /**
     * Called when a file transfer fails.
     */
    default void onFileError(String transferId, String error) {}

    /**
     * Called when a ping/pong completes (for latency measurement).
     */
    default void onPingComplete(String peerId, long rttMs) {}

    /**
     * Called when a pong carrying optional data is received.
     */
    default void onPongInfo(String peerId, byte[] data) {}

    /**
     * Called when an error occurs.
     */
    default void onError(String peerId, int errorCode, String message) {}

    // ============ Bytes Events ============

    /**
     * Called when a bytes message is received.
     * @param peerId the sender peer ID
     * @param messageId the message ID
     * @param dataType the data type hint (0=raw, 1=json, 2=protobuf, etc.)
     * @param data the raw byte array
     */
    default void onBytesReceived(String peerId, long messageId, int dataType, byte[] data) {}

    /**
     * Called when a bytes acknowledgment is received.
     * @param peerId the peer who acknowledged
     * @param messageId the ID of the acknowledged message
     * @param rttMs round-trip time in milliseconds
     */
    default void onBytesAck(String peerId, long messageId, long rttMs) {}

    // ============ Relay Events ============

    /**
     * Called when an anonymous relayed message is received.
     * Note: origin FID is NOT provided (privacy-preserving).
     * @param relayPeerId the relay node that delivered the message
     * @param message the inner message that was relayed
     */
    default void onRelayedMessageReceived(String relayPeerId, fudp.message.AppMessage message) {}

    /**
     * Called when an identified relayed message is received (sender revealed identity).
     * Used for bidirectional protocols like file transfer.
     * @param relayPeerId the relay node that delivered the message
     * @param senderFid the sender's FID (revealed for response routing)
     * @param sessionId the relay session ID (groups related messages)
     * @param message the inner message that was relayed
     */
    default void onRelayedMessageReceived(String relayPeerId, String senderFid, long sessionId, fudp.message.AppMessage message) {
        // Default: call anonymous version for backward compatibility
        onRelayedMessageReceived(relayPeerId, message);
    }

    /**
     * Called when a relay acknowledgment is received.
     * @param messageId the ID of the relayed message
     * @param rttMs round-trip time in milliseconds
     */
    default void onRelayAck(long messageId, long rttMs) {}

    /**
     * Called when a relay operation fails.
     * @param messageId the ID of the relayed message
     * @param errorCode the error code (see RelayErrorCode)
     * @param reason human-readable error description
     */
    default void onRelayFailed(long messageId, int errorCode, String reason) {}

    /**
     * File offer information.
     */
    class FileOffer {
        private final String transferId;
        private final String fileName;
        private final long fileSize;
        private final String fileHash;

        public FileOffer(String transferId, String fileName, long fileSize, String fileHash) {
            this.transferId = transferId;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.fileHash = fileHash;
        }

        public String getTransferId() {
            return transferId;
        }

        public String getFileName() {
            return fileName;
        }

        public long getFileSize() {
            return fileSize;
        }

        public String getFileHash() {
            return fileHash;
        }

        @Override
        public String toString() {
            return "FileOffer{" +
                    "transferId='" + transferId + '\'' +
                    ", fileName='" + fileName + '\'' +
                    ", fileSize=" + fileSize +
                    '}';
        }
    }
}
