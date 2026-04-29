# FUDP4V1_Security

## Contents

[Summary](#summary)

[Abstract](#abstract)

[Motivation](#motivation)

[Encryption Model](#encryption-model)

[Handshake Security](#handshake-security)

[Session Epoch](#session-epoch)

[Replay Protection](#replay-protection)

[Sensitive Data Handling](#sensitive-data-handling)

[Security Considerations](#security-considerations)

[Related Protocols](#related-protocols)

[Reference Implementation](#reference-implementation)

---

## Summary

|Field|Content|
|---|---|
|Title|Security|
|Type|FUDP|
|SN|4|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-03-28|
|PID||

## Abstract

This document specifies the security mechanisms of the FUDP transport protocol. FUDP uses exclusively AsyTwoWay (asymmetric two-way) encryption based on ECDH key exchange with secp256k1 elliptic curve cryptography. There is no symmetric key negotiation phase in the handshake; all encrypted packets use ECDH-derived keys as specified by FTSP11 (Ecc256K1AesGcm256) and FTSP13 (HKDF). This document defines the encryption model, handshake security properties, session epoch management, and replay protection.

## Motivation

FUDP requires mandatory encryption for all data packets (see FUDP0, General Rule 4). The security model must satisfy several constraints:

1. **Identity-native cryptography.** Peers are identified by secp256k1 public keys. The encryption scheme must use these same keys directly rather than introducing a separate key infrastructure.

2. **Zero-round-trip encryption.** Once a peer's public key is known, encrypted packets can be sent immediately. The ECDH shared secret is derived deterministically from the two peers' key pairs, requiring no negotiation round-trips.

3. **Replay resistance.** UDP is inherently unordered and unreliable. An attacker who captures packets can replay them. The protocol must detect and reject replayed packets without relying on TCP-style sequence guarantees.

4. **Restart resilience.** Peers in a P2P network may restart at any time. The protocol must detect peer restarts and reset stale connection state to avoid protocol errors from mismatched state.

This document specifies how these requirements are met.

## Encryption Model

FUDP uses exclusively AsyTwoWay (asymmetric two-way) encryption based on ECDH key exchange with secp256k1 elliptic curve cryptography. There is NO symmetric key negotiation in the handshake. All encrypted packets use ECDH-derived keys.

### Cryptographic Profile

FUDP relies on FTSP11 (Ecc256K1AesGcm256) for all packet encryption:

1. **Key Exchange**: ECDH on secp256k1 curve.
2. **Key Derivation**: HKDF-SHA512 (per FTSP13) with info string `"hkdf"`.
   - Extract: HKDF-Extract(salt=IV, ikm=ecdhSharedSecret) using HMAC-SHA512.
   - Expand: HKDF-Expand(prk, info="hkdf", length=32) to produce a 32-byte AES key.
3. **Encryption**: AES-256-GCM with 12-byte random IV and 128-bit authentication tag.

### Identity Model

Each FUDP node is identified by its secp256k1 key pair:

- **Public Key**: 33-byte compressed secp256k1 public key (serves as node identity).
- **Private Key**: 32-byte scalar (never transmitted).
- **FID**: Freeverse Identity derived from the public key (for human-readable identification).

The public key doubles as both the encryption key and the node's identity. No separate identity certificates are needed.

### Encrypted Packet Structure

For DATA and ACK packets, the payload after the 21-byte header (as defined in FUDP1) is an encrypted bundle in CryptoDataByte format per FTSP11:

```
Encrypted Bundle (CryptoDataByte format per FTSP11):
  - Ciphertext (variable length, includes AES-GCM 128-bit auth tag)
  - IV (12 bytes, random per packet)
  - Type indicator: AsyTwoWay
  - Public Key A (33 bytes, sender's compressed public key)
  - Public Key B (33 bytes, receiver's compressed public key)
  - Algorithm ID: "EccK1AesGcm256@No1_NrC7"
```

The plaintext before encryption has the following layout:

```
Bytes 0-7:   Timestamp (64-bit big-endian, optional when HAS_TIMESTAMP flag is set)
Bytes 8-15:  Session Epoch (64-bit big-endian, optional when HAS_EPOCH flag is set)
Bytes N+:    Frames (concatenated frame data)
```

### Encryption Procedure

**Sender:**

1. Serialize frames into plaintext bytes.
2. Prepend optional timestamp/session-epoch fields according to header flags, then append frame bytes.
3. Encrypt the plaintext using FTSP11 with the sender's private key and the receiver's public key, binding the 21-byte packet header as AEAD AAD.
4. The resulting CryptoDataByte bundle becomes the packet payload.

**Receiver:**

1. Parse the 21-byte header to obtain the packet type and connection ID.
2. Decrypt the payload using FTSP11 with the receiver's private key and the sender's public key (extracted from the CryptoDataByte bundle).
3. Extract timestamp/session-epoch only when corresponding header flags are set, then parse frames from the remaining plaintext.
4. Validate the timestamp and session epoch (see Replay Protection).
5. Process frames.

### Shared Secret Caching

Computing ECDH shared secrets is computationally expensive. Implementations SHOULD cache the derived AES-256 symmetric key per peer public key to avoid redundant ECDH and HKDF computations on every packet. The cached key MUST be evicted when the connection is closed or when the peer's public key changes.

## Handshake Security

The handshake flow (as defined in FUDP1) exchanges public keys in plaintext:

1. Initiator sends CONTROL_HELLO (plaintext).
2. Responder sends CONTROL_PUBLIC_KEY containing their 33-byte compressed public key (plaintext).
3. Initiator sends first encrypted DATA packet using ECDH with the responder's public key.
4. Responder decrypts, learning the initiator's public key from the CryptoDataByte bundle.

### Public Key Response Rate Limiting

To prevent amplification attacks via PUBLIC_KEY responses, implementations SHOULD rate-limit public key responses per remote address:

- Maximum 3 responses per 2-second window per source address.

Implementations MAY use stricter limits.

### Security Properties of the Handshake

The plaintext HELLO/PUBLIC_KEY exchange is vulnerable to man-in-the-middle attacks at the network level. However:

- Once the first encrypted packet is sent, the ECDH shared secret binds both parties' identities cryptographically.
- Applications that require authentication SHOULD verify the peer's public key or FID through an out-of-band mechanism (e.g., blockchain-published identity records).
- The CryptoDataByte bundle in every encrypted packet includes both public keys, allowing either party to verify the peer's identity on every packet.

An attacker who intercepts the HELLO/PUBLIC_KEY exchange and substitutes their own public key would need to maintain an active man-in-the-middle position for the entire connection, decrypting and re-encrypting every packet. Out-of-band public key verification eliminates this attack.

## Session Epoch

Each node generates a random 64-bit Session Epoch value at startup. This value:

- Is included in encrypted packet plaintext while the peer has not yet confirmed the epoch. The Java reference implementation omits it after an ACK confirms receipt, using the header `HAS_EPOCH` flag to indicate presence.
- Changes only when the node restarts.
- Allows peers to detect that a node has restarted and reset stale connection state.

The Session Epoch MUST be generated using a cryptographically secure random number generator. The value `0` is reserved on the wire to mean "unknown or omitted" and SHOULD NOT be generated as a real session epoch.

### Restart Detection

When a receiver observes a change in the Session Epoch for an existing connection:

1. The old connection state (replay window, ACK tracking, retransmission state) MUST be reset.
2. The connection MAY be re-established or the old connection evicted.
3. Stale connections (different epoch AND idle for more than 30 seconds) SHOULD be evicted.

## Replay Protection

FUDP implements replay protection using a per-connection sliding window combined with timestamp validation.

### Parameters

|Parameter|Value|Description|
|---|---|---|
|Window Size|65,536|Number of packet numbers tracked in the sliding window|
|Timestamp Tolerance|+/- 60 seconds (default)|Maximum clock skew allowed between peers|

### Replay Check Algorithm

The following pseudocode defines the replay check procedure. Implementations MUST implement equivalent logic.

```
function checkAndRecord(connectionId, packetNumber, timestamp, sessionEpoch):
    // Step 1: Validate timestamp
    if abs(timestamp - currentTime()) > TIMESTAMP_TOLERANCE:
        return INVALID_TIMESTAMP

    // Step 2: Get or create window for this connection
    window = getOrCreateWindow(connectionId)

    // Step 3: Check for peer restart (session epoch change)
    if window.sessionEpoch != 0 AND sessionEpoch != window.sessionEpoch:
        window.reset()
        window.sessionEpoch = sessionEpoch
        window.record(packetNumber)
        return PEER_RESTART

    // Step 4: Initialize session epoch on first packet
    if window.sessionEpoch == 0:
        window.sessionEpoch = sessionEpoch

    // Step 5: Sliding window check
    return window.checkAndRecord(packetNumber)
```

### Sliding Window Algorithm

```
function checkAndRecord(packetNumber):
    if packetNumber < 0:
        return DUPLICATE

    // First packet
    if highestPacketNumber < 0:
        highestPacketNumber = packetNumber
        mark(0)
        return OK

    // Packet too old (before window)
    if packetNumber <= highestPacketNumber - WINDOW_SIZE:
        return DUPLICATE

    // Packet within current window
    if packetNumber <= highestPacketNumber:
        offset = highestPacketNumber - packetNumber
        if isMarked(offset):
            return DUPLICATE
        mark(offset)
        return OK

    // New highest -- slide window forward
    shift = packetNumber - highestPacketNumber
    if shift >= WINDOW_SIZE:
        clearAll()
    else:
        shiftBitsRight(shift)
    highestPacketNumber = packetNumber
    mark(0)
    return OK
```

The sliding window SHOULD be implemented as a bitset of size WINDOW_SIZE. The `mark`, `isMarked`, `shiftBitsRight`, and `clearAll` operations correspond to standard bitset operations.

### Check Results

|Result|Meaning|
|---|---|
|OK|Packet is valid and has not been seen before|
|DUPLICATE|Packet number already seen or falls before the window|
|INVALID_TIMESTAMP|Timestamp outside the tolerance window|
|PEER_RESTART|Session epoch changed; the replay window was reset|

### Handling of Results

- **OK**: Process the packet normally.
- **DUPLICATE**: Drop the packet silently. Implementations SHOULD NOT send any response to duplicate packets.
- **INVALID_TIMESTAMP**: The Java reference implementation sends a CONNECTION_CLOSE with `INTERNAL_ERROR` and removes the connection. Other implementations MAY drop silently if they prefer not to reveal timestamp-validation policy.
- **PEER_RESTART**: Process the packet normally. The connection state has been reset. Implementations SHOULD log the peer restart event.

## Sensitive Data Handling

Implementations MUST:

- Clear private keys from memory when no longer needed.
- Clear ECDH shared secrets and derived AES keys from memory when the connection is closed.
- Never log or persist private key material.
- Never log or persist ECDH shared secrets or derived session keys.

Implementations SHOULD use secure memory allocation (e.g., non-swappable pages) for private key material where the platform supports it.

## Security Considerations

### 1. Forward Secrecy

FUDP does NOT provide forward secrecy. The same ECDH key pair is used for all connections. Compromise of the private key allows decryption of all past and future traffic encrypted with that key pair.

Applications that require forward secrecy MUST implement ephemeral key exchange at a higher layer or rotate their FUDP identity keys periodically.

### 2. Identity Binding

The peer's public key in the CryptoDataByte bundle provides identity binding on every encrypted packet. An attacker cannot inject packets into an existing connection without possessing the private key corresponding to the sender's public key embedded in the bundle. Tampering with the public key fields in the bundle will cause decryption failure due to ECDH key mismatch.

### 3. Replay Protection

The sliding window and timestamp check prevent replay attacks within the tolerance window. The default 60-second tolerance balances replay resistance and practical clock drift.

Implementations MAY tune tolerance within a bounded safe range (implementation-constrained).

### 4. Session Epoch

The session epoch mechanism detects peer restarts to prevent stale connection state from causing protocol errors. Without this mechanism, a restarted peer would reset its packet number counter while the remote peer's replay window still expects higher packet numbers, causing all packets to be rejected as duplicates.

### 5. No Key Rotation

FUDP v1 does not support mid-connection key rotation. Long-lived connections use the same derived AES key throughout the connection lifetime. If key rotation is required, the connection must be closed and re-established.

### 6. Amplification Attack Mitigation

The PUBLIC_KEY response rate limit (3 responses per 2-second window per source address) prevents attackers from using FUDP responders as amplifiers. Since CONTROL_HELLO is small and CONTROL_PUBLIC_KEY is larger (contains a 33-byte public key), an attacker could otherwise spoof source addresses to direct amplified responses at victims.

### 7. Denial of Service

An attacker can send a high volume of packets with invalid encryption to force a node to perform ECDH computations. Shared secret caching mitigates this for known peers. For unknown peers, the HELLO/PUBLIC_KEY exchange occurs before any ECDH computation, and the rate limiting on PUBLIC_KEY responses bounds the resource expenditure. Additional DDoS defense mechanisms are specified in FUDP5.
The reference implementation further applies a per-source decrypt-failure rate limiter, temporarily dropping packets from abusive sources before decryption to cap CPU burn during attack bursts.

## Related Protocols

|Protocol|Relationship|
|---|---|
|FTSP11 (Ecc256K1AesGcm256)|Defines the ECDH key agreement and AES-256-GCM encryption procedure used for all packet encryption.|
|FTSP13 (HKDF)|Defines the HKDF-SHA512 key derivation function used to derive AES keys from ECDH shared secrets.|
|FUDP0 (FUDP)|Foundational rules for the FUDP protocol series, including the mandatory encryption requirement.|
|FUDP1 (Core Transport)|Defines the packet header format, frame types, connection lifecycle, and handshake flow that this document secures.|
|FUDP3 (Loss & Congestion)|Defines ACK processing and packet number assignment that the replay protection mechanism operates on.|
|FUDP5 (DDoS Defense)|Defines additional defense mechanisms (proof-of-work, IP verification) that complement the security measures in this document.|

## Reference Implementation

The reference implementation is in Java (FC-JDK), located in the `fudp` package:

- `fudp/security/ReplayProtection.java` -- Sliding window replay protection.
- `fudp/connection/ConnectionContext.java` -- Session epoch management and connection security state.
- `fudp/handler/MessageHandler.java` -- Encryption and decryption of packet payloads.
- `fudp/node/FudpNode.java` -- Handshake flow and public key exchange.

The FC-JDK implementation is authoritative for resolving ambiguities in this specification.
