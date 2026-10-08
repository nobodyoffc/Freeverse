package exception;

/**
 * A local database could not be opened, typically because another process holds its lock.
 *
 * Thrown instead of exiting the JVM: inside Tomcat, System.exit during webapp startup deadlocks
 * with Tomcat's own shutdown hook, leaving a process that serves nothing and cannot be stopped
 * with shutdown.sh. Web apps fail just that webapp; command-line apps exit through
 * {@link config.Starter#initiateOrExit}.
 */
public class LocalDbUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public LocalDbUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
