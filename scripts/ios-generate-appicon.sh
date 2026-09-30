#!/usr/bin/env bash
# Installs the Odivrelo app icon, in-app mark and launch mark into the iOS
# asset catalog.
#
# The artwork is not drawn here. design/logo/generate_odivrelo_brand.py renders
# every raster from the one mark geometry and the palette in
# design/tokens/odivrelo.tokens.json, and commits them under
# design/logo/png/ios/. This script only copies them into place and writes the
# catalog's Contents.json files, so it needs no rasteriser and runs the same on
# a developer Mac and on CI.
#
# The icons are square and opaque with no corner mask: iOS applies its own.
# Three appearances ship: any (white mark on deep teal blue), dark (white mark
# on night) and tinted (grayscale, which the system recolours).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="${REPO_ROOT}/design/logo/png/ios"
ASSETS="${REPO_ROOT}/apps/ios/Odivrelo/Resources/Assets.xcassets"

required=(AppIcon-1024.png AppIcon-1024-Dark.png AppIcon-1024-Tinted.png)
for base in BrandMark BrandMark-Dark LaunchMark LaunchMark-Dark; do
  required+=("${base}.png" "${base}@2x.png" "${base}@3x.png")
done
for file in "${required[@]}"; do
  if [[ ! -f "${SOURCE}/${file}" ]]; then
    echo "error: ${SOURCE}/${file} not found; run design/logo/generate_odivrelo_brand.py" >&2
    exit 1
  fi
done

ICONSET="${ASSETS}/AppIcon.appiconset"
MARKSET="${ASSETS}/BrandMark.imageset"
LAUNCHSET="${ASSETS}/LaunchMark.imageset"
rm -rf "${ICONSET}" "${MARKSET}" "${LAUNCHSET}" "${ASSETS}/Wordmark.imageset"
mkdir -p "${ICONSET}" "${MARKSET}" "${LAUNCHSET}"

cp "${SOURCE}/AppIcon-1024.png"        "${ICONSET}/AppIcon-1024.png"
cp "${SOURCE}/AppIcon-1024-Dark.png"   "${ICONSET}/AppIcon-1024-Dark.png"
cp "${SOURCE}/AppIcon-1024-Tinted.png" "${ICONSET}/AppIcon-1024-Tinted.png"

for suffix in "" "@2x" "@3x"; do
  cp "${SOURCE}/BrandMark${suffix}.png"       "${MARKSET}/BrandMark${suffix}.png"
  cp "${SOURCE}/BrandMark-Dark${suffix}.png"  "${MARKSET}/BrandMark-Dark${suffix}.png"
  cp "${SOURCE}/LaunchMark${suffix}.png"      "${LAUNCHSET}/LaunchMark${suffix}.png"
  cp "${SOURCE}/LaunchMark-Dark${suffix}.png" "${LAUNCHSET}/LaunchMark-Dark${suffix}.png"
done

cat > "${ICONSET}/Contents.json" <<'JSON'
{
  "images" : [
    {
      "filename" : "AppIcon-1024.png",
      "idiom" : "universal",
      "platform" : "ios",
      "size" : "1024x1024"
    },
    {
      "appearances" : [ { "appearance" : "luminosity", "value" : "dark" } ],
      "filename" : "AppIcon-1024-Dark.png",
      "idiom" : "universal",
      "platform" : "ios",
      "size" : "1024x1024"
    },
    {
      "appearances" : [ { "appearance" : "luminosity", "value" : "tinted" } ],
      "filename" : "AppIcon-1024-Tinted.png",
      "idiom" : "universal",
      "platform" : "ios",
      "size" : "1024x1024"
    }
  ],
  "info" : { "author" : "xcode", "version" : 1 }
}
JSON

write_imageset() {
  local folder="$1" base="$2"
  cat > "${folder}/Contents.json" <<JSON
{
  "images" : [
    { "filename" : "${base}.png", "idiom" : "universal", "scale" : "1x" },
    { "filename" : "${base}@2x.png", "idiom" : "universal", "scale" : "2x" },
    { "filename" : "${base}@3x.png", "idiom" : "universal", "scale" : "3x" },
    {
      "appearances" : [ { "appearance" : "luminosity", "value" : "dark" } ],
      "filename" : "${base}-Dark.png", "idiom" : "universal", "scale" : "1x"
    },
    {
      "appearances" : [ { "appearance" : "luminosity", "value" : "dark" } ],
      "filename" : "${base}-Dark@2x.png", "idiom" : "universal", "scale" : "2x"
    },
    {
      "appearances" : [ { "appearance" : "luminosity", "value" : "dark" } ],
      "filename" : "${base}-Dark@3x.png", "idiom" : "universal", "scale" : "3x"
    }
  ],
  "info" : { "author" : "xcode", "version" : 1 }
}
JSON
}

write_imageset "${MARKSET}" "BrandMark"
write_imageset "${LAUNCHSET}" "LaunchMark"

# An iOS app icon must be opaque. The generator flattens it; check anyway, so a
# hand-edited PNG with an alpha channel cannot slip into the catalog.
for f in "${ICONSET}"/AppIcon-*.png; do
  if [[ "$(sips -g hasAlpha "$f" | awk '/hasAlpha/{print $2}')" == "yes" ]]; then
    echo "error: $(basename "$f") has an alpha channel; app icons must be opaque" >&2
    exit 1
  fi
done

echo "app icon, brand mark and launch mark installed in ${ASSETS} from ${SOURCE}"
