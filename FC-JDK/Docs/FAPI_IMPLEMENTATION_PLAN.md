# FAPI 模块实现计划

## 1. 概述

FAPI (Freecash API) 模块是基于 FUDP 协议的查询服务，提供对 FchParser 和 FEIP 写入 Elasticsearch 数据的查询能力。与基于 HTTP 的 APIP 服务不同，FAPI 利用 FUDP 协议内置的身份认证、加密、防重放攻击与传输计量，上层自带经济模型，无需额外的 HTTP 基础设施。

### 1.1 设计目标

- **复用查询逻辑**：复用 `FcHttpRequestHandler` 的核心 Elasticsearch 查询逻辑
- **适配 FUDP 协议**：使用 FUDP 的 Request/Response 消息格式
- **简化架构**：去除 HTTP 相关依赖，直接处理字节流
- **保持兼容性**：响应格式与 APIP 保持一致，便于客户端统一处理

### 1.2 关键差异

| 特性 | FcHttpRequestHandler (HTTP) | FcFudpRequestHandler (FUDP) |
|------|----------------------------|----------------------------|
| 协议层 | HTTP/HTTPS | FUDP (基于 UDP) |
| 身份认证 | HTTP Header (Session/PublicKey) | FUDP 内置认证 |
| 加密 | HTTP 层加密 (AES/ECC) | FUDP 内置加密 |
| 防重放 | 自定义实现 | FUDP 内置防重放 |
| 经济模型 | 账户管理器 | FAPI 经济模块（基于传输计量） |
| 请求格式 | HTTP Request Body (JSON) | RequestMessage.data (byte[]) |
| 响应格式 | HTTP Response Body (JSON) | ResponseMessage.data (byte[]) |
| 数据查询 | 通过 APIP 服务 | 直接查询 Elasticsearch 或 NASA_RPC |

**重要说明**：
- **FAPI 是 APIP 的替代**，不依赖 APIP 服务
- **数据查询方式**：
  - 主要使用 **Elasticsearch** 直接查询索引（Block、Tx、Cid、Cash 等）
  - 可选使用 **NASA_RPC** 查询 UTXO 并转换为 Cash（通过 `CashManager.getCashListFromNasaNode()`）
  - 不使用 APIP Client 进行数据查询
- **IncomeManager** 的收入检测也遵循此原则，只使用 ES 或 NASA_RPC

## 2. 架构设计

### 2.1 模块结构

```
FC-JDK/src/main/java/
    └── fapi/
        ├── handler/
        │   ├── FcFudpRequestHandler.java      # 核心请求处理器
        │   └── FapiRequestRouter.java         # 请求路由（可选）
        ├── message/
        │   ├── FapiRequest.java               # FAPI 请求包装类
        │   └── FapiResponse.java              # FAPI 响应包装类
        ├── service/
        │   ├── FapiServer.java                # FAPI 服务主类
        │   └── FapiServerRegistry.java        # 服务注册表
        └── util/
            ├── FcdslParser.java                # FCDSL 解析工具
            └── ResponseBuilder.java             # 响应构建工具
```

### 2.2 核心类设计

#### 2.2.1 FcFudpRequestHandler

**职责**：
- 解析 FUDP RequestMessage 中的 FCDSL JSON 数据
- 执行 Elasticsearch 查询（复用 `FcHttpRequestHandler` 逻辑）
- 构建响应数据并封装到 ResponseMessage

**主要方法**：
```java
public class FcFudpRequestHandler {
    // 核心查询方法（复用 FcHttpRequestHandler 逻辑）
    public <T> List<T> doRequest(String index, List<Sort> defaultSortList, Class<T> tClass);
    public <T> List<T> doIdsRequest(String index, Class<T> clazz);
    public <T> Map<String, T> doRequestForMap(String indexName, Class<T> tClass, String keyFieldName);
    
    // FUDP 特定方法
    public ResponseMessage handleRequest(RequestMessage request, String peerId);
    public byte[] buildResponseData(Object data, Long got, Long total, List<String> last);
}
```

**与 FcHttpRequestHandler 的关系**：
- **继承或组合**：建议使用**组合**而非继承，因为两者职责不同（HTTP vs FUDP）
- **代码复用**：将 `FcHttpRequestHandler` 中的查询逻辑提取为**共享工具类**或**内部方法**
- **依赖注入**：通过构造函数注入 `ElasticsearchClient` 和 `Settings`

#### 2.2.2 FapiRequest / FapiResponse

**FapiRequest**：
```java
public class FapiRequest {
    private String sid;              // Service ID (from RequestMessage)
                                    // sid 是身份在链上注册服务的ID，值为注册交易的交易ID
                                    // 服务类型不写入sid中，而是在链上注册时通过Service.types字段标注
    private Service service;         // 从ES获取的Service对象（启动时加载）
    private Fcdsl fcdsl;             // 解析后的 FCDSL 对象
    private String index;             // 目标索引名称（从 fcdsl.index 或 fcdsl.entity 中确定）
    private String entityType;        // 实体类型（从 fcdsl.entity 获取，如 "block", "tx", "cid"）
    private String peerId;            // 请求来源的 Peer ID
}
```

**FapiResponse**：
```java
public class FapiResponse {
    private int code;                 // 状态码（对应 CodeMessage）
    private String message;           // 错误消息
    private Object data;             // 查询结果数据
    private Long got;                 // 返回数量
    private Long total;               // 总数量
    private List<String> last;       // 分页游标
    private Long bestHeight;          // 最佳区块高度
    // 注意：FUDP 不再自动填充 balance/balanceSequenceNumber；如需返回余额，由 FAPI 经济模块基于计量决定并填充
}
```

### 2.3 请求处理流程

```
1. FUDP RequestMessage 到达
   ↓
2. 从 RequestMessage 提取信息
   - 提取 sid (RequestMessage.getSid()，即 Service ID，注册交易的交易ID)
   - 提取 data (RequestMessage.getData()，FCDSL JSON bytes)
   ↓
3. 验证 sid 并获取 Service 对象
   - 从已加载的Service对象中查找 sid == Service.id
   - 验证 Service.types 包含 "FAPI"
   - 如果验证失败，返回错误响应
   ↓
4. 解析 FCDSL JSON
   - 使用 JsonUtils.fromJson() 反序列化
   - 验证 FCDSL 有效性（isBadFcdsl()）
   ↓
5. 确定目标索引和实体类型
   - 优先使用 fcdsl.index（如果存在）
   - 如果 fcdsl.index 为空，从 fcdsl.entity 映射到索引名称
   - 根据索引名称确定对应的实体类（Block.class, Tx.class, Freer.class）
   ↓
6. 执行查询
   - 调用 doRequest() / doIdsRequest() / doRequestForMap()
   - 使用 ElasticsearchClient 查询
   ↓
7. 构建响应
   - 创建 FapiResponse 对象
   - 设置 code, data, got, total, last, bestHeight
   - 序列化为 JSON bytes
   ↓
8. 封装 ResponseMessage
   - statusCode = 0 (成功) 或错误码
   - data = JSON bytes（序列化后的 FapiResponse）
   - 如需余额字段，由 FAPI 经济模块填充；FUDP 不自动处理 balance/balanceSequenceNumber
   - 调用 `fudpNode.respond(peerId, requestId, statusCode, data)` 发送响应
   ↓
9. FUDP 协议层处理响应
   - 仅负责传输与计量，不处理经济逻辑
   - 将 ResponseMessage 发送给请求方
```

### 2.4 服务广告（PING/PONG 扩展）

- FudpNode 新增 `pongDataProvider`，FAPI 在 `FapiServer.setFudpNode()` 时注册，用于在收到携带 `WANT_PONG_INFO` 的 PING 后回复服务信息。
- PONG data 默认上限 1KB，节点按 peer 做最小 2s 的速率限制，超限截断，防止放大/DoS。
- 建议返回紧凑 JSON：
  ```json
  {
    "services": [
      {
        "sid": "<txid>",
        "types": ["FAPI"],
        "ver": "1.0.0",
        "pricePerKB": "0.00000001",
        "minPayment": "0.01",
        "dealerPubkey":"037d81362d2e91e5766b047fba488b5d2f44212eb314bf9039659f4da0004a8186"
      }
    ]
  }
  ```
- 如需更详细数据（>1KB），对端可基于返回的 sid 再发 REQUEST 获取。

## 3. 实现细节

### 3.1 FCDSL 解析

**输入**：`RequestMessage.data` (byte[])
**输出**：`Fcdsl` 对象

```java
public static Fcdsl parseFcdslFromBytes(byte[] data) {
    try {
        String jsonStr = new String(data, StandardCharsets.UTF_8);
        Fcdsl fcdsl = JsonUtils.fromJson(jsonStr, Fcdsl.class);
        if (fcdsl.isBadFcdsl()) {
            throw new IllegalArgumentException("Invalid FCDSL");
        }
        return fcdsl;
    } catch (Exception e) {
        throw new IllegalArgumentException("Failed to parse FCDSL", e);
    }
}
```

### 3.2 Entity 到索引映射

