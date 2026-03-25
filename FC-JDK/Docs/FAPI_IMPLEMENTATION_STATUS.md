# FAPI 经济模型实现状态检查报告

> 最后更新: 2024年12月28日
> 版本: 6.0
> 状态: **核心经济模型功能全部完成，客户端自动充值已实现**

## 概述

本文档对比 `FAPI_ECONOMICS_DESIGN.md` 设计文档与 `FapiBalanceManager.java` 实际实现，列出已实现、未实现和存在差异的功能。

## 新增实现记录（2024-12-28 v6.0）

### 客户端自动充值模块

新增 `fapi/client/` 目录相关文件：

| 文件 | 说明 |
|------|------|
| `AutoRechargeManager.java` | 自动充值管理器，核心充值逻辑 |
| `RechargeMenu.java` | 手动充值菜单 UI |

#### 自动充值功能

当客户端余额低于阈值时，自动触发充值流程：

1. **触发检测**：每次请求响应后检查余额是否低于阈值
2. **获取定价**：从 Service 获取 pricePerKB、minPayment、dealer 信息
3. **计算金额**：`payment = max(purchaseKb * pricePerKB, minPayment)`
4. **获取 UTXOs**：调用 `cashValid` API 获取可用余额
5. **构造交易**：使用 `TxCreator.createAndSignFchTx()` 签名交易
6. **广播交易**：调用 `broadcastTx` API 广播
7. **通知回调**：成功或失败后通知上层应用

#### 核心特性

| 特性 | 说明 |
|------|------|
| 冷却期保护 | 防止短时间内重复充值（默认 60 秒） |
| 重试策略 | Cash 无效时自动重试（默认 3 次，指数退避） |
| 异步执行 | 不阻塞业务请求 |
| Service 缓存 | 减少服务信息查询次数（5 分钟缓存） |
| 回调通知 | 充值结果可回调通知上层 |
| **价格保护** | 支付额超过最大限制时停止充值并告警（默认 1 FCH） |

#### 配置项（Settings）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `autoRechargeEnabled` | `true` | 是否启用自动充值 |
| `autoRechargeThreshold` | `0` | 触发阈值（聪），余额 <= 阈值时触发 |
| `autoRechargeKb` | `1000` | 购买量（KB） |
| `autoRechargeCooldownMs` | `60000` | 冷却时间（毫秒） |
| `autoRechargeMaxRetries` | `3` | 最大重试次数 |
| `autoRechargeRetryDelayMs` | `2000` | 重试基础延迟（毫秒） |
| `autoRechargeMaxPayment` | `100000000` | **最大支付额（聪）**，超过此值停止充值并告警，默认 1 FCH |

#### FapiClient 新增方法

| 方法 | 说明 |
|------|------|
| `manualRecharge(Double amountFch)` | 手动充值（指定金额） |
| `manualRecharge()` | 手动充值（默认计算金额） |
| `setRechargeCallback(callback)` | 设置充值结果回调 |
| `isRecharging()` | 检查是否正在充值 |
| `getLastRechargeTxId()` | 获取上次成功充值的交易ID |
| `getAutoRechargeManager()` | 获取自动充值管理器 |

#### 手动充值菜单（RechargeMenu）

提供命令行交互界面：
- 查看当前余额
- 查看服务定价
- 手动充值（默认金额）
- 手动充值（自定义金额）
- 查看自动充值状态（含价格告警状态）
- 查看上次充值信息
- **重置价格告警**（手动恢复被阻止的自动充值）

#### 价格保护机制

当计算的支付额超过 `maxPayment` 限制时：

1. **立即停止充值**：不执行交易
2. **设置告警状态**：`stoppedDueToPriceLimit = true`
3. **记录告警消息**：详细说明请求金额和限额
4. **触发回调**：通知上层应用
5. **阻止后续自动充值**：直到手动调用 `resetPriceAlert()`

这可以防止：
- 服务商恶意设置高价
- 配置错误导致意外大额支付
- 链上价格信息被篡改

---

## 新增实现记录（2024-12-28 v5.0）

### LISTEN_PATH 监控模块

新增 `fapi/recharge/` 目录：

| 文件 | 说明 |
|------|------|
| `BlockStorageWatcher.java` | 区块存储目录监控器，基于 Java NIO WatchService |
| `RechargeScanner.java` | 充值扫描器，配合监控器自动处理新充值 |

