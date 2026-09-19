# FIMP0V2_FIMP

## Contents

- [Summary](#summary)
- [Abstract](#abstract)
- [What is FIMP](#what-is-fimp)
- [Scope Boundaries](#scope-boundaries)
- [General Rules](#general-rules)
- [The ImMessage Envelope](#the-immessage-envelope)
- [Payload Sizing](#payload-sizing)
- [Enumerations](#enumerations)
- [Encryption Model](#encryption-model)
- [Delivery Channels](#delivery-channels)
- [DOCK Conventions](#dock-conventions)
- [Identifiers](#identifiers)
- [Changes from Version 1](#changes-from-version-1)
- [Protocol Document Structure](#protocol-document-structure)
- [FIMP List](#fimp-list)

---

## Summary

|Field|Content|
|---|---|
|Title|FIMP|
|Type|FIMP|
|SN|0|
|Ver|2|
|Status|Draft|
|Author|C_armX|
|Created|2026-05-08|
|Updated|2026-08-15|
|PID||

## Abstract

FIMP (Freeverse Instant Messaging Protocol) defines a decentralized instant messaging framework for the Freeverse ecosystem. FIMP runs over FUDP encrypted transport and FAPI application services (DOCK for store-and-forward, DISK for large attachments, BASE for on-chain entity lookup). It defines a single message envelope -- `ImMessage` -- that is shared by four messaging modes: **P2P** (direct one-to-one), **Room** (locally-defined group), **Square** (open on-chain group), and **Team** (closed on-chain owner-managed group). Each mode has its own membership semantics, encryption rules, and DOCK addressing conventions, all specified in companion documents (FIMP1-4).

This document (FIMP0) defines the foundational rules shared by all FIMP modes: the wire envelope, content-type registry, request-type registry, encryption layering, delivery channels, and identifier conventions.

**Version 2 is a deliberate wire break.** It replaces the three separate payload fields of version 1 (`content`, `dataBase64`, `cipher`) with a single length-prefixed `body`, which is the only private field in the envelope. See [Changes from Version 1](#changes-from-version-1).

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

For example: `FIMP1V2_P2P` refers to the P2P mode, serial number 1, version 2.

### The Need for FIMP

FUDP defines an encrypted peer-to-peer transport. FAPI defines decentralized API services (BASE/DISK/DOCK/MAP/ROAD). Neither defines what messages users send to one another, how groups are addressed, how a closed group rotates a shared key, or how a recipient who is offline retrieves messages later.

FIMP fills this gap by specifying:

- A single self-describing message envelope (`ImMessage`) that is mode-agnostic on the wire.
- Four messaging modes (P2P, Room, Square, Team) that differ only in membership and encryption rules.
- The DOCK store-and-forward conventions (recipient lists, `dataType` tags, payload framing) that allow offline delivery.
- The encryption layering: FUDP secures all transport hops; FIMP seals the message body end-to-end whenever the message crosses an intermediary server.

A conformant FIMP implementation can interoperate with the Freer Android reference client without sharing source code.

### Relationship with Other Protocols

FIMP depends on and references several Freeverse protocol series:

- **FUDP** -- Provides the encrypted, reliable transport layer for direct delivery and for FAPI requests. All FIMP traffic ultimately rides on FUDP streams. Authentication and confidentiality of every hop are handled entirely by FUDP; FIMP does not embed signatures, nonces, or transport-level credentials.
- **FAPI1V1 (Core Protocol)** -- Defines the wire format for API requests sent to FAPI servers (used to talk to DOCK, DISK, BASE).
- **FAPI13V1 (DOCK)** -- The store-and-forward service used to deliver messages to offline recipients and to fan out to group/team/room recipients. FIMP defines the `dataType` tag, recipient identifier conventions, and payload framing for items stored on DOCK.
- **FAPI12V1 (DISK)** -- Used for attachments larger than the resolved inline budget. FIMP messages carry a `HAT` reference (DID + key cipher) to a DISK object.
- **FAPI11V1 (BASE)** -- Used to look up `Square` and `Team` records on chain (membership, owner, advertised DOCK URL), and to read a DOCK service record's advertised `maxDataSize`.
- **FEIP** -- Defines the on-chain protocol-level data formats. Square and Team are FEIP entities; FIMP only references their lookup. Room is **not** an on-chain entity.
- **FVEP** -- Defines FIDs and identity concepts used throughout FIMP.
- **FTSP** -- Defines the cryptographic algorithms (AES-GCM, ECC secp256k1, ECDH, asymmetric one-way and two-way encryption) that FIMP uses.

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

1. **Message envelope** -- The `ImMessage` schema, its binary wire format, the body framing, and the mapping between local-only and on-the-wire fields.
2. **Mode contracts** -- For each of P2P, Room, Square, and Team: which envelope fields are populated, what `targetId` means, how the body is sealed, and what the body's `content` and `data` sections carry for each ContentType.
3. **DOCK conventions** -- The `dataType` tag, recipient list semantics, dock URL selection, and payload framing used to deliver messages via FAPI13.
4. **Encryption layering** -- Which modes seal the body at the IM layer, with which scheme, and on which channels.
5. **Payload sizing** -- How an implementation resolves the inline budget and when it must fall back to DISK.
6. **Lifecycle messages** -- Symkey distribution, room invitations, member updates, history sync, and other control messages.

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

Version 2 additionally forbids JSON *inside* the wire envelope for cryptographic material: the sealed body is a binary FTSP bundle, not a `CryptoDataByte` JSON string. See [Encryption Model](#encryption-model).

### 4. Message Modes

FIMP defines four messaging modes, expressed by the `type` field of `ImMessage`:

|Mode|Spec|Membership|Body sealing at the IM layer|
|---|---|---|---|
|P2P|FIMP1|Two endpoints; no shared state|Asymmetric (secp256k1), on DOCK and ROAD channels|
|Square|FIMP3|On-chain FEIP record; open membership|None -- body is plaintext|
|Team|FIMP4|On-chain FEIP record; owner-managed membership|AES-GCM under the team symkey|
|Room|FIMP2|Local; owner-managed membership; not on chain|AES-GCM under the room symkey|

The wire envelope is identical across all four modes; only the field population, recipient addressing, and sealing rules differ.

### 5. Status Codes

FIMP itself does not define status codes for message delivery. Delivery state is tracked locally by the sender using `MessageStatus` (a local-only enum: `PENDING`, `QUARANTINED`, `SENT`, `DELIVERED`, `READ`, `FAILED`, `IMPORTED`). FAPI13 status codes apply to DOCK API calls. FUDP `NOTIFY_ACK` acknowledgments apply to direct FUDP delivery.

### 6. Monetary Values

FIMP itself does not move money. DOCK fees incurred by storage and retrieval are denominated and accounted under FAPI13 / FAPI4. Note that DOCK charges by the kilobyte on ingress **and** per kilobyte per day of retention, so envelope size has a direct monetary cost; see [Payload Sizing](#payload-sizing).

### 7. JSON Encoding

When ImMessage is serialized as JSON for local storage, JSON output MUST conform to [RFC 8259](https://www.rfc-editor.org/rfc/rfc8259). Field names use camelCase. The local-storage JSON schema includes ALL `ImMessage` fields, including local-only fields and the *opened* body sections; the binary wire format excludes local-only fields. Implementations SHOULD ignore unrecognized fields for forward compatibility and MUST NOT reject a record solely because it contains unrecognized fields.

Two fields are binary in the model and have no native JSON form. Both MUST be written as **Base64 strings**, under these names:

|Model field|Local-storage JSON key|Encoding|
|---|---|---|
|`data`|`data`|Base64 of the raw bytes|
|`body`|`body`|Base64 of the sealed bundle|

This matters because a history file (FIMP1 §6.4, FIMP4 §5) is written by one client and read by the other, so it is an interoperation format in its own right. Implementations MUST NOT rely on a serializer's default handling of a byte array — Gson, for one, writes `byte[]` as a JSON array of signed numbers, which is neither compact nor what the other client expects.

The version 1 names `dataBase64` and `cipher` MUST NOT be used. The *encoding* behind both changed in version 2 (a JSON envelope became a binary bundle), so reusing the names would let a version 1 record parse and then fail inside the cipher rather than at the field.

### 8. RFC 2119 Keywords

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in FIMP documents are to be interpreted as described in [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).

### 9. Reference Implementation

The reference implementations of FIMP are the Freer Android client (with the FC-AJDK Java library) and the Freer Mac client. Where the specification and the reference implementations diverge, the divergence MUST be resolved in the specification before either implementation is changed further; the specification is the interoperation contract between the two clients.

> This rule is tightened from version 1, which made the Android implementation authoritative over the specification. That precedence produced silent drift between the two clients (for example, an endpoint named in one client that never existed on the server). Version 2 inverts it: the document is the contract, and an implementation that disagrees with it is reporting a specification bug, not defining behavior.

### 10. Protocol Versioning

The current FIMP protocol version is **2**, carried explicitly in the first two bytes of every wire envelope (see [The ImMessage Envelope §2](#the-immessage-envelope)).

Wire-incompatible changes MUST increment the version number. Minor additions (new optional fields behind new flag bits, new ContentType values appended at the end of the enum, new RequestType values appended at the end) are version-2-compatible provided that ordinal positions of existing enum values and the meanings of existing flag bits are not changed.

**Version 2 does not interoperate with version 1.** A version 2 implementation MUST reject a version 1 envelope rather than attempt to decode it, and MUST NOT emit version 1 envelopes. There is no negotiation and no dual-format mode; a peer that speaks only version 1 is unreachable.

### 11. Language Agnosticism

FIMP is a language-agnostic protocol. Any platform able to produce and consume the binary envelope and exchange data over FUDP / FAPI13 can implement a conformant FIMP client.

## The ImMessage Envelope

`ImMessage` is the single envelope used across all four modes. The same record is produced by senders, transmitted on the wire, and stored by receivers (with local-only fields populated post-receipt).

### 1. Logical Fields

|Field|Type|Wire?|Required|Description|
|---|---|---|---|---|
|`id`|string (16-char lowercase hex)|Yes (flag)|See §[Identifiers](#identifiers)|Globally unique message identifier. Generated as an unsigned 64-bit integer by the FUDP layer of the originating sender, encoded as 16-char lowercase hex (`%016x`).|
|`type`|enum `ImType`|Yes|Yes|`P2P`, `SQUARE`, `TEAM`, `ROOM`. Determines mode.|
|`senderId`|string (FID)|Yes|Yes|Authenticated FID of the originator.|
|`targetId`|string|Yes|Yes|Recipient identifier. Semantics depend on `type`: peer FID for P2P; `squareId` for Square; `teamId` for Team; `roomId` for Room.|
|`timestamp`|int64 (ms since epoch)|Yes|Yes|Sender's local time at message creation.|
|`sequence`|int64|No (local-only)|No|Tie-breaker for messages within the same millisecond. Local ordering aid.|
|`contentType`|enum `ContentType`|Yes|Yes|See [Enumerations](#enumerations).|
|`body`|byte string|Yes (flag)|Conditional|**The only private field.** Carries both the textual and the binary payload, framed as described in §3. Present whenever the message has any payload at all. When the mode seals at the IM layer, `body` holds the sealed bytes and nothing else in the envelope is encrypted. See [Encryption Model](#encryption-model).|
|`symkeyVersion`|uint32|Yes (flag)|Conditional|Identifies which version of the per-room or per-team symkey sealed `body`. REQUIRED whenever `body` is sealed symmetrically (Room, Team). MUST NOT be set otherwise. Read as **unsigned**; see [Symkey id](#symkey-id).|
|`requestType`|enum `RequestType`|Yes (flag)|Conditional|Set when `contentType` is `REQUEST`. See [Enumerations](#enumerations).|
|`requestId`|string|Yes (flag)|Conditional|For `RESPONSE` and `RECEIPT` content types, the `id` of the message being answered or acknowledged.|
|`replyToId`|string|Yes (flag)|No|`id` of the message being replied to.|
|`threadId`|string|Yes (flag)|No|Thread group identifier.|
|`content`|string|No (inside `body`)|Conditional|Textual payload. Not a wire field in version 2: it is the first section **inside** `body`. For TEXT, plain text; for HAT, JSON of a `Hat` object; for STREAM/VOICE, metadata JSON; for REQUEST, request data; for ROOM_INFO, JSON of a `RoomInfo`; for ROOM_LEAVE, the `roomId`.|
|`data`|bytes|No (inside `body`)|Conditional|Binary payload. Not a wire field in version 2: it is the second section **inside** `body`, carried as **raw bytes** (version 1's Base64 `dataBase64` string is removed). Used by STREAM, VOICE, HISTORY (carries the kCipher) and other binary content types.|
|`roadIds`, `dockId`, `deliveryMethod`, `status`, `deliveredAt`, `readAt`, `senderName`, `unread`, `pinned`, `deleted`|various|No (local-only)|No|Local delivery state, UI cache, soft-delete flag. MUST NOT cross the wire.|

The version 1 fields `dataBase64` and `cipher` no longer exist. Their roles are taken by the `data` section of `body` and by sealing `body` itself, respectively.

### 2. Compact Binary Wire Format

The compact binary wire format is the canonical encoding when ImMessage is sent over FUDP NOTIFY (binary) or stored in DOCK. Multi-byte integers are big-endian.

```
┌──────────────────────────────────────────────────────┐
│ magic           uint8       0xF1                     │
│ version         uint8       0x02                     │
│ type            uint8       (ordinal of ImType)      │
│ contentType     uint8       (ordinal of ContentType) │
│ senderIdLen     uint8                                │
│ senderId        senderIdLen bytes (UTF-8)            │
│ targetIdLen     uint8                                │
│ targetId        targetIdLen bytes (UTF-8)            │
│ timestamp       int64       (ms since epoch)         │
│ flags           uint16      (bitmap, see below)      │
│ ─── conditional fields, in flag-bit order ────       │
│ if FLAG_BODY          : len(uint32) + raw bytes      │
│ if FLAG_SYMKEY_VERSION: int32                        │
│ if FLAG_REQUEST_TYPE  : uint8 (ordinal)              │
│ if FLAG_REQUEST_ID    : len(uint16) + UTF-8 bytes    │
│ if FLAG_REPLY_TO_ID   : len(uint16) + UTF-8 bytes    │
│ if FLAG_THREAD_ID     : len(uint16) + UTF-8 bytes    │
│ if FLAG_MESSAGE_ID    : len(uint16) + UTF-8 bytes    │
└──────────────────────────────────────────────────────┘
```

#### Magic and version

The envelope opens with the two-byte sequence `0xF1 0x02`.

`0xF1` is a constant magic byte; `0x02` is the FIMP wire version. Decoders MUST verify both and MUST reject the input otherwise.

The magic byte is REQUIRED, not decorative. A version 1 envelope begins with the `ImType` ordinal, a value in the range `0x00`-`0x03`. A bare version byte of `0x02` would therefore be indistinguishable from a version 1 `TEAM` message, and a version 2 decoder would parse legacy TEAM traffic as if it were version 2 -- a silent misparse rather than a clean rejection. `0xF1` cannot occur as a version 1 first byte, so rejection is deterministic in both directions.

#### Flag bitmap

|Bit|Mask|Field|
|---|---|---|
|0|`0x0001`|body|
|1|`0x0002`|bodySealed (see below)|
|2|`0x0004`|symkeyVersion|
|3|`0x0008`|requestType|
|4|`0x0010`|requestId|
|5|`0x0020`|replyToId|
|6|`0x0040`|threadId|
|7|`0x0080`|messageId (the `id` field)|
|8-15|reserved|MUST be zero in version 2|

`FLAG_BODY_SEALED` (bit 1) consumes no bytes of its own; it states whether the bytes carried in `body` are sealed or plaintext. It MUST NOT be set unless `FLAG_BODY` is also set. Which scheme sealed the body is determined by `type` and, for the symmetric modes, by `symkeyVersion`; the sealed bundle is itself self-describing (see [Encryption Model](#encryption-model)), so no further selector is carried.

A receiver MUST NOT infer sealing from the channel a message arrived on. The flag is authoritative. Version 1 had no such signal, which forced receivers to guess from delivery context.

#### Length prefixes

- `senderId` and `targetId` use an **unsigned 8-bit** length prefix. A FID is well below 256 bytes.
- `body` uses an **unsigned 32-bit** length prefix.
- All other variable-length string fields use an **unsigned 16-bit** length prefix.

Version 1 gave the payload fields a 16-bit prefix, capping any payload at 65,535 bytes while simultaneously specifying a 900 KB inline limit -- a contradiction within the document itself. The 32-bit prefix on `body` removes it. Encoders MUST NOT truncate or wrap a length that does not fit its prefix; they MUST fail the encode.

#### Enum ordinals

Enum ordinals are stable and unchanged from version 1. Future versions MAY append new values but MUST NOT reorder existing values. See [Enumerations](#enumerations) for the ordinal positions.

#### Minimum size

A wire-format message is at least 16 bytes (`1+1+1+1+1+0+1+0+8+2` with empty IDs and no flags). Implementations MUST reject shorter inputs.

### 3. Body Framing

`body` carries both payload sections in one length-prefixed blob. **Before** sealing (and after opening), its layout is:

```
┌──────────────────────────────────────────────────────┐
│ contentLen      uint32                               │
│ content         contentLen bytes (UTF-8)             │
│ dataLen         uint32                               │
│ data            dataLen bytes (raw, NOT Base64)      │
└──────────────────────────────────────────────────────┘
```

Both sections are always present in the framing; an absent section is encoded with a length of zero. A body framing is therefore at least 8 bytes. A message with no payload at all omits `body` entirely (`FLAG_BODY` clear) rather than carrying an empty framing.

When the mode seals at the IM layer, the **entire framing above** is the plaintext input to the seal, and the resulting bundle is what the `body` length prefix measures. Implementations MUST NOT seal the sections separately.

This single-field design is the central change of version 2, and it exists for a specific reason. Version 1 had three payload fields and encrypted only one of them, so a voice message travelled with its metadata encrypted and its audio in the clear, and a file share travelled with the file's symmetric key in whichever field the implementation happened to seal. With one private field the rule is "seal the body", the partially-encrypted state is unrepresentable, and any private field added by a future version rides inside the seal automatically.

### 4. No Plain-Text Fast Path

Earlier drafts described an optional FUDP "CHAT" fast path that carried `TEXT` and `RECEIPT` payloads as raw UTF-8 strings tagged by a distinct FUDP `dataType`. The reference implementations do not use it: every FIMP message, on every channel (FUDP, ROAD, DOCK) and for every ContentType, is encoded with the compact binary wire format of §2 and carried as a raw FUDP `NOTIFY` payload. There is no dedicated `dataType` selector distinguishing a plain-text path from a binary path.

Conformant implementations MUST NOT emit a plain-text fast-path payload; `TEXT` and `RECEIPT` messages MUST use the binary envelope like every other ContentType.

## Payload Sizing

### 1. There is no protocol-wide inline constant

Version 1 specified a fixed `MAX_INLINE_DATA_SIZE` of 900 KB, justified in implementation comments by an assumed 1 MB DOCK limit. No such limit exists. FAPI13 servers enforce a per-item ceiling of **65,536 bytes by default**, and each DOCK operator MAY publish a different ceiling in the `maxDataSize` field of its on-chain service record. The 900 KB figure was fourteen times the real default, which is why inline binary failed in practice well before the documented limit.

Version 2 therefore specifies **no fixed inline constant**. The budget is resolved per destination.

### 2. Resolving the budget

Before encoding a message for DOCK delivery, a sender MUST resolve the inline budget of the DOCK it will post to:

1. Read `maxDataSize` from that DOCK's on-chain service record (BASE lookup, `entity: "service"`), interpreted as a decimal byte count.
2. If the record has no `maxDataSize`, or it does not parse, assume **65,536 bytes**.

Implementations SHOULD cache the resolved value alongside the rest of the service record and refresh it whenever they refresh the record. Implementations MUST NOT hard-code a budget in place of this lookup.

When a message is forwarded via `targetDockUrl`, the governing budget is the **smaller** of the two DOCKs' ceilings; the forwarding server enforces its own on ingress.

### 3. Enforcing the budget

The budget applies to the **fully encoded wire envelope** -- every byte of §2, including the magic, the header, the sealed `body` and its length prefix -- because that byte string is exactly what is handed to `dock.put`. It does not apply to the body alone, and it does not apply to the pre-seal framing.

A sender MUST measure the encoded envelope and MUST NOT attempt a `dock.put` that exceeds the resolved budget. Exceeding it is a client-side error, not something to discover from the server's rejection.

### 4. Falling back to DISK

When the encoded envelope would exceed the budget, the sender MUST move the binary payload out of the envelope: upload it to a DISK service and send a `HAT` message referencing it, per the relevant mode document. The `data` section then carries nothing and `content` carries the `Hat` JSON.

This fallback is a permanent part of the protocol. It applies to every binary ContentType, `VOICE` included: a voice note is inline when it fits the resolved budget and a DISK reference when it does not. Implementations MUST NOT assume any ContentType is always inline.

Senders SHOULD choose encoding parameters that keep ordinary payloads inline -- for voice, a low-bitrate speech codec rather than a music-grade one -- since an inline payload avoids a DISK round trip and a separate key-wrapping step for every recipient. But the fallback MUST exist regardless, because the budget is a property of the destination DOCK and can be smaller than any parameter the sender chose.

### 5. Direct delivery

The FUDP direct channel has its own, far larger frame limits and does not consult the DOCK budget. A sender MAY exceed the DOCK budget on a message it delivers directly. It SHOULD NOT do so for any message it might later need to retry through a DOCK, because that retry would then be impossible without re-encoding the payload to DISK.

## Enumerations

### ImType

|Ordinal|Name|Mode|
|---|---|---|
|0|`P2P`|Direct one-to-one|
|1|`SQUARE`|Open on-chain group (FIMP3)|
|2|`TEAM`|Closed on-chain owner-managed group (FIMP4)|
|3|`ROOM`|Local owner-managed group (FIMP2)|

### ContentType

Descriptions below refer to the `content` and `data` sections of `body` (§[The ImMessage Envelope §3](#3-body-framing)).

|Ordinal|Name|Description|
|---|---|---|
|0|`TEXT`|Plain text in `content`.|
|1|`HAT`|`content` is a JSON `Hat` referencing data stored on DISK.|
|2|`STREAM`|Inline binary blob. `content` holds metadata JSON (`name`, `size`, `type`); `data` holds the bytes.|
|3|`SYMKEY`|Push of a symmetric key for Team or Room. `content` holds `<entityId>:<asyOneWayCipherJson>`.|
|4|`MEMBERS`|Push of a member list for Team or Room. `content` holds a JSON list of FIDs.|
|5|`HISTORY`|Push of an encrypted message history. `content` holds a JSON `Hat` (DISK reference); `data` holds the kCipher (the file's symmetric key wrapped to the recipient's pubkey).|
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
|17|`VOICE`|Voice message. `content` holds metadata JSON (`durationMs`, `sampleRate`, `format`); `data` holds the audio bytes. Subject to [Payload Sizing](#payload-sizing) like any other binary type -- a voice note that exceeds the resolved budget becomes a `HAT`.|
|18|`ROOM_ACCEPT`|Room invitation acceptance (Room mode only). `content` is the `roomId`; sent by an invitee to the owner to confirm joining. See FIMP2.|
|19|`ROOM_DISBAND`|Room disband notification (Room mode only). `content` is the `roomId`. Sent by the room owner. See FIMP2.|
|20|`ROOM_REMOVED`|Room member-removal notification (Room mode only). `content` is the `roomId`; sent by the room owner to a removed member. See FIMP2.|

> **Note:** ordinals 18-20 were appended after the initial Draft. They are Room-mode control signals; other modes MUST NOT emit them.

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

Transport encryption protects FIMP traffic from passive observers between the endpoints of each hop. It does **not** protect it from the intermediary itself: a DOCK or ROAD server terminates the FUDP session and sees whatever the IM layer left in the clear.

### IM Layer

The IM layer seals the message body end-to-end, through any intermediary.

The rule is uniform and has one clause: **when a message crosses an intermediary server, its body is sealed.** The envelope header -- type, sender, target, timestamp, flags, message ids -- stays in the clear, because the DOCK needs to route and the receiver needs to deduplicate before it can decrypt anything.

|Mode|Channel|Body|Scheme|
|---|---|---|---|
|P2P|FUDP direct|Plaintext|None. The FUDP session is already end-to-end between exactly the two endpoints; a second layer would protect against nobody.|
|P2P|DOCK, ROAD|Sealed|Asymmetric two-way (`asy2way`): sender's private key + recipient's public key, per FTSP.|
|P2P (self-chat)|Any|Sealed|Asymmetric one-way (`asy1way`) to one's own public key. Two-way sealing is not usable when both endpoints are the same identity, because side-selection cannot resolve which of two identical keys is the counterparty.|
|Square|Any|Plaintext|None. Membership is open and messages are explicitly public. `FLAG_BODY_SEALED` and `symkeyVersion` MUST NOT be set.|
|Team|Any|Sealed|AES-GCM under the current team symkey. `symkeyVersion` REQUIRED.|
|Room|Any|Sealed|AES-GCM under the current room symkey. `symkeyVersion` REQUIRED.|

A sender that cannot seal a body it is required to seal MUST fail the send. It MUST NOT fall back to sending the body in the clear.

> Version 1 stated that P2P adds no IM-layer encryption at all, on the grounds that FUDP transport suffices. That reasoning holds for the direct channel and fails for the other two: on the DOCK path the message sits in a third party's datastore for up to a year, and on the ROAD path it passes through a relay. Version 2 corrects it.

### Sealed body structure

A sealed `body` is a **binary FTSP bundle**, not a JSON string. The bundle carries its own algorithm identifier, its type (`asy1way`, `asy2way`, or `sym`), the ephemeral or sender public key where the scheme requires one, the IV, and the ciphertext. It is self-describing: a receiver selects the opening path from the bundle's own header, with no additional envelope field and no key lookup beyond the symkey identified by `symkeyVersion`.

Version 1 placed a `CryptoDataByte` **JSON** string in a `cipher` field, which cost a JSON envelope plus Base64 expansion of every cryptographic field. The binary bundle carries the same material in roughly 52 bytes of overhead. Together with the removal of `dataBase64`, version 2 eliminates both Base64 layers from the wire.

The cryptographic primitives are unchanged from version 1. Only the container changed.

### Symkey distribution

Symmetric keys for Room and Team are distributed via P2P `SYMKEY` messages. The `content` section of a `SYMKEY` message is `"<entityId>:<asyOneWayCipherJson>"`, where `asyOneWayCipherJson` is a `CryptoDataByte` JSON of `type: "asy1way"` representing the symkey wrapped to the recipient's secp256k1 public key (per FTSP). The `symkeyVersion` field carries the version. Per-mode rules (when to push, when to rotate, who may request) are specified in FIMP2 and FIMP4.

Because a `SYMKEY` message is itself a P2P message, its body is sealed by the P2P rules above whenever it crosses a DOCK -- so the wrapped key is sealed a second time in transit.

## Delivery Channels

FIMP supports three delivery channels, in priority order:

1. **FUDP_DIRECT** -- The sender and recipient have an active FUDP connection (or can establish one). The sender invokes a FUDP `NOTIFY` (with or without ACK) carrying the compact binary wire format of `ImMessage` as the raw payload.
2. **ROAD_RELAY** -- The recipient is reachable through a ROAD server. The sender sends the binary wire format wrapped in a ROAD relay request (see ROAD specification). The body is sealed.
3. **DOCK_STORED** -- The recipient is offline or addressable only by group ID. The sender uploads the binary wire format to a DOCK server with appropriate recipients (see [DOCK Conventions](#dock-conventions)). The body is sealed. The recipient retrieves with `dock.fetch` later.

The choice of channel is local to the sender and is signaled by the local-only `deliveryMethod` field. The wire envelope is the same across channels; only its containing transport, the sealing rule, and the size budget differ.

For Team, Room, and Square, the practical channel is almost always DOCK because the message is addressed to an entity (teamId / roomId / squareId), not to a specific online peer.

## DOCK Conventions

Every DOCK item produced by FIMP follows these rules:

### dataType

The `dataType` tag of every FIMP DockItem MUST be one of:

- `"IM"` -- The body is the compact binary wire format of an `ImMessage`. This is the default for ordinary messages of any mode.
- `"SYMKEY_REQ"` -- (RESERVED) Reserved for future use as an out-of-band symkey request envelope. Implementations MUST NOT route by this tag unless explicitly specified by the relevant mode document.
- `"SYMKEY"` -- (RESERVED) Reserved as above.

Receivers MUST ignore items with unrecognized `dataType` values. Receivers MUST NOT reject a fetch response solely because it contains items with unknown `dataType`.

### recipients

The `recipients` array of a DockItem MUST contain at most 100 entries (FAPI13). FIMP uses the array as follows:

|Mode|`recipients` content|
|---|---|
|P2P|`[<recipientFid>]` (single FID)|
|Square|`[<squareId>]` (server expands to current members per FAPI13)|
|Team|`[<teamId>]` (server expands to current members)|
|Room|`[<roomId>]` (server expands to current members)|

For SYMKEY and other P2P-typed control messages used by Team or Room, `recipients` is `[<recipientFid>]`.

### Body framing

The DockItem body (transmitted as raw binary on `dock.put`, returned as Base64 in `dock.fetch`) is the **compact binary wire format** of the `ImMessage`. The DockItem body MUST NOT be a JSON encoding.

The DOCK server treats this byte string as opaque. It does not parse the envelope, inspect the ContentType, or read the body; it enforces a length limit, stores the bytes, and returns them. FIMP wire changes therefore require no FAPI13 server change.

### Size limit

Every DOCK item is subject to the destination's `maxDataSize`, as specified in [Payload Sizing](#payload-sizing). Senders MUST resolve and respect it before `dock.put`.

### Dock URL selection

|Mode|Sender posts to|
|---|---|
|P2P|Recipient's advertised DOCK URL (from `freer.home` on chain or cached `TalkPartner.home`).|
|Square|The Square's advertised DOCK URL (from the on-chain Square `home` map). Senders MAY post to their own DOCK with `targetDockUrl` set to the Square's DOCK to use FAPI13 forwarding.|
|Team|The Team's advertised DOCK URL (from the on-chain Team `home` map). Forwarding MAY be used as for Square.|
|Room|The Room owner's advertised DOCK URL (from the room's `home` field, if any), or the sender's own DOCK with the recipient's DOCK as `targetDockUrl`. Because Rooms are not on chain, the DOCK URL is propagated as part of the `RoomInfo` distributed at invite time.|

The `home` map keys for advertised DOCK URLs follow the FAPI service-id convention (e.g. `"DOCK@No1_NrC7"`). Implementations MUST tolerate missing `home` entries and fall back to the sender's own DOCK with `targetDockUrl` set.

A DOCK URL is an endpoint, not a string. Implementations MUST compare DOCK URLs by resolved host and port -- not by literal string equality -- when deciding whether a DOCK is their own, so that two spellings of the same endpoint are recognized as one.

### Dedup

Receivers MUST track the `id` of every DockItem they have already routed and skip duplicates. The dedup window MUST cover at least the longest expected DOCK retention period.

Group items (Square, Team, Room) are addressed to an entity and one stored copy serves every member. A member MUST NOT delete a group item after fetching it; deletion would remove it for members who have not yet fetched. Per-recipient P2P items MAY be deleted by their recipient.

## Identifiers

### FID

A Freecash Identity (FID), per FVEP. Used as `senderId` in every message and as `targetId` for P2P messages.

### Message id

A 16-character lowercase hexadecimal string. The underlying value is an unsigned 64-bit integer generated by the originating FUDP node (uniqueness is guaranteed per sender). The conversion is `String.format("%016x", longId)` over the **signed** 64-bit value, which prints the two's-complement bit pattern; the round trip is over the bits, not the value, so a negative underlying id is ordinary and MUST parse. Implementations MUST NOT generate hash-based IDs for FIMP messages; the FUDP-allocated ID is the canonical id.

### roomId

For Room mode: a locally-generated identifier of the form `"room_" + <24-char hex>` where the hex is derived from `SHA-256(owner FID || creation millis || secure random 64-bit)`, truncated to the first 24 hex characters. See FIMP2.

### squareId, teamId

Defined by the corresponding on-chain FEIP entity. FIMP treats them as opaque strings. See FIMP3 and FIMP4.

### Symkey id

Symkeys are addressed by `(entityId, version)` where `entityId` is the `roomId` or `teamId`. The `symkeyVersion` field of `ImMessage` carries the version.

A version is **the number of seconds since the Unix epoch at the moment the key was minted**, floored above every version the minting device already knows for that entity:

```
version = max(nowSeconds, highestKnownVersion(entityId) + 1)
```

The field is 32 bits on the wire and MUST be read as **unsigned**. A signed reading -- `(long) buf.getInt()`, `Int32(bitPattern:)` -- makes every version minted after January 2038 negative, and a negative value is not a version at all (see the last paragraph of this section), so every key minted from then on would be rejected. Unsigned, the field is good until 2106.

Two properties follow, and the mode documents (FIMP2 §7.2, FIMP4 §7.2) depend on both:

- **It orders itself.** The larger version is the later key, so "the current key" is the highest version held, with no counter to agree on and no coordination between the minter's devices.
- **It cannot be minted twice by one device**, because of the floor. A device that mints twice within one second is forced to `highestKnown + 1`, and a device whose clock steps backwards cannot mint a key that sorts below one it already holds.

Version numbers below **1,000,000,000** are pre-timestamp counters (1, 2, 3, ...) minted by earlier implementations. They remain valid and MUST be accepted. They cannot collide with a timestamp, and `max()` still selects the newest key, because every timestamp exceeds every such counter. A value of 0, or any value read as negative, is not a version.

Two devices minting in the *same second* for the same entity -- possible only for two devices holding one identity's key, partitioned from each other -- produce two distinct keys at one version. Receivers MUST tolerate this rather than assume it away: see FIMP2 §7.2 and FIMP4 §7.2, which require that a stored symkey is never overwritten and that a receiver holding two keys at one version tries each.

## Changes from Version 1

Version 2 is wire-incompatible with version 1. There is no negotiation and no dual-format support.

|#|Change|Reason|
|---|---|---|
|1|Added a two-byte `0xF1 0x02` magic-and-version prefix.|Version 1 had no version marker at all. A bare version byte would collide with the version 1 `ImType` ordinal for TEAM; the magic makes rejection deterministic in both directions.|
|2|Replaced `content`, `dataBase64` and `cipher` with a single `body`.|Three payload fields meant encryption had to remember all three, and implementations sealed only some -- shipping voice metadata encrypted with the audio in the clear, and a file-share key next to the ciphertext it unlocked. One private field makes "seal the body" the whole rule and makes the half-sealed state unrepresentable.|
|3|`body` uses a 32-bit length prefix; payloads are raw bytes.|Version 1's 16-bit prefix capped payloads at 65,535 bytes while the same document specified a 900 KB inline limit. Android silently wrapped the length, corrupting every field after it; the Mac threw. Base64 added 33% on top for nothing.|
|4|Added a `bodySealed` flag bit.|Version 1 gave receivers no way to tell a sealed body from a plaintext one except by inferring from the delivery channel.|
|5|Sealed bodies are binary FTSP bundles, not `CryptoDataByte` JSON strings.|Removes a JSON envelope and a second Base64 layer, at ~52 bytes of overhead. The bundle is self-describing, so side-selection needs no extra lookup.|
|6|P2P seals its body on the DOCK and ROAD channels.|Version 1 declared P2P unencrypted at the IM layer because FUDP secures the transport. That holds for direct delivery only; on the DOCK path the message rests in a third party's datastore, and the reference implementation had already diverged from the specification here.|
|7|Removed the fixed `MAX_INLINE_DATA_SIZE`; the budget is resolved from the destination DOCK's `maxDataSize`.|The 900 KB constant was justified by an assumed 1 MB DOCK limit that does not exist. The real FAPI13 default is 65,536 bytes and each operator may publish its own, so the value must be read, not assumed.|
|8|Stated that no ContentType is unconditionally inline.|Follows from 7: with a per-destination budget, `VOICE` and every other binary type need the DISK fallback.|
|9|The specification now takes precedence over the reference implementation (General Rule 9).|The inverted precedence of version 1 let the two clients drift apart silently.|

Enum ordinals, identifier formats, DOCK `dataType` tags, recipient conventions and the cryptographic primitives are unchanged.

### Migration

Both clients change together; there is no interoperation window.

- **Encoders** emit the magic, frame the two payload sections into `body`, seal per the mode table, and measure the encoded envelope against the resolved budget.
- **Decoders** verify the magic, reject anything else, read `body`, and open it when `bodySealed` is set.
- **Stored history** is unaffected: local storage is JSON and keeps the opened `content` and `data`. Only the wire representation changed.
- **In-flight version 1 items** already resting on a DOCK will not decode after the upgrade and are discarded on fetch.

## Protocol Document Structure

Every FIMP mode document (FIMP1 onward) follows this section layout:

```
## Contents
## Summary               - Identification table (Title, SN, Ver, Status, Author, Created, PID)
## Abstract              - 2-3 sentence description
## 1. Overview           - Mode model (membership, encryption, lifecycle in one paragraph)
## 2. Data Model         - Mode-specific entities (Room, Team, Square, TalkPartner) and their wire forms
## 3. Lifecycle          - Create / invite / join / leave / disband sequences in terms of ImMessage exchanges and on-chain operations referenced
## 4. Message Contracts  - For each ContentType used in this mode: envelope field population and body section schemas
## 5. Request/Response   - For each RequestType used: request and response wire format
## 6. DOCK Use           - dataType, recipients, body framing, size budget, dock URL selection
## 7. Encryption         - What is sealed, how, and when keys rotate
## 8. Security Considerations
## 9. Versioning
## 10. Related Protocols
```

## FIMP List

|SN|Ver|Name|Scope|
|---|---|---|---|
|0|2|FIMP|Shared core: envelope, enumerations, encryption model, sizing, DOCK conventions|
|1|2|P2P|Direct one-to-one messaging|
|2|2|Room|Local owner-managed group|
|3|2|Square|Open on-chain group|
|4|2|Team|Closed on-chain owner-managed group|
