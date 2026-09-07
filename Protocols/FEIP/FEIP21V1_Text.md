# FEIP21V1_Text

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
|Title|Text|
|Type|FEIP|
|SN|21|
|Version|1|
|Category|Publish|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-23|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

The **Text** protocol indexes **published text works** on-chain: **title**, optional **type**, **did** (document id string), **lang**, **authors**, **format**, **summary**, and a monotonic string **`ver`** on the entity that starts at **`1`** and increments on each **update**. The **publisher** is the **signer** of **publish**. Operations **publish**, **update**, **delete**, **recover**, and **rate** manage lifecycle and CDD-weighted **tRate** / **tCdd** (publisher cannot **rate**).

## Motivation

- **Catalogue** text publications with rich metadata without putting full body in OP_RETURN (body typically lives off-chain at **`did`** or related storage).
- **Versioning**: On-entity **`ver`** bumps on **update** so indexers and clients can detect editions.
- **Soft delete**: **delete** / **recover** flip **`deleted`** without dropping the row (in reference).

## Specification

### Text entity (indexed)

|Field|Source|Description|
|---|---|---|
|`id`|Publish txid|Stable text id (same as **`textId`** for **publish**).|
|`title`|Op|Work title (required on **publish** / **update** in reference).|
|`ver`|Indexer|Edition counter as **decimal string**: **`1`** on first **publish**; each **update** sets **`ver` ← String(parseInt(ver)+1)**.|
|`type`|Op|Optional category / MIME-ish hint (opaque string).|
|`did`|Op|Optional **document id** (URI, CID, or app-defined pointer to content).|
|`lang`|Op|Optional language tag.|
|`authors`|Op|Optional list of author FIDs or display strings.|
|`format`|Op|Optional format hint (e.g. `markdown`, `plain`).|
|`summary`|Op|Optional short abstract.|
|`publisher`|Publish signer|FID of the publisher; **update** requires this FID; **delete** / **recover** allow [FEIP6](FEIP6V1_Master.md) **master** bypass when signer ≠ publisher (see below).|
|`birthTime`, `birthHeight`, `lastTxId`, `lastTime`, `lastHeight`|Tx / block|Lifecycle.|
|`tCdd`, `tRate`|**rate**|Cumulative CDD weight and weighted average rating.|
|`deleted`|**delete** / **recover**|`true` when logically deleted.|

### `data.op` values

Lowercase per [TextOpData.toLowerCase()](../../FC-JDK/src/main/java/data/feipData/TextOpData.java): **`publish`**, **`update`**, **`delete`**, **`recover`**, **`rate`**.

### Operations

#### 1. publish

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"publish"`|
|`title`|Y|String|Non-empty title.|

Optional: `type`, `did`, `ver` (in op JSON is **not** copied to history in reference — entity **`ver`** is forced to **`1`**), `lang`, `authors`, `format`, `summary`.

**Consensus rules**

1. **`textId`** MUST NOT be supplied by the client; **`id`** = **`textId`** = **this transaction id**.
2. **CDD:** When height &gt; `CddCheckHeight`, **publish** requires **`cdd` ≥ `CddRequired`** (reference **makeText**).
3. If a Text document for this id **already exists**, reject.
4. **`deleted`** = **`false`**; **`publisher`** = signer.

#### 2. update

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"update"`|
|`textId`|Y|String|Existing text **`id`**.|
|`title`|Y|String|Non-empty.|

Optional: `type`, `did`, `lang`, `authors`, `format`, `summary` — reference **overwrites** entity fields from history even when a field is null (see implementation notes).

**Consensus rules**

5. Text MUST exist and **`deleted`** MUST be false.
6. **Signer MUST equal `publisher`** (reference does **not** apply the FEIP6 **master** bypass used on **delete** / **recover**).
7. Increment entity **`ver`** as integer string + 1.
8. Refresh last-* fields.

#### 3. delete

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"delete"`|
|`textIds`|Y|String array|One or more text **`id`** values.|

**Consensus rules**

9. For each id: if **signer** = **publisher**, apply **`deleted` = true**. If signer ≠ publisher, reference loads **Freer** for signer and applies delete only if **`master` != null** and **`master.equals(signer)`** (same pattern as [FEIP18](FEIP18V1_Team.md) transfer side-path).
10. Refresh last-* for changed rows; bulk index.

#### 4. recover

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"recover"`|
|`textIds`|Y|String array|Text **`id`** values to undelete.|

**Consensus rules**

11. Same authorization as **delete**, but set **`deleted` = false**.

