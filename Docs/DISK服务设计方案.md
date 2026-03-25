# DISK分布式存储服务设计方案

> 版本: 1.0
> 日期: 2024年12月
> 状态: 待实现
> 前置依赖: FAPI服务重构方案

## 1. 概述

### 1.1 服务定位

DISK是基于FAPI框架的分布式存储服务，提供文件存储和检索功能。

**服务类型声明**：
```java
service.types = ["FAPI", "BASE", "DISK"]  // 必须包含FAPI和BASE
```

### 1.2 设计原则

- **开放存储**：不验证身份和内容，不记录所有者
- **内容寻址**：did = SHA256x2(content)，相同内容只存一份
- **过期机制**：每次访问更新expire字段（延长有效期）
- **永久存储**：通过carve接口设置expire=-1
- **无显式删除**：过期数据自动清理，无需delete接口

## 2. 数据模型

### 2.1 元数据结构

```java
/**
 * DISK存储元数据
 * 存储于ES索引 `{sid}_disk_data`
 * 
 * 设计说明：
 * - 开放存储：不验证身份和内容，不记录所有者
 * - 内容寻址：did = SHA256x2(content)，相同内容只存一份
 * - 过期机制：每次访问更新expire字段（延长有效期）
 * - 永久存储：通过carve接口设置expire=-1
 */
public class DiskItem extends FcObject {
    private String did;      // 文件ID = SHA256x2(content)，内容寻址
    private Long since;      // 创建时间戳
    private Long expire;     // 过期时间戳（-1=永不过期，每次访问更新）
    private Long size;       // 文件大小（字节）
}
```

### 2.2 ES索引Mapping

**DISK元数据索引**（`{sid}_disk_data`）：

```json
{
  "mappings": {
    "properties": {
      "did": { 
        "type": "keyword",
        "doc_values": true
      },
      "since": { 
        "type": "long" 
      },
      "expire": { 
        "type": "long",
        "doc_values": true
      },
      "size": { 
        "type": "long" 
      }
    }
  },
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0
  }
}
```

**索引命名规范**：
- 服务相关索引：`{sid_brief}_{index_name}`
- sid_brief：服务ID的前8位（通过`Settings.addSidBriefToName()`生成）

### 2.3 文件存储路径

```
存储目录结构：
{storageDir}/{d[0:2]}/{d[2:4]}/{d[4:6]}/{d[6:8]}/{did}

示例：
/data/disk/ab/cd/12/34/abcd1234567890abcdef...

设计说明：
- 使用did前8位字符分4级目录，避免单目录文件过多
- did = SHA256x2(content)，相同内容只存储一份（去重）
- 文件名即为did，便于直接查找
```

## 3. API设计

### 3.1 API列表

| API | 参数类型 | 说明 |
|-----|---------|------|
| `disk.list` | fcdsl | 查询文件列表 |
| `disk.put` | params | 上传小文件（<1MB） |
| `disk.batchPut` | params | 批量上传小文件 |
| `disk.get` | params | 下载文件（支持ids列表） |
| `disk.check` | params | 检查文件是否存在（支持ids列表） |
| `disk.carve` | params | 永久保存文件（设置expire=-1） |
| `disk.prepareUpload` | params | 大文件：准备上传 |
| `disk.confirmUpload` | params | 大文件：确认上传 |
| `disk.prepareDownload` | params | 大文件：准备下载 |
| `disk.resumeUpload` | params | 断点续传：恢复上传 |

### 3.2 API详细设计

#### disk.list - 查询文件列表

```json
// 请求
{
  "id": "req-123",
  "api": "disk.list",
  "fcdsl": {
    "filter": { "range": { "size": { "gte": 1000 } } },
    "size": 20,
    "sort": [{ "since": "desc" }]
  }
}

// 响应
{
  "code": 0,
  "data": [
    { "did": "abc123...", "since": 1703580000, "expire": 1706172000, "size": 1024 }
  ],
  "got": 20,
  "total": 100
}
```

#### disk.put - 上传小文件

```json
// 请求
{
  "id": "req-123",
  "api": "disk.put",
  "params": {
    "content": "base64EncodedContent..."
  }
}

// 响应
{
  "code": 0,
  "data": {
    "did": "abcd1234567890...",
    "size": 1024
  }
}
```

