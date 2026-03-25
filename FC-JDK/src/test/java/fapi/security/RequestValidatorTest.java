package fapi.security;

import data.apipData.Fcdsl;
import fapi.FapiCode;
import fapi.message.FapiRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RequestValidator 单元测试
 */
@DisplayName("RequestValidator Tests")
class RequestValidatorTest {
    
    // ==================== validate() 测试 ====================
    
    @Nested
    @DisplayName("validate() 方法测试")
    class ValidateTests {
        
        @Test
        @DisplayName("null请求应返回错误")
        void testNullRequest() {
            ValidationResult result = RequestValidator.validate(null);
            
            assertFalse(result.isValid());
            assertEquals(FapiCode.BAD_REQUEST, result.getCode());
            assertEquals("Request cannot be null", result.getMessage());
        }
        
        @Test
        @DisplayName("空API名称应返回错误")
        void testEmptyApiName() {
            FapiRequest request = new FapiRequest();
            request.setApi("");
            
            ValidationResult result = RequestValidator.validate(request);
            
            assertFalse(result.isValid());
            assertEquals(FapiCode.BAD_REQUEST, result.getCode());
        }
        
        @Test
        @DisplayName("API名称缺少点号应返回错误")
        void testApiNameWithoutDot() {
            FapiRequest request = new FapiRequest();
            request.setApi("basesearch");
            
            ValidationResult result = RequestValidator.validate(request);
            
            assertFalse(result.isValid());
            assertEquals(FapiCode.BAD_REQUEST, result.getCode());
            assertTrue(result.getMessage().contains("component.method"));
        }
        
        @Test
        @DisplayName("有效API名称应通过验证")
        void testValidApiName() {
            FapiRequest request = new FapiRequest();
            request.setApi("base.search");
            Fcdsl fcdsl = new Fcdsl();
            fcdsl.setEntity("block");
            request.setFcdsl(fcdsl);
            
            ValidationResult result = RequestValidator.validate(request);
            
            assertTrue(result.isValid());
        }
        
        @Test
        @DisplayName("健康检查API不需要fcdsl")
        void testHealthApiNoFcdslRequired() {
            FapiRequest request = new FapiRequest();
            request.setApi("base.health");
            
            ValidationResult result = RequestValidator.validate(request);
            
            assertTrue(result.isValid());
        }
        
        @Test
        @DisplayName("totals API不需要fcdsl")
        void testTotalsApiNoFcdslRequired() {
            FapiRequest request = new FapiRequest();
            request.setApi("base.totals");
            
            ValidationResult result = RequestValidator.validate(request);
            
            assertTrue(result.isValid());
        }
        
        @Test
        @DisplayName("操作类API需要params")
        void testOperationApiRequiresParams() {
            FapiRequest request = new FapiRequest();
            request.setApi("disk.put");
            
            ValidationResult result = RequestValidator.validate(request);
            
            assertFalse(result.isValid());
            assertTrue(result.getMessage().contains("params is required"));
        }
        
        @Test
        @DisplayName("操作类API有params应通过")
        void testOperationApiWithParams() {
            FapiRequest request = new FapiRequest();
            request.setApi("disk.put");
            request.setParams(Map.of("content", "base64data"));
            
            ValidationResult result = RequestValidator.validate(request);
            
            assertTrue(result.isValid());
        }
    }
    
    // ==================== validateIds() 测试 ====================
    
    @Nested
    @DisplayName("validateIds() 方法测试")
    class ValidateIdsTests {
        
        @Test
        @DisplayName("null IDs应返回错误")
        void testNullIds() {
            ValidationResult result = RequestValidator.validateIds(null);
            
            assertFalse(result.isValid());
            assertEquals("IDs cannot be empty", result.getMessage());
        }
        
        @Test
        @DisplayName("空IDs列表应返回错误")
        void testEmptyIds() {
            ValidationResult result = RequestValidator.validateIds(new ArrayList<>());
            
            assertFalse(result.isValid());
            assertEquals("IDs cannot be empty", result.getMessage());
        }
        
