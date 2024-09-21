package javaTools;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fcData.FcReplierHttp;
import feip.feipData.Cid;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.*;

public class ObjectTools {

    public static void main(String[] args) {
        FcReplierHttp fcReplierHttp = new FcReplierHttp();

        Cid cid = new Cid();
        cid.setCid("liu");
        cid.setHot(13424L);
        Map<String,Cid> map = new HashMap<>();
        map.put(cid.getCid(),cid);
        fcReplierHttp.setData(map);
        Gson gson = new Gson();
        String json = gson.toJson(fcReplierHttp);
        Object data = gson.fromJson(json, FcReplierHttp.class).getData();
        Map<String, Cid> newMap = objectToMap(data, String.class, Cid.class);
        JsonTools.printJson(newMap);
    }

    public static <K, T> Map<K, T> objectToMap(Object obj, Class<K> kClass, Class<T> tClass) {
        Gson gson = new Gson();
        Type type = TypeToken.getParameterized(Map.class, kClass, tClass).getType();
        try{
            return new HashMap<>(gson.fromJson(gson.toJson(obj), type));
        }catch (Exception e){
            return null;
        }
    }

    public static <K, T> Map<K, T> objectToLinkedHashMap(Object obj, Class<K> kClass, Class<T> tClass) {
        Gson gson = new Gson();
        Type type = TypeToken.getParameterized(Map.class, kClass, tClass).getType();
        try{
            return new LinkedHashMap<>(gson.fromJson(gson.toJson(obj), type));
        }catch (Exception e){
            return null;
        }
    }

    public static <T> List<T> objectToList(Object obj, Class<T> tClass) {
        Gson gson = new Gson();
        try{
            Type type = TypeToken.getParameterized(ArrayList.class, tClass).getType();
            String jsonString = gson.toJson(obj);
            List<T> tempList = gson.fromJson(jsonString, type);
            return new ArrayList<>(tempList);
        }catch (Exception e){
            return null;
        }
    }

    public static <T, K> Map<K, T> listToMap(List<T> list, String keyFieldName) {
        Map<K, T> resultMap = new HashMap<>();
        try {
            if (list != null && !list.isEmpty()) {
                Field keyField = list.get(0).getClass().getDeclaredField(keyFieldName);
                keyField.setAccessible(true);

                for (T item : list) {
                    @SuppressWarnings("unchecked")
                    K key = (K) keyField.get(item);
                    resultMap.put(key, item);
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return resultMap;
    }

    public static <T> T objectToClass(Object ob,Class<T> tClass) {
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(ob),tClass);
    }

    public static Map<String, Long> convertToLongMap(Map<String, String> stringStringMap) {
        Map<String, Long> nPriceMap = new HashMap<>();

        for (Map.Entry<String, String> entry : stringStringMap.entrySet()) {
            try {
                // Parse the String value to Long and put it in the new Map
                Long price = Long.parseLong(entry.getValue());
                nPriceMap.put(entry.getKey(), price);
            } catch (NumberFormatException e) {
                // Handle the case where parsing fails
                System.out.println("Error parsing value for key " + entry.getKey() + ": " + entry.getValue());
            }
        }
        return nPriceMap;
    }
}
