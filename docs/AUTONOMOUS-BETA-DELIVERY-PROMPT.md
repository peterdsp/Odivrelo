# Complete product rename and autonomous delivery through version 1.0.0

This is an execution prompt to paste into the implementation agent. Creating this document does not implement, approve, build, test, or publish the application. The authorization below takes effect when the user invokes this prompt for execution.

---

You are responsible for replacing the HodoMap identity entirely with a newly selected brand and taking the product from its actual current repository state to a complete, working, verified beta across its supported platforms and a deployed Web version `1.0.0`. HodoMap is the rejected former name, not the final brand. Execute the work. Do not finish with a plan, scaffold, checklist, screenshots of mockups, or instructions for me to perform work you can do.

## 1. Autonomous working agreement

I authorize you to make routine product, architecture, implementation, testing, accessibility, localization, build, packaging, and beta-release decisions for this task without asking me questions. Choose sensible defaults from the repository and current official platform documentation, record consequential decisions, and continue.

Do not ask “should I continue?”, “may I commit?”, “which framework?”, “which name do you prefer?”, or “what should I do next?”. Resolve ordinary ambiguity yourself. Progress through the entire delivery sequence, not just naming, Phase 0, or the first platform. Keep updates concise and factual.

This instruction expands the earlier pilot-only engineering scope to include working iOS, Android, and Web beta applications, shared logic, backend services, operations, and distribution preparation. Record this scope change in a dated decision during execution. Preserve the original pilot evidence and history. Do not represent the five-traveller validation gate, source permissions, or human data reviews as passed merely because engineering scope has expanded.

Within this task you may:

- Inspect the repository, authenticated project tools, configured infrastructure, installed SDKs, simulators, emulators, and existing release accounts.
- Create a `codex/` branch; implement and fix code; install ordinary project dependencies within the available environment; run builds and tests; create scoped commits; push the task branch; and open or update a draft PR when the existing GitHub account permits it.
- Use subagents for independent, bounded work if available, assigning separate ownership and integrating their results yourself. Do not create separate user-facing tasks unless I separately request them.
- Run existing CI and create project-specific CI workflows with bounded jobs and artifact retention. Prefer available local resources; do not purchase services or increase billing limits.
- Deploy an isolated beta to an unambiguously identified, already authorized destination for this project using existing credentials and capacity, after its gates pass. Prepare and upload signed mobile beta builds to the matching existing app records and internal test tracks when access allows it. Do not create new tester audiences, send invitations, or enable public test links unless already configured and authorized.
- For version `1.0.0`, deploy the public Web application at `https://<newname>.peterdsp.dev`. This explicitly authorizes the project-specific hosting configuration for the selected new name, DNS record, HTTPS certificate, and deployment needed for that hostname using available authorized accounts and capacity. Inspect existing records first, preserve unrelated services, and do not purchase hosting or alter apex, mail, wildcard, or unrelated subdomain records.
- Fix problems found during verification and repeat affected checks until they pass.

Do not bypass platform permissions, sandbox restrictions, source rights, account security, required legal declarations, or human review requirements. Do not purchase anything, accept legal agreements for me, invent compliance answers, modify unrelated production services, force-push, delete user work, or publish a public mobile-store release. The public Web `1.0.0` deployment at `https://<newname>.peterdsp.dev` is explicitly authorized above. Do not message operators, recruiters, testers, or other people. Draft any necessary outreach without sending it.

When an external prerequisite is unavailable, make the safe choice without interrupting me: finish everything independent of it, prepare the exact remaining artifact or command, and record the blocker. Never treat silence as approval or fabricate credentials, team identifiers, signing identities, source rights, or approvals. A blocked upload must not block building another platform. A missing physical device must not block available simulator testing. An unavailable source must not block engineering against clearly separated synthetic fixtures.

If a tool-enforced approval is unavoidable, obey it. Do not route around it. Continue unaffected work and accurately report the blocked action and reason. The no-questions requirement concerns avoidable consultation; it does not grant capabilities or permissions that do not exist.

## 2. Platform scope and delivery meaning

### Mandatory new name and complete rebrand

