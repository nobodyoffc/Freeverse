# FAPI15V1_ROAD

|Field|Content|
|---|---|
|Title|ROAD|
|Type|FAPI|
|SN|15|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-03-28|
|PID||

## Contents

- [Abstract](#abstract)
- [Summary](#summary)
- [1. Overview](#1-overview)
- [2. Concepts](#2-concepts)
  - [2.1. Dumb Pipe Design](#21-dumb-pipe-design)
  - [2.2. Two-Hop Limit](#22-two-hop-limit)
  - [2.3. Sender-Driven Discovery](#23-sender-driven-discovery)
  - [2.4. Cost Control](#24-cost-control)
  - [2.5. Charging Model](#25-charging-model)
- [3. API List](#3-api-list)
- [4. Method Definitions](#4-method-definitions)
  - [4.1. road.relay](#41-roadrelay)
  - [4.2. road.forward](#42-roadforward)
  - [4.3. road.stats](#43-roadstats)
- [5. Relay Algorithm](#5-relay-algorithm)
- [6. Relay Error Codes](#6-relay-error-codes)
- [7. Security Considerations](#7-security-considerations)
- [8. Versioning](#8-versioning)
- [9. References](#9-references)

---

## Abstract

FAPI15V1 specifies the ROAD (Relay Of Arbitrary Data) component of the FAPI protocol series. ROAD provides a data relay service for peer-to-peer communication when direct connectivity between peers is unavailable. It operates as a "dumb pipe" that forwards opaque binary payloads from a sender to one or more target FIDs without interpreting, modifying, or inspecting the data. ROAD enforces a maximum of 2 hops per relay chain, requires the MAP component for local peer address resolution, and charges the sender ingress and egress fees per kilobyte per target. This document defines the three ROAD API methods (`road.relay`, `road.forward`, `road.stats`), the relay algorithm, error codes, and the charging model.

## Summary

The ROAD component relays arbitrary binary data between peers that cannot establish direct FUDP connections. ROAD depends on the MAP component (FAPI14) for local address lookup. The sender discovers the target's home ROAD server by querying the target's Freer object via `base.freerByIds` and reading the `home.ROAD` field. ROAD itself never performs on-chain lookups. Data traverses at most 2 hops: from the sender's ROAD to the target's home ROAD, then to the target. Fees are charged per kilobyte (rounded up) per successfully delivered target. The `maxCost` parameter allows senders to cap charges. This document specifies the complete API surface, relay algorithm, error taxonomy, and security properties of the ROAD component.

**Type ID**: `ROAD@No1_NrC7`

## 1. Overview

In a decentralized peer-to-peer network, many nodes sit behind NAT devices or firewalls that prevent inbound connections. While the MAP component (FAPI14) enables peers to discover each other's observed external addresses, NAT hole-punching does not always succeed. ROAD fills this gap by providing a relay path: the sender transmits data to a ROAD server, which forwards it to the target peer.

ROAD follows a "dumb pipe" design. It does not interpret, parse, validate, or modify the relayed payload. From ROAD's perspective, the payload is opaque bytes. This design keeps the relay component simple, minimizes attack surface, and ensures that ROAD can relay any application-level protocol without modification.

ROAD enforces a strict 2-hop limit. Data may be delivered directly by the sender's ROAD server (1 hop), or forwarded once to the target's home ROAD server for final delivery (2 hops). The `road.forward` method enforces this limit by refusing to forward data that has already been forwarded. This prevents relay chains from growing unbounded and limits amplification potential.

ROAD requires the MAP component as a dependency. When attempting local delivery, ROAD looks up the target FID in the co-hosted MAP component to obtain the target's FUDP address. If MAP is not available, ROAD cannot operate.

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this document are to be interpreted as described in RFC 2119.

## 2. Concepts

### 2.1. Dumb Pipe Design

ROAD does not interpret, parse, or modify the relayed data. The payload is opaque bytes from ROAD's perspective. ROAD has no knowledge of the application-layer protocol carried within the payload. It does not perform content filtering, format validation, or deep packet inspection. This design principle ensures that:

- ROAD can relay any application-level protocol without modification or version coupling.
- The relay server cannot selectively censor or alter message content.
- The implementation remains minimal and auditable.

### 2.2. Two-Hop Limit

Data traverses at most 2 network hops between ROAD servers:

- **1 hop**: Sender -> ROAD Server A -> Target (local delivery). The target is registered in Server A's MAP.
- **2 hops**: Sender -> ROAD Server A -> ROAD Server B -> Target (chain relay). Server A forwards the data to Server B via `road.forward`. Server B delivers locally.

The `road.forward` method MUST NOT forward data to another ROAD server. If the target is not registered in the receiving ROAD's local MAP, delivery fails with `code: 404` and the `MAX_HOPS_REACHED` counter is incremented. This hard limit prevents:

- Unbounded relay chains that increase latency and cost.
- Amplification attacks where a single message triggers cascading forwards across many servers.

### 2.3. Sender-Driven Discovery

The sender is responsible for discovering the target's home ROAD URL before calling `road.relay`. The discovery process is:

1. The sender queries `base.freerByIds` on a BASE component with the target's FID.
2. The response contains the target's Freer object, which includes a `home` field.
3. The `home.ROAD` field contains the FUDP URL of the target's home ROAD server.
4. The sender passes this URL as the `targetRoad` parameter in the `road.relay` request.

ROAD itself never performs on-chain lookups, BASE queries, or any external service discovery. This separation of concerns keeps the relay path deterministic and avoids circular dependencies.

### 2.4. Cost Control

The `maxCost` parameter in the `road.relay` request allows the sender to specify the maximum total fee (in satoshi) they are willing to pay. Before processing the relay, the server estimates the total cost:

```
estimatedCost = ceil(dataSize / 1024) * (pricePerKBIn + pricePerKBOut) * numberOfTargets
```

If `estimatedCost` exceeds `maxCost`, the server rejects the entire request with status code 402 (PAYMENT_REQUIRED) and error code `MAX_COST_EXCEEDED` without processing any targets.

### 2.5. Charging Model

ROAD charges the sender both ingress and egress fees, calculated per kilobyte (rounded up to the nearest kilobyte) per target:

```
feePerTarget = ceil(dataSize / 1024) * (pricePerKBIn + pricePerKBOut)
totalFee = feePerTarget * numberOfSuccessfulDeliveries
```

- **pricePerKBIn**: The ingress fee per kilobyte, sourced from the operator's `Service.pricePerKBIn` (falling back to `Service.pricePerKB`, then to a default of 10 satoshi/KB).
- **pricePerKBOut**: The egress fee per kilobyte, sourced from `Service.pricePerKBOut` (with the same fallback chain).
- **Failed deliveries are not charged.** Charging happens only after a successful local FUDP send, a successful ACK-confirmed direct send, or a successful chain-relay response from the remote ROAD.
- For multi-target relays, each successfully delivered target is charged independently. Chain-relayed targets are recorded with the charge tag suffix `:chain` (`road.relay:in:chain`, `road.relay:out:chain`).
- **Balance pre-check.** Before processing any target, the server requires the sender's balance to cover at least one target's cost (`pricePerKBIn + pricePerKBOut` per KB). If not, the request is rejected with code 402 and the `INSUFFICIENT_BALANCE` counter is incremented.

## 3. API List

The ROAD component exposes 3 methods:

| # | API | Category | Binary | Description |
|---|---|---|---|---|
| 1 | `road.relay` | Operation | Request: relay data | Send data to one or more target FIDs via relay |
| 2 | `road.forward` | Operation (internal) | Request: relay data | Receive forwarded data from another ROAD node |
| 3 | `road.stats` | Query | None | Get relay statistics |

## 4. Method Definitions

### 4.1. road.relay

Relay arbitrary data to one or more target FIDs. This is the primary public API for the ROAD component.

- **Category**: Binary operation
- **Request**: `params` with:
  - `targetFid` (string, OPTIONAL): Single target FID.
  - `targetFids` (array of strings, OPTIONAL): Multiple target FIDs. At most 100 targets per request.
  - `targetRoad` (string, OPTIONAL): The FUDP URL of the target's home ROAD server, discovered by the sender via a BASE `freerByIds` lookup (the `home.ROAD` field of the Freer object). If absent, the server assumes the target is reachable locally or attempts direct FUDP delivery.
  - `maxCost` (integer, OPTIONAL): Maximum total cost in satoshi the sender is willing to pay. If the estimated cost exceeds this value, the server rejects the request with code 402.

  At least one of `targetFid` or `targetFids` MUST be provided. If both are provided, they are merged into a single target list. Binary data follows the JSON header (the payload to relay).

- **Response**: `data` contains:
  - `successCount` (integer): Number of targets successfully reached.
  - `failCount` (integer): Number of targets that could not be reached.
  - `totalTargets` (integer): Total number of targets attempted.
  - `chargedIn` (integer): Total ingress fees charged in satoshi.
  - `chargedOut` (integer): Total egress fees charged in satoshi.
  - `totalCharged` (integer): Sum of `chargedIn` and `chargedOut`.
  - `relayResults` (object): Mapping of each target FID to its individual relay result, containing `success` (boolean), `code` (integer FapiCode -- 0 on success, otherwise an HTTP-style status such as 404, 502, or 504), `message` (human-readable description), `chargedIn` (integer), `chargedOut` (integer), `chainRelayed` (boolean, present and `true` only when delivered via another ROAD), and `relayedVia` (string, the URL of the remote ROAD; present only when `chainRelayed` is true).

- **Example request**:
  ```json
  {
    "api": "road.relay",
    "params": {
      "targetFids": ["FBejsS6cJaBrAwPcMjFJYgfRfBBRRbwi2D", "F86zoAvNpFV6fuS3GNz7bjR34h1pMhEC2Q"],
      "targetRoad": "fudp://road.example.com:9000",
      "maxCost": 500
    }
  }
  ```

- **Example response**:
  ```json
  {
    "code": 0,
    "message": "OK",
    "data": {
      "successCount": 2,
      "failCount": 0,
      "totalTargets": 2,
      "chargedIn": 20,
      "chargedOut": 20,
      "totalCharged": 40,
      "relayResults": {
        "FBejsS6cJaBrAwPcMjFJYgfRfBBRRbwi2D": {
          "success": true,
          "code": 0,
          "message": "Delivered",
          "chargedIn": 10,
          "chargedOut": 10
        },
        "F86zoAvNpFV6fuS3GNz7bjR34h1pMhEC2Q": {
          "success": true,
          "code": 0,
          "message": "Delivered via chain relay",
          "chargedIn": 10,
          "chargedOut": 10,
          "chainRelayed": true,
          "relayedVia": "fudp://road.example.com:9000"
        }
      }
    }
  }
  ```

- **Example response (partial failure)**:
  ```json
  {
    "code": 0,
    "message": "OK",
    "data": {
      "successCount": 1,
      "failCount": 1,
      "totalTargets": 2,
      "chargedIn": 10,
      "chargedOut": 10,
      "totalCharged": 20,
      "relayResults": {
        "FBejsS6cJaBrAwPcMjFJYgfRfBBRRbwi2D": {
          "success": true,
          "code": 0,
          "message": "Delivered",
          "chargedIn": 10,
          "chargedOut": 10
        },
        "F86zoAvNpFV6fuS3GNz7bjR34h1pMhEC2Q": {
          "success": false,
          "code": 404,
          "message": "Target home.ROAD is this server but target is not reachable: <reason>",
          "chargedIn": 0,
          "chargedOut": 0
        }
      }
    }
  }
  ```

### 4.2. road.forward

Receive forwarded data from another ROAD node for local delivery. This method is for internal inter-ROAD communication only.

- **Category**: Binary operation (internal)
- **Request**: `params` with:
  - `targetFid` (string, REQUIRED): The ultimate destination FID.
  - `originSid` (string, OPTIONAL): The service ID of the originating ROAD server.

  Binary data follows the JSON header (the payload to deliver).

- **Response**: `data` contains the same structure as a single-target `road.relay` response:
  - `successCount` (integer): 1 if delivered, 0 if not.
  - `failCount` (integer): 0 if delivered, 1 if not.
  - `totalTargets` (integer): Always 1.
  - `chargedIn` (integer): Ingress fees charged.
  - `chargedOut` (integer): Egress fees charged.
  - `totalCharged` (integer): Sum of `chargedIn` and `chargedOut`.
  - `relayResults` (object): Single entry for the target FID, with the same per-target fields as the `road.relay` response (`success`, `code`, `message`, `chargedIn`, `chargedOut`).

- **Behavior**: The receiving ROAD attempts local delivery only. It looks up the `targetFid` in its local MAP component and delivers via FUDP if found. It MUST NOT forward the data to yet another ROAD server (2-hop rule). If the target is not found in local MAP, the per-target result has `success: false` and `code: 404` ("Target not found (max hops reached)"); the server's `MAX_HOPS_REACHED` error counter is incremented.

- **Example request**:
  ```json
  {
    "api": "road.forward",
    "params": {
      "targetFid": "F86zoAvNpFV6fuS3GNz7bjR34h1pMhEC2Q",
      "originSid": "service_abc123"
    }
  }
  ```

- **Example response**:
  ```json
  {
    "code": 0,
    "message": "OK",
    "data": {
      "successCount": 1,
      "failCount": 0,
      "totalTargets": 1,
      "chargedIn": 10,
      "chargedOut": 10,
      "totalCharged": 20,
      "relayResults": {
        "F86zoAvNpFV6fuS3GNz7bjR34h1pMhEC2Q": {
          "success": true,
          "code": 0,
          "message": "Delivered",
          "chargedIn": 10,
          "chargedOut": 10
        }
      }
    }
  }
  ```

### 4.3. road.stats

Return relay statistics for the ROAD component.

- **Category**: Simple query
- **Request**: No parameters required.
- **Response**: `data` contains:
  - `totalRelays` (integer): Total number of relay attempts.
  - `successfulRelays` (integer): Number of successful deliveries.
  - `failedRelays` (integer): Number of failed delivery attempts.
  - `chainRelays` (integer): Number of deliveries forwarded via another ROAD node.
  - `bytesIn` (long): Total bytes received for relay.
  - `bytesOut` (long): Total bytes sent to targets.
  - `totalChargedIn` (long): Total ingress fees charged in satoshi.
  - `totalChargedOut` (long): Total egress fees charged in satoshi.
  - `pricePerKBIn` (integer): Current ingress price per kilobyte in satoshi.
  - `pricePerKBOut` (integer): Current egress price per kilobyte in satoshi.
  - `errorCounts` (object): Mapping of error type names to their occurrence counts.
  - `timestamp` (long): Current server time in milliseconds since epoch.

- **Example response**:
  ```json
  {
    "code": 0,
    "message": "OK",
    "data": {
      "totalRelays": 15420,
      "successfulRelays": 14893,
      "failedRelays": 527,
      "chainRelays": 3201,
      "bytesIn": 104857600,
      "bytesOut": 98304000,
      "totalChargedIn": 102400,
      "totalChargedOut": 96000,
      "pricePerKBIn": 10,
      "pricePerKBOut": 10,
      "errorCounts": {
        "NOT_FOUND": 312,
        "DELIVERY_FAILED": 148,
        "MAX_HOPS_REACHED": 10,
        "INSUFFICIENT_BALANCE": 42,
        "MAX_COST_EXCEEDED": 15
      },
      "timestamp": 1774934400000
    }
  }
  ```

## 5. Relay Algorithm

When `road.relay` is called, the server processes each target FID using the following algorithm. Steps are evaluated in order; the first matching condition determines the delivery path. Multi-target requests are dispatched in parallel (bounded thread pool, up to 10 concurrent workers) with a per-target timeout of 15 seconds.

**Step 1 -- Local MAP Lookup.** The server queries the co-hosted MAP component for the target FID. If the target is registered in MAP, the server adds the target's pubkey + observed address to the FUDP PeerBook and delivers the payload via `sendNotify` (fire-and-forget). Delivery is reported as successful as soon as the FUDP send returns without exception.

**Step 2 -- Hop Limit Check.** If the current request was received via `road.forward` (i.e., this is already a forwarded relay) and the target was not found in Step 1, delivery fails with `code: 404` ("Target not found (max hops reached)"). The server MUST NOT call `road.forward` on another ROAD. The `MAX_HOPS_REACHED` counter in `errorCounts` is incremented.

**Step 3 -- Self-URL Check.** If `targetRoad` is provided and matches the current server's own URL, the server attempts ACK-confirmed direct FUDP delivery via `sendNotifyWaitAck` (3-second timeout). If the target is in the FUDP ConnectionManager (i.e., previously connected as a client), its address is harvested into the PeerBook before sending. On ACK timeout or send failure, the result is `code: 404` and the `DELIVERY_FAILED` counter is incremented.

**Step 4 -- Remote Forwarding.** If `targetRoad` is provided and points to a different server, the server obtains a `FapiClient` to that URL and calls `road.forward` on it (10-second timeout). On a successful response the result is marked `chainRelayed: true` with `relayedVia` set to the remote URL, and the `chainRelays` counter is incremented.

**Step 5 -- Fallback.** If remote forwarding in Step 4 fails (unreachable, error response, or timeout), the server falls back to ACK-confirmed direct FUDP delivery (same path as Step 3). This handles cases where `targetRoad` is an unrecognized alias for this server (e.g., an Android emulator's `10.0.2.2`).

**Step 6 -- No targetRoad.** If `targetRoad` is absent, the server attempts ACK-confirmed direct FUDP delivery. Clients omit `targetRoad` when the target's `home.ROAD` equals the client's configured server URL, or when the target's home ROAD is unknown.

**Step 7 -- All Paths Exhausted.** If no path succeeded, the per-target result carries the failure from the last attempted path and the `NOT_FOUND` counter is incremented.

## 6. Relay Error Codes

The per-target `code` in `relayResults` is a numeric `FapiCode` (HTTP-style status). The values produced by ROAD are:

| Code | Constant | When it appears |
|---|---|---|
| 0 | SUCCESS | Delivery succeeded (local, direct, or chain). |
| 404 | NOT_FOUND | Target not in MAP and not reachable; or `road.forward` could not deliver locally (max hops); or ACK timeout from a direct FUDP send. |
| 502 | BAD_GATEWAY | The local FUDP `sendNotify` raised an exception; or the overall relay returned with zero successful targets. |
| 504 | GATEWAY_TIMEOUT | A multi-target worker exceeded the 15-second per-target timeout. |

Top-level `code` values returned by `road.relay` and `road.forward`:

| Code | Constant | When it appears |
|---|---|---|
| 0 | SUCCESS | At least one target was delivered. |
| 400 | BAD_REQUEST | `data` is empty, `params` missing, no targets supplied, or more than 100 targets. |
| 402 | PAYMENT_REQUIRED | Estimated cost exceeded `maxCost` (`MAX_COST_EXCEEDED`); or sender balance cannot cover the per-target cost (`INSUFFICIENT_BALANCE`). |
| 405 | METHOD_NOT_ALLOWED | Unknown method name on the ROAD endpoint. |
| 502 | BAD_GATEWAY | Every target failed to deliver. |

The `errorCounts` map returned by `road.stats` uses symbolic names (not numeric codes) and is initialized with the following keys:

| Counter | Incremented when |
|---|---|
| `NOT_FOUND` | All delivery paths for a target were exhausted. |
| `DELIVERY_FAILED` | Local MAP delivery raised an exception, or an ACK-confirmed direct send failed/timed out. |
| `MAX_HOPS_REACHED` | A `road.forward` request had no local MAP entry for the target. |
| `INSUFFICIENT_BALANCE` | The sender's balance could not cover the per-target cost. |
| `MAX_COST_EXCEEDED` | The estimated total cost exceeded the sender's `maxCost`. |

## 7. Security Considerations

1. **Spam prevention via fees.** ROAD charges both ingress and egress fees to the sender for every successfully delivered target. This economic cost makes high-volume spam relays expensive and impractical. The per-kilobyte, per-target charging model ensures that costs scale linearly with abuse volume.

2. **Two-hop amplification limit.** The strict 2-hop maximum prevents ROAD from being used as an amplification vector. A single relay request can trigger at most one additional forwarding hop. Without this limit, a malicious sender could construct relay chains that amplify traffic across many servers.

3. **maxCost protection.** The `maxCost` parameter allows senders to set an upper bound on relay fees before processing begins. The server estimates the total cost and rejects the request if it would exceed this limit. This protects senders from unexpected charges due to large payloads or many targets.

4. **Opaque payload.** ROAD does not inspect, validate, or log the relayed payload content. The payload is opaque bytes. This limits the relay server's liability for transported content and reduces the attack surface of the relay implementation. End-to-end encryption of payloads is the responsibility of the communicating peers.

5. **Authentication.** All ROAD requests are transported over FUDP, which authenticates peers using secp256k1 public keys during the handshake. The `peerId` passed to the ROAD handler is the authenticated FID. ROAD does not implement its own authentication layer.

6. **Balance enforcement.** ROAD MUST verify the sender's balance before processing the relay. If the balance cannot cover at least one target's per-target cost, ROAD MUST reject the request with code 402 (`PAYMENT_REQUIRED`) and increment the `INSUFFICIENT_BALANCE` counter without processing any targets.

## 8. Versioning

| Version | Date | Changes |
|---|---|---|
| 1 | 2026-03-28 | Initial specification. Defines the ROAD component with 3 API methods (road.relay, road.forward, road.stats), relay algorithm, error codes, and charging model. |

## 9. References

- **FAPI1**: Core Protocol. Defines the wire format (UnifiedCodec), request/response structures, status codes, binary data handling, and API routing mechanism.
- **FAPI3**: Components. Defines the component model, component interface contract, lifecycle states, and the summary specification of all built-in components including ROAD.
- **FAPI4**: Economics. Defines the pricing model, balance management, and fee charging mechanisms used by ROAD for ingress and egress fees.
- **FAPI14**: MAP. Defines the MAP component that ROAD depends on for local FID-to-address resolution.
- **FUDP**: Freecash UDP Protocol. Provides the transport layer including peer authentication, encryption, and reliable delivery.
- **RFC 2119**: Key words for use in RFCs to indicate requirement levels.
