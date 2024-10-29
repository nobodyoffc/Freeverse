package apip.apipData;

import crypto.Hash;

public class WebhookRequestBody {
    private String hookUserId;
    private String userId;
    private String method;
    private String endpoint;
    private Object data;
    private String op;

    public static String makeHookUserId(String sid, String newCashByFidsAPI, String userId) {
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

}
