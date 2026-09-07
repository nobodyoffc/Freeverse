# FEIP22V1_Remark

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
|Title|Remark|
|Type|FEIP|
|SN|22|
|Version|1|
|Category|Publish|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-24|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

The **Remark** protocol indexes **annotations** on-chain: metadata about a comment or note anchored to another published object via **`onDid`** (the **target** document id), plus optional **`did`** for this remark’s own body pointer. The lifecycle matches [FEIP21 Text](FEIP21V1_Text.md) / [FEIP24 Image](FEIP24V1_Image.md): **`publish`**, **`update`**, **`delete`**, **`recover`**, **`rate`**, with **`remarkId`** / **`remarkIds`**, string **`ver`**, **`publisher`**, and CDD-weighted **`tRate`** / **`tCdd`**.

## Motivation

- **Threaded discourse**: Link remarks to essays, images, sound, video, or any **`did`**-identified work.
- **Same indexer patterns** as other **Publish** FEIPs for clients and APIs.

## Specification

### Remark entity (indexed)

|Field|Source|Description|
|---|---|---|
|`id`|Publish txid|Stable remark id (same as **`remarkId`** on **publish**).|
|`title`|Op|Title (required on **publish** / **update** in reference).|
|`ver`|Indexer|Decimal **string**; **`1`** on **publish**; each **update** increments by `parseInt(ver)+1`.|
|`did`|Op|Optional pointer to this remark’s content (URI, CID, etc.).|
|`onDid`|Op|Optional **target** document id — the object being remarked on (same id space as other FEIP **`did`** fields).|
|`lang`|Op|Optional language tag.|
|`authors`|Op|Optional author list.|
|`format`|Op|Optional format hint.|
|`summary`|Op|Optional short text.|
|`publisher`|Publish signer|**update** requires this FID; **delete** / **recover** allow [FEIP6](FEIP6V1_Master.md) **master** bypass when signer ≠ publisher.|
|`birthTime`, `birthHeight`, `lastTxId`, `lastTime`, `lastHeight`|Tx / block|Lifecycle.|
|`tCdd`, `tRate`|**rate**|CDD-weighted rating aggregate.|
|`deleted`|**delete** / **recover**|Logical deletion flag.|

### `data.op` values

Lowercase: **`publish`**, **`update`**, **`delete`**, **`recover`**, **`rate`** ([RemarkOpData](../../FC-JDK/src/main/java/data/feipData/RemarkOpData.java)).

### Operations

#### 1. publish

- **Required:** `op`, non-empty **`title`**.
- **Optional:** `did`, **`onDid`**, `lang`, `authors`, `format`, `summary`. Entity **`ver`** is **`1`** in the reference.
- **`remarkId`** MUST NOT be set; **`id`** = **`remarkId`** = this **txid**.
- **CDD:** when height exceeds **`CddCheckHeight`**, **`cdd`** MUST be non-null and **≥ `CddRequired`** (**makeRemark**).
- Reject if document already exists.

#### 2. update

- **Required:** **`remarkId`**, non-empty **`title`**.
- **Optional:** `did`, **`onDid`**, `lang`, `authors`, `format`, `summary` — reference overwrites from history (nulls may clear stored fields).
- Signer MUST equal **`publisher`**.
- Remark must exist, **`deleted`** false; bump **`ver`**.

#### 3. delete

- **Required:** **`remarkIds`** (string array).
- Per hit: publisher or **Freer.master** gate (same as [FEIP21](FEIP21V1_Text.md) **delete**); set **`deleted` = true**.

#### 4. recover

- Same as **delete** with **`remarkIds`**, **`deleted` = false**.

#### 5. rate

Score somebody else's remark, weighted by the transaction's **CDD**. A
remark is rated exactly as the work it annotates is: same fields, same
range, same weighting, same publisher bar.