#### disk.batchPut - 批量上传

```json
// 请求
{
  "id": "req-123",
  "api": "disk.batchPut",
  "params": {
    "files": [
      { "content": "base64..." },
      { "content": "base64..." }
    ]
  }
}

// 响应
{
  "code": 0,
  "data": [
    { "did": "abc123...", "size": 1024, "success": true },
    { "did": "def456...", "size": 2048, "success": true }
  ]
}
```

#### disk.get - 下载文件

```json
// 请求（单个）
{
  "id": "req-123",
  "api": "disk.get",
  "params": { "id": "abcd1234..." }
}

// 请求（批量）
{
  "id": "req-123",
  "api": "disk.get",
  "params": { "ids": ["abcd1234...", "efgh5678..."] }
}

// 响应
{
  "code": 0,
  "data": {
    "abcd1234...": "base64EncodedContent...",
    "efgh5678...": "base64EncodedContent..."
  }
}
```

#### disk.check - 检查文件存在

```json
// 请求
{
  "id": "req-123",
  "api": "disk.check",
  "params": { "ids": ["abcd1234...", "efgh5678..."] }
}

// 响应
{
  "code": 0,
  "data": {
    "abcd1234...": true,
    "efgh5678...": false
  }
}
```

#### disk.carve - 永久保存

```json
// 请求
{
  "id": "req-123",
  "api": "disk.carve",
  "params": { "id": "abcd1234..." }
}

// 响应
{
  "code": 0,
  "data": {
    "did": "abcd1234...",
    "expire": -1
  }
}
```

#### disk.prepareUpload - 准备大文件上传

```json
// 请求
{
  "id": "req-123",
  "api": "disk.prepareUpload",
  "params": {
    "size": 104857600,
    "hash": "sha256:abcd1234..."
  }
}

// 响应
{
  "code": 0,
  "data": {
    "uploadToken": "token-xxx",
    "chunkSize": 32768,
    "totalChunks": 3200,
    "timeout": 3660000
  }
}
```

#### disk.resumeUpload - 断点续传

```json
// 请求
{
  "id": "req-123",
  "api": "disk.resumeUpload",
  "params": { "uploadToken": "token-xxx" }
}

// 响应
{
  "code": 0,
  "data": {
    "uploadedChunks": [0, 1, 2, 5, 6],
    "nextChunk": 3,
    "totalChunks": 3200
  }
}
```

## 4. 组件实现

### 4.1 DiskComponent

```java
/**
 * DISK组件 - 分布式存储服务
 * 
 * 大文件传输策略：
 * 1. 小文件（<1MB）：直接通过FapiRequest/Response传输
 * 2. 大文件（>=1MB）：使用FUDP的FileHandler机制
 *    - disk.prepareUpload 返回 uploadToken
 *    - 客户端使用 FudpNode.sendFile() 发送
 *    - 服务端通过 FileHandler 接收
 * 
 * 参数传递：
 * - 查询类（list）：使用 fcdsl
 * - 操作类（put/get/check/carve等）：使用 params
 * 
 * 批量操作设计：
 * - get/check 支持 ids 列表参数，无需独立批量API
 * - batchPut 需要独立API（每个文件有独立内容）
 */
public class DiskComponent extends AbstractFapiComponent {
    private static final long LARGE_FILE_THRESHOLD = 1024 * 1024; // 1MB
    private static final String ES_INDEX_SUFFIX = "data";
    
    private DiskManager diskManager;
    private ElasticsearchClient esClient;
    private FileHandler fileHandler;
    private String storageDir;
    
    @Override
    public String getName() { return "DISK"; }
    
    @Override
    public List<String> getApiList() {
        return List.of(
            "disk.list",           // 查询文件列表（使用fcdsl）
            "disk.put",            // 上传小文件（使用params）
            "disk.batchPut",       // 批量上传小文件（使用params）
            "disk.get",            // 下载文件，支持ids列表（使用params）
            "disk.check",          // 检查文件是否存在，支持ids列表（使用params）
            "disk.carve",          // 永久保存文件（使用params）
            "disk.prepareUpload",  // 大文件：准备上传（使用params）
            "disk.confirmUpload",  // 大文件：确认上传（使用params）
            "disk.prepareDownload",// 大文件：准备下载（使用params）
            "disk.resumeUpload"    // 断点续传：恢复上传（使用params）
        );
    }
    
    @Override
    protected void doInitialize() {
        this.diskManager = new DiskManager(settings);
        this.storageDir = diskManager.getDataDir();
        this.esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);
        this.fileHandler = server.getFudpNode().getFileHandler();
    }
    
    @Override
    public boolean supportsAsync() { 
        return true; // DISK是I/O密集型，支持异步
    }
    
    @Override
    public FapiResponse handleRequest(FapiRequest request, String peerId) {
        String method = request.getMethodName();
        String requestId = request.getId();
        return switch (method) {
            case "list" -> handleList(request, peerId);
            case "check" -> handleCheck(request, peerId);
            case "put" -> handlePut(request, peerId);
            case "batchPut" -> handleBatchPut(request, peerId);
            case "get" -> handleGet(request, peerId);
            case "carve" -> handleCarve(request, peerId);
            case "prepareUpload" -> handlePrepareUpload(request, peerId);
            case "confirmUpload" -> handleConfirmUpload(request, peerId);
            case "prepareDownload" -> handlePrepareDownload(request, peerId);
            case "resumeUpload" -> handleResumeUpload(request, peerId);
            default -> FapiResponse.error(requestId, FapiCode.METHOD_NOT_ALLOWED, 
                "Method not found: " + method);
        };
    }
    
    // ... 各方法实现见下文 ...
}
```

