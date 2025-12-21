package fudp.message;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MessageCodec.
 */
public class MessageCodecTest {

    @Test
    public void testEncodDecodeChatMessage() {
        // Create message
        ChatMessage original = new ChatMessage("Hello, World!");
        original.setMessageId(12345L);
        original.setContentType(ChatMessage.CONTENT_TYPE_TEXT);

        // Encode
        byte[] encoded = MessageCodec.encode(original);
        assertNotNull(encoded);
        assertTrue(encoded.length > 10);

        // Decode
        AppMessage decoded = MessageCodec.decode(encoded);
        assertNotNull(decoded);
        assertInstanceOf(ChatMessage.class, decoded);

        ChatMessage chat = (ChatMessage) decoded;
        assertEquals(original.getMessageId(), chat.getMessageId());
        assertEquals(original.getContentType(), chat.getContentType());
        assertEquals(original.getContent(), chat.getContent());
    }

    @Test
    public void testEncodeDecodeRequestMessage() {
        // Create message
        byte[] data = "{\"action\": \"get\"}".getBytes(StandardCharsets.UTF_8);
        RequestMessage original = new RequestMessage(54321L, "user.profile", data);

        // Encode
        byte[] encoded = MessageCodec.encode(original);
        assertNotNull(encoded);

        // Decode
        AppMessage decoded = MessageCodec.decode(encoded);
        assertNotNull(decoded);
        assertInstanceOf(RequestMessage.class, decoded);

        RequestMessage request = (RequestMessage) decoded;
        assertEquals(original.getMessageId(), request.getMessageId());
        assertEquals(original.getSid(), request.getSid());
        assertArrayEquals(original.getData(), request.getData());
    }

    @Test
    public void testEncodeDecodeResponseMessage() {
        // Create message
        byte[] data = "{\"name\": \"John\"}".getBytes(StandardCharsets.UTF_8);
        ResponseMessage original = new ResponseMessage(99999L, ResponseMessage.STATUS_SUCCESS, data);

        // Encode
        byte[] encoded = MessageCodec.encode(original);
        assertNotNull(encoded);

        // Decode
        AppMessage decoded = MessageCodec.decode(encoded);
        assertNotNull(decoded);
        assertInstanceOf(ResponseMessage.class, decoded);

        ResponseMessage response = (ResponseMessage) decoded;
        assertEquals(original.getMessageId(), response.getMessageId());
        assertEquals(original.getStatusCode(), response.getStatusCode());
        assertArrayEquals(original.getData(), response.getData());
        assertTrue(response.isSuccess());
    }

    @Test
    public void testEncodeDecodeErrorMessage() {
        // Create message
        ErrorMessage original = new ErrorMessage(404, "Not found");
        original.setMessageId(11111L);

        // Encode
        byte[] encoded = MessageCodec.encode(original);
        assertNotNull(encoded);

        // Decode
        AppMessage decoded = MessageCodec.decode(encoded);
        assertNotNull(decoded);
        assertInstanceOf(ErrorMessage.class, decoded);

        ErrorMessage error = (ErrorMessage) decoded;
        assertEquals(original.getMessageId(), error.getMessageId());
        assertEquals(original.getErrorCode(), error.getErrorCode());
        assertEquals(original.getErrorMessage(), error.getErrorMessage());
    }

    @Test
    public void testEncodeDecodePingPong() {
        // Ping
        PingMessage ping = new PingMessage();
        ping.setMessageId(1L);

        byte[] encodedPing = MessageCodec.encode(ping);
        AppMessage decodedPing = MessageCodec.decode(encodedPing);
        assertInstanceOf(PingMessage.class, decodedPing);
        assertEquals(ping.getTimestamp(), ((PingMessage) decodedPing).getTimestamp());

        // Pong
        PongMessage pong = new PongMessage(ping.getTimestamp());
        pong.setMessageId(2L);

        byte[] encodedPong = MessageCodec.encode(pong);
        AppMessage decodedPong = MessageCodec.decode(encodedPong);
        assertInstanceOf(PongMessage.class, decodedPong);
        assertEquals(pong.getEchoTimestamp(), ((PongMessage) decodedPong).getEchoTimestamp());
    }

