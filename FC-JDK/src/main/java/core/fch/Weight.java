package core.fch;

public class Weight {
    public static int cdPercentInWeight = 40;
    public static int cddPercentInWeight = 10;
    public static int reputationPercentInWeight = 50;

    public static long calcWeight(long cd, long cdd, long reputation) {
        return (cd * cdPercentInWeight + cdd * cddPercentInWeight + reputation * reputationPercentInWeight) / 100;
    }
}
