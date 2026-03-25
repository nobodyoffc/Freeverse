package fapi;

import java.util.Map;

/**
 * FAPI错误码定义
 * 与FUDP层ResponseMessage状态码保持一致
 * 
 * 码段划分：
 * - 0: 成功
 * - 1: 通用错误
 * - 4xx: 客户端错误（参考HTTP）
 * - 5xx: 服务端错误（参考HTTP）
 */
public final class FapiCode {
    
    // ==================== 成功 ====================
    /** 成功（与FUDP STATUS_SUCCESS一致） */
    public static final int SUCCESS = 0;
    
    // ==================== 通用错误 ====================
    /** 通用错误（与FUDP STATUS_ERROR一致） */
    public static final int ERROR = 1;
    
    // ==================== 客户端错误 4xx ====================
    /** 请求格式错误、参数无效 */
    public static final int BAD_REQUEST = 400;
    
    /** 未授权（预留，FUDP层已处理认证） */
    public static final int UNAUTHORIZED = 401;
    
    /** 余额不足 */
    public static final int PAYMENT_REQUIRED = 402;
    
    /** 禁止访问、信用额度超限 */
    public static final int FORBIDDEN = 403;
    
    /** 数据不存在、组件不存在 */
    public static final int NOT_FOUND = 404;
    
    /** 方法不存在 */
    public static final int METHOD_NOT_ALLOWED = 405;
    
    /** 冲突（如重复提交） */
    public static final int CONFLICT = 409;
    
    /** 资源已删除 */
    public static final int GONE = 410;
    
    /** 请求体过大 */
    public static final int PAYLOAD_TOO_LARGE = 413;
    
    /** 请求过于频繁 */
    public static final int TOO_MANY_REQUESTS = 429;
    
    // ==================== 服务端错误 5xx ====================
    /** 服务器内部错误 */
    public static final int INTERNAL_ERROR = 500;
    
    /** 功能未实现 */
    public static final int NOT_IMPLEMENTED = 501;
    
    /** 外部服务错误（ES、RPC等） */
    public static final int BAD_GATEWAY = 502;
    
    /** 服务不可用（组件未就绪） */
    public static final int SERVICE_UNAVAILABLE = 503;
    
    /** 外部服务超时 */
    public static final int GATEWAY_TIMEOUT = 504;
    
    // ==================== 错误消息 ====================
    private static final Map<Integer, String> MESSAGES = Map.ofEntries(
        Map.entry(SUCCESS, "Success"),
        Map.entry(ERROR, "Error"),
        Map.entry(BAD_REQUEST, "Bad request"),
        Map.entry(UNAUTHORIZED, "Unauthorized"),
        Map.entry(PAYMENT_REQUIRED, "Insufficient balance"),
        Map.entry(FORBIDDEN, "Forbidden"),
        Map.entry(NOT_FOUND, "Not found"),
        Map.entry(METHOD_NOT_ALLOWED, "Method not allowed"),
        Map.entry(CONFLICT, "Conflict"),
        Map.entry(GONE, "Gone"),
        Map.entry(PAYLOAD_TOO_LARGE, "Payload too large"),
        Map.entry(TOO_MANY_REQUESTS, "Too many requests"),
        Map.entry(INTERNAL_ERROR, "Internal server error"),
        Map.entry(NOT_IMPLEMENTED, "Not implemented"),
        Map.entry(BAD_GATEWAY, "Bad gateway"),
        Map.entry(SERVICE_UNAVAILABLE, "Service unavailable"),
        Map.entry(GATEWAY_TIMEOUT, "Gateway timeout")
    );
    
    private FapiCode() {
        // 不允许实例化
    }
    
    /**
     * 获取错误码对应的消息
     */
    public static String getMessage(int code) {
        return MESSAGES.getOrDefault(code, "Unknown error");
    }
    
    /**
     * 判断是否成功
     */
    public static boolean isSuccess(int code) {
        return code == SUCCESS;
    }
    
    /**
     * 判断是否为客户端错误
     */
    public static boolean isClientError(int code) {
        return code >= 400 && code < 500;
    }
    
    /**
     * 判断是否为服务端错误
     */
    public static boolean isServerError(int code) {
        return code >= 500;
    }
}

