package clients.fcspClient;

import com.google.gson.Gson;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class TalkItem {
    private transient String id; //time+nonce
    private ToType toType;
    private String to;
    /*
    0. fidA-fidB.fidA<fidB. 69bytes
    1. Gid
    2. Tid
    3. Lid,创建的sha256x2(time+nonce)。hex32 私群，创建人全权，生成symKey，分发symKey。
    4. self
    Hex 32, sha256x2(fidA+fidB), gid(no encrypt), tid(encrypt,share sessionKey), random number for private room(encrypt,share sessionKey).
    传输加解密，本地保存明文。分享采用临时对称密钥，再公钥加密分发。
    用hat，规定文件格式，用于存储和分享。
    */
    private Long time;
    private Integer nonce;
    private String from;//FID
    private DataType dataType; //0 text, 1 cipher, 2 HAT, 3 sign
    private String data;
    private transient byte[] bytes; //toType+to+time+nonce+from+dataType+data
    public static final String MAPPINGS = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"wildcard\"},\"toType\":{\"type\":\"keyword\"},\"to\":{\"type\":\"wildcard\"},\"time\":{\"type\":\"long\"},\"nonce\":{\"type\":\"integer\"},\"from\":{\"type\":\"wildcard\"},\"dataType\":{\"type\":\"keyword\"},\"data\":{\"type\":\"text\"}}}}";
    public enum ToType {
        SELF((byte)0),
        FID((byte)1),
        GROUP((byte)2),
        TEAM((byte)3),
        LIST((byte)4);

        public final byte number;
        ToType(byte number) {this.number=number;}
    }
    public enum DataType {
        SERVER_MSG((byte)0),
        TEXT((byte)1),
        CIPHER((byte)2),
        SIGNATURE((byte)3),
        HAT((byte)4),
        SIGN_IN((byte)5), //data = {"sid":,"nonce":,"time":}
        CREAT_ROOM((byte)6),
        ASK_ROOM_INFO((byte)7),
        SHARE_ROOM_INFO((byte)8),
        ADD_MEMBER((byte)9),
        REMOVE_MEMBER((byte)10),
        CLOSE_ROOM((byte)11),
        ASK_KEY((byte)12),
        SHARE_KEY((byte)13),
        UPDATE_ITEMS((byte)14),
        EXIT((byte)19);

        public final byte number;
        DataType(byte number) {this.number=number;}
    }

    public static class ServerMsg{
        private Integer forNonce;
        private String sign;
        private String sessionName;
        private Object data;

        public Integer getForNonce() {
            return forNonce;
        }

        public void setForNonce(Integer forNonce) {
            this.forNonce = forNonce;
        }

        public String getSign() {
            return sign;
        }

        public void setSign(String sign) {
            this.sign = sign;
        }

        public String getSessionName() {
            return sessionName;
        }

        public void setSessionName(String sessionName) {
            this.sessionName = sessionName;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }
    }

    public void toBytes(){
        if (this.data == null || this.to == null || this.dataType == null || this.time == null || this.from == null || this.nonce == null) {
            System.out.println("Failed to make the bytes of TalkItem.");
            return;
        }

        byte[] toTypeBytes = new byte[]{this.toType.number};
        byte[] toBytes = switch (toType) {
            case SELF, FID -> this.to.getBytes(StandardCharsets.UTF_8);
            case GROUP, TEAM, LIST -> Hex.fromHex(this.to);
        };
        byte[] timeBytes = BytesTools.longToBytes(this.time);
        byte[] nonceBytes = BytesTools.intToByteArray(this.nonce);
        byte[] fromBytes = this.from.getBytes(StandardCharsets.UTF_8);
        byte[] dataTypeBytes = new byte[]{this.dataType.number};
        byte[] dataBytes = this.data.getBytes(StandardCharsets.UTF_8);

        int totalLength = 1 + toBytes.length + 8 + 4 + fromBytes.length + 1 + dataBytes.length;
        byte[] resultBytes = new byte[totalLength];

        int pos = 0;
        System.arraycopy(toTypeBytes, 0, resultBytes, pos, toTypeBytes.length);
        pos += toTypeBytes.length;
        System.arraycopy(toBytes, 0, resultBytes, pos, toBytes.length);
        pos += toBytes.length;
        System.arraycopy(timeBytes, 0, resultBytes, pos, timeBytes.length);
        pos += timeBytes.length;
        System.arraycopy(nonceBytes, 0, resultBytes, pos, nonceBytes.length);
        pos += nonceBytes.length;
        System.arraycopy(fromBytes, 0, resultBytes, pos, fromBytes.length);
        pos += fromBytes.length;
        System.arraycopy(dataTypeBytes, 0, resultBytes, pos, dataTypeBytes.length);
        pos += dataTypeBytes.length;
        System.arraycopy(dataBytes, 0, resultBytes, pos, dataBytes.length);

        this.bytes = resultBytes;
    }
    public void fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        // Extract ToType
        byte toTypeByte = buffer.get();
        this.toType = ToType.values()[toTypeByte];

        // Extract 'to' based on ToType
        int toLength = 0;
        switch (this.toType) {
            case SELF -> toLength = 34;//FID
            case FID -> toLength = 69; //fidA-fidB
            case GROUP, TEAM, LIST -> toLength = 32; // Hex
        }
        byte[] toBytes = new byte[toLength];
        buffer.get(toBytes);
        this.to = new String(toBytes, StandardCharsets.UTF_8);

        // Extract time
        this.time = buffer.getLong();

        // Extract nonce
        this.nonce = buffer.getInt();

        // Extract from
        byte[] fromBytes = new byte[34];
        buffer.get(fromBytes);
        this.from = new String(fromBytes, StandardCharsets.UTF_8);

        // Extract DataType
        byte dataTypeByte = buffer.get();
        this.dataType = DataType.values()[dataTypeByte];

        // Extract data
        int remaining = buffer.remaining();
        byte[] dataBytes = new byte[remaining];
        buffer.get(dataBytes);
        this.data = new String(dataBytes, StandardCharsets.UTF_8);
    }

    public TalkItem() {
        this.nonce = BytesTools.bytesToIntBE(BytesTools.getRandomBytes(4));
        this.time = System.currentTimeMillis();
    }

    public TalkItem(Long time, Integer nonce, String from, ToType toType, String to, DataType dataType, String data) {
        this.time = time;
        this.nonce = nonce;
        this.from = from;
        this.toType = toType;
        this.to = to;
        this.dataType = dataType;
        this.data = data;
    }

    public static TalkItem fromJson(String talkItemJson){
        return new Gson().fromJson(talkItemJson,TalkItem.class);
    }

    public String toJson() {
        return JsonTools.toJson(this);
    }

    public String toString(){
        return toJson();
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

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
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
}
