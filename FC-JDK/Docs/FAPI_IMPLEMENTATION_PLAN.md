# FAPI 模块实现计划

> 版本: 2.0
> 日期: 2024年12月
> 状态: **已完成实施**

## 0. 重构后的模块化架构 (2024-12 重构)

### 0.1 重构范围

**重要原则**：
- ✅ **仅对FAPI服务进行重构**
- ✅ **原有启动逻辑和服务保持不变**
- ✅ **新增DISK等业务服务基于重构后的FAPI框架**
- ✅ **简洁优先，不考虑向后兼容**（项目尚未进入生产环境）
- ✅ **保持FAPI独立性**（不依赖CodeMessage等不成熟设计）
- ❌ **不需要兼容APIP**
- ❌ **DISK组件暂不实现**（将在专门的任务中实现）

**已完成任务**：
- ✅ 重构FAPI的启动逻辑（ServiceBootstrap、ServiceBootstrapConfig）
- ✅ 对FAPI框架层做必要改进（FapiComponent架构）
- ✅ 对BASE组件做必要改进（16个API）
- ✅ 为FAPI客户端实现默认服务发现机制
- ✅ 实现安全与监控模块

### 0.2 新模块结构

```
fapi/
├── FapiCode.java                       # 统一错误码（HTTP状态码风格）
├── FapiComponent.java                  # 组件接口（生命周期、API列表）
├── AbstractFapiComponent.java          # 组件抽象基类
├── ComponentRegistry.java              # 组件注册表
├── FapiDefaults.java                   # 默认配置（服务端点、超时）
├── FapiServiceDiscovery.java           # 服务发现逻辑
├── ServiceBootstrap.java               # 统一启动器
├── ServiceBootstrapConfig.java         # 启动配置
├── StartFapiServer.java                # 新服务端启动类
├── StartFapiClient.java                # 新客户端启动类
│
├── components/
│   └── BaseComponent.java              # BASE组件（16个API）
│
├── message/
│   ├── FapiRequest.java                # 统一请求格式
│   └── FapiResponse.java               # 统一响应格式
│
├── service/
│   └── FapiServer.java                 # FAPI服务主类（集成组件管理、监控）
│
├── security/
│   ├── RequestValidator.java           # 请求验证器
│   └── ValidationResult.java           # 验证结果
│
├── monitor/
│   ├── HealthChecker.java              # 健康检查器
│   ├── HealthStatus.java               # 健康状态
│   ├── ComponentHealth.java            # 组件健康状态
│   ├── MetricsCollector.java           # 性能指标收集器
│   ├── MetricsReport.java              # 性能报告
│   └── AuditLogger.java                # 审计日志器
│
├── handler/
│   ├── FapiRequestDispatcher.java      # 请求分发器（旧）
│   ├── FcFudpRequestHandler.java       # 旧请求处理器（兼容）
│   └── endpoint/                       # Endpoint处理器组（旧）
│
├── query/
│   ├── FcdslQueryExecutor.java         # FCDSL查询执行器
│   └── QueryResult.java                # 查询结果
│
├── rpc/
│   └── RpcOperationService.java        # RPC操作服务
│
└── util/
    └── ResponseBuilder.java            # 响应构建工具
```

### 0.3 核心模块说明

#### FapiComponent（组件接口）

定义FAPI服务组件的生命周期和API处理：

```java
public interface FapiComponent {
    enum State { CREATED, INITIALIZING, RUNNING, STOPPING, STOPPED }
    
    String getName();                    // 组件名称（如"BASE"、"DISK"）
    List<String> getApiList();           // 支持的API列表
    State getState();                    // 当前状态
    boolean isHealthy();                 // 健康检查
    
    void initialize(FapiServer server);  // 初始化
    FapiResponse handleRequest(FapiRequest request, String peerId);  // 处理请求
    void close(long timeoutMs);          // 关闭
}
```

#### FapiCode（统一错误码）

与HTTP状态码保持一致，独立于旧的CodeMessage：

```java
public final class FapiCode {
    public static final int SUCCESS = 0;
    public static final int ERROR = 1;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int PAYMENT_REQUIRED = 402;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int PAYLOAD_TOO_LARGE = 413;
    public static final int TOO_MANY_REQUESTS = 429;
    public static final int INTERNAL_ERROR = 500;
    public static final int SERVICE_UNAVAILABLE = 503;
}
```

#### ServiceBootstrap（统一启动器）

封装服务端和客户端的启动逻辑：

```java
public class ServiceBootstrap {
    // 服务端启动
    public static ServerBootstrapResult bootstrapServer(ServiceBootstrapConfig config);
    
    // 客户端启动
    public static ClientBootstrapResult bootstrapClient(ServiceBootstrapConfig config);
}
```

