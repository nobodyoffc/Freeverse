package nerve;

import constants.ApiNames;
import constants.NetNames;
import config.Settings;
import data.fcData.ReplyBody;
import initial.Initiator;
import utils.http.AuthType;
import utils.http.ApacheHttp;
import server.HttpRequestChecker;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@WebServlet(ApiNames.NERVE + ApiNames.GetPrices)
public class GetPrices extends HttpServlet {
    private final Settings settings;

    public GetPrices() {
        this.settings = Initiator.settings;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.SYMKEY_ENCRYPT;
        doRequest(request, response, authType, settings);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthType authType = AuthType.FREE;
        doRequest(request, response, authType, settings);
    }
    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) throws ServletException, IOException {
        //Check authorization
        try {
            HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings);
            boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
            if (!isOk) {
                return;
            }
            ReplyBody replier = httpRequestChecker.getReplyBody();
            
            // Fetch swap info from API
            double calculatedPrice = getPriceFromSwapInfo();
            
            Map<String,Double> prices = new HashMap<>();
            prices.put("fch/doge", calculatedPrice);
            replier.setTotal((long) prices.size());
            replier.setGot((long)prices.size());
            replier.setData(prices);
            replier.reply0SuccessHttp(prices,response);

        }catch (Exception e){
            e.printStackTrace();
        }
    }
    
    /**
     * Fetches swap info from API and calculates price using the first item
     * @return calculated price, or 0.05 as fallback
     */
    private double getPriceFromSwapInfo() {
        String apiUrl = "https://cid.cash/APIP/swapHall/v1/swapInfo";
        double defaultPrice = 0.05;
        
        try {
            // Make HTTP GET request
            ApacheHttp.Request request = new ApacheHttp.Request(apiUrl, NetNames.GET, null, null);
            ApacheHttp.Response response = ApacheHttp.request(request);
            
            if (response == null || response.isBadResponse() || response.getBody() == null) {
                System.out.println("Failed to fetch swap info from API: " + 
                    (response != null ? response.getCode() + " " + response.getMessage() : "null response"));
                return defaultPrice;
            }
            
            // Parse JSON response
            String responseBody = response.getBody();
            JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
            
            // Extract data array
            if (!jsonResponse.has("data") || !jsonResponse.get("data").isJsonArray()) {
                System.out.println("No data array found in API response");
                return defaultPrice;
            }
            
            JsonArray dataArray = jsonResponse.getAsJsonArray("data");
            if (dataArray.size() == 0) {
                System.out.println("Data array is empty");
                return defaultPrice;
            }
            
            // Get first item as JsonObject
            JsonObject firstItem = dataArray.get(0).getAsJsonObject();
            
            // Extract required fields and calculate price
            // JavaScript formula: (((moneySum) / (goodsSum - 1)) / (1 - swapFee - serviceFee))
            double mSum = getDoubleValueFromJson(firstItem.get("mSum"));
            double mPendingSum = getDoubleValueFromJson(firstItem.get("mPendingSum"));
            double gSum = getDoubleValueFromJson(firstItem.get("gSum"));
            double gPendingSum = getDoubleValueFromJson(firstItem.get("gPendingSum"));
            double swapFee = getDoubleValueFromJson(firstItem.get("swapFee"));
            double serviceFee = getDoubleValueFromJson(firstItem.get("serviceFee"));
            
            double moneySum = mSum + mPendingSum;
            double goodsSum = gSum + gPendingSum;
            
            // Apply the formula from JavaScript: (((moneySum) / (goodsSum - 1)) / (1 - swapFee - serviceFee))
            if (goodsSum <= 1 || (1 - swapFee - serviceFee) <= 0) {
                System.out.println("Invalid values for price calculation: goodsSum=" + goodsSum + 
                    ", swapFee=" + swapFee + ", serviceFee=" + serviceFee);
                return defaultPrice;
            }
            
            double denominator = 1 - swapFee - serviceFee;
            double price = ((moneySum / (goodsSum - 1)) / denominator);
            
            // Round to 8 decimal places (like toFixed(8) in JavaScript)
            price = Math.round(price * 100000000.0) / 100000000.0;
            
            return price;
            
        } catch (Exception e) {
            System.out.println("Error calculating price from swap info: " + e.getMessage());
            e.printStackTrace();
            return defaultPrice;
        }
    }
    
    private double getDoubleValueFromJson(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return 0.0;
        }
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isNumber()) {
                return element.getAsDouble();
            }
            if (element.getAsJsonPrimitive().isString()) {
                try {
                    return Double.parseDouble(element.getAsString());
                } catch (NumberFormatException e) {
                    return 0.0;
                }
            }
        }
        return 0.0;
    }
}