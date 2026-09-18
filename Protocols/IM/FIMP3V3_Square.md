# FIMP3V3_Square

|Field|Content|
|---|---|
|Title|Square|
|Type|FIMP|
|SN|3|
|Ver|3|
|Status|Draft|
|Author|C_armX|
|Created|2026-09-17|
|Updated|2026-09-17|
|PID||

## Contents

- [Abstract](#abstract)
- [1. Overview](#1-overview)
- [2. Data Model](#2-data-model)
- [3. Lifecycle](#3-lifecycle)
  - [3.1. Create](#31-create)
  - [3.2. Join](#32-join)
  - [3.3. Leave](#33-leave)
  - [3.4. Update](#34-update)
  - [3.5. Membership sync](#35-membership-sync)
- [4. Message Contracts](#4-message-contracts)
  - [4.1. Chat messages](#41-chat-messages)
  - [4.2. Disallowed ContentTypes](#42-disallowed-contenttypes)
- [5. Request / Response](#5-request--response)
- [6. DOCK Use](#6-dock-use)
- [7. Encryption](#7-encryption)
- [8. Membership Verification](#8-membership-verification)
- [9. Security Considerations](#9-security-considerations)
- [10. Versioning](#10-versioning)
- [11. Related Protocols](#11-related-protocols)

---

## Abstract

FIMP3V3 defines the **Square** mode of FIMP -- an open, public group chat whose membership is recorded on chain as a FEIP `Square` entity. Joins and leaves are FEIP transactions. There is no manager and no symmetric key: messages are plaintext at the IM layer, with confidentiality relying entirely on FUDP transport encryption. A sender posts a message to the square's advertised DOCK with `recipients = [squareId]`, and members fetch with `recipientIds = [squareId]`. The DOCK stores and serves items by id and checks nobody's membership; every message is signed by its author, and each receiver keeps only messages from members. Square is the simplest of the four modes and has the smallest IM-layer protocol surface.

## 1. Overview

A Square is characterized by:

- An on-chain FEIP `Square` record (`entityType = SQUARE`) whose `id` is the `squareId`.
- An on-chain `members` list maintained by the FEIP transactions (Create/Join/Leave/Update). FIMP only **reads** this list via FAPI BASE; it never writes it.
- An on-chain `home` map advertising the square's DOCK URL (typically under the key `"DOCK@No1_NrC7"`).
- Open, self-service membership: any FID may join by submitting an on-chain Join transaction, and may leave by submitting a Leave transaction.
- Plaintext IM-layer messages: the body is never sealed. `FLAG_BODY_SEALED` and `symkeyVersion` MUST NOT be set.
- All messages addressed to the square as a whole through DOCK, stored once under the `squareId` and returned to anyone who fetches that id. The DOCK does not check membership.

Every message in this mode, including every control message, carries the FIMP0V3 signature trailer: the author's pubkey and a Schnorr signature over the whole envelope. Before acting on a message a receiver verifies the signature, checks that the message is addressed to it, and drops a `(senderId, id)` it has taken in before (FIMP0V3 §3.5). Throughout this document, **the verified sender** means the `senderId` of a message that has passed those checks.

Square differs from Room (FIMP2) and Team (FIMP4) in three crucial ways: (a) the membership list is on chain and is the authoritative source; (b) anyone may join without invitation; (c) messages are not encrypted at the IM layer.

## 2. Data Model

The `Square` FEIP entity is fully defined by FEIP and is not redefined here. FIMP only consumes the following fields:

|Field|Type|Source|FIMP usage|
|---|---|---|---|
|`id`|string|on-chain FEIP|`squareId`. Used as `targetId` in messages and as the DOCK recipient.|
|`name`|string|on-chain FEIP|Display name.|
|`desc`|string|on-chain FEIP|Description.|
|`members`|list of FIDs|on-chain FEIP|Authoritative member list. Used by senders to know that a square is joinable; used by receivers to discard messages from non-members (§8.2).|
|`home`|map<string,string>|on-chain FEIP|Service map. The DOCK URL under `"DOCK@No1_NrC7"` is the square's mailbox.|
|`namers`|list of FIDs|on-chain FEIP|FIDs who have renamed the square; informational only.|
|`active`|boolean (or implicit)|on-chain FEIP|If false (or absent), the square is closed and SHOULD NOT receive messages.|
|`birthHeight`, `lastHeight`, ...|various|on-chain FEIP|Used for incremental sync (§3.5).|

Implementations MUST query these fields via FAPI BASE (FAPI11) and MUST NOT cache for longer than a configurable refresh interval; on-chain updates are the only signal of membership change.

## 3. Lifecycle

All Square lifecycle events except message exchange are **on-chain** FEIP transactions. They generate no FIMP wire traffic. A FIMP implementation only **observes** these events through periodic FAPI sync; it does not initiate them at the IM layer.

### 3.1. Create

Any FID MAY create a Square by submitting a FEIP Create transaction (specified by FEIP). The transaction sets `name`, `desc`, optional `home` (DOCK URL), and other FEIP fields. The creator becomes the first member.

FIMP wire effect: none.

### 3.2. Join

Any FID MAY join an existing Square by submitting a FEIP Join transaction referencing the `squareId`. The on-chain `members` list is updated by the FEIP layer.

FIMP wire effect: none.

**Reading starts at once; posting waits for the block.** Because the square's DOCK serves its messages to anyone who asks, a client MAY open the square and fetch from its DOCK as soon as its Join transaction is broadcast, using the `home` map of the square record it joined from. It MUST NOT post until a block includes the join: until then the chain's `members` list does not name it, and every receiver would discard the post (§8.2). A client SHOULD tell the user so while the join is waiting, and SHOULD treat a join still unconfirmed after a day as failed. After the next sync (§3.5) the square is an ordinary membership.

### 3.3. Leave

Any FID MAY leave a Square by submitting a FEIP Leave transaction. The on-chain `members` list is updated.

FIMP wire effect: none.

### 3.4. Update

The on-chain Update transaction may change `name`, `desc`, `home`, or the rename history. After the next sync, members MUST refresh their local cache and, if `home.DOCK@No1_NrC7` changed, switch to the new DOCK URL for subsequent reads and posts.

### 3.5. Membership sync

FIMP implementations SHOULD periodically poll FAPI BASE for Square records that match either:

- a known `squareId` set (refresh changes); or
- a filter `members contains <ownFid>` ordered by `lastHeight ASC`, with `lastHeight > <last seen height>` (incremental sync).

The cursor (`last seen height`) is local state. There is no FIMP wire signal for Square membership changes; the only mechanism is the on-chain query.

## 4. Message Contracts

Throughout this section, `content` and `data` name the two **sections of the body** (FIMP0 §Body Framing), not envelope fields. Square bodies are never sealed, so they are always readable as written.

### 4.1. Chat messages

|Field|Value|
|---|---|
|`type`|`SQUARE`|
|`contentType`|user content type (see §4.2 for the allowed set)|
|`senderId`|sender FID (MUST be a current member at send time)|
|`targetId`|`squareId`|
|`timestamp`|ms since epoch|
|`body`|unsealed body framing. Section 1 (`content`) is the plaintext payload appropriate to `contentType` (see FIMP1 §5 for TEXT, STREAM, VOICE, HAT formats); section 2 (`data`) carries raw bytes for STREAM, VOICE, etc.|
|`FLAG_BODY_SEALED`|MUST NOT be set|
|`symkeyVersion`|MUST NOT be set|
|`replyToId`, `threadId`|optional|

Senders post the binary wire format to the square's DOCK; see §6.

### 4.2. Disallowed ContentTypes

The following ContentTypes are NOT used in Square mode and senders MUST NOT emit them with `type = SQUARE`:

- `SYMKEY`, `MEMBERS` -- no symmetric key, no member-list push (membership is on chain).
- `ROOM_INFO`, `ROOM_LEAVE` -- specific to Room mode.

The following ContentTypes are valid: `TEXT`, `HAT`, `STREAM`, `VOICE`, `REACTION`, `EDIT`, `DELETE`, `FORWARD`, `TYPING`, `RECEIPT`, `PRESENCE`, `REQUEST`, `RESPONSE`, `HISTORY`. (`HISTORY` is allowed but in practice rarely needed, see §5.)

## 5. Request / Response

Square does not define a REQUEST/RESPONSE protocol of its own. Two notes:

- **No `RequestType.HISTORY` for Square.** Square's history is publicly available via the square's DOCK using ordinary `dock.fetch` with FCDSL filters. There is no need to route a HISTORY request through a member -- a new joiner simply pages back through the DOCK.
- **No `RequestType.MEMBERS` for Square.** The member list lives on chain; clients query FAPI BASE directly.

A sender MAY use any `REQUEST`/`RESPONSE` `RequestType` defined in FIMP0 (e.g., `HAT` to request a Hat record from another member); these inherit P2P semantics and should be sent with `type = P2P`, not `type = SQUARE`. Implementations MUST NOT emit a `REQUEST` with `type = SQUARE`; receivers MUST ignore such messages.

## 6. DOCK Use

|Operation|recipients|dataType|Body|Target dock|
|---|---|---|---|---|
|Chat message (§4.1)|`[<squareId>]`|`"IM"`|compact binary `ImMessage`|the square's advertised DOCK (from on-chain `home.DOCK@No1_NrC7`)|

When the sender's FAPI client has a direct connection to the square's DOCK (cached during sync, FAPI13 ServiceDiscovery applies), the sender posts there directly. Otherwise, the sender MAY post to its own DOCK with `targetDockUrl` set to the square's DOCK to use FAPI13 forwarding.

Receivers fetch with `dock.fetch` and `recipientIds = [<squareId>]` (or include `<ownFid>` to also receive direct P2P traffic in the same call). Receivers MUST de-duplicate by `(senderId, id)` and discard any message whose `targetId` is not the square they fetched for (FIMP0V3 §3.5).

`maxDays` for Square messages is configurable per sender, subject to FAPI13 limits and the sender's balance. FAPI13 default is 7 days.

## 7. Encryption

Square messages are **plaintext** at the IM layer. `FLAG_BODY_SEALED` and `symkeyVersion` MUST NOT be set; receivers MUST reject any Square message that has either set.

A Square has open, self-service membership: anyone may join by submitting an on-chain transaction. Sealing the body would therefore protect nothing -- any key that reached every member would reach every prospective member too. The plaintext body is a deliberate consequence of the membership model, not an omission.

Confidentiality relies entirely on FUDP transport encryption between each FUDP peer:

- The sender ↔ DOCK FUDP connection.
- Each recipient ↔ DOCK FUDP connection.

The DOCK server sees the plaintext `ImMessage`. Square is therefore appropriate for **public** discussions where the DOCK operator is not adversarial. Senders that require end-to-end confidentiality from the DOCK server MUST NOT use Square; they SHOULD use Team (FIMP4) or Room (FIMP2).

For confidential **attachments** in a Square (e.g., a private file shared with a single member of the public square), the sender SHOULD use a `HAT` body whose underlying DISK file is encrypted with a key wrapped to the intended recipient(s), per FAPI12. This effectively shifts confidentiality to the DISK layer.

## 8. Membership Verification

### 8.1. Sender side

A sender MUST NOT post to a square whose on-chain `members` list does not name its FID -- including while its own Join transaction is still waiting for a block (§3.2). The DOCK would accept and charge for the post, and every receiver would discard it.

### 8.2. Receiver side

A receiver fetches square messages by querying the square's DOCK with `recipientIds = [<squareId>]`. The DOCK does not check membership, for fetching or for posting, so membership is enforced here.

A receiver MUST discard a square message whose verified sender (the signer, FIMP0V3 §3) is not a member of the square. A receiver's copy of the member list may lag the chain, so when the sender is not in the local copy the receiver SHOULD read the square from BASE once before discarding: a member may have joined since the last sync. To bound the lookups a stream of non-member posts can cause, a receiver MAY skip the re-read for an item stored before its last read of that square. If BASE cannot be reached, a receiver MAY keep the message rather than lose a member's post to a network failure. A receiver's own messages are always kept.

`DockItem.sender` MUST NOT be used for this check: after a DOCK forward it names the forwarding server, not the author.

## 9. Security Considerations

### 9.1. Plaintext exposure

Square messages are exposed to the operator of the square's DOCK server, to any FAPI relay along the way (if forwarding is used), and to anyone with read access to the DOCK server's storage. Senders MUST treat Square as a **public** medium and MUST NOT include sensitive data in Square messages.

### 9.2. Sender impersonation

Square bodies are plaintext, so without a signature anyone could post in any member's name. The author is the FID whose key signed the envelope (FIMP0V3 §3); receivers MUST verify the signature and discard a message not signed by `senderId`'s key. The FUDP peer and `DockItem.sender` MUST NOT be used as the author.

### 9.3. Spam and flooding

Open membership creates a spam vector. Mitigations:

- DOCK charges per-message storage and ingress fees (FAPI13 §2.4), creating an economic deterrent.
- Receivers discard posts from non-members (§8.2), so reaching readers requires a confirmed on-chain join, which costs a transaction fee.
- Receivers MAY apply local heuristics (rate limits, blocklists, reputation) at the IM layer.

FIMP version 1 does not specify a spam-control protocol; this is left to the application layer.

### 9.4. Membership timing

A sender's view of `members` may be stale at send time relative to a receiver's view at fetch time. A message from a member who has just left the square may still be delivered (because they were a member at `createHeight`); a new member may miss messages sent shortly before their join. Receivers SHOULD use `createHeight`-based membership checks rather than current membership.

### 9.5. Replay

Anyone can re-post a member's signed message verbatim; it verifies every time. Receivers MUST de-duplicate on `(senderId, id)` for at least the DOCK retention period, not by `DockItem.id`, which a replay does not share.

## 10. Versioning

This document defines version 3 of the Square mode (FIMP3V3), which accompanies the version 3 envelope of FIMP0V3 and does not interoperate with version 2 or version 1.

Changes from FIMP3V2:

1. Every message is signed by its author (FIMP0V3 §3); authorship is no longer taken from the FUDP peer or `DockItem.sender` (§9.2).
2. The DOCK is stated not to check membership. Receivers MUST discard messages from non-members, re-reading the square once before doing so (§8.2); previously a SHOULD against `DockItem.sender`.
3. A client may read a square as soon as its join is broadcast, and MUST NOT post until the join is confirmed (§3.2, §8.1).
4. Receivers de-duplicate on `(senderId, id)` and discard messages not addressed to the square (§6, §9.5).

Changes from FIMP3V1, retained from version 2, are confined to the envelope: `content` and `dataBase64` are no longer envelope fields but the two sections of the single `body` field, with binary data raw rather than Base64; and inline payload size is governed by the destination's resolved budget (FIMP0V2 §Payload Sizing) rather than a fixed constant. Square's IM layer remains plaintext, so the sealing changes of FIMP1V2 / FIMP2V2 / FIMP4V2 did not apply here.

Future versions MAY:

- Define optional moderator roles and signed moderation actions.
- Define an optional encryption variant for "sealed" Squares.
- Define a member-introduction message (CHAT_INTRO or similar).

Wire-incompatible changes require a new version number.

## 11. Related Protocols

- **FIMP0V3** -- Foundational rules, the `ImMessage` envelope, body framing, payload sizing, and the envelope signature and receive checks (`FIMP0V3_Signing_Proposal`).
- **FIMP1V3** -- P2P mode (used for any direct member-to-member request/response).
- **FIMP2V3** -- Room mode (closed local-managed group).
- **FIMP4V3** -- Team mode (closed on-chain owner-managed group with encryption).
- **FEIP** -- Defines the on-chain `Square` entity, Create/Join/Leave/Update transactions, and the `members` list semantics.
- **FAPI11V1 (BASE)** -- Used to query Square records on chain.
- **FAPI13V1 (DOCK)** -- Store-and-forward; one item stored under `recipients = [<squareId>]` for every reader to fetch.
- **FAPI12V1 (DISK)** -- Used for HAT-referenced large attachments.
