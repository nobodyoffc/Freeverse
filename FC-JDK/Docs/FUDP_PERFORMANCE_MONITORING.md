# FUDP 性能监测指南

## 概述

FUDP 提供了完善的性能监测功能，用于评估网络质量、诊断连接问题和优化传输性能。

## 功能特性

### 1. RTT (往返时间) 监测

| 指标 | 说明 | 获取方式 |
|------|------|----------|
| `smoothedRtt` | 平滑 RTT (EWMA 算法) | `conn.getRttEstimator().getSmoothedRtt()` |
| `minRtt` | 最小 RTT | `conn.getRttEstimator().getMinRtt()` |
| `rttVariance` | RTT 方差 | `conn.getRttEstimator().getRttVariance()` |
| `rto` | 重传超时时间 | `conn.getRttEstimator().getRto()` |

**RTT 估算算法**：使用 EWMA (指数加权移动平均)
- `smoothedRtt = 7/8 * smoothedRtt + 1/8 * latestRtt`
- `rttVariance = 3/4 * rttVariance + 1/4 * |smoothedRtt - latestRtt|`
- `RTO = smoothedRtt + 4 * rttVariance`

### 2. 丢包率统计

#### 统计指标

| 指标 | 说明 |
|------|------|
| `suspectedLostCount` | 疑似丢失（触发了超时检测） |
| `ackedAfterSuspectedLost` | 误判恢复（后来收到了 ACK） |
| `effectiveLostCount` | **有效丢失** = 疑似丢失 - 误判恢复 |
| `lossRate` | 有效丢包率 = effectiveLost / packetsSent |

#### 丢包检测逻辑

包被判定为丢失的条件（满足任一）：
1. **包序号阈值**：超过 3 个后续包已被确认
2. **时间阈值**：超过 `max(50ms, smoothedRtt × 1.5 + rttVariance)`

**注意**：只有 ACK-eliciting 包（需要确认的包）才会被跟踪和统计丢包。ACK-only 包不需要确认，不计入丢包统计。

### 3. 重传统计

| 指标 | 说明 |
|------|------|
| `retransmitCount` | 重传次数 |
| `retransmitRate` | 重传率 = retransmitCount / packetsSent |

**重传率 vs 丢包率**：
- 重传率是更可靠的网络质量指标
- 丢包率可能因延迟 ACK 而产生误判
- 建议优先使用重传率评估网络质量

### 4. 拥塞控制状态

| 指标 | 说明 |
|------|------|
| `congestionWindow` | 拥塞窗口大小 |
| `bytesInFlight` | 在途字节数 |
| `ccState` | 拥塞控制状态：SLOW_START / CONGESTION_AVOIDANCE / RECOVERY |

## API 使用

### 获取节点整体统计

```java
FudpNode node = ...;

// 获取聚合统计
NodeStats stats = node.getNodeStats();

// 连接统计
System.out.println("Total connections: " + stats.getTotalConnections());
System.out.println("Established: " + stats.getEstablishedConnections());

// 流量统计
System.out.println("Packets sent: " + stats.getTotalPacketsSent());
System.out.println("Packets received: " + stats.getTotalPacketsReceived());
System.out.println("Bytes out: " + stats.getTotalBytesOut());
System.out.println("Bytes in: " + stats.getTotalBytesIn());

// 丢包统计
System.out.println("Retransmit rate: " + stats.getRetransmitRatePercent());
System.out.println("Loss rate: " + stats.getLossRatePercent());

// RTT 统计
System.out.println("Min RTT: " + stats.getMinRttMs() + "ms");
System.out.println("Avg RTT: " + stats.getAvgSmoothedRttMs() + "ms");
System.out.println("Max RTT: " + stats.getMaxRttMs() + "ms");

// 输出完整统计
System.out.println(stats.toString());
```

### 获取单个 Peer 统计

```java
// 通过 FID 或别名获取
NodeStats.PeerStats peerStats = node.getPeerStats("FEk41Kqj...");

if (peerStats != null) {
    System.out.println("Peer: " + peerStats.getPeerId());
    System.out.println("State: " + peerStats.getState());
    System.out.println("RTT: " + peerStats.getSmoothedRttMs() + "ms");
    System.out.println("Loss: " + peerStats.getLossRatePercent());
    System.out.println("Retransmit: " + peerStats.getRetransmitRatePercent());
}
```

### 遍历所有 Peer 统计

```java
NodeStats stats = node.getNodeStats();
for (NodeStats.PeerStats ps : stats.getPeerStatsList()) {
    System.out.println(ps.toString());
}
```

### 使用 MeterListener 监听事件

```java
node.addMeterListener(record -> {
    System.out.println("Peer: " + record.getPeerId());
    System.out.println("Direction: " + record.getDirection());
    System.out.println("Bytes: " + record.getPayloadBytes());
    System.out.println("RTT: " + record.getRttMicros() + "µs");
});
```

## CLI 工具使用

启动 `StartFudpNode` 后，选择 **"Performance Stats"** 菜单：

### Node Overview

显示节点整体统计：

