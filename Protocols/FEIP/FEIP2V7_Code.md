# FEIP2V7_Code

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
|Title|Code|
|Type|FEIP|
|SN|2|
|Version|7|
|Category|Construct|
|Status|Active|
|Author|C_armX, No1_NrC7|
|Created|2026-03-20|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

FEIP2 Code is the **on-chain registry layer** for code artifacts in the Freeverse ecosystem: anyone can publish and manage references to software implementations, libraries, and tools on-chain without a central authority. Each published code entry is keyed by a unique **`codeId`** (the txid of its publish transaction) and carries metadata such as name, version, programming languages, homepage links, and the protocols it implements.

## Motivation

Protocols alone are not enough — they need implementations. When code references live only on GitHub repos, private servers, or opaque package managers, the link between a protocol specification and its running software is fragile and centralized. By registering code artifacts on-chain, the Freeverse ecosystem gains a **permissionless code directory** where:

- **Anyone** can publish a new implementation of any protocol and let the community discover and rate it.
- **Integrators** can look up which code artifacts implement a given protocol (via the `protocols` field) and compare quality signals (CDD-weighted ratings).
- **Versioning** is transparent: every update to a code entry is recorded on-chain with full history.
- **Ownership** is cryptographic: only the publishing FID (or its master) can update or manage the entry.

## Specification

### Identifier and envelope

- **Serial number (`sn`)**: `"2"`.
- **Version (`ver`)**: `"7"`.
- **Protocol name (envelope `name`)**: `"Code"`.
- **`id`**: The primary key of the **Code** entity (inherited from `FcEntity`), set to the txid of the transaction that **published** this code entry. When quoted independently in operation data, it is called **`codeId`**. After publication, **`update`** and **`rate`** refer to an existing code entry by its `codeId`.

The OP_RETURN MUST be a UTF-8 JSON object following [FEIP0V1_FEIP](FEIP0V1_FEIP.md) (type `FEIP`, `sn`, `ver`, `name`, optional `pid`/`did` on the envelope, and `data` for the operation).

### Operations

Operation names in `data.op` are lowercase: `publish`, `update`, `stop`, `recover`, `close`, `rate`.

#### 1. publish

Create a new on-chain code entry. The new **`codeId`** is the **txid** of this transaction.

**`data` fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: `publish`|
|name|Y|String|Name of the code artifact. MUST NOT be null or empty.|
|ver|N|String|Version string of the code.|
|did|N|String|Document id (e.g. a commit hash, release tag, or on-chain reference).|
|desc|N|String|Human-readable description.|
|langs|N|List\<String\>|Programming languages used (e.g. `["Java", "Go"]`).|
|home|N|List\<String\>|URLs or locators for source code or project homepage.|
|protocols|N|List\<String\>|List of protocol ids (`pid`) that this code implements.|
|waiters|N|List\<String\>|Optional list of FIDs to notify.|

**OP_RETURN example:**

```json
{
  "type": "FEIP",
  "sn": "2",
  "ver": "7",
  "name": "Code",
  "data": {
    "op": "publish",
    "name": "FC-JDK",
    "ver": "1.0.0",
    "desc": "Java development kit for the Freeverse ecosystem.",
    "langs": ["Java"],
    "home": ["https://github.com/example/fc-jdk"],
    "protocols": ["abc123...protocolPid..."]
  }
}
```

#### 2. update

Replace mutable metadata of an existing code entry. Only the **owner** may update.

**`data` fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: `update`|
|codeId|Y|String|Existing code id (publish txid).|
|name|Y|String|Updated name. MUST NOT be null or empty.|
|ver|N|String|Updated version string.|
|did|N|String|Updated document id.|
|desc|N|String|Updated description.|
|langs|N|List\<String\>|Updated programming languages.|
|home|N|List\<String\>|Updated home links.|
|protocols|N|List\<String\>|Updated protocol references.|
|waiters|N|List\<String\>|Optional; recorded in history.|

**OP_RETURN example:**

