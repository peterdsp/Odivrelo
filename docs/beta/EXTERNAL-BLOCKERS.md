# External blockers

Only blockers that were actually hit. No speculation. Each entry records what
is missing, what it blocks, what was attempted, what is already prepared, and
the precise action that would unblock it.

Last updated: 30 September 2026.

---

## EB-01: Cloudflare DNS record for `poravia.peterdsp.dev`

- **Affected capability:** the public Web `1.0.0` release gate. This is the
  one gate that cannot be closed without it.
- **State:** blocked.

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

## EB-02: Apple signing identity and App Store Connect access

- **Affected capability:** an installable iPhone beta. TestFlight upload,
  processing, and availability to testers.
- **State:** blocked.

**What is missing.** No Apple Developer team identifier, no distribution
signing certificate and no provisioning profile are configured on this machine,
and no App Store Connect API key is available. There is no existing app record
for `dev.peterdsp.poravia`, because the identifier is new.

**What was attempted.** The Release configuration is built and validated as far
as signing allows. Entitlements, usage descriptions, the privacy manifest,
bundled resources, localisations, the linked shared framework and bundle
identity are all checked.

**What is already prepared.**

- A working simulator build, runtime-verified on iPhone, iPad and iPhone Duo.
- The exact export options and signing configuration, written out in
  `BUILD-AND-RELEASE.md`.
- A `PrivacyInfo.xcprivacy` derived from the code that was actually written.

**Exact action needed.** Enrol or sign in with the Apple Developer account,
register the bundle identifier `dev.peterdsp.poravia`, create the App Store
Connect app record, then create an App Store Connect API key and export
`APP_STORE_CONNECT_KEY_ID`, `APP_STORE_CONNECT_ISSUER_ID` and the `.p8` key, and
run the archive and upload steps in `BUILD-AND-RELEASE.md`.

**Explicitly not done.** No disposable signing identity was invented, no
compliance or review answer was fabricated, and a simulator build is **not**
being reported as an installable iPhone beta.

---

## EB-03: Android release signing key and Play Console access

- **Affected capability:** a signed release bundle, Play internal-track upload,
  and availability to testers.
- **State:** blocked.

**What is missing.** No release keystore is present, and there is no Play
Console app record for `dev.peterdsp.poravia`.

**What is already prepared.** An installable debug/QA APK, a release build and
bundle produced unsigned and labelled as such, and the full signing
configuration in `BUILD-AND-RELEASE.md`.

**Exact action needed.** Create or restore the upload keystore, put its
credentials in the repository secrets named in `BUILD-AND-RELEASE.md` (names
only are recorded there, never values), create the Play Console app record,
then run the signed release job.

**Explicitly not done.** No unprotected disposable production signing identity
was created to claim completion, and a debug key is not used for store
distribution.

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
5. Only then flip `dataMode` to `real`. No code change is required.

---

## EB-05: The five-traveller validation, Gate D2

- **Affected capability:** the product claim that Poravia beats the operator's
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

- **Affected capability:** confidence in the Poravia name beyond preliminary
  screening.
- **State:** blocked for the automated checks, open for the human ones.

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

**Exact action needed.** A professional trademark search in the EU, Greece and
Albania in the relevant classes, and one Greek and one Albanian native speaker
saying the name aloud. Neither is a beta blocker; both are launch blockers.

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
host that can reach it, then confirm `poravia-acquire.timer` is active and the
legacy data root migrated.

---

## Not blockers

Recorded so they are not mistaken for blockers later.

- **`poravia.com` is registered** to a dormant placeholder. Irrelevant: the
  release hostname is `poravia.peterdsp.dev`, per the project's existing
  convention, and a separately purchased domain is not a release dependency.
- **TicketWeb** is disabled by design, not blocked. It stays off until written
  terms approval exists, and the gate honours all three environment prefixes so
  a rename cannot flip it.
