# Odivrelo shared core

The Kotlin Multiplatform core the Android and iOS applications are both built on.

It decides everything that must not be decided twice: how a contract payload is
decoded, what a service date means, when a journey crosses midnight, which
results a search returns and in what order, whether a downloaded pack may be
trusted, and what the absence of an answer actually means.

## Targets and artifacts

`androidTarget()`, `iosArm64()`, `iosSimulatorArm64()` and `iosX64()`.

iOS consumes a single static XCFramework:

```
bash scripts/shared-build-xcframework.sh
# shared/core/build/XCFramework/debug/OdivreloCore.xcframework
# shared/core/build/XCFramework/release/OdivreloCore.xcframework
```

Both carry an `ios-arm64` device slice and a merged
`ios-arm64_x86_64-simulator` slice. Exported types are annotated so the generated
Objective-C header uses stable `Odivrelo`-prefixed names, suspending members
project into Swift as `async`, and `downloadPack` uses callbacks plus a
`Cancellable` so Swift needs no coroutine bridge.

## Linking it into an iOS target

The framework is **static**, so it carries no link dependencies of its own. It
embeds SQLDelight's native driver, which needs the system SQLite.

`scripts/shared-build-xcframework.sh` declares that requirement in each slice's
`Modules/module.modulemap`:

```
framework module "OdivreloCore" {
    umbrella header "OdivreloCore.h"
    …
    link "sqlite3"
}
```

Clang autolinking passes `-lsqlite3` for anything that imports `OdivreloCore`, so
an app target should need no manual linker flag. If a build system bypasses
autolinking, the fallback is to add `-lsqlite3` to **Other Linker Flags** on the
app target; the symptom otherwise is undefined `sqlite3_*` symbols at link time.

## Errors across the Objective-C boundary

Every exported suspending member declares:

```kotlin
@Throws(OdivreloException::class, CancellationException::class)
```

This is not decoration. On Kotlin/Native an exception outside a function's
`@Throws` list is never converted into an `NSError`: the runtime terminates the
process before Swift can catch anything, and it cannot be fixed from Swift. A
fresh installation has no data release, so the very first call fails for an
entirely ordinary reason, and without the annotation that killed the application
on launch.

Two tests hold the line, because an annotation is exactly the kind of thing that
silently comes back:

- `ExportedApiContractTest` parses `OdivreloCore.kt` and `OdivreloCoreExtras.kt`
  (through the `generateExportedApiFacts` Gradle task) and fails if any exported
  suspending member is missing the annotation. It reads the **source**, not the
  generated header, because a suspending function's completion handler carries an
  `NSError` parameter whether or not `@Throws` is present, so the header looks
  identical either way.
- `FirstRunTest` calls every exported member on a core with nothing installed and
  fails if any of them throws something that is not a `OdivreloException`. The
  implementation routes every exported call through one guard that translates
  whatever a driver, decoder or file system threw, which is what makes the
  declaration safe.

`CoreConfig`'s constructor deliberately does **not** throw, for the same reason:
a throwing initialiser across the boundary terminates the process. Construction
always succeeds, `CoreConfig.validationError` says what is wrong, and
`createOdivreloCore` throws a typed `OdivreloException` a host can report.

## The exported surface

`OdivreloCore` is the whole public read and write API; `createOdivreloCore(config)`
builds one. `OdivreloCoreExtras` carries two host-application capabilities that
deliberately sit beside the contract surface: the cached detail of a saved trip,
and adopting a release the host bundled with the application.

## Data path

A pack contains exactly what the matching `/v1/...` endpoint returns. There is no
separate offline shape, so the core has **one read path with two transports**
rather than two read paths:

1. read the pack from the installed release, or
2. fetch it from the configured origin, verify its SHA-256 against the manifest,
   install it atomically, then read it.

Whichever way the bytes arrived, the same decoder and the same query code produce
the answer. `TransportEqualityTest` asserts that a bundled release and the same
release downloaded over HTTP give byte-equal results for the same query.

### Pack names

`meta`, `coverage`, `sources`, `places`, `operators`, `stops`, `gtfs`, and
`journeys-<YYYY-MM-DD>`, one per service date. The first six are required; `gtfs`
is published for other tools and the core never reads it.

### The two absences

A release only materialises a journeys pack for the dates its data actually
names. A weekday-recurring service on some other date is resolved by
`/v1/journeys` on request and is deliberately not in any pack.

So the core keeps two states apart that it would be easy, and wrong, to merge:

| Situation | What the core returns |
| --- | --- |
| A pack for the date was read and nothing matched | `unavailableReason = no_service_on_date` |
| No pack for the date, and no service reachable | `dateDataState = NO_OFFLINE_PACK`, `unavailableReason = null` |

The first is a statement about service. The second is a statement about this
device. Conflating them would invent a certainty the data does not support.

### Release mixing

Two rules, enforced structurally rather than checked afterwards:

- `PackStore` keeps only one release id in `current/`. Adopting a different
  release archives the whole installed one into `previous/` first, which is also
  what makes a single-release rollback possible.
- `ReleaseLoader` reads the release id declared inside every pack and refuses the
  whole release if any of them disagrees with the manifest.

A live answer whose release id differs from the installed one is refused rather
than merged.

## What the core must not do

It does not scrape operators, store credentials, decide source rights, invent a
route or a service calendar, or treat a booking observation as a timetable.

## Tests

`commonTest` runs on the JVM (through `androidUnitTest`) and on the iOS
simulator, from the same sources:

- contract decoding against the canonical release
- overnight service dates, and the 2026-03-29 and 2026-10-25 Greek clock changes
- SHA-256 against the standard's vectors, including the one-million-character one
- a corrupted pack that is still valid JSON, refused on its digest alone
- a pack from another release refused even though its own digest is valid
- release mismatch, missing packs, and a day pack that covers the wrong date
- bounded retry, resume with a `Range` request, and cancellation that installs
  nothing
- saved-trip persistence and the version 1 to 2 schema migration
- "no offline data for this date" kept distinct from "no service on this date"
- the same query answered from a bundled pack and from a downloaded pack

Fixtures are not kept beside the tests. They are compiled in from the one
canonical release in `artifacts/releases/<slug>/`, so a contract change breaks the
tests instead of silently passing:

```
PY=.venv/bin/python bash scripts/api-seed-demo.sh
./gradlew :shared:core:allTests
```
