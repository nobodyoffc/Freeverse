package swap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SwapDataGetter {
    public static List<SwapAffair> getSwapAffairList(Object responseData) {
        Type t = new TypeToken<ArrayList<SwapAffair>>() {}.getType();
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(responseData), t);
    }

    public static Map<String, SwapAffair> getSwapAffairMap(Object responseData) {
        Type t = new TypeToken<HashMap<String, SwapAffair>>() {}.getType();
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(responseData), t);
    }

    public static List<SwapPriceData> getSwapPriceList(Object responseData) {
        Type t = new TypeToken<ArrayList<SwapPriceData>>() {}.getType();
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(responseData), t);
    }

}