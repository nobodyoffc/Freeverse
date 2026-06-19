# FIMP2V1_Room

|Field|Content|
|---|---|
|Title|Room|
|Type|FIMP|
|SN|2|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-05-08|
|PID||

## Contents

- [Abstract](#abstract)
- [1. Overview](#1-overview)
- [2. Data Model](#2-data-model)
  - [2.1. Room](#21-room)
  - [2.2. RoomInfo (wire form)](#22-roominfo-wire-form)
- [3. Lifecycle](#3-lifecycle)
  - [3.1. Create](#31-create)
  - [3.2. Invite](#32-invite)
  - [3.3. Join (accept)](#33-join-accept)
  - [3.4. Reject](#34-reject)
  - [3.5. Update (membership change, rename, description)](#35-update-membership-change-rename-description)
  - [3.6. Leave](#36-leave)
  - [3.7. Disband](#37-disband)
- [4. Message Contracts](#4-message-contracts)
  - [4.1. Chat messages](#41-chat-messages)
  - [4.2. ROOM_INFO](#42-room_info)
  - [4.3. ROOM_LEAVE](#43-room_leave)
  - [4.4. SYMKEY](#44-symkey)
  - [4.5. MEMBERS](#45-members)
  - [4.6. ROOM_ACCEPT](#46-room_accept)
  - [4.7. ROOM_DISBAND](#47-room_disband)
  - [4.8. ROOM_REMOVED](#48-room_removed)
- [5. Request / Response](#5-request--response)
  - [5.1. ROOM_INFO](#51-room_info)
  - [5.2. SYMKEY](#52-symkey)
  - [5.3. SYMKEY_HISTORY](#53-symkey_history)
  - [5.4. HISTORY](#54-history)
- [6. DOCK Use](#6-dock-use)
- [7. Encryption](#7-encryption)
- [8. Security Considerations](#8-security-considerations)
- [9. Versioning](#9-versioning)
- [10. Related Protocols](#10-related-protocols)

---

## Abstract

FIMP2V1 defines the **Room** mode of FIMP -- a closed, locally-defined group chat that is **not** registered on chain. A Room has an owner who controls membership, name, description, and the symmetric key used to encrypt messages. Members hold the room state locally; new members learn it through a peer-to-peer `ROOM_INFO` message at invitation time. Chat messages are end-to-end encrypted under the room symkey and delivered through DOCK using `recipients = [roomId]`.

## 1. Overview

A Room is characterized by:

- A locally-generated `roomId` of the form `room_<24-hex-chars>` (see FIMP0 §Identifiers).
- A single **owner** FID. The owner is the sole authority for adding/removing members, rotating the symkey, and renaming the room.
- A **member list** maintained by the owner and pushed to all members via `ROOM_INFO` messages.
- A **symmetric key** (AES-256) generated and rotated by the owner. The key is wrapped to each recipient's secp256k1 public key for distribution.
- All chat messages encrypted under the current symkey; only members holding the corresponding key version can decrypt.
- All chat messages addressed to the room as a whole through DOCK; the DOCK server expands `recipients = [roomId]` to the current member list.

A Room differs from a Team (FIMP4) in that Room state is **purely local**: there is no on-chain record. Membership lists and metadata are propagated entirely through FIMP messages.

## 2. Data Model

### 2.1. Room

`Room` is the local representation of a room and is **not** sent on the wire as a single object. The wire-visible projection is `RoomInfo` (§2.2). The local fields are:

|Field|Type|Wire?|Description|
|---|---|---|---|
|`id`|string|Yes (in `RoomInfo`)|`roomId`. Format: `"room_" + sha256(owner ‖ creationMillis ‖ secureRandom).hex()[0:24]`.|
|`name`|string|Yes|Display name.|
|`desc`|string|Yes|Description.|
|`owner`|FID|Yes|Owner FID.|
|`members`|list of FIDs|Yes|Current member list, including the owner.|
|`pendingMembers`|list of FIDs|No (local)|Owner-side bookkeeping: invited members that have not yet confirmed joining via `ROOM_ACCEPT` (§4.6). A subset of `members`. Never sent on the wire.|
|`home`|map<string,string>|Yes (optional)|Service map for the room (typically an advertised DOCK URL using key `"DOCK@No1_NrC7"`). MAY be null.|
|`symkeyVersion`|int32|Yes (in `RoomInfo`)|Current symkey version.|
|`symkeyCipher` (local: encrypted-for-owner; wire: encrypted-for-recipient)|string (JSON)|Yes (in `RoomInfo`)|Current symkey wrapped to a specific recipient's pubkey. The wire copy is per-recipient; the local copy stored on the owner is wrapped to the owner's own pubkey.|
|`symkeyHistory`|map<version, cipher>|No (local)|Older symkey ciphers, used to decrypt past messages.|
|`active`|boolean|No (local)|Local soft-delete flag; set to false when the user leaves or the room is disbanded.|
|`muted`, `pinned`|boolean|No (local)|UI preferences.|
|`created`, `lastActive`, `lastUpdated`|int64 ms|No (local)|Timestamps.|

### 2.2. RoomInfo (wire form)

`RoomInfo` is the JSON object placed in the `content` field of `ROOM_INFO` messages. It contains the room metadata plus the symkey wrapped to the specific recipient.

```json
{
  "id": "room_8a7f2c3b4d5e6f0a1b2c3d4e",
  "name": "Engineering",
  "desc": "Internal discussions",
  "owner": "F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW",
  "members": [
    "F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW",
    "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK",
    "FHnRyV8PHKFQ1hQRNRMmJk6LkRbSx1FKBJ"
  ],
  "symkeyVersion": 3,
  "symkey": "<JSON of CryptoDataByte type:asy1way wrapping the symkey to the recipient's pubkey>",
  "home": { "DOCK@No1_NrC7": "https://dock.example.com" }
}
```

The `symkey` field is per-recipient: each member receives a `RoomInfo` whose `symkey` is wrapped to **that** member's pubkey. All other fields are identical across recipients.

A `RoomInfo` MAY omit `home`. It MUST include all other fields.

## 3. Lifecycle

All Room lifecycle events are expressed as FIMP messages. There is no on-chain operation in Room mode.

### 3.1. Create

The owner generates a `roomId`, an initial symkey at version 1, and a `Room` record locally. No wire traffic is required at creation time. If the owner immediately invites members, see §3.2.

### 3.2. Invite

For each invitee, the owner sends a `ROOM_INFO` message:

|Field|Value|
|---|---|
|`type`|`P2P`|
|`contentType`|`ROOM_INFO`|
|`senderId`|owner FID|
|`targetId`|invitee FID|
|`content`|`RoomInfo` JSON, with `symkey` wrapped to the **invitee's** pubkey|
|`symkeyVersion`|current version|

Note that `type` is `P2P`, not `ROOM`: the invite is delivered to a single FID, not to the (not-yet-formed) room recipients. The DockItem `recipients` for an invite is `[<inviteeFid>]`, `dataType = "IM"`.

The owner SHOULD record each invitee in the local `pendingMembers` list (§2.1) at invitation time. The invitee remains pending until the owner receives a `ROOM_ACCEPT` (§4.6) from that FID, or a `ROOM_LEAVE` (rejection), or the owner removes the member. Because an invitee already holds the symkey from the moment the invite is sent, owners SHOULD review long-pending members and MAY remove them (with symkey rotation) if they never confirm.

### 3.3. Join (accept)

On receipt of a `ROOM_INFO` message addressed to a `roomId` not present locally, the receiver SHOULD treat it as an **invitation** and prompt the user. Implementations MAY auto-accept invitations from peers in the local contact list; the wire envelope makes no distinction.

To accept, the receiver:

1. Stores a local `Room` record from the `RoomInfo` payload.
2. Decrypts the `symkey` using its private key and stores the symkey at the cited `symkeyVersion`.
3. Sends a `ROOM_ACCEPT` (§4.6) to the owner.

The `ROOM_ACCEPT` lets the owner distinguish members who actually joined (and hold the symkey knowingly) from invitees who never responded. On receipt of a valid `ROOM_ACCEPT`, the owner removes the sender from `pendingMembers`. The `ROOM_ACCEPT` is a confirmation, not a join request: the invitee is already in the member list and already holds the symkey, so a member who participates without ever sending `ROOM_ACCEPT` MUST still be treated as a member (e.g., an implementation predating this message). Receivers of `ROOM_ACCEPT` MUST treat duplicates as idempotent.

### 3.4. Reject

To reject an invitation, the receiver sends a `ROOM_LEAVE` (§4.3) to the owner. No local Room record is created. The owner SHOULD remove the rejecting FID from the room and (per local policy) MAY rotate the symkey.

### 3.5. Update (membership change, rename, description)

The owner is the sole authority for membership and metadata changes. The owner:

1. Updates the local `Room` record (members, name, desc, home as applicable).
2. If a member was removed, sends a `ROOM_REMOVED` (§4.8) to the removed member, then generates a new symkey at `symkeyVersion + 1` and stores it locally.
3. Broadcasts an updated `ROOM_INFO` to every current member by sending one P2P `ROOM_INFO` per member, each with the new symkey wrapped to that member's pubkey.

The `ROOM_REMOVED` notification is what allows the removed member to deactivate its local `Room` record; without it, the removed member would keep posting to the room channel and requesting symkeys indefinitely. The removed member, on receiving a `ROOM_REMOVED` whose sender is verified to be the room owner, SHOULD set its local `active = false` and retain stored symkeys for reading history.

A member receiving an updated `ROOM_INFO` for a known room MUST replace its local `Room` record with the new fields, and MUST store the cited symkey version. Older symkey versions MUST be retained locally for decrypting past messages, unless the implementation has a key-discard policy.

If the owner removes a member but does **not** rotate the symkey, removed members can still decrypt subsequent messages. Rotation on member removal is therefore RECOMMENDED.

### 3.6. Leave

A non-owner member who wishes to leave sends a `ROOM_LEAVE` to the owner (§4.3). After sending, the member SHOULD set its local `active = false`. The owner, on receiving `ROOM_LEAVE`, removes the member and SHOULD rotate the symkey and broadcast a new `ROOM_INFO` to remaining members.

### 3.7. Disband

The owner disbands a room with an explicit `ROOM_DISBAND` (§4.7), delivered twice over:

1. One P2P `ROOM_DISBAND` to every member (excluding the owner), for members reachable now.
2. One `ROOM_DISBAND` posted through the **room channel** (`type = ROOM`, content encrypted under the current symkey, `recipients = [roomId]`), so that members who are offline at disband time learn of the closure on their next DOCK fetch.

After sending, the owner sets the local `active = false` and MUST stop serving `ROOM_INFO` and `SYMKEY` requests for the room (§5). The owner SHOULD send the notifications **before** deactivating, since the room-channel copy must be encrypted under the still-available symkey.

A member receiving a `ROOM_DISBAND` MUST verify that the sender is the room owner (per the authenticated transport identity, §8.4) before acting on it; a `ROOM_DISBAND` from any other FID MUST be discarded. On a valid disband, the member sets `active = false` and SHOULD retain stored symkeys so past messages remain readable. Duplicate disband notices (the P2P copy plus the room-channel copy) MUST be treated as idempotent.

## 4. Message Contracts

### 4.1. Chat messages

Every Room chat message is encrypted under the current symkey. The wire envelope:

|Field|Value|
|---|---|
|`type`|`ROOM`|
|`contentType`|the user content type (`TEXT`, `STREAM`, `VOICE`, `HAT`, `REACTION`, `EDIT`, `DELETE`, `FORWARD`)|
|`senderId`|sender FID|
|`targetId`|`roomId`|
|`timestamp`|ms since epoch|
|`content`|MUST be `null` (cleared)|
|`cipher`|JSON `CryptoDataByte` AES-GCM ciphertext over the original plaintext bytes of `content`, using the current symkey|
|`symkeyVersion`|the version of the symkey used to produce `cipher`|
|`dataBase64`|For `STREAM`/`VOICE`: present, but **not** encrypted in version 1. See §[7. Encryption](#7-encryption).|
|`replyToId`, `threadId`|optional|

Receivers decrypt using the symkey for the cited `symkeyVersion`. If the symkey is unknown, see §5.2.

#### Plaintext schema

The plaintext that is encrypted is the same string that would have been placed in the unencrypted `content` field. For `TEXT`, that is the text body. For `STREAM`/`VOICE`, that is the metadata JSON described in FIMP1 §4.2 / §4.3. For `HAT`, that is the `Hat` JSON. The `dataBase64` payload (when present) is **not** encrypted at the IM layer; senders that require confidential blob payloads MUST use a `HAT` body whose underlying DISK file is encrypted (e.g., with `Hat.kCipher`).

### 4.2. ROOM_INFO

Push of a complete `RoomInfo` snapshot to a single recipient. Used for invites and for membership/metadata updates.

|Field|Value|
|---|---|
|`type`|`P2P`|
|`contentType`|`ROOM_INFO`|
|`senderId`|owner FID|
|`targetId`|recipient FID|
|`content`|`RoomInfo` JSON (§2.2)|
|`symkeyVersion`|the version contained in the `RoomInfo`|

`cipher` MUST NOT be set on a `ROOM_INFO` message; the JSON itself is sent in the clear (its `symkey` field is already wrapped asymmetrically).

### 4.3. ROOM_LEAVE

Notification that a member is leaving (or rejecting an invitation).

|Field|Value|
|---|---|
|`type`|`P2P`|
|`contentType`|`ROOM_LEAVE`|
|`senderId`|departing FID|
|`targetId`|owner FID|
|`content`|the `roomId` (string)|

A `ROOM_LEAVE` is always P2P-addressed to the owner. `cipher` MUST NOT be set.

### 4.4. SYMKEY

Push of a symmetric key for a room. Used by the owner to deliver the current symkey to a new joiner without sending a full `ROOM_INFO`, or by any member who is responding to a SYMKEY request (§5.2).

|Field|Value|
|---|---|
|`type`|`P2P`|
|`contentType`|`SYMKEY`|
|`senderId`|sender FID (must be a current member)|
|`targetId`|recipient FID|
|`content`|`"<roomId>:<asyOneWayCipherJson>"`|
|`symkeyVersion`|the version of the key being delivered|
|`requestId`|present iff this is a response to a `RequestType.SYMKEY` request|

The `asyOneWayCipherJson` is the JSON serialization of a `CryptoDataByte` of `type: "asy1way"` wrapping the raw symkey bytes to the recipient's pubkey, per FTSP.

### 4.5. MEMBERS

Push of a member list update without a full `RoomInfo`. OPTIONAL. Implementations MAY use `MEMBERS` for incremental updates that do not change the symkey or other metadata.

|Field|Value|
|---|---|
|`type`|`P2P`|
|`contentType`|`MEMBERS`|
|`senderId`|owner FID|
|`targetId`|recipient FID|
|`content`|JSON `{"roomId": "...", "members": ["fid1", "fid2", ...]}`|

In version 1, `MEMBERS` is OPTIONAL; receivers MUST tolerate the absence of `MEMBERS` messages and rely on `ROOM_INFO` updates as the authoritative source.

### 4.6. ROOM_ACCEPT

Confirmation by an invitee that it has accepted a room invitation (§3.3).

|Field|Value|
|---|---|
|`type`|`P2P`|
|`contentType`|`ROOM_ACCEPT`|
|`senderId`|invitee FID|
|`targetId`|owner FID|
|`content`|the `roomId` (string)|

`cipher` MUST NOT be set. The owner, on receipt, MUST verify the sender is in the room's member list; `ROOM_ACCEPT` from a non-member MUST be discarded. A valid `ROOM_ACCEPT` removes the sender from the owner's local `pendingMembers`. Duplicates are idempotent. `ROOM_ACCEPT` carries no authorization weight: membership was granted at invite time; this message only confirms receipt.

### 4.7. ROOM_DISBAND

Notification by the owner that the room is closed (§3.7). Sent in two forms:

|Field|P2P form|Room-channel form|
|---|---|---|
|`type`|`P2P`|`ROOM`|
|`contentType`|`ROOM_DISBAND`|`ROOM_DISBAND`|
|`senderId`|owner FID|owner FID|
|`targetId`|member FID|`roomId`|
|`content`|the `roomId` (string), in the clear|MUST be `null` (cleared)|
|`cipher`|MUST NOT be set|AES-GCM ciphertext of the `roomId` under the current symkey|
|`symkeyVersion`|--|version used for `cipher`|

Receivers MUST verify the sender is the room owner (per the authenticated transport identity, §8.4); a `ROOM_DISBAND` from any other FID MUST be discarded. On a valid disband, receivers set the local `active = false`, SHOULD retain stored symkeys for history, and MUST stop posting to the room channel. Duplicates (across both forms) are idempotent.

### 4.8. ROOM_REMOVED

Notification by the owner to a member that it has been removed from the room (§3.5).

|Field|Value|
|---|---|
|`type`|`P2P`|
|`contentType`|`ROOM_REMOVED`|
|`senderId`|owner FID|
|`targetId`|removed member FID|
|`content`|the `roomId` (string)|

`cipher` MUST NOT be set. The receiver MUST verify the sender is the room owner; a `ROOM_REMOVED` from any other FID MUST be discarded. On a valid removal, the receiver sets the local `active = false` and SHOULD retain stored symkeys for history. The owner sends `ROOM_REMOVED` **before** rotating the symkey, so removal and rotation appear to the removed member in a consistent order. Note that `ROOM_REMOVED` only informs the removed member; remaining members learn of the removal through the subsequent `ROOM_INFO` broadcast.

## 5. Request / Response

### 5.1. ROOM_INFO

Request the room metadata from a current member. Used when the receiver has a `roomId` (e.g., learned from a referenced message) but no local `Room` record.

|Direction|Field|Value|
|---|---|---|
|Request|`type`|`P2P`|
|Request|`contentType`|`REQUEST`|
|Request|`requestType`|`ROOM_INFO`|
|Request|`content`|the `roomId` (string)|
|Response|message form|a `ROOM_INFO` message (§4.2), with `requestId` set to the request's id|

The responder MUST verify that the requester is a current member before responding; non-members MUST be refused (no response, or no `symkey` in the response). A responder MUST also refuse requests for rooms it has locally deactivated (left, removed, or disbanded; `active = false`).

### 5.2. SYMKEY

Request the current room symkey (or a specific version) from any current member.

|Direction|Field|Value|
|---|---|---|
|Request|`type`|`P2P`|
|Request|`contentType`|`REQUEST`|
|Request|`requestType`|`SYMKEY`|
|Request|`content`|`"<roomId>"` for the current version, or `"<roomId>:<version>"` for a specific version|
|Response|message form|a `SYMKEY` message (§4.4), with `requestId` set to the request's id|

The responder MUST verify that the requester is a current member of the room before responding. Non-members MUST be refused, as MUST requests for rooms the responder has locally deactivated (`active = false`).

### 5.3. SYMKEY_HISTORY

Request a batch of historical symkeys, used by a member who is recovering past encrypted messages after rejoining or after a key-store loss.

|Direction|Field|Value|
|---|---|---|
|Request|`type`|`P2P`|
|Request|`contentType`|`REQUEST`|
|Request|`requestType`|`SYMKEY_HISTORY`|
|Request|`content`|`"<roomId>:<v1>,<v2>,..."` (comma-separated list of versions)|
|Response|message form|one or more `SYMKEY` messages (§4.4), each carrying one version, all with the same `requestId`|

### 5.4. HISTORY

Request the conversation history of the room, encrypted as a single file on DISK.

|Direction|Field|Value|
|---|---|---|
|Request|`type`|`P2P` (addressed to a current member, typically the owner)|
|Request|`contentType`|`REQUEST`|
|Request|`requestType`|`HISTORY`|
|Request|`content`|JSON `{"entityId": "<roomId>", "since": <ms?>, "before": <ms?>}`|
|Response|`contentType`|`HISTORY`|
|Response|`content`|JSON of the `Hat` referencing the encrypted history file on DISK|
|Response|`dataBase64`|Base64 of the `kCipher` (the file's symmetric key wrapped to the requester's pubkey)|

The DISK file MUST be a JSON array of `ImMessage` records (in the same form used for local storage), encrypted with a fresh symmetric key. The responder MUST verify that the requester is a current member before responding.

## 6. DOCK Use

|Operation|recipients|dataType|Body|Target dock|
|---|---|---|---|---|
|Chat message (§4.1)|`[<roomId>]`|`"IM"`|compact binary `ImMessage`|the room's advertised DOCK (from `RoomInfo.home`), or sender's own DOCK with `targetDockUrl` = the room's DOCK|
|`ROOM_INFO`, `ROOM_LEAVE`, `SYMKEY`, `MEMBERS`, `ROOM_ACCEPT`, `ROOM_REMOVED`, and the P2P form of `ROOM_DISBAND` (§4.2-4.8)|`[<recipientFid>]`|`"IM"`|compact binary `ImMessage`|recipient's DOCK (per FIMP1)|
|`ROOM_DISBAND`, room-channel form (§4.7)|`[<roomId>]`|`"IM"`|compact binary `ImMessage`|the room's advertised DOCK, same as a chat message|
|REQUEST/RESPONSE (§5)|`[<recipientFid>]`|`"IM"`|compact binary `ImMessage`|recipient's DOCK (per FIMP1)|

Receivers fetch room messages with `dock.fetch` using `recipientIds = [<roomId>]`. Receivers also separately fetch their own P2P inbox (`recipientIds = [<own FID>]`) to receive the control messages above.

The owner of a room SHOULD configure the room's `home` map to advertise a DOCK URL the owner controls, so that all members can read from the same DOCK and the FAPI13 group expansion mechanism applies. If `home` is absent, senders fall back to their own DOCK with `targetDockUrl` set per recipient -- which, for chat messages, requires a separate post per member and is not equivalent to a true broadcast.

A receiver MUST de-duplicate incoming DockItems by their dock id (FAPI13 §2 and FIMP0 §DOCK Conventions).

## 7. Encryption

### 7.1. Symkey

The room symkey is a 256-bit AES key generated by the owner via a CSPRNG. The owner stores it locally wrapped to its own pubkey. For each member, the owner produces a one-way asymmetric ciphertext (`CryptoDataByte` of `type: "asy1way"`) wrapping the symkey to that member's secp256k1 pubkey, per FTSP.

### 7.2. Versioning

Symkeys are versioned by a monotonically increasing 32-bit integer. Version 1 is the initial key. The owner MUST increment the version when:

- A member is removed from the room.
- The owner judges the current key compromised.

The owner MAY increment the version at other times (e.g., periodic rotation).

### 7.3. Message encryption

The plaintext for AES-GCM encryption is the UTF-8 byte sequence that would have been placed in the `content` field of an unencrypted message. The IV and other AES-GCM parameters are encoded in the `CryptoDataByte` JSON, per FTSP. The output JSON is placed in `cipher`, and `content` MUST be set to `null`.

The `dataBase64` payload (when present, e.g., for `STREAM` or `VOICE`) is NOT encrypted at the IM layer in version 1. Senders requiring confidential bulk payloads MUST use `HAT` bodies referencing DISK files encrypted under a `Hat.kCipher` wrapped to each recipient's pubkey -- which is impractical for groups, so in practice room confidential attachments SHOULD be encrypted under the **room** symkey: the file is uploaded to DISK with a `Hat.key` set to the symkey identifier `(roomId, version)` and the recipients decrypt by retrieving the symkey locally. Implementations MAY define this convention freely as long as they signal the key reference inside the encrypted `content`.

### 7.4. Recovery from missing symkey

When a receiver decodes a chat message whose `symkeyVersion` is not in its local store, the receiver SHOULD:

1. Request the missing symkey via §5.2 from the owner or any current member.
2. Pending recovery, surface the message to the user as undecryptable (e.g., "[Encrypted -- missing key v3]").

A receiver SHOULD NOT request the same missing key more often than once per minute per `(roomId, version)` to avoid request storms.

## 8. Security Considerations

### 8.1. Authentication of room state

Because Room is not on chain, there is no globally-authoritative source for the member list. A member trusts the owner's `ROOM_INFO`. A malicious owner can therefore lie about membership; a member who does not trust the owner MUST NOT join the room. This is by design: Room is a **convenience** mode for ad-hoc groups where the owner is socially trusted. For groups that require third-party-verifiable membership, use Team (FIMP4).

### 8.2. Forward secrecy

FIMP version 1 does NOT provide forward secrecy for room messages. A future compromise of a current member's prikey allows decryption of every past message that member received (because past `ROOM_INFO` payloads include past symkeys wrapped to that member's pubkey, and past messages are stored on DOCK or in DISK). Implementations that require forward secrecy MUST limit the on-server retention of `ROOM_INFO` (which DOCK already does via `maxDays`) and SHOULD discard old symkeys locally once no longer needed.

### 8.3. Symkey leakage on member removal

A member who is removed without symkey rotation can still decrypt subsequent messages until rotation occurs. Owners MUST rotate the symkey on member removal in any Room where post-removal confidentiality is required.

### 8.4. Sender impersonation

Within a room, any member who holds the symkey can produce a message with `cipher` valid under that key. Receivers MUST NOT authenticate the **sender** of a chat message based on decryption alone. The sender's identity is authenticated by the FUDP layer (when delivered direct) or by `DockItem.sender` (when delivered via DOCK). Receivers MUST treat that authenticated identity as the authoritative `senderId` and discard messages whose `ImMessage.senderId` field disagrees.

### 8.5. Replay

DOCK guarantees uniqueness of `DockItem.id`. Receivers MUST de-duplicate by dock id. The `ImMessage.id` field, while typically unique per sender, is not a sufficient replay guard across senders.

### 8.6. Authorization of control messages

Room control messages carry destructive or state-changing semantics and MUST be authorized against the authenticated transport identity (§8.4), not the `senderId` field alone:

- `ROOM_DISBAND` and `ROOM_REMOVED` MUST be accepted only from the room **owner**. Without this check, any member (or any peer who learns the `roomId`) could close another user's room view.
- `ROOM_ACCEPT` and `ROOM_LEAVE` MUST be accepted only from a FID that is currently in the member list.
- All four are idempotent: replaying a control message (e.g., a re-fetched DockItem) MUST NOT produce additional effects.

Because `ROOM_DISBAND`/`ROOM_REMOVED` only deactivate the receiver's local record (keys and history are retained), a successfully forged or replayed notice is a denial-of-room, not a confidentiality break; the owner check above is nevertheless REQUIRED.

## 9. Versioning

This document defines version 1 of the Room mode (FIMP2V1). The control messages `ROOM_ACCEPT`, `ROOM_DISBAND`, and `ROOM_REMOVED` (§4.6-4.8) were added while this document was in Draft status; they are appended ContentType values and are wire-compatible with earlier implementations, which simply do not emit or act on them (see §3.3 on tolerating members that never send `ROOM_ACCEPT`).

Future versions MAY:

- Append new ContentType or RequestType values.
- Add per-version forward-secret variants of `SYMKEY` distribution.
- Define `MEMBERS` encryption.

Wire-incompatible changes require a new version number.

## 10. Related Protocols

- **FIMP0V1** -- Foundational rules and the `ImMessage` envelope.
- **FIMP1V1** -- P2P mode (used as the carrier for ROOM_INFO, ROOM_LEAVE, SYMKEY, MEMBERS, REQUEST/RESPONSE).
- **FIMP4V1** -- Team mode (analogous closed-group semantics, but with on-chain membership).
- **FAPI13V1** -- DOCK store-and-forward; in particular the group-expansion behavior for `recipients = [<roomId>]`.
- **FAPI12V1** -- DISK service for HISTORY files.
- **FTSP** -- Cryptographic primitives (`CryptoDataByte` of types `"sym"` and `"asy1way"`).
