# Build, sign, deploy, verify, roll back

Exact, repeatable steps. Secret **names** only; no value appears in this
repository or in any log.

Version `1.0.0`. Data contract `1.0.0`. Data mode `demo`.

---

## 0. Prerequisites

| Tool | Version | Note |
|---|---|---|
| Python | 3.12 | `python3.12 -m venv .venv` at the repository root |
| Node | 24 | npm 11 |
| JDK | 21 | `export JAVA_HOME=/opt/homebrew/opt/openjdk@21` — there is no system Java on macOS, so a bare `java` fails |
| Android SDK | platforms 34 and 36, build-tools 36 | `~/Library/Android/sdk`; write `local.properties` with `sdk.dir` |
| Xcode | 27.0, iOS 27 SDK, Swift 6.4 | |

```bash
cd /Users/peterdsp/git/Poravia
python3.12 -m venv .venv
.venv/bin/pip install -r server/api/requirements.txt -r server/api/requirements-dev.txt
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"
```

---

## 1. Build the data release first

Everything else consumes it, so it goes first.

```bash
bash scripts/ci-pipeline-smoke.sh
```

That runs `scripts/api-seed-demo.sh` (migrate, seed, import the Aloria
fixtures, insert calendars and exceptions, review, compile, generate packs and
GTFS, write the manifest last), then asserts independently that the manifest
verifies, that every pack is content addressed, that no withheld row leaked
into a public pack, that the GTFS feed carries the release id and its
past-midnight times, and that the dataset labels itself as demonstration data.
Finally it regenerates the artifact manifest.

Output lands in the gitignored `artifacts/`:

```
artifacts/ingest.db                      ingestion and review database
artifacts/public.db                      compiled read-only public database
artifacts/public-previous.db             the previous release, for rollback
artifacts/releases/poravia/manifest.json written last
artifacts/releases/poravia/packs/…       content-addressed packs plus GTFS
artifacts/ARTIFACT-MANIFEST.json         machine-readable artifact manifest
```

**Release rollback (data):**

```bash
cd server/ktel-staging && PYTHONPATH=. ../../.venv/bin/python -c "
from poravia_ktel import ktel_release
print(ktel_release.rollback_release('../../artifacts/releases/poravia'))"
```

Packs are immutable, so nothing is deleted and the restored manifest still
verifies. To roll the database back as well, move `public-previous.db` over
`public.db` and restart the service.

---

## 2. Backend service

```bash
export PORAVIA_PUBLIC_DB_PATH="$PWD/artifacts/public.db"
export PORAVIA_RELEASE_DIR="$PWD/artifacts/releases/poravia"
export PORAVIA_DATA_MODE=demo
bash scripts/api-run.sh                    # uvicorn, see the script for workers
.venv/bin/pytest server/api/tests -q       # tests
```

Health: `GET /healthz` is liveness. `GET /readyz` fails when the public
database is missing, carries no published release, or disagrees with the
manifest. Treat a failing `/readyz` as do-not-serve, not as a warning.

Administrative surface: set `ADMIN_ENABLED=1` and `ADMIN_API_TOKEN`. **Do not
mount it on the public origin.** With `ADMIN_ENABLED` unset the routes do not
exist at all.

Backup and restore:

```bash
bash scripts/api-backup.sh                 # snapshot database plus manifest
bash scripts/api-backup.sh --restore <dir> # restore; asserts an identical release id
```

Secret names: `ADMIN_API_TOKEN`. No value is stored in the repository.

---

## 3. Web, and the `1.0.0` public release

```bash
bash scripts/web-sync-packs.sh artifacts/releases/poravia   # packs into public/data
cd apps/web
npm ci
npm run lint && npm run typecheck && npm run test -- --run
npm run build            # -> apps/web/dist
npx playwright install --with-deps chromium firefox webkit
npm run test:e2e
cd ../..
bash scripts/check-brand.sh --dist apps/web/dist
```

The build is stamped with `VITE_APP_VERSION`, `VITE_GIT_COMMIT`,
`VITE_BUILD_TIME` and the data release id, all four visible in Settings.

`dist` includes `CNAME` (`poravia.peterdsp.dev`), `.nojekyll`, `404.html` for
deep-link fallback on static hosting, `robots.txt`, `manifest.webmanifest` and
`data/` with the release packs.

### Deploying

Deployment runs only from a release tag or a manual dispatch, never from a pull
request, so untrusted code cannot reach the credentials.

```bash
git tag -a v1.0.0 -m "Poravia 1.0.0 beta" && git push origin v1.0.0
# or:
gh workflow run deploy-web.yml -f reason="1.0.0 beta"
```

The workflow enables the Pages site on first run, builds, publishes, then runs
`scripts/verify-deployment.sh https://poravia.peterdsp.dev`, which checks DNS,
HTTPS, the HTTP redirect, the homepage, the release manifest, three deep-link
loads and the PWA files, and **fails the job** rather than reporting a green
deployment that is not live.

### The DNS record this needs, which does not exist yet

`poravia.peterdsp.dev` does not resolve. This is **EB-01** and it is the one
thing standing between this and a verified `1.0.0`.

```
Zone:    peterdsp.dev  (Cloudflare)
Type:    CNAME
Name:    poravia
Target:  peterdsp.github.io
Proxy:   Proxied, matching every sibling subdomain
TTL:     Auto
```

Then:

```bash
gh api -X PUT repos/peterdsp/Poravia/pages -f cname=poravia.peterdsp.dev
gh api -X POST repos/peterdsp/Poravia/pages/https_certificate
bash scripts/verify-deployment.sh https://poravia.peterdsp.dev
```

### Rollback

Re-run the deploy workflow from the previous tag, or re-publish the previous
Pages deployment from the repository's deployments view. Packs are content
addressed, so a rolled-back site keeps serving valid data.

