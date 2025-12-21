# FAPI 及其他 FUDP 上层服务经济模型设计方案

## 范围与目标
- 适用于 FAPI 及基于 FUDP/Freeverse 生态的其他上层 API 服务。
- FUDP 仅提供加密/可靠传输与计量，经济模型完全在上层实现。
- 提供可验证、可恢复、低耦合的计费/结算能力，支持多种上链数据源。

## 术语与身份
- peerId 与 userId 等同，均为 FID（freecash 公钥派生身份）；在 fudp 层标记为 peerId，在 fapi 层标记为 userId。

## 落地快速清单
- 服务启动前必须拿到 pricePerKB/orderViaShare/consumeViaShare，缺失或 <0 直接拒绝启动；启动自检包含键格式校验与目录可写性。
- 所有金额按“聪”存 long，分成比例按万分比整数（basis points，0-10000）存储，计算中使用 long，必要时放大到 1/10000 聪再向下取整，禁止 double 漂移。
- 关键幂等键：充值用 cashId，扣费用 requestKey，结算用 cycleId；重复且参数冲突时返回 ALREADY_EXISTS_WITH_CONFLICT 并写审计。
- 账本写入必须在单个 WriteBatch 中完成余额、幂等标记、审计，失败后整体回滚；fsync 策略可聚合但不能跳过。
- 启动恢复顺序固定：加载快照 → 重放 WAL → 重放审计（可选）；无法验证哈希或版本不兼容时中止启动。
- 运行时监控：bestHeight 单调性、WAL/审计 CRC、余额/信用告警、LISTEN_PATH 延迟；超过阈值可触发“冻结入账/拒绝服务”保护开关。

## 全局约束与参数表
- 金额：long，单位聪，0 ≤ amount ≤ 10000000000000（1e13），否则返回 `INVALID_AMOUNT` 并写审计；价格/分成计算后向下取整到 long，禁止负值和溢出。
- 键校验：`peerId/userId/requestKey/cashId` 仅允许 `[a-zA-Z0-9._:-]`，长度 ≤128；非法或超长返回 `INVALID_KEY`。`requestKey` 冲突且参数不一致返回 `ALREADY_EXISTS_WITH_CONFLICT`（文档化选项），`cashId` 冲突且金额/用户不一致拒绝并告警但不冻结账户。
- meta 大小：扣费附带的 `meta` 序列化后 ≤2KB，超出拒绝并写审计。
- pricePerKB/orderViaShare/consumeViaShare：仅在启动或菜单手动刷新链上 Service 信息后更新；加载失败中止启动；运行中不自动拉取；手动更新成功即刻生效。`pricePerKB <0` 或缺失中止服务，=0 则全免费。
- 回滚/补扫窗口：统一为 30 区块（检测、补扫、回滚）。充值记录保留 100 天，超窗口仅保留审计。
- 审计/快照目录固定，权限仅限服务账号；WAL/审计损坏时向后截断到最后一条有效记录并写告警。

## 核心组件
- **BalanceManager（新实现，LevelDB/KV）**
  - 按 peerId 记录余额、序列号、信用额度、黑名单标记。
  - 原子接口：`checkAndCharge(meter)`、`charge(requestKey, peerId, amount, meta)`、`credit(userId, cashId, amount, src, status)`、`settle(cycleId)`、`getBalance(peerId)`。
  - 存储模型与事务：
    - KV key：`B/<peerId>`（余额/信用/序列号）、`R/cash/<cashId>`（入账状态）、`R/req/<requestKey>`（扣费幂等）、`M/meta`（bestHeight/bestBlockId/审计 tip）。
    - 一次业务操作（同一 cashId 或 requestKey）在单个 WriteBatch 中落盘，包含余额更新、幂等标记、审计记录，保证原子性。
    - WAL（Write-Ahead Log，先写顺序日志再刷表）+ append-only 审计日志，定期快照（余额 map 摘要 + bestHeight/blockId + 审计 prevHash）；启动恢复顺序：加载快照 → 重放 WAL → 重放审计（可选）。
    - 审计日志滚动时保留 prevHash tip；避免长日志导致重放过长。
  - 线程安全：推荐单线程序列器或 peer 粒度串行；多线程需保证同 peer 序列化，避免死锁。
