# FVEP5V1_Currency

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
|Title|Currency|
|Type|FVEP|
|SN|5|
|Ver|1|
|Category|Foundation|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-22|
|PID||

## Abstract

This protocol defines the currency of the Freeverse ecosystem. The primary currency is **Freecash (FCH)**, the native currency of the Freecash blockchain. FCH has three units — **FCH**, **Cash**, and **Satoshi** — with fixed conversion ratios. This protocol standardizes their names, symbols, abbreviations, and conversion rules for use across all Freeverse applications, APIs, and documentation.

## Motivation

A consistent currency notation is essential for interoperability across the Freeverse ecosystem. Without a standard:

1. Different applications may display amounts in different units without clarity.
2. API responses may use satoshi while UIs show FCH, causing confusion.
3. Rounding and precision errors can occur when conversions are not standardized.

This protocol provides a single source of truth for currency units, symbols, and conversions.

## Specification

### Currency

The native currency of the Freeverse ecosystem is **Freecash**, originating from the Freecash blockchain.

### Units

Freecash has three units, from largest to smallest:

|Unit|Abbreviation|Symbol|Description|
|---|---|---|---|
|**FCH**|F|—|The primary unit of Freecash. The symbol is the lowercase letter 'f' with an additional short horizontal bar below its existing crossbar (two horizontal bars total, similar to how € has two bars on E).|
|**Cash**|c|—|The middle unit. The symbol is the lowercase letter 'c' with a short horizontal bar through its vertical center.|
|**Satoshi**|s|—|The smallest unit. The symbol is the lowercase letter 's' with a short horizontal bar through its vertical center.|

> **Note on symbols**: The currency symbols described above are custom glyphs that do not have exact representations in standard Unicode. Implementations SHOULD render these symbols using a custom font or glyph set. The abbreviations `F`, `c`, `s` are used in text where the custom symbols are unavailable.

### Conversion

|Conversion|Ratio|
|---|---|
|1 FCH = ? Cash|1 F = 1,000,000 c|
|1 Cash = ? Satoshi|1 c = 100 s|
|1 FCH = ? Satoshi|1 F = 100,000,000 s|

The conversion ratios are fixed and will not change.

### Satoshi as Base Unit

**Satoshi** is the indivisible base unit of Freecash. All amounts on the blockchain are stored and transmitted as integer values in satoshi. There are no fractional satoshi.

The relationship between the three units:

```
1 FCH = 1,000,000 Cash = 100,000,000 Satoshi
1 Cash = 100 Satoshi
```

### Display and Formatting

#### Decimal Places

|Unit|Max Decimal Places|Example|
|---|---|---|
|FCH (F)|8|1.23456789 F|
|Cash (c)|2|1.23 c|
|Satoshi (s)|0 (integer)|123 s|

#### Formatting Rules

1. When displaying amounts in **FCH**, up to 8 decimal places MAY be shown. Trailing zeros after the decimal point SHOULD be stripped unless a fixed-width display is required.

2. When displaying amounts in **Cash**, up to 2 decimal places MAY be shown.

3. **Satoshi** amounts MUST be displayed as integers with no decimal point.

4. Negative amounts are permitted and SHOULD be prefixed with a minus sign (`-`).

5. Thousands separators (`,`) MAY be used for readability but MUST NOT be included in machine-readable formats (APIs, storage, serialization).

#### Symbol Placement

The currency symbol or abbreviation SHOULD be placed **after** the amount, separated by a space:

```
12.5 F
1250000 c
125000000 s
```

When the symbol or abbreviation is used alone (not with an amount), it is written as-is: `FCH`, `Cash`, `Satoshi`, or `F`, `c`, `s`.

### Default Unit

When the unit is not specified in an API response, storage field, or data format:

- **Integer values** SHOULD be interpreted as **Satoshi** (the base unit on the blockchain).
- **Decimal values** SHOULD be interpreted as **FCH**.

Protocols and APIs SHOULD always specify the unit explicitly to avoid ambiguity.

### Rules

1. **Satoshi** is the base unit. All on-chain values are in satoshi.

2. The conversion ratios (1 F = 1,000,000 c = 100,000,000 s) are fixed constants.

3. Implementations MUST use integer arithmetic for satoshi values. Floating-point arithmetic SHOULD be avoided for financial calculations; use fixed-point or `BigDecimal` equivalents.

4. When converting from FCH (decimal) to satoshi (integer), implementations SHOULD use rounding mode HALF_UP.

5. APIs and data formats SHOULD specify the unit of currency fields. When unspecified, integer values are satoshi and decimal values are FCH.

6. The abbreviations `F`, `c`, and `s` are case-sensitive. `F` is FCH, `c` is Cash, `s` is Satoshi.

## Examples

### Unit conversion

```
1.5 F
= 1,500,000 c
= 150,000,000 s
```

```
0.00000001 F  (the smallest amount expressible in FCH)
= 0.01 c
= 1 s
```

```
0.001 F
= 1,000 c
= 100,000 s
```

### API representation

A balance field in an API response:

```json
{
  "balance": 150000000,
  "unit": "s"
}
```

Equivalent in FCH:

```json
{
  "balance": 1.5,
  "unit": "F"
}
```

### Display formatting

|Satoshi Value|FCH Display|Cash Display|Satoshi Display|
|---|---|---|---|
|1|0.00000001 F|0.01 c|1 s|
|100|0.000001 F|1 c|100 s|
|100000000|1 F|1000000 c|100000000 s|
|123456789|1.23456789 F|1234567.89 c|123456789 s|

## Versioning

|Version|Date|Changes|
|---|---|---|
|1|2026-03-22|Initial version. Defines FCH, Cash, and Satoshi units with conversion ratios and formatting rules.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FVEP0 FVEP|General rules for all FVEP protocols|
|FVEP4 Time|Time protocol; currency and time are both foundation-level concepts|
|FVEP6 CoinDay|CoinDay is calculated from currency value and time|

## Reference Implementation

- `FC-JDK/src/main/java/constants/Constants.java` — `COIN_TO_SATOSHI` (100,000,000) and `CASH_TO_SATOSHI` (100) constants
- `FC-JDK/src/main/java/utils/FchUtils.java` — Conversion methods: `coinToSatoshi()`, `satoshiToCoin()`, `satoshiToCash()`, `satoshiToCoinStr()`
