package javaTools;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

// Example class that has a field which could be String or String[]
class GsonTest {
    private List<String> values;

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }
}

// Custom TypeAdapter to handle both String and String[] cases
class FlexibleStringAdapter implements JsonDeserializer<List<String>> {
    @Override
    public List<String> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        if (json.isJsonNull()) {
            return null;
        }

        // Handle single string
        if (json.isJsonPrimitive()) {
            return Arrays.asList(json.getAsString());
        }

        // Handle string array
        if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            String[] strings = new String[array.size()];
            for (int i = 0; i < array.size(); i++) {
                JsonElement element = array.get(i);
                strings[i] = element.isJsonNull() ? null : element.getAsString();
            }
            return Arrays.asList(strings);
        }

        throw new JsonParseException("Unexpected JSON type: " + json.getClass());
    }
}

// Usage example
public class GsonExample {
    public static void main(String[] args) {
        // Create Gson instance with custom adapter
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(new TypeToken<List<String>>(){}.getType(), new FlexibleStringAdapter())
                .create();

        // Test with string
        String json1 = "{\"values\": \"single value\"}";
        GsonTest obj1 = gson.fromJson(json1, GsonTest.class);
        System.out.println("String input: " + obj1.getValues());  // [single value]

        // Test with string array
        String json2 = "{\"values\": [\"value1\", \"value2\"]}";
        GsonTest obj2 = gson.fromJson(json2, GsonTest.class);
        System.out.println("Array input: " + obj2.getValues());  // [value1, value2]

        // Test with null
        String json3 = "{\"values\": null}";
        GsonTest obj3 = gson.fromJson(json3, GsonTest.class);
        System.out.println("Null input: " + obj3.getValues());  // null
    }
}
