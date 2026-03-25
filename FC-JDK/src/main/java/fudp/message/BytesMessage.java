package fudp.message;

import java.nio.ByteBuffer;

/**
 * General-purpose byte array message for arbitrary data transfer.
 *
 * Payload format:
 * ┌─────────────────────────────────────┐
 * │ Data Type (1 byte)                  │  0=raw, 1=json, 2=protobuf, etc.
 * ├─────────────────────────────────────┤
 * │ Data Length (4 bytes)               │
 * ├─────────────────────────────────────┤
 * │ Data (variable)                     │
 * └─────────────────────────────────────┘
 */
public class BytesMessage extends AppMessage {

    public static final int DATA_TYPE_RAW = 0;
    public static final int DATA_TYPE_JSON = 1;
    public static final int DATA_TYPE_PROTOBUF = 2;
    public static final int DATA_TYPE_MSGPACK = 3;

    private int dataType;
    private byte[] data;

    public BytesMessage() {
        super(MessageType.BYTES);
        this.dataType = DATA_TYPE_RAW;
        this.data = new byte[0];
    }

    public BytesMessage(byte[] data) {
        super(MessageType.BYTES);
        this.dataType = DATA_TYPE_RAW;
        this.data = data != null ? data : new byte[0];
    }

    public BytesMessage(byte[] data, int dataType) {
        super(MessageType.BYTES);
        this.dataType = dataType;
        this.data = data != null ? data : new byte[0];
    }

    public int getDataType() {
        return dataType;
    }

    public void setDataType(int dataType) {
        this.dataType = dataType;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data != null ? data : new byte[0];
    }

    @Override
    public byte[] encodePayload() {
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + data.length);
        buffer.put((byte) dataType);
        buffer.putInt(data.length);
        buffer.put(data);
        return buffer.array();
    }

    @Override
    public void decodePayload(byte[] payload) {
        if (payload == null || payload.length < 5) {
            throw new IllegalArgumentException("Invalid bytes message payload");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        dataType = buffer.get() & 0xFF;
        int length = buffer.getInt();
        if (buffer.remaining() < length) {
            throw new IllegalArgumentException("Invalid bytes message payload: data truncated");
        }
        data = new byte[length];
        buffer.get(data);
    }

    @Override
    public String toString() {
        return "BytesMessage{" +
                "messageId=" + messageId +
                ", dataType=" + dataType +
                ", dataLength=" + data.length +
                '}';
    }
}

