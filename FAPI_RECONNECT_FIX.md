# FAPI 客户端重连问题修复

## 问题描述

当 FAPI 客户端断开后重新连接服务端时，会出现以下问题：
1. 客户端报错：`Failed to connect FAPI endpoint 127.0.0.1:9000: null`
2. 从服务端日志看，连接实际上是成功的（PING 和请求都被正确处理）
3. 但客户端初始化阶段认为连接失败

## 根本原因

1. **配置不完整**：第一次连接时，虽然保存了 `ApiProvider` 和 `ApiAccount`，但没有完整保存 `Service` 对象的详细信息
2. **错误信息不清晰**：当连接失败时，异常的 `getMessage()` 可能返回 null，导致错误信息为 "Failed to connect ... : null"
3. **日志不足**：缺少详细的调试日志来诊断连接过程

## 修复内容

### 1. 增强日志记录 (`ClientGroup.java`)

在 `connectFapiAccount` 方法中添加了详细的调试日志：
- 记录快速路径（fast path）的尝试和结果
- 记录完整发现流程的每个步骤
- 记录服务选择和客户端创建
- 改进错误消息，当 `getMessage()` 为 null 时显示异常类型

### 2. 自动更新配置 (`ClientGroup.java`)

当通过完整发现流程成功连接后，自动更新 `ApiProvider` 的 `service` 字段：
```java
// Update provider with full service info for future fast-path reconnects
if (provider.getService() == null) {
    log.debug("connectFapiAccount: updating provider with service info for future fast-path");
    provider.setService(service);
    provider.setApiParams(Params.getParamsFromService(service, Params.class));
    provider.setDealerPubkey(Hex.toHex(discovery.getPublicKey()));
    Configure.saveConfig();
}
```

### 3. 改进错误处理

在所有连接方法中（`ClientGroup.java`, `Settings.java`, `StartFapiClient.java`）：
- 捕获异常时显示更多信息（异常类型、堆栈跟踪）
- 确保错误消息始终有意义（使用 `getClass().getSimpleName()` 作为后备）

## 测试步骤

### 1. 启动服务端

```bash
cd /Users/liuchangyong/Desktop/Freeverse/FC-JDK
# 编译（如果需要）
mvn clean package -DskipTests

# 运行 FAPI Server
java -cp target/FC-SDK.jar fapi.StartFapiServer
```

记录服务端的：
- Peer FID
- Service SID  
- 端口号（默认 9000）

### 2. 第一次客户端连接

```bash
# 在新终端
java -cp target/FC-SDK.jar fapi.StartFapiClient
```

操作：
1. 选择或创建 FID
2. 当提示配置 FAPI 服务时，输入服务端的 host:port
3. 验证连接成功
4. 测试几个 API（如 bestHeight, ping 等）
5. 退出客户端（注意：保持服务端运行）

### 3. 第二次客户端连接（关键测试）

```bash
# 在同一终端重新运行
java -cp target/FC-SDK.jar fapi.StartFapiClient
```

**预期行为（修复后）：**
1. 客户端应该能够自动重连到已配置的服务端
2. 终端应该显示详细的连接日志：
   ```
   Initiate FAPI accounts and clients...
   connectFapiAccount: skipping fast path for 127.0.0.1:9000 (pubkey=present, service=null)
   connectFapiAccount: starting full discovery for 127.0.0.1:9000
   connectFapiAccount: discovery completed for 127.0.0.1:9000, peerId=FTxMo6wbWR9G9Poac84d4jHaVUNfViALVD, services=1
   connectFapiAccount: selected service SID=b4b621be..., name=FAPI Linux Test
   connectFapiAccount: successfully connected to FAPI endpoint 127.0.0.1:9000
   ```
3. 连接成功后，配置会自动更新，下次重连将使用快速路径

### 4. 第三次客户端连接（验证快速路径）

```bash
# 再次退出并重新运行
java -cp target/FC-SDK.jar fapi.StartFapiClient
```

**预期行为：**
1. 这次应该使用快速路径（因为配置已完整）
2. 日志应该显示：
   ```
   connectFapiAccount: trying fast path for 127.0.0.1:9000 (pubkey present, service present)
   connectFapiAccount: fast path succeeded for 127.0.0.1:9000
   ```
3. 连接应该更快（跳过了 HELLO 步骤）

## 日志级别配置

为了看到详细的调试日志，确保 `logback.xml` 或日志配置中包含：

```xml
<logger name="clients.ClientGroup" level="DEBUG"/>
<logger name="config.Settings" level="DEBUG"/>
<logger name="fapi.client.FapiClient" level="DEBUG"/>
```

## 配置文件位置

配置保存在：
- `/Users/liuchangyong/Desktop/Freeverse/config/config.json`

重连问题修复后，`apiProviderMap` 中的 FAPI provider 应该包含完整的 `service` 对象。

## 验证点

1. ✅ 第一次连接成功
2. ✅ 第二次连接成功（不再显示 "Failed to connect ... : null"）
3. ✅ 服务端日志显示请求被正确处理
4. ✅ 客户端日志显示详细的连接过程
5. ✅ 配置文件自动更新包含完整的 service 信息
6. ✅ 第三次连接使用快速路径

## 故障排除

如果问题仍然存在：

1. **检查端口号**：确认配置文件中的端口与服务端实际端口一致
2. **检查服务端状态**：确认服务端正在运行且 PONG 包含服务信息
3. **查看详细日志**：设置 DEBUG 级别查看完整的连接流程
4. **清理配置**：如果配置损坏，删除对应的 ApiAccount 和 ApiProvider，让客户端重新发现

## 相关文件

- `FC-JDK/src/main/java/clients/ClientGroup.java` - 客户端组连接逻辑
- `FC-JDK/src/main/java/config/Settings.java` - 设置和连接管理
- `FC-JDK/src/main/java/fapi/StartFapiClient.java` - 客户端启动类
- `FC-JDK/src/main/java/fapi/client/FapiClient.java` - FAPI 客户端核心
- `config/config.json` - 配置文件


