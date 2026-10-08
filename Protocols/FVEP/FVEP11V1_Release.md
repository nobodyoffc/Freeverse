# FVEP11V1_Release

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
|Title|Release|
|Type|FVEP|
|SN|11|
|Ver|1|
|Category|Data|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-10-09|
|PID||

## Abstract

FVEP11 Release defines how the protocols, code and apps of a software release are registered on chain with FEIP1 Protocol, FEIP2 Code and FEIP15 APP, so that anyone can check a registration against the release it names. It fixes what each DID is the hash of, including a byte-exact code archive that any tool can rebuild from the repository; how a record is kept across versions; how a document names its own PID; which home keys point where; and the freeverse-release.json manifest that tells a tool what a repository releases. It adds no on-chain operation.

## Motivation

FEIP1, FEIP2 and FEIP15 each carry a `did`, but leave open what it is the hash of: "protocol-specific", "a commit hash, release tag", "document or release id". A registration whose DID cannot be recomputed proves nothing. A reader holding a protocol document, a module's source or a downloaded app cannot tell whether it is the one that was registered.

A code DID needs more than a hash function. Two archives of the same files differ if their entries, timestamps or compression differ, and common tools do not hold these still: `git archive` writes the commit id into the archive and stamps the current time when given a tree. So the archive itself must be defined byte for byte.

Releases also need a few shared conventions so that independent tools agree: which on-chain record a new version updates, where a document's own PID goes, and how links between protocols, code and apps are stated. With them, a release can be registered by any conforming tool and verified by anyone, without trusting the tool that carved it.

## Specification

The key words MUST, MUST NOT, SHOULD and MAY are used as in RFC 2119.

### Definitions

|Term|Meaning|
|---|---|
|sha256x2|`SHA-256(SHA-256(bytes))`, written as 64 lowercase hex digits in digest byte order (not reversed as a txid is).|
|Release|A tagged version of a repository, with its assets (e.g. a GitHub release).|
|Entity|A protocol, code or app registered with FEIP1, FEIP2 or FEIP15.|
|Record id|The `pid`, `codeId` or `aid`: the txid of the entity's `publish`.|
|Owner|The FID that published the record. Only the owner can update it: FEIP1, FEIP2 and FEIP15 ignore an `update` whose signer is not the owner. A master (FEIP6) may stop, recover or close the record, but not update it.|

### 1. Identity across versions

1. An entity is published once. Its record id is the txid of that `publish` and never changes.
2. A new version of the entity MUST be carved as an `update` of the same record id, not as a new `publish`. Its `did` and `ver` change; the id does not.
3. An `update` replaces the record's mutable fields. A tool MUST resend every field it means to keep, including fields it did not change.
4. A tool finds the existing record of an entity by its key. The owner is part of every key, and the owner is the FID that signs the release's carves:

|Entity|Key|
|---|---|
|Protocol|owner + `type` + `sn` (a document that states its PID is matched by that PID instead)|
|Code|owner + `name`|
|App|owner + `stdName`|

   - Records of other owners are never matched. Anyone can publish a FEIP1 record with the same `type` and `sn`: that is a separate registration (a fork, or a competing specification), not a version of this one, and an update carved against it would be ignored. Which owner's record is the reference one is for each reader to decide.
   - A document whose PID is a record of another owner MUST NOT be registered: the release's signer cannot update it.
   - If several records of the owner share a key, the tool MUST NOT choose one silently.

5. Stopped and closed records are not updated: the parsers ignore an `update` to an inactive record. A stopped record MUST be recovered first. A closed record stays closed.

### 2. Protocol documents

A protocol document is a Markdown file named `<TYPE><SN>V<VER>_<Name>.md`, for example `FEIP1V7_Protocol.md`. Its **Summary table** is the first pipe table under the `## Summary` heading; if that section has none, the first pipe table before `## Contents`; failing both, the first pipe table in the file.

1. **Fields.** The registration's fields come from the document:

