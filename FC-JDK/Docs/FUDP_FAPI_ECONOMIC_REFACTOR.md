# FUDP ↔ FAPI 经济模型重构方案

## 目标
- **职责分离**：FUDP 仅负责加密、可靠传输与计量，不包含余额/扣费/信用逻辑。
- **灵活经济模型**：FAPI（或其他上层）自定义计费策略，复用传输计量数据。
- **无兼容负担**：当前未上线，可直接调整协议/接口以简化。

## 设计原则
- 传输层只上报事实数据（peerId、方向、类型、payload 大小、时延/重传等），不上决策。
- 经济模型可插拔，作为上层模块；传输层不解析/填充 balance 字段。
- 保持可观测性：保留/增强审计与指标，但仅记录传输指标。
- 逐步切换：先引入 FAPI 侧经济层，再删除 FUDP 中的经济代码。

## 阶段规划
1. **Phase 1：FUDP 去耦与接口收敛（进行中）**
   - 去掉 FUDP 对经济层的硬依赖（BalanceManager/EconomicsService 报文字段、信用/黑名单耦合）。
   - 在收发路径增加可订阅的计量事件：`peerId, streamId, messageType, direction, bytesIn/Out, ts, rtt, retransmits, lossHint`。
   - 文档更新：`P2P_PROTOCOL_DESIGN.md` 改为“纯传输+计量”，`FUDP_NODE_IMPLEMENTATION_PLAN.md` 将 economics 目录标注为“上层插件”。

2. **Phase 2：FAPI 经济模型封装**
   - 在 FAPI 创建 `economics/`（或 `billing/`）模块，定义 `EconomicsService` 接口，输入 `MeterRecord`，输出计费/信用决策。
   - 适配 `handlers.AccountManager` 为 FAPI 版：去掉 APIP/HTTP 依赖，保留收入、分发、黑名单/信用逻辑。
   - FAPI 请求链路接入：收到 FUDP 计量事件 → 经济服务计费 → 结果写入 FAPI 响应或元数据。

3. **Phase 3：菜单与 CLI 迁移**
   - `fapi/menu/BalanceIncomeMenu` 仅操作 FAPI 经济层，不再引用 `fudp.economics.*`。
   - 复用/改写 `AccountManager` 的菜单逻辑（Redis/本地 DB 兼容）。

4. **Phase 4：数据模型与持久化**
   - 经济相关索引/本地 DB key 迁移到 FAPI 命名空间。
   - 明确双余额模型（服务者权威）为 FAPI 默认策略，计费范围由 FAPI 配置。

5. **Phase 5：测试与回归**
   - FUDP：传输回归（聊天/请求响应/文件），验证计量事件准确性。
   - FAPI：计费/余额/信用/分发单测与端到端扣费场景。
   - 文档同步：`FAPI_IMPLEMENTATION_PLAN.md` 移除“FUDP Balance 系统自动填充”描述。

6. **Phase 6：切换与清理**
   - 在 FAPI 经济层稳定后，删除 FUDP 经济代码与配置项；提供迁移说明。

## Phase 1 当期任务
- 盘点 FUDP 经济耦合点：代码、配置、报文字段。
- 删除/熔断经济字段解析与默认经济服务；保证纯传输路径可用。
- 加入计量事件接口（仅上报数据，不决策）。
- 更新相关设计文档。

## 参考
- 现有经济逻辑：`APIP/ApipManager/src/main/java/startAPIP/StartApipManager.java`、`FC-JDK/src/main/java/handlers/AccountManager.java`
- 设计文档：`Docs/P2P_PROTOCOL_DESIGN.md`、`Docs/FUDP_NODE_IMPLEMENTATION_PLAN.md`、`Docs/FUDP_ACCOUNT_PLAN.md`、`Docs/FAPI_IMPLEMENTATION_PLAN.md`
