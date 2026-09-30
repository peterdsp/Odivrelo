# Contributing to Poravia

Thank you for helping make Greek intercity coach information easier to use.

## Before opening a change

1. Open or reference an issue that explains the passenger problem.
2. Identify which layer owns the change.
3. Keep timetable and routing truth on the server.
4. Confirm that any data or fixture is lawful to retain and redistribute.
5. Avoid committing credentials, raw booking responses or passenger data.

## Change requirements

### Application code

- Add focused tests for new behavior.
- Preserve Greek, English and Albanian behavior.
- Check offline and online states.
- Check accessibility on the affected platform.
- Keep platform-specific maps and UI behavior behind narrow interfaces.

### API and timetable logic

- Add contract tests.
- Preserve source lineage and release identity.
- Validate service dates in `Europe/Athens`.
- Keep clients from independently reinterpreting timetable semantics.
- Document backward-incompatible API changes before merging.

### Source adapters

Every adapter change requires:

- A small, lawful fixture.
- Source and retrieval metadata.
- Parser version.
- Timeouts and response-size limits.
- A semantic diff.
- Quarantine behavior for malformed or surprising records.
- Rights status and review ownership.

### Stop and route changes

- Preserve source identifiers and original labels.
- Do not merge stops from name similarity alone.
- Provide map or source evidence for coordinate changes.
- Do not call routed or inferred geometry exact without review.

## Pull requests

Keep pull requests narrow and explain:

- What changed.
- Why it changed.
- How it was verified.
- What was not deployed or live-tested.
- Whether data rights or attribution changed.

Do not mix runtime database files with application changes.

## Commit style

Use short imperative subjects:

```text
Add operator coverage contract
Fix service-date parsing
Document stop review workflow
```

## Security

Do not report credentials or exploitable vulnerabilities in public issues.
Follow [SECURITY.md](SECURITY.md).
