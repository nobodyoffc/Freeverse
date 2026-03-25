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

The Nobody protocol lets an FID **irreversibly abandon** an address by publishing the private key that controls it on-chain. Parsers record the leak, attach the key to the **Freer** entity for that FID, and index a separate **Nobody** record for audit and discovery.

## Motivation

Users may want to prove they no longer control an address, transfer archival responsibility, or exit an identity in a way third parties can verify. Publishing the private key is extreme: **anyone can spend funds** still held at that FID afterward. This protocol therefore encodes a deliberate, one-way act of abandonment, not a casual metadata update.

Implementers and wallet software MUST warn users before constructing such a transaction.

## Specification

### Operations

#### 1. publish (implicit)

There is no `op` field. A valid Nobody transaction carries only the private key in `data`. The operation is accepted or rejected as a whole per the parsing rules below.

**`data` Fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|prikey|Y|String|Private key whose derived Freecash address (FID) MUST equal the transaction signer (first-input FID). Encoding is whatever the reference implementation accepts (e.g. WIF or hex) as long as derivation matches.|

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

3. Let `derivedFid` be the Freecash address obtained from `prikey` (derive public key, then FID). `derivedFid` MUST equal the **signer** (FID of the first transaction input). If not equal, the operation is ignored.

4. If a **Nobody** record already exists for `signer` (same id as signer FID), the operation is rejected; no state change.

5. A **Freer** document MUST already exist for `signer`. If not found, the operation is rejected; no state change.

6. On success:
   - Update **Freer** for `signer`: set `prikey` to `data.prikey`, set `lastHeight` to the current block height.
   - Index a **Nobody** entity with `id` = signer FID, `prikey`, `leakTime` (block timestamp), `leakHeight`, `leakTxId` (this txid), `leakTxIndex`.

### Output

The Nobody protocol updates the **Freer** entity and creates a **Nobody** entity. It also appends **FreerHist** in `FREER_HISTORY` (one row per successful tx). API layers may expose a unified history shape (e.g. `CidHist`); normative stored history for this protocol is **FreerHist** as below.

**Freer entity** (keyed by FID), fields touched by Nobody:

|Field|Type|Description|
|---|---|---|
|prikey|String|Leaked private key from the successful operation.|
|lastHeight|Long|Block height of the Nobody transaction.|

**Nobody entity** (keyed by FID = signer):

|Field|Type|Description|
|---|---|---|
|id|String|Signer FID.|
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
|signer|String|The FID that performed the operation|
|sn|String|"4"|
|ver|String|"1"|
|prikey|String|The published private key|

## Examples

### Example 1: Successful leak

**Signer:** `FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV` (must match key derived from `prikey`)

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

**Result:** Freer gains `prikey` and updated `lastHeight`; Nobody index holds leak metadata; FreerHist row indexed.

### Example 2: Wrong key

If `prikey` derives to a different FID than the signer, the operation is **ignored**.

### Example 3: Duplicate Nobody

If a Nobody record already exists for this signer, a second transaction is **rejected** (no Freer update).

## Versioning

|Version|Changes|
|---|---|
|1|Current version|

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
|Rollback|`FEIP/FeipParser/src/main/java/identity/IdentityRollbacker.java` → `rollbackCid` (Freer + FreerHist reparse; see implementation for Nobody index consistency on reorg)|
