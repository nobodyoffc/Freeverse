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
- [Stream Retirement](#stream-retirement)

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

A single FUDP connection between two peers often carries multiple concurrent logical data flows -- for example, a request, a response, a notification, and a keepalive may all be in progress simultaneously. Without stream multiplexing, these flows would either require separate connections (costly in handshake overhead and state) or share a single byte stream (causing head-of-line blocking).

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

#### Parity Assignment (bit 0)

Both endpoints on a connection allocate locally opened stream IDs from independent counters that share ONE number space (local IDs and remote-created IDs both live in `streams`, keyed by the same `long`). If both endpoints started their counter at the same base value with the same parity, the two allocators would race to reuse the same IDs. Historically this was avoided only by convention — each side happened to advance its counter in lockstep with the other (one response per request) — which breaks under any desync: a connection object rebuilt without a full restart (see FUDP1, Path Migration), a retried request, or simply two requests in flight at once. When one endpoint allocated an ID the other had already used and *retired* (FUDP2, Stream Retirement), the retirement tombstone silently dropped every frame of the new stream — a request could be fully processed and answered server-side while the requester observed nothing but a timeout, with no error logged on either end.

Implementations MUST assign each endpoint's local allocator a fixed parity for bit 0 that is derived **deterministically from identity comparison, not from connection-establishment role**, so that no negotiation or handshake state is required and the assignment is stable across reconnects and connection rebuilds:

```
PROCEDURE assign_local_stream_parity(localFid, remoteFid):
    IF localFid < remoteFid (lexicographic/byte comparison):
        RETURN 0   -- this endpoint allocates even-numbered local stream IDs
    ELSE:
        RETURN 1   -- this endpoint allocates odd-numbered local stream IDs
```

This guarantees the two endpoints' local allocators are permanently disjoint (one strictly even, one strictly odd) regardless of which side initiated the connection, how many times a connection is rebuilt, or how requests interleave. It MUST be applied before the first locally opened stream on a connection, and is idempotent (a later call with the same peer pair is a no-op) since a connection's peer identities never change mid-connection.

Note this diverges from the initiator/responder framing described above: parity is a property of the *identity pair*, not of who sent the first handshake packet. The Java reference implementation implements this as `StreamManager.initLocalStreamParity(parity)`, invoked from both the outbound `Protocol.connect(...)` path and the inbound `Protocol.handleIncomingPacket(...)` path (so it is set correctly regardless of which side is first to send data), and folds the parity into `nextLocalStreamId`'s starting value (also reapplied on `resetForRestart()`, so a rebuilt connection keeps the same parity instead of colliding with its own prior allocations).

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

Any transition to CLOSED also occurs if the connection itself is closed.

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
2. The LEN bit MUST always be set in released v1 wire behavior. Receivers treat STREAM frames without LEN as protocol violations.
3. The FIN bit MUST be set on the final STREAM frame for a given message or send direction. The Java high-level node API sends one complete message per stream and retires the stream after FIN and full message delivery (see [Stream Retirement](#stream-retirement)).
4. A STREAM frame with FIN set MAY carry zero bytes of payload. This is valid and simply signals end-of-stream.

### Data Reassembly

Because FUDP operates over UDP, STREAM frames may arrive out of order, be duplicated, or be lost (and later retransmitted). Receivers MUST implement a reassembly buffer to reconstruct the original byte stream in order.

The reassembly procedure is as follows:

1. **Buffer received frames.** Each received STREAM frame is indexed by its offset and length. The receiver stores the payload in a buffer keyed by the byte range [offset, offset + length).
2. **Detect and discard duplicates.** If a received frame's byte range overlaps with data already buffered or already delivered, the overlapping portion MUST be discarded. Partial overlap (where some bytes are new) is permitted; the receiver extracts and buffers only the new bytes.
3. **Assemble contiguous data.** Starting from the next expected offset (initially 0), the receiver assembles the longest contiguous run of buffered bytes.
4. **Deliver to the application.** The assembled contiguous data is delivered to the application layer in order. Data MUST NOT be delivered out of order or with gaps.
5. **Advance the expected offset.** After delivery, the next expected offset advances by the number of bytes delivered.
6. **Detect stream completion.** If the FIN bit has been received and all bytes up to and including the final offset have been delivered, the receive side of the stream is complete. Once the delivered message has been handed to the application, the receiver MUST retire the stream ID (see [Stream Retirement](#stream-retirement)) so that late or retransmitted frames cannot re-create the stream and deliver the same message a second time.

   > **Pitfall — out-of-order FIN.** Receiving a STREAM frame with the FIN bit set is NOT by itself stream completion. Under loss and reordering the FIN frame routinely arrives *before* earlier frames of the same stream. The receiver MUST record the end offset carried by the FIN frame (`offset + length`) and treat the receive side as complete only when the contiguous delivery offset has reached that end offset. An implementation that retires the stream upon merely observing a FIN frame will tombstone the stream while data is still missing; every retransmission of the missing frames is then dropped by the retired-stream check while still being acknowledged at the packet level, so the sender stops retransmitting and the message is permanently lost (the receiver's higher-level request/response layer observes only a timeout). The Java reference implementation encodes this rule as `Stream.isRecvComplete()` — true only when a FIN has been seen AND `recvOffset` has advanced to the FIN's end offset.

Implementations SHOULD bound the size of the reassembly buffer. If a peer sends data that would cause the buffer to exceed a reasonable limit, the receiver MAY close the stream or the connection with an appropriate error.

The reassembly described here reconstructs the ordered *byte stream*. The layer above it (FUDP6 §Message Envelope) reassembles that byte stream into application *messages*, and a single message may be far larger than any individual frame (e.g. a whole-file transfer, since a stream carries exactly one message). To keep a large transfer from exhausting the receiver's heap, that message-assembly layer SHOULD bound its in-memory footprint independently of the maximum message size — buffering a message above an in-memory cap to backing storage rather than the heap — as specified in FUDP6 §Security Considerations "Receive-Side Memory Management". This does not change the ordered, gap-free delivery contract above; it only changes where the assembled bytes are held.

### Flow Control

FUDP implements two levels of flow control to prevent a fast sender from overwhelming a slow receiver. Both levels operate on byte counts. Flow control does not apply to control frames -- only to STREAM frame payload bytes.

#### Stream-Level Flow Control

Each stream has a maximum data limit, expressed as a byte offset. The receiver advertises its willingness to accept data via MAX_STREAM_DATA frames (as defined in FUDP1). The Java reference implementation initializes both send and receive stream limits to 100 MB and applies the receive limit to buffered out-of-order data.

> **The receive-buffer limit MUST apply only to out-of-order data.** The limit bounds the memory held in the reassembly buffer, which contains *only* frames that arrived ahead of the next expected offset (in-order data is drained to the application immediately and does not stay buffered). A receiver MUST therefore enforce the limit exclusively against frames whose offset is beyond the current contiguous delivery offset (`offset > recvOffset`). An in-order frame (`offset == recvOffset`) — the gap-filling frame — MUST NOT be rejected on flow-control grounds, because it is drained immediately and typically triggers a large contiguous drain that *reduces* the buffered byte count. Rejecting it deadlocks the transfer: once the buffer is near the limit behind a lost early frame, the retransmitted frame that would fill the gap and free the entire buffer would itself be refused, and (per [Error Handling](#error-handling)) the whole connection torn down as a flow-control violation — killing every other stream on it too. The Java reference implementation encodes this as `if (offset > recvOffset && recvData + len > maxRecvData) throw` in `Stream.onDataReceived`.

|Parameter|Default Value|Description|
|---|---|---|
|Initial Max Stream Data|100,000,000 bytes (100 MB)|The initial per-stream byte limit, representing the maximum offset the sender is permitted to reach.|
|Expansion Trigger|50% consumed|When the number of bytes consumed (delivered to the application) reaches 50% of the current limit, the receiver doubles the limit.|

The sender SHOULD NOT send data on a stream that would cause the stream's maximum offset to exceed the limit last advertised by the receiver. In the Java reference implementation, `Protocol.send(...)` checks the stream send limit for single-call sends; streaming close helpers focus on MTU chunking and pacing. Receivers close the connection on detected receive-buffer flow-control violations.

When the receiver's consumed byte count reaches 50% of the current limit, the receiver MAY send a MAX_STREAM_DATA frame with a new limit equal to twice the current limit. The Java classes expose this expansion logic, but automatic outbound MAX_STREAM_DATA generation is not part of the current transport loop.

#### Connection-Level Flow Control

All streams within a connection share a connection-level byte limit. The receiver advertises this limit via MAX_DATA frames (as defined in FUDP1). The Java reference implementation stores and parses connection-level limits, but does not currently make connection-level flow control the primary send gate.

|Parameter|Default Value|Description|
|---|---|---|
|Initial Max Data|10,485,760 bytes (10 MB)|The initial connection-level byte limit, representing the maximum total bytes across all streams.|
|Expansion Trigger|50% consumed|When the total consumed bytes across all streams reaches 50% of the current limit, the receiver doubles the limit.|

A sender SHOULD NOT send data that would cause the total bytes sent across all streams to exceed the connection-level limit, even if individual stream-level limits would permit it. In the Java reference implementation, pacing and stream-level checks are the active controls for large transfers.

When the total consumed bytes across all streams reaches 50% of the connection-level limit, the receiver MAY send a MAX_DATA frame with a doubled limit. The Java classes expose this calculation, but automatic MAX_DATA generation is not currently wired into packet processing.

#### Interaction Between Stream and Connection Flow Control

A STREAM frame is fully flow-control-compliant only if both of the following conditions are satisfied:

1. The stream-level offset after sending does not exceed the stream's MAX_STREAM_DATA limit.
2. The total connection-level byte count after sending does not exceed the connection's MAX_DATA limit.

If either condition is not met, the sender SHOULD buffer the data until the corresponding limit is raised by the receiver.

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

1. Locally initiated streams use base IDs 0, 4, 8, 12, ... or 1, 5, 9, 13, ..., as determined by the two least significant bits of the stream ID. Which base an endpoint uses is fixed by its parity assignment (see [Parity Assignment (bit 0)](#stream-id-encoding)) — a deterministic function of the two endpoints' identities — not by connection-establishment role.
2. Stream IDs MUST be used in monotonically increasing order within each type. An implementation MUST NOT skip stream IDs. If stream ID N is opened, all streams with IDs less than N of the same type MUST be considered implicitly opened.
3. If a received STREAM frame references a stream ID that does not yet exist locally, the implementation MUST create the stream automatically and transition it to the OPEN state -- unless that stream ID has been retired (see [Stream Retirement](#stream-retirement)), in which case the frame MUST be dropped without creating a stream.
4. If creating a new stream would cause the total number of streams of that type to exceed the stream count limit, the implementation SHOULD send a CONNECTION_CLOSE frame with error code STREAM_LIMIT_ERROR and close the connection. The Java reference implementation currently returns `null` for over-limit remote stream creation and drops the frame.

### Stream Closing

A stream is closed by sending a STREAM frame with the FIN bit set. The FIN indicates that no more application data will be sent on that half of the stream.

For bidirectional streams, each direction is closed independently. The stream transitions to CLOSED only after both sides have sent FIN. This permits a pattern where one side finishes sending (FIN) but continues to receive data from the other side (half-closed state).

For unidirectional streams, a single FIN from the initiator closes the stream entirely.

Abrupt stream termination behavior (RESET-style signaling) is implementation-defined in v1 and is not standardized in this document.

### Stream Retirement

Because streams are created lazily (see [Stream Opening](#stream-opening)) and FUDP3 retransmits lost frames in new packets with new packet numbers, a completed stream that is simply removed from tracking is vulnerable to **duplicate message delivery**: a late or retransmitted STREAM frame arriving after removal would automatically re-create the stream, and — since the high-level node API carries exactly one complete message per stream — the reassembled message would be delivered to the application a second time. For request/response traffic this means the same request is executed (and, in metered services, charged) once per retransmitted copy.

To prevent this, receivers MUST retire a remote stream once its message has been fully delivered:

1. **Retire on completion.** When the FIN bit has been received, all bytes have been delivered, and the assembled message has been handed to the application, the receiver removes the stream from active tracking and records its stream ID in a retired-stream set.
2. **Drop frames for retired streams.** A received STREAM frame referencing a retired stream ID MUST be dropped without re-creating the stream and without delivering any data.
3. **Still acknowledge the packet.** Packet-level acknowledgment (FUDP3) is unaffected: the packet carrying the dropped frame is acknowledged normally, so the sender's retransmission of that data stops. Dropping the frame at the stream layer while acknowledging at the packet layer is what terminates a retransmission storm without re-executing its payload.
4. **Not an error.** Frames for retired streams are an expected consequence of retransmission and reordering. They MUST NOT be treated as STREAM_STATE_ERROR and MUST NOT trigger connection closure.

The following rules keep retirement sound:

- **Completion, not FIN observation.** Retirement MUST be gated on actual receive completion: a FIN frame has been seen AND the contiguous delivery offset has reached the FIN frame's end offset AND the assembled message has been handed to the application. Retiring upon merely observing a FIN frame is incorrect — the FIN may arrive ahead of lost or reordered earlier frames, and premature retirement permanently blackholes the message: the tombstone drops every retransmission of the missing data while packet-level acknowledgments (rule 3 below) simultaneously stop the sender from retrying (see the out-of-order FIN pitfall under [Data Reassembly](#data-reassembly)).
- **Scope.** Only remote-initiated streams whose receive side has completed are retired. Sender-side cleanup of locally opened streams does not retire the ID: in the current Java implementation, local and remote stream IDs are allocated from the same number space (see [Stream ID Encoding](#stream-id-encoding)), so a tombstone on a locally used ID could wrongly block a future legitimate remote stream with the same number.
- **Permanence.** A remote peer allocates stream IDs monotonically and never reuses an ID within a connection, so retirement is permanent for the life of the connection.
- **Bounded memory.** The retired-stream set SHOULD be bounded (the Java reference implementation keeps the most recent 4096 retired IDs in FIFO order). Evicting old entries is safe in practice because retransmissions of very old streams are bounded by the Max Retransmit Count (FUDP3).
- **Peer restart.** On peer restart detection (FUDP1), the retired-stream set MUST be cleared along with the rest of the stream state: the restarted peer's stream IDs begin again at the lowest value, and stale tombstones would wrongly drop its new streams.

### Error Handling

The following error conditions are defined for stream operations:

|Error Code|Name|Description|
|---|---|---|
|0x04|STREAM_LIMIT_ERROR|A peer attempted to open more streams than the permitted maximum.|
|0x05|STREAM_STATE_ERROR|A frame was received that is not permitted in the current stream state (e.g., data on a closed stream).|
|0x03|FLOW_CONTROL_ERROR|A peer sent data that exceeds the advertised flow control limit (stream-level or connection-level).|

Upon detecting a flow control violation or stream limit violation, an implementation MUST close the connection by sending a CONNECTION_CLOSE frame with the appropriate error code.

Upon detecting a stream state error, an implementation SHOULD close the connection with an appropriate CONNECTION_CLOSE error code. Exception: STREAM frames referencing a retired stream ID are an expected artifact of retransmission and MUST be silently dropped, not treated as STREAM_STATE_ERROR (see [Stream Retirement](#stream-retirement)).

## Security Considerations

1. **Resource exhaustion.** A malicious peer could attempt to open a large number of streams or send data beyond flow control limits to exhaust the receiver's memory. Implementations MUST enforce stream count limits and flow control limits strictly. Violations MUST result in connection closure.

2. **Stream ID predictability.** Stream IDs follow a deterministic pattern. This is by design and does not constitute a security weakness, as all FUDP data packets are encrypted (FUDP4). An observer cannot determine stream IDs from the ciphertext.

3. **Reassembly buffer limits.** Implementations SHOULD impose a maximum size on per-stream reassembly buffers. A peer that sends widely scattered offsets (e.g., offset 0 and offset 1,000,000,000 with nothing in between) could force the receiver to allocate excessive memory. Implementations MAY close the stream or connection if the reassembly buffer grows beyond a configured threshold.

4. **Flow control manipulation.** A receiver that never advances its flow control limits can stall a sender indefinitely. This is expected behavior (backpressure), not an attack. However, implementations SHOULD expose flow control stalls to the application layer so that higher-level timeouts can be applied.

## Versioning

|Ver|Date|Changes|
|---|---|---|
|1|2026-03-28|Initial specification.|
|1 (rev)|2026-07-11|Added Stream Retirement: completed remote streams are tombstoned so retransmitted frames cannot re-create them and deliver the same message twice (duplicate request execution). Qualified lazy stream creation and STREAM_STATE_ERROR handling accordingly.|
|1 (rev)|2026-07-13|Made receive-completion detection explicit: retirement MUST be gated on the contiguous delivery offset reaching the FIN frame's end offset, never on FIN observation alone. Documented the out-of-order FIN pitfall (premature retirement + packet-level ACKs permanently blackhole the message) discovered via multi-frame upload failures on lossy networks.|
|1 (rev)|2026-07-14|Added mandatory [Parity Assignment (bit 0)](#stream-id-encoding): each endpoint's local stream ID allocator MUST use a parity derived deterministically from identity comparison, not connection role. Discovered via a field failure where a NAT rebind (FUDP1, Path Migration) restarted one side's allocator, colliding with IDs the peer had already retired and silently swallowing responses.|
|1 (rev)|2026-07-17|Clarified in Data Reassembly that the message-assembly layer above stream reassembly SHOULD bound its in-memory footprint independently of the maximum message size (spilling large messages to backing storage), cross-referencing FUDP6 §Receive-Side Memory Management. Delivery ordering/gap-free contract unchanged.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FUDP0 (FUDP)|Foundational rules, varint encoding, conformance requirements.|
|FUDP1 (Core Transport)|Defines STREAM frame format (types 0x08-0x0F), MAX_STREAM_DATA, MAX_DATA, MAX_STREAMS, and CONNECTION_CLOSE frame formats referenced by this specification.|
|FUDP3 (Loss & Congestion)|Handles retransmission of lost STREAM frames and congestion control that affects send rates on streams.|
|FUDP4 (Security)|All STREAM frame data is encrypted. Security handshake must complete before streams can carry application data.|

## Reference Implementation

The reference implementation is located in the FC-JDK repository under the `fudp` package. Key classes:

- `fudp.connection.ConnectionContext` -- Manages stream state and flow control within a connection.
- `fudp.connection.PeerConnection` -- Handles stream multiplexing over a peer connection.
- `fudp.stream.Stream` -- Per-stream reassembly buffer and receive-completion detection (`isRecvComplete()`: FIN seen and contiguous delivery reached the FIN end offset).
- `fudp.stream.StreamManager` -- Stream lifecycle: lazy creation, removal, and retirement (bounded tombstone set for completed remote streams).
- `fudp.handler.MessageHandler` -- Processes incoming STREAM frames and performs data reassembly.
- `fudp.node.FudpNode` -- Top-level node that manages connections and their associated streams; retires a remote stream after its message is fully delivered.
