package fapi.components;

import config.Settings;
import data.apipData.Fcdsl;
import fapi.FapiCode;
import fapi.FapiComponent;
import fapi.components.map.MapEntry;
import fapi.message.FapiRequest;
import fapi.message.FapiResponse;
import fapi.service.FapiServer;
import fudp.Protocol;
import fudp.connection.ConnectionManager;
import fudp.connection.PeerConnection;
import fudp.node.FudpNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MapComponent 单元测试
 */
@DisplayName("MapComponent Tests")
class MapComponentTest {
    
    @Mock
    private FapiServer mockServer;
    
    @Mock
    private Settings mockSettings;
    
    @Mock
    private FudpNode mockFudpNode;
    
    @Mock
    private Protocol mockProtocol;
    
    @Mock
    private ConnectionManager mockConnectionManager;
    
    @Mock
    private PeerConnection mockConnection;
    
    @TempDir
    Path tempDir;
    
    private MapComponent component;
    
    private static final String TEST_PEER_ID = "FTestPeerIdABCDEFGHIJKLMNOPQR123";
    private static final byte[] TEST_PUBKEY = new byte[33];
    private static final String TEST_IP = "192.168.1.100";
    private static final int TEST_PORT = 8500;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 设置模拟对象链
        when(mockServer.getSettings()).thenReturn(mockSettings);
        when(mockServer.getFudpNode()).thenReturn(mockFudpNode);
        when(mockSettings.getDbDir()).thenReturn(tempDir.toString());
        
        when(mockFudpNode.getProtocol()).thenReturn(mockProtocol);
        when(mockProtocol.getConnectionManager()).thenReturn(mockConnectionManager);
        when(mockConnectionManager.getByPeerId(TEST_PEER_ID)).thenReturn(mockConnection);
        when(mockConnection.getPeerAddress()).thenReturn(new InetSocketAddress(TEST_IP, TEST_PORT));
        when(mockConnection.getPeerPublicKey()).thenReturn(TEST_PUBKEY);
        
