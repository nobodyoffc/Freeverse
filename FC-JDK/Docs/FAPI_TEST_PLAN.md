# FAPI 测试计划

## 测试环境信息

### 服务端 (FAPI Manager)
- **Peer FID**: FTxMo6wbWR9G9Poac84d4jHaVUNfViALVD
- **Public Key (hex)**: 037d81362d2e91e5766b047fba488b5d2f44212eb314bf9039659f4da0004a8186
- **Service SID**: b4b621be5865ba21dc1731afd5df2f4aa47109f0e2dad3ca4fbf73298ff1aa20
- **Host**: 127.0.0.1
- **Port**: 8500
- **Password**: 750228

### 客户端 (FAPI Client)
- **Peer FID**: FEk41Kqjar45fLDriztUDTUkdki7mmcjWK
- **Public Key (hex)**: 030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a
- **Password**: 750228

## 测试阶段

### Phase 1: 基础连接测试
验证客户端和服务端之间的 FUDP 连接和基本通信。

#### 1.1 服务配置测试
**目标**: 验证客户端能够正确配置服务连接信息

**步骤**:
1. 启动 FAPI Client (`StartFapiClient`)
2. 进入 "Settings" -> "Configure Service"
3. 输入服务端信息:
   - Public Key: `037d81362d2e91e5766b047fba488b5d2f44212eb314bf9039659f4da0004a8186`
   - Service SID: `b4b621be5865ba21dc1731afd5df2f4aa47109f0e2dad3ca4fbf73298ff1aa20`
   - Host: `127.0.0.1`
   - Port: `8500`

**预期结果**:
- ✅ 客户端成功计算并显示 Peer FID: `FTxMo6wbWR9G9Poac84d4jHaVUNfViALVD`
- ✅ 客户端成功添加 peer 到 FUDP node
- ✅ 客户端显示 "FAPI Client configured successfully"
- ✅ "Show Current Config" 显示正确的配置信息

#### 1.2 Ping 测试
**目标**: 验证 FUDP 节点之间的连通性

**步骤**:
1. 在客户端选择 "Basic APIs" -> "ping"
2. 验证是否自动使用配置的服务 peer ID

**预期结果**:
- ✅ 如果服务已配置，自动使用服务 peer ID
- ✅ 如果服务未配置，提示输入 peer FID
- ✅ Ping 成功发送，无错误信息

### Phase 2: 基础 API 测试
测试最基本的查询功能。

#### 2.1 bestBlock 测试
**目标**: 验证获取最佳区块功能

**步骤**:
1. 在客户端选择 "Basic APIs" -> "bestBlock"
2. 检查返回结果

**预期结果**:
- ✅ 返回最新的 Block 对象
- ✅ Block 包含有效的字段（id, height, time, etc.）
- ✅ 响应 JSON 格式正确
- ✅ 无错误信息

**验证点**:
- Block.id 不为空
- Block.height 是有效的数字
- Block.time 是有效的时间戳

#### 2.2 bestHeight 测试
**目标**: 验证获取最佳区块高度功能

**步骤**:
1. 在客户端选择 "Basic APIs" -> "bestHeight"
2. 检查返回结果

**预期结果**:
- ✅ 返回有效的区块高度（Long 类型）
- ✅ 高度值大于 0
- ✅ 无错误信息

**验证点**:
- 返回的高度与 bestBlock 返回的 Block.height 一致

### Phase 3: 区块链查询测试
测试 Block、Tx、Cash 相关的查询功能。

#### 3.1 blockSearch 测试
**目标**: 验证区块搜索功能

**步骤**:
1. 在客户端选择 "Blockchain" -> "blockSearch"
2. 使用默认参数（size=20, sort="height:desc->id:asc"）
3. 检查返回结果

**预期结果**:
- ✅ 返回 Block 列表
- ✅ 列表大小不超过 20
- ✅ 按高度降序排列
- ✅ 响应包含 got, total, last 等分页信息
- ✅ bestHeight 字段存在且有效

**验证点**:
- 返回的 blocks 按 height 降序排列
- 第一个 block 的 height 最高
- 如果 total > got，说明有更多数据

#### 3.2 blockByIds 测试
**目标**: 验证通过 ID 查询区块功能

