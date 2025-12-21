# FUDP（P2P 无握手对等加密传输协议）设计文档

## 1. 概述

### 1.1 项目简介

本协议是一个基于 UDP 的对等（Peer-to-Peer）加密传输协议，具有以下核心特性：

- **无预握手**：首包即可携带应用数据，实现 0-RTT 数据传输
- **对等设计**：节点间无客户端/服务端区别，完全对等通信
- **统一加密**：仅使用 AsyTwoWay（ECDH）加密，无需密钥协商
- **身份即公钥**：使用公钥作为节点身份，无需 CA 证书体系
- **可靠传输**：借鉴 QUIC 设计，提供 ACK、重传、流控制、拥塞控制
- **地址透明**：身份基于公钥而非 IP，自动适应地址变化
- **简化设计**：移除对称密钥协商，降低协议复杂度

### 1.2 设计目标

| 目标 | 指标 |
|------|------|
| 首包延迟 | 0-RTT（发送方视角） |
| 加密性能 | ~2-3 μs/packet (ECDH 缓存后) |
| 连接建立 | 无需预握手，无需密钥协商 |
| 安全性 | 端到端加密 + 身份认证 |
| 可靠性 | 丢包率 < 1% 时达到 TCP 级别可靠性 |
| 协议复杂度 | 低（无会话状态管理） |

### 1.3 应用场景

- P2P 文件传输
- 数据请求与响应P
- 去中心化消息通信
- IoT 设备互联
- 游戏 P2P 连接
- 内网高性能数据传输

---

## 2. 架构设计

### 2.1 协议栈层次

```
┌─────────────────────────────────────┐
│ 应用层 (Application Layer)          │
│ - 业务逻辑                           │
│ - 流数据接口                         │
├─────────────────────────────────────┤
│ 加密层 (Crypto Layer)                │
│ - 公钥加密/解密                       │
│      (Ecc256K1 + AES-GCM/ChaCha20)  │
│ - 对称密钥管理                       │
│ - 身份认证                           │
├─────────────────────────────────────┤
│ 传输层 (Transport Layer)             │
│ - 可靠传输 (ACK/NACK)                │
│ - 包序列号管理                       │
│ - 流复用 (Multiplexing)              │
│ - 流控制 (Flow Control)              │
│ - 拥塞控制 (Congestion Control)      │
├─────────────────────────────────────┤
│ 连接层 (Connection Layer)            │
│ - 连接状态管理                       │
│ - 对等节点管理                       │
│ - 连接复用                           │
├─────────────────────────────────────┤
│ UDP                                 │
└─────────────────────────────────────┘
```

### 2.2 核心组件

```
P2PProtocol
├── CryptoManager         # 加密管理器
│   ├── KeyPair           # 本地密钥对
│   └── ECDHCache         # ECDH 计算缓存（可选优化）
├── ConnectionManager     # 连接管理器
│   ├── PeerConnection    # 对等连接
│   └── AddressBook       # 地址簿
├── TransportManager      # 传输管理器
│   ├── PacketSender      # 包发送器
│   ├── PacketReceiver    # 包接收器
│   ├── ACKTracker        # ACK 跟踪器
│   └── RetransmitQueue   # 重传队列
├── StreamManager         # 流管理器
│   ├── StreamMultiplexer # 流复用器
│   └── FlowController    # 流控制器
└── CongestionControl     # 拥塞控制
    ├── RTTEstimator      # RTT 估算器
    └── CUBICController   # CUBIC 算法

注：已移除 SessionManager 和 SymKeyPool，协议仅使用 AsyTwoWay 加密。
```

### 2.3 传输层计量（供上层消费）

- FUDP 仅做加密/可靠传输，协议不内置经济/余额字段，默认作为“纯通讯”使用。
- 在收发路径记录计量数据（peerId、方向、消息类型、payload 大小、时间戳、RTT/重传/丢包提示等），通过回调暴露给上层应用。
- 上层（如 FAPI）可基于计量数据实现自定义计费/信用模型，FUDP 不解析价格/信用/黑名单等业务参数。
- 报文格式保持精简，不因经济模型变化而变动，避免协议复杂度增加。

---

## 3. 密码学设计

### 3.1 密钥体系

#### 3.1.1 密钥类型

```java
// 节点身份密钥对（长期）
ECKeyPair identityKeyPair;
  - privateKey: byte[32]    // 私钥
  - publicKey: byte[33]     // 公钥（压缩公钥，即节点身份）

// ECDH 共享密钥（每个对等节点一个，缓存）
byte[32] symkey = asyKeyToSymkey(byte[] priKey, byte[] pubKey,byte[]nonce);
// 对称会话密钥（短期，可轮换）
byte[32] symmetricKey;
  - 节点生成: SecureRandom.nextBytes()
```


### 3.2 加密算法选择

| 用途 | 算法 | 密钥长度 | 理由 |
|------|------|---------|------|
| 身份密钥 | ECC speck1 | 256 bit | ECDH |
| 对称加密 | AES GCM | 256 bit |  |
| 哈希 | SHA-256 | - | 广泛支持 |

### 3.3 加密模式

**简化设计：仅使用 AsyTwoWay（ECDH 双向加密）**

```java
// 唯一支持的加密类型
EncryptType.AsyTwoWay(0x02)  // ECDH 双向加密
```

**设计决策说明：**

在 v2.0 版本中，我们移除了 Symkey（对称密钥）加密模式，原因如下：

1. **性能差异不显著** - 缓存 ECDH 后，每包仅多 1-2 μs
2. **复杂性成本过高** - 密钥协商、会话管理、状态同步的复杂性与微小性能提升不成正比
3. **可靠性提升** - 无状态设计天然支持重启、地址变化、网络切换
4. **代码量减少** - 删除 ~800 行密钥协商代码
5. **前向保密可替代实现** - 定期清除 ECDH 缓存即可实现类似效果

**核心设计原则：**
- ✅ **AsyTwoWay 提供身份认证** - 双向 ECDH 确保通信双方身份
- ✅ **公钥加密任何时候都可用** - 天然支持会话恢复、地址变化
- ✅ **无状态设计** - 无需会话管理，简化错误处理
- ✅ **与 Freeverse 生态集成** - 使用统一的身份系统和密文格式

#### 3.3.1 AsyTwoWay 模式（唯一加密模式）

```
加密过程 (发送方):
1. 计算 ECDH: shared = ECDH(my_private_key, peer_public_key)
2. 派生密钥: key = HKDF(shared, nonce, "hkdf", 32)
3. 生成 IV: 随机生成 12 字节
4. 加密: ciphertext = AES_GCM(key, iv, frames)
5. 构造 Bundle: [algId, 0x02, my_pubkey, iv, cipher, tag]

解密过程 (接收方):
1. 从 Bundle 提取 peer_pubkey
2. 计算 ECDH: shared = ECDH(my_private_key, peer_pubkey)
3. 派生密钥: key = HKDF(shared, nonce, "hkdf", 32)
4. 解密: frames = AES_GCM_Decrypt(key, iv, ciphertext, tag)
5. 身份验证: peer_id = pubkeyToFid(peer_pubkey)

性能: ~50 μs/packet (首次 ECDH), ~2-3 μs/packet (ECDH 缓存后)

使用场景:
- 所有数据包（统一加密方式）
- 首包发送（0-RTT 数据传输）
- 连接恢复（无需重新协商）
- 地址迁移（自动适应）
```

#### 3.3.2 Symkey 模式（已移除）

> **注意：Symkey 模式已在 v2.0 中移除。**
>
> 如果未来确实遇到性能瓶颈（吞吐量 > 1 Gbps），可以考虑重新引入 Symkey 作为可选优化。
> 但作为初始设计，**简单性优先**是更明智的选择。


### 3.4 通信流程（无需密钥协商）

**简化设计：移除密钥协商**

由于仅使用 AsyTwoWay 加密，无需密钥协商过程。通信流程极其简单：

```
场景: 数据传输（A → B）
┌─────┐                                        ┌─────┐
│  A  │                                        │  B  │
└──┬──┘                                        └──┬──┘
   │                                              │
   │ Packet {                                     │
   │   header: [connId, pktNum],                  │
   │   bundle: [                                  │
   │     alg: FC_EccK1AesGcm256,                  │
   │     type: AsyTwoWay,                         │
   │     pubkeyA: A_pubkey,                       │
   │     iv: random_12_bytes,                     │
   │     cipher: encrypt(frames),                 │
   │     tag: auth_tag                            │
   │   ],                                         │
   │   frames: [                                  │
   │     STREAM(data: "Hello")                    │
   │   ]                                          │
   │ }                                            │
   ├─────────────────────────────────────────────>│
   │                                              │ 1. 提取 A_pubkey
   │                                              │ 2. ECDH 解密: shared = ECDH(B_prikey, A_pubkey)
   │                                              │ 3. HKDF 派生密钥
   │                                              │ 4. AES-GCM 解密
   │                                              │ 5. 识别身份: fid_A = pubkeyToFid(A_pubkey)
   │                                              │
   │ Packet {                                     │
   │   bundle: [                                  │
   │     alg: FC_EccK1AesGcm256,                  │
   │     type: AsyTwoWay,                         │
   │     pubkeyA: B_pubkey,                       │
   │     cipher: encrypt(frames)                  │
   │   ],                                         │
   │   frames: [                                  │
   │     ACK(...),                                │
   │     STREAM(data: "Hi there!")                │
   │   ]                                          │
   │ }                                            │
   │<─────────────────────────────────────────────┤
   │                                              │
   │ 后续: 双方继续使用 AsyTwoWay 加密发送           │
   │       每个包都包含发送者公钥，无需会话状态       │
   │<────────────────────────────────────────────>│
```

**设计优势：**
- ✅ **无状态** - 不需要维护会话状态机
- ✅ **无协商** - 首包即可传输数据
- ✅ **自恢复** - 重启后无需重新协商
- ✅ **地址透明** - 自动适应地址变化

> **注意：** 以下内容已在 v2.0 中移除：
> - SYMKEY_PROPOSAL / SYMKEY_ACK 帧
> - SessionManager / FudpSession
> - KeyState 状态机
> - SESSION_NOT_FOUND 错误处理

### 3.5 身份识别与连接管理

**身份识别逻辑（简化版）：**

```java
// 从接收到的包中识别发送者身份
// 仅支持 AsyTwoWay，身份识别变得简单直接
String identifySender(CryptoDataByte bundle) {
    // 每个包都包含发送者公钥
    byte[] pubkey = bundle.getPubkeyA();
    return KeyTools.pubKeyToFid(pubkey);  // 转换为 FID
}
```

**连接查找/创建：**

