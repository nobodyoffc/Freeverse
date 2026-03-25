package fapi.service;

import clients.ApipClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import config.Settings;
import constants.OpNames;
import data.feipData.Feip;
import data.feipData.Service;
import data.feipData.ServiceOpData;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import data.feipData.ServiceType;
import fapi.ComponentRegistry;
import fapi.FapiBalanceManager;
import fapi.FapiCode;
import fapi.FapiComponent;
import fapi.FapiDefaults;
import fapi.client.FapiClient;
import fapi.message.FapiRequest;
import fapi.message.FapiResponse;
import fapi.message.UnifiedCodec;
import fapi.message.UnifiedCodec.UnifiedRequest;
import fapi.message.UnifiedCodec.UnifiedResponse;
import fapi.monitor.AuditLogger;
import fapi.monitor.HealthChecker;
import fapi.monitor.HealthStatus;
import fapi.monitor.MetricsCollector;
import fapi.monitor.MetricsReport;
import fapi.recharge.RechargeScanner.CashInfo;
import fapi.security.RequestValidator;
import fapi.security.ValidationResult;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import data.fchData.Cash;
import core.fch.TxCreator;
import utils.FchUtils;
import fapi.components.DockComponent;
import fapi.service.tasks.DockCleanupTask;
import fapi.service.tasks.RechargeTask;
import fapi.service.tasks.SettleTask;
import fudp.node.FudpNode;
import fudp.node.NodeEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ui.Menu;
import ui.Shower;
import utils.JsonUtils;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static constants.Constants.FUND_FID;
import static constants.FieldNames.*;

/**
 * FAPI 服务主类
 * <p>
 * 提供 FAPI 服务的核心功能，包括：
 * - 请求路由与处理
 * - 组件管理
 * - 余额管理
 * - 服务配置管理
 * - 健康检查与监控
 * <p>
 * 注意：此类不再继承 ServiceManager，以提高 FAPI 模块的独立性。
 */
public class FapiServer implements NodeEventListener {
    private static final Logger log = LoggerFactory.getLogger(FapiServer.class);
    
    /** Default maximum number of IDs allowed in a single batch request (e.g., check, getByIds) */
    public static final int MAX_IDS_PER_REQUEST = 200;
    
    // ==================== 核心字段 ====================
    private FudpNode fudpNode;
    private final Map<String, Service> serviceMap;
    private final Settings settings;
    
    // ==================== 服务配置（原 ServiceManager 字段） ====================
    protected Service service;
    protected BufferedReader br;
    protected byte[] symKey;
    
    // ==================== 余额管理 ====================
    /** FAPI专用余额管理器 */
    private FapiBalanceManager balanceManager;
    /** 充值扫描器（保留兼容性） */
    @Deprecated
    private fapi.recharge.RechargeScanner rechargeScanner;
    
    // ==================== 区块事件调度 ====================
    /** 区块事件调度器 */
    private BlockEventDispatcher blockEventDispatcher;
    /** 结算周期（区块数） */
    private long settleCycle = FapiBalanceManager.DEFAULT_SETTLE_CYCLE;
    /** 股东分成配置 */
    private Map<String, Long> stakeholders;
    
    // ==================== 组件管理 ====================
    /** 按名称索引的组件映射 */
    private final Map<String, FapiComponent> components = new ConcurrentHashMap<>();
    /** 按类型索引的组件映射 */
    private final Map<Class<? extends FapiComponent>, FapiComponent> componentsByClass = new ConcurrentHashMap<>();
    
    // ==================== 安全与监控 ====================
    /** 健康检查器 */
    private HealthChecker healthChecker;
    /** 性能指标收集器 */
    private final MetricsCollector metricsCollector = new MetricsCollector();
    /** 审计日志器 */
    private final AuditLogger auditLogger = AuditLogger.getInstance();
    
    // ==================== 请求处理线程池 ====================
    private final ExecutorService requestExecutor = Executors.newFixedThreadPool(4,
            r -> { Thread t = new Thread(r, "fapi-worker"); t.setDaemon(true); return t; });
    
    // ==================== 远程客户端管理 ====================
    /** 远程FAPI服务客户端缓存 (URL -> FapiClient) */
    private final Map<String, FapiClient> remoteClients = new ConcurrentHashMap<>();
    /** 客户端最后使用时间 (URL -> timestamp) */
    private final Map<String, Long> clientLastUsed = new ConcurrentHashMap<>();
    /** 远程客户端连接超时（毫秒） */
    private static final long REMOTE_CLIENT_TIMEOUT_MS = 10000;
    /** 远程客户端请求超时（秒）- kept short so chain relay responds quickly */
    private static final long REMOTE_CLIENT_REQUEST_TIMEOUT_SEC = 15;
    
    // ==================== 一次性广播白名单 ====================
    /** Temporary whitelist: FID -> expiry timestamp. Grants one free broadcastTx after PAYMENT_REQUIRED. */
    private final ConcurrentHashMap<String, Long> broadcastWhitelist = new ConcurrentHashMap<>();
    private static final long BROADCAST_WHITELIST_TTL_MS = 60_000;
    
    /**
     * 构造函数
     * @param service 服务对象
     * @param br 输入流
     * @param symKey 对称密钥
     * @param settings Settings 对象（用于获取其他依赖）
     */
    public FapiServer(Service service, BufferedReader br, byte[] symKey, Settings settings) {
        this.service = service;
        this.br = br;
        this.symKey = symKey;
        this.settings = settings;
        this.serviceMap = new HashMap<>();
    }
    
    /**
     * 便捷构造函数（从 Settings 创建）
     */
    public FapiServer(Settings settings) {
        this.settings = settings;
        this.serviceMap = new HashMap<>();
        this.service = settings.getService();
        this.symKey = settings.getSymkey();
    }
    
    /**
     * 设置 FudpNode（在启动时调用）
     */
    public void setFudpNode(FudpNode node) {
        this.fudpNode = node;
    }

    /**
     * 获取 FudpNode
     */
    public FudpNode getFudpNode() {
        return fudpNode;
    }
    
    /**
     * 初始化服务
     */
    public void initialize() {
        // 将当前服务加入服务映射
        if (service != null) {
            serviceMap.put(service.getId(), service);
            log.info("FapiServer initialized with service: {}", service.getId());
            
            // 初始化余额管理器
            initializeBalanceManager();
            
            // 初始化区块事件调度器（包含充值扫描和结算任务）
            initializeBlockEventDispatcher();
        } else {
            log.warn("FapiServer initialized without service");
        }
        
        // 初始化健康检查器
        this.healthChecker = new HealthChecker(this);
    }
    
    /**
     * 初始化余额管理器
     */
    private void initializeBalanceManager() {
        try {
            Map<String, Object> settingMap = settings != null ? settings.getSettingMap() : null;
            
            Long creditLimit = null;
            if (settingMap != null) {
                Object creditLimitObj = settingMap.get(FapiBalanceManager.CREDIT_LIMIT);
                if (creditLimitObj instanceof Number) {
                    creditLimit = ((Number) creditLimitObj).longValue();
                }
            }
            
            String dbDir = settings != null ? settings.getDbDir() : null;
            String mainFid = settings != null ? settings.getMainFid() : null;
            String sid = service != null ? service.getId() : null;
            
            this.balanceManager = new FapiBalanceManager(service, dbDir, mainFid, sid, creditLimit, settingMap);
            log.info("FapiBalanceManager initialized successfully");
        } catch (Exception e) {
            log.warn("Failed to initialize FapiBalanceManager: {}", e.getMessage(), e);
            // 余额管理器初始化失败不应阻止服务启动
        }
    }
    