Find and independently select an entirely new product name. I do not want HodoMap, Hodo, or a cosmetic variation of either. Use **Nostavela** as a reference for the kind of distinctive, evocative, memorable invented name I like, not as a name to reuse or imitate by changing a few letters. Do not assume Nostavela is available, belongs to this product, or should become this product's identity.

Generate a meaningful internal shortlist, assess the strongest candidates, choose one, and implement it without asking me to choose or approve a shortlist. Favor a name that is easy to say, spell, remember, and use in Greek, English, and Albanian; fits journeys and discovery; and can grow beyond a literal bus-map description. Reject Hodo-derived names, generic Bus/Map/KTEL combinations, confusing lookalikes, and names implying official operator affiliation. Do not invent etymologies or claim an invented word has a verified translation.

Before selection, perform current public searches for exact and similar names across travel products, general web results, Apple App Store, Google Play, relevant trademark search services, and the existing `peterdsp.dev` project/subdomain inventory accessible to you. Check obvious pronunciation, meaning, and spelling problems in the three product languages. Record dated sources, collisions, and limits of the checks; this is preliminary screening, not a guarantee of worldwide uniqueness or legal clearance. Reject obvious conflicts and choose another candidate autonomously. If a particular search service is unavailable, document that limitation instead of claiming it passed. Do not buy domains, register trademarks, or contact anyone.

Record the chosen display name and a simple lowercase ASCII DNS-safe slug in `docs/beta/BRAND-DECISION.md`, with a concise rationale, screened alternatives, evidence, and migration inventory. Use that exact slug for `<newname>.peterdsp.dev`. Resolve the name early, before final app identities, assets, deep links, hosting, or store materials are created. A separately purchased domain is not a release dependency.

Replace the old identity across all current product surfaces: app and launcher names, navigation/onboarding, logos and wordmarks, icons and splash screens, PWA manifest, browser titles and favicons, metadata and social previews, accessibility labels, translations, store listings, screenshots, support/privacy/source pages, active documentation, release notes, generated artifacts, CI/deployment labels, and public URLs. Adapt the existing visual language deliberately to the new name and create original brand assets where necessary. Do not merely change one heading while shipping HodoMap elsewhere.

Audit technical names too: package/module names, imports, build targets, artifact names, environment-variable prefixes, service names, configuration, URL schemes, associated domains, API documentation, and project metadata. Rename safely with focused verification. For existing persistent data, installed clients, secrets, infrastructure, or immutable store identifiers, preserve compatibility through explicit mappings or migrations; do not lose saved trips, break signing/update identity, or abandon a registered app just to remove an internal string. Use the new name for identifiers that have not yet been registered. Keep genuinely necessary legacy references only in a documented allowlist, never as current user-facing branding.

The existing `/Users/peterdsp/git/HodoMap` checkout is the starting location, not the product identity. Do not move the active checkout or rename the remote repository as an unexamined global substitution. If a remote repository rename is appropriate and supported by available project permissions, inventory and repair integrations and links first; otherwise retain the technical location and document it. Preserve historical documents, migrations, attribution, and audit evidence accurately, clearly identifying former-name references as historical.

Before release, search source, generated output, installed app screens, metadata, store materials, and the deployed site for remaining HodoMap/Hodo branding and unresolved `<newname>` placeholders. Fix every unintended occurrence and verify all final URLs resolve to the selected brand. A new name chosen in a document alone does not satisfy this requirement.

### Required platforms

Deliver all of the following, with equivalent essential traveller capabilities and appropriate platform behavior:

| Target | Required implementation | Required output |
|---|---|---|
| iPhone, including iPhone Duo | Native SwiftUI, MapKit, shared KMP logic | Runnable simulator build; signed archive and TestFlight build when signing/account access exists |
| iPad | Adaptive native iOS application | Tablet and multitasking verification in the same iOS product |
| Android phones | Native Jetpack Compose and shared KMP logic | Installable APK; signed release APK/AAB and internal-track upload when credentials exist |
| Android tablets and foldables | Adaptive native Android application | Runtime checks for large screens, folds, hinges, resizing, and continuity |
| Mobile Web | Responsive, accessible React/TypeScript application | Production build, tested browser flows, installable PWA where supported |
| macOS, Windows, Linux, ChromeOS | Desktop Web/PWA with keyboard and pointer support | The same production web beta, verified in available desktop browsers |
| Backend and ingestion | Python services and governed release pipeline | Reproducible service packaging, health checks, deployment and rollback procedures |

