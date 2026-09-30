package db.fcdsl;

/**
 * A query the engine will not run. The reason tells the server which code to answer with:
 * BAD_QUERY → 400, UNSUPPORTED and TOO_BROAD → 501.
 */
public class FcdslException extends RuntimeException {

    public enum Reason {
        /** The query is wrong: unknown field, bad value, malformed cursor. Resending it won't help. */
        BAD_QUERY,
        /** Valid FCDSL that this engine does not implement. */
        UNSUPPORTED,
        /** The query would examine more rows than allowed. Never answered with a short page. */
        TOO_BROAD
    }

    private final Reason reason;

    public FcdslException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    static FcdslException bad(String message) {
        return new FcdslException(Reason.BAD_QUERY, message);
    }
}
