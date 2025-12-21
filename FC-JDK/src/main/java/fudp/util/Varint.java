package fudp.util;

import java.nio.ByteBuffer;

/**
 * QUIC-style variable length integer encoding/decoding
 *
 * 2-bit prefix determines length:
 *   00 = 1 byte  (6 bits data, max 63)
 *   01 = 2 bytes (14 bits data, max 16383)
 *   10 = 4 bytes (30 bits data, max 1073741823)
 *   11 = 8 bytes (62 bits data, max 4611686018427387903)
 */
public class Varint {

    public static final long MAX_1_BYTE = 63;
    public static final long MAX_2_BYTES = 16383;
    public static final long MAX_4_BYTES = 1073741823L;
    public static final long MAX_8_BYTES = 4611686018427387903L;

    /**
     * Encode a long value as a varint
     * @param value The value to encode (must be non-negative)
     * @return The encoded bytes
     */
    public static byte[] encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Varint value must be non-negative: " + value);
        }

        if (value <= MAX_1_BYTE) {
            return new byte[] { (byte) value };
        } else if (value <= MAX_2_BYTES) {
            return new byte[] {
                (byte) ((value >> 8) | 0x40),
                (byte) value
            };
        } else if (value <= MAX_4_BYTES) {
            return new byte[] {
                (byte) ((value >> 24) | 0x80),
                (byte) (value >> 16),
                (byte) (value >> 8),
                (byte) value
            };
        } else if (value <= MAX_8_BYTES) {
            return new byte[] {
                (byte) ((value >> 56) | 0xC0),
                (byte) (value >> 48),
                (byte) (value >> 40),
                (byte) (value >> 32),
                (byte) (value >> 24),
                (byte) (value >> 16),
                (byte) (value >> 8),
                (byte) value
            };
        } else {
            throw new IllegalArgumentException("Varint value too large: " + value);
        }
    }

    /**
     * Decode a varint from a ByteBuffer
     * @param buffer The buffer to read from
     * @return The decoded value
     */
    public static long decode(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            throw new IllegalArgumentException("Buffer is empty");
        }

        byte first = buffer.get();
        int prefix = (first & 0xC0) >> 6;

        switch (prefix) {
            case 0: // 1 byte
                return first & 0x3F;

            case 1: // 2 bytes
                if (buffer.remaining() < 1) {
                    throw new IllegalArgumentException("Not enough bytes for 2-byte varint");
                }
                return ((first & 0x3FL) << 8) | (buffer.get() & 0xFFL);

            case 2: // 4 bytes
                if (buffer.remaining() < 3) {
                    throw new IllegalArgumentException("Not enough bytes for 4-byte varint");
                }
                return ((first & 0x3FL) << 24) |
                       ((buffer.get() & 0xFFL) << 16) |
                       ((buffer.get() & 0xFFL) << 8) |
                       (buffer.get() & 0xFFL);

            case 3: // 8 bytes
                if (buffer.remaining() < 7) {
                    throw new IllegalArgumentException("Not enough bytes for 8-byte varint");
                }
                return ((first & 0x3FL) << 56) |
                       ((buffer.get() & 0xFFL) << 48) |
                       ((buffer.get() & 0xFFL) << 40) |
                       ((buffer.get() & 0xFFL) << 32) |
                       ((buffer.get() & 0xFFL) << 24) |
                       ((buffer.get() & 0xFFL) << 16) |
                       ((buffer.get() & 0xFFL) << 8) |
                       (buffer.get() & 0xFFL);

            default:
                throw new IllegalStateException("Invalid varint prefix");
        }
    }

    /**
     * Decode a varint from a byte array
     * @param data The byte array
     * @param offset The offset to start reading from
     * @return A DecodeResult containing the value and bytes consumed
     */
    public static DecodeResult decode(byte[] data, int offset) {
        ByteBuffer buffer = ByteBuffer.wrap(data, offset, data.length - offset);
        int startPos = buffer.position();
        long value = decode(buffer);
        int bytesConsumed = buffer.position() - startPos;
        return new DecodeResult(value, bytesConsumed);
    }

    /**
     * Get the number of bytes needed to encode a value
     * @param value The value to encode
     * @return The number of bytes needed
     */
    public static int encodedLength(long value) {
        if (value <= MAX_1_BYTE) return 1;
        if (value <= MAX_2_BYTES) return 2;
        if (value <= MAX_4_BYTES) return 4;
        return 8;
    }

    /**
     * Result of decoding a varint
     */
    public static class DecodeResult {
        public final long value;
        public final int bytesConsumed;

        public DecodeResult(long value, int bytesConsumed) {
            this.value = value;
            this.bytesConsumed = bytesConsumed;
        }
    }
}
