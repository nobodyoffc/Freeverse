package core.fch;

public class Weight {
    public static int CD_WEIGHT = 40;
    public static int CDD_WEIGHT = 10;
    public static int REPUTATION_WEIGHT = 50;

    public static long calcWeight(long cd, long cdd, long reputation) {
        return (cd * CD_WEIGHT + cdd * CDD_WEIGHT + reputation * REPUTATION_WEIGHT) / 100;
    }
}
