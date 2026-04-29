---
title: FUDP v2 Repair Plan — cross-end security/correctness fixes
status: draft · awaiting repair work on Linux side
origin: raised during the FreerForMac Swift port survey (2026-04-24)
scope: FC-JDK (Linux server) · FC-AJDK (Android client) · FreerForMac (Swift client, not yet started)
---

# FUDP v2 Repair Plan

## 1. Why this document exists

A port of the FUDP/FAPI client is being built on macOS (Swift) for the `FreerForMac` project. A thorough survey of the **Android** client (`FC-AJDK/src/main/java/com/fc/fc_ajdk/fudp/`) during port planning surfaced several security-level and robustness issues in the protocol itself — not just in one implementation. The **Linux server** (`FC-JDK/src/main/java/fudp/`) mirrors the same wire format and presumably inherits most of these issues.

Rather than reproduce these issues in a third codebase, this doc proposes a coordinated **v2 repair** across all three ends. The Android↔Linux system is already in production, so the repair must be planned as a protocol-version bump with a clean cutover.

**This doc was written by a port-planning assistant with deep knowledge of the Android client only.** Every finding below cites Android file/line references. The Linux server presumably mirrors them but needs confirmation before any code change lands. Where paths read `FC-AJDK/…`, the Linux analogue is at `FC-JDK/src/main/java/fudp/…` with the same relative layout.

## 2. What was surveyed

Under `FC-AJDK/src/main/java/com/fc/fc_ajdk/`:

- `fudp/packet/` — PacketHeader (21-byte fixed header), Packet (frames), StreamFrame
- `fudp/crypto/` — CryptoManager (ECDH cache, session epoch), PacketCrypto (encrypt/decrypt)
- `fudp/security/` — ReplayProtection (sliding window, timestamp check)
- `fudp/congestion/` — CongestionControl (CUBIC), RttEstimator
- `fudp/connection/` — PeerConnection, ConnectionManager
- `fudp/Protocol.java` — top-level protocol glue
- `fapi/message/` — FapiRequest / FapiResponse / UnifiedCodec
- `fapi/client/` — FapiClient

Cross-referenced with `core/crypto/` where Bundle-format crypto is shared between FUDP's `AsyTwoWay` path and the rest of the app (`CryptoDataByte.java`).

Existing related docs in `FC-JDK/Docs/` worth reading before starting the repair:

- `FUDP_CHAT_TEST_ANALYSIS.md`
- `FUDP_ENCRYPT_MODE_IMPROVEMENT.md`
- `FUDP_FAPI_ECONOMIC_REFACTOR.md`
- `CHACHA20_IMPLEMENTATION.md`

If the repair here overlaps with plans already in those docs, let those docs win — this one is only a summary of *newly identified* issues from the port-planning survey, not a green-field redesign.

## 3. Findings

Severity is subjective but calibrated:
- **high** = exploitable in a realistic threat model, or a correctness bug with user-visible impact.
- **medium** = weakens a security property but is not directly exploitable, or a robustness issue that bites under load.
- **low** = code-quality / protocol-hygiene issue that should not block a release but should be fixed before v2 freezes.

### 3.1 Critical — must fix in v2

#### F1 (high). FUDP packet header is not authenticated
- **Location:** `fudp/crypto/PacketCrypto.java` — encryption call sites pass no AAD to the underlying `AesGcm256.encrypt(...)`.
- **Problem:** The 21-byte on-the-wire header (flags, version, connectionId, packetNumber) is sent **in the clear and unauthenticated**. An active attacker on the path can flip header bits — e.g., mutate the packet number to force replay-window state divergence, or flip the type bits to turn a DATA packet into an ACK — without breaking the AEAD tag. The receiver will parse the tampered header, *then* verify the payload, discover nothing wrong, and act on the corrupted header fields.
- **Why it matters:** QUIC and every modern UDP-crypto protocol treats the header as AEAD-associated-data precisely to prevent this. Our threat model assumes a hostile network path; we should assume an attacker will try header fuzzing.
- **Fix (in v2):** Pass the full 21-byte serialized header as AAD to `AesGcm256.encrypt` on send, and to `decrypt` on receive. Any bit flip on the header fails the AEAD tag and the packet is dropped silently — exactly what we want.
- **Interop impact:** Wire-format-incompatible with v1. Needs a protocol version bump.

