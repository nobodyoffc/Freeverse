# FUDP Node 快速指南

## 概述

FUDP Node 是基于 FUDP 协议的应用层节点实现，提供加密的 P2P 通信能力。它构建在底层 FUDP 协议栈之上，为应用程序提供简洁的消息传递、请求/响应和文件传输接口。

### 核心特性

- ✅ **加密 P2P 通信** - 端到端加密，基于公钥身份认证
- ✅ **实时消息传递** - 支持聊天消息和消息确认
- ✅ **请求/响应模式** - 异步请求处理，支持超时
- ✅ **对等节点管理** - 持久化存储，自动地址更新
- ✅ **流式传输** - 基于 FUDP Stream 的多路复用
- ✅ **0-RTT 连接** - 首包即可携带数据，无需预握手

---

## 架构

```
┌─────────────────────────────────────────┐
│ 应用层 (Application Layer)              │
│   FudpNode, MessageHandler, ChatHandler │
│   (Chat, Request/Response)               │
├─────────────────────────────────────────┤
│ 协议层 (Protocol Layer)                 │
│   Protocol, Connection, Stream           │
│   (Encryption, ACK/Retransmit, Flow Ctrl)│
├─────────────────────────────────────────┤
│ UDP                                     │
└─────────────────────────────────────────┘
```

### 关键组件

- **FudpNode** - 主节点类，提供高级 API
- **Protocol** - 底层协议实现（连接、流、加密）
- **MessageHandler** - 消息路由和分发
- **PeerBook** - 对等节点存储和管理
- **MessageCodec** - 消息序列化/反序列化

---

## 已实现功能

### 1. 消息类型

| 类型 | 说明 | 状态 |
|------|------|------|
| `CHAT` | 文本消息 | ✅ 已实现 |
| `CHAT_ACK` | 消息确认 | ✅ 已实现 |
| `REQUEST` | 应用请求 | ✅ 已实现 |
| `RESPONSE` | 应用响应 | ✅ 已实现 |
| `ERROR` | 错误响应 | ✅ 已实现 |
| `PING` | 心跳检测 | ✅ 已实现 |
| `PONG` | 心跳响应 | ✅ 已实现 |
| `FILE_*` | 文件传输 | ⏳ 计划中 |
| `RELAY_*` | 消息中继 | ⏳ 计划中 |

### 2. 核心功能

- ✅ 节点启动/停止
- ✅ 对等节点添加/删除/列表
- ✅ 发送聊天消息（带/不带确认）
- ✅ 发送请求并等待响应
- ✅ 自动连接管理
- ✅ 消息去重
- ✅ 事件回调接口
- ✅ **性能监测** - RTT、丢包率、重传率统计

---

## 快速开始

### 1. 基本使用

```java
import fudp.node.*;
import core.crypto.KeyTools;

// 生成密钥对
byte[] privateKey = KeyTools.generatePrivateKey();

// 创建配置
NodeConfig config = new NodeConfig();
config.setPort(8400);
config.setDataDir("~/.fudp");

// 创建节点
FudpNode node = new FudpNode(privateKey, config);

// 设置事件监听器
node.setEventListener(new NodeEventListener() {
    @Override
    public void onChatReceived(String peerId, long messageId, String message) {
        System.out.println("[" + peerId.substring(0, 8) + "]: " + message);
    }

    @Override
    public void onRequestReceived(String peerId, long requestId, String serviceName, byte[] data) {
        // 处理请求并响应
        byte[] response = processRequest(serviceName, data);
        node.respond(requestId, response);
    }
});

// 启动节点
node.start();

// 添加对等节点
node.addPeer("FAbcd1234...", publicKey, "192.168.1.100", 8400, "alice");

// 发送消息
node.sendChat("FAbcd1234...", "Hello!");

// 发送带确认的消息
long msgId = node.sendChatWithAck("FAbcd1234...", "Important message");

// 发送请求
CompletableFuture<ResponseMessage> future = node.request(
    "FAbcd1234...", 
    "user.profile", 
    "{\"action\":\"get\"}".getBytes()
);
ResponseMessage response = future.get(30, TimeUnit.SECONDS);

// 停止节点
node.stop();
```

