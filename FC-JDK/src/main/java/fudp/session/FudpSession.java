package fudp.session;

import com.google.gson.Gson;
import core.crypto.Hash;
import fudp.util.ByteUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * FUDP Session - represents a symmetric key session with a peer
 *
 * Key negotiation flow:
 * - Proposer: PROPOSED → (receive ACK) → ACTIVE
 * - Acceptor: ACCEPTED → (receive encrypted packet) → ACTIVE
 *
 * Key rotation:
 * - Old session: ACTIVE → DEPRECATED → deleted (after 60s)
 * - New session: follows normal flow
 */
public class FudpSession {

    private String id;           // KeyName (sha256(key)[0:6]) in hex
    private byte[] keyName;      // KeyName bytes (6 bytes)
    private byte[] keyBytes;     // Symmetric key (32 bytes)
    private String fid;       // Peer FID
    private String pubkey;   // Peer public key (hex)
    private KeyState state;      // PROPOSED, ACCEPTED, ACTIVE, DEPRECATED
    private long birthTime;      // Creation time, for app-layer rotation decisions
    private long lastUsed;       // Last usage time (optional, for cleanup)

    public FudpSession() {
        this.birthTime = System.currentTimeMillis();
        this.lastUsed = this.birthTime;
    }

    public FudpSession(byte[] symkey, String fid, String pubkey) {
        this();
        this.keyBytes = symkey;
        this.fid = fid;
        this.pubkey = pubkey;
        makeKeyName();
    }

    /**
     * Generate key name from the symmetric key
     */
    public void makeKeyName() {
        if (keyBytes != null) {
            byte[] hash = Hash.sha256(keyBytes);
            this.keyName = Arrays.copyOf(hash, 6);
            this.id = ByteUtils.toHex(keyName);
        }
    }

    /**
     * Check if the session is expired based on age
     * @param maxAgeMs Maximum age in milliseconds
     * @return true if session is older than maxAgeMs
     */
    public boolean isExpired(long maxAgeMs) {
        if (maxAgeMs <= 0) return false;
        return System.currentTimeMillis() > birthTime + maxAgeMs;
    }

    /**
     * Check if the session is inactive
     * @param inactiveMs Inactivity threshold in milliseconds
     * @return true if session hasn't been used for inactiveMs
     */
    public boolean isInactive(long inactiveMs) {
        if (inactiveMs <= 0) return false;
        return System.currentTimeMillis() > lastUsed + inactiveMs;
    }

    /**
     * Clear sensitive data
     */
    public void clear() {
        ByteUtils.clear(keyBytes);
        keyBytes = null;
    }

    public byte[] toBytes() {
        return new Gson().toJson(this).getBytes(StandardCharsets.UTF_8);
    }

    public static FudpSession fromBytes(byte[] bytes) {
        return new Gson().fromJson(new String(bytes, StandardCharsets.UTF_8), FudpSession.class);
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public byte[] getKeyName() {
        return keyName;
    }

    public void setKeyName(byte[] keyName) {
        this.keyName = keyName;
        if (keyName != null) {
            this.id = ByteUtils.toHex(keyName);
        }
    }

    public byte[] getKeyBytes() {
        return keyBytes;
    }

    public void setKeyBytes(byte[] keyBytes) {
        this.keyBytes = keyBytes;
        makeKeyName();
    }

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    public String getPubkey() {
        return pubkey;
    }

    public void setPubkey(String pubkey) {
        this.pubkey = pubkey;
    }

    public KeyState getState() {
        return state;
    }

    public void setState(KeyState state) {
        this.state = state;
    }

    public long getBirthTime() {
        return birthTime;
    }

    public void setBirthTime(long birthTime) {
        this.birthTime = birthTime;
    }

    public long getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(long lastUsed) {
        this.lastUsed = lastUsed;
    }

    public void updateLastUsed() {
        this.lastUsed = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return String.format("FudpSession[id=%s, peer=%s, state=%s]", id, fid, state);
    }
}
