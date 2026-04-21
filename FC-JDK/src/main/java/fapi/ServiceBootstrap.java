package fapi;

import config.Configure;
import config.Settings;
import data.feipData.ApiGroupType;
import data.feipData.Service;
import fapi.service.FapiServer;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.BindException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * FAPI服务统一启动器
 * <p>
 * 提供简化的服务启动流程，替代原有复杂的 Starter 类。
 * <p>
 * 主要职责：
 * 1. 加载配置和认证
 * 2. 初始化必需的外部服务客户端
 * 3. 加载链上服务信息
 * 4. 创建和启动 FapiServer
 */
public class ServiceBootstrap {
    private static final Logger log = LoggerFactory.getLogger(ServiceBootstrap.class);
    
    private ServiceBootstrap() {
        // 工具类，禁止实例化
    }
    
    /**
     * 启动FAPI服务端
     * 
     * @param config 启动配置
     * @return FapiServer实例，失败返回null
     */
    public static FapiServer bootstrapServer(ServiceBootstrapStarter config) {
        BufferedReader br = config.getBr();
        if (br == null) {
            log.error("BufferedReader is required");
            return null;
        }
        
        try {
            // 1. 加载配置文件并验证密码
            Configure.loadConfig(br);
            Configure configure = Configure.checkPassword(br);
            if (configure == null) {
                log.error("Failed to authenticate");
                return null;
            }
            byte[] symkey = configure.getSymkey();
            
            // 2. 选择服务
            String sid = configure.chooseSid(config.getServiceType());
            
            // 3. 加载或创建 Settings
            Settings settings = loadOrCreateSettings(sid, config, configure);

            // 4. 初始化必需的外部服务（ES, NASA_RPC 等）
            settings.setBootstrapping(true);
            initializeExternalServices(settings, symkey, configure);
            settings.setBootstrapping(false);

            // 5. 加载链上服务信息
            Service service = settings.loadMyService(sid, symkey, configure);
            if (service == null) {
                log.error("Failed to load service");
                return null;
            }
            
            String[] componentTypes = mergeComponentTypes(
                config.getComponentTypes(), 
                service.getComponents()
            );
            
            // 7. 创建 FapiServer（会自动初始化 FapiBalanceManager）
            FapiServer server = new FapiServer(service, br, symkey, settings);
            server.initialize();
            
            // 8. 加载组件
            server.loadComponentsByTypes(componentTypes);
            
            // 9. 创建并启动 FudpNode
            FudpNode fudpNode = createAndStartFudpNode(settings, service, server);
            if (fudpNode == null) {
                log.error("Failed to start FUDP node");
                return null;
            }
            server.setFudpNode(fudpNode);
            
            // 10. 配置余额管理监听（使用 FapiServer 内置的 FapiBalanceManager）
            setupBalanceListener(fudpNode, server);

            System.out.println("\nFAPI Server started successfully");
            System.out.println("  Service SID: "+ service.getId());
            System.out.println("  Local FID: "+fudpNode.getLocalFid());
            System.out.println("  Port: "+fudpNode.getConfig().getPort());
            System.out.println("  Components:"+ String.join(", ", componentTypes));
            System.out.println("  BalanceManager:"+(server.getBalanceManager() != null ? "initialized" : "not available"));
            
            return server;
            
        } catch (Exception e) {
            log.error("Failed to bootstrap FAPI server", e);
            return null;
        }
    }
    
