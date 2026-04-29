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
 * - Timestamp (8 bytes, optional): Current time for replay protection. Present when FLAG_HAS_TIMESTAMP is set.
 *   Omitted for ACK-only packets to save 8 bytes.
 * - Session Epoch (8 bytes, optional): Random value for restart detection. Present when FLAG_HAS_EPOCH is set.
 *   Omitted once the peer has confirmed receipt of the epoch.
 * - Frames (variable): Protocol frames. The last StreamFrame may use implicit length (no length varint).
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
        return serializeFrames(sessionEpoch, true, true);
    }

    /**
     * Serialize all frames to plaintext bytes (before encryption).
     * Conditionally includes timestamp and session epoch to save bytes.
     *
     * <p>Sets the header's HAS_TIMESTAMP / HAS_EPOCH flag bits to match what was
     * actually written into the payload. Callers must invoke this before reading
     * the header bytes (e.g. before passing the header as AEAD AAD).
     *
     * @param sessionEpoch The sender's session epoch for restart detection
     * @param includeTimestamp Whether to include the 8-byte timestamp (skip for ACK-only packets)
     * @param includeEpoch Whether to include the 8-byte session epoch (skip once peer confirmed)
     * @return Serialized bytes including optional timestamp, optional session epoch, and frames
     */
    public byte[] serializeFrames(long sessionEpoch, boolean includeTimestamp, boolean includeEpoch) {
        // Set header flags first so the header reflects what we are about to write.
        // (Previously these were toggled mid-serialization as a side effect.)
        header.setHasTimestamp(includeTimestamp);
        header.setHasEpoch(includeEpoch);

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            if (includeTimestamp) {
                out.write(ByteUtils.longToBytes(System.currentTimeMillis()));
            }
            if (includeEpoch) {
                out.write(ByteUtils.longToBytes(sessionEpoch));
            }

            // F5: every frame carries an explicit length. The previous
            // last-frame-implicit-length optimisation saved 1-2 bytes per
            // packet but, combined with an unauthenticated header, turned
            // a packet truncation into a parser oracle. Header AAD (F1)
            // already prevents truncation, but mandating an explicit
            // length keeps the parser straightforward.
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
     * Extracts timestamp and session epoch based on header flags.
     */
    public void parseFrames(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);

        // Read timestamp (8 bytes) only if present (indicated by header flag)
        if (header.hasTimestamp()) {
            this.timestamp = buffer.getLong();
        } else {
            this.timestamp = System.currentTimeMillis();
        }

        // Read session epoch (8 bytes) only if present (indicated by header flag)
        if (header.hasEpoch()) {
            this.sessionEpoch = buffer.getLong();
        } else {
            this.sessionEpoch = 0;
        }

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
        int size = 0;
        if (header.hasTimestamp()) size += 8;  // timestamp
        if (header.hasEpoch()) size += 8;      // sessionEpoch
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
