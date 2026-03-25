package fapi;

/**
 * FAPI客户端默认配置
 * 
 * 提供默认的服务端点和超时配置，简化客户端启动流程。
 * 客户端启动时会按顺序尝试这些默认端点，成功后可查询链上服务商列表供用户选择。
 */
public final class FapiDefaults {
    
    private FapiDefaults() {
        // 工具类，禁止实例化
    }
    
    /**
     * 默认FAPI服务端点列表
     * 按优先级顺序排列：
     * 1. 本地服务（开发调试）
     * 2. 公共服务（生产环境）
     */
    public static final String[] DEFAULT_ENDPOINTS = {
        "fudp://127.0.0.1:8500",       // 本地服务（开发调试）
        "fudp://fapi.cid.cash:8500",   // 公共服务（生产环境）
        "fudp://fapi.apip.cash:8500"    // 公共服务（生产环境）
    };
    
    /**
     * 默认FAPI服务端口
     */
    public static final int DEFAULT_FAPI_PORT = 8500;
    
    /**
     * 服务发现超时时间（毫秒）
     */
    public static final int DEFAULT_DISCOVERY_TIMEOUT_MS = 5000;
    
    /**
     * HELLO握手超时时间（毫秒）
     */
    public static final int DEFAULT_HELLO_TIMEOUT_MS = 5000;
    
    /**
     * PING超时时间（毫秒）
     */
    public static final int DEFAULT_PING_TIMEOUT_MS = 5000;
    
    /**
     * 默认请求超时时间（秒）
     */
    public static final int DEFAULT_REQUEST_TIMEOUT_SEC = 30;
    
    /**
     * 默认FAPI客户端端口
     */
    public static final int DEFAULT_FAPI_CLIENT_PORT = 8501;
    
    /**
     * 端口绑定失败时最大重试次数
     */
    public static final int MAX_PORT_RETRY_COUNT = 20;
    
    /**
     * 解析端点字符串
     * @param endpoint 格式为 "host:port"、"host" 或 "protocol://host:port" (如 "fudp://host:port")
     * @return [host, port] 数组
     */
    public static String[] parseEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        
        String trimmed = endpoint.trim();
        
        // 移除协议前缀（如果存在），通过查找 "://" 来识别
        int protocolIndex = trimmed.indexOf("://");
        if (protocolIndex != -1) {
            trimmed = trimmed.substring(protocolIndex + 3); // 跳过 "://"
        }
        
        String[] parts = trimmed.split(":");
        String host = parts[0];
        String port = parts.length > 1 ? parts[1] : String.valueOf(DEFAULT_FAPI_PORT);
        
        return new String[] { host, port };
    }
    
    /**
     * 解析端点获取主机
     */
    public static String getHost(String endpoint) {
        String[] parts = parseEndpoint(endpoint);
        return parts != null ? parts[0] : null;
    }
    
    /**
     * 解析端点获取端口
     */
    public static int getPort(String endpoint) {
        String[] parts = parseEndpoint(endpoint);
        if (parts == null) {
            return DEFAULT_FAPI_PORT;
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return DEFAULT_FAPI_PORT;
        }
    }
}

