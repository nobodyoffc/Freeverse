# FIMP2V3_Room

|Field|Content|
|---|---|
|Title|Room|
|Type|FIMP|
|SN|2|
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

FIMP2V3 defines the **Room** mode of FIMP -- a closed, locally-defined group chat that is **not** registered on chain. A Room has an owner who controls membership, name, description, and the symmetric key used to encrypt messages. Members hold the room state locally; new members learn it through a peer-to-peer `ROOM_INFO` message at invitation time. Chat messages are end-to-end encrypted under the room symkey and delivered through DOCK using `recipients = [roomId]`.

## 1. Overview

A Room is characterized by:

- A locally-generated `roomId` of the form `room_<24-hex-chars>` (see FIMP0 §Identifiers).
- A single **owner** FID. The owner is the sole authority for adding/removing members, rotating the symkey, and renaming the room.
- A **member list** maintained by the owner and pushed to all members via `ROOM_INFO` messages.
- A **symmetric key** (AES-256) generated and rotated by the owner. The key is wrapped to each recipient's secp256k1 public key for distribution.
- All chat messages encrypted under the current symkey; only members holding the corresponding key version can decrypt.
- All chat messages addressed to the room as a whole through DOCK; stored once under the `roomId` and returned to anyone who fetches that id. The DOCK does not check membership; the symkey keeps the content private and the receiver enforces who may speak (§8.4).

Every message in this mode, including every control message, carries the FIMP0V3 signature trailer: the author's pubkey and a Schnorr signature over the whole envelope. Before acting on a message a receiver verifies the signature, checks that the message is addressed to it, and drops a `(senderId, id)` it has taken in before (FIMP0V3 §3.5). Throughout this document, **the verified sender** means the `senderId` of a message that has passed those checks.

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

A member receiving a `ROOM_DISBAND` MUST verify that the sender is the room owner (the verified sender, §8.4) before acting on it; a `ROOM_DISBAND` from any other FID MUST be discarded. On a valid disband, the member sets `active = false` and SHOULD retain stored symkeys so past messages remain readable. Duplicate disband notices (the P2P copy plus the room-channel copy) MUST be treated as idempotent.

## 4. Message Contracts

Throughout this section, `content` and `data` name the two **sections of the body** (FIMP0 §Body Framing), not envelope fields. Where the body is sealed, they describe the plaintext *inside* the seal.

### 4.1. Chat messages

Every Room chat message is encrypted under the current symkey. The wire envelope:

|Field|Value|
|---|---|
|`type`|`ROOM`|
|`contentType`|the user content type (`TEXT`, `STREAM`, `VOICE`, `HAT`, `REACTION`, `EDIT`, `DELETE`, `FORWARD`)|
|`senderId`|sender FID|
|`targetId`|`roomId`|
|`timestamp`|ms since epoch|
|`body`|the sealed body: AES-GCM ciphertext, as a binary FTSP bundle, over the **whole** body framing (both the `content` and `data` sections), using the current symkey|
|`FLAG_BODY_SEALED`|set|
|`symkeyVersion`|the version of the symkey that sealed `body`|
|`replyToId`, `threadId`|optional|

Receivers decrypt using the symkey for the cited `symkeyVersion`. If the symkey is unknown, see §5.2.

#### Plaintext schema

The plaintext input to the seal is the complete body framing of FIMP0 §Body Framing: `contentLen + content + dataLen + data`. Section 1 (`content`) holds what would have been the plaintext `content` -- for `TEXT`, the text body; for `STREAM`/`VOICE`, the metadata JSON described in FIMP1 §5.2 / §5.3; for `HAT`, the `Hat` JSON. Section 2 (`data`) holds the raw binary payload, if any.

Both sections are inside the seal. There is no partially-encrypted form: a `VOICE` message's audio is sealed together with its metadata, and a `HAT`'s key material is sealed together with its DISK locations.

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