Native desktop binaries, watches, TV, automotive, and spatial apps are not implied by “every platform” in this repository. Desktop coverage is explicitly Web/PWA. Do not claim native desktop packages or OS/browser combinations that were not built or tested.

The target is an operational, product-complete beta over verified coverage. “National” coverage remains a separate evidence claim: satisfy the repository's national-beta data gates before using that label. A functioning product with one cleared corridor is a limited-coverage beta, not a completed national dataset.

The release target is version `1.0.0`. At minimum, the complete Web application must be publicly deployed, working, and verified at `https://<newname>.peterdsp.dev` before version `1.0.0` can be called delivered. The naming convention is `<appname>.peterdsp.dev`; resolve `<newname>` to the lowercase DNS-safe slug of the newly selected brand before configuring, building, or deploying the release. Never deploy a literal placeholder or use `hodomap` as the final public hostname. Native beta deliverables remain required, but mobile signing or store processing must not prevent an otherwise release-ready Web deployment. A local build, preview URL, deployment configuration, or placeholder page does not satisfy this release requirement.

## 3. Inspect current reality before changing it

Work in `/Users/peterdsp/git/HodoMap`. Read applicable `AGENTS.md` files and relevant skills. Follow the user's explicit instructions over conflicting project preferences, while respecting higher-priority tool and safety boundaries.

Read at least:

- `README.md`, `CONTRIBUTING.md`, `SECURITY.md`.
- `docs/INDEX.md`, `docs/PILOT_DECISION.md`, `docs/pilot/README.md`, `docs/pilot/CORRIDOR_BRIEF.md`, and the pilot tickets.
- `docs/phase0/README.md` and all Phase 0 tickets.
- `docs/ARCHITECTURE.md`, `docs/DATA_GOVERNANCE.md`, `docs/DESIGN_SYSTEM.md`, `docs/PRODUCT_DIFFERENTIATION.md`, `docs/ROADMAP.md`, and `docs/NATIONAL_EXECUTION_PLAN.md`.
- `docs/LIVE_COACH_MAP_AND_ETA.md`, the app/shared/server READMEs, design tokens, actual staging code and tests, source registry, and Raspberry Pi runbooks.

Verify branch, worktree, staged changes, remotes, authenticated account identity, dependency state, installed toolchains, and available devices. Do not output credential values. Resolve existing app IDs and beta destinations from verified configuration. If identity or destination is ambiguous, produce local artifacts and record the distribution blocker instead of guessing.

The drafting-time tree contained an unfinished `syrmos_admin` → `hodomap_ktel` rename, new Phase 0 documents, and pre-existing pilot edits. Treat this as a lead to recheck, not permission to discard or automatically stage every file. Preserve the original staged/unstaged changes and ownership; never reset, stash away, or overwrite unrelated work. If needed, use an isolated checkout and carefully transfer only the relevant, understood changes while leaving the original intact.

The pasted handoff reported a missing `generator` import in `server/ktel-staging/tests/test_ktel.py`, which prevented the full suite from loading. Reproduce the present behavior. Repair the actual integration contract; do not remove meaningful assertions simply to get green output. If a Syrmos-only behavior is obsolete, document why and replace its coverage with the corresponding HodoMap publishing/GTFS behavior.

Do not copy unrelated Syrmos application code or touch its live service. Existing operator registry counts are registry evidence, not timetable coverage.

Create a dependency-ordered delivery ledger under `docs/beta/`. Choose versions and architecture once supported by current compatibility evidence, lock them, and avoid repeatedly replacing the stack. Prefer the repository's KMP core, SwiftUI, Compose, React/TypeScript, Python/FastAPI, and SQLite boundaries unless a demonstrated issue warrants a documented change.

## 4. Complete the data and service foundation

Finish and verify Phase 0, then build the vertical data path needed by the applications:

