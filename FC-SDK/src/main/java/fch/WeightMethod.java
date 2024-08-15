package fch;

public class WeightMethod {
    public static int cdPercentInWeight = 40;
    public static int cddPercentInWeight = 10;
    public static int repuPercentInWeight = 50;

    public static long calcWeight(long cd, long cdd, long reputation) {
        return (long) ((cd * cdPercentInWeight + cdd * cddPercentInWeight + reputation * repuPercentInWeight) / 100);
    }
}
