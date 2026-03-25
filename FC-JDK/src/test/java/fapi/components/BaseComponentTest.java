package fapi.components;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import clients.NaSaClient.NaSaRpcClient;
import config.Settings;
import data.apipData.Fcdsl;
import data.feipData.ServiceType;
import fapi.FapiCode;
import fapi.FapiComponent;
import fapi.message.FapiRequest;
import fapi.message.FapiResponse;
import fapi.monitor.HealthStatus;
import fapi.service.FapiServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * BaseComponent 单元测试
 */
@DisplayName("BaseComponent Tests")
class BaseComponentTest {
    
    @Mock
    private FapiServer mockServer;
    
    @Mock
    private Settings mockSettings;
    
    @Mock
    private ElasticsearchClient mockEsClient;
    
    @Mock
    private NaSaRpcClient mockRpcClient;
    
    private BaseComponent component;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        when(mockServer.getSettings()).thenReturn(mockSettings);
        when(mockSettings.getClient(ServiceType.ES)).thenReturn(mockEsClient);
        when(mockSettings.getClient(ServiceType.NASA_RPC)).thenReturn(mockRpcClient);
        
        component = new BaseComponent();
    }
    
    // ==================== 基本属性测试 ====================
    
    @Nested
    @DisplayName("基本属性测试")
    class BasicPropertiesTests {
        
        @Test
        @DisplayName("组件名称应为BASE")
        void testGetName() {
            assertEquals("BASE", component.getName());
        }
        
        @Test
        @DisplayName("API列表应包含所有预期的API")
        void testGetApiList() {
            List<String> apis = component.getApiList();
            
            assertNotNull(apis);
            assertTrue(apis.contains("base.getByIds"));
            assertTrue(apis.contains("base.search"));
            assertTrue(apis.contains("base.totals"));
            assertTrue(apis.contains("base.health"));
            assertTrue(apis.contains("base.balanceByIds"));
            assertTrue(apis.contains("base.cashValid"));
            assertTrue(apis.contains("base.getUtxo"));
            assertTrue(apis.contains("base.chainInfo"));
            assertTrue(apis.contains("base.broadcastTx"));
            assertTrue(apis.contains("base.decodeTx"));
            assertTrue(apis.contains("base.estimateFee"));
        }
        
        @Test
        @DisplayName("初始状态应为CREATED")
        void testInitialState() {
            assertEquals(FapiComponent.State.CREATED, component.getState());
        }
    }
    
    // ==================== 初始化测试 ====================
    
    @Nested
    @DisplayName("初始化测试")
    class InitializationTests {
        
        @Test
        @DisplayName("初始化成功后状态应为RUNNING")
        void testInitializeSuccess() {
            component.initialize(mockServer);
            
            assertEquals(FapiComponent.State.RUNNING, component.getState());
            assertTrue(component.isHealthy());
        }
        
        @Test
        @DisplayName("缺少ES客户端应抛出异常")
        void testInitializeWithoutEsClient() {
            when(mockSettings.getClient(ServiceType.ES)).thenReturn(null);
            
            assertThrows(IllegalStateException.class, () -> {
                component.initialize(mockServer);
            });
        }
    }
    
    // ==================== handleRequest 路由测试 ====================
    
    @Nested
    @DisplayName("handleRequest 路由测试")
    class HandleRequestRoutingTests {
        
        @BeforeEach
        void initComponent() {
            component.initialize(mockServer);
        }
        
        @Test
        @DisplayName("health API应返回健康状态")
        void testHealthApi() {
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("base.health");
            
            // Mock health status
            HealthStatus mockStatus = new HealthStatus();
            mockStatus.setOverallHealthy(true);
            when(mockServer.getHealthStatus()).thenReturn(mockStatus);
            
            FapiResponse response = component.handleRequest(request, "testPeer");
            
            assertNotNull(response);
            assertEquals(0, response.getCode());
            assertEquals("req-001", response.getRequestId());
        }
        
        @Test
        @DisplayName("未知方法应返回METHOD_NOT_ALLOWED")
        void testUnknownMethod() {
            FapiRequest request = new FapiRequest();
            request.setId("req-002");
            request.setApi("base.unknownMethod");
            
            FapiResponse response = component.handleRequest(request, "testPeer");
            
            assertEquals(FapiCode.METHOD_NOT_ALLOWED, response.getCode());
            assertTrue(response.getMessage().contains("unknownMethod"));
        }
        
        @Test
        @DisplayName("getByIds缺少fcdsl应返回错误")
        void testGetByIdsWithoutFcdsl() {
            FapiRequest request = new FapiRequest();
            request.setId("req-003");
            request.setApi("base.getByIds");
            
            FapiResponse response = component.handleRequest(request, "testPeer");
            
            assertEquals(FapiCode.BAD_REQUEST, response.getCode());
        }
        
        @Test
        @DisplayName("getByIds缺少entity应返回错误")
        void testGetByIdsWithoutEntity() {
            FapiRequest request = new FapiRequest();
            request.setId("req-004");
            request.setApi("base.getByIds");
            Fcdsl fcdsl = new Fcdsl();
            fcdsl.setIds(List.of("id1", "id2"));
            request.setFcdsl(fcdsl);
            
            FapiResponse response = component.handleRequest(request, "testPeer");
            
            assertEquals(FapiCode.BAD_REQUEST, response.getCode());
            assertTrue(response.getMessage().contains("entity"));
        }
        
        @Test
        @DisplayName("balanceByIds缺少FID列表应返回错误")
        void testBalanceByIdsWithoutFids() {
            FapiRequest request = new FapiRequest();
            request.setId("req-005");
            request.setApi("base.balanceByIds");
            Fcdsl fcdsl = new Fcdsl();
            request.setFcdsl(fcdsl);
            
            FapiResponse response = component.handleRequest(request, "testPeer");
            
            assertEquals(FapiCode.BAD_REQUEST, response.getCode());
        }
        
        @Test
        @DisplayName("broadcastTx缺少params应返回错误")
        void testBroadcastTxWithoutParams() {
            FapiRequest request = new FapiRequest();
            request.setId("req-006");
            request.setApi("base.broadcastTx");
            
            FapiResponse response = component.handleRequest(request, "testPeer");
            
            assertEquals(FapiCode.BAD_REQUEST, response.getCode());
        }
        
        @Test
        @DisplayName("broadcastTx缺少rawTx应返回错误")
        void testBroadcastTxWithoutRawTx() {
            FapiRequest request = new FapiRequest();
            request.setId("req-007");
            request.setApi("base.broadcastTx");
            request.setParams(Map.of("other", "value"));
            
            FapiResponse response = component.handleRequest(request, "testPeer");
            
            assertEquals(FapiCode.BAD_REQUEST, response.getCode());
            assertTrue(response.getMessage().contains("rawTx"));
        }
        
        @Test
        @DisplayName("decodeTx缺少rawTx应返回错误")
        void testDecodeTxWithoutRawTx() {
            FapiRequest request = new FapiRequest();
            request.setId("req-008");
            request.setApi("base.decodeTx");
            request.setParams(Map.of());
            
            FapiResponse response = component.handleRequest(request, "testPeer");
            
            assertEquals(FapiCode.BAD_REQUEST, response.getCode());
        }
    }
    
    // ==================== 生命周期测试 ====================
    
    @Nested
    @DisplayName("生命周期测试")
    class LifecycleTests {
        
        @Test
        @DisplayName("关闭组件后状态应为STOPPED")
        void testClose() throws InterruptedException {
            component.initialize(mockServer);
            
            component.close(5000);
            
            assertEquals(FapiComponent.State.STOPPED, component.getState());
            assertFalse(component.isHealthy());
        }
        
        @Test
        @DisplayName("默认不支持异步")
        void testSupportsAsync() {
            assertFalse(component.supportsAsync());
        }
    }
}