- **Pricing/Policy**
  - pricePerKB、免费消息类型列表、信用额度、黑名单策略。
  - 单余额模式（服务商权威），不再提供双余额；对外返回的 balance 必须源自这一权威账本，可被客户端作为可信计费/对账依据，服务端需要保证与内部存储一致。
- **Meter 输入**
  - 来自 FUDP 的 `MeterRecord`（peerId、方向、类型、payloadBytes、ts、rtt、重传），上层根据方向/类型/大小计费或豁免。
- **计费公式（补充）**
  - 仅对配置为计费的方向/类型计费；免费列表优先于其他规则。
  - 字节计费基数：`payloadBytes`（取 FUDP 上报的 on-wire payload 长度，含业务加密/压缩后的实际负载，不含 FUDP 头）；重传按首次计费，重传字节不重复计费。
  - 价格：`charge = ceil(payloadBytes / 1024.0) * pricePerKB`，向上取整到 KB；结果单位聪，向下取整到 long，金额需 ≥0。
  - 若 `pricePerKB` 未设置或 <0，报错并终止服务，=0则全部免费。
  - `rtt` 仅做观测，不影响计费。
  - 计费伪代码示例：
    ```pseudo
    def checkAndCharge(meter):
      if invalid_key(meter.peerId): return INVALID_KEY
      if meter.payloadBytes < 0: return INVALID_AMOUNT
      if meter.type in freeList or pricePerKB == 0: return OK, 0
      kb = ceil(meter.payloadBytes / 1024.0)
      charge = kb * pricePerKB
      // 重传只计首包：依赖 meter.isRetransmit 标记或上层去重
      return charge(requestKey = meter.id, peerId = meter.peerId, amount = charge, meta = meter.meta)
    ```
  - Meter 字段示例：`{peerId, direction(up/down), type(msg/ack/control), payloadBytes(int), ts(ms), rtt(ms, optional), isRetransmit(bool), meta(optional≤2KB)}`
- **Recharge/Income Pipeline**
  - 监听链上/索引数据，更新余额或生成入账事件。

## 响应与客户端余额校验（新增）
- 服务端响应体：`FapiResponse` 显式包含 `balance`（Long，可为 null；服务端内部账本余额，单位聪）及可选 `balanceSeq`（Long，可为 null，幂等/回放序列号）。在 `ResponseBuilder` 内从 `BalanceManager.getBalance(peerId)` 读取权威值；读取失败返回 `null` 并告警，但不阻塞业务响应。服务完全免费或关闭计费时可返回 null。
- `bestHeight` 反映链上数据，`balance` 来源于内部账本，不要求同事务同步（余额变动更频繁，链上高度约 1 分钟变一次；回滚仅影响充值部分，不回滚消费）。错误响应也要带上当前 `bestHeight` 和可用的 `balance`，便于客户端对账。
- 客户端接收：`FapiClient` 反序列化后缓存最近的 `balance/bestHeight/balanceSeq`。交互 CLI（StartFapiClient）在查询完成后展示最新余额及获取时间，便于人工核对。
- 余额验真器：新增轻量 `BalanceVerifier`（客户端侧），对比“预期余额”与服务端权威余额，公式 `abs(serverBalance - expectedBalance) > tolerance` 触发漂移事件；连续或累积超阈值按策略停用。
  - 预期余额来源：本地 pricePerKB + FUDP `MeterRecord` 计量推导消费（仅对计费方向/类型）；若无法获得计量，仅记录单点对账。
  - 策略：支持累积偏差；记录最近 N 次漂移；`warn` 仅告警，`stop` 暂停发送请求或关闭 FUDP 节点，需打印明确提示。