---

## 4. Shared core

```bash
bash scripts/shared-build-xcframework.sh          # PoraviaCore.xcframework
./gradlew :shared:core:allTests --no-daemon
```

The XCFramework lands under `shared/core/build/XCFramework/` and is what the
iOS target links. Building it is a prerequisite for any iOS build.

---

## 5. Android

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew :apps:android:testDebugUnitTest
./gradlew :apps:android:assembleDebug        # QA APK
./gradlew :apps:android:assembleRelease      # release APK
./gradlew :apps:android:bundleRelease        # AAB for Play
```

Validate before shipping: application id `dev.peterdsp.poravia`, manifest,
declared permissions, min and target SDK against current policy, a version code
that has never been uploaded before, native library ABIs, all three
localisations present, resource shrinking behaviour, and the mapping file.
Smoke-test the **release** configuration, not only debug.

### Signing, currently blocked, EB-03

There is no release keystore. The release outputs are produced unsigned and
labelled as such. **No disposable production signing identity was created**,
and a debug key is never used for store distribution.

When a keystore exists, these secret **names** are expected, values never
stored here:

```
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
PLAY_SERVICE_ACCOUNT_JSON
```

Then sign, verify and upload:

```bash
./gradlew :apps:android:bundleRelease
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --verbose <aab-or-apk>
# upload to the internal test track with the Play service account
```

`uploaded` is not `available-to-testers`. Confirm internal-track availability
before claiming either.

---

## 6. iOS and iPadOS

```bash
bash scripts/shared-build-xcframework.sh
xcodebuild -project apps/ios/Poravia.xcodeproj -scheme Poravia \
  -destination 'platform=iOS Simulator,name=iPhone 18 Pro Max' build
xcodebuild -project apps/ios/Poravia.xcodeproj -scheme Poravia \
  -destination 'platform=iOS Simulator,name=iPhone 18 Pro Max' test
xcodebuild -project apps/ios/Poravia.xcodeproj -scheme Poravia \
  -configuration Release -destination 'generic/platform=iOS' \
  -archivePath artifacts/ios/Poravia.xcarchive archive
```

Validate: entitlements, usage descriptions, `PrivacyInfo.xcprivacy` against the
APIs the code actually calls, bundled resources, all three localisations, the
linked `PoraviaCore` slices and symbols, bundle identity
`dev.peterdsp.poravia`, and the export options.

### Signing, currently blocked, EB-02

No Apple team, certificate, profile or App Store Connect key is configured, and
the bundle identifier is not registered. **Nothing was invented to work around
that.** A simulator build is delivered and is explicitly **not** an installable
iPhone beta.

When access exists:

```bash
# 1. register dev.peterdsp.poravia and create the App Store Connect record
# 2. export with the real team id
xcodebuild -exportArchive -archivePath artifacts/ios/Poravia.xcarchive \
  -exportOptionsPlist apps/ios/ExportOptions.plist \
  -exportPath artifacts/ios/export
# 3. upload
xcrun altool --upload-app -f artifacts/ios/export/Poravia.ipa -t ios \
  --apiKey "$APP_STORE_CONNECT_KEY_ID" --apiIssuer "$APP_STORE_CONNECT_ISSUER_ID"
```

Secret names: `APP_STORE_CONNECT_KEY_ID`, `APP_STORE_CONNECT_ISSUER_ID`,
`APP_STORE_CONNECT_PRIVATE_KEY`, `APPLE_TEAM_ID`.

`uploaded`, `processed`, `available-to-testers` and `submitted for external
review` are four different states. Do not collapse them.

---

## 7. Associated domains and deep links

Both platforms' verified links need files served from the deployed site, so
they depend on EB-01.

- Apple: `apps/web/public/.well-known/apple-app-site-association`, served as
  `application/json` with no extension, listing `<TEAMID>.dev.peterdsp.poravia`.
  Until the team id exists, the entitlement is configured but unverified and
  the `poravia://` scheme carries deep links.
- Android: `apps/web/public/.well-known/assetlinks.json` with the release
  signing certificate's SHA-256 fingerprint. Until the keystore exists,
  `autoVerify` stays off and the `poravia://` scheme carries deep links.

Both files must be published **before** claiming verified universal or app
links work.

---

## 8. Release checklist

1. `bash scripts/ci-pipeline-smoke.sh` passes.
2. `cd server/ktel-staging && PYTHONPATH=. ../../.venv/bin/python -m unittest discover -s tests` passes.
3. `.venv/bin/pytest server/api/tests` passes.
4. `bash scripts/gen-contracts.sh --check` reports no contract drift.
5. `bash scripts/check-brand.sh` and `bash scripts/check-brand.sh --dist apps/web/dist` are both clean.
6. Web, Android and iOS builds and test suites pass, **rebuilt after the final
   change**. Evidence from an older build must not be attached to a newer one.
7. `scripts/artifact-manifest.py` regenerated, and every path in it exists.
8. `TEST-MATRIX.md` has no `not-run` row that a gate depends on.
9. `RELEASE-READINESS.md` reflects reality, including the open gates.
10. Deploy, then `scripts/verify-deployment.sh` passes against the live host.

---

## 9. What must never happen

- Committing a `.db`, `-wal`, `-shm`, keystore, signing key, ticket file or any
  secret value. `.gitignore` covers these and PHASE0-04 audits it.
- Reusing an uploaded build number, or overwriting an existing release.
- Shipping with `dataMode` accidentally set to `real` while the data is still
  the Aloria fixture.
- Submitting to either store while `dataMode` is `demo`.
- Calling an unsigned build, a simulator app, a preview URL or `localhost` a
  deployed release.
