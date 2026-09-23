# FUDP7V1_Datagram

|Field|Content|
|---|---|
|Title|Datagram|
|Type|FUDP|
|SN|7|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-09-23|
|PID||

## Contents

- [Abstract](#abstract)
- [Summary](#summary)
- [1. Introduction](#1-introduction)
- [2. DATAGRAM Frame](#2-datagram-frame)
  - [2.1. Encoding](#21-encoding)
  - [2.2. Size Limit](#22-size-limit)
  - [2.3. Packing](#23-packing)
- [3. Capability](#3-capability)
- [4. Sending](#4-sending)
  - [4.1. Send or Drop](#41-send-or-drop)
  - [4.2. Rate Budget](#42-rate-budget)
  - [4.3. Send Outcomes](#43-send-outcomes)
- [5. Loss, ACKs and Congestion](#5-loss-acks-and-congestion)
- [6. Receiving](#6-receiving)
- [7. Security](#7-security)
- [8. Priority Is Sender-Side Only](#8-priority-is-sender-side-only)
- [9. Test Vectors](#9-test-vectors)
- [Versioning](#versioning)
- [10. References](#10-references)

## Abstract

This document specifies the DATAGRAM frame for FUDP: an application payload that is encrypted like every other frame but delivered at most once, never retransmitted, and never delayed behind reliable data. It is intended for real-time media, where a late packet is worthless and retransmitting it only adds delay. It is implemented alongside FUDP1 (Core Transport), FUDP3 (Loss and Congestion) and FUDP4 (Security).

## Summary

- A DATAGRAM frame (type `0x10`) carries a length and opaque bytes. It MUST fit in one packet; it is never fragmented.
- It is not retransmitted, not ack-eliciting, and a packet carrying only DATAGRAM, ACK and PADDING frames is not counted in bytes in flight.
- The sender sends it at once or drops it. It bypasses the congestion window and pacing, and never waits for the socket. A per-connection rate budget replaces congestion control.
- A sender MUST NOT send DATAGRAM frames on a connection until the application has established that the peer supports them.
- Packets carrying DATAGRAM frames carry a timestamp, so they get the same replay protection as any packet with application data.

## 1. Introduction

Every other FUDP application path — STREAM data, requests, NOTIFYs — is reliable and ordered. For live audio that is the wrong trade: a 40 ms voice frame that arrives 300 ms late is discarded by the receiver anyway, and retransmitting it spends bandwidth that the next frame needed. DATAGRAM gives the application an unreliable path on the same authenticated, encrypted connection it already has, so real-time traffic needs no second handshake and no second port.

The first user is the voice call design (Freer `VOICE_SPEC.md`), which puts one end-to-end encrypted media frame in each DATAGRAM frame. FUDP does not interpret the payload.

## 2. DATAGRAM Frame

### 2.1. Encoding

```
DATAGRAM Frame {
  Type (varint) = 0x10,
  Length (varint),
  Data (Length bytes)
}
```

Varints are the QUIC-style variable-length integers of FUDP1. `Length` MAY be zero.

`0x10` is the first value after the STREAM range (`0x08`–`0x0F`). The values `0x06` and `0x07` belonged to removed frame types and are reserved; they MUST NOT be reused.

A receiver MUST reject a frame whose `Length` exceeds the bytes remaining in the packet; FUDP1's rule for unparseable frames then applies to the whole packet.

### 2.2. Size Limit

A DATAGRAM frame MUST fit in one packet. The sender refuses any payload that would not fit; it never splits one.

The largest payload is the room for frames in a packet of Max Packet Size (FUDP1 §Packet Size Budget), less the frame's own type and length:

```
room            = maxPacketSize − 21 (header) − 68 (crypto) − 16 (timestamp + session epoch)
maxDatagramSize = largest n such that 1 + varintLength(n) + n ≤ room
```

|Max Packet Size|Max DATAGRAM payload|
|---|---|
|1200|1092|
|1350 (default)|1242|
|1400|1292|
|1500|1392|

The session epoch is budgeted even once it is confirmed and no longer sent, so the limit does not change during a connection. An application can size its payloads once, at connection setup.

### 2.3. Packing

A packet MAY carry several DATAGRAM frames, and a packet MAY mix DATAGRAM frames with other frames. A receiver MUST accept DATAGRAM frames in any position in a packet.

A sender handed several datagrams at once (`sendDatagrams`) SHOULD pack them into as few packets as they fit, in the order given. A relay forwarding to many receivers SHOULD batch what falls due for one receiver within a few milliseconds into one packet: each packet it saves is roughly 100 bytes of header, crypto and prefix.

The reference implementation sends datagrams in packets of their own, with at most a pending ACK frame placed ahead of them when it fits whole (FUDP3 §2.1). It does not piggyback datagrams on STREAM packets, so a datagram never waits for a stream packet to be built.

## 3. Capability

A receiver that does not know type `0x10` cannot parse the packet and drops all of it, including any known frames (FUDP1 §Versioning). Nodes deployed before FUDP1's 2026-09-22 revision also count such packets as decrypt failures, and after five in a row block every packet from that address for a second, reliable traffic included.

Therefore:

- A sender MUST NOT send a DATAGRAM frame on a connection until it knows the peer supports DATAGRAM.
- Support is established by the application, per connection: from an application-level exchange that only DATAGRAM-capable peers make (for example a response field saying so, or signalling that only capable clients send). It MUST NOT be assumed from the protocol version, which DATAGRAM does not change.
- Until the application enables it, the send call refuses every datagram with `NOT_ENABLED` and nothing goes on the wire. The reference API is `enableDatagrams(connectionId)`.
- When a peer restart is detected on a connection (FUDP4), the capability MUST be cleared. The restarted peer may run different software, and the application must establish support again.

A node's PONG `info` payload MUST NOT be used as the capability signal: it describes the node, not what a particular connection has agreed to.

## 4. Sending

### 4.1. Send or Drop

A datagram is sent the moment it is handed over, or dropped. It is never queued inside FUDP.

- It is not subject to the congestion window gate or rate-based pacing (FUDP3 §5.4).
- If the socket's send buffer is full, the datagram is dropped immediately. The sender MUST NOT wait for the buffer to drain, as it may for bulk data (FUDP3, Send-Buffer Backpressure).
- Nothing is retried. A dropped datagram is gone; the application decides whether to send a newer one.

### 4.2. Rate Budget

Because datagrams bypass congestion control, each connection has a datagram budget instead: a token bucket counted in DATAGRAM **payload** bytes, not packet bytes.

|Parameter|Value|
|---|---|
|Default rate|256 kbps|
|Burst|100 ms of the rate, and never less than 1500 bytes, so one maximum-size datagram always fits an idle budget|

- A datagram that does not fit the budget is dropped at the sender (`OVER_BUDGET`). It is never delayed.
- The application MAY change the rate per connection. A relay that forwards several speakers to each receiver may raise its own sending rate, for example to 1 Mbps.
- Adapting to the path — lowering the bitrate on loss — is the application's job, using its own feedback. FUDP does not throttle datagrams on loss.

### 4.3. Send Outcomes

Every send reports its outcome for each datagram:

|Outcome|Meaning|
|---|---|
|`SENT`|Handed to the socket.|
|`NOT_ENABLED`|The capability has not been established on this connection (§3).|
|`TOO_LARGE`|Larger than the maximum payload (§2.2).|
|`OVER_BUDGET`|Over the connection's rate budget (§4.2).|
|`BUFFER_FULL`|The socket send buffer was full (§4.1).|
|`NO_CONNECTION`|No open connection, or it is closing.|

## 5. Loss, ACKs and Congestion

These rules are also stated in FUDP3 where they touch its mechanisms.

- **Not retransmitted.** A DATAGRAM frame is never retransmitted, whether or not its packet is acknowledged.
- **Not ack-eliciting.** A packet containing only DATAGRAM, ACK and PADDING frames does not elicit an ACK. A packet that also carries an ack-eliciting frame elicits one as usual.
- **Not tracked.** Such a packet is not recorded among the sender's sent packets and is not counted in bytes in flight (FUDP3 §5.5). It does not move the congestion window.
- **ACK ranges.** A receiver MAY list these packet numbers in an ACK it sends anyway, and SHOULD, so that ranges have holes only where packets were lost (FUDP3 §2.1). A sender MUST ignore ACK ranges covering packet numbers it does not track.
- **Loss detection and RTT.** Untracked packets consume packet numbers. The reordering gap MUST be counted in tracked packets, not packet numbers, and RTT MUST be sampled from the largest newly acknowledged *tracked* packet (FUDP3 §3.2, §4.1.1). Otherwise a DATAGRAM flow makes reliable packets look lost.
- **Timestamp.** A packet carrying a DATAGRAM frame MUST carry a timestamp (FUDP1), so it has full replay protection (FUDP4).
- **Idle timeout.** Datagram traffic counts as activity for the connection's idle timeout.

## 6. Receiving

- Received datagrams are handed to the application as `onDatagram(peerId, connectionId, data)`, in arrival order.
- FUDP gives no ordering and no deduplication beyond the packet replay window (FUDP4). An application that needs either numbers its own payloads.
- In the reference implementation the callback runs on the node's single receive thread. It MUST return quickly; slow work there delays every packet the node receives. A relay may forward inline in the callback.

## 7. Security

- A DATAGRAM frame is encrypted and authenticated with its packet like any other frame (FUDP4). An observer of the path sees only packet sizes and timing.
- Encryption is hop by hop. A relay that terminates the connection can read the DATAGRAM payload; an application that relays through third parties MUST add its own end-to-end protection, as the voice call design does.
- A packet that decrypts correctly but cannot be parsed MUST NOT be counted as a decrypt failure (FUDP1 §Versioning, FUDP4).
- The rate budget (§4.2) bounds what a sender puts on a path. A receiver or relay SHOULD also bound what it accepts per peer, since a peer that ignores the budget can still send.

## 8. Priority Is Sender-Side Only

DATAGRAM frames never wait behind stream data **at the sender**. They do not get priority anywhere else. A bulk transfer on the same path still fills queues further along — the receiver's socket buffer, a bottleneck router — because loss-based congestion control (FUDP3 §5.3) backs off only once such a queue overflows, and datagrams then wait in it. On loopback, a 32 MB upload on the same connection put datagram delay at p99 ≈ 0.5 s.

An application that needs low delay while bulk data flows SHOULD cap stream traffic for that time with the stream rate cap (FUDP3 §5.4.3, `setStreamRateCap`) or pause bulk transfers. With the same upload capped at 8 Mbit/s, datagram delay stayed at its idle level. A delay-based limit on streams inside FUDP is future work.

## 9. Test Vectors

Cross-implementation vectors are generated by FreerForMac's `tools/vector-gen` into `fudpVectors.json`, and checked by FC-JDK (`DatagramVectorTest`), FC-AJDK (`DatagramVectorTest`) and FCTransport (`DatagramFrameTests`). They cover:

- `datagram_frame`: payloads of 0, 1, 3, 63, 64, 160, 1242, 1292, 16383 and 16384 bytes, straddling the varint length boundaries.
- `datagram_payload`: plaintext payloads mixing DATAGRAM with ACK, STREAM and PADDING frames, with the datagrams expected and whether the packet is ack-eliciting.
- `datagram_max_size`: the maximum payload for packet sizes 1200, 1350, 1400 and 1500 (the table in §2.2).

For example, a 3-byte datagram `03 04 05` encodes as `10 03 03 04 05`, and a 64-byte one begins `10 40 40`.

## Versioning

DATAGRAM does not change the packet header version. It is a new frame type under FUDP1's rule that new types MAY be added without a version change but MUST NOT be sent before the peer's support is known (§3).

|Ver|Date|Changes|
|---|---|---|
|1|2026-09-23|Initial specification. Wire format frozen with the voice call Phase 1 gate.|

## 10. References

- FUDP0V1_FUDP -- FUDP protocol overview.
- FUDP1V1_CoreTransport -- Packet format, frame types, packet size budget, unknown frame rules.
- FUDP3V1_LossAndCongestion -- ACK generation, loss detection, bytes in flight, stream rate cap.
- FUDP4V1_Security -- Packet encryption, replay protection, decrypt-failure limiting.
- Freer `VOICE_SPEC.md` -- The voice call design that uses DATAGRAM.
- RFC 9221 -- An Unreliable Datagram Extension to QUIC (the model for this frame).
- RFC 2119 -- Key words for use in RFCs to Indicate Requirement Levels.