- 参数配置（放入 `config.Settings`，默认保守，支持菜单/热更新）：
  - 阈值形式：支持“百分比阈值 + 绝对值下限”以兼容不同 pricePerKB；如 `balanceTolerancePct`（默认 2%）与 `balanceToleranceSatMin`（默认 10_000 聪）取较大者作为告警阈值。
  - 累积/终止：`balanceDriftAccumPct`/`balanceDriftAccumSat` 达到阈值触发一次报警；`balanceDriftStopPct`/`balanceDriftStopSat` 达到终止阈值则停用服务；可辅以 `balanceMaxConsecutiveDrift` 控制连续超阈值。
  - 动作：`balanceDriftAction`：`log|warn|stop`，默认 `warn`。
  - 展示：`balanceDisplayPrecision`（CLI 显示精度，可选）。
  - 服务端：`logBalanceReadError`（布尔），控制余额读取失败是否告警。

## 计量一致性（客户端 vs 服务端）
- 客户端计费推导必须与服务端一致：相同的计量输入（FUDP `MeterRecord`）、相同的计费方向/类型、相同的 KB 取整规则（`ceil(payloadBytes/1024.0)`）和 `pricePerKB`。
- 推荐复用同一计费函数或公共工具类，避免双实现漂移；若无法复用，应以测试用例对同一批 meter 数据做对比，确保本地“预期余额”与服务端计算一致。

## 参数与默认值（建议配置表）
- `pricePerKB`：链上 Service 提供；仅在服务启动和菜单手动修改时刷新，启动加载失败中止启动；手动刷新成功后立即生效。
- `orderViaShare / consumeViaShare`：同上，启动或手动修改时生效。
- `creditLimit`：配置文件加载（聪），热更新后新请求立即执行。
- `rollbackWindow`：30 区块（freecash 链重组保护，节点不接受超过 30 区块的回滚）。
- `sharePrecision`：分成比例以万分比整数（0-10000）保存，计算时使用 long；需要更高精度时放大到 1/10000 聪再向下取整，避免浮点误差。
- `snapshotInterval`、`maxWALLength`、`auditRetention`：  
  - `snapshotInterval`：余额+meta 快照周期，建议“时间 10 分钟或累计 10 万次事务/2GB 余额映射写入”二选一触发，上限 30 分钟强制快照。  
  - `maxWALLength`：单个 WAL 文件最大长度，默认 128MB，超过 256MB 触发滚动并做快照，硬上限 512MB。  
  - `auditRetention`：审计日志保留策略，默认保留最近 3 个滚动文件。
- `fsyncInterval`：批量 fsync 周期。

## 数据源与优先级
- **bestBlock（bestHeight, bestBlockId）**
  1. ES block 索引（FAPI 服务需要以 ES 作为可信源）。
  2. FAPI 服务提供的区块数据接口（非 FAPI 服务以已经运行的FAPI服务为可信链上数据源）。
  - 需校验高度单调、哈希连续，避免回滚污染。
- **充值/支付获取来源（无 mempool）**
  - FAPI服务监听 LISTEN_PATH（本地区块存储目录）文件变动，表示有新区块落盘；此时主动向 ES 拉取自上次 bestHeight 后的新 cash 列表。非FAPI服务订阅FAPI服务的newCashAlert服务，获取新收入通知（如果数据源大于1，则查询其他源验证）入账。充值需 1 个区块确认才入账。
  - 本地保存100天（100*24*60个区块）内的充值记录：`(cashId, userId, amount, height, blockId)`。
  - 每 30 区块做回滚检查：获取之前保存的检查点height 的 blockId是否与保存值一致，若不一致视为回滚。一致则更新检查点height和blockId为最新值。
  - 发生回滚时，不修改已确认入账记录，而是追加“冲减回滚充值”的新记录（状态 `reverted`/审计 `adjust`）扣回余额；重扫回滚高度向前 30 个区块范围内的充值并重新记账；30 区块系 freecash 的重组保护阈值。最后更新检查点height和blockId。

