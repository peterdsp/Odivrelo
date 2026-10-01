# Odivrelo documentation

## Start here

The **beta delivery ledger** in [`beta/`](beta/) is the current authority on
what exists, what is verified and what is blocked.

- [Requirement matrix](beta/REQUIREMENT-MATRIX.md), every requirement verified against the running implementation on 2 October 2026
- [Operator coverage ledger](beta/OPERATOR-COVERAGE-LEDGER.md), what real data is discovered, imported, validated, published, stale or blocked
- [Execution status](beta/EXECUTION-STATUS.md), what is done, in progress and blocked
- [Release readiness](beta/RELEASE-READINESS.md), the gates, assessed separately
- [Test matrix](beta/TEST-MATRIX.md), evidence per platform, with what was not tested
- [External blockers](beta/EXTERNAL-BLOCKERS.md), each with the exact unblocking action
- [Architecture decisions](beta/ARCHITECTURE-DECISIONS.md), dated, AD-001 onward
- [Brand decision](beta/BRAND-DECISION.md), the name, its screening and its limits
- [Build and release](beta/BUILD-AND-RELEASE.md), exact steps per platform
- [Release notes](beta/RELEASE-NOTES.md)
- [Release materials](beta/release/), localised store listings, privacy, support, data safety

## Engineering and data

- [Architecture](ARCHITECTURE.md)
- [Data governance](DATA_GOVERNANCE.md)
- [Design system](DESIGN_SYSTEM.md)
- [Public data contract, version 1](../data/schemas/CONTRACT-v1.md), which every client implements

## Phase 0, closed 30 September 2026

- [Phase 0 backlog](phase0/README.md)
- [Runbook](phase0/RUNBOOK.md), the exact ingest-to-release commands
- [NAP dataset evidence](phase0/NAP-DATASET-EVIDENCE.md), why the real-data gate fails
- [Hygiene audit](phase0/HYGIENE-AUDIT.md)

## Operations

- [Raspberry Pi daily acquisition](../ops/raspberry-pi/DAILY_ACQUISITION.md)
- [Server areas](../server/README.md)

## Historical

These predate 30 September 2026, carry the product's former name, HodoMap (the
product was later called Poravia, and has been Odivrelo since 1 October 2026),
and are **preserved as written**. Rewriting a dated decision to look like it always
said Odivrelo would falsify the record.

- [Pilot decision: web-first, one-corridor](PILOT_DECISION.md) (6 September 2026)
- [Pilot backlog and tickets](pilot/README.md)
- [Corridor brief](pilot/CORRIDOR_BRIEF.md)
- [National execution plan](NATIONAL_EXECUTION_PLAN.md)
- [Roadmap](ROADMAP.md)
- [Product differentiation strategy](PRODUCT_DIFFERENTIATION.md)
- [Live coach map and ETA product analysis](LIVE_COACH_MAP_AND_ETA.md)
- [Earlier KTEL platform analysis](KTEL_NATIONAL_PLATFORM.md)

## How the documents relate

`PILOT_DECISION.md` approved a web-first, one-corridor pilot and deferred
native apps. [AD-001](beta/ARCHITECTURE-DECISIONS.md) records the product
owner's decision to expand **engineering** scope beyond that. It expands
engineering only: neither Gate D1, the corridor data check, nor Gate D2, the
five-traveller validation, is passed by it, and
[PHASE0-02's evidence](phase0/NAP-DATASET-EVIDENCE.md) shows Gate D1 failing.

`NATIONAL_EXECUTION_PLAN.md` remains the longer arc, sequenced behind the
pilot, not a commitment to build in full.

`KTEL_NATIONAL_PLATFORM.md` preserves earlier technical analysis and must not
be read as proof of complete national timetable coverage. Operator registry
counts are registry evidence, not timetable coverage.
