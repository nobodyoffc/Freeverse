# FEIP12V3_Contact

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
|Title|Contact|
|Type|FEIP|
|SN|12|
|Version|3|
|Category|Personal|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-22|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

The Contact protocol lets a Subject (FID) store **encrypted** address-book entries on-chain. Each entry is one **Contact** entity: the chain holds opaque `cipher` (and optional `alg` hint); only the owner can decrypt private fields such as titles, memo, and preferences. Operations **add**, **update**, **delete**, and **recover** manage the lifecycle. Ciphertext format and encryption types follow **FVEP8**; concrete algorithms are defined in **FTSP**.

## Motivation

- **Privacy**: Contact details are not public; they are encrypted to the owner’s keys (or a chosen scheme per FVEP8).
- **Portability**: Standard FEIP envelope + indexable entity `id` (txid of the add operation).
- **Separation from public identity**: The contact’s **CID** and **noticeFee** are read from the blockchain ([FEIP3](FEIP3V4_CID.md), [FEIP10](FEIP10V1_NoticeFee.md)) by FID; they MUST NOT be placed inside the encrypted payload. Private labels (titles, memo, flags) stay inside `cipher`.

## Specification

### Contact entity

Contact has 3 kinds of fields:

**Feilds from on-chain operations**
|Field|Source|Description|
|---|---|---|
|`id`|Add txid|Unique entity id (same as the transaction id of the `add` operation).|
|`owner`|Signer FID|Subject who owns the contact record.|
|`birthTime`|Block time|When the contact was first added.|
|`birthHeight`|Block height|Block height at add.|
|`lastHeight`|Block height|Updated on every operation affecting this entity.|
|`active`|Boolean|`true` unless **delete** set it `false`; **recover** sets `true`.|
|`alg`|Optional string|Algorithm hint for decrypting `cipher` (e.g. `AlgorithmId` display name). MAY be omitted; parsers still require `cipher` for **add** / **update**.|
|`cipher`|String|Encrypted payload. SHOULD be a **FVEP8** JSON ciphertext string (`CryptoDataStr` / `CryptoDataByte` serialized as JSON or, for legacy, Base64 bundle / Bitcore).|

**Feilds from decrypted plaintext** (`cipher` after decryption) SHOULD be a JSON object with the recommended fields:

|Field|Type|Description|
|---|---|---|
|`fid`|String|Contact’s FID (see FVEP2); binds the entry to a Subject.|
|`pubkey`|String|Optional hex pubkey if the client stores it for convenience (may also be resolved from chain).|
|`titles`|Array of strings|User-defined labels.|
|`memo`|String|Free-form note.|
|`seeStatement`|Boolean|Whether to surface statements from this contact (client convention).|
|`seeWritings`|Boolean|Whether to surface writings (client convention).|

**Feilds from other protocols** :

|Field|Source instead|
|---|---|
|`cid`|Resolve from [FEIP3](FEIP3V4_CID.md) for the contact’s `fid` (Freer / CID index).|
|`noticeFee`|Resolve from [FEIP10](FEIP10V1_NoticeFee.md) for the contact’s `fid`.|


### `data` field — operations

The `op` field uses **lowercase** strings: `add`, `update`, `delete`, `recover` (as accepted by the reference parser).

#### 1. add

Creates a new contact. **`cipher` is required.**

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"add"`|
|`cipher`|Y|String|Encrypted contact detail (FVEP8).|
|`alg`|N|String|Optional algorithm hint.|

**Consensus rules**

1. `cipher` MUST NOT be null or empty.
2. Entity `id` MUST be set to the current transaction id (`txid`).
3. `owner` MUST be the transaction signer (first-input FID).
4. `active` MUST be `true`.
5. `birthTime`, `birthHeight`, and `lastHeight` MUST be set from the containing block/tx context.

#### 2. update

Replaces ciphertext for an existing contact.

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"update"`|
|`contactId`|Y|String|Existing contact entity `id` (txid of original **add**).|
|`cipher`|Y|String|New encrypted payload.|
|`alg`|N|String|Optional algorithm hint.|

**Consensus rules**

6. `contactId` MUST reference an existing contact.
7. Indexed contact’s `owner` MUST equal the transaction signer.
8. Contact MUST be **active** (`active == true`).
9. `cipher` MUST NOT be null or empty.
10. `lastHeight` MUST be updated to the current operation height.

#### 3. delete

Soft-deactivates one or more contacts.

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"delete"`|
|`contactIds`|Y|Array of strings|List of contact `id` values to deactivate.|

**Consensus rules**

11. `contactIds` MUST be non-null and non-empty.
12. All listed ids MUST exist in the index; otherwise the operation fails (reference parser rejects empty result).
13. For each matched entity, `active` MUST be set `false` and `lastHeight` updated.

**Security note:** The reference parser does **not** verify that each contact’s `owner` equals the signer before deactivating. Implementations **SHOULD** enforce **owner == signer** for every id in `contactIds` (recommended fix for production indexers).

#### 4. recover

Re-activates previously deleted contacts.

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"recover"`|
|`contactIds`|Y|Array of strings|List of contact `id` values to reactivate.|

