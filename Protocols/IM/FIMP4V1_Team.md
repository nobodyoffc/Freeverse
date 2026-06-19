# FIMP4V1_Team

|Field|Content|
|---|---|
|Title|Team|
|Type|FIMP|
|SN|4|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-05-08|
|PID||

## Contents

- [Abstract](#abstract)
- [1. Overview](#1-overview)
- [2. Data Model](#2-data-model)
- [3. Lifecycle](#3-lifecycle)
  - [3.1. Create](#31-create)
  - [3.2. Add member](#32-add-member)
  - [3.3. Remove member](#33-remove-member)
  - [3.4. Update](#34-update)
  - [3.5. Disband](#35-disband)
  - [3.6. Membership sync](#36-membership-sync)
- [4. Message Contracts](#4-message-contracts)
  - [4.1. Chat messages](#41-chat-messages)
  - [4.2. SYMKEY (proactive push)](#42-symkey-proactive-push)
  - [4.3. MEMBERS](#43-members-optional)
  - [4.4. HISTORY response](#44-history-response)
- [5. Request / Response](#5-request--response)
  - [5.1. SYMKEY](#51-symkey)
  - [5.2. SYMKEY_HISTORY](#52-symkey_history)
  - [5.3. HISTORY](#53-history)
  - [5.4. MEMBERS](#54-members)
- [6. DOCK Use](#6-dock-use)
- [7. Encryption](#7-encryption)
- [8. Membership Verification](#8-membership-verification)
- [9. Security Considerations](#9-security-considerations)
- [10. Versioning](#10-versioning)
- [11. Related Protocols](#11-related-protocols)

---

## Abstract

FIMP4V1 defines the **Team** mode of FIMP -- a closed, owner-managed group chat whose membership and metadata are recorded on chain as a FEIP `Team` entity, and whose messages are end-to-end encrypted under a per-team symmetric key. The team owner is the sole authority for adding and removing members, rotating the symkey, and disbanding the team. Symmetric keys are distributed via P2P `SYMKEY` messages, each wrapping the key to a specific member's secp256k1 public key. Messages are addressed to the team's advertised DOCK with `recipients = [teamId]`, encrypted under the current symkey, and tagged with the symkey version that produced them.

## 1. Overview

A Team is characterized by:

- An on-chain FEIP `Team` record (`entityType = TEAM`) whose `id` is the `teamId`.
- A single **owner** FID, encoded on chain. Only the owner may modify membership and metadata.
- An on-chain `members` list, modified only by owner-signed FEIP transactions.
- An on-chain `home` map advertising the team's DOCK URL (under `"DOCK@No1_NrC7"`).
- A per-team **symmetric key** (AES-256), generated and rotated by the owner at the IM layer (the symkey is NOT on chain).
- All chat messages encrypted under the current symkey; only members holding the corresponding key version can decrypt.
- All chat messages addressed to the team as a whole through DOCK, with `recipients = [teamId]`.

Team is the IM-layer counterpart of Square (FIMP3) for closed groups, and the on-chain counterpart of Room (FIMP2). It combines on-chain authoritative membership with end-to-end IM-layer encryption.

## 2. Data Model

The `Team` FEIP entity is fully defined by FEIP and is not redefined here. FIMP only consumes the following fields:

|Field|Type|Source|FIMP usage|
|---|---|---|---|
|`id`|string|on-chain FEIP|`teamId`. Used as `targetId` in chat messages and as the DOCK recipient.|
|`owner`|FID|on-chain FEIP|Sole authority for membership / symkey / metadata changes.|
|`stdName`|string|on-chain FEIP|Display name.|
|`desc`|string|on-chain FEIP|Description.|
|`members`|list of FIDs|on-chain FEIP|Authoritative member list, owner-managed.|
|`home`|map<string,string>|on-chain FEIP|Service map. The DOCK URL under `"DOCK@No1_NrC7"` is the team's mailbox.|
|`active`|boolean|on-chain FEIP|If false, the team is disbanded.|
|`birthHeight`, `lastHeight`, ...|various|on-chain FEIP|Used for incremental sync (§3.6).|

Per-member local state (NOT on the wire, NOT on chain):

|Field|Type|Description|
|---|---|---|
|symkey store|map<(teamId, version) → symkey>|All symkey versions known to this member.|
|`currentVersion`|int32|Highest version known to this member.|

## 3. Lifecycle

Membership and metadata changes are **on-chain** FEIP transactions and generate no FIMP wire traffic. Symkey distribution is the only IM-layer activity tied to lifecycle events.

### 3.1. Create

The owner submits a FEIP Create transaction for the Team (specified by FEIP), which sets `stdName`, `desc`, optional `home` (DOCK URL), and an initial `members` list.

After creation, the owner:

1. Generates a fresh AES-256 symkey at version 1.
2. Stores the symkey locally, wrapped to the owner's own pubkey.
3. For each non-owner member listed at create time, sends a P2P `SYMKEY` message (§4.2) wrapping the symkey to that member's pubkey.

### 3.2. Add member

The owner submits a FEIP Add-member transaction. After confirmation, the owner sends a P2P `SYMKEY` message (§4.2) to the new member with the current symkey wrapped to their pubkey. The owner MAY simultaneously rotate the symkey (§3.4) -- although rotation is RECOMMENDED only for removals, not additions, since adding a member does not compromise the existing key.

### 3.3. Remove member

The owner submits a FEIP Remove-member transaction. After confirmation, the owner SHOULD:

1. Generate a new symkey at the next version.
2. Distribute the new symkey to every remaining member via P2P `SYMKEY`.

The removed member retains older symkey versions (used to decrypt past messages) but cannot decrypt messages encrypted under the new version. Owners that do not rotate the symkey on removal accept that the removed member can still decrypt subsequent messages.

### 3.4. Update

The on-chain Update transaction may change `stdName`, `desc`, `home`, or other metadata. After the next sync, members refresh their local cache; if `home.DOCK@No1_NrC7` changed, members switch to the new DOCK URL for subsequent reads and posts.

The owner MAY rotate the symkey at any time without changing on-chain state. Rotation is signaled by sending a fresh P2P `SYMKEY` (with a higher `symkeyVersion`) to every member.

### 3.5. Disband

The owner submits a FEIP transaction setting `active = false` (or otherwise marking the team as disbanded, per FEIP). FIMP wire effect: none. Members observing `active = false` after sync SHOULD stop posting and SHOULD treat the team as read-only.

### 3.6. Membership sync

FIMP implementations SHOULD periodically poll FAPI BASE for Team records that match either:

- a known `teamId` set (refresh changes); or
- a filter `members contains <ownFid>` ordered by `lastHeight ASC`, with `lastHeight > <last seen height>` (incremental sync).

The cursor (`last seen height`) is local state. There is no FIMP wire signal for Team membership changes; the only mechanism is the on-chain query.

## 4. Message Contracts

### 4.1. Chat messages

|Field|Value|
|---|---|
|`type`|`TEAM`|
|`contentType`|user content type (`TEXT`, `STREAM`, `VOICE`, `HAT`, `REACTION`, `EDIT`, `DELETE`, `FORWARD`)|
|`senderId`|sender FID (MUST be a current team member at send time)|
|`targetId`|`teamId`|
|`timestamp`|ms since epoch|
|`content`|MUST be `null` (cleared)|
|`cipher`|JSON `CryptoDataByte` AES-GCM ciphertext over the original plaintext bytes of `content`, using the current team symkey|
|`symkeyVersion`|the version of the symkey used to produce `cipher`|
|`dataBase64`|For `STREAM`/`VOICE`: present, but **not** encrypted at the IM layer in version 1 (see §[7. Encryption](#7-encryption))|
|`replyToId`, `threadId`|optional|

The plaintext that is encrypted is the same string that would have been placed in the unencrypted `content` field (see FIMP1 §4 for per-ContentType plaintext schemas).

### 4.2. SYMKEY (proactive push)

The owner pushes the team symkey to a single member. Used at create time, after add-member, and after rotation.

|Field|Value|
|---|---|
|`type`|`P2P`|
|`contentType`|`SYMKEY`|
|`senderId`|owner FID|
|`targetId`|recipient FID (a current member)|
|`content`|`"<teamId>:<asyOneWayCipherJson>"`|
|`symkeyVersion`|the version of the key being delivered|

The `asyOneWayCipherJson` is the JSON serialization of a `CryptoDataByte` of `type: "asy1way"` wrapping the raw symkey bytes to the recipient's pubkey, per FTSP.

A member receiving a `SYMKEY` MUST verify that the sender (per FUDP authentication / `DockItem.sender`) is a current team member as of the message's delivery height. The member SHOULD additionally verify that the sender is the team owner; non-owner-originated `SYMKEY` messages are valid only as responses to an explicit request (§5.1) and MUST carry a `requestId` matching such a request.

### 4.3. MEMBERS (optional)

The owner MAY push a member-list snapshot proactively. This is a convenience to avoid forcing every member to wait for the next on-chain sync.

|Field|Value|
|---|---|
|`type`|`P2P`|
|`contentType`|`MEMBERS`|
|`senderId`|owner FID|
|`targetId`|recipient FID|
|`content`|JSON `{"teamId": "...", "members": ["fid1", "fid2", ...], "lastHeight": <int>}`|

The `lastHeight` field allows the receiver to update its sync cursor. Receivers MUST treat the on-chain record as authoritative; a `MEMBERS` push is informational and MUST NOT replace on-chain verification when the receiver makes an authorization decision (e.g., responding to a SYMKEY request).

### 4.4. HISTORY response

See §5.3 for the request side. The response is a single message:

|Field|Value|
|---|---|
|`type`|`P2P`|
|`contentType`|`HISTORY`|
|`senderId`|responder FID|
|`targetId`|requester FID|
|`content`|JSON of the `Hat` referencing the encrypted history file on DISK|
|`dataBase64`|Base64 of the `kCipher` (the file's symmetric key wrapped to the requester's pubkey)|
|`requestId`|id of the original `HISTORY` request|

## 5. Request / Response

### 5.1. SYMKEY

A member requests the current team symkey (or a specific version) from any current member. Used by a new joiner whose owner has not yet pushed the key, or by a member whose symkey store has been lost.

|Direction|Field|Value|
|---|---|---|
|Request|`type`|`P2P` (preferred) or `TEAM`|
|Request|`contentType`|`REQUEST`|
|Request|`requestType`|`SYMKEY`|
|Request|`content`|`"<teamId>"` for the current version, or `"<teamId>:<version>"` for a specific version|
|Response|message form|a `SYMKEY` message (§4.2), with `requestId` set to the request's id|

The responder MUST verify that the requester is a current team member (per the on-chain record at `lastHeight ≥ request.timestamp`'s confirmed block) before responding. Non-members MUST be refused.

A request from a non-member SHOULD also be reported to the local quarantine / stranger-detection layer.

### 5.2. SYMKEY_HISTORY

A member requests a batch of historical symkeys.

|Direction|Field|Value|
|---|---|---|
|Request|`type`|`P2P`|
|Request|`requestType`|`SYMKEY_HISTORY`|
|Request|`content`|`"<teamId>:<v1>,<v2>,..."` (comma-separated list of versions)|
|Response|message form|one or more `SYMKEY` messages, each carrying one version, all with the same `requestId`|

### 5.3. HISTORY

A member requests the team's chat history, encrypted as a single file on DISK.

|Direction|Field|Value|
|---|---|---|
|Request|`type`|`P2P`|
|Request|`contentType`|`REQUEST`|
|Request|`requestType`|`HISTORY`|
|Request|`content`|JSON `{"entityId": "<teamId>", "since": <ms?>, "before": <ms?>}`|
|Response|see §4.4||

The DISK file MUST be a JSON array of `ImMessage` records (in their local-storage form, including `cipher` and `symkeyVersion` for past chat messages). The file is encrypted with a fresh per-history symmetric key, which is then wrapped to the requester's pubkey and placed in `dataBase64`.

The responder MUST verify that the requester is a current member.

### 5.4. MEMBERS

A member requests the current member list. The response is a `MEMBERS` message (§4.3) with `requestId` set.

|Direction|Field|Value|
|---|---|---|
|Request|`type`|`P2P`|
|Request|`contentType`|`REQUEST`|
|Request|`requestType`|`MEMBERS`|
|Request|`content`|`"<teamId>"`|
|Response|message form|a `MEMBERS` message (§4.3), with `requestId` set|

## 6. DOCK Use

|Operation|recipients|dataType|Body|Target dock|
|---|---|---|---|---|
|Chat message (§4.1)|`[<teamId>]`|`"IM"`|compact binary `ImMessage`|the team's advertised DOCK (from on-chain `home.DOCK@No1_NrC7`); senders MAY post via own DOCK with `targetDockUrl` set to the team's DOCK to use FAPI13 forwarding|
|`SYMKEY`, `MEMBERS`, HISTORY response (§4.2-4.4)|`[<recipientFid>]`|`"IM"`|compact binary `ImMessage`|recipient's own DOCK (per FIMP1)|
|`REQUEST` (§5)|`[<recipientFid>]`|`"IM"`|compact binary `ImMessage`|recipient's own DOCK (per FIMP1)|

Receivers fetch team chat with `dock.fetch` and `recipientIds = [<teamId>]`. Receivers separately fetch their own P2P inbox with `recipientIds = [<own FID>]` to receive control messages.

Receivers MUST de-duplicate by `DockItem.id` (FIMP0 §DOCK Conventions).

## 7. Encryption

### 7.1. Symkey

The team symkey is a 256-bit AES key generated by the owner via a CSPRNG. The owner stores it locally wrapped to its own pubkey. For each member, the owner produces a one-way asymmetric ciphertext (`CryptoDataByte` of `type: "asy1way"`) wrapping the symkey to that member's secp256k1 pubkey, per FTSP.

### 7.2. Versioning

Symkeys are versioned by a monotonically increasing 32-bit integer. Version 1 is the initial key. The owner MUST increment the version when:

- A member is removed from the team and continued post-removal confidentiality is required.
- The owner judges the current key compromised.

The owner MAY increment the version at other times (e.g., scheduled rotation). All members MUST retain old symkey versions locally to decrypt past messages; an implementation MAY discard old versions only if the user explicitly opts in to forfeiting access to encrypted history.

### 7.3. Message encryption

The plaintext for AES-GCM encryption is the UTF-8 byte sequence that would have been placed in the `content` field of an unencrypted message. The IV and other AES-GCM parameters are encoded in the `CryptoDataByte` JSON, per FTSP. The output JSON is placed in `cipher`, and `content` MUST be set to `null`.

The `dataBase64` payload (when present) is NOT encrypted at the IM layer in version 1. Senders requiring confidential bulk payloads SHOULD use a `HAT` body whose underlying DISK file is encrypted using the team symkey or a per-file key wrapped under the team symkey. Implementations MAY define this convention freely as long as the key reference is signaled inside the encrypted `content`.

### 7.4. Recovery from missing symkey

When a receiver decodes a chat message whose `symkeyVersion` is not in its local store, the receiver SHOULD:

1. Send a `SYMKEY` request (§5.1) for the missing version.
2. Pending recovery, surface the message to the user as undecryptable.

A receiver SHOULD rate-limit retries per `(teamId, version)` to avoid request storms. A common limit is one request per two minutes.

## 8. Membership Verification

### 8.1. Sender side

Before posting a chat message, the sender SHOULD verify that the sender's own FID is a current member of the team. Non-members posting will incur DOCK fees but the message will not be delivered to any current member's fetch (FAPI13 expansion will not include the sender).

### 8.2. Receiver side

Receivers MUST treat the on-chain `members` list as the authoritative source. When evaluating whether to:

- Display a chat message: the receiver SHOULD verify that `DockItem.sender` was a member at `DockItem.createHeight` and discard the message otherwise (defense in depth; the DOCK server is expected to enforce this, but the receiver MAY also enforce).
- Respond to a `SYMKEY`, `SYMKEY_HISTORY`, `HISTORY`, or `MEMBERS` request: the receiver MUST verify the requester is a current member and MUST refuse non-members. Refusal MAY take the form of no response at all.
- Accept a proactive `SYMKEY` push: the receiver MUST verify that the sender is the current team owner. A `SYMKEY` push from a non-owner is valid only as a response (i.e., when `requestId` is set and matches a recent request).

## 9. Security Considerations

### 9.1. Authoritative membership

Membership is anchored on chain. A malicious actor cannot fake membership without subverting the chain. Receivers MUST use the on-chain record (queried via FAPI BASE) as the ground truth, not any push received over IM.

### 9.2. Owner trust

The owner controls the symkey. A malicious or compromised owner can:

- Add an attacker as a "member" on chain and push the current symkey to them, exposing future messages.
- Refuse to rotate the key after removing a member, allowing the removed member to continue decrypting messages.
- Delay or fail to deliver `SYMKEY` to legitimate new members.

These trust assumptions are inherent to the owner-managed model. For groups that require less trusted ownership, FIMP version 1 has no answer; the application layer MUST select participants accordingly.

### 9.3. Forward secrecy

FIMP version 1 does NOT provide forward secrecy. A future compromise of a member's prikey decrypts every past `SYMKEY` push the member received, which then decrypts every past chat message under those symkey versions. Implementations that require forward secrecy MUST limit retention of `SYMKEY` items on DOCK (FAPI13 `maxDays`) and SHOULD discard old symkeys locally once the user has explicitly opted out of historical access. A future FIMP version MAY define a forward-secure variant.

### 9.4. Sender impersonation

Within a team, any member who holds the symkey can produce a chat message with a valid `cipher`. Receivers MUST NOT authenticate the **sender** of a chat message based on decryption alone. The sender's identity is established by FUDP / `DockItem.sender`. Receivers MUST treat that authenticated identity as the authoritative `senderId` and discard messages whose `ImMessage.senderId` field disagrees.

### 9.5. Replay

Receivers MUST de-duplicate by `DockItem.id`.

### 9.6. Symkey-leak resistance

A symkey leaked to a non-member compromises every chat message under that version and any future version that is delivered to a still-included compromised member. Owners SHOULD rotate the symkey when:

- A member is removed.
- A member's device is reported lost.
- The current symkey has been used for an extended period (operational policy).

## 10. Versioning

This document defines version 1 of the Team mode (FIMP4V1). Future versions MAY:

- Define forward-secret symkey delivery (e.g., a tree-based key agreement).
- Define multi-owner / multi-admin authorization.
- Define a sealed-message variant that hides `senderId` from non-members.
- Define an explicit rekey-broadcast message containing all members' wrapped keys in a single envelope.

Wire-incompatible changes require a new version number.

## 11. Related Protocols

- **FIMP0V1** -- Foundational rules and the `ImMessage` envelope.
- **FIMP1V1** -- P2P mode (used as the carrier for SYMKEY, MEMBERS, HISTORY responses, REQUESTs).
- **FIMP2V1** -- Room mode (analogous closed-group semantics, but with local non-on-chain membership).
- **FIMP3V1** -- Square mode (open on-chain membership, no IM-layer encryption).
- **FEIP** -- Defines the on-chain `Team` entity, owner-signed Add/Remove/Update/Disband transactions, and the `members` list semantics.
- **FAPI11V1 (BASE)** -- Used to query Team records on chain.
- **FAPI13V1 (DOCK)** -- Store-and-forward; group-expansion behavior for `recipients = [<teamId>]`.
- **FAPI12V1 (DISK)** -- Used for HISTORY files and HAT-referenced large attachments.
- **FTSP** -- Cryptographic primitives (`CryptoDataByte` of types `"sym"` and `"asy1way"`).
