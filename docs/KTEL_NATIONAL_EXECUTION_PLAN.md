# Dromologio: National Greek Intercity Coach Execution Plan

> **Historical document.** This records a decision made under the product's
> former name, HodoMap, which was rejected on 30 September 2026 and replaced
> by **Poravia**. The text below is preserved as written, including the old
> name, because rewriting a dated decision would falsify the record. See
> [the brand decision](../docs/beta/BRAND-DECISION.md).


Status: proposed execution plan

Prepared: 26 July 2026

Working product name: **Dromologio**

Greek presentation: **Δρομολόγιο**

English store title: **Dromologio: Greece by Bus**

This is the delivery plan for turning the current KTEL API foundation into a
national, source-backed intercity coach product. It does not claim that the
national timetable database already exists.

## 1. Naming decision

### Recommendation

Use **Dromologio** as the public product name.

Use this presentation:

- Product: `Dromologio`
- Greek name: `Δρομολόγιο`
- Subtitle: `Intercity buses across Greece`
- Greek subtitle: `Υπεραστικά λεωφορεία σε όλη την Ελλάδα`
- Dataset: `Greek Intercity Coach Dataset`
- API: `Dromologio Coach API`
- Repository, if separated later: `dromologio`
- Current backend module during development: `syrmos-coach-api`

### Why not call it KTEL Greece

`KTEL Greece`, `National KTEL`, `Official KTEL`, and similar names can imply
that the product is operated or endorsed by the federation or all regional
operators. That relationship does not currently exist.

KTEL operator names, logos, booking systems, and trademarks remain with their
respective owners. The product must say clearly that it is independent and must
link users to each operator's official booking service for purchases.

### Relationship with Syrmos

Syrmos is explicitly a rail product, and its name refers to train carriages.
Dromologio should be a sibling product that reuses the proven Syrmos technical
platform:

```text
Independent transit products
|
+-- Syrmos
|   +-- Greek rail
|
+-- Dromologio
    +-- Greek intercity coaches
    +-- 62 operator registry
    +-- Official booking handoffs
```

Do not rename the existing Syrmos app during the data phase. Validate the
Dromologio name through Greek and EU trademark searches, domain checks, App
Store checks, Play Store checks, and qualified legal review before launch.

## 2. National outcome

A resident or visitor must be able to:

1. Search an origin, destination, and operating date.
2. See only source-backed journeys that are valid for that date.
3. Understand the operator, intermediate stops, departure time, arrival time,
   service restrictions, source freshness, and data confidence.
4. View reviewed route geometry where it exists.
5. Continue to the official operator service to reserve or buy a ticket.
6. Use a useful offline snapshot when the network is unavailable.
7. See an honest partial-coverage message where data is missing or blocked.

The service must not sell tickets, process payment, retain passenger details,
or impersonate an operator without a later written commercial agreement.

## 3. Definition of national completion

National completion does not mean that every field contains a value. It means
that every one of the 62 operators has been investigated, classified, and
supported by evidence.

Each operator receives a coverage level:

| Level | Meaning | Minimum evidence |
|---|---|---|
| R0 | Registry only | Official identity and official website |
| R1 | Source mapped | Timetable, booking, and announcement sources recorded |
| R2 | Stops reviewed | Stop inventory normalized and geographic errors quarantined |
| R3 | Timetable usable | Routes, trips, calendars, and exceptions published |
| R4 | Geometry usable | Reviewed stop order and road geometry published |
| R5 | Booking handoff | Official deep link tested without issuing a ticket |
| R6 | Production monitored | Refresh job, freshness alert, fixtures, and owner active |

The national public beta gate is:

- All 62 operators at R1 or a documented blocked state.
- At least 50 operators at R3.
- All published rows have permitted public reuse.
- No invalid coordinate is present in the public database.
- Every published trip has source lineage, effective dates, and retrieval time.
- Every public endpoint declares release ID and coverage freshness.

The national 1.0 gate is:

- All 62 operators at R3, or a dated official attestation that schedule data
  is unavailable and a manual official-source handoff is provided.
