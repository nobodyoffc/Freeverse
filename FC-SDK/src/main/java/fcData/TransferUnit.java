package fcData;

import apip.apipData.RequestBody;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.google.gson.Gson;
import crypto.CryptoDataByte;
import crypto.KeyTools;
import javaTools.BytesTools;
import javaTools.DateTools;
import javaTools.Hex;
import javaTools.JsonTools;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class TransferUnit{

    private transient String id; //for database, time+nonce

    private final transient byte[] MAGIC = "FCTU".getBytes();  //for file
    private transient Integer size;  //for file
    private transient int sendCount; //for transfer

    //UDP 64KB-8
    private TransferUnitState stata; //For database

    //Basic fields
    private String from;//FID
    private ToType toType;
    private String to;
    private List<String> toList;
    private String dock; //1byte to mark the length

    //Nullable fields
    private transient Byte flag; //for toBytes
    private Long time;
    private Integer nonce;
    private String did;
    private DataType dataType;

    //Body
    private Object data;

    private transient byte[] bytes;

    @Test
    public void test(){
        TransferUnit transferUnit = new TransferUnit();
        transferUnit.from = "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK";
//        transferUnit.toType = ToType.FID;
        transferUnit.toType = ToType.GROUP_LIST;
//        transferUnit.to = "F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW";
        transferUnit.toList = new ArrayList<>();
        transferUnit.toList.add("db91fc9c16fcc9ae9330ac51b6a30442ab348ce61a43394c34c2612f88fa6019");
        transferUnit.toList.add("0be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a");
        transferUnit.dock = "TALK";
//        transferUnit.dataType = DataType.TEXT;
        transferUnit.dataType = DataType.SIGNED_TEXT;

        Signature signature = new Signature();
        signature.setFid("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
        signature.sign("hello world!",KeyTools.getPriKey32("L2bHRej6Fxxipvb4TiR5bu1rkT3tRp8yWEsUy4R1Zb8VMm2x7sd8"), AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7);

        transferUnit.data = signature;


        transferUnit.toBytes();

        System.out.println(Hex.toHex(transferUnit.bytes));

        TransferUnit transferUnit1 = TransferUnit.fromBytes(transferUnit.bytes);

        System.out.println(transferUnit1.toNiceJson());
    }

    public TransferUnit() {
        this.nonce = Math.abs(BytesTools.bytesToIntBE(BytesTools.getRandomBytes(4)));
        this.time = System.currentTimeMillis();
    }
//    public TransferUnit(String from, ToType toType, String to, DataType dataType) {
//        this.nonce = Math.abs(BytesTools.bytesToIntBE(BytesTools.getRandomBytes(4)));
//        this.time = System.currentTimeMillis();
//        this.from =from;
//        this.dataType = dataType;
//        this.toType = toType;
//        this.to = to;
//    }

    public static TransferUnit fromJson(String talkItemJson){
        return new Gson().fromJson(talkItemJson, TransferUnit.class);
    }

    public String toJson() {
        return JsonTools.toJson(this);
    }
    public String toNiceJson() {
        return JsonTools.toNiceJson(this);
    }
    @Override
    public String toString(){
        return toJson();
    }

    public static final String MAPPINGS = "{\"mappings\":{\"properties\":{\"toType\":{\"type\":\"keyword\"},\"to\":{\"type\":\"wildcard\"},\"door\":{\"type\":\"wildcard\"},\"time\":{\"type\":\"long\"},\"nonce\":{\"type\":\"integer\"},\"from\":{\"type\":\"wildcard\"},\"size\":{\"type\":\"long\"},\"dataType\":{\"type\":\"keyword\"},\"data\":{\"type\":\"text\"}}}}";

    public enum TransferUnitState{
        NEW((byte) 0),
        READY((byte) 1),
        SENT((byte) 2),
        RELAYING((byte) 3),
        GOT((byte) 4);

        //new, ready, sent, relaying，relayed, got,suspended
        public final byte number;
        TransferUnitState(byte number) {this.number=number;}
    }
    public enum ToType {
        SELF((byte)0),

        FID((byte)1),
        GROUP((byte)2),
        TEAM((byte)3),
        ROOM((byte)4),

        FID20_LIST((byte)5),
        GROUP_LIST((byte)6),
        TEAM_LIST((byte)7),
        ROOM_LIST((byte)8),

        SERVER((byte)9),
        ANYONE((byte)10),
        EVERYONE((byte)11);

        public final byte number;
        ToType(byte number) {this.number=number;}
        public static ToType getToType(byte number) {
            for (ToType toType : ToType.values()) {
                if (toType.number == number) {
                    return toType;
                }
            }
            return null;
        }
    }


    public enum DataType {
        BYTES((byte)0),
        TEXT((byte)1),
        HAT((byte)2),
        REQUEST((byte) 3),
        REPLY((byte)4),

        SIGNED_BYTES((byte)10),
        SIGNED_TEXT((byte)11),
        SIGNED_HAT((byte)12),
        SIGNED_REQUEST((byte)13),
        SIGNED_REPLY((byte)14),

        ENCRYPTED_BYTES((byte)20),
        ENCRYPTED_TEXT((byte)21),
        ENCRYPTED_HAT((byte)22),
        ENCRYPTED_REQUEST((byte)23),
        ENCRYPTED_REPLY((byte)24),

        ENCRYPTED_SIGNED_BYTES((byte)30),
        ENCRYPTED_SIGNED_TEXT((byte)31),
        ENCRYPTED_SIGNED_HAT((byte)32),
        ENCRYPTED_SIGNED_REQUEST((byte)33),
        ENCRYPTED_SIGNED_REPLY((byte)34);

        public final byte number;
        DataType(byte number) {this.number=number;}

        public static DataType getDataType(byte number) {
            for (DataType dataType : DataType.values()) {
                if (dataType.number == number) {
                    return dataType;
                }
            }
            return null;
        }
    }

    public byte[] toBytes(){

        try(ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder()){
            byteArrayBuilder.write(KeyTools.addrToHash160(this.from));

            byteArrayBuilder.write(this.toType.number);
            switch (this.toType){
                case SELF,SERVER,ANYONE,EVERYONE -> {}
                case FID -> byteArrayBuilder.write(KeyTools.addrToHash160(to));
                case GROUP,TEAM,ROOM -> Hex.fromHex(this.to);
                case FID20_LIST -> {
                    byteArrayBuilder.write(BytesTools.intToByteArray(this.toList.size()));
                    for(String fid:toList){
                        byteArrayBuilder.write(KeyTools.addrToHash160(fid));
                    }
                }
                case GROUP_LIST,TEAM_LIST,ROOM_LIST -> {
                    byteArrayBuilder.write(BytesTools.intToByteArray(this.toList.size()));
                    for(String id:toList){
                        byteArrayBuilder.write(Hex.fromHex(id));
                    }
                }
            }

            if(this.dock!=null){
                byte[] dockBytes = this.dock.getBytes();
                int size = dockBytes.length<256?dockBytes.length:255;
                byteArrayBuilder.write((byte)size);
                byteArrayBuilder.write(dockBytes);
            }

            if(this.flag==null)this.flag = 0;
            if(this.time!=null) this.flag  = BytesTools.setBit(flag,0,true);
            if(this.nonce!=null) this.flag =BytesTools.setBit(flag,1,true);
            if(this.did!=null) this.flag = BytesTools.setBit(flag,2,true);

            byteArrayBuilder.write(this.flag);

            if(this.time!=null) byteArrayBuilder.write(BytesTools.longToBytes(this.time));
            if(this.nonce!=null) byteArrayBuilder.write(BytesTools.intToByteArray(this.nonce));
            if(this.did!=null) byteArrayBuilder.write(Hex.fromHex(this.did));

            byteArrayBuilder.write(this.dataType.number);
            switch (this.dataType){
                case BYTES -> byteArrayBuilder.write((byte[]) data);
                case TEXT -> byteArrayBuilder.write(((String)data).getBytes());
                default -> byteArrayBuilder.write(JsonTools.toJson(data).getBytes());
            }

            this.bytes = byteArrayBuilder.toByteArray();
        }
        return this.bytes;
    }

    public static TransferUnit fromBytes(byte[] bytes) {
        TransferUnit transferUnit = new TransferUnit();
        transferUnit.time = null;
        transferUnit.nonce = null;

        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        // Extract from
        byte[] fromBytes = new byte[20];
        buffer.get(fromBytes);
        transferUnit.from = KeyTools.hash160ToFchAddr(fromBytes);

        // Extract ToType
        byte toTypeByte = buffer.get();
        transferUnit.toType = ToType.getToType(toTypeByte);

        // Extract 'to' based on ToType
        int toLength = 0;
        byte[] toBytes;
        switch (transferUnit.toType) {
            case FID -> {
                toLength = 20;
                toBytes = new byte[toLength];
                buffer.get(toBytes);
                transferUnit.to = KeyTools.hash160ToFchAddr(toBytes);
            }
            case GROUP, TEAM, ROOM -> {
                toLength = 32; // Hex
                toBytes = new byte[toLength];
                buffer.get(toBytes);
                transferUnit.to = Hex.toHex(toBytes);
            }
            case FID20_LIST -> {
                int sizeLength = 4;
                int itemLength = 20;
                byte[] sizeBytes = new byte[sizeLength];
                buffer.get(sizeBytes);
                int size = BytesTools.bytesToIntBE(sizeBytes);
                toBytes = new byte[itemLength];
                transferUnit.toList = new ArrayList<>();
                for(;size>0;size--) {
                    buffer.get(toBytes);
                    transferUnit.toList.add(KeyTools.hash160ToFchAddr(toBytes));
                }
            }

            case GROUP_LIST,TEAM_LIST,ROOM_LIST -> {
                int sizeLength = 4;
                int itemLength = 32;
                byte[] sizeBytes = new byte[sizeLength];
                buffer.get(sizeBytes);
                int size = BytesTools.bytesToIntBE(sizeBytes);
                toBytes = new byte[itemLength];
                transferUnit.toList = new ArrayList<>();
                for(;size>0;size--) {
                    buffer.get(toBytes);
                    transferUnit.toList.add(Hex.toHex(toBytes));
                }
            }
        }

        //Extract dock
        byte dockSize = buffer.get();
        if(dockSize!=0){
            byte[] dockBytes = new byte[dockSize];
            buffer.get(dockBytes);
            transferUnit.dock = new String(dockBytes);
        }

        byte flag = buffer.get();
        // Extract time
        if(Boolean.TRUE.equals(BytesTools.getBit(flag,0)))
            transferUnit.time = buffer.getLong();

        // Extract nonce
        if(Boolean.TRUE.equals(BytesTools.getBit(flag,1)))
            transferUnit.nonce = buffer.getInt();

        // Extract did
        if(Boolean.TRUE.equals(BytesTools.getBit(flag,2))) {
            byte[] didBytes = new byte[32];
            buffer.get(didBytes);
            transferUnit.did = new String(didBytes);
        }

        // Extract DataType
        byte dataTypeByte = buffer.get();
        transferUnit.dataType = DataType.getDataType(dataTypeByte);

        // Extract data
        int remaining = buffer.remaining();
        byte[] dataBytes = new byte[remaining];
        buffer.get(dataBytes);
        Gson gson = new Gson();
        switch (transferUnit.dataType){
            case BYTES -> transferUnit.data = dataBytes;
            case TEXT -> transferUnit.data = new String(dataBytes, StandardCharsets.UTF_8);
            case HAT -> transferUnit.data = gson.fromJson(new String(dataBytes, StandardCharsets.UTF_8), Hat.class);
            case REPLY -> transferUnit.data = gson.fromJson(new String(dataBytes, StandardCharsets.UTF_8), FcReplier.class);
            case REQUEST -> transferUnit.data = gson.fromJson(new String(dataBytes, StandardCharsets.UTF_8), RequestBody.class);
            case SIGNED_BYTES,
                    SIGNED_TEXT,
                    SIGNED_HAT,
                    SIGNED_REPLY,
                    SIGNED_REQUEST
                    -> transferUnit.data = gson.fromJson(new String(dataBytes, StandardCharsets.UTF_8), Signature.class);
            case ENCRYPTED_BYTES,
                    ENCRYPTED_TEXT,
                    ENCRYPTED_HAT,
                    ENCRYPTED_REPLY,
                    ENCRYPTED_REQUEST,

                    ENCRYPTED_SIGNED_BYTES,
                    ENCRYPTED_SIGNED_TEXT,
                    ENCRYPTED_SIGNED_HAT,
                    ENCRYPTED_SIGNED_REPLY,
                    ENCRYPTED_SIGNED_REQUEST
                    -> transferUnit.data = CryptoDataByte.fromJson(new String(dataBytes,StandardCharsets.UTF_8));
        }
        return transferUnit;
    }

    public String makeId() {
        int nonce;
        if(this.nonce!=null)nonce =this.nonce;
        else nonce= Math.abs(BytesTools.bytesToIntBE(BytesTools.getRandomBytes(4)));

        long time;
        if(this.time!=null)time=this.time;
        else time = System.currentTimeMillis();

        String date = DateTools.longToTime(time,"yyyyMMddHHmmssSSS");
        return date+"_"+nonce;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }
    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public Integer getNonce() {
        return nonce;
    }

    public void setNonce(Integer nonce) {
        this.nonce = nonce;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public ToType getToType() {
        return toType;
    }

    public void setToType(ToType toType) {
        this.toType = toType;
    }

    public DataType getDataType() {
        return dataType;
    }

    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }


    public String getDock() {
        return dock;
    }

    public void setDock(String dock) {
        this.dock = dock;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public byte[] getMAGIC() {
        return MAGIC;
    }

    public TransferUnitState getStata() {
        return stata;
    }

    public void setStata(TransferUnitState stata) {
        this.stata = stata;
    }

    public String getDid() {
        return did;
    }

    public void setDid(String did) {
        this.did = did;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public int getSendCount() {
        return sendCount;
    }

    public void setSendCount(int sendCount) {
        this.sendCount = sendCount;
    }

    public List<String> getToList() {
        return toList;
    }

    public void setToList(List<String> toList) {
        this.toList = toList;
    }

    public Byte getFlag() {
        return flag;
    }

    public void setFlag(Byte flag) {
        this.flag = flag;
    }
}