For a `ROOM_INFO` message, the body is NOT sealed under the room symkey and `symkeyVersion` MUST NOT be set. Being a P2P-addressed message, its body follows the P2P sealing rules of FIMP1V3 §7 -- sealed to the recipient's pubkey when it crosses a DOCK or ROAD, plaintext on a direct FUDP session. Note that an invitee does not yet hold the room symkey, which is precisely what this message delivers, so symkey sealing would be circular; the `symkey` field inside the JSON is already wrapped asymmetrically to the invitee.

### 4.3. ROOM_LEAVE

Notification that a member is leaving (or rejecting an invitation).

|Field|Value|
|---|---|
|`type`|`P2P`|
|`contentType`|`ROOM_LEAVE`|
|`senderId`|departing FID|
|`targetId`|owner FID|
|`content`|the `roomId` (string)|

A `ROOM_LEAVE` is always P2P-addressed to the owner. As such, the body is NOT sealed under the room symkey and `symkeyVersion` MUST NOT be set. Being a P2P-addressed message, its body follows the P2P sealing rules of FIMP1V3 §7 -- sealed to the recipient's pubkey when it crosses a DOCK or ROAD, plaintext on a direct FUDP session.

### 4.4. SYMKEY

Push of a symmetric key for a room. Used by the owner to deliver the current symkey to a new joiner without sending a full `ROOM_INFO`, or by any member who is responding to a SYMKEY request (§5.2).

|Field|Value|
|---|---|
|`type`|`P2P`|
|`contentType`|`SYMKEY`|
|`senderId`|sender FID (must be a current member; checked against the verified sender)|
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

As a P2P-addressed message, the body is NOT sealed under the room symkey and `symkeyVersion` MUST NOT be set. Being a P2P-addressed message, its body follows the P2P sealing rules of FIMP1V3 §7 -- sealed to the recipient's pubkey when it crosses a DOCK or ROAD, plaintext on a direct FUDP session. The owner, on receipt, MUST verify the sender is in the room's member list; `ROOM_ACCEPT` from a non-member MUST be discarded. A valid `ROOM_ACCEPT` removes the sender from the owner's local `pendingMembers`. Duplicates are idempotent. `ROOM_ACCEPT` carries no authorization weight: membership was granted at invite time; this message only confirms receipt.

### 4.7. ROOM_DISBAND

Notification by the owner that the room is closed (§3.7). Sent in two forms:

|Field|P2P form|Room-channel form|
|---|---|---|
|`type`|`P2P`|`ROOM`|
|`contentType`|`ROOM_DISBAND`|`ROOM_DISBAND`|
|`senderId`|owner FID|owner FID|
|`targetId`|member FID|`roomId`|
|`content`|the `roomId` (string)|the `roomId` (string)|
|`FLAG_BODY_SEALED`|per the P2P channel rules of FIMP1V3 §7|set|
|`symkeyVersion`|MUST NOT be set|version of the symkey that sealed `body`|

Receivers MUST verify the sender is the room owner (the verified sender, §8.4); a `ROOM_DISBAND` from any other FID MUST be discarded. On a valid disband, receivers set the local `active = false`, SHOULD retain stored symkeys for history, and MUST stop posting to the room channel. Duplicates (across both forms) are idempotent.

### 4.8. ROOM_REMOVED

Notification by the owner to a member that it has been removed from the room (§3.5).

|Field|Value|
|---|---|
|`type`|`P2P`|
|`contentType`|`ROOM_REMOVED`|
|`senderId`|owner FID|
|`targetId`|removed member FID|
|`content`|the `roomId` (string)|

As a P2P-addressed message, the body is NOT sealed under the room symkey and `symkeyVersion` MUST NOT be set. Being a P2P-addressed message, its body follows the P2P sealing rules of FIMP1V3 §7 -- sealed to the recipient's pubkey when it crosses a DOCK or ROAD, plaintext on a direct FUDP session. The receiver MUST verify the sender is the room owner; a `ROOM_REMOVED` from any other FID MUST be discarded. On a valid removal, the receiver sets the local `active = false` and SHOULD retain stored symkeys for history. The owner sends `ROOM_REMOVED` **before** rotating the symkey, so removal and rotation appear to the removed member in a consistent order. Note that `ROOM_REMOVED` only informs the removed member; remaining members learn of the removal through the subsequent `ROOM_INFO` broadcast.

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