- At least 90 percent of published journey patterns at R4.
- All operators with online booking at R5.
- All production adapters at R6.
- iOS, Android, Web, API, offline packs, monitoring, legal, and accessibility
  gates passed.

No unavailable timetable should be invented to reach 100 percent.

## 4. Workstream map

| ID | Workstream | Resolves | Primary owner |
|---|---|---|---|
| W0 | Governance, naming, and legal | TicketWeb rights, branding, source terms | Product lead and Greek/EU counsel |
| W1 | Source acquisition | NAP XLSX and permitted raw exports | Partnerships and data engineer |
| W2 | All-operator source registry | 36 unverified TicketWeb operators | Research lead |
| W3 | Stop normalization | 19,872 unnormalized source stops | GIS and data engineer |
| W4 | Routes and timetables | Missing national journeys and calendars | Transit data engineers |
| W5 | Road geometry | Unverified national paths | GIS reviewer |
| W6 | Demand and revenue | Missing planning evidence | Analyst and partnerships |
| W7 | Raspberry Pi production | No deployment or live test | Backend/SRE owner |
| W8 | Product clients | Missing iOS, Android, and Web screens | KMP, iOS, and Web owners |
| W9 | Repository hygiene | Modified tracked SQLite sidecar | Backend owner |

## 5. Phase 0: freeze claims and protect runtime state

Estimated effort: 2 to 3 days

### Actions

1. Preserve the current working tree and record the files changed by the KTEL
   foundation work.
2. Do not overwrite `ops/syrmos-api/data/syrmos.db-shm`.
3. Confirm whether any SQLite process still owns the database or sidecar.
4. Back up the database, WAL, and SHM as one consistent SQLite snapshot before
   any cleanup.
5. Compare the tracked sidecar with the repository version and determine why a
   runtime-generated file is tracked.
6. Open a separate cleanup change that removes SQLite WAL and SHM files from
   version control only after the active database is safely checkpointed and
   backed up.
7. Publish the present coverage statement as a machine-readable API response.

### Acceptance criteria

- No runtime database state is lost.
- The KTEL ingestion database is isolated from the rail production database.
- Public documentation calls the current result a foundation, not a completed
  national timetable.
- Working tree ownership is clear before implementation continues.

## 6. Phase 1: legal, provider, and source authority

Estimated elapsed time: 2 to 8 weeks, depending on external responses

This phase runs in parallel with work that uses clearly permitted public
sources, but it blocks TicketWeb rows from public compilation.

### TicketWeb provider request

Ask the platform provider for a written answer covering:

- Documented read-only API or bulk export.
- Tenant inventory or a way to confirm operator participation.
- Permitted fields and purposes.
- Authentication and credential handling.
- Request limits and concurrency limits.
- Cache duration and refresh expectations.
- Storage of raw responses.
- Redistribution in open or public compiled datasets.
- Display inside free iOS, Android, and Web applications.
- Attribution requirements.
- Official booking deep-link rules.
- Change notification or versioning support.
- Commercial terms, if public redistribution is not free.

Do not copy browser session credentials or undocumented secrets into the
repository.

### Qualified legal review

Commission a Greek and EU qualified lawyer to review:

- TicketWeb terms and database reuse rights.
- Each automated operator source category.
- EU database rights.
- Copyright in timetable documents and maps.
- ODbL obligations for OpenStreetMap-derived data.
- Trademark and passing-off risk for the product name and visual identity.
- Deep linking to official booking pages.
- GDPR implications if ticketing is added later.
- Required attribution, disclaimers, and takedown process.

### Rights states

Every source artifact must have one of these states:

- `permitted`
- `permission_pending`
- `restricted_internal`
- `prohibited`
- `unknown`

Only `permitted` data can enter the public compiler.

### Acceptance criteria

- Signed legal memo stored outside the public repository where appropriate.
- Written TicketWeb provider response retained with date and scope.
- Source rights matrix covers every enabled adapter.
- Dromologio name and domains receive preliminary clearance.
- Compiler tests prove that non-permitted rows cannot leak.

