package apip.apipData;

import crypto.Hash;

public class WebhookRequestBody {
    private String hookUserId;
    private String userName;
    private String method;
    private String endpoint;
    private Object data;
    private String op;
    private Long lastHeight;

    public static String makeHookUserId(String sid, String userId, String newCashByFidsAPI) {
        return Hash.sha256x2(sid+newCashByFidsAPI+userId);
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public String getHookUserId() {
        return hookUserId;
    }

    public void setHookUserId(String hookUserId) {
        this.hookUserId = hookUserId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Long getLastHeight() {
        return lastHeight;
    }

    public void setLastHeight(Long lastHeight) {
        this.lastHeight = lastHeight;
    }
}