功能：
- 监听本地区块存储目录文件变动
- 检测新区块落盘触发充值查询
- 支持定时轮询模式和文件监控模式
- 失败重试和冻结保护机制
- 重扫功能

### 重传去重功能

`FapiBalanceManager.checkAndCharge()` 增强：
- 重传消息（retransmitCount > 0）不重复计费
- 使用原始消息的 requestKey 进行去重
- 如果原始消息已计费，重传直接返回已存在结果

### 分成分配模块

新增 `fapi/settle/` 目录：

| 文件 | 说明 |
|------|------|
| `ShareDistributor.java` | 分成分配器，实现 orderViaShare 和 consumeViaShare |

功能：
- 充值分成（orderViaShare）计算
- 消费分成（consumeViaShare）计算
- 内部精度为万分之一聪，避免精度丢失
- 余数累积机制
- 周期结算分配（包含备用金预留）

### 经济模型指标收集

新增 `fapi/monitor/EconomicsMetricsCollector.java`：

| 指标类别 | 指标项 |
|----------|--------|
| 扣费指标 | 成功/失败率、信用拒绝次数、黑名单拒绝数 |
| 充值指标 | 充值次数、总额、冲突次数 |
| 消费指标 | 总消费、免费请求数、重传去重数 |
| 数据源指标 | 最佳高度、数据源高度、高度差 |
| 快照指标 | 快照次数、失败次数、滞后时间 |
| WAL 指标 | 写入次数、字节数、fsync 次数、平均耗时 |
| 结算指标 | 结算次数、分配总额 |
| 回滚指标 | 检测次数、处理次数 |
| 告警状态 | 各项阈值告警 |

### 客户端余额验证器增强

`fapi/client/BalanceVerifier.java` 增强：
- 基于本地 pricePerKB 和计量数据推导预期消费
- 跟踪最近 N 次漂移事件历史
- 记录本地消费与服务端消费对比
- 获取验证器状态报告

---

## 新增实现记录（2024-12-28 v4.0）

### WAL (Write-Ahead Log) 机制

新增 `fapi/wal/` 目录：

| 文件 | 说明 |
|------|------|
| `WalEntry.java` | WAL 条目数据结构，包含类型、时间戳、键、数据、哈希链 |
| `WalEntryType.java` | WAL 条目类型枚举 |
| `WalManager.java` | WAL 管理器，支持追加写入、批量写入、fsync、轮转、恢复 |

### 快照机制

新增 `fapi/snapshot/` 目录：

| 文件 | 说明 |
|------|------|
| `SnapshotManager.java` | 快照管理器，支持创建、加载、验证快照 |

### FapiBalanceManager 增强

| 功能 | 方法 | 说明 |
|------|------|------|
| WAL 集成 | `initializeWalAndSnapshot()` | 初始化 WAL 和快照管理器 |
| 启动恢复 | `performStartupRecovery()` | 加载快照 → 重放 WAL |
| 定时快照 | `startSnapshotScheduler()` | 每 10 分钟或 10 万事务触发快照 |
| 周期结算 | `settle(cycleId, stakeholders)` | 周期分配（幂等，基于 cycleId） |
| 回滚检测 | `updateBestBlock(height, blockId)` | 检测 blockId 变化触发回滚 |
| 回滚处理 | `handleRollback()` | 30 区块窗口内充值冲减 |
| 余额调整 | `adjustCredit()` | 支持回滚冲减 |
| 黑名单 | `isBlacklisted()`, `addToBlacklist()`, `removeFromBlacklist()` | flags bit0 标记 |
| 免费类型 | `setFreeMessageTypes()`, `isFreeMessageType()` | 配置免费消息类型 |
| 重传去重 | 在 `checkAndCharge()` 中 | 重传按首次计费 |

---

## 架构重构记录（2024-12-28 v3.0）

### 已删除的旧代码（服务端）

| 文件 | 说明 |
|------|------|
| `FcFudpRequestHandler.java` | 旧请求处理器，已被组件化架构替代 |
| `FapiRequestDispatcher.java` | 旧请求分发器 |
| `endpoint/AbstractEndpointHandler.java` | 旧端点处理器基类 |
| `endpoint/*EndpointHandler.java` | 已整合到 BaseComponent |

### FapiServer 简化