## 7. Phase 2: acquire the national source corpus

Estimated effort: 2 to 4 person-weeks, excluding provider delays

### NAP XLSX recovery plan

The existing download returned HTTP 502 on 26 July 2026. Use this escalation
order:

1. Retry the canonical dataset URL with bounded exponential backoff.
2. Inspect the portal metadata API for a replacement resource URL.
3. Ask the NAP publisher for the current workbook directly.
4. Ask for a checksum and publication or effective date.
5. Request a stable API, GTFS, NeTEx, or recurring export instead of relying
   only on a portal attachment.
6. Retain the exact permitted original file, source URL, headers, retrieval
   timestamp, license, and SHA-256 digest.
7. Never substitute an unverified third-party mirror.

The NAP importer may stage workbook rows, but no sheet is mapped into public
entities until its semantics and dates are reviewed.

### Operator evidence package

Create one versioned package per operator containing:

- Operator identity and aliases.
- Official domains.
- Official timetable pages and files.
- Official booking entry points.
- Announcements and seasonal change pages.
- Contact channel used for verification.
- Source rights state.
- Retrieval timestamp and effective period.
- Adapter type and maintainer.
- Fixtures for parser tests.

### Acceptance criteria

- All 62 operators have an evidence package.
- Every source has provenance, freshness, rights, and an assigned owner.
- The NAP workbook is either retained lawfully or its absence has a dated
  publisher response and an approved alternative.
- Raw artifacts are immutable and content-addressed.

## 8. Phase 3: resolve all 62 operators and the 36 TicketWeb unknowns

Estimated effort: 3 to 6 person-weeks

The remaining 36 operators must not be labelled absent merely because no tenant
was found.

### Verification method

For each operator:

1. Inspect the federation listing and official operator website.
2. Inspect official online ticket buttons and application links.
3. Record the booking host, provider, redirect chain, and tenant identifier
   when explicitly exposed by an official page.
4. Check provider-owned public documentation or obtain provider confirmation.
5. Contact the operator when the booking provider remains ambiguous.
6. Assign exactly one status:
   - `verified_live`
   - `verified_not_ticketweb`
   - `unverified`
   - `blocked`
   - `retired`
7. Store the evidence URL, observation date, reviewer, and note.

### Prohibited discovery pattern

Do not brute-force provider tenant namespaces or every possible origin and
destination pair. A national sweep must use strict rate limits, caching, terms
review, and preferably provider cooperation.

### Acceptance criteria

- The operator registry contains 62 dated and reviewed TicketWeb statuses.
- No operator is called absent without official or provider evidence.
- All discovered non-TicketWeb systems are assigned a lawful adapter plan.
- The two known non-federation TicketWeb tenants remain separately classified
  and do not inflate the official 62 count.

## 9. Phase 4: normalize 19,872 source stop records

Estimated effort: 4 to 8 person-weeks after lawful export acquisition

### Processing stages

```text
Permitted source rows
    |
    v
Schema validation
    |
    v
Text and language normalization
    |
    v
Coordinate validation and quarantine
    |
    v
Candidate matching
    |
    v
Human review of ambiguous groups
    |
    v
Canonical stop place and boarding stop
```

### Normalization rules

- Preserve every source ID and raw label.
- Normalize Greek tonos, casing, whitespace, punctuation, and common
  abbreviations without deleting the original.
- Maintain Greek, English, and Albanian display names independently.
- Separate a station or terminal from its individual boarding stops.
- Never merge only on name similarity.
- Use distance, municipality, operator, route membership, booking graph, and
  reviewer evidence together.
- Quarantine zero coordinates, swapped latitude and longitude, out-of-country
  points, impossible jumps, and duplicate coordinates with conflicting names.
- Record merge and split decisions as reviewable history.

### Review queues

- Exact safe matches.
- Probable duplicates.
- Cross-operator shared terminals.
- Same-name places in different regions.
- Invalid or missing coordinates.
- Island and ferry-linked services.
- Temporary and seasonal stops.

### Acceptance criteria