**Entity 到索引名称映射表**：
```java
// Entity 类型到索引的映射（用于 fcdsl.entity）
private static final Map<String, IndexMapping> ENTITY_INDEX_MAP = Map.of(
    "block", new IndexMapping(IndicesNames.BLOCK, Block.class, "id"),
    "tx", new IndexMapping(IndicesNames.TX, Tx.class, "id"),
    "cid", new IndexMapping(IndicesNames.CID, Freer.class, "id"),
    "cash", new IndexMapping(IndicesNames.CASH, Cash.class, "id"),
    "opreturn", new IndexMapping(IndicesNames.OPRETURN, OpReturn.class, "id"),
    "contact", new IndexMapping(IndicesNames.CONTACT, Contact.class, "id"),
    "mail", new IndexMapping(IndicesNames.MAIL, Mail.class, "id"),
    // ... 其他实体映射
);

// 索引名称到实体类的映射（用于 fcdsl.index，支持自定义索引）
private final Map<String, IndexMapping> indexNameMapping = new ConcurrentHashMap<>();

class IndexMapping {
    String indexName;
    Class<?> entityClass;
    String idFieldName;
}
```

**初始化索引映射**：
```java
// 启动时初始化标准索引映射
private void initializeIndexMappings() {
    // 从 ENTITY_INDEX_MAP 初始化索引名称映射
    for (IndexMapping mapping : ENTITY_INDEX_MAP.values()) {
        indexNameMapping.put(mapping.indexName, mapping);
    }
    
    // 可以加载配置文件或从数据库加载自定义索引映射
    // loadCustomIndexMappings();
}

// 支持动态添加自定义索引映射
public void addCustomIndexMapping(String indexName, Class<?> entityClass, String idFieldName) {
    indexNameMapping.put(indexName, new IndexMapping(indexName, entityClass, idFieldName));
}
```

**索引确定逻辑**：
1. **优先使用 `fcdsl.index`**（如果存在且非空）
2. **如果 `fcdsl.index` 为空**，从 `fcdsl.entity` 映射到索引名称
   - 使用 `ENTITY_INDEX_MAP` 查找对应的 `IndexMapping`
3. 如果两者都不存在或无法映射，返回错误

**实现示例**：
```java
private IndexMapping determineIndexMapping(Fcdsl fcdsl) {
    // 优先使用 fcdsl.index
    if (fcdsl.getIndex() != null && !fcdsl.getIndex().isEmpty()) {
        String indexName = fcdsl.getIndex();
        // 从索引名称映射表中查找（支持自定义索引）
        IndexMapping mapping = indexNameMapping.get(indexName);
        if (mapping != null) {
            return mapping;
        }
        throw new IllegalArgumentException("Cannot find mapping for index: " + indexName);
    }
    
    // 如果 index 为空，使用 entity 映射
    if (fcdsl.getEntity() != null && !fcdsl.getEntity().isEmpty()) {
        IndexMapping mapping = ENTITY_INDEX_MAP.get(fcdsl.getEntity());
        if (mapping != null) {
            return mapping;
        }
        throw new IllegalArgumentException("Unknown entity type: " + fcdsl.getEntity());
    }
    
    throw new IllegalArgumentException("Cannot determine index: both index and entity are missing or invalid");
}
```

### 3.3 查询方法适配

**需要适配的方法**（来自 `FcHttpRequestHandler`）：

1. **索引和实体类型确定**
   - 优先使用 `fcdsl.index`
   - 如果 `fcdsl.index` 为空，从 `fcdsl.entity` 映射到索引名称
   - 根据索引名称确定实体类和 ID 字段名

2. **通用查询**：`doRequest(String index, List<Sort> defaultSortList, Class<T> tClass)`
   - 直接复用，无需修改
   - `index` 从 `fcdsl.index` 或 `fcdsl.entity` 确定

3. **IDs 查询**：`doIdsRequest(String index, Class<T> clazz)`
   - 需要从 `Fcdsl.ids` 获取 ID 列表
   - 移除 HTTP 相关参数
   - `index` 从 `fcdsl.index` 或 `fcdsl.entity` 确定

4. **Map 查询**：`doRequestForMap(String indexName, Class<T> tClass, String keyFieldName)`
   - 需要确定 keyFieldName（从 Entity 映射表获取）
   - `indexName` 从 `fcdsl.index` 或 `fcdsl.entity` 确定

### 3.4 响应构建

**成功响应**：
```java
public byte[] buildSuccessResponse(Object data, Long got, Long total, List<String> last, Settings settings) {
    FapiResponse response = new FapiResponse();
    response.setCode(CodeMessage.Code0Success);
    response.setMessage(CodeMessage.getMsg(CodeMessage.Code0Success));
    response.setData(data);
    response.setGot(got);
    response.setTotal(total);
    response.setLast(last);
    
    // 获取最佳区块高度（从 ES 或 NaSaRpcClient）
    Long bestHeight = settings.getBestHeight();
    response.setBestHeight(bestHeight);
    
    // 注意：FUDP 不自动填充 balance/balanceSequenceNumber；如需返回余额，由 FAPI 经济模块基于计量填充
    
    return JsonUtils.toJson(response).getBytes(StandardCharsets.UTF_8);
}
```

**错误响应**：
```java
/**
 * 构建错误响应
 * @param code 错误码（CodeMessage 常量）
 * @param customMessage 自定义错误消息（可选，如果为null则使用CodeMessage中的默认消息）
 * @param settings Settings对象（用于获取bestHeight）
 * @return 序列化后的JSON字节数组
 */
public byte[] buildErrorResponse(int code, String customMessage, Settings settings) {
    FapiResponse response = new FapiResponse();
    response.setCode(code);
    response.setMessage(customMessage != null ? customMessage : CodeMessage.getMsg(code));
    response.setData(null);
    response.setGot(0L);
    response.setTotal(0L);
    
    // 设置最佳区块高度
    if (settings != null) {
        Long bestHeight = settings.getBestHeight();
        response.setBestHeight(bestHeight);
    }
    
    return JsonUtils.toJson(response).getBytes(StandardCharsets.UTF_8);
}

/**
 * 构建错误响应（使用默认消息）
 */
public byte[] buildErrorResponse(int code, Settings settings) {
    return buildErrorResponse(code, null, settings);
}

/**
 * 构建错误响应JSON字符串（用于日志等场景）
 */
public String buildErrorJson(int code, String customMessage) {
    FapiResponse response = new FapiResponse();
    response.setCode(code);
    response.setMessage(customMessage != null ? customMessage : CodeMessage.getMsg(code));
    response.setData(null);
    response.setGot(0L);
    response.setTotal(0L);
    return JsonUtils.toJson(response);
}
```

### 3.5 错误处理

**错误码映射**：
- `CodeMessage.Code1011DataNotFound` → `ResponseMessage.STATUS_NOT_FOUND (404)`
- `CodeMessage.Code1012BadQuery` → `ResponseMessage.STATUS_BAD_REQUEST (400)`
- `CodeMessage.Code1013BadRequest` → `ResponseMessage.STATUS_BAD_REQUEST (400)`
- `CodeMessage.Code1020OtherError` → `ResponseMessage.STATUS_ERROR (1)`
- 其他错误 → `ResponseMessage.STATUS_INTERNAL_ERROR (500)`

**异常处理**：
```java
try {
    // 处理请求
} catch (IllegalArgumentException e) {
    return buildErrorResponse(ResponseMessage.STATUS_BAD_REQUEST, e.getMessage());
} catch (Exception e) {
    log.error("Failed to handle FAPI request", e);
    return buildErrorResponse(ResponseMessage.STATUS_INTERNAL_ERROR, "Internal server error");
}
```

## 4. 与 FUDP Node 集成

### 4.1 消息处理机制

**FUDP 消息类型**：
- FAPI 使用 FUDP 已定义的消息类型：
  - `MessageType.REQUEST` (0x10) - 应用请求
  - `MessageType.RESPONSE` (0x11) -  - 应用响应
  - `MessageType.ERROR` (0x12) - 错误响应

**消息处理流程**：
1. FUDP 的 `MessageHandler` 接收 `REQUEST` 消息
2. 完成余额检查（信用额度、费用估算等）
3. 调用 `NodeEventListener.onRequestReceived()` 传递请求
4. FAPI 服务在 `onRequestReceived()` 中处理请求
5. 使用 `FudpNode.respond()` 发送响应

**信用额度默认规则**：
- 当 `NodeConfig.defaultCreditLimit` 未显式设置（值为 0）时，FUDP 节点按 `max(pricePerKb * creditLimitPriceMultiplier, creditLimitMinSats)` 计算默认额度，覆盖约 1MB 响应成本。
- 仍可通过配置设定固定额度以满足特殊场景（如测试环境低价或高风险限制）。

**FAPI 服务实现**：
FAPI 服务需要实现 `NodeEventListener` 接口，在 `onRequestReceived()` 方法中处理请求：

