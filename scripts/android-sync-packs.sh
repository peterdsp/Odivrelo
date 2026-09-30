#!/usr/bin/env bash
# Bundle the generated release into the Android client's assets.
#
#   scripts/android-sync-packs.sh [release-dir]
#
# The Android client reads release/manifest.json and release/packs/... from its
# assets. Those are these files, copied byte for byte from the one release the
# one generator produced, which is also what GET /v1/offline/manifest and
# GET /v1/offline/packs/{filename} serve. The client therefore decodes the same
# payloads whether it is online or offline.
#
# This replaces scripts/shared-build-demo-release.sh, which emitted a third,
# incompatible pack shape of its own.
#
# Default release directory: artifacts/releases/<brand slug>/
# Run scripts/api-seed-demo.sh first to produce one.
set -euo pipefail
cd "$(dirname "$0")/.."
exec bash scripts/api-sync-packs.sh "apps/android/src/main/assets/release" ${1:+"$1"}
