# FVEP2V1_ID

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
|Title|ID|
|Type|FVEP|
|SN|2|
|Ver|1|
|Category|Foundation|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-21|
|PID||

## Abstract

This protocol defines the identification system of the Freeverse ecosystem. Every entity has an ID (called EID in the general case). There are three fundamental ID levels corresponding to the entity type hierarchy: **EID** for any entity, **subjectId** for Subjects, and **OID** for Objects. IDs are further classified into three kinds by their derivation method: **Hash IDs**, **Human-readable IDs**, and **Transformed IDs**.

## Motivation

A consistent identification system is essential for the Freeverse ecosystem. Entities are referenced across protocols, APIs, databases, and user interfaces. Without a formal definition of how IDs are derived, named, and classified, interoperability breaks down. This protocol establishes:

1. The three fundamental ID levels and their relationship to the entity hierarchy.
2. The three kinds of ID derivation methods.
3. The naming convention for entity-specific ID types.
4. A set of reserved short ID type names.

## Specification

### Fundamental IDs

The entity hierarchy defined in FVEP1 gives rise to three fundamental ID levels:

|ID|Full Name|Entity Type|Description|
|---|---|---|---|
|EID|Entity ID|Entity (base)|The general identifier of any entity in Freeverse. Every entity has an EID.|
|subjectId|Subject ID|Subject|The identifier of a Subject. FID is the current subjectId for the Freecash blockchain.|
|OID|Object ID|Object|The identifier of an Object. OID is a specific kind of EID.|

```
EID (Entity ID)
├── subjectId — for Subjects
│   ├── FID (Freecash ID) — hash-based subjectId on the Freecash blockchain (secp256k1)
│   ├── CID (Crypto ID)   — human-readable subjectId (via FEIP3)
│   └── (future subjectIds for other blockchains or algorithms)
└── OID (Object ID) — for Objects
    ├── DID (Data ID)      — hash-based OID (double SHA-256 of bytes)
    ├── Transformed ID     — txid-based OID (e.g., SID, AID, PID, codeId, ...)
    └── NID (Named ID)     — human-readable OID (via FEIP11)
```

In the current version, the only subjectId implementation is **FID** (Freecash ID), derived from an ECC secp256k1 pubkey. Future versions MAY introduce additional subjectId types for other blockchains or key algorithms.

### ID Structure

```
                             ┌──────────────────────┐
                             │     EID (Entity ID)  │
                             └──────┬───────────┬───┘
                                    │           │
                    ┌───────────────▼──┐   ┌────▼───────────────┐
                    │    subjectId     │   │       OID          │
                    │   (Subject ID)   │   │   (Object ID)      │
                    └──┬───────────┬───┘   └──┬───────┬────────┬┘
                       │           │          │       │        │
               ┌───────▼────┐ ┌────▼────┐ ┌───▼──┐ ┌──▼───┐ ┌──▼──────┐
               │    FID     │ │   CID   │ │ DID  │ │transf│ │ NID     │
               │(Freecash   │ │(Crypto  │ │(Data │ │ormed │ │(Name ID)│
               │   ID)      │ │  ID)    │ │  ID) │ │ ID   │ │         │
               │            │ │         │ │      │ │      │ │         │
               │(secp256k1) │ │  FID    │ │  of  │ │      │ │human    │
               │  hash of   │ │ human-  │ │double│ │reuse │ │readable │
               │  pubkey    │ │readable │ │sha256│ │txid  │ │OID      │
               │            │ │(FEIP3)  │ │bytes │ │      │ │(FEIP11) │
               └────────────┘ └─────────┘ └──────┘ └──────┘ └─────────┘
                                                             
                                                           
```

### Three Kinds of ID

IDs in Freeverse are classified into three kinds based on how they are derived:

#### 1. Hash ID

A Hash ID is derived by applying a hash function to the entity's intrinsic data. Hash IDs are deterministic, collision-resistant, and content-addressable.

**FID** — The identifier of a Subject, derived from its public key:

```
pubkey (33 bytes, compressed, secp256k1)
  → SHA-256
  → RIPEMD-160
  → hash160 (20 bytes)
  → prepend version byte 0x23
  → SHA-256 × 2, take first 4 bytes as checksum
  → append checksum
  → Base58 encode
  → FID (a Base58Check string starting with 'F')
```

This is the same address derivation used by the Freecash blockchain, ensuring that a Subject's FID is its native blockchain address.

**DID (Data ID)** — A Hash ID for an Object, derived from its byte content:

```
bytes (the byte array representation of the Object)
  → SHA-256 × 2 (double SHA-256)
  → hex encode
  → DID (a 64-character hex string)
```

DID is used when the Object's identity is determined by its content (content-addressable).

#### 2. Human-readable ID

A Human-readable ID is a name registered on-chain that provides a human-friendly way to refer to an entity. It is designed for human use — easy to read, remember, and communicate.