```java
public class FapiServer implements NodeEventListener {
    private final FudpNode fudpNode;
    private final FcFudpRequestHandler requestHandler;
    private final Map<String, Service> serviceMap;
    
    @Override
    public void onRequestReceived(String peerId, long requestId, String serviceName, byte[] data) {
        // serviceName 就是 RequestMessage.sid（即 Service.id）
        // 1. 验证 sid 并获取 Service 对象
        Service service = serviceMap.get(serviceName);
        if (service == null || !Arrays.asList(service.getTypes()).contains("FAPI")) {
            byte[] errorData = buildErrorJson(CodeMessage.Code1011DataNotFound, "Service not found");
            fudpNode.respond(peerId, requestId, ResponseMessage.STATUS_NOT_FOUND, errorData);
            return;
        }
        
        // 2. 处理请求（异步处理，避免阻塞协议层）
        CompletableFuture.supplyAsync(() -> {
            return requestHandler.handleRequest(service, peerId, data);
        }, elasticsearchExecutor).thenAccept(responseData -> {
            // responseData 是 byte[]，包含序列化后的 FapiResponse JSON
            // FUDP 协议层会自动处理余额更新和 ResponseMessage 封装
            fudpNode.respond(peerId, requestId, ResponseMessage.STATUS_SUCCESS, responseData);
        }).exceptionally(e -> {
            log.error("Failed to handle FAPI request", e);
            byte[] errorData = buildErrorJson(CodeMessage.Code1020OtherError, "Internal server error");
            fudpNode.respond(peerId, requestId, ResponseMessage.STATUS_INTERNAL_ERROR, errorData);
            return null;
        });
    }
}
```

**注意**：
- **不需要**注册服务到 MessageHandler
- FUDP 协议层已经处理了消息路由、余额管理、加密等
- FAPI 只需要实现 `NodeEventListener` 接口并处理业务逻辑

### 4.2 服务启动与路由

**sid 的含义**：
- `sid` 是身份在链上注册服务的**ID**，值为**注册交易的交易ID**
- 服务类型**不写入**sid中，而是在链上注册时通过 `Service.types` 字段标注
- 一个身份可以提供多个服务，每个服务有独立的 `sid`（不同的交易ID）
- FAPI、DISK、TALK 等都是服务类型，通过 `Service.types` 数组标识

**服务启动流程**：
1. 从 Elasticsearch 查询当前身份注册的所有 Service
2. 筛选出 `Service.types` 包含 `"FAPI"` 的服务
3. 将 Service 对象缓存到内存中（Map<String, Service>，key为Service.id）
4. 验证 Service 配置（如 params 中的价格参数等）

**服务更新机制**：
- **不自动更新**：服务信息仅在启动时加载一次
- **手动更新**：通过菜单选项手动触发服务信息更新
- **实现方式**：
  ```java
  // 在菜单中添加"更新服务信息"选项
  public void updateServices() {
      log.info("Updating FAPI services...");
      serviceMap.clear();
      initializeServices(); // 重新加载服务
      log.info("FAPI services updated. Total: " + serviceMap.size());
  }
  ```

**请求路由逻辑**：
1. 收到 RequestMessage 后，提取 `sid`（即 RequestMessage.sid）
2. 从缓存的 Service Map 中查找 `Service.id == sid` 的服务
3. 验证 Service 存在且 `Service.types` 包含 `"FAPI"`
4. 如果验证失败，返回错误响应（STATUS_NOT_FOUND 或 STATUS_BAD_REQUEST）
5. 验证通过后，根据 `fcdsl.index` 或 `fcdsl.entity` 确定查询目标

**实现示例**：
```java
public class FapiServer {
    private final Map<String, Service> serviceMap = new HashMap<>();
    private final ElasticsearchClient esClient;
    private final Settings settings;
    
    public void initialize() {
        // 从ES获取当前身份的所有FAPI服务
        String ownerFid = settings.getMainFid();
        Configure configure = settings.getConfig();
        List<Service> services = configure.getServiceListByOwnerAndTypeFromEs(
            ownerFid, Service.ServiceType.FAPI, esClient);
        
        if (services != null) {
            for (Service service : services) {
                serviceMap.put(service.getId(), service);
            }
        }
    }
    
    public ResponseMessage handleRequest(RequestMessage request) {
        String sid = request.getSid();
        Service service = serviceMap.get(sid);
        
        if (service == null) {
            return buildErrorResponse(STATUS_NOT_FOUND, 
                "Service not found: " + sid);
        }
        
        if (!Arrays.asList(service.getTypes()).contains("FAPI")) {
            return buildErrorResponse(STATUS_BAD_REQUEST, 
                "Service type mismatch");
        }
        
        // 继续处理请求...
    }
}
```

### 4.3 服务启动模块

**参照 StartApipManager.java 的启动流程**：

**启动类设计**：`FC-JDK/src/main/java/startFAPI/StartFapiServer.java`

**完整的启动流程**：
```java
public class StartFapiServer {
    private static final Logger log = LoggerFactory.getLogger(StartFapiServer.class);
    public static Service service;
    private static ElasticsearchClient esClient = null;
    private static BufferedReader br;
    public static NaSaRpcClient naSaRpcClient;
    public static String sid;
    public static Params params;  // 使用通用 Params 类型
    private static Settings settings;
    public static final Service.ServiceType serverType = Service.ServiceType.FAPI;
    
    public static void main(String[] args) {
        // 1. 定义必需的 Modules
        List<data.fcData.Module> modules = new ArrayList<>();
        modules.add(new data.fcData.Module(Service.class.getSimpleName(), 
            Service.ServiceType.NASA_RPC.name()));      // 用于获取最佳区块高度
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(), 
            Manager.ManagerType.MEMPOOL.name()));        // 处理未确认交易查询（如需要）
        modules.add(new data.fcData.Module(Service.class.getSimpleName(), 
            Service.ServiceType.ES.name()));             // 主要的数据来源
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(), 
            Manager.ManagerType.CASH.name()));           // 管理cash(UTXO)，用于支付收益分配
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(), 
            Manager.ManagerType.INCOME.name()));         // 新建的 IncomeManager（见 4.4 节）
        // 注意：不需要 ACCOUNT Manager，因为 FUDP 协议层已处理服务扣费
        
        // 2. 定义设置参数
        Map<String, Object> settingMap = new HashMap<>();
        settingMap.put(Settings.FORBID_FREE_API, false);
        settingMap.put(Settings.WINDOW_TIME, Settings.DEFAULT_WINDOW_TIME);
        settingMap.put(IncomeManager.DISTRIBUTE_DAYS, IncomeManager.DEFAULT_DISTRIBUTE_DAYS);
        settingMap.put(IncomeManager.MIN_DISTRIBUTE_BALANCE, IncomeManager.DEFAULT_MIN_DISTRIBUTE_BALANCE);
        settingMap.put(IncomeManager.DEALER_MIN_BALANCE, IncomeManager.DEFAULT_DEALER_MIN_BALANCE);
        
        // 3. 定义自动任务
        List<AutoTask> autoTaskList = new ArrayList<>();
        autoTaskList.add(new AutoTask(Manager.ManagerType.INCOME, "updateIncome", null));
        autoTaskList.add(new AutoTask(Manager.ManagerType.INCOME, "distribute", 10 * Constants.SEC_PER_DAY));
        autoTaskList.add(new AutoTask(Manager.ManagerType.INCOME, "saveMapsToLocalDB", Constants.SEC_PER_DAY));
        
        // 4. 启动服务（使用 Starter.startServer）
        Menu.welcome("FAPI Manager");
        br = new BufferedReader(new InputStreamReader(System.in));
        
        // 注意：Service.ServiceType.FAPI 已在枚举中定义
        // Starter.startServer() 会自动通过 Service.types 过滤包含 "FAPI" 的服务
        settings = Starter.startServer(serverType, settingMap, null, modules, br, autoTaskList);
        if (settings == null) return;
        
        // 5. 获取服务信息
        byte[] symkey = settings.getSymkey();
        service = settings.getService();
        sid = service.getId();
        params = ObjectUtils.objectToClass(service.getParams(), Params.class);
        
        // 6. 准备 API 客户端
        esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        naSaRpcClient = (NaSaRpcClient) settings.getClient(Service.ServiceType.NASA_RPC);
        
        // 7. 初始化 FAPI 服务
        FapiServer fapiServer = new FapiServer(settings);
        fapiServer.initialize();
        
        // 8. 启动 FUDP Node 并注册 FAPI 服务
        FudpNode fudpNode = new FudpNode(settings);
        fudpNode.addEventListener(fapiServer);
        fudpNode.start();
        
        // 9. 主菜单循环
        IncomeManager incomeManager = (IncomeManager) settings.getManager(Manager.ManagerType.INCOME);
        while (true) {
            Menu menu = new Menu("FAPI Manager", () -> close(br));
            menu.add("Manage service", () -> manageService(service, br, symkey));
            menu.add("Manage income", () -> incomeManager.menu(br, false));
            menu.add("Update services", () -> fapiServer.updateServices());
            menu.add("Settings", () -> settings.setting(br, serverType));
            menu.showAndSelect(br);
        }
    }
    
    private static void close(BufferedReader br) {
        try {
            System.out.println("Do you want to quit? 'q' to quit.");
            String input = br.readLine();
            if ("q".equals(input)) {
                br.close();
                settings.close();
                System.out.println("Exited, see you again.");
                System.exit(0);
            }
        } catch (IOException e) {
            log.error("Failed to close resources", e);
        }
    }
}
```

**关键差异说明**：
- ✅ **使用 `Starter.startServer()`**：复用现有的启动框架
- ✅ **Module 配置**：使用 `INCOME` Manager 替代 `ACCOUNT` Manager
- ✅ **Service 类型**：使用 `Service.ServiceType.FAPI`（已在枚举中定义）
- ✅ **服务选择逻辑**：`Starter.startServer()` 会自动通过 `Service.types` 数组过滤包含 "FAPI" 的服务
- ✅ **FUDP Node 集成**：在启动后创建 FudpNode 并注册 FapiServer 作为事件监听器
- ✅ **不依赖 APIP**：FAPI 是 APIP 的替代，所有数据查询通过 Elasticsearch 或 NASA_RPC 完成

