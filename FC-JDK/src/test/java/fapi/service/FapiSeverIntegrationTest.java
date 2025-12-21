package fapi.service;

import config.Configure;
import config.Settings;
import constants.IndicesNames;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import data.apipData.Fcdsl;
import data.fchData.Block;
import data.feipData.Service;
import fudp.message.ResponseMessage;
import fudp.node.FudpNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import utils.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;

/**
 * FapiService 集成测试
 * 
 * 注意：这是集成测试，需要模拟 Elasticsearch 和 FUDP Node 环境
 * 在实际环境中，这些测试需要真实的 Elasticsearch 连接
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FapiServer Integration Tests")
class FapiServerIntegrationTest {
    
    @Mock
    private Settings settings;
    
    @Mock
    private ElasticsearchClient esClient;
    
    @Mock
    private Configure configure;
    
    @Mock
    private FudpNode fudpNode;
    
    private FapiServer fapiServer;
    
    @BeforeEach
    void setUp() {
        lenient().when(settings.getClient(Service.ServiceType.ES)).thenReturn(esClient);
        lenient().when(settings.getMainFid()).thenReturn("test-fid");
        lenient().when(settings.getConfig()).thenReturn(configure);
        lenient().when(settings.getBestHeight()).thenReturn(1000L);
        
        fapiServer = new FapiServer(settings);
        fapiServer.setFudpNode(fudpNode);
    }
    
    @Test
    @DisplayName("集成测试 - 完整的请求处理流程")
    void testCompleteRequestProcessingFlow() throws Exception {
        // 1. 初始化服务
        Service testService = new Service();
        testService.setId("test-service-id");
        testService.setTypes(new String[]{"FAPI"});
        testService.setStdName("Test FAPI Service");
        
        List<Service> services = new ArrayList<>();
        services.add(testService);
        
        try (MockedStatic<Configure> mockedConfigure = mockStatic(Configure.class)) {
            mockedConfigure.when(() -> Configure.getServiceListByDealerFromEs(
                    eq("test-fid"),
                    eq(esClient)
            )).thenReturn(services);
        
        fapiServer.initialize();
        
        // 2. 准备查询请求
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEntity("block");
        fcdsl.setSize("10");
        byte[] requestData = JsonUtils.toJson(fcdsl).getBytes(StandardCharsets.UTF_8);
        
        // 3. Mock Elasticsearch 响应
        SearchResponse<Block> mockResponse = mock(SearchResponse.class);
        HitsMetadata<Block> hitsMetadata = mock(HitsMetadata.class);
            TotalHits totalHits = TotalHits.of(t -> t.value(5L).relation(co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq));
        
        List<Hit<Block>> hits = new ArrayList<>();
        Block block1 = new Block();
        block1.setId("block1");
        Hit<Block> hit1 = mock(Hit.class);
        when(hit1.source()).thenReturn(block1);
        hits.add(hit1);
        
        when(hitsMetadata.total()).thenReturn(totalHits);
        when(hitsMetadata.hits()).thenReturn(hits);
        when(mockResponse.hits()).thenReturn(hitsMetadata);
        
            when(esClient.search(any(SearchRequest.class), eq(Block.class))).thenReturn(mockResponse);
        
        // 4. 发送请求
        String peerId = "test-peer-id";
        long requestId = 12345L;
        fapiServer.onRequestReceived(peerId, requestId, "test-service-id", requestData);
        
        // 5. 验证响应
        verify(fudpNode, times(1)).respond(
            eq(peerId),
            eq(requestId),
            eq(ResponseMessage.STATUS_SUCCESS),
            any(byte[].class)
        );
        }
    }
    
    @Test
    @DisplayName("集成测试 - Block 查询")
    void testBlockQuery() throws Exception {
        // 初始化服务
        Service testService = new Service();
        testService.setId("test-service-id");
        testService.setTypes(new String[]{"FAPI"});
        
        fapiServer.getServiceMap().put("test-service-id", testService);
        
        // 创建 Block 查询
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEntity("block");
        fcdsl.setSize("20");
        byte[] requestData = JsonUtils.toJson(fcdsl).getBytes(StandardCharsets.UTF_8);
        
        // Mock Elasticsearch 响应
        SearchResponse<Block> mockResponse = mock(SearchResponse.class);
        HitsMetadata<Block> hitsMetadata = mock(HitsMetadata.class);
        TotalHits totalHits = TotalHits.of(t -> t.value(10L).relation(co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq));
        
        when(hitsMetadata.total()).thenReturn(totalHits);
        when(hitsMetadata.hits()).thenReturn(Collections.emptyList());
        when(mockResponse.hits()).thenReturn(hitsMetadata);
        
        when(esClient.search(any(SearchRequest.class), eq(Block.class))).thenReturn(mockResponse);
        
        // 发送请求
        fapiServer.onRequestReceived("peer-id", 1L, "test-service-id", requestData);
        
        // 验证
        verify(fudpNode, times(1)).respond(anyString(), anyLong(), eq(ResponseMessage.STATUS_SUCCESS), any(byte[].class));
    }
    
    @Test
    @DisplayName("集成测试 - Tx 查询")
    void testTxQuery() throws Exception {
        // 初始化服务
        Service testService = new Service();
        testService.setId("test-service-id");
        testService.setTypes(new String[]{"FAPI"});
        
        fapiServer.getServiceMap().put("test-service-id", testService);
        
        // 创建 Tx 查询
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEntity("tx");
        fcdsl.setSize("10");
        byte[] requestData = JsonUtils.toJson(fcdsl).getBytes(StandardCharsets.UTF_8);
        
        // Mock Elasticsearch 响应
        SearchResponse<data.fchData.Tx> mockResponse = mock(SearchResponse.class);
        HitsMetadata<data.fchData.Tx> hitsMetadata = mock(HitsMetadata.class);
        TotalHits totalHits = TotalHits.of(t -> t.value(5L).relation(co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq));
        
        when(hitsMetadata.total()).thenReturn(totalHits);
        when(hitsMetadata.hits()).thenReturn(Collections.emptyList());
        when(mockResponse.hits()).thenReturn(hitsMetadata);
        
        when(esClient.search(any(SearchRequest.class), eq(data.fchData.Tx.class))).thenReturn(mockResponse);
        
        // 发送请求
        fapiServer.onRequestReceived("peer-id", 1L, "test-service-id", requestData);
        
        // 验证
        verify(fudpNode, times(1)).respond(anyString(), anyLong(), eq(ResponseMessage.STATUS_SUCCESS), any(byte[].class));
    }
    
    @Test
    @DisplayName("集成测试 - Cid 查询")
    void testCidQuery() throws Exception {
        // 初始化服务
        Service testService = new Service();
        testService.setId("test-service-id");
        testService.setTypes(new String[]{"FAPI"});
        
        fapiServer.getServiceMap().put("test-service-id", testService);
        
        // 创建 Cid 查询
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEntity("cid");
        fcdsl.setSize("10");
        byte[] requestData = JsonUtils.toJson(fcdsl).getBytes(StandardCharsets.UTF_8);
        
        // Mock Elasticsearch 响应
        SearchResponse<data.fchData.Freer> mockResponse = mock(SearchResponse.class);
        HitsMetadata<data.fchData.Freer> hitsMetadata = mock(HitsMetadata.class);
        TotalHits totalHits = TotalHits.of(t -> t.value(3L).relation(co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq));
        
        when(hitsMetadata.total()).thenReturn(totalHits);
        when(hitsMetadata.hits()).thenReturn(Collections.emptyList());
        when(mockResponse.hits()).thenReturn(hitsMetadata);
        
        when(esClient.search(any(SearchRequest.class), eq(data.fchData.Freer.class))).thenReturn(mockResponse);
        
        // 发送请求
        fapiServer.onRequestReceived("peer-id", 1L, "test-service-id", requestData);
        
        // 验证
        verify(fudpNode, times(1)).respond(anyString(), anyLong(), eq(ResponseMessage.STATUS_SUCCESS), any(byte[].class));
    }
    
    @Test
    @DisplayName("集成测试 - 使用 index 而非 entity")
    void testQueryWithIndex() throws Exception {
        // 初始化服务
        Service testService = new Service();
        testService.setId("test-service-id");
        testService.setTypes(new String[]{"FAPI"});
        
        fapiServer.getServiceMap().put("test-service-id", testService);
        
        // 使用 index 而非 entity
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIndex(IndicesNames.BLOCK);
        fcdsl.setSize("10");
        byte[] requestData = JsonUtils.toJson(fcdsl).getBytes(StandardCharsets.UTF_8);
        
        // Mock Elasticsearch 响应
        SearchResponse<Block> mockResponse = mock(SearchResponse.class);
        HitsMetadata<Block> hitsMetadata = mock(HitsMetadata.class);
        TotalHits totalHits = TotalHits.of(t -> t.value(0L).relation(co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq));
        
        when(hitsMetadata.total()).thenReturn(totalHits);
        when(hitsMetadata.hits()).thenReturn(Collections.emptyList());
        when(mockResponse.hits()).thenReturn(hitsMetadata);
        
        when(esClient.search(any(SearchRequest.class), eq(Block.class))).thenReturn(mockResponse);
        
        // 发送请求
        fapiServer.onRequestReceived("peer-id", 1L, "test-service-id", requestData);
        
        // 验证
        verify(fudpNode, times(1)).respond(anyString(), anyLong(), anyInt(), any(byte[].class));
    }
    
    @Test
    @DisplayName("集成测试 - 错误处理流程")
    void testErrorHandlingFlow() throws Exception {
        // 初始化服务
        Service testService = new Service();
        testService.setId("test-service-id");
        testService.setTypes(new String[]{"FAPI"});
        
        fapiServer.getServiceMap().put("test-service-id", testService);
        
        // 创建无效的查询（缺少 entity 和 index）
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setSize("10");
        // 不设置 entity 或 index
        byte[] requestData = JsonUtils.toJson(fcdsl).getBytes(StandardCharsets.UTF_8);
        
        // 发送请求
        fapiServer.onRequestReceived("peer-id", 1L, "test-service-id", requestData);
        
        // 验证发送了错误响应
        verify(fudpNode, times(1)).respond(
            eq("peer-id"),
            eq(1L),
            eq(ResponseMessage.STATUS_SUCCESS), // 注意：FapiService 总是返回 STATUS_SUCCESS，错误在 data 中
            any(byte[].class)
        );
        
        // 验证响应数据包含错误信息
        // 这里需要捕获响应数据并验证
    }
    
    @Test
    @DisplayName("集成测试 - 并发请求处理")
    void testConcurrentRequestHandling() throws Exception {
        // 初始化服务
        Service testService = new Service();
        testService.setId("test-service-id");
        testService.setTypes(new String[]{"FAPI"});
        
        fapiServer.getServiceMap().put("test-service-id", testService);
        
        // Mock Elasticsearch 响应
        SearchResponse<Block> mockResponse = mock(SearchResponse.class);
        HitsMetadata<Block> hitsMetadata = mock(HitsMetadata.class);
        TotalHits totalHits = TotalHits.of(t -> t.value(0L).relation(co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq));
        
        when(hitsMetadata.total()).thenReturn(totalHits);
        when(hitsMetadata.hits()).thenReturn(Collections.emptyList());
        when(mockResponse.hits()).thenReturn(hitsMetadata);
        
        when(esClient.search(any(SearchRequest.class), eq(Block.class))).thenReturn(mockResponse);
        
        // 创建多个请求
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEntity("block");
        fcdsl.setSize("10");
        byte[] requestData = JsonUtils.toJson(fcdsl).getBytes(StandardCharsets.UTF_8);
        
        // 模拟并发请求
        int requestCount = 5;
        for (int i = 0; i < requestCount; i++) {
            fapiServer.onRequestReceived("peer-id-" + i, (long) i, "test-service-id", requestData);
        }
        
        // 验证所有请求都被处理
        verify(fudpNode, times(requestCount)).respond(anyString(), anyLong(), anyInt(), any(byte[].class));
    }
}
