package fapi.components;

import clients.ApipClient;
import config.Settings;
import data.feipData.Service;
import fapi.FapiBalanceManager;
import fapi.FapiCode;
import fapi.FapiComponent;
import fapi.message.FapiRequest;
import fapi.message.FapiResponse;
import fapi.message.UnifiedCodec.UnifiedResponse;
import fapi.service.FapiServer;
import fudp.Protocol;
import fudp.connection.ConnectionManager;
import fudp.node.FudpNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static constants.FieldNames.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RoadComponent 单元测试
 */
@DisplayName("RoadComponent Tests")
class RoadComponentTest {
    
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
    private MapComponent mockMapComponent;
    
    @Mock
    private FapiBalanceManager mockBalanceManager;
    
    @Mock
    private Service mockService;
    
    @TempDir
    Path tempDir;
    
    private RoadComponent component;
    
    private static final String TEST_PEER_ID = "FTestPeerIdABCDEFGHIJKLMNOPQR123";
    private static final String TARGET_PEER_ID = "FTargetPeerIdXYZ789012345678901";
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Set up mock chain
        when(mockServer.getSettings()).thenReturn(mockSettings);
        when(mockServer.getFudpNode()).thenReturn(mockFudpNode);
        when(mockServer.getComponent(MapComponent.class)).thenReturn(mockMapComponent);
        when(mockServer.getBalanceManager()).thenReturn(mockBalanceManager);
        when(mockSettings.getDbDir()).thenReturn(tempDir.toString());
        when(mockSettings.getService()).thenReturn(mockService);
        
        when(mockFudpNode.getProtocol()).thenReturn(mockProtocol);
        when(mockProtocol.getConnectionManager()).thenReturn(mockConnectionManager);
        
        // Default pricing
        when(mockService.getPricePerKBIn()).thenReturn("0.0000001");
        when(mockService.getPricePerKBOut()).thenReturn("0.0000001");
        
        // Default balance check - allow
        when(mockBalanceManager.canAfford(anyString(), anyLong())).thenReturn(true);
        
