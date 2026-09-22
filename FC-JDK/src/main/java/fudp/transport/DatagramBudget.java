package fudp.transport;

/**
 * Per-connection send budget for DATAGRAM frames (FUDP7).
 * <p>
 * Datagrams are exempt from congestion control, so this token bucket is what
 * stops an application from flooding a path with them. It counts DATAGRAM
 * payload bytes (the application's bytes, not packet overhead). A datagram
 * that does not fit the budget is dropped at the sender, never delayed:
 * adapting to the network is the application's job.
 * <p>
 * The bucket holds {@link #BURST_MS} worth of budget, and never less than one
 * maximum-size datagram, so a single large datagram is always sendable on an
 * idle budget.
 */
public class DatagramBudget {

    /** Default rate: 256 kbps. */
    public static final long DEFAULT_RATE_BPS = 256_000;

    /** How much unused budget may accumulate, in milliseconds of rate. */
    static final long BURST_MS = 100;

    /** Floor on the bucket size, so any single datagram fits an idle budget. */
    static final long MIN_BURST_BYTES = 1500;

    private long rateBps;
    private long capacityBytes;
    private double tokens;
    private long lastRefillNanos;

    public DatagramBudget(long rateBps) {
        setRate(rateBps);
        this.tokens = capacityBytes;
    }

    /**
     * Change the rate. The bucket keeps what it holds, trimmed to the new size.
     *
     * @param rateBps bits per second of DATAGRAM payload; must be positive
     */
    public synchronized void setRate(long rateBps) {
        if (rateBps <= 0) {
            throw new IllegalArgumentException("Datagram rate must be positive: " + rateBps);
        }
        refill(System.nanoTime());
        this.rateBps = rateBps;
        this.capacityBytes = Math.max(MIN_BURST_BYTES, rateBps / 8 * BURST_MS / 1000);
        if (tokens > capacityBytes) tokens = capacityBytes;
    }

    public synchronized long getRate() {
        return rateBps;
    }

    /**
     * Take {@code bytes} from the budget if it holds that many.
     *
     * @return true if the datagram may be sent; false if it must be dropped
     */
    public synchronized boolean tryConsume(int bytes) {
        refill(System.nanoTime());
        if (tokens < bytes) return false;
        tokens -= bytes;
        return true;
    }

    private void refill(long now) {
        if (lastRefillNanos != 0) {
            double added = (now - lastRefillNanos) * (rateBps / 8.0) / 1_000_000_000.0;
            tokens = Math.min(capacityBytes, tokens + added);
        }
        lastRefillNanos = now;
    }
}
