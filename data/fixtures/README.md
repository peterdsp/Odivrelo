# Test fixtures

Only small synthetic or lawfully retained fixtures belong here.

Each source-derived fixture must document:

- Source.
- Retrieval date.
- Rights basis.
- Redactions.
- Scenario being tested.

Never commit credentials, passenger information, bulk provider exports or
unreviewed raw responses.

## The Aloria demonstration dataset

**This is invented demonstration data. The region of Aloria does not exist. No
row in these files represents a real departure, terminal, bay, fare, telephone
number or operator. Nothing here may be used in store screenshots, marketing,
press material or any other context that implies real coverage of any real
transport network.**

Files:

| File | What it is |
| --- | --- |
| `aloria-demo-snapshot.json` | The main normalized snapshot: one invented operator, one terminal with two bays, three further stops, one line, two journey patterns, two service calendars with exceptions, four journeys, and one stop deliberately left unreviewed. |
| `aloria-demo-rights-pending.json` | A second snapshot from a source whose reuse rights are still pending. It is approved by review on purpose, to prove that review alone never makes something public. |

### Why a demonstration dataset exists at all

`data/operators/registry.json` lists 62 federation operators and every one of
the directory sources carries `rightsStatus: unknown`. No real Greek operator
currently has documented reuse rights, so the beta cannot ship real timetables
without asserting a right it does not have. It ships this instead, labelled at
every layer:

- the snapshot declares `"dataset": "demo"`;
- the operator id is `demo-aloria-coach` and it has no federation number;
- the API refuses to start without an explicit data mode, and `/readyz` fails
  if a release containing these rows is configured to be served as real data;
- every public response carries `dataMode: "demo"`, which obliges every client
  to show a persistent, non-dismissible notice and to send `noindex` on web;
- `/v1/coverage` states in `notCovered` that no real operator is covered.

### Provenance

- **Source**: invented by hand for engineering and contract verification. There
  is no upstream source and nothing was retrieved from any operator.
- **Retrieval date**: not applicable. The fixture records
  `retrievedAt: 2026-09-20T06:00:00Z` so imports are deterministic.
- **Rights basis**: the data is original to this repository and carries the
  repository licence. The `manual-review` source it is attributed to has
  `rightsStatus: allowed` because it describes this project's own review notes.
- **Redactions**: none are needed. There is nothing real to redact.

### Coordinates

Every stop sits in the open Atlantic near 0°N 0°E (latitude 0.06 to 0.52,
longitude -0.58 to -0.11). That is deliberate: the positions are obviously
synthetic, they are thousands of kilometres from any Greek terminal, and they
cannot be mistaken for a real place on a map.

Those coordinates fail the real-data coordinate gate, which quarantines anything
outside a Greek bounding box. The gate was not weakened to let them through.
The compiled-data layer's `ktel_registry.coordinate_status` takes an explicit
`dataset="demo"` flag that skips the bounding box and nothing else: a missing,
unparseable, out-of-range or null-island coordinate is still quarantined, and a
snapshot without the flag is still held to the full Greek gate.

### Scenarios these fixtures exercise

| Scenario | Where |
| --- | --- |
| Ordinary daytime journey | `trip-daytime`, 2026-10-02 09:00 to 12:10 |
| Journey crossing midnight, GTFS time past 24:00 | `trip-overnight`, 2026-10-02 23:40 to 2026-10-03 01:20, exported as `25:20:00` |
| Spring daylight-saving transition | `trip-sunday-dst` on 2026-03-29, departs 09:00+03:00 |
| Autumn daylight-saving transition | the same journey on 2026-10-25, departs 09:00+02:00 |
| Calendar exception, date removed | `cal-weekday` removes 2026-04-06, so no journey is returned |
| Calendar exception, date added | `cal-weekday` adds Saturday 2026-04-11, so the weekday journey runs |
| Purchase by ticket office only | `trip-overnight`, no online sale and no booking URL |
| Purchase online | `trip-daytime`, `trip-sunday-dst`, `trip-weekday-afternoon` |
| Intermediate stop that may not be boarded | `stop-mistona-village`, `pickup: not_allowed` |
| Reviewed step-free boarding point | `stop-aloria-bay-a1`, `stepFree: true` with a review timestamp |
| Boarding point with no step-free review | `stop-aloria-bay-a2` |
| Journey restriction text | `trip-overnight`, `overnight_unaccompanied_minors` |
| Rights-pending source excluded from public output | `aloria-demo-rights-pending.json`, source `ticketweb` |
| Unreviewed candidate excluded from public output | `stop-unreviewed-candidate`, held back by `leaveUnreviewed` |

### Loading it

```bash
scripts/api-seed-demo.sh
```

That builds the ingestion database, imports both fixtures, publishes everything
except the entities held back on purpose, compiles the read-only public
database, and generates the release packs, the GTFS feed and the manifest into a
gitignored artifacts directory. No database, pack or manifest is committed.

### Extra keys these files carry

The snapshot importer accepts three keys beyond the normalized snapshot
contract. All three are inert for real data:

- `dataset`: `"demo"` here. Absent means real data and every gate applies as
  before.
- `demoOperators`: the invented operator rows to register before import. Refused
  unless `dataset` is `"demo"`, every id must start with `demo-`, and none may
  claim a federation number. The national registry is never given an invented
  entry.
- `leaveUnreviewed`: external ids the seeder must not publish, so the fixture
  can carry a permanent unreviewed candidate.

`serviceCalendars` and `publicAttributes` are read by the seeder and the API
respectively; see `server/api/README.md`.