### 3.2 Medium

#### F2 (medium). ECDH shared-secret cache is unbounded with non-LRU eviction
- **Location:** `fudp/crypto/CryptoManager.java:114-122` — `ecdhCache` is a `ConcurrentHashMap<String, byte[]>` with a soft "remove one entry when size > 1000" rule.
- **Problem:** An attacker sending packets from N ephemeral public keys will grow the cache to at least N/1 during the attack (one entry removed per insert above the threshold, so steady-state is roughly 1000 but burstiness can push it higher). Eviction is effectively random — the first entry returned by `keySet().iterator()` is removed, which may be a heavily-used peer. This both wastes memory and forces renegotiation for active peers when a flood happens.
- **Fix:** Switch to a proper LRU with a hard cap (e.g. 512 entries) — `LinkedHashMap(initialCapacity, 0.75f, /*accessOrder=*/ true)` inside `synchronized` is the simplest. On macOS we'll use an explicit `LRUCache` type in Swift. Match semantics across implementations.
- **Interop impact:** None. Local-only cache policy change.

#### F3 (medium). Replay-protection timestamp tolerance is 500 seconds
- **Location:** `fudp/security/ReplayProtection.java:26` — `TIMESTAMP_TOLERANCE_MS = 500_000L`.
- **Problem:** 8 minutes 20 seconds of allowed clock drift is very permissive. The nominal reason to have any tolerance is clock skew between peer and self; 30 seconds is adequate for well-configured NTP systems and still leaves slack for coarsely-synchronised mobile devices. A tolerance of 500s enlarges the replay window: an attacker who captures a packet and replays it within that window against a peer that has had its connection state torn down and re-established (fresh `receivedBitmap`) can land an old packet as if new.
- **Fix:** Tighten to 60 seconds by default, with a configurable field per connection. Apps with known clock-drift issues can raise it explicitly.
- **Interop impact:** None — tolerance is receiver-side. A peer with a ≥ 1-minute clock drift from us will see their packets rejected, but that's the correct behaviour.

### 3.3 Low — worth fixing while we're already bumping v2

#### F4 (low). No protocol-version negotiation
- **Location:** `fudp/packet/PacketHeader.java` — version field is hardcoded to `1`.
- **Problem:** Future protocol upgrades require every peer to either understand every version, or older peers fail to decrypt in unhelpful ways.
- **Fix:** Bump version to `2` for the changes in this plan, and add a capability-negotiation step: the first packet of a connection includes a `supportedVersions` bitmap in a control frame; both peers pick the highest common version. This is cheap to add now and essential for the *next* repair cycle.
- **Interop impact:** Inherent to v2 rollout. Old v1 peers will need to upgrade or be refused by new v2 peers.

#### F5 (low). `StreamFrame` uses implicit length for the last frame in a packet
- **Location:** `fudp/packet/Packet.java:94-98`, `fudp/packet/StreamFrame.java:28`.
- **Problem:** The last `StreamFrame` in a packet omits its length varint and takes "all remaining bytes" as its payload. This saves 1-2 bytes per packet but means a packet truncated mid-frame is silently "valid" up to the cut, with the last frame padded by garbage. Combined with the F1 (unauthenticated header) issue, an attacker can truncate a packet and get something the receiver parses as valid but containing payload they chose.
- **Fix:** Always encode a length varint, even for the last frame. The 1-2 byte overhead is trivial.
- **Interop impact:** Wire-format-incompatible with v1 (but absorbed into the v2 bump).

## 4. Orthogonal issues surfaced during the same survey

These are **not FUDP-specific** but were found in adjacent crypto code and should be tracked separately. They affect *message-at-rest* and *signed-payload* encoding, not the FUDP wire format — but FUDP handlers that call into `core/crypto/CryptoDataByte.java` inherit them.

