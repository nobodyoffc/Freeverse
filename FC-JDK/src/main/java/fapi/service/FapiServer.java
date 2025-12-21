package fapi.service;

import clients.ApipClient;
import config.ApiAccount;
import config.Configure;
import config.Settings;
import constants.CodeMessage;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import data.feipData.Service;
import data.feipData.serviceParams.Params;
import fapi.handler.FcFudpRequestHandler;
import fapi.util.ResponseBuilder;
import fudp.message.ResponseMessage;
import fudp.node.FudpNode;
import fudp.node.NodeEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.serviceManagers.ServiceManager;
import utils.JsonUtils;
import utils.ObjectUtils;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static constants.FieldNames.*;

/**
 * FAPI 服务主类
 * 继承 ServiceManager 提供服务管理功能，同时实现 NodeEventListener 接口处理 FUDP 请求
 */
public class FapiServer extends ServiceManager implements NodeEventListener {
    private static final Logger log = LoggerFactory.getLogger(FapiServer.class);
    
    private FudpNode fudpNode;
    private final FcFudpRequestHandler requestHandler;
    private final Map<String, Service> serviceMap;
    private final Settings settings;
    
    /**
     * 构造函数（用于 ServiceManager）
     * @param service 服务对象
     * @param apipAccount APIP 账户（FAPI 不需要，可为 null）
     * @param br 输入流
     * @param symKey 对称密钥
     * @param paramsClass 参数类型（使用 Params.class）
     * @param settings Settings 对象（用于获取其他依赖）
     */
    public FapiServer(Service service, ApiAccount apipAccount, BufferedReader br, byte[] symKey, Class<?> paramsClass, Settings settings) {
        super(service, apipAccount, br, symKey, paramsClass);
        this.settings = settings;
        this.requestHandler = new FcFudpRequestHandler(settings);
        this.serviceMap = new HashMap<>();
    }
    
    /**
     * 便捷构造函数（从 Settings 创建，用于向后兼容）
     * 注意：此构造函数创建的实例不能直接使用 ServiceManager 的 menu() 方法
     * 需要使用带 Service 参数的构造函数
     */
    public FapiServer(Settings settings) {
        // 调用父类构造函数，使用临时值（这些值在创建后会被正确设置）
        super(null, null, null, null, Params.class);
        this.settings = settings;
        this.requestHandler = new FcFudpRequestHandler(settings);
        this.serviceMap = new HashMap<>();
        // 从 settings 获取 service 并设置
        if (settings != null) {
            this.service = settings.getService();
            this.symKey = settings.getSymkey();
        }
    }
    
    /**
     * 设置 FudpNode（在启动时调用）
     */
    public void setFudpNode(FudpNode fudpNode) {
        this.fudpNode = fudpNode;
        if (fudpNode != null && fudpNode.getConfig() != null) {
            fudpNode.getConfig().setPongDataProvider(this::buildPongAdvertiseData);
        }
    }
    
    /**
     * 初始化服务
     * 从 Elasticsearch 加载当前身份注册的所有 FAPI 服务
     */
    public void initialize() {
        log.info("Initializing FAPI service...");
        serviceMap.clear();
        
        // 从 ES 获取当前身份的所有 FAPI 服务
        String dealerFid = settings.getMainFid();
        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);

        if (this.service != null) {
            serviceMap.put(this.service.getId(), this.service);
        }else {
            List<Service> services = Configure.getServiceListByDealerFromEs(
                    dealerFid,
                    esClient
            );

            if (services != null) {
                for (Service service : services) {
                    // 验证 Service.types 包含 "FAPI"
                    if (service.getTypes() != null &&
                            Arrays.asList(service.getTypes()).contains("FAPI")) {
                        serviceMap.put(service.getId(), service);
                        log.info("Loaded FAPI service: sid={}, name={}",
                                service.getId(), service.getStdName());
                    }
                }
            }
        }

