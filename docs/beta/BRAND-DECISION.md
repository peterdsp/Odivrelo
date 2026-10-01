# Brand decision: Odivrelo

- Status: decided and implemented
- Date: 1 October 2026
- Decided by: the product owner
- Supersedes: the `Poravia` name (30 September 2026 to 1 October 2026), which
  itself superseded `HodoMap`. Both are historical. The decision that chose
  Poravia is preserved below as written, because rewriting a dated decision
  would falsify the record.

## Decision

| Field | Value |
|---|---|
| Display name | **Odivrelo**, in running text and in every interface string, in all three languages. Greek text keeps the Latin spelling, as it did for the previous name |
| Capitals | `ODIVRELO` only where a layout sets everything in capitals |
| DNS-safe slug | `odivrelo` |
| Public hostname | `odivrelo.peterdsp.dev`, Cloudflare Pages project `odivrelo` |
| Proposed domain | `odivrelo.com`, a **proposal only**. Ownership and hosting are not confirmed, so it is not linked, not canonical and not in any configuration |
| Apple bundle identifier | `dev.peterdsp.odivrelo` |
| Android application id and package | `dev.peterdsp.odivrelo` |
| Custom URL scheme | `odivrelo://` |
| Environment-variable prefix | `ODIVRELO_`, with `PORAVIA_` and `HODOMAP_` still read as fallbacks |
| Repository | `peterdsp/Odivrelo` |
| Product description | Odivrelo helps travellers find intercity coach journeys in Greece, understand exactly where to board, and reach the correct operator's ticket purchase page. Stored per language in `brand.json` |
| Canonical identity file | `brand.json` at the repository root |

## What is and is not claimed

- **No etymology is claimed.** Odivrelo is presented as an invented name. The
  product does not say it is a Greek word, does not translate it and does not
  explain where it came from. The About copy in every client says exactly
  that and no more.
- **No trademark ownership or clearance is claimed.** No collision screening,
  trademark search or native-speaker review was performed for Odivrelo as part
  of this rename. `EXTERNAL-BLOCKERS.md` EB-06 carries the follow-up, which is
  a launch blocker and not a beta blocker.
- **Dromiqo** is listed in `brand.json.legacyNames` because the owner named it
  among previous names for this product. It never appeared in this repository;
  it is on the list so that the rename gate stops it from appearing now.

## Visual identity: Route O

The Poravia arch mark is retired. The owner's brand board defines the new
direction, and `design/logo/generate_odivrelo_brand.py` draws it:

- A bold ring in deep teal blue `#0B4A6B`, a road with white edges and lane
  dashes sweeping up through it from the lower left, and a warm orange
  `#FF9F2E` node on the upper right of the ring, where the road arrives.
- A horizontal logo with the wordmark in Inter Bold, deep navy on light, white
  on dark, plus black and white one-colour versions.
- A square 1024 app icon master with no corner mask, white mark on deep teal
  blue, with a light variant; a simplified cut that stays legible at 16 px for
  the favicon; clear space of a quarter of the ring's diameter.
- The palette lives in `design/tokens/odivrelo.tokens.json`.
  `scripts/generate-design-tokens.sh` writes the Web, Android and iOS themes
  from it and audits WCAG AA contrast in both themes. Orange is an accent and
  is never used as text on a light surface.

The web CSS prefix moved from `--pv-` to `--od-` with the name.

## Migration inventory, 1 October 2026

