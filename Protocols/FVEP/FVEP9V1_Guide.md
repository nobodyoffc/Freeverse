# FVEP9V1_Guide

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
|Title|Guide|
|Type|FVEP|
|SN|9|
|Ver|1|
|Category|Foundation|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-04-17|
|PID||

## Abstract

This protocol defines **Guide** — the deterministic, one-time attribution that every FID (Freecash ID) receives at the moment it first appears on the Freecash blockchain. The Guide of a newly born FID is the owner of the first input of the transaction that first paid it, or the constant `Coinbase` when the FID is introduced by a coinbase transaction. Once set, a Guide is immutable.

The Guide relation yields a directed acyclic graph (rooted at `Coinbase`) that records, for every address in the ecosystem, the address that introduced it — providing a chain-derived, tamper-resistant foundation for referral, reputation propagation, network growth analytics, and trust-path construction.

## Motivation

In any open ecosystem, understanding **how participants enter the network** is essential for:

1. **Referral and growth tracking** — Identifying which addresses bring new users into the ecosystem and at what rate.
2. **Reputation propagation** — Allowing reputation, trust, or weight to flow along introduction edges in a verifiable way.
3. **Network analysis** — Constructing the introduction DAG enables community detection, influence ranking, and Sybil resistance analysis.
4. **Reward distribution** — Services MAY reward guides for bringing in active participants, creating an organic incentive for ecosystem growth.
5. **Deterministic attribution** — Because the Guide is derived purely from on-chain data, every implementation independently agrees on the Guide of every FID without coordination.

Unlike `master` or other mutable social relations, the Guide is fixed by the first on-chain event involving an FID and can be recomputed from blockchain data at any time. This makes it an ideal low-level primitive on top of which higher-level social protocols can be built.

## Specification

### Definitions

#### FID (Freecash ID)

An identifier of a Subject, derived from its pubkey (see FVEP2). In the context of this protocol, an FID is the owner field of a Cash (UTXO). Only FIDs that correspond to an address (i.e., exclude synthetic owners such as `"Unknown"` and `"OpReturn"`) participate in the Guide relation.

#### Coinbase

The constant string value `"Coinbase"`. It represents the virtual root of the Guide DAG — the origin of all FCH that was introduced to an address by block rewards rather than by another address.

#### First Receiving Transaction

For a given FID `F`, the **first receiving transaction** is the transaction with the lowest block height (and, within a block, the lowest transaction index) that contains at least one output whose owner is `F`.

#### Birth Event

The **birth event** of `F` is the pair `(T, k)` where `T` is the first receiving transaction of `F` and `k` is the index of the first output in `T` whose owner is `F`. The birth event is unique per FID.

#### Guide

The **Guide** of an FID `F` is a string determined at the birth event of `F`:

- If `T` is a **coinbase transaction** (i.e., `T.spentCashes` is empty), then `Guide(F) = "Coinbase"`.
- Otherwise, `Guide(F) = owner(T.spentCashes[0])` — the owner of the **first input** of `T`.

`Guide(F)` MUST be set exactly once — at the birth event — and is immutable thereafter.

#### Guide DAG

The **Guide DAG** is the directed graph with:
- Nodes: every FID that has appeared on the blockchain, plus the special node `Coinbase`.
- Edges: for every FID `F`, a directed edge `Guide(F) → F`.

Because every non-root node has exactly one Guide and the Guide is always defined at an earlier block height, the structure is a directed acyclic graph rooted at `Coinbase`.

### Data Format

The Guide is represented as a field on the address entity (`Freer`):

|Field|Type|Description|
|---|---|---|
|`id`|string|The FID of the address.|
|`guide`|string|The Guide of this FID. Either the FID of another address or the constant `"Coinbase"`.|
|`birthHeight`|long|The block height of the birth event.|

JSON shape (partial):

```json
{
  "id": "FEb...abc",
  "guide": "FTx...k9Q",
  "birthHeight": 812345
}
```

### Rules

1. **Single assignment** — `Guide(F)` MUST be set exactly once, at the birth event of `F`. Subsequent receipts of Cash by `F` MUST NOT modify its Guide.

2. **Coinbase root** — If the first receiving transaction of `F` has no spent inputs (coinbase), then `Guide(F) = "Coinbase"`. The reserved value `"Coinbase"` MUST NOT be used as an FID.

3. **First-input rule** — If the first receiving transaction has one or more spent inputs, `Guide(F)` is the owner of the input at index `0` of `T.spentCashes`. The order of `spentCashes` is the order of inputs as they appear in the transaction.

