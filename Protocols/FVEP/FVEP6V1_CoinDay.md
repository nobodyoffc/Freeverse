# FVEP6V1_CoinDay

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
|Title|CoinDay|
|Type|FVEP|
|SN|6|
|Ver|1|
|Category|Foundation|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-22|
|PID||

## Abstract

This protocol defines **CoinDay (CD)** and **CoinDay Destroyed (CDD)** — two fundamental metrics in the Freeverse ecosystem that combine currency value (FVEP5) and time (FVEP4) to measure the economic weight of unspent transaction outputs (UTXOs). CoinDay quantifies how much value has been held for how long, providing a measure of economic commitment that is resistant to manipulation.

## Motivation

In a UTXO-based blockchain, the balance of an address is just a snapshot — it says nothing about how long the value has been held. A user who just received 100 FCH and a user who has held 100 FCH for a year have the same balance, but their economic commitment is vastly different.

CoinDay and CoinDay Destroyed provide:

1. **Economic weight** — A measure that combines both the amount and the duration of holding, rewarding long-term participants.
2. **Spam resistance** — CDD-based metrics make it expensive to fake reputation or influence, since both value and time must be committed.
3. **Reputation and governance** — CDD accumulated over an address's history reflects genuine economic participation, useful for voting, ranking, and access control.
4. **Transaction significance** — CDD of a transaction indicates the economic weight of the inputs consumed, distinguishing significant transfers from trivial ones.

## Specification

### Definitions

#### UTXO (Cash)

A UTXO (Unspent Transaction Output), called **Cash** in Freeverse, is an output of a transaction that has not yet been spent. Each Cash has:

|Field|Type|Description|
|---|---|---|
|`value`|long|The amount in satoshi|
|`birthTime`|long|Unix timestamp (seconds) when this Cash was created|
|`spendTime`|long|Unix timestamp (seconds) when this Cash was spent (null if unspent)|

#### CoinDay (CD)

**CoinDay (CD)** is the accumulated value-time product of a **UTXO** (unspent Cash). It measures how much value has been held for how long, and grows as time passes.

**Formula**:

```
CD = floor( value × floor( (currentTime − birthTime) / 86400 ) / 100000000 )
```

Where:
- `value` — the Cash value in **satoshi** (integer)
- `currentTime` — the current Unix timestamp in **seconds** at the time of computation
- `birthTime` — the Unix timestamp in **seconds** when this Cash was created
- `86400` — seconds per day (60 × 60 × 24)
- `100000000` — satoshi per FCH (see FVEP5)
- `floor()` — integer division (round toward zero)

The unit of both CD and CDD is **cd**. 1 cd = holding 1 FCH for 1 day.

**Properties**:
- CD applies to **UTXO** (unspent Cash) only.
- CD is non-negative and grows monotonically as `currentTime` increases.
- CD is zero for Cash that is less than 1 day old (when `currentTime − birthTime < 86400`).
- CD is a snapshot — its value depends on when it is computed.

#### CoinDay Destroyed (CDD)

**CoinDay Destroyed (CDD)** is the value-time product of a **STXO** (spent Cash), computed at the moment the Cash is spent. Once computed, CDD is fixed and does not change.

**Formula**:

```
CDD = floor( value × floor( (spendTime − birthTime) / 86400 ) / 100000000 )
```

Where:
- `spendTime` — the Unix timestamp (seconds) of the block in which this Cash was spent
- `birthTime` — the Unix timestamp (seconds) when this Cash was created

**Properties**:
- CDD applies to **STXO** (spent Cash) only.
- CDD is computed once at the time of spending and is immutable thereafter.
- CDD represents the economic weight that was "destroyed" (consumed) by spending the Cash.
- CDD is essentially the CD of the Cash frozen at the moment of spending.

#### Transaction CDD

The **CDD of a transaction** is the sum of the CDD values of all its inputs:

```
Tx_CDD = Σ CDD(input_i)    for all inputs i
```

This measures the total economic weight consumed by the transaction.

#### Address CDD

The **CDD of an address** is the cumulative sum of CDD from all Cashes ever spent by that address:

```
Addr_CDD = Σ CDD(cash_j)    for all spent Cashes j owned by the address
```

This measures the total historical economic commitment of the address.

### Computation Details

#### Integer Arithmetic

All CD and CDD computations MUST use integer arithmetic to ensure deterministic results across implementations. The computation order matters:

```
Step 1: age_days = floor( (spendTime − birthTime) / 86400 )
Step 2: product  = value × age_days
Step 3: CDD      = floor( product / 100000000 )
```

