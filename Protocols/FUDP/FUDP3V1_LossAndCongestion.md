# FUDP3V1_LossAndCongestion

|Field|Content|
|---|---|
|Title|Loss and Congestion|
|Type|FUDP|
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
- [2. ACK Processing](#2-ack-processing)
  - [2.1. ACK Generation Rules](#21-ack-generation-rules)
  - [ACK Frames MUST Be Redundant, Not Fire-Once](#ack-frames-must-be-redundant-not-fire-once)
  - [2.2. ACK Frame Encoding](#22-ack-frame-encoding)
- [3. RTT Estimation](#3-rtt-estimation)
  - [3.1. Parameters](#31-parameters)
  - [3.2. EWMA Algorithm](#32-ewma-algorithm)
  - [3.3. Retransmission Timeout](#33-retransmission-timeout)
- [4. Loss Detection](#4-loss-detection)
  - [4.1. Loss Detection Algorithm](#41-loss-detection-algorithm)
    - [4.1.1. Gap-Based Detection](#411-gap-based-detection)
    - [4.1.2. Time-Based Detection (Timeout)](#412-time-based-detection-timeout)
  - [4.2. Parameters](#42-parameters)
  - [4.3. Retransmission](#43-retransmission)
- [5. Congestion Control](#5-congestion-control)
  - [5.1. States](#51-states)
  - [5.2. Parameters](#52-parameters)
  - [5.3. CUBIC Algorithm](#53-cubic-algorithm)
  - [5.4. Send Pacing](#54-send-pacing)
    - [5.4.1. Congestion-Window Gate](#541-congestion-window-gate)
    - [5.4.2. Rate-Based Pacing](#542-rate-based-pacing)
    - [Send-Buffer Backpressure](#send-buffer-backpressure)
  - [5.5. Bytes-in-Flight Tracking](#55-bytes-in-flight-tracking)
- [Versioning](#versioning)
- [6. References](#6-references)

## Abstract

This document specifies the loss detection and congestion control mechanisms for the FUDP (Freeverse UDP) protocol. It defines ACK processing rules, round-trip time estimation, time-based loss detection, and CUBIC congestion control. These mechanisms ensure reliable data delivery over unreliable UDP transport while maintaining fair use of network resources. This specification is language-agnostic and intended to be implemented alongside FUDP1 (Core Transport) and FUDP2 (Streams).

## Summary

FUDP3 defines four interrelated subsystems:

1. **ACK Processing** -- Rules for generating and interpreting acknowledgment frames, including immediate and delayed ACK policies. ACK generation MUST be redundant over a trailing retention window, not fire-once, so a single lost ACK packet cannot permanently orphan the packet numbers it covered.
2. **RTT Estimation** -- Exponentially Weighted Moving Average (EWMA) computation of smoothed round-trip time and variance, used as input to loss detection and retransmission timeout.
3. **Loss Detection** -- Gap-based (acknowledgment-evidence) detection with an adaptive reordering threshold as the primary, fast mechanism, backed by an adaptive, backed-off, capped time-threshold as a timeout backstop. Only gap-detected loss is a congestion signal. Rate-limited retransmission with a maximum retransmission count before abandonment.
4. **Congestion Control** -- CUBIC algorithm (computed in MSS/packet units per RFC 8312) with slow start, congestion avoidance, and recovery states, governing the congestion window and bytes-in-flight tracking, gated by a mandatory congestion-window check and rate-based pacing on bulk sends.

## 1. Introduction

FUDP operates over UDP and therefore does not inherit TCP's built-in reliability or congestion control. This specification defines the mechanisms used by the Java reference implementation to acknowledge packets, detect lost packets, estimate network conditions, retransmit lost frames, and pace outgoing data.

The key words "MUST", "MUST NOT", "SHOULD", "SHOULD NOT", and "MAY" in this document are to be interpreted as described in RFC 2119.

All packet and frame wire formats referenced in this document are defined in FUDP1V1_CoreTransport.

## 2. ACK Processing

### 2.1. ACK Generation Rules

Implementations MUST acknowledge every ack-eliciting packet received.

The following frames are classified by their ack-eliciting property:

| Classification | Frame Types |
|---|---|
| NOT ack-eliciting | ACK, PADDING |
| Ack-eliciting | STREAM, CONNECTION_CLOSE, MAX_DATA, MAX_STREAM_DATA, MAX_STREAMS, and all other non-ACK/non-PADDING frame types |

A packet is ack-eliciting if it contains at least one ack-eliciting frame. A packet containing only ACK and/or PADDING frames is NOT ack-eliciting.

Implementations SHOULD send an ACK frame immediately upon receiving an ack-eliciting packet. The ACK threshold is defined as:

| Parameter | Value |
|---|---|
| ACK_THRESHOLD | 1 packet |
| MAX_ACK_DELAY | 10 milliseconds |

If an immediate ACK is not triggered for any reason, a delayed ACK MUST be sent within MAX_ACK_DELAY (10 milliseconds) of receiving the ack-eliciting packet.

#### ACK Frames MUST Be Redundant, Not Fire-Once

ACK frames are themselves carried in non-ack-eliciting packets (§2.1) and are therefore never retransmitted by FUDP3's own loss recovery. If a receiver generates each ACK frame from a working set of "packet numbers received since the last ACK was sent" and clears that set once the frame is built, then a single lost ACK packet **permanently** removes its covered packet numbers from every future ACK the receiver will ever send. The sender has no way to learn those packets were actually received; its loss-detection timer (§4) eventually expires them, and — because this failure mode recurs continuously on any lossy path, not as a one-off — retransmission and the resulting `onLoss()` calls (§5.3) can fire every few seconds indefinitely, pinning the congestion window near its floor even though the path is delivering the large majority of packets. This was observed in the field: a real WAN path measured at ~1 MB/s by an independent transfer (scp) sustained only ~35 KB/s over FUDP because of this exact mechanism.

Implementations MUST instead generate each ACK frame from the set of ALL packet numbers received within a trailing retention window, not only those received since the previous ACK:

```
PROCEDURE onPacketReceived(packetNumber):
    receivedPackets[packetNumber] = now()   -- overwrite is fine; idempotent

PROCEDURE generateAckFrame():
    prune receivedPackets entries older than ACK_RETAIN_MS
    IF no packet number is newer than the last generated frame:
        RETURN null   -- nothing new to report; avoid redundant frames on quiet links
    encode ranges (per §2.2) from ALL currently retained packet numbers, newest first
    RETURN frame
```

| Parameter | Value | Description |
|---|---|---|
| ACK_RETAIN_MS | 4,000 ms | How long a received packet number keeps being re-advertised. MUST comfortably exceed the loss-detection timeout threshold (§4.2) plus one RTT, so a later ACK still reaches the sender before it would falsely expire the packets the lost ACK covered. |
| MAX_RETAINED_PACKET_NUMBERS | 16,384 | Memory bound on the retained set; oldest entries are pruned first. |
| MAX_RANGES_PER_FRAME | 128 | Bound on encoded ranges per frame; a healthy link produces 1-2. |

Re-advertising already-acknowledged packet numbers is intentionally redundant: the sender's `removeSentPacket` is a no-op for packet numbers it has already removed (§4.3), so duplicate acknowledgment is harmless and idempotent. The cost is a few extra bytes per ACK frame on a healthy link (still typically 1-2 ranges); the benefit is that ACK loss stops being a silent, compounding failure.

### 2.2. ACK Frame Encoding

The ACK frame wire format is defined in FUDP1. The ACK frame encodes ranges of acknowledged packet numbers compactly using a largest acknowledged packet number, a range-count field, a first range, and zero or more additional (gap, length) pairs. In the Java reference implementation the range-count field is the total number of ranges, including the first range.

The following pseudocode reconstructs the set of acknowledged packet numbers from an ACK frame:

```
function getAcknowledgedPackets(largest, firstRange, additionalRanges):
    packets = []
    pn = largest

    // First range: packets from largest down to (largest - firstRange)
    for i = 0 to firstRange:
        packets.add(pn - i)
    pn = pn - firstRange - 1

    // Additional ranges
    for each (gap, length) in additionalRanges:
        pn = pn - gap - 1
        for j = 0 to length:
            packets.add(pn - j)
        pn = pn - length - 1

    return packets
```

The `gap` field represents the number of consecutive unacknowledged packet numbers minus one. The `length` field represents the number of consecutive acknowledged packet numbers minus one.

## 3. RTT Estimation

Round-trip time is estimated using an Exponentially Weighted Moving Average (EWMA). The RTT estimate drives both loss detection thresholds and retransmission timeout calculation.

### 3.1. Parameters

| Parameter | Value | Description |
|---|---|---|
| Initial RTT | 50 ms | Used before the first RTT sample is obtained |
| Minimum RTT (MIN_RTT) | 1 ms | Floor applied to all RTT samples |

Implementations MUST initialize `smoothedRtt` to the Initial RTT value and `rttVariance` to Initial RTT / 2 before any samples are collected. The `minRtt` tracker MUST be initialized to positive infinity.

### 3.2. EWMA Algorithm

Upon obtaining a new RTT sample (derived from the time between sending an ack-eliciting packet and receiving an ACK acknowledging it), implementations MUST update their RTT estimate as follows:

```
function updateRtt(latestRtt):
    latestRtt = max(latestRtt, MIN_RTT)

    if latestRtt < minRtt:
        minRtt = latestRtt

    if firstSample:
        smoothedRtt = latestRtt
        rttVariance = latestRtt / 2
        firstSample = false
        return

    rttDiff = abs(smoothedRtt - latestRtt)
    rttVariance = (3 * rttVariance + rttDiff) / 4
    smoothedRtt = (7 * smoothedRtt + latestRtt) / 8
```

The smoothing factor for `smoothedRtt` is 1/8. The smoothing factor for `rttVariance` is 1/4. These values are consistent with established practice in TCP (RFC 6298).

RTT samples SHOULD NOT be generated from packets that were retransmitted, as the implementation cannot determine whether the ACK corresponds to the original or retransmitted packet (retransmission ambiguity). The current Java implementation does not persist an explicit retransmitted flag in `SentPacket`; RTT is sampled when the largest acknowledged packet is removed from the sent-packet table.

### 3.3. Retransmission Timeout

The Retransmission Timeout (RTO) is computed from the smoothed RTT and variance:

```
RTO = smoothedRtt + 4 * rttVariance
RTO = clamp(RTO, 1ms, 60000ms)
```

| Parameter | Value | Description |
|---|---|---|
| Minimum RTO | 1 ms | Lower bound for RTO |
| Maximum RTO | 60,000 ms | Upper bound for RTO (60 seconds) |

The RTO serves as a fallback timer. If no ACK is received within the RTO period, the implementation SHOULD treat the situation as indicative of loss and invoke the loss detection procedure.

## 4. Loss Detection

FUDP uses two complementary loss-detection mechanisms, mirroring QUIC (RFC 9002): **gap-based** detection using acknowledgment evidence (fast, but requires reliable ACKs — see §2.1's ACK redundancy requirement) and **time-based** detection as a backstop for tail loss and dead links. Only gap-based detection MAY be treated as a congestion signal; time-based ("timeout") detection MUST NOT shrink the congestion window, for the reason given in §4.1.2.

### 4.1. Loss Detection Algorithm

#### 4.1.1. Gap-Based Detection

A sent packet is declared lost if a later packet, sent from the same connection, has already been acknowledged while this one has not — i.e. the peer's ACK stream has passed it by. This requires that ACK frames reliably convey "everything received so far," which is why §2.1 mandates redundant (not fire-once) ACK generation: without it, gap-based detection cannot distinguish real loss from an ACK that simply has not arrived yet for an in-order packet.

```
function isLostByGap(packet, largestAcknowledged, packetReorderThreshold, now):
    age = now - packet.sentTime
    return (largestAcknowledged - packet.packetNumber >= packetReorderThreshold)
       and (age > max(20ms, smoothedRtt))
```

The age guard (`age > max(20ms, smoothedRtt)`) exists because reordered packets typically arrive within about one RTT of their in-order peers; requiring the gap to persist for at least one RTT keeps ordinary reordering from being misread as loss before the reordered packet has had a fair chance to arrive.

**Reordering threshold MUST be adaptive.** A fixed `packetReorderThreshold` (QUIC's default is 3) is too tight for paths with deep packet reordering — observed in the field on a heavily load-balanced international route, where the fixed threshold produced a loss "event" roughly every 3-5 seconds even though the path was demonstrably capable of ~900 KB/s (confirmed by an independent transfer over the same path at that moment). Implementations MUST widen the threshold using the strongest available evidence that a "lost" packet was not actually lost — a packet that was declared lost by gap detection and is *subsequently acknowledged* is proof the path reorders at least that deep:

```
ON ackReceived(packetNumber) WHERE packetNumber WAS PREVIOUSLY marked suspected-lost:
    -- Spurious loss detected: widen to the observed reordering extent.
    observedExtent = largestAcknowledgedPacketNumber - packetNumber + 2
    packetReorderThreshold = min(MAX_PACKET_THRESHOLD,
                                  max(packetReorderThreshold + 4, observedExtent))
```

| Parameter | Value | Description |
|---|---|---|
| Initial Packet Reorder Threshold | 6 | Starting value; QUIC's kPacketThreshold (3) plus margin |
| Maximum Packet Reorder Threshold | 64 | Ceiling; beyond this, treat as genuine loss regardless of prior spurious events |

This converges within one or two spurious-loss events on a given path and requires no path characterization or configuration — it is purely reactive to observed evidence.

#### 4.1.2. Time-Based Detection (Timeout)

A sent packet is also declared lost if it remains unacknowledged for longer than a computed time threshold, derived from the current RTT estimate:

```
function detectLostByTimeout(packet, smoothedRtt, rttVariance, minTimeThresholdMs, now):
    timeThreshold = clamp(2.0 * smoothedRtt + 4 * rttVariance,
                           minTimeThresholdMs, MAX_TIME_THRESHOLD_MS)
    effectiveThreshold = timeThreshold << min(packet.retransmitCount, 2)   -- backoff, capped at 4x
    return (now - packet.sentTime) > effectiveThreshold
```

**Timeout-detected loss MUST NOT shrink the congestion window.** On paths with jitter — observed in the field as multi-second RTT spikes on a congested cross-border route — a purely time-based scheme routinely expires the *entire in-flight window at once* on every spike, and each such event was treated as a congestion signal, permanently pinning the window near its floor regardless of the path's actual capacity. Per RFC 9002's PTO (Probe Timeout) model, a timeout is retransmission-worthy (the packet might genuinely be lost, or might simply be slow) but is not congestion evidence the way a gap is: the sender only knows a definite ACK has NOT arrived, not that a *later* packet has been confirmed delivered past it. §4.3 accordingly gates the congestion-loss signal on gap detection having fired during that retransmission cycle.

**Exponential backoff on retransmission count** (`effectiveThreshold` above, capped at 4x the base threshold) prevents a packet that keeps timing out from being retried at a constant rate that never gives a struggling path room to recover — under sustained loss, constant-rate blind retransmissions compete with fresh data for the trickle of packets the path can carry, driving up the retransmit-count-based abandonment (§4.3) of the very packets that need patience, not persistence. The backoff MUST be capped low (2 doublings / 4x, not unbounded) because single-packet messages such as small RPC responses have no gap evidence available and rely on this timer alone; an unbounded backoff would directly inflate their worst-case tail latency.

### 4.2. Parameters

| Parameter | Value | Description |
|---|---|---|
| Minimum Time Threshold | 500 ms | Floor on the timeout threshold. Lowered from a historical 2,000 ms: the higher floor existed only to paper over fire-once ACK false positives (§2.1); with redundant ACKs, the floor can be lowered without reintroducing them, recovering genuine loss roughly 4x faster. |
| Maximum Time Threshold | 4,000 ms | Ceiling on the timeout threshold. Without a ceiling, chaotic RTT samples collected under heavy loss can inflate the threshold so far that lost packets sit unretransmitted for many seconds — with the send window full of them, the sender stalls silently and the application-level idle timer (FUDP6, Request Timeout) fires first. |
| Time Threshold Multiplier | 2.0 | Applied to smoothedRtt in threshold computation |
| RTT Variance Multiplier | 4.0 | Applied to rttVariance in threshold computation; widens the threshold under jitter instead of mass-expiring the flight |
| Retransmit Timeout Backoff Cap | 4x (2 doublings) | Maximum multiplier applied to the timeout threshold via retransmit-count backoff |

### 4.3. Retransmission

When packets are declared lost, the data they carried is retransmitted subject to the following constraints:

| Parameter | Value | Description |
|---|---|---|
| Max Retransmit Rate | 50 packets, AND pacer-rate-derived byte budget (§5.4), per 50 ms cycle | Rate limit to prevent retransmission-induced congestion. The byte budget matters as much as the packet count: 50 back-to-back retransmits (tens of KB) is itself a line-rate burst that a shallow bottleneck buffer clips (see §5.4), so retransmission MUST be paced the same way new data is. |
| Max Retransmit Count | 60 | Maximum retransmissions of the same data before abandonment |

If any data segment has been retransmitted Max Retransmit Count (60) times without successful acknowledgment, the implementation MAY abandon that segment. Closing the entire connection for one undeliverable segment is NOT required. Note that abandoning a segment leaves a permanent gap in its stream, so the receiving request/response layer will observe the message as never completing (a timeout); the count should therefore reflect genuine on-wire transmission attempts. In particular, a datagram dropped because the *local* OS send buffer was full must not be charged as a real attempt — see Send-Buffer Backpressure (§5.4), which prevents such local drops from consuming the retransmit budget in the first place. The count was raised from a historical value of 30 alongside the retransmit-timeout backoff (§4.1.2): with backoff, 60 attempts span minutes of real elapsed time rather than seconds, so abandonment remains a last resort for genuinely dead flows rather than a routine outcome of transient timeout misfires; implementations SHOULD observe that the application-level idle timeout (FUDP6, Request Timeout) still gives up well before this budget is exhausted in practice.

Implementations SHOULD log an abandonment event (connection, peer, and — for STREAM frames — stream ID, offset, length, and FIN bit) at a visible severity: an abandoned STREAM frame silently and permanently blackholes that stream's message, and without an explicit log line the failure is indistinguishable from a hung request at every layer above the transport.

**Congestion-window reduction (`onLoss`, §5.3) MUST be invoked only when at least one packet retransmitted in that cycle was gap-detected (§4.1.1), never for a cycle where every retransmission was timeout-only.** This is the mechanism referenced in §4.1.2: it is what prevents jitter-induced timeout storms from being misread as congestion.

The retransmission procedure is as follows:

1. Remove the old SentPacket record from the tracking data structure. Decrement `bytesInFlight` by the packet size using `onRetransmitRemove`. This removal does NOT grow the congestion window.
2. Re-package the retransmittable frames that were in the lost packet into a new packet. ACK and PADDING frames are not retransmitted. The new packet MUST be assigned a new, monotonically increasing packet number.
3. Send the new packet. Increment `bytesInFlight` via `onSend`. Record the new SentPacket with the current timestamp.

Retransmitted packets carry new packet numbers. The original packet number is permanently retired and MUST NOT be reused.

Because a retransmitted packet has a new packet number, it is NOT filtered by packet-level replay protection at the receiver: the receiver may legitimately be handed the same STREAM frame payload more than once (e.g., when the original packet was delivered but its ACK was lost). Receivers deduplicate this at the stream layer -- within an active stream via offset-based duplicate discard, and after stream completion via stream retirement (see FUDP2, Stream Retirement). A frame for a retired stream is dropped while the packet carrying it is still acknowledged, which is what stops a spurious retransmission storm without re-delivering its payload to the application.

## 5. Congestion Control

FUDP includes a CUBIC congestion controller. CUBIC tracks congestion window state and bytes in flight; loss events reduce the window (subject to the gap-vs-timeout distinction in §4.3). Application bulk-send loops MUST gate on the congestion window (§5.4) so the send rate follows what the network has demonstrated it can carry; retransmissions are exempt from the window (§4.3) since they are loss recovery, not new data.

### 5.1. States

The congestion controller operates in one of three states:

| State | Description |
|---|---|
| SLOW_START | Congestion window grows exponentially with each acknowledged byte. Active from connection start until the window reaches the slow start threshold (`ssthresh`). |
| CONGESTION_AVOIDANCE | Congestion window grows according to the CUBIC function. Active after slow start or after recovery completes. |
| RECOVERY | Entered upon loss detection. The window is reduced multiplicatively. Transitions to CONGESTION_AVOIDANCE when new ACKs arrive. |

The initial state is SLOW_START with `ssthresh` set to positive infinity (effectively unlimited).

### 5.2. Parameters

| Parameter | Value | Description |
|---|---|---|
| Initial Window | 120,000 bytes | Approximately 89 packets at 1,350-byte MTU |
| Minimum Window (MIN_WINDOW) | 14,400 bytes | Approximately 10 packets; floor for congestion window |
| Maximum Window (MAX_WINDOW) | 100,000,000 bytes | 100 MB; ceiling for congestion window |
| Beta | 0.7 | Multiplicative decrease factor on loss |
| C | 0.4 | CUBIC scaling constant |
| MSS | 1,350 bytes | Segment size used to convert the byte-denominated window into CUBIC's packet units (see §5.3) |

The initial window of 120,000 bytes allows rapid ramp-up on modern networks. The minimum window of 14,400 bytes ensures that the connection can always make progress even under persistent loss.

### 5.3. CUBIC Algorithm

RFC 8312's CUBIC growth function is defined in **packet (MSS) units**, not bytes: `K = cbrt(W_max * (1-beta) / C)` is only dimensionally an elapsed-time in seconds when `W_max` is a packet count. Implementations MUST convert the byte-denominated congestion window to MSS units before applying the growth function, and convert the result back to bytes:

**Implementations MUST NOT apply the CUBIC growth formula directly to a byte-denominated window.** Doing so was the Java reference implementation's original (incorrect) behavior, and it silently produces a congestion window that can only ratchet downward: for a representative in-field window of ~137,500 bytes, the byte-denominated `K` computes to roughly 47 **seconds** — meaning the window is frozen at essentially zero growth for the better part of a minute after every loss event, while §4 (even before the fixes described there) still triggers a Beta-multiplicative *reduction* roughly every one to a few seconds under real-world loss and jitter. The two rates are wildly asymmetric: shrink dominates growth by roughly two orders of magnitude, so the window is driven to `MIN_WINDOW` and held there — independent of and prior to any of the loss-detection fixes in §4. This was measured in the field as a sustained ~35 KB/s over a path independently confirmed (via a concurrent unrelated transfer) to carry over 1 MB/s.

The following pseudocode defines the congestion control behavior for each event:

```
// On ACK received: grow the congestion window
function onAck(ackedBytes):
    bytesInFlight -= ackedBytes
    bytesInFlight = max(0, bytesInFlight)

    switch state:
        case SLOW_START:
            cwnd += ackedBytes
            if cwnd >= ssthresh:
                state = CONGESTION_AVOIDANCE
                epochStart = now()
                wMax = cwnd

        case CONGESTION_AVOIDANCE:
        case RECOVERY:
            // CUBIC growth function computed in MSS (packet) units per RFC 8312.
            // At t=0 this evaluates to beta*wMax (the post-loss window), rises
            // back to wMax by t=k (a few seconds for realistic windows), then
            // probes beyond it -- the intended CUBIC shape, on a realistic
            // timescale.
            t = (now() - epochStart) / 1000.0        // elapsed time in seconds
            wMaxPackets = wMax / MSS
            k = cbrt(wMaxPackets * (1 - Beta) / C)
            targetPackets = C * (t - k)^3 + wMaxPackets
            target = targetPackets * MSS

            // Reno-style AIMD floor (~1 MSS per window of ACKed data) so
            // growth never stalls in the curve's flat region near wMax.
            renoIncrement = max(1, MSS * ackedBytes / max(1, cwnd))

            // Growth per ACK is capped at the ACKed byte count (slow-start
            // rate): guards against a large cubic target computed after an
            // idle or application-limited period opening the window in one
            // jump disconnected from recent delivery evidence.
            growth = min(max(renoIncrement, target - cwnd), ackedBytes)
            if growth > 0:
                cwnd += growth

            if state == RECOVERY:
                state = CONGESTION_AVOIDANCE

    cwnd = min(cwnd, MAX_WINDOW)
```

```
// On loss detected: reduce the congestion window
function onLoss():
    wMax = cwnd
    cwnd = cwnd * Beta
    cwnd = max(cwnd, MIN_WINDOW)
    ssthresh = cwnd
    state = RECOVERY
    epochStart = now()
```

```
// On send: track bytes in flight
function onSend(sentBytes):
    bytesInFlight += sentBytes
```

```
// Send permission check exposed by the congestion controller
function canSend(bytes):
    return bytesInFlight + bytes <= cwnd
```

```
// On retransmit removal: adjust bytes in flight without window growth
function onRetransmitRemove(removedBytes):
    bytesInFlight -= removedBytes
    bytesInFlight = max(0, bytesInFlight)
```

The variable `wMax` records the congestion window size at the time of the most recent loss event. The variable `k` is the time period after which the CUBIC function reaches `wMax` again. The CUBIC function grows slowly near `wMax` (probing cautiously near the last known point of congestion) and more aggressively further away from it.

### 5.4. Send Pacing

To prevent bursty transmission patterns that may overwhelm receiver UDP buffers or intermediate network equipment, implementations MUST pace outgoing packets on the application bulk-send path, using BOTH a congestion-window gate and rate-based pacing together — neither alone is sufficient, as explained below.

#### 5.4.1. Congestion-Window Gate

Bulk-send loops (streaming uploads, large single-call sends) MUST block before sending each packet until the congestion window has room, rather than sending unconditionally and relying on pacing alone to hold the rate down:

```
function awaitCongestionWindow(bytes, stallBudgetMs):
    if canSend(bytes): return true
    deadline = now() + stallBudgetMs
    while now() < deadline:
        sleep(~1ms)
        if canSend(bytes): return true
    return false   // caller MUST abort the transfer: no ACK progress means the link is down
```

Without this gate, a sender on a fast local network path (e.g. loopback, LAN) sends at local I/O speed regardless of what the far side of a WAN bottleneck can actually absorb; the excess is dropped in transit, and the receiver observes only a slow trickle of rate-limited retransmissions — the transfer can take an order of magnitude longer than the path's real capacity, or effectively never finish for a sufficiently large payload. `stallBudgetMs` SHOULD be on the order of 30 seconds; the caller MUST treat exhaustion as a fatal transfer error (no ACKs are arriving at all), not silently continue.

#### 5.4.2. Rate-Based Pacing

Within the congestion window, packets MUST still be spread over time rather than emitted as a burst. A fixed-size-burst-then-pause strategy (pause after every N packets, N chosen from a target burst byte count) is NOT sufficient on its own: shallow bottleneck-router buffers and ingress traffic policers (common on budget VPS hosting and mobile carrier NAT) clip line-rate bursts even when the resulting *average* rate is well under the path's real capacity, producing a steady drip of loss events purely from burst shape rather than sustained overload. Implementations MUST instead pace individual packets at a rate derived from the current congestion window and RTT estimate:

```
function reservePacingDelayNanos(bytes, cwnd, smoothedRtt):
    rate = max(MIN_PACING_RATE, PACING_GAIN * cwnd * 1000.0 / max(1, smoothedRtt))  // bytes/sec
    nanosForBytes = bytes * 1e9 / rate
    now = nanoTime()
    if pacerNextSendTime < now - BURST_ALLOWANCE_NANOS:
        pacerNextSendTime = now     // idle: restart the bucket, allow a small burst
    delay = max(0, pacerNextSendTime - now)
    pacerNextSendTime += nanosForBytes
    return delay
```

| Parameter | Value | Description |
|---|---|---|
| PACING_GAIN | 1.25 | Pace slightly above the strict ACK-clocked rate (`cwnd/smoothedRtt`) so the window is still able to grow, without bursting |
| MIN_PACING_RATE | 10,000 bytes/sec | Floor so pacing never stalls sending entirely on a collapsed window |
| BURST_ALLOWANCE_NANOS | 2,000,000 (2 ms) | Small leaky-bucket burst allowance after an idle period |

This is a leaky-bucket (QUIC-style) pacer: it is a per-connection function of `cwnd` and RTT, requiring no separate configuration, and on fast paths (localhost, LAN) `cwnd / smoothedRtt` is large enough that the computed delay rounds to effectively zero — the pacer is a no-op exactly where it should be.

**Retransmission is also subject to rate pacing**, not just new data (§4.3): a burst of retransmitted packets is exactly the kind of line-rate burst a shallow bottleneck buffer clips, and clipping retransmissions turns one loss event into a self-sustaining loss storm. Implementations SHOULD derive a per-retransmit-cycle byte budget from the same rate function (`pacingBudgetBytes(intervalMs) = rate * intervalMs / 1000`) rather than only a fixed packet-count limit.

Implementations MAY use more sophisticated pacing algorithms, provided the resulting send rate does not exceed `PACING_GAIN * cwnd / smoothedRtt` bytes per second on average and individual packets are not emitted in bursts large enough to be clipped by typical shallow router buffers (tens of KB).

#### Send-Buffer Backpressure

Fixed-rate pacing (a constant burst size and interval) does not adapt to the real uplink. When the configured pace exceeds the path's actual capacity — e.g. a device on a slow mobile uplink — the sender overruns the OS socket send buffer. On a non-blocking socket, `send()` then returns 0 (buffer full). An implementation that treats this as a *drop* and moves on creates a damaging failure mode:

- The datagram was already recorded as in-flight (for loss detection), so it is not truly lost data — but it never reached the wire, and loss detection will retransmit it a full RTO later.
- Under a sustained over-send, most datagrams are dropped locally this way, producing a self-inflicted retransmit storm on top of any real network loss.
- Each local drop still counts toward the Max Retransmit Count (30). A segment can exhaust its retransmit budget on repeated *local* buffer-full drops — never having been transmitted — and then be abandoned per §4.3, leaving a permanent gap that stalls the whole message until the request idle timeout fires.

Therefore, on the application bulk-send path, when the OS send buffer is full an implementation SHOULD apply **backpressure**: wait (with bounded backoff) for the buffer to drain and retry the same datagram, rather than dropping it. Because the buffer drains at the true uplink rate, this paces the send loop to the network automatically — eliminating local drops and the retransmit storm — and is strictly more effective than a fixed pace at matching a slow link. Implementations SHOULD bound the wait so a genuinely dead link does not hang the sender: cap the per-datagram wait, and abort the transfer if *no* datagram makes progress for an interval comparable to the request idle timeout. Best-effort senders that must not block (the retransmit timer, control packets) SHOULD use only a small buffer-wait budget so they never stall a shared scheduler.

A useful side effect: when the send loop is paced by backpressure, a byte-read-driven upload progress indicator reflects the true delivery rate, because the loop reads its source only as fast as the buffer drains. The Java reference implementation applies backpressure in the streaming send helpers (`sendAndCloseFromInputStream` / `sendFromInputStream`) via `sendFrameBackpressured`, with a 1 s per-datagram budget and a 30 s no-progress abort.

### 5.5. Bytes-in-Flight Tracking

The `bytesInFlight` variable tracks the total size (in bytes) of all sent packets that have not yet been acknowledged or removed for retransmission. Accurate maintenance of this counter is essential for correct congestion control behavior.

The counter is modified in three contexts:

| Event | Operation | Window Effect |
|---|---|---|
| Ack-eliciting packet sent (`onSend`) | `bytesInFlight += packetSize` | None |
| ACK received (`onAck`) | `bytesInFlight -= ackedBytes` | Window grows per CUBIC |
| Retransmit removal (`onRetransmitRemove`) | `bytesInFlight -= removedBytes` | No window growth |

The distinction between ACK-driven decrements and retransmit-removal decrements is critical. ACK-driven decrements represent successful delivery and permit congestion window growth. Retransmit-removal decrements represent bookkeeping adjustments prior to retransmission and MUST NOT cause window growth.

**`onSend` MUST only be invoked for ack-eliciting packets.** ACK-only packets (§2.1, "NOT ack-eliciting") are never recorded in the sent-packet tracking table and therefore can never be matched and decremented by `onAck`. If `bytesInFlight` is incremented for every packet sent — including ACK-only packets — the counter accumulates an unrecoverable upward leak: on a connection carrying substantial ACK-only traffic (e.g. the receiving side of a large download, whose only outbound packets are ACKs), the leaked bytes eventually consume the entire congestion window and any sender gated on this connection's window (§5.4.1) via `canSend` stalls permanently, even though every packet the connection is actually accountable for has long since been acknowledged.

## Versioning

|Ver|Date|Changes|
|---|---|---|
|1|2026-03-28|Initial specification.|
|1 (rev)|2026-07-14|Major revision covering a field investigation into a real WAN path (independently measured near 1 MB/s, e.g. via scp) sustaining only tens of KB/s over FUDP: (1) §2.1 — ACK generation MUST be redundant over a retention window, not fire-once, since a single lost ACK packet previously orphaned its covered packet numbers permanently; (2) §4.1 — re-enabled gap-based loss detection with an adaptive, evidence-driven reordering threshold (previously fixed and too tight for deeply-reordering routes), added an RTT age guard, and made the timeout threshold adaptive-but-capped with per-packet exponential backoff; (3) §4.3 — congestion-window reduction MUST be gated on gap-detected loss only, since timeout-only "loss" is routinely spurious on jittery paths and was previously pinning the window at its floor on every RTT spike; raised Max Retransmit Count 30→60 to match the backoff; (4) §5.3 — fixed the CUBIC growth function to operate in MSS (packet) units per RFC 8312 rather than bytes, correcting a unit error that made the window's growth phase roughly two orders of magnitude slower than its Beta-multiplicative shrink phase; (5) §5.4 — added a mandatory congestion-window gate on bulk-send loops and replaced fixed-size-burst pacing with rate-based (leaky-bucket) pacing derived from cwnd/RTT, since burst pacing was clipped by shallow bottleneck buffers and ingress policers even at low average rates; (6) §5.5 — fixed `onSend` to count only ack-eliciting packets, closing a `bytesInFlight` leak from ACK-only traffic that could permanently stall a congestion-window-gated sender.|

## 6. References

- FUDP0V1_FUDP -- FUDP protocol overview and design rationale.
- FUDP1V1_CoreTransport -- Packet structure, frame definitions, and wire format encoding.
- FUDP2V1_Streams -- Stream multiplexing and flow control.
- RFC 6298 -- Computing TCP's Retransmission Timer.
- RFC 8312 -- CUBIC for Fast Long-Distance Networks.
- RFC 2119 -- Key words for use in RFCs to Indicate Requirement Levels.
