# FAPI12V1_DISK

## Contents

- [Summary](#summary)
- [Abstract](#abstract)
- [1. Overview](#1-overview)
- [2. Concepts](#2-concepts)
  - [2.1. DID (Disk ID)](#21-did-disk-id)
  - [2.2. Data Life and Permanent Storage](#22-data-life-and-permanent-storage)
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

FAPI12V1 defines the DISK component specification for the FAPI (Freecash API) protocol series. DISK provides decentralized file storage with content addressing and configurable expiration. Files are identified by their DID (Disk ID), computed as the double SHA-256 hash (`SHA256x2`) of the file content. The component supports two storage modes: time-limited storage with configurable `dataLifeDays` via `disk.put`, and permanent storage via `disk.carve`. Retrieval, metadata inspection, and listing operations complete the API surface. DISK requires an Elasticsearch backend for metadata indexing and a file system backend for binary content. The component type ID is `DISK@No1_NrC7`.

## 1. Overview

The DISK component serves as the file storage layer of the FAPI ecosystem. It enables clients to store, retrieve, and manage binary files on decentralized FAPI servers. The design is built around the following principles:

- **Content addressing**: Every file is identified by its DID, which is the SHA256x2 hex digest of the file content. Two files with identical content share the same DID, enabling natural deduplication.
- **Configurable expiration**: Files stored via `disk.put` have a time-limited lifespan. Clients MAY specify a `dataLifeDays` parameter, or the server applies its configured default. Expiration is automatically extended when a file is accessed via `disk.get` or `disk.check`.
- **Permanent storage**: Files stored via `disk.carve` are retained indefinitely. Re-storing or checking an existing temporary file with `permanent: true` upgrades it to permanent storage.
- **Paid access**: DISK requests are charged by the FAPI server's economics model (FAPI4). This specification does not define an additional DISK-specific fee formula.

DISK requires the following backend infrastructure:

- **Elasticsearch**: Indexes file metadata (`id`, `size`, `since`, `expire`) for query and listing operations.
- **File storage backend**: Stores binary file content under a content-addressed directory tree.

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this document are to be interpreted as described in RFC 2119.

## 2. Concepts

### 2.1. DID (Disk ID)

A DID (Disk ID) is a content-addressed file identifier computed as the double SHA-256 hash (`SHA256x2`) of the file content. The DID is a 64-character hexadecimal string and is stored in normalized lowercase form by the storage backend.

Properties:

- Two files with identical binary content produce the same DID.
- A single-byte difference in content produces an entirely different DID.
- The DID serves as both the unique identifier and the integrity verification mechanism for stored files.

Servers MUST compute the DID from the received binary data and use it as the canonical file identifier. If the same DID already exists, the server MUST NOT rewrite the file content and MUST update the existing metadata according to the expiration rules in Section 2.2. The `dataHash` header field, when present in the FAPI binary envelope, SHOULD contain the same SHA256x2 digest; DISK semantics are defined by the server-computed DID.

### 2.2. Data Life and Permanent Storage

DISK supports two storage modes:

- **Time-limited storage** (`disk.put`): The file is stored for a specified number of days (`dataLifeDays`). If `dataLifeDays` is absent, the server applies its configured default `dataLifeDays` value, which defaults to 30 days. After the expiration time, the file becomes eligible for garbage collection.
- **Permanent storage** (`disk.carve`): The file is stored indefinitely. Its metadata has no `expire` value. The server operator MAY remove permanently stored files only through manual administrative action outside the FAPI protocol.
- **Expiration extension**: When an existing temporary file is accessed or checked, the server extends `expire` to `max(currentExpire, now + dataLifeDays)`. For `disk.get`, the server uses its default `dataLifeDays`. For `disk.check`, the client MAY provide `dataLifeDays`; otherwise the server default is used. If `permanent: true` is supplied to `disk.check`, or if the existing DID is stored again with `disk.carve`, the file is upgraded to permanent storage.

### 2.3. File Size Limit

The maximum file size per upload is controlled by the server's `maxDataSize` setting. The default limit is 100 MB (104,857,600 bytes). Servers MAY publish and enforce a different limit through service parameters or local settings. Requests whose binary data exceeds `maxDataSize` MUST be rejected with `BAD_REQUEST`.

### 2.4. File Metadata Object

File metadata returned by DISK methods uses the following structure:

| Field | Type | Description |
|---|---|---|
| `id` | string | The content-addressed file identifier (SHA256x2 hex digest). |
| `since` | integer | Unix epoch timestamp (milliseconds) when the metadata record was created. |
| `expire` | integer | Unix epoch timestamp (milliseconds) when the file expires. This field is absent or null for permanently stored files. |
| `size` | integer | File size in bytes. |

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
- **Request**: `params` with optional `dataLifeDays` (integer, number of days until expiration; absent uses the server's configured default). Binary data follows the JSON header (file content). The `dataSize` and `dataHash` fields SHOULD be set in the request.
- **Response**: `data` contains the stored file metadata including `id`, `since`, `expire`, and `size`.
- **Charging**: Charged according to the FAPI server's pricing model. The `charged` field in the response reflects the actual amount deducted.

**Request example:**

```json
{
  "id": "req-1711612800000-a1b2c3d4",
  "api": "disk.put",
  "params": {
    "dataLifeDays": 30
  },
  "dataSize": 524288,
  "dataHash": "5df6e0e2761358c581d049d9256d89db6f11d54f94ee7196f28cf05b38783f34"
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
    "id": "5df6e0e2761358c581d049d9256d89db6f11d54f94ee7196f28cf05b38783f34",
    "since": 1711612800000,
    "expire": 1714204800000,
    "size": 524288
  },
  "balance": 9500000,
  "charged": 500000
}
```

### 4.2. disk.carve

Store a file permanently with no expiration.

- **Category**: Binary operation
- **Request**: Same wire format as `disk.put`. `dataLifeDays` is ignored for permanent storage. Binary data follows the JSON header (file content). The `dataSize` and `dataHash` fields SHOULD be set in the request.
- **Response**: `data` contains the stored file metadata including `id`, `since`, and `size`. No `expire` field is returned for permanent storage.
- **Charging**: Charged according to the FAPI server's pricing model. The `charged` field in the response reflects the actual amount deducted.

**Request example:**

```json
{
  "id": "req-1711612800000-b2c3d4e5",
  "api": "disk.carve",
  "dataSize": 102400,
  "dataHash": "954d5a49fd70d9b8bcdb35d25226736c86fcb1569600ea3b1769f50faebd6bb7"
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
    "id": "954d5a49fd70d9b8bcdb35d25226736c86fcb1569600ea3b1769f50faebd6bb7",
    "since": 1711612800000,
    "size": 102400
  },
  "balance": 8500000,
  "charged": 1000000
}
```

### 4.3. disk.get

Retrieve a file by its DID.

- **Category**: Binary operation
- **Request**: `params` with `id` (string, the file's Disk ID).
- **Response**: The JSON header contains file metadata. Binary data follows the JSON header (file content). The `dataSize` field in the response indicates the file size.
- **Charging**: Charged according to the FAPI server's pricing model. The `charged` field in the response reflects the actual amount deducted.
- **Side effect**: For time-limited files, the expiration timer is automatically extended upon successful retrieval to `max(currentExpire, now + defaultDataLifeDays * 86400000)`.

**Request example:**

```json
{
  "id": "req-1711612800000-c3d4e5f6",
  "api": "disk.get",
  "params": {
    "id": "5df6e0e2761358c581d049d9256d89db6f11d54f94ee7196f28cf05b38783f34"
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
    "id": "5df6e0e2761358c581d049d9256d89db6f11d54f94ee7196f28cf05b38783f34",
    "since": 1711612800000,
    "expire": 1716796800000,
    "size": 524288
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
- **Request**: `fcdsl` with `ids` containing a list of DIDs. For compatibility, clients MAY instead send `params.dids` as a list or `params.id` as a single DID. Maximum 200 DIDs per request.
- **Response**: `data` contains an object keyed by requested DID. Each value is the file metadata object if the file exists, or `null` if the file does not exist.
- **Side effect**: For existing temporary files, `disk.check` extends expiration. `params.dataLifeDays` MAY override the server default extension period. `params.permanent: true` upgrades existing temporary files to permanent storage.
- **Charging**: Standard query fee as defined by the service's pricing model.

**Request example:**

```json
{
  "id": "req-1711612800000-d4e5f6g7",
  "api": "disk.check",
  "params": {
    "dataLifeDays": 30
  },
  "fcdsl": {
    "ids": [
      "5df6e0e2761358c581d049d9256d89db6f11d54f94ee7196f28cf05b38783f34",
      "954d5a49fd70d9b8bcdb35d25226736c86fcb1569600ea3b1769f50faebd6bb7",
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
  "data": {
    "5df6e0e2761358c581d049d9256d89db6f11d54f94ee7196f28cf05b38783f34": {
      "id": "5df6e0e2761358c581d049d9256d89db6f11d54f94ee7196f28cf05b38783f34",
      "since": 1711612800000,
      "expire": 1716796800000,
      "size": 524288
    },
    "954d5a49fd70d9b8bcdb35d25226736c86fcb1569600ea3b1769f50faebd6bb7": {
      "id": "954d5a49fd70d9b8bcdb35d25226736c86fcb1569600ea3b1769f50faebd6bb7",
      "since": 1711612800000,
      "size": 102400
    },
    "0000000000000000000000000000000000000000000000000000000000000000": null
  },
  "balance": 9390000,
  "charged": 10000
}
```

Note: The third DID in the request (`0000...`) does not exist on the server and is returned with a `null` value.

### 4.5. disk.list

Query stored files using FCDSL filter and pagination syntax.

- **Category**: Query
- **Request**: `fcdsl` with `filter`, `sort`, `size`, and optionally `after`/`last` for cursor-based pagination. Filterable and sortable metadata fields are `id`, `size`, `since`, and `expire`. If no sort is provided, the server applies a default sort on `since`.
- **Response**: `data` contains an array of file metadata objects. `got`, `total`, and `last` provide pagination metadata.
- **Charging**: Standard query fee as defined by the service's pricing model.

**Request example:**

```json
{
  "id": "req-1711612800000-e5f6g7h8",
  "api": "disk.list",
  "fcdsl": {
    "filter": {
      "range": { "size": { "gte": 1024 } }
    },
    "sort": [{ "since": "desc" }],
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
      "id": "5df6e0e2761358c581d049d9256d89db6f11d54f94ee7196f28cf05b38783f34",
      "since": 1711612800000,
      "expire": 1716796800000,
      "size": 524288
    },
    {
      "id": "954d5a49fd70d9b8bcdb35d25226736c86fcb1569600ea3b1769f50faebd6bb7",
      "since": 1711612780000,
      "size": 102400
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

The DID mechanism provides built-in content integrity verification. Because the DID is the SHA256x2 hash of the file content, clients can independently verify that retrieved data matches the expected DID by computing the double SHA-256 hash of the received binary data and comparing it to the DID.

Servers MUST compute the DID from the actual binary data received during `disk.put` and `disk.carve` operations. Servers MUST verify file content against the requested DID during retrieval before returning the binary content. Invalid DID formats are treated as missing files.

Clients SHOULD always verify the integrity of data retrieved via `disk.get` by computing the SHA256x2 hash of the received binary content and comparing it to the requested DID.

### 5.2. Size Limits

The configurable `maxDataSize` limit serves as a defense against oversized uploads that could degrade server performance or exhaust memory. Servers MUST reject oversized payloads with `BAD_REQUEST`. The FAPI binary envelope also validates that the declared `dataSize` matches the actual binary payload length.

### 5.3. Storage Exhaustion Prevention

Servers MUST implement safeguards against storage exhaustion:

- **Quota enforcement**: Servers SHOULD enforce per-peer storage quotas to prevent a single client from consuming disproportionate storage resources. Quota limits are implementation-defined and MAY be published in the service declaration.
- **Configurable storage limits**: Servers SHOULD enforce `maxTotalDiskUsage` for local storage and SHOULD reject or stop synchronization when the configured storage ceiling would be exceeded.
- **Economic deterrence**: The FAPI charging model ensures that storing and retrieving large volumes of data incurs proportional cost. This economic pressure discourages frivolous or abusive storage consumption.
- **Garbage collection**: Servers MUST implement a garbage collection process that removes expired files (those past their `expire` timestamp). The frequency and timing of garbage collection runs are implementation-defined, but expired files SHOULD be removed within 24 hours of their expiration time.
- **Rate limiting**: Servers SHOULD enforce rate limits on upload operations (`disk.put`, `disk.carve`) to prevent burst abuse. Rate-limited requests MUST be rejected with code 429 (TOO_MANY_REQUESTS).

## 6. Versioning

This is version 1 of the DISK component specification. Future versions MAY introduce:

- Additional metadata fields in file metadata objects.
- New methods for batch operations or administrative functions.
- Extended query capabilities for metadata search.
- Configurable deduplication policies.
- Additional synchronization controls for mirroring DISK data between FAPI servers.

Version increments follow the FAPI versioning rules. Non-breaking additions (new optional fields, new methods) MAY be introduced within the same version. Breaking changes to existing method signatures or semantics REQUIRE a new version number.

## 7. References

- **FAPI1** (Core Protocol): Wire format (UnifiedCodec), request/response structures, status codes, binary data handling, and request validation rules referenced throughout this specification.
- **FAPI2** (FCDSL): Freeverse Common Data Service Language syntax used by `disk.check` and `disk.list` for IDs, query expressions, filtering, sorting, and pagination.
- **FAPI3** (Components): Component model, lifecycle states, component type IDs, and the interface contract that the DISK component implements.
- **FAPI4** (Economics): Pricing model, charging rules, balance management, and the economic framework used by DISK requests.
- **RFC 2119**: Key words for use in RFCs to indicate requirement levels.
