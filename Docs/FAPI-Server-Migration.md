# FAPI Server Migration

Last updated: 2026-11-17. Shared copy: <https://claude.ai/artifact/AJchvcy56pcB7U3H3j9knm>

## Overview

A FAPI service (one SID) moves to a new host in two parts: a small **migration bundle** made by `StartFapiServer --export`, and the **DISK file folder**, copied separately with `rsync`. The DISK folder can be very large, so it stays out of the bundle.

Stored DISK files never change: each is named by its DID (the hash of its content) and written through a temp file plus an atomic rename. So the bulk copy can run while the old server still serves, and only a small final pass happens during the downtime.

Two marker files stop the service from running in two places at once, which would split its balances. The old host refuses to start after export. The new host refuses to start until its DISK files match the imported metadata.

## What moves

Paths are relative to the server's working directory. `<FID>_<SID>` is the server's FID and the service's SID.

| State | Location | How it moves |
| --- | --- | --- |
| Keys and accounts (encrypted) | `config/config.json` | Bundle |
| Service settings | `config/<SID>_settings.json` | Bundle; `dbDir` and `diskDir` rewritten on import |
| Balances, with WAL and snapshots | `db/<FID>_<SID>_fapi_balance/` | Bundle |
| DOCK | `db/<FID>_<SID>_fapi_dock/` | Bundle |
| DISK metadata | `db/<FID>_<SID>_fapi_disk_meta/` | Bundle |
| MAP entries, DISK sync state | `db/map_entries.json`, `db/diskSyncState.json` | Bundle (shared by all SIDs in that `dbDir`) |
| DISK files | `diskDir` from settings, else `db/disk/` | `rsync`, not in the bundle |
| Chain index (full servers only) | Elasticsearch | Not moved: point at an ES on the new host or rebuild |
| Logs, FUDP downloads | `logs/`, `fudp_data/` | Not needed |

Other SIDs' stores and other services' settings in the same folders are left out. LevelDB `LOCK` files are skipped.

The setting `fapiDiskDataPath` is not read by the DISK component. The real DISK folder is `settingMap.diskDir`, or `dbDir/disk` when that is unset.

## Procedure

The move has two phases. Phase 1 copies the DISK data while the service keeps running, and takes the longest. Phase 2, the migration itself, starts only when phase 1 has finished, so the downtime is one small rsync plus the bundle.

### Phase 1: copy the DISK data (service up)

1. **Prepare the new host.** Install Java 17 and the FAPI server jar. Open the FUDP port (default 8500, UDP). Make a separate working folder for the FAPI server: FchParser, FeipParser and the FAPI server each read `config/config.json` from their working folder, and import would replace the parsers' file. A full server also needs its Elasticsearch and node; a light server needs only its upstream.
2. **Bulk-copy the DISK folder while the old server keeps serving.** It can take hours or days; if it stops, run it again and it resumes:
   `rsync -aH --partial --info=progress2 --exclude='*.tmp.*' OLD_DISK_DIR/ NEW_HOST:NEW_DISK_DIR/`
   New uploads keep arriving, so repeat it until a run sends only a few files. Phase 1 is done only then.
3. **Catch up the parsers (full servers with BASE).** Run FchParser and FeipParser on the new host, each from its own folder, until they reach the chain tip. Phase 2 waits for them: the imported server refuses to start while its chain index is below the block the balances were exported at. Starting earlier would be read as a chain rollback and reverse recent recharges. A light server skips this step.

### Phase 2: migrate the server (short downtime)

4. **Stop the old server.** Quit it from its menu so every store closes cleanly.
5. **Export on the old host:** `java -cp <jar> fapi.StartFapiServer --export` (add the SID if the host runs several FAPI services). It first asks whether the bulk DISK copy has finished; answering no exports nothing. It then writes `fapi-migration-<sid8>-<yyyyMMdd-HHmm>.zip` and the MOVED marker, and prints the final rsync command with the real path.
6. **Final DISK pass:** `rsync -aH --delete --exclude='*.tmp.*' OLD_DISK_DIR/ NEW_HOST:NEW_DISK_DIR/`. It sends only what changed since phase 1. `--delete` removes files that expired or were deleted, so the files match the exported metadata.
7. **Import on the new host:** copy the bundle into the FAPI server's own folder, cd there, then run `java -cp <jar> fapi.StartFapiServer --import fapi-migration-….zip`. Give the new `dbDir` and DISK folder when asked. Import ends with a DISK check. If the check reports missing files, finish the rsync and run `StartFapiServer --check-disk`. The server will not start until nothing is missing.
8. **Start and verify.** Start the server as usual and enter the same password as on the old host. Connect a client directly to the new address. Check a known balance, a DOCK read and a few `disk.get` calls.
9. **Point clients at the new host.** If `apiUrl` uses a domain name, change DNS and you are done. If it uses an IP, run **Update services** in the server menu and send the printed OpReturn in a transaction from the service owner.
10. **Clean up.** Leave the old host stopped. Delete the bundle once the new host is confirmed, or keep it offline: it holds the encrypted keys and every balance.

