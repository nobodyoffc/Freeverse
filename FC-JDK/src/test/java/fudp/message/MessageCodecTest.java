package fudp.message;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MessageCodec.
 */
public class MessageCodecTest {

    @Test
    public void testEncodeDecodeNotifyMessage() {
        // Create message
        NotifyMessage original = new NotifyMessage("Hello, World!".getBytes(StandardCharsets.UTF_8), NotifyMessage.DATA_TYPE_JSON);
        original.setMessageId(12345L);

        // Encode
        byte[] encoded = MessageCodec.encode(original);
        assertNotNull(encoded);
        assertTrue(encoded.length > 10);

        // Decode
        AppMessage decoded = MessageCodec.decode(encoded);
        assertNotNull(decoded);
        assertInstanceOf(NotifyMessage.class, decoded);

        NotifyMessage notify = (NotifyMessage) decoded;
        assertEquals(original.getMessageId(), notify.getMessageId());
        assertEquals(original.getDataType(), notify.getDataType());
        assertArrayEquals(original.getData(), notify.getData());
    }

    @Test
    public void testEncodeDecodeRequestMessage() {
        byte[] data = "{\"action\": \"get\"}".getBytes(StandardCharsets.UTF_8);
        RequestMessage original = new RequestMessage(54321L, "user.profile", data);

        byte[] encoded = MessageCodec.encode(original);
        assertNotNull(encoded);

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
        byte[] data = "{\"name\": \"John\"}".getBytes(StandardCharsets.UTF_8);
        ResponseMessage original = new ResponseMessage(99999L, ResponseMessage.STATUS_SUCCESS, data);

        byte[] encoded = MessageCodec.encode(original);
        assertNotNull(encoded);

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
        ErrorMessage original = new ErrorMessage(404, "Not found");
        original.setMessageId(11111L);

        byte[] encoded = MessageCodec.encode(original);
        assertNotNull(encoded);

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
        PingMessage ping = new PingMessage();
        ping.setMessageId(1L);

        byte[] encodedPing = MessageCodec.encode(ping);
        AppMessage decodedPing = MessageCodec.decode(encodedPing);
        assertInstanceOf(PingMessage.class, decodedPing);
        assertEquals(ping.getTimestamp(), ((PingMessage) decodedPing).getTimestamp());

        PongMessage pong = new PongMessage(ping.getTimestamp());
        pong.setMessageId(2L);

        byte[] encodedPong = MessageCodec.encode(pong);
        AppMessage decodedPong = MessageCodec.decode(encodedPong);
        assertInstanceOf(PongMessage.class, decodedPong);
        assertEquals(pong.getEchoTimestamp(), ((PongMessage) decodedPong).getEchoTimestamp());
    }

    @Test
    public void testEncodeDecodeNotifyAck() {
        NotifyAckMessage original = new NotifyAckMessage(77777L);
        original.setMessageId(88888L);

        byte[] encoded = MessageCodec.encode(original);
        assertNotNull(encoded);

        AppMessage decoded = MessageCodec.decode(encoded);
        assertNotNull(decoded);
        assertInstanceOf(NotifyAckMessage.class, decoded);

        NotifyAckMessage ack = (NotifyAckMessage) decoded;
        assertEquals(original.getMessageId(), ack.getMessageId());
        assertEquals(original.getAckedMessageId(), ack.getAckedMessageId());
    }

    @Test
    public void testPeekType() {
        NotifyMessage notify = new NotifyMessage("Test".getBytes(StandardCharsets.UTF_8));
        byte[] encoded = MessageCodec.encode(notify);

        MessageType type = MessageCodec.peekType(encoded);
        assertEquals(MessageType.NOTIFY, type);
    }

    @Test
    public void testPeekMessageId() {
        NotifyMessage notify = new NotifyMessage("Test".getBytes(StandardCharsets.UTF_8));
        notify.setMessageId(123456789L);
        byte[] encoded = MessageCodec.encode(notify);

        long messageId = MessageCodec.peekMessageId(encoded);
        assertEquals(123456789L, messageId);
    }

    @Test
    public void testFlags() {
        NotifyMessage notify = new NotifyMessage("Test with flags".getBytes(StandardCharsets.UTF_8));
        notify.setMessageId(1L);
        notify.setFlag(AppMessage.FLAG_NEED_ACK);
        notify.setFlag(AppMessage.FLAG_COMPRESSED);

        byte[] encoded = MessageCodec.encode(notify);
        NotifyMessage decoded = (NotifyMessage) MessageCodec.decode(encoded);

        assertTrue(decoded.hasFlag(AppMessage.FLAG_NEED_ACK));
        assertTrue(decoded.hasFlag(AppMessage.FLAG_COMPRESSED));
        assertFalse(decoded.hasFlag(AppMessage.FLAG_ENCRYPTED_APP));
    }

    @Test
    public void testEmptyData() {
        NotifyMessage notify = new NotifyMessage(new byte[0]);
        notify.setMessageId(1L);

        byte[] encoded = MessageCodec.encode(notify);
        NotifyMessage decoded = (NotifyMessage) MessageCodec.decode(encoded);

        assertEquals(0, decoded.getData().length);
    }

    @Test
    public void testLargeData() {
        // 100KB payload
        byte[] largeData = new byte[100_000];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        NotifyMessage notify = new NotifyMessage(largeData);
        notify.setMessageId(1L);

        byte[] encoded = MessageCodec.encode(notify);
        NotifyMessage decoded = (NotifyMessage) MessageCodec.decode(encoded);

        assertArrayEquals(largeData, decoded.getData());
    }

    @Test
    public void testInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> MessageCodec.decode(new byte[5]));
        assertThrows(IllegalArgumentException.class, () -> MessageCodec.decode(null));
    }

    @Test
    public void testUnknownMessageType() {
        byte[] data = new byte[20];
        data[0] = (byte) 0xFF;
        assertThrows(IllegalArgumentException.class, () -> MessageCodec.decode(data));
    }
}
