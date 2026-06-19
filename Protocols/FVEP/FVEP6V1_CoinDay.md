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
|`birthHeight`|long|Block height when this Cash was created|
|`spendHeight`|long|Block height when this Cash was spent (null if unspent)|

#### CoinDay (CD)

**CoinDay (CD)** is the accumulated value-time product of a **UTXO** (unspent Cash). It measures how much value has been held for how long, and grows as new blocks are mined.

Because Freecash targets a **1-minute block interval**, block height is a more reliable and manipulation-resistant clock than block timestamps. CD/CDD are therefore measured in **block heights**, where **1 day = 1440 blocks** (60 × 24).

**Formula**:

```
CD = floor( value × floor( (currentHeight − birthHeight) / 1440 ) / 100000000 )
```

Where:
- `value` — the Cash value in **satoshi** (integer)
- `currentHeight` — the current best block height at the time of computation
- `birthHeight` — the block height when this Cash was created
- `1440` — blocks per day (60 minutes × 24 hours at 1-minute block time)
- `100000000` — satoshi per FCH (see FVEP5)
- `floor()` — integer division (round toward zero)

The unit of both CD and CDD is **cd**. 1 cd = holding 1 FCH for 1 day (1440 blocks).

**Properties**:
- CD applies to **UTXO** (unspent Cash) only.
- CD is non-negative and grows monotonically as `currentHeight` increases.
- CD is zero for Cash that is less than 1 day old (when `currentHeight − birthHeight < 1440`).
- CD is a snapshot — its value depends on the height at which it is computed.

#### CoinDay Destroyed (CDD)

**CoinDay Destroyed (CDD)** is the value-time product of a **STXO** (spent Cash), computed at the moment the Cash is spent. Once computed, CDD is fixed and does not change.

**Formula**:

```
CDD = floor( value × floor( (spendHeight − birthHeight) / 1440 ) / 100000000 )
```

Where:
- `spendHeight` — the block height at which this Cash was spent
- `birthHeight` — the block height when this Cash was created

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
Step 1: age_days = floor( (spendHeight − birthHeight) / 1440 )
Step 2: product  = value × age_days
Step 3: CDD      = floor( product / 100000000 )
```

The multiplication in Step 2 is performed in satoshi (before dividing by 100,000,000) to preserve precision. This avoids floating-point errors.

> **Note on overflow**: `value` (long, max ~9.2 × 10¹⁸) multiplied by `age_days` can overflow a 64-bit signed integer for very large or very old Cash. In practice, the total supply of FCH (~2.1 × 10⁹ FCH = ~2.1 × 10¹⁷ satoshi) and realistic ages (< 10,000 days) keep the product within safe bounds. Implementations MAY use `BigInteger` for safety.

#### Time Source

The `birthHeight` and `spendHeight` are **block heights** as recorded on the Freecash blockchain. Because Freecash targets a fixed **1-minute block interval**, block height is a deterministic, monotonic, and manipulation-resistant measure of elapsed time — more reliable than block timestamps, which miners may skew within consensus tolerances. Using heights also makes CD/CDD fully consistent across all nodes regardless of local clock or timestamp anomalies.

#### Day Boundary

A day in the CD/CDD computation is exactly **1440 blocks** (60 minutes × 24 hours). Partial days are truncated (floored), not rounded. A Cash held for 1439 blocks has 0 days of age.

### Rules

1. CD and CDD MUST be computed using the integer formula specified above. Floating-point arithmetic MUST NOT be used for final results.

2. The unit of CD and CDD is **cd** (1 cd = 1 FCH held for 1 day, i.e. 1440 blocks).

3. CD applies to UTXO (unspent Cash) and changes as new blocks arrive. It MUST be recomputed against the current best height whenever a current value is needed.

4. CDD applies to STXO (spent Cash) and is immutable. It MUST be computed at the time of spending and stored permanently.

5. Transaction CDD is the sum of the CDD values of all its inputs.

6. `birthHeight` and `spendHeight` are blockchain-recorded block heights, where 1 day = 1440 blocks.

7. CDD MUST NOT be negative. If `spendHeight < birthHeight` due to blockchain anomalies, the CDD SHOULD be treated as 0.

8. Cash younger than 1 day (age < 1440 blocks) has a CD and CDD of 0.

## Examples

### Example 1: Basic CD calculation

A Cash with 2 FCH (200,000,000 satoshi) created 10 days ago (14,400 blocks ago):

```
value         = 200000000    (2 FCH in satoshi)
heightSpan    = 14400        (10 days = 10 × 1440 blocks)
age_days      = floor(14400 / 1440) = 10
product       = 200000000 × 10 = 2000000000
CD            = floor(2000000000 / 100000000) = 20

→ CD = 20 cd
```

### Example 2: CDD of a spent Cash

A Cash with 0.5 FCH (50,000,000 satoshi), created at block height 0, spent at block height 527,040 (366 days later, 366 × 1440 blocks):

```
value         = 50000000
heightSpan    = 527040 − 0 = 527040
age_days      = floor(527040 / 1440) = 366
product       = 50000000 × 366 = 18300000000
CDD           = floor(18300000000 / 100000000) = 183

→ CDD = 183 cd
```

### Example 3: Transaction CDD

A transaction with two inputs:
- Input 1: 1 FCH held for 100 days (144,000 blocks) → CDD = 100
- Input 2: 0.5 FCH held for 200 days (288,000 blocks) → CDD = 100

```
Tx_CDD = 100 + 100 = 200 cd
```

### Example 4: Sub-day Cash (zero CDD)

A Cash with 10 FCH held for only 720 blocks (half a day):

```
age_days  = floor(720 / 1440) = 0
CDD       = floor(1000000000 × 0 / 100000000) = 0

→ CDD = 0 (less than 1 full day)
```

### Example 5: Small Cash, long hold

A Cash with 0.00000001 FCH (1 satoshi) held for 1000 days (1,440,000 blocks):

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
|1|2026-06-06|Switched the time basis from block timestamps to block heights (1 day = 1440 blocks) for determinism and manipulation resistance. CD/CDD now use `birthHeight`/`spendHeight`.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FVEP0 FVEP|General rules for all FVEP protocols|
|FVEP4 Time|Defines Freeverse Time (block height); CD/CDD measure age in block heights (1 day = 1440 blocks)|
|FVEP5 Currency|Defines FCH and satoshi; CD/CDD values are denominated in cd|

## Reference Implementation

- `FC-JDK/src/main/java/utils/FchUtils.java` — `cdd(long value, long birthHeight, long spendHeight)` method
- `FC-JDK/src/main/java/data/fchData/Cash.java` — `cd` and `cdd` fields on the Cash (UTXO) class; `makeCd(long bestHeight)` method for computing current CD
- `FchParser/src/main/java/writeEs/CdMaker.java` — batch (re)computation of UTXO `cd` against the current best height
- `FchParser/src/main/java/writeEs/BlockMaker.java` — computes the immutable `cdd` of each spent input at its spend height
