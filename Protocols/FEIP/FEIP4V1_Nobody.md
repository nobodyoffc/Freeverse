# FEIP4V1_Nobody

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
|Title|Nobody|
|Type|FEIP|
|SN|4|
|Version|1|
|Category|Identity|
|Status|Active|
|Author|C_armX|
|Created|2026-03-20|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

The Nobody protocol marks an FID as **irreversibly abandoned** by publishing the private key that controls it on-chain. The owner may announce its own key, and **anyone may announce a key that has leaked**. Parsers record the leak, attach the key to the **Freer** entity for that FID, and index a separate **Nobody** record for audit and discovery.

## Motivation

Users may want to prove they no longer control an address, transfer archival responsibility, or exit an identity in a way third parties can verify. Publishing the private key is extreme: **anyone can spend funds** still held at that FID afterward. This protocol therefore encodes a deliberate, one-way act of abandonment, not a casual metadata update.

A key that has really leaked usually cannot be announced by its own FID. Sweepers spend whatever the FID holds as soon as it arrives, so it never gathers the 1 CDD that every FEIP operation needs (see FEIP0). A published private key proves itself, though: whoever can publish it has shown that the FID it controls is compromised. So the announcement may be signed by any FID, which pays the fee and the CDD from its own coins.

Implementers and wallet software MUST warn users before constructing such a transaction, and MUST show which FID the key controls.

## Specification

### Operations

#### 1. publish (implicit)

There is no `op` field. A valid Nobody transaction carries only the private key in `data`. The operation is accepted or rejected as a whole per the parsing rules below.

**`data` Fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|prikey|Y|String|The private key to announce, as 64 hex characters or a compressed WIF. The FID it controls (the **target**) is marked as Nobody. It may be the signer's own key or any other FID's.|

**OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "4",
  "ver": "1",
  "name": "Nobody",
  "data": {
    "prikey": "<private-key>"
  }
}
```

### Parsing Rules

1. The OP_RETURN MUST parse as JSON and deserialize to the expected `data` shape. Otherwise the operation is ignored.

2. `data.prikey` MUST NOT be null. If null, the operation is ignored.

3. Let `target` be the Freecash address obtained from `prikey` (derive public key, then FID). If `prikey` is not a valid private key, the operation is ignored. The **signer** (FID of the first transaction input) may be `target` or any other FID; it is recorded as the `announcer`.

4. If a **Nobody** record already exists for `target`, the operation is rejected; no state change. The first valid announcement of a key wins.

5. A **Freer** document MUST already exist for `target`. If not found, the operation is rejected; no state change.

6. On success:
   - Update **Freer** for `target`: set `prikey` to `data.prikey`, set `lastHeight` to the current block height.
   - Index a **Nobody** entity with `id` = `target`, `announcer` = signer, `prikey`, `leakTime` (block timestamp), `leakHeight`, `leakTxId` (this txid), `leakTxIndex`.

7. The general CDD rule of FEIP0 applies to the transaction, and so to the announcer's coins.

8. An announcement signed by an FID that is already a Nobody is ignored (FEIP0 rule 10). Anyone else may still announce the key.

### Effect on Other Protocols

After a successful announcement, every FEIP operation signed by the target is ignored by all parsers ([FEIP0](FEIP0V1_FEIP.md), General Rule 10). What the target did before the announcement stands.

### Output

The Nobody protocol updates the **Freer** entity and creates a **Nobody** entity. It also appends **FreerHist** in `FREER_HISTORY` (one row per successful tx). API layers may expose a unified history shape (e.g. `CidHist`); normative stored history for this protocol is **FreerHist** as below.

**Freer entity** (keyed by FID), fields touched by Nobody:

|Field|Type|Description|
|---|---|---|
|prikey|String|Leaked private key from the successful operation.|
|lastHeight|Long|Block height of the Nobody transaction.|

**Nobody entity** (keyed by the target FID):

|Field|Type|Description|
|---|---|---|
|id|String|Target FID: the FID the leaked key controls.|
|announcer|String|Signer of the announcement. Equals `id` when the FID announced its own key.|
|prikey|String|Leaked private key.|
|leakTime|Long|Block timestamp of the leak transaction.|
|leakHeight|Long|Block height of the leak transaction.|
|leakTxId|String|Transaction id.|
|leakTxIndex|Integer|Transaction index in block.|

**FreerHist** (keyed by txid; no `op` field is set for Nobody in the reference implementation):

|Field|Type|Description|
|---|---|---|
|id|String|Transaction ID|
|height|Long|Block height|
|index|Integer|Transaction index in block|
|time|Long|Block timestamp|
|signer|String|The FID that performed the operation (the announcer)|
|fid|String|The target FID. Histories written before third-party announcements were accepted lack it; their target is `signer`.|
|sn|String|"4"|
|ver|String|"1"|
|prikey|String|The published private key|

## Examples

### Example 1: Successful leak

**Signer:** `FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV` (the FID the key controls, announcing its own key)

**OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "4",
  "ver": "1",
  "name": "Nobody",
  "data": {
    "prikey": "<valid-private-key-for-that-FID>"
  }
}
```

