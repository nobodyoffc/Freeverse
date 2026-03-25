package fudp.message;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Relay message for forwarding data through intermediate nodes.
 * 
 * Privacy modes:
 * - Anonymous (default): senderFid is empty, target doesn't know origin
 * - Identified: senderFid is set, enables bidirectional communication (for file transfer)
 * 
 * Session support:
 * - sessionId groups related messages (e.g., file offer, accept, chunks)
 * - Relay maintains bidirectional routing for entire session
 *
 * Payload format:
 * ┌─────────────────────────────────────┐
 * │ Flags (1 byte)                      │  bit0=hasSenderFid, bit1=hasSessionId
 * ├─────────────────────────────────────┤
 * │ Target FID Length (2 bytes)         │
 * ├─────────────────────────────────────┤
 * │ Target FID (UTF-8 string)           │
 * ├─────────────────────────────────────┤
 * │ [Sender FID Length (2 bytes)]       │  (if hasSenderFid)
 * ├─────────────────────────────────────┤
 * │ [Sender FID (UTF-8 string)]         │  (if hasSenderFid)
 * ├─────────────────────────────────────┤
 * │ [Session ID (8 bytes)]              │  (if hasSessionId)
 * ├─────────────────────────────────────┤
 * │ Hop Count (1 byte)                  │
 * ├─────────────────────────────────────┤
 * │ Inner Payload Length (4 bytes)      │
 * ├─────────────────────────────────────┤
 * │ Inner Payload (variable)            │
 * └─────────────────────────────────────┘
 */
public class RelayMessage extends AppMessage {

    public static final int MAX_HOP_COUNT = 5;
    public static final int MAX_RELAY_PAYLOAD = 64 * 1024; // 64KB

    private static final int FLAG_HAS_SENDER_FID = 0x01;
    private static final int FLAG_HAS_SESSION_ID = 0x02;

    private String targetFid;
    private String senderFid;  // Optional: set for identified relay (file transfer)
    private long sessionId;    // Optional: groups related messages
    private int hopCount;
    private byte[] innerPayload;

    public RelayMessage() {
        super(MessageType.RELAY);
        this.targetFid = "";
        this.senderFid = null;
        this.sessionId = 0;
        this.hopCount = MAX_HOP_COUNT;
        this.innerPayload = new byte[0];
    }

    public RelayMessage(String targetFid, byte[] innerPayload) {
        super(MessageType.RELAY);
        this.targetFid = targetFid != null ? targetFid : "";
        this.senderFid = null;
        this.sessionId = 0;
        this.hopCount = MAX_HOP_COUNT;
        this.innerPayload = innerPayload != null ? innerPayload : new byte[0];
    }

    public RelayMessage(String targetFid, int hopCount, byte[] innerPayload) {
        super(MessageType.RELAY);
        this.targetFid = targetFid != null ? targetFid : "";
        this.senderFid = null;
        this.sessionId = 0;
        this.hopCount = Math.min(hopCount, MAX_HOP_COUNT);
        this.innerPayload = innerPayload != null ? innerPayload : new byte[0];
    }

    /**
     * Create an identified relay message (sender identity revealed).
     * Used for bidirectional protocols like file transfer.
     */
    public static RelayMessage createIdentified(String targetFid, String senderFid, long sessionId, byte[] innerPayload) {
        RelayMessage msg = new RelayMessage(targetFid, innerPayload);
        msg.setSenderFid(senderFid);
        msg.setSessionId(sessionId);
        return msg;
    }

    public String getTargetFid() {
        return targetFid;
    }

    public void setTargetFid(String targetFid) {
        this.targetFid = targetFid != null ? targetFid : "";
    }

    public String getSenderFid() {
        return senderFid;
    }

    public void setSenderFid(String senderFid) {
        this.senderFid = senderFid;
    }

    /**
     * Check if sender identity is revealed.
     */
    public boolean hasSenderFid() {
        return senderFid != null && !senderFid.isEmpty();
    }

    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Check if this is a session-based relay.
     */
    public boolean hasSessionId() {
        return sessionId != 0;
    }

