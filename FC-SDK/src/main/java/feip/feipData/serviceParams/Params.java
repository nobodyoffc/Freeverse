package feip.feipData.serviceParams;

import clients.apipClient.ApipClient;
import configure.ServiceType;
import feip.feipData.Service;
import appTools.Inputer;
import com.google.gson.Gson;
import javaTools.JsonTools;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

public abstract class Params {
    protected transient ApipClient apipClient;
    protected String account;
    protected String pricePerKBytes;
    protected String minPayment;
    protected String pricePerRequest;
    protected String sessionDays;
    protected String urlHead;
    protected String consumeViaShare;
    protected String orderViaShare;
    protected String currency;

    public Params() {
    }

    public static Class<? extends Params> getParamsClassByApiType(ServiceType type) {
        return switch (type){
            case NASA_RPC -> null;
            case APIP -> ApipParams.class;
            case ES -> null;
            case REDIS -> null;
            case DISK -> DiskParams.class;
            case OTHER -> null;
            case TALK -> null;
            case MAP -> null;
            case SWAP_HALL -> null;
        };
    }

    public static <T> T getParamsFromService(Service service, Class<T> tClass) {
        T params;
        Gson gson = new Gson();
        try {
            params = gson.fromJson(gson.toJson(service.getParams()), tClass);
        }catch (Exception e){
            System.out.println("Parse parameters from Service wrong.");
            return null;
        }
        service.setParams(params);
        return params;
    }

    protected String updateAccount(BufferedReader br, byte[] symKey, ApipClient apipClient) {
        if(Inputer.askIfYes(br,"The account is "+this.account)){
            return fch.Inputer.inputOrCreateFid("Input the account:",br,symKey,apipClient);
        }
        return this.account;
    }

    public void updateParams(BufferedReader br, byte[] symKey, ApipClient apipClient){
        try {
            this.urlHead = Inputer.promptAndUpdate(br,"urlHead",this.urlHead);
            this.currency = Inputer.promptAndUpdate(br,"currency",this.currency);
            this.account = updateAccount(br, symKey, apipClient);
            this.pricePerKBytes = Inputer.promptAndUpdate(br, "pricePerKBytes", this.pricePerKBytes);
            this.minPayment = Inputer.promptAndUpdate(br,"minPayment",this.minPayment);
            this.sessionDays = Inputer.promptAndUpdate(br,"sessionDays",this.sessionDays);
            this.consumeViaShare = Inputer.promptAndUpdate(br,"consumeViaShare",this.consumeViaShare);
            this.orderViaShare = Inputer.promptAndUpdate(br,"orderViaShare",this.orderViaShare);
        } catch (IOException e) {
            System.out.println("Failed to updateParams. "+e.getMessage());
        }
    }

    public void inputParams(BufferedReader br, byte[]symKey, ApipClient apipClient){
        this.urlHead = Inputer.inputString(br,"Input the urlHead:");
        this.currency = Inputer.inputString(br,"Input the currency:");
        this.account = fch.Inputer.inputOrCreateFid("Input the account:",br,symKey, apipClient);
        this.pricePerKBytes = Inputer.inputDoubleAsString(br,"Input the pricePerKBytes:");
        this.minPayment = Inputer.inputDoubleAsString(br,"Input the minPayment:");
        this.consumeViaShare = Inputer.inputDoubleAsString(br,"Input the consumeViaShare:");
        this.orderViaShare = Inputer.inputDoubleAsString(br,"Input the orderViaShare:");
    }

    public void fromMap(Map<String, String> map, Params params) {
        params.currency = map.get("currency");
        params.consumeViaShare = map.get("consumeViaShare");
        params.orderViaShare = map.get("orderViaShare");
        params.account = map.get("account");
        params.pricePerKBytes = map.get("pricePerKBytes");
        params.minPayment = map.get("minPayment");
        params.pricePerRequest = map.get("pricePerRequest");
        params.sessionDays = map.get("sessionDays");
        params.urlHead = map.get("urlHead");
    }

    public void toMap(Map<String, String> map) {
        if (this.currency != null) map.put("currency", this.currency);
        if (this.consumeViaShare != null) map.put("consumeViaShare", this.consumeViaShare);
        if (this.orderViaShare != null) map.put("orderViaShare", this.orderViaShare);
        if (this.account != null) map.put("account", this.account);
        if (this.pricePerKBytes != null) map.put("pricePerKBytes", this.pricePerKBytes);
        if (this.minPayment != null) map.put("minPayment", this.minPayment);
        if (this.pricePerRequest != null) map.put("pricePerRequest", this.pricePerRequest);
        if (this.sessionDays != null) map.put("sessionDays", this.sessionDays);
        if (this.urlHead != null) map.put("urlHead", this.urlHead);
    }

    public String toJson(){
        return JsonTools.toNiceJson(this);
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPricePerKBytes() {
        return pricePerKBytes;
    }

    public void setPricePerKBytes(String pricePerKBytes) {
        this.pricePerKBytes = pricePerKBytes;
    }

    public String getMinPayment() {
        return minPayment;
    }

    public void setMinPayment(String minPayment) {
        this.minPayment = minPayment;
    }

    public String getPricePerRequest() {
        return pricePerRequest;
    }

    public void setPricePerRequest(String pricePerRequest) {
        this.pricePerRequest = pricePerRequest;
    }

    public String getUrlHead() {
        return urlHead;
    }

    public void setUrlHead(String urlHead) {
        this.urlHead = urlHead;
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

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
