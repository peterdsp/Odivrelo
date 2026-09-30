# Data Governance

Odivrelo publishes public transport information only when its provenance,
rights, freshness and review state are known.

This document is an engineering policy, not legal advice.

## Source classes

Sources may include:

- Government open-data publications.
- Official operator websites and documents.
- Operator-provided feeds.
- Provider feeds covered by written permission.
- OpenStreetMap data under its applicable terms.
- Small synthetic or lawfully retained test fixtures.

An accessible endpoint is not automatically a reusable public dataset.

## Rights states

Every source artifact receives exactly one rights state:

| State | Meaning | Public compilation |
|---|---|---|
| `permitted` | Reuse is documented and within scope | Allowed after review |
| `permission_pending` | Permission has been requested | Blocked |
| `restricted_internal` | Internal validation only | Blocked |
| `prohibited` | Terms or owner prohibit the use | Blocked |
| `unknown` | Rights have not been established | Blocked |

Only `permitted` artifacts can contribute rows to a public release.

## Review states

Normalized entities use:

- `candidate`
- `approved`
- `rejected`
- `quarantined`
- `retired`

Only `approved` entities can be compiled.

## Required lineage

Every source record retains:

- Source identifier and operator.
- Original external identifier.
- Retrieval timestamp.
- Effective date or period, when available.
- Source URL or delivery reference.
- Content digest.
- Parser version.
- Rights state.
- Review history.

Public entities retain links back to every contributing source record.

## Raw data

Do not commit:

- Browser credentials or session material.
- Raw private or undocumented booking responses.
- Passenger names, contacts, payment details or tickets.
- Provider exports without documented retention rights.
- Production databases, WAL files, SHM files or backups.
- Bulk files whose license is unresolved.

Use local encrypted storage or approved infrastructure for restricted
artifacts. Store only the minimum required for the approved purpose and apply a
retention period.

## Stop normalization

- Preserve original names and identifiers.
- Normalize Greek text without destroying the source spelling.
- Keep station complexes separate from boarding points.
- Validate coordinates before matching.
- Never merge on name similarity alone.
- Require human review for ambiguous or cross-operator terminal matches.
- Keep a reversible merge and split history.

Invalid coordinates enter quarantine and never reach a public release.

## Timetables

Every public trip requires:

- Operator.
- Route and ordered journey pattern.
- Service calendar and exceptions.
- Departure and arrival chronology.
- Effective period.
- Source retrieval time.
- Review state.

Booking observations are date-specific evidence of availability. They do not
prove a recurring calendar.

## Geometry

Preferred geometry sources:

1. Operator-provided geometry.
2. Government-authoritative geometry.
3. Reviewed OpenStreetMap relations.
4. Road routing through ordered reviewed stops, followed by human review.

Every geometry records source, method, confidence, version and reviewer.

## Publication gate

Before release:

1. Select only permitted source records.
2. Select only approved normalized entities.
3. Validate foreign keys and route chronology.
4. Reject invalid coordinates.
5. Validate attribution.
6. Compile into a new temporary public artifact.
7. Run integrity and contract tests.
8. Publish immutable packs.
9. Write the release manifest last.

The previous public release remains available for rollback.

## Corrections and takedowns

Every public entity should expose a correction path. Reports must preserve:

- Entity and release ID.
- Reported issue.
- Reporter contact only when voluntarily supplied.
- Review outcome.
- Corrective source evidence.

Rights holders can request review or takedown through `info@peterdsp.dev`.