A request MUST NOT name more than **64** versions, and a responder MUST answer at most the first 64 in ascending version order, ignoring the rest. A member needing more than 64 versions sends more than one request. See §8.8 for why the bound exists; the responder's half of it is what makes it a defence, since a requester that disregards the limit is exactly the case it is there for.

Entries the responder cannot read as a positive integer are **skipped, not grounds for refusing the request**: a list of eight versions with one malformed entry in it is still seven keys the asker needs. A request in which no entry is readable, or which names no version at all, has nothing to answer and is ignored.

Versions SHOULD be de-duplicated and sent in ascending order, so that the same set of missing versions always produces the same request and a responder's rate limit (§7.4) can recognise a repeat.

The responder answers with the versions it holds and **omits those it does not**, rather than refusing the batch: the same rule as §5.2, for the same reason. The asker learns what arrived by which messages open.

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
|Response|`data`|the `kCipher` (the file's symmetric key wrapped to the requester's pubkey), raw in the body's `data` section|

The DISK file MUST be a JSON array of `ImMessage` records (in the same form used for local storage), encrypted with a fresh symmetric key. The responder MUST verify that the requester is a current member before responding.

## 6. DOCK Use

|Operation|recipients|dataType|Body|Target dock|
|---|---|---|---|---|
|Chat message (§4.1)|`[<roomId>]`|`"IM"`|compact binary `ImMessage`|the room's advertised DOCK (from `RoomInfo.home`), or sender's own DOCK with `targetDockUrl` = the room's DOCK|
|`ROOM_INFO`, `ROOM_LEAVE`, `SYMKEY`, `MEMBERS`, `ROOM_ACCEPT`, `ROOM_REMOVED`, and the P2P form of `ROOM_DISBAND` (§4.2-4.8)|`[<recipientFid>]`|`"IM"`|compact binary `ImMessage`|recipient's DOCK (per FIMP1)|
|`ROOM_DISBAND`, room-channel form (§4.7)|`[<roomId>]`|`"IM"`|compact binary `ImMessage`|the room's advertised DOCK, same as a chat message|
|REQUEST/RESPONSE (§5)|`[<recipientFid>]`|`"IM"`|compact binary `ImMessage`|recipient's DOCK (per FIMP1)|

Receivers fetch room messages with `dock.fetch` using `recipientIds = [<roomId>]`. The DOCK stores an item under the ids it is addressed to and returns it to whoever asks for one of them; it does not check membership, so every membership rule in this document is enforced by the receiver. Receivers also separately fetch their own P2P inbox (`recipientIds = [<own FID>]`) to receive the control messages above.

The owner of a room SHOULD configure the room's `home` map to advertise a DOCK URL the owner controls, so that all members read one stored copy of each message from the same DOCK. If `home` is absent, senders fall back to their own DOCK with `targetDockUrl` set per recipient -- which, for chat messages, requires a separate post per member and is not equivalent to a true broadcast.

A receiver MUST de-duplicate incoming messages by `(senderId, id)` and discard any whose `targetId` is not the room it fetched for (FIMP0V3 §3.5). De-duplicating by dock id alone is not enough: a replay is stored under a new one.

## 7. Encryption

### 7.1. Symkey

The room symkey is a 256-bit AES key generated by the owner via a CSPRNG. The owner stores it locally wrapped to its own pubkey. For each member, the owner produces a one-way asymmetric ciphertext (`CryptoDataByte` of `type: "asy1way"`) wrapping the symkey to that member's secp256k1 pubkey, per FTSP.

### 7.2. Versioning

Symkeys are versioned by a monotonically increasing 32-bit integer. Version 1 is the initial key. The owner MUST increment the version when:

- A member is removed from the room.
- The owner judges the current key compromised.

The owner MAY increment the version at other times (e.g., periodic rotation).