```java
// 基于发送者 FID 查找或创建连接
PeerConnection getOrCreateConnection(String senderId, SocketAddress fromAddress) {
    PeerConnection conn = connectionMap.get(senderId);

    if (conn == null) {
        conn = new PeerConnection(senderId, fromAddress);
        connectionMap.put(senderId, conn);
    } else {
        // 地址变化时直接更新
        // 由于解密成功已验证身份，无需额外验证
        conn.updateAddress(fromAddress);
    }

    return conn;
}
```

**天然支持地址变化：**

由于 FUDP 的身份验证不依赖 IP 地址，而是基于每包解密验证，协议天然支持地址变化场景：

- **NAT 重绑定** - 客户端 IP/Port 变化后，下一个包会自动更新连接地址
- **网络切换** - 从 WiFi 切换到移动网络，连接状态保持不变
- **多路径** - 可以从不同地址发送包到同一对等节点

```
为什么不需要 PATH_CHALLENGE/PATH_RESPONSE？

传统协议需要验证新地址的合法性，防止：
1. 反射攻击 - FUDP 响应发给 from 地址，攻击者收不到
2. 连接劫持 - FUDP 私钥验证，无法劫持

FUDP 中解密成功 = 身份验证通过，所以地址更新是自动的。
```

**优势：**
- ✅ **无状态包头** - 包头不包含身份信息，提升隐私
- ✅ **身份绑定** - AsyTwoWay 通过公钥认证，Symkey 通过会话关联
- ✅ **天然支持地址变化** - 解密验证身份，地址自动更新
- ✅ **与 Freeverse 统一** - FID 作为全局唯一身份标识

### 3.6 安全特性

#### 3.6.1 前向保密性（Forward Secrecy）

```
实现方式:
1. 对称密钥定期轮换（默认 1 小时）
2. 旧密钥立即销毁
3. 可选: 每个流使用不同的临时密钥

密钥轮换流程:
- 节点 A 生成新的 SymKey_new
- 在下一个包的 flags 中设置 REKEY
- 附带 new_symkey（用当前密钥加密）
- 双方切换到新密钥
```

#### 3.6.2 重放攻击防护

```java
class ReplayProtection {
    // 每个对等节点维护一个滑动窗口
    Map<String, PacketWindow> windows;  // Key: peer FID

    static class PacketWindow {
        long highestPacketNumber;
        BitSet receivedBitmap;  // 窗口大小 1024
        long windowSize = 1024;

        boolean checkAndRecord(long packetNumber, long timestamp) {
            // 1. 检查时间戳（允许 ±5 秒时钟偏差）
            // 注意：timestamp 从解密后的载荷中提取
            long now = System.currentTimeMillis();
            if (Math.abs(timestamp - now) > 5000) {
                return false; // 时间戳无效
            }

            // 2. 检查包序列号
            if (packetNumber <= highestPacketNumber - windowSize) {
                return false; // 太老的包
            }

            if (packetNumber <= highestPacketNumber) {
                long offset = highestPacketNumber - packetNumber;
                if (receivedBitmap.get((int)offset)) {
                    return false; // 重复包
                }
                receivedBitmap.set((int)offset);
            } else {
                // 更新窗口
                long shift = packetNumber - highestPacketNumber;
                // 移动窗口
                for (int i = 0; i < shift && i < windowSize; i++) {
                    receivedBitmap.clear((int)(windowSize - 1 - i));
                }
                highestPacketNumber = packetNumber;
                receivedBitmap.set(0);
            }

            return true;
        }
    }
}
```

#### 3.6.3 身份认证

```
认证机制:

隐式认证: 能用对方公钥解密即证明对方持有私钥

```

### 3.7 无公钥引导（明文 HELLO）

场景：仅拿到对端 URL/地址，尚未拿到对端公钥。由于 FUDP 的加密公钥即身份公钥，可以用最小明文引导获取公钥。

流程：
- A → B：发送明文 `HELLO` 包（唯一允许明文的控制包，不包含 CryptoDataByte）。载荷可选字段：协议版本、特性位、自身份公钥（方便 B 缓存）。
- B 处理：
  - 收到 `HELLO`；或收到 AsyTwoWay/Symkey 加密包但解密失败（无会话或公钥不匹配）时，返回明文 `PUBLIC_KEY` 包，字段：`pubkeyB`（33B 压缩公钥，亦为 B 的身份），可选支持的 `algId`。
- A 收到 `PUBLIC_KEY` 后缓存 `pubkeyB`，立即切换为 AsyTwoWay 加密发送后续数据/`SYMKEY_PROPOSAL`。
- 若后续仍解密失败，可重发 `HELLO` 触发公钥重送。

安全说明：
- 该阶段仅提供发现/引导，不做公钥真实性保证；上层可用指纹比对或预共享信任锚校验公钥。
- 响应无需 sigB/nonce，减少往返；对明文 `HELLO` 应做速率限制，防止被滥用为 DoS/探测。

---

## 4. 协议格式

### 4.1 数据包结构

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
│ │   - Timestamp (8 bytes)                    │ │
│ │   - Frame 1                                │ │
│ │   - Frame 2                                │ │
│ │   - ...                                    │ │
│ │   - Auth Tag (16 bytes, GCM/Poly1305)      │ │
│ └────────────────────────────────────────────┘ │
└────────────────────────────────────────────────┘
```

### 4.2 包头格式

```
Packet Header (总长度: 21 字节)
┌─────────────────────────────────────────────────┐
│ Byte 0: Flags (8 bits)                          │
├─────────────────────────────────────────────────┤
│ Byte 1-4: Version (32 bits)                     │
├─────────────────────────────────────────────────┤
│ Byte 5-12: Connection ID (64 bits)              │
├─────────────────────────────────────────────────┤
│ Byte 13-20: Packet Number (64 bits)             │
└─────────────────────────────────────────────────┘
```

**设计说明：**
- **极简设计** - 包头只包含路由和排序信息，不包含加密和身份信息
- **发送者识别** - 通过加密载荷（CryptoDataByte Bundle）中的信息识别发送者
- **隐私保护** - 明文包头无法识别通信双方身份

#### 4.2.1 Flags 字段 (8 bits)

```
Bit 0-1: Packet Type
  00 = Data packet
  01 = ACK packet
  10 = Control packet
  11 = Error packet (New)

Bit 2: HAS_SYMKEY_PROPOSAL
  0 = 不包含对称密钥提议
  1 = 包含对称密钥提议

Bit 3: REKEY
  0 = 正常包
  1 = 密钥轮换

Bit 4: FIN
  0 = 正常包
  1 = 连接关闭

Bit 5-7: Reserved
```

### 4.2.2 加密载荷格式（CryptoDataByte Bundle）

包头之后是加密载荷，采用 Freeverse 统一的 CryptoDataByte Bundle 格式：

**AsyTwoWay 模式（ECDH 双向加密）:**
```
┌─────────────────────────────────────────────────┐
│ Algorithm ID (6 bytes)                          │
├─────────────────────────────────────────────────┤
│ EncryptType = 0x02 (1 byte)                     │
├─────────────────────────────────────────────────┤
│ PubKeyA (33 bytes) - 发送者公钥                  │
├─────────────────────────────────────────────────┤
│ IV (12 bytes)                                   │
├─────────────────────────────────────────────────┤
│ Encrypted Frames + Auth Tag (variable length)   │
└─────────────────────────────────────────────────┘
```

**Symkey 模式（对称密钥加密）:**
```
┌─────────────────────────────────────────────────┐
│ Algorithm ID (6 bytes)                          │
├─────────────────────────────────────────────────┤
│ EncryptType = 0x03 (1 byte)                     │
├─────────────────────────────────────────────────┤
│ KeyName (6 bytes) - sha256(symkey)[0:6]         │
├─────────────────────────────────────────────────┤
│ IV (12 bytes)                                   │
├─────────────────────────────────────────────────┤
│ Encrypted Frames + Auth Tag (variable length)   │
└─────────────────────────────────────────────────┘
```

**支持的 Algorithm ID (预定义):**
```
0x000000000001 = FC_AesCbc256_No1_NrC7           (已废弃)
0x000000000002 = FC_EccK1AesCbc256_No1_NrC7      (已废弃)
0x000000000003 = FC_AesGcm256_No1_NrC7           (推荐)
0x000000000004 = FC_EccK1AesGcm256_No1_NrC7      (推荐)
0x000000000005 = FC_X25519AesGcm256_No1_NrC7     (与Freeverse公钥不兼容，不推荐)
0x??????...... = 链上注册算法 (FEIP0_Protocol)
```

**EncryptType 定义:**
```java
enum EncryptType {
    AsyTwoWay(0x02),  // ECDH 双向加密（发送者私钥 + 接收者公钥）
    Symkey(0x03),     // 对称密钥加密（会话密钥）
}
```

**说明：**
- AsyTwoWay 模式只包含发送者公钥（pubkeyA），接收者公钥由接收者本地提供
- Symkey 模式通过 KeyName 查找本地保存的会话密钥（FcSession）
- 所有加密算法必须提供认证（GCM 或 Poly1305），确保完整性
- IV 长度根据算法确定：
  - AES-GCM: 12 bytes
  - AES-CBC: 16 bytes（已废弃）
  - ChaCha20: 12 bytes
- Auth Tag（16 bytes）已包含在 Encrypted Frames 字段末尾（GCM/Poly1305 自动附加）

### 4.3 Frame 格式

所有 Frame 都封装在加密的 Payload 中。

#### 4.3.1 加密载荷结构

```
Encrypted Payload
┌─────────────────────────────────────┐
│ Timestamp (64 bits, milliseconds)   │  ← 防重放攻击
├─────────────────────────────────────┤
│ Frame 1                             │
├─────────────────────────────────────┤
│ Frame 2                             │
├─────────────────────────────────────┤
│ ...                                 │
└─────────────────────────────────────┘

说明:
- Timestamp: 发送时间戳（毫秒），用于防重放攻击检查
- 接收方验证: |timestamp - now| < 5000ms（允许 ±5 秒时钟偏差）
- 时间戳在加密载荷内，不暴露给网络观察者
```

#### 4.3.2 Frame 通用头部

```
┌─────────────────────────────────────┐
│ Frame Type (varint, 1-8 bytes)      │
├─────────────────────────────────────┤
│ Frame-specific payload              │
└─────────────────────────────────────┘
```

#### 4.3.3 Frame 类型定义

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
    PACKET_TYPE_ERROR(0x03); // 错误包 (New)
}
```

#### 4.3.4 STREAM Frame

