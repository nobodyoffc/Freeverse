package fcData;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
    public static int ID_DEFAULT_SHOW_SIZE = 13;
    public static int TEXT_DEFAULT_SHOW_SIZE = 33;
    public static int TEXT_SHORT_DEFAULT_SHOW_SIZE = 9;
    public static int TIME_DEFAULT_SHOW_SIZE = 15;
    public static int AMOUNT_DEFAULT_SHOW_SIZE = 8;
    public static int CD_DEFAULT_SHOW_SIZE = 5;
    public static int BOOLEAN_DEFAULT_SHOW_SIZE = 15;

    protected String id;
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
}
