# FAPI服务架构重构方案

> 版本: 1.8
> 日期: 2024年12月
> 状态: 实现阶段

## 1. 项目背景

### 1.1 重构范围

**重要原则**：
- ✅ **仅对FAPI服务进行重构**
- ✅ **原有启动逻辑和服务保持不变**（APIP、现有Manager等将逐步被取代）
- ✅ **新增DISK等业务服务基于重构后的FAPI框架**
- ✅ **简洁优先，不考虑向后兼容**（项目尚未进入生产环境）
- ✅ **保持FAPI独立性**（不依赖CodeMessage等不成熟设计）
- ✅ **复用现有实现**（HELLO/PING已在FudpNode实现，计费规则使用Service.pricePerKB）
- ✅ **保留旧启动类**（StartFapiClientOld、StartFapiServerOld暂时保留，创建新的启动类）
- ❌ **不需要兼容APIP**
- ❌ **不需要迁移映射接口**
- ❌ **DISK组件暂不实现**（将在专门的任务中实现）

**本次任务范围**：
- ✅ 重构FAPI的启动逻辑
- ✅ 对FAPI框架层做必要改进
- ✅ 对BASE组件做必要改进
- ✅ 为FAPI客户端实现默认服务发现机制
- ❌ DISK等业务组件详细设计将在专门的后续任务中完成（本文档仅提供框架示例）

### 1.2 重构目标

1. 建立清晰的分层架构（传输层→框架层→基础服务层→业务服务层）
2. 支持一个服务同时提供多种类型（FAPI+BASE+DISK等）
3. 统一FAPI服务注册和启动流程
4. 简化代码，保持逻辑清晰
5. 设计简洁统一的请求/响应格式

### 1.3 设计原则

**请求格式统一**：
- 统一使用 `api` 字段指定接口，格式为 `component.method`
- 不再使用 `endpoint` 字段（简化设计）
- 查询类请求使用 `fcdsl` 字段（标准查询语法）
- 其他操作使用 `params` 字段（灵活参数）

**错误码设计**：
- 与FUDP层 `ResponseMessage.STATUS_*` 保持一致
- 参考HTTP状态码规范（2xx成功，4xx客户端错误，5xx服务端错误）
- 不依赖现有 `CodeMessage` 类

**现有代码处理**：
- 现有 `AbstractEndpointHandler` 逻辑可复用
- `FapiEndpoint` 枚举可映射到新的 `api` 格式

## 2. 核心概念

### 2.1 服务注册机制

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          链上服务注册流程                                 │
│                                                                         │
│  ┌──────────┐    ┌───────────────┐    ┌──────────────┐                 │
│  │   FEIP   │───►│ ServiceOpData │───►│  TxCreator   │                 │
│  │ 协议定义  │    │   操作数据     │    │ 交易创建器   │                 │
│  └──────────┘    └───────────────┘    └──────┬───────┘                 │
│                                               │                         │
│                                               ▼                         │
│                                       ┌──────────────┐                 │
│                                       │  OP_RETURN   │                 │
│                                       │  链上注册     │                 │
│                                       └──────┬───────┘                 │
│                                               │                         │
│                                               ▼                         │
│                                       ┌──────────────┐                 │
│                                       │   ES 索引    │                 │
│                                       │  (解析存储)   │                 │
│                                       └──────┬───────┘                 │
│                                               │                         │
│                                               ▼                         │
│                                       ┌──────────────┐                 │
│                                       │ BASE 服务查询 │                 │
│                                       │  (FAPI协议)   │                 │
│                                       └──────┬───────┘                 │
│                                               │                         │
│                                               ▼                         │
│                                       ┌──────────────┐                 │
│                                       │   Service    │  ← 链上服务实体  │
│                                       │   实例       │                 │
│                                       └──────┬───────┘                 │
│                                               │                         │
│                                               ▼                         │
│                                       ┌──────────────┐                 │
│                                       │ ApiProvider  │  ← 本地存储      │
│                                       │   实例       │                 │
│                                       └──────────────┘                 │
└─────────────────────────────────────────────────────────────────────────┘
```

**关键类说明**：

| 类 | 位置 | 职责 |
|---|------|-----|
| `Feip` | `data/feipData/Feip.java` | FEIP协议定义，包含协议类型和版本 |
| `ServiceOpData` | `data/feipData/ServiceOpData.java` | 服务操作数据（PUBLISH/UPDATE/STOP等） |
| `TxCreator` | `core/fch/TxCreator.java` | 创建交易，将数据写入OP_RETURN |
| `Service` | `data/feipData/Service.java` | 链上服务实体 |
| `ApiProvider` | `config/ApiProvider.java` | 本地API提供者配置（继承自Service） |

### 2.2 服务类型组合

**一个链上服务可以同时提供多种类型**：

```java
// 服务types字段示例
service.types = ["FAPI", "BASE", "DISK", "MAP"]
```

**规则**：
1. 基于FAPI的服务，types必须包含"FAPI"
2. 提供链上数据的服务，types应包含"BASE"
3. 客户端根据types判断服务支持哪些功能
4. 服务器端根据types加载对应的组件

**服务类型层次**：

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         服务类型层次结构                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  生态外服务（无需链上注册）                                               │
│  ├─ ES        Elasticsearch服务                                        │
│  ├─ REDIS     Redis缓存服务                                            │
│  └─ NASA_RPC  Freecash全节点RPC                                        │
│                                                                         │
│  生态内服务（链上注册，types字段声明）                                     │
│  │                                                                      │
│  │  框架层类型                                                          │
│  │  └─ FAPI   基于FUDP的API框架（必须包含此类型才能基于FUDP通信）          │
│  │                                                                      │
│  │  基础服务类型                                                        │
│  │  └─ BASE   链上基础数据服务（FID、TX、Block等）                       │
│  │                                                                      │
│  │  业务服务类型                                                        │
│  │  ├─ DISK   分布式存储服务                                            │
│  │  ├─ MAP    地图服务                                                  │
│  │  ├─ ROAD   路由服务                                                  │
│  │  ├─ DOCK   码头服务                                                  │
│  │  ├─ TALK   通讯服务                                                  │
│  │  └─ SWAP   交换服务                                                  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.3 FudpNode架构

**FudpNode特性**：
- 绑定一个私钥，对应一个FID（Freecash地址）
- 监听一个UDP端口
- 提供端到端加密的P2P通信

**FudpNode分配策略**：

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         FudpNode分配方案                                 │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  方案：一个FID一个FudpNode，一个端口                                      │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                     服务器进程                                    │   │
│  │                                                                   │   │
│  │   ┌──────────────────────────────────────────────────────────┐  │   │
│  │   │               FudpNode (FID: FEk41...)                    │  │   │
│  │   │               Port: 8500                                  │  │   │
│  │   │                                                           │  │   │
│  │   │   ┌─────────────────────────────────────────────────┐    │  │   │
│  │   │   │            FAPI 服务框架                          │    │  │   │
│  │   │   │                                                   │    │  │   │
│  │   │   │   ┌─────────┐ ┌─────────┐ ┌─────────┐           │    │  │   │
│  │   │   │   │  BASE   │ │  DISK   │ │   MAP   │  ...      │    │  │   │
│  │   │   │   │  组件   │ │  组件   │ │  组件    │           │    │  │   │
│  │   │   │   └─────────┘ └─────────┘ └─────────┘           │    │  │   │
│  │   │   │                                                   │    │  │   │
│  │   │   └─────────────────────────────────────────────────┘    │  │   │
│  │   └──────────────────────────────────────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  多用户场景：每个用户独立的端口                                           │
│                                                                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                     │
│  │ FudpNode A  │  │ FudpNode B  │  │ FudpNode C  │                     │
│  │ FID: FEk41..│  │ FID: FGw2...│  │ FID: FTx3...│                     │
│  │ Port: 8500  │  │ Port: 8501  │  │ Port: 8502  │                     │
│  └─────────────┘  └─────────────┘  └─────────────┘                     │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

**端口分配规则**：

| 场景 | 端口分配 | 说明 |
|-----|---------|-----|
| 单用户服务 | 使用service.apiUrl中的端口 | 从链上服务信息获取 |
| 多用户本地 | 基础端口 + 用户序号 | 如 8500, 8501, 8502 |
| 动态分配 | 系统分配可用端口 | 客户端场景 |

**端口冲突检测**：

启动时需要检测端口是否被占用，避免服务冲突：

```java
/**
 * 检测端口是否可用
 */
public static boolean isPortAvailable(int port) {
    try (DatagramChannel channel = DatagramChannel.open()) {
        channel.bind(new InetSocketAddress(port));
        return true;
    } catch (IOException e) {
        return false;
    }
}

/**
 * 查找可用端口（从指定端口开始）
 */
public static int findAvailablePort(int startPort, int maxAttempts) {
    for (int i = 0; i < maxAttempts; i++) {
        int port = startPort + i;
        if (isPortAvailable(port)) {
            return port;
        }
    }
    throw new RuntimeException("No available port found starting from " + startPort);
}

// 在FapiServer启动时使用
public void start() throws IOException {
    int configuredPort = getPortFromServiceUrl();
    
    if (!isPortAvailable(configuredPort)) {
        System.err.println("Port " + configuredPort + " is already in use!");
        // 选择：1) 失败退出  2) 自动寻找可用端口
        throw new BindException("Port " + configuredPort + " already in use");
    }
    
    // 创建并启动FudpNode...
}
```

**配置方式**：

```java
// NodeConfig 端口配置
NodeConfig config = new NodeConfig();
config.setPort(8500);                    // 指定端口
config.setBindAddress("0.0.0.0");        // 绑定地址
config.setDataDir("fudp_data/" + fid);   // 每个FID独立数据目录
```

### 2.4 客户端服务发现机制

**设计目标**：简化客户端配置，提供开箱即用的体验。

**默认服务地址**：

```java
/**
 * FAPI客户端默认服务端点
 */
public class FapiDefaults {
    public static final String[] DEFAULT_ENDPOINTS = {
        "127.0.0.1:8500",      // 本地服务（开发调试）
        "freecash.info:8500"   // 公共服务（生产环境）
    };
    
    public static final int DEFAULT_FAPI_PORT = 8500;
    public static final int DEFAULT_DISCOVERY_TIMEOUT_MS = 5000;
}
```

**服务发现流程**：

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     客户端服务发现流程                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌────────────────┐                                                    │
│  │   启动客户端    │                                                    │
│  └───────┬────────┘                                                    │
│          ▼                                                             │
│  ┌────────────────┐                                                    │
│  │ 尝试默认服务列表│ ← 127.0.0.1:8500, freecash.info:8500              │
│  └───────┬────────┘                                                    │
│          ▼                                                             │
│  ┌────────────────┐     ┌─────────────┐                               │
│  │  HELLO + PING  │────►│ 发现服务成功 │                               │
│  │  (每个端点)     │     └──────┬──────┘                               │
│  └───────┬────────┘            │                                       │
│          │                     ▼                                       │
│          │             ┌────────────────┐                              │
│          │             │ 查询链上FAPI服务│ base.search(entity=service) │
│          │             │ 供用户选择      │                              │
│          │             └───────┬────────┘                              │
│          │                     │                                       │
│          ▼                     ▼                                       │
│  ┌────────────────┐    ┌────────────────┐                             │
│  │ 全部失败        │    │ 用户选择服务   │                             │
│  │ 提示手动输入    │    │ 或使用默认     │                             │
│  └───────┬────────┘    └───────┬────────┘                             │
│          │                     │                                       │
│          ▼                     ▼                                       │
│  ┌────────────────┐    ┌────────────────┐                             │
│  │ 用户输入URL     │    │ 连接并保存配置 │                             │
│  └───────┬────────┘    └────────────────┘                             │
│          │                                                             │
│          ▼                                                             │
│  ┌────────────────┐                                                    │
│  │  HELLO + PING  │                                                    │
│  │  (用户输入地址) │                                                    │
│  └────────────────┘                                                    │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

**实现逻辑**：

```java
/**
 * 客户端服务发现
 */
public class FapiServiceDiscovery {
    
    /**
     * 尝试通过默认端点发现服务
     * @return 发现结果，包含连接的服务信息；如果全部失败返回null
     */
    public static DiscoveryResult discoverViaDefaults(FudpNode node) {
        for (String endpoint : FapiDefaults.DEFAULT_ENDPOINTS) {
            try {
                String[] parts = endpoint.split(":");
                String host = parts[0];
                int port = parts.length > 1 ? Integer.parseInt(parts[1]) 
                    : FapiDefaults.DEFAULT_FAPI_PORT;
                
                DiscoveryResult result = FapiClient.discoverViaHelloAndPing(
                    node, host, port,
                    FapiDefaults.DEFAULT_DISCOVERY_TIMEOUT_MS,
                    FapiDefaults.DEFAULT_DISCOVERY_TIMEOUT_MS
                );
                
                if (result != null && result.getServices() != null 
                    && !result.getServices().isEmpty()) {
                    System.out.println("Connected to " + endpoint);
                    return result;
                }
            } catch (Exception e) {
                System.out.println("Failed to connect to " + endpoint + ": " + e.getMessage());
            }
        }
        return null;
    }
    
    /**
     * 获取链上FAPI服务商列表
     */
    public static List<Service> fetchFapiProviders(FapiClient client) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEntity("service");
        fcdsl.addNewFilter()
            .addNewTerms()
            .addNewFields("types")
            .addNewValues("FAPI");
        fcdsl.addNewSort()
            .addNewField("lastHeight")
            .addOrder("desc");
        fcdsl.setSize(20);
        
        List<Service> services = client.entitySearch("service", fcdsl, Service.class);
        return services != null ? services : Collections.emptyList();
    }
}
```

**客户端启动逻辑**：

```java
/**
 * 客户端启动服务发现
 */
public static FapiClient bootstrapClient(FudpNode node, BufferedReader br) {
    // 1. 尝试默认服务
    System.out.println("Connecting to FAPI service...");
    DiscoveryResult discovery = FapiServiceDiscovery.discoverViaDefaults(node);
    
    if (discovery != null) {
        // 2. 成功连接，创建临时客户端查询链上服务列表
        FapiClient tempClient = new FapiClient(node, discovery.getPeerId(), 
            discovery.getServices().get(0).getId(), 30, null);
        
        // 3. 查询链上FAPI服务商
        List<Service> providers = FapiServiceDiscovery.fetchFapiProviders(tempClient);
        
        if (!providers.isEmpty()) {
            // 4. 显示服务列表供用户选择
            System.out.println("\nAvailable FAPI services:");
            for (int i = 0; i < providers.size(); i++) {
                Service s = providers.get(i);
                System.out.printf("%d) %s (%s) - %s\n", 
                    i + 1, s.getStdName(), s.getId().substring(0, 8) + "...", 
                    s.getApiUrl());
            }
            System.out.println("0) Use current connection");
            System.out.println("-1) Enter manually");
            
            Long choice = Inputer.inputLong(br, "Select service", 0L);
            
            if (choice == 0) {
                // 使用当前连接
                return tempClient;
            } else if (choice > 0 && choice <= providers.size()) {
                // 连接用户选择的服务
                Service selected = providers.get(choice.intValue() - 1);
                return connectToService(node, selected, br);
            }
            // choice == -1 或其他：继续到手动输入
        } else {
            // 没有链上服务，使用当前连接
            System.out.println("No FAPI services found on chain, using current connection.");
            return tempClient;
        }
    }
    
    // 5. 全部失败或用户选择手动输入
    System.out.println("\nFailed to connect to default services.");
    return manualConnect(node, br);
}

/**
 * 手动输入服务地址
 */
private static FapiClient manualConnect(FudpNode node, BufferedReader br) {
    String input = Inputer.inputString(br, 
        "Enter FAPI service address (host:port, e.g. 192.168.1.100:8500):");
    
    if (input == null || input.isBlank()) {
        return null;
    }
    
    String[] parts = input.split(":");
    String host = parts[0];
    int port = parts.length > 1 ? Integer.parseInt(parts[1]) 
        : FapiDefaults.DEFAULT_FAPI_PORT;
    
    try {
        DiscoveryResult result = FapiClient.discoverViaHelloAndPing(
            node, host, port,
            FapiDefaults.DEFAULT_DISCOVERY_TIMEOUT_MS,
            FapiDefaults.DEFAULT_DISCOVERY_TIMEOUT_MS
        );
        
        if (result != null && result.getServices() != null 
            && !result.getServices().isEmpty()) {
            return new FapiClient(node, result.getPeerId(),
                result.getServices().get(0).getId(), 30, null);
        }
    } catch (Exception e) {
        System.out.println("Failed to connect: " + e.getMessage());
    }
    
    return null;
}
```

**配置持久化**：

成功连接后，将服务信息保存到本地配置（`config/FAPI_Client_settings.json`），下次启动时优先尝试上次连接的服务。

## 3. 分层架构设计

### 3.1 整体架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          业务服务层                                      │
│    ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐          │
│    │ DISK │  │ MAP  │  │ ROAD │  │ DOCK │  │ TALK │  │ SWAP │  ...     │
│    └──┬───┘  └──┬───┘  └──┬───┘  └──┬───┘  └──┬───┘  └──┬───┘          │
│       │         │         │         │         │         │               │
│       └─────────┴─────────┴────┬────┴─────────┴─────────┘               │
│                                ▼                                         │
├─────────────────────────────────────────────────────────────────────────┤
│                          基础服务层                                      │
│                       ┌──────────┐                                      │
│                       │   BASE   │  ← 提供链上基础信息                   │
│                       │  组件    │    (FID, TX, Block, Service...)      │
│                       └────┬─────┘                                      │
│                            │                                             │
├────────────────────────────┼────────────────────────────────────────────┤
│                          框架层                                          │
│                       ┌────┴─────┐                                      │
│                       │   FAPI   │  ← API协议框架                        │
│                       │  Server  │    - 统一的Request/Response           │
│                       └────┬─────┘    - HELLO/PING（复用FudpNode实现）   │
│                            │          - 组件注册和请求路由               │
│                            │          - 服务广播（FapiServer.buildAdvertiseData）│
├────────────────────────────┼────────────────────────────────────────────┤
│                          传输层                                          │
│                       ┌────┴─────┐                                      │
│                       │ FudpNode │  ← 安全传输                           │
│                       └────┬─────┘    - UDP协议                          │
│                            │          - 公私钥身份认证                    │
│                            │          - 端到端加密                        │
│                            │          - 可靠传输（重传、确认）            │
│                            ▼                                             │
│                      ┌──────────┐                                       │
│                      │   UDP    │                                       │
│                      └──────────┘                                       │
└─────────────────────────────────────────────────────────────────────────┘
```

### 3.2 组件交互流程

```
客户端请求流程：

┌─────────┐     ┌─────────┐     ┌─────────┐     ┌─────────┐
│ Client  │────►│FudpNode │────►│  FAPI   │────►│Component│
│         │     │         │     │ Server  │     │(BASE/   │
│         │◄────│         │◄────│         │◄────│DISK...) │
└─────────┘     └─────────┘     └─────────┘     └─────────┘
     │               │               │               │
     │  1.FapiRequest│               │               │
     │  (加密)       │               │               │
     │──────────────►│               │               │
     │               │ 2.解密&解析   │               │
     │               │──────────────►│               │
     │               │               │ 3.路由到组件  │
     │               │               │──────────────►│
     │               │               │               │ 4.处理
     │               │               │ 5.FapiResponse│
     │               │               │◄──────────────│
     │               │ 6.加密&发送   │               │
     │               │◄──────────────│               │
     │ 7.响应(加密)  │               │               │
     │◄──────────────│               │               │
```

### 3.3 服务类型与组件映射

```java
// 服务类型到组件的映射
public class ComponentRegistry {
    private static final Map<String, Class<? extends FapiComponent>> COMPONENT_MAP = Map.of(
        "BASE", BaseComponent.class,
        "DISK", DiskComponent.class,
        "MAP",  MapComponent.class,
        "ROAD", RoadComponent.class,
        "DOCK", DockComponent.class,
        "TALK", TalkComponent.class,
        "SWAP", SwapComponent.class
    );
    
    // 根据service.types加载组件
    public List<FapiComponent> loadComponents(String[] types) {
        List<FapiComponent> components = new ArrayList<>();
        for (String type : types) {
            Class<? extends FapiComponent> clazz = COMPONENT_MAP.get(type.toUpperCase());
            if (clazz != null) {
                components.add(clazz.getDeclaredConstructor().newInstance());
            }
        }
        return components;
    }
}
```

