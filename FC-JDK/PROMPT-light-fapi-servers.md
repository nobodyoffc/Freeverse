# Task: light FAPI servers, and DISK/DOCK off Elasticsearch

First written 2026-09-30 at the end of a Freer (Android) session. Rewritten the same day
after a design discussion with the user in this repo. **The decisions below are settled;
build them.** Anything marked *open* still needs the user.

## The need

Today every FAPI server is a full node in one process: FUDP, the components,
Elasticsearch with the parsed chain, and billing. A runner should be able to run a
service as a **light server** on hardware suited to it:

| Light server | What it needs most |
|---|---|
| CALL | bandwidth, low latency, a good network position (e.g. Hong Kong for China) |
| DISK | storage |
| DOCK | storage and an index for inbox queries |
| ROAD + MAP | many live connections; always together |

A light server keeps no chain. It reads what it needs from an **upstream BASE**, a full
FAPI server set in its config.

## Decisions

### D1. A light server is an ordinary FAPI instance, and its economy follows its SID

- A light server is a FAPI instance that runs a subset of the components and has no
  local chain stack. There is no separate "light mode": it follows from the config
  (upstream set, no ES, only non-BASE components).
- **Its economy is tied to its SID.** One process has one SID, one `FapiBalanceManager`
  and one economy, whatever components it runs. This is already how it works.
- Different instances have different SIDs, and their economies are independent. Each
  has its own:
  - FID and dealer;
  - pricing and via shares;
  - balances and stakeholder settlement.
- There is no shared balance and no ledger across instances. A user tops up each SID
  separately.
- **Each light server publishes its own FEIP Service,** listing only its components:
  - `[CALL]`, `[DISK]` or `[DOCK]`;
  - `[ROAD, MAP]` together, never ROAD alone. ROAD delivers only to devices in its own
    co-hosted MAP, and the apps offer a ROAD only if it also runs MAP.

### D2. The upstream

- **The local config decides which upstream to use.** The server is never pointed at an
  upstream it discovered on its own. It needs the upstream even to read its own
  Service record.
- **Declaring it on chain is optional:** a light server *may* list its upstream's SID in
  its Service's `services` field. Nothing requires or reads that entry.
- **A light server is a paying client of its upstream,** with its own balance there that
  tops itself up automatically. `DiskSyncManager.requestRechargeToMinBalance` already
  does this for a dealer balance. The upstream does not have to belong to the same
  runner.
- **Trust:** the upstream says who paid. If it lies, only that server's revenue is at
  risk, which is acceptable. A second upstream that cross-checks large credits is an
  optional hardening, not part of this task.
- **When the upstream is down:**
  - balances are local, so the server keeps serving;
  - credits catch up by height once the upstream is back;
  - `creditLimit` covers new payers meanwhile;
  - DOCK's best height freezes, so items expire late, which is harmless.
- **Pending top-ups:** show them from `base.unconfirmedCashes` so a user who has just paid
  is not refused. Credit only confirmed cashes.

### D3. DISK and DOCK move off Elasticsearch, on every server

- Their metadata moves to **LevelDB**, on full servers too. There is one implementation,
  not an ES one and a KV one.
- **One-time migration:** on first start, if the LevelDB store is empty and an ES client
  is configured, scroll the DISK index (`{sid}_data`) and the DOCK index into LevelDB,
  then write a marker. After that DISK and DOCK never touch ES, and their ES code is
  removed.
- `DiskComponent`'s "resolve a service declared on chain from the local index" (around
  line 205, used for sync sources) goes through the chain source (D5) instead.
- A full server still keeps ES for BASE and the chain.

### D4. Full FCDSL over LevelDB, as a reusable engine

- **Every FCDSL operator is supported:**
  - `ids`, `query` / `filter` / `except` (each an `FcQuery`);
  - within an `FcQuery`: `terms`, `equals`, `unequals`, `range`, `part`, `match`,
    `exists`, `unexists`;
  - `sort`, `size`, `after`, `fields` / `noFields`.
