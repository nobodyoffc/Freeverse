# FEIP19V4_Square

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
|Title|Square|
|Type|FEIP|
|SN|19|
|Version|4|
|Category|Organize|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-23|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

A **Square** is an **unmanaged** on-chain group: anyone can **join** or **leave**; there is no owner-only admin role beyond **metadata updates** (which any **member** may perform after a **CDD** threshold). The square **`id`** is the **create** transaction id. Indexed state includes **name**, **desc**, optional **home** links (string map), **members**, **namers** (FIDs who performed **update**), **member** count, and **CDD** bookkeeping (`cddToUpdate`, `tCdd`) used to rate-limit **update**.

**Version 4** replaces **version 3** naming: the FEIP envelope **`name`** was **`Group`** and is now **`Square`**; in `data`, **`gid` / `gids`** are **`squareId` / `squareIds`**. On-chain JSON MUST use **`"ver": "4"`** and **`"name": "Square"`**.

## Motivation

- **Lightweight communities** without Team-style governance contracts.
- **Open membership**: **join** / **leave** with minimal rules; **update** restricted by cumulative **CDD** so renames/home spam costs weight.
- **Discoverability**: **home** map (e.g. URL or app-specific keys) similar in spirit to [FEIP9](FEIP9V1_Home.md) patterns.
- **Clear naming**: **Square** and **`squareId`** avoid confusion with other “group” concepts on-chain.

## Specification

### Square entity (indexed)

|Field|Source|Description|
|---|---|---|
|`id`|Create txid|Stable square identifier (same as `squareId` for **create**).|
|`name`|Create / **update**|Display name.|
|`desc`|Create / **update**|Optional description.|
|`home`|Create / **update**|Optional `Map<String,String>` (e.g. link type → URL).|
|`members`|Create / **join** / **leave**|Distinct FID list of current members.|
|`memberNum`|Derived|`members.length` (reference maintains explicitly).|
|`namers`|Create / **update**|FIDs who have successfully **updated** metadata (creator is first namer on **create**).|
|`birthTime`, `birthHeight`|**create**|First appearance.|
|`lastTxId`, `lastTime`, `lastHeight`|Any mutating op|Last change context.|
|`cddToUpdate`|**create** / **update**|Minimum **CDD** the next **update** tx must carry (`create`: `1` if op CDD null, else `opCdd + 1`; after each **update**: set to `opCdd + 1`).|
|`tCdd`|**create** / **join** / **update** / **leave**|Running sum of operation **CDD** values applied (null-safe in reference).|

### `data` field — operations

Lowercase `op` strings: **`create`**, **`update`**, **`join`**, **`leave`** (see [SquareOpData](../../FC-JDK/src/main/java/data/feipData/SquareOpData.java) / [FeipOp](../../FC-JDK/src/main/java/data/feipData/FeipOp.java)).

#### 1. create

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"create"`|
|`name`|Y|String|Square name.|
|`squareId`|N|—|MUST be **omitted** or **null** (client must not pre-assign).|
|`desc`|N|String|Optional.|
|`home`|N|Object|Optional string map (`{ "key": "value", ... }`).|

**Consensus rules**

1. **`squareId`** MUST NOT be set on **create**; indexer assigns **`id`** = **`squareId`** = **this transaction id**.
2. **`name`** MUST NOT be null.
3. Initial **`members`** and **`namers`** each contain exactly the **creator** (transaction **signer**).
4. **`memberNum`** = 1.
5. **CDD (reference):** When height &gt; configured `CddCheckHeight`, **create** requires **`cdd` ≥ `CddRequired`** on the OpReturn context (same pattern as other personal/org ops).
6. If a Square document with id = this txid **already exists**, **create** MUST be rejected.

#### 2. update

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"update"`|
|`squareId`|Y|String|Existing square **`id`**.|
|`name`|N|String|At least one of **`name`** or **`home`** MUST be non-null in the op (reference rejects if both null).|
|`home`|N|Object|Optional map update.|
|`desc`|N|String|Optional.|

**Consensus rules**

7. Square MUST exist.
8. **Signer MUST be in `members`.**
9. Let **`cdd`** be this operation’s **CDD** (from tx context). **`cdd`** MUST NOT be null and MUST satisfy **`cdd` ≥ `cddToUpdate`** stored on the square; otherwise reject.
10. After success: **`cddToUpdate` ← `cdd + 1`**; **`tCdd` ← `tCdd + cdd`** (null-safe).
11. Non-null op fields overwrite **`name`**, **`desc`**, **`home`** on the entity.
12. Append **signer** to **`namers`** (set union in reference).
13. Refresh last-* fields.

