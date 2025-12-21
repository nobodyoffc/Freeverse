package fudp.message;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Chat message for simple text communication.
 *
 * Payload format:
 * ┌─────────────────────────────────────┐
 * │ Content Type (1 byte)               │  0=text, 1=markdown, 2=json
 * ├─────────────────────────────────────┤
 * │ Content (UTF-8 string)              │
 * └─────────────────────────────────────┘
 */
public class ChatMessage extends AppMessage {

    public static final int CONTENT_TYPE_TEXT = 0;
    public static final int CONTENT_TYPE_MARKDOWN = 1;
    public static final int CONTENT_TYPE_JSON = 2;

    private int contentType;
    private String content;

    public ChatMessage() {
        super(MessageType.CHAT);
        this.contentType = CONTENT_TYPE_TEXT;
        this.content = "";
    }

    public ChatMessage(String content) {
        super(MessageType.CHAT);
        this.contentType = CONTENT_TYPE_TEXT;
        this.content = content;
    }

    public ChatMessage(String content, int contentType) {
        super(MessageType.CHAT);
        this.contentType = contentType;
        this.content = content;
    }

    public int getContentType() {
        return contentType;
    }

    public void setContentType(int contentType) {
        this.contentType = contentType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public byte[] encodePayload() {
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + contentBytes.length);
        buffer.put((byte) contentType);
        buffer.put(contentBytes);
        return buffer.array();
    }

    @Override
    public void decodePayload(byte[] payload) {
        if (payload == null || payload.length < 1) {
            throw new IllegalArgumentException("Invalid chat message payload");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        contentType = buffer.get() & 0xFF;
        byte[] contentBytes = new byte[buffer.remaining()];
        buffer.get(contentBytes);
        content = new String(contentBytes, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "messageId=" + messageId +
                ", contentType=" + contentType +
                ", content='" + content + '\'' +
                '}';
    }
}
