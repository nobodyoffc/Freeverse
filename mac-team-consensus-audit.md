# Audit the Mac client for the team / consensus defects found in the Android client

The Android client (Freer, `~/AndroidStudioProjects/Freer`) was just audited and fixed for a
set of FEIP-18 Team defects. Most were client-side logic errors, not platform quirks, so they
very likely exist in this codebase too. Audit this project for each item below.

**How to work:** for every item, first find the equivalent code here and decide whether the
defect actually exists — the class names below are Android's and will differ. Report each as
**applies / does not apply / not implemented here**, with the file and line. Do not assume a
defect is present because it was present on Android, and do not assume it is absent because
the code looks different. Fix the ones that apply. Verify against the protocol sources rather
than against my description:

- Spec: `~/Desktop/Freeverse/Protocols/FEIP/FEIP18V1_Team.md`
- Indexer: `~/Desktop/Freeverse/FEIP/FeipParser/src/main/java/organize/OrganizationParser.java`
  (`makeTeam` validates the op, `parseTeam` applies it)

---

## Protocol invariants that drive most of this

Verified against the live parser. Get these wrong and the rest follows.

1. **`home` is replaced wholesale on update**, not merged:
   `if (teamHist.getHome() != null) team.setHome(teamHist.getHome());`
   Omitting `home` from an update preserves what is stored. Carving a *partial* map **erases every
   key not in it.**

2. **Consensus agreement is a negative set.** `Team.notAgreeMembers` is refilled with every
   non-owner member whenever an update carves a `consensusId` that *differs* from the stored one.
   Same id = no reset, so name/desc/home-only edits do not force re-signing. `agree consensus`
   only *removes* the signer. There is no `agreedMembers` list and no per-member signature record;
   "who signed version X" is reconstructible only from `TeamHistory`.

3. **The owner is never in `notAgreeMembers`** and can never send `agree consensus` — their
   update *is* their signature. The parser rejects the op from anyone not currently in the set.

4. **`consensusId` is a content address in this client family**, though the protocol treats it as
   an opaque string: `consensusId = sha256x2(documentBytes)`, doubling as the raw HAT id. The
   document is uploaded unencrypted and permanent to the team's DISK, and the DISK SID is
   published in plaintext as `home["DISK@No1_NrC7"] = "(sid)<sid>"` so any peer can resolve and
   verify it. The chain enforces none of this — the client must.

5. **Exact `confirm` strings** (byte-identical, from `FeipConstants`):
   - join: `I join the team and agree with the team consensus.`
   - transfer: `I transfer the team to the transferee.`
   - take over: `I take over the team and agree with the team consensus.`
   - agree consensus: `I agree with the new consensus.`

---

## A. Data-loss and correctness — check these first

**A1. Team update wipes the DISK entry from `home`.**
Android's update screen built a `home` map containing only `DOCK@No1_NrC7`. Because the parser
replaces `home` wholesale (invariant 1), every update erased `DISK@No1_NrC7` — after which
*no member could ever fetch the team's consensus document*, permanently.
→ Build the update's `home` by **merging** over the team's stored `home`, never by constructing a
fresh map. Also check for hardcoded `"DOCK@No1_NrC7"` string literals instead of the constants,
and for raw values stored without normalising to `(sid)<id>`.

**A2. Update accepted a typed `consensusId` with no document behind it.**
The create screen uploaded the document and carved the hash the upload returned. The update
screen took a free-text id and carved it directly, so a team's consensus could point at bytes
nobody can fetch.
→ On update, the carved id must be proven: either the hash returned by uploading local bytes, or
an id confirmed present on the target DISK, or bytes downloaded and hash-verified from another
DISK. Never carve an unverified string.

**A3. Changing the team's DISK stranded the old consensus document.**
Members who have not re-signed still need to read what they originally agreed to.
→ When the DISK changes, copy the *current* consensus document to the new DISK before carving.

**A4. Ordering when placing a document on a DISK.**
Implement as: **already on the target DISK → local bytes → pull from the fallback DISK.** Checking
the target first is not just an optimisation — carving is a *payment*, so re-uploading a document
that is already there charges the owner for nothing.
Two edge cases Android got wrong at first:
  - A "same source and target DISK" short-circuit that returns success **without verifying the
    document is actually there** — that reintroduces A2.
  - A **blank old DISK** (the state A1 leaves teams in) must be treated as a plain "not found",
    not an error. Do not warn the owner that the old document could not be moved when the team
    never had a DISK to move it from — there was nothing to lose, and this screen is how they
    repair such a team.

**A5. `localNames` type mismatch.**
Android's `TeamOpData` / `TeamHistory` declared `String[]` while `Team`, the server-side FC-JDK
classes and the parser all use `Map<String, String>`. The indexer writes a map into a field the
client reads as an array.
→ Check every Team-related DTO field against `~/Desktop/Freeverse/FC-JDK/src/main/java/data/feipData/`.

---

## B. Missing capability — the member re-signing flow