**Consensus rules**

14. Same shape as **delete**, but `active` MUST be set `true`.

Same **owner** verification **SHOULD** apply as for **delete**.

### OP_RETURN envelope

```json
{
  "type": "FEIP",
  "sn": "12",
  "ver": "3",
  "name": "Contact",
  "data": { }
}
```

`data` contains one of the operation payloads above plus `op`.

### Encryption (FVEP8)

- The string in `cipher` SHOULD conform to [FVEP8V1_Encryption](../FVEP/FVEP8V1_Encryption.md): JSON with `type`, `alg`, `iv`, `cipher` (Base64 inner ciphertext), etc., or a recognized legacy form (Base64 bundle starting with algorithm-specific prefix, or Bitcore-compatible ciphertext) as implemented in `Contact.parseDetail` / `Decryptor`.
- Only the **owner** (or holder of the decryption key chosen at encryption time) can recover plaintext.

### Parsing order

- Parsing order follows [FEIP0](FEIP0V1_FEIP.md) (block height, then tx index).

## Examples

### Example 1 — add

```json
{
  "type": "FEIP",
  "sn": "12",
  "ver": "3",
  "name": "Contact",
  "data": {
    "op": "add",
    "alg": "EccK1AesGcm256@No1_NrC7",
    "cipher": "<FVEP8 JSON string or legacy ciphertext>"
  }
}
```

### Example 2 — update

```json
{
  "type": "FEIP",
  "sn": "12",
  "ver": "3",
  "name": "Contact",
  "data": {
    "op": "update",
    "contactId": "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90",
    "cipher": "<new FVEP8 ciphertext>"
  }
}
```

### Example 3 — delete

```json
{
  "type": "FEIP",
  "sn": "12",
  "ver": "3",
  "name": "Contact",
  "data": {
    "op": "delete",
    "contactIds": [
      "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
    ]
  }
}
```

### Example 4 — recover

```json
{
  "type": "FEIP",
  "sn": "12",
  "ver": "3",
  "name": "Contact",
  "data": {
    "op": "recover",
    "contactIds": [
      "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
    ]
  }
}
```

### Example 5 — decrypted plaintext (conceptual)

Only fields that belong **inside** `cipher` (no `cid`, no `noticeFee`):

```json
{
  "fid": "FEk41MvNLA85EqYuSWpkXUrgs9UGkdhmDLF",
  "pubkey": "02…",
  "titles": ["friend", "dev"],
  "memo": "Met at conference",
  "seeStatement": true,
  "seeWritings": true
}
```

**After merge** with chain data for that `fid`, a UI might show `cid: "Alice_kUV"` from [FEIP3](FEIP3V4_CID.md) and `noticeFee` from [FEIP10](FEIP10V1_NoticeFee.md); those are not decrypted from this blob.

## Versioning

|Version|Date|Summary|
|---|---|---|
|3|2026-03-22|Documented specification aligned with `Feip.FeipProtocol.CONTACT` (`12`/`3`) and `PersonalParser.parseContact`.|
|2|—|Prior on-chain usage (not documented in this repo).|
|1|—|Prior on-chain usage (not documented in this repo).|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FEIP0|General FEIP rules, OP_RETURN, CDD.|
|FEIP3|On-chain CID for the contact’s FID — **not** inside Contact `cipher`.|
|FEIP10|On-chain notice fee for the contact’s FID — **not** inside Contact `cipher`.|
|FVEP1 / FVEP2|Subject (owner) and FID.|
|FVEP8|Encryption envelope for `cipher`.|
|FTSP|Concrete algorithms (`AlgorithmId`, ECDH + AEAD).|

## Reference Implementation

|Component|Location|
|---|---|
|`Contact`| [FC-JDK/src/main/java/data/feipData/Contact.java](../../FC-JDK/src/main/java/data/feipData/Contact.java) |
|`ContactOpData`| [FC-JDK/src/main/java/data/feipData/ContactOpData.java](../../FC-JDK/src/main/java/data/feipData/ContactOpData.java) |
|`PersonalParser.parseContact`| [FEIP/FeipParser/src/main/java/personal/PersonalParser.java](../../FEIP/FeipParser/src/main/java/personal/PersonalParser.java) |
|`PersonalRollbacker.rollback`| [FEIP/FeipParser/src/main/java/personal/PersonalRollbacker.java](../../FEIP/FeipParser/src/main/java/personal/PersonalRollbacker.java) |
|`FeipProtocol.CONTACT`| [FC-JDK/src/main/java/data/feipData/Feip.java](../../FC-JDK/src/main/java/data/feipData/Feip.java) |

### Implementation notes (non-normative)

- **`ContactOpData.OP_FIELDS`**: The static initializer registers `ADD` twice; the second entry (with `contactId`) overwrites the first. Prefer a single row per op key.
- **`PersonalParser` — delete/recover**: Consider adding `owner == signer` checks for each id (recommended for security).
- **`Contact.getInputFieldDefaultValueMap`**: `new ArrayList<>().add("")` does not put an empty list in the map; use `List.of("")` or `Collections.singletonList("")` if fixing the map.