### 4.4 经济模型与收入管理

**问题**：
- `AccountManager` 与 FUDP 的 `economics` 模块在服务扣费功能上重叠
- FUDP 协议层已经通过 `BalanceManager`、`FeeCalculator` 等实现了完整的扣费机制
- `AccountManager` 主要用于 APIP（HTTP）服务的账户管理，包含 Redis 依赖和消费扣费逻辑

**解决方案：新建 IncomeManager**：

**IncomeManager 的设计原则**：
1. **基于 AccountManager，但去除以下功能**：
   - ❌ **Redis 相关**：不使用 Redis，仅使用 LocalDB
   - ❌ **用户余额管理**：FUDP 协议层已处理服务扣费，无需维护用户余额
   - ❌ **消费扣费**：`userSpend()`、`updateUserBalance()` 等方法全部移除
   - ❌ **Via 消费分成**：只保留 Order Via Share，移除 Consume Via Share 相关逻辑

2. **保留的核心功能**：
   - ✅ **收入检测**：`updateIncome()` - 从 ES 或 NASA_RPC 查询支付给服务的 Cash
   - ✅ **收入分配**：`distribute()` - 根据 Service.params 分配收入
   - ✅ **收益支付**：`settle()` - 使用 CashManager 发送支付交易
   - ✅ **Via 订单分成**：保留 Order Via Share 逻辑（通过 OP_RETURN 中的 via 字段）

**IncomeManager 的职责**：
1. **检测用户购买**：
   - 从 Elasticsearch 查询 `Cash` 索引（使用 `CashManager.getCashListFromEs()`）
   - 或从 NASA_RPC 查询 UTXO 并转换为 Cash（使用 `CashManager.getCashListFromNasaNode()`）
   - 查询条件：`Cash.owner == Service.dealer` 且 `Cash.issuer != Cash.owner`
   - 识别支付给当前服务的交易（通过 Service.dealer）
   - 记录收入记录到 LocalDB
   - **注意**：不依赖 APIP，FAPI 是 APIP 的替代

2. **收入分配**：
   - 根据 `Service.params` 中的分配参数进行收入分配
   - 支持多种分配模式：
     - **固定成本（Fixed Costs）**：定期支付给指定地址
     - **贡献者分成（Contributor Shares）**：按比例分配给贡献者
     - **Order Via Share**：订单通过 Via 渠道的分成（从 OP_RETURN 中提取 via 字段）
   - 生成分配记录到 `payoffMap`

3. **收益支付**：
   - 定期或按需执行收益分配（通过 AutoTask）
   - 使用 `CASH` Manager 发送支付交易
   - 更新支付状态到 LocalDB

**IncomeManager 类结构**：
```java
// 注意：Income 需要从 AccountManager.Income 提取为独立类
// 位置：FC-JDK/src/main/java/data/fcData/Income.java
public class IncomeManager extends Manager<Income> {
    private static final Logger log = LoggerFactory.getLogger(IncomeManager.class);
    
    // 常量定义（从 AccountManager 复制，去除 Redis 相关）
    public static final String DEALER_MIN_BALANCE = "dealerMinBalance";
    public static final String MIN_DISTRIBUTE_BALANCE = "minDistributeBalance";
    public static final String DISTRIBUTE_DAYS = "distributeDays";
    public static final Long DEFAULT_DEALER_MIN_BALANCE = 100000000L;
    public static final Long DEFAULT_MIN_DISTRIBUTE_BALANCE = 5 * 100000000L;
    public static final Long DEFAULT_DISTRIBUTE_DAYS = 10;
    
    // 核心依赖
    private final String mainFid;              // Service.dealer
    private final String sid;                   // Service.id
    private final ElasticsearchClient esClient; // 必需：用于查询 Cash 索引
    private final NaSaRpcClient nasaClient;    // 可选：用于查询 UTXO 并转换为 Cash
    private final CashManager cashManager;      // 用于发送支付交易
    // 注意：不依赖 ApipClient，FAPI 是 APIP 的替代
    
    // 配置参数
    private final Long dealerMinBalance;
    private final Long minDistributeBalance;
    private final Long distributeDays;
    private final Long priceBase;
    private Double orderViaShare;              // 只保留 Order Via Share
    private Double minPay;
    
    // 状态管理（仅使用 LocalDB，不使用 Redis）
    private Long myBalance;
    private Map<String, Long> payoffMap;
    private long lastUpdateIncomeTime = 0;
    private String startHeight;
    
    // 构造函数
    public IncomeManager(Settings settings) {
        super(settings);
        this.mainFid = settings.getMainFid();
        this.sid = settings.getSid();
        this.esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        this.nasaClient = (NaSaRpcClient) settings.getClient(Service.ServiceType.NASA_RPC);
        // 注意：不依赖 APIP，FAPI 是 APIP 的替代
        this.cashManager = (CashManager) settings.getManager(Manager.ManagerType.CASH);
        
        // 验证至少有一个数据源可用
        if (esClient == null && nasaClient == null) {
            throw new IllegalStateException(
                "IncomeManager requires at least ES or NASA_RPC client");
        }
        
        // 从 settings 获取配置
        Map<String, Object> settingMap = settings.getSettingMap();
        this.dealerMinBalance = (Long) settingMap.getOrDefault(DEALER_MIN_BALANCE, DEFAULT_DEALER_MIN_BALANCE);
        this.minDistributeBalance = (Long) settingMap.getOrDefault(MIN_DISTRIBUTE_BALANCE, DEFAULT_MIN_DISTRIBUTE_BALANCE);
        this.distributeDays = (Long) settingMap.getOrDefault(DISTRIBUTE_DAYS, DEFAULT_DISTRIBUTE_DAYS);
        
        // 从 Service.params 获取价格和分成参数
        Service service = settings.getService();
        Params params = ObjectUtils.objectToClass(service.getParams(), Params.class);
        this.priceBase = params.getPriceBase();
        // 从 params 中提取 orderViaShare（如果存在）
        
        // 初始化状态
        this.payoffMap = new HashMap<>();
        loadStateFromLocalDB();
    }
    
    // 核心方法（从 AccountManager 复制并简化）
    
    /**
     * 检测收入：查询支付给服务的 Cash
     * 注意：
     * 1. 不更新用户余额，因为 FUDP 协议层已处理
     * 2. 不依赖 APIP，只使用 ES 或 NASA_RPC
     * 3. 使用 CashManager.getCashListFromEs() 或 CashManager.getCashListFromNasaNode()
     */
    public List<Income> updateIncome() {
        // 1. 获取上次查询位置
        List<String> lastIncome = getLastIncome();
        Long lastHeight = getLastHeight();
        
        // 2. 查询新的 Cash（只查询 owner == mainFid 且 issuer != owner 的 Cash）
        List<Cash> newCashes = null;
        
        if (esClient != null) {
            // 使用 Elasticsearch 查询
            ReplyBody replyBody = CashManager.getCashListFromEs(
                Arrays.asList(mainFid),  // fids
                true,                     // valid
                lastHeight,               // afterHeight
                Constants.DEFAULT_DISPLAY_LIST_SIZE,  // size
                null,                     // sortList (使用默认排序)
                lastIncome,               // last
                esClient
            );
            
            if (replyBody.getCode() == 0 && replyBody.getData() != null) {
                newCashes = ObjectUtils.objectToList(replyBody.getData(), Cash.class);
                lastIncome = replyBody.getLast();
            }
        } else if (nasaClient != null) {
            // 使用 NASA_RPC 查询 UTXO 并转换为 Cash
            ReplyBody replyBody = CashManager.getCashListFromNasaNode(
                mainFid,
                null,    // minConf
                true,    // includeUnsafe
                nasaClient
            );
            
            if (replyBody.getCode() == 0 && replyBody.getData() != null) {
                newCashes = ObjectUtils.objectToList(replyBody.getData(), Cash.class);
                // 过滤：只保留 issuer != owner 的 Cash（收入）
                newCashes = newCashes.stream()
                    .filter(cash -> !cash.getIssuer().equals(cash.getOwner()))
                    .collect(Collectors.toList());
            }
        }
        
        if (newCashes == null || newCashes.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 3. 转换为 Income 记录（移除用户余额更新逻辑）
        List<Income> incomeList = new ArrayList<>();
        for (Cash cash : newCashes) {
            Income income = new Income(
                cash.getId(),
                cash.getIssuer(),
                cash.getValue(),
                cash.getBirthTime(),
                cash.getBirthHeight()
            );
            incomeList.add(income);
        }
        
        // 4. 检测 Order Via（从 OP_RETURN 提取 via 字段）
        checkIncomeVia(newCashes);
        
        // 5. 保存收入记录到 LocalDB
        if (!incomeList.isEmpty()) {
            updateIncomes(incomeList);
            if (lastHeight != null) {
                setLastHeight(lastHeight);
            }
            setLastIncome(lastIncome);
        }
        
        return incomeList;
    }
    
    /**
     * 检测收入中的 Via 信息（仅 Order Via，不处理 Consume Via）
     */
    private void checkIncomeVia(List<Cash> newCashes) {
        if (newCashes == null || newCashes.isEmpty()) return;
        
        // 收集交易 ID
        Map<String, String> txIdCashIdMap = new HashMap<>();
        for (Cash cash : newCashes) {
            txIdCashIdMap.put(cash.getBirthTxId(), cash.getId());
        }
        
        // 从 ES 查询 OP_RETURN（不依赖 APIP）
        Map<String, OpReturn> opReturnMap = null;
        if (esClient != null) {
            try {
                opReturnMap = EsUtils.getOpReturnsByIds(esClient, txIdCashIdMap.keySet());
            } catch (Exception e) {
                log.error("Error getting op returns from ES", e);
                return;
            }
        }
        
        if (opReturnMap == null || opReturnMap.isEmpty()) return;
        
        // 处理 Via 信息（仅 Order Via）
        for (Map.Entry<String, OpReturn> entry : opReturnMap.entrySet()) {
            OpReturn opReturn = entry.getValue();
            Map<String, String> opReturnData = ObjectUtils.objectToMap(
                opReturn.getOpReturn(), String.class, String.class);
            
            if (opReturnData != null && opReturnData.containsKey(FieldNames.VIA)) {
                String viaFid = opReturnData.get(FieldNames.VIA);
                String cashId = txIdCashIdMap.get(entry.getKey());
                
                if (viaFid != null && cashId != null) {
                    // 计算 Order Via Share 并更新 viaBalance
                    // 注意：只处理 Order Via，不处理 Consume Via
                    updateViaBalanceForOrder(cashId, viaFid, opReturnData);
                }
            }
        }
    }
    
    /**
     * 分配收入：根据配置分配收益
     */
    public Boolean distribute() {
        // 实现逻辑与 AccountManager.distribute() 相同
        // 但移除 Redis 相关代码
        // 只保留 Order Via Share，移除 Consume Via Share
    }
    
    /**
     * 执行支付：发送支付交易
     */
    public boolean settle() {
        // 实现逻辑与 AccountManager.settle() 相同
        // 使用 CashManager 发送支付
    }
    
    /**
     * 更新服务余额
     * 注意：不依赖 APIP，只使用 ES 或 NASA_RPC
     */
    public boolean updateMyBalance() {
        // 从 ES 或 NASA_RPC 查询 Service.dealer 的余额
        if (esClient != null) {
            try {
                Freer cid = EsUtils.getById(esClient, IndicesNames.CID, mainFid, Freer.class);
                if (cid != null && cid.getBalance() != null) {
                    this.myBalance = cid.getBalance();
                    return true;
                }
            } catch (IOException e) {
                log.error("Error getting balance from ES", e);
            }
        } else if (nasaClient != null) {
            // 从 NASA_RPC 获取余额
            nasaClient.freshBestBlock();
            ReplyBody replyBody = CashManager.getCashListFromNasaNode(
                mainFid, null, true, nasaClient);
            if (replyBody.getCode() == 0 && replyBody.getData() != null) {
                List<Cash> cashList = ObjectUtils.objectToList(
                    replyBody.getData(), Cash.class);
                this.myBalance = cashList.stream()
                    .mapToLong(Cash::getValue)
                    .sum();
                return true;
            }
        }
        return false;
    }
    
    // 辅助方法（仅使用 LocalDB）
    private void loadStateFromLocalDB() {
        // 从 LocalDB 加载状态
    }
    
    public void saveMapsToLocalDB() {
        // 保存状态到 LocalDB
    }
    
    // 移除的方法（不再需要）：
    // - updateUserBalance()
    // - userSpend()
    // - checkUserBalance()
    // - updateFidConsumeVia() (消费 Via)
    // - 所有 Redis 相关方法
}
```