    /**
     * 初始化区块事件调度器
     * <p>
     * 统一管理基于区块高度变化的所有任务：
     * 1. RechargeTask - 充值订单扫描
     * 2. SettleTask - 周期结算检查
     * 3. 未来可添加更多任务（如快照、清理等）
     */
    private void initializeBlockEventDispatcher() {
        if (balanceManager == null || service == null) {
            log.warn("Cannot initialize BlockEventDispatcher: balanceManager or service is null");
            return;
        }
        
        String dealer = service.getDealer();
        if (dealer == null || dealer.isEmpty()) {
            log.warn("Cannot initialize BlockEventDispatcher: service dealer address is not set");
            return;
        }
        
        // 获取 ES 客户端
        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);
        if (esClient == null) {
            log.warn("Cannot initialize BlockEventDispatcher: ElasticsearchClient is not available");
            return;
        }
        
        // 创建区块高度获取函数
        java.util.function.Supplier<Long> heightSupplier = () -> queryBestBlockHeight(esClient);
        
        // 创建区块事件调度器
        this.blockEventDispatcher = new BlockEventDispatcher(heightSupplier);
        
        // ============ 注册任务 ============
        
        // 1. 注册充值扫描任务
        java.util.function.Function<Long, List<CashInfo>> cashQueryFunction = 
                (fromHeight) -> queryNewCashesForDealer(esClient, dealer, fromHeight);
        java.util.function.Function<List<CashInfo>, Map<String, String>> viaQueryFunction = 
                (cashes) -> queryViaFromOpReturns(esClient, cashes);
        
        RechargeTask rechargeTask = new RechargeTask(
                balanceManager, dealer, 1, cashQueryFunction, viaQueryFunction);
        blockEventDispatcher.registerTask(rechargeTask);
        
        // 2. 注册结算任务
        Map<String, Object> settingMap = settings != null ? settings.getSettingMap() : null;
        if (settingMap != null) {
            // 读取结算周期
            Object settleCycleObj = settingMap.get(FapiBalanceManager.KEY_SETTLE_CYCLE);
            this.settleCycle = settleCycleObj instanceof Number 
                ? ((Number) settleCycleObj).longValue() 
                : FapiBalanceManager.DEFAULT_SETTLE_CYCLE;
            
            // 读取股东配置
            Object stakeholdersObj = settingMap.get(FapiBalanceManager.KEY_STAKEHOLDERS);
            if (stakeholdersObj instanceof Map) {
                this.stakeholders = parseStakeholders((Map<?, ?>) stakeholdersObj);
            } else {
                this.stakeholders = new HashMap<>();
            }
            
            SettleTask settleTask = new SettleTask(balanceManager, settleCycle, stakeholders);
            blockEventDispatcher.registerTask(settleTask);
        }
        
        // 3. Register DOCK cleanup task (if DOCK component is loaded)
        DockComponent dockComponent = getComponent(DockComponent.class);
        if (dockComponent != null) {
            long cleanupInterval = DockCleanupTask.DEFAULT_CLEANUP_INTERVAL;
            if (settingMap != null && settingMap.containsKey("dockCleanupInterval")) {
                Object v = settingMap.get("dockCleanupInterval");
                if (v instanceof Number) {
                    cleanupInterval = ((Number) v).longValue();
                }
            }
            DockCleanupTask dockCleanupTask = new DockCleanupTask(dockComponent, cleanupInterval);
            blockEventDispatcher.registerTask(dockCleanupTask);
        }
        
        // ============ 启动调度器 ============
        
        // 尝试从设置中获取 listenPath
        String listenPath = null;
        if (settings != null && settings.getSettingMap() != null) {
            Object listenPathObj = settings.getSettingMap().get(Settings.LISTEN_PATH);
            if (listenPathObj != null) {
                listenPath = listenPathObj.toString();
            }
        }
        
        // 启动调度器
        if (listenPath != null && !listenPath.isEmpty()) {
            try {
                java.nio.file.Path path = java.nio.file.Paths.get(listenPath);
                blockEventDispatcher.startWithWatcher(path);
                log.info("BlockEventDispatcher started with watcher: listenPath={}, tasks={}", 
                        listenPath, blockEventDispatcher.getTasks().size());
            } catch (Exception e) {
                log.warn("Failed to start BlockEventDispatcher with watcher, falling back to polling: {}", e.getMessage());
                blockEventDispatcher.startWithPolling(60_000L);
                log.info("BlockEventDispatcher started with polling: tasks={}", 
                        blockEventDispatcher.getTasks().size());
            }
        } else {
            blockEventDispatcher.startWithPolling(60_000L);
            log.info("BlockEventDispatcher started with polling (no listenPath configured): tasks={}", 
                    blockEventDispatcher.getTasks().size());
        }
        
        // 如果是首次启动（lastOrderScanHeight 为 null），立即执行一次充值扫描
        if (balanceManager.getLastOrderScanHeight() == null) {
            log.info("First time startup detected (lastOrderScanHeight is null), triggering initial order scan...");
            Long currentHeight = queryBestBlockHeight(esClient);
            if (currentHeight != null && currentHeight > 0) {
                rechargeTask.execute(currentHeight, 0);
                log.info("Initial order scan completed at height {}", currentHeight);
            } else {
                log.warn("Cannot perform initial order scan: unable to get current block height");
            }
        }
        