        component = new MapComponent();
    }
    
    // ==================== 基本属性测试 ====================
    
    @Nested
    @DisplayName("基本属性测试")
    class BasicPropertiesTests {
        
        @Test
        @DisplayName("组件名称应为MAP")
        void testGetName() {
            assertEquals("MAP", component.getName());
        }
        
        @Test
        @DisplayName("API列表应包含所有预期的API")
        void testGetApiList() {
            List<String> apis = component.getApiList();
            
            assertNotNull(apis);
            assertTrue(apis.contains("map.register"));
            assertTrue(apis.contains("map.find"));
            assertTrue(apis.contains("map.unregister"));
            assertTrue(apis.contains("map.list"));
            assertTrue(apis.contains("map.stats"));
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
        @DisplayName("初始化后条目数量应为0")
        void testInitializeEmptyEntries() {
            component.initialize(mockServer);
            
            assertEquals(0, component.getEntryCount());
        }
    }
    
    // ==================== Register API测试 ====================
    
    @Nested
    @DisplayName("Register API测试")
    class RegisterApiTests {
        
        @BeforeEach
        void initComponent() {
            component.initialize(mockServer);
        }
        
        @Test
        @DisplayName("注册应成功并返回条目信息")
        void testRegisterSuccess() {
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("map.register");
            
            FapiResponse response = component.handleRequest(request, TEST_PEER_ID);
            
            assertEquals(FapiCode.SUCCESS, response.getCode());
            assertEquals("req-001", response.getRequestId());
            assertNotNull(response.getData());
            
            // 验证条目已存储
            assertEquals(1, component.getEntryCount());
            MapEntry entry = component.getEntry(TEST_PEER_ID);
            assertNotNull(entry);
            assertEquals(TEST_PEER_ID, entry.getFid());
            assertEquals(TEST_IP, entry.getObservedIp());
            assertEquals(TEST_PORT, entry.getObservedPort());
        }
        
        @Test
        @DisplayName("重复注册应覆盖旧条目")
        void testRegisterOverwrite() {
            // 第一次注册
            FapiRequest request1 = new FapiRequest();
            request1.setId("req-001");
            request1.setApi("map.register");
            component.handleRequest(request1, TEST_PEER_ID);
            
            MapEntry entry1 = component.getEntry(TEST_PEER_ID);
            long registeredAt = entry1.getRegisteredAt();
            
            // 模拟地址变化
            when(mockConnection.getPeerAddress()).thenReturn(new InetSocketAddress("10.0.0.1", 9000));
            
            // 延迟确保时间戳不同
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
            
            // 第二次注册
            FapiRequest request2 = new FapiRequest();
            request2.setId("req-002");
            request2.setApi("map.register");
            component.handleRequest(request2, TEST_PEER_ID);
            
            // 应该只有一个条目
            assertEquals(1, component.getEntryCount());
            
            MapEntry entry2 = component.getEntry(TEST_PEER_ID);
            assertEquals("10.0.0.1", entry2.getObservedIp());
            assertEquals(9000, entry2.getObservedPort());
            // 首次注册时间应保留
            assertEquals(registeredAt, entry2.getRegisteredAt());
            // lastSeen应更新
            assertTrue(entry2.getLastSeen() > entry1.getLastSeen());
        }
        
        @Test
        @DisplayName("无连接应返回错误")
        void testRegisterNoConnection() {
            when(mockConnectionManager.getByPeerId(TEST_PEER_ID)).thenReturn(null);
            
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("map.register");
            
            FapiResponse response = component.handleRequest(request, TEST_PEER_ID);
            
            assertEquals(FapiCode.INTERNAL_ERROR, response.getCode());
            assertTrue(response.getMessage().contains("Connection not found"));
        }
    }
    
    // ==================== Find API测试 ====================
    
    @Nested
    @DisplayName("Find API测试")
    class FindApiTests {
        
        @BeforeEach
        void initComponent() {
            component.initialize(mockServer);
            
            // 先注册一个条目
            FapiRequest regRequest = new FapiRequest();
            regRequest.setId("reg-001");
            regRequest.setApi("map.register");
            component.handleRequest(regRequest, TEST_PEER_ID);
        }
        
        @Test
        @DisplayName("查找已注册FID应成功")
        void testFindSuccess() {
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("map.find");
            Fcdsl fcdsl = new Fcdsl();
            fcdsl.setIds(List.of(TEST_PEER_ID));
            request.setFcdsl(fcdsl);
            
            FapiResponse response = component.handleRequest(request, "otherPeer");
            
            assertEquals(FapiCode.SUCCESS, response.getCode());
            assertNotNull(response.getData());
        }
        
        @Test
        @DisplayName("查找未注册FID应返回NOT_FOUND")
        void testFindNotFound() {
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("map.find");
            Fcdsl fcdsl = new Fcdsl();
            fcdsl.setIds(List.of("FNonExistentPeerIdXYZ123456789"));
            request.setFcdsl(fcdsl);
            
            FapiResponse response = component.handleRequest(request, "otherPeer");
            
            assertEquals(FapiCode.NOT_FOUND, response.getCode());
        }
        
        @Test
        @DisplayName("使用params.fid查找应成功")
        void testFindWithParams() {
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("map.find");
            request.setParams(Map.of("fid", TEST_PEER_ID));
            
            FapiResponse response = component.handleRequest(request, "otherPeer");
            
            assertEquals(FapiCode.SUCCESS, response.getCode());
        }
        
        @Test
        @DisplayName("缺少目标FID应返回BAD_REQUEST")
        void testFindNoTarget() {
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("map.find");
            
            FapiResponse response = component.handleRequest(request, "otherPeer");
            
            assertEquals(FapiCode.BAD_REQUEST, response.getCode());
            assertTrue(response.getMessage().contains("Target FID"));
        }
    }
    
    // ==================== Unregister API测试 ====================
    
    @Nested
    @DisplayName("Unregister API测试")
    class UnregisterApiTests {
        
        @BeforeEach
        void initComponent() {
            component.initialize(mockServer);
            
            // 先注册一个条目
            FapiRequest regRequest = new FapiRequest();
            regRequest.setId("reg-001");
            regRequest.setApi("map.register");
            component.handleRequest(regRequest, TEST_PEER_ID);
        }
        
        @Test
        @DisplayName("注销已注册FID应成功")
        void testUnregisterSuccess() {
            assertEquals(1, component.getEntryCount());
            
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("map.unregister");
            
            FapiResponse response = component.handleRequest(request, TEST_PEER_ID);
            
            assertEquals(FapiCode.SUCCESS, response.getCode());
            assertEquals(0, component.getEntryCount());
        }
        
        @Test
        @DisplayName("注销未注册FID应返回NOT_FOUND")
        void testUnregisterNotFound() {
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("map.unregister");
            
            FapiResponse response = component.handleRequest(request, "otherPeer");
            
            assertEquals(FapiCode.NOT_FOUND, response.getCode());
        }
    }
    
    // ==================== List API测试 ====================
    
    @Nested
    @DisplayName("List API测试")
    class ListApiTests {
        
        @BeforeEach
        void initComponent() {
            component.initialize(mockServer);
        }
        
        @Test
        @DisplayName("空列表应返回空数组")
        void testListEmpty() {
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("map.list");
            
            FapiResponse response = component.handleRequest(request, TEST_PEER_ID);
            
            assertEquals(FapiCode.SUCCESS, response.getCode());
            assertEquals(0L, response.getGot());
            assertEquals(0L, response.getTotal());
        }
        
        @Test
        @DisplayName("有条目时应返回所有条目")
        void testListWithEntries() {
            // 注册一个条目
            FapiRequest regRequest = new FapiRequest();
            regRequest.setId("reg-001");
            regRequest.setApi("map.register");
            component.handleRequest(regRequest, TEST_PEER_ID);
            
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("map.list");
            
            FapiResponse response = component.handleRequest(request, TEST_PEER_ID);
            
            assertEquals(FapiCode.SUCCESS, response.getCode());
            assertEquals(1L, response.getGot());
            assertEquals(1L, response.getTotal());
        }
    }
    
    // ==================== Stats API测试 ====================
    
    @Nested
    @DisplayName("Stats API测试")
    class StatsApiTests {
        
        @BeforeEach
        void initComponent() {
            component.initialize(mockServer);
        }
        
        @Test
        @DisplayName("统计应返回正确信息")
        void testStats() {
            // 注册一个条目
            FapiRequest regRequest = new FapiRequest();
            regRequest.setId("reg-001");
            regRequest.setApi("map.register");
            component.handleRequest(regRequest, TEST_PEER_ID);
            
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("map.stats");
            
            FapiResponse response = component.handleRequest(request, TEST_PEER_ID);
            
            assertEquals(FapiCode.SUCCESS, response.getCode());
            assertNotNull(response.getData());
            
            @SuppressWarnings("unchecked")
            Map<String, Object> stats = (Map<String, Object>) response.getData();
            assertEquals(1, ((Number) stats.get("totalEntries")).intValue());
            assertEquals(1, ((Number) stats.get("freshEntries")).intValue());
            assertEquals(0, ((Number) stats.get("staleEntries")).intValue());
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
        @DisplayName("未知方法应返回METHOD_NOT_ALLOWED")
        void testUnknownMethod() {
            component.initialize(mockServer);
            
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("map.unknownMethod");
            
            FapiResponse response = component.handleRequest(request, TEST_PEER_ID);
            
            assertEquals(FapiCode.METHOD_NOT_ALLOWED, response.getCode());
        }
    }
    
    // ==================== MapEntry测试 ====================
    
    @Nested
    @DisplayName("MapEntry测试")
    class MapEntryTests {
        
        @Test
        @DisplayName("isStale应正确判断过期")
        void testIsStale() {
            MapEntry entry = new MapEntry("fid", "pubkey", "ip", 8500);
            
            // 刚创建的条目不应过期
            assertFalse(entry.isStale(1000));
            
            // 设置过去的lastSeen
            entry.setLastSeen(System.currentTimeMillis() - 2000);
            
            // 现在应该过期
            assertTrue(entry.isStale(1000));
        }
        
        @Test
        @DisplayName("shouldCleanup应正确判断是否需要清理")
        void testShouldCleanup() {
            MapEntry entry = new MapEntry("fid", "pubkey", "ip", 8500);
            
            // 刚创建的条目不应清理
            assertFalse(entry.shouldCleanup(1000));
            
            // 设置很久以前的lastSeen
            entry.setLastSeen(System.currentTimeMillis() - 100000);
            
            // 现在应该清理
            assertTrue(entry.shouldCleanup(1000));
        }
    }
}
