# FUDP1V1_CoreTransport

## Contents

[Summary](#summary)

[Abstract](#abstract)

[Notation and Conventions](#notation-and-conventions)

[Packet Header](#packet-header)

[Encrypted Payload Structure](#encrypted-payload-structure)

[Control Packets](#control-packets)

[Variable-Length Integer Encoding](#variable-length-integer-encoding)

[Frame Types](#frame-types)

[Connection Lifecycle](#connection-lifecycle)

[Default Protocol Parameters](#default-protocol-parameters)

[Security Considerations](#security-considerations)

[Versioning](#versioning)

[Related Protocols](#related-protocols)

---

## Summary

|Field|Content|
|---|---|
|Title|Core Transport|
|Type|FUDP|
|SN|1|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-03-28|
|PID||

## Abstract

FUDP (Freeverse UDP Protocol) Core Transport defines the wire format, frame types, variable-length integer encoding, and connection lifecycle for peer-to-peer communication over UDP within the Freeverse ecosystem. All encrypted payloads use the ECDH key exchange and AES-256-GCM authenticated encryption profile specified in [FTSP11V1_Ecc256K1AesGcm256](../FTSP/FTSP11V1_Ecc256K1AesGcm256.md), with key derivation per [FTSP13V1_HKDF](../FTSP/FTSP13V1_HKDF.md).

This specification is language-agnostic. Wire formats are described in binary and hex diagrams, state transitions as tables, and algorithms as pseudocode.

## Notation and Conventions

- All multi-byte integer fields are encoded in **big-endian** (network byte order) unless otherwise stated.
- The keywords "MUST", "MUST NOT", "SHOULD", "SHOULD NOT", and "MAY" are interpreted as described in RFC 2119.
- `(varint)` denotes a variable-length integer as defined in [Variable-Length Integer Encoding](#variable-length-integer-encoding).
- Byte offsets are zero-indexed.
- All times are in milliseconds since the Unix epoch (1970-01-01T00:00:00Z) unless otherwise stated.

---

## Packet Header

Every FUDP packet begins with a 21-byte plaintext header. This header is never encrypted and MUST be present in all packet types.

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|    Flags      |                  Version                      |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|               |                                               |
+-+-+-+-+-+-+-+-+         Connection ID (64 bits)               |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|               |                                               |
+-+-+-+-+-+-+-+-+         Packet Number (64 bits)               |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|               |
+-+-+-+-+-+-+-+-+
```

**Total header size:** 1 (Flags) + 4 (Version) + 8 (Connection ID) + 8 (Packet Number) = **21 bytes**.

### Field Definitions

#### Flags (byte 0)

| Bits | Name | Description |
|---|---|---|
| 0-1 | Packet Type | Determines the packet category (see table below) |
| 2-3 | Reserved | Reserved for future use; MUST be set to 0 on transmission |
| 4 | FIN | Connection close marker; indicates this packet is part of connection termination |
| 5 | HAS_TIMESTAMP | `1` if the encrypted plaintext starts with an 8-byte timestamp |
| 6 | HAS_EPOCH | `1` if the encrypted plaintext includes an 8-byte session epoch |
| 7 | Reserved | MUST be set to 0 on transmission |

**Packet Type values (bits 0-1):**

| Value | Name | Description |
|---|---|---|
| 0x00 | DATA | Carries encrypted frames containing application data and control frames |
| 0x01 | ACK | Carries encrypted ACK frames |
| 0x02 | CONTROL | Plaintext control messages used during handshake |
| 0x03 | ERROR | Error notification |

#### Version (bytes 1-4)

A 32-bit unsigned integer identifying the protocol version. The current version is **1** (`0x00000001`).

Receivers MUST reject encrypted DATA/ACK packets with an unrecognized version. Plaintext CONTROL packets are version-agnostic in v1.

#### Connection ID (bytes 5-12)

A 64-bit random value that identifies the sender's local connection record. Each endpoint allocates the value it writes into outbound DATA and ACK packets.

Implementations MUST use a cryptographically secure random number generator or equivalent secure randomness for locally allocated Connection IDs.

**Connection ID is the PRIMARY routing key for inbound packets, not source address.** After decryption, an implementation MUST first look up the local connection record by the remote peer's Connection ID (the value the peer itself stamped in the packet, learned from the peer's earlier packets), and only fall back to `(peer identity, source address)` lookup when that remote Connection ID has not been seen before. Address is treated purely as the current network path for an already-identified connection, not as part of the connection's identity. See [Path Migration](#path-migration) for the rationale and required behavior when a peer's source address changes mid-connection.

#### Packet Number (bytes 13-20)

A 64-bit unsigned integer that increases monotonically within each connection, starting from 0. Each endpoint maintains its own packet number space. Packet numbers MUST NOT be reused within a connection.

---

## Encrypted Payload Structure

DATA packets (type `0x00`) and ACK packets (type `0x01`) carry an encrypted payload immediately following the 21-byte header. The encryption uses the ECDH + AES-256-GCM profile defined in [FTSP11V1_Ecc256K1AesGcm256](../FTSP/FTSP11V1_Ecc256K1AesGcm256.md).

### Plaintext Layout (before encryption)

The plaintext that is encrypted into the payload has the following structure:

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                      Timestamp (64 bits)                      |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    Session Epoch (64 bits)                     |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                   Frames (variable length)                    |
|                             ...                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

| Offset | Length | Field | Presence | Description |
|---|---|---|---|---|
| 0 | 8 bytes | Timestamp | If `HAS_TIMESTAMP=1` | 64-bit unsigned integer; milliseconds since Unix epoch at packet creation |
| 8 or 0 | 8 bytes | Session Epoch | If `HAS_EPOCH=1` | 64-bit random value generated once at node startup; used for replay/restart detection |
| variable | variable | Frames | Always | One or more concatenated frames as defined in [Frame Types](#frame-types) |

In the current implementation, `HAS_TIMESTAMP` MAY be 0 for ACK-only packets and `HAS_EPOCH` MAY be 0 after epoch confirmation to reduce overhead. A packet carrying a DATAGRAM frame MUST set `HAS_TIMESTAMP`: it carries application data, so it gets the same replay protection as any ack-eliciting packet, although it is not ack-eliciting itself.

### Encrypted Encoding

The plaintext is encrypted per FTSP11 (Ecc256K1AesGcm256). The resulting wire-format payload is a `CryptoDataByte` bundle containing:

- Ciphertext (the encrypted plaintext described above)
- Initialization vector (IV)
- Sender's compressed public key (33 bytes, secp256k1)
- Algorithm identifier

The shared secret for encryption is derived via ECDH between the sender's private key and the receiver's public key, with key derivation per [FTSP13V1_HKDF](../FTSP/FTSP13V1_HKDF.md).

### Replay Protection

The combination of **Timestamp** and **Session Epoch** provides replay protection:

- The **Session Epoch** changes each time a node restarts, invalidating all packets from previous sessions.
- The **Timestamp** allows receivers to reject packets that are too old.
- Receivers SHOULD maintain a window of recently seen Packet Numbers per connection and reject duplicates.

---

## Control Packets

Control packets (Packet Type `0x02`) carry **plaintext** payloads. They are used exclusively during the pre-encryption handshake phase before a shared secret has been established.

The maximum payload size for a control packet is **256 bytes**. Implementations MUST discard control packets with payloads exceeding this limit.

### Control Payload Format

```
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| Control Type  |     Payload (variable)  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

| Offset | Length | Field | Description |
|---|---|---|---|
| 0 | 1 byte | Control Type | Identifies the control message type |
| 1 | variable | Payload | Type-specific payload data |

### Control Types

| Value | Name | Payload Length | Description |
|---|---|---|---|
| 0x01 | HELLO | 0 bytes | Requests the responder's public key |
| 0x02 | PUBLIC_KEY | 33 bytes | Carries a compressed secp256k1 public key |
| 0x03 | CHALLENGE | variable | DDoS proof-of-work challenge (defined in FUDP5) |
| 0x04 | CHALLENGE_RESPONSE | variable | Proof-of-work solution (defined in FUDP5) |

#### HELLO (0x01)

No additional payload. Total control payload: 1 byte.

#### PUBLIC_KEY (0x02)

```
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| 0x02          | Compressed Public Key       |
|               | (33 bytes, secp256k1)       |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

Total control payload: 1 byte (type) + 33 bytes (key) = **34 bytes**.

The public key is expected to be a valid compressed secp256k1 point. The first byte of the key is `0x02` or `0x03` indicating the parity of the Y coordinate.

---

## Variable-Length Integer Encoding

FUDP uses a variable-length integer (varint) encoding for all frame fields, following the same scheme as QUIC (RFC 9000, Section 16). The two most significant bits (2MSB) of the first byte determine the total encoding length.

### Encoding Table

| 2MSB | Encoding Length | Usable Bits | Maximum Representable Value |
|---|---|---|---|
| 00 | 1 byte | 6 bits | 63 |
| 01 | 2 bytes | 14 bits | 16,383 |
| 10 | 4 bytes | 30 bits | 1,073,741,823 |
| 11 | 8 bytes | 62 bits | 4,611,686,018,427,387,903 |

### Encoding Procedure

```
PROCEDURE encode_varint(value):
    IF value <= 63:
        WRITE 1 byte: value (2MSB = 00)
    ELSE IF value <= 16383:
        WRITE 2 bytes: (0x40 OR (value >> 8)), (value AND 0xFF)
    ELSE IF value <= 1073741823:
        WRITE 4 bytes: (0x80 OR (value >> 24)),
                       (value >> 16) AND 0xFF,
                       (value >> 8) AND 0xFF,
                       value AND 0xFF
    ELSE IF value <= 4611686018427387903:
        WRITE 8 bytes: (0xC0 OR (value >> 56)),
                       (value >> 48) AND 0xFF,
                       (value >> 40) AND 0xFF,
                       (value >> 32) AND 0xFF,
                       (value >> 24) AND 0xFF,
                       (value >> 16) AND 0xFF,
                       (value >> 8) AND 0xFF,
                       value AND 0xFF
    ELSE:
        ERROR: value exceeds maximum representable range
```

### Decoding Procedure

```
PROCEDURE decode_varint(bytes):
    first_byte = READ 1 byte
    prefix = first_byte >> 6         -- extract 2MSB
    value = first_byte AND 0x3F      -- mask off 2MSB

    IF prefix == 0:                  -- 1-byte encoding
        RETURN value
    ELSE IF prefix == 1:             -- 2-byte encoding
        value = (value << 8) OR READ(1 byte)
        RETURN value
    ELSE IF prefix == 2:             -- 4-byte encoding
        value = (value << 8) OR READ(1 byte)
        value = (value << 8) OR READ(1 byte)
        value = (value << 8) OR READ(1 byte)
        RETURN value
    ELSE:                            -- 8-byte encoding (prefix == 3)
        value = (value << 8) OR READ(1 byte)
        value = (value << 8) OR READ(1 byte)
        value = (value << 8) OR READ(1 byte)
        value = (value << 8) OR READ(1 byte)
        value = (value << 8) OR READ(1 byte)
        value = (value << 8) OR READ(1 byte)
        value = (value << 8) OR READ(1 byte)
        RETURN value
```

### Examples

| Decimal Value | Hex Encoding | Bytes |
|---|---|---|
| 0 | `0x00` | 1 |
| 37 | `0x25` | 1 |
| 15,293 | `0x7B BD` | 2 |
| 494,878,333 | `0x9D 7F 3E 7D` | 4 |

---

## Frame Types

Frames are the fundamental unit of data within an encrypted payload. After any optional timestamp and session-epoch fields indicated by the header flags, one or more frames are concatenated without delimiters. Each frame begins with a varint-encoded frame type.

### Frame Type Summary

| Type Value | Name | Description |
|---|---|---|
| 0x00 | PADDING | No operation; ignored by receiver |
| 0x01 | ACK | Acknowledgment of received packets |
| 0x02 | CONNECTION_CLOSE | Graceful connection termination |
| 0x03 | MAX_DATA | Update connection-level flow control limit |
| 0x04 | MAX_STREAM_DATA | Update stream-level flow control limit |
| 0x05 | MAX_STREAMS | Update maximum number of concurrent streams |
| 0x08-0x0F | STREAM | Application data with flags encoded in type (LEN is mandatory in v1 wire behavior) |
| 0x10 | DATAGRAM | Unreliable application data: never retransmitted, not ack-eliciting. Defined in [FUDP7](FUDP7V1_Datagram.md). MUST NOT be sent until the peer is known to support it (see [Versioning](#versioning)). |

The values `0x06` and `0x07` belonged to the removed SYMKEY_PROPOSAL and SYMKEY_ACK frames and MUST NOT be reused.

### PADDING Frame (0x00)

```
PADDING Frame {
  Type (varint) = 0x00
}
```

A single zero byte. No additional fields. Receivers MUST silently ignore PADDING frames. PADDING frames may be used to increase packet size for path MTU discovery or to obscure traffic patterns.

### ACK Frame (0x01)

```
ACK Frame {
  Type (varint) = 0x01,
  Largest Acknowledged (varint),
  ACK Delay (varint),
  ACK Range Count (varint),
  First ACK Range (varint),
  ACK Range (..) ...
}
```

| Field | Type | Description |
|---|---|---|
| Type | varint | `0x01` |
| Largest Acknowledged | varint | The largest packet number being acknowledged |
| ACK Delay | varint | Time elapsed since the largest acknowledged packet was received, in **microseconds** |
| ACK Range Count | varint | Total number of ACK range entries encoded in this frame, including the first range |
| First ACK Range | varint | Number of contiguous packets acknowledged before Largest Acknowledged (inclusive with Largest Acknowledged, the range covers First ACK Range + 1 packets total) |

If `ACK Range Count` is greater than 1, each range after the first has the following structure:

```
ACK Range {
  Gap (varint),
  ACK Range Length (varint)
}
```

| Field | Type | Description |
|---|---|---|
| Gap | varint | Number of consecutive unacknowledged packets in the gap |
| ACK Range Length | varint | Number of consecutive acknowledged packets in this range minus one |

#### Reconstructing Acknowledged Packet Numbers

```
PROCEDURE reconstruct_ack_ranges(largest_ack, first_range, additional_ranges[]):
    -- Step 1: First range
    range_start = largest_ack
    range_end   = largest_ack - first_range
    EMIT acknowledged range [range_end .. range_start]

    current = range_end

    -- Step 2: Process each additional ACK Range
    FOR EACH (gap, ack_range_length) IN additional_ranges:
        current = current - gap - 2
        range_start = current
        range_end   = current - ack_range_length
        EMIT acknowledged range [range_end .. range_start]
        current = range_end
```

#### ACK-Eliciting Behavior

ACK frames are **NOT** ack-eliciting. A packet containing only ACK frames (and optionally PADDING) does not require the receiver to send an acknowledgment in response.

### CONNECTION_CLOSE Frame (0x02)

```
CONNECTION_CLOSE Frame {
  Type (varint) = 0x02,
  Error Code (varint),
  Reason Phrase Length (varint),
  Reason Phrase (UTF-8 bytes)
}
```

| Field | Type | Description |
|---|---|---|
| Type | varint | `0x02` |
| Error Code | varint | Machine-readable error code (see table below) |
| Reason Phrase Length | varint | Length of the Reason Phrase field in bytes |
| Reason Phrase | bytes | Human-readable UTF-8 encoded explanation; MAY be empty |

#### Error Codes

| Code | Name | Description |
|---|---|---|
| 0x00 | NO_ERROR | Normal closure; no error occurred |
| 0x01 | INTERNAL_ERROR | Unspecified implementation error |
| 0x02 | CRYPTO_ERROR | Cryptographic operation failed (decryption, signature verification, etc.) |
| 0x03 | FLOW_CONTROL_ERROR | Peer exceeded an advertised flow control limit |
| 0x04 | STREAM_LIMIT_ERROR | Peer exceeded the advertised maximum number of streams |
| 0x05 | PROTOCOL_VIOLATION | Peer violated a protocol requirement |
| 0x06 | IDLE_TIMEOUT | Connection has been idle beyond the allowed threshold |
| 0x07 | CONNECTION_LIMIT | Peer has exceeded the maximum allowed connections |

### MAX_DATA Frame (0x03)

```
MAX_DATA Frame {
  Type (varint) = 0x03,
  Maximum Data (varint)
}
```

| Field | Type | Description |
|---|---|---|
| Type | varint | `0x03` |
| Maximum Data | varint | The new maximum total bytes the peer may send on the connection |

The Maximum Data value is cumulative. A new MAX_DATA frame with a value less than or equal to a previously received value MUST be ignored.

### MAX_STREAM_DATA Frame (0x04)

```
MAX_STREAM_DATA Frame {
  Type (varint) = 0x04,
  Stream ID (varint),
  Maximum Stream Data (varint)
}
```

| Field | Type | Description |
|---|---|---|
| Type | varint | `0x04` |
| Stream ID | varint | Identifies the stream to which the limit applies |
| Maximum Stream Data | varint | The new maximum bytes the peer may send on the identified stream |

### MAX_STREAMS Frame (0x05)

```
MAX_STREAMS Frame {
  Type (varint) = 0x05,
  Maximum Streams (varint)
}
```

| Field | Type | Description |
|---|---|---|
| Type | varint | `0x05` |
| Maximum Streams | varint | The new maximum number of concurrent streams the peer may open |

### STREAM Frame (0x08-0x0F)

The STREAM frame carries application data. Three flag bits are encoded in the lower 3 bits of the frame type:

| Bit | Mask | Name | Description |
|---|---|---|---|
| 0 | 0x01 | FIN | This frame marks the end of the stream |
| 1 | 0x02 | LEN | Length field; MUST be set in released v1 packets |
| 2 | 0x04 | OFF | The Offset field is present (omitted when offset is 0) |

The effective type byte ranges from `0x08` (no flags) to `0x0F` (all flags set).

```
STREAM Frame {
  Type (varint) = 0x08..0x0F,
  Stream ID (varint),
  [Offset (varint)],
  [Length (varint)],
  Stream Data (bytes)
}
```

| Field | Type | Presence | Description |
|---|---|---|---|
| Type | varint | always | `0x08` + flag bits |
| Stream ID | varint | always | Identifies the stream |
| Offset | varint | if OFF bit set | Byte offset within the stream for the first byte of Stream Data; implicit 0 when absent |
| Length | varint | always in v1 | Length of Stream Data in bytes |
| Stream Data | bytes | always | The application data bytes |

#### Stream ID Allocation

Stream IDs are varint-encoded unsigned integers. The two least significant bits of the Stream ID carry semantics:

| Bits 0-1 | Initiated By | Type |
|---|---|---|
| 0x00 | Initiator | Bidirectional |
| 0x01 | Responder | Bidirectional |
| 0x02 | Initiator | Unidirectional |
| 0x03 | Responder | Unidirectional |

See FUDP2 (Stream ID Encoding) for the required parity-assignment rule (bit 0) and the failure mode it closes: two independent local allocators sharing one ID space cannot stay collision-free by convention alone, so each endpoint MUST derive its parity deterministically rather than by negotiation.

---

## Connection Lifecycle

### Connection States

| State | Description |
|---|---|
| IDLE | Initial state; no packets have been sent or received for this connection |
| ESTABLISHING | Handshake in progress; at least one handshake packet has been sent or received |
| ESTABLISHED | Shared secret derived; encrypted data can flow bidirectionally |
| CLOSING | FIN sent or CONNECTION_CLOSE frame sent; draining remaining packets |
| CLOSED | Connection fully terminated; all resources released |

### State Transition Table

| Current State | Event | Next State |
|---|---|---|
| IDLE | Send or receive HELLO | ESTABLISHING |
| ESTABLISHING | First encrypted DATA packet decrypted successfully | ESTABLISHED |
| ESTABLISHING | Handshake timeout (recommended: 10,000 ms) | CLOSED |
| ESTABLISHED | Send or receive CONNECTION_CLOSE frame | CLOSING |
| ESTABLISHED | Idle timeout exceeded | CLOSING |
| CLOSING | Drain period expires (recommended: 3x RTT or 3,000 ms minimum) | CLOSED |
| Any state | Unrecoverable error | CLOSED |

### Connection Establishment (Handshake)

FUDP uses a plaintext key-exchange handshake to establish a shared secret before any encrypted communication.

```
Initiator                                            Responder
    |                                                     |
    |  1. CONTROL: HELLO                                  |
    | --------------------------------------------------> |
    |                                                     |
    |  2. CONTROL: PUBLIC_KEY (33-byte compressed pubkey)  |
    | <-------------------------------------------------- |
    |                                                     |
    |  3. Derive shared secret via ECDH (FTSP11)          |
    |                                                     |
    |  4. DATA (encrypted, CryptoDataByte includes         |
    |     initiator's public key)                         |
    | --------------------------------------------------> |
    |                                                     |
    |  5. Responder decrypts via ECDH,                    |
    |     extracts initiator identity from bundle         |
    |                                                     |
    |  6. ACK (encrypted)                                 |
    | <-------------------------------------------------- |
    |                                                     |
    | -- Connection ESTABLISHED --                        |
```

**Step-by-step procedure:**

1. **Initiator** sends a CONTROL packet (type `0x02`) with a HELLO payload to the responder's address. Control packets use connection ID `0` in the Java reference implementation.

2. **Responder** replies with a CONTROL packet containing a PUBLIC_KEY payload: the responder's 33-byte compressed secp256k1 public key.

3. **Initiator** derives a shared secret using ECDH per [FTSP11V1_Ecc256K1AesGcm256](../FTSP/FTSP11V1_Ecc256K1AesGcm256.md): perform elliptic-curve Diffie-Hellman with the initiator's private key and the responder's public key, then derive the symmetric key via HKDF per [FTSP13V1_HKDF](../FTSP/FTSP13V1_HKDF.md).

4. **Initiator** creates a local connection record, allocates a local Connection ID, and sends the first encrypted DATA packet. The `CryptoDataByte` bundle in the payload includes the initiator's compressed public key, allowing the responder to identify the initiator.

5. **Responder** extracts the initiator's public key from the `CryptoDataByte` bundle, performs ECDH to derive the same shared secret, decrypts the payload, and creates or reuses its own connection record for that peer/source address.

6. **Responder** sends an encrypted ACK packet. Upon receipt by the initiator, the connection transitions to ESTABLISHED.

### Multi-Connection Support

A peer (identified by its FID / public key) MAY maintain multiple simultaneous connections from different network addresses. Each endpoint's connection record has a unique local Connection ID.

Implementations SHOULD enforce a maximum number of concurrent connections per peer. The recommended limit is **5** connections per peer. The Java reference implementation evicts the idlest or least-recently-used connection when this limit is exceeded.

### Path Migration

A peer's network-visible source address can change mid-connection without the peer restarting — most commonly a NAT rebinding the mapped external port on a long-lived UDP flow (observed in the field on mobile/carrier NAT and consumer routers after tens of seconds to a few minutes of the path being otherwise idle, e.g. while a large upload's ACKs are the only traffic in one direction). This is NOT a peer restart: the Session Epoch (see [Encrypted Payload Structure](#encrypted-payload-structure)) is unchanged, and the peer's connection state — packet-number space, open streams, congestion window — is still valid and MUST be preserved.

An implementation that keys connection lookup by `(peer identity, source address)` cannot distinguish this from a genuinely new connection: it creates a second connection record at the new address while the first is still tracked at the old one. This causes cascading failure:

1. The new record's packet numbers start fresh (e.g. from a low value), which the *old* record's replay-protection window judges as replayed/stale traffic — packets may be silently dropped.
2. Outbound packets (ACKs, responses) already queued or newly generated for the old record are sent to the now-unreachable old address and are never delivered.
3. If the implementation's local stream ID allocator is a single per-connection counter (see [Stream ID Encoding](#stream-id-encoding) requirements in FUDP2 for details), the phantom new connection restarts that counter from its base value. If the base value coincides with a stream ID the peer already used and retired on the "real" connection, the peer's stream-retirement tombstone silently drops every frame of the response sent on the colliding ID — the request succeeds server-side but the requester observes only a timeout.

Implementations MUST therefore resolve inbound packets to a connection primarily by the sender's Connection ID (per [Connection ID](#connection-id-bytes-5-12)), not by source address, and MUST migrate an existing connection's tracked address rather than creating a new connection when:

- The decrypted peer identity matches an existing connection's peer identity, AND
- The packet's Connection ID matches that connection's previously observed remote Connection ID, AND
- The packet's source address differs from the connection's currently tracked address.

```
PROCEDURE resolve_connection(senderId, remoteConnId, sourceAddress):
    conn = lookup_by_remote_connection_id(remoteConnId)
    IF conn != NULL AND conn.peerId == senderId:
        IF conn.trackedAddress != sourceAddress:
            migrate_address(conn, sourceAddress)   -- keep all connection state
        RETURN conn

    conn = lookup_or_create_by(senderId, sourceAddress)
    IF conn.knownRemoteConnectionId == 0:
        bind_remote_connection_id(remoteConnId, conn)
    ELSE IF conn.knownRemoteConnectionId != remoteConnId:
        -- Same peer+address, but the peer is using a DIFFERENT connection id
        -- than before: it rebuilt its connection object (fresh packet-number
        -- space and streams) without a full restart. Treat like peer restart.
        reset_connection_state(conn)               -- see Peer Restart handling
        bind_remote_connection_id(remoteConnId, conn)
    RETURN conn
```

Migration MUST preserve the connection's packet-number space, replay-protection window, open streams (including retired-stream tombstones), congestion controller state, and RTT estimate — only the tracked destination address changes. Migration SHOULD be logged (address, connection ID) for operational diagnosis, since a rebind is otherwise invisible to the application layer.

The second branch of the procedure above also handles a related but distinct case: the same peer identity and address are observed, but with a Connection ID the implementation has not associated with that connection before. This means the peer rebuilt its local connection object (e.g. after an internal reconnect) while keeping the same identity and address. Because the peer's packet-number space and stream allocator have restarted, the implementation MUST reset its own tracked state for that connection (equivalent to the [Peer Restart](#connection-establishment-handshake) handling below) rather than reject the new packet numbers as replays.

### Peer Endpoint Selection

An implementation MAY persist multiple known network addresses (endpoints) for the same peer FID, accumulated from outbound connections, inbound connections, and configuration. Persisted endpoints go stale: servers change IP addresses, and addresses learned from inbound connections behind NAT (ephemeral source ports) are usually not reachable for new outbound traffic.

When initiating communication with a peer, implementations MUST prefer the most recently **verified** endpoint — one that answered a HELLO with a PUBLIC_KEY matching the peer's FID, or that produced authenticated (decryptable) traffic. In particular:

1. When an endpoint answers a HELLO, it SHOULD be promoted to the peer's primary endpoint so subsequent connections and retries target it before older persisted endpoints. Demoted endpoints MAY be retained as fallbacks.
2. Creating a local connection record is not evidence of reachability. An outbound connection that never reaches ESTABLISHED SHOULD be discarded after a request timeout (e.g. an unanswered application-level PING) rather than reused for later requests.
3. Endpoints learned from inbound connections SHOULD NOT take precedence over endpoints verified by outbound handshakes.

Without these rules, a stale primary endpoint can shadow a freshly verified address indefinitely: each cycle re-verifies the correct address via HELLO, then sends application traffic to the stale endpoint and times out.

### Connection Termination

Either endpoint MAY terminate the connection at any time:

1. Send a packet containing a CONNECTION_CLOSE frame with the appropriate error code.
2. The FIN flag (bit 4) MAY be set in the packet header, but the Java reference implementation relies on the CONNECTION_CLOSE frame itself.
3. The connection transitions to CLOSING state.
4. After a drain period (recommended: 3 times the estimated RTT, minimum 3,000 ms), transition to CLOSED state and release all connection resources.

```
PROCEDURE close_connection(connection, error_code, reason):
    frame = build_connection_close_frame(error_code, reason)
    packet = build_packet(connection, DATA, frames=[frame])
    SEND packet
    connection.state = CLOSING
    START drain_timer(max(3 * connection.rtt, 3000 ms))
    ON drain_timer EXPIRED:
        connection.state = CLOSED
        RELEASE connection resources
```

### Idle Timeout

Connections with no packet activity (sent or received) exceeding the stale idle threshold MAY be closed by either endpoint. The recommended stale idle threshold is **30,000 ms** (30 seconds).

Implementations that require long-lived connections SHOULD use application-level PING/PONG messages (sent as STREAM frame data) to maintain activity within the idle threshold.

---

## Default Protocol Parameters

| Parameter | Value | Description |
|---|---|---|
| Max Packet Size | 1,350 bytes | Default maximum UDP datagram size; safe for most network MTUs. A hard limit — see [Packet Size Budget](#packet-size-budget) |
| Header Size | 21 bytes | Fixed-size plaintext packet header |
| Crypto Overhead | 68 bytes | CryptoDataByte bundle around the plaintext: algorithm prefix (6), type (1), sender public key (33), IV (12), GCM tag (16) |
| Plaintext Prefix | 16 bytes | Timestamp (8) and session epoch (8), budgeted as if both are present |
| Max Frame Bytes per Packet | 1,245 bytes | Max Packet Size − Header Size − Crypto Overhead − Plaintext Prefix |
| Protocol Version | 1 | Current version of FUDP Core Transport |
| Stale Idle Threshold | 30,000 ms | Duration of inactivity before a connection may be evicted |
| Handshake Timeout | 10,000 ms | Maximum time to complete the handshake before aborting |
| Drain Period | max(3 * RTT, 3,000 ms) | Time to wait in CLOSING state before releasing resources |
| Max Connections per Peer | 5 | Recommended limit on concurrent connections from a single peer |
| Control Packet Max Payload | 256 bytes | Maximum payload size for plaintext control packets |

### Packet Size Budget

No packet may exceed Max Packet Size. A UDP packet over the path MTU is split into IP fragments, and losing either fragment loses the whole packet, so an overshoot turns into extra loss exactly on the paths that can least afford it. Loopback (MTU 16 KB or more) hides it completely.

Every sender MUST budget its frames to Max Frame Bytes per Packet:

- **STREAM chunks** are sized for the worst-case STREAM header: type (1) + stream ID (8) + offset (8) + length (4) = 21 bytes, leaving 1,224 data bytes at the default size.
- **A pending ACK frame is added to a packet only if it fits** beside the frames already there. Otherwise it is sent first, in a packet of its own. It MUST NOT be shortened to fit: every ACK frame's full ranges are what protect against earlier ACK packets being lost (FUDP3 §2.1).
- **An ACK frame is itself limited** to Max Frame Bytes, dropping its oldest ranges first.
- DATAGRAM frames are limited to what fits alone (FUDP7).

Two mistakes produced oversize packets before 2026-09-22. The crypto overhead was budgeted as ~52 bytes rather than 68. More seriously, the pending ACK was piggybacked without any check, and with data flowing both ways ACK frames reached 264 bytes (FUDP3 §2.1 explains why), producing 1,664-byte packets on a 1,400-byte setting. The Java reference now counts any packet over the limit (`getOversizePacketCount`) as a budget bug, and `PacketSizeBudgetTest` asserts it stays zero.

---

## Security Considerations

1. **Confidentiality.** All DATA and ACK payloads are encrypted using AES-256-GCM via FTSP11 (Ecc256K1AesGcm256). The 21-byte packet header is always plaintext by design, exposing the Connection ID and Packet Number to on-path observers.

2. **Authenticity.** AES-256-GCM provides authenticated encryption. Any modification to the ciphertext will cause decryption to fail. Receivers MUST discard packets that fail authentication.

3. **Header integrity.** The 21-byte serialized header is bound as AEAD AAD in the reference implementation. Tampering with header fields (version, flags, connection ID, packet number) causes tag verification failure.

4. **Replay Protection.** Session Epoch and monotonically increasing Packet Numbers together prevent replay attacks. Receivers SHOULD maintain a sliding window of accepted packet numbers and reject duplicates.

5. **Connection ID Confidentiality.** Connection IDs are transmitted in plaintext. On-path observers can correlate packets belonging to the same connection. This is an accepted trade-off for enabling stateless routing and multiplexing.

6. **Handshake Vulnerability.** The HELLO/PUBLIC_KEY exchange is unauthenticated plaintext. This makes the initial handshake susceptible to active man-in-the-middle attacks. Implementations SHOULD verify the responder's public key against a known FID or trusted peer book before transmitting sensitive data. DDoS mitigation via proof-of-work challenges (FUDP5) provides an additional layer of protection during handshake.

7. **Denial of Service.** Implementations MUST enforce connection limits per peer (recommended: 5) and SHOULD implement rate limiting on CONTROL packets to mitigate resource exhaustion attacks.

8. **Key Derivation.** The ECDH shared secret MUST be processed through HKDF per [FTSP13V1_HKDF](../FTSP/FTSP13V1_HKDF.md) before use as an AES-256-GCM key. Direct use of the raw ECDH output as a key is prohibited.

---

## Versioning

This document defines FUDP Core Transport version 1. Future versions MAY introduce new frame types, modify the header format, or change default parameters.

The Version field in the packet header identifies the wire format. The Java reference implementation accepts version `1` on encrypted DATA and ACK packets and silently drops encrypted packets with unsupported versions. CONTROL packets are plaintext and version-agnostic in v1.

New frame types MAY be introduced without incrementing the version number, but they are **not** transparently backward-compatible. A frame carries no length that a receiver could use to skip a type it does not know, so a receiver that meets an unknown frame type cannot parse the rest of the packet: it MUST drop the whole packet, including any known frames in it. A sender MUST therefore not send a new frame type on a connection until it knows the peer supports it — learned from the application or a negotiation, never assumed.

A packet dropped this way decrypted and authenticated correctly; only its frames could not be read. Receivers MUST NOT count it as a decrypt failure. Decrypt failures feed the per-source limiter (FUDP4 §7), and the Java reference used to count parse failures there: five such packets in a row — an older peer receiving a 25 packet/s DATAGRAM flow reaches that in a fifth of a second — dropped every packet from that address, reliable traffic included, for a second at a time. Nodes already deployed with that behaviour are one more reason the capability rule above is a MUST. The lost packet's reliable frames are recovered by ordinary retransmission; nothing else is lost.

|Ver|Date|Changes|
|---|---|---|
|1|2026-03-28|Initial specification.|
|1 (rev)|2026-09-22|[Packet Size Budget](#packet-size-budget): Max Packet Size is a hard limit. Crypto overhead corrected from ~52 to 68 bytes. STREAM chunks are budgeted for the worst-case header, and a piggybacked ACK must fit or be sent alone. Previously, traffic in both directions produced packets up to 264 bytes over the limit.|
|1 (rev)|2026-09-22|Added DATAGRAM (`0x10`, FUDP7) to the Frame Type Summary and reserved `0x06`/`0x07`. A packet with a DATAGRAM frame MUST carry a timestamp. [Versioning](#versioning): corrected the claim that new frame types are backward-compatible — an unknown type loses the whole packet, so new types need known peer support first — and required that such a parse failure not be counted as a decrypt failure.|
|1 (rev)|2026-07-14|Added [Path Migration](#path-migration): Connection ID (not source address) MUST be the primary key for resolving inbound packets to a connection, and an address change on an already-known Connection ID MUST migrate the existing connection rather than create a phantom new one. Discovered via a field failure where a NAT rebind mid-upload caused colliding stream IDs between a phantom connection and the real one, silently swallowing responses via stream-retirement tombstones.

---

## Related Protocols

| Protocol | Relationship |
|---|---|
| [FTSP11V1_Ecc256K1AesGcm256](../FTSP/FTSP11V1_Ecc256K1AesGcm256.md) | Defines the ECDH key exchange and AES-256-GCM encryption used for all encrypted payloads |
| [FTSP13V1_HKDF](../FTSP/FTSP13V1_HKDF.md) | Defines the HKDF key derivation function used to derive symmetric keys from ECDH shared secrets |
| FUDP5 (DDoS Protection) | Defines the proof-of-work challenge/response mechanism for CONTROL packets |
