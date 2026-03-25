package fchat;

import data.fcData.DeliveryMethod;
import data.fcData.FcEntity;
import data.fcData.Hat;
import utils.JsonUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Instant Message entity for all communication types (p2p, group, team, room).
 *
 * ID: assigned externally by the FUDP layer as a globally unique 8-byte long,
 * stored as a 16-char lowercase hex string.
 *
 * Serialization:
 *   - toJson() / fromJson(): full object for local storage
 *   - toWireBytes() / fromWireBytes(): compact binary for network transmission
 */
public class ImMessage extends FcEntity {

    private ImType type;
    private String senderId;
    private String targetId;

    private Long timestamp;
    private Long sequence;

    private ContentType contentType;
    private String content;
    private String dataBase64;

    private RequestType requestType;
    private String requestId;

    private List<String> roadIds;
    private String dockId;
    private DeliveryMethod deliveryMethod;
    private MessageStatus status;
    private Long deliveredAt;
    private Long readAt;

    private String cipher;
    private Long symkeyVersion;

    private String replyToId;
    private String threadId;

    private String senderName;

    private Boolean unread;
    private Boolean pinned;
    private Boolean deleted;

    // ========== Wire format constants ==========

    private static final int FLAG_CONTENT         = 0x0001;
    private static final int FLAG_DATA_BASE64     = 0x0002;
    private static final int FLAG_CIPHER          = 0x0004;
    private static final int FLAG_SYMKEY_VERSION  = 0x0008;
    private static final int FLAG_REQUEST_TYPE    = 0x0010;
    private static final int FLAG_REQUEST_ID      = 0x0020;
    private static final int FLAG_REPLY_TO_ID     = 0x0040;
    private static final int FLAG_THREAD_ID       = 0x0080;
    private static final int FLAG_MESSAGE_ID     = 0x0100;
    // bits 9-15 reserved

    // ========== ID helpers ==========

    public static String longIdToHex(long id) {
        return String.format("%016x", id);
    }

    public static long hexIdToLong(String hexId) {
        return Long.parseUnsignedLong(hexId, 16);
    }

    public void setIdFromLong(long fudpId) {
        this.id = longIdToHex(fudpId);
    }

    // ========== Factory methods ==========

    private static ImMessage createBase(ImType type, String senderId, String targetId, ContentType contentType) {
        ImMessage msg = new ImMessage();
        msg.setType(type);
        msg.setSenderId(senderId);
        msg.setTargetId(targetId);
        msg.setContentType(contentType);
        msg.setTimestamp(System.currentTimeMillis());
        msg.setStatus(MessageStatus.PENDING);
        return msg;
    }

    public static ImMessage createText(ImType type, String senderId, String targetId, String text) {
        ImMessage msg = createBase(type, senderId, targetId, ContentType.TEXT);
        msg.setContent(text);
        msg.setUnread(false);
        return msg;
    }

    public static ImMessage createHat(ImType type, String senderId, String targetId, Hat hat) {
        ImMessage msg = createBase(type, senderId, targetId, ContentType.HAT);
        msg.setContent(JsonUtils.toJson(hat));
        msg.setUnread(false);
        return msg;
    }

    public static ImMessage createRequest(ImType type, String senderId, String targetId,
                                          RequestType requestType, String requestData) {
        ImMessage msg = createBase(type, senderId, targetId, ContentType.REQUEST);
        msg.setRequestType(requestType);
        msg.setContent(requestData);
        return msg;
    }

    public static ImMessage createResponse(ImType type, String senderId, String targetId,
                                           String requestId, String responseData) {
        ImMessage msg = createBase(type, senderId, targetId, ContentType.RESPONSE);
        msg.setRequestId(requestId);
        msg.setContent(responseData);
        return msg;
    }

    public static ImMessage createReceipt(String senderId, String targetId,
                                          String originalMessageId, boolean isRead) {
        ImMessage msg = createBase(ImType.P2P, senderId, targetId, ContentType.RECEIPT);
        msg.setRequestId(originalMessageId);
        msg.setContent(isRead ? "read" : "delivered");
        return msg;
    }

    public static ImMessage createSymkey(ImType type, String senderId, String targetId,
                                         String symkeyData, long version) {
        ImMessage msg = createBase(type, senderId, targetId, ContentType.SYMKEY);
        msg.setContent(symkeyData);
        msg.setSymkeyVersion(version);
        return msg;
    }

    public static ImMessage createMembers(ImType type, String senderId, String targetId,
                                           String membersJson) {
        ImMessage msg = createBase(type, senderId, targetId, ContentType.MEMBERS);
        msg.setContent(membersJson);
        return msg;
    }