### 4.2 方法实现

#### handleList - 文件列表查询

```java
/**
 * 处理文件列表查询
 * 请求格式：{ "id": "req-123", "api": "disk.list", "fcdsl": { "filter": {...}, "size": 20 } }
 */
private FapiResponse handleList(FapiRequest request, String peerId) {
    Fcdsl fcdsl = request.getFcdsl();
    if (fcdsl == null) {
        fcdsl = new Fcdsl();
    }
    
    String index = Settings.addSidBriefToName(settings.getSid(), ES_INDEX_SUFFIX);
    List<DiskItem> files = esClient.search(index, fcdsl, DiskItem.class);
    
    return FapiResponse.success(request.getId(), files);
}
```

#### handleCheck - 检查文件存在

```java
/**
 * 检查文件是否存在（支持批量）
 * 请求格式：{ "id": "req-123", "api": "disk.check", "params": { "ids": ["did1", "did2"] } }
 * 或单个：{ "id": "req-123", "api": "disk.check", "params": { "id": "did1" } }
 */
private FapiResponse handleCheck(FapiRequest request, String peerId) {
    DiskCheckParams params = parseParams(request.getParams(), DiskCheckParams.class);
    List<String> ids = params.getIds() != null ? params.getIds() : 
                      (params.getId() != null ? List.of(params.getId()) : null);
    
    if (ids == null || ids.isEmpty()) {
        return FapiResponse.error(request.getId(), FapiCode.BAD_REQUEST, 
            "params.id or params.ids is required");
    }
    
    Map<String, Boolean> result = new HashMap<>();
    for (String did : ids) {
        result.put(did, diskManager.checkFileOfDisk(did));
    }
    
    return FapiResponse.success(request.getId(), result);
}
```

#### handlePut - 小文件上传

```java
/**
 * 处理小文件上传
 * 请求格式：{ "id": "req-123", "api": "disk.put", "params": { "content": "base64..." } }
 */
private FapiResponse handlePut(FapiRequest request, String peerId) {
    DiskPutParams params = parseParams(request.getParams(), DiskPutParams.class);
    
    if (params.getContent() == null) {
        return FapiResponse.error(request.getId(), FapiCode.BAD_REQUEST, 
            "params.content is required");
    }
    
    byte[] content = Base64.getDecoder().decode(params.getContent());
    if (content.length >= LARGE_FILE_THRESHOLD) {
        return FapiResponse.error(request.getId(), FapiCode.PAYLOAD_TOO_LARGE, 
            "File too large. Use disk.prepareUpload for files >= 1MB");
    }
    
    // 保存文件并获取did
    String did = diskManager.put(content);
    if (did == null) {
        return FapiResponse.error(request.getId(), FapiCode.INTERNAL_ERROR, 
            "Failed to save file");
    }
    
    // 保存元数据到ES
    DiskItem item = new DiskItem(did, System.currentTimeMillis(), 
        calculateExpireTime(), (long) content.length);
    saveToEs(item);
    
    return FapiResponse.success(request.getId(), Map.of("did", did, "size", content.length));
}
```