#### 5. rate

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"rate"`|
|`textId`|Y|String|Target text **`id`**.|
|`rate`|Y|Integer|Rating value. MUST be present and **0–5** inclusive; an absent or out-of-range value ignores the operation. Earlier revisions of this document said the reference did not clamp `rate`, and it did not: a value outside the range was indexed and folded into `tRate`, where no client could correct it and no op removes it. Bounded in place rather than by a version bump — the range was always the intended one (`FeipConstants.MAX_RATE`), and the Construct protocols have enforced it since v1.|
|`cause`|N|String|Free text saying **why**, carried with the rating and stored on **TextHistory**. Omit the field entirely when there is no reason to give; a client MUST NOT carve it as an empty string. Counts against the OP_RETURN size limit like any other field.|

**Consensus rules**

12. **CDD** on the operation MUST be **≥ `CddRequired`** (**makeText**).
13. **Signer MUST NOT equal `publisher`**.
14. **`tRate`** / **`tCdd`**: if unset, initialize from this op; else CDD-weighted average:  
    **`tRate ← (tRate * tCdd + rate * cdd) / (tCdd + cdd)`**, **`tCdd ← tCdd + cdd`**.
15. Refresh last-* fields.

### OP_RETURN envelope

```json
{
  "type": "FEIP",
  "sn": "21",
  "ver": "1",
  "name": "Text",
  "data": { }
}
```

### TextHistory (audit)

[TextHistory](../../FC-JDK/src/main/java/data/feipData/TextHistory.java) stores block context, `signer`, `op`, `textId` / `textIds`, the metadata fields needed for reparse, and — for **rate** — `rate`, `cdd`, and `cause` when the op supplied one.

## Examples

### Example 1 — publish

```json
{
  "type": "FEIP",
  "sn": "21",
  "ver": "1",
  "name": "Text",
  "data": {
    "op": "publish",
    "title": "Why Freecash",
    "type": "essay",
    "did": "disk:...",
    "lang": "en",
    "authors": ["FID1..."],
    "format": "markdown",
    "summary": "Economic argument in short form."
  }
}
```

### Example 2 — update

```json
{
  "type": "FEIP",
  "sn": "21",
  "ver": "1",
  "name": "Text",
  "data": {
    "op": "update",
    "textId": "<publish_txid>",
    "title": "Why Freecash (rev 2)",
    "summary": "Expanded abstract."
  }
}
```

### Example 3 — delete / recover

```json
{
  "type": "FEIP",
  "sn": "21",
  "ver": "1",
  "name": "Text",
  "data": {
    "op": "delete",
    "textIds": ["<publish_txid>"]
  }
}
```

Use `"op": "recover"` with the same `textIds` shape to clear **`deleted`**.

### Example 4 — rate

```json
{
  "type": "FEIP",
  "sn": "21",
  "ver": "1",
  "name": "Text",
  "data": {
    "op": "rate",
    "textId": "<publish_txid>",
    "rate": 5,
    "cause": "Clearest write-up of the reorg rules I have read."
  }
}
```

## Versioning

|Version|Date|Summary|
|---|---|---|
|1|2026-03-23|Initial spec; aligned with `Feip.TEXT` (`21`/`1`).|
|1|2026-09-06|Optional **`cause`** added to the **rate** op (free text saying why, stored on history, no effect on `tRate` / `tCdd`). Added in place rather than by a version bump: it is a new optional field, so every carve valid before this change is still valid and reads identically, and a parser that does not know `cause` simply drops it. Mirrors [FEIP16 Reputation](FEIP16V1_Reputation.md), where a rating has carried a `cause` from the start.|
|1|2026-09-06|**`rate` is now bounded to 0–5** in the reference parser. It previously required only a non-null value, so an out-of-range score was indexed and folded into `tRate` permanently. Bounded in place: the range matches `FeipConstants.MAX_RATE`, which the Construct protocols have enforced all along.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FEIP0|Envelope, OP_RETURN, CDD.|
|FEIP6 Master|**delete** / **recover** authorization when signer ≠ publisher.|
|FEIP18 Team|Similar **rate** CDD-weighting pattern.|

## Reference Implementation

|Component|Location|
|---|---|
|`Text`| [FC-JDK/src/main/java/data/feipData/Text.java](../../FC-JDK/src/main/java/data/feipData/Text.java) |
|`TextOpData`| [FC-JDK/src/main/java/data/feipData/TextOpData.java](../../FC-JDK/src/main/java/data/feipData/TextOpData.java) |
|`TextHistory`| [FC-JDK/src/main/java/data/feipData/TextHistory.java](../../FC-JDK/src/main/java/data/feipData/TextHistory.java) |
|`PublishParser.makeText` / `parseText`| [FEIP/FeipParser/src/main/java/publish/PublishParser.java](../../FEIP/FeipParser/src/main/java/publish/PublishParser.java) |
|`Feip.TEXT`| [FC-JDK/src/main/java/data/feipData/Feip.java](../../FC-JDK/src/main/java/data/feipData/Feip.java) |

### Implementation notes (non-normative)


- **delete** / **recover**: if no rows pass the publisher/master filter, **parseText** logs and **`return false`** explicitly (no silent exit from the switch).
