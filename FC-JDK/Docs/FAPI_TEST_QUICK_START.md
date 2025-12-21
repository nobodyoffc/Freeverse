# FAPI 测试快速开始指南

## 前置条件

1. **服务端 (FAPI Manager) 已启动**
   - 运行 `StartFapiManager`
   - 确认显示连接信息：
     ```
     Peer FID: FTxMo6wbWR9G9Poac84d4jHaVUNfViALVD
     Public Key (hex): 037d81362d2e91e5766b047fba488b5d2f44212eb314bf9039659f4da0004a8186
     Service SID: b4b621be5865ba21dc1731afd5df2f4aa47109f0e2dad3ca4fbf73298ff1aa20
     Host: 127.0.0.1
     Port: 8500
     ```

2. **Elasticsearch 已运行并包含数据**
   - 确认 ES 中有 Block、Tx、Cid、Cash、Service 等索引数据

3. **客户端 (FAPI Client) 准备就绪**
   - 准备运行 `StartFapiClient`

## 快速测试流程

### 步骤 1: 启动并配置客户端

1. 启动 `StartFapiClient`
2. 选择菜单: **Settings** -> **Configure Service**
3. 依次输入：
   ```
   Host: 127.0.0.1
   Port: 8500
   ```
   客户端会通过 HELLO 获取公钥，通过带 `WANT_PONG_INFO` 的 PING 自动发现 FAPI 服务 SID。
4. 确认输出中包含“Discovered peer FID”与“Using service SID”，并显示 "FAPI Client configured successfully"
5. 选择 **Settings** -> **Show Current Config** 验证配置

### 步骤 2: 基础连接测试

1. 选择 **Basic APIs** -> **ping**
   - 应该自动使用配置的服务 peer ID
   - 确认显示 "Ping sent to FTxMo6wbWR9G9Poac84d4jHaVUNfViALVD"

### 步骤 3: 基础 API 测试

1. **bestBlock**
   - 选择 **Basic APIs** -> **bestBlock**
   - 确认返回最新的 Block 对象
   - 记录返回的 Block ID 和 Height

2. **bestHeight**
   - 选择 **Basic APIs** -> **bestHeight**
   - 确认返回有效的区块高度
   - 验证高度与 bestBlock 返回的一致

### 步骤 4: 区块链查询测试

1. **blockSearch**
   - 选择 **Blockchain** -> **blockSearch**
   - 使用默认参数（直接按 Enter）
   - 确认返回 Block 列表（最多 20 条）
   - 记录几个 Block IDs 用于后续测试

2. **blockByIds**
   - 选择 **Blockchain** -> **blockByIds**
   - 输入刚才记录的 Block IDs（用逗号分隔）
   - 确认返回对应的 Block 对象

3. **txSearch**
   - 选择 **Blockchain** -> **txSearch**
   - 使用默认参数
   - 确认返回 Tx 列表
   - 记录几个 Tx IDs

4. **txByIds**
   - 选择 **Blockchain** -> **txByIds**
   - 输入刚才记录的 Tx IDs
   - 确认返回对应的 Tx 对象

5. **cashSearch** 和 **cashByIds**（类似步骤）

### 步骤 5: 身份查询测试

1. **freerSearch**
   - 选择 **Identity** -> **freerSearch**
   - 使用默认参数
   - 确认返回 Freer 列表

2. **freerByIds**
   - 选择 **Identity** -> **freerByIds**
   - 输入服务端 FID: `FTxMo6wbWR9G9Poac84d4jHaVUNfViALVD`
   - 确认返回对应的 Freer 对象

### 步骤 6: 服务查询测试

1. **serviceSearch**
   - 选择 **Service** -> **serviceSearch**
   - 使用默认参数
   - 确认返回 Service 列表
   - 验证能找到当前 FAPI 服务

2. **serviceByIds**
   - 选择 **Service** -> **serviceByIds**
   - 输入当前 FAPI 服务的 SID: `b4b621be5865ba21dc1731afd5df2f4aa47109f0e2dad3ca4fbf73298ff1aa20`
   - 确认返回对应的 Service 对象
   - 验证 Service.types 包含 "FAPI"

### 步骤 7: 通用查询测试

1. 选择 **General Query**
2. 输入索引名称: `Block`
3. 输入 FCDSL 查询条件（或使用默认）
4. 确认返回查询结果

### 步骤 8: 错误处理测试

1. **未配置服务测试**
   - 重新启动客户端（不配置服务）
   - 尝试执行 bestBlock
   - 确认显示 "FAPI Client not configured."

2. **无效服务 ID 测试**
   - 配置服务时使用无效的 SID
   - 尝试执行查询
   - 确认返回错误信息

## 常见问题排查

### 问题 1: 服务配置失败
**症状**: 显示 "Failed to configure FAPI Client"

**可能原因**:
- 服务端未启动
- 网络连接问题
- HELLO/PING 超时，或服务端未在 PONG data 中返回服务信息

**解决方法**:
1. 确认服务端已启动并显示连接信息
2. 检查防火墙设置，确保 HELLO/PING 能到达服务端
3. 在服务端控制台确认已显示 “Clients can now bootstrap with only host:port...”
4. 重试 **Settings -> Configure Service**，观察是否能打印 FID 与 SID

### 问题 2: 查询返回 null 或错误
**症状**: 查询操作返回 null 或错误信息

**可能原因**:
- Elasticsearch 未运行
- ES 中没有数据
- 服务端未正确初始化

**解决方法**:
1. 检查 Elasticsearch 是否运行
2. 检查 ES 中是否有相应的索引数据
3. 查看服务端日志
4. 确认服务端已正确加载服务

### 问题 3: 连接超时
**症状**: 请求超时或连接失败

**可能原因**:
- 网络问题
- 服务端未响应
- FUDP 节点未正确连接

**解决方法**:
1. 检查服务端是否正常运行
2. 确认端口 8500 未被占用
3. 检查网络连接
4. 查看服务端和客户端日志

### 问题 4: 服务未找到
**症状**: 返回 "Service not found or type mismatch"

**可能原因**:
- 服务端 PONG data 未包含 FAPI 服务
- Service 未在 ES 中注册
- Service.types 不包含 "FAPI"

**解决方法**:
1. 在服务端选择 "Update services" 重新加载服务并确保 FAPI 服务存在
2. 确认 PING 请求带上 WANT_PONG_INFO（客户端自动处理）
3. 检查 ES 中是否有对应的 Service 记录
4. 验证 Service.types 包含 "FAPI"

## 测试检查清单

快速验证所有功能是否正常：

```
基础功能:
[ ] 服务配置成功
[ ] Ping 成功
[ ] bestBlock 返回数据
[ ] bestHeight 返回数据

区块链查询:
[ ] blockSearch 返回数据
[ ] blockByIds 返回数据
[ ] txSearch 返回数据
[ ] txByIds 返回数据
[ ] cashSearch 返回数据
[ ] cashByIds 返回数据

身份查询:
[ ] freerSearch 返回数据
[ ] freerByIds 返回数据

服务查询:
[ ] serviceSearch 返回数据
[ ] serviceByIds 返回数据（包含当前 FAPI 服务）

通用查询:
[ ] General Query 正常工作

错误处理:
[ ] 未配置服务时正确提示
[ ] 无效服务 ID 时正确错误处理
```

## 下一步

完成快速测试后，请参考 `FAPI_TEST_PLAN.md` 进行更详细的测试。
