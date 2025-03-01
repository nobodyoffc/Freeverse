package exception;

public class IllegalPriKeyException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public IllegalPriKeyException() {
        super("The priKey must be a 32-byte array.");
    }

    public IllegalPriKeyException(String message) {
        super(message);
    }

    public IllegalPriKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
