# Owner setup: the account-only steps for mobile beta distribution

> **Credential activation, 2 October 2026:** All seven `ios-beta` secrets,
> all five `android-beta` secrets, and the Android certificate fingerprint are
> now installed and validated using the owner's existing release setup. The
> Odivrelo Apple bundle ID, Associated Domains capability, and distribution
> profile have also been created. Do not ask the owner to enrol again or
> recreate these credentials. The remaining account checks are an Odivrelo
> App Store Connect app record and Google Play app-record/access setup.
> See [the verified activation report](CREDENTIAL-AND-DATA-STATUS.md).
> The provisioning checklist below is retained as a setup reference, not a
> claim that its already-completed steps are still missing.


Last updated: 2 October 2026.

## What is already in place

- The release workflows exist and are complete:
  [`.github/workflows/release-ios.yml`](../../.github/workflows/release-ios.yml)
  and
  [`.github/workflows/release-android.yml`](../../.github/workflows/release-android.yml).
  Both are manual-dispatch only, so credentials are never exposed to
  pull-request code. Each validates its required secrets by name before any
  expensive build, builds and signs, verifies the signature, uploads, writes
  checksums and a summary, and cleans up all signing material even on failure.
- The GitHub environments `ios-beta` and `android-beta` exist. The secrets below
  go into the matching environment, not into repository-wide secrets.
- Android signing is wired: Gradle reads `keystore.properties`, the version code
  is overridable with `ODIVRELO_VERSION_CODE`, and
  `scripts/android-app-verify.sh` runs an authenticated signature check when
  `ANDROID_RELEASE_CERT_SHA256` is set.
- iOS signing is wired: the workflow imports the certificate and profile into a
  throwaway keychain, archives with manual signing, exports an IPA and uploads
  with the App Store Connect API key. The build number is overridable with
  `CURRENT_PROJECT_VERSION`.

## How to install a secret without putting it in chat or a commit

Never paste a secret into chat, a commit, a log, an artifact or a command line.
Install each one from a file with the GitHub CLI, which reads the value over
stdin and never prints it:

```bash
# From a file on your machine:
gh secret set NAME --env ios-beta < /path/to/value
# Base64 ones: produce the file first, then set it, then delete the file.
base64 -i distribution.p12 -o dist.p12.b64
gh secret set IOS_DISTRIBUTION_P12_BASE64 --env ios-beta < dist.p12.b64
rm -f dist.p12.b64
```

A repository or environment **variable** (not a secret) is for a non-secret
value such as a certificate fingerprint:

```bash
gh variable set ANDROID_RELEASE_CERT_SHA256 --env android-beta --body "<sha256>"
```

---

## iOS: TestFlight

Unblocks: [`release-ios.yml`](../../.github/workflows/release-ios.yml).

### Owner-only account steps (cannot be automated)

1. **Apple Developer Program enrolment.** Needs the account holder, Apple ID
   two-factor, identity verification and the paid membership. Minimum role to
   mint the items below: **Admin** or **Account Holder**.
2. **Register the bundle identifier** `dev.peterdsp.odivrelo` under
   Certificates, Identifiers & Profiles. It is now registered; preserve the existing identifier.
3. **Create the App Store Connect app record** for that bundle id. Set the
   primary language and the beta app information. This is the record TestFlight
   builds attach to.
4. **Accept the current Apple agreements** (Program License Agreement, and the
   relevant tax and banking only if required for your case). These cannot be
   accepted on your behalf.
5. The export-compliance and beta-review answers are declarations only the
   account holder can make. The workflow proves upload acceptance; it does not
   answer these.

### The seven secrets, into the `ios-beta` environment

