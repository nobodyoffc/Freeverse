# FAPI16V1_CALL

|Field|Content|
|---|---|
|Title|CALL|
|Type|FAPI|
|SN|16|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-09-29|
|PID||

## Contents

- [Abstract](#abstract)
- [Summary](#summary)
- [1. Overview](#1-overview)
- [2. Concepts](#2-concepts)
  - [2.1. Calls and Meetings](#21-calls-and-meetings)
  - [2.2. Transport Keys and Delegations](#22-transport-keys-and-delegations)
  - [2.3. The Admission Key](#23-the-admission-key)
  - [2.4. Routes and Streams](#24-routes-and-streams)
  - [2.5. What the Relay Can See](#25-what-the-relay-can-see)
- [3. API List](#3-api-list)
- [4. Method Definitions](#4-method-definitions)
  - [4.1. Common Rules](#41-common-rules)
  - [4.2. call.create](#42-callcreate)
  - [4.3. call.register](#43-callregister)
  - [4.4. call.join](#44-calljoin)
  - [4.5. call.leave](#45-callleave)
  - [4.6. call.control](#46-callcontrol)
  - [4.7. call.hand](#47-callhand)
  - [4.8. call.rekey](#48-callrekey)
  - [4.9. call.prove](#49-callprove)
  - [4.10. call.report](#410-callreport)
  - [4.11. call.info](#411-callinfo)
  - [4.12. call.stats](#412-callstats)
- [5. Notices](#5-notices)
- [6. Media on the Relay](#6-media-on-the-relay)
  - [6.1. The Media Frame Header](#61-the-media-frame-header)
  - [6.2. Forwarding](#62-forwarding)
  - [6.3. Speaker Selection](#63-speaker-selection)
  - [6.4. Attestations](#64-attestations)
- [7. Host Role](#7-host-role)
- [8. Charging](#8-charging)
- [9. Limits and Timers](#9-limits-and-timers)
- [10. Error Codes](#10-error-codes)
- [11. Security Considerations](#11-security-considerations)
- [12. Versioning](#12-versioning)
- [13. References](#13-references)

---

## Abstract

FAPI16V1 specifies the CALL component of the FAPI protocol series: a relay for end-to-end encrypted voice. CALL forwards opaque, sealed audio frames between the participants of a 1:1 call or a meeting, selects which speakers each participant receives, passes on the senders' signed attestations, and charges the call's payer per minute of data. It never holds a key that opens the audio. This document defines the eleven CALL methods, the notices the relay pushes, the parts of the media frame and attestation formats the relay reads, speaker selection, the host role, charging, limits and error codes. The end-to-end layer that clients run on top of CALL — signalling, key derivation, frame encryption and attestation checking — is specified in FIMP5.

## Summary

A participant reaches CALL over its own FUDP connection, authenticated under a throwaway transport key that its FID has delegated for this one call. The relay admits it to a call only on a signature under the call's admission key, which proves it holds the call secret without revealing it. Audio travels as FUDP DATAGRAM frames; the relay binds each frame to the connection that sent it by the frame's route id and ssrc, drops frames that do not match, and forwards the rest unchanged to the other participants — in a meeting, only the loudest few. Attestations travel as reliable NOTIFYs and are forwarded the same way. The creator of a call pays for everyone's data, per minute. A relay operator registers CALL like any other FAPI component; a user chooses one by setting `home["CALL@No1_NrC7"]`.

**Type ID**: `CALL@No1_NrC7`

## 1. Overview

A voice call cannot always go directly between its participants: NATs block it, and a meeting of many would cost every sender one upload per listener. CALL is the relay that carries it when it cannot. It is a selective forwarding unit: it does not mix or decode audio, it only chooses which sealed frames to pass to whom.

CALL is built so that the relay can be run by anyone without being trusted with the audio:

- **It holds no key that opens a frame.** Frames are sealed end to end by their senders (FIMP5). The relay sees each frame's header, which the seal authenticates, so it cannot alter it either.
- **It cannot impersonate a participant.** Every member of a meeting holds the call secret and could seal a frame claiming any sender; the relay stops that on its own connections by binding each stream to the connection that joined with it, and receivers stop it end to end by checking each sender's signed attestations.
- **It learns FIDs only through delegations** the participants choose to show it, and it learns nothing it could use to join or to decrypt.

A CALL relay depends on a co-hosted MAP component (FAPI14) only for its clients' own `map.register` calls. It performs no on-chain lookups.

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this document are to be interpreted as described in RFC 2119.

## 2. Concepts

### 2.1. Calls and Meetings

The relay keeps one record per call, keyed by `meetingId`, of one of two kinds:

- **`p2p`**: a 1:1 call. Its `meetingId` is the call's `callId`, 16 random bytes in hex (32 hex digits). It has at most 2 participants by default. Its admission key is registered after the callee answers (§4.3). It has no host controls and is never rekeyed.
- **`meeting`**: a meeting in a Room or a Team. Its `meetingId` is `"mtg_"` followed by 12 random bytes in lowercase hex. It holds up to 64 participants. Its admission key is given at creation.

A call record holds the `meetingId`, the kind, the host FID, the admission key and key epoch, the participant list, the pinned streams and the forwarding state. The relay stores no audio and keeps nothing once the call closes.

### 2.2. Transport Keys and Delegations

For every call or meeting it joins, a client makes a fresh secp256k1 keypair `(tPriv, tPub)` and connects to the relay with a FUDP connection that authenticates as `tPub`. Its FID's key never touches the relay. The FID vouches for `tPub` with a **delegation**:

```json
{
  "fid": "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK",
  "fidPub": "02…(33 bytes hex)",
  "tPub": "03…(33 bytes hex)",
  "expiresSec": 1790000000,
  "sig": "…(64 bytes hex)"
}
```

where

```
sig = Schnorr(fidPriv, "FreerCall-delegate-v1" ‖ str(meetingId) ‖ tPub(33) ‖ u64(expiresSec))
```

`Schnorr` is the BCH Schnorr signature FIMP0V3 uses, over SHA-256d of the preimage; `str(x)` is a 2-byte big-endian length followed by the UTF-8 bytes; integers are big-endian. The exact derivations, with test vectors, are in FIMP5 §4.

A delegation verifies when `fidPub` hashes to `fid`, the signature verifies, `expiresSec` has not passed and lies no more than 24 hours ahead, and the id it names is the call in hand. The relay treats the connection's peer as that FID for this call only.

### 2.3. The Admission Key

Every call has a secret that only its participants can derive (FIMP5 §4.2). From it they derive a secp256k1 keypair `(authPriv, authPub)`. The host gives the relay only `authPub`. A joiner proves it holds the call secret by signing, with `authPriv`, a statement binding its own transport key and stream:

```
admitSig = Schnorr(authPriv, "FreerCall-admit-v1" ‖ str(meetingId) ‖ tPub(33) ‖ u32(ssrc) ‖ u64(ts))
```

`ts` is the signer's clock in milliseconds. The relay learns neither the call secret nor anything that would let it derive the secret or the audio keys.

When a meeting's key changes (§4.8), a new `authPub` starts a new **key epoch**, and each participant proves the new key with a fresh `admitSig` (§4.9).

### 2.4. Routes and Streams

Each participant sends one audio stream, named by its **ssrc**, a random 32-bit number it picks for each join. On admitting it, the relay assigns it a **routeId**, a random 32-bit handle, unique in the call. Both travel in the header of every frame the participant sends (§6.1), and the relay forwards a frame only if both match the connection it arrived on.

### 2.5. What the Relay Can See

Who is in each call (the FIDs in their delegations), who is sending, how loud (the `level` in each frame header), how often, and for how long. It cannot see or change the audio, and it cannot tell a frame's content from any other's.

## 3. API List

| # | API | Who | Description |
|---|---|---|---|
| 1 | `call.create` | host | Open a call or meeting |
| 2 | `call.register` | host, `p2p` only | Give the admission key once the callee has answered |
| 3 | `call.join` | anyone | Join, proving the call key |
| 4 | `call.leave` | participant | Leave |
| 5 | `call.control` | host | Moderate: mute, lock a mute, kick, pin, unpin, hand over the host role, end |
| 6 | `call.hand` | participant | Raise or lower one's hand |
| 7 | `call.rekey` | host, meetings | Start a new key epoch |
| 8 | `call.prove` | participant | Prove the new key epoch's key |
| 9 | `call.report` | participant | Report downlink loss |
| 10 | `call.info` | anyone | Whether a call is open, and how many are in it |
| 11 | `call.stats` | anyone | Operator counters |

No method carries binary data; all requests and responses are JSON (FAPI1). Audio and attestations do not travel as requests (§6).

## 4. Method Definitions

### 4.1. Common Rules

- **Connection.** Every request travels over the participant's FUDP connection. The relay identifies a participant by that connection.
- **Delegation.** Every request other than `call.leave`, `call.info` and `call.stats` carries `delegation`, as the JSON object of §2.2 or its string form. The relay MUST verify it (§2.2) for the request's `meetingId`, and MUST check that its `tPub` is the key the FUDP connection authenticated with; otherwise it refuses with 401. A delegation stolen from one connection is therefore useless on another. The relay admits, names and bills the delegated FID.
- **Numbers.** `ssrc`, `routeId` and `keyEpoch` are unsigned; `ssrc` and `routeId` are sent as JSON numbers in 0..2³²−1.
- **Idempotence.** A client that loses a reply (FAPI 408) retries. `call.create`, `call.register` and `call.join` are safe to retry, as their descriptions say.

### 4.2. call.create

Open a call or a meeting. The delegated FID becomes its host and its payer (§8).

- **Request** `params`:
  - `meetingId` (string, REQUIRED): for `p2p`, 32 hex digits; for `meeting`, `"mtg_"` + 24 lowercase hex digits.
  - `kind` (string, REQUIRED): `p2p` or `meeting`.
  - `delegation` (REQUIRED).
  - `authPub` (hex, 33 bytes): REQUIRED for a meeting; a `p2p` call gives it later with `call.register`.
  - `maxParticipants` (integer, OPTIONAL): 2 to 64. Defaults to 2 for `p2p`, 64 for a meeting.
  - `maxCostPerMinute` (integer, OPTIONAL, meetings): the most the host will pay for the whole meeting per minute, in the service's smallest unit. 0 or absent means no cap.
- **Response** `data`: `price` {`perKBIn`, `perKBOut`}, `maxParticipants`, `speakers` (the number of loudest speakers forwarded, §6.3).
- **Checks:** the host MUST be able to pay for one minute of at least two participants (§8), or the relay refuses with 402 before anything else happens; a caller's app learns so before the callee is rung. A host has at most 4 open calls (429).
- **Retry:** a repeated create for an existing `meetingId` is refused with 409 `meeting exists`. A client whose first create's reply was lost reads that as success.

```json
{
  "api": "call.create",
  "params": {
    "meetingId": "mtg_79dfacb1e958d3b57cc98c02",
    "kind": "meeting",
    "authPub": "02c1…",
    "delegation": { "fid": "FEk4…", "fidPub": "03…", "tPub": "02…", "expiresSec": 1790000000, "sig": "…" }
  }
}
```

```json
{ "code": 0, "message": "OK", "data": { "price": { "perKBIn": 1, "perKBOut": 1 }, "maxParticipants": 64, "speakers": 3 } }
```

### 4.3. call.register

A `p2p` host gives the admission key once the callee has answered, and so could derive the call secret (FIMP5 §6.2). Until then the relay admits only the host.

- **Request** `params`: `meetingId`, `delegation`, `authPub` (hex, 33 bytes).
- **Response** `data`: `{}`.
- **Checks:** only the host (403). Registering the same `authPub` again succeeds; a different one is refused with 409.

### 4.4. call.join

Join a call and start sending and receiving.

- **Request** `params`:
  - `meetingId`, `delegation` (REQUIRED).
  - `ssrc` (number, REQUIRED): this join's stream, random, not in use in the call.
  - `ts` (number, REQUIRED): milliseconds; MUST be within ±60 s of the relay's clock.
  - `admitSig` (hex, 64 bytes): REQUIRED once the call's `authPub` is known (§2.3), from every joiner, the host included.
  - `maxCostPerMinute` (integer, OPTIONAL): caps this participant's own charge per minute.
  - `candidates` (array, OPTIONAL): up to 8 of `{"t": "lan", "a": "ip:port"}` or `"[ipv6]:port"` — the joiner's private addresses, for a direct path (FIMP5 §6). Host names are refused.
  - `reflexive` (boolean, OPTIONAL): if true, the relay adds `{"t": "map", "a": <the address it sees the joiner's connection at>}` to the joiner's candidates.
- **Response** `data`:
  - `routeId` (number): this participant's handle (§2.4).
  - `datagram` (true): the relay accepts DATAGRAM frames on this connection (FUDP7 §2.3).
  - `roster` (array): everyone in the call, as in the `roster` notice (§5).
  - `host` (string): the host FID. The joiner gets no roster notice of its own join, so it learns the host here.
  - `keyEpoch` (number): the current key epoch.
  - `speakers` (number): see §6.3.
- **Admission:**
  - If the call's `authPub` is known, `admitSig` MUST verify under it for this `meetingId`, the delegation's `tPub`, `ssrc` and `ts`; otherwise 401.
  - If it is not yet known (a `p2p` call whose host has not registered), only the host may join. Anyone else is refused with 409 `not open yet`, and the relay sends the host a `knock` notice carrying the joiner's delegation (§5). The joiner retries (FIMP5 §6.2).
- **Replay:** the relay remembers each `(meetingId, tPub, ts)` it admitted and refuses a repeat with 409 `join replayed`. A retry from the **same connection** for the same call and `ssrc`, whose first reply was lost, is answered with the same result instead.
- **Other refusals:** 409 `ssrc in use`; 409 `this connection is already in a call` (a connection carries one join); 403 `meeting full`; 403 if the FID was kicked from this call; 402 if the payer cannot cover one minute of this participant at the full speaker count.
- **Effects:** the participant is added, datagrams are enabled on its connection, and everyone else gets a `roster` notice.
- **Two devices, one FID:** two joins with different transport keys are separate participants, with their own ssrc and routeId.

```json
{
  "api": "call.join",
  "params": {
    "meetingId": "4c1d…(32 hex)",
    "ssrc": 3021554871,
    "ts": 1790000001234,
    "admitSig": "…",
    "reflexive": true,
    "candidates": [ { "t": "lan", "a": "192.168.1.23:52113" } ],
    "delegation": { "…": "…" }
  }
}
```

```json
{
  "code": 0, "message": "OK",
  "data": {
    "routeId": 912004411, "datagram": true, "host": "FEk4…", "keyEpoch": 0, "speakers": 1,
    "roster": [
      { "fid": "FEk4…", "ssrc": 1180044, "routeId": 3301, "delegation": "{…}", "keyEpoch": 0,
        "candidates": [ { "t": "lan", "a": "192.168.1.9:61002" }, { "t": "map", "a": "203.0.113.7:61002" } ] },
      { "fid": "F86z…", "ssrc": 3021554871, "routeId": 912004411, "delegation": "{…}", "keyEpoch": 0,
        "candidates": [ { "t": "lan", "a": "192.168.1.23:52113" }, { "t": "map", "a": "198.51.100.4:40211" } ] }
    ]
  }
}
```

### 4.5. call.leave

Leave the call this connection is in. Best effort; a participant that disappears is removed after 30 s anyway (§9).

- **Request** `params`: `meetingId`. No delegation: the connection identifies the participant.
- **Response** `data`: `{}`.
- **Effects:** the participant's part-minute is charged (§8); the others get a `roster` notice; if the host left and no other device of its FID remains, the host role passes on (§7).

### 4.6. call.control

The host moderates a meeting. Not available in a `p2p` call (400).

- **Request** `params`: `meetingId`, `delegation`, `action`, and for every action but `end`, `target`: a FID (every device of it in the meeting) or an `ssrc` (number).

| `action` | Effect |
|---|---|
| `mute` | The relay drops the target's frames and sends it a `muted` notice. The target MAY lift it with its own `unmute`. |
| `lockMute` | As `mute`, but only the host can lift it. |
| `unmute` | Lifts a mute. Allowed to the host, and to a participant for itself after a plain `mute`. |
| `kick` | Sends the target `kicked` (`reason: host`), removes it, and bars its FID from rejoining this meeting. The host cannot kick itself (400). |
| `pin` | Forwards the target to everyone on top of the loudest speakers; at most 2 pins (429). |
| `unpin` | Undoes `pin`. |
| `handoverHost` | Makes the target the host. |
| `end` | Sends everyone `ended` and closes the meeting at once, charging each part-minute. |

- **Response** `data`: `{}`. Every action but `kick` and `end` then pushes a `roster` notice, which shows mutes (`muted`: `host` or `locked`) and the host.
- **Checks:** only the host (403), except a participant's own `unmute` of a plain mute; a target not in the meeting (404); an unknown action (400).

A kicked participant still holds the meeting's key; only a key change stops it decrypting what follows (FIMP5 §8.4).

### 4.7. call.hand

- **Request** `params`: `meetingId`, `delegation`, `raised` (boolean).
- **Response** `data`: `{}`. Everyone gets a `roster` notice showing `hand: true` on that participant's entry.

### 4.8. call.rekey

The host moves a meeting onto a new key, after the Room's or Team's owner rotated its symkey (FIMP5 §8.4). Not available in a `p2p` call (400).

- **Request** `params`: `meetingId`, `delegation`, `symkeyVersion` (number), `nonce` (hex, 32 bytes), `authPub` (hex, 33 bytes, the new admission key).
- **Response** `data`: `keyEpoch`, the new epoch.
- **Effects:** the key epoch goes up by one; new joiners are admitted only under the new `authPub`; every participant gets a `rekey` notice with the new key's parameters and 30 s to prove it (§4.9). Those who have not proved it after 30 s get `kicked` with `reason: rekey` and are removed.
- **Checks:** only the host (403); the current `authPub` again is refused with 409.

### 4.9. call.prove

A participant proves it holds the new key epoch's key.

- **Request** `params`: `meetingId`, `delegation`, `keyEpoch` (the current epoch), `ts`, `admitSig` under the new `authPriv` for the participant's own `tPub` and `ssrc`.
- **Response** `data`: `keyEpoch`.
- **Effects:** the participant's proven epoch is recorded, and if it changed, everyone gets a `roster` notice. Each roster entry carries its participant's proven `keyEpoch`, which is how senders know when everyone can open frames under the new key (FIMP5 §8.4).
- **Checks:** not in the meeting (404); not the current epoch (409); `ts` outside ±60 s (400); `admitSig` not verifying (401).

### 4.10. call.report

A receiver reports its downlink loss, which the relay uses to reduce what it sends (§6.3).

- **Request** `params`: `meetingId`, `delegation`, `streams`: an object mapping each ssrc to `{loss, lateLoss, jitterMs}` over the last 2 s, `loss` and `lateLoss` as fractions.
- **Response** `data`: `speakers`, the number of loudest speakers this receiver now gets.

### 4.11. call.info

Public: no delegation. Clients use it for a meeting card, to confirm that a meeting another member says has ended is gone, and while ringing, to learn that a call was answered elsewhere or abandoned.

- **Request** `params`: `meetingId`.
- **Response** `data`: `open` (boolean) and `participants` (number); when open, also `kind` and `started` (milliseconds).

### 4.12. call.stats

Public operator counters, like `road.stats`: `meetings`, `participants`, `framesIn`, `framesOut`, `framesDropped`, `framesNotSelected`, `framesMuted`, `packedSends`, `attestationsForwarded`, `minutesCharged`.

## 5. Notices

The relay pushes notices as FUDP NOTIFY messages with `dataType = 1`, whose payload is a JSON object with a `type` and the `meetingId`. A client MUST ignore a notice whose `type` it does not know.

| `type` | To | Fields | When |
|---|---|---|---|
| `roster` | every participant but the one whose join caused it | `host`, `roster`: array of `{fid, ssrc, routeId, delegation, keyEpoch, muted?, hand?, candidates?}` | Someone joined, left, proved a key; a mute, a hand or the host changed. `delegation` is the JSON the participant sent, as a string, so receivers verify it themselves. `keyEpoch` is the epoch that participant has proved. `muted` is `host` or `locked`. |
| `knock` | the host | `fid`, `delegation` | A join was refused with 409 because the `p2p` host has not registered `authPub`. The delegation is the joiner's own, which the host verifies as it would an answer (FIMP5 §6.2). |
| `rekey` | every participant | `symkeyVersion`, `nonce`, `authPub`, `keyEpoch`, `proveWithinSeconds` | `call.rekey`. |
| `muted` | the target | `locked` (boolean) | `mute` or `lockMute`. |
| `kicked` | the target | `reason`: `host`, `rekey` or `balance` | Removed by the host, for not proving a new key in time, or unpaid (§8). |
| `ended` | every participant | — | `call.control end`. |
| `balance` | the payer's devices | `graceSeconds` | A minute could not be paid (§8). |
| `uplink` | each sender in a meeting, every 2 s | `loss`, `jitterMs` | The fraction of the sender's frames lost on the way to the relay, and their interarrival jitter, which only the relay can see. A gap before a frame flagged DTX is a pause, not loss. |

## 6. Media on the Relay

### 6.1. The Media Frame Header

Audio travels as the payload of FUDP DATAGRAM frames (FUDP7), one sealed media frame each, or several packed into one packet (§6.2). The relay reads only the header, which the seal authenticates:

```
MediaFrame {
  kind      (1)  0x01
  flags     (1)  bit0 VAD (voice active), bit1 DTX (frames just before were not sent),
                 bit7 CONTROL, bits 2-6 zero
  routeId   (4)  assigned at join; 0 on a direct path between two clients
  ssrc      (4)
  seq       (8)
  timestamp (4)  48 kHz sample clock
  level     (1)  0..127 = -dBov (RFC 6464); 127 is silence
  keyEpoch  (1)
  ciphertext     AES-256-GCM, 16-byte tag included; opened only by participants (FIMP5 §5)
}
```

All integers are big-endian; the header is 24 bytes. A datagram whose `kind` is not `0x01`, that sets a reserved flag bit, or that is shorter than the header and a tag is not a media frame, and is dropped.

### 6.2. Forwarding

For each DATAGRAM, the relay:

1. Finds the sender by its connection; drops the frame if the connection is in no call.
2. Drops the frame unless its `routeId` and `ssrc` are the ones that connection joined with, or if the sender is over its inbound rate (§9). **This binding is what stops one participant sending as another on the relay:** every member of a meeting holds the call key, so the seal alone cannot.
3. Counts the frame against the sender's bytes in (§8), and, if VAD is set, its `level` towards speaker selection.
4. Drops the frame if the host has muted the sender.
5. Forwards it, byte for byte, to every other participant for whom the sender is currently selected (§6.3). A participant never receives its own frames.
6. In a meeting, remembers for each receiver and sender which `seq`s it forwarded, so it can pass on the matching attestations (§6.4).

In a meeting the relay SHOULD pack each receiver's frames of one moment into one packet, waiting up to 10 ms for the rest of the moment's speakers: fewer packets cost less for everyone (§8).

### 6.3. Speaker Selection

In a `p2p` call each side receives the other. In a meeting, every 100 ms the relay ranks the streams by their smoothed `level` over the last 300 ms, counting only frames with VAD set, and forwards:

- the loudest **N** (3 by default, at most 5), plus any pinned streams;
- a new speaker displaces a selected one only when it has been at least 6 dB louder for 300 ms, so the selection does not flap;
- a receiver whose `call.report` shows loss above 10 % on any stream gets one speaker fewer, down to 1, and one back after 10 s without such loss.

Each receiver decodes and mixes the streams it gets itself.

### 6.4. Attestations

Senders sign what they sent, so that receivers can tell which participant a frame came from (FIMP5 §5.1). An attestation travels as a FUDP NOTIFY with `dataType = 0`, reliably, not as a datagram:

```
Attestation {
  kind      (1)  0x02
  routeId   (4)
  ssrc      (4)
  firstSeq  (8)
  count     (1)  1..64
  digests   (8 × count)
  sig       (64) Schnorr(tPriv, "FreerCall-attest-v1" ‖ str(meetingId) ‖ every byte above)
}
```

The relay:

- MUST forward an attestation only if its `routeId` and `ssrc` are those of the connection it came from;
- MUST forward it unchanged: in a `p2p` call to the other participant, in a meeting to every participant it forwarded at least one frame of that `ssrc` in `firstSeq .. firstSeq+count−1`;
- MAY verify attestations itself, and MAY remove a participant whose attestations do not match the frames it relayed.

## 7. Host Role

Being the host is a relay role, not a role in the Room or Team. The host is the FID that created the call until:

- it hands the role over with `handoverHost`, or
- it leaves, and no other device of its FID is present: the role passes to the participant who has been present longest.

Every roster notice names the current host. Only the host may `call.control` (with the one `unmute` exception), `call.rekey` and `call.register`; the relay checks these against the delegated FID. Admission (`call.join`) depends only on `admitSig`, never on the host.

The payer does not change with the host: it is always the FID that created the call (§8).

A relay with a BASE component MAY also check a Team meeting's joiners against the Team's on-chain members. It cannot do so for a Room, which has no chain record.

## 8. Charging

CALL follows FAPI4, measured per **minute** of each participant's presence rather than per request.

- **Who pays:** the FID that created the call, for every participant: in a 1:1 call the caller pays for both sides, and in a meeting the host who created it pays for everyone. Members and callees need no account at the relay.
- **What a minute costs**, per participant:

  ```
  minuteCost = ceil(bytesIn / 1024) · pricePerKBIn + ceil(bytesOut / 1024) · pricePerKBOut
  ```

  where `bytesIn` is what that participant sent to the relay in the minute, and `bytesOut` what the relay sent it. The prices are the service's on-chain `pricePerKBIn` and `pricePerKBOut`, falling back to `pricePerKB` (FAPI3 §3). An operator MAY set them to zero for a free relay.
- **When:** at the end of each minute of a participant's presence, counted from its join, and for the part-minute when it leaves or is removed. The charge key is `call:<meetingId>:<fid>:<ssrc>:<minute>`, so a charge is never taken twice (FAPI4 §5.2).
- **Caps:** a participant's `maxCostPerMinute` caps its own minute; a meeting's `maxCostPerMinute` caps the whole meeting per wall-clock minute.
- **Before starting:** `call.create` requires the payer to afford a minute of two participants, and `call.join` a minute of the joiner at the full speaker count; otherwise 402. The relay's estimate is 470 KB in and 375 KB out per speaker per minute.
- **Running out:** if a minute cannot be charged, the relay sends `balance` with `graceSeconds = 60` to the payer's devices. If still unpaid 60 s later, it sends `kicked` with `reason: balance` to each unpaid participant and removes it. In a 1:1 call that ends the call.

For scale, measured with 24 kbps Opus in 40 ms frames and 3 of 40 participants speaking: about 0.46 MB a minute in per speaker, and about 1.1 MB a minute out per participant, of which about 0.7 MB is audio and the rest per-packet overhead and attestations.

## 9. Limits and Timers

| Limit | Value |
|---|---|
| Participants per call | 64 (a `p2p` call: 2 by default) |
| Open calls per host FID | 4 |
| `call.join` attempts per transport key | 10 per minute (429 beyond) |
| Candidates per join | 8 |
| Clock skew for `ts` | ±60 s |
| Inbound media per participant | 64 kbps; frames beyond are dropped |
| Pins | 2 |
| Proving a new key | 30 s |
| Nothing heard from a participant, not even an ACK | 30 s, then it is removed without a notice and charged to that moment |
| An empty call | closed after 60 s |
| Unpaid grace | 60 s |

A live participant is never quiet for 30 s: muted or silent, a client still sends a DTX update every 400 ms or so, and it acknowledges the relay's notices.

The relay SHOULD keep its UDP receive buffer small rather than raise it to absorb bursts. A relay that falls behind should drop frames: a deep buffer turns overload into seconds of delay on every frame, and audio that late is worthless.

## 10. Error Codes

Refusals are FAPI responses with these codes (FAPI1), and a message saying why.

| Code | Meaning |
|---|---|
| 400 | Malformed: a missing or badly formed field, a `meetingId` of the wrong shape, an unknown `kind` or `action`, `ts` out of range, a candidate that is not `lan` with a numeric address, a host control on a `p2p` call. |
| 401 | The delegation does not verify, is for another call, or is for a transport key other than the connection's; or `admitSig` does not verify. |
| 402 | The payer's balance is below one minute of the call. |
| 403 | Not the host; the meeting is full; the FID was kicked from this meeting; a locked mute lifted by its target. |
| 404 | No such call; the participant is not in it; an unknown method. |
| 409 | `meeting exists`; `not open yet` (the `p2p` host has not registered); `ssrc in use`; `join replayed`; `this connection is already in a call`; a different `authPub` for `call.register`; the current `authPub` for `call.rekey`; not the current key epoch for `call.prove`. |
| 429 | Too many calls for this host, joins for this key, or pins. |

## 11. Security Considerations

1. **The relay cannot hear.** Frames are sealed end to end under keys derived from a call secret the relay never learns (FIMP5 §4). `authPub` and `admitSig` reveal nothing that derives it.
2. **The relay cannot alter a frame.** The header is the seal's associated data; a changed `level`, `seq` or anything else fails authentication at the receiver. So the relay forwards frames unchanged.
3. **Participants cannot send as each other through the relay.** A frame's `routeId` and `ssrc` must match the connection it arrived on (§6.2). This does not rely on the key, which every meeting member holds.
4. **A dishonest relay cannot make one participant sound like another.** It could forward a member's frames under another's roster entry, but receivers check every frame against its sender's signed attestations, under the transport key the sender's own delegation names (FIMP5 §5.1). A relay that withholds attestations only pauses the audio.
5. **Delegations are bound to connections.** A delegation replayed from another FUDP connection fails, because the relay checks its `tPub` against the connection's key.
6. **Joins cannot be replayed.** `admitSig` covers the joiner's own `tPub`, its `ssrc` and a timestamp within ±60 s; the relay refuses a repeated `(meetingId, tPub, ts)`.
7. **A kicked member still holds the key.** Kicking removes it from the relay only. Only a new key, from the owner's symkey rotation followed by `call.rekey`, stops it decrypting what follows.
8. **What the relay learns** is listed in §2.5: membership, timing and loudness. Clients that want to hide their IP address from the other participants send no candidates (FIMP5 §6).

## 12. Versioning

| Version | Date | Changes |
|---|---|---|
| 1 | 2026-09-29 | Initial specification: eleven methods, notices, media forwarding, speaker selection, attestations, host role, charging, limits and error codes. Written from VOICE_SPEC §5–§7 and FC-JDK's `CallRelay` after the Android and Mac clients interoperated through it. |

## 13. References

- **FAPI1**: Core Protocol. Request and response structures and status codes.
- **FAPI3**: Components. The component model and registration, including prices.
- **FAPI4**: Economics. Balances and idempotent charging.
- **FAPI14**: MAP.
- **FUDP7**: DATAGRAM frames, which carry the audio.
- **FIMP0V3**: the Schnorr signature and the signed envelope.
- **FIMP5**: Calls and meetings — the signalling, key derivations, frame encryption, attestation checking and direct paths clients run over CALL, with test vectors.
- **RFC 6464**: audio level indication.
- **RFC 2119**: requirement levels.
