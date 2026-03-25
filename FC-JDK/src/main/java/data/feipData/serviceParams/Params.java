package data.feipData.serviceParams;

import clients.ApipClient;
import data.feipData.Service;
import ui.Inputer;
import com.google.gson.Gson;
import utils.JsonUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;

public class Params {
    protected transient ApipClient apipClient;
    protected String pricePerKB;
    protected String minPayment;
    protected String pricePerRequest;
    protected String sessionDays;
    protected String urlHead;
    protected String consumeViaShare;
    protected String orderViaShare;
    protected String currency;

    public Params() {
    }


    // Helper method to search for a field in the current class and its superclasses
    private static Field findField(Class<?> tClass, String fieldName) {
        Class<?> currentClass = tClass;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField(fieldName);  // Look for the field in the current class
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();  // Move to the superclass
            }
        }
        return null;  // Field not found
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


    public void updateParams(BufferedReader br, byte[] symKey, ApipClient apipClient){
        try {
            this.urlHead = Inputer.promptAndUpdate(br,"urlHead",this.urlHead);
            this.currency = Inputer.promptAndUpdate(br,"currency",this.currency);
            this.pricePerKB = Inputer.promptAndUpdate(br, "pricePerKBytes", this.pricePerKB);
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
        this.pricePerKB = Inputer.inputDoubleAsString(br,"Input the pricePerKBytes:");
        this.minPayment = Inputer.inputDoubleAsString(br,"Input the minPayment:");
        this.consumeViaShare = Inputer.inputDoubleAsString(br,"Input the consumeViaShare:");
        this.orderViaShare = Inputer.inputDoubleAsString(br,"Input the orderViaShare:");
    }

    public void fromMap(Map<String, String> map, Params params) {
        params.currency = map.get("currency");
        params.consumeViaShare = map.get("consumeViaShare");
        params.orderViaShare = map.get("orderViaShare");
        params.pricePerKB = map.get("pricePerKBytes");
        params.minPayment = map.get("minPayment");
        params.pricePerRequest = map.get("pricePerRequest");
        params.sessionDays = map.get("sessionDays");
        params.urlHead = map.get("urlHead");
    }

    public void toMap(Map<String, String> map) {
        if (this.currency != null) map.put("currency", this.currency);
        if (this.consumeViaShare != null) map.put("consumeViaShare", this.consumeViaShare);
        if (this.orderViaShare != null) map.put("orderViaShare", this.orderViaShare);
        if (this.pricePerKB != null) map.put("pricePerKBytes", this.pricePerKB);
        if (this.minPayment != null) map.put("minPayment", this.minPayment);
        if (this.pricePerRequest != null) map.put("pricePerRequest", this.pricePerRequest);
        if (this.sessionDays != null) map.put("sessionDays", this.sessionDays);
        if (this.urlHead != null) map.put("urlHead", this.urlHead);
    }

    public String toJson(){
        return JsonUtils.toNiceJson(this);
    }


    public String getPricePerKB() {
        return pricePerKB;
    }

    public void setPricePerKB(String pricePerKB) {
        this.pricePerKB = pricePerKB;
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
