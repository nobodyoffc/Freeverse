# FAPI API 参考文档

> 版本: 1.0
> 日期: 2024年12月
> 状态: 已实现

## 1. 概述

FAPI（Freecash API）是基于 FUDP 协议的去中心化 API 服务框架。本文档描述了 FAPI 提供的所有 API 接口。

### 1.1 请求格式

所有请求使用 JSON 格式：

```json
{
  "id": "req-xxx",           // 请求ID（客户端生成，用于追踪）
  "api": "component.method", // API名称
  "fcdsl": { ... },          // 查询参数（查询类API）
  "params": { ... }          // 操作参数（操作类API）
}
```

### 1.2 响应格式

```json
{
  "id": "resp-xxx",          // 响应ID（服务端生成）
  "requestId": "req-xxx",    // 对应请求ID
  "code": 0,                 // 状态码（0=成功）
  "message": "Success",      // 状态消息
  "data": { ... },           // 响应数据
  "got": 10,                 // 返回数量
  "total": 100,              // 总数量
  "last": ["..."],           // 分页游标
  "bestHeight": 1234567,     // 最佳区块高度
  "balance": 100000000       // 账户余额（satoshi）
}
```

### 1.3 状态码

| 状态码 | 说明 |
|--------|------|
| 0 | 成功 |
| 1 | 通用错误 |
| 400 | 请求格式错误 |
| 401 | 未授权 |
| 402 | 余额不足 |
| 403 | 禁止访问 |
| 404 | 未找到 |
| 405 | 方法不允许 |
| 413 | 请求体过大 |
| 429 | 请求过于频繁 |
| 500 | 服务器内部错误 |
| 503 | 服务不可用 |

---

## 2. BASE 组件 API

BASE 组件提供链上基础数据查询服务。

### 2.1 base.health

健康检查接口。

**请求**：
```json
{
  "id": "req-001",
  "api": "base.health"
}
```

**响应**：
```json
{
  "code": 0,
  "data": {
    "timestamp": 1703664000000,
    "overallHealthy": true,
    "components": {
      "BASE": {
        "name": "BASE",
        "state": "RUNNING",
        "healthy": true,
        "apiCount": 16
      }
    },
    "fudpRunning": true,
    "esConnected": true,
    "rpcConnected": true,
    "connectedPeers": 5
  }
}
```

### 2.2 base.totals

获取所有 ES 索引的文档计数。

**请求**：
```json
{
  "id": "req-002",
  "api": "base.totals"
}
```

**响应**：
```json
{
  "code": 0,
  "data": {
    "block": 1234567,
    "tx": 5678900,
    "cash": 12345678,
    "freer": 100000
  }
}
```

### 2.3 base.getByIds

根据 ID 列表查询实体。

**请求**：
```json
{
  "id": "req-003",
  "api": "base.getByIds",
  "fcdsl": {
    "entity": "block",
    "ids": ["000000000000...", "000000000001..."]
  }
}
```

**响应**：
```json
{
  "code": 0,
  "data": [
    { "height": 1234567, "hash": "000000...", ... },
    { "height": 1234568, "hash": "000001...", ... }
  ],
  "got": 2,
  "total": 2
}
```

**支持的 entity 类型**：
- `block` - 区块
- `tx` - 交易
- `cash` - UTXO
- `freer` - 用户信息
- `service` - 服务
- `group` - 群组
- `team` - 团队
- 等等...

### 2.4 base.search

通用搜索接口。

**请求**：
```json
{
  "id": "req-004",
  "api": "base.search",
  "fcdsl": {
    "entity": "tx",
    "filter": {
      "must": [
        { "term": { "height": 1234567 } }
      ]
    },
    "sort": [
      { "txIndex": "asc" }
    ],
    "size": "20"
  }
}
```

**响应**：
```json
{
  "code": 0,
  "data": [...],
  "got": 20,
  "total": 150,
  "last": ["cursor1", "cursor2"]
}
```

### 2.5 base.balanceByIds

根据 FID 列表查询余额。

**请求**：
```json
{
  "id": "req-005",
  "api": "base.balanceByIds",
  "fcdsl": {
    "ids": ["FEk41Aik3q9L4jVR2BL6iZzJuQ8fAGvmb2", "FGw2xxxx"]
  }
}
```

**响应**：
```json
{
  "code": 0,
  "data": {
    "FEk41Aik3q9L4jVR2BL6iZzJuQ8fAGvmb2": 100000000,
    "FGw2xxxx": 50000000
  }
}
```

### 2.6 base.cashValid

获取有效的 UTXO（Cash 格式）。

**请求**：
```json
{
  "id": "req-006",
  "api": "base.cashValid",
  "fcdsl": {
    "ids": ["FEk41Aik3q9L4jVR2BL6iZzJuQ8fAGvmb2"],
    "size": "50"
  }
}
```

**响应**：
```json
{
  "code": 0,
  "data": [
    {
      "id": "txid:vout",
      "owner": "FEk41...",
      "value": 10000000,
      "birthHeight": 1234567,
      "valid": true
    }
  ],
  "got": 50,
  "total": 100
}
```

### 2.7 base.getUtxo

获取 UTXO（Utxo 格式，用于构建交易）。

