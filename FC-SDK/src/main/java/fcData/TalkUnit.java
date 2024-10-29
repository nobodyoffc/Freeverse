package fcData;

import apip.apipData.RequestBody;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import crypto.*;
import javaTools.BytesTools;
import javaTools.DateTools;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.TcpTools;
import org.bitcoinj.core.ECKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TalkUnit implements Comparable<TalkUnit> {

    private transient String id; //for database, time formatted + nonce
    private transient BytesTools.ByteArrayAsKey idBytes;
    private TransferUnitState stata; //For database

    //Basic fields
    private String from;//FID
    private IdType toType;
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

    public TalkUnit(@NotNull IdType toType, @Nullable String to, @Nullable List<String> toList, @NotNull DataType dataType) {
        this.nonce = Math.abs(BytesTools.bytesToIntBE(BytesTools.getRandomBytes(4)));
        this.time = System.currentTimeMillis();
        this.toType = toType;
        this.to = to;
        this.toList = toList;
        this.dataType = dataType;
    }


    public static TalkUnit newSample(){
        TalkUnit talkUnit = new TalkUnit();
        talkUnit.setIdType(IdType.FID);
        talkUnit.setFrom(KeyTools.pubKeyToFchAddr(new ECKey().getPubKey()));
        talkUnit.setTo(KeyTools.pubKeyToFchAddr(new ECKey().getPubKey()));
        talkUnit.setDataType(DataType.TEXT);
        talkUnit.setData("The data at "+talkUnit.getTime());
        return talkUnit;
    }

    @Nullable
    public static TalkUnit readTalkUnitByTcp(DataInputStream dataInputStream) throws IOException {
        byte[] receivedBytes;
        receivedBytes = TcpTools.readBytes(dataInputStream);
        if(receivedBytes==null) return null;

        return fromBytes(receivedBytes);
    }
    public static boolean writeTalkUnitByTcp(DataOutputStream outputStream, TalkUnit talkUnitRequest) {
        return writeTalkUnitByTcp(outputStream,talkUnitRequest,null,null,null);
    }

    public static boolean writeTalkUnitByTcp(DataOutputStream outputStream, TalkUnit talkUnitRequest, EncryptType encryptType, byte[] key, byte[] pubKey) {
        byte[] data = talkUnitRequest.toBytes();

        return writeBytesByTcp(outputStream, encryptType, key, pubKey, data);
    }

    public static boolean writeBytesByTcp(DataOutputStream outputStream, EncryptType encryptType, byte[] key, byte[] pubKey, byte[] data) {
        Encryptor encryptor;
        if(encryptType ==null)
            return TcpTools.writeBytes(outputStream, data);

        CryptoDataByte cryptoDataByte=null;
        switch (encryptType){
            case SymKey-> {
                encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
                cryptoDataByte = encryptor.encryptBySymKey(data, key);
            }
            case Password -> {
                encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
                cryptoDataByte = encryptor.encryptByPassword(data, BytesTools.bytesToChars(key));
            }
            case AsyTwoWay -> {
                encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
                cryptoDataByte = encryptor.encryptByAsyTwoWay(data, key, pubKey);
            }
            case AsyOneWay -> {
                encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
                cryptoDataByte = encryptor.encryptByAsyOneWay(data, key);
            }
        }

        if(cryptoDataByte==null || cryptoDataByte.getCode()!=0)
            return false;

        return TcpTools.writeBytes(outputStream, cryptoDataByte.toBundle());
    }


    public byte[] encrypt(byte[] symKey) {
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte = encryptor.encryptBySymKey(toBytes(),symKey);
        return cryptoDataByte.toBundle();
    }

    public static TalkUnit decrypt(byte[] bundle, byte[] symKey) {
        CryptoDataByte cryptoDataByte1 = CryptoDataByte.fromBundle(bundle);
        Decryptor decryptor = new Decryptor();
        cryptoDataByte1.setSymKey(symKey);
        decryptor.decrypt(cryptoDataByte1);
        return TalkUnit.fromBytes(cryptoDataByte1.getData());
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

    public void renew() {
        this.nonce = Math.abs(BytesTools.bytesToIntBE(BytesTools.getRandomBytes(4)));
        this.time = System.currentTimeMillis();
    }
//
//    @Override
//    public void serialize(@NotNull DataOutput2 out, @NotNull TalkUnit talkUnit) throws IOException {
//        out.writeBoolean(talkUnit.from != null);
//        if (talkUnit.from != null) out.write(KeyTools.addrToHash160(from));
//        out.write(talkUnit.toBundle());
//    }
//
//    @Override
//    public TalkUnit deserialize(@NotNull DataInput2 in, int i) throws IOException {
//        TalkUnit talkUnit = new TalkUnit();
//
//        // Read `from`
//        if (in.readBoolean()) {
//            talkUnit.from = in.readUTF();
//        }
//        byte[] hash160 = new byte[20];
//        in.readFully(hash160);
//        byte[] bundle = new byte[i-20];
//        in.readFully(bundle);
//        return null;
//    }
//@Override
//public void serialize(DataOutput2 out, TalkUnit value) throws IOException {
//        // Handle 'from' array
//    if (value.from == null) {
//        out.writeBoolean(false); // Use a sentinel value to indicate null
//    } else {
//        out.writeBoolean(true);
//        out.write(KeyTools.addrToHash160(value.getFrom()));
//    }
//
//    // Handle 'bundle' array
//    byte[] bundle = value.toBundle();
//    out.writeInt(bundle.length);
//    out.write(bundle);
//}
//
//    @Override
//    public TalkUnit deserialize(DataInput2 in, int available) throws IOException {
//        // Handle 'from' array
//        boolean isFromNull = in.readBoolean();
//        byte[] from = (!isFromNull) ? null : new byte[20];
//        String fromFid = null;
//        if (from != null) {
//            in.readFully(from);
//            fromFid = KeyTools.hash160ToFchAddr(from);
//        }
//
//        // Handle 'bundle' array
//        int bundleLength = in.readInt();
//        byte[] bundle = (bundleLength == -1) ? null : new byte[bundleLength];
//        if (bundle != null) {
//            in.readFully(bundle);
//        }
//
//        // Return a new TalkUnit object with the deserialized data
//        TalkUnit talkUnit = TalkUnit.fromBundle(bundle);
//        if(talkUnit==null)return null;
//        talkUnit.setFrom(fromFid);
//        return talkUnit;
//    }

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
    public enum IdType {

        FID((byte)0),
        GROUP((byte)1),
        TEAM((byte)2),

        FID_LIST((byte)3),
        GROUP_LIST((byte)4),
        TEAM_LIST((byte)5),
        YOU((byte)6);


        public final byte number;
        IdType(byte number) {this.number=number;}
        public static IdType getIdType(byte number) {
            for (IdType toType : IdType.values()) {
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
//            byteArrayBuilder.write(KeyTools.addrToHash160(this.from));

            byteArrayBuilder.write(this.toType.number);
            switch (this.toType){
//                case SELF,SERVER,ANYONE,EVERYONE, CLIENT -> {}
                case FID -> byteArrayBuilder.write(KeyTools.addrToHash160(to));
                case GROUP,TEAM -> Hex.fromHex(this.to);
                case FID_LIST -> {
                    byteArrayBuilder.write(BytesTools.intToByteArray(this.toList.size()));
                    for(String fid:toList){
                        byteArrayBuilder.write(KeyTools.addrToHash160(fid));
                    }
                }
                case GROUP_LIST,TEAM_LIST-> {
                    byteArrayBuilder.write(BytesTools.intToByteArray(this.toList.size()));
                    for(String id:toList){
                        byteArrayBuilder.write(Hex.fromHex(id));
                    }
                }
                case YOU -> {}
            }

            byteArrayBuilder.write(BytesTools.longToBytes(this.time));
            byteArrayBuilder.write(BytesTools.intToByteArray(this.nonce));

            byteArrayBuilder.write(this.dataType.number);
            switch (this.dataType){
                case BYTES -> byteArrayBuilder.write((byte[]) data);
                case TEXT -> byteArrayBuilder.write(((String)data).getBytes());
                case SIGNED_TEXT,SIGNED_BYTES,SIGNED_HAT,SIGNED_REPLY,SIGNED_REQUEST
                        -> byteArrayBuilder.write(((Signature)data).toBundle());
                case ENCRYPTED_REQUEST,ENCRYPTED_BYTES,ENCRYPTED_HAT,ENCRYPTED_REPLY,
                        ENCRYPTED_SIGNED_BYTES,ENCRYPTED_SIGNED_TEXT,ENCRYPTED_SIGNED_HAT,
                        ENCRYPTED_SIGNED_REPLY,ENCRYPTED_SIGNED_REQUEST,ENCRYPTED_TEXT
                        -> {
                    byte[] cipher = ((CryptoDataByte) data).toBundle();
                    byteArrayBuilder.write(cipher);
                }
                default -> byteArrayBuilder.write(JsonTools.toJson(data).getBytes());
            }
            return byteArrayBuilder.toByteArray();
        }
    }
    public static TalkUnit fromBytes(byte[] bytes) {
        TalkUnit talkUnit = new TalkUnit();
        try {
            // Use setter methods
            talkUnit.setTime(null);
            talkUnit.setNonce(null);

            ByteBuffer buffer = ByteBuffer.wrap(bytes);

            // Extract ToType
            byte toTypeByte = buffer.get();
            talkUnit.setIdType(IdType.getIdType(toTypeByte));

            // Extract 'to' based on ToType
            int toLength = 0;
            byte[] toBytes;
            switch (talkUnit.getIdType()) {
                case FID -> {
                    toLength = 20;
                    toBytes = new byte[toLength];
                    buffer.get(toBytes);
                    talkUnit.setTo(KeyTools.hash160ToFchAddr(toBytes));
                }
                case GROUP, TEAM -> {
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
                case YOU -> {}

                case GROUP_LIST, TEAM_LIST -> {
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
        }catch (Exception e){
            System.out.println("Failed to parse. Error:"+e.getMessage());
            return null;
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
        this.id = date + "_" + nonce;//Hex.toHex(BytesTools.intToByteArray(nonce));
        return id;
    }

    public byte[] makeIdBytes() {
        this.idBytes = new BytesTools.ByteArrayAsKey(BytesTools.bytesMerger(BytesTools.longToBytes(this.time), BytesTools.intToByteArray(this.nonce)));

        return this.idBytes.getBytes();
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

    public Integer getNonce() {
        return nonce;
    }

    public void setNonce(Integer nonce) {
        this.nonce = nonce;
    }

//    public String getFrom() {
//        return from;
//    }
//
//    public void setFrom(String from) {
//        this.from = from;
//    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public IdType getIdType() {
        return toType;
    }

    public void setIdType(IdType toType) {
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
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TalkUnit talkUnit = (TalkUnit) o;
        return talkUnit.makeId().equals(this.makeId());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(makeIdBytes());
    }
}
