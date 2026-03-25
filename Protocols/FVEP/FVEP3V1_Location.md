# FVEP3V1_Location

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
|Title|Location|
|Type|FVEP|
|SN|3|
|Ver|1|
|Category|Foundation|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-21|
|PID||

## Abstract

This protocol defines a notation for expressing the location of entities and the positional relationships between entities in the Freeverse ecosystem. The notation uses five operators — **Slash**, **At**, **Sharp**, **Escape**, and **Typed ID** — to describe containment hierarchies, entity references, and data fragments. Location expressions are used in network addressing, storage paths, cross-references, and documentation.

## Motivation

Entities in Freeverse exist within hierarchical structures — blocks contain transactions, transactions contain outputs, services contain endpoints, documents contain sections. A formal notation for expressing these positional relationships enables:

1. **Uniform addressing** across network APIs and storage systems.
2. **Cross-referencing** between entities in different contexts.
3. **Path-based navigation** through entity hierarchies.
4. **Unambiguous identification** of data fragments within entities.

Without a standard location notation, each service and application would invent its own path scheme, breaking interoperability.

## Specification

### Operators

|Symbol|Name|Semantic|Direction|
|---|---|---|---|
|`/`|**Slash** (Path Operator)|Parent contains child|Left to right: `A/B/C` means A > B > C|
|`@`|**At** (Within Operator)|Child is within parent|Left to right: `A@B@C` means C > B > A|
|`#`|**Sharp** (Part Indicator)|A data fragment (not an entity) within an entity|`entity#part` refers to a non-entity data part|
|`\`|**Escape**|The next character is literal, not an operator|`big\@dog` is the entity name "big@dog"|
|`()`|**Typed ID** (defined in FVEP2)|Disambiguates txid-based OIDs|`(codeId)abc...` is a Code entity|

### Slash — Path Operator `/`

Slash expresses containment from parent to child, reading left to right:

```
A/B/C
```

This means: A contains B, B contains C. The hierarchy from outermost to innermost is A > B > C.

Slash is the primary operator for constructing paths, analogous to filesystem paths and URLs.

### At — Within Operator `@`

At expresses containment from child to parent, reading left to right:

```
A@B@C
```

This means: A is within B, B is within C. The hierarchy from outermost to innermost is C > B > A.

At is the inverse of Slash. Any pure At expression can be converted to a pure Slash expression by reversing the segment order:

```
A@B@C  ↔  C/B/A
```

The At operator also appears in **NID** (Named ID) format. A NID like `myDoc@Alice_kUV` uses `@` to express that the named object `myDoc` is registered by the subject `Alice_kUV`. When a NID appears within a Slash path, it is treated as a single unit (see [Precedence](#precedence)).

### Sharp — Part Indicator `#`

Sharp refers to a **data fragment** within an entity — a field, property, or section that is not itself an independent entity:

```
entity#part
```

For example:
- `article123#title` — the "title" field of entity article123
- `(SID)abc123/config#apiUrl` — the "apiUrl" field of the "config" entity within Service abc123

The part indicated by `#` is NOT an entity and does NOT have its own EID. It is a piece of data internal to the entity on its left.