**`data` fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"rate"`|
|`remarkId`|Y|String|Target remark **`id`**. Absent or unparsable ignores the operation.|
|`rate`|Y|Integer|MUST be present and **0–5** inclusive; an absent or out-of-range value ignores the operation.|
|`cause`|N|String|Free text saying **why**, carried with the rating and stored on **RemarkHistory**. Omit the field entirely when there is no reason to give; a client MUST NOT carve it as an empty string. Counts against the OP_RETURN size limit like any other field.|

**Consensus rules**

- **CDD** on the operation MUST be non-null and **≥ `CddRequired`** (**makeRemark**); otherwise ignore.
- **Signer MUST NOT equal `publisher`.** A remark's author cannot rate their own remark.
- The remark MUST exist; otherwise no state change.
- **`tRate`** / **`tCdd`**: if unset, initialize from this op; else CDD-weighted average:
  **`tRate ← (tRate * tCdd + rate * cdd) / (tCdd + cdd)`**, **`tCdd ← tCdd + cdd`**.
- `cause` does **not** affect `tRate` / `tCdd`; it is recorded on the history row only.
- Refresh **`lastTxId`** / **`lastTime`** / **`lastHeight`**.

**On the range.** Earlier revisions of this document said the reference
did not clamp `rate`, and it did not: a value outside the range was
indexed and folded into `tRate`, where no client could correct it and no
op removes it. Bounded in place rather than by a version bump — the
range was always the intended one (`FeipConstants.MAX_RATE`), and the
Construct protocols have enforced it since v1.

### OP_RETURN envelope

```json
{
  "type": "FEIP",
  "sn": "22",
  "ver": "1",
  "name": "Remark",
  "data": { }
}
```

### RemarkHistory (audit)

[RemarkHistory](../../FC-JDK/src/main/java/data/feipData/RemarkHistory.java) stores block context, `signer`, `op`, `remarkId` / `remarkIds`, `onDid`, the other metadata fields, and — for **rate** — `rate`, `cdd`, and `cause` when the op supplied one.

The `remark_history` index mapping previously declared neither **`rate`** nor **`remarkIds`**, so a rating's score was dynamic-mapped as `long` where every sibling index uses `short`, and the plural key `delete` / `recover` write was dynamic-mapped as text. Both are declared now.

## Examples

### publish (remark on another document)

```json
{
  "type": "FEIP",
  "sn": "22",
  "ver": "1",
  "name": "Remark",
  "data": {
    "op": "publish",
    "title": "Errata for section 3",
    "onDid": "<target_work_did>",
    "did": "disk:...",
    "summary": "Suggested correction."
  }
}
```

### update

```json
{
  "type": "FEIP",
  "sn": "22",
  "ver": "1",
  "name": "Remark",
  "data": {
    "op": "update",
    "remarkId": "<publish_txid>",
    "title": "Errata for section 3 (v2)"
  }
}
```

### delete / recover

```json
{
  "type": "FEIP",
  "sn": "22",
  "ver": "1",
  "name": "Remark",
  "data": {
    "op": "delete",
    "remarkIds": ["<publish_txid>"]
  }
}
```

### rate

```json
{
  "type": "FEIP",
  "sn": "22",
  "ver": "1",
  "name": "Remark",
  "data": {
    "op": "rate",
    "remarkId": "<publish_txid>",
    "rate": 4,
    "cause": "Adds the benchmark the parent post was missing."
  }
}
```

## Versioning

|Version|Date|Summary|
|---|---|---|
|1|2026-03-24|Initial spec; aligned with `Feip.REMARK` (`22`/`1`).|
|1|2026-09-06|Optional **`cause`** added to the **rate** op (free text saying why, stored on history, no effect on `tRate` / `tCdd`). Added in place rather than by a version bump: it is a new optional field, so every carve valid before this change is still valid and reads identically, and a parser that does not know `cause` simply drops it. Mirrors [FEIP16 Reputation](FEIP16V1_Reputation.md), where a rating has carried a `cause` from the start. **`remark_history`** additionally gained explicit `rate` and `cause` mappings; `rate` had never been mapped at all, so a Remark rating's score was dynamic-mapped as `long` where every sibling index uses `short`.|
|1|2026-09-06|**`rate` is now bounded to 0–5** in the reference parser. It previously required only a non-null value, so an out-of-range score was indexed and folded into `tRate` permanently. Bounded in place: the range matches `FeipConstants.MAX_RATE`, which the Construct protocols have enforced all along.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FEIP0|Envelope, CDD.|
|FEIP21 Text|Parallel publish/update/delete/rate; **Remark** adds **`onDid`**.|
|FEIP23–25|Other publish media; remarks may target their **`did`** values via **`onDid`**.|
|FEIP6 Master|**delete** / **recover** when signer ≠ publisher.|

## Reference Implementation

|Component|Location|
|---|---|
|`Remark`| [FC-JDK/src/main/java/data/feipData/Remark.java](../../FC-JDK/src/main/java/data/feipData/Remark.java) |
|`RemarkOpData`| [FC-JDK/src/main/java/data/feipData/RemarkOpData.java](../../FC-JDK/src/main/java/data/feipData/RemarkOpData.java) |
|`RemarkHistory`| [FC-JDK/src/main/java/data/feipData/RemarkHistory.java](../../FC-JDK/src/main/java/data/feipData/RemarkHistory.java) |
|`PublishParser.makeRemark` / `parseRemark`| [FEIP/FeipParser/src/main/java/publish/PublishParser.java](../../FEIP/FeipParser/src/main/java/publish/PublishParser.java) |
|`Feip.REMARK`| [FC-JDK/src/main/java/data/feipData/Feip.java](../../FC-JDK/src/main/java/data/feipData/Feip.java) |

