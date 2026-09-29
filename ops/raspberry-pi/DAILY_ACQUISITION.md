# Raspberry Pi Daily Acquisition

## What the timer does

Every day the Pi:

1. Loads the validated 62-operator registry.
2. Selects enabled sources whose cadence is due.
3. Sends bounded conditional HTTP requests.
4. Uses ETag and Last-Modified when upstream supports them.
5. Hashes bounded response bodies.
6. Extracts external official-site candidates from directory pages.
7. Discards bodies when retention rights are unknown.
8. Stores only explicitly permitted artifacts.
9. Adds changed sources and discovered links to review queues.
10. Writes a JSON run report.
11. Publishes no timetable automatically.

The default registry monitors:

- 62 federation operator directory pages.
- The national POAYS directory.
- The Greek NAP dataset catalog page.

These checks discover source changes. They do not yet parse every operator's
timetable. Each reviewed official timetable URL receives its own adapter and
rights state before it can enter the national database.

TicketWeb is intentionally absent from the enabled registry. Adding it requires
written terms approval, documented credentials outside the repository and a
separate bounded adapter.

## Verified deployment

On 26 July 2026:

- The source tree was deployed to `/home/peterdsp/poravia`.
- The registry validated 62 official operators and 64 enabled metadata targets.
- `poravia-acquire.timer` was enabled and active.
- User lingering was confirmed active for unattended runs.
- A live three-source smoke run completed with three successful HTTP 200
  checks.
- Three changed digests entered the review queue.
- Zero rights-unknown source bodies were retained.
- No public timetable was published.

The first full due sweep then checked the remaining 61 targets:

- 60 targets succeeded.
- The Greek NAP catalog failed TLS verification because the upstream
  certificate had expired.
- TLS verification was not bypassed.
- Across both runs, all 62 operator directory pages were checked.
- The acquisition store contained 63 changed-source review items.
- The artifact store still contained zero files.

This verifies the daily acquisition monitor. It does not verify the future
public API, timetable parsers, compiler, backup restoration or national client
applications.

## Runtime locations

```text
/home/peterdsp/poravia/
    Deployed source tree

/home/peterdsp/.config/poravia/acquisition.env
    Private runtime configuration

/home/peterdsp/.local/share/poravia/acquisition.db
    Acquisition state and review queue

/home/peterdsp/.local/share/poravia/artifacts/
    Permitted content-addressed artifacts only

/home/peterdsp/.local/state/poravia/reports/
    Per-run JSON reports
```

## Deployment

From the repository root:

```bash
./ops/raspberry-pi/deploy.sh
```

The script deploys to the dedicated `/home/peterdsp/poravia` directory. It
does not delete the remote directory, replace the private environment file or
touch Syrmos runtime data.

For unattended user timers after reboot, enable user lingering once:

```bash
sudo loginctl enable-linger peterdsp
```

## Operations

Validate the registry:

```bash
PYTHONPATH=server/src python3 -m poravia_pipeline validate-registry
```

Show what is due:

```bash
PYTHONPATH=server/src python3 -m poravia_pipeline plan
```

Run a bounded smoke acquisition:

```bash
PYTHONPATH=server/src python3 -m poravia_pipeline \
  refresh --max-sources 3
```

Inspect status:

```bash
PYTHONPATH=server/src python3 -m poravia_pipeline status
```

Inspect the user timer:

```bash
systemctl --user status poravia-acquire.timer
systemctl --user list-timers poravia-acquire.timer
```

Inspect the latest job:

```bash
journalctl --user -u poravia-acquire.service -n 200 --no-pager
```

Trigger one full due run:

```bash
systemctl --user start poravia-acquire.service
```

## Failure policy

- One source failure does not abort the remaining sources.
- Failed sources retain consecutive failure counts.
- Changes enter review and are not public automatically.
- Oversized or unexpected responses fail closed.
- Unknown rights permit metadata-only monitoring, not body retention.
- Ticketing sources fail closed until terms approval is explicit.
- The entire run has a hard request budget.
- Requests to one host are rate limited and jittered.