### 7.3. Message encryption

The plaintext for AES-GCM encryption is the entire body framing (FIMP0 §Body Framing), covering both the textual and the binary payload. The IV and other AES-GCM parameters are carried in the binary FTSP bundle that becomes `body`; `FLAG_BODY_SEALED` is set and `symkeyVersion` names the key.

Because the binary payload is inside the seal, a room attachment small enough to travel inline needs no separate key-wrapping scheme. For an attachment that exceeds the destination's size budget (FIMP0 §Payload Sizing) and must go to DISK, the file SHOULD be encrypted under the **room** symkey: upload it to DISK with `Hat.key` set to the symkey identifier `(roomId, version)`, and members decrypt by retrieving that symkey locally. The `Hat` itself travels inside the sealed body, so the key reference is never exposed to the DOCK.

> **Change from version 1.** FIMP2V1 encrypted `content` and left `dataBase64` in the clear, so a room voice message travelled with its metadata encrypted and its audio readable by the DOCK operator. Version 2 seals the whole body, which removes the failure mode rather than documenting a workaround for it.

### 7.4. Recovery from missing symkey

When a receiver decodes a chat message whose `symkeyVersion` is not in its local store, the receiver SHOULD:

1. Request the missing symkey via §5.2 from the owner or any current member.
2. Pending recovery, surface the message to the user as undecryptable (e.g., "[Encrypted -- missing key v3]").
3. On storing a symkey for the room -- from any source: a response to the request above, a `ROOM_INFO` carrying the key (§4.2), a proactive push from the owner (§4.4), or a `SYMKEY_HISTORY` batch (§5.3) -- open the messages already stored under the version it supplies, and replace the undecryptable rendering with the recovered content.

Step 3 completes the recovery and is REQUIRED for step 1 to have any effect a user can see. A receiver that performs steps 1 and 2 alone stores the key and changes nothing on screen, so a recovery that succeeded is indistinguishable from one that failed -- and asking again cannot help, because the key is already held. The sealed body retained under §7.2 is what makes step 3 possible without a second delivery of the same key, and a receiver MUST NOT require one.

Step 3 MUST be idempotent per `(roomId, version)`. The same version may arrive more than once -- two members answering one request, or a `ROOM_INFO` racing a response -- and the repeat MUST NOT duplicate, reorder or re-count anything. In particular, opening a stored backlog MUST NOT raise the conversation's unread count: those messages were counted when they were stored, and their unread state is unchanged by becoming readable.

A receiver SHOULD NOT request the same missing key more often than once per minute per `(roomId, version)` to avoid request storms.

## 8. Security Considerations

### 8.1. Authentication of room state

Because Room is not on chain, there is no globally-authoritative source for the member list. A member trusts the owner's `ROOM_INFO`. A malicious owner can therefore lie about membership; a member who does not trust the owner MUST NOT join the room. This is by design: Room is a **convenience** mode for ad-hoc groups where the owner is socially trusted. For groups that require third-party-verifiable membership, use Team (FIMP4).

### 8.2. Forward secrecy

FIMP version 1 does NOT provide forward secrecy for room messages. A future compromise of a current member's prikey allows decryption of every past message that member received (because past `ROOM_INFO` payloads include past symkeys wrapped to that member's pubkey, and past messages are stored on DOCK or in DISK). Implementations that require forward secrecy MUST limit the on-server retention of `ROOM_INFO` (which DOCK already does via `maxDays`) and SHOULD discard old symkeys locally once no longer needed.

### 8.3. Symkey leakage on member removal

A member who is removed without symkey rotation can still decrypt subsequent messages until rotation occurs. Owners MUST rotate the symkey on member removal in any Room where post-removal confidentiality is required.

### 8.4. Sender impersonation

Within a room, any member who holds the symkey can produce a message whose `body` seals validly under that key, so decryption proves only that the author held the key. The author is the FID whose key signed the envelope (FIMP0V3 §3). Receivers MUST verify the signature before opening the body, MUST discard a message whose signature does not verify or was not made by `senderId`'s key, and MUST NOT use the FUDP peer or `DockItem.sender` as the author. A receiver SHOULD also discard a room chat message whose verified sender is not in its member list for the room.