|ID|Full Name|Is a|Protocol|Description|
|---|---|---|---|---|
|CID|Crypto ID|subjectId|FEIP3|A human-readable FID. Format: `{name}_{suffix}`, where suffix is derived from the FID.|
|NID|Named ID|OID|FEIP11|A human-readable OID. A named identifier registered by a Subject that points to an Object.|

CID is a kind of subjectId — it is the human-readable form of FID. NID is a kind of OID — it is the human-readable form of an Object's identifier. They coexist with their hash-based or txid-based counterparts rather than replacing them.

#### 3. Transformed ID

A Transformed ID is an ID that reuses an existing identifier (typically a transaction ID) as the entity's ID. This is the most common kind of OID in the Freeverse ecosystem.

When an entity is created by a FEIP on-chain operation, the **txid** of the creating transaction is used as the entity's ID. Some entity types have reserved short names (see below); others follow the naming convention defined in [ID Naming Convention](#id-naming-convention).

### Reserved ID Type Names

The following short ID type names are reserved in the Freeverse ecosystem. They SHOULD be used consistently in protocols, APIs, code, and documentation.

|Name|Full Name|Description|
|---|---|---|
|AID|App ID|The identifier of an App entity (txid of FEIP15 publish)|
|BID|Box ID|The identifier of a Box entity (txid of FEIP13 create)|
|CID|Crypto ID|A human-readable subjectId (human-readable FID, registered via FEIP3)|
|DID|Data ID|A content-hash-based identifier of an Object|
|EID|Entity ID|The general identifier of any entity|
|FID|Freecash ID|A subjectId for the Freecash blockchain, derived from its pubkey (secp256k1)|
|IID|Internal ID|An identifier used internally within a system or service|
|NID|Named ID|A human-readable OID (registered via FEIP11)|
|OID|Object ID|The general identifier of an Object|
|PID|Protocol ID|The identifier of a Protocol entity (txid of FEIP1 publish)|
|SID|Service ID|The identifier of a Service entity (txid of FEIP5 publish)|
|TID|Team ID|The identifier of a Team entity (txid of FEIP18 create)|

### ID Naming Convention

For entity types that do not have a reserved short name, the ID type name follows the convention:

```
<entityTypeName> + Id
```

