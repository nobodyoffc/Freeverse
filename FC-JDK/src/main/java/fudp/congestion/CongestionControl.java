package fudp.congestion;

/**
 * CUBIC-based congestion control
 */
public class CongestionControl {

    // Initial window: 10 * MSS (typical MSS = 1200 bytes)
    private static final long INITIAL_WINDOW = 12000;
    private static final long MIN_WINDOW = 2400;
    private static final long MAX_WINDOW = 100000000; // 100 MB

    // CUBIC parameters
    private static final double BETA = 0.7;
    private static final double C = 0.4;

    private long congestionWindow;
    private long ssthresh;
    private long bytesInFlight;

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
        this.bytesInFlight = 0;
        this.state = State.SLOW_START;
        this.epochStart = System.currentTimeMillis();
    }

    /**
     * Called when bytes are acknowledged
     */
    public void onAck(int ackedBytes) {
        bytesInFlight = Math.max(0, bytesInFlight - ackedBytes);

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
     * Called when packet loss is detected
     */
    public void onLoss() {
        wMax = congestionWindow;
        congestionWindow = (long) (congestionWindow * BETA);
        congestionWindow = Math.max(congestionWindow, MIN_WINDOW);
        ssthresh = congestionWindow;
        state = State.RECOVERY;
        epochStart = System.currentTimeMillis();
    }

    /**
     * Called when bytes are sent
     */
    public void onSend(int sentBytes) {
        bytesInFlight += sentBytes;
    }

    /**
     * Check if we can send more data
     */
    public boolean canSend(int bytes) {
        return bytesInFlight + bytes <= congestionWindow;
    }

    /**
     * Get available window
     */
    public long getAvailableWindow() {
        return Math.max(0, congestionWindow - bytesInFlight);
    }

    /**
     * Get congestion window
     */
    public long getCongestionWindow() {
        return congestionWindow;
    }

    /**
     * Get bytes in flight
     */
    public long getBytesInFlight() {
        return bytesInFlight;
    }

    /**
     * Get current state
     */
    public State getState() {
        return state;
    }

    /**
     * Reset to initial state
     */
    public void reset() {
        this.congestionWindow = INITIAL_WINDOW;
        this.ssthresh = Long.MAX_VALUE;
        this.bytesInFlight = 0;
        this.state = State.SLOW_START;
        this.epochStart = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return String.format("CC[cwnd=%d, ssthresh=%d, inFlight=%d, state=%s]",
                congestionWindow, ssthresh, bytesInFlight, state);
    }
}