        @Test
        @DisplayName("超过100个ID应返回错误")
        void testTooManyIds() {
            List<String> ids = IntStream.range(0, 150)
                .mapToObj(i -> "FID" + i)
                .toList();
            
            ValidationResult result = RequestValidator.validateIds(ids);
            
            assertFalse(result.isValid());
            assertTrue(result.getMessage().contains("Too many IDs"));
            assertTrue(result.getMessage().contains("100"));
        }
        
        @Test
        @DisplayName("正好100个ID应通过")
        void testExactlyMaxIds() {
            List<String> ids = IntStream.range(0, 100)
                .mapToObj(i -> "FID" + i)
                .toList();
            
            ValidationResult result = RequestValidator.validateIds(ids);
            
            assertTrue(result.isValid());
        }
        
        @Test
        @DisplayName("包含空ID应返回错误")
        void testEmptyIdInList() {
            List<String> ids = List.of("FID1", "", "FID3");
            
            ValidationResult result = RequestValidator.validateIds(ids);
            
            assertFalse(result.isValid());
            assertTrue(result.getMessage().contains("index 1"));
        }
        
        @Test
        @DisplayName("有效IDs应通过")
        void testValidIds() {
            List<String> ids = List.of("FEk41xxxx", "FGw2yyyy");
            
            ValidationResult result = RequestValidator.validateIds(ids);
            
            assertTrue(result.isValid());
        }
    }
    
    // ==================== validateFcdsl() 测试 ====================
    
    @Nested
    @DisplayName("validateFcdsl() 方法测试")
    class ValidateFcdslTests {
        
        @Test
        @DisplayName("null fcdsl应通过")
        void testNullFcdsl() {
            ValidationResult result = RequestValidator.validateFcdsl(null);
            
            assertTrue(result.isValid());
        }
        
        @Test
        @DisplayName("entity名称过长应返回错误")
        void testEntityTooLong() {
            Fcdsl fcdsl = new Fcdsl();
            fcdsl.setEntity("a".repeat(100));
            
            ValidationResult result = RequestValidator.validateFcdsl(fcdsl);
            
            assertFalse(result.isValid());
            assertTrue(result.getMessage().contains("Entity name too long"));
        }
        
        @Test
        @DisplayName("fcdsl中IDs过多应返回错误")
        void testTooManyIdsInFcdsl() {
            Fcdsl fcdsl = new Fcdsl();
            List<String> ids = IntStream.range(0, 150)
                .mapToObj(i -> "FID" + i)
                .toList();
            fcdsl.setIds(ids);
            
            ValidationResult result = RequestValidator.validateFcdsl(fcdsl);
            
            assertFalse(result.isValid());
            assertTrue(result.getMessage().contains("Too many IDs in fcdsl"));
        }
        
        @Test
        @DisplayName("分页大小过大应返回错误")
        void testPageSizeTooLarge() {
            Fcdsl fcdsl = new Fcdsl();
            fcdsl.setSize("200");
            
            ValidationResult result = RequestValidator.validateFcdsl(fcdsl);
            
            assertFalse(result.isValid());
            assertTrue(result.getMessage().contains("Page size too large"));
        }
        
        @Test
        @DisplayName("负数分页大小应返回错误")
        void testNegativePageSize() {
            Fcdsl fcdsl = new Fcdsl();
            fcdsl.setSize("-10");
            
            ValidationResult result = RequestValidator.validateFcdsl(fcdsl);
            
            assertFalse(result.isValid());
            assertTrue(result.getMessage().contains("cannot be negative"));
        }
        
        @Test
        @DisplayName("无效分页大小格式应返回错误")
        void testInvalidPageSizeFormat() {
            Fcdsl fcdsl = new Fcdsl();
            fcdsl.setSize("abc");
            
            ValidationResult result = RequestValidator.validateFcdsl(fcdsl);
            
            assertFalse(result.isValid());
            assertTrue(result.getMessage().contains("Invalid page size format"));
        }
        
        @Test
        @DisplayName("有效fcdsl应通过")
        void testValidFcdsl() {
            Fcdsl fcdsl = new Fcdsl();
            fcdsl.setEntity("block");
            fcdsl.setSize("50");
            fcdsl.setIds(List.of("id1", "id2"));
            
            ValidationResult result = RequestValidator.validateFcdsl(fcdsl);
            
            assertTrue(result.isValid());
        }
    }
    
    // ==================== validateFileSize() 测试 ====================
    