```
STREAM Frame
┌─────────────────────────────────────┐
│ Type = 0x01 (varint)                │
├─────────────────────────────────────┤
│ Stream ID (varint)                  │
├─────────────────────────────────────┤
│ Offset (varint)                     │
├─────────────────────────────────────┤
│ Length (varint)                     │
├─────────────────────────────────────┤
│ Stream Data (Length bytes)          │
└─────────────────────────────────────┘

Flags (编码在 Type 的低位):
  Bit 0: FIN - 流结束标志
  Bit 1: LEN - 是否包含 Length 字段
  Bit 2: OFF - 是否包含 Offset 字段
```

#### 4.3.5 ACK Frame

```
ACK Frame
┌─────────────────────────────────────┐
│ Type = 0x02 (varint)                │
├─────────────────────────────────────┤
│ Largest Acknowledged (varint)       │
├─────────────────────────────────────┤
│ ACK Delay (varint, microseconds)    │
├─────────────────────────────────────┤
│ ACK Range Count (varint)            │
├─────────────────────────────────────┤
│ First ACK Range (varint)            │
├─────────────────────────────────────┤
│ ACK Ranges (repeated)               │
│ ┌───────────────────────────────┐   │
│ │ Gap (varint)                  │   │
│ ├───────────────────────────────┤   │
│ │ ACK Range Length (varint)     │   │
│ └───────────────────────────────┘   │
└─────────────────────────────────────┘

示例:
  Largest Acknowledged = 100
  First ACK Range = 5
  表示收到了包: 96, 97, 98, 99, 100

  如果还有 Gap=2, ACK Range=3
  表示额外收到了包: 90, 91, 92
```

#### 4.3.6 SYMKEY_PROPOSAL Frame

```
SYMKEY_PROPOSAL Frame
┌─────────────────────────────────────┐
│ Type = 0x07 (varint)                │
├─────────────────────────────────────┤
│ Symmetric Key (32 bytes)            │
└─────────────────────────────────────┘

说明:
- Symmetric Key: 32 字节随机生成的对称密钥
- Key Name: 接收方计算 sha256(SymKey)[0:6] 作为密钥标识
- 密钥生命周期由应用层管理（通过 session.birthTime）
```

#### 4.3.7 SYMKEY_ACK Frame

```
SYMKEY_ACK Frame
┌─────────────────────────────────────┐
│ Type = 0x08 (varint)                │
├─────────────────────────────────────┤
│ Key Name (48 bits)                  │
└─────────────────────────────────────┘

说明:
- Key Name: sha256(SymKey)[0:6]，确认接受的对称密钥的标识
- 收到此帧后，发送方将对应的密钥状态从 PROPOSED 更新为 ACTIVE
```

#### 4.3.8 CONNECTION_CLOSE Frame

```
CONNECTION_CLOSE Frame
┌─────────────────────────────────────┐
│ Type = 0x03 (varint)                │
├─────────────────────────────────────┤
│ Error Code (varint)                 │
├─────────────────────────────────────┤
│ Reason Phrase Length (varint)       │
├─────────────────────────────────────┤
│ Reason Phrase (UTF-8)               │
└─────────────────────────────────────┘

Error Codes:
  0x00 = NO_ERROR
  0x01 = INTERNAL_ERROR
  0x02 = CRYPTO_ERROR
  0x03 = FLOW_CONTROL_ERROR
  0x04 = STREAM_LIMIT_ERROR
  0x05 = PROTOCOL_VIOLATION
```

#### 4.3.9 PING / PONG 控制消息

应用层 PING/PONG 使用 STREAM 数据承载，格式保持极简：

- **PING 载荷**：`Timestamp (8B)`；如果 `Flags`（AppMessage flags bit4）设置 `WANT_PONG_INFO`，请求对方在 PONG 中携带附加信息。
- **PONG 载荷**：`Echo Timestamp (8B)` + `Reply Timestamp (8B)` + `DataLen (varint)` + `Data (可选)`。
  - Data 默认上限 **1024B**，节点侧可配置；超过上限截断。
  - 仅在对方 PING 设置了 `WANT_PONG_INFO` 且通过速率限制时附带 Data（默认同一 peer ≥2s 一次，防放大/DoS）。
  - Data 仍处于加密载荷内，不单独签名；由应用层定义格式（如 JSON TLV）。
  - 用例：FAPI 节点在 PONG Data 中广播本节点提供的服务 ID/元信息，便于发现/路由。

### 4.4 Varint 编码

使用 QUIC 风格的变长整数编码：

```
2-bit prefix 表示长度:
  00 = 1 byte  (6 bits 数据, 最大 63)
  01 = 2 bytes (14 bits 数据, 最大 16383)
  10 = 4 bytes (30 bits 数据, 最大 1073741823)
  11 = 8 bytes (62 bits 数据, 最大 4611686018427387903)

示例:
  值 37 → 0x25 (00100101)
  值 15293 → 0x7BBD (01111011 10111101)
```

---

## 5. 传输层设计

### 5.1 连接管理

#### 5.1.1 连接标识

```java
class ConnectionId {
    PublicKey peerPublicKey;  // 32 bytes - 对等节点公钥（主标识）
    long localConnectionId;    // 8 bytes - 本地分配的连接 ID

    // 连接唯一标识 = Hash(my_pubkey || peer_pubkey)
    // 确保双向唯一
}
```

#### 5.1.2 连接状态机

```
状态转换:

  ┌─────────┐  send/recv first packet   ┌──────────────┐
  │  IDLE   │ ─────────────────────────> │ ESTABLISHING │
  └─────────┘                            └──────┬───────┘
                                                │
                                                │ recv ACK
                                                ▼
                                         ┌─────────────┐
                      data exchange      │ ESTABLISHED │
                     ◄──────────────────>└──────┬──────┘
                                                │
                                                │ send/recv FIN
                                                ▼
                                         ┌─────────────┐
                                         │   CLOSING   │
                                         └──────┬──────┘
                                                │
                                                │ timeout / all ACKed
                                                ▼
                                         ┌─────────────┐
                                         │   CLOSED    │
                                         └─────────────┘
```

### 5.2 可靠传输

#### 5.2.1 包序列号管理

```java
class PacketNumberSpace {
    long nextPacketNumber = 0;
    long largestAcked = -1;

    // 已发送但未确认的包
    TreeMap<Long, SentPacket> sentPackets = new TreeMap<>();

    // 已接收的包序列号集合
    PacketNumberSet receivedPackets = new PacketNumberSet();

    long allocatePacketNumber() {
        return nextPacketNumber++;
    }
}

class SentPacket {
    long packetNumber;
    Instant sentTime;
    List<Frame> frames;
    int size;
    boolean ackEliciting;  // 是否需要 ACK
    int retransmitCount;
}
```

#### 5.2.2 ACK 机制

```java
class AckManager {
    static final long MAX_ACK_DELAY_MS = 10;  // 最大 ACK 延迟（优化后降低延迟）
    static final int ACK_THRESHOLD = 1;        // 收到 1 个包后立即 ACK（最低延迟模式）

    PacketNumberSet pendingAcks = new PacketNumberSet();
    ScheduledFuture<?> ackTimer;

    void onPacketReceived(long packetNumber) {
        pendingAcks.add(packetNumber);

        if (pendingAcks.size() >= ACK_THRESHOLD) {
            sendAckImmediately();
        } else if (ackTimer == null) {
            // 启动延迟 ACK 定时器
            ackTimer = scheduler.schedule(
                this::sendAck,
                MAX_ACK_DELAY_MS,
                TimeUnit.MILLISECONDS
            );
        }
    }

    AckFrame generateAckFrame() {
        // 将 pendingAcks 转换为 ACK ranges
        List<AckRange> ranges = pendingAcks.toRanges();

        return new AckFrame(
            pendingAcks.largest(),
            calculateAckDelay(),
            ranges
        );
    }
}
```

#### 5.2.3 丢包检测

```java
class LossDetection {
    static final double TIME_THRESHOLD = 9.0 / 8.0;  // 1.125 * RTT
    static final int PACKET_THRESHOLD = 3;            // 快速重传阈值

    void detectLostPackets(long largestAcked, Duration rtt) {
        long lossDelay = (long)(TIME_THRESHOLD * rtt.toNanos());
        Instant lostSendTime = Instant.now().minusNanos(lossDelay);

        List<Long> lostPackets = new ArrayList<>();

        for (Map.Entry<Long, SentPacket> entry : sentPackets.entrySet()) {
            long pn = entry.getKey();
            SentPacket packet = entry.getValue();

            // 1. 基于包序列号的检测（快速重传）
            if (largestAcked - pn >= PACKET_THRESHOLD) {
                lostPackets.add(pn);
                continue;
            }

            // 2. 基于时间的检测
            if (packet.sentTime.isBefore(lostSendTime)) {
                lostPackets.add(pn);
            }
        }

        // 重传丢失的包
        for (long pn : lostPackets) {
            retransmitPacket(sentPackets.remove(pn));
        }
    }
}
```

#### 5.2.4 重传策略

```java
class RetransmissionManager {
    static final int MAX_RETRANSMIT_COUNT = 10;

    void retransmitPacket(SentPacket lostPacket) {
        if (lostPacket.retransmitCount >= MAX_RETRANSMIT_COUNT) {
            // 超过最大重传次数，关闭连接
            closeConnection(ERROR_TOO_MANY_RETRANSMITS);
            return;
        }

        // 重新封装 frames（使用新的包序列号）
        Packet newPacket = new Packet();
        newPacket.packetNumber = allocatePacketNumber();

        for (Frame frame : lostPacket.frames) {
            // 某些 frame 类型不需要重传（如 ACK）
            if (shouldRetransmit(frame)) {
                newPacket.addFrame(frame);
            }
        }

        SentPacket newSentPacket = new SentPacket(newPacket);
        newSentPacket.retransmitCount = lostPacket.retransmitCount + 1;

        sendPacket(newPacket);
        sentPackets.put(newPacket.packetNumber, newSentPacket);
    }

    boolean shouldRetransmit(Frame frame) {
        return !(frame instanceof AckFrame) &&
               !(frame instanceof PaddingFrame);
    }
}
```

#### 5.2.5 应用层消息去重

由于传输层重传使用新的包序列号，重放保护无法阻止应用层收到重复的消息数据。因此需要应用层去重机制：

**问题场景**：
1. 发送方发送消息（packetNumber=1）
2. ACK 延迟或丢失，发送方认为包丢失
3. 发送方重传消息（packetNumber=2，相同的消息内容）
4. 接收方收到两个包，都通过了重放保护（不同的 packetNumber）
5. 如果没有应用层去重，消息会被处理两次

**解决方案**：

