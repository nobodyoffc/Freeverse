# FEIP6V1_Master

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
|Title|Master|
|Type|FEIP|
|SN|6|
|Version|1|
|Category|Identity|
|Status|Active|
|Author|C_armX|
|Created|2026-03-20|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

The Master protocol lets an FID designate another FID as its **master address**. By publishing a fixed promise string ("The master owns all my rights.") together with the master FID, the signer irrevocably delegates ownership semantics to that master. Master assignment is **write-once**: once set successfully, it cannot be changed or removed.

The signer MAY also attach an encrypted copy of its private key (`cipherPriKey` + `alg`) so the master can recover spending control. These optional fields are recorded in history but are **not** stored on the Freer entity.

## Motivation

Users who manage multiple addresses may want a single "root" identity that controls subordinate FIDs. Setting a master address provides a discoverable on-chain record of this relationship, enabling wallets and services to treat the master as the authoritative owner.

The write-once constraint prevents the relationship from being revoked or hijacked: once a master is declared, the promise is permanent. This gives strong guarantees to systems that rely on the master relationship for access control or identity aggregation.

## Specification

### Operations

#### 1. set (implicit)

There is no `op` discriminator. A valid Master transaction carries the required fields in `data`.

**`data` Fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|master|Y|String|FID of the master address. MUST pass `KeyTools.isGoodFid()` validation (valid Base58Check Freecash address).|
|promise|Y|String|MUST be exactly `"The master owns all my rights."`. Any other value causes the operation to be ignored.|
|cipherPriKey|N|String|The signer's private key encrypted to the master's public key. Stored in history only.|
|alg|N|String|Encryption algorithm used for `cipherPriKey`. Stored in history only.|

**OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "6",
  "ver": "1",
  "name": "Master",
  "data": {
    "master": "F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW",
    "promise": "The master owns all my rights.",
    "cipherPriKey": "<encrypted-private-key>",
    "alg": "ECC256k1-AES256CBC"
  }
}
```

### Parsing Rules

1. The OP_RETURN MUST parse as JSON. If parsing fails or `data` is null, the operation is ignored.

2. `data.promise` MUST NOT be null. If null, the operation is ignored.

3. `data.promise` MUST equal exactly `"The master owns all my rights."`. If not, the operation is ignored.

4. `data.master` MUST be a valid Freecash address. If invalid or null, the operation is ignored.

5. **Write-once check:** If a **Freer** already exists for the signer and its `master` field is non-null and non-blank, the operation is **rejected**. Master cannot be changed once set.

6. If a **Freer** exists and its `master` is null or blank: set `master` to `data.master` and set `lastHeight` to the current block height.

7. If no **Freer** exists: create a new Freer with `id` = signer, `master` = `data.master`, and `lastHeight` = current block height.

8. `cipherPriKey` and `alg` from `data` are stored on the **FreerHist** record but are **not** written to the Freer entity.

### Output

**Freer entity** (keyed by FID), fields touched by Master:

|Field|Type|Description|
|---|---|---|
|master|String|FID of the master address. Immutable once set.|
|lastHeight|Long|Block height of the Master operation.|

**FreerHist** (keyed by txid):

|Field|Type|Description|
|---|---|---|
|id|String|Transaction ID|
|height|Long|Block height|
|index|Integer|Transaction index in block|
|time|Long|Block timestamp|
|signer|String|The FID that performed the operation|
|sn|String|"6"|
|ver|String|"1"|
|master|String|The designated master FID|
|cipherPrikey|String|Encrypted private key (optional; from `data.cipherPriKey`)|
|alg|String|Encryption algorithm (optional; from `data.alg`)|

Note: `cipherPrikey` and `alg` are available only in history, not on the Freer entity. API layers can expose unified history shapes (e.g. `CidHist`); normative stored history is **FreerHist** in `FREER_HISTORY`.

## Examples

### Example 1: Set master with encrypted key backup

**Signer:** `FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV`

**OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "6",
  "ver": "1",
  "name": "Master",
  "data": {
    "master": "F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW",
    "promise": "The master owns all my rights.",
    "cipherPriKey": "a1b2c3...",
    "alg": "ECC256k1-AES256CBC"
  }
}
```

**Precondition:** Freer for signer has `master` = null (or Freer does not exist yet).

**Result:** Freer gains `master: "F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW"` and updated `lastHeight`. FreerHist includes `cipherPrikey` and `alg`.

### Example 2: Set master without key backup

```json
{
  "type": "FEIP",
  "sn": "6",
  "ver": "1",
  "name": "Master",
  "data": {
    "master": "F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW",
    "promise": "The master owns all my rights."
  }
}
```

Works identically; `cipherPrikey` and `alg` are null in history.

### Example 3: Master already set (rejected)

If the signer's Freer already has `master: "Fxxx..."`, a second Master transaction is **rejected** regardless of whether the new master matches. No state change occurs.

### Example 4: Wrong promise string

`"promise": "I grant master rights."` — does not match the required string. Operation is **ignored**.

## Versioning

|Version|Changes|
|---|---|
|1|Current version|

## Related Protocols

|Protocol|Relationship|
|---|---|
|[FEIP0_FEIP](FEIP0V1_FEIP.md)|General FEIP rules (signer, CDD, OP_RETURN limits).|
|[FEIP3_CID](FEIP3V4_CID.md)|CID on the same Freer entity.|
|[FEIP4_Nobody](FEIP4V1_Nobody.md)|Nobody leak on the same Freer entity. Publishing the private key is a stronger act than setting a master.|
|[FEIP9_Home](FEIP9V1_Home.md)|Home links on the same Freer entity.|
|[FEIP10_NoticeFee](FEIP10V1_NoticeFee.md)|Notice fee on the same Freer entity.|
|[FEIP16_Reputation](FEIP16V1_Reputation.md)|Reputation on the same Freer entity.|

## Reference Implementation

|Component|Location|
|---|---|
|OpData|`FC-JDK/src/main/java/data/feipData/MasterOpData.java`|
|Entity|`FC-JDK/src/main/java/data/fchData/Freer.java`|
|History|`FC-JDK/src/main/java/data/feipData/FreerHist.java`|
|Parser|`FEIP/FeipParser/src/main/java/identity/IdentityParser.java` → `makeMaster()`, `parseMaster()`|
|Dispatcher|`FEIP/FeipParser/src/main/java/startFEIP/FileParser.java` → `case MASTER`|
|Rollback|`FEIP/FeipParser/src/main/java/identity/IdentityRollbacker.java` → `rollbackCid` (Freer deletion + FreerHist reparse covers sn 6)|
