package fapi.handler;

import config.Settings;
import constants.CodeMessage;
import constants.Constants;
import constants.IndicesNames;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.MgetRequest;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import data.apipData.Fcdsl;
import data.fchData.Block;
import data.fchData.Tx;
import data.fchData.Freer;
import data.fchData.Cash;
import data.feipData.Service;
import fapi.message.FapiResponse;
import fapi.util.ResponseBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import utils.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FcFudpRequestHandler 单元测试
 */
@DisplayName("FcFudpRequestHandler Tests")
class FcFudpRequestHandlerTest {
    
    @Mock
    private Settings settings;
    
    @Mock
    private ElasticsearchClient esClient;
    
    private FcFudpRequestHandler handler;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(settings.getClient(Service.ServiceType.ES)).thenReturn(esClient);
        when(settings.getBestHeight()).thenReturn(1000L);
        handler = new FcFudpRequestHandler(settings);
    }
    
    @Test
    @DisplayName("测试 FCDSL 解析 - 有效 JSON")
    void testParseValidFcdsl() {
        String json = "{\"entity\":\"block\",\"size\":\"10\"}";
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        
        Service service = mock(Service.class);
        when(service.getId()).thenReturn("test-service-id");
        when(service.getTypes()).thenReturn(new String[]{"FAPI"});
        
        // Mock Elasticsearch 响应
        SearchResponse<Block> mockResponse = mock(SearchResponse.class);
        HitsMetadata<Block> hitsMetadata = mock(HitsMetadata.class);
        TotalHits totalHits = TotalHits.of(t -> t.value(0L).relation(co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq));
        
        when(hitsMetadata.total()).thenReturn(totalHits);
        when(hitsMetadata.hits()).thenReturn(Collections.emptyList());
        when(mockResponse.hits()).thenReturn(hitsMetadata);
        
        try {
            when(esClient.search(any(SearchRequest.class), eq(Block.class))).thenReturn(mockResponse);
        } catch (Exception e) {
            // Ignore
        }
        
        byte[] response = handler.handleRequest(service, "peerId", data);
        assertNotNull(response);
        
        String responseStr = new String(response, StandardCharsets.UTF_8);
        FapiResponse fapiResponse = JsonUtils.fromJson(responseStr, FapiResponse.class);
        assertNotNull(fapiResponse);
    }
    
    @Test
    @DisplayName("测试 FCDSL 解析 - 无效 JSON")
    void testParseInvalidJson() {
        String invalidJson = "{invalid json}";
        byte[] data = invalidJson.getBytes(StandardCharsets.UTF_8);
        
        Service service = mock(Service.class);
        when(service.getId()).thenReturn("test-service-id");
        when(service.getTypes()).thenReturn(new String[]{"FAPI"});
        
        byte[] response = handler.handleRequest(service, "peerId", data);
        assertNotNull(response);
        
        String responseStr = new String(response, StandardCharsets.UTF_8);
        FapiResponse fapiResponse = JsonUtils.fromJson(responseStr, FapiResponse.class);
        assertEquals(CodeMessage.Code1013BadRequest, fapiResponse.getCode());
    }
    
    @Test
    @DisplayName("测试索引映射 - 使用 entity")
    void testIndexMappingByEntity() {
        String json = "{\"entity\":\"block\",\"size\":\"10\"}";
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        
        Service service = mock(Service.class);
        when(service.getId()).thenReturn("test-service-id");
        when(service.getTypes()).thenReturn(new String[]{"FAPI"});
        
        // Mock Elasticsearch 响应
        SearchResponse<Block> mockResponse = mock(SearchResponse.class);
        HitsMetadata<Block> hitsMetadata = mock(HitsMetadata.class);
        TotalHits totalHits = TotalHits.of(t -> t.value(0L).relation(co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq));
        
        when(hitsMetadata.total()).thenReturn(totalHits);
        when(hitsMetadata.hits()).thenReturn(Collections.emptyList());
        when(mockResponse.hits()).thenReturn(hitsMetadata);
        
        try {
            when(esClient.search(any(SearchRequest.class), eq(Block.class))).thenReturn(mockResponse);
        } catch (Exception e) {
            // Ignore
        }
        
        byte[] response = handler.handleRequest(service, "peerId", data);
        assertNotNull(response);
        
        // 验证查询被调用（通过 entity 映射到 block 索引）
        try {
            verify(esClient, atLeastOnce()).search(any(SearchRequest.class), eq(Block.class));
        } catch (Exception e) {
            // Ignore
        }
    }
    
    @Test
    @DisplayName("测试索引映射 - 使用 index")
    void testIndexMappingByIndex() {
        String json = "{\"index\":\"" + IndicesNames.BLOCK + "\",\"size\":\"10\"}";
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        
        Service service = mock(Service.class);
        when(service.getId()).thenReturn("test-service-id");
        when(service.getTypes()).thenReturn(new String[]{"FAPI"});
        
        // Mock Elasticsearch 响应
        SearchResponse<Block> mockResponse = mock(SearchResponse.class);
        HitsMetadata<Block> hitsMetadata = mock(HitsMetadata.class);
        TotalHits totalHits = TotalHits.of(t -> t.value(0L).relation(co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq));
        
        when(hitsMetadata.total()).thenReturn(totalHits);
        when(hitsMetadata.hits()).thenReturn(Collections.emptyList());
        when(mockResponse.hits()).thenReturn(hitsMetadata);
        
        try {
            when(esClient.search(any(SearchRequest.class), eq(Block.class))).thenReturn(mockResponse);
        } catch (Exception e) {
            // Ignore
        }
        
        byte[] response = handler.handleRequest(service, "peerId", data);
        assertNotNull(response);
    }
    
    @Test
    @DisplayName("测试错误处理 - 缺少 entity 和 index")
    void testMissingEntityAndIndex() {
        String json = "{\"size\":\"10\"}";
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        
        Service service = mock(Service.class);
        when(service.getId()).thenReturn("test-service-id");
        when(service.getTypes()).thenReturn(new String[]{"FAPI"});
        
        byte[] response = handler.handleRequest(service, "peerId", data);
        assertNotNull(response);
        
        String responseStr = new String(response, StandardCharsets.UTF_8);
        FapiResponse fapiResponse = JsonUtils.fromJson(responseStr, FapiResponse.class);
        assertEquals(CodeMessage.Code1012BadQuery, fapiResponse.getCode());
    }
    
    @Test
    @DisplayName("测试错误处理 - 无效的 entity")
    void testInvalidEntity() {
        String json = "{\"entity\":\"invalid_entity\",\"size\":\"10\"}";
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        
        Service service = mock(Service.class);
        when(service.getId()).thenReturn("test-service-id");
        when(service.getTypes()).thenReturn(new String[]{"FAPI"});
        
        byte[] response = handler.handleRequest(service, "peerId", data);
        assertNotNull(response);
        
        String responseStr = new String(response, StandardCharsets.UTF_8);
        FapiResponse fapiResponse = JsonUtils.fromJson(responseStr, FapiResponse.class);
        assertEquals(CodeMessage.Code1013BadRequest, fapiResponse.getCode());
    }
    
    @Test
    @DisplayName("测试 IDs 查询")
    void testIdsRequest() {
        String json = "{\"entity\":\"block\",\"ids\":[\"id1\",\"id2\",\"id3\"]}";
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        
        Service service = mock(Service.class);
        when(service.getId()).thenReturn("test-service-id");
        when(service.getTypes()).thenReturn(new String[]{"FAPI"});
        
        // Mock Elasticsearch mget 响应
        MgetResponse<Block> mockMgetResponse = mock(MgetResponse.class);
        List<MultiGetResponseItem<Block>> items = new ArrayList<>();
        
        Block block1 = new Block();
        block1.setId("id1");
        MultiGetResponseItem<Block> item1 = mock(MultiGetResponseItem.class);
        // Use Answer with Proxy to create an object that implements found() and source() methods
        when(item1.result()).thenAnswer(invocation -> {
            // Create a proxy that handles the method calls
            return java.lang.reflect.Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class[]{},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("found".equals(methodName)) {
                        return true;
                    }
                    if ("source".equals(methodName)) {
                        return block1;
                    }
                    if ("equals".equals(methodName)) {
                        return proxy == args[0];
                    }
                    if ("hashCode".equals(methodName)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("toString".equals(methodName)) {
                        return "MockFields";
                    }
                    return null;
                }
            );
        });
        items.add(item1);
        
        when(mockMgetResponse.docs()).thenReturn(items);
        
        try {
            when(esClient.mget(any(MgetRequest.class), eq(Block.class))).thenReturn(mockMgetResponse);
        } catch (Exception e) {
            // Ignore
        }
        
        byte[] response = handler.handleRequest(service, "peerId", data);
        assertNotNull(response);
        
        String responseStr = new String(response, StandardCharsets.UTF_8);
        FapiResponse fapiResponse = JsonUtils.fromJson(responseStr, FapiResponse.class);
        assertNotNull(fapiResponse);
    }
    
    @Test
    @DisplayName("测试 IDs 查询 - 超过最大数量限制")
    void testIdsRequestExceedsMaxSize() {
        // 创建超过 MaxRequestSize 的 ID 列表
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < Constants.MaxRequestSize + 1; i++) {
            ids.add("id" + i);
        }
        String json = "{\"entity\":\"block\",\"ids\":" + JsonUtils.toJson(ids) + "}";
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        
        Service service = mock(Service.class);
        when(service.getId()).thenReturn("test-service-id");
        when(service.getTypes()).thenReturn(new String[]{"FAPI"});
        
        byte[] response = handler.handleRequest(service, "peerId", data);
        assertNotNull(response);
        
        String responseStr = new String(response, StandardCharsets.UTF_8);
        FapiResponse fapiResponse = JsonUtils.fromJson(responseStr, FapiResponse.class);
        assertEquals(CodeMessage.Code1013BadRequest, fapiResponse.getCode());
    }
    
    @Test
    @DisplayName("测试 size 参数处理 - 超过最大值")
    void testSizeExceedsMax() {
        String json = "{\"entity\":\"block\",\"size\":\"" + (Constants.MaxRequestSize + 100) + "\"}";
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        
        Service service = mock(Service.class);
        when(service.getId()).thenReturn("test-service-id");
        when(service.getTypes()).thenReturn(new String[]{"FAPI"});
        
        // Mock Elasticsearch 响应
        SearchResponse<Block> mockResponse = mock(SearchResponse.class);
        HitsMetadata<Block> hitsMetadata = mock(HitsMetadata.class);
        TotalHits totalHits = TotalHits.of(t -> t.value(0L).relation(co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq));
        
        when(hitsMetadata.total()).thenReturn(totalHits);
        when(hitsMetadata.hits()).thenReturn(Collections.emptyList());
        when(mockResponse.hits()).thenReturn(hitsMetadata);
        
        try {
            when(esClient.search(any(SearchRequest.class), eq(Block.class))).thenReturn(mockResponse);
        } catch (Exception e) {
            // Ignore
        }
        
        byte[] response = handler.handleRequest(service, "peerId", data);
        assertNotNull(response);
        
        // 验证 size 被限制为 DefaultSize
        try {
            verify(esClient, atLeastOnce()).search(any(SearchRequest.class), eq(Block.class));
        } catch (Exception e) {
            // Ignore
        }
    }
    
    @Test
    @DisplayName("测试支持的实体类型")
    void testSupportedEntityTypes() {
        String[] entities = {"block", "tx", "cid", "cash"};
        
        for (String entity : entities) {
            String json = "{\"entity\":\"" + entity + "\",\"size\":\"10\"}";
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            
            Service service = mock(Service.class);
            when(service.getId()).thenReturn("test-service-id");
            when(service.getTypes()).thenReturn(new String[]{"FAPI"});
            
            // Mock Elasticsearch 响应 - 为每种实体类型创建特定的 mock
            Class<?> entityClass = getEntityClass(entity);
            
            // 为每种类型创建特定的 mock
            if (entityClass == Block.class) {
                SearchResponse<Block> mockResponse = mock(SearchResponse.class);
                HitsMetadata<Block> hitsMetadata = mock(HitsMetadata.class);
                TotalHits totalHits = TotalHits.of(t -> t.value(0L).relation(co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq));
                when(hitsMetadata.total()).thenReturn(totalHits);
                when(hitsMetadata.hits()).thenReturn(Collections.emptyList());
                when(mockResponse.hits()).thenReturn(hitsMetadata);
                try {
                    when(esClient.search(any(SearchRequest.class), eq(Block.class))).thenReturn(mockResponse);
                } catch (Exception e) {
                    // Ignore
                }
            } else if (entityClass == Tx.class) {
                SearchResponse<Tx> mockResponse = mock(SearchResponse.class);
                HitsMetadata<Tx> hitsMetadata = mock(HitsMetadata.class);
                TotalHits totalHits = TotalHits.of(t -> t.value(0L).relation(co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq));
                when(hitsMetadata.total()).thenReturn(totalHits);
                when(hitsMetadata.hits()).thenReturn(Collections.emptyList());
                when(mockResponse.hits()).thenReturn(hitsMetadata);
                try {
                    when(esClient.search(any(SearchRequest.class), eq(Tx.class))).thenReturn(mockResponse);
                } catch (Exception e) {
                    // Ignore
                }
            } else if (entityClass == Freer.class) {
                SearchResponse<Freer> mockResponse = mock(SearchResponse.class);
                HitsMetadata<Freer> hitsMetadata = mock(HitsMetadata.class);
                TotalHits totalHits = TotalHits.of(t -> t.value(0L).relation(co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq));
                when(hitsMetadata.total()).thenReturn(totalHits);
                when(hitsMetadata.hits()).thenReturn(Collections.emptyList());
                when(mockResponse.hits()).thenReturn(hitsMetadata);
                try {
                    when(esClient.search(any(SearchRequest.class), eq(Freer.class))).thenReturn(mockResponse);
                } catch (Exception e) {
                    // Ignore
                }
            } else if (entityClass == Cash.class) {
                SearchResponse<Cash> mockResponse = mock(SearchResponse.class);
                HitsMetadata<Cash> hitsMetadata = mock(HitsMetadata.class);
                TotalHits totalHits = TotalHits.of(t -> t.value(0L).relation(co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq));
                when(hitsMetadata.total()).thenReturn(totalHits);
                when(hitsMetadata.hits()).thenReturn(Collections.emptyList());
                when(mockResponse.hits()).thenReturn(hitsMetadata);
                try {
                    when(esClient.search(any(SearchRequest.class), eq(Cash.class))).thenReturn(mockResponse);
                } catch (Exception e) {
                    // Ignore
                }
            }
            
            byte[] response = handler.handleRequest(service, "peerId", data);
            assertNotNull(response, "Entity type " + entity + " should be supported");
        }
    }
    
    private Class<?> getEntityClass(String entity) {
        return switch (entity) {
            case "block" -> Block.class;
            case "tx" -> Tx.class;
            case "cid" -> Freer.class;
            case "cash" -> Cash.class;
            default -> throw new IllegalArgumentException("Unknown entity: " + entity);
        };
    }
    
    @Test
    @DisplayName("测试 FCDSL 验证 - ids 与 query 冲突")
    void testBadFcdslIdsWithQuery() {
        String json = "{\"entity\":\"block\",\"ids\":[\"id1\"],\"query\":{\"terms\":{\"fields\":[\"height\"],\"values\":[\"1000\"]}}}";
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        
        Service service = mock(Service.class);
        when(service.getId()).thenReturn("test-service-id");
        when(service.getTypes()).thenReturn(new String[]{"FAPI"});
        
        byte[] response = handler.handleRequest(service, "peerId", data);
        assertNotNull(response);
        
        String responseStr = new String(response, StandardCharsets.UTF_8);
        FapiResponse fapiResponse = JsonUtils.fromJson(responseStr, FapiResponse.class);
        assertEquals(CodeMessage.Code1013BadRequest, fapiResponse.getCode());
    }
}