## Commands

The three commands run instead of starting the server, from the server's working directory. Code: `fapi.migrate.ServerMigration` (logic) and `fapi.migrate.MigrationCli` (console).

| Command | Run on | What it does | Refuses when |
| --- | --- | --- | --- |
| `--export [SID]` | Old host, server stopped | Asks whether the bulk DISK copy has finished, then writes the bundle, counts DISK files and metadata items, writes the MOVED marker, prints the rsync command | A store is still open (server running); the service was already exported from here |
| `--import BUNDLE` | New host | Checks every file's SHA-256, extracts, rewrites `dbDir` and `diskDir` in the settings, lists other absolute paths that don't exist here, runs the DISK check, records the block height the parsers must reach, warns when the folder holds other programs' settings | Any checksum fails (nothing is extracted); a store for this SID already exists; a MOVED marker for it exists here |
| `--check-disk [SID]` | New host, server stopped | Compares DISK files with metadata; sets or clears the pending marker | A store is still open |

**Bundle format.** A zip with `config/…` and `db/…` entries and a `migration-manifest.json`. The manifest holds the format version, SID, FID, export time, the old `dbDir`, the DISK file count and bytes, and a path, size and SHA-256 for every file.

**config.json on the new host.** If none exists, the bundled one is installed. If an identical one exists, it is kept. If a different one exists, import asks before replacing it and keeps the old one as `config.json.bak-<time>`. The bundled file is required: it holds the server FID's encrypted key. Never replace the config.json of a folder that parsers or other servers run from; import the FAPI server into its own folder instead.

**Markers** (in `dbDir`, checked by `ServiceBootstrap` before any store opens):

- `<FID>_<SID>_MOVED.json`: written by export. The old host refuses to start the service.
- `<FID>_<SID>_DISK_CHECK_PENDING.json`: written by import or `--check-disk` while any metadata item has no file. Removed as soon as none is missing.
- `<FID>_<SID>_CHAIN_CHECK_PENDING.json`: written by import with the block height the balances were exported at. A full server refuses to start until its Elasticsearch reaches that height, then removes the file. A light server removes it at once.

## Safety and troubleshooting

**Never run both copies.** Two live servers with the same SID each take recharges and debit users on their own, and their balances can't be merged afterwards. The markers exist for this; delete one only when you are sure the other copy is gone.

| Symptom | Cause | Fix |
| --- | --- | --- |
| Export: "is open; stop the server first" | The server, or another process, holds a LevelDB store | Quit the server from its menu and run export again |
| Old host: "This service was exported to another host" | The MOVED marker | Expected. To abandon the move and keep the old host, delete the marker and start it |
| Import: "checksum mismatch" | Bundle damaged in transfer | Copy it again; compare with the SHA-256 that export printed |
| Import: "already exists here" | A store for this SID is already in the new `dbDir` | Move it aside (it may be an earlier test import), then import again |
| New host: "DISK files do not match" | Metadata items with no file | Run the final rsync, then `--check-disk` |
| Check shows orphan files | Files with no metadata, left by deletes after the bulk copy | Harmless; the final `rsync --delete` removes them. They don't block a start |
| New host: "this host's chain index is at block …" | FchParser or FeipParser has not reached the block the balances were exported at | Let the parsers finish, then start again; the check clears itself once the height is reached |
| Parsers fail after the import | Their config.json was replaced by the FAPI one (shared folder) | Restore `config.json.bak-<time>`, then re-import the FAPI server from its own folder |
| Clients still reach the old address | They cached the old `apiUrl` or DNS | Wait for the on-chain update or DNS TTL; clients that look up the service again find the new address |

**Rolling back.** Until clients are pointed at the new host, nothing on chain has changed. To go back, delete the MOVED marker on the old host and start it. Do this only if the new host never served users; otherwise its balances have moved on.

**Not covered.** The new host's Elasticsearch, fullnode and upstream config are set up as for any new server. Settings paths other than `dbDir` and `diskDir` are listed by import, not changed.