        log.info("BlockEventDispatcher initialized: settleCycle={} blocks, stakeholders={}, minSettleAmount={} sat", 
                settleCycle, stakeholders != null ? stakeholders.size() : 0, 
                balanceManager.getMinSettleAmount());
    }
    
    /**
     * 查询当前最佳区块高度
     */
    private Long queryBestBlockHeight(ElasticsearchClient esClient) {
        try {
            co.elastic.clients.elasticsearch.core.SearchRequest.Builder searchBuilder = 
                    new co.elastic.clients.elasticsearch.core.SearchRequest.Builder();
            searchBuilder.index("block");
            searchBuilder.size(1);
            searchBuilder.sort(s -> s.field(f -> f.field("height").order(
                    co.elastic.clients.elasticsearch._types.SortOrder.Desc)));
            
            co.elastic.clients.elasticsearch.core.SearchResponse<data.fchData.Block> searchResponse = 
                    esClient.search(searchBuilder.build(), data.fchData.Block.class);
            
            if (searchResponse.hits() != null && searchResponse.hits().hits() != null 
                    && !searchResponse.hits().hits().isEmpty()) {
                data.fchData.Block block = searchResponse.hits().hits().get(0).source();
                if (block != null && block.getHeight() != null) {
                    return block.getHeight();
                }
            }
        } catch (Exception e) {
            log.error("Error querying best block height", e);
        }
        return null;
    }
    
    /**
     * 批量查询 OpReturn 获取渠道信息
     */
    private Map<String, String> queryViaFromOpReturns(ElasticsearchClient esClient, List<CashInfo> cashList) {
        Map<String, String> viaMap = new HashMap<>();
        
        if (esClient == null || cashList == null || cashList.isEmpty()) {
            return viaMap;
        }
        
        // 收集所有 birthTxId
        List<String> txIds = cashList.stream()
                .map(CashInfo::getBirthTxId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        
        if (txIds.isEmpty()) {
            return viaMap;
        }
        
        try {
            // 批量查询 OpReturn
            int batchSize = 100;
            for (int i = 0; i < txIds.size(); i += batchSize) {
                List<String> batch = txIds.subList(i, Math.min(i + batchSize, txIds.size()));
                
                co.elastic.clients.elasticsearch.core.SearchRequest.Builder searchBuilder = 
                        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder();
                searchBuilder.index(constants.IndicesNames.OPRETURN);
                searchBuilder.size(batch.size());
                searchBuilder.query(q -> q.ids(ids -> ids.values(batch)));
                
                co.elastic.clients.elasticsearch.core.SearchResponse<data.fchData.OpReturn> searchResponse = 
                        esClient.search(searchBuilder.build(), data.fchData.OpReturn.class);
                
                if (searchResponse.hits() != null && searchResponse.hits().hits() != null) {
                    for (co.elastic.clients.elasticsearch.core.search.Hit<data.fchData.OpReturn> hit : 
                            searchResponse.hits().hits()) {
                        data.fchData.OpReturn opReturn = hit.source();
                        if (opReturn != null && opReturn.getOpReturn() != null) {
                            String via = parseViaFromOpReturn(opReturn.getOpReturn());
                            if (via != null) {
                                // 找到对应的 cashId
                                for (CashInfo cash : cashList) {
                                    if (hit.id() != null && hit.id().equals(cash.getBirthTxId())) {
                                        viaMap.put(cash.getCashId(), via);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error querying OpReturns for via", e);
        }
        
        return viaMap;
    }
    
    /**
     * 从 OpReturn 内容解析 via 字段
     */
    private String parseViaFromOpReturn(String opReturnContent) {
        if (opReturnContent == null || opReturnContent.isEmpty()) {
            return null;
        }
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> json = new Gson().fromJson(opReturnContent, Map.class);
            if (json != null && json.containsKey("via")) {
                Object via = json.get("via");
                if (via instanceof String && isValidFid((String) via)) {
                    return (String) via;
                }
            }
        } catch (Exception e) {
            // 不是 JSON 格式，忽略
        }
        
        return null;
    }
    
    /**
     * 简单验证 FID 格式
     */
    private boolean isValidFid(String fid) {
        return fid != null && fid.length() >= 26 && fid.length() <= 35 
                && (fid.startsWith("F") || fid.startsWith("1") || fid.startsWith("3"));
    }
    
    /**
     * 解析股东配置
     */
    private Map<String, Long> parseStakeholders(Map<?, ?> raw) {
        Map<String, Long> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Number) {
                result.put(key, ((Number) value).longValue());
            }
        }
        return result;
    }
    
    /**
     * 获取区块事件调度器
     */
    public BlockEventDispatcher getBlockEventDispatcher() {
        return blockEventDispatcher;
    }
    
    /**
     * 查询指定高度之后发送到 dealer 地址的新 Cash
     */
    private List<fapi.recharge.RechargeScanner.CashInfo> queryNewCashesForDealer(
            ElasticsearchClient esClient, String dealer, Long fromHeight) {
        List<fapi.recharge.RechargeScanner.CashInfo> result = new ArrayList<>();
        
        try {
            co.elastic.clients.elasticsearch.core.SearchRequest.Builder searchBuilder = 
                    new co.elastic.clients.elasticsearch.core.SearchRequest.Builder();
            searchBuilder.index("cash");
            searchBuilder.size(1000);  // 限制每次查询数量
            searchBuilder.trackTotalHits(t -> t.enabled(true));
            
            // 按高度和ID排序
            searchBuilder.sort(s -> s.field(f -> f.field("birthHeight").order(
                    co.elastic.clients.elasticsearch._types.SortOrder.Asc)));
            searchBuilder.sort(s -> s.field(f -> f.field("id").order(
                    co.elastic.clients.elasticsearch._types.SortOrder.Asc)));
            
            // 构建查询条件
            co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder boolBuilder = 
                    new co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder();
            
            // owner = dealer
            co.elastic.clients.elasticsearch._types.query_dsl.TermQuery ownerQuery = 
                    co.elastic.clients.elasticsearch._types.query_dsl.TermQuery.of(
                            t -> t.field("owner").value(dealer));
            boolBuilder.must(new co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder()
                    .term(ownerQuery).build());
            
            // valid = true
            co.elastic.clients.elasticsearch._types.query_dsl.TermQuery validQuery = 
                    co.elastic.clients.elasticsearch._types.query_dsl.TermQuery.of(
                            t -> t.field("valid").value(true));
            boolBuilder.must(new co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder()
                    .term(validQuery).build());
            
            // birthHeight > fromHeight（查询大于 lastOrderScanHeight 的订单）
            if (fromHeight != null && fromHeight >= 0) {
                co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery heightQuery = 
                        co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery.of(
                                r -> r.field("birthHeight").gt(co.elastic.clients.json.JsonData.of(fromHeight)));
                boolBuilder.must(new co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder()
                        .range(heightQuery).build());
            }
            
            searchBuilder.query(q -> q.bool(boolBuilder.build()));
            
            co.elastic.clients.elasticsearch.core.SearchResponse<data.fchData.Cash> searchResponse = 
                    esClient.search(searchBuilder.build(), data.fchData.Cash.class);
            
            if (searchResponse.hits() != null && searchResponse.hits().hits() != null) {
                for (co.elastic.clients.elasticsearch.core.search.Hit<data.fchData.Cash> hit : 
                        searchResponse.hits().hits()) {
                    data.fchData.Cash cash = hit.source();
                    if (cash != null && cash.getOwner() != null && cash.getOwner().equals(dealer)) {
                        // 获取 issuer（发送者）- Cash 对象已经有 issuer 字段
                        String issuer = cash.getIssuer();
                        if (issuer != null && !issuer.isEmpty() && !issuer.equals("coinbase")) {
                            String cashId = cash.getId() != null ? cash.getId() : 
                                    (cash.getBirthTxId() != null && cash.getBirthIndex() != null ?
                                            cash.getBirthTxId() + ":" + cash.getBirthIndex() : null);
                            if (cashId != null) {
                                result.add(new fapi.recharge.RechargeScanner.CashInfo(
                                        cashId,
                                        issuer,
                                        cash.getOwner(),
                                        cash.getValue() != null ? cash.getValue() : 0L,
                                        cash.getBirthHeight() != null ? cash.getBirthHeight() : 0L,
                                        cash.getBirthBlockId() != null ? cash.getBirthBlockId() : "",
                                        cash.getBirthTxId()  // 添加 birthTxId 用于 OpReturn 查询
                                ));
                            }
                        }
                    }
                }
            }
            
            if(result.size()>0)
                log.debug("queryNewCashesForDealer: found {} new cashes for dealer {} from height {}",
                    result.size(), dealer, fromHeight);
            
        } catch (Exception e) {
            log.error("Error querying new cashes for dealer {} from height {}: {}", 
                    dealer, fromHeight, e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * 获取保存的订单扫描高度
     * <p>
     * 此方法仅用于外部查询，初始化逻辑已移至 RechargeScanner
     * 
     * @return 保存的订单扫描高度，null 表示未保存
     */
    public Long getSavedOrderScanHeight() {
        return balanceManager.getLastOrderScanHeight();
    }
    
    /**
     * 获取余额管理器
     */
    public FapiBalanceManager getBalanceManager() {
        return balanceManager;
    }
    
    /**
     * 获取服务映射
     */
    public Map<String, Service> getServiceMap() {
        return serviceMap;
    }

    /**
     * 添加服务到服务映射
     */
    public void addService(Service service) {
        if (service != null && service.getId() != null) {
            serviceMap.put(service.getId(), service);
            log.debug("Added service to FapiServer: {}", service.getId());
        }
    }
    
    // ==================== 服务管理方法（原 ServiceManager 功能） ====================
    
    /**
     * 服务管理菜单
     */
    public void menu() {
        Menu menu = new Menu();
        menu.setTitle("Service Manager");
        ArrayList<String> menuItemList = new ArrayList<>();

        menuItemList.add("Show service");
        menuItemList.add("Publish service");
        menuItemList.add("Update service");
        menuItemList.add("Stop service");
        menuItemList.add("Recover service");
        menuItemList.add("Close service");
        menuItemList.add("Reload services");

        menu.add(menuItemList);
        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> showService();
                case 2 -> publishService();
                case 3 -> updateService(symKey, br);
                case 4 -> stopService(br);
                case 5 -> recoverService(br);
                case 6 -> closeService(br);
                case 7 -> reloadService(br, symKey);
                case 0 -> {
                    return;
                }
            }
        }
    }
    
    private void showService() {
        System.out.println(JsonUtils.toNiceJson(service));
    }
    
    public void publishService() {
        System.out.println("Publish service...");

        if (Menu.askIfToDo("Get the OpReturn text to publish a new service?", br)) return;

        Feip dataOnChain = setFcInfoForService();

        ServiceOpData data = new ServiceOpData();

        data.setOp(OpNames.PUBLISH);

        data.inputService(br);

        dataOnChain.setData(data);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        Shower.printUnderline(10);
        String opReturnJson = gson.toJson(dataOnChain);
        System.out.println(opReturnJson);
        Shower.printUnderline(10);
        System.out.println("Check, and edit if you want, the JSON text above. Send it in a TX by the owner of the service to freecash blockchain:");
        Menu.anyKeyToContinue(br);
    }

    public void updateService(byte[] symKey, BufferedReader br) {
        System.out.println("Update service...");
        if (service == null) return;
        showService();

        if (Menu.askIfToDo("Get the OpReturn text to update a service?", br)) return;

        Feip dataOnChain = setFcInfoForService();

        ServiceOpData data = new ServiceOpData();
        ServiceOpData.serviceToServiceData(service, data);
        data.setOp(OpNames.UPDATE);

        if (symKey != null) {
            data.updateService(br, symKey, null);
        } else {
            data.updateService(br);
        }

        System.out.println("Update the pricing fields...");
        data.updatePricingFields(br);

        dataOnChain.setData(data);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        System.out.println("Check the JSON text below. Send it in a TX by the owner of the service to freecash blockchain:");
        System.out.println(gson.toJson(dataOnChain));

        Menu.anyKeyToContinue(br);
    }
    
    private void stopService(BufferedReader br) {
        System.out.println("Stop service...");
        operateService(br, OpNames.STOP);
    }

    private void recoverService(BufferedReader br) {
        System.out.println("Recover service...");
        operateService(br, OpNames.RECOVER);
    }

    private void closeService(BufferedReader br) {
        System.out.println("Close service...");
        operateService(br, OpNames.CLOSE);
    }
    
    private void reloadService(BufferedReader br, byte[] symKey) {
        if (service == null) return;
        String sid = service.getId();

        Service reloadedService = getService();
        if (reloadedService != null) {
            this.service = reloadedService;
            serviceMap.put(sid, reloadedService);
            System.out.println("Service reloaded: " + sid);
        }
    }

    private void operateService(BufferedReader br, String op) {
        showService();

        if (Menu.askIfToDo("Get the OpReturn text to " + op + " a service?", br)) return;

        Feip dataOnChain = setFcInfoForService();

        ServiceOpData data = new ServiceOpData();
        data.setOp(op);
        data.setSid(service.getId());
        dataOnChain.setData(data);

        System.out.println("The owner can send a TX with below json in OpReturn to " + op + " the service: " + service.getId());
        System.out.println(JsonUtils.toNiceJson(dataOnChain));

        System.out.println("you can replace the value of 'data.sid' to " + op + " other your own service services.");
        Menu.anyKeyToContinue(br);
    }
    
    private static Feip setFcInfoForService() {
        Feip dataOnChain = new Feip();
        dataOnChain.setType("FEIP");
        dataOnChain.setSn(Feip.FeipProtocol.SERVICE.getSn());
        dataOnChain.setVer(Feip.FeipProtocol.SERVICE.getVer());
        dataOnChain.setName(Feip.FeipProtocol.SERVICE.getName());
        return dataOnChain;
    }

    // ==================== NodeEventListener 接口实现 ====================
    
    /**
     * 处理接收到的 FUDP 请求
     * <p>
     * 支持统一二进制协议：[headerLen][FapiRequest JSON][binary data]
     * 同时向后兼容纯 JSON 格式和旧版 DISK 二进制协议
     */
    @Override
    public void onRequestReceived(String peerId, long requestId, String serviceName, byte[] data) {
        requestExecutor.submit(() -> handleRequest(peerId, requestId, serviceName, data));
    }
    
    private void handleRequest(String peerId, long requestId, String serviceName, byte[] data) {
        try {
            FapiRequest fapiRequest;
            byte[] binaryData = null;
            int requestSize = data.length;
            
            // 尝试使用统一协议解码
            if (UnifiedCodec.isUnifiedProtocol(data)) {
                UnifiedRequest unified = UnifiedCodec.decodeRequest(data);
                fapiRequest = unified.request();
                binaryData = unified.binaryData();
            } 
            // 向后兼容：旧版 DISK 二进制协议
            else if ("DISK".equals(serviceName) && fapi.components.disk.DiskProtocol.isDiskProtocol(data)) {
                handleLegacyBinaryDiskRequest(peerId, requestId, data);
                return;
            }
            // 向后兼容：纯 JSON 格式
            else {
                String json = new String(data, StandardCharsets.UTF_8);
                fapiRequest = JsonUtils.fromJson(json, FapiRequest.class);
            }
            
            if (fapiRequest == null) {
                sendErrorResponse(peerId, requestId, FapiCode.BAD_REQUEST, "Invalid request format");
                return;
            }
            
            // Handle bare "ping" as "base.health" for backward compatibility
            if ("ping".equalsIgnoreCase(fapiRequest.getApi())) {
                fapiRequest.setApi("base.health");
            }
            
            // 验证 api 格式
            if (fapiRequest.getApi() == null || !fapiRequest.getApi().contains(".")) {
                sendErrorResponse(peerId, requestId, FapiCode.BAD_REQUEST, 
                    "Invalid api format, expected: component.method (e.g., base.search)");
                return;
            }
            
            // 验证 dataSize 与实际二进制数据大小是否匹配
            if (fapiRequest.hasBinaryData() && binaryData != null) {
                if (fapiRequest.getDataSize() != binaryData.length) {
                    sendErrorResponse(peerId, requestId, FapiCode.BAD_REQUEST, 
                        "dataSize mismatch: declared=" + fapiRequest.getDataSize() + 
                        ", actual=" + binaryData.length);
                    return;
                }
            }
            
            // 获取组件
            String componentName = fapiRequest.getComponentName();
            String method = fapiRequest.getMethodName();
            FapiComponent component = components.get(componentName != null ? componentName.toUpperCase() : "");
            
            if (component == null) {
                sendErrorResponse(peerId, requestId, FapiCode.NOT_FOUND, 
                    "Component not found: " + componentName);
                return;
            }
            
            // 预校验费用
            if (balanceManager != null) {
                // 估算响应大小：如果有二进制数据用实际大小，否则假设最小响应 1KB
                long estimatedResponseSize = (binaryData != null && binaryData.length > 0) ? binaryData.length : 1024;
                long estimatedFee = balanceManager.estimateFee(requestSize, estimatedResponseSize);
                
                // 检查 maxCost 限制
                if (fapiRequest.hasMaxCost() && estimatedFee > fapiRequest.getMaxCost()) {
                    sendErrorResponse(peerId, requestId, FapiCode.PAYMENT_REQUIRED, 
                        "Estimated cost (" + estimatedFee + " sat) exceeds maxCost (" + fapiRequest.getMaxCost() + " sat)");
                    return;
                }
                
                // 检查余额是否充足
                if (!balanceManager.canAfford(peerId, estimatedFee)) {
                    // Check if this is a whitelisted free broadcast (one-time recharge pass)
                    if ("base.broadcastTx".equals(fapiRequest.getApi())) {
                        Long expiry = broadcastWhitelist.remove(peerId);
                        if (expiry != null && System.currentTimeMillis() < expiry) {
                            log.info("Allowing whitelisted free broadcastTx for peer {}", peerId);
                            // Fall through to route the request without charging
                        } else {
                            sendErrorResponse(peerId, requestId, FapiCode.PAYMENT_REQUIRED,
                                "Insufficient balance for request");
                            return;
                        }
                    } else {
                        sendPaymentRequiredWithCashes(peerId, requestId);
                        return;
                    }
                }
            }
            
            // 路由到组件处理（使用统一请求格式）
            UnifiedResponse unifiedResponse = routeUnifiedRequest(fapiRequest, binaryData, peerId);
            
            // 填充余额信息
            fillBalanceInfo(unifiedResponse.response(), peerId);
            
            // 编码并发送响应
            if (fudpNode != null) {
                FapiResponse fapiResponse = unifiedResponse.response();
                int statusCode = fapiResponse.getCode() != null 
                    ? fapiResponse.getCode() : FapiCode.SUCCESS;
                
                // Check if the response has a streaming file source (e.g., disk.get)
                if (fapiResponse.hasStreamSource()) {
                    // === Streaming response path (avoids loading file into memory) ===
                    java.nio.file.Path streamPath = fapiResponse.getStreamSourcePath();
                    long streamSize = fapiResponse.getStreamSourceSize();
                    
                    // Compute total response size for billing: header + file content
                    byte[] headerBytes = UnifiedCodec.encodeResponseHeaderOnly(fapiResponse);
                    long totalResponseSize = headerBytes.length + streamSize;
                    
                    // 计算并收费
                    if (balanceManager != null && fapiResponse.isSuccess()) {
                        String requestKey = fapiRequest.getId() != null ? fapiRequest.getId() : 
                            peerId + ":" + requestId + ":" + System.currentTimeMillis();
                        String via = fapiRequest.getVia();
                        
                        long actualFee = balanceManager.estimateFee(requestSize, totalResponseSize);
                        if (fapiRequest.hasMaxCost() && actualFee > fapiRequest.getMaxCost()) {
                            sendErrorResponse(peerId, requestId, FapiCode.PAYMENT_REQUIRED, 
                                "Actual cost (" + actualFee + " sat) exceeds maxCost (" + fapiRequest.getMaxCost() + " sat)");
                            return;
                        }
                        
                        FapiBalanceManager.ChargeResult chargeResult = balanceManager.chargeByTraffic(
                            requestKey, peerId, requestSize, totalResponseSize, 
                            via != null ? "via:" + via : null);
                        
                        if (chargeResult != null) {
                            fapiResponse.setCharged(chargeResult.getAmount());
                            // Re-encode header to include charged field
                            headerBytes = UnifiedCodec.encodeResponseHeaderOnly(fapiResponse);
                        }
                    } else {
                        fapiResponse.setCharged(0L);
                        headerBytes = UnifiedCodec.encodeResponseHeaderOnly(fapiResponse);
                    }
                    
                    // Stream the response: header + file content via InputStream
                    log.debug("Streaming response to {}: requestId={}, statusCode={}, fileSize={}", 
                        peerId, requestId, statusCode, streamSize);
                    try (java.io.InputStream fileStream = java.nio.file.Files.newInputStream(streamPath)) {
                        fudpNode.respondWithStream(peerId, requestId, statusCode, 
                            headerBytes, fileStream, streamSize);
                    }
                    log.debug("Streaming response sent to {}: requestId={}", peerId, requestId);
                    
                } else {
                    // === Normal (non-streaming) response path ===
                    byte[] responseData = UnifiedCodec.encodeResponse(unifiedResponse);
                    
                    // 计算并收费（基于实际请求和响应大小）
                    if (balanceManager != null && fapiResponse.isSuccess()) {
                        String requestKey = fapiRequest.getId() != null ? fapiRequest.getId() : 
                            peerId + ":" + requestId + ":" + System.currentTimeMillis();
                        String via = fapiRequest.getVia();
                        
                        // 实际收费前再次检查 maxCost（基于实际响应大小）
                        long actualFee = balanceManager.estimateFee(requestSize, responseData.length);
                        if (fapiRequest.hasMaxCost() && actualFee > fapiRequest.getMaxCost()) {
                            // 实际费用超过限制，不收费，返回错误
                            sendErrorResponse(peerId, requestId, FapiCode.PAYMENT_REQUIRED, 
                                "Actual cost (" + actualFee + " sat) exceeds maxCost (" + fapiRequest.getMaxCost() + " sat)");
                            return;
                        }
                        
                        FapiBalanceManager.ChargeResult chargeResult = balanceManager.chargeByTraffic(
                            requestKey, peerId, requestSize, responseData.length, 
                            via != null ? "via:" + via : null);
                        
                        // 设置实际收费金额
                        if (chargeResult != null) {
                            fapiResponse.setCharged(chargeResult.getAmount());
                            // 重新编码响应以包含 charged 字段
                            responseData = UnifiedCodec.encodeResponse(unifiedResponse);
                        }
                    } else {
                        // 未收费时设置 charged 为 0
                        fapiResponse.setCharged(0L);
                        responseData = UnifiedCodec.encodeResponse(unifiedResponse);
                    }
                    
                    log.debug("Sending response to {}: requestId={}, statusCode={}, size={}", 
                        peerId, requestId, statusCode, responseData.length);
                    fudpNode.respond(peerId, requestId, statusCode, responseData);
                    log.debug("Response sent to {}: requestId={}", peerId, requestId);
                }
            }
            
        } catch (Exception e) {
            log.error("Error handling request from {}: {}", peerId, e.getMessage(), e);
            sendErrorResponse(peerId, requestId, FapiCode.INTERNAL_ERROR, "Internal server error");
        }
    }
    
    /**
     * 路由统一请求到组件
     * @param request FAPI请求
     * @param binaryData 可选的二进制数据
     * @param peerId 请求方FID
     * @return 统一响应（包含 FapiResponse 和可选的二进制数据）
     */
    private UnifiedResponse routeUnifiedRequest(FapiRequest request, byte[] binaryData, String peerId) {
        String requestId = request.getId();
        String api = request.getApi();
        String componentName = request.getComponentName();
        
        // 审计日志：请求开始
        auditLogger.logRequestStart(request, peerId);
        
        try {
            // 查找组件
            FapiComponent component = components.get(componentName);
            if (component == null) {
                FapiResponse errorResp = FapiResponse.error(requestId, FapiCode.NOT_FOUND, 
                    "Component not found: " + componentName);
                return new UnifiedResponse(errorResp, null);
            }
            
            // 组件健康检查
            if (!component.isHealthy()) {
                FapiResponse errorResp = FapiResponse.error(requestId, FapiCode.SERVICE_UNAVAILABLE, 
                    "Component not ready: " + componentName);
                return new UnifiedResponse(errorResp, null);
            }
            
            // 执行请求（使用统一接口）
            UnifiedResponse response = component.handleUnifiedRequest(request, binaryData, peerId);
            
            return response;
            
        } catch (Exception e) {
            log.error("Error routing request {} from {}: {}", api, peerId, e.getMessage(), e);
            auditLogger.logRequestError(request, peerId, e);
            FapiResponse errorResp = FapiResponse.error(requestId, FapiCode.INTERNAL_ERROR, 
                "Internal server error");
            return new UnifiedResponse(errorResp, null);
        }
    }
    
    /**
     * 处理旧版 DISK 二进制协议请求（PUT/CARVE）
     * <p>
     * 向后兼容：用于高效的文件上传，避免 Base64 编码开销
     * @deprecated 使用统一协议 UnifiedCodec 替代
     */
    @Deprecated
    private void handleLegacyBinaryDiskRequest(String peerId, long requestId, byte[] data) {
        try {
            // 获取 DISK 组件
            FapiComponent diskComponent = components.get("DISK");
            if (diskComponent == null) {
                log.error("DISK component not found");
                sendErrorResponse(peerId, requestId, FapiCode.NOT_FOUND, "DISK component not available");
                return;
            }
            
            if (!(diskComponent instanceof fapi.components.DiskComponent)) {
                log.error("DISK component is not of expected type");
                sendErrorResponse(peerId, requestId, FapiCode.INTERNAL_ERROR, "DISK component misconfigured");
                return;
            }
            
            // 调用二进制协议处理方法
            fapi.components.DiskComponent disk = (fapi.components.DiskComponent) diskComponent;
            byte[] responseData = disk.handleBinaryProtocolRequest(data, peerId);
            
            // 发送二进制响应
            if (fudpNode != null) {
                fudpNode.respond(peerId, requestId, FapiCode.SUCCESS, responseData);
            }
            
            log.debug("Handled binary DISK request from {}, response size: {} bytes", 
                    peerId, responseData != null ? responseData.length : 0);
            
        } catch (Exception e) {
            log.error("Error handling binary DISK request from {}: {}", peerId, e.getMessage(), e);
            sendErrorResponse(peerId, requestId, FapiCode.INTERNAL_ERROR, "DISK operation failed: " + e.getMessage());
        }
    }
    
    /**
     * 发送错误响应（使用统一协议编码）
     */
    private void sendErrorResponse(String peerId, long requestId, int code, String message) {
        if (fudpNode != null) {
            try {
                FapiResponse errorResponse = FapiResponse.error(null, code, message);
                // 错误响应也填充余额信息
                fillBalanceInfo(errorResponse, peerId);
                byte[] responseData = UnifiedCodec.encodeResponse(errorResponse, null);
                fudpNode.respond(peerId, requestId, code, responseData);
            } catch (Exception e) {
                log.error("Failed to send error response to {}: {}", peerId, e.getMessage());
            }
        }
    }
    
    /**
     * Send PAYMENT_REQUIRED error with the client's valid cash list in response.data,
     * and grant a one-time free broadcastTx pass.
     */
    private void sendPaymentRequiredWithCashes(String peerId, long requestId) {
        if (fudpNode == null) return;
        try {
            FapiResponse errorResponse = FapiResponse.error(null, FapiCode.PAYMENT_REQUIRED,
                    "Insufficient balance for request");
            fillBalanceInfo(errorResponse, peerId);

            // Query valid cashes for the client so they can build a recharge TX
            try {
                long minPaymentSatoshi = parseMinPaymentSatoshi();
                if (minPaymentSatoshi > 0) {
                    List<Cash> cashes = queryValidCashesForRecharge(peerId, minPaymentSatoshi);
                    if (cashes != null && !cashes.isEmpty()) {
                        errorResponse.setData(cashes);
                        broadcastWhitelist.put(peerId,
                                System.currentTimeMillis() + BROADCAST_WHITELIST_TTL_MS);
                        log.info("PAYMENT_REQUIRED for {}: included {} cashes, granted broadcast whitelist",
                                peerId, cashes.size());
                    } else {
                        log.info("PAYMENT_REQUIRED for {}: no valid cashes found on-chain", peerId);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to query cashes for {}: {}", peerId, e.getMessage());
            }

            byte[] responseData = UnifiedCodec.encodeResponse(errorResponse, null);
            fudpNode.respond(peerId, requestId, FapiCode.PAYMENT_REQUIRED, responseData);
        } catch (Exception e) {
            log.error("Failed to send PAYMENT_REQUIRED with cashes to {}: {}", peerId, e.getMessage());
        }
    }

    /**
     * Parse the minPayment field from the current service config (in satoshi).
     */
    private long parseMinPaymentSatoshi() {
        if (service == null || service.getMinPayment() == null || service.getMinPayment().isEmpty()) {
            return 0;
        }
        try {
            double minPaymentFch = Double.parseDouble(service.getMinPayment());
            return FchUtils.coinToSatoshi(minPaymentFch);
        } catch (NumberFormatException e) {
            log.warn("Invalid minPayment format: {}", service.getMinPayment());
            return 0;
        }
    }

    /**
     * Query Elasticsearch for valid cashes owned by the given FID,
     * sufficient to cover the specified payment amount (in satoshi).
     */
    private List<Cash> queryValidCashesForRecharge(String fid, long amountSatoshi) {
        try {
            ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);
            if (esClient == null) {
                log.warn("ElasticsearchClient not available for cash query");
                return null;
            }

            SearchRequest.Builder searchBuilder = new SearchRequest.Builder();
            searchBuilder.index("cash");
            searchBuilder.size(200);
            searchBuilder.sort(s -> s.field(f -> f.field("cd").order(SortOrder.Asc)));
            searchBuilder.sort(s -> s.field(f -> f.field("id").order(SortOrder.Asc)));

            BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
            boolBuilder.must(new Query.Builder().term(TermQuery.of(t -> t.field("owner").value(fid))).build());
            boolBuilder.must(new Query.Builder().term(TermQuery.of(t -> t.field("valid").value(true))).build());
            searchBuilder.query(q -> q.bool(boolBuilder.build()));

            SearchResponse<Cash> result = esClient.search(searchBuilder.build(), Cash.class);
            if (result.hits() == null || result.hits().hits() == null || result.hits().hits().isEmpty()) {
                return null;
            }

            List<Cash> allCashes = new ArrayList<>();
            for (Hit<Cash> hit : result.hits().hits()) {
                if (hit.source() != null) allCashes.add(hit.source());
            }

            Long bestHeight = settings.getBestHeight();
            long fchSum = 0;
            long fee = 0;
            List<Cash> meetList = new ArrayList<>();

            for (Cash cash : allCashes) {
                if (bestHeight != null && "coinbase".equals(cash.getIssuer())) {
                    long age = bestHeight - cash.getBirthHeight();
                    if (FUND_FID.equals(cash.getOwner()) ? age < 10000 : age < 100) continue;
                }
                meetList.add(cash);
                fchSum += cash.getValue();
                long txSize = TxCreator.calcTxSize(meetList.size(), 1, 0);
                fee = TxCreator.calcFee(txSize, TxCreator.DEFAULT_FEE_RATE);
                if (fchSum >= (amountSatoshi + fee)) {
                    return meetList;
                }
            }

            // Return whatever we have even if insufficient -- client will see the shortfall
            return meetList.isEmpty() ? null : meetList;
        } catch (Exception e) {
            log.error("Error querying valid cashes for {}: {}", fid, e.getMessage());
            return null;
        }
    }

    /**
     * 当收到 PONG 时，可以从 advertise data 中解析服务信息
     */
    @Override
    public void onPongInfo(String peerId, byte[] data) {
        if (data == null || data.length == 0) {
            log.debug("onPongInfo: no advertise data from {}", peerId);
            return;
        }
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            log.debug("onPongInfo: received advertise data from {}: {}", peerId, json);
            // Parse and process advertise data if needed
        } catch (Exception e) {
            log.warn("onPongInfo: failed to parse advertise data from {}: {}", peerId, e.getMessage());
        }
    }
    
    /**
     * 构建本服务的广告信息（用于 PONG 响应）
     */
    public byte[] buildAdvertiseData(String peerId) {
        try {
            if (serviceMap.isEmpty()) {
                log.debug("buildAdvertiseData: no services to advertise for {}", peerId);
                return new byte[0];
            }
            List<Map<String, Object>> services = new ArrayList<>();
            for (Service svc : serviceMap.values()) {
                Map<String, Object> item = new HashMap<>();
                item.put(SID, svc.getId());
                item.put(NAME, svc.getStdName());
                // Use enum's toString() so type is "FAPI@No1_NrC7" (canonical @ form), not name() "FAPI_No1_NrC7"
                ServiceType st = svc.fetchServiceType();
                item.put(TYPE, st != null ? st.toString() : null);
                item.put(VER, svc.getVer());
                item.put(DEALER_PUBKEY, svc.getDealerPubkey());
                if (svc.getComponents() != null && !svc.getComponents().isEmpty()) {
                    item.put(COMPONENTS, svc.getComponents());
                }
                // Use Service fields directly for pricing info
                if (svc.getPricePerKB() != null) {
                    item.put(PRICE_PER_K_B, svc.getPricePerKB());
                }
                if (svc.getMinPayment() != null) {
                    item.put(MIN_PAYMENT, svc.getMinPayment());
                }
                if (svc.getMinCredit() != null) {
                    item.put(MIN_CREDIT, svc.getMinCredit());
                }
                services.add(item);
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put(SERVICES, services);
            String json = JsonUtils.toJson(payload);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            log.debug("buildAdvertiseData: advertising {} services ({} bytes) to {}", services.size(), bytes.length, peerId);
            return bytes;
        } catch (Exception e) {
            log.warn("buildAdvertiseData: failed for {}: {}", peerId, e.getMessage());
            return new byte[0];
        }
    }

    public Settings getSettings() {
        return settings;
    }
    
    public Service getService() {
        return service;
    }
    
    public void setService(Service service) {
        this.service = service;
    }
    
    // ==================== 组件管理方法 ====================
    
    /**
     * 根据 components 加载组件
     */
    public void loadComponentsByTypes(String[] types) {
        ComponentRegistry registry = new ComponentRegistry();
        List<FapiComponent> loaded = registry.loadComponents(types);
        for (FapiComponent component : loaded) {
            registerComponent(component);
        }
    }
    
    /**
     * 注册组件
     */
    public void registerComponent(FapiComponent component) {
        if (component == null) {
            return;
        }
        component.initialize(this);
        components.put(component.getName().toUpperCase(), component);
        componentsByClass.put(component.getClass(), component);
        log.info("Registered component: {}", component.getName());
    }
    
    /**
     * 获取组件引用（按名称）
     */
    @SuppressWarnings("unchecked")
    public <T extends FapiComponent> T getComponent(String name) {
        if (name == null) {
            return null;
        }
        return (T) components.get(name.toUpperCase());
    }
    
    /**
     * 类型安全的组件获取（推荐）
     */
    @SuppressWarnings("unchecked")
    public <T extends FapiComponent> T getComponent(Class<T> componentClass) {
        return (T) componentsByClass.get(componentClass);
    }
    
    /**
     * 获取所有已注册的组件
     */
    public List<FapiComponent> getComponents() {
        return new ArrayList<>(components.values());
    }
    
    /**
     * 路由请求到对应组件
     * @param request FAPI请求
     * @param peerId 请求方FID（已通过FUDP层认证）
     * @return FAPI响应
     */
    public FapiResponse routeRequest(FapiRequest request, String peerId) {
        String requestId = request.getId();
        long startTime = System.currentTimeMillis();
        String api = request.getApi();
        String componentName = request.getComponentName();
        
        // 审计日志：请求开始
        auditLogger.logRequestStart(request, peerId);
        
        try {
            // 1. 输入验证
            ValidationResult validationResult = RequestValidator.validate(request);
            if (!validationResult.isValid()) {
                FapiResponse errorResp = FapiResponse.error(requestId, 
                    validationResult.getCode(), validationResult.getMessage());
                recordMetricsAndAudit(request, errorResp, peerId, startTime, false);
                return errorResp;
            }
            
            // 2. 验证请求格式
            if (api == null || !api.contains(".")) {
                FapiResponse errorResp = FapiResponse.error(requestId, FapiCode.BAD_REQUEST, 
                    "Invalid api format, expected: component.method");
                recordMetricsAndAudit(request, errorResp, peerId, startTime, false);
                return errorResp;
            }
            
            // 3. 查找组件
            FapiComponent component = components.get(componentName);
            if (component == null) {
                FapiResponse errorResp = FapiResponse.error(requestId, FapiCode.NOT_FOUND, 
                    "Component not found: " + componentName);
                recordMetricsAndAudit(request, errorResp, peerId, startTime, false);
                return errorResp;
            }
            
            // 4. 组件健康检查
            if (!component.isHealthy()) {
                FapiResponse errorResp = FapiResponse.error(requestId, FapiCode.SERVICE_UNAVAILABLE, 
                    "Component not ready: " + componentName);
                recordMetricsAndAudit(request, errorResp, peerId, startTime, false);
                return errorResp;
            }
            
            // 5. 执行请求
            FapiResponse response;
            if (component.supportsAsync()) {
                response = component.handleRequestAsync(request, peerId).join();
            } else {
                response = component.handleRequest(request, peerId);
            }
            
            // 6. 填充余额信息
            fillBalanceInfo(response, peerId);
            
            // 7. 记录指标和审计
            boolean success = response.isSuccess();
            recordMetricsAndAudit(request, response, peerId, startTime, success);
            
            return response;
            
        } catch (Exception e) {
            log.error("Error routing request {} from {}: {}", api, peerId, e.getMessage(), e);
            auditLogger.logRequestError(request, peerId, e);
            
            FapiResponse errorResp = FapiResponse.error(requestId, FapiCode.INTERNAL_ERROR, 
                "Internal server error");
            metricsCollector.recordRequest(componentName != null ? componentName : "UNKNOWN", 
                api, System.currentTimeMillis() - startTime, false);
            return errorResp;
        }
    }
    
    /**
     * 记录指标和审计日志
     */
    private void recordMetricsAndAudit(FapiRequest request, FapiResponse response, 
            String peerId, long startTime, boolean success) {
        long durationMs = System.currentTimeMillis() - startTime;
        String componentName = request.getComponentName();
        String api = request.getApi();
        
        // 记录性能指标
        metricsCollector.recordRequest(
            componentName != null ? componentName : "UNKNOWN", 
            api, 
            durationMs, 
            success
        );
        
        // 审计日志：请求完成
        auditLogger.logRequestComplete(request, response, peerId, durationMs);
    }
    
    /**
     * 填充余额信息到响应
     */
    private void fillBalanceInfo(FapiResponse response, String peerId) {
        // 使用内置的余额管理器
        if (balanceManager != null) {
            try {
                FapiBalanceManager.BalanceView balance = balanceManager.getBalance(peerId);
                if (balance != null) {
                    response.setBalance(balance.getBalance());
                    response.setBalanceSeq(balance.getSeq());
//                    log.debug("fillBalanceInfo: peerId={}, balance={} sat ({} FCH), available={} sat, seq={}",
//                            peerId, balance.getBalance(),
//                            balance.getBalance() / 100_000_000.0, balance.getAvailable(), balance.getSeq());
                } else {
                    log.warn("fillBalanceInfo: balance is null for peerId={}", peerId);
                }
            } catch (Exception e) {
                log.warn("Failed to fill balance for peer {}: {}", peerId, e.getMessage(), e);
            }
        } else {
            log.warn("fillBalanceInfo: balanceManager is null, cannot fill balance for peerId={}", peerId);
        }
        // 填充最佳区块高度和区块ID
        if (settings != null) {
            data.fchData.Block bestBlock = settings.getBestBlock();
            if (bestBlock != null) {
                response.setBestHeight(bestBlock.getHeight());
                response.setBestBlockId(bestBlock.getId());
            } else {
                // 回退到只获取高度
                response.setBestHeight(settings.getBestHeight());
            }
        }
    }
    
    /**
     * 关闭所有组件
     */
    public void stopComponents() {
        // 关闭请求处理线程池
        requestExecutor.shutdown();
        try {
            if (!requestExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                requestExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            requestExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // 关闭区块事件调度器
        if (blockEventDispatcher != null) {
            log.info("Stopping BlockEventDispatcher...");
            try {
                blockEventDispatcher.close();
            } catch (java.io.IOException e) {
                log.warn("Error closing BlockEventDispatcher", e);
            }
        }
        
        // 清理远程客户端缓存
        clearRemoteClients();
        
        for (FapiComponent component : components.values()) {
            try {
                log.info("Stopping component: {}", component.getName());
                component.close(5000);
            } catch (InterruptedException e) {
                log.warn("Component {} stop interrupted", component.getName());
                Thread.currentThread().interrupt();
            }
        }
        components.clear();
        componentsByClass.clear();
    }
    
    /**
     * 关闭余额管理器
     */
    public void closeBalanceManager() {
        if (balanceManager != null) {
            try {
                balanceManager.close();
                log.info("FapiBalanceManager closed");
            } catch (Exception e) {
                log.warn("Error closing FapiBalanceManager", e);
            }
        }
    }
    
    // ==================== 远程客户端管理方法 ====================
    
    /**
     * 获取或创建远程FAPI服务的客户端
     * <p>
     * 连接被缓存以供重用。用于组件之间的服务器到服务器通信，
     * 例如 DOCK 转发和 ROAD 链式转发。
     * 
     * @param url 远程服务URL (host:port 或 fudp://host:port)
     * @return FapiClient，如果连接失败则返回 null
     */
    public FapiClient getOrCreateClient(String url) {
        if (url == null || url.isEmpty()) {
            log.warn("getOrCreateClient: URL is null or empty");
            return null;
        }
        
        // 检查缓存
        FapiClient cached = remoteClients.get(url);
        if (cached != null) {
            clientLastUsed.put(url, System.currentTimeMillis());
            log.debug("getOrCreateClient: using cached client for {}", url);
            return cached;
        }
        
        // 创建新连接
        try {
            if (fudpNode == null) {
                log.error("getOrCreateClient: FudpNode is not available");
                return null;
            }
            
            String host = FapiDefaults.getHost(url);
            int port = FapiDefaults.getPort(url);
            
            if (isSelfAddress(host, port)) {
                log.debug("getOrCreateClient: {} points to this server, skipping", url);
                return null;
            }
            
            log.info("getOrCreateClient: connecting to {}:{} ...", host, port);
            
            FapiClient.DiscoveryResult discovery = FapiClient.discoverViaHelloAndPing(
                fudpNode, host, port, REMOTE_CLIENT_TIMEOUT_MS, REMOTE_CLIENT_TIMEOUT_MS);
            
            if (discovery != null && !discovery.getServices().isEmpty()) {
                FapiClient client = new FapiClient(fudpNode, discovery.getPeerId(),
                    discovery.getServices().get(0).getId(), REMOTE_CLIENT_REQUEST_TIMEOUT_SEC, settings);
                client.setServerUrl(url);
                
                remoteClients.put(url, client);
                clientLastUsed.put(url, System.currentTimeMillis());
                log.info("getOrCreateClient: connected to {} -> peerId={}, sid={}", 
                        url, discovery.getPeerId(), discovery.getServices().get(0).getId());
                return client;
            } else {
                log.warn("getOrCreateClient: no FAPI services found at {}", url);
            }
        } catch (Throwable e) {
            log.warn("getOrCreateClient: failed to connect to {}: {}", url, e.getMessage());
        }
        return null;
    }
    
    /**
     * Check if a host:port refers to this server's own FUDP node.
     * Prevents self-connection attempts (which would time out).
     * <p>
     * Note: Cannot detect NAT/virtual aliases (e.g. Android emulator's 10.0.2.2).
     * The ROAD component handles such cases via fallback to direct FUDP send.
     */
    public boolean isSelfAddress(String host, int port) {
        if (fudpNode == null || host == null) return false;
        
        int myPort = fudpNode.getConfig().getPort();
        if (port != myPort) return false;
        
        try {
            java.net.InetAddress addr = java.net.InetAddress.getByName(host);
            if (addr.isLoopbackAddress()) return true;
            return java.net.NetworkInterface.getByInetAddress(addr) != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if a URL (e.g. "fudp://host:port") refers to this server.
     */
    public boolean isSelfUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            String host = FapiDefaults.getHost(url);
            int port = FapiDefaults.getPort(url);
            boolean result = isSelfAddress(host, port);
            log.debug("isSelfUrl('{}') -> host={}, port={}, myPort={}, result={}", 
                    url, host, port, fudpNode != null ? fudpNode.getConfig().getPort() : -1, result);
            return result;
        } catch (Exception e) {
            log.warn("isSelfUrl('{}') threw: {}", url, e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取所有缓存的远程客户端
     * 
     * @return 不可修改的客户端映射
     */
    public Map<String, FapiClient> getRemoteClients() {
        return java.util.Collections.unmodifiableMap(remoteClients);
    }
    
    /**
     * 移除缓存的客户端（例如，连接失败时）
     * 
     * @param url 远程服务URL
     */
    public void removeClient(String url) {
        if (url != null) {
            FapiClient removed = remoteClients.remove(url);
            clientLastUsed.remove(url);
            if (removed != null) {
                log.info("removeClient: removed cached client for {}", url);
            }
        }
    }
    
    /**
     * 清理所有远程客户端连接
     */
    public void clearRemoteClients() {
        int count = remoteClients.size();
        remoteClients.clear();
        clientLastUsed.clear();
        if (count > 0) {
            log.info("clearRemoteClients: cleared {} cached clients", count);
        }
    }
    
    /**
     * 获取已缓存的远程客户端数量
     */
    public int getRemoteClientCount() {
        return remoteClients.size();
    }
    
    // ==================== 监控与健康检查 ====================
    
    /**
     * 获取健康状态
     */
    public HealthStatus getHealthStatus() {
        if (healthChecker == null) {
            healthChecker = new HealthChecker(this);
        }
        return healthChecker.check();
    }
    
    /**
     * 快速健康检查
     */
    public boolean isHealthy() {
        if (healthChecker == null) {
            healthChecker = new HealthChecker(this);
        }
        return healthChecker.isHealthy();
    }
    
    /**
     * 获取性能指标报告
     */
    public MetricsReport getMetricsReport() {
        return metricsCollector.getReport();
    }
    
    /**
     * 获取性能指标收集器
     */
    public MetricsCollector getMetricsCollector() {
        return metricsCollector;
    }
    
    /**
     * 获取健康检查器
     */
    public HealthChecker getHealthChecker() {
        if (healthChecker == null) {
            healthChecker = new HealthChecker(this);
        }
        return healthChecker;
    }
    
    /**
     * 获取审计日志器
     */
    public AuditLogger getAuditLogger() {
        return auditLogger;
    }
}
