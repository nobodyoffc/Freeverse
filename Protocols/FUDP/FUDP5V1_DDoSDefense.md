# FUDP5V1_DDoSDefense

## Contents

[Summary](#summary)

[Abstract](#abstract)

[Motivation](#motivation)

[Specification](#specification)

- [Overview](#overview)
- [IP Verification via Proof-of-Work Challenge](#ip-verification-via-proof-of-work-challenge)
  - [Challenge Flow](#challenge-flow)
  - [Challenge Packet Format](#challenge-packet-format)
- [Proof-of-Work Algorithm](#proof-of-work-algorithm)
  - [Hash Function](#hash-function)
  - [Procedure](#procedure)
  - [Leading Zero Bit Count](#leading-zero-bit-count)
  - [Difficulty Levels](#difficulty-levels)
- [Adaptive Difficulty](#adaptive-difficulty)
- [IP Verification Whitelist](#ip-verification-whitelist)
- [Rate Limiting](#rate-limiting)
  - [Token Bucket Algorithm](#token-bucket-algorithm)
- [Initiator-Side Protection](#initiator-side-protection)
- [Cleanup](#cleanup)

[Security Considerations](#security-considerations)

[Versioning](#versioning)

[Related Protocols](#related-protocols)

[Reference Implementation](#reference-implementation)

---

## Summary

|Field|Content|
|---|---|
|Title|DDoS Defense|
|Type|FUDP|
|SN|5|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-03-28|
|PID||

## Abstract

FUDP5 defines the DDoS defense mechanisms for the FUDP transport protocol. It specifies Proof-of-Work (PoW) challenges for IP verification, adaptive difficulty adjustment under load, IP whitelisting with configurable TTL, and per-IP rate limiting using a token bucket algorithm. These mechanisms protect internet-facing FUDP nodes from resource exhaustion attacks by requiring computational work before expensive cryptographic operations are performed.

## Motivation

FUDP connections involve costly cryptographic operations during the handshake -- ECDH key agreement, AES-GCM session key derivation, and public key exchange. An attacker can exploit this asymmetry by sending a flood of HELLO packets from spoofed or rotating IP addresses, forcing the responder to perform expensive operations for each one. Without transport-layer defense, a modest-bandwidth attacker can exhaust the CPU and memory of a FUDP node.

DDoS defense at the transport layer addresses this by inserting a lightweight verification step before any expensive processing occurs. The Proof-of-Work challenge shifts computational cost onto the initiator, while the responder only needs to verify a single SHA-256 hash per challenge response. This creates a favorable cost asymmetry for the defender.

FUDP is a P2P protocol where every node can act as both an initiator (connecting to others) and a responder (receiving connections). Both roles have configurable protection settings defined in this specification.

## Specification

### Overview

FUDP DDoS defense is an OPTIONAL module (disabled by default). It is RECOMMENDED for internet-facing nodes. When enabled, it requires new connections to complete a Proof-of-Work challenge before the responder performs expensive cryptographic operations (such as replying with a PUBLIC_KEY packet or computing ECDH shared secrets).

The defense operates in three layers:

1. **Proof-of-Work IP Verification** -- Unverified IPs must solve a computational challenge before the handshake proceeds.
2. **IP Whitelist** -- Verified IPs are cached so that subsequent connections from the same IP skip the challenge within a configurable TTL.
3. **Rate Limiting** -- Even verified IPs are subject to per-IP packet rate limiting to prevent abuse from authenticated sources.

The key words "MUST", "MUST NOT", "SHOULD", "SHOULD NOT", and "MAY" in this document are to be interpreted as described in RFC 2119.

### IP Verification via Proof-of-Work Challenge

#### Challenge Flow

When DDoS defense is enabled on the responder, the following procedure applies to connections from unverified IP addresses:

1. **Unverified IP sends HELLO.** The responder does NOT reply with PUBLIC_KEY. Instead, it sends a CONTROL packet containing a CHALLENGE frame.
2. **Responder sends CHALLENGE.** The challenge contains a 16-byte random nonce, difficulty value, and challenge timestamp.
3. **Initiator solves challenge.** The initiator finds an 8-byte solution such that `SHA-256(nonce || solution)` has at least `difficulty` leading zero bits.
4. **Initiator sends CHALLENGE_RESPONSE.** The response contains the original nonce and the computed solution.
5. **Responder verifies solution.** The responder recomputes `SHA-256(nonce || solution)` and checks the leading zero bits. If the solution is valid, the source IP is added to the verified whitelist.
6. **Initiator re-sends HELLO.** Now originating from a verified IP, the normal handshake proceeds as defined in FUDP1.

If the initiator fails to respond within the challenge TTL, or provides an invalid solution, the responder silently discards the attempt. The responder MUST NOT send error responses to failed challenges, as this would itself become an amplification vector.

#### Challenge Packet Format

Challenge and response payloads are carried in CONTROL packets as defined in FUDP1. The control type field identifies the payload type.

**CHALLENGE control payload (type 0x03):**

```
Byte 0:       Control Type (0x03)
Bytes 1-16:   Nonce (16 bytes, random)
Byte 17:      Difficulty (uint8, number of required leading zero bits)
Bytes 18-25:  Challenge Timestamp (uint64, milliseconds since Unix epoch)
```

Total payload size: 26 bytes.

**CHALLENGE_RESPONSE control payload (type 0x04):**

```
Byte 0:      Control Type (0x04)
Bytes 1-16:  Nonce (16 bytes, echoed from challenge)
Bytes 17-24: Solution (8 bytes)
```

Total payload size: 25 bytes.

The nonce MUST be generated using a cryptographically secure random number generator. The responder MUST track outstanding nonces to prevent replay of previously issued challenges.

### Proof-of-Work Algorithm

#### Hash Function

SHA-256 (as defined in FIPS 180-4).

#### Procedure

1. The responder generates a random 16-byte nonce.
2. The solver (initiator) iterates over 8-byte candidate solutions, starting from 0 and incrementing.
3. For each candidate, compute: `hash = SHA-256(nonce || solution)` where `||` denotes byte concatenation. The nonce occupies bytes 0-15 and the solution occupies bytes 16-23 of the SHA-256 input.
4. Check if `hash` has at least `difficulty` leading zero bits.
5. If yes, the solution is valid. The solver returns this solution.

Implementations MAY use any search strategy (sequential, random, parallel) to find a valid solution, as long as the resulting `(nonce, solution)` pair satisfies the difficulty requirement.

#### Leading Zero Bit Count

Count leading zero bits by examining each byte of the hash output from the most significant byte (index 0):

1. If the byte equals `0x00`, add 8 to the count and continue to the next byte.
2. Otherwise, count the leading zero bits in the current byte (i.e., the number of positions before the first set bit, counting from the most significant bit) and stop.

For example:
- `0x00 0x00 0x03 ...` has 14 leading zero bits (8 + 6).
- `0x00 0x01 ...` has 15 leading zero bits (8 + 7).
- `0x0F ...` has 4 leading zero bits.

#### Difficulty Levels

The following table provides expected performance characteristics for each difficulty level:

| Difficulty (bits) | Expected Attempts | Approximate Solve Time |
|---|---|---|
| 4 | ~16 | <1 ms |
| 8 | ~256 | <1 ms |
| 12 | ~4,096 | ~5 ms |
| 16 | ~65,536 | ~50 ms |
| 20 | ~1,048,576 | ~500 ms |
| 24 | ~16,777,216 | ~8 seconds |

Solve times are approximate and assume a single-threaded implementation on commodity hardware (circa 2025).

| Parameter | Value |
|---|---|
| Minimum difficulty | 4 bits |
| Maximum difficulty | 20 bits (default implementation cap) |
| Default difficulty | 12 bits |

Implementations MUST reject difficulty values outside the accepted implementation range.

### Adaptive Difficulty

The responder MAY dynamically adjust the PoW difficulty based on the rate of incoming packets. This allows the defense to automatically strengthen under heavy load and relax during normal operation.

| Parameter | Default Value | Description |
|---|---|---|
| High Load Threshold | 5,000 packets/sec | Increase difficulty above this rate |
| Low Load Threshold | 1,000 packets/sec | Decrease difficulty below this rate |
| Adjustment Interval | 1,000 ms | Minimum time between difficulty adjustments |
| Increase Step | 2 bits | Difficulty increase per adjustment |
| Decrease Step | 1 bit | Difficulty decrease per adjustment |

**Behavior:**

- When the incoming packet rate exceeds the high load threshold, increase difficulty by the increase step (capped at the configured maximum difficulty; the Java default is 20 bits and the hard PoW utility cap is 24 bits).
- When the incoming packet rate falls below the low load threshold, decrease difficulty by the decrease step (floored at the configured base difficulty, which defaults to 12 bits).
- When the packet rate is between the two thresholds, the difficulty remains unchanged.
- Adjustments MUST NOT occur more frequently than the adjustment interval.

Implementations that support adaptive difficulty SHOULD measure packet rate using a sliding window or exponential moving average rather than instantaneous counts, to avoid oscillation.

### IP Verification Whitelist

Once an IP address has completed a valid PoW challenge, it is added to a verification whitelist. Subsequent connections from the same IP address bypass the challenge for the duration of the TTL.

| Parameter | Default Value | Description |
|---|---|---|
| Verified TTL | 3,600,000 ms (1 hour) | Duration a verified IP remains whitelisted |
| Challenge TTL | 5,000 ms | Duration a pending challenge remains valid |
| Max Pending Challenges | 10,000 | Maximum number of concurrent outstanding challenges |

After the verified TTL expires, the IP MUST complete a new challenge to regain verified status.

If the number of pending (unsolved) challenges reaches the maximum, the responder MUST reject new HELLO packets from unverified IPs by silently dropping them. The responder MUST NOT allocate additional challenge state beyond the configured maximum.

### Rate Limiting

After IP verification, per-IP rate limiting is enforced using a token bucket algorithm. This prevents verified IPs from overwhelming the node with excessive packet volume.

| Parameter | Default Value | Description |
|---|---|---|
| Max Packets/Second/IP | 100 | Steady-state rate limit per source IP |
| Burst Capacity | 200 packets | Maximum token bucket size, allowing short bursts |

Packets that exceed the rate limit MUST be silently dropped. The responder MUST NOT send any response to rate-limited packets.

#### Token Bucket Algorithm

The following pseudocode defines the token bucket rate limiter:

```
function tryConsume(ip):
    bucket = getBucket(ip)
    now = currentTime()
    elapsed = now - bucket.lastRefill

    // Refill tokens based on elapsed time
    tokensToAdd = elapsed * maxPacketsPerSecond / 1000
    bucket.tokens = min(burstCapacity, bucket.tokens + tokensToAdd)
    bucket.lastRefill = now

    // Attempt to consume one token
    if bucket.tokens >= 1:
        bucket.tokens -= 1
        return ALLOW
    else:
        return DENY
```

Each source IP address has an independent token bucket. Buckets are initialized with `burstCapacity` tokens and a `lastRefill` timestamp of the current time upon first packet arrival. Buckets for IPs that have not sent packets within the verified TTL MAY be reclaimed during cleanup.

### Initiator-Side Protection

Initiators connecting to potentially hostile responders SHOULD enforce limits on the PoW challenges they are willing to accept:

| Parameter | Default Value | Description |
|---|---|---|
| Max Acceptable Difficulty | 16 | Refuse challenges with difficulty above this value |
| Max PoW Solve Time | 2,000 ms | Abort solving if it exceeds this duration |
| Max Consecutive High Difficulty | 3 | Blacklist peer after this many high-difficulty challenges |

A "high difficulty" challenge is defined as one whose difficulty value exceeds 75% of the initiator's max acceptable difficulty (i.e., difficulty > 12 when the max is 16).

**Behavior:**

- If a challenge has difficulty exceeding the max acceptable difficulty, the initiator MUST discard the challenge and SHOULD NOT attempt the connection again for a cooldown period (RECOMMENDED: 60 seconds).
- If solving a challenge exceeds the max PoW solve time, the initiator MUST abort the attempt.
- If a peer issues consecutive high-difficulty challenges exceeding the configured threshold, the initiator SHOULD consider the peer potentially malicious and refuse further connection attempts for an extended period (RECOMMENDED: 300 seconds).

These protections prevent a malicious responder from consuming excessive CPU time on the initiator through artificially inflated difficulty values.

### Cleanup

Implementations MUST periodically clean up expired entries to prevent unbounded memory growth:

- Remove pending challenges whose challenge TTL has expired.
- Remove verified IPs whose verified TTL has expired.
- Remove token buckets for IPs that have no active connections and whose verified TTL has expired.

| Parameter | Default Value | Description |
|---|---|---|
| Cleanup Interval | 60,000 ms (1 minute) | How often the cleanup routine runs |

The cleanup routine SHOULD execute in a background thread or equivalent mechanism and MUST NOT block packet processing.

## Security Considerations

1. **IP Spoofing Prevention.** The PoW challenge-response mechanism requires the initiator to receive the challenge packet at its claimed IP address and return the solution. An attacker using spoofed source IPs cannot receive the challenge and therefore cannot complete the handshake. This prevents IP spoofing-based amplification attacks.

2. **Computational Asymmetry.** At the default difficulty of 12 bits, solving a challenge costs approximately 5 ms of CPU time (iterating ~4,096 SHA-256 hashes), while verification requires a single SHA-256 computation. This asymmetry strongly favors the defender.

3. **Memory Exhaustion Prevention.** The max pending challenges limit (default: 10,000) bounds the memory consumed by outstanding challenge state. An attacker cannot force the responder to allocate unbounded memory by sending a flood of HELLO packets.

4. **Amplification Prevention.** Before PoW verification, the responder only sends small CHALLENGE packets (18 bytes of control payload), not expensive PUBLIC_KEY responses. This minimizes the amplification factor for reflected attacks.

5. **Adaptive Response.** Dynamic difficulty adjustment allows the system to automatically increase protection under sustained attack without manual intervention. The increase step of 2 bits quadruples the expected work per adjustment, providing rapid escalation.

6. **Initiator Protection.** Initiator-side limits on acceptable difficulty and solve time prevent a malicious responder from using the PoW mechanism as a denial-of-service vector against connecting peers.

7. **Nonce Freshness.** The challenge TTL (default: 5 seconds) ensures that captured challenge-response pairs cannot be replayed after a short window. Responders MUST NOT accept solutions for expired nonces.

## Versioning

|Ver|Date|Changes|
|---|---|---|
|1|2026-03-28|Initial specification.|

## Related Protocols

- **FUDP0V1_FUDP** -- Foundational rules for all FUDP protocols, including byte order, variable-length integer encoding, and conformance requirements.
- **FUDP1 (Core Transport)** -- Defines the CONTROL packet format used to carry CHALLENGE and CHALLENGE_RESPONSE payloads, as well as the HELLO and PUBLIC_KEY packets referenced in the challenge flow.
- **FUDP4 (Security)** -- Defines the handshake cryptography and session key establishment that DDoS defense protects from abuse.
- **FTSP11 (Ecc256K1AesGcm256)** -- Defines the ECDH key agreement that constitutes the "expensive cryptographic operation" this specification aims to protect.

## Reference Implementation

The reference implementation is located in the FC-JDK repository under the `fudp` package. The relevant source files include:

- `fudp/node/FudpNode.java` -- Node-level DDoS defense configuration and integration.
- `fudp/node/NodeConfig.java` -- Configuration parameters for PoW difficulty, rate limits, and TTL values.
- `fudp/handler/MessageHandler.java` -- Challenge generation, verification, and rate limiting logic.

The FC-JDK implementation is authoritative for resolving ambiguities in this specification, as stated in FUDP0.
