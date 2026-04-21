# FUDP6V1_Messages

## Contents

[Summary](#summary)

[Abstract](#abstract)

[Motivation](#motivation)

[Specification](#specification)

- [Message Envelope Format](#message-envelope-format)
- [Type Field](#type-field)
- [Message ID](#message-id)
- [Flags](#flags)
- [Payload Length](#payload-length)
- [Payload](#payload)
- [Messaging (Chat)](#messaging-chat)
- [Request/Response (RPC)](#requestresponse-rpc)
- [File Transfer](#file-transfer)
- [Control](#control)
- [Relay](#relay)
- [NAT Traversal](#nat-traversal)
- [General Data](#general-data)
- [Decoding Procedure](#decoding-procedure)

[Security Considerations](#security-considerations)

[Versioning](#versioning)

[Related Protocols](#related-protocols)

[Reference Implementation](#reference-implementation)

---

## Summary

|Field|Content|
|---|---|
|Title|Messages|
|Type|FUDP|
|SN|6|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-03-28|
|PID||

## Abstract

FUDP6 defines the standard application-layer message types transmitted over FUDP streams. Each message is a complete unit delivered on a single stream. The message format is independent of the underlying transport framing defined in FUDP1 and FUDP2. This specification covers the message envelope format, message type codes, payload structures for each message category, and the decoding procedure.

This specification is OPTIONAL. Implementations MAY define their own message layer on top of FUDP streams. However, implementations that wish to interoperate with the standard FUDP message ecosystem MUST follow this specification.

## Motivation

FUDP1 and FUDP2 provide reliable, ordered byte streams between peers. These streams carry raw bytes with no inherent structure beyond ordering. Applications built on FUDP require a common message framing to distinguish message types, correlate requests with responses, and negotiate multi-step interactions such as file transfers.

Without a standard message layer, each application must define its own framing, leading to incompatible implementations and duplicated effort. FUDP6 addresses this by defining a minimal, extensible message envelope and a set of common message types covering chat, RPC, file transfer, relay routing, NAT traversal, and general-purpose data exchange.

## Specification

### Message Envelope Format

Every application message begins with a fixed envelope. The envelope is transmitted as the payload of one or more STREAM frames on a single stream (see FUDP2). The stream delivers the envelope bytes in order; the message layer operates on the reassembled byte sequence.

```
Message Envelope {
  Type (1 byte),
  Message ID (8 bytes, big-endian uint64),
  Flags (1 byte),
  Payload Length (varint),
  Payload (variable bytes)
}
```

Minimum envelope size: 1 + 8 + 1 + 1 = 11 bytes (with a 1-byte varint encoding a zero-length payload).

### Type Field

The first byte of the envelope identifies the message type. The following type codes are defined:

| Code | Name | Category | Description |
|------|------|----------|-------------|
| 0x01 | CHAT | Messaging | Text message |
| 0x02 | CHAT_ACK | Messaging | Delivery acknowledgment for CHAT |
| 0x10 | REQUEST | RPC | Application-level request |
| 0x11 | RESPONSE | RPC | Application-level response |
| 0x12 | ERROR | RPC | Error response |
| 0x20 | FILE_OFFER | File Transfer | Offer to send a file |
| 0x21 | FILE_ACCEPT | File Transfer | Accept a file offer |
| 0x22 | FILE_REJECT | File Transfer | Reject a file offer |
| 0x23 | FILE_CHUNK | File Transfer | File data chunk |
| 0x24 | FILE_COMPLETE | File Transfer | File transfer complete notification |
| 0x25 | FILE_CANCEL | File Transfer | Cancel an in-progress transfer |
| 0x30 | PING | Control | Keep-alive ping |
| 0x31 | PONG | Control | Ping response |
| 0x32 | PEER_INFO | Control | Exchange peer information |
| 0x40 | RELAY | Relay | Relay message to target FID |
| 0x41 | RELAY_ACK | Relay | Relay delivery confirmed |
| 0x42 | RELAY_FAIL | Relay | Relay delivery failed |
| 0x43 | RELAY_QUERY | Relay | Query relay path/cost |
| 0x44 | RELAY_QUOTE | Relay | Relay cost quote response |
| 0x50 | NAT_REGISTER | NAT | Register with relay for NAT traversal |
| 0x51 | NAT_KEEPALIVE | NAT | Keep NAT mapping alive |
| 0x52 | NAT_PROBE | NAT | Probe for direct connectivity |
| 0x53 | NAT_PROBE_RESPONSE | NAT | Response to connectivity probe |
| 0x60 | BYTES | Data | General-purpose byte array |
| 0x61 | BYTES_ACK | Data | Bytes delivery confirmed |

Type codes not listed above are reserved. An implementation that receives a message with an unrecognized type code MUST ignore the message. Implementations MUST NOT send messages with reserved type codes.

### Message ID

Bytes 1 through 8 of the envelope carry an 8-byte message identifier encoded as a big-endian unsigned 64-bit integer. The Message ID serves two purposes:

1. **Correlation.** Response and acknowledgment messages reference the Message ID of the originating message to associate the two.
2. **Deduplication.** Implementations MAY use the Message ID to detect and discard duplicate messages at the application layer.

Implementations SHOULD generate Message IDs using monotonically increasing values or cryptographically random 64-bit values. The value 0x0000000000000000 is reserved and MUST NOT be used as a Message ID.

### Flags

Byte 9 of the envelope is a flags field. All bits are reserved for future use. Senders MUST set this byte to 0x00. Receivers MUST ignore unknown flag bits. This ensures forward compatibility when new flags are defined in future versions.

### Payload Length

The payload length follows the flags byte and is encoded as a variable-length integer (varint) using the encoding defined in FUDP1. The value represents the number of bytes in the payload that follows. A payload length of zero is valid; some message types (e.g., NAT_KEEPALIVE) carry no payload data.

### Payload

The remaining bytes of the envelope constitute the payload. The structure and interpretation of the payload depend on the message type, as defined in the following sections.

### Messaging (Chat)

#### CHAT (0x01)

The CHAT message carries a text message between peers.

Payload: UTF-8 encoded text string. The payload length determines the string length. Implementations MUST reject payloads that are not valid UTF-8.

Maximum recommended payload size: 65,535 bytes. Implementations MAY impose their own limits but SHOULD accept at least 65,535 bytes.

#### CHAT_ACK (0x02)

The CHAT_ACK message acknowledges receipt of a CHAT message.

Payload: 8 bytes containing the Message ID of the acknowledged CHAT message, encoded as a big-endian unsigned 64-bit integer.

A sender SHOULD send a CHAT_ACK upon receiving and successfully processing a CHAT message. A sender that does not receive a CHAT_ACK within a reasonable timeout MAY retransmit the original CHAT message with the same Message ID.

### Request/Response (RPC)

The RPC message types provide a general-purpose request/response pattern over FUDP streams.

#### REQUEST (0x10)

Payload: Application-defined request data. The format of the request data (e.g., JSON, binary, protobuf) is determined by the application. This specification does not constrain the payload format.

#### RESPONSE (0x11)

Payload: Application-defined response data. The Message ID of the RESPONSE envelope SHOULD match the Message ID of the corresponding REQUEST to enable correlation.

#### ERROR (0x12)

The ERROR message conveys an error in response to a REQUEST. The Message ID of the ERROR envelope SHOULD match the Message ID of the corresponding REQUEST.

Payload:

```
Error Payload {
  Error Code (varint),
  Error Message Length (varint),
  Error Message (UTF-8 bytes)
}
```

The Error Code is an application-defined value. The Error Message is a human-readable description encoded as UTF-8. If no error message is provided, the Error Message Length MUST be 0.

### File Transfer

File transfer follows a negotiation protocol. The typical sequence is:

1. The sender transmits a FILE_OFFER containing file metadata.
2. The receiver replies with FILE_ACCEPT or FILE_REJECT.
3. If accepted, the sender transmits FILE_CHUNK messages. These SHOULD be sent on a dedicated stream to avoid head-of-line blocking with other messages.
4. After the final chunk, the sender transmits FILE_COMPLETE.
5. Either party MAY send FILE_CANCEL at any time to abort the transfer.

#### FILE_OFFER (0x20)

Payload:

```
File Offer Payload {
  File Name Length (varint),
  File Name (UTF-8 bytes),
  File Size (8 bytes, big-endian uint64),
  Checksum Length (varint),
  Checksum (bytes)
}
```

The File Name is a UTF-8 encoded string representing the file name (not a full path). The File Size is the total size of the file in bytes. The Checksum is typically a SHA-256 hash of the complete file content; the Checksum Length indicates the number of bytes in the checksum. Implementations MUST support SHA-256 (32 bytes). Other checksum algorithms MAY be supported by mutual agreement.

#### FILE_ACCEPT (0x21)

Payload: 8 bytes containing the Message ID of the accepted FILE_OFFER, encoded as a big-endian unsigned 64-bit integer.

#### FILE_REJECT (0x22)

Payload: 8 bytes containing the Message ID of the rejected FILE_OFFER, encoded as a big-endian unsigned 64-bit integer, followed by an optional UTF-8 reason string. If the payload length is exactly 8 bytes, no reason is provided. If the payload length exceeds 8 bytes, bytes 9 through the end of the payload are a UTF-8 encoded reason string.

#### FILE_CHUNK (0x23)

Payload:

```
File Chunk Payload {
  Chunk Offset (8 bytes, big-endian uint64),
  Chunk Data (remaining bytes)
}
```

The Chunk Offset indicates the byte position within the file where this chunk's data begins. The Chunk Data occupies the remaining payload bytes. Chunks MAY arrive out of order; the receiver MUST use the Chunk Offset to reassemble the file correctly.

#### FILE_COMPLETE (0x24)

Payload: 8 bytes containing the Message ID of the original FILE_OFFER, encoded as a big-endian unsigned 64-bit integer.

Upon receiving FILE_COMPLETE, the receiver SHOULD verify the checksum of the reassembled file against the checksum provided in the FILE_OFFER. If the checksum does not match, the receiver SHOULD discard the file and MAY notify the sender via an ERROR message.

#### FILE_CANCEL (0x25)

Payload: 8 bytes containing the Message ID of the FILE_OFFER being cancelled, encoded as a big-endian unsigned 64-bit integer, followed by an optional UTF-8 reason string (same encoding as FILE_REJECT).

Either the sender or the receiver MAY send FILE_CANCEL at any time after FILE_OFFER and before FILE_COMPLETE. Upon receiving FILE_CANCEL, the other party MUST cease sending or expecting further FILE_CHUNK messages for that transfer.

### Control

#### PING (0x30)

Payload: 8 bytes containing a timestamp encoded as a big-endian unsigned 64-bit integer representing milliseconds since the Unix epoch (1970-01-01T00:00:00Z).

The receiver MUST respond with a PONG message.

#### PONG (0x31)

Payload: 8 bytes containing the timestamp echoed from the corresponding PING message, encoded identically. The Message ID of the PONG envelope SHOULD match the Message ID of the corresponding PING.

The round-trip time can be computed by subtracting the echoed timestamp from the current time upon receiving the PONG.

#### PEER_INFO (0x32)

Payload: Application-defined peer information. Typical contents include supported feature flags, listening addresses, software version, and capabilities. The format is not constrained by this specification.

Peers SHOULD exchange PEER_INFO messages after connection establishment.

### Relay

Relay messages enable routing through intermediary nodes when direct connectivity between two peers is not possible. A relay node receives a RELAY message from the sender and forwards the inner message to the target peer.

#### RELAY (0x40)

Payload:

```
Relay Payload {
  Target FID Length (varint),
  Target FID (UTF-8 bytes),
  Inner Message Length (varint),
  Inner Message (bytes)
}
```

The Target FID is the Freecash Identity (FID) of the intended recipient, encoded as a UTF-8 string. The Inner Message is a complete FUDP6 message envelope to be delivered to the target peer. The relay node MUST NOT modify the Inner Message.

#### RELAY_ACK (0x41)

Payload: 8 bytes containing the Message ID of the relayed message, encoded as a big-endian unsigned 64-bit integer. The relay node sends RELAY_ACK to the original sender upon successful delivery of the inner message to the target.

#### RELAY_FAIL (0x42)

Payload:

```
Relay Fail Payload {
  Original Message ID (8 bytes, big-endian uint64),
  Error Code (varint),
  Reason Length (varint),
  Reason (UTF-8 bytes)
}
```

The relay node sends RELAY_FAIL to the original sender when delivery to the target fails. The following error codes are defined:

| Code | Name | Description |
|------|------|-------------|
| 0x01 | TARGET_NOT_FOUND | Target FID is not connected to the relay node. |
| 0x02 | TARGET_UNREACHABLE | Target FID is known but delivery failed. |
| 0x03 | RELAY_REFUSED | The relay node refuses to relay this message. |
| 0x04 | QUOTA_EXCEEDED | The sender has exceeded the relay node's rate or volume limit. |

Error codes 0x05 through 0xFF are reserved for future use.

#### RELAY_QUERY (0x43)

Payload: Target FID encoded as a varint-prefixed UTF-8 string (varint length followed by UTF-8 bytes).

A peer sends RELAY_QUERY to a relay node to inquire whether a given target is reachable and what the cost of relaying would be. The relay node responds with RELAY_QUOTE.

#### RELAY_QUOTE (0x44)

Payload: Application-defined cost and path information. The format is not constrained by this specification. Typical contents include estimated latency, relay fee (in satoshis), and the number of intermediate hops.

### NAT Traversal

NAT traversal messages enable peers behind Network Address Translation devices to establish connectivity through relay-assisted hole punching.

#### NAT_REGISTER (0x50)

Payload: Application-defined registration data. A peer behind a NAT sends NAT_REGISTER to a relay node to register its presence and external address information. Typical contents include the peer's observed external IP address and port.

#### NAT_KEEPALIVE (0x51)

Payload: Empty or a minimal timestamp (8 bytes, big-endian, milliseconds since Unix epoch).

NAT mappings are typically ephemeral. A registered peer SHOULD send NAT_KEEPALIVE at regular intervals to prevent the NAT mapping from expiring. The recommended interval is 25 seconds.

#### NAT_PROBE (0x52)

Payload: Target address information for connectivity testing. A relay node sends NAT_PROBE to a registered peer to test whether direct connectivity with another peer is possible. The payload typically contains the address and port of the other peer.

#### NAT_PROBE_RESPONSE (0x53)

Payload: Probe result. The peer responds to a NAT_PROBE with a NAT_PROBE_RESPONSE indicating whether the target address was reachable, along with the observed source address if a response was received.

### General Data

#### BYTES (0x60)

Payload: Arbitrary byte array. The BYTES message type provides a general-purpose transport for application-specific data that does not fit any other message category. The interpretation of the payload is entirely application-defined.

#### BYTES_ACK (0x61)

Payload: 8 bytes containing the Message ID of the acknowledged BYTES message, encoded as a big-endian unsigned 64-bit integer.

### Decoding Procedure

To decode a message from a stream's reassembled byte sequence:

1. Read 1 byte as Type. Look up the message type in the type table.
2. Read 8 bytes as Message ID (big-endian uint64).
3. Read 1 byte as Flags.
4. Read a varint as Payload Length (using the varint encoding defined in FUDP1).
5. Read exactly Payload Length bytes as Payload.
6. Decode the Payload according to the rules for the message type identified in step 1.

If the Type is not recognized, the implementation MUST skip the message (consume Payload Length bytes) and proceed to the next message on the stream.

To peek at the message type without performing a full decode, read only byte 0 of the envelope. To peek at the Message ID, read bytes 1 through 8.

Multiple messages MAY be sent sequentially on a single stream. After decoding one message, the next message (if any) begins immediately at the next byte. End-of-stream (FIN) indicates that no further messages will be sent.

## Security Considerations

1. **Payload validation.** Implementations MUST validate all payload fields before processing. Malformed payloads (e.g., a varint that exceeds the remaining payload bytes, a UTF-8 string that is not valid UTF-8, or a Payload Length that exceeds implementation limits) MUST be rejected. The message SHOULD be discarded and the stream MAY be reset.

2. **Message size limits.** Implementations SHOULD enforce a maximum message size to prevent memory exhaustion. A recommended default maximum payload size is 16,777,216 bytes (16 MB). Messages exceeding this limit SHOULD be rejected. File transfer data is naturally chunked via FILE_CHUNK and is not subject to this limit per-chunk.

3. **Relay amplification.** A malicious peer could use RELAY messages to amplify traffic toward a target. Relay nodes MUST enforce rate limits per sender and per target. Relay nodes SHOULD require authentication (via FUDP4 or FUDP5) before accepting RELAY messages.

4. **Inner message integrity.** The Inner Message within a RELAY payload is a complete FUDP6 envelope. The relay node MUST NOT modify the Inner Message. The target peer SHOULD verify the inner message's integrity using the authentication mechanisms established by the FUDP connection between the original sender and the target.

5. **File transfer verification.** Implementations MUST verify the checksum of a completed file transfer. A file whose checksum does not match the FILE_OFFER checksum MUST be discarded. This prevents data corruption and detects tampering by intermediary nodes.

6. **Encryption.** All FUDP6 messages are transmitted within FUDP streams, which are encrypted at the transport layer (FUDP4). Application-layer encryption of message payloads is outside the scope of this specification but MAY be applied by the application for end-to-end confidentiality in relay scenarios.

## Versioning

|Ver|Date|Changes|
|---|---|---|
|1|2026-03-28|Initial specification.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FUDP0 (FUDP)|Foundational rules, varint encoding, conformance requirements.|
|FUDP1 (Core Transport)|Defines STREAM frame format and varint encoding referenced by this specification for Payload Length fields.|
|FUDP2 (Streams)|Defines stream multiplexing and data reassembly. FUDP6 messages are carried as stream payload.|
|FUDP3 (Loss & Congestion)|Handles retransmission of lost STREAM frames carrying FUDP6 message data.|
|FUDP4 (Security)|All stream data is encrypted. Security handshake must complete before application messages can be exchanged.|
|FUDP5 (Identity & Authentication)|Peer identity verification. Required for relay and NAT traversal message authentication.|

## Reference Implementation

The reference implementation is located in the FC-JDK repository under the `fudp` package. Key classes:

- `fudp.handler.MessageHandler` -- Decodes incoming FUDP6 message envelopes and dispatches by type.
- `fudp.connection.PeerConnection` -- Sends and receives application messages over FUDP streams.
- `fudp.connection.ConnectionContext` -- Manages stream allocation for message transmission.
- `fudp.node.FudpNode` -- Top-level node coordinating message routing, relay, and NAT traversal.
