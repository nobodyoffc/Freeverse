package constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequiredCdTest {

    @Test
    void belowCheckHeightNothingIsRequired() {
        assertEquals(0, Constants.requiredCd(3_600_000L, Constants.CD_REQUIRED));
        assertEquals(0, Constants.requiredCd(Constants.CDD_CHECK_HEIGHT - 1, 5));
    }

    @Test
    void fromCheckHeightTheRequestedCdApplies() {
        assertEquals(1, Constants.CD_REQUIRED);
        assertEquals(1, Constants.requiredCd(Constants.CDD_CHECK_HEIGHT, Constants.CD_REQUIRED));
        assertEquals(5, Constants.requiredCd(4_100_000L, 5));
    }

    @Test
    void unknownHeightKeepsTheRequestedCd() {
        assertEquals(1, Constants.requiredCd(null, 1));
        assertEquals(1, Constants.requiredCd(0L, 1));
    }

    @Test
    void zeroRequestStaysZero() {
        assertEquals(0, Constants.requiredCd(4_100_000L, 0));
    }
}
