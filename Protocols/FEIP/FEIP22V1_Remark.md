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

- **Required:** **`remarkId`**, **`rate`**, non-null **CDD** ≥ **`CddRequired`** (**makeRemark**).
- Signer MUST NOT be **`publisher`**.
- **`tRate`** / **`tCdd`** by CDD-weighted average.

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

[RemarkHistory](../../FC-JDK/src/main/java/data/feipData/RemarkHistory.java) stores block context, `signer`, `cdd` (**rate**), `op`, `remarkId` / `remarkIds`, `onDid`, and other metadata fields.

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
    "rate": 4
  }
}
```

## Versioning

|Version|Date|Summary|
|---|---|---|
|1|2026-03-24|Initial spec; aligned with `Feip.REMARK` (`22`/`1`).|

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

