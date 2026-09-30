# Public data contract, version 1

Status: authoritative. Every client (Web, iOS, Android) and the API service
implement exactly these shapes. Breaking changes require `/v2`.

Contract version string: `1.0.0`. Served in every response envelope as
`contractVersion`.

## Cross-cutting rules

- All times are ISO-8601 with an explicit offset. Service dates are
  `YYYY-MM-DD` interpreted in `Europe/Athens`.
- A journey that departs before midnight and arrives after it keeps the
  earlier service date. Clients must not recompute the service date from the
  arrival instant.
- Identifiers are opaque strings. Clients must not parse them.
- Every payload carries `releaseId`. A client holding packs from release A must
  not mix them with API responses from release B; it shows the cached release
  and its age instead.
- `dataMode` is `real` or `demo`. When `demo`, every client must show a
  persistent, non-dismissible notice that the services shown are invented and
  are not real departures, and must send `noindex` on web.

## Envelope

Every 200 response is an object containing at least:

```json
{
  "contractVersion": "1.0.0",
  "releaseId": "4bf83a41cd6f9514",
  "publishedAt": "2026-09-30T10:00:00Z",
  "dataMode": "demo"
}
```

## Enumerations

- `rightsStatus`: `allowed` | `permission_pending` | `prohibited` | `unknown`
- `reviewState`: `candidate` | `verified` | `published` | `stale` | `withdrawn` | `quarantined`
- `timeQuality`: `scheduled` | `approximate` | `unknown`
- `positionQuality`: `scheduled` | `predicted` | `estimated` | `live`
- `geometryConfidence`: `unverified` | `ordered_stops_only` | `osm_candidate` | `reviewed` | `rejected`
- `purchaseKind`: `online` | `ticket_office` | `phone` | `onboard` | `unavailable`
- `coverageState`: `covered` | `partial` | `not_covered` | `demo`

## Endpoints

### `GET /v1/meta`

```json
{
  "contractVersion": "1.0.0",
  "releaseId": "…", "publishedAt": "…", "dataMode": "demo",
  "product": { "name": "…", "version": "1.0.0", "commit": "…", "builtAt": "…" },
  "languages": ["el", "en", "sq"],
  "coverage": { "state": "demo", "operatorCount": 1, "corridorCount": 1,
                "note": "…" },
  "offlineManifestUrl": "/v1/offline/manifest",
  "gtfsUrl": "/v1/gtfs",
  "attribution": [ { "name": "…", "url": "…", "licence": "…" } ]
}
```

### `GET /v1/places?q=&limit=&lang=`

Place disambiguation. Returns stop places (terminals) and their boarding
points. `q` is matched case- and accent-insensitively across `el`/`en`.

```json
{ "…envelope…",
  "places": [
    { "id": "…", "kind": "stop_place" | "stop",
      "name": { "el": "…", "en": "…", "sq": "…" },
      "parentId": null,
      "municipality": "…",
      "latitude": 38.1, "longitude": 23.7,
      "coordinateStatus": "valid",
      "boardingPointCount": 3,
      "operatorIds": ["…"],
      "coverage": "covered" }
  ],
  "total": 12 }
```

### `GET /v1/journeys?origin=&destination=&date=&lang=&accessible=`

`origin`/`destination` accept a place id of either kind. `date` is a service
date. `accessible=true` filters to journeys with a reviewed step-free boarding
point.

```json
{ "…envelope…",
  "query": { "originId": "…", "destinationId": "…", "date": "2026-10-02" },
  "coverage": "covered" | "partial" | "not_covered" | "demo",
  "results": [
    { "id": "…",
      "operator": { "id": "…", "name": {"el":"…","en":"…","sq":"…"},
                    "logoAvailable": false },
      "departure": { "at": "2026-10-02T09:00:00+03:00",
                     "stopId": "…", "stopName": {"el":"…","en":"…","sq":"…"},
                     "quality": "scheduled" },
      "arrival":   { "at": "2026-10-02T12:10:00+03:00",
                     "stopId": "…", "stopName": {…},
                     "quality": "approximate" },
      "durationMinutes": 190,
      "intermediateStopCount": 2,
      "serviceDate": "2026-10-02",
      "crossesMidnight": false,
      "positionQuality": "scheduled",
      "fare": { "amount": 18.0, "currency": "EUR", "isIndicative": true } | null,
      "freshness": { "checkedAt": "…", "ageHours": 6, "state": "fresh" | "aging" | "stale" },
      "confidence": "reviewed" | "candidate",
      "purchase": { "kind": "online", "url": "…", "label": {…} } }
  ],
  "unavailableReason": null | "no_service_on_date" | "outside_coverage" | "origin_equals_destination"
}
```

### `GET /v1/journeys/{id}?date=&lang=`

Adds to the result object:

```json
{ "…envelope…",
  "journey": { "…as in results…",
    "boardingPoint": {
      "stopId": "…", "name": {…}, "terminalName": {…},
      "bay": "…" | null,
      "latitude": 38.1, "longitude": 23.7,
      "instructions": {…} | null,
      "reviewState": "published", "reviewedAt": "…",
      "stepFree": true | false | null },
    "stops": [ { "stopId": "…", "sequence": 1, "name": {…},
                 "arrivalAt": null, "departureAt": "…",
                 "timeQuality": "scheduled",
                 "pickup": "allowed", "dropoff": "not_allowed" } ],
    "geometry": { "type": "LineString", "coordinates": [[lon,lat],…],
                  "confidence": "ordered_stops_only",
                  "method": "…", "attribution": "…" } | null,
    "restrictions": [ {"code":"…","text":{…}} ],
    "provenance": [ { "sourceId": "…", "sourceName": "…", "sourceUrl": "…",
                      "retrievedAt": "…", "rightsStatus": "allowed",
                      "licence": "…" } ],
    "purchase": { "kind": "ticket_office",
                  "url": null,
                  "phone": "+30…",
                  "address": "…",
                  "openingHours": "…",
                  "label": {…},
                  "disclaimer": {…} },
    "correctionUrl": "…" }
}
```

### `GET /v1/operators` and `GET /v1/operators/{id}`

```json
{ "…envelope…",
  "operator": { "id": "…", "name": {…}, "federationNumber": 59,
    "officialSiteUrl": "…", "directoryUrl": "…",
    "contact": { "phone": "…", "email": "…", "address": "…" } | null,
    "coverage": { "state": "demo", "routeCount": 1, "stopCount": 4 },
    "sources": [ … provenance … ],
    "verifiedAt": "…",
    "correctionUrl": "…" } }
```

### `GET /v1/stops/{id}`

Station page: names, coordinates, parent terminal, boarding points, operators
serving it, next departures for a given date, provenance, correction path.

### `GET /v1/coverage`

Machine-readable coverage and freshness summary, including the explicit
statement of what is **not** covered.

### `GET /v1/sources`

Source registry with rights status and licence per source. Public.

### `GET /v1/offline/manifest`

Mirrors the generated release manifest:

```json
{ "contractVersion": "1.0.0", "product": "…", "releaseId": "…",
  "publishedAt": "…", "counts": {…},
  "files": { "places": { "path": "packs/places-<digest16>.json",
                         "sha256": "…", "bytes": 1234,
                         "mediaType": "application/json" }, … } }
```

`files` is keyed by logical pack name. **There is exactly one generator of
packs, and a pack carries this contract's shapes, not the shape of any internal
staging table.** A pack holds precisely what the corresponding `/v1/…` endpoint
would have returned for the same input, so an offline client and an online
client decode the same payloads with the same code and cannot disagree.

The permitted logical pack names are exactly:

| Pack | Contents |
| --- | --- |
| `meta` | the `GET /v1/meta` body |
| `coverage` | the `GET /v1/coverage` body |
| `sources` | the `GET /v1/sources` body |
| `places` | the `GET /v1/places` body, unfiltered and unpaged |
| `operators` | every operator in the `GET /v1/operators/{id}` shape, keyed by id |
| `stops` | every stop in the `GET /v1/stops/{id}` shape, keyed by id |
| `journeys-<serviceDate>` | the `GET /v1/journeys` result list for that service date, plus the `GET /v1/journeys/{id}` detail for each result keyed by journey id |
| `gtfs` | the reviewed GTFS feed for the release |

A manifest carrying any other name is invalid, and a generator that produces one
must fail rather than publish it.

A release materialises one `journeys-<serviceDate>` pack per service date it
declares: the service date of every date-specific journey, the template date of
every calendar-backed journey, and every date an `added` calendar exception names.
Further recurrences of a weekday calendar are resolved by `GET /v1/journeys` on
request and are deliberately not materialised, because a year of packs per
calendar is not a release. A client asking offline for a date with no pack has no
cached answer for that date and must say so rather than report no service.

Every pack body carries the same envelope as an API response, so a client can
tell which release a cached pack came from and must not mix it with responses
from another.

### `GET /v1/offline/packs/{filename}`

Serves a pack byte-for-byte with `ETag: "<sha256>"`, `Cache-Control:
public, max-age=31536000, immutable`. Content-addressed names make this safe.

### `GET /v1/gtfs`

The reviewed GTFS zip for the current release.

### `GET /healthz` and `GET /readyz`

`/healthz` is process liveness. `/readyz` fails when the public database is
missing, carries no published release, or disagrees with the manifest.

### Private administrative surface

Mounted under `/admin`, requires a bearer token from `ADMIN_API_TOKEN`, never
mounted on the public origin in deployment, and returns
`Cache-Control: no-store`. Covers: review queue, approve/quarantine/withdraw
with an audit trail, correction intake, takedown, and release publish/rollback.

## Errors

```json
{ "error": { "code": "not_found" | "invalid_request" | "unavailable"
                     | "release_mismatch" | "unauthorized",
             "message": "…",
             "field": "date" } }
```

HTTP codes: 400, 401, 404, 409 (`release_mismatch`), 503 (`unavailable`).