        log.info("FAPI service initialized. Total services: {}", serviceMap.size());
    }
    
    /**
     * 更新服务信息（手动触发）
     */
    public void updateServices() {
        log.info("Updating FAPI services...");
        serviceMap.clear();
        initialize();
        log.info("FAPI services updated. Total: {}", serviceMap.size());
    }
    
    @Override
    public void onRequestReceived(String peerId, long requestId, String serviceName, byte[] data) {
        log.debug("Received FAPI request: peerId={}, requestId={}, serviceName={}", 
            peerId, requestId, serviceName);
        
        // 1. 验证 sid 并获取 Service 对象
        Service service = serviceMap.get(serviceName);
        if (service == null || 
            service.getTypes() == null || 
            !Arrays.asList(service.getTypes()).contains("FAPI")) {
            log.warn("Service not found or type mismatch: sid={}", serviceName);
            byte[] errorData = ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1011DataNotFound,
                "Service not found or type mismatch",
                settings,
                peerId
            );
            try {
                if (fudpNode != null) {
                    fudpNode.respond(peerId, requestId, ResponseMessage.STATUS_NOT_FOUND, errorData);
                }
            } catch (Exception e) {
                log.error("Failed to send error response", e);
            }
            return;
        }
        
        // 2. 处理请求（FUDP 协议层已经在独立线程中调用此方法，直接处理即可）
        try {
            byte[] responseData = requestHandler.handleRequest(service, peerId, data);
            if (fudpNode != null) {
                fudpNode.respond(peerId, requestId, ResponseMessage.STATUS_SUCCESS, responseData);
                log.debug("FAPI request processed successfully: peerId={}, requestId={}", 
                    peerId, requestId);
            } else {
                log.error("FudpNode is not set, cannot send response");
            }
        } catch (Exception e) {
            log.error("Failed to handle FAPI request", e);
            byte[] errorData = ResponseBuilder.buildErrorResponse(
                CodeMessage.Code1020OtherError,
                "Internal server error",
                settings,
                peerId
            );
            try {
                if (fudpNode != null) {
                    fudpNode.respond(peerId, requestId, ResponseMessage.STATUS_INTERNAL_ERROR, errorData);
                }
            } catch (Exception ex) {
                log.error("Failed to send error response", ex);
            }
        }
    }
    
    /**
     * 获取服务映射（用于测试和调试）
     */
    public Map<String, Service> getServiceMap() {
        return serviceMap;
    }

    /**
     * Build optional pong data advertising available FAPI services.
     */
    private byte[] buildPongAdvertiseData(String peerId) {
        try {
            if (serviceMap.isEmpty()) {
                log.debug("buildPongAdvertiseData: no services to advertise for {}", peerId);
                return new byte[0];
            }
            List<Map<String, Object>> services = new ArrayList<>();
            for (Service service : serviceMap.values()) {
                Map<String, Object> item = new HashMap<>();
                item.put(SID, service.getId());
                item.put(TYPES, service.getTypes());
                item.put(VER, service.getVer());
                item.put(DEALER_PUBKEY, service.getDealerPubkey());
                // params 可能是反序列化后的 Map，确保安全转换以避免构建 PONG 时抛出异常
                Params params = null;
                try {
                    Object rawParams = service.getParams();
                    if (rawParams instanceof Params p) {
                        params = p;
                    } else if (rawParams != null) {
                        params = ObjectUtils.objectToClass(rawParams, Params.class);
                    }
                } catch (Exception e) {
                    log.warn("Failed to convert params for service {}: {}", service.getId(), e.getMessage());
                }
                if (params != null) {
                    item.put(PRICE_PER_K_Bytes, params.getPricePerKB());
                    item.put(MIN_PAYMENT, params.getMinPayment());
                }
                services.add(item);
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put(SERVICES, services);
            String json = JsonUtils.toJson(payload);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            log.debug("buildPongAdvertiseData: advertising {} services ({} bytes) to {}", services.size(), bytes.length, peerId);
            return bytes;
        } catch (Exception e) {
            log.warn("buildPongAdvertiseData: failed for {}: {}", peerId, e.getMessage());
            return new byte[0];
        }
    }
    
    /**
     * 实现 ServiceManager 的抽象方法：输入参数
     */
    @Override
    protected Params inputParams(byte[] symKey, BufferedReader br) {
        Params params = new Params();
        ApipClient apipClient = null;
        if (apipAccount != null) {
            apipClient = (ApipClient) apipAccount.getClient();
        }
        params.inputParams(br, symKey, apipClient);
        return params;
    }
    
    /**
     * 实现 ServiceManager 的抽象方法：更新参数
     */
    @Override
    protected Params updateParams(Params serviceParams, BufferedReader br, byte[] symKey) {
        ApipClient apipClient = null;
        if (apipAccount != null) {
            apipClient = (ApipClient) apipAccount.getClient();
        }
        serviceParams.updateParams(br, symKey, apipClient);
        return serviceParams;
    }
}
