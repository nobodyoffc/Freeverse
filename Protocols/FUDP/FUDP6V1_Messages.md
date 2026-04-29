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
- [Request/Response (RPC)](#requestresponse-rpc)
- [Notify](#notify)
- [Keepalive](#keepalive)
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

This specification is OPTIONAL. Implementations MAY define their own message layer on top of FUDP streams. This document follows the FC-JDK Java reference implementation profile.

## Motivation

FUDP1 and FUDP2 provide reliable, ordered byte streams between peers. These streams carry raw bytes with no inherent structure beyond ordering. Applications built on FUDP require a common message framing to distinguish message types, correlate requests with responses, and exchange one-way notifications.

Without a standard message layer, each application must define its own framing, leading to incompatible implementations and duplicated effort. FUDP6 addresses this by defining the Java reference implementation's minimal envelope and message set for RPC, notifications, keepalive, and error reporting.

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
| 0x10 | REQUEST | RPC | Application-level request |
| 0x11 | RESPONSE | RPC | Application-level response |
| 0x12 | ERROR | RPC | Error response |
| 0x20 | NOTIFY | Notify | One-way data message |
| 0x21 | NOTIFY_ACK | Notify | Delivery acknowledgment for NOTIFY |
| 0x30 | PING | Control | Keep-alive ping |
| 0x31 | PONG | Control | Ping response |

Type codes not listed above are reserved. The Java reference implementation rejects unrecognized message types during decode and reports an invalid message to the node event listener.

### Message ID

Bytes 1 through 8 of the envelope carry an 8-byte message identifier encoded as a big-endian unsigned 64-bit integer. The Message ID serves two purposes:

1. **Correlation.** Response and acknowledgment messages reference the Message ID of the originating message to associate the two.
2. **Deduplication.** Implementations MAY use the Message ID to detect and discard duplicate messages at the application layer.

Implementations SHOULD generate Message IDs using monotonically increasing values or cryptographically random 64-bit values. The value 0x0000000000000000 is reserved and MUST NOT be used as a Message ID.

### Flags

Byte 9 of the envelope is a flags field. The Java reference implementation defines the following flags:

| Bit | Name | Description |
|---|---|---|
| 0x01 | NEED_ACK | Request a NOTIFY_ACK for a NOTIFY message |
| 0x02 | COMPRESSED | Payload is compressed by the application |
| 0x04 | ENCRYPTED_APP | Payload has additional application-layer encryption |
| 0x08 | FRAGMENTED | Message is fragmented by the application |
| 0x10 | WANT_PONG_INFO | Ask the responder to include optional info in PONG |

Receivers MUST ignore unknown flag bits.

### Payload Length

The payload length follows the flags byte and is encoded as a variable-length integer (varint) using the encoding defined in FUDP1. The value represents the number of bytes in the payload that follows. A payload length of zero is valid for message types whose payload definition permits it.

### Payload

The remaining bytes of the envelope constitute the payload. The structure and interpretation of the payload depend on the message type, as defined in the following sections.

### Request/Response (RPC)

The RPC message types provide a general-purpose request/response pattern over FUDP streams.

#### REQUEST (0x10)

Payload:

```
Request Payload {
  Service Name Length (varint),
  Service Name (UTF-8 bytes),
  Request Data (remaining bytes)
}
```

The Service Name identifies the application service or method being requested. Request Data is application-defined.

#### RESPONSE (0x11)

Payload:

```
Response Payload {
  Status Code (2 bytes, big-endian uint16),
  Response Data (remaining bytes)
}
```

The Message ID of the RESPONSE envelope SHOULD match the Message ID of the corresponding REQUEST to enable correlation. The Java reference implementation defines status code `0` as success and uses HTTP-like values such as `400`, `403`, `404`, and `500` for common errors.

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

### Notify

#### NOTIFY (0x20)

Payload:

```
Notify Payload {
  Data Type (1 byte),
  Data Length (4 bytes, big-endian uint32),
  Data (variable bytes)
}
```

The Java reference implementation defines data type `0` as raw bytes, `1` as JSON, `2` as protobuf, and `3` as MessagePack. If the NEED_ACK flag is set, the receiver SHOULD respond with NOTIFY_ACK.

#### NOTIFY_ACK (0x21)

Payload: 8 bytes containing the Message ID of the acknowledged NOTIFY message, encoded as a big-endian unsigned 64-bit integer.

### Keepalive

#### PING (0x30)

Payload: 8 bytes containing a timestamp encoded as a big-endian unsigned 64-bit integer representing milliseconds since the Unix epoch (1970-01-01T00:00:00Z).

The receiver MUST respond with a PONG message.

#### PONG (0x31)

Payload: 8 bytes containing the timestamp echoed from the corresponding PING message, encoded identically. The Message ID of the PONG envelope SHOULD match the Message ID of the corresponding PING.

The round-trip time can be computed by subtracting the echoed timestamp from the current time upon receiving the PONG.

### Decoding Procedure

To decode a message from a stream's reassembled byte sequence:

1. Read 1 byte as Type. Look up the message type in the type table.
2. Read 8 bytes as Message ID (big-endian uint64).
3. Read 1 byte as Flags.
4. Read a varint as Payload Length (using the varint encoding defined in FUDP1).
5. Read exactly Payload Length bytes as Payload.
6. Decode the Payload according to the rules for the message type identified in step 1.

If the Type is not recognized, the Java reference implementation rejects the message during decode and reports an invalid-message error to the node event listener.

To peek at the message type without performing a full decode, read only byte 0 of the envelope. To peek at the Message ID, read bytes 1 through 8.

Multiple messages MAY be sent sequentially on a single stream. After decoding one message, the next message (if any) begins immediately at the next byte. End-of-stream (FIN) indicates that no further messages will be sent.

## Security Considerations

1. **Payload validation.** Implementations MUST validate all payload fields before processing. Malformed payloads (e.g., a varint that exceeds the remaining payload bytes, a UTF-8 string that is not valid UTF-8, or a Payload Length that exceeds implementation limits) MUST be rejected. The message SHOULD be discarded and the stream MAY be reset.

2. **Message size limits.** Implementations SHOULD enforce a maximum message size to prevent memory exhaustion. A recommended default maximum payload size is 16,777,216 bytes (16 MB). Messages exceeding this limit SHOULD be rejected. The Java reference implementation's stream assembler uses a 64 MB buffer cap for reassembled message bytes.

3. **Notify amplification.** A malicious peer could request acknowledgments for a large number of NOTIFY messages. Implementations SHOULD rate-limit application-level acknowledgments and SHOULD discard malformed NOTIFY payloads.

4. **Application payload integrity.** FUDP6 messages are protected hop-by-hop by FUDP4 transport encryption. Applications that require end-to-end integrity across relays or storage layers SHOULD sign or authenticate their payloads at the application layer.

5. **Encryption.** All FUDP6 messages are transmitted within FUDP streams, which are encrypted at the transport layer (FUDP4). Application-layer encryption of message payloads is outside the scope of this specification but MAY be applied by the application.

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
|FUDP5 (DDoS Defense)|Optional challenge/rate-limit defense before application messaging on public networks.|

## Reference Implementation

The reference implementation is located in the FC-JDK repository under the `fudp` package. Key classes:

- `fudp.message.MessageCodec` -- Encodes and decodes FUDP6 message envelopes.
- `fudp.handler.MessageHandler` -- Dispatches REQUEST, RESPONSE, ERROR, PING, and PONG messages.
- `fudp.node.FudpNode` -- Reassembles message bytes from streams and handles NOTIFY, NOTIFY_ACK, and PING/PONG convenience behavior.
- `fudp.node.MessageFrameAssembler` -- Extracts complete FUDP6 envelopes from chunked stream data.