## 安全性与一致性
- 传输层没有经济模型，所有经济状态由上层存储维护。
- 幂等与去重：以 cashId（txid+index 的哈希，参考 Cash.makeCashId，mempool utxo/txo 同规则）作为唯一键；入账/扣费都基于 cashId 去重。
- 原子性：计费与余额扣减在同一 KV 事务或批次中完成；若失败，外部应重试或拒绝服务。
- 防回滚：记录 bestHeight/bestBlockId，支持回滚后重扫；若检测到回滚（同高度 blockId 变化），对相关确认入账追加冲减记录（不直接修改原记录），重扫充值，余额为负但未超信用时可继续服务，否则拒绝。
- 审计：可选启用 append-only 审计日志（建议行级 JSON，含 hash 链 prevHash + 当前记录哈希，字段见下）；充值、分配、回滚必须记录；消费扣费默认不逐笔写审计（量太大），仅写计数/聚合指标，必要时可配置按采样或阈值写详细行。
- 黑名单/信用：由上层配置，BalanceManager 执行；避免在传输层做拒绝。

## 性能与效率
- LevelDB/嵌入式 KV，批量写（WriteBatch）+ 顺序日志，避免 Redis/ES 运行时依赖。
- 读取走内存缓存（可选 LRU）+ 定期快照，降低磁盘 I/O。
- 定期压缩/截断审计日志，避免文件膨胀；配置最大 WAL 长度/快照周期以控制恢复时间。

## 模型简化与扩展
- 单余额模型：不采用双余额，余额 = 服务商视角的可用金额，信用额度/黑名单可选。
- 服务商购买其他服务的余额由 `config/ApiAccount.java` 缓存，按请求从响应中更新；为其他用户提供服务时在 response 中写入用户 balance（提示用，上层余额以 AccountManager 记录为准）。
- 支出模块：支持“购买其他服务”扣费，按ApiProvider的pricePerKB计价，按minPayment配置基于minPayment的乘数付费。
- 分成策略：
  - 充值渠道：收到订单时按 `orderViaShare` 比例记账（确认/待确认分别标记）。
  - 消费渠道：消费时按 `consumeViaShare` 比例记账；若消费额很小（如 1 聪），分成可能 <1 聪，提高内部精度为万分之一聪，并在实际发放时向下取整到聪，余数累计到下次分配；避免 long 溢出和负值。
- 分配模块：在配置的结算周期内，核算收入，先发放充值分成、消费分成，再扣减成本，剩余按股份比例分配利润。

## 状态机与返回约定
- `credit`：`pending -> confirmed -> reverted`（回滚或手动调整）；幂等键 cashId；回滚时写审计 `adjust`。
- `charge/checkAndCharge`：幂等键 requestKey；返回码需区分 `OK`、`ALREADY_EXISTS`、`INSUFFICIENT_BALANCE_BUT_WITHIN_CREDIT`、`CREDIT_EXCEEDED`、`INVALID_AMOUNT`、`ERROR`。
- `settle(cycleId)`：幂等键 cycleId，状态 `pending -> confirmed`；失败可重试，重试写幂等。
- 审计写入顺序：单批次中先写余额更新，再写幂等标记，最后写审计行，保证重放一致。
- 错误码映射：如果使用 HTTP，推荐 200 + body.code；内部 gRPC/接口沿用上述枚举，禁止复用 `ERROR` 代表业务校验问题。
- 返回体需附带当前余额、信用额度剩余、bestHeight，便于客户端自检同步；charge/checkAndCharge 的冲突类错误需回传原幂等记录的 peerId/amount 供排查（不含 meta 原文）。

