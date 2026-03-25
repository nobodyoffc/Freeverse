package fapi.monitor;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import clients.NaSaClient.NaSaRpcClient;
import config.Settings;
import data.feipData.ServiceType;
import fapi.FapiComponent;
import fapi.service.FapiServer;
import fudp.node.FudpNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查器
 * 
 * 提供服务健康状态检查功能，通过 base.health API 暴露。
 * 
 * 检查内容：
 * 1. 各组件状态
 * 2. 外部依赖连接（ES、RPC）
 * 3. FUDP节点状态
 */
public class HealthChecker {
    private static final Logger log = LoggerFactory.getLogger(HealthChecker.class);
    
    private final FapiServer server;
    private final Settings settings;
    
    public HealthChecker(FapiServer server) {
        this.server = server;
        this.settings = server != null ? server.getSettings() : null;
    }
    
    /**
     * 执行健康检查
     */
    public HealthStatus check() {
        HealthStatus status = new HealthStatus();
        status.setTimestamp(System.currentTimeMillis());
        
        try {
            // 检查各组件状态
            checkComponents(status);
            
            // 检查外部依赖
            checkExternalDependencies(status);
            
            // 检查FUDP节点
            checkFudpNode(status);
            
            // 汇总状态
            boolean componentsHealthy = status.getComponents().values().stream()
                .allMatch(ComponentHealth::isHealthy);
            
            status.setOverallHealthy(
                componentsHealthy && 
                (status.isEsConnected() || !status.isEsRequired())
            );
            
        } catch (Exception e) {
            log.error("Health check failed", e);
            status.setOverallHealthy(false);
            status.setErrorMessage(e.getMessage());
        }
        
        return status;
    }
    
    /**
     * 快速健康检查（只检查核心状态）
     */
    public boolean isHealthy() {
        try {
            // 快速检查：服务器和FUDP节点是否正常
            if (server == null) return false;
            
            FudpNode fudpNode = server.getFudpNode();
            if (fudpNode == null || !fudpNode.isRunning()) return false;
            
            // 检查所有组件是否健康
            for (FapiComponent component : server.getComponents()) {
                if (!component.isHealthy()) {
                    return false;
                }
            }
            
            return true;
        } catch (Exception e) {
            log.warn("Quick health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查各组件状态
     */
    private void checkComponents(HealthStatus status) {
        Map<String, ComponentHealth> componentHealth = new HashMap<>();
        
        if (server == null || server.getComponents() == null) {
            status.setComponents(componentHealth);
            return;
        }
        
        for (FapiComponent component : server.getComponents()) {
            ComponentHealth health = new ComponentHealth();
            health.setName(component.getName());
            health.setState(component.getState().name());
            health.setHealthy(component.isHealthy());
            health.setApiCount(component.getApiList() != null ? component.getApiList().size() : 0);
            componentHealth.put(component.getName(), health);
        }
        
        status.setComponents(componentHealth);
    }
    
    /**
     * 检查外部依赖
     */
    private void checkExternalDependencies(HealthStatus status) {
        // 检查ES连接
        status.setEsConnected(checkEsConnection());
        status.setEsRequired(isEsRequired());
        
        // 检查RPC连接
        status.setRpcConnected(checkRpcConnection());
    }
    
    /**
     * 检查FUDP节点
     */
    private void checkFudpNode(HealthStatus status) {
        if (server == null) {
            status.setFudpRunning(false);
            status.setConnectedPeers(0);
            return;
        }
        
        FudpNode fudpNode = server.getFudpNode();
        if (fudpNode != null) {
            status.setFudpRunning(fudpNode.isRunning());
            status.setLocalFid(fudpNode.getLocalFid());
            status.setPort(fudpNode.getConfig().getPort());
            
            // 获取连接的对等节点数量
            try {
                status.setConnectedPeers(fudpNode.listPeers().size());
            } catch (Exception e) {
                status.setConnectedPeers(0);
            }
        } else {
            status.setFudpRunning(false);
            status.setConnectedPeers(0);
        }
    }
    
    /**
     * 检查ES连接
     */
    private boolean checkEsConnection() {
        if (settings == null) return false;
        
        try {
            Object client = settings.getClient(ServiceType.ES);
            if (client instanceof ElasticsearchClient esClient) {
                // 尝试一个简单的ping操作
                return esClient.ping().value();
            }
        } catch (Exception e) {
            log.debug("ES connection check failed: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * 检查RPC连接
     */
    private boolean checkRpcConnection() {
        if (settings == null) return false;
        
        try {
            Object client = settings.getClient(ServiceType.NASA_RPC);
            if (client instanceof NaSaRpcClient rpcClient) {
                // 尝试获取最佳区块高度
                rpcClient.freshBestBlock();
                return rpcClient.getBestHeight() > 0;
            }
        } catch (Exception e) {
            log.debug("RPC connection check failed: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * 判断ES是否必需
     */
    private boolean isEsRequired() {
        if (server == null) return false;
        
        // 如果有BASE组件，则ES是必需的
        for (FapiComponent component : server.getComponents()) {
            if ("BASE".equalsIgnoreCase(component.getName())) {
                return true;
            }
        }
        return false;
    }
}

