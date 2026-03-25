# FEIP8V1_Statement

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
|Title|Statement|
|Type|FEIP|
|SN|8|
|Version|1|
|Category|Publish|
|Status|Active|
|Author|C_armX, No1_NrC7|
|Created|2026-03-22|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

The Statement protocol allows an FID to publish a **formal, irrevocable statement** on the Freecash blockchain. A statement consists of a title and/or content, accompanied by an explicit confirmation phrase that acknowledges the irrevocable nature of the publication. Once published, a statement cannot be updated or deleted.

## Motivation

In a decentralized ecosystem, there is a need for individuals and entities to make formal declarations — public commitments, announcements, legal notices, terms of service, or any content that the publisher intends to be permanently attributable to their identity. Unlike ordinary messages or mutable content, a statement is:

1. **Immutable** — Once published on-chain, the statement cannot be modified or removed.
2. **Attributable** — The statement is permanently linked to the signer's FID via the transaction.
3. **Timestamped** — The blockchain provides a trustworthy, consensus-agreed timestamp.
4. **Deliberate** — The required confirmation phrase ensures the publisher understands the irrevocable nature of the action.

## Specification

### Operations

#### 1. publish (implicit)

There is no `op` field. A valid Statement transaction carries the statement data directly. The operation is accepted or rejected as a whole per the parsing rules below.

**`data` Fields:**

|Field|Required|Type|Description|
|---|---|---|---|
|title|N*|String|The title of the statement.|
|content|N*|String|The content of the statement.|
|confirm|Y|String|MUST be exactly: `"This is a formal and irrevocable statement."`|

\* At least one of `title` or `content` MUST be present.

**OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "8",
  "ver": "1",
  "name": "Statement",
  "data": {
    "title": "<statement title>",
    "content": "<statement content>",
    "confirm": "This is a formal and irrevocable statement."
  }
}
```

### Parsing Rules

1. The OP_RETURN MUST parse as JSON and conform to the FEIP envelope with `sn` = `"8"`. Otherwise the transaction is ignored.

2. `data.confirm` MUST be exactly `"This is a formal and irrevocable statement."` (case-sensitive, no extra whitespace). If missing or different, the operation is ignored.

3. At least one of `data.title` or `data.content` MUST be non-null. If both are null, the operation is ignored.

4. The `id` of the resulting Statement entity is the **txid** of this transaction (Transformed ID).

5. The `publisher` is the **signer** — the FID of the first input of the transaction.

6. On success:
   - Index a **Statement** entity with `id` = txid, `title`, `content`, `publisher` (signer FID), `birthTime` (block timestamp), `birthHeight` (block height).

7. There is no update or delete operation. Each valid Statement transaction creates a new, independent Statement entity. The same FID may publish any number of statements.

### Output

**Statement entity** (keyed by txid):

|Field|Type|Description|
|---|---|---|
|id|String|Transaction ID (txid) of the publish transaction.|
|title|String|The statement title (may be null if only content is provided).|
|content|String|The statement content (may be null if only title is provided).|
|publisher|String|The signer FID that published the statement.|
|birthTime|Long|Block timestamp (Unix seconds) of the publish transaction.|
|birthHeight|Long|Block height of the publish transaction.|

## Examples

### Example 1: Statement with title and content

**Signer:** `FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV`

**OP_RETURN:**

```json
{
  "type": "FEIP",
  "sn": "8",
  "ver": "1",
  "name": "Statement",
  "data": {
    "title": "Terms of Service for My API",
    "content": "By using this API you agree to the following terms...",
    "confirm": "This is a formal and irrevocable statement."
  }
}
```

**Result:** A Statement entity is created with `id` = txid, `publisher` = `FPL44YJRwPdd2ipziFvqq6y2tw4VnVvkUV`, and the given title and content.

### Example 2: Statement with content only

```json
{
  "type": "FEIP",
  "sn": "8",
  "ver": "1",
  "name": "Statement",
  "data": {
    "content": "I hereby renounce all claims to the project formerly known as XYZ.",
    "confirm": "This is a formal and irrevocable statement."
  }
}
```

**Result:** A Statement entity is created with `title` = null and the given content.

### Example 3: Missing confirm — rejected

```json
{
  "type": "FEIP",
  "sn": "8",
  "ver": "1",
  "name": "Statement",
  "data": {
    "title": "My Statement",
    "content": "Some important content."
  }
}
```

**Result:** Operation is **ignored** — `confirm` field is missing.

### Example 4: Wrong confirm text — rejected

```json
{
  "type": "FEIP",
  "sn": "8",
  "ver": "1",
  "name": "Statement",
  "data": {
    "title": "My Statement",
    "content": "Some content.",
    "confirm": "I agree."
  }
}
```

**Result:** Operation is **ignored** — `confirm` does not match the required phrase.

### Example 5: Both title and content null — rejected

```json
{
  "type": "FEIP",
  "sn": "8",
  "ver": "1",
  "name": "Statement",
  "data": {
    "confirm": "This is a formal and irrevocable statement."
  }
}
```

**Result:** Operation is **ignored** — both `title` and `content` are null.

## Versioning

|Version|Changes|
|---|---|
|1|Current version|

## Related Protocols

|Protocol|Relationship|
|---|---|
|[FEIP0_FEIP](FEIP0V1_FEIP.md)|General FEIP rules (signer, CDD, OP_RETURN limits).|
|[FEIP14_Proof](FEIP14V1_Proof.md)|Proof is a related publishing protocol; Proofs reference external evidence, Statements are self-contained declarations.|

## Reference Implementation

|Component|Location|
|---|---|
|OpData|`FC-JDK/src/main/java/data/feipData/StatementOpData.java`|
|Entity|`FC-JDK/src/main/java/data/feipData/Statement.java`|
|Parser|`FEIP/FeipParser/src/main/java/publish/PublishParser.java` → `parseStatement()`|
|Dispatcher|`FEIP/FeipParser/src/main/java/startFEIP/FileParser.java` → `case STATEMENT`|
|Rollback|`FEIP/FeipParser/src/main/java/publish/PublishRollbacker.java`|