**与 AccountManager 的主要差异**：

| 功能 | AccountManager | IncomeManager |
|------|----------------|---------------|
| 用户余额管理 | ✅ 是 | ❌ 否（FUDP 处理） |
| 消费扣费 | ✅ 是 | ❌ 否（FUDP 处理） |
| Redis 支持 | ✅ 是 | ❌ 否（仅 LocalDB） |
| 收入检测 | ✅ 是 | ✅ 是 |
| 收入分配 | ✅ 是 | ✅ 是 |
| 收益支付 | ✅ 是 | ✅ 是 |
| Order Via Share | ✅ 是 | ✅ 是 |
| Consume Via Share | ✅ 是 | ❌ 否（简化） |

**Service.params 结构**：
- 直接使用 `Params` 基类，无需新建 `FapiParams`
- 如果需要在 params 中存储特定参数，可以通过 `Params` 的通用字段或扩展字段实现
- 参考 `ApipParams` 的实现方式，但使用 `Params` 作为基础类型
- 支持以下参数（通过 `Params` 的通用字段）：
  - `priceBase`：价格基数
  - `orderViaShare`：订单 Via 分成比例
  - `contributorShares`：贡献者分成映射
  - `fixedCosts`：固定成本映射

## 5. 代码复用策略

### 5.1 提取共享查询逻辑

**方案：创建 `ElasticsearchQueryExecutor` 工具类**

```java
public class ElasticsearchQueryExecutor {
    private final ElasticsearchClient esClient;
    private final Settings settings;
    
    // 从 FcHttpRequestHandler 提取的核心查询方法
    public <T> List<T> executeQuery(Fcdsl fcdsl, String index, List<Sort> defaultSort, Class<T> tClass);
    public <T> List<T> executeIdsQuery(List<String> ids, String index, Class<T> clazz);
    // ... 其他查询方法
}
```

**FcHttpRequestHandler 和 FcFudpRequestHandler 都使用这个工具类**：
```java
// FcHttpRequestHandler
private final ElasticsearchQueryExecutor queryExecutor;

public <T> List<T> doRequest(...) {
    return queryExecutor.executeQuery(fcdsl, index, defaultSort, tClass);
}

// FcFudpRequestHandler
private final ElasticsearchQueryExecutor queryExecutor;

public <T> List<T> doRequest(...) {
    return queryExecutor.executeQuery(fcdsl, index, defaultSort, tClass);
}
```

### 5.2 响应格式统一

**创建 `ResponseFormatter` 工具类**：
```java
public class ResponseFormatter {
    // HTTP 响应格式化
    public void formatHttpResponse(ReplyBody replyBody, HttpServletResponse response);
    
    // FUDP 响应格式化
    public byte[] formatFudpResponse(ReplyBody replyBody);
}
```

## 6. 依赖管理

### 6.1 Maven 依赖

**FC-JDK/pom.xml**（FUDP 和 FAPI 已合并到 FC-JDK）：
```xml
<dependencies>
    <!-- Elasticsearch Client -->
    <dependency>
        <groupId>co.elastic.clients</groupId>
        <artifactId>elasticsearch-java</artifactId>
    </dependency>
    
    <!-- 其他依赖已在 FC-JDK pom.xml 中定义 -->
    <!-- FUDP 和 FAPI 代码现在直接位于 FC-JDK 模块中，无需额外依赖 -->
</dependencies>
```

### 6.2 模块依赖关系

```
FC-JDK (包含 FUDP 和 FAPI)
├── FUDP (消息协议、节点框架) - 已合并到 FC-JDK
└── FAPI (查询服务) - 已合并到 FC-JDK
    └── 依赖 FC-JDK 核心库 (数据模型、工具类、Elasticsearch 客户端)
```

**注意**：FUDP 和 FAPI 已迁移到 FC-JDK 模块，不再作为独立的 Maven 模块存在。

## 7. 测试计划

### 7.1 单元测试

- **FcdslParserTest**：测试 FCDSL JSON 解析
- **FcFudpRequestHandlerTest**：测试查询逻辑（Mock ElasticsearchClient）
- **ResponseBuilderTest**：测试响应构建

### 7.2 集成测试

- **FapiServerIntegrationTest**：端到端测试（真实 Elasticsearch）
- **FudpNodeIntegrationTest**：与 FUDP Node 集成测试

### 7.3 测试数据

- 使用 FchParser 和 FEIP 的测试数据
- 覆盖各种查询场景（terms, match, range, ids, etc.）

## 8. 实施步骤

### Phase 0: 基础设施准备（1 天）
1. ✅ 在 `Manager.ManagerType` 枚举中添加 `INCOME`
   ```java
   // 在 Manager.java 的 ManagerType 枚举中添加
   INCOME,
   ```
2. ✅ 在 `Settings.java` 中注册 IncomeManager
   ```java
   // 在 Settings.initManager() 方法中添加
   case INCOME -> handlers.put(type, new IncomeManager(this));
   ```
3. ✅ 将 `Income` 类提取为独立类（从 `AccountManager.Income` 提取）
   - 创建 `FC-JDK/src/main/java/data/fcData/Income.java`
   - 从 `AccountManager.Income` 复制代码
4. ✅ 创建 `IncomeManager` 基础类结构（先不实现具体逻辑）

### Phase 1: 基础框架（2-3 天）
1. ✅ 创建 FAPI 项目结构
2. ✅ 实现服务启动逻辑（参照 StartApipManager）
   - 创建 `StartFapiServer.java`
   - 使用 `Starter.startServer()` 启动
   - 从 ES 加载 Service 对象
   - 验证 Service.types 包含 "FAPI"
   - 缓存 Service 到内存
3. ✅ 实现 `FcFudpRequestHandler` 基础框架
4. ✅ 实现 sid 验证逻辑（sid == Service.id）
5. ✅ 实现 FCDSL 解析工具
6. ✅ 实现响应构建工具
7. ✅ 创建 `FapiServer` 类并实现 `NodeEventListener` 接口

