#!/usr/bin/env bash
# Rename gate. Fails when the old identity, or an unresolved placeholder,
# survives anywhere it should not.
#
#   scripts/check-brand.sh                 # scan tracked source
#   scripts/check-brand.sh --dist <dir>    # scan a built artifact directory
set -euo pipefail

cd "$(dirname "$0")/.."

fail=0
report() { printf '\n%s\n' "$1"; fail=1; }

# Paths where a historical reference is deliberate. Every entry here is also
# listed in docs/beta/BRAND-DECISION.md under the legacy-reference allowlist.
ALLOWLIST_RE='^(docs/AUTONOMOUS-BETA-DELIVERY-PROMPT\.md|docs/beta/BRAND-DECISION\.md|docs/beta/ARCHITECTURE-DECISIONS\.md|docs/beta/EXECUTION-STATUS\.md|docs/beta/EXTERNAL-BLOCKERS\.md|docs/PILOT_DECISION\.md|docs/pilot/|docs/phase0/|docs/KTEL_|docs/INDEX\.md|docs/NATIONAL_EXECUTION_PLAN\.md|docs/ROADMAP\.md|docs/PRODUCT_DIFFERENTIATION\.md|docs/LIVE_COACH_MAP_AND_ETA\.md|server/ktel-staging/syrmos-api-integration\.patch|scripts/check-brand\.sh|CHANGELOG\.md|brand\.json)'

if [ "${1:-}" = "--dist" ]; then
  dist="${2:?--dist needs a directory}"
  [ -d "$dist" ] || { echo "no such directory: $dist" >&2; exit 2; }
  echo "Scanning built artifact: $dist"
  # A built site has no historical documents in it, so nothing is allowed.
  if hits=$(grep -rIlniE 'hodomap|(^|[^a-z])hodo([^a-z]|$)|<newname>|perastra' "$dist" 2>/dev/null); then
    report "Old-brand or placeholder strings in the built artifact:"
    echo "$hits"
  fi
  # Binary assets too.
  if hits=$(grep -rlaiE 'hodomap|<newname>' "$dist" 2>/dev/null); then
    report "Old-brand strings inside binary assets:"
    echo "$hits"
  fi
  [ "$fail" -eq 0 ] && echo "Built artifact is clean."
  exit "$fail"
fi

echo "Scanning tracked source for the rejected identity..."
tracked=$(git ls-files)

while IFS= read -r f; do
  [ -f "$f" ] || continue
  case "$f" in *.png|*.jpg|*.jpeg|*.ico|*.zip|*.xlsx|*.pdf|*.woff|*.woff2|*.ttf) continue ;; esac
  echo "$f" | grep -qE "$ALLOWLIST_RE" && continue
  # A line that explicitly introduces the old name as historical is allowed.
  # Everything else is a defect.
  if hits=$(grep -nIiE 'hodomap|<newname>|perastra' "$f" 2>/dev/null \
            | grep -viE 'former name|formerly|historical|legacy|rejected on'); then
    report "Old-brand or placeholder string in $f:"
    echo "$hits" | head -5
  fi
done <<< "$tracked"

# The legacy environment-variable fallback is deliberate and is the ONLY place
# a syrmos identifier may survive in code.
if hits=$(echo "$tracked" | grep -vE "$ALLOWLIST_RE" | xargs grep -lIni 'syrmos' 2>/dev/null \
          | grep -vE '^server/ktel-staging/(hodomap_ktel|poravia_ktel)/ktel_db\.py$'); then
  report "Unexpected 'syrmos' reference outside the documented env fallback:"
  echo "$hits"
fi

# File and directory names must not carry the old brand either.
if hits=$(echo "$tracked" | grep -iE 'hodomap|perastra' | grep -vE "$ALLOWLIST_RE"); then
  report "Old-brand string in a tracked path:"
  echo "$hits"
fi

if [ "$fail" -eq 0 ]; then
  echo "Clean. No unintended HodoMap, Hodo, syrmos or placeholder reference."
fi
exit "$fail"
