package feip.feipData.serviceParams;

import feip.feipData.Service;
import appTools.Inputer;
import clients.apipClient.ApipClient;
import com.google.gson.Gson;
import redis.clients.jedis.Jedis;

import java.io.BufferedReader;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class TalkParams extends Params {

    public static TalkParams fromObject(Object data) {
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(data), TalkParams.class);
    }
    public void setParamsToRedis(String key, Jedis jedis) {
        Map<String, String> paramMap = new HashMap<>();
        for (Field field : TalkParams.class.getDeclaredFields()) {
            field.setAccessible(true); // to access private fields
            try {
                Object value = field.get(this);
                if(value!=null)
                    paramMap.put(field.getName(), String.valueOf(value));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        jedis.hmset(key, paramMap);
    }

    public static TalkParams getParamsFromService(Service service) {
        TalkParams params;
        Gson gson = new Gson();
        try {
            params = gson.fromJson(gson.toJson(service.getParams()), TalkParams.class);
        }catch (Exception e){
            System.out.println("Parse maker parameters from Service wrong.");
            return null;
        }
        service.setParams(params);
        return params;
    }

    public void inputParams(BufferedReader br, byte[]symKey){
        inputParams(br,symKey,this.apipClient);
        this.sessionDays = Inputer.inputDoubleAsString(br,"Input the sessionDays:");
    }
}
