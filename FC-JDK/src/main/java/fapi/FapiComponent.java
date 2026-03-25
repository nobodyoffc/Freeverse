package fapi;

import fapi.message.FapiRequest;
import fapi.message.FapiResponse;
import fapi.message.UnifiedCodec.UnifiedResponse;
import fapi.service.FapiServer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * FAPI服务组件接口
 * 所有业务服务（BASE、DISK、MAP等）都实现此接口
 * 
 * 设计说明：
 * - 认证已在FUDP层完成，peerId即为已验证的用户身份
 * - 可继承AbstractFapiComponent简化实现
 */
public interface FapiComponent {
    
    /**
     * 组件生命周期状态
     */
    enum State {
        /** 已创建，未初始化 */
        CREATED,
        /** 正在初始化 */
        INITIALIZING,
        /** 正常运行 */
        RUNNING,
        /** 正在停止 */
        STOPPING,
        /** 已停止 */
        STOPPED
    }
    
    /**
     * 获取组件名称（对应service.types中的类型）
     * 例如：BASE、DISK、MAP
     */
    String getName();
    
    /**
     * 获取组件支持的API列表
     * 例如：["base.getByIds", "base.search", "base.health"]
     */
    List<String> getApiList();
    
    /**
     * 获取当前状态
     */
    default State getState() {
        return State.RUNNING;
    }
    
    /**
     * 健康检查
     */
    default boolean isHealthy() {
        return getState() == State.RUNNING;
    }
    
    /**
     * 初始化组件
     * @param server FAPI服务器实例，可获取其他组件引用和配置
     */
    void initialize(FapiServer server);
    
    /**
     * 处理请求（同步）
     * @param request FAPI请求
     * @param peerId 请求方FID（已通过FUDP层认证）
     * @return FAPI响应
     */
    FapiResponse handleRequest(FapiRequest request, String peerId);
    
    /**
     * 检查指定方法是否返回二进制响应
     * 用于某些API需要返回原始二进制数据（如文件下载）
     * 
     * @param method 方法名（不含组件前缀，如 "get"）
     * @return true 如果该方法返回二进制响应
     */
    default boolean returnsBinaryResponse(String method) {
        return false;
    }
    
    /**
     * 处理需要返回二进制的请求
     * 只在 returnsBinaryResponse(method) 返回 true 时被调用
     * 
     * @param request FAPI请求
     * @param peerId 请求方FID
     * @return 原始二进制响应数据
     * @deprecated 使用 handleUnifiedRequest 替代
     */
    @Deprecated
    default byte[] handleBinaryRequest(FapiRequest request, String peerId) {
        throw new UnsupportedOperationException("Binary response not supported");
    }
    
    /**
     * 处理统一请求（支持二进制数据输入和输出）
     * <p>
     * 这是新的统一接口，支持：
     * - 请求中携带二进制数据（如文件上传）
     * - 响应中携带二进制数据（如文件下载）
     * 
     * @param request FAPI请求
     * @param binaryData 可选的二进制数据（来自请求）
     * @param peerId 请求方FID（已通过FUDP层认证）
     * @return 统一响应（包含 FapiResponse 和可选的二进制数据）
     */
    default UnifiedResponse handleUnifiedRequest(FapiRequest request, byte[] binaryData, String peerId) {
        // 默认实现：忽略二进制数据，调用原有方法，返回无二进制数据的响应
        FapiResponse response = handleRequest(request, peerId);
        return new UnifiedResponse(response, null);
    }
    
    /**
     * 检查指定方法是否在响应中返回二进制数据
     * 
     * @param method 方法名（不含组件前缀，如 "get"）
     * @return true 如果该方法在响应中包含二进制数据
     */
    default boolean returnsBinaryData(String method) {
        return false;
    }
    
    /**
     * 处理请求（异步）- 适用于I/O密集型操作
     * 默认实现：包装同步方法
     */
    default CompletableFuture<FapiResponse> handleRequestAsync(FapiRequest request, String peerId) {
        return CompletableFuture.supplyAsync(() -> handleRequest(request, peerId));
    }
    
    /**
     * 是否支持异步处理
     * 返回true时，FapiServer优先使用handleRequestAsync
     */
    default boolean supportsAsync() {
        return false;
    }
    
    /**
     * 关闭组件，释放资源
     * @param timeoutMs 超时时间（毫秒）
     */
    default void close(long timeoutMs) throws InterruptedException {
        // 默认空实现
    }
    
    /**
     * 关闭组件（使用默认超时）
     */
    default void close() {
        try {
            close(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

