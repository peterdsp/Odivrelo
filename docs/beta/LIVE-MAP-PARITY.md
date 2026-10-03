# Live Map Parity

Prepared 3 October 2026. Compares the Syrmos reference map against each Odivrelo
platform, records what changed this session, and states the honest live-feed
coverage. Syrmos evidence is from a read-only inspection of
`/Users/peterdsp/git/Syrmos`; Odivrelo evidence is from this checkout.

## Syrmos reference capabilities (what good looks like)

- Basemap: keyless Esri "Gray Canvas" raster tiles (public ArcGIS endpoints),
  no API key. Android via osmdroid `XYTileSource`, iOS via MapKit
  `MKTileOverlay` with `canReplaceMapContent`. Light and dark variants.
- Vehicle state model (the strongest part): explicit `LIVE` (<= 90s),
  `STALE` (<= 600s), `EXPIRED` (> 600s, dropped). Single source of truth in
  `core/model/.../LiveVehicleFreshness.kt`, mirrored in Swift
  `DataFreshness.swift`. Markers age LIVE -> STALE -> gone against the wall
  clock even with no new data (1s Android sim tick, 5s iOS `nowTick`). Clearly
  distinguishes real GPS vehicles from timetable projections, which are labelled
  "Approximate estimate, not a live position".
- Stops resolved by stop id, clustered by name then 300 m proximity, with a
  4 tier zoom-band declutter and a viewport cull. One route polyline per line
  from shared geometry. Locate-me via platform location managers. Note: no
  fit-to-route; a fixed Athens frame on first open.
- State management: Android KMP MVVM `StateFlow`; iOS a native SwiftUI
  `UIViewRepresentable` over `MKMapView` observing shared singletons.
- Accessibility is weak in Syrmos: control buttons are labelled, but map markers
  have no VoiceOver/TalkBack labels, and Reduce Motion is not wired to the
  system flag. Odivrelo should do better here, not copy this.

## Odivrelo current state by platform

### Web (`apps/web/src/components/MapPanel.tsx`)

- MapLibre GL, loaded dynamically. Deliberately tileless: a style with no tile
  source, glyphs or sprite, so it makes no network request and works offline.
  It draws the route line and stop markers from data the page holds.
- Pan, zoom, fit (recentre) controls, keyboard navigation on. Honours reduced
  motion. The stop list is the accessible equivalent; markers are aria-hidden.
- Fixed: the route line draws, and stop markers now read real per-stop
  coordinates resolved by id (the contract previously omitted them, so markers
  were placed at `undefined` and `savedTrips.ts` `mapData` was always false).
- Basemap added (4 October 2026): the map now loads the free, keyless OpenFreeMap
  `liberty` style through MapLibre, with the OpenFreeMap and OpenStreetMap
  attribution visible. Verified in a preview build with Playwright: the style and
  tiles fetch and the attribution renders. The provider URL is configurable via
  `VITE_MAP_STYLE_URL`; empty keeps the offline-first tileless diagram. The CSP
  now allows exactly `https://tiles.openfreemap.org` and nothing else.
- Still missing versus the requirement: an accessible main Map destination (the
  map is journey-detail only), stop and operator/route filters, clustering, and
  locate-me.

### iOS (`apps/ios/.../JourneyDetail/RouteMapSection.swift`)

- MapKit journey map, polyline drawn solid when reviewed and dashed otherwise,
  with a confidence badge. No fleet tracking.
- Before this session it drew unnamed geometry vertices and a single boarding
  pin, with a comment that per-stop coordinates were not in the contract.
- Changed this session: stops are now resolved by stop id from
  `detail.journey.stops` and drawn as named annotations, with the boarded and
  alighted stops of the selected segment prominent and stops outside the segment
  muted. Geometry is used only for the line shape, never as a stop identity.
- Still missing: a main Map destination, a real basemap is already provided by
  MapKit, but there are no stop/route filters, no locate-me, no live/estimated
  state, and the map still renders only when drawable geometry exists (a journey
  with located stops but no geometry shows the list only). Marker accessibility
  is intentionally deferred to the stop list.

### Android (`apps/android/.../ui/journey/JourneyDetailScreen.kt`)

- `MapOrList` shows explanatory UI and an external maps handoff. There is no map
  library in the Android build (no MapLibre, osmdroid, Google or Mapbox
  dependency) and therefore no embedded interactive map at all.
- Changed this session: the shared model now carries per-stop coordinates, so
  the data an embedded map needs is available. The embedded map itself is not
  implemented.
- Missing: everything the requirement asks for on Android. This is the largest
  remaining map gap and needs a provider choice plus a new dependency, then
  emulator verification.

## What changed this session (stop coordinates by id)

The contract did not publish a coordinate on each journey stop, so no client
could place a stop by id. This was fixed end to end:

- `data/schemas/public-api-v1.yaml`: `JourneyStop` gains nullable
  `latitude`/`longitude`. Schemas and `generated/types.ts` regenerated.
- `server/api/publicapi/repository.py`: journey stop list emits each stop's own
  coordinate, resolved by stop id from the stop index.
- `apps/web/.../contract.ts` and `MapPanel.tsx`: coordinates are nullable and
  only located stops are plotted; the always-false `mapData` flag now reflects
  reality.
- `shared/core/.../Contract.kt`: shared `JourneyStop` gains nullable
  `latitude`/`longitude`, so iOS and Android receive them.
- iOS `ContractModels.swift`, `CoreMapping.swift`, `RouteMapSection.swift`:
  struct, mapper and map updated to use them.

Verification: server suites green (286 api + 27 ktel), `gen-contracts.sh` in
sync, web typecheck and 178 web tests green, new server regression test
`test_journey_stops_resolve_their_own_coordinates_by_id`. iOS and Android are
code complete but not yet built or run this session; build them with the release
CI workflows or a local Xcode and emulator pass.

## Live feed coverage (honest status)

- No live coach GPS feed is connected on any Odivrelo platform. The clients are
  scheduled-only today.
- No permitted live KTEL coach feed has been identified. Syrmos uses OASA
  telematics and rail live feeds, which are not KTEL intercity coverage and must
  not be substituted.
- Explicit live/estimated/scheduled/stale states are not implemented on any
  Odivrelo platform yet. The scheduled state is the only truthful state the data
  supports now.
- Conclusion: live coverage is incomplete. The real-data map and the live
  integration infrastructure (provider adapter, normalized live contract, client
  refresh, freshness and aging) remain to be built, and the exact missing feed
  (a permitted KTEL vehicle-position source) is not yet found.

## Provider decision (made 4 October 2026)

- Web: MapLibre GL JS with the OpenFreeMap `liberty` style (done, verified).
- Android: MapLibre Native with OpenFreeMap (to implement).
- iOS: MapKit (kept).

OpenFreeMap's public instance is free, keyless, permits commercial use and
requires attribution with no SLA. The provider URL stays configurable, and the
route and stop list remain the fallback during a provider outage. No planet
download and no paid commitment.

## CI evidence (PR #21, as of 4 October 2026)

- Data pipeline, public API, contract drift, Web, shared core and Android build,
  and iOS build and tests: passing.
- Rename gate: repaired this session.
- Android instrumentation (non-blocking): 11 failures that reproduce on a local
  host-GPU emulator. Nine are pre-existing deep-link and activity-recreation
  cases; two (`SearchFlowTest.c/d`) are the demo date time-bomb described in
  `REAL-PRODUCT-ACCEPTANCE.md`. CI build and test success does not prove a person
  can use the map; the Android map still needs building and an interactive run.
