package fapi;

import data.fcData.Module;
import data.feipData.Service;
import data.feipData.ServiceType;
import fapi.client.AutoRechargeManager;
import fapi.client.BalanceVerifier;
import ui.Inputer;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FAPI服务启动配置
 * <p>
 * 用于配置服务端或客户端的启动参数，支持链式调用。
 * 这是重构后的配置类，替代原有复杂的 Settings 初始化流程。
 */
public class ServiceBootstrapStarter {
    
    // ==================== 基础配置 ====================
    
    /** 输入流 */
    private BufferedReader br;
    
    /** 服务类型 */
    private ServiceType serviceType;
    
    /** 服务名称 */
    private String serviceName;
    
    /** 组件类型列表（如 BASE, DISK, MAP 等） */
    private String[] componentTypes;
    
    /** 必需的模块列表 */
    private List<Module> modules = new ArrayList<>();
    
    /** 自定义设置 */
    private Map<String, Object> settingMap = new HashMap<>();
    
    // ==================== 服务端配置 ====================
    
    /** 服务端口 */
    private int port = FapiDefaults.DEFAULT_FAPI_PORT;
    
    // 注意：dataDir, creditLimit 等配置已移至 settingMap
    // 客户端配置（clientPort, clientDataDir）也已移至 settingMap
    
    // ==================== 构造器 ====================
    
    private ServiceBootstrapStarter() {
        // 私有构造器，使用工厂方法创建
    }

    /**
     * 创建FAPI服务端配置
     */
    public static ServiceBootstrapStarter forFapiServer() {
        ServiceBootstrapStarter config = new ServiceBootstrapStarter();
        config.serviceType = ServiceType.FAPI_No1_NrC7;
        config.serviceName = "FAPI Server";
        config.componentTypes = new String[] { "BASE" }; // 默认启用BASE组件
        
        // 服务端默认需要的模块
        config.modules.add(new Module(Service.class.getSimpleName(), ServiceType.NASA_RPC.name()));
        config.modules.add(new Module(Service.class.getSimpleName(), ServiceType.ES.name()));
        
        return config;
    }
    
    /**
     * 创建FAPI客户端配置
     */
    public static ServiceBootstrapStarter forFapiClient() {
        ServiceBootstrapStarter config = new ServiceBootstrapStarter();
        config.serviceType = ServiceType.FAPI_No1_NrC7;
        config.serviceName = "FAPI Client";
        
        return config;
    }
    
    // ==================== 链式设置方法 ====================
    
    public ServiceBootstrapStarter setBr(BufferedReader br) {
        this.br = br;
        return this;
    }
    