- **Not only DISK and DOCK will use it.** Other tasks will run FCDSL over other LevelDB
  instances; the first candidates are the stores behind `db.LocalDB<T>` /
  `db.LevelDB<T>` used by `managers.Manager` subclasses. So:
  - The engine lives in its own package, e.g. `db.fcdsl`, with no DISK or DOCK types in
    it.
  - **Field schema:** name → type (LONG, KEYWORD, KEYWORD_ARRAY, …) plus a getter.
    Ranges compare numbers as numbers. `terms` or `equals` on an array field match any
    element, as ES does.
  - **`FcdslMatcher`:** a pure test of a whole Fcdsl against one object. It knows
    nothing about storage.
  - **Indexed collection:** a collection on a LevelDB `DB` under a key prefix, with
    declared indexes. An index may be composite: equality on a (possibly array) field,
    then a sort field. `put` and `remove` keep the indexes in step through one
    `WriteBatch`.
  - **Planner:** picks an access path, then applies the rest of the query as a filter:
    - `ids` → direct lookups;
    - an equality on the index's first field → that part of the index;
    - a range on the sort field → start and end positions;
    - a sort that follows an index is read straight from it, otherwise the matching
      set is sorted in memory, up to a cap.
    - An `id` tie-break is always appended to the sort, as today.
  - The matcher and the planner talk to the store through a small interface for an
    ordered key-value store (get, seek, iterate forward or back, write batch). Keep
    them free of dependencies Android lacks, so FC-AJDK can port them.
- **`match`:** ES's full-text match has no exact equivalent here. Define it as
  case-insensitive equality of tokens, and write that into the spec.
- **`total`:** return it when it is cheap:
  - no filter beyond the index: count one recipient's range, or keep a DISK counter;
  - otherwise leave it out, and make it optional in the spec.

  No client reads it today. `DiskSyncManager` and `DockFetchScheduler` don't.

#### Never a short page unless the data really ends

The existing clients stop on a short page:
- `DockFetchScheduler` (Freer app): `if (items.size() < FETCH_PAGE_SIZE) hasMore = false;`
- `DiskSyncManager`: stops on an empty page.

So the server must **never** stop scanning after some number of rows and return a partial
page with a cursor. If a query would exceed the per-request limit on rows examined, it
fails with an error instead.
- The limit is configurable per server; default about 100 000 examined rows.
- The same cap applies to sorting in memory.

#### Error codes

`FapiCode` already has everything:

| Situation | Code |
|---|---|
| Unknown field, bad range value, malformed `after` cursor | **400 BAD_REQUEST** (ES used to return an empty result instead) |
| Valid FCDSL this server doesn't implement (a newer operator or `ver`) | **501 NOT_IMPLEMENTED** |
| Examined-row limit exceeded | **501 NOT_IMPLEMENTED**, with a message naming the fix, e.g. "add a range on createTime" |

#### Saved cursors must survive the migration

`DockFetchScheduler` persists its cursors (`saveCursors`). An app's stored cursor must
resume at the same place after ES → LevelDB. So a cursor is exactly the ES sort values
as strings:
- DOCK: `[createTime, id]`
- DISK: `[since, id]`

A cursor for a different sort holds that sort's values plus `id`.

### D5. Chain source seam

- Add a `ChainSource` interface with two implementations:
  - ES, for full servers: today's code, moved behind the interface.
  - upstream, for light servers, over `fapi.client.FapiClient`.