**步骤**:
1. 先执行 blockSearch 获取一些 block IDs
2. 在客户端选择 "Blockchain" -> "blockByIds"
3. 输入 1-3 个 block IDs（用逗号分隔）
4. 检查返回结果

**预期结果**:
- ✅ 返回 Map<String, Block>
- ✅ Map 的 key 是 block ID
- ✅ 返回的 block 数量与输入的 ID 数量一致（如果 ID 存在）
- ✅ 每个 block 的 id 与输入的 ID 匹配

**测试用例**:
- 测试单个 ID
- 测试多个 ID（2-3 个）
- 测试不存在的 ID（应返回空或 null）

#### 3.3 txSearch 测试
**目标**: 验证交易搜索功能

**步骤**:
1. 在客户端选择 "Blockchain" -> "txSearch"
2. 使用默认参数（size=20, sort="height:desc->id:asc"）
3. 检查返回结果

**预期结果**:
- ✅ 返回 Tx 列表
- ✅ 列表大小不超过 20
- ✅ 按高度降序排列
- ✅ 响应包含分页信息

**验证点**:
- 返回的 transactions 按 height 降序排列
- 每个 tx 包含有效的字段（id, height, time, etc.）

#### 3.4 txByIds 测试
**目标**: 验证通过 ID 查询交易功能

**步骤**:
1. 先执行 txSearch 获取一些 tx IDs
2. 在客户端选择 "Blockchain" -> "txByIds"
3. 输入 1-3 个 tx IDs（用逗号分隔）
4. 检查返回结果

**预期结果**:
- ✅ 返回 Map<String, Tx>
- ✅ Map 的 key 是 tx ID
- ✅ 返回的 tx 数量与输入的 ID 数量一致（如果 ID 存在）

#### 3.5 cashSearch 测试
**目标**: 验证 Cash (UTXO) 搜索功能

**步骤**:
1. 在客户端选择 "Blockchain" -> "cashSearch"
2. 使用默认参数（size=20, sort="valid:desc->birthHeight:desc->id:asc"）
3. 检查返回结果

**预期结果**:
- ✅ 返回 Cash 列表
- ✅ 列表大小不超过 20
- ✅ 响应包含分页信息

**验证点**:
- 每个 cash 包含有效的字段（id, owner, value, valid, etc.）

#### 3.6 cashByIds 测试
**目标**: 验证通过 ID 查询 Cash 功能

**步骤**:
1. 先执行 cashSearch 获取一些 cash IDs
2. 在客户端选择 "Blockchain" -> "cashByIds"
3. 输入 1-3 个 cash IDs（用逗号分隔）
4. 检查返回结果

**预期结果**:
- ✅ 返回 Map<String, Cash>
- ✅ Map 的 key 是 cash ID
- ✅ 返回的 cash 数量与输入的 ID 数量一致（如果 ID 存在）

### Phase 4: 身份查询测试
测试 Freer (CID) 相关的查询功能。

#### 4.1 freerSearch 测试
**目标**: 验证身份搜索功能

**步骤**:
1. 在客户端选择 "Identity" -> "freerSearch"
2. 使用默认参数（size=20, sort="nameTime:desc->id:asc"）
3. 检查返回结果

**预期结果**:
- ✅ 返回 Freer 列表
- ✅ 列表大小不超过 20
- ✅ 响应包含分页信息

**验证点**:
- 每个 freer 包含有效的字段（id, fid, name, balance, etc.）

#### 4.2 freerByIds 测试
**目标**: 验证通过 ID 查询身份功能

**步骤**:
1. 在客户端选择 "Identity" -> "freerByIds"
2. 输入已知的 FID（如服务端的 FID: `FTxMo6wbWR9G9Poac84d4jHaVUNfViALVD`）
3. 检查返回结果

**预期结果**:
- ✅ 返回 Map<String, Freer>
- ✅ Map 的 key 是 FID
- ✅ 返回的 freer 包含正确的信息

**测试用例**:
- 测试服务端 FID
- 测试客户端 FID
- 测试不存在的 FID

### Phase 5: 服务查询测试
测试 Service 相关的查询功能。

#### 5.1 serviceSearch 测试
**目标**: 验证服务搜索功能

**步骤**:
1. 在客户端选择 "Service" -> "serviceSearch"
2. 使用默认参数（size=20, sort="active:desc->tRate:desc->id:asc"）
3. 检查返回结果

