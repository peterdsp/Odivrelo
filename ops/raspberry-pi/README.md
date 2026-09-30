# Raspberry Pi operations

This directory contains the first deployable Poravia service: a bounded daily
source-acquisition timer.

It uses:

- A dedicated `/home/peterdsp/poravia` deployment path.
- A private environment file outside the repository.
- A systemd user timer.
- SQLite acquisition state outside the repository.
- Content-addressed storage only for permitted artifacts.
- JSON reports and a review queue.

Start with [DAILY_ACQUISITION.md](DAILY_ACQUISITION.md).

The public API, release compiler, nginx, backup and rollback services will be
added after the three-operator data pilot. Production and staging must use
separate paths, ports, databases and manifests.
