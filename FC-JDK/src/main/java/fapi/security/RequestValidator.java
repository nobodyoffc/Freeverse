package fapi.security;

import data.apipData.Fcdsl;
import fapi.FapiCode;
import fapi.message.FapiRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 请求参数验证器
 * 
 * 注意：防重放由FUDP层处理（ReplayProtection + 加密），此处只需验证业务参数。
 * 
 * 验证内容：
 * 1. API名称格式
 * 2. 查询类请求的fcdsl参数
 * 3. 操作类请求的params参数
 * 4. 参数值范围（如IDs数量限制）
 */
public class RequestValidator {
    
    /** 最大ID数量 */
    public static final int MAX_IDS_COUNT = 100;
    
    /** 最大查询深度 */
    public static final int MAX_QUERY_DEPTH = 5;
    
    /** 最大文件大小（10MB） */
    public static final int MAX_FILE_SIZE = 10 * 1024 * 1024;
    
    /** 最大批量操作数量 */
    public static final int MAX_BATCH_SIZE = 50;
    
    /** 查询类方法 */
    private static final Set<String> QUERY_METHODS = Set.of(
        "getByIds", "search", "list", "totals", "health"
    );
    
    /** 操作类方法（需要params） */
    private static final Set<String> OPERATION_METHODS = Set.of(
        "put", "batchPut", "get", "carve", "delete", 
        "prepareUpload", "confirmUpload", "prepareDownload", "resumeUpload"
    );
    
    private RequestValidator() {
        // 工具类，禁止实例化
    }
    
    // ==================== 验证方法 ====================
    
    /**
     * 验证通用请求参数
     */
    public static ValidationResult validate(FapiRequest request) {
        if (request == null) {
            return ValidationResult.fail(FapiCode.BAD_REQUEST, "Request cannot be null");
        }
        
        // 验证API名称格式
        String api = request.getApi();
        if (api == null || api.isBlank()) {
            return ValidationResult.fail(FapiCode.BAD_REQUEST, "API name is required");
        }
        
        if (!api.contains(".")) {
            return ValidationResult.fail(FapiCode.BAD_REQUEST, 
                "Invalid api format, expected: component.method");
        }
        
        String method = request.getMethodName();
        if (method == null || method.isBlank()) {
            return ValidationResult.fail(FapiCode.BAD_REQUEST, "Method name cannot be empty");
        }
        
        // 验证查询类请求
        if (isQueryMethod(method)) {
            if (request.getFcdsl() == null && !"health".equals(method) && !"totals".equals(method)) {
                return ValidationResult.fail(FapiCode.BAD_REQUEST, 
                    "fcdsl is required for query API: " + method);
            }
            if (request.getFcdsl() != null) {
                ValidationResult fcdslResult = validateFcdsl(request.getFcdsl());
                if (!fcdslResult.isValid()) {
                    return fcdslResult;
                }
            }
        }
        
        // 验证操作类请求
        if (isOperationMethod(method) && request.getParams() == null) {
            return ValidationResult.fail(FapiCode.BAD_REQUEST, 
                "params is required for operation API: " + method);
        }
        
        return ValidationResult.ok();
    }
    
    /**
     * 验证IDs查询参数
     */
    public static ValidationResult validateIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return ValidationResult.fail(FapiCode.BAD_REQUEST, "IDs cannot be empty");
        }
        if (ids.size() > MAX_IDS_COUNT) {
            return ValidationResult.fail(FapiCode.BAD_REQUEST, 
                "Too many IDs, max: " + MAX_IDS_COUNT + ", got: " + ids.size());
        }
        
        // 验证每个ID不为空
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            if (id == null || id.isBlank()) {
                return ValidationResult.fail(FapiCode.BAD_REQUEST, 
                    "ID at index " + i + " cannot be empty");
            }
        }
        
        return ValidationResult.ok();
    }
    
    /**
     * 验证Fcdsl查询复杂度
     */
    public static ValidationResult validateFcdsl(Fcdsl fcdsl) {
        if (fcdsl == null) {
            return ValidationResult.ok();
        }
        
        // 验证entity
        if (fcdsl.getEntity() != null && fcdsl.getEntity().length() > 64) {
            return ValidationResult.fail(FapiCode.BAD_REQUEST, 
                "Entity name too long, max: 64 characters");
        }
        
        // 验证IDs数量
        if (fcdsl.getIds() != null && fcdsl.getIds().size() > MAX_IDS_COUNT) {
            return ValidationResult.fail(FapiCode.BAD_REQUEST, 
                "Too many IDs in fcdsl, max: " + MAX_IDS_COUNT);
        }
        
        // 验证分页大小
        if (fcdsl.getSize() != null) {
            try {
                int size = Integer.parseInt(fcdsl.getSize());
                if (size > MAX_IDS_COUNT) {
                    return ValidationResult.fail(FapiCode.BAD_REQUEST, 
                        "Page size too large, max: " + MAX_IDS_COUNT);
                }
                if (size < 0) {
                    return ValidationResult.fail(FapiCode.BAD_REQUEST, 
                        "Page size cannot be negative");
                }
            } catch (NumberFormatException e) {
                return ValidationResult.fail(FapiCode.BAD_REQUEST, 
                    "Invalid page size format");
            }
        }
        
        return ValidationResult.ok();
    }
    
    /**
     * 验证文件大小
     */
    public static ValidationResult validateFileSize(long size) {
        if (size < 0) {
            return ValidationResult.fail(FapiCode.BAD_REQUEST, "File size cannot be negative");
        }
        if (size > MAX_FILE_SIZE) {
            return ValidationResult.fail(FapiCode.PAYLOAD_TOO_LARGE, 
                "File too large, max: " + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
        }
        return ValidationResult.ok();
    }
    
    /**
     * 验证批量操作参数
     */
    @SuppressWarnings("unchecked")
    public static ValidationResult validateBatchParams(Object params, String fieldName) {
        if (params == null) {
            return ValidationResult.fail(FapiCode.BAD_REQUEST, "params cannot be null");
        }
        
        if (params instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) params;
            Object items = map.get(fieldName);
            if (items instanceof List) {
                List<?> list = (List<?>) items;
                if (list.isEmpty()) {
                    return ValidationResult.fail(FapiCode.BAD_REQUEST, 
                        fieldName + " cannot be empty");
                }
                if (list.size() > MAX_BATCH_SIZE) {
                    return ValidationResult.fail(FapiCode.BAD_REQUEST, 
                        "Too many items in " + fieldName + ", max: " + MAX_BATCH_SIZE);
                }
            }
        }
        
        return ValidationResult.ok();
    }
    
    /**
     * 验证peerId格式（FCH地址格式）
     */
    public static ValidationResult validatePeerId(String peerId) {
        if (peerId == null || peerId.isBlank()) {
            return ValidationResult.fail(FapiCode.UNAUTHORIZED, "Peer ID is required");
        }
        
        // FCH地址以F开头，长度约34个字符
        if (!peerId.startsWith("F") || peerId.length() < 26 || peerId.length() > 35) {
            return ValidationResult.fail(FapiCode.UNAUTHORIZED, "Invalid peer ID format");
        }
        
        return ValidationResult.ok();
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 判断是否为查询类方法
     */
    public static boolean isQueryMethod(String method) {
        return QUERY_METHODS.contains(method);
    }
    
    /**
     * 判断是否为操作类方法
     */
    public static boolean isOperationMethod(String method) {
        return OPERATION_METHODS.contains(method);
    }
}

