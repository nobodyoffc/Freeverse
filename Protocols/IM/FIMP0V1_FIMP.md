# FIMP0V1_FIMP

## Contents

- [Summary](#summary)
- [Abstract](#abstract)
- [What is FIMP](#what-is-fimp)
- [Scope Boundaries](#scope-boundaries)
- [General Rules](#general-rules)
- [The ImMessage Envelope](#the-immessage-envelope)
- [Enumerations](#enumerations)
- [Encryption Model](#encryption-model)
- [Delivery Channels](#delivery-channels)
- [DOCK Conventions](#dock-conventions)
- [Identifiers](#identifiers)
- [Protocol Document Structure](#protocol-document-structure)
- [FIMP List](#fimp-list)

---

## Summary

|Field|Content|
|---|---|
|Title|FIMP|
|Type|FIMP|
|SN|0|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-05-08|
|PID||

## Abstract

FIMP (Freeverse Instant Messaging Protocol) defines a decentralized instant messaging framework for the Freeverse ecosystem. FIMP runs over FUDP encrypted transport and FAPI application services (DOCK for store-and-forward, DISK for large attachments, BASE for on-chain entity lookup). It defines a single message envelope -- `ImMessage` -- that is shared by four messaging modes: **P2P** (direct one-to-one), **Room** (locally-defined group), **Square** (open on-chain group), and **Team** (closed on-chain owner-managed group). Each mode has its own membership semantics, encryption rules, and DOCK addressing conventions, all specified in companion documents (FIMP1-4).

This document (FIMP0) defines the foundational rules shared by all FIMP modes: the wire envelope, content-type registry, request-type registry, encryption layering, delivery channels, and identifier conventions.

## What is FIMP

### Naming

FIMP stands for **Freeverse Instant Messaging Protocol**.

- **F** -- Freeverse: FIMP is the standard messaging layer of the Freeverse ecosystem.
- **IM** -- Instant Messaging: FIMP carries chat-style messages between identities.
- **P** -- Protocol: Each FIMP document defines a formal specification for one mode or the shared core.

### Identification

Each FIMP protocol is identified by its serial number (`sn`) and version (`ver`). The naming convention is:

```
FIMP{sn}V{ver}_{Name}
```

For example: `FIMP1V1_P2P` refers to the P2P mode, serial number 1, version 1.

### The Need for FIMP

FUDP defines an encrypted peer-to-peer transport. FAPI defines decentralized API services (BASE/DISK/DOCK/MAP/ROAD). Neither defines what messages users send to one another, how groups are addressed, how a closed group rotates a shared key, or how a recipient who is offline retrieves messages later.

FIMP fills this gap by specifying:

- A single self-describing message envelope (`ImMessage`) that is mode-agnostic on the wire.
- Four messaging modes (P2P, Room, Square, Team) that differ only in membership and encryption rules.
- The DOCK store-and-forward conventions (recipient lists, `dataType` tags, payload framing) that allow offline delivery.
- The encryption layering: FUDP secures all transport hops; FIMP layers a per-message symmetric cipher on top **only** for closed-group modes (Room, Team).

A conformant FIMP implementation can interoperate with the Freer Android reference client without sharing source code.

### Relationship with Other Protocols

FIMP depends on and references several Freeverse protocol series:

- **FUDP** -- Provides the encrypted, reliable transport layer for direct delivery and for FAPI requests. All FIMP traffic ultimately rides on FUDP streams. Authentication and confidentiality of every hop are handled entirely by FUDP; FIMP does not embed signatures, nonces, or transport-level credentials.
- **FAPI1V1 (Core Protocol)** -- Defines the wire format for API requests sent to FAPI servers (used to talk to DOCK, DISK, BASE).
- **FAPI13V1 (DOCK)** -- The store-and-forward service used to deliver messages to offline recipients and to fan out to group/team/room recipients. FIMP defines the `dataType` tag, recipient identifier conventions, and payload framing for items stored on DOCK.
- **FAPI12V1 (DISK)** -- Used for attachments larger than the inline data limit. FIMP messages carry a `HAT` reference (DID + key cipher) to a DISK object.
- **FAPI11V1 (BASE)** -- Used to look up `Square` and `Team` records on chain (membership, owner, advertised DOCK URL).
- **FEIP** -- Defines the on-chain protocol-level data formats. Square and Team are FEIP entities; FIMP only references their lookup. Room is **not** an on-chain entity.
- **FVEP** -- Defines FIDs and identity concepts used throughout FIMP.
- **FTSP** -- Defines the cryptographic algorithms (AES-GCM, ECC secp256k1, ECDH, asymmetric one-way encryption) that FIMP uses.

### Position in the Protocol Stack

|Layer|Protocol Series|Scope|
|---|---|---|
|Blockchain Consensus|FBP|Block validation, transaction rules, mining|
|On-chain Application|FEIP|Square and Team registration, member lists|
|Ecosystem Foundation|FVEP|FIDs, identities|
|Technical Standard|FTSP|Cryptographic primitives (ECDH, AES-GCM, etc.)|
|Transport|FUDP|P2P encrypted transport over UDP|
|Application Service|FAPI|BASE, DISK, DOCK, MAP, ROAD|
|**Application**|**FIMP**|**Instant messaging modes (P2P, Room, Square, Team)**|

FIMP sits above FAPI and below user-facing applications. It consumes FUDP-provided encrypted streams and FAPI-provided storage services, and exposes a uniform messaging contract.

## Scope Boundaries

### What Belongs in FIMP

FIMP protocols define the wire-level behavior of the messaging layer:

1. **Message envelope** -- The `ImMessage` schema, its binary wire format, and the mapping between local-only and on-the-wire fields.
2. **Mode contracts** -- For each of P2P, Room, Square, and Team: which envelope fields are populated, what `targetId` means, what `cipher`/`symkeyVersion` mean, and what `content`/`dataBase64` carry for each ContentType.
3. **DOCK conventions** -- The `dataType` tag, recipient list semantics, dock URL selection, and payload framing used to deliver messages via FAPI13.
4. **Encryption layering** -- Which modes encrypt at the IM layer (Room, Team) and which rely solely on FUDP-level encryption (P2P, Square).
5. **Lifecycle messages** -- Symkey distribution, room invitations, member updates, history sync, and other control messages.

### What Does NOT Belong in FIMP

- **Transport mechanics** -- FUDP (packet framing, retransmission, ECDH handshake, session keys, replay protection).
- **Cryptographic algorithm internals** -- FTSP (e.g., how AES-GCM works, how ECDH derives a shared secret).
- **On-chain registration** -- FEIP (e.g., how a Square or Team is created on chain). FIMP only references the lookup.
- **API request framing for DOCK/DISK/BASE** -- FAPI1 (request/response envelope) and FAPI11/12/13 (per-component schemas).
- **Local UI, storage, threading, retry policy, queue eviction** -- Implementation concerns.
- **Identity and address derivation** -- FVEP / FTSP.

## General Rules

### 1. Transport Requirement

All FIMP communication MUST be carried over FUDP for direct delivery, or over FAPI13 (DOCK) for store-and-forward delivery (which itself uses FUDP between client and DOCK server). FIMP messages MUST NOT be sent over raw UDP, TCP, HTTP, or any other transport. The FUDP layer provides encryption, authentication, reliability, and stream multiplexing.

### 2. No FIMP-Level Authentication

The identity of the sender is established by FUDP during the connection handshake using secp256k1 public-key cryptography. The DOCK server propagates the authenticated sender FID into the `sender` field of every stored item. FIMP messages MUST NOT include signatures, nonces, authentication tokens, or other credentials in the envelope. A receiver MUST treat the FUDP-authenticated peer FID (and, for DOCK-delivered items, the `DockItem.sender` field) as authoritative.

### 3. Wire Format

FIMP messages cross the wire in a single canonical representation of the `ImMessage` object:

- **Compact binary wire format** -- Used for direct FUDP `NOTIFY` payloads and for the body of DOCK items. See [The ImMessage Envelope](#the-immessage-envelope) below for the exact binary layout. Every mode and every ContentType -- including `TEXT` and `RECEIPT` -- is encoded this way on every delivery channel (FUDP, ROAD, DOCK). There is no separate plain-text fast path.

JSON SHOULD be used for **local storage** of `ImMessage` objects but MUST NOT be used as a wire encoding between FIMP peers. Implementations that re-encode the envelope as JSON for transport are non-conformant.

### 4. Message Modes

FIMP defines four messaging modes, expressed by the `type` field of `ImMessage`:

|Mode|Spec|Membership|Wire Encryption|
|---|---|---|---|
|P2P|FIMP1|Two endpoints; no shared state|FUDP transport only|
|Square|FIMP3|On-chain FEIP record; open membership|FUDP transport only (plaintext payload)|
|Team|FIMP4|On-chain FEIP record; owner-managed membership|FUDP transport + AES-GCM symkey on payload|
|Room|FIMP2|Local; owner-managed membership; not on chain|FUDP transport + AES-GCM symkey on payload|

The wire envelope is identical across all four modes; only the field population, recipient addressing, and encryption layer differ.

### 5. Status Codes

FIMP itself does not define status codes for message delivery. Delivery state is tracked locally by the sender using `MessageStatus` (a local-only enum: `PENDING`, `QUARANTINED`, `SENT`, `DELIVERED`, `READ`, `FAILED`, `IMPORTED`). FAPI13 status codes apply to DOCK API calls. FUDP `NOTIFY_ACK` acknowledgments apply to direct FUDP delivery.

### 6. Monetary Values

FIMP itself does not move money. DOCK fees incurred by storage and retrieval are denominated and accounted under FAPI13 / FAPI4.

### 7. JSON Encoding

When ImMessage is serialized as JSON for local storage, JSON output MUST conform to [RFC 8259](https://www.rfc-editor.org/rfc/rfc8259). Field names use camelCase. The local-storage JSON schema includes ALL `ImMessage` fields, including local-only fields; the binary wire format excludes local-only fields. Implementations SHOULD ignore unrecognized fields for forward compatibility and MUST NOT reject a record solely because it contains unrecognized fields.

### 8. RFC 2119 Keywords

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in FIMP documents are to be interpreted as described in [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).

### 9. Reference Implementation

The reference implementation of FIMP is the Freer Android client and the FC-AJDK Java library. Where the specification and the reference implementation diverge, the reference implementation is authoritative and the specification SHOULD be amended.

### 10. Protocol Versioning

The current FIMP protocol version is **1**. Wire-incompatible changes MUST increment the version number; minor additions (new optional fields, new ContentType values appended at the end of the enum, new RequestType values appended at the end) are version-1-compatible provided that ordinal positions of existing enum values are not changed.

### 11. Language Agnosticism

FIMP is a language-agnostic protocol. Any platform able to produce and consume the binary envelope and exchange data over FUDP / FAPI13 can implement a conformant FIMP client.

## The ImMessage Envelope

`ImMessage` is the single envelope used across all four modes. The same record is produced by senders, transmitted on the wire, and stored by receivers (with local-only fields populated post-receipt).

### 1. Logical Fields

|Field|Type|Wire?|Required|Description|
|---|---|---|---|---|
|`id`|string (16-char lowercase hex)|Yes (optional flag)|See §[Identifiers](#identifiers)|Globally unique message identifier. Generated as an unsigned 64-bit integer by the FUDP layer of the originating sender, encoded as 16-char lowercase hex (`%016x`).|
|`type`|enum `ImType`|Yes|Yes|`P2P`, `SQUARE`, `TEAM`, `ROOM`. Determines mode.|
|`senderId`|string (FID)|Yes|Yes|Authenticated FID of the originator.|
|`targetId`|string|Yes|Yes|Recipient identifier. Semantics depend on `type`: peer FID for P2P; `squareId` for Square; `teamId` for Team; `roomId` for Room.|
|`timestamp`|int64 (ms since epoch)|Yes|Yes|Sender's local time at message creation.|
|`sequence`|int64|No (local-only)|No|Tie-breaker for messages within the same millisecond. Local ordering aid.|
|`contentType`|enum `ContentType`|Yes|Yes|See [Enumerations](#enumerations).|
|`content`|string|Yes (flag)|Conditional|Plaintext payload. For TEXT, plain text; for HAT, JSON of a `Hat` object; for STREAM/VOICE, metadata JSON; for REQUEST, request data; for ROOM_INFO, JSON of a `RoomInfo`; for ROOM_LEAVE, the `roomId`. MUST be `null` when the payload is encrypted (use `cipher` instead).|
|`dataBase64`|string (Base64)|Yes (flag)|Conditional|Inline binary payload. Used by STREAM, VOICE, HISTORY (carries the kCipher), and other binary content types. The decoded byte length MUST NOT exceed 900 KB (`MAX_INLINE_DATA_SIZE`); larger payloads MUST use HAT + DISK.|
|`requestType`|enum `RequestType`|Yes (flag)|Conditional|Set when `contentType` is `REQUEST`. See [Enumerations](#enumerations).|
|`requestId`|string|Yes (flag)|Conditional|For `RESPONSE` and `RECEIPT` content types, the `id` of the message being answered or acknowledged.|
|`cipher`|string (JSON)|Yes (flag)|Conditional|Symmetric-key ciphertext of `content`, when the mode encrypts at the IM layer (Room, Team). The string is a JSON `CryptoDataByte` object as specified by FTSP. When `cipher` is set, `content` MUST be `null`. See [Encryption Model](#encryption-model).|
|`symkeyVersion`|int32|Yes (flag)|Conditional|Identifies which version of the per-room or per-team symkey was used to produce `cipher`. Required whenever `cipher` is set for Room or Team.|
|`replyToId`|string|Yes (flag)|No|`id` of the message being replied to.|
|`threadId`|string|Yes (flag)|No|Thread group identifier.|
|`roadIds`, `dockId`, `deliveryMethod`, `status`, `deliveredAt`, `readAt`, `senderName`, `unread`, `pinned`, `deleted`|various|No (local-only)|No|Local delivery state, UI cache, soft-delete flag. MUST NOT cross the wire.|

### 2. Compact Binary Wire Format

The compact binary wire format is the canonical encoding when ImMessage is sent over FUDP NOTIFY (binary) or stored in DOCK. Multi-byte integers are big-endian.

```
┌──────────────────────────────────────────────────────┐
│ type            uint8       (ordinal of ImType)      │
│ contentType     uint8       (ordinal of ContentType) │
│ senderIdLen     uint8                                │
│ senderId        senderIdLen bytes (UTF-8)            │
│ targetIdLen     uint8                                │
│ targetId        targetIdLen bytes (UTF-8)            │
│ timestamp       int64       (ms since epoch)         │
│ flags           uint16      (bitmap, see below)      │
│ ─── conditional fields, in flag-bit order ────       │
│ if FLAG_CONTENT       : len(uint16) + UTF-8 bytes    │
│ if FLAG_DATA_BASE64   : len(uint16) + UTF-8 bytes    │
│ if FLAG_CIPHER        : len(uint16) + UTF-8 bytes    │
│ if FLAG_SYMKEY_VERSION: int32                        │
│ if FLAG_REQUEST_TYPE  : uint8 (ordinal)              │
│ if FLAG_REQUEST_ID    : len(uint16) + UTF-8 bytes    │
│ if FLAG_REPLY_TO_ID   : len(uint16) + UTF-8 bytes    │
│ if FLAG_THREAD_ID     : len(uint16) + UTF-8 bytes    │
│ if FLAG_MESSAGE_ID    : len(uint16) + UTF-8 bytes    │
└──────────────────────────────────────────────────────┘
```

#### Flag bitmap

|Bit|Mask|Field|
|---|---|---|
|0|`0x0001`|content|
|1|`0x0002`|dataBase64|
|2|`0x0004`|cipher|
|3|`0x0008`|symkeyVersion|
|4|`0x0010`|requestType|
|5|`0x0020`|requestId|
|6|`0x0040`|replyToId|
|7|`0x0080`|threadId|
|8|`0x0100`|messageId (the `id` field)|
|9-15|reserved|MUST be zero in version 1|

#### Length prefixes

- `senderId` and `targetId` use an **unsigned 8-bit** length prefix. A FID is well below 256 bytes.
- All other variable-length string fields use an **unsigned 16-bit** length prefix.

#### Enum ordinals

Enum ordinals are stable. Future versions MAY append new values but MUST NOT reorder existing values. See [Enumerations](#enumerations) for the version 1 ordinal positions.

#### Minimum size

A wire-format message is at least 14 bytes (1+1+1+0+1+0+8+2 with empty IDs and no flags). Implementations MUST reject shorter inputs.

### 3. No Plain-Text Fast Path

Earlier drafts described an optional FUDP "CHAT" fast path that carried `TEXT` and `RECEIPT` payloads as raw UTF-8 strings tagged by a distinct FUDP `dataType`. The reference implementation does not use it: every FIMP message, on every channel (FUDP, ROAD, DOCK) and for every ContentType, is encoded with the compact binary wire format of §2 and carried as a raw FUDP `NOTIFY` payload. There is no dedicated `dataType` selector distinguishing a plain-text path from a binary path.

Conformant implementations MUST NOT emit a plain-text fast-path payload; `TEXT` and `RECEIPT` messages MUST use the binary envelope like every other ContentType. The `id`, `senderId`, `type`, and all populated fields are encoded within that envelope (with `id` optionally carried via the `FLAG_MESSAGE_ID` flag, per §2).

## Enumerations

### ImType

|Ordinal|Name|Mode|
|---|---|---|
|0|`P2P`|Direct one-to-one|
|1|`SQUARE`|Open on-chain group (FIMP3)|
|2|`TEAM`|Closed on-chain owner-managed group (FIMP4)|
|3|`ROOM`|Local owner-managed group (FIMP2)|

### ContentType

|Ordinal|Name|Description|
|---|---|---|
|0|`TEXT`|Plain text body in `content`.|
|1|`HAT`|`content` is a JSON `Hat` referencing data stored on DISK.|
|2|`STREAM`|Inline binary blob. `content` holds metadata JSON (`name`, `size`, `type`); `dataBase64` holds the bytes. Max decoded size 900 KB.|
|3|`SYMKEY`|Push of a symmetric key for Team or Room. `content` holds `<entityId>:<asyOneWayCipherJson>`.|
|4|`MEMBERS`|Push of a member list for Team or Room. `content` holds a JSON list of FIDs.|
|5|`HISTORY`|Push of an encrypted message history. `content` holds a JSON `Hat` (DISK reference); `dataBase64` holds the kCipher (the file's symmetric key wrapped to the recipient's pubkey).|
|6|`REQUEST`|`requestType` is set; `content` is request payload.|
|7|`RESPONSE`|`requestId` is set; `content` is response payload.|
|8|`TYPING`|Typing indicator. No payload required.|
|9|`RECEIPT`|Delivery or read acknowledgment. `requestId` references the original message; `content` is `"delivered"` or `"read"`.|
|10|`PRESENCE`|Online status update.|
|11|`REACTION`|Reaction (typically an emoji) to a message. `content` is the reaction; `requestId` is the target message.|
|12|`EDIT`|Edit of a previous message. `requestId` is the original; `content` is the new body.|
|13|`DELETE`|Soft delete of a previous message. `requestId` is the target.|
|14|`FORWARD`|Forwarded message envelope.|
|15|`ROOM_INFO`|Room metadata bundle (Room mode only). `content` is a `RoomInfo` JSON, see FIMP2.|
|16|`ROOM_LEAVE`|Room leave notification (Room mode only). `content` is the `roomId`.|
|17|`VOICE`|Voice message. `content` holds metadata JSON (`durationMs`, `sampleRate`, `format`); `dataBase64` holds the audio bytes (typically AAC).|
|18|`ROOM_ACCEPT`|Room invitation acceptance (Room mode only). `content` is the `roomId`; sent by an invitee to the owner to confirm joining. See FIMP2 §4.6.|
|19|`ROOM_DISBAND`|Room disband notification (Room mode only). `content` is the `roomId` in the P2P form, or `null` with the `roomId` encrypted in `cipher` in the room-channel form. Sent by the room owner. See FIMP2 §4.7.|
|20|`ROOM_REMOVED`|Room member-removal notification (Room mode only). `content` is the `roomId`; sent by the room owner to a removed member. See FIMP2 §4.8.|

> **Note:** ordinals 18-20 were appended after the initial Draft (see FIMP2 §9). They are Room-mode control signals; other modes MUST NOT emit them. Because they are appended at the end of the enum, they are wire-compatible with implementations that predate them (which simply ignore them).

### RequestType

|Ordinal|Name|Used in modes|
|---|---|---|
|0|`HAT`|All|
|1|`MEMBERS`|Team, Room|
|2|`SYMKEY`|Team, Room|
|3|`MESSAGE_SYNC`|All (typically P2P)|
|4|`HISTORY`|P2P, Team, Room|
|5|`PUBLIC_KEY`|All (typically P2P)|
|6|`SYMKEY_HISTORY`|Team, Room|
|7|`ROOM_INFO`|Room|

## Encryption Model

FIMP uses two layers of encryption.

### Transport Layer (FUDP)

Every FUDP hop is end-to-end encrypted between the two FUDP peers using ECDH-derived session keys. This applies to:

- A direct sender-to-recipient FUDP connection (P2P direct path).
- A sender-to-DOCK-server FUDP connection (DOCK upload path).
- A recipient-to-DOCK-server FUDP connection (DOCK fetch path).
- A sender-to-ROAD-server FUDP connection (ROAD relay path).

Transport encryption protects FIMP traffic from passive observers between the endpoints of each hop. The DOCK and ROAD servers, however, see plaintext at the FUDP layer; they only see the IM-layer ciphertext if the mode encrypts there as well.

### IM Layer (Per-Mode)

The IM layer adds end-to-end confidentiality through any intermediary servers (DOCK, ROAD).

|Mode|IM-layer encryption|Mechanism|
|---|---|---|
|P2P|None|FUDP transport encryption is sufficient between the two endpoints. The DOCK server, when used as fallback, sees the message envelope but the FUDP wrapper around DOCK requests still hides it from observers; FIMP does NOT add a second layer for P2P.|
|Square|None|Open membership; messages are explicitly plaintext. `cipher` MUST NOT be set for Square messages.|
|Team|AES-GCM with team symkey|Sender encrypts `content` (and any inline `dataBase64` if applicable per mode spec) with the current team symkey, places the JSON ciphertext in `cipher`, sets `content` to `null`, and sets `symkeyVersion`.|
|Room|AES-GCM with room symkey|Same as Team. `cipher`, `symkeyVersion` set; `content` cleared.|

### Cipher payload structure

When `cipher` is set, it MUST be the JSON serialization of a `CryptoDataByte` record (see FTSP) representing an AES-GCM ciphertext over the original UTF-8 bytes of `content`. The JSON contains at minimum: `alg`, `type` = `"sym"`, `iv`, `data`. The receiver decrypts using the stored symkey for the cited `symkeyVersion`.

### Symkey distribution

Symmetric keys for Room and Team are distributed via P2P `SYMKEY` messages. The `content` field of a `SYMKEY` message is `"<entityId>:<asyOneWayCipherJson>"`, where `asyOneWayCipherJson` is a `CryptoDataByte` JSON of `type: "asy1way"` representing the symkey wrapped to the recipient's secp256k1 public key (per FTSP). The `symkeyVersion` field carries the version. Per-mode rules (when to push, when to rotate, who may request) are specified in FIMP2 and FIMP4.

## Delivery Channels

FIMP supports three delivery channels, in priority order:

1. **FUDP_DIRECT** -- The sender and recipient have an active FUDP connection (or can establish one). The sender invokes a FUDP `NOTIFY` (with or without ACK) carrying the compact binary wire format of `ImMessage` as the raw payload. This applies to all ContentTypes, including `TEXT` and `RECEIPT`; there is no separate fast-path encoding (see [The ImMessage Envelope §3](#the-immessage-envelope)).
2. **ROAD_RELAY** -- The recipient is reachable through a ROAD server. The sender sends the binary wire format wrapped in a ROAD relay request (see ROAD specification).
3. **DOCK_STORED** -- The recipient is offline or addressable only by group ID. The sender uploads the binary wire format to a DOCK server with appropriate recipients (see [DOCK Conventions](#dock-conventions)). The recipient retrieves with `dock.fetch` later.

The choice of channel is local to the sender and is signaled by the local-only `deliveryMethod` field. The wire envelope is the same across channels; only its containing transport differs.

For Team, Room, and Square, the practical channel is almost always DOCK because the message is addressed to an entity (teamId / roomId / squareId), not to a specific online peer.

## DOCK Conventions

Every DOCK item produced by FIMP follows these rules:

### dataType

The `dataType` tag of every FIMP DockItem MUST be one of:

- `"IM"` -- The body is the compact binary wire format of an `ImMessage`. This is the default for ordinary messages of any mode.
- `"SYMKEY_REQ"` -- (RESERVED) Reserved for future use as an out-of-band symkey request envelope. Implementations MUST NOT route by this tag in version 1 unless explicitly specified by the relevant mode document.
- `"SYMKEY"` -- (RESERVED) Reserved as above.

Receivers MUST ignore items with unrecognized `dataType` values. Receivers MUST NOT reject a fetch response solely because it contains items with unknown `dataType`.

### recipients

The `recipients` array of a DockItem MUST contain at most 100 entries (FAPI13 §2.1). FIMP uses the array as follows:

|Mode|`recipients` content|
|---|---|
|P2P|`[<recipientFid>]` (single FID)|
|Square|`[<squareId>]` (server expands to current members per FAPI13)|
|Team|`[<teamId>]` (server expands to current members)|
|Room|`[<roomId>]` (server expands to current members)|

For SYMKEY and other P2P-typed control messages used by Team or Room, `recipients` is `[<recipientFid>]`.

### Body framing

The DockItem body (transmitted as raw binary on `dock.put`, returned as Base64 in `dock.fetch`) is the **compact binary wire format** of the `ImMessage`. The body MUST NOT be a JSON encoding.

### Dock URL selection

|Mode|Sender posts to|
|---|---|
|P2P|Recipient's advertised DOCK URL (from `freer.home` on chain or cached `TalkPartner.home`).|
|Square|The Square's advertised DOCK URL (from the on-chain Square `home` map). Senders MAY post to their own DOCK with `targetDockUrl` set to the Square's DOCK to use FAPI13 forwarding.|
|Team|The Team's advertised DOCK URL (from the on-chain Team `home` map). Forwarding MAY be used as for Square.|
|Room|The Room owner's advertised DOCK URL (from the room's `home` field, if any), or the sender's own DOCK with the recipient's DOCK as `targetDockUrl`. Because Rooms are not on chain, the DOCK URL is propagated as part of the `RoomInfo` distributed at invite time.|

The `home` map keys for advertised DOCK URLs follow the FAPI service-id convention (e.g. `"DOCK@No1_NrC7"`). Implementations MUST tolerate missing `home` entries and fall back to the sender's own DOCK with `targetDockUrl` set.

### Dedup

Receivers MUST track the `id` of every DockItem they have already routed and skip duplicates. The dedup window MUST cover at least the longest expected DOCK retention period.

## Identifiers

### FID

A Freecash Identity (FID), per FVEP. Used as `senderId` in every message and as `targetId` for P2P messages.

### Message id

A 16-character lowercase hexadecimal string. The underlying value is an unsigned 64-bit integer generated by the originating FUDP node (uniqueness is guaranteed per sender). The conversion is `String.format("%016x", longId)`. Implementations MUST NOT generate hash-based IDs for FIMP messages; the FUDP-allocated ID is the canonical id.

### roomId

For Room mode: a locally-generated identifier of the form `"room_" + <24-char hex>` where the hex is derived from `SHA-256(owner FID || creation millis || secure random 64-bit)`, truncated to the first 24 hex characters. See FIMP2.

### squareId, teamId

Defined by the corresponding on-chain FEIP entity. FIMP treats them as opaque strings. See FIMP3 and FIMP4.

### Symkey id

Symkeys are addressed by `(entityId, version)` where `entityId` is the `roomId` or `teamId`. The `symkeyVersion` field of `ImMessage` carries the version. Versions are monotonic increasing 32-bit integers starting at 1.

## Protocol Document Structure

Each FIMP protocol document SHOULD follow this structure:

```
# FIMP{sn}V{ver}_{Name}

## Contents
## Summary               - Identification table (Title, SN, Ver, Status, Author, Created, PID)
## Abstract              - 2-3 sentence description
## 1. Overview           - Mode model (membership, encryption, lifecycle in one paragraph)
## 2. Data Model         - Mode-specific entities (Room, Team, Square, TalkPartner) and their wire forms
## 3. Lifecycle          - Create / invite / join / leave / disband sequences in terms of ImMessage exchanges and on-chain operations referenced
## 4. Message Contracts  - For each ContentType used in this mode: envelope field population and payload schemas
## 5. Request/Response   - For each RequestType used: request and response wire format
## 6. DOCK Use           - dataType, recipients, payload framing, dock URL selection
## 7. Encryption         - What is encrypted, how, and when keys rotate
## 8. Security Considerations
## 9. Versioning
## 10. Related Protocols
```

## FIMP List

|SN|Name|Scope|
|---|---|---|
|0|FIMP|This document. Foundational rules and the shared `ImMessage` envelope.|
|1|[P2P](FIMP1V1_P2P.md)|Direct one-to-one messaging, receipts, request/response, attachments.|
|2|[Room](FIMP2V1_Room.md)|Local owner-managed group; symkey distribution; ROOM_INFO and ROOM_LEAVE.|
|3|[Square](FIMP3V1_Square.md)|Open on-chain group; plaintext messages; on-chain membership sync.|
|4|[Team](FIMP4V1_Team.md)|Closed on-chain owner-managed group; symkey rotation; encrypted history.|

> **Note:** SN 5+ are reserved for future modes (channels, broadcast, federation).
