# Odivrelo Android application

The native Android client. Jetpack Compose, Material 3, one activity, no
fragments, no XML layouts.

Everything about services, dates, midnight crossings, filters and pack
integrity lives in `shared/core`. This module renders it and never recomputes
it.

## Building

There is no system Java on the development machine, so every Gradle call needs a
JDK on the path:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew :apps:android:assembleDebug
./gradlew :apps:android:assembleRelease     # unsigned on purpose, see SIGNING.md
./gradlew :apps:android:bundleRelease       # unsigned on purpose
./gradlew :apps:android:assembleReleaseSmoke  # release config, debug-signed
```

The bundled data release is build output, not source. Regenerate it with:

```sh
PY=.venv/bin/python bash scripts/api-seed-demo.sh
bash scripts/android-sync-packs.sh
```

`scripts/android-app-verify.sh` runs the whole gate: unit tests, the three
builds, and the package identity, permission, localisation and shrinking checks
against the produced artifacts.

## Testing

```sh
./gradlew :apps:android:testDebugUnitTest          # JVM and Robolectric
./gradlew :apps:android:connectedDebugAndroidTest  # Compose, on a device
```

The unit tests include the two gates that are not negotiable:

- `LocalisationCompletenessTest` fails when Greek, English or Albanian is
  missing a key, carries a blank value, or disagrees about format arguments.
- `MissingPackIsNotMissingServiceTest` fails if "no offline data for this date"
  and "no service on this date" ever collapse into the same sentence, in any of
  the three languages.

The instrumented suite exercises the same distinction end to end against the
release the application actually bundles: today's date has no journeys pack in
the demonstration release, and the search must say so without claiming anything
about service.

## Architecture

```
MainActivity            one activity, no configChanges, resizeable
 └ OdivreloApp           theme, window measurement, first run
    └ OdivreloShell      navigation bar or rail, deep links, lifecycle
       └ OdivreloNavHost every destination, and the one place geometry
                        changes navigation
```

`OdivreloViewModel` holds the whole application state in one value and is the
only thing that writes it. That is what makes the adaptive requirement provable:
one pane and two panes read the same state, so folding cannot leave a detail
pane showing a journey the list no longer has. The session half of the state
(query, service date, filters, selected journey, scroll offset, open ticket) is
mirrored into `SavedStateHandle` on every change and therefore survives process
death.

`OdivreloServices` owns the single `OdivreloCore` instance and rebuilds it only
when the language changes, so two cores never contend for one database file.

## Deliberate choices worth knowing about

- **No dynamic colour.** A "stale" chip that borrows its hue from a wallpaper
  stops meaning stale.
- **No `configChanges`.** Rotation, resize and folding all recreate the
  activity. That is the harder path, and choosing it means the restoration code
  is exercised constantly rather than only when the system kills the process.
- **`allowBackup` is false.** The wallet can hold an imported travel document
  encrypted with a Keystore key that cannot leave the device. A backup would
  carry ciphertext nothing can ever decrypt while still copying a travel
  document into cloud storage the person did not choose. See the comment in
  `AndroidManifest.xml`.
- **`autoVerify` is off** on the https intent filter, because
  `assetlinks.json` cannot be published before a signing identity exists. See
  `SIGNING.md`.
- **No `androidx.startup` provider.** It is removed in the manifest, and
  WorkManager is configured by hand in `OdivreloApplication`, so nothing runs
  arbitrary library initialisers at process start.
- **No offline map tiles.** The application says so on the offline screen and
  hands coordinates to the device's own maps application instead of drawing a
  map it cannot draw without a connection.
- **Only scheduled data.** There is no live vehicle position in this release and
  nothing animates one.
