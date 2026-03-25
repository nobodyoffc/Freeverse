# FEIP11V1_NID

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
|Title|NID|
|Type|FEIP|
|SN|11|
|Version|1|
|Category|Identity|
|Status|Active|
|Author|C_armX, No1_NrC7|
|Created|2026-03-22|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

The NID (Named ID) protocol allows any Subject to create a personal namespace and register human-readable names that point to Object IDs (OIDs). A NID takes the form `name@namerSubjectId`, making OIDs — which are typically 64-character hex strings — easy to read, remember, and communicate. Each Subject owns its own namespace; the same name may be used by different namers without conflict. In the current version, the namer is identified by its FID (or CID), but future versions may support other kinds of subjectId.

## Motivation

Object IDs in Freeverse are typically hash-based (DID) or txid-based (SID, AID, codeId, etc.) — all 64-character hex strings that are impossible for humans to remember or communicate verbally. While CID (FEIP3) solves this problem for Subject identities, there is no equivalent for Objects.

NID provides:

1. **Human-readable Object references** — `myAPI@Alice_kUV` is far easier than `a1b2c3d4e5f6...`.
2. **Personal namespaces** — Each Subject controls its own namespace. No central authority allocates names.
3. **Decentralized naming** — Names are registered on-chain, making them censorship-resistant and publicly verifiable.
4. **Location integration** — NIDs integrate naturally with the location notation defined in FVEP3, where `@` has higher precedence than `/`, allowing NIDs to appear in paths like `platform/myAPI@Alice_kUV/v2`.

## Specification

### Namespace Model

Each Subject (namer) owns an independent namespace. A name is unique within a namer's namespace but not globally. The combination of `name` + `namer's subjectId` is globally unique.

The **NID string** is formatted as:

```
name@subjectId
```

Where `subjectId` is the namer's identifier. In the current version, this is a FID or CID (see FVEP2).

For example:
- `myDoc@FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV` (using FID)
- `myDoc@Alice_kUV` (using CID for readability)

The canonical form uses the namer's FID. When other kinds of subjectId are introduced in the future, they may also serve as the namer identifier in the NID format.

### NID Entity ID

The `id` of a NID entity is derived deterministically from the name and the namer's subjectId:

```
id = SHA-256 × 2 ( name + subjectId )
```

Where `name + subjectId` is the string concatenation (UTF-8 encoded). In the current version, `subjectId` is the namer's FID. This ensures that the same name registered by the same namer always produces the same entity ID, regardless of when or how many times the operation is performed.

### Operations

#### 1. add

Register a new NID — a mapping from a human-readable name to an OID.

**`data` Fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|`"add"`|
|name|Y|String|The human-readable name to register. MUST NOT be empty.|
|oid|Y|String|The Object ID that this name points to.|
|desc|N|String|A description of what this NID refers to.|

**OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "11",
  "ver": "1",
  "name": "NID",
  "data": {
    "op": "add",
    "name": "myAPI",
    "oid": "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90",
    "desc": "My public API service"
  }
}
```

#### 2. stop

Deactivate one or more NIDs. The NID entities remain in the index but are marked as inactive.

**`data` Fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|`"stop"`|
|names|Y|String[]|List of name strings to deactivate. Each name is resolved within the signer's namespace.|

**OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "11",
  "ver": "1",
  "name": "NID",
  "data": {
    "op": "stop",
    "names": ["myAPI", "oldDoc"]
  }
}
```

#### 3. recover

Reactivate previously stopped NIDs.

**`data` Fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|op|Y|String|`"recover"`|
|names|Y|String[]|List of name strings to reactivate.|

**OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "11",
  "ver": "1",
  "name": "NID",
  "data": {
    "op": "recover",
    "names": ["myAPI"]
  }
}
```

### Parsing Rules

1. The OP_RETURN MUST parse as JSON and conform to the FEIP envelope with `sn` = `"11"`. Otherwise the transaction is ignored.

2. `data.op` MUST be one of `"add"`, `"stop"`, or `"recover"`. If missing or unrecognized, the operation is ignored.

3. **For `add`**:
   - `data.name` MUST NOT be null or empty. If null, the operation is ignored.
   - `data.oid` MUST NOT be null. If null, the operation is ignored.
   - The NID entity `id` is computed as `SHA-256 × 2 (name + signer's subjectId)`. In the current version, the signer's subjectId is its FID.
   - The `nid` string is `name + "@" + signer's subjectId`.
   - The `namer` is the signer's subjectId.
   - If a NID entity with the same `id` already exists, it is overwritten (the name is re-registered with the new oid).
   - The entity is created with `active` = true.

