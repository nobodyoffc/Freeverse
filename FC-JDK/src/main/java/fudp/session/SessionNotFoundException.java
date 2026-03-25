package fudp.session;

/**
 * Exception thrown when a session key is not found during decryption.
 * Protocol layer should catch this and send SESSION_NOT_FOUND error to peer.
 */
public class SessionNotFoundException extends RuntimeException {

    private final byte[] keyName;

    public SessionNotFoundException(byte[] keyName) {
        super("Session not found for key name");
        this.keyName = keyName;
    }

    public byte[] getKeyName() {
        return keyName;
    }
}
