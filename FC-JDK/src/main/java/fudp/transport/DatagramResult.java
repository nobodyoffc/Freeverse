package fudp.transport;

/**
 * Outcome of sending one DATAGRAM frame. Every outcome other than
 * {@link #SENT} means the datagram was dropped; it is never queued or retried.
 */
public enum DatagramResult {
    /** Handed to the socket. */
    SENT,
    /** Datagrams are not enabled on this connection (peer capability unknown). */
    NOT_ENABLED,
    /** Larger than one packet can carry; datagrams are never fragmented. */
    TOO_LARGE,
    /** Over the connection's datagram rate budget. */
    OVER_BUDGET,
    /** The OS send buffer was full. */
    BUFFER_FULL,
    /** No such connection, or the transport is not running. */
    NO_CONNECTION
}
