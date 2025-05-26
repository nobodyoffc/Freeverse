package data.feipData;

public enum FeipOp {
    PUBLISH("publish"),
    UPDATE("update"),
    STOP("stop"),
    RECOVER("recover"),
    CLOSE("close"),
    RATE("rate"),
    REGISTER("register"),
    UNREGISTER("unregister"),
    CREATE("create"),
    JOIN("join"),
    LEAVE("leave"),
    TRANSFER("transfer"),
    TAKE_OVER("take over"),
    AGREE_CONSENSUS("agree consensus"),
    INVITE("invite"),
    WITHDRAW_INVITATION("withdraw invitation"),
    DISMISS("dismiss"),
    APPOINT("appoint"),
    CANCEL_APPOINTMENT("cancel appointment"),
    DISBAND("disband"),
    ADD("add"),
    DELETE("delete"),
    SEND("send"),
    DEPLOY("deploy"),
    ISSUE("issue"),
    DESTROY("destroy"),
    SIGN("sign"),
    DROP("drop");

    private final String value;

    FeipOp(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static FeipOp fromString(String text) {
        for (FeipOp op : FeipOp.values()) {
            if (op.value.equalsIgnoreCase(text)) {
                return op;
            }
        }
        throw new IllegalArgumentException("No constant with text " + text + " found");
    }

    public interface FeipOpFields {
        String getValue();
        String[] getRequiredFields();
        String toLowerCase();
    }
}