    public static ImMessage createHistory(ImType type, String senderId, String targetId,
                                           String hatJson, String kCipherBase64) {
        ImMessage msg = createBase(type, senderId, targetId, ContentType.HISTORY);
        msg.setContent(hatJson);
        msg.setDataBase64(kCipherBase64);
        return msg;
    }

    // ========== Utility ==========

    public boolean isOutgoing(String myFid) {
        return myFid != null && myFid.equals(senderId);
    }

    public String getConversationPartnerId(String myFid) {
        if (type != ImType.P2P) return targetId;
        return myFid.equals(senderId) ? targetId : senderId;
    }

    // ========== JSON serialization ==========

    public String toJson() {
        return JsonUtils.toJson(this);
    }

    public String toNiceJson() {
        return JsonUtils.toNiceJson(this);
    }

    public static ImMessage fromJson(String json) {
        return JsonUtils.fromJson(json, ImMessage.class);
    }

    // ========== Binary wire serialization ==========

    public byte[] toWireBytes() {
        int flags = 0;
        byte[] contentBytes = null;
        byte[] dataB64Bytes = null;
        byte[] cipherBytes = null;
        byte[] replyToIdBytes = null;
        byte[] threadIdBytes = null;
        byte[] requestIdBytes = null;
        byte[] messageIdBytes = null;

        if (content != null) { flags |= FLAG_CONTENT; contentBytes = content.getBytes(StandardCharsets.UTF_8); }
        if (dataBase64 != null) { flags |= FLAG_DATA_BASE64; dataB64Bytes = dataBase64.getBytes(StandardCharsets.UTF_8); }
        if (cipher != null) { flags |= FLAG_CIPHER; cipherBytes = cipher.getBytes(StandardCharsets.UTF_8); }
        if (symkeyVersion != null) flags |= FLAG_SYMKEY_VERSION;
        if (requestType != null) flags |= FLAG_REQUEST_TYPE;
        if (requestId != null) { flags |= FLAG_REQUEST_ID; requestIdBytes = requestId.getBytes(StandardCharsets.UTF_8); }
        if (replyToId != null) { flags |= FLAG_REPLY_TO_ID; replyToIdBytes = replyToId.getBytes(StandardCharsets.UTF_8); }
        if (threadId != null) { flags |= FLAG_THREAD_ID; threadIdBytes = threadId.getBytes(StandardCharsets.UTF_8); }
        if (id != null) { flags |= FLAG_MESSAGE_ID; messageIdBytes = id.getBytes(StandardCharsets.UTF_8); }

        byte[] senderIdBytes = senderId != null ? senderId.getBytes(StandardCharsets.UTF_8) : new byte[0];
        byte[] targetIdBytes = targetId != null ? targetId.getBytes(StandardCharsets.UTF_8) : new byte[0];

        int size = 1 + 1 + 1 + senderIdBytes.length + 1 + targetIdBytes.length + 8 + 2;
        if (contentBytes != null) size += 2 + contentBytes.length;
        if (dataB64Bytes != null) size += 2 + dataB64Bytes.length;
        if (cipherBytes != null) size += 2 + cipherBytes.length;
        if (symkeyVersion != null) size += 4;
        if (requestType != null) size += 1;
        if (requestIdBytes != null) size += 2 + requestIdBytes.length;
        if (replyToIdBytes != null) size += 2 + replyToIdBytes.length;
        if (threadIdBytes != null) size += 2 + threadIdBytes.length;
        if (messageIdBytes != null) size += 2 + messageIdBytes.length;

        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.put((byte) (type != null ? type.ordinal() : 0));
        buf.put((byte) (contentType != null ? contentType.ordinal() : 0));
        writeLenPfx8(buf, senderIdBytes);
        writeLenPfx8(buf, targetIdBytes);
        buf.putLong(timestamp != null ? timestamp : 0L);
        buf.putShort((short) flags);

        if (contentBytes != null) writeLenPfx16(buf, contentBytes);
        if (dataB64Bytes != null) writeLenPfx16(buf, dataB64Bytes);
        if (cipherBytes != null) writeLenPfx16(buf, cipherBytes);
        if (symkeyVersion != null) buf.putInt(symkeyVersion.intValue());
        if (requestType != null) buf.put((byte) requestType.ordinal());
        if (requestIdBytes != null) writeLenPfx16(buf, requestIdBytes);
        if (replyToIdBytes != null) writeLenPfx16(buf, replyToIdBytes);
        if (threadIdBytes != null) writeLenPfx16(buf, threadIdBytes);
        if (messageIdBytes != null) writeLenPfx16(buf, messageIdBytes);

        return buf.array();
    }

