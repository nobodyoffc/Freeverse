package apip.apipData;

import com.google.gson.Gson;

public class WebhookPushBody {
    private String hookUserId;
    private String method;
    private String sessionName;
    private String data;
    private String sign;
    private Long sinceHeight;
    private Long bestHeight;
    public String toJson(){
        return new Gson().toJson(this);
    }


    public String getHookUserId() {
        return hookUserId;
    }

    public void setHookUserId(String hookUserId) {
        this.hookUserId = hookUserId;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getSessionName() {
        return sessionName;
    }

    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    public Long getSinceHeight() {
        return sinceHeight;
    }

    public void setSinceHeight(Long sinceHeight) {
        this.sinceHeight = sinceHeight;
    }

    public Long getBestHeight() {
        return bestHeight;
    }

    public void setBestHeight(Long bestHeight) {
        this.bestHeight = bestHeight;
    }
}