- All permitted raw records are imported, rejected, or quarantined with reason.
- Zero invalid coordinates reach the public database.
- Every canonical stop retains source lineage.
- Automated match precision is sampled and reviewed.
- Ambiguous merges require a named human reviewer.
- A repeat import produces stable canonical IDs.

## 10. Phase 5: populate routes, trips, and calendars

Estimated effort: 8 to 16 person-weeks

### Adapter priority

1. Documented operator or government feeds.
2. Stable, permitted structured exports.
3. Reviewed static HTML tables.
4. Reviewed PDFs.
5. Images or announcements requiring double-entry transcription.
6. Manual operator submissions through a controlled admin workflow.

Every adapter needs:

- Parser version.
- Source fixture.
- Response and file-size bounds.
- Timeouts and retry policy.
- Semantic diff.
- Effective-date parsing.
- Seasonal and holiday exception handling.
- Quarantine thresholds.
- Maintainer and review owner.

### TicketWeb observation plan

Where written permission exists:

1. Import the tenant's permitted stop inventory.
2. Identify web-active origins.
3. Query `reachableStops` for those origins.
4. Build the directed sellable graph.
5. Query a bounded rolling date window.
6. Store observations as date-specific bookability, not as an inferred
   permanent timetable.
7. Expire stale observations.
8. Use official published calendars as the recurring schedule authority.

### Timetable validation

- Departure and arrival chronology.
- Stop sequence integrity.
- Calendar coverage.
- Holiday and seasonal exceptions.
- Duplicate trip detection.
- Plausible duration bounds.
- Source-to-normalized row counts.
- Comparison with official operator presentation.
- Manual review of first publication and large semantic changes.

### Acceptance criteria

- Every published trip has operator, route, pattern, calendar, source, effective
  date, retrieval time, and review state.
- No TicketWeb observation is presented as a permanent recurring service.
- Every one of the 62 operators reaches R3 or has a documented blocked state.
- Daily and weekly freshness alerts identify stale sources.
- Rollback can restore the previous compiled release.

## 11. Phase 6: verify national road geometry

Estimated effort: 4 to 10 person-weeks

Ordered stops are not proof of the exact road used.

### Geometry source order

1. Operator-provided geometry.
2. Government-authoritative geometry.
3. Reviewed OpenStreetMap relation with required attribution.
4. Generated road routing constrained by ordered reviewed stops, followed by
   human review.

### Validation

- Geometry begins and ends at the correct terminals.
- Stops occur in the correct order near the geometry.
- Route does not cross water without a documented ferry segment.
- Restricted or impossible roads are rejected.
- Express and local variants remain distinct.
- Directional variants are not created by simply reversing a one-way path.
- Distance and duration outliers enter quarantine.
- Reviewer sees map, stops, source, and change diff together.

### Acceptance criteria

- Each geometry has source, method, reviewer, version, and confidence.
- No inferred straight line is labelled as an exact route.
- At least 90 percent of published journey patterns have reviewed geometry for
  national 1.0.
- Missing geometry does not block a correct text timetable.

## 12. Phase 7: passenger demand and revenue

Estimated effort: variable, partnership-dependent

This is a separate analytics workstream and is not required to launch a
trustworthy journey information MVP.

### Lawful sources to pursue

- Aggregated operator ridership and ticket sales.
- Ministry and regional authority statistics.
- Public-service contracts and annual reports.
- Anonymized provider aggregates under agreement.
- Station footfall studies.
- Passenger surveys with documented methodology.

Do not estimate demand from repeated booking queries, seat-map visibility, or
endpoint behavior. That would be methodologically weak and may violate terms.

### Minimum analytical contract

For every metric record:

- Operator and geography.
- Reporting period.
- Unit and currency.
- Gross or net treatment.
- VAT treatment.
- Passenger, journey, ticket, or seat denominator.
- Inclusion and exclusion rules.
- Source and legal basis.
- Revision status.

### Acceptance criteria

- Demand and revenue endpoints remain absent until data passes legal and
  methodological review.
- Published aggregates have no personally identifiable information.
- Coverage gaps and uncertainty are displayed.