4. **For `stop`**:
   - `data.names` MUST be a non-empty list. If null or empty, the operation is ignored.
   - For each name in the list, compute `id = SHA-256 × 2 (name + signer's subjectId)`.
   - Look up the NID entity by `id`. If not found, skip that name.
   - The `namer` of the found NID MUST equal the signer. If not, skip that name (a namer can only stop their own NIDs).
   - Set `active` = false and update `lastTime` and `lastHeight`.

5. **For `recover`**:
   - Same rules as `stop`, but set `active` = true instead of false.

6. Only the **namer** (the Subject that created the NID) can stop or recover it. Operations on NIDs belonging to other namers are silently skipped.

### Output

**Nid entity** (keyed by `SHA-256 × 2(name + namer's subjectId)`):

|Field|Type|Description|
|---|---|---|
|id|String|`SHA-256 × 2 (name + subjectId)` — deterministic entity ID.|
|nid|String|The full NID string: `name@subjectId`.|
|name|String|The human-readable name.|
|oid|String|The Object ID that this name points to.|
|desc|String|Description (may be null).|
|namer|String|The subjectId of the Subject that registered this NID (currently FID).|
|birthTime|Long|Block timestamp when this NID was first created.|
|birthHeight|Long|Block height when this NID was first created.|
|lastTime|Long|Block timestamp of the last operation on this NID.|
|lastHeight|Long|Block height of the last operation on this NID.|
|active|Boolean|Whether this NID is currently active.|

## Examples

### Example 1: Register a NID

**Signer:** `FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV`

**OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "11",
  "ver": "1",
  "name": "NID",
  "data": {
    "op": "add",
    "name": "communityRules",
    "oid": "b3a2e7c9f1d4a6b8e0c2d4f6a8b0c2d4e6f8a0b2c4d6e8f0a2b4c6d8e0f2a4",
    "desc": "The official community rules document"
  }
}
```

**Result:**
- NID entity created with:
  - `id` = SHA-256 × 2 (`"communityRules" + "FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV"`)
  - `nid` = `"communityRules@FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV"`
  - `name` = `"communityRules"`
  - `oid` = `"b3a2e7c9f1d4..."`
  - `namer` = `"FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV"`
  - `active` = true

### Example 2: Use NID in a location path (FVEP3)

Once registered, the NID can be used in location expressions:

```
platform/communityRules@FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV/section3
```

Or with the namer's CID for readability:

```
platform/communityRules@Alice_kUV/section3
```

### Example 3: Stop a NID

**Signer:** `FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV` (must be the namer)

```json
{
  "type": "FEIP",
  "sn": "11",
  "ver": "1",
  "name": "NID",
  "data": {
    "op": "stop",
    "names": ["communityRules"]
  }
}
```

**Result:** The NID `communityRules@FPL44...` is set to `active` = false. It still exists in the index but is no longer considered active.

### Example 4: Recover a stopped NID

```json
{
  "type": "FEIP",
  "sn": "11",
  "ver": "1",
  "name": "NID",
  "data": {
    "op": "recover",
    "names": ["communityRules"]
  }
}
```

**Result:** The NID is reactivated (`active` = true).

### Example 5: Re-register (update the target OID)

To change the OID that a name points to, simply `add` the same name again with a new OID:

```json
{
  "type": "FEIP",
  "sn": "11",
  "ver": "1",
  "name": "NID",
  "data": {
    "op": "add",
    "name": "communityRules",
    "oid": "d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4",
    "desc": "Updated community rules v2"
  }
}
```

**Result:** The existing NID entity (same `id`) is overwritten with the new `oid` and `desc`.

### Example 6: Wrong namer — stop fails silently

If signer `F_other...` tries to stop `communityRules` which was registered by `FPL44...`, the operation is silently skipped because the signer does not match the namer.

## Versioning

|Version|Changes|
|---|---|
|1|Current version|

## Related Protocols

|Protocol|Relationship|
|---|---|
|[FEIP0_FEIP](FEIP0V1_FEIP.md)|General FEIP rules (signer, CDD, OP_RETURN limits).|
|[FEIP3_CID](FEIP3V4_CID.md)|CID provides human-readable names for Subjects; NID provides human-readable names for Objects.|
|[FVEP2_ID](../FVEP/FVEP2V1_ID.md)|Defines the ID system including NID as a human-readable OID.|
|[FVEP3_Location](../FVEP/FVEP3V1_Location.md)|NID format `name@subjectId` integrates with the location notation (@ operator).|

## Reference Implementation

|Component|Location|
|---|---|
|OpData|`FC-JDK/src/main/java/data/feipData/NidOpData.java`|
|Entity|`FC-JDK/src/main/java/data/feipData/Nid.java`|
|Parser|`FEIP/FeipParser/src/main/java/identity/IdentityParser.java` → `parseNid()`|
|Dispatcher|`FEIP/FeipParser/src/main/java/startFEIP/FileParser.java` → `case NID`|
|Rollback|`FEIP/FeipParser/src/main/java/identity/IdentityRollbacker.java`|
