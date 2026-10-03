# External blockers

Only blockers that were actually hit. No speculation. Each entry records what
is missing, what it blocks, what was attempted, what is already prepared, and
the precise action that would unblock it.

Last updated: 1 October 2026. On that date the product was renamed from Poravia
to Odivrelo (AD-011). Entries that describe current actions use the new
identifiers; history inside each entry is kept as it was written.

---

## EB-01: Cloudflare DNS record for `poravia.peterdsp.dev`

> **Superseded by the rename, 1 October 2026.** The product is now served at
> `odivrelo.peterdsp.dev` from the Cloudflare Pages project `odivrelo`. The owner
> added the record (`odivrelo` CNAME to `odivrelo.pages.dev`, proxied) and
> attached the custom domain; no new blocker was needed. The `poravia` project
> and its record are left in place, still serving the last Poravia build. The
> entry below is the Poravia history, kept as written.

- **Affected capability:** the public Web `1.0.0` release gate. This is the
  one gate that cannot be closed without it.
- **State:** resolved, 1 October 2026. The owner added the record
  (`poravia` CNAME to `poravia.pages.dev`, proxied) and the Cloudflare secrets.
  The release workflow published through CI and `verify-deployment.sh
  https://poravia.peterdsp.dev` passed every check in run
  https://github.com/peterdsp/Poravia/actions/runs/36779339890. The history
  below is kept as it was written.

**What is missing.** `peterdsp.dev` is on Cloudflare nameservers
(`ganz.ns.cloudflare.com`, `itzel.ns.cloudflare.com`). Every sibling project
follows the same pattern: a per-subdomain DNS record proxied through Cloudflare
to a GitHub Pages origin. There is no wildcard record, which was verified by
resolving two random subdomains and getting `NXDOMAIN`. `poravia.peterdsp.dev`
does not resolve today.

No Cloudflare API token is present in the environment, and this session is not
permitted to go looking for credentials.

**What was attempted.**

- `dig +short poravia.peterdsp.dev` → no record.
- Confirmed the existing convention from three sibling repositories via the
  GitHub API: `klipa`, `glint` and `kujto` each have a Pages site with
  `cname: <name>.peterdsp.dev` and `protected_domain_state: verified`, so the
  apex domain is already verified on the account and only the record is
  missing.
- Confirmed no wildcard: `zzzrandomtest123.peterdsp.dev` and
  `qqqnope.peterdsp.dev` both fail to resolve.
- Searched the process environment for a Cloudflare token by variable name.
  None is set. A broader credential search was refused by policy and was not
  worked around.

**What is already prepared.** Everything except the record.

- The production Web artifact builds and is verified locally.
- `.github/workflows/deploy-web.yml` builds, publishes to GitHub Pages, and
  then runs the live verification.
- `scripts/verify-deployment.sh` checks DNS, HTTPS, the HTTP redirect, the
  homepage, the release manifest, deep-link loads and the PWA files, and fails
  loudly rather than reporting a green deployment that is not live.
- `apps/web/public/CNAME` contains `poravia.peterdsp.dev`.

**Interim deployment, 30 September 2026.** The same production artifact is
live on Cloudflare Pages at https://poravia.pages.dev (project `poravia` on the
owner's account, deployed with `npx wrangler pages deploy apps/web/dist
--project-name poravia --branch main`). `scripts/verify-deployment.sh
https://poravia.pages.dev` passes every check, including the full security
header set from `_headers`, which GitHub Pages cannot apply. The custom domain
is still unset, so this entry stays open until `poravia.peterdsp.dev` resolves.

**Exact action needed.** One of these, then re-run
`bash scripts/verify-deployment.sh https://poravia.peterdsp.dev`.

Option A, Cloudflare dashboard: add a DNS record on the `peterdsp.dev` zone.

```
Type:    CNAME
Name:    poravia
Target:  peterdsp.github.io
Proxy:   Proxied (orange cloud), matching the sibling subdomains
TTL:     Auto
```

Option B, API, with a token scoped to `Zone.DNS:Edit` on `peterdsp.dev`:

```bash
ZONE_ID=$(curl -s -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
  "https://api.cloudflare.com/client/v4/zones?name=peterdsp.dev" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["result"][0]["id"])')

curl -s -X POST -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
  -H "Content-Type: application/json" \
  "https://api.cloudflare.com/client/v4/zones/$ZONE_ID/dns_records" \
  -d '{"type":"CNAME","name":"poravia","content":"peterdsp.github.io","proxied":true,"ttl":1}'
```

