package fapi.monitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MetricsCollector 单元测试
 */
@DisplayName("MetricsCollector Tests")
class MetricsCollectorTest {
    
    private MetricsCollector collector;
    
    @BeforeEach
    void setUp() {
        collector = new MetricsCollector();
    }
    
    // ==================== recordRequest() 测试 ====================
    
    @Nested
    @DisplayName("recordRequest() 方法测试")
    class RecordRequestTests {
        
        @Test
        @DisplayName("记录成功请求应增加成功计数")
        void testRecordSuccessRequest() {
            collector.recordRequest("BASE", "base.search", 100, true);
            
            MetricsReport report = collector.getReport();
            
            assertEquals(1, report.getTotalRequests());
            assertEquals(1, report.getSuccessRequests());
            assertEquals(0, report.getFailedRequests());
            assertEquals(100.0, report.getSuccessRate());
        }
        
        @Test
        @DisplayName("记录失败请求应增加失败计数")
        void testRecordFailedRequest() {
            collector.recordRequest("BASE", "base.search", 50, false);
            
            MetricsReport report = collector.getReport();
            
            assertEquals(1, report.getTotalRequests());
            assertEquals(0, report.getSuccessRequests());
            assertEquals(1, report.getFailedRequests());
            assertEquals(0.0, report.getSuccessRate());
        }
        
        @Test
        @DisplayName("记录多个请求应正确统计")
        void testRecordMultipleRequests() {
            collector.recordRequest("BASE", "base.search", 100, true);
            collector.recordRequest("BASE", "base.getByIds", 50, true);
            collector.recordRequest("DISK", "disk.put", 200, false);
            
            MetricsReport report = collector.getReport();
            
            assertEquals(3, report.getTotalRequests());
            assertEquals(2, report.getSuccessRequests());
            assertEquals(1, report.getFailedRequests());
            assertEquals(2.0 / 3 * 100, report.getSuccessRate(), 0.01);
        }
        
        @Test
        @DisplayName("响应时间统计应正确")
        void testResponseTimeStats() {
            collector.recordRequest("BASE", "base.search", 100, true);
            collector.recordRequest("BASE", "base.search", 200, true);
            collector.recordRequest("BASE", "base.search", 300, true);
            
            MetricsReport report = collector.getReport();
            
            assertEquals(100, report.getMinResponseTimeMs());
            assertEquals(300, report.getMaxResponseTimeMs());
            assertEquals(200.0, report.getAvgResponseTimeMs(), 0.01);
        }
        
        @Test
        @DisplayName("组件级别统计应正确")
        void testComponentMetrics() {
            collector.recordRequest("BASE", "base.search", 100, true);
            collector.recordRequest("BASE", "base.getByIds", 150, true);
            collector.recordRequest("DISK", "disk.put", 200, false);
            
            MetricsReport report = collector.getReport();
            
            // BASE 组件统计
            MetricsCollector.ComponentMetrics baseMetrics = report.getComponentMetrics().get("BASE");
            assertNotNull(baseMetrics);
            assertEquals(2, baseMetrics.getRequests());
            assertEquals(2, baseMetrics.getSuccessCount());
            assertEquals(0, baseMetrics.getFailedCount());
            assertEquals(100.0, baseMetrics.getSuccessRate());
            
            // DISK 组件统计
            MetricsCollector.ComponentMetrics diskMetrics = report.getComponentMetrics().get("DISK");
            assertNotNull(diskMetrics);
            assertEquals(1, diskMetrics.getRequests());
            assertEquals(0, diskMetrics.getSuccessCount());
            assertEquals(1, diskMetrics.getFailedCount());
            assertEquals(0.0, diskMetrics.getSuccessRate());
        }
        
        @Test
        @DisplayName("API级别统计应正确")
        void testApiCounts() {
            collector.recordRequest("BASE", "base.search", 100, true);
            collector.recordRequest("BASE", "base.search", 100, true);
            collector.recordRequest("BASE", "base.getByIds", 100, true);
            
            MetricsReport report = collector.getReport();
            MetricsCollector.ComponentMetrics baseMetrics = report.getComponentMetrics().get("BASE");
            
            assertEquals(2L, baseMetrics.getApiCounts().get("base.search"));
            assertEquals(1L, baseMetrics.getApiCounts().get("base.getByIds"));
        }
    }
    
