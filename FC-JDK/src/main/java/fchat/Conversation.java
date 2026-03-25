package fchat;

import data.fcData.FcEntity;
import utils.JsonUtils;

/**
 * Represents a conversation (chat thread) for display in conversation list.
 */
public class Conversation extends FcEntity {

    private String targetId;
    private ImType type;
    private String displayName;

    private String lastMessageId;
    private String lastMessageContent;
    private String lastMessageSenderId;
    private ContentType lastMessageType;
    private Long lastMessageTime;

    private Integer unreadCount;
    private Boolean pinned;
    private Boolean muted;
    private Long lastActiveAt;
    private Long createdAt;

    public static Conversation fromMessage(ImMessage message, String myFid) {
        Conversation conv = new Conversation();
        conv.setType(message.getType());
        if (message.getType() == ImType.P2P) {
            conv.setTargetId(message.getConversationPartnerId(myFid));
        } else {
            conv.setTargetId(message.getTargetId());
        }
        conv.setId(conv.getType().name() + "_" + conv.getTargetId());
        conv.updateWithMessage(message);
        conv.setUnreadCount(message.isOutgoing(myFid) ? 0 : 1);
        conv.setCreatedAt(message.getTimestamp());
        return conv;
    }

    public void updateWithMessage(ImMessage message) {
        lastMessageId = message.getId();
        lastMessageContent = previewContent(message);
        lastMessageSenderId = message.getSenderId();
        lastMessageType = message.getContentType();
        lastMessageTime = message.getTimestamp();
        lastActiveAt = message.getTimestamp();
    }

    private String previewContent(ImMessage message) {
        if (message.getContentType() == null) return message.getContent();
        return switch (message.getContentType()) {
            case TEXT -> message.getContent();
            case HAT -> "[File]";
            case STREAM -> "[Stream]";
            case SYMKEY -> "[Symkey]";
            case MEMBERS -> "[Members]";
            case HISTORY -> "[History]";
            case REQUEST -> "[Request]";
            case RESPONSE -> "[Response]";
            case TYPING -> "typing...";
            case RECEIPT, PRESENCE -> "";
            case REACTION -> "[Reaction]";
            case EDIT -> message.getContent();
            case DELETE -> "[Deleted]";
            case FORWARD -> "[Forwarded]";
        };
    }

    public void incrementUnread() {
        unreadCount = (unreadCount != null ? unreadCount : 0) + 1;
    }

    public void markAsRead() {
        unreadCount = 0;
    }

    public String toJson() { return JsonUtils.toJson(this); }
    public static Conversation fromJson(String json) { return JsonUtils.fromJson(json, Conversation.class); }

    // Getters and setters
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public ImType getType() { return type; }
    public void setType(ImType type) { this.type = type; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getLastMessageId() { return lastMessageId; }
    public void setLastMessageId(String lastMessageId) { this.lastMessageId = lastMessageId; }
    public String getLastMessageContent() { return lastMessageContent; }
    public void setLastMessageContent(String lastMessageContent) { this.lastMessageContent = lastMessageContent; }
    public String getLastMessageSenderId() { return lastMessageSenderId; }
    public void setLastMessageSenderId(String lastMessageSenderId) { this.lastMessageSenderId = lastMessageSenderId; }
    public ContentType getLastMessageType() { return lastMessageType; }
    public void setLastMessageType(ContentType lastMessageType) { this.lastMessageType = lastMessageType; }
    public Long getLastMessageTime() { return lastMessageTime; }
    public void setLastMessageTime(Long lastMessageTime) { this.lastMessageTime = lastMessageTime; }
    public Integer getUnreadCount() { return unreadCount; }
    public void setUnreadCount(Integer unreadCount) { this.unreadCount = unreadCount; }
    public Boolean getPinned() { return pinned; }
    public void setPinned(Boolean pinned) { this.pinned = pinned; }
    public Boolean getMuted() { return muted; }
    public void setMuted(Boolean muted) { this.muted = muted; }
    public Long getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Long lastActiveAt) { this.lastActiveAt = lastActiveAt; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
}