```java
class ChatHandler {
    // 已处理的消息 ID（线程安全）: 复合键 -> 接收时间戳
    // 复合键格式: "peerId:messageId"，避免不同节点间的 ID 冲突
    private final Map<String, Long> receivedMessageIds = new ConcurrentHashMap<>();

    /**
     * 构建复合键
     */
    private static String buildKey(String peerId, long messageId) {
        return peerId + ":" + messageId;
    }

    /**
     * 原子检查并标记消息为已处理
     * @param peerId 发送者 ID
     * @param messageId 消息 ID
     * @return true 如果是新消息，false 如果是重复消息
     */
    public boolean tryMarkAsProcessed(String peerId, long messageId) {
        long now = System.currentTimeMillis();
        String key = buildKey(peerId, messageId);
        // putIfAbsent 返回 null 表示新消息
        return receivedMessageIds.putIfAbsent(key, now) == null;
    }

    /**
     * 定期清理过期的消息 ID（防止内存泄漏）
     */
    public void cleanupOldMessages(long maxAgeMs) {
        long cutoff = System.currentTimeMillis() - maxAgeMs;
        receivedMessageIds.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }
}
```

**处理流程**：

```
收到消息 → tryMarkAsProcessed(peerId, messageId)
    ├─ 返回 true（新消息）
    │   ├─ 发送 ACK
    │   ├─ 处理消息
    │   └─ 触发应用回调
    └─ 返回 false（重复消息）
        ├─ 发送 ACK（确保发送方停止重传）
        └─ 丢弃消息，不触发回调
```

**关键设计点**：
1. **原子操作**：使用 `ConcurrentHashMap.newKeySet().add()` 确保并发安全
2. **重复消息仍发送 ACK**：防止发送方无限重传
3. **消息 ID 唯一性**：发送方使用单调递增的消息 ID（通常是时间戳或计数器）
4. **内存管理**：定期清理过期的消息 ID，防止内存泄漏

### 5.3 流复用

#### 5.3.1 流标识

```java
class StreamId {
    long value;

    // 流 ID 编码（借鉴 QUIC）
    // Bit 0: 0=客户端发起, 1=服务端发起（对等模式下根据公钥大小决定）
    // Bit 1: 0=双向流, 1=单向流
    // Bit 2+: 流序号

    boolean isInitiatedByPeer() {
        return (value & 0x01) == 1;
    }

    boolean isUnidirectional() {
        return (value & 0x02) == 2;
    }

    long streamNumber() {
        return value >> 2;
    }

    static StreamId bidirectional(boolean initiatedByPeer, long number) {
        return new StreamId((number << 2) | (initiatedByPeer ? 1 : 0));
    }
}
```

#### 5.3.2 流状态管理

```java
class Stream {
    StreamId id;
    StreamState sendState;
    StreamState recvState;

    // 发送缓冲区
    ByteBuffer sendBuffer;
    long sendOffset = 0;

    // 接收缓冲区（乱序重组）
    TreeMap<Long, byte[]> recvBuffer = new TreeMap<>();
    long recvOffset = 0;

    // 流控制
    long maxSendData;    // 对方允许我发送的最大字节数
    long maxRecvData;    // 我允许对方发送的最大字节数
    long sentData = 0;
    long recvData = 0;
}

enum StreamState {
    IDLE,
    OPEN,
    HALF_CLOSED_LOCAL,   // 本地发送完成
    HALF_CLOSED_REMOTE,  // 对方发送完成
    CLOSED
}
```

#### 5.3.3 流数据发送

```java
class StreamSender {
    void sendStreamData(Stream stream, byte[] data) throws IOException {
        // 1. 检查流控制
        if (stream.sentData + data.length > stream.maxSendData) {
            // 等待 MAX_STREAM_DATA 更新
            waitForStreamCredit(stream);
        }

        // 2. 分片（如果数据太大）
        int maxFrameSize = 1200 - HEADER_SIZE - CRYPTO_OVERHEAD;
        int offset = 0;

        while (offset < data.length) {
            int chunkSize = Math.min(maxFrameSize, data.length - offset);

            StreamFrame frame = new StreamFrame(
                stream.id,
                stream.sendOffset,
                Arrays.copyOfRange(data, offset, offset + chunkSize),
                offset + chunkSize == data.length  // FIN flag
            );

            sendFrame(frame);

            stream.sendOffset += chunkSize;
            stream.sentData += chunkSize;
            offset += chunkSize;
        }

        if (offset == data.length) {
            stream.sendState = StreamState.HALF_CLOSED_LOCAL;
        }
    }
}
```

#### 5.3.4 流数据接收与重组

```java
class StreamReceiver {
    byte[] receiveStreamData(Stream stream, StreamFrame frame) {
        // 1. 检查流控制
        if (stream.recvData + frame.data.length > stream.maxRecvData) {
            throw new FlowControlException();
        }

        // 2. 存入接收缓冲区
        stream.recvBuffer.put(frame.offset, frame.data);
        stream.recvData += frame.data.length;

        // 3. 尝试重组连续数据
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        while (!stream.recvBuffer.isEmpty()) {
            Map.Entry<Long, byte[]> entry = stream.recvBuffer.firstEntry();

            if (entry.getKey() == stream.recvOffset) {
                output.write(entry.getValue());
                stream.recvOffset += entry.getValue().length;
                stream.recvBuffer.pollFirstEntry();
            } else {
                break;  // 存在间隙，等待后续数据
            }
        }

        // 4. 更新流控制窗口
        if (stream.recvData > stream.maxRecvData / 2) {
            sendMaxStreamDataFrame(stream, stream.maxRecvData * 2);
        }

        // 5. 检查 FIN
        if (frame.fin) {
            stream.recvState = StreamState.HALF_CLOSED_REMOTE;
        }

        return output.toByteArray();
    }
}
```

### 5.4 流控制

#### 5.4.1 连接级流控制

```java
class ConnectionFlowControl {
    long maxData;            // 对方允许的连接最大数据量
    long dataReceived = 0;   // 已接收数据量
    long dataSent = 0;       // 已发送数据量

    void onDataReceived(int bytes) {
        dataReceived += bytes;

        // 当接收量超过窗口一半时，扩大窗口
        if (dataReceived > maxData / 2) {
            maxData *= 2;
            sendMaxDataFrame(maxData);
        }
    }

    boolean canSend(int bytes) {
        return dataSent + bytes <= maxData;
    }
}
```

#### 5.4.2 流级流控制

```java
class StreamFlowControl {
    Map<StreamId, Long> streamMaxData = new HashMap<>();
    Map<StreamId, Long> streamDataSent = new HashMap<>();

    boolean canSendOnStream(StreamId streamId, int bytes) {
        long sent = streamDataSent.getOrDefault(streamId, 0L);
        long max = streamMaxData.getOrDefault(streamId, initialMaxStreamData);

        return sent + bytes <= max;
    }

    void updateStreamWindow(StreamId streamId, long newMax) {
        streamMaxData.put(streamId, newMax);
    }
}
```

### 5.5 拥塞控制

#### 5.5.1 RTT 估算

```java
class RttEstimator {
    Duration smoothedRtt = Duration.ofMillis(50);   // 初始值（优化后适应本地/LAN 网络）
    Duration rttVar = Duration.ofMillis(25);        // RTT 方差
    Duration minRtt = Duration.ofMillis(Long.MAX_VALUE);

    void updateRtt(Duration latestRtt) {
        // 首次测量
        if (smoothedRtt == null) {
            smoothedRtt = latestRtt;
            rttVar = latestRtt.dividedBy(2);
            minRtt = latestRtt;
            return;
        }

        // 更新 min_rtt
        if (latestRtt.compareTo(minRtt) < 0) {
            minRtt = latestRtt;
        }

        // EWMA 算法（指数加权移动平均）
        Duration ackDelay = latestRtt.minus(minRtt);
        Duration adjustedRtt = latestRtt.minus(ackDelay);

        rttVar = rttVar.multipliedBy(3).plus(
            smoothedRtt.minus(adjustedRtt).abs()
        ).dividedBy(4);

        smoothedRtt = smoothedRtt.multipliedBy(7).plus(
            adjustedRtt
        ).dividedBy(8);
    }

    Duration getRto() {
        // Retransmission Timeout
        return smoothedRtt.plus(rttVar.multipliedBy(4));
    }
}
```

#### 5.5.2 CUBIC 拥塞控制

```java
class CubicCongestionControl {
    // 拥塞窗口（字节）
    long congestionWindow;
    long ssthresh;  // 慢启动阈值

    // CUBIC 参数
    static final double BETA = 0.7;
    static final double C = 0.4;

    long wMax = 0;           // 上次拥塞时的窗口大小
    Instant epochStart;      // 进入拥塞避免的时间

    enum State {
        SLOW_START,
        CONGESTION_AVOIDANCE,
        RECOVERY
    }
    State state = State.SLOW_START;

    void onAck(int ackedBytes, Duration rtt) {
        switch (state) {
            case SLOW_START:
                congestionWindow += ackedBytes;
                if (congestionWindow >= ssthresh) {
                    state = State.CONGESTION_AVOIDANCE;
                    epochStart = Instant.now();
                }
                break;

            case CONGESTION_AVOIDANCE:
                // CUBIC 增长函数
                double t = Duration.between(epochStart, Instant.now()).toMillis() / 1000.0;
                double offset = wMax - congestionWindow;
                double target = wMax + C * Math.pow(t - Math.cbrt(offset / C), 3);

                if (target > congestionWindow) {
                    congestionWindow = (long) target;
                }
                break;
        }
    }

    void onLoss() {
        // 乘性减
        wMax = congestionWindow;
        congestionWindow = (long)(congestionWindow * BETA);
        ssthresh = congestionWindow;
        state = State.RECOVERY;
        epochStart = Instant.now();
    }

    void onRecovery() {
        state = State.CONGESTION_AVOIDANCE;
    }
}
```

---

## 6. Java 实现架构

### 6.1 项目结构

```
fudp/
├── src/main/java/
│   └── fudp/
│       ├── Protocol.java              # 主入口
│       ├── crypto/
│       │   ├── CryptoDataByte.java    # 加密数据封装（复用 FC-JDK）
│       │   ├── Encryptor.java         # 加密器（复用 FC-JDK）
│       │   ├── Decryptor.java         # 解密器（复用 FC-JDK）
│       │   └── AlgorithmRegistry.java # 算法注册表（链上同步）
│       ├── session/
│       │   ├── SessionManager.java    # 会话管理器
│       │   └── FcSession.java         # 会话对象（复用 FC-JDK）
│       ├── connection/
│       │   ├── ConnectionManager.java
│       │   ├── PeerConnection.java
│       │   └── ConnectionId.java
│       ├── transport/
│       │   ├── PacketProcessor.java   # 包处理器（发送/接收）
│       │   ├── AckManager.java
│       │   ├── LossDetection.java
│       │   └── RetransmissionManager.java
│       ├── stream/
│       │   ├── StreamManager.java
│       │   ├── Stream.java
│       │   ├── StreamMultiplexer.java
│       │   └── FlowController.java
│       ├── congestion/
│       │   ├── CongestionControl.java
│       │   ├── RttEstimator.java
│       │   └── CubicController.java
│       ├── packet/
│       │   ├── Packet.java
│       │   ├── PacketHeader.java      # 21 字节简化包头
│       │   ├── Frame.java
│       │   └── frames/
│       │       ├── StreamFrame.java
│       │       ├── AckFrame.java
│       │       ├── SymKeyProposalFrame.java
│       │       ├── SymKeyAckFrame.java
│       │       └── ...
│       ├── security/
│       │   ├── ReplayProtection.java
│       │   └── PacketWindow.java
│       └── util/
│           ├── Varint.java
│           ├── ByteUtils.java
│           └── PacketNumberSet.java
├── src/test/java/
│   └── com/yourcompany/p2p/
│       ├── CryptoTest.java
│       ├── PacketTest.java
│       └── IntegrationTest.java
└── pom.xml
```

