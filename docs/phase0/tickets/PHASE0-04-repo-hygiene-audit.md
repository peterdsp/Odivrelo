# PHASE0-04: Repository runtime-state and secrets hygiene audit

> **Historical document.** This records a decision made under the product's
> former name, HodoMap, which was rejected on 30 September 2026 and replaced
> by **Poravia**. The text below is preserved as written, including the old
> name, because rewriting a dated decision would falsify the record. See
> [the brand decision](../../../docs/beta/BRAND-DECISION.md).


- Type: hygiene
- Status: done, 30 September 2026
- Phase: 0, clear the runway
- Depends on: none
- Blocks: a clean pilot start
- Owner: unassigned
- Estimate: half a day

## Goal

Confirm the repository holds no tracked runtime database state, no secrets, and
no rights-restricted bodies, and that the acquisition state store is isolated
from the repository.

## Why

The governance rules forbid committing SQLite runtime files, credentials, and
rights-pending bodies. A quick audit before the pilot keeps the repository honest
and protects the project's independence posture. It is cheap now and expensive to
discover later.

## Tasks

- [ ] Confirm no `*.db`, `*-wal`, or `*-shm` files are tracked.
- [ ] Confirm `.gitignore` covers the acquisition data root and the artifact
      root used by the pipeline.
- [ ] Grep tracked files for tokens, keys, and credentials, and for any contact
      value that should not be committed.
- [ ] Confirm the ticketing acquisition gate stays off: the
      `HODOMAP_TICKETWEB_TERMS_APPROVED` switch is not set in any tracked file.
- [ ] Record the audit result in a short note under `docs/phase0/`.

## Data and rights

- If any secret, credential, or rights-restricted body is found tracked, treat it
  as an incident: stop, remove it, and rotate the secret if one was exposed.

## Acceptance criteria

- [ ] The audit is recorded with its date and outcome.
- [ ] `.gitignore` covers runtime state and pipeline artifacts.
- [ ] No secret or rights-restricted body is tracked. Any finding has a
      remediation note.

## Kill or switch criteria

- If a secret or rights-restricted body is found, stop other Phase 0 work and
  remediate before continuing.

## Out of scope (provisional, do not build yet)

- National secret management or a vault.
- Production deployment hardening on the Raspberry Pi.

## References

- [Data governance, raw data rules](../../DATA_GOVERNANCE.md)
- [National execution plan, phase 0 protect runtime state](../../NATIONAL_EXECUTION_PLAN.md)

## Outcome (30 September 2026)

Pass, no remediation needed. No tracked runtime database, secret or
rights-restricted body. The TicketWeb gate is off everywhere. `.gitignore`
gained `/artifacts/` and `apps/*/artifacts/`.

Full record: [HYGIENE-AUDIT.md](../HYGIENE-AUDIT.md).