| Surface | Old | New | State |
|---|---|---|---|
| Product display name | Poravia | Odivrelo | migrated in every client, all three languages |
| Public hostname | `poravia.peterdsp.dev` (Pages project `poravia`) | `odivrelo.peterdsp.dev` (Pages project `odivrelo`) | new project deployed; the old one is left serving the last Poravia build |
| Repository (GitHub) | `peterdsp/Poravia` | `peterdsp/Odivrelo` | renamed; GitHub keeps the permanent redirect for the old URL |
| Repository (local checkout) | `/Users/peterdsp/git/Poravia` | `/Users/peterdsp/git/Odivrelo` | moved; `.venv` recreated from pinned requirements (absolute interpreter paths) |
| Apple bundle identifier | `dev.peterdsp.poravia` | `dev.peterdsp.odivrelo` | changed; nothing was ever registered under the old one (EB-02) |
| Android application id and package | `dev.peterdsp.poravia` | `dev.peterdsp.odivrelo` | changed; no Play record or upload key existed (EB-03) |
| URL scheme and app links | `poravia://`, `poravia.peterdsp.dev` | `odivrelo://`, `odivrelo.peterdsp.dev` | migrated |
| Kotlin packages | `dev.peterdsp.poravia.*` | `dev.peterdsp.odivrelo.*` | migrated with `git mv` |
| Swift targets, modules and types | `Poravia`, `PoraviaCore`, `PoraviaTests`, `PoraviaCoreClient` and the rest | `Odivrelo*` | migrated |
| Python packages | `poravia_ktel`, `poravia_pipeline` | `odivrelo_ktel`, `odivrelo_pipeline` | migrated with `git mv` |
| Env var prefix | `PORAVIA_*` | `ODIVRELO_*` | migrated; `PORAVIA_` and `HODOMAP_` still read as fallbacks |
| Systemd units, Pi paths | `poravia-acquire.*`, `~/.local/share/poravia` | `odivrelo-acquire.*`, `~/.local/share/odivrelo` | migrated; an existing legacy data root is still used rather than starting empty |
| Design tokens | `poravia.tokens.json`, `--pv-*` | `odivrelo.tokens.json`, `--od-*` | migrated, with the new palette |
| Brand board and marks | `Poravia-Brand-Board.svg`, `poravia-mark*.svg` | `Odivrelo-Brand-Board.svg`, `design/logo/odivrelo-*` | replaced; the old artwork is in git history only |
| Release pack directory | `artifacts/releases/poravia/` | `artifacts/releases/odivrelo/` | migrated; bundled iOS packs regenerated |

Nothing was published to a store under either name, so no installed client,
store record or signing identity is orphaned. The only live surface was the
Poravia web site, and it keeps running on its own Pages project.

## Legacy-reference allowlist, 1 October 2026

In addition to the table further down, these references to the former names
stay deliberately:

