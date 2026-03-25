package fapi.wal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import core.crypto.Hash;

import java.nio.charset.StandardCharsets;

/**
 * WAL (Write-Ahead Log) 条目
 * <p>
 * 每个 WAL 条目代表一个原子操作，用于持久化和恢复。
 * 格式：type,ts,key,data,prevHash,hash
 */
public class WalEntry {
    private static final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    
    /** 操作类型 */
    private WalEntryType type;
    
    /** 时间戳（毫秒） */
    private long ts;
    
    /** 主键（peerId/cashId/requestKey/cycleId） */
    private String key;
    
    /** 操作数据（JSON 序列化） */
    private String data;
    
    /** 前一条记录的哈希 */
    private String prevHash;
    
    /** 当前记录的哈希 */
    private String hash;
    
    /** 序列号（用于排序和恢复） */
    private long seq;
    
    public WalEntry() {
    }
    
    public WalEntry(WalEntryType type, String key, Object data, String prevHash, long seq) {
        this.type = type;
        this.ts = System.currentTimeMillis();
        this.key = key;
        this.data = data != null ? gson.toJson(data) : null;
        this.prevHash = prevHash;
        this.seq = seq;
        this.hash = computeHash();
    }
    
    /**
     * 计算条目哈希
     */
    public String computeHash() {
        String canonical = canonicalWithoutHash();
        return Hash.sha256(canonical);
    }
    
    /**
     * 生成不含 hash 字段的规范字符串
     */
    public String canonicalWithoutHash() {
        return type + "," + ts + "," + seq + "," + 
               (key != null ? key : "") + "," + 
               (data != null ? data : "") + "," + 
               (prevHash != null ? prevHash : "");
    }
    
    /**
     * 验证哈希链完整性
     */
    public boolean verifyHash() {
        return hash != null && hash.equals(computeHash());
    }
    
    /**
     * 验证与前一条记录的链接
     */
    public boolean verifyChain(String expectedPrevHash) {
        return prevHash != null && prevHash.equals(expectedPrevHash);
    }
    
    /**
     * 序列化为 JSON 行
     */
    public String toJsonLine() {
        return gson.toJson(this);
    }
    
    /**
     * 从 JSON 行反序列化
     */
    public static WalEntry fromJsonLine(String line) {
        return gson.fromJson(line, WalEntry.class);
    }
    
    /**
     * 序列化为字节数组
     */
    public byte[] toBytes() {
        return toJsonLine().getBytes(StandardCharsets.UTF_8);
    }
    
    // ==================== Getters and Setters ====================
    
    public WalEntryType getType() {
        return type;
    }
    
    public void setType(WalEntryType type) {
        this.type = type;
    }
    
    public long getTs() {
        return ts;
    }
    
    public void setTs(long ts) {
        this.ts = ts;
    }
    
    public String getKey() {
        return key;
    }
    
    public void setKey(String key) {
        this.key = key;
    }
    
    public String getData() {
        return data;
    }
    
    public void setData(String data) {
        this.data = data;
    }
    
    public String getPrevHash() {
        return prevHash;
    }
    
    public void setPrevHash(String prevHash) {
        this.prevHash = prevHash;
    }
    
    public String getHash() {
        return hash;
    }
    
    public void setHash(String hash) {
        this.hash = hash;
    }
    
    public long getSeq() {
        return seq;
    }
    
    public void setSeq(long seq) {
        this.seq = seq;
    }
    
    /**
     * 解析 data 字段为指定类型
     */
    public <T> T parseData(Class<T> clazz) {
        if (data == null) return null;
        return gson.fromJson(data, clazz);
    }
    
    @Override
    public String toString() {
        return "WalEntry{" +
               "type=" + type +
               ", ts=" + ts +
               ", seq=" + seq +
               ", key='" + key + '\'' +
               ", hash='" + (hash != null ? hash.substring(0, 8) + "..." : "null") + '\'' +
               '}';
    }
}