### 6.2 核心类设计

#### 6.2.1 Protocol (主入口)

```java
public class Protocol {
    private final CryptoManager cryptoManager;
    private final ConnectionManager connectionManager;
    private final TransportManager transportManager;
    private final DatagramChannel channel;
    private final ExecutorService executor;

    public Protocol(KeyPair localKeyPair, int port) throws IOException {
        this.cryptoManager = new CryptoManager(localKeyPair);
        this.connectionManager = new ConnectionManager(cryptoManager);
        this.transportManager = new TransportManager(connectionManager);
        this.channel = DatagramChannel.open();
        this.channel.bind(new InetSocketAddress(port));
        this.executor = Executors.newFixedThreadPool(4);
    }

    public void start() {
        executor.submit(this::receiveLoop);
        executor.submit(this::ackTimerLoop);
        executor.submit(this::retransmitLoop);
    }

    public Stream connect(PublicKey peerPublicKey, SocketAddress address) {
        PeerConnection conn = connectionManager.getOrCreate(peerPublicKey, address);
        return conn.openStream();
    }

    public void send(Stream stream, byte[] data) throws IOException {
        transportManager.sendStreamData(stream, data);
    }

    public byte[] receive(Stream stream) throws IOException {
        return stream.read();
    }

    private void receiveLoop() {
        ByteBuffer buffer = ByteBuffer.allocate(65536);
        while (!Thread.interrupted()) {
            try {
                buffer.clear();
                SocketAddress sender = channel.receive(buffer);
                buffer.flip();

                byte[] packetBytes = new byte[buffer.remaining()];
                buffer.get(packetBytes);

                handleIncomingPacket(packetBytes, sender);
            } catch (IOException e) {
                // Log error
            }
        }
    }

    private void handleIncomingPacket(byte[] data, SocketAddress from) {
        try {
            Packet packet = Packet.parse(data);
            PeerConnection conn = connectionManager.getByPublicKey(packet.senderPublicKey);

            if (conn == null) {
                // 新连接
                conn = connectionManager.acceptConnection(packet.senderPublicKey, from);
            }

            conn.handlePacket(packet, from);
        } catch (Exception e) {
            // Log error
        }
    }
}
```

#### 6.2.2 CipherRegistry（硬编码算法注册表）

```java
/**
 * 加密算法注册表（硬编码实现）
 *
 * 设计决策：采用硬编码方式定义算法，不依赖区块链动态查询。
 * 详见附录 C。
 */
public class CipherRegistry {
    private static final Map<AlgorithmId, CipherAlgorithm> CIPHERS = new HashMap<>();

    static {
        // 推荐算法
        CIPHERS.put(AlgorithmId.FC_AesGcm256_No1_NrC7, new AesGcm256Cipher());
        CIPHERS.put(AlgorithmId.FC_EccK1AesGcm256_No1_NrC7, new AesGcm256Cipher());
        CIPHERS.put(AlgorithmId.FC_EccK1ChaCha20_No1_NrC7, new ChaCha20Poly1305Cipher());

        // 废弃算法（仅用于解密旧数据）
        CIPHERS.put(AlgorithmId.FC_AesCbc256_No1_NrC7, new AesCbc256Cipher());
        CIPHERS.put(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7, new AesCbc256Cipher());
    }

    /**
     * 获取算法实现
     * @param algoId 算法ID
     * @return 加密算法实现
     * @throws UnsupportedAlgorithmException 未知算法
     */
    public static CipherAlgorithm getCipher(AlgorithmId algoId) {
        CipherAlgorithm cipher = CIPHERS.get(algoId);
        if (cipher == null) {
            throw new UnsupportedAlgorithmException("Unknown algorithm: " + algoId);
        }
        return cipher;
    }

    /**
     * 检查算法是否支持用于加密
     * @param algoId 算法ID
     * @return true 如果支持加密，false 如果仅支持解密
     */
    public static boolean isSupported(AlgorithmId algoId) {
        return algoId.isSupported();
    }

    /**
     * 获取推荐的 AsyTwoWay 模式算法
     */
    public static AlgorithmId getDefaultAsyAlgorithm() {
        return AlgorithmId.FC_EccK1AesGcm256_No1_NrC7;
    }

    /**
     * 获取推荐的 Symkey 模式算法
     */
    public static AlgorithmId getDefaultSymAlgorithm() {
        return AlgorithmId.FC_AesGcm256_No1_NrC7;
    }
}

/**
 * 加密算法接口
 */
public interface CipherAlgorithm {
    byte[] encrypt(byte[] key, byte[] iv, byte[] plaintext);
    byte[] decrypt(byte[] key, byte[] iv, byte[] ciphertext) throws CryptoException;
    int getIvLength();
    int getTagLength();
}

/**
 * AES-256-GCM 实现
 */
public class AesGcm256Cipher implements CipherAlgorithm {
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 16;

    @Override
    public byte[] encrypt(byte[] key, byte[] iv, byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH * 8, iv);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new CryptoException("AES-GCM encryption failed", e);
        }
    }

    @Override
    public byte[] decrypt(byte[] key, byte[] iv, byte[] ciphertext) throws CryptoException {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH * 8, iv);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new CryptoException("AES-GCM decryption failed", e);
        }
    }

    @Override
    public int getIvLength() { return IV_LENGTH; }

    @Override
    public int getTagLength() { return TAG_LENGTH; }
}

/**
 * ChaCha20-Poly1305 实现
 */
public class ChaCha20Poly1305Cipher implements CipherAlgorithm {
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 16;

    @Override
    public byte[] encrypt(byte[] key, byte[] iv, byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
            IvParameterSpec spec = new IvParameterSpec(iv);
            SecretKeySpec keySpec = new SecretKeySpec(key, "ChaCha20");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new CryptoException("ChaCha20-Poly1305 encryption failed", e);
        }
    }

    @Override
    public byte[] decrypt(byte[] key, byte[] iv, byte[] ciphertext) throws CryptoException {
        try {
            Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
            IvParameterSpec spec = new IvParameterSpec(iv);
            SecretKeySpec keySpec = new SecretKeySpec(key, "ChaCha20");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new CryptoException("ChaCha20-Poly1305 decryption failed", e);
        }
    }

    @Override
    public int getIvLength() { return IV_LENGTH; }

    @Override
    public int getTagLength() { return TAG_LENGTH; }
}
```

#### 6.2.3 SessionManager (内存存储)

```java
public class SessionManager {
    // KeyName → FudpSession 映射
    private final Map<String, FudpSession> sessionsByKeyName = new ConcurrentHashMap<>();

    // UserID (FID) → List<FudpSession> 映射
    private final Map<String, List<FudpSession>> sessionsByPeerId = new ConcurrentHashMap<>();

    // 定时清理 DEPRECATED 会话
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public SessionManager() {
        // 仅内存存储，无需持久化初始化
        // Session 在进程退出后自动清除
    }

    // ... implementation ...
}
```

**FudpSession 状态机：**

```java
// fudp/session/FudpSession.java
public class FudpSession {
    private String id;              // KeyName (sha256(key)[0:6])
    private byte[] keyBytes;        // 对称密钥（32 字节）
    private String fid;             // 对等节点 FID
    private String pubkey;          // 对等节点公钥（Hex）
    private KeyState state;         // 密钥状态
    private long birthTime;         // 创建时间
    private long lastUsed;          // 最后使用时间

    enum KeyState {
        PROPOSED,    // 已提议，等待确认
        ACTIVE,      // 正在使用
        DEPRECATED,  // 已废弃，保留60秒
    }

    // 生成 KeyName
    public void makeKeyName() {
        if (keyBytes != null) {
            byte[] hash = Hash.sha256(keyBytes);
            this.keyName = Arrays.copyOf(hash, 6);
            this.id = ByteUtils.toHex(keyName);
        }
    }
}
```

**设计优势：**
- ✅ **简化实现** - 无需持久化层，减少 ~200 行代码
- ✅ **提升安全性** - 密钥不持久化，降低泄露风险，提升前向保密性
- ✅ **符合成熟协议实践** - 与 QUIC、TLS 1.3、WireGuard 等协议一致
- ✅ **状态管理** - 完整的生命周期管理（PROPOSED → ACTIVE → DEPRECATED）
- ✅ **自动清理** - 过期会话自动删除，防止内存泄漏

#### 6.2.4 重启/地址复用下的状态重置

当对端重启但复用相同 IP/port 时，需要显式重置以下状态，避免“包号重用 / streamId 重用 / 旧会话残留”导致解密或流偏移异常：
- **重放窗口**：收到 AsyTwoWay 携带的 `SYMKEY_PROPOSAL` 视为重启信号，重置 replay window，接受新的包号。
- **流与 ACK 状态**：同一信号下清空流管理和 ACK 跟踪，避免旧的 streamId/offset、pending ACK 干扰新连接。
- **会话清理**：收到 `ERROR_SESSION_NOT_FOUND`（code=1）时仅清理 ACTIVE/DEPRECATED，会保留 PROPOSED，避免正在协商的新密钥被误删。
- **临时 AsyTwoWay**：若在 ACTIVE 会话下收到 AsyTwoWay 数据，开启 `forceAsyTwoWay` 直至新会话协商完成，避免用失效对称密钥加密。

#### 6.2.4 CryptoManager

