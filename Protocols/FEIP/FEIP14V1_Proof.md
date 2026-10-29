# FEIP14V1_Proof

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
|Title|Proof|
|Type|FEIP|
|SN|14|
|Version|1|
|Category|Finance|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-23|
|PID|d5aafaff461e8b92916614860042f1e19e4be99b783ba142f2c9417677e627d3|

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

The **Proof** protocol registers **on-chain proof documents**: a **title**, **body** (`content`), optional **multi-party cosigning**, **transferability**, and lifecycle operations **issue**, **sign**, **transfer**, and **destroy**. Each proof has a stable **`id`** equal to the **issue** transaction id. The **issuer** is the issuing transaction’s signer; **owner** holds current rights: initially the FID named in **`issueTo`**, otherwise the issuer; a **transfer** hands it to the FID named in **`transferTo`**. Owners are always named in the op data, never inferred from transaction outputs. **Cosigners** invited by the issuer may **sign** to satisfy **`allSignsRequired`** gating before the proof is **active**.

## Motivation

- **Attestations and agreements**: Publish a clear title + content with optional co-signer workflow.
- **Controlled activation**: Require all invited cosigners to sign before the proof becomes **active** when **`allSignsRequired`** is true.
- **Issue to someone**: The issuer can make another FID the first owner with **`issueTo`**. This is the only way an untransferable proof can belong to anyone but its issuer.
- **Transfer**: Allow **transfer** of ownership only when **`transferable`** is true.
- **Destruction**: **Owner** may **destroy** one or more proofs they own.

## Specification

### Proof entity (indexed)

