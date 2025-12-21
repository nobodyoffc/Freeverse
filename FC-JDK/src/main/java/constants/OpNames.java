package constants;

public class OpNames {

    public static final String UPDATE = "update";
    public static final String FIND = "find";
    public static final String LIST_RECENT = "list";
    public static final String REGISTER = "register";
    public static final String UNREGISTER = "unregister";
    public static final String PUBLISH = "publish";
    public static final String CREATE = "create";
    public static final String LEAVE = "leave";

    public static final String STOP = "stop";
    public static final String RECOVER = "recover";
    public static final String CLOSE = "close";
    public static final String RATE = "rate";
    public static final String READ = "read";
    public static final String DELETE = "delete";
    public static final String ADD = "add";
    public static final String DISBAND = "disband";
    public static final String TRANSFER = "transfer";
    public static final String TAKE_OVER = "take over";
    public static final String DEPLOY = "deploy";
    public static final String ISSUE = "issue";
    public static final String DESTROY = "destroy";
    public static final String SIGN = "sign";
    public static final String DROP = "drop";
    public static final String ANNOUNCE = "announce";

    public static boolean contains(String value) {
        return value.equals(UPDATE) || value.equals(PUBLISH) || value.equals(STOP) ||
                value.equals(RECOVER) || value.equals(CLOSE) || value.equals(RATE);
    }

    public static String showAll() {
        return "[" + UPDATE + "," + PUBLISH + "," + STOP + "," + RECOVER + "," + CLOSE + "," + RATE + "]";
    }
}
