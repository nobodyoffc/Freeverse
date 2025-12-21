# FUDP 协议概述

## 1. 简介

FUDP（P2P 无握手对等加密传输协议）是一个基于 UDP 的对等加密传输协议，具有以下核心特性：

- **0-RTT 数据传输**：首包即可携带应用数据，无需预握手
- **完全对等**：节点间无客户端/服务端区别
- **端到端加密**：支持公钥加密和对称密钥加密的动态切换
- **身份即公钥**：使用公钥作为节点身份，无需 CA 证书体系
- **可靠传输**：提供 ACK、重传、流控制、拥塞控制
- **地址透明**：身份基于公钥而非 IP，自动适应地址变化

## 2. 协议架构

```
┌─────────────────────────────────────┐
│ 应用层 (Application Layer)          │
├─────────────────────────────────────┤
│ 加密层 (Crypto Layer)                │
│ - 公钥加密/解密 (ECDH + AES-GCM)     │
│ - 对称密钥管理                       │
├─────────────────────────────────────┤
│ 传输层 (Transport Layer)             │
│ - 可靠传输 (ACK/重传)                │
│ - 流复用与流控制                     │
│ - 拥塞控制                           │
├─────────────────────────────────────┤
│ 连接层 (Connection Layer)            │
│ - 连接状态管理                       │
│ - 对等节点管理                       │
├─────────────────────────────────────┤
│ UDP                                 │
└─────────────────────────────────────┘
```

## 3. 数据包格式

### 3.1 包结构

```
UDP Datagram
┌────────────────────────────────────────────────┐
│ Packet Header (21 bytes, 明文)                  │
├────────────────────────────────────────────────┤
│ CryptoDataByte Bundle (加密载荷)                │
│ ┌────────────────────────────────────────────┐ │
│ │ Algorithm ID (6 bytes)                     │ │
│ │ EncryptType (1 byte)                       │ │
│ │ PubKeyA/KeyName (33/6 bytes)               │ │
│ │ IV (12 bytes)                              │ │
│ │ Encrypted Data:                            │ │
│ │   - Timestamp (8 bytes)                   │ │
│ │   - Frame 1                                │ │
│ │   - Frame 2                                │ │
│ │   - ...                                    │ │
│ │   - Auth Tag (16 bytes)                    │ │
│ └────────────────────────────────────────────┘ │
└────────────────────────────────────────────────┘
```

### 3.2 包头格式

```
Packet Header (21 bytes)
┌─────────────────────────────────────────────────┐
│ Flags (1 byte)                                   │
│   Bit 0-1: Packet Type (Data/ACK/Control/Error) │
│   Bit 2: HAS_SYMKEY_PROPOSAL                     │
│   Bit 3: REKEY                                   │
│   Bit 4: FIN                                     │
├─────────────────────────────────────────────────┤
│ Version (4 bytes)                                │
├─────────────────────────────────────────────────┤
│ Connection ID (8 bytes)                          │
├─────────────────────────────────────────────────┤
│ Packet Number (8 bytes)                           │
└─────────────────────────────────────────────────┘
```

## 4. 加密模式

### 4.1 加密类型

```java
enum EncryptType {
    AsyTwoWay(0x02),  // ECDH 双向加密（任何时候都可用）
    Symkey(0x03),     // 会话对称密钥（可轮换）
}
```

### 4.2 AsyTwoWay 模式（ECDH 双向加密）

- **用途**：首包发送、身份认证、密钥协商
- **过程**：
  1. 计算 ECDH: `shared = ECDH(my_private_key, peer_public_key)`
  2. 派生密钥: `key = HKDF(shared, "FUDP-v1", 32)`
  3. 加密: `ciphertext = AES_GCM(key, iv, frames)`
- **性能**：~50 μs/packet (首次), ~2 μs/packet (缓存后)

### 4.3 Symkey 模式（对称密钥加密）

