# Roadmap

> **Historical document.** This records a decision made under the product's
> former name, HodoMap, which was rejected on 30 September 2026 and replaced
> by **Poravia**. The text below is preserved as written, including the old
> name, because rewriting a dated decision would falsify the record. See
> [the brand decision](../docs/beta/BRAND-DECISION.md).


The roadmap advances by evidence gates, not by claiming arbitrary percentage
completion.

The full workstreams, R0 to R6 coverage levels, public-beta gates, national 1.0
gates, 30-day sequence and acceptance criteria are maintained in
[NATIONAL_EXECUTION_PLAN.md](NATIONAL_EXECUTION_PLAN.md).

Product differentiation, the connected-corridor launch wedge, and the
Web-to-app conversion strategy are maintained in
[PRODUCT_DIFFERENTIATION.md](PRODUCT_DIFFERENTIATION.md).

## Phase 0: repository and contracts

- Establish repository structure and contribution rules.
- Lock toolchain versions.
- Scaffold KMP mobile core, native iOS, Android and Web applications.
- Define OpenAPI, public release and offline-pack contracts.
- Add continuous integration for tests, formatting and secret scanning.

Exit gate: each application builds against a synthetic public fixture.

## Phase 1: governed three-operator pilot

- Select three operators with different source formats in one connected,
  high-confusion corridor.
- Establish source rights and evidence packages.
- Implement bounded adapters and fixtures.
- Normalize stops, routes, calendars and trips.
- Review road geometry.
- Build search, result, detail and operator screens.
- Verify a journey purchase link or complete operator and ticket-office contact
  fallback for every pilot operator.
- Publish Web-first Trip Ready pages with exact boarding points and explicit
  coverage boundaries before requiring app installation.
- Build the scheduled route, intermediate-stop timeline and live-data degraded
  states defined in
  [Live coach map and ETA product analysis](LIVE_COACH_MAP_AND_ETA.md).
- Add versioned offline traffic profiles for normal, weekend, seasonal, and
  holiday prediction states where permitted evidence is sufficient.

Exit gate: all three operators work from ingestion through offline clients with
source and freshness visibility.

## Phase 2: national source registry

- Investigate all 62 federation operators.
- Classify booking providers with dated evidence.
- Acquire a stable NAP export or documented replacement.
- Record source rights, freshness and adapter ownership.

Exit gate: every operator is source-mapped or has a documented blocked state.

## Phase 3: national data build

- Normalize lawfully acquired stop records.
- Implement remaining HTML, PDF and provider adapters.
- Build service calendars and exceptions.
- Review geometry and terminal crosswalks.
- Publish coverage and freshness scorecards.

Exit gate: at least 50 operators have usable reviewed timetables for public
beta.

## Phase 4: Raspberry Pi staging

- Deploy isolated ingestion and public databases.
- Configure API, compiler, backups, nginx, systemd and monitoring.
- Prove atomic release publication and rollback.
- Complete a seven-day staging soak.

Exit gate: cold boot, backup restore, release mismatch and rollback are tested.

## Phase 5: national public beta

- Ship iOS, Android and Web.
- Add offline packs, favorites, saved trips, protected local ticket imports,
  external-browser booking handoffs, and travel notifications.
- Show complete operator and ticket-office contact details when electronic
  ticketing is unavailable or unverified.
- Show estimated coaches offline from timetable and downloaded seasonal
  profiles, with route-position and ETA ranges.
- Add live coach tracking only for operators with authorized, healthy
  real-time feeds. Keep schedule, predicted, online estimated, and live
  coverage as separate metrics.
- Complete accessibility, localization and security reviews.
- Publish source transparency and corrections workflows.

Exit gate: legal, rights, production and platform release gates pass. Saved
tickets remain local, and notifications expose no passenger or ticket data.

## National 1.0

- All 62 operators have reviewed timetables or a dated official unavailable
  state.
- At least 90 percent of published journey patterns have reviewed geometry.
- Every supported online booking handoff is verified.
- Every operator without electronic ticketing has verified physical purchase
  and contact information.
- All enabled adapters have monitoring, fixtures and an assigned owner.
