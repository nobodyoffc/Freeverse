# FC-JDK Split & Consolidation Plan — `fc-core` + `fc-android`

> **Status: DEFERRED (2026-04-21).** This plan is not currently scheduled.
> The SafeForMac project proceeds directly against `Freeverse/FC-JDK` as-is
> rather than blocking on this consolidation. Revisit when:
> (a) FC-JDK ↔ Android-copy drift starts causing real bugs, or
> (b) a second desktop/JVM consumer is planned and three-way drift becomes
>     maintenance-expensive, or
> (c) iOS (or another Kotlin/Native target) is planned, at which point the
>     platform-abstraction interfaces in §3 become load-bearing.
>
> Until then, Android fixes flow directly into `Freeverse/FC-JDK` as part
> of normal development (see SafeForMac's Phase 0 diff step), and Freer /
> Safe Android continue to maintain their own FC-AJDK copies.

This plan consolidates the three divergent copies of the FC crypto library
(`Freeverse/FC-JDK`, `Freer/FC-AJDK`, `Safe/FC-AJDK`) into a single canonical
JVM library (`fc-core`) with a thin Android adapter (`fc-android`). Consumers
(Freer Android, Safe Android, and SafeForMac) would all depend on the same
published artifact.

---

## 1. Scope and Goals

**Goals:**
- Produce `fc-core`: pure-JVM library, Maven-published, no Android types
- Produce `fc-android`: Android adapter hosting the Android-only utilities
  (`AndroidFileUtils`, `QRCodeScanner`, `ImageUtils`, `TimberLogger`,
  `AvatarMaker`, Android-context-bound `Configure`/`TxCreator` shims)
- Migrate Freer Android and Safe Android to consume `fc-core` + `fc-android`
  and verify parity
- Resolve two latent blockers: package namespace divergence and the
  BouncyCastle variant conflict
- Unblock the macOS port by giving it a stable `fc-core` to build against

**Non-goals:**
- New features in the library
- Server-side (`server`, `fapi`, `fudp`, `managers`) cleanup beyond what's
  needed to make `fc-core` a clean dependency for Android and desktop
- Any macOS UI work (that is its own plan)

---

## 2. Current State

### 2.1 Three copies, three shapes

| Copy | Java files | Build | Package root | BouncyCastle | Role |
|---|---|---|---|---|---|
| `Desktop/Freeverse/FC-JDK` | 583 | Maven (`pom.xml`) | flat (`utils`, `core`, `app`, …) | `bcprov-jdk18on:1.76` (direct) | Superset; server + client; pure JVM; "nearly debugged" |
| `Freer/FC-AJDK` | 323 | Gradle Android library | `com.fc.fc_ajdk.*` | `bcprov-jdk15to18` (transitive via freecashj) | Android; has parallel fixes |
| `Safe/FC-AJDK` | 200 | Gradle Android library | `com.fc.fc_ajdk.*` | `bcprov-jdk15to18` (transitive via freecashj) | Android; smallest; may have fixes FC-JDK lacks |

Drift exists in **both** directions: FC-JDK has server-side content and its
own debug work; the Android copies have their own fixes for overlapping
issues.

### 2.2 Two blockers the previous plans hadn't named

**Blocker A — Package namespace divergence.**
FC-JDK files declare flat packages:

```java
package utils;
package core.crypto.Encryptor;
```

Android FC-AJDK files declare nested packages:

```java
package com.fc.fc_ajdk.utils;
package com.fc.fc_ajdk.core.crypto;
```

Consumers cannot simply drop in a JAR built from FC-JDK — every import
statement in Freer Android and Safe Android references
`com.fc.fc_ajdk.*`. Picking a single namespace is the first irreversible
decision of this project.

**Blocker B — BouncyCastle variant conflict.**
FC-JDK `pom.xml` declares `bcprov-jdk18on:1.76` directly. Android FC-AJDK
deliberately relies on `bcprov-jdk15to18` pulled transitively through
`com.github.nobodyoffc:freecashj:v0.16` (the current `build.gradle.kts`
even carries a comment warning against adding `bcprov-jdk18on`).

Both variants contain `org.bouncycastle.*` classes. With both on the
classpath, classloading order decides which `Argon2BytesGenerator` wins —
quietly, not noisily. This must be unified across all consumers before a
single artifact can serve them.

### 2.3 Android-only code FC-JDK can't host

Per the Safe plan §2.2, the following files in the Android FC-AJDK copies
depend on Android types and must live outside `fc-core`:

- `utils/AndroidFileUtils.java` — Android file I/O
- `utils/QRCodeScanner.java` — CameraX / activity / permission
- `utils/ImageUtils.java` — `android.graphics.Bitmap` / `Canvas`
- `utils/QRCodeUtils.java` — `Bitmap`
- `utils/TimberLogger.java` — `android.util.Log`
- `feature/avatar/AvatarMaker.java` — Bitmap composition
- `config/Configure.java` — `Context` for file paths
- `core/fch/TxCreator.java` — `Context` + `Toast`
- trivial `androidx.annotation.NonNull`/`Nullable` usages in
  `utils/BytesUtils.java`, `utils/FcDate.java`,
  `data/fchData/MultisignTxDetail.java`

FC-JDK does not contain these — they were stripped when FC-JDK was carved
out. This is actually helpful: FC-JDK is already close to what `fc-core`
needs to be.

---

## 3. Target Architecture

```
fc-core/                     pure JVM, Maven-published
  └── org.freeverse.fc.*     (final namespace TBD — see §4.1)

fc-android/                  Android Library (AAR), depends on fc-core
  └── org.freeverse.fc.android.*
      AndroidFileSystem, AndroidImageCodec, AndroidQRScanner,
      AndroidLogger, AndroidAppPaths, AndroidToastBridge

fc-platform-desktop/         lives inside the SafeForMac repo, not here
  └── org.freeverse.fc.desktop.*
      DesktopFileSystem, AwtImageCodec, Slf4jLogger, DesktopAppPaths
```

`fc-core` exposes small interfaces (`FileSystem`, `ImageCodec`, `AppPaths`,
`Logger`, `ToastBridge` / user-message bus) that `fc-android` and
`fc-platform-desktop` implement. The interfaces are the seam between the
crypto core and each host platform.

### Publishing

- `fc-core` and `fc-android` are published from a single parent Maven
  project (the existing `fc/Freeverse` parent pom is already the home for
  FC-JDK).
- Distribution: JitPack is already configured in `FC-JDK/pom.xml`
  repositories — reuse that path unless a private Maven is wanted.

### BouncyCastle — one variant everywhere

- `fc-core` depends on `bcprov-jdk18on:1.76` only.
- `fc-android` excludes `org.bouncycastle:bcprov-jdk15to18` from freecashj:

  ```kotlin
  implementation("com.github.nobodyoffc:freecashj:v0.16") {
      exclude(group = "com.google.code.gson", module = "gson")
      exclude(group = "org.json", module = "json")
      exclude(group = "org.slf4j", module = "slf4j-api")
      exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
  }
  ```

- Verified during Phase C with an Android integration test that
  `Argon2BytesGenerator` still runs and produces the expected KDF output
  from the existing Android fixtures. If it fails, we either patch
  freecashj or stay on `bcprov-jdk15to18` *everywhere* (including
  FC-JDK) — but pick one, don't mix.

---

## 4. Critical Decisions (must be made before Phase B)

### 4.1 Namespace

Three options, in rough order of recommendation:

| Option | Effort | Why |
|---|---|---|
| **Rename FC-JDK's flat packages → `com.fc.fc_ajdk.*`** | Low (FC-JDK files only); Android consumers unchanged | Android apps are bigger and noisier to rename; keep them working |
| Rename Android copies → FC-JDK's flat packages | High; touches every import in both Android apps | Flat packages are not a Java convention and will look wrong in a published artifact |
| Adopt a new unified namespace (`org.freeverse.fc.*`) | Highest; touches everything | Cleanest long-term, most churn now |

**Recommendation: option 1** — it's the smallest blast radius and the
Android package name is the one most visible to library consumers.
`com.fc.fc_ajdk` is also already the published convention.

If option 3 is preferred for branding reasons, make the decision before
Phase A so the three-way diff can be done against post-rename FC-JDK.

### 4.2 Which fixes take precedence when FC-JDK and Android disagree

The three-way diff in Phase A will find files where all three copies
differ. Policy before starting:

- FC-JDK wins unless an Android copy has a demonstrably newer bug fix
  (verified by running the fix against a known-failing case).
- When in doubt, flag the file for human review rather than auto-merging.

### 4.3 Consumer migration cadence

Two sub-options:

- **Synchronized**: Freer and Safe Android both cut over to `fc-core` in
  the same week. Lowest ongoing-maintenance cost but highest week of risk.
- **Sequenced**: Safe Android first (smaller, less server surface), then
  Freer. Strictly more work but halves the rollback surface.

Recommend sequenced unless both apps are trivially testable by one person
in a day.

---

## 5. Phased Plan

### Phase A — Three-way diff & reconciliation (3–5 days)

- Pick a canonical subset: the files that exist in all three copies (the
  overlap; should be the majority of the ~200 files in Safe/FC-AJDK).
- Diff each file across the three copies. Classify each divergent file:
  1. **Identical** (no action).
  2. **Cosmetic-only** (whitespace, comment, import order) — take FC-JDK.
  3. **Functional — FC-JDK ahead** — take FC-JDK.
  4. **Functional — Android ahead** — flag for backport into FC-JDK.
  5. **Functional — both ahead in different ways** — human review.
- Output: a checklist of files to backport in Phase C, plus a short doc
  recording which copy "won" each decision.

### Phase B — Prepare `fc-core` shape (2–3 days)

- Rename FC-JDK packages per §4.1 (or adopt new namespace).
- Split `fc-core` source from the bits that only exist for server/client
  use cases that neither Android nor macOS need. Keep them in FC-JDK but
  in a separate Maven module if desired (e.g. `fc-core` vs `fc-server`),
  or simply annotate them as optional. The macOS app does not need the
  server code, and keeping it in a separate module avoids dragging Netty
  and servlet-api into desktop/mobile classpaths.
- Define platform-abstraction interfaces (`FileSystem`, `ImageCodec`,
  `AppPaths`, `Logger`, `ToastBridge`) inside `fc-core`. Refactor the
  internal callers (e.g. current Android `Configure` path logic) to use
  them.
- Publish a `0.1.0-SNAPSHOT` of `fc-core` to JitPack or local Maven.

### Phase C — Backport Android-side fixes (3–5 days)

- Apply the Phase A backport list onto `fc-core`.
- Unit-test `fc-core` on a plain JVM (pre-existing `src/test/java` from
  FC-JDK plus any tests migrated from the Android copies).
- **Verify BouncyCastle consolidation**: Argon2 KDF against known vectors
  from the Android apps, ECC sign/verify round-trip, any other crypto
  fixtures the Android copies have.
- Publish `fc-core 1.0.0`.

### Phase D — Build `fc-android` adapter (2–3 days)

- Create Android Library module (AAR) depending on `fc-core:1.0.0`.
- Host the Android-only files (§2.3) behind `fc-core`'s interfaces:
  - `AndroidFileSystem : FileSystem`
  - `AndroidImageCodec : ImageCodec` (Bitmap / Canvas implementations for
    `ImageUtils`, `QRCodeUtils`, `AvatarMaker`)
  - `AndroidAppPaths : AppPaths` (wraps `Context.getFilesDir()` etc.)
  - `AndroidLogger : Logger` (Timber / `android.util.Log` bridge)
  - `AndroidToastBridge : ToastBridge` (keeps `TxCreator` library-clean;
    the Android app provides the `Toast` side)
  - `QRCodeScanner` stays here as-is (CameraX / activity concerns).
- Exclude `bcprov-jdk15to18` from freecashj in the `fc-android` module.
- Publish `fc-android 1.0.0` (AAR).

### Phase E — Migrate Freer Android (3–5 days)

- Delete `Freer/FC-AJDK/` source tree.
- Add `implementation("com.github.fc:fc-core:1.0.0")` and
  `implementation("com.github.fc:fc-android:1.0.0")` to
  `Freer/app/build.gradle.kts`.
- Fix any imports that referenced internal-to-FC-AJDK paths that didn't
  make it into the published artifact.
- Run the full Freer test suite; manual smoke of the core flows.
- Commit.

### Phase F — Migrate Safe Android (3–5 days)

- Same steps as Phase E, for Safe Android.
- At this point: three-way drift is resolved, both Android apps run on
  `fc-core 1.0.0`, and the macOS plan is unblocked.

### Phase G — Sign-off & handoff (1–2 days)

- Tag `fc-core 1.0.0` and `fc-android 1.0.0` in git.
- Generate the **golden vectors** for macOS interop on the Android
  builds (see `MACOS_MIGRATION_PLAN.md` Phase 1). Commit them to
  `fc-core/src/test/resources/golden/`. They double as regression tests
  for `fc-core` itself and as the cross-platform interop gate for macOS.
- Write a one-pager on how to consume `fc-core` from a new JVM project
  (the macOS port will be the first reader).

---

## 6. Risks

- **Namespace rename on 583 files is noisy but mechanical.** Use IntelliJ
  "Rename package" or a single `sed` pass; do it in one commit so it's
  easy to review.
- **BouncyCastle unification breaks freecashj on Android.** Low
  probability (both variants expose the same public APIs used by
  freecashj), but has to be verified by running Argon2 and ECC on a real
  Android device in Phase C before either Android app is migrated.
- **Android-only fixes miss backport.** Phase A's three-way diff is the
  only safety net. Budget time for human review of ambiguous diffs —
  auto-merging crypto is a lose.
- **Server-side content in FC-JDK pulls in Netty / servlet-api.** If
  `fc-core` keeps it, Android and macOS classpaths get bloated. If it's
  split into a separate `fc-server` module, the server consumers have to
  add one more dep. Prefer splitting.
- **JitPack build failure.** JitPack builds on each tag; if the Maven
  layout is unusual (multi-module parent pom) the build can fail silently.
  Publish once to JitPack in Phase B as a smoke test before relying on
  it in Phase E/F.
- **freecashj is the only non-Maven-Central dep** (via JitPack, from a
  GitHub fork). It is a single point of supply-chain risk for both
  platforms. Not this plan's job to fix, but worth flagging.

---

## 7. Summary Estimate

| Phase | Effort |
|---|---|
| A — Three-way diff & reconciliation | 3–5 days |
| B — `fc-core` shape + namespace + interfaces | 2–3 days |
| C — Backport fixes + BC verification + publish | 3–5 days |
| D — Build `fc-android` adapter + publish | 2–3 days |
| E — Migrate Freer Android | 3–5 days |
| F — Migrate Safe Android | 3–5 days |
| G — Sign-off, golden vectors, handoff | 1–2 days |

**Total: 3–5 weeks of focused work.** Phase A's diff work determines
everything downstream; if the diff is small, phases C–F compress. If the
three copies have diverged heavily, phases C and E–F dominate.

Completion of Phase G is the gate for starting the macOS port.