|FEIP1 field|Source|
|---|---|
|`name`|Summary `Title`|
|`type`|Summary `Type`; MUST equal the file name's `<TYPE>`|
|`sn`|Summary `SN`; MUST equal the file name's `<SN>`|
|`ver`|Summary `Ver` or `Version`; MUST equal the file name's `<VER>`|
|`lang`|Summary `Lang` or `Language`; `en` if absent|
|`desc`|The `## Abstract` section as one line: fenced code and lines starting with `#` dropped, `[text](link)` replaced by `text`, `**` and backticks removed, whitespace collapsed. It MAY be shortened, at a word and ending in "…", to fit the OP_RETURN.|
|`did`|sha256x2 of the document file's bytes, as carved (see 3)|

A file whose Summary disagrees with its name, such as a change proposal, is not a version of the protocol and MUST NOT be registered as one.

2. **Versions.** For each `type` + `sn`, only the highest `<VER>` is registered.

3. **The PID row.** The Summary table has a `PID` row.
   - For a `publish`, the row is left empty (`|PID||`): the PID does not exist until the publish is carved.
   - For an `update`, the record id MUST be written into the row (`|PID|<pid>|`) **before** the DID is computed. The registered document thus names its own record. If the table has no `PID` row, one is appended to it.
   - A document that differs from a registered one only by an empty `PID` row is the same version.

4. **Storage.** The document, as hashed, SHOULD be stored permanently and unencrypted on DISK under its DID, so that anyone can fetch it by the DID on chain.

### 3. Code archives

A code is a module of a repository (a directory) or, with path `.`, the whole repository. Its `ver` is the release tag, and its `did` is the sha256x2 of its **archive**, built as follows.

1. **Files.** The files are the blobs listed by `git ls-tree -r --full-tree <tag> -- <path>/` (the whole tree for path `.`), read with `git cat-file`. Submodules (commit entries) are excluded. A symbolic link (mode 120000) is stored as a file whose content is the link target.
2. **Names.** Each entry is named `<code name>/<path relative to the module directory>`, encoded in UTF-8. Entries are sorted by the byte order of their UTF-8 names. There are no directory entries.
3. **Format.** The archive is a ZIP file with exactly these structures, all integers little-endian:

|Structure|Content|
|---|---|
|Local file header, per entry, in order|signature `0x04034B50`; version needed `20`; flags `0x0800` (UTF-8 names); method; time `0`; date `0x0021` (1980-01-01); CRC-32 of the file; compressed size; uncompressed size; name length; extra length `0`; the name; the entry data|
|Central directory header, per entry, in order|signature `0x02014B50`; version made by `0x0314` (Unix, 2.0); version needed `20`; flags `0x0800`; method; time `0`; date `0x0021`; CRC-32; compressed size; uncompressed size; name length; extra length `0`; comment length `0`; disk `0`; internal attributes `0`; external attributes `mode << 16`; offset of the local header; the name|
|End of central directory|signature `0x06054B50`; disk `0`; central directory disk `0`; entries on this disk; entries total; central directory size; central directory offset; comment length `0`|

   - `mode` is `0o100755` for a blob of git mode `100755`, otherwise `0o100644`.
   - **Method and data.** The entry is compressed with raw DEFLATE (RFC 1951) as produced by zlib at level 5 with a 15-bit window and memory level 8, i.e. `deflateInit2(level 5, Z_DEFLATED, -15, 8, Z_DEFAULT_STRATEGY)`, and stored with method `8` if that output is strictly shorter than the file; otherwise, and for an empty file, the file is stored as is with method `0`.
   - No data descriptors, no extra fields, no comments, no encryption, no ZIP64. A code with 65 535 or more files, or an archive of 4 GiB or more, cannot be archived under this version.
4. **Name.** The archive is published as `<code name>-<tag>.zip`.
5. **Storage.** The archive SHOULD be stored permanently and unencrypted on DISK under its DID, and SHOULD be attached to the release.

Because the archive depends only on the files, a module whose files did not change between two tags has the same DID, and needs no update.

### 4. Apps

