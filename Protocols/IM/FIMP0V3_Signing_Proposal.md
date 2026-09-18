# FIMP0V3 Signing Proposal

## Contents

- [Summary](#summary)
- [Abstract](#abstract)
- [1. Problem](#1-problem)
- [2. Why Not DockItem.sender](#2-why-not-dockitemsender)
- [3. Proposed Changes to FIMP0](#3-proposed-changes-to-fimp0)
  - [3.1. General Rule 2 is replaced](#31-general-rule-2-is-replaced)
  - [3.2. Wire version 3](#32-wire-version-3)
  - [3.3. The signature trailer](#33-the-signature-trailer)
  - [3.4. Signing](#34-signing)
  - [3.5. Verifying](#35-verifying)
  - [3.6. Payload sizing](#36-payload-sizing)
- [4. Consequential Changes to FIMP1-4](#4-consequential-changes-to-fimp1-4)
- [5. Security Considerations](#5-security-considerations)
- [6. Migration](#6-migration)
- [7. Resolved Questions](#7-resolved-questions)
- [8. Implementation Notes](#8-implementation-notes)

---

## Summary

|Field|Content|
|---|---|
|Title|FIMP0V3 Signing Proposal|
|Type|FIMP (change proposal)|
|SN|0|
|Ver|3 (proposed)|
|Status|Draft — implemented in FreerForMac and Freer Android|
|Author||
|Created|2026-09-17|
|Amends|FIMP0V2_FIMP; mode documents in FIMP1V3_P2P, FIMP2V3_Room, FIMP3V3_Square, FIMP4V3_Team|

## Abstract

FIMP0V2 carries the sender of a message as a plain `senderId` field and says authorship is established by the transport: the FUDP handshake, and on the DOCK path the `DockItem.sender` the server records. That does not hold. `senderId` is text any sender can write, and `DockItem.sender` names whoever connected to the storing server, which after a DOCK forward is the forwarding server rather than the author. In practice any member of a Team or Room can post as another member, anyone can post to a Square as any member, and on the DOCK path anyone can post a P2P message as anyone.

This proposal makes every FIMP message carry the author's pubkey and a Schnorr signature over the whole encoded envelope, so authorship is checked end to end by the receiver and no server is trusted with it. It is a wire break: the envelope version moves from `0x02` to `0x03`.

## 1. Problem

|Mode|Why a forged `senderId` is accepted today|
|---|---|
|Square|The body is plaintext and unsigned. Nothing ties it to anyone.|
|Team, Room|The body is sealed under a symkey every member holds. Any member can seal a message naming any other member, and it opens normally for everyone.|
|P2P over DOCK|The AsyTwoWay bundle records the pubkey it was sealed with, and opening needs only that pubkey. A bundle sealed with Mallory's key and naming Alice opens as cleanly as Alice's own. Unsealed P2P control messages (key requests, key shares, room invitations) were accepted with no check at all.|

The consequences are not cosmetic. A forged `SYMKEY` share or `ROOM_INFO` from a room's owner, a forged Team notice, or a Square post in another member's name are all accepted as genuine. FIMP3V2 §8 asks receivers to keep only members' messages, which is meaningless while membership is checked against an unauthenticated field.

## 2. Why Not DockItem.sender

FIMP0V2 General Rule 2 names `DockItem.sender` as authoritative. Enforcing that rule instead of adding signatures was considered and rejected:

1. **Forwarding replaces it.** Under FAPI13 §2.5 the local DOCK "connects to `targetDockUrl` as a FAPI client and submits the same payload", so the remote item's `sender` is the forwarding server. Every P2P message to a recipient on another DOCK arrives with the wrong sender.
2. **It trusts every server on the path.** A DOCK can write any `sender` it likes. The operator of a Square's DOCK could post as any member.
3. **It is bound to the connection, not the identity.** A client that holds several identities and keeps one FUDP session (the macOS client connects with its main FID's key) stores every identity's messages under one sender.
4. **It is lost off the DOCK path.** A message re-delivered inside a HISTORY share, or carried over ROAD, has no `DockItem`.

A signature made by the author survives all four.

## 3. Proposed Changes to FIMP0

### 3.1. General Rule 2 is replaced

> **2. FIMP-Level Authentication**
>
> Every FIMP message MUST be signed by the private key of its `senderId`, as specified in [The signature trailer](#33-the-signature-trailer). A receiver MUST verify the signature before acting on any part of the message and MUST discard a message that fails. Transport identities — the FUDP peer, `DockItem.sender` — identify connections, not authors; a receiver MUST NOT use them to establish who wrote a message.

### 3.2. Wire version 3

The envelope opens with `0xF1 0x03`. Everything in FIMP0V2 §2 is unchanged up to and including the last conditional field, and is followed by the trailer below. A version 3 decoder MUST reject `0xF1 0x02`, and a version 2 decoder already rejects `0xF1 0x03`.

The flag bitmap is unchanged. Bits 8-15 stay reserved: the trailer is mandatory, so it needs no flag.

### 3.3. The signature trailer

```
┌──────────────────────────────────────────────────────┐
│ ... FIMP0V2 §2 fields, from magic to the last        │
│     conditional field, with version = 0x03 ...       │
│ ─── trailer (mandatory) ────                         │
│ senderPubkey    33 bytes (compressed secp256k1)      │
│ signature       64 bytes (Schnorr, as FTSP24)        │
└──────────────────────────────────────────────────────┘
```

The trailer is the last 97 bytes of the envelope. A decoder that has consumed every field the flags call for MUST find exactly 97 bytes left, and MUST reject the envelope otherwise.

The pubkey is carried rather than looked up because a FID that has never spent has published no pubkey on chain, and a newcomer's first messages — asking for a first FCH, asking a guide for help — are exactly those.

### 3.4. Signing

```
signingInput = "FIMP-SIG" (8 ASCII bytes)
             ‖ envelope bytes from the magic byte up to, not including, the trailer
msgHash      = SHA256( SHA256( signingInput ) )
signature    = schnorr_sign( msgHash, privkey of senderId )
```

The primitive and the double hash are those of FTSP24 (`SchnorrSignMsg`): FC-AJDK `SchnorrSignature.schnorr_sign`, the BCH-2019 Schnorr scheme, whose nonce is derived from the key and message so the same inputs always give the same 64 bytes. Only the input differs, being bytes rather than a UTF-8 string. The `"FIMP-SIG"` prefix keeps a message signature from ever verifying as a signature over some other structure signed with the same key.

**What is covered.** Everything before the trailer: the version, the mode, the sender, the target, the timestamp, the flags, the body exactly as it travels (sealed, if the mode seals), the symkey version and every id. Nothing on the wire is unsigned except the signature itself.

**Signed after sealing.** The body is sealed first and the envelope signed second, so a receiver verifies before decrypting. That makes a forged or spam message cheap to discard — in a Team or Room, without spending a symkey open on it — and it means a message cannot be re-targeted or re-bodied by anyone without the author's key.

**The sender signs with the sender's key.** A client that holds several identities signs each message with the key of the `senderId` it names, whatever key its transport session was opened with.

### 3.5. Verifying

A receiver, for every decoded envelope and before anything else:

1. MUST check that `FID(senderPubkey)` equals `senderId` (FVEP address derivation). On mismatch, discard.
2. MUST verify `signature` over `msgHash`, computed from the received bytes exactly as in §3.4, against `senderPubkey`. On failure, discard.
3. MUST check that the message is addressed to it: for P2P, `targetId` is one of its own FIDs; for a group, `targetId` is a group it is collecting for. Otherwise, discard. (Without this, a validly signed P2P message could be replayed at a different recipient.)
4. MUST de-duplicate on `(senderId, id)` before filing, over at least the longest DOCK retention period. A signed message can be re-posted verbatim by anyone who has seen it; dedup is what makes that harmless.

Only then does mode-specific processing — membership, opening the body, routing a signal — begin. A discarded message is not filed, not routed, not acknowledged and not held as a message request.

A message nested inside another, such as the messages carried by a HISTORY share, keeps its original trailer and MUST be verified the same way when it is imported.

### 3.6. Payload sizing

The trailer adds 97 bytes to every message. It is part of the encoded envelope and counts against the destination DOCK's `maxDataSize` like every other byte (FIMP0V2 Payload Sizing §3).

## 4. Consequential Changes to FIMP1-4

|Document|Change|
|---|---|
|FIMP1V2 (P2P)|The AsyTwoWay seal remains for confidentiality. Its recorded pubkey is no longer the proof of authorship; the trailer is. A receiver SHOULD still discard a P2P body whose AsyTwoWay pubkey is not the verified sender's, as defence in depth. An AsyOneWay body is valid only for a message from and to the same FID.|
|FIMP2V2 (Room)|The sender MUST be a member of the room as the receiver holds it. Owner-only messages (`ROOM_INFO` rewriting membership, room close) MUST come from the owner, checked against the verified `senderId`.|
|FIMP3V2 (Square)|§8 is rewritten: the DOCK does not check membership, so the receiver MUST discard a message whose verified `senderId` is not a member of the square. A sender that is not listed in the receiver's copy of the member list triggers a fresh read of the square from BASE before the message is discarded, since a member may have joined since the last sync. `DockItem.sender` is no longer referenced. §9.4's `createHeight` rule stays a SHOULD.|
|FIMP4V2 (Team)|The sender MUST be a member. Owner- and manager-only notices, and `SYMKEY` pushes for the team, MUST come from a FID holding that role on chain, checked against the verified `senderId`.|

## 5. Security Considerations

- **Deniability is given up.** A signed message is transferable proof that its author wrote those bytes. For P2P and Team/Room the signed bytes are the sealed body, so proving *what* was said also requires disclosing the key that opens it — but proving *that* a message was sent, when and to whom, does not.
- **Metadata is unchanged.** The header was already in the clear; the pubkey adds nothing a FID did not already reveal once spent, and reveals a pubkey early for a FID that has not spent.
- **Replay** is handled by per-receiver dedup (§3.5 step 4) and target binding (§3.5 step 3). A receiver MAY additionally discard messages whose `timestamp` is further in the past than DOCK retention allows or implausibly far in the future.
- **Key compromise** of a FID lets the holder sign as that FID, as it already lets them spend its coins. FIMP adds no revocation of its own.
- **Server trust** drops to availability only. A DOCK can still drop, delay or reorder messages; it can no longer author them.

## 6. Migration

As for version 2, both clients change together and there is no interoperation window.

- **Encoders** write `0xF1 0x03`, encode as before, then append the pubkey and signature. Clear P2P control messages must already be sealed on the DOCK path; the macOS client now does so.
- **Decoders** reject version 2 envelopes, verify the trailer before anything else, then proceed as in version 2.
- **Stored history** is unaffected; it is local JSON. Implementations MAY store the trailer to allow later re-verification.
- **In-flight version 2 items** on a DOCK will not decode after the upgrade and are discarded on fetch, exactly as version 1 items were at the last break.

**Alongside version 3**, the macOS client keeps two checks from before it as defence in depth: a P2P body's AsyTwoWay pubkey must hash to `senderId` (or the body is AsyOneWay from and to the receiver's own FID), and a P2P message with content is not accepted unsealed; it also seals its own P2P control messages on the way out.

## 7. Resolved Questions

1. **Hash construction.** Double SHA-256 over `"FIMP-SIG" ‖ envelope`, as FTSP24, so both clients reuse their existing Schnorr message-signing code.
2. **FUDP_DIRECT is signed too.** One rule on every channel; the Android client signs its direct sends with the same encoder.
3. **Android's control traffic.** Android seals every P2P message that has a payload on the DOCK and ROAD paths (`P2pHandler.sealedEnvelope`), and now signs every envelope it sends, P2P and group alike.
4. **HISTORY imports.** History files carry local JSON, not wire envelopes, so imported messages have no trailer to verify. They are imported and marked **unverified** in both clients' transcripts.

## 8. Implementation Notes

- **Encoders** sign in the codec: `ImMessage.toWireBytes(signingWith:)` (Swift) and `ImMessage.toWireBytes(byte[] prikey)` (Java) refuse to sign a message whose `senderId` is not the key's FID.
- **Decoders** verify in the codec: `fromWireBytes` rejects a missing trailer, a trailer pubkey that is not `senderId`'s, or a signature that does not verify, so no caller can act on an unverified message.
- **Vectors.** `tools/vector-gen` in FreerForMac generates the `im_message` vectors from the real FC-AJDK class using the FTSP0 §2.1 example keys; the Swift suite matches them byte for byte, and FC-AJDK's `ImWireV2Test` asserts the same bytes.
- **Target check (§3.5 step 3).** A P2P message must name the receiving identity; a message fetched from a DOCK must name a recipient the fetch asked for (macOS) and, when the item lists recipients, one of those. macOS: `MessageCourier.collect`; Android: `InboundGuard`, applied in `DockItemRouter`, direct FUDP receipt and the fallback group fetch.
- **Replay check (§3.5 step 4).** Both clients persist `(senderId, id)` for every message taken in and drop a second arrival before it is acted on — including signals such as key requests and room notices, which never become transcript rows. Records are kept 400 days (longer than a DOCK's 365-day maximum) and pruned when an identity is opened. macOS: `SeenMessagesStore`; Android: `InboundGuard` over the messages database's `fimp_seen_messages` map.
- **Mode documents.** §4 is written out in FIMP1V3_P2P, FIMP2V3_Room, FIMP3V3_Square and FIMP4V3_Team, which also state that a DOCK checks no membership, and (FIMP3V3 §3.2) that a square may be read as soon as a join is broadcast but not posted to until it confirms.
- **Not yet done:** FIMP0 itself exists as this proposal, not as a full FIMP0V3_FIMP document.