### Phase 2: 核心查询逻辑（2-3 天）
1. ✅ 提取 `ElasticsearchQueryExecutor`（或直接复用）
2. ✅ 实现 `doRequest()` 方法
3. ✅ 实现 `doIdsRequest()` 方法
4. ✅ 实现 `doRequestForMap()` 方法

### Phase 3: 特定类型查询（2-3 天）
1. ✅ 实现 Block 查询
2. ✅ 实现 Tx 查询
3. ✅ 实现 Cid 查询
4. ✅ 实现 Cash 查询（如需要）

### Phase 4: 集成与测试（2-3 天）
1. ✅ 与 FUDP Node 集成
2. ✅ 编写单元测试
3. ✅ 编写集成测试
4. ✅ 性能测试

### Phase 5: IncomeManager 实现（2-3 天）
1. ✅ 创建 IncomeManager 类（基于 AccountManager，移除 Redis 和用户余额功能）
2. ✅ 实现收入检测逻辑（updateIncome，移除用户余额更新）
   - 从 ES 查询 Cash 索引（使用 `CashManager.getCashListFromEs()`）
   - 或从 NASA_RPC 查询 UTXO 并转换为 Cash（使用 `CashManager.getCashListFromNasaNode()`）
   - 查询条件：owner == Service.dealer 且 issuer != owner
   - 记录收入到 LocalDB
   - 检测 Order Via（从 OP_RETURN 提取 via 字段，使用 `EsUtils.getOpReturnsByIds()`）
   - 不更新用户余额（FUDP 协议层处理）
   - 不依赖 APIP（FAPI 是 APIP 的替代）
3. ✅ 实现收入分配逻辑（distribute，移除 Redis 和 Consume Via Share）
   - 计算可用余额（myBalance - dealerMinBalance）
   - 处理 Order Via Share（仅保留订单分成，移除消费分成）
   - 处理固定成本（Fixed Costs）
   - 处理贡献者分成（Contributor Shares）
   - 生成 payoffMap
4. ✅ 实现收益支付逻辑（settle，使用 CashManager）
   - 从 payoffMap 生成支付列表
   - 使用 CashManager.send() 发送交易
   - 更新支付状态
5. ✅ 实现 LocalDB 状态管理
   - loadStateFromLocalDB()：加载状态
   - saveMapsToLocalDB()：保存状态
   - 管理 income_list、payoff_map、via_balance_map 等
6. ✅ 实现 updateMyBalance()（从区块链查询余额，不使用 Redis）
7. ✅ 编写单元测试

### Phase 6: 文档与优化（1-2 天）
1. ✅ API 文档
2. ✅ 使用示例
3. ✅ 性能优化

**总计：10-16 天**

## 9. 待讨论的问题

### 9.1 架构决策

1. **代码复用方式**
   - 选项 A：提取 `ElasticsearchQueryExecutor` 工具类（推荐）
   - 选项 B：`FcFudpRequestHandler` 继承 `FcHttpRequestHandler`（不推荐，职责不同）
   - 选项 C：直接复制代码（不推荐，维护成本高）

2. **服务 ID (sid) 的含义**
   - **已确定**：`sid` 是身份在链上注册服务的ID，值为**注册交易的交易ID**
   - 服务类型**不写入**sid中，而是通过 `Service.types` 字段标注（如 `["FAPI"]`）
   - 一个身份可以提供多个服务，每个服务有独立的 `sid`（不同的交易ID）
   - 服务启动时从 ES 查询并缓存 Service 对象
   - 请求路由时验证 `sid == Service.id` 且 `Service.types` 包含 `"FAPI"`

3. **响应格式**
   - 选项 A：完全兼容 `ReplyBody` JSON 格式（推荐）
   - 选项 B：简化格式（只包含必要字段）
   - 选项 C：自定义格式

4. **IncomeManager vs AccountManager**
   - **已确定**：新建 IncomeManager，只处理收入检测和分配
   - FUDP 协议层处理服务扣费（BalanceManager）
   - AccountManager 主要用于 HTTP 服务，不适用于 FAPI
   - 只保留对购买服务的通道按照orderViaShare公布的比例确定的报酬逻辑，不保留过于复杂的对消费渠道的报酬逻辑
   - IncomeManager不使用Redis

### 9.2 功能范围

1. **支持的查询类型**
   - ✅ 基础查询（terms, match, range, etc.）
   - ✅ IDs 查询
   - ✅ 分页查询（after, size, sort）
   - ❓ 字段过滤（fields, noFields）
   - ❓ 聚合查询（暂不需要）

2. **支持的实体类型**
   - ✅ Block, Tx, Cid, Cash（基础）
   - ❓ FEIP 协议数据（Contact, Mail, Secret, etc.）
   - ❓ 其他自定义实体

3. **特殊功能**
   - ❓ Cid 余额计算（`reCalcWeight()`）
   - ❓ Cid 数量更新（`updateCidNumbers()`）
   - ❓ Cash 余额聚合（`sumCashValueByOwners()`）

### 9.3 性能与限制

#### 1. 查询大小限制

**建议：复用 `Constants.MaxRequestSize`，并保持一致的限制策略**

**实现方案**：
- ✅ **复用 `Constants.MaxRequestSize = 3000`**：与 `FcHttpRequestHandler` 保持一致，避免行为差异
- ✅ **普通查询 size 限制**：
  ```java
  int size = fcdsl.getSize() != null ? Integer.parseInt(fcdsl.getSize()) : 0;
  if (size == 0 || size > Constants.MaxRequestSize) {
      size = Constants.DefaultSize; // 20
  }
  ```
- ✅ **IDs 查询限制**：
  ```java
  List<String> idList = fcdsl.getIds();
  if (idList.size() > Constants.MaxRequestSize) {
      return buildErrorResponse(CodeMessage.Code1010TooMuchData, 
          "Maximum IDs count exceeded: " + Constants.MaxRequestSize);
  }
  ```
- ✅ **请求数据大小限制**：考虑 FUDP 消息大小限制，建议限制 `RequestMessage.data` 不超过 64KB（与 FUDP 协议层保持一致）

**理由**：
- 保持与 HTTP APIP 服务的一致性，便于客户端统一处理
- `MaxRequestSize = 3000` 已经过验证，适合 Elasticsearch 查询性能
- 避免单个查询返回过多数据导致内存压力

#### 2. 并发处理

**建议：使用异步处理，但复用 FUDP 协议层的线程池**

**实现方案**：
- ✅ **不创建独立的线程池**：FUDP 的 `Protocol` 层已经使用 `Executors.newFixedThreadPool(4)` 处理数据包
- ✅ **简洁的异步处理方案**：
  ```java
  // 使用 FUDP 协议层已有的线程池，无需创建新的线程池
  @Override
  public void onRequestReceived(String peerId, long requestId, String serviceName, byte[] data) {
      // FUDP 协议层已经在独立线程中调用此方法，直接处理即可
      // Elasticsearch 查询虽然是阻塞的，但 FUDP 协议层已经提供了并发处理
      try {
          byte[] responseData = requestHandler.handleRequest(serviceName, peerId, data, settings);
          fudpNode.respond(peerId, requestId, ResponseMessage.STATUS_SUCCESS, responseData);
      } catch (Exception e) {
          log.error("Failed to handle FAPI request", e);
          byte[] errorData = buildErrorResponse(CodeMessage.Code1020OtherError, 
              "Internal server error", settings);
          fudpNode.respond(peerId, requestId, ResponseMessage.STATUS_INTERNAL_ERROR, errorData);
      }
  }
  ```
  
  **说明**：
  - FUDP 协议层已经使用线程池处理请求，FAPI 服务可以直接在回调中处理
  - 如果后续发现性能瓶颈，再考虑添加专用的 Elasticsearch 查询线程池
  - 简化实现，减少资源占用和复杂度

**理由**：
- FUDP 协议层已经处理了网络 I/O 的并发，FAPI 只需要处理业务逻辑并发
- Elasticsearch 查询是 I/O 密集型操作，需要独立的线程池避免阻塞协议层
- 使用 `CompletableFuture` 可以更好地处理异步流程和错误

#### 3. 缓存策略

**建议：分阶段实现，初期不实现缓存，后续根据需求添加**

**Phase 1（初期）：不实现缓存**
- ❌ **暂不实现查询结果缓存**：原因如下
  - FAPI 查询结果实时性要求高（区块链数据）
  - 缓存失效策略复杂（需要监听区块高度变化）
  - 增加系统复杂度，可能引入缓存一致性问题
  - 初期性能压力不大，Elasticsearch 本身有查询缓存

**Phase 2（优化阶段）：选择性缓存**
- ✅ **Service 对象缓存**（已实现）：
  ```java
  // 启动时加载，运行时更新（监听 Service 链上变更）
  private final Map<String, Service> serviceMap = new ConcurrentHashMap<>();
  ```
- ✅ **索引映射缓存**：
  ```java
  // 静态映射，无需缓存失效
  private static final Map<String, IndexMapping> ENTITY_INDEX_MAP = ...;
  ```