    /**
     * 启动FAPI客户端的FudpNode
     * 
     * @param config 启动配置
     * @return 初始化结果，包含 FudpNode 和 Settings
     */
    public static ClientBootstrapResult bootstrapClient(ServiceBootstrapStarter config) {
        BufferedReader br = config.getBr();
        if (br == null) {
            log.error("BufferedReader is required");
            return null;
        }
        
        try {
            // 1. 加载配置文件并验证密码
            Configure.loadConfig(br);
            Configure configure = Configure.checkPassword(br);
            if (configure == null) {
                log.error("Failed to authenticate");
                return null;
            }
            byte[] symkey = configure.getSymkey();
            
            // 2. 选择主FID
            String mainFid = configure.chooseMainFid(symkey);
            if (mainFid == null) {
                log.error("No FID selected");
                return null;
            }
            
            // 3. 获取私钥
            String prikeyCipher = configure.getMainCidInfoMap().get(mainFid).getPrikeyCipher();
            if (prikeyCipher == null) {
                log.error("No private key found for FID: {}", mainFid);
                return null;
            }
            
            byte[] privateKey = core.crypto.Decryptor.decryptPrikey(prikeyCipher, symkey);
            if (privateKey == null) {
                log.error("Failed to decrypt private key");
                return null;
            }
            
            // 4. 初始化客户端默认配置
            config.initializeClientDefaults();
            
            // 5. 创建 NodeConfig
            NodeConfig nodeConfig = new NodeConfig();
            nodeConfig.setMaxPacketSize(8000);
            nodeConfig.setSocketBufferSize(4 * 1024 * 1024);
            Map<String, Object> settingMap = config.getSettingMap();
            Object clientPortObj = settingMap.get("fapiClientPort");
            int clientPort = clientPortObj instanceof Number 
                    ? ((Number) clientPortObj).intValue() 
                    : FapiDefaults.DEFAULT_FAPI_CLIENT_PORT;
            
            Object clientDataDirObj = settingMap.get("fapiClientDataDir");
            String clientDataDir = clientDataDirObj != null ? String.valueOf(clientDataDirObj) : "~/.fudp_client";
            nodeConfig.setDataDir(clientDataDir);
            
            // 6. 创建并启动 FudpNode（端口被占用时自动尝试下一个端口）
            FudpNode fudpNode = null;
            int port = clientPort;
            int maxRetries = FapiDefaults.MAX_PORT_RETRY_COUNT;
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    nodeConfig.setPort(port);
                    fudpNode = new FudpNode(privateKey, nodeConfig);
                    fudpNode.start();
                    if (port != clientPort) {
                        log.info("Default port {} was in use, using port {} instead", clientPort, port);
                    }
                    break;
                } catch (BindException e) {
                    log.debug("Port {} is already in use, trying next port...", port);
                    if (fudpNode != null) {
                        try { fudpNode.stop(); } catch (Exception ignored) {}
                        fudpNode = null;
                    }
                    port++;
                    if (attempt == maxRetries) {
                        log.error("Failed to bind to any port in range {}-{}", clientPort, port - 1);
                        throw new BindException("All ports in range " + clientPort + "-" + (port - 1) + " are in use");
                    }
                }
            }
            
            log.info("FAPI Client FudpNode started");
            log.info("  Local FID: {}", fudpNode.getLocalFid());
            log.info("  Port: {}", fudpNode.getConfig().getPort());
            
            // 7. 创建简化的 Settings（仅包含必要信息）
            Settings settings = createClientSettings(configure, mainFid, config, symkey);
            settings.setMyPrikeyCipher(prikeyCipher);
            