## 4. 核心接口设计

### 4.1 FAPI请求/响应结构

```java
/**
 * FAPI统一请求结构
 * 
 * 设计原则：
 * - 统一使用 api 字段，格式为 "component.method"
 * - 查询类请求使用 fcdsl 字段（标准查询语法）
 * - 其他操作使用 params 字段（灵活参数）
 * - 两者互斥：fcdsl 用于查询，params 用于其他操作
 * 
 * 注意：
 * - 无需nonce字段，防重放由FUDP层处理（ReplayProtection + 加密）
 * - 无需sign字段，认证由FUDP层处理（公私钥签名）
 */
public class FapiRequest {
    private String id;            // 请求ID（客户端生成，用于追踪和幂等控制）
    private String api;           // API名称: "component.method" (如 "base.search", "disk.put")
    private String sid;           // 服务ID（可选，用于多服务场景）
    private Fcdsl fcdsl;          // 查询参数（用于 search/getByIds 等查询类API）
    private Object params;        // 操作参数（用于 put/get/carve 等非查询API）
    
    /**
     * 从API名称提取组件名
     */
    public String getComponentName() {
        if (api == null || !api.contains(".")) return null;
        return api.split("\\.", 2)[0].toUpperCase();
    }
    
    /**
     * 从API名称提取方法名
     */
    public String getMethodName() {
        if (api == null || !api.contains(".")) return null;
        return api.split("\\.", 2)[1];
    }
    
    /**
     * 是否为查询类请求
     */
    public boolean isQuery() {
        return fcdsl != null;
    }
}

/**
 * FAPI统一响应结构
 * 
 * ID设计说明：
 * - id: 响应自身的唯一ID（服务端生成），用于响应审计和日志
 * - requestId: 对应请求的ID（回传），便于客户端匹配请求-响应
 * 
 * 注意：
 * - 时间戳已在FUDP协议层包含（用于防重放），应用层不需要重复
 * - 认证已在FUDP层完成（公私钥身份验证），应用层无需额外认证
 * - code 与 FUDP 层 ResponseMessage.statusCode 保持一致
 */
public class FapiResponse {
    private String id;            // 响应ID（服务端生成，用于审计和日志）
    private String requestId;     // 对应请求的ID（回传，便于客户端匹配）
    private Integer code;         // 响应码 (0=成功，与FUDP层一致)
    private String message;       // 响应消息
    private Object data;          // 响应数据
    private Long got;             // 返回数量（查询类响应）
    private Long total;           // 总数量（查询类响应）
    private List<String> last;    // 分页游标（查询类响应）
    private Long bestHeight;      // 最新区块高度
    private Long balance;         // 权威余额（单位聪）
    private Long balanceSeq;      // 余额序列号
    
    public static FapiResponse success(String requestId, Object data) {
        FapiResponse resp = new FapiResponse();
        resp.setId(generateResponseId());
        resp.setRequestId(requestId);
        resp.setCode(FapiCode.SUCCESS);
        resp.setMessage("Success");
        resp.setData(data);
        return resp;
    }
    
    public static FapiResponse error(String requestId, int code, String message) {
        FapiResponse resp = new FapiResponse();
        resp.setId(generateResponseId());
        resp.setRequestId(requestId);
        resp.setCode(code);
        resp.setMessage(message);
        return resp;
    }
    
    private static String generateResponseId() {
        return "resp-" + System.currentTimeMillis() + "-" + 
               Integer.toHexString(ThreadLocalRandom.current().nextInt());
    }
}
```

### 4.2 错误码设计

**设计原则**：
- 与FUDP层 `ResponseMessage.STATUS_*` 保持一致
- 参考HTTP状态码规范，便于理解和调试
- 保持FAPI独立性，不依赖现有CodeMessage

```java
/**
 * FAPI错误码定义
 * 与FUDP层ResponseMessage状态码保持一致
 * 
 * 码段划分：
 * - 0: 成功
 * - 1: 通用错误
 * - 4xx: 客户端错误（参考HTTP）
 * - 5xx: 服务端错误（参考HTTP）
 */
public final class FapiCode {
    // ==================== 成功 ====================
    public static final int SUCCESS = 0;              // 成功（与FUDP STATUS_SUCCESS一致）
    
    // ==================== 通用错误 ====================
    public static final int ERROR = 1;                // 通用错误（与FUDP STATUS_ERROR一致）
    
    // ==================== 客户端错误 4xx ====================
    public static final int BAD_REQUEST = 400;        // 请求格式错误、参数无效
    public static final int UNAUTHORIZED = 401;       // 未授权（预留，FUDP层已处理认证）
    public static final int PAYMENT_REQUIRED = 402;   // 余额不足
    public static final int FORBIDDEN = 403;          // 禁止访问、信用额度超限
    public static final int NOT_FOUND = 404;          // 数据不存在、组件不存在
    public static final int METHOD_NOT_ALLOWED = 405; // 方法不存在
    public static final int CONFLICT = 409;           // 冲突（如重复提交）
    public static final int GONE = 410;               // 资源已删除
    public static final int PAYLOAD_TOO_LARGE = 413;  // 请求体过大
    public static final int TOO_MANY_REQUESTS = 429;  // 请求过于频繁
    
    // ==================== 服务端错误 5xx ====================
    public static final int INTERNAL_ERROR = 500;     // 服务器内部错误
    public static final int NOT_IMPLEMENTED = 501;    // 功能未实现
    public static final int BAD_GATEWAY = 502;        // 外部服务错误（ES、RPC等）
    public static final int SERVICE_UNAVAILABLE = 503; // 服务不可用（组件未就绪）
    public static final int GATEWAY_TIMEOUT = 504;    // 外部服务超时
    
    // ==================== 错误消息 ====================
    private static final Map<Integer, String> MESSAGES = Map.ofEntries(
        Map.entry(SUCCESS, "Success"),
        Map.entry(ERROR, "Error"),
        Map.entry(BAD_REQUEST, "Bad request"),
        Map.entry(UNAUTHORIZED, "Unauthorized"),
        Map.entry(PAYMENT_REQUIRED, "Insufficient balance"),
        Map.entry(FORBIDDEN, "Forbidden"),
        Map.entry(NOT_FOUND, "Not found"),
        Map.entry(METHOD_NOT_ALLOWED, "Method not allowed"),
        Map.entry(CONFLICT, "Conflict"),
        Map.entry(GONE, "Gone"),
        Map.entry(PAYLOAD_TOO_LARGE, "Payload too large"),
        Map.entry(TOO_MANY_REQUESTS, "Too many requests"),
        Map.entry(INTERNAL_ERROR, "Internal server error"),
        Map.entry(NOT_IMPLEMENTED, "Not implemented"),
        Map.entry(BAD_GATEWAY, "Bad gateway"),
        Map.entry(SERVICE_UNAVAILABLE, "Service unavailable"),
        Map.entry(GATEWAY_TIMEOUT, "Gateway timeout")
    );
    
    public static String getMessage(int code) {
        return MESSAGES.getOrDefault(code, "Unknown error");
    }
    
    public static boolean isSuccess(int code) {
        return code == SUCCESS;
    }
    
    public static boolean isClientError(int code) {
        return code >= 400 && code < 500;
    }
    
    public static boolean isServerError(int code) {
        return code >= 500;
    }
}
```

**与FUDP层的对应关系**：

| FUDP ResponseMessage | FapiCode | 说明 |
|---------------------|----------|-----|
| STATUS_SUCCESS (0) | SUCCESS (0) | 成功 |
| STATUS_ERROR (1) | ERROR (1) | 通用错误 |
| STATUS_BAD_REQUEST (400) | BAD_REQUEST (400) | 请求格式错误 |
| STATUS_FORBIDDEN (403) | FORBIDDEN (403) | 禁止访问 |
| STATUS_OVER_CREDIT_LIMIT (403) | FORBIDDEN (403) | 信用额度超限 |
| STATUS_NOT_FOUND (404) | NOT_FOUND (404) | 未找到 |
| STATUS_INTERNAL_ERROR (500) | INTERNAL_ERROR (500) | 服务器错误 |

### 4.3 FAPI组件接口

```java
/**
 * FAPI服务组件接口
 * 所有业务服务（BASE、DISK、MAP等）都实现此接口
 * 
 * 设计说明：
 * - 认证已在FUDP层完成，peerId即为已验证的用户身份
 * - 可继承现有AbstractEndpointHandler实现平滑迁移
 */
public interface FapiComponent {
    
    /**
     * 组件生命周期状态
     */
    enum State { 
        CREATED,      // 已创建，未初始化
        INITIALIZING, // 正在初始化
        RUNNING,      // 正常运行
        STOPPING,     // 正在停止
        STOPPED       // 已停止
    }
    
    /**
     * 获取组件名称（对应service.types中的类型）
     */
    String getName();
    
    /**
     * 获取组件支持的API列表
     */
    List<String> getApiList();
    
    /**
     * 获取当前状态
     */
    default State getState() { return State.RUNNING; }
    
    /**
     * 健康检查
     */
    default boolean isHealthy() { return getState() == State.RUNNING; }
    
    /**
     * 初始化组件
     * @param server FAPI服务器实例，可获取其他组件引用
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
    default boolean supportsAsync() { return false; }
    
    /**
     * 关闭组件，释放资源
     * @param timeoutMs 超时时间（毫秒）
     */
    default void close(long timeoutMs) throws InterruptedException {}
    
    /**
     * 关闭组件（无超时）
     */
    default void close() {
        try {
            close(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

/**
 * 组件抽象基类
 * 提供通用实现，简化组件开发
 */
public abstract class AbstractFapiComponent implements FapiComponent {
    protected FapiServer server;
    protected Settings settings;
    protected volatile State state = State.CREATED;
    
    @Override
    public State getState() { return state; }
    
    @Override
    public void initialize(FapiServer server) {
        this.state = State.INITIALIZING;
        this.server = server;
        this.settings = server.getSettings();
        doInitialize();
        this.state = State.RUNNING;
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
    
    @Override
    public void close(long timeoutMs) throws InterruptedException {
        state = State.STOPPING;
        doClose(timeoutMs);
        state = State.STOPPED;
    }
    
    /**
     * 子类实现具体关闭逻辑
     */
    protected void doClose(long timeoutMs) throws InterruptedException {}
    
    /**
     * 组件间调用：获取其他组件的引用
     * 示例：DISK组件调用BASE组件查询链上数据
     */
    protected FapiResponse callComponent(String componentName, FapiRequest request, String peerId) {
        FapiComponent target = server.getComponent(componentName);
        if (target == null) {
            return FapiResponse.error(request.getId(), FapiCode.NOT_FOUND, 
                "Component not found: " + componentName);
        }
        return target.handleRequest(request, peerId);
    }
}
```

### 4.4 BASE组件实现

#### 4.4.1 API列表总览

BASE组件提供链上基础数据查询服务，API分为以下几类：

| 类别 | API | 说明 | 参数类型 |
|-----|-----|------|---------|
| **通用查询** | `base.getByIds` | 根据ID列表获取实体 | fcdsl |
| | `base.search` | 搜索实体 | fcdsl |
| | `base.totals` | 获取所有索引文档数量 | 无 |
| | `base.health` | 健康检查 | 无 |
| **余额查询** | `base.balanceByIds` | 根据FID列表获取余额 | fcdsl |
| | `base.cashValid` | 获取有效UTXO | fcdsl+params |
| | `base.getUtxo` | 获取UTXO（Utxo格式） | params |
| **链信息** | `base.chainInfo` | 获取链信息 | params |
| | `base.blockTimeHistory` | 获取出块时间历史 | params |
| | `base.difficultyHistory` | 获取难度历史 | params |
| | `base.hashRateHistory` | 获取算力历史 | params |
| **内存池** | `base.unconfirmed` | 获取未确认交易信息 | fcdsl |
| | `base.unconfirmedCashes` | 获取未确认的Cash | fcdsl |
| **交易操作** | `base.broadcastTx` | 广播交易 | params |
| | `base.decodeTx` | 解码交易 | params |
| | `base.estimateFee` | 估算交易费用 | params |

#### 4.4.2 服务端实现

