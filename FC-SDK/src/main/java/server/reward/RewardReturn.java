package server.reward;

import java.util.HashMap;
import java.util.Map;

public class RewardReturn {
    private int code;
    private String msg;
    private Object data;

    public static Map<Integer,String> codeMsgMap;

    public static String codeToMsg(int code){
        return switch (code){
            case 0 -> "Success.";
            case 1 -> "Get account failed. Check the service parameters in redis.";
            case 2 -> "Failed to get last order id.";
            case 3 -> "No income.";
            case 4 -> "Failed to get reward parameters. Check redis.";
            case 5 -> "Making rewardInfo wrong.";
            case 6 -> "Failed to save affairSignTxJson to tomcat directory. Check tomcat directory.";
            case 7 -> "BackUp payment failed. Check ES.";
            case 8 -> "Failed to get cash list.";
            case 9 -> "The balance is insufficient to send rewards.";
            case 10 -> "Failed to make TX.";

            default -> throw new IllegalStateException("Unexpected value: " + code);
        };
    }

    public int getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public void setCode(int code) {
        this.code = code;
        this.msg = codeToMsg(code);
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