#### handleBatchPut - 批量上传

```java
/**
 * 批量上传小文件
 * 请求格式：{ "id": "req-123", "api": "disk.batchPut", "params": { 
 *   "files": [{ "content": "base64..." }, { "content": "base64..." }] 
 * }}
 */
private FapiResponse handleBatchPut(FapiRequest request, String peerId) {
    DiskBatchPutParams params = parseParams(request.getParams(), DiskBatchPutParams.class);
    
    if (params.getFiles() == null || params.getFiles().isEmpty()) {
        return FapiResponse.error(request.getId(), FapiCode.BAD_REQUEST, 
            "params.files is required");
    }
    
    List<Map<String, Object>> results = new ArrayList<>();
    for (DiskPutParams file : params.getFiles()) {
        try {
            byte[] content = Base64.getDecoder().decode(file.getContent());
            String did = diskManager.put(content);
            results.add(Map.of("did", did, "size", content.length, "success", true));
        } catch (Exception e) {
            results.add(Map.of("success", false, "error", e.getMessage()));
        }
    }
    
    return FapiResponse.success(request.getId(), results);
}
```

#### handlePrepareUpload - 准备大文件上传

```java
/**
 * 准备大文件上传（支持断点续传）
 * 请求格式：{ "id": "req-123", "api": "disk.prepareUpload", "params": { 
 *   "size": 104857600, "hash": "sha256:..." 
 * }}
 * 响应：{ "uploadToken": "xxx", "chunkSize": 32768, "totalChunks": 3200 }
 */
private FapiResponse handlePrepareUpload(FapiRequest request, String peerId) {
    DiskPrepareParams params = parseParams(request.getParams(), DiskPrepareParams.class);
    
    if (params.getSize() == null || params.getSize() <= 0) {
        return FapiResponse.error(request.getId(), FapiCode.BAD_REQUEST, 
            "params.size is required");
    }
    
    String uploadToken = generateUploadToken(peerId, params.getHash());
    int chunkSize = fileHandler.getChunkSize();
    long totalChunks = (params.getSize() + chunkSize - 1) / chunkSize;
    
    // 计算动态超时（无硬上限）
    long timeout = calculateDynamicTimeout(params.getSize());
    
    fileHandler.registerPendingUpload(uploadToken, storageDir, params.getHash(), timeout);
    
    return FapiResponse.success(request.getId(), Map.of(
        "uploadToken", uploadToken,
        "chunkSize", chunkSize,
        "totalChunks", totalChunks,
        "timeout", timeout
    ));
}
```

#### handleResumeUpload - 断点续传

```java
/**
 * 恢复断点续传
 * 请求格式：{ "id": "req-123", "api": "disk.resumeUpload", "params": { 
 *   "uploadToken": "xxx" 
 * }}
 * 响应：{ "uploadedChunks": [0,1,2,5,6], "nextChunk": 3 }
 */
private FapiResponse handleResumeUpload(FapiRequest request, String peerId) {
    DiskResumeParams params = parseParams(request.getParams(), DiskResumeParams.class);
    
    if (params.getUploadToken() == null) {
        return FapiResponse.error(request.getId(), FapiCode.BAD_REQUEST, 
            "params.uploadToken is required");
    }
    
    UploadState state = fileHandler.getUploadState(params.getUploadToken());
    if (state == null) {
        return FapiResponse.error(request.getId(), FapiCode.NOT_FOUND, 
            "Upload session not found or expired");
    }
    
    return FapiResponse.success(request.getId(), Map.of(
        "uploadedChunks", state.getUploadedChunks(),
        "nextChunk", state.getNextChunk(),
        "totalChunks", state.getTotalChunks()
    ));
}
```

### 4.3 辅助方法

