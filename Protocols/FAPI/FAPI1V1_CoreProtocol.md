# FAPI1V1_CoreProtocol

|Field|Content|
|---|---|
|Title|Core Protocol|
|Type|FAPI|
|SN|1|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-03-28|
|PID||

## Contents

- [Abstract](#abstract)
- [Summary](#summary)
- [1. Introduction](#1-introduction)
- [2. Wire Format (UnifiedCodec)](#2-wire-format-unifiedcodec)
  - [2.1. Message Envelope](#21-message-envelope)
  - [2.2. Encoding Procedure](#22-encoding-procedure)
  - [2.3. Decoding Procedure](#23-decoding-procedure)
  - [2.4. Format Detection](#24-format-detection)
  - [2.5. Streaming Support](#25-streaming-support)
- [3. Request Structure (FapiRequest)](#3-request-structure-fapirequest)
  - [3.1. JSON Schema](#31-json-schema)
  - [3.2. Field Definitions](#32-field-definitions)
  - [3.3. API Routing](#33-api-routing)
  - [3.4. Request Categories](#34-request-categories)
- [4. Response Structure (FapiResponse)](#4-response-structure-fapiresponse)
  - [4.1. JSON Schema](#41-json-schema)
  - [4.2. Field Definitions](#42-field-definitions)
- [5. Status Codes](#5-status-codes)
- [6. Request Validation](#6-request-validation)
- [7. Request-Response Flow](#7-request-response-flow)
- [8. References](#8-references)

## Abstract

FAPI1V1 defines the Core Protocol for the FAPI (Freecash API) protocol series. It specifies the binary wire format used for encoding and decoding all FAPI messages, the structure of request and response objects, status codes for indicating operation outcomes, and the mechanism by which API calls are routed to service components. This specification is transport-agnostic at the message level but assumes FUDP as the underlying transport layer. It is language-agnostic and intended for implementation across any platform or runtime.

## Summary

The FAPI Core Protocol provides a unified binary envelope (UnifiedCodec) that encapsulates a JSON header followed by optional binary data. All client-server communication in the FAPI ecosystem uses this envelope. Requests identify their target via a two-part `api` field in "component.method" format. Responses carry structured result data, pagination state, blockchain synchronization metadata, and account balance information. A set of numeric status codes modeled after HTTP semantics communicates success or failure. This document also defines mandatory validation rules that all conforming server implementations must enforce.

## 1. Introduction

FAPI is a protocol suite for building decentralized API services on top of the Freecash blockchain. The protocol suite is organized into numbered specifications:

- **FAPI1** (this document): Core Protocol -- wire format, request/response structures, status codes, API routing.
- **FAPI2**: FCDSL (Freecash Domain Specific Language) -- query syntax for data retrieval operations.
- Additional FAPI specifications define individual components and their method contracts.

All FAPI messages are transported over FUDP (Freecash UDP Protocol), which provides reliable, encrypted, multiplexed streams between peers. FUDP handles peer identity, authentication, and connection management. FAPI operates at the application layer above FUDP, concerned only with message encoding, API semantics, and request processing.

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this document are to be interpreted as described in RFC 2119.

## 2. Wire Format (UnifiedCodec)

### 2.1. Message Envelope

All FAPI messages -- both requests and responses -- use the following binary envelope:

```
+-------------------------------------------------------+
| Header Length (4 bytes, big-endian signed int32)       |
| JSON Header  (UTF-8 encoded string)                   |
| Binary Data  (remaining bytes, optional)              |
+-------------------------------------------------------+
```

The envelope consists of three contiguous regions:

1. **Header Length**: A 4-byte big-endian signed 32-bit integer indicating the byte length of the JSON header that follows.
2. **JSON Header**: A UTF-8 encoded JSON string of exactly `headerLength` bytes, representing either a FapiRequest or FapiResponse object.
3. **Binary Data**: Zero or more bytes of opaque binary content (e.g., file content, serialized objects). This region is optional and its presence is indicated by the `dataSize` field in the JSON header.

### 2.2. Encoding Procedure

To encode a FAPI message:

1. Serialize the FapiRequest or FapiResponse object to a JSON string.
2. Encode the JSON string as a UTF-8 byte array. Let this be `jsonBytes`.
3. Write 4 bytes: the length of `jsonBytes` as a big-endian signed 32-bit integer.
4. Write `jsonBytes`.
5. If binary data is present (i.e., `dataSize > 0` in the message object), append the binary data immediately after `jsonBytes`.

### 2.3. Decoding Procedure

To decode a FAPI message:

1. Read 4 bytes from the input and interpret them as a big-endian signed 32-bit integer. This is `headerLength`.
2. Read exactly `headerLength` bytes and decode them as a UTF-8 string. This is the JSON header.
3. Parse the JSON string into a FapiRequest or FapiResponse object.
4. If there are remaining bytes in the input beyond the JSON header, they constitute the binary data payload.
5. The `dataSize` field in the parsed JSON header indicates the expected number of binary data bytes. Implementations SHOULD verify that the actual binary data length matches `dataSize`.

### 2.4. Format Detection

To determine whether a byte sequence is a valid UnifiedCodec message:

1. The data MUST be at least 5 bytes in length.
2. The first 4 bytes, interpreted as a big-endian signed 32-bit integer, MUST be a positive value less than `(totalLength - 4)`.
3. The 5th byte (`data[4]`) SHOULD be `0x7B` (the ASCII/UTF-8 code for `{`), indicating the start of a JSON object.

If all three conditions hold, the data may be treated as a UnifiedCodec message. This heuristic is not cryptographically secure and is intended only for protocol multiplexing and debugging.

### 2.5. Streaming Support

For large binary payloads, the header and binary data MAY be sent in separate transmissions:

1. **Header-only message**: `[4-byte length][JSON bytes]` with no binary data appended. The `dataSize` field in the JSON header indicates that binary data will follow.
2. **Binary data stream**: The binary payload is transmitted separately, for example as file chunks over the same FUDP stream.

The `dataSize` field in the JSON header tells the receiver how many binary bytes to expect in total. The receiver MUST buffer incoming data until `dataSize` bytes of binary content have been received.

Implementations MUST NOT interleave binary data from multiple concurrent requests on the same stream. Each FUDP stream carries at most one FAPI request-response exchange at a time. Concurrent requests MUST use separate FUDP streams.

## 3. Request Structure (FapiRequest)

### 3.1. JSON Schema

```json
{
  "id": "req-1711612800000-a1b2c3d4",
  "api": "component.method",
  "sid": "txid...",
  "via": "FID...",
  "fcdsl": { ... },
  "params": { ... },
  "dataSize": 1024000,
  "dataHash": "sha256hex...",
  "maxCost": 100000
}
```

### 3.2. Field Definitions

| Field | Type | Required | Description |
|---|---|---|---|
| id | string | REQUIRED | Client-generated unique request identifier. Recommended format: `req-{timestamp}-{random}`, where `timestamp` is Unix epoch milliseconds and `random` is a hex string of at least 4 bytes. Used for idempotency detection and request-response correlation. |
| api | string | REQUIRED | API endpoint in `component.method` format. The component name is case-insensitive; the method name is case-sensitive. Examples: `base.search`, `disk.put`, `road.relay`. |
| sid | string | OPTIONAL | Service ID, which is the transaction ID of the on-chain service registration. Used when a server hosts multiple logical services and the client must specify which service to address. |
| via | string | OPTIONAL | Revenue channel FID (Freecash ID). When set, the referenced channel receives a share of the service fee charged for this request. Fee-splitting ratios are defined by the service configuration. |
| fcdsl | object | CONDITIONAL | Query parameters expressed in FCDSL syntax (see FAPI2). REQUIRED for query methods (`getByIds`, `search`, `list`, `totals`). MUST NOT be present simultaneously with `params`. |
| params | object | CONDITIONAL | Operation parameters as a JSON object. REQUIRED for operation methods (`put`, `get`, `carve`, `delete`, `relay`, etc.). MUST NOT be present simultaneously with `fcdsl`. |
| dataSize | integer | OPTIONAL | Size of attached binary data in bytes. When greater than 0, binary data follows the JSON header in the wire format. Default: 0. |
| dataHash | string | OPTIONAL | SHA-256 hex digest of the attached binary data. Used for integrity verification. Servers SHOULD verify the hash when present and reject the request with code 400 if verification fails. |
| maxCost | integer | OPTIONAL | Maximum cost the client is willing to pay for this request, in satoshi. The server MUST reject the request with code 402 if the actual cost would exceed this value. A value of null or 0 indicates no cost limit. |

### 3.3. API Routing

The `api` field determines which component and method handle the request. The routing algorithm is:

1. Split the `api` string on the first `.` character.
2. The left part is the component name. Convert it to uppercase: `componentName = api.split(".")[0].toUpperCase()`.
3. The right part is the method name: `methodName = api.split(".")[1]`.
4. Look up the component by `componentName` in the server's component registry.
5. If the component is not found, return code 404 with message "Component not found".
6. Dispatch to the method handler identified by `methodName` on the resolved component.
7. If the method is not found on the component, return code 405 with message "Method not allowed".

Component names are case-insensitive to simplify client implementations. Method names are case-sensitive and MUST match exactly.

### 3.4. Request Categories

FAPI requests fall into four categories based on their content:

| Category | Characteristics | Typical Methods |
|---|---|---|
| Query requests | `fcdsl` field is set; `params` is absent | `getByIds`, `search`, `list`, `totals` |
| Operation requests | `params` field is set; `fcdsl` is absent | `put`, `get`, `carve`, `delete`, `relay` |
| Simple requests | Neither `fcdsl` nor `params` is set | `health`, `stats`, `info` |
| Binary requests | `dataSize > 0`; binary data follows the JSON header | `put` (with file), `upload` |

A request MAY be both an operation request and a binary request (e.g., `disk.put` with file content). A request MUST NOT have both `fcdsl` and `params` set simultaneously; servers MUST reject such requests with code 400.

## 4. Response Structure (FapiResponse)

### 4.1. JSON Schema

```json
{
  "id": "resp-1711612800123-x9y8z7",
  "requestId": "req-1711612800000-a1b2c3d4",
  "code": 0,
  "message": "Success",
  "data": { ... },
  "got": 20,
  "total": 500,
  "last": ["cursor1", "cursor2"],
  "bestHeight": 1234567,
  "bestBlockId": "abc123...",
  "balance": 10000000,
  "balanceSeq": 42,
  "dataSize": 1024000,
  "charged": 50000
}
```

### 4.2. Field Definitions

| Field | Type | Required | Description |
|---|---|---|---|
| id | string | REQUIRED | Server-generated unique response identifier. Recommended format: `resp-{timestamp}-{random}`. |
| requestId | string | REQUIRED | Echoed from the request's `id` field. Used for request-response correlation. Clients MUST use this field to match responses to outstanding requests. |
| code | integer | REQUIRED | Numeric status code indicating the outcome. `0` indicates success. See Section 5 for the complete list. |
| message | string | REQUIRED | Human-readable status message. Intended for logging and debugging; clients SHOULD NOT parse this field programmatically. |
| data | any | OPTIONAL | Response payload. The type and structure depend on the API called. May be a JSON object, array, string, number, or null. |
| got | integer | OPTIONAL | Number of items returned in this response. Present for paginated query responses. |
| total | integer | OPTIONAL | Total number of items matching the query across all pages. Present for paginated query responses where the total is known or estimated. |
| last | array of strings | OPTIONAL | Pagination cursor. To retrieve the next page, pass this value as `fcdsl.last` in the subsequent query request. Absent when there are no more pages. |
| bestHeight | integer | OPTIONAL | Current best block height of the server's blockchain view. Provides clients with synchronization context. |
| bestBlockId | string | OPTIONAL | Hash of the current best block. Together with `bestHeight`, allows clients to detect chain reorganizations. |
| balance | integer | OPTIONAL | Client's current account balance in satoshi on this service. Present when the service uses a prepaid balance model. |
| balanceSeq | integer | OPTIONAL | Monotonically increasing sequence number for the balance. Can be used for optimistic concurrency control on balance-dependent operations. |
| dataSize | integer | OPTIONAL | Size of attached binary data in bytes. When greater than 0, binary data follows the JSON header in the response wire format. |
| charged | integer | OPTIONAL | Actual cost charged for this request in satoshi. Present when the service charges per-request fees. Clients can compare this with `maxCost` to verify billing. |

## 5. Status Codes

FAPI uses numeric status codes inspired by HTTP semantics but adapted for the FAPI context.

### 5.1. Code Ranges

| Range | Category | Description |
|---|---|---|
| 0 | Success | The operation completed successfully. |
| 1 | Generic Error | An unspecified error occurred. |
| 400-499 | Client Error | The request was malformed, unauthorized, or otherwise rejected due to client-side issues. |
| 500-599 | Server Error | The server encountered an internal failure while processing a valid request. |

### 5.2. Code Definitions

| Code | Name | Description |
|---|---|---|
| 0 | SUCCESS | Operation completed successfully. The `data` field contains the result. |
| 1 | ERROR | Generic error that does not fit any specific category. The `message` field provides details. |
| 400 | BAD_REQUEST | The request is malformed, has invalid parameters, or violates a validation rule. |
| 401 | UNAUTHORIZED | Authentication failed. Reserved for cases where FUDP-level authentication is insufficient or additional application-level authentication is required. Under normal operation, peer identity is established by FUDP and this code is not used. |
| 402 | PAYMENT_REQUIRED | The client's account balance is insufficient to cover the request cost, or the estimated cost exceeds `maxCost`. |
| 403 | FORBIDDEN | The client is authenticated but not authorized for the requested operation. Also used when credit limits are exceeded. |
| 404 | NOT_FOUND | The requested component, API method, or data resource does not exist. |
| 405 | METHOD_NOT_ALLOWED | The specified method does not exist on the resolved component. |
| 409 | CONFLICT | The request conflicts with existing state, such as a duplicate request ID (idempotency violation) or a concurrent modification conflict. |
| 410 | GONE | The requested resource existed previously but has been permanently deleted or revoked. |
| 413 | PAYLOAD_TOO_LARGE | The request body or attached binary data exceeds the server's size limit. |
| 429 | TOO_MANY_REQUESTS | The client has exceeded the rate limit. Clients SHOULD implement exponential backoff before retrying. |
| 500 | INTERNAL_ERROR | An unexpected server-side error occurred. The `message` field MAY contain diagnostic information. |
| 501 | NOT_IMPLEMENTED | The requested feature or method is recognized but not yet implemented. |
| 502 | BAD_GATEWAY | An external dependency (e.g., Elasticsearch, blockchain RPC node) returned an error. |
| 503 | SERVICE_UNAVAILABLE | The target component is not ready, is shutting down, or is temporarily overloaded. |
| 504 | GATEWAY_TIMEOUT | An external dependency did not respond within the expected time. |

## 6. Request Validation

Conforming server implementations MUST validate all incoming requests before processing. The following table defines the mandatory validation rules:

| Rule | Condition | Error Code | Error Message |
|---|---|---|---|
| API field presence | `api` field MUST be present and non-empty | 400 | "Missing api field" |
| API format | `api` field MUST contain at least one `.` character | 400 | "Invalid api format" |
| Method name | The method part (after the first `.`) MUST NOT be empty | 400 | "Empty method name" |
| Query method parameters | `fcdsl` MUST be present for methods: `getByIds`, `search`, `list`, `totals` | 400 | "fcdsl required for query methods" |
| Operation method parameters | `params` MUST be present for methods: `put`, `get`, `carve`, `delete`, `relay` | 400 | "params required for operation methods" |
| Mutual exclusion | `fcdsl` and `params` MUST NOT both be present | 400 | "fcdsl and params are mutually exclusive" |
| ID list size | `fcdsl.ids` list MUST NOT exceed 100 elements | 400 | "ID list exceeds maximum of 100" |
| Page size | `fcdsl.size` MUST NOT exceed 100 | 400 | "Page size exceeds maximum of 100" |
| Entity name length | `fcdsl.entity` MUST NOT exceed 64 characters | 400 | "Entity name exceeds 64 characters" |
| Binary data size | Attached binary data MUST NOT exceed 10 MB (10,485,760 bytes) | 413 | "Payload too large" |
| Batch size | Batch operations MUST NOT include more than 50 items | 400 | "Batch size exceeds maximum of 50" |
| Peer ID format | The authenticated peer ID (from FUDP) MUST start with `F` and be 26-35 characters in length | 401 | "Invalid peer ID format" |

Servers MAY impose additional validation rules beyond those listed here, but MUST implement all rules in this table. When multiple validation rules are violated, the server MAY report any one of the violations; it is not required to report all of them.

## 7. Request-Response Flow

The complete lifecycle of a FAPI request-response exchange proceeds as follows:

1. **Serialization**: The client constructs a FapiRequest object and serializes it to a JSON string. If binary data is to be attached, the client sets `dataSize` and optionally `dataHash`.

2. **Encoding**: The client encodes the message using UnifiedCodec: `[4-byte header length][JSON bytes][binary data (if any)]`.

3. **Transmission**: The client sends the encoded bytes over a FUDP stream. The FUDP layer handles encryption, reliability, and flow control.

4. **Reception**: The server receives the stream data from FUDP and decodes it using UnifiedCodec.

5. **Validation**: The server validates the request against all rules defined in Section 6. If validation fails, the server returns an error response with the appropriate status code.

6. **Routing**: The server parses the `api` field to determine the target component and method (see Section 3.3).

7. **Processing**: The resolved component method processes the request. This may involve querying Elasticsearch, reading from storage, performing blockchain operations, or other service-specific logic.

8. **Response Construction**: The component constructs a FapiResponse with the result data, pagination metadata, blockchain state, and billing information as appropriate.

9. **Response Encoding**: The server encodes the response using UnifiedCodec, optionally attaching binary data.

10. **Response Transmission**: The server sends the encoded response back to the client on the same FUDP stream.

11. **Response Decoding**: The client decodes the response, correlates it with the original request using the `requestId` field, and processes the result.

### 7.1. Error Handling

If the server cannot decode the request at all (e.g., invalid UnifiedCodec framing), it SHOULD close the FUDP stream with an appropriate error. If the request is decodable but invalid, the server MUST return a FapiResponse with the appropriate error code and MUST NOT close the stream (allowing the client to send further requests if the transport supports it).

### 7.2. Idempotency

Clients SHOULD generate unique `id` values for each request. Servers MAY use the `id` field to detect and reject duplicate requests (returning code 409). The scope and duration of idempotency tracking is implementation-defined, but servers SHOULD maintain idempotency state for at least 60 seconds after the initial request.

### 7.3. Timeouts

This specification does not define mandatory timeout values. Implementations SHOULD document their timeout behavior. As a guideline, clients SHOULD time out after 30 seconds for standard requests and allow longer timeouts for binary transfers proportional to `dataSize`.

## 8. References

- **FUDP**: Freecash UDP Protocol. Provides the transport layer for FAPI, including peer identity, encryption, reliable delivery, and stream multiplexing.
- **FAPI2**: FCDSL (Freecash Domain Specific Language). Defines the query syntax used in the `fcdsl` field of FapiRequest objects for data retrieval operations.
- **RFC 2119**: Key words for use in RFCs to indicate requirement levels.
