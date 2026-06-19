# FAPI13V1_DOCK

## Contents

- [Summary](#summary)
- [Abstract](#abstract)
- [1. Overview](#1-overview)
- [2. Concepts](#2-concepts)
  - [2.1. Recipients](#21-recipients)
  - [2.2. Lifetime (maxDays / expireHeight)](#22-lifetime-maxdays--expireheight)
  - [2.3. Size Limit](#23-size-limit)
  - [2.4. Charging Model](#24-charging-model)
  - [2.5. Forwarding](#25-forwarding)
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
  - [5.3. Lifetime Limits](#53-lifetime-limits)
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

FAPI13V1 defines the DOCK component of the FAPI (Freeverse API Protocol) series. DOCK provides a store-and-forward messaging service in which senders deposit data addressed to one or more recipients, and recipients retrieve the data at a later time. The component is designed for small payloads up to 64 KB with time-limited storage. For larger data, clients SHOULD use the DISK component and send only the DID (Data ID) via DOCK. This specification defines the complete API surface of the DOCK component (7 methods), the charging model, recipient addressing, lifetime semantics, optional cross-server forwarding, and security considerations. The component type ID is `DOCK@No1_NrC7`.

## 1. Overview

The DOCK component serves as a message drop-off and pickup facility within the FAPI ecosystem. Senders deposit data for one or more recipients by calling `dock.put`. Recipients retrieve their messages at a later time using `dock.get`, `dock.fetch`, or `dock.list`. Messages are stored on the server for a configurable number of days (`maxDays`) and are automatically garbage-collected after their `expireHeight` is reached.

DOCK is intended for small, transient payloads -- notifications, control messages, encrypted keys, metadata references, and similar lightweight data. The maximum payload size defaults to 64 KB. For larger data transfers, clients SHOULD upload the data to the DISK component and send the resulting DID as the DOCK message payload, allowing the recipient to retrieve the full data from DISK.

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this document are to be interpreted as described in RFC 2119.

## 2. Concepts

### 2.1. Recipients

A message MAY be addressed to one or more recipients. Each recipient is identified by one of the following:

- **FID** -- A Freecash Identity, addressing a single individual.
- **Team ID** -- Addresses all members of the specified team.
- **Group ID** -- Addresses all members of the specified group.
- **Room ID** -- Addresses all participants of the specified room.

A single `dock.put` request MUST NOT contain more than 100 recipients. When a message is addressed to multiple recipients, the server stores a single copy of the data and indexes the recipients on the document. Each recipient independently retrieves the message and is charged egress fees on retrieval.

### 2.2. Lifetime (maxDays / expireHeight)

The lifetime of a DOCK item is expressed both in days (`maxDays`, supplied by the sender) and in block height (`expireHeight`, computed by the server). One day is treated as 1440 FCH blocks (~1 block per minute).

- **Default**: 7 days.
- **Maximum**: 365 days.
- **Minimum**: 1 day.

When a `dock.put` is accepted at chain height `h0`, the server records `createHeight = h0` and `expireHeight = h0 + maxDays * 1440`. Items are eligible for garbage collection once the chain advances past `expireHeight`. Servers MAY retain expired items briefly but MUST NOT guarantee their availability beyond `expireHeight`.

The sender specifies the lifetime at deposit time via the `maxDays` parameter in `dock.put`. The lifetime MAY be extended after deposit using `dock.extend`, which appends `extraDays * 1440` blocks to `expireHeight`.

A server's default `maxDays` is resolved with priority: on-chain `service.dataExpiresInDays` > `settingMap.dataExpiresInDays` > 7.

### 2.3. Size Limit

The maximum payload size for a single DOCK message defaults to 64 KB (65,536 bytes), measured on the raw binary payload. Servers MAY override this limit via `service.maxDataSize`.

Servers MUST reject `dock.put` requests where the payload exceeds the configured limit with error code `BAD_REQUEST`.

For payloads exceeding the configured limit, clients SHOULD:

1. Upload the data to the DISK component via `disk.put`.
2. Obtain the DID (Data ID) from the DISK response.
3. Send the DID as the DOCK message payload via `dock.put`.
4. The recipient retrieves the DID from DOCK and fetches the full data from DISK.

### 2.4. Charging Model

DOCK charges are computed in satoshi based on storage duration, data size, and transfer volume. All sizes are charged per kilobyte, rounded up: `sizeKB = ceil(size / 1024)`.

- **Storage fee**: `storageFee = sizeKB * maxDays * pricePerKBDay`. Charged to the sender on `dock.put`.
- **Ingress fee**: `ingressFee = sizeKB * pricePerKBIn`. Charged to the sender on `dock.put`.
- **Egress fee**: `egressFee = sizeKB * pricePerKBOut`. Charged to the recipient on `dock.get` and on `dock.fetch` for every item whose payload is returned inline.
- **Extend fee**: `additionalFee = sizeKB * extraDays * pricePerKBDay`. Charged to the sender on `dock.extend`.
- **Delete refund**: On `dock.delete`, the sender receives `storageFee * remainingDays / maxDays`, prorated against the unused storage period. The ingress fee is NOT refunded.

The total charge to the sender at deposit time is `ingressFee + storageFee` (returned as `totalFee`). `dock.list` does not charge egress because content is not transferred. `dock.check` does not transfer payload and does not charge egress.

Pricing fields (`pricePerKBIn`, `pricePerKBOut`, `pricePerKBDay`) are taken from the on-chain `Service` record. If `pricePerKBIn` or `pricePerKBOut` is unset, the server falls back to `pricePerKB`.

### 2.5. Forwarding

A DOCK server MAY relay a `dock.put` to a different DOCK server when the request includes a `targetDockUrl` that does not point to the local server. When forwarding is enabled (controlled by `settingMap.dockForwardEnabled`, default `true`):

1. The local server connects to `targetDockUrl` as a FAPI client and submits the same payload.
2. The remote server stores the item and returns its own `id`, `storageFee`, and `ingressFee`.
3. The local server charges the sender `localIngressFee + localEgressFee + remoteStorageFee + remoteIngressFee`.
4. The response includes `forwarded: true`, the remote `id`, `localFee`, `remoteFee`, and `totalFee`.

If forwarding is disabled, the server returns `METHOD_NOT_ALLOWED`. If the remote DOCK is unreachable or rejects the request, the server returns `BAD_GATEWAY`.

## 3. API List

The DOCK component exposes 7 methods:

| # | API | Category | Description |
|---|---|---|---|
| 1 | `dock.put` | Binary operation | Store data for one or more recipients (binary request) |
| 2 | `dock.get` | Binary operation | Retrieve data by item ID (binary response) |
| 3 | `dock.fetch` | Query | List items with inline Base64 data for the requesting recipient(s) |
| 4 | `dock.list` | Query | List item metadata for the requesting recipient |
| 5 | `dock.check` | Operation | Check item status without downloading |
| 6 | `dock.delete` | Operation | Sender removes an item (partial refund) |
| 7 | `dock.extend` | Operation | Extend an item's lifetime (sender pays) |

## 4. Method Definitions

### 4.1. dock.put

Store data for one or more recipients. The sender pays storage and ingress fees at deposit time.

- **Category**: Binary operation. The request MUST use the unified binary protocol; the payload travels as the binary body, not as a Base64 string in `params`.
- **Request `params`**:
  - `recipients` (array of strings, REQUIRED): Recipient identifiers. Each entry MAY be an FID, team ID, group ID, or room ID. Maximum: 100.
  - `maxDays` (integer, OPTIONAL): Lifetime in days. Defaults to the server's configured default (typically 7). Minimum: 1. Maximum: 365.
  - `dataType` (string, OPTIONAL): Application-level type tag stored on the item.
  - `targetDockUrl` (string, OPTIONAL): If set and not pointing to the local server, the request is forwarded (see [2.5. Forwarding](#25-forwarding)).
- **Request body**: Raw binary payload. MUST NOT be empty and MUST NOT exceed the server's configured `maxDataSize` (default 64 KB).
- **Response `data`**: Item metadata and accounting:
  - `id`, `size`, `maxDays`, `createHeight`, `expireHeight`, `storageFee`, `ingressFee`, `totalFee`, optional `dataType`.
  - When forwarded: also `targetDockUrl`, `localFee`, `remoteFee`, `forwarded: true`.
- **Charging**: Sender pays `ingressFee + storageFee`. For forwarded requests, sender additionally pays the local egress fee and the remote server's `ingressFee + storageFee`.
- **Errors**:
  - `BAD_REQUEST`: Missing/empty body, missing `recipients`, too many recipients, invalid `maxDays`, or oversized payload.
  - `PAYMENT_REQUIRED`: Sender's balance cannot cover the total fee.
  - `METHOD_NOT_ALLOWED`: `targetDockUrl` set but forwarding is disabled.
  - `BAD_GATEWAY`: Remote DOCK is unreachable or rejected the forwarded request.
  - `INTERNAL_ERROR`: Storage or indexing failure.

**Request example** (params portion of the unified request):

```json
{
  "api": "dock.put",
  "params": {
    "recipients": ["FEk41Kqjar45fLDriztUDTUkdki7mmcjWK"],
    "maxDays": 14,
    "dataType": "note"
  }
}
```

**Response example**:

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "d8f3a2b1c4e5f6a7b8c9d0e1f2a3b4c5",
    "size": 26,
    "maxDays": 14,
    "createHeight": 850000,
    "expireHeight": 870160,
    "storageFee": 14,
    "ingressFee": 10,
    "totalFee": 24,
    "dataType": "note"
  }
}
```

**Forwarded response example**:

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "9b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e",
    "size": 26,
    "maxDays": 14,
    "targetDockUrl": "https://dock.example.com",
    "createHeight": 850000,
    "expireHeight": 870160,
    "localFee": 20,
    "remoteFee": 24,
    "totalFee": 44,
    "forwarded": true
  }
}
```

### 4.2. dock.get

Retrieve item data as binary by item ID. Only a designated recipient MAY retrieve the item.

- **Category**: Binary operation
- **Request `params`**:
  - `id` (string, REQUIRED): The item ID returned by `dock.put`.
  - `recipientId` (string, OPTIONAL): A recipient identifier (e.g., team or group ID) to assert membership against the item, in addition to the caller's authenticated peer ID.
- **Response header `data`**: `id`, `sender`, `size`, `createTime`, `expireHeight`, `egressFee`.
- **Response body**: Raw decoded item content (Base64-decoded from the stored representation).
- **Charging**: Recipient pays `egressFee = ceil(size/1024) * pricePerKBOut`.
- **Errors**:
  - `BAD_REQUEST`: Missing `id`.
  - `NOT_FOUND`: Item ID does not exist, or stored payload is missing.
  - `FORBIDDEN`: Caller is not a designated recipient.
  - `GONE`: Item has expired.
  - `PAYMENT_REQUIRED`: Recipient's balance cannot cover the egress fee.

**Request example**:

```json
{
  "api": "dock.get",
  "params": {
    "id": "d8f3a2b1c4e5f6a7b8c9d0e1f2a3b4c5"
  }
}
```

**Response header example**:

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "d8f3a2b1c4e5f6a7b8c9d0e1f2a3b4c5",
    "sender": "F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW",
    "size": 26,
    "createTime": 1743120000000,
    "expireHeight": 870160,
    "egressFee": 10
  }
}
```

The binary response body immediately follows the header and contains the raw decoded item content.

### 4.3. dock.fetch

List items addressed to the requesting peer (or to a list of recipient IDs), with item data included inline as Base64. This combines list+get for small items in a single round-trip.

- **Category**: Query
- **Request `params`** (OPTIONAL):
  - `recipientIds` (array of strings, OPTIONAL): Recipient identifiers to query. The caller MUST be authorized to receive on behalf of each ID.
  - `recipientId` (string, OPTIONAL): Alternative to `recipientIds` for a single recipient.
  - If neither is provided, the requesting peer's own ID is used.
- **Request `fcdsl`** (OPTIONAL): Standard FCDSL `sort`, `size` (max 50, default 20), and `after` (search-after cursor) fields as defined in FAPI2.
- **Response**: `data` is an array of item objects with `id`, `sender`, `size`, `createTime`, `expireHeight`, `recipients`, optional `dataType`, and `dataBase64` (the Base64-encoded payload). Standard pagination fields (`got`, `total`, `last`) are included.
- **Charging**: Recipient pays an egress fee on the aggregate of all returned payloads: `ceil(sum(size)/1024) * pricePerKBOut`. No per-item ingress charge applies.
- **Errors**:
  - `PAYMENT_REQUIRED`: Recipient's balance cannot cover the aggregate egress fee.
  - `INTERNAL_ERROR`: Search failure.

**Request example**:

```json
{
  "api": "dock.fetch",
  "params": {
    "recipientIds": ["FEk41Kqjar45fLDriztUDTUkdki7mmcjWK"]
  },
  "fcdsl": {
    "sort": [{ "createTime": "desc" }],
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
      "id": "d8f3a2b1c4e5f6a7b8c9d0e1f2a3b4c5",
      "sender": "F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW",
      "size": 26,
      "createTime": 1743120000000,
      "expireHeight": 870160,
      "recipients": ["FEk41Kqjar45fLDriztUDTUkdki7mmcjWK"],
      "dataType": "note",
      "dataBase64": "SGVsbG8sIHRoaXMgaXMgYSB0ZXN0IG1lc3NhZ2Uu"
    },
    {
      "id": "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6",
      "sender": "FHnRyV8PHKFQ1hQRNRMmJk6LkRbSx1FKBJ",
      "size": 42,
      "createTime": 1743033600000,
      "expireHeight": 860080,
      "recipients": ["FEk41Kqjar45fLDriztUDTUkdki7mmcjWK"],
      "dataBase64": "VGhpcyBpcyBhbm90aGVyIHRlc3QgbWVzc2FnZSBmb3IgZG9jayBmZXRjaC4="
    }
  ],
  "last": ["1743033600000", "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6"]
}
```

### 4.4. dock.list

List item metadata addressed to the requesting peer, without item content. Use this to discover available items before selectively downloading them with `dock.get`.

- **Category**: Query
- **Request `params`** (OPTIONAL):
  - `recipientId` (string, OPTIONAL): Recipient identifier to query. Defaults to the requesting peer's ID.
- **Request `fcdsl`** (OPTIONAL): Standard FCDSL `sort`, `size` (max 100, default 20), and `after` fields. The server transparently excludes `dataBase64` from results.
- **Response**: `data` is an array of metadata objects: `id`, `sender`, `size`, `createTime`, `expireHeight`, `remainingDays`, `egressFee`, optional `dataType`. Standard pagination fields (`got`, `total`, `last`) are included.
- **Charging**: No egress fee, since item content is not returned.
- **Errors**:
  - `INTERNAL_ERROR`: Search failure.

**Request example**:

```json
{
  "api": "dock.list",
  "fcdsl": {
    "sort": [{ "createTime": "desc" }],
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
      "id": "d8f3a2b1c4e5f6a7b8c9d0e1f2a3b4c5",
      "sender": "F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW",
      "size": 26,
      "createTime": 1743120000000,
      "expireHeight": 870160,
      "remainingDays": 13,
      "dataType": "note",
      "egressFee": 10
    },
    {
      "id": "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6",
      "sender": "FHnRyV8PHKFQ1hQRNRMmJk6LkRbSx1FKBJ",
      "size": 42,
      "createTime": 1743033600000,
      "expireHeight": 860080,
      "remainingDays": 6,
      "egressFee": 10
    },
    {
      "id": "e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2",
      "sender": "F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW",
      "size": 1024,
      "createTime": 1742947200000,
      "expireHeight": 893200,
      "remainingDays": 28,
      "egressFee": 10
    }
  ],
  "last": ["1742947200000", "e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2"]
}
```

### 4.5. dock.check

Check the status of a specific item without downloading its content. Both the sender and any designated recipient MAY call this method.

- **Category**: Operation
- **Request `params`**:
  - `id` (string, REQUIRED): The item ID to check.
- **Response `data`**: `id`, `sender`, `size`, `createTime`, `createHeight`, `expireHeight`, `maxDays`, `recipients`, optional `dataType`, `expired` (boolean), `remainingDays`, `egressFee`. When the caller is the sender, the response additionally includes `storageFee` and `ingressFee`.
- **Errors**:
  - `BAD_REQUEST`: Missing `id`.
  - `NOT_FOUND`: Item ID does not exist.
  - `FORBIDDEN`: Caller is neither the sender nor a designated recipient.

**Request example**:

```json
{
  "api": "dock.check",
  "params": {
    "id": "d8f3a2b1c4e5f6a7b8c9d0e1f2a3b4c5"
  }
}
```

**Response example** (caller is the sender):

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "d8f3a2b1c4e5f6a7b8c9d0e1f2a3b4c5",
    "sender": "F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW",
    "size": 26,
    "createTime": 1743120000000,
    "createHeight": 850000,
    "expireHeight": 870160,
    "maxDays": 14,
    "recipients": ["FEk41Kqjar45fLDriztUDTUkdki7mmcjWK"],
    "dataType": "note",
    "expired": false,
    "remainingDays": 13,
    "egressFee": 10,
    "storageFee": 14,
    "ingressFee": 10
  }
}
```

### 4.6. dock.delete

Delete an item from the server. Only the original sender MAY delete an item. A partial refund is issued for the unused storage period.

- **Category**: Operation
- **Request `params`**:
  - `id` (string, REQUIRED): The item ID to delete.
- **Response `data`**: `id`, `deleted: true`, `refund` (satoshi).
- **Refund**: `refund = storageFee * remainingDays / maxDays`. The ingress fee is NOT refunded. If `remainingDays <= 0` or no storage fee was charged, `refund` is 0.
- **Errors**:
  - `BAD_REQUEST`: Missing `id`.
  - `NOT_FOUND`: Item ID does not exist.
  - `FORBIDDEN`: Caller is not the original sender.

**Request example**:

```json
{
  "api": "dock.delete",
  "params": {
    "id": "d8f3a2b1c4e5f6a7b8c9d0e1f2a3b4c5"
  }
}
```

**Response example**:

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "d8f3a2b1c4e5f6a7b8c9d0e1f2a3b4c5",
    "deleted": true,
    "refund": 13
  }
}
```

### 4.7. dock.extend

Extend the lifetime of an existing item. Only the original sender MAY extend an item. The sender pays for the additional storage period.

- **Category**: Operation
- **Request `params`**:
  - `id` (string, REQUIRED): The item ID to extend.
  - `extraDays` (integer, REQUIRED): Number of days to add. Minimum: 1. Maximum: 365.
- **Response `data`**: `id`, `extraDays`, `newExpireHeight`, `additionalFee`, `totalStorageFee`.
- **Charging**: Sender pays `additionalFee = ceil(size/1024) * extraDays * pricePerKBDay`. The new `expireHeight` is `previousExpireHeight + extraDays * 1440`, and `maxDays` is incremented by `extraDays`.
- **Errors**:
  - `BAD_REQUEST`: Missing `id` or `extraDays` outside `[1, 365]`.
  - `NOT_FOUND`: Item ID does not exist.
  - `FORBIDDEN`: Caller is not the original sender.
  - `PAYMENT_REQUIRED`: Sender's balance cannot cover the additional fee.

**Request example**:

```json
{
  "api": "dock.extend",
  "params": {
    "id": "d8f3a2b1c4e5f6a7b8c9d0e1f2a3b4c5",
    "extraDays": 30
  }
}
```

**Response example**:

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "d8f3a2b1c4e5f6a7b8c9d0e1f2a3b4c5",
    "extraDays": 30,
    "newExpireHeight": 913360,
    "additionalFee": 30,
    "totalStorageFee": 44
  }
}
```

## 5. Security Considerations

### 5.1. Storage Abuse Prevention

DOCK servers MUST enforce the configured `maxDataSize` to prevent storage abuse. Servers SHOULD also enforce the 100-recipient limit per `dock.put` to bound fan-out cost. Additionally, servers SHOULD implement rate limiting on `dock.put` to prevent a single sender from flooding the system with a large number of small items. The FCH micropayment model provides an economic deterrent against abuse, as each stored item incurs a cost proportional to its size and lifetime. Servers MAY define a minimum charge per item to ensure that even the smallest items carry a non-trivial cost.

Servers SHOULD monitor total storage utilization and MAY reject new `dock.put` requests when storage capacity is exhausted.

### 5.2. Sender-Only Deletion

Only the original sender of an item MAY delete it via `dock.delete`. Recipients MUST NOT be permitted to delete items. This design ensures that the sender retains control over the item lifecycle and can reclaim storage fees for undelivered items. The server MUST verify the requester's authenticated peer ID (from the FUDP connection) against the item's `sender` field before permitting deletion or extension.

Recipients who wish to disregard an item simply do not retrieve it; the item expires naturally when the chain advances past `expireHeight`.

### 5.3. Lifetime Limits

The maximum lifetime of 365 days prevents indefinite storage consumption. Servers MUST reject `dock.put` requests with `maxDays` outside `[1, 365]`. The `dock.extend` method MUST enforce that each individual `extraDays` value is within `[1, 365]`. This cap ensures predictable storage cost accounting and prevents long-tail storage obligations.

Servers MUST NOT silently truncate `maxDays` values. If a requested value is out of range, the server MUST return `BAD_REQUEST` rather than accepting the request with a reduced lifetime, ensuring that clients are always aware of the actual storage duration.

## 6. Versioning

This document defines version 1 of the DOCK component specification (FAPI13V1). Future versions MAY introduce additional methods, modify charging semantics, or adjust size and lifetime limits. Version changes follow the FAPI versioning rules defined in FAPI0:

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