```java
/**
 * BASE组件 - 提供链上基础数据
 * 
 * 整合了原有的EndpointHandler功能：
 * - GeneralEndpointHandler: totals
 * - CashEndpointHandler: balanceByIds, cashValid, getUtxo
 * - ChainInfoEndpointHandler: chainInfo, blockTimeHistory, difficultyHistory, hashRateHistory
 * - MempoolEndpointHandler: unconfirmed, unconfirmedCashes
 * - TransactionEndpointHandler: broadcastTx, decodeTx, estimateFee
 */
public class BaseComponent extends AbstractFapiComponent {
    private static final Logger log = LoggerFactory.getLogger(BaseComponent.class);
    
    private ElasticsearchClient esClient;
    private RpcOperationService rpcService;
    private FcdslQueryExecutor queryExecutor;
    
    @Override
    public String getName() { return "BASE"; }
    
    @Override
    public List<String> getApiList() {
        return List.of(
            // 通用查询
            "base.getByIds",           // 根据ID获取实体
            "base.search",             // 搜索实体
            "base.totals",             // 获取所有索引文档数量
            "base.health",             // 健康检查
            // 余额查询
            "base.balanceByIds",       // 根据FID列表获取余额
            "base.cashValid",          // 获取有效UTXO
            "base.getUtxo",            // 获取UTXO（Utxo格式）
            // 链信息
            "base.chainInfo",          // 获取链信息
            "base.blockTimeHistory",   // 获取出块时间历史
            "base.difficultyHistory",  // 获取难度历史
            "base.hashRateHistory",    // 获取算力历史
            // 内存池
            "base.unconfirmed",        // 获取未确认交易信息
            "base.unconfirmedCashes",  // 获取未确认的Cash
            // 交易操作
            "base.broadcastTx",        // 广播交易
            "base.decodeTx",           // 解码交易
            "base.estimateFee"         // 估算交易费用
        );
    }
    
    @Override
    protected void doInitialize() {
        this.esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);
        this.rpcService = new RpcOperationService(settings);
        this.queryExecutor = new FcdslQueryExecutor(esClient);
    }
    
    @Override
    public FapiResponse handleRequest(FapiRequest request, String peerId) {
        String method = request.getMethodName();
        String requestId = request.getId();
        return switch (method) {
            // 通用查询
            case "getByIds" -> handleGetByIds(request);
            case "search" -> handleSearch(request);
            case "totals" -> handleTotals(request);
            case "health" -> handleHealth(requestId);
            // 余额查询
            case "balanceByIds" -> handleBalanceByIds(request, peerId);
            case "cashValid" -> handleCashValid(request, peerId);
            case "getUtxo" -> handleGetUtxo(request, peerId);
            // 链信息
            case "chainInfo" -> handleChainInfo(request, peerId);
            case "blockTimeHistory" -> handleBlockTimeHistory(request, peerId);
            case "difficultyHistory" -> handleDifficultyHistory(request, peerId);
            case "hashRateHistory" -> handleHashRateHistory(request, peerId);
            // 内存池
            case "unconfirmed" -> handleUnconfirmed(request, peerId);
            case "unconfirmedCashes" -> handleUnconfirmedCashes(request, peerId);
            // 交易操作
            case "broadcastTx" -> handleBroadcastTx(request, peerId);
            case "decodeTx" -> handleDecodeTx(request, peerId);
            case "estimateFee" -> handleEstimateFee(request, peerId);
            default -> FapiResponse.error(requestId, FapiCode.METHOD_NOT_ALLOWED, 
                "Method not found: " + method);
        };
    }
    
    // ==================== 通用查询 ====================
    
    /**
     * 处理getByIds请求
     * 请求格式：{ "id": "req-xxx", "api": "base.getByIds", "fcdsl": { "entity": "freer", "ids": [...] } }
     */
    private FapiResponse handleGetByIds(FapiRequest request) {
        String requestId = request.getId();
        Fcdsl fcdsl = request.getFcdsl();
        if (fcdsl == null || fcdsl.getIds() == null) {
            return FapiResponse.error(requestId, FapiCode.BAD_REQUEST, "fcdsl.ids is required");
        }
        
        String entityType = fcdsl.getEntity();
        if (entityType == null) {
            return FapiResponse.error(requestId, FapiCode.BAD_REQUEST, "fcdsl.entity is required");
        }
        
        String index = getIndexName(entityType);
        Map<String, Object> result = esClient.mgetById(index, fcdsl.getIds());
        
        if (result == null || result.isEmpty()) {
            return FapiResponse.error(requestId, FapiCode.NOT_FOUND, "No data found");
        }
        
        return FapiResponse.success(requestId, result);
    }
    
    /**
     * 处理search请求
     * 请求格式：{ "api": "base.search", "fcdsl": { "entity": "tx", "filter": {...}, "size": 20 } }
     */
    private FapiResponse handleSearch(FapiRequest request) {
        String requestId = request.getId();
        Fcdsl fcdsl = request.getFcdsl();
        if (fcdsl == null || fcdsl.getEntity() == null) {
            return FapiResponse.error(requestId, FapiCode.BAD_REQUEST, "fcdsl.entity is required");
        }
        
        String index = getIndexName(fcdsl.getEntity());
        List<Object> result = esClient.search(index, fcdsl);
        return FapiResponse.success(requestId, result);
    }
    
    /**
     * 处理totals请求 - 获取所有索引文档数量
     * 请求格式：{ "id": "req-xxx", "api": "base.totals" }
     * 响应：{ "data": { "block": "123456", "tx": "789012", ... } }
     */
    private FapiResponse handleTotals(FapiRequest request) {
        String requestId = request.getId();
        try {
            // 使用Elasticsearch cat API获取所有索引信息
            IndicesResponse result = esClient.cat().indices();
            List<IndicesRecord> indicesRecordList = result.valueBody();
            
            // 从EntityProperty获取实体名称集合
            Set<String> entityNames = EntityProperty.getEntityNames();
            
            // 只保留EntityProperty中定义的索引
            Map<String, String> allSumMap = new HashMap<>();
            for (IndicesRecord record : indicesRecordList) {
                String indexName = record.index();
                if (entityNames.contains(indexName)) {
                    allSumMap.put(indexName, record.docsCount());
                }
            }
            
            FapiResponse response = FapiResponse.success(requestId, allSumMap);
            response.setGot((long) allSumMap.size());
            response.setTotal((long) allSumMap.size());
            return response;
        } catch (Exception e) {
            log.error("Failed to handle totals", e);
            return FapiResponse.error(requestId, FapiCode.INTERNAL_ERROR, 
                "Failed to get totals: " + e.getMessage());
        }
    }
    
    /**
     * 健康检查
     */
    private FapiResponse handleHealth(String requestId) {
        Map<String, Object> health = Map.of(
            "status", "healthy",
            "component", getName(),
            "timestamp", System.currentTimeMillis()
        );
        return FapiResponse.success(requestId, health);
    }
    
    // ==================== 余额查询 ====================
    
    /**
     * 处理balanceByIds请求 - 根据FID列表获取余额
     * 请求格式：{ "id": "req-xxx", "api": "base.balanceByIds", "fcdsl": { "ids": ["FEk41...", "FGw2..."] } }
     * 响应：{ "data": { "FEk41...": 100000000, "FGw2...": 50000000 } }
     */
    private FapiResponse handleBalanceByIds(FapiRequest request, String peerId) {
        String requestId = request.getId();
        Fcdsl fcdsl = request.getFcdsl();
        
        List<String> fids = fcdsl != null ? fcdsl.getIds() : null;
        if (fids == null || fids.isEmpty()) {
            return FapiResponse.error(requestId, FapiCode.BAD_REQUEST, "fcdsl.ids (FID list) is required");
        }
        
        try {
            Map<String, Long> balanceMap = sumCashValueByOwners(fids);
            FapiResponse response = FapiResponse.success(requestId, balanceMap);
            response.setGot((long) balanceMap.size());
            response.setTotal((long) balanceMap.size());
            return response;
        } catch (Exception e) {
            log.error("Failed to handle balanceByIds", e);
            return FapiResponse.error(requestId, FapiCode.INTERNAL_ERROR, 
                "Failed to get balances: " + e.getMessage());
        }
    }
    
    /**
     * 处理cashValid请求 - 获取有效UTXO
     * 
     * 两种模式：
     * 1. 标准FCDSL查询：{ "api": "base.cashValid", "fcdsl": { "filter": {...} } }
     * 2. 智能选币：{ "api": "base.cashValid", "fcdsl": { "ids": ["fid"] }, 
     *              "params": { "amount": "1.0", "cd": "1000", "msgSize": 100, "outputSize": 2 } }
     */
    private FapiResponse handleCashValid(FapiRequest request, String peerId) {
        String requestId = request.getId();
        Fcdsl fcdsl = request.getFcdsl();
        Map<String, Object> params = parseParams(request.getParams(), Map.class);
        
        // 模式1：标准FCDSL查询（params为空）
        if (params == null || params.isEmpty()) {
            if (fcdsl == null) {
                fcdsl = new Fcdsl();
            }
            // 确保filter包含valid=true条件
            if (fcdsl.getFilter() == null) {
                fcdsl.addNewFilter().addNewTerms().addNewFields("valid").addNewValues("true");
            }
            
            QueryResult<Cash> queryResult = queryExecutor.executeQuery("cash", Cash.class, fcdsl, null);
            if (queryResult == null || queryResult.isEmpty()) {
                return FapiResponse.error(requestId, FapiCode.NOT_FOUND, "No valid cashes found");
            }
            
            FapiResponse response = FapiResponse.success(requestId, queryResult.getData());
            response.setGot(queryResult.getGot());
            response.setTotal(queryResult.getTotal());
            response.setLast(queryResult.getLast());
            return response;
        }
        
        // 模式2：智能选币（params包含amount/cd等参数）
        String fid = null;
        if (fcdsl != null && fcdsl.getIds() != null && !fcdsl.getIds().isEmpty()) {
            fid = fcdsl.getIds().get(0);
        }
        if (fid == null) {
            fid = (String) params.get("fid");
        }
        if (fid == null || fid.isEmpty()) {
            return FapiResponse.error(requestId, FapiCode.BAD_REQUEST, "fid parameter is required");
        }
        
        try {
            String amountStr = (String) params.get("amount");
            String cdStr = (String) params.get("cd");
            String msgSizeStr = (String) params.get("msgSize");
            String outputSizeStr = (String) params.get("outputSize");
            String sinceHeightStr = (String) params.get("sinceHeight");
            
            Long amount = amountStr != null ? FchUtils.coinToSatoshi(Double.parseDouble(amountStr)) : null;
            Long cd = cdStr != null ? Long.parseLong(cdStr) : null;
            int msgSize = msgSizeStr != null ? Integer.parseInt(msgSizeStr) : 0;
            int outputSize = outputSizeStr != null ? Integer.parseInt(outputSizeStr) : 0;
            Long sinceHeight = sinceHeightStr != null ? Long.parseLong(sinceHeightStr) : null;
            
            List<Cash> cashList = getValidCashes(fid, amount, cd, sinceHeight, outputSize, msgSize);
            if (cashList == null || cashList.isEmpty()) {
                return FapiResponse.error(requestId, FapiCode.NOT_FOUND, "No valid cashes found");
            }
            
            FapiResponse response = FapiResponse.success(requestId, cashList);
            response.setGot((long) cashList.size());
            response.setTotal((long) cashList.size());
            return response;
        } catch (NumberFormatException e) {
            return FapiResponse.error(requestId, FapiCode.BAD_REQUEST, 
                "Invalid parameter format: " + e.getMessage());
        } catch (IllegalStateException e) {
            return FapiResponse.error(requestId, FapiCode.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to get valid cashes", e);
            return FapiResponse.error(requestId, FapiCode.INTERNAL_ERROR, 
                "Failed to get valid cashes: " + e.getMessage());
        }
    }
    
    /**
     * 处理getUtxo请求 - 获取UTXO（Utxo格式）
     * 请求格式：{ "api": "base.getUtxo", "params": { "address": "FEk41...", "amount": "1.0", "cd": "0" } }
     * 响应：{ "data": [{ "txid": "...", "vout": 0, "value": 100000000, ... }] }
     */
    private FapiResponse handleGetUtxo(FapiRequest request, String peerId) {
        String requestId = request.getId();
        Map<String, Object> params = parseParams(request.getParams(), Map.class);
        
        if (params == null || params.isEmpty()) {
            return FapiResponse.error(requestId, FapiCode.BAD_REQUEST, "params is required");
        }
        
        String fid = (String) params.get("address");
        if (fid == null || fid.isEmpty()) {
            return FapiResponse.error(requestId, FapiCode.BAD_REQUEST, "address parameter is required");
        }
        
        if (!KeyTools.isGoodFid(fid)) {
            return FapiResponse.error(requestId, FapiCode.BAD_REQUEST, "Invalid FID format");
        }
        
        try {
            String amountStr = (String) params.get("amount");
            String cdStr = (String) params.get("cd");
            
            Long amount = amountStr != null ? FchUtils.coinStrToSatoshi(amountStr) : 0L;
            Long cd = cdStr != null ? Long.parseLong(cdStr) : 0L;
            
            List<Cash> cashList = getValidCashes(fid, amount, cd, null, 0, 0);
            if (cashList == null || cashList.isEmpty()) {
                return FapiResponse.error(requestId, FapiCode.NOT_FOUND, "No UTXOs found");
            }
            
            // 转换为Utxo格式
            List<Utxo> utxoList = new ArrayList<>();
            for (Cash cash : cashList) {
                utxoList.add(Utxo.cashToUtxo(cash));
            }
            
            FapiResponse response = FapiResponse.success(requestId, utxoList);
            response.setGot((long) utxoList.size());
            response.setTotal((long) utxoList.size());
            return response;
        } catch (Exception e) {
            log.error("Failed to get UTXOs", e);
            return FapiResponse.error(requestId, FapiCode.INTERNAL_ERROR, 
                "Failed to get UTXOs: " + e.getMessage());
        }
    }
    
    // ==================== 链信息 ====================
    
    /**
     * 处理chainInfo请求 - 获取链信息
     * 请求格式：{ "api": "base.chainInfo" } 或 { "api": "base.chainInfo", "params": { "height": 123456 } }
     * 响应：{ "data": { "bestHeight": ..., "bestBlockHash": ..., ... } }
     */
    private FapiResponse handleChainInfo(FapiRequest request, String peerId) {
        String requestId = request.getId();
        Map<String, Object> params = parseParams(request.getParams(), Map.class);
        
        Long height = null;
        if (params != null) {
            String heightStr = (String) params.get("height");
            if (heightStr != null && !heightStr.isBlank()) {
                try {
                    height = Long.parseLong(heightStr);
                } catch (NumberFormatException e) {
                    return FapiResponse.error(requestId, FapiCode.BAD_REQUEST, "Invalid height parameter");
                }
            }
        }
        
        RpcResult<FchChainInfo> result = rpcService.getChainInfo(height);
        if (!result.isSuccess()) {
            return FapiResponse.error(requestId, FapiCode.INTERNAL_ERROR, result.getErrorMessage());
        }
        
        return FapiResponse.success(requestId, result.getData());
    }
    
    /**
     * 处理blockTimeHistory请求 - 获取出块时间历史
     * 请求格式：{ "api": "base.blockTimeHistory", "params": { "startTime": 0, "endTime": 0, "count": 100 } }
     */
    private FapiResponse handleBlockTimeHistory(FapiRequest request, String peerId) {
        String requestId = request.getId();
        HistoryParams params = parseHistoryParams(request);
        if (params.errorMessage != null) {
            return FapiResponse.error(requestId, FapiCode.BAD_REQUEST, params.errorMessage);
        }
        
        if (params.count > FchChainInfo.MAX_REQUEST_COUNT) {
            return FapiResponse.error(requestId, FapiCode.BAD_REQUEST, 
                "The count can not be bigger than " + FchChainInfo.MAX_REQUEST_COUNT);
        }
        
        RpcResult<Map<Long, Long>> result = rpcService.getBlockTimeHistory(
            params.startTime, params.endTime, params.count);
        if (!result.isSuccess()) {
            return FapiResponse.error(requestId, FapiCode.INTERNAL_ERROR, result.getErrorMessage());
        }
        
        Map<Long, Long> hist = result.getData();
        FapiResponse response = FapiResponse.success(requestId, hist);
        response.setGot((long) hist.size());
        response.setTotal(getHistoryTotal());
        return response;
    }
    
    /**
     * 处理difficultyHistory请求 - 获取难度历史
     * 请求格式：{ "api": "base.difficultyHistory", "params": { "startTime": 0, "endTime": 0, "count": 100 } }
     */
    private FapiResponse handleDifficultyHistory(FapiRequest request, String peerId) {
        String requestId = request.getId();
        HistoryParams params = parseHistoryParams(request);
        if (params.errorMessage != null) {
            return FapiResponse.error(requestId, FapiCode.BAD_REQUEST, params.errorMessage);
        }
        
        if (params.count > FchChainInfo.MAX_REQUEST_COUNT) {
            return FapiResponse.error(requestId, FapiCode.BAD_REQUEST, 
                "The count can not be bigger than " + FchChainInfo.MAX_REQUEST_COUNT);
        }
        
        RpcResult<Map<Long, String>> result = rpcService.getDifficultyHistory(
            params.startTime, params.endTime, params.count);
        if (!result.isSuccess()) {
            return FapiResponse.error(requestId, FapiCode.INTERNAL_ERROR, result.getErrorMessage());
        }
        
        Map<Long, String> hist = result.getData();
        FapiResponse response = FapiResponse.success(requestId, hist);
        response.setGot((long) hist.size());
        response.setTotal(getHistoryTotal());
        return response;
    }
    
    /**
     * 处理hashRateHistory请求 - 获取算力历史
     * 请求格式：{ "api": "base.hashRateHistory", "params": { "startTime": 0, "endTime": 0, "count": 100 } }
     */
    private FapiResponse handleHashRateHistory(FapiRequest request, String peerId) {
        String requestId = request.getId();
        HistoryParams params = parseHistoryParams(request);
        if (params.errorMessage != null) {
            return FapiResponse.error(requestId, FapiCode.BAD_REQUEST, params.errorMessage);
        }
        
        if (params.count > FchChainInfo.MAX_REQUEST_COUNT) {
            return FapiResponse.error(requestId, FapiCode.BAD_REQUEST, 
                "The count can not be bigger than " + FchChainInfo.MAX_REQUEST_COUNT);
        }
        
        RpcResult<Map<Long, String>> result = rpcService.getHashRateHistory(
            params.startTime, params.endTime, params.count);
        if (!result.isSuccess()) {
            return FapiResponse.error(requestId, FapiCode.INTERNAL_ERROR, result.getErrorMessage());
        }
        
        Map<Long, String> hist = result.getData();
        FapiResponse response = FapiResponse.success(requestId, hist);
        response.setGot((long) hist.size());
        response.setTotal(getHistoryTotal());
        return response;
    }
    
    // ==================== 内存池 ====================
    
    /**
     * 处理unconfirmed请求 - 获取未确认交易信息
     * 请求格式：{ "api": "base.unconfirmed", "fcdsl": { "ids": ["FEk41...", "FGw2..."] } }
     * 响应：{ "data": { "FEk41...": { "incomeCount": 1, "incomeValue": 100000000, ... }, ... } }
     */
    private FapiResponse handleUnconfirmed(FapiRequest request, String peerId) {
        String requestId = request.getId();
        Fcdsl fcdsl = request.getFcdsl();
        
        List<String> idList = fcdsl != null ? fcdsl.getIds() : null;
        if (idList == null || idList.isEmpty()) {
            return FapiResponse.error(requestId, FapiCode.BAD_REQUEST, "fcdsl.ids (FID list) is required");
        }
        
        try {
            Map<String, UnconfirmedInfo> resultMap = getUnconfirmedInfo(idList);
            FapiResponse response = FapiResponse.success(requestId, resultMap);
            response.setGot((long) resultMap.size());
            response.setTotal((long) resultMap.size());
            return response;
        } catch (Exception e) {
            log.error("Failed to handle unconfirmed", e);
            return FapiResponse.error(requestId, FapiCode.INTERNAL_ERROR, 
                "Failed to get unconfirmed info: " + e.getMessage());
        }
    }
    
    /**
     * 处理unconfirmedCashes请求 - 获取未确认的Cash
     * 请求格式：{ "api": "base.unconfirmedCashes" } 或 { "api": "base.unconfirmedCashes", "fcdsl": { "ids": [...] } }
     * 响应：{ "data": { "FEk41...": [{ ... }], ... } }
     */
    private FapiResponse handleUnconfirmedCashes(FapiRequest request, String peerId) {
        String requestId = request.getId();
        Fcdsl fcdsl = request.getFcdsl();
        
        List<String> fidList = fcdsl != null ? fcdsl.getIds() : null; // 可为null
        
        try {
            Map<String, List<Cash>> resultMap = getUnconfirmedCashes(fidList);
            FapiResponse response = FapiResponse.success(requestId, resultMap);
            response.setGot((long) resultMap.size());
            response.setTotal((long) resultMap.size());
            return response;
        } catch (Exception e) {
            log.error("Failed to handle unconfirmedCashes", e);
            return FapiResponse.error(requestId, FapiCode.INTERNAL_ERROR, 
                "Failed to get unconfirmed cashes: " + e.getMessage());
        }
    }
    
    // ==================== 交易操作 ====================
    
    /**
     * 处理broadcastTx请求 - 广播交易
     * 请求格式：{ "api": "base.broadcastTx", "params": { "rawTx": "010000000..." } }
     * 响应：{ "data": "txid..." }
     */
    private FapiResponse handleBroadcastTx(FapiRequest request, String peerId) {
        String requestId = request.getId();
        Map<String, Object> params = parseParams(request.getParams(), Map.class);
        
        if (params == null || !params.containsKey("rawTx")) {
            return FapiResponse.error(requestId, FapiCode.BAD_REQUEST, "rawTx parameter is required");
        }
        
        String rawTx = (String) params.get("rawTx");
        RpcResult<String> result = rpcService.broadcastTransaction(rawTx);
        
        if (!result.isSuccess()) {
            return FapiResponse.error(requestId, FapiCode.INTERNAL_ERROR, result.getErrorMessage());
        }
        
        return FapiResponse.success(requestId, result.getData());
    }
    
    /**
     * 处理decodeTx请求 - 解码交易
     * 请求格式：{ "api": "base.decodeTx", "params": { "rawTx": "010000000..." } }
     * 响应：{ "data": { "txid": "...", "version": 1, "vin": [...], "vout": [...], ... } }
     */
    private FapiResponse handleDecodeTx(FapiRequest request, String peerId) {
        String requestId = request.getId();
        Map<String, Object> params = parseParams(request.getParams(), Map.class);
        
        if (params == null || !params.containsKey("rawTx")) {
            return FapiResponse.error(requestId, FapiCode.BAD_REQUEST, "rawTx parameter is required");
        }
        
        String rawTx = (String) params.get("rawTx");
        RpcResult<Object> result = rpcService.decodeTransaction(rawTx);
        
        if (!result.isSuccess()) {
            return FapiResponse.error(requestId, FapiCode.INTERNAL_ERROR, result.getErrorMessage());
        }
        
        return FapiResponse.success(requestId, result.getData());
    }
    
    /**
     * 处理estimateFee请求 - 估算交易费用
     * 请求格式：{ "api": "base.estimateFee" } 或 { "api": "base.estimateFee", "params": { "nBlocks": 6 } }
     * 响应：{ "data": 0.0001 }
     */
    private FapiResponse handleEstimateFee(FapiRequest request, String peerId) {
        String requestId = request.getId();
        Map<String, Object> params = parseParams(request.getParams(), Map.class);
        
        Integer nBlocks = null;
        if (params != null && params.containsKey("nBlocks")) {
            try {
                nBlocks = Integer.parseInt(params.get("nBlocks").toString());
            } catch (NumberFormatException e) {
                return FapiResponse.error(requestId, FapiCode.BAD_REQUEST, "Invalid nBlocks parameter");
            }
        }
        
        RpcResult<Double> result = rpcService.estimateFee(nBlocks);
        
        if (!result.isSuccess()) {
            return FapiResponse.error(requestId, FapiCode.INTERNAL_ERROR, result.getErrorMessage());
        }
        
        return FapiResponse.success(requestId, result.getData());
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 支持的实体类型映射到ES索引
     */
    private String getIndexName(String entityType) {
        EntityProperty prop = EntityProperty.getByName(entityType);
        if (prop != null) {
            return prop.getEntityName();
        }
        return switch (entityType.toLowerCase()) {
            case "address" -> "freer";
            case "transaction" -> "tx";
            case "utxo" -> "cash";
            default -> entityType;
        };
    }
    
    /**
     * 计算FID列表的余额
     */
    private Map<String, Long> sumCashValueByOwners(List<String> fids) throws Exception {
        Map<String, Long> balanceMap = new HashMap<>();
        
        List<FieldValue> fidValues = new ArrayList<>();
        for (String fid : fids) {
            fidValues.add(FieldValue.of(fid));
            balanceMap.put(fid, 0L);
        }
        
        SearchRequest.Builder searchBuilder = new SearchRequest.Builder();
        searchBuilder.index("cash");
        searchBuilder.size(10000);
        
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
        TermsQuery ownerQuery = TermsQuery.of(t -> t.field("owner").terms(t1 -> t1.value(fidValues)));
        boolBuilder.must(new Query.Builder().terms(ownerQuery).build());
        TermQuery validQuery = TermQuery.of(t -> t.field("valid").value(true));
        boolBuilder.must(new Query.Builder().term(validQuery).build());
        
        searchBuilder.query(q -> q.bool(boolBuilder.build()));
        
        SearchResponse<Cash> result = esClient.search(searchBuilder.build(), Cash.class);
        
        if (result.hits() != null && result.hits().hits() != null) {
            for (Hit<Cash> hit : result.hits().hits()) {
                Cash cash = hit.source();
                if (cash != null && cash.getOwner() != null && cash.getValue() != null) {
                    balanceMap.merge(cash.getOwner(), cash.getValue(), Long::sum);
                }
            }
        }
        
        return balanceMap;
    }
    
    /**
     * 获取有效UTXO（智能选币）
     */
    private List<Cash> getValidCashes(String fid, Long amount, Long cd, Long sinceHeight,
                                     int outputSize, int msgSize) throws Exception {
        List<Cash> cashList = new ArrayList<>();
        
        SearchRequest.Builder searchBuilder = new SearchRequest.Builder();
        searchBuilder.index("cash");
        searchBuilder.size(200);
        searchBuilder.trackTotalHits(t -> t.enabled(true));
        searchBuilder.sort(s -> s.field(f -> f.field("cd").order(SortOrder.Asc)));
        searchBuilder.sort(s -> s.field(f -> f.field("id").order(SortOrder.Asc)));
        
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
        boolBuilder.must(new Query.Builder().term(TermQuery.of(t -> t.field("owner").value(fid))).build());
        boolBuilder.must(new Query.Builder().term(TermQuery.of(t -> t.field("valid").value(true))).build());
        
        if (sinceHeight != null) {
            RangeQuery heightQuery = RangeQuery.of(r -> r.field("birthHeight").gt(JsonData.of(sinceHeight)));
            boolBuilder.must(new Query.Builder().range(heightQuery).build());
        }
        
        searchBuilder.query(q -> q.bool(boolBuilder.build()));
        
        SearchResponse<Cash> result = esClient.search(searchBuilder.build(), Cash.class);
        
        if (result.hits() == null || result.hits().hits() == null || result.hits().hits().isEmpty()) {
            return cashList;
        }
        
        for (Hit<Cash> hit : result.hits().hits()) {
            if (hit.source() != null) {
                cashList.add(hit.source());
            }
        }
        
        if (amount == null && cd == null) {
            return cashList;
        }
        
        // 智能选币逻辑
        Long bestHeight = settings.getBestHeight();
        amount = amount == null ? 0L : amount;
        cd = cd == null ? 0L : cd;
        
        long fchSum = 0;
        long cdSum = 0;
        long fee = 0;
        List<Cash> meetList = new ArrayList<>();
        
        for (Cash cash : cashList) {
            if (isImmature(cash, bestHeight)) continue;
            
            long cdd = 0;
            if (cash.getBirthTime() != null) {
                cdd = FchUtils.cdd(cash.getValue(), cash.getBirthTime(), System.currentTimeMillis() / 1000);
            }
            
            fchSum += cash.getValue();
            cdSum += cdd;
            meetList.add(cash);
            
            long txSize = TxCreator.calcTxSize(meetList.size(), outputSize, msgSize);
            fee = TxCreator.calcFee(txSize, TxCreator.DEFAULT_FEE_RATE);
            
            if (fchSum >= (amount + fee) && cdSum >= cd) {
                break;
            }
        }
        
        if (fchSum < (amount + fee) || cdSum < cd) {
            throw new IllegalStateException("Can't get enough amount or cd within 200 cashes");
        }
        
        return meetList;
    }
    
    /**
     * 检查Cash是否未成熟（coinbase需要等待一定高度）
     */
    private boolean isImmature(Cash cash, Long bestHeight) {
        if (bestHeight == null) return false;
        if (!"coinbase".equals(cash.getIssuer())) return false;
        
        if (Constants.FUND_FID.equals(cash.getOwner())) {
            return (bestHeight - cash.getBirthHeight()) < 10000;
        }
        return (bestHeight - cash.getBirthHeight()) < 100;
    }
    
    /**
     * 获取未确认交易信息
     */
    private Map<String, UnconfirmedInfo> getUnconfirmedInfo(List<String> idList) throws Exception {
        Map<String, UnconfirmedInfo> resultMap = new HashMap<>();
        
        for (String id : idList) {
            UnconfirmedInfo info = new UnconfirmedInfo();
            info.setFid(id);
            info.setIncomeCount(0);
            info.setIncomeValue(0);
            info.setSpendCount(0);
            info.setSpendValue(0);
            info.setNet(0);
            resultMap.put(id, info);
        }
        
        String[] mempoolTxIds = rpcService.getMempoolTxIds();
        if (mempoolTxIds == null || mempoolTxIds.length == 0) {
            return resultMap;
        }
        
        for (String txId : mempoolTxIds) {
            try {
                String rawTxHex = rpcService.getRawTx(txId);
                if (rawTxHex == null) continue;
                
                TxHasInfo txInfo = RawTxParser.parseMempoolTx(rawTxHex, txId, null, esClient);
                if (txInfo == null) continue;
                
                for (String fid : idList) {
                    UnconfirmedInfo info = resultMap.get(fid);
                    Map<String, Long> txValueMap = info.getTxValueMap();
                    if (txValueMap == null) {
                        txValueMap = new HashMap<>();
                        info.setTxValueMap(txValueMap);
                    }
                    
                    if (txInfo.getInCashList() != null) {
                        for (Cash cash : txInfo.getInCashList()) {
                            if (fid.equals(cash.getOwner()) && cash.getValue() != null) {
                                info.setSpendCount(info.getSpendCount() + 1);
                                info.setSpendValue(info.getSpendValue() + cash.getValue());
                                info.setNet(info.getNet() - cash.getValue());
                                txValueMap.merge(txId, -cash.getValue(), Long::sum);
                            }
                        }
                    }
                    
                    if (txInfo.getOutCashList() != null) {
                        for (Cash cash : txInfo.getOutCashList()) {
                            if (fid.equals(cash.getOwner()) && cash.isValid() != null 
                                && cash.isValid() && cash.getValue() != null) {
                                info.setIncomeCount(info.getIncomeCount() + 1);
                                info.setIncomeValue(info.getIncomeValue() + cash.getValue());
                                info.setNet(info.getNet() + cash.getValue());
                                txValueMap.merge(txId, cash.getValue(), Long::sum);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error processing mempool tx {}: {}", txId, e.getMessage());
            }
        }
        
        for (UnconfirmedInfo info : resultMap.values()) {
            if (info.getTxValueMap() != null && info.getTxValueMap().isEmpty()) {
                info.setTxValueMap(null);
            }
        }
        
        return resultMap;
    }
    
    /**
     * 获取未确认的Cash
     */
    private Map<String, List<Cash>> getUnconfirmedCashes(List<String> fidList) throws Exception {
        Map<String, List<Cash>> resultMap = new HashMap<>();
        
        if (fidList != null && !fidList.isEmpty()) {
            for (String fid : fidList) {
                resultMap.put(fid, new ArrayList<>());
            }
        }
        
        String[] mempoolTxIds = rpcService.getMempoolTxIds();
        if (mempoolTxIds == null || mempoolTxIds.length == 0) {
            return resultMap;
        }
        
        for (String txId : mempoolTxIds) {
            try {
                String rawTxHex = rpcService.getRawTx(txId);
                if (rawTxHex == null) continue;
                
                TxHasInfo txInfo = RawTxParser.parseMempoolTx(rawTxHex, txId, null, esClient);
                if (txInfo == null) continue;
                
                if (txInfo.getInCashList() != null) {
                    for (Cash cash : txInfo.getInCashList()) {
                        String owner = cash.getOwner();
                        if (owner != null && (fidList == null || fidList.isEmpty() || fidList.contains(owner))) {
                            resultMap.computeIfAbsent(owner, k -> new ArrayList<>()).add(cash);
                        }
                    }
                }
                
                if (txInfo.getOutCashList() != null) {
                    for (Cash cash : txInfo.getOutCashList()) {
                        String owner = cash.getOwner();
                        if (owner != null && (fidList == null || fidList.isEmpty() || fidList.contains(owner))) {
                            resultMap.computeIfAbsent(owner, k -> new ArrayList<>()).add(cash);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error processing mempool tx {}: {}", txId, e.getMessage());
            }
        }
        
        return resultMap;
    }
    
    /**
     * 解析历史查询参数
     */
    private HistoryParams parseHistoryParams(FapiRequest request) {
        HistoryParams params = new HistoryParams();
        Map<String, Object> paramsMap = parseParams(request.getParams(), Map.class);
        
        if (paramsMap == null || paramsMap.isEmpty()) {
            return params;
        }
        
        try {
            if (paramsMap.containsKey("startTime")) {
                params.startTime = Long.parseLong(paramsMap.get("startTime").toString());
            }
            if (paramsMap.containsKey("endTime")) {
                params.endTime = Long.parseLong(paramsMap.get("endTime").toString());
            }
            if (paramsMap.containsKey("count")) {
                params.count = Integer.parseInt(paramsMap.get("count").toString());
            }
        } catch (NumberFormatException e) {
            params.errorMessage = "Invalid history parameters";
        }
        
        return params;
    }
    
    private Long getHistoryTotal() {
        Long bestHeight = settings.getBestHeight();
        if (bestHeight != null && bestHeight > 0) {
            return bestHeight - 1;
        }
        return null;
    }
    
    /**
     * 历史参数容器
     */
    private static class HistoryParams {
        long startTime;
        long endTime;
        int count;
        String errorMessage;
    }
    
    @SuppressWarnings("unchecked")
    private <T> T parseParams(Object params, Class<T> clazz) {
        if (params == null) return null;
        if (clazz.isInstance(params)) return (T) params;
        return JsonUtils.fromJson(JsonUtils.toJson(params), clazz);
    }
}
```

