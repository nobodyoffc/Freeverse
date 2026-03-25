# FEIP5V3_Service

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
|Title|Service|
|Type|FEIP|
|SN|5|
|Version|3|
|Category|Finance|
|Status|Active|
|Author|C_armX, No1_NrC7|
|Created|2026-03-20|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

FEIP5 Service is the **on-chain registry layer** for decentralized services in the Freeverse ecosystem: anyone can publish and manage service offerings—API endpoints, storage, relay, and other components—with structured metadata, optional pricing, and links to protocols and code. Each service is keyed by **`sid`**, which is the **`id`** of the **Service** entity (the txid of its `publish` transaction).

## Motivation

Applications need a neutral place to discover **who runs what**, under which terms, and how it connects to on-chain protocol and code registries. A centralized directory censors or goes stale; an on-chain **Service** record gives every participant the same facts: owner, standard name, localized names, home URLs (including API entry points), dependencies on `pid` / `codeId` / other `sid`, and optional commercial fields (prices, session length, credit limits). CDD-weighted **rate** operations attach a costed quality signal without replacing human judgment.

## Specification

### Identifier and envelope

- **Serial number (`sn`)**: `"5"`.
- **Version (`ver`)**: `"3"`.
- **Protocol name (envelope `name`)**: `"Service"`.
- **`id`**: The primary key of the **Service** entity (inherited from `FcEntity`), set to the txid of the transaction that **published** this service. When quoted independently in operation data, it is called **`sid`**. After publication, **`update`** and **`rate`** refer to an existing service by its `sid`.

The OP_RETURN MUST be a UTF-8 JSON object following [FEIP0V1_FEIP](FEIP0V1_FEIP.md) (type `FEIP`, `sn`, `ver`, `name`, optional `pid`/`did` on the envelope, and `data` for the operation).

### Operations

Operation names in `data.op` are lowercase: `publish`, `update`, `stop`, `recover`, `close`, `rate`.

#### 1. publish

Create a new on-chain service. The new **`sid`** is the **txid** of this transaction.

**`data` fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: `publish`|
|stdName|Y|String|Standard (e.g. English) name. MUST NOT be null or empty.|
|sid|N|String|MUST NOT be set on `publish` (the reference parser rejects a non-null `sid`).|
|localNames|N|Object|Map of locale or language tag → display name (e.g. `{"zh-CN": "我的服务"}`).|
|desc|N|String|Human-readable description.|
|ver|N|String|Version string of the service offering.|
|type|N|String|Service type label (implementation-defined; see `ServiceType` in reference).|
|components|N|List\<String\>|Logical component names.|
|dealer|N|String|Dealer FID (see validation with `dealerPubkey` below).|
|dealerPubkey|N|String|Dealer public key; if both `dealer` and `dealerPubkey` are present, `dealer` MUST match the FCH address derived from `dealerPubkey`.|
|home|N|Object|Map of string keys to URLs or locators (e.g. API URL under key `api`, org/doc URLs).|
|waiters|N|List\<String\>|FIDs to notify.|
|protocols|N|List\<String\>|Referenced protocol ids (`pid`).|
|codes|N|List\<String\>|Referenced code ids (`codeId`).|
|services|N|List\<String\>|Referenced other service ids (`sid`).|
|params|N|Any|Opaque service-specific parameters (JSON object or other JSON value).|
|pricePerKB|N|String|Pricing: FCH per KB (string form as stored by reference).|
|pricePerKBIn|N|String|Price for incoming data (requests) per KB.|
|pricePerKBOut|N|String|Price for outgoing data (responses) per KB.|
|pricePerKBDay|N|String|Storage price per KB per day.|
|minPayment|N|String|Minimum payment.|
|pricePerRequest|N|String|Price per request.|
|sessionDays|N|String|Session duration in days.|
|consumeViaShare|N|String|Revenue share for consumption (e.g. `"0.5"`).|
|orderViaShare|N|String|Revenue share for orders.|
|currency|N|String|Currency label (e.g. `fch`).|
|minCredit|N|String|Minimum credit.|
|maxDataSize|N|String|Maximum data size hint.|
|dataExpiresInDays|N|String|Data retention / expiry in days.|

**OP_RETURN example:**

```json
{
  "type": "FEIP",
  "sn": "5",
  "ver": "3",
  "name": "Service",
  "data": {
    "op": "publish",
    "stdName": "ExampleAPI",
    "ver": "1",
    "desc": "Public read API for chain data.",
    "type": "API@READ",
    "home": {
      "api": "https://api.example.com/v1",
      "doc": "https://docs.example.com"
    },
    "protocols": ["txProtocol1"],
    "codes": ["txCode1"],
    "minPayment": "0.0001"
  }
}
```

#### 2. update

