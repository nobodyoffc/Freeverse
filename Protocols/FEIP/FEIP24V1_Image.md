# FEIP24V1_Image

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
|Title|Image|
|Type|FEIP|
|SN|24|
|Version|1|
|Category|Publish|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-24|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

The **Image** protocol indexes **image publication references** on-chain: metadata and a **`did`** pointer to the asset (no image bytes in OP_RETURN). The indexed shape matches [FEIP23 Sound](FEIP23V1_Sound.md) / [FEIP21 Text](FEIP21V1_Text.md) minus **`type`**: **`publish`**, **`update`**, **`delete`**, **`recover`**, and **`rate`**, with **`imageId`** / **`imageIds`** in `data`, stable **`id`** = publish txid, string **`ver`** from **`1`** incremented on **update**, and CDD-weighted **`tRate`** / **`tCdd`**.

## Motivation

- **Catalogue** image works (covers, art, diagrams) with the same publish lifecycle as **Sound** and **Text**.
- **Soft delete** and **rate** with **CDD** economics.

## Specification

### Image entity (indexed)

|Field|Source|Description|
|---|---|---|
|`id`|Publish txid|Stable image id (same as **`imageId`** on **publish**).|
|`title`|Op|Title (required on **publish** / **update** in reference).|
|`ver`|Indexer|Decimal **string**; **`1`** on **publish**; each **update** increments by `parseInt(ver)+1`.|
|`did`|Op|Optional pointer to image bytes (URI, CID, DISK id, etc.).|
|`lang`|Op|Optional language tag.|
|`authors`|Op|Optional author list.|
|`format`|Op|Optional format hint (e.g. `image/png`).|
|`summary`|Op|Optional short description.|
|`publisher`|Publish signer|**update** requires this FID; **delete** / **recover** allow [FEIP6](FEIP6V1_Master.md) **master** bypass when signer ≠ publisher.|
|`birthTime`, `birthHeight`, `lastTxId`, `lastTime`, `lastHeight`|Tx / block|Lifecycle.|
|`tCdd`, `tRate`|**rate**|CDD-weighted rating aggregate.|
|`deleted`|**delete** / **recover**|Logical deletion flag.|

### `data.op` values

Lowercase: **`publish`**, **`update`**, **`delete`**, **`recover`**, **`rate`** ([ImageOpData](../../FC-JDK/src/main/java/data/feipData/ImageOpData.java)).

### Operations

#### 1. publish

- **Required:** `op`, non-empty **`title`**.
- **Optional:** `did`, `lang`, `authors`, `format`, `summary`. Entity **`ver`** is set to **`1`** in the reference (**`ver`** in op JSON is not applied to the entity).
- **`imageId`** MUST NOT be set; **`id`** = **`imageId`** = this **txid**.
- **CDD:** when height exceeds **`CddCheckHeight`**, **`cdd`** MUST be non-null and **≥ `CddRequired`** (**makeImage**).
- Reject if document already exists.

#### 2. update

- **Required:** **`imageId`**, non-empty **`title`**.
- **Optional:** `did`, `lang`, `authors`, `format`, `summary` — reference overwrites from history (nulls may clear stored fields).
- Signer MUST equal **`publisher`** (no master bypass).
- Document must exist, **`deleted`** false; bump **`ver`**.

#### 3. delete

- **Required:** **`imageIds`** (string array).
- Per hit: publisher match or **Freer.master** gate (same as [FEIP21](FEIP21V1_Text.md) **delete**); set **`deleted` = true**.

#### 4. recover

- Same as **delete** with **`imageIds`**, **`deleted` = false**.

#### 5. rate

- **Required:** **`imageId`**, **`rate`**, non-null **CDD** ≥ **`CddRequired`** (**makeImage** validates **null** **rate**/**CDD**).
- Signer MUST NOT be **`publisher`**.
- **`tRate`** / **`tCdd`** updated by CDD-weighted average (same formula as Sound/Text).

### OP_RETURN envelope

```json
{
  "type": "FEIP",
  "sn": "24",
  "ver": "1",
  "name": "Image",
  "data": { }
}
```

### ImageHistory (audit)

[ImageHistory](../../FC-JDK/src/main/java/data/feipData/ImageHistory.java) stores block context, `signer`, `cdd` (**rate**), `op`, `imageId` / `imageIds`, and metadata fields.

## Examples

### publish

```json
{
  "type": "FEIP",
  "sn": "24",
  "ver": "1",
  "name": "Image",
  "data": {
    "op": "publish",
    "title": "Cover art",
    "did": "disk:...",
    "format": "image/webp",
    "summary": "Album cover"
  }
}
```

### update

```json
{
  "type": "FEIP",
  "sn": "24",
  "ver": "1",
  "name": "Image",
  "data": {
    "op": "update",
    "imageId": "<publish_txid>",
    "title": "Cover art (cropped)"
  }
}
```

### delete / recover

```json
{
  "type": "FEIP",
  "sn": "24",
  "ver": "1",
  "name": "Image",
  "data": {
    "op": "delete",
    "imageIds": ["<publish_txid>"]
  }
}
```

### rate

```json
{
  "type": "FEIP",
  "sn": "24",
  "ver": "1",
  "name": "Image",
  "data": {
    "op": "rate",
    "imageId": "<publish_txid>",
    "rate": 4
  }
}
```

## Versioning

|Version|Date|Summary|
|---|---|---|
|1|2026-03-24|Initial spec; aligned with `Feip.IMAGE` (`24`/`1`).|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FEIP0|Envelope, CDD.|
|FEIP21 Text|Parallel semantics; Text adds **`type`**.|
|FEIP23 Sound|Parallel semantics for audio references.|
|FEIP6 Master|**delete** / **recover** when signer ≠ publisher.|

## Reference Implementation

|Component|Location|
|---|---|
|`Image`| [FC-JDK/src/main/java/data/feipData/Image.java](../../FC-JDK/src/main/java/data/feipData/Image.java) |
|`ImageOpData`| [FC-JDK/src/main/java/data/feipData/ImageOpData.java](../../FC-JDK/src/main/java/data/feipData/ImageOpData.java) |
|`ImageHistory`| [FC-JDK/src/main/java/data/feipData/ImageHistory.java](../../FC-JDK/src/main/java/data/feipData/ImageHistory.java) |
|`PublishParser.makeImage` / `parseImage`| [FEIP/FeipParser/src/main/java/publish/PublishParser.java](../../FEIP/FeipParser/src/main/java/publish/PublishParser.java) |
|`Feip.IMAGE`| [FC-JDK/src/main/java/data/feipData/Feip.java](../../FC-JDK/src/main/java/data/feipData/Feip.java) |
