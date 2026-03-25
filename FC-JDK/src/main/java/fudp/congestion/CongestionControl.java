package fudp.congestion;

import java.util.concurrent.atomic.AtomicLong;

/**
 * CUBIC-based congestion control.
 *
 * Thread safety: onSend() is called from the sender thread, onAck() from the
 * ACK-processing thread, and canSend() from the sender thread.  All methods
 * that mutate shared state are synchronized to prevent lost updates on
 * compound read-modify-write sequences (e.g. bytesInFlight += n).
 */
public class CongestionControl {

    // Initial window: generous to avoid slow ramp-up (especially localhost)
    private static final long INITIAL_WINDOW = 120000;
    // Minimum window: must allow at least several MTU-sized packets in flight.
    // Too small (e.g. 2400 = less than 2 packets for 1350-byte MTU) causes
    // the sender to stall after a congestion collapse with almost no recovery.
    private static final long MIN_WINDOW = 14400; // ~10 packets for 1350-byte MTU
    private static final long MAX_WINDOW = 100_000_000; // 100 MB

    // CUBIC parameters
    private static final double BETA = 0.7;
    private static final double C = 0.4;

    private long congestionWindow;
    private long ssthresh;
    private final AtomicLong bytesInFlight = new AtomicLong(0);

    // CUBIC state
    private long wMax;
    private long epochStart;
    private State state;

    public enum State {
        SLOW_START,
        CONGESTION_AVOIDANCE,
        RECOVERY
    }

    public CongestionControl() {
        this.congestionWindow = INITIAL_WINDOW;
        this.ssthresh = Long.MAX_VALUE;
        this.state = State.SLOW_START;
        this.epochStart = System.currentTimeMillis();
    }

    /**
     * Called when bytes are acknowledged.
     */
    public synchronized void onAck(int ackedBytes) {
        bytesInFlight.addAndGet(-ackedBytes);
        if (bytesInFlight.get() < 0) bytesInFlight.set(0);

        switch (state) {
            case SLOW_START -> {
                congestionWindow += ackedBytes;
                if (congestionWindow >= ssthresh) {
                    state = State.CONGESTION_AVOIDANCE;
                    epochStart = System.currentTimeMillis();
                    wMax = congestionWindow;
                }
            }
            case CONGESTION_AVOIDANCE, RECOVERY -> {
                // CUBIC growth function
                double t = (System.currentTimeMillis() - epochStart) / 1000.0;
                double k = Math.cbrt(wMax * (1 - BETA) / C);
                double target = C * Math.pow(t - k, 3) + wMax;

                if (target > congestionWindow) {
                    congestionWindow = (long) target;
                }

                if (state == State.RECOVERY) {
                    state = State.CONGESTION_AVOIDANCE;
                }
            }
        }

        // Cap the window
        congestionWindow = Math.min(congestionWindow, MAX_WINDOW);
    }

    /**
     * Called when packet loss is detected.
     */
    public synchronized void onLoss() {
        wMax = congestionWindow;
        congestionWindow = (long) (congestionWindow * BETA);
        congestionWindow = Math.max(congestionWindow, MIN_WINDOW);
        ssthresh = congestionWindow;
        state = State.RECOVERY;
        epochStart = System.currentTimeMillis();
    }

    /**
     * Called when bytes are sent.  Uses AtomicLong so it never races with
     * onAck's decrement.
     */
    public void onSend(int sentBytes) {
        bytesInFlight.addAndGet(sentBytes);
    }

    /**
     * Called when a packet is removed for retransmission (not a successful ACK).
     * Only decrements bytesInFlight without any congestion window growth.
     * This prevents bytesInFlight leaks when packets are retransmitted:
     * the old packet's bytes are subtracted, and the new retransmitted packet
     * will add its bytes via onSend().
     */
    public void onRetransmitRemove(int removedBytes) {
        bytesInFlight.addAndGet(-removedBytes);
        if (bytesInFlight.get() < 0) bytesInFlight.set(0);
    }

    /**
     * Check if we can send more data.
     */
    public synchronized boolean canSend(int bytes) {
        return bytesInFlight.get() + bytes <= congestionWindow;
    }

    /**
     * Get available window.
     */
    public synchronized long getAvailableWindow() {
        return Math.max(0, congestionWindow - bytesInFlight.get());
    }

    public synchronized long getCongestionWindow() {
        return congestionWindow;
    }

    public long getBytesInFlight() {
        return bytesInFlight.get();
    }

    public synchronized State getState() {
        return state;
    }

    /**
     * Reset to initial state.
     */
    public synchronized void reset() {
        this.congestionWindow = INITIAL_WINDOW;
        this.ssthresh = Long.MAX_VALUE;
        this.bytesInFlight.set(0);
        this.state = State.SLOW_START;
        this.epochStart = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return String.format("CC[cwnd=%d, ssthresh=%d, inFlight=%d, state=%s]",
                congestionWindow, ssthresh, bytesInFlight.get(), state);
    }
}
