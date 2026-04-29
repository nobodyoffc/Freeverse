# FAPI2V1_FCDSL

## Contents

[Summary](#summary)

[Abstract](#abstract)

[Motivation](#motivation)

[Specification](#specification)

[URL Parameter Encoding](#url-parameter-encoding)

[Query Examples](#query-examples)

[Validation Rules](#validation-rules)

[Versioning](#versioning)

[Related Protocols](#related-protocols)

[Reference Implementation](#reference-implementation)

---

## Summary

|Field|Content|
|---|---|
|Title|FCDSL|
|Type|FAPI|
|SN|2|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-03-28|
|PID||

## Abstract

FAPI2 defines FCDSL (Freeverse Common Data Service Language), the standard query language for all FAPI query-type requests. FCDSL provides a declarative, JSON-based way to express data retrieval operations including filtering, exclusion, text search, range comparison, sorting, field selection, and cursor-based pagination. FCDSL is backend-agnostic: while the reference implementation translates to Elasticsearch queries, any conformant backend (SQL, NoSQL, or other) can implement compatible behavior.

## Motivation

The Freeverse ecosystem exposes many entity types -- blocks, transactions, cash records, identities, contacts, services, tokens, and more -- through FAPI endpoints. Without a common query language, each endpoint would define its own ad hoc parameter scheme, forcing clients to learn a different syntax for every entity. A shared query language allows clients to construct arbitrarily complex queries against any entity type using a single, well-defined grammar.

FCDSL draws a deliberate line between three query roles:

1. **Query** -- the primary selection criteria (mapped to Elasticsearch `must` clauses). Query conditions participate in relevance scoring.
2. **Filter** -- additional mandatory constraints (mapped to Elasticsearch `filter` clauses). Filter conditions do NOT affect relevance scoring and are cached for performance.
3. **Except** -- exclusion constraints (mapped to Elasticsearch `must_not` clauses). Except conditions remove matching documents from the result set.

This three-role separation lets implementations optimize execution: filters can be cached, scoring can be skipped when irrelevant, and exclusions are handled independently. Clients that do not need scoring can place all conditions in `filter` for maximum performance.

FCDSL also defines cursor-based pagination via the `after` field, which avoids the deep-pagination performance problems of offset-based schemes. The cursor is opaque to the client, making it safe across concurrent index changes.

## Specification

### FCDSL Object

An FCDSL query is a JSON object with the following top-level fields:

```json
{
  "ver": "1",
  "entity": "string",
  "endpoint": "string",
  "ids": ["id1", "id2"],
  "query": { ... },
  "filter": { ... },
  "except": { ... },
  "size": "20",
  "sort": [ ... ],
  "after": ["cursor1", "cursor2"],
  "fields": ["field1", "field2"],
  "noFields": ["field3", "field4"],
  "other": { "key": "value" }
}
```

### Top-Level Fields

|Field|Type|Required|Description|
|---|---|---|---|
|ver|string|N|FCDSL version. Current version: `"1"`.|
|entity|string|N|Entity type to query. Determines which data index or collection to search. Examples: `"block"`, `"tx"`, `"cash"`, `"freer"`, `"contact"`, `"mail"`, `"service"`, `"token"`.|
|endpoint|string|N|Special API endpoint name for non-entity queries (e.g., `"totals"`). When present, the server routes the request to the named endpoint handler instead of performing an entity/index query.|
|ids|array of strings|N|Specific entity IDs to fetch by direct lookup. When present, performs a batch multi-get by document ID. The fields `query`, `filter`, `except`, `sort`, `size`, and `after` MUST NOT be present.|
|query|object|N|Primary query conditions. See [Query/Filter/Except Structure](#queryfilterexcept-structure). Mapped to Elasticsearch `must` (participates in scoring).|
|filter|object|N|Filter conditions. Same structure as `query`. Mapped to Elasticsearch `filter` (no scoring, cacheable).|
|except|object|N|Exclusion conditions. Same structure as `query`. Mapped to Elasticsearch `must_not`.|
|size|string|N|Maximum number of results to return per page. String representation of a positive integer. Default: server-defined (typically `"20"`). Maximum: server-defined (typically `"100"`).|
|sort|array of Sort objects|N|Sort order specification. See [Sort Structure](#sort-structure).|
|after|array of strings|N|Pagination cursor returned by the previous response's `last` field. Pass this value unchanged to retrieve the next page.|
|fields|array of strings|N|Field inclusion list. When present, only these fields are returned in each result entity. Reduces response payload.|
|noFields|array of strings|N|Field exclusion list. When present, these fields are omitted from each result entity.|
|other|object (string-to-string map)|N|Additional key-value parameters for endpoint-specific or extension use. Implementations MAY ignore unrecognized keys.|

### Mutual Exclusivity Rules

- `ids` is mutually exclusive with `query`, `filter`, `except`, `sort`, `size`, and `after`. If `ids` is present and any of these fields is also present, the FCDSL is considered invalid.
- `query`, `filter`, and `except` MAY be combined freely. When none of the three is present and `ids` is also absent, the query matches all documents (match-all).
- `fields` and `noFields` MAY be combined. When both are present, `fields` specifies the inclusion set and `noFields` specifies additional exclusions within that set.

### Query/Filter/Except Structure

The `query`, `filter`, and `except` fields share the same structure. Each is a JSON object that MAY contain any combination of the following condition types:

```json
{
  "terms": { ... },
  "part": { ... },
  "match": { ... },
  "range": { ... },
  "equals": { ... },
  "unequals": { ... },
  "exists": ["field1", "field2"],
  "unexists": ["field3"]
}
```

All condition types within a single `query`/`filter`/`except` block are combined with logical AND. That is, a document must satisfy ALL specified conditions within the block to match.

#### terms (multi-field, multi-value match)

```json
{
  "terms": {
    "fields": ["field1", "field2"],
    "values": ["value1", "value2", "value3"]
  }
}
```

Matches documents where ANY of the listed fields contains ANY of the listed values. The fields are combined with OR, and the values within each field are combined with OR.

|Sub-field|Type|Description|
|---|---|---|
|fields|array of strings|One or more field names to search.|
|values|array of strings|One or more values to match against.|

Semantic: For each field, check whether the field's value is in the values list. A document matches if at least one field matches at least one value.

#### part (wildcard / substring match)

```json
{
  "part": {
    "fields": ["field1", "field2"],
    "value": "substring",
    "isCaseInsensitive": "true"
  }
}
```

Matches documents where ANY of the listed fields contains the given substring (wildcard `*substring*`).

|Sub-field|Type|Description|
|---|---|---|
|fields|array of strings|One or more field names to search.|
|value|string|The substring to search for.|
|isCaseInsensitive|string|Optional. `"true"` for case-insensitive matching. Default: `"false"`.|

#### match (full-text search)

```json
{
  "match": {
    "fields": ["field1", "field2"],
    "value": "search text"
  }
}
```

Performs full-text (analyzed) search on the specified fields. The value is tokenized and matched against the field's analyzed content.

|Sub-field|Type|Description|
|---|---|---|
|fields|array of strings|One or more field names to search.|
|value|string|The text to search for.|

Multiple fields are combined with OR: a document matches if any field matches the search text.

#### range (comparison)

```json
{
  "range": {
    "fields": ["field1"],
    "gt": "100",
    "gte": "100",
    "lt": "200",
    "lte": "200"
  }
}
```

Matches documents where the field value falls within the specified bounds.

|Sub-field|Type|Description|
|---|---|---|
|fields|array of strings|One or more field names. When multiple fields are specified, they are combined with OR.|
|gt|string|Optional. Greater than.|
|gte|string|Optional. Greater than or equal.|
|lt|string|Optional. Less than.|
|lte|string|Optional. Less than or equal.|

At least one bound (`gt`, `gte`, `lt`, `lte`) MUST be specified. Multiple bounds MAY be combined (e.g., `gte` and `lt` together define a half-open interval).

#### equals (exact multi-value match on single field)

```json
{
  "equals": {
    "fields": ["field1"],
    "values": ["value1", "value2"]
  }
}
```

Matches documents where the field value equals any of the listed values. Functionally similar to `terms`, but semantically intended for exact equality checks. Multiple fields are combined with OR.

|Sub-field|Type|Description|
|---|---|---|
|fields|array of strings|One or more field names.|
|values|array of strings|One or more values to match.|

#### unequals (exact multi-value exclusion)

```json
{
  "unequals": {
    "fields": ["field1"],
    "values": ["value1", "value2"]
  }
}
```

Matches documents where the field value does NOT equal any of the listed values. The inverse of `equals`.

|Sub-field|Type|Description|
|---|---|---|
|fields|array of strings|One or more field names.|
|values|array of strings|One or more values to exclude.|

#### exists (field existence)

```json
{
  "exists": ["field1", "field2"]
}
```

Matches documents where ALL of the listed fields exist and are not null.

#### unexists (field non-existence)

```json
{
  "unexists": ["field1", "field2"]
}
```

Matches documents where ALL of the listed fields do NOT exist or are null.

### Sort Structure

Sort is an array of Sort objects. Each object specifies a field and a direction:

```json
[
  {"field": "height", "order": "desc"},
  {"field": "txIndex", "order": "asc"}
]
```

|Sub-field|Type|Description|
|---|---|---|
|field|string|The field name to sort by.|
|order|string|Sort direction: `"asc"` (ascending) or `"desc"` (descending). Default: `"desc"`.|

Multiple sort objects are applied in order: the first is the primary sort key, the second is the secondary sort key, and so on.

When `sort` is omitted, the server applies a default sort order appropriate to the entity type.

### Pagination

FCDSL uses cursor-based pagination via the `after` field:

1. **First page**: Send the query without the `after` field.
2. **Response**: The server returns results along with a `last` array (opaque sort values from the last returned document).
3. **Next page**: Send the same query with `after` set to the `last` values from the previous response.
4. **End of results**: When `got` < `size` (as an integer) or `last` is null/empty, there are no more pages.

#### Response Pagination Fields

|Field|Type|Description|
|---|---|---|
|data|array|The matching entities for this page.|
|got|integer|Number of items returned in this page.|
|total|integer|Total matching items across all pages.|
|last|array of strings|Cursor for the next page. Null or absent if no more results.|

The `after`/`last` values are opaque to the client. Clients MUST NOT interpret, modify, or construct these values. They MUST be passed back to the server exactly as received.

### Field Selection

The `fields` and `noFields` arrays control which fields appear in each returned entity:

- **`fields` only**: Only the listed fields are included (source filtering inclusion).
- **`noFields` only**: All fields except the listed ones are included (source filtering exclusion).
- **Both**: The listed `fields` are included, then the listed `noFields` are excluded from that set.
- **Neither**: All fields are returned (default).

Field selection does not affect which documents match the query -- it only controls the response payload.

## URL Parameter Encoding

FCDSL queries MAY be encoded as URL query parameters for HTTP GET requests. The encoding follows these rules:

```
entity=<entityName>
ids=<id1>,<id2>,...
terms=<fieldCount>,<field1>,...,<fieldN>,<value1>,...,<valueM>
match=<field1>,<field2>,...,<value>
range=<field>,gt,<value>,gte,<value>,lt,<value>,lte,<value>
part=<field1>,<field2>,...,<value>
exists=<field1>,<field2>,...
unexists=<field1>,<field2>,...
equals=<field>,<value1>,<value2>,...
sort=<field1>,<order1>,<field2>,<order2>,...
size=<integer>
after=<cursor1>,<cursor2>,...
fields=<field1>,<field2>,...
noFields=<field1>,<field2>,...
```

URL parameter encoding has the following limitations:

- The `filter` and `except` blocks support only `terms` encoding in URL form. Complex filter/except conditions require JSON POST.
- Values containing commas MUST be URL-encoded.
- All values MUST be URL-encoded per RFC 3986.

## Query Examples

### Fetch entities by IDs

```json
{
  "entity": "tx",
  "ids": ["txid_abc123", "txid_def456", "txid_ghi789"]
}
```

### Match-all with pagination

```json
{
  "entity": "block",
  "sort": [{"field": "height", "order": "desc"}],
  "size": "50"
}
```

### Query with terms

```json
{
  "entity": "cash",
  "query": {
    "terms": {
      "fields": ["owner"],
      "values": ["FEk41Kqjar45fLDriztUDTUkdki7mmcjWK"]
    },
    "range": {
      "fields": ["value"],
      "gte": "100000"
    }
  },
  "sort": [{"field": "value", "order": "desc"}],
  "size": "20"
}
```

### Query with filter and except

```json
{
  "entity": "service",
  "query": {
    "match": {
      "fields": ["name", "desc"],
      "value": "FAPI blockchain"
    }
  },
  "filter": {
    "terms": {
      "fields": ["active"],
      "values": ["true"]
    }
  },
  "except": {
    "terms": {
      "fields": ["owner"],
      "values": ["FBlacklisted123"]
    }
  },
  "size": "10"
}
```

### Substring search with field selection

```json
{
  "entity": "freer",
  "query": {
    "part": {
      "fields": ["name", "nick"],
      "value": "alice",
      "isCaseInsensitive": "true"
    }
  },
  "fields": ["id", "name", "nick", "hot"],
  "size": "20"
}
```

### Paginated query (second page)

```json
{
  "entity": "block",
  "query": {
    "range": {
      "fields": ["height"],
      "gte": "1000000"
    }
  },
  "sort": [{"field": "height", "order": "desc"}],
  "size": "50",
  "after": ["999950", "00000000000f3a6c..."]
}
```

### Existence check

```json
{
  "entity": "freer",
  "query": {
    "exists": ["master"],
    "unexists": ["noticeFee"]
  },
  "size": "30"
}
```

### URL parameter form

```
entity=cash&terms=1,owner,FEk41Kqjar45fLDriztUDTUkdki7mmcjWK&range=value,gte,100000&sort=value,desc&size=20
```

## Validation Rules

|Rule|Constraint|Behavior on Violation|
|---|---|---|
|`ids` mutual exclusivity|When `ids` is present, `query`, `filter`, `except`, `sort`, `size`, and `after` MUST be absent.|Request is rejected as invalid.|
|Maximum page size|`size` MUST NOT exceed the server-defined maximum (reference implementation: `Constants.MaxRequestSize`).|Server clamps to default size or rejects.|
|Size format|`size` MUST be a string representation of a positive integer.|Server uses default size.|
|Sort order values|`order` MUST be `"asc"` or `"desc"`.|Request is rejected as invalid.|
|Range bounds|At least one of `gt`, `gte`, `lt`, `lte` MUST be present in a `range` condition.|The range condition is ignored.|
|Field arrays|`fields` in condition types (`terms`, `match`, `part`, `range`, `equals`, `unequals`) MUST contain at least one non-empty string.|The condition is ignored.|
|Value presence|`values` in `terms`/`equals`/`unequals` and `value` in `match`/`part` MUST be non-null and non-empty.|The condition is ignored.|

## Versioning

|Version|Date|Changes|
|---|---|---|
|1|2026-03-28|Initial specification.|

## Related Protocols

- **FAPI1** -- Defines the FAPI request/response envelope in which FCDSL queries are carried.
- **FVEP1 (Entity)** -- Defines the entity concept and the `id` field semantics used by FCDSL's `ids` lookup and `entity` field.

## Reference Implementation

The reference implementation of FCDSL is in FC-JDK (Java):

- **FCDSL data model**: `FC-JDK/src/main/java/data/apipData/Fcdsl.java`
- **Condition types**: `FC-JDK/src/main/java/data/apipData/` -- `FcQuery.java`, `Filter.java`, `Except.java`, `Terms.java`, `Match.java`, `Part.java`, `Range.java`, `Equals.java`, `Sort.java`
- **Query executor**: `FC-JDK/src/main/java/fapi/query/FcdslQueryExecutor.java`
- **Query result**: `FC-JDK/src/main/java/fapi/query/QueryResult.java`

The FC-JDK implementation translates FCDSL to Elasticsearch queries. `query` maps to `bool.must`, `filter` maps to `bool.filter`, and `except` maps to `bool.must_not`. The `after` field maps to Elasticsearch's `search_after` for cursor-based pagination.
