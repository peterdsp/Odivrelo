#!/usr/bin/env bash
# Regenerates the Android application's three strings.xml files from one table.
#
#   bash scripts/android-generate-strings.sh
#
# Greek, English and Albanian are written from a single list in
# scripts/_android_strings_table.py, so the three files cannot drift apart: a key
# either exists in all three or in none. The Android module also carries a unit
# test that re-checks this at build time, because a later hand edit will not go
# through this script.
#
# Writes:
#   apps/android/src/main/res/values/strings.xml       Greek, the default
#   apps/android/src/main/res/values-en/strings.xml    English
#   apps/android/src/main/res/values-sq/strings.xml    Albanian
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

python3 scripts/_android_strings_table.py apps/android
