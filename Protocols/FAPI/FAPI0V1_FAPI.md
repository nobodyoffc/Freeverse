# FAPI0V1_FAPI

## Contents

[Summary](#summary)

[Abstract](#abstract)

[What is FAPI](#what-is-fapi)

[Scope Boundaries](#scope-boundaries)

[General Rules](#general-rules)

[Protocol Document Structure](#protocol-document-structure)

[FAPI List](#fapi-list)

---

## Summary

|Field|Content|
|---|---|
|Title|FAPI|
|Type|FAPI|
|SN|0|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-03-28|
|PID||

## Abstract

FAPI (Freeverse API Protocol) defines a decentralized API service framework for the Freeverse ecosystem. Built on top of FUDP encrypted transport, FAPI provides blockchain data querying, file storage, messaging, address mapping, data relay, and other application-level services through a component-based architecture. Clients interact with FAPI servers using JSON-based request/response messages over FUDP streams, with optional binary data attachments, and pay for services via FCH micropayments.

This document (FAPI0) defines the foundational rules shared by all FAPI protocols.

## What is FAPI

### Naming

FAPI stands for **Freeverse API Protocol**.

- **F** - Freeverse: FAPI serves the Freeverse ecosystem.
- **API** - Application Programming Interface: FAPI defines structured interfaces for querying data, storing files, relaying messages, and other application-level services.
- **P** - Protocol: Each FAPI document defines a formal specification for one aspect of the decentralized API service layer.

### Identification

Each FAPI protocol is identified by its serial number (`sn`) and version (`ver`). The naming convention is:

```
FAPI{sn}V{ver}_{Name}
```

For example: `FAPI1V1_CoreProtocol` refers to the core wire format and request/response specification, serial number 1, version 1.

### The Need for FAPI

The Freeverse ecosystem requires a standardized way for clients to consume services -- querying blockchain data, storing and retrieving files, sending messages, mapping addresses, and relaying data between peers. Without a common application-level protocol, each service provider would define its own request format, error codes, routing scheme, and billing model, leading to fragmentation and poor interoperability.

The existing protocol series do not address application-level service interfaces:

- **FBP** defines blockchain consensus rules (block validation, mining).
- **FEIP** defines on-chain application data formats (OP_RETURN structures).
- **FVEP** defines ecosystem concepts (entities, identities, time, currency).
- **FTSP** defines technical primitives (algorithms, encoding, cryptographic procedures).
- **FUDP** defines peer-to-peer encrypted transport over UDP.
- **FBSP** defines business-level rules (commercial services, marketplace matching).

FUDP provides the encrypted transport channel, but it does not define what messages applications send over that channel, how API endpoints are named, how queries are expressed, or how clients pay for services. FAPI fills this gap by specifying a complete application service framework on top of FUDP.

### Relationship with Other Protocols

FAPI depends on and references several other Freeverse protocol series:

- **FUDP** -- Provides the encrypted, reliable transport layer. All FAPI communication is carried over FUDP streams. Authentication, replay protection, and connection management are handled entirely by FUDP; FAPI messages do not include signatures, nonces, or other authentication data.
- **FTSP** -- Defines the cryptographic operations (e.g., ECDH, AES-GCM, HKDF) that FUDP uses to secure the transport. FAPI does not reference FTSP directly but inherits its security guarantees through FUDP.
- **FEIP** -- Defines on-chain service registration. FAPI servers publish their service metadata (endpoints, capabilities, pricing) on-chain using FEIP protocols. Clients discover FAPI servers by reading these on-chain records.
- **FVEP** -- Defines the entity and identity concepts (FIDs, public keys) used to identify clients and servers.
- **FBSP** -- Defines business rules that govern commercial service interactions. FAPI provides the technical mechanism for billing (micropayments); FBSP defines the business policies.

### Position in the Protocol Stack

|Layer|Protocol Series|Scope|
|---|---|---|
|Blockchain Consensus|FBP|Block validation, transaction rules, mining|
|On-chain Application|FEIP|Structured data in OP_RETURN|
|Ecosystem Foundation|FVEP|Entities, IDs, time, currency (concepts)|
|Technical Standard|FTSP|Algorithms, encoding, transport primitives|
|Transport|FUDP|P2P encrypted transport over UDP|
|**Application Service**|**FAPI**|**Decentralized API services over FUDP**|
|Business Standard|FBSP|Commercial services, marketplace rules|

FAPI sits above the transport layer (FUDP) and below the business standard layer (FBSP). It consumes FUDP-provided encrypted streams and exposes a structured, extensible API framework for higher-layer applications and business services.

## Scope Boundaries

### What Belongs in FAPI

FAPI protocols define the behavior and formats of the decentralized API service layer:

1. **Wire Format** -- The binary framing of FAPI messages over FUDP streams, including the header length prefix, JSON encoding, and binary data attachments.
2. **Request/Response Structure** -- The JSON schema for API requests (method routing, parameters, pagination) and responses (status codes, result data, error messages).
3. **API Routing** -- The naming convention for API endpoints (`component.method`), method dispatch, and component registration.
4. **FCDSL** -- Freeverse Common Data Service Language for expressing structured queries against server-side data.
5. **Component Model** -- The component-based architecture for extending FAPI with new service capabilities (BASE, DISK, DOCK, MAP, ROAD, and future components).
6. **Billing and Economics** -- The micropayment model for per-request billing, balance management, pricing negotiation, and settlement.
7. **Service Discovery** -- How FAPI servers register on-chain and how clients discover and connect to them.

### What Does NOT Belong in FAPI

- **Transport mechanics** -- FUDP (e.g., packet framing, retransmission, congestion control, stream multiplexing).
- **Authentication and replay protection** -- FUDP (e.g., ECDH handshake, session keys, nonce management).
- **Cryptographic algorithm internals** -- FTSP (e.g., how AES-GCM encryption works).
- **Identity and entity definitions** -- FVEP (e.g., what an FID is and how it is derived).
- **On-chain data structures** -- FEIP (e.g., OP_RETURN formats for service registration are defined by FEIP; FAPI only references them).
- **Blockchain consensus rules** -- FBP (e.g., block validation, transaction rules).
- **Business policies and marketplace rules** -- FBSP (e.g., swap matching algorithms, service-level agreements).

## General Rules

### 1. Transport Requirement

All FAPI communication MUST be transported over FUDP streams. FAPI messages MUST NOT be sent over raw UDP, TCP, HTTP, or any other transport. The FUDP layer provides encryption, authentication, reliability, and stream multiplexing.

### 2. No FAPI-Level Authentication

Authentication is handled entirely by the FUDP layer. FAPI messages MUST NOT include signatures, nonces, authentication tokens, or other credentials. The identity of the remote peer is established by FUDP during the connection handshake using secp256k1 public key cryptography. FAPI servers identify clients by the FID associated with the FUDP connection.

### 3. Wire Format

FAPI messages use the following wire format on a FUDP stream:

1. A **4-byte big-endian unsigned integer** indicating the length of the JSON header in bytes.
2. The **JSON header** encoded as UTF-8, containing the request or response fields.
3. An **optional binary data section** immediately following the JSON header, extending to the end of the stream message.

Implementations MUST use this exact framing. The JSON header MUST be valid UTF-8 encoded JSON.

### 4. API Naming Convention

API endpoints follow the format `component.method`, where:

- `component` identifies the service component (e.g., `base`, `disk`, `map`).
- `method` identifies the specific operation (e.g., `ping`, `put`, `get`).

Component names are **case-insensitive** for routing purposes. Method names are **case-insensitive** for routing purposes. Implementations SHOULD normalize to lowercase internally.

### 5. Status Codes

FAPI status codes follow HTTP conventions:

|Code|Meaning|
|---|---|
|0|Success|
|1|Other/general information|
|4xx|Client error (malformed request, missing parameter, insufficient balance, etc.)|
|5xx|Server error (internal failure, service unavailable, etc.)|

Specific status codes are defined in FAPI1 (Core Protocol).

### 6. Monetary Values

All monetary values in FAPI messages are expressed in **satoshi** (1 FCH = 100,000,000 satoshi). Implementations MUST use integer arithmetic for monetary calculations. Floating-point representations of monetary values are NOT permitted in FAPI messages.

### 7. JSON Encoding

FAPI JSON messages MUST conform to [RFC 8259](https://www.rfc-editor.org/rfc/rfc8259). Field names are case-sensitive. Implementations SHOULD ignore unrecognized fields for forward compatibility. Implementations MUST NOT reject a message solely because it contains unrecognized fields.

### 8. Component Architecture

FAPI uses a component-based extensible architecture. Each component provides a set of related API methods. The built-in components are listed below; each has its own specification document (FAPI11-15):

|Component|Spec|Scope|
|---|---|---|
|BASE|FAPI11|Core server operations: ping, session info, balance query, recharge|
|DISK|FAPI12|File storage and retrieval|
|DOCK|FAPI13|Messaging and relay services|
|MAP|FAPI14|Address and identity mapping|
|ROAD|FAPI15|Data relay and forwarding|

Additional components MAY be defined by future FAPI protocols or by individual server implementations. Custom components MUST NOT use names that conflict with the built-in component names listed above.

> **Note:** SN 6-10 are reserved for future framework-level documents. SN 16+ are reserved for future components.

### 9. RFC 2119 Keywords

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in FAPI documents are to be interpreted as described in [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).

### 10. Reference Implementation

The reference implementation of FAPI is in FC-JDK (Java), located under the `fapi` package. The FC-JDK implementation is authoritative for resolving ambiguities in the specification.

### 11. Protocol Versioning

The current FAPI protocol version is **1**. The version number is carried in the service metadata published on-chain. Servers MUST advertise their supported FAPI version. Clients SHOULD verify version compatibility before issuing requests. Future versions that introduce wire-incompatible changes MUST increment the version number.

### 12. Language Agnosticism

FAPI is a language-agnostic protocol. Any programming language that can produce and consume UTF-8 JSON and communicate over FUDP streams can implement a conformant FAPI client or server. The specification defines wire formats and behavior, not implementation details.

## Protocol Document Structure

Each FAPI protocol document SHOULD follow this structure:

```
# FAPI{sn}V{ver}_{Name}

## Contents
## Summary               - Identification table (Title, SN, Ver, Status, Author, PID)
## Abstract               - 2-3 sentence description
## Motivation             - Why this specification is needed
## Specification
   ### Wire Format        - Message layouts, field definitions
   ### Procedures         - Step-by-step protocol behavior
   ### API Definitions    - Request/response schemas for each endpoint
   ### Error Handling     - Error conditions, error codes, and responses
## Security Considerations
## Versioning             - Version history table
## Related Protocols
## Reference Implementation
```

### Summary Table Fields

|Field|Description|
|---|---|
|Title|Protocol name|
|Type|Fixed: "FAPI"|
|SN|Serial number|
|Ver|Current version number|
|Status|One of: Draft, Active, Deprecated, Replaced|
|Author|Author FID or name|
|Created|Creation date|
|PID|Protocol ID (txid of the on-chain publish transaction, if published)|

## FAPI List

|SN|Name|Scope|
|---|---|---|
|0|FAPI|This document. Foundational rules for the FAPI series.|
|1|[Core Protocol](FAPI1V1_CoreProtocol.md)|Wire format (UnifiedCodec), request/response structure, status codes, API routing.|
|2|[FCDSL](FAPI2V1_FCDSL.md)|Freeverse Common Data Service Language specification.|
|3|[Components](FAPI3V1_Components.md)|Component model, lifecycle, type IDs, registration. Individual component specs are in FAPI11+.|
|4|[Economics](FAPI4V1_Economics.md)|Billing model, balance management, pricing, settlement, recharge.|
|5|[Service Discovery](FAPI5V1_ServiceDiscovery.md)|On-chain service registration, discovery flow, default endpoints.|
|11|[BASE](FAPI11V1_BASE.md)|Blockchain data queries and transaction operations.|
|12|[DISK](FAPI12V1_DISK.md)|Decentralized file storage with content addressing and expiration.|
|13|[DOCK](FAPI13V1_DOCK.md)|Store-and-forward messaging with TTL-based expiration.|
|14|[MAP](FAPI14V1_MAP.md)|NAT-friendly FID-to-network-address mapping.|
|15|[ROAD](FAPI15V1_ROAD.md)|Data relay service for indirect peer-to-peer communication.|