**预期结果**:
- ✅ 返回 Service 列表
- ✅ 列表大小不超过 20
- ✅ 响应包含分页信息

**验证点**:
- 每个 service 包含有效的字段（id, stdName, types, dealer, etc.）
- 应该能找到当前 FAPI 服务（SID: `b4b621be5865ba21dc1731afd5df2f4aa47109f0e2dad3ca4fbf73298ff1aa20`）

#### 5.2 serviceByIds 测试
**目标**: 验证通过 ID 查询服务功能

**步骤**:
1. 在客户端选择 "Service" -> "serviceByIds"
2. 输入当前 FAPI 服务的 SID: `b4b621be5865ba21dc1731afd5df2f4aa47109f0e2dad3ca4fbf73298ff1aa20`
3. 检查返回结果

**预期结果**:
- ✅ 返回 Map<String, Service>
- ✅ Map 的 key 是 Service ID
- ✅ 返回的 service 包含正确的信息
- ✅ Service.types 包含 "FAPI"
- ✅ Service.dealer 是服务端的 FID

### Phase 6: 通用查询测试
测试 General Query 功能，验证 FCDSL 查询的灵活性。

#### 6.1 自定义索引查询测试
**目标**: 验证通过自定义索引名称查询功能

**步骤**:
1. 在客户端选择 "General Query"
2. 输入索引名称（如 "Block", "Tx", "Cid", "Cash"）
3. 输入 FCDSL 查询条件
4. 检查返回结果

**预期结果**:
- ✅ 返回查询结果
- ✅ 响应格式正确
- ✅ 支持各种 FCDSL 查询条件（terms, match, range, etc.）

**测试用例**:
- 测试 Block 索引查询
- 测试 Tx 索引查询
- 测试 Cid 索引查询
- 测试 Cash 索引查询

### Phase 7: 错误处理测试
测试各种错误场景的处理。

#### 7.1 服务未配置测试
**目标**: 验证未配置服务时的错误处理

**步骤**:
1. 启动客户端但不配置服务
2. 尝试执行任何查询操作（如 bestBlock）

**预期结果**:
- ✅ 显示 "FAPI Client not configured."
- ✅ 提示用户配置服务
- ✅ 程序不崩溃

#### 7.2 无效服务 ID 测试
**目标**: 验证使用无效服务 ID 时的错误处理

**步骤**:
1. 配置服务时使用无效的 Service SID
2. 尝试执行查询操作

**预期结果**:
- ✅ 服务端返回错误响应（STATUS_NOT_FOUND）
- ✅ 错误消息包含 "Service not found or type mismatch"
- ✅ 客户端正确处理错误

#### 7.3 无效 FCDSL 测试
**目标**: 验证使用无效 FCDSL 时的错误处理

**步骤**:
1. 在 General Query 中输入无效的 FCDSL
2. 检查错误处理

**预期结果**:
- ✅ 服务端返回错误响应（STATUS_BAD_REQUEST）
- ✅ 错误消息包含 "Invalid FCDSL" 或类似信息
- ✅ 客户端显示错误信息

#### 7.4 超时测试
**目标**: 验证请求超时处理

**步骤**:
1. 执行一个可能超时的查询（如查询大量数据）
2. 检查超时处理

**预期结果**:
- ✅ 如果超时，返回超时错误（408）
- ✅ 错误消息包含 "Request timeout"
- ✅ 客户端正确处理超时

### Phase 8: 性能测试
测试查询性能和并发处理能力。

#### 8.1 单次查询性能测试
**目标**: 验证单次查询的响应时间

**步骤**:
1. 执行各种查询操作
2. 记录响应时间

**预期结果**:
- ✅ 简单查询（如 bestBlock）响应时间 < 1 秒
- ✅ 搜索查询响应时间 < 3 秒
- ✅ IDs 查询响应时间 < 2 秒

#### 8.2 并发查询测试
**目标**: 验证并发查询处理能力

**步骤**:
1. 同时执行多个查询操作
2. 检查所有查询是否都能正常完成

**预期结果**:
- ✅ 所有并发查询都能正常完成
- ✅ 响应时间在可接受范围内
- ✅ 无错误或崩溃

### Phase 9: 集成测试
测试完整的端到端流程。

#### 9.1 完整查询流程测试
**目标**: 验证完整的查询流程