- **用途**：建立连接后的高性能加密
- **过程**：
  1. 查找会话: `session = getSessionByKeyName(keyName)`
  2. 获取对称密钥: `symkey = session.getKeyBytes()`
  3. 加密: `ciphertext = AES_GCM(symkey, iv, frames)`
- **性能**：~1 μs/packet

## 5. 密钥协商流程

### 5.1 基本流程

```
节点 A                                   节点 B
  │                                        │
  │ Packet {                               │
  │   bundle: AsyTwoWay,                   │
  │   frames: [                            │
  │     SYMKEY_PROPOSAL(SymKey),           │
  │     STREAM(data: "Hello")              │
  │   ]                                    │
  │ }                                      │
  ├───────────────────────────────────────>│
  │                                        │ 1. 识别身份: fid_A
  │                                        │ 2. ECDH 解密
  │                                        │ 3. 创建会话: ACCEPTED
  │                                        │
  │ Packet {                               │
  │   bundle: AsyTwoWay,                   │
  │   frames: [                            │
  │     SYMKEY_ACK(keyName)                │
  │   ]                                    │
  │ }                                      │
  │<───────────────────────────────────────┤
  │                                        │
  │ 1. 收到 SYMKEY_ACK → SymKey: ACTIVE    │
  │                                        │
  │ Packet {                               │
  │   bundle: Symkey,                      │
  │   keyName: keyName,                    │
  │   frames: [                            │
  │     STREAM(data: "More data")         │
  │   ]                                    │
  │ }                                      │
  ├───────────────────────────────────────>│
  │                                        │ B 收到加密包 → SymKey: ACTIVE
  │                                        │
  │ 后续: 双方都用 SymKey 加密发送           │
  │<──────────────────────────────────────>│
```

### 5.2 密钥状态机

```java
enum KeyState {
    PROPOSED,    // 我提议了这个密钥，等待对方的 ACK
    ACCEPTED,    // 我接受了对方的提议，等待对方开始使用
    ACTIVE,      // 密钥正在使用（用于加密和解密）
    DEPRECATED,  // 已废弃，但保留用于解密滞后包（60秒）
}
```

### 5.3 双向发起处理

当双方同时发起密钥提议时：
- 公钥较大的一方作为 Proposer（提议者）
- 公钥较小的一方作为 Acceptor（接受者）
- Acceptor 丢弃自己的提议，接受 Proposer 的密钥

### 5.4 重启/复用场景的会话与状态重置

当对端重启但复用相同 IP/port 时，可能出现包号从 0 重新计数、streamId 重用、旧会话残留导致解密/重放/流偏移异常。处理策略：
- 收到 AsyTwoWay 携带的 `SYMKEY_PROPOSAL` 视为“对端重启”信号：重置重放窗口、清空流和 ACK 状态，允许新的包号/streamId 被接受。
- 收到 `ERROR_SESSION_NOT_FOUND`（code=1）时，只清理 ACTIVE/DEPRECATED，保留 PROPOSED，避免正在协商的新密钥被误删。
- 如果在 ACTIVE 会话下收到 AsyTwoWay 数据，会临时启用 `forceAsyTwoWay` 直至新会话协商完成，避免用失效的对称密钥加密。

## 6. Frame 类型

```java
enum FrameType {
    PADDING(0x00),           // 填充
    STREAM(0x01),            // 流数据
    ACK(0x02),               // 确认
    CONNECTION_CLOSE(0x03),  // 连接关闭
    MAX_DATA(0x04),          // 连接级流控
    MAX_STREAM_DATA(0x05),   // 流级流控
    MAX_STREAMS(0x06),       // 最大流数
    SYMKEY_PROPOSAL(0x07),   // 对称密钥提议
    SYMKEY_ACK(0x08),        // 确认接受对称密钥
}
```

## 7. 核心组件

### 7.1 主要类

