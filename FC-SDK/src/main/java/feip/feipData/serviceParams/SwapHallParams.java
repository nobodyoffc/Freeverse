package feip.feipData.serviceParams;

import com.google.gson.Gson;

public class SwapHallParams extends Params{
    private String urlHead;
    private String currency;
    private String pricePerRequest;
    private String sessionDays;
    private String consumeViaShare;
    private String orderViaShare;
    private String uploadMultiplier;

    public static SwapHallParams fromObject(Object data) {
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(data), SwapHallParams.class);
    }

    public String getUrlHead() {
        return urlHead;
    }

    public void setUrlHead(String urlHead) {
        this.urlHead = urlHead;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getPricePerRequest() {
        return pricePerRequest;
    }

    public void setPricePerRequest(String pricePerRequest) {
        this.pricePerRequest = pricePerRequest;
    }

    public String getSessionDays() {
        return sessionDays;
    }

    public void setSessionDays(String sessionDays) {
        this.sessionDays = sessionDays;
    }

    public String getConsumeViaShare() {
        return consumeViaShare;
    }

    public void setConsumeViaShare(String consumeViaShare) {
        this.consumeViaShare = consumeViaShare;
    }

    public String getOrderViaShare() {
        return orderViaShare;
    }

    public void setOrderViaShare(String orderViaShare) {
        this.orderViaShare = orderViaShare;
    }

    public String getUploadMultiplier() {
        return uploadMultiplier;
    }

    public void setUploadMultiplier(String uploadMultiplier) {
        this.uploadMultiplier = uploadMultiplier;
    }

}