        component = new RoadComponent();
    }
    
    // ==================== Basic Properties Tests ====================
    
    @Nested
    @DisplayName("Basic Properties Tests")
    class BasicPropertiesTests {
        
        @Test
        @DisplayName("Component name should be ROAD")
        void testGetName() {
            assertEquals("ROAD", component.getName());
        }
        
        @Test
        @DisplayName("API list should contain expected APIs")
        void testGetApiList() {
            List<String> apis = component.getApiList();
            
            assertNotNull(apis);
            assertTrue(apis.contains("road.relay"));
            assertTrue(apis.contains("road.forward"));
            assertTrue(apis.contains("road.stats"));
        }
        
        @Test
        @DisplayName("Initial state should be CREATED")
        void testInitialState() {
            assertEquals(FapiComponent.State.CREATED, component.getState());
        }
    }
    
    // ==================== Initialization Tests ====================
    
    @Nested
    @DisplayName("Initialization Tests")
    class InitializationTests {
        
        @Test
        @DisplayName("Initialization should succeed with MAP component")
        void testInitializeSuccess() {
            component.initialize(mockServer);
            
            assertEquals(FapiComponent.State.RUNNING, component.getState());
            assertTrue(component.isHealthy());
        }
        
        @Test
        @DisplayName("Initialization should fail without MAP component")
        void testInitializeWithoutMap() {
            when(mockServer.getComponent(MapComponent.class)).thenReturn(null);
            
            // AbstractFapiComponent wraps the exception in RuntimeException
            RuntimeException ex = assertThrows(RuntimeException.class, () -> {
                component.initialize(mockServer);
            });
            assertTrue(ex.getCause() instanceof IllegalStateException);
            assertTrue(ex.getMessage().contains("ROAD"));
        }
    }
    
    // ==================== Stats API Tests ====================
    
    @Nested
    @DisplayName("Stats API Tests")
    class StatsApiTests {
        
        @BeforeEach
        void initComponent() {
            component.initialize(mockServer);
        }
        
        @Test
        @DisplayName("Stats should return correct information")
        void testStats() {
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("road.stats");
            
            FapiResponse response = component.handleRequest(request, TEST_PEER_ID);
            
            assertEquals(FapiCode.SUCCESS, response.getCode());
            assertNotNull(response.getData());
            
            @SuppressWarnings("unchecked")
            Map<String, Object> stats = (Map<String, Object>) response.getData();
            assertEquals(0L, ((Number) stats.get("totalRelays")).longValue());
            assertEquals(0L, ((Number) stats.get("successfulRelays")).longValue());
            assertEquals(0L, ((Number) stats.get("failedRelays")).longValue());
            assertNotNull(stats.get("pricePerKBIn"));
            assertNotNull(stats.get("pricePerKBOut"));
        }
    }
    
    // ==================== Relay API Tests ====================
    
    @Nested
    @DisplayName("Relay API Tests")
    class RelayApiTests {
        
        @BeforeEach
        void initComponent() {
            component.initialize(mockServer);
        }
        
        @Test
        @DisplayName("Relay via handleRequest should require unified protocol")
        void testRelayRequiresUnifiedProtocol() {
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("road.relay");
            
            FapiResponse response = component.handleRequest(request, TEST_PEER_ID);
            
            assertEquals(FapiCode.BAD_REQUEST, response.getCode());
            assertTrue(response.getMessage().contains("unified binary protocol"));
        }
        
        @Test
        @DisplayName("Relay with missing data should fail")
        void testRelayMissingData() {
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("road.relay");
            
            Map<String, Object> params = new HashMap<>();
            params.put(TARGET_FID, TARGET_PEER_ID);
            request.setParams(params);
            
            UnifiedResponse response = component.handleUnifiedRequest(request, null, TEST_PEER_ID);
            
            assertEquals(FapiCode.BAD_REQUEST, response.response().getCode());
            assertTrue(response.response().getMessage().contains("Data is required"));
        }
        
        @Test
        @DisplayName("Relay with missing targetFid should fail")
        void testRelayMissingTargetFid() {
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("road.relay");
            request.setParams(new HashMap<>());
            
            byte[] data = "test data".getBytes();
            
            UnifiedResponse response = component.handleUnifiedRequest(request, data, TEST_PEER_ID);
            
            assertEquals(FapiCode.BAD_REQUEST, response.response().getCode());
            assertTrue(response.response().getMessage().contains("targetFid is required"));
        }
        
        @Test
        @DisplayName("Relay should fail with NOT_FOUND when target not in any MAP")
        void testRelayTargetNotFound() {
            // Mock: target not found in local MAP
            when(mockMapComponent.getEntry(TARGET_PEER_ID)).thenReturn(null);
            
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("road.relay");
            
            Map<String, Object> params = new HashMap<>();
            params.put(TARGET_FID, TARGET_PEER_ID);
            request.setParams(params);
            
            byte[] data = "test data".getBytes();
            
            UnifiedResponse response = component.handleUnifiedRequest(request, data, TEST_PEER_ID);
            
            assertEquals(FapiCode.NOT_FOUND, response.response().getCode());
            
            // Verify ingress was charged
            verify(mockBalanceManager, atLeastOnce()).charge(anyString(), eq(TEST_PEER_ID), anyLong(), contains("in"));
        }
        
        @Test
        @DisplayName("Relay should fail with PAYMENT_REQUIRED when exceeding maxCost")
        void testRelayExceedsMaxCost() {
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("road.relay");
            
            Map<String, Object> params = new HashMap<>();
            params.put(TARGET_FID, TARGET_PEER_ID);
            params.put(MAX_COST, 1L);  // Very low max cost
            request.setParams(params);
            
            // 10KB of data should exceed 1 satoshi max cost
            byte[] data = new byte[10240];
            
            UnifiedResponse response = component.handleUnifiedRequest(request, data, TEST_PEER_ID);
            
            assertEquals(FapiCode.PAYMENT_REQUIRED, response.response().getCode());
            assertTrue(response.response().getMessage().contains("exceeds max"));
        }
        
        @Test
        @DisplayName("Relay should fail with PAYMENT_REQUIRED when insufficient balance")
        void testRelayInsufficientBalance() {
            when(mockBalanceManager.canAfford(anyString(), anyLong())).thenReturn(false);
            
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("road.relay");
            
            Map<String, Object> params = new HashMap<>();
            params.put(TARGET_FID, TARGET_PEER_ID);
            request.setParams(params);
            
            byte[] data = "test data".getBytes();
            
            UnifiedResponse response = component.handleUnifiedRequest(request, data, TEST_PEER_ID);
            
            assertEquals(FapiCode.PAYMENT_REQUIRED, response.response().getCode());
            assertTrue(response.response().getMessage().contains("Insufficient balance"));
        }
    }
    
    // ==================== Forward API Tests ====================
    
    @Nested
    @DisplayName("Forward API Tests")
    class ForwardApiTests {
        
        @BeforeEach
        void initComponent() {
            component.initialize(mockServer);
        }
        
        @Test
        @DisplayName("Forward should fail with max hops reached when target not found locally")
        void testForwardMaxHopsReached() {
            // Mock: target not found in local MAP
            when(mockMapComponent.getEntry(TARGET_PEER_ID)).thenReturn(null);
            
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("road.forward");  // This is a forwarded request
            
            Map<String, Object> params = new HashMap<>();
            params.put(TARGET_FID, TARGET_PEER_ID);
            params.put(ORIGIN_SID, "original-road-sid");
            request.setParams(params);
            
            byte[] data = "test data".getBytes();
            
            UnifiedResponse response = component.handleUnifiedRequest(request, data, TEST_PEER_ID);
            
            assertEquals(FapiCode.NOT_FOUND, response.response().getCode());
            assertTrue(response.response().getMessage().contains("max hops reached"));
        }
    }
    
    // ==================== Lifecycle Tests ====================
    
    @Nested
    @DisplayName("Lifecycle Tests")
    class LifecycleTests {
        
        @Test
        @DisplayName("Unknown method should return METHOD_NOT_ALLOWED")
        void testUnknownMethod() {
            component.initialize(mockServer);
            
            FapiRequest request = new FapiRequest();
            request.setId("req-001");
            request.setApi("road.unknownMethod");
            
            FapiResponse response = component.handleRequest(request, TEST_PEER_ID);
            
            assertEquals(FapiCode.METHOD_NOT_ALLOWED, response.getCode());
        }
        
        @Test
        @DisplayName("returnsBinaryData should return false for all methods")
        void testReturnsBinaryData() {
            assertFalse(component.returnsBinaryData("relay"));
            assertFalse(component.returnsBinaryData("forward"));
            assertFalse(component.returnsBinaryData("stats"));
        }
    }
    
    // ==================== Pricing Tests ====================
    
    @Nested
    @DisplayName("Pricing Tests")
    class PricingTests {
        
        @Test
        @DisplayName("Pricing should be loaded from service")
        void testPricingLoaded() {
            when(mockService.getPricePerKBIn()).thenReturn("0.0001");
            when(mockService.getPricePerKBOut()).thenReturn("0.0002");
            
            component.initialize(mockServer);
            
            // 0.0001 FCH = 10000 satoshi
            assertEquals(10000, component.getPricePerKBIn());
            // 0.0002 FCH = 20000 satoshi
            assertEquals(20000, component.getPricePerKBOut());
        }
        
        @Test
        @DisplayName("Default pricing should be used when service has no pricing")
        void testDefaultPricing() {
            when(mockService.getPricePerKBIn()).thenReturn(null);
            when(mockService.getPricePerKBOut()).thenReturn(null);
            when(mockService.getPricePerKB()).thenReturn(null);
            
            component.initialize(mockServer);
            
            // Default: 10 satoshi per KB
            assertEquals(10, component.getPricePerKBIn());
            assertEquals(10, component.getPricePerKBOut());
        }
    }
}