#### BaseComponent（BASE组件）

提供16个链上基础数据API：

| API | 说明 |
|-----|------|
| base.health | 健康检查 |
| base.totals | 各索引文档计数 |
| base.getByIds | 根据ID列表查询实体 |
| base.search | 通用FCDSL搜索 |
| base.balanceByIds | 根据FID查询余额 |
| base.cashValid | 获取有效UTXO |
| base.getUtxo | 获取UTXO（用于构建交易） |
| base.chainInfo | 链信息 |
| base.blockTimeHistory | 出块时间历史 |
| base.difficultyHistory | 难度历史 |
| base.hashRateHistory | 算力历史 |
| base.unconfirmed | 未确认交易信息 |
| base.unconfirmedCashes | 未确认的UTXO |
| base.broadcastTx | 广播交易 |
| base.decodeTx | 解码交易 |
| base.estimateFee | 估算交易费用 |

### 0.4 依赖关系

```
                    ┌─────────────────────────────┐
                    │      ServiceBootstrap       │
                    │      (Unified Launcher)     │
                    └─────────────┬───────────────┘
                                  │
              ┌───────────────────┼───────────────────┐
              │                   │                   │
              ▼                   ▼                   ▼
   ┌──────────────────┐  ┌────────────────┐  ┌─────────────────────┐
   │    FapiServer    │  │  FudpNode      │  │   Settings          │
   │  (Service Core)  │  │  (Transport)   │  │   (Configuration)   │
   └────────┬─────────┘  └────────────────┘  └─────────────────────┘
            │
            ▼
   ┌────────────────────────────────────────────────────────────┐
   │                    Component Layer                         │
   │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
   │  │ BaseComponent│  │(DiskComponent)│ │(MapComponent) │ ... │
   │  │  (16 APIs)   │  │  (Future)     │ │  (Future)     │     │
   │  └──────────────┘  └──────────────┘  └──────────────┘     │
   └────────────────────────────────────────────────────────────┘
            │
            ▼
   ┌────────────────────────────────────────────────────────────┐
   │                  Security & Monitoring                     │
   │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
   │  │RequestValidator│ │HealthChecker │ │MetricsCollector│    │
   │  └──────────────┘  └──────────────┘  └──────────────┘     │
   └────────────────────────────────────────────────────────────┘
```

### 0.5 客户端服务发现机制

**默认服务端点**：

```java
public class FapiDefaults {
    public static final String[] DEFAULT_ENDPOINTS = {
        "127.0.0.1:8500",      // 本地服务
        "freecash.info:8500"   // 公共服务
    };
    public static final int DEFAULT_FAPI_PORT = 8500;
    public static final int DEFAULT_DISCOVERY_TIMEOUT_MS = 5000;
}
```

**服务发现流程**：

```
启动客户端 → 尝试默认服务列表 → HELLO+PING发现服务
       ↓                              ↓
   全部失败 ← ─ ─ ─ ─ ─ ─ ─ ─     发现成功
       ↓                              ↓
  提示手动输入                    查询链上FAPI服务
       ↓                              ↓
  用户输入URL                    显示服务列表供选择
       ↓                              ↓
  HELLO+PING                     用户选择或使用默认
       ↓                              ↓
   连接成功 ─ ─ ─ ─ ─ ─ ─ ─ ─ → 连接并保存配置
```

### 0.6 设计优势

1. **单一职责**: 每个组件职责清晰
2. **可扩展**: 添加新组件只需实现FapiComponent接口
3. **可测试**: 每个模块可独立单元测试
4. **可维护**: 修改某一组件不影响其他模块
5. **类型安全**: 使用枚举和强类型替代硬编码字符串
6. **统一监控**: 内置健康检查、性能指标、审计日志

---

## 1. 概述

FAPI (Freecash API) 模块是基于 FUDP 协议的查询服务，提供对 FchParser 和 FEIP 写入 Elasticsearch 数据的查询能力。与基于 HTTP 的 APIP 服务不同，FAPI 利用 FUDP 协议内置的身份认证、加密、防重放攻击与传输计量，上层自带经济模型，无需额外的 HTTP 基础设施。

### 1.1 设计目标

- **组件化架构**：通过FapiComponent接口实现可插拔的服务组件
- **统一启动框架**：ServiceBootstrap封装复杂的初始化逻辑
- **自动服务发现**：客户端自动发现并连接FAPI服务
- **内置安全监控**：输入验证、健康检查、性能指标、审计日志

### 1.2 关键差异（新旧对比）

