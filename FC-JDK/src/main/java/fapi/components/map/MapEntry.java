package fapi.components.map;

/**
 * MAP组件数据条目
 * 存储FID到网络地址的映射信息
 */
public class MapEntry {
    
    /** FID (从FUDP层peerId获取，已通过加密验证) */
    private String fid;
    
    /** 公钥 (hex编码，从FUDP连接获取) */
    private String pubkey;
    
    /** 观察到的外部IP (从UDP包源地址获取，用于NAT穿透) */
    private String observedIp;
    
    /** 观察到的外部端口 (从UDP包源端口获取) */
    private int observedPort;
    
    /** 最后活跃时间 (注册或心跳的时间戳) */
    private long lastSeen;
    
    /** 首次注册时间 */
    private long registeredAt;
    
    /** 是否过期 (find时ping失败会设置此标志) */
    private transient Boolean stale;
    
    public MapEntry() {
    }
    
    public MapEntry(String fid, String pubkey, String observedIp, int observedPort) {
        this.fid = fid;
        this.pubkey = pubkey;
        this.observedIp = observedIp;
        this.observedPort = observedPort;
        this.lastSeen = System.currentTimeMillis();
        this.registeredAt = System.currentTimeMillis();
    }
    
    // ==================== Getters and Setters ====================
    
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
    
    public String getObservedIp() {
        return observedIp;
    }
    
    public void setObservedIp(String observedIp) {
        this.observedIp = observedIp;
    }
    
    public int getObservedPort() {
        return observedPort;
    }
    
    public void setObservedPort(int observedPort) {
        this.observedPort = observedPort;
    }
    
    public long getLastSeen() {
        return lastSeen;
    }
    
    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }
    
    public long getRegisteredAt() {
        return registeredAt;
    }
    
    public void setRegisteredAt(long registeredAt) {
        this.registeredAt = registeredAt;
    }
    
    public Boolean getStale() {
        return stale;
    }
    
    public void setStale(Boolean stale) {
        this.stale = stale;
    }
    
    /**
     * 检查条目是否过期（超过指定毫秒数未活跃）
     */
    public boolean isStale(long thresholdMs) {
        return System.currentTimeMillis() - lastSeen > thresholdMs;
    }
    
    /**
     * 检查条目是否应该被清理（超过指定毫秒数未活跃）
     */
    public boolean shouldCleanup(long maxAgeMs) {
        return System.currentTimeMillis() - lastSeen > maxAgeMs;
    }
    
    @Override
    public String toString() {
        return "MapEntry{" +
                "fid='" + fid + '\'' +
                ", observedIp='" + observedIp + '\'' +
                ", observedPort=" + observedPort +
                ", lastSeen=" + lastSeen +
                '}';
    }
}