## 13. Phase 8: Raspberry Pi staging and production

Estimated effort: 1 to 2 person-weeks after the data path is stable

### Deployment sequence

1. Inventory the Pi OS, disk, memory, Python, nginx, systemd, backups, TLS, and
   current Syrmos service.
2. Produce a consistent backup and prove restoration on a temporary location.
3. Deploy Dromologio to a staging path, database, output directory, service,
   port, and hostname separate from rail.
4. Run migrations and seed the 62-operator registry.
5. Import only approved fixtures and permitted sources.
6. Compile a release-addressed public database and JSON packs.
7. Run API, rights-gate, release-ID, ETag, and offline-pack smoke tests.
8. Run bounded concurrency and memory tests appropriate for the Pi.
9. Configure timers, logs, disk alerts, source freshness alerts, and backup
   retention.
10. Prove rollback to the previous public database and manifest.
11. Promote the already-tested immutable release to production.

### Required production properties

- Separate ingestion and public SQLite databases.
- Read-only public API database.
- Manifest written last.
- API returns 503 while database and manifest release IDs disagree.
- Atomic release replacement.
- Previous release retained for rollback.
- Request rate limits and bounded upstream acquisition.
- No upstream secret in clients or repository.
- Health endpoint distinguishes process, database, release, and freshness.

### Acceptance criteria

- Pi cold-boot recovery is tested.
- Backup restoration is tested.
- Deployment and rollback are documented and repeated.
- Seven-day staging soak has no release mismatch or unbounded disk growth.
- Live tests do not issue or reserve a ticket.

## 14. Phase 9: iOS, Android, and Web

Estimated effort: 6 to 10 person-weeks, partly parallel

### Shared product journeys

- Search origin, destination, and date.
- Browse all 62 operators.
- View station and nearby stops.
- View journey results and transfers.
- View trip details and intermediate stops.
- View reviewed road geometry.
- Save favorites and recent searches locally.
- Download offline regional or operator packs.
- Open the official booking page.
- View source, freshness, coverage, and correction link.

### Required screens

1. Coach home and search.
2. Place and stop picker.
3. Journey results.
4. Journey detail.
5. Route map.
6. Operator directory.
7. Operator detail and official contacts.
8. Station detail.
9. Offline pack management.
10. Coverage and source transparency.
11. Service alerts and seasonal changes.
12. Settings, language, accessibility, and disclaimer.

### Product rules

- Do not show a purchase button if only a timetable is known.
- A booking button must name the external operator.
- Display local Greek time and explicit service date.
- Distinguish scheduled, observed bookable, changed, cancelled, and unknown.
- Show partial results instead of silently hiding coverage gaps.
- Preserve English, Greek, and Albanian parity.
- Keep the basic directory and reviewed schedules usable offline.

### Acceptance criteria

- Shared domain contracts are implemented once and consumed by all clients.
- iOS, Android, and Web use the same release ID and server-side timetable
  semantics.
- Offline and online results agree for the same release.
- VoiceOver, TalkBack, keyboard, contrast, dynamic type, and reduced motion are
  tested.
- External booking handoff is tested without completing a purchase.

## 15. Release sequence

### Milestone A: governed foundation

- Legal requests sent.
- Source rights matrix active.
- Repository runtime state protected.
- 62-operator evidence package template complete.

### Milestone B: three-operator pilot

Choose three operators with different source shapes:

- One permitted TicketWeb operator.
- One operator with stable HTML or PDF timetables.
- One island or seasonal operator.

Complete R0 through R6 for all three before scaling adapters.

### Milestone C: permitted platform cohort

- Process every provider-confirmed and permitted TicketWeb operator.
- Complete stop normalization and bounded rolling observations.
- Publish only rights-cleared data.

### Milestone D: national data beta

- All 62 at R1 or documented blocked.
- At least 50 at R3.
- API and offline packs available on Pi staging.
- Internal iOS, Android, and Web coach screens operational.

### Milestone E: national public beta

- Legal and trademark gates passed.
- Public source transparency page live.
- Corrections workflow and monitoring active.
- No ticket issuance.