    public static ImMessage fromWireBytes(byte[] data) {
        if (data == null || data.length < 14) throw new IllegalArgumentException("Wire data too short");
        ByteBuffer buf = ByteBuffer.wrap(data);
        ImMessage msg = new ImMessage();

        int typeOrd = buf.get() & 0xFF;
        if (typeOrd < ImType.values().length) msg.setType(ImType.values()[typeOrd]);
        int ctOrd = buf.get() & 0xFF;
        if (ctOrd < ContentType.values().length) msg.setContentType(ContentType.values()[ctOrd]);

        msg.setSenderId(readLenPfx8(buf));
        msg.setTargetId(readLenPfx8(buf));
        msg.setTimestamp(buf.getLong());

        int flags = buf.getShort() & 0xFFFF;
        if ((flags & FLAG_CONTENT) != 0) msg.setContent(readLenPfx16(buf));
        if ((flags & FLAG_DATA_BASE64) != 0) msg.setDataBase64(readLenPfx16(buf));
        if ((flags & FLAG_CIPHER) != 0) msg.setCipher(readLenPfx16(buf));
        if ((flags & FLAG_SYMKEY_VERSION) != 0) msg.setSymkeyVersion((long) buf.getInt());
        if ((flags & FLAG_REQUEST_TYPE) != 0) {
            int rtOrd = buf.get() & 0xFF;
            if (rtOrd < RequestType.values().length) msg.setRequestType(RequestType.values()[rtOrd]);
        }
        if ((flags & FLAG_REQUEST_ID) != 0) msg.setRequestId(readLenPfx16(buf));
        if ((flags & FLAG_REPLY_TO_ID) != 0) msg.setReplyToId(readLenPfx16(buf));
        if ((flags & FLAG_THREAD_ID) != 0) msg.setThreadId(readLenPfx16(buf));
        if ((flags & FLAG_MESSAGE_ID) != 0) msg.setId(readLenPfx16(buf));

        return msg;
    }

    private static void writeLenPfx8(ByteBuffer buf, byte[] data) {
        buf.put((byte) (data != null ? data.length : 0));
        if (data != null && data.length > 0) buf.put(data);
    }

    private static String readLenPfx8(ByteBuffer buf) {
        int len = buf.get() & 0xFF;
        if (len == 0) return "";
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeLenPfx16(ByteBuffer buf, byte[] data) {
        buf.putShort((short) (data != null ? data.length : 0));
        if (data != null && data.length > 0) buf.put(data);
    }

    private static String readLenPfx16(ByteBuffer buf) {
        int len = buf.getShort() & 0xFFFF;
        if (len == 0) return "";
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // ========== Getters and setters ==========

    public ImType getType() { return type; }
    public void setType(ImType type) { this.type = type; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    public Long getSequence() { return sequence; }
    public void setSequence(Long sequence) { this.sequence = sequence; }
    public ContentType getContentType() { return contentType; }
    public void setContentType(ContentType contentType) { this.contentType = contentType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getDataBase64() { return dataBase64; }
    public void setDataBase64(String dataBase64) { this.dataBase64 = dataBase64; }
    public RequestType getRequestType() { return requestType; }
    public void setRequestType(RequestType requestType) { this.requestType = requestType; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public List<String> getRoadIds() { return roadIds; }
    public void setRoadIds(List<String> roadIds) { this.roadIds = roadIds; }
    public String getDockId() { return dockId; }
    public void setDockId(String dockId) { this.dockId = dockId; }
    public DeliveryMethod getDeliveryMethod() { return deliveryMethod; }
    public void setDeliveryMethod(DeliveryMethod deliveryMethod) { this.deliveryMethod = deliveryMethod; }
    public MessageStatus getStatus() { return status; }
    public void setStatus(MessageStatus status) { this.status = status; }
    public Long getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Long deliveredAt) { this.deliveredAt = deliveredAt; }
    public Long getReadAt() { return readAt; }
    public void setReadAt(Long readAt) { this.readAt = readAt; }
    public String getCipher() { return cipher; }
    public void setCipher(String cipher) { this.cipher = cipher; }
    public Long getSymkeyVersion() { return symkeyVersion; }
    public void setSymkeyVersion(Long symkeyVersion) { this.symkeyVersion = symkeyVersion; }
    public String getReplyToId() { return replyToId; }
    public void setReplyToId(String replyToId) { this.replyToId = replyToId; }
    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public Boolean getUnread() { return unread; }
    public void setUnread(Boolean unread) { this.unread = unread; }
    public Boolean getPinned() { return pinned; }
    public void setPinned(Boolean pinned) { this.pinned = pinned; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
