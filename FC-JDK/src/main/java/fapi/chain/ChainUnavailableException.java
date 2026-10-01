package fapi.chain;

/** The chain source could not answer. Nothing may be concluded from the missing answer. */
public class ChainUnavailableException extends RuntimeException {
    public ChainUnavailableException(String message) {
        super(message);
    }

    public ChainUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