```json
{
  "type": "FEIP",
  "sn": "2",
  "ver": "7",
  "name": "Code",
  "data": {
    "op": "update",
    "codeId": "def456...publishTxid...",
    "name": "FC-JDK",
    "ver": "2.0.0",
    "desc": "Major refactor with new crypto module.",
    "langs": ["Java", "Kotlin"],
    "home": ["https://github.com/example/fc-jdk"]
  }
}
```

#### 3. stop

Mark one or more owned code entries as inactive (`active` = false).

**`data` fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: `stop`|
|codeIds|Y|List\<String\>|Non-empty list of `codeId` values to affect.|

#### 4. recover

Mark one or more owned code entries as active again (`active` = true), unless the entry is **closed**.

**`data` fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: `recover`|
|codeIds|Y|List\<String\>|Non-empty list of `codeId` values to affect.|

#### 5. close

Permanently close one or more code entries (`closed` = true, `active` = false). Closed entries MUST NOT be updated.

**`data` fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: `close`|
|codeIds|Y|List\<String\>|Non-empty list of `codeId` values to affect.|
|closeStatement|N|String|Optional message recorded in **CodeHistory** only.|

#### 6. rate

Submit a numeric rating for someone else's code entry, weighted by the transaction's **CDD** (see [FEIP0V1_FEIP](FEIP0V1_FEIP.md)). The **signer MUST NOT** be the code owner.

**`data` fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: `rate`|
|codeId|Y|String|Target code id.|
|rate|Y|Integer|Rating value, 0–5.|

### Parsing rules

1. **Invalid JSON or wrong envelope** for this FEIP (`type` ≠ `FEIP`, or `sn` / `ver` mismatch): ignore; no state change ([FEIP0V1_FEIP](FEIP0V1_FEIP.md)).

2. **`publish`**
   - `name` MUST be present and MUST NOT be empty. Otherwise ignore.
   - `codeId` MUST NOT be present in the `data` (it is derived from the txid). If `codeId` is set, ignore.
   - Let `codeId = txid`. If a **Code** entity with id `codeId` already exists, ignore (duplicate publish).
   - Create the entity with `owner` = signer, `birthTime` / `birthHeight` from the block, `active` = true, `closed` = false, and `last*` fields from this tx.

3. **`update`**
   - `codeId` and non-empty `name` are required; otherwise ignore.
   - Load code by `codeId`. If missing, ignore.
   - If `closed` is true, ignore.
   - If `owner` ≠ signer, ignore.
   - If `active` is false, ignore.
   - Overwrite mutable fields from the operation; refresh `lastTxId`, `lastTime`, `lastHeight`.

4. **`stop` / `recover` / `close`**
   - `codeIds` MUST be non-null and non-empty; otherwise ignore.
   - For each `codeId`, load the code entry. Skip if already **`closed`**.
   - **Authorization**: apply the change only if the signer is the **`owner`**, or if the signer's `Freer` record has a `master` field matching the signer (see FEIP6 Master).
   - **`stop`**: set `active` = false.
   - **`recover`**: set `active` = true.
   - **`close`**: set `closed` = true and `active` = false; persist `closeStatement` on the **history** record when provided.
   - Update `lastTxId`, `lastTime`, `lastHeight` on each modified code entry.

5. **`rate`**
   - `codeId` is required; otherwise ignore.
   - `rate` MUST be between 0 and 5 (inclusive); otherwise ignore.
   - Signer MUST NOT equal `owner`; otherwise ignore.
   - Transaction MUST satisfy **CDD ≥ 1** (reference uses `CddRequired`) for the rating to apply.
   - Let `cdd` be the transaction's CDD and `r` the submitted `rate`. Update aggregate fields:
     - First rating: `tRate = r`, `tCdd = cdd`.
     - Later: `tRate = (tRate * tCdd + r * cdd) / (tCdd + cdd)`, `tCdd = tCdd + cdd` (floating-point in reference).
   - Refresh `lastTxId`, `lastTime`, `lastHeight`.

6. **Order**: Operations are applied in strict blockchain order (height, then tx index) per FEIP0.

### Output

#### Code entity (keyed by `id`)

