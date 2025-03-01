package exception;

public class IllegalPubKeyException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public IllegalPubKeyException() {
        super("The pubKey must be a non-empty string and valid hex32 format.");
    }

    public IllegalPubKeyException(String message) {
        super(message);
    }

    public IllegalPubKeyException(String message, Throwable cause) {
        super(message, cause);
    }
} 