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

#### Request Timeout

A requester waiting for a RESPONSE SHOULD use an **idle-based** deadline, not a fixed wall-clock deadline: the timeout fires only after a configured period with no activity from the responder, and the deadline is refreshed while the exchange is demonstrably progressing. Two signals count as activity, and both MUST refresh the deadline:

1. **Inbound stream data** from the responder — the RESPONSE is arriving. This keeps a large or slow download alive for as long as bytes keep flowing.
2. **Inbound ACK frames** from the responder — the REQUEST is still being uploaded and the responder is acknowledging it. A large request produces no inbound stream data until the transfer completes, so without this signal any upload that takes longer than the idle budget times out while transferring normally (the requester observes "no response data" even though the responder is receiving steadily and will respond once the upload finishes).

A fixed deadline (`future.get(timeout)`) is only acceptable when the request and response are both known to be small relative to the timeout. Implementations MAY additionally scale the initial idle budget with the request payload size (the Java reference implementation adds 1 second per 100 KB) to cover responder-side processing of large uploads.

Under this rule a request fails only on true silence — an unreachable peer, a dead connection, or a responder that stopped acknowledging — rather than on slow but healthy transfers. Since ACKs stop when retransmissions are exhausted or the path goes down, the timeout remains bounded in genuine failure cases.

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

To peek at the message type without performing a full decode, read only byte 0 of the envelope. To peek at the Message ID, read bytes 1 through 8. This partial-read capability is what lets a receiver route even a very large, disk-spilled message (§Receive-Side Memory Management) without loading it: the fixed header and the payload's own small leading fields are read from the front of the reassembled bytes, and the bulk payload is left in backing storage.

Steps 5–6 describe the payload as bytes to be read and then decoded; an implementation SHOULD decode the payload directly from the reassembled buffer's payload slice rather than copying it out first (§Receive-Side Memory Management).

Multiple messages MAY be sent sequentially on a single stream. After decoding one message, the next message (if any) begins immediately at the next byte. End-of-stream (FIN) indicates that no further messages will be sent.

## Security Considerations

1. **Payload validation.** Implementations MUST validate all payload fields before processing. Malformed payloads (e.g., a varint that exceeds the remaining payload bytes, a UTF-8 string that is not valid UTF-8, or a Payload Length that exceeds implementation limits) MUST be rejected. The message SHOULD be discarded and the stream MAY be reset.

2. **Message size limits.** Implementations SHOULD enforce a maximum message size to prevent memory exhaustion. Because a stream carries exactly one message per FUDP2's model, and applications built on FUDP MAY use large single-message transfers (e.g. whole-file uploads), the maximum assembled message size MUST be configurable up to the largest message the application layer is expected to accept — a fixed cap lower than that ceiling silently and permanently fails every oversized transfer (§Reassembly Failure Mode below), rather than rejecting it cleanly at the application layer where a size limit belongs.

   Two distinct limits SHOULD be distinguished, so that supporting large transfers does not force a correspondingly large heap footprint (§Receive-Side Memory Management below):

   - an **in-memory reassembly cap** (recommended default 16,777,216 bytes / 16 MB), which bounds how much of a single message is buffered on the heap; and
   - a **hard maximum assembled-message size**, which bounds a single message overall (heap plus any spill-to-disk buffering) and MAY be as large as the application's file-size policy.

   The hard maximum MUST be derived independently of the in-memory cap — for example from the application's configured maximum file size — and MUST NOT be used to size an in-memory buffer, since doing so makes a large file-size policy allocate a correspondingly large heap buffer. (An earlier revision of the Java reference implementation derived a single cap as `max(64 MB, configuredMaxFileSize + 1 MB)` and used it to size the in-memory reassembly buffer; because the default file-size policy is 1 GB, this admitted ~1 GB single-message heap allocations and caused out-of-memory failures on memory-constrained receivers. The cap is now split into the two limits above.)

#### Reassembly Failure Mode (informative)

If a message's total length, once known from its envelope's Payload Length field, exceeds the receiver's configured cap, the message can never be fully assembled — the sender will retransmit indefinitely (bounded by FUDP3's Max Retransmit Count) while the receiver discards or refuses to buffer the excess, and the sender's request eventually times out with no diagnostic information available on the receiving side unless the receiver explicitly detects and logs the condition. Implementations SHOULD detect this condition as early as possible (as soon as the Payload Length field has been parsed, before buffering payload bytes it can never use) and log it at a visible severity identifying the stream and declared size, since without such logging an oversized-message failure is indistinguishable at the transport layer from ordinary packet loss.

#### Reassembly Performance (informative)

A reassembly implementation that grows its buffer by copying the entire accumulated buffer into a new array on every incoming chunk exhibits O(total_size²) total work per message, because each of the O(total_size / chunk_size) chunks triggers an O(total_size) copy. This is invisible on small messages but becomes the dominant cost on multi-megabyte transfers: on a single-threaded receive path, the copy cost eventually exceeds the time available to keep up with incoming packets, delaying the ACKs that loss detection (FUDP3 §2) depends on and triggering timeout-based false-loss detection purely as a side effect of receiver-side CPU cost, not network conditions. Implementations SHOULD instead use an in-place growable buffer (amortized O(chunk) append, doubling capacity as needed) and parse the message's declared total length from its envelope header ONCE per message rather than re-parsing it from the accumulating buffer on every chunk, so that per-chunk cost is O(chunk_size) rather than O(total_size_so_far).