    @Test
    public void testEncodDecodeChatAck() {
        // Create message
        ChatAckMessage original = new ChatAckMessage(77777L);
        original.setMessageId(88888L);

        // Encode
        byte[] encoded = MessageCodec.encode(original);
        assertNotNull(encoded);

        // Decode
        AppMessage decoded = MessageCodec.decode(encoded);
        assertNotNull(decoded);
        assertInstanceOf(ChatAckMessage.class, decoded);

        ChatAckMessage ack = (ChatAckMessage) decoded;
        assertEquals(original.getMessageId(), ack.getMessageId());
        assertEquals(original.getAckedMessageId(), ack.getAckedMessageId());
    }

    @Test
    public void testPeekType() {
        ChatMessage chat = new ChatMessage("Test");
        byte[] encoded = MessageCodec.encode(chat);

        MessageType type = MessageCodec.peekType(encoded);
        assertEquals(MessageType.CHAT, type);
    }

    @Test
    public void testPeekMessageId() {
        ChatMessage chat = new ChatMessage("Test");
        chat.setMessageId(123456789L);
        byte[] encoded = MessageCodec.encode(chat);

        long messageId = MessageCodec.peekMessageId(encoded);
        assertEquals(123456789L, messageId);
    }

    @Test
    public void testFlags() {
        ChatMessage chat = new ChatMessage("Test with flags");
        chat.setMessageId(1L);
        chat.setFlag(AppMessage.FLAG_NEED_ACK);
        chat.setFlag(AppMessage.FLAG_COMPRESSED);

        byte[] encoded = MessageCodec.encode(chat);
        ChatMessage decoded = (ChatMessage) MessageCodec.decode(encoded);

        assertTrue(decoded.hasFlag(AppMessage.FLAG_NEED_ACK));
        assertTrue(decoded.hasFlag(AppMessage.FLAG_COMPRESSED));
        assertFalse(decoded.hasFlag(AppMessage.FLAG_ENCRYPTED_APP));
    }

    @Test
    public void testEmptyContent() {
        ChatMessage chat = new ChatMessage("");
        chat.setMessageId(1L);

        byte[] encoded = MessageCodec.encode(chat);
        ChatMessage decoded = (ChatMessage) MessageCodec.decode(encoded);

        assertEquals("", decoded.getContent());
    }

    @Test
    public void testUnicodeContent() {
        String unicode = "Hello 世界 🌍 مرحبا";
        ChatMessage chat = new ChatMessage(unicode);
        chat.setMessageId(1L);

        byte[] encoded = MessageCodec.encode(chat);
        ChatMessage decoded = (ChatMessage) MessageCodec.decode(encoded);

        assertEquals(unicode, decoded.getContent());
    }

    @Test
    public void testLargeContent() {
        // Create large message (100KB)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("0123456789");
        }
        String largeContent = sb.toString();

        ChatMessage chat = new ChatMessage(largeContent);
        chat.setMessageId(1L);

        byte[] encoded = MessageCodec.encode(chat);
        ChatMessage decoded = (ChatMessage) MessageCodec.decode(encoded);

        assertEquals(largeContent, decoded.getContent());
    }

    @Test
    public void testInvalidData() {
        // Too short
        assertThrows(IllegalArgumentException.class, () -> {
            MessageCodec.decode(new byte[5]);
        });

        // Null
        assertThrows(IllegalArgumentException.class, () -> {
            MessageCodec.decode(null);
        });
    }

    @Test
    public void testUnknownMessageType() {
        // Create data with unknown type
        byte[] data = new byte[20];
        data[0] = (byte) 0xFF; // Unknown type

        assertThrows(IllegalArgumentException.class, () -> {
            MessageCodec.decode(data);
        });
    }

    @Test
    public void testContentTypes() {
        // Markdown
        ChatMessage md = new ChatMessage("# Header", ChatMessage.CONTENT_TYPE_MARKDOWN);
        md.setMessageId(1L);
        byte[] encoded = MessageCodec.encode(md);
        ChatMessage decoded = (ChatMessage) MessageCodec.decode(encoded);
        assertEquals(ChatMessage.CONTENT_TYPE_MARKDOWN, decoded.getContentType());

        // JSON
        ChatMessage json = new ChatMessage("{}", ChatMessage.CONTENT_TYPE_JSON);
        json.setMessageId(2L);
        encoded = MessageCodec.encode(json);
        decoded = (ChatMessage) MessageCodec.decode(encoded);
        assertEquals(ChatMessage.CONTENT_TYPE_JSON, decoded.getContentType());
    }
}
