# FEIP23V1_Sound

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
|Title|Sound|
|Type|FEIP|
|SN|23|
|Version|1|
|Category|Publish|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-24|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

The **Sound** protocol indexes **audio publication references** on-chain (metadata and **`did`** pointer to the asset; the OP_RETURN does not carry audio bytes). Fields mirror [FEIP21 Text](FEIP21V1_Text.md) except there is **no `type`** field on the **Sound** entity. Operations **`publish`**, **`update`**, **`delete`**, **`recover`**, and **`rate`** follow the same patterns: stable **`id`** = **publish** txid, string **`ver`** starting at **`1`** and incrementing on **update**, **`publisher`** = publish signer, CDD-weighted **`tRate`** / **`tCdd`**, and **delete** / **recover** with publisher or FEIP6 **master** bypass.

## Motivation

- **Discoverability** for music, podcasts, and other audio without stuffing binary data into the chain.
- **Same lifecycle as Text** for tooling reuse: soft **delete**, **recover**, and **rate** with CDD weighting.

## Specification

### Sound entity (indexed)

|Field|Source|Description|
|---|---|---|
|`id`|Publish txid|Stable sound id (same as **`soundId`** on **publish**).|
|`title`|Op|Title (required on **publish** / **update** in reference).|
|`ver`|Indexer|Decimal **string**; **`1`** on **publish**; each **update** increments by `parseInt(ver)+1`.|
|`did`|Op|Optional pointer to audio (URI, CID, DISK id, etc.).|
|`lang`|Op|Optional language tag.|
|`authors`|Op|Optional author list.|
|`format`|Op|Optional format hint (e.g. codec or container).|
|`summary`|Op|Optional short description.|
|`publisher`|Publish signer|Publisher FID; **update** requires this FID; **delete** / **recover** allow [FEIP6](FEIP6V1_Master.md) **master** bypass when signer ≠ publisher.|
|`birthTime`, `birthHeight`, `lastTxId`, `lastTime`, `lastHeight`|Tx / block|Lifecycle.|
|`tCdd`, `tRate`|**rate**|CDD-weighted rating aggregate.|
|`deleted`|**delete** / **recover**|Logical deletion flag.|

### `data.op` values

Lowercase: **`publish`**, **`update`**, **`delete`**, **`recover`**, **`rate`** ([SoundOpData](../../FC-JDK/src/main/java/data/feipData/SoundOpData.java)).

### Operations

#### 1. publish

- **Required:** `op`, non-empty **`title`**.
- **Optional:** `did`, `lang`, `authors`, `format`, `summary`. (Op JSON **`ver`** is not applied by reference; entity **`ver`** is **`1`**.)
- **`soundId`** MUST NOT be set; **`id`** = **`soundId`** = this **txid**.
- **CDD:** above `CddCheckHeight`, **`cdd` ≥ `CddRequired`** (**makeSound**).
- Reject if document already exists.

#### 2. update

- **Required:** `soundId`, non-empty **`title`**.
- **Optional:** `did`, `lang`, `authors`, `format`, `summary` — reference overwrites fields from history (nulls may clear stored values).
- Signer MUST equal **`publisher`** (no master bypass in reference).
- Sound must exist, **`deleted`** false; bump **`ver`**.

#### 3. delete

- **Required:** **`soundIds`** (string array of sound **`id`**s).
- Per id: signer = **publisher**, or **Freer.master** equals signer (same rule as [FEIP21](FEIP21V1_Text.md) **delete**); set **`deleted` = true** and refresh last-*.

#### 4. recover

- Same as **delete** with **`soundIds`**, but **`deleted` = false**.

#### 5. rate

- **Required:** `soundId`, **`rate`**, sufficient **`cdd`** (≥ `CddRequired` in **makeSound**).
- Signer MUST NOT be **`publisher`**.
- Update **`tRate`** / **`tCdd`** by CDD-weighted average (same formula as Text / Team).

### OP_RETURN envelope

```json
{
  "type": "FEIP",
  "sn": "23",
  "ver": "1",
  "name": "Sound",
  "data": { }
}
```

### SoundHistory (audit)

[SoundHistory](../../FC-JDK/src/main/java/data/feipData/SoundHistory.java) records block context, `signer`, `cdd` (**rate**), `op`, `soundId` / `soundIds`, and metadata fields.

## Examples

### publish

```json
{
  "type": "FEIP",
  "sn": "23",
  "ver": "1",
  "name": "Sound",
  "data": {
    "op": "publish",
    "title": "Genesis theme",
    "did": "disk:...",
    "lang": "en",
    "format": "audio/ogg",
    "summary": "Short ambient clip."
  }
}
```

### update

```json
{
  "type": "FEIP",
  "sn": "23",
  "ver": "1",
  "name": "Sound",
  "data": {
    "op": "update",
    "soundId": "<publish_txid>",
    "title": "Genesis theme (remaster tag)"
  }
}
```

### delete / recover

```json
{
  "type": "FEIP",
  "sn": "23",
  "ver": "1",
  "name": "Sound",
  "data": {
    "op": "delete",
    "soundIds": ["<publish_txid>"]
  }
}
```

### rate

```json
{
  "type": "FEIP",
  "sn": "23",
  "ver": "1",
  "name": "Sound",
  "data": {
    "op": "rate",
    "soundId": "<publish_txid>",
    "rate": 5
  }
}
```

## Versioning

|Version|Date|Summary|
|---|---|---|
|1|2026-03-24|Initial spec; aligned with `Feip.SOUND` (`23`/`1`).|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FEIP0|Envelope, CDD.|
|FEIP21 Text|Parallel publish/update/delete/rate semantics; Text adds **`type`**.|
|FEIP6 Master|**delete** / **recover** when signer ≠ publisher.|

## Reference Implementation

|Component|Location|
|---|---|
|`Sound`| [FC-JDK/src/main/java/data/feipData/Sound.java](../../FC-JDK/src/main/java/data/feipData/Sound.java) |
|`SoundOpData`| [FC-JDK/src/main/java/data/feipData/SoundOpData.java](../../FC-JDK/src/main/java/data/feipData/SoundOpData.java) |
|`SoundHistory`| [FC-JDK/src/main/java/data/feipData/SoundHistory.java](../../FC-JDK/src/main/java/data/feipData/SoundHistory.java) |
|`PublishParser.makeSound` / `parseSound`| [FEIP/FeipParser/src/main/java/publish/PublishParser.java](../../FEIP/FeipParser/src/main/java/publish/PublishParser.java) |
|`Feip.SOUND`| [FC-JDK/src/main/java/data/feipData/Feip.java](../../FC-JDK/src/main/java/data/feipData/Feip.java) |

