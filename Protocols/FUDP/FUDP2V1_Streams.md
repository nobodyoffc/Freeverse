# FUDP2V1_Streams

## Contents

[Summary](#summary)

[Abstract](#abstract)

[Motivation](#motivation)

[Specification](#specification)

- [Stream ID Encoding](#stream-id-encoding)
- [Stream States](#stream-states)
- [Stream Data Transmission](#stream-data-transmission)
- [Data Reassembly](#data-reassembly)
- [Flow Control](#flow-control)
- [Stream Opening](#stream-opening)
- [Stream Closing](#stream-closing)

[Security Considerations](#security-considerations)

[Versioning](#versioning)

[Related Protocols](#related-protocols)

[Reference Implementation](#reference-implementation)

---

## Summary

|Field|Content|
|---|---|
|Title|Streams|
|Type|FUDP|
|SN|2|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-03-28|
|PID||

## Abstract

FUDP2 defines stream multiplexing, flow control, and data reassembly for the FUDP transport protocol. Multiple independent streams are multiplexed over a single FUDP connection, each with its own data ordering, flow control limits, and lifecycle. This specification covers stream identification, state management, in-order data delivery, and two-level flow control (stream-level and connection-level).

## Motivation

A single FUDP connection between two peers often carries multiple concurrent logical data flows -- for example, a chat message, a file transfer, and a control signal may all be in progress simultaneously. Without stream multiplexing, these flows would either require separate connections (costly in handshake overhead and state) or share a single byte stream (causing head-of-line blocking).

FUDP2 addresses this by defining lightweight streams within a connection. Each stream is an independent, ordered byte sequence with its own flow control window. Streams can be created and destroyed cheaply, and head-of-line blocking on one stream does not affect others. Flow control at both the stream level and the connection level prevents a fast sender from overwhelming a slow receiver.

## Specification

### Stream ID Encoding

Stream IDs are variable-length integers encoded using the QUIC-style varint encoding defined in FUDP0. The two least significant bits of the stream ID encode the stream type:

|Bit 1|Bit 0|Type|
|---|---|---|
|0|0|Client-initiated, Bidirectional|
|0|1|Server-initiated, Bidirectional|
|1|0|Client-initiated, Unidirectional|
|1|1|Server-initiated, Unidirectional|

New stream IDs within a given type increment by 4, preserving the lower 2 bits. For example, client-initiated bidirectional streams use IDs 0, 4, 8, 12, and so on. Server-initiated bidirectional streams use IDs 1, 5, 9, 13, and so on.

In FUDP's peer-to-peer model, "client" refers to the connection initiator (the peer that sent the first handshake packet) and "server" refers to the responder.

### Stream States

Each stream progresses through a defined set of states. The current state determines which operations are permitted on the stream.

|State|Description|
|---|---|
|IDLE|Stream ID has been allocated but no data has been sent or received.|
|OPEN|Both send and receive directions are active. Data may flow in both directions (bidirectional) or in the permitted direction (unidirectional).|
|HALF_CLOSED_LOCAL|The local endpoint has sent a STREAM frame with the FIN bit set. No further data may be sent locally, but data may still be received from the remote peer.|
|HALF_CLOSED_REMOTE|The remote endpoint has sent a STREAM frame with the FIN bit set. No further data will arrive from the remote peer, but the local endpoint may still send data.|
|CLOSED|Both directions are closed. The stream has completed its lifecycle and its resources may be reclaimed.|

#### State Transitions for Bidirectional Streams

```
         +------+
         | IDLE |
         +------+
             |
             | (first STREAM frame sent or received)
             v
         +------+
         | OPEN |
         +------+
        /        \
       / local    \ remote
      /  sends     \ sends
     /   FIN        \ FIN
    v                v
+-------------------+  +--------------------+
| HALF_CLOSED_LOCAL |  | HALF_CLOSED_REMOTE |
+-------------------+  +--------------------+
    \                /
     \ remote       / local
      \ sends      / sends
       \ FIN      / FIN
        v        v
        +--------+
        | CLOSED |
        +--------+
```

- IDLE -> OPEN: The first STREAM frame is sent or received on this stream.
- OPEN -> HALF_CLOSED_LOCAL: The local endpoint sends a STREAM frame with the FIN bit set.
- OPEN -> HALF_CLOSED_REMOTE: The local endpoint receives a STREAM frame with the FIN bit set from the remote peer.
- HALF_CLOSED_LOCAL -> CLOSED: The local endpoint receives a STREAM frame with the FIN bit set from the remote peer.
- HALF_CLOSED_REMOTE -> CLOSED: The local endpoint sends a STREAM frame with the FIN bit set.

Any transition to CLOSED also occurs immediately if either side sends a RESET_STREAM frame or if the connection itself is closed.

#### State Transitions for Unidirectional Streams

Unidirectional streams permit data flow in only one direction: from the initiator to the receiver.

- The initiator's view: IDLE -> OPEN -> CLOSED (upon sending FIN).
- The receiver's view: IDLE -> OPEN -> CLOSED (upon receiving FIN).

The initiator MUST NOT receive application data on a unidirectional stream it initiated. The receiver MUST NOT send application data on a unidirectional stream initiated by the remote peer.

A single FIN from the initiator closes the stream for both endpoints.

### Stream Data Transmission

Data is carried in STREAM frames (frame types 0x08 through 0x0F, as defined in FUDP1). The frame type byte encodes three flags:

|Bit|Name|Meaning|
|---|---|---|
|0x01|FIN|This is the final data on this stream.|
|0x02|LEN|The frame includes an explicit length field.|
|0x04|OFF|The frame includes an explicit offset field.|

Each stream maintains a monotonically increasing send offset, starting at 0 for the first byte sent on the stream. The offset increments by the number of bytes in each STREAM frame's payload.

The following rules apply:

1. When the offset is 0, the OFF bit MAY be omitted. Receivers MUST treat the absence of the OFF bit as an implicit offset of 0.
2. The LEN bit SHOULD always be set. An explicit length field enables the sender to pack multiple frames into a single UDP packet. The LEN bit MAY be omitted only when the STREAM frame is the last frame in the packet and its payload extends to the end of the packet.
3. The FIN bit MUST be set on the final STREAM frame for a given direction. It indicates that no further data will be sent on this stream (or this half of a bidirectional stream).
4. A STREAM frame with FIN set MAY carry zero bytes of payload. This is valid and simply signals end-of-stream.

### Data Reassembly

Because FUDP operates over UDP, STREAM frames may arrive out of order, be duplicated, or be lost (and later retransmitted). Receivers MUST implement a reassembly buffer to reconstruct the original byte stream in order.

The reassembly procedure is as follows:

1. **Buffer received frames.** Each received STREAM frame is indexed by its offset and length. The receiver stores the payload in a buffer keyed by the byte range [offset, offset + length).
2. **Detect and discard duplicates.** If a received frame's byte range overlaps with data already buffered or already delivered, the overlapping portion MUST be discarded. Partial overlap (where some bytes are new) is permitted; the receiver extracts and buffers only the new bytes.
3. **Assemble contiguous data.** Starting from the next expected offset (initially 0), the receiver assembles the longest contiguous run of buffered bytes.
4. **Deliver to the application.** The assembled contiguous data is delivered to the application layer in order. Data MUST NOT be delivered out of order or with gaps.
5. **Advance the expected offset.** After delivery, the next expected offset advances by the number of bytes delivered.
6. **Detect stream completion.** If the FIN bit has been received and all bytes up to and including the final offset have been delivered, the receive side of the stream is complete.

Implementations SHOULD bound the size of the reassembly buffer. If a peer sends data that would cause the buffer to exceed a reasonable limit, the receiver MAY close the stream or the connection with an appropriate error.

### Flow Control

FUDP implements two levels of flow control to prevent a fast sender from overwhelming a slow receiver. Both levels operate on byte counts. Flow control does not apply to control frames -- only to STREAM frame payload bytes.

#### Stream-Level Flow Control

Each stream has a maximum data limit, expressed as a byte offset. The receiver advertises its willingness to accept data via MAX_STREAM_DATA frames (as defined in FUDP1).

|Parameter|Default Value|Description|
|---|---|---|
|Initial Max Stream Data|100,000,000 bytes (100 MB)|The initial per-stream byte limit, representing the maximum offset the sender is permitted to reach.|
|Expansion Trigger|50% consumed|When the number of bytes consumed (delivered to the application) reaches 50% of the current limit, the receiver doubles the limit.|

The sender MUST NOT send data on a stream that would cause the stream's maximum offset to exceed the limit last advertised by the receiver. If the sender has data to send but is blocked by the stream-level limit, it MUST wait until the receiver sends a new MAX_STREAM_DATA frame with a higher limit.

When the receiver's consumed byte count reaches 50% of the current limit, the receiver SHOULD send a MAX_STREAM_DATA frame with a new limit equal to twice the current limit. This automatic expansion ensures that a steady data flow is not stalled by flow control under normal conditions.

#### Connection-Level Flow Control

All streams within a connection share a connection-level byte limit. The receiver advertises this limit via MAX_DATA frames (as defined in FUDP1).

|Parameter|Default Value|Description|
|---|---|---|
|Initial Max Data|10,485,760 bytes (10 MB)|The initial connection-level byte limit, representing the maximum total bytes across all streams.|
|Expansion Trigger|50% consumed|When the total consumed bytes across all streams reaches 50% of the current limit, the receiver doubles the limit.|

A sender MUST NOT send data that would cause the total bytes sent across all streams to exceed the connection-level limit, even if individual stream-level limits would permit it. The connection-level limit acts as a global cap.

When the total consumed bytes across all streams reaches 50% of the connection-level limit, the receiver SHOULD send a MAX_DATA frame with a doubled limit.

#### Interaction Between Stream and Connection Flow Control

A STREAM frame is permitted only if both of the following conditions are satisfied:

1. The stream-level offset after sending does not exceed the stream's MAX_STREAM_DATA limit.
2. The total connection-level byte count after sending does not exceed the connection's MAX_DATA limit.

If either condition is not met, the sender MUST buffer the data until the corresponding limit is raised by the receiver.

#### Stream Count Limits

The maximum number of concurrent streams is controlled by MAX_STREAMS frames (as defined in FUDP1).

|Parameter|Default Value|Description|
|---|---|---|
|Max Local Streams|100|Maximum number of streams that may be initiated by the local endpoint.|
|Max Remote Streams|100|Maximum number of streams that may be initiated by the remote endpoint.|

These limits apply independently to bidirectional and unidirectional streams. A peer that wishes to open a stream beyond the current limit MUST wait until the remote peer sends a MAX_STREAMS frame with a higher limit.

### Stream Opening

Streams are created lazily. A stream comes into existence when the first STREAM frame referencing its stream ID is sent or received. There is no explicit "open stream" handshake.

The following rules govern stream creation:

1. Locally initiated streams use even-numbered base IDs: 0, 4, 8, 12, ... (for client-initiated) or 1, 5, 9, 13, ... (for server-initiated), as determined by the two least significant bits of the stream ID.
2. Stream IDs MUST be used in monotonically increasing order within each type. An implementation MUST NOT skip stream IDs. If stream ID N is opened, all streams with IDs less than N of the same type MUST be considered implicitly opened.
3. If a received STREAM frame references a stream ID that does not yet exist locally, the implementation MUST create the stream automatically and transition it to the OPEN state.
4. If creating a new stream would cause the total number of streams of that type to exceed the stream count limit, the implementation SHOULD send a CONNECTION_CLOSE frame with error code STREAM_LIMIT_ERROR and close the connection.

### Stream Closing

A stream is closed by sending a STREAM frame with the FIN bit set. The FIN indicates that no more application data will be sent on that half of the stream.

For bidirectional streams, each direction is closed independently. The stream transitions to CLOSED only after both sides have sent FIN. This permits a pattern where one side finishes sending (FIN) but continues to receive data from the other side (half-closed state).

For unidirectional streams, a single FIN from the initiator closes the stream entirely.

A stream may also be abruptly terminated by sending a RESET_STREAM frame. A RESET_STREAM immediately transitions the stream to CLOSED, discarding any unsent or buffered data. The receiver of a RESET_STREAM SHOULD discard any buffered data for that stream and signal an error to the application.

### Error Handling

The following error conditions are defined for stream operations:

|Error Code|Name|Description|
|---|---|---|
|0x04|STREAM_LIMIT_ERROR|A peer attempted to open more streams than the permitted maximum.|
|0x05|STREAM_STATE_ERROR|A frame was received that is not permitted in the current stream state (e.g., data on a closed stream).|
|0x03|FLOW_CONTROL_ERROR|A peer sent data that exceeds the advertised flow control limit (stream-level or connection-level).|

Upon detecting a flow control violation or stream limit violation, an implementation MUST close the connection by sending a CONNECTION_CLOSE frame with the appropriate error code.

Upon detecting a stream state error, an implementation SHOULD send a RESET_STREAM frame for the affected stream. If the error is severe or repeated, the implementation MAY close the connection.

## Security Considerations

1. **Resource exhaustion.** A malicious peer could attempt to open a large number of streams or send data beyond flow control limits to exhaust the receiver's memory. Implementations MUST enforce stream count limits and flow control limits strictly. Violations MUST result in connection closure.

2. **Stream ID predictability.** Stream IDs follow a deterministic pattern. This is by design and does not constitute a security weakness, as all FUDP data packets are encrypted (FUDP4). An observer cannot determine stream IDs from the ciphertext.

3. **Reassembly buffer limits.** Implementations SHOULD impose a maximum size on per-stream reassembly buffers. A peer that sends widely scattered offsets (e.g., offset 0 and offset 1,000,000,000 with nothing in between) could force the receiver to allocate excessive memory. Implementations MAY close the stream or connection if the reassembly buffer grows beyond a configured threshold.

4. **Flow control manipulation.** A receiver that never advances its flow control limits can stall a sender indefinitely. This is expected behavior (backpressure), not an attack. However, implementations SHOULD expose flow control stalls to the application layer so that higher-level timeouts can be applied.

## Versioning

|Ver|Date|Changes|
|---|---|---|
|1|2026-03-28|Initial specification.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FUDP0 (FUDP)|Foundational rules, varint encoding, conformance requirements.|
|FUDP1 (Core Transport)|Defines STREAM frame format (types 0x08-0x0F), MAX_STREAM_DATA, MAX_DATA, MAX_STREAMS, RESET_STREAM, and CONNECTION_CLOSE frame formats referenced by this specification.|
|FUDP3 (Loss & Congestion)|Handles retransmission of lost STREAM frames and congestion control that affects send rates on streams.|
|FUDP4 (Security)|All STREAM frame data is encrypted. Security handshake must complete before streams can carry application data.|

## Reference Implementation

The reference implementation is located in the FC-JDK repository under the `fudp` package. Key classes:

- `fudp.connection.ConnectionContext` -- Manages stream state and flow control within a connection.
- `fudp.connection.PeerConnection` -- Handles stream multiplexing over a peer connection.
- `fudp.handler.MessageHandler` -- Processes incoming STREAM frames and performs data reassembly.
- `fudp.node.FudpNode` -- Top-level node that manages connections and their associated streams.