## 边界与拒绝策略
- 金额必须为非负 long，单位聪；>10000000000000 或溢出直接拒绝并写审计错误。
- `requestKey/cashId` 长度和字符集需限制（写入前校验），非法时返回 `INVALID_KEY`。
- pricePerKB/Share 缺失或 <0 时：中止启动或拒绝服务；=0 时为免费/分配额为0。
- 黑名单或信用超限时拒绝服务并计数告警。

## 线程模型与写入/恢复
- 默认单线程事件循环处理计费/入账；如需多线程，按 peerId 粒度串行队列，禁止跨队列共享同一 peer 的批次。
- WriteBatch 包含余额、幂等键、审计，按配置的 fsyncInterval 刷盘；失败后外部可重试。
- 快照包含：余额 map 摘要（哈希）、bestHeight、bestBlockId、最新审计 prevHash tip；启动顺序：快照 → WAL → 审计重放。
- WAL、审计、快照路径需固定目录，权限只写给服务账号；滚动策略与最大 WAL 长度需配置。
- 并发控制：`B/<peerId>` 中的 `seq` 在每次写入时 +1，作为 Compare-And-Set 保护；跨线程写同一 peerId 需要拿到最新 seq 后再写，否则拒绝并重试单 peer 队列。

## 监听/重扫/重试
- LISTEN_PATH 触发失败时按配置的重试间隔重试，记录最大落后高度；超过阈值告警并可选“冻结入账”。LISTEN_PATH 监听的是区块链本地区块存储目录的文件变化。
- 回滚或主备切换后，对最近 30 高度区间的 cash 进行重扫和差异比对；重扫操作幂等写审计 `adjust`。
- 回滚/重扫伪代码（窗口固定 30 区块）：
  ```pseudo
  def handleNewBlock(height, blockId):
    if height <= meta.bestHeight and blockId != meta.bestBlockId:
      rollbackFrom = height
      for cash in credits within [height-30, height]: append adjust(-amount)
      rescan(height-30, meta.bestHeight)
    meta.bestHeight = height; meta.bestBlockId = blockId

  def rescan(start, end):
    for h in [start..end]:
      for cash in cashList(h):
        credit(cash.userId, cash.cashId, cash.amount, src="rescan", status="confirmed")
  ```

## 审计日志建议（简洁、稳定）
- 行级 JSON，每行一个事件，字段：
  - `ts`(ms), `type`(`income|spend|settle|adjust`), `cashId`, `peerId`, `amount`, `currency`(默认 satoshi/FCH), `status`(`pending|confirmed|reverted`), `bestHeight`, `bestBlockId`, `note`(可选), `prevHash`, `hash`.
  - `hash = SHA256(prevHash || canonical_json_without_hash)`，`prevHash` 初始可为全零或创世常量。
- 保留策略：定期滚动文件（按大小/时间），保留最近 N 个文件；快照固化最新 `prevHash` 以便校验；滚动时记录跨文件 prevHash。

## 多币种预留
- 当前仅支持 satoshi/FCH。预留字段 `currency`，未来支持多币种时需引入汇率源/固定计价单位，并在计费/分配时保持金额单位一致。

## 配置与价格源
- pricePerKB、orderViaShare、consumeViaShare 由链上 `Service`（ES 或 FAPI 获取）提供，在服务启动或手动操作时刷新；启动获取失败中止启动，运行中不自动刷新；防止被篡改，可用clientGroup提供多数据源校验。
- 按 sid 链上获取 Service 参数的通道：FAPI 服务直接请求 ES 的 service 索引；非 FAPI 服务通过 FAPI 的 `entityByIds` 获取 service 实体，再解析参数。
- creditLimit 默认从配置文件加载（聪为单位），启动或手动更新后生效；允许余额为负但不可超过信用额度，超过则拒绝服务。
- FAPI服务配置 LISTEN_PATH ，其变动触发查询 ES 是否有新的充值。