- **Everything a light server needs is already in BASE,** so no new upstream API:

  | Need | BASE API |
  |---|---|
  | cashes paid to my FID since height H | `base.search` on cash |
  | the via of each top-up (in its tx's OpReturn) | `base.getByIds` on opreturn |
  | best height and block ID | `base.chainInfo` |
  | pending top-ups | `base.unconfirmedCashes` |
  | my own Service record | `base.getByIds` / `base.search` on service |
  | settlement payouts | `base.cashValid`, `base.broadcastTx` |

- **Where the seam goes:**
  - `FapiServer` (around line 267) already passes `cashQueryFunction` and
    `viaQueryFunction` into `RechargeTask`.
  - `RechargeScanner` still holds `esClient` directly, for best height
    (`queryBestBlockFromEs`) and OpReturns (`queryViaFromOpReturns`). Both move behind
    `ChainSource`.
- DOCK takes its best height from the balance manager (`DockComponent.java:371`), which
  the recharge path updates. It therefore works once the upstream supplies the height.

### D6. Packaging

- One jar and one `StartFapiServer`. A light server differs only in its config:
  - `upstream`: a FUDP URL, optionally with an SID;
  - the store directory;
  - components and limits.
- The server must start without ES when every component it runs is one of CALL, DISK,
  DOCK, ROAD or MAP.
- A full server running every component keeps working as today, apart from DISK and
  DOCK now using LevelDB (D3).

## Verified facts (2026-09-30)

### What each component needs from the chain

| Component | Needs | Doesn't need |
|---|---|---|
| CALL | top-ups, best height | homes: the apps enforce home.CALL (Freer `docs/VOICE_SPEC.md`, Decision 11) |
| ROAD | top-ups, best height | homes: `targetRoad` comes from the sender (`RoadComponent.java:235`) |
| MAP | top-ups, best height | chain pubkeys: it takes the peer's key from the FUDP connection (`MapComponent.java:208`) |
| DOCK | top-ups, best height | homes: `targetDockUrl` comes from the sender (`DockComponent.java:342`) |
| DISK | top-ups | anything else: peer sync already uses `FapiClient` |

Every component also needs the via of each top-up, its own Service record, and its UTXOs
plus broadcast for settlement. `map.register` is billed; only PING and PONG are free.

### The FCDSL that clients send today

This is the minimum that must work, and it is all served by an index:

| Caller | API | FCDSL |
|---|---|---|
| `DockFetchScheduler` (Freer) | dock.fetch | sort createTime asc, id asc; size; after |
| `NewcomerBoard` (Freer) | dock.fetch | sort createTime desc; size; range `createTime > since` |
| Team, Room and Square handlers (Freer) | dock.fetch | size 50 |
| `DiskSyncManager` | disk.list | sort since asc, id asc; size; after |
| `StartFapiClient` / ApipClient CLIs | disk.list, dock.list | size |

The apps never call `disk.list` or `dock.list`.

### A bug to fix along the way

`DockComponent.handleFetch` builds its own ES query and ignores `fcdsl.query`. So
NewcomerBoard's `createTime > since` range has never reached the server; the board works
only because it sorts newest first and filters on the client. **The new store applies the
range. The user approved this.**

### Other points

- `dock.fetch` with several recipients must return an item addressed to more than one of
  them only once.
- `dock.fetch` has no permission check: anyone may fetch any recipient's items, which are
  encrypted. The newcomer "nobody" board relies on this. Keep it as it is.

## Planned LevelDB layout (a sketch; the generic engine decides the real key encoding)

Numbers are big-endian, so keys sort in number order.

- **DOCK**
  - `item:<id>` → the item
  - `rcpt:<recipient>:<createTime>:<id>`: the composite index on recipients × createTime.
    `fetch` over several recipients merges one iterator per recipient and skips repeated
    ids.
  - `exp:<expireHeight>:<id>`: the expiry sweep. Reads also skip expired items, so a
    stale height is harmless.
- **DISK**
  - `did:<did>` → the item
  - `since:<since>:<did>`, `exp:<expire>:<did>`, `size:<size>:<did>`: every sortable
    field has an index, so every DISK sort is read from one.
- A sort on any other DOCK field loads that recipient's items and sorts them in memory,
  up to the cap. The newcomer board is the one large recipient, and it sorts by
  createTime, which is the index.

## Build order (on a branch)

1. **Done** on branch `light-fapi-fcdsl-engine`. **`db.fcdsl` engine:** field schema, matcher,
   indexed collection, planner.
   - Classes: `FieldSchema`, `FcdslQuery` (compile + match), `IndexDef`, `IndexedCollection`
     (planner and paths), `KeyCodec`, `SortedKv` / `LevelDbKv`, `FcdslProjection`,
     `FcdslException`.
   - LevelDB 0.12 cannot iterate backwards (`prev()` throws), so a descending index stores
     complemented keys; declare each index in the direction queries sort by. DOCK needs both
     `createTime asc` (DockFetchScheduler) and `desc` (NewcomerBoard).
   - Tests in `db.fcdsl.IndexedCollectionTest`, including a randomized check of every path
     and full paging against a brute-force sort.
   - Unit tests over a temporary LevelDB, for every operator.
   - Paging with `after`, forward and back.
   - Recipients as an array field, and several recipients with ids removed if repeated.
   - The examined-row limit returns 501, never a short page.
   - 400 for unknown fields.
2. **Done** on the same branch. **`ChainSource`:** move the ES code behind it and add the
   upstream implementation. `FapiServer` stops holding `esClient` directly.
   - `fapi.chain`: `ChainSource`, `EsChainSource`, `UpstreamChainSource`, `ChainSources`
     (ES if configured, else a `FAPI` / `FAPI_No1_NrC7` client), `Via`,
     `ChainUnavailableException`.
   - Wired into `FapiServer`: best height, recharge cashes and via, valid cashes for the
     payment-required helper; and into `DiskComponent.resolveServiceOnChain`.
   - Fixed on the way: the cash scan read one page of 1000 and moved on, so cashes cut off
     at the page's last height were never credited. Both sources now read every page. A
     failed read throws instead of looking like "no cashes", so the scan height can't
     advance past cashes nobody saw.
   - `RechargeScanner` is dead code: nothing constructs it. Only its nested `CashInfo` is
     used. Left in place.
   - Settlement (`SettleTask`) only records distributions; it broadcasts nothing, so the
     chain source needs no broadcast.
   - `EsChainSource` has not been run against a live ES. `UpstreamChainSourceTest` checks
     the upstream against a fake BASE built on `db.fcdsl`.
   - Pending top-ups (`base.unconfirmedCashes`, D2) are not wired yet.
3. **Done** on the same branch. `fapi.components.dock.DockStore` (LevelDB at
   `<dbDir>/<mainFid>_<sid>_fapi_dock`), `EsDockPages` (scroll-API migration).
   `DockComponent` no longer needs ES. Tests: `DockStoreTest`, `DockComponentTest`.
   - **Cursor shape:** DOCK always appended `id asc` to the sort, even after
     DockFetchScheduler's `[createTime asc, id asc]`. So ES cursors had **three** values,
     and the apps saved those. The engine now keeps the declared sort for cursors and
     orders only up to the first id key; the old cursors resume in place.
   - **Fixed:** `dock.put` ids were `sha256(sender:recipients:millis)`, so two puts in one
     millisecond collided and the second overwrote the first (53 of 200 lost in a tight
     loop). Random bytes are now part of the input; the id format is unchanged.
   - **Migration:** runs at startup until it completes, then is marked done. A server
     without ES marks it done at once. The old ES index is left in place for the runner to
     drop.
   - `total` is now null for `dock.fetch` / `dock.list` pages (no client reads it).
   - The Base64 data stays inside the item, as in ES, so a scan decodes the data too.
     Split it out if big DOCK items become common.

   The original plan for this step:
   **DOCK on the engine:**
   - the ES → LevelDB migration;
   - the NewcomerBoard range;
   - the cursor format unchanged; test that a cursor saved before the migration resumes
     correctly.
4. **Done** on the same branch. **DISK on the engine,** with its migration.
   `DiskSyncManager`'s sync must keep working.
   - `fapi.components.disk.DiskMetaStore` (LevelDB at
     `<dbDir>/<mainFid>_<sid>_fapi_disk_meta`); indexes on since (both directions), expire
     and size. The files stay where they were.
   - `FapiDiskHandler(storageRoot, DiskMetaStore)` replaces `(storageRoot, esClient,
     indexName)`. A null store keeps no metadata, as a null ES client did.
   - The ES sum aggregation for disk usage (sync limit, server menu) is now a running total:
     summed once at open, then kept on every write.
   - `disk.list` goes through the engine and applies `fields` / `noFields`. With no sort it
     is `since desc` as before, but the cursor now also carries the id (`[since, id]`). ES
     gave a one-value cursor for that default, which could skip files sharing a `since`.
   - The migration pager is now generic: `fapi.migrate.EsIndexPages<T>`, used by both DISK
     and DOCK.
   - Tests: `DiskMetaStoreTest`, and `DiskSyncOverStoreTest`, which syncs 230 files between
     two real servers over FUDP on localhost (three `disk.list` pages, `disk.get`
     downloads, then a second cycle that resumes from the saved cursor and finds nothing).
   - Unchanged, and still true: no DISK code deletes expired files.
5. **Done** on the same branch. **Config and startup without ES** for light servers.
   - **Start:** `StartFapiServer --light`. When the server's settings are first created,
     its one module is an upstream FAPI (`FAPI_No1_NrC7`) instead of NASA_RPC + ES. The
     existing FAPI account flow asks for the upstream and connects a `FapiClient` on its
     own ephemeral-port node. After that, the saved settings decide; the flag does nothing
     for a SID that already has settings.
   - **Light = no ES client.** `ServiceBootstrap.resolveComponentTypes` then:
     - drops BASE;
     - adds MAP when ROAD is there;
     - refuses to start when nothing runnable is left.

     A full server merges its components as before.
   - **Own Service:** `Settings.loadMyService` / `getMyService` read it through the
     upstream when there's neither APIP nor ES (by SID, or the dealer's services).
     `Settings.getUpstreamFapiClient()` gives that client.
   - **Paying the upstream:** the upstream bills the light server. `ChainSources` wraps the
     module client with `FapiClient.withSettings(settings)`, so it gets an
     `AutoRechargeManager` (on by default) paying from the dealer key.
   - **Tests:** `fapi.LightServerTest`.
     - The component rules.
     - A real upstream FAPI server whose stand-in BASE answers from `db.fcdsl`. A light
       server with no ES starts against it over FUDP and credits a top-up it only knows
       from the upstream, with the via share from the OpReturn.
   - **Not tested:** the interactive setup (the `--light` prompts, and `loadMyService`'s
     upstream branch). Publishing a new Service from a light server is step 6.
   - `fudp.LossyRequestResponseTest` failed once in three full runs (1 of 25 carves under
     simulated loss) and passed alone and on the rerun: flaky, not touched here.
