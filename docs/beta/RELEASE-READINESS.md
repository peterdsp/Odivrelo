# Release readiness, 1.0.0 beta

Each gate is assessed separately. A gate is `pass` only with evidence a reader
can check. `blocked` means an external prerequisite is missing, not that the
work was skipped; see `EXTERNAL-BLOCKERS.md`.

Last updated: 30 September 2026.

> **Rows marked `pending` are not yet claimed.** The client applications are
> still being built and verified as this is written. A row moves to `pass` only
> when a matching row exists in `TEST-MATRIX.md` with a command, an artifact and
> a build commit behind it. Nothing here is a pass by assumption.

---

## 1. Product gates

| Gate | State | Evidence |
|---|---|---|
| Works with no account and no mandatory permissions | pending | first-launch flow on all three clients, `TEST-MATRIX.md` §1 |
| Greek, English and Albanian complete everywhere | pending | per-platform catalogue-completeness tests fail on any missing key |
| Search, disambiguation, service date, results, empty and error states | pending | `TEST-MATRIX.md` §2 |
| Journey detail with boarding point, provenance, freshness, confidence | pending | `TEST-MATRIX.md` §2 |
| Official booking handoff, and a verified contact fallback where there is no online sale | pending | `TEST-MATRIX.md` §2 |
| Product never implies it sells or issues tickets | pending | stated on the first-launch screen, every journey detail and every purchase surface |
| Offline packs: integrity, atomic install, interrupted-download recovery, update, rollback, delete | pending | `TEST-MATRIX.md` §3 and §4 |
| Saved trips and Trip Ready usable offline with a visible cached release and age | pending | `TEST-MATRIX.md` §3 |
| Travel wallet: local only, never uploaded, never logged, never in a shared cache, deletion really deletes | pending | `TEST-MATRIX.md` §6 |
| Reminders opt-in, no passenger or barcode data in any payload, permission-denied handled | pending | `TEST-MATRIX.md` §7 |
| Every visible control does something or explains its genuine unavailability | pending | no TODO handler, fake success or unconditional mock result ships |
| Scheduled, predicted, estimated and live kept visibly distinct | pending | only `scheduled` is present in this release, and the clients say so |
| Accessibility: screen reader, focus order, large text, keyboard, contrast, reduced motion, non-map route | pending | `TEST-MATRIX.md` §10 |
| Adaptive layout: iPhone Duo, Android foldables, tablets, resizing, state preservation | pending | `TEST-MATRIX.md` §9; Duo runtime coverage is reported exactly as far as the installed runtime allowed |

## 2. Data gates

| Gate | State | Evidence |
|---|---|---|
| Only rights-cleared sources reach a public release | pass | the compiler joins on `rights_status = 'allowed'`; a test proves a `permission_pending` row stays out **even after a reviewer approves it** |
| Only reviewed entities reach a public release | pass | a test proves an unreviewed candidate never appears in any public payload |
| Candidate and public datasets stay separate | pass | two databases; public routes open the compiled database read-only and never the ingestion database |
| Releases are immutable, checksummed, and the manifest is written last | pass | content-addressed packs, digest and size verification, corrupt-pack and missing-pack tests |
| Rollback is available and the rolled-back release still verifies | pass | one previous manifest retained; rollback test |
| A mixed or inconsistent release is refused | pass | the generator refuses a compiled identity that disagrees with the public database; `/readyz` fails on the same condition |
| `Europe/Athens` service dates, midnight crossings, GTFS times past 24:00, DST | pass | resolved once server-side; both 2026 transitions tested |
| Invalid coordinates, broken references, duplicates, malformed input quarantined | pass | coordinate quarantine test; `PRAGMA foreign_key_check` on every compile |
| Corridor rights, freshness, completeness and boarding points verified | **fail** | `docs/phase0/NAP-DATASET-EVIDENCE.md`. Pilot Gate D1 fails on evidence. |
| Real-data beta readiness | **fail** | the release ships a labelled invented dataset; see EB-04 |
| National coverage | **not claimed** | this is a limited-coverage demonstration beta, and every client says so |