#### 4.4.3 客户端实现

```java
/**
 * BASE组件客户端
 * 提供类型安全的API调用方法
 */
public class BaseClient {
    private final FapiClient fapiClient;
    
    public BaseClient(FapiClient fapiClient) {
        this.fapiClient = fapiClient;
    }
    
    // ==================== 通用查询 ====================
    
    /**
     * 根据ID列表获取实体
     */
    public <T> Map<String, T> getByIds(String entity, List<String> ids, Class<T> clazz) throws FapiException {
        FapiRequest request = new FapiRequest();
        request.setId(generateRequestId());
        request.setApi("base.getByIds");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEntity(entity);
        fcdsl.setIds(ids);
        request.setFcdsl(fcdsl);
        
        FapiResponse response = fapiClient.send(request);
        checkResponse(response);
        return parseMapResult(response.getData(), clazz);
    }
    
    /**
     * 搜索实体
     */
    public <T> List<T> search(String entity, Fcdsl fcdsl, Class<T> clazz) throws FapiException {
        FapiRequest request = new FapiRequest();
        request.setId(generateRequestId());
        request.setApi("base.search");
        fcdsl.setEntity(entity);
        request.setFcdsl(fcdsl);
        
        FapiResponse response = fapiClient.send(request);
        checkResponse(response);
        return parseListResult(response.getData(), clazz);
    }
    
    /**
     * 获取所有索引文档数量
     */
    public Map<String, String> totals() throws FapiException {
        FapiRequest request = new FapiRequest();
        request.setId(generateRequestId());
        request.setApi("base.totals");
        
        FapiResponse response = fapiClient.send(request);
        checkResponse(response);
        return (Map<String, String>) response.getData();
    }
    
    /**
     * 健康检查
     */
    public Map<String, Object> health() throws FapiException {
        FapiRequest request = new FapiRequest();
        request.setId(generateRequestId());
        request.setApi("base.health");
        
        FapiResponse response = fapiClient.send(request);
        checkResponse(response);
        return (Map<String, Object>) response.getData();
    }
    
    // ==================== 余额查询 ====================
    
    /**
     * 根据FID列表获取余额
     */
    public Map<String, Long> balanceByIds(List<String> fids) throws FapiException {
        FapiRequest request = new FapiRequest();
        request.setId(generateRequestId());
        request.setApi("base.balanceByIds");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(fids);
        request.setFcdsl(fcdsl);
        
        FapiResponse response = fapiClient.send(request);
        checkResponse(response);
        return parseBalanceMap(response.getData());
    }
    
    /**
     * 获取有效UTXO（标准查询）
     */
    public List<Cash> cashValid(Fcdsl fcdsl) throws FapiException {
        FapiRequest request = new FapiRequest();
        request.setId(generateRequestId());
        request.setApi("base.cashValid");
        request.setFcdsl(fcdsl);
        
        FapiResponse response = fapiClient.send(request);
        checkResponse(response);
        return parseListResult(response.getData(), Cash.class);
    }
    
    /**
     * 获取有效UTXO（智能选币）
     */
    public List<Cash> cashValid(String fid, Double amount, Long cd, Integer msgSize, Integer outputSize) throws FapiException {
        FapiRequest request = new FapiRequest();
        request.setId(generateRequestId());
        request.setApi("base.cashValid");
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(List.of(fid));
        request.setFcdsl(fcdsl);
        
        Map<String, String> params = new HashMap<>();
        if (amount != null) params.put("amount", String.valueOf(amount));
        if (cd != null) params.put("cd", String.valueOf(cd));
        if (msgSize != null) params.put("msgSize", String.valueOf(msgSize));
        if (outputSize != null) params.put("outputSize", String.valueOf(outputSize));
        request.setParams(params);
        
        FapiResponse response = fapiClient.send(request);
        checkResponse(response);
        return parseListResult(response.getData(), Cash.class);
    }
    
    /**
     * 获取UTXO（Utxo格式）
     */
    public List<Utxo> getUtxo(String address, Double amount, Long cd) throws FapiException {
        FapiRequest request = new FapiRequest();
        request.setId(generateRequestId());
        request.setApi("base.getUtxo");
        
        Map<String, String> params = new HashMap<>();
        params.put("address", address);
        if (amount != null) params.put("amount", String.valueOf(amount));
        if (cd != null) params.put("cd", String.valueOf(cd));
        request.setParams(params);
        
        FapiResponse response = fapiClient.send(request);
        checkResponse(response);
        return parseListResult(response.getData(), Utxo.class);
    }
    
    // ==================== 链信息 ====================
    
    /**
     * 获取链信息
     */
    public FchChainInfo chainInfo() throws FapiException {
        return chainInfo(null);
    }
    
    /**
     * 获取指定高度的链信息
     */
    public FchChainInfo chainInfo(Long height) throws FapiException {
        FapiRequest request = new FapiRequest();
        request.setId(generateRequestId());
        request.setApi("base.chainInfo");
        
        if (height != null) {
            request.setParams(Map.of("height", String.valueOf(height)));
        }
        
        FapiResponse response = fapiClient.send(request);
        checkResponse(response);
        return parseResult(response.getData(), FchChainInfo.class);
    }
    
    /**
     * 获取出块时间历史
     */
    public Map<Long, Long> blockTimeHistory(long startTime, long endTime, int count) throws FapiException {
        FapiRequest request = new FapiRequest();
        request.setId(generateRequestId());
        request.setApi("base.blockTimeHistory");
        request.setParams(Map.of(
            "startTime", String.valueOf(startTime),
            "endTime", String.valueOf(endTime),
            "count", String.valueOf(count)
        ));
        
        FapiResponse response = fapiClient.send(request);
        checkResponse(response);
        return parseHistoryMap(response.getData());
    }
    
    /**
     * 获取难度历史
     */
    public Map<Long, String> difficultyHistory(long startTime, long endTime, int count) throws FapiException {
        FapiRequest request = new FapiRequest();
        request.setId(generateRequestId());
        request.setApi("base.difficultyHistory");
        request.setParams(Map.of(
            "startTime", String.valueOf(startTime),
            "endTime", String.valueOf(endTime),
            "count", String.valueOf(count)
        ));
        
        FapiResponse response = fapiClient.send(request);
        checkResponse(response);
        return parseStringHistoryMap(response.getData());
    }
    
    /**
     * 获取算力历史
     */
    public Map<Long, String> hashRateHistory(long startTime, long endTime, int count) throws FapiException {
        FapiRequest request = new FapiRequest();
        request.setId(generateRequestId());
        request.setApi("base.hashRateHistory");
        request.setParams(Map.of(
            "startTime", String.valueOf(startTime),
            "endTime", String.valueOf(endTime),
            "count", String.valueOf(count)
        ));
        
        FapiResponse response = fapiClient.send(request);
        checkResponse(response);
        return parseStringHistoryMap(response.getData());
    }
    
    // ==================== 内存池 ====================
    
    /**
     * 获取未确认交易信息
     */
    public Map<String, UnconfirmedInfo> unconfirmed(List<String> fids) throws FapiException {
        FapiRequest request = new FapiRequest();
        request.setId(generateRequestId());
        request.setApi("base.unconfirmed");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(fids);
        request.setFcdsl(fcdsl);
        
        FapiResponse response = fapiClient.send(request);
        checkResponse(response);
        return parseMapResult(response.getData(), UnconfirmedInfo.class);
    }
    
    /**
     * 获取未确认的Cash（所有）
     */
    public Map<String, List<Cash>> unconfirmedCashes() throws FapiException {
        return unconfirmedCashes(null);
    }
    
    /**
     * 获取未确认的Cash（指定FID）
     */
    public Map<String, List<Cash>> unconfirmedCashes(List<String> fids) throws FapiException {
        FapiRequest request = new FapiRequest();
        request.setId(generateRequestId());
        request.setApi("base.unconfirmedCashes");
        
        if (fids != null && !fids.isEmpty()) {
            Fcdsl fcdsl = new Fcdsl();
            fcdsl.setIds(fids);
            request.setFcdsl(fcdsl);
        }
        
        FapiResponse response = fapiClient.send(request);
        checkResponse(response);
        return parseListMapResult(response.getData(), Cash.class);
    }
    
    // ==================== 交易操作 ====================
    
    /**
     * 广播交易
     */
    public String broadcastTx(String rawTx) throws FapiException {
        FapiRequest request = new FapiRequest();
        request.setId(generateRequestId());
        request.setApi("base.broadcastTx");
        request.setParams(Map.of("rawTx", rawTx));
        
        FapiResponse response = fapiClient.send(request);
        checkResponse(response);
        return (String) response.getData();
    }
    
    /**
     * 解码交易
     */
    public Object decodeTx(String rawTx) throws FapiException {
        FapiRequest request = new FapiRequest();
        request.setId(generateRequestId());
        request.setApi("base.decodeTx");
        request.setParams(Map.of("rawTx", rawTx));
        
        FapiResponse response = fapiClient.send(request);
        checkResponse(response);
        return response.getData();
    }
    
    /**
     * 估算交易费用
     */
    public Double estimateFee() throws FapiException {
        return estimateFee(null);
    }
    
    /**
     * 估算交易费用（指定确认区块数）
     */
    public Double estimateFee(Integer nBlocks) throws FapiException {
        FapiRequest request = new FapiRequest();
        request.setId(generateRequestId());
        request.setApi("base.estimateFee");
        
        if (nBlocks != null) {
            request.setParams(Map.of("nBlocks", String.valueOf(nBlocks)));
        }
        
        FapiResponse response = fapiClient.send(request);
        checkResponse(response);
        return ((Number) response.getData()).doubleValue();
    }
    
    // ==================== 辅助方法 ====================
    
    private String generateRequestId() {
        return "req-" + System.currentTimeMillis() + "-" + 
               Integer.toHexString(ThreadLocalRandom.current().nextInt());
    }
    
    private void checkResponse(FapiResponse response) throws FapiException {
        if (response.getCode() != FapiCode.SUCCESS) {
            throw new FapiException(response.getCode(), response.getMessage());
        }
    }
    
    private <T> T parseResult(Object data, Class<T> clazz) {
        return JsonUtils.fromJson(JsonUtils.toJson(data), clazz);
    }
    
    private <T> List<T> parseListResult(Object data, Class<T> clazz) {
        if (data instanceof List) {
            List<?> list = (List<?>) data;
            List<T> result = new ArrayList<>();
            for (Object item : list) {
                result.add(parseResult(item, clazz));
            }
            return result;
        }
        return List.of();
    }
    
    private <T> Map<String, T> parseMapResult(Object data, Class<T> clazz) {
        if (data instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) data;
            Map<String, T> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(entry.getKey().toString(), parseResult(entry.getValue(), clazz));
            }
            return result;
        }
        return Map.of();
    }
    
    private <T> Map<String, List<T>> parseListMapResult(Object data, Class<T> clazz) {
        if (data instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) data;
            Map<String, List<T>> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(entry.getKey().toString(), parseListResult(entry.getValue(), clazz));
            }
            return result;
        }
        return Map.of();
    }
    
    private Map<String, Long> parseBalanceMap(Object data) {
        if (data instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) data;
            Map<String, Long> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(entry.getKey().toString(), ((Number) entry.getValue()).longValue());
            }
            return result;
        }
        return Map.of();
    }
    
    private Map<Long, Long> parseHistoryMap(Object data) {
        if (data instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) data;
            Map<Long, Long> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(Long.parseLong(entry.getKey().toString()), 
                          ((Number) entry.getValue()).longValue());
            }
            return result;
        }
        return Map.of();
    }
    
    private Map<Long, String> parseStringHistoryMap(Object data) {
        if (data instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) data;
            Map<Long, String> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(Long.parseLong(entry.getKey().toString()), 
                          entry.getValue().toString());
            }
            return result;
        }
        return Map.of();
    }
}
```

#### 4.4.4 API请求/响应示例

