# FEIP13V1_Box

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
|Title|Box|
|Type|FEIP|
|SN|13|
|Version|1|
|Category|Personal|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-23|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

The **Box** protocol defines **virtual containers** for personal data on-chain. Each Box is a named record owned by one FID. Payload may be stored in two complementary ways:

- **`contain`** — a **plaintext** JSON value (object, array, string, number, boolean, or structured nesting) visible to anyone who reads the chain or index.
- **`cipher`** — an **encrypted** string (typically **FVEP8** `CryptoDataStr` JSON or compatible serialization) for private payload; optional **`alg`** hints the decryption profile.

Operations **create**, **update**, **drop**, and **recover** manage Box entities and lifecycle. Box **`id`** (and **`bid`** in operations) is the transaction id of the **create** operation.

## Motivation

- **Flexible personal storage**: One protocol for small structured data that may be **public** (`contain`), **private** (`cipher`), **both** (e.g. public index + encrypted body), or **neither** (metadata-only box with `name` / `desc` only).
- **Single owner**: Clear **`owner`** FID; only the owner can **update**, **drop**, or **recover** (per reference consensus).
- **Alignment with FEIP**: Same envelope as other personal protocols; indexable **`id`** stable across updates.

## Specification

### Payload: `contain` vs `cipher` (normative)

|Field|Encryption|Visibility|Typical use|
|---|---|---|---|
|`contain`|None|Public on-chain / in index|Non-sensitive structure, tags, public metadata, or app-defined JSON.|
|`cipher`|Yes (per FVEP8 + FTSP)|Opaque on-chain; cleartext only after decrypt|Secrets, personal notes, or any data that MUST NOT be public.|

**Rules**

1. **`contain`** MUST be a JSON value allowed inside the FEIP `data` object (after parsing). Implementations represent it as a generic JSON tree (e.g. object, array, scalar).
2. **`cipher`**, when present, SHOULD be a **FVEP8** ciphertext string (`EncryptType` and fields per [FVEP8](../FVEP/FVEP8V1_Encryption.md)); **`alg`** MAY name the `AlgorithmId` / profile for decryption.
3. **Sensitive data** MUST NOT be placed only in **`contain`**; it MUST be protected in **`cipher`** (or kept off-chain). Clients MUST assume **`contain`** is world-readable.
4. **`contain`** and **`cipher`** MAY both be set on the same Box (e.g. public summary in `contain`, encrypted detail in `cipher`). No ordering merge semantics are defined; clients define interpretation.

### Box entity (indexed)

|Field|Source|Description|
|---|---|---|
|`id`|Create txid|Permanent Box identifier (same as `bid` for **create**).|
|`name`|Op `name`|Human-readable box name (required on **create** / **update** in reference).|
|`desc`|Op `desc`|Optional description.|
|`contain`|Op `contain`|Optional plaintext JSON payload.|
|`cipher`|Op `cipher`|Optional encrypted payload (FVEP8-shaped string).|
|`alg`|Op `alg`|Optional algorithm hint for `cipher`.|
|`owner`|Signer FID|Subject who owns the Box (`opre.getSigner()` in reference).|
|`birthTime`, `birthHeight`|Create tx/block|First appearance.|
|`lastTxId`, `lastTime`, `lastHeight`|Last mutating op|Updated on **create**, **update**, **drop**, **recover** when applied.|
|`active`|Boolean|`true` unless **drop**; **recover** sets `true`.|

### `data` field — operations

Lowercase `op` strings: **`create`**, **`update`**, **`drop`**, **`recover`** (as in [BoxOpData](../../FC-JDK/src/main/java/data/feipData/BoxOpData.java) and the reference parser).

#### 1. create

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"create"`|
|`name`|Y|String|Box name.|
|`bid`|N|—|MUST be **omitted** or **null**; clients MUST NOT supply a `bid` on create.|
|`desc`|N|String|Optional.|
|`contain`|N|JSON value|Optional plaintext payload.|
|`cipher`|N|String|Optional encrypted payload (FVEP8).|
|`alg`|N|String|Optional hint when `cipher` is used.|

**Consensus rules**

1. **`bid`** MUST NOT be set by the client; the indexer assigns **`id`** = **`bid`** = **this transaction’s txid**.
2. **`name`** MUST NOT be null or empty (reference rejects null `name`).
3. **`owner`** MUST be the transaction **signer** FID.
4. **`active`** MUST be `true`.
5. **`birthTime`**, **`birthHeight`**, and last-* fields MUST be set from this transaction’s block/time context.
6. If a Box document with id = this txid **already exists**, the **create** MUST be rejected (reference: “Box already exists”).
7. **CDD:** The reference implementation requires a minimum **CDD** on **create** when the chain height is above a configured threshold (see `StartFEIP.CddCheckHeight` / `CddRequired`). Conforming indexers SHOULD apply the same policy for anti-spam consistency with the reference.

#### 2. update

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"update"`|
|`bid`|Y|String|Existing Box **`id`** (create txid).|
|`name`|Y|String|New or same name (required in reference).|
|`desc`|N|String|Optional.|
|`contain`|N|JSON value|Optional; replaces stored `contain` when provided (reference sets field when non-null in op).|
|`cipher`|N|String|Optional.|
|`alg`|N|String|Optional.|

