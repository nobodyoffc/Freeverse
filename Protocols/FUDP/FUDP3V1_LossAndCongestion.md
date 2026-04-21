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
  - [2.2. ACK Frame Encoding](#22-ack-frame-encoding)
- [3. RTT Estimation](#3-rtt-estimation)
  - [3.1. Parameters](#31-parameters)
  - [3.2. EWMA Algorithm](#32-ewma-algorithm)
  - [3.3. Retransmission Timeout](#33-retransmission-timeout)
- [4. Loss Detection](#4-loss-detection)
  - [4.1. Loss Detection Algorithm](#41-loss-detection-algorithm)
  - [4.2. Parameters](#42-parameters)
  - [4.3. Retransmission](#43-retransmission)
- [5. Congestion Control](#5-congestion-control)
  - [5.1. States](#51-states)
  - [5.2. Parameters](#52-parameters)
  - [5.3. CUBIC Algorithm](#53-cubic-algorithm)
  - [5.4. Send Pacing](#54-send-pacing)
  - [5.5. Bytes-in-Flight Tracking](#55-bytes-in-flight-tracking)
- [6. References](#6-references)

## Abstract

This document specifies the loss detection and congestion control mechanisms for the FUDP (Freeverse UDP) protocol. It defines ACK processing rules, round-trip time estimation, time-based loss detection, and CUBIC congestion control. These mechanisms ensure reliable data delivery over unreliable UDP transport while maintaining fair use of network resources. This specification is language-agnostic and intended to be implemented alongside FUDP1 (Packet Formats) and FUDP2 (Connection Management).

## Summary

FUDP3 defines four interrelated subsystems:

1. **ACK Processing** -- Rules for generating and interpreting acknowledgment frames, including immediate and delayed ACK policies.
2. **RTT Estimation** -- Exponentially Weighted Moving Average (EWMA) computation of smoothed round-trip time and variance, used as input to loss detection and retransmission timeout.
3. **Loss Detection** -- Time-threshold-based algorithm for declaring packets lost, with rate-limited retransmission and a maximum retransmission count before connection closure.
4. **Congestion Control** -- CUBIC algorithm with slow start, congestion avoidance, and recovery states, governing the congestion window and bytes-in-flight tracking.

## 1. Introduction

FUDP operates over UDP and therefore does not inherit TCP's built-in reliability or congestion control. This specification defines the mechanisms that FUDP implementations MUST use to detect lost packets, estimate network conditions, and regulate the rate of data transmission.

The key words "MUST", "MUST NOT", "SHOULD", "SHOULD NOT", and "MAY" in this document are to be interpreted as described in RFC 2119.

All packet and frame wire formats referenced in this document are defined in FUDP1V1_PacketFormats.

## 2. ACK Processing

### 2.1. ACK Generation Rules

Implementations MUST acknowledge every ack-eliciting packet received.

The following frames are classified by their ack-eliciting property:

| Classification | Frame Types |
|---|---|
| NOT ack-eliciting | ACK, PADDING |
| Ack-eliciting | STREAM, CONNECTION_CLOSE, MAX_DATA, MAX_STREAM_DATA, PING, and all other frame types |

A packet is ack-eliciting if it contains at least one ack-eliciting frame. A packet containing only ACK and/or PADDING frames is NOT ack-eliciting.

Implementations SHOULD send an ACK frame immediately upon receiving an ack-eliciting packet. The ACK threshold is defined as:

| Parameter | Value |
|---|---|
| ACK_THRESHOLD | 1 packet |
| MAX_ACK_DELAY | 10 milliseconds |

If an immediate ACK is not triggered for any reason, a delayed ACK MUST be sent within MAX_ACK_DELAY (10 milliseconds) of receiving the ack-eliciting packet.

### 2.2. ACK Frame Encoding

The ACK frame wire format is defined in FUDP1. The ACK frame encodes ranges of acknowledged packet numbers compactly using a largest acknowledged packet number, a first range, and zero or more additional (gap, length) pairs.

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

RTT samples MUST NOT be generated from packets that were retransmitted, as the implementation cannot determine whether the ACK corresponds to the original or retransmitted packet (retransmission ambiguity).

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

FUDP uses time-based loss detection. Unlike packet-number-threshold approaches, time-based detection adapts naturally to varying network conditions through the RTT estimate.

### 4.1. Loss Detection Algorithm

A sent packet is declared lost if it remains unacknowledged for longer than a computed time threshold. The time threshold is derived from the current RTT estimate:

```
function detectLostPackets(sentPackets, smoothedRtt, rttVariance):
    timeThreshold = max(2000ms, 2.0 * smoothedRtt + rttVariance)
    now = currentTime()
    lostPackets = []

    for each packet in sentPackets:
        if now - packet.sentTime > timeThreshold:
            lostPackets.add(packet)

    return lostPackets
```

Implementations MUST run this algorithm periodically or upon receiving ACK frames. When packets are declared lost, the implementation MUST invoke the congestion control loss handler (`onLoss`) and initiate retransmission of the lost data.

### 4.2. Parameters

| Parameter | Value | Description |
|---|---|---|
| Minimum Time Threshold | 2,000 ms | Prevents false positive loss declarations on high-jitter paths |
| Time Threshold Multiplier | 2.0 | Applied to smoothedRtt in threshold computation |

The minimum time threshold of 2,000 ms is deliberately conservative to avoid spurious retransmissions. This value is appropriate for FUDP's target deployment environments, which may include paths with significant jitter.

### 4.3. Retransmission

When packets are declared lost, the data they carried MUST be retransmitted subject to the following constraints:

| Parameter | Value | Description |
|---|---|---|
| Max Retransmit Rate | 50 packets per 50 ms | Rate limit to prevent retransmission-induced congestion |
| Max Retransmit Count | 30 | Maximum retransmissions of the same data before connection closure |

If any data segment has been retransmitted Max Retransmit Count (30) times without successful acknowledgment, the implementation MUST close the connection with an appropriate error.

The retransmission procedure is as follows:

1. Remove the old SentPacket record from the tracking data structure. Decrement `bytesInFlight` by the packet size using `onRetransmitRemove`. This removal does NOT grow the congestion window.
2. Re-package the frames that were in the lost packet into a new packet. The new packet MUST be assigned a new, monotonically increasing packet number.
3. Send the new packet. Increment `bytesInFlight` via `onSend`. Record the new SentPacket with the current timestamp.

Retransmitted packets carry new packet numbers. The original packet number is permanently retired and MUST NOT be reused.

## 5. Congestion Control

FUDP uses the CUBIC congestion control algorithm. CUBIC provides good throughput in high-bandwidth, high-latency environments and converges efficiently to fair bandwidth sharing.

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

The initial window of 120,000 bytes allows rapid ramp-up on modern networks. The minimum window of 14,400 bytes ensures that the connection can always make progress even under persistent loss.

### 5.3. CUBIC Algorithm

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
            t = (now() - epochStart) / 1000.0   // elapsed time in seconds
            k = cbrt(wMax * (1 - Beta) / C)
            target = C * (t - k)^3 + wMax
            if target > cwnd:
                cwnd = target
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
// Send permission check
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

To prevent bursty transmission patterns that may overwhelm receiver UDP buffers or intermediate network equipment, implementations SHOULD pace outgoing packets.

A simple pacing strategy is to insert a short delay (for example, 1 millisecond) after transmitting a burst of N packets. The burst size N SHOULD be tuned based on the current congestion window and observed network behavior.

Implementations MAY use more sophisticated pacing algorithms, provided the resulting send rate does not exceed `cwnd / smoothedRtt` bytes per second on average.

### 5.5. Bytes-in-Flight Tracking

The `bytesInFlight` variable tracks the total size (in bytes) of all sent packets that have not yet been acknowledged or removed for retransmission. Accurate maintenance of this counter is essential for correct congestion control behavior.

The counter is modified in three contexts:

| Event | Operation | Window Effect |
|---|---|---|
| Packet sent (`onSend`) | `bytesInFlight += packetSize` | None |
| ACK received (`onAck`) | `bytesInFlight -= ackedBytes` | Window grows per CUBIC |
| Retransmit removal (`onRetransmitRemove`) | `bytesInFlight -= removedBytes` | No window growth |

The distinction between ACK-driven decrements and retransmit-removal decrements is critical. ACK-driven decrements represent successful delivery and permit congestion window growth. Retransmit-removal decrements represent bookkeeping adjustments prior to retransmission and MUST NOT cause window growth.

## 6. References

- FUDP0V1_FUDP -- FUDP protocol overview and design rationale.
- FUDP1V1_PacketFormats -- Packet structure, frame definitions, and wire format encoding.
- FUDP2V1_ConnectionManagement -- Connection lifecycle, handshake, and termination.
- RFC 6298 -- Computing TCP's Retransmission Timer.
- RFC 8312 -- CUBIC for Fast Long-Distance Networks.
- RFC 2119 -- Key words for use in RFCs to Indicate Requirement Levels.