- 删除所有旧请求处理相关代码
- 所有请求必须使用 `FapiRequest` 格式，包含 `api` 字段

---

## ✅ 已完全实现的功能

### 1. 核心数据结构
- ✅ `PeerBalance` 类：包含 balance, creditLimit, seq, flags, version
- ✅ `MetaState` 类：包含 bestHeight, bestBlockId, auditTipHash, snapshotVersion, lastSnapshotHeight
- ✅ KV 存储键格式：`B/<peerId>`, `R/cash/<cashId>`, `R/req/<requestKey>`, `S/<cycleId>`, `M/meta`, `A/` (审计)

### 2. 核心业务接口
- ✅ `checkAndCharge(MeterRecord)` - 基于计量记录扣费（含重传去重、免费类型、黑名单检查）
- ✅ `charge(requestKey, peerId, amount, meta)` - 直接扣费
- ✅ `credit(userId, cashId, amount, src, status, height, blockId)` - 充值入账
- ✅ `getBalance(peerId)` - 查询余额
- ✅ `refreshPricingFromService()` - 手动刷新价格参数
- ✅ `settle(cycleId, stakeholders)` - 周期结算分配

### 3. 计费逻辑
- ✅ KB 向上取整：`(payloadBytes + 1023) / 1024`
- ✅ 价格计算：`kb * pricePerKB`
- ✅ pricePerKB = 0 时免费处理
- ✅ 金额校验：0 ≤ amount ≤ 1e13 satoshi
- ✅ 溢出检测：`safeMultiply` 方法
- ✅ 免费消息类型列表
- ✅ 重传去重：重传按首次计费

### 4. 幂等性保证
- ✅ `requestKey` 幂等：重复请求返回 `ALREADY_EXISTS` 或 `ALREADY_EXISTS_WITH_CONFLICT`
- ✅ `cashId` 幂等：重复充值检测冲突
- ✅ `cycleId` 幂等：重复结算检测
- ✅ 冲突检测：peerId/amount 不一致时返回冲突错误码

### 5. 信用额度管理
- ✅ 信用额度支持：余额可为负，但不能超过 creditLimit
- ✅ 错误码区分：`INSUFFICIENT_BALANCE_BUT_WITHIN_CREDIT` vs `CREDIT_EXCEEDED`
- ✅ 序列号（seq）：每次写入时递增，用于并发控制

### 6. 输入校验
- ✅ Key 格式校验：`[a-zA-Z0-9._:-]`，长度 ≤128
- ✅ 金额范围校验：非负且 ≤ MAX_AMOUNT
- ✅ Meta 大小限制：≤2KB

### 7. 持久化与原子性
- ✅ WriteBatch 原子性：余额、幂等标记、审计在同一批次写入
- ✅ LevelDB 存储：使用嵌入式 KV 数据库
- ✅ 审计日志：append-only，哈希链结构（prevHash + hash）
- ✅ WAL 机制：先写顺序日志再刷表
- ✅ 快照机制：定期生成余额 map 摘要
- ✅ 启动恢复：加载快照 → 重放 WAL

### 8. 价格参数解析
- ✅ `pricePerKB` 解析：从 Service 对象读取并转换为 satoshi
- ✅ `orderViaShare` 解析：转换为 basis points (0-10000)
- ✅ `consumeViaShare` 解析：转换为 basis points (0-10000)
- ✅ 启动校验：pricePerKB <0 或缺失时抛出异常

### 9. 响应集成
- ✅ `FapiResponse` 包含 `balance` 字段
- ✅ 所有响应通过 `fillBalanceInfo()` 填充余额
- ✅ 错误响应也包含余额信息

### 10. 客户端新架构
- ✅ `FapiRequest` 格式：包含 `api`、`fcdsl`、`params` 字段
- ✅ `api` 字段格式：`component.method`（如 `base.search`）
- ✅ 查询类请求使用 `fcdsl` 字段
- ✅ 操作类请求使用 `params` 字段
- ✅ 客户端便捷方法：`query()`、`operation()`、`simple()`

### 11. 审计日志
- ✅ 审计记录类型：income, spend, settle, adjust
- ✅ 哈希链：`hash = SHA256(prevHash || canonical_json)`
- ✅ 规范格式：`ts,type,cashId,requestKey,peerId,amount,currency,status,bestHeight,bestBlockId,note,prevHash`
- ✅ `getRecentAudit(int limit)` - 查询最近审计记录