- ⚠️ **查询结果缓存**（可选，需谨慎）：
  - **适用场景**：只读查询（如历史区块、已确认交易）
  - **缓存键**：`FCDSL JSON + 索引名称` 的 SHA256 哈希
  - **缓存失效**：基于 `bestHeight`，当新区块确认时清除相关缓存
  - **实现建议**：使用 `Caffeine` 或 `Guava Cache`
    ```java
    private final Cache<String, FapiResponse> queryCache = Caffeine.newBuilder()
        .maximumSize(10000)                    // 最多缓存 10000 个查询
        .expireAfterWrite(30, TimeUnit.SECONDS) // 30 秒过期
        .recordStats()                          // 记录统计信息
        .build();
    
    // 在新区块确认时清除缓存
    public void onNewBlockConfirmed(long height) {
        queryCache.invalidateAll();
    }
    ```
  - **注意事项**：
    - 只缓存 `bestHeight` 不变时的查询结果
    - 不缓存包含 `after` 分页参数的查询（分页结果变化频繁）
    - 不缓存 IDs 查询（结果确定性高，但缓存收益低）

**Phase 3（高级优化）：智能缓存**
- ✅ **查询模式识别**：识别热点查询并优先缓存
- ✅ **缓存预热**：启动时预加载常用查询
- ✅ **分布式缓存**：多节点共享缓存（如 Redis，但文档中已明确不使用 Redis）

**理由**：
- 初期实现缓存会增加复杂度，且收益不明显
- Elasticsearch 本身有查询缓存和文件系统缓存
- 区块链数据实时性要求高，缓存可能影响用户体验
- 后续根据实际性能瓶颈再决定是否实现缓存

#### 4. 其他性能考虑

**请求超时**：
- ✅ 复用 `NodeConfig.requestTimeoutMs = 30000`（30 秒）
- ✅ Elasticsearch 查询超时：建议设置为 20 秒（小于请求超时）
  ```java
  SearchRequest searchRequest = SearchRequest.of(s -> s
      .index(index)
      .timeout(Time.of(t -> t.time("20s")))
      // ... 其他配置
  );
  ```

**连接池配置**：
- ✅ Elasticsearch 客户端连接池：使用默认配置即可
- ✅ 如需优化，可调整 `RestClient` 的连接池大小

**内存管理**：
- ✅ 限制单次查询返回的数据量（已通过 `MaxRequestSize` 限制）
- ✅ 使用流式处理大结果集（如需要，但当前 `MaxRequestSize = 3000` 不需要）

**监控指标**：
- ✅ 记录查询耗时、成功率、错误类型
- ✅ 记录线程池使用情况（队列长度、活跃线程数）
- ✅ 记录缓存命中率（如果实现了缓存）

## 10. 后续优化方向

1. **查询优化**
   - 查询结果缓存
   - 批量查询支持
   - 查询性能监控

2. **功能扩展**
   - 订阅/推送机制（基于 FUDP）
   - 批量操作支持
   - 事务性查询

3. **监控与运维**
   - 查询日志记录
   - 性能指标收集
   - 错误追踪

---

## 附录

### A. 参考代码位置

- `FcHttpRequestHandler`: `FC-JDK/src/main/java/server/FcHttpRequestHandler.java`
- `Fcdsl`: `FC-JDK/src/main/java/data/apipData/Fcdsl.java`
- `RequestMessage`: `FC-JDK/src/main/java/fudp/message/RequestMessage.java`
- `ResponseMessage`: `FC-JDK/src/main/java/fudp/message/ResponseMessage.java`
- `FudpNode`: `FC-JDK/src/main/java/fudp/node/FudpNode.java`

### B. 相关常量

- `IndicesNames`: `FC-JDK/src/main/java/constants/IndicesNames.java`
- `CodeMessage`: `FC-JDK/src/main/java/constants/CodeMessage.java`
- `Constants`: `FC-JDK/src/main/java/constants/Constants.java`

---

## 11. 文档检查与改进总结

### 11.1 已修复的问题

1. **✅ 请求处理流程描述修正**
   - **问题**：原文档说"解析 RequestMessage.payload"，但实际应直接从 `RequestMessage.getSid()` 和 `RequestMessage.getData()` 获取
   - **修复**：更新为直接从 RequestMessage 对象获取 sid 和 data

2. **✅ 响应构建方法改进**
   - **问题**：`getBestHeight()` 获取途径与余额填充责任不清
   - **修复**：
     - `bestHeight` 通过 `Settings.getBestHeight()` 获取（从 ES 或 NASA_RPC，不依赖 APIP）
     - 余额相关字段由 FAPI 经济模块决定，FUDP 不自动填充

3. **✅ 索引映射逻辑完善**
   - **问题**：`findMappingByIndexName()` 方法未定义
   - **修复**：提供了两种实现方案（反向查找或根据索引名称推断）

4. **✅ FAPI 服务实现示例改进**
   - **问题**：缺少异步处理和错误处理细节
   - **修复**：添加了完整的异步处理示例，包括 CompletableFuture 和异常处理

5. **✅ 服务更新机制补充**
   - **问题**：未说明如何处理 Service 的链上更新
   - **修复**：添加了服务更新机制说明（监听区块、定期重载等）

6. **✅ 删除重复内容**
   - **问题**：第 8 节末尾有重复的 IncomeManager 描述（与 4.4 节重复）
   - **修复**：删除了重复内容

### 11.2 需要进一步明确的问题（已明确）

1. **✅ FapiParams 类定义**
   - **已明确**：直接使用 `Params` 基类，无需新建 `FapiParams`
   - **实现**：`Service.params` 使用 `Params` 类型，通过 `ObjectUtils.objectToClass()` 转换为具体类型（如 `ApipParams`）或直接使用 `Params`

2. **✅ 索引名称到实体类的反向映射**
   - **已明确**：支持自定义索引，维护索引名称到实体类的映射表
   - **实现**：
     - 使用 `Map<String, IndexMapping> indexNameMapping` 维护索引名称映射
     - 启动时从 `ENTITY_INDEX_MAP` 初始化标准索引映射
     - 支持通过 `addCustomIndexMapping()` 动态添加自定义索引映射
     - 详见 3.2 节实现示例

3. **✅ Service 更新频率**
   - **已明确**：不自动更新服务，仅在启动时和通过菜单手动更新服务信息
   - **实现**：
     - 启动时加载一次 Service 列表
     - 在菜单中添加"更新服务信息"选项，手动触发更新
     - 详见 4.2 节服务更新机制

4. **✅ 错误响应格式**
   - **已明确**：已定义 `buildErrorResponse()` 和 `buildErrorJson()` 方法
   - **实现**：详见 3.4 节错误响应部分

5. **✅ 线程池配置**
   - **已明确**：使用简洁方案，直接使用 FUDP 协议层的线程池
   - **实现**：
     - 无需创建专用线程池
     - FUDP 协议层已在独立线程中调用 `onRequestReceived()`
     - 直接处理请求，如果后续有性能瓶颈再考虑优化
     - 详见 9.3.2 节并发处理部分

6. **✅ FapiResponse 中的 balance 字段**
   - **已明确**：不需要 FapiResponse 中的 balance 字段
   - **实现**：
     - 已从 FapiResponse 类定义中移除 balance 字段
     - balance 和 balanceSequenceNumber（如需要）由 FAPI 经济模块填充，FUDP 不自动处理
     - 详见 2.2.2 节 FapiResponse 定义

### 11.3 建议补充的内容

#### 1. 错误处理详细说明

**建议**：
- **错误场景分类**：
  ```java
  // 1. 服务不存在或类型不匹配
  if (service == null || !Arrays.asList(service.getTypes()).contains("FAPI")) {
      return buildErrorResponse(CodeMessage.Code1011DataNotFound, 
          "Service not found or type mismatch", settings);
  }
  
  // 2. FCDSL 解析失败
  try {
      fcdsl = parseFcdslFromBytes(data);
  } catch (IllegalArgumentException e) {
      return buildErrorResponse(CodeMessage.Code1013BadRequest, 
          "Invalid FCDSL: " + e.getMessage(), settings);
  }
  
  // 3. 索引映射失败
  try {
      mapping = determineIndexMapping(fcdsl);
  } catch (IllegalArgumentException e) {
      return buildErrorResponse(CodeMessage.Code1012BadQuery, 
          e.getMessage(), settings);
  }
  
  // 4. Elasticsearch 查询异常
  try {
      result = esClient.search(searchRequest, entityClass);
  } catch (Exception e) {
      log.error("Elasticsearch query failed", e);
      return buildErrorResponse(CodeMessage.Code1020OtherError, 
          "Query execution failed", settings);
  }
  ```

- **错误码映射**（已在 3.5 节定义）：
  - `Code1011DataNotFound` → `STATUS_NOT_FOUND (404)`
  - `Code1012BadQuery` → `STATUS_BAD_REQUEST (400)`
  - `Code1013BadRequest` → `STATUS_BAD_REQUEST (400)`
  - `Code1020OtherError` → `STATUS_INTERNAL_ERROR (500)`

- **错误日志规范**：
  ```java
  // 记录错误日志，包含关键信息
  log.error("FAPI request failed: peerId={}, sid={}, error={}", 
      peerId, serviceName, e.getMessage(), e);
  ```

#### 2. 性能优化建议