| Secret | What it is | Where to get it | Min role |
|---|---|---|---|
| `APP_STORE_CONNECT_KEY_ID` | API key identifier | App Store Connect, Users and Access, Integrations, App Store Connect API, create a Team key | Admin |
| `APP_STORE_CONNECT_ISSUER_ID` | Issuer id for that key | Same page, shown above the key list | Admin |
| `APP_STORE_CONNECT_PRIVATE_KEY` | The `.p8` contents | Downloaded once when the key is created; keep the file, set it as the secret | Admin |
| `APPLE_TEAM_ID` | Ten-character team id | Apple Developer, Membership details | Any member |
| `IOS_DISTRIBUTION_P12_BASE64` | Distribution certificate plus private key, exported `.p12`, base64 | Keychain Access, export your Apple Distribution identity as `.p12`, then base64 | Admin |
| `IOS_DISTRIBUTION_P12_PASSWORD` | Password for that `.p12` | The password you set on export | Admin |
| `IOS_PROVISIONING_PROFILE_BASE64` | App Store distribution profile for the bundle id, base64 | Certificates, Identifiers & Profiles, Profiles, App Store, download and base64 | Admin |

### Then

```bash
gh workflow run release-ios.yml --ref <trusted-commit-or-branch>
```

The job validates the secrets, archives, exports, validates and uploads. After
it is green, confirm in App Store Connect: processing completion, export
compliance, and adding the build to the existing internal TestFlight group.
Those are deliberately separate states from upload acceptance.

---

## Android: Play internal track

Unblocks: [`release-android.yml`](../../.github/workflows/release-android.yml).

### Owner-only account steps (cannot be automated)

1. **Google Play Console account** and its one-time registration and identity
   verification. Minimum role on the app: **Admin** (to grant API access and to
   create the internal track).
2. **Create the Play app record** for `dev.peterdsp.odivrelo` and complete the
   first-time app setup and content declarations Play requires before any
   upload. The API cannot skip console onboarding.
3. **Play App Signing.** When the app is created, Play App Signing is enabled by
   default: you upload with an **upload key**, and Play re-signs with the app
   signing key. Keep the upload key; it is the one the workflow signs with.
4. **Enable the Google Play Android Developer API** and create a **service
   account**. In the Play Console, grant that service account **app-specific**
   permission for internal testing releases only. Cloud IAM alone is not Play
   authorization. Do not grant financial, billing, or production-release access.

### The upload key

Reuse the registered upload key if one exists; do not replace it silently. If
this is a genuinely new app with no registered key, generate a dedicated upload
keystore once, store it securely, and record a recoverable backup location
before first use (it is authorised, but it must be protected, never committed,
and never regenerated per run):

```bash
keytool -genkeypair -v -keystore upload.jks -alias odivrelo-upload \
  -keyalg RSA -keysize 2048 -validity 10000
# Record the certificate fingerprint for the gate and for App Links:
keytool -list -v -keystore upload.jks -alias odivrelo-upload | grep 'SHA-256:'
```

### The secrets and the variable, into the `android-beta` environment

| Name | Kind | What it is | Min role |
|---|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | secret | The upload keystore, base64 | app Admin |
| `ANDROID_KEYSTORE_PASSWORD` | secret | Keystore password | app Admin |
| `ANDROID_KEY_ALIAS` | secret | Key alias (`odivrelo-upload` above) | app Admin |
| `ANDROID_KEY_PASSWORD` | secret | Key password | app Admin |
| `PLAY_SERVICE_ACCOUNT_JSON` | secret | Service account JSON key | app Admin |
| `ANDROID_RELEASE_CERT_SHA256` | variable | SHA-256 of the **upload** certificate (public, not a secret) | app Admin |

### App Links

Android App Links must list the certificate of the **actual distribution
channel**. For a Play-distributed install, that is the **Play app signing
certificate** (from the Play Console, App integrity), not the upload key. Add
its SHA-256 to the Digital Asset Links file served at
`https://odivrelo.peterdsp.dev/.well-known/assetlinks.json` before relying on
verified App Links for Play installs. A locally release-signed build verifies
against the upload-key fingerprint instead.

### Then

```bash
gh workflow run release-android.yml --ref <trusted-commit-or-branch>
```

The job validates, builds the signed AAB with a unique version code, runs the
authenticated signature check, uploads to the internal track as a completed
release, and writes the checksums. After it is green, confirm the build is
available to the internal testing group in the Play Console.

---

## Hardening to apply once (optional but recommended)

- Pin the Play publisher action in `release-android.yml` to a commit SHA after
  verifying it, rather than the `v1.1.3` tag.
- Add a required reviewer to the `ios-beta` and `android-beta` environments so a
  release needs a human approval, and restrict each environment to the protected
  branch or the release tags.
