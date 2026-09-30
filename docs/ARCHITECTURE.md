# Poravia Architecture

Status: accepted foundation decision

## Decision

Poravia will use Kotlin Multiplatform for shared iOS and Android logic.

Android uses Jetpack Compose. iOS uses SwiftUI and MapKit. The public Web app
uses TypeScript, React and a web-native map library. Backend services use
Python, FastAPI and SQLite unless measured requirements justify a later change.

## Why this split

The mobile applications need the same:

- Journey and operator contracts.
- API behavior.
- Offline database.
- Favorites.
- Coverage and freshness states.
- Service-date and time-zone handling.
- Search coordination.

Sharing those behaviors prevents platform drift.

The user interfaces do not need to be identical. Native iOS behavior, Android
Compose conventions and semantic Web pages matter more than maximizing the
percentage of shared source code.

## System boundaries

### Mobile shared core

The KMP shared core may:

- Decode API and offline-pack contracts.
- Store public release data.
- Coordinate searches.
- Apply display-safe sorting and filtering.
- Manage favorites and local settings.
- Expose observable state to native user interfaces.

It must not:

- Scrape operators.
- Store provider credentials.
- Decide source rights.
- Invent routes or service calendars.
- Interpret a booking observation as a permanent timetable.

### Native applications

Each application owns:

- Navigation and platform lifecycle.
- Accessibility integration.
- Map rendering.
- External booking handoff.
- Location permissions.
- Notifications.
- Platform distribution and signing.

### Web application

The Web app consumes the same OpenAPI and JSON schema contracts as mobile. It
owns:

- Search-indexable operator, route and station pages.
- Semantic HTML and browser accessibility.
- Link previews and structured metadata.
- Web mapping and deep links.

### Server

The server is authoritative for:

- Operators, stops, routes and journey patterns.
- Timetables, calendars and exceptions.
- Source lineage and freshness.
- Rights and review gates.
- Release identity.
- Search results and official booking links.

Clients can read cached public data, but they must not independently implement
a conflicting national timetable engine.

## Data path

```text
Official or permitted sources
        |
        v
Bounded source adapters
        |
        v
Immutable source artifacts
        |
        v
Candidate normalization
        |
        v
Quarantine and human review
        |
        v
Rights-gated compiler
        |
        +--> Read-only public SQLite database
        +--> Release-addressed JSON and offline packs
        +--> Manifest written last
```

## Database separation

Use two databases:

1. An ingestion and review database containing candidates, provenance and
   review state.
2. A compiled public database containing only permitted and approved rows.

Compilation must be reproducible. Public database and manifest release IDs must
match. The API should refuse inconsistent releases instead of serving a mixed
snapshot.

SQLite WAL and SHM files are runtime state. They must never be committed.

## Proposed mobile modules

```text
shared/
├── core/
│   ├── common
│   ├── model
│   ├── network
│   ├── database
│   ├── domain
│   └── testing
└── features/
    ├── search
    ├── journeys
    ├── operators
    ├── stops
    ├── map
    ├── offline
    └── settings
```

Modules should be introduced as working code requires them. Do not create a
large empty Gradle graph only to match this diagram.

## API rules

- Version public contracts.
- Use stable opaque identifiers.
- Return source freshness and coverage state.
- Use `Europe/Athens` for service-date interpretation.
- Keep booking links operator-owned.
- Use ETags and immutable release IDs.
- Generate client models from reviewed contracts where practical.
- Preserve backward compatibility throughout a published client release.

## Offline strategy

Offline packs are:

- Public, rights-cleared and immutable.
- Addressed by release ID and content digest.
- Split by operator or geography where size requires it.
- Verified before atomic installation.
- Retained with one previous release for rollback.

Booking availability is network-enhanced information and must not be treated as
permanently valid offline.

## Mapping strategy

Geometry is published only with source, method, version and confidence.

- iOS renders with MapKit.
- Android renders with a reviewed native map SDK.
- Web renders with MapLibre GL JS or an equivalent accessible web SDK.
- Attribution remains visible and compliant.

Straight lines and unreviewed routing must not be labelled exact.

## Deferred decisions

These decisions belong to implementation changes with working prototypes:

- Exact Kotlin, Compose and SQLDelight versions.
- Android map SDK.
- React framework and deployment platform.
- Authentication for the private review service.
- Whether the public and administrative APIs use separate processes.
- Long-term migration from SQLite if measured production load requires it.