Then, once it resolves:

```bash
gh api -X PUT repos/peterdsp/Poravia/pages -f cname=poravia.peterdsp.dev
gh api -X POST repos/peterdsp/Poravia/pages/https_certificate   # enforce HTTPS
```

**Not done instead.** No other hostname was substituted, no apex, mail,
wildcard or unrelated record was touched, nothing was purchased, and the
`1.0.0` Web gate is left explicitly open rather than marked ready.

---

## EB-02: App Store Connect app record and beta distribution

- **State:** credentials and provisioning resolved on 2 October 2026;
  store-record creation and actual distribution remain outstanding.
- All seven `ios-beta` secrets are installed. The existing Apple API key
  authenticated, the distribution certificate/private key are valid, and an
  Odivrelo-specific profile was created after registering
  `dev.peterdsp.odivrelo` and enabling Associated Domains.
- A read-only App Store Connect app lookup returned no matching app record.
  Create the Odivrelo iOS app record for the registered bundle ID, then upload
  and verify processing and internal TestFlight availability. Do not request
  replacement credentials or a new developer membership as a default remedy.
- See [the activation report](CREDENTIAL-AND-DATA-STATUS.md) for verified
  facts. Signed-build output is separate from tester installability.

---

## EB-03: Google Play app record and application access

- **State:** all five signing/publishing secrets and the upload certificate
  fingerprint installed on 2 October 2026; Play app access remains unresolved.
- The existing upload keystore validated and the existing Google service
  account obtained an access token. No replacement signing key was generated.
- The authenticated publisher API returned HTTP 404 for
  `dev.peterdsp.odivrelo`. Confirm the Play Console app record/initial upload
  and app-specific access for this service account. Authentication alone does
  not establish app access or an available test-track release.
- See [the activation report](CREDENTIAL-AND-DATA-STATUS.md). No claim of
  Play upload or tester availability follows merely from installing secrets.

---

## EB-04: Source reuse rights for any real Greek coach corridor

- **Affected capability:** a real-data beta. Pilot Gate D1.
- **State:** blocked, with the evidence now gathered rather than assumed.

**What is missing.** Documented reuse rights plus fresh, complete, boarding-
point-level data for at least one corridor.

**What was established on 30 September 2026** (full record in
[`docs/phase0/NAP-DATASET-EVIDENCE.md`](../phase0/NAP-DATASET-EVIDENCE.md)):

- The Greek National Access Point long-distance bus dataset is licensed
  **ODbL 1.0**. Rights are therefore **not** the blocker for this source.
- It was last updated **1 December 2020**.
- It contains **no boarding points, no coordinates and no stop identifiers**,
  only free-text city names, so the Athens Kifissos-versus-Liosion terminal
  question, which is the product's core promise, cannot be answered from it.
- **`ΔΕΛΦΟΙ` appears zero times**, so the chosen pilot corridor is absent.
- Calendars are unnormalised Greek prose with no validity period, and one sheet
  records "modified every week" where departure times should be.
- All 62 federation operator directory sources remain `rightsStatus: unknown`.

**Exact action needed**, in order:

1. Write to the corridor operators, KTEL Fokida and KTEL Livadeia or Thiva for
   the primary corridor, requesting written permission to reuse and republish
   their timetable and boarding-point data. A draft can be prepared; **no
   message was sent, because contacting people is out of scope.**
2. Verify exact boarding points and coordinates at the Athens terminal and the
   destination end from official sources (PILOT-04).
3. Verify a working official purchase or contact action per operator
   (PILOT-05).
4. Obtain a human legal read on the ODbL share-alike obligation before
   publishing any database derived from the NAP dataset.
5. Complete and verify the real-source release integration described in
   [the activation and data audit](CREDENTIAL-AND-DATA-STATUS.md), then deploy
   the reviewed release. A mode flip cannot replace this work.

