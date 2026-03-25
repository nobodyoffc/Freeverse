package fapi.monitor;

import fapi.message.FapiRequest;
import fapi.message.FapiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Set;

/**
 * 审计日志记录器
 * 
 * 记录重要操作的审计日志，支持分布式追踪。
 * 
 * 日志格式：[timestamp] [level] [component] [traceId] [peerId] [api] message
 * 
 * 字段说明：
 * - traceId: 请求追踪ID（使用FapiRequest.id），用于跨组件调用追踪
 * - peerId: 请求方FID
 * - api: API名称
 */
public class AuditLogger {
    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);
    
    /** MDC keys for distributed tracing */
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_PEER_ID = "peerId";
    public static final String MDC_API = "api";
    public static final String MDC_COMPONENT = "component";
    
    /** 需要审计的敏感API（写操作） */
    private static final Set<String> AUDITABLE_APIS = Set.of(
        "disk.put",
        "disk.batchPut",
        "disk.carve",
        "disk.delete",
        "disk.prepareUpload",
        "disk.confirmUpload"
    );
    
    /** 是否启用详细审计 */
    private volatile boolean detailedAuditEnabled = false;
    
    /** 是否记录请求参数 */
    private volatile boolean logRequestParams = false;
    
    private AuditLogger() {
        // 私有构造器
    }
    
    // ==================== 单例模式 ====================
    
    private static final AuditLogger INSTANCE = new AuditLogger();
    
    public static AuditLogger getInstance() {
        return INSTANCE;
    }
    
    // ==================== 核心方法 ====================
    
    /**
     * 设置MDC上下文（用于日志追踪）
     */
    public static void setContext(FapiRequest request, String peerId) {
        if (request != null) {
            MDC.put(MDC_TRACE_ID, request.getId() != null ? request.getId() : "-");
            MDC.put(MDC_API, request.getApi() != null ? request.getApi() : "-");
            MDC.put(MDC_COMPONENT, request.getComponentName() != null ? request.getComponentName() : "-");
        }
        MDC.put(MDC_PEER_ID, peerId != null ? peerId : "-");
    }
    
    /**
     * 清除MDC上下文
     */
    public static void clearContext() {
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_PEER_ID);
        MDC.remove(MDC_API);
        MDC.remove(MDC_COMPONENT);
    }
    
    /**
     * 记录请求开始
     */
    public void logRequestStart(FapiRequest request, String peerId) {
        if (request == null) return;
        
        setContext(request, peerId);
        
        String api = request.getApi();
        if (shouldAudit(api)) {
            log.info("[AUDIT] Request started: {} from {}", api, maskPeerId(peerId));
            
            if (logRequestParams && request.getParams() != null) {
                log.debug("[AUDIT] Request params: {}", request.getParams());
            }
        } else if (detailedAuditEnabled) {
            log.debug("[AUDIT] Request: {} from {}", api, maskPeerId(peerId));
        }
    }
    
    /**
     * 记录请求完成
     */
    public void logRequestComplete(FapiRequest request, FapiResponse response, String peerId, long durationMs) {
        if (request == null) return;
        
        String api = request.getApi();
        int code = response != null && response.getCode() != null ? response.getCode() : -1;
        boolean success = code == 0;
        
        if (shouldAudit(api)) {
            if (success) {
                log.info("[AUDIT] Request completed: {} from {} ({}ms)", 
                    api, maskPeerId(peerId), durationMs);
            } else {
                log.warn("[AUDIT] Request failed: {} from {} code={} ({}ms)", 
                    api, maskPeerId(peerId), code, durationMs);
            }
        } else if (detailedAuditEnabled) {
            log.debug("[AUDIT] Completed: {} code={} ({}ms)", api, code, durationMs);
        }
        
        clearContext();
    }
    
    /**
     * 记录请求错误
     */
    public void logRequestError(FapiRequest request, String peerId, Throwable error) {
        String api = request != null ? request.getApi() : "unknown";
        
        log.error("[AUDIT] Request error: {} from {} - {}", 
            api, maskPeerId(peerId), error.getMessage(), error);
        
        clearContext();
    }
    
    /**
     * 记录重要业务事件
     */
    public void logEvent(String event, String peerId, String details) {
        log.info("[AUDIT] Event: {} peer={} - {}", event, maskPeerId(peerId), details);
    }
    
    /**
     * 记录安全事件
     */
    public void logSecurityEvent(String event, String peerId, String details) {
        log.warn("[SECURITY] {} peer={} - {}", event, maskPeerId(peerId), details);
    }
    
    /**
     * 记录余额变化
     */
    public void logBalanceChange(String peerId, long oldBalance, long newBalance, String reason) {
        long delta = newBalance - oldBalance;
        if (delta != 0) {
            log.info("[AUDIT] Balance change: peer={} delta={} ({} -> {}) reason={}",
                maskPeerId(peerId), delta, oldBalance, newBalance, reason);
        }
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 判断是否需要审计
     */
    public boolean shouldAudit(String api) {
        if (api == null) return false;
        
        // 敏感API总是审计
        if (AUDITABLE_APIS.contains(api)) {
            return true;
        }
        
        // 详细模式下审计所有请求
        return detailedAuditEnabled;
    }
    
    /**
     * 掩码PeerId（安全考虑）
     */
    private String maskPeerId(String peerId) {
        if (peerId == null || peerId.length() < 10) {
            return peerId;
        }
        // 显示前6位和后4位，中间用...替代
        return peerId.substring(0, 6) + "..." + peerId.substring(peerId.length() - 4);
    }
    
    // ==================== 配置方法 ====================
    
    public void setDetailedAuditEnabled(boolean enabled) {
        this.detailedAuditEnabled = enabled;
        log.info("Detailed audit {}", enabled ? "enabled" : "disabled");
    }
    
    public boolean isDetailedAuditEnabled() {
        return detailedAuditEnabled;
    }
    
    public void setLogRequestParams(boolean enabled) {
        this.logRequestParams = enabled;
    }
    
    public boolean isLogRequestParams() {
        return logRequestParams;
    }
    
    /**
     * 添加需要审计的API
     */
    public static boolean isAuditableApi(String api) {
        return AUDITABLE_APIS.contains(api);
    }
}

