package fcData;

import appTools.Shower;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;

public class FcData {

    public String toJson() {
        return new Gson().toJson(this);
    }

    public String toNiceJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }

    public static <T extends FcData> T fromJson(String json, Class<T> clazz) {
        Gson gson = new Gson();
        return gson.fromJson(json, clazz);
    }

    public byte[] toBytes() {
        Gson gson = new Gson();
        return gson.toJson(this).getBytes();
    }

    public static <T extends FcData> T fromBytes(byte[] bytes, Class<T> clazz) {
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

    public static Serializer<? extends FcData> serializer(Class<? extends FcData> clazz) {
        return new Serializer<FcData>() {
            @Override
            public void serialize(DataOutput2 out, FcData value) throws IOException {
                byte[] bytes = value.toBytes();
                out.writeInt(bytes.length);
                out.write(bytes);
            }

            @Override
            public FcData deserialize(DataInput2 input, int available) throws IOException {
                int length = input.readInt();
                byte[] bytes = new byte[length];
                input.readFully(bytes);
                return fromBytes(bytes, clazz);
            }
        };
    }

//    public byte[] toBytes() {
//        try {
//            MessagePack msgpack = new MessagePack();
//            msgpack.register(FcObject.class);
//            return msgpack.write(this);
//        } catch (Exception e) {
//            System.out.println("Failed to serialize object to MessagePack bytes:"+e.getMessage());
//        }
//        return null;
//    }

//    public static FcObject fromBytes(byte[] bytes) {
//        try {
//            MessagePack msgpack = new MessagePack();
//            msgpack.register(FcObject.class);
//            return msgpack.read(bytes, FcObject.class);
//        } catch (Exception e) {
//            System.out.println("Failed to deserialize MessagePack bytes to object:"+e.getMessage());
//        }
//        return null;
//    }

    public static void showList(List<FcData> list, String title, List<String> fields, List<Integer> widths) {
        if (fields == null || widths == null || fields.size() != widths.size()) {
            System.out.println("Invalid fields or widths configuration");
            return;
        }

        String[] fieldArray = fields.toArray(new String[0]);
        int[] widthArray = widths.stream().mapToInt(Integer::intValue).toArray();
        List<List<Object>> valueListList = new ArrayList<>();

        for (FcData obj : list) {
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

        Shower.showDataTable(title, fieldArray, widthArray, valueListList, 0);
    }

    public static <T extends FcData> Serializer<T> getSerializer(Class<T> clazz) {
        return new Serializer<T>() {
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
                return fromBytes(bytes, clazz);
            }
        };
    }

}
