package fcData;

import appTools.Shower;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;
import org.mapdb.serializer.GroupSerializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class FcEntity {
    protected String id;
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
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
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

    public static void showList(List<FcEntity> list, String title, List<String> fields, List<Integer> widths) {
        if (fields == null || widths == null || fields.size() != widths.size()) {
            System.out.println("Invalid fields or widths configuration");
            return;
        }

        String[] fieldArray = fields.toArray(new String[0]);
        int[] widthArray = widths.stream().mapToInt(Integer::intValue).toArray();
        List<List<Object>> valueListList = new ArrayList<>();

        for (FcEntity obj : list) {
            List<Object> showList = new ArrayList<>();
            // Convert object to JSON to access fields dynamically
            @SuppressWarnings("unchecked")
            Map<String, Object> map = new Gson().fromJson(obj.toJson(), Map.class);

            for (String field : fields) {
                Object value = map.get(field);
                showList.add(value != null ? value : "");
            }
            valueListList.add(showList);
        }

        Shower.showDataTable(title, fieldArray, widthArray, valueListList, 0, true);
    }


    //MapDB Serializer
    public static <T extends FcEntity> Serializer<T> getMapDBSerializer(Class<T> clazz) {
        return new FcEntitySerializer<>(clazz);
    }

    // Update the serializer class to extend GroupSerializer
    public static class FcEntitySerializer<T extends FcEntity> implements GroupSerializer<T> {
        private final Class<T> entityClass;
        private final Gson gson = new Gson();

        public FcEntitySerializer(Class<T> clazz) {
            this.entityClass = clazz;
        }

        @Override
        public void serialize(DataOutput2 out, T value) throws IOException {
            byte[] bytes = value.toBytes();
            out.packInt(bytes.length);
            out.write(bytes);
        }

        @Override
        public T deserialize(DataInput2 input, int available) throws IOException {
            int length = input.unpackInt();
            byte[] bytes = new byte[length];
            input.readFully(bytes);
            return fromBytes(bytes, entityClass);
        }

        @Override
        public boolean equals(T a1, T a2) {
            if (a1 == a2) return true;
            if (a1 == null || a2 == null) return false;
            return gson.toJson(a1).equals(gson.toJson(a2));
        }

        @Override
        public int hashCode(T t, int seed) {
            return t == null ? 0 : gson.toJson(t).hashCode() + seed;
        }

        @Override
        public int compare(T o1, T o2) {
            if (o1 == o2) return 0;
            if (o1 == null) return -1;
            if (o2 == null) return 1;
            return gson.toJson(o1).compareTo(gson.toJson(o2));
        }

        @Override
        public boolean isTrusted() {
            return true;
        }

        public Class<T> getEntityClass() {
            return entityClass;
        }

        @Override
        public int valueArraySearch(Object keys, T key) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'valueArraySearch'");
        }

        @Override
        public int valueArraySearch(Object keys, T key, Comparator comparator) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'valueArraySearch'");
        }

        @Override
        public void valueArraySerialize(DataOutput2 out, Object vals) throws IOException {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'valueArraySerialize'");
        }

        @Override
        public Object valueArrayDeserialize(DataInput2 in, int size) throws IOException {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'valueArrayDeserialize'");
        }

        @Override
        public T valueArrayGet(Object vals, int pos) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'valueArrayGet'");
        }

        @Override
        public int valueArraySize(Object vals) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'valueArraySize'");
        }

        @Override
        public Object valueArrayEmpty() {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'valueArrayEmpty'");
        }

        @Override
        public Object valueArrayPut(Object vals, int pos, T newValue) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'valueArrayPut'");
        }

        @Override
        public Object valueArrayUpdateVal(Object vals, int pos, T newValue) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'valueArrayUpdateVal'");
        }

        @Override
        public Object valueArrayFromArray(Object[] objects) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'valueArrayFromArray'");
        }

        @Override
        public Object valueArrayCopyOfRange(Object vals, int from, int to) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'valueArrayCopyOfRange'");
        }

        @Override
        public Object valueArrayDeleteValue(Object vals, int pos) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'valueArrayDeleteValue'");
        }
    }

    public static class GsonSerializer implements Serializer<Object> {
        private final Gson gson = new GsonBuilder()
                // .serializeNulls()
                .create();
    
        @Override
        public void serialize(@NotNull DataOutput2 out, @NotNull Object value) throws IOException {
            String json = gson.toJson(value);
            out.writeUTF(json);
        }
    
        @Override
        public Object deserialize(@NotNull DataInput2 input, int available) throws IOException {
            String json = input.readUTF();
            // Try to determine the type from the JSON structure
            if (json.startsWith("[")) {
                return gson.fromJson(json, new TypeToken<Set<String>>(){}.getType());
            } else if (json.startsWith("{")) {
                return gson.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
            }
            return gson.fromJson(json, Object.class);
        }
    }

}