1. Complete the package migration under the selected new identity without breaking intentional legacy environment-variable compatibility. Treat `hodomap_ktel` as an intermediate historical name where appropriate, not a required final package name.
2. Make migrations, seed, bounded ingestion, normalization, review, compilation, query, and GTFS export work end to end on lawful fixtures.
3. Preserve separate candidate/review and read-only public datasets. Only permitted sources and approved entities may enter public releases.
4. Establish versioned OpenAPI/JSON contracts in `data/schemas/`, including stable entity IDs, service dates, coverage, source lineage, freshness, geometry confidence, official purchase actions, and release identity.
5. Implement the minimum reliable API under `server/api/`, reuse the staging modules intentionally, and keep private administrative capabilities separate from public access.
6. Generate immutable, checksummed public datasets and offline packs. Publish the manifest last, verify release consistency, support rollback, and reject corrupt/mixed releases.
7. Provide practical private review commands or UI with an audit trail, secure access, corrections, quarantine, and takedown support. Never silently mark unresolved records approved.
8. Produce a working staging deployment, backups, health/readiness checks, bounded logs, monitoring, restore, and rollback verification. Use existing Pi configuration only when that specific target is accessible and authorized; otherwise finish a locally runnable equivalent and deployment package.

Implement correct `Europe/Athens` service-date handling, midnight crossings, GTFS times beyond 24:00, calendar exceptions, daylight-saving transitions, invalid coordinates, broken references, duplicate inputs, malformed files, missing fields, and source expiry. Resolve timezone semantics once and test them against the same contract across clients. Do not let each UI invent its own timetable engine.

Attempt the proposed Athens–Delphi corridor first only if current evidence supports it; use documented fallbacks or another justified corridor if rights, date validity, completeness, or boarding locations fail. Reverify departure terminals and purchase actions from official sources. Do not publish historical assumptions as current travel instructions.

Permission for a catalog page does not automatically license every linked dataset. Keep restricted provider integrations, including TicketWeb where approval is absent, disabled. Respect the acquisition service's retention, request budget, rate-limit, and permission rules.

If no real corridor passes the data gate, finish a clearly labelled engineering demo with invented test locations and services that cannot be mistaken for real departures. Keep it separate from public travel data, production search indexing, and store screenshots implying actual coverage. This does not satisfy real-data beta readiness. Record the exact missing evidence and continue all other work.

## 5. Build complete traveller flows on every primary client

Implement production-quality screens and navigation under the selected new brand. Inspect the existing Aegean teal, limestone, sun amber, and design tokens as a starting point; retain or refine them coherently for the new identity. Replace old-name logos and wordmarks, and update the design system and assets consistently. Avoid generic visuals and fabricated app screenshots; release screenshots must show the running rebranded product.

Required flows:

- First launch that works without a mandatory account, unnecessary permissions, or a network-only onboarding trap; Greek, English, and Albanian throughout.
- Origin/destination search, useful place disambiguation, service-date selection, recent searches, accessible filters, valid results, and clear empty/error/partial-coverage states.
- Journey detail with operating date, operator, stops, departure/arrival semantics, exact reviewed boarding point when available, map/list alternatives, restrictions, provenance, freshness, and confidence.
- Official booking handoff, returning to the same app state afterward; verified ticket-office/contact fallback when online purchase is unavailable. Never imply this product sells or issues tickets.
- Operator and station pages with verified details, attribution, relevant service coverage, and correction paths.
- Favorites, saved trips, and Trip Ready views that remain useful offline, visibly showing cached release and freshness.
- Offline packs with size information, progress, retry/cancel, integrity verification, atomic installation, interrupted-download recovery, updates, rollback, and deletion. Show what maps and data are actually available offline.
- A private travel wallet for explicitly imported supported ticket files. Use local platform protection, size/type validation, safe rendering, deletion, and clear backup behavior. Do not upload tickets, expose barcodes in logs/analytics, or cache them in public web/service-worker caches. On Web, explain storage persistence limitations honestly and provide equivalent import/view/delete behavior where supported.
- Opt-in reminders and notification preferences with permission-denied behavior. Use local reminders where sufficient; browser or remote push only where supported and configured. Do not include passenger names, booking references, ticket images, or barcodes in notification payloads. Do not claim scheduled background reminders on browsers that cannot deliver them.
- Settings for language, appearance, accessibility, offline storage, privacy, notifications, data sources, support, licenses, version, and diagnostic information without secrets.
- Stable, validated deep links to public journey/operator/station content, sensible expired/unknown-link handling, and return-state preservation. Enable verified app/universal links only for an actually controlled configured domain.