#### 3. join

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"join"`|
|`squareId`|Y|String|Square **`id`**.|

**Consensus rules**

14. Square MUST exist.
15. Add **signer** to **`members`** if not already present (reference uses a set).
16. **`memberNum`** reflects new member count.
17. **CDD:** Same minimum **CDD** rule as **create** when above `CddCheckHeight` (**makeSquare**).
18. If **`cdd`** present on history, **`tCdd` ← `tCdd + cdd`**.
19. Refresh last-* fields.

#### 4. leave

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"leave"`|
|`squareIds`|Y|Array of strings|One or more square **`id`** values to leave in one transaction.|

**Consensus rules**

20. For each id in **`squareIds`**, load the square; **signer MUST be in `members`**; remove **signer** from **`members`** and update **`memberNum`**.
21. If **no members remain**, the reference implementation **deletes** the square document and attempts history cleanup (see implementation notes); otherwise re-index the square and refresh last-* fields.
22. **`cdd`** on **leave** may update **`tCdd`** when present.

### OP_RETURN envelope

```json
{
  "type": "FEIP",
  "sn": "19",
  "ver": "4",
  "name": "Square",
  "data": { }
}
```

[Feip.SQUARE](../../FC-JDK/src/main/java/data/feipData/Feip.java) registers **`("19","4","Square")`**. [OrganizationParser.makeSquare](../../FEIP/FeipParser/src/main/java/organize/OrganizationParser.java) **ignores** payloads with numeric **`ver` &lt; 4**.

### SquareHistory (audit)

[SquareHistory](../../FC-JDK/src/main/java/data/feipData/SquareHistory.java) records `height`, `index`, `time`, `signer`, `cdd`, `op`, and op-specific fields (`squareId`, `squareIds`, `name`, `desc`, `home`) for reparse / rollback pipelines.

### Parsing order and reorg

- Strict block order per [FEIP0](FEIP0V1_FEIP.md).
- Organization rollback / reparse (reference) replays **SquareHistory** as for other org entities.

## Examples

### Example 1 — create

```json
{
  "type": "FEIP",
  "sn": "19",
  "ver": "4",
  "name": "Square",
  "data": {
    "op": "create",
    "name": "Freecash builders",
    "desc": "Open coordination square",
    "home": {
      "web": "https://example.com/square"
    }
  }
}
```

### Example 2 — join

```json
{
  "type": "FEIP",
  "sn": "19",
  "ver": "4",
  "name": "Square",
  "data": {
    "op": "join",
    "squareId": "<create_txid>"
  }
}
```

### Example 3 — update (requires sufficient CDD)

```json
{
  "type": "FEIP",
  "sn": "19",
  "ver": "4",
  "name": "Square",
  "data": {
    "op": "update",
    "squareId": "<create_txid>",
    "name": "Freecash Builders",
    "home": { "web": "https://example.org/new" }
  }
}
```

### Example 4 — leave (one or more squares)

```json
{
  "type": "FEIP",
  "sn": "19",
  "ver": "4",
  "name": "Square",
  "data": {
    "op": "leave",
    "squareIds": ["<create_txid_a>", "<create_txid_b>"]
  }
}
```

## Versioning

|Version|Date|Summary|
|---|---|---|
|4|2026-03-23|Current spec: protocol **`name` `Square`**, fields **`squareId` / `squareIds`**; matches `Feip.SQUARE` and parser gate `ver` ≥ 4.|
|3|—|Earlier naming: **`Group`** protocol name and **`gid` / `gids`** in `data` (and related types). Superseded by v4 naming; not accepted by reference **makeSquare** when `ver` &lt; 4.|
|≤2|—|Legacy; reference ignores `ver` &lt; 4.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FEIP0|Envelope, parsing, CDD.|
|FEIP9|Home-style link maps (conceptual parallel to `home`).|
|FEIP18 Team|Contrasts **managed** teams vs **unmanaged** squares ([FEIP18V1_Team](FEIP18V1_Team.md)).|

## Reference Implementation

|Component|Location|
|---|---|
|`Square`| [FC-JDK/src/main/java/data/feipData/Square.java](../../FC-JDK/src/main/java/data/feipData/Square.java) |
|`SquareOpData`| [FC-JDK/src/main/java/data/feipData/SquareOpData.java](../../FC-JDK/src/main/java/data/feipData/SquareOpData.java) |
|`SquareHistory`| [FC-JDK/src/main/java/data/feipData/SquareHistory.java](../../FC-JDK/src/main/java/data/feipData/SquareHistory.java) |
|`OrganizationParser.makeSquare` / `parseSquare`| [FEIP/FeipParser/src/main/java/organize/OrganizationParser.java](../../FEIP/FeipParser/src/main/java/organize/OrganizationParser.java) |
|`Feip.SQUARE`| [FC-JDK/src/main/java/data/feipData/Feip.java](../../FC-JDK/src/main/java/data/feipData/Feip.java) |
