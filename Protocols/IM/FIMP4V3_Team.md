# FIMP4V3_Team

|Field|Content|
|---|---|
|Title|Team|
|Type|FIMP|
|SN|4|
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
  - [5.2. SYMKEY_HISTORY (deprecated)](#52-symkey_history-deprecated)
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

FIMP4V3 defines the **Team** mode of FIMP -- a closed, owner-managed group chat whose membership and metadata are recorded on chain as a FEIP `Team` entity, and whose messages are end-to-end encrypted under a per-team symmetric key. The team owner is the sole authority for adding and removing members, rotating the symkey, and disbanding the team. Symmetric keys are distributed via P2P `SYMKEY` messages, each wrapping the key to a specific member's secp256k1 public key. Messages are addressed to the team's advertised DOCK with `recipients = [teamId]`, encrypted under the current symkey, and tagged with the symkey version that produced them.

## 1. Overview

A Team is characterized by:

- An on-chain FEIP `Team` record (`entityType = TEAM`) whose `id` is the `teamId`.
- A single **owner** FID, encoded on chain. Only the owner may modify membership and metadata.
- An on-chain `members` list, modified only by owner-signed FEIP transactions.
- An on-chain `home` map advertising the team's DOCK URL (under `"DOCK@No1_NrC7"`).
- A per-team **symmetric key** (AES-256), generated and rotated by the owner at the IM layer (the symkey is NOT on chain).
- All chat messages encrypted under the current symkey; only members holding the corresponding key version can decrypt.
- All chat messages addressed to the team as a whole through DOCK, with `recipients = [teamId]`.

Every message in this mode, including every control message, carries the FIMP0V3 signature trailer: the author's pubkey and a Schnorr signature over the whole envelope. Before acting on a message a receiver verifies the signature, checks that the message is addressed to it, and drops a `(senderId, id)` it has taken in before (FIMP0V3 §3.5). Throughout this document, **the verified sender** means the `senderId` of a message that has passed those checks.

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

Throughout this section, `content` and `data` name the two **sections of the body** (FIMP0 §Body Framing), not envelope fields. Where the body is sealed, they describe the plaintext *inside* the seal.

### 4.1. Chat messages

|Field|Value|
|---|---|
|`type`|`TEAM`|
|`contentType`|user content type (`TEXT`, `STREAM`, `VOICE`, `HAT`, `REACTION`, `EDIT`, `DELETE`, `FORWARD`)|
|`senderId`|sender FID (MUST be a current team member at send time)|
|`targetId`|`teamId`|
|`timestamp`|ms since epoch|
|`body`|the sealed body: AES-GCM ciphertext, as a binary FTSP bundle, over the **whole** body framing (both the `content` and `data` sections), using the current team symkey|
|`FLAG_BODY_SEALED`|set|
|`symkeyVersion`|the version of the symkey that sealed `body`|
|`replyToId`, `threadId`|optional|

The plaintext input to the seal is the complete body framing of FIMP0 §Body Framing: `contentLen + content + dataLen + data`. Section 1 holds what would have been the plaintext `content` (see FIMP1 §5 for per-ContentType schemas); section 2 holds the raw binary payload, if any. Both are inside the seal.

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

A member receiving a `SYMKEY` MUST verify that the verified sender is a current team member as of the message's delivery height, and MUST store the key only when one of these holds:

1. the verified sender is the team **owner**; or
2. the verified sender is the receiver's **own FID**; or
3. the message carries a `requestId` matching a request **this device** has outstanding (§7.4), and the version it carries is one that request named.

The second case is not a weakening. One FID may be signed in on several devices, and the envelope signature (FIMP0V3 §3) proves the sender holds this identity's prikey — so the key comes from this identity, whatever device sent it. It is also the only route a reinstalled owner has, since their other device may hold the only copy of the key in existence.

Anything else is discarded. A `SYMKEY` from a member who was not asked is not a gift, it is an unsolicited write into the one store that decides what this device can read; and since §7.1 forbids overwriting, accepting it would let any member add rows to any other member's key store indefinitely. Together with §7.1 this is also why no overwrite rule is needed: what cannot be stored cannot displace anything.

A receiver MUST NOT infer solicitation from the mere presence of a `requestId`. The id must match a request the receiver still has a record of, which is the record §7.4 requires it to keep.

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
|`data`|the `kCipher` (the file's symmetric key wrapped to the requester's pubkey), raw in the body's `data` section|
|`requestId`|id of the original `HISTORY` request|

## 5. Request / Response

### 5.1. SYMKEY

A member requests team symkeys from any current member: the current one, one named version, or a batch of named versions. Used by a new joiner whose owner has not yet pushed the key, by a member whose symkey store has been lost, and by a member recovering the older versions that seal messages sent before they arrived.

|Direction|Field|Value|
|---|---|---|
|Request|`type`|`P2P` (preferred) or `TEAM`|
|Request|`contentType`|`REQUEST`|
|Request|`requestType`|`SYMKEY`|
|Request|`content`|`"<teamId>"`, `"<teamId>:<version>"`, or `"<teamId>:<v1>,<v2>,..."`|
|Response|message form|one `SYMKEY` message (§4.2) per version answered, each with `requestId` set to the request's id|

The three content forms are one form. A bare `teamId` asks for whatever the responder holds as current; a single version is a list of one; a comma-separated list asks for each of them. Implementations MUST parse all three with one parser and answer them on one path, and MUST NOT treat the single-version form as a different kind of request from the batch.

The responder MUST verify that the requester is a current team member (per the on-chain record at `lastHeight ≥ request.timestamp`'s confirmed block) before responding. Non-members MUST be refused.

A request from a non-member SHOULD also be reported to the local quarantine / stranger-detection layer.

### 5.2. SYMKEY_HISTORY (deprecated)

`SYMKEY_HISTORY` was the batch form of §5.1, with content `"<teamId>:<v1>,<v2>,..."` -- a string §5.1 now accepts. It is **deprecated**: implementations MUST NOT send it, MUST still accept it as an alias for §5.1, and MUST keep its `RequestType` ordinal reserved rather than reassigning it.

The rules below govern a batch in either form.

A request MUST NOT name more than **64** versions, and a responder MUST answer at most the first 64 in ascending version order, ignoring the rest. A member needing more than 64 versions sends more than one request. See §9.8 for why the bound exists; the responder's half of it is what makes it a defence, since a requester that disregards the limit is exactly the case it is there for.

Entries the responder cannot read as a positive integer are **skipped, not grounds for refusing the request**: a list of eight versions with one malformed entry in it is still seven keys the asker needs. A request in which no entry is readable, or which names no version at all, has nothing to answer and is ignored.

Versions SHOULD be de-duplicated and sent in ascending order, so that the same set of missing versions always produces the same request and a responder's cooldown (§7.4) can recognise a repeat.

The responder answers with the versions it holds and **omits those it does not**, rather than refusing the batch: the same rule as §5.1, for the same reason. The asker learns what arrived by which messages open.

### 5.3. HISTORY

A member requests the team's chat history, encrypted as a single file on DISK.

|Direction|Field|Value|
|---|---|---|
|Request|`type`|`P2P`|
|Request|`contentType`|`REQUEST`|
|Request|`requestType`|`HISTORY`|
|Request|`content`|JSON `{"entityId": "<teamId>", "since": <ms?>, "before": <ms?>}`|
|Response|see §4.4||

The DISK file MUST be a JSON array of `ImMessage` records in their local-storage form. Local storage keeps the *opened* body, so history records carry `content` and `data` in the clear within the file, together with `symkeyVersion` for provenance; the file's own encryption is what protects them. The file is encrypted with a fresh per-history symmetric key, which is then wrapped to the requester's pubkey and placed in the body's `data` section.

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

The DOCK stores an item under the ids it is addressed to and returns it to whoever asks; it does not check membership, so the rules of §8 are enforced by the receiver. Receivers MUST de-duplicate by `(senderId, id)` and discard any message whose `targetId` is not the team or FID they fetched for (FIMP0V3 §3.5).

## 7. Encryption

### 7.1. Symkey

The team symkey is a 256-bit AES key generated by the owner via a CSPRNG. The owner stores it locally wrapped to its own pubkey. For each member, the owner produces a one-way asymmetric ciphertext (`CryptoDataByte` of `type: "asy1way"`) wrapping the symkey to that member's secp256k1 pubkey, per FTSP.

A stored symkey MUST NOT be overwritten. A key that arrives for a `(teamId, version)` already held is either the same key -- in which case storing it is a no-op, and two members answering one request is the ordinary reason for it -- or a different key, in which case **both are kept**. No sender, the owner included, may replace a key already stored: the displaced key is the only thing that can open the messages sealed under it, and there is no other copy of it anywhere.

A member holding two keys at one version MUST try each when opening a body that names that version, and MUST select by which one authenticates. AES-GCM's tag decides; a wrong key cannot produce a false positive. Implementations SHOULD distinguish stored keys locally by `SHA-256(symkey)`, which is computed from the key and is therefore never transmitted and costs no wire byte.

An implementation MAY cap the number of keys it stores per team (256 is ample) to bound a misbehaving peer. §4.2 is what keeps that cap out of a peer's reach: a key that is neither the owner's nor an answer to a request this device made is not stored at all.

### 7.2. Versioning

A symkey version is the number of seconds since the Unix epoch at the moment the key was minted, floored above every version the minting device already knows for that team, and read as unsigned. FIMP0 §Symkey id defines it. The owner MUST mint a new version when:

- A member is removed from the team and continued post-removal confidentiality is required.
- The owner judges the current key compromised.

The owner MAY mint at other times (e.g., scheduled rotation). All members MUST retain old symkey versions locally to decrypt past messages; an implementation MAY discard old versions only if the user explicitly opts in to forfeiting access to encrypted history.

**Minting is not single-device.** One FID may be signed in on several devices, so two devices of the same owner can be partitioned from each other and both mint for the same team. A counter cannot survive that: each device mints "the next version" from its own store, and a device that holds nothing -- reinstalled, restored, or newly signed in -- mints version 1 for a key that is not the version 1 the team already uses. A timestamp with the floor in FIMP0 §Symkey id reduces that to two devices minting in the same second, and §7.1's no-overwrite rule makes even that survivable rather than destructive.

For the same reason, an implementation MUST NOT rotate automatically in response to an on-chain event without a single-writer rule or a per-device delay. A human action on one device at a time collides only by coincidence; "member removed on chain, therefore rotate" fires on every one of the owner's devices, in the same second, every time.

### 7.3. Message encryption

The plaintext for AES-GCM encryption is the entire body framing (FIMP0 §Body Framing), covering both the textual and the binary payload. The IV and other AES-GCM parameters are carried in the binary FTSP bundle that becomes `body`; `FLAG_BODY_SEALED` is set and `symkeyVersion` names the key.

Because the binary payload is inside the seal, a team attachment small enough to travel inline needs no separate key-wrapping scheme. For an attachment that exceeds the destination's size budget (FIMP0 §Payload Sizing) and must go to DISK, the file SHOULD be encrypted under the team symkey, or under a per-file key wrapped to it. The `Hat` carrying that key reference travels inside the sealed body, so it is never exposed to the DOCK.

> **Change from version 1.** FIMP4V1 encrypted `content` and left `dataBase64` in the clear, so a team voice message travelled with its metadata encrypted and its audio readable by the DOCK operator. Version 2 seals the whole body.

### 7.4. Recovery from missing symkey

When a receiver decodes a chat message whose `symkeyVersion` is not in its local store, the receiver SHOULD:

1. Send a `SYMKEY` request (§5.1) for the missing version, addressed to **the message's verified sender**, who demonstrably holds that key. A receiver MAY additionally ask other members, and SHOULD let the user choose them, but it MUST NOT wait for the user to act before asking the sender: the automatic ask is what makes recovery the normal case rather than a feature the user has to find.
2. Pending recovery, surface the message to the user as undecryptable, identifying the key by its mint time rather than by its raw version number.
3. On storing a symkey for the team -- from any source: a response to the request above, a proactive push (§4.2), or a batch (§5.1) -- open the messages already stored under the version it supplies, and replace the undecryptable rendering with the recovered content.

Step 3 completes the recovery and is REQUIRED for step 1 to have any effect a user can see. A receiver that performs steps 1 and 2 alone stores the key and changes nothing on screen, so a recovery that succeeded is indistinguishable from one that failed -- and asking again cannot help, because the key is already held. The sealed body retained under §7.2 is what makes step 3 possible without a second delivery of the same key, and a receiver MUST NOT require one.

Step 3 MUST be idempotent per `(teamId, version)`. The same version may arrive more than once -- two members answering one request, or a push racing a response -- and the repeat MUST NOT duplicate, reorder or re-count anything. In particular, opening a stored backlog MUST NOT raise the conversation's unread count: those messages were counted when they were stored, and their unread state is unchanged by becoming readable.

A receiver MUST rate-limit requests per `(teamId, version, responder)` to **at most one per two minutes**, counted from the last request actually sent to that responder, and MUST persist enough state to enforce it across a restart.

**Per responder, not per question.** The cost this bounds is borne by whoever answers (§9.8), so what must be limited is how often one member is made to answer the same thing. Counting per question instead would mean a receiver that asked one member and got nothing could not ask a second for two minutes — which throttles recovery rather than traffic, since asking somebody else is not a repeat and costs the first member nothing. The limit is what makes step 1's automatic ask safe: without it, a backlog of a hundred messages under three missing versions is a hundred requests, paid for by the asker and delivered to members who may hold none of them.

The state a receiver persists for this is the same state §4.2 requires to decide whether an unsolicited key may be stored -- the team, the version, who was asked, the `requestId`, and when. An implementation SHOULD keep one record per outstanding `(teamId, version)` and clear it when a key for that version is stored.

A receiver SHOULD show an outstanding request to the user, naming who was asked and when, and SHOULD keep showing it until it is answered or the user abandons it. A request that produced nothing is the only signal that recovery has stalled, and it is information only the user can act on: which member to ask next is a judgement about people, not about keys.

## 8. Membership Verification

### 8.1. Sender side

Before posting a chat message, the sender SHOULD verify that the sender's own FID is a current member of the team. A non-member's post is accepted and charged for by the DOCK, and discarded by every receiver (§8.2).

### 8.2. Receiver side

Receivers MUST treat the on-chain `members` list as the authoritative source. When evaluating whether to:

- Display a chat message: the receiver MUST discard a message whose verified sender is not a member, and SHOULD judge membership as of `DockItem.createHeight`. The DOCK does not enforce this. `DockItem.sender` MUST NOT be used for the check: after a DOCK forward it names the forwarding server.
- Respond to a `SYMKEY`, `SYMKEY_HISTORY`, `HISTORY`, or `MEMBERS` request: the receiver MUST verify the requester is a current member and MUST refuse non-members. Refusal MAY take the form of no response at all.
- Accept a proactive `SYMKEY` push: the receiver MUST verify that the sender is the current team owner. A `SYMKEY` push from a non-owner is valid only as a response, on the terms §4.2 sets out -- a `requestId` matching a request the receiver still holds a record of, naming the version delivered.

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

Within a team, any member who holds the symkey can produce a chat message whose `body` seals validly under that key, so decryption proves only that the author held the key. The author is the FID whose key signed the envelope (FIMP0V3 §3). Receivers MUST verify the signature before opening the body, MUST discard a message not signed by `senderId`'s key, and MUST NOT use the FUDP peer or `DockItem.sender` as the author. Owner-only and manager-only messages are authorized against the verified sender.

### 9.5. Replay

A signed message verifies every time it is presented, and anyone who has seen it can store the same bytes again under a new `DockItem.id`. Receivers MUST remember `(senderId, id)` for every message taken in, for at least the DOCK retention period, and discard a second arrival before acting on it -- a replayed `SYMKEY` push or request as much as a chat line.

### 9.6. Symkey-leak resistance

A symkey leaked to a non-member compromises every chat message under that version and any future version that is delivered to a still-included compromised member. Owners SHOULD rotate the symkey when:

- A member is removed.
- A member's device is reported lost.
- The current symkey has been used for an extended period (operational policy).

### 9.7. Record of symkey distribution

A device answers a `SYMKEY` request **automatically, without asking its user**. §5.1 requires only that the requester be a current member, and that condition is satisfied by anyone the owner has added on chain. The key handed over opens every message under its version, including messages sent long before the requester joined. This is intended -- a new joiner has to obtain the key from somewhere, and requiring a human to approve each request would leave joiners stranded -- but it means a member's device gives away the team's readable history on the strength of a check it performs silently.

An implementation SHOULD therefore keep a local, durable record of every symkey it distributes and every symkey it accepts, holding at least:

- the team and the version,
- the counterparty FID,
- the direction (sent or received),
- whether it was solicited (a response to a request) or unsolicited (a proactive push),
- the time.

A request the device **refused or could not answer** SHOULD be recorded on the same terms -- a version not held, a requester with no published pubkey, a requester the on-chain record does not list. These rows are the most actionable the user has: a member repeatedly asking for a version nobody answers is a recovery that has stalled and that the owner can fix by re-sharing, and a non-member asking at all is the anomaly §9.2 warns about, arriving in the one place it can be seen.

Since §7.2 makes a version its own mint time, a row needs no separate field for when the key was created; the version answers it. The row's own `time` remains the time of the exchange, which is the different question the record exists to answer.

The record is bounded by §4.2 and §7.4 rather than by a retention policy: a `received` row can be caused only by the owner or by a request this device made, and a `sent` row only by a member the on-chain record lists, no more often than the responder's own limits allow. So an implementation is not choosing between keeping the record and bounding its growth.

One case that reasoning does not cover is a **refusal**, which costs the sender only the sending: a member pushing keys in a loop would write a row per attempt. An implementation SHOULD therefore fold repeats of the identical event -- same entity, version, counterparty, direction and outcome -- inside a window of about an hour into the row already written, carrying a count and a last-seen time. That preserves the signal exactly, since what a reader needs from a hundred identical refusals is that they happened, from whom, and between when and when. It is not a retention policy and MUST NOT be used as one: distinct events are never folded, and nothing is dropped.

Three questions depend on this record, and nothing else can answer them:

- **Leak radius.** §9.6 tells an owner to rotate when a key may have leaked. Deciding that, and knowing who must be re-keyed, requires knowing who holds which version. The chain does not answer it: membership is public, but key *delivery* is not, and the two differ whenever a push was skipped, refused, or answered by a member rather than the owner.
- **Anomaly.** A device that answered a request its user did not expect -- from a FID added to the team without their knowledge (§9.2), or by software acting on the local key store -- leaves no other trace that the key left the device.
- **Provenance.** A received key determines whose copy of the conversation the user is reading. Who supplied it is part of what the resulting plaintext is worth, particularly when it came from a member rather than the owner.

The record is local and MUST NOT be transmitted. It is a map of who can read what, and is more sensitive than the membership it derives from: membership is already on chain, whereas this is not recoverable from any public source. It MUST NOT be included in a `HISTORY` response (§5.3) and SHOULD be excluded from conversation exports and backups that are shared with a peer.

An implementation SHOULD retain the record for at least as long as it retains the symkeys themselves, and SHOULD NOT discard it under a general log-rotation or ring-buffer policy. A distribution record is consulted after a suspected compromise, so the entry that matters is characteristically the oldest one, and a bounded diagnostic log is the form most likely to have dropped it.

### 9.8. Request amplification

A batch `SYMKEY` request -- several versions in one message, however it is spelled -- is answered with one `SYMKEY` message per version named, and **the responder bears the whole cost of answering**: one asymmetric seal per version, and one DOCK item per version put at the requester's DOCK, whose ingress and storage the putting party pays for. The request that causes all of it is a single short message.

That asymmetry is an amplifier. Without the bound in §5.2, one member could name ten thousand versions and have another member's device perform ten thousand seals and pay to store ten thousand items -- from inside the team, with valid membership, using nothing but the protocol as specified. Nothing else in this document limits it: the membership check passes, the signature verifies, the replay check (§9.5) sees one message because there *is* one message, and the rate limit in §7.4 governs how often a request may be repeated rather than how much a single one may ask for.

The bound is therefore a limit on **one request**, enforced by the responder and not merely by the requester's good behaviour: a requester that respects it needs no limit imposed, and a requester that does not is the case the limit exists for. 64 costs nothing legitimate -- it is far more rotations than a real team accumulates, and a member who genuinely needs more sends a second request, which the rate limit then paces.

A responder MAY apply a stricter limit, and SHOULD count the versions it has actually answered rather than the versions named, so that a request padded with versions the responder does not hold cannot buy a larger answer than an honest one.

## 10. Versioning

This document defines version 3 of the Team mode (FIMP4V3), which accompanies the version 3 envelope of FIMP0V3 and does not interoperate with version 2 or version 1.

Amendments made within version 3, during development:

1. A symkey version is a mint timestamp rather than a counter (§7.2, FIMP0 §Symkey id), read unsigned, with pre-timestamp counters still accepted.
2. A stored symkey is never overwritten, two keys may share a version, and a receiver tries each (§7.1).
3. `SYMKEY` takes zero, one or many versions; `SYMKEY_HISTORY` is deprecated to an accepted alias (§5.1, §5.2).
4. The solicited-response rule for a non-owner `SYMKEY` is a MUST, checked against a persisted request record; a key from the receiver's own FID is admitted unasked, since the envelope signature proves it came from this identity (§4.2, §8.2).
5. Recovery asks the message's sender automatically, and the retry limit is a MUST with persisted state, counted per responder rather than per question (§7.4).
6. Refused requests join the distribution record (§9.7).

**None of these changes the wire**, so none requires a new version number under the rule at the end of this section: every one is either a constraint on the value carried in an existing field or a local record that is never transmitted. A build implementing them exchanges bytes with a build that does not; what differs is which values are minted and which messages are stored.

Changes from FIMP4V2:

1. Every message is signed by its author (FIMP0V3 §3). Membership, owner and manager checks are made against the verified sender instead of FUDP authentication or `DockItem.sender` (§4.2, §8.2, §9.4).
2. The DOCK is stated not to check membership; receivers MUST discard chat from non-members (§8.2), previously a SHOULD.
3. Receivers de-duplicate on `(senderId, id)` rather than dock id, and discard messages not addressed to them (§6, §9.5).

Changes from FIMP4V1, retained from version 2:

1. The sealed unit is the whole body, so a team `STREAM`/`VOICE` payload is now encrypted along with its metadata (§4.1, §7.3). Version 1 sealed `content` only and left `dataBase64` readable by the DOCK.
2. `content` and `dataBase64` are no longer envelope fields; they are the two sections of the single `body` field, and binary data is raw rather than Base64.
3. The sealed body is a binary FTSP bundle rather than a `CryptoDataByte` JSON string in `cipher`.
4. Inline payload size is governed by the destination's resolved budget (FIMP0V2 §Payload Sizing) rather than a fixed constant.

Future versions MAY:

- Define forward-secret symkey delivery (e.g., a tree-based key agreement).
- Define multi-owner / multi-admin authorization.
- Define a sealed-message variant that hides `senderId` (and the signature trailer's pubkey) from non-members.
- Define an explicit rekey-broadcast message containing all members' wrapped keys in a single envelope.

Wire-incompatible changes require a new version number.

## 11. Related Protocols

- **FIMP0V3** -- Foundational rules, the `ImMessage` envelope, body framing, payload sizing, and the envelope signature and receive checks (`FIMP0V3_Signing_Proposal`).
- **FIMP1V3** -- P2P mode (used as the carrier for SYMKEY, MEMBERS, HISTORY responses, REQUESTs, and the source of their sealing rules).
- **FIMP2V3** -- Room mode (analogous closed-group semantics, but with local non-on-chain membership).
- **FIMP3V3** -- Square mode (open on-chain membership, no IM-layer sealing).
- **FEIP** -- Defines the on-chain `Team` entity, owner-signed Add/Remove/Update/Disband transactions, and the `members` list semantics.
- **FAPI11V1 (BASE)** -- Used to query Team records on chain.
- **FAPI13V1 (DOCK)** -- Store-and-forward; one item stored under `recipients = [<teamId>]` for every member to fetch.
- **FAPI12V1 (DISK)** -- Used for HISTORY files and HAT-referenced large attachments.
- **FTSP** -- Cryptographic primitives (`CryptoDataByte` of types `"sym"` and `"asy1way"`).
