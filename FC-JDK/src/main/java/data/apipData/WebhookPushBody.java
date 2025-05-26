package data.apipData;

import com.google.gson.Gson;
import data.fcData.FcSession;
import handlers.SessionHandler;
import org.jetbrains.annotations.Nullable;

import static data.fcData.Signature.symSign;

public class WebhookPushBody {
    private String hookUserId;
    private String method;
    private String sessionName;
    private String data;
    private String sign;
    private Long bestHeight;

    @Nullable
    public static WebhookPushBody checkWebhookPushBody(SessionHandler sessionHandler, byte[] requestBodyBytes) {
        WebhookPushBody webhookPushBody;

        try {
            webhookPushBody = new Gson().fromJson(new String(requestBodyBytes), WebhookPushBody.class);
            if (webhookPushBody ==null) return null;
        }catch (Exception ignore){
            return null;
        }

        FcSession session = sessionHandler.getSessionByName(webhookPushBody.getSessionName());
        String hookUserId = session.getUserId();
        String pushedHookUserId = webhookPushBody.getHookUserId();
        if(!hookUserId.equals(pushedHookUserId)) return null;


        String sign = symSign(webhookPushBody.getData(),session.getKey());
        if(!sign.equals(webhookPushBody.getSign()))return null;
        return webhookPushBody;
    }

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

    public Long getBestHeight() {
        return bestHeight;
    }

    public void setBestHeight(Long bestHeight) {
        this.bestHeight = bestHeight;
    }
}
