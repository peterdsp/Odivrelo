#!/usr/bin/env bash
# Final step of the brand migration: rename the staging Python package from
# hodomap_ktel to poravia_ktel.
#
# This is deliberately LAST. The package is imported by the API service, the
# seed script, the release generator and the test suites, so renaming it while
# other work is in flight would break every one of them at once.
#
#   bash scripts/finalize-package-rename.sh            # do it
#   bash scripts/finalize-package-rename.sh --check     # report only
#
# The legacy HODOMAP_KTEL_* and SYRMOS_KTEL_* environment names are NOT touched.
# They are the documented compatibility fallback for an existing deployment and
# are listed in the allowlist in docs/beta/BRAND-DECISION.md.
set -euo pipefail

cd "$(dirname "$0")/.."
OLD=hodomap_ktel
NEW=poravia_ktel
PKG_DIR="server/ktel-staging/$OLD"

if [ "${1:-}" = "--check" ]; then
  echo "Files importing $OLD:"
  git grep -ln "$OLD" -- . || echo "  none"
  exit 0
fi

if [ ! -d "$PKG_DIR" ]; then
  echo "Already renamed: $PKG_DIR does not exist."
  exit 0
fi

echo "== moving the package, preserving history =="
git mv "$PKG_DIR" "server/ktel-staging/$NEW"

echo "== rewriting imports and references =="
# Every tracked text file, excluding the historical documents that describe the
# PHASE0-01 rename as it happened.
git ls-files -z | while IFS= read -r -d '' f; do
  case "$f" in
    docs/phase0/tickets/*|docs/AUTONOMOUS-BETA-DELIVERY-PROMPT.md) continue ;;
    docs/beta/BRAND-DECISION.md|brand.json) continue ;;
    *.png|*.jpg|*.jpeg|*.ico|*.zip|*.xlsx|*.pdf) continue ;;
  esac
  [ -f "$f" ] || continue
  if grep -qI "$OLD" "$f" 2>/dev/null; then
    # Protect the env-var fallbacks, which keep their historical spelling.
    sed -i '' \
      -e 's/HODOMAP_KTEL_/@@ENV1@@/g' \
      -e "s/$OLD/$NEW/g" \
      -e 's/@@ENV1@@/HODOMAP_KTEL_/g' "$f"
    echo "  $f"
  fi
done

echo "== verifying =="
cd server/ktel-staging
PY="${PY:-../../.venv/bin/python}"
PYTHONPATH=. "$PY" -c "
import $NEW, $NEW.ktel_api, $NEW.ktel_db, $NEW.ktel_gtfs, $NEW.ktel_ingest
import $NEW.ktel_publish, $NEW.ktel_registry, $NEW.ktel_release, $NEW.ktel_ticketweb
print('  all modules import under the new name')"
PYTHONPATH=. "$PY" -m unittest discover -s tests 2>&1 | tail -3
cd ../..
"$PY" -m pytest server/api/tests -q 2>&1 | tail -3
bash scripts/ci-pipeline-smoke.sh >/dev/null && echo "  pipeline smoke passed"
bash scripts/check-brand.sh

echo
echo "Rename complete. Review the diff before committing."
