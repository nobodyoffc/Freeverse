package feip.feipData.serviceParams;

import clients.ApipClient;
import feip.feipData.Service;
import appTools.Inputer;
import com.google.gson.Gson;
import utils.JsonUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;

public class Params {
    protected transient ApipClient apipClient;
    protected String dealer;
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

    public static Params mapToParams(Map<String, String> map) {
        return new Params() {
            {
                dealer = map.get("dealer");
                pricePerKBytes = map.get("pricePerKBytes");
                minPayment = map.get("minPayment");
                pricePerRequest = map.get("pricePerRequest");
                sessionDays = map.get("sessionDays");
                urlHead = map.get("urlHead");
                consumeViaShare = map.get("consumeViaShare");
                orderViaShare = map.get("orderViaShare");
                currency = map.get("currency");
            }
        };
    }

    public static <T extends Params> T mapToParams(Map<String, String> map, Class<T> tClass) {
        try {
            // Create a new instance of the given class
            T params = tClass.getDeclaredConstructor().newInstance();

            // Iterate over the map and set fields in the Params object
            for (Map.Entry<String, String> entry : map.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                try {
                    // Try to find the field in the current class or its superclasses
                    Field field = findField(tClass, key);

                    if (field != null) {
                        field.setAccessible(true);  // Allow access to private/protected fields
                        field.set(params, value);   // Set the field value
                    } else {
                        System.out.println("Field " + key + " does not exist in class " + tClass.getSimpleName());
                    }
                } catch (IllegalAccessException e) {
                    // Handle potential access issues
                    throw new RuntimeException("Failed to set field value for " + key, e);
                }
            }

            return params;
        } catch (Exception e) {
            throw new RuntimeException("Error mapping params", e);
        }
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

    public static Class<? extends Params> getParamsClassByApiType(Service.ServiceType type) {
        return switch (type){
            case NASA_RPC -> null;
            case APIP -> ApipParams.class;
            case ES -> null;
            case REDIS -> null;
            case DISK -> DiskParams.class;
            case OTHER -> null;
            case TALK -> TalkParams.class;
            case MAP -> null;
            case SWAP_HALL -> null;
            case FEIP -> null;
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
        if(Inputer.askIfYes(br,"The dealer is "+this.dealer +". Update it?")){
            return fch.Inputer.inputOrCreateFid("Input the dealer:",br,symKey,apipClient);
        }
        return this.dealer;
    }

    public void updateParams(BufferedReader br, byte[] symKey, ApipClient apipClient){
        try {
            this.urlHead = Inputer.promptAndUpdate(br,"urlHead",this.urlHead);
            this.currency = Inputer.promptAndUpdate(br,"currency",this.currency);
            this.dealer = updateAccount(br, symKey, apipClient);
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
        this.dealer = fch.Inputer.inputOrCreateFid("Input the dealer:",br,symKey, apipClient);
        this.pricePerKBytes = Inputer.inputDoubleAsString(br,"Input the pricePerKBytes:");
        this.minPayment = Inputer.inputDoubleAsString(br,"Input the minPayment:");
        this.consumeViaShare = Inputer.inputDoubleAsString(br,"Input the consumeViaShare:");
        this.orderViaShare = Inputer.inputDoubleAsString(br,"Input the orderViaShare:");
    }

    public void fromMap(Map<String, String> map, Params params) {
        params.currency = map.get("currency");
        params.consumeViaShare = map.get("consumeViaShare");
        params.orderViaShare = map.get("orderViaShare");
        params.dealer = map.get("dealer");
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
        if (this.dealer != null) map.put("dealer", this.dealer);
        if (this.pricePerKBytes != null) map.put("pricePerKBytes", this.pricePerKBytes);
        if (this.minPayment != null) map.put("minPayment", this.minPayment);
        if (this.pricePerRequest != null) map.put("pricePerRequest", this.pricePerRequest);
        if (this.sessionDays != null) map.put("sessionDays", this.sessionDays);
        if (this.urlHead != null) map.put("urlHead", this.urlHead);
    }

    public String toJson(){
        return JsonUtils.toNiceJson(this);
    }

    public String getDealer() {
        return dealer;
    }

    public void setDealer(String dealer) {
        this.dealer = dealer;
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