6. **Done** on the same branch. **Publishing:** a light server's Service lists only its
   components; `[ROAD, MAP]` go together; `services` may list the upstream.
   - Publishing never needed APIP or ES: `FapiServer.publishService` / `updateService`
     print the FEIP OpReturn JSON for the owner to send in a TX. Step 5 made the "enter
     the SID you published" loop look it up through the upstream.
   - `fapi.LightService` holds the rule. `FapiServer.applyLightServiceRules` applies it on
     a light server, after input, on both publish and update:
     - drop BASE;
     - write bare names in full (`dock` → `DOCK@No1_NrC7`, the form the apps and
       `ComponentRegistry` match);
     - add MAP to ROAD;
     - warn when nothing runnable is left;
     - offer (not force) to add the upstream's SID to `services`.

     A full server's declaration is left alone.
   - The upstream's FEIP parser has to index a newly sent Service before the lookup
     finds it; until then the loop keeps asking.
   - Tests: `fapi.service.LightServicePublishTest`.

## Status after steps 1–6 (2026-09-30)

All six steps are on branch `light-fapi-fcdsl-engine`, not merged. The full regression set
above plus the new tests is 148 tests, all passing.

Still open:
- **Pending top-ups** (`base.unconfirmedCashes`, D2) are not shown or credited early.
- **Not run for real:** the one-time ES → LevelDB migrations of DISK and DOCK, and
  `EsChainSource`'s paging against a live ES.
