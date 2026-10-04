package fapi.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BalanceVerifierTest {

    static BalanceVerifier verifier(BalanceVerifier.Action action) {
        return new BalanceVerifier(
                BalanceVerifier.DEFAULT_TOLERANCE_PCT, BalanceVerifier.DEFAULT_TOLERANCE_SAT_MIN,
                BalanceVerifier.DEFAULT_DRIFT_ACCUM_PCT, BalanceVerifier.DEFAULT_DRIFT_ACCUM_SAT,
                BalanceVerifier.DEFAULT_DRIFT_STOP_PCT, BalanceVerifier.DEFAULT_DRIFT_STOP_SAT,
                BalanceVerifier.DEFAULT_MAX_CONSECUTIVE_DRIFT, action);
    }

    /** The light server's log: an auto-recharge of 0.01 FCH lands on a balance of -6541. */
    @Test
    void aRechargeLandingIsNotDrift() {
        BalanceVerifier v = verifier(BalanceVerifier.Action.stop);
        assertEquals(BalanceVerifier.Result.Type.NOOP, v.observe(-6541L).getType());
        assertEquals(BalanceVerifier.Result.Type.NOOP, v.observe(993_449L).getType());
        assertFalse(v.isStopped());
        assertEquals(BalanceVerifier.Result.Type.NOOP, v.observe(993_439L).getType());
    }

    @Test
    void aLargeUnexplainedFallStillStops() {
        BalanceVerifier v = verifier(BalanceVerifier.Action.stop);
        v.observe(1_000_000L);
        assertEquals(BalanceVerifier.Result.Type.STOP, v.observe(800_000L).getType());
        assertTrue(v.isStopped());
    }

    /** With action warn, one big fall warns once; later replies are measured from the new balance. */
    @Test
    void aWarningStartsOverFromTheServersBalance() {
        BalanceVerifier v = verifier(BalanceVerifier.Action.warn);
        v.observe(1_000_000L);
        assertEquals(BalanceVerifier.Result.Type.WARN, v.observe(800_000L).getType());
        assertEquals(BalanceVerifier.Result.Type.NOOP, v.observe(799_990L).getType());
        assertFalse(v.isStopped());
    }
}