```java
public class CryptoManager {
    private final KeyPair localKeyPair;
    private final Map<PublicKey, byte[]> ecdhCache = new ConcurrentHashMap<>();
    private final Map<byte[], SymmetricKeyEntry> symmetricKeys = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public CryptoManager(KeyPair keyPair) {
        this.localKeyPair = keyPair;
    }

    // 加密包
    public byte[] encryptPacket(Packet packet, AlgorithmId algoId, EncryptType encryptType) {
        // 使用硬编码的算法注册表
        CipherAlgorithm cipher = CipherRegistry.getCipher(algoId);

        byte[] key = deriveEncryptionKey(algoId, encryptType, packet);
        byte[] iv = generateIv(cipher.getIvLength());

        return cipher.encrypt(key, iv, packet.getPayload());
    }

    // 解密包
    public byte[] decryptPacket(Packet packet) {
        AlgorithmId algoId = AlgorithmId.fromBytes(packet.getAlgorithmId());
        EncryptType encryptType = packet.getEncryptType();

        // 使用硬编码的算法注册表
        CipherAlgorithm cipher = CipherRegistry.getCipher(algoId);

        byte[] key = deriveDecryptionKey(algoId, encryptType, packet);
        byte[] iv = packet.getIv();

        try {
            return cipher.decrypt(key, iv, packet.getCiphertext());
        } catch (CryptoException e) {
            // 尝试优雅降级
            return tryFallbackDecrypt(packet, cipher);
        }
    }

    // 优雅降级：尝试其他可能的密钥
    private byte[] tryFallbackDecrypt(Packet packet, CipherAlgorithm cipher) {
        byte[] iv = packet.getIv();

        // 尝试所有 PROPOSED 和 DEPRECATED 状态的对称密钥
        for (Map.Entry<byte[], SymmetricKeyEntry> entry : symmetricKeys.entrySet()) {
            try {
                return cipher.decrypt(entry.getValue().key, iv, packet.getCiphertext());
            } catch (CryptoException e) {
                // 继续尝试下一个
            }
        }

        // 最后尝试 ECDH 派生
        if (packet.hasSenderPublicKey()) {
            try {
                byte[] sharedSecret = getOrComputeECDH(packet.getSenderPublicKey());
                byte[] key = HKDF.expand(sharedSecret, "FUDP-v1", 32);
                return cipher.decrypt(key, iv, packet.getCiphertext());
            } catch (CryptoException e) {
                // 失败
            }
        }

        throw new DecryptionFailedException("All decryption attempts failed");
    }

    // 密钥派生（加密）
    private byte[] deriveEncryptionKey(AlgorithmId algoId, EncryptType encryptType, Packet packet) {
        switch (encryptType) {
            case AsyTwoWay:
                byte[] shared = getOrComputeECDH(packet.getPeerPublicKey());
                return HKDF.expand(shared, "FUDP-v1", 32);

            case Symkey:
                byte[] keyName = packet.getKeyName();
                SymmetricKeyEntry entry = symmetricKeys.get(keyName);
                if (entry == null) {
                    throw new KeyNotFoundException("Key not found: " + Hex.toHex(keyName));
                }
                return entry.key;

            default:
                throw new UnsupportedEncryptTypeException(encryptType);
        }
    }

    // 密钥派生（解密）
    private byte[] deriveDecryptionKey(AlgorithmId algoId, EncryptType encryptType, Packet packet) {
        switch (encryptType) {
            case AsyTwoWay:
                byte[] shared = getOrComputeECDH(packet.getSenderPublicKey());
                return HKDF.expand(shared, "FUDP-v1", 32);

            case Symkey:
                byte[] keyName = packet.getKeyName();
                SymmetricKeyEntry entry = symmetricKeys.get(keyName);
                if (entry == null) {
                    throw new KeyNotFoundException("Key not found: " + Hex.toHex(keyName));
                }
                return entry.key;

            default:
                throw new UnsupportedEncryptTypeException(encryptType);
        }
    }

    // 对称密钥管理
    public byte[] generateSymmetricKey() {
        byte[] key = new byte[32];
        random.nextBytes(key);
        return key;
    }

    public byte[] getKeyName(byte[] symmetricKey) {
        return Arrays.copyOf(Hash.sha256(symmetricKey), 6);
    }

    public void addSymmetricKey(byte[] keyName, byte[] key, KeyState state) {
        symmetricKeys.put(keyName, new SymmetricKeyEntry(key, state, Instant.now()));
    }

    public void updateKeyState(byte[] keyName, KeyState newState) {
        SymmetricKeyEntry entry = symmetricKeys.get(keyName);
        if (entry != null) {
            entry.state = newState;
            entry.lastUpdate = Instant.now();

            if (newState == KeyState.DEPRECATED) {
                // 60秒后删除
                scheduleKeyDeletion(keyName, Duration.ofSeconds(60));
            }
        }
    }

    // IV 生成
    private byte[] generateIv(int length) {
        byte[] iv = new byte[length];
        random.nextBytes(iv);
        return iv;
    }

    // ECDH 缓存
    private byte[] getOrComputeECDH(byte[] peerPublicKey) {
        return ecdhCache.computeIfAbsent(
            new PublicKeyWrapper(peerPublicKey),
            pk -> ECDHEngine.computeSharedSecret(localKeyPair.getPrivateKey(), pk.bytes)
        ).clone();
    }

    public byte[] getLocalPublicKey() {
        return localKeyPair.getPublicKey();
    }

    // 内部类
    static class SymmetricKeyEntry {
        byte[] key;
        KeyState state;
        Instant lastUpdate;

        SymmetricKeyEntry(byte[] key, KeyState state, Instant lastUpdate) {
            this.key = key;
            this.state = state;
            this.lastUpdate = lastUpdate;
        }
    }

    enum KeyState {
        PROPOSED, ACTIVE, DEPRECATED
    }

    // PublicKey 包装器（用于 Map key）
    static class PublicKeyWrapper {
        final byte[] bytes;

        PublicKeyWrapper(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return Arrays.equals(bytes, ((PublicKeyWrapper) o).bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }
}
```

#### 6.2.3 PeerConnection

```java
public class PeerConnection {
    private final PublicKey peerPublicKey;
    private SocketAddress peerAddress;
    private ConnectionState state;

    // 加密状态
    private final CryptoManager cryptoManager;
    private EncryptionMode encryptionMode = EncryptionMode.PUBLIC_KEY;
    private byte[] mySymmetricKey;
    private byte[] peerSymmetricKey;
    private byte[] activeSymmetricKey;

    // 传输状态
    private final PacketNumberSpace packetNumberSpace;
    private final AckManager ackManager;
    private final LossDetection lossDetection;
    private final RttEstimator rttEstimator;

    // 流管理
    private final StreamManager streamManager;
    private final FlowController flowController;

    // 拥塞控制
    private final CubicCongestionControl congestionControl;

    // 安全
    private final ReplayProtection replayProtection;

    public PeerConnection(PublicKey peerPublicKey, SocketAddress address, CryptoManager cryptoManager) {
        this.peerPublicKey = peerPublicKey;
        this.peerAddress = address;
        this.cryptoManager = cryptoManager;
        this.state = ConnectionState.IDLE;

        this.packetNumberSpace = new PacketNumberSpace();
        this.ackManager = new AckManager(this);
        this.lossDetection = new LossDetection(this);
        this.rttEstimator = new RttEstimator();

        this.streamManager = new StreamManager(this);
        this.flowController = new FlowController();

        this.congestionControl = new CubicCongestionControl();
        this.replayProtection = new ReplayProtection();
    }

    public void sendData(Stream stream, byte[] data) throws IOException {
        // 1. 检查是否需要提议对称密钥
        boolean shouldProposeKey = (packetNumberSpace.nextPacketNumber == 0);

        // 2. 构建 frames
        List<Frame> frames = new ArrayList<>();

        if (shouldProposeKey) {
            mySymmetricKey = cryptoManager.generateSymmetricKey();
            frames.add(new SymKeyProposalFrame(mySymmetricKey));
        }

        frames.add(new StreamFrame(stream.getId(), stream.getSendOffset(), data, false));

        // 3. 构建包
        Packet packet = buildPacket(frames);

        // 4. 加密
        byte[] encryptedPayload = encryptPayload(packet.getPayload(), packet.getPacketNumber());
        packet.setEncryptedPayload(encryptedPayload);

        // 5. 发送
        sendPacket(packet);

        // 6. 记录已发送包
        packetNumberSpace.recordSent(packet);
    }

    public void handlePacket(Packet packet, SocketAddress from) throws IOException {
        // 1. 防重放检查
        if (!replayProtection.checkAndRecord(peerPublicKey, packet.getPacketNumber(), packet.getTimestamp())) {
            throw new SecurityException("Replay attack detected");
        }

        // 2. 更新地址（支持迁移）
        if (!from.equals(peerAddress)) {
            migrateToAddress(from, packet);
        }

        // 3. 解密
        byte[] payload = decryptPayload(packet.getEncryptedPayload(), packet.getPacketNumber(), packet.getFlags());

        // 4. 解析 frames
        List<Frame> frames = Frame.parseMultiple(payload);

        // 5. 处理每个 frame
        for (Frame frame : frames) {
            handleFrame(frame);
        }

        // 6. 记录接收
        ackManager.onPacketReceived(packet.getPacketNumber());

        // 7. 更新 RTT
        if (packet.containsAck()) {
            updateRtt(packet.getAckFrame());
        }
    }

    private void handleFrame(Frame frame) {
        switch (frame.getType()) {
            case STREAM:
                StreamFrame sf = (StreamFrame) frame;
                streamManager.onStreamData(sf);
                break;

            case ACK:
                AckFrame af = (AckFrame) frame;
                onAckReceived(af);
                break;

            case SYMKEY_PROPOSAL:
                SymKeyProposalFrame kf = (SymKeyProposalFrame) frame;
                onSymKeyProposal(kf.getSymmetricKey());
                break;

            case CONNECTION_CLOSE:
                onConnectionClose((ConnectionCloseFrame) frame);
                break;

            // ... 其他 frame 类型
        }
    }

    private void onSymKeyProposal(byte[] proposedKey) {
        peerSymmetricKey = proposedKey;
        negotiateSymmetricKey();
    }

    private void negotiateSymmetricKey() {
        if (mySymmetricKey != null && peerSymmetricKey != null) {
            // 确定性选择
            int comparison = comparePublicKeys(cryptoManager.getLocalPublicKey(), peerPublicKey);

            if (comparison > 0) {
                activeSymmetricKey = mySymmetricKey;
            } else {
                activeSymmetricKey = peerSymmetricKey;
            }

            encryptionMode = EncryptionMode.SYMMETRIC_NEGOTIATED;
        }
    }

    private byte[] encryptPayload(byte[] payload, long packetNumber) {
        switch (encryptionMode) {
            case PUBLIC_KEY:
                return cryptoManager.encryptWithPublicKey(peerPublicKey, payload, packetNumber);

            case SYMMETRIC_NEGOTIATED:
                return cryptoManager.encryptWithSymmetricKey(activeSymmetricKey, payload, packetNumber);

            default:
                throw new IllegalStateException("Unknown encryption mode");
        }
    }

    private byte[] decryptPayload(byte[] ciphertext, long packetNumber, byte flags) {
        EncryptionMode mode = extractEncryptionMode(flags);

        switch (mode) {
            case PUBLIC_KEY:
                return cryptoManager.decryptWithPublicKey(peerPublicKey, ciphertext, packetNumber);

            case SYMMETRIC_NEGOTIATED:
                if (activeSymmetricKey == null) {
                    throw new IllegalStateException("No symmetric key available");
                }
                return cryptoManager.decryptWithSymmetricKey(activeSymmetricKey, ciphertext, packetNumber);

            default:
                throw new IllegalStateException("Unknown encryption mode");
        }
    }
}
```

