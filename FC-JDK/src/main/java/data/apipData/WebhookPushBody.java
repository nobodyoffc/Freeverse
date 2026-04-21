package data.apipData;

import com.google.gson.Gson;
import constants.CodeMessage;
import core.crypto.CryptoDataByte;
import core.crypto.Decryptor;
import org.jetbrains.annotations.Nullable;

public class WebhookPushBody {
    private String hookUserId;
    private String method;
    private Long bestHeight;
    private Integer code;
    private String message;
    private String encryptedData;

    @Nullable
    public static WebhookPushBody checkWebhookPushBody(byte[] requestBodyBytes, byte[] recipientPrikey, byte[] senderPubkey) {
        WebhookPushBody webhookPushBody;
        try {
            webhookPushBody = new Gson().fromJson(new String(requestBodyBytes), WebhookPushBody.class);
            if (webhookPushBody == null) return null;
        } catch (Exception ignore) {
            return null;
        }

        if (webhookPushBody.getCode() != null && webhookPushBody.getCode() != CodeMessage.Code0Success) {
            return webhookPushBody;
        }

        if (webhookPushBody.getEncryptedData() == null) return null;

        Decryptor decryptor = new Decryptor();
        CryptoDataByte result = decryptor.decryptJsonByAsyTwoWay(webhookPushBody.getEncryptedData(), recipientPrikey, senderPubkey);
        if (result == null || result.getData() == null) return null;

        webhookPushBody.setDecryptedData(new String(result.getData()));
        return webhookPushBody;
    }

    private transient String decryptedData;

    public String toJson() {
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

    public Long getBestHeight() {
        return bestHeight;
    }

    public void setBestHeight(Long bestHeight) {
        this.bestHeight = bestHeight;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getEncryptedData() {
        return encryptedData;
    }

    public void setEncryptedData(String encryptedData) {
        this.encryptedData = encryptedData;
    }

    public String getDecryptedData() {
        return decryptedData;
    }

    public void setDecryptedData(String decryptedData) {
        this.decryptedData = decryptedData;
    }
}