| 特性 | 旧实现 | 新实现 |
|------|-------|-------|
| 启动方式 | Starter.startServer() | ServiceBootstrap.bootstrapServer() |
| 请求处理 | FcFudpRequestHandler | FapiComponent.handleRequest() |
| 错误码 | CodeMessage | FapiCode（HTTP风格） |
| 请求格式 | endpoint字段 | api字段（component.method） |
| 组件管理 | 无 | ComponentRegistry |
| 健康检查 | 无 | HealthChecker |
| 性能监控 | 无 | MetricsCollector |
| 服务发现 | 手动配置 | FapiServiceDiscovery（自动） |

## 2. 请求/响应格式

### 2.1 请求格式

```json
{
  "id": "req-xxx",           // 请求ID（客户端生成）
  "api": "component.method", // API名称
  "fcdsl": { ... },          // 查询参数（查询类API）
  "params": { ... }          // 操作参数（操作类API）
}
```

### 2.2 响应格式

```json
{
  "id": "resp-xxx",          // 响应ID（服务端生成）
  "requestId": "req-xxx",    // 对应请求ID
  "code": 0,                 // 状态码
  "message": "Success",      // 状态消息
  "data": { ... },           // 响应数据
  "got": 10,                 // 返回数量
  "total": 100,              // 总数量
  "last": ["..."],           // 分页游标
  "bestHeight": 1234567,     // 最佳区块高度
  "balance": 100000000       // 账户余额
}
```

### 2.3 API分类

| API类型 | 参数字段 | 示例 |
|--------|---------|------|
| 查询类 | `fcdsl` | base.search, base.getByIds |
| 操作类 | `params` | disk.put, base.broadcastTx |
| 系统类 | 无或`params` | base.health, base.totals |

## 3. 实现细节

### 3.1 组件初始化流程

```java
// 1. 创建配置
ServiceBootstrapConfig config = ServiceBootstrapConfig.forFapiServer()
    .setBr(br)
    .addModule(new Module(Service.class.getSimpleName(), Service.ServiceType.NASA_RPC.name()))
    .addModule(new Module(Service.class.getSimpleName(), Service.ServiceType.ES.name()))
    .setComponentTypes(new String[]{"BASE"});

// 2. 启动服务
ServerBootstrapResult result = ServiceBootstrap.bootstrapServer(config);
FapiServer fapiServer = result.getFapiServer();

// 3. 服务自动加载组件、启动监听
```

### 3.2 请求处理流程

```
1. FUDP RequestMessage 到达
   ↓
2. FapiServer.onRequestReceived() 接收
   ↓
3. RequestValidator.validate() 验证请求
   ↓
4. FapiServer.routeRequest() 路由到组件
   ↓
5. FapiComponent.handleRequest() 处理
   ↓
6. MetricsCollector.recordRequest() 记录指标
   ↓
7. AuditLogger.logRequest() 审计日志
   ↓
8. FudpNode.respond() 发送响应
```

### 3.3 输入验证规则

```java
public class RequestValidator {
    // 限制常量
    private static final int MAX_IDS_COUNT = 100;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_ENTITY_LENGTH = 64;
    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    
    // 验证方法
    public static ValidationResult validate(FapiRequest request);
    public static ValidationResult validateIds(List<String> ids);
    public static ValidationResult validateFcdsl(Fcdsl fcdsl);
    public static ValidationResult validateFileSize(long size);
    public static ValidationResult validatePeerId(String peerId);
}
```

### 3.4 健康检查

```java
public class HealthChecker {
    public HealthStatus check() {
        HealthStatus status = new HealthStatus();
        
        // 检查各组件状态
        for (FapiComponent component : server.getComponents()) {
            status.getComponents().put(component.getName(), 
                new ComponentHealth(component.getName(), 
                    component.getState().name(), 
                    component.isHealthy()));
        }
        
        // 检查外部依赖
        status.setEsConnected(checkEsConnection());
        status.setRpcConnected(checkRpcConnection());
        status.setFudpRunning(fudpNode.isRunning());
        
        return status;
    }
}
```

### 3.5 性能指标收集

```java
public class MetricsCollector {
    public void recordRequest(String component, String api, long durationMs, boolean success);
    public MetricsReport getReport();
    public void reset();
}

// 指标包括：
// - 请求总数/成功数/失败数
// - 响应时间（平均/最小/最大）
// - 成功率
// - 每组件、每API统计
// - QPS
```

## 4. 安全设计

### 4.1 FUDP层安全（已实现）

