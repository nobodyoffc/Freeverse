package fapi.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 性能指标收集器
 * 
 * 收集和统计服务性能指标：
 * 1. 请求计数
 * 2. 响应时间统计
 * 3. 每组件统计
 */
public class MetricsCollector {
    private static final Logger log = LoggerFactory.getLogger(MetricsCollector.class);
    
    /** 默认滑动窗口大小（毫秒） */
    private static final long DEFAULT_WINDOW_SIZE_MS = 60_000;
    
    // ==================== 全局统计 ====================
    
    /** 总请求数 */
    private final LongAdder totalRequests = new LongAdder();
    
    /** 成功请求数 */
    private final LongAdder successRequests = new LongAdder();
    
    /** 失败请求数 */
    private final LongAdder failedRequests = new LongAdder();
    
    /** 总响应时间（毫秒） */
    private final LongAdder totalResponseTimeMs = new LongAdder();
    
    /** 最小响应时间 */
    private final AtomicLong minResponseTimeMs = new AtomicLong(Long.MAX_VALUE);
    
    /** 最大响应时间 */
    private final AtomicLong maxResponseTimeMs = new AtomicLong(0);
    
    // ==================== 每组件统计 ====================
    
    /** 每组件的指标 */
    private final ConcurrentHashMap<String, ComponentMetrics> componentMetrics = new ConcurrentHashMap<>();
    
    // ==================== 时间窗口统计 ====================
    
    /** 窗口起始时间 */
    private volatile long windowStartTime = System.currentTimeMillis();
    
    /** 窗口内请求数 */
    private final LongAdder windowRequests = new LongAdder();
    
    /** 窗口大小（毫秒） */
    private final long windowSizeMs;
    
    public MetricsCollector() {
        this(DEFAULT_WINDOW_SIZE_MS);
    }
    
    public MetricsCollector(long windowSizeMs) {
        this.windowSizeMs = windowSizeMs;
    }
    
    // ==================== 记录方法 ====================
    
    /**
     * 记录请求
     * 
     * @param component 组件名称
     * @param api 完整API名称（如 base.search）
     * @param durationMs 响应时间（毫秒）
     * @param success 是否成功
     */
    public void recordRequest(String component, String api, long durationMs, boolean success) {
        // 全局统计
        totalRequests.increment();
        if (success) {
            successRequests.increment();
        } else {
            failedRequests.increment();
        }
        
        totalResponseTimeMs.add(durationMs);
        updateMinMax(durationMs);
        
        // 窗口统计
        checkAndResetWindow();
        windowRequests.increment();
        
        // 组件统计
        componentMetrics.computeIfAbsent(component, k -> new ComponentMetrics())
            .record(api, durationMs, success);
    }
    
    /**
     * 记录请求（简化版）
     */
    public void recordRequest(String component, long durationMs, boolean success) {
        recordRequest(component, null, durationMs, success);
    }
    
    /**
     * 更新最小/最大响应时间
     */
    private void updateMinMax(long durationMs) {
        // 更新最小值
        long currentMin;
        do {
            currentMin = minResponseTimeMs.get();
            if (durationMs >= currentMin) break;
        } while (!minResponseTimeMs.compareAndSet(currentMin, durationMs));
        
        // 更新最大值
        long currentMax;
        do {
            currentMax = maxResponseTimeMs.get();
            if (durationMs <= currentMax) break;
        } while (!maxResponseTimeMs.compareAndSet(currentMax, durationMs));
    }
    
    /**
     * 检查并重置时间窗口
     */
    private void checkAndResetWindow() {
        long now = System.currentTimeMillis();
        if (now - windowStartTime >= windowSizeMs) {
            synchronized (this) {
                if (now - windowStartTime >= windowSizeMs) {
                    windowStartTime = now;
                    windowRequests.reset();
                }
            }
        }
    }
    
    // ==================== 获取报告 ====================
    
    /**
     * 获取性能报告
     */
    public MetricsReport getReport() {
        MetricsReport report = new MetricsReport();
        
        long total = totalRequests.sum();
        long success = successRequests.sum();
        long failed = failedRequests.sum();
        long totalTime = totalResponseTimeMs.sum();
        
        report.setTotalRequests(total);
        report.setSuccessRequests(success);
        report.setFailedRequests(failed);
        
        // 成功率
        report.setSuccessRate(total > 0 ? (double) success / total * 100 : 100.0);
        
        // 响应时间
        report.setAvgResponseTimeMs(total > 0 ? (double) totalTime / total : 0);
        report.setMinResponseTimeMs(minResponseTimeMs.get() == Long.MAX_VALUE ? 0 : minResponseTimeMs.get());
        report.setMaxResponseTimeMs(maxResponseTimeMs.get());
        
        // 当前窗口QPS
        checkAndResetWindow();
        long windowDuration = System.currentTimeMillis() - windowStartTime;
        if (windowDuration > 0) {
            report.setCurrentQps((double) windowRequests.sum() / windowDuration * 1000);
        }
        
        // 组件指标
        Map<String, ComponentMetrics> componentMetricsCopy = new HashMap<>(componentMetrics);
        report.setComponentMetrics(componentMetricsCopy);
        
        report.setTimestamp(System.currentTimeMillis());
        
        return report;
    }
    
    /**
     * 重置所有指标
     */
    public void reset() {
        totalRequests.reset();
        successRequests.reset();
        failedRequests.reset();
        totalResponseTimeMs.reset();
        minResponseTimeMs.set(Long.MAX_VALUE);
        maxResponseTimeMs.set(0);
        windowStartTime = System.currentTimeMillis();
        windowRequests.reset();
        componentMetrics.clear();
        
        log.info("Metrics reset");
    }
    
    // ==================== 内部类 ====================
    
    /**
     * 组件级别的指标
     */
    public static class ComponentMetrics {
        private final LongAdder requests = new LongAdder();
        private final LongAdder successCount = new LongAdder();
        private final LongAdder failedCount = new LongAdder();
        private final LongAdder totalTimeMs = new LongAdder();
        
        /** 每API的请求计数 */
        private final ConcurrentHashMap<String, LongAdder> apiCounts = new ConcurrentHashMap<>();
        
        public void record(String api, long durationMs, boolean success) {
            requests.increment();
            if (success) {
                successCount.increment();
            } else {
                failedCount.increment();
            }
            totalTimeMs.add(durationMs);
            
            if (api != null) {
                apiCounts.computeIfAbsent(api, k -> new LongAdder()).increment();
            }
        }
        
        public long getRequests() {
            return requests.sum();
        }
        
        public long getSuccessCount() {
            return successCount.sum();
        }
        
        public long getFailedCount() {
            return failedCount.sum();
        }
        
        public double getAvgResponseTimeMs() {
            long total = requests.sum();
            return total > 0 ? (double) totalTimeMs.sum() / total : 0;
        }
        
        public double getSuccessRate() {
            long total = requests.sum();
            return total > 0 ? (double) successCount.sum() / total * 100 : 100.0;
        }
        
        public Map<String, Long> getApiCounts() {
            Map<String, Long> result = new HashMap<>();
            apiCounts.forEach((api, count) -> result.put(api, count.sum()));
            return result;
        }
    }
}

