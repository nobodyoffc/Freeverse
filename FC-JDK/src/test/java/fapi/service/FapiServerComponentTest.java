package fapi.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import clients.NaSaClient.NaSaRpcClient;
import config.Settings;
import data.feipData.ServiceType;
import fapi.FapiCode;
import fapi.FapiComponent;
import fapi.components.BaseComponent;
import fapi.message.FapiRequest;
import fapi.message.FapiResponse;
import fapi.monitor.HealthStatus;
import fapi.monitor.MetricsReport;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * FapiServer 组件集成测试
 * 测试组件管理、请求路由、安全与监控功能
 */
@DisplayName("FapiServer Component Integration Tests")
class FapiServerComponentTest {
    
    @Mock
    private Settings mockSettings;
    
    @Mock
    private ElasticsearchClient mockEsClient;
    
    @Mock
    private NaSaRpcClient mockRpcClient;
    
    @Mock
    private FudpNode mockFudpNode;
    
    @Mock
    private NodeConfig mockNodeConfig;
    
    private FapiServer fapiServer;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        when(mockSettings.getClient(ServiceType.ES)).thenReturn(mockEsClient);
        when(mockSettings.getClient(ServiceType.NASA_RPC)).thenReturn(mockRpcClient);
        when(mockSettings.getBestHeight()).thenReturn(1000L);
        when(mockSettings.getMainFid()).thenReturn("testFid");
        
        when(mockFudpNode.getConfig()).thenReturn(mockNodeConfig);
        when(mockNodeConfig.getPort()).thenReturn(8500);
        when(mockFudpNode.isRunning()).thenReturn(true);
        when(mockFudpNode.getLocalFid()).thenReturn("FEk41xxxx");
        