### 6.3 依赖库

```xml
<!-- pom.xml -->
<dependencies>
    <!-- 加密库 -->
    <dependency>
        <groupId>org.bouncycastle</groupId>
        <artifactId>bcprov-jdk18on</artifactId>
        <version>1.77</version>
    </dependency>

    <!-- 或使用 Google Tink -->
    <dependency>
        <groupId>com.google.crypto.tink</groupId>
        <artifactId>tink</artifactId>
        <version>1.11.0</version>
    </dependency>

    <!-- 日志 -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
        <version>2.0.9</version>
    </dependency>

    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
        <version>1.4.14</version>
    </dependency>

    <!-- 测试 -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.10.1</version>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-core</artifactId>
        <version>5.8.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## 7. 使用示例

### 7.1 基本使用

```java
// 节点 A
KeyPair keyPairA = KeyPair.generate();
Protocol protocolA = new Protocol(keyPairA, 5000);
protocolA.start();

// 节点 B
KeyPair keyPairB = KeyPair.generate();
Protocol protocolB = new Protocol(keyPairB, 5001);
protocolB.start();

// A 连接到 B
PublicKey publicKeyB = keyPairB.getPublicKey();
SocketAddress addressB = new InetSocketAddress("localhost", 5001);
Stream streamAB = protocolA.connect(publicKeyB, addressB);

// A 发送数据
protocolA.send(streamAB, "Hello from A".getBytes());

// B 接收数据（在回调中）
protocolB.onStreamData(stream -> {
    byte[] data = stream.read();
    System.out.println("Received: " + new String(data));

    // B 回复
    stream.write("Hello from B".getBytes());
});
```

### 7.2 高级用法

```java
// 配置传输参数
TransportParameters params = TransportParameters.builder()
    .initialMaxData(1_000_000)              // 1 MB 连接级窗口
    .initialMaxStreamDataBidiLocal(500_000) // 500 KB 流级窗口
    .initialMaxStreamsBidi(100)             // 最多 100 个双向流
    .maxIdleTimeout(Duration.ofSeconds(30)) // 30 秒空闲超时
    .build();

Protocol protocol = new Protocol(keyPair, 5000, params);

// 多流传输
Stream stream1 = protocol.connect(peerKey, peerAddr);
Stream stream2 = protocol.openStream(connection);  // 在已有连接上开新流

protocol.send(stream1, data1);
protocol.send(stream2, data2);  // 并行传输

// 密钥轮换
protocol.rotateSymmetricKey(connection);
```

---

## 8. 性能指标

### 8.1 目标性能

| 指标 | 目标值 | 测试环境 |
|------|--------|---------|
| 握手延迟 | 0-RTT (首包) | - |
| 加密吞吐量 | > 1 Gbps | 单核，ChaCha20 |
| 包处理延迟 | < 100 μs | 1KB 包 |
| 连接建立 | < 1 ms | 本地网络 |
| 内存占用 | < 100 KB/连接 | 稳定状态 |
| CPU 占用 | < 5% | 100 Mbps 吞吐 |

### 8.2 性能优化点

1. **零拷贝 I/O**
   - 使用 `DirectByteBuffer`
   - NIO 异步 I/O

2. **ECDH 缓存**
   - 计算一次 ECDH，缓存共享密钥
   - 节省 ~50 μs/包

3. **批量处理**
   - ACK 合并
   - 包批量发送（GSO）

4. **对象池**
   - 复用 ByteBuffer
   - 复用 Packet 对象

5. **无锁数据结构**
   - ConcurrentHashMap
   - LockFree Queue

---

## 9. 安全性分析

### 9.1 威胁模型

| 威胁 | 防护措施 | 强度 |
|------|---------|------|
| 窃听 | ChaCha20-Poly1305 加密 | 高 |
| 篡改 | AEAD 认证 | 高 |
| 重放攻击 | 包序列号 + 时间戳 + 滑动窗口 | 高 |
| 中间人攻击 | 公钥身份绑定 | 高 |
| DDoS 放大 | 首包大小限制 | 中 |
| 身份伪造 | 公钥加密隐式认证 | 高 |

### 9.2 密钥管理

- **密钥生成**: `SecureRandom` + 高熵源
- **密钥存储**: 加密存储私钥（可选 HSM）
- **密钥轮换**: 每小时或每 1 GB 数据
- **密钥销毁**: 及时清零内存

### 9.3 合规性

- **前向保密**: ✅ 支持（通过密钥轮换）
- **抗量子**: ⚠️ 不抗量子（未来可升级到 Kyber）
- **审计**: 支持密钥使用日志

---

## 10. 测试策略

### 10.1 单元测试

```java
@Test
void testPacketEncryptionDecryption() {
    KeyPair kpA = KeyPair.generate();
    KeyPair kpB = KeyPair.generate();

    CryptoManager cmA = new CryptoManager(kpA);
    CryptoManager cmB = new CryptoManager(kpB);

    byte[] plaintext = "test data".getBytes();
    long packetNumber = 123;

    // A 加密
    byte[] ciphertext = cmA.encryptWithPublicKey(kpB.getPublicKey(), plaintext, packetNumber);

    // B 解密
    byte[] decrypted = cmB.decryptWithPublicKey(kpA.getPublicKey(), ciphertext, packetNumber);

    assertArrayEquals(plaintext, decrypted);
}

@Test
void testStreamReassembly() {
    Stream stream = new Stream(StreamId.bidirectional(false, 0));

    // 乱序接收
    stream.onData(new StreamFrame(stream.getId(), 10, "world".getBytes(), false));
    stream.onData(new StreamFrame(stream.getId(), 0, "hello ".getBytes(), false));
    stream.onData(new StreamFrame(stream.getId(), 5, "cruel ".getBytes(), false));

    byte[] data = stream.read();
    assertEquals("hello cruel world", new String(data));
}
```

### 10.2 集成测试

```java
@Test
void testEndToEndCommunication() throws Exception {
    // 启动两个节点
    Protocol nodeA = new Protocol(KeyPair.generate(), 0);
    Protocol nodeB = new Protocol(KeyPair.generate(), 0);

    nodeA.start();
    nodeB.start();

    // A 连接 B
    Stream stream = nodeA.connect(
        nodeB.getPublicKey(),
        nodeB.getLocalAddress()
    );

    // A 发送
    nodeA.send(stream, "test message".getBytes());

    // B 接收
    CompletableFuture<byte[]> received = new CompletableFuture<>();
    nodeB.onStreamData(s -> received.complete(s.read()));

    byte[] data = received.get(5, TimeUnit.SECONDS);
    assertEquals("test message", new String(data));
}
```

### 10.3 压力测试

```java
@Test
void testHighThroughput() throws Exception {
    Protocol sender = new Protocol(KeyPair.generate(), 0);
    Protocol receiver = new Protocol(KeyPair.generate(), 0);

    sender.start();
    receiver.start();

    Stream stream = sender.connect(receiver.getPublicKey(), receiver.getLocalAddress());

    // 发送 1 GB 数据
    int totalBytes = 1024 * 1024 * 1024;
    int chunkSize = 1024;
    byte[] chunk = new byte[chunkSize];

    long startTime = System.nanoTime();

    for (int i = 0; i < totalBytes / chunkSize; i++) {
        sender.send(stream, chunk);
    }

    long endTime = System.nanoTime();
    double throughput = totalBytes / ((endTime - startTime) / 1e9) / 1024 / 1024;

    System.out.printf("Throughput: %.2f MB/s%n", throughput);
    assertTrue(throughput > 100);  // 至少 100 MB/s
}
```

### 10.4 安全测试

```java
@Test
void testReplayAttackPrevention() {
    PeerConnection conn = createTestConnection();
    Packet packet = createTestPacket(100);

    // 首次接收成功
    assertTrue(conn.handlePacket(packet, peerAddress));

    // 重放攻击被阻止
    assertThrows(SecurityException.class, () -> {
        conn.handlePacket(packet, peerAddress);
    });
}

@Test
void testTimestampValidation() {
    PeerConnection conn = createTestConnection();

    // 时间戳太旧
    Packet oldPacket = createPacketWithTimestamp(System.currentTimeMillis() - 60000);
    assertThrows(SecurityException.class, () -> conn.handlePacket(oldPacket, peerAddress));

    // 时间戳太新
    Packet futurePacket = createPacketWithTimestamp(System.currentTimeMillis() + 60000);
    assertThrows(SecurityException.class, () -> conn.handlePacket(futurePacket, peerAddress));
}
```

---

## 11. 部署与运维

### 11.1 配置参数

```java
// config.properties
p2p.port=5000
p2p.max_connections=1000
p2p.max_streams_per_connection=100
p2p.initial_max_data=10485760  # 10 MB
p2p.idle_timeout=30000          # 30 秒
p2p.keep_alive_interval=10000   # 10 秒
p2p.congestion_control=cubic
p2p.enable_migration=true
p2p.symkey_rotation_interval=3600000  # 1 小时
```

### 11.2 监控指标

```java
public class Metrics {
    // 连接指标
    Counter connectionsActive;
    Counter connectionsTotal;
    Histogram connectionDuration;

    // 传输指标
    Counter bytesSent;
    Counter bytesReceived;
    Counter packetsSent;
    Counter packetsReceived;
    Counter packetsLost;

    // 性能指标
    Histogram rtt;
    Histogram encryptionLatency;
    Gauge congestionWindow;

