# FVEP1V1_Entity

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
|Title|Entity|
|Type|FVEP|
|SN|1|
|Version|1|
|Category|Foundation|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-21|
|PID||

## Abstract

Freeverse is an information universe. All entities in Freeverse are information. This protocol defines the fundamental entity model that underpins the entire Freeverse ecosystem. Every entity has an ID. There are two basic types of entities: **Subject** (an entity that possesses a private key and a public key) and **Object** (an entity that can be represented as a byte array).

## Motivation

A consistent entity model is the foundation of the Freeverse ecosystem. Without a shared definition of what constitutes an entity and how entities are identified, independent services and applications cannot interoperate reliably. This protocol establishes the common ground by defining:

1. What an entity is in Freeverse.
2. The two fundamental types of entities and their properties.
3. How each type of entity is identified.
4. How metadata is attached to entities.

## Specification

### Definitions

**Entity** — The most fundamental unit in Freeverse. An entity is a piece of information that has a unique identifier (`id`) and optional metadata (`meta`). All things in the Freeverse ecosystem — persons, services, protocols, transactions, documents, tokens — are entities.

**Subject** — An entity that possesses a cryptographic key pair (private key and public key). A subject can sign data, prove ownership, and initiate actions. In the current version, the key pair algorithm is **ECC on the secp256k1 curve**.

**Object** — An entity that can be represented as a byte array. An object is passive information — it does not possess keys and cannot sign. Its identity is derived from its content.

### Entity (Base)

Every entity in Freeverse has the following base structure:

|Field|Type|Required|Description|
|---|---|---|---|
|id|String|Y|The unique identifier of the entity|
|meta|Meta|N|Metadata describing the protocol context of this entity|

#### Meta

The `meta` field carries protocol context:

|Field|Type|Required|Description|
|---|---|---|---|
|p|String|N|Protocol ID — the identifier of the protocol that defines this entity type|
|v|String|N|Version — the version of the protocol|
|n|String|N|Name — the human-readable name of the entity type|

#### JSON Representation

```json
{
  "id": "<entity id>",
  "meta": {
    "p": "<protocol id>",
    "v": "<version>",
    "n": "<entity type name>"
  }
}
```

### Subject

A Subject extends Entity with cryptographic key pair capabilities.

|Field|Type|Required|Description|
|---|---|---|---|
|id|String|Y|The subjectId — currently FID (Freecash ID), derived from the public key (see FVEP2)|
|meta|Meta|N|Protocol metadata|
|pubkey|String|Y|The compressed public key in hex (33 bytes, secp256k1)|
|cid|String|N|The human-readable Crypto Identity name (registered via FEIP3)|
|usedCids|List\<String\>|N|Previously used CID names (max 4)|
|isNobody|Boolean|N|Whether this subject has been declared abandoned (via FEIP4)|

The private key (`prikey`) is **never** stored or transmitted as part of the entity data. It exists only in the subject's local environment.

#### Key Algorithm

In this version, the key pair algorithm is **Elliptic Curve Cryptography (ECC)** on the **secp256k1** curve, the same curve used by Bitcoin and Freecash.

|Property|Value|
|---|---|
|Curve|secp256k1|
|Private key length|32 bytes (256 bits)|
|Compressed public key length|33 bytes (prefix `02` or `03` + 32-byte X coordinate)|
|Uncompressed public key length|65 bytes (prefix `04` + 32-byte X + 32-byte Y)|

The FID derivation algorithm is defined in FVEP2 (ID).

#### Subject Capabilities

A Subject can:
- **Sign** data with its private key to prove identity and authorize actions.
- **Encrypt/Decrypt** data using ECC-based key exchange (ECDH).
- **Own** objects and other entities on the blockchain.
- **Initiate** transactions and protocol operations.

#### JSON Representation

```json
{
  "id": "FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUZ",
  "meta": {
    "n": "Subject"
  },
  "pubkey": "030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a",
  "cid": "CY_vpAv",
  "usedCids": [],
  "isNobody": false
}
```

### Object

An Object extends Entity with the ability to be serialized to a byte array.

|Field|Type|Required|Description|
|---|---|---|---|
|id|String|Y|The OID (Object ID) — see FVEP2 for ID derivation methods|
|meta|Meta|N|Protocol metadata|
|objName|String|N|A human-readable name for this object|

The OID derivation methods (Hash ID and Transformed ID) are defined in FVEP2 (ID).

#### Byte Representation

Every Object can be represented as a byte array. The specific serialization method depends on the concrete entity type or the protocol that defines it.

#### JSON Representation

```json
{
  "id": "b3a2e7c9f1d4a6b8e0c2d4f6a8b0c2d4e6f8a0b2c4d6e8f0a2b4c6d8e0f2a4",
  "meta": {
    "p": "FEIP8",
    "v": "1",
    "n": "Square"
  },
  "objName": "Freecash Community"
}
```

