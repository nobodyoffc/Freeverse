package fapi.service;

import config.Configure;
import config.Settings;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import data.feipData.Service;
import fudp.message.ResponseMessage;
import fudp.node.FudpNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FapiService 单元测试
 */
@DisplayName("FapiServer Tests")
class FapiServerTest {
    
    @Mock
    private Settings settings;
    
    @Mock
    private ElasticsearchClient esClient;
    
    @Mock
    private Configure configure;
    
    @Mock
    private FudpNode fudpNode;
    
    private MockedStatic<Configure> configureMock;
    private FapiServer fapiServer;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(settings.getClient(Service.ServiceType.ES)).thenReturn(esClient);
        when(settings.getMainFid()).thenReturn("test-fid");
        when(settings.getConfig()).thenReturn(configure);
        when(settings.getBestHeight()).thenReturn(1000L);
        
        configureMock = mockStatic(Configure.class);
        configureMock.when(() -> Configure.getServiceListByDealerFromEs(anyString(), any()))
                .thenReturn(Collections.emptyList());

        fapiServer = new FapiServer(settings);
        fapiServer.setFudpNode(fudpNode);
    }

    @AfterEach
    void tearDown() {
        if (configureMock != null) {
            configureMock.close();
        }
    }
    
    @Test
    @DisplayName("测试服务初始化 - 成功加载服务")
    void testInitializeSuccess() {
        // Mock Configure.getServiceListByOwnerAndTypeFromEs
        List<Service> mockServices = new ArrayList<>();
        Service service1 = new Service();
        service1.setId("service-id-1");
        service1.setTypes(new String[]{"FAPI"});
        service1.setStdName("Test FAPI Service");
        mockServices.add(service1);
        
        configureMock.when(() -> Configure.getServiceListByDealerFromEs(
                eq("test-fid"),
                eq(esClient)
        )).thenReturn(mockServices);
        
        fapiServer.initialize();
        
        Map<String, Service> serviceMap = fapiServer.getServiceMap();
        assertEquals(1, serviceMap.size());
        assertTrue(serviceMap.containsKey("service-id-1"));
    }
    
    @Test
    @DisplayName("测试服务初始化 - 过滤非 FAPI 服务")
    void testInitializeFiltersNonFapiServices() {
        // Mock Configure.getServiceListByOwnerAndTypeFromEs
        List<Service> mockServices = new ArrayList<>();
        
        // FAPI 服务
        Service fapiService1 = new Service();
        fapiService1.setId("fapi-service-1");
        fapiService1.setTypes(new String[]{"FAPI"});
        mockServices.add(fapiService1);
        
        // 非 FAPI 服务（应该被过滤）
        Service nonFapiService = new Service();
        nonFapiService.setId("non-fapi-service");
        nonFapiService.setTypes(new String[]{"DISK"});
        mockServices.add(nonFapiService);
        
        // 包含 FAPI 但还有其他类型的服务
        Service mixedService = new Service();
        mixedService.setId("mixed-service");
        mixedService.setTypes(new String[]{"FAPI", "DISK"});
        mockServices.add(mixedService);
        
        configureMock.when(() -> Configure.getServiceListByDealerFromEs(
                eq("test-fid"),
                eq(esClient)
        )).thenReturn(mockServices);
        
        fapiServer.initialize();
        
        Map<String, Service> serviceMap = fapiServer.getServiceMap();
        // 应该包含 fapi-service-1 和 mixed-service，但不包含 non-fapi-service
        assertEquals(2, serviceMap.size());
        assertTrue(serviceMap.containsKey("fapi-service-1"));
        assertTrue(serviceMap.containsKey("mixed-service"));
        assertFalse(serviceMap.containsKey("non-fapi-service"));
    }
    
    @Test
    @DisplayName("测试服务更新")
    void testUpdateServices() {
        // 先初始化一些服务
        List<Service> mockServices = new ArrayList<>();
        Service service1 = new Service();
        service1.setId("service-id-1");
        service1.setTypes(new String[]{"FAPI"});
        mockServices.add(service1);
        
        configureMock.when(() -> Configure.getServiceListByDealerFromEs(anyString(), any()))
                .thenReturn(mockServices);
        
        fapiServer.initialize();
        assertEquals(1, fapiServer.getServiceMap().size());
        
        // 更新服务（清空并重新加载）
        fapiServer.updateServices();
        
        // 验证服务映射已重新加载
        Map<String, Service> serviceMap = fapiServer.getServiceMap();
        assertNotNull(serviceMap);
        // 由于 mock 返回相同的服务，应该还是 1 个
        assertEquals(1, serviceMap.size());
    }
    
    @Test
    @DisplayName("测试请求处理 - 服务不存在")
    void testOnRequestReceivedServiceNotFound() {
        String peerId = "test-peer-id";
        long requestId = 12345L;
        String serviceName = "non-existent-service";
        byte[] data = "{\"entity\":\"block\"}".getBytes();
        
        fapiServer.onRequestReceived(peerId, requestId, serviceName, data);
        
        // 验证发送了错误响应
        try {
            verify(fudpNode, times(1)).respond(
                eq(peerId),
                eq(requestId),
                eq(ResponseMessage.STATUS_NOT_FOUND),
                any(byte[].class)
            );
        } catch (Exception e) {
            // 忽略异常
        }
    }
    
    @Test
    @DisplayName("测试请求处理 - 服务类型不匹配")
    void testOnRequestReceivedServiceTypeMismatch() {
        // 先添加一个非 FAPI 服务
        Service nonFapiService = new Service();
        nonFapiService.setId("non-fapi-service");
        nonFapiService.setTypes(new String[]{"DISK"});
        
        fapiServer.getServiceMap().put("non-fapi-service", nonFapiService);
        
        String peerId = "test-peer-id";
        long requestId = 12345L;
        String serviceName = "non-fapi-service";
        byte[] data = "{\"entity\":\"block\"}".getBytes();
        
        fapiServer.onRequestReceived(peerId, requestId, serviceName, data);
        
        // 验证发送了错误响应
        try {
            verify(fudpNode, times(1)).respond(
                eq(peerId),
                eq(requestId),
                eq(ResponseMessage.STATUS_NOT_FOUND),
                any(byte[].class)
            );
        } catch (Exception e) {
            // 忽略异常
        }
    }
    
    @Test
    @DisplayName("测试请求处理 - 服务类型为 null")
    void testOnRequestReceivedServiceTypesNull() {
        // 添加一个 types 为 null 的服务
        Service serviceWithNullTypes = new Service();
        serviceWithNullTypes.setId("service-null-types");
        serviceWithNullTypes.setTypes(null);
        
        fapiServer.getServiceMap().put("service-null-types", serviceWithNullTypes);
        
        String peerId = "test-peer-id";
        long requestId = 12345L;
        String serviceName = "service-null-types";
        byte[] data = "{\"entity\":\"block\"}".getBytes();
        
        fapiServer.onRequestReceived(peerId, requestId, serviceName, data);
        
        // 验证发送了错误响应
        try {
            verify(fudpNode, times(1)).respond(
                eq(peerId),
                eq(requestId),
                eq(ResponseMessage.STATUS_NOT_FOUND),
                any(byte[].class)
            );
        } catch (Exception e) {
            // 忽略异常
        }
    }
    
    @Test
    @DisplayName("测试请求处理 - FudpNode 为 null")
    void testOnRequestReceivedFudpNodeNull() {
        // 添加一个有效的 FAPI 服务
        Service fapiService = new Service();
        fapiService.setId("fapi-service-1");
        fapiService.setTypes(new String[]{"FAPI"});
        
        this.fapiServer.getServiceMap().put("fapi-service-1", fapiService);
        this.fapiServer.setFudpNode(null); // 设置为 null
        
        String peerId = "test-peer-id";
        long requestId = 12345L;
        String serviceName = "fapi-service-1";
        byte[] data = "{\"entity\":\"block\"}".getBytes();
        
        // 应该不会抛出异常，但也不会发送响应
        assertDoesNotThrow(() -> {
            this.fapiServer.onRequestReceived(peerId, requestId, serviceName, data);
        });
    }
    
    @Test
    @DisplayName("测试获取服务映射")
    void testGetServiceMap() {
        Map<String, Service> serviceMap = fapiServer.getServiceMap();
        assertNotNull(serviceMap);
        assertTrue(serviceMap.isEmpty()); // 初始为空
        
        // 手动添加服务
        Service service = new Service();
        service.setId("test-service");
        service.setTypes(new String[]{"FAPI"});
        serviceMap.put("test-service", service);
        
        assertEquals(1, fapiServer.getServiceMap().size());
        assertTrue(fapiServer.getServiceMap().containsKey("test-service"));
    }
}
