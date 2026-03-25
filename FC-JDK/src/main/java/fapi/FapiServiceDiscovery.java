package fapi;

import data.apipData.Fcdsl;
import data.feipData.Service;
import fapi.client.FapiClient;
import fapi.client.FapiClient.DiscoveryResult;
import fudp.node.FudpNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * FAPI服务发现工具类
 * 
 * 提供自动发现FAPI服务的功能：
 * 1. 尝试默认服务端点连接
 * 2. 查询链上FAPI服务商列表
 * 3. 支持手动指定端点
 */
public final class FapiServiceDiscovery {
    private static final Logger log = LoggerFactory.getLogger(FapiServiceDiscovery.class);
    
    private FapiServiceDiscovery() {
        // 工具类，禁止实例化
    }
    
    /**
     * 尝试通过默认端点发现服务
     * 
     * @param node FudpNode实例
     * @return 发现结果，如果全部失败返回null
     */
    public static DiscoveryResult discoverViaDefaults(FudpNode node) {
        return discoverViaDefaults(node, 
                FapiDefaults.DEFAULT_HELLO_TIMEOUT_MS, 
                FapiDefaults.DEFAULT_PING_TIMEOUT_MS);
    }
    
    /**
     * 尝试通过默认端点发现服务（自定义超时）
     * 
     * @param node FudpNode实例
     * @param helloTimeoutMs HELLO超时毫秒
     * @param pingTimeoutMs PING超时毫秒
     * @return 发现结果，如果全部失败返回null
     */
    public static DiscoveryResult discoverViaDefaults(FudpNode node, long helloTimeoutMs, long pingTimeoutMs) {
        for (String endpoint : FapiDefaults.DEFAULT_ENDPOINTS) {
            try {
                String host = FapiDefaults.getHost(endpoint);
                int port = FapiDefaults.getPort(endpoint);
                
                log.info("Trying default endpoint: {}:{}", host, port);
                
                DiscoveryResult result = FapiClient.discoverViaHelloAndPing(
                        node, host, port, helloTimeoutMs, pingTimeoutMs);
                
                if (result.getServices() != null && !result.getServices().isEmpty()) {
                    log.info("Connected to {} - found {} service(s)", endpoint, result.getServices().size());
                    return result;
                } else {
                    log.debug("No FAPI services found at {}", endpoint);
                }
            } catch (Throwable e) {
                log.debug("Failed to connect to {}: {}", endpoint, e.getMessage());
            }
        }
        
        log.warn("All default endpoints failed");
        return null;
    }
    
    /**
     * 尝试连接指定端点
     * 
     * @param node FudpNode实例
     * @param host 主机
     * @param port 端口
     * @return 发现结果，失败返回null
     */
    public static DiscoveryResult discoverViaEndpoint(FudpNode node, String host, int port) {
        try {
            return FapiClient.discoverViaHelloAndPing(
                    node, host, port,
                    FapiDefaults.DEFAULT_HELLO_TIMEOUT_MS,
                    FapiDefaults.DEFAULT_PING_TIMEOUT_MS);
        } catch (Throwable e) {
            log.warn("Failed to connect to {}:{}: {}", host, port, e.getMessage());
            return null;
        }
    }
    
    /**
     * 尝试连接指定端点字符串
     * 
     * @param node FudpNode实例
     * @param endpoint 端点字符串 "host:port" 或 "host"
     * @return 发现结果，失败返回null
     */
    public static DiscoveryResult discoverViaEndpoint(FudpNode node, String endpoint) {
        String host = FapiDefaults.getHost(endpoint);
        int port = FapiDefaults.getPort(endpoint);
        return discoverViaEndpoint(node, host, port);
    }
    
    /**
     * 获取链上FAPI服务商列表
     * 
     * @param client 已连接的FapiClient
     * @return FAPI服务列表，如果查询失败返回空列表
     */
    public static List<Service> fetchFapiProviders(FapiClient client) {
        return fetchFapiProviders(client, 20);
    }
    
    /**
     * 获取链上FAPI服务商列表
     * 
     * @param client 已连接的FapiClient
     * @param limit 最大返回数量
     * @return FAPI服务列表，如果查询失败返回空列表
     */
    public static List<Service> fetchFapiProviders(FapiClient client, int limit) {
        if (client == null) {
            return Collections.emptyList();
        }
        
        try {
            Fcdsl fcdsl = new Fcdsl();
            fcdsl.setEntity("service");
            
            // 过滤 type 为 FAPI 的服务
            fcdsl.addNewFilter()
                    .addNewTerms()
                    .addNewFields("type")
                    .addNewValues("FAPI");
            
            // 按最后更新高度降序
            fcdsl.addSort("lastHeight", "desc");
            
            fcdsl.addSize(limit);
            
            List<Service> services = client.entitySearch("service", fcdsl, Service.class);
            
            if (services != null) {
                log.info("Found {} FAPI providers on chain", services.size());
                return services;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch FAPI providers: {}", e.getMessage());
        }
        
        return Collections.emptyList();
    }
    
    /**
     * 过滤出活跃的FAPI服务商
     * 
     * @param services 服务列表
     * @return 活跃服务列表（状态为active）
     */
    public static List<Service> filterActiveProviders(List<Service> services) {
        if (services == null || services.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<Service> active = new ArrayList<>();
        for (Service service : services) {
            if (service != null && !service.isClosed()) {
                active.add(service);
            }
        }
        return active;
    }
    
    /**
     * 根据服务创建FapiClient
     * 
     * @param node FudpNode实例
     * @param service 链上服务信息
     * @return FapiClient实例，如果连接失败返回null
     */
    public static FapiClient connectToService(FudpNode node, Service service) {
        if (service == null || service.getApiUrl() == null) {
            log.warn("Service or apiUrl is null");
            return null;
        }
        
        String apiUrl = service.getApiUrl();
        String host = FapiDefaults.getHost(apiUrl);
        int port = FapiDefaults.getPort(apiUrl);
        
        DiscoveryResult result = discoverViaEndpoint(node, host, port);
        if (result != null && result.getServices() != null && !result.getServices().isEmpty()) {
            return new FapiClient(node, result.getPeerId(), service.getId(), 
                    FapiDefaults.DEFAULT_REQUEST_TIMEOUT_SEC);
        }
        
        return null;
    }
}

