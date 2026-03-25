package fapi.monitor;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康状态
 */
public class HealthStatus {
    
    /** 检查时间戳 */
    private long timestamp;
    
    /** 整体是否健康 */
    private boolean overallHealthy;
    
    /** 错误消息 */
    private String errorMessage;
    
    /** 各组件健康状态 */
    private Map<String, ComponentHealth> components = new HashMap<>();
    
    /** ES连接状态 */
    private boolean esConnected;
    
    /** ES是否必需 */
    private boolean esRequired;
    
    /** RPC连接状态 */
    private boolean rpcConnected;
    
    /** FUDP节点运行状态 */
    private boolean fudpRunning;
    
    /** 连接的对等节点数量 */
    private int connectedPeers;
    
    /** 本地FID */
    private String localFid;
    
    /** 监听端口 */
    private int port;
    
    // ==================== Getters and Setters ====================
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public boolean isOverallHealthy() {
        return overallHealthy;
    }
    
    public void setOverallHealthy(boolean overallHealthy) {
        this.overallHealthy = overallHealthy;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public Map<String, ComponentHealth> getComponents() {
        return components;
    }
    
    public void setComponents(Map<String, ComponentHealth> components) {
        this.components = components;
    }
    
    public boolean isEsConnected() {
        return esConnected;
    }
    
    public void setEsConnected(boolean esConnected) {
        this.esConnected = esConnected;
    }
    
    public boolean isEsRequired() {
        return esRequired;
    }
    
    public void setEsRequired(boolean esRequired) {
        this.esRequired = esRequired;
    }
    
    public boolean isRpcConnected() {
        return rpcConnected;
    }
    
    public void setRpcConnected(boolean rpcConnected) {
        this.rpcConnected = rpcConnected;
    }
    
    public boolean isFudpRunning() {
        return fudpRunning;
    }
    
    public void setFudpRunning(boolean fudpRunning) {
        this.fudpRunning = fudpRunning;
    }
    
    public int getConnectedPeers() {
        return connectedPeers;
    }
    
    public void setConnectedPeers(int connectedPeers) {
        this.connectedPeers = connectedPeers;
    }
    
    public String getLocalFid() {
        return localFid;
    }
    
    public void setLocalFid(String localFid) {
        this.localFid = localFid;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
}