### 8.5. Replay

A signed message verifies every time it is presented, and anyone who has seen it can store the same bytes again under a new `DockItem.id`. Receivers MUST remember `(senderId, id)` for every message taken in, for at least the DOCK retention period, and discard a second arrival before acting on it. `ImMessage.id` alone is not enough: ids are unique per sender, not across senders.

### 8.6. Authorization of control messages

Room control messages carry destructive or state-changing semantics and MUST be authorized against the verified sender (§8.4):

- `ROOM_DISBAND` and `ROOM_REMOVED` MUST be accepted only from the room **owner**. Without this check, any member (or any peer who learns the `roomId`) could close another user's room view.
- `ROOM_ACCEPT` and `ROOM_LEAVE` MUST be accepted only from a FID that is currently in the member list.
- `ROOM_INFO` that changes membership, and a proactive `SYMKEY` push, MUST be accepted only from the owner; a `SYMKEY` from another member is valid only as the response to a request this device made.
- All are idempotent: replaying a control message MUST NOT produce additional effects, and the `(senderId, id)` check of §8.5 discards a replay before it is acted on.

Because `ROOM_DISBAND`/`ROOM_REMOVED` only deactivate the receiver's local record (keys and history are retained), a notice that got past these checks would be a denial-of-room, not a confidentiality break; the owner check above is nevertheless REQUIRED.

### 8.7. Record of symkey distribution

A device answers a `SYMKEY` request (§5.2) **automatically, without asking its user**, and the key handed over opens every message under its version, including messages sent long before the requester was invited.

A Room is **not** registered on chain, which makes this weightier here than for a Team. The membership a responder checks the requester against is its own local copy, assembled from `ROOM_INFO` messages that may be stale, may have been missed, and have no authoritative source to be reconciled against (§8.1). There is therefore no record anywhere -- local or public -- of who was a member of a room at a given moment, and so no way to reconstruct after the fact who was legitimately given a key and who was not.

An implementation SHOULD therefore keep a local, durable record of every symkey it distributes and every symkey it accepts, holding at least:

