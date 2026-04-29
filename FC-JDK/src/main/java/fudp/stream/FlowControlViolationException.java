package fudp.stream;

/**
 * Thrown when a peer pushes more out-of-order stream data than receiver-side
 * flow control permits. The transport layer treats this as a protocol
 * violation: drop the offending packet and close the connection.
 */
public class FlowControlViolationException extends RuntimeException {
    public FlowControlViolationException(String message) {
        super(message);
    }
}