### 12. 回滚检测与处理
- ✅ `updateBestBlock(height, blockId)` - 更新最佳区块
- ✅ 回滚检测：同高度 blockId 变化触发回滚
- ✅ `handleRollback()` - 30 区块窗口内充值冲减
- ✅ `adjustCredit()` - 追加调整记录（不直接修改原记录）

### 13. 黑名单功能
- ✅ `isBlacklisted(peerId)` - 检查黑名单状态
- ✅ `addToBlacklist(peerId)` - 加入黑名单
- ✅ `removeFromBlacklist(peerId)` - 移除黑名单
- ✅ `FLAG_BLACKLISTED` = 0x01（flags bit0）

### 14. WAL 机制
- ✅ `WalEntry` - WAL 条目数据结构
- ✅ `WalEntryType` - 条目类型枚举
- ✅ `WalManager` - WAL 管理器
- ✅ 顺序追加写入
- ✅ 哈希链验证
- ✅ 可配置 fsync 策略（默认 50ms）
- ✅ 自动轮转（默认 256MB）
- ✅ 启动恢复

### 15. 快照机制
- ✅ `SnapshotManager` - 快照管理器
- ✅ 定期快照（默认 10 分钟或 10 万事务）
- ✅ 余额摘要哈希验证
- ✅ 快照轮转（默认保留 3 个）

### 16. LISTEN_PATH 监听（新增）
- ✅ `BlockStorageWatcher` - 区块存储目录监控
- ✅ 基于 Java NIO WatchService 实现
- ✅ 检测新区块文件触发回调
- ✅ 失败重试和冻结保护
- ✅ `RechargeScanner` - 充值扫描器
- ✅ 支持监控模式和轮询模式
- ✅ 重扫功能

### 17. 重传去重（新增）
- ✅ 重传消息不重复计费
- ✅ 使用原始消息 requestKey 去重
- ✅ 在 `checkAndCharge()` 中实现

### 18. 分成策略实际分配（新增）
- ✅ `ShareDistributor` - 分成分配器
- ✅ `calculateOrderShare()` - 充值分成计算
- ✅ `calculateConsumeShare()` - 消费分成计算
- ✅ 内部精度为万分之一聪
- ✅ 余数累积机制
- ✅ `distribute()` - 周期结算分配
- ✅ 备用金预留（1 FCH）

### 19. 监控指标（新增）
- ✅ `EconomicsMetricsCollector` - 经济模型指标收集器
- ✅ 扣费失败率指标
- ✅ 信用拒绝次数指标
- ✅ 数据源高度差指标
- ✅ 快照滞后指标
- ✅ WAL 写入/fsync 指标
- ✅ 结算/回滚指标
- ✅ 可配置告警阈值

### 20. 客户端余额校验器（增强）
- ✅ `BalanceVerifier` - 余额验证器
- ✅ 漂移检测和告警
- ✅ 累积偏差和连续超阈值检测
- ✅ 基于本地 pricePerKB 推导预期消费（新增）
- ✅ 漂移事件历史记录（新增）
- ✅ 验证器状态报告（新增）

---

## ❌ 未实现的功能

### 低优先级（高级功能）

| 功能 | 说明 | 优先级 |
|------|------|--------|
| 多源数据校验 | 主/备数据源校验，高度/blockId/参数一致性检查 | 🟢 低 |
| 破损检测与修复 | WAL/审计行按行校验 hash/CRC | 🟢 低 |

---

## 📊 实现完成度统计

| 类别 | 已实现 | 未实现 | 总计 | 完成度 |
|------|--------|--------|------|--------|
| 核心接口 | 6 | 0 | 6 | 100% |
| 持久化机制 | 6 | 0 | 6 | 100% |
| 计费逻辑 | 8 | 0 | 8 | 100% |
| 充值/回滚 | 4 | 0 | 4 | 100% |
| 分配/结算 | 3 | 0 | 3 | 100% |
| 监控/运维 | 4 | 0 | 4 | 100% |
| 高级功能 | 0 | 2 | 2 | 0% |
| **总计** | **31** | **2** | **33** | **94%** |

**总体完成度：约 94%**（从 81% 提升到 94%）

---

## 📁 完整架构文件结构

