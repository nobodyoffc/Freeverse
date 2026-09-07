# FEIP16V1_Reputation

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
|Title|Reputation|
|Type|FEIP|
|SN|16|
|Version|1|
|Category|Identity|
|Status|Active|
|Author|C_armX|
|Created|2026-03-20|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

The Reputation protocol lets one FID (**rater**, the transaction signer) record a **good** or **bad** rating against another FID (**ratee**, named by `data.fid`). The weight of the rating in the reference implementation is the transaction's **CoinDays Destroyed (CDD)**. Indexed state updates **Freer.reputation**, **Freer.hot**, and recomputed **Freer.weight** for the ratee; each operation is also stored as **RepuHist**.

## Motivation

Raw balances and activity counts do not capture social trust. Binding ratings to **CDD** makes spam expensive and ties reputation changes to meaningful stake movement. Separating **reputation** (signed cumulative score) from **hot** (CDD-weighted attention) allows indexers to display both trust and engagement.

A rating is an **opinion about** a FID, not a transfer to it. It therefore names its subject in the payload and pays nobody: you may rate a FID you have never paid and would never pay, and the rating survives any output arrangement the transaction happens to have.

## Specification

### Operations

#### 1. rate (implicit)

There is no `op` field. The rating is entirely in `data`, **including the ratee**.

**`data` Fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|fid|Y|String|The rated FID (**ratee**). MUST be a valid FCH address. If absent or unparsable, the operation is ignored.|
|rate|Y|String|MUST be `good` or `bad` (see Values.GOOD / Values.BAD in FC-JDK). Any other value ignores the operation.|
|cause|N|String|Optional free-text reason.|

**OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "16",
  "ver": "1",
  "name": "Reputation",
  "data": {
    "fid": "FBBBnQ4mQ8vDbFCiRTxnLvvxSbXhCJz1cP",
    "rate": "good",
    "cause": "Helpful contributor"
  }
}
```

The envelope's `did` MUST NOT be used to carry the ratee. `did` is FEIP0's *document* id — an identifier for an on-chain record, not for a party — and a parser MUST NOT read a ratee from it.

The transaction's outputs are **not** consulted. A rating SHOULD pay nobody; a transaction that happens to pay someone for an unrelated reason still rates whoever `data.fid` names.

### Parsing Rules

1. **CDD gate:** Before accepting the operation, the reference implementation requires `cdd >= StartFEIP.CddRequired` (default **1**, same order of magnitude as FEIP0's post–height-4_000_000 minimum). If below threshold, the operation is ignored.

2. **Data:** `data` MUST deserialize to `ReputationOpData`. `fid` and `rate` MUST NOT be null, and `rate` MUST be `good` or `bad`. If any of those fails, the operation is ignored.

   Refusing an unrecognised `rate` outright is deliberate. The reference parser used to let one through with a **null** reputation delta, which the Freer update then unboxed into a `long` — an NPE reachable from arbitrary on-chain data. There is no sensible delta for a verdict the protocol does not define, so such an operation is not parseable and is dropped where dropping it is free.

3. **Rater:** The **signer** (first-input FID) is `rater`.

4. **Ratee:** The **ratee** is `data.fid`, and it MUST be a valid FCH address. It is taken from the payload alone — neither from `OpReturn.recipient` nor from the envelope's `did`. A parser MUST NOT substitute a sentinel (such as `IndicesNames.NOBODY`) when the field is missing or unparsable; either way the operation is ignored.

5. **Self-rating:** If `rater` equals `ratee`, the operation is ignored. A FID may not rate itself.

6. **RepuHist deltas (per tx):**
   - `hot` = transaction CDD (always set on the history row).
   - If `rate` equals `good`, `reputation` delta on the history row = **+CDD**.
   - If `rate` equals `bad`, `reputation` delta on the history row = **−CDD**.
   - No other `rate` string reaches this point: rule 2 has already ignored the operation.

7. **Freer update:** A **Freer** document MUST exist for **ratee**. If not found, the operation fails (no state change).

8. On success:
   - `Freer.reputation` ← if previous `reputation` is null, set to this tx's reputation delta; otherwise add this tx's delta to previous.
   - `Freer.hot` ← if previous `hot` is null, set to this tx's CDD; otherwise add this tx's CDD.
   - `Freer.lastHeight` ← block height.
   - Call `Freer.reCalcWeight()` so `weight` reflects `cd`, `cdd`, and `reputation` per `Weight.calcWeight`.

9. **History:** On success, make **RepuHist** with document id = txid, fields as in Output below.

### Rollback

A reorg deletes the affected **RepuHist** rows and recomputes each touched ratee's aggregates from the history that survives. Because the ratee is carried on the history row, rollback never re-reads the OP_RETURN or the transaction's outputs.

For every ratee named by a deleted row:

- `reputation` ← sum of `reputation` over that ratee's surviving rows, **0** when none survive.
- `hot` ← sum of `hot` over the same rows, **0** when none survive.
- `weight` ← recomputed by `Weight.calcWeight(cd, cdd, reputation)` from the restored `reputation`. Rule 8 recomputes weight on every successful parse, so a rollback that restored only `reputation` and `hot` would leave `weight` derived from a rating history that no longer exists.
- `cd` and `cdd` are not touched. This protocol never writes them.

A ratee whose entire history fell inside the rolled-back range contributes no aggregation bucket; absent history means **zero**, not "unchanged". The deletes must be visible to search before the aggregation runs.

### Output

**Freer entity** (keyed by **ratee** FID), fields touched:

|Field|Type|Description|
|---|---|---|
|reputation|Long|Cumulative signed score from reputation deltas.|
|hot|Long|Cumulative CDD from reputation txs.|
|weight|Long|Recomputed via `reCalcWeight()` after each successful parse.|
|lastHeight|Long|Height of the latest affecting tx.|

**RepuHist** (keyed by txid, index `REPUTATION_HISTORY`):

|Field|Type|Description|
|---|---|---|
|id|String|Transaction ID|
|height|Long|Block height|
|index|Integer|Transaction index in block|
|time|Long|Block timestamp|
|rater|String|Signer FID|
|ratee|String|Rated FID, from `data.fid`|
|rate|String|`good` or `bad`|
|cause|String|Optional|
|reputation|Long|Signed delta for this tx (+CDD or −CDD)|
|hot|Long|This tx's CDD|

**CidHist** and similar DTOs are optional aggregates for APIs; normative per-tx history for this protocol is **RepuHist**.

## Examples

### Example 1: Good rating

Signer `FAAA...` broadcasts a transaction whose only outputs are its own change and the OP_RETURN:

```json
{
  "type": "FEIP",
  "sn": "16",
  "ver": "1",
  "name": "Reputation",
  "data": { "fid": "FBBB...", "rate": "good" }
}
```

Assume CDD = 100. Then `RepuHist` has `reputation: 100`, `hot: 100`, `rater: FAAA...`, `ratee: FBBB...`. Freer for `FBBB...` gains +100 reputation and +100 hot (plus prior totals). Nothing was paid to `FBBB...`.

### Example 2: Bad rating

Same structure with `"rate": "bad"`. Reputation delta is **−CDD**; hot still increases by CDD.

### Example 3: Missing ratee

`data` carries `rate` but no `fid`. The operation is **ignored** under parsing rule 2 — no `RepuHist` row, no `Freer` update. The parser does not fall back to an output owner, and does not record the rating against a sentinel.

### Example 4: A rating in a transaction that also pays someone

The transaction pays `FCCC...` for an unrelated reason and carries the OP_RETURN above naming `FBBB...`. The rating applies to `FBBB...`. Outputs have no bearing on who is rated.

## Versioning

|Version|Changes|
|---|---|
|1|Current version. The ratee is `data.fid`. (Earlier drafts of this document specified the ratee as `OpReturn.recipient`, the first non-signer output owner, with a `"nobody"` sentinel when absent. That reading required paying whomever you rated, lost the rating whenever the output was absent or reordered, and produced carves that confirmed while changing no state. It was never correctly deployed, so it is corrected in place rather than superseded by a new version.) Parsing rule 5 (**self-rating is ignored**) is new: the old recipient-based reading excluded the signer by construction, and naming the ratee in the payload reopens it — without rule 5 a FID could raise its own reputation for the price of its own coin-days. Rule 2 additionally now **ignores** a `rate` that is neither `good` nor `bad`, where the reference parser previously let it through with a null delta and crashed on it.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|[FEIP0_FEIP](FEIP0V1_FEIP.md)|Signer, CDD rules, OP_RETURN limits. Defines `did` as a document id — not a party, and not a ratee.|
|[FEIP3_CID](FEIP3V4_CID.md)|CID on the same Freer entity as reputation aggregates.|
|[FEIP4_Nobody](FEIP4V1_Nobody.md)|Same Freer entity.|
|[FEIP9_Home](FEIP9V1_Home.md)|Same Freer entity.|
|[FEIP10_NoticeFee](FEIP10V1_NoticeFee.md)|Same Freer entity.|
|[FEIP11_NID](FEIP11V1_NID.md)|Identifiers; orthogonal to reputation.|

## Reference Implementation

|Component|Location|
|---|---|
|OpData|`FC-JDK/src/main/java/data/feipData/ReputationOpData.java` (carries `fid`, `rate`, `cause`)|
|Constants|`FC-JDK/src/main/java/constants/Values.java` (`GOOD`, `BAD`)|
|Entity|`FC-JDK/src/main/java/data/fchData/Freer.java` (`reputation`, `hot`, `reCalcWeight`)|
|History|`FC-JDK/src/main/java/data/feipData/RepuHist.java`|
|OpReturn|`FC-JDK/src/main/java/data/fchData/OpReturn.java` (`signer`, `cdd`)|
|FID validation|`FC-JDK/src/main/java/core/crypto/KeyTools.java` → `isGoodFid`|
|Parser|`FEIP/FeipParser/src/main/java/identity/IdentityParser.java` → `makeReputation()`, `parseReputation()`|
|CDD threshold|`FEIP/FeipParser/src/main/java/startFEIP/StartFEIP.java` → `CddRequired`|
|Dispatcher|`FEIP/FeipParser/src/main/java/startFEIP/FileParser.java` → `case REPUTATION`|
|Rollback|`FEIP/FeipParser/src/main/java/identity/IdentityRollbacker.java` → `rollbackRepu`|
