#!/usr/bin/env bash
# Snapshot and restore a published release.
#
#   scripts/api-backup.sh create [archive]     # default: ops/backups/<release>.tar.gz
#   scripts/api-backup.sh restore <archive>
#   scripts/api-backup.sh describe <archive>
#   scripts/api-backup.sh list
#
# A snapshot always captures the compiled public database and the manifest that
# describes it in one archive, together with every pack the manifest lists and
# the release id they agree on. Capturing them separately can pair a database
# from one release with a manifest from another, which is the mixed snapshot the
# release contract forbids.
#
# Restore is refused unless the archived database, manifest and packs all verify
# and all carry the recorded release id, so a corrupt archive can never replace
# a working release. Restore then re-verifies in place and fails if the release
# id is not identical to the one in the snapshot.
#
# The restore procedure is documented in server/api/README.md.
#
# Environment: the same variables the service itself needs, because a snapshot
# is taken from the release the service is configured to serve. The script never
# prints a value.
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"

PY="${PY:-$ROOT/.venv/bin/python}"
case "$PY" in /*) ;; *) [ -x "$ROOT/$PY" ] && PY="$ROOT/$PY" ;; esac
[ -x "$PY" ] || PY="$(command -v python3)"

BACKUP_DIR="${BACKUP_DIR:-$ROOT/ops/backups}"
COMMAND="${1:-}"
[ -n "$COMMAND" ] || { sed -n '2,24p' "$0" >&2; exit 2; }
shift || true

if [ "$COMMAND" = "list" ]; then
  if [ -d "$BACKUP_DIR" ]; then
    find "$BACKUP_DIR" -maxdepth 1 -name '*.tar.gz' -print | sort
  else
    echo "no snapshots in $BACKUP_DIR" >&2
  fi
  exit 0
fi

ARCHIVE="${1:-}"
if [ "$COMMAND" = "create" ] && [ -z "$ARCHIVE" ]; then
  mkdir -p "$BACKUP_DIR"
  STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
  ARCHIVE="$BACKUP_DIR/release-$STAMP.tar.gz"
fi
[ -n "$ARCHIVE" ] || { echo "FAIL: $COMMAND needs an archive path" >&2; exit 2; }

cd "$ROOT/server/api"
PYTHONPATH=. exec "$PY" -m publicapi.backup "$COMMAND" "$ARCHIVE"
