# Ingestion

The initial bounded acquisition implementation lives in
`server/src/poravia_pipeline`.

Every adapter requires:

- Lawful fixture.
- Source and rights metadata.
- Parser version.
- Time and response-size bounds.
- Semantic diff.
- Quarantine behavior.
- Maintainer ownership.

Do not add brute-force operator or origin-destination sweeps.

Run:

```bash
./scripts/test-server.sh
```

The current daily runner monitors source changes and creates review work. It
does not yet convert operator-specific HTML and PDF timetables into public
journeys.
