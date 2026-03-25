# FEIP18V1_Team

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
|Title|Team|
|Type|FEIP|
|SN|18|
|Version|1|
|Category|Organize|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-23|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

The **Team** protocol defines **owner-managed** on-chain organizations: a stable **`tid`** (team id = **create** txid), **`owner`**, **`consensusId`** (binding members to a shared rule set), **`members`**, **`managers`** (who may **invite** / **withdraw invitation**), optional **waiters** / **accounts** / **localNames** / **home** / **desc**, lifecycle **`active`**, and reputation fields **`tCdd`** / **`tRate`**. Operations cover **create**, **update**, **join**, **leave**, **transfer** / **take over**, **disband**, **agree consensus**, **invite**, **withdraw invitation**, **dismiss**, **appoint**, **cancel appointment**, and **rate**. Several ops require exact **`confirm`** sentences.

## Motivation

- **Governance**: Clear **owner** and **managers** vs **members**; consensus changes tracked via **`notAgreeMembers`** until **agree consensus**.
- **Membership workflow**: **Invite** → **join** (with consensus acknowledgment); **leave** / **dismiss** / **exMembers** history.
- **Succession**: **transfer** sets **`transferee`**; **take over** completes handoff when the transferee signs.
- **Reputation**: **rate** (0–5) weighted by **CDD** into **`tRate`**.

## Specification

### Team entity (indexed)

|Field|Description|
|---|---|
|`id`, `tid`|Team id = **create** transaction id (same value in reference).|
|`owner`|Current owner FID (creator on **create**; may change on **take over**).|
|`stdName`|Canonical team name.|
|`localNames`|Optional map of locale → display name.|
|`consensusId`|Identifier of the active team consensus / rules (opaque string; compared for equality).|
|`desc`|Optional description.|
|`home`|Optional `Map<String,String>` links (cf. [FEIP9](FEIP9V1_Home.md)).|
|`waiters`, `accounts`|Optional FID lists from **create** / **update**.|
|`members`|Current member FIDs.|
|`memberNum`|`members.length` (maintained explicitly).|
|`exMembers`|FIDs who left or were dismissed.|
|`managers`|FIDs who may **invite** / **withdraw invitation** (initially creator on **create**).|
|`invitees`|FIDs invited but not yet joined.|
|`notAgreeMembers`|Members who must still **agree consensus** after a consensus change.|
|`transferee`|Pending new owner after **transfer** until **take over**.|
|`birthTime`, `birthHeight`, `lastTxId`, `lastTime`, `lastHeight`|Lifecycle.|
|`tCdd`, `tRate`|Cumulative CDD weight and weighted average rating from **rate** ops.|
|`active`|`false` after **disband**.|

### Global CDD gate (reference)

[OrganizationParser.makeTeam](../../FEIP/FeipParser/src/main/java/organize/OrganizationParser.java) returns **null** when `height > CddCheckHeight` and `cdd < CddRequired` **before** dispatching the op — i.e. **all** team ops require minimum CDD above the threshold (except inner **rate** also checks CDD separately). Conforming indexers SHOULD match this.

### `op` strings (normative)

Use **FeipOp** values (case as serialized in JSON, typically lowercase where single token):

| `op` value | Enum / notes |
|---|---|
| `create` | `CREATE` |
| `update` | `UPDATE` |
| `join` | `JOIN` |
| `leave` | `LEAVE` |
| `transfer` | `TRANSFER` |
| `take over` | `TAKE_OVER` (space, not underscore) |
| `disband` | `DISBAND` |
| `agree consensus` | `AGREE_CONSENSUS` |
| `invite` | `INVITE` |
| `withdraw invitation` | `WITHDRAW_INVITATION` |
| `dismiss` | `DISMISS` |
| `appoint` | `APPOINT` |
| `cancel appointment` | `CANCEL_APPOINTMENT` |
| `rate` | `RATE` |

[TeamOpData.toLowerCase()](../../FC-JDK/src/main/java/data/feipData/TeamOpData.java) uses `FeipOp.getValue().toLowerCase()` — multi-word ops keep spaces (e.g. **`take over`**, **`agree consensus`**).

### Required `confirm` strings (exact)

| Op | `confirm` MUST equal |
|---|---|
| `join` | `I join the team and agree with the team consensus.` |
| `transfer` | `I transfer the team to the transferee.` |
| `take over` | `I take over the team and agree with the team consensus.` |
| `agree consensus` | `I agree with the new consensus.` |

### Operations (consensus summary)

#### 1. create

- **Fields:** `stdName` (required), `consensusId` (required), optional `localNames`, `waiters`, `accounts`, `desc`, `home`. **`tid` MUST be absent** on create.
- **Effect:** New team; `id` = `tid` = txid; `owner` = signer; `members` = [signer]; `managers` = [signer]; `active` = true; CDD checks (global + duplicate in reference).

#### 2. update

- **Fields:** `tid`, `stdName`, `consensusId` required in **makeTeam**; optional other profile fields.
- **Effect:** Only **`owner`** may update. If **`consensusId`** in the op **differs** from stored, update consensus and set **`notAgreeMembers`** (reference: non-owner members, with a second pass that may set all members in edge cases — see code).

#### 3. join

- **Fields:** `tid`, **`confirm`** (exact), `consensusId` MUST **match** team’s current consensus.
- **Effect:** Signer must appear in **`invitees`**; move signer from invitees to **`members`**; adjust **`exMembers`** if re-joining.

#### 4. leave

- **Fields:** `tids` (array of team ids).
- **Effect:** For each team: skip if inactive; **owner cannot leave** via this op; remove signer from **`members`**, add to **`exMembers`**, strip from **`managers`** if present; bulk index.

