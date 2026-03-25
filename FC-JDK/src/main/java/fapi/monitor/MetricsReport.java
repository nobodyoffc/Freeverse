package fapi.monitor;

import java.util.HashMap;
import java.util.Map;

/**
 * 性能指标报告
 */
public class MetricsReport {
    
    /** 报告时间戳 */
    private long timestamp;
    
    /** 总请求数 */
    private long totalRequests;
    
    /** 成功请求数 */
    private long successRequests;
    
    /** 失败请求数 */
    private long failedRequests;
    
    /** 成功率（百分比） */
    private double successRate;
    
    /** 平均响应时间（毫秒） */
    private double avgResponseTimeMs;
    
    /** 最小响应时间（毫秒） */
    private long minResponseTimeMs;
    
    /** 最大响应时间（毫秒） */
    private long maxResponseTimeMs;
    
    /** 当前QPS */
    private double currentQps;
    
    /** 组件级别指标 */
    private Map<String, MetricsCollector.ComponentMetrics> componentMetrics = new HashMap<>();
    
    // ==================== Getters and Setters ====================
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public long getTotalRequests() {
        return totalRequests;
    }
    
    public void setTotalRequests(long totalRequests) {
        this.totalRequests = totalRequests;
    }
    
    public long getSuccessRequests() {
        return successRequests;
    }
    
    public void setSuccessRequests(long successRequests) {
        this.successRequests = successRequests;
    }
    
    public long getFailedRequests() {
        return failedRequests;
    }
    
    public void setFailedRequests(long failedRequests) {
        this.failedRequests = failedRequests;
    }
    
    public double getSuccessRate() {
        return successRate;
    }
    
    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }
    
    public double getAvgResponseTimeMs() {
        return avgResponseTimeMs;
    }
    
    public void setAvgResponseTimeMs(double avgResponseTimeMs) {
        this.avgResponseTimeMs = avgResponseTimeMs;
    }
    
    public long getMinResponseTimeMs() {
        return minResponseTimeMs;
    }
    
    public void setMinResponseTimeMs(long minResponseTimeMs) {
        this.minResponseTimeMs = minResponseTimeMs;
    }
    
    public long getMaxResponseTimeMs() {
        return maxResponseTimeMs;
    }
    
    public void setMaxResponseTimeMs(long maxResponseTimeMs) {
        this.maxResponseTimeMs = maxResponseTimeMs;
    }
    
    public double getCurrentQps() {
        return currentQps;
    }
    
    public void setCurrentQps(double currentQps) {
        this.currentQps = currentQps;
    }
    
    public Map<String, MetricsCollector.ComponentMetrics> getComponentMetrics() {
        return componentMetrics;
    }
    
    public void setComponentMetrics(Map<String, MetricsCollector.ComponentMetrics> componentMetrics) {
        this.componentMetrics = componentMetrics;
    }
    
    @Override
    public String toString() {
        return String.format(
            "MetricsReport{total=%d, success=%d (%.1f%%), failed=%d, avgTime=%.1fms, qps=%.1f}",
            totalRequests, successRequests, successRate, failedRequests, avgResponseTimeMs, currentQps
        );
    }
}