### 2. CLI 使用

```bash
# 启动节点（通过 StartFudpNode）
java -cp ... fudp.StartFudpNode

# 菜单选项：
# 1. Start Node - 启动节点
# 2. Stop Node - 停止节点
# 3. Node Status - 查看状态
# 4. Performance Stats - 性能统计 (RTT/丢包率/重传率)
# 5. Peer Management - 管理对等节点
# 6. Send Chat - 发送聊天消息
# 7. Ping Peer - 测试连接
# 8. Send Request - 发送请求
```

---

## 重启后的连接与会话恢复

当对端重启但继续复用相同 IP/port 时，需要处理以下情况：
- **会话清理**：收到 `ERROR_SESSION_NOT_FOUND`（code=1）时，仅清理 ACTIVE/DEPRECATED session，保留 PROPOSED，避免正在协商的新密钥被误删。
- **重放窗口重置**：收到 AsyTwoWay 携带的 `SYMKEY_PROPOSAL` 视为“对端重启”信号，会重置重放保护窗口，接受重新从 0 开始的包号。
- **连接状态重置**：同一信号下还会清空旧的流状态和 ACK 状态（包括已存在的 streamId/offset 记录），确保新的 `streamId=4`、`offset=0` 数据包能被应用层处理。
- **强制 AsyTwoWay 响应**：当收到 AsyTwoWay 数据且已有 ACTIVE 会话时，会临时开启 `forceAsyTwoWay`，直到新会话协商完成，避免旧密钥加密。

典型症状与处理：
- 看到日志 `[Protocol] Session not found...` + error code=1 且新消息丢失：检查是否触发了上述重置逻辑；升级到包含重置修复的版本。
- 收到 STREAM 包但应用层无消息：通常是旧流状态未重置导致 offset 不匹配，现已在“重启检测”时一并重置。

---

## 消息格式

### 应用消息帧格式

```
┌─────────────────────────────────────┐
│ Message Type (1 byte)               │
├─────────────────────────────────────┤
│ Message ID (8 bytes)                 │
├─────────────────────────────────────┤
│ Flags (1 byte)                      │
├─────────────────────────────────────┤
│ Payload Length (varint)             │
├─────────────────────────────────────┤
│ Payload (variable)                  │
└─────────────────────────────────────┘
```

### 标志位 (Flags)

- `Bit 0: NEED_ACK` - 需要确认
- `Bit 1: COMPRESSED` - 已压缩（未实现）
- `Bit 2: ENCRYPTED_APP` - 应用层加密（未实现）
- `Bit 3: FRAGMENTED` - 分片消息（未实现）

---

## 配置

### NodeConfig 主要参数

```java
NodeConfig config = new NodeConfig();
config.setPort(8400);                    // 监听端口
config.setDataDir("~/.fudp");           // 数据目录
config.setAddressCacheTtlMs(3600000);   // 地址缓存 TTL (1小时)
```

### 数据存储

- **对等节点信息**: `{dataDir}/{fid}_peers.json`
- **会话数据**: `{dataDir}/fudp_sessions.db` (LevelDB)
- **日志**: `{dataDir}/logs/`

---

## 事件接口

### NodeEventListener

```java
public interface NodeEventListener {
    // 连接事件
    void onPeerConnected(String peerId);
    void onPeerDisconnected(String peerId);

    // 消息事件
    void onChatReceived(String peerId, long messageId, String message);
    void onChatAck(String peerId, long messageId, long rttMs);  // 包含 RTT 测量

    // 请求事件
    void onRequestReceived(String peerId, long requestId, String serviceName, byte[] data);

    // 错误事件
    void onError(String peerId, int errorCode, String message);
}
```

---

## 设计决策

### 1. 分层架构

- **应用层** (`fudp.node.*`) - 高级 API，面向应用
- **消息层** (`fudp.message.*`) - 消息类型和编解码
- **处理层** (`fudp.handler.*`) - 消息路由和处理
- **协议层** (`fudp.*`) - 底层协议实现

### 2. 消息传输

