# Poravia web

The public Poravia web application: a static, offline-capable progressive web app
served from the site root of `https://poravia.peterdsp.dev`.

It is built with React 19, TypeScript in strict mode, Vite 7 and React Router 7.
It reads the published, content-addressed release packs as static files, so the
deployed 1.0.0 site needs no server at all.

**Poravia does not sell or issue tickets.** Every purchase happens with the
operator. The application says so on every screen that could be mistaken for a
checkout.

---

## This build shows invented data

Release 1.0.0 ships a demonstration dataset for **Aloria**, a region that does not
exist. No departure, operator, terminal, fare or telephone number in it is real,
and none of it describes coverage of Greece or any other country.

While `dataMode` is `demo` the application:

- shows a persistent, non-dismissible notice on every page, in Greek, English and
  Albanian, saying the data is invented and that Aloria does not exist;
- ships `<meta name="robots" content="noindex, nofollow">` in the HTML of every
  page and publishes no sitemap;
- makes `robots.txt` disallow everything.

Flipping the release to `dataMode: "real"` removes the notice, publishes a
sitemap, and lets the operator, station and coverage pages upgrade themselves to
`index, follow`. No code change is needed for any of that.

---

## Running it

```bash
cd apps/web
npm ci

# The data comes from the one canonical release generator, never from here.
PY=../../.venv/bin/python bash ../../scripts/api-seed-demo.sh
bash ../../scripts/web-sync-packs.sh

npm run dev          # http://localhost:5173
```

`scripts/web-sync-packs.sh` copies a generated release into
`apps/web/public/data/`, verifies every digest and byte length in the manifest,
and **refuses** a release that does not carry the canonical pack names. There is
no pack generator in this directory, by design: a second implementation of the
pack shape is exactly the drift the single generator exists to prevent.

## Commands

| Command | What it does |
|---|---|
| `npm run dev` | Development server. |
| `npm run lint` | ESLint over `src` and `e2e`, failing on any warning. |
| `npm run typecheck` | `tsc --noEmit` over the app and the Node-side configs. |
| `npm test` | Vitest unit and integration suite. |
| `npm run test:e2e` | Playwright, against the production build. |
| `npm run build` | Typecheck, Vite build, post-build files, prerendered routes. |
| `npm run budget` | Fails if a performance budget is exceeded. |
| `npm run preview` | Serves `dist` the way a static host would. |

The build **does not** generate packs. Run `scripts/web-sync-packs.sh` first; CI
and the deploy workflow both do.

---

## Architecture

### One data interface, two implementations

Every screen goes through `PublicDataSource`. Two implementations satisfy it:

- **`StaticPackSource`** reads `/data/manifest.json` and the content-addressed
  packs it names. This is what the deployed site uses.
- **`HttpApiSource`** calls the `/v1/...` REST endpoints, and is selected when
  `VITE_API_BASE_URL` is configured.

A pack contains exactly what the matching endpoint returns, so `StaticPackSource`
deserialises responses rather than computing them: it holds no timetable logic.
The test suite runs the same queries through both against the same release and
asserts deep equality, which is what keeps offline and online from drifting.

Every pack is verified against the SHA-256 in the manifest before it is parsed.
A pack that fails is discarded, never used, and never installed.

### "No offline data" is not "no service"

A journeys pack exists only for the dates the data names. A recurring service on
any other date is resolved by the live API on request.

So when `StaticPackSource` has no pack for a requested date it returns
`no_offline_data_for_date`, and the UI says this device cannot answer for that
date. It never says nothing runs. Those are different claims, and only a live
source is entitled to make the second one.

### Storage

- **IndexedDB** holds offline packs, saved trips, favourites and the travel
  wallet. The release manifest is stored alongside the packs, because
  content-addressed file names are meaningless without it.
- **`localStorage`** holds per-viewer preferences only: language, theme,
  accessibility choices, the last filter and recent searches.

Every access is guarded. A browser that refuses storage gets an application that
says so and keeps working online, not one that throws.

### The map

MapLibre is imported dynamically, so a route that never opens a map never
downloads it, and it is deliberately excluded from the service-worker precache.

The map style has **no tile source, sprite or glyph URL**. It draws the route
geometry and the stops from data the page already holds, and makes no network
request of any kind. Poravia ships no map imagery, so the panel says so rather
than letting an empty background imply a failure.

Every map is optional. All journey, boarding-point and stop information is on the
page in text, and the ordered stop list carries the pickup and drop-off rules the
map cannot show at all.

---

## Prerendering, and why

A single-page app on GitHub Pages answers every deep link with `404.html` and a
**404 status**. The page renders, but the response says it does not exist, which
breaks shared links, link previews and any hope of indexing.

`npm run build` therefore emits a real `index.html` per route:

- one per static route (`/search/index.html`, `/settings/index.html`, and so on);
- one per operator, stop and journey in the release, enumerated from the packs in
  `dist/data/`.

Each carries its **own** `<title>`, description, canonical link, Open Graph tags,
robots directive and, for entity pages, JSON-LD. The body is still hydrated by the
client: this is the shell plus correct per-page metadata, not server-side
rendering of the body. The two defects were the status code and the metadata, and
both are fixed.