**Precondition:** Freer document exists for the signer; no Nobody document yet for that id.

**Result:** Freer gains `prikey` and updated `lastHeight`; Nobody index holds leak metadata with `announcer` equal to the signer; FreerHist row indexed.

### Example 2: Announcing someone else's leaked key

**Signer:** any FID with at least 1 CDD, say `FEk41Kqjar45fLDriztUDTUkdki7mmcjWK`.

**Data:** the leaked key of `FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV`.

**Result:** `FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV` becomes Nobody, with `announcer` = `FEk41Kqjar45fLDriztUDTUkdki7mmcjWK`. The signer's own Freer is unchanged.

### Example 3: Invalid key

If `prikey` is not a valid private key, the operation is **ignored**.

### Example 4: Duplicate Nobody

If a Nobody record already exists for the target, a later announcement of the same key, by anyone, is **rejected** (no Freer update).

## Versioning

|Version|Changes|
|---|---|
|1|Current version. Since 2026-11-03 the announcement may be signed by any FID, not only the FID the key controls, and an announced FID can sign no further FEIP operation (FEIP0 rule 10). This only accepts operations that were ignored before, so no earlier valid operation changes meaning.|

## Related Protocols

| Protocol                                    |Relationship|
|---------------------------------------------|---|
| [FEIP0_FEIP](FEIP0V1_FEIP.md)               |General FEIP rules (signer, CDD, OP_RETURN limits).|
| [FEIP3_CID](FEIP3V4_CID.md)                 |CID on the same Freer entity.|
| [FEIP6_Master](FEIP6V1_Master.md)           |Master address on the same Freer entity.|
| [FEIP9_Home](FEIP9V1_Home.md)               |Home links on the same Freer entity.|
| [FEIP10_NoticeFee](FEIP10V1_NoticeFee.md)   |Notice fee on the same Freer entity.|
| [FEIP16_Reputation](FEIP16V1_Reputation.md) |Reputation on the same Freer entity.|

## Reference Implementation

|Component|Location|
|---|---|
|OpData|`FC-JDK/src/main/java/data/feipData/NobodyOpData.java`|
|Entity (Nobody)|`FC-JDK/src/main/java/data/fchData/Nobody.java`|
|Entity (Freer)|`FC-JDK/src/main/java/data/fchData/Freer.java`|
|History|`FC-JDK/src/main/java/data/feipData/FreerHist.java`|
|Parser|`FEIP/FeipParser/src/main/java/identity/IdentityParser.java` → `makeNobody()`, `parseNobody()`|
|Dispatcher|`FEIP/FeipParser/src/main/java/startFEIP/FileParser.java` → `case NOBODY`|
|Rollback|`FEIP/FeipParser/src/main/java/identity/IdentityRollbacker.java` → `rollbackCid` / `rebuildFreers`: affected FIDs are the targets of the rolled-back histories; their FEIP fields and Nobody records are removed and rebuilt from the surviving histories matched by `signer` or `fid`.|
