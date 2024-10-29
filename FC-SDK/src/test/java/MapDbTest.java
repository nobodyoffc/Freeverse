import fcData.TalkUnit;
import javaTools.BytesTools;
import javaTools.Hex;
import org.mapdb.*;

import java.util.concurrent.ConcurrentMap;

public class MapDbTest {

    private static final String DEMO_KEY = "key";

    public static void main(String[] args) {
//        DB db = DBMaker.memoryDB().make();
//        ConcurrentMap map = db.hashMap("map").createOrOpen();
//        String key = "Hello";
//        String val = "simple";
//        map.put(key, val);
//        System.out.println("第1次取值，" + map.get(key));
//
//        db.close();

//            testTreeMap();

//        fileMapMemoryMapTest();

        byte[] bytes = "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK".getBytes();
        byte[] subBytes = "cjWK".getBytes();

        if(BytesTools.contains(bytes,subBytes)) System.out.println("Yes.");
    }

    @SuppressWarnings("unused")
private static void testTreeMap() {
        // Create or open a database file (it will persist data)
        DB db = DBMaker
                .fileDB("myTreeMap.db") // Path to the database file
                .fileMmapEnable()
                // Enable memory-mapped files for faster access
                .make();

        // Create or open a TreeMap
        BTreeMap<byte[], byte[]> treeMap = db.treeMap("talkUnitMap")
                .keySerializer(Serializer.BYTE_ARRAY)                // Use STRING serializer for keys
                .valueSerializer(Serializer.BYTE_ARRAY)                // Use TalkUnit as the custom serializer for values
                .createOrOpen();

        // Add some entries
        TalkUnit talkUnit = new TalkUnit();
        talkUnit.setFrom("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
        talkUnit.setTo("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
        talkUnit.setIdType(TalkUnit.IdType.FID);
        talkUnit.setDataType(TalkUnit.DataType.TEXT);
        talkUnit.setData("hi");
        byte[] byteId = talkUnit.makeIdBytes();
        treeMap.put(byteId,talkUnit.toBytes());

        db.commit();

        BTreeMap<byte[], byte[]> talkUnitMap2 =
                db.treeMap("talkUnitMap", Serializer.BYTE_ARRAY, Serializer.BYTE_ARRAY)
                .createOrOpen();


        for(byte[] id :talkUnitMap2.keySet()){
            byte[] bytes = talkUnitMap2.get(id);
            System.out.println(Hex.toHex(id) +":"+ TalkUnit.fromBytes(bytes).toJson());
            System.out.println("byte size:"+ bytes.length);
            System.out.println("Json size:"+talkUnit.toJson().getBytes().length);
        }
        db.close();
    }

    /**
     * 在64位操作系统中，开启内存映射
     * 个性化序列化
     */
    public static void fileMapMemoryMapTest() {
        DB db = DBMaker
                .fileDB("file.db")
                .fileMmapEnable()
                .transactionEnable()
                .make();
        ConcurrentMap<String,Long> map = db
                .hashMap("mapsl", Serializer.STRING, Serializer.LONG)
                .createOrOpen();
        long val = 51;
        map.put(DEMO_KEY, val);
        System.out.println("第1次取值，期望值：" + val + "，取到的值：" +map.get(DEMO_KEY));
        db.close();

        db = DBMaker
                .fileDB("file.db")
                .fileMmapEnable()
                .closeOnJvmShutdown() //JVM关闭时关闭db
                .make();
        map = db.hashMap("mapsl", Serializer.STRING, Serializer.LONG)
                .createOrOpen();
        System.out.println("第2次取值，期望值：" + val + "，取到的值：" +map.get(DEMO_KEY));
        db.close();
    }
}
