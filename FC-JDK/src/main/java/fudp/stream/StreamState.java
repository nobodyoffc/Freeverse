package fudp.stream;

/**
 * Stream state machine
 */
public enum StreamState {
    IDLE,
    OPEN,
    HALF_CLOSED_LOCAL,   // Local side finished sending
    HALF_CLOSED_REMOTE,  // Remote side finished sending
    CLOSED
}
