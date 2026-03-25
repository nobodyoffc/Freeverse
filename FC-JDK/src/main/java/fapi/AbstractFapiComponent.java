package fapi;

import config.Settings;
import fapi.message.FapiRequest;
import fapi.message.FapiResponse;
import fapi.service.FapiServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JsonUtils;

/**
 * 组件抽象基类
 * 提供通用实现，简化组件开发
 */
public abstract class AbstractFapiComponent implements FapiComponent {
    
    protected final Logger log = LoggerFactory.getLogger(getClass());
    
    protected FapiServer server;
    protected Settings settings;
    protected volatile State state = State.CREATED;
    
    @Override
    public State getState() {
        return state;
    }
    
    @Override
    public void initialize(FapiServer server) {
        this.state = State.INITIALIZING;
        this.server = server;
        this.settings = server.getSettings();
        
        log.info("Initializing component: {}", getName());
        
        try {
            doInitialize();
            this.state = State.RUNNING;
            log.info("Component {} initialized successfully", getName());
        } catch (Exception e) {
            this.state = State.STOPPED;
            log.error("Failed to initialize component {}: {}", getName(), e.getMessage(), e);
            throw new RuntimeException("Component initialization failed: " + getName(), e);
        }
    }
    
    /**
     * 子类实现具体初始化逻辑
     */
    protected abstract void doInitialize();
    
    /**
     * 类型安全的组件获取
     */
    protected <T extends FapiComponent> T getComponent(Class<T> componentClass) {
        return server.getComponent(componentClass);
    }
    
    /**
     * 按名称获取组件
     */
    protected FapiComponent getComponent(String name) {
        return server.getComponent(name);
    }
    
    @Override
    public void close(long timeoutMs) throws InterruptedException {
        if (state == State.STOPPED || state == State.STOPPING) {
            return;
        }
        
        state = State.STOPPING;
        log.info("Stopping component: {}", getName());
        
        try {
            doClose(timeoutMs);
            log.info("Component {} stopped", getName());
        } finally {
            state = State.STOPPED;
        }
    }
    
    /**
     * 子类实现具体关闭逻辑
     */
    protected void doClose(long timeoutMs) throws InterruptedException {
        // 默认空实现
    }
    
    /**
     * 组件间调用：调用其他组件处理请求
     * 
     * @param componentName 目标组件名称
     * @param request 请求
     * @param peerId 请求方ID
     * @return 响应
     */
    protected FapiResponse callComponent(String componentName, FapiRequest request, String peerId) {
        FapiComponent target = server.getComponent(componentName);
        if (target == null) {
            return FapiResponse.error(request.getId(), FapiCode.NOT_FOUND, 
                "Component not found: " + componentName);
        }
        return target.handleRequest(request, peerId);
    }
    
    /**
     * 解析参数为指定类型
     * 
     * @param params 原始参数
     * @param clazz 目标类型
     * @return 解析后的对象，失败返回null
     */
    @SuppressWarnings("unchecked")
    protected <T> T parseParams(Object params, Class<T> clazz) {
        if (params == null) {
            return null;
        }
        if (clazz.isInstance(params)) {
            return (T) params;
        }
        try {
            return JsonUtils.fromJson(JsonUtils.toJson(params), clazz);
        } catch (Exception e) {
            log.warn("Failed to parse params to {}: {}", clazz.getSimpleName(), e.getMessage());
            return null;
        }
    }
    
    /**
     * 创建错误响应的便捷方法
     */
    protected FapiResponse errorResponse(String requestId, int code, String message) {
        return FapiResponse.error(requestId, code, message);
    }
    
    /**
     * 创建错误响应的便捷方法（使用默认消息）
     */
    protected FapiResponse errorResponse(String requestId, int code) {
        return FapiResponse.error(requestId, code);
    }
    
    /**
     * 创建成功响应的便捷方法
     */
    protected FapiResponse successResponse(String requestId, Object data) {
        return FapiResponse.success(requestId, data);
    }
    
    /**
     * 创建成功响应的便捷方法（带分页）
     */
    protected FapiResponse successResponse(String requestId, Object data, Long got, Long total) {
        FapiResponse response = FapiResponse.success(requestId, data);
        response.setGot(got);
        response.setTotal(total);
        return response;
    }
}

