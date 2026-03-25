# FEIP17V3_Secret

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
|Title|Secret|
|Type|FEIP|
|SN|17|
|Version|3|
|Category|Personal|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-22|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

The Secret protocol lets a Subject (FID) store **encrypted** personal secrets on-chain (passwords, keys, notes, or other sensitive strings). Each record is one **Secret** entity: the index holds opaque `cipher` (and optional `alg`); only the holder of the decryption key can recover plaintext. Ciphertext follows **FVEP8**; algorithms are in **FTSP**. 

## Motivation

- **Privacy**: Secret material is not readable from the chain without the owner’s keys.
- **Self-custody**: Same FEIP envelope and `id` = add **txid** pattern as other personal FEIPs.

## Specification

### Secret entity

**Fields from on-chain operations**

|Field|Source|Description|
|---|---|---|
|`id`|Add txid|Unique entity id (transaction id of the **add** operation).|
|`owner`|Signer FID|Subject who owns the secret record.|
|`birthTime`|Block time|When the secret was first added.|
|`birthHeight`|Block height|Block height at add.|
|`lastHeight`|Block height|Updated on every operation affecting this entity.|
|`active`|Boolean|`true` unless **delete** set it `false`; **recover** sets `true`.|
|`alg`|Optional string|Algorithm hint for decrypting `cipher` (e.g. `AlgorithmId` display name).|
|`cipher`|String|Encrypted payload (FVEP8 JSON, Base64 bundle, or legacy Bitcore — see [`Secret.parseDetail`](../../FC-JDK/src/main/java/data/feipData/Secret.java)).|

**Fields from decrypted plaintext** (JSON inside the outer `cipher` after decryption)

|Field|Type|Description|
|---|---|---|
|`type`|String|User-defined category or kind of secret (e.g. `password`, `seed`).|
|`title`|String|Short label for the secret.|
|`content`|String|Optional cleartext secret value.|
|`memo`|String|Optional note.|

Implementations SHOULD keep all sensitive bytes inside the outer on-chain `cipher`. The **`type`**, **`title`**, and **`memo`** in plaintext JSON are only “private” relative to the chain because they sit inside that blob; clients MAY leave `title`/`type` minimal if even metadata must stay hidden.

### `data` field — operations

The `op` field uses **lowercase** strings: `add`, `update`, `delete`, `recover` (reference: `PersonalParser.parseSecret`).

#### 1. add

Creates a new secret.

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"add"`|
|`cipher`|Y*|String|Encrypted secret payload (FVEP8).|
|`alg`|N|String|Optional algorithm hint.|

\*Exactly one of **`cipher`** MUST be non-null; the parser sets `secret.cipher` from `cipher`. If both are missing, the operation fails.

**Consensus rules**

1. Entity `id` MUST be the current transaction id.
2. `owner` MUST be the signer.
3. `active` MUST be `true`.
4. `birthTime`, `birthHeight`, and `lastHeight` MUST be set from block/tx context.

#### 2. update

Replaces the stored ciphertext.

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"update"`|
|`secretId`|Y|String|Existing secret `id` (txid of original **add**).|
|`cipher`|Y|String|New encrypted payload.|
|`alg`|N|String|Optional algorithm hint.|

**Consensus rules**

5. `secretId` MUST reference an existing secret.
6. `owner` MUST equal the signer.
7. Secret MUST be **active**.
8. `cipher` MUST NOT be null.
9. `lastHeight` MUST be updated.

#### 3. delete

Soft-deactivates one or more secrets.

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"delete"`|
|`secretIds`|Y|Array of strings|Ids to deactivate.|

**Consensus rules**

10. `secretIds` MUST be non-null and non-empty.
11. Entries whose `owner` is not the signer are **removed from the batch** (reference); if none remain, the operation fails.
12. For each remaining entity, set `active` to `false` and update `lastHeight`.

#### 4. recover

Re-activates deleted secrets.

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"recover"`|
|`secretIds`|Y|Array of strings|Ids to reactivate.|

**Consensus rules**

13. Same as **delete**, but `active` MUST be `true`, with the same **owner** filtering.

### OP_RETURN envelope

```json
{
  "type": "FEIP",
  "sn": "17",
  "ver": "3",
  "name": "Secret",
  "data": { }
}
```

### Encryption (FVEP8)

- On-chain `cipher` SHOULD conform to [FVEP8V1_Encryption](../FVEP/FVEP8V1_Encryption.md), same patterns as Contact: `CryptoDataStr` JSON, Base64 bundle, or Bitcore fallback (`Secret.parseDetail` / `Decryptor`).

### Parsing order

- Parsing order follows [FEIP0](FEIP0V1_FEIP.md) (block height, then tx index).

## Examples

### Example 1 — add (using `cipher`)

```json
{
  "type": "FEIP",
  "sn": "17",
  "ver": "3",
  "name": "Secret",
  "data": {
    "op": "add",
    "alg": "EccK1AesGcm256@No1_NrC7",
    "cipher": "<FVEP8 ciphertext of Secret JSON>"
  }
}
```

### Example 2 — update

```json
{
  "type": "FEIP",
  "sn": "17",
  "ver": "3",
  "name": "Secret",
  "data": {
    "op": "update",
    "secretId": "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90",
    "cipher": "<new FVEP8 ciphertext>"
  }
}
```

### Example 3 — delete / recover

Same shape as [FEIP12V3_Contact](FEIP12V3_Contact.md) but field name **`secretIds`** and **`sn` / `name`** for Secret.

### Example 4 — decrypted plaintext (conceptual)

```json
{
  "type": "password",
  "title": "Exchange API",
  "content": "Content",
  "memo": "Rotate quarterly"
}
```

## Versioning

|Version|Date|Summary|
|---|---|---|
|3|2026-03-22|Documented to match `Feip.FeipProtocol.SECRET` (`17`/`3`) and `PersonalParser.parseSecret`.|
|2|—|Prior usage (not documented in this repo).|
|1|—|Prior usage (not documented in this repo).|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FEIP0|OP_RETURN, parsing order, CDD.|
|FEIP12|Parallel personal protocol (Contact); same operation pattern and FVEP8 usage.|
|FVEP1 / FVEP2|Owner as Subject / FID.|
|FVEP8|Encryption envelope for `cipher`.|
|FTSP|Concrete algorithms.|

## Reference Implementation

|Component|Location|
|---|---|
|`Secret`| [FC-JDK/src/main/java/data/feipData/Secret.java](../../FC-JDK/src/main/java/data/feipData/Secret.java) |
|`SecretOpData`| [FC-JDK/src/main/java/data/feipData/SecretOpData.java](../../FC-JDK/src/main/java/data/feipData/SecretOpData.java) |
|`PersonalParser.parseSecret`| [FEIP/FeipParser/src/main/java/personal/PersonalParser.java](../../FEIP/FeipParser/src/main/java/personal/PersonalParser.java) |
|`FeipProtocol.SECRET`| [FC-JDK/src/main/java/data/feipData/Feip.java](../../FC-JDK/src/main/java/data/feipData/Feip.java) |