| Location | Reference | Why it stays |
|---|---|---|
| `server/api/publicapi/config.py`, `_staging.py`, `server/src/odivrelo_pipeline/cli.py`, `acquisition.py`, `server/ktel-staging/odivrelo_ktel/ktel_db.py`, `scripts/api-seed-demo.sh` | `PORAVIA_*` environment fallbacks, and the `poravia` legacy data root | A deployment configured under the previous name keeps working until it migrates. |
| `brand.json` `legacyNames` | every former name | The list the rename gate and the client tests read. |
| `scripts/check-brand.sh`, `scripts/verify-deployment.sh`, `scripts/finalize-package-rename.sh` | the patterns they hunt for | Guards, not branding. |
| Dated documents (this file's history section, `ARCHITECTURE-DECISIONS.md`, `EXECUTION-STATUS.md`, `EXTERNAL-BLOCKERS.md`, `docs/phase0/`, `docs/pilot/`, the historical strategy documents) | the name they were written under | Rewriting a dated record would falsify it. Each carries a banner or dated context. |

---

## History: the Poravia decision, 30 September 2026

> **Historical record.** Everything below was written when the product was
> named Poravia and is preserved as written. It is superseded by the decision
> above.

- Status: decided and implemented
- Date: 30 September 2026
- Decided by: the beta delivery execution, under the autonomous working
  agreement in `docs/AUTONOMOUS-BETA-DELIVERY-PROMPT.md`
- Supersedes: the `HodoMap` name, which is rejected and is now historical

### Decision

| Field | Value |
|---|---|
| Display name | **Poravia** |
| DNS-safe slug | `poravia` |
| Public hostname | `poravia.peterdsp.dev` |
| Apple bundle identifier | `dev.peterdsp.poravia` |
| Android application id | `dev.peterdsp.poravia` |
| Custom URL scheme | `poravia://` |
| Environment-variable prefix | `PORAVIA_` |
| Canonical identity file | `brand.json` at the repository root |

Every platform reads its constants from `brand.json`. A future rename touches
that one file plus the generated constants, not every surface.

### Rationale

Poravia is an **invented** name. It was built to suggest the Greek *πόρος*, a
passage, ford or strait, which is the product's actual promise: getting through
a confusing network to the right coach, at the right terminal, on the right
date. It is not a Greek word, it is not claimed to be one, and it has no
verified translation in any language. Do not publish an etymology stronger than
that sentence.

It satisfies the naming brief:

- **Distinctive and evocative**, in the register of the reference name the
  product owner liked, without reusing or imitating it. The reference name is
  also already in use by a separate private project of the same owner, so it
  was excluded outright.
- **Easy in Greek, English and Albanian.** It contains no hard `b`, `d` or `g`,
  which Greek must write as the digraphs μπ, ντ and γκ. Greek writes it
  Ποράβια, Albanian writes it Poravia, and English reads it po-RAH-vee-a.
- **No rude, funereal or misleading meaning** was found in any of the three
  languages.
- **Grows past buses.** Nothing in the name is a bus, a map or a timetable, so
  the product can extend to journeys and discovery generally.
- **Not Hodo-derived**, not a Bus/Map/KTEL construction, and it does not imply
  affiliation with the KTEL federation, any regional operator or any ticketing
  provider.

### Preliminary collision screening

All checks below were run on **30 September 2026**. This is preliminary
screening for engineering purposes. **It is not legal clearance and does not
establish worldwide uniqueness.** The unperformed checks are listed and must
not be read as passes.

#### Poravia, the selected name

| Check | Query | Result |
|---|---|---|
| General web | `"Poravia"` | No company, product, app or brand under the exact name. Hits are word-unscrambler pages and an Esperanto dictionary fragment. |
| Near misses | — | Peravia Province, Dominican Republic (a place, not a brand); the Pora river. |
| Travel and transport | `"Poravia" app travel` | No exact match. Nearest is **Portavia Travel**, a safari operator, `https://portaviatravel.com/`, which differs by a consonant. Kept on the watch list. |
| Apple App Store | `"Poravia" site:apps.apple.com` | No exact match. Nearest: Portavia / Porta Via food ordering. |
| Google Play | `"Poravia" site:play.google.com` | No exact match. Nearest: Porva Drive, Provia, Poorvika. |
| Greek market | `"Poravia" OR "Ποράβια" Ελλάδα` | Nothing. |
| `poravia.com` | DNS + fetch | **Registered** (Namecheap nameservers, Google IPs) but serving a near-empty placeholder titled "AVIA Study". Not a trading business and not in travel. |
| `poravia.peterdsp.dev` | `dig` | **Free.** No DNS record exists. |
| GitHub `peterdsp/poravia` | `gh repo view` | Does not exist. |
| Greek meaning and spelling | manual | Ποράβια spells cleanly from hearing, no digraph problem, no rude or funereal sense. |
| Albanian meaning | dictionary search | No dictionary meaning found. Orthographically native-looking and easy to say. **Absence of a bad meaning is unproven, not confirmed.** |
| English reading | manual | Pronounceable, no unwanted reading. |

`poravia.com` being parked is **not a release dependency**. The release
hostname is `poravia.peterdsp.dev`, per the project's existing
`<appname>.peterdsp.dev` convention. No domain was purchased and none is
required.

#### Screened and rejected

| Candidate | Verdict | Why |
|---|---|---|
| Perastra | rejected | Περάστρα is a real settlement on Tinos, so the brand would collide with a destination in its own market. Hotel Per Astra trades on `perastra.me` in the travel sector. A Serbian company and an Amazon houseware brand also use the name. |
| Farovia | rejected | `farovia.com` is an active Atlanta consultancy with the identical name **and** the identical lighthouse-plus-way etymology. Farovia-200 is also a live pharmaceutical brand. |
| Vialora | rejected | `vialora.nl` is an active Dutch travel business selling European car holidays. The `via-` travel namespace is saturated. |
| Anemori | rejected | Anemoi is an existing trip planner and ANEMORE is a live App Store travel app. Two direct in-category collisions. |
| Kalivera | rejected | Greek ears hear καλύβα, hut or shack, which is the wrong register, and it is one letter from Spanish *calavera*, a Day-of-the-Dead skull. Several active companies also hold the name. |
| Antamo | not chosen | Best meaning of the set: Greek αντάμα, together, and ανταμώνω, to meet up. Rejected because **Antom**, Ant International's payments brand, markets explicitly to travel and airlines, and `antamo.com` is listed at premium reseller pricing. Worth revisiting only after a real trademark search. |
| Astivera | not chosen | Cleanest availability signal of all candidates and `astivera.com` does not resolve, but it says nothing about journeys. A blank name would have to be given meaning at cost. |
| Selanira | not chosen | No brand collision anywhere, but Albanian and Balkan speakers hear *Selanik*, the standard Albanian name for Thessaloniki, which would wrongly imply a city-specific service. |
| Meltera | not chosen | Reads as a misspelling of *meltemi*, points at wind and sailing rather than coaches, and English hears "melt". Several small live commercial uses. |
| Perasia | not chosen | περασιά is a real Greek dictionary word, so it is descriptive and weak as a mark, and international readers see "Persia" or "per-Asia". |
| Anaplora | not chosen | ανάπλωρα is a real Greek adverb, and Anaplora is already a trading restaurant in Nea Smyrni, Athens. |
| Orivia | not chosen | Orivia Corp (Japan) and Orivia Tech (US) are both live, it reads as a hiking product via the ορειβ- root, and it collides in speech with the given name Olivia. |
| Vialora, Nostavela, any Hodo- form | excluded by brief | The reference name belongs to a separate private project; Hodo forms are the rejected identity. |

#### Checks that could NOT be performed

These are gaps, not passes. **No trademark register was successfully searched
for any candidate, including Poravia.**

1. **EUIPO eSearch plus** — the results view is a JavaScript single-page app;
   the fetch returned only navigation and footer. No EU trademark search ran.
2. **TMview / TMDN** — results require a POST this toolchain cannot issue.
3. **USPTO trademark search** — JavaScript SPA; the documented API endpoint
   returned HTTP 404 without credentials. No US trademark search ran.
4. **WIPO Global Brand Database** — gated behind an ALTCHA proof-of-work
   challenge in front of an Angular SPA.
5. **Justia Trademarks and uspto.report** — HTTP 403 to automated fetches.
6. **National registers not attempted at all**: Greek OBI, Albanian DPPI,
   Greek GEMI, Albanian QKB.
7. **Domain status is DNS-inferred, not WHOIS-confirmed.**
8. **Social handles were not systematically checked** on Instagram, X, TikTok
   or GitHub.
9. **Greek and Albanian app stores were not browsed natively**; store coverage
   came from web search restricted to `apps.apple.com` and
   `play.google.com`, which under-indexes region-locked listings.
10. **No native-speaker review.** Albanian dictionary searches returned nothing
    for "poravia", so the absence of a bad meaning is unproven. Before any
    public launch beyond this beta, have a native Albanian speaker and a native
    Greek speaker say the name aloud.

**Required follow-up before a public launch:** a real trademark search in the
EU, Greece and Albania in the relevant classes, and native-speaker review.
Tracked in `EXTERNAL-BLOCKERS.md`.

Nothing was purchased, no trademark was registered, and nobody was contacted.

### Brand assets

The existing visual language is retained and adapted rather than replaced. The
Aegean teal, limestone and sun amber palette in
`design/tokens/hodomap.tokens.json` carries the same product meaning under the
new name, so the palette survives and the naming around it changes:

- CSS custom properties move from the `--hm-` prefix to `--pv-`.
- Token and stylesheet files are renamed to `poravia.tokens.json` and
  `poravia.css`.
- The brand board, wordmark, app icons, splash and favicons are redrawn as
  original Poravia assets. No HodoMap mark ships anywhere.

### Migration inventory

| Surface | Old | New | State |
|---|---|---|---|
| Product display name | HodoMap | Poravia | migrated |
| Public hostname | none published | `poravia.peterdsp.dev` | configured |
| Repository (local checkout) | `/Users/peterdsp/git/HodoMap` | `/Users/peterdsp/git/Poravia` | migrated |
| Repository (GitHub) | `peterdsp/HodoMap` | `peterdsp/Poravia` | see note below |
| Python staging package | `syrmos_admin` | `hodomap_ktel` then `poravia_ktel` | migrated |
| Python pipeline package | `hodomap_pipeline` | `poravia_pipeline` | migrated |
| Env var prefix | `SYRMOS_KTEL_*`, `HODOMAP_*` | `PORAVIA_*` | migrated, legacy names still read as a fallback |
| Design tokens | `hodomap.tokens.json`, `--hm-*` | `poravia.tokens.json`, `--pv-*` | migrated |
| Systemd units | `hodomap-acquire.*` | `poravia-acquire.*` | migrated |
| Apple bundle id | not registered | `dev.peterdsp.poravia` | new, no migration needed |
| Android application id | not registered | `dev.peterdsp.poravia` | new, no migration needed |
| Release pack directory | `out/ktel/` | `out/poravia/` | migrated |

Nothing user-facing was registered under the old name, so there is no installed
client, no store record and no signing identity to preserve. The rename is
therefore clean: no update identity breaks and no saved trip is lost.

### Documented legacy-reference allowlist

These `HodoMap`, `Hodo` or `syrmos` strings are retained **deliberately**. They
are historical or compatibility references, never current branding.

| Location | Reference | Why it stays |
|---|---|---|
| `server/ktel-staging/hodomap_ktel/ktel_db.py` | `SYRMOS_KTEL_DB_PATH`, `SYRMOS_KTEL_PUBLIC_DB_PATH` env fallback | An existing Raspberry Pi deployment still sets these. Removing them breaks a running service. |
| `server/ktel-staging/syrmos-api-integration.patch` | patches the external Syrmos repository | It belongs to a separate live project and encodes a superseded plan. Not Poravia branding. |
| `docs/phase0/tickets/PHASE0-01-rename-syrmos-package.md` and other dated tickets | historical outcome notes | Audit evidence. Rewriting dated decisions to look like they always said Poravia would falsify the record. |
| `docs/PILOT_DECISION.md`, `docs/pilot/**`, `docs/KTEL_*` | the product's former name in dated decisions | Same reason. Each is introduced as the former name. |
| Git history and commit messages before 30 September 2026 | the former name | History is not rewritten. |
| `docs/AUTONOMOUS-BETA-DELIVERY-PROMPT.md` | the former name | It is the instruction that commissioned the rename. |

Any occurrence outside this table is a defect. The rename gate greps the tree,
the built artifacts and the deployed site for `hodomap`, `hodo` and unresolved
`<newname>` placeholders; see `RELEASE-READINESS.md`.

### Note on the GitHub repository rename

The local checkout was first kept at `/Users/peterdsp/git/HodoMap` so no
existing tooling would break, and was moved to `/Users/peterdsp/git/Poravia` on
30 September 2026 at the owner's request (see AD-007). The **remote** repository name is public
branding and is renamed to `peterdsp/Poravia`. GitHub keeps permanent
redirects for the old URL, so:

- existing clones and `git remote` entries keep working;
- the fifteen issue links cross-referenced from `docs/pilot/` and
  `docs/phase0/` keep resolving.

`brand.json.repository` records the current URL.