**请求**：
```json
{
  "id": "req-007",
  "api": "base.getUtxo",
  "fcdsl": {
    "ids": ["FEk41Aik3q9L4jVR2BL6iZzJuQ8fAGvmb2"]
  }
}
```

**响应**：
```json
{
  "code": 0,
  "data": [
    {
      "txId": "abc123...",
      "index": 0,
      "value": 10000000,
      "script": "76a914..."
    }
  ]
}
```

### 2.8 base.chainInfo

获取链信息。

**请求**：
```json
{
  "id": "req-008",
  "api": "base.chainInfo"
}
```

**响应**：
```json
{
  "code": 0,
  "data": {
    "height": 1234567,
    "hash": "000000...",
    "difficulty": 12345678.90,
    "hashRate": "1.23 EH/s",
    "circulatingSupply": 2100000000000000
  }
}
```

### 2.9 base.blockTimeHistory

获取出块时间历史。

**请求**：
```json
{
  "id": "req-009",
  "api": "base.blockTimeHistory",
  "params": {
    "startTime": 1700000000,
    "endTime": 1703664000,
    "count": 100
  }
}
```

**响应**：
```json
{
  "code": 0,
  "data": [
    { "time": 1700000000, "avgBlockTime": 600 },
    { "time": 1700100000, "avgBlockTime": 580 }
  ]
}
```

### 2.10 base.difficultyHistory

获取难度历史。

**请求**：
```json
{
  "id": "req-010",
  "api": "base.difficultyHistory",
  "params": {
    "startTime": 1700000000,
    "endTime": 1703664000,
    "count": 100
  }
}
```

### 2.11 base.hashRateHistory

获取算力历史。

**请求**：
```json
{
  "id": "req-011",
  "api": "base.hashRateHistory",
  "params": {
    "startTime": 1700000000,
    "endTime": 1703664000,
    "count": 100
  }
}
```

### 2.12 base.unconfirmed

获取未确认交易信息。

**请求**：
```json
{
  "id": "req-012",
  "api": "base.unconfirmed",
  "fcdsl": {
    "ids": ["FEk41Aik3q9L4jVR2BL6iZzJuQ8fAGvmb2"]
  }
}
```

**响应**：
```json
{
  "code": 0,
  "data": {
    "FEk41...": {
      "income": 10000000,
      "spend": 5000000,
      "net": 5000000,
      "txCount": 3
    }
  }
}
```

### 2.13 base.unconfirmedCashes

获取未确认的 Cash。

**请求**：
```json
{
  "id": "req-013",
  "api": "base.unconfirmedCashes",
  "fcdsl": {
    "ids": ["FEk41Aik3q9L4jVR2BL6iZzJuQ8fAGvmb2"]
  }
}
```

### 2.14 base.broadcastTx

广播交易。

**请求**：
```json
{
  "id": "req-014",
  "api": "base.broadcastTx",
  "params": {
    "rawTx": "0100000001..."
  }
}
```

**响应**：
```json
{
  "code": 0,
  "data": {
    "txId": "abc123..."
  }
}
```

### 2.15 base.decodeTx

解码交易。

**请求**：
```json
{
  "id": "req-015",
  "api": "base.decodeTx",
  "params": {
    "rawTx": "0100000001..."
  }
}
```

**响应**：
```json
{
  "code": 0,
  "data": {
    "txId": "abc123...",
    "version": 1,
    "locktime": 0,
    "inputs": [...],
    "outputs": [...]
  }
}
```

### 2.16 base.estimateFee

估算交易费用。

**请求**：
```json
{
  "id": "req-016",
  "api": "base.estimateFee",
  "params": {
    "blocks": 6
  }
}
```

**响应**：
```json
{
  "code": 0,
  "data": {
    "feeRate": 1.0,
    "blocks": 6
  }
}
```

---

## 3. 输入验证规则

### 3.1 通用规则

| 参数 | 限制 |
|------|------|
| IDs 数量 | 最多 100 个 |
| 分页大小 | 最多 100 条 |
| Entity 名称 | 最长 64 字符 |
| 批量操作 | 最多 50 项 |
| 文件大小 | 最大 10MB |

### 3.2 API 名称格式

- 必须包含 `.` 分隔符
- 格式: `component.method`
- 组件名不区分大小写

### 3.3 查询类 API

以下方法需要 `fcdsl` 参数：
- `getByIds` - 需要 `entity` 和 `ids`
- `search` - 需要 `entity`
- `list` - 可选参数

以下方法不需要参数：
- `health`
- `totals`

### 3.4 操作类 API

以下方法需要 `params` 参数：
- `put`
- `batchPut`
- `get`
- `delete`
- `broadcastTx`
- `decodeTx`

---

## 4. 监控与运维

### 4.1 健康检查

通过 `base.health` API 获取服务健康状态：
- 组件状态
- 外部依赖连接状态
- FUDP 节点状态

### 4.2 性能指标

服务端内部收集以下指标：
- 请求总数（成功/失败）
- 响应时间统计（平均/最小/最大）
- QPS（每秒请求数）
- 组件级别统计
- API 级别统计

### 4.3 审计日志

敏感操作会记录审计日志：
- 请求追踪 ID
- 请求方 FID
- API 名称
- 操作结果

---

## 5. 更新记录

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0 | 2024-12-27 | 初始版本，包含 BASE 组件所有 API |

