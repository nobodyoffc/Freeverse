# FEIP3V4_CID(Crypto Identity)

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
|Title|CID|
|Type|FEIP|
|SN|3|
|Version|4|
|Category|Identity|
|Status|Active|
|Author|C_armX|
|Created|2026-03-12|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

The CID (Crypto Identity) protocol enables a Freecash address (FID) to register a human-readable identity name. The CID takes the form `name_suffix` where the suffix is derived from the signer's FID, ensuring global uniqueness without central coordination. An FID can register up to 4 CIDs and switch between them freely.

## Motivation

Freecash addresses (FIDs) are 34-character strings like `FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV`, which are difficult for humans to read and remember. The CID protocol provides a human-friendly naming layer on top of FIDs, allowing users to identify each other by names like `Alice_VkUV` instead of raw addresses.

Unlike centralized naming systems, CID names are:
- **Decentralized**: Registered on-chain without any registration authority.
- **Self-sovereign**: Only the FID holder can register or unregister their CID.
- **Collision-resistant**: The suffix mechanism guarantees uniqueness even when multiple users choose the same name.

## Specification

### Operations

#### 1. register

Register a new CID for the signer's FID.

**`data` Fields:**

|Field|Required|Type| Description                                                                   |
|---|---|---|-------------------------------------------------------------------------------|
|op|Y|String| Fixed: "register"                                                             |
|name|Y|String| The desired name. MUST NOT be null, empty, or contain spaces, `@`, `#` or `/`. |

**OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "3",
  "ver": "4",
  "name": "CID",
  "data": {
    "op": "register",
    "name": "Alice"
  }
}
```

#### 2. unregister

Clear the current CID of the signer's FID.

**`data` Fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|Fixed: "unregister"|

**OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "3",
  "ver": "4",
  "name": "CID",
  "data": {
    "op": "unregister"
  }
}
```

### Parsing Rules

#### register

1. The `name` field MUST NOT be null, empty, or contain spaces, `@`,`#` or `/`. If invalid, the operation is ignored.

2. The CID is formed as `{name}_{suffix}`, where `suffix` is the last 4 characters of the signer's FID.

3. If the generated CID is already in the `usedCids` of a **different** FID, extend the suffix by one additional character from the FID (reading rightward from the FID's end) and retry. Repeat until a unique CID is found.

4. If the generated CID already exists in the signer's own `usedCids`, the CID is simply re-activated as the current CID. No new entry is added to `usedCids`.

5. An FID can have at most **4** entries in `usedCids`. If the signer already has 4 used CIDs and the new CID would be a 5th, the operation is ignored.

6. The `nameTime` field is set to the block timestamp only on the very first CID registration for this FID. Subsequent registrations do not change `nameTime`.

7. The `lastHeight` field is updated to the current block height on every successful operation.

#### unregister

1. The current `cid` field is set to an empty string.

2. The `usedCids` list is preserved -- unregistering does not remove history.

3. The `lastHeight` field is updated to the current block height.

4. If the FID has no current CID (already empty), the operation has no effect.

### Output

The CID protocol writes the following fields to the **Freer** entity and **FreerHist** history. The Freer entity is defined in the FVEP (Freeverse Ecosystem Protocol) series. Multiple FEIP protocols contribute to the same Freer entity; the CID protocol is responsible only for the fields listed below.

**Freer entity** (keyed by FID):

|Field|Type|Description|
|---|---|---|
|cid|String|The current active CID. Empty string if unregistered.|
|usedCids|List\<String\>|All CIDs ever registered by this FID. Maximum 4 entries.|
|nameTime|Long|Block timestamp of the first-ever CID registration for this FID.|
|lastHeight|Long|Block height of the most recent operation.|

**FreerHist** (keyed by txid, one record per operation):

|Field|Type|Description|
|---|---|---|
|id|String|Transaction ID|
|height|Long|Block height|
|index|Integer|Transaction index in block|
|time|Long|Block timestamp|
|signer|String|The FID that performed the operation|
|sn|String|"3"|
|ver|String|"4"|
|op|String|"register" or "unregister"|
|name|String|The registered name (null for unregister)|

## Examples

### Example 1: First-time Registration

**Signer:** `FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV`

**OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "3",
  "ver": "4",
  "name": "CID",
  "data": {
    "op": "register",
    "name": "Alice"
  }
}
```

**Processing:**
1. Suffix = last 4 chars of FID = `VkUV`
2. CID candidate = `Alice_VkUV`
3. No collision found → CID is registered

**Freer entity after operation:**

```json
{
  "id": "FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV",
  "cid": "Alice_VkUV",
  "usedCids": ["Alice_VkUV"],
  "nameTime": 1672531200
}
```

### Example 2: Name Collision

**Signer:** `F9x2kqz7B5jRwPdd2ipziFvqq6y2tVkUV`

The FID ends with the same 4 characters `VkUV`. Another user tries to register "Alice":

**Processing:**
1. Suffix = last 4 chars = `VkUV`
2. CID candidate = `Alice_VkUV` → already used by a different FID
3. Extend suffix to 5 chars = `tVkUV`
4. CID candidate = `Alice_tVkUV` → unique

**Freer entity after operation:**

```json
{
  "id": "F9x2kqz7B5jRwPdd2ipziFvqq6y2tVkUV",
  "cid": "Alice_tVkUV",
  "usedCids": ["Alice_tVkUV"],
  "nameTime": 1672531300
}
```

### Example 3: Re-registration of Same Name

The signer `FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV` has previously unregistered, and now registers "Alice" again.

**Processing:**
1. CID candidate = `Alice_VkUV`
2. Found in the signer's own `usedCids` → re-activate

**Freer entity after operation:**

```json
{
  "id": "FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV",
  "cid": "Alice_VkUV",
  "usedCids": ["Alice_VkUV"],
  "nameTime": 1672531200
}
```

The `nameTime` remains unchanged (preserves the original first registration time).

### Example 4: Unregister

**Signer:** `FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV`

**OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "3",
  "ver": "4",
  "name": "CID",
  "data": {
    "op": "unregister"
  }
}
```

