# FIMP1V1_P2P

|Field|Content|
|---|---|
|Title|P2P|
|Type|FIMP|
|SN|1|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-05-08|
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
- [8. Stranger Handling](#8-stranger-handling)
- [9. Security Considerations](#9-security-considerations)
- [10. Versioning](#10-versioning)
- [11. Related Protocols](#11-related-protocols)

---

## Abstract

FIMP1V1 defines the **P2P** mode of FIMP -- direct one-to-one messaging between two FIDs. The default delivery channel is the recipient's **DOCK** server (store-and-forward). Two optional channels -- direct **FUDP** and **ROAD** relay -- may be enabled by the sender via local settings to reduce latency when both peers are online or when the sender wishes to avoid storing the message on a DOCK. The IM layer adds no encryption beyond transport encryption; the recipient's authenticated identity at the FUDP / ROAD / DOCK layer is sufficient. P2P is also the carrier for several control messages used by Room and Team modes (e.g., `SYMKEY` push, `ROOM_INFO` invitation), which inherit P2P delivery semantics.

## 1. Overview

P2P messages are characterized by:

- `ImMessage.type = P2P`.
- `targetId` is the **recipient FID**.
- The message is delivered to exactly one recipient.
- No symmetric key is involved at the IM layer; `cipher` and `symkeyVersion` MUST NOT be set.
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

Inline binary attachment. The decoded body MUST NOT exceed 900 KB; larger payloads MUST use HAT (§[5.4](#54-hat)).

|Field|Value|
|---|---|
|`contentType`|`STREAM`|
|`content`|metadata JSON|
|`dataBase64`|Base64 of the binary payload|

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

|Field|Value|
|---|---|
|`contentType`|`VOICE`|
|`content`|metadata JSON|
|`dataBase64`|Base64 of the audio bytes|

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
|Response|`dataBase64`|Base64 of the kCipher (the file's symmetric key wrapped to the requester's pubkey, as a `CryptoDataByte` JSON of `type: "asy1way"`, then UTF-8 encoded, then Base64'd)|

The file on DISK MUST be a serialization of an array of `ImMessage` JSON records, encrypted with a fresh symmetric key using AES-GCM.

## 7. Encryption

P2P does **not** add IM-layer encryption. The `cipher` and `symkeyVersion` flags MUST NOT be set on a P2P message. Confidentiality relies on the underlying transport(s):

- FUDP encryption between sender and recipient (FUDP_DIRECT).
- FUDP encryption between sender and ROAD, plus an inner FUDP encryption between ROAD and recipient (ROAD_RELAY).
- FUDP encryption between sender and DOCK, plus FUDP encryption between recipient and DOCK (DOCK_STORED).

When a P2P message is delivered via DOCK or ROAD, the intermediate server sees the message envelope (sender, recipient, contentType, payload). Senders that require end-to-end confidentiality from intermediate servers MAY:

- Use a `HAT` body whose underlying DISK payload is encrypted with a key wrapped to the recipient's pubkey (the standard pattern for confidential attachments).
- Use a P2P `SYMKEY` exchange (per FIMP2 / FIMP4) with the recipient and switch to a Room (with two members) for further messages.

Future versions MAY introduce optional per-message asymmetric encryption for P2P; in version 1, no such mechanism is defined.

## 8. Stranger Handling

The IM layer does not authenticate strangers. The transport layer authenticates the sender's FID, but a recipient has no automatic basis to trust an unknown FID's content. Implementations SHOULD provide a local quarantine for messages from unknown senders. Quarantine is a local concept; it does NOT change the wire envelope and does NOT generate a wire-level signal back to the sender. A quarantined sender SHOULD NOT receive any signal that distinguishes quarantine from successful delivery.

Receivers MUST NOT silently drop a stranger's message at the wire level; the message is stored locally (possibly in a separate quarantine area) and surfaced to the user according to local policy.

## 9. Security Considerations

### 9.1. Replay

FUDP guarantees freshness of each NOTIFY through its own nonce/sequence machinery. DOCK items are stored under a server-generated id and are de-duplicated by the recipient using the dock id. Receivers MUST NOT rely on `ImMessage.timestamp` for freshness; an adversary cannot forge a timestamp without forging the FUDP-authenticated sender, but a clock-skewed sender may produce out-of-order timestamps.

### 9.2. Sender forgery

The authenticated sender FID is established by FUDP at the transport layer (direct) or by the DOCK server's `DockItem.sender` field (offline). Receivers MUST NOT trust a `senderId` field that disagrees with the FUDP-authenticated peer or the `DockItem.sender`. If they disagree, the message MUST be discarded.

### 9.3. Receipt forgery

A `RECEIPT` is no more authenticated than any other P2P message. A peer that wishes to lie about delivery or read state can do so. Senders MUST NOT make security decisions based on receipts.

### 9.4. Message id collisions

The 16-char hex id is generated by the sender's FUDP node as a 64-bit value. Across two different senders, ids may collide; receivers MUST treat the tuple `(senderId, id)` as the uniqueness key when correlating REPLY, RECEIPT, REACTION, EDIT, DELETE, etc.

## 10. Versioning

This document defines version 1 of the P2P mode (FIMP1V1). Future versions MAY add ContentTypes (appended to the enum) and RequestTypes (appended to the enum). Wire-incompatible changes -- for example, redefining the meaning of `cipher` for P2P -- require a new version number.

## 11. Related Protocols

- **FIMP0V1** -- Foundational rules and the `ImMessage` envelope.
- **FIMP2V1** -- Room mode (uses P2P as the transport for SYMKEY pushes and ROOM_INFO invites).
- **FIMP4V1** -- Team mode (uses P2P as the transport for SYMKEY pushes).
- **FAPI13V1** -- DOCK store-and-forward service.
- **FAPI12V1** -- DISK service for HAT-referenced large attachments.
- **FUDP** -- Encrypted transport, NOTIFY/NOTIFY_ACK framing, message id allocation.
