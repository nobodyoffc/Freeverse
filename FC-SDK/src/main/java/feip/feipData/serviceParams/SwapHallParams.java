package feip.feipData.serviceParams;

import clients.apipClient.ApipClient;
import com.google.gson.Gson;

import java.io.BufferedReader;

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

}