Replace mutable metadata of an existing service. Only the **owner** may update (unless extended by master rules in the reference parser for bulk ops only).

**`data` fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: `update`|
|sid|Y|String|Existing service id (publish txid).|
|stdName|Y|String|Updated standard name. MUST NOT be null or empty.|
|localNames|N|Object|Updated locale map.|
|desc|N|String|Updated description.|
|ver|N|String|Updated version.|
|type|N|String|Updated type.|
|components|N|List\<String\>|Updated components.|
|dealer|N|String|Updated dealer FID.|
|dealerPubkey|N|String|Updated dealer pubkey (same validation as `publish`).|
|home|N|Object|Updated home map.|
|waiters|N|List\<String\>|Updated waiters.|
|protocols|N|List\<String\>|Updated protocol ids.|
|codes|N|List\<String\>|Updated code ids.|
|services|N|List\<String\>|Updated dependent service ids.|
|params|N|Any|Updated params.|
|pricePerKB, pricePerKBIn, pricePerKBOut, pricePerKBDay, minPayment, pricePerRequest, sessionDays, consumeViaShare, orderViaShare, currency, minCredit, maxDataSize, dataExpiresInDays|N|String|Updated pricing / policy fields when present.|

#### 3. stop

Mark one or more owned services as inactive (`active` = false).

**`data` fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: `stop`|
|sids|Y|List\<String\>|Non-empty list of `sid` values to affect.|

#### 4. recover

Mark one or more owned services as active again (`active` = true), unless the service is **closed**.

**`data` fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: `recover`|
|sids|Y|List\<String\>|Non-empty list of `sid` values to affect.|

#### 5. close

Permanently close one or more services (`closed` = true, `active` = false). Closed services MUST NOT be updated.

**`data` fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: `close`|
|sids|Y|List\<String\>|Non-empty list of `sid` values to affect.|
|closeStatement|N|String|Optional message recorded in **ServiceHistory** only.|

#### 6. rate

Submit a numeric rating for someone else's service, weighted by the transaction's **CDD**. The **signer MUST NOT** be the service owner.

**`data` fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: `rate`|
|sid|Y|String|Target service id.|
|rate|Y|Integer|Rating value, 0–5.|

### Parsing rules

1. **Invalid JSON or wrong envelope** for this FEIP: ignore; no state change ([FEIP0V1_FEIP](FEIP0V1_FEIP.md)).

2. **`publish`**
   - `stdName` MUST be present and non-empty. `sid` in `data` MUST be absent (or null); otherwise the reference parser rejects.
   - Let `sid = txid`. If a **Service** with id `sid` already exists, ignore (duplicate publish).
   - If both `dealer` and `dealerPubkey` are set, they MUST be consistent with pubkey-to-address derivation; otherwise ignore.
   - Create entity with `owner` = signer, `birthTime` / `birthHeight`, `active` = true, `closed` = false, `last*` from this tx, and fields copied from the op.

3. **`update`**
   - `sid` and non-empty `stdName` are required; otherwise ignore.
   - Load service by `sid`. If missing, ignore.
   - If `closed` is true, ignore.
   - If `owner` ≠ signer, ignore.
   - The reference implementation does **not** require `active` = true for `update` (unlike FEIP2 Code / FEIP15 App in the current parser).
   - Overwrite mutable fields; refresh `lastTxId`, `lastTime`, `lastHeight`. Re-validate `dealer` / `dealerPubkey` when both present.

4. **`stop` / `recover` / `close`**
   - `sids` MUST be non-null and non-empty; otherwise ignore.
   - For each `sid`, load the service. Skip if already **`closed`**.
   - **Authorization**: signer is **`owner`**, or the signer's **Freer** record satisfies the reference **master** check (see [Reference Implementation](#reference-implementation)).
   - Apply state changes and update `last*` on each modified service.

5. **`rate`**
   - `sid` required; `rate` in **0–5**; CDD ≥ `CddRequired`; signer ≠ `owner`; otherwise ignore.
   - Update `tRate` / `tCdd` with CDD-weighted mean (same formula as FEIP2 Code).
   - Refresh `lastTxId`, `lastTime`, `lastHeight`.

6. **Order**: Strict blockchain order per FEIP0.

### Output

#### Service entity (keyed by `id`)