    public int getHopCount() {
        return hopCount;
    }

    public void setHopCount(int hopCount) {
        this.hopCount = Math.min(hopCount, MAX_HOP_COUNT);
    }

    /**
     * Decrement hop count for forwarding.
     * @return the new hop count
     */
    public int decrementHopCount() {
        return --hopCount;
    }

    public byte[] getInnerPayload() {
        return innerPayload;
    }

    public void setInnerPayload(byte[] innerPayload) {
        this.innerPayload = innerPayload != null ? innerPayload : new byte[0];
    }

    /**
     * Check if payload size is within limits.
     */
    public boolean isPayloadValid() {
        return innerPayload.length <= MAX_RELAY_PAYLOAD;
    }

    @Override
    public byte[] encodePayload() {
        byte[] targetBytes = targetFid.getBytes(StandardCharsets.UTF_8);
        byte[] senderBytes = hasSenderFid() ? senderFid.getBytes(StandardCharsets.UTF_8) : new byte[0];
        
        int flags = 0;
        if (hasSenderFid()) flags |= FLAG_HAS_SENDER_FID;
        if (hasSessionId()) flags |= FLAG_HAS_SESSION_ID;
        
        int size = 1 + 2 + targetBytes.length + 1 + 4 + innerPayload.length;
        if (hasSenderFid()) size += 2 + senderBytes.length;
        if (hasSessionId()) size += 8;
        
        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.put((byte) flags);
        buffer.putShort((short) targetBytes.length);
        buffer.put(targetBytes);
        
        if (hasSenderFid()) {
            buffer.putShort((short) senderBytes.length);
            buffer.put(senderBytes);
        }
        
        if (hasSessionId()) {
            buffer.putLong(sessionId);
        }
        
        buffer.put((byte) hopCount);
        buffer.putInt(innerPayload.length);
        buffer.put(innerPayload);
        return buffer.array();
    }

    @Override
    public void decodePayload(byte[] payload) {
        if (payload == null || payload.length < 8) {
            throw new IllegalArgumentException("Invalid relay message payload");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        
        int flags = buffer.get() & 0xFF;
        boolean hasSender = (flags & FLAG_HAS_SENDER_FID) != 0;
        boolean hasSession = (flags & FLAG_HAS_SESSION_ID) != 0;
        
        int targetLength = buffer.getShort() & 0xFFFF;
        if (buffer.remaining() < targetLength + 5) {
            throw new IllegalArgumentException("Invalid relay message payload: target FID truncated");
        }
        byte[] targetBytes = new byte[targetLength];
        buffer.get(targetBytes);
        targetFid = new String(targetBytes, StandardCharsets.UTF_8);
        
        if (hasSender) {
            int senderLength = buffer.getShort() & 0xFFFF;
            if (buffer.remaining() < senderLength) {
                throw new IllegalArgumentException("Invalid relay message payload: sender FID truncated");
            }
            byte[] senderBytes = new byte[senderLength];
            buffer.get(senderBytes);
            senderFid = new String(senderBytes, StandardCharsets.UTF_8);
        } else {
            senderFid = null;
        }
        
        if (hasSession) {
            sessionId = buffer.getLong();
        } else {
            sessionId = 0;
        }
        
        hopCount = buffer.get() & 0xFF;
        
        int payloadLength = buffer.getInt();
        if (buffer.remaining() < payloadLength) {
            throw new IllegalArgumentException("Invalid relay message payload: inner payload truncated");
        }
        innerPayload = new byte[payloadLength];
        buffer.get(innerPayload);
    }

    @Override
    public String toString() {
        return "RelayMessage{" +
                "messageId=" + messageId +
                ", targetFid='" + targetFid + '\'' +
                ", senderFid='" + (senderFid != null ? senderFid : "ANONYMOUS") + '\'' +
                ", sessionId=" + (sessionId != 0 ? sessionId : "NONE") +
                ", hopCount=" + hopCount +
                ", innerPayloadLength=" + innerPayload.length +
                '}';
    }
}
