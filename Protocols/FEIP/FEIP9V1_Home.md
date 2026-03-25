# FEIP9V1_Home

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
|Title|Home|
|Type|FEIP|
|SN|9|
|Version|1|
|Category|Identity|
|Status|Active|
|Author|C_armX, No1_NrC7|
|Created|2026-03-20|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

The Home protocol lets an FID attach a **map of string keys to string values** describing homepage or related links. Operations are **register** (replace the stored map) and **unregister** (clear home data when a specific primary link key is present).

## Motivation

FIDs are opaque. A small, structured `home` object on-chain gives explorers a standard place to find URLs or labels (e.g. a canonical `"home"` URL) without a central directory.

## Specification

### Operations

#### 1. register

Set or replace the signer's **Freer.home** map.

**`data` Fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: `register` (exact string matched by the reference parser).|
|home|Y|Object|JSON object with string keys and string values (`Map<String,String>`). MUST be present (not null).|

**OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "9",
  "ver": "1",
  "name": "Home",
  "data": {
    "op": "register",
    "home": {
      "home": "https://example.com/me",
      "blog": "https://example.com/blog"
    }
  }
}
```

#### 2. unregister

Clear **Freer.home** when the current state satisfies the parser's eligibility checks (see Parsing Rules).

**`data` Fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: `unregister`.|

**OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "9",
  "ver": "1",
  "name": "Home",
  "data": {
    "op": "unregister"
  }
}
```

### Parsing Rules

#### register

1. `data.home` MUST NOT be null. If null, the operation is ignored.

2. `data.op` MUST be exactly `register` (case-sensitive as in the reference code). Otherwise the operation is ignored.

3. If a **Freer** exists for the signer, set `home` to `data.home` and set `lastHeight` to the current block height.

4. If no **Freer** exists, create one with `id` = signer, set `home` to `data.home`, and set `lastHeight`.

#### unregister

1. `data.op` MUST be exactly `unregister` (case-sensitive as in the reference code). Otherwise the operation is ignored.

2. A **Freer** MUST exist for the signer. If not found, the operation fails (no state change).

3. Let `current` be `Freer.home`. Unregister succeeds only if:
   - `current` is not null,
   - `current` is not empty,

4. On success, set `Freer.home` to `null` and update `lastHeight`.

5. If condition (3) fails, the operation fails (no state change).

### Output

**Freer entity** (keyed by FID), fields touched by Home:

|Field|Type|Description|
|---|---|---|
|home|Map\<String,String\>|Home links; `null` after successful unregister.|
|lastHeight|Long|Block height of the latest Home operation.|

**FreerHist** (keyed by txid):

|Field|Type|Description|
|---|---|---|
|id|String|Transaction ID|
|height|Long|Block height|
|index|Integer|Transaction index in block|
|time|Long|Block timestamp|
|signer|String|Signer FID|
|sn|String|"9"|
|ver|String|"1"|
|op|String|`register` or `unregister`|
|home|Map\<String,String\>|Payload map from the operation|

Unified history DTOs (e.g. **FreerHist**) MAY aggregate several protocols.

## Examples

### Example 1: First-time register (new Freer)

Signer has no Freer row yet. **register** with `home: { "blog": "https://a.com" }` creates Freer with that map and `lastHeight`.

### Example 2: Update links

Signer already has a Freer. **register** replaces `home` entirely with the new object.

### Example 3: Unregister

Current Freer has `home.blog` = `"https://a.com"`. **unregister** with `op: "unregister"` clears `home` to `null`.

### Example 4: Unregister blocked

Current Freer has not `home`. **unregister** fails; Freer is unchanged.

## Versioning

|Version|Changes|
|---|---|
|1|Current version|

## Related Protocols

| Protocol                                    |Relationship|
|---------------------------------------------|---|
| [FEIP0_FEIP](FEIP0V1_FEIP.md)               |General FEIP rules.|
| [FEIP3_CID](FEIP3V4_CID.md)                 |CID on the same Freer entity.|
| [FEIP4_Nobody](FEIP4V1_Nobody.md)           |Nobody leak on the same Freer entity.|
| [FEIP6_Master](FEIP6V1_Master.md)           |Master on the same Freer entity.|
| [FEIP10_NoticeFee](FEIP10V1_NoticeFee.md)   |Notice fee on the same Freer entity.|
| [FEIP16_Reputation](FEIP16V1_Reputation.md) |Reputation on the same Freer entity.|

## Reference Implementation

|Component|Location|
|---|---|
|OpData|`FC-JDK/src/main/java/data/feipData/HomeOpData.java`|
|Entity|`FC-JDK/src/main/java/data/fchData/Freer.java`|
|History|`FC-JDK/src/main/java/data/feipData/FreerHist.java`|
|Parser|`FEIP/FeipParser/src/main/java/identity/IdentityParser.java` → `makeHome()`, `parseHome()`|
|Dispatcher|`FEIP/FeipParser/src/main/java/startFEIP/FileParser.java` → `case HOME`|
|Rollback|`FEIP/FeipParser/src/main/java/identity/IdentityRollbacker.java` → `rollbackCid`|