| # | Severity | Location | Issue |
|---|---|---|---|
| X1 | high | `core/crypto/CryptoDataByte.java:1188,1212` | MAC verify compares hex-encoded MACs via `String.equals` — not constant-time. Timing attack against HMAC. |
| X2 | medium | `core/crypto/CryptoDataByte.java:1130` | AES-CBC HMAC key **equals** the AES encryption key. No key separation. |
| X3 | medium | `core/crypto/CryptoDataByte.java:1131` (`makeSum4`) | HMAC-SHA-256 truncated to 4 bytes = 32 bits of auth strength. |
| X4 | low | `core/crypto/HKDF.java:12` | Class named `HKDF` actually uses HMAC-SHA-512 internally. Misleading. |
| X5 | medium | `core/crypto/X25519.java:51-61` | X25519 path bypasses HKDF; uses raw `sha512(nonce‖shared)`. Incompatible with the `Ecc256K1Hkdf` derivation path — same shared secret produces different keys. |

These should ideally be fixed in the same sweep (they're small), but they're not blocking for FUDP v2 since FUDP's `AsyTwoWay` path uses `AesGcm256` which doesn't hit the MAC issues.

## 5. Proposed FUDP v2 spec

### 5.1 Header (unchanged bytes; semantics change)

21 bytes fixed, big-endian:

| Offset | Length | Field |
|---|---|---|
| 0 | 1 | flags (type in bits 0-1, FIN at 4, HAS_TIMESTAMP at 5, HAS_EPOCH at 6) |
| 1 | 4 | version (int32) — **set to 2** |
| 5 | 8 | connectionId (int64) |
| 13 | 8 | packetNumber (int64) |

**Change from v1:** this 21-byte block is now the **AAD** for the payload AEAD. No byte-level change; just a semantics addition.

### 5.2 Payload

Unchanged from v1:
- optional 8 bytes timestamp (if `HAS_TIMESTAMP`)
- optional 8 bytes sessionEpoch (if `HAS_EPOCH`)
- frames

**Change from v1 (F5):** the last `StreamFrame` in a packet now carries an explicit length varint like every other frame.

### 5.3 Encryption (AsyTwoWay bundle, unchanged on the wire *except* for AAD)

- 6 bytes algId
- 1 byte encryptType (`0x02` for AsyTwoWay)
- 33 bytes sender compressed secp256k1 pubkey
- 12 bytes IV
- N bytes AES-GCM ciphertext || 16-byte tag

**Change from v1 (F1):** AES-GCM now authenticates the 21-byte on-the-wire packet header as AAD. The AAD is *not* transmitted — both sides already have it.

```
AAD = packet header bytes [0..21)
AES-GCM.seal(key=sessionKey, nonce=IV, plaintext=payload, aad=AAD) → ciphertext || tag
```

Decryption tests the tag against `AAD=received_header`. Any bit flipped between the attacker and the receiver on the header fails the tag and the packet is dropped.

### 5.4 Replay protection

**Change (F3):** `TIMESTAMP_TOLERANCE_MS = 60_000L` (was 500 000). Window size, sliding-bitmap, and session-epoch restart detection are unchanged.

### 5.5 ECDH cache

**Change (F2):** hard-bounded LRU (512 entries recommended, configurable). Cache key unchanged (hex of peer compressed pubkey). Value unchanged (32-byte shared secret).

### 5.6 Version negotiation

**Change (F4):** first packet of every new connection MUST include a `CAPS` control frame listing supported versions. Both peers AND together, pick the highest bit. If the result is `0`, the connection is refused with an `ERROR` packet carrying code `NO_COMPATIBLE_VERSION`. Existing implementations send `version=1` on the header but are free to negotiate up.

Proposed `CAPS` frame body:

| Offset | Length | Field |
|---|---|---|
| 0 | 1 | frameType (new value: `CAPS = 0x05`) |
| 1 | 2 | versionBitmap (LE uint16; bit N set ⇒ supports version N) |
| 3 | 1 | reserved (0) |

## 6. Coordination strategy

### Option (A) — Flag-day cutover

All three codebases ship v2 simultaneously. Old v1 clients are refused after the cutover date.

- **Pro:** simplest, cleanest. No compat matrix to reason about.
- **Con:** requires every Android user to update before the cutover; stragglers lose service.

### Option (B) — Negotiated overlap (recommended)

1. Ship v2-capable Linux server first. Server advertises `supportedVersions = {1, 2}`. Existing Android v1 clients continue to work unchanged.
2. Ship Android client update that advertises `{1, 2}` and prefers v2 when the server supports it.
3. Start the Mac client on v2-only.
4. After ~90 days (or whatever window covers >95% of Android upgrades), drop `1` from the server's advertised list. At that point Android clients that haven't updated fail over to an in-app update prompt.

This spreads the rollout over client update cycles and avoids a hard flag-day for users.

**Recommended: B.**

## 7. Testing strategy

For each of the five items (F1–F5):

1. **Unit-level parity test.** Test vectors emitted from the Java side are consumed by Swift (once Mac port resumes). The FreerForMac project has a working `tools/vector-gen/` pattern ready for this.
2. **Wire-format negative test.** For F1: flip a single bit in each 21-byte header field and assert the receiver rejects with AEAD tag failure. For F5: truncate the last byte of a packet and assert rejection.
3. **Three-end interop matrix.** Linux server ↔ Android client and Linux server ↔ Mac client, on both v1 (until sunset) and v2. Record the matrix as a doc in `FC-JDK/Docs/`.
4. **Soak/load test.** ECDH-cache LRU (F2) needs a 10k-ephemeral-pubkey flood test to confirm bounded memory and no eviction of active peers.

## 8. Out of scope for FUDP v2

The X1–X5 items in §4 are orthogonal and should move through a separate repair cycle in `core/crypto/`. Listed here only so they are not forgotten.

Congestion control (CUBIC constants in `CongestionControl.java`) is not touched by this plan — the survey did not find any correctness issue there, only some aggressive tuning for LAN (initial window 120 000 bytes). If production latency data suggests retuning, handle that as a separate RFC.

File-transfer semantics (chunking, parallel stream, integrity per file, resume on interrupt) already live in the FUDP stream layer and the FAPI message layer. The repair in this doc does not change file-transfer APIs. If robustness issues specific to large-file transfer surface during the v2 work, add them to this doc or a sibling.

## 9. Cross-references

- **Android client survey (full)** lives only in conversation memory; findings above are the distilled form.
- **Android bugs doc** (living log): `/Users/liuchangyong/AndroidStudioProjects/Freer/docs/android-issues-to-fix.md` — entries S6–S10, C1, C4–C8 cover the same ground as F1–F5 and X1–X5 here, with shorter per-entry rationale.
- **Mac port plan** (paused on Phase 4 until v2 lands): `/Users/liuchangyong/MacApp/FreerForMac/PLAN.md`.
- **Mac port repo:** https://github.com/nobodyoffc/freer-mac

## 10. Next steps

For the incoming Linux-side repair conversation:

1. **Confirm that Linux `FC-JDK/src/main/java/fudp/` mirrors each Android finding** before touching code. The analogous file paths are the same (`packet/PacketHeader.java`, `crypto/CryptoManager.java`, etc.); just verify the issues exist in the Linux tree too.
2. **Read the existing `FUDP_*.md` docs in this folder** (`FUDP_CHAT_TEST_ANALYSIS.md`, `FUDP_ENCRYPT_MODE_IMPROVEMENT.md`, `FUDP_FAPI_ECONOMIC_REFACTOR.md`). If they already propose v2-like fixes, align this plan with theirs rather than double-planning.
3. **Start with F1 (header AAD)** — it's the highest-severity item and a pure additive change to the encrypt/decrypt call signatures. Can be implemented on Linux first, gated behind the v2 version byte, then Android and Mac follow.
