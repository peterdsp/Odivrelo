#!/usr/bin/env bash
# Bundle the generated release into the web client's static data directory.
#
#   scripts/web-sync-packs.sh [release-dir]
#
# The deployed web client reads /data/manifest.json and /data/packs/... . Those
# are these files, copied byte for byte from the one release the one generator
# produced, which is also what GET /v1/offline/manifest and
# GET /v1/offline/packs/{filename} serve. The web client therefore decodes the
# same payloads whether it is online or offline.
#
# Default release directory: artifacts/releases/<brand slug>/
# Run scripts/api-seed-demo.sh first to produce one.
set -euo pipefail
cd "$(dirname "$0")/.."
exec bash scripts/api-sync-packs.sh "apps/web/public/data" ${1:+"$1"}
