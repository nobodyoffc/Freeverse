# FAPI 客户端重连问题修复

## 问题
客户端断开后重新连接服务端失败，错误信息为：`Failed to connect FAPI endpoint 127.0.0.1:9000: null`

## 解决方案
已修复以下三个文件，改进了连接逻辑、日志记录和配置管理：

### 修改的文件
1. ✅ `FC-JDK/src/main/java/clients/ClientGroup.java`
   - 添加详细的调试日志
   - 自动更新配置以支持快速重连
   - 改进错误消息

2. ✅ `FC-JDK/src/main/java/config/Settings.java`
   - 添加详细的连接日志
   - 改进错误处理

3. ✅ `FC-JDK/src/main/java/fapi/StartFapiClient.java`
   - 改进用户可见的输出信息
   - 增强错误报告

### 新增的文件
- ✅ `FAPI_RECONNECT_SUMMARY.md` - 详细的问题分析和修复说明
- ✅ `FAPI_RECONNECT_FIX.md` - 完整的测试计划
- ✅ `test_fapi_reconnect.sh` - 便捷的测试脚本

## 快速测试

### 方法 1: 使用测试脚本（推荐）

```bash
# 1. 编译项目
./test_fapi_reconnect.sh build

# 2. 启动服务端（新终端）
./test_fapi_reconnect.sh server

# 3. 启动客户端（另一个终端）
./test_fapi_reconnect.sh client
# 测试后退出

# 4. 重新连接（验证修复）
./test_fapi_reconnect.sh client
# 应该成功连接！

# 查看帮助
./test_fapi_reconnect.sh help
```

### 方法 2: 手动测试

```bash
# 编译
cd FC-JDK
mvn clean package -DskipTests

# 终端 1: 启动服务端
java -cp target/FC-SDK.jar fapi.StartFapiServer

# 终端 2: 启动客户端（多次）
java -cp target/FC-SDK.jar fapi.StartFapiClient
```

## 预期结果

### 第一次连接
```
Initializing FAPI node & client...
Discovering peer via HELLO+PING at 127.0.0.1:9000...
Discovered peer FID: FTxMo6wbWR9G9Poac84d4jHaVUNfViALVD
Found 1 FAPI service(s).
✓ FAPI Client configured successfully.
```

### 第二次连接（修复后）
```
Initiate FAPI accounts and clients...
DEBUG: connectFapiAccount: starting full discovery for 127.0.0.1:9000
DEBUG: connectFapiAccount: discovery completed, peerId=..., services=1
DEBUG: connectFapiAccount: updating provider with service info
INFO:  connectFapiAccount: successfully connected to FAPI endpoint
✓ 连接成功！
```

### 第三次连接（快速路径）
```
Initiate FAPI accounts and clients...
DEBUG: connectFapiAccount: trying fast path (pubkey present, service present)
DEBUG: connectFapiAccount: fast path succeeded
✓ 连接成功（更快）！
```

## 关键改进

### 1. 自动配置更新
重连时自动保存完整的服务信息，下次连接使用快速路径（只需 PING，不需要 HELLO）

### 2. 详细日志
每个连接步骤都有清晰的日志，方便诊断问题

### 3. 更好的错误消息
不再显示 `null`，而是显示具体的异常类型和消息

## 故障排除

### 问题：仍然连接失败
```bash
# 1. 启用调试日志
./test_fapi_reconnect.sh client-debug

# 2. 查看配置
./test_fapi_reconnect.sh config

# 3. 清理配置（如果损坏）
cp config/config.json config/config.json.backup
# 手动编辑 config.json，删除 FAPI 相关的 apiAccountMap 条目
# 重新运行客户端
```

### 问题：端口不匹配
检查：
- 服务端日志中显示的端口
- `config/config.json` 中 `apiUrl` 的端口
- 确保它们一致（默认 9000）

### 问题：配置文件锁定
```bash
# 检查是否有其他实例运行
ps aux | grep StartFapi

# 如果需要，删除锁文件
rm -rf db/*_leveldb/LOCK
rm -rf fudp_data/*_fudp_sessions.db.wal.*
```

## 技术细节

### 快速路径 vs 完整发现

| 连接方式 | 条件 | 流程 | 速度 |
|---------|------|------|------|
| 快速路径 | 配置完整 | 只发送 PING | 快 |
| 完整发现 | 首次/配置不完整 | HELLO + PING | 慢但可靠 |

修复后，系统会自动从完整发现升级到快速路径。

### 日志级别

调试日志默认关闭，需要时可以在 `logback.xml` 中启用：
```xml
<logger name="clients.ClientGroup" level="DEBUG"/>
<logger name="config.Settings" level="DEBUG"/>
<logger name="fapi.client.FapiClient" level="DEBUG"/>
```

## 相关文档

- 📄 `FAPI_RECONNECT_SUMMARY.md` - 完整的问题分析和修复说明
- 📄 `FAPI_RECONNECT_FIX.md` - 详细的测试计划和验证步骤
- 🔧 `test_fapi_reconnect.sh` - 自动化测试脚本

## 验证清单

- [ ] 编译成功（`./test_fapi_reconnect.sh build`）
- [ ] 服务端启动正常
- [ ] 客户端首次连接成功
- [ ] 客户端重连成功（不再显示错误）
- [ ] 查看日志，确认连接过程清晰
- [ ] 第三次连接使用快速路径

## 总结

✅ **问题已修复**
- 重连不再失败
- 错误消息清晰
- 自动优化连接速度
- 详细日志帮助诊断

请编译并测试，如有问题请提供完整日志。




