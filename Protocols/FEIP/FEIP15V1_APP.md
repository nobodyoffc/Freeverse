# FEIP15V1_APP

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
|Title|APP|
|Type|FEIP|
|SN|15|
|Version|1|
|Category|Finance|
|Status|Active|
|Author|C_armX,No1_NrC7|
|Created|2026-03-20|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

FEIP15 APP is the **on-chain registry layer** for decentralized applications in the Freeverse ecosystem: anyone can publish and manage app metadata—names, types, home links, download entries, and dependencies on protocols, services, and code. Each app is keyed by **`aid`**, which is the **`id`** of the **App** entity (the txid of its `publish` transaction).

## Motivation

Users and integrators need a **neutral catalog** of applications that implement ecosystem protocols—not just binaries on a store, but claims bound to a signing FID, with history and optional community ratings. Linking an **App** to `pid`, `sid`, and `codeId` makes the dependency graph explicit on-chain. Lifecycle operations (`stop`, `recover`, `close`) mirror other registries so wallets and indexers can treat apps, services, and code uniformly.

## Specification

### Identifier and envelope

- **Serial number (`sn`)**: `"15"`.
- **Version (`ver`)**: `"1"`.
- **Protocol name (envelope `name`)**: `"APP"`.
- **`id`**: The primary key of the **App** entity (inherited from `FcEntity`), set to the txid of the transaction that **published** this app. When quoted independently in operation data, it is called **`aid`**. After publication, **`update`** and **`rate`** refer to an existing app by its `aid`.

The OP_RETURN MUST be a UTF-8 JSON object following [FEIP0V1_FEIP](FEIP0V1_FEIP.md) (type `FEIP`, `sn`, `ver`, `name`, optional `pid`/`did` on the envelope, and `data` for the operation).

### Operations

Operation names in `data.op` are lowercase: `publish`, `update`, `stop`, `recover`, `close`, `rate`.

#### 1. publish

Create a new on-chain app. The new **`aid`** is the **txid** of this transaction.

