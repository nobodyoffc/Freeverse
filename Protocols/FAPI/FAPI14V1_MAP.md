# FAPI14V1_MAP

|Field|Content|
|---|---|
|Title|MAP|
|Type|FAPI|
|SN|14|
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
  - [2.1. NAT Awareness](#21-nat-awareness)
  - [2.2. Freshness Threshold](#22-freshness-threshold)
  - [2.3. Cleanup Threshold](#23-cleanup-threshold)
  - [2.4. Ping Verification](#24-ping-verification)
  - [2.5. Heartbeat](#25-heartbeat)
  - [2.6. Persistence](#26-persistence)
- [3. Data Model](#3-data-model)
  - [3.1. MapEntry](#31-mapentry)
- [4. API List](#4-api-list)
- [5. Method Definitions](#5-method-definitions)
  - [5.1. map.register](#51-mapregister)
  - [5.2. map.find](#52-mapfind)
  - [5.3. map.unregister](#53-mapunregister)
  - [5.4. map.list](#54-maplist)
  - [5.5. map.stats](#55-mapstats)
- [6. Security Considerations](#6-security-considerations)
  - [6.1. Address Trust](#61-address-trust)
  - [6.2. No Client-Declared Addresses](#62-no-client-declared-addresses)
  - [6.3. Self-Only Unregistration](#63-self-only-unregistration)
- [7. Versioning](#7-versioning)
- [8. References](#8-references)

## Abstract

FAPI14V1 defines the MAP component specification for the FAPI (Freecash API) protocol series. MAP provides a NAT-friendly FID-to-network-address mapping service. Nodes behind Network Address Translation (NAT) register their presence by sending a request to the MAP server, which observes their external IP and port from the UDP packet source address. Other nodes can then look up a peer's registered address to establish direct communication. The component type ID is `MAP@No1_NrC7`. This specification depends on FUDP as the underlying transport layer and assumes familiarity with FAPI1 (Core Protocol) for request/response formats and FAPI3 (Components) for the component model.

## Summary

The MAP component solves the fundamental problem of peer discovery in networks where most nodes reside behind NAT devices. When a node calls `map.register`, the MAP server records the node's FID alongside the external IP address and port observed from the incoming UDP packet -- not any address declared by the client. Other nodes can then call `map.find` with a target FID to retrieve that peer's last-known network address. The server enforces a freshness threshold of 30 seconds: entries older than this are verified via a FUDP ping before being returned. Entries not refreshed within 24 hours are automatically removed. Clients behind NAT SHOULD send a heartbeat registration every 25 seconds to keep their NAT mapping alive. MAP entries are persisted to a JSON file every 60 seconds and on shutdown, and are reloaded on startup.

## 1. Overview

In a peer-to-peer network, nodes behind NAT cannot receive unsolicited inbound connections. The MAP component addresses this by maintaining a server-side registry of FID-to-address mappings. The workflow is as follows:

1. A node connects to the MAP server via FUDP and calls `map.register`. The server observes the node's external IP and port from the UDP source address and records the mapping.
2. Another node wishing to communicate with the first calls `map.find` with the target FID. The server returns the registered address, verifying freshness if necessary.
3. The requesting node can then attempt a direct FUDP connection to the returned address, or use the ROAD relay component if direct connectivity fails.

The MAP server does not relay traffic itself. It serves only as a directory, mapping identities to their observable network addresses.

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this document are to be interpreted as described in RFC 2119.

## 2. Concepts

### 2.1. NAT Awareness

The MAP server does not accept or trust client-declared network addresses. Instead, it observes the external IP address and port from the source of the incoming UDP packet. This approach accurately reflects the peer's NAT-translated address as seen by the server, eliminating the risk of peers registering false or unreachable addresses.

### 2.2. Freshness Threshold

The freshness threshold is **30 seconds** (30000 milliseconds). An entry whose `lastSeen` timestamp is within 30 seconds of the current time is considered fresh and is returned immediately in response to a `map.find` request. An entry older than 30 seconds triggers a ping verification before being returned.

### 2.3. Cleanup Threshold

The cleanup threshold is **24 hours** (86400000 milliseconds). A periodic cleanup task runs every 10 minutes and removes any entry whose `lastSeen` timestamp exceeds 24 hours. This prevents the registry from accumulating stale entries for nodes that have gone permanently offline.

### 2.4. Ping Verification

When a `map.find` request targets an entry older than the freshness threshold, the server performs a FUDP ping to the registered address before returning the result. If the ping succeeds, the entry's `lastSeen` is updated and the `stale` flag is cleared. If the ping fails, the entry is still returned but with `stale` set to `true`. The entry is not removed on ping failure; it remains available until the cleanup threshold is exceeded.

### 2.5. Heartbeat

Clients behind NAT SHOULD periodically call `map.register` at least every **25 seconds** to keep their NAT mapping alive and their MAP entry fresh. The 25-second interval is chosen to stay well within the 30-second freshness threshold while accommodating typical NAT UDP session timeouts, which commonly range from 30 seconds to several minutes. The exact interval a client uses may depend on its NAT device's UDP session timeout characteristics.

### 2.6. Persistence

MAP entries are persisted to a JSON file on disk. The server writes the current state of all entries to disk every **60 seconds** and performs an additional write on graceful shutdown. On startup, the server reloads persisted entries from the file, allowing registrations to survive server restarts. Entries that exceed the cleanup threshold at reload time are discarded.

## 3. Data Model

### 3.1. MapEntry

A MapEntry represents a single FID-to-address mapping in the registry.

| Field | Type | Description |
|---|---|---|
| `fid` | string | The Freecash Identity (FID) of the registered peer. |
| `pubkey` | string | The peer's secp256k1 compressed public key (hex-encoded). |
| `observedIp` | string | The external IP address observed from the UDP packet source. |
| `observedPort` | integer | The external port observed from the UDP packet source. |
| `lastSeen` | integer | Unix timestamp (milliseconds) of the last registration or successful ping. |
| `registeredAt` | integer | Unix timestamp (milliseconds) of the initial registration. Preserved across updates. |

Example:

```json
{
  "fid": "FEk41Kqjar45fLDGQ...",
  "pubkey": "02a1633cafcc01ebfb6d78e39f687a1f0995c62fc95f51ead10a02ee0be551b5dc",
  "observedIp": "203.0.113.42",
  "observedPort": 51234,
  "lastSeen": 1743120000000,
  "registeredAt": 1743116400000
}
```

## 4. API List

The MAP component exposes 5 methods:

| # | API | Category | Description |
|---|---|---|---|
| 1 | `map.register` | Simple operation | Register the caller's address (zero parameters) |
| 2 | `map.find` | Operation | Look up a peer's registered network address |
| 3 | `map.unregister` | Simple operation | Remove the caller's registration |
| 4 | `map.list` | Simple query | List all registered entries (administrative) |
| 5 | `map.stats` | Simple query | Get registration statistics |

## 5. Method Definitions

### 5.1. map.register

Register the calling peer's network address. This method takes no explicit parameters. The server extracts all required information from the FUDP connection context:

- **peerId**: The authenticated FID from the FUDP handshake.
- **pubkey**: The peer's secp256k1 compressed public key from the FUDP connection.
- **observedIp**: The source IP address of the incoming UDP packet.
- **observedPort**: The source port of the incoming UDP packet.

- **Category**: Simple operation
- **Request**: No `params` or `fcdsl` required.
- **Response**: `data` contains the registered MapEntry object.

Subsequent calls to `map.register` from the same peer update the existing entry, refreshing `lastSeen` and updating the observed address if it has changed. The `registeredAt` timestamp is preserved from the initial registration.

**Request example:**

```json
{
  "api": "map.register"
}
```

**Response example:**

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "fid": "FEk41Kqjar45fLDGQ...",
    "pubkey": "02a1633cafcc01ebfb6d78e39f687a1f0995c62fc95f51ead10a02ee0be551b5dc",
    "observedIp": "203.0.113.42",
    "observedPort": 51234,
    "lastSeen": 1743120000000,
    "registeredAt": 1743116400000
  },
  "got": 1,
  "total": 1
}
```

### 5.2. map.find

Look up a peer's registered network address by FID.

- **Category**: Operation
- **Request**: `params` with `fid` (string, the target FID to look up).
- **Response**: `data` contains the MapEntry for the target FID, with an additional `stale` boolean field indicating whether the entry may be outdated.
- **Freshness verification**: If the entry's `lastSeen` is older than 30 seconds, the server attempts a FUDP ping to the registered address. If the ping succeeds, `lastSeen` is updated and `stale` is set to `false`. If the ping fails, `stale` is set to `true` but the entry is not removed.
- **Error**: Returns code 404 if the target FID is not registered.

**Request example:**

```json
{
  "api": "map.find",
  "params": {
    "fid": "FEk41Kqjar45fLDGQ..."
  }
}
```

**Response example (fresh entry):**

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "fid": "FEk41Kqjar45fLDGQ...",
    "pubkey": "02a1633cafcc01ebfb6d78e39f687a1f0995c62fc95f51ead10a02ee0be551b5dc",
    "observedIp": "203.0.113.42",
    "observedPort": 51234,
    "lastSeen": 1743120000000,
    "registeredAt": 1743116400000,
    "stale": false
  },
  "got": 1,
  "total": 1
}
```

**Response example (stale entry, ping failed):**

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "fid": "FEk41Kqjar45fLDGQ...",
    "pubkey": "02a1633cafcc01ebfb6d78e39f687a1f0995c62fc95f51ead10a02ee0be551b5dc",
    "observedIp": "203.0.113.42",
    "observedPort": 51234,
    "lastSeen": 1743119800000,
    "registeredAt": 1743116400000,
    "stale": true
  },
  "got": 1,
  "total": 1
}
```

**Error example (not registered):**

```json
{
  "code": 404,
  "message": "Target FID is not registered.",
  "data": null,
  "got": 0,
  "total": 0
}
```

### 5.3. map.unregister

Remove the calling peer's own registration. A peer can only unregister itself; it cannot remove another peer's entry.

- **Category**: Simple operation
- **Request**: No `params` or `fcdsl` required.
- **Response**: `data` contains `{"success": true, "fid": "<peerId>"}`.
- **Error**: Returns code 404 if the caller was not registered.

**Request example:**

```json
{
  "api": "map.unregister"
}
```

**Response example:**

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "success": true,
    "fid": "FEk41Kqjar45fLDGQ..."
  },
  "got": 1,
  "total": 1
}
```

**Error example (not registered):**

```json
{
  "code": 404,
  "message": "Caller is not registered.",
  "data": null,
  "got": 0,
  "total": 0
}
```

### 5.4. map.list

List all registered entries. Intended for administrative use.

- **Category**: Simple query
- **Request**: No parameters required.
- **Response**: `data` contains an array of all MapEntry objects. `got` and `total` indicate the count.

**Request example:**

```json
{
  "api": "map.list"
}
```

**Response example:**

```json
{
  "code": 0,
  "message": "OK",
  "data": [
    {
      "fid": "FEk41Kqjar45fLDGQ...",
      "pubkey": "02a1633cafcc01ebfb6d78e39f687a1f0995c62fc95f51ead10a02ee0be551b5dc",
      "observedIp": "203.0.113.42",
      "observedPort": 51234,
      "lastSeen": 1743120000000,
      "registeredAt": 1743116400000
    },
    {
      "fid": "F9kYD3oV3fA6KmGkR...",
      "pubkey": "03b2e1c7a5d8f4e9021345abc678def901234567890abcdef1234567890abcdef12",
      "observedIp": "198.51.100.17",
      "observedPort": 43210,
      "lastSeen": 1743119950000,
      "registeredAt": 1743118000000
    }
  ],
  "got": 2,
  "total": 2
}
```

### 5.5. map.stats

Return registration statistics.

- **Category**: Simple query
- **Request**: No parameters required.
- **Response**: `data` contains registration statistics.

**Request example:**

```json
{
  "api": "map.stats"
}
```

**Response example:**

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "totalEntries": 142,
    "freshEntries": 98,
    "staleEntries": 44,
    "freshThresholdMs": 30000,
    "cleanupThresholdMs": 86400000,
    "timestamp": 1743120060000
  },
  "got": 1,
  "total": 1
}
```

## 6. Security Considerations

### 6.1. Address Trust

The MAP server derives peer addresses exclusively from the UDP packet source address as observed by the server's network stack. This is the only trustworthy indicator of a peer's reachable address in the presence of NAT. The server MUST NOT allow peers to specify or override their registered address through request parameters.

### 6.2. No Client-Declared Addresses

The MAP API deliberately provides no mechanism for a client to declare its own IP address or port. All address information is extracted from the FUDP connection context. This design prevents address spoofing attacks where a malicious peer could register a false address to redirect traffic, perform denial-of-service attacks against third parties, or impersonate another node's network location.

### 6.3. Self-Only Unregistration

The `map.unregister` method only permits a peer to remove its own registration. The server identifies the caller from the authenticated FUDP connection and removes only the entry matching that identity. There is no mechanism to remove another peer's entry. This prevents denial-of-service attacks where a malicious peer could unregister legitimate nodes from the directory.

## 7. Versioning

This document specifies MAP version 1. Future versions MAY introduce additional fields to MapEntry, new methods, or modified thresholds. Servers SHOULD indicate supported MAP versions through the FAPI service discovery mechanism defined in FAPI5. Clients SHOULD verify version compatibility before relying on version-specific features.

## 8. References

- **FAPI1V1** (Core Protocol): Request/response structure and wire format.
- **FAPI3V1** (Components): Component model, lifecycle, and type identifiers.
- **FAPI5V1** (Service Discovery): Service registration and discovery mechanisms.
- **FUDP**: The underlying transport protocol providing reliable encrypted streams and peer identity.
- **RFC 2119**: Key words for use in RFCs to indicate requirement levels.