Provide loading, empty, stale, restricted, unavailable, retry, permission-denied, storage-full, server-error, offline, update, and expired-service states. Every visible button must do something useful or clearly explain its genuine unavailability. Do not leave TODO buttons, fake successes, forced empty screens, or unconditional mock results in beta builds.

Keep scheduled, model-predicted, online-estimated, and live positions visibly distinct. Implement the applicable degraded states described in the product documents. Enable predictions only with defensible source inputs, uncertainty, and tests. Enable live tracking only for an authorized working feed, with age and disconnect behavior. When inputs are absent, provide schedule/stop information; do not animate a made-up live coach to make the app look complete.

## 6. Shared logic and platform integration

- Put shared domain models, network/offline contracts, persistence, saved-trip logic, freshness rules, and deterministic feature state in the appropriate `shared/core/` and `shared/features/` modules.
- Wire shared code into the actual Android application and iOS framework/project. Prove linked release builds; compiling isolated shared files is insufficient.
- Keep native navigation, maps, permissions, file handling, secure storage, lifecycle, accessibility, and platform notifications in platform adapters.
- Generate or validate TypeScript contracts from the same authoritative schemas. Fail CI on incompatible generated contract drift.
- Support cancellation, retries with bounds, timeouts, backpressure where needed, cache invalidation, and process/lifecycle recovery. Keep network and database work off UI threads.
- Avoid oversized scaffolding, unnecessary accounts, and backend dependencies that do not serve a delivered feature. Retain an upgrade path without designing speculative infrastructure.

## 7. iPhone Duo, Android foldables, tablets, and changing windows

Treat adaptive behavior as part of the first screen implementation and release gate. Do not defer it until the end or infer support from device names.

Use these official starting points and recheck their current SDK/API guidance before implementation:

- Apple: https://developer.apple.com/design/human-interface-guidelines/designing-for-iphone-duo
- Apple: https://developer.apple.com/videos/play/tech-talks/111466/
- Android: https://developer.android.com/develop/adaptive-apps/guides/foldables/learn-about-foldables
- Android: https://developer.android.com/develop/adaptive-apps/guides/app-orientation-aspect-ratio-resizability

### Common adaptive contract

Compute layouts from the actual usable window/container, safe areas, text size, keyboard, and any platform-reported occluded or reserved regions. Do not hardcode fold locations, hinge widths, device model names, or a single full-screen width.

Use compact layouts for constrained space and meaningful additional panes when space permits: search/results beside journey detail or map, with useful reading widths. A wide layout is not a stretched phone screen. Keep important controls reachable and all content accessible when only one pane fits. Adapt navigation without duplicating destinations or losing the user's current task.

Across resize, rotation, fold/unfold, split-screen, background/foreground, and supported process restoration, preserve query, service date, filters, selected journey, back stack, relevant scroll position, map intent, saved-trip state, and ticket viewing state. Restore the relevant state securely, not indiscriminately persisting sensitive document data. A geometry change must not restart a download, duplicate a reminder, reset a purchase handoff, or issue repeated identical searches.

### Apple

Implement flexible SwiftUI layouts and native navigation suitable for ordinary iPhones, iPads, and iPhone Duo. Inspect the installed Xcode SDK and official APIs before coding Duo-specific behavior. Respect platform safe areas and supported reserved-region information with compile-time/runtime availability guards. Preserve a robust geometry-driven fallback on older systems. Do not invent symbols or infer a foldable posture from aspect ratio alone.

Test the official Duo simulator/runtime and posture tools if available. Include supported outer/inner configurations, rotations, resizing/multitasking, system overlays, keyboard, large Dynamic Type, and asymmetric usable areas. Verify any required app configuration using current official guidance. If that runtime is missing, test adaptive layouts on available Apple simulators and explicit geometry fixtures, and report Duo runtime coverage as blocked. Synthetic geometry checks are supplementary evidence, not proof of Duo runtime or physical-device support.

### Android

Use current supported Jetpack adaptive/window APIs, window size classes, and actual folding-feature bounds, posture, and occlusion. Respect system bars, display cutouts, keyboard, edge-to-edge content, and resizable activities. Keep dialogs, boarding actions, ticket barcodes, and important text out of obstructed regions. Do not lock orientation to hide layout bugs or assume every crease fully occludes content.

