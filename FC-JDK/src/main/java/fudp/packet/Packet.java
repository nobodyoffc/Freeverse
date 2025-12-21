package fudp.packet;

import fudp.packet.frames.*;
import fudp.util.ByteUtils;
import fudp.util.Varint;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * FUDP Packet
 *
 * Structure:
 * - PacketHeader (21 bytes, plaintext)
 * - CryptoDataByte Bundle (encrypted payload)
 * 
 * Encrypted Payload Structure:
 * - Timestamp (8 bytes): Current time for replay protection
 * - Session Epoch (8 bytes): Random value generated at node startup for restart detection
 * - Frames (variable): Protocol frames
 */
public class Packet {

    private PacketHeader header;
    private List<Frame> frames;
    private byte[] encryptedPayload;

    // Metadata for transmission
    private long timestamp;
    private long sessionEpoch;  // Sender's session epoch for restart detection
    private byte[] peerPublicKey;
    // Note: usedKeyName field removed - no longer needed without Symkey encryption

    public Packet() {
        this.header = new PacketHeader();
        this.frames = new ArrayList<>();
    }

    public Packet(long connectionId, long packetNumber) {
        this.header = new PacketHeader(connectionId, packetNumber);
        this.frames = new ArrayList<>();
    }

    /**
     * Add a frame to the packet
     */
    public void addFrame(Frame frame) {
        frames.add(frame);
    }

    /**
     * Serialize all frames to plaintext bytes (before encryption).
     * 
     * @param sessionEpoch The sender's session epoch for restart detection
     * @return Serialized bytes including timestamp, session epoch, and frames
     */
    public byte[] serializeFrames(long sessionEpoch) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Write timestamp (8 bytes) for replay protection
            out.write(ByteUtils.longToBytes(System.currentTimeMillis()));
            
            // Write session epoch (8 bytes) for restart detection
            out.write(ByteUtils.longToBytes(sessionEpoch));

            // Write all frames
            for (Frame frame : frames) {
                out.write(frame.toBytes());
            }

            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize frames", e);
        }
    }

    /**
     * Parse frames from decrypted payload.
     * Extracts timestamp and session epoch for replay protection and restart detection.
     */
    public void parseFrames(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);

        // Read timestamp (8 bytes)
        this.timestamp = buffer.getLong();
        
        // Read session epoch (8 bytes)
        this.sessionEpoch = buffer.getLong();

        // Parse frames
        frames.clear();
        while (buffer.hasRemaining()) {
            int typeByte = (int) Varint.decode(buffer);
            FrameType frameType = FrameType.fromValue(typeByte);

            Frame frame = switch (frameType) {
                case PADDING -> {
                    // Skip padding
                    yield null;
                }
                case STREAM -> StreamFrame.parse(buffer, typeByte);
                case ACK -> AckFrame.parse(buffer);
                case CONNECTION_CLOSE -> ConnectionCloseFrame.parse(buffer);
                case MAX_DATA -> MaxDataFrame.parse(buffer);
                case MAX_STREAM_DATA -> MaxStreamDataFrame.parse(buffer);
                case MAX_STREAMS -> MaxStreamsFrame.parse(buffer);
            };

            if (frame != null) {
                frames.add(frame);
            }
        }
    }

    /**
     * Serialize the complete packet (header + encrypted payload)
     */
    public byte[] toBytes() {
        if (encryptedPayload == null) {
            throw new IllegalStateException("Encrypted payload not set");
        }

        return ByteUtils.concat(header.toBytes(), encryptedPayload);
    }

    /**
     * Parse a packet from raw bytes
     */
    public static Packet fromBytes(byte[] data) {
        if (data.length < PacketHeader.HEADER_SIZE) {
            throw new IllegalArgumentException("Packet too small");
        }

        Packet packet = new Packet();
        packet.header = PacketHeader.fromBytes(data);
        packet.encryptedPayload = ByteUtils.copy(data, PacketHeader.HEADER_SIZE,
                data.length - PacketHeader.HEADER_SIZE);
        return packet;
    }

    /**
     * Check if the packet is ACK-eliciting
     */
    public boolean isAckEliciting() {
        for (Frame frame : frames) {
            if (frame.isAckEliciting()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the total size of all frames
     */
    public int getFramesSize() {
        int size = 16; // timestamp (8) + sessionEpoch (8)
        for (Frame frame : frames) {
            size += frame.getSize();
        }
        return size;
    }
    
    /**
     * Get the sender's session epoch.
     * This value changes when the sender restarts.
     */
    public long getSessionEpoch() {
        return sessionEpoch;
    }

    // Getters and setters
    public PacketHeader getHeader() {
        return header;
    }

    public void setHeader(PacketHeader header) {
        this.header = header;
    }

    public List<Frame> getFrames() {
        return frames;
    }

    public void setFrames(List<Frame> frames) {
        this.frames = frames;
    }

    public byte[] getEncryptedPayload() {
        return encryptedPayload;
    }

    public void setEncryptedPayload(byte[] encryptedPayload) {
        this.encryptedPayload = encryptedPayload;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public byte[] getPeerPublicKey() {
        return peerPublicKey;
    }

    public void setPeerPublicKey(byte[] peerPublicKey) {
        this.peerPublicKey = peerPublicKey;
    }

    public long getConnectionId() {
        return header.getConnectionId();
    }

    public long getPacketNumber() {
        return header.getPacketNumber();
    }

    @Override
    public String toString() {
        return String.format("Packet[%s, frames=%d]", header, frames.size());
    }
}
