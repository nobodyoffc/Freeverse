# FAPI5V1_ServiceDiscovery

|Field|Content|
|---|---|
|Title|Service Discovery|
|Type|FAPI|
|SN|5|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-03-28|
|PID||

## Contents

- [Abstract](#abstract)
- [Summary](#summary)
- [1. Introduction](#1-introduction)
- [2. Service Registration](#2-service-registration)
  - [2.1. On-Chain Service Declaration](#21-on-chain-service-declaration)
  - [2.2. Service Object Fields](#22-service-object-fields)
  - [2.3. Service Lifecycle](#23-service-lifecycle)
- [3. Discovery Flow](#3-discovery-flow)
  - [3.1. Bootstrap Connection](#31-bootstrap-connection)
  - [3.2. Bootstrap Handshake](#32-bootstrap-handshake)
  - [3.3. Query On-Chain Providers](#33-query-on-chain-providers)
  - [3.4. Select Service](#34-select-service)
  - [3.5. Connect to Selected Service](#35-connect-to-selected-service)
- [4. Direct Connection](#4-direct-connection)
- [5. Custom Endpoint Connection](#5-custom-endpoint-connection)
- [6. Client Configuration](#6-client-configuration)
- [7. Service Health](#7-service-health)
  - [7.1. Health Check Request](#71-health-check-request)
  - [7.2. Health Check Response](#72-health-check-response)
  - [7.3. Failover](#73-failover)
- [8. Security Considerations](#8-security-considerations)
  - [8.1. Bootstrap Trust](#81-bootstrap-trust)
  - [8.2. Service Authenticity](#82-service-authenticity)
  - [8.3. Man-in-the-Middle Protection](#83-man-in-the-middle-protection)
  - [8.4. Endpoint Freshness](#84-endpoint-freshness)
- [9. References](#9-references)

## Abstract

FAPI5V1 defines the Service Discovery mechanism for the FAPI (Freecash API) protocol series. It specifies how FAPI services are registered on the Freecash blockchain via FEIP protocol declarations, how clients discover available services through bootstrap endpoints and on-chain queries, and how clients select and connect to a service provider. This specification covers the full lifecycle from initial bootstrap through provider selection, health verification, and failover. It is transport-dependent on FUDP and assumes familiarity with FAPI1 (Core Protocol) for request/response formats and FAPI3 (Components) for component type identifiers.

## Summary

FAPI services advertise their availability by publishing a service declaration on the Freecash blockchain using the FEIP service protocol. Each declaration binds a Freecash Identity (FID) to a FUDP endpoint, a set of hosted component types, and pricing information. Clients discover services through a two-phase process: first, they connect to a well-known bootstrap endpoint to gain initial network access; then, they issue an on-chain query to enumerate active FAPI providers. The client selects a provider based on available components, pricing, latency, or trust, and establishes a FUDP connection to the chosen endpoint. Health checks allow clients to verify service availability before and during use. This document also defines security considerations for the discovery process.

## 1. Introduction

In a decentralized service ecosystem, clients cannot rely on a single centralized registry or DNS-based discovery. FAPI addresses this by anchoring service declarations on the Freecash blockchain, making them tamper-evident and publicly auditable. Any node with access to the blockchain (or to an FAPI node that indexes blockchain data) can enumerate the full set of available FAPI service providers.

The discovery process is designed to be simple and robust:

1. Connect to a bootstrap endpoint (a well-known FAPI node).
2. Query for on-chain FAPI service registrations.
3. Select a provider and connect.

Clients that already know a provider's endpoint -- from local configuration, peer exchange, or prior sessions -- MAY skip the bootstrap phase entirely.

This specification depends on:

- **FUDP**: The underlying transport protocol, providing reliable encrypted streams and peer identity.
- **FEIP**: The on-chain protocol for publishing service declarations.
- **FAPI1** (Core Protocol): The wire format and request/response structure used for all queries.
- **FAPI3** (Components): The component type identifiers referenced in service declarations.

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this document are to be interpreted as described in RFC 2119.

## 2. Service Registration

### 2.1. On-Chain Service Declaration

FAPI services register on the Freecash blockchain using the FEIP service protocol. A service declaration is an OP_RETURN transaction signed by the service operator's FID. The declaration creates a persistent, publicly verifiable record that binds the operator's identity to a service endpoint and its capabilities.

The on-chain transaction ID serves as the unique identifier for the service registration.

### 2.2. Service Object Fields

The service declaration contains the following fields:

| Field | Type | Required | Description |
|---|---|---|---|
| type | string | REQUIRED | Fixed value: `"FAPI"`. Identifies this as an FAPI service. |
| types | array of strings | REQUIRED | Component type IDs hosted by this service. Each entry is a component identifier as defined in FAPI3. E.g., `["BASE@No1_NrC7", "DISK@No1_NrC7", "MAP@No1_NrC7"]`. |
| apiUrl | string | REQUIRED | FUDP endpoint URL. Format: `fudp://host:port`. E.g., `"fudp://fapi.cid.cash:8500"`. |
| pricePerKB | number | OPTIONAL | General per-kilobyte charge in FCH. Applies when direction-specific pricing is not set. |
| pricePerKBIn | number | OPTIONAL | Ingress (client-to-server) per-kilobyte charge in FCH. |
| pricePerKBOut | number | OPTIONAL | Egress (server-to-client) per-kilobyte charge in FCH. |
| pricePerKBDay | number | OPTIONAL | Storage per-kilobyte-per-day charge in FCH. Applicable to persistent storage components (e.g., DOCK). |
| minCredit | number | OPTIONAL | Minimum credit balance in FCH required to use the service. Clients with a balance below this threshold will be rejected. |
| desc | string | OPTIONAL | Human-readable description of the service. |
| id | string | -- | The transaction ID of the service registration. This field is not included in the OP_RETURN payload; it is the transaction ID itself and is populated by indexers. |

**Pricing resolution**: If both `pricePerKB` and a direction-specific field (`pricePerKBIn` or `pricePerKBOut`) are set, the direction-specific field takes precedence for that direction. If no pricing fields are set, the service is free to use (subject to any `minCredit` requirement).

### 2.3. Service Lifecycle

A service registration has two possible states:

- **Active**: The service is currently accepting connections. The registration transaction has not been followed by a close transaction.
- **Closed**: The operator has published an on-chain close transaction referencing the original registration. The service is no longer available.

Clients MUST filter out closed services during discovery. Indexers MUST track close transactions and update the `active` status of service records accordingly.

A service operator MAY publish a new registration at any time, creating a new service record with a new transaction ID. The old registration remains valid unless explicitly closed.

## 3. Discovery Flow

### 3.1. Bootstrap Connection

Clients that do not yet have a connection to any FAPI node MUST begin by connecting to a bootstrap endpoint. Bootstrap endpoints are well-known FAPI nodes that provide initial network access.

#### Default Bootstrap Endpoints

| Endpoint | Purpose |
|---|---|
| `fudp://127.0.0.1:8500` | Local development |
| `fudp://fapi.cid.cash:8500` | Production |
| `fudp://fapi.apip.cash:8500` | Production (alternative) |

The default FAPI port is **8500**.

Implementations SHOULD attempt bootstrap endpoints in order, falling back to the next endpoint if the current one is unreachable. Implementations MAY allow the user to configure additional or alternative bootstrap endpoints.

### 3.2. Bootstrap Handshake

The bootstrap handshake establishes a FUDP connection to the bootstrap node:

1. **Client sends HELLO**: The client initiates a FUDP HELLO message to the bootstrap endpoint.
2. **Server responds with PUBLIC_KEY**: The server replies with its public key as part of the FUDP handshake. At this point, ECDH key exchange occurs and the connection becomes encrypted.
3. **Client sends PING**: The client sends a FUDP PING message to verify the connection is live.
4. **Server responds with PONG**: The server replies with PONG, confirming the connection is operational.
5. **Connection established**: The FUDP connection is now ready. The server's peer ID (FID) is known to the client.

#### Handshake Timeouts

| Parameter | Default Value | Description |
|---|---|---|
| HELLO timeout | 5,000 ms | Maximum time to wait for a response to the HELLO message. |
| PING timeout | 5,000 ms | Maximum time to wait for a PONG response. |

If either timeout expires, the client MUST close the connection attempt and proceed to the next bootstrap endpoint (if any). If all bootstrap endpoints are exhausted, the client MUST report a connection failure to the caller.

### 3.3. Query On-Chain Providers

Once connected to any FAPI node (whether via bootstrap or a prior connection), the client queries for available FAPI service providers by issuing a search request using the FAPI1 request format:

```json
{
  "api": "base.search",
  "fcdsl": {
    "entity": "service",
    "filter": {
      "must": [
        {"term": {"type": "FAPI"}},
        {"term": {"active": true}}
      ]
    },
    "sort": [{"lastHeight": "desc"}],
    "size": "20"
  }
}
```

**Field descriptions**:

- `api`: Routes to the BASE component's `search` method (see FAPI3).
- `fcdsl.entity`: Targets the `service` entity, which contains on-chain service registrations indexed from blockchain data.
- `fcdsl.filter.must`: Requires `type` to be `"FAPI"` and `active` to be `true`, filtering out non-FAPI services and closed registrations.
- `fcdsl.sort`: Orders results by `lastHeight` descending, so the most recently updated registrations appear first.
- `fcdsl.size`: Limits the result set to 20 entries. Clients MAY adjust this value.

The response body contains an array of service objects as described in Section 2.2. Each object includes the service's endpoint, pricing, hosted components, and registration metadata.

Clients MAY issue additional queries with pagination to retrieve more results if needed.

### 3.4. Select Service

The client selects a service provider from the query results. Selection criteria include, but are not limited to:

1. **Component availability**: The service's `types` array MUST include all component types the client intends to use. For example, a client that needs MAP and ROAD components should select a service whose `types` array contains both.
2. **Pricing**: The client compares `pricePerKB`, `pricePerKBIn`, `pricePerKBOut`, and `pricePerKBDay` across providers and selects based on cost requirements.
3. **Latency**: The client MAY probe multiple candidates with PING messages and select the one with the lowest round-trip time.
4. **Reputation or trust**: The client MAY maintain a local trust list of known FIDs and prefer services operated by trusted identities.
5. **Credit requirements**: The client MUST ensure its balance meets or exceeds the `minCredit` threshold of the selected service.

The selection algorithm is implementation-defined. This specification does not mandate a particular strategy.

### 3.5. Connect to Selected Service

After selecting a service, the client establishes a connection:

1. **Parse the endpoint**: Extract host and port from the `apiUrl` field. For example, `"fudp://fapi.cid.cash:8500"` yields host `fapi.cid.cash` and port `8500`.
2. **Establish FUDP connection**: Initiate a new FUDP connection to the extracted host and port.
3. **Perform handshake**: Execute the HELLO/PING handshake as described in Section 3.2.
4. **Verify identity**: The server's FID (derived from the public key exchanged during HELLO) SHOULD match the FID that signed the on-chain service registration. If it does not match, the client SHOULD treat the connection as untrusted (see Section 8.2).
5. **Begin operation**: The client may now send FAPI requests to the connected service using the formats defined in FAPI1.

## 4. Direct Connection

Clients that already know a service's endpoint -- from local configuration, a previous session, or peer exchange -- MAY skip the bootstrap and discovery phases entirely.

The direct connection procedure is:

1. Establish a FUDP connection to the known endpoint.
2. Perform the HELLO/PING handshake (Section 3.2).
3. Optionally verify the server's FID against on-chain records.
4. Begin sending FAPI requests.

Direct connection is the RECOMMENDED approach for clients that have previously discovered and cached a provider's endpoint, as it avoids the latency of the bootstrap and query phases.

## 5. Custom Endpoint Connection

Clients MAY connect to arbitrary FAPI endpoints specified by the user or application. The endpoint is expressed as a FUDP URL:

```
fudp://{host}:{port}
```

Where:

- `{host}` is an IPv4 address, IPv6 address, or domain name.
- `{port}` is the FUDP port number. If omitted, the default port **8500** is used.

Examples:

| URL | Host | Port |
|---|---|---|
| `fudp://192.168.1.100:8500` | 192.168.1.100 | 8500 |
| `fudp://fapi.cid.cash:8500` | fapi.cid.cash | 8500 |
| `fudp://fapi.cid.cash` | fapi.cid.cash | 8500 (default) |
| `fudp://[::1]:8500` | ::1 | 8500 |

Implementations MUST parse the URL and extract host and port. If the port component is absent, the implementation MUST use port 8500.

## 6. Client Configuration

Conforming client implementations MUST support the following configuration parameters:

| Parameter | Type | Default Value | Description |
|---|---|---|---|
| bootstrapEndpoints | array of strings | See Section 3.1 | Ordered list of bootstrap FUDP URLs to attempt during initial discovery. |
| requestTimeout | integer (ms) | 30,000 ms | Default timeout for FAPI request/response cycles. |
| port | integer | 8500 | Default FUDP port when not specified in an endpoint URL. |
| healthCheckInterval | integer (ms) | 60,000 ms | Interval between periodic health checks on the active service connection. |
| maxRetries | integer | 3 | Maximum number of bootstrap endpoints to attempt before reporting failure. |

Implementations MAY support additional configuration parameters beyond those listed here.

## 7. Service Health

### 7.1. Health Check Request

Before relying on a discovered service, and periodically during use, clients SHOULD verify the service's health by issuing:

```json
{
  "api": "base.health"
}
```

This request targets the BASE component's `health` method, as defined in FAPI3.

### 7.2. Health Check Response

The health check response indicates the operational state of the service. The response body includes:

| Field | Type | Description |
|---|---|---|
| components | object | A map of component names to their states. Each value is a string: `"RUNNING"`, `"STOPPED"`, or `"ERROR"`. |
| dependencies | object | A map of external dependency names (e.g., `"elasticsearch"`, `"rpc"`) to their states: `"CONNECTED"` or `"DISCONNECTED"`. |
| fudpNode | object | FUDP node status, including `running` (boolean) and `connectedPeers` (integer). |

A service is considered **healthy** if:

- All components listed in the service's `types` declaration have state `"RUNNING"`.
- All dependencies have state `"CONNECTED"`.
- The `fudpNode.running` field is `true`.

A service that fails any of these conditions is considered **unhealthy**.

### 7.3. Failover

When a client detects that its current service is unhealthy (via a failed health check or a request timeout), it SHOULD:

1. Mark the current service as unavailable in its local cache.
2. Select an alternative service from the previously discovered provider list (Section 3.4).
3. Connect to the alternative service (Section 3.5).
4. Verify health of the alternative service before resuming operations.

If no alternative services are available, the client SHOULD re-execute the full discovery flow starting from bootstrap (Section 3.1).

Implementations SHOULD implement exponential backoff when retrying connections to previously failed endpoints.

## 8. Security Considerations

### 8.1. Bootstrap Trust

Bootstrap endpoints are trusted only for the purpose of initial network access. A compromised bootstrap node could return a manipulated list of service providers. To mitigate this risk:

- Clients SHOULD verify discovered service providers against on-chain records by cross-referencing the provider's FID with the blockchain data.
- Clients MAY use multiple bootstrap endpoints and compare results for consistency.
- Implementations SHOULD allow users to configure their own trusted bootstrap endpoints.

### 8.2. Service Authenticity

The on-chain service registration binds a FID to a FUDP endpoint. During the FUDP handshake, the server presents its public key, from which the client can derive the server's FID. The client SHOULD verify that this derived FID matches the FID that signed the on-chain service registration.

If the FIDs do not match, the client SHOULD:

1. Log a warning indicating a possible identity mismatch.
2. Refuse to send authenticated requests or credit-consuming operations to the service.
3. Optionally disconnect and attempt an alternative provider.

### 8.3. Man-in-the-Middle Protection

FUDP provides ECDH-based encryption after the handshake, preventing eavesdropping on application data. However, the initial HELLO exchange is unencrypted. An active attacker could intercept the HELLO and substitute their own public key.

To mitigate this:

- Clients SHOULD verify the server's public key (and derived FID) against known-good values obtained from on-chain records or prior trusted sessions.
- Implementations MAY maintain a local key-pinning database that associates endpoints with expected public keys.
- If a key mismatch is detected, the client MUST abort the connection and alert the user.

### 8.4. Endpoint Freshness

On-chain service registrations are persistent and may become stale if the operator takes the service offline without publishing a close transaction. Clients MUST NOT assume that an active on-chain registration implies a reachable service.

Mitigations:

- Always perform a health check (Section 7.1) before relying on a discovered service.
- Prefer services with recent `lastHeight` values, as they indicate more recent on-chain activity.
- Implement timeouts and failover (Section 7.3) to handle unreachable endpoints gracefully.

## 9. References

| Reference | Description |
|---|---|
| FAPI1V1 | Core Protocol -- wire format, request/response structures, status codes, API routing. |
| FAPI3V1 | Components -- component type definitions and method contracts for BASE, DISK, MAP, ROAD, and others. |
| FEIP | Freecash Extension Identity Protocol -- on-chain protocol for service declarations, identity, and metadata. |
| FUDP | Freecash UDP Protocol -- reliable encrypted transport providing peer identity, authentication, and multiplexed streams. |
| RFC 2119 | Key words for use in RFCs to indicate requirement levels. |