**Consensus rules**

8. The Box **`bid`** MUST exist.
9. **`owner`** of the Box MUST equal the transaction **signer**; otherwise reject (“Box owner is not the signer”).
10. The Box MUST be **active**; otherwise reject (“Box is not active”).
11. Update **`lastTxId`**, **`lastTime`**, **`lastHeight`** from this transaction.

*Note:* The reference only overwrites `contain` / `cipher` / `alg` / `desc` / `name` when the corresponding op fields are **non-null**. Clearing encrypted or plaintext payload may require an implementation convention (e.g. explicit empty object) — not specified in v1.

#### 3. drop

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"drop"`|
|`bids`|Y|Array of strings|Box **`id`** values to deactivate.|

**Consensus rules**

12. For each **`bid`** in **`bids`**, if the Box exists, **`owner`** equals **signer**, and **`active`** is **true**, set **`active`** to **false** and refresh last-* fields. Skip entries that do not match (reference: `continue`).

#### 4. recover

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"recover"`|
|`bids`|Y|Array of strings|Box **`id`** values to reactivate.|

**Consensus rules**

13. Same as **drop**, but only for boxes where **`active`** is **false**; set **`active`** to **true** and refresh last-* fields. Skip if already active.

### OP_RETURN envelope

```json
{
  "type": "FEIP",
  "sn": "13",
  "ver": "1",
  "name": "Box",
  "data": { }
}
```

### BoxHistory (audit)

[BoxHistory](../../FC-JDK/src/main/java/data/feipData/BoxHistory.java) records per-operation context: `height`, `index`, `time`, `signer`, `op`, and the op-specific fields (`bid` / `bids`, `name`, `desc`, `contain`, `cipher`, `alg`) for indexing or audit trails separate from the current Box document.

### Parsing order and reorg

- Strict block order per [FEIP0](FEIP0V1_FEIP.md).
- Rollback behaviour follows the same **Personal** rollback strategy as other personal protocols in the reference parser (remove or trim state by height as implemented).

## Examples

### Example 1 — create with plaintext `contain` only

```json
{
  "type": "FEIP",
  "sn": "13",
  "ver": "1",
  "name": "Box",
  "data": {
    "op": "create",
    "name": "Reading list",
    "desc": "Public bookmarks",
    "contain": {
      "items": [
        { "title": "FEIP0", "url": "https://example.com/feip0" }
      ]
    }
  }
}
```

After indexing, `id` and `bid` equal this transaction’s txid.

### Example 2 — create with `cipher` (shape illustrative)

```json
{
  "type": "FEIP",
  "sn": "13",
  "ver": "1",
  "name": "Box",
  "data": {
    "op": "create",
    "name": "Private notes",
    "alg": "EccK1AesGcm256@No1_NrC7",
    "cipher": "{\"type\":\"Password\",\"alg\":\"...\",\"cipher\":\"...\"}"
  }
}
```

Use the **FVEP8** JSON shape appropriate to your chosen `EncryptType` and **FTSP** algorithm.

### Example 3 — update

```json
{
  "type": "FEIP",
  "sn": "13",
  "ver": "1",
  "name": "Box",
  "data": {
    "op": "update",
    "bid": "<create_txid>",
    "name": "Reading list",
    "contain": { "items": [] }
  }
}
```

### Example 4 — drop / recover

```json
{
  "type": "FEIP",
  "sn": "13",
  "ver": "1",
  "name": "Box",
  "data": {
    "op": "drop",
    "bids": ["<create_txid_1>", "<create_txid_2>"]
  }
}
```

Use `"op": "recover"` with the same `bids` shape to reactivate.

## Versioning

|Version|Date|Summary|
|---|---|---|
|1|2026-03-23|Initial spec: `contain` vs `cipher`, create/update/drop/recover; aligned with `Feip.BOX` (`13`/`1`).|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FEIP0|Envelope, parsing, CDD.|
|FVEP8|Encryption types and JSON/bundle shapes for `cipher`.|
|FTSP|`AlgorithmId` and AEAD/ECDH parameters.|
|FEIP12 / FEIP7 / FEIP17|Other personal protocols (contact, mail, secret).|

## Reference Implementation

|Component|Location|
|---|---|
|`Box`| [FC-JDK/src/main/java/data/feipData/Box.java](../../FC-JDK/src/main/java/data/feipData/Box.java) |
|`BoxOpData`| [FC-JDK/src/main/java/data/feipData/BoxOpData.java](../../FC-JDK/src/main/java/data/feipData/BoxOpData.java) |
|`BoxHistory`| [FC-JDK/src/main/java/data/feipData/BoxHistory.java](../../FC-JDK/src/main/java/data/feipData/BoxHistory.java) |
|`PersonalParser.makeBox` / `parseBox`| [FEIP/FeipParser/src/main/java/personal/PersonalParser.java](../../FEIP/FeipParser/src/main/java/personal/PersonalParser.java) |
|`Feip.BOX`| [FC-JDK/src/main/java/data/feipData/Feip.java](../../FC-JDK/src/main/java/data/feipData/Feip.java) |
