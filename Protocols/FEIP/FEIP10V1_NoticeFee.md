# FEIP10V1_NoticeFee

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
|Title|NoticeFee|
|Type|FEIP|
|SN|10|
|Version|1|
|Category|Identity|
|Status|Active|
|Author|C_armX|
|Created|2026-03-20|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

The NoticeFee protocol stores a **single string** on the signer's **Freer** record representing a fee (or fee policy) that senders must satisfy to deliver notices to that FID. There is no `op` field: each valid transaction sets `noticeFee` as given.

## Motivation

Higher-level messaging or notification systems can read `noticeFee` from indexers to enforce spam resistance or economic friction without encoding payment logic inside this minimal FEIP.

## Specification

### Operations

#### 1. set (implicit)

**`data` Fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|noticeFee|N|String|Stored verbatim on **Freer.noticeFee**. The reference parser does not validate format, numeric range, or units. Omitted or null values are passed through as the deserialized object allows.|

There is **no** `op` discriminator.

**OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "10",
  "ver": "1",
  "name": "NoticeFee",
  "data": {
    "noticeFee": "10000"
  }
}
```

### Parsing Rules

1. The OP_RETURN MUST parse as JSON. If parsing fails or `data` is null, the operation is ignored.

2. If a **Freer** exists for the signer, set `noticeFee` from `data.noticeFee` and set `lastHeight` to the current block height.

3. If no **Freer** exists, create a **Freer** with `id` = signer, set `noticeFee` from `data.noticeFee`, and set `lastHeight`.

4. The reference implementation does not interpret `noticeFee` semantics (e.g. satoshis vs display string); clients and higher protocols define meaning.

### Output

**Freer entity** (keyed by FID):

|Field|Type|Description|
|---|---|---|
|noticeFee|String|Fee string from the latest successful operation.|
|lastHeight|Long|Block height of that operation.|

**FreerHist** (keyed by txid):

|Field|Type|Description|
|---|---|---|
|id|String|Transaction ID|
|height|Long|Block height|
|index|Integer|Transaction index in block|
|time|Long|Block timestamp|
|signer|String|Signer FID|
|sn|String|"10"|
|ver|String|"1"|
|noticeFee|String|Value from `data.noticeFee`|

Unified history DTOs (e.g. **CidHist**) MAY include `noticeFee`; normative stored history is **FreerHist** in `FREER_HISTORY`.

## Examples

### Example 1: Set notice fee

```json
{
  "type": "FEIP",
  "sn": "10",
  "ver": "1",
  "name": "NoticeFee",
  "data": {
    "noticeFee": "5000"
  }
}
```

Freer (new or existing) gets `noticeFee: "5000"` and updated `lastHeight`.

### Example 2: Clear or update

A later transaction can set `noticeFee` to another string (e.g. empty string) if the client encodes “no fee” that way; the parser does not special-case empty values.

## Versioning

|Version|Changes|
|---|---|
|1|Current version|

## Related Protocols

| Protocol                          |Relationship|
|-----------------------------------|---|
| [FEIP0_FEIP](FEIP0V1_FEIP.md)     |General FEIP rules.|
| [FEIP3_CID](FEIP3V4_CID.md)       |CID on the same Freer entity.|
| [FEIP4_Nobody](FEIP4V1_Nobody.md) |Nobody on the same Freer entity.|
| [FEIP6_Master](FEIP6V1_Master.md) |Master on the same Freer entity.|
| [FEIP9_Home](FEIP9V1_Home.md)     |Home on the same Freer entity.|
| [FEIP11_NID](FEIP11V1_NID.md)     |Named identifiers; orthogonal to notice fee storage.|
