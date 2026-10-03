#!/usr/bin/env bash
# One entry point for every distribution workflow. It chooses the release
# channel and then proves the result matches that channel, so a workflow can
# never ship a release that disagrees with the channel it asked for.
#
#   RELEASE_CHANNEL=demo        scripts/build-release.sh   (default)
#   RELEASE_CHANNEL=production  scripts/build-release.sh
#
# demo       : build the labelled Aloria demonstration release, then assert it is
#              unmistakably data mode demo. This is the current beta behaviour.
# production : build from an explicit reviewed real dataset
#              (<PREFIX>_RELEASE_DATASET), then assert it is data mode real with no
#              demonstration operator and at least one published journey. There is
#              no fallback to demo: a missing dataset fails the build.
#
# The default is demo on purpose: a tag push or dispatch that names nothing keeps
# shipping the clearly labelled demonstration dataset rather than silently
# attempting, and failing, a production build. Flip to production only when a
# reviewed real dataset is wired in.
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"

PY="${PY:-$ROOT/.venv/bin/python}"
case "$PY" in /*) ;; *) [ -x "$ROOT/$PY" ] && PY="$ROOT/$PY" ;; esac
[ -x "$PY" ] || PY="$(command -v python3)"
export PY

PREFIX="$("$PY" -c 'import json;print(json.load(open("brand.json"))["envPrefix"])')"
SLUG="$("$PY" -c 'import json;print(json.load(open("brand.json"))["slug"])')"
ARTIFACTS="${ARTIFACTS_DIR:-$ROOT/artifacts}"
RELEASE_DIR="${RELEASE_DIR:-$ARTIFACTS/releases/$SLUG}"

CHANNEL="${RELEASE_CHANNEL:-demo}"

case "$CHANNEL" in
  demo)
    echo "== release channel: demo =="
    bash scripts/ci-pipeline-smoke.sh
    echo
    echo "== asserting the release is labelled demonstration data =="
    cd "$ROOT/server/api"
    PYTHONPATH=. "$PY" -m publicapi.release_guard \
      --release-dir "$RELEASE_DIR" --expect demo
    cd "$ROOT"
    ;;
  production)
    echo "== release channel: production =="
    bash scripts/release-production.sh
    ;;
  *)
    echo "FAIL: unknown RELEASE_CHANNEL '$CHANNEL' (expected demo or production)." >&2
    exit 1
    ;;
esac

echo
echo "Release build complete on the $CHANNEL channel."