|Field|Source|Description|
|---|---|---|
|`id`|Issue txid|Permanent proof identifier.|
|`title`|Issue / history|Headline of the proof.|
|`content`|Issue / history|Body text (public on-chain).|
|`cosignersInvited`|Derived on issue|FID list from op **`cosigners`**, **excluding** the issuer (signer). Empty if none.|
|`cosignersSigned`|Updated on **sign**|FID list of invited cosigners who have signed, in application order.|
|`transferable`|Issue|Whether ownership transfer is intended to be allowed (boolean).|
|`active`|Issue / **sign**|`true` when the proof is considered live; may start **`false`** if **`allSignsRequired`** and there are invited cosigners (see [issue](#1-issue)).|
|`destroyed`|**destroy**|`true` after successful **destroy**; **`active`** also set **`false`**.|
|`issuer`|Issue signer|FID that issued the proof.|
|`owner`|Issue / **transfer**|Current owner FID: **`issueTo`** or the issuer at issue, then each **`transferTo`** (see [issue](#1-issue) and [transfer](#3-transfer)).|
|`birthTime`, `birthHeight`|Issue block|Creation context.|
|`lastTxId`, `lastTime`, `lastHeight`|Last mutating op|Updated when the indexed document changes.|

### `data` field — operations

Lowercase `op` strings: **`issue`**, **`sign`**, **`transfer`**, **`destroy`** (as in [FeipOp](../../FC-JDK/src/main/java/data/feipData/FeipOp.java) / [OpNames](../../FC-JDK/src/main/java/constants/OpNames.java)).

#### 1. issue

Creates a new proof. **`proofId` is not supplied** — it becomes the **current transaction id**.

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"issue"`|
|`title`|Y|String|Proof title.|
|`content`|Y|String|Proof body (plaintext, public in the index).|
|`cosigners`|N|Array of FID strings|Invited cosigners (issuer SHOULD NOT be listed; reference **drops** the issuer if present). Every entry MUST be a valid FID; one malformed entry makes the op invalid.|
|`transferable`|N|Boolean|Whether the proof may be transferred.|
|`allSignsRequired`|N|Boolean|If **true** and there is at least one invited cosigner after filtering, **`active`** starts **`false`** until every invited cosigner has **signed**.|
|`issueTo`|N|String (FID)|The first owner. Absent means the issuer owns the proof.|

**Consensus rules**

1. **`title`** and **`content`** MUST be present (reference rejects null).
2. **`id`** = **`proofId`** = **this transaction’s txid**; document MUST NOT already exist for that id.
3. **`issuer`** MUST be the transaction **signer** FID.
4. If **`issueTo`** is present it MUST be a valid FID (otherwise the op is invalid), and **`owner`** = **`issueTo`**; otherwise **`owner`** = **`issuer`**. Transaction outputs play no part: paying an FID in the issue transaction does not make it the owner.
5. **`destroyed`** MUST be **`false`** on issue; **`cosignersSigned`** starts unset / empty.
6. **CDD:** When block height exceeds **`CddCheckHeight`**, **issue** requires **CDD ≥ `CddRequired`** (1 cd), the same as other FEIP operations (see `StartFEIP` in the reference parser).

#### 2. sign

Records one invited cosigner’s signature on an existing proof.

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"sign"`|
|`proofId`|Y|String|Target proof **`id`** (issue txid).|

**Consensus rules**

7. **`proofId`** MUST be present. **One sign transaction signs exactly one proof**; **`proofIds`** is not accepted for **sign** and an op carrying only **`proofIds`** is invalid.
8. The proof MUST exist, MUST NOT be **`destroyed`**, and MUST have a non-empty **`cosignersInvited`** list.
9. The transaction **signer** MUST be one of **`cosignersInvited`** and MUST NOT already appear in **`cosignersSigned`**.
10. Append the signer to **`cosignersSigned`**. If the number of signed cosigners **equals** the number of invited cosigners, set **`active`** to **`true`** (reference activates when lengths match). Signing is permitted while the proof is **`active`** — activation is the effect of the final signature, not a precondition.
11. Update last-* fields from this transaction.

*One proof per sign:* [ProofOpData](../../FC-JDK/src/main/java/data/feipData/ProofOpData.java) defines only **`proofId`** for **sign**, and `ProofOpData.makeSign(proofId)` builds it that way. Cosigning several proofs requires one **sign** transaction each; only **destroy** batches over **`proofIds`**.

#### 3. transfer

Moves **ownership** to a new FID.

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"transfer"`|
|`proofId`|Y|String|Proof **`id`**.|
|`transferTo`|Y|String (FID)|The new owner.|

**Consensus rules**

12. The proof MUST exist, MUST NOT be **`destroyed`**, MUST be **`active`**, and MUST be **`transferable`** (`true`; absent counts as not transferable).
13. **`signer`** MUST equal current **`owner`**.
14. **`transferTo`** MUST be present, MUST be a valid FID and MUST NOT equal the **signer**; **`owner`** becomes **`transferTo`**. Transaction outputs play no part.
15. Update last-* fields.

#### 4. destroy

Marks proofs as destroyed (and inactive).

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"destroy"`|
|`proofIds`|Y|Array of strings|Proof **`id`** values to destroy.|

**Consensus rules**

16. **`proofIds`** MUST be present and non-empty.
17. For each id, if the proof exists, is not already **`destroyed`**, and **`owner`** equals **signer**, set **`destroyed`** = **`true`**, **`active`** = **`false`**, and update last-* fields. Other entries in the list are skipped (reference: per-item `continue`).
18. If **no** listed proof qualifies, the operation as a whole is **invalid**: nothing is written and **no `ProofHistory` is recorded** (the reference parser records history only for operations that changed indexed state).

### OP_RETURN envelope

```json
{
  "type": "FEIP",
  "sn": "14",
  "ver": "1",
  "name": "Proof",
  "data": { }
}
```

### ProofHistory (audit)

[ProofHistory](../../FC-JDK/src/main/java/data/feipData/ProofHistory.java) stores operation context: `height`, `index`, `time`, `signer`, `op`, and op-specific fields (`proofId`, `proofIds`, `title`, `content`, `cosigners`, `transferable`, `allSignsRequired`, `issueTo`, `transferTo`) for audit or reparse (see **FinanceRollbacker**).

### Parsing order and reorg

- Strict block order per [FEIP0](FEIP0V1_FEIP.md).
- Reference **FinanceRollbacker** rebuilds affected proofs from **ProofHistory** after reorg.

## Examples

### Example 1 — issue (no cosigners)

```json
{
  "type": "FEIP",
  "sn": "14",
  "ver": "1",
  "name": "Proof",
  "data": {
    "op": "issue",
    "title": "Delivery receipt",
    "content": "Party A confirms receipt of goods listed in attachment XYZ.",
    "transferable": true,
    "allSignsRequired": false
  }
}
```

### Example 1b — issue to another owner

```json
{
  "type": "FEIP",
  "sn": "14",
  "ver": "1",
  "name": "Proof",
  "data": {
    "op": "issue",
    "title": "Diploma",
    "content": "The holder completed the course ...",
    "transferable": false,
    "issueTo": "FID..."
  }
}
```

The proof is owned by `issueTo` from birth. Being untransferable, it stays with that FID until destroyed.

### Example 2 — issue with cosigners (all must sign before active)

```json
{
  "type": "FEIP",
  "sn": "14",
  "ver": "1",
  "name": "Proof",
  "data": {
    "op": "issue",
    "title": "Mutual agreement",
    "content": "Terms as negotiated off-chain reference CID ...",
    "cosigners": ["FID1...", "FID2..."],
    "transferable": false,
    "allSignsRequired": true
  }
}
```

If **`allSignsRequired`** is **true** and there is at least one invited cosigner, the indexed proof starts **`active`: false** until each invited FID has issued a **`sign`** op.

### Example 3 — sign

```json
{
  "type": "FEIP",
  "sn": "14",
  "ver": "1",
  "name": "Proof",
  "data": {
    "op": "sign",
    "proofId": "<issue_txid>"
  }
}
```

### Example 4 — transfer

```json
{
  "type": "FEIP",
  "sn": "14",
  "ver": "1",
  "name": "Proof",
  "data": {
    "op": "transfer",
    "proofId": "<issue_txid>",
    "transferTo": "FID..."
  }
}
```

The new **owner** is **`transferTo`**.

### Example 5 — destroy

```json
{
  "type": "FEIP",
  "sn": "14",
  "ver": "1",
  "name": "Proof",
  "data": {
    "op": "destroy",
    "proofIds": ["<issue_txid_1>", "<issue_txid_2>"]
  }
}
```

## Versioning

|Version|Date|Summary|
|---|---|---|
|1|2026-03-23|Initial spec: issue / sign / transfer / destroy; aligned with `Feip.PROOF` (`14`/`1`).|
|1|2026-10-29|Owners named in the data: **`issueTo`** on issue, required **`transferTo`** on transfer; transaction recipients are no longer read. **`transferable`** enforced on transfer.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FEIP0|Envelope, parsing, CDD.|
|FEIP20 Token|Same **Finance** area; shared op naming patterns (`issue`, `transfer`, `destroy`).|

## Reference Implementation

|Component|Location|
|---|---|
|`Proof`| [FC-JDK/src/main/java/data/feipData/Proof.java](../../FC-JDK/src/main/java/data/feipData/Proof.java) |
|`ProofOpData`| [FC-JDK/src/main/java/data/feipData/ProofOpData.java](../../FC-JDK/src/main/java/data/feipData/ProofOpData.java) |
|`ProofHistory`| [FC-JDK/src/main/java/data/feipData/ProofHistory.java](../../FC-JDK/src/main/java/data/feipData/ProofHistory.java) |
|`FinanceParser.makeProof` / `parseProof`| [FEIP/FeipParser/src/main/java/finance/FinanceParser.java](../../FEIP/FeipParser/src/main/java/finance/FinanceParser.java) |
|`Feip.PROOF`| [FC-JDK/src/main/java/data/feipData/Feip.java](../../FC-JDK/src/main/java/data/feipData/Feip.java) |

### Implementation notes (non-normative)

- **CDD on issue**: a missing (`null`) CDD is treated as insufficient, i.e. the same as a CDD below the threshold.
- **`destroy`** writes the whole batch in one bulk request to the **`proof`** index; a batch in which every entry is skipped produces no bulk request at all (see rule 18).
