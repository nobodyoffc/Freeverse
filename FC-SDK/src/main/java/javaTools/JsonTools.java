package javaTools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class JsonTools {

    private static void sort(JsonElement e) {
        if (e.isJsonNull() || e.isJsonPrimitive()) {
            return;
        }

        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            Iterator<JsonElement> it = a.iterator();
            it.forEachRemaining(i -> sort(i));
            return;
        }

        if (e.isJsonObject()) {
            Map<String, JsonElement> tm = new TreeMap<>(getComparator());
            for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
                tm.put(en.getKey(), en.getValue());
            }

            String key;
            JsonElement val;
            for (Map.Entry<String, JsonElement> en : tm.entrySet()) {
                key = en.getKey();
                val = en.getValue();
                e.getAsJsonObject().remove(key);
                e.getAsJsonObject().add(key, val);
                sort(val);
            }
        }
    }

    public static <T, E> Type getMapType(Class<T> t, Class<E> e) {
        return new TypeToken<Map<T, E>>() {
        }.getType();
    }

    public static String toNiceJson(Object ob) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setPrettyPrinting();
        Gson gson = gsonBuilder.create();
        return gson.toJson(ob);
    }

    public static Map<String, String> getStringStringMap(String json) {
        Gson gson = new Gson();
        // Define the type of the map
        Type mapType = new TypeToken<Map<String, String>>() {
        }.getType();
        // Parse the JSON string back into a map
        Map<String, String> map = gson.fromJson(json, mapType);
        return map;
    }

    public static String toJson(Object ob) {
        return new Gson().toJson(ob);
    }


    public static void printJson(Object ob) {
        if(ob==null) {
            System.out.println("printJson: The object is null.");
            return;
        }
        System.out.println("----------\n" + ob.getClass().toString() + ": " + toNiceJson(ob) + "\n----------");
    }

    public static String strToJson(String rawStr) {
        if (!rawStr.contains("{")) return null;
        if (!rawStr.contains("}")) return null;
        int begin = rawStr.indexOf("{");
        int end = rawStr.lastIndexOf("}");
        rawStr = rawStr.substring(begin, end + 1);
        return rawStr.replaceAll("[\r\n\t]", "");
    }

    private static Comparator<String> getComparator() {
        return String::compareTo;
    }

    public static <T> T readObjectFromJsonFile(String filePath, String fileName, Class<T> tClass) throws IOException {
        File file = new File(filePath, fileName);
        if (!file.exists() || file.length() == 0) return null;
        Gson gson = new Gson();
        FileInputStream fis = new FileInputStream(file);
        byte[] configJsonBytes = new byte[fis.available()];
        fis.read(configJsonBytes);

        String configJson = new String(configJsonBytes);
        T t = gson.fromJson(configJson, tClass);
        fis.close();
        return t;
    }

    public static <K,T> Map<K, T> readMapFromJsonFile(String filePath, String fileName, Class<K> kClass,Class<T> tClass) throws IOException {
        File file = new File(filePath, fileName);
        if (!file.exists() || file.length() == 0) return null;
        FileInputStream fis = new FileInputStream(file);
        byte[] configJsonBytes = new byte[fis.available()];
        fis.read(configJsonBytes);

        String configJson = new String(configJsonBytes);

        Map<K, T> map = jsonToMap(configJson, kClass, tClass);

        fis.close();
        return map;
    }

    public static <T> void writeObjectToJsonFile(T obj, String fileName, boolean append) {
        FileTools.createFileDirectories(fileName);
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setPrettyPrinting();
        Gson gson = gsonBuilder.create();
        try (Writer writer = new FileWriter(fileName, append)) {
            gson.toJson(obj, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static <T> void writeObjectToJsonFile(T obj,String path, String fileName, boolean append) {
        writeObjectToJsonFile(obj,path+fileName,append);
    }

    public static <T> void writeObjectListToJsonFile(List<T> objList, String fileName, boolean append) {
        FileTools.createFileDirectories(fileName);
        Gson gson = new Gson();
        try (Writer writer = new FileWriter(fileName, append)) {
            objList.forEach(t -> gson.toJson(t, writer));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static <T> T readObjectFromJsonFile(FileInputStream fis, Class<T> tClass) throws IOException {

        T t;
        byte[] jsonBytes = readOneJsonFromFile(fis);
        if (jsonBytes == null) return null;

        Gson gson = new Gson();
        try {
            String json = new String(jsonBytes, StandardCharsets.UTF_8);
            t = gson.fromJson(json, tClass);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return t;
    }

    public static <T> T readOneJsonFromFile(String path,String fileName,Class<T> tClass) throws IOException {
        File file = new File(path,fileName);
        if(!file.exists())return null;
        FileInputStream fis = new FileInputStream(file);
        byte[] jsonBytes = readOneJsonFromFile(fis);
        if(jsonBytes==null) return null;
        String json = new String(jsonBytes,StandardCharsets.UTF_8);
        Gson gson = new Gson();
        T t;
        try {
            t = gson.fromJson(json, tClass);
        }catch (Exception e){
            return null;
        }
        return t;
    }

    @Nullable
    public static byte[] readOneJsonFromFile(FileInputStream fis) throws IOException {
        byte[] jsonBytes;
        ArrayList<Integer> jsonByteList = new ArrayList<>();

        int tip = 0;

        boolean counting = false;
        boolean ignore;
        int b;
        while (true) {
            b = fis.read();
            if (b < 0) return null;

            ignore = (char) b == '\\';

            if (ignore) {
                jsonByteList.add(b);
                continue;
            }

            if ((char) b == '{') {
                counting = true;
                tip++;
            } else {
                if ((char) b == '}' && counting) tip--;
            }

            jsonByteList.add(b);

            if (counting && tip == 0) {
                jsonBytes = new byte[jsonByteList.size()];
                int i = 0;
                for (int b1 : jsonByteList) {
                    jsonBytes[i] = (byte) b1;
                    i++;
                }
                counting = false;
                break;
            }
        }
        if (counting) return null;
        return jsonBytes;
    }

    public static String removeEscapes(String input) {
        StringBuilder result = new StringBuilder();
        boolean escape = false;

        for (char c : input.toCharArray()) {
            if (escape) {
                switch (c) {
                    case 'n' -> result.append('\n');
                    case 't' -> result.append('\t');
                    case 'r' -> result.append('\r');
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case '\\' -> result.append('\\');
                    default -> result.append(c);
                }
                escape = false;
            } else {
                if (c == '\\') {
                    escape = true;
                } else {
                    result.append(c);
                }
            }
        }

        return result.toString();
    }

    public static <T> T readJsonFromFile(String filePath, Class<T> classOfT) throws IOException {
        // Read file content
        String fileContent = new String(Files.readAllBytes(Paths.get(filePath)));

        // Create a Gson instance
        Gson gson = new Gson();

        // Deserialize the JSON string into an instance of class T
        return gson.fromJson(fileContent, classOfT);
    }

    public static <T> List<T> readJsonObjectListFromFile(String fileName, Class<T> clazz) {
        List<T> objects = new ArrayList<>();
        Gson gson = new Gson();

        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            StringBuilder jsonBuilder = new StringBuilder();

            int braceCount = 0;
            int ch;

            while ((ch = reader.read()) != -1) {
                char c = (char) ch;
                jsonBuilder.append(c);

                if (c == '{') {
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;

                    if (braceCount == 0) {
                        T obj = gson.fromJson(jsonBuilder.toString(), clazz);
                        objects.add(obj);
                        jsonBuilder = new StringBuilder();
                    }
                }
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return null;
        }
        return objects;
    }

    public static <T> List<T> readJsonObjectListFromFile(FileInputStream fis, int count, Class<T> clazz) {
        List<T> objectList = new ArrayList<>();
        Gson gson = new Gson();
        int i = 0;

        StringBuilder jsonBuilder = new StringBuilder();

        int braceCount = 0;
        int ch;

        while (true) {
            try {
                ch = fis.read();
            } catch (IOException e) {
                System.out.println("FileInputStream is wrong.");
                return null;
            }
            if (ch == -1) break;
            char c = (char) ch;
            jsonBuilder.append(c);

            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;

                if (braceCount == 0) {
                    T obj = gson.fromJson(jsonBuilder.toString(), clazz);
                    objectList.add(obj);
                    i++;
                    if (i == count) break;
                    jsonBuilder = new StringBuilder();
                }
            }
        }
        return objectList;
    }

    public static <K, T> Map<K, T> jsonToMap(String json, Class<K> kClass, Class<T> tClass) {
        Type type = TypeToken.getParameterized(Map.class, kClass, tClass).getType();
        try{
            return new HashMap<>(new Gson().fromJson(json, type));
        }catch (Exception e){
            return null;
        }
    }

    @Test
    public void strTest() {
        String str = "{\n\t\"f\":\"v\"\n}";
        byte[] b = str.getBytes();
        byte[] b1 = new byte[b.length + 1];
        for (int i = 0; i < b.length; i++) {
            b1[i] = b[i];
        }
        b1[b.length] = 0x00;

        String ns = new String(b1, StandardCharsets.UTF_8);
        System.out.println(str);
        System.out.println(strToJson(str));
        System.out.println(ns);
        System.out.println(ns.trim());
        System.out.println(strToJson(ns));
    }

    @Test
    public void testRemoveEscapes() {
        String input = "{\\n  \\\"type\\\": \\\"APIP\\\",\\n  \\\"sn\\\": \\\"0\\\",\\n  \\\"ver\\\": \\\"1\\\",\\n  \\\"name\\\": \\\"OpenAPI\\\",\\n  \\\"pid\\\": \\\"\\\",\\n  \\\"data\\\": {\\n    \\\"op\\\": \\\"buy\\\",\\n    \\\"sid\\\": \\\"46c1df926598cf0b881f0f1ab2ac6340826a5f954dd690786459c36388d6c131\\\"\\n  }\\n}";
        String cleanedString = removeEscapes(input);
        System.out.println("Original: " + input);
        System.out.println("Cleaned:  " + cleanedString);
    }
}