The multiplication in Step 2 is performed in satoshi (before dividing by 100,000,000) to preserve precision. This avoids floating-point errors.

> **Note on overflow**: `value` (long, max ~9.2 × 10¹⁸) multiplied by `age_days` can overflow a 64-bit signed integer for very large or very old Cash. In practice, the total supply of FCH (~2.1 × 10⁹ FCH = ~2.1 × 10¹⁷ satoshi) and realistic ages (< 10,000 days) keep the product within safe bounds. Implementations MAY use `BigInteger` for safety.

#### Time Source

The `birthTime` and `spendTime` are **block timestamps** (Unix timestamps in seconds) as recorded on the Freecash blockchain. They are NOT Freeverse Time (block height). This ensures CD and CDD reflect actual elapsed real-world time, accounting for variations in block intervals.

#### Day Boundary

A day in the CD/CDD computation is exactly **86,400 seconds** (24 × 60 × 60). Partial days are truncated (floored), not rounded. A Cash held for 23 hours and 59 minutes has 0 days of age.

### Rules

1. CD and CDD MUST be computed using the integer formula specified above. Floating-point arithmetic MUST NOT be used for final results.

2. The unit of CD and CDD is **cd** (1 cd = 1 FCH held for 1 day).

3. CD applies to UTXO (unspent Cash) and changes over time. It MUST be recomputed whenever a current value is needed.

4. CDD applies to STXO (spent Cash) and is immutable. It MUST be computed at the time of spending and stored permanently.

5. Transaction CDD is the sum of the CDD values of all its inputs.

6. `birthTime` and `spendTime` are blockchain-recorded Unix timestamps in seconds, not Freeverse Time (block heights).

7. CDD MUST NOT be negative. If `spendTime < birthTime` due to blockchain timestamp anomalies, the CDD SHOULD be treated as 0.

8. Cash younger than 1 day (age < 86,400 seconds) has a CD and CDD of 0.

## Examples

### Example 1: Basic CD calculation

A Cash with 2 FCH (200,000,000 satoshi) created 10 days ago:

```
value     = 200000000    (2 FCH in satoshi)
age       = 864000       (10 days in seconds)
age_days  = floor(864000 / 86400) = 10
product   = 200000000 × 10 = 2000000000
CD        = floor(2000000000 / 100000000) = 20

→ CD = 20 cd
```

### Example 2: CDD of a spent Cash

A Cash with 0.5 FCH (50,000,000 satoshi), created at time 1577836802 (block 0), spent at time 1609459202 (approximately 1 year later, 365 days):

```
value     = 50000000
age       = 1609459202 − 1577836802 = 31622400
age_days  = floor(31622400 / 86400) = 366
product   = 50000000 × 366 = 18300000000
CDD       = floor(18300000000 / 100000000) = 183

→ CDD = 183 cd
```

### Example 3: Transaction CDD

A transaction with two inputs:
- Input 1: 1 FCH held for 100 days → CDD = 100
- Input 2: 0.5 FCH held for 200 days → CDD = 100

```
Tx_CDD = 100 + 100 = 200 cd
```

### Example 4: Sub-day Cash (zero CDD)

A Cash with 10 FCH held for only 12 hours (43,200 seconds):

```
age_days  = floor(43200 / 86400) = 0
CDD       = floor(1000000000 × 0 / 100000000) = 0

→ CDD = 0 (less than 1 full day)
```

### Example 5: Small Cash, long hold

A Cash with 0.00000001 FCH (1 satoshi) held for 1000 days:

```
value     = 1
age_days  = 1000
product   = 1 × 1000 = 1000
CDD       = floor(1000 / 100000000) = 0

→ CDD = 0 (too small to register as 1 cd)
```

This shows that CDD has a minimum resolution — very small amounts need to be held for very long periods to accumulate meaningful CDD.

## Versioning

|Version|Date|Changes|
|---|---|---|
|1|2026-03-22|Initial version. Defines CoinDay (CD) and CoinDay Destroyed (CDD) based on UTXO value and time.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FVEP0 FVEP|General rules for all FVEP protocols|
|FVEP4 Time|Defines Freeverse Time; CD/CDD use blockchain timestamps for computation|
|FVEP5 Currency|Defines FCH and satoshi; CD/CDD values are denominated in cd|

## Reference Implementation

- `FC-JDK/src/main/java/utils/FchUtils.java` — `cdd(long value, long birthTime, long spentTime)` method
- `FC-JDK/src/main/java/data/fchData/Cash.java` — `cd` and `cdd` fields on the Cash (UTXO) class; `makeCd()` method for computing current CD