#### Receive-Side Memory Management (informative)

The reassembly and decode of a single message can, if done naively, hold several full-size copies of the message on the heap simultaneously — the reassembly buffer, a copy of the extracted message, a copy of the extracted payload, and finally the application's own copy. On a memory-constrained receiver a large message (tens of MB and up) can exhaust the heap even though only one copy is logically needed. Two independent techniques bound this:

- **Decode in place (avoid the payload copy).** After the envelope header has been parsed, the payload occupies a known `[offset, offset+length)` slice of the reassembled buffer. Implementations SHOULD decode message-type payloads directly from that slice rather than first copying the payload into a fresh array and decoding from the copy. Each message type still makes exactly the one unavoidable copy of the bytes it must own; the intermediate whole-payload copy is eliminated, roughly halving peak decode-time memory for large messages. Length-prefixed fields inside a payload (service-name length, data length, error-message length, etc.) MUST be validated against the remaining slice bounds before allocation, both to reject truncated/corrupt input and to avoid a huge or negative allocation driven by a corrupt length field.

- **Spill large messages to disk (bound the heap).** A message whose declared total length exceeds the in-memory reassembly cap (§Security Considerations 2) SHOULD be streamed to backing storage (e.g. a temp file) as its chunks arrive, rather than accumulated on the heap, so that the receiver's heap footprint per in-flight transfer is bounded by roughly one packet regardless of the message's size. A message at or below the cap stays entirely in memory (the fast path). The completeness rules are unchanged: the message is delivered only once all its bytes are present.

  When a message has been spilled, implementations MAY deliver it to the application as a *file-backed* payload — the small framing/header fields read from the front of the spilled data, and the bulk payload exposed as a stream over the backing storage — so the application can copy it to its final destination (e.g. a download's output file) without ever materialising the whole payload in memory. For backward compatibility, a byte-array accessor MAY lazily materialise the spilled payload on first access; consumers of potentially large payloads SHOULD prefer the streaming accessor.

  Backing-storage lifecycle: the spilled temp file MUST be reclaimed once the message is consumed, and also when no consumer claims it (e.g. the corresponding request already timed out). Implementations SHOULD additionally purge orphaned spill files at startup and periodically sweep stale ones, so that a crash or an abandoned transfer cannot leak storage.

This is a receiver-local implementation concern: the wire format is unchanged, and whether a message travelled in memory or via disk on the receiver is not observable to the sender.

3. **Notify amplification.** A malicious peer could request acknowledgments for a large number of NOTIFY messages. Implementations SHOULD rate-limit application-level acknowledgments and SHOULD discard malformed NOTIFY payloads.

4. **Application payload integrity.** FUDP6 messages are protected hop-by-hop by FUDP4 transport encryption. Applications that require end-to-end integrity across relays or storage layers SHOULD sign or authenticate their payloads at the application layer.

5. **Encryption.** All FUDP6 messages are transmitted within FUDP streams, which are encrypted at the transport layer (FUDP4). Application-layer encryption of message payloads is outside the scope of this specification but MAY be applied by the application.

## Versioning

|Ver|Date|Changes|
|---|---|---|
|1|2026-03-28|Initial specification.|
|1 (rev)|2026-07-13|Added Request Timeout: requester deadlines MUST be idle-based and refreshed by both inbound stream data (response arriving) and inbound ACK frames (request still uploading). Discovered via large disk.carve uploads on slow links always timing out at the fixed 30s deadline while the transfer was progressing normally.|
|1 (rev)|2026-07-14|Message size limits: the reassembly buffer cap MUST be configurable up to the application's largest expected message rather than a fixed 64 MB, and oversized-message detection SHOULD be logged rather than failing silently. Added informative notes on the O(n²) reassembly performance pitfall (whole-buffer copy per chunk delays ACKs and causes false-loss detection) and its fix (in-place growable buffer, header length parsed once). Discovered via multi-megabyte uploads whose effective throughput decayed as the transfer progressed.|
|1 (rev)|2026-07-17|Receive-side memory: split the single reassembly cap into an in-memory cap (heap footprint, default 16 MB) and a hard maximum assembled-message size (bounds a single message overall, MAY equal the file-size policy) — the hard maximum MUST NOT size an in-memory buffer. Added the Receive-Side Memory Management note: decode the payload in place from the reassembled slice (eliminating the intermediate whole-payload copy) with per-field length-bound validation, and spill messages above the in-memory cap to disk with optional file-backed delivery and temp-file lifecycle rules. Wire format unchanged. Discovered via an out-of-memory crash decoding a ~52 MB response on a mobile receiver whose cap had been sized from the 1 GB file-size policy.|

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
- `fudp.node.MessageFrameAssembler` -- Extracts complete FUDP6 envelopes from chunked stream data using an in-place growable buffer (amortized O(chunk) append) with the message length parsed once from the envelope header per message; configurable buffer cap (`max(64 MB, configured max file size + 1 MB)` in the FAPI disk-transfer profile) rather than a fixed constant.