**`data` fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: `publish`|
|stdName|Y|String|Standard name. MUST NOT be null or empty.|
|localNames|N|Object|Map of locale or language tag → display name.|
|desc|N|String|Description.|
|ver|N|String|Version string.|
|types|N|List\<String\>|Application type tags (e.g. categories).|
|home|N|Object|Map of string keys to URLs or locators.|
|downloads|N|List\<Object\>|Download descriptors (see [Download object](#download-object)).|
|waiters|N|List\<String\>|FIDs to notify.|
|protocols|N|List\<String\>|Referenced protocol ids (`pid`).|
|codes|N|List\<String\>|Referenced code ids (`codeId`).|
|services|N|List\<String\>|Referenced service ids (`sid`).|

##### Download object

Each element of `downloads` is a JSON object:

|Field|Required|Type|Description|
|---|---|---|---|
|os|N|String|OS or platform label.|
|link|N|String|Download URL or locator.|
|did|N|String|Document or release id for that artifact.|

**OP_RETURN example:**

```json
{
  "type": "FEIP",
  "sn": "15",
  "ver": "1",
  "name": "APP",
  "data": {
    "op": "publish",
    "stdName": "FreeverseWallet",
    "ver": "1.0.0",
    "desc": "Wallet for FCH and FEIP.",
    "types": ["wallet", "desktop"],
    "home": { "org": "https://wallet.example.com" },
    "downloads": [
      { "os": "macOS", "link": "https://releases.example.com/wallet.dmg", "did": "release-1.0.0" }
    ],
    "protocols": ["txPid1"],
    "codes": ["txCode1"],
    "services": ["txSid1"]
  }
}
```

#### 2. update

Replace mutable metadata. Only the **owner** may update.

**`data` fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: `update`|
|aid|Y|String|Existing app id (publish txid).|
|stdName|Y|String|Updated standard name. MUST NOT be null or empty.|
|localNames|N|Object|Updated locale map.|
|desc|N|String|Updated description.|
|ver|N|String|Updated version.|
|types|N|List\<String\>|Updated type tags.|
|home|N|Object|Updated home map.|
|downloads|N|List\<Object\>|Updated downloads.|
|waiters|N|List\<String\>|Updated waiters.|
|protocols|N|List\<String\>|Updated protocol ids.|
|codes|N|List\<String\>|Updated code ids.|
|services|N|List\<String\>|Updated service ids.|

#### 3. stop / 4. recover / 5. close

Same semantics as FEIP5 Service, with **`aids`** instead of **`sids`**.

|Op|`data` fields|
|---|---|
|`stop`|`op`, `aids` (non-empty)|
|`recover`|`op`, `aids` (non-empty)|
|`close`|`op`, `aids` (non-empty), optional `closeStatement`|

#### 6. rate

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: `rate`|
|aid|Y|String|Target app id.|
|rate|Y|Integer|0–5.|

### Parsing rules

1. **Invalid envelope** for this FEIP: ignore ([FEIP0V1_FEIP](FEIP0V1_FEIP.md)).

2. **`publish`**
   - Non-empty `stdName`; 
   - Since height **4_000_000**, CDD ≥ `CddRequired` when above `CddCheckHeight`.
   - `owner` = signer; `active` = true; `closed` = false; set `birth*` and `last*`.

3. **`update`**
   - `aid` and non-empty `stdName` required.
   - App must exist, not `closed`, `owner` = signer, and **`active` must be true** (reference implementation).
   - Overwrite mutable fields; refresh `last*`.

4. **`stop` / `recover` / `close`**
   - Non-empty `aids`; skip targets already `closed`; owner or **master** authorization as in `parseApp` / `Freer`.
   - `close` sets `closed` = true, `active` = false; optional `closeStatement` on history.

5. **`rate`**
   - `aid`, `rate` 0–5, CDD ≥ `CddRequired`, signer ≠ `owner`.
   - CDD-weighted `tRate` / `tCdd`; refresh `last*`.

6. **Order**: Per FEIP0.

### Output

#### App entity (keyed by `id`)

|Field|Type|Description|
|---|---|---|
|id|String|publish txid.|
|stdName|String|Standard name.|
|localNames|Map\<String, String\>|Localized names.|
|types|List\<String\>|Type tags.|
|desc|String|Description.|
|ver|String|Version string.|
|home|Map\<String, String\>|URLs by key.|
|downloads|List\<Download\>|Download entries (`os`, `link`, `did`).|
|waiters|List\<String\>|Waiter FIDs.|
|protocols|List\<String\>|Protocol ids.|
|codes|List\<String\>|Code ids.|
|services|List\<String\>|Service ids.|
|owner|String|Signer of `publish`.|
|birthTime, birthHeight|Long|Birth block.|
|lastTxId, lastTime, lastHeight|Various|Last operation.|
|tCdd|Long|Rating CDD sum.|
|tRate|Float|CDD-weighted mean rating.|
|active|Boolean|Active flag.|
|closed|Boolean|Closed flag.|
|closeStatement|String|Reserved on entity; reference stores close text on history.|

#### AppHistory (keyed by txid `id`)

|Field|Type|Description|
|---|---|---|
|id|String|Operation txid.|
|height, index, time|Various|Block context.|
|signer|String|Signer FID.|
|op|String|Operation.|
|aid|String|Target for `publish` / `update` / `rate` (`publish`: equals `id`).|
|aids|List\<String\>|For bulk lifecycle ops.|
|stdName, ver, localNames, desc, types, home, downloads, waiters, protocols, codes, services|Various|When present.|
|rate|Integer|For `rate`.|
|cdd|Long|For `rate`.|
|closeStatement|String|For `close`.|

## Examples

### Example 1: Publish

```json
{
  "type": "FEIP",
  "sn": "15",
  "ver": "1",
  "name": "APP",
  "data": {
    "op": "publish",
    "stdName": "FreeverseWallet",
    "ver": "1.0.0",
    "desc": "wallet for Freeverse."
  }
}
```
### Example 2: Update (owner, active app only)

```json
{
  "type": "FEIP",
  "sn": "15",
  "ver": "1",
  "name": "APP",
  "data": {
    "op": "update",
    "aid": "txAppPublish1",
    "stdName": "FreeverseWallet",
    "ver": "1.0.1",
    "desc": "Bugfix release."
  }
}
```

If the app was previously **`stop`**ped (`active` = false), the reference parser rejects `update` until **`recover`**.

### Example 3: Bulk stop

```json
{
  "type": "FEIP",
  "sn": "15",
  "ver": "1",
  "name": "APP",
  "data": {
    "op": "stop",
    "aids": ["txAppPublish1"]
  }
}
```

## Related Protocols

|Protocol|Relationship|
|---|---|
|[FEIP0V1_FEIP](FEIP0V1_FEIP.md)|Envelope, CDD.|
|[FEIP1V7_Protocol](FEIP1V7_PROTOCOL.md)|Apps list `pid`.|
|[FEIP2V7_Code](FEIP2V7_Code.md)|Apps list `codeId`.|
|[FEIP5V3_Service](FEIP5V3_Service.md)|Apps list `sid`.|
|FEIP6 Master|Bulk lifecycle authorization.|

## Reference Implementation

|Component|Location|
|---|---|
|Op payload|`FC-JDK/src/main/java/data/feipData/AppOpData.java`|
|Entity|`FC-JDK/src/main/java/data/feipData/App.java`|
|History|`FC-JDK/src/main/java/data/feipData/AppHistory.java`|
|Parse / apply|`FEIP/FeipParser/src/main/java/construct/ConstructParser.java` → `makeApp()`, `parseApp()`|
|Rollback / reparse|`FEIP/FeipParser/src/main/java/construct/ConstructRollbacker.java`|
|Constants|`FC-JDK/src/main/java/data/feipData/Feip.java` → `FeipProtocol.APP`|
|CDD gates|`FEIP/FeipParser/src/main/java/startFEIP/StartFEIP.java`|

