# FEIP20V1_Token

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
|Title|Token|
|Type|FEIP|
|SN|20|
|Version|1|
|Category|Finance|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-24|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

The **Token** protocol defines **deploy**, **issue**, **transfer**, **destroy** (burn holder balance), and **close** (deployer marks token closed) for fungible-like assets on-chain. **`tokenId`** for a new token is the **deploy transaction id**. Balances are indexed per **(fid, tokenId)** as **`TokenHolder`** documents whose **id** is **SHA256(fid ‖ tokenId)** (hex). The reference parser enforces **decimal places**, optional **capacity**, **openIssue** rules (max per issue, min CDD per issue, max issue txs per signer), and **transferable** semantics at index time where applicable.

## Motivation

- **Issued assets**: Names, supply caps, decimals, and issuance policy on-chain.
- **Clear holder ledger**: ES-backed **`TOKEN`** and **`TOKEN_HOLDER`** indices for APIs and clients.

## Specification

### Token entity (indexed)

|Field|Source|Description|
|---|---|---|
|`id`|Deploy txid|Same as **`tokenId`** for that token.|
|`name`|deploy|Human-readable name (required in reference **`makeToken`**).|
|`desc`, `consensusId`|deploy|Optional metadata.|
|`capacity`|deploy|Optional max **circulating** (string decimal).|
|`decimal`|deploy|String integer; max fractional digits for **issue** / **transfer** amounts (default **`0`** if omitted in parser).|
|`transferable`, `closable`, `openIssue`|deploy|Booleans; defaults **`false`** when omitted in reference.|
|`maxAmtPerIssue`, `minCddPerIssue`, `maxIssuesPerAddr`|deploy|Optional; applied when **`openIssue`** is true (per-issue total cap, min **CDD** on issue tx, max prior **issue** ops per signer in history index).|
|`closed`|**close**|**true** when deployer closes the token.|
|`deployer`|deploy signer|FID that deployed the token.|
|`circulating`|issue / destroy|Aggregated issued amount minus **destroy** burns (reference uses `Double`).|
|`birthTime`, `birthHeight`, `lastTxId`, `lastTime`, `lastHeight`|Tx / block|Lifecycle.|

### TokenHolder entity

|Field|Description|
|---|---|
|`id`|**SHA256(fid + tokenId)** hex (see **`TokenHolder.getTokenHolderId`**).|
|`fid`, `tokenId`|Holder and token.|
|`balance`|Amount (reference: `Double`).|
|`firstHeight`, `lastHeight`|First credit / last update height.|

### `data.op` values

Lowercase strings aligned with [TokenOpData](../../FC-JDK/src/main/java/data/feipData/TokenOpData.java): **`deploy`**, **`issue`**, **`transfer`**, **`close`**. The chain may also use **`destroy`** (burn); it is accepted in **`makeToken`** / **`parseToken`** but not listed in **`TokenOpData.Op`** enum.

### Operations (reference behavior)

#### 1. deploy

- **Required:** `op`, non-empty **`name`**.
- **CDD:** When block height exceeds **`CddCheckHeight`**, **`cdd`** must be non-null and **≥ `CddRequired * 100`** (**`makeToken`**).
- **`tokenId`** is **not** taken from JSON; it is set to **this transaction’s id**.
- **Optional:** `desc`, `consensusId`, `capacity`, `decimal` (must be integer string), `transferable`, `closable`, `openIssue`, and when `openIssue` is true: `maxAmtPerIssue`, `minCddPerIssue`, `maxIssuesPerAddr`.
- Creates **`TOKEN`**; **`News.createNews`** with op **`create`**.

#### 2. issue

- **Required:** **`tokenId`**, non-null **`issueTo`** array of **`{ "fid", "amount" }`** (both required; amounts must respect **decimal**).
- If **`openIssue`** is false, **signer** must be **`deployer`**.
- If **`openIssue`** is true: enforce **per-issue** total ≤ **`maxAmtPerIssue`** (if set), **CDD** ≥ **`minCddPerIssue`** (if set; null CDD rejects), and **issue** count by **signer** in **`TOKEN_HISTORY`** &lt; **`maxIssuesPerAddr`** (if set).
- **Circulating** += sum of amounts; must not exceed **`capacity`** if set.
- Upserts **`TOKEN_HOLDER`** for each recipient (new holders get their own **amount**, not the batch total).

#### 3. transfer

- **Required:** **`tokenId`**, **`transferTo`** (same shape as **issueTo**).
- Token must exist and not be **closed**. The reference parser does **not** reject **transfer** when **`transferable`** is false (the flag is stored for clients / off-chain policy).
- **Signer** must have a **`TOKEN_HOLDER`** row; **sum(transferTo.amount)** ≤ balance; sender balance reduced; recipients created or incremented.

