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

The Reputation protocol lets one FID (**rater**, the transaction signer) record a **good** or **bad** rating against another FID (**ratee**). The weight of the rating in the reference implementation is the transaction's **CoinDays Destroyed (CDD)**. Indexed state updates **Freer.reputation**, **Freer.hot**, and recomputed **Freer.weight** for the ratee; each operation is also stored as **RepuHist**.

## Motivation

Raw balances and activity counts do not capture social trust. Binding ratings to **CDD** makes spam expensive and ties reputation changes to meaningful stake movement. Separating **reputation** (signed cumulative score) from **hot** (CDD-weighted attention) allows indexers to display both trust and engagement.

## Specification

### Operations

#### 1. rate (implicit)

There is no `op` field. The rating is entirely in `data`, and the **ratee** is taken from the transaction's **recipient** field as produced by the block parser (see Parsing Rules).

**`data` Fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|rate|Y|String|MUST be `good` or `bad` for interoperable behavior (see Values.GOOD / Values.BAD in FC-JDK). The reference parser applies a non-zero reputation delta only for these two values.|
|cause|N|String|Optional free-text reason.|

**OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "16",
  "ver": "1",
  "name": "Reputation",
  "data": {
    "rate": "good",
    "cause": "Helpful contributor"
  }
}
```

### Parsing Rules

1. **CDD gate:** Before accepting the operation, the reference implementation requires `cdd >= StartFEIP.CddRequired` (default **1**, same order of magnitude as FEIP0's post–height-4_000_000 minimum). If below threshold, the operation is ignored.

2. **Data:** `data` MUST deserialize to `ReputationOpData`. `rate` MUST NOT be null. If null, the operation is ignored.

3. **Rater:** The **signer** (first-input FID) is `rater`.

4. **Ratee (recipient):** The **ratee** is `OpReturn.recipient`, set when building blocks: the **first output owner** that is not the signer, not `Unknown`, and not `OpReturn`. If no such output exists, the parser sets recipient to the sentinel string `"nobody"` (`IndicesNames.NOBODY`). For a rating to apply to a real user, the transaction **SHOULD** include an output paying to the ratee's FID so `recipient` is that FID.

5. **RepuHist deltas (per tx):**
   - `hot` = transaction CDD (always set on the history row).
   - If `rate` equals `good`, `reputation` delta on the history row = **+CDD**.
   - If `rate` equals `bad`, `reputation` delta on the history row = **−CDD**.
   - For any other `rate` string, the reference implementation does **not** set a reputation delta on the history row; combined with Freer update logic this is unsafe for interoperability. Clients MUST use only `good` or `bad`.

6. **Freer update:** A **Freer** document MUST exist for **ratee**. If not found, the operation fails (no state change).

7. On success:
   - `Freer.reputation` ← if previous `reputation` is null, set to this tx's reputation delta; otherwise add this tx's delta to previous. (If `rate` is not `good` or `bad`, the delta is undefined in the reference code; clients MUST only use `good`/`bad`.)
   - `Freer.hot` ← if previous `hot` is null, set to this tx's CDD; otherwise add this tx's CDD.
   - `Freer.lastHeight` ← block height.
   - Call `Freer.reCalcWeight()` so `weight` reflects `cd`, `cdd`, and `reputation` per `Weight.calcWeight`.

8. **History:** On success, make **RepuHist** with document id = txid, fields as in Output below.

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
|ratee|String|Recipient FID (from parser rules)|
|rate|String|`good` or `bad` (MUST for interoperability)|
|cause|String|Optional|
|reputation|Long|Signed delta for this tx (+CDD or −CDD)|
|hot|Long|This tx's CDD|

**CidHist** and similar DTOs are optional aggregates for APIs; normative per-tx history for this protocol is **RepuHist**.

## Examples

### Example 1: Good rating with payment to ratee

Signer `FAAA...` includes an output to `FBBB...` (ratee) and OP_RETURN:

```json
{
  "type": "FEIP",
  "sn": "16",
  "ver": "1",
  "name": "Reputation",
  "data": { "rate": "good" }
}
```

Assume CDD = 100. Then `RepuHist` has `reputation: 100`, `hot: 100`, `rater: FAAA...`, `ratee: FBBB...`. Freer for `FBBB...` gains +100 reputation and +100 hot (plus prior totals).

### Example 2: Bad rating

Same structure with `"rate": "bad"`. Reputation delta is **−CDD**; hot still increases by CDD.

### Example 3: No recipient output

Only signer outputs and OP_RETURN: recipient may become `"nobody"`. **Freer** for `"nobody"` is unlikely to exist—operation typically fails. Wallets SHOULD always add a ratee output.

## Versioning

|Version|Changes|
|---|---|
|1|Current version|

## Related Protocols

|Protocol|Relationship|
|---|---|
|[FEIP0_FEIP](FEIP0V1_FEIP.md)|Signer, CDD rules, OP_RETURN limits.|
|[FEIP3_CID](FEIP3V4_CID.md)|CID on the same Freer entity as reputation aggregates.|
|[FEIP4_Nobody](FEIP4V1_Nobody.md)|Same Freer entity.|
|[FEIP9_Home](FEIP9V1_Home.md)|Same Freer entity.|
|[FEIP10_NoticeFee](FEIP10V1_NoticeFee.md)|Same Freer entity.|
|[FEIP11_NID](FEIP11V1_NID.md)|Identifiers; orthogonal to reputation.|

## Reference Implementation

|Component|Location|
|---|---|
|OpData|`FC-JDK/src/main/java/data/feipData/ReputationOpData.java`|
|Constants|`FC-JDK/src/main/java/constants/Values.java` (`GOOD`, `BAD`)|
|Entity|`FC-JDK/src/main/java/data/fchData/Freer.java` (`reputation`, `hot`, `reCalcWeight`)|
|History|`FC-JDK/src/main/java/data/feipData/RepuHist.java`|
|OpReturn|`FC-JDK/src/main/java/data/fchData/OpReturn.java` (`signer`, `recipient`, `cdd`)|
|Recipient resolution|`FchParser/src/main/java/writeEs/BlockMaker.java` (first non-signer output owner)|
|Parser|`FEIP/FeipParser/src/main/java/identity/IdentityParser.java` → `makeReputation()`, `parseReputation()`|
|CDD threshold|`FEIP/FeipParser/src/main/java/startFEIP/StartFEIP.java` → `CddRequired`|
|Dispatcher|`FEIP/FeipParser/src/main/java/startFEIP/FileParser.java` → `case REPUTATION`|
|Rollback|`FEIP/FeipParser/src/main/java/identity/IdentityRollbacker.java` → `rollbackRepu`|
