package feip.feipData.serviceParams;

import clients.apipClient.ApipClient;
import feip.feipData.Service;
import appTools.Inputer;
import com.google.gson.Gson;
import redis.clients.jedis.Jedis;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DiskParams extends Params {
    private String dataLifeDays;
    private String pricePerKBytesPermanent;
    private transient ApipClient apipClient;

    public DiskParams(){
        super();
    };

    public static DiskParams fromObject(Object data) {
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(data), DiskParams.class);
    }
    public void writeParamsToRedis(String key, Jedis jedis) {
        Map<String, String> paramMap = toMap();
        jedis.hmset(key, paramMap);
    }

    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>();
        toMap(map);
        if (this.dataLifeDays != null) map.put("dataLifeDays", this.dataLifeDays);
        if (this.pricePerKBytesPermanent != null) map.put("pricePerKBytesPermanent", this.pricePerKBytesPermanent);
        return map;
    }

    // Method to create DiskParams from Map
    public static DiskParams fromMap(Map<String, String> map) {
        DiskParams params = new DiskParams();
        params.fromMap(map, params);
        params.dataLifeDays = map.get("dataLifeDays");
        params.pricePerKBytesPermanent = map.get("pricePerKBytesPermanent");
        return params;
    }

    public void inputParams(BufferedReader br, byte[]symKey){
        inputParams(br,symKey,apipClient);
        this.dataLifeDays = Inputer.inputString(br,"Input the dataLifeDays:");
        this.pricePerKBytesPermanent = Inputer.inputDoubleAsString(br,"Input the pricePerKBytesPermanent:");
    }

    public void updateParams(BufferedReader br, byte[] symKey) {
        try {
            updateParams(br, symKey,apipClient );
            this.dataLifeDays = Inputer.promptAndUpdate(br,"Input the dataLifeDays:",this.dataLifeDays);
            this.pricePerKBytesPermanent = Inputer.promptAndUpdate(br,"Input the pricePerKBytesPermanent:",this.pricePerKBytesPermanent);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static DiskParams getParamsFromService(Service service) {
        DiskParams params;
        Gson gson = new Gson();
        try {
            params = gson.fromJson(gson.toJson(service.getParams()), DiskParams.class);
        }catch (Exception e){
            System.out.println("Parse maker parameters from Service wrong："+e.getMessage());
            return null;
        }
        service.setParams(params);
        return params;
    }


    public String getDataLifeDays() {
        return dataLifeDays;
    }

    public void setDataLifeDays(String dataLifeDays) {
        this.dataLifeDays = dataLifeDays;
    }

    public String getPricePerKBytesPermanent() {
        return pricePerKBytesPermanent;
    }

    public void setPricePerKBytesPermanent(String pricePerKBytesPermanent) {
        this.pricePerKBytesPermanent = pricePerKBytesPermanent;
    }
    public ApipClient getApipClient() {
        return apipClient;
    }
}
