# FIMP1V3_P2P

|Field|Content|
|---|---|
|Title|P2P|
|Type|FIMP|
|SN|1|
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
  - [2.1. TalkPartner](#21-talkpartner)
  - [2.2. Conversation](#22-conversation)
- [3. Lifecycle](#3-lifecycle)
- [4. Channel Selection](#4-channel-selection)
  - [4.1. DOCK (default)](#41-dock-default)
  - [4.2. FUDP direct (opt-in)](#42-fudp-direct-opt-in)
  - [4.3. ROAD relay (opt-in)](#43-road-relay-opt-in)
  - [4.4. Selection rules](#44-selection-rules)
  - [4.5. Required configuration](#45-required-configuration)
- [5. Message Contracts](#5-message-contracts)
  - [5.1. TEXT](#51-text)
  - [5.2. STREAM](#52-stream)
  - [5.3. VOICE](#53-voice)
  - [5.4. HAT](#54-hat)
  - [5.5. RECEIPT](#55-receipt)
  - [5.6. REACTION, EDIT, DELETE, FORWARD, TYPING, PRESENCE](#56-reaction-edit-delete-forward-typing-presence)
- [6. Request / Response](#6-request--response)
  - [6.1. PUBLIC_KEY](#61-public_key)
  - [6.2. HAT](#62-hat)
  - [6.3. MESSAGE_SYNC](#63-message_sync)
  - [6.4. HISTORY](#64-history)
- [7. Encryption](#7-encryption)
  - [7.1. What is sealed](#71-what-is-sealed)
  - [7.2. Self-chat](#72-self-chat)
  - [7.3. No plaintext fallback](#73-no-plaintext-fallback)
  - [7.4. What sealing does and does not protect](#74-what-sealing-does-and-does-not-protect)
  - [7.5. Sealing is not authorship](#75-sealing-is-not-authorship)
- [8. Stranger Handling](#8-stranger-handling)
- [9. Security Considerations](#9-security-considerations)
- [10. Versioning](#10-versioning)
- [11. Related Protocols](#11-related-protocols)

---

## Abstract

FIMP1V3 defines the **P2P** mode of FIMP -- direct one-to-one messaging between two FIDs. The default delivery channel is the recipient's **DOCK** server (store-and-forward). Two optional channels -- direct **FUDP** and **ROAD** relay -- may be enabled by the sender via local settings to reduce latency when both peers are online or when the sender wishes to avoid storing the message on a DOCK. Whenever a P2P message crosses an intermediary -- a DOCK or a ROAD -- its body is sealed to the recipient's public key; on a direct FUDP session, where the transport is already end-to-end between exactly the two endpoints, the body travels plaintext. P2P is also the carrier for several control messages used by Room and Team modes (e.g., `SYMKEY` push, `ROOM_INFO` invitation), which inherit P2P delivery semantics.

## 1. Overview

Every message in this mode, including every control message, carries the FIMP0V3 signature trailer: the author's pubkey and a Schnorr signature over the whole envelope. Before acting on a message a receiver verifies the signature, checks that the message is addressed to it, and drops a `(senderId, id)` it has taken in before (FIMP0V3 §3.5). Throughout this document, **the verified sender** means the `senderId` of a message that has passed those checks.

P2P messages are characterized by:

- `ImMessage.type = P2P`.
- `targetId` is the **recipient FID**.
- The message is delivered to exactly one recipient.
- No symmetric key is involved at the IM layer; `symkeyVersion` MUST NOT be set. Sealing, where required, is asymmetric (see §[7. Encryption](#7-encryption)).
- The sender selects a delivery channel from {DOCK_STORED, FUDP_DIRECT, ROAD_RELAY} based on local settings and the recipient's advertised endpoints (see §[4. Channel Selection](#4-channel-selection)).
- All P2P messages, regardless of channel, use the **compact binary envelope** of `ImMessage` (`toWireBytes` / `fromWireBytes`). There is no separate text fast path.

## 2. Data Model

### 2.1. TalkPartner

A `TalkPartner` is the local representation of a P2P peer. It is **not** transmitted on the wire; only its `pubkey` and `home` are derived from on-chain `freer.home` records or from FUDP discovery.

|Field|Type|Wire|Description|
|---|---|---|---|
|`fid`|string|n/a|FID of the partner. Equals the local record id.|
|`cid`|string|n/a|Optional CID for display.|
|`pubkey`|string (hex)|via on-chain `freer.home`|secp256k1 public key, used to encrypt asymmetric payloads (e.g., wrapped symkeys for Team/Room) and for FUDP handshake.|
|`home`|map<string,string>|via on-chain `freer.home`|Service-id → URL map (e.g., `DOCK@No1_NrC7`, `FUDP@No1_NrC7`, `ROAD@No1_NrC7`). Used to choose a delivery channel.|
|`source`|string|n/a|Local origin tag (`FUDP_DISCOVER`, `CONTACT`, `SEARCH`).|
|`addedAt`, `lastTalkedAt`, `lastTalkVia`|various|n/a|Local statistics.|

Implementations SHOULD cache the partner's `pubkey` and `home` after first resolution, and refresh on demand or after a configurable TTL.

### 2.2. Conversation

A `Conversation` aggregates messages exchanged with one peer for UI presentation. It is local and is not on the wire.

## 3. Lifecycle

There is no setup or registration for a P2P channel. Two FIDs may exchange messages as soon as the sender knows the recipient's FID and can resolve the recipient's `home` record (for DOCK) or establish a FUDP connection.

A sender MUST be configured with at least one outbound channel before P2P can be used:

- The sender's own `freer.home.DOCK` MUST be set, **or**
- The sender's own `freer.home.FUDP` MUST be set (requires a fixed reachable IP/port).

If neither is set, the local IM layer MUST be disabled until the user configures one. Implementations SHOULD prompt the user at first run.

## 4. Channel Selection

### 4.1. DOCK (default)

The sender's client posts the binary envelope to the recipient's DOCK server (or to its own DOCK with a forwarding hint). The recipient retrieves messages from its DOCK on its own schedule.

|Field|Value|
|---|---|
|`recipients`|`[<recipientFid>]` (single FID)|
|`dataType`|`"IM"`|
|body|compact binary wire format of the `ImMessage`|
|target dock URL|recipient's advertised DOCK URL (from `freer.home` or cached `TalkPartner.home`); senders MAY post to their own DOCK with `targetDockUrl` set to the recipient's DOCK to use FAPI13 forwarding.|

The recipient retrieves with `dock.fetch` and `recipientIds = [<own FID>]` (or omitted). The recipient MUST decode the body via the compact binary wire format.

### 4.2. FUDP direct (opt-in)

The sender's FUDP node sends the binary envelope to the recipient's FUDP node as a `NOTIFY` frame with `dataType = IM` (a reserved data-type code; see implementation). Delivery is acknowledged by the transport-level `NOTIFY_ACK`.

A sender MAY use this channel only when **all** of the following hold:

- The sender has enabled the *Use direct FUDP* setting.
- The sender's `liveFid` equals its `mainFid` (sub-FIDs do not have access to the prikey required for the FUDP handshake).
- The sender's own `freer.home.FUDP` is set.
- The recipient's `freer.home.FUDP` is set (resolved from `TalkPartner.home` cache or from on-chain `freer.home`).

If any condition fails, FUDP MUST be skipped without delay or fallback retry.

### 4.3. ROAD relay (opt-in)

The sender's ROAD client forwards the binary envelope to the target's ROAD URL (resolved from `freer.home.ROAD`). ROAD delivery may incur a fee charged by the relay service; this is why it is opt-in.

A sender MAY use this channel only when:

- The sender has enabled the *Use ROAD relay* setting.
- The recipient's `freer.home.ROAD` is set.

### 4.4. Selection rules

For each outbound P2P message, the sender SHOULD attempt channels in the following order, stopping on the first success:

1. If FUDP is enabled and §[4.2](#42-fudp-direct-opt-in) preconditions hold: try FUDP. On `NOTIFY_ACK` timeout (transport-defined), proceed to step 2.
2. If ROAD is enabled and §[4.3](#43-road-relay-opt-in) preconditions hold: try ROAD. On failure, proceed to step 3.
3. Always try DOCK if the recipient's DOCK URL is resolvable. If the resolution fails, the message MUST be marked `FAILED` and surfaced to the sender.

If FUDP and ROAD are both disabled (the default), step 3 is the only channel and is taken immediately without latency overhead.

### 4.5. Required configuration

The IM layer is enabled only when the local identity satisfies all of:

- `liveFid == mainFid` (sub-FIDs are read-only with respect to IM).
- `freer.home.DOCK` is set, **or** `freer.home.FUDP` is set.

When neither is set, the application MUST prompt the user to register one on-chain. If the user declines, P2P chat MUST present a disabled state and reject `send()` calls until the user completes setup.

## 5. Message Contracts

Throughout this section, `content` and `data` name the two **sections of the body** (FIMP0 §Body Framing), not envelope fields. Where the body is sealed (§7), they describe the plaintext *inside* the seal.

### 5.1. TEXT

A plain text message.

|Field|Value|
|---|---|
|`type`|`P2P`|
|`contentType`|`TEXT`|
|`senderId`|sender FID|
|`targetId`|recipient FID|
|`timestamp`|ms since epoch|
|`content`|text body (UTF-8)|

### 5.2. STREAM

Inline binary attachment. The encoded envelope MUST fit the destination's resolved size budget (FIMP0 §Payload Sizing); a payload that does not fit MUST be sent as a HAT instead (§[5.4](#54-hat)).

|Field|Value|
|---|---|
|`contentType`|`STREAM`|
|`content`|metadata JSON (body section 1)|
|`data`|the binary payload, raw bytes (body section 2)|

`content` JSON schema:

```json
{
  "name": "string (file name)",
  "size": 12345,
  "type": "string (MIME type or extension)"
}
```

Implementations MAY include additional fields; receivers MUST ignore unknown fields.

### 5.3. VOICE

Inline voice message. The audio bytes are typically AAC; receivers MUST be prepared for the `format` field to indicate other codecs in future versions.

`VOICE` is subject to the same size budget as any other binary content type. A voice note whose encoded envelope exceeds the destination's resolved budget MUST be sent as a HAT (§[5.4](#54-hat)); implementations MUST NOT assume a voice note is always inline. Senders SHOULD choose speech-grade encoding parameters so that ordinary notes stay inline, since the inline path avoids a DISK round trip and a key-wrapping step.

|Field|Value|
|---|---|
|`contentType`|`VOICE`|
|`content`|metadata JSON (body section 1)|
|`data`|the audio bytes, raw (body section 2)|

`content` JSON schema:

```json
{
  "durationMs": 4200,
  "sampleRate": 16000,
  "format": "aac"
}
```

### 5.4. HAT

Reference to a large object stored on DISK. The recipient retrieves the body via DISK using the `Hat` reference.

|Field|Value|
|---|---|
|`contentType`|`HAT`|
|`content`|JSON of a `Hat` object (FTSP / FAPI12)|

The `Hat` object contains at minimum: `id` (DID), `hAlg`, `size`, optional `key`/`kCipher`, `locas`, `name`, `desc`. See FAPI12 for the full schema. For confidential P2P attachments, the sender SHOULD encrypt the file with a fresh symmetric key, place the encrypted DID in `Hat.id`, and place the symmetric key wrapped to the recipient's pubkey in `Hat.kCipher`.

### 5.5. RECEIPT

Delivery or read acknowledgment. P2P only.

|Field|Value|
|---|---|
|`contentType`|`RECEIPT`|
|`content`|`"delivered"` or `"read"`|
|`requestId`|`id` of the original message being acknowledged|

When the original message was delivered via FUDP, the transport-level `NOTIFY_ACK` already implies *delivered*; receivers MUST NOT additionally emit a `delivered` RECEIPT in that case. A `read` RECEIPT is always carried as a regular P2P message in the binary envelope, regardless of channel.

A RECEIPT MUST NOT itself trigger a RECEIPT.

### 5.6. REACTION, EDIT, DELETE, FORWARD, TYPING, PRESENCE

|ContentType|Required fields|Description|
|---|---|---|
|`REACTION`|`requestId` = target message id; `content` = reaction string (typically a single emoji)|Add a reaction to a previous message.|
|`EDIT`|`requestId` = original message id; `content` = new body|Edit a previous text message.|
|`DELETE`|`requestId` = target message id|Soft-delete a previous message.|
|`FORWARD`|`content` = forwarded body or preview; `replyToId` MAY reference the original|Forward a message.|
|`TYPING`|none|Indicates the sender is typing. Receivers SHOULD treat as ephemeral.|
|`PRESENCE`|`content` = presence JSON|Online status. Receivers SHOULD treat as ephemeral. Implementations MAY omit a UI presence indicator.|

REACTION, EDIT, and DELETE address messages by `id` (16-char hex). The receiver applies them to its local view of the conversation.

## 6. Request / Response

A request message uses `contentType = REQUEST`, `requestType = <enum>`, and `content` carrying the request payload. The response uses `contentType = RESPONSE`, `requestId` = the request's `id`, and `content` carrying the response payload. `requestId` is the correlation key on the sender side.

### 6.1. PUBLIC_KEY

Request the recipient's public key. Used when the sender needs to wrap a symmetric key for the recipient (e.g., a Hat kCipher) and has no cached pubkey.

|Direction|Field|Value|
|---|---|---|
|Request|`contentType`|`REQUEST`|
|Request|`requestType`|`PUBLIC_KEY`|
|Request|`content`|empty or JSON `{"alg": "secp256k1"}`|
|Response|`contentType`|`RESPONSE`|
|Response|`requestId`|request id|
|Response|`content`|hex-encoded compressed public key|

### 6.2. HAT

Request a Hat record by id (e.g., when the sender provides only an id and the receiver wants the full record before fetching from DISK).

|Direction|Field|Value|
|---|---|---|
|Request|`requestType`|`HAT`|
|Request|`content`|the Hat id (string)|
|Response|`content`|the JSON of the `Hat` object|

### 6.3. MESSAGE_SYNC

Request that the peer resend any messages it has sent since a given timestamp that the requester may have missed. Optional; not all peers will support this.

|Direction|Field|Value|
|---|---|---|
|Request|`requestType`|`MESSAGE_SYNC`|
|Request|`content`|JSON `{"since": <ms>, "max": <int>}`|
|Response|`content`|JSON array of ImMessage objects (each as a JSON record with the same field names as in local storage). Implementations MAY split into multiple responses correlated by the same `requestId`.|

### 6.4. HISTORY

Request the peer's record of the conversation, encrypted as a single file on DISK. The response uses `contentType = HISTORY` (not `RESPONSE`).

|Direction|Field|Value|
|---|---|---|
|Request|`contentType`|`REQUEST`|
|Request|`requestType`|`HISTORY`|
|Request|`content`|JSON `{"entityId": <peerFid>, "since": <ms?>, "before": <ms?>}`|
|Response|`contentType`|`HISTORY`|
|Response|`content`|JSON of the `Hat` referencing the encrypted history file on DISK|
|Response|`data`|the kCipher: the file's symmetric key wrapped to the requester's pubkey as a `CryptoDataByte` of `type: "asy1way"`, UTF-8 encoded. Carried raw in the body's `data` section -- version 1's extra Base64 layer is removed.|

The file on DISK MUST be a serialization of an array of `ImMessage` JSON records, encrypted with a fresh symmetric key using AES-GCM.

The records are local JSON, not signed envelopes, so nothing in the file proves who wrote each message: that rests on the FID that answered the request. An importer MUST mark imported messages as unverified wherever it shows who sent them.

## 7. Encryption

P2P seals its body whenever the message crosses an intermediary. The rule is per **channel**, not per message type, and `symkeyVersion` is never set on a P2P message.

|Channel|Body|Scheme|
|---|---|---|
|FUDP_DIRECT|Plaintext. `FLAG_BODY_SEALED` clear.|None. The FUDP session runs between exactly the two endpoints and is already end-to-end; a second layer would protect against nobody.|
|ROAD_RELAY|Sealed. `FLAG_BODY_SEALED` set.|`asy2way`: sender's private key + recipient's public key, per FTSP.|
|DOCK_STORED|Sealed. `FLAG_BODY_SEALED` set.|`asy2way`, as above.|
|Any, self-chat|Sealed. `FLAG_BODY_SEALED` set.|`asy1way` to one's own public key.|

### 7.1. What is sealed

The entire body framing -- both the `content` and `data` sections -- is the plaintext input to the seal, and the sealed bundle is what the envelope carries (FIMP0 §Body Framing). Implementations MUST NOT seal the sections separately, and MUST NOT seal one and leave the other in the clear.

The envelope header stays readable: `type`, `senderId`, `targetId`, `timestamp`, `contentType`, the flags and the message ids. A DOCK must route on the recipient and a receiver must deduplicate before it can decrypt, so these cannot be private.

### 7.2. Self-chat

When `senderId == targetId`, the sender is messaging its own identity (a personal notes conversation). `asy2way` is not usable here: it would place the same key in both slots, and the receiver's side-selection cannot resolve which of two identical keys is the counterparty. Self-chat therefore seals with `asy1way` to the sender's own public key. The bundle is self-describing, so the receiver needs no separate signal to choose the opening path.

### 7.3. No plaintext fallback

A sender that cannot seal a body it is required to seal -- an unresolvable recipient public key, a crypto failure -- MUST fail the send and surface the failure. It MUST NOT downgrade to an unsealed body, and it MUST NOT silently reroute to the direct channel to avoid sealing.

### 7.4. What sealing does and does not protect

Sealing protects the payload from the DOCK operator, the ROAD operator, and anyone with access to their storage. A DOCK retains items for up to a year by default, so this is the difference between a payload that rests in a third party's datastore readable and one that does not.

It does not conceal metadata. The intermediary still learns that a given sender addressed a given recipient at a given time with a given `contentType`, and it learns the payload's approximate size. Senders needing to conceal that fact MUST NOT rely on FIMP.

### 7.5. Sealing is not authorship

Opening an `asy2way` bundle needs only the recipient's private key and the pubkey recorded inside the bundle, so a bundle sealed with any key opens cleanly whatever `senderId` the envelope names. Authorship comes from the signature trailer, not from the seal. As defence in depth, a receiver SHOULD additionally discard a DOCK- or ROAD-delivered P2P message when:

- the body is `asy2way` and the recorded pubkey does not hash to `senderId`;
- the body is `asy1way` and the message is not from and to the receiver's own FID (a throwaway key proves nothing about the sealer); or
- the body carries content or data but is not sealed.

A sender MUST therefore seal every P2P message with a payload on the DOCK and ROAD channels -- control messages (`SYMKEY`, `REQUEST`, `ROOM_INFO` and the rest) included, not only chat.

> **Change from version 1.** FIMP1V1 stated that P2P adds no IM-layer encryption, reasoning that FUDP transport encryption suffices. That reasoning holds for the direct channel and fails for the other two: FUDP secures each *hop*, and a DOCK terminates its hop. Under version 1 the payload -- including, for a file share, the symmetric key that unlocks the DISK object it pointed at -- rested in the DOCK's datastore in the clear. Version 2 seals it.

## 8. Stranger Handling

The IM layer does not vouch for strangers. The envelope signature proves which FID wrote a message, but a recipient has no automatic basis to trust an unknown FID's content. Implementations SHOULD provide a local quarantine for messages from unknown senders. Quarantine is a local concept; it does NOT change the wire envelope and does NOT generate a wire-level signal back to the sender. A quarantined sender SHOULD NOT receive any signal that distinguishes quarantine from successful delivery.

Receivers MUST NOT silently drop a stranger's message at the wire level; the message is stored locally (possibly in a separate quarantine area) and surfaced to the user according to local policy.

## 9. Security Considerations

### 9.1. Replay

A signed envelope verifies every time it is presented. Anyone who has seen one can put the same bytes on a DOCK again under a new item id, so de-duplicating by `DockItem.id` stops nothing. Receivers MUST remember the `(senderId, id)` of every message they take in, for at least as long as a DOCK may keep an item (365 days), and MUST discard a second arrival before acting on it. This applies to signals and control messages as much as to chat: a replayed `SYMKEY`, `REQUEST` or `RECEIPT` is an action taken twice.

A receiver MUST also discard a P2P message whose `targetId` is not one of its own FIDs, and a DOCK item whose recipients do not include the message's `targetId`. The signature binds the target, but a validly signed message can still be stored for someone it was never for.

Receivers MUST NOT rely on `ImMessage.timestamp` for freshness. It is signed, so it cannot be altered, but it is the sender's clock and may be skewed.

### 9.2. Sender forgery

The author of a message is the FID whose key signed it (FIMP0V3 §3). Receivers MUST verify the signature trailer and discard a message whose trailer pubkey does not hash to `senderId`. The FUDP peer and `DockItem.sender` identify connections, not authors: a DOCK that forwards an item re-puts it under its own identity, and a client that holds several identities may connect with one key while writing as another. Receivers MUST NOT use either to establish who wrote a message.

### 9.3. Receipt forgery

A `RECEIPT` is signed like any other message, so a third party cannot forge one, but its content is still the peer's own claim. A peer that wishes to lie about delivery or read state can do so. Senders MUST NOT make security decisions based on receipts.

### 9.4. Message id collisions

The 16-char hex id is generated by the sender's FUDP node as a 64-bit value. Across two different senders, ids may collide; receivers MUST treat the tuple `(senderId, id)` as the uniqueness key when correlating REPLY, RECEIPT, REACTION, EDIT, DELETE, etc.

## 10. Versioning

This document defines version 3 of the P2P mode (FIMP1V3), which accompanies the version 3 envelope of FIMP0V3 and does not interoperate with version 2 or version 1.

Changes from FIMP1V2:

1. Every message is signed by its author (FIMP0V3 §3), and authorship is taken from the signature instead of the FUDP peer or `DockItem.sender` (§9.2).
2. Receivers de-duplicate on `(senderId, id)` rather than `DockItem.id`, and discard messages not addressed to them (§9.1).
3. Every P2P message with a payload is sealed on the DOCK and ROAD channels, control messages included; receivers SHOULD check the seal's pubkey against the sender (§7.5).
4. Imported HISTORY files carry no signatures; their messages MUST be presented as unverified (§6.4).

Changes from FIMP1V1, retained from version 2:

1. The body is sealed on the DOCK and ROAD channels (§7). Version 1 sealed nothing at the IM layer.
2. `content` and `dataBase64` are no longer envelope fields; they are the two sections of the single `body` field, and `data` is raw bytes rather than Base64 (§5).
3. The fixed 900 KB inline limit is replaced by the destination's resolved size budget (FIMP0 §Payload Sizing), and `VOICE` is no longer assumed to be unconditionally inline (§5.3).

Future versions MAY add ContentTypes and RequestTypes (appended to their enums). Wire-incompatible changes require a new version number.

## 11. Related Protocols

- **FIMP0V3** -- Foundational rules, the `ImMessage` envelope, body framing, payload sizing, and the envelope signature and receive checks (`FIMP0V3_Signing_Proposal`).
- **FIMP2V3** -- Room mode (uses P2P as the transport for SYMKEY pushes and ROOM_INFO invites).
- **FIMP4V3** -- Team mode (uses P2P as the transport for SYMKEY pushes).
- **FAPI13V1** -- DOCK store-and-forward service, including the per-item `maxDataSize` ceiling.
- **FAPI12V1** -- DISK service for HAT-referenced large attachments.
- **FTSP** -- `asy1way` / `asy2way` sealing and the binary bundle format.
- **FUDP** -- Encrypted transport, NOTIFY/NOTIFY_ACK framing, message id allocation.