4. **Self-guide allowed** — If `F` also happens to own the first input of its first receiving transaction (e.g., a self-send that produced a fresh change address), then `Guide(F) = F`. Implementations MUST NOT treat this as an error.

5. **Excluded owners** — Synthetic owner labels such as `"Unknown"` and `"OpReturn"` are not FIDs and do not participate in the Guide relation, either as subject or as Guide.

6. **Determinism** — Given the same blockchain state, every compliant implementation MUST compute the same Guide for every FID. Guide assignment is purely a function of on-chain data and MUST NOT depend on wall-clock time, local configuration, or external state.

7. **Rollback safety** — If a blockchain reorganization rolls back the block containing the birth event of `F`, then `Guide(F)` MUST be cleared so that a subsequent reparse can reassign it from the new canonical chain.

8. **Acyclicity** — Because the Guide is always defined by a transaction at an earlier or equal block height than the birth event and the first-input owner must have existed by then, the Guide DAG is acyclic (with `Coinbase` as the only root).

### Computation

The following pseudocode describes how to assign `Guide(F)` while processing a block:

```
for each transaction T in the block, in transaction order:
    for each output O of T, in output order:
        let F = owner(O)
        if F in {null, "", "Unknown", "OpReturn"}: continue
        if addr[F].guide is already set: continue

        if T.spentCashes is empty:
            addr[F].guide = "Coinbase"
        else:
            addr[F].guide = owner(T.spentCashes[0])
```

This procedure guarantees that the Guide is assigned exactly once per FID and only from on-chain data available at the time of the birth event.

## Examples

### Example 1: Guide via ordinary transaction

Address `A` sends 1 FCH to the previously unseen address `B`. The transaction `T` has:
- Inputs: `[cashOwnedBy(A), cashOwnedBy(A)]`
- Outputs: `[1 FCH → B, change → A]`

Because `B` has never appeared before, its birth event is in `T`. The first input of `T` is owned by `A`, therefore:

```
Guide(B) = A
```

Address `A` is already known (has a prior Guide) and is not affected.

### Example 2: Guide via coinbase

Miner address `M` receives a block reward from a coinbase transaction `T`. `T.spentCashes` is empty. If `M` is new, then:

```
Guide(M) = "Coinbase"
```

If `M` already has a Guide from a prior block, its Guide is unchanged.

### Example 3: Self-guide via change

A newly derived address `C` is the change output of a transaction whose first input is also owned by `C` (e.g., a wallet that spent an old UTXO of the same logical wallet but returned change to a fresh derivation). Then:

```
Guide(C) = C
```

This is permitted by Rule 4.

### Example 4: Guide DAG

Over three transactions in block order:
1. Coinbase to `M`.
2. `M` pays new address `A`.
3. `A` pays new address `B` and new address `C` in the same transaction.

Resulting Guide edges:

```
Coinbase → M
M        → A
A        → B
A        → C
```

### Example 5: Mixed owners ignored

A transaction `T` has spent inputs `[cashOwnedBy("OpReturn"), cashOwnedBy(X)]`. Because the order of `spentCashes` is the transaction input order, `spentCashes[0]` is `"OpReturn"`. In practice, `OpReturn` outputs cannot be spent, so this case does not occur on a valid chain. Implementations SHOULD still defensively skip any owner that equals `"OpReturn"` or `"Unknown"` and use the next eligible input — but because this scenario is unreachable on-chain, the behavior is not normative.

## Versioning

|Version|Date|Changes|
|---|---|---|
|1|2026-04-17|Initial version. Defines the Guide attribution as "owner of the first input of the first receiving transaction, or Coinbase".|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FVEP0 FVEP|General rules for all FVEP protocols|
|FVEP1 Entity|Defines Subject and Object; Guide relates two Subjects (FIDs)|
|FVEP2 ID|Defines FID; Guide values are FIDs (or the `Coinbase` constant)|
|FVEP4 Time|`birthHeight` uses Freeverse Time (block height)|
|FVEP6 CoinDay|CDD accumulated along Guide edges MAY be used for reputation propagation|

## Reference Implementation

- `FchParser/src/main/java/writeEs/BlockMaker.java` — `makeAddress` method assigns `Freer.guide` once, using the owner of the first spent Cash of the transaction, or `COINBASE` when the transaction has no inputs.
- `FC-JDK/src/main/java/data/fchData/Freer.java` — `guide` field on the address entity, with getter/setter.
- `FchParser/src/main/java/startFCH/StartFCH.java` — defines the `COINBASE = "Coinbase"` constant.
