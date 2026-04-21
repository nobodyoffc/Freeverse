# FAPI12V1_DISK

## Contents

- [Summary](#summary)
- [Abstract](#abstract)
- [1. Overview](#1-overview)
- [2. Concepts](#2-concepts)
  - [2.1. DID (Disk ID)](#21-did-disk-id)
  - [2.2. Expiration and Permanent Storage](#22-expiration-and-permanent-storage)
  - [2.3. File Size Limit](#23-file-size-limit)
  - [2.4. File Metadata Object](#24-file-metadata-object)
- [3. API List](#3-api-list)
- [4. Method Definitions](#4-method-definitions)
  - [4.1. disk.put](#41-diskput)
  - [4.2. disk.carve](#42-diskcarve)
  - [4.3. disk.get](#43-diskget)
  - [4.4. disk.check](#44-diskcheck)
  - [4.5. disk.list](#45-disklist)
- [5. Security Considerations](#5-security-considerations)
  - [5.1. Content Integrity](#51-content-integrity)
  - [5.2. Size Limits](#52-size-limits)
  - [5.3. Storage Exhaustion Prevention](#53-storage-exhaustion-prevention)
- [6. Versioning](#6-versioning)
- [7. References](#7-references)

---

## Summary

|Field|Content|
|---|---|
|Title|DISK|
|Type|FAPI|
|SN|12|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-03-28|
|PID||

## Abstract

FAPI12V1 defines the DISK component specification for the FAPI (Freecash API) protocol series. DISK provides decentralized file storage with content addressing and configurable expiration. Files are identified by their DID (Disk ID), computed as the SHA-256 hash of the file content. The component supports two storage modes: time-limited storage with configurable expiration via `disk.put`, and permanent storage via `disk.carve`. Retrieval, metadata inspection, and listing operations complete the API surface. DISK requires an Elasticsearch backend for metadata indexing and a file system or object storage backend for binary content. The component type ID is `DISK@No1_NrC7`.

## 1. Overview

The DISK component serves as the file storage layer of the FAPI ecosystem. It enables clients to store, retrieve, and manage binary files on decentralized FAPI servers. The design is built around the following principles:

- **Content addressing**: Every file is identified by its DID, which is the SHA-256 hex digest of the file content. Two files with identical content share the same DID, enabling natural deduplication.
- **Configurable expiration**: Files stored via `disk.put` have a time-limited lifespan. Clients specify an `expireDays` parameter, or the server applies a default TTL. Expiration is automatically extended when a file is accessed via `disk.get`.
- **Permanent storage**: Files stored via `disk.carve` are retained indefinitely. They are never subject to automatic garbage collection.
- **Paid storage**: All operations are charged via the FAPI economics model (FAPI4). Storage fees are proportional to file size and duration. Retrieval fees are proportional to file size.

DISK requires the following backend infrastructure:

- **Elasticsearch**: Indexes file metadata (DID, size, owner, expiration time, upload time) for query and listing operations.
- **File storage backend**: Stores binary file content. Implementations MAY use a local file system, distributed file system, or object storage service.

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this document are to be interpreted as described in RFC 2119.

## 2. Concepts

### 2.1. DID (Disk ID)

A DID (Disk ID) is a content-addressed file identifier computed as the SHA-256 hex digest of the file content. The DID is a 64-character lowercase hexadecimal string.

Properties:

- Two files with identical binary content produce the same DID.
- A single-byte difference in content produces an entirely different DID.
- The DID serves as both the unique identifier and the integrity verification mechanism for stored files.

Servers MUST compute the DID from the received binary data and use it as the canonical file identifier. If the client provides a `dataHash` in the request, the server MUST verify that the computed DID matches the provided hash and reject the request with code 400 if they differ.

### 2.2. Expiration and Permanent Storage

DISK supports two storage modes:

- **Time-limited storage** (`disk.put`): The file is stored for a specified number of days (`expireDays`). After the expiration time, the file becomes eligible for garbage collection. If `expireDays` is 0 or null, the server applies its configured default TTL. The expiration timer is reset each time the file is accessed via `disk.get`, extending the file's lifespan by the original duration.
- **Permanent storage** (`disk.carve`): The file is stored indefinitely. It is never automatically deleted. The server operator MAY remove permanently stored files only through manual administrative action outside the FAPI protocol.

### 2.3. File Size Limit

The maximum file size per upload is 10 MB (10,485,760 bytes), as enforced by FAPI1 Section 6 (binary data size validation). Servers MAY configure a lower limit but MUST NOT accept files exceeding 10 MB. Requests with binary data exceeding the limit MUST be rejected with code 413 (PAYLOAD_TOO_LARGE).

### 2.4. File Metadata Object

File metadata returned by DISK methods uses the following structure:

| Field | Type | Description |
|---|---|---|
| `did` | string | The content-addressed file identifier (SHA-256 hex digest). |
| `size` | integer | File size in bytes. |
| `owner` | string | FID of the peer that originally stored the file. |
| `uploadTime` | integer | Unix epoch timestamp (milliseconds) when the file was stored. |
| `expireTime` | integer | Unix epoch timestamp (milliseconds) when the file expires. Null for permanently stored files. |
| `permanent` | boolean | `true` if the file was stored via `disk.carve`; `false` otherwise. |

## 3. API List

The DISK component exposes 5 methods:

| # | API | Category | Binary | Description |
|---|---|---|---|---|
| 1 | `disk.put` | Operation | Request: file data | Store a file with configurable expiration |
| 2 | `disk.carve` | Operation | Request: file data | Store a file permanently |
| 3 | `disk.get` | Operation | Response: file data | Retrieve a file by DID |
| 4 | `disk.check` | Query | None | Check file existence and metadata |
| 5 | `disk.list` | Query | None | Query stored files with FCDSL |

## 4. Method Definitions

### 4.1. disk.put

Store a file with an optional expiration period.

- **Category**: Binary operation
- **Request**: `params` with optional `expireDays` (integer, number of days until expiration; 0 or null uses the server's configured default). Binary data follows the JSON header (file content). The `dataSize` and `dataHash` fields SHOULD be set in the request.
- **Response**: `data` contains the stored file metadata including `did`, `size`, and `expireTime`.
- **Charging**: `pricePerKB * ceil(size / 1024) * days` in satoshi. The `charged` field in the response reflects the actual amount deducted.

**Request example:**

```json
{
  "id": "req-1711612800000-a1b2c3d4",
  "api": "disk.put",
  "params": {
    "expireDays": 30
  },
  "dataSize": 524288,
  "dataHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
}
```

Binary data (524,288 bytes of file content) follows the JSON header in the wire format.

**Response example:**

```json
{
  "id": "resp-1711612800123-x9y8z7",
  "requestId": "req-1711612800000-a1b2c3d4",
  "code": 0,
  "message": "Success",
  "data": {
    "did": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "size": 524288,
    "expireTime": 1714204800000
  },
  "balance": 9500000,
  "charged": 500000
}
```

### 4.2. disk.carve

Store a file permanently with no expiration.

- **Category**: Binary operation
- **Request**: Same wire format as `disk.put` but no `expireDays` parameter. Binary data follows the JSON header (file content). The `dataSize` and `dataHash` fields SHOULD be set in the request.
- **Response**: `data` contains the stored file metadata including `did` and `size`. No `expireTime` is returned.
- **Charging**: A one-time permanent storage fee. The fee model is implementation-defined but MUST be proportional to file size. The `charged` field in the response reflects the actual amount deducted.

**Request example:**

```json
{
  "id": "req-1711612800000-b2c3d4e5",
  "api": "disk.carve",
  "dataSize": 102400,
  "dataHash": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
}
```

Binary data (102,400 bytes of file content) follows the JSON header in the wire format.

**Response example:**

```json
{
  "id": "resp-1711612800456-w8v7u6",
  "requestId": "req-1711612800000-b2c3d4e5",
  "code": 0,
  "message": "Success",
  "data": {
    "did": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
    "size": 102400
  },
  "balance": 8500000,
  "charged": 1000000
}
```

### 4.3. disk.get

Retrieve a file by its DID.

- **Category**: Binary operation
- **Request**: `params` with `did` (string, the file's Disk ID).
- **Response**: The JSON header contains file metadata. Binary data follows the JSON header (file content). The `dataSize` field in the response indicates the file size.
- **Charging**: `pricePerKBOut * ceil(size / 1024)` in satoshi. The `charged` field in the response reflects the actual amount deducted.
- **Side effect**: For time-limited files, the expiration timer is automatically extended upon successful retrieval. The new expiration time is computed as `now + originalExpireDays * 86400000` milliseconds.

**Request example:**

```json
{
  "id": "req-1711612800000-c3d4e5f6",
  "api": "disk.get",
  "params": {
    "did": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
  }
}
```

**Response example:**

```json
{
  "id": "resp-1711612800789-t5s4r3",
  "requestId": "req-1711612800000-c3d4e5f6",
  "code": 0,
  "message": "Success",
  "data": {
    "did": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "size": 524288,
    "owner": "FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV",
    "uploadTime": 1711612800000,
    "expireTime": 1716796800000,
    "permanent": false
  },
  "dataSize": 524288,
  "balance": 9400000,
  "charged": 100000
}
```

Binary data (524,288 bytes of file content) follows the JSON header in the response wire format.

### 4.4. disk.check

Check whether files exist and retrieve their metadata without downloading content.

- **Category**: Query
- **Request**: `fcdsl` with `ids` containing a list of DIDs. Maximum 200 DIDs per request.
- **Response**: `data` contains an array of file metadata objects. Files that do not exist on the server are silently omitted from the response. The `got` field indicates the number of metadata objects returned.
- **Charging**: Standard query fee as defined by the service's pricing model.

**Request example:**

```json
{
  "id": "req-1711612800000-d4e5f6g7",
  "api": "disk.check",
  "fcdsl": {
    "ids": [
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
      "0000000000000000000000000000000000000000000000000000000000000000"
    ]
  }
}
```

**Response example:**

```json
{
  "id": "resp-1711612801234-q2p1o0",
  "requestId": "req-1711612800000-d4e5f6g7",
  "code": 0,
  "message": "Success",
  "data": [
    {
      "did": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      "size": 524288,
      "owner": "FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV",
      "uploadTime": 1711612800000,
      "expireTime": 1716796800000,
      "permanent": false
    },
    {
      "did": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
      "size": 102400,
      "owner": "FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV",
      "uploadTime": 1711612800000,
      "expireTime": null,
      "permanent": true
    }
  ],
  "got": 2,
  "balance": 9390000,
  "charged": 10000
}
```

Note: The third DID in the request (`0000...`) does not exist on the server and is omitted from the response.

### 4.5. disk.list

Query stored files using FCDSL filter and pagination syntax.

- **Category**: Query
- **Request**: `fcdsl` with `filter`, `sort`, `size`, and optionally `last` for cursor-based pagination. Filterable fields include `owner`, `size`, `uploadTime`, `expireTime`, and `permanent`. Sortable fields include `size`, `uploadTime`, and `expireTime`.
- **Response**: `data` contains an array of file metadata objects. `got`, `total`, and `last` provide pagination metadata.
- **Charging**: Standard query fee as defined by the service's pricing model.

**Request example:**

```json
{
  "id": "req-1711612800000-e5f6g7h8",
  "api": "disk.list",
  "fcdsl": {
    "filter": {
      "term": { "owner": "FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV" }
    },
    "sort": [{ "uploadTime": "desc" }],
    "size": 20
  }
}
```

**Response example:**

```json
{
  "id": "resp-1711612801567-n9m8l7",
  "requestId": "req-1711612800000-e5f6g7h8",
  "code": 0,
  "message": "Success",
  "data": [
    {
      "did": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      "size": 524288,
      "owner": "FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV",
      "uploadTime": 1711612800000,
      "expireTime": 1716796800000,
      "permanent": false
    },
    {
      "did": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
      "size": 102400,
      "owner": "FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV",
      "uploadTime": 1711612780000,
      "expireTime": null,
      "permanent": true
    }
  ],
  "got": 2,
  "total": 2,
  "balance": 9380000,
  "charged": 10000
}
```

## 5. Security Considerations

### 5.1. Content Integrity

The DID mechanism provides built-in content integrity verification. Because the DID is the SHA-256 hash of the file content, clients can independently verify that retrieved data matches the expected DID by computing the hash of the received binary data and comparing it to the DID.

Servers MUST compute the DID from the actual binary data received during `disk.put` and `disk.carve` operations. If the client provides a `dataHash` field in the request, the server MUST verify that the computed hash matches the provided value. A mismatch MUST result in rejection with code 400 (BAD_REQUEST).

Clients SHOULD always verify the integrity of data retrieved via `disk.get` by computing the SHA-256 hash of the received binary content and comparing it to the requested DID.

### 5.2. Size Limits

The 10 MB file size limit (enforced at the FAPI1 wire format level) serves as a defense against oversized uploads that could degrade server performance or exhaust memory. Servers MAY impose a lower limit via local configuration. Servers MUST reject oversized payloads with code 413 (PAYLOAD_TOO_LARGE) before reading the full binary data if the `dataSize` header indicates a size exceeding the limit.

### 5.3. Storage Exhaustion Prevention

Servers MUST implement safeguards against storage exhaustion:

- **Quota enforcement**: Servers SHOULD enforce per-peer storage quotas to prevent a single client from consuming disproportionate storage resources. Quota limits are implementation-defined and MAY be published in the service declaration.
- **Economic deterrence**: The per-KB, per-day charging model ensures that storing large volumes of data for extended periods incurs proportional cost. This economic pressure discourages frivolous or abusive storage consumption.
- **Garbage collection**: Servers MUST implement a garbage collection process that removes expired files (those past their `expireTime`). The frequency and timing of garbage collection runs are implementation-defined, but expired files SHOULD be removed within 24 hours of their expiration time.
- **Rate limiting**: Servers SHOULD enforce rate limits on upload operations (`disk.put`, `disk.carve`) to prevent burst abuse. Rate-limited requests MUST be rejected with code 429 (TOO_MANY_REQUESTS).

## 6. Versioning

This is version 1 of the DISK component specification. Future versions MAY introduce:

- Additional metadata fields in file metadata objects.
- New methods for batch operations or administrative functions.
- Extended query capabilities for metadata search.
- Configurable deduplication policies.

Version increments follow the FAPI versioning rules. Non-breaking additions (new optional fields, new methods) MAY be introduced within the same version. Breaking changes to existing method signatures or semantics REQUIRE a new version number.

## 7. References

- **FAPI1** (Core Protocol): Wire format (UnifiedCodec), request/response structures, status codes, binary data handling, and request validation rules referenced throughout this specification.
- **FAPI2** (Query Language): FCDSL syntax used by `disk.check` and `disk.list` for query expressions, filtering, sorting, and pagination.
- **FAPI3** (Components): Component model, lifecycle states, component type IDs, and the interface contract that the DISK component implements.
- **FAPI4** (Economics): Pricing model, charging rules, balance management, and the economic framework governing `pricePerKB`, `pricePerKBOut`, and permanent storage fees.
- **RFC 2119**: Key words for use in RFCs to indicate requirement levels.
