package data.feipData.serviceParams;

import clients.ApipClient;
import data.feipData.Service;
import ui.Inputer;
import com.google.gson.Gson;
import redis.clients.jedis.Jedis;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DiskParams {
    private String dataLifeDays;
    private String pricePerKBCarve;
    private String maxDataSize;
    private transient ApipClient apipClient;

    public DiskParams(){
        super();
    }

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
        if (this.dataLifeDays != null) map.put("dataLifeDays", this.dataLifeDays);
        if (this.pricePerKBCarve != null) map.put("pricePerKBytesPermanent", this.pricePerKBCarve);
        if (this.maxDataSize != null) map.put("maxDataSize", this.maxDataSize);
        return map;
    }

    public static DiskParams fromMap(Map<String, String> map) {
        DiskParams params = new DiskParams();
        params.dataLifeDays = map.get("dataLifeDays");
        params.pricePerKBCarve = map.get("pricePerKBytesPermanent");
        params.maxDataSize = map.get("maxDataSize");
        return params;
    }

    public void inputParams(BufferedReader br, byte[]symKey){
        this.dataLifeDays = Inputer.inputString(br,"Input the dataLifeDays:");
        this.pricePerKBCarve = Inputer.inputDoubleAsString(br,"Input the pricePerKBytesPermanent:");
        this.maxDataSize = Inputer.inputString(br,"Input the maxDataSize (bytes, e.g. 104857600 for 100MB):");
    }

    public DiskParams updateParams(BufferedReader br, byte[] symKey) {
        try {
            this.dataLifeDays = Inputer.promptAndUpdate(br,"dataLifeDays",this.dataLifeDays);
            this.pricePerKBCarve = Inputer.promptAndUpdate(br,"pricePerKBytesPermanent",this.pricePerKBCarve);
            this.maxDataSize = Inputer.promptAndUpdate(br,"maxDataSize",this.maxDataSize);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
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

    public String getPricePerKBCarve() {
        return pricePerKBCarve;
    }

    public void setPricePerKBCarve(String pricePerKBCarve) {
        this.pricePerKBCarve = pricePerKBCarve;
    }
    public String getMaxDataSize() {
        return maxDataSize;
    }

    public void setMaxDataSize(String maxDataSize) {
        this.maxDataSize = maxDataSize;
    }

    public ApipClient getApipClient() {
        return apipClient;
    }
}