```json
// ==================== 通用查询 ====================

// base.getByIds - 根据ID获取实体
// 请求：
{
  "id": "req-001",
  "api": "base.getByIds",
  "fcdsl": {
    "entity": "freer",
    "ids": ["FEk41Kqjar45fLDriztUDTUkdki7mmcjWK", "FGw2..."]
  }
}
// 响应：
{
  "id": "resp-001",
  "requestId": "req-001",
  "code": 0,
  "message": "Success",
  "data": {
    "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK": { "fid": "FEk41...", "balance": 100000000, ... },
    "FGw2...": { "fid": "FGw2...", "balance": 50000000, ... }
  }
}

// base.totals - 获取所有索引文档数量
// 请求：
{ "id": "req-002", "api": "base.totals" }
// 响应：
{
  "id": "resp-002",
  "requestId": "req-002",
  "code": 0,
  "data": {
    "block": "1234567",
    "tx": "12345678",
    "cash": "98765432",
    "freer": "12345"
  },
  "got": 4,
  "total": 4
}

// ==================== 余额查询 ====================

// base.balanceByIds - 根据FID列表获取余额
// 请求：
{
  "id": "req-003",
  "api": "base.balanceByIds",
  "fcdsl": { "ids": ["FEk41...", "FGw2..."] }
}
// 响应：
{
  "id": "resp-003",
  "requestId": "req-003",
  "code": 0,
  "data": {
    "FEk41...": 100000000,
    "FGw2...": 50000000
  },
  "got": 2,
  "total": 2
}

// base.cashValid - 智能选币
// 请求：
{
  "id": "req-004",
  "api": "base.cashValid",
  "fcdsl": { "ids": ["FEk41..."] },
  "params": { "amount": "1.0", "cd": "0", "outputSize": "2" }
}
// 响应：
{
  "id": "resp-004",
  "requestId": "req-004",
  "code": 0,
  "data": [
    { "cashId": "...", "value": 50000000, "owner": "FEk41...", ... },
    { "cashId": "...", "value": 60000000, "owner": "FEk41...", ... }
  ],
  "got": 2,
  "total": 2
}

// base.getUtxo - 获取UTXO（Utxo格式）
// 请求：
{
  "id": "req-005",
  "api": "base.getUtxo",
  "params": { "address": "FEk41...", "amount": "1.0" }
}
// 响应：
{
  "id": "resp-005",
  "requestId": "req-005",
  "code": 0,
  "data": [
    { "txid": "abc123...", "vout": 0, "value": 100000000, "scriptPubKey": "..." },
    { "txid": "def456...", "vout": 1, "value": 50000000, "scriptPubKey": "..." }
  ]
}

// ==================== 链信息 ====================

// base.chainInfo - 获取链信息
// 请求：
{ "id": "req-006", "api": "base.chainInfo" }
// 响应：
{
  "id": "resp-006",
  "requestId": "req-006",
  "code": 0,
  "data": {
    "bestHeight": 1234567,
    "bestBlockHash": "0000000000000abc...",
    "difficulty": "123456789.12",
    "hashRate": "1.23 EH/s",
    ...
  }
}

// base.blockTimeHistory - 获取出块时间历史
// 请求：
{
  "id": "req-007",
  "api": "base.blockTimeHistory",
  "params": { "startTime": "0", "endTime": "0", "count": "100" }
}
// 响应：
{
  "id": "resp-007",
  "requestId": "req-007",
  "code": 0,
  "data": {
    "1703580000": 600,
    "1703580600": 540,
    ...
  },
  "got": 100,
  "total": 1234566
}

// ==================== 内存池 ====================

// base.unconfirmed - 获取未确认交易信息
// 请求：
{
  "id": "req-008",
  "api": "base.unconfirmed",
  "fcdsl": { "ids": ["FEk41...", "FGw2..."] }
}
// 响应：
{
  "id": "resp-008",
  "requestId": "req-008",
  "code": 0,
  "data": {
    "FEk41...": {
      "fid": "FEk41...",
      "incomeCount": 1,
      "incomeValue": 100000000,
      "spendCount": 0,
      "spendValue": 0,
      "net": 100000000,
      "txValueMap": { "abc123...": 100000000 }
    },
    "FGw2...": { ... }
  }
}

// base.unconfirmedCashes - 获取未确认的Cash
// 请求：
{
  "id": "req-009",
  "api": "base.unconfirmedCashes",
  "fcdsl": { "ids": ["FEk41..."] }
}
// 响应：
{
  "id": "resp-009",
  "requestId": "req-009",
  "code": 0,
  "data": {
    "FEk41...": [
      { "cashId": "...", "value": 100000000, "valid": true, ... }
    ]
  }
}

// ==================== 交易操作 ====================

// base.broadcastTx - 广播交易
// 请求：
{
  "id": "req-010",
  "api": "base.broadcastTx",
  "params": { "rawTx": "0100000001abc..." }
}
// 响应：
{
  "id": "resp-010",
  "requestId": "req-010",
  "code": 0,
  "data": "abc123def456..."
}

// base.decodeTx - 解码交易
// 请求：
{
  "id": "req-011",
  "api": "base.decodeTx",
  "params": { "rawTx": "0100000001abc..." }
}
// 响应：
{
  "id": "resp-011",
  "requestId": "req-011",
  "code": 0,
  "data": {
    "txid": "abc123...",
    "version": 1,
    "vin": [...],
    "vout": [...],
    ...
  }
}

// base.estimateFee - 估算交易费用
// 请求：
{ "id": "req-012", "api": "base.estimateFee", "params": { "nBlocks": "6" } }
// 响应：
{
  "id": "resp-012",
  "requestId": "req-012",
  "code": 0,
  "data": 0.0001
}
```

### 4.5 DISK组件（独立文档）

> **注意**：DISK组件的详细设计已移至独立文档 `Docs/DISK服务设计方案.md`，将在后续专门任务中实现。

**简要说明**：
- DISK是基于FAPI框架的分布式存储服务
- 提供文件上传、下载、检查等功能
- 支持大文件传输和断点续传
- 采用开放存储设计，无需身份验证

**API列表**：
| API | 说明 |
|-----|------|
| `disk.list` | 查询文件列表 |
| `disk.put` | 上传小文件 |
| `disk.get` | 下载文件 |
| `disk.check` | 检查文件存在 |
| `disk.carve` | 永久保存 |
| `disk.prepareUpload` | 准备大文件上传 |
| `disk.resumeUpload` | 断点续传 |

### 4.6 FAPI服务器

```java
/**
 * FAPI服务器
 * 管理FudpNode和所有组件
 * 
 * 认证说明：
 * - 身份认证在FUDP层完成（公私钥签名验证）
 * - peerId即为已验证的用户FID
 * - FAPI层无需额外认证，直接信任peerId
 */
public class FapiServer implements NodeEventListener {
    private FudpNode fudpNode;
    private final Map<String, FapiComponent> components = new HashMap<>();
    private final Map<Class<? extends FapiComponent>, FapiComponent> componentsByClass = new HashMap<>();
    private final Service service;
    private final Settings settings;
    private final BalanceManager balanceManager;
    
    public FapiServer(Service service, Settings settings, byte[] symkey) {
        this.service = service;
        this.settings = settings;
        this.balanceManager = (BalanceManager) settings.getManager(Manager.ManagerType.BALANCE);
    }
    
    /**
     * 根据service.types加载组件
     */
    public void loadComponentsByTypes(String[] types) {
        ComponentRegistry registry = new ComponentRegistry();
        List<FapiComponent> loaded = registry.loadComponents(types);
        for (FapiComponent component : loaded) {
            component.initialize(this);
            components.put(component.getName(), component);
            componentsByClass.put(component.getClass(), component);
        }
    }
    
    /**
     * 获取组件引用（按名称）
     */
    @SuppressWarnings("unchecked")
    public <T extends FapiComponent> T getComponent(String name) {
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
     * 路由请求到对应组件
     * @param request FAPI请求
     * @param peerId 请求方FID（已通过FUDP层认证）
     */
    public FapiResponse routeRequest(FapiRequest request, String peerId) {
        String requestId = request.getId();
        
        // 验证请求格式
        if (request.getApi() == null || !request.getApi().contains(".")) {
            return FapiResponse.error(requestId, FapiCode.BAD_REQUEST, 
                "Invalid api format, expected: component.method");
        }
        
        String componentName = request.getComponentName();
        FapiComponent component = components.get(componentName);
        if (component == null) {
            return FapiResponse.error(requestId, FapiCode.NOT_FOUND, 
                "Component not found: " + componentName);
        }
        
        // 组件健康检查
        if (!component.isHealthy()) {
            return FapiResponse.error(requestId, FapiCode.SERVICE_UNAVAILABLE, 
                "Component not ready: " + componentName);
        }
        
        // 执行请求
        FapiResponse response;
        if (component.supportsAsync()) {
            response = component.handleRequestAsync(request, peerId).join();
        } else {
            response = component.handleRequest(request, peerId);
        }
        
        // 填充余额信息
        fillBalanceInfo(response, peerId);
        
        return response;
    }
    
    /**
     * 填充余额信息到响应
     * 集成BalanceManager，在每次响应中返回最新余额
     */
    private void fillBalanceInfo(FapiResponse response, String peerId) {
        if (balanceManager != null) {
            BalanceManager.BalanceView balance = balanceManager.getBalance(peerId);
            if (balance != null) {
                response.setBalance(balance.getBalance());
                response.setBalanceSeq(balance.getSeq());
            }
        }
        // 填充最佳区块高度
        response.setBestHeight(settings.getBestHeight());
    }
    
    /**
     * 启动服务（含端口冲突检测）
     */
    public void start() throws IOException {
        // 获取配置的端口
        int port = getPortFromServiceUrl();
        
        // 创建并启动FudpNode（端口被占用会抛出BindException）
        byte[] privateKey = settings.decryptPrikey();
        NodeConfig config = new NodeConfig()
            .setPort(port)
            .setDataDir("fudp_data/" + settings.getMainFid());
        
        fudpNode = new FudpNode(privateKey, config);
        fudpNode.setEventListener(this);
        
        // 注册流量计量监听器，用于计费
        if (balanceManager != null) {
            fudpNode.addMeterListener(record -> {
                BalanceManager.ChargeResult result = balanceManager.checkAndCharge(record);
                if (result.getCode() == BalanceManager.ResultCode.CREDIT_EXCEEDED) {
                    log.warn("Credit exceeded for peer {}", record.getPeerId());
                }
            });
        }
        
        fudpNode.start();
        
        // 加载组件
        loadComponentsByTypes(service.getTypes());
        
        log.info("FAPI Server started on port {}", port);
        log.info("FID: {}", fudpNode.getLocalFid());
        log.info("Components: {}", String.join(", ", components.keySet()));
    }
    
    /**
     * 关闭服务器（优雅关闭）
     * 
     * 关闭顺序：
     * 1. 停止接收新请求
     * 2. 等待进行中的请求完成（最多30秒）
     * 3. 按依赖逆序关闭组件（业务层→基础层）
     * 4. 关闭FudpNode
     */
    public void stop() {
        log.info("Stopping FAPI Server...");
        
        // 1. 标记服务为关闭中，停止接收新请求
        this.shuttingDown = true;
        
        // 2. 等待进行中的请求完成（最多30秒）
        long waitStart = System.currentTimeMillis();
        long maxWait = 30_000;
        while (pendingRequests.get() > 0 && 
               System.currentTimeMillis() - waitStart < maxWait) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (pendingRequests.get() > 0) {
            log.warn("Force shutdown with {} pending requests", pendingRequests.get());
        }
        
        // 3. 逆序关闭组件（业务层→基础层）
        List<String> orderedTypes = DependencyRegistry.resolveOrder(
            components.keySet().toArray(new String[0])
        );
        Collections.reverse(orderedTypes);
        
        for (String type : orderedTypes) {
            FapiComponent component = components.get(type);
            if (component != null) {
                try {
                    component.close(5000);
                    log.info("Component {} stopped", type);
                } catch (InterruptedException e) {
                    log.warn("Component {} stop interrupted", type);
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        // 4. 关闭FudpNode
        if (fudpNode != null) {
            fudpNode.stop();
        }
        
        log.info("FAPI Server stopped");
    }
}
```

## 5. 启动框架设计

### 5.1 统一启动配置

```java
/**
 * 服务启动配置
 */
public class ServiceBootstrapConfig {
    private String[] componentTypes;          // 要加载的组件类型
    private Map<String, Object> settings;     // 配置参数
    private List<Module> modules;             // 模块列表
    private BufferedReader br;                // 输入流
    
    // 预定义的服务配置
    public static ServiceBootstrapConfig forFapiServer() {
        return new ServiceBootstrapConfig()
            .setComponentTypes(new String[]{"BASE"})  // 默认包含BASE
            .setModules(List.of(
                new Module("Service", "ES"),
                new Module("Service", "NASA_RPC"),
                new Module("Manager", "MEMPOOL"),
                new Module("Manager", "CASH"),
                new Module("Manager", "BALANCE")
            ));
    }
    
    public static ServiceBootstrapConfig forFullService() {
        return forFapiServer()
            .setComponentTypes(new String[]{"BASE", "DISK"});
    }
}
```

### 5.2 统一启动入口

```java
/**
 * 服务启动器
 */
public class ServiceBootstrap {
    
    /**
     * 启动FAPI服务
     */
    public static FapiServer bootstrap(ServiceBootstrapConfig config) {
        // 1. 加载配置
        Configure.loadConfig(config.getBr());
        Configure configure = Configure.checkPassword(config.getBr());
        if (configure == null) return null;
        byte[] symkey = configure.getSymkey();
        
        // 2. 创建Settings
        Settings settings = loadOrCreateSettings(config, configure);
        
        // 3. 初始化外部服务客户端
        initializeExternalServices(settings, config.getModules());
        
        // 4. 加载或选择链上服务
        Service service = loadService(settings);
        if (service == null) return null;
        
        // 5. 确定要加载的组件类型
        String[] componentTypes = mergeComponentTypes(
            config.getComponentTypes(), 
            service.getTypes()
        );
        
        // 6. 创建并启动FAPI服务器
        FapiServer server = new FapiServer(service, settings, symkey);
        server.loadComponentsByTypes(componentTypes);
        
        try {
            server.start();
        } catch (BindException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            return null;
        }
        
        return server;
    }
    
    /**
     * 合并组件类型（配置指定 + 链上声明）
     */
    private static String[] mergeComponentTypes(String[] configured, String[] onChain) {
        Set<String> types = new LinkedHashSet<>();
        if (configured != null) Collections.addAll(types, configured);
        if (onChain != null) {
            for (String t : onChain) {
                if (ComponentRegistry.isComponent(t)) {
                    types.add(t);
                }
            }
        }
        return types.toArray(new String[0]);
    }
}
```

### 5.3 启动类设计

**启动类命名规范**：

| 类名 | 说明 | 状态 |
|-----|------|------|
| `StartFapiServerOld` | 原有服务端启动类 | 保留，逐步废弃 |
| `StartFapiClientOld` | 原有客户端启动类 | 保留，逐步废弃 |
| `StartFapiServer` | 新的服务端启动类 | 新建 |
| `StartFapiClient` | 新的客户端启动类 | 新建 |

**保留旧类的原因**：
- 现有代码可能依赖旧的启动逻辑
- 便于对比新旧实现，验证重构正确性
- 过渡期间提供回退选项

**服务端启动类**：

```java
/**
 * 新的FAPI服务端启动类
 * 
 * 文件：fapi/StartFapiServer.java
 * 注意：StartFapiServerOld.java 暂时保留
 */
public class StartFapiServer {
    public static void main(String[] args) {
        Menu.welcome("FAPI Server");
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        
        // 使用统一配置启动
        ServiceBootstrapConfig config = ServiceBootstrapConfig.forFapiServer()
            .setBr(br);
        
        FapiServer server = ServiceBootstrap.bootstrap(config);
        if (server == null) return;
        
        // 主菜单
        server.showMainMenu();
    }
}
```

**客户端启动类**：

```java
/**
 * 新的FAPI客户端启动类
 * 
 * 文件：fapi/StartFapiClient.java
 * 注意：StartFapiClientOld.java 暂时保留
 * 
 * 新增功能：
 * 1. 默认服务发现（127.0.0.1:8500, freecash.info:8500）
 * 2. 自动获取链上FAPI服务商列表供用户选择
 * 3. 服务连接失败时才提示手动输入
 */
public class StartFapiClient {
    private static final Logger log = LoggerFactory.getLogger(StartFapiClient.class);
    
    public static Settings settings;
    public static FudpNode fudpNode;
    public static FapiClient fapiClient;
    public static BufferedReader br;
    public static final String CLIENT_NAME = "FAPI Client";
    
    public static void main(String[] args) {
        Menu.welcome(CLIENT_NAME);
        br = new BufferedReader(new InputStreamReader(System.in));
        
        // 1. 初始化基础设置
        settings = initializeSettings(br);
        if (settings == null) return;
        
        // 2. 初始化FudpNode
        fudpNode = initializeFudpNode(settings);
        if (fudpNode == null) return;
        
        // 3. 服务发现与连接（新的自动发现逻辑）
        fapiClient = bootstrapClient(fudpNode, br);
        if (fapiClient == null) {
            System.out.println("Failed to connect to any FAPI service. Exiting.");
            cleanup();
            return;
        }
        
        // 4. 主菜单
        showMainMenu();
    }
    
    /**
     * 服务发现与连接
     * 优先级：1.默认服务 → 2.链上服务列表 → 3.手动输入
     */
    private static FapiClient bootstrapClient(FudpNode node, BufferedReader br) {
        System.out.println("Discovering FAPI services...");
        
        // 1. 尝试默认服务端点
        DiscoveryResult discovery = FapiServiceDiscovery.discoverViaDefaults(node);
        
        if (discovery != null) {
            // 创建临时客户端
            FapiClient tempClient = new FapiClient(node, discovery.getPeerId(),
                discovery.getServices().get(0).getId(), 30, settings);
            
            // 2. 查询链上FAPI服务商列表
            List<Service> providers = FapiServiceDiscovery.fetchFapiProviders(tempClient);
            
            if (providers != null && !providers.isEmpty()) {
                // 显示服务列表供用户选择
                Service selected = selectService(providers, br);
                if (selected != null) {
                    return connectToService(node, selected, br);
                }
            }
            
            // 没有链上服务或用户选择当前连接
            System.out.println("Using current connection: " + discovery.getPeerId());
            return tempClient;
        }
        
        // 3. 默认服务全部失败，提示手动输入
        System.out.println("\nCould not connect to default services.");
        System.out.println("Default endpoints tried:");
        for (String ep : FapiDefaults.DEFAULT_ENDPOINTS) {
            System.out.println("  - " + ep);
        }
        
        return manualConnect(node, br);
    }
    
    /**
     * 显示服务列表供用户选择
     */
    private static Service selectService(List<Service> services, BufferedReader br) {
        System.out.println("\n=== Available FAPI Services ===");
        for (int i = 0; i < services.size(); i++) {
            Service s = services.get(i);
            String url = s.getApiUrl() != null ? s.getApiUrl() : "N/A";
            System.out.printf("%2d) %-20s  SID: %s...  URL: %s\n",
                i + 1,
                s.getStdName() != null ? s.getStdName() : "Unknown",
                s.getId().substring(0, 8),
                url);
        }
        System.out.println(" 0) Use current connection");
        System.out.println("-1) Enter address manually");
        
        Long choice = Inputer.inputLong(br, "Select service (default 0)", 0L);
        
        if (choice == null || choice == 0) {
            return null; // 使用当前连接
        } else if (choice > 0 && choice <= services.size()) {
            return services.get(choice.intValue() - 1);
        }
        return null; // 其他情况（包括-1）返回null，触发手动输入
    }
    
    /**
     * 手动输入服务地址
     */
    private static FapiClient manualConnect(FudpNode node, BufferedReader br) {
        while (true) {
            String input = Inputer.inputString(br,
                "Enter FAPI service address (host:port, or 'q' to quit):");
            
            if (input == null || "q".equalsIgnoreCase(input.trim())) {
                return null;
            }
            
            try {
                String[] parts = input.trim().split(":");
                String host = parts[0];
                int port = parts.length > 1 ? Integer.parseInt(parts[1])
                    : FapiDefaults.DEFAULT_FAPI_PORT;
                
                DiscoveryResult result = FapiClient.discoverViaHelloAndPing(
                    node, host, port,
                    FapiDefaults.DEFAULT_DISCOVERY_TIMEOUT_MS,
                    FapiDefaults.DEFAULT_DISCOVERY_TIMEOUT_MS);
                
                if (result != null && result.getServices() != null
                    && !result.getServices().isEmpty()) {
                    System.out.println("Connected to " + host + ":" + port);
                    return new FapiClient(node, result.getPeerId(),
                        result.getServices().get(0).getId(), 30, settings);
                } else {
                    System.out.println("Failed to discover services at " + input);
                }
            } catch (Exception e) {
                System.out.println("Connection error: " + e.getMessage());
            }
        }
    }
    
    private static void showMainMenu() {
        Menu menu = new Menu("FAPI Client", StartFapiClient::cleanup);
        menu.add("Basic APIs", StartFapiClient::basicApi);
        menu.add("Wallet APIs", StartFapiClient::walletApi);
        menu.add("Entity By ID", StartFapiClient::entityByIds);
        menu.add("Entity Search", StartFapiClient::entitySearch);
        menu.add("General Query", StartFapiClient::generalQuery);
        menu.add("Buy Service", StartFapiClient::buyService);
        menu.add("Switch Service", StartFapiClient::switchService);  // 新增：切换服务
        menu.add("Settings", StartFapiClient::settings);
        
        menu.showAndSelect(br);
    }
    
    /**
     * 新增：切换FAPI服务
     */
    private static void switchService() {
        System.out.println("Discovering available FAPI services...");
        List<Service> providers = FapiServiceDiscovery.fetchFapiProviders(fapiClient);
        
        if (providers == null || providers.isEmpty()) {
            System.out.println("No FAPI services found on chain.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        Service selected = selectService(providers, br);
        if (selected != null) {
            FapiClient newClient = connectToService(fudpNode, selected, br);
            if (newClient != null) {
                fapiClient = newClient;
                System.out.println("Switched to: " + selected.getStdName());
            }
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void cleanup() {
        if (fudpNode != null) {
            fudpNode.stop();
        }
        if (settings != null) {
            try {
                settings.close();
            } catch (Exception e) {
                log.error("Error closing settings", e);
            }
        }
    }
    
    // ... 其他方法参考 StartFapiClientOld 实现 ...
}
```