|Field|Type|Description|
|---|---|---|
|id|String|Same as `codeId` (publish txid).|
|name|String|Name of the code artifact.|
|ver|String|Version string.|
|did|String|Document id.|
|desc|String|Description.|
|langs|List\<String\>|Programming languages.|
|home|List\<String\>|Home links.|
|protocols|List\<String\>|Protocol ids this code implements.|
|waiters|List\<String\>|Present on the model; the v7 reference parser does not persist `waiters` onto the entity (only on history when supplied).|
|owner|String|Signer of the `publish` tx (FID).|
|birthTime|Long|Block time of `publish`.|
|birthHeight|Long|Block height of `publish`.|
|lastTxId|String|Last affecting txid.|
|lastTime|Long|Time of last affecting tx.|
|lastHeight|Long|Height of last affecting tx.|
|tCdd|Long|Sum of CDD contributing to ratings.|
|tRate|Float|CDD-weighted mean rating.|
|active|Boolean|Whether the code entry is active.|
|closed|Boolean|Whether the code entry is permanently closed.|
|closeStatement|String|Reserved on entity; not set by the v7 reference `close` path (statement lives on history).|

#### CodeHistory (keyed by txid `id`, one row per successful operation)

|Field|Type|Description|
|---|---|---|
|id|String|This operation's txid.|
|height|Long|Block height.|
|index|Integer|Tx index in block.|
|time|Long|Block time.|
|signer|String|Signer FID.|
|op|String|`publish`, `update`, `stop`, `recover`, `close`, `rate`.|
|codeId|String|Target id (`publish` / `update` / `rate`); for `publish`, equals `id`.|
|codeIds|List\<String\>|For `stop` / `recover` / `close`.|
|name, ver, did, desc, langs, home, protocols, waiters|Various|Copies from op when present.|
|rate|Integer|For `rate`.|
|cdd|Long|CDD snapshot for `rate`.|
|closeStatement|String|For `close` when provided.|

## Examples

### Example 1: Publish

**Signer:** `FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV`

**OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "2",
  "ver": "7",
  "name": "Code",
  "data": {
    "op": "publish",
    "name": "FC-JDK",
    "ver": "1.0.0",
    "desc": "Java development kit for the Freeverse ecosystem.",
    "langs": ["Java"],
    "home": ["https://github.com/example/fc-jdk"],
    "protocols": ["txProtocol1"]
  }
}
```

**Processing:**
1. `name` = "FC-JDK" — valid (non-empty).
2. No `codeId` in `data` — valid.
3. CDD check passes.
4. `codeId` = txid of this transaction, e.g. `txCodePublish1`.
5. No existing Code entity with this id — create new.

**Code entity after operation:**

```json
{
  "id": "txCodePublish1",
  "name": "FC-JDK",
  "ver": "1.0.0",
  "desc": "Java development kit for the Freeverse ecosystem.",
  "langs": ["Java"],
  "home": ["https://github.com/example/fc-jdk"],
  "protocols": ["txProtocol1"],
  "owner": "FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV",
  "birthTime": 1672531200,
  "birthHeight": 1200000,
  "lastTxId": "txCodePublish1",
  "lastTime": 1672531200,
  "lastHeight": 1200000,
  "active": true,
  "closed": false
}
```

### Example 2: Update

**Signer:** `FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV` (same owner)

**OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "2",
  "ver": "7",
  "name": "Code",
  "data": {
    "op": "update",
    "codeId": "txCodePublish1",
    "name": "FC-JDK",
    "ver": "2.0.0",
    "desc": "Major refactor with new crypto module.",
    "langs": ["Java", "Kotlin"],
    "home": ["https://github.com/example/fc-jdk"]
  }
}
```

**Processing:**
1. `codeId` = "txCodePublish1" — found.
2. Not closed, signer is owner, is active — proceed.
3. Overwrite mutable fields; update `lastTxId`, `lastTime`, `lastHeight`.

**Code entity after operation:**

