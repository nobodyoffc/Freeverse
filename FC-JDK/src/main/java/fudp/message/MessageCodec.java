package fudp.message;

import fudp.util.Varint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Codec for encoding and decoding application messages.
 */
public class MessageCodec {

    /**
     * Encode an AppMessage to bytes.
     *
     * Format:
     * - Type (1 byte)
     * - Message ID (8 bytes)
     * - Flags (1 byte)
     * - Payload length (varint)
     * - Payload (variable)
     */
    public static byte[] encode(AppMessage message) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Type (1 byte)
            out.write(message.getType().getCode());

            // Message ID (8 bytes)
            ByteBuffer idBuffer = ByteBuffer.allocate(8);
            idBuffer.putLong(message.getMessageId());
            out.write(idBuffer.array());

            // Flags (1 byte)
            out.write(message.getFlags());

            // Payload
            byte[] payload = message.encodePayload();

            // Payload length (varint)
            byte[] lengthBytes = Varint.encode(payload.length);
            out.write(lengthBytes);

            // Payload data
            out.write(payload);

            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode message", e);
        }
    }

    /**
     * Decode bytes to an AppMessage.
     */
    public static AppMessage decode(byte[] data) {
        if (data == null || data.length < 11) {
            throw new IllegalArgumentException("Invalid message data: too short");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);

        // Type (1 byte)
        int typeCode = buffer.get() & 0xFF;
        MessageType type = MessageType.fromCode(typeCode);

        // Message ID (8 bytes)
        long messageId = buffer.getLong();

        // Flags (1 byte)
        int flags = buffer.get() & 0xFF;

        // Payload length (varint)
        int payloadLength = (int) Varint.decode(buffer);

        // Payload
        if (buffer.remaining() < payloadLength) {
            throw new IllegalArgumentException("Invalid message data: payload truncated");
        }
        byte[] payload = new byte[payloadLength];
        buffer.get(payload);

        // Create specific message type
        AppMessage message = createMessage(type);
        message.setMessageId(messageId);
        message.setFlags(flags);
        message.decodePayload(payload);

        return message;
    }

    /**
     * Create an empty message instance based on type.
     */
    private static AppMessage createMessage(MessageType type) {
        return switch (type) {
            case CHAT -> new ChatMessage();
            case CHAT_ACK -> new ChatAckMessage();
            case REQUEST -> new RequestMessage();
            case RESPONSE -> new ResponseMessage();
            case ERROR -> new ErrorMessage();
            case PING -> new PingMessage();
            case PONG -> new PongMessage();
            default -> throw new UnsupportedOperationException("Message type not implemented: " + type);
        };
    }

    /**
     * Get the message type from encoded data without full decode.
     */
    public static MessageType peekType(byte[] data) {
        if (data == null || data.length < 1) {
            throw new IllegalArgumentException("Invalid message data");
        }
        return MessageType.fromCode(data[0] & 0xFF);
    }

    /**
     * Get the message ID from encoded data without full decode.
     */
    public static long peekMessageId(byte[] data) {
        if (data == null || data.length < 9) {
            throw new IllegalArgumentException("Invalid message data");
        }
        ByteBuffer buffer = ByteBuffer.wrap(data, 1, 8);
        return buffer.getLong();
    }
}
