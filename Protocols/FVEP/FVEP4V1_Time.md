# FVEP4V1_Time

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
|Title|Time|
|Type|FVEP|
|SN|4|
|Ver|1|
|Category|Foundation|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-21|
|PID||

## Abstract

This protocol defines **Freeverse Time** — a time system based on the block height of the Freecash blockchain. Freeverse Time is deterministic, decentralized, and independent of real-world clocks. It uses a custom calendar with 400-day years, where each block represents one minute. The epoch (time zero) is the genesis block of Freecash, mined at 2020-01-01 00:00:02 UTC.

## Motivation

Real-world timestamps depend on centralized time services, time zones, and clock synchronization. In a decentralized system, relying on wall-clock time introduces ambiguity and trust assumptions. The Freecash blockchain provides a natural, consensus-based time source: the block height. Every participant agrees on the block height, making it an ideal foundation for a decentralized time system.

Freeverse Time provides:

1. **Deterministic ordering** — Events are ordered by block height with no clock-synchronization disputes.
2. **Decentralized consensus** — Block height is agreed upon by all blockchain nodes.
3. **Human-readable representation** — The `year.day.hour.minute` format is easy to read and communicate.
4. **Bidirectional conversion** — Block height, Freeverse Time, and Unix timestamps can be converted to and from each other.

## Specification

### Epoch

The epoch of Freeverse Time is **block 0** (the genesis block of Freecash):

|Property|Value|
|---|---|
|Block height|0|
|Unix timestamp|1577836802 (seconds)|
|UTC|2020-01-01 00:00:02|
|UTC+8|2020-01-01 08:00:02|
|FcDate|0.0.0.0|

### Block Interval

The target block interval of the Freecash blockchain is **1 minute** (60 seconds). One block equals one Freeverse minute.

> **Note**: Actual block intervals vary due to mining difficulty adjustments. The target is a statistical average, not a guarantee. Freeverse Time tracks block height, not elapsed real-world time.

### Calendar Structure

Freeverse Time uses a fixed calendar where each unit is defined in terms of blocks:

|Unit|Symbol|Blocks|Relation|Range|
|---|---|---|---|---|
|Minute|M|1|1 block|0–59|
|Hour|H|60|60 minutes|0–23|
|Day|D|1,440|24 hours|0–399|
|Year|Y|576,000|400 days|0–∞|

Key properties:
- 1 Freeverse Year = **400** Freeverse Days
- 1 Freeverse Day = **24** Freeverse Hours
- 1 Freeverse Hour = **60** Freeverse Minutes
- 1 Freeverse Minute = **1** block

There are no months, weeks, or leap years. The calendar is purely arithmetic — no irregular adjustments.

### FcDate Format

A Freeverse Time value is represented as a dot-separated string:

```
Y.D.H.M
```

Where:
- **Y** — Year (0-based, non-negative integer)
- **D** — Day within the year (0–399)
- **H** — Hour within the day (0–23)
- **M** — Minute within the hour (0–59)

All fields are integers. Leading zeros are optional but not required. The format uses `.` (period) as the separator.

### Conversion

#### Block Height → FcDate

Given a block height `h`:

```
Y = h ÷ 576000        (integer division)
r = h mod 576000
D = r ÷ 1440
r = r mod 1440
H = r ÷ 60
M = r mod 60

FcDate = Y.D.H.M
```

Where `576000 = 400 × 24 × 60` and `1440 = 24 × 60`.

#### FcDate → Block Height

Given an FcDate `Y.D.H.M`:

```
h = Y × 576000 + D × 1440 + H × 60 + M
```

#### Block Height → Unix Timestamp (approximate)

```
unixTime = 1577836802 + h × 60    (in seconds)
```

> This conversion is approximate because actual block intervals are not exactly 60 seconds.

#### Unix Timestamp → Block Height (approximate)

```
h = (unixTime − 1577836802) ÷ 60    (integer division, in seconds)
```

### Comparison with Real-World Time

|Freeverse Time|Real-World Equivalent|
|---|---|
|1 Freeverse Minute|≈ 1 real minute|
|1 Freeverse Hour|≈ 1 real hour|
|1 Freeverse Day|≈ 1 real day|
|1 Freeverse Year (400 days)|≈ 400 real days ≈ 1.095 real years|

Since 1 Freeverse Year = 400 days ≈ 13.15 months, Freeverse Time and Gregorian calendar years drift apart over time. This is by design — Freeverse Time is a blockchain-native system, not a mapping of the Gregorian calendar.

### Rules

1. Block height is the authoritative time reference in Freeverse. FcDate is a human-readable representation of block height.

2. FcDate components MUST satisfy the ranges: Y ≥ 0, 0 ≤ D ≤ 399, 0 ≤ H ≤ 23, 0 ≤ M ≤ 59.

3. FcDate values MUST be normalized — each component within its valid range. For example, `0.0.0.60` is invalid; the correct representation is `0.0.1.0`.

4. Conversion between FcDate and Unix timestamps is approximate. Applications requiring precise real-world timing SHOULD use the actual block timestamps recorded on-chain rather than the `height × 60` approximation.

5. FcDate values before the epoch (negative block heights) are undefined.

6. When a single time value is transmitted or stored, block height (a plain integer) SHOULD be preferred over FcDate string for compactness. FcDate is intended for human-readable display and communication.

7. The `.` (period) separator MUST be used in FcDate string representation. Implementations MUST NOT use other separators.

## Examples

### Block height to FcDate

|Block Height|Computation|FcDate|Approximate UTC|
|---|---|---|---|
|0|0÷576000=0, 0÷1440=0, 0÷60=0, 0%60=0|0.0.0.0|2020-01-01 00:00|
|61|0.0.1.1|0.0.1.1|2020-01-01 01:01|
|1440|0.1.0.0|0.1.0.0|2020-01-02 00:00|
|576000|1.0.0.0|1.0.0.0|2021-02-04 ≈|
|2961246|5.56.10.6|5.56.10.6|2025-08-16 ≈|

### FcDate to block height

```
FcDate: 3.200.12.30
Height = 3 × 576000 + 200 × 1440 + 12 × 60 + 30
       = 1728000 + 288000 + 720 + 30
       = 2016750
```

### Approximate real-world date

```
Height 2016750
Unix = 1577836802 + 2016750 × 60 = 1577836802 + 121005000 = 1698841802
≈ 2023-11-01 UTC
```

## Versioning

|Version|Date|Changes|
|---|---|---|
|1|2026-03-21|Initial version. Defines Freeverse Time based on block height with 400-day year calendar.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FVEP0 FVEP|General rules for all FVEP protocols|
|FVEP1 Entity|Entities may have time-related properties expressed in Freeverse Time|
|FVEP6 CoinDay|CoinDay calculation depends on block height and time intervals|

## Reference Implementation

- `FC-JDK/src/main/java/utils/FcDate.java` — FcDate class: parsing, formatting, and block height conversion
- `FC-JDK/src/main/java/utils/FcUtils.java` — Utility methods for block height to Unix time conversion