The `entityTypeName` is generally the value of the `meta.n` field (the name field in the entity's metadata, as defined in FVEP1). It is written in camelCase. Examples:

|Entity Type|ID Type Name|
|---|---|
|Code|codeId|
|Square|squareId|
|Token|tokenId|
|Statement|statementId|
|Text|textId|
|Proof|proofId|
|Remark|remarkId|

### Typed ID Notation

> **Note**: "Typed ID" (a *notation*) is distinct from "Transformed ID" (a *derivation kind*). A Transformed ID is one **kind** of OID — produced by reusing a txid. A Typed ID is a **way of writing** any 64-char hex OID with a `(type)` prefix so its type is unambiguous. A DID (Hash ID) written as `(DID)b3a2…` is a Hash-derived OID rendered in Typed ID notation.

DID, txid, and all txid-based OIDs share the same 64-character hex string format. To disambiguate, a **Typed ID** notation is defined:

```
(type)value
```

For example:
- `(codeId)eed190e4f003fc61bcd71d19da2a940d49bbf6bdcca05409c062576a92e7e361` — an ID of a Code entity
- `(SID)a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90` — an ID of a Service entity
- `(PID)f6e5d4c3b2a1908172635445362718091a2b3c4d5e6f7081920a1b2c3d4e5f60` — an ID of a Protocol entity
- `(DID)b3a2e7c9f1d4a6b8e0c2d4f6a8b0c2d4e6f8a0b2c4d6e8f0a2b4c6d8e0f2a4b6` — a content-hash ID of a Data Object

#### Scope

The Typed ID notation applies to any **OID that is rendered as a 64-character hex string** — this includes both **txid-based OIDs** (SID, AID, PID, codeId, …) and **Hash-based OIDs** (DID). Because they share the same raw format, they are ambiguous without context and benefit from the `(type)value` prefix.

The notation is NOT used for human-readable IDs (CID, NID) or for non-OID identifiers, since their formats are already self-distinguishing.

#### When to Use

- **Use the `(type)` prefix** when the ID appears outside its own entity context and the type cannot be determined from surrounding information — for example, in cross-references, logs, mixed-type lists, or documentation.
- **Omit the `(type)` prefix** when the type of the ID can be determined from the context — for example, within the entity's own `id` field, or in a field whose name already indicates the type (e.g., a field named `codeId`).

#### Parsing

A Typed ID string can be parsed by splitting at the `)` character:

```
(codeId)eed190e4... → { type: "codeId", value: "eed190e4..." }
```

If no `(` prefix is present, the value is an untyped ID whose type must be inferred from context.

### ID Properties

|Property|Hash ID|Human-readable ID|Transformed ID|
|---|---|---|---|
|Derivation|Hash of intrinsic data|On-chain registration|Reuse of txid|
|Deterministic|Yes|No (user-chosen name)|Yes (from txid)|
|Human-friendly|No|Yes|No|
|Uniqueness|Guaranteed by hash|Guaranteed by suffix mechanism|Guaranteed by txid uniqueness|
|Mutability|Immutable|CID can be changed; NID can be updated|Immutable|
|Applies to|subjectId (FID), OID (DID)|subjectId (CID), OID (NID)|OID (SID, AID, PID, codeId, ...)|

### Rules

1. Every entity in Freeverse MUST have a non-empty EID.

2. The EID of a Subject is its subjectId. In the current version, the only subjectId is FID, derived from the pubkey using ECC secp256k1. Future versions MAY introduce additional subjectId types for other blockchains or key algorithms.

3. The EID of an Object is its OID. The specific derivation method depends on the entity type — either a Hash ID (DID) or a Transformed ID (txid-based).

4. For on-chain entities created by FEIP operations, the txid of the creating transaction SHOULD be used as the OID (Transformed ID).

5. For off-chain entities, the double SHA-256 hash of the byte representation SHOULD be used as the OID (Hash ID / DID).

6. CID is a human-readable subjectId (human-readable FID). NID is a human-readable OID. They coexist with their hash-based or txid-based counterparts.

7. Reserved ID type names (AID, BID, CID, DID, EID, FID, IID, NID, OID, PID, SID, TID) MUST be used consistently across protocols, APIs, and code. New three-letter `*ID` names SHOULD NOT conflict with reserved names.

8. For entity types without a reserved short name, the ID type name SHOULD follow the `<entityTypeName>Id` convention, where `entityTypeName` corresponds to `meta.n`.

9. OIDs rendered as a 64-character hex string (both txid-based OIDs and Hash-based DIDs) SHOULD use the Typed ID notation `(type)value` when the ID appears outside its own entity context and the type cannot be determined from surrounding information. When the type can be determined from context, the `(type)` prefix SHOULD be omitted.

## Examples

### Hash ID: FID derivation

```
Private key (32 bytes):  e.g., randomly generated
  ↓ ECC secp256k1
Public key (33 bytes):   030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a
  ↓ SHA-256 → RIPEMD-160
hash160 (20 bytes):      e.g., 3a2e7c9f...
  ↓ prepend 0x23, append 4-byte checksum
  ↓ Base58 encode
FID:                     FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUZ
```

### Hash ID: DID derivation

```
Object byte content:     {"name":"Freecash Community","desc":"..."}
  ↓ SHA-256 × 2 (double SHA-256)
DID:                     b3a2e7c9f1d4a6b8e0c2d4f6a8b0c2d4e6f8a0b2c4d6e8f0a2b4c6d8e0f2a4b6
```

### Human-readable ID: CID

```
FID:                     FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV
Chosen name:             Alice
Suffix (last 4 of FID):  kUV
CID:                     Alice_kUV
```

### Transformed ID: Service ID (SID)

```
FEIP5 publish transaction txid:  a1b2c3d4e5f6...
SID of the created Service:      a1b2c3d4e5f6...  (same as txid)
```

### Full-form ID: codeId

```
FEIP2 publish transaction txid:  f6e5d4c3b2a1...
codeId of the created Code:      f6e5d4c3b2a1...  (same as txid)
```

### Typed ID notation

A raw hex string is ambiguous — it could be a txid, a codeId, a SID, etc. The Typed ID notation resolves this:

```
Without type (ambiguous):   eed190e4f003fc61bcd71d19da2a940d49bbf6bdcca05409c062576a92e7e361
With type (unambiguous):    (codeId)eed190e4f003fc61bcd71d19da2a940d49bbf6bdcca05409c062576a92e7e361
```

When the context makes the type clear, omit the prefix:

```
In a "codeId" field:        eed190e4f003fc61bcd71d19da2a940d49bbf6bdcca05409c062576a92e7e361
In a mixed-type list:       (codeId)eed190e4f003fc61bcd71d19da2a940d49bbf6bdcca05409c062576a92e7e361
```

## Versioning

|Version|Date|Changes|
|---|---|---|
|1|2026-03-21|Initial version. Defines the three fundamental IDs, three kinds of ID, reserved names, and naming convention.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FVEP0 FVEP|General rules for all FVEP protocols|
|FVEP1 Entity|Defines the entity model (Entity, Subject, Object) that this ID system identifies|
|FVEP3 Name|Defines human-friendly names for entities (builds on top of IDs)|
|FEIP3 CID|On-chain protocol for registering human-readable CIDs for Subjects|
|FEIP11 NID|On-chain protocol for registering named identifiers|

## Reference Implementation

- `FC-JDK/src/main/java/data/fcData/FcEntity.java` — Base entity with `id` field (EID)
- `FC-JDK/src/main/java/data/fcData/FcSubject.java` — Subject with FID derivation from pubkey
- `FC-JDK/src/main/java/data/fcData/FcObject.java` — Object with OID (DID)
- `FC-JDK/src/main/java/core/crypto/KeyTools.java` — `pubkeyToFchAddr()` implements FID derivation
- `FC-JDK/src/main/java/data/feipData/NidOpData.java` — NID operation data