    // ==================== getReport() 测试 ====================
    
    @Nested
    @DisplayName("getReport() 方法测试")
    class GetReportTests {
        
        @Test
        @DisplayName("无请求时报告应为初始值")
        void testEmptyReport() {
            MetricsReport report = collector.getReport();
            
            assertEquals(0, report.getTotalRequests());
            assertEquals(0, report.getSuccessRequests());
            assertEquals(0, report.getFailedRequests());
            assertEquals(100.0, report.getSuccessRate()); // 无请求时成功率为100%
            assertEquals(0.0, report.getAvgResponseTimeMs());
            assertEquals(0, report.getMinResponseTimeMs());
            assertEquals(0, report.getMaxResponseTimeMs());
            assertTrue(report.getComponentMetrics().isEmpty());
        }
        
        @Test
        @DisplayName("报告应包含时间戳")
        void testReportHasTimestamp() {
            MetricsReport report = collector.getReport();
            
            assertTrue(report.getTimestamp() > 0);
            assertTrue(report.getTimestamp() <= System.currentTimeMillis());
        }
        
        @Test
        @DisplayName("QPS计算应大于等于0")
        void testQpsCalculation() {
            collector.recordRequest("BASE", "base.search", 100, true);
            
            MetricsReport report = collector.getReport();
            
            assertTrue(report.getCurrentQps() >= 0);
        }
    }
    
    // ==================== reset() 测试 ====================
    
    @Nested
    @DisplayName("reset() 方法测试")
    class ResetTests {
        
        @Test
        @DisplayName("重置后所有计数应为0")
        void testResetClearsAllMetrics() {
            collector.recordRequest("BASE", "base.search", 100, true);
            collector.recordRequest("DISK", "disk.put", 200, false);
            
            collector.reset();
            
            MetricsReport report = collector.getReport();
            assertEquals(0, report.getTotalRequests());
            assertEquals(0, report.getSuccessRequests());
            assertEquals(0, report.getFailedRequests());
            assertTrue(report.getComponentMetrics().isEmpty());
        }
    }
    
    // ==================== 并发安全测试 ====================
    
    @Nested
    @DisplayName("并发安全测试")
    class ConcurrencyTests {
        
        @Test
        @DisplayName("多线程记录请求应正确统计")
        void testConcurrentRecording() throws InterruptedException {
            int threadCount = 10;
            int requestsPerThread = 1000;
            
            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < requestsPerThread; j++) {
                        collector.recordRequest("BASE", "base.search", j % 100, j % 2 == 0);
                    }
                });
            }
            
            // 启动所有线程
            for (Thread thread : threads) {
                thread.start();
            }
            
            // 等待所有线程完成
            for (Thread thread : threads) {
                thread.join();
            }
            
            MetricsReport report = collector.getReport();
            
            // 验证总请求数
            assertEquals(threadCount * requestsPerThread, report.getTotalRequests());
            
            // 验证成功/失败数（每个线程一半成功一半失败）
            assertEquals(threadCount * requestsPerThread / 2, report.getSuccessRequests());
            assertEquals(threadCount * requestsPerThread / 2, report.getFailedRequests());
        }
    }
    
    // ==================== ComponentMetrics 测试 ====================
    
    @Nested
    @DisplayName("ComponentMetrics 内部类测试")
    class ComponentMetricsTests {
        
        @Test
        @DisplayName("平均响应时间计算正确")
        void testAvgResponseTime() {
            MetricsCollector.ComponentMetrics metrics = new MetricsCollector.ComponentMetrics();
            metrics.record("api1", 100, true);
            metrics.record("api1", 200, true);
            metrics.record("api1", 300, true);
            
            assertEquals(200.0, metrics.getAvgResponseTimeMs(), 0.01);
        }
        
        @Test
        @DisplayName("无请求时平均响应时间为0")
        void testAvgResponseTimeWithNoRequests() {
            MetricsCollector.ComponentMetrics metrics = new MetricsCollector.ComponentMetrics();
            
            assertEquals(0.0, metrics.getAvgResponseTimeMs());
        }
        
        @Test
        @DisplayName("无请求时成功率为100%")
        void testSuccessRateWithNoRequests() {
            MetricsCollector.ComponentMetrics metrics = new MetricsCollector.ComponentMetrics();
            
            assertEquals(100.0, metrics.getSuccessRate());
        }
    }
}

