package server.reward;

import static constants.Strings.REWARD;

public class RewardData {
    private String sid;
    private String op = REWARD;

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }
}