Sharp binds tighter than both At and Slash (see [Precedence](#precedence)), so `A/B#field` is always parsed as `A / [B#field]`.

### Escape `\`

Escape causes the next character to be treated as a literal character rather than an operator. This is necessary because entity names, CIDs, or document titles may contain `@`, `/`, or `#`.

```
big\@dog       → entity name is "big@dog"
path\/to       → entity name is "path/to"
section\#1     → entity name is "section#1"
\\literal      → entity name is "\literal"
```

Escape has the highest precedence — it is always resolved first.

### Typed ID `()`

The Typed ID notation `(type)value` from FVEP2 disambiguates txid-based OIDs within location expressions:

```
(SID)abc123/users
(PID)def456/implementations/(codeId)ghi789
```

Typed IDs are resolved after Escape and Sharp but before At and Slash.

### Precedence

Operators are resolved in the following order (highest precedence first):

|Priority|Operator|Name|
|---|---|---|
|1 (highest)|`\`|Escape|
|2|`#`|Sharp|
|3|`()`|Typed ID|
|4|`@`|At|
|5 (lowest)|`/`|Slash|

For operators of the same precedence, **left-to-right associativity** applies.

This precedence ensures that:
- Escape sequences are resolved before anything else.
- Fragments (`entity#part`) group as single units.
- Typed IDs (`(type)value`) group as single units.
- At expressions (including NIDs like `name@subject`) group before Slash splits the path.
- Slash is resolved last, splitting the expression into the final path segments.

### Parsing Algorithm

Given a location string, parse as follows:

1. **Resolve Escape** — Scan left to right. Each `\` causes the next character to be treated as a literal. Remove the `\` and mark the following character as non-operator.

2. **Resolve Sharp** — Scan left to right. Each unescaped `#` binds the identifier on its left (the entity) with the identifier on its right (the part) into a single fragment reference.

3. **Resolve Typed ID** — Scan left to right. Each unescaped `(` finds the matching `)` and groups the type with the value that follows into a single typed entity reference.

4. **Resolve At** — Scan left to right. Each unescaped `@` groups the segment on its left with the segment on its right into a single unit (the left is within the right).

5. **Resolve Slash** — Scan left to right. Each unescaped `/` separates the expression into path segments ordered from outermost (left) to innermost (right).

The result is an ordered list of segments from root (outermost container) to leaf (innermost entity or fragment).

### Equivalence and Conversion

A pure Slash path and a pure At path can express the same hierarchy:

```
A/B/C  ↔  C@B@A
```

To convert:
- **Slash → At**: Reverse the segment order and replace `/` with `@`.
- **At → Slash**: Reverse the segment order and replace `@` with `/`.

A mixed expression (containing both `@` and `/`) can always be converted to a pure Slash form or pure At form by first resolving all operators according to precedence, then emitting the segments in the desired order.

### Canonical Form

The **canonical form** of a location is the pure **Slash form** (root→leaf). When storing or transmitting locations, the Slash form SHOULD be used unless the At form is more natural for the context (e.g., a NID reference).

### Rules

1. Every location expression MUST contain at least one entity reference.

2. Escape (`\`) MUST only precede the characters `@`, `/`, `#`, `\`, `(`, `)`. Escaping other characters has no effect but is not an error.

3. Sharp (`#`) MUST be preceded by an entity reference and followed by a part name. The part name is NOT an entity — it has no EID.

4. A Typed ID (`(type)value`) MUST conform to the Typed ID notation defined in FVEP2.

5. When a NID (format `objectName@subjectId`) appears within a Slash path, it is automatically grouped as a single unit due to At having higher precedence than Slash.

6. A location expression MUST NOT end with an operator (`/`, `@`, `#`).

7. When the location is stored or transmitted and the context does not determine the format, the canonical Slash form SHOULD be used.

8. Implementations MUST handle escape sequences correctly. Entity names, CIDs, and document titles MAY contain `@`, `/`, or `#`, which MUST be escaped when used in location expressions.

## Examples

### 1. Simple Slash path (parent→child)

```
block456/tx123/opreturn0
→ hierarchy: block456 > tx123 > opreturn0
```

### 2. Simple At path (child→parent)

```
opreturn0@tx123@block456
→ hierarchy: block456 > tx123 > opreturn0  (same as above)
```

### 3. Equivalence

```
C@B@A  ↔  A/B/C
Both describe the hierarchy: A > B > C
```

### 4. NID in a Slash path

A NID `myAPI@Alice_kUV` (the object "myAPI" registered by subject "Alice_kUV") embedded in a path:

```
platform/myAPI@Alice_kUV/v2/users
  → @ groups first: [myAPI@Alice_kUV]
  → / resolves: platform / [myAPI@Alice_kUV] / v2 / users
  → hierarchy: platform > myAPI@Alice_kUV > v2 > users
```

### 5. Typed ID in a path

```
(PID)abc123/implementations/(codeId)def456
→ hierarchy: Protocol abc123 > implementations > Code def456
```

### 6. Fragment (data part, not entity)

```
(SID)abc123/config#apiUrl
  → # groups first: [config#apiUrl]
  → / resolves: (SID)abc123 / [config#apiUrl]
  → the "apiUrl" part of the "config" entity within Service abc123
```

### 7. Escape (entity name contains @)

```
docs/big\@dog/notes
  → \ first: "big@dog" is a literal name
  → / resolves: docs / big@dog / notes
  → hierarchy: docs > big@dog > notes
```

### 8. NID with typed ID in path

```
(SID)svc001/myData@Bob_xYz/records
  → @ groups: [myData@Bob_xYz]
  → / resolves: (SID)svc001 / [myData@Bob_xYz] / records
  → hierarchy: Service svc001 > myData@Bob_xYz > records
```

### 9. Sharp and At in the same segment — ambiguity

Because `#` has higher precedence than `@`, the expression `myDoc@Alice_kUV#abstract` is parsed as:

```
myDoc@Alice_kUV#abstract
  → # first: [Alice_kUV#abstract]  (the "abstract" part of Alice_kUV)
  → @ next: myDoc @ [Alice_kUV#abstract]
  → meaning: myDoc is within the "abstract" part of Alice_kUV
```

This is probably NOT the intended meaning. To refer to the "abstract" part of the NID `myDoc@Alice_kUV`, use the Slash form instead:

```
myDoc@Alice_kUV/abstract
  → @ first: [myDoc@Alice_kUV]
  → / next: [myDoc@Alice_kUV] / abstract
  → meaning: "abstract" is within myDoc@Alice_kUV
```

Or in a full path:

```
library/myDoc@Alice_kUV/abstract
→ hierarchy: library > myDoc@Alice_kUV > abstract
```

**Rule of thumb**: avoid combining `#` and `@` in the same segment. Use `/` to navigate into a NID, and `#` only on the final entity in a path.

### 10. Conversion between forms

```
Slash form:   platform/myAPI@Alice_kUV/v2
At form:      v2@myAPI@Alice_kUV@platform
```

The At form contains three `@` symbols: the first is the At operator (v2 within myAPI), the second is part of the NID (myAPI@Alice_kUV), and the third is the At operator (NID within platform). Left-to-right associativity for `@` resolves this: `[[[v2] @ myAPI] @ Alice_kUV] @ platform`, yielding hierarchy `platform > Alice_kUV > myAPI > v2` — but this is NOT the intended meaning.

The correct At form requires escaping the `@` inside the NID:

```
Slash form:   platform/myAPI@Alice_kUV/v2
At form:      v2@myAPI\@Alice_kUV@platform
```

This correctly yields: `platform > myAPI@Alice_kUV > v2`.

**Conclusion**: when a NID appears in an At-form expression, the `@` inside the NID MUST be escaped. The Slash form avoids this issue because `@` has higher precedence than `/`, so the NID groups naturally.

## Versioning

|Version|Date|Changes|
|---|---|---|
|1|2026-03-21|Initial version. Defines Slash, At, Sharp, Escape operators and Typed ID in location expressions.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FVEP0 FVEP|General rules for all FVEP protocols|
|FVEP1 Entity|Defines the entity model referenced by location expressions|
|FVEP2 ID|Defines the Typed ID notation used in location expressions|
|FEIP11 NID|NID format uses `@` which integrates with location expressions|

## Reference Implementation

- `FC-JDK/src/main/java/data/fcData/FcLocation.java` — Location expression parser and converter
