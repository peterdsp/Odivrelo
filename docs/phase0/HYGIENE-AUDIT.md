# PHASE0-04 outcome: repository runtime-state and secrets hygiene audit

- Ticket: [PHASE0-04](tickets/PHASE0-04-repo-hygiene-audit.md)
- Audited: **30 September 2026**, at commit `22f09b9` on `codex/beta-1.0.0`
- Result: **pass, no remediation needed**

## Findings

| Check | Command | Result |
|---|---|---|
| No tracked SQLite runtime state | `git ls-files \| grep -iE '\.(db\|sqlite\|sqlite3)$\|-wal$\|-shm$'` | none |
| No tracked credential files | `git ls-files \| grep -iE '\.env$\|\.pem$\|\.key$\|\.p12$\|\.jks$\|\.keystore$\|id_rsa\|credentials'` | none |
| No token-shaped strings | `git grep -nIE "(gh[pousr]_[A-Za-z0-9]{20,}\|AKIA[0-9A-Z]{16}\|-----BEGIN [A-Z ]*PRIVATE KEY-----\|xox[baprs]-)"` | none |
| TicketWeb gate off | `git grep -n TICKETWEB_TERMS_APPROVED` | only `=0` in the two `.env.example` files, the `!= "1"` guards in code, and prose in docs. The switch is not enabled anywhere. |
| `.gitignore` covers runtime state | review | covers `*.db`, `*.db-wal`, `*.db-shm`, `*.sqlite*`, `.venv/`, `**/build/`, `node_modules/`, `DerivedData/`, `local.properties`, `*.jks`, `*.keystore`, `*.p12`, `*.pem`, `*.key`, `.env`, `google-services.json`, `GoogleService-Info.plist` |
| `.gitignore` covers data roots | review | covers `/data/raw/`, `/data/staging/`, `/data/private/`, `/data/releases/`, `/data/cache/`, `/server/cache/`, `/server/out/`, `/ops/backups/` |

## Changes made

`.gitignore` gained the artifact roots introduced by this delivery, so no
binary, screenshot, log or downloaded dataset can be committed by accident:

```
/artifacts/
apps/*/artifacts/
```

## Rights-restricted bodies

None are tracked. The NAP workbook retrieved for
[PHASE0-02](NAP-DATASET-EVIDENCE.md) is ODbL, so redistribution would be
permitted, but it is deliberately **not** committed: it is a 231 KB binary and
`DATA_GOVERNANCE.md` keeps bulk source bodies out of the repository. Its
SHA-256 and full lineage are committed instead, and the file itself lives under
the gitignored `artifacts/nap/`.

No passenger data, no booking response, no provider export and no production
database exists anywhere in the tree.

## Incident status

No secret, credential or rights-restricted body was found tracked. No rotation
is required. No other Phase 0 work was blocked.