- **Protocol**: 主入口，管理协议生命周期
- **CryptoManager**: 加密管理器，处理密钥生成和 ECDH 计算
- **SessionManager**: 会话管理器，管理对称密钥会话
- **ConnectionManager**: 连接管理器，管理对等连接
- **PacketCrypto**: 包加密/解密处理
- **PeerConnection**: 对等连接，管理单个对等节点的状态
- **StreamManager**: 流管理器，管理多路复用流

### 7.2 安全特性

- **重放攻击防护**：包序列号 + 时间戳 + 滑动窗口
- **身份认证**：能用对方公钥解密即证明对方持有私钥
- **前向保密性**：支持对称密钥轮换（可选）

## 8. 使用示例

### 8.1 基本使用

```java
// 初始化协议
byte[] privateKey = ...; // 32 字节私钥
Protocol protocol = new Protocol(privateKey, 5000, "/data/dir");
protocol.start();

// 连接到对等节点
byte[] peerPublicKey = ...;
SocketAddress peerAddress = new InetSocketAddress("192.168.1.100", 5001);
Stream stream = protocol.connect(peerPublicKey, peerAddress);

// 发送数据
byte[] data = "Hello, World!".getBytes();
protocol.send(stream, data);

// 接收数据（通过监听器或回调）
protocol.addPacketListener((conn, packet) -> {
    for (Frame frame : packet.getFrames()) {
        if (frame instanceof StreamFrame) {
            StreamFrame sf = (StreamFrame) frame;
            // 处理流数据
            System.out.println("Received: " + new String(sf.getData()));
        }
    }
});

// 关闭连接
protocol.close(peerId, 0, "Normal close");
protocol.stop();
```

### 8.2 流管理

```java
// 在已有连接上打开新流
PeerConnection conn = protocol.getConnectionManager().getByPeerId(peerId);
Stream stream2 = conn.openStream();

// 发送并关闭流
protocol.sendAndClose(stream2, "Final message".getBytes());
```

## 9. 算法支持

### 9.1 推荐算法

| 用途 | 算法 ID | 说明 |
|------|---------|------|
| AsyTwoWay 模式 | `FC_EccK1AesGcm256_No1_NrC7` (0x04) | ECDH + AES-256-GCM |
| Symkey 模式 | `FC_AesGcm256_No1_NrC7` (0x03) | AES-256-GCM |

### 9.2 算法实现

- 采用硬编码方式定义算法，不依赖区块链动态查询
- 确保启动即可用，无网络延迟和不确定性
- 与 Freeverse 生态的 CryptoDataByte Bundle 格式兼容

## 10. 特性总结

### 10.1 核心优势

- ✅ **0-RTT 数据传输**：首包即可携带应用数据
- ✅ **完全对等**：无客户端/服务端区别
- ✅ **端到端加密**：公钥加密 + 对称密钥加密
- ✅ **身份即公钥**：无需 CA 证书体系
- ✅ **可靠传输**：ACK、重传、流控制、拥塞控制
- ✅ **地址透明**：自动适应地址变化（NAT 重绑定、网络切换）

### 10.2 性能指标

- 首包延迟：0-RTT（发送方视角）
- 加密性能：对称加密模式下 < 2μs/KB
- 连接建立：无需预握手
- 可靠性：丢包率 < 1% 时达到 TCP 级别可靠性

## 11. 与 Freeverse 生态集成

- **身份系统**：使用 FID（Freeverse ID）作为节点身份标识
- **加密格式**：兼容 CryptoDataByte Bundle 格式
- **会话管理**：使用 FcSession 进行密钥管理
- **算法兼容**：与 FC-JDK 中的算法定义保持一致

## 12. 参考资料

详细设计文档请参考：`P2P_PROTOCOL_DESIGN.md`

---

**文档版本**: 1.0  
**最后更新**: 2025-01-XX  
**维护者**: Freeverse Team
