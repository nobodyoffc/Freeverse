# FEIP25V1_Video

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
|Title|Video|
|Type|FEIP|
|SN|25|
|Version|1|
|Category|Publish|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-24|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

The **Video** protocol indexes **video publication references** on-chain: metadata and a **`did`** pointer to the asset (no video payload in OP_RETURN). The model matches [FEIP24 Image](FEIP24V1_Image.md) and [FEIP23 Sound](FEIP23V1_Sound.md): **`videoId`** / **`videoIds`**, **`publish`**, **`update`**, **`delete`**, **`recover`**, **`rate`**, string **`ver`** from **`1`**, **`publisher`**, CDD-weighted **`tRate`** / **`tCdd`**, and soft **`deleted`**.

## Motivation

- **Catalogue** video works (clips, streams, long-form) with the same indexer patterns as **Image** and **Sound**.
- **Reuse** client and indexer logic across publish media FEIPs.

## Specification

### Video entity (indexed)

|Field|Source|Description|
|---|---|---|
|`id`|Publish txid|Stable video id (same as **`videoId`** on **publish**).|
|`title`|Op|Title (required on **publish** / **update** in reference).|
|`ver`|Indexer|Decimal **string**; **`1`** on **publish**; each **update** increments by `parseInt(ver)+1`.|
|`did`|Op|Optional pointer to video (URI, CID, DISK id, etc.).|
|`lang`|Op|Optional language tag.|
|`authors`|Op|Optional author list.|
|`format`|Op|Optional format hint (e.g. container or codec).|
|`summary`|Op|Optional short description.|
|`publisher`|Publish signer|**update** requires this FID; **delete** / **recover** allow [FEIP6](FEIP6V1_Master.md) **master** bypass when signer ≠ publisher.|
|`birthTime`, `birthHeight`, `lastTxId`, `lastTime`, `lastHeight`|Tx / block|Lifecycle.|
|`tCdd`, `tRate`|**rate**|CDD-weighted rating aggregate.|
|`deleted`|**delete** / **recover**|Logical deletion flag.|

### `data.op` values

Lowercase: **`publish`**, **`update`**, **`delete`**, **`recover`**, **`rate`** ([VideoOpData](../../FC-JDK/src/main/java/data/feipData/VideoOpData.java)).

### Operations

#### 1. publish

- **Required:** `op`, non-empty **`title`**.
- **Optional:** `did`, `lang`, `authors`, `format`, `summary`. Entity **`ver`** is **`1`** in the reference.
- **`videoId`** MUST NOT be set; **`id`** = **`videoId`** = this **txid**.
- **CDD:** when height exceeds **`CddCheckHeight`**, **`cdd`** MUST be non-null and **≥ `CddRequired`** (**makeVideo**).
- Reject if document already exists.

#### 2. update

- **Required:** **`videoId`**, non-empty **`title`**.
- **Optional:** `did`, `lang`, `authors`, `format`, `summary` — reference overwrites from history (nulls may clear stored fields).
- Signer MUST equal **`publisher`**.
- Document must exist, **`deleted`** false; bump **`ver`**.

#### 3. delete

- **Required:** **`videoIds`** (string array).
- Per hit: publisher or **Freer.master** gate (same as [FEIP21](FEIP21V1_Text.md) **delete**); set **`deleted` = true**.

#### 4. recover

- Same as **delete** with **`videoIds`**, **`deleted` = false**.

#### 5. rate

- **Required:** **`videoId`**, **`rate`**, non-null **CDD** ≥ **`CddRequired`** (**makeVideo** validates **null** **rate**/**CDD**).
- Signer MUST NOT be **`publisher`**.
- **`tRate`** / **`tCdd`** by CDD-weighted average.

### OP_RETURN envelope

```json
{
  "type": "FEIP",
  "sn": "25",
  "ver": "1",
  "name": "Video",
  "data": { }
}
```

### VideoHistory (audit)

[VideoHistory](../../FC-JDK/src/main/java/data/feipData/VideoHistory.java) stores block context, `signer`, `cdd` (**rate**), `op`, `videoId` / `videoIds`, and metadata fields.

## Examples

### publish

```json
{
  "type": "FEIP",
  "sn": "25",
  "ver": "1",
  "name": "Video",
  "data": {
    "op": "publish",
    "title": "Workshop recording",
    "did": "disk:...",
    "format": "video/mp4",
    "summary": "2024 dev meetup"
  }
}
```

### update

```json
{
  "type": "FEIP",
  "sn": "25",
  "ver": "1",
  "name": "Video",
  "data": {
    "op": "update",
    "videoId": "<publish_txid>",
    "title": "Workshop recording (chapters)"
  }
}
```

### delete / recover

```json
{
  "type": "FEIP",
  "sn": "25",
  "ver": "1",
  "name": "Video",
  "data": {
    "op": "delete",
    "videoIds": ["<publish_txid>"]
  }
}
```

### rate

```json
{
  "type": "FEIP",
  "sn": "25",
  "ver": "1",
  "name": "Video",
  "data": {
    "op": "rate",
    "videoId": "<publish_txid>",
    "rate": 5
  }
}
```

## Versioning

|Version|Date|Summary|
|---|---|---|
|1|2026-03-24|Initial spec; aligned with `Feip.VIDEO` (`25`/`1`).|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FEIP0|Envelope, CDD.|
|FEIP23 Sound / FEIP24 Image|Same publish lifecycle and fields (no **`type`** on entity).|
|FEIP21 Text|Adds **`type`** on **Text**; otherwise similar patterns.|
|FEIP6 Master|**delete** / **recover** when signer ≠ publisher.|

## Reference Implementation

|Component|Location|
|---|---|
|`Video`| [FC-JDK/src/main/java/data/feipData/Video.java](../../FC-JDK/src/main/java/data/feipData/Video.java) |
|`VideoOpData`| [FC-JDK/src/main/java/data/feipData/VideoOpData.java](../../FC-JDK/src/main/java/data/feipData/VideoOpData.java) |
|`VideoHistory`| [FC-JDK/src/main/java/data/feipData/VideoHistory.java](../../FC-JDK/src/main/java/data/feipData/VideoHistory.java) |
|`PublishParser.makeVideo` / `parseVideo`| [FEIP/FeipParser/src/main/java/publish/PublishParser.java](../../FEIP/FeipParser/src/main/java/publish/PublishParser.java) |
|`Feip.VIDEO`| [FC-JDK/src/main/java/data/feipData/Feip.java](../../FC-JDK/src/main/java/data/feipData/Feip.java) |