**Re-checked 2 October 2026.** A fresh read-only discovery pass found no source
that is simultaneously rights-cleared, fresh, and boarding-point-level for even
one Greek intercity corridor. The authoritative NAP catalog still reports
`metadata_modified` 2020-12-01 and a single 2020 resource (re-verified via its
CKAN API); a `data.gov.gr` mirror is reported to carry newer 2021 and 2023
editions of the same shape, still without boarding points. The one artefact
found with boarding-point-level KTEL GTFS (a personal GitHub feed for Lefkada
and Kefalonia) carries no licence and no provenance, so it is not reusable. The
full result, and the discovered-to-blocked state of all 62 operators, is in
[`OPERATOR-COVERAGE-LEDGER.md`](OPERATOR-COVERAGE-LEDGER.md). This blocker is
unchanged.

---

## EB-05: The five-traveller validation, Gate D2

- **Affected capability:** the product claim that Odivrelo beats the operator's
  own site. Not an engineering gate.
- **State:** not started, and correctly so.

Gate D2 requires watching five likely travellers attempt the task. That needs
recruiting and observing people, which this delivery deliberately does not do.
**Engineering scope expanding does not pass this gate**, and nothing in this
release claims it does.

**Exact action needed.** Run PILOT-09 with five recruited travellers against a
real corridor, once EB-04 clears. A demonstration dataset cannot substitute.

---

## EB-06: Trademark search and native-speaker review for the name

- **Affected capability:** confidence in the Odivrelo name.
- **State:** partly closed. **Preliminary public collision screening for
  Odivrelo was performed on 2 October 2026** and found no material conflict:
  the exact term is used by no third-party product in accessible sources,
  `odivrelo.com` is unregistered, the GitHub handle is free, and no App Store
  app bears the name (DNS, GitHub and App Store results re-verified first-hand).
  Full dated record in `BRAND-DECISION.md`. What **remains external**: no
  trademark register (EUIPO, USPTO, WIPO, Greek OBI, Albanian DPPI) could be
  searched, all being CAPTCHA-gated or JavaScript-only, so registered-mark risk
  is unknown; and a native-speaker review in Greek, English and Albanian is
  still outstanding. No trademark ownership or clearance is claimed anywhere.

The record below is what was attempted for the previous name, Poravia. The
same register limitations will apply to Odivrelo.

**What was attempted and failed on 30 September 2026:** EUIPO eSearch plus,
TMview/TMDN, USPTO trademark search including its documented API, the WIPO
Global Brand Database, Justia Trademarks and uspto.report. All are JavaScript
single-page applications, proof-of-work gated, or returned HTTP 403 or 404 to
automated requests. **No trademark register was searched for any candidate.**

Not attempted at all: the Greek OBI register, the Albanian DPPI register, the
Greek GEMI and Albanian QKB company registers.

Also missing: native-speaker review. Albanian dictionary lookups for "poravia"
returned nothing, so the absence of a bad meaning is **unproven**, not
confirmed.

**Exact action needed.** A professional trademark search for **Odivrelo** in the
EU, Greece and Albania in the relevant classes, a check of the proposed domain
`odivrelo.com` and of app-store and social handles, and one Greek and one
Albanian native speaker saying the name aloud. Neither is a beta blocker; both
are launch blockers.

---

## EB-07: Raspberry Pi acquisition target

- **Affected capability:** the production daily acquisition deployment.
- **State:** blocked, with a locally runnable equivalent delivered.

The Raspberry Pi described in `ops/raspberry-pi/` is not reachable from this
environment and no credentials for it are configured, so no remote check was
run and none is claimed. The acquisition pipeline, its bounded request budget,
its rate limits and its TicketWeb gate all run and are tested locally, and the
systemd units and deploy script are updated for the new name with the legacy
environment variables still honoured.

**Exact action needed.** Run `ops/raspberry-pi/deploy.sh` against the Pi from a
host that can reach it, then confirm `odivrelo-acquire.timer` is active and the
legacy data root migrated.

---

## Not blockers

Recorded so they are not mistaken for blockers later.

- **`odivrelo.com`** is a proposed domain only. Its ownership and hosting are
  not confirmed, so nothing links to it and it is not a canonical URL. The
  release hostname is `odivrelo.peterdsp.dev`, per the project's existing
  convention, and a separately purchased domain is not a release dependency.
  (For the previous name, `poravia.com` was registered to a dormant
  placeholder; equally irrelevant.)
- **TicketWeb** is disabled by design, not blocked. It stays off until written
  terms approval exists, and the gate honours all three environment prefixes so
  a rename cannot flip it.