Verify ordinary phone, narrow outer display, expanded inner display, book/tabletop configurations where supported, tablet, split-screen, and freely resized windows. Exercise real emulator posture changes and activity recreation, plus process-death restoration. Use connected physical foldables if available, without disturbing personal device data.

### Web/PWA

Use responsive container layouts and standard browser capabilities. Feature-detect any viewport-segment support and retain a complete single-viewport fallback. Test keyboard, pointer, touch, zoom, virtual keyboard, and narrow/wide windows. Verify service-worker updates, offline navigation, install behavior where supported, and recovery from cleared/evicted storage. Avoid caching operator booking sessions, private ticket documents, administrative responses, or stale travel claims indefinitely.

## 8. Runtime verification and release gates

Build and run the actual apps, inspect their screens, and exercise real flows. Use relevant installed iOS/Android/browser testing skills and tools. Screenshots from running builds must be retained with device/runtime, build identity, scenario, and date. Never substitute design mockups or static HTML for native runtime evidence.

Maintain `docs/beta/TEST-MATRIX.md` with per-row status: `passed`, `failed`, `not-run`, or `blocked`; command/scenario; artifact link; build commit; runtime/device; and limitation. No pass without evidence. Rebuild/retest affected artifacts after final changes; do not attach evidence from an older build as if it tested the delivered one.

Mandatory scenarios:

1. Clean install and cold launch in all three languages; light/dark appearance and system-language changes.
2. Search → correct service date → result → boarding detail → official purchase/contact action → return to preserved state.
3. Reviewed offline pack download → airplane mode → restart → saved journey still usable with accurate stale/cache labels.
4. Corrupt/interrupted downloads, insufficient storage, failed API requests, changed release manifest, rollback, and expired data.
5. Denied location/notification/file permissions, delayed network, empty coverage, invalid input, unknown deep links, and missing maps.
6. Protected ticket import/view/delete, app restart, and relevant backup/log/cache inspection with synthetic ticket documents.
7. Notification opt-in/out, rescheduling/cancellation, time-zone changes, and permission revocation on supported targets.
8. Date/calendar/DST cases, overnight trips, provenance exclusion, rights exclusion, review exclusion, quarantine, and atomic public release behavior.
9. Fold/unfold or equivalent supported posture transitions while searching, inspecting detail, using maps, downloading a pack, and viewing a ticket; rotation and constrained multitasking during the same flows.
10. Accessibility: actual available VoiceOver/TalkBack/browser screen-reader checks, focus order, labels, large text, keyboard, contrast, reduced motion, error announcements, and a non-map route to essential information.
11. Production Web build in available Chromium, WebKit/Safari, and Firefox runtimes; mobile and desktop widths; navigation/reload/deep links; install/offline/update behavior. List untested host OS combinations honestly.
12. Backend cold start, migration, backup restore, readiness failure, release mismatch, bounded ingestion, and rollback; remote checks only when the actual beta service is accessible.

For layout continuity tests, hold dataset, clock/service date, locale, theme, and network conditions constant while changing geometry; otherwise unrelated changes can hide a state-loss bug. Then run separate lifecycle/network/time-change scenarios.

Add meaningful domain, migration, contract, integration, and regression tests, plus a focused set of end-to-end tests. Do not replace runtime verification with large counts of shallow snapshots. Do not remove assertions, skip failing product requirements, or lower gates to present success.

Measure launch, search, scrolling, map interaction, memory, download size, and offline operation on named targets. Set justified beta budgets, capture results, and fix observable stalls, crashes, leaks, and unusable transitions. Do not invent universal performance claims from desktop unit-test timings.

## 9. CI, packaging, and beta distribution

Create reproducible scripts and CI under `scripts/` and `.github/workflows/` for dependencies, checks, shared core, backend, Web, Android, and iOS. Use appropriate runner operating systems, locked dependency versions, narrow token permissions, protected signing secrets, and explicit release jobs. Do not expose secrets to untrusted PR workflows. Keep ordinary CI distinct from distribution triggers.

