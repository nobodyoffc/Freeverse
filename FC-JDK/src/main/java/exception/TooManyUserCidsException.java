package exception;

public class TooManyUserCidsException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TooManyUserCidsException() {
        super("The number of userCids must be less than 4.");
    }

    public TooManyUserCidsException(String message) {
        super(message);
    }

    public TooManyUserCidsException(String message, Throwable cause) {
        super(message, cause);
    }
} 