## 数据源可信与多源校验
- Settings/ClientGroup 支持多源（ES/FAPI）；一个主源，其余用于交叉验证和备用，检测高度/哈希不一致时告警或切换；切换与补扫策略见下文“多源数据校验设计”。

## 防滥用与告警
- 短时间大量用户超过信用被拒绝时触发告警/限流，可临时禁止非正余额用户访问。

## 接口与并发/持久化
- BalanceManager 对外接口（幂等）：  
  - `credit(userId, cashId, amount, src, status)`：入账，幂等键为 cashId（全局唯一）；`userId`/`peerId` 指明记账对象，可校验 `cash.issuer` 等匹配。  
  - `charge(requestKey, peerId, amount, meta)`：扣费，requestKey 稳定且全局唯一（推荐 `messageId|peerId|serviceId` 或业务请求唯一 ID），防重复扣费；返回值需区分“余额不足但未超信用”（可提示充值/继续）与“超信用拒绝”（需停止服务）。  
  - `checkAndCharge(meter)`：基于计量扣费，返回同样的信用区分错误码。  
  - 如支出生成新的 cash：先以 requestKey 预扣/幂等，待链上生成 cash 后以 cashId 二次去重确认（链上查询或客户端发起充值确认请求）。  
  - `settle(cycleId)`：周期分配（幂等，基于 cycleId 去重）。  
  - 返回值需区分：已存在/成功/失败/信用不足。

## 数据模型与键/值格式（补充）
- `B/<peerId>`（余额主记录，值为定长或版本化结构）：
  - 字段：`balance`(long, 聪，可负)、`creditLimit`(long)、`seq`(long, 单调+1)、`flags`(int，bit0=blacklist，预留)、`version`(int)。
  - 更新规则：同一 peer 写入需按 `seq` 序列化；多版本时向后兼容旧版字段默认为 0。
- `R/req/<requestKey>`（扣费幂等）：
  - 字段：`peerId`、`amount`、`metaHash`（对 meta 做 sha256 以限制大小）、`status`(`ok|failed|insufficient|credit_exceeded`)、`ts`。
  - 重复 requestKey 且 `peerId/amount` 不一致时返回 `ALREADY_EXISTS_WITH_CONFLICT` 并写审计。
  - requestKey 会存入持久化 KV 用于幂等；推荐使用 `messageId|peerId|serviceId` 的全长字符串保证唯一性并保存在审计中。
- `R/cash/<cashId>`（收入幂等/回滚状态）：
  - 字段：`userId`、`amount`、`src`、`status`(`pending|confirmed|reverted`)、`height`、`blockId`、`ts`。
  - 回滚时将状态置 `reverted` 并写审计 `adjust`；若同 cashId 不同金额再次出现，拒绝并告警。
- `M/meta`：
  - 字段：`bestHeight`、`bestBlockId`、`auditTipHash`、`snapshotVersion`、`lastSnapshotHeight`。
- 审计行（canonical JSON，字段顺序固定）：`ts,type,cashId,requestKey,peerId,amount,currency,status,bestHeight,bestBlockId,note,prevHash,hash`；hash 按定义计算，空字段写空字符串。

## 输入校验（补充）
- `peerId/userId/requestKey/cashId` 仅允许 `[a-zA-Z0-9._:-]`，长度 ≤128；超长或非法字符返回 `INVALID_KEY`。
- `meta`（扣费附带元信息）序列化后大小 ≤2KB；超出截断或拒绝并写审计。
- 金额校验沿用：非负、≤10000000000000，超出返回 `INVALID_AMOUNT`。
- bestHeight 必须单调递增；同高 blockId 变更即触发回滚流程。