```
=== Node Statistics ===
Connections: 2 total (2 established, 0 establishing, 0 closing)
Packets: 150 sent, 148 received
Bytes: 45.2 KB out, 42.1 KB in
Retransmits: 3 (2.00% retransmit rate)
Loss: 5 suspected, 4 recovered, 1 effective lost (0.67% loss rate)
RTT: min=12ms, avg=25ms, max=45ms
Congestion: cwnd=36.0 KB, in-flight=2.4 KB

--- Per-Peer Summary ---
Peer[k41Kqj] state=ESTABLISHED, sent=75, recv=74, retx=1.33%, loss=0.00%, rtt=25ms(min=12ms), cc=CONGESTION_AVOIDANCE
Peer[29rYyw] state=ESTABLISHED, sent=75, recv=74, retx=2.67%, loss=1.33%, rtt=45ms(min=30ms), cc=SLOW_START
```

### Peer Details

显示指定 Peer 的详细统计：

```
=== Peer Statistics: FEk41Kqjar45fLDriztUDTUkdki7mmcjWK ===
Connection State: ESTABLISHED

--- Packet Stats ---
  Sent:     75
  Received: 74
  Bytes Out: 8.5 KB
  Bytes In:  8.2 KB

--- Retransmit & Loss Stats ---
  Retransmits:      1 (1.33% retransmit rate)
  Suspected Lost:   2 (triggered retransmit)
  Recovered (ACKed):2 (false positives)
  Effective Lost:   0 (0.00% loss rate)

--- RTT Stats ---
  Smoothed RTT: 25 ms
  Min RTT:      12 ms
  RTT Variance: 5 ms
  RTO:          45 ms

--- Congestion Control ---
  State:           CONGESTION_AVOIDANCE
  Congestion Wnd:  24.0 KB
  Bytes In Flight: 1.2 KB
```

### Ping Test

执行多次 ping 测试并统计：

```
Pinging FEk41Kqjar45fLDriztUDTUkdki7mmcjWK with 5 packets...

  Reply from FEk41Kqjar45fLDriztUDTUkdki7mmcjWK: time=23 ms
  Reply from FEk41Kqjar45fLDriztUDTUkdki7mmcjWK: time=18 ms
  Reply from FEk41Kqjar45fLDriztUDTUkdki7mmcjWK: time=21 ms
  Reply from FEk41Kqjar45fLDriztUDTUkdki7mmcjWK: time=19 ms
  Reply from FEk41Kqjar45fLDriztUDTUkdki7mmcjWK: time=22 ms

--- Ping Statistics for FEk41Kqjar45fLDriztUDTUkdki7mmcjWK ---
  Packets: Sent=5, Received=5, Lost=0 (0% loss)
  RTT: min=18ms, avg=20ms, max=23ms
```

## 技术参数

### 丢包检测参数

| 参数 | 值 | 说明 |
|------|------|------|
| `TIME_THRESHOLD_MULTIPLIER` | 1.5 | 时间阈值乘数（QUIC 标准为 1.125） |
| `MIN_TIME_THRESHOLD_MS` | 50ms | 最小时间阈值 |
| `PACKET_THRESHOLD` | 3 | 包重排序阈值 |

时间阈值计算公式：
```
timeThreshold = max(MIN_TIME_THRESHOLD_MS, smoothedRtt × TIME_THRESHOLD_MULTIPLIER + rttVariance)
```

### RTT 估算参数

| 参数 | 值 | 说明 |
|------|------|------|
| `INITIAL_RTT_MS` | 50ms | 初始 RTT 估计 |
| `MIN_RTT_MS` | 1ms | 最小 RTT 值 |

### 拥塞控制参数 (CUBIC)

| 参数 | 值 | 说明 |
|------|------|------|
| `INITIAL_WINDOW` | 12000 bytes | 初始窗口 |
| `MIN_WINDOW` | 2400 bytes | 最小窗口 |
| `MAX_WINDOW` | 100 MB | 最大窗口 |
| `BETA` | 0.7 | 丢包后窗口缩减系数 |

## 网络质量评估标准

| 指标 | 优秀 | 良好 | 一般 | 差 |
|------|------|------|------|------|
| RTT | < 50ms | 50-100ms | 100-200ms | > 200ms |
| 重传率 | < 1% | 1-5% | 5-10% | > 10% |
| 丢包率 | 0% | < 1% | 1-5% | > 5% |

## 故障排查

### 高重传率

1. 检查网络质量（ping 测试）
2. 检查 RTT 波动（rttVariance）
3. 考虑增加 `MIN_TIME_THRESHOLD_MS`

### 高丢包率但低重传率

1. 可能是 ACK 延迟到达
2. 检查 `recovered` 数量
3. 考虑调整 `TIME_THRESHOLD_MULTIPLIER`

### 拥塞窗口持续很小

1. 可能存在持续丢包
2. 检查网络拥塞情况
3. 检查 ccState 是否频繁进入 RECOVERY

## 相关文件

- `fudp/node/NodeStats.java` - 节点统计类
- `fudp/node/FudpNode.java` - 节点主类 (getNodeStats/getPeerStats)
- `fudp/connection/PeerConnection.java` - 连接统计
- `fudp/congestion/RttEstimator.java` - RTT 估算
- `fudp/congestion/CongestionControl.java` - 拥塞控制
- `fudp/StartFudpNode.java` - CLI 工具

## 更新历史

- **2025-12-21**: 初始版本
  - 新增 NodeStats 类提供聚合统计
  - 新增丢包率精确统计（区分疑似丢失和有效丢失）
  - 新增重传率统计
  - 优化丢包检测阈值（1.5 倍 RTT + 方差）
  - 修复 ACK-only 包误统计问题
  - 新增 CLI 性能统计菜单

