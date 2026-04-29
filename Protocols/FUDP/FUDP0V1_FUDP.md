# FUDP0V1_FUDP

## Contents

[Summary](#summary)

[Abstract](#abstract)

[What is FUDP](#what-is-fudp)

[Scope Boundaries](#scope-boundaries)

[General Rules](#general-rules)

[Protocol Document Structure](#protocol-document-structure)

[FUDP List](#fudp-list)

---

## Summary

|Field|Content|
|---|---|
|Title|FUDP|
|Type|FUDP|
|SN|0|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-03-28|
|PID||

## Abstract

FUDP (Freeverse UDP Protocol) defines a UDP-based encrypted transport protocol for peer-to-peer communication within the Freeverse ecosystem. Inspired by QUIC but simplified for blockchain-oriented peer-to-peer use cases, FUDP provides reliable, multiplexed, and encrypted transport using identity-based cryptography (secp256k1 public keys as peer identities). All data packets are encrypted via AsyTwoWay (ECDH) -- plaintext data transmission is not permitted.

This document (FUDP0) defines the foundational rules shared by all FUDP protocols.

## What is FUDP

### Naming

FUDP stands for **Freeverse UDP Protocol**.

- **F** - Freeverse: FUDP serves the Freeverse ecosystem.
- **UDP** - User Datagram Protocol: FUDP builds reliable, encrypted transport on top of UDP.
- **P** - Protocol: Each FUDP document defines a formal specification for one aspect of the transport layer.

### Identification

Each FUDP protocol is identified by its serial number (`sn`) and version (`ver`). The naming convention is:

```
FUDP{sn}V{ver}_{Name}
```

For example: `FUDP1V1_CoreTransport` refers to the core transport specification (packet format, header, frame types, connection lifecycle), serial number 1, version 1.

### The Need for FUDP

The Freeverse ecosystem requires a transport protocol that meets several demands simultaneously:

1. **Identity-based security.** Peers in the Freeverse network are identified by secp256k1 public keys (FIDs). The transport layer should authenticate peers using these existing identities rather than relying on external certificate authorities.

2. **Simplified handshake.** Unlike TLS or full QUIC, FUDP uses only AsyTwoWay (ECDH) encryption. There is no symmetric key negotiation phase in the handshake -- the shared secret is derived directly from the peers' secp256k1 key pairs via ECDH and HKDF, as specified by FTSP11 (Ecc256K1AesGcm256) and FTSP13 (HKDF).

3. **UDP-native design.** TCP-based protocols suffer from head-of-line blocking at the transport layer. FUDP operates over UDP datagrams, enabling independent stream multiplexing, faster connection establishment, and better performance over lossy networks.

4. **Peer-to-peer orientation.** FUDP is designed for symmetric peer-to-peer communication rather than the client-server model assumed by QUIC. Both sides of a connection are equal participants identified by their public keys.

5. **Language-agnostic specification.** The FUDP series defines the wire format and protocol behavior precisely enough that any programming language can implement a compatible node. The reference implementation is in Java (FC-JDK), but conformant implementations in other languages are expected.

The existing Freeverse protocol series do not address transport:

- **FBP** defines blockchain consensus rules.
- **FEIP** defines on-chain application data formats.
- **FVEP** defines ecosystem concepts (entities, identities, time, currency).
- **FTSP** defines technical primitives (algorithms, encoding, cryptographic procedures).
- **FBSP** defines business-level service rules.

FUDP fills the transport gap by specifying how peers establish connections, exchange encrypted data, manage streams, handle packet loss, and defend against network-level attacks.

### Relationship with Other Protocols

FUDP depends on and references several other Freeverse protocol series:

- **FTSP11 (Ecc256K1AesGcm256)** -- Defines the ECDH key agreement and AES-256-GCM encryption used by FUDP for all data packet encryption.
- **FTSP13 (HKDF)** -- Defines the key derivation function used to derive session keys from the ECDH shared secret.
- **FVEP** -- Defines the entity and identity concepts (FIDs, public keys) that FUDP uses for peer identification and authentication.

FUDP does not define new cryptographic algorithms. It composes the primitives specified by FTSP into a transport protocol.

### Position in the Protocol Stack

|Layer|Protocol Series|Scope|
|---|---|---|
|Blockchain Consensus|FBP|Block validation, transaction rules, mining|
|On-chain Application|FEIP|Structured data in OP_RETURN|
|Ecosystem Foundation|FVEP|Entities, IDs, time, currency (concepts)|
|Technical Standard|FTSP|Algorithms, encoding, transport primitives|
|**Transport**|**FUDP**|**P2P encrypted transport over UDP**|
|Business Standard|FBSP|Commercial services, marketplace rules|

FUDP sits between the technical primitives (FTSP) and the business-level services (FBSP). It consumes FTSP-defined cryptographic procedures and provides reliable encrypted transport for higher-layer protocols and applications.

## Scope Boundaries

### What Belongs in FUDP

FUDP protocols define the behavior and wire formats of the UDP-based transport layer:

1. **Packet Format** -- Header structure, frame encoding, and packet assembly rules.
2. **Connection Lifecycle** -- Connection establishment, migration, and termination procedures.
3. **Stream Multiplexing** -- Stream creation, data transfer, flow control, and teardown within a connection.
4. **Reliability** -- Acknowledgment processing, loss detection, retransmission, and congestion control.
5. **Transport Security** -- Handshake crypto integration (referencing FTSP), replay protection, and session epoch management.
6. **DDoS Defense** -- Proof-of-work challenges, IP verification, and rate limiting at the transport layer.
7. **Application Messages** -- Standardized message envelope and Java reference message types carried over FUDP streams (request/response, notify, ping/pong, error).

### What Does NOT Belong in FUDP

- **Cryptographic algorithm internals** -- FTSP (e.g., how AES-GCM encryption works is defined in FTSP12; FUDP only references it).
- **Identity and entity definitions** -- FVEP (e.g., what an FID is and how it is derived).
- **On-chain data structures** -- FEIP (e.g., OP_RETURN formats).
- **Blockchain consensus rules** -- FBP (e.g., block validation).
- **Business logic and service rules** -- FBSP (e.g., API pricing, swap matching).
- **Application-layer semantics beyond transport** -- Higher-layer protocols built on top of FUDP define their own message semantics.

## General Rules

### 1. Byte Order

All multi-byte integers in FUDP protocols are encoded in **big-endian** (network byte order) unless a specific protocol document states otherwise.

### 2. Variable-Length Integer Encoding

FUDP uses QUIC-style variable-length integer encoding. The two most significant bits of the first byte indicate the encoding length:

|Prefix (2 bits)|Encoding Length|Usable Bits|Range|
|---|---|---|---|
|00|1 byte|6|0 -- 63|
|01|2 bytes|14|0 -- 16383|
|10|4 bytes|30|0 -- 1073741823|
|11|8 bytes|62|0 -- 4611686018427387903|

The prefix bits are part of the first byte. The remaining bits (after removing the prefix) form the integer value in big-endian order.

### 3. Conformance Requirements

Implementations MUST support the following to be considered FUDP-conformant:

- **FUDP1 (Core Transport)** -- Full connection lifecycle, packet format, and frame processing.
- **FUDP2 (Streams)** -- Stream multiplexing and flow control at both stream and connection level.
- **FUDP3 (Loss & Congestion)** -- ACK processing, loss detection, retransmission, and congestion control.

### 4. Mandatory Encryption

Security (FUDP4) is **REQUIRED** for all connections. Plaintext data packets are not permitted on the wire. Every data packet MUST be encrypted using the session keys established during the handshake.

### 5. Optional Components

- **FUDP5 (DDoS Defense)** is OPTIONAL but RECOMMENDED for internet-facing nodes.
- **FUDP6 (Messages)** is OPTIONAL. Implementations MAY define their own application-layer message formats on top of FUDP streams.

### 6. RFC 2119 Keywords

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in FUDP documents are to be interpreted as described in [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).

### 7. Reference Implementation

The reference implementation of FUDP is in FC-JDK (Java), located under the `fudp` package. The FC-JDK implementation is authoritative for resolving ambiguities in the specification.

### 8. Protocol Versioning

The FUDP protocol version is carried in the Version field of the packet header. The current version is **1**. Implementations MUST reject encrypted DATA and ACK packets with unrecognized version numbers. The Java reference implementation rejects them by silently dropping the packet. Plaintext CONTROL packets are version-agnostic in v1. Future versions that change wire-incompatible behavior MUST increment the version number.

### 9. Identity-Based Addressing

Peers are identified by their secp256k1 compressed public keys (33 bytes). The corresponding FID (Freecash Identity) serves as a human-readable alias. Connection authentication is performed by proving possession of the private key corresponding to the advertised public key.

## Protocol Document Structure

Each FUDP protocol document SHOULD follow this structure:

```
# FUDP{sn}V{ver}_{Name}

## Contents
## Summary               - Identification table (Title, SN, Ver, Status, Author, PID)
## Abstract               - 2-3 sentence description
## Motivation             - Why this specification is needed
## Specification
   ### Wire Format        - Packet/frame layouts, field definitions
   ### Procedures         - Step-by-step protocol behavior
   ### State Machines     - Connection/stream state transitions (where applicable)
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
|Type|Fixed: "FUDP"|
|SN|Serial number|
|Ver|Current version number|
|Status|One of: Draft, Active, Deprecated, Replaced|
|Author|Author FID or name|
|Created|Creation date|
|PID|Protocol ID (txid of the on-chain publish transaction, if published)|

## FUDP List

|SN|Name|Scope|
|---|---|---|
|0|FUDP|This document. Foundational rules for the FUDP series.|
|1|Core Transport|Packet format, header structure, frame types, varint encoding, connection lifecycle.|
|2|Streams|Stream multiplexing, flow control (stream and connection level), reassembly.|
|3|Loss & Congestion|ACK processing, loss detection, retransmission, CUBIC congestion control, RTT estimation.|
|4|Security|Handshake crypto (referencing FTSP11), replay protection, session epoch management.|
|5|DDoS Defense|Proof-of-work challenges, IP verification, rate limiting.|
|6|Messages|Application-layer message envelope and reference message types (request/response, notify, ping/pong, error).|