**步骤**:
1. 配置服务
2. 执行一系列查询操作（bestBlock -> blockSearch -> blockByIds -> txSearch -> ...）
3. 检查所有操作是否正常

**预期结果**:
- ✅ 所有查询操作都能正常完成
- ✅ 数据一致性正确（如 blockSearch 和 blockByIds 返回的数据一致）
- ✅ 无内存泄漏或资源问题

#### 9.2 服务端重启测试
**目标**: 验证服务端重启后的连接恢复

**步骤**:
1. 配置并连接服务
2. 执行一些查询操作
3. 重启服务端
4. 等待服务端恢复
5. 再次执行查询操作

**预期结果**:
- ✅ 服务端重启后，客户端能够自动重连（如果实现了重连机制）
- ✅ 或客户端能够检测到连接断开并提示用户
- ✅ 重新连接后查询功能正常

## 测试检查清单

### 基础功能
- [ ] 服务配置成功
- [ ] Ping 功能正常
- [ ] bestBlock 返回有效数据
- [ ] bestHeight 返回有效数据

### 区块链查询
- [ ] blockSearch 返回有效数据
- [ ] blockByIds 返回有效数据
- [ ] txSearch 返回有效数据
- [ ] txByIds 返回有效数据
- [ ] cashSearch 返回有效数据
- [ ] cashByIds 返回有效数据

### 身份查询
- [ ] freerSearch 返回有效数据
- [ ] freerByIds 返回有效数据

### 服务查询
- [ ] serviceSearch 返回有效数据
- [ ] serviceByIds 返回有效数据（包括当前 FAPI 服务）

### 通用查询
- [ ] General Query 支持自定义索引
- [ ] General Query 支持各种 FCDSL 条件

### 错误处理
- [ ] 未配置服务时正确提示
- [ ] 无效服务 ID 时正确错误处理
- [ ] 无效 FCDSL 时正确错误处理
- [ ] 超时情况正确处理

### 性能
- [ ] 查询响应时间在可接受范围内
- [ ] 并发查询正常处理

### 集成
- [ ] 完整流程测试通过
- [ ] 服务端重启后连接恢复正常

## 测试执行顺序建议

1. **Phase 1**: 基础连接测试（必须先通过）
2. **Phase 2**: 基础 API 测试（验证基本功能）
3. **Phase 3**: 区块链查询测试（核心功能）
4. **Phase 4**: 身份查询测试
5. **Phase 5**: 服务查询测试
6. **Phase 6**: 通用查询测试
7. **Phase 7**: 错误处理测试
8. **Phase 8**: 性能测试（可选）
9. **Phase 9**: 集成测试

## 测试数据准备

### 需要的测试数据
1. **已知的 Block IDs**: 从 blockSearch 获取
2. **已知的 Tx IDs**: 从 txSearch 获取
3. **已知的 Cash IDs**: 从 cashSearch 获取
4. **已知的 FIDs**: 
   - 服务端: `FTxMo6wbWR9G9Poac84d4jHaVUNfViALVD`
   - 客户端: `FEk41Kqjar45fLDriztUDTUkdki7mmcjWK`
5. **已知的 Service SID**: `b4b621be5865ba21dc1731afd5df2f4aa47109f0e2dad3ca4fbf73298ff1aa20`

### 测试环境要求
1. **Elasticsearch**: 必须运行并包含测试数据
2. **FUDP Node**: 服务端和客户端都必须运行
3. **网络**: 服务端和客户端能够互相通信（127.0.0.1:8500）

## 问题记录模板

在测试过程中，如果发现问题，请记录以下信息：

```
**问题描述**: 
**测试阶段**: Phase X.X
**复现步骤**: 
1. 
2. 
3. 
**预期结果**: 
**实际结果**: 
**错误信息**: 
**环境信息**: 
- 服务端版本: 
- 客户端版本: 
- 操作系统: 
**截图/日志**: 
```

## 测试报告模板

测试完成后，请填写以下报告：

```
**测试日期**: 
**测试人员**: 
**测试环境**: 
- 服务端: 
- 客户端: 
- Elasticsearch: 

**测试结果汇总**:
- 总测试用例数: 
- 通过: 
- 失败: 
- 跳过: 

**主要发现**:
1. 
2. 
3. 

**建议**:
1. 
2. 
3. 
```