Target release version `1.0.0`, using prerelease identifiers where supported and unique mobile build numbers consistent with existing release records. Do not overwrite an existing release or reuse an uploaded build number. Stamp artifacts with version, source commit, build environment, and compatible public-data contract/release. Exclude local secrets, passenger documents, and runtime databases from source control and distributable bundles.

### iOS/iPadOS

Produce and launch simulator builds, then archive the actual Release configuration for device distribution. Validate entitlements, usage descriptions, privacy manifests, bundled resources, localizations, KMP framework architectures, symbols, bundle identity, and signing/export options. Use current App Store Connect requirements and the existing verified app/team configuration.

When credentials permit, export the distributable artifact, upload to TestFlight, and inspect processing/compliance status. Make it available to an existing authorized internal group only when the platform and prior configuration allow it. Do not invent review answers or call “upload accepted” the same as “testers can install.” Prepare external-beta review materials when relevant; submission/review/approval remain distinct states.

If signing is unavailable, still deliver the working simulator artifact, all passing builds/checks possible, and exact signing/export configuration. An unsigned build or simulator app is not an installable iPhone beta. Report the missing capability precisely.

### Android

Produce an installable APK for QA and a proper release bundle. Validate package identity, release signing when available, manifest, target/min SDK against current policy, version codes, permissions, native libraries, localization, resource shrinking behavior, and symbols/mapping files when applicable. Smoke-test the release configuration as well as debug.

Use the existing protected upload key and matching Play app when available. Verify bundle upload and internal-track availability; do not use a debug key for store distribution. If release credentials are missing, deliver the debug/QA APK and prepared unsigned release outputs with accurate labels. Do not create an unprotected disposable production signing identity just to claim completion.

### Web/PWA and service

Produce a deployable production artifact with the required server/static runtime documented, environment validation, CSP/security headers appropriate to the app, controlled CORS, caching/update policy, health checks, and rollback. Use an isolated beta target for prerelease checks when available, then deploy the release-ready `1.0.0` Web application at `https://<newname>.peterdsp.dev`, including the backend connectivity required by its real traveller flows. Configure only the project-specific DNS and hosting settings authorized above. Do not create new paid hosting or call localhost a deployed release.

Verify public DNS resolution, valid HTTPS, HTTP-to-HTTPS behavior, the canonical hostname, homepage, direct deep-link loads/reloads, static assets, API connectivity, real search and journey detail, official booking/contact handoff, and applicable offline/PWA behavior on the deployed site. Confirm the deployed version/commit matches the tested `1.0.0` artifact, no development endpoint is required, and rollback is available. Record the live URL, deployment identity, timestamp, and verification evidence in the release manifest and readiness report.

If domain, DNS, hosting, or service access is unavailable, finish the release package and all independent work, record the precise missing capability and prepared deployment step, and leave the `1.0.0` Web release gate blocked. Do not quietly substitute another hostname or mark version `1.0.0` ready without the working public deployment.

### Release materials

Prepare accurate localized store descriptions, beta release notes, installation instructions, known issues, supported devices/OS versions, actual-build screenshots in required formats, icons/splash assets, accessibility notes, support/privacy pages, source attribution, open-source notices, and a corrections path. Verify current platform screenshot and submission requirements rather than guessing dimensions or policies.

Prepare privacy/data-safety declarations from an inventory of actual code, SDKs, permissions, storage, and network behavior. Do not certify missing legal or account-holder declarations. Do not claim analytics, live data, offline maps, national coverage, or human validation that the implementation does not provide.

## 10. Deliverables and evidence

Create and keep current:

- `docs/beta/EXECUTION-STATUS.md`: milestones, decisions, completed work, remaining independent work, and external blockers.
- `docs/beta/ARCHITECTURE-DECISIONS.md`: scope expansion, dependency versions, module ownership, and consequential tradeoffs.
- `docs/beta/BRAND-DECISION.md`: selected name and DNS slug, dated preliminary collision checks, brand assets, migration inventory, and necessary legacy-reference allowlist.
- `docs/beta/TEST-MATRIX.md`: evidence for functional, accessibility, lifecycle, adaptive, language, device, and browser coverage.
- `docs/beta/RELEASE-READINESS.md`: individual product, data, operational, signing, distribution, legal/account, and research gates.
- `docs/beta/BUILD-AND-RELEASE.md`: exact repeatable build, signing, deployment, verification, and rollback steps with secret names only.
- `docs/beta/EXTERNAL-BLOCKERS.md`: missing access/evidence, affected capability, attempts made, prepared artifact, and precise action needed to unblock it; no speculative blockers.
- `docs/beta/RELEASE-NOTES.md` and localized store/support/privacy material in an appropriate release directory.
- A machine-readable artifact manifest containing platform, version/build, commit, build time, local/CI/download location, digest, signing state, data mode, tests, and distribution state. Include store build identifiers and tester-installability status when available.