**全功能服务启动类**（暂时不实现DISK）：

```java
/**
 * 全功能服务启动类（BASE组件）
 * 
 * 注意：DISK组件将在后续专门任务中实现
 */
public class StartFullService {
    public static void main(String[] args) {
        Menu.welcome("Freeverse Full Service");
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        
        // 启动包含BASE组件的服务（暂不包含DISK）
        ServiceBootstrapConfig config = ServiceBootstrapConfig.forFullService()
            .setBr(br);
        
        FapiServer server = ServiceBootstrap.bootstrap(config);
        if (server == null) return;
        
        server.showMainMenu();
    }
}
```

## 6. 依赖管理

### 6.1 服务依赖配置

```java
/**
 * 服务依赖定义
 */
public class ServiceDependency {
    private final String serviceType;
    private final ServiceLayer layer;
    private final List<String> requiredServices;
    private final boolean requiresFudpNode;
    
    public enum ServiceLayer {
        EXTERNAL,      // 外部服务（ES, REDIS, NASA_RPC）
        FRAMEWORK,     // 框架层（FAPI）
        BASE,          // 基础服务层（BASE）
        BUSINESS       // 业务服务层（DISK, MAP等）
    }
}

/**
 * 依赖配置注册表
 */
public class DependencyRegistry {
    private static final Map<String, ServiceDependency> DEPENDENCIES = Map.ofEntries(
        // 外部服务（无依赖）
        entry("ES", new ServiceDependency("ES", EXTERNAL, List.of(), false)),
        entry("NASA_RPC", new ServiceDependency("NASA_RPC", EXTERNAL, List.of(), false)),
        
        // 框架层（需要FUDP）
        entry("FAPI", new ServiceDependency("FAPI", FRAMEWORK, List.of(), true)),
        
        // 基础服务层（依赖ES，作为FAPI组件运行）
        entry("BASE", new ServiceDependency("BASE", BASE, List.of("ES"), true)),
        
        // 业务服务层（依赖BASE，作为FAPI组件运行）
        entry("DISK", new ServiceDependency("DISK", BUSINESS, List.of("BASE"), true)),
        entry("MAP", new ServiceDependency("MAP", BUSINESS, List.of("BASE"), true)),
        entry("TALK", new ServiceDependency("TALK", BUSINESS, List.of("BASE"), true)),
        entry("SWAP", new ServiceDependency("SWAP", BUSINESS, List.of("BASE"), true))
    );
    
    /**
     * 解析依赖并返回初始化顺序
     */
    public static List<String> resolveOrder(String[] types) {
        // 拓扑排序，确保依赖在前
        // EXTERNAL → FRAMEWORK → BASE → BUSINESS
        List<String> ordered = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        for (String type : types) {
            resolveRecursive(type, ordered, visited);
        }
        
        return ordered;
    }
    
    private static void resolveRecursive(String type, List<String> ordered, Set<String> visited) {
        if (visited.contains(type)) return;
        visited.add(type);
        
        ServiceDependency dep = DEPENDENCIES.get(type);
        if (dep != null) {
            for (String required : dep.getRequiredServices()) {
                resolveRecursive(required, ordered, visited);
            }
        }
        
        ordered.add(type);
    }
}
```

### 6.2 模块初始化流程

```java
/**
 * 模块初始化器链
 */
public class ModuleInitializerChain {
    private final List<ModuleInitializer> initializers = List.of(
        new ExternalServiceInitializer(),  // ES, NASA_RPC
        new FudpNodeInitializer(),         // FUDP Node
        new ManagerInitializer(),          // CASH, BALANCE等Manager
        new ComponentInitializer()         // BASE, DISK等组件
    );
    
    public void initializeAll(List<Module> modules, Settings settings) {
        for (Module module : modules) {
            for (ModuleInitializer init : initializers) {
                if (init.canHandle(module)) {
                    init.initialize(module, settings);
                    break;
                }
            }
        }
    }
}
```

## 7. 安全设计

### 7.1 安全机制（FUDP层已实现）

FAPI服务的安全机制完全在FUDP传输层完成，应用层无需额外处理：

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         FUDP层安全机制                                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  1. 身份认证                                                            │
│     ├─ HELLO/HELLO_ACK 交换公钥                                        │
│     ├─ ECDH 生成共享密钥                                               │
│     └─ 所有后续通信使用此密钥加密签名                                   │
│                                                                         │
│  2. 防重放保护 (ReplayProtection类)                                     │
│     ├─ 滑动窗口机制（window size = 1024）                              │
│     ├─ 时间戳检查（±5秒容差）                                          │
│     ├─ Session Epoch 检测对端重启                                      │
│     └─ 加密保护，攻击者无法修改或重放                                   │
│                                                                         │
│  3. 消息去重                                                            │
│     └─ 基于 peerId + messageId 的复合键去重                            │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘

客户端                                      服务端
  │                                           │
  │  1. HELLO (client pubkey)                 │
  │──────────────────────────────────────────►│
  │                                           │
  │  2. HELLO_ACK (server pubkey)             │
  │◄──────────────────────────────────────────│
  │                                           │
  │  3. 双方使用ECDH生成共享密钥              │
  │     后续所有通信使用此密钥加密            │
  │                                           │
  │  4. REQUEST (加密 + packetNumber)         │
  │──────────────────────────────────────────►│
  │                                           │ 5. ReplayProtection检查
  │                                           │    解密数据
  │                                           │    peerId = 客户端FID
  │  6. RESPONSE (加密)                       │
  │◄──────────────────────────────────────────│
```

**FAPI层信任模型**：
- ✅ **认证**：`peerId` 由FUDP层提供，已通过公私钥验证
- ✅ **防重放**：FUDP层 `ReplayProtection` 已处理，无需nonce
- ✅ **加密**：所有数据在传输层已加密，应用层处理明文
- ✅ **去重**：FUDP层消息去重机制已处理

### 7.2 限流机制

**分层限流设计**：FUDP层和FAPI层各有职责，互不冲突。

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         限流机制分层                                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  FUDP层（传输级保护）- 已实现                                            │
│  ├─ 公钥响应限流：每2秒最多3次（防DDoS）                                │
│  ├─ 连接数限制                                                          │
│  └─ 拥塞控制                                                            │
│                                                                         │
│  FAPI层（业务级限流）- 需实现                                            │
│  ├─ 全局QPS限制                                                         │
│  ├─ 单用户QPS限制                                                       │
│  ├─ 按接口限流                                                          │
│  └─ 突发容量控制                                                        │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

```java
/**
 * FAPI层限流配置
 */
public class RateLimitConfig {
    private int globalQps = 1000;           // 全局QPS限制
    private int perPeerQps = 100;           // 单用户QPS限制
    private int burstSize = 50;             // 突发容量
    private Map<String, Integer> apiLimits; // 特定API限流配置
    
    // 默认API限流配置
    private static final Map<String, Integer> DEFAULT_API_LIMITS = Map.of(
        "disk.put", 10,           // 上传限制10 QPS
        "disk.batchPut", 5,       // 批量上传限制5 QPS
        "base.search", 50         // 搜索限制50 QPS
    );
}

/**
 * 限流器实现（令牌桶算法）
 */
public class RateLimiter {
    private final RateLimitConfig config;
    private final Map<String, TokenBucket> peerBuckets = new ConcurrentHashMap<>();
    private final TokenBucket globalBucket;
    
    public RateLimiter(RateLimitConfig config) {
        this.config = config;
        this.globalBucket = new TokenBucket(config.getGlobalQps(), config.getBurstSize());
    }
    
    /**
     * 检查是否允许请求
     * @return true=允许, false=被限流
     */
    public boolean tryAcquire(String peerId, String api) {
        // 1. 检查全局限流
        if (!globalBucket.tryAcquire()) {
            return false;
        }
        
        // 2. 检查用户限流
        TokenBucket peerBucket = peerBuckets.computeIfAbsent(peerId, 
            k -> new TokenBucket(config.getPerPeerQps(), config.getBurstSize()));
        if (!peerBucket.tryAcquire()) {
            return false;
        }
        
        // 3. 检查API限流（如果配置了）
        Integer apiLimit = config.getApiLimits().get(api);
        if (apiLimit != null) {
            // API级别限流逻辑
        }
        
        return true;
    }
}
```

### 7.3 超时控制

**动态超时设计**：根据操作类型和数据大小动态计算超时，支持大文件传输。

```java
/**
 * 超时配置
 * 设计原则：不设硬性上限，根据数据量动态计算
 */
public class TimeoutConfig {
    // ==================== 基础超时 ====================
    private long defaultTimeout = 30_000;          // 默认超时30秒
    private long esQueryTimeout = 10_000;          // ES查询超时10秒
    private long componentInitTimeout = 60_000;    // 组件初始化超时60秒
    
    // ==================== 文件传输超时（动态计算）====================
    private long fileBaseTimeout = 60_000;         // 文件传输基础超时60秒
    private long filePerMbTimeout = 10_000;        // 每MB额外10秒
    private long fileMinTimeout = 60_000;          // 最小超时60秒
    // 注意：不设置maxTimeout，支持任意大小文件（10GB+）
    
    /**
     * 根据文件大小计算超时时间
     * 
     * 示例：
     * - 10MB → 60s + 10*10s = 160秒
     * - 100MB → 60s + 100*10s = 17分钟
     * - 1GB → 60s + 1024*10s ≈ 3小时
     * - 10GB → 60s + 10240*10s ≈ 29小时
     */
    public long calculateFileTimeout(long fileSizeBytes) {
        long sizeMb = Math.max(1, fileSizeBytes / (1024 * 1024));
        long timeout = fileBaseTimeout + sizeMb * filePerMbTimeout;
        return Math.max(fileMinTimeout, timeout);
    }
}
```

**超时场景汇总**：

| 场景 | 超时计算 | 示例 |
|-----|---------|------|
| 普通API请求 | 固定30秒 | base.search |
| ES查询 | 固定10秒 | - |
| 小文件上传（<1MB） | 固定30秒 | disk.put |
| 大文件上传 | 60s + 10s/MB | 1GB → 3小时 |
| 组件初始化 | 固定60秒 | - |

### 7.4 断点续传

**大文件断点续传机制**：

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         断点续传流程                                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  客户端                              服务端                              │
│    │                                   │                                │
│    │  1. disk.prepareUpload            │                                │
│    │  { size: 1GB, hash: "sha256:..." }│                                │
│    │──────────────────────────────────►│                                │
│    │                                   │                                │
│    │  2. { uploadToken, chunkSize,     │                                │
│    │       totalChunks: 32768 }        │                                │
│    │◄──────────────────────────────────│                                │
│    │                                   │                                │
│    │  3. 开始分块上传                   │                                │
│    │  chunk[0], chunk[1], ...          │                                │
│    │──────────────────────────────────►│ 4. 记录已接收分块               │
│    │                                   │                                │
│    │  ══════ 网络中断 ══════           │                                │
│    │                                   │                                │
│    │  5. disk.resumeUpload             │                                │
│    │  { uploadToken }                  │                                │
│    │──────────────────────────────────►│                                │
│    │                                   │                                │
│    │  6. { uploadedChunks: [0,1,2],    │                                │
│    │       nextChunk: 3 }              │                                │
│    │◄──────────────────────────────────│                                │
│    │                                   │                                │
│    │  7. 从chunk[3]继续上传            │                                │
│    │  chunk[3], chunk[4], ...          │                                │
│    │──────────────────────────────────►│                                │
│    │                                   │                                │
│    │  8. disk.confirmUpload            │                                │
│    │──────────────────────────────────►│ 9. 校验hash，合并分块          │
│    │                                   │                                │
│    │  10. { did, size }                │                                │
│    │◄──────────────────────────────────│                                │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

```java
/**
 * 上传状态（用于断点续传）
 */
public class UploadState {
    private String uploadToken;           // 上传令牌
    private String peerId;                // 上传者FID
    private String expectedHash;          // 期望的文件hash
    private long totalSize;               // 总大小
    private int chunkSize;                // 分块大小
    private long totalChunks;             // 总分块数
    private BitSet receivedChunks;        // 已接收分块位图
    private long createTime;              // 创建时间
    private long lastActiveTime;          // 最后活跃时间
    private long timeout;                 // 超时时间（动态计算）
    private String tempDir;               // 临时存储目录
    
    /**
     * 获取已上传的分块列表
     */
    public List<Integer> getUploadedChunks() {
        List<Integer> chunks = new ArrayList<>();
        for (int i = receivedChunks.nextSetBit(0); i >= 0; i = receivedChunks.nextSetBit(i + 1)) {
            chunks.add(i);
        }
        return chunks;
    }
    
    /**
     * 获取下一个需要上传的分块
     */
    public int getNextChunk() {
        return receivedChunks.nextClearBit(0);
    }
    
    /**
     * 标记分块已接收
     */
    public void markChunkReceived(int chunkIndex) {
        receivedChunks.set(chunkIndex);
        lastActiveTime = System.currentTimeMillis();
    }
    
    /**
     * 检查是否已完成
     */
    public boolean isComplete() {
        return receivedChunks.cardinality() == totalChunks;
    }
    
    /**
     * 检查是否超时
     */
    public boolean isExpired() {
        return System.currentTimeMillis() - lastActiveTime > timeout;
    }
}
```

### 7.5 输入验证

**注意**：防重放已在FUDP层实现，FAPI层无需处理：
- FUDP使用 `ReplayProtection` 类（滑动窗口 + 时间戳）
- 所有请求都是端到端加密的，攻击者无法重放
- 消息级别使用 `peerId + messageId` 去重

```java
/**
 * 请求参数验证器
 * 注意：防重放由FUDP层处理，此处只需验证业务参数
 */
public class RequestValidator {
    private static final int MAX_IDS_COUNT = 100;
    private static final int MAX_QUERY_DEPTH = 5;
    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    
    /**
     * 验证通用请求参数
     */
    public static ValidationResult validate(FapiRequest request) {
        // 验证API名称格式
        if (request.getApi() == null || !request.getApi().contains(".")) {
            return ValidationResult.fail("Invalid api format, expected: component.method");
        }
        
        // 验证查询类请求必须有fcdsl
        String method = request.getMethodName();
        if (isQueryMethod(method) && request.getFcdsl() == null) {
            return ValidationResult.fail("fcdsl is required for query API");
        }
        
        // 验证操作类请求必须有params
        if (isOperationMethod(method) && request.getParams() == null) {
            return ValidationResult.fail("params is required for operation API");
        }
        
        return ValidationResult.ok();
    }
    
    /**
     * 验证IDs查询参数
     */
    public static ValidationResult validateIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return ValidationResult.fail("IDs cannot be empty");
        }
        if (ids.size() > MAX_IDS_COUNT) {
            return ValidationResult.fail("Too many IDs, max: " + MAX_IDS_COUNT);
        }
        return ValidationResult.ok();
    }
    
    /**
     * 验证Fcdsl查询复杂度
     */
    public static ValidationResult validateFcdsl(Fcdsl fcdsl) {
        // 检查嵌套深度
        // 检查查询大小（size不能过大）
        // 防止过于复杂的查询消耗资源
        if (fcdsl.getSize() != null && fcdsl.getSize() > 1000) {
            return ValidationResult.fail("Query size too large, max: 1000");
        }
        return ValidationResult.ok();
    }
    
    private static boolean isQueryMethod(String method) {
        return Set.of("search", "getByIds", "list").contains(method);
    }
    
    private static boolean isOperationMethod(String method) {
        return Set.of("put", "get", "check", "carve", "prepareUpload", "prepareDownload").contains(method);
    }
}
```

### 7.6 经济安全（信用控制）

**计费规则**（复用现有实现）：
- 统一使用链上发布的 `Service.pricePerKB` 计费
- BASE请求按响应数据大小计费
- DISK存储按文件大小计费
- 计费逻辑已在 `FapiServer.buildAdvertiseData()` 和 `BalanceManager` 中实现

```java
/**
 * 信用控制流程（集成BalanceManager）
 * 
 * 错误码说明：
 * - 402 PAYMENT_REQUIRED: 余额不足
 * - 403 FORBIDDEN: 信用额度超限
 */
public class CreditController {
    
    /**
     * 请求前检查
     * 在路由到组件之前检查用户信用额度
     */
    public CreditCheckResult checkBeforeRequest(String peerId) {
        BalanceManager.BalanceView balance = balanceManager.getBalance(peerId);
        
        if (balance == null) {
            // 新用户，使用默认信用额度
            return CreditCheckResult.allow(defaultCreditLimit);
        }
        
        if (balance.getBalance() < -balance.getCreditLimit()) {
            // 已超出信用额度，返回403
            return CreditCheckResult.deny(FapiCode.FORBIDDEN, "Credit limit exceeded");
        }
        
        if (balance.getBalance() < 0) {
            // 余额为负但在信用额度内，可继续但警告
            log.warn("User {} balance is negative: {}", peerId, balance.getBalance());
        }
        
        return CreditCheckResult.allow(balance.getCreditLimit());
    }
}
```

### 7.7 审计日志

```java
/**
 * 审计日志记录
 * 记录所有修改操作，便于追踪和审计
 */
public class AuditLogger {
    
    /**
     * 记录修改操作
     */
    public void logModification(String peerId, String api, Object params, FapiResponse response) {
        AuditRecord record = new AuditRecord();
        record.setTimestamp(System.currentTimeMillis());
        record.setPeerId(peerId);
        record.setApi(api);
        record.setParams(sanitize(params)); // 脱敏处理
        record.setSuccess(response.getCode() == 0);
        record.setResultCode(response.getCode());
        
        // 写入审计日志
        auditStore.append(record);
    }
    
    /**
     * 需要审计的操作
     */
    private static final Set<String> AUDITABLE_APIS = Set.of(
        "disk.put",
        "disk.carve"
        // 注：DISK采用开放存储设计，无delete接口，过期数据自动清理
    );
}
```

## 8. 监控和运维

### 8.1 健康检查

```java
/**
 * 健康检查端点
 * 通过 base.health API 暴露
 */
public class HealthChecker {
    
    /**
     * 执行健康检查
     */
    public HealthStatus check() {
        HealthStatus status = new HealthStatus();
        status.setTimestamp(System.currentTimeMillis());
        
        // 检查各组件状态
        Map<String, ComponentHealth> componentHealth = new HashMap<>();
        for (FapiComponent component : server.getComponents()) {
            ComponentHealth health = new ComponentHealth();
            health.setName(component.getName());
            health.setState(component.getState().name());
            health.setHealthy(component.isHealthy());
            componentHealth.put(component.getName(), health);
        }
        status.setComponents(componentHealth);
        
        // 检查外部依赖
        status.setEsConnected(checkEsConnection());
        status.setRpcConnected(checkRpcConnection());
        
        // 检查FUDP节点
        status.setFudpRunning(fudpNode.isRunning());
        status.setConnectedPeers(fudpNode.listPeers().size());
        
        // 汇总状态
        status.setOverallHealthy(
            componentHealth.values().stream().allMatch(ComponentHealth::isHealthy)
            && status.isEsConnected()
        );
        
        return status;
    }
}
```

### 8.2 性能指标

```java
/**
 * 性能指标收集器
 */
