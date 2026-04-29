package fudp.packet;

import java.nio.ByteBuffer;

/**
 * FUDP Packet Header (21 bytes)
 *
 * Structure:
 * - Byte 0: Flags (8 bits)
 * - Byte 1-4: Version (32 bits)
 * - Byte 5-12: Connection ID (64 bits)
 * - Byte 13-20: Packet Number (64 bits)
 */
public class PacketHeader {

    public static final int HEADER_SIZE = 21;
    /**
     * Wire-format version. The current (and only released) format binds the
     * 21-byte serialised header as AEAD AAD on data packets — see
     * FUDP_V2_REPAIR_PLAN.md F1. Receivers reject any other version on the
     * data path; control packets (HELLO / PUBLIC_KEY / CHALLENGE) remain
     * version-agnostic since they are plaintext and transport-layer-only.
     */
    public static final int CURRENT_VERSION = 1;

    // Flag bit positions
    public static final int FLAG_PACKET_TYPE_MASK = 0x03;     // Bits 0-1
    // Bits 2-3 reserved (previously held flags for symkey-negotiation /
    // rekey paths that were removed before any production rollout).
    public static final int FLAG_FIN = 0x10;                   // Bit 4
    public static final int FLAG_HAS_TIMESTAMP = 0x20;         // Bit 5: timestamp present in payload
    public static final int FLAG_HAS_EPOCH = 0x40;             // Bit 6: session epoch present in payload

    // Packet types
    public static final int PACKET_TYPE_DATA = 0x00;
    public static final int PACKET_TYPE_ACK = 0x01;
    public static final int PACKET_TYPE_CONTROL = 0x02;
    public static final int PACKET_TYPE_ERROR = 0x03;

    private byte flags;
    private int version;
    private long connectionId;
    private long packetNumber;

    public PacketHeader() {
        this.version = CURRENT_VERSION;
    }

    public PacketHeader(long connectionId, long packetNumber) {
        this.version = CURRENT_VERSION;
        this.connectionId = connectionId;
        this.packetNumber = packetNumber;
        this.flags = PACKET_TYPE_DATA;
    }

    /**
     * Serialize the header to bytes
     */
    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE);
        buffer.put(flags);
        buffer.putInt(version);
        buffer.putLong(connectionId);
        buffer.putLong(packetNumber);
        return buffer.array();
    }

    /**
     * Parse a header from bytes
     */
    public static PacketHeader fromBytes(byte[] data) {
        return fromBytes(data, 0);
    }

    /**
     * Parse a header from bytes at a specific offset
     */
    public static PacketHeader fromBytes(byte[] data, int offset) {
        if (data.length - offset < HEADER_SIZE) {
            throw new IllegalArgumentException("Not enough data for packet header");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data, offset, HEADER_SIZE);
        PacketHeader header = new PacketHeader();
        header.flags = buffer.get();
        header.version = buffer.getInt();
        header.connectionId = buffer.getLong();
        header.packetNumber = buffer.getLong();
        return header;
    }

    // Packet type helpers
    public int getPacketType() {
        return flags & FLAG_PACKET_TYPE_MASK;
    }

    public void setPacketType(int type) {
        flags = (byte) ((flags & ~FLAG_PACKET_TYPE_MASK) | (type & FLAG_PACKET_TYPE_MASK));
    }

    public boolean isDataPacket() {
        return getPacketType() == PACKET_TYPE_DATA;
    }

    public boolean isAckPacket() {
        return getPacketType() == PACKET_TYPE_ACK;
    }

    public boolean isControlPacket() {
        return getPacketType() == PACKET_TYPE_CONTROL;
    }

    public boolean isErrorPacket() {
        return getPacketType() == PACKET_TYPE_ERROR;
    }

    // Flag helpers
    public boolean isFin() {
        return (flags & FLAG_FIN) != 0;
    }

    public void setFin(boolean value) {
        if (value) {
            flags |= FLAG_FIN;
        } else {
            flags &= ~FLAG_FIN;
        }
    }

    public boolean hasTimestamp() {
        return (flags & FLAG_HAS_TIMESTAMP) != 0;
    }

    public void setHasTimestamp(boolean value) {
        if (value) {
            flags |= FLAG_HAS_TIMESTAMP;
        } else {
            flags &= ~FLAG_HAS_TIMESTAMP;
        }
    }

    public boolean hasEpoch() {
        return (flags & FLAG_HAS_EPOCH) != 0;
    }

    public void setHasEpoch(boolean value) {
        if (value) {
            flags |= FLAG_HAS_EPOCH;
        } else {
            flags &= ~FLAG_HAS_EPOCH;
        }
    }

    // Getters and setters
    public byte getFlags() {
        return flags;
    }

    public void setFlags(byte flags) {
        this.flags = flags;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public long getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(long connectionId) {
        this.connectionId = connectionId;
    }

    public long getPacketNumber() {
        return packetNumber;
    }

    public void setPacketNumber(long packetNumber) {
        this.packetNumber = packetNumber;
    }

    @Override
    public String toString() {
        return String.format("PacketHeader[type=%d, version=%d, connId=%d, pktNum=%d, flags=0x%02x]",
                getPacketType(), version, connectionId, packetNumber, flags);
    }
}