    @Nested
    @DisplayName("validateFileSize() 方法测试")
    class ValidateFileSizeTests {
        
        @Test
        @DisplayName("负数文件大小应返回错误")
        void testNegativeFileSize() {
            ValidationResult result = RequestValidator.validateFileSize(-100);
            
            assertFalse(result.isValid());
            assertTrue(result.getMessage().contains("cannot be negative"));
        }
        
        @Test
        @DisplayName("超过10MB应返回错误")
        void testFileTooLarge() {
            long size = 15 * 1024 * 1024; // 15MB
            
            ValidationResult result = RequestValidator.validateFileSize(size);
            
            assertFalse(result.isValid());
            assertEquals(FapiCode.PAYLOAD_TOO_LARGE, result.getCode());
        }
        
        @Test
        @DisplayName("正好10MB应通过")
        void testExactlyMaxSize() {
            long size = 10 * 1024 * 1024; // 10MB
            
            ValidationResult result = RequestValidator.validateFileSize(size);
            
            assertTrue(result.isValid());
        }
        
        @Test
        @DisplayName("0字节应通过")
        void testZeroSize() {
            ValidationResult result = RequestValidator.validateFileSize(0);
            
            assertTrue(result.isValid());
        }
    }
    
    // ==================== validatePeerId() 测试 ====================
    
    @Nested
    @DisplayName("validatePeerId() 方法测试")
    class ValidatePeerIdTests {
        
        @Test
        @DisplayName("null peerId应返回错误")
        void testNullPeerId() {
            ValidationResult result = RequestValidator.validatePeerId(null);
            
            assertFalse(result.isValid());
            assertEquals(FapiCode.UNAUTHORIZED, result.getCode());
        }
        
        @Test
        @DisplayName("空peerId应返回错误")
        void testEmptyPeerId() {
            ValidationResult result = RequestValidator.validatePeerId("");
            
            assertFalse(result.isValid());
            assertEquals(FapiCode.UNAUTHORIZED, result.getCode());
        }
        
        @Test
        @DisplayName("不以F开头应返回错误")
        void testPeerIdNotStartingWithF() {
            ValidationResult result = RequestValidator.validatePeerId("A1234567890123456789012345678901234");
            
            assertFalse(result.isValid());
            assertTrue(result.getMessage().contains("Invalid peer ID format"));
        }
        
        @Test
        @DisplayName("长度过短应返回错误")
        void testPeerIdTooShort() {
            ValidationResult result = RequestValidator.validatePeerId("F12345");
            
            assertFalse(result.isValid());
        }
        
        @Test
        @DisplayName("有效FCH地址应通过")
        void testValidPeerId() {
            ValidationResult result = RequestValidator.validatePeerId("FEk41Aik3q9L4jVR2BL6iZzJuQ8fAGvmb2");
            
            assertTrue(result.isValid());
        }
    }
    
    // ==================== 辅助方法测试 ====================
    
    @Nested
    @DisplayName("辅助方法测试")
    class HelperMethodsTests {
        
        @Test
        @DisplayName("isQueryMethod - 查询方法")
        void testIsQueryMethod() {
            assertTrue(RequestValidator.isQueryMethod("getByIds"));
            assertTrue(RequestValidator.isQueryMethod("search"));
            assertTrue(RequestValidator.isQueryMethod("list"));
            assertTrue(RequestValidator.isQueryMethod("totals"));
            assertTrue(RequestValidator.isQueryMethod("health"));
            
            assertFalse(RequestValidator.isQueryMethod("put"));
            assertFalse(RequestValidator.isQueryMethod("delete"));
        }
        
        @Test
        @DisplayName("isOperationMethod - 操作方法")
        void testIsOperationMethod() {
            assertTrue(RequestValidator.isOperationMethod("put"));
            assertTrue(RequestValidator.isOperationMethod("batchPut"));
            assertTrue(RequestValidator.isOperationMethod("get"));
            assertTrue(RequestValidator.isOperationMethod("carve"));
            assertTrue(RequestValidator.isOperationMethod("delete"));
            
            assertFalse(RequestValidator.isOperationMethod("search"));
            assertFalse(RequestValidator.isOperationMethod("health"));
        }
    }
}