public class MetricsCollector {
    
    // 请求计数
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong successRequests = new AtomicLong();
    private final AtomicLong failedRequests = new AtomicLong();
    
    // 响应时间统计（使用滑动窗口）
    private final SlidingWindowHistogram responseTime = new SlidingWindowHistogram(60_000);
    
    // 每组件统计
    private final ConcurrentMap<String, ComponentMetrics> componentMetrics = new ConcurrentHashMap<>();
    
    /**
     * 记录请求
     */
    public void recordRequest(String component, long durationMs, boolean success) {
        totalRequests.incrementAndGet();
        if (success) {
            successRequests.incrementAndGet();
        } else {
            failedRequests.incrementAndGet();
        }
        
        responseTime.record(durationMs);
        
        componentMetrics.computeIfAbsent(component, k -> new ComponentMetrics())
            .record(durationMs, success);
    }
    
    /**
     * 获取性能报告
     */
    public MetricsReport getReport() {
        MetricsReport report = new MetricsReport();
        report.setTotalRequests(totalRequests.get());
        report.setSuccessRate(
            totalRequests.get() > 0 
            ? (double) successRequests.get() / totalRequests.get() * 100 
            : 100.0
        );
        report.setP50ResponseTime(responseTime.getPercentile(50));
        report.setP95ResponseTime(responseTime.getPercentile(95));
        report.setP99ResponseTime(responseTime.getPercentile(99));
        report.setComponentMetrics(new HashMap<>(componentMetrics));
        return report;
    }
}
```

### 8.3 日志规范

```
日志级别使用规范：

DEBUG:
  - 请求/响应详情（开发调试用）
  - 组件内部处理步骤

INFO:
  - 服务启动/停止
  - 组件生命周期事件
  - 重要业务操作摘要
  
WARN:
  - 性能降级（响应时间超阈值）
  - 重试操作
  - 信用额度接近上限
  - 可恢复的错误
  
ERROR:
  - 请求处理失败
  - 组件异常
  - 外部依赖连接失败
  - 不可恢复的错误

日志格式（支持分布式追踪）：
[timestamp] [level] [component] [traceId] [peerId] [api] message

字段说明：
- traceId: 请求追踪ID（使用FapiRequest.id），用于跨组件调用追踪
- peerId: 请求方FID
- api: API名称

示例：
2024-12-26 10:30:45 INFO  [DISK] [req-abc123] [FEk41...] [disk.put] File uploaded (1024 bytes)
2024-12-26 10:30:46 WARN  [BALANCE] [req-abc123] [FGw2...] [-] Credit usage at 80%
2024-12-26 10:30:47 ERROR [BASE] [req-def456] [FTx3...] [base.search] ES query failed: connection timeout
```

## 9. 测试策略

### 9.1 单元测试

```java
/**
 * 组件单元测试示例
 */
public class BaseComponentTest {
    
    private BaseComponent component;
    private ElasticsearchClient mockEsClient;
    private FapiServer mockServer;
    
    @BeforeEach
    void setUp() {
        mockEsClient = mock(ElasticsearchClient.class);
        mockServer = mock(FapiServer.class);
        when(mockServer.getSettings().getClient(ServiceType.ES)).thenReturn(mockEsClient);
        
        component = new BaseComponent();
        component.initialize(mockServer);
    }
    
    @Test
    void testGetByIds_Success() {
        // Given
        List<String> ids = List.of("FEk41...", "FGw2...");
        when(mockEsClient.mgetById("freer", ids)).thenReturn(Map.of(
            "FEk41...", new Freer(),
            "FGw2...", new Freer()
        ));
        
        FapiRequest request = new FapiRequest();
        request.setId("req-test-001");
        request.setApi("base.getByIds");
        // 查询类API使用fcdsl而非params
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEntity("freer");
        fcdsl.setIds(ids);
        request.setFcdsl(fcdsl);
        
        // When
        FapiResponse response = component.handleRequest(request, "testPeer");
        
        // Then
        assertEquals(0, response.getCode());
        assertEquals("req-test-001", response.getRequestId());
        assertNotNull(response.getData());
    }
    
    @Test
    void testGetByIds_TooManyIds() {
        // Given: 超过限制的ID数量
        List<String> ids = IntStream.range(0, 200)
            .mapToObj(i -> "FID" + i)
            .toList();
        
        FapiRequest request = new FapiRequest();
        request.setId("req-test-002");
        request.setApi("base.getByIds");
        // 查询类API使用fcdsl
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEntity("freer");
        fcdsl.setIds(ids);
        request.setFcdsl(fcdsl);
        
        // When
        FapiResponse response = component.handleRequest(request, "testPeer");
        
        // Then
        assertEquals(FapiErrorCode.BAD_REQUEST.getCode(), response.getCode());
    }
}
```

### 9.2 集成测试

```java
/**
 * 组件间集成测试
 */
public class ComponentIntegrationTest {
    
    private FapiServer server;
    private FudpNode clientNode;
    
    @BeforeAll
    static void startServer() {
        // 启动完整的FAPI服务器
        ServiceBootstrapConfig config = ServiceBootstrapConfig.forFullService();
        server = ServiceBootstrap.bootstrap(config);
    }
    
    @Test
    void testDiskComponentUsesBase() {
        // DISK组件应该能够通过BASE组件查询链上数据
        
        // 1. 上传文件（操作类API使用params）
        FapiRequest putRequest = new FapiRequest();
        putRequest.setId("req-int-001");
        putRequest.setApi("disk.put");
        putRequest.setParams(Map.of(
            "content", Base64.getEncoder().encodeToString("Hello World".getBytes())
        ));
        
        FapiResponse putResponse = server.routeRequest(putRequest, "testPeer");
        assertEquals(0, putResponse.getCode());
        String did = ((Map<String, Object>) putResponse.getData()).get("did").toString();
        
        // 2. 通过disk.list查询文件列表（查询类API使用fcdsl）
        FapiRequest listRequest = new FapiRequest();
        listRequest.setId("req-int-002");
        listRequest.setApi("disk.list");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addFilter("did", did);
        listRequest.setFcdsl(fcdsl);
        
        FapiResponse listResponse = server.routeRequest(listRequest, "testPeer");
        assertEquals(0, listResponse.getCode());
    }
    
    @Test
    void testFudpEndToEnd() throws Exception {
        // 端到端FUDP通信测试
        
        // 创建客户端节点
        byte[] clientKey = KeyTools.genNewPrivateKey();
        NodeConfig clientConfig = new NodeConfig().setPort(0); // 随机端口
        clientNode = new FudpNode(clientKey, clientConfig);
        clientNode.start();
        
        // 添加服务器为peer
        clientNode.addPeer(server.getFudpNode().getLocalFid(), 
            "127.0.0.1", server.getPort());
        
        // 发送请求（查询类使用fcdsl）
        Map<String, Object> requestMap = new LinkedHashMap<>();
        requestMap.put("id", "req-fudp-001");
        requestMap.put("api", "base.getByIds");
        requestMap.put("fcdsl", Map.of("entity", "block", "ids", List.of("000000...")));
        byte[] requestData = JsonUtils.toJson(requestMap).getBytes();
        
        CompletableFuture<ResponseMessage> future = clientNode.request(
            server.getFudpNode().getLocalFid(),
            service.getId(),
            requestData
        );
        
        ResponseMessage response = future.get(5, TimeUnit.SECONDS);
        assertEquals(0, response.getStatusCode());
    }
    
    @AfterAll
    static void stopServer() {
        if (server != null) server.stop();
        if (clientNode != null) clientNode.stop();
    }
}
```

### 9.3 性能测试

```java
/**
 * 性能测试
 */
public class PerformanceTest {
    