- **Not tested:** the interactive paths (`--light` first-time setup, the upstream branch
  of `loadMyService`, the publish prompts).
- **Checked with the Android app?** No. The code keeps the wire contracts (cursor shapes,
  responses); only the listed changes are visible.
- **No FC-AJDK port** of `db.fcdsl`. Its engine avoids Android-missing APIs, but Android
  has no iq80 LevelDB binding here.
- `fudp.LossyRequestResponseTest` is flaky under load (one failure in several full runs).

## Constraints

- **Wire protocols are unchanged** for clients: FAPI16 CALL, FAPI14 MAP, FAPI15 ROAD,
  FAPI12 DISK, the DOCK API, FUDP. The Android and Mac apps must work against a light
  server exactly as against a full one. The only visible changes:
  - 400 for unknown fields;
  - 501 as in D4;
  - NewcomerBoard's range now applied.
- Tests to keep green, among others:
  - `fapi.service.StreamedUploadTest`
  - `fapi.components.call.CallComponentFapiTest`
  - `fapi.components.disk.StreamingDiskTest`
  - `fudp.LargeNotifySpillTest`
  - `fudp.LossyRequestResponseTest`
  - `MultiDeviceRoadTest`, `RoadComponentTest`

  Run `mvn -f FC-JDK/pom.xml clean` first if surefire reports "wrong name" errors: iCloud
  makes conflict copies such as `X 8.class` under `target/`.
- Never commit private keys or log them.
- Commit on a branch. The user merges and pushes on request ("merge and push").

## Related

- **`CallRelayServer`** (test sources, `fapi.components.call`) is today's free test relay
  on the HK server. It runs as the systemd user service `callrelay`, on UDP and TCP
  19950. It is not billed, and it is a crude light CALL server already: a possible
  starting point.
- **Freer, for the client side of the query contract:**
  - `FC-AJDK/src/main/java/com/fc/fc_ajdk/fapi/client/FapiClient.java`
  - `app/src/main/java/com/fc/freer/im/dock/DockFetchScheduler.java`
  - `app/src/main/java/com/fc/freer/im/NewcomerBoard.java`
- **The docs the spec changes go into:** FAPI12 for DISK, the DOCK API doc, and the
  FCDSL doc (for `match`, `total`, 400 and 501).