#### 5. transfer

- **Fields:** `tid`, `transferee`, **`confirm`** (exact).
- **Effect:** Signer **must be owner**, or reference allows non-owner path using signer’s **Freer** `master` field (see implementation notes). Sets **`transferee`** (cleared if transferee equals current owner).

#### 6. take over

- **Fields:** `tid`, **`confirm`** (exact); optional `consensusId` in op — if present MUST equal team’s `consensusId`.
- **Effect:** Team must be **active**, **`transferee`** non-null, signer must equal **`transferee`**; signer becomes **`owner`**, **`managers`** = [signer], **`transferee`** cleared, signer added to **`members`**.

#### 7. disband

- **Fields:** `tids`.
- **Effect:** For each id: only **owner** may disband; skip inactive; set **`active`** false; bulk index; **News** entry in reference.

#### 8. agree consensus

- **Fields:** `tid`, **`confirm`** (exact); `consensusId` in op MUST equal team’s current **`consensusId`**.
- **Effect:** Signer must be in **`notAgreeMembers`**; remove signer from that set.

#### 9. invite

- **Fields:** `tid`, `list` (non-empty FID array).
- **Effect:** Signer must be in **`managers`**; add FIDs to **`invitees`** (skip owner and existing members).

#### 10. withdraw invitation

- **Fields:** `tid`, `list`.
- **Effect:** Signer must be in **`managers`**; remove listed FIDs from **`invitees`**.

#### 11. dismiss

- **Fields:** `tid`, `list`.
- **Effect:** Signer must be in **`managers`**; remove listed **members** (not owner) from **`members`**, add to **`exMembers`**, remove from **`managers`** if listed.

#### 12. appoint

- **Fields:** `tid`, `list`.
- **Effect:** **Owner** only; each FID in `list` that is already a **member** (not owner) is added to **`managers`**.

#### 13. cancel appointment

- **Fields:** `tid`, `list`.
- **Effect:** **Owner** only; remove listed FIDs from **`managers`** (not owner).

#### 14. rate

- **Fields:** `tid`, `rate` integer **0–5** inclusive.
- **Effect:** Signer **must not** be **owner**; requires sufficient **CDD** on the op; updates **`tRate`** as CDD-weighted average with **`tCdd`**.

### OP_RETURN envelope

```json
{
  "type": "FEIP",
  "sn": "18",
  "ver": "1",
  "name": "Team",
  "data": { }
}
```

### TeamHistory (audit)

[TeamHistory](../../FC-JDK/src/main/java/data/feipData/TeamHistory.java) records block context, `signer`, `cdd` (e.g. for **rate**), `op`, `tid` / `tids`, and op-specific fields (`stdName`, `consensusId`, `transferee`, `list`, `rate`, etc.). **`confirm` is validated in `makeTeam` but not stored** on `TeamHistory`.

## Examples

### Example — create

```json
{
  "type": "FEIP",
  "sn": "18",
  "ver": "1",
  "name": "Team",
  "data": {
    "op": "create",
    "stdName": "Core dev",
    "consensusId": "feip:team-rules:v1",
    "desc": "Protocol maintainers"
  }
}
```

### Example — invite and join

**Invite** (manager signs):

```json
{
  "type": "FEIP",
  "sn": "18",
  "ver": "1",
  "name": "Team",
  "data": {
    "op": "invite",
    "tid": "<create_txid>",
    "list": ["FID_INVITED..."]
  }
}
```

**Join** (invitee signs):

```json
{
  "type": "FEIP",
  "sn": "18",
  "ver": "1",
  "name": "Team",
  "data": {
    "op": "join",
    "tid": "<create_txid>",
    "consensusId": "feip:team-rules:v1",
    "confirm": "I join the team and agree with the team consensus."
  }
}
```

### Example — transfer / take over

**Transfer** (owner):

```json
{
  "op": "transfer",
  "tid": "<create_txid>",
  "transferee": "FID_NEW_OWNER...",
  "confirm": "I transfer the team to the transferee."
}
```

**Take over** (transferee):

```json
{
  "op": "take over",
  "tid": "<create_txid>",
  "confirm": "I take over the team and agree with the team consensus."
}
```

## Versioning

|Version|Date|Summary|
|---|---|---|
|1|2026-03-23|Initial spec text aligned with `Feip.TEAM` (`18`/`1`) and `OrganizationParser`.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FEIP0|Envelope, parsing, CDD.|
|FEIP6 Master|Referenced for non-owner **transfer** signer check (`Freer.master`).|
|FEIP19 Square|**Unmanaged** squares vs **managed** teams ([FEIP19V4_Square](FEIP19V4_Square.md)).|

## Reference Implementation

|Component|Location|
|---|---|
|`Team`| [FC-JDK/src/main/java/data/feipData/Team.java](../../FC-JDK/src/main/java/data/feipData/Team.java) |
|`TeamOpData`| [FC-JDK/src/main/java/data/feipData/TeamOpData.java](../../FC-JDK/src/main/java/data/feipData/TeamOpData.java) |
|`TeamHistory`| [FC-JDK/src/main/java/data/feipData/TeamHistory.java](../../FC-JDK/src/main/java/data/feipData/TeamHistory.java) |
|`OrganizationParser.makeTeam` / `parseTeam`| [FEIP/FeipParser/src/main/java/organize/OrganizationParser.java](../../FEIP/FeipParser/src/main/java/organize/OrganizationParser.java) |
|`Feip.TEAM`| [FC-JDK/src/main/java/data/feipData/Feip.java](../../FC-JDK/src/main/java/data/feipData/Feip.java) |