## 状态机边界规则（补充）
- `cashId` 重新出现但金额/用户不一致：拒绝入账并写告警审计；保留原记录为 `reverted` 不变。
- `requestKey` 重复且 `peerId/amount` 不同：返回 `ALREADY_EXISTS_WITH_CONFLICT` 并写审计，不更新余额。
- 回滚期间：若 `creditLimit` 或黑名单已更新，重放时按最新配置执行；负余额但未超信用可继续服务，超信用立即拒绝。
- pending `credit` 在回滚窗口被撤销时，余额扣回后状态置 `reverted`，并允许后续同 cashId 重新入账。

## WAL/快照/审计运维参数（补充）
- WAL 轮转：按大小（默认 256MB）或时间（6h）滚动；超过阈值立即生成快照并截断旧 WAL。
- fsync 策略：默认按批次 fsync；可配置 `fsyncIntervalMs`（如 50ms）聚合多批写。
- 快照格式：版本化 JSON 或定长二进制，包含 `version`、`bestHeight`、`bestBlockId`、`auditTipHash`、余额摘要（对全部 `B/<peerId>` 记录按 peerId 排序计算 Merkle/哈希）；启动校验版本，不兼容时拒绝启动并提示迁移。
- 破损检测：WAL/审计行按行校验 hash/CRC；遇到半行或 CRC 失败时截断到最后一条有效记录并写告警，同时将截断点的 `prevHash` 作为新的审计 tip 写入快照。
- 审计轮转：按大小（默认 128MB）或时间（日/周）滚动；保留最近 N（默认 10）个文件，跨文件 prevHash 需衔接。
- 并发模型：推荐单线程事件循环或 peer 粒度串行；如用锁，需防止死锁，写入使用批量。
- 持久化：WAL 顺序追加，fsync 策略明确（如每 N ms 或每批）；定期快照（写入 bestHeight/hash、余额映射摘要），启动时先加载快照再重放 WAL。
- 回滚：检测到同高度 blockId 变化时，对相关确认入账追加冲减记录并生成审计调整，重扫充值；若余额仍负但未超信用，继续服务，否则拒绝。
- 启动/恢复流程（实施版）：
  1. 校验配置文件、目录权限、键格式、限额合法性；加载链上 Service 价格与分成，未取到直接失败。
  2. 加载最近快照，校验版本/哈希；重放 WAL，遇到损坏按最后有效行截断并告警；如启用审计重放，则继续重放审计链。
  3. 初始化内存缓存与 peer 队列，开始监听 LISTEN_PATH 与计费输入；拉取 ES/FAPI 的 bestBlock，写入 meta。
  4. 启动后定期执行快照、WAL 轮转、审计轮转与健康检查；任何自检失败可选择“只读模式”（暂停扣费/入账）并告警。

## 消费/充值幂等与精度
- 收入去重键：`cashId`。  
- 支出幂等键：`requestKey`（如 requestId+peerId/service 或业务 id），避免重复扣费。  
- 数值类型：统一 long，金额单位聪；上限 10000000000000，超出拒绝。计费与分配向下取整；内部精度累积到 1 聪再落地，余额累计超过 1000 聪时触发实际发放（余数继续累积）。  
- 分成内部精度：计算时提升为万分之一聪的整数，按账号累计；当累计 ≥1 聪时落地到余额并写审计，<1 聪的余数按账号保留供下次累加，避免长期偏差或负值。
- 分成/支出预扣：报价或 pricePerKB 变化时，如扣费失败需回滚预扣并记录审计。

## 价格与数据源校验
- 数据源数量可配置：  
  - 若仅 1 个源：简化策略，直接使用，不做跨源校验。  
  - 若 >1 个源：主/备校验高度、blockId、pricePerKB 等；不一致时告警，按策略降级/切换。
- 对来自链上 Service 的参数变更：仅在启动或菜单手动触发时读取并验证（签名或多源一致性）；读取失败或参数非法中止启动；手动刷新成功后对新请求立即生效。

