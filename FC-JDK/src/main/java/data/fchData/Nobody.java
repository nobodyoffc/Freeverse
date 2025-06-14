package data.fchData;

import data.fcData.FcObject;

import java.util.*;

import static constants.FieldNames.*;

public class Nobody extends FcObject {
    private String prikey;
    private Long leakTime;
    private Long leakHeight;
    private String leakTxId;
    private Integer leakTxIndex;


    public static LinkedHashMap<String,Integer> getFieldWidthMap(){
        LinkedHashMap<String,Integer> map = new LinkedHashMap<>();
        map.put(ID, DEFAULT_ID_LENGTH);
        map.put(PRIKEY, DEFAULT_ID_LENGTH);
        map.put(LEAK_TIME, DEFAULT_TIME_LENGTH);
        map.put(LEAK_TX_ID, DEFAULT_ID_LENGTH);
        return map;
    }
    public static List<String> getTimestampFieldList(){
        return List.of(LEAK_TIME);
    }

    public static List<String> getSatoshiFieldList(){
        return new ArrayList<>();
    }
    public static Map<String, String> getHeightToTimeFieldMap() {
        return new HashMap<>();
    }

    public static Map<String, String> getShowFieldNameAsMap() {
        Map<String,String> map = new HashMap<>();
        map.put(ID,FID);
        return map;
    }
    public static List<String> getReplaceWithMeFieldList() {
        return List.of(OWNER,ISSUER);
    }

    //For create with user input
    public static Map<String, Object> getInputFieldDefaultValueMap() {
        return new HashMap<>();
    }


    public String getPrikey() {
        return prikey;
    }

    public void setPrikey(String prikey) {
        this.prikey = prikey;
    }

    public Long getLeakTime() {
        return leakTime;
    }

    public void setLeakTime(Long leakTime) {
        this.leakTime = leakTime;
    }

    public Long getLeakHeight() {
        return leakHeight;
    }

    public void setLeakHeight(Long leakHeight) {
        this.leakHeight = leakHeight;
    }

    public String getLeakTxId() {
        return leakTxId;
    }

    public void setLeakTxId(String leakTxId) {
        this.leakTxId = leakTxId;
    }

    public Integer getLeakTxIndex() {
        return leakTxIndex;
    }

    public void setLeakTxIndex(Integer leakTxIndex) {
        this.leakTxIndex = leakTxIndex;
    }
}