**The data mode is `demo`.** That is a first-class field in the contract, not a
footnote. Flipping it to `real` requires EB-04, not a code change.

## 3. Operational gates

| Gate | State | Evidence |
|---|---|---|
| Reproducible service packaging | pass | pinned dependencies; `server/api/README.md` |
| Health and readiness checks | pass | `/healthz` liveness; `/readyz` fails on a missing database, no published release, or a release/manifest disagreement |
| Environment validation at startup | pass | fails fast listing the missing or invalid variables |
| Bounded, secret-free logging | pass | structured JSON, capped message size, no tokens, no request bodies |
| Backup and restore verified | pass | restore test asserts an identical release id |
| Rollback procedure verified | pass | `rollback_release`, then re-verification |
| Private administrative surface isolated and authenticated | pass | separate router, bearer token required, `Cache-Control: no-store`, absent entirely when disabled |
| Production acquisition deployment on the Raspberry Pi | **blocked** | EB-07; a locally runnable equivalent and the updated deploy script are delivered |

## 4. Release and distribution gates

| Platform | Build | Runtime | Package | Sign | Upload | Testers |
|---|---|---|---|---|---|---|
| Web and PWA | passed | passed, less Firefox (W18) | passed | n/a | **blocked**, EB-01 | **blocked**, EB-01 |
| iOS and iPadOS | passed | **partial**, see I12 | passed | **blocked**, EB-02 | **blocked**, EB-02 | **blocked**, EB-02 |
| Android | pending | pending | pending | **blocked**, EB-03 | **blocked**, EB-03 | **blocked**, EB-03 |
| Backend service | passed | passed | passed | n/a | local and packaged | n/a |

Detail per row, with commands, artifacts and limitations, is in
`TEST-MATRIX.md` and the artifact manifest.

## 5. The `1.0.0` Web release gate

**Status: satisfied, 1 October 2026.**

`https://odivrelo.peterdsp.dev` is live on Cloudflare Pages with a valid
certificate, and the release workflow's live verification passed every check
(DNS, HTTPS, redirect, homepage identity, release manifest, six deep links,
404 handling, PWA files) in
https://github.com/peterdsp/Odivrelo/actions/runs/36779339890. EB-01 is
resolved.

## 6. Legal and account gates

| Gate | State | Note |
|---|---|---|
| Source licence recorded per source | pass | registry plus the dated NAP evidence |
| ODbL share-alike obligation assessed | **needs human legal review** | engineering assessment only; not legal advice |
| Privacy and data-safety declarations | prepared, **not certified** | derived from an inventory of the actual code, SDKs, permissions, storage and network behaviour. No declaration was answered on the account holder's behalf. |
| App Store and Play legal agreements | **not accepted** | nobody's agreement was accepted on their behalf |
| Trademark clearance | **fail** | no register could be searched; see EB-06 |
| Native-speaker review of the name | **not done** | see EB-06 |
| Operator permission for real data | **not obtained** | no outreach was sent; drafting is prepared, sending is out of scope |

## 7. Research gates

| Gate | State |
|---|---|
| Gate D1, corridor data check | **fail**, on evidence |
| Gate D2, five-traveller validation | **not run**; requires recruiting and observing people |

Neither gate is passed by engineering scope expanding, and nothing in this
release claims otherwise.

---

## Overall

**Client engineering is in progress. Distribution is blocked on every platform,
and the `1.0.0` Web release gate is open.** This section is rewritten with the
actual per-platform outcome once `TEST-MATRIX.md` is complete.

Odivrelo is not a validated product, not a national dataset, and not installable
by a tester today. It is a working, verified, honest beta whose remaining gaps
are each written down with the exact action that would close them.