```java
/**
 * 计算动态超时时间（无硬上限，根据文件大小动态计算）
 */
private long calculateDynamicTimeout(long fileSizeBytes) {
    long baseTimeout = 60_000;           // 基础超时60秒
    long perMbTimeout = 10_000;          // 每MB额外10秒
    long minTimeout = 60_000;            // 最小超时60秒
    
    long sizeMb = Math.max(1, fileSizeBytes / (1024 * 1024));
    long timeout = baseTimeout + sizeMb * perMbTimeout;
    return Math.max(minTimeout, timeout);
    // 不设置maxTimeout，支持任意大小文件
}

/**
 * 计算过期时间（默认30天）
 */
private long calculateExpireTime() {
    return System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000;
}

/**
 * 保存元数据到ES
 */
private void saveToEs(DiskItem item) {
    String index = Settings.addSidBriefToName(settings.getSid(), ES_INDEX_SUFFIX);
    esClient.index(index, item.getDid(), item);
}
```

## 5. 数据一致性

### 5.1 写操作一致性

采用"先写文件，后写元数据，失败回滚"策略：

```java
/**
 * 文件写入与元数据更新的一致性保证
 */
private FapiResponse handlePut(FapiRequest request, String peerId) {
    byte[] content = decodeContent(request);
    
    // 1. 先保存文件到磁盘
    String did = diskManager.put(content);
    if (did == null) {
        return FapiResponse.error(request.getId(), FapiCode.INTERNAL_ERROR, 
            "Failed to save file");
    }
    
    // 2. 保存元数据到ES
    try {
        DiskItem item = new DiskItem(did, System.currentTimeMillis(), 
            calculateExpireTime(), content.length);
        saveToEs(item);
    } catch (Exception e) {
        // 3. 元数据保存失败，回滚：删除已保存的文件
        diskManager.deleteFile(did);
        return FapiResponse.error(request.getId(), FapiCode.INTERNAL_ERROR, 
            "Failed to save metadata: " + e.getMessage());
    }
    
    return FapiResponse.success(request.getId(), Map.of("did", did));
}
```

**注意**：这是简化的单节点一致性，不涉及分布式事务。

## 6. 限流配置

```java
// 默认API限流配置
private static final Map<String, Integer> DEFAULT_API_LIMITS = Map.of(
    "disk.put", 10,           // 上传限制10 QPS
    "disk.batchPut", 5,       // 批量上传限制5 QPS
    "disk.get", 100,          // 下载限制100 QPS
    "disk.list", 50           // 列表查询限制50 QPS
);
```

## 7. 超时配置

| 场景 | 超时计算 | 示例 |
|-----|---------|------|
| 小文件上传（<1MB） | 固定30秒 | disk.put |
| 大文件上传 | 60s + 10s/MB | 1GB → 约3小时 |
| 文件下载 | 动态计算 | 与上传类似 |

## 8. 审计日志

需要审计的操作：
- `disk.put` - 文件上传
- `disk.carve` - 永久保存

注：DISK采用开放存储设计，无delete接口，过期数据自动清理。

## 9. 文件变更清单

**新增文件**：
```
FC-JDK/src/main/java/
├── fapi/components/
│   └── DiskComponent.java
├── data/diskData/
│   └── DiskItem.java
├── managers/
│   └── DiskManager.java
└── fapi/params/
    ├── DiskPutParams.java
    ├── DiskBatchPutParams.java
    ├── DiskCheckParams.java
    ├── DiskPrepareParams.java
    └── DiskResumeParams.java
```

## 10. 实施计划

| 任务 | 说明 | 预估时间 |
|-----|------|---------|
| DiskItem数据模型 | 元数据结构定义 | 0.5天 |
| DiskManager | 文件存储管理 | 1天 |
| DiskComponent | 组件实现 | 2天 |
| 大文件传输集成 | FileHandler集成 | 1.5天 |
| 断点续传 | UploadState管理 | 1天 |
| ES索引创建 | Mapping配置 | 0.5天 |
| 单元测试 | 覆盖主要场景 | 1天 |
| 集成测试 | 端到端测试 | 1天 |

**总计**：约8.5天

---

## 更新记录

| 版本 | 日期 | 变更内容 |
|-----|------|---------|
| 1.0 | 2024-12 | 从FAPI服务重构方案中提取DISK相关内容，形成独立设计文档 |