**建议**：
- **查询结果缓存**（Phase 2 优化阶段）：
  - 初期不实现缓存，直接查询 Elasticsearch
  - 后续根据实际性能需求，考虑缓存只读查询（如历史区块、已确认交易）
  - 缓存键：`FCDSL JSON + 索引名称` 的 SHA256 哈希
  - 缓存失效：基于 `bestHeight`，新区块确认时清除相关缓存

- **Elasticsearch 查询优化**：
  - 使用 `trackTotalHits` 控制总数计算（已实现）
  - 合理设置 `size` 参数（受 `MaxRequestSize` 限制）
  - 使用 `_source` 过滤减少数据传输（已支持 fields/noFields）
  - 考虑使用 `scroll` API 处理大批量查询（如需要）

- **并发控制**：
  - 使用 FUDP 协议层的并发处理机制
  - 通过 `MaxRequestSize` 限制单次查询数据量
  - 监控查询耗时，识别慢查询

#### 3. 安全考虑

**建议**：
- **输入验证**：
  ```java
  // 验证 FCDSL 参数
  if (fcdsl.isBadFcdsl()) {
      throw new IllegalArgumentException("Invalid FCDSL");
  }
  
  // 验证 size 参数
  int size = fcdsl.getSize() != null ? Integer.parseInt(fcdsl.getSize()) : 0;
  if (size > Constants.MaxRequestSize) {
      size = Constants.DefaultSize;
  }
  
  // 验证 IDs 数量
  if (fcdsl.getIds() != null && fcdsl.getIds().size() > Constants.MaxRequestSize) {
      throw new IllegalArgumentException("Too many IDs");
  }
  ```

- **防止查询注入**：
  - FCDSL 是结构化查询语言，通过对象映射而非字符串拼接
  - Elasticsearch Java Client 已提供参数化查询，防止注入攻击
  - 验证索引名称和实体类型，防止访问未授权索引

- **请求频率限制**：
  - FUDP 协议层已通过余额机制实现请求频率限制
  - 无需额外实现频率限制逻辑

#### 4. 监控与日志

**建议**：
- **关键指标监控**：
  ```java
  // 记录查询指标
  long startTime = System.currentTimeMillis();
  // ... 执行查询 ...
  long duration = System.currentTimeMillis() - startTime;
  
  // 记录到日志或监控系统
  log.info("FAPI query: index={}, duration={}ms, got={}, total={}", 
      indexName, duration, got, total);
  ```

- **日志记录规范**：
  - **INFO**：正常查询请求（包含索引、耗时、结果数量）
  - **WARN**：查询参数异常但可处理（如 size 超限自动调整）
  - **ERROR**：查询失败、服务不存在等错误（包含完整错误信息）

- **性能分析工具**：
  - 使用 SLF4J + Logback 记录日志
  - 考虑使用 Micrometer 或类似工具收集指标
  - 定期分析慢查询日志，优化查询性能

#### 5. 测试用例示例

**建议**：
- **单元测试示例**：
  ```java
  @Test
  public void testParseFcdsl() {
      String json = "{\"entity\":\"block\",\"size\":\"10\"}";
      Fcdsl fcdsl = FcFudpRequestHandler.parseFcdslFromBytes(
          json.getBytes(StandardCharsets.UTF_8));
      assertEquals("block", fcdsl.getEntity());
      assertEquals("10", fcdsl.getSize());
  }
  
  @Test
  public void testDetermineIndexMapping() {
      Fcdsl fcdsl = new Fcdsl();
      fcdsl.setEntity("block");
      IndexMapping mapping = handler.determineIndexMapping(fcdsl);
      assertEquals(IndicesNames.BLOCK, mapping.indexName);
      assertEquals(Block.class, mapping.entityClass);
  }
  ```

- **集成测试示例**：
  ```java
  @Test
  public void testHandleRequest() {
      // 准备测试数据
      Service service = createTestService();
      byte[] fcdslData = createTestFcdsl();
      
      // 执行请求
      byte[] response = handler.handleRequest(service, "peerId", fcdslData, settings);
      
      // 验证响应
      FapiResponse responseObj = JsonUtils.fromJson(
          new String(response, StandardCharsets.UTF_8), FapiResponse.class);
      assertEquals(CodeMessage.Code0Success, responseObj.getCode());
      assertNotNull(responseObj.getData());
  }
  ```

#### 6. 部署与运维

**建议**：
- **服务启动脚本**：
  ```bash
  #!/bin/bash
  # start-fapi.sh
  java -Xmx2g -Xms1g \
      -cp "FAPI.jar:FC-JDK.jar:FUDP.jar:..." \
      startFAPI.StartFapiServer
  ```

- **配置文件说明**：
  - FAPI 使用与 APIP 相同的配置文件结构（`config.json` 和 `settings.json`）
  - 配置文件通过 `Starter.startServer()` 自动加载
  - 主要配置项：
    ```json
    {
      "sid": "服务ID（注册交易的交易ID）",
      "mainFid": "服务提供者的FID",
      "settingMap": {
        "dealerMinBalance": 100000000,
        "minDistributeBalance": 500000000,
        "distributeDays": 10
      }
    }
    ```

- **常见问题排查**：
  - **服务未找到**：
    - 检查 Service 是否在 ES 中注册
    - 检查 `Service.types` 是否包含 "FAPI"
    - 检查 `Service.id` 是否与启动时选择的 sid 一致
  - **查询失败**：
    - 检查 Elasticsearch 连接和索引是否存在
    - 检查 FCDSL 查询语法是否正确
    - 查看日志中的详细错误信息
  - **响应超时**：
    - 检查网络连接和 Elasticsearch 性能
    - 检查查询复杂度，考虑优化 FCDSL
  - **余额不足**：
    - 检查 FUDP Balance 系统配置
    - 检查服务提供者的余额是否充足
  - **收入检测失败**：
    - 检查 Cash 索引是否存在
    - 检查 `Service.dealer` 是否正确
    - 检查 `startHeight` 配置是否合理
  - **收益分配失败**：
    - 检查 `dealerMinBalance` 和 `minDistributeBalance` 配置
    - 检查 `CashManager` 是否正常初始化
    - 检查支付地址是否有效

#### 7. IncomeManager 实现细节

**实现步骤**：

1. **在 Settings.java 中注册 IncomeManager**：
   ```java
   // 在 Settings.initManager() 方法中添加
   case INCOME -> handlers.put(type, new IncomeManager(this));
   ```

2. **提取 Income 类**：
   - 从 `AccountManager.Income` 提取为独立类
   - 位置：`FC-JDK/src/main/java/data/fcData/Income.java`
   - 参考：`AccountManager.Income` 的定义

3. **创建 IncomeManager 类**：
   - 位置：`FC-JDK/src/main/java/handlers/IncomeManager.java`
   - 继承：`Manager<Income>`
   - 参考：`AccountManager.java`，但移除 Redis 和用户余额相关代码

4. **核心方法实现**：
   - `updateIncome()`：从 AccountManager 复制，移除 `updateUserBalanceMap()` 调用
   - `distribute()`：从 AccountManager 复制，移除 Redis 相关代码
   - `settle()`：从 AccountManager 复制，保持不变
   - `updateMyBalance()`：从 AccountManager 复制，移除 Redis 相关代码

5. **LocalDB 字段映射**：
   ```java
   // 使用与 AccountManager 相同的字段名，但仅存储在 LocalDB
   private static final String INCOME_LIST = "income_list";
   private static final String PAYOFF_MAP = "payoff_map";
   private static final String VIA_BALANCE_MAP = "via_balance_map";  // 仅 Order Via
   private static final String CONTRIBUTOR_SHARE_MAP = "contributor_share_map";
   private static final String FIXED_COST_MAP = "fixed_cost_map";
   private static final String UNPAID_VIA_BALANCE_MAP = "unpaid_via_balance_map";
   private static final String UNPAID_FIXED_COST_MAP = "unpaid_fixed_cost_map";
   ```

6. **测试要点**：
   - 测试收入检测（从 ES 查询 Cash）
   - 测试收入分配（Order Via Share、贡献者分成、固定成本）
   - 测试收益支付（使用 CashManager 发送交易）
   - 验证不使用 Redis，仅使用 LocalDB
   - 验证不维护用户余额

### 11.4 架构设计建议

1. **代码复用策略确认**
   - 建议采用**选项 A**：提取 `ElasticsearchQueryExecutor` 工具类
   - 这样可以保持代码清晰，避免职责混乱

2. **响应格式确认**
   - 建议采用**选项 A**：完全兼容 `ReplyBody` JSON 格式
   - 便于客户端统一处理，减少适配成本

3. **IncomeManager 实现优先级**
   - 建议将 IncomeManager 的实现放在 Phase 5，但可以标记为可选
   - 初期可以先实现核心查询功能，收入管理功能可以后续迭代

### 11.5 文档完整性评估

**总体评价**：文档结构完整，涵盖了从架构设计到实施步骤的各个方面。主要改进点：

- ✅ **架构设计**：清晰明确，覆盖了核心组件和流程
- ✅ **实现细节**：提供了足够的代码示例和实现建议
- ✅ **集成方案**：详细说明了与 FUDP Node 的集成方式
- ⚠️ **错误处理**：需要补充更详细的错误处理示例
- ⚠️ **测试计划**：需要添加具体的测试用例示例
- ⚠️ **运维文档**：需要补充部署和运维相关内容

**建议**：在开始实施前，先解决"需要进一步明确的问题"部分列出的问题，确保实现过程中不会遇到重大障碍。
