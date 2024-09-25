package fctp;

import apip.apipData.RequestBody;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.google.gson.Gson;
import crypto.CryptoDataByte;
import crypto.KeyTools;
import fcData.AlgorithmId;
import fcData.FcReplier;
import fcData.Hat;
import fcData.Signature;
import javaTools.BytesTools;
import javaTools.DateTools;
import javaTools.Hex;
import javaTools.JsonTools;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MsgUnit implements Comparable<MsgUnit> {

    private transient String idStr; //for database, time+nonce
    private transient BytesTools.ByteArrayAsKey idBytes;

    private final transient byte[] MAGIC = "FCTU".getBytes();  //for file
    private transient Integer size;  //for file
    private transient int sendCount; //for transfer

    //UDP 64KB-8
    private MsgUnitState stata; //For database


    /*
        flag
            0 asm ? sym encrypted?
            1 slice?
            2-7 reserved
        keyName //for decrypt

        tId  //for slice
        tSize
        offset

        msgUnit
            receipt

     */

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

    public void sendCountAddOne(){
        this.sendCount++;
    }

    @Test
    public void test(){
        MsgUnit msgUnit = new MsgUnit();
        msgUnit.from = "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK";
//        transferUnit.toType = ToType.FID;
        msgUnit.toType = ToType.GROUP_LIST;
//        transferUnit.to = "F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW";
        msgUnit.toList = new ArrayList<>();
        msgUnit.toList.add("db91fc9c16fcc9ae9330ac51b6a30442ab348ce61a43394c34c2612f88fa6019");
        msgUnit.toList.add("0be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a");
        msgUnit.dock = "TALK";
//        transferUnit.dataType = DataType.TEXT;
        msgUnit.dataType = DataType.SIGNED_TEXT;

        Signature signature = new Signature();
        signature.setFid("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
        signature.sign("hello world!",KeyTools.getPriKey32("L2bHRej6Fxxipvb4TiR5bu1rkT3tRp8yWEsUy4R1Zb8VMm2x7sd8"), AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7);

        msgUnit.data = signature;


        msgUnit.toBytes();

        System.out.println(Hex.toHex(msgUnit.bytes));

        MsgUnit msgUnit1 = MsgUnit.fromBytes(msgUnit.bytes);

        System.out.println(msgUnit1.toNiceJson());
    }

    public MsgUnit() {
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

    public static MsgUnit fromJson(String talkItemJson){
        return new Gson().fromJson(talkItemJson, MsgUnit.class);
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
    @Override
    public int compareTo(@NotNull MsgUnit other) {
        return Arrays.compare(this.idBytes.getBytes(),other.getIdBytes().getBytes());
    }

    public enum MsgUnitState {
        NEW((byte) 0),
        READY((byte) 1),
        SENT((byte) 2),
        RELAYING((byte) 3),
        GOT((byte) 4);

        //new, ready, sent, relaying，relayed, got,suspended
        public final byte number;
        MsgUnitState(byte number) {this.number=number;}
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
        RECEIPT((byte)5),

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
                case BYTES,RECEIPT -> byteArrayBuilder.write((byte[]) data);
                case TEXT -> byteArrayBuilder.write(((String)data).getBytes());
                default -> byteArrayBuilder.write(JsonTools.toJson(data).getBytes());
            }

            this.bytes = byteArrayBuilder.toByteArray();
        }
        return this.bytes;
    }

    public static MsgUnit fromBytes(byte[] bytes) {
        MsgUnit msgUnit = new MsgUnit();
        msgUnit.time = null;
        msgUnit.nonce = null;

        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        // Extract from
        byte[] fromBytes = new byte[20];
        buffer.get(fromBytes);
        msgUnit.from = KeyTools.hash160ToFchAddr(fromBytes);

        // Extract ToType
        byte toTypeByte = buffer.get();
        msgUnit.toType = ToType.getToType(toTypeByte);

        // Extract 'to' based on ToType
        int toLength = 0;
        byte[] toBytes;
        switch (msgUnit.toType) {
            case FID -> {
                toLength = 20;
                toBytes = new byte[toLength];
                buffer.get(toBytes);
                msgUnit.to = KeyTools.hash160ToFchAddr(toBytes);
            }
            case GROUP, TEAM, ROOM -> {
                toLength = 32; // Hex
                toBytes = new byte[toLength];
                buffer.get(toBytes);
                msgUnit.to = Hex.toHex(toBytes);
            }
            case FID20_LIST -> {
                int sizeLength = 4;
                int itemLength = 20;
                byte[] sizeBytes = new byte[sizeLength];
                buffer.get(sizeBytes);
                int size = BytesTools.bytesToIntBE(sizeBytes);
                toBytes = new byte[itemLength];
                msgUnit.toList = new ArrayList<>();
                for(;size>0;size--) {
                    buffer.get(toBytes);
                    msgUnit.toList.add(KeyTools.hash160ToFchAddr(toBytes));
                }
            }

            case GROUP_LIST,TEAM_LIST,ROOM_LIST -> {
                int sizeLength = 4;
                int itemLength = 32;
                byte[] sizeBytes = new byte[sizeLength];
                buffer.get(sizeBytes);
                int size = BytesTools.bytesToIntBE(sizeBytes);
                toBytes = new byte[itemLength];
                msgUnit.toList = new ArrayList<>();
                for(;size>0;size--) {
                    buffer.get(toBytes);
                    msgUnit.toList.add(Hex.toHex(toBytes));
                }
            }
        }

        //Extract dock
        byte dockSize = buffer.get();
        if(dockSize!=0){
            byte[] dockBytes = new byte[dockSize];
            buffer.get(dockBytes);
            msgUnit.dock = new String(dockBytes);
        }

        byte flag = buffer.get();
        // Extract time
        if(Boolean.TRUE.equals(BytesTools.getBit(flag,0)))
            msgUnit.time = buffer.getLong();

        // Extract nonce
        if(Boolean.TRUE.equals(BytesTools.getBit(flag,1)))
            msgUnit.nonce = buffer.getInt();

        // Extract did
        if(Boolean.TRUE.equals(BytesTools.getBit(flag,2))) {
            byte[] didBytes = new byte[32];
            buffer.get(didBytes);
            msgUnit.did = new String(didBytes);
        }

        // Extract DataType
        byte dataTypeByte = buffer.get();
        msgUnit.dataType = DataType.getDataType(dataTypeByte);

        // Extract data
        int remaining = buffer.remaining();
        byte[] dataBytes = new byte[remaining];
        buffer.get(dataBytes);
        Gson gson = new Gson();
        switch (msgUnit.dataType){
            case BYTES,RECEIPT -> msgUnit.data = dataBytes;
            case TEXT -> msgUnit.data = new String(dataBytes, StandardCharsets.UTF_8);
            case HAT -> msgUnit.data = gson.fromJson(new String(dataBytes, StandardCharsets.UTF_8), Hat.class);
            case REPLY -> msgUnit.data = gson.fromJson(new String(dataBytes, StandardCharsets.UTF_8), FcReplier.class);
            case REQUEST -> msgUnit.data = gson.fromJson(new String(dataBytes, StandardCharsets.UTF_8), RequestBody.class);
            case SIGNED_BYTES,
                    SIGNED_TEXT,
                    SIGNED_HAT,
                    SIGNED_REPLY,
                    SIGNED_REQUEST
                    -> msgUnit.data = gson.fromJson(new String(dataBytes, StandardCharsets.UTF_8), Signature.class);
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
                    -> msgUnit.data = CryptoDataByte.fromJson(new String(dataBytes,StandardCharsets.UTF_8));
        }
        return msgUnit;
    }

    public String makeIdStr() {
        int nonce;
        if(this.nonce!=null)nonce =this.nonce;
        else nonce= Math.abs(BytesTools.bytesToIntBE(BytesTools.getRandomBytes(4)));

        long time;
        if(this.time!=null)time=this.time;
        else time = System.currentTimeMillis();

        String date = DateTools.longToTime(time,"yyyyMMddHHmmssSSS");
        this.idStr = date+"_"+nonce;
        return this.idStr;
    }
    public byte[] makeIdBytes() {
        this.idBytes = new BytesTools.ByteArrayAsKey(BytesTools.bytesMerger(BytesTools.longToBytes(this.time), BytesTools.intToByteArray(this.nonce)));
        return bytes;
    }

    public String getIdStr() {
        return idStr;
    }

    public void setIdStr(String idStr) {
        this.idStr = idStr;
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

    public MsgUnitState getStata() {
        return stata;
    }

    public void setStata(MsgUnitState stata) {
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

    public BytesTools.ByteArrayAsKey getIdBytes() {
        return idBytes;
    }

    public void setIdBytes(BytesTools.ByteArrayAsKey idBytes) {
        this.idBytes = idBytes;
    }
}