```json
{
  "id": "txCodePublish1",
  "name": "FC-JDK",
  "ver": "2.0.0",
  "desc": "Major refactor with new crypto module.",
  "langs": ["Java", "Kotlin"],
  "home": ["https://github.com/example/fc-jdk"],
  "protocols": ["txProtocol1"],
  "owner": "FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV",
  "birthTime": 1672531200,
  "birthHeight": 1200000,
  "lastTxId": "txCodeUpdate1",
  "lastTime": 1672535000,
  "lastHeight": 1200050,
  "active": true,
  "closed": false
}
```

### Example 3: Stop and recover

Owner sends `stop` with `"codeIds": ["txCodePublish1"]` → `active` = false.
Later, owner sends `recover` with the same list → `active` = true (if not `closed`).

**Stop OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "2",
  "ver": "7",
  "name": "Code",
  "data": {
    "op": "stop",
    "codeIds": ["txCodePublish1"]
  }
}
```

### Example 4: Close

**OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "2",
  "ver": "7",
  "name": "Code",
  "data": {
    "op": "close",
    "codeIds": ["txCodePublish1"],
    "closeStatement": "Project discontinued. Successor: txCodePublish2."
  }
}
```

After this operation, the code entry is permanently closed (`closed` = true, `active` = false). No further `update` or `recover` operations will be accepted.

### Example 5: Rate

Another FID rates `txCodePublish1` with `rate: 4` and sufficient CDD:

**OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "2",
  "ver": "7",
  "name": "Code",
  "data": {
    "op": "rate",
    "codeId": "txCodePublish1",
    "rate": 4
  }
}
```

**Processing:**
1. Signer ≠ owner — valid.
2. `rate` = 4, within 0–5 — valid.
3. CDD ≥ 1 — valid.
4. First rating: `tRate` = 4.0, `tCdd` = transaction CDD.

### Example 6: Duplicate publish ignored

A second transaction by the same signer attempts `publish` with the same txid (impossible in practice, since txids are unique). If somehow the `codeId` already exists, the operation is ignored.

### Example 7: Non-owner update rejected

A different FID `F9x2kqz7B5jRwPdd2ipziFvqq6y2tVkUV` sends `update` for `codeId: "txCodePublish1"`. Since the signer is not the owner, the operation is ignored.

## Versioning

|Version|Changes|
|---|---|
|7|Current version; aligns with construct parser. Adds `langs`, `protocols`, `waiters`, CDD-weighted ratings, bulk `stop`/`recover`/`close`, `closeStatement`|
|…|Earlier versions: incremental field and rule additions.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|[FEIP0V1_FEIP](FEIP0V1_FEIP.md)|Global FEIP rules, CDD, envelope.|
|[FEIP1V7_Protocol](FEIP1V7_Protocol.md)|Defines protocol specifications; code entries reference protocols by `pid` via the `protocols` field.|
|[FEIP5V3_Service](FEIP5V3_Service.md)|Services may list dependent code artifacts via their `codes` field.|
|FEIP6 Master (Identity)|Master FID; bulk operations in the reference parser consult `Freer.master`.|
|[FEIP15V1_APP](FEIP15V1_APP.md)|Applications may list dependent code artifacts via their `codes` field.|

## Reference Implementation

|Component|Location|
|---|---|
|Op payload|`FC-JDK/src/main/java/data/feipData/CodeOpData.java`|
|Entity|`FC-JDK/src/main/java/data/feipData/Code.java`|
|History|`FC-JDK/src/main/java/data/feipData/CodeHistory.java`|
|Parse / apply|`FEIP/FeipParser/src/main/java/construct/ConstructParser.java` → `makeCode()`, `parseCode()`|
|Rollback / reparse|`FEIP/FeipParser/src/main/java/construct/ConstructRollbacker.java`|
|Constants|`FC-JDK/src/main/java/data/feipData/Feip.java` → `FeipProtocol.CODE`|
|CDD gates|`FEIP/FeipParser/src/main/java/startFEIP/StartFEIP.java` → `CddCheckHeight`, `CddRequired` |

**Bulk authorization note:** In `parseCode`, for `stop` / `recover` / `close`, each target code entry must not be `closed`, and the signer must be the `owner` **or** satisfy the parser's `Freer` lookup on the signer (`IndicesNames.FREER`, `master` field). Deployments SHOULD align this rule with FEIP6 Master semantics once documented on-chain.