|Field|Type|Description|
|---|---|---|
|id|String|publish txid.|
|stdName|String|Standard name.|
|localNames|Map\<String, String\>|Localized names.|
|desc|String|Description.|
|ver|String|Version string.|
|type|String|Service type.|
|components|List\<String\>|Component names.|
|dealer|String|Dealer FID.|
|dealerPubkey|String|Dealer public key.|
|home|Map\<String, String\>|URLs / locators by key.|
|waiters|List\<String\>|Waiter FIDs.|
|protocols|List\<String\>|Protocol ids.|
|codes|List\<String\>|Code ids.|
|services|List\<String\>|Other service ids.|
|params|Any|Opaque parameters.|
|pricePerKB, pricePerKBIn, pricePerKBOut, pricePerKBDay, minPayment, pricePerRequest, sessionDays, consumeViaShare, orderViaShare, currency, minCredit, maxDataSize, dataExpiresInDays|String|Pricing and policy fields.|
|owner|String|Signer of `publish`.|
|birthTime, birthHeight|Long|Birth block time / height.|
|lastTxId, lastTime, lastHeight|Various|Last affecting operation.|
|tCdd|Long|CDD sum for ratings.|
|tRate|Float|CDD-weighted mean rating.|
|active|Boolean|Active flag.|
|closed|Boolean|Closed flag.|
|closeStatement|String|Reserved on entity; reference `close` stores statement on history.|

#### ServiceHistory (keyed by txid `id`, one row per successful operation)

|Field|Type|Description|
|---|---|---|
|id|String|This operation's txid.|
|height, index, time|Various|Block context.|
|signer|String|Signer FID.|
|op|String|Operation name.|
|sid|String|Target id for `publish` / `update` / `rate` (`publish`: equals `id`).|
|sids|List\<String\>|For `stop` / `recover` / `close`.|
|stdName, localNames, desc, ver, type, components, dealer, dealerPubkey, home, waiters, protocols, codes, services, params|Various|Copies when present.|
|Pricing fields|String|As in entity when present.|
|rate|Integer|For `rate`.|
|cdd|Long|CDD snapshot for `rate`.|
|closeStatement|String|For `close` when provided.|

## Examples

### Example 1: Publish

**OP_RETURN** (abbreviated):

```json
{
  "type": "FEIP",
  "sn": "5",
  "ver": "3",
  "name": "Service",
  "data": {
    "op": "publish",
    "stdName": "DiskGateway",
    "desc": "DISK-compatible gateway.",
    "home": { "api": "https://disk.example.com" },
    "protocols": ["txPidFeipDisk"]
  }
}
```

**Result:** New **Service** with `id` = this txid (`sid`), `owner` = signer, `active` = true, `closed` = false.

### Example 2: Update

```json
{
  "type": "FEIP",
  "sn": "5",
  "ver": "3",
  "name": "Service",
  "data": {
    "op": "update",
    "sid": "txServicePublish1",
    "stdName": "DiskGateway",
    "ver": "2",
    "minPayment": "0.0002"
  }
}
```

### Example 3: Stop / recover / close

Same pattern as FEIP2: `sids` lists; `close` may include `closeStatement`.

### Example 4: Rate

Another FID rates `txServicePublish1` with `"rate": 5` and sufficient CDD; owner cannot rate their own service.

## Versioning

|Version|Changes|
|---|---|
|3|Current version; aligns with `Feip.FeipProtocol.SERVICE` (`"5"`, `"3"`) and `ConstructParser`.|
|…|Earlier versions: fields for pricing, components, dealer, bulk lifecycle ops, CDD-weighted ratings.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|[FEIP0V1_FEIP](FEIP0V1_FEIP.md)|Envelope, CDD, parsing order.|
|[FEIP1V7_Protocol](FEIP1V7_PROTOCOL.md)|Services reference `pid`.|
|[FEIP2V7_Code](FEIP2V7_Code.md)|Services reference `codeId`.|
|FEIP6 Master|Bulk `stop` / `recover` / `close` authorization via `Freer.master`.|
|[FEIP15V1_APP](FEIP15V1_APP.md)|Apps may list dependent `sid` values.|

## Reference Implementation

|Component|Location|
|---|---|
|Op payload|`FC-JDK/src/main/java/data/feipData/ServiceOpData.java`|
|Entity|`FC-JDK/src/main/java/data/feipData/Service.java`|
|History|`FC-JDK/src/main/java/data/feipData/ServiceHistory.java`|
|Parse / apply|`FEIP/FeipParser/src/main/java/construct/ConstructParser.java` → `makeService()`, `parseService()`|
|Rollback / reparse|`FEIP/FeipParser/src/main/java/construct/ConstructRollbacker.java`|
|Constants|`FC-JDK/src/main/java/data/feipData/Feip.java` → `FeipProtocol.SERVICE`|
|CDD gates|`FEIP/FeipParser/src/main/java/startFEIP/StartFEIP.java` → `CddCheckHeight`, `CddRequired`|

**Bulk authorization note:** For `stop` / `recover` / `close`, the reference checks `Freer` on the signer and `master` (see FEIP6). **Service `update` does not use the master shortcut** in the current reference—only the listed owner may update.
