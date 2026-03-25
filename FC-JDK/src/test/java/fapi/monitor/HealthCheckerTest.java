package fapi.monitor;

import config.Settings;
import fapi.FapiComponent;
import fapi.service.FapiServer;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * HealthChecker 单元测试
 */
@DisplayName("HealthChecker Tests")
class HealthCheckerTest {
    
    @Mock
    private FapiServer server;
    
    @Mock
    private Settings settings;
    
    @Mock
    private FudpNode fudpNode;
    
    @Mock
    private NodeConfig nodeConfig;
    
    @Mock
    private FapiComponent mockComponent;
    
    private HealthChecker healthChecker;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        when(server.getSettings()).thenReturn(settings);
        when(server.getFudpNode()).thenReturn(fudpNode);
        when(fudpNode.getConfig()).thenReturn(nodeConfig);
        when(nodeConfig.getPort()).thenReturn(8500);
        
        healthChecker = new HealthChecker(server);
    }
    
    // ==================== check() 测试 ====================
    
    @Nested
    @DisplayName("check() 方法测试")
    class CheckTests {
        
        @Test
        @DisplayName("所有组件健康时返回健康状态")
        void testAllComponentsHealthy() {
            when(mockComponent.getName()).thenReturn("BASE");
            when(mockComponent.getState()).thenReturn(FapiComponent.State.RUNNING);
            when(mockComponent.isHealthy()).thenReturn(true);
            when(mockComponent.getApiList()).thenReturn(List.of("base.search", "base.health"));
            when(server.getComponents()).thenReturn(List.of(mockComponent));
            when(fudpNode.isRunning()).thenReturn(true);
            when(fudpNode.getLocalFid()).thenReturn("FEk41xxxx");
            when(fudpNode.listPeers()).thenReturn(Collections.emptyList());
            
            HealthStatus status = healthChecker.check();
            
            assertNotNull(status);
            assertTrue(status.getTimestamp() > 0);
            assertEquals(1, status.getComponents().size());
            assertTrue(status.getComponents().get("BASE").isHealthy());
            assertEquals("RUNNING", status.getComponents().get("BASE").getState());
            assertEquals(2, status.getComponents().get("BASE").getApiCount());
            assertTrue(status.isFudpRunning());
        }
        
        @Test
        @DisplayName("FUDP节点未运行时状态反映这一点")
        void testFudpNodeNotRunning() {
            when(server.getComponents()).thenReturn(Collections.emptyList());
            when(fudpNode.isRunning()).thenReturn(false);
            
            HealthStatus status = healthChecker.check();
            
            assertFalse(status.isFudpRunning());
        }
        
        @Test
        @DisplayName("组件不健康时整体状态为不健康")
        void testUnhealthyComponent() {
            when(mockComponent.getName()).thenReturn("BASE");
            when(mockComponent.getState()).thenReturn(FapiComponent.State.STOPPED);
            when(mockComponent.isHealthy()).thenReturn(false);
            when(mockComponent.getApiList()).thenReturn(Collections.emptyList());
            when(server.getComponents()).thenReturn(List.of(mockComponent));
            when(fudpNode.isRunning()).thenReturn(true);
            when(fudpNode.getLocalFid()).thenReturn("FEk41xxxx");
            when(fudpNode.listPeers()).thenReturn(Collections.emptyList());
            
            HealthStatus status = healthChecker.check();
            
            assertFalse(status.isOverallHealthy());
            assertFalse(status.getComponents().get("BASE").isHealthy());
        }
        
        @Test
        @DisplayName("无FudpNode时状态正确")
        void testNoFudpNode() {
            when(server.getFudpNode()).thenReturn(null);
            when(server.getComponents()).thenReturn(Collections.emptyList());
            
            HealthStatus status = healthChecker.check();
            
            assertFalse(status.isFudpRunning());
            assertEquals(0, status.getConnectedPeers());
        }
        
        @Test
        @DisplayName("null server时状态正确")
        void testNullServer() {
            HealthChecker checkerWithNullServer = new HealthChecker(null);
            
            HealthStatus status = checkerWithNullServer.check();
            
            assertNotNull(status);
            assertFalse(status.isFudpRunning());
            assertTrue(status.getComponents().isEmpty());
        }
    }
    
    // ==================== isHealthy() 测试 ====================
    
    @Nested
    @DisplayName("isHealthy() 快速检查测试")
    class IsHealthyTests {
        
        @Test
        @DisplayName("所有条件满足时返回true")
        void testAllConditionsMet() {
            when(mockComponent.isHealthy()).thenReturn(true);
            when(server.getComponents()).thenReturn(List.of(mockComponent));
            when(fudpNode.isRunning()).thenReturn(true);
            
            boolean healthy = healthChecker.isHealthy();
            
            assertTrue(healthy);
        }
        
        @Test
        @DisplayName("FUDP未运行时返回false")
        void testFudpNotRunning() {
            when(fudpNode.isRunning()).thenReturn(false);
            
            boolean healthy = healthChecker.isHealthy();
            
            assertFalse(healthy);
        }
        
        @Test
        @DisplayName("组件不健康时返回false")
        void testComponentUnhealthy() {
            when(mockComponent.isHealthy()).thenReturn(false);
            when(server.getComponents()).thenReturn(List.of(mockComponent));
            when(fudpNode.isRunning()).thenReturn(true);
            
            boolean healthy = healthChecker.isHealthy();
            
            assertFalse(healthy);
        }
        
        @Test
        @DisplayName("null server时返回false")
        void testNullServer() {
            HealthChecker checkerWithNullServer = new HealthChecker(null);
            
            boolean healthy = checkerWithNullServer.isHealthy();
            
            assertFalse(healthy);
        }
        
        @Test
        @DisplayName("null fudpNode时返回false")
        void testNullFudpNode() {
            when(server.getFudpNode()).thenReturn(null);
            
            boolean healthy = healthChecker.isHealthy();
            
            assertFalse(healthy);
        }
    }
}

