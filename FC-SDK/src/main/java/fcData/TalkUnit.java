package fcData;

import apip.apipData.RequestBody;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import crypto.CryptoDataByte;
import crypto.Decryptor;
import crypto.Encryptor;
import crypto.KeyTools;
import javaTools.BytesTools;
import javaTools.DateTools;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.http.TcpTools;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TalkUnit implements Comparable<TalkUnit>{

    private transient String id; //for database, time formatted + nonce
    private transient BytesTools.ByteArrayAsKey idBytes;
    private transient byte[] bytes; //for transfer

    private TransferUnitState stata; //For database

    //Basic fields
    private String from;//FID
    private ToType toType;
    private String to;
    private List<String> toList;
    private Long time;
    private Integer nonce;
    private DataType dataType;

    //Body
    private Object data;

    public TalkUnit() {
        this.nonce = Math.abs(BytesTools.bytesToIntBE(BytesTools.getRandomBytes(4)));
        this.time = System.currentTimeMillis();
    }

    public TalkUnit(@NotNull String from, @NotNull ToType toType, @Nullable String to, @Nullable List<String> toList, @NotNull DataType dataType) {
        this.nonce = Math.abs(BytesTools.bytesToIntBE(BytesTools.getRandomBytes(4)));
        this.time = System.currentTimeMillis();
        this.from = from;
        this.toType = toType;
        this.to = to;
        this.toList = toList;
        this.dataType = dataType;
    }

    @Nullable
    public static TalkUnit readTalkUnitByTcp(DataInputStream dataInputStream) throws IOException {
        byte[] receivedBytes;
        receivedBytes = TcpTools.readBytes(dataInputStream);
        if(receivedBytes==null) return null;

        return fromBundle(receivedBytes);
    }

    public byte[] encrypt(byte[] symKey) {
        if(bytes==null)this.toBundle();
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte = encryptor.encryptBySymKey(bytes,symKey);
        return cryptoDataByte.toBundle();
    }

    public static TalkUnit decrypt(byte[] bundle, byte[] symKey) {
        CryptoDataByte cryptoDataByte1 = CryptoDataByte.fromBundle(bundle);
        Decryptor decryptor = new Decryptor();
        cryptoDataByte1.setSymKey(symKey);
        decryptor.decrypt(cryptoDataByte1);
        return TalkUnit.fromBundle(cryptoDataByte1.getData());
    }

    public static TalkUnit fromJson(String talkItemJson){
        return new Gson().fromJson(talkItemJson, TalkUnit.class);
    }

    public String toJson() {
        return JsonTools.toJson(this);
    }
    public String toNiceJson() {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        return gson.toJson(this);
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

        FID((byte)0),
        GROUP((byte)1),
        TEAM((byte)2),
        ROOM((byte)3),

        FID_LIST((byte)4),
        GROUP_LIST((byte)5),
        TEAM_LIST((byte)6),
        ROOM_LIST((byte)7),

        SELF((byte)8),
        YOU((byte)9),
        SERVER((byte)10),
        ANYONE((byte)11),
        EVERYONE((byte)12);


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

    public byte[] toBundle(){

        try(ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder()){
            byteArrayBuilder.write(KeyTools.addrToHash160(this.from));

            byteArrayBuilder.write(this.toType.number);
            switch (this.toType){
                case SELF,SERVER,ANYONE,EVERYONE,YOU -> {}
                case FID -> byteArrayBuilder.write(KeyTools.addrToHash160(to));
                case GROUP,TEAM,ROOM -> Hex.fromHex(this.to);
                case FID_LIST -> {
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

            byteArrayBuilder.write(BytesTools.longToBytes(this.time));
            byteArrayBuilder.write(BytesTools.intToByteArray(this.nonce));

            byteArrayBuilder.write(this.dataType.number);
            switch (this.dataType){
                case BYTES -> byteArrayBuilder.write((byte[]) data);
                case TEXT -> byteArrayBuilder.write(((String)data).getBytes());
                case SIGNED_TEXT,SIGNED_BYTES,SIGNED_HAT,SIGNED_REPLY,SIGNED_REQUEST
                        -> byteArrayBuilder.write(((Signature)data).toBundle());
                case ENCRYPTED_REQUEST,ENCRYPTED_BYTES,ENCRYPTED_HAT,ENCRYPTED_REPLY,ENCRYPTED_SIGNED_BYTES,ENCRYPTED_SIGNED_TEXT,ENCRYPTED_SIGNED_HAT,ENCRYPTED_SIGNED_REPLY,ENCRYPTED_SIGNED_REQUEST,ENCRYPTED_TEXT
                        -> {
                    byte[] cipher = ((CryptoDataByte) data).toBundle();
                    byteArrayBuilder.write(cipher);
                }
                default -> byteArrayBuilder.write(JsonTools.toJson(data).getBytes());
            }
            this.bytes = byteArrayBuilder.toByteArray();
        }
        return this.bytes;
    }
    public static TalkUnit fromBundle(byte[] bytes) {
        TalkUnit talkUnit = new TalkUnit();

        // Use setter methods
        talkUnit.setTime(null);
        talkUnit.setNonce(null);

        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        // Extract 'from'
        byte[] fromBytes = new byte[20];
        buffer.get(fromBytes);
        talkUnit.setFrom(KeyTools.hash160ToFchAddr(fromBytes));

        // Extract ToType
        byte toTypeByte = buffer.get();
        talkUnit.setToType(ToType.getToType(toTypeByte));

        // Extract 'to' based on ToType
        int toLength = 0;
        byte[] toBytes;
        switch (talkUnit.getToType()) {
            case FID -> {
                toLength = 20;
                toBytes = new byte[toLength];
                buffer.get(toBytes);
                talkUnit.setTo(KeyTools.hash160ToFchAddr(toBytes));
            }
            case GROUP, TEAM, ROOM -> {
                toLength = 32; // Hex
                toBytes = new byte[toLength];
                buffer.get(toBytes);
                talkUnit.setTo(Hex.toHex(toBytes));
            }
            case FID_LIST -> {
                int sizeLength = 4;
                int itemLength = 20;
                byte[] sizeBytes = new byte[sizeLength];
                buffer.get(sizeBytes);
                int size = BytesTools.bytesToIntBE(sizeBytes);
                toBytes = new byte[itemLength];
                List<String> toList = new ArrayList<>();
                for (; size > 0; size--) {
                    buffer.get(toBytes);
                    toList.add(KeyTools.hash160ToFchAddr(toBytes));
                }
                talkUnit.setToList(toList);
            }

            case GROUP_LIST, TEAM_LIST, ROOM_LIST -> {
                int sizeLength = 4;
                int itemLength = 32;
                byte[] sizeBytes = new byte[sizeLength];
                buffer.get(sizeBytes);
                int size = BytesTools.bytesToIntBE(sizeBytes);
                toBytes = new byte[itemLength];
                List<String> toList = new ArrayList<>();
                for (; size > 0; size--) {
                    buffer.get(toBytes);
                    toList.add(Hex.toHex(toBytes));
                }
                talkUnit.setToList(toList);
            }
        }

        talkUnit.setTime(buffer.getLong());

        talkUnit.setNonce(buffer.getInt());

        // Extract DataType
        byte dataTypeByte = buffer.get();
        talkUnit.setDataType(DataType.getDataType(dataTypeByte));

        // Extract data
        int remaining = buffer.remaining();
        byte[] dataBytes = new byte[remaining];
        buffer.get(dataBytes);
        Gson gson = new Gson();

        switch (talkUnit.getDataType()) {
            case BYTES -> talkUnit.setData(dataBytes);
            case TEXT -> talkUnit.setData(new String(dataBytes, StandardCharsets.UTF_8));
            case HAT -> talkUnit.setData(gson.fromJson(new String(dataBytes, StandardCharsets.UTF_8), Hat.class));
            case REPLY -> talkUnit.setData(gson.fromJson(new String(dataBytes, StandardCharsets.UTF_8), FcReplier.class));
            case REQUEST -> talkUnit.setData(gson.fromJson(new String(dataBytes, StandardCharsets.UTF_8), RequestBody.class));
            case SIGNED_BYTES, SIGNED_TEXT, SIGNED_HAT, SIGNED_REPLY, SIGNED_REQUEST -> {
                Signature signature = Signature.fromBundle(dataBytes);
                talkUnit.setData(signature);
            }
            case ENCRYPTED_BYTES, ENCRYPTED_TEXT, ENCRYPTED_HAT, ENCRYPTED_REPLY, ENCRYPTED_REQUEST,
                    ENCRYPTED_SIGNED_BYTES, ENCRYPTED_SIGNED_TEXT, ENCRYPTED_SIGNED_HAT, ENCRYPTED_SIGNED_REPLY,
                    ENCRYPTED_SIGNED_REQUEST -> {
                CryptoDataByte cryptoDataByte = CryptoDataByte.fromBundle(dataBytes);
                talkUnit.setData(cryptoDataByte);
            }
        }

        return talkUnit;
    }
//    public static TalkUnit fromBytes(byte[] bytes) {
//        TalkUnit talkUnit = new TalkUnit();
//        talkUnit.time = null;
//        talkUnit.nonce = null;
//
//        ByteBuffer buffer = ByteBuffer.wrap(bytes);
//
//        // Extract from
//        byte[] fromBytes = new byte[20];
//        buffer.get(fromBytes);
//        talkUnit.from = KeyTools.hash160ToFchAddr(fromBytes);
//
//        // Extract ToType
//        byte toTypeByte = buffer.get();
//        talkUnit.toType = ToType.getToType(toTypeByte);
//
//        // Extract 'to' based on ToType
//        int toLength = 0;
//        byte[] toBytes;
//        switch (talkUnit.toType) {
//            case FID -> {
//                toLength = 20;
//                toBytes = new byte[toLength];
//                buffer.get(toBytes);
//                talkUnit.to = KeyTools.hash160ToFchAddr(toBytes);
//            }
//            case GROUP, TEAM, ROOM -> {
//                toLength = 32; // Hex
//                toBytes = new byte[toLength];
//                buffer.get(toBytes);
//                talkUnit.to = Hex.toHex(toBytes);
//            }
//            case FID_LIST -> {
//                int sizeLength = 4;
//                int itemLength = 20;
//                byte[] sizeBytes = new byte[sizeLength];
//                buffer.get(sizeBytes);
//                int size = BytesTools.bytesToIntBE(sizeBytes);
//                toBytes = new byte[itemLength];
//                talkUnit.toList = new ArrayList<>();
//                for(;size>0;size--) {
//                    buffer.get(toBytes);
//                    talkUnit.toList.add(KeyTools.hash160ToFchAddr(toBytes));
//                }
//            }
//
//            case GROUP_LIST,TEAM_LIST,ROOM_LIST -> {
//                int sizeLength = 4;
//                int itemLength = 32;
//                byte[] sizeBytes = new byte[sizeLength];
//                buffer.get(sizeBytes);
//                int size = BytesTools.bytesToIntBE(sizeBytes);
//                toBytes = new byte[itemLength];
//                talkUnit.toList = new ArrayList<>();
//                for(;size>0;size--) {
//                    buffer.get(toBytes);
//                    talkUnit.toList.add(Hex.toHex(toBytes));
//                }
//            }
//        }
//
//
//        talkUnit.time = buffer.getLong();
//
//
//        talkUnit.nonce = buffer.getInt();
//
//        // Extract DataType
//        byte dataTypeByte = buffer.get();
//        talkUnit.dataType = DataType.getDataType(dataTypeByte);
//
//        // Extract data
//        int remaining = buffer.remaining();
//        byte[] dataBytes = new byte[remaining];
//        buffer.get(dataBytes);
//        Gson gson = new Gson();
//        switch (talkUnit.dataType){
//            case BYTES -> talkUnit.data = dataBytes;
//            case TEXT -> talkUnit.data = new String(dataBytes, StandardCharsets.UTF_8);
//            case HAT -> talkUnit.data = gson.fromJson(new String(dataBytes, StandardCharsets.UTF_8), Hat.class);
//            case REPLY -> talkUnit.data = gson.fromJson(new String(dataBytes, StandardCharsets.UTF_8), FcReplier.class);
//            case REQUEST -> talkUnit.data = gson.fromJson(new String(dataBytes, StandardCharsets.UTF_8), RequestBody.class);
//            case SIGNED_BYTES,
//                    SIGNED_TEXT,
//                    SIGNED_HAT,
//                    SIGNED_REPLY,
//                    SIGNED_REQUEST
//                    -> talkUnit.data = Signature.fromBundle(dataBytes);
//            case ENCRYPTED_BYTES,
//                    ENCRYPTED_TEXT,
//                    ENCRYPTED_HAT,
//                    ENCRYPTED_REPLY,
//                    ENCRYPTED_REQUEST,
//
//                    ENCRYPTED_SIGNED_BYTES,
//                    ENCRYPTED_SIGNED_TEXT,
//                    ENCRYPTED_SIGNED_HAT,
//                    ENCRYPTED_SIGNED_REPLY,
//                    ENCRYPTED_SIGNED_REQUEST
//                    -> talkUnit.data = CryptoDataByte.fromBundle(dataBytes);
//        }
//        return talkUnit;
//    }

    public String makeId() {
        if(this.nonce==null)
            nonce= Math.abs(BytesTools.bytesToIntBE(BytesTools.getRandomBytes(4)));

        if(this.time==null)
            time = System.currentTimeMillis();

        String date = DateTools.longToTime(time,"yyyyMMdd_HHmmssSSS");
        this.id = date + "_" + Hex.toHex(BytesTools.intToByteArray(nonce));
        return id;
    }

    public byte[] makeIdBytes() {
        this.idBytes = new BytesTools.ByteArrayAsKey(BytesTools.bytesMerger(BytesTools.longToBytes(this.time), BytesTools.intToByteArray(this.nonce)));
        return bytes;
    }
    @Override
    public int compareTo(@NotNull TalkUnit other) {
        return Arrays.compare(this.idBytes.getBytes(),other.getIdBytes().getBytes());
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

    public TransferUnitState getStata() {
        return stata;
    }

    public void setStata(TransferUnitState stata) {
        this.stata = stata;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public List<String> getToList() {
        return toList;
    }

    public void setToList(List<String> toList) {
        this.toList = toList;
    }

    public BytesTools.ByteArrayAsKey getIdBytes() {
        return idBytes;
    }

    public void setIdBytes(BytesTools.ByteArrayAsKey idBytes) {
        this.idBytes = idBytes;
    }
}