`404.html` is kept. A genuinely unknown path is a 404 and says so.

`e2e/prerender.spec.ts` fails if a route in the router has no entry point, so a
new route cannot silently regress.

---

## Security headers

GitHub Pages cannot set response headers. This is a real limitation and is not
worked around:

- The **Content-Security-Policy is applied via `<meta http-equiv>`** in every
  page. A meta CSP **cannot carry `frame-ancestors`**, and it applies marginally
  later than a header would. `X-Frame-Options: DENY` in `public/_headers` is the
  intended mitigation, and it only takes effect on a host that can set headers.
- `public/_headers` documents the full set a real host must apply: CSP with
  `frame-ancestors 'none'`, `X-Content-Type-Options`, `Referrer-Policy`,
  `Permissions-Policy`, `Cross-Origin-Opener-Policy`,
  `Cross-Origin-Resource-Policy`, `X-Frame-Options` and HSTS, plus the cache
  policy for immutable packs.

`upgrade-insecure-requests` is in `_headers` but deliberately **not** in the meta
CSP: on an https page with `default-src 'self'` it buys nothing, and on an http
origin it makes the browser upgrade requests that cannot be served. Chromium
exempts localhost from that rule and WebKit does not, so it turned the local
preview into a blank page in Safari while looking fine in Chrome.

For nginx, the same policy is:

```nginx
add_header Content-Security-Policy "default-src 'self'; base-uri 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' blob: data:; font-src 'self'; connect-src 'self'; worker-src 'self' blob:; frame-src blob:; object-src 'none'; form-action 'none'; frame-ancestors 'none'; manifest-src 'self'" always;
add_header X-Content-Type-Options nosniff always;
add_header Referrer-Policy strict-origin-when-cross-origin always;
```

---

## Privacy

There are no accounts, no analytics, no trackers and no third-party scripts. An
end-to-end test asserts that loading the app makes **no request to any origin but
its own**.

Ticket files imported into the travel wallet are stored only in IndexedDB. They
are never uploaded, never placed in the service-worker cache, never written to a
log or to diagnostics, and never put into a URL. Deleting one really deletes it.
The wallet screen says plainly that browser storage can be evicted and that this
is therefore not a backup.

---

## What is verified, and what is not

### Verified by automated tests

- Both data sources return deep-equal results for every place pair, service date,
  stop, operator and journey in the release.
- Pack integrity: a corrupted or truncated pack is rejected and nothing is
  installed.
- Download resume, cancellation, update, rollback and delete.
- Service-date arithmetic across both Europe/Athens transitions, including an
  overnight journey whose elapsed duration differs from its wall-clock duration by
  an hour.
- Translation completeness: every key present in all three languages, with no
  English leaking into Greek or Albanian, and matching placeholders.
- Wallet file validation, including a file whose bytes disagree with its claimed
  type, and real deletion.
- Storage failure: `localStorage` absent, throwing on read, and throwing on write.
- `axe-core` at zero violations on every route, in both themes, at both widths.
- One `h1` per page and no skipped heading level, on every route.
- State preserved across reload, resize, rotation and the back stack, and an
  identical search never re-issued because the window changed shape.

### Not verified

- **Firefox.** The Playwright Firefox build on the development machine refuses to
  start (`Could not find profile folder`), including when launched directly
  outside Playwright. The suite is configured for `firefox-mobile` and
  `firefox-desktop` and should be run in CI, where the browser installs cleanly.
- **Offline document reload in WebKit.** Playwright's WebKit intercepts requests
  before the service worker, so cutting the network aborts a document navigation
  before the worker is consulted. That scenario is skipped by name on WebKit and
  passes in Chromium. The in-app offline path is tested on every engine.
- **Tab order to links in WebKit.** Safari leaves links out of the sequential
  focus order unless Full Keyboard Access is enabled. That assertion is skipped by
  name on WebKit; the focus contract the application owns is tested everywhere.
- **Real transit data.** Everything here has only ever been exercised against the
  invented Aloria release.
- **Lighthouse.** Not run; the bundle and precache sizes in
  `artifacts/build-report.json` are measured, and `npm run budget` fails the build
  when a limit is exceeded, but no lab performance profile has been captured.
- **Screen readers.** `axe-core` is automated coverage, not a substitute for
  testing with VoiceOver, NVDA or TalkBack.

### Known data-side gap

`meta.coverage.note` arrives from the release as a plain English string rather
than a per-language object, so on a Greek or Albanian page that one sentence
renders in English. The application handles both shapes; making it localised is a
change to the release generator, not to this app.

---

## Artifacts

`npm run build` and the test suites write to `apps/web/artifacts/`, which is
gitignored:

| Path | Contents |
|---|---|
| `build-report.json` | Per-chunk sizes, raw and gzip, and the release id. |
| `prerendered-routes.json` | Every entry point emitted. |
| `screenshots/` | From the production build, named `scenario__project__viewport__date.png`. |
| `playwright-report/` | The HTML report. |
| `coverage/` | Vitest coverage, when `npm run test:coverage` is run. |