**B1. Nothing ever read `notAgreeMembers`, and `agree consensus` was never sent.**
The op builder existed with zero call sites. So an owner could change the consensus, every member
was placed in `notAgreeMembers` by the indexer, and there was **no way for any member to see this
or get out of it**.
→ Implement the full loop:
  - **Detect from the chain**, not from a P2P message: a member owes a signature exactly while the
    team lists them in `notAgreeMembers`. Do this wherever a freshly-fetched team is stored, so it
    fires on every refresh path, and so it also **self-clears** when the signature came from
    another device or the member was dismissed. Deliberately chain-driven: it survives being
    offline, reinstalling, or a lost message.
  - **Capture the previous `consensusId` from the cached team immediately before overwriting it.**
    The chain only ever holds the current one, so this is the *only* moment the old id exists.
    Store it on the pending item so the member can read the old and new documents side by side.
    It is legitimately absent when the device never saw the previous version — handle that.
  - **Offer three actions**: sign (`agree consensus`), postpone, or leave the team (`leave` with
    `tids`). Postponed items should stay reachable but stop nagging.
  - **Re-read the id from the freshly-synced team when signing**, not from the stored notification —
    the parser rejects an `agree consensus` whose id differs from the team's current one, and a
    further change may have landed in between.
  - Warn the owner before publishing a consensus change that it obliges every member to re-sign.
  - Consider showing a "has not agreed" marker per member in the member list.

---

## C. Correctness of the consensus document UI

**C1. "View consensus" showed the wrong document.**
Android's update screen viewed `team.getConsensusId()` — the team's *stored* consensus — while the
user was looking at, and asking about, the consensus **input field**. After picking or editing a
document the field held a new id, so the viewer went hunting for the old on-chain id and failed
with "failed to download" for a document sitting right there in the data list.
→ View what the field names; fall back to the team's stored consensus only when the field is empty.

**C2. Viewing should try the local store first, then each DISK.**
Order: local data store → each candidate DISK in turn. On the update screen the candidates are
**both** the team's current DISK and the SID typed into the form, because a team mid-move has its
document on one side or the other. Verify downloaded bytes against the id before displaying.
Read the local store file — never delete it; delete only temp downloads.

**C3. Dead-end when the document is not local.**
Requiring local bytes and otherwise refusing is wrong: a consensus is uploaded and may well have
been pruned locally. Fall back to the DISKs before asking the user for a file.

**C4. Error messages that describe a step that never ran.**
"Failed to download" when there was no DISK to try at all is misleading. Say *why*: not on this
device, and no DISK to read it from.

---

## D. Worthwhile improvements (not defects)

**D1. Offer an editable consensus template when creating a team.** Most of a consensus asks the
same questions every time; an owner given a starting point writes a usable one. Seed a template
document, open it in the text editor, and put the resulting id in the field.
*If your editor re-hashes on save (it should — the id is the content hash), make sure it reports
the DID it saved under. Android's returned a bare success code, so the caller kept pointing at the
unedited template.*

**D2. Filter invitees against current membership before carving.** Android sent the raw picked
list. The indexer silently skips the owner and existing members — but the transaction is still
**paid for**, and it re-indexes the team anyway, bumping `lastTxId`/`lastTime`/`lastHeight` for a
write that changed nothing. Worse, the "you've been invited" notification went to existing members
too, who then landed in a join screen with nothing to join.
→ Partition into: already members/owner (drop entirely), already invited but not joined (drop from
the transaction — re-adding is a set operation — but still notify, since a resend is the only way
to nudge a missed invitation), genuinely new (carve and notify). Carve nothing if nothing is new.
Read membership **from the chain** for this, not from a possibly-stale cache: wrongly excluding
someone is worse than a wasted fee. Degrade to no filtering if that read fails.

**D3. Generate the team symkey at creation.** Rooms already do; teams deferred it to first use,
stalling the owner behind a "creating symkey…" gate on opening their own team chat. It is free at
creation — the only member is the owner, so there is nobody to distribute to and no network call.
*Ordering matters:* the owner check reads the team through the cache, and an unconfirmed team does
not exist on chain to fall back to, so this must run **after** the pending team is cached or it
fails silently. Late joiners are unaffected — they fetch the key by request.

**D4. Make dialog titles and content selectable** so users can copy error text. If you sweep this
broadly, exclude buttons, checkboxes, editable fields and anything already clickable — making a
view selectable turns it focusable and long-clickable and installs a movement method, which
swallows taps and would break dialog controls.

---

## E. Probably Android-only — check whether an equivalent exists

- **String-resource newline collapsing.** Android's `aapt` collapses literal newlines in string
  resources into single spaces, which silently flattened a multi-line consensus template into one
  paragraph. Any templated multi-line text on your platform should be verified *in the built
  artifact*, not in the source.

---

## Known-good, for reference

These were verified correct on Android and are worth confirming rather than changing:

- Room member addition already rejects duplicates and only re-shares room info when something
  actually changed.
- The `confirm` string literals matched `FeipConstants` byte-for-byte.
- Upload honoured an explicit target DISK SID rather than falling back to the user's default.

## Also note

The parser itself had four defects, now fixed in `OrganizationParser.java`: a missing
`return` causing `case "join"` to fall through into `case "leave"`; a dead second
`notAgreeMembers` pass in `case UPDATE` that would have set the set to *all* members including
the owner; `makeTeam` NPEing instead of returning null when `confirm` was absent; and
`join` / `agree consensus` not requiring the `consensusId` that `parseTeam` dereferences.
Only relevant if this project carries its own copy of the parser.

Note: the `FeipParser` maven module does not currently compile on this machine — roughly 73
pre-existing errors in `*Rollbacker.java` (`EsUtils.scanHitsAboveHeight` missing from its FC-JDK
dependency), unrelated to any of the above. Compare error counts before and after rather than
expecting a green build.
