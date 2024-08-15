package constants;

public class OpNames {

    public static final String UPDATE = "update";
    public static final String REGISTER = "register";
    public static final String UNREGISTER = "unregister";
    public static final String PUBLISH = "publish";
    public static final String STOP = "stop";
    public static final String RECOVER = "recover";
    public static final String CLOSE = "close";
    public static final String RATE = "rate";

    public static boolean contains(String value) {
        return value.equals(UPDATE) || value.equals(PUBLISH) || value.equals(STOP) ||
                value.equals(RECOVER) || value.equals(CLOSE) || value.equals(RATE);
    }

    public static String showAll() {
        return "[" + UPDATE + "," + PUBLISH + "," + STOP + "," + RECOVER + "," + CLOSE + "," + RATE + "]";
    }
}