- **身份认证**：HELLO/CHALLENGE握手，基于FCH地址
- **消息加密**：AES-256-GCM对称加密
- **防重放**：ReplayProtection，基于nonce和时间窗口
- **防放大**：PongDataProvider限制1KB，速率限制2秒

### 4.2 FAPI层安全（已实现）

- **输入验证**：RequestValidator验证所有请求参数
- **业务限流**：基于API类型的限流配置
- **审计日志**：AuditLogger记录敏感操作

## 5. 测试覆盖

### 5.1 已实现的测试

| 测试类 | 测试内容 | 状态 |
|--------|---------|------|
| RequestValidatorTest | 请求验证（32个测试） | ✅ 通过 |
| MetricsCollectorTest | 性能指标收集（14个测试） | ✅ 通过 |
| HealthCheckerTest | 健康检查 | ⚠️ 需Mockito环境 |
| BaseComponentTest | BASE组件 | ⚠️ 需Mockito环境 |
| FapiServerComponentTest | 服务器集成测试 | ⚠️ 需Mockito环境 |

### 5.2 运行测试

```bash
# 运行不需要Mockito的测试
mvn test -Dtest="fapi.security.RequestValidatorTest,fapi.monitor.MetricsCollectorTest"

# 运行所有FAPI测试（需要配置Mockito环境）
mvn test -Dtest="fapi.**"
```

## 6. 实施状态

### 6.1 已完成阶段

| 阶段 | 内容 | 状态 |
|------|------|------|
| 阶段1 | 核心框架（FapiComponent、FapiCode、FapiRequest/Response） | ✅ 完成 |
| 阶段2 | BASE组件（16个API、FcdslQueryExecutor、RpcOperationService） | ✅ 完成 |
| 阶段3 | 启动框架（ServiceBootstrap、服务发现） | ✅ 完成 |
| 阶段4 | 安全与监控（RequestValidator、HealthChecker、MetricsCollector、AuditLogger） | ✅ 完成 |
| 阶段5 | 测试与文档（单元测试、API文档） | ✅ 完成 |

### 6.2 新增文件列表

**核心框架**：
- `fapi/FapiComponent.java`
- `fapi/AbstractFapiComponent.java`
- `fapi/ComponentRegistry.java`
- `fapi/FapiCode.java`
- `fapi/message/FapiRequest.java`
- `fapi/message/FapiResponse.java`

**启动框架**：
- `fapi/ServiceBootstrapConfig.java`
- `fapi/ServiceBootstrap.java`
- `fapi/StartFapiServer.java`
- `fapi/StartFapiClient.java`
- `fapi/FapiDefaults.java`
- `fapi/FapiServiceDiscovery.java`

**安全与监控**：
- `fapi/security/RequestValidator.java`
- `fapi/security/ValidationResult.java`
- `fapi/monitor/HealthChecker.java`
- `fapi/monitor/HealthStatus.java`
- `fapi/monitor/ComponentHealth.java`
- `fapi/monitor/MetricsCollector.java`
- `fapi/monitor/MetricsReport.java`
- `fapi/monitor/AuditLogger.java`

**组件**：
- `fapi/components/BaseComponent.java`

**测试**：
- `test/java/fapi/security/RequestValidatorTest.java`
- `test/java/fapi/monitor/HealthCheckerTest.java`
- `test/java/fapi/monitor/MetricsCollectorTest.java`
- `test/java/fapi/components/BaseComponentTest.java`
- `test/java/fapi/service/FapiServerComponentTest.java`

**文档**：
- `Docs/FAPI-API-Reference.md`

### 6.3 后续任务

- ❌ DISK组件实现（独立任务）
- ❌ 其他业务组件（MAP、ROAD等）

## 7. 参考文档

- **设计文档**：`Docs/FAPI服务重构方案.md`（v1.9）
- **API文档**：`Docs/FAPI-API-Reference.md`
- **DISK设计**：`Docs/DISK服务设计方案.md`

---

## 附录

### A. 常量定义

```java
// 索引名称：constants/IndicesNames.java
// 错误码：fapi/FapiCode.java
// 默认配置：fapi/FapiDefaults.java
```

### B. 实体名称映射

参见 `data/fcData/EntityProperty.java` 中定义的71种实体类型。

### C. 更新记录

| 版本 | 日期 | 变更内容 |
|-----|------|---------|
| 1.0 | 2024-12 | 初始版本 |
| 2.0 | 2024-12 | **重大重构**：<br>- 新增FapiComponent组件化架构<br>- 新增ServiceBootstrap统一启动框架<br>- 新增客户端服务发现机制<br>- 新增安全与监控模块<br>- 实现BASE组件16个API<br>- 更新所有实施状态为已完成 |
