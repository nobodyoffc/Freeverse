# FVEP10V1_HAT

## Contents

[Summary](#summary)

[Abstract](#abstract)

[Motivation](#motivation)

[Specification](#specification)

[Examples](#examples)

[Versioning](#versioning)

[Related Protocols](#related-protocols)

[Reference Implementation](#reference-implementation)

---

## Summary

|Field|Content|
|---|---|
|Title|HAT|
|Type|FVEP|
|SN|10|
|Ver|1|
|Category|Foundation|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-05-09|
|PID||

## Abstract

This protocol defines **HAT** (Hash Attribute Table) — a structured metadata record that describes a piece of data identified by a DID (Data ID). DISK stores only the raw byte content addressed by the DID; HAT carries every attribute *about* that content: how it was hashed, when it was created, what it is for, which application owns it, where copies live, whether it is encrypted, what version it is, and so on.

HAT and DISK together implement a clean separation of **content** and **metadata**. DISK is a deduplicated, content-addressed byte store that knows nothing about meaning. HAT is a mutable, transmissible description that knows nothing about bytes. The DID is the only link between the two: it identifies a unique chunk of bytes in DISK and serves as the primary key of the HAT record that describes those bytes.

A HAT is small (typically far smaller than the data it describes) and self-contained. It can be kept private as a personal data index, shared among a group of collaborators, exchanged between two parties through a messaging channel, or published openly. In particular, **in messaging applications, what gets transmitted is the HAT, not the data**: the bytes already live on one or more DISK services, and the HAT carries everything the recipient needs to locate, verify, and (if encrypted) decrypt them.

## Motivation

A content-addressed store such as DISK (FVEP, see Related Protocols) has a deliberately narrow contract: given a DID, return the bytes. This is the foundation of openness, deduplication, and verifiability — but it is intentionally meaning-blind. The same DID may be:

- a draft contract for the sender and a reference document for the recipient;
- the latest version of a document, or an outdated previous version;
- a raw file, or the cipher of a raw file held under a different DID;
- a slice of a large file, or the whole file;
- accessed every day, or untouched for a year.

None of this can or should live inside DISK. DISK must remain a thin byte layer so that anyone can run a DISK service, anyone can mirror data, and the same byte content can serve any number of independent contexts without ambiguity.

HAT is the missing layer — a structured way to describe data, decoupled from the bytes themselves, so that:

1. **Description and content are separable.** The same DID can carry different HATs in different contexts (different applications, different recipients, different versions of the same library) without conflict, and the bytes need not be re-encoded.
2. **HATs are transmissible.** Because a HAT is much smaller than the data it describes, it is a natural unit of exchange: in messaging, sharing a file means sending its HAT, while the bytes stay on DISK.
3. **Visibility is a user choice.** A HAT may be kept private as a personal index, shared with selected parties, or published openly. Privacy is enabled by encryption of the underlying bytes (recorded in the HAT's crypto group), not by the HAT being inherently private.
4. **Versioning, slicing, and encryption become first-class.** The relationships between original / previous / cipher / slice variants are recorded in HAT, not embedded in the bytes.
5. **Multi-replica strategies are explicit.** HAT records the list of DISK services that hold the content; the holder of a HAT — not any single platform — knows where the data lives.
6. **Application metadata is preserved without polluting the byte payload.** AIDs, PIDs, types, and ranks describe how the data is used, while the bytes remain untouched.

## Specification

### Definitions

**Data** — A finite byte sequence whose identity is its DID (double SHA-256 of the bytes; see FVEP2 ID).

**HAT (Hash Attribute Table)** — A structured record that describes one Data item. The HAT's `id` is the DID of the Data it describes; for cipher variants, see "Crypto group" below.

**Raw HAT** — A HAT whose `rawDid` field is null. It describes data in its original (unencrypted) form. In application contexts, raw HATs are the primary entries the user manipulates.

**Cipher HAT** — A HAT whose `rawDid` field is non-null. It describes the encrypted form of another piece of data; the `rawDid` points to the raw HAT that describes the original content.

**HAT Store** — Any database that maps DIDs to HAT records. A HAT store may be kept locally (a personal index of one user's data), shared by a group (a collaborative library), or exposed publicly (a catalog). HAT records are not part of the on-chain consensus; their visibility and synchronization are a deployment choice, not a property of the HAT format itself.

### Fields

A HAT is grouped into six logical sections. Every field except `id` is OPTIONAL; an implementation MAY omit fields that are not relevant to a given use case.

#### Identity

|Field|Type|Description|
|---|---|---|
|`id`|String|The DID of the data described by this HAT (64-char lowercase hex). Primary key.|

The `id` is computed from the byte content of the data, using the algorithm declared in `hAlg`. When the HAT itself needs an ID derived from its own content (e.g., before a DID is assigned), implementations MAY compute a sha256x2 over the HAT's serialized form as a fallback.

#### Basic group

|Field|Type|Description|
|---|---|---|
|`hAlg`|String|Hash algorithm used to compute the DID. Default `"sha256x2"` (double SHA-256).|
|`size`|Long|Size of the data in bytes.|
|`born`|Long|Timestamp (ms) when the HAT was created locally.|
|`last`|Long|Timestamp (ms) of the last access (read, write, or check) of the data through this HAT.|

#### Extension group

|Field|Type|Description|
|---|---|---|
|`name`|String|Human-readable name of the data.|
|`desc`|String|Free-form description.|
|`types`|List\<String\>|Type tags (e.g., MIME types, application-specific categories).|
|`aids`|List\<String\>|AIDs (Application IDs) that produced or claim this data (see FEIP15).|
|`pids`|List\<String\>|PIDs (Protocol IDs) that govern the data's structure or usage.|

#### Version group

|Field|Type|Description|
|---|---|---|
|`srcDid`|String|DID of the first version of this lineage. The original from which all later versions descend.|
|`preDid`|String|DID of the immediately previous version. The data this version was edited from.|

`srcDid == id` indicates that this HAT describes the lineage's original. A version chain is reconstructed by following `preDid` until `srcDid` is reached.

#### Slice group

|Field|Type|Description|
|---|---|---|
|`tDid`|String|DID of the **t**otal (whole) data, if this HAT describes a slice.|
|`tSize`|Long|Size of the whole data in bytes.|
|`offset`|Long|Byte offset of this slice within the whole data.|

A HAT with non-null `tDid` describes a slice; otherwise it describes a complete piece of data. The DID of the slice is the hash of the slice's bytes — it is independent of the whole-data DID, ensuring slices are themselves content-addressable.

#### Crypto group

|Field|Type|Description|
|---|---|---|
|`rawDid`|String|If this HAT describes a cipher, the DID of the raw (unencrypted) data. Null on raw HATs.|
|`key`|String|The symmetric key used for encryption (held only by parties authorized to decrypt).|
|`kCipher`|String|The encrypted form of the symmetric key, e.g., for transport to a recipient.|
|`Leaked`|Boolean|Whether the symmetric key has been leaked. Implementations MAY warn the user before reusing data whose `Leaked` is true.|
|`cipherIds`|List\<String\>|On a raw HAT: the list of DIDs of cipher variants known to encrypt this raw data.|

The crypto group makes the raw/cipher relation symmetric: from the cipher HAT, follow `rawDid` to find the original; from the raw HAT, read `cipherIds` to discover all known encrypted variants.

#### Management group

|Field|Type|Description|
|---|---|---|
|`rank`|Integer|User-assigned rank (importance, priority, etc.).|
|`state`|DataState|One of `ACTIVE`, `DELETED`, `OUTDATED`, `ARCHIVED`.|
|`locas`|List\<String\>|Locations where the bytes can be retrieved (e.g., `disk://<serviceId>`, local file URIs, mirror URLs).|

`DataState` values:

|Name|Number|Meaning|
|---|---|---|
|`ACTIVE`|1|The HAT (and its data) is in active use.|
|`DELETED`|0|The HAT is logically deleted; implementations MAY hide it from default views.|
|`OUTDATED`|2|A newer version exists; this HAT is kept for history.|
|`ARCHIVED`|3|Long-term archival; not expected to be touched in normal workflows.|

### JSON Representation

```json
{
  "id": "5df6e0e2761359d30a8275058e299fcc0381534545f55cf43e41983f5d4c9456",
  "hAlg": "sha256x2",
  "size": 524288,
  "born": 1715212800000,
  "last": 1715299200000,
  "name": "draft-contract.pdf",
  "desc": "Initial draft of the service agreement.",
  "types": ["application/pdf", "contract"],
  "aids": ["A1b2c3..."],
  "pids": ["P9x8y7..."],
  "srcDid": "5df6e0e2...",
  "preDid": null,
  "tDid": null,
  "tSize": null,
  "offset": null,
  "rawDid": null,
  "key": null,
  "kCipher": null,
  "Leaked": false,
  "cipherIds": ["a3f1...", "9b22..."],
  "rank": 1,
  "state": "ACTIVE",
  "locas": ["disk://FRk8...mZ", "disk://FQp2...nA"]
}
```

### Rules

1. **DID as primary key.** A HAT's `id` MUST equal the DID of the data it describes. Two HATs with the same `id` describe the same byte content; their other fields MAY differ when maintained in different contexts.

2. **HAT is content-free.** Implementations MUST NOT embed HAT fields inside the byte content stored in DISK. The byte payload is the data; the HAT is its description. HAT and DISK remain two independent stores connected only by the DID.

3. **Visibility is a deployment choice.** A HAT MAY be kept private (e.g., in a single user's app database), shared with selected parties (e.g., transmitted over MAIL/P2P/Room/DOCK), or published openly (e.g., as a catalog entry). The HAT format is the same in all three modes; only the channel and access control differ.

4. **Decoupled lifecycle.** A HAT MAY exist without the bytes being currently retrievable (e.g., the only DISK that held them is offline), and bytes MAY exist in DISK without any party holding a HAT for them. The two stores are independent.

5. **Raw / cipher symmetry.** When a cipher HAT is created with `rawDid = R`, implementations SHOULD also append the cipher's DID to the `cipherIds` list of the HAT whose `id == R`, if such a HAT is accessible. This keeps the raw → cipher mapping queryable from both sides.

6. **Privacy comes from encryption, not from the HAT being private.** A HAT MAY be transmitted in the clear if the data it describes is non-sensitive, or if the bytes themselves are encrypted (cipher HAT) and only authorized parties hold the symmetric key. The `key` field MUST NOT be transmitted to parties not authorized to decrypt; `kCipher` is the field designed to carry an encrypted form of `key` to a specific recipient.

7. **Access touches `last`.** Any operation that reads, writes, checks, or otherwise uses the data through this HAT SHOULD update `last` to the current timestamp. This enables LRU-style ordering and "recently used" views.

8. **`rawDid` distinguishes display sets.** Applications presenting a list of data SHOULD by default show only raw HATs (i.e., those with `rawDid == null`). Cipher HATs are bookkeeping records; they are reachable via the raw HAT's `cipherIds` list.

9. **Locations are hints, not guarantees.** `locas` records where the data was last seen; it is not a binding promise that the data is still retrievable from those locations. Clients MUST verify by attempting `disk.check` or `disk.get`.

10. **Version chain is acyclic.** `preDid` and `srcDid` together MUST NOT form a cycle. Implementations SHOULD reject a HAT update that would create one.

11. **Slice DIDs are independent.** The DID of a slice is computed from the slice's bytes; it does not derive from `tDid`. Slicing is a metadata claim, verifiable only by re-slicing the whole data and comparing DIDs.

### Lifecycle

A typical HAT lifecycle inside an application:

1. **Create** — A user or service prepares a piece of data, computes the DID, fills in `size`, `born`, `last`, `name`, optionally `desc`/`types`/`aids`/`pids`, sets `state = ACTIVE`, and saves the HAT.
2. **Sync to DISK** — The bytes are uploaded to one or more DISK services and each `disk://<serviceId>` is recorded in `locas`.
3. **Encrypt (optional)** — When the data is to be shared but kept confidential, the bytes are encrypted with a symmetric key. The cipher DID is computed, a cipher HAT is created (`rawDid` set, `kCipher` filled with the key encrypted for the intended recipient, `key` retained only by parties authorized to decrypt), and the cipher DID is appended to the raw HAT's `cipherIds`.
4. **Edit** — A new version is created by editing the bytes; a new HAT is added with `preDid` pointing to the old version's DID. The old HAT MAY be marked `OUTDATED`. `srcDid` is inherited from the previous HAT (or set to the previous DID if the previous was the source).
5. **Exchange** — A HAT MAY be sent to another party over a messaging channel (DOCK, MAIL, P2P, Room) or published in a catalog. The recipient inserts the HAT into their own HAT store and proceeds to retrieve and verify the bytes from DISK.
6. **Retrieve** — When the bytes are needed and not held locally, the holder of the HAT picks a `locas` entry and issues `disk.get`. The bytes are verified against the HAT's `id` (the DID); if encrypted, decrypted using `key` (recovered from `kCipher` if necessary). On success, `last` is updated.
7. **Refresh** — Periodic `disk.check` calls (see DISK protocol) are issued for HATs whose data should be kept alive on DISK.
8. **Delete** — A HAT is marked as `DELETED`; the bytes in DISK are not affected (DISK reclaims by its own expiry / no-one-uses-it-deletes-it rule).

### HAT Exchange in Messaging

The most important consequence of the HAT–DISK separation is that **data sharing reduces to HAT sharing**. The bytes need to be uploaded to DISK only once; thereafter, distributing the data to any number of recipients means distributing the HAT.

#### Why exchange the HAT, not the bytes

- **HATs are small.** A HAT for a 1 GB video is a few hundred bytes; the same 1 GB does not need to flow through every messaging channel.
- **Bytes already exist somewhere.** As long as the data is reachable through one of the DISK services in `locas`, every recipient can fetch it independently.
- **Verifiability is preserved.** The HAT's `id` is the DID; the recipient verifies the fetched bytes against it. The sender cannot substitute different content under the same HAT.
- **Encryption metadata travels with the description.** For confidential data, the cipher HAT carries `kCipher` (the symmetric key encrypted for the recipient). The recipient decrypts the symmetric key, fetches the cipher bytes from DISK, and decrypts them. The bytes themselves never need to be re-uploaded for each new recipient.
- **Multi-recipient delivery is cheap.** Sending a cipher HAT to 100 recipients in a Room is 100 small messages; the cipher bytes are uploaded to DISK once and fetched 100 times.

#### Recommended exchange pattern

For confidential data sent from sender `S` to recipient `R`:

1. `S` encrypts the raw bytes with a fresh symmetric key `K` and uploads the cipher bytes to one or more DISK services.
2. `S` constructs a cipher HAT: `id` = cipher DID, `rawDid` = raw DID, `kCipher` = `ECC-encrypt(K, R.pubkey)`, `locas` = the DISK services where the cipher bytes live, and any descriptive fields (`name`, `desc`, `types`).
3. `S` transmits the cipher HAT to `R` through a messaging channel (typically DOCK; see related protocols).
4. `R` receives the HAT, decrypts `kCipher` to recover `K`, fetches the cipher bytes from one of the `locas` services via `disk.get`, verifies the bytes hash to the HAT's `id`, and decrypts to obtain the raw bytes.

For non-confidential data, the same pattern applies with the cipher HAT replaced by a raw HAT and the `kCipher` step omitted.

#### Public catalogs

A HAT MAY also be published in a public catalog (e.g., a search index, a community board, an open dataset directory). In this mode the HAT is the entry seen by every reader; the bytes are fetched by interested parties on demand. Description fields (`name`, `desc`, `types`) are written for general consumption rather than for one specific recipient.

## Examples

### Example 1: Plain document

A user adds a PDF. The application stores:

```json
{
  "id": "5df6e0e2...456",
  "size": 524288,
  "born": 1715212800000,
  "last": 1715299200000,
  "name": "report.pdf",
  "types": ["application/pdf"],
  "state": "ACTIVE",
  "locas": ["disk://FRk8...mZ"]
}
```

The bytes live in one DISK service; the user's HAT store records the local meaning ("report.pdf") and the location.

### Example 2: Sending a confidential file by transmitting its HAT

Alice wants to send the report to Bob. She encrypts it with a fresh symmetric key `K`, computes the cipher DID `c0ffee...`, uploads the cipher bytes to DISK, and constructs a cipher HAT:

```json
{
  "id": "c0ffee...",
  "rawDid": "5df6e0e2...456",
  "kCipher": "<ECC-encrypt(K, BobPubkey)>",
  "size": 524400,
  "name": "report.pdf",
  "locas": ["disk://FRk8...mZ"],
  "state": "ACTIVE"
}
```

Alice transmits **the HAT** (not the bytes) to Bob via DOCK. The HAT is small enough to fit comfortably within DOCK's per-message limit. Bob inserts the HAT into his own store, recovers `K` from `kCipher`, calls `disk.get` against `disk://FRk8...mZ` for `c0ffee...`, verifies the returned bytes hash to `c0ffee...`, decrypts with `K`, and verifies the decrypted bytes hash to `5df6e0e2...456`. Alice also appends `c0ffee...` to her local raw HAT's `cipherIds` for bookkeeping.

The same pattern serves multi-recipient delivery: a Room with 100 members receives 100 HAT-sized DOCK messages, but the cipher bytes are uploaded to DISK only once and fetched on demand by each member.

### Example 3: Slice of a large file

A 1 GB video has DID `aaaa...`. The user keeps a 64 MB preview slice from offset 0:

```json
{
  "id": "bbbb...",
  "size": 67108864,
  "tDid": "aaaa...",
  "tSize": 1073741824,
  "offset": 0,
  "name": "video-preview",
  "state": "ACTIVE"
}
```

The slice's DID `bbbb...` is the hash of the 64 MB bytes; it is independently retrievable from DISK without the whole video.

### Example 4: Version chain

Three drafts of a document, in order:

```
v1: id = 1111...,  srcDid = 1111...,  preDid = null,    state = OUTDATED
v2: id = 2222...,  srcDid = 1111...,  preDid = 1111..., state = OUTDATED
v3: id = 3333...,  srcDid = 1111...,  preDid = 2222..., state = ACTIVE
```

Following `preDid` from v3 reconstructs the full edit history; `srcDid` always points to the original.

### Example 5: Multi-replica strategy

A critical document is stored on three DISK services:

```json
{
  "id": "9c01...",
  "name": "operating-agreement.pdf",
  "state": "ACTIVE",
  "locas": [
    "disk://FRk8...mZ",
    "disk://FQp2...nA",
    "disk://FXb7...vC"
  ]
}
```

If the first service goes offline, the holder of the HAT can issue `disk.get` against the second or third. The holder of the HAT — not any single DISK provider — knows where the data lives.

### Example 6: Public catalog entry

A community publishes a curated dataset of historical documents. Each document is stored on multiple DISK services, and the catalog publishes the corresponding HATs so that anyone can browse and retrieve:

```json
{
  "id": "f00d...",
  "size": 12582912,
  "born": 1715212800000,
  "name": "1947_treaty_text.pdf",
  "desc": "Original 1947 treaty text, scanned from the National Archives.",
  "types": ["application/pdf", "history", "treaty"],
  "state": "ACTIVE",
  "locas": [
    "disk://FRk8...mZ",
    "disk://FQp2...nA"
  ]
}
```

The HAT is public; anyone can see it, fetch the bytes from DISK, and verify them against the DID. The same HAT may sit in the public catalog and in the personal HAT stores of every reader who saves it.

## Versioning

|Version|Date|Changes|
|---|---|---|
|1|2026-05-09|Initial version. Defines HAT as a transmissible metadata record describing data identified by DID, with six field groups, the DISK ↔ HAT decoupling, and the messaging-as-HAT-exchange pattern.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FVEP0 FVEP|General rules for all FVEP protocols|
|FVEP1 Entity|HAT is an Object whose ID is a DID; it describes another Object's content|
|FVEP2 ID|Defines DID as the hash-based OID; HAT's `id` and `rawDid`/`preDid`/`srcDid`/`tDid`/`cipherIds` are all DIDs|
|FVEP3 Location|`locas` entries follow the Location notation (e.g., `disk://serviceId`)|
|FVEP4 Time|`born` and `last` use millisecond Unix timestamps|
|FVEP7 Signature|HAT records MAY be signed when shared between users|
|FVEP8 Encryption|Cipher HATs reference the encryption scheme used to produce the cipher DID from the raw DID|
|FEIP15 App|`aids` references registered Apps|

DISK (the open data storage component, defined as a FAPI component in the cryptoeconomic stack) is the byte-storage counterpart of HAT: DISK holds bytes addressed by DID; HAT holds attributes addressed by the same DID.

## Reference Implementation

- `FC-AJDK/src/main/java/com/fc/fc_ajdk/data/fcData/Hat.java` — HAT data class with all six field groups and the `DataState` enum.
- `Freer/app/src/main/java/com/fc/freer/manager/HatManager.java` — Singleton manager that maintains the user's local HAT store, provides paginated listing sorted by `last`, search across name/desc/types/aids/pids/DIDs/locas, and the raw ↔ cipher helpers (`createCipherHat`, `addCipherId`, `filterOutCipherHats`).
- `Freer/app/src/main/java/com/fc/freer/data/DataActivity.java` — User-facing data management screen built on `HatManager`; demonstrates HAT-driven listing, search, sort, encryption, and DISK upload/download flows.
