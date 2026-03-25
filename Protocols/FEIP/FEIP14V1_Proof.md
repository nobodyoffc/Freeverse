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
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

The **Proof** protocol registers **on-chain proof documents**: a **title**, **body** (`content`), optional **multi-party cosigning**, **transferability**, and lifecycle operations **issue**, **sign**, **transfer**, and **destroy**. Each proof has a stable **`id`** equal to the **issue** transaction id. The **issuer** is the issuing transaction’s signer; **owner** holds current rights (initially the **recipient** of that transaction if present, otherwise the issuer). **Cosigners** invited by the issuer may **sign** to satisfy **`allSignsRequired`** gating before the proof is **active**.

## Motivation

- **Attestations and agreements**: Publish a clear title + content with optional co-signer workflow.
- **Controlled activation**: Require all invited cosigners to sign before the proof becomes **active** when **`allSignsRequired`** is true.
- **Transfer**: Optionally allow **transfer** of ownership when **`transferable`** is true (enforced by client/economic convention; reference **transfer** checks **active**, not destroyed, and **owner** == signer).
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
|`owner`|Issue / **transfer**|Current owner FID (see [issue](#1-issue) and [transfer](#3-transfer)).|
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
|`cosigners`|N|Array of FID strings|Invited cosigners (issuer SHOULD NOT be listed; reference **drops** the issuer if present).|
|`transferable`|N|Boolean|Whether the proof may be transferred.|
|`allSignsRequired`|N|Boolean|If **true** and there is at least one invited cosigner after filtering, **`active`** starts **`false`** until every invited cosigner has **signed**.|

**Consensus rules**

1. **`title`** and **`content`** MUST be present (reference rejects null).
2. **`id`** = **`proofId`** = **this transaction’s txid**; document MUST NOT already exist for that id.
3. **`issuer`** MUST be the transaction **signer** FID.
4. **`owner`** MUST be the **recipient** FID from the transaction context when a recipient is defined; otherwise **`owner`** = **`issuer`** (reference: `opre.getRecipient()`).
5. **`destroyed`** MUST be **`false`** on issue; **`cosignersSigned`** starts unset / empty.
6. **CDD (reference):** When block height exceeds a configured threshold, **issue** requires **CDD ≥ `CddRequired` × 100** (see `StartFEIP` in the reference parser). Conforming indexers SHOULD apply the same anti-spam policy for consistency.

#### 2. sign

Records one invited cosigner’s signature on an existing proof.

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"sign"`|
|`proofId`|Y|String|Target proof **`id`** (issue txid).|

**Consensus rules**

7. The proof MUST exist, MUST NOT be **`destroyed`**, and MUST have a non-empty **`cosignersInvited`** list.
8. The transaction **signer** MUST be one of **`cosignersInvited`** and MUST NOT already appear in **`cosignersSigned`**.
9. Append the signer to **`cosignersSigned`**. If the number of signed cosigners **equals** the number of invited cosigners, set **`active`** to **`true`** (reference activates when lengths match).
10. Update last-* fields from this transaction.

*Batch sign:* [ProofOpData](../../FC-JDK/src/main/java/data/feipData/ProofOpData.java) only defines **`proofId`** for **sign**. A separate **sign** transaction is used per proof; bulk **destroy** uses **`proofIds`**.

#### 3. transfer

Moves **ownership** to a new FID.

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"transfer"`|
|`proofId`|Y|String|Proof **`id`**.|

**Consensus rules**

11. The proof MUST exist, MUST NOT be **`destroyed`**, and MUST be **`active`**.
12. **`signer`** MUST equal current **`owner`**.
13. A **recipient** FID MUST be present in the transaction context (reference rejects null `recipient`); **`owner`** becomes that recipient.
14. Update last-* fields.

*Note:* Reference **transfer** does not re-check **`transferable`**; clients SHOULD refuse to build transfer txs when **`transferable`** is false.

#### 4. destroy

Marks proofs as destroyed (and inactive).

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"destroy"`|
|`proofIds`|Y|Array of strings|Proof **`id`** values to destroy.|

**Consensus rules**

15. For each id, if the proof exists, is not already **`destroyed`**, and **`owner`** equals **signer**, set **`destroyed`** = **`true`**, **`active`** = **`false`**, and update last-* fields. Other entries in the list are skipped (reference: per-item `continue`).

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

[ProofHistory](../../FC-JDK/src/main/java/data/feipData/ProofHistory.java) stores operation context: `height`, `index`, `time`, `signer`, `recipient` (when applicable), `op`, and op-specific fields (`proofId`, `proofIds`, `title`, `content`, `cosigners`, `transferable`, `allSignsRequired`) for audit or reparse (see **FinanceRollbacker**).

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
    "proofId": "<issue_txid>"
  }
}
```

The new **owner** is the transaction **recipient** FID (same notion as other finance FEIPs in the reference).

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

- **`makeProof`** uses a shared branch for **`sign`** and **`destroy`** that requires **`proofIds`** and does not set **`proofId`** for **sign**, while **`parseProof`** reads **`getProofId()`** for **sign** — the reference path for **sign** is inconsistent. Indexers SHOULD treat **`proofId`** as normative for **sign** (as in `ProofOpData`) or map a single-element **`proofIds`** to **`proofId`** before `parseProof`.
- **`parseProof` / destroy** bulk indexing uses **`IndicesNames.PROTOCOL`** in the reference snippet; this is likely a typo for **`PROOF`** and SHOULD be corrected in code.
- **`transferable`** is stored on the entity but not enforced in **`parseProof`** for **transfer**; enforcement is a client concern unless tightened in a later FEIP version.