### Milestone F: national 1.0

- National 1.0 gates from section 3 passed.
- Production rollback and recovery proven.
- Every client has passed accessibility and release tests.

## 16. Team and ownership

A realistic small delivery team:

- Product and partnerships lead, 0.5 to 1 full-time equivalent.
- Backend and data platform engineer, 1 full-time equivalent.
- Transit data and GIS engineer, 1 full-time equivalent.
- Greek-speaking timetable reviewer, 1 full-time equivalent during population.
- KMP/mobile/Web engineer, 1 full-time equivalent.
- SRE support, part-time.
- Qualified Greek/EU counsel, external.

One person can build the system, but the 62-operator acquisition and review
program should not depend on one unreviewed person.

Expected technical effort is roughly 25 to 45 person-weeks after source access.
External permission, legal, and operator response time can extend elapsed time.

## 17. Operating cadence

### Daily

- Refresh enabled machine-readable sources.
- Compile only when reviews and rights gates pass.
- Check stale sources, failed adapters, release mismatch, disk, and backup.

### Weekly

- Recheck operator source registry.
- Review timetable semantic diffs.
- Review stop and geometry queues.
- Contact operators with unresolved changes.
- Publish coverage report.

### Monthly

- Restore a backup in an isolated environment.
- Review source rights and terms changes.
- Audit attribution and booking links.
- Sample public timetable accuracy across operators.
- Review API and client performance.

### Seasonal

- Create Easter, summer, school-year, Christmas, and public-holiday campaigns.
- Require effective dates and exception validation.
- Archive superseded artifacts without deleting lineage.

## 18. National scorecard

Publish these metrics by operator and nationally:

- Operators at R0 through R6.
- Operators verified live, not TicketWeb, unverified, blocked, or retired.
- Canonical stop places.
- Source stop rows imported, quarantined, merged, and unresolved.
- Routes and journey patterns.
- Trips in the next 7, 30, and 90 days.
- Calendar and exception coverage.
- Journey patterns with reviewed geometry.
- Booking handoffs tested.
- Sources current, stale, failed, and rights-blocked.
- Median and oldest source age.
- Adapter success rate.
- Corrections awaiting review.
- Public release ID and build time.

Demand and revenue must remain separate until lawful data exists.

## 19. Immediate next 30 days

### Week 1

- Adopt Dromologio as the working name.
- Preserve and diagnose the SQLite sidecar state.
- Create the legal and provider question pack.
- Create 62 operator evidence-package records.
- Select the three pilot operators.

### Week 2

- Send provider, NAP publisher, and operator data requests.
- Implement the first non-TicketWeb adapter with fixtures.
- Build the stop review queue.
- Define client API contracts and coverage states.

### Week 3

- Import one permitted pilot corpus.
- Normalize and review pilot stops.
- Create route and calendar validation reports.
- Build the coach search and result screen skeletons against fixtures.

### Week 4

- Complete the three-operator pilot review.
- Deploy an isolated Pi staging instance.
- Test compile, publish, rollback, offline pack, and client release-ID parity.
- Write a go or no-go review for scaling to all 62.

## 20. Immediate blockers requiring external authority

The following cannot be solved responsibly through more scraping:

- TicketWeb public redistribution permission.
- A retained permitted export of the 19,872 source stop records.
- Provider confirmation for ambiguous TicketWeb tenants.
- A stable NAP workbook or replacement feed.
- Qualified legal advice.
- Private national passenger demand and revenue data.

Until those are resolved, implementation should continue on official permitted
operator sources, fixtures, review tooling, Pi staging, and client contracts.

## 21. Final truth statement

Today, the project has a complete 62-operator registry, ingestion and review
foundation, bounded provider-query design, public rights gate, compiler, API,
and Raspberry Pi deployment pattern.

It does not yet have every national timetable, normalized stop, exact road
geometry, verified booking provider, demand record, production deployment, or
coach client.

The product becomes Dromologio when those gaps are closed through evidence,
permission, human review, and repeatable operations, not through a one-time
unbounded scrape.
