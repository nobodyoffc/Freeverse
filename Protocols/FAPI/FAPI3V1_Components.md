# FAPI3V1_Components

|Field|Content|
|---|---|
|Title|Components|
|Type|FAPI|
|SN|3|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-03-28|
|PID||

## Contents

- [Abstract](#abstract)
- [Summary](#summary)
- [1. Introduction](#1-introduction)
- [2. Component Model](#2-component-model)
  - [2.1. Component Interface](#21-component-interface)
  - [2.2. Lifecycle States](#22-lifecycle-states)
  - [2.3. Component Type IDs](#23-component-type-ids)
  - [2.4. API Naming Convention](#24-api-naming-convention)
  - [2.5. Request Dispatch](#25-request-dispatch)
- [3. Component Registration](#3-component-registration)
  - [3.1. On-chain Service Declaration](#31-on-chain-service-declaration)
  - [3.2. Server Startup](#32-server-startup)
- [4. Built-in Component Specifications](#4-built-in-component-specifications)
- [5. Security Considerations](#5-security-considerations)
- [6. Versioning](#6-versioning)
- [7. References](#7-references)

---

## Abstract

FAPI3V1 defines the component model, lifecycle, registration mechanism, and type ID system of the FAPI (Freecash API) protocol series. FAPI uses a component-based architecture in which each component provides a set of related API methods. Components are independently deployable, registered on-chain via FEIP service declarations, and identified by type strings. This specification defines the component interface contract, lifecycle state machine, type ID convention, and the registration mechanism that governs how servers discover and load components.

## Summary

The FAPI component model separates concerns into independently deployable units. A server hosts one or more components, each identified by a type string (e.g., `BASE@No1_NrC7`). Every component implements a uniform interface: `getName()`, `getApiList()`, `handleRequest()`, and `getState()`. Components progress through a five-state lifecycle (CREATED, INITIALIZING, RUNNING, STOPPING, STOPPED). Clients discover available components through the on-chain service declaration, which lists the component type IDs and pricing parameters. This document specifies the interface contract, lifecycle rules, and registration mechanism. The API definitions for individual built-in components are provided in separate FAPI documents (FAPI11 through FAPI15).

## 1. Introduction

FAPI1 (Core Protocol) defines the wire format, request/response structures, and API routing mechanism. FAPI2 defines FCDSL, the query language used for data retrieval. This document (FAPI3) defines the component model that organizes server functionality into discrete, independently deployable units.

A FAPI server is not a monolithic service. It is composed of one or more components, each responsible for a distinct domain. A minimal server might host only the BASE component to serve blockchain queries. A full-featured server might host all five built-in components. The set of hosted components is declared on-chain through the service's `types` array, enabling clients to discover which capabilities a given server provides before connecting.

The API specifications for each individual built-in component are defined in separate documents: FAPI11 (BASE), FAPI12 (DISK), FAPI13 (DOCK), FAPI14 (MAP), and FAPI15 (ROAD).

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this document are to be interpreted as described in RFC 2119.

## 2. Component Model

### 2.1. Component Interface

Every FAPI component MUST implement the following interface:

| Method | Return Type | Description |
|---|---|---|
| `getName()` | string | Returns the component name in uppercase (e.g., `"BASE"`, `"DISK"`). This name is used for API routing as defined in FAPI1 Section 3.3. |
| `getApiList()` | list of strings | Returns the complete list of API endpoints supported by this component, in `component.method` format (e.g., `["base.getByIds", "base.search"]`). |
| `handleRequest(request, peerId)` | FapiResponse | Processes a FapiRequest and returns a FapiResponse. The `peerId` parameter is the authenticated FID of the requesting peer, established at the FUDP transport layer. |
| `getState()` | State | Returns the current lifecycle state of the component (see Section 2.2). |
| `initialize(server)` | void | Initializes the component with a reference to the hosting server. Called once during server startup. |
| `close(timeoutMs)` | void | Gracefully shuts down the component within the specified timeout. Called once during server shutdown. |

Components that handle binary data (file uploads, relay payloads) MUST additionally implement:

| Method | Return Type | Description |
|---|---|---|
| `handleUnifiedRequest(request, binaryData, peerId)` | UnifiedResponse | Processes a request that includes binary data. Returns a response that MAY include binary data. |
| `returnsBinaryData(method)` | boolean | Returns `true` if the specified method produces a response containing binary data. |

### 2.2. Lifecycle States

Components progress through the following states:

| State | Description |
|---|---|
| CREATED | The component object has been instantiated but `initialize()` has not been called. No resources are allocated. |
| INITIALIZING | `initialize()` has been called. The component is loading configuration, connecting to backend services (Elasticsearch, RPC nodes, etc.), and preparing internal state. |
| RUNNING | Initialization completed successfully. The component is accepting and processing requests. |
| STOPPING | `close()` has been called. The component is completing in-flight requests, flushing state, and releasing resources. New requests MUST be rejected with code 503. |
| STOPPED | All resources have been released. The component MUST NOT process any further requests. |

The valid state transitions are:

```
CREATED --> INITIALIZING --> RUNNING --> STOPPING --> STOPPED
                |
                +---> STOPPED  (on initialization failure)
```

A component MUST NOT transition backward (e.g., from STOPPED to RUNNING). If initialization fails, the component transitions directly from INITIALIZING to STOPPED.

Servers MUST check the component state before dispatching requests. If a component is not in the RUNNING state, the server MUST return code 503 (SERVICE_UNAVAILABLE) without invoking `handleRequest()`.

### 2.3. Component Type IDs

Each built-in component is identified by a type string that combines the component name with an author identifier:

| Component | Type ID | Description |
|---|---|---|
| BASE | `BASE@No1_NrC7` | Blockchain data queries and transaction operations |
| DISK | `DISK@No1_NrC7` | Decentralized file storage with expiration support |
| DOCK | `DOCK@No1_NrC7` | Store-and-forward messaging |
| MAP | `MAP@No1_NrC7` | FID-to-network-address mapping |
| ROAD | `ROAD@No1_NrC7` | Data relay service |

The format is `{NAME}@{AUTHOR_FID_SUFFIX}`. Third-party components MAY define their own type IDs using the same format with a different author suffix.

### 2.4. API Naming Convention

All API endpoints follow the `component.method` naming convention as defined in FAPI1 Section 3.3. The component name is the lowercase form of the component's `getName()` return value. Examples:

- `base.search` -- the `search` method on the BASE component
- `disk.put` -- the `put` method on the DISK component
- `map.register` -- the `register` method on the MAP component

Method names are case-sensitive. Component names are case-insensitive during routing but SHOULD be written in lowercase in client code.

### 2.5. Request Dispatch

When a server receives a FapiRequest, it follows the routing procedure defined in FAPI1 Section 3.3 to resolve the target component. The server then checks the component state (Section 2.2) and dispatches to the appropriate handler:

1. If `dataSize > 0` in the request or `returnsBinaryData(method)` returns true, invoke `handleUnifiedRequest(request, binaryData, peerId)`.
2. Otherwise, invoke `handleRequest(request, peerId)`.

Components MUST NOT make assumptions about which thread or executor invokes the handler. Implementations SHOULD be thread-safe.

## 3. Component Registration

### 3.1. On-chain Service Declaration

Components are registered on-chain through FEIP service declarations (see FEIP5, Service protocol). The service object includes the following fields relevant to component registration:

| Field | Type | Description |
|---|---|---|
| `types` | array of strings | Component type IDs hosted by this service (e.g., `["BASE@No1_NrC7", "DISK@No1_NrC7"]`). |
| `apiUrl` | string | FUDP endpoint address (e.g., `"fudp://host:8500"`). Clients use this to establish a FUDP connection. |
| `pricePerKB` | string | Default price per kilobyte in FCH. Applied when direction-specific pricing is not set. |
| `pricePerKBIn` | string | Price per kilobyte for ingress (data sent to the server) in FCH. |
| `pricePerKBOut` | string | Price per kilobyte for egress (data sent from the server) in FCH. |
| `minCredit` | string | Minimum credit balance in FCH required to use the service. |

Clients discover available services by querying the blockchain (via the BASE component of another server or directly from indexed data). The `types` array tells the client which components a server supports before establishing a connection.

### 3.2. Server Startup

At startup, the server performs the following steps to load components:

1. Read the service's `types` array from the on-chain declaration or local configuration.
2. For each type ID, resolve it to a component class via the component registry.
3. Instantiate each component (state transitions to CREATED).
4. Call `initialize(server)` on each component in dependency order. Components that depend on other components (e.g., ROAD depends on MAP) MUST be initialized after their dependencies.
5. If any required component fails to initialize, the server SHOULD log the failure and MAY continue operating with the remaining components in a degraded mode, or MAY abort startup entirely.
6. Once all components are initialized, the server begins accepting FUDP connections.

## 4. Built-in Component Specifications

The built-in components are specified in separate FAPI documents:

| SN | Component | Type ID | Document |
|---|---|---|---|
| 11 | BASE | BASE@No1_NrC7 | [FAPI11V1_BASE](FAPI11V1_BASE.md) |
| 12 | DISK | DISK@No1_NrC7 | [FAPI12V1_DISK](FAPI12V1_DISK.md) |
| 13 | DOCK | DOCK@No1_NrC7 | [FAPI13V1_DOCK](FAPI13V1_DOCK.md) |
| 14 | MAP | MAP@No1_NrC7 | [FAPI14V1_MAP](FAPI14V1_MAP.md) |
| 15 | ROAD | ROAD@No1_NrC7 | [FAPI15V1_ROAD](FAPI15V1_ROAD.md) |

New components MAY be defined in future FAPI documents with serial numbers 16 and above. Third-party components MAY be specified outside this series, using the `{NAME}@{AUTHOR}` type ID convention defined in Section 2.3.

## 5. Security Considerations

1. **Authentication**: All FAPI requests are transported over FUDP, which authenticates peers using secp256k1 public keys during the handshake. The `peerId` passed to component handlers is the authenticated FID. Components MUST NOT implement their own authentication layer.

2. **Authorization**: Components MAY implement authorization checks based on the `peerId`. For example, a component may verify that the caller is authorized to perform the requested operation on the specified resource. Authorization logic is component-specific and defined in each component's individual specification.

3. **Balance enforcement**: Components that charge fees MUST verify the sender's balance before processing the request. If the balance is insufficient, the component MUST return code 402 (PAYMENT_REQUIRED) without performing the operation.

## 6. Versioning

| Version | Date | Changes |
|---|---|---|
| 1 | 2026-03-28 | Initial specification. Defines the component model, lifecycle, registration mechanism, and type ID system. Individual component APIs specified in FAPI11-FAPI15. |

## 7. References

- **FAPI1**: Core Protocol. Defines the wire format (UnifiedCodec), request/response structures, status codes, and API routing mechanism referenced throughout this document.
- **FAPI2**: FCDSL (Freecash Domain Specific Language). Defines the query syntax used in the `fcdsl` field for all query-category methods.
- **FAPI11-FAPI15**: Built-in component specifications. Define the API surface and behavior of BASE, DISK, DOCK, MAP, and ROAD respectively.
- **FUDP**: Freecash UDP Protocol. Provides the transport layer, including peer identity, authentication, encryption, reliable delivery, and stream multiplexing.
- **FEIP5**: Service protocol. Defines the on-chain service declaration format used for component registration (Section 3.1).
- **RFC 2119**: Key words for use in RFCs to indicate requirement levels.