    public ServiceBootstrapStarter setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
        return this;
    }
    
    public ServiceBootstrapStarter setServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }
    
    public ServiceBootstrapStarter setComponentTypes(String... componentTypes) {
        this.componentTypes = componentTypes;
        return this;
    }
    
    public ServiceBootstrapStarter addModule(Module module) {
        this.modules.add(module);
        return this;
    }
    
    public ServiceBootstrapStarter setModules(List<Module> modules) {
        this.modules = modules != null ? modules : new ArrayList<>();
        return this;
    }
    
    public ServiceBootstrapStarter putSetting(String key, Object value) {
        this.settingMap.put(key, value);
        return this;
    }
    
    public ServiceBootstrapStarter setSettingMap(Map<String, Object> settingMap) {
        this.settingMap = settingMap != null ? settingMap : new HashMap<>();
        return this;
    }
    
    // ==================== Getters ====================
    
    public BufferedReader getBr() {
        return br;
    }
    
    public ServiceType getServiceType() {
        return serviceType;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public String[] getComponentTypes() {
        return componentTypes;
    }
    
    public List<Module> getModules() {
        return modules;
    }
    
    public Map<String, Object> getSettingMap() {
        return settingMap;
    }

    
    // ==================== 初始化方法 ====================
    
    /**
     * 初始化服务端默认配置
     * @param br 输入流，用于提示用户输入 creditLimit
     */
    public void initializeServerDefaults(BufferedReader br) {
        if (settingMap == null) {
            settingMap = new HashMap<>();
        }

        // 提示用户输入 creditLimit
        if (!settingMap.containsKey(FapiBalanceManager.CREDIT_LIMIT)) {
            Long creditLimit = Inputer.inputLong(br,
                "Credit limit (satoshi, default: " + FapiBalanceManager.DEFAULT_CREDIT_LIMIT + "):",
                FapiBalanceManager.DEFAULT_CREDIT_LIMIT);
            settingMap.put(FapiBalanceManager.CREDIT_LIMIT, creditLimit);
        }
        
        // 静默设置其他服务端默认值
        settingMap.putIfAbsent(FapiBalanceManager.KEY_CREDIT_RETENTION_DAYS, 
            FapiBalanceManager.DEFAULT_CREDIT_RETENTION_DAYS);
        settingMap.putIfAbsent("fapiDiskDataPath", 
            System.getProperty("user.home") + "/diskData");
        
        // 结算相关默认值
        settingMap.putIfAbsent(FapiBalanceManager.KEY_SETTLE_CYCLE, 
            FapiBalanceManager.DEFAULT_SETTLE_CYCLE);
        settingMap.putIfAbsent(FapiBalanceManager.KEY_MIN_SETTLE_AMOUNT, 
            FapiBalanceManager.DEFAULT_MIN_SETTLE_AMOUNT);
        settingMap.putIfAbsent(FapiBalanceManager.KEY_DEFAULT_VIA, 
            FapiBalanceManager.DEFAULT_VIA);
        // stakeholders 默认为空 Map，需要用户配置
        settingMap.putIfAbsent(FapiBalanceManager.KEY_STAKEHOLDERS, 
            new java.util.HashMap<String, Long>());

        // DISK sync defaults
        settingMap.putIfAbsent(fapi.components.disk.DiskSyncManager.KEY_MAX_DATA_SIZE,
            fapi.components.disk.DiskSyncManager.DEFAULT_MAX_DATA_SIZE);
        settingMap.putIfAbsent(fapi.components.disk.DiskSyncManager.KEY_MAX_TOTAL_DISK_USAGE,
            fapi.components.disk.DiskSyncManager.DEFAULT_MAX_TOTAL_DISK_USAGE);
        settingMap.putIfAbsent(fapi.components.disk.DiskSyncManager.KEY_MIN_DEALER_BALANCE,
            fapi.components.disk.DiskSyncManager.DEFAULT_MIN_DEALER_BALANCE);
        settingMap.putIfAbsent(fapi.components.disk.DiskSyncManager.KEY_DISK_SYNC_INTERVAL_HOURS,
            fapi.components.disk.DiskSyncManager.DEFAULT_SYNC_INTERVAL_HOURS);
    }
    
    /**
     * 初始化客户端默认配置
     */
    public void initializeClientDefaults() {
        if (settingMap == null) {
            settingMap = new HashMap<>();
        }
        
        // 静默设置客户端默认值
        settingMap.putIfAbsent("fapiClientPort", 8501);
        settingMap.putIfAbsent("fapiClientDataDir", "~/.fudp_client");
        
        // 设置余额验证默认值
        settingMap.putIfAbsent(BalanceVerifier.KEY_TOLERANCE_PCT, BalanceVerifier.DEFAULT_TOLERANCE_PCT);
        settingMap.putIfAbsent(BalanceVerifier.KEY_TOLERANCE_SAT_MIN, BalanceVerifier.DEFAULT_TOLERANCE_SAT_MIN);
        settingMap.putIfAbsent(BalanceVerifier.KEY_DRIFT_ACCUM_PCT, BalanceVerifier.DEFAULT_DRIFT_ACCUM_PCT);
        settingMap.putIfAbsent(BalanceVerifier.KEY_DRIFT_ACCUM_SAT, BalanceVerifier.DEFAULT_DRIFT_ACCUM_SAT);
        settingMap.putIfAbsent(BalanceVerifier.KEY_DRIFT_STOP_PCT, BalanceVerifier.DEFAULT_DRIFT_STOP_PCT);
        settingMap.putIfAbsent(BalanceVerifier.KEY_DRIFT_STOP_SAT, BalanceVerifier.DEFAULT_DRIFT_STOP_SAT);
        settingMap.putIfAbsent(BalanceVerifier.KEY_MAX_CONSECUTIVE_DRIFT, BalanceVerifier.DEFAULT_MAX_CONSECUTIVE_DRIFT);
        settingMap.putIfAbsent(BalanceVerifier.KEY_DRIFT_ACTION, BalanceVerifier.DEFAULT_DRIFT_ACTION);
        
        // 设置自动充值默认值
        settingMap.putIfAbsent(AutoRechargeManager.KEY_ENABLED, AutoRechargeManager.DEFAULT_ENABLED);
        settingMap.putIfAbsent(AutoRechargeManager.KEY_THRESHOLD, AutoRechargeManager.DEFAULT_THRESHOLD);
        settingMap.putIfAbsent(AutoRechargeManager.KEY_PURCHASE_KB, AutoRechargeManager.DEFAULT_PURCHASE_KB);
        settingMap.putIfAbsent(AutoRechargeManager.KEY_COOLDOWN_MS, AutoRechargeManager.DEFAULT_COOLDOWN_MS);
        settingMap.putIfAbsent(AutoRechargeManager.KEY_MAX_RETRIES, AutoRechargeManager.DEFAULT_MAX_RETRIES);
        settingMap.putIfAbsent(AutoRechargeManager.KEY_RETRY_DELAY_MS, AutoRechargeManager.DEFAULT_RETRY_DELAY_MS);
        settingMap.putIfAbsent(AutoRechargeManager.KEY_MAX_PAYMENT, AutoRechargeManager.DEFAULT_MAX_PAYMENT);
        
        // 设置消费渠道默认值
        settingMap.putIfAbsent(FapiBalanceManager.KEY_DEFAULT_VIA, FapiBalanceManager.DEFAULT_VIA);
    }
}