## 多源数据校验设计
- 配置主/备数据源（ES/FAPI），定义切换阈值：主源落后 ≥K 高度（默认 3，可配置）或 blockId 不连续即告警并切换到最新且哈希连续的备源；切回条件为主源连续 N 个高度（默认 10）与备源一致。
- 校验维度：`bestHeight/bestBlockId` 连续性、`pricePerKB/orderViaShare/consumeViaShare` 参数一致性、充值 cash 列表差异。差异写审计并可选“冻结入账”直至一致。
- 补扫策略：切换或回滚后，对最近 M 高度区间（固定 30 区块）的 cash 重新比对并回放，确保余额一致；审计记录使用 `adjust` 说明来源。
- 双写可选：使用备源时仍周期性检查主源以评估恢复，校验通过后输出“恢复事件”并可切回。

## 审计与验证
- 行级 JSON 哈希链（唯一格式）：`ts,type,cashId,requestKey,peerId,amount,currency,status,bestHeight,bestBlockId,note,prevHash,hash`。
- `hash = SHA256(prevHash || canonical_json_without_hash)`，`prevHash` 初始为全零或常量。
- 跨文件衔接：上一文件末尾 hash 作为下一文件首条 prevHash；定期输出 hash tip（可签名存档）。
- 校验工具：提供命令示例（如 `python audit_check.py --path audit.log`）校验连续性和哈希链。
- 访问控制：审计文件只读权限，避免被篡改。

## 滥用防护与信用耗尽
- 当大量超信用拒绝出现时，触发告警/限流，临时拒绝非正余额用户或提高信用前需人工确认。
- 信用耗尽前可提前告警/提示充值，避免服务闪断。

## 迁移与复用原则
- 不复用 `handlers/AccountManager`、`IncomeManager`、`Manager` 现有实现，仅参考其经济规则（双余额、信用、分发）。
- 新的 BalanceManager 需：
  - 无 CLI/Menu 交互。
  - 无 Redis/ES/HTTP 客户端硬依赖。
  - 明确线程模型和错误处理。
  - 对接 FUDP 计量事件和上链充值管道。

## 分配与备用金
- 分配周期配置在 `config/Settings.java` 的 `settingMap`，默认 10 天（10*24*60 区块），可动态调整。
- 预留不参与分配的备用金，默认 1 FCH，用于必要支出。
- 分配生成的交易需通过 ES 或 FAPI 查询 txId 确认；失败时报警，并将应发金额计入对应 fid 余额，在下次分配时优先支付。

## 监控与运维
- 指标：扣费失败率、信用拒绝次数、数据源高度差、快照滞后、审计校验失败、LISTEN_PATH 落后高度、fsync/批次耗时。
- 告警阈值需配置；主备切换/冻结入账/回滚事件需写审计并通知。
- 目录布局：LevelDB、WAL、审计、快照分目录存放；审计只读，备份/归档策略明确。
- 热更新流程：pricePerKB/Share 仅启动或菜单修改；creditLimit 允许运行时更新并立即生效。

## 测试与演练
- 恢复演练：在 WriteBatch 落盘中断后重启，验证快照+WAL 重放一致性。
- 回滚演练：制造同高 blockId 变化，触发 30 区块回滚、扣回余额、重扫充值。
- 主备切换演练：主源落后/不连续时切换备源，恢复后切回；验证补扫和审计。
- 精度与溢出演练：小额累积到 1 聪后落地；超额/负额/非法 key 触发拒绝。

## 开放问题/待决策
- 计价单位和汇率来源（是否固定 satoshi/FCH，是否支持多币种）。
- 监控与告警指标（扣费失败率、信用拒绝次数、数据源高度差、快照滞后、审计校验失败）与阈值。
- LISTEN_PATH 触发失败的重试间隔、最大落后高度、冻结入账策略和告警方式。
- 运维流程：目录结构、备份/恢复脚本、热更新 pricePerKB/Share 的操作手册。
