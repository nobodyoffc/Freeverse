package fapi.util;

import config.Settings;
import constants.CodeMessage;
import fapi.message.FapiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import utils.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ResponseBuilder 单元测试
 */
@DisplayName("ResponseBuilder Tests")
class ResponseBuilderTest {
    
    @Mock
    private Settings settings;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(settings.getBestHeight()).thenReturn(1000L);
    }
    
    @Test
    @DisplayName("测试构建成功响应")
    void testBuildSuccessResponse() {
        List<String> testData = Arrays.asList("item1", "item2", "item3");
        Long got = 3L;
        Long total = 10L;
        List<String> last = Arrays.asList("last1", "last2");
        
        byte[] response = ResponseBuilder.buildSuccessResponse(
            testData, got, total, last, settings
        );
        
        assertNotNull(response);
        
        // 验证响应内容
        String responseStr = new String(response, StandardCharsets.UTF_8);
        FapiResponse fapiResponse = JsonUtils.fromJson(responseStr, FapiResponse.class);
        
        assertNotNull(fapiResponse);
        assertEquals(CodeMessage.Code0Success, fapiResponse.getCode());
        assertEquals(got, fapiResponse.getGot());
        assertEquals(total, fapiResponse.getTotal());
        assertEquals(last, fapiResponse.getLast());
        assertEquals(1000L, fapiResponse.getBestHeight());
        assertNotNull(fapiResponse.getData());
    }
    
    @Test
    @DisplayName("测试构建错误响应 - 使用默认消息")
    void testBuildErrorResponseWithDefaultMessage() {
        int errorCode = CodeMessage.Code1011DataNotFound;
        
        byte[] response = ResponseBuilder.buildErrorResponse(errorCode, settings);
        
        assertNotNull(response);
        
        String responseStr = new String(response, StandardCharsets.UTF_8);
        FapiResponse fapiResponse = JsonUtils.fromJson(responseStr, FapiResponse.class);
        
        assertNotNull(fapiResponse);
        assertEquals(errorCode, fapiResponse.getCode());
        assertEquals(0L, fapiResponse.getGot());
        assertEquals(0L, fapiResponse.getTotal());
        assertNull(fapiResponse.getData());
        assertNotNull(fapiResponse.getMessage());
    }
    
    @Test
    @DisplayName("测试构建错误响应 - 使用自定义消息")
    void testBuildErrorResponseWithCustomMessage() {
        int errorCode = CodeMessage.Code1012BadQuery;
        String customMessage = "Custom error message";
        
        byte[] response = ResponseBuilder.buildErrorResponse(
            errorCode, customMessage, settings
        );
        
        assertNotNull(response);
        
        String responseStr = new String(response, StandardCharsets.UTF_8);
        FapiResponse fapiResponse = JsonUtils.fromJson(responseStr, FapiResponse.class);
        
        assertNotNull(fapiResponse);
        assertEquals(errorCode, fapiResponse.getCode());
        assertEquals(customMessage, fapiResponse.getMessage());
    }
    
    @Test
    @DisplayName("测试构建错误响应 JSON 字符串")
    void testBuildErrorJson() {
        int errorCode = CodeMessage.Code1020OtherError;
        String customMessage = "Internal error";
        
        String json = ResponseBuilder.buildErrorJson(errorCode, customMessage);
        
        assertNotNull(json);
        assertTrue(json.contains("\"code\":" + errorCode));
        assertTrue(json.contains(customMessage));
    }
}

