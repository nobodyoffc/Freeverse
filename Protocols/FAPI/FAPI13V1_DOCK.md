# FAPI13V1_DOCK

## Contents

- [Summary](#summary)
- [Abstract](#abstract)
- [1. Overview](#1-overview)
- [2. Concepts](#2-concepts)
  - [2.1. Recipients](#21-recipients)
  - [2.2. Time-to-Live (TTL)](#22-time-to-live-ttl)
  - [2.3. Size Limit](#23-size-limit)
  - [2.4. Charging Model](#24-charging-model)
- [3. API List](#3-api-list)
- [4. Method Definitions](#4-method-definitions)
  - [4.1. dock.put](#41-dockput)
  - [4.2. dock.get](#42-dockget)
  - [4.3. dock.fetch](#43-dockfetch)
  - [4.4. dock.list](#44-docklist)
  - [4.5. dock.check](#45-dockcheck)
  - [4.6. dock.delete](#46-dockdelete)
  - [4.7. dock.extend](#47-dockextend)
- [5. Security Considerations](#5-security-considerations)
  - [5.1. Storage Abuse Prevention](#51-storage-abuse-prevention)
  - [5.2. Sender-Only Deletion](#52-sender-only-deletion)
  - [5.3. TTL Limits](#53-ttl-limits)
- [6. Versioning](#6-versioning)
- [7. References](#7-references)

---

## Summary

|Field|Content|
|---|---|
|Title|DOCK|
|Type|FAPI|
|SN|13|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-03-28|
|PID||

## Abstract

FAPI13V1 defines the DOCK component of the FAPI (Freeverse API Protocol) series. DOCK provides a store-and-forward messaging service in which senders deposit data addressed to one or more recipients, and recipients retrieve the data at a later time. The component is designed for small payloads up to 64 KB with time-limited storage. For larger data, clients SHOULD use the DISK component and send only the DID (Data ID) via DOCK. This specification defines the complete API surface of the DOCK component (7 methods), the charging model, recipient addressing, TTL semantics, and security considerations. The component type ID is `DOCK@No1_NrC7`.

## 1. Overview

The DOCK component serves as a message drop-off and pickup facility within the FAPI ecosystem. Senders deposit data for one or more recipients by calling `dock.put`. Recipients retrieve their messages at a later time using `dock.get`, `dock.fetch`, or `dock.list`. Messages are stored on the server for a configurable duration (TTL) and are automatically garbage-collected after expiration.

DOCK is intended for small, transient payloads -- notifications, control messages, encrypted keys, metadata references, and similar lightweight data. The maximum payload size is 64 KB. For larger data transfers, clients SHOULD upload the data to the DISK component and send the resulting DID as the DOCK message payload, allowing the recipient to retrieve the full data from DISK.

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this document are to be interpreted as described in RFC 2119.

## 2. Concepts

### 2.1. Recipients

A message MAY be addressed to one or more recipients. Each recipient is identified by one of the following:

- **FID** -- A Freecash Identity, addressing a single individual.
- **Team ID** -- Addresses all members of the specified team.
- **Group ID** -- Addresses all members of the specified group.
- **Room ID** -- Addresses all participants of the specified room.

When a message is addressed to multiple recipients, the server stores a single copy of the data and maintains a delivery record for each recipient. Each recipient independently retrieves and manages their own copy of the message metadata.

### 2.2. Time-to-Live (TTL)

The TTL defines the number of days a message is stored on the server before automatic deletion.

- **Default**: 7 days.
- **Maximum**: 365 days.
- **Minimum**: 1 day.

After TTL expiration, the message becomes eligible for garbage collection. Servers MAY retain expired messages for a brief grace period but MUST NOT guarantee their availability beyond the TTL.

The sender specifies the TTL at deposit time via the `ttl` parameter in `dock.put`. The TTL MAY be extended after deposit using `dock.extend`.

### 2.3. Size Limit

The maximum payload size for a single DOCK message is 64 KB (65,536 bytes), measured after Base64 decoding.

Servers MUST reject `dock.put` requests where the decoded payload exceeds this limit with error code 413 (PAYLOAD_TOO_LARGE).

For payloads exceeding 64 KB, clients SHOULD:

1. Upload the data to the DISK component via `disk.put`.
2. Obtain the DID (Data ID) from the DISK response.
3. Send the DID as the DOCK message payload via `dock.put`.
4. The recipient retrieves the DID from DOCK and fetches the full data from DISK.

### 2.4. Charging Model

DOCK charges are computed based on storage duration, data size, and transfer volume:

- **Storage cost**: `pricePerKBDay * ceil(size / 1024) * days`
  - `pricePerKBDay` is the server's configured rate per kilobyte per day.
  - `size` is the raw payload size in bytes (after Base64 decoding).
  - `days` is the requested TTL in days.
- **Ingress fee**: Charged to the sender on `dock.put`, based on the ingress pricing (`pricePerKBIn`) and the size of the uploaded data.
- **Egress fee**: Charged to the recipient on `dock.get`, based on the egress pricing (`pricePerKBOut`) and the size of the downloaded data.

The total cost to the sender at deposit time is: storage cost + ingress fee. The recipient pays only the egress fee upon retrieval. Query operations (`dock.fetch`, `dock.list`) are charged standard query fees as defined in FAPI4 (Economics).

## 3. API List

The DOCK component exposes 7 methods:

| # | API | Category | Description |
|---|---|---|---|
| 1 | `dock.put` | Operation | Store data for one or more recipients |
| 2 | `dock.get` | Binary operation | Retrieve data by message ID (binary response) |
| 3 | `dock.fetch` | Query | List items with inline Base64 data for requesting recipient |
| 4 | `dock.list` | Query | List item metadata for requesting recipient |
| 5 | `dock.check` | Operation | Check item status without downloading |
| 6 | `dock.delete` | Operation | Sender removes an item (partial refund) |
| 7 | `dock.extend` | Operation | Extend an item's TTL (sender pays) |

## 4. Method Definitions

### 4.1. dock.put

Store data for one or more recipients. The sender pays storage fees and ingress fees at the time of deposit.

- **Category**: Operation
- **Request**: `params` with:
  - `recipients` (array of strings, REQUIRED): Recipient identifiers. Each entry MAY be an FID, team ID, group ID, or room ID.
  - `ttl` (integer, OPTIONAL): Time-to-live in days. Default: 7. Minimum: 1. Maximum: 365.
  - `data` (string, REQUIRED): Base64-encoded payload data. Decoded size MUST NOT exceed 64 KB.
- **Response**: `data` contains the message ID (`mid`) and storage metadata.
- **Charging**: The sender pays `pricePerKBDay * ceil(size / 1024) * days` for storage, plus ingress fees based on `pricePerKBIn`.
- **Errors**:
  - 413 (PAYLOAD_TOO_LARGE): Decoded payload exceeds 64 KB.
  - 400 (BAD_REQUEST): Missing required fields or invalid TTL value.
  - 402 (INSUFFICIENT_CREDIT): Sender's credit balance is insufficient.

**Request example**:

```json
{
  "api": "dock.put",
  "params": {
    "recipients": ["FEk41Kqjar45fLDriztUDTUkdki7mmcjWK"],
    "ttl": 14,
    "data": "SGVsbG8sIHRoaXMgaXMgYSB0ZXN0IG1lc3NhZ2Uu"
  }
}
```

**Response example**:

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "mid": "d8f3a2b1c4e5f6a7b8c9d0e1f2a3b4c5",
    "sender": "F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW",
    "recipients": ["FEk41Kqjar45fLDriztUDTUkdki7mmcjWK"],
    "size": 26,
    "ttl": 14,
    "createdAt": 1743120000000,
    "expiresAt": 1744329600000
  }
}
```

### 4.2. dock.get

Retrieve message data as binary by message ID. Only a designated recipient MAY retrieve the message.

- **Category**: Binary operation
- **Request**: `params` with:
  - `mid` (string, REQUIRED): The message ID returned by `dock.put`.
- **Response**: The JSON header contains message metadata (sender, size, timestamps). The binary body contains the raw message content.
- **Charging**: The recipient pays egress fees based on `pricePerKBOut` and the message size.
- **Errors**:
  - 404 (NOT_FOUND): Message ID does not exist or has expired.
  - 403 (FORBIDDEN): Requester is not a designated recipient.

**Request example**:

```json
{
  "api": "dock.get",
  "params": {
    "mid": "d8f3a2b1c4e5f6a7b8c9d0e1f2a3b4c5"
  }
}
```

**Response header example**:

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "mid": "d8f3a2b1c4e5f6a7b8c9d0e1f2a3b4c5",
    "sender": "F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW",
    "size": 26,
    "createdAt": 1743120000000,
    "expiresAt": 1744329600000
  }
}
```

The binary response body immediately follows the JSON header and contains the raw decoded message content.

### 4.3. dock.fetch

List items addressed to the requesting peer, with message data included inline as Base64. This method combines the functionality of `dock.list` and `dock.get` into a single call, suitable for small messages.

- **Category**: Query
- **Request**: `fcdsl` with optional filter, sort, size, and pagination parameters as defined in FAPI2.
- **Response**: `data` contains an array of message objects, each including a `data` field with the Base64-encoded content. Standard pagination fields (`got`, `total`, `last`) are included.
- **Charging**: Standard query fees plus egress fees for all returned message content.

**Request example**:

```json
{
  "api": "dock.fetch",
  "fcdsl": {
    "filter": {
      "range": {
        "createdAt": { "gte": 1743120000000 }
      }
    },
    "sort": [{ "createdAt": "desc" }],
    "size": 10
  }
}
```

**Response example**:

```json
{
  "code": 0,
  "message": "OK",
  "got": 2,
  "total": 2,
  "data": [
    {
      "mid": "d8f3a2b1c4e5f6a7b8c9d0e1f2a3b4c5",
      "sender": "F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW",
      "size": 26,
      "ttl": 14,
      "createdAt": 1743120000000,
      "expiresAt": 1744329600000,
      "data": "SGVsbG8sIHRoaXMgaXMgYSB0ZXN0IG1lc3NhZ2Uu"
    },
    {
      "mid": "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6",
      "sender": "FHnRyV8PHKFQ1hQRNRMmJk6LkRbSx1FKBJ",
      "size": 42,
      "ttl": 7,
      "createdAt": 1743033600000,
      "expiresAt": 1743638400000,
      "data": "VGhpcyBpcyBhbm90aGVyIHRlc3QgbWVzc2FnZSBmb3IgZG9jayBmZXRjaC4="
    }
  ]
}
```

### 4.4. dock.list

List item metadata addressed to the requesting peer, without message content. Use this method to discover available messages before selectively downloading them with `dock.get`.

- **Category**: Query
- **Request**: `fcdsl` with optional filter, sort, size, and pagination parameters as defined in FAPI2.
- **Response**: `data` contains an array of message metadata objects (sender, size, timestamps, TTL). No `data` field is included in each item.
- **Charging**: Standard query fees only. No egress fees are charged because message content is not transferred.

**Request example**:

```json
{
  "api": "dock.list",
  "fcdsl": {
    "sort": [{ "createdAt": "desc" }],
    "size": 20
  }
}
```

**Response example**:

```json
{
  "code": 0,
  "message": "OK",
  "got": 3,
  "total": 3,
  "data": [
    {
      "mid": "d8f3a2b1c4e5f6a7b8c9d0e1f2a3b4c5",
      "sender": "F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW",
      "size": 26,
      "ttl": 14,
      "status": "pending",
      "createdAt": 1743120000000,
      "expiresAt": 1744329600000
    },
    {
      "mid": "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6",
      "sender": "FHnRyV8PHKFQ1hQRNRMmJk6LkRbSx1FKBJ",
      "size": 42,
      "ttl": 7,
      "status": "pending",
      "createdAt": 1743033600000,
      "expiresAt": 1743638400000
    },
    {
      "mid": "e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2",
      "sender": "F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW",
      "size": 1024,
      "ttl": 30,
      "status": "delivered",
      "createdAt": 1742947200000,
      "expiresAt": 1745539200000
    }
  ]
}
```

### 4.5. dock.check

Check the status of a specific message without downloading its content. Both the sender and any designated recipient MAY call this method.

- **Category**: Operation
- **Request**: `params` with:
  - `mid` (string, REQUIRED): The message ID to check.
- **Response**: `data` contains the message metadata including delivery status.
- **Errors**:
  - 404 (NOT_FOUND): Message ID does not exist or has expired.
  - 403 (FORBIDDEN): Requester is neither the sender nor a designated recipient.

**Request example**:

```json
{
  "api": "dock.check",
  "params": {
    "mid": "d8f3a2b1c4e5f6a7b8c9d0e1f2a3b4c5"
  }
}
```

**Response example**:

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "mid": "d8f3a2b1c4e5f6a7b8c9d0e1f2a3b4c5",
    "sender": "F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW",
    "recipients": ["FEk41Kqjar45fLDriztUDTUkdki7mmcjWK"],
    "size": 26,
    "ttl": 14,
    "status": "pending",
    "createdAt": 1743120000000,
    "expiresAt": 1744329600000,
    "deliveredTo": []
  }
}
```

### 4.6. dock.delete

Delete a message from the server. Only the original sender MAY delete a message. A partial refund is issued for the remaining unused storage period.

- **Category**: Operation
- **Request**: `params` with:
  - `mid` (string, REQUIRED): The message ID to delete.
- **Response**: `data` contains deletion confirmation and the refund amount.
- **Refund**: The sender receives a refund proportional to the remaining TTL: `pricePerKBDay * ceil(size / 1024) * remainingDays`. The ingress fee is not refunded.
- **Errors**:
  - 404 (NOT_FOUND): Message ID does not exist or has expired.
  - 403 (FORBIDDEN): Requester is not the original sender.

**Request example**:

```json
{
  "api": "dock.delete",
  "params": {
    "mid": "d8f3a2b1c4e5f6a7b8c9d0e1f2a3b4c5"
  }
}
```

**Response example**:

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "mid": "d8f3a2b1c4e5f6a7b8c9d0e1f2a3b4c5",
    "deleted": true,
    "remainingDays": 10,
    "refundAmount": "0.00000260"
  }
}
```

### 4.7. dock.extend

Extend the time-to-live of an existing message. Only the original sender MAY extend a message's TTL. The sender pays for the additional storage period.

- **Category**: Operation
- **Request**: `params` with:
  - `mid` (string, REQUIRED): The message ID to extend.
  - `additionalDays` (integer, REQUIRED): Number of days to add to the current TTL. Minimum: 1. The resulting total TTL (original TTL + extension) MUST NOT exceed 365 days.
- **Response**: `data` contains the updated expiration time and the charge amount.
- **Charging**: The sender pays `pricePerKBDay * ceil(size / 1024) * additionalDays` for the extended storage period.
- **Errors**:
  - 404 (NOT_FOUND): Message ID does not exist or has expired.
  - 403 (FORBIDDEN): Requester is not the original sender.
  - 400 (BAD_REQUEST): Extension would exceed the 365-day maximum TTL.
  - 402 (INSUFFICIENT_CREDIT): Sender's credit balance is insufficient.

**Request example**:

```json
{
  "api": "dock.extend",
  "params": {
    "mid": "d8f3a2b1c4e5f6a7b8c9d0e1f2a3b4c5",
    "additionalDays": 30
  }
}
```

**Response example**:

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "mid": "d8f3a2b1c4e5f6a7b8c9d0e1f2a3b4c5",
    "previousExpiresAt": 1744329600000,
    "expiresAt": 1746921600000,
    "additionalDays": 30,
    "chargeAmount": "0.00000780"
  }
}
```

## 5. Security Considerations

### 5.1. Storage Abuse Prevention

DOCK servers MUST enforce the 64 KB size limit to prevent storage abuse. Additionally, servers SHOULD implement rate limiting on `dock.put` to prevent a single sender from flooding the system with a large number of small messages. The FCH micropayment model provides an economic deterrent against abuse, as each stored message incurs a cost proportional to its size and TTL. Servers MAY define a minimum charge per message to ensure that even the smallest messages carry a non-trivial cost.

Servers SHOULD monitor total storage utilization and MAY reject new `dock.put` requests with error code 507 (INSUFFICIENT_STORAGE) when storage capacity is exhausted.

### 5.2. Sender-Only Deletion

Only the original sender of a message MAY delete it via `dock.delete`. Recipients MUST NOT be permitted to delete messages. This design ensures that the sender retains control over the message lifecycle and can reclaim storage fees for undelivered messages. The server MUST verify the requester's authenticated FID (from the FUDP connection) against the message's sender field before permitting deletion.

Recipients who wish to disregard a message simply do not retrieve it; the message expires naturally at the end of its TTL.

### 5.3. TTL Limits

The maximum TTL of 365 days prevents indefinite storage consumption. Servers MUST reject `dock.put` requests with a TTL exceeding 365 days. The `dock.extend` method MUST enforce that the total TTL (original TTL plus all extensions) does not exceed 365 days from the original creation time. This cap ensures predictable storage cost accounting and prevents long-tail storage obligations.

Servers MUST NOT silently truncate TTL values. If a requested TTL exceeds the maximum, the server MUST return an error rather than accepting the request with a reduced TTL, ensuring that clients are always aware of the actual storage duration.

## 6. Versioning

This document defines version 1 of the DOCK component specification (FAPI13V1). Future versions MAY introduce additional methods, modify charging semantics, or adjust size and TTL limits. Version changes follow the FAPI versioning rules defined in FAPI0:

- **Minor changes** (new optional fields, relaxed limits) increment the version number.
- **Breaking changes** (removed methods, incompatible request formats) require a new serial number.

Servers MUST advertise the supported DOCK version through the component type ID. Clients SHOULD verify compatibility before invoking DOCK methods.

## 7. References

- **FAPI0V1** -- FAPI foundational rules and protocol structure.
- **FAPI1V1** -- Core Protocol: wire format, request/response structures, API routing.
- **FAPI2V1** -- FCDSL: Freeverse Common Data Service Language syntax for filter, sort, and pagination.
- **FAPI3V1** -- Components: component model, lifecycle, and built-in component catalog.
- **FAPI4V1** -- Economics: pricing model, credit system, and micropayment mechanics.
- **FEIP5** -- Service protocol: on-chain service declaration format.
- **RFC 2119** -- Key words for use in RFCs to indicate requirement levels.
