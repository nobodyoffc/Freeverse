# FEIP1V7_Protocol

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
|Title|Protocol|
|Type|FEIP|
|SN|1|
|Version|7|
|Category|Construct|
|Status|Active|
|Author|C_armX, No1_NrC7|
|Created|2026-03-20|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

FEIP1 Protocol is the **on-chain registry layer** for the protocols of Freeverse ecosystem: anyone can publish and manage formal protocols on-chain without a central gatekeeper. 

## Motivation

Freeverse grows by stacking many small protocols—identity, storage, messaging, publishing, finance, and whatever comes next. If the “source of truth” for those rules sits only on corporate sites, closed docs, or opaque package stores, the stack is only as open as whoever hosts that layer. Binding definitions to the chain gives every participant the same **read-only contract**: what was published, by whom, and under which version and links, all keyed by a single **`pid`**.

That does not pick winners. It records offers. Any FID can publish a new spec, extend or challenge a lineage with **`preDid`**, or run a parallel design; wallets, indexers, and apps then **opt in** to the `pid`s they trust. The protocol layer stays **permissionless** while still giving integrators something concrete to implement against and users something concrete to compare.

**Ratings weighted by CDD** are deliberately lightweight: they do not replace judgment or governance in code, but they attach a **costed signal** to feedback so raw spam is expensive and sustained participation weighs more than drive-by noise. The aim is a protocol market that is open to entry, hard to capture, and legible across the whole ecosystem—not a committee-approved checklist.

## Specification

### Identifier and envelope

- **Serial number (`sn`)**: `"1"`.
- **Version (`ver`)**: `"7"`.
- **Protocol name (envelope `name`)**: `"Protocol"`.
- **`id`**: The primary key of the **Protocol** entity (inherited from `FcEntity`), set to the txid of the transaction that **published** this protocol. When quoted independently in operation data, it is called **`pid`**. After publication, **`update`** and **`rate`** refer to an existing protocol by its `pid`.

The OP_RETURN MUST be a UTF-8 JSON object following [FEIP0V1_FEIP](FEIP0V1_FEIP.md) (type `FEIP`, `sn`, `ver`, `name`, optional `pid`/`did` on the envelope, and `data` for the operation).

### Operations

Operation names in `data.op` are lowercase: `publish`, `update`, `stop`, `recover`, `close`, `rate`.

#### 1. publish

Create a new on-chain protocol definition. The new **`pid`** is the **txid** of this transaction.