- 每个应用消息使用独立的 Stream
- CHAT 消息：单向流，发送后关闭
- REQUEST/RESPONSE：双向流，响应后关闭
- 自动流管理和复用

### 3. 对等节点管理

- 基于 FID (Freecash ID) 识别
- 地址自动更新（从连接中学习）
- 持久化存储，支持别名
- 连接状态跟踪

### 4. 错误处理

- 消息去重（基于 Message ID）
- 自动重连（协议层）
- 超时处理（请求/响应）
- 错误回调通知

---

## 项目结构

```
FUDP/src/main/java/fudp/
├── node/                    # 节点核心
│   ├── FudpNode.java        # 主节点类
│   ├── NodeConfig.java      # 配置
│   ├── NodeStats.java       # 性能统计
│   ├── PeerBook.java        # 对等节点存储
│   ├── Peer.java            # 对等节点信息
│   └── NodeEventListener.java
├── message/                 # 消息协议
│   ├── MessageType.java     # 消息类型枚举
│   ├── MessageCodec.java    # 编解码
│   ├── AppMessage.java      # 基础消息类
│   ├── ChatMessage.java     # 聊天消息
│   ├── RequestMessage.java  # 请求消息
│   └── ResponseMessage.java # 响应消息
├── handler/                 # 消息处理
│   ├── MessageHandler.java  # 消息路由
│   └── ChatHandler.java     # 聊天处理
├── Protocol.java            # 协议层
├── connection/               # 连接管理
├── stream/                  # 流管理
├── session/                 # 会话管理
└── crypto/                  # 加密管理
```

---

## 限制和待实现功能

### 当前限制

- ❌ 文件传输功能未实现
- ❌ 消息中继功能未实现
- ❌ NAT 穿透未实现
- ❌ 消息压缩未实现
- ❌ 广播消息未实现

### 计划功能

- ⏳ 文件传输（分块、恢复、完整性验证）
- ⏳ 消息中继（多跳路由、费用机制）
- ⏳ NAT 穿透（STUN/TURN）
- ⏳ 消息压缩（gzip）
- ⏳ 广播消息

---

## 常见问题

### Q: 如何获取本地 FID？

```java
String localFid = node.getLocalFid();
```

### Q: 如何检查节点是否运行？

```java
if (node.isRunning()) {
    // 节点正在运行
}
```

### Q: 如何列出所有对等节点？

```java
List<Peer> peers = node.listPeers();
for (Peer peer : peers) {
    System.out.println(peer.getDisplayName() + " - " + peer.getState());
}
```

### Q: 如何处理请求超时？

```java
CompletableFuture<ResponseMessage> future = node.request(...);
try {
    ResponseMessage response = future.get(30, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    // 处理超时
}
```

### Q: 如何获取性能统计？

```java
// 获取节点整体统计
NodeStats stats = node.getNodeStats();
System.out.println("RTT: " + stats.getAvgSmoothedRttMs() + "ms");
System.out.println("Loss: " + stats.getLossRatePercent());
System.out.println("Retransmit: " + stats.getRetransmitRatePercent());

// 获取单个 Peer 统计
NodeStats.PeerStats peerStats = node.getPeerStats("FAbcd1234...");
if (peerStats != null) {
    System.out.println(peerStats.toString());
}
```

### Q: 如何进行 Ping 测试并获取 RTT？

```java
// 发送 ping 并等待 pong
CompletableFuture<PongMessage> future = node.pingAwaitPong("FAbcd1234...", false, 5000);
try {
    PongMessage pong = future.get(5, TimeUnit.SECONDS);
    long rtt = pong.calculateRtt();
    System.out.println("RTT: " + rtt + "ms");
} catch (TimeoutException e) {
    System.out.println("Ping timeout");
}
```

---

## 相关文档

- [FUDP 协议概述](P2P_PROTOCOL_OVERVIEW.md) - 底层协议详细说明
- [FUDP Node 实现计划](FUDP_NODE_IMPLEMENTATION_PLAN.md) - 完整实现计划（详细版）
- [FUDP 性能监测指南](FUDP_PERFORMANCE_MONITORING.md) - RTT/丢包率/重传率监测

---

*最后更新: 2025-12-21*
