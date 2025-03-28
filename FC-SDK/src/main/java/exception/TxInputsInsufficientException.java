package exception;

public class TxInputsInsufficientException extends Exception {

    public TxInputsInsufficientException() {
        super("The input value is insufficient.");
    }

    public TxInputsInsufficientException(String message, Throwable cause) {
        super(message, cause);
    }
}
