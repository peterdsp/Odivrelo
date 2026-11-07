#!/usr/bin/env bash
# Runs the iOS App Store listing (and, with "submit", the App Review
# submission) from this Mac, using the App Store Connect key in
# ~/git/signing-keys. For when GitHub Actions is unavailable.
#
#   bash scripts/ios-app-store-listing.sh          listing, screenshots, build
#   bash scripts/ios-app-store-listing.sh submit   the same, then App Review
#
# PRICE_EUR, BUILD, CONTENT_RIGHTS and REVIEW_PHONE pass through; see
# scripts/ios-app-store-listing.py.
set -euo pipefail
cd "$(dirname "$0")/.."
ASC="${ASC_DIR:-$HOME/git/signing-keys/app-store-connect}"
export ASC_KEY_ID="${ASC_KEY_ID:-G4VF7GLND7}"
export ASC_KEY_PATH="${ASC_KEY_PATH:-$ASC/AuthKey_$ASC_KEY_ID.p8}"
export ASC_ISSUER_ID="${ASC_ISSUER_ID:-$(python3 -c "import json,sys; print(json.load(open(sys.argv[1]))['issuerId'], end='')" "$ASC/nostavela-appstore.json")}"
export VERSION="${VERSION:-1.0.0}"
export SUBMIT="$([[ "${1:-}" == submit ]] && echo 1 || echo 0)"
uv run --quiet --with "pyjwt[crypto]" python scripts/ios-app-store-listing.py
