package data.fcData;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;
import utils.JsonUtils;

import java.io.File;
import java.util.*;

import static constants.Strings.DOT_JSON;


public abstract class FcEntity {
    public static final String METHOD_GET_FIELD_WIDTH_MAP = "getFieldWidthMap";
    public static final String METHOD_GET_TIMESTAMP_FIELD_LIST = "getTimestampFieldList";
    public static final String METHOD_GET_SATOSHI_FIELD_LIST = "getSatoshiFieldList";
    public static final String METHOD_GET_HEIGHT_TO_TIME_FIELD_MAP = "getHeightToTimeFieldMap";
    public static final String METHOD_GET_SHOW_FIELD_NAME_AS_MAP = "getShowFieldNameAsMap";
    public static final String METHOD_GET_INPUT_FIELD_DEFAULT_VALUE_MAP = "getInputFieldDefaultValueMap";
    public static final String METHOD_GET_REPLACE_WITH_ME_FIELD_LIST = "getReplaceWithMeFieldList";
    public static int DEFAULT_ID_LENGTH = 13;
    public static int DEFAULT_TEXT_LENGTH = 33;
    public static int DEFAULT_SHORT_TEXT_LENGTH = 9;
    public static int DEFAULT_TIME_LENGTH = 15;
    public static int DEFAULT_AMOUNT_LENGTH = 10;
    public static int DEFAULT_CD_LENGTH = 5;
    public static int DEFAULT_BOOLEAN_LENGTH = 5;

    protected String id;
    protected Meta meta;
    public static  <T extends FcEntity> int updateIntoListById(T item, List<T> itemList) {
        if(itemList==null)return -1;
        for (int i = 0; i < itemList.size(); i++) {
            if (itemList.get(i).getId().equals(item.getId())) {
                itemList.set(i, item); // Replace old keyInfo with new one
                return i;
            }
        }
        itemList.add(item);
        return itemList.size() - 1;
    }

    @NotNull
    public static <T> ShowingRules getRules(Class<T> itemClass) {
        LinkedHashMap<String, Integer> fieldWidthMap;
        List<String> timestampFieldList;
        List<String> satoshiField;
        Map<String, String> heightToTimeFieldMap;
        Map<String, String> showFieldNameAs;
        List<String> replaceWithMeFieldList;
        try {
            // Call static methods on the specific itemClass using reflection
            fieldWidthMap = (LinkedHashMap<String, Integer>) itemClass.getMethod(METHOD_GET_FIELD_WIDTH_MAP).invoke(null);
            timestampFieldList = (List<String>) itemClass.getMethod(METHOD_GET_TIMESTAMP_FIELD_LIST).invoke(null);
            satoshiField = (List<String>) itemClass.getMethod(METHOD_GET_SATOSHI_FIELD_LIST).invoke(null);
            heightToTimeFieldMap = (Map<String, String>) itemClass.getMethod(METHOD_GET_HEIGHT_TO_TIME_FIELD_MAP).invoke(null);
            showFieldNameAs = (Map<String, String>) itemClass.getMethod(METHOD_GET_SHOW_FIELD_NAME_AS_MAP).invoke(null);
            replaceWithMeFieldList = (List<String>) itemClass.getMethod(METHOD_GET_REPLACE_WITH_ME_FIELD_LIST).invoke(null);
        } catch (Exception e) {
            // If reflection fails, fall back to FcEntity defaults
            fieldWidthMap = getFieldWidthMap();
            timestampFieldList = getTimestampFieldList();
            satoshiField = getSatoshiFieldList();
            heightToTimeFieldMap = getHeightToTimeFieldMap();
            showFieldNameAs = getShowFieldNameAsMap();
            replaceWithMeFieldList = getReplaceWithMeFieldList();
        }
        ShowingRules result = new ShowingRules(fieldWidthMap, timestampFieldList, satoshiField, heightToTimeFieldMap, showFieldNameAs, replaceWithMeFieldList);
        return result;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
    public String toJson() {
        return new Gson().toJson(this);
    }

    public String toNiceJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        return gson.toJson(this);
    }

    public static <T extends FcEntity> T fromJson(String json, Class<T> clazz) {
        Gson gson = new Gson();
        return gson.fromJson(json, clazz);
    }

    public byte[] toBytes() {
        Gson gson = new Gson();
        return gson.toJson(this).getBytes();
    }

    public static <T extends FcEntity> T fromBytes(byte[] bytes, Class<T> clazz) {
        String json = new String(bytes);

        Gson gson = new Gson();
        try {
            return gson.fromJson(json, clazz);
        } catch (Exception e) {
            System.err.println("Failed to parse JSON: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static <T extends FcEntity> List<T> listFromFile(Class<T> clazz) {
        return listFromFile(null,clazz);
    }
    public static <T extends FcEntity> List<T> listFromFile(String filePath, Class<T> clazz) {
        if(filePath==null)filePath = clazz.getSimpleName()+".json";
        try {
            return JsonUtils.readJsonObjectListFromFile(filePath, clazz);
        } catch (Exception e) {
            System.out.println("Error reading "+clazz.getSimpleName()+" list from file: " + e.getMessage());
            return null;
        }
    }

    public static <T extends FcEntity> void listToFile(List<T> list, Class<T> clazz) {
        listToFile(list, clazz.getSimpleName(), false);
    }
    public static <T extends FcEntity> void listToFile(List<T> list, String className, boolean isAppend) {
        String filePath = className + DOT_JSON;
        if(list==null || list.isEmpty()){
            if(isAppend)return;
            else {
                File file = new File(filePath);
                if(file.exists())file.delete();
                return;
            }
        }
        try {
            JsonUtils.writeListToJsonFile(list, filePath, isAppend);
        } catch (Exception e) {
            System.out.println("Error writing "+list.get(0).getClass().getSimpleName()+" list to file: " + e.getMessage());
        }
        
    }
    /**
     * Get the field width map for displaying items
     * @return Map of field names to their display widths
     */
    public static LinkedHashMap<String, Integer> getFieldWidthMap() {
        return new LinkedHashMap<>();
    }

    /**
     * Get list of fields that contain timestamp values
     * @return List of timestamp field names
     */
    public static List<String> getTimestampFieldList() {
        return new ArrayList<>();
    }

    /**
     * Get list of fields that contain satoshi values
     * @return List of satoshi field names
     */
    public static List<String> getSatoshiFieldList() {
        return new ArrayList<>();
    }

    /**
     * Get map of height fields to their corresponding time fields
     * @return Map of height field names to time field names
     */
    public static Map<String, String> getHeightToTimeFieldMap() {
        return new HashMap<>();
    }

    /**
     * Get map of field names to their display names
     * @return Map of field names to display names
     */
    public static Map<String, String> getShowFieldNameAsMap() {
        return new HashMap<>();
    }

    /**
     * Get map of input field names to their default values
     * @return Map of input field names to default values
     */
    public static Map<String, Object> getInputFieldDefaultValueMap() {
        return new HashMap<>();
    }

    /**
     * Get map of input field names to their default values
     * @return Map of input field names to default values
     */
    public static List<String> getReplaceWithMeFieldList() {
        return new ArrayList<>();
    }

    public record ShowingRules(LinkedHashMap<String, Integer> fieldWidthMap, List<String> timestampFieldList, List<String> satoshiField, Map<String, String> heightToTimeFieldMap, Map<String, String> showFieldNameAsMap,List<String> replaceWithMeFieldList) {
    }

    public Meta getMeta() {
        return meta;
    }

    public void setMeta(Meta meta) {
        this.meta = meta;
    }
}