    @Test
    void testConcurrentRequests() throws Exception {
        int concurrency = 100;
        int requestsPerThread = 100;
        
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch latch = new CountDownLatch(concurrency);
        AtomicLong totalTime = new AtomicLong();
        AtomicInteger successCount = new AtomicInteger();
        
        for (int i = 0; i < concurrency; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        long start = System.currentTimeMillis();
                        FapiResponse response = sendRequest();
                        long duration = System.currentTimeMillis() - start;
                        
                        totalTime.addAndGet(duration);
                        if (response.getCode() == 0) {
                            successCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        int totalRequests = concurrency * requestsPerThread;
        double avgTime = (double) totalTime.get() / totalRequests;
        double successRate = (double) successCount.get() / totalRequests * 100;
        
        System.out.println("Total requests: " + totalRequests);
        System.out.println("Success rate: " + successRate + "%");
        System.out.println("Average response time: " + avgTime + "ms");
        
        // 断言性能要求
        assertTrue(successRate >= 99.0, "Success rate should be >= 99%");
        assertTrue(avgTime < 100, "Average response time should be < 100ms");
    }
}
```

## 10. 实施计划

### 10.1 阶段划分

```
阶段1: 核心框架 (2周)
├─ 1.1 定义FapiComponent接口（含生命周期、异步支持）
├─ 1.2 实现FapiServer框架（含余额集成、组件管理）
├─ 1.3 实现组件注册和请求路由
├─ 1.4 更新FapiRequest（兼容Fcdsl）
└─ 1.5 定义FapiErrorCode统一错误码

阶段2: BASE组件 (1.5周)
├─ 2.1 实现BaseComponent（继承AbstractFapiComponent）
├─ 2.2 迁移现有EndpointHandler逻辑
├─ 2.3 实现getByIds/search通用接口
└─ 2.4 单元测试

阶段3: 启动框架与客户端 (2周)
├─ 3.1 实现ServiceBootstrapConfig
├─ 3.2 实现ServiceBootstrap
├─ 3.3 实现依赖解析器
├─ 3.4 创建StartFapiServer（保留StartFapiServerOld）
├─ 3.5 创建StartFapiClient（保留StartFapiClientOld）
├─ 3.6 实现FapiDefaults（默认服务端点配置）
└─ 3.7 实现FapiServiceDiscovery（服务发现机制）

阶段4: 安全与监控 (1周)
├─ 4.1 实现RequestValidator输入验证
├─ 4.2 实现HealthChecker健康检查
├─ 4.3 实现MetricsCollector性能指标
└─ 4.4 实现AuditLogger审计日志

阶段5: 测试与文档 (1周)
├─ 5.1 补充单元测试（覆盖率≥80%）
├─ 5.2 端到端集成测试
├─ 5.3 性能测试
└─ 5.4 更新API文档

总计: 约7.5周（不含DISK）

---
后续任务（单独安排）:
├─ DISK组件实现（含大文件传输）
├─ 其他业务组件框架（MAP、TALK等）
└─ 组件间集成测试
```

### 10.2 详细任务

#### 阶段1: 核心框架

| 任务 | 说明 | 产出 |
|-----|------|-----|
| 1.1 定义接口 | FapiComponent含生命周期、异步支持 | FapiComponent.java |
| 1.2 抽象基类 | AbstractFapiComponent | 抽象基类 |
| 1.3 FapiServer | 含余额集成、类型安全组件获取 | FapiServer.java |
| 1.4 组件注册 | ComponentRegistry | 组件管理 |
| 1.5 请求路由 | 支持新旧格式、异步路由 | 路由逻辑 |
| 1.6 错误码 | FapiErrorCode统一定义 | 错误码枚举 |

#### 阶段2: BASE组件

| 任务 | 说明 | 产出 |
|-----|------|-----|
| 2.1 BaseComponent | 继承AbstractFapiComponent | BaseComponent.java |
| 2.2 迁移Handler | 复用现有EndpointHandler逻辑 | 迁移代码 |
| 2.3 getByIds | 通用ID查询（含验证） | 查询实现 |
| 2.4 search | 通用搜索（含验证） | 搜索实现 |
| 2.5 单元测试 | 覆盖主要场景 | 测试用例 |

#### 阶段3: 启动框架与客户端

| 任务 | 说明 | 产出 |
|-----|------|-----|
| 3.1 配置类 | ServiceBootstrapConfig | 配置类 |
| 3.2 启动器 | ServiceBootstrap | 启动逻辑 |
| 3.3 依赖解析 | DependencyRegistry拓扑排序 | 依赖管理 |
| 3.4 服务端启动类 | 创建StartFapiServer，保留StartFapiServerOld | StartFapiServer.java |
| 3.5 客户端启动类 | 创建StartFapiClient，保留StartFapiClientOld | StartFapiClient.java |
| 3.6 默认配置 | FapiDefaults（默认服务端点127.0.0.1:8500, freecash.info:8500） | FapiDefaults.java |
| 3.7 服务发现 | FapiServiceDiscovery（自动发现与链上服务查询） | FapiServiceDiscovery.java |

#### 阶段4: 安全与监控

| 任务 | 说明 | 产出 |
|-----|------|-----|
| 4.1 输入验证 | RequestValidator | 验证器 |
| 4.2 健康检查 | HealthChecker + base.health | 健康检查 |
| 4.3 性能指标 | MetricsCollector | 指标收集 |
| 4.4 审计日志 | AuditLogger | 审计模块 |

#### 阶段5: 测试与文档

| 任务 | 说明 | 产出 |
|-----|------|-----|
| 5.1 单元测试 | 覆盖率≥80% | 测试代码 |
| 5.2 集成测试 | 端到端FUDP测试 | 测试代码 |
| 5.3 性能测试 | 并发压力测试 | 测试报告 |
| 5.4 API文档 | 更新接口文档 | 文档 |

#### 后续任务: 业务组件（单独安排）

| 任务 | 说明 | 产出 |
|-----|------|-----|
| DISK组件 | 含大文件传输支持（在专门任务中实现） | DiskComponent.java |
| 大文件机制 | 集成FileHandler | prepareUpload/Download |
| 其他组件 | MAP/TALK等框架 | 组件骨架 |
| 集成测试 | 组件间调用测试 | 测试用例 |

### 10.3 风险控制

| 风险 | 影响 | 缓解措施 |
|-----|------|---------|
| 接口设计不当 | 后续扩展困难 | 充分设计评审，参考现有AbstractEndpointHandler |
| 性能问题 | 服务响应慢 | 增加性能测试，支持异步处理 |
| 端口冲突 | 服务启动失败 | FudpNode启动时自动检测 |
| 向后兼容 | 现有客户端无法使用 | FapiRequest支持新旧两种格式 |
| 大文件传输 | 内存溢出 | 使用FileHandler流式传输 |
| 组件依赖循环 | 初始化死锁 | DependencyRegistry拓扑排序 |

## 11. 附录

### 11.1 文件变更清单

**新增文件**：
```
FC-JDK/src/main/java/
├── fapi/
│   ├── FapiComponent.java (接口)
│   ├── AbstractFapiComponent.java (抽象基类)
│   ├── FapiErrorCode.java (错误码枚举)
│   ├── FapiDefaults.java (默认配置：服务端点、超时等)
│   ├── FapiServiceDiscovery.java (服务发现机制)
│   ├── ComponentRegistry.java
│   ├── StartFapiServer.java (新服务端启动类)
│   ├── StartFapiClient.java (新客户端启动类)
│   ├── components/
│   │   ├── BaseComponent.java
│   │   └── ... (DiskComponent等在后续任务实现)
│   ├── validation/
│   │   └── RequestValidator.java
│   └── metrics/
│       ├── HealthChecker.java
│       ├── MetricsCollector.java
│       └── AuditLogger.java
├── config/
│   ├── ServiceBootstrap.java
│   ├── ServiceBootstrapConfig.java
│   └── DependencyRegistry.java
```

**保留文件（暂时保留，逐步废弃）**：
```
FC-JDK/src/main/java/fapi/
├── StartFapiServerOld.java (原有服务端启动类)
└── StartFapiClientOld.java (原有客户端启动类)
```

**修改文件**：
```
- fapi/service/FapiServer.java (增强：余额集成、类型安全组件、生命周期管理)
- fapi/message/FapiRequest.java (增强：兼容Fcdsl、新旧格式支持)
- fapi/message/FapiResponse.java (保持现有结构)
- fapi/StartFapiServer.java (简化，使用ServiceBootstrap)
```

**保留复用**：
```
- fapi/handler/endpoint/*.java (现有EndpointHandler逻辑复用)
- fapi/FapiEndpoint.java (端点枚举)
- managers/BalanceManager.java (余额管理)
```

### 11.2 API设计规范

**命名规则**：
```
{component}.{method}
```

#### BASE组件API列表

**通用查询类API（使用fcdsl）**：
| API | 说明 | 参数 |
|-----|------|------|
| `base.getByIds` | 根据ID列表获取实体 | fcdsl.entity, fcdsl.ids |
| `base.search` | 搜索实体 | fcdsl.entity, fcdsl.filter, fcdsl.size等 |
| `base.totals` | 获取所有索引文档数量 | 无 |
| `base.health` | 健康检查 | 无 |

**余额查询类API**：
| API | 说明 | 参数 |
|-----|------|------|
| `base.balanceByIds` | 根据FID列表获取余额 | fcdsl.ids (FID列表) |
| `base.cashValid` | 获取有效UTXO | fcdsl + params (fid, amount, cd, msgSize, outputSize) |
| `base.getUtxo` | 获取UTXO（Utxo格式） | params.address, params.amount, params.cd |

**链信息类API（使用params）**：
| API | 说明 | 参数 |
|-----|------|------|
| `base.chainInfo` | 获取链信息 | params.height (可选) |
| `base.blockTimeHistory` | 获取出块时间历史 | params.startTime, params.endTime, params.count |
| `base.difficultyHistory` | 获取难度历史 | params.startTime, params.endTime, params.count |
| `base.hashRateHistory` | 获取算力历史 | params.startTime, params.endTime, params.count |

**内存池类API（使用fcdsl）**：
| API | 说明 | 参数 |
|-----|------|------|
| `base.unconfirmed` | 获取未确认交易信息 | fcdsl.ids (FID列表) |
| `base.unconfirmedCashes` | 获取未确认的Cash | fcdsl.ids (FID列表，可选) |

**交易操作类API（使用params）**：
| API | 说明 | 参数 |
|-----|------|------|
| `base.broadcastTx` | 广播交易 | params.rawTx |
| `base.decodeTx` | 解码交易 | params.rawTx |
| `base.estimateFee` | 估算交易费用 | params.nBlocks (可选) |

#### DISK组件API列表

**查询类API（使用fcdsl）**：
| API | 说明 | 参数 |
|-----|------|------|
| `disk.list` | 查询文件列表 | fcdsl.filter, fcdsl.size等 |

**操作类API（使用params）**：
| API | 说明 | 参数 |
|-----|------|------|
| `disk.put` | 上传小文件 | params.content (base64) |
| `disk.batchPut` | 批量上传小文件 | params.files |
| `disk.get` | 下载文件 | params.ids |
| `disk.check` | 检查文件是否存在 | params.ids |
| `disk.carve` | 永久保存文件 | params.did |
| `disk.prepareUpload` | 大文件上传准备 | params.size, params.hash |
| `disk.confirmUpload` | 大文件上传确认 | params.uploadToken |
| `disk.prepareDownload` | 大文件下载准备 | params.did |
| `disk.resumeUpload` | 断点续传恢复 | params.uploadToken |

#### 其他组件API

```
操作类API（使用params）:
```

**请求参数示例**：

```json
// ==================== 查询类请求（使用fcdsl）====================

// base.getByIds - 根据ID获取实体
{
  "id": "req-abc001",
  "api": "base.getByIds",
  "fcdsl": {
    "entity": "freer",
    "ids": ["FEk41...", "FGw2..."]
  }
}

// base.search - 搜索实体
{
  "id": "req-abc002",
  "api": "base.search",
  "fcdsl": {
    "entity": "tx",
    "filter": {"term": {"sender": "FEk41..."}},
    "size": 20,
    "sort": [{"timestamp": "desc"}]
  }
}

// disk.list - 查询文件列表
{
  "id": "req-abc003",
  "api": "disk.list",
  "fcdsl": {
    "filter": {"term": {"uploader": "FEk41..."}},
    "size": 50
  }
}

// ==================== 操作类请求（使用params）====================

// disk.put - 上传小文件
{
  "id": "req-abc004",
  "api": "disk.put",
  "params": {
    "content": "base64_encoded_data"
  }
}

// disk.get - 下载文件（支持批量）
{
  "id": "req-abc005",
  "api": "disk.get",
  "params": {
    "ids": ["did1", "did2"]
  }
}

// disk.check - 检查文件是否存在（支持批量）
{
  "id": "req-abc006",
  "api": "disk.check",
  "params": {
    "ids": ["did1", "did2", "did3"]
  }
}

// disk.prepareUpload - 大文件上传准备
{
  "id": "req-abc007",
  "api": "disk.prepareUpload",
  "params": {
    "size": 10737418240,
    "hash": "sha256:abcd1234..."
  }
}
// 响应: { "id": "resp-xyz001", "requestId": "req-abc007", "code": 0, 
//         "data": { "uploadToken": "token123", "chunkSize": 32768, "totalChunks": 327680, "timeout": 29500000 } }
// 客户端使用 FudpNode.sendFile(peerId, uploadToken, filePath) 上传

// disk.resumeUpload - 断点续传
{
  "id": "req-abc008",
  "api": "disk.resumeUpload",
  "params": {
    "uploadToken": "token123"
  }
}
// 响应: { "id": "resp-xyz002", "requestId": "req-abc008", "code": 0,
//         "data": { "uploadedChunks": [0,1,2,5,6], "nextChunk": 3, "totalChunks": 327680 } }

// disk.batchPut - 批量上传小文件
{
  "id": "req-abc009",
  "api": "disk.batchPut",
  "params": {
    "files": [
      { "content": "base64_data_1" },
      { "content": "base64_data_2" }
    ]
  }
}

// ==================== 系统请求 ====================

// base.health - 健康检查（无参数）
{
  "id": "req-abc010",
  "api": "base.health"
}
```

**响应格式示例**：

```json
// 成功响应 - 查询类
{
  "id": "resp-xyz100",
  "requestId": "req-abc001",
  "code": 0,
  "message": "Success",
  "data": [...],
  "got": 10,
  "total": 100,
  "last": ["cursor1", "cursor2"],
  "bestHeight": 1234567,
  "balance": 100000000,
  "balanceSeq": 42
}

// 成功响应 - 操作类
{
  "id": "resp-xyz101",
  "requestId": "req-abc004",
  "code": 0,
  "message": "Success",
  "data": {
    "did": "abcd1234567890...",
    "size": 1024
  },
  "bestHeight": 1234567,
  "balance": 99990000,
  "balanceSeq": 43
}

// 成功响应 - 批量检查
{
  "id": "resp-xyz102",
  "requestId": "req-abc006",
  "code": 0,
  "message": "Success",
  "data": {
    "did1": true,
    "did2": false,
    "did3": true
  }
}

// 错误响应
{
  "id": "resp-xyz103",
  "requestId": "req-abc002",
  "code": 400,
  "message": "Bad request: fcdsl.entity is required",
  "bestHeight": 1234567,
  "balance": 100000000,
  "balanceSeq": 42
}
```

### 11.3 配置示例

**服务启动配置（fapi_config.json）**：
```json
{
  "components": ["BASE", "DISK"],
  "fudpPort": 8500,
  "settings": {
    "storageDir": "/data/disk",
    "creditLimit": 10000
  },
  "modules": [
    {"type": "Service", "name": "ES"},
    {"type": "Service", "name": "NASA_RPC"},
    {"type": "Manager", "name": "BALANCE"}
  ]
}
```

**多用户端口配置**：
```json
{
  "users": [
    {"fid": "FEk41...", "port": 8500},
    {"fid": "FGw2...", "port": 8501},
    {"fid": "FTx3...", "port": 8502}
  ]
}
```

### 11.4 组件依赖关系图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         组件依赖关系                                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  外部服务                                                               │
│  ┌────────┐  ┌────────┐  ┌────────┐                                   │
│  │   ES   │  │  RPC   │  │ REDIS  │                                   │
│  └────┬───┘  └────┬───┘  └────┬───┘                                   │
│       │           │           │                                         │
│       └───────────┼───────────┘                                         │
│                   │                                                      │
│                   ▼                                                      │
│           ┌──────────────┐                                              │
│           │ BalanceManager│ ← 经济模型                                  │
│           └──────┬───────┘                                              │
│                  │                                                       │
│                  ▼                                                       │
│           ┌──────────────┐                                              │
│           │  FapiServer  │ ← 服务器核心                                 │
│           └──────┬───────┘                                              │
│                  │                                                       │
│       ┌──────────┼──────────┐                                           │
│       ▼          ▼          ▼                                           │
│  ┌────────┐ ┌────────┐ ┌────────┐                                      │
│  │  BASE  │ │  DISK  │ │  MAP   │  ← 组件                              │
│  └────────┘ └────┬───┘ └────┬───┘                                      │
│       ▲          │          │                                           │
│       └──────────┴──────────┘  DISK/MAP依赖BASE                        │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 11.5 认证与安全总结

| 层级 | 职责 | 实现位置 |
|-----|------|---------|
| 传输层（FUDP） | 身份认证、端到端加密 | FudpNode（已实现）|
| 框架层（FAPI） | 请求路由、余额管理、输入验证 | FapiServer |
| 组件层 | 业务逻辑、资源访问控制 | 各Component |

**关键设计决策**：
- ✅ 认证在FUDP层完成，FAPI层直接信任peerId
- ✅ 防重放在FUDP层完成（ReplayProtection），无需nonce
- ✅ 错误码与FUDP层保持一致（FapiCode ↔ ResponseMessage.STATUS_*）
- ✅ 不依赖CodeMessage，保持FAPI独立性
- ✅ 请求格式统一：api + fcdsl(查询) 或 api + params(操作)
- ✅ 不考虑向后兼容，简洁优先
- ✅ HELLO/PING复用FudpNode实现，服务广播通过FapiServer.buildAdvertiseData()
- ✅ 计费规则使用链上Service.pricePerKB，按响应/文件大小计费

### 11.6 实体名称列表

所有支持的实体名称定义于 `EntityProperty.java`，BASE组件的查询接口使用这些实体名。

**链上基础实体**：

| 实体名 | 类 | 说明 |
|-------|-----|------|
| `block` | Block | 区块 |
| `tx` | Tx | 交易 |
| `cash` | Cash | UTXO |
| `opreturn` | OpReturn | OP_RETURN数据 |
| `multisig` | Multisig | 多签地址 |
| `p2sh` | P2SH | P2SH地址 |
| `block_mark` | BlockMark | 区块标记 |

**身份实体**：

| 实体名 | 类 | 说明 |
|-------|-----|------|
| `freer` | Freer | 自由者（用户身份）|
| `freer_history` | FreerHist | 身份变更历史 |
| `reputation_history` | RepuHist | 声誉历史 |
| `nid` | Nid | NID |
| `nobody` | Nobody | 匿名身份 |

**协议/服务实体**：

| 实体名 | 类 | 说明 |
|-------|-----|------|
| `protocol` | Protocol | 协议 |
| `code` | Code | 代码 |
| `service` | Service | 服务 |
| `app` | App | 应用 |
| `protocol_history` | ProtocolHistory | 协议历史 |
| `code_history` | CodeHistory | 代码历史 |
| `service_history` | ServiceHistory | 服务历史 |
| `app_history` | AppHistory | 应用历史 |

**社交实体**：

| 实体名 | 类 | 说明 |
|-------|-----|------|
| `contact` | Contact | 联系人 |
| `mail` | Mail | 邮件 |
| `secret` | Secret | 密信 |
| `group` | Group | 群组 |
| `team` | Team | 团队 |
| `group_history` | GroupHistory | 群组历史 |
| `team_history` | TeamHistory | 团队历史 |
| `statement` | Statement | 声明 |
| `text` | Text | 文本 |
| `remark` | Remark | 备注 |
| `text_history` | TextHistory | 文本历史 |
| `remark_history` | RemarkHistory | 备注历史 |
| `news` | News | 新闻 |

**内容实体**：

| 实体名 | 类 | 说明 |
|-------|-----|------|
| `sound` | Sound | 音频 |
| `sound_history` | SoundHistory | 音频历史 |
| `image` | Image | 图片 |
| `image_history` | ImageHistory | 图片历史 |
| `video` | Video | 视频 |
| `video_history` | VideoHistory | 视频历史 |
| `proof` | Proof | 证明 |
| `proof_history` | ProofHistory | 证明历史 |
| `box` | Box | 盒子 |
| `box_history` | BoxHistory | 盒子历史 |

**交换实体**：

| 实体名 | 类 | 说明 |
|-------|-----|------|
| `swap_state` | SwapStateData | 交换状态 |
| `swap_lp` | SwapLpData | 流动性提供者 |
| `swap_finished` | SwapAffair | 已完成交换 |
| `swap_pending` | SwapPendingData | 待处理交换 |
| `swap_price` | SwapPriceData | 交换价格 |
| `token` | Token | 代币 |
| `token_history` | TokenHistory | 代币历史 |
| `token_holder` | TokenHolder | 代币持有者 |

**其他**：

| 实体名 | 类 | 说明 |
|-------|-----|------|
| `webhook` | WebhookInfo | Webhook |

### 11.7 请求格式速查

| API类型 | 参数字段 | 示例 |
|--------|---------|------|
| 查询类 | `fcdsl` | base.search, base.getByIds, disk.list |
| 操作类 | `params` | disk.put, disk.get, disk.carve |
| 系统类 | 无或`params` | base.health |

```
// 查询类请求模板
{
  "id": "req-{client-generated-id}",
  "api": "{component}.{method}",
  "fcdsl": { "entity": "freer", "filter": {...}, "size": 20 }
}

// 操作类请求模板
{
  "id": "req-{client-generated-id}",
  "api": "{component}.{method}",
  "params": { ... }
}

// 响应模板
{
  "id": "resp-{server-generated-id}",
  "requestId": "req-{client-generated-id}",
  "code": 0,
  "message": "Success",
  "data": {...}
}
```

### 11.8 分页游标实现

**last字段说明**：

响应中的`last`字段用于分页查询，采用ES的`search_after`机制：

```java
/**
 * 分页游标使用示例
 * 
 * last字段为ES的search_after参数值，由排序字段的值组成
 * 客户端在下次请求时将last值传入fcdsl.after
 */

// 首次请求
{
  "api": "base.search",
  "fcdsl": { 
    "entity": "tx", 
    "size": 20,
    "sort": [{"timestamp": "desc"}, {"txId": "asc"}]
  }
}

// 首次响应
{
  "code": 0,
  "data": [...],
  "got": 20,
  "total": 1000,
  "last": ["1703580000000", "txid123"]  // 最后一条记录的排序字段值
}

// 翻页请求（将last值传入after）
{
  "api": "base.search",
  "fcdsl": { 
    "entity": "tx", 
    "size": 20,
    "sort": [{"timestamp": "desc"}, {"txId": "asc"}],
    "after": ["1703580000000", "txid123"]
  }
}
```

**注意事项**：
- `last`数组长度与`sort`字段数量一致
- 排序字段必须包含唯一字段（如txId）以确保分页稳定性
- `after`值必须与上次响应的`last`完全一致

### 11.9 ES索引Mapping设计

> **注意**：DISK元数据索引设计已移至 `Docs/DISK服务设计方案.md`

**索引命名规范**：
- 服务相关索引：`{sid_brief}_{index_name}`
- sid_brief：服务ID的前8位（通过`Settings.addSidBriefToName()`生成）

### 11.10 客户端SDK使用示例

**FapiClient已实现完整的客户端功能**，以下是使用示例：

```java
/**
 * 客户端使用示例（基于现有FapiClient实现）
 * 参见：fapi/client/FapiClient.java
 */
public class FapiClientExample {
    public static void main(String[] args) throws Exception {
        // 1. 创建客户端FudpNode
        byte[] clientKey = KeyTools.genNewPrivateKey();
        NodeConfig config = new NodeConfig().setPort(0); // 随机端口
        FudpNode node = new FudpNode(clientKey, config);
        node.start();
        
        // 2. 使用引导发现连接到FAPI服务
        FapiClient client = FapiClient.bootstrap(node);
        if (client == null) {
            System.err.println("Failed to connect to FAPI service");
            return;
        }
        
        // 3. 查询区块高度
        Long height = client.bestHeight();
        System.out.println("Best height: " + height);
        
        // 4. 根据FID查询余额
        Map<String, Long> balances = client.balanceByIds("FEk41...", "FGw2...");
        System.out.println("Balances: " + balances);
        
        // 5. 自定义FCDSL查询
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEntity("tx");
        fcdsl.addFilter("sender", "FEk41...");
        fcdsl.addSize(10);
        FapiResponse response = client.general(fcdsl);
        
        // 6. 获取余额信息（每次响应都会返回）
        System.out.println("Balance: " + client.getLastBalance());
        System.out.println("Balance seq: " + client.getLastBalanceSeq());
        
        // 7. 清理
        node.stop();
    }
}
```

**服务发现流程**（`FapiClient.discoverViaHelloAndPing()`）：
1. 发送HELLO获取服务端公钥
2. 发送PING（wantInfo=true）获取服务信息
3. 从PONG中解析FAPI服务列表
4. 创建FapiClient连接到服务

### 11.11 API版本管理

**当前设计**：不实现显式版本号，通过以下方式保持兼容性：

1. **语义化API命名**：`component.method` 格式天然支持演进
2. **新增而非修改**：新功能通过新增API实现，而非修改现有API
3. **响应扩展**：响应字段只增不减，客户端忽略未知字段

**未来扩展**（如需要显式版本）：

```json
// 方案A：在api字段中嵌入版本
{ "api": "v2.base.search", ... }

// 方案B：在请求中增加版本字段
{ "ver": "2.0", "api": "base.search", ... }
```

**当前版本**：所有API默认为v1，无需显式标注。

### 11.12 数据一致性保证

**写操作一致性策略**：
- 采用"先写数据，后写元数据，失败回滚"策略
- 这是简化的单节点一致性，不涉及分布式事务

> 具体实现示例见 `Docs/DISK服务设计方案.md` 第5节

---

## 更新记录

| 版本 | 日期 | 变更内容 |
|-----|------|---------|
| 1.0 | 2024-12 | 初始版本 |
| 1.1 | 2024-12 | 简化设计：移除APIP兼容层；统一API为getByIds/search；添加端口冲突检测；移除FapiResponse的time字段 |
| 1.2 | 2024-12 | 增强设计：添加组件生命周期、异步处理、余额集成、大文件传输、安全监控、测试策略 |
| 1.3 | 2024-12 | 进一步简化：<br>- **不考虑向后兼容**（项目尚未生产环境）<br>- **统一使用api字段**，移除endpoint兼容<br>- **参数分离**：fcdsl用于查询，params用于其他操作<br>- **独立错误码设计**：FapiCode类，与FUDP层保持一致，参考HTTP状态码<br>- **不依赖CodeMessage**，保持FAPI独立性<br>- **移除nonce字段**：防重放由FUDP层ReplayProtection处理<br>- 更新安全设计章节，说明FUDP层已实现的安全机制 |
| 1.4 | 2024-12 | 完善设计：<br>- **请求/响应ID设计**：FapiRequest.id（请求ID），FapiResponse.id（响应ID）+ requestId（回传请求ID）<br>- **限流机制分层**：FUDP层传输级保护，FAPI层业务级限流，互不冲突<br>- **动态超时控制**：根据文件大小动态计算，不设硬上限，支持10GB+大文件<br>- **断点续传机制**：disk.resumeUpload API，UploadState状态管理<br>- **DISK组件完善**：元数据结构（DiskItem）、存储路径设计、批量操作（仅需batchPut）<br>- **实体名称标准化**：使用EntityProperty.java定义的实体名（如freer代替fid）<br>- **附录补充**：完整实体名称列表（71种） |
| 1.5 | 2024-12 | 任务边界明确与设计澄清：<br>- **明确任务范围**：本次聚焦FAPI启动逻辑重构和BASE组件改进，DISK等业务组件详细设计在后续任务<br>- **复用现有实现**：HELLO/PING已在FudpNode实现，计费规则使用Service.pricePerKB<br>- **DiskItem设计澄清**：开放存储、无需身份验证、expire=-1表示永不过期、每次访问更新expire<br>- **移除disk.delete**：采用过期自动清理机制，无需显式删除接口<br>- **修复章节编号**：7.3/7.4重复问题修正为7.6/7.7<br>- **组件间调用机制**：AbstractFapiComponent.callComponent()方法<br>- **优雅关闭顺序**：FapiServer.stop()增加等待进行中请求完成逻辑<br>- **分布式追踪**：日志格式增加traceId字段<br>- **FapiResponse.error签名统一**：统一使用(requestId, code, message)格式<br>- **新增附录**：分页游标实现(11.8)、ES索引Mapping(11.9)、客户端SDK示例(11.10)、API版本管理(11.11)、数据一致性保证(11.12) |
| 1.6 | 2024-12 | BASE组件API完整实现：<br>- **移植EndpointHandler**：将GeneralEndpointHandler、CashEndpointHandler、ChainInfoEndpointHandler、MempoolEndpointHandler、TransactionEndpointHandler的功能整合到BaseComponent<br>- **新增16个BASE API**：totals、balanceByIds、cashValid、getUtxo、chainInfo、blockTimeHistory、difficultyHistory、hashRateHistory、unconfirmed、unconfirmedCashes、broadcastTx、decodeTx、estimateFee（加上原有的getByIds、search、health）<br>- **服务端完整实现**：BaseComponent包含所有API的处理逻辑和辅助方法<br>- **客户端SDK实现**：BaseClient提供类型安全的API调用方法<br>- **API请求/响应示例**：每个API的完整JSON示例<br>- **API设计规范更新**：11.2节添加完整的BASE和DISK组件API列表表格 |
| 1.7 | 2024-12 | 客户端服务发现与启动类重构：<br>- **新增默认服务端点**：FapiDefaults定义默认端点（127.0.0.1:8500, freecash.info:8500）<br>- **客户端自动服务发现**：启动时尝试默认服务，成功后查询链上FAPI服务商列表供用户选择<br>- **新增FapiServiceDiscovery**：实现服务发现逻辑，包括默认端点连接和链上服务查询<br>- **保留旧启动类**：StartFapiServerOld和StartFapiClientOld暂时保留，创建新的StartFapiServer和StartFapiClient<br>- **DISK组件延后**：明确DISK组件将在专门的后续任务中实现，本次重构不包含<br>- **实施计划调整**：移除阶段4的DISK实现任务，标记为后续任务；新增阶段3的客户端服务发现相关任务<br>- **新增章节2.4**：客户端服务发现机制，包含流程图和实现代码示例<br>- **启动类设计更新**：5.3节增加启动类命名规范表格和客户端启动类详细设计 |
| 1.8 | 2024-12 | 文档精简：<br>- **DISK内容独立**：将DISK组件详细设计提取到 `Docs/DISK服务设计方案.md`<br>- **精简4.5节**：仅保留DISK组件概述和API列表<br>- **精简11.9节**：移除DISK ES索引Mapping详细设计<br>- **精简11.12节**：移除DISK一致性代码示例，保留策略说明 |
| 1.9 | 2024-12 | **实施完成**：<br>- **阶段1-5全部实施**：核心框架、BASE组件、启动框架、安全与监控、测试与文档<br>- **新增核心类**：FapiComponent、AbstractFapiComponent、ComponentRegistry、FapiCode、FapiRequest/Response<br>- **新增启动类**：ServiceBootstrapConfig、ServiceBootstrap、StartFapiServer、StartFapiClient<br>- **新增安全类**：RequestValidator、ValidationResult<br>- **新增监控类**：HealthChecker、HealthStatus、ComponentHealth、MetricsCollector、MetricsReport、AuditLogger<br>- **新增组件**：BaseComponent（整合16个API）<br>- **FapiServer增强**：集成输入验证、性能指标收集、审计日志、健康检查<br>- **单元测试**：RequestValidatorTest、HealthCheckerTest、MetricsCollectorTest、BaseComponentTest、FapiServerComponentTest<br>- **API文档**：新增 `Docs/FAPI-API-Reference.md` |