### Entity Type Hierarchy

```
FcEntity (base, id = EID)
├── FcSubject (has prikey + pubkey, id = FID)
│   └── e.g., Freer, CidInfo
└── FcObject (can be represented as bytes, id = OID)
    └── e.g., Block, Tx, Cash, Protocol, Service, Token, Square, ...
```

### Entity Classification

The following table lists the major entity types currently defined in the Freeverse ecosystem:

|Entity|Base Type|Category|Description|
|---|---|---|---|
|Freer|Subject|Identity|A Freecash user with CID, reputation, and on-chain history|
|Block|Object|Blockchain|A block in the Freecash blockchain|
|Tx|Object|Blockchain|A transaction|
|Cash|Object|Blockchain|A UTXO (unspent transaction output)|
|OpReturn|Object|Blockchain|An OP_RETURN record|
|Protocol|Object|Construct|A protocol definition (FEIP1)|
|Code|Object|Construct|A code reference (FEIP2)|
|Service|Object|Finance|A registered service (FEIP5)|
|App|Object|Finance|A registered application (FEIP15)|
|Contact|Object|Personal|Encrypted contact information (FEIP12)|
|Mail|Object|Personal|An encrypted message (FEIP7)|
|Secret|Object|Personal|A shared secret (FEIP17)|
|Box|Object|Personal|An encrypted data box (FEIP13)|
|Square|Object|Organize|A community square (FEIP19)|
|Team|Object|Organize|A consensus-governed team (FEIP18)|
|Token|Object|Finance|A deployed token (FEIP20)|
|Statement|Object|Publish|A signed statement (FEIP8)|
|Text|Object|Publish|Published text content (FEIP21)|
|Nid|Object|Identity|A named identifier (FEIP11)|
|Proof|Object|Finance|An on-chain proof (FEIP14)|

### Rules

1. Every entity in Freeverse MUST have a non-empty `id` (EID).

2. The `id` of a Subject is its subjectId (currently FID). The `id` of an Object is its OID. The derivation methods are defined in FVEP2 (ID).

3. Two entities with the same `id` and the same type are considered the same entity. The `id` MUST be unique within a given entity type.

4. The `meta` field is OPTIONAL. When present, it provides context about which protocol defines the entity, enabling generic tooling to interpret entities without prior knowledge of their specific types.

5. The key algorithm for Subject in this version is ECC secp256k1. Future versions of this protocol MAY introduce additional key algorithms.

6. The private key of a Subject MUST NOT be included in any serialized, transmitted, or stored entity representation (except in encrypted form for the subject's own backup purposes).

7. Objects MUST be representable as a byte array. The serialization method is determined by the concrete entity type or the protocol that defines it.

## Examples

### Creating a Subject

A Subject is created by generating a new ECC secp256k1 key pair:

```
1. Generate a random 32-byte private key using a cryptographically secure random number generator.
2. Derive the 33-byte compressed public key from the private key.
3. Derive the FID from the public key (see FVEP2 for the derivation algorithm).
4. The Subject now exists with:
   - id = FID
   - pubkey = compressed public key in hex
```

### Creating an Object

An Object is created when structured data is persisted (on-chain or off-chain):

```
1. Determine the OID (see FVEP2 for ID derivation methods):
   - If on-chain: the txid of the creating transaction (Transformed ID).
   - If off-chain: hash of the byte representation (Hash ID / DID).
2. The Object now exists with:
   - id = OID
   - meta = { protocol context }
   - (type-specific fields)
```

## Versioning

|Version|Date|Changes|
|---|---|---|
|1|2026-03-21|Initial version. Defines Entity, Subject, and Object.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FVEP0 FVEP|General rules for all FVEP protocols|
|FVEP2 ID|Defines the ID system for entities (builds on the `id` field defined here)|
|FVEP3 Name|Defines human-friendly names for entities|
|FVEP4 Location|Defines the location or path of entities|
|FEIP3 CID|Registers human-readable names for Subjects on-chain|
|FEIP4 Nobody|Declares a Subject as abandoned on-chain|

## Reference Implementation

- `FC-JDK/src/main/java/data/fcData/FcEntity.java` — Base entity class
- `FC-JDK/src/main/java/data/fcData/FcSubject.java` — Subject implementation
- `FC-JDK/src/main/java/data/fcData/FcObject.java` — Object implementation
- `FC-JDK/src/main/java/data/fcData/Meta.java` — Metadata class
- `FC-JDK/src/main/java/data/fcData/EntityProperty.java` — Entity type registry
- `FC-JDK/src/main/java/core/crypto/KeyTools.java` — Key derivation and address generation