Put binaries, screenshots, and logs in an ignored local artifacts directory or authorized CI artifact storage, not directly into normal source commits. Record actual artifact locations and retention/expiry. Verify each delivered file exists and the relevant package installs/launches where that target is available. Never add private ticket or credential material to evidence.

Update README and the documentation index to explain the actual implemented state and how to run/install the beta. Keep historical decisions dated instead of silently rewriting them as if the original pilot had already succeeded.

Use these states precisely and separately for each platform: `implemented`, `built`, `runtime-verified`, `packaged`, `signed`, `uploaded`, `processed`, `available-to-testers`, and `blocked`. A platform may be runtime-verified but distribution-blocked. A feature checklist is not an artifact manifest.

## 11. Execution sequence and stopping rule

Work in this order, parallelizing only independent work:

1. Inventory and preserve the current tree; record scope and delivery ledger; research and select the entirely new name, then implement the brand migration before final packaging and deployment.
2. Repair Phase 0 and prove the fixture-based ingest → review → compile → query path.
3. Establish data contracts and one complete Web/API journey flow while continuing lawful corridor verification.
4. Build the shared mobile core and native iOS/Android flows, with adaptive layouts from the start.
5. Finish offline packs, saved trips, wallet, reminders, translations, accessibility, and all failure/recovery states.
6. Verify the complete flows in real browsers, simulators, and emulators; fix defects and complete evidence.
7. Build release artifacts, perform operational checks, prepare store materials, complete every authorized beta upload, and deploy and verify Web `1.0.0` at `https://<newname>.peterdsp.dev`.
8. Reconcile final code, test evidence, artifacts, documentation, source coverage, and distribution status; leave scoped commits and a reviewable draft PR when possible.

Do not stop because you reached a milestone, created tickets, wrote a plan, encountered one blocked provider, or got a single platform building. Do not repeatedly retry the same unavailable credential or approval. Finish all remaining independent work before concluding. When context limits require continuation, preserve exact progress, artifact locations, commands, and next actions so execution resumes without starting over. Do not claim unattended future work is running unless an actual authorized scheduler exists.

Call the overall beta ready only when all required product/data gates and all applicable platform build/runtime gates pass, the relevant installable/deployable artifacts exist, and distribution claims are verified. If full completion is externally blocked, say “engineering complete, beta distribution blocked” only if engineering really is complete; otherwise report the remaining implementation gaps. A demo-only dataset, missing platform build, or untested required foldable runtime must remain visible as a limitation.

Version `1.0.0` additionally requires the working, publicly accessible Web deployment at `https://<newname>.peterdsp.dev` and its live verification evidence. This is a mandatory release gate, not optional follow-up work. Report native beta readiness separately so a successful Web release does not imply that every mobile build is signed or available to testers.

Version `1.0.0` must carry the selected new identity consistently across every delivered platform and the live website. Shipping under HodoMap, retaining an old-name public hostname as canonical, or leaving the rename for a later release fails the release gate. Resolve all placeholder hostnames in final implementation and delivery reports to the actual selected slug.

Your final response must be concise and concrete:

- What works now and the actual coverage/data mode.
- The selected new name, brief rationale, and completed rebrand status.
- The live `https://<newname>.peterdsp.dev` URL and verified deployed `1.0.0` identity, or an explicit blocked Web-release status.
- One row per platform with build/version, artifact or beta URL, verification, and distribution status.
- Foldable/Duo runtime evidence and any device-specific gaps.
- Remaining blockers and exact prepared next actions, if any.
- Branch/PR and links to the release manifest and runbook.

Do not ask me what to do next. Start with repository inspection and carry this through to the strongest complete, demonstrably working beta outcome that the available permissions, evidence, devices, and accounts allow.