            return new ClientBootstrapResult(fudpNode, settings, symkey, configure);
            
        } catch (Exception e) {
            log.error("Failed to bootstrap FAPI client", e);
            return null;
        }
    }
    
    /**
     * 客户端启动结果
     */
    public static class ClientBootstrapResult {
        private final FudpNode fudpNode;
        private final Settings settings;
        private final byte[] symkey;
        private final Configure configure;
        
        public ClientBootstrapResult(FudpNode fudpNode, Settings settings, byte[] symkey, Configure configure) {
            this.fudpNode = fudpNode;
            this.settings = settings;
            this.symkey = symkey;
            this.configure = configure;
        }
        
        public FudpNode getFudpNode() {
            return fudpNode;
        }
        
        public Settings getSettings() {
            return settings;
        }
        
        public byte[] getSymkey() {
            return symkey;
        }
        
        public Configure getConfigure() {
            return configure;
        }
        
        public void cleanup() {
            if (fudpNode != null) {
                try {
                    fudpNode.stop();
                } catch (Exception e) {
                    log.warn("Error stopping FudpNode", e);
                }
            }
            if (settings != null) {
                try {
                    settings.close();
                } catch (Exception e) {
                    log.warn("Error closing settings", e);
                }
            }
        }
    }
    
    // ==================== 辅助方法 ====================
    
    private static Settings loadOrCreateSettings(String sid, ServiceBootstrapStarter starter, Configure configure) {
        Settings settings = null;
        if (sid != null) {
            settings = Settings.loadSettings(null, sid);
        }
        if (settings == null) {
            // 初始化服务端默认配置
            starter.initializeServerDefaults(configure.getBr());
            
            settings = new Settings(configure, starter.getServiceType(), starter.getSettingMap(),
                starter.getModules());
        }
        return settings;
    }
    
    private static void initializeExternalServices(Settings settings,
                                                   byte[] symkey, Configure configure) {
        // 初始化模块
        settings.initiateServer(settings.getSid(), symkey, configure, null);
    }
    
    private static FudpNode createAndStartFudpNode(Settings settings, Service service, FapiServer server) 
            throws IOException {
        byte[] privateKey = settings.decryptPrikey();
        if (privateKey == null) {
            log.error("Failed to decrypt private key");
            return null;
        }
        
        // 从服务配置中提取端口
        int port = extractPortFromService(service);
        
        // 创建 NodeConfig
        NodeConfig nodeConfig = new NodeConfig();
        nodeConfig.setPort(port);
        nodeConfig.setDataDir("fudp_data/" + settings.getMainFid());
        nodeConfig.setMaxPacketSize(8000);
        nodeConfig.setSocketBufferSize(4 * 1024 * 1024);
        nodeConfig.setPongDataProvider(server::buildAdvertiseData);
        
        // 创建并启动 FudpNode
        FudpNode fudpNode;
        try {
            fudpNode = new FudpNode(privateKey, nodeConfig);
            fudpNode.setEventListener(server);
            fudpNode.start();
        } catch (BindException e) {
            log.error("Port {} is already in use", port);
            throw e;
        }
        
        return fudpNode;
    }
    
    private static int extractPortFromService(Service service) {
        if (service == null || service.getApiUrl() == null) {
            return FapiDefaults.DEFAULT_FAPI_PORT;
        }
        return FapiDefaults.getPort(service.getApiUrl());
    }
    
    /**
     * 配置余额管理监听（使用 FapiServer 内置的 FapiBalanceManager）
     */
    private static void setupBalanceListener(FudpNode fudpNode, FapiServer server) {
        FapiBalanceManager balanceManager = server.getBalanceManager();
        if (balanceManager != null) {
            fudpNode.addMeterListener(record -> {
                FapiBalanceManager.ChargeResult result = balanceManager.checkAndCharge(record);
                if (result.getCode() == FapiBalanceManager.ResultCode.CREDIT_EXCEEDED ||
                    result.getCode() == FapiBalanceManager.ResultCode.INVALID_AMOUNT ||
                    result.getCode() == FapiBalanceManager.ResultCode.INVALID_KEY) {
                    log.warn("Charge rejected for peer {}: {}", record.getPeerId(), result.getCode());
                }
            });
            log.info("Balance metering listener configured");
        } else {
            log.warn("FapiBalanceManager not available, metering disabled");
        }
    }
    
    private static Settings createClientSettings(Configure configure, String mainFid,
                                                 ServiceBootstrapStarter config, byte[] symkey) {
        Settings settings = new Settings(configure, config.getServiceName(), 
            config.getModules(), config.getSettingMap());
        settings.setMainFid(mainFid);
        settings.setSymkey(symkey);
        return settings;
    }
    
    /**
     * 合并组件类型（配置指定 + 链上声明的 components）
     */
    private static String[] mergeComponentTypes(String[] configured, java.util.List<String> components) {
        Set<String> types = new LinkedHashSet<>();
        if (configured != null) {
            for (String t : configured) {
                types.add(t.toUpperCase());
            }
        }
        if (components != null) {
            for (String t : components) {
                if (ComponentRegistry.isComponent(t)) {
                    types.add(t.toUpperCase());
                }
            }
        }
        // 确保至少包含 BASE
        if (types.isEmpty()) {
            types.add(ApiGroupType.BASE_NO1_NRC7);
        }
        return types.toArray(new String[0]);
    }
}