```
fapi/
├── FapiBalanceManager.java          # 经济模型核心（全功能版）
├── FapiCode.java                     # 错误码定义
├── FapiComponent.java                # 组件接口
├── AbstractFapiComponent.java        # 组件基类
├── ComponentRegistry.java            # 组件注册表
├── ServiceBootstrap.java             # 服务启动器
├── ServiceBootstrapConfig.java       # 启动配置
├── FapiDefaults.java                 # 默认值
├── components/
│   └── BaseComponent.java            # BASE组件
├── message/
│   ├── FapiRequest.java              # 请求格式
│   └── FapiResponse.java             # 响应格式
├── service/
│   └── FapiServer.java               # 服务主类
├── util/
│   └── ResponseBuilder.java          # 响应构建器
├── query/
│   ├── FcdslQueryExecutor.java       # FCDSL查询执行器
│   └── QueryResult.java              # 查询结果
├── rpc/
│   └── RpcOperationService.java      # RPC操作服务
├── monitor/
│   ├── AuditLogger.java              # 审计日志
│   ├── EconomicsMetricsCollector.java # 经济模型指标收集器（新增）
│   ├── HealthChecker.java            # 健康检查
│   ├── HealthStatus.java             # 健康状态
│   ├── MetricsCollector.java         # 指标收集
│   └── MetricsReport.java            # 指标报告
├── security/
│   ├── RequestValidator.java         # 请求验证
│   └── ValidationResult.java         # 验证结果
├── wal/
│   ├── WalEntry.java                 # WAL 条目
│   ├── WalEntryType.java             # 条目类型枚举
│   └── WalManager.java               # WAL 管理器
├── snapshot/
│   └── SnapshotManager.java          # 快照管理器
├── recharge/                         # 充值模块（新增）
│   ├── BlockStorageWatcher.java      # 区块存储监控
│   └── RechargeScanner.java          # 充值扫描器
├── settle/                           # 结算模块（新增）
│   └── ShareDistributor.java         # 分成分配器
└── client/
    ├── FapiClient.java               # 客户端
    └── BalanceVerifier.java          # 余额验证器（增强版）
```

---

## 📝 备注

1. **核心功能全部完成**：所有设计文档中的核心功能均已实现
2. **不向后兼容**：不再支持旧格式请求
3. **余额信息保证**：所有响应都填充余额信息
4. **代码质量**：代码结构清晰，模块化良好
5. **WAL/快照机制**：完整实现，支持启动恢复
6. **回滚处理**：支持 30 区块窗口的回滚检测和处理
7. **黑名单功能**：使用 PeerBalance.flags bit0 标记
8. **重传去重**：重传消息不重复计费
9. **分成分配**：完整实现 orderViaShare 和 consumeViaShare
10. **监控指标**：经济模型专用指标收集器

## 📋 新增 API 清单

### 充值监控

```java
// 创建区块存储监控器
BlockStorageWatcher watcher = new BlockStorageWatcher(listenPath, this::onNewBlock);
watcher.start();

// 创建充值扫描器
RechargeScanner scanner = new RechargeScanner(balanceManager, cashQueryFunction, serviceAddress);
scanner.startWithWatcher(listenPath);
```

### 分成分配

```java
// 创建分成分配器
ShareDistributor distributor = new ShareDistributor(orderViaShareBps, consumeViaShareBps);

// 计算分成
long orderShare = distributor.calculateOrderShare(amount, channelId);
long consumeShare = distributor.calculateConsumeShare(amount, channelId);

// 周期结算
DistributionResult result = distributor.distribute(totalIncome, totalSpend, channelId, stakeholders);
```

### 经济模型指标

```java
// 创建指标收集器
EconomicsMetricsCollector metrics = new EconomicsMetricsCollector();

// 记录各类指标
metrics.recordChargeSuccess(amount);
metrics.recordCreditExceeded();
metrics.recordSnapshot();

// 获取报告
EconomicsMetricsReport report = metrics.getReport();
```

### 客户端余额验证

```java
// 创建验证器
BalanceVerifier verifier = BalanceVerifier.fromSettings(settings);

// 设置本地价格
verifier.setPricePerKb(pricePerKb);

// 记录本地消费
verifier.recordLocalSpend(payloadBytes);

// 观察服务端余额
Result result = verifier.observe(serverBalance);

// 获取状态报告
VerifierStatus status = verifier.getStatus();
```