#### 4. destroy

- **Required:** **`tokenIds`** containing **exactly one** id (same as **`tokenId`** for the burn); reference sets **`tokenId`** from that element (**`makeToken`**).
- **Signer**’s holder balance must be **&gt; 0**; balance set to **0**; **circulating** reduced by prior balance.

#### 5. close

- **Required:** non-empty **`tokenIds`**.
- For **each** id: token must exist, not already **closed**, and **signer** must be **deployer**; sets **`closed` = true**.
- **`News.createNews`** with op **`close`** and the full id list.

### OP_RETURN envelope

```json
{
  "type": "FEIP",
  "sn": "20",
  "ver": "1",
  "name": "Token",
  "data": { }
}
```

### TokenHistory

[TokenHistory](../../FC-JDK/src/main/java/data/feipData/TokenHistory.java) records block context, **`signer`**, **`cdd`** (used for **issue** limits), **`op`**, **`tokenId`** / **`tokenIds`**, deploy fields, **`issueTo`** / **`transferTo`**.

## Examples

### deploy

```json
{
  "type": "FEIP",
  "sn": "20",
  "ver": "1",
  "name": "Token",
  "data": {
    "op": "deploy",
    "name": "Example Coin",
    "decimal": "8",
    "capacity": "21000000",
    "transferable": true,
    "closable": true,
    "openIssue": true,
    "maxAmtPerIssue": "1000",
    "minCddPerIssue": "1000000",
    "maxIssuesPerAddr": "10"
  }
}
```

### issue

```json
{
  "type": "FEIP",
  "sn": "20",
  "ver": "1",
  "name": "Token",
  "data": {
    "op": "issue",
    "tokenId": "<deploy_txid>",
    "issueTo": [
      { "fid": "<fid1>", "amount": 100 },
      { "fid": "<fid2>", "amount": 50 }
    ]
  }
}
```

### transfer

```json
{
  "type": "FEIP",
  "sn": "20",
  "ver": "1",
  "name": "Token",
  "data": {
    "op": "transfer",
    "tokenId": "<deploy_txid>",
    "transferTo": [
      { "fid": "<fid2>", "amount": 25 }
    ]
  }
}
```

### destroy

```json
{
  "type": "FEIP",
  "sn": "20",
  "ver": "1",
  "name": "Token",
  "data": {
    "op": "destroy",
    "tokenIds": ["<deploy_txid>"]
  }
}
```

### close

```json
{
  "type": "FEIP",
  "sn": "20",
  "ver": "1",
  "name": "Token",
  "data": {
    "op": "close",
    "tokenIds": ["<deploy_txid>", "<other_txid>"]
  }
}
```

## Versioning

|Version|Date|Summary|
|---|---|---|
|1|2026-03-24|Initial spec; aligned with `Feip.TOKEN` (`20`/`1`).|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FEIP0|Envelope, CDD constants.|
|FEIP21+|Other domains; tokens may attach to services/apps off-chain by convention.|

## Reference Implementation

|Component|Location|
|---|---|
|`Token`| [FC-JDK/src/main/java/data/feipData/Token.java](../../FC-JDK/src/main/java/data/feipData/Token.java) |
|`TokenHolder`| [FC-JDK/src/main/java/data/feipData/TokenHolder.java](../../FC-JDK/src/main/java/data/feipData/TokenHolder.java) |
|`TokenOpData`| [FC-JDK/src/main/java/data/feipData/TokenOpData.java](../../FC-JDK/src/main/java/data/feipData/TokenOpData.java) |
|`TokenHistory`| [FC-JDK/src/main/java/data/feipData/TokenHistory.java](../../FC-JDK/src/main/java/data/feipData/TokenHistory.java) |
|`FinanceParser.makeToken` / `parseToken`| [FEIP/FeipParser/src/main/java/finance/FinanceParser.java](../../FEIP/FeipParser/src/main/java/finance/FinanceParser.java) |
|`Feip.TOKEN`| [FC-JDK/src/main/java/data/feipData/Feip.java](../../FC-JDK/src/main/java/data/feipData/Feip.java) |

### Implementation notes (non-normative)

- **Issue:** New holders each receive their **`issueTo`** amount; **`mget` miss** handling iterates **all** misses (no single-iteration **`break`**).
- **Close:** Closes **every** id in **`tokenIds`**, not only a single **`tokenId`** field.
- **Destroy:** **`tokenIds`** must contain exactly one id; **`tokenId`** is derived for **`parseToken`**.
- **Null-safety:** Token **closed** checks use **`Boolean.TRUE.equals`**. **CDD** for **minCddPerIssue** rejects null. **Deploy** CDD uses null-safe comparison. Holder balances treat null as **0** where needed.