**`data` fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: `publish`|
|sn|Y|String|Serial number of the protocol being defined (the target FEIP’s `sn`, not necessarily `"1"`)|
|name|Y|String|Short name of the protocol. MUST NOT be null or empty.|
|type|N|String|Protocol family label (e.g. `FEIP`). Used when forming the display `title`.|
|ver|N|String|Version string of the defined protocol.|
|did|N|String|Document id for the defined protocol (off-chain or on-chain reference, protocol-specific).|
|desc|N|String|Human-readable description.|
|lang|N|String|Primary language code or label for the document.|
|home|N|List\<String\>|URLs or locators for the full specification.|
|preDid|N|String|Previous document id in a replacement chain; stored as **`prePid`** on the entity and history (same string value).|
|waiters|N|List\<String\>|Optional list carried in history (see [Output](#output)).|

**OP_RETURN example:**

```json
{
  "type": "FEIP",
  "sn": "1",
  "ver": "7",
  "name": "Protocol",
  "data": {
    "op": "publish",
    "type": "FEIP",
    "sn": "3",
    "ver": "4",
    "name": "CID",
    "did": "doc-cid-v4",
    "desc": "Crypto Identity naming on Freecash.",
    "lang": "en",
    "home": ["https://example.com/feip3-cid"],
    "preDid": "doc-cid-v3"
  }
}
```

#### 2. update

Replace mutable metadata of an existing protocol. Only the **owner** may update.

**`data` fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: `update`|
|pid|Y|String|Existing protocol id (publish txid).|
|sn|Y|String|Updated serial number string.|
|name|Y|String|Updated name. MUST NOT be null or empty.|
|type|N|String|Updated type label.|
|ver|N|String|Updated version string.|
|did|N|String|Updated document id.|
|desc|N|String|Updated description.|
|lang|N|String|Updated language.|
|home|N|List\<String\>|Updated home links.|
|preDid|N|String|Updated previous-document id (stored as `prePid`).|
|waiters|N|List\<String\>|Optional; recorded in history.|

**OP_RETURN example:**

```json
{
  "type": "FEIP",
  "sn": "1",
  "ver": "7",
  "name": "Protocol",
  "data": {
    "op": "update",
    "pid": "abc123...publishTxid...",
    "type": "FEIP",
    "sn": "3",
    "ver": "5",
    "name": "CID",
    "desc": "CID v5 draft.",
    "lang": "en"
  }
}
```

#### 3. stop

Mark one or more owned protocols as inactive (`active` = false).

**`data` fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: `stop`|
|pids|Y|List\<String\>|Non-empty list of `pid` values to affect.|

#### 4. recover

Mark one or more owned protocols as active again (`active` = true), unless the protocol is **closed**.

**`data` fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: `recover`|
|pids|Y|List\<String\>|Non-empty list of `pid` values to affect.|

#### 5. close

Permanently close one or more protocols (`closed` = true, `active` = false). Closed protocols MUST NOT be updated.

**`data` fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: `close`|
|pids|Y|List\<String\>|Non-empty list of `pid` values to affect.|
|closeStatement|N|String|Optional message recorded in **ProtocolHistory** only.|

#### 6. rate

Submit a numeric rating for someone else’s protocol, weighted by the transaction’s **CDD** (see [FEIP0V1_FEIP](FEIP0V1_FEIP.md)). The **signer MUST NOT** be the protocol owner.

**`data` fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: `rate`|
|pid|Y|String|Target protocol id.|
|rate|Y|Integer|Rating value. Parsers MAY restrict the range (e.g. 0–5) in a later version; v7 reference accepts any integer present in JSON.|

### Parsing rules

1. **Invalid JSON or wrong envelope** for this FEIP (`type` ≠ `FEIP`, or `sn` / `ver` mismatch): ignore; no state change ([FEIP0V1_FEIP](FEIP0V1_FEIP.md)).

2. **`publish`**
   - `sn` and `name` MUST be present; `name` MUST NOT be empty. Otherwise ignore.
   - Since block height **4_000_000**, the transaction MUST meet the global **minimum CDD** (≥ 1) in the reference parser. Below that height, CDD is not required for `publish`.
   - Let `pid = txid`. If a **Protocol** entity with id `pid` already exists, ignore (duplicate publish).
   - Create the entity with `owner` = signer, `birthTime` / `birthHeight` from the block, `active` = true, `closed` = false, and `last*` fields from this tx.
   - **`title`** (reference): `type + sn + "V" + ver + "_" + name + "(" + lang + ")"` using the strings present (null `type`/`ver`/`lang` are stringified as in the reference implementation).

3. **`update`**
   - `pid`, `sn`, and non-empty `name` are required; otherwise ignore.
   - Load protocol by `pid`. If missing, ignore.
   - If `closed` is true, ignore.
   - If `owner` ≠ signer, ignore.
   - If `active` is false, ignore (reference implementation).
   - Overwrite mutable fields from the operation; refresh `lastTxId`, `lastTime`, `lastHeight`, and recompute **`title`** as for `publish`.

4. **`stop` / `recover` / `close`**
   - `pids` MUST be non-null and non-empty; otherwise ignore.
   - For each `pid`, load the protocol. Skip if already **`closed`**.
   - **Authorization**: apply the change only if the signer is the **`owner`**, or if the reference implementation’s additional **Freer / master** check passes (see [Reference Implementation](#reference-implementation)).
   - **`stop`**: set `active` = false.
   - **`recover`**: set `active` = true.
   - **`close`**: set `closed` = true and `active` = false; persist `closeStatement` on the **history** record when provided.
   - Update `lastTxId`, `lastTime`, `lastHeight` on each modified protocol.

5. **`rate`**
   - `pid` is required; otherwise ignore.
   - Signer MUST NOT equal `owner`; otherwise ignore.
   - Transaction MUST satisfy **CDD ≥ 1** (reference uses `CddRequired`) for the rating to apply.
   - Let `cdd` be the transaction’s CDD and `r` the submitted `rate`. Update aggregate fields:
     - First rating: `tRate = r`, `tCdd = cdd`.
     - Later: `tRate = (tRate * tCdd + r * cdd) / (tCdd + cdd)`, `tCdd = tCdd + cdd` (floating-point in reference).
   - Refresh `lastTxId`, `lastTime`, `lastHeight`.

6. **Order**: Operations are applied in strict blockchain order (height, then tx index) per FEIP0.

### Output

#### Protocol entity (keyed by `id`)

|Field|Type|Description|
|---|---|---|
|id|String|Same as `pid` (publish txid).|
|type|String|Protocol family label.|
|sn|String|Defined protocol serial number.|
|ver|String|Defined protocol version string.|
|did|String|Document id.|
|name|String|Short name.|
|lang|String|Language.|
|desc|String|Description.|
|prePid|String|Carried from `preDid` in `data`.|
|home|List\<String\>|Home links.|
|title|String|Derived display title (reference formula).|
|owner|String|Signer of the `publish` tx (FID).|
|birthTime|Long|Block time of `publish`.|
|birthHeight|Long|Block height of `publish`.|
|lastTxId|String|Last affecting txid.|
|lastTime|Long|Time of last affecting tx.|
|lastHeight|Long|Height of last affecting tx.|
|tCdd|Long|Sum of CDD contributing to ratings.|
|tRate|Float|CDD-weighted mean rating.|
|active|Boolean|Whether the definition is active.|
|closed|Boolean|Whether the definition is permanently closed.|
|closeStatement|String|Reserved on entity; not set by the v7 reference `close` path (statement lives on history).|
|waiters|List\<String\>|Present on the model; the v7 reference parser does not persist `waiters` onto the entity (only on history when supplied).|

#### ProtocolHistory (keyed by txid `id`, one row per successful operation)

|Field|Type|Description|
|---|---|---|
|id|String|This operation’s txid.|
|height|Long|Block height.|
|index|Integer|Tx index in block.|
|time|Long|Block time.|
|signer|String|Signer FID.|
|op|String|`publish`, `update`, `stop`, `recover`, `close`, `rate`.|
|pid|String|Target id (`publish` / `update` / `rate`); for `publish`, equals `id`.|
|pids|List\<String\>|For `stop` / `recover` / `close`.|
|type, sn, ver, did, name, desc, lang, prePid, home|Various|Copies from op when present (`prePid` from `preDid`).|
|rate|Integer|For `rate`.|
|cdd|Long|CDD snapshot for `rate`.|
|closeStatement|String|For `close` when provided.|
|waiters|List\<String\>|When provided on `publish` / `update`.|

## Examples

### Example 1: Publish

Signer publishes FEIP3 CID v4. **`pid`** becomes this transaction’s txid, e.g. `txPublish1`.

**Resulting entity (illustrative):**

```json
{
  "id": "txPublish1",
  "type": "FEIP",
  "sn": "3",
  "ver": "4",
  "name": "CID",
  "owner": "FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV",
  "active": true,
  "closed": false,
  "title": "FEIP3V4_CID(en)"
}
```

### Example 2: Update

Owner sends `update` with the same `pid`, bumps `ver` to `"5"`. `lastHeight` / `lastTime` / `lastTxId` move to the new tx; `title` reflects new `ver`.

### Example 3: Stop and recover

Owner sends `stop` with `"pids": ["txPublish1"]` → `active` = false.  
Later, owner sends `recover` with the same list → `active` = true (if not `closed`).

### Example 4: Rate

Another FID rates `txPublish1` with `rate: 5` and sufficient CDD → `tRate` / `tCdd` updated; owner cannot rate their own protocol.

## Versioning

|Version|Changes|
|---|---|
|7|Current version; aligns with `Feip.FeipProtocol.PROTOCOL` (`"1"`,`"7"`) and construct parser.|
|…|Earlier versions: incremental fields (`waiters`, `closeStatement`, CDD-weighted ratings, bulk `stop`/`recover`/`close`).|

## Related Protocols

|Protocol|Relationship|
|---|---|
|[FEIP0V1_FEIP](FEIP0V1_FEIP.md)|Global FEIP rules, CDD, envelope.|
|[FEIP2V7_Code](FEIP2V7_Code.md)|Defines code artifacts; often references protocols by `pid`.|
|[FEIP5V3_Service](FEIP5V3_Service.md)|Services may list dependent protocols.|
|FEIP6 Master (Identity)|Master FID; bulk operations in the reference parser consult `Freer.master`.|

## Reference Implementation

|Component|Location|
|---|---|
|Op payload|`FC-JDK/src/main/java/data/feipData/ProtocolOpData.java` (`preDid` in JSON maps to `prePid` on history/entity)|
|Entity|`FC-JDK/src/main/java/data/feipData/Protocol.java`|
|History|`FC-JDK/src/main/java/data/feipData/ProtocolHistory.java`|
|Parse / apply|`FEIP/FeipParser/src/main/java/construct/ConstructParser.java` → `makeProtocol()`, `parseProtocol()`|
|Rollback / reparse|`FEIP/FeipParser/src/main/java/construct/ConstructRollbacker.java`|
|Constants|`FC-JDK/src/main/java/data/feipData/Feip.java` → `FeipProtocol.PROTOCOL`|
|CDD gates|`FEIP/FeipParser/src/main/java/startFEIP/StartFEIP.java` → `CddCheckHeight`, `CddRequired`|

**Bulk authorization note:** In `parseProtocol`, for `stop` / `recover` / `close`, each target protocol must not be `closed`, and the signer must be the `owner` **or** satisfy the parser’s `Freer` lookup on the signer (`IndicesNames.FREER`, `master` field). Deployments SHOULD align this rule with FEIP6 Master semantics once documented on-chain.
