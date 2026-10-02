# Credential activation and real-data audit

Verified 2 October 2026. This records the current findings rather than repeating
the earlier assumption that the owner has no usable release credentials.

## Credentials configured

With the owner's explicit permission, reusable credentials from the separate
Syrmos project were validated on an isolated temporary workflow branch and
encrypted for this repository's environment public keys. No plaintext secret
was returned in logs, artifacts, or local output. The temporary branch and the
encrypted transfer artifacts were deleted after installation; source `main`
and its release configuration were not changed.

- `ios-beta`: all seven required secrets installed. App Store Connect API
  authentication succeeded. The existing distribution certificate and private
  key were valid; the certificate expires on 9 July 2027.
- Registered `dev.peterdsp.odivrelo`, enabled Associated Domains to match the
  existing app entitlement, and created a new Odivrelo-specific App Store
  provisioning profile using the existing distribution certificate.
- `android-beta`: all five required secrets installed, plus the public
  `ANDROID_RELEASE_CERT_SHA256` variable. The existing upload keystore opened
  successfully and the service account obtained a Google API access token.
- No existing signing identity was revoked or replaced. No private key or
  password was committed.

## Remaining store account state

- Apple API returned no Odivrelo App Store Connect app record. Bundle
  registration and provisioning are now done; creating the store app record
  is the next separate step. Developer enrolment and fresh API-key generation
  are not established blockers for this owner.
- The authenticated Google Play API returned HTTP 404 for
  `dev.peterdsp.odivrelo`. Confirm its app record/initial upload and access for
  the reused service account in Play Console. A valid service-account token
  does not establish access to a particular app.
- Store availability is not implied by credentials, profile creation, or a
  signed artifact. It still requires the matching record, successful upload,
  processing, and the intended test-track/group availability.

The iOS build configuration set `CODE_SIGNING_ALLOWED=NO` and
`CODE_SIGNING_REQUIRED=NO`. The release workflow now overrides both to `YES`,
verifies the exported app signature, and retains its IPA with a digest before
upload. An explicit `upload=false` run can prove signing independently of a
missing store app record; its summary must not claim TestFlight availability.

## Why the public product still has no real timetable

There are both data requirements and unfinished integration work:

1. All checked release workflows rebuild the invented Aloria dataset.
   `deploy-web.yml` calls `ci-pipeline-smoke.sh`, which calls
   `api-seed-demo.sh`; the mobile release workflows also call the demo seeder.
   Adding store secrets cannot change that selection.
2. The normalized importer and reviewed compiler exist, but service-calendar
   import still lives in `publicapi/demo_seed.py`, and there is no equivalent
   documented, verified end-to-end real-source release job in the current
   deployment workflows. Real delivery needs its own reviewed-input path,
   contract checks, release selection and runtime verification.
3. No current real corridor is imported, reviewed and published. The existing
   NAP evidence records a licensed but old city-level workbook, not a usable
   current departure/boarding-point feed. Operator-source permission and
   current service/boarding evidence remain unestablished for publication.
4. The demo seeder previously accepted `data_mode="real"`. This is now rejected
   before any database or release artifact is created, with regression tests.
   Merely changing a mode flag must never relabel fictional travel data.

Thus, "only external blockers; no code change needed" was too strong. Neither
credential migration nor that claim supplies a real-data release.

## Additional source checks

- The official [KTEL Fokida site](https://www.ktel-fokidas.gr/) exposes an
  Athens–Delphi timetable link, office locations and an official purchase
  handoff. An office address is not automatically a verified boarding bay;
  the page's availability is not documented redistribution permission or
  proof of a current effective service period. No timetable was imported or
  republished by this audit.
- The [MobilityDatabase FAQ](https://mobilitydatabase.org/faq) links to a
  publicly accessible [catalog CSV](https://files.mobilitydatabase.org/feeds_v2.csv).
  An account is not needed to inspect this catalog. Its five Greek entries at
  this check were rail/OASA entries (`mdb-1161`, `mdb-1228`, `mdb-3220`,
  `mdb-3221`, `tld-711`); none identified an intercity KTEL feed. This closes
  the previous catalog-access uncertainty, not a universal claim that no
  reusable KTEL data exists anywhere.

## Real-data delivery work that remains

Choose one corridor and establish the allowed scope of its source, verify its
current service dates and exact boarding locations, and retain the evidence
required by `DATA_GOVERNANCE.md`. Normalize its schedules and calendar
exceptions as candidates; obtain the required review without silently
auto-approving them. Build an immutable real-data release through a separate
reviewed-input path. Make deployment select that exact release rather than
reseed the demonstration. Test the actual journey and its offline copy before
changing any public coverage claim.

Requests to operators have not been sent: this task did not authorize external
messages. Existing rights and review gates were not weakened to manufacture
coverage. The current website therefore remains a labelled demo.
