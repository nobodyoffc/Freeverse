# FIMP5V1_Call

|Field|Content|
|---|---|
|Title|Call|
|Type|FIMP|
|SN|5|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-09-29|
|Updated|2026-09-29|
|PID||

## Contents

- [Abstract](#abstract)
- [1. Overview](#1-overview)
- [2. Conventions](#2-conventions)
- [3. The CALL Content Type](#3-the-call-content-type)
  - [3.1. Envelope](#31-envelope)
  - [3.2. 1:1 Signals](#32-11-signals)
  - [3.3. Call Records](#33-call-records)
- [4. Keys](#4-keys)
  - [4.1. Transport Identity and Delegation](#41-transport-identity-and-delegation)
  - [4.2. The Call Secret](#42-the-call-secret)
  - [4.3. Sender Keys and Nonces](#43-sender-keys-and-nonces)
  - [4.4. The Admission Key](#44-the-admission-key)
- [5. The Media Frame](#5-the-media-frame)
  - [5.1. Signed Attestations](#51-signed-attestations)
- [6. 1:1 Calls](#6-11-calls)
  - [6.1. Whose Relay](#61-whose-relay)
  - [6.2. Setting Up a Call](#62-setting-up-a-call)
  - [6.3. The Direct Path](#63-the-direct-path)
  - [6.4. Ringing and Reaching the Callee](#64-ringing-and-reaching-the-callee)
- [7. Codec](#7-codec)
- [8. Meetings](#8-meetings)
  - [8.1. Meeting Signals](#81-meeting-signals)
  - [8.2. Starting, Joining and Ending](#82-starting-joining-and-ending)
  - [8.3. Meetings of Chosen People](#83-meetings-of-chosen-people)
  - [8.4. Following a Key Rotation](#84-following-a-key-rotation)
  - [8.5. Membership Verification](#85-membership-verification)
  - [8.6. Ringing](#86-ringing)
- [9. Test Vectors](#9-test-vectors)
- [10. Security Considerations](#10-security-considerations)
- [11. Versioning](#11-versioning)
- [12. Related Protocols](#12-related-protocols)

---

## Abstract

FIMP5V1 defines voice calls and meetings between Freer clients: 1:1 calls between two FIDs, and meetings in a Room (FIMP2) or a Team (FIMP4). Calls are signalled with FIMP messages of the new content type `CALL`; audio is Opus, sealed end to end per sender, and carried either through a CALL relay (FAPI16) or, for a 1:1 call between contacts, directly over FUDP. A 1:1 call's secret comes from an ephemeral ECDH, so it is forward secret; a meeting's comes from the Room's or Team's symkey, or, for a meeting of chosen members, from a random key sent to each of them alone. Because every member of a meeting holds its key, each sender also signs what it sent, and receivers play a speaker only while its signatures bear the audio out.

## 1. Overview

A call involves three things:

- **Signalling** over FIMP (§3, §8.1): who is calling whom, on which relay, under which keys. It travels on every channel FIMP has, at once, because a ring is meant to arrive now.
- **Keys** (§4): each call or meeting join makes a throwaway transport key that the FID delegates for this call only. The FID's own key never touches the relay or the media.
- **Media** (§5): each participant's Opus frames, sealed under its own sender key, go through a CALL relay (FAPI16) or directly to the peer (§6.3). Signed attestations let receivers tell who really sent each frame.

The relay forwards sealed frames and learns neither the audio nor any key that would open it.

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this document are to be interpreted as described in RFC 2119.

## 2. Conventions

- `‖` is concatenation. Integers are big-endian and unsigned; `u8`, `u32`, `u64` are 1, 4 and 8 bytes. `ssrc` is a `u32` even where a language stores it signed.
- A quoted literal, such as `"FreerCall v1 p2p"`, is its UTF-8 bytes with no prefix. `str(x)` is a 2-byte big-endian length, then the UTF-8 bytes.
- `HKDF` is RFC 5869 HKDF with **HMAC-SHA512** (FTSP13), output 32 bytes. An empty salt (`∅`) is RFC 5869's all-zero salt.
- `Schnorr(key, m)` is the BCH Schnorr signature FIMP0V3 uses, over SHA-256d of `m`: 64 bytes, deterministic. Every preimage starts with its own literal tag, so a signature made for one purpose never verifies for another.
- `ECDH(a, B)` is the x-coordinate of `a·B`, 32 bytes, on secp256k1.
- A `callId` used as a salt is its 16 raw bytes, not its hex; a meeting `nonce` is its 32 raw bytes.
- Timestamps are milliseconds since the epoch, except `expiresSec`, in seconds.

## 3. The CALL Content Type

### 3.1. Envelope

`CALL` is ContentType ordinal **21**, appended after `ROOM_REMOVED` (FIMP0). A `CALL` message is an ordinary FIMP message:

- signed with the FIMP0V3 trailer, and deduplicated on `(senderId, id)`;
- sealed as its mode requires: `asy2way` for a 1:1 message, the entity's symkey for a message in a Room or Team;
- its `content` is JSON with an `op` field. Fields shown required below MUST be present; a receiver MUST drop a signal that lacks what its `op` needs.

`CALL` messages are signals: except the local records of §3.3 and the meeting cards of §8.1, they are not shown as chat rows. A client that does not know ordinal 21 SHOULD drop such a message quietly rather than show its JSON.

### 3.2. 1:1 Signals

`type = P2P`, `targetId` = the other FID.

| `op` | From | Fields | Meaning |
|---|---|---|---|
| `INVITE` | caller | `callId`, `transportPub`, `delegation`, `relay` {`url`, `pubkey`?, `sid`?}, `expires`, `codecs` = `["opus"]`, `candidates`? | Ring the callee. |
| `ACCEPT` | callee | `callId`, `transportPub`, `delegation`, `candidates`? | Answered. |
| `REJECT` | callee | `callId`, `reason` ∈ {`declined`, `busy`, `unsupported`, `relay`} | Not answered. |
| `CANCEL` | caller | `callId`, `reason` ∈ {`cancelled`, `timeout`, `answered_elsewhere`} | Stop ringing. |
| `HANGUP` | either | `callId`, `duration` (ms) | Ended. |

- `callId` is 16 random bytes in hex. Two calls never share one. A receiver MUST drop a signal whose `callId` is not 16 bytes of hex.
- `transportPub` is the sender's transport key for this call (§4.1), hex; `delegation` is the §4.1 object itself, not a string.
- `relay` names the CALL relay the call runs on (§6.1): its FUDP `url`, and, when the caller learned them connecting, the relay's `pubkey` and FAPI service id `sid`, so the callee connects without discovery.
- `expires` is now + 45 s. A callee MUST NOT ring for an INVITE whose `expires` has passed; such an INVITE, usually the DOCK copy arriving late, is shown as a missed call.
- `candidates` in INVITE and ACCEPT are reserved. Version 1 sends none: candidates travel in the relay's roster (§6.3). A receiver ignores them.
- **Verification.** A callee rings only for an INVITE whose `delegation` is from its FIMP sender, for this `callId`, naming the INVITE's `transportPub`. A caller accepts an ACCEPT only from the callee it rang, under the same check, and sends nothing that depends on the answer before it has verified both the ACCEPT's FIMP signature and its delegation.
- **Busy.** A device already in a call or meeting answers a new INVITE with `REJECT busy`. There is no call waiting.
- **The callee's relay only.** A callee answers an INVITE whose `relay` is not its own `home["CALL@No1_NrC7"]` with `REJECT relay`, and records a missed call (§6.1).
- **Other devices.** A FID may be signed in on several devices, all of which ring. When the caller verifies an ACCEPT, it sends `CANCEL answered_elsewhere` to the callee's FID; the device that answered ignores it, the others stop ringing.
- **Strangers.** An INVITE from a FID the user has not accepted MUST NOT ring. It is held like a stranger's message (FIMP1 §8) and shown as a missed call. Once the user accepts that FID, its later INVITEs ring. A client SHOULD treat writing to or calling a FID as accepting it.

### 3.3. Call Records

A client records each call in the 1:1 chat as a local `CALL` message whose content is

```json
{"record": "ENDED", "outgoing": true, "duration": 252000, "callId": "4c1d…"}
```

with `record` one of `ENDED`, `MISSED`, `DECLINED`, `NO_ANSWER`, `BUSY`, `CANCELLED`, `ANSWERED_ELSEWHERE`. Records are made from the signalling, written into the local chat, and never sent.

## 4. Keys

### 4.1. Transport Identity and Delegation

For each call, and for each join of a meeting, a client makes a fresh secp256k1 keypair `(tPriv, tPub)`. Everything about the call — its FUDP connections to the relay and to the peer, its attestations — runs under `tPriv`. The FID's key only signs:

```
sig = Schnorr(fidPriv, "FreerCall-delegate-v1" ‖ str(callOrMeetingId) ‖ tPub(33) ‖ u64(expiresSec))
```

and the client publishes `{fid, fidPub, tPub, expiresSec, sig}` (hex for keys and signature). A verifier MUST check that `fidPub` hashes to `fid`, the signature verifies, `expiresSec` has not passed and is at most 24 h ahead, and the id is the call or meeting in hand. It then treats the peer `tPub` as `fid` for that call only.

`tPriv` MUST be erased when the call ends or the join is left.

### 4.2. The Call Secret

**1:1**, forward secret:

```
callSecret = HKDF(ikm  = ECDH(tPriv_self, tPub_peer),
                  salt = callId,
                  info = "FreerCall v1 p2p" ‖ str(min(fidA, fidB)) ‖ str(max(fidA, fidB)))
```

**Meeting**, no stronger than the entity's symkey:

```
callSecret = HKDF(ikm  = symkey(entityId, symkeyVersion),
                  salt = nonce,
                  info = "FreerCall v1 meeting" ‖ str(entityId) ‖ u64(symkeyVersion) ‖ str(meetingId))
```

**Meeting of chosen people** (§8.3): the same, with the meeting's random key `K` for the symkey, the `meetingId` for the entity, and version 1:

```
callSecret = HKDF(ikm = K, salt = nonce, info = "FreerCall v1 meeting" ‖ str(meetingId) ‖ u64(1) ‖ str(meetingId))
```

A member may hold two keys at one symkey version (FIMP2 §7.1). It picks the one whose derived `authPub` (§4.4) equals the `authPub` the host announced, without either side sending the key or its hash.

### 4.3. Sender Keys and Nonces

```
senderKey = HKDF(ikm = callSecret, salt = ∅, info = "FreerCall v1 sender" ‖ str(fid) ‖ u32(ssrc) ‖ u8(keyEpoch))
nonce(12) = u32(ssrc) ‖ u64(seq)
```

- `ssrc` is random for every join, and MUST change on rejoin.
- `seq` starts at 0 and increases by one for every encoded frame, including those DTX leaves unsent.
- A receiver derives a sender's key from the roster entry `(fid, ssrc)` and the key epoch, and never from anything the sender offers.

### 4.4. The Admission Key

```
authSeed = HKDF(ikm = callSecret, salt = ∅, info = "FreerCall v1 admit")
authPriv = authSeed mod n      (if zero, retry with info ‖ 0x01, then ‖ 0x01 0x01, …)
authPub  = authPriv · G
```

The relay knows only `authPub`. A joiner proves it holds the call secret with

```
admitSig = Schnorr(authPriv, "FreerCall-admit-v1" ‖ str(meetingId) ‖ tPub(33) ‖ u32(ssrc) ‖ u64(ts))
```

(FAPI16 §4.4). In a 1:1 call the caller registers `authPub` only after the callee has answered, so until then the relay admits only the caller, and afterwards only a peer that completed the ECDH.

## 5. The Media Frame

Each frame is the payload of a FUDP DATAGRAM (FUDP7), the same on the relay and on a direct path:

```
MediaFrame {
  kind      (1)  0x01
  flags     (1)  bit0 VAD, bit1 DTX (the frames just before this one were not sent), bit7 CONTROL, bits 2-6 zero
  routeId   (4)  from call.join; 0 on a direct path
  ssrc      (4)
  seq       (8)
  timestamp (4)  48 kHz sample clock, random start per ssrc
  level     (1)  0..127 = -dBov (RFC 6464); 127 is silence
  keyEpoch  (1)
  ciphertext     AES-256-GCM(senderKey(keyEpoch), nonce, opus, aad = the 24 header bytes), 16-byte tag included
}
```

A receiver MUST drop a frame whose `ssrc` is not in the roster, whose `keyEpoch` is neither current nor within 5 s of being replaced (§8.4), that fails authentication, or whose `seq` it has already seen within a 1024-frame window for that ssrc. It MUST ignore a datagram whose `kind` it does not know.

A 40 ms frame at 24 kbps is about 160 bytes.

### 5.1. Signed Attestations

The seal proves only that the sender holds the call key, which in a meeting every member does. Attestations prove which participant sent a frame.

```
Attestation {
  kind      (1)  0x02
  routeId   (4)
  ssrc      (4)
  firstSeq  (8)
  count     (1)  1..64
  digests   (8 × count)  for seq = firstSeq .. firstSeq+count-1: the first 8 bytes of SHA-256 of the
                         complete MediaFrame, or 8 zero bytes for a seq not sent
  sig       (64) Schnorr(tPriv, "FreerCall-attest-v1" ‖ str(callOrMeetingId) ‖ every byte above)
}
```

**Sender:**

- Sends one attestation a second covering every frame since the previous one, one early if `count` would exceed 64, and a final one before leaving or hanging up.
- Ranges tile with no gaps. A run of unsent seqs that would overflow 64 closes the current attestation, and the next starts at the next sent frame.
- Sends them as FUDP NOTIFY with `dataType = 0`, reliably, not as datagrams; a lost attestation would look like forgery. On a direct path it still sends them.
- Changing `routeId` (joining the relay, or moving to or from a direct path) closes the current attestation first: one attestation carries one `routeId`.

**Receiver:**

1. It verifies each roster entry's delegation itself (§4.1), which gives the `tPub` bound to that FID, and accepts an attestation for an `ssrc` only under the `tPub` of the participant the roster names for it.
2. It plays frames as they arrive and keeps each played frame's digest for 5 s.
3. **In a meeting:**
   - a played frame whose digest does not match its attestation makes that ssrc **unverified** for the rest of the join: it stops playing it, and says that audio claimed to be from that FID could not be verified;
   - a played frame no attestation has covered within 3 s **pauses** that ssrc: none of its frames play, and their digests are kept. When an attestation arrives that matches every held digest, it resumes.
4. **In a 1:1 call** only the two ends hold the key, so a frame that opens can only be the peer's: a late or lost attestation never silences the peer. A mismatch still does.
5. **On a direct path** the FUDP connection itself attributes each frame, since it is accepted only under the peer's delegated `tPub` (§6.3). The receiver keeps no digests for frames that arrive on it and waits for no attestation for them.

At most about 3 s of unattributed audio can play in a meeting before it is caught.

## 6. 1:1 Calls

### 6.1. Whose Relay

A call runs on the callee's terms:

- The relay is the callee's `home["CALL@No1_NrC7"]`, read fresh from the chain when calling. A FID without one cannot be called; setting one is opt-in.
- The callee rings only for an INVITE naming its own CALL service: the same `sid` when its home names the service by id, the same URL when it names a URL. Any other INVITE is answered `REJECT relay`.
- The caller pays (FAPI16 §8).

### 6.2. Setting Up a Call

1. **Caller:** connects to the callee's relay under its transport key, `call.create`s the call (`kind = p2p`) and joins it. Only then does it send the INVITE, with the relay's key and service id. A callee therefore never rings for a call whose relay the caller could not reach.
2. **Callee:** when the user answers, derives the call secret and sends ACCEPT.
3. **Caller:** on a verified ACCEPT, or a `knock` notice from the relay (FAPI16 §5), whichever comes first, derives the call secret and `call.register`s `authPub`. A knock carries the delegation the callee joined under; the caller verifies it exactly as an ACCEPT's and treats it as the answer. The ACCEPT travels over IM and may be slow; the knock is one relay hop behind the callee's first join.
4. **Callee:** connects to the relay and joins with `admitSig`. Until the caller has registered, the join fails with 409; the callee retries after 0.25, 0.5, 1 and 2 s and then every 2.5 s, ten attempts over about 16 s, within the relay's 10 joins per minute.
5. **Audio** starts on the relay once both are in the roster.
6. **Lost replies:** `call.create`, `call.register` and `call.join` are retried when a reply is lost (FAPI16 §4.1).
7. **Ending:** HANGUP. A peer that leaves the relay's roster and is not back within 6 s has hung up, even if its HANGUP never arrives.

### 6.3. The Direct Path

Two contacts may carry a call directly, beside the relay, which they stay joined to. A direct path shows each side's IP address to the other, so a client MUST try one only with a contact, and MUST NOT with *Always relay* on.

**Candidates.** A candidate is `{"t": "lan" | "map", "a": "ip:port" | "[ipv6]:port"}`:

- `lan`: the device's private interface addresses (RFC 1918, or an IPv6 ULA), for two devices on one network;
- `map`: the address the relay sees the device's call connection at, which the relay adds when the join asks with `reflexive = true` (FAPI16 §4.4).

A client that may go direct sends its `lan` candidates and `reflexive = true` in `call.join`, and learns the peer's from the peer's roster entry. For `map` to mean anything, the client's relay connection and its direct path MUST use the same local UDP port.

**Steps**, once a side sees the peer's roster entry with a delegation that verifies:

1. **Punching:** send a FUDP HELLO to every candidate every 100 ms for up to 3 s, and answer every HELLO with a PUBLIC_KEY carrying `tPub`. A PUBLIC_KEY that is the peer's `tPub` marks an address that works.
2. **Opening:** only the side with the lexicographically lower FID opens an encrypted FUDP connection there, so there is one connection, not two. The other accepts it.
3. **Checking:** a connection counts only if its FUDP peer key is the `tPub` of the peer's verified delegation. Each side then sends a probe datagram every 100 ms, `{0x03, heard}`, where `heard` is 1 once it has received a probe from the other. A side whose probe comes back with `heard = 1` knows both directions work. (0x03 is neither a MediaFrame nor an Attestation.)
4. **Switching:** once probes have passed both ways, a side sends its audio and attestations only on the direct path, with `routeId = 0`, closing its current attestation first. Probes continue every 500 ms as a keepalive.
5. **Falling back:** if nothing arrives on the direct path for 2 s, that side sends on the relay again, for the rest of the call. If neither side reaches the other within 8 s, both stay on the relay.

### 6.4. Ringing and Reaching the Callee

A signal is sent on every channel at once: FUDP directly when the peer is reachable, ROAD (FAPI15) to the peer's `home.ROAD`, and DOCK as the lasting record. The `(senderId, id)` check drops the extra copies. Unlike chat messages, call signals ignore opt-in settings that disable ROAD or direct FUDP: the fee a ring costs is part of placing a call.

While a device rings, it asks the INVITE's relay for `call.info` every 2 s, with a throwaway key and no delegation: two participants means another of the callee's devices answered, and none, after the caller was seen, means the caller gave up. Either ends the ring at once. It stops watching before joining itself.

Only a running device can ring. Freer uses no third-party push service. A client MAY offer to keep itself running for calls (Android: a foreground service; Mac: a menu-bar mode), and SHOULD then show the ringing screen without the app's own lock, showing only who is calling.

## 7. Codec

Opus (RFC 6716): 48 kHz, mono, `OPUS_APPLICATION_VOIP`, VBR around 24 kbps (12–32), in-band FEC on, DTX on.

- **Frame length** is the sender's choice and may change between frames: 40 ms by default, and on the relay always; 20 ms MAY be used on a direct path whose RTT has stayed below 50 ms for 3 s, returning to 40 ms once it has stayed at 70 ms or more for 3 s. Senders MUST NOT use 2.5, 5 or 10 ms.
- **Receivers** announce nothing: each reads a packet's length from its Opus TOC byte (RFC 6716 §3.1), plays out in 20 ms ticks, and restarts that stream's jitter buffer when the length changes.
- A muted or silent sender still sends a DTX frame every 400 ms or so, which keeps it alive on the relay.

## 8. Meetings

### 8.1. Meeting Signals

In a Room or Team chat (`type = ROOM` or `TEAM`, `targetId` = the entity), sealed under the entity's symkey:

| `op` | From | Fields | Meaning |
|---|---|---|---|
| `MEETING_START` | host | `meetingId` (`"mtg_"` + 24 lowercase hex), `relay` {`url`, `pubkey`?, `sid`?}, `nonce` (32 bytes hex), `symkeyVersion`, `authPub`, `keyEpoch`, `title`?, `started` | A meeting is open: its card, with Join. |
| `MEETING_END` | host | `meetingId`, `duration` | Closed: the card says so. |

- Receivers show one card per `meetingId`, from its first `MEETING_START`.
- **After a rekey** (§8.4) the host posts `MEETING_START` again for the same `meetingId` with the new `nonce`, `symkeyVersion`, `authPub` and `keyEpoch`. Receivers keep every key set, newest `keyEpoch` first, and join with the newest one they can derive, falling back to an older one if the relay answers 401. They take a key set from any member, since the host role can pass on; a bogus one costs a joiner only a failed attempt.
- `MEETING_END`, and a `MEETING_START` with `keyEpoch` above 0, change the card and are not chat rows of their own.

### 8.2. Starting, Joining and Ending

**The relay:** the entity's `home["CALL@No1_NrC7"]` (a Team's on-chain `home`, a Room's `RoomInfo.home`), otherwise the host's own.

**Starting:** the host takes the entity's newest symkey version, makes a `meetingId` and a `nonce`, derives the keys (§4.2, §4.4), `call.create`s the meeting with `authPub` and joins it, then posts `MEETING_START` with the relay's key and service id.

**Joining:** a member derives the keys from the named symkey version and joins. A member without that version asks for it the FIMP way (FIMP2 §7.4, FIMP4 §7.4).

**Ending:** the host `call.control end`s the meeting and posts `MEETING_END`. A meeting whose host disappears ends when the relay closes it.

**The host** is a relay role (FAPI16 §7), not a Room or Team role: an owner has no power in a meeting it did not start unless the host hands it over.

### 8.3. Meetings of Chosen People

A host may meet with only some members. The entity's symkey cannot limit that, since every member holds it, so the meeting is keyed by a random 32-byte key `K` of its own (§4.2), and nothing is posted to the chat. Each invitee gets a 1:1 `CALL` message, sealed `asy2way` to it alone and sent on every channel like a call signal (§6.4):

| `op` | From | Fields | Meaning |
|---|---|---|---|
| `MEETING_INVITE` | host | `meetingId`, `entityId`, `entityType` (`ROOM` or `TEAM`), `relay`, `nonce`, `symkeyVersion` = 1, `authPub`, `key` (`K`, hex), `title`?, `started` | You are invited. |
| `MEETING_END` | host | `meetingId`, `duration`, `entityId`, `entityType` | Closed. |

- A receiver accepts an invitation only if its sender and the receiver are both members of `entityId`. It keeps `K` with its symkeys, under an entity named by the `meetingId` at version 1, and shows the card in its own copy of that chat only. It forgets `K` when the meeting ends.
- The host invites more members during the meeting the same way.
- Such a meeting does not follow the entity's symkey rotations (§8.4). A removed participant still holds `K`.

### 8.4. Following a Key Rotation

A meeting's key follows the entity's symkey. When the owner rotates it during a meeting, usually after removing a member (FIMP2 §3.5, FIMP4 §3.3), and the host holds the new version:

1. The host derives a new call secret and `authPub` from the new version and a new `nonce`, and `call.rekey`s the meeting (FAPI16 §4.8); it posts `MEETING_START` with the new key set (§8.1).
2. Every participant gets the relay's `rekey` notice, derives the new secret — asking the members for a symkey version it lacks — and `call.prove`s it within 30 s, or is removed.
3. **Switching:** a participant that has the new key opens frames under both epochs, but keeps *sending* under the previous one until every roster entry shows the new `keyEpoch`, or until the 30 s deadline. Then it sends under the new epoch, and keeps the previous epoch's keys 5 s longer for frames in flight. So a member still waiting for the new symkey keeps hearing everyone; the cost is that a removed member still connected can listen up to 30 s more, which a host avoids by also kicking it.

The host never rotates the symkey itself: FIMP forbids automatic rotation (FIMP2 §7.2). It only follows the owner's. A kicked participant still holds the key; only the owner's rotation stops it decrypting what follows.

### 8.5. Membership Verification

Anyone holding the named symkey can read the `nonce` and `authPub`, so receivers apply FIMP's membership checks:

- A `MEETING_START` whose verified sender is not a member is discarded (FIMP2 §8.4, FIMP4 §8.2).
- A `MEETING_END` from the host who started the meeting closes its card.
- A `MEETING_END` from any other member is a hint only: the client asks `call.info`, after the relay would have closed an empty meeting, and closes the card only if the relay says it is gone.

### 8.6. Ringing

A new meeting rings its members like a call, with Join and Decline, for up to 45 s, whether it is for everyone in the chat or for chosen people. It rings only if it started within the last 2 minutes by the host's clock, is someone else's, and finds the device in no call or meeting; its end, an incoming call, or joining it stops the ring. A card that arrives later only shows.

A ring can come no sooner than its signal is fetched, so a client SHOULD check its own DOCK every few seconds and its group DOCKs every ten or so while it runs.

## 9. Test Vectors

`callVectors.json` pins every derivation and format in §4 and §5: keys, delegations, 1:1 and meeting secrets, sender keys, admission keys and signatures, sealed media frames and attestations. It is generated by FreerForMac's vector generator (`CallRef`) and checked by FC-AJDK's `CallVectorTest`, the Mac's `CallVectorTests` and FC-JDK; an implementation MUST reproduce every value.

## 10. Security Considerations

1. **Forward secrecy.** A 1:1 call's secret comes from ephemeral keys erased at hang-up; a recording of the call cannot be opened later, even with both FIDs' keys. A meeting's is no stronger than the symkey it comes from.
2. **The relay learns no key.** It sees `authPub` and `admitSig`s, from which nothing opens the audio (FAPI16 §11).
3. **Attribution.** In a meeting every member holds the key, so the seal alone does not say who spoke; attestations under each participant's delegated transport key do. A relay or member that forges or misroutes frames gets them silenced (§5.1).
4. **Removal.** Kicking removes a participant from the relay; only a key rotation stops it decrypting. With Decision-19 switching, a removed member still connected may hear up to 30 s more unless also kicked.
5. **IP addresses.** A direct path shows each side's address to the other, which is why it is only for contacts and never with *Always relay* (§6.3). Through the relay, participants see no one's address.
6. **Strangers cannot ring.** A stranger's INVITE is held as a message request and shown as a missed call (§3.2).
7. **What the relay learns:** who is in each call, who speaks, how loud, how often and for how long.

## 11. Versioning

| Version | Date | Changes |
|---|---|---|
| 1 | 2026-09-29 | Initial specification: the `CALL` content type, 1:1 signals and records, keys, the media frame and attestations, 1:1 setup and the direct path, the codec, meetings (including chosen-people meetings, key rotation and ringing). Written from VOICE_SPEC after the Android and Mac clients interoperated. |

## 12. Related Protocols

- **FIMP0**: the envelope, the `CALL` ordinal, and the FIMP0V3 signature.
- **FIMP1**: P2P, and the stranger gate.
- **FIMP2**, **FIMP4**: Rooms and Teams, their symkeys, rotation and recovery.
- **FAPI15**: ROAD, one of the channels signals travel on.
- **FAPI16**: CALL, the relay.
- **FUDP7**: DATAGRAM frames.
- **FTSP13**: HKDF.
- **RFC 6716**: Opus. **RFC 6464**: audio levels. **RFC 5869**: HKDF.
