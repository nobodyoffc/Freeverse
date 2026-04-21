package fudp.congestion;

/**
 * RTT (Round-Trip Time) estimator using EWMA
 */
public class RttEstimator {

    /** 
     * Initial RTT estimate. 
     * Lowered from 333ms to 50ms for better performance on local/LAN networks.
     * This affects loss detection timing: packets are considered lost after 1.125 * smoothedRtt.
     */
    private static final long INITIAL_RTT_MS = 50;
    private static final long MIN_RTT_MS = 1;

    private long smoothedRtt;
    private long rttVariance;
    private long minRtt;
    private boolean firstSample = true;

    public RttEstimator() {
        this.smoothedRtt = INITIAL_RTT_MS;
        this.rttVariance = INITIAL_RTT_MS / 2;
        this.minRtt = Long.MAX_VALUE;
    }

    /**
     * Update RTT with a new sample
     * @param latestRtt RTT sample in milliseconds
     */
    public void updateRtt(long latestRtt) {
        if (latestRtt < MIN_RTT_MS) {
            latestRtt = MIN_RTT_MS;
        }

        // Update min RTT
        if (latestRtt < minRtt) {
            minRtt = latestRtt;
        }

        if (firstSample) {
            smoothedRtt = latestRtt;
            rttVariance = latestRtt / 2;
            firstSample = false;
            return;
        }

        // EWMA calculation
        // smoothedRtt = 7/8 * smoothedRtt + 1/8 * latestRtt
        // rttVariance = 3/4 * rttVariance + 1/4 * |smoothedRtt - latestRtt|

        long adjustedRtt = latestRtt;
        long rttDiff = Math.abs(smoothedRtt - adjustedRtt);

        rttVariance = (3 * rttVariance + rttDiff + 2) / 4;
        smoothedRtt = (7 * smoothedRtt + adjustedRtt + 4) / 8;
    }

    /**
     * Get the retransmission timeout
     * RTO = smoothedRtt + 4 * rttVariance
     */
    public long getRto() {
        long rto = smoothedRtt + 4 * rttVariance;
        // Minimum RTO of 1ms, maximum of 60 seconds
        return Math.max(1, Math.min(rto, 60000));
    }

    /**
     * Get smoothed RTT
     */
    public long getSmoothedRtt() {
        return smoothedRtt;
    }

    /**
     * Get RTT variance
     */
    public long getRttVariance() {
        return rttVariance;
    }

    /**
     * Get minimum RTT
     */
    public long getMinRtt() {
        return minRtt == Long.MAX_VALUE ? INITIAL_RTT_MS : minRtt;
    }

    /**
     * Reset the estimator
     */
    public void reset() {
        this.smoothedRtt = INITIAL_RTT_MS;
        this.rttVariance = INITIAL_RTT_MS / 2;
        this.minRtt = Long.MAX_VALUE;
        this.firstSample = true;
    }

    @Override
    public String toString() {
        return String.format("RTT[srtt=%dms, var=%dms, min=%dms, rto=%dms]",
                smoothedRtt, rttVariance, getMinRtt(), getRto());
    }
}