    // 安全指标
    Counter replayAttacksBlocked;
    Counter invalidPackets;
}
```

### 11.3 日志

```java
logger.info("Connection established: peer={}, address={}", peerPublicKey, address);
logger.debug("Packet sent: pn={}, size={}, encryption={}", packetNumber, size, mode);
logger.warn("Packet loss detected: lost={}, rtt={}", lostPackets.size(), rtt);
logger.error("Crypto error: {}", exception.getMessage());
```

---

## 12. 未来扩展

### 12.1 功能路线图

**版本 1.0**（MVP）
- ✅ 基础加密通信
- ✅ 可靠传输
- ✅ 流复用
- ✅ 基础流控制

**版本 1.1**
- 🔲 BBR 拥塞控制
- 🔲 0-RTT 优化
- 🔲 FEC（前向纠错）
- 🔲 NAT 穿透优化

**版本 2.0**
- 🔲 抗量子密码（Kyber + Dilithium）
- 🔲 多路径传输（类似 MPTCP）
- 🔲 硬件加速（AES-NI, AVX）
- 🔲 DPDK 支持

### 12.2 兼容性计划

- **版本协商**: 支持多版本共存
- **特性协商**: 可选特性标志位
- **降级策略**: 优雅回退到旧版本

---

## 13. 参考资料

### 13.1 相关协议

- **QUIC**: RFC 9000 (传输层设计参考)
- **TLS 1.3**: RFC 8446 (密码学参考)
- **Noise Protocol**: https://noiseprotocol.org/ (握手设计参考)
- **WireGuard**: https://www.wireguard.com/ (对等加密参考)

### 13.2 密码学库

- **Bouncy Castle**: https://www.bouncycastle.org/
- **Google Tink**: https://github.com/google/tink
- **libsodium**: https://libsodium.gitbook.io/

### 13.3 论文

- "The QUIC Transport Protocol: Design and Internet-Scale Deployment" (SIGCOMM 2017)
- "CUBIC: A New TCP-Friendly High-Speed TCP Variant" (ACM OSR 2008)
- "Noise Protocol Framework" (Trevor Perrin, 2018)

---

## 附录 A: 错误码定义

```java
public enum ErrorCode {
    NO_ERROR(0x00),
    INTERNAL_ERROR(0x01),
    CRYPTO_ERROR(0x02),
    FLOW_CONTROL_ERROR(0x03),
    STREAM_LIMIT_ERROR(0x04),
    PROTOCOL_VIOLATION(0x05),
    INVALID_PACKET(0x06),
    REPLAY_ATTACK(0x07),
    CONNECTION_TIMEOUT(0x08),
    INVALID_MIGRATION(0x09),
    KEY_ROTATION_ERROR(0x0A);

    private final int code;

    ErrorCode(int code) {
        this.code = code;
    }
}
```

---

## 附录 B: 性能调优清单

- [ ] 启用 ECDH 缓存
- [ ] 使用 DirectByteBuffer
- [ ] 配置合理的拥塞窗口初始值
- [ ] 调整流控制窗口大小
- [ ] 启用批量 ACK
- [ ] 使用对象池减少 GC
- [ ] 配置合理的线程池大小
- [ ] 启用 JIT 编译优化
- [ ] 使用高性能序列化库
- [ ] 监控并调整 GC 参数

---

## 附录 C: 加密算法定义（硬编码）

本协议采用**硬编码方式**定义加密算法，不依赖区块链动态查询。这一设计决策基于以下考量：

### C.1 设计理由

**为什么选择硬编码：**

1. **降低复杂性** - 无需维护区块链客户端连接、缓存同步、网络重试逻辑
2. **提高安全性** - 只使用经过充分审计的算法，避免动态加载带来的攻击面
3. **确定性行为** - 启动即可用，无网络延迟和不确定性
4. **简化部署** - 节点无需访问区块链即可正常工作

**保留链上注册的价值：**

开发者仍可将算法定义注册到区块链（FEIP0_Protocol），用于：
- 文档化和公告新算法
- 不同实现之间的互操作性协商
- 算法引入历史的追溯

但节点运行时只使用硬编码的实现。

### C.2 算法 ID 定义

```java
public enum AlgorithmId {
    // 6 字节 Algorithm ID
    FC_AesCbc256_No1_NrC7(0x01, false),           // 不推荐
    FC_EccK1AesCbc256_No1_NrC7(0x02, false),      // 已废弃，不兼容
    FC_AesGcm256_No1_NrC7(0x03, true),            // 对称加密，推荐
    FC_EccK1AesGcm256_No1_NrC7(0x04, true),       // ECDH加密，推荐
    FC_EccK1ChaCha20_No1_NrC7(0x05, true);        // ECDH+ChaCha20，高性能场景

    private final int id;
    private final boolean supported;  // 是否支持用于加密（false = 仅解密）

    AlgorithmId(int id, boolean supported) {
        this.id = id;
        this.supported = supported;
    }

    public byte[] toBytes() {
        byte[] bytes = new byte[6];
        bytes[5] = (byte) id;
        return bytes;
    }

    public static AlgorithmId fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 6) {
            throw new IllegalArgumentException("Invalid algorithm ID bytes");
        }
        int id = bytes[5] & 0xFF;
        for (AlgorithmId algo : values()) {
            if (algo.id == id) {
                return algo;
            }
        }
        throw new UnsupportedAlgorithmException("Unknown algorithm ID: " + id);
    }

    public boolean isSupported() {
        return supported;
    }
}
```

### C.3 算法详细规格

| Algorithm ID | 名称 | 对称加密 | 密钥派生 | 状态 | 用途 |
|-------------|------|---------|---------|------|------|
| 0x01 | FC_AesCbc256 | AES-256-CBC | - | 废弃 | 仅解密旧数据 |
| 0x02 | FC_EccK1AesCbc256 | AES-256-CBC | ECDH-SHA256 | 废弃 | 仅解密旧数据 |
| 0x03 | FC_AesGcm256 | AES-256-GCM | - | 推荐 | Symkey 模式 |
| 0x04 | FC_EccK1AesGcm256 | AES-256-GCM | ECDH-HKDF-SHA256 | 推荐 | AsyTwoWay 模式 |
| 0x05 | FC_EccK1ChaCha20 | ChaCha20-Poly1305 | ECDH-HKDF-SHA256 | 支持 | 高性能场景 |

**推荐配置：**
- AsyTwoWay 模式（首包/身份认证）: `FC_EccK1AesGcm256_No1_NrC7` (0x04)
- Symkey 模式（会话加密）: `FC_AesGcm256_No1_NrC7` (0x03)

### C.4 算法实现接口

```java
public interface CipherAlgorithm {
    /**
     * 加密数据
     * @param key 密钥（32 字节）
     * @param iv 初始化向量（12 字节 for GCM/ChaCha20）
     * @param plaintext 明文
     * @return 密文 + Auth Tag
     */
    byte[] encrypt(byte[] key, byte[] iv, byte[] plaintext);

    /**
     * 解密数据
     * @param key 密钥（32 字节）
     * @param iv 初始化向量
     * @param ciphertext 密文 + Auth Tag
     * @return 明文
     * @throws CryptoException 解密失败或认证失败
     */
    byte[] decrypt(byte[] key, byte[] iv, byte[] ciphertext) throws CryptoException;

    /**
     * 获取 IV 长度
     */
    int getIvLength();

    /**
     * 获取 Auth Tag 长度
     */
    int getTagLength();
}

// 算法实现注册
public class CipherRegistry {
    private static final Map<AlgorithmId, CipherAlgorithm> CIPHERS = new HashMap<>();

    static {
        CIPHERS.put(AlgorithmId.FC_AesGcm256_No1_NrC7, new AesGcm256Cipher());
        CIPHERS.put(AlgorithmId.FC_EccK1AesGcm256_No1_NrC7, new AesGcm256Cipher());
        CIPHERS.put(AlgorithmId.FC_EccK1ChaCha20_No1_NrC7, new ChaCha20Poly1305Cipher());
        // 废弃算法仅用于解密
        CIPHERS.put(AlgorithmId.FC_AesCbc256_No1_NrC7, new AesCbc256Cipher());
        CIPHERS.put(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7, new AesCbc256Cipher());
    }

    public static CipherAlgorithm getCipher(AlgorithmId algoId) {
        CipherAlgorithm cipher = CIPHERS.get(algoId);
        if (cipher == null) {
            throw new UnsupportedAlgorithmException(algoId.name());
        }
        return cipher;
    }
}
```

### C.5 版本升级策略

**添加新算法的流程：**

1. **协议层面** - 在 `AlgorithmId` 枚举中添加新值
2. **实现层面** - 在 `CipherRegistry` 中注册实现
3. **发布新版本** - 节点升级后自动支持新算法
4. **链上公告**（可选）- 开发者可将新算法注册到 FEIP0_Protocol 进行公告

**废弃旧算法的流程：**

1. 将 `supported` 设为 `false`，禁止用于加密
2. 保留解密能力一段时间（如 1 年）
3. 最终版本完全移除

**未来扩展（抗量子）：**

当需要支持抗量子算法时：
- 添加新的 Algorithm ID（如 0x10 = Kyber-1024 + AES-GCM）
- 实现相应的密钥封装机制
- 通过协议版本升级引入

### C.6 与 Freeverse 生态的兼容性

FUDP 的 Algorithm ID 与 FC-JDK 中的 `AlgorithmId` 枚举保持一致，确保：

- CryptoDataByte Bundle 格式兼容
- 密文可跨服务解密（APIP、DISK、TALK）
- 密文格式统一（CryptoDataByte Bundle）

```java
// FC-JDK 中的对应定义
// data/fcData/AlgorithmId.java
public enum AlgorithmId {
    FC_AesCbc256_No1_NrC7,      // 已废弃
    FC_EccK1AesCbc256_No1_NrC7, // 已废弃
    FC_AesGcm256_No1_NrC7,      // 推荐（对称加密）
    FC_EccK1AesGcm256_No1_NrC7, // 推荐（FUDP 默认）
    FC_EccK1ChaCha20_No1_NrC7;  // 高性能场景
    // ...
}
```

---

**文档版本**: 2.1
**最后更新**: 2025-12-21
**变更记录**:
- v2.1: **性能优化** - ACK 机制和消息去重增强
  - ACK 参数优化: `MAX_ACK_DELAY_MS` 25→10ms, `ACK_THRESHOLD` 2→1
  - 消息去重增强: 使用复合键 `peerId:messageId` 避免跨节点 ID 冲突
  - RTT 估计优化: 初始值从 333ms 降至 50ms，适应本地/LAN 网络
  - 添加时间戳支持定期清理过期消息 ID
- v2.0: **重大简化** - 移除 Symkey 加密模式，仅使用 AsyTwoWay（ECDH）加密
  - 删除 SessionManager, FudpSession, KeyState
  - 删除 SYMKEY_PROPOSAL, SYMKEY_ACK 帧
  - 删除密钥协商流程和状态机
  - 简化 Protocol, PacketCrypto, CryptoManager
  - 代码量减少约 800 行
- v1.3: 移除 Session 持久化，简化状态机（移除 ACCEPTED 状态），优化密钥协商流程
- v1.2: 采用硬编码算法设计，简化 AlgorithmRegistry 和 CryptoManager，移除区块链动态查询依赖
- v1.1: 添加 Algorithm ID 和 Key ID 支持，更新密钥协商流程
- v1.0: 初始版本

**维护者**: Freeverse Team
**状态**: Production
