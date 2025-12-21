package data.fcData;

import data.fcData.FcEntity;
import data.fcData.FcObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static constants.FieldNames.*;
import static constants.Strings.TIME;

// Inner classes for Income and Expense
public class Income extends FcObject {
    private String from;
    private Long value;
    private Long time;
    private Long height;


    // Constructor and getters/setters
    public Income(String id, String from, Long value, Long time, Long height) {
        this.id = id;
        this.from = from;
        this.value = value;
        this.time = time;
        this.height = height;

    }

    public static LinkedHashMap<String, Integer> getFieldWidthMap() {
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
        map.put(ID, FcEntity.DEFAULT_ID_LENGTH);
        map.put(FROM, FcEntity.DEFAULT_ID_LENGTH);
        map.put(VALUE, FcEntity.DEFAULT_AMOUNT_LENGTH);
        map.put(TIME, FcEntity.DEFAULT_TIME_LENGTH);
        map.put(HEIGHT, FcEntity.DEFAULT_AMOUNT_LENGTH);
        return map;
    }

    public static List<String> getTimestampFieldList() {
        return List.of(TIME);
    }

    public static List<String> getSatoshiFieldList() {
        return List.of(VALUE);
    }

    public static Map<String, String> getHeightToTimeFieldMap() {
        return new HashMap<>();
    }

    public static Map<String, String> getShowFieldNameAsMap() {
        Map<String, String> map = new HashMap<>();
        map.put(ID, CASH_ID);
        return map;
    }

    public static List<String> getReplaceWithMeFieldList() {
        return List.of(OWNER, ISSUER);
    }

    public static Map<String, Object> getInputFieldDefaultValueMap() {
        return new HashMap<>();
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public Long getValue() {
        return value;
    }

    public void setValue(Long value) {
        this.value = value;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public Long getHeight() {
        return height;
    }

    public void setHeight(Long height) {
        this.height = height;
    }
}