        fapiServer = new FapiServer(mockSettings);
        fapiServer.setFudpNode(mockFudpNode);
        fapiServer.initialize();
    }
    
    // ==================== 组件管理测试 ====================
    
    @Nested
    @DisplayName("组件管理测试")
    class ComponentManagementTests {
        
        @Test
        @DisplayName("注册组件应成功")
        void testRegisterComponent() {
            BaseComponent baseComponent = new BaseComponent();
            
            fapiServer.registerComponent(baseComponent);
            
            FapiComponent retrieved = fapiServer.getComponent("BASE");
            assertNotNull(retrieved);
            assertEquals("BASE", retrieved.getName());
            assertTrue(retrieved.isHealthy());
        }
        
        @Test
        @DisplayName("按类型获取组件应成功")
        void testGetComponentByClass() {
            BaseComponent baseComponent = new BaseComponent();
            fapiServer.registerComponent(baseComponent);
            
            BaseComponent retrieved = fapiServer.getComponent(BaseComponent.class);
            
            assertNotNull(retrieved);
            assertSame(baseComponent, retrieved);
        }
        
        @Test
        @DisplayName("获取所有组件应返回列表")
        void testGetComponents() {
            BaseComponent baseComponent = new BaseComponent();
            fapiServer.registerComponent(baseComponent);
            
            List<FapiComponent> components = fapiServer.getComponents();
            
            assertNotNull(components);
            assertEquals(1, components.size());
            assertEquals("BASE", components.get(0).getName());
        }
        
        @Test
        @DisplayName("获取不存在的组件应返回null")
        void testGetNonExistentComponent() {
            FapiComponent component = fapiServer.getComponent("NONEXISTENT");
            
            assertNull(component);
        }
        
        @Test
        @DisplayName("停止组件应清空组件列表")
        void testStopComponents() {
            BaseComponent baseComponent = new BaseComponent();
            fapiServer.registerComponent(baseComponent);
            
            fapiServer.stopComponents();
            
            assertTrue(fapiServer.getComponents().isEmpty());
        }
    }
    
    // ==================== 请求路由测试 ====================
    
    @Nested
    @DisplayName("请求路由测试")
    class RequestRoutingTests {
        
        @BeforeEach
        void registerComponents() {
            BaseComponent baseComponent = new BaseComponent();
            fapiServer.registerComponent(baseComponent);
        }
        
        @Test
        @DisplayName("有效请求应路由到正确组件")
        void testRouteValidRequest() {
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("base.health");
            
            FapiResponse response = fapiServer.routeRequest(request, "testPeer");
            
            assertNotNull(response);
            assertEquals(0, response.getCode());
        }
        
        @Test
        @DisplayName("无效API格式应返回错误")
        void testRouteInvalidApiFormat() {
            FapiRequest request = new FapiRequest();
            request.setId("req-002");
            request.setApi("invalidformat");
            
            FapiResponse response = fapiServer.routeRequest(request, "testPeer");
            
            assertEquals(FapiCode.BAD_REQUEST, response.getCode());
        }
        
        @Test
        @DisplayName("不存在的组件应返回NOT_FOUND")
        void testRouteToNonExistentComponent() {
            FapiRequest request = new FapiRequest();
            request.setId("req-003");
            request.setApi("disk.put");
            request.setParams(java.util.Map.of("content", "data"));
            
            FapiResponse response = fapiServer.routeRequest(request, "testPeer");
            
            assertEquals(FapiCode.NOT_FOUND, response.getCode());
            assertTrue(response.getMessage().contains("Component not found"));
        }
        
        @Test
        @DisplayName("响应应包含bestHeight")
        void testResponseContainsBestHeight() {
            FapiRequest request = new FapiRequest();
            request.setId("req-004");
            request.setApi("base.health");
            
            FapiResponse response = fapiServer.routeRequest(request, "testPeer");
            
            assertEquals(1000L, response.getBestHeight());
        }
    }
    
    // ==================== 安全与监控测试 ====================
    
    @Nested
    @DisplayName("安全与监控测试")
    class SecurityAndMonitoringTests {
        
        @BeforeEach
        void registerComponents() {
            BaseComponent baseComponent = new BaseComponent();
            fapiServer.registerComponent(baseComponent);
        }
        
        @Test
        @DisplayName("请求应记录到性能指标")
        void testMetricsRecording() {
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("base.health");
            
            // 执行多个请求
            fapiServer.routeRequest(request, "testPeer");
            fapiServer.routeRequest(request, "testPeer");
            fapiServer.routeRequest(request, "testPeer");
            
            MetricsReport report = fapiServer.getMetricsReport();
            
            assertEquals(3, report.getTotalRequests());
            assertEquals(3, report.getSuccessRequests());
        }
        
        @Test
        @DisplayName("失败请求应记录到性能指标")
        void testFailedRequestMetrics() {
            FapiRequest request = new FapiRequest();
            request.setId("req-002");
            request.setApi("nonexistent.method");
            
            fapiServer.routeRequest(request, "testPeer");
            
            MetricsReport report = fapiServer.getMetricsReport();
            
            assertEquals(1, report.getTotalRequests());
            assertEquals(1, report.getFailedRequests());
        }
        
        @Test
        @DisplayName("健康检查应返回正确状态")
        void testHealthCheck() {
            HealthStatus status = fapiServer.getHealthStatus();
            
            assertNotNull(status);
            assertTrue(status.getTimestamp() > 0);
            assertNotNull(status.getComponents());
        }
        
        @Test
        @DisplayName("快速健康检查应返回正确结果")
        void testIsHealthy() {
            // 注册了组件且FUDP运行中，应该健康
            boolean healthy = fapiServer.isHealthy();
            
            assertTrue(healthy);
        }
        
        @Test
        @DisplayName("审计日志器应可获取")
        void testAuditLogger() {
            assertNotNull(fapiServer.getAuditLogger());
        }
    }
    
    // ==================== 输入验证集成测试 ====================
    
    @Nested
    @DisplayName("输入验证集成测试")
    class InputValidationTests {
        
        @BeforeEach
        void registerComponents() {
            BaseComponent baseComponent = new BaseComponent();
            fapiServer.registerComponent(baseComponent);
        }
        
        @Test
        @DisplayName("null API应返回验证错误")
        void testNullApi() {
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi(null);
            
            FapiResponse response = fapiServer.routeRequest(request, "testPeer");
            
            assertEquals(FapiCode.BAD_REQUEST, response.getCode());
        }
        
        @Test
        @DisplayName("空API应返回验证错误")
        void testEmptyApi() {
            FapiRequest request = new FapiRequest();
            request.setId("req-002");
            request.setApi("");
            
            FapiResponse response = fapiServer.routeRequest(request, "testPeer");
            
            assertEquals(FapiCode.BAD_REQUEST, response.getCode());
        }
        
        @Test
        @DisplayName("查询API缺少fcdsl应返回错误")
        void testQueryApiWithoutFcdsl() {
            FapiRequest request = new FapiRequest();
            request.setId("req-003");
            request.setApi("base.search");
            
            FapiResponse response = fapiServer.routeRequest(request, "testPeer");
            
            assertEquals(FapiCode.BAD_REQUEST, response.getCode());
        }
    }
}