1. One app record is registered per release asset that users install: a jar, war, dmg, apk, zip, and so on. A library that is not run is registered as code, not as an app.
2. The app's `ver` is the release tag.
3. Its `downloads` holds the asset: `os` (free text such as `java`, `macos`, `android`, `web`), `link` (the asset's download URL) and `did` = sha256x2 of the asset's bytes. When the host publishes the asset's SHA-256, the DID is the SHA-256 of that digest's 32 bytes; the file need not be downloaded.

### 5. Links

1. A code's `protocols` lists the record ids of the protocols it implements. A family MAY be named by its root protocol (`FEIP0`, `FUDP0`, …) rather than by each member, to fit the OP_RETURN.
2. An app's `codes` and `protocols` list the record ids of the code it is built from and the protocols it speaks.
3. Protocols are carved before code, and code before apps, so every link names a record that exists. A record id is known as soon as its carve is broadcast; it need not be confirmed first.

### 6. Home keys

`home` is a `Map<String,String>` for all three entities. A releasing tool sets these keys and MUST keep any others the record already has:

|Entity|Key|Value|
|---|---|---|
|Protocol|`src`|URL of the document in the repository|
|Code|`src`|URL of the module directory at the tag|
|Code|`zip`|Download URL of the archive (3.4)|
|App|`src`|URL of the release page|

### 7. The release manifest

A repository states what it releases in `freeverse-release.json` at its root. A conforming tool MUST NOT register a repository without one.

```json
{
  "github": "owner/repo",
  "protocolDirs": ["Protocols"],
  "codes": [
    { "name": "FC-JDK", "path": "FC-JDK", "langs": ["Java"], "desc": "…",
      "protocols": ["FEIP0", "FUDP0"] }
  ],
  "apps": [
    { "stdName": "FapiServer", "asset": "FapiServer.jar", "os": "java", "types": ["server"],
      "desc": "…", "localNames": { "en": "…" },
      "codes": ["FC-JDK"], "protocols": ["FAPI0"] }
  ]
}
```

|Field|Type|Meaning|
|---|---|---|
|`github`|String|`owner/repo` of the repository; release assets and `home` URLs derive from it.|
|`protocolDirs`|List\<String\>|Directories searched, recursively, for protocol documents (section 2).|
|`codes[].name`|String|The code's `name`, its key. Unique in the manifest.|
|`codes[].path`|String|Module directory relative to the root, or `.` for the whole repository. MUST stay inside the repository.|
|`codes[].langs`, `.desc`|List\<String\>, String|Carved as the code's `langs` and `desc`.|
|`codes[].protocols`|List\<String\>|Protocol references (below).|
|`apps[].stdName`|String|The app's `stdName`, its key. Unique in the manifest.|
|`apps[].asset`|String|Name of the release asset; `*` matches any run of characters (`Freer-*.dmg`). It MUST match exactly one asset.|
|`apps[].os`, `.types`, `.desc`, `.localNames`|String, List\<String\>, String, Map|Carved as the app's download `os`, `types`, `desc` and `localNames`.|
|`apps[].codes`|List\<String\>|Code references (below).|
|`apps[].protocols`|List\<String\>|Protocol references (below).|

**References.** A protocol reference is `<TYPE><SN>` (`FEIP1`), resolving to the owner's record of that type and sn, or a 64-hex record id. A code reference is a code `name`, optionally prefixed by a repository (`repo/name`), resolving to the owner's code of that name, or a 64-hex record id. Names resolve only among the owner's records; to link a record of another owner, write its record id.

The manifest is the source of the fields it holds: a tool compares them with the record and updates the record when they differ, even if the DID did not change. Fields it does not hold (`waiters`, extra `home` keys, `preDid`, `services`) are carried over from the record.

## Examples

### Example 1: Updating a protocol to a new version

The owner's record of FEIP5 has `ver` 3. The repository now holds `FEIP5V4_Service.md`, with an empty PID row.

1. Match: `type` FEIP, `sn` 5 → the owner's record `<pid>`; `ver` 3 → 4 is an update of `<pid>`, not a new publish.
2. Write `|PID|<pid>|` into the Summary table.
3. Hash the file: `did` = sha256x2(file).
4. Store the file on DISK under that DID.
5. Carve FEIP1 `update` with `pid` `<pid>`, `sn` "5", `ver` "4", the new `did`, `home.src` the document's URL, and the record's existing `waiters` and `preDid`.

### Example 2: Verifying a code registration

The chain holds code `FreerForMac`, `ver` `v0.4.1`, `did` `38ad5bf570246fe52ddde6ed4ea4b6e4dcfa80d61fd7cceac18132525c0ebce6`, `home.src` `https://github.com/nobodyoffc/freer-mac/tree/v0.4.1`.

A reader clones the repository, builds the archive of path `.` at tag `v0.4.1` named `FreerForMac` (section 3), and computes sha256x2: `38ad5bf5…ebce6`. It matches, so the registered code is exactly the tagged source. The reader can also fetch `FreerForMac-v0.4.1.zip` from DISK by the DID, or from the release, and check its hash without cloning.

### Example 3: An unchanged module

FeipClient's files are the same at tags `v0.3` and `v0.3.1`. Both archives are byte-identical (`did` `3b122fdc…`), so releasing `v0.3.1` leaves the FeipClient record as it is.

## Versioning

|Version|Date|Changes|
|---|---|---|
|1|2026-10-09|Initial version. DID rules for protocols, code and apps; the byte-exact code archive; identity across versions; the PID row; links and home keys; the release manifest.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FVEP0 FVEP|General rules for all FVEP protocols|
|FVEP2 ID|A DID is the sha256x2 hash identifier defined there|
|FVEP10 HAT|Documents and archives stored on DISK are described by HATs|
|FEIP1 Protocol|Carries protocol registrations; its `did` follows section 2|
|FEIP2 Code|Carries code registrations; its `did` follows section 3|
|FEIP15 APP|Carries app registrations; each download's `did` follows section 4|
|FAPI12 DISK|Stores documents and archives by DID|

## Reference Implementation

- `FreerForMac/Packages/FCDomain/Sources/FCDomain/ReleaseSync/` — Release Sync in Freer for macOS: manifest (`ReleaseManifest.swift`), protocol documents and the PID row (`ProtocolDoc.swift`), the code archive (`DeterministicZip.swift`, `ReleaseTools.swift`), matching and planning (`ReleasePlan.swift`), carving (`ReleaseRunner.swift`). Its design notes are `RELEASE_SYNC_SPEC.md`.
- The code archive in Python, standard library only. It reproduces the DIDs of the Swift implementation byte for byte:

```python
import hashlib, struct, subprocess, zlib

def code_archive(repo, tag, path, name):
    args = ["git", "-C", repo, "ls-tree", "-r", "-z", "--full-tree", tag]
    if path not in (".", ""):
        args += ["--", path.rstrip("/") + "/"]
    prefix = "" if path in (".", "") else path.rstrip("/") + "/"
    entries = []
    for rec in subprocess.run(args, check=True, capture_output=True).stdout.split(b"\0"):
        if not rec:
            continue
        meta, p = rec.split(b"\t", 1)
        mode, kind, oid = meta.decode().split(" ")
        if kind != "blob":
            continue
        data = subprocess.run(["git", "-C", repo, "cat-file", "blob", oid],
                              check=True, capture_output=True).stdout
        entries.append(((name + "/" + p.decode()[len(prefix):]).encode(), data, mode == "100755"))
    entries.sort(key=lambda e: e[0])
    out, central = bytearray(), bytearray()
    for nm, data, exe in entries:
        crc = zlib.crc32(data) & 0xFFFFFFFF
        c = zlib.compressobj(5, zlib.DEFLATED, -15, 8)
        d = c.compress(data) + c.flush() if data else b""
        method, body = (8, d) if data and len(d) < len(data) else (0, data)
        offset = len(out)
        out += struct.pack("<IHHHHHIIIHH", 0x04034B50, 20, 0x0800, method, 0, 0x0021,
                           crc, len(body), len(data), len(nm), 0) + nm + body
        attr = (0o100755 if exe else 0o100644) << 16
        central += struct.pack("<IHHHHHHIIIHHHHHII", 0x02014B50, 0x0314, 20, 0x0800, method, 0, 0x0021,
                               crc, len(body), len(data), len(nm), 0, 0, 0, 0, attr, offset) + nm
    cd_offset = len(out)
    out += central + struct.pack("<IHHHHIIH", 0x06054B50, 0, 0, len(entries), len(entries),
                                 len(central), cd_offset, 0)
    return bytes(out)

def did(data):
    return hashlib.sha256(hashlib.sha256(data).digest()).hexdigest()
```