**Freer entity after operation:**

```json
{
  "id": "FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV",
  "cid": "",
  "usedCids": ["Alice_VkUV"],
  "nameTime": 1672531200
}
```

The `cid` is cleared, but `usedCids` is preserved.

### Example 5: Maximum CIDs Reached

The signer already has 4 entries in `usedCids`: `["Alice_VkUV", "Bob_VkUV", "Carol_VkUV", "Dave_VkUV"]`.

A new registration with `"name": "Eve"` would produce `Eve_VkUV` which is not in `usedCids`. Since `usedCids` already has 4 entries, the operation is **ignored**. No state change occurs.

## Versioning

|Version|Changes|
|---|---|
|4|Current version|
|3|Add the rule of maximum 4 used CIDs for a FID|
|2|Use JSON|
|1|Initial version|

## Related Protocols

| Protocol                                    |Relationship|
|---------------------------------------------|---|
| [FEIP4_Nobody](FEIP4V1_Nobody.md)           |Declares an FID as abandoned. Operates on the same Freer entity.|
| [FEIP6_Master](FEIP6V1_Master.md)           |Sets a master address for an FID. Operates on the same Freer entity.|
| [FEIP9_Home](FEIP9V1_Home.md)               |Sets homepage links for an FID. Operates on the same Freer entity.|
| [FEIP10_NoticeFee](FEIP10V1_NoticeFee.md)   |Sets notice fee for an FID. Operates on the same Freer entity.|
| [FEIP11_NID](FEIP11V1_NID.md)               |Registers named identifiers. Can reference FIDs with CIDs.|
| [FEIP16_Reputation](FEIP16V1_Reputation.md) |Rates the reputation of FIDs. Operates on the same Freer entity.|

## Reference Implementation

|Component|Location|
|---|---|
|OpData|`FC-JDK/src/main/java/data/feipData/CidOpData.java`|
|Parser|`FEIP/FeipParser/src/main/java/identity/IdentityParser.java` → `makeCid()`, `parseCid()`|
|Rollbacker|`FEIP/FeipParser/src/main/java/identity/IdentityRollbacker.java`|
|Entity|`FC-JDK/src/main/java/data/fchData/Freer.java`|
|History|`FC-JDK/src/main/java/data/feipData/FreerHist.java`|