- the room and the version,
- the counterparty FID,
- the direction (sent or received),
- whether it was solicited (a response to a request) or unsolicited (a `ROOM_INFO` or an owner's push),
- the time.

Three questions depend on this record, and for a Room nothing else can answer them:

- **Leak radius.** §8.3 requires rotation on member removal where post-removal confidentiality matters. Knowing which versions a departing member actually received -- not which they were entitled to -- is what says whether rotation is sufficient or whether earlier versions are already compromised.
- **Anomaly.** A device that answered a request its user did not expect leaves no other trace that the key left the device. Since room membership is local, an attacker who can write to the member list can also authorize themselves; the distribution record is written at the moment of the answer and is the only artefact that outlives such an edit.
- **Provenance.** §8.6 permits a `SYMKEY` from a non-owner only as a response to a request this device made. Recording which key came from whom is what lets that rule be audited rather than merely enforced at the moment of arrival.

The record is local and MUST NOT be transmitted. It is a map of who can read what, and for a Room it is strictly more informative than any other artefact about the room's real membership. It MUST NOT be included in a `HISTORY` response (§5.4) or in a `ROOM_INFO` (§4.2), and SHOULD be excluded from conversation exports and backups that are shared with a peer.

An implementation SHOULD retain the record for at least as long as it retains the symkeys themselves, and SHOULD NOT discard it under a general log-rotation or ring-buffer policy. A distribution record is consulted after a suspected compromise, so the entry that matters is characteristically the oldest one, and a bounded diagnostic log is the form most likely to have dropped it.

### 8.8. Request amplification

A `SYMKEY_HISTORY` request (§5.3) is answered with one `SYMKEY` message per version named, and **the responder bears the whole cost of answering**: one asymmetric seal per version, and one DOCK item per version put at the requester's DOCK, whose ingress and storage the putting party pays for. The request that causes all of it is a single short message.

That asymmetry is an amplifier. Without the bound in §5.3, one member could name ten thousand versions and have another member's device perform ten thousand seals and pay to store ten thousand items -- using nothing but the protocol as specified. Nothing else in this document limits it: the membership check passes, the signature verifies, the replay check (§8.5) sees one message because there *is* one message, and the rate limit in §7.4 governs how often a request may be repeated rather than how much a single one may ask for.

For a Room the exposure is wider than for a Team, for the reason §8.7 gives: membership is the responder's own local copy with no authoritative source behind it, so "a current member" is a weaker statement here, and an entry that should have been removed from that list may still be spending another member's DOCK allowance.

The bound is therefore a limit on **one request**, enforced by the responder and not merely by the requester's good behaviour: a requester that respects it needs no limit imposed, and a requester that does not is the case the limit exists for. 64 costs nothing legitimate -- it is far more rotations than a real room accumulates, and a member who genuinely needs more sends a second request, which the rate limit then paces.

A responder MAY apply a stricter limit, and SHOULD count the versions it has actually answered rather than the versions named, so that a request padded with versions the responder does not hold cannot buy a larger answer than an honest one.

## 9. Versioning

This document defines version 3 of the Room mode (FIMP2V3), which accompanies the version 3 envelope of FIMP0V3 and does not interoperate with version 2 or version 1.

Changes from FIMP2V2:

1. Every message is signed by its author (FIMP0V3 §3). Sender checks -- owner-only control messages, member-only `ROOM_ACCEPT`/`ROOM_LEAVE`/`SYMKEY` -- are made against the verified sender instead of the FUDP peer or `DockItem.sender` (§8.4, §8.6).
2. Receivers de-duplicate on `(senderId, id)` rather than dock id, and discard messages not addressed to the room they fetched for (§6, §8.5).
3. The DOCK is stated not to check membership; the receiver enforces it (§6).

Changes from FIMP2V1, retained from version 2:

1. The sealed unit is the whole body, so a room `STREAM`/`VOICE` payload is now encrypted along with its metadata (§4.1, §7.3). Version 1 sealed `content` only and left `dataBase64` readable by the DOCK.
2. `content` and `dataBase64` are no longer envelope fields; they are the two sections of the single `body` field, and binary data is raw rather than Base64.
3. The sealed body is a binary FTSP bundle rather than a `CryptoDataByte` JSON string in `cipher`.
4. Inline payload size is governed by the destination's resolved budget (FIMP0V2 §Payload Sizing) rather than a fixed constant.

From version 1, retained: the control messages `ROOM_ACCEPT`, `ROOM_DISBAND`, and `ROOM_REMOVED` (§4.6-4.8) were added while this document was in Draft status; they are appended ContentType values and are wire-compatible with earlier implementations, which simply do not emit or act on them (see §3.3 on tolerating members that never send `ROOM_ACCEPT`).

Future versions MAY:

- Append new ContentType or RequestType values.
- Add per-version forward-secret variants of `SYMKEY` distribution.
- Define `MEMBERS` encryption.

Wire-incompatible changes require a new version number.

## 10. Related Protocols

- **FIMP0V3** -- Foundational rules, the `ImMessage` envelope, body framing, payload sizing, and the envelope signature and receive checks (`FIMP0V3_Signing_Proposal`).
- **FIMP1V3** -- P2P mode (used as the carrier for ROOM_INFO, ROOM_LEAVE, SYMKEY, MEMBERS, REQUEST/RESPONSE, and the source of their sealing rules).
- **FIMP4V3** -- Team mode (analogous closed-group semantics, but with on-chain membership).
- **FAPI13V1** -- DOCK store-and-forward; in particular storing one item under `recipients = [<roomId>]` for every member to fetch.
- **FAPI12V1** -- DISK service for HISTORY files.
- **FTSP** -- Cryptographic primitives (`CryptoDataByte` of types `"sym"` and `"asy1way"`).
