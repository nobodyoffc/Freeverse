package data.feipData.serviceParams;

import com.google.gson.Gson;
import redis.clients.jedis.Jedis;

import java.util.HashMap;
import java.util.Map;

public class ApipParams extends Params{

    public void writeParamsToRedis(String key, Jedis jedis) {
        Map<String, String> paramMap = toMap();
        jedis.hmset(key, paramMap);
    }

    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>();
        toMap(map);
        return map;
    }

    public static ApipParams fromObject(Object data) {
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(data), ApipParams.class);
    }
}