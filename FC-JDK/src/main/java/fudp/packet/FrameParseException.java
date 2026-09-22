package fudp.packet;

/**
 * A packet that decrypted and authenticated correctly but whose frames could
 * not be parsed — typically a frame type this implementation does not know.
 * Only that packet is lost; unlike a decrypt failure, it says nothing bad
 * about the source, so it must not count toward decrypt-failure rate limits.
 */
public class FrameParseException extends RuntimeException {

    private final String senderId;

    public FrameParseException(String senderId, Throwable cause) {
        super("Unparseable frames from " + senderId + ": " + cause.getMessage(), cause);
        this.senderId = senderId;
    }

    public String getSenderId() {
        return senderId;
    }
}
