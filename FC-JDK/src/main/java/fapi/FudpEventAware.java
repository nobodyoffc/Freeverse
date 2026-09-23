package fapi;

/**
 * A component that handles FUDP traffic beyond requests: DATAGRAM frames,
 * NOTIFYs and disconnects. {@code FapiServer} is the node's only listener
 * and passes these events to every component that implements this.
 * <p>
 * {@link #onDatagram} runs on the node's single receive thread and must
 * return quickly (FUDP7 §6).
 */
public interface FudpEventAware {

    default void onDatagram(String peerId, long connectionId, byte[] data) {}

    default void onNotifyReceived(String peerId, long messageId, int dataType, byte[] data) {}

    default void onPeerDisconnected(String peerId, long connectionId) {}